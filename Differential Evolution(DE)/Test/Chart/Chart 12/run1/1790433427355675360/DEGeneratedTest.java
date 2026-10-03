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
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{1000,-622,14,-257,31,-279,47,382,-164,-364,899,1000,519,93,92,1000,-271,75,1000,-158,322,-56,-1000,-589,-452,313,1000,767,-453,983,-548,-861,-635,-495,-1000,361,-376,1000,-163,-947,-1000,31,461,-29,-1000,-230,356,-659,-1000,1000,-299,-544,-1000,905,-71,464,-835,-512,801,332,-519,852,445,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{11,-319,-885,-701,-652,783,973,-586,-1000,-1000,-239,-1000,-374,1000,-446,761,730,-129,1000,1000,400,-1000,-31,221,-1000,-526,158,-1000,-166,732,1000,1000,-974,-1000,1000,-1000,-212,-21,-514,-399,-698,-570,1000,-1000,45,-693,958,-658,-902,141,-175,-651,-962,951,-948,445,1000,987,-51,-409,1000,407,-120,-630}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{365,-92,-121,-439,-826,1000,-65,379,-443,139,-798,-415,-614,77,-485,464,-81,-941,-119,41,-1000,56,1000,-334,281,-475,1000,-369,-310,140,-383,744,-261,-495,272,-482,846,1000,-771,-204,-790,-234,-115,-381,-58,131,661,-311,749,417,-532,-228,444,347,99,1000,728,433,-1000,332,247,302,-930,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{124,-249,-721,-716,-1000,1000,730,1000,-565,-296,-168,-1000,519,783,-440,536,220,-1000,1000,1000,-1000,-934,946,643,-319,-1000,630,-1000,-346,629,1000,980,-1000,-1000,1000,-1000,447,-21,-897,-195,-698,-834,1000,-1000,876,-398,-839,-925,1000,-86,1000,-970,-40,537,212,530,1000,646,-956,474,1000,387,-804,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "equals(java.lang.Object):boolean",
            new int[]{860,-1000,769,-344,-875,-998,-1000,-275,77,979,-118,-507,-424,144,121,-278,-301,552,-708,-126,787,-679,-1000,257,791,-513,583,1000,120,-1000,-332,-1000,-774,-724,159,-302,-965,1000,-1000,653,208,86,376,-663,1000,-391,-29,-1000,637,-1000,1000,-800,679,-355,282,-668,-490,795,665,-429,492,889,802,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "equals(java.lang.Object):boolean",
            new int[]{-772,-418,-761,-383,-1000,-640,-807,-610,-749,1000,-95,1000,865,1000,448,-646,-553,785,-806,-193,667,-113,1000,646,961,-50,1000,1000,-796,-275,-1000,-938,-1000,-192,-338,-696,-365,862,-884,936,975,-468,186,-46,623,291,-653,-1000,505,-488,709,-631,-394,633,686,860,-80,1000,1000,-131,1000,320,367,-607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "equals(java.lang.Object):boolean",
            new int[]{438,188,233,-153,418,-145,-965,-677,554,1000,220,-307,-1000,144,-22,-278,-1000,1000,10,-273,787,560,-910,257,877,-890,881,859,-190,-965,-368,56,-703,-724,-90,-302,-1000,-28,-863,480,199,86,-226,-634,-20,449,-194,-1000,710,-1000,1000,-1000,145,-52,282,52,270,795,665,419,492,570,206,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5NA==", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getAggregatedItemsKey():java.lang.Comparable",
            new int[]{-211,-1000,254,-572,814,-630,928,1000,862,1000,-83,1000,482,-295,-664,-1000,242,-453,-436,-996,-251,1000,9,561,-690,-813,1000,-45,-1000,-15,204,-384,1000,-1000,-154,981,243,-157,-144,77,-278,1000,-1000,-269,-604,76,1000,1000,-866,-315,1000,-949,-551,-481,294,-747,475,-713,378,1000,581,218,1000,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:T3RoZXI=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getAggregatedItemsKey():java.lang.Comparable",
            new int[]{-199,25,-73,-961,408,-279,-597,-459,595,47,1000,431,-342,269,-89,1000,-871,-213,827,1000,-359,7,227,881,-331,-752,-1000,-530,-266,-476,365,-1000,-1000,-829,1000,313,668,-152,-525,-1000,870,1000,970,-567,-1000,63,-891,438,732,20,-1000,119,-549,1000,829,-942,536,-315,-126,1000,1000,-633,-370,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:T3RoZXI=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getAggregatedItemsKey():java.lang.Comparable",
            new int[]{-973,-1000,632,-15,463,309,-415,-267,-1000,-195,848,-400,-503,1000,-895,538,-277,-1000,196,-1000,-249,-1000,459,-352,-1000,-1000,43,-945,-531,-1000,365,-20,924,-953,44,-186,630,730,-758,-475,349,604,1,-1000,-20,-1000,1000,1000,693,1000,1000,1000,-556,997,1000,-811,1000,-836,1000,-989,20,-1000,-809,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("COLOR:-4144960", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getAggregatedItemsPaint():java.awt.Paint",
            new int[]{-241,-648,-171,140,815,209,-225,959,1000,-1000,635,217,-698,-913,835,-146,52,-226,-234,-248,353,-448,-1000,-1000,-236,1000,-1000,878,-1000,765,1000,-1000,1000,-1000,1000,579,635,-1000,1000,340,1000,-1000,117,-446,-805,-1000,-1000,491,311,202,846,871,1000,-332,-1000,400,662,284,-1000,359,590,-1000,-576,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("COLOR:-15318587", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getAggregatedItemsPaint():java.awt.Paint",
            new int[]{-726,1000,902,-87,534,65,-315,381,396,-1000,70,131,-1000,-1000,-253,630,505,1000,-1000,1000,-1000,1000,-1000,734,114,1000,-1000,812,-470,1000,-644,-906,-169,695,659,215,534,-1000,1000,1000,942,140,62,-390,-1000,-466,-623,183,-1000,1000,-545,1000,1000,-47,-1000,-167,181,-1000,-541,-1000,432,-175,-187,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("COLOR:-4144960", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getAggregatedItemsPaint():java.awt.Paint",
            new int[]{322,362,-687,-485,498,1000,-1000,1000,616,-35,1000,-1000,568,569,1000,-1000,21,400,-347,-1000,1000,137,666,462,-172,-276,-928,467,1000,292,-400,-400,-1000,1000,-731,-622,1000,1000,1000,-933,880,435,244,645,29,-1000,1000,-1000,1000,28,29,-379,-151,-708,612,967,814,1000,-652,885,-1000,-236,531,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("COLOR:-4144960", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getAggregatedItemsPaint():java.awt.Paint",
            new int[]{857,-1000,-176,915,-201,781,-476,-140,342,1000,653,-340,1000,1000,1000,751,-127,-1000,420,-1000,1000,-1000,1000,112,5,-1000,-98,1000,1000,-708,1000,1000,-1000,269,-943,-1000,-1000,1000,1000,-913,-1000,-193,-581,1000,1000,1000,780,-1000,1000,-1000,138,-1000,-1000,272,1000,-393,-62,1000,79,1000,-1000,897,1000,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.TableOrder", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getDataExtractOrder():org.jfree.chart.util.TableOrder",
            new int[]{-786,124,318,492,1000,472,-907,-306,1000,1000,-168,126,268,-1000,1000,1000,-709,-281,-876,-417,136,1000,-1000,-731,652,614,-1000,-942,1000,-1000,1000,-198,-1000,1000,-1000,468,742,831,644,-1000,-600,445,650,469,1000,1000,414,-870,122,-664,-842,-425,914,-285,-1000,584,-1000,460,1000,832,-83,-312,-1000,-90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.TableOrder", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getDataExtractOrder():org.jfree.chart.util.TableOrder",
            new int[]{-1000,-70,-161,-682,-1000,535,-746,351,-306,1000,-547,1000,1000,-373,-485,857,-1000,1000,-415,-1000,-396,1000,1000,-1000,539,-28,-292,-317,572,-1000,455,-46,1000,1000,-1000,848,1000,1000,-85,1000,4,1000,202,922,1000,1000,738,423,802,-1000,-818,597,1000,-927,-1000,1000,-1000,851,858,-1000,-1000,-139,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.TableOrder", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getDataExtractOrder():org.jfree.chart.util.TableOrder",
            new int[]{-432,-52,103,335,887,671,-483,174,1000,190,-185,-464,1000,140,-1000,249,-1000,1000,-657,-820,-993,-138,154,-451,-57,-755,82,-618,235,-1000,-39,-450,894,740,-611,14,1000,728,-1000,444,769,51,100,1000,-955,146,1000,-1000,1000,-623,-668,-52,586,164,-482,540,-75,208,477,-408,-1000,-292,368,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultCategoryDataset|getRowCount=22:java.lang.Integer:Mg==|getColumnCount=22:java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{-596,130,-331,-22,-760,-586,337,-1000,78,-37,-649,589,447,-223,-415,73,948,-1000,646,-323,-1000,-873,400,-589,-1000,1000,-650,-525,-464,-122,1000,-341,467,-262,158,535,-514,-684,-1000,-583,-1000,719,400,102,858,-254,-670,747,355,-1000,334,-1000,-418,180,847,-177,278,-631,695,-704,-802,798,-1000,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultCategoryDataset|getRowCount=22:java.lang.Integer:MQ==|getColumnCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{-331,289,425,1000,115,90,271,711,179,-392,-571,-216,-295,1000,-282,982,-199,-498,156,13,-49,424,-243,-931,441,479,1000,-169,1000,1000,-578,-293,1000,5,267,520,-591,195,-27,224,-869,-714,-1000,-280,-185,508,568,488,12,818,620,-109,-298,-52,654,287,908,-226,696,-942,340,-647,1000,593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultCategoryDataset|getRowCount=22:java.lang.Integer:MQ==|getColumnCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{1000,1000,-281,-828,450,-776,-723,-1000,-207,-175,-1000,-1000,337,-173,-415,984,-235,-1000,678,395,-917,1000,-114,1000,602,1000,1000,20,1000,48,-108,-1000,329,165,-1000,351,-514,52,-228,1000,-94,-131,1000,157,-1000,-288,1000,499,-534,-1000,290,276,-1000,-738,1000,855,1000,-170,606,-1000,1000,-446,-1000,192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{740,130,278,-505,899,-61,-967,-954,-535,348,-1000,-590,447,926,-1000,316,-177,-438,1000,-526,-219,261,348,-767,-816,614,473,-253,-345,-122,-152,-1000,365,-262,201,856,473,1000,-600,465,-163,-619,-229,342,647,-32,-769,349,-492,-1000,334,-615,-402,245,340,-977,586,-695,-288,-28,371,528,209,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultCategoryDataset|getRowCount=22:java.lang.Integer:Mw==|getColumnCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{692,-578,954,1000,941,109,-102,1000,739,-50,-718,439,466,442,-398,781,-850,-754,1000,-262,708,1000,-1000,-540,-39,-668,246,-209,195,1000,89,1000,1000,-511,857,227,-137,1000,580,655,693,-95,-1000,-587,613,766,504,1000,78,1000,-623,798,-1000,-595,-944,170,861,-725,-158,-891,648,-1000,1000,-327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{1000,327,-1000,-737,-685,-905,-403,-13,-482,-1000,-341,579,405,-498,-806,-8,-160,-831,1000,-1000,617,370,749,3,-382,-513,868,-934,607,815,-1000,828,707,-890,1000,-48,-433,-20,-135,-1000,364,873,209,-198,-253,127,-1000,-148,311,737,-909,1000,-432,-1000,-287,-872,-1000,-967,-1000,73,-947,-66,-1000,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-273,-477,-470,-1000,473,-576,719,281,-368,37,-746,620,495,1000,-53,-1000,-1000,224,1000,-25,-1000,-223,-644,55,-1000,-236,-542,-1000,815,706,-1000,1000,1000,-197,481,-691,153,35,174,-1000,268,1000,-424,-6,1000,-223,1000,681,13,260,-1000,1000,-527,-547,182,-672,1000,-284,314,1000,156,273,-548,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{1000,934,928,-146,287,322,1000,-1000,1,-876,-726,764,638,-1000,539,1000,1000,-1000,-1000,-675,1000,1000,-1000,737,-345,-626,1000,663,896,-1000,897,-940,-932,-1000,624,1000,-1000,-991,278,-470,-1000,-30,1000,-1000,-1000,-1000,1000,249,87,965,11,574,994,-852,-265,1000,441,-1000,-126,1000,-1000,-1000,-431,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{512,388,789,21,524,699,-1000,-430,285,-569,-136,-1000,-759,-804,994,421,-924,611,-1000,-1000,1000,610,-1000,285,1000,225,-749,781,1000,882,903,-570,189,-61,585,-1000,-666,-123,375,1000,-93,-416,831,1000,-244,-177,1000,1000,-1000,-383,-1000,-267,957,76,8,-388,-482,707,1000,442,-310,182,-85,-109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-1000,-1000,-107,866,-6,1000,-18,111,-533,396,122,33,-278,-104,-680,-389,1000,1000,-514,-278,-782,77,-197,-383,567,-456,-186,191,206,87,152,589,-341,1000,-209,-752,1000,1000,1000,-702,853,-1000,153,890,279,1000,-111,394,-449,-467,91,-931,953,1000,1000,-682,-643,582,962,452,1000,1000,654,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getLimit():double",
            new int[]{-279,878,-36,-718,-1000,-1000,-1000,-517,595,965,888,-537,-472,1000,-833,844,-321,-235,-157,489,-168,1000,-747,-639,-510,-322,-51,1000,-512,-1000,-2,-929,208,-1000,503,417,-185,-876,1000,-227,1000,150,-540,-1000,294,-945,-116,210,-1000,-829,195,206,462,1000,-389,-344,224,-70,-395,1000,-942,759,-111,-836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getLimit():double",
            new int[]{386,228,564,-583,142,-18,-485,-272,100,529,333,-537,32,44,166,-285,297,-439,-367,-758,-699,-21,-35,-437,790,438,192,1000,-512,168,29,-929,-251,-329,503,-518,-470,-703,891,-227,190,-138,-1000,-137,38,-265,86,187,987,-829,195,180,-228,-360,-185,-1000,50,-235,-45,614,-23,114,382,-70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getLimit():double",
            new int[]{-538,542,102,571,250,907,1000,-437,377,-279,-583,1000,-1000,-620,579,716,-883,-1000,682,-374,665,1000,-1000,1000,248,660,914,-712,-558,-483,134,-317,942,676,-865,991,1000,1000,-480,535,256,-537,22,760,1000,-883,776,-738,1000,67,-324,-602,159,-1000,308,1000,773,912,1000,-1000,-426,122,-817,265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getLimit():double",
            new int[]{-1000,-1000,-546,238,221,-166,279,-591,739,-325,236,-263,-1000,-399,812,1000,-1000,-1000,-775,-791,-414,999,-970,102,-859,1000,-198,-723,-135,-80,-1000,-323,692,953,-1000,-374,768,1000,660,1000,-345,1000,-113,1000,1000,-787,1000,-456,814,-938,-549,16,731,-473,365,552,1000,1000,387,-648,-1000,-36,-420,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.JFreeChart", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getPieChart():org.jfree.chart.JFreeChart",
            new int[]{-1000,-408,-596,1000,793,-690,381,1000,-923,-1000,756,-401,954,1000,99,471,-264,562,-1000,-1000,1000,1000,-234,1000,1000,-362,-1000,897,284,-869,-269,1000,-373,-1000,1000,-137,-663,-1000,-1000,-748,-952,1000,-1000,-84,-187,169,-204,318,-1000,800,-1000,144,-438,-1000,1000,-936,151,547,1000,700,-504,722,851,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.JFreeChart", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getPieChart():org.jfree.chart.JFreeChart",
            new int[]{-429,514,-921,241,-202,516,-719,580,1000,303,-373,216,-907,818,-934,393,-93,246,185,-921,-27,176,-206,280,-383,1000,65,272,518,311,-597,157,356,646,125,-295,260,974,564,-165,365,-911,512,-607,10,-827,-307,-940,645,525,423,439,-1000,-350,131,-214,199,305,-970,-1000,1,-172,-474,740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.JFreeChart", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getPieChart():org.jfree.chart.JFreeChart",
            new int[]{-266,-1000,707,206,-1000,-574,1000,560,671,180,-1000,69,-1000,852,454,712,1000,-632,1000,-1000,-75,-734,-1000,1000,-1000,1000,-785,-601,1000,1000,-1000,-314,1000,1000,-697,893,1000,1000,-551,-753,-137,1000,1000,-366,-1000,30,-599,871,1000,442,-1000,-791,233,-315,-1000,-174,1000,-919,-1000,-170,-524,-472,-1000,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.JFreeChart", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getPieChart():org.jfree.chart.JFreeChart",
            new int[]{478,-260,498,-564,-888,728,587,-309,69,330,122,332,-913,-127,-467,-231,921,-174,1000,-922,-209,75,-100,206,-269,923,625,-1000,233,778,-547,-445,787,596,-396,573,259,584,894,676,-106,-756,299,25,-116,-136,23,-328,1000,-484,726,189,510,700,-1000,94,302,-335,-430,-843,-379,-41,-876,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.String:TXVsdGlwbGUgUGllIFBsb3Q=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getPlotType():java.lang.String",
            new int[]{-574,-346,-312,-719,593,615,1000,-487,46,-57,-595,-14,-5,1000,1000,24,-499,-377,96,103,-726,-46,259,1000,-314,430,605,244,241,929,143,1000,118,621,-1000,266,698,1000,-989,717,-123,1000,74,489,557,726,-641,1000,378,-34,-1000,-1000,-1000,-1000,749,-167,-349,53,-1000,-44,1000,-131,402,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.String:TXVsdGlwbGUgUGllIFBsb3Q=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getPlotType():java.lang.String",
            new int[]{-1000,154,749,-147,73,364,543,-43,616,2,-263,-248,-542,-184,1000,1000,-716,622,-603,-662,1000,1000,-705,906,886,1000,-544,-1000,123,-74,894,1000,1000,-1000,304,451,502,1000,-1000,521,-328,1000,-255,-8,1000,-118,-266,394,752,-705,-454,-843,713,37,-181,526,1000,-1000,-779,740,61,-383,-125,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.String:TXVsdGlwbGUgUGllIFBsb3Q=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getPlotType():java.lang.String",
            new int[]{-889,407,116,-1000,-630,-356,486,-972,-887,186,-7,147,455,414,360,787,-949,1000,310,-704,591,392,38,524,538,275,-470,414,983,219,-652,-431,222,526,1000,756,576,54,-693,-82,-686,351,-27,343,568,-1000,219,-1000,19,-196,-592,-886,-228,-772,76,619,-115,-899,-401,-335,-256,431,-606,672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:TXVsdGlwbGUgUGllIFBsb3Q=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "getPlotType():java.lang.String",
            new int[]{1000,942,-676,602,1000,-1000,-675,367,1000,-809,1000,-616,415,-1000,-757,1000,-886,935,-422,859,-349,701,-204,750,-1000,163,-1000,-271,-224,-1000,-290,-1000,-1000,-1000,-1000,1000,1000,-1000,940,-672,1000,-1000,-648,-792,-526,1000,1000,-355,611,73,867,1000,1000,1000,-1000,1000,1000,810,-245,-1000,-1000,1000,-1000,-510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setAggregatedItemsKey(java.lang.Comparable):void",
            new int[]{532,-740,594,-947,-1000,224,221,823,-166,56,1000,-767,-566,-407,-681,1000,1000,387,-1000,-344,706,299,-838,218,-246,510,-565,-769,444,558,40,-1000,1000,339,-722,807,421,-189,89,818,1000,480,1000,1000,-1000,80,438,-1000,131,-444,180,-908,-193,530,667,787,32,749,-127,263,-1000,-315,1000,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setAggregatedItemsKey(java.lang.Comparable):void",
            new int[]{246,170,629,-647,100,46,-103,-283,431,1000,-772,52,-1000,324,7,869,569,-770,595,1000,146,633,-1000,1000,-147,453,-510,-348,479,495,-1000,-1000,-256,1000,1000,876,488,9,-370,-477,-381,206,-920,644,-126,-1000,-11,-561,772,-83,1000,-866,840,-668,-86,-222,645,1000,479,1000,-609,648,850,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setAggregatedItemsKey(java.lang.Comparable):void",
            new int[]{736,771,366,-1000,-71,-680,-47,573,1000,349,279,668,-586,-762,-299,1000,992,-785,-1000,226,-704,-784,-485,1000,-608,266,143,671,-505,-369,994,-1000,1000,250,110,-400,495,-986,196,769,383,-262,25,145,-254,-155,-196,-24,-616,-6,-350,-818,234,445,-1000,-1000,-1000,-485,414,66,-975,-802,373,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setAggregatedItemsPaint(java.awt.Paint):void",
            new int[]{-1000,-1000,-752,404,-845,942,677,1000,-335,411,30,416,140,783,-41,-576,892,96,-1000,-174,341,-1000,1000,126,1000,1000,388,1000,-666,-1000,-392,-1000,1000,-1000,-548,1000,-156,1000,719,-653,900,-625,-508,627,-1000,-531,-1000,-1000,189,-1000,-206,-1000,392,149,458,-417,716,-690,529,204,1000,-1000,-144,983}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setAggregatedItemsPaint(java.awt.Paint):void",
            new int[]{-404,-500,-706,865,-851,396,293,933,-87,-496,555,-230,455,-274,643,282,563,115,-479,-29,-925,-71,769,-418,-1000,-116,1000,-427,268,-186,302,505,1000,-856,-267,592,-337,597,13,313,282,465,757,1000,297,67,-1000,-45,-966,-821,134,-65,-367,-34,1000,570,-3,773,338,247,998,-1000,223,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setAggregatedItemsPaint(java.awt.Paint):void",
            new int[]{-205,-1000,-756,-42,-400,181,565,1000,-10,417,-400,-1000,452,-43,-753,375,694,302,-1000,-135,-947,-708,-18,-581,-1000,-207,898,-1000,97,683,258,138,745,-649,-200,336,-548,1000,1000,-527,191,-231,477,167,-209,-308,-1000,492,-219,-381,-405,84,-204,-1000,364,424,1,241,-20,-652,406,-1000,-713,-691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setDataExtractOrder(org.jfree.chart.util.TableOrder):void",
            new int[]{-729,-756,-367,-542,65,909,1000,1000,-1000,-44,771,639,1000,-281,-462,814,465,-1000,1000,642,-437,-1000,101,-53,41,-1000,530,172,422,1000,-178,-785,287,1000,-193,-53,-925,57,-178,-54,1000,1000,-244,300,1000,-1000,893,132,-679,61,1000,-929,275,-1000,-1000,923,-6,388,-1000,-1000,79,423,653,923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setDataExtractOrder(org.jfree.chart.util.TableOrder):void",
            new int[]{-1000,-571,-684,-362,820,18,869,411,-541,-1000,-377,757,962,400,-196,17,749,-991,510,611,482,-694,860,-344,-160,-1000,869,1000,-400,263,1000,-697,693,575,-1000,-400,-736,387,-416,799,881,1000,379,400,850,-802,-35,-381,-1000,796,1000,-645,56,-558,-947,676,-160,265,-23,-906,548,1000,-171,-34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setDataExtractOrder(org.jfree.chart.util.TableOrder):void",
            new int[]{-287,-514,-12,737,-26,-1000,348,116,-903,-275,323,-400,434,844,1000,658,501,-476,952,971,57,585,72,-289,-1000,343,1000,-289,-613,-516,-966,-576,-293,196,-218,-487,-483,-307,-734,-669,618,658,-837,549,288,-621,-1000,566,-136,562,-864,-781,1000,-726,304,927,349,188,-460,-400,-457,872,-301,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setDataExtractOrder(org.jfree.chart.util.TableOrder):void",
            new int[]{452,530,278,624,-374,-1000,-208,925,619,795,1000,1000,360,-1000,-904,-461,-1000,-346,-452,526,-942,-648,111,121,488,-931,-171,-798,832,1000,-225,242,386,1000,0,610,866,501,950,712,16,-86,-1000,-615,-186,-84,1000,43,1000,-639,620,-781,472,196,-1000,-201,113,292,-1000,-1000,-1000,-895,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setDataset(org.jfree.data.category.CategoryDataset):void",
            new int[]{-671,1000,-451,404,932,109,-153,719,-508,201,-1000,-671,1000,-1000,600,1000,850,-35,573,-140,824,207,-278,844,-1000,-890,-3,-419,912,559,-1000,-24,1000,1000,756,180,-224,203,-233,-1000,-253,860,425,824,-1000,-542,355,-696,-1000,-780,-995,1000,367,-725,233,-754,-495,701,101,-170,808,-241,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setDataset(org.jfree.data.category.CategoryDataset):void",
            new int[]{-759,-691,-473,722,-147,-986,-1000,625,541,-708,919,-287,-666,-300,-231,-113,-343,403,623,161,-8,-101,272,-416,-129,-45,-122,-1000,260,708,380,285,-58,306,-513,106,-478,573,-445,-190,-723,744,-1000,55,630,-230,16,-353,400,-476,546,139,105,-1000,-348,-363,-65,1000,-370,-113,-311,457,-580,-756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setDataset(org.jfree.data.category.CategoryDataset):void",
            new int[]{-694,-1000,-281,594,-472,-95,680,-1000,1000,751,1000,-317,-428,55,-104,-1000,-686,786,711,-1000,-334,-1000,652,-1000,144,-435,-673,-1000,-310,4,1000,591,616,-991,-1000,672,-494,580,-897,1000,-1000,1000,-1000,323,1000,-1000,936,-1000,38,1000,996,-1000,-1000,-1000,-919,-747,-252,160,117,-364,-461,-675,-1000,-546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("VOID|getLimit=java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setLimit(double):void",
            new int[]{238,584,-407,404,-915,-563,-158,-401,-213,-110,438,438,-400,-168,35,-690,88,-959,-175,-727,344,-765,175,-602,290,-234,172,-80,498,353,-525,-493,-113,-201,414,-1000,1000,-371,395,-362,-851,-68,-201,-951,-205,498,-122,432,-201,-637,-440,-872,-168,91,1000,-397,400,153,-371,361,426,-363,724,-679}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("VOID|getLimit=java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setLimit(double):void",
            new int[]{352,723,-533,-941,-609,134,-1000,63,-477,396,425,607,20,-417,-344,-797,134,-1000,-886,-508,-59,-488,787,-301,20,23,-16,688,-1000,547,-853,-1000,142,-267,-773,-1000,883,-1000,571,549,-1000,-141,535,-687,400,1000,140,271,519,-59,10,-592,-445,-469,838,-162,13,815,88,1000,-639,105,1000,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("VOID|getLimit=java.lang.Double:LTY0NS4w", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setLimit(double):void",
            new int[]{-908,1000,-492,-480,-996,-146,133,357,71,-151,-529,1000,240,-709,160,-645,-25,-1000,193,-953,237,-92,-465,-508,710,-289,-593,-152,509,931,-514,-696,755,-222,398,-1000,692,-791,-637,571,-1000,-830,296,-262,-887,361,1000,432,-990,-497,-557,59,153,84,491,65,-875,-868,-205,-253,160,-544,1000,-800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setPieChart(org.jfree.chart.JFreeChart):void",
            new int[]{-237,266,-486,470,-416,-550,414,24,-129,882,-420,352,220,970,-676,-1000,-542,-1000,-911,-725,-325,216,207,-1000,106,916,302,658,517,-524,1000,-618,355,-1000,-1000,-179,-311,15,-982,255,-448,77,-627,465,376,1000,-73,677,-839,-994,-995,13,-858,-849,1000,76,-200,-512,-77,721,592,-188,171,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setPieChart(org.jfree.chart.JFreeChart):void",
            new int[]{-1000,-761,-479,-215,712,331,-315,-1000,-951,838,505,580,580,738,-540,273,261,-187,689,1000,-612,-408,706,-917,-386,-1000,781,296,-785,437,-532,-913,-1000,136,317,566,-574,-769,-737,925,673,-720,-191,-387,1000,-708,377,-518,-1000,430,222,-1000,-363,158,719,-710,-977,890,145,-961,350,868,-628,-136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.MultiplePiePlot", "org.jfree.chart.plot.MultiplePiePlot", "setPieChart(org.jfree.chart.JFreeChart):void",
            new int[]{-205,-1000,573,-947,195,-791,-141,478,1000,-938,138,-655,532,505,-247,968,-1000,368,-190,697,1000,299,234,400,-1000,1000,-424,335,-985,24,400,1000,39,810,-1000,528,406,490,157,57,1000,592,-1000,-573,-102,-97,-186,-1000,604,753,164,254,-338,74,-1000,-3,-350,304,1000,55,-222,-1000,-993,-545}));
    }
}
