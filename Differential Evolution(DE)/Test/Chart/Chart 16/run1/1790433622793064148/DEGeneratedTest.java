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
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultIntervalCategoryDataset|getRowCount=22:java.lang.Integer:Mg==|getColumnCount=22:java.lang.Integer:MQ==|getSeriesCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "clone():java.lang.Object",
            new int[]{282,1000,-58,-341,311,-328,1000,1000,250,983,-34,1000,551,-26,839,-94,139,-415,704,-612,86,-457,565,1000,3,-415,600,-64,-91,-98,-191,-941,-214,-888,296,-237,-1000,-214,664,-1000,541,731,-1000,35,438,-530,266,1000,307,130,-23,-1000,-453,162,511,262,173,831,680,-175,32,644,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultIntervalCategoryDataset|getRowCount=22:java.lang.Integer:MA==|getColumnCount=22:java.lang.Integer:MA==|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "clone():java.lang.Object",
            new int[]{526,813,-156,-678,-962,311,-387,-163,-348,296,-983,679,-90,681,800,-204,-75,-632,267,-652,927,645,932,667,843,868,675,634,-104,-867,-678,-781,-150,-879,976,-573,-773,-657,-46,-1,-407,587,-311,-213,844,-448,-402,959,302,350,-423,-82,-588,866,-481,130,807,781,64,-38,-357,499,637,-975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultIntervalCategoryDataset|getRowCount=22:java.lang.Integer:NA==|getColumnCount=22:java.lang.Integer:NA==|getSeriesCount=22:java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "clone():java.lang.Object",
            new int[]{-12,673,592,1000,675,505,351,-432,933,-233,850,679,51,-1000,-75,-374,-509,-632,400,-560,-440,-409,764,-670,843,868,206,73,1000,-1000,-678,1000,-417,1000,976,101,-175,1000,370,1000,-303,852,-503,830,-993,-448,1000,959,-930,556,568,-1000,836,310,320,127,-913,-1000,64,-457,279,-1000,388,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultIntervalCategoryDataset|getRowCount=22:java.lang.Integer:Mg==|getColumnCount=22:java.lang.Integer:NQ==|getSeriesCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "clone():java.lang.Object",
            new int[]{758,-874,230,648,-764,-550,-630,-956,53,650,-664,-817,631,493,-998,847,402,-782,922,26,703,891,-690,962,203,-648,-859,51,-878,728,-431,-403,90,941,-779,930,240,-321,490,-541,-115,-100,414,854,-59,80,-590,-246,-822,-835,-230,130,-680,-564,-440,-248,-967,365,384,115,928,953,-688,-434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultIntervalCategoryDataset|getRowCount=22:java.lang.Integer:MA==|getColumnCount=22:java.lang.Integer:MA==|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "clone():java.lang.Object",
            new int[]{131,-387,0,-456,-962,658,175,-328,-1000,-88,841,534,419,1000,210,-306,314,-1000,263,254,-44,455,-638,-1000,-888,-624,811,0,-158,1000,-1000,-582,688,0,-633,256,315,-49,-1000,-3,1000,670,485,-31,621,-1000,-824,542,-854,311,-1000,708,100,-480,-944,-157,411,-72,232,-213,1000,1000,-293,-56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultIntervalCategoryDataset|getRowCount=22:java.lang.Integer:MA==|getColumnCount=22:java.lang.Integer:MA==|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "clone():java.lang.Object",
            new int[]{1000,149,450,204,-418,603,-1000,-1000,-1000,-934,599,-376,888,-1000,1000,-415,-356,-1000,1000,-495,-780,392,341,-1000,1000,507,84,-598,-373,5,-152,409,465,486,729,962,840,1000,468,-124,657,680,536,119,-989,428,491,972,-1000,678,-1000,-188,1000,1000,-1000,328,-746,-153,-625,-288,-202,480,-388,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "equals(java.lang.Object):boolean",
            new int[]{118,-1000,1000,57,322,1000,798,661,324,49,-1000,-554,-353,493,-75,635,-395,-1000,158,1000,1000,-1000,-246,-57,-518,1000,-706,522,781,18,-678,1000,-15,746,115,-711,-293,-488,524,86,194,-304,279,-9,466,1000,-1000,-1000,-1000,-969,400,141,1000,407,-516,-993,1000,212,-1000,740,119,-686,473,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "equals(java.lang.Object):boolean",
            new int[]{-719,-106,-891,1000,-1000,-592,887,-131,837,264,580,323,-658,-962,-834,-420,1000,156,-1000,52,-702,462,678,-336,-401,467,1000,-266,1000,-1000,1000,-204,1000,-334,18,-324,-921,1000,-258,214,103,457,147,-400,-361,-736,-526,-583,906,-408,312,-655,-245,-120,-118,-168,-680,70,-375,-757,193,902,313,314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "equals(java.lang.Object):boolean",
            new int[]{419,1000,1000,-628,759,388,1000,1000,-1000,-900,-1000,-217,824,1000,-1000,-1000,214,-925,758,-68,1000,-1000,-618,1000,-1000,724,-1000,587,-238,953,-1000,1000,-560,463,457,-277,158,-879,390,-1000,-269,-995,188,-1000,1000,20,-245,647,-786,44,-603,985,93,1000,-299,-1000,-389,776,-418,388,133,-454,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "equals(java.lang.Object):boolean",
            new int[]{701,1000,-1000,-30,-32,385,-60,-114,749,-160,305,-931,644,-1000,96,1000,-111,-912,-1000,-256,603,-129,807,1000,243,1000,507,499,-1000,218,-436,-971,-1000,-1000,-548,-1000,272,478,-144,-683,338,993,-837,412,520,407,183,-175,1000,-1000,-1000,-582,1000,-810,519,-1000,-638,141,-620,-41,-559,-503,1000,28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "equals(java.lang.Object):boolean",
            new int[]{-707,-814,1000,1000,-999,-340,887,-52,82,-196,-806,1000,641,-1000,-1000,-525,90,742,-540,-306,-72,-359,-93,194,-1000,316,-275,668,554,-282,-615,706,-102,591,363,-1000,267,305,819,-876,-397,-13,-451,623,1000,-1000,796,15,420,-340,63,1000,672,941,-1000,-1000,800,1000,-603,-246,100,946,311,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getCategoryCount():int",
            new int[]{-951,653,887,-428,-584,-1000,-137,-902,357,503,129,-1000,252,-236,-229,-68,908,279,400,-722,-226,1000,314,-513,1000,-153,-679,-427,-803,691,-1000,1000,-565,749,1000,-1000,-508,-1000,-565,-266,838,763,-681,-543,365,-1000,-759,-4,-702,493,766,87,-233,976,290,492,829,-844,-383,-363,-175,-400,1000,953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getCategoryCount():int",
            new int[]{-1000,-406,916,475,-1000,1000,266,-222,1000,387,-691,-1000,71,1000,-1000,717,-1000,67,1000,-230,956,-318,-398,-169,-629,-1000,101,-406,474,-114,-406,-939,613,-1000,-118,1000,1000,164,1000,922,236,-241,765,-297,-918,559,956,-731,-936,924,-201,-68,283,-248,-566,-530,-352,1000,99,639,423,-1000,391,-461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getCategoryCount():int",
            new int[]{13,0,192,0,-851,-1000,-694,842,0,702,1000,-571,-1000,423,-324,1000,731,-1000,1000,-425,58,888,-33,-250,-429,-191,15,-1000,-676,-258,730,-355,-552,1000,-54,20,1000,-1000,894,0,-296,760,-1000,-190,-1000,-379,0,391,-775,429,46,-49,1000,180,580,593,-302,1000,542,518,570,-1000,88,-360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getCategoryCount():int",
            new int[]{464,-524,573,-658,826,-516,-702,654,-245,895,1000,-877,-1000,1000,-96,1000,1000,-1000,-11,487,-674,-1000,201,-1000,-1000,433,-577,221,879,-1000,543,-524,1000,703,-1000,1000,-6,200,1000,615,293,1000,-745,-465,-556,927,695,-41,-1000,-215,-1000,1000,-1000,-1000,-332,-531,-847,669,826,-148,1000,-898,-1000,-967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getCategoryCount():int",
            new int[]{-635,44,845,1000,-164,-847,118,-17,185,613,-352,207,107,-351,-637,-471,1000,799,1000,325,-691,966,-80,460,1000,734,-216,-631,470,-279,-223,1000,-434,1000,556,-1000,993,198,69,-988,926,-58,-938,395,147,-1000,-205,796,-630,177,907,745,301,702,731,838,332,1000,-964,-260,-175,384,-676,771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getCategoryIndex(java.lang.Comparable):int",
            new int[]{-335,892,-49,-370,-149,233,-363,1000,-275,995,-400,-817,526,1000,-305,526,1000,1000,1000,308,-411,-1000,-1000,-1000,-1000,-691,574,750,-817,-1000,-356,1000,-600,268,-467,-622,1000,682,-1000,-352,446,-1000,90,-962,1000,-428,528,1000,560,344,-1000,-407,1000,-523,457,-1000,-767,3,534,-674,403,-426,455,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getCategoryIndex(java.lang.Comparable):int",
            new int[]{1000,254,-682,-1000,704,1000,659,-893,-818,-668,944,-640,-833,-146,7,-188,248,784,313,145,1000,122,-1000,833,652,1000,1000,140,435,368,-373,453,-251,61,720,205,78,487,488,-793,520,803,-1000,-926,-1000,1000,-657,-739,223,99,-98,-160,-1000,620,948,-300,-163,529,-436,-259,628,92,89,-594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getCategoryIndex(java.lang.Comparable):int",
            new int[]{591,400,-440,-1000,1000,-1000,-486,-1000,1000,-122,-1000,973,374,-829,-637,-1000,-1000,-1000,-1000,-1000,-1000,1000,648,1000,8,858,-1000,-1000,402,763,1000,-1000,299,-1000,1000,-1000,-1000,-1000,158,438,-1000,320,-886,1000,258,-1000,647,-1000,240,-1000,1000,472,228,207,-1000,1000,-550,678,-1000,1000,-1000,1000,-428,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getCategoryIndex(java.lang.Comparable):int",
            new int[]{477,634,-440,-1000,807,906,85,-1000,566,-122,-715,186,177,-611,-637,-933,-745,-582,-522,-481,400,1000,404,1000,459,949,728,-795,331,915,192,-618,-55,-964,1000,-81,-462,-506,-588,438,-1000,451,-1000,1000,-500,-1000,479,-1000,468,-226,828,487,-762,-835,376,149,-647,450,-712,840,-606,1000,-1000,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getCategoryIndex(java.lang.Comparable):int",
            new int[]{-710,-263,965,-266,733,-876,206,-965,652,516,-964,121,-51,98,815,-573,-739,284,-538,-424,-996,594,-250,823,441,-513,-870,-427,-237,18,687,-428,-207,-280,453,-743,-946,-252,-501,596,-319,-38,-25,727,-60,-603,586,-266,-57,-659,859,-59,583,-889,-988,287,-336,555,-782,-473,-254,270,637,273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnCount():int",
            new int[]{-730,-1000,790,1000,-122,63,-1000,-543,684,75,-579,470,496,637,134,756,-66,-818,-1000,-717,1000,19,650,-114,-463,-767,670,-348,-1000,930,-917,86,-628,-192,-71,652,1000,-1000,-1000,-205,-1000,141,-125,-388,-785,-539,1000,-1000,495,-1000,-1000,-962,58,-238,-662,-1000,-1000,-900,622,219,839,1000,129,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnCount():int",
            new int[]{-781,-756,-708,-720,233,-999,1000,50,-1000,-1000,1000,-1000,327,-1000,552,99,-1000,-1000,-433,1000,-120,-1000,-1000,-1000,-308,1000,-1000,587,-454,-736,377,-319,-901,953,1000,-386,-1000,594,1000,-120,-1000,919,321,1000,-365,179,1000,-300,-988,-347,1000,-92,157,-888,1000,-3,1000,1000,-526,-521,-1000,-1000,1000,-78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnCount():int",
            new int[]{981,1000,310,-1000,-887,-202,1000,1000,-1000,440,1000,-1000,-1000,-1000,823,-1000,-452,-460,-174,1000,-1000,-609,-1000,714,1000,1000,-1000,71,679,-760,1000,-551,-880,486,-250,-135,-15,1000,1000,902,400,-735,-542,1000,863,1000,-357,1000,-375,1000,193,-759,-1000,789,-872,1000,1000,1000,-1000,-353,-1000,-1000,497,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnCount():int",
            new int[]{-165,-400,-67,29,-1000,740,288,387,-302,-120,534,-1000,741,-262,-61,-613,-307,458,665,36,-20,20,-565,1000,262,376,-754,-1000,759,-469,1000,-796,-648,467,486,-722,623,311,620,1000,225,-303,-446,701,899,317,84,447,201,306,-420,640,132,776,-1000,980,365,223,-1000,-550,-358,20,-663,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnCount():int",
            new int[]{672,-1000,-524,-1000,-782,-432,1000,825,-1000,-691,1000,-1000,-665,-1000,1000,-1000,-452,-460,141,1000,-1000,-1000,1000,-352,1000,1000,-1000,1000,345,-1000,1000,-825,-880,828,-695,-1000,-1000,1000,1000,1000,341,232,66,1000,906,1000,148,-400,-1000,-364,613,-375,-683,-98,107,-1000,1000,1000,-1000,-551,-1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnCount():int",
            new int[]{-955,-402,1000,-727,107,300,231,750,-1000,-347,573,-1000,647,-653,566,386,-1000,-1000,-1000,1000,-236,-565,-1000,-765,110,831,-1000,1000,-1000,-161,-465,-189,-959,492,246,411,-435,-1000,626,1000,-1000,432,144,837,-1000,761,1000,717,-125,1000,-300,-1000,440,-553,637,-380,1000,455,-678,229,-656,-864,-300,662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnIndex(java.lang.Comparable):int",
            new int[]{-234,-1000,-814,-22,612,885,-1000,-330,-885,1000,1000,-392,-362,446,-714,-1000,-1000,-982,184,1000,-694,1000,-608,1000,330,-546,-348,-440,-1000,-1000,-146,1000,-1000,-1000,-1000,-1000,218,1000,967,-161,718,591,932,-1000,918,828,-874,-1000,1000,-717,174,-968,-1000,1000,-1000,-803,-1000,576,1000,-320,1000,591,-1000,68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnIndex(java.lang.Comparable):int",
            new int[]{516,786,-860,-298,549,474,-806,13,-85,300,-413,-949,-39,722,-129,87,330,962,32,-321,-888,950,-358,226,438,907,856,395,152,268,684,564,173,-404,963,687,675,113,-978,205,331,-486,158,-20,520,-132,741,627,-404,-453,-8,52,126,401,-668,-449,-653,-342,533,369,979,-585,-125,-697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnIndex(java.lang.Comparable):int",
            new int[]{-362,1000,-1000,862,1000,194,812,1000,-1000,-940,-349,-1000,-1000,1000,1000,1000,1000,-1000,1000,-939,-1000,1000,-1000,-46,47,-306,813,-1000,715,52,-364,-561,-406,-406,-1000,1000,672,-1000,-1000,1000,498,-1000,1000,-414,-691,-144,-338,-1000,-1000,-446,1000,317,43,-899,-601,-1000,559,-1000,1000,893,1000,-547,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnIndex(java.lang.Comparable):int",
            new int[]{22,400,-1000,1000,717,-413,39,-907,-1000,175,108,-511,-1000,-39,686,0,400,-370,1000,609,-439,657,-1000,1000,-841,802,1000,-1000,174,327,-1000,-400,-721,-422,-983,280,-397,-352,27,724,147,-809,1000,-1000,-482,0,379,-1000,133,-545,1000,432,-1000,-93,-1000,-704,60,-344,64,-1000,237,0,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnIndex(java.lang.Comparable):int",
            new int[]{565,-70,-860,342,-579,1000,-199,-654,583,849,68,-375,-22,443,-703,-149,-578,562,-869,214,-640,394,53,-507,495,256,-529,497,254,-704,917,310,778,839,39,511,1000,205,-333,320,1000,832,206,492,712,254,753,-519,-87,-1000,-746,-689,832,-450,-844,665,54,512,-504,-147,-596,575,873,-422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnIndex(java.lang.Comparable):int",
            new int[]{-583,335,84,300,876,300,498,498,249,-160,461,464,261,295,1000,304,-23,-1000,812,51,-43,125,-808,359,-89,274,1000,-1000,-1000,1000,-663,-850,-482,163,-1000,755,146,486,1000,240,-423,-946,1000,-591,-115,546,1000,-300,-300,-263,1000,-226,-1000,-300,140,208,-566,300,-1000,944,616,864,-568,35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnKey(int):java.lang.Comparable",
            new int[]{-1000,1000,-1000,-1000,-1000,734,-348,1000,-69,-203,513,584,400,-1000,795,602,212,-305,-359,288,-200,-683,-42,96,529,1000,-1000,-593,-712,876,-1000,-1000,-541,400,1000,-1000,850,541,-1000,292,574,-81,1000,-1000,400,-211,309,-337,794,-100,1000,1000,288,722,400,666,292,-1000,-896,472,-1000,-402,902,789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgMQ==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnKey(int):java.lang.Comparable",
            new int[]{-673,1000,1000,-1000,-1000,-405,1000,1000,1000,-1000,1000,1000,-787,-265,1000,1000,701,-1000,1000,1000,-1000,-231,0,1000,-1000,-1000,298,-969,-770,-560,1000,736,-1000,-947,1000,279,-1000,-197,292,-33,-420,515,126,0,-869,660,-1000,690,-679,-471,1000,433,0,-58,-1000,1000,318,-1000,-965,-496,42,1000,737,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnKey(int):java.lang.Comparable",
            new int[]{-548,845,228,-732,-556,1000,-371,-1000,-242,-216,29,980,461,307,-878,755,858,627,-7,139,1000,1000,78,901,8,-554,567,-1000,-908,20,-153,-633,625,-27,-1000,-20,1000,-1000,-781,400,-21,587,-1000,20,264,1000,-172,738,-404,1000,717,-143,1000,19,473,258,-440,562,-404,-117,434,-452,-853,-835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnKey(int):java.lang.Comparable",
            new int[]{1000,-891,1000,-605,198,692,1000,-1000,-443,-494,-383,688,-834,953,-1000,625,-301,340,-136,195,844,1000,-1000,454,565,61,38,-35,-850,450,42,27,45,-103,-466,-1000,389,-812,674,-143,-1000,221,-526,1000,-146,188,400,375,-868,501,-104,-1000,561,-992,-642,778,620,1000,-543,-1000,524,-1000,-788,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnKeys():java.util.List",
            new int[]{1000,-1000,-1000,-1000,-985,1000,1000,527,869,1000,727,-341,-582,-1000,-1000,705,-1000,1000,-65,-70,-1000,-363,-1000,-911,338,1000,1000,619,-640,1000,948,-58,84,654,-641,-216,-366,1000,1000,-50,1000,-306,-661,-659,-1000,-1000,375,997,300,1000,-724,-1000,-310,300,-728,-300,-875,-1000,-701,1000,-425,-1000,-2,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnKeys():java.util.List",
            new int[]{469,-400,1000,-400,-147,312,400,264,-308,400,214,1000,-394,-400,-908,397,566,400,1000,-251,-400,-922,-400,407,-303,305,570,400,-400,1000,523,-1000,173,-174,-163,-1000,-325,365,115,-530,400,877,8,-18,-1000,-400,1000,-68,400,400,74,-527,-182,400,-239,-400,-310,-559,-327,116,-717,-400,776,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnKeys():java.util.List",
            new int[]{-180,-300,300,-300,327,294,-121,31,558,-79,300,403,482,-300,-612,-1000,-300,300,214,1000,-300,65,-1000,816,-231,308,1000,-620,-991,691,-732,-621,-397,-583,506,1000,1000,65,-345,-1000,1000,-1000,821,-384,1000,125,1000,786,702,-1000,-189,-1000,453,-1,140,555,-975,343,-533,253,-96,-390,1000,-300}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnKeys():java.util.List",
            new int[]{262,1000,-1000,1000,1000,763,-1000,-1000,885,-147,1000,1000,-1000,1000,557,-141,1000,-1000,1000,-123,676,422,-449,-542,-709,-1000,635,-1000,259,-377,-1000,582,364,-449,1000,960,311,-962,-595,-1000,-1000,-1000,1000,644,-135,1000,-606,1000,-819,-1000,621,-1000,734,-1000,-998,1000,643,897,-895,-793,1000,1000,-318,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnKeys():java.util.List",
            new int[]{-789,1000,-1000,1000,1000,-533,-1000,-508,869,-1000,1000,1000,-1000,1000,1000,-1000,-380,-1000,1000,-70,1000,606,1000,-911,-1000,-859,-2,-1000,1000,-1000,-1000,152,218,-545,1000,741,-351,-950,-1000,-114,-1000,-824,1000,1000,-1000,1000,388,-332,-1000,-1000,-113,-1000,1000,-1000,-520,1000,871,719,-733,-594,1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getColumnKeys():java.util.List",
            new int[]{-53,510,300,510,510,358,-510,353,511,-169,362,1000,-1000,1000,569,-565,-604,-510,510,-1000,161,963,-449,-666,-505,-270,569,-1000,-138,1000,-1000,572,-1000,-769,1000,536,211,-810,-507,4,-510,-1000,1000,-165,550,510,928,1000,-1000,-790,-1000,-1000,-151,-510,-448,1000,145,799,-910,-69,-11,510,-338,510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuOQ==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(int,int):java.lang.Number",
            new int[]{896,645,-400,-1000,418,-1000,-1000,1000,989,-400,-85,-685,200,-400,1000,-400,-280,-156,419,98,776,779,69,337,569,-1000,-29,-734,-545,-779,158,-106,824,-850,-107,231,-850,681,-400,-53,1000,1000,-321,959,107,-975,-11,498,-152,1000,-347,621,690,-471,254,619,1000,-3,107,115,-1000,191,102,149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(int,int):java.lang.Number",
            new int[]{-1000,948,-1000,1000,764,494,210,-251,82,1000,1000,-820,619,73,-400,-400,-512,935,974,98,-447,183,69,-429,-1000,-1000,-1000,-533,-545,145,662,-317,-1000,-850,-558,85,720,534,-362,-53,-531,346,-1000,853,-229,1000,859,-400,-1000,-897,-704,371,636,-523,-852,-1000,-472,-579,-1000,-1000,1000,1000,-585,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(int,int):java.lang.Number",
            new int[]{20,-1000,296,-162,430,-869,-992,592,1000,593,-218,-1000,-797,242,954,381,-954,-300,-171,1000,1000,922,-246,520,0,637,-20,-1000,0,-585,276,-85,18,-627,204,796,-737,741,333,948,-195,597,96,219,456,-1000,0,14,-1000,960,20,-435,1000,929,202,0,-179,-797,931,369,-673,731,-778,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(int,int):java.lang.Number",
            new int[]{123,1000,-756,-486,-226,-1000,-1000,167,1000,-221,596,-388,-532,-547,1000,-517,26,430,892,1000,1000,994,86,-793,1000,-799,1000,-964,507,-153,-657,-192,-247,-76,1000,-402,-464,472,-1000,894,173,-88,971,45,-519,-1000,-196,537,-594,1000,-1000,890,-289,-270,-628,4,-194,-1000,1000,-293,-334,215,403,-453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(int,int):java.lang.Number",
            new int[]{738,629,-204,-300,-352,-1000,-1000,417,989,-709,1000,-828,-530,-1000,1000,46,-971,844,-490,1000,894,646,641,-219,895,-1000,1000,-849,-13,184,320,675,-247,-88,-138,634,-249,311,-1000,821,-126,1000,1000,332,-384,-1000,552,1000,-1000,1000,-1000,1000,1000,-480,-885,-39,1000,-1000,216,-781,-330,-29,464,-701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(int,int):java.lang.Number",
            new int[]{1000,-78,1000,1000,1000,-1000,-1000,1000,1000,1000,-1000,-589,446,1000,1000,1000,-1000,-1000,1000,1000,1000,691,-1000,736,-204,1000,-1000,-1000,8,-469,1000,223,-949,-853,956,-113,-677,1000,1000,1000,-1000,573,1000,633,1000,-1000,-1000,-1000,1000,1000,1000,-1000,-1000,1000,1000,202,-278,1000,1000,1000,-185,1000,-280,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(int,int):java.lang.Number",
            new int[]{-84,645,-1000,-1000,-152,-1000,-1000,592,989,-807,697,-727,-383,-1000,1000,-1000,-234,544,754,98,776,776,350,-143,1000,-1000,951,-734,1000,-364,-442,290,741,-477,1000,524,-588,422,-1000,-105,217,765,155,574,-437,-964,383,1000,-933,1000,-1000,1000,1000,-542,-543,216,717,-1000,38,-515,-673,-78,324,-216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(int,int):java.lang.Number",
            new int[]{1000,71,1000,1000,1000,-1000,-1000,1000,1000,1000,-1000,-1000,1000,1000,1000,1000,-1000,-1000,1000,1000,1000,1000,-1000,1000,-250,481,-1000,-1000,-939,-1000,1000,632,-1000,-1000,-113,-484,-1000,1000,1000,1000,-1000,737,1000,1000,1000,-1000,-1000,-1000,1000,1000,1000,-1000,-284,948,928,98,-591,1000,1000,1000,-396,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-466,-1000,989,1000,614,1000,-1000,499,1000,-1000,641,1000,1000,102,-216,-432,379,1000,780,1000,1000,-978,-604,-1000,-966,859,1000,215,1000,761,471,-494,-1000,-1000,436,-1000,1000,-1000,335,-74,-751,1000,-1000,1000,-575,-1000,-1000,795,1000,1000,1000,-1000,-1000,-1000,1000,-128,-834,-677,227,-457,784,-1000,-1000,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{797,-891,1000,1000,-175,1000,-1000,541,1000,-835,352,1000,1000,215,-1000,28,-406,920,620,-478,1000,-198,-538,-769,-71,751,1000,1000,1000,966,1000,-1000,-1000,-1000,-454,-946,826,-1000,733,408,-842,1000,-1000,-1000,-381,-1000,860,937,691,1000,-406,-206,-938,-1000,300,544,-893,-1000,1000,259,1000,-398,-1000,-944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{258,-270,1000,508,-999,400,53,-18,812,-285,-155,-824,878,1000,-400,149,-216,1000,490,-603,400,-885,-880,-1000,-875,-1000,865,1000,400,1000,1000,1000,-554,-1000,-18,-1000,1000,-952,652,1000,-1000,1000,1000,-1000,-494,-1000,1000,1000,1000,1000,16,-532,-1000,-400,713,1000,-1000,-1000,479,-119,1000,-300,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getEndValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{39,-786,-1000,1000,1000,168,398,-924,1000,-410,408,78,457,-866,-1000,-1000,561,-908,620,316,946,1000,-6,1000,884,864,974,765,916,-1000,1000,-300,-1000,-667,-223,1000,-762,283,315,-870,-842,372,-731,1000,992,-793,-709,-892,-959,132,172,569,520,-1000,276,544,-617,-785,457,301,393,996,-1000,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowCount():int",
            new int[]{-1000,1000,-1000,-1000,187,-1000,-584,-1000,822,-1000,-106,208,588,-1000,1000,-776,0,-1000,-1000,0,1000,0,770,1000,-112,973,-70,1000,833,481,1000,277,-1000,-43,312,-1000,0,1000,0,73,216,1000,-95,1000,-1000,860,-1000,0,-656,632,1000,1000,1000,1000,-1000,1000,-1000,-1000,-107,-1000,537,-420,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowCount():int",
            new int[]{0,655,156,-420,648,0,-546,1000,-363,-778,0,536,103,-424,1000,-569,0,0,111,-248,0,-247,-306,1000,-1000,490,466,0,455,157,453,502,-29,526,-699,0,-1000,-17,172,0,841,-105,-880,1000,272,-321,0,-96,964,-133,-1000,-683,-39,-420,252,989,419,0,503,-1000,-330,0,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowCount():int",
            new int[]{-642,454,-1000,-1000,648,-97,-387,1000,-825,-775,0,536,-15,-767,1000,-1000,-343,-490,-597,-114,134,99,-278,1000,-1000,806,649,-96,954,-45,668,362,9,363,-120,-343,-1000,102,172,-112,841,822,-823,1000,-94,-1000,23,-96,831,577,-941,-781,268,-303,-400,1000,306,0,671,-539,0,0,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowCount():int",
            new int[]{-1000,-976,666,-420,-966,0,-148,645,-1000,-254,1000,-818,614,-1000,-711,-129,717,-1000,1000,1000,1000,-609,1000,1000,-658,-1000,-1000,1000,137,-1000,54,-296,-412,368,-699,-1000,1000,-1000,-1000,845,841,69,-724,1000,1000,-241,150,-797,-1000,-150,1000,-272,-252,1000,252,221,1000,243,866,-1000,-1000,1000,-1000,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowIndex(java.lang.Comparable):int",
            new int[]{-746,1000,-1000,343,-932,-1000,411,282,-436,154,-776,-387,683,-334,-965,-1000,-321,1000,-432,-1000,-1000,-606,554,-556,-554,400,869,1000,-1000,-1000,-670,-156,-145,1000,626,-1000,703,1000,731,-186,107,975,1000,-737,-307,-57,-1000,-1000,1000,-473,-1000,-338,983,691,-530,-19,-845,606,1000,-422,-1000,837,1000,-816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowIndex(java.lang.Comparable):int",
            new int[]{0,901,678,-636,-36,-302,0,1000,0,1000,1000,-1000,-8,-246,0,216,-932,-581,-220,236,813,-1000,87,0,718,626,-853,0,364,-143,0,-1000,479,0,28,0,1000,-393,502,264,-529,-534,784,-875,1000,-350,181,1000,0,890,714,-1000,457,840,205,655,0,229,-786,830,1000,-1000,113,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowIndex(java.lang.Comparable):int",
            new int[]{-433,-1000,-458,1000,-1000,-61,-1000,-1000,92,-524,-1000,1000,-1000,1000,-1000,-716,1000,948,1000,-2,-1000,1000,-1000,222,563,290,1000,1000,1000,826,534,1000,300,93,1000,-547,-878,-959,-1000,-850,716,593,-1000,-1000,-401,73,-901,1000,137,-1000,-441,1000,41,-1000,-1000,1000,-1000,-1000,1000,-281,-525,576,911,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowIndex(java.lang.Comparable):int",
            new int[]{-1000,-302,-1000,1000,-1000,-1000,-470,-761,-267,-619,-1000,1000,-11,998,-1000,-1000,1000,1000,719,-1000,-1000,794,-266,-509,185,770,1000,1000,400,629,-123,1000,65,1000,1000,-1000,-19,-331,-669,-966,-1000,1000,-249,-1000,-97,-706,-528,400,1000,-1000,-987,705,466,-709,-1000,630,-1000,-683,1000,-1000,-1000,773,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowIndex(java.lang.Comparable):int",
            new int[]{700,861,338,-69,251,443,259,490,-150,581,1000,-1000,-8,-904,59,190,-1000,99,-644,50,363,-1000,624,155,-494,-239,-853,-653,-1000,787,-247,-1000,0,-1,-654,260,154,942,1000,264,-957,-400,784,343,-701,1000,181,-1000,-195,296,-461,-490,779,1000,542,72,0,842,-297,830,-439,668,81,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowKey(int):java.lang.Comparable",
            new int[]{-1000,-400,189,-1000,-1000,-548,-1000,1000,-722,-1000,-762,835,359,-344,909,1000,-843,990,27,-676,1000,-542,300,-1000,1000,365,784,-311,1000,1000,251,-1000,-579,-417,326,1000,-1000,1000,800,271,-296,117,-779,-365,1000,-863,-400,-304,196,-777,-557,349,-234,-1000,-751,340,-783,122,-1000,-521,1000,53,810,-393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowKey(int):java.lang.Comparable",
            new int[]{618,421,0,942,-221,918,0,674,-922,147,-309,-10,1000,-679,-541,611,170,991,581,-376,-323,-481,546,419,-1000,-367,377,-384,-440,-258,-1000,122,-588,595,-317,-141,900,-483,-195,650,-264,-129,868,-65,-464,245,1000,996,606,409,-761,459,-59,1000,-1000,-679,-881,406,-1000,372,-580,-1000,429,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowKey(int):java.lang.Comparable",
            new int[]{-1000,68,-71,-718,-540,-407,-404,601,-197,-1000,-381,-469,249,-344,-798,370,-122,79,-53,2,300,-939,73,-1000,1000,291,496,313,143,-221,-1000,-553,377,-590,547,-958,-188,-109,642,-350,700,777,-591,-1000,1000,-1000,-584,686,280,662,209,1000,-629,596,-381,-112,10,-555,-345,549,1000,1000,-719,377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.String:U2VyaWVzIDE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowKey(int):java.lang.Comparable",
            new int[]{-48,-252,814,-981,-28,-781,370,-608,1000,-1000,-187,347,-388,-300,-484,488,-199,-244,263,750,-897,1000,-470,-400,13,-177,350,-120,2,130,-939,-156,300,-300,1000,-304,-644,-32,-465,303,911,-438,162,832,189,643,-298,180,-104,1000,256,-40,-141,967,744,-14,300,829,224,562,-145,474,-204,-623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowKeys():java.util.List",
            new int[]{1000,-686,601,1000,-492,-824,-427,451,1000,1000,796,322,1000,781,1000,524,1000,-335,1000,-1000,-1000,-896,661,1000,1000,14,-289,4,-571,780,-1000,152,-44,-957,-1000,-1000,-739,-1000,1000,507,-747,-1000,238,1000,1000,-1000,-360,-1000,-252,-247,1000,1000,1000,-66,768,-300,897,1000,-581,1000,-1000,-1000,932,-998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowKeys():java.util.List",
            new int[]{-479,-463,-1000,1000,202,497,-1000,149,1000,-25,-873,736,1000,-598,1000,586,-1000,-1000,598,-540,1000,-676,-614,-1000,-1000,1000,70,1000,-1000,-51,1000,-890,872,1000,-183,468,1000,1000,-59,1000,861,-609,1000,331,-1000,-331,-54,1000,585,-1000,-1000,-1000,-499,-436,-1000,-1000,1000,343,203,631,373,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowKeys():java.util.List",
            new int[]{-692,1000,-132,-192,38,482,-1000,-835,-790,-813,-602,-639,504,246,15,-438,847,500,-15,-3,601,796,542,-1000,113,68,225,-22,-509,-620,-555,-1000,-762,-1000,-188,1000,-1000,617,468,-60,-296,-400,718,-1000,1000,20,-387,833,-416,-388,-1000,-725,414,253,-273,-194,-182,-627,-612,442,1000,34,-1000,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getRowKeys():java.util.List",
            new int[]{-26,-203,-156,-618,814,-272,-609,-293,-1000,-464,-847,162,-689,-745,384,338,163,776,-789,400,-25,964,445,-911,-331,-546,981,400,734,-314,664,-730,-34,-489,230,126,1000,-154,-890,252,60,921,35,-565,-369,-369,429,770,164,626,-1000,-398,-482,698,-896,-790,-192,-66,-275,636,1000,79,-969,-213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesCount():int",
            new int[]{-400,-400,-530,400,-925,-1000,883,95,216,353,534,781,-206,-685,1000,300,846,-630,149,390,682,400,400,400,-929,-1000,1000,1000,-1000,400,449,-83,950,893,400,758,400,-769,400,400,-400,400,400,243,-1000,-391,-1000,1000,1000,-1000,-400,-296,860,1000,-400,-1000,-1000,400,-174,339,185,-649,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesCount():int",
            new int[]{-116,-720,-351,300,1000,-1000,24,-776,607,-300,-300,-110,-318,-447,25,594,-726,387,-520,-353,-717,720,720,-93,-355,-720,766,300,510,720,486,-749,-1000,289,681,-631,356,-649,630,300,-1000,720,314,-720,-571,-726,775,-1000,764,235,-1000,160,-64,768,-720,-711,-720,616,356,456,-300,1000,-720,-300}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesCount():int",
            new int[]{1000,1000,-546,132,272,-672,329,-101,-612,-653,-1000,301,-60,794,223,-400,863,-1000,-882,420,142,-1000,-1000,-1000,361,1000,-1000,-190,1000,-1000,-16,-55,1000,650,-944,-1000,-480,660,-872,32,551,-1000,-420,1000,1000,1000,859,1000,-1000,299,1000,-279,955,543,1000,1000,1000,-852,19,-562,-397,-467,1000,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesCount():int",
            new int[]{-100,-400,782,1000,-193,-1000,1000,-671,405,-376,-496,521,-206,-155,700,452,-554,-630,-132,403,-615,-1000,628,-636,-1000,-1000,880,1000,-69,19,-41,1000,338,1000,-533,955,872,-769,1000,891,718,605,1000,1000,-1000,-587,326,584,195,400,-1000,952,-32,807,-400,-921,-930,381,-351,1000,-57,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesCount():int",
            new int[]{-1000,-1000,-188,-1000,1000,-1000,-703,-778,-700,-1000,55,1000,-590,1000,295,1000,-1000,833,-1000,-643,-1000,-771,721,-161,-836,1000,-1000,343,1000,1000,1000,400,87,853,1000,-1000,1000,417,792,289,330,992,1000,1000,-723,1000,608,-1000,-378,370,1000,507,317,1000,-1000,-1000,-1000,-409,-671,1000,-572,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesCount():int",
            new int[]{-647,-1000,862,-288,1000,-1000,-349,-1000,-335,-1000,-75,304,-1000,1000,-207,1000,-1000,1000,-1000,-849,-1000,-967,1000,-926,-1000,-275,-321,868,1000,1000,763,1000,-906,-264,776,-1000,1000,623,1000,760,692,1000,1000,1000,-1000,-1000,707,-1000,-299,861,-927,1000,3,606,-1000,-839,-857,1000,-309,1000,-1000,1000,-314,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesCount():int",
            new int[]{-266,-266,625,-969,1000,-266,-401,379,136,-208,673,25,-413,-764,-1000,420,-1000,297,-810,-481,-357,730,560,-48,805,-575,-1000,261,925,238,-588,0,-977,151,-770,-799,82,-212,54,0,180,266,547,838,150,1000,831,-980,-790,-167,-170,-702,-344,-168,-169,-211,-497,-228,970,265,317,-70,713,43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesCount():int",
            new int[]{-61,-357,0,1000,-497,0,1000,-191,18,0,-142,476,430,-1000,0,-885,1000,0,849,1000,1000,265,151,178,-1000,-1000,1000,983,-1000,376,0,-1000,1000,1000,1000,1000,52,-1000,398,-159,-707,0,-323,-136,-1000,-391,-1000,1000,1000,-1000,-1000,0,1000,1000,-400,-991,-795,400,-894,-48,0,-649,-20,434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesIndex(java.lang.Comparable):int",
            new int[]{-1000,-1000,-832,1000,-1000,730,997,-882,1000,1000,726,1000,-1000,-1000,-1000,-382,-1000,-1000,1000,1000,832,-166,-1000,1000,-183,-327,-804,-1000,484,-1000,674,1000,-491,-1000,-1000,-562,679,-174,-1000,-552,1000,-1000,1000,-17,-977,588,-1000,-1000,-1000,738,-1000,1000,-497,-77,-1000,583,-1000,-733,802,1000,-1000,-233,-835,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesIndex(java.lang.Comparable):int",
            new int[]{-827,-155,-1000,1000,-805,828,995,-132,535,476,-583,-1000,-1000,-1000,-616,-301,-1000,-1000,1000,1000,1000,908,-1000,1000,45,-1000,-916,-932,339,-1000,-53,1000,-807,-818,-1000,-505,139,-834,-883,-874,1000,-1000,1000,344,-1000,281,-741,-946,803,436,-1000,1000,-473,671,-1000,-167,-1000,-637,145,1000,-482,-121,-39,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesIndex(java.lang.Comparable):int",
            new int[]{-167,-292,-745,164,1000,509,614,-224,-319,92,726,1000,-52,1000,239,-862,375,38,-475,-25,-240,-166,-1000,553,1000,-327,123,1000,448,-1000,19,275,-90,486,-667,650,-338,977,-399,107,797,-961,-885,362,-672,506,621,50,-1000,-1000,497,-511,1000,264,-857,-1000,-168,-3,-1000,569,-603,1000,-143,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesIndex(java.lang.Comparable):int",
            new int[]{278,821,-1000,1000,-472,968,943,592,625,766,-827,-1000,-1000,-1000,-343,-253,-1000,-1000,1000,394,1000,1000,-1000,1000,934,-1000,-1000,-1000,835,-1000,-739,1000,-956,-691,-992,-401,-365,-1000,-801,-912,1000,-1000,1000,578,-1000,-484,-560,-907,1000,225,-1000,-344,-457,1000,-1000,-175,-679,-1000,386,833,-188,-310,460,951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesIndex(java.lang.Comparable):int",
            new int[]{30,234,-1000,-305,51,826,427,-432,-365,40,-1000,-1000,-1000,-1000,-491,54,-1000,-1000,734,557,1000,1000,-1000,925,-170,-1000,-642,-877,277,-737,853,570,349,-710,-969,-1000,295,-1000,-1000,-475,1000,-1000,1000,1000,-1000,388,-1000,-1000,1000,1000,-960,763,-186,189,-281,-83,-692,177,845,45,-1000,149,548,863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesIndex(java.lang.Comparable):int",
            new int[]{-1000,452,-918,390,127,549,571,-657,659,40,-315,82,-683,-1000,-74,140,-1000,-1000,739,969,810,221,-819,1000,1000,-289,-319,352,172,-830,891,1000,-546,273,-947,361,376,-1000,-1000,255,734,-688,755,-581,-980,525,-352,-1000,66,1000,-690,768,-1000,49,-819,431,-1000,129,900,732,-1000,-31,258,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesKey(int):java.lang.Comparable",
            new int[]{-1000,12,-1000,1000,-1000,-65,-3,856,812,-503,-517,-1000,77,1000,1000,1000,-1000,-325,666,1000,-1000,568,768,-1000,1000,1000,572,1000,-605,-194,153,-816,594,766,-807,1000,-171,854,-1000,-1000,354,625,-602,-1000,1000,585,482,-1000,1000,-231,-667,-637,1000,-1000,1000,165,-353,926,-823,1000,-882,1000,-542,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesKey(int):java.lang.Comparable",
            new int[]{811,49,332,-578,1000,1000,238,-727,-73,-322,-933,21,1000,121,-641,-1000,869,995,-11,-1000,106,-1000,-620,-636,-42,-1000,447,-842,483,-1000,58,1000,-518,-1000,-354,-1000,-300,571,722,-23,7,-1000,-1000,457,-797,-619,-541,853,-779,-1000,938,493,152,1000,-1000,509,-440,146,1000,252,177,1000,-629,-742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesKey(int):java.lang.Comparable",
            new int[]{43,-914,-31,583,837,636,265,-288,28,-480,-875,-663,934,-211,264,-6,-740,-506,244,614,106,-120,-246,24,694,-361,-708,250,841,-137,-349,-18,-811,-347,320,858,-820,-825,722,-859,883,353,63,750,909,-619,-500,664,830,-252,-681,227,839,-429,-146,60,-334,708,5,-64,177,-686,-629,243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesKey(int):java.lang.Comparable",
            new int[]{101,-459,-1000,-315,882,658,688,993,1000,536,94,-953,1000,1000,-1000,-578,664,-1000,-1000,-1000,969,-666,896,-1000,1000,289,-1000,1000,-1000,220,868,-1000,1000,661,-1000,-719,-339,446,1000,875,1000,-722,-821,-1000,242,990,859,-89,249,-305,-1000,-1000,1000,545,-638,1000,-1000,1000,-1000,704,-1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesKey(int):java.lang.Comparable",
            new int[]{216,366,-1000,-51,972,1000,1000,417,1000,1000,128,-929,1000,1000,-1000,-1000,395,-716,-1000,-1000,961,-870,516,-1000,1000,-91,510,1000,-1000,-1000,441,-1000,730,351,-621,-896,-188,1000,-690,1000,1000,-1000,-779,-1000,242,318,723,385,249,-438,-902,-647,1000,405,1000,1000,-1000,1000,-961,704,-873,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.String:U2VyaWVzIDI=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getSeriesKey(int):java.lang.Comparable",
            new int[]{446,1000,-478,-1000,-1000,204,-650,1000,1000,1000,-119,-139,-297,1000,-543,489,-92,758,-1000,-124,399,-512,-197,-745,514,1000,1000,-352,-1000,-1000,873,1000,680,-1000,-840,-1000,601,1000,219,1000,77,645,-962,-932,-586,1000,1000,-1000,-999,-267,425,279,627,657,946,-1000,-1000,645,1000,832,-33,1000,297,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getStartValue(int,int):java.lang.Number",
            new int[]{877,-292,-22,-1000,190,-489,-1000,-500,621,300,1000,1000,-1000,-1000,-566,-46,-1000,767,-127,105,-706,200,167,-1000,-1000,540,34,1000,1000,721,-523,-1000,1000,-393,1000,994,4,250,314,-812,-1000,-929,1000,-493,-706,-1000,167,-1000,128,226,424,-975,1000,-1000,-127,1000,-1000,-20,1000,403,245,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getStartValue(int,int):java.lang.Number",
            new int[]{1000,-1000,-446,-1000,-71,1000,-1000,-1000,1000,1000,-1000,1000,-595,102,-595,116,1000,-892,-541,773,-1000,216,210,1000,-1000,1000,-1000,1000,-605,-10,272,-1000,1000,-556,1000,474,-611,-589,-1000,-1000,1000,-1000,1000,-1000,1000,-1000,-534,-1000,342,864,1000,-1000,-271,-1000,-333,-1000,-664,976,1000,-576,1000,-73,-92,891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getStartValue(int,int):java.lang.Number",
            new int[]{405,-1000,-1000,-304,-1000,1000,-1000,-1000,1000,300,-1000,300,-407,1000,-635,-899,1000,-1000,-88,932,-362,956,1000,720,-840,1000,-300,300,-49,382,1000,-300,595,-1000,1000,806,-668,-1000,-1000,-614,1000,-1000,300,-521,1000,-635,-374,-416,875,1000,300,-1000,-1000,-592,-1000,-1000,-338,260,300,4,418,-499,706,786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getStartValue(int,int):java.lang.Number",
            new int[]{0,0,0,0,-152,-637,388,0,-113,0,767,0,262,-399,0,-38,396,0,66,479,0,89,132,0,197,0,0,0,-93,240,-422,0,-209,187,0,27,-47,434,0,0,-747,0,0,0,0,-26,210,0,172,-89,0,0,101,202,378,636,130,38,-59,-309,-118,183,233,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getStartValue(int,int):java.lang.Number",
            new int[]{914,590,1000,-1000,-451,-643,295,-749,1000,630,1000,1000,-1000,-1000,-655,-61,-1000,1000,476,176,-794,498,1000,-948,-1000,660,253,1000,1000,1000,-1000,1000,1000,52,1000,920,-176,1,880,218,-1000,-1000,1000,220,-1000,-1000,304,-1000,-519,851,413,-1000,1000,-1000,-1000,1000,-1000,-1000,1000,934,574,99,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getStartValue(int,int):java.lang.Number",
            new int[]{-458,53,185,-228,532,-489,-676,-500,780,-699,1000,-286,-1000,-1000,-566,-1000,-1000,235,-280,-545,573,939,1000,-649,-347,-159,339,1000,350,1000,-112,-62,1000,-331,653,852,-429,244,483,-294,-747,-322,1000,698,-499,-1000,1000,-623,842,219,388,-113,461,-744,-851,1000,-200,-1000,1000,1000,-795,-829,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getStartValue(int,int):java.lang.Number",
            new int[]{1000,-1000,-446,-1000,-85,-1000,-318,-1000,483,993,814,766,-388,-46,0,0,448,-892,-607,391,-1000,-175,0,1000,-317,1000,-1000,1000,-605,747,505,-1000,593,-852,605,704,-462,1000,-1000,-1000,-172,-1000,1000,-1000,1000,-878,0,-1000,181,570,1000,-986,-895,-602,-222,1000,127,129,809,0,587,333,0,18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getStartValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{579,-826,-1000,-1000,-424,-1000,-662,-1000,-603,200,889,-1000,-317,-971,-562,-1000,343,595,249,834,638,763,-1000,-86,-1000,-847,-579,-894,493,969,-821,1000,-771,827,-362,-72,14,-282,-464,268,-685,-104,-485,-653,-1000,1000,-772,-44,-792,-1000,-471,684,352,-1000,71,-1000,-575,706,-960,-930,724,460,-709,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getStartValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{104,-1000,-918,-888,-191,-336,935,1000,-1000,1000,322,371,-781,-310,-280,1000,-1000,-187,-938,813,627,472,82,1000,-233,15,383,-1000,-1000,-872,-400,-406,-1000,-1000,-1000,946,-136,-542,-964,400,1000,1000,1000,-1000,1000,-1000,349,-241,224,721,635,1000,937,-1000,-803,1000,-493,617,-47,-706,821,88,-1000,-327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getStartValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-377,-787,640,-761,419,-1000,990,608,-1000,300,-378,623,-367,390,420,450,-722,513,-238,113,-73,-97,300,1000,-689,-448,930,-1000,-757,-598,300,-680,-723,-1000,-1000,246,-836,-1000,229,-300,841,359,618,-1000,562,-676,-276,-7,924,1000,839,357,598,-720,-147,1000,601,8,-300,-6,653,27,-525,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getStartValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-271,-1000,138,-180,-119,53,966,21,-595,-1000,-419,-91,7,485,1000,-461,-348,-642,500,747,1000,-801,-300,145,-691,-1000,-582,-230,-109,-1000,-1000,-95,104,858,-100,-1000,-379,-986,-23,-633,122,-161,-1000,-565,-258,-759,-133,1000,-378,-769,249,1000,1000,204,12,696,914,-98,-917,80,-381,-15,-102,443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{-489,1000,1000,-14,443,694,416,1000,-686,-257,549,-933,708,1000,361,-1000,1000,-1000,-805,-35,-1000,826,-945,480,1000,1000,-817,300,-1000,1000,-416,1000,-400,-556,1000,825,-1000,978,-1000,386,396,-1000,120,-1000,113,-1000,-1000,-407,375,-1000,-275,675,178,-985,244,-9,1000,737,-53,517,-1000,-493,73,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{-886,597,638,-632,901,-332,528,351,-748,221,-585,-747,866,730,566,226,230,-646,58,-654,-943,956,49,-428,191,472,-804,26,556,652,-594,267,443,545,110,998,248,970,-650,117,631,630,-912,964,-20,-427,369,142,60,-783,237,-583,775,331,612,-174,71,14,-233,367,-791,-7,-554,411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{-151,144,106,407,89,1000,1000,-987,158,666,691,710,678,931,-70,1000,-893,-133,838,-768,352,-1000,-935,324,-196,-328,-527,686,432,1000,-595,-552,1000,539,-487,-282,1000,1000,954,404,1000,278,-78,-309,754,637,-229,-414,-195,49,1000,55,476,944,1000,-1000,90,-98,-1000,900,-1,305,807,-227}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{-151,850,730,-532,-304,863,-74,387,1000,-347,786,-24,678,1000,-12,-557,874,-864,-300,-768,161,24,-461,-649,991,105,-41,-532,-1000,-73,-1000,58,628,19,1000,341,3,1000,-432,-247,-840,-46,179,777,653,-418,-229,-414,808,-162,294,286,-566,-977,247,-661,1000,842,-158,900,-199,-494,458,209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{1000,-732,1000,414,-975,-370,421,131,70,1000,-411,-184,309,-1000,951,-664,-95,134,-600,1000,684,-628,849,617,528,-1000,836,936,267,-1000,716,-665,-1000,433,-325,-527,336,1000,780,1000,79,556,515,-1000,-124,-773,-866,551,31,817,-1000,-394,436,-907,58,259,-1000,-924,-428,-662,587,779,-534,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{-692,969,754,-14,220,694,177,-51,-241,-121,549,-933,651,17,397,-859,1000,-283,-227,410,-1000,-9,-382,800,632,472,-199,168,-704,1000,-416,32,917,337,-92,81,1000,1000,-237,707,1000,-399,-1000,1000,-576,-1000,-232,-881,250,-693,-275,-804,1000,-1000,1000,-1000,-458,737,27,-987,-1000,372,-627,294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{-270,1000,1000,-1000,1000,-641,256,1000,-1000,547,-563,-1000,856,1000,1000,-1000,336,-1000,-1000,-682,-1000,1000,217,-671,1000,1000,-880,626,-969,1000,-95,1000,-1000,-63,1000,1000,-1000,970,-1000,-124,276,1000,-1000,1000,-208,-1000,-863,278,110,-1000,-687,-1000,1000,-930,-540,242,-447,-504,609,1000,-1000,-59,-977,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{1000,121,1000,-439,919,-928,1000,745,-1000,1000,-1000,-1000,151,-492,1000,-324,66,-599,-1000,1000,158,250,600,178,349,-9,-301,1000,-26,-365,1000,133,-1000,544,151,451,-191,1000,113,1000,420,943,-71,-1000,-69,-795,-994,87,-708,159,-1000,-102,279,1000,61,375,-1000,-1000,-702,-318,-667,494,-1000,971}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-199,1000,1000,1000,1000,963,-986,-1000,300,-575,-201,123,1000,1000,910,-1000,1000,-653,910,1000,-1000,-394,-1000,-400,1000,1000,1000,-1000,1000,975,-1000,-1000,-1000,-1000,-757,904,1000,-1000,837,1000,1000,1000,1000,686,1000,-1000,-1000,-41,1000,1000,-1000,-1000,1000,-1000,969,-1000,-1000,-1000,657,669,42,-711,373,-965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{676,1000,646,1000,283,-339,-11,-1000,1000,-671,-722,-24,1000,1000,-916,-283,754,-1000,601,1000,-545,-266,-178,132,424,820,342,-1000,100,-579,-1000,-305,-1000,-1000,426,244,1000,-897,449,566,1000,-127,1000,-452,1000,-1000,-367,684,1000,152,-424,-1000,731,60,-430,-1000,-910,178,731,786,234,1000,-278,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-1000,-400,-684,654,-314,-674,-646,400,-610,985,-179,197,834,-135,-129,287,361,504,742,184,-89,485,661,30,228,53,-400,-1000,44,638,531,-55,-832,55,378,796,-400,-1000,467,-395,364,-260,651,-218,1000,-16,459,127,286,-4,310,400,1000,111,-143,273,-774,706,-34,15,667,269,-262,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-1000,-694,-1000,-1000,50,646,158,-361,-1000,0,36,0,0,-1000,817,624,272,-88,393,26,1000,-829,824,-733,1000,-994,319,1000,-1000,1000,-17,450,1000,1000,733,-1000,227,581,0,-696,0,-1000,0,-992,-800,0,1000,-1000,-429,-1000,1000,392,-438,39,0,437,-739,474,-662,-1000,-651,-802,-1000,670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:NQ==|getColumnCount=java.lang.Integer:MA==|getSeriesCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setCategoryKeys(java.lang.Comparable[]):void",
            new int[]{-1000,-304,-769,156,1000,-1000,1000,0,-1000,0,-276,0,-7,-472,1000,-295,1000,-212,848,0,-1000,-1000,-1000,338,1000,-889,1000,-1000,-73,-684,1000,-1000,0,677,-1000,-221,1000,1000,-508,-58,448,1000,-229,883,-1000,-607,1000,0,-1000,-661,-935,0,-119,0,-1000,923,0,-312,1000,151,533,43,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setCategoryKeys(java.lang.Comparable[]):void",
            new int[]{1000,-76,724,183,-700,671,-250,171,1000,117,205,-411,-1000,1000,-51,913,-300,-192,-508,1000,-70,6,439,-106,510,136,339,-800,-374,-1000,-300,994,670,-372,27,643,1000,-206,-1000,409,324,440,660,-97,-263,1000,134,-575,-383,-467,-526,-499,423,-478,-871,479,-246,430,-174,619,-196,973,219,-585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setCategoryKeys(java.lang.Comparable[]):void",
            new int[]{-1000,114,-1000,26,1000,-1000,1000,-55,-1000,0,-498,-384,899,196,217,-1000,-243,712,1000,72,-31,-254,-1000,719,1000,-671,916,41,-1000,-466,1000,-1000,169,270,-1000,-1000,253,-397,-206,-1000,118,1000,-49,597,-1000,-467,-33,763,-1000,-663,-682,-975,-578,1000,-1000,297,-330,-1000,1000,1000,923,43,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:Mg==|getSeriesCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setCategoryKeys(java.lang.Comparable[]):void",
            new int[]{-1000,1000,-1000,-1000,1000,-1000,-64,1000,-1000,-1000,902,692,-231,-1000,-400,-444,1000,-455,1000,1000,365,-615,-1000,1000,212,-985,491,400,139,-126,515,-1000,-454,677,-1000,-973,634,1000,-643,-598,1000,1000,-1000,-859,968,-676,-400,-202,-1000,-285,-579,-26,4,859,-1000,-1000,-326,-1000,-178,836,942,-981,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setCategoryKeys(java.lang.Comparable[]):void",
            new int[]{-641,-26,-1000,-306,-491,-1000,235,-312,-1000,-723,744,126,-46,-987,729,-677,701,37,718,1000,-154,-1000,-692,-367,198,-684,392,108,-992,200,-478,-1000,-938,139,-909,-72,752,928,359,-783,183,107,-1000,724,-16,-350,313,-222,-1000,-408,-210,457,-41,698,-830,175,-1000,387,1000,228,737,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setEndValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{368,481,651,1000,465,-46,-1000,350,710,536,1000,-490,-1000,669,-978,179,1000,-1000,-381,-1000,30,-263,363,1000,-353,13,-1000,-1000,848,404,236,-183,573,-1000,483,1000,-1000,-852,-804,-195,-1000,-1000,-23,701,-1000,305,399,-186,-1000,-822,710,-153,834,-197,-34,202,960,-1000,1000,-955,734,126,-943,-145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setEndValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{-485,1000,-856,720,-870,-226,300,-449,-1000,1000,24,486,-376,24,-1000,-1000,-25,-652,26,110,-380,974,-1000,-1000,-1000,1000,-245,-431,-1000,-18,259,-510,-48,580,193,-855,-348,-775,-597,105,-893,472,-682,-266,-74,423,158,492,-219,801,-2,529,1000,1000,-1000,-151,-1000,-580,82,241,31,-259,474,-33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setEndValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{173,-802,-733,873,-221,74,1000,488,400,81,241,848,482,-293,252,515,-400,306,386,1000,-146,558,-228,808,614,-737,1000,458,-14,-1000,-198,1000,-441,1000,-1000,871,277,-103,411,-216,25,-253,822,-633,474,-297,-229,787,579,-581,1000,1000,-132,-786,977,870,-1000,-256,-336,1000,-194,345,853,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setSeriesKeys(java.lang.Comparable[]):void",
            new int[]{999,1000,-1000,-608,-1000,629,158,-1000,-544,647,-369,323,56,642,367,-420,1000,-1000,-842,-16,-887,98,1000,-1000,1000,-51,385,1000,-119,56,-879,-1000,-561,-1000,-339,-531,-386,-1000,-1000,-76,-1000,-606,-352,-1000,1000,1000,648,204,882,-480,-50,-371,-202,-793,-669,-946,54,-902,93,370,-84,678,-982,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:NA==|getSeriesCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setSeriesKeys(java.lang.Comparable[]):void",
            new int[]{1000,-1000,-1000,1000,1000,-625,958,-551,1000,1000,721,-1000,696,-886,1000,-1000,388,-1000,-486,-18,-589,-1000,1000,-662,-399,-510,723,233,1000,-665,-433,166,-823,-1000,697,590,-1000,-596,-396,838,-1000,-1000,-1000,-674,1000,649,565,226,-504,-1000,563,690,-442,1000,-734,-1000,1000,-1000,1000,1000,-167,361,-390,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setSeriesKeys(java.lang.Comparable[]):void",
            new int[]{-291,-28,42,-834,934,131,586,402,781,221,-418,-118,774,698,-483,-537,-392,-6,-339,893,369,343,989,497,517,-140,292,777,-547,548,-326,393,-217,188,-692,835,-988,4,-631,-227,335,-143,-610,-455,434,358,-58,532,749,711,503,-718,568,385,644,145,-567,-234,-725,923,895,-163,559,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:Mg==|getSeriesCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setSeriesKeys(java.lang.Comparable[]):void",
            new int[]{1000,1000,-1000,-568,1000,-334,1000,-1000,-291,1000,98,-1000,208,884,-1000,-1000,-1000,776,-1000,915,545,18,153,727,1000,-1000,1000,1000,53,1000,-1000,-864,-1000,204,-1000,587,-1000,728,-1000,-1000,-941,1000,-925,-1000,-358,807,-896,377,953,298,1000,886,1000,-1000,1000,686,-557,-1000,-1000,1000,546,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setSeriesKeys(java.lang.Comparable[]):void",
            new int[]{-319,-1000,280,676,659,1000,448,1000,1000,-1000,-1000,609,-503,1000,788,-991,-277,-1000,-105,210,37,1000,643,-339,1000,-148,-69,213,-1000,646,698,867,-244,1000,-644,227,115,-91,564,-1000,1000,389,-66,669,666,-430,1000,795,157,-200,785,1000,611,145,212,1000,-1000,1000,-1000,302,1000,567,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setSeriesKeys(java.lang.Comparable[]):void",
            new int[]{440,626,-590,0,-1000,0,0,-1000,-796,0,0,890,147,0,1000,0,714,-668,116,-687,-1000,285,465,23,0,1000,53,391,-336,-1000,0,-1000,-652,-751,496,-546,117,-913,564,1000,-1000,-104,686,577,1000,241,439,-640,-394,0,-1000,1000,-1000,0,-1000,-271,-927,-54,0,-181,-1000,-851,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setStartValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{-55,-1000,1000,-1000,449,-314,-567,-891,-1000,1000,-1000,1000,287,-1000,-1000,350,-1000,1000,-1000,454,-1000,347,-1000,1000,956,-199,1000,-682,855,-165,-125,-152,102,-268,525,-949,596,-737,-712,334,1000,1000,1000,-132,96,-1000,-142,1000,-1000,331,-747,648,225,531,1000,705,762,-1000,-1000,704,753,567,-872,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setStartValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{566,1000,872,-277,516,1000,594,-1000,231,644,509,-1000,-262,-34,724,-1000,-1000,146,-478,-866,-678,1000,773,-388,-403,1000,-816,-1000,-30,-697,571,724,709,-407,-1000,-389,163,958,-1000,1000,-450,1000,137,-614,-1000,415,587,-456,847,-549,-810,-185,326,-1000,-523,149,1000,365,896,-986,-498,-678,596,-457}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setStartValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{1000,-475,34,340,1000,-643,-965,1000,92,-164,1000,692,-1000,624,942,-208,684,-1000,-580,-1000,-208,236,1000,592,-1000,366,-186,-101,1000,771,482,1000,1000,594,492,1000,1000,269,1000,555,-1000,1000,-1000,-576,-1000,1000,-319,21,838,582,873,942,-528,716,1000,-258,583,1000,-850,1000,77,-339,-260,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setStartValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{88,810,872,1000,1000,-969,-219,430,-135,608,509,-1000,-880,-34,445,-1000,1000,-179,687,-988,-444,-336,628,1000,-616,-284,-81,376,-30,-697,767,1000,-273,891,-348,723,1000,114,-315,1000,-971,267,137,-614,-1000,772,740,752,844,42,458,-771,326,1000,1000,-258,496,678,-500,957,-913,-635,780,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.category.DefaultIntervalCategoryDataset", "org.jfree.data.category.DefaultIntervalCategoryDataset", "setStartValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{-24,-743,-1000,1000,668,-1000,-1000,1000,1000,-1000,509,1000,-1000,1000,277,386,179,-1000,1000,-1000,1000,-764,716,-364,1000,-999,761,-705,1000,1000,-147,357,-796,1000,-106,-400,1000,-40,1000,-977,-450,-112,-1000,423,499,1000,1000,29,1000,-656,1000,-400,-592,1000,-1000,-1000,-1000,1000,-1000,1000,1000,-1000,1000,-308}));
    }
}
