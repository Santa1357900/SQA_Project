import java.lang.reflect.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic test-program decoder. Used unchanged during search and JUnit replay. */
final class DEReplay {
    public static final int DIMENSIONS = 64;
    /** Local generic bean used to create stable reflection Type and Field values. */
    public static final class GenericInput {
        public String text;
        public java.util.List<String> names;
        public java.util.Map<String, Integer> counts;
        public java.util.List<java.util.Map<String, Long>> nested;
        public int[] numbers;
        public String[] words;
    }
    static final class Genes {
        final int[] values; int at; Field lastField;
        Object jacksonBean, jacksonProvider, jacksonGenerator;
        StringWriter jacksonOutput;
        Genes(int[] values) { this.values = values; }
        int next() { return values[(at++) % values.length]; }
        int pick(int n) { return Math.floorMod(next(), n); }
    }
    public static final class Observation {
        public String token, error, generatedSource;
        public boolean reachedTarget;
        public int setupCalls, setupFailures, nullFallbacks;
    }
    public static String signature(Method m) {
        StringJoiner params = new StringJoiner(",");
        for (Class<?> t : m.getParameterTypes()) params.add(t.getTypeName());
        return m.getName() + "(" + params + "):" + m.getReturnType().getTypeName();
    }
    static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, true, Thread.currentThread().getContextClassLoader());
    }
    static Method method(String target, String signature) throws Exception {
        for (Method m : load(target).getDeclaredMethods())
            if (signature(m).equals(signature)) {
                // Public methods on package-private Defects4J classes (for
                // example Gson's TypeInfoFactory) are not reflectively
                // accessible until opened on the unnamed application module.
                if (!m.isAccessible()) m.setAccessible(true);
                return m;
            }
        throw new NoSuchMethodException(signature);
    }
    static List<Constructor<?>> constructors(Class<?> type) {
        List<Constructor<?>> out = new ArrayList();
        if (!Modifier.isAbstract(type.getModifiers()) && Modifier.isPublic(type.getModifiers()))
            for (Constructor<?> c : type.getConstructors())
                if (c.getParameterTypes().length <= 6) out.add(c);
        Collections.sort(out, new Comparator<Constructor<?>>() {
            public int compare(Constructor<?> a, Constructor<?> b) {
                int byArity = a.getParameterTypes().length - b.getParameterTypes().length;
                return byArity != 0 ? byArity : a.toString().compareTo(b.toString());
            }
        });
        return out;
    }
    private static Object[] arguments(Class<?>[] types, Genes g, int depth,
                                      Observation report) throws Exception {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) args[i] = value(types[i], g, depth, report);
        return args;
    }
    private static Object[] arguments(Method method, Genes g, int depth,
                                      Observation report) throws Exception {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        boolean closureCompile = method.getDeclaringClass().getName().equals("com.google.javascript.jscomp.Compiler")
            && method.getName().equals("compile");
        Type[] generic = method.getGenericParameterTypes();
        boolean jacksonSerialization = method.getDeclaringClass().getName().equals(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter")
            && method.getName().startsWith("serializeAs")
            && types.length == 3 && types[0] == Object.class;
        for (int i = 0; i < types.length; i++) {
            if (jacksonSerialization && g.jacksonBean != null) {
                if (i == 0) args[i] = g.jacksonBean;
                else if (i == 1) args[i] = g.jacksonGenerator;
                else args[i] = g.jacksonProvider;
                continue;
            }
            if (closureCompile && (List.class.isAssignableFrom(types[i])
                    || (types[i].isArray() && load("com.google.javascript.jscomp.SourceFile")
                        .isAssignableFrom(types[i].getComponentType())))) {
                // Compiler.compile takes externs and inputs in either Lists or
                // arrays depending on Closure version. Keep externs empty and
                // supply a nonempty, gene-selected input program.
                if (i == 0) args[i] = types[i].isArray()
                    ? Array.newInstance(types[i].getComponentType(), 0) : new ArrayList();
                else {
                    Object sourceFile = value(load("com.google.javascript.jscomp.SourceFile"),
                        g, depth + 1, report);
                    if (types[i].isArray()) {
                        Object files = Array.newInstance(types[i].getComponentType(), 1);
                        Array.set(files, 0, sourceFile); args[i] = files;
                    } else args[i] = new ArrayList(Collections.singletonList(sourceFile));
                }
            } else args[i] = value(types[i], g, depth, report);
        }
        return args;
    }
    private static Number number(Genes g) {
        int n = g.next();
        switch (g.pick(12)) {
            case 0: return 0; case 1: return 1; case 2: return -1;
            case 3: return Integer.MAX_VALUE; case 4: return Integer.MIN_VALUE;
            case 5: return Long.MAX_VALUE; case 6: return Long.MIN_VALUE;
            case 7: return Double.NaN; case 8: return Double.POSITIVE_INFINITY;
            case 9: return Double.NEGATIVE_INFINITY; case 10: return n / 10.0;
            default: return n;
        }
    }
    private static String string(Genes g) {
        int mode = g.pick(16), n = g.next();
        String digits = Long.toString(Math.abs((long)n));
        String sign = new String[]{"", "-", "+", "--"}[g.pick(4)];
        switch (mode) {
            case 0: return null; case 1: return ""; case 2: return " ";
            case 3: return Integer.toString(n);
            case 4: return sign + digits;
            case 5: return sign + digits + "." + g.pick(1000);
            case 6: return sign + digits + "e" + g.next();
            case 7: return sign + "0x" + Long.toHexString(Math.abs((long)n));
            case 8: return sign + "0x8" + "0".repeat(g.pick(20));
            case 9: return sign + digits + "fFdDlL".charAt(g.pick(6));
            case 10: return " " + sign + digits + " ";
            case 11: return new String[]{"true", "false", "null", "NaN", "Infinity"}[g.pick(5)];
            case 12: return "a".repeat(g.pick(25));
            default:
                String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+-._ /\\\t\n";
                StringBuilder s = new StringBuilder();
                int length = g.pick(25);
                for (int i = 0; i < length; i++) s.append(alphabet.charAt(g.pick(alphabet.length())));
                return s.toString();
        }
    }
    private static Object value(Class<?> t, Genes g, int depth, Observation report) throws Exception {
        if (t == String.class || t == CharSequence.class) return string(g);
        if (t == Comparable.class) return "key" + g.pick(5);
        if (t == boolean.class || t == Boolean.class) return g.pick(2) == 0;
        if (t == char.class || t == Character.class) return (char)g.pick(128);
        if (t == byte.class || t == Byte.class) return number(g).byteValue();
        if (t == short.class || t == Short.class) return number(g).shortValue();
        if (t == int.class || t == Integer.class) return number(g).intValue();
        if (t == long.class || t == Long.class) return number(g).longValue();
        if (t == float.class || t == Float.class) return number(g).floatValue();
        if (t == double.class || t == Double.class || t == Number.class) return number(g).doubleValue();
        if (t.isEnum()) {
            Object[] constants = t.getEnumConstants();
            return constants.length == 0 ? null : constants[g.pick(constants.length)];
        }
        if (t == Object.class) return g.pick(3) == 0 ? null : "object" + g.pick(5);
        if (depth >= 3) { report.nullFallbacks++; return null; }
        if (t.isArray()) {
            int length = g.pick(6);
            Object array = Array.newInstance(t.getComponentType(), length);
            for (int i = 0; i < length; i++) Array.set(array, i, value(t.getComponentType(), g, depth + 1, report));
            return array;
        }
        if (t == List.class || t == Collection.class || t == Iterable.class || t == Set.class) {
            Collection<Object> items = t == Set.class ? new LinkedHashSet() : new ArrayList();
            int size = g.pick(5);
            for (int i = 0; i < size; i++) items.add("item" + g.pick(5));
            return items;
        }
        if (t == Map.class) {
            Map<Object, Object> items = new LinkedHashMap();
            int size = g.pick(5);
            for (int i = 0; i < size; i++) items.put("key" + g.pick(5), number(g));
            return items;
        }
        if (t == java.util.Date.class) return new java.util.Date(g.next() * 86400000L);
        if (t == Class.class) return String.class;
        if (t == java.lang.reflect.Field.class) {
            Field[] fields = GenericInput.class.getFields();
            g.lastField = fields[g.pick(fields.length)];
            return g.lastField;
        }
        if (t == java.lang.reflect.Type.class) {
            if (g.lastField != null && g.pick(3) == 0)
                return g.lastField.getDeclaringClass();
            Field[] fields = GenericInput.class.getFields();
            return fields[g.pick(fields.length)].getGenericType();
        }
        if (t == java.io.Reader.class || t == java.io.BufferedReader.class
                || t == java.io.StringReader.class) {
            String content = "header,value\n" + string(g) + "," + number(g) + "\n"
                + "alpha,beta\n";
            StringReader reader = new StringReader(content);
            return t == java.io.BufferedReader.class ? new BufferedReader(reader) : reader;
        }
        if (t.getName().equals("org.apache.commons.csv.CSVFormat")) {
            Class<?> format = load("org.apache.commons.csv.CSVFormat");
            for (String fieldName : new String[]{"DEFAULT", "RFC4180", "EXCEL"}) try {
                Object result = format.getField(fieldName).get(null);
                if (t.isInstance(result)) return result;
            } catch (ReflectiveOperationException ignored) { }
            for (Method factory : format.getMethods())
                if (Modifier.isStatic(factory.getModifiers()) && factory.getParameterTypes().length == 0
                        && t.isAssignableFrom(factory.getReturnType())) try {
                    return factory.invoke(null);
                } catch (ReflectiveOperationException ignored) { }
        }
        if (t.getName().equals("com.google.javascript.jscomp.SourceFile")) {
            Class<?> source = load("com.google.javascript.jscomp.SourceFile");
            for (Method factory : source.getMethods())
                if (Modifier.isStatic(factory.getModifiers()) && factory.getName().equals("fromCode")
                        && factory.getParameterTypes().length == 2 && factory.getParameterTypes()[0] == String.class
                        && factory.getParameterTypes()[1] == String.class) {
                    String replaySource = System.getProperty("de.generated.source");
                    if (replaySource != null) {
                        try {
                            report.generatedSource = replaySource;
                            return factory.invoke(null, "de-input.js", replaySource);
                        } catch (ReflectiveOperationException ignored) { }
                    }
                    String[] unusedParameterScripts = {
                        "window.f = function(a) {};",
                        "window.f = function(a, b) { return b; };",
                        "window.f = function(a, b) { var used = b; return used; };",
                        "window['f'] = function(unused) {};",
                        "window.f = function(unused, value) { return value; };",
                        "window['f'] = function(unused, value) { return value; };",
                        "window.f = function(first, unused, last) { return last; };",
                        "window.f = function(unused) { var local = 1; return local; };",
                        "window.f = function(unused, value) { var alias = value; return alias; };",
                        "window.f = function(unused, value) { if (value) { return 1; } return 2; };",
                        "window.f = function(unused, value) { value = value + 1; return value; };",
                        "window.f = function(unused, value) { return function() { return value; }; };",
                        "window.f = function(unused) { function inner() { return 1; } return inner(); };",
                        "window.f = function(a, b, unused) { return a + b; };",
                        "window.f = function(a, unused, b, c) { return a + c; };"
                    };
                    String[] catchDependencyScripts = {
                        "window.f = function() { var saved; try { throw Error('x'); } catch (caught) { saved = caught; } return saved.stack; };",
                        "window.f = function(flag) { var saved; try { if (flag) throw Error('x'); } catch (caught) { saved = caught; } return saved.message; };",
                        "window.f = function() { var saved; try { throw Error('x'); } catch (caught) { saved = caught; } return saved.name; };",
                        "window.f = function() { var saved; try { throw Error('x'); } catch (caught) { saved = caught; } return String(saved); };",
                        "window.f = function(flag) { var saved; try { if (flag) throw Error('x'); } catch (problem) { saved = problem; } return saved.stack; };",
                        "window.f = function() { var saved; try { throw Error('x'); } catch (problem) { saved = problem; } return saved.message; };"
                    };
                    String[] genericScripts = {
                        "function f(unused, used) { var local = 1; return used; } f(1, 2);",
                        "function f() { var unused = 1; var used = 2; return used; } f();",
                        "function f(x) { var first = x; first = 3; return x; } f(2);",
                        "function f() { var unused = 1; } f();",
                        "function keep() { var dead = 1; return 7; } keep();"
                    };
                    String modified = System.getProperty("de.modified.classes", "");
                    String[] scripts;
                    if (modified.contains("RemoveUnusedVars") && modified.contains("FlowSensitiveInlineVariables")) {
                        scripts = new String[unusedParameterScripts.length + catchDependencyScripts.length];
                        System.arraycopy(unusedParameterScripts, 0, scripts, 0, unusedParameterScripts.length);
                        System.arraycopy(catchDependencyScripts, 0, scripts, unusedParameterScripts.length,
                            catchDependencyScripts.length);
                    }
                    else if (modified.contains("FlowSensitiveInlineVariables")) scripts = catchDependencyScripts;
                    else if (modified.contains("RemoveUnusedVars")) scripts = unusedParameterScripts;
                    else scripts = genericScripts;
                    try {
                        String code = scripts[g.pick(scripts.length)];
                        report.generatedSource = code;
                        return factory.invoke(null, "de-input.js", code);
                    }
                    catch (ReflectiveOperationException ignored) { }
                }
        }
        if (t.getName().equals("com.google.javascript.jscomp.CompilerOptions")) {
            try {
                Object options = t.getConstructor().newInstance();
                // In Closure Compiler, unused-variable passes are enabled by
                // CompilationLevel, rather than by a CompilerOptions enum
                // setter. Apply the real public configuration when available.
                try {
                    Class<?> levelType = load("com.google.javascript.jscomp.CompilationLevel");
                    Object advanced = levelType.getField("ADVANCED_OPTIMIZATIONS").get(null);
                    for (Method configure : levelType.getMethods())
                        if (configure.getName().equals("setOptionsForCompilationLevel")
                                && configure.getParameterTypes().length == 1
                                && configure.getParameterTypes()[0].isInstance(options)) {
                            configure.invoke(advanced, options); break;
                        }
                } catch (ReflectiveOperationException ignored) { }
                // Also set the relevant options directly for Closure releases
                // whose compilation-level helper no longer enables this pass.
                for (Class<?> current = t; current != null; current = current.getSuperclass())
                    for (Field field : current.getDeclaredFields()) {
                        String name = field.getName().toLowerCase(Locale.ROOT);
                        if (field.getType() == boolean.class && name.equals("removeglobals")) try {
                            // Closure-1 specifically guards argument removal
                            // when globals are preserved. Keep the optimization
                            // pass enabled while exercising that configuration.
                            field.setAccessible(true); field.setBoolean(options, false);
                        } catch (Exception ignored) { }
                        if (field.getType() == boolean.class
                                && (name.contains("removeunusedvar") || name.contains("removeunusedlocal"))) try {
                            field.setAccessible(true); field.setBoolean(options, true);
                        } catch (Exception ignored) { }
                    }
                for (Method setter : t.getMethods()) {
                    if (!Modifier.isPublic(setter.getModifiers()) || !setter.getName().startsWith("set")
                            || setter.getParameterTypes().length != 1) continue;
                    String name = setter.getName().toLowerCase(Locale.ROOT);
                    if (setter.getParameterTypes()[0] == boolean.class && name.contains("removeglobals")) {
                        try { setter.invoke(options, false); } catch (ReflectiveOperationException ignored) { }
                    } else if (setter.getParameterTypes()[0] == boolean.class
                            && (name.contains("removeunusedvar") || name.contains("removeunusedlocal"))) {
                        try { setter.invoke(options, true); } catch (ReflectiveOperationException ignored) { }
                    } else if (name.contains("optimizationlevel")
                            && setter.getParameterTypes()[0].isEnum()) {
                        Object[] values = setter.getParameterTypes()[0].getEnumConstants();
                        for (Object value : values) if (String.valueOf(value).contains("ADVANCED"))
                            try { setter.invoke(options, value); } catch (ReflectiveOperationException ignored) { }
                    }
                }
                return options;
            } catch (ReflectiveOperationException ignored) { }
        }
        if (t.getName().equals("com.google.javascript.rhino.Node")) {
            try {
                Class<?> ir = load("com.google.javascript.rhino.IR");
                for (String factoryName : new String[]{"script", "root", "name", "string"})
                    for (Method factory : ir.getMethods())
                        if (Modifier.isStatic(factory.getModifiers()) && factory.getName().equals(factoryName)
                                && factory.getParameterTypes().length == 0 && t.isAssignableFrom(factory.getReturnType()))
                            return factory.invoke(null);
            } catch (ReflectiveOperationException ignored) { }
        }
        if (t.getName().equals("com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser")) {
            Object parser = xmlParser(g, report);
            if (parser != null && t.isInstance(parser)) return parser;
        }
        if (t.getName().equals("com.fasterxml.jackson.databind.ser.BeanPropertyWriter")
                || t.getName().equals("com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter")) {
            Object writer = jacksonWriter(t, g, report);
            if (writer != null && t.isInstance(writer)) return writer;
        }
        if (t == java.awt.Graphics2D.class || t == java.awt.Graphics.class)
            return new java.awt.image.BufferedImage(80, 80, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
        if (java.awt.Paint.class.isAssignableFrom(t)) {
            java.awt.Color c = new java.awt.Color(g.pick(256), g.pick(256), g.pick(256));
            if (t.isInstance(c)) return c;
        }
        if (java.awt.Stroke.class.isAssignableFrom(t)) {
            java.awt.BasicStroke s = new java.awt.BasicStroke(g.pick(10) / 2.0f);
            if (t.isInstance(s)) return s;
        }
        if (java.awt.Shape.class.isAssignableFrom(t)) {
            java.awt.Shape s = new java.awt.geom.Rectangle2D.Double(g.next(), g.next(), g.pick(80), g.pick(80));
            if (t.isInstance(s)) return s;
        }
        if (t == java.awt.geom.Point2D.class) return new java.awt.geom.Point2D.Double(g.next(), g.next());
        if (t.getName().equals("org.apache.commons.cli.CommandLine"))
            return commandLine(g, report);
        Object domain = chart(t, g, report);
        if (domain != null) return domain;
        Object language = language(t, g, report);
        if (language != null) return language;
        List<Constructor<?>> ctors = constructors(t);
        // Older Java libraries often use public singleton constants in place of enums.
        if (ctors.isEmpty()) {
            List<Field> constants = new ArrayList();
            for (Field field : t.getFields())
                if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())
                        && t.isAssignableFrom(field.getType())) constants.add(field);
            Collections.sort(constants, new Comparator<Field>() {
                public int compare(Field a, Field b) { return a.getName().compareTo(b.getName()); }
            });
            if (!constants.isEmpty()) {
                Object constant = constants.get(g.pick(constants.size())).get(null);
                if (constant != null) return constant;
            }
        }
        if (!ctors.isEmpty()) {
            Constructor<?> ctor = ctors.get(g.pick(ctors.size()));
            try { return ctor.newInstance(arguments(ctor.getParameterTypes(), g, depth + 1, report)); }
            catch (Exception ignored) { }
        }
        report.nullFallbacks++;
        return null;
    }
    private static Object language(Class<?> t, Genes g, Observation report) {
        if (!t.getName().equals("org.apache.commons.lang3.time.FastDateFormat")) return null;
        try {
            Method factory = t.getMethod("getInstance", String.class, java.util.TimeZone.class,
                java.util.Locale.class);
            String[] patterns = {"yyyy-MM-dd", "MM/dd/yy HH:mm:ss", "EEE, d MMM yyyy HH:mm:ss Z"};
            return factory.invoke(null, patterns[g.pick(patterns.length)],
                java.util.TimeZone.getTimeZone("UTC"), java.util.Locale.US);
        } catch (ReflectiveOperationException ignored) { }
        try { return t.getMethod("getInstance", String.class).invoke(null, "yyyy-MM-dd"); }
        catch (ReflectiveOperationException ignored) { return null; }
    }
    private static Object xmlParser(Genes g, Observation report) {
        try {
            Class<?> factoryType = load("com.fasterxml.jackson.dataformat.xml.XmlFactory");
            Object factory = factoryType.getConstructor().newInstance();
            String[] docs = {"<root><value>1</value><name>x</name></root>",
                "<root value=\"42\"><item>a</item><item>b</item></root>",
                "<root/>"};
            String xml = docs[g.pick(docs.length)];
            for (Method method : factoryType.getMethods()) {
                if (!method.getName().equals("createParser") || method.getParameterTypes().length != 1) continue;
                Class<?> p = method.getParameterTypes()[0];
                Object input = p == String.class ? xml : p == Reader.class ? new StringReader(xml)
                    : p == InputStream.class ? new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)) : null;
                if (input == null) continue;
                try {
                    Object parser = method.invoke(factory, input);
                    if (parser != null) {
                        int advance = g.pick(4);
                        Method next = parser.getClass().getMethod("nextToken");
                        for (int i = 0; i < advance; i++) if (next.invoke(parser) == null) break;
                        return parser;
                    }
                } catch (ReflectiveOperationException ignored) { }
            }
        } catch (Throwable ignored) { }
        return null;
    }
    private static Object jacksonWriter(Class<?> requested, Genes g, Observation report) {
        try {
            Class<?> mapperType = load("com.fasterxml.jackson.databind.ObjectMapper");
            Object mapper = mapperType.getConstructor().newInstance();
            Object bean = new GenericInput();
            Class<?> javaTypeType = load("com.fasterxml.jackson.databind.JavaType");
            Object javaType = mapperType.getMethod("constructType", Type.class).invoke(mapper, bean.getClass());
            // Jackson 2.6 (used by this Defects4J project) exposes a provider
            // blueprint from ObjectMapper. Create a configured provider via
            // DefaultSerializerProvider, the supported API used by ObjectMapper.
            Object providerBlueprint = mapperType.getMethod("getSerializerProvider").invoke(mapper);
            Class<?> serializationConfigType = load("com.fasterxml.jackson.databind.SerializationConfig");
            Class<?> serializerFactoryType = load("com.fasterxml.jackson.databind.ser.SerializerFactory");
            Object config = mapperType.getMethod("getSerializationConfig").invoke(mapper);
            Object factory = mapperType.getMethod("getSerializerFactory").invoke(mapper);
            Class<?> defaultProviderType = load("com.fasterxml.jackson.databind.ser.DefaultSerializerProvider");
            Method createProvider = defaultProviderType.getMethod("createInstance",
                serializationConfigType, serializerFactoryType);
            Object provider = createProvider.invoke(providerBlueprint, config, factory);
            g.jacksonBean = bean;
            g.jacksonProvider = provider;
            g.jacksonOutput = new StringWriter();
            Object jsonFactory = mapperType.getMethod("getFactory").invoke(mapper);
            for (String factoryMethod : new String[]{"createGenerator", "createJsonGenerator"}) {
                try {
                    Method createGenerator = jsonFactory.getClass().getMethod(factoryMethod, Writer.class);
                    g.jacksonGenerator = createGenerator.invoke(jsonFactory, g.jacksonOutput);
                    break;
                } catch (NoSuchMethodException ignored) { }
            }
            if (g.jacksonGenerator == null)
                throw new NoSuchMethodException("JsonFactory.createGenerator(Writer) or createJsonGenerator(Writer)");
            Class<?> providerType = load("com.fasterxml.jackson.databind.SerializerProvider");
            Class<?> beanPropertyType = load("com.fasterxml.jackson.databind.BeanProperty");
            Method find = providerType.getMethod("findValueSerializer", javaTypeType, beanPropertyType);
            Object serializer = find.invoke(provider, new Object[]{javaType, null});
            List<Object> writers = new ArrayList();
            try {
                // Available on Jackson 2.6 and newer.
                Class<?> serializerType = load("com.fasterxml.jackson.databind.JsonSerializer");
                Method properties = serializerType.getMethod("properties");
                Iterator<?> it = (Iterator<?>)properties.invoke(serializer);
                while (it.hasNext()) {
                    Object item = it.next();
                    if (item != null && item.getClass().getName().endsWith("BeanPropertyWriter"))
                        writers.add(item);
                }
            } catch (NoSuchMethodException oldJackson) {
                // JacksonDatabind-1 predates JsonSerializer.properties(). Its
                // BeanSerializerBase stores writers in the protected _props
                // array; read that array only for these old releases.
                Class<?> base = load("com.fasterxml.jackson.databind.ser.std.BeanSerializerBase");
                if (!base.isInstance(serializer))
                    throw new IllegalStateException("Expected BeanSerializerBase, got "
                        + serializer.getClass().getName(), oldJackson);
                Field props = base.getDeclaredField("_props");
                props.setAccessible(true);
                Object array = props.get(serializer);
                for (int i = 0; i < Array.getLength(array); i++) {
                    Object item = Array.get(array, i);
                    if (item != null && item.getClass().getName().endsWith("BeanPropertyWriter"))
                        writers.add(item);
                }
            }
            if (writers.isEmpty())
                throw new IllegalStateException("ObjectMapper produced no bean property writers for DEReplay.GenericInput");
            Object writer = writers.get(g.pick(writers.size()));
            boolean requireUnwrapping = requested.getName().equals(
                "com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter");
            if (!requireUnwrapping && g.pick(2) == 0 && requested.isInstance(writer)) return writer;
            Class<?> transformerType = load("com.fasterxml.jackson.databind.util.NameTransformer");
            Object nop = transformerType.getField("NOP").get(null);
            for (Method m : writer.getClass().getMethods())
                if (m.getName().equals("unwrappingWriter") && m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0].isInstance(nop)) {
                    Object unwrapped = m.invoke(writer, nop);
                    if (requested.isInstance(unwrapped)) return unwrapped;
                }
            if (requested.isInstance(writer)) return writer;
            throw new IllegalStateException("Generated Jackson property writer is not " + requested.getName());
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot construct Jackson BeanPropertyWriter: " + failure, failure);
        }
    }
    /** Build the non-constructible Commons CLI result through its public Parser API. */
    private static Object commandLine(Genes g, Observation report) throws Exception {
        Class<?> optionsClass = load("org.apache.commons.cli.Options");
        Class<?> optionClass = load("org.apache.commons.cli.Option");
        Class<?> parserClass = load("org.apache.commons.cli.PosixParser");
        Object options = optionsClass.getConstructor().newInstance();
        Method addOption = optionsClass.getMethod("addOption", optionClass);
        int numberOfOptions = 1 + g.pick(3);
        List<String> spellings = new ArrayList();
        for (int i = 0; i < numberOfOptions; i++) {
            String shortName = String.valueOf((char)('a' + i));
            String longName = "de-option-" + i;
            boolean hasArgument = g.pick(2) == 0;
            Object option = null;
            try {
                option = optionClass.getConstructor(String.class, String.class, boolean.class, String.class)
                    .newInstance(shortName, longName, hasArgument, "DE-generated option");
            } catch (NoSuchMethodException ignored) { }
            if (option == null) try {
                option = optionClass.getConstructor(String.class, boolean.class, String.class)
                    .newInstance(shortName, hasArgument, "DE-generated option");
            } catch (NoSuchMethodException ignored) { }
            if (option == null) throw new NoSuchMethodException("No supported Commons CLI Option constructor");
            addOption.invoke(options, option);
            spellings.add("-" + shortName);
            if (hasArgument) spellings.add("value-" + Math.abs((long)g.next()));
        }
        String[] argv = spellings.toArray(new String[0]);
        Object parser = parserClass.getConstructor().newInstance();
        List<Method> parseMethods = new ArrayList();
        for (Method candidate : parserClass.getMethods()) {
            Class<?>[] p = candidate.getParameterTypes();
            if (candidate.getName().equals("parse") && p.length >= 2 && p[0] == optionsClass
                    && p[1] == String[].class && candidate.getReturnType() == load("org.apache.commons.cli.CommandLine"))
                parseMethods.add(candidate);
        }
        Collections.sort(parseMethods, new Comparator<Method>() {
            public int compare(Method a, Method b) {
                return a.getParameterTypes().length - b.getParameterTypes().length;
            }
        });
        for (Method parse : parseMethods) {
            Object[] args = new Object[parse.getParameterTypes().length];
            Class<?>[] p = parse.getParameterTypes();
            args[0] = options; args[1] = argv;
            for (int i = 2; i < p.length; i++) {
                if (p[i] == boolean.class || p[i] == Boolean.class) args[i] = g.pick(2) == 0;
                else if (p[i] == java.util.Properties.class) args[i] = new java.util.Properties();
                else args[i] = value(p[i], g, 1, report);
            }
            try { return parse.invoke(parser, args); }
            catch (InvocationTargetException ignored) { }
        }
        throw new NoSuchMethodException("No successful public PosixParser.parse(Options,String[])");
    }
    /** Optional type recipes, shared across bugs; none contains a bug-specific expected answer. */
    private static Object chart(Class<?> t, Genes g, Observation report) throws Exception {
        String n = t.getName();
        if (!n.startsWith("org.jfree.")) return null;
        if (n.equals("org.jfree.data.Range")) {
            double a = g.next(), b = g.next();
            return t.getConstructor(double.class, double.class).newInstance(Math.min(a, b), Math.max(a, b));
        }
        if (n.equals("org.jfree.data.time.RegularTimePeriod"))
            return load("org.jfree.data.time.Day").getConstructor(int.class, int.class, int.class)
                .newInstance(1 + g.pick(28), 1 + g.pick(12), 1990 + g.pick(40));
        if (n.equals("org.jfree.data.time.TimeSeries")) {
            Object series = t.getConstructor(Comparable.class).newInstance("DE");
            Class<?> period = load("org.jfree.data.time.RegularTimePeriod");
            Constructor<?> day = load("org.jfree.data.time.Day")
                .getConstructor(int.class, int.class, int.class);
            Method add = t.getMethod("add", period, double.class);
            int count = 2 + g.pick(4), year = 1990 + g.pick(40);
            for (int i = 0; i < count; i++)
                add.invoke(series, day.newInstance(i + 1, 1, year), g.next() / 10.0);
            return series;
        }
        if (n.equals("org.jfree.data.category.CategoryDataset")
                || n.equals("org.jfree.data.category.DefaultCategoryDataset")) {
            Class<?> c = load("org.jfree.data.category.DefaultCategoryDataset");
            Object data = c.getConstructor().newInstance();
            Method add = c.getMethod("addValue", Number.class, Comparable.class, Comparable.class);
            int rows = 1 + g.pick(3), columns = 1 + g.pick(3);
            for (int r = 0; r < rows; r++) for (int col = 0; col < columns; col++)
                add.invoke(data, Double.valueOf(g.next() / 10.0), "R" + r, "C" + col);
            return data;
        }
        if (n.equals("org.jfree.data.xy.XYDataset") || n.equals("org.jfree.data.xy.XYSeriesCollection")) {
            Class<?> seriesClass = load("org.jfree.data.xy.XYSeries");
            Object series = seriesClass.getConstructor(Comparable.class).newInstance("DE");
            int count = 1 + g.pick(5);
            for (int i = 0; i < count; i++) seriesClass.getMethod("add", double.class, double.class)
                .invoke(series, (double)i, g.next() / 10.0);
            Class<?> c = load("org.jfree.data.xy.XYSeriesCollection");
            Object data = c.getConstructor().newInstance();
            c.getMethod("addSeries", seriesClass).invoke(data, series);
            return data;
        }
        if (n.equals("org.jfree.data.general.PieDataset") || n.equals("org.jfree.data.general.DefaultPieDataset")) {
            Class<?> c = load("org.jfree.data.general.DefaultPieDataset");
            Object data = c.getConstructor().newInstance();
            int count = 1 + g.pick(5);
            for (int i = 0; i < count; i++) c.getMethod("setValue", Comparable.class, Number.class)
                .invoke(data, "K" + i, Double.valueOf(g.next() / 10.0));
            return data;
        }
        return null;
    }
    static List<Method> setupMethods(Class<?> receiver) {
        List<Method> methods = new ArrayList();
        for (Method m : receiver.getMethods()) {
            String n = m.getName();
            if (!Modifier.isStatic(m.getModifiers()) && !m.isSynthetic()
                    && m.getParameterTypes().length <= 3 && !n.contains("Listener")
                    && (n.startsWith("set") || n.startsWith("add") || n.startsWith("update")
                        || n.startsWith("remove") || n.equals("clear"))) methods.add(m);
        }
        Collections.sort(methods, new Comparator<Method>() {
            public int compare(Method a, Method b) {
                return signature(a).compareTo(signature(b));
            }
        });
        return methods;
    }
    public static Observation execute(String target, String receivers, String signature, int[] genes) {
        Observation out = new Observation();
        try {
            Genes g = new Genes(genes);
            Method m = method(target, signature);
            Object receiver = null;
            if (!Modifier.isStatic(m.getModifiers())) {
                String[] choices = receivers.split(",");
                Class<?> receiverType = load(choices[g.pick(choices.length)]);
                receiver = value(receiverType, g, 0, out);
                if (receiver == null) throw new IllegalArgumentException("Receiver construction failed");
                // Compiler.compile owns a strict initialization sequence.
                // Random calls to its mutators before compilation can corrupt
                // state or spend the search budget on irrelevant setup.
                if (!receiverType.getName().equals("com.google.javascript.jscomp.Compiler")) {
                    List<Method> setup = setupMethods(receiverType);
                    int count = g.pick(5);
                    for (int i = 0; i < count && !setup.isEmpty(); i++) {
                        Method s = setup.get(g.pick(setup.size()));
                        try { s.invoke(receiver, arguments(s.getParameterTypes(), g, 0, out)); out.setupCalls++; }
                        catch (Exception e) { out.setupFailures++; }
                    }
                }
            }
            Object[] args = arguments(m, g, 0, out);
            out.reachedTarget = true;
            try {
                boolean jacksonSerialization = m.getDeclaringClass().getName().equals(
                    "com.fasterxml.jackson.databind.ser.BeanPropertyWriter")
                    && m.getName().startsWith("serializeAs") && g.jacksonGenerator != null;
                boolean arrayShape = m.getName().contains("Column")
                    || m.getName().contains("Element") || m.getName().contains("Placeholder");
                if (jacksonSerialization)
                    g.jacksonGenerator.getClass().getMethod(arrayShape
                        ? "writeStartArray" : "writeStartObject").invoke(g.jacksonGenerator);
                Object result = m.invoke(receiver, args);
                boolean closureCompile = m.getDeclaringClass().getName().equals(
                    "com.google.javascript.jscomp.Compiler") && m.getName().equals("compile");
                if (closureCompile) {
                    // Result is only a status object; the compiled JavaScript is
                    // the behavioral output that reveals whether an argument
                    // was removed from a globally exposed function.
                    Object js = receiver.getClass().getMethod("toSource").invoke(receiver);
                    out.token = "CLOSURE_SOURCE:" + stable(js, 0);
                } else if (jacksonSerialization) {
                    g.jacksonGenerator.getClass().getMethod(arrayShape
                        ? "writeEndArray" : "writeEndObject").invoke(g.jacksonGenerator);
                    g.jacksonGenerator.getClass().getMethod("flush").invoke(g.jacksonGenerator);
                    out.token = "JSON:" + Base64.getEncoder().encodeToString(
                        g.jacksonOutput.toString().getBytes(StandardCharsets.UTF_8));
                } else out.token = m.getReturnType() == void.class
                    ? state(receiver, m.getName()) : stable(result, 0);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof VirtualMachineError || e.getCause() instanceof LinkageError
                        || e.getCause() instanceof ThreadDeath) throw e;
                out.token = "THROW:" + e.getCause().getClass().getName();
            }
        } catch (Throwable e) {
            out.token = "HARNESS_ERROR";
            out.error = e.getClass().getName() + ":" + String.valueOf(e.getMessage());
        }
        return out;
    }
    public static String run(String target, String receivers, String signature, int[] genes) {
        Observation o = execute(target, receivers, signature, genes);
        if (!o.reachedTarget || o.token.equals("HARNESS_ERROR"))
            throw new AssertionError("Cannot replay test: " + o.error);
        return o.token;
    }
    private static String state(Object receiver, String method) {
        if (receiver == null) return "VOID";
        List<String> getters = new ArrayList();
        if (method.startsWith("set") && method.length() > 3) {
            getters.add("get" + method.substring(3)); getters.add("is" + method.substring(3));
        }
        getters.addAll(Arrays.asList("getItemCount", "getRowCount", "getColumnCount", "getSeriesCount"));
        StringBuilder s = new StringBuilder("VOID");
        for (String name : getters) {
            try {
                Method getter = receiver.getClass().getMethod(name);
                if (getter.getReturnType().isPrimitive() || getter.getReturnType() == String.class)
                    s.append('|').append(name).append('=').append(stable(getter.invoke(receiver), 0));
            } catch (ReflectiveOperationException ignored) { }
        }
        return s.toString();
    }
    /** Only whitelisted value types are rendered. Never use arbitrary object toString(). */
    static String stable(Object value, int depth) {
        if (value == null) return "NULL";
        Class<?> t = value.getClass();
        if (value instanceof String || value instanceof Boolean || value instanceof Character
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof java.math.BigInteger || value instanceof java.math.BigDecimal)
            return t.getName() + ":" + Base64.getEncoder().encodeToString(value.toString().getBytes(StandardCharsets.UTF_8));
        if (value instanceof Enum) return "ENUM:" + t.getName() + ":" + ((Enum<?>)value).name();
        if (t.isArray() && depth < 3) {
            StringBuilder s = new StringBuilder("ARRAY:" + t.getName() + ":" + Array.getLength(value));
            for (int i = 0; i < Math.min(64, Array.getLength(value)); i++) {
                String item = stable(Array.get(value, i), depth + 1);
                s.append(':').append(item.length()).append(':').append(item);
            }
            return s.toString();
        }
        if (value instanceof java.awt.Color) return "COLOR:" + ((java.awt.Color)value).getRGB();
        // Observe safe scalar properties of returned objects. This catches
        // changes to value caches and state while avoiding identity-based toString().
        StringBuilder observed = new StringBuilder("STATE:" + t.getName());
        int properties = 0;
        for (String name : Arrays.asList("getItemCount", "getMinY", "getMaxY",
                "getRowCount", "getColumnCount", "getSeriesCount")) {
            try {
                Method getter = t.getMethod(name);
                Class<?> r = getter.getReturnType();
                if (!r.isPrimitive() && r != String.class && !Number.class.isAssignableFrom(r))
                    continue;
                if (r == void.class) continue;
                Object result = getter.invoke(value);
                String token = stable(result, depth + 1);
                observed.append('|').append(name).append('=').append(token.length())
                    .append(':').append(token);
                properties++;
            } catch (Exception ignored) { }
        }
        return properties == 0 ? "TYPE:" + t.getName() : observed.toString();
    }
    public static void main(String[] args) {
        if (args.length > 4) {
            String source = new String(Base64.getDecoder().decode(args[4]), StandardCharsets.UTF_8);
            System.setProperty("de.generated.source", source);
        }
        String[] encodedGenes = args[3].split(",");
        int[] genes = new int[encodedGenes.length];
        for (int i = 0; i < encodedGenes.length; i++) genes[i] = Integer.parseInt(encodedGenes[i]);
        boolean closureCompile = args[0].equals("com.google.javascript.jscomp.Compiler")
            && args[2].startsWith("compile(");
        // Compiler.compile is an expensive whole-program operation. Fixed-side
        // suites are still executed twice by the runner, so capture its oracle
        // once here instead of launching three compilations just to check the
        // same deterministic source output.
        int repetitions = closureCompile ? 1 : 3;
        for (int i = 0; i < repetitions; i++) {
            Observation out = execute(args[0], args[1], args[2], genes);
            System.out.println("DE_TOKEN:" + Base64.getEncoder().encodeToString(out.token.getBytes(StandardCharsets.UTF_8)));
        }
    }
}

public class DEGeneratedTest {
    @org.junit.Test(timeout=60000L)
    public void testDE00000() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "canOmitFields():boolean",
            new int[]{-586,463,437,340,-48,-127,662,-37,489,-1000,391,-67,1000,504,1000,-276,-248,-541,-306,-167,-546,-383,-552,244,62,-760,-360,115,1000,2,-166,-574,562,256,-785,-533,134,337,-198,639,-858,562,247,489,-862,-275,409,-328,-303,159,-785,-103,261,472,-268,293,-103,-93,266,935,-523,287,-966,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "canUseSchema(com.fasterxml.jackson.core.FormatSchema):boolean",
            new int[]{-211,28,-184,894,646,38,671,-1000,587,32,400,-555,164,-533,282,767,-680,-810,306,-689,687,-83,837,664,627,520,-101,767,439,-544,605,195,1000,492,-415,476,-669,-480,-585,-205,434,580,467,357,-615,1000,-1000,997,751,-1000,591,-240,234,749,71,-609,-720,490,1000,267,-373,782,859,607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "canUseSchema(com.fasterxml.jackson.core.FormatSchema):boolean",
            new int[]{510,28,87,507,752,-213,-756,28,-487,268,995,-841,612,559,94,-602,-680,-100,-293,20,687,394,837,-931,-27,-114,-265,-445,-224,960,-722,-167,319,-739,-415,69,539,-570,717,408,111,-177,213,-978,361,-130,432,-457,-884,463,-826,-426,996,64,-66,948,389,-640,-761,176,-609,-738,30,152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "canWriteBinaryNatively():boolean",
            new int[]{-947,-852,-433,377,261,212,56,47,372,-577,-131,-168,-653,-120,-380,162,-334,-1000,-808,1000,134,-892,1000,-683,459,-1000,-758,380,-634,-47,-792,-496,168,429,-316,1000,635,149,393,-171,-446,-863,1000,-132,292,-731,938,809,1000,434,570,-116,1000,411,69,-1000,1000,-279,31,-576,983,-1000,-227,678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "canWriteFormattedNumbers():boolean",
            new int[]{749,345,655,-326,-371,35,-379,-121,-122,884,-125,775,119,-1000,461,119,435,-888,341,84,-675,293,167,133,-661,682,-1000,-775,982,323,-43,705,-908,-985,478,-647,1000,391,-883,570,1000,-549,1000,-528,-700,452,1000,564,-282,-290,-1000,58,1000,-724,304,1000,463,240,-110,870,-79,1000,-139,666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "canWriteObjectId():boolean",
            new int[]{-354,-786,-309,352,537,-618,1000,-1000,114,227,722,-984,663,333,-1000,568,-1000,-253,1000,475,-1000,-135,258,1000,17,-871,466,-862,-1000,32,-896,-1000,-233,483,-1000,672,120,-1000,-440,544,-1000,-618,-25,323,-234,867,334,-316,-766,16,41,-201,-605,-751,-474,774,1000,828,725,1000,1000,-83,388,417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "canWriteTypeId():boolean",
            new int[]{90,192,-463,810,-612,177,-1000,382,219,444,-814,-951,1000,901,-617,-285,-587,-741,-1000,-789,-264,583,794,1000,-714,-842,1000,166,176,-1000,537,1000,74,-375,1000,-932,-225,1000,-688,747,-925,281,-714,-524,884,97,72,436,206,1000,1000,927,571,-857,1000,-1000,242,-685,696,870,289,1000,-513,-71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "close():void",
            new int[]{170,446,-870,644,512,-1000,192,-671,-908,634,174,-286,-493,374,-613,195,419,925,731,-142,103,1000,620,-412,-1000,-1000,-613,-67,-255,128,498,1000,433,1000,-1000,-171,1000,-1000,-874,-203,-80,1000,157,-734,-1000,-25,-11,248,186,99,-1000,-696,-24,-546,875,-918,801,-680,-540,-801,-386,667,-336,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "close():void",
            new int[]{-290,729,-821,379,961,-1000,295,-1000,-1000,963,651,214,265,46,-841,-1000,182,1000,318,-1000,-711,303,898,662,16,-1000,-686,31,892,-913,-353,671,-245,1000,-594,-1000,1000,-851,-812,-1000,-550,1000,-631,-1000,-1000,-138,129,-704,1000,97,-514,-1000,879,139,969,-289,-962,-1000,-822,-236,-696,621,-313,-398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.UTF8JsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{-539,711,-220,1000,-1000,-323,-403,1000,-602,-371,-1000,415,219,728,451,741,580,400,324,-411,153,1000,-380,86,1000,1000,1000,-163,-249,1000,-611,106,-469,644,1000,-681,1000,-69,-958,-829,-35,-904,539,-1000,317,-286,-971,-410,109,393,-698,-627,-1000,-330,808,-761,-400,-1000,-1000,18,716,-124,-522,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{-290,-191,1000,-341,227,-1000,752,-1000,-57,879,-701,1000,-274,829,347,-185,786,-688,-931,-669,416,-895,784,749,1000,106,278,-1000,1000,-722,432,-592,-1000,165,-149,308,1000,409,-1000,-700,562,689,-214,-487,-623,868,741,189,1000,-783,658,-36,-1000,-321,808,-28,530,-979,-220,977,1000,-1000,-1000,-559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "copyCurrentEvent(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{785,-61,1000,803,-547,933,-1000,1000,-974,-836,-841,1000,557,917,404,-1000,-683,1000,881,-462,-735,912,1000,966,75,-843,-208,888,156,1000,-1000,400,1000,1000,831,-74,-1000,506,-600,1000,-311,-446,669,-75,-184,439,101,736,1000,370,-1000,206,-348,-1000,-1000,-1000,-358,649,217,-1000,-711,-660,496,-391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "copyCurrentStructure(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{-863,362,518,-310,325,827,-340,-116,310,774,-889,1000,1000,-795,-3,365,115,-457,-538,538,123,114,-1000,889,-486,1000,1000,395,336,-263,1000,384,700,819,1000,-524,-577,664,898,-687,1000,-1000,4,-325,-968,1000,256,-611,-1000,584,88,357,310,660,396,-141,516,1000,-410,-779,-1000,-345,-347,-630}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{222,-712,852,1000,1000,782,-1000,1000,666,-226,-117,-1000,861,429,43,351,-463,-673,1000,1000,759,896,-79,920,104,-701,-654,719,1000,1000,790,-1000,1000,356,-610,984,528,750,44,1000,546,1000,-994,-1000,1000,845,-1000,1000,1000,1000,-181,25,-714,907,1000,-1000,1000,-97,-1000,709,-1000,339,-925,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "enable(com.fasterxml.jackson.core.JsonGenerator$Feature):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{-134,153,-1000,238,312,1000,1000,-902,-83,-1000,269,-239,-967,-812,-630,1000,-403,-129,-293,952,-32,-12,-304,496,187,288,1000,-384,522,-1000,-169,-763,-286,-375,1000,-408,-1000,1000,-1000,117,887,1000,222,-121,481,197,-192,-287,138,1000,-982,919,-1000,-960,1000,1000,1000,-382,593,145,1000,796,-835,-924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "enable(com.fasterxml.jackson.core.JsonGenerator$Feature):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{782,-464,-1000,-1000,-243,-977,-1000,-335,-457,-257,1000,85,-356,-516,-1000,735,-974,25,-17,553,-155,623,590,815,-1000,943,-138,158,-885,363,-575,-67,-1000,6,861,-9,925,0,-705,803,-1000,-1000,911,280,-780,759,-360,-1000,1000,-949,611,-835,1000,1000,-236,-1000,-1000,-1000,-1000,532,-1000,1000,258,-447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "flush():void",
            new int[]{361,-301,319,674,-254,-447,-42,237,18,-221,183,-795,619,-400,-670,-915,-1000,-233,-640,-400,227,-454,-411,-191,-400,-963,765,-1000,824,-1000,-885,-576,-448,-138,-529,-351,89,-359,842,-104,-366,278,245,243,92,560,404,54,-512,499,-241,-85,312,1000,-118,114,-279,-893,-859,587,354,-549,912,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getCharacterEscapes():com.fasterxml.jackson.core.io.CharacterEscapes",
            new int[]{-791,832,-245,-870,716,-978,-362,-888,-961,873,505,627,407,-679,-947,90,-804,-144,59,967,996,-575,-242,-511,733,-866,-291,-453,-670,873,862,820,-130,459,-756,197,681,-52,470,-867,292,-572,79,773,-112,-243,628,379,471,491,397,868,766,-462,595,135,-366,977,44,240,-335,889,429,491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getCharacterEscapes():com.fasterxml.jackson.core.io.CharacterEscapes",
            new int[]{-738,1000,-719,1000,-229,446,485,61,-991,-217,499,-187,1000,-277,35,-161,-881,-279,-1000,994,1000,732,932,-243,512,1000,-1000,1000,1000,1000,1000,450,112,501,402,700,-987,-943,533,-1000,59,-61,-1000,-1000,-436,1000,265,371,-544,1000,1000,346,-683,-792,-44,-147,751,-56,-718,-466,826,-390,-215,-479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getCodec():com.fasterxml.jackson.core.ObjectCodec",
            new int[]{-630,-835,-796,215,515,-80,1000,1000,658,278,89,336,701,-923,1000,920,90,-86,-807,-1000,1000,1000,837,-937,-319,-128,594,455,858,1000,-621,-1000,-97,687,-459,506,-1000,-606,-527,-840,1000,-953,-116,-1000,957,954,607,-1000,325,587,-1000,272,876,961,213,-110,273,168,-497,-19,-1000,54,-899,395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getCodec():com.fasterxml.jackson.core.ObjectCodec",
            new int[]{206,1000,-660,-1000,334,-305,-251,-1000,-664,-491,-261,386,-773,-924,80,312,177,-41,873,1000,-1000,-1000,-666,1000,-1000,851,-969,-1000,-661,-574,39,959,-921,-746,-401,616,-772,-660,1000,1000,-1000,-169,491,1000,806,246,328,-72,680,-459,-296,486,-1000,168,-523,-110,-652,-68,142,899,252,-541,-532,-151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getCurrentValue():java.lang.Object",
            new int[]{-666,-636,-924,333,-734,-749,-605,-1000,-295,339,533,177,-1000,43,605,-251,1000,1000,909,-952,-547,722,-333,-1000,-268,484,-226,613,361,471,932,763,-110,228,693,-510,-201,651,1000,-570,1000,33,-1000,-913,287,148,-112,-900,77,-342,-538,564,-248,-259,148,36,-143,-128,735,658,506,380,727,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getCurrentValue():java.lang.Object",
            new int[]{-555,1000,423,-53,395,-1000,15,-1000,-938,624,966,815,-1000,-168,617,-196,276,1000,-200,-1000,-298,129,803,-1000,412,246,-397,827,1000,-1000,-304,768,-423,-761,616,129,-531,-255,400,186,1000,867,-746,353,-319,-805,887,-945,74,223,-342,1000,684,-236,391,617,-107,-622,100,402,1000,-278,-25,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTQxNg==", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getFeatureMask():int",
            new int[]{-562,1000,-99,-430,-204,-688,-416,191,734,799,461,-618,777,-222,1000,-39,-341,-22,1000,800,-349,-287,410,-613,371,303,-1000,-89,663,38,-547,-1000,-47,251,1000,-1000,-263,-1000,-1000,-67,-207,-766,-414,-1000,813,-113,1000,-245,-214,1000,-665,-328,-1000,473,452,641,705,150,979,-93,-1000,-211,-78,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getFormatFeatures():int",
            new int[]{-954,1000,-400,618,620,-708,802,-601,-121,-878,655,805,-167,704,-696,-504,-132,-458,-83,-858,1000,-665,-892,1000,739,421,-1000,366,962,-627,-1000,-745,1000,-113,615,824,558,-376,182,434,-8,886,548,293,615,1000,-672,323,-354,-1000,672,590,1000,-439,204,-637,-1000,-204,704,-629,186,-339,1000,308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getHighestEscapedChar():int",
            new int[]{221,-966,60,862,-957,-857,173,851,-736,-225,-891,991,732,335,-536,-877,-1000,936,444,-377,-123,-794,950,871,-1000,-781,500,163,900,151,-674,-233,-800,258,-619,-1000,-173,259,-146,-397,665,-1000,1000,-985,1000,-185,-1000,925,-489,1000,902,905,-1000,-1000,809,-1000,933,1000,709,-116,-1000,1000,758,613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getOutputBuffered():int",
            new int[]{-859,-1000,270,1000,481,1000,-193,-1000,-1000,699,893,731,-329,1000,-1000,663,1000,749,-569,-602,-1000,-1000,-1000,797,-1000,778,-400,-928,-673,-434,871,-209,-982,1000,-1000,867,-1000,1000,1000,4,172,-710,1000,874,-432,1000,-1000,1000,1000,1000,-1000,489,521,-100,884,518,-986,-395,-1000,-187,1000,-287,-247,903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.JsonWriteContext", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getOutputContext():com.fasterxml.jackson.core.JsonStreamContext",
            new int[]{626,-71,462,-1000,-88,308,339,-512,682,-922,549,-384,-801,414,398,-1000,-742,71,-324,366,1000,-746,231,319,997,-504,1000,-1000,289,135,-143,-146,-477,-307,239,-420,14,239,-496,206,-1000,-468,-1000,1000,-220,207,61,696,1000,-119,444,657,603,-849,-771,-255,321,-337,1000,322,-1000,1000,-671,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getOutputTarget():java.lang.Object",
            new int[]{-867,154,1000,608,1000,-720,-598,-58,243,369,495,99,-468,-606,333,-545,825,778,-488,608,628,749,-1000,-577,122,350,-82,374,-300,303,-791,108,882,-238,1000,-280,-956,-862,-775,-326,134,-864,-910,38,-387,873,-1000,1000,405,-1000,335,-301,234,331,226,83,-353,1000,-752,-358,1000,853,-472,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getPrettyPrinter():com.fasterxml.jackson.core.PrettyPrinter",
            new int[]{417,-637,332,-171,-1000,-101,-834,-1000,-215,-901,-121,333,916,1000,748,131,-1000,293,-1000,-1000,-430,1000,40,-20,-447,380,-1000,225,584,1000,589,1000,1000,-203,1000,-1000,116,143,-217,1000,1000,-997,-1,-198,-192,-1000,-402,-1000,-1000,115,-1000,-483,110,632,-727,-492,-149,1000,-811,-1000,729,-1000,940,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "getSchema():com.fasterxml.jackson.core.FormatSchema",
            new int[]{-483,516,-1000,-267,-520,-684,-798,980,814,-621,-833,-531,101,1000,-472,394,39,-606,-1000,-281,731,784,1000,-615,393,-361,-1000,-456,-160,1000,-582,-1000,-674,453,260,859,-356,1000,680,462,989,-507,948,629,-579,793,1000,-285,981,-356,375,418,-643,1000,115,1000,-555,173,-1000,-551,880,-229,-1000,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "isClosed():boolean",
            new int[]{666,-1000,-891,-361,-901,647,1000,130,844,-411,365,-493,-324,-1000,959,734,-976,1000,-1000,-316,-1000,-1000,-292,670,-1000,-1000,1000,481,-1000,-1000,-469,-617,1000,-1000,-527,355,215,472,1000,-1000,1000,316,-997,1000,186,119,-336,-6,-958,-81,-1000,-1000,-311,-577,1000,1000,195,879,-133,1000,-579,-1000,288,179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "isEnabled(com.fasterxml.jackson.core.JsonGenerator$Feature):boolean",
            new int[]{961,-537,704,647,508,808,-688,-1000,-758,-171,-497,-211,596,-646,-148,-550,-711,-21,-198,135,-482,569,389,937,29,-319,625,210,662,1000,-197,-106,-369,-740,-895,847,-872,1000,-473,-677,-238,-402,-159,166,495,-99,753,243,1000,-445,150,134,118,-669,-902,724,-1000,-793,38,-523,-935,1000,-597,800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "overrideFormatFeatures(int,int):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{481,-1000,-370,-665,-817,-139,-423,-240,263,244,30,-106,925,-857,-555,710,-1000,-945,1000,-4,-819,-656,-785,-609,121,1000,-1000,-689,893,500,290,-236,-1000,958,-170,1000,-817,-417,-73,-552,681,740,921,771,1000,750,603,-412,-481,-925,-83,20,781,-1000,-558,282,1000,-727,1000,130,515,964,-658,-717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.UTF8JsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "overrideStdFeatures(int,int):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{-551,-126,-795,216,503,-102,-717,-1000,-1000,-501,469,47,727,1000,279,-270,-812,-1000,923,-570,1000,-688,-1000,872,-53,-124,-874,201,-18,894,-279,684,88,630,-1000,77,383,-269,188,43,279,984,-1000,583,-297,745,487,755,-868,842,858,259,-45,-71,-518,224,542,585,-128,-417,-599,1000,139,-447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "setCharacterEscapes(com.fasterxml.jackson.core.io.CharacterEscapes):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{194,141,-623,-903,326,157,-186,-448,-148,-157,-561,-325,-711,1000,877,-552,-828,-1000,459,153,-160,-753,1000,885,1000,932,946,-73,63,454,-331,-910,1000,233,-348,143,722,159,-1000,-295,356,-514,-1000,1000,-457,973,1000,977,799,-1000,866,-218,-1000,-998,-1000,1000,517,661,-356,159,538,-165,-656,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "setCodec(com.fasterxml.jackson.core.ObjectCodec):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{682,-1000,48,624,-345,-3,-848,-76,373,-249,899,-681,964,29,-257,30,-753,-47,74,1000,159,1000,1000,-651,-694,-1000,11,-703,-198,588,-39,-883,86,1000,-1000,-462,129,-422,-1000,1000,-1000,-568,-1000,103,-1000,-1000,964,-522,-916,-1000,-1000,1000,477,-1000,299,-1000,1000,-1000,-418,1000,-345,-927,-1000,302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "setCurrentValue(java.lang.Object):void",
            new int[]{-486,-203,560,448,-438,937,95,-17,-776,-78,347,-75,-260,-236,-452,-155,-685,-960,557,-559,363,309,74,827,-137,-723,97,-991,-119,132,-199,-32,-165,385,-610,-261,781,-962,-113,-632,651,910,316,-320,179,-424,288,454,57,-507,-404,743,780,655,-886,872,-444,362,-978,-72,-274,560,-811,-540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "setCurrentValue(java.lang.Object):void",
            new int[]{909,-853,-552,771,865,-497,6,-890,154,668,-681,-618,-652,969,784,871,-950,-362,706,80,48,-86,-132,453,-215,-450,-127,33,329,-158,-385,-830,-332,204,619,360,744,-212,404,-227,-620,682,510,-889,-995,812,384,-951,915,826,-74,-281,-295,-24,170,-576,457,-718,-680,116,-709,104,137,-984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "setFeatureMask(int):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{182,-633,685,465,-960,254,-672,391,-408,-819,79,-740,-127,157,392,536,-716,674,-870,231,366,-195,-453,-286,-106,-42,54,792,-239,-439,-948,738,786,-18,101,638,465,-187,56,886,962,-751,-550,955,182,795,-647,669,635,-270,219,-859,-17,892,-163,-781,-341,-818,-637,-108,-253,-90,677,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.UTF8JsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "setHighestNonEscapedChar(int):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{-191,783,-368,1000,-17,-1000,418,668,-20,889,807,29,-145,-61,433,-1000,160,-450,-914,73,465,937,-96,-277,-1000,-128,-1000,-960,776,1000,1000,-81,1000,56,-1000,-1000,1000,118,-119,122,-532,777,324,-747,515,-383,-955,1000,28,422,1000,687,345,799,-826,658,-512,-1000,-175,840,80,808,-1000,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.UTF8JsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "setPrettyPrinter(com.fasterxml.jackson.core.PrettyPrinter):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{969,385,-375,-639,671,-661,-806,-695,659,-621,519,176,740,-441,-369,-507,490,901,121,-743,-573,889,-337,889,-310,655,438,160,23,-839,-776,-938,812,617,126,-906,-122,996,184,557,-186,593,-582,-914,-264,292,277,-121,-501,-563,909,-131,686,-964,175,425,-59,-618,-65,69,609,137,-259,192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.UTF8JsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "setRootValueSeparator(com.fasterxml.jackson.core.SerializableString):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{769,645,273,505,-250,479,120,759,514,-567,-535,237,127,554,464,-720,164,-374,203,504,204,921,-264,-25,272,459,469,-751,528,-861,566,634,-935,-481,388,443,-779,-125,-680,919,470,-328,763,-975,-338,-408,-438,-631,140,891,687,-863,-896,971,-407,-884,474,812,-640,629,-528,-825,-547,-474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "setSchema(com.fasterxml.jackson.core.FormatSchema):void",
            new int[]{-795,1000,1000,1000,-156,-163,-759,1000,9,1000,629,979,-16,626,787,646,-528,518,-1000,461,-863,291,-484,748,209,1000,172,73,-688,-433,-771,221,955,577,-1000,-1000,-1000,-544,1000,-1000,820,391,-453,1000,766,1000,-413,-820,-317,-1000,-673,457,780,973,-1000,-725,-1000,55,400,-352,400,-610,-108,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.UTF8JsonGenerator", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "useDefaultPrettyPrinter():com.fasterxml.jackson.core.JsonGenerator",
            new int[]{13,187,-243,-612,835,-552,-227,688,-793,694,655,-31,-250,892,489,1000,351,-508,533,-154,609,-207,-104,469,-679,234,-691,60,-278,140,287,1000,80,22,287,23,1000,1000,-304,917,-62,-179,485,1000,1000,-902,-186,-456,326,1000,210,-675,-909,-109,-1000,-1000,-470,256,616,496,724,-1000,277,-281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "version():com.fasterxml.jackson.core.Version",
            new int[]{509,603,-1000,986,283,705,1000,-47,1000,-73,-309,597,-362,-299,-594,-1000,-31,1000,-227,365,1000,-1000,-295,1000,-283,-32,-867,-594,-790,794,-290,1000,-518,1000,-148,820,-1000,-7,-67,-1000,-757,-795,514,-1000,-927,-283,-1000,-766,144,47,-594,-20,438,-70,-1000,1000,1000,1000,-376,-924,-53,1000,-278,-640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "version():com.fasterxml.jackson.core.Version",
            new int[]{-902,-60,461,40,171,-832,-179,-679,-137,979,730,345,-702,678,247,671,314,-921,-934,-780,765,-601,776,653,314,-739,-89,-216,-174,705,-339,-658,96,-841,173,73,-414,79,674,628,66,-331,-668,-1,-354,-516,-299,-645,520,27,-97,-948,-55,-530,267,315,-533,568,-954,-372,-783,-505,58,-767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(double[],int,int):void",
            new int[]{-994,1000,-242,165,1000,264,-617,-236,-553,4,-1000,631,-72,341,-362,1000,634,-936,1000,960,-1000,677,-832,117,-1000,-116,1000,817,1000,-812,-71,-1000,-663,-1000,-988,-1000,1000,-1000,-103,963,-386,1000,-37,775,354,1000,-47,604,-524,-342,-1000,527,54,-636,1000,-1000,-1000,333,191,-686,78,-316,676,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(double[],int,int):void",
            new int[]{449,-967,1000,408,-1000,-781,573,-164,-38,519,-126,-1000,1000,-1,629,-346,-696,-176,924,-438,440,-1000,1000,657,283,1000,565,-207,-34,-1000,-37,-198,752,186,-495,1000,-1000,457,881,744,-1000,1000,-627,1000,734,138,1000,229,-1000,-1000,357,-1000,1000,-1000,-33,1000,958,133,-308,-201,-357,1000,-245,671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(double[],int,int):void",
            new int[]{642,-146,762,154,-788,-586,-78,121,-379,-509,248,81,930,238,537,-586,-252,-81,892,-251,-912,217,791,307,-161,441,439,20,879,-963,-417,-296,-220,870,-653,466,-554,868,650,396,-940,866,-727,633,276,713,748,-102,-299,-945,-88,-265,416,-391,160,840,548,-825,250,142,-314,166,-167,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(double[],int,int):void",
            new int[]{729,1000,-788,39,72,-21,-674,277,-122,-3,-301,538,399,-17,-954,765,-319,-419,852,61,157,948,-514,342,-60,-935,436,-80,800,463,726,-364,-120,-757,-384,-48,101,307,903,1000,502,-918,-564,1,957,1000,-910,797,1000,-10,-929,1000,-722,-323,1000,31,-197,-591,40,-723,-332,-1000,297,548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(int[],int,int):void",
            new int[]{-662,-71,730,-1000,186,83,-168,-205,544,-888,-430,240,-1000,-859,1000,-397,-1000,1000,123,-264,604,23,719,124,-552,56,481,1000,-802,276,322,-945,450,1000,871,77,-450,1000,-395,1000,-188,-579,1000,-807,-382,-278,379,-193,-391,-331,839,-1000,-185,-695,-783,525,1000,339,-269,230,-1000,118,142,-864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(int[],int,int):void",
            new int[]{-750,875,1000,805,1000,1000,-116,-762,-708,642,-160,-1,-1000,-49,-1000,217,-979,-992,25,1000,-625,1000,1000,361,-631,991,-795,-1000,1000,50,-397,1000,883,-714,1000,1000,1000,-608,1000,389,-237,-1000,316,-941,-320,-569,1000,1000,1000,404,-800,1000,-507,1000,-688,-1000,947,474,-1000,211,1000,-1000,561,-972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(int[],int,int):void",
            new int[]{786,115,103,-227,64,684,-272,-463,-313,994,-966,169,-78,962,-275,-406,-117,-544,-263,-19,749,-468,994,-675,135,499,792,982,355,224,-710,150,-219,349,-746,942,-199,-372,-380,680,-13,478,-277,-728,981,997,89,-823,367,-766,592,-689,-695,256,-533,496,-923,-285,-828,560,854,35,-563,47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(long[],int,int):void",
            new int[]{629,462,492,-1000,-295,709,300,-58,804,788,487,-333,-491,893,221,1000,1000,1000,-210,-451,-1000,-908,-1000,478,-348,1000,160,-1000,-1000,440,-153,858,1000,-1000,810,-965,832,51,311,-1000,524,-1000,-1000,-216,224,-714,-217,-1000,-116,-470,-589,-471,555,564,-1000,-786,-1000,1000,-172,1000,-710,-62,439,-138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(long[],int,int):void",
            new int[]{-799,102,-87,-245,-1000,956,604,313,244,-917,-325,778,-869,184,678,-487,932,-56,139,299,-190,-559,-921,965,475,-121,971,-120,-544,589,441,-623,-217,738,-356,-231,1000,-664,700,-987,-605,-247,-438,-512,1000,1000,489,184,78,-268,-350,-1000,16,1000,187,211,-568,1000,-994,191,-376,1000,-191,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(long[],int,int):void",
            new int[]{-327,624,-849,-898,-312,92,954,-134,719,352,953,130,296,-5,-619,48,-525,836,-447,-446,307,-964,880,802,-166,-154,468,564,850,-835,662,203,-713,-628,-523,-748,67,20,-276,912,-492,-143,817,-152,998,-573,737,532,-475,976,443,117,-134,802,-861,-718,772,316,234,-130,-673,70,573,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArrayFieldStart(java.lang.String):void",
            new int[]{570,-911,-970,-730,-922,-582,488,501,-169,-647,677,121,555,-510,970,866,-389,265,177,191,-612,-500,-391,304,768,-699,148,486,137,815,-326,783,-784,-317,-554,60,225,-955,637,125,333,871,-892,-808,210,74,-259,299,-437,-624,-841,-551,13,217,-495,235,378,134,94,511,-478,-16,241,238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArrayFieldStart(java.lang.String):void",
            new int[]{-274,-956,-1000,-659,-923,-476,1000,-1000,1000,187,-29,-1000,770,127,1000,-340,-415,-155,-36,1000,-1000,103,822,1000,1000,-62,233,1000,218,1000,210,1000,332,283,-69,183,377,-1000,-16,-71,-1000,215,-769,-26,29,-1000,-99,-1000,588,1000,-1000,-1000,1000,1000,1000,-1000,-208,1000,1000,1000,186,-1000,1000,-64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeBinary(byte[]):void",
            new int[]{-803,649,-733,-771,56,-959,-532,-546,611,383,517,-533,-247,975,535,-738,-579,73,-714,-427,393,179,-356,778,-628,183,-450,-476,-479,177,-5,-265,-767,896,-761,-1,-829,-106,-271,822,-635,816,9,749,539,-591,-730,-86,702,295,-853,-747,-451,-55,-148,-711,-773,-241,-179,185,627,756,-341,591}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeBinary(byte[],int,int):void",
            new int[]{858,-1000,419,-404,-905,640,1000,-637,458,894,-1000,-91,-1000,-101,-1000,1000,1000,1000,-63,-501,-626,997,384,-1000,-1000,702,-537,-931,870,1000,447,462,981,915,1000,-1000,-454,1000,208,967,1000,872,-63,-138,-1000,-1000,335,117,-1000,-369,-450,561,1000,-1000,-360,418,665,13,-1000,-1000,90,671,403,-181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeBinary(byte[],int,int):void",
            new int[]{446,-522,-807,752,-731,720,-451,-294,370,684,-733,471,-739,148,374,961,460,718,-701,-481,-4,262,49,859,94,963,-419,-818,-47,555,-215,221,649,852,51,-893,-382,981,214,955,941,-132,-193,-185,-317,-524,64,-700,-78,-932,-180,559,790,-702,-387,525,820,-626,-360,-599,233,687,965,-799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeBinary(com.fasterxml.jackson.core.Base64Variant,byte[],int,int):void",
            new int[]{-135,466,235,-1000,705,-1000,-754,347,-591,451,821,363,37,695,925,-420,528,-243,-194,-884,-1000,855,139,-288,1000,438,22,536,-266,299,-299,-996,-572,-249,-1000,-674,-233,-641,-175,-402,-201,829,-573,1000,16,-80,1000,494,102,272,635,-869,-331,-219,-889,370,-914,-211,268,-638,-716,-97,-153,-824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeBinary(com.fasterxml.jackson.core.Base64Variant,java.io.InputStream,int):int",
            new int[]{-702,417,744,810,722,503,-867,132,-662,488,-20,526,-894,413,791,-987,-605,20,-72,759,-94,-229,-851,-49,-19,361,362,-643,972,-488,-167,-502,655,879,-44,169,539,-32,104,-107,396,117,33,827,-713,-218,-145,-373,968,660,-303,812,-322,480,-187,303,-32,720,136,956,-471,-61,-957,-824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeBinary(java.io.InputStream,int):int",
            new int[]{330,1000,458,-1000,551,1000,-881,857,-589,593,1000,-129,-35,-524,-12,-1000,-573,1000,-613,1000,-935,-846,-1000,1000,1000,1000,158,1000,-1000,-1000,-1000,-792,-1000,1000,-125,-1000,-612,-97,833,796,1000,1000,-125,-334,386,711,1000,291,-601,624,1000,703,-1000,1000,1000,-230,1000,334,723,821,466,-1000,672,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeBinaryField(java.lang.String,byte[]):void",
            new int[]{226,77,282,-464,1000,319,639,544,-577,-591,338,-24,-195,-342,-608,-167,-273,-957,1000,122,146,83,-132,-249,7,412,145,565,315,-505,250,44,-625,-437,-152,506,-73,94,284,727,367,-13,418,-693,145,158,-711,-74,20,634,510,-617,-1,490,-253,-456,704,607,-447,-473,-266,7,524,-119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeBoolean(boolean):void",
            new int[]{-362,-391,-110,1000,-341,-956,1000,247,592,-196,-843,1000,3,986,-757,594,-213,-188,436,-1000,-535,-1000,-26,260,56,1000,-1000,492,-181,-507,-935,1000,-260,-534,507,-775,1000,1000,519,174,1000,698,876,524,158,699,1000,761,8,67,-1000,204,-1000,-1000,321,-320,-64,-1000,1000,-920,765,-971,128,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeBoolean(boolean):void",
            new int[]{-30,-48,84,-926,-46,-19,-493,138,70,-612,-489,-1000,983,3,-1000,-486,263,931,215,-127,-498,443,1000,-886,139,-881,70,-290,890,993,580,545,-380,-180,464,-230,-461,-1000,177,573,-1000,-42,-367,-801,-284,-1000,-152,-230,-738,-718,-144,943,705,1000,1000,301,576,880,-996,-237,-348,-440,684,-433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeBooleanField(java.lang.String,boolean):void",
            new int[]{553,838,1000,-462,-512,-162,364,242,514,-651,-309,664,-986,-939,868,564,1000,1000,124,316,-968,570,453,461,41,683,338,-479,-749,-180,174,-970,-795,696,-460,-1000,405,-444,-233,-825,358,-737,679,-397,-1000,-1000,228,-219,14,1000,-481,-321,693,281,-905,-150,503,443,101,-17,-122,637,481,328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeBooleanField(java.lang.String,boolean):void",
            new int[]{-519,124,-524,-443,158,988,-442,-1000,-259,-506,-44,268,414,239,1000,435,434,947,705,-302,114,-524,-415,-180,69,557,-1000,995,-86,66,552,-246,522,-45,1000,775,-998,833,-875,659,-1000,-271,-1000,-793,133,594,508,-680,234,-768,517,650,1000,216,-1000,-1000,318,-647,914,613,-933,258,779,273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeEmbeddedObject(java.lang.Object):void",
            new int[]{-702,182,-247,104,119,727,-1000,1000,4,-117,795,-187,-229,53,1000,737,212,1000,-206,-994,1000,377,-888,-79,-649,1000,927,362,-121,1000,817,-249,691,-1000,-1000,1000,-1000,211,540,298,-72,588,-668,1000,644,299,-1000,-785,-353,244,-290,442,-920,-61,-853,-948,690,-1000,51,534,102,553,774,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeEmbeddedObject(java.lang.Object):void",
            new int[]{878,-103,90,-212,-673,1000,888,-666,155,163,-233,-1000,-229,1000,397,227,668,1000,-750,175,542,666,-1000,-511,232,531,-540,281,-849,719,563,480,-1000,-978,250,370,-1000,-114,-1000,340,619,-1000,-259,-607,-909,-751,352,89,-631,533,1000,-688,54,-787,-1000,224,127,-374,429,193,-1000,-619,803,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeEndArray():void",
            new int[]{-879,781,-473,336,442,-101,-1000,-1000,34,139,266,-466,-254,71,797,1000,555,-431,1000,485,-1000,-1000,495,-31,581,-792,1000,-1000,-1000,-220,-377,848,1000,-155,-1000,-1000,-1000,313,73,-1000,-557,-114,182,66,-1000,-416,-947,270,618,-125,1000,-1000,-628,-200,987,-240,-959,1000,-523,85,-472,-54,-382,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeEndObject():void",
            new int[]{-566,1000,115,749,1000,-1000,56,228,-469,-117,1000,933,295,251,-765,-276,-936,669,907,523,-945,-199,750,-885,-1000,-948,30,474,1000,-159,425,-996,-667,-774,1000,-460,-5,-435,-1000,-45,-1000,891,230,-467,-607,773,-943,278,-1000,1000,-1000,-270,-1000,93,-944,-232,1000,171,-1000,-139,-415,1000,-303,595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeFieldId(long):void",
            new int[]{330,948,454,-795,-48,427,175,-476,-676,871,555,-292,686,-547,676,682,-649,29,342,-905,355,814,433,108,615,182,-706,-282,-76,-634,-398,540,-842,131,151,-592,-489,-490,283,-668,264,519,-661,343,620,-516,-593,-384,-517,-942,916,868,-292,-706,-845,471,304,-727,-310,-987,-644,667,-185,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeFieldName(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-486,-821,904,-294,-597,-500,-416,-446,-926,525,161,487,558,919,549,117,547,232,-1000,1000,-872,-149,-493,529,61,-1000,163,705,561,-356,-477,-222,-59,-71,830,-250,158,-726,759,212,-360,1000,-592,-445,1000,998,266,1000,-1000,-570,-971,-184,249,-979,697,-1000,120,0,-1000,-166,767,62,947,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeFieldName(java.lang.String):void",
            new int[]{106,-1000,-286,-1000,-371,29,-597,982,-1000,259,651,-259,862,615,-155,-496,1000,173,-65,-436,-1000,-531,1000,-770,-854,76,-184,494,453,286,1000,1000,1000,-1000,129,830,-998,1000,-710,884,-1000,-421,207,-595,801,443,825,1000,550,459,-1000,-1000,-221,-1000,-1000,-1000,-1000,-252,1000,297,710,1000,-554,-930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNull():void",
            new int[]{-766,648,-356,-1000,454,114,595,204,47,4,-236,-1000,-557,543,914,141,-419,-619,-197,49,-803,-713,-72,-1000,-1000,212,735,219,-134,582,-720,70,518,-728,-845,1000,807,355,-133,-693,-431,609,462,454,198,131,504,-185,-1000,-255,1000,-746,-167,490,792,-94,-68,-310,266,-432,-825,-176,-633,-475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNullField(java.lang.String):void",
            new int[]{-322,373,-1000,56,192,402,899,-553,-237,-547,-545,-280,435,452,-480,887,-1000,-919,372,-489,624,-748,1000,475,124,998,1000,-359,672,-930,82,-167,317,-157,1000,-217,-898,684,56,555,-163,971,735,-824,-216,-571,805,403,135,-1000,788,1000,760,851,-693,-197,68,63,414,87,645,252,624,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumber(double):void",
            new int[]{850,-483,-565,318,-231,1000,1000,744,-726,227,-1000,-67,-705,13,685,696,-912,511,1000,-291,-957,-128,-1000,-218,-664,-573,694,998,214,-1000,1000,139,341,875,526,420,1000,188,-528,-802,569,-481,-191,924,478,-1000,731,1000,-202,606,-552,192,162,-78,499,-474,-566,-454,563,-1000,-394,-1000,146,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumber(float):void",
            new int[]{378,-182,772,1000,-894,670,1000,-1000,-776,-71,662,579,231,-132,-371,269,-966,548,281,-1000,-19,1000,247,-713,789,267,-170,1000,-1000,-38,-1000,283,1000,137,-1000,-1000,-233,-882,827,-1000,-70,422,-188,481,-1000,-519,-60,-876,1000,-1000,1000,-449,1000,619,751,247,-206,-34,297,1000,922,-82,1000,-84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumber(float):void",
            new int[]{297,-1000,-513,-955,607,-922,11,1000,-86,-118,-1000,-601,-975,-182,741,-1000,1000,1000,-711,1000,-763,167,805,1000,-809,760,-361,-592,337,1000,-363,75,-1000,-1000,1000,-389,-779,-814,312,1000,365,-547,1000,-43,1000,-467,938,-322,-1000,1000,855,904,-275,-1000,379,646,-567,-900,-1000,-555,-1000,1000,-960,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumber(int):void",
            new int[]{-802,-942,406,-810,265,-637,-455,712,56,-551,-221,957,-42,-715,-471,714,622,-509,988,-513,719,212,-441,-370,97,-198,-701,-524,-86,136,358,185,-338,-699,-977,-528,-591,-76,-290,467,-879,459,992,-514,-586,-622,592,710,-262,402,-838,456,664,672,-378,-987,657,-956,-154,-127,555,615,-421,581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumber(int):void",
            new int[]{378,652,-995,-919,777,-1000,-1000,107,-549,-233,1000,-1000,-1000,-52,517,-1000,-1000,-860,-345,358,125,185,-404,1000,192,989,297,1000,883,588,161,-1000,39,876,352,-363,1000,-1000,-675,-626,1000,1000,-1000,-376,-183,-635,-1000,-508,-1000,-279,1000,-1000,418,93,-518,1000,1000,651,-20,624,-1000,-1000,-128,243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumber(java.lang.String):void",
            new int[]{441,424,650,380,-346,140,329,-416,215,-181,433,87,-363,-329,462,29,313,522,342,-401,-597,-481,473,-1000,-1000,587,38,1000,494,643,-318,-668,-789,-722,694,-211,-401,-43,-941,182,132,-204,1000,-193,62,-706,-1000,-1000,-706,118,712,-134,-61,-431,359,670,-1000,-57,-631,539,-465,713,-834,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumber(java.math.BigDecimal):void",
            new int[]{-715,-50,-705,-356,858,-382,301,586,792,-843,-745,-811,686,425,-505,129,-832,-833,-996,-860,54,191,28,845,-187,-644,510,-161,-717,814,858,328,-269,796,-135,-55,-763,-561,-331,-59,129,-364,-510,834,-288,689,-375,-162,934,675,669,-113,725,867,239,-51,-182,287,643,429,552,530,-140,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumber(java.math.BigDecimal):void",
            new int[]{733,-1000,-977,477,-1000,-575,-252,561,-1000,529,490,-257,939,357,-683,-649,734,1000,-1000,-1000,-258,-1000,-1000,-974,1000,-501,-225,-1000,-276,-950,446,733,234,9,1000,1000,-213,-47,1000,609,801,-753,291,785,711,526,-269,1000,1000,795,904,67,-250,-1000,-397,-475,-741,-473,-1000,1000,-159,1000,400,344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumber(java.math.BigInteger):void",
            new int[]{353,779,197,778,-94,-388,1000,-98,-237,-817,-881,893,259,1000,738,173,389,270,665,555,76,-1000,-775,161,627,185,338,31,341,-867,-892,-20,-12,-799,247,284,92,-957,-157,-503,-1000,-222,-28,-91,566,296,-390,1000,696,-758,911,331,-378,-1000,64,-493,-386,131,-282,1000,947,-109,-944,-617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumber(long):void",
            new int[]{897,840,182,-842,-905,569,-548,-911,914,603,-515,-721,-469,-131,631,-260,595,319,-401,-994,-686,118,834,877,-816,248,108,-769,-764,-761,632,-95,-892,-998,834,-366,991,944,70,-207,555,-572,55,-665,-657,-538,-484,697,772,-996,-310,693,916,-273,696,-540,-489,-771,-509,354,270,716,-814,372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumber(short):void",
            new int[]{-7,-303,-476,-859,-138,-244,-774,-273,199,13,495,-613,-400,-170,339,-672,-302,244,-209,-419,-664,95,-417,-267,711,915,69,-725,1000,1000,-56,-783,-154,184,361,-198,-260,-117,-186,-52,-402,-932,-620,76,139,551,-870,626,-437,-451,-461,-1000,-437,-1000,-242,300,687,66,-370,-757,290,474,-335,184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumberField(java.lang.String,double):void",
            new int[]{178,1000,478,-644,504,-84,115,335,919,-169,-27,-1000,-122,-1000,1000,-302,1000,190,-652,-838,-1000,-1000,900,-1000,-1000,-317,-156,289,1000,-580,-359,-406,152,457,-1000,701,289,-265,-1000,-940,-65,-637,-94,-461,-503,783,-523,443,-1000,-1000,-1000,1000,158,1000,-897,1000,-441,49,-767,205,-523,-184,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumberField(java.lang.String,float):void",
            new int[]{142,27,-1000,715,351,751,998,-2,619,1000,503,-387,408,822,1000,17,952,-384,-664,-818,-887,1000,333,-637,-40,1000,-662,485,-990,322,704,-1000,-969,612,-527,169,518,1000,-1000,916,997,1000,1000,1000,304,-697,676,-143,1000,508,55,491,-406,1000,-366,-1000,-1000,-1000,-1000,1000,-760,1000,412,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumberField(java.lang.String,int):void",
            new int[]{-647,970,204,339,-500,-558,1000,286,-481,-106,-1000,-705,237,-482,-581,841,183,539,-539,-1000,603,-558,140,-1000,168,-1000,-513,-895,-1000,96,322,472,216,-963,-577,-482,-489,-584,783,657,-70,269,185,-329,-705,204,-669,-292,-575,-671,-124,602,450,-743,-433,1000,-1000,982,-414,-1000,614,-416,-20,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumberField(java.lang.String,java.math.BigDecimal):void",
            new int[]{161,208,-161,-287,271,556,926,-834,-853,-343,-459,-73,-988,-481,509,-589,131,133,-65,371,-720,909,-324,518,-107,466,641,-348,783,-47,-730,-957,-653,475,-39,893,-540,356,934,-486,-109,-971,25,573,-1,-874,258,182,-293,1000,696,-43,332,346,9,-780,-323,-152,517,-527,-983,-130,998,989}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeNumberField(java.lang.String,long):void",
            new int[]{149,552,44,755,1000,1000,-442,-1000,490,-86,413,461,-1000,935,-1000,789,-78,1000,68,772,-651,1000,1000,103,1000,-644,429,-1000,-1000,-126,-167,228,-48,231,-1000,-483,217,-1000,-905,315,-251,-416,640,-127,-682,-148,1000,1000,1000,264,493,1000,-1000,254,622,816,1000,-606,420,-917,-1000,415,-560,438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeObject(java.lang.Object):void",
            new int[]{-391,539,1000,-882,811,-1000,429,365,478,304,-490,345,-1000,557,737,-815,-138,-447,284,371,-1000,-712,263,449,1000,430,-522,-447,331,-216,900,-886,-261,-44,1000,-730,1000,870,-773,-681,534,171,-533,1000,624,-373,-9,-1000,-554,-1000,-463,-743,208,1000,-544,1000,-1000,-508,-51,808,782,135,-305,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeObject(java.lang.Object):void",
            new int[]{-643,286,-896,245,-863,-897,-796,-605,-373,-326,439,-666,24,-664,-589,-814,-427,635,518,404,487,-192,579,-688,77,-940,638,4,238,835,-965,301,-514,-932,-571,25,-640,-793,-380,438,-794,-301,-249,413,-883,602,-281,585,85,-22,-491,-421,-655,-181,557,227,-711,389,-801,662,355,-905,840,-895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeObjectField(java.lang.String,java.lang.Object):void",
            new int[]{-747,-56,278,1000,-1000,43,-221,-126,-506,562,951,-483,0,794,231,-227,-1000,975,-928,338,-167,-162,-212,-45,1000,-293,-1000,683,181,525,968,-1000,-841,468,900,273,-749,94,-224,-66,1000,-502,-727,3,-954,432,505,-381,-538,-452,-779,91,-862,1000,314,-22,155,-691,1000,-642,598,672,-76,-512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeObjectFieldStart(java.lang.String):void",
            new int[]{14,-507,-110,39,-994,-900,-929,368,694,-111,-166,-827,-449,117,245,-127,640,-564,-870,572,-912,248,-29,434,92,262,698,369,-519,-107,942,-930,-494,703,-605,725,554,-529,-677,716,771,692,-6,518,209,290,-187,639,31,-825,-724,949,567,886,530,-988,555,-960,-728,-830,-216,-466,-3,-4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeObjectFieldStart(java.lang.String):void",
            new int[]{881,-1000,-1000,93,-994,-900,-975,1000,649,392,871,-253,587,-406,105,446,356,-879,-793,1000,-1000,-1000,-801,318,1000,262,1000,1000,-1000,-1000,1000,-530,-494,-455,-663,1000,-383,-720,-852,-469,1000,1000,-1000,1000,1000,1000,981,-393,1000,-768,-1000,520,1000,-372,1000,-991,1000,-960,-810,-1000,-123,-504,-878,-4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeObjectId(java.lang.Object):void",
            new int[]{-482,876,653,686,173,1000,-771,1000,1000,-282,269,-1000,-437,828,299,-41,799,960,225,-1000,-91,181,1000,-264,1000,-747,1000,463,-608,-576,937,1000,262,1000,695,1000,629,-64,-11,489,-26,1000,961,249,-899,545,470,510,34,379,-281,-514,-534,681,621,-224,125,1000,-818,-19,-14,-967,-946,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeObjectId(java.lang.Object):void",
            new int[]{453,-128,1000,1000,881,1000,-1000,713,581,-522,195,-1000,-1000,615,429,-659,1000,-60,1000,-1000,655,-767,1000,-14,1000,-1000,-94,748,-1000,-532,757,-162,1000,984,226,426,1000,365,-797,-33,1000,1000,-612,-836,-1000,1000,1000,221,502,1000,119,-1000,-1000,1000,1000,-927,198,1000,-172,-482,543,-754,-890,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeObjectRef(java.lang.Object):void",
            new int[]{753,-967,-1000,1000,-465,1000,-470,197,-921,-547,-1000,-153,259,-202,1000,154,-1000,1000,1000,-861,-266,-32,1000,1000,1000,-1000,-447,769,-855,1000,1000,1000,1000,843,-1000,367,-61,1000,226,-1000,624,-484,644,703,-1000,998,46,1000,837,716,1000,-1000,-1000,-573,-874,-1000,1000,-384,10,-165,-389,198,179,756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeOmittedField(java.lang.String):void",
            new int[]{-459,988,-135,-169,303,776,-569,791,-1,-518,627,-301,-172,-912,15,525,-642,263,246,-381,984,121,98,-86,888,762,827,680,-979,-249,-259,367,217,-597,376,953,426,290,464,921,290,-253,13,481,798,303,240,986,599,-41,-154,638,-303,5,178,495,-276,705,-812,583,755,555,-199,-535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRaw(char):void",
            new int[]{98,524,-1000,1000,299,775,-1000,-257,-1000,-141,-437,-242,-636,1000,805,879,1000,311,473,-1000,866,-281,-189,-1000,-807,1000,-1000,324,-151,1000,1000,701,497,-143,1000,-872,-92,-831,-707,1000,-1000,551,-916,813,-137,-95,1000,-930,1000,324,573,1000,-268,-1000,-661,-632,1000,-743,-1000,-921,-507,-827,268,564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRaw(char[],int,int):void",
            new int[]{261,149,373,775,1,-879,-1000,641,1000,119,723,539,687,617,-462,419,126,-339,736,-608,731,509,235,-51,-921,-109,-237,-1000,2,210,736,-161,-1000,266,-831,1000,247,553,-188,-160,-1000,-429,-1000,-1000,1000,-311,295,-85,1000,-1000,-714,-767,87,1000,-212,589,95,-348,210,43,-1000,-355,-28,-111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRaw(char[],int,int):void",
            new int[]{-882,-341,1000,47,706,916,-115,139,-1000,-761,-1000,-677,-1000,1000,-329,1000,1000,-1000,-552,-1000,-1000,15,-1000,336,-83,876,1000,535,1000,-1000,-1000,1000,-1000,-388,882,834,-681,1000,252,-365,-423,934,1000,-398,-1000,-775,-979,-1000,-1000,1000,1000,-342,-872,-553,-623,535,195,-357,-1000,-552,1000,-35,1000,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRaw(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{413,637,889,-400,967,1000,-770,853,515,639,383,700,-1000,783,-883,-1000,-1000,-533,-322,-1000,-11,-853,436,400,-869,-1000,-1000,580,-821,-1000,-1000,-397,86,-250,1000,-1000,-400,-277,829,-193,652,1000,371,400,-608,1000,-48,1000,-553,94,400,-291,-643,1000,928,-479,297,-292,395,748,-339,-1000,-836,814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRaw(java.lang.String):void",
            new int[]{49,1000,686,-603,1000,1000,1000,-1000,479,-958,-939,687,-871,686,484,-365,168,-1000,401,226,1000,-1000,1000,592,301,-1000,960,-1000,-1000,-378,-23,-109,818,-254,-714,1000,-1000,872,-1000,-1000,623,204,1000,643,-1000,1000,1000,-911,-14,-776,-1000,1000,-94,972,-1000,414,295,-1000,-316,795,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRaw(java.lang.String,int,int):void",
            new int[]{-475,-1000,1000,12,-321,-108,-312,790,929,-611,887,-536,-421,1000,-1000,714,919,-215,-1000,972,-874,812,-131,373,269,-1000,1000,755,-811,1000,1000,-792,537,834,-826,1000,-1000,400,1000,194,-938,1000,-1000,763,-1000,616,-252,1000,-1000,-350,-1000,-1000,1000,-506,67,-1000,-81,-1000,-127,-124,-448,-962,1000,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRawUTF8String(byte[],int,int):void",
            new int[]{966,-1000,103,811,-928,-136,-75,323,1000,-738,-469,-1000,-1000,621,-1000,1000,552,886,320,714,-744,883,70,898,-370,1000,-1000,559,231,710,108,-216,-115,-1000,-826,-441,-129,202,-317,-1000,-331,-365,647,-355,1000,-1000,-77,742,968,-1000,509,721,593,1000,-640,609,1000,1000,-356,915,-899,1000,-693,-581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRawUTF8String(byte[],int,int):void",
            new int[]{225,383,100,-550,454,461,418,-93,527,-197,168,-411,-25,-199,5,32,963,-701,-366,156,-789,6,327,357,-324,1000,323,-806,-142,-387,812,221,-718,650,190,-752,629,-597,991,235,-272,959,-1000,570,-436,49,-45,-277,-1000,-95,137,-806,-561,290,-18,-498,-554,-781,397,619,1000,-748,-658,-183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRawValue(char[],int,int):void",
            new int[]{678,1000,-38,-353,1000,1000,304,-212,45,49,147,-1000,428,341,-597,179,725,-273,-982,390,520,579,281,-178,1000,-732,342,-590,-684,56,342,603,-361,111,-131,-90,995,722,-1000,240,913,863,-495,-1000,390,588,497,-1000,536,-1000,-793,674,931,303,1000,63,910,-856,158,442,-839,-400,-1000,234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRawValue(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-843,-836,1000,277,222,1000,-785,-672,248,525,19,885,-49,611,-139,-645,-54,-1000,-441,-1000,865,283,-174,-1000,130,-93,150,-567,936,-767,-1000,-877,-1000,749,228,1000,304,-450,-1000,1000,-225,1000,-878,285,355,-1000,1000,-243,-379,1000,-694,764,370,426,-546,-1000,-1000,1000,554,27,447,-267,113,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRawValue(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-602,371,-580,74,-947,-562,-268,96,-235,-917,-647,-529,955,-1000,1000,-651,700,590,307,274,-769,-445,783,780,-704,922,-1000,1000,-1000,-367,835,273,113,-1000,-93,-810,-699,-1000,1000,-188,-873,1000,-292,226,298,50,-70,481,351,-824,779,-1000,-1000,-230,1000,-528,21,333,-472,-339,-103,1000,1000,-952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRawValue(java.lang.String):void",
            new int[]{642,-261,-1000,-327,-822,266,-1000,-109,59,-209,635,716,1000,1000,837,986,-125,-31,355,300,-411,-680,215,70,-308,-796,-49,679,-338,-245,351,-685,-477,530,-705,-97,-982,-472,-152,762,-690,-164,-1000,1000,816,-1000,-319,-246,572,-197,-1000,-829,-1000,-15,1000,918,0,-518,-107,-373,145,833,-1000,450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRawValue(java.lang.String,int,int):void",
            new int[]{338,423,718,67,-708,399,758,59,-598,458,-909,-307,-108,516,-883,598,-613,572,201,187,940,-847,116,-90,779,-645,468,218,-936,-150,-190,-687,-883,-948,-803,-347,12,201,332,997,228,-432,-903,-699,-870,-800,-701,-803,448,-329,734,-46,-349,-851,308,853,-881,763,875,-283,-643,740,-287,-24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeRawValue(java.lang.String,int,int):void",
            new int[]{-139,447,208,333,-224,-696,665,-1000,-206,-873,-271,79,-198,385,-529,-475,34,71,-202,547,724,563,317,150,-575,365,21,-435,161,1000,488,-553,-542,-362,-133,952,-741,68,22,653,308,-750,-840,-687,-807,-860,-344,-750,315,142,600,-1000,577,-805,66,628,-1000,266,264,-475,-663,192,143,-4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeStartArray():void",
            new int[]{-502,-1000,-115,-770,-19,-1000,423,154,718,297,324,1000,1000,925,-320,18,1000,-657,-1000,713,535,1000,-273,207,1000,-969,-1000,-1000,-1000,-565,545,-82,847,807,-108,398,-577,755,-802,256,439,-1000,-774,193,1000,1000,326,1000,901,309,1000,-1000,-554,627,-503,1000,-988,1000,1000,-532,395,-215,-532,750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeStartArray():void",
            new int[]{-502,-387,-17,459,-627,1000,1000,154,-976,1000,71,-978,272,851,285,-1000,72,-73,1000,-1000,-1000,-399,736,-295,-1000,-570,-1000,-1000,1000,-565,-385,253,394,-189,-205,-149,-577,968,96,-668,-1000,-261,-774,-432,-848,-215,-796,612,-666,309,-321,750,-286,754,-503,1000,981,-734,-476,743,312,777,-601,921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeStartArray(int):void",
            new int[]{-546,279,687,-154,-988,-445,-654,364,701,154,533,-989,648,-462,-138,735,586,-39,-14,-886,-626,449,308,-79,-371,595,-759,724,-204,-728,623,-346,400,-941,186,-9,-188,-722,-218,-866,110,708,237,47,152,-551,666,1000,140,-20,-121,-926,-171,-1000,57,-388,-1000,233,432,-1000,537,-844,-224,361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeStartObject():void",
            new int[]{-567,-489,-718,-61,856,300,984,995,11,-667,1000,-307,1000,1000,452,509,-109,1000,819,48,227,537,272,315,966,-396,826,211,520,741,-433,-551,125,359,-253,303,-456,142,702,389,160,213,-686,1000,167,-138,552,19,338,-267,-1000,-161,1000,-185,555,-215,757,394,-275,-783,926,-1000,-174,-593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeStartObject():void",
            new int[]{321,956,937,263,-667,497,1000,-8,624,-372,-153,-13,287,-166,1000,-838,624,-151,-897,753,-17,338,-342,-632,-710,745,965,110,520,-1000,-341,383,-545,-733,29,1000,118,-865,948,-1000,163,-511,-587,539,-935,-285,943,-1000,-345,-11,-1000,738,-658,-1000,-683,-1000,-247,-251,-116,677,-1000,747,-264,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeStartObject(java.lang.Object):void",
            new int[]{-886,406,332,-14,-340,-662,-325,-871,-376,949,395,827,522,800,-850,741,-194,408,454,165,108,-298,-847,-959,266,753,699,-966,743,-382,989,-463,-9,888,-80,-71,-471,-796,-593,718,89,-547,-375,307,-962,589,469,-990,469,-880,850,-284,-158,-814,311,-905,887,838,-305,637,-450,636,91,937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeStartObject(java.lang.Object):void",
            new int[]{886,-1000,-383,1000,214,1000,-1000,-243,-901,-377,510,-1000,639,-1000,712,461,1000,-16,1000,186,-1000,128,1000,64,-1000,133,1000,-270,-841,130,-115,-274,597,-642,-519,46,659,902,-25,-570,505,270,736,-433,656,-581,814,68,318,48,-1000,-1000,96,995,-8,827,-638,1000,687,1000,-126,25,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeString(char[],int,int):void",
            new int[]{-766,997,989,988,-483,184,-438,-713,-997,-371,575,-549,597,-194,145,949,-875,647,833,-888,-10,-265,-974,-290,-93,-665,-469,215,391,-930,-537,-34,304,830,24,355,-360,996,516,824,954,447,125,992,-644,974,-611,-447,849,-305,-979,705,-92,869,839,831,693,-896,-109,-83,41,-630,557,-252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeString(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{109,1000,-578,-112,723,1000,-717,238,499,-378,-283,905,-173,172,-170,545,566,525,64,-99,40,-698,88,400,256,-250,953,546,-931,423,-416,-1000,-904,30,635,-104,792,-604,-1000,119,-189,220,-236,-601,-733,1000,-287,471,-132,1000,-979,288,382,902,969,-51,-640,256,-419,-1000,267,336,-563,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeString(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-530,908,435,524,-622,699,73,-338,791,589,-45,153,610,-433,-676,954,313,108,291,427,624,395,521,-83,896,-199,-354,395,7,968,-484,819,410,-260,976,621,-259,408,-836,-73,824,270,-463,-536,-705,315,-227,-77,171,23,507,-448,637,-280,473,-844,102,-306,-994,-652,132,-612,-881,-114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeString(java.lang.String):void",
            new int[]{545,-481,426,-204,-52,33,-20,-374,-512,703,-865,725,-541,285,610,191,-198,-733,1000,1000,-24,686,-596,470,88,-338,-460,-1000,-702,701,400,-400,-165,424,628,70,341,770,-400,-396,710,400,-612,73,-894,774,-330,117,132,184,-515,-153,-539,-21,274,602,-787,320,83,904,613,1000,43,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeStringField(java.lang.String,java.lang.String):void",
            new int[]{226,-1000,-142,-1000,864,-668,644,155,403,21,-201,441,-652,326,-865,-469,1000,1000,-1000,1000,-1000,-994,-1000,591,621,504,813,408,-691,423,-1000,-664,-254,248,460,745,-715,947,581,-271,-109,1000,161,1000,785,-701,-1000,-893,-420,140,-1000,342,-1000,-6,858,-856,-473,-1000,1000,-51,-1000,-467,-590,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeTree(com.fasterxml.jackson.core.TreeNode):void",
            new int[]{-843,1000,142,796,354,-663,-923,593,-182,189,-261,-84,-114,-682,-43,-394,-387,258,209,638,-964,-1000,-1000,-235,-65,323,-832,825,949,-1000,-200,257,330,27,372,-777,-489,-21,-340,88,-1000,95,-1000,1000,-370,-190,97,-413,521,721,-75,1000,-542,-1000,-1000,171,38,-875,-659,-450,106,816,-182,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeTree(com.fasterxml.jackson.core.TreeNode):void",
            new int[]{162,-1000,969,422,637,-446,652,73,-298,289,659,-1000,-1000,1000,-233,-917,375,646,-15,850,768,-1000,331,503,765,1000,-247,-572,-219,-1000,283,117,-980,1000,-1000,-992,-236,-1000,-133,230,-1000,-876,1000,874,-800,-85,676,329,39,-1000,-1000,483,-1000,286,139,133,1000,-1000,-748,-1000,229,602,1000,918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeTypeId(java.lang.Object):void",
            new int[]{-46,950,-1000,1000,-1000,-1000,-400,1000,-66,978,-277,381,-310,-331,1000,-761,109,111,-198,-779,606,1000,29,714,1000,-400,433,400,-141,343,252,-1000,-1000,700,-400,197,-131,-1000,-1000,221,-734,1000,541,1000,-1000,-646,-118,467,717,-181,-104,-919,-772,232,-141,985,-628,-580,283,-161,1000,-442,67,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeTypeId(java.lang.Object):void",
            new int[]{-118,210,645,1000,351,1000,1000,1000,479,348,-1000,-1000,651,-124,-649,-761,287,1000,903,186,1000,-111,911,-884,381,-610,-182,-400,-276,-142,-1000,751,575,-1000,1000,230,171,196,-400,152,509,209,1000,1000,-864,-1000,790,-146,-433,-583,-1000,-6,-536,-178,114,-679,-1000,211,-317,-902,-1000,-515,-71,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.core.JsonGenerator", "com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate,com.fasterxml.jackson.core.json.UTF8JsonGenerator,com.fasterxml.jackson.core.json.WriterBasedJsonGenerator,com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeUTF8String(byte[],int,int):void",
            new int[]{306,-696,400,-664,-1000,-1000,-458,-179,-858,504,831,-741,917,-1000,-371,-251,-1000,295,-384,723,-1000,159,-1000,-179,-93,202,-296,-422,-589,572,-376,-1000,-506,1000,343,-245,-223,28,-360,1000,764,-307,-1000,-896,-895,-818,-726,1000,99,206,-285,-112,-470,177,-347,-1000,-1000,1000,-479,-1000,277,149,282,-355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "copyCurrentEvent(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "copyCurrentStructure(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(double[],int,int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(int[],int,int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "com.fasterxml.jackson.core.util.JsonGeneratorDelegate", "writeArray(long[],int,int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
