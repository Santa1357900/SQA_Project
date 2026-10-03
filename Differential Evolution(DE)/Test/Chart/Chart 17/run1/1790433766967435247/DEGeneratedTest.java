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
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{1000,-600,-1000,968,-1000,92,-1000,-732,1000,-368,-1000,1000,111,198,1000,502,-262,-1000,-902,1000,1000,536,1000,1000,-504,-203,-698,826,1000,-311,-1000,-896,-1000,-1000,-1000,1000,-1000,-1000,-992,1000,858,1000,-365,-1000,630,-663,1000,1000,1000,-1000,-1000,-21,24,-413,-1000,-1000,1000,1000,717,210,366,-1000,1000,870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{1000,-751,-1000,-336,-99,222,499,-484,-867,155,322,1000,616,1000,264,-345,1000,-1000,-497,-1000,180,898,994,-605,-514,-1000,-696,775,-518,1000,-1000,-498,1000,124,-1000,-938,-108,1000,-1000,-195,-211,-1000,310,-1000,-252,1000,-667,412,762,-327,-1000,239,-1000,-982,-450,-1000,988,-72,891,795,-804,-1000,1000,-875}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{1000,-689,-895,-862,-191,-830,-141,362,-131,-257,-314,841,537,-18,-115,370,1000,-1000,-846,-25,-126,428,882,184,-781,-514,1000,506,-68,-648,-486,-218,-620,115,178,631,271,572,-216,-300,-435,-748,148,-653,838,39,396,-513,522,-184,-380,-928,-837,106,-1000,-616,210,-1000,-334,820,-816,726,280,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{-1000,573,1000,-1000,652,-375,-986,1000,992,-24,1000,-522,-105,-400,-1000,-97,-32,-15,897,191,-527,-985,-389,-298,660,434,1000,-1000,-1000,-1000,1000,454,-633,960,1000,20,1000,503,1000,-1000,-563,-789,1000,-288,825,-833,98,-849,-1000,193,1000,-602,704,726,823,1000,-539,-1000,-740,1000,940,1000,-1000,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{744,-419,-1000,-178,-355,-598,-306,534,-824,604,-732,228,460,-135,-782,-152,-15,-82,16,-377,1000,-1000,886,557,31,-509,432,-500,-974,388,-282,-896,-266,354,307,511,-1000,818,190,-797,-1000,-822,990,-931,505,249,-423,1000,-936,-1000,-1000,-1000,-561,943,151,-594,405,-1000,-293,854,281,-420,-791,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{1000,-682,400,140,48,392,496,52,-1000,410,225,756,1000,948,-464,-411,937,107,-449,-1000,905,-182,1000,-560,-632,-1000,-935,329,-995,1000,-794,-232,1000,-953,-85,-300,-1000,1000,34,-427,-400,1000,546,-952,-530,1000,-1000,-1000,46,-351,-279,521,1000,-252,-96,-716,584,-72,360,852,-942,-680,390,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MTQ=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{-571,207,-20,923,600,306,-1000,75,-921,-1000,1000,-58,-1000,-609,-745,-834,479,698,732,1000,973,-468,178,300,1000,1000,-7,-1000,645,1000,1000,-248,-567,-1000,-656,-215,-577,-1000,1000,432,512,1000,-362,264,962,-1000,729,1000,-1000,-721,1000,-1000,1000,-518,1000,-385,1000,-1000,-643,1000,1000,-599,201,794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{1000,389,-432,-835,-32,403,-977,7,310,-1000,-1000,1000,133,-243,345,1000,-156,-1000,509,-172,-148,1000,994,876,-1000,-509,-1000,1000,1000,1000,226,-1000,-13,603,-1000,989,-1000,314,-1000,377,-1000,-331,-685,501,1000,101,141,718,1000,-1000,322,612,1000,902,-641,384,-255,1000,1000,752,-898,534,1000,850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{-268,-222,-1000,144,-228,306,-438,459,-852,-661,1000,841,-378,-256,-625,-232,1000,-77,699,185,-1000,-267,485,-386,568,521,470,-481,215,627,825,-669,200,325,-419,-400,-142,400,1000,335,-300,-380,-701,-1000,874,164,735,385,-43,-814,141,-938,-223,-266,753,-478,287,-1000,286,182,569,-655,288,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MTA=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{1000,-1000,-320,-482,1000,939,522,808,585,251,-211,-148,1000,348,-208,-66,-1000,69,611,1000,970,-1000,658,-440,96,-771,-78,-503,252,1000,129,647,-812,1000,5,-524,-1000,-225,91,1000,1000,-1000,-1000,77,968,1000,1000,307,878,211,1000,150,186,-73,1000,-203,1000,-1000,-689,-367,278,-18,-1000,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{-962,631,-95,-953,120,303,1000,1000,-701,-1000,-1000,-748,-18,1000,-522,100,-832,595,-12,-241,-87,-1000,673,-189,-614,488,-1000,-1000,1000,-126,-1000,500,-1000,1000,-1000,855,-9,-276,921,290,1000,291,-71,1000,-225,-846,-550,611,161,263,1000,-1000,899,-310,53,-884,-1000,792,-839,573,-707,-635,-885,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{-143,-1000,-163,1000,-310,148,-362,-756,1000,1000,558,-720,1000,-360,-1000,199,-608,-449,291,1000,393,977,-149,-803,-121,-511,805,97,-414,-388,399,516,470,277,-1000,400,1000,-1000,653,488,271,-88,140,332,-846,876,-286,-206,-393,622,564,742,967,-1000,630,-214,150,-1000,-537,-986,1000,183,-111,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{-1000,-658,-167,160,494,5,-26,-146,-426,21,235,-487,1000,310,-1000,75,-610,-211,918,1000,1000,-514,-853,69,-1000,-1000,19,-570,-214,273,24,1000,-196,115,713,981,265,-794,857,-31,1000,-1000,476,-406,-464,502,1000,417,713,205,650,-743,458,-86,-377,-1000,-421,795,-406,-1000,-551,-1000,-722,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{-74,-684,898,26,743,479,-495,779,-636,-663,123,-1000,395,631,-711,-86,34,886,544,-600,855,-423,-813,-888,358,-1000,-467,-608,167,-978,1000,291,-1000,94,-1000,768,-193,569,188,61,620,-913,-451,-183,312,1000,-405,147,7,1000,570,1000,-170,452,77,-412,653,363,123,-476,-406,-75,-127,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{759,-277,-713,-282,879,719,1000,901,-692,376,-536,-599,-177,587,552,272,-875,616,-930,682,-335,-98,-743,-899,-425,368,205,-926,976,-345,430,657,-2,362,-913,818,474,-1000,477,923,-69,141,354,983,878,642,718,-282,-161,455,928,171,1000,-821,826,771,982,-903,397,126,-43,-121,-1000,421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{-1000,-917,-95,548,1000,513,-655,1000,-846,37,766,271,1000,211,-711,94,-328,-588,1000,-241,1000,-110,-373,73,-801,-1000,611,830,-1000,-126,-1000,1000,44,-230,1000,-416,-833,263,805,864,755,-1000,1000,-1000,936,-846,1000,-666,970,-266,1000,-235,426,-582,69,-67,-1000,11,-1000,573,-757,-956,133,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{-1000,-1000,1000,955,1000,-421,-1000,-566,1000,1000,1000,-659,1000,123,-959,516,-1000,-531,1000,534,1000,-1000,294,7,-510,-12,1000,807,-1000,368,359,837,-329,494,1000,-518,-270,-1000,1000,1000,1000,-1000,1000,-227,361,1000,929,-804,657,-94,864,480,1000,158,866,287,1000,-1000,-900,-1000,594,-833,-879,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{859,-435,994,374,-919,47,234,867,934,-606,-313,-598,516,-598,-917,220,385,449,-18,-1000,-274,-1000,209,242,1000,458,-998,202,654,-738,275,1000,77,-115,980,739,-389,1000,1000,83,235,-92,268,-339,400,1000,-1000,-323,-249,-268,564,115,116,-83,-48,118,-492,170,-1000,508,139,1000,294,-138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-292,1000,-672,-432,-697,-551,656,1000,-877,-265,-884,-487,246,-6,291,-604,1000,-474,-220,-1000,-405,-504,930,-187,1000,-1000,1000,-1000,-401,1000,-1000,1000,922,-367,-1000,165,-61,-1000,-1000,783,-1000,-279,1000,-1000,-183,1000,18,625,655,-1000,228,166,-202,601,-431,-1000,221,-1000,195,554,-413,903,415,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-90,981,209,252,-405,-670,-107,675,88,-692,86,-1000,-127,396,99,-604,1000,-435,-147,-751,-665,-1000,1000,-735,844,-807,1000,-413,-593,856,-631,1000,777,-684,-797,271,-27,-425,-257,40,-1000,-195,1000,-732,-167,429,-747,828,132,-1000,42,1000,58,559,-660,-634,221,-465,332,-10,-601,903,493,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-50,984,158,212,-443,-66,333,-144,73,-348,-803,-1000,-227,-455,-46,-148,633,-840,311,-976,221,-168,774,296,270,-778,178,-565,-528,507,-1000,543,783,53,-1000,329,-350,227,-385,353,-1000,-130,1000,-1000,-313,1000,1000,355,370,-819,346,231,-257,832,-591,-958,12,-140,-678,1000,-212,908,-459,133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{835,-748,566,-114,-14,949,-246,-845,512,-412,700,229,711,618,402,-1000,-1000,93,484,363,285,1000,-676,598,-708,936,729,669,-146,-67,1000,-804,182,-468,424,-999,-414,-690,873,-1000,128,863,-851,409,199,-1000,-182,429,-945,909,-626,85,-88,320,1000,428,190,742,886,-331,816,-828,-627,567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{1000,976,-955,255,-1000,-56,143,994,-861,384,-680,-782,846,-372,861,1000,1000,-542,-457,260,-323,365,898,465,466,-308,954,-1000,691,-978,693,-285,1000,-260,691,-439,151,-88,-422,567,-435,-269,221,-984,-243,295,-306,362,255,-420,-949,-17,72,174,24,-1000,593,-905,512,373,689,233,417,-293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-155,-186,77,-1000,-480,667,1000,339,-370,-586,-1000,707,651,-273,-337,-335,-167,-676,236,-702,218,371,-861,-606,1000,-1000,-12,-1000,-283,-568,-1000,-68,1000,625,-1000,-863,333,-850,-995,300,-590,1000,456,-851,588,1000,1000,-252,-631,-246,1000,-489,451,-302,-111,327,-314,-1000,558,1000,228,-739,151,-234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{680,-289,918,-285,16,-245,-1000,-1000,344,-444,1000,254,-173,281,-110,-1000,-1000,670,1000,448,-873,1000,-695,-184,145,1000,-671,-1000,57,-150,972,256,653,-385,268,-523,-378,577,1000,-1000,6,601,-284,317,208,-980,-473,482,-1000,761,-255,576,666,325,1000,1000,-681,855,1000,-1000,244,380,496,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-657,-791,350,375,-579,949,-946,751,969,-579,700,229,-650,-27,402,-222,149,343,-717,118,-40,-722,-676,-746,238,316,-593,393,862,311,277,-804,848,277,414,38,-55,113,-312,765,712,863,-954,284,147,311,468,-746,901,-89,-42,-868,716,-683,-581,870,809,-886,954,-321,-340,747,-688,443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{109,1000,-794,-205,-541,-52,638,572,-928,566,-13,-271,534,-115,1000,1000,961,-462,-21,223,-973,-927,491,-642,1000,-1000,1000,-579,396,-223,-521,-26,776,311,-797,-159,-409,-456,-730,1000,-584,1,1000,-257,-197,429,273,295,80,-1000,202,694,-368,-290,-425,-242,48,-1000,163,-360,-224,751,688,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-56,414,-812,148,913,-949,1000,-242,443,886,-250,47,537,-1000,51,-54,-1000,-1000,-452,-726,322,781,1000,929,-674,-221,274,-94,-1000,276,-127,1000,-1000,-343,-729,-446,1000,274,331,-162,936,-1000,1000,-784,-123,-783,-920,-206,-834,-126,-317,713,-835,658,-51,-1000,259,804,-711,-1000,-562,-1000,333,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-1000,-1000,108,-40,325,-47,985,-327,234,594,-685,385,1000,-692,145,61,1000,678,951,883,1000,-1000,676,-879,665,-1000,1000,-76,-549,-1000,148,-104,-803,911,-324,417,748,83,-1000,-279,-1000,-717,1000,-474,-216,557,-122,133,1000,917,-874,-482,137,-96,269,1000,646,-940,-1000,-98,-18,1000,-214,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-683,-98,121,-853,-192,-602,-617,-351,116,-704,663,-896,638,-276,-699,-196,404,-554,-180,564,812,-768,247,635,-689,-919,938,-563,884,-762,-213,-74,-519,573,773,-908,-36,136,-988,326,-442,461,690,-766,356,563,561,826,962,456,-945,-31,41,-485,-532,477,-784,8,-814,-713,-463,754,-553,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-129,-221,-803,-369,519,1000,173,319,-91,1000,391,1000,-87,-257,15,282,981,517,-114,907,-200,438,1000,-396,-492,-1000,468,1000,127,-41,1000,-86,603,-340,-1000,-41,-237,889,887,231,112,525,473,863,-497,-590,394,312,372,391,430,-268,343,331,-301,-191,1000,-254,169,422,1000,-35,6,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-49,-345,14,508,-246,722,-569,-553,343,430,-627,151,807,-1000,139,-907,425,655,-573,1000,754,-389,-595,-595,1000,-143,469,-620,-189,-813,582,-692,462,284,460,1000,409,1000,-932,651,-819,-886,-67,-1000,-512,-293,1000,451,-42,760,-7,-465,-622,-475,-149,1000,-1000,248,-590,1000,324,-465,-868,533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-889,-294,498,469,614,13,330,358,454,788,1000,363,-1000,-524,-930,212,-922,-1000,-1000,-1000,1000,1000,-73,-608,-972,-848,-1000,-914,274,243,35,-321,-12,-352,1000,-1000,376,379,1000,719,845,1000,-437,860,1000,-179,510,375,-227,-1000,-790,1000,904,-618,829,-954,-154,692,782,310,-802,-387,3,-994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-1000,-975,-230,-1000,1000,-1000,449,74,147,-1000,1000,520,-155,1000,-1000,-423,-1000,-1000,1000,-1000,-484,-1000,1000,832,-1000,-412,-119,1000,1000,607,-1000,1000,-1000,1000,1000,-1000,-957,-847,-292,-1000,1000,1000,1000,498,70,1000,-1000,1000,1000,-126,424,845,1000,1000,59,-564,1000,-225,-945,-1000,-1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-772,-311,174,-395,1000,-863,-272,226,510,889,464,-10,637,29,-463,-993,-433,-221,-775,841,-468,-1000,197,681,-593,-100,767,-421,669,-369,-688,458,-628,1000,303,-625,374,-762,-1000,-1000,-161,-161,1000,-512,-476,1000,234,1000,1000,-606,406,-57,1000,376,-286,505,-923,-272,-1000,-460,-510,816,375,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-1000,-832,213,-1000,-211,-421,-1000,-69,-213,-1000,1000,407,1000,384,-1000,-663,-383,-1000,-1000,-500,-469,-1000,1000,635,-1000,-400,1000,-563,1000,-268,89,72,-736,573,773,-908,-919,927,-1000,326,1000,1000,773,-1000,279,1000,-406,522,962,862,-570,-252,962,-498,-1000,-245,-1000,-106,-1000,-713,-1000,1000,-383,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{552,748,-239,376,-100,644,-971,-459,528,-12,-207,151,-226,-782,0,-841,-87,500,-396,1000,1000,568,971,-139,1000,-12,-235,-793,197,-286,686,-703,1000,-135,708,579,100,981,-307,1000,-496,-395,-399,-217,-804,1000,1000,-431,-915,20,100,78,-756,-26,-46,746,-1000,1000,-1000,811,570,-1000,-840,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{436,-215,15,-673,676,-281,14,478,-56,400,688,-400,-284,-288,1000,-1000,76,-205,-141,-795,-1000,489,346,-507,75,-143,-266,-223,-908,262,144,739,421,556,400,-569,-792,1000,221,-777,-400,-267,-1000,708,297,400,-1000,-873,-565,-339,1000,1000,-65,-270,-761,313,145,-838,628,350,419,48,892,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{979,835,-931,-427,214,-559,-715,850,607,-467,546,457,851,-893,460,-723,-390,-205,265,-494,-779,489,-255,-559,605,-771,239,245,-725,19,197,739,-179,437,-788,-433,113,-176,-853,-777,601,691,-935,-625,573,-447,47,-308,-32,-186,629,924,484,556,-380,-588,-934,-838,344,350,613,485,986,-808}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{1000,627,-1000,-846,274,-316,-759,1000,609,811,621,-91,1000,108,1000,-1000,-253,-1000,-471,797,-1000,200,-465,-766,974,-727,379,46,-927,71,-899,953,11,99,-727,-888,276,701,-498,-933,-168,709,-1000,352,-32,385,1000,194,-367,-553,1000,1000,1000,596,-807,-878,-289,-418,-440,-499,1000,52,999,-129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{-129,-1000,-400,-715,-383,888,-1000,640,1000,1000,920,-384,-556,1000,-889,-561,-205,-599,-1000,-1000,-1000,1000,-878,1000,257,120,-1000,-1000,-1000,-338,812,-338,-922,-431,-204,-1000,1000,-1000,-1000,-964,-510,-580,97,-742,-722,1000,499,-28,284,-1000,200,1000,903,-1000,14,53,-78,-1000,-1000,1000,690,616,150,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{706,136,-597,-28,43,-621,-700,227,608,157,289,77,-327,-344,192,-235,-398,188,286,219,-855,597,395,-255,289,-587,-547,-501,-356,-505,291,13,76,220,-421,322,287,-242,-844,-637,477,47,-334,-420,188,361,-75,-571,-319,-219,456,157,25,232,-88,-72,-204,-764,-1000,630,701,721,-42,-30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{-229,-530,24,-746,-155,-392,-1000,254,859,1000,781,-851,-1000,357,655,-1000,-217,-524,-1000,699,-1000,814,-16,1000,278,-14,-508,-636,-1000,-471,650,-197,416,277,53,-1000,544,400,-457,-592,-813,-328,-42,701,-95,1000,-109,-255,-148,-1000,1000,1000,-336,-1000,-13,350,-118,-460,-507,942,400,-348,343,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{394,-284,-931,-696,361,-437,-426,120,726,-5,768,168,851,392,-446,-1000,-967,-1000,-1000,-143,-1000,394,890,465,103,-37,808,245,-1000,-775,1000,-1000,548,1000,-874,-107,600,157,-298,641,345,691,-192,509,322,-400,737,-989,82,185,629,-400,-323,-723,-701,537,-627,-873,-626,1000,-426,-1000,1000,-207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{199,1000,-343,323,169,604,184,1000,-491,-1000,257,801,-930,-439,-759,460,-678,-1000,-385,-1000,843,-802,-996,382,-414,-511,910,367,-1000,-48,1000,-256,-630,1000,-1000,-940,339,-1000,-153,835,261,1000,-992,-1000,354,-1000,1000,-829,497,-465,-400,-1000,1000,423,-272,-229,-1000,-1000,85,821,-671,-894,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{505,304,-628,66,-57,-202,-945,549,675,-545,29,-231,637,432,-629,-155,-1000,-794,-481,1000,-310,340,-401,106,414,-399,103,-526,-578,-608,817,-254,-566,784,-527,-882,1000,-751,-787,-418,-139,1000,30,800,-186,-443,1000,-254,-261,-261,105,958,795,-51,-469,-611,-887,-999,-1000,137,1000,-304,717,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{-639,-1000,1000,-746,-375,764,-400,-335,953,1000,-921,-1000,-1000,-1000,-821,-335,-549,-706,29,699,269,1000,-918,-58,-89,-14,-448,773,5,-391,650,-833,-695,627,1000,-1000,963,400,-645,-1000,-1000,470,-1000,-520,156,-301,1000,-769,252,-1000,1000,358,-183,-400,309,344,-1000,-460,466,163,-1000,593,628,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{726,-74,-463,-497,-940,992,845,-147,233,970,324,-312,-544,-596,-330,631,717,-894,-551,-87,-627,178,515,-294,676,628,-590,170,415,-991,-842,-947,-951,-854,744,309,-274,107,-768,-904,-594,-120,-53,801,930,867,446,-718,333,-712,-899,355,-43,842,-977,796,-8,-426,819,569,955,-204,-833,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{619,-1000,-537,413,-1000,974,1000,-1000,-166,-288,1000,-1000,-67,1000,-1000,580,938,-593,-476,-336,-171,384,-1000,509,-383,669,393,729,-1000,172,-1000,-718,621,214,42,1000,-368,1000,-233,-506,-339,-23,513,1000,488,-876,-1000,-1000,506,95,117,-489,152,1000,-646,-1000,-159,1000,-125,1000,958,-1000,47,533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{224,535,323,371,-750,219,30,-27,29,846,-838,1000,-698,-454,309,112,-39,-820,4,-554,-315,-59,817,-345,-395,-368,-63,37,-309,-571,-1000,-934,-556,455,-390,839,430,-828,339,-751,-14,-1000,-281,-56,-1000,-135,1000,-124,920,-790,125,-201,53,-276,170,1000,-585,-448,1000,-1000,668,257,656,640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{602,-793,-279,490,-1000,1000,1000,-1000,-302,-624,1000,433,-1000,1000,-426,546,694,-442,-742,-497,-121,-360,-90,1000,-1000,-48,-618,876,-1000,-256,-1000,-721,310,1000,-249,692,203,857,427,-1000,436,61,1000,1000,-711,374,-664,-664,995,-673,390,-541,899,432,-617,-925,-755,1000,-67,1000,23,-323,-577,893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-93,568,-300,-264,1000,-161,-349,367,-1000,47,1000,201,450,261,-623,1000,-1000,13,-330,-57,-449,479,1000,70,344,1000,-1000,-98,-1000,181,883,-1000,-1000,-706,-876,319,680,-1000,994,435,1000,130,-1000,-497,456,1000,1000,-990,749,-717,-1000,842,-1000,-949,-1000,-300,1000,-1000,336,1000,-714,-990,700,184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{325,669,135,490,487,-277,-66,129,380,-276,-389,792,-29,-917,859,361,-384,-539,51,415,-450,-387,989,314,644,-419,292,-1000,1000,-883,-337,-602,-1000,353,-1000,-670,469,-707,69,-707,1000,-354,-187,-301,-393,553,1000,112,300,-652,347,1000,-628,-380,-304,233,-578,-135,-301,-581,286,136,120,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{778,542,-477,35,1000,544,752,759,-1000,-390,802,924,1000,191,-173,-111,685,175,12,-992,268,1000,-471,289,376,91,1000,-407,-284,1000,-776,70,924,-640,195,310,-973,971,-988,984,-77,902,594,741,577,338,-10,-1000,-113,-337,185,-565,-326,1000,111,-592,-7,-1000,-1000,-498,-1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{1000,133,-62,1000,-1000,1000,-472,574,-1000,202,1000,-522,332,1000,-1000,902,-14,938,939,1000,-171,-168,-134,699,1000,-1000,198,879,9,1000,972,-23,502,675,-866,478,1000,652,-877,-300,458,1000,-291,-810,883,1000,-1000,-1000,345,812,-1000,-1000,-535,220,-1000,-169,-603,-419,-385,651,-655,-1000,-1000,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-487,-79,259,1000,90,-1000,418,-27,281,761,604,508,-1000,154,-266,101,-736,-299,-525,-38,142,-1000,1000,443,-671,-716,-1000,-630,-592,-1000,765,56,-956,1000,-820,-1000,468,249,1000,-1000,746,88,-281,-411,-1000,-674,1000,792,492,-932,1000,301,-369,-1000,-1000,827,-646,513,-928,803,-1000,1000,820,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{315,-153,924,858,-5,-193,-588,396,169,1000,386,1000,-1000,408,353,126,-577,49,-293,-234,-171,-680,1000,-488,-1000,-789,-584,-822,-1000,-184,160,-284,-1000,1000,-874,-494,395,-25,636,-1000,1000,-618,-291,-807,-1000,-443,1000,1000,937,-1000,188,-89,-294,-754,-1000,1000,-746,-106,846,1000,-831,1000,-78,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{-165,-1000,1000,887,-6,699,-90,467,207,-1000,-461,-354,-596,-889,-788,205,1000,-748,-671,267,-744,-1000,-5,-1000,-368,125,1000,-1000,417,-1000,-851,-218,225,372,-279,-1000,-714,10,-62,359,-729,-945,-1000,-309,1000,62,1000,1000,294,238,1000,-1000,1000,-249,-126,-789,1000,-1000,126,1000,-203,996,268,-305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{-796,-443,630,294,-280,-127,-256,37,281,979,79,783,1000,533,-1000,-582,-965,385,-1000,-31,719,-442,-503,1000,324,875,-50,-82,-775,857,680,-876,-491,189,359,43,-65,184,-548,-78,1000,-205,169,53,476,303,-426,-421,756,716,18,-331,-263,882,214,931,-1000,1000,195,1000,129,673,360,-154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{-590,-73,-388,-190,28,135,-674,-574,678,-585,-720,-355,-944,-323,-119,-28,813,279,983,601,-42,-311,739,571,353,792,735,645,880,608,-138,-103,-824,335,90,876,465,696,348,828,917,356,22,1,-762,-186,-731,293,-265,-353,-721,636,975,198,-83,-567,337,-79,734,-955,241,-547,274,943}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{1000,-238,-113,1000,-757,23,-1000,794,93,191,476,275,-1000,691,681,-656,-456,637,-486,204,738,-150,649,-569,636,-1000,-53,-1000,-524,-756,611,-627,-630,78,-171,932,-586,-1000,1000,687,836,246,-595,330,-174,59,390,-247,-739,-1000,74,-741,-33,-1000,-365,-667,-207,416,413,-381,-37,247,685,487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{-527,-546,-295,-204,-657,979,437,664,377,784,-595,502,1000,956,-482,-149,-1000,-55,-1000,-295,605,-392,-25,1000,-945,-137,204,-422,-337,-29,641,-116,-786,-313,439,-715,331,258,-1000,-727,726,133,-131,571,527,182,-68,-526,-329,1000,-155,68,154,1000,629,0,-969,846,-43,-3,119,634,-158,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{-1000,-1000,782,1000,-370,329,-824,37,953,268,-856,-762,-606,-1000,-852,450,541,385,-1000,1000,-1000,-1000,-244,-55,4,260,1000,-1000,788,857,-229,-288,235,-68,202,-1000,-444,357,-548,538,319,-1000,-1000,-758,1000,384,503,991,531,-1000,438,-1000,1000,492,654,931,838,-318,-413,1000,-916,1000,-62,-799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{-400,490,-1000,-1000,-82,-412,-962,-521,158,-39,754,-753,1000,623,728,539,463,885,-1000,-32,503,-228,756,1000,318,173,263,-1000,531,400,574,-433,-780,152,298,1000,400,-235,1000,980,527,-35,377,-177,-802,446,-448,-177,-212,-986,-437,470,1000,-124,663,27,431,-26,-715,-1000,-556,-312,535,38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{964,-685,478,790,-489,470,690,1000,624,-638,-1000,320,-1000,-121,1000,-236,207,-380,477,-571,177,-963,739,-1000,-549,-1000,666,-722,-679,-950,-138,311,143,-365,-298,-839,-187,-532,-83,362,-561,220,-1000,180,184,-296,874,794,-797,-160,1000,-664,1000,-3,-83,-264,337,-1000,243,806,357,1000,336,910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{-1000,929,1000,-1000,203,178,-741,-1000,-755,1000,619,1000,1000,-1000,-1000,-597,-1000,264,-725,345,492,931,588,1000,1000,1000,-1000,1000,377,1000,439,-1000,-1000,508,-644,1000,258,1000,-1000,1000,1000,373,893,-591,-582,879,-1000,-44,1000,-332,-998,1000,-1000,-501,649,1000,-1000,1000,-549,889,-623,-1000,944,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,-1000,298,-298,572,-31,-178,-459,312,-1000,510,-181,-1000,600,-246,747,-928,1000,978,-341,281,1000,15,-582,1000,-1000,-132,412,-1000,-313,-1000,1000,891,-329,-190,1000,-1000,579,-887,-421,589,1000,-1000,1000,596,-1000,128,626,700,276,-195,355,-1000,-181,-742,-973,94,1000,-581,-432,-601,-1000,114,-171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,-1000,-125,-1000,493,773,144,-425,585,443,-929,-1000,25,1000,-1000,476,-186,1000,787,846,144,-897,-1000,-377,1000,-144,101,1000,-883,-1000,-939,-281,-794,1000,569,-613,187,626,679,1000,889,-255,-140,1000,179,-1000,-459,973,-1000,-1000,-530,1000,-1000,135,377,1000,-534,904,-76,1000,797,-1000,-675,603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{137,-223,1000,493,-1000,612,143,965,-329,346,157,-608,-356,-523,-1000,-177,635,-102,-365,-621,-376,1000,-427,-407,1000,392,-727,1000,-1000,-746,-994,-166,1000,1000,-786,370,-259,481,-478,-100,339,-257,-1000,276,-1000,-690,223,748,-620,717,1000,813,-516,315,-724,1000,-327,84,328,-137,-668,500,449,-849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-928,-311,130,-1000,1000,851,293,626,-288,-142,44,-1000,-1000,751,-377,867,-897,-153,329,276,-316,-614,340,-683,1000,-861,889,240,-935,939,-258,1000,753,154,-959,1000,-933,-191,-556,-95,349,722,-1000,519,-391,45,85,-262,-430,-284,671,-22,-714,-831,-716,553,-187,944,-152,441,90,-750,-25,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,-1000,229,-357,555,879,-491,90,-926,716,-63,-1000,-699,1000,-801,476,559,1000,1000,460,38,503,-1000,-423,1000,-816,-1000,1000,-1000,-1000,-1000,504,-259,1000,569,21,289,1000,408,264,1000,-922,-1000,1000,-527,921,139,1000,-1000,-783,359,1000,-1000,656,617,1000,-54,1000,-834,1000,616,1000,-342,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{776,751,727,390,1000,453,662,816,-411,-894,310,552,780,-440,-1000,-237,692,702,-253,-724,783,-657,1000,-648,220,-725,544,323,471,-742,-79,1000,-22,-489,-897,510,-839,645,-178,-406,-1000,-100,-1000,-534,400,676,504,676,400,-156,551,-348,1000,-1000,-623,-400,-450,-273,-1000,-244,-1000,1000,155,63}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-725,-1000,661,-400,-556,304,-877,824,711,-113,-35,-43,-172,917,-1000,350,-430,725,1000,750,838,-247,-989,-933,991,-302,-394,424,-600,399,91,-546,92,400,-215,-514,211,814,269,937,204,-216,-741,727,-112,-1000,675,839,-369,-364,671,1000,-888,290,-504,985,34,1000,-762,988,-72,-543,713,75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-754,-415,1000,-293,-980,-360,129,-571,151,-690,-109,402,-34,835,1000,-647,-897,-99,689,459,349,1000,-551,-539,969,-860,-862,828,-1000,-434,-282,161,242,-140,-996,113,208,1000,-953,184,631,22,-938,960,-733,-502,831,1000,-645,161,150,1000,-1000,330,-530,496,-454,536,-288,435,-291,-428,-1000,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{191,-157,422,-735,739,494,834,-926,-156,267,-978,-922,362,178,635,329,-235,-596,-426,-579,123,17,543,637,399,-743,248,51,-299,-165,-540,839,699,300,-198,873,-425,116,304,-404,638,-231,-484,-924,-55,956,-701,221,-809,-272,267,-841,484,278,320,964,620,-959,687,104,-5,-285,476,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-20,1000,990,914,-347,864,-257,528,-174,263,-85,991,-1000,812,-1000,-1000,89,-445,-328,-1000,261,1000,-597,-609,-425,-849,360,638,586,652,-227,-1000,-1000,632,-19,1000,1000,-556,1000,649,-1000,-609,-162,-251,-733,693,499,806,-1000,-716,-200,-77,1000,462,887,900,638,-592,-574,-1000,174,1000,-1000,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-7,-414,53,-420,664,-362,431,-836,164,740,-822,-193,575,378,16,-943,-22,-724,-286,-132,340,356,-89,-1000,-45,-20,349,548,582,-1000,444,825,-457,-627,-895,-405,-1000,497,-1000,693,-433,-191,803,-1000,-150,-216,206,320,942,-378,449,-872,199,634,911,388,-146,-1000,-1000,-22,-1000,-860,1000,386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,-145,-5,188,121,792,556,994,888,843,-587,984,875,1000,103,-925,-481,214,-1000,286,-727,35,-241,-1000,-804,225,-225,1000,426,-1000,415,165,353,-606,1000,1000,-447,621,-871,880,637,351,-340,-1000,-1000,671,260,37,1000,290,569,-472,-173,-585,-659,1000,-984,-1000,488,377,-1000,-890,-633,828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{278,-79,-390,-692,146,-1000,839,540,34,301,121,-292,-413,475,-163,533,-1000,629,-267,235,753,-1000,-530,947,-1000,-1000,1000,-109,229,-643,-1000,-93,-825,406,-1000,-95,463,-844,1000,-148,-388,-953,169,-744,-399,-401,311,360,661,188,85,709,419,-141,170,-584,-578,-777,513,-1000,-134,-596,63,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-686,159,277,44,-351,352,-843,1000,599,114,561,1000,1000,-589,-981,-750,-678,1000,219,412,-1000,-1000,-578,-545,-1000,-477,226,-334,702,-483,-688,-635,-388,-695,500,630,-114,50,976,-620,-755,254,-154,989,-283,502,743,1000,463,940,-290,533,-956,-484,-941,-332,-1000,-518,1000,-609,-140,-662,-644,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{169,-269,995,808,-41,646,-799,994,624,463,125,932,723,-102,-182,-634,-509,967,-221,-563,-703,-533,-291,126,-935,-895,111,-46,863,-726,-56,-257,799,201,656,430,-800,276,-121,-632,-380,726,-177,341,-349,468,993,572,759,991,-798,466,-647,-586,-899,467,-775,-515,416,498,90,-792,-90,757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-7,1000,853,-798,-110,-546,431,1000,575,424,-629,742,318,-248,161,1000,703,715,508,-1000,-486,264,-1000,1000,-866,-433,1000,-920,-430,394,-1000,825,-511,-911,1000,600,-978,-1000,-1000,-969,1000,428,567,-1000,-150,-140,-205,408,1000,-378,12,1000,603,-1000,911,-838,-487,-1000,-1000,-780,-1000,-1000,-1000,819}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{650,-1000,-5,723,119,-102,489,-256,260,380,-445,-626,661,1000,103,-925,-568,676,-149,-178,247,-992,-26,692,-804,-1000,1000,730,-110,-1000,415,752,356,251,-1000,-851,-698,73,-566,880,-1000,232,1000,-663,617,264,1000,-186,1000,175,570,11,411,394,346,262,-423,-1000,-1000,193,231,-1000,1000,433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,-1000,191,930,267,-393,-363,-831,36,469,1000,-371,366,904,351,-1000,-1000,697,-870,-1000,225,-1000,1000,598,-1000,-1000,-569,-1000,183,-1000,-454,-1000,1000,-122,-825,-1000,1000,1000,805,-872,-609,-541,-863,835,-174,-967,1000,489,-1000,1000,-1000,612,-325,193,222,-284,-1000,-884,297,963,1000,322,-1000,947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{921,1000,731,-1000,139,234,-75,1000,495,423,-1000,806,531,-1000,745,1000,1000,-1000,933,-599,-104,1000,-1000,-305,848,1000,-124,-480,-358,809,-230,-1000,-374,-1000,1000,490,-1000,-1000,-488,-83,1000,1000,751,-495,-1000,334,-1000,-240,1000,-40,260,-177,1000,840,-777,-907,-641,638,-683,-402,-1000,-660,-1000,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{-1000,485,-1000,555,-633,-561,544,-519,16,-1000,-508,-678,-885,664,518,-867,-1000,-262,-185,1000,1000,-1000,-1000,1000,-1000,-401,80,1000,400,-1000,-1000,-845,156,510,616,-482,-872,-776,889,-1000,419,-1000,11,-276,1000,-915,1000,390,1000,441,-455,986,-1000,896,-916,-1000,-197,1000,-806,1000,-110,-1000,-794,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{-1000,-1000,-221,-437,-354,28,814,152,733,465,340,41,982,924,-238,716,687,-427,788,-11,419,1000,-696,460,-533,414,9,-1000,261,-1000,481,719,-1000,-90,124,-1000,160,-308,-541,-736,-368,-700,-1000,-437,-360,177,-138,-999,-510,635,76,-592,-223,-1000,641,-1000,-1000,-387,88,528,897,43,248,-544}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{-291,-85,-1000,-459,-633,440,235,816,754,-1000,-340,-1000,158,763,1000,-585,-278,-780,1000,53,266,-297,-411,246,-1000,391,-597,-80,-13,-201,-352,-16,-843,-491,58,-1000,-1000,73,-242,-1000,1000,-1000,-1000,-1000,-400,-292,1000,-724,-726,831,-31,986,-1000,553,113,-1000,-197,-400,-709,593,779,-1000,-141,-273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{-1000,-1000,141,214,-757,-577,-846,182,637,106,1000,627,1000,863,172,449,1000,-1000,1000,395,-933,-382,1000,-213,187,350,788,267,-1000,894,737,989,1000,765,-220,1000,554,-1000,-1000,-1000,111,-559,752,-1000,-983,792,-740,187,1000,1000,44,354,-736,1000,546,-156,-1000,-517,-294,-1000,966,193,884,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{311,-818,-1000,311,-1000,-1000,-1000,-741,1000,26,1000,431,-183,249,-297,-297,-1000,-222,469,78,-18,-501,-424,-228,166,-1000,217,495,-926,345,143,1000,-243,628,-572,1000,-122,-754,404,-1000,-182,-953,1000,-1000,229,133,-490,-819,1000,1000,459,14,-1000,681,751,96,-1000,1000,-411,604,1000,-526,402,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{-260,-595,-167,-459,-412,1000,-727,1000,771,953,268,-489,1000,-432,352,21,-21,-796,845,-921,-49,-18,566,-902,264,784,-1000,-379,-780,1000,175,302,-388,-1000,385,-165,-748,1000,-1000,-494,-593,-1000,-1000,472,-737,755,229,-929,-1000,-174,904,333,-276,243,1000,-319,-210,-1000,-609,-749,820,-486,694,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{-815,271,-1000,-232,-481,750,-245,798,-637,606,664,170,369,155,629,-840,-843,909,-751,-1000,737,1000,-421,-458,422,-729,-1000,63,-101,564,-743,48,-260,-1000,1000,-179,-620,830,-473,162,-30,-638,-1000,983,357,-108,667,540,-210,868,450,792,-6,43,1000,-417,-215,-852,-777,936,-695,-514,924,710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{753,631,534,-898,-354,1000,-955,1000,-252,229,320,1000,952,-481,373,96,246,909,847,-1000,-1000,1000,-421,-458,578,33,-1000,183,261,1000,415,-460,261,-1000,-380,-1000,160,566,-943,861,244,662,189,-76,357,-292,-1000,540,16,117,657,-592,636,43,1000,1000,296,-852,-428,589,-695,607,924,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{-889,-79,-707,-563,-979,526,-671,908,564,304,581,519,763,637,353,-867,-46,-230,791,-378,6,999,865,-5,114,-145,-349,-514,-770,888,-76,575,600,-418,710,-564,-731,-569,-829,-718,-63,302,-60,-276,42,-221,-134,390,-369,441,28,777,-816,724,807,590,-670,-648,-141,-316,204,-788,879,105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{429,-273,289,735,-692,708,373,-562,949,-1000,-435,-935,610,594,-17,237,548,-678,1000,566,-341,-535,717,-328,-710,847,-1000,-758,-472,-1000,387,623,-136,793,-1000,37,-101,-862,-335,-827,-144,-1000,22,-975,41,597,-363,187,-253,-427,31,-522,-596,-12,-859,-299,-490,582,-449,-604,1000,-880,806,-848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{1000,1000,-1000,-40,-600,-62,573,-937,1000,-512,766,1000,878,912,-1000,1000,598,1000,1000,-662,613,1000,987,1000,1000,-708,-813,-874,-1000,-101,-193,-33,-544,-1000,313,-354,1000,78,27,-1000,685,817,-487,-810,-4,-70,-511,-184,368,-646,1000,245,-176,-470,720,-395,1000,-428,-106,1000,1000,-154,671,41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{95,198,296,-864,531,-241,63,453,-707,235,1000,1000,577,-1000,-558,137,256,-682,-1000,824,241,672,1000,346,-1000,-819,-661,612,63,-1000,32,-803,1000,277,1000,1000,400,384,705,423,0,1000,-271,-156,200,868,-944,-966,250,-255,14,280,-770,-630,-1000,-1000,1000,-3,234,460,334,-792,496,487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{1000,-822,406,1000,-670,-405,-510,748,-47,-556,307,-1000,213,-545,1000,678,628,633,-18,-478,655,-1000,124,-292,534,-70,1000,91,455,971,851,-751,-523,266,-1000,916,-763,615,-1000,-67,-496,-716,-256,298,472,201,961,1000,-1000,-81,-821,1000,769,401,-26,1000,-1000,-450,-973,-82,-986,939,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{868,-1000,146,-1000,367,-252,897,1000,-449,1000,325,1000,-502,-346,181,709,-1000,676,281,769,66,572,-335,280,413,-1000,-21,-420,-1000,256,-1000,110,900,14,1000,948,957,-697,1000,-99,-358,-527,-1000,-857,-572,-154,400,-195,-109,-34,73,-1000,-542,50,-241,-325,1000,429,-603,-592,-127,-1000,1000,784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{-1000,-1000,-411,-717,993,579,-484,-1000,138,-667,783,-745,811,-717,-150,1000,103,-120,-324,763,618,-269,1000,282,-1000,-244,-1000,61,366,-903,340,830,362,133,-479,1000,725,800,305,421,-225,212,-564,326,436,552,-978,218,913,1000,-41,-863,-841,-1000,31,-73,21,-28,-1000,-372,647,637,-753,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{846,-710,-475,1000,-391,-55,-687,-682,-429,-228,447,-575,-621,396,465,930,-10,-362,225,-130,559,-674,-1000,940,-275,653,225,-155,408,-168,123,4,-314,6,-136,1000,45,55,-746,112,734,-102,316,-293,160,307,-742,674,192,43,-544,1000,-488,242,-558,511,-242,-83,-627,-608,-580,504,-917,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{1000,-1000,1000,1000,-1000,329,-447,1000,-719,-27,-87,587,-1000,-1000,1000,401,-71,736,-336,-554,560,-1000,-1000,-401,1000,1000,1000,-382,1000,1000,1000,-909,508,1000,-1000,555,-950,573,-1000,-146,-1000,-1000,-239,608,807,-201,427,1000,-1000,97,-1000,952,1000,-331,-33,1000,-1000,-722,-941,-903,-1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:OA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{1000,-637,-749,887,-401,-517,546,-758,-426,-922,927,643,-101,1000,365,784,1000,657,-749,-517,1000,441,-480,59,-1000,724,-805,-955,1000,-116,-49,3,-1000,-1000,-1000,347,145,1000,-1000,-588,1000,-644,560,-903,1000,504,168,1000,-1000,-1000,804,23,756,-1000,-1000,94,1000,-1000,-259,-983,1000,-882,270,487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{1000,573,-682,386,-523,138,-447,-1000,780,-524,-163,587,315,289,376,874,570,649,828,-554,520,-199,-331,458,1000,-195,886,-809,-568,1000,1000,-290,-440,369,-113,-341,1000,356,-165,-821,211,-514,-576,-734,471,-143,-686,66,-60,-423,1,451,1000,-908,-87,1000,396,-241,-39,848,168,578,-230,-340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{800,412,1000,-210,387,-516,1000,1000,210,571,-792,132,-946,-1000,1000,-78,867,-1000,-678,-1000,-793,-86,-381,-222,-1000,1000,1000,1000,996,-957,-191,-561,-1000,279,-1000,456,-115,-1000,-927,111,232,330,-310,1000,1000,-184,57,-1000,1000,-546,-587,-49,-1000,1000,702,-10,-821,410,-367,23,264,-611,522,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-568,-227,-138,-1000,-440,-1000,-636,1000,-748,-5,-1000,-942,-1000,443,654,634,885,-1000,-962,-809,-620,376,313,-858,-895,-94,350,556,-110,-245,-306,-748,-789,-980,181,555,-1000,-702,-1000,-370,1000,289,311,1000,487,403,-72,-1000,-652,-139,-453,-1000,652,625,250,882,-191,1000,-1000,1000,927,827,808,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{559,483,-488,-699,719,-809,270,819,-66,-131,-75,103,-994,738,506,-139,836,-714,11,-655,-664,571,-530,-743,-849,939,505,366,368,-792,-103,530,-951,-271,-548,587,-216,-574,-959,-335,738,-95,47,883,681,16,-332,-920,-660,-100,-467,-701,-907,464,903,28,-495,470,-633,895,469,989,399,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{655,-14,-86,-1000,54,169,-539,-916,606,-620,124,-1000,-252,163,-137,-455,955,252,939,914,-815,86,237,-546,-725,238,47,-632,-93,-345,471,-331,-846,-35,75,308,650,-106,-85,-47,300,-984,-491,336,-142,-715,-401,962,961,-104,-138,-634,612,-41,-51,-947,-283,163,-49,818,-456,-542,29,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-558,459,118,354,1000,1000,745,491,-976,177,-579,562,-1000,211,1000,-374,-1000,-621,-728,-1000,942,850,-352,-85,-7,-234,-1000,547,1000,321,-549,1000,-50,294,-276,123,263,-1000,340,-524,996,601,826,463,699,967,241,-414,-158,-60,-204,98,-1000,-228,-47,62,-292,-342,-723,-76,-776,-941,163,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-611,483,-735,-699,463,-689,167,-522,-497,-449,-267,113,-496,843,314,313,1000,-124,-316,-619,-580,208,977,-1000,-721,1000,1000,366,-71,409,192,901,-224,-66,902,166,121,-610,-495,-229,297,-407,-258,840,385,-420,123,-789,-932,-2,-39,-513,-1000,311,383,-268,-495,-510,-529,1000,-328,1000,154,705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-652,-1000,-86,-1000,1000,-486,-539,1000,796,-981,-846,734,-992,781,1000,1000,1000,-515,-587,-864,374,864,-1000,-1000,-1000,1000,-660,1000,277,-1000,-835,-519,464,-945,1000,308,-462,-357,-85,-1000,-293,342,777,1000,-65,-1000,-412,-122,155,1000,844,-1000,-1000,-825,965,299,803,1000,-1000,497,1000,-743,865,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-26,-1000,-648,-1000,614,-91,-852,688,1000,-222,-562,-166,-322,-1000,1000,306,1000,-209,-1000,-557,19,-553,1000,956,386,1000,-74,27,-370,-292,-424,-139,79,-48,498,-487,-12,-1000,-61,-548,-398,153,-453,1000,257,-1000,127,699,1000,698,400,-155,-790,-21,-109,-414,1000,966,-688,-655,810,-1000,593,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{385,30,220,193,969,-637,49,3,40,-556,-117,-943,-423,345,-430,-346,99,687,294,486,-437,-189,471,-762,-555,-788,901,119,972,19,464,-248,-930,-313,-618,651,983,765,681,-423,787,-453,-172,485,25,788,-242,480,574,223,-526,217,498,-397,-733,-463,-859,-812,199,757,-28,180,-30,21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-563,-325,-1000,-1000,-866,-1000,699,186,59,-530,-34,-491,216,-507,244,286,-253,857,755,800,-275,984,992,-786,806,54,305,-959,29,-534,-493,1000,-1000,1000,103,-604,1000,-220,1000,1000,22,-885,-140,873,-345,-985,1000,44,-766,1000,289,-533,-960,-927,-663,1000,-439,779,168,-660,-383,-172,-395,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-818,-752,633,158,205,-466,473,-1000,-596,1000,-948,-787,1000,1000,329,196,1000,-494,-682,414,901,24,-108,100,-497,203,-440,206,219,527,-312,-633,737,723,292,1000,-8,-788,680,-600,-661,1000,-365,784,-529,1000,-548,-363,427,441,910,275,510,-471,285,242,-865,822,157,-372,-708,291,473,-578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-235,700,788,246,-677,33,-720,-1000,-654,1000,-1000,-1000,910,-433,-12,-124,-125,667,109,397,191,237,584,-225,-1000,1000,-1000,1000,1000,258,1000,531,861,1000,-1000,1000,817,-601,296,861,1000,821,-742,534,1000,-545,232,-445,144,608,563,190,-676,-142,846,-912,-1000,-1000,431,-1000,-815,1000,-418,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{346,1000,-890,-832,-1000,-886,-349,-1000,-76,30,-71,-479,1000,946,1000,-400,-460,-30,1000,763,969,-244,-9,34,-159,-264,-745,1000,-338,-90,-779,24,-405,-724,937,155,-704,381,-1000,1000,690,-428,-286,-727,55,95,-580,-1000,422,409,-136,-748,861,733,-259,-560,316,-355,168,1000,378,360,-404,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-931,-1000,978,1000,-1000,-32,-1000,-1000,-932,539,-1000,-495,901,613,145,-129,722,562,60,762,256,-105,1000,-33,-1000,1000,-870,1000,-506,672,-406,892,1000,1000,-1000,-1000,1000,-957,630,1000,103,-237,-421,1000,1000,-994,81,218,80,1000,1000,-130,-198,-772,-451,-580,-1000,72,-36,-883,-909,680,-260,-513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-1000,-1000,-456,83,-474,68,-329,-381,-169,1000,45,-1000,-240,1000,-764,-1000,952,478,56,1000,999,401,1000,-322,-1000,486,-1000,424,623,-538,-839,67,276,1000,-394,1000,1000,-364,931,923,1000,-237,-1000,1000,131,198,-72,-334,-1000,1000,837,1000,-1000,-1000,-418,246,1000,762,381,-83,-1000,1000,-107,-782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{339,453,-423,415,-808,431,149,730,-805,55,-583,-1000,-382,768,171,-43,441,86,507,591,535,-504,344,-239,-887,-127,-652,429,-587,628,-582,-522,-1000,-123,163,204,-782,149,-72,378,122,-829,368,-765,134,673,-1000,-1000,-73,473,-1000,125,154,-274,626,-1000,1000,916,-614,-50,292,-684,-133,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:OQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-563,-325,706,-1000,-866,-1000,699,-106,59,-530,109,-1000,216,-507,244,286,-253,857,755,800,-275,984,992,676,806,54,305,-959,29,-534,1000,1000,-1000,211,-1000,-604,1000,-220,1000,9,909,-885,-140,873,-345,-985,1000,44,-766,1000,289,-533,-960,-927,-663,1000,-439,-954,168,-660,-383,-1000,-395,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-316,-351,464,636,475,468,669,-48,-1000,516,-251,-462,-94,915,-661,76,978,29,-640,620,653,-474,593,504,-218,-477,-425,133,98,270,-484,-270,-453,873,272,322,-354,-720,666,-423,19,144,118,49,259,422,-498,-622,64,318,-25,507,-140,-628,829,-561,-18,870,-613,-348,120,-1000,864,-958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-461,-1000,1000,393,201,79,1000,-586,524,1000,-915,-845,2,250,-680,55,840,-101,-997,751,795,-288,991,815,-609,-142,-71,497,287,654,-338,-503,114,709,415,800,-30,-1000,730,-411,689,-1000,-719,788,659,818,-552,-526,8,348,672,597,-12,-1000,264,-523,-427,668,-664,-465,-497,-207,999,-406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{755,-1000,1000,256,1000,-586,140,-254,-855,273,1000,469,618,-49,-417,448,-576,1000,239,-616,479,1000,-974,502,-495,-1000,-697,-112,-1000,-157,-455,-1000,-1000,-373,-1000,-58,-93,680,136,289,529,-204,95,-480,168,-177,27,1000,466,1000,-558,-975,-1000,-1000,-515,-474,-811,21,-1000,1000,794,164,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{242,-90,880,-697,869,765,151,-327,-564,-447,183,902,1000,-357,-69,-367,683,277,-627,190,-291,708,654,-533,-311,-79,493,83,-665,710,-350,303,1000,-240,-920,-319,55,329,141,587,-376,-319,39,363,843,130,88,636,454,407,-891,27,140,-332,-159,-1000,-840,-1000,179,533,359,-81,603,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{766,-1000,-236,338,-1000,-126,-1000,1000,1000,533,-542,367,323,61,744,-716,509,78,751,-803,-786,-1000,-746,287,-311,-1000,493,1000,1000,-573,-1000,1000,-734,91,-920,1000,-294,-996,174,-149,-376,1000,-1000,-628,-1000,690,-1000,-1000,-976,407,628,528,690,-332,-137,381,1000,1000,-407,533,-12,-254,-1000,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{200,-388,385,1000,794,-466,723,270,78,-864,1000,599,1000,525,849,-954,99,1000,-30,-318,-1000,-1000,270,234,-212,1000,-231,-1000,-1000,-476,166,409,1000,-117,1000,-696,-267,-585,356,821,1000,-1000,-1000,-154,154,-321,-1000,1000,439,686,-18,376,563,704,1000,-1000,128,829,-211,-724,-594,-300,333,-958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{417,572,493,1000,-1000,-432,927,587,13,-1000,89,-281,1000,666,-853,295,382,1000,86,-308,-636,-749,683,196,-199,1000,-243,-537,-1000,-580,844,767,1000,-281,1000,-820,-990,81,606,596,1000,-788,293,-64,437,1000,-1000,350,803,385,1000,580,1000,2,865,-1000,-428,107,99,-1000,659,-972,-660,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{505,-1000,-470,619,256,38,208,1000,1000,436,305,775,1000,-66,-196,254,167,1000,451,-1000,170,400,-722,976,-536,-560,240,1000,-1000,-623,-1000,734,177,353,-1000,-232,11,-162,137,78,451,893,-210,-1000,-192,1000,-1000,-195,-906,1000,-85,1000,995,-1000,-137,381,-1000,-280,-1000,517,863,-317,-261,-342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{-239,1000,-1000,-810,-472,583,467,-358,35,-1000,-668,363,709,1000,-573,-104,-99,-614,-229,1000,-376,-1000,1000,-1000,-470,1000,1000,-641,-68,-499,-19,1000,1000,-220,187,-342,-463,991,305,-1000,1000,-1000,459,1000,-278,1000,-721,-411,68,-721,358,1000,1000,671,-905,-278,370,671,1000,-711,-887,-278,-1000,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{278,-1000,858,-188,1000,978,-916,37,-723,537,1000,701,205,-679,475,504,389,460,-693,-306,-352,-1000,-1000,18,-705,117,-68,1000,-1000,1000,-1000,-682,-301,465,-38,668,-436,908,-976,905,775,-1000,-187,507,-668,-321,145,1000,61,1000,-1000,-1000,266,704,-4,-816,67,-1000,-1000,724,308,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{-260,965,1000,1000,-606,-1000,-606,-1000,657,-1000,1000,43,180,695,-576,458,-325,963,-1000,849,-558,1000,257,-135,873,1000,-625,-1000,-1000,-617,592,-92,1000,-654,1000,-761,283,800,560,960,1000,-893,129,468,154,453,-292,1000,416,-168,390,1000,1000,193,-1000,-1000,37,-1000,-41,-1000,-515,-563,-139,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{-270,282,276,680,360,-82,245,-21,155,-651,-448,214,495,476,-131,32,215,319,-344,74,-418,98,511,1000,-207,593,-65,-257,-365,-1000,108,152,704,-147,289,-173,267,352,160,635,416,-709,12,109,-892,1000,-1000,-426,-240,-309,169,259,599,898,473,-690,273,278,247,-622,312,-16,-333,-631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{748,153,760,-498,-681,-126,-586,-935,-1000,-138,-472,128,-833,585,1000,-1000,1000,-1000,-884,741,266,-1000,601,287,-1000,314,629,-658,-460,-573,1000,-148,-779,-710,-312,1000,-530,-996,-748,-149,-289,-714,-962,1000,-283,-955,1000,-284,-327,-196,-345,-95,-393,153,-327,-1000,1000,1000,1000,307,-1000,531,-365,-212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{674,-751,-107,547,-1000,-1000,-701,-859,264,-452,1000,36,93,-446,380,-82,258,604,1000,850,356,347,644,-179,-1000,-119,313,-534,600,276,-358,58,-988,966,-576,891,-993,-512,-259,-490,-76,1000,-908,403,396,-360,1000,-424,1000,1000,123,-1,679,74,1000,-540,-352,20,1000,-311,430,-60,729,-129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{983,-101,-1000,1000,-1000,-306,-923,-500,-26,-752,410,868,-396,-1000,744,-82,258,1000,1000,-16,-1000,1000,644,-179,-1000,-466,-793,-534,600,820,-693,1000,-1000,-288,23,652,-765,-1000,146,-490,-972,630,26,847,395,618,1000,-610,100,492,123,200,1000,74,1000,-1000,189,20,263,639,430,1000,1000,-790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{-521,1000,7,330,114,839,459,-373,1000,563,-955,100,142,-384,71,-1000,-540,-897,-565,225,-254,-451,913,34,816,217,-189,327,130,79,-718,-862,667,1000,373,-1000,151,748,-129,-722,-644,-879,410,-854,-1000,516,-474,-1000,58,-558,-268,1000,-567,-885,639,1000,668,-297,666,375,143,-606,-160,-624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{112,272,-1000,942,-753,659,372,-758,-472,-867,631,-371,763,11,585,173,-263,1000,608,-1000,-106,1000,101,503,-805,-994,-732,210,344,1000,-87,589,-1000,-1000,-227,206,525,-241,300,73,-22,170,590,530,-200,-41,-351,-473,-683,-1000,11,243,137,287,950,-468,557,-531,189,782,284,-1000,-3,-136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{1000,-847,-1000,543,-1000,-1000,-292,-793,-891,-1000,1000,1000,-922,-1000,18,-757,-1000,1000,1000,1000,-419,957,-735,-900,-1000,-896,624,-1000,566,1000,-678,467,-1000,1000,-286,1000,-595,-1000,-6,-325,1000,1000,-955,1000,739,-839,-902,-494,1000,970,469,-386,854,1000,-35,-1000,-1000,461,759,-202,1000,-47,367,-327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{-357,67,135,-287,130,-776,-94,-27,-482,-222,513,5,-1000,-491,-1,311,-292,-1000,225,407,328,-185,-210,-172,578,1000,1000,-1000,1000,-741,550,-487,232,556,25,-881,-824,125,-1000,-498,201,905,-346,-147,-147,1000,-592,1000,854,489,-71,-938,865,-593,-486,-928,-820,601,-441,510,910,-1000,-219,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{686,-932,-873,933,-286,94,-162,-718,539,-108,322,283,260,-546,947,292,-774,1000,391,1000,-1000,1000,-384,723,-233,-990,-588,-469,304,727,-693,1000,-420,-143,-38,239,-555,-696,372,-94,-423,592,-74,652,-23,-254,882,-560,-476,1000,1000,251,469,184,-41,-686,32,-445,129,393,-276,181,1000,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{-338,-1000,-283,-340,986,778,1000,132,-762,782,-783,-223,-49,-624,506,-796,146,464,-780,216,954,-687,150,-626,542,154,-108,346,-566,478,-137,-1000,-841,-378,207,-414,669,-648,1000,1000,924,-505,-189,-437,863,-450,-982,588,608,-68,-1000,1000,-343,93,-641,1000,-289,164,-898,290,-128,-100,136,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{781,-133,1000,-173,-42,-1000,-909,-500,-288,-342,1000,504,-466,-1000,-939,-1000,-201,755,349,370,275,1000,644,-121,-1000,-160,-793,-534,916,-375,255,-850,-1000,415,759,105,-31,-435,-19,1000,757,-121,-963,847,312,6,17,728,1000,1000,81,-591,-1000,731,-1000,154,-33,-448,193,63,1000,-1000,-423,-790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{111,186,-275,778,-379,1000,-854,-487,310,-627,-1000,-629,1000,208,851,-1000,-1000,678,-435,-686,-1000,727,15,555,577,-705,-579,1000,770,-1000,444,-641,-146,-787,732,-1000,-219,303,520,-618,-1000,-11,1000,1000,-1000,1000,-83,-670,-1000,-874,228,-84,1000,1000,571,231,1000,337,-336,1000,-690,-273,-1000,-687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{1000,-1000,1000,-834,209,-641,-414,-1000,1000,1000,-304,-1000,1000,-718,-545,-481,-1000,1000,113,-469,-1000,723,-788,776,1000,-1000,18,-1000,1000,-648,-959,756,1000,1000,-917,1000,444,968,-781,-1000,950,-647,232,17,1000,139,-297,929,-1000,885,1000,595,1000,-29,1000,488,-1000,489,-72,-428,577,955,591,478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{1000,-1000,230,-1000,-135,439,-942,-97,1000,1000,-427,303,651,-1000,435,495,-1000,522,-597,200,-1000,119,-1000,-68,-258,-1000,-210,-1000,-27,-753,-1000,156,90,503,145,1000,910,-643,-1000,-1000,753,109,-162,1000,-551,-23,-955,-402,772,777,153,-15,-78,-1000,-803,959,236,450,-330,1000,945,-88,132,-96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{502,-75,1000,-508,-140,-925,-26,-547,1000,838,-206,229,1000,-699,-1000,-425,-614,-100,-523,-1000,-1000,471,202,25,1000,-1000,271,252,831,-1000,-461,-705,582,-173,-578,946,-887,528,-687,-69,759,116,-97,-967,-240,838,634,-144,-1000,1000,430,-46,1000,-261,206,-370,-1000,-276,-700,-907,-144,1000,416,-111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{342,-57,749,745,-965,962,525,705,559,-1000,147,416,-385,362,-452,268,188,882,1000,694,412,40,979,-497,-544,941,999,157,-875,-1000,699,-529,-193,308,-724,-182,92,-574,-631,-333,103,-1000,1000,-177,1000,-1000,-416,-412,87,231,-189,-1000,-158,312,969,-1000,780,-399,-475,-339,892,783,1000,320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{223,-135,-784,-1000,-276,643,-366,-687,-1000,8,-14,50,291,1000,5,433,992,-215,-7,122,858,64,441,-303,65,1000,-33,-147,233,1000,288,-144,-409,-447,74,-1000,1000,1000,281,1000,-582,150,451,-378,-600,-676,403,-847,-1000,200,379,101,346,-298,142,-236,1000,63,455,-1000,-484,-429,-1000,-23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{493,-57,827,-204,609,37,525,83,592,364,774,-171,928,490,-452,-2,123,79,529,400,-480,1000,-4,132,-400,408,714,157,-875,-797,23,349,-617,-752,656,553,-151,-820,-460,-566,103,-1000,350,-202,-132,1000,-416,-934,733,-57,-1,-212,-662,-442,-488,-347,158,-68,237,1000,89,-1000,1000,-495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{759,1000,482,513,916,687,-691,1000,-280,43,1000,-456,897,1000,590,1000,87,-22,556,1000,-500,632,-113,-1000,-1000,-1000,1000,-201,-172,267,919,-956,-1000,-71,1000,73,352,-978,337,-267,367,-935,1000,-240,-400,626,-637,-1000,1000,906,-1000,-661,-1000,-453,-312,629,1000,872,-462,-602,94,-55,416,-295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{1000,833,-149,-875,322,1000,524,-542,-1000,-412,940,-182,526,1000,-684,685,1000,51,-7,246,933,74,1000,-901,370,760,-1000,698,564,1000,923,-655,-1000,1000,310,-1000,531,1000,517,1000,-84,-1000,995,-1000,-516,-623,1000,-922,-239,621,-378,-134,-503,49,287,-785,891,-236,-162,-1000,-1000,-704,-1000,839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-638,-729,-643,324,25,1000,346,-443,969,24,239,-78,21,881,141,-856,-288,-119,88,-570,-372,-825,387,-871,-1000,-461,691,-1000,-532,874,-13,269,-1000,-1000,-367,-964,-90,-424,225,-910,233,319,153,337,-430,413,-706,347,239,-18,645,18,1000,138,-827,612,-327,299,-424,-567,54,-184,854,71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{1000,1000,1000,374,475,389,720,-885,-1000,-439,120,-671,1000,135,1000,1000,736,832,933,1000,-1000,1000,345,1000,-206,834,-1000,-55,1000,54,-681,38,483,-1000,-23,401,61,1000,477,1000,-1000,1000,-1000,-7,-1000,1000,-482,-814,-608,-697,-1000,-607,-1000,-1000,-1000,436,1000,416,-1000,524,-1000,972,249,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-520,-883,714,-309,146,529,39,1000,-667,298,461,49,365,12,-61,-264,-651,-461,-824,-836,-845,-922,-636,-997,-612,385,993,-650,-453,-553,301,56,-1000,-664,294,-567,-403,-810,-613,-1000,-471,754,40,314,-317,141,-45,-63,775,-325,158,-576,610,392,728,1000,103,-315,22,-779,636,-153,852,-47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-638,-63,-497,324,-816,-324,78,-482,-291,24,-571,705,-676,777,-573,473,961,632,596,1000,1000,-63,-1000,-226,207,-327,-44,-871,-1000,300,891,-360,-1000,398,-1000,-964,-130,-557,75,-64,-519,319,921,-705,391,-1000,-649,-78,-339,-18,1000,1000,666,-450,-131,92,-626,354,-436,653,1000,1000,-786,-488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-687,-1000,561,-309,146,529,-94,433,-806,351,676,49,763,-348,993,665,-794,-611,-923,-1000,-1000,-987,-338,-1000,-612,385,772,-557,-325,-553,412,-86,-1000,-664,905,-564,-621,-853,435,-1000,-529,1000,-238,336,-322,172,-45,-395,1000,-624,-203,-1000,458,426,728,517,340,50,39,-835,636,-457,849,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,-581,-1000,327,-847,1000,182,102,174,131,559,-548,834,499,245,-1000,-1000,-341,-743,849,-426,-958,563,-540,-713,-406,239,-677,-362,695,505,-488,-812,-616,1000,-696,-539,-511,526,-690,-47,431,-282,258,-233,562,-541,-151,189,-626,209,933,220,240,1000,723,345,149,-1000,-683,15,-734,1000,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-49,118,41,793,683,1000,882,-906,713,-743,-258,-412,-757,1000,-313,73,294,801,764,-1000,50,-460,968,510,-1000,-1000,-192,-727,-135,1000,-666,477,-320,-1000,14,-554,-284,130,-14,-1000,979,577,278,697,-558,1000,-1000,974,136,72,576,935,928,147,-1000,706,-980,385,-193,-54,-540,-916,244,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{1000,-175,-1000,435,-1000,1000,-501,-89,1000,139,-326,466,-1000,760,-357,31,1000,952,-220,714,734,-1000,570,-19,-13,-126,674,-724,-1000,260,531,-1000,-770,-599,-545,-708,-1000,-1000,939,38,-209,-903,974,117,-19,-1000,30,-539,-444,1000,880,450,783,-498,1000,249,-1000,641,-572,656,596,640,553,-797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-977,-968,-955,-329,-43,624,74,354,685,131,679,-78,834,146,857,-712,-818,-426,-743,-904,-689,-958,994,-878,-602,248,239,-606,-272,114,213,-21,-918,-614,944,-351,-534,-511,599,-899,-148,822,-414,308,-439,477,-289,-330,699,-626,-350,-847,690,207,728,323,342,269,-389,-683,111,-805,849,167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-471,-329,-276,1000,-36,73,37,-967,248,1000,1000,-1000,1000,-1000,-320,-781,18,-285,-375,689,-989,-1000,-684,-973,-634,-105,-602,-883,-708,1000,419,702,-1000,-275,404,-1000,569,1000,921,-1000,-1000,575,253,-543,266,-108,-1000,-520,225,-1000,-999,-1000,-667,419,182,396,331,1,392,-1000,-778,-933,802,389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,-1000,-352,-873,-274,529,466,753,-828,270,983,-90,1000,-236,-241,-1000,-1000,-1000,-785,-441,-1000,-877,-466,-1000,-1000,447,1000,-988,-301,539,619,290,-1000,-676,507,-977,556,-519,-1000,-1000,-691,1000,-32,-52,-68,823,-489,80,1000,-1000,-999,-1000,1000,1000,-279,1000,1000,99,125,-1000,621,-544,1000,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-231,542,-387,-341,-1000,-36,340,-326,-220,-472,412,-450,519,203,202,-400,692,-268,-1000,-1000,-392,195,-259,-143,545,-1000,-262,463,-126,-871,-643,303,55,148,-492,-522,138,637,707,-492,366,-781,328,518,1000,1000,-384,-298,1000,762,15,645,400,-362,-1000,976,22,2,-172,159,1000,-303,-470,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{833,1000,-1000,933,-565,-846,-944,-351,524,1000,1000,-1000,1000,-215,-264,390,912,-796,230,-1000,958,1000,-1000,67,1000,-1000,93,1000,-107,-1000,-64,1000,950,728,-1000,-659,-719,120,502,-880,133,473,-752,1000,-1000,656,-446,501,1000,480,468,411,225,965,-710,562,146,-71,-1000,1000,-29,-1000,-1000,235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-51,542,-842,82,-952,89,586,-56,-325,-939,-504,-215,1000,-411,1000,-400,1000,-231,-1000,-1000,801,-35,486,-202,400,-1000,-199,1000,-595,-1000,114,-60,359,457,-371,-573,138,637,600,189,714,206,-316,-877,242,1000,-293,-326,1000,531,460,1000,-348,167,-684,-73,-77,-955,-416,-517,1000,-563,-1000,-72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-62,267,1,-919,-555,1000,45,-444,-151,162,32,1000,429,-619,538,-81,963,-860,452,566,-945,250,87,1000,-1000,-860,-645,1000,-101,-544,746,-646,835,95,402,-202,400,-449,860,344,-972,608,1000,-266,284,-213,-386,-255,759,512,-229,296,171,-390,-997,-272,-452,-121,484,984,179,185,-150,252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{1000,-852,-454,1000,1000,519,457,1000,1000,81,-588,679,-1000,973,1000,1000,-819,-1000,928,1000,1000,253,-1000,-31,985,93,779,-1000,-1000,1000,162,124,755,1000,12,1000,1000,-1000,-1000,-1000,-1000,-829,-1000,-1000,-746,626,1000,1000,-1000,-1000,-1000,1000,714,-295,206,380,-1000,-578,-1000,-412,-400,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,455,-38,-331,-322,247,-1000,-1000,-68,-646,891,140,35,1000,269,765,625,-548,-46,289,-70,43,-347,-1000,245,-86,411,-976,947,-766,68,-81,-1000,-431,83,589,-202,-789,-110,-24,561,872,605,1000,-219,238,-1000,1000,72,638,-592,234,-1000,-33,504,-129,-307,-114,236,530,-305,603,800,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{1000,1000,1000,-1000,-397,969,-861,-802,55,563,1000,759,-645,99,1000,1000,556,-1000,302,388,-1000,474,-271,-30,-1000,379,-464,710,230,-257,746,-11,56,299,-464,123,-1000,-398,1000,-472,-1000,531,1000,1000,-689,-804,-597,1000,527,536,-1000,-338,432,-755,-969,91,450,1000,1000,1000,-413,1000,1000,276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{994,755,-838,-753,285,559,-254,29,-2,-916,908,705,-314,880,561,730,766,215,372,540,-29,-433,211,-813,325,-363,592,460,-96,-364,290,552,-766,-826,882,264,-580,-624,-356,-803,692,-583,-499,701,861,873,-945,-836,242,-133,450,-196,-609,358,-25,-319,-784,346,-439,275,-984,178,11,-169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-444,393,108,1000,-1000,46,4,814,961,-983,-988,-277,-433,801,742,-1000,469,218,-1000,-1000,598,-602,-1000,1000,118,-54,-345,-562,932,-118,-231,-921,1000,735,434,-834,1000,221,642,495,405,-676,751,36,1000,577,570,-1000,648,34,1000,-240,1000,-237,-685,531,-74,124,273,-1000,1000,-238,524,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.TimeSeriesDataItem", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{434,-34,-454,887,-452,892,-468,47,-1000,457,174,-1000,452,491,804,-854,706,1000,-921,-115,646,-802,73,54,-299,-706,-1000,1000,-1000,-89,-1000,-241,-280,1000,755,375,1000,815,-1000,157,-577,-501,883,-641,564,1000,-1000,-850,702,-343,-471,-479,-45,346,293,1000,78,924,728,385,1000,614,-173,371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{-234,-608,-310,-568,261,-356,635,-94,-41,-735,-553,-638,299,610,-199,-344,-284,-612,309,-722,36,803,-149,27,-509,239,616,-398,-970,341,-304,-1000,369,-220,767,489,52,769,274,-454,234,305,318,-101,-514,677,-400,-706,333,297,216,409,-260,-782,908,54,-614,49,-282,359,-452,-644,-489,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{538,399,-78,273,230,-487,-302,619,129,98,582,-567,3,647,1000,-979,370,940,-425,259,-944,-1000,-538,72,-455,381,-603,738,241,551,-546,41,-956,752,920,435,549,1000,-753,-27,-1000,249,20,-292,46,808,-610,-93,111,-310,254,419,936,292,-54,1000,444,27,-320,186,573,336,924,525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("java.lang.String:YWFh", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{543,-122,-693,-1000,468,-1000,110,624,1000,-868,-510,-204,278,809,-771,-821,-1000,-82,933,-1000,-805,-601,-658,135,1000,-254,-118,347,644,234,50,603,-676,-270,787,1000,-863,765,-6,-806,560,541,-1000,393,-732,-1000,178,-633,94,68,-294,156,500,-1000,-1000,-107,-487,-400,-1000,705,220,-479,1000,666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{214,1000,-1000,295,827,-396,225,175,400,258,-86,-106,439,733,54,954,99,-917,1,121,923,124,911,926,80,338,1000,626,103,509,-200,598,939,19,-220,968,355,-749,-324,-584,-605,-176,-400,-737,167,-437,-1000,765,981,-924,812,-1000,-912,-496,675,-10,-217,-656,211,74,-413,23,-896,-840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("java.lang.String:bnVsbA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{-908,626,665,-722,157,-1000,855,-451,-230,-1000,-465,838,162,-55,914,628,1000,-545,315,1000,406,-430,466,587,430,904,1000,347,-104,447,-768,-603,-537,-133,-32,-785,-159,665,386,-482,-452,154,991,398,69,-17,-807,1000,411,376,529,485,-1000,-259,1000,-1000,-1000,-1000,1000,-1000,-1000,-751,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{-721,-371,196,-642,3,-8,-256,-641,210,-270,-808,-598,-316,1000,-336,-180,-190,770,-646,836,-1000,-957,207,-603,139,-400,-401,-872,149,1000,384,-378,-762,85,704,129,-676,1000,-499,448,-407,259,-991,669,-118,-494,563,-893,-447,1000,-1000,572,817,-51,240,477,407,-341,-110,935,-139,873,1000,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{-272,697,225,1000,-185,-875,-736,-458,-1000,787,-814,396,-130,239,-62,914,1000,138,-1000,-301,1000,-824,1000,-35,-1000,239,-236,1000,704,-288,-304,-378,-135,1000,885,646,1000,898,-876,240,273,356,1000,-1000,1000,-865,-335,-420,1000,113,-1000,-1000,-1000,-362,1000,-1000,-160,103,-907,-565,1000,-376,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{102,670,-701,-179,116,826,-361,43,657,-351,-165,-337,299,549,820,-670,345,954,-766,248,238,-887,258,27,526,-851,-299,589,-970,216,-843,-227,-792,399,767,708,415,568,-751,265,-264,-231,233,-101,393,-63,-324,-896,320,-248,-872,-695,462,-782,-12,556,-424,261,505,643,879,20,293,337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTQ=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-1000,1000,-1000,-1000,-1000,-871,-540,-1000,1000,1000,1000,-1000,-1000,1000,1000,-1000,1000,1000,1000,-622,734,-797,-955,1000,-48,1000,-209,-613,344,-696,-1000,710,1000,1000,-907,-1000,12,-1000,-1000,1000,-217,-16,1000,-280,1000,-929,1000,-1000,-981,-1000,574,619,-970,498,1000,1000,1000,-221,-1000,705,369,-604,495,458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{1000,-326,-1000,1000,-491,-502,-44,179,-230,-965,960,-223,955,-165,380,1000,1000,-936,361,-522,-271,-1000,-970,1000,-1000,1000,604,-482,306,-711,-872,1000,1000,1000,-898,-1000,-1000,-405,-1000,837,227,-184,782,-34,-206,1000,708,-1000,-507,-1000,-1000,792,442,469,8,766,-230,164,464,-1000,692,105,540,693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{619,-126,-851,1000,120,-478,-346,94,-1000,-812,507,-9,1000,-655,-300,1000,421,447,-571,363,-157,-1000,-806,923,-984,1000,114,-263,634,-609,528,-103,297,133,-1000,-1000,-434,-16,-638,-14,-113,-246,791,57,-600,1000,684,-1000,-538,-1000,-1000,358,22,195,147,-238,229,-805,-178,1000,1000,963,-949,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-840,-7,-511,-457,-224,549,18,-247,-667,-297,328,194,678,-598,-313,730,1000,281,9,296,124,-726,-59,937,324,1000,725,-614,-442,-1000,-410,453,1000,466,-966,-1000,159,735,-421,465,-443,121,863,564,-827,451,1000,-1000,-879,960,-1000,1000,-423,723,-24,-214,224,-1000,435,707,614,1000,165,408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTM=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-287,976,134,-739,-208,129,-653,568,-791,-398,975,-539,750,-339,-873,881,-939,244,472,999,-218,-444,-546,309,-150,406,446,-634,808,-280,130,-724,-850,-533,-592,11,159,849,-426,360,494,-7,80,-83,-148,647,-692,-122,-543,-611,-997,-376,803,-258,-336,79,145,-773,140,648,537,-109,-120,170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{155,327,92,-196,-752,-637,-224,448,-556,-556,544,457,1000,-623,473,680,202,-696,-274,928,-1000,-475,-138,-110,305,-400,141,-1000,-966,-590,-485,41,-61,-75,-858,-254,-402,-148,117,672,268,287,81,-140,232,190,-189,400,-230,-534,221,583,883,-48,187,984,-804,-741,840,19,-304,71,1000,339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTM=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{473,-457,744,696,719,270,453,1000,-676,-1000,-818,-165,1000,-716,-18,552,-320,-184,-1000,-17,-832,-7,851,-1000,-107,215,-234,27,-1000,-766,1000,727,-148,-707,-346,210,42,421,1000,-850,453,-1000,882,-564,-705,678,227,-587,186,721,-1000,477,-79,-872,220,-466,-230,-324,775,104,-713,1000,-583,-724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-854,902,-1000,1000,-923,-784,547,338,499,-943,780,524,277,-375,806,-578,1000,-374,991,-17,-832,-885,-156,-388,531,215,551,-777,334,-539,826,727,1000,923,-1000,-1000,-803,-1000,240,795,-612,-399,993,-257,1000,678,379,-173,584,-1000,62,967,1,-541,784,662,-230,-489,-234,-867,-788,-230,-1000,-799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-611,-136,167,1000,706,-316,487,926,-885,-943,-175,-532,1000,-1000,-1000,-578,1000,-730,-805,700,-832,826,737,-1000,-72,-677,-366,476,1000,-192,1000,-1000,-1000,-1000,223,1000,1000,1000,346,-621,481,-1000,-774,-163,-898,1000,-1000,737,584,-235,-993,-517,1000,-1000,-432,662,-917,-998,1000,445,-309,168,-1000,-353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-958,695,308,313,-427,-786,739,158,552,-663,-535,-716,-261,-400,-114,-486,-957,-620,180,-303,-69,77,880,-343,430,-453,-215,-250,455,-872,333,22,345,-649,-370,-104,-42,66,-270,94,444,-899,388,-949,-92,820,-438,489,-423,-18,-810,982,782,-850,438,393,-878,-431,503,-152,483,-82,334,-421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-76,747,-1000,-901,-107,-787,689,-302,194,807,935,-227,-1000,248,303,-270,1000,1000,911,-1000,841,-325,-1000,1000,255,909,445,602,298,14,-1000,641,1000,1000,0,-974,646,833,103,700,-842,-114,1000,-65,444,-766,532,-214,-333,-229,-224,-13,-559,543,-263,-477,1000,203,-503,1000,1000,366,165,-101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-650,1000,1000,400,1000,-256,-529,-317,-99,898,1000,-449,1000,-1000,-395,196,1000,-525,737,-1000,-272,1000,-1000,1000,736,-1000,1000,-1000,460,543,-1000,451,-1000,1000,947,1000,790,-1000,-1000,237,1000,1000,-742,-727,1000,-1000,1000,652,448,-74,-701,1000,993,-656,242,1000,791,1000,-1000,-531,-1000,-908,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-640,-28,319,-144,-703,-506,-337,779,184,-184,1000,707,381,882,-318,-783,1000,8,860,410,777,159,-1000,1000,291,-1000,94,-503,-1000,732,653,-1000,-1000,594,1000,1000,262,-871,734,1000,-159,-507,-1000,-709,1000,-1000,83,-1000,-1000,221,-1000,-106,-1000,-1000,751,521,49,-362,787,-742,-47,372,996,-352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-955,-283,636,-844,355,-1000,428,-478,584,-708,-715,1000,-1000,709,-903,163,-471,949,-33,-1000,-133,-620,-522,961,-1000,1000,-370,-1000,-379,409,124,49,1000,-40,1000,1000,-946,-119,438,-1000,-951,1000,-564,-1000,229,1000,-483,-729,1000,578,998,50,1000,-871,586,-1000,-89,850,-289,-1000,-390,442,-184,-391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-1000,1000,491,137,719,234,124,646,-167,-492,400,924,-808,-126,-513,149,12,-203,7,-979,219,475,-1000,422,-1000,114,203,-1000,539,1000,-533,-17,-1000,414,-54,400,-160,-912,-49,-1000,-747,1000,-801,-143,120,-400,206,-300,712,-106,815,-369,1000,-210,164,-935,274,847,-640,-408,-542,14,-579,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{1000,-1000,646,-384,73,619,309,97,-1000,-1000,-1000,-854,-1000,642,779,478,-1000,1000,1000,-292,903,-1000,1000,-1000,730,-177,-1000,-770,-1000,1000,1000,-22,1000,-48,799,-863,-1000,1000,284,976,-166,-1000,611,-162,-688,732,248,-1000,-813,1000,1000,-519,-483,-527,1000,-1000,1000,-18,1000,1000,896,1000,33,-426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("java.lang.Integer:OA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-911,502,636,-777,355,-1000,-1000,-497,-216,1000,1000,546,1000,81,1000,470,-283,447,813,-983,694,393,281,961,291,-511,779,-973,496,804,-1000,-384,-1000,545,1000,-19,-177,-185,-579,99,922,1000,-367,-665,427,-606,1000,-606,577,-6,-1000,1000,-481,-771,341,1000,-162,120,-773,-560,-758,-534,-500,-853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-575,706,1000,180,279,-463,648,-312,-1000,-529,131,-70,-263,-547,16,671,18,-265,1000,-502,198,562,-605,1000,1000,435,1000,-1000,898,-275,-1000,400,-1000,872,34,295,-24,-1000,-1000,-271,794,600,1000,-1000,617,-214,877,-313,1000,-168,-1000,525,1000,-1000,790,-572,1000,1000,-1000,-1000,-1000,-1000,-967,-759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-935,140,799,-1000,56,183,-297,-436,224,-111,1000,575,-135,882,-505,-730,1000,704,132,-697,777,626,-1000,446,190,-965,395,-361,687,-194,-809,-1000,-1000,286,-618,747,363,-1000,-101,1000,-279,1000,-1000,400,951,-1000,83,-1000,-1000,-241,-834,674,-867,-920,525,516,49,1000,-1000,-6,-120,372,996,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{1000,-530,936,-344,392,-6,-204,-256,-845,553,-521,382,-958,758,-524,287,785,-12,-273,400,465,-830,-654,232,1000,408,-230,-975,161,-174,-787,-32,55,1000,-328,-653,647,-344,-817,618,-531,-614,543,-872,-392,392,-391,-1000,-952,514,-814,-440,562,-682,1000,-276,935,1000,138,-253,-571,-60,202,13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{674,191,-315,400,1000,640,-104,940,904,898,1000,-996,-283,-107,-1000,-742,793,54,-784,180,759,907,-1000,-862,496,-45,1000,-1000,-267,-496,-140,-481,-527,139,-79,1000,1000,466,-414,-188,-161,156,429,780,-142,-1000,-60,652,297,475,238,-411,700,830,-1000,602,750,417,-702,-303,-730,-121,188,-614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-776,760,-879,-905,835,-973,72,-327,967,-358,241,335,994,-456,119,419,-283,-818,-627,-24,-382,302,-196,698,224,796,-609,592,496,-417,-747,143,-407,-184,-800,-233,72,-161,-301,-937,-326,277,528,45,-788,371,-806,902,733,-704,-751,99,151,517,61,643,-162,-498,-708,-785,-715,-930,-620,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{-550,-1000,-1000,-950,682,-182,-414,695,920,800,-75,105,450,-726,-1000,-720,-948,547,1000,1000,-934,-1000,-827,-208,586,820,269,-782,531,391,1000,-526,1000,-706,-684,1000,-582,-1000,-530,-806,-703,-478,-532,-31,920,-114,400,-531,-1000,-832,-1000,592,-840,-233,-1000,1000,642,-203,-177,-1000,520,459,-365,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{-1000,51,351,1000,340,-984,-1000,744,-621,-1000,-1000,837,168,1000,-829,380,-97,222,281,1000,-1000,-636,-594,-221,-1000,-1000,-197,98,1000,-1000,353,-1000,-88,1000,483,528,100,-1000,-331,112,-1000,-1000,-1000,-1000,1000,1000,-650,101,-85,564,-174,-1000,-356,114,-1000,-48,562,882,715,-902,-580,-234,426,-421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{918,1000,514,-456,-63,-892,1000,-48,-452,196,-47,599,-504,-241,421,-982,271,-449,-198,-1000,-24,936,-396,216,-698,1000,566,396,-264,-761,-715,-293,1000,-7,492,875,830,283,151,-86,-236,156,-556,-631,740,-73,-1000,186,-306,-1000,99,674,-404,-342,-240,-778,160,-1000,173,986,392,-477,1000,324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{136,-736,39,-179,1000,904,-885,-980,832,978,167,-124,735,-1000,-1000,205,-1000,-12,700,1000,-635,590,-706,-744,1000,674,-561,-923,-227,966,785,-1000,1000,-534,-1000,431,467,533,-384,-436,-77,1000,-112,1000,-663,379,174,419,-261,-503,-1000,-1000,730,1000,556,128,854,-224,354,-1000,-136,-1000,-784,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{-1000,-100,-435,-562,-494,277,-908,60,1000,-170,-1000,-629,1000,-1000,-1000,438,-1000,334,1000,1000,-807,-82,1000,-772,536,-879,12,-987,-435,-360,1000,-1000,672,479,-342,216,-1000,602,334,-998,-255,-113,-645,379,1000,198,-970,479,830,1000,-1000,470,-228,98,-1000,586,303,-52,-888,-1000,1000,1000,-135,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{-150,51,-387,-400,1000,-977,400,917,-237,-1000,-1000,844,-1000,-876,-269,-400,239,386,113,-1000,-222,-132,746,-526,167,-429,-773,-914,802,383,-1000,523,404,751,1000,451,-214,-1000,693,-752,531,-1000,1000,-310,453,-354,-177,181,92,341,-343,400,-354,-602,-255,331,-1000,578,-494,-986,834,1000,-696,-203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{417,-638,-697,400,310,-53,524,754,-215,-1000,-1000,829,-625,507,1000,493,-1000,553,-1000,-777,-340,-337,-113,1000,-1000,71,868,542,-227,-425,-523,-419,40,255,1000,22,787,-195,1000,-631,195,-1000,873,1000,216,379,1000,1000,625,1000,780,-1000,-862,1000,-1000,-652,854,1000,1000,450,35,939,-237,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{1000,376,759,342,278,-27,-684,-579,-1000,-1000,-759,219,74,200,-213,941,1000,352,-158,-719,99,1000,519,353,255,-1000,-1000,186,303,-404,-516,-52,-1000,448,580,-686,983,190,1000,-134,222,-232,574,258,-1000,687,824,1000,1000,1000,641,-1000,1000,992,1000,-547,-1000,1000,-174,668,-466,-238,-683,-600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-276,-932,705,1000,1000,684,-856,1000,664,-263,419,925,277,-27,-474,279,511,-1000,-1000,427,-601,891,-287,-876,841,-1000,-124,1000,-360,-1000,-984,1000,-364,1000,206,-511,-1000,-1000,-1000,1000,1000,242,-954,640,-221,514,-178,-433,17,-10,-204,-1000,-1000,-1000,-1000,-907,1000,-1000,1000,963,600,-1000,13,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-3,1000,-76,955,-232,-592,-81,581,-794,581,-785,-604,628,814,1000,-191,116,1000,-83,1000,-430,549,1000,-973,643,613,-276,645,678,567,1000,-43,-1000,203,44,228,1000,362,1000,-1000,-655,562,604,-742,529,-941,538,-1000,-1000,1000,-948,1000,564,-1000,708,537,-573,139,873,899,-87,-624,420,-863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-1000,1000,502,1000,-566,-181,-1000,482,-1000,-1000,-37,-494,36,-681,708,1000,712,1000,145,462,297,1000,-478,-1000,-824,-1000,-416,848,131,-1000,1000,346,-638,1000,958,-52,905,11,1000,-1000,897,-1000,1000,601,-558,-898,686,53,-545,18,-1000,1000,-272,-209,274,-674,-385,527,1000,827,-822,-554,37,-734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-863,1000,-597,206,-718,673,-740,-30,-1000,-318,-1000,-648,1000,-1000,246,801,122,-440,240,-150,938,1000,111,-1000,-723,-988,1000,425,506,-363,941,-206,-828,981,-826,892,812,-648,94,209,-226,-33,535,851,-1000,-350,927,189,-792,-590,-278,-584,720,215,669,-139,-563,1000,617,53,23,-256,536,-348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-36,59,-557,-469,-232,-617,-783,14,-91,804,-312,69,-667,786,-404,-761,-605,713,-83,1000,-376,555,6,-919,319,710,-420,645,359,330,493,-242,-610,-274,-285,-287,340,767,754,47,-655,425,604,-1000,529,116,354,-90,-271,-174,-270,1000,564,-979,453,103,41,-190,-577,253,782,-189,372,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{380,-467,-765,763,706,-33,-832,557,-596,816,-401,1000,-1000,1000,-267,-833,-233,651,-107,1000,-1000,-1000,624,-591,479,-545,-1000,393,551,-234,143,-495,-727,-672,404,-1000,-993,1000,-727,19,-758,-1000,-502,-1000,461,483,548,-150,-547,-491,-649,400,-1000,-1000,257,-368,1000,-1000,397,1000,616,-80,167,-997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-1000,1000,-172,-1000,-1000,418,-278,-1000,-982,947,871,-1000,1000,-1000,-463,1000,-962,-667,1000,183,1000,1000,350,-1000,-1000,37,1000,974,1000,1000,574,-696,-1000,432,244,1000,1000,-248,1000,-1000,-250,1000,1000,1000,-1000,-674,1000,62,-1000,-1000,19,-544,1000,456,1000,1000,-708,1000,-218,-1000,671,-56,1000,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{581,903,24,-567,-1000,953,-265,-833,-832,400,186,790,991,-904,-1000,-797,856,-1000,-677,281,687,310,-226,-368,669,-98,1000,9,96,32,-1000,83,-618,337,-538,935,-751,34,-635,1000,1000,742,-790,713,-492,1000,-426,-413,-865,-1000,1000,-1000,789,960,-492,623,-292,-158,-223,-999,1000,-503,156,126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-880,648,-1000,-927,-954,324,-413,-991,-1000,1000,-1000,666,-363,-160,-266,-830,-592,-246,253,1000,160,-844,1000,-943,-441,771,6,67,1000,1000,740,-1000,-1000,-278,903,-710,-927,1000,-288,-803,-1000,262,-180,-1000,-208,900,1000,304,-1000,571,-432,-434,-256,-1000,1000,501,800,73,41,-123,690,44,1000,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-755,59,419,931,17,-1000,-1000,1000,664,-596,361,278,-1000,-27,-474,639,594,1000,121,1000,-868,555,-710,-919,841,-192,-124,1000,-360,-1000,1000,1000,-357,389,-314,-810,560,470,754,-364,673,-975,1000,-713,444,-1000,134,-676,-406,-10,-947,1000,-226,-979,-1000,-1000,1000,-521,1000,1000,-493,-686,-303,-612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{1000,1000,-36,-28,1000,512,-621,830,1000,-129,-339,1000,-479,435,-943,-1000,-1000,-1000,-1000,619,-376,1000,-66,-319,1000,-222,-500,-415,-112,330,-1000,1000,-610,941,-872,-550,-735,1000,-716,1000,-236,-1000,-846,-112,1000,874,-594,-818,486,-884,-270,-1000,564,182,-1000,653,1000,-1000,974,253,1000,-1000,745,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{-167,-577,265,45,-190,-139,-1000,-685,-121,150,775,-62,-701,246,29,1,-267,-1000,-982,857,6,39,-757,330,-390,-1000,-766,-498,-385,-687,-1000,-238,390,-137,-220,-1000,844,-31,1000,29,-62,-862,1000,-978,650,1000,-323,1000,246,698,-909,-1000,5,-458,-560,323,1000,-1000,503,328,50,-1000,-503,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{-761,-577,1000,-1000,-1000,-1000,-1000,-572,508,-89,679,126,-918,-25,-307,953,282,99,-982,236,-827,343,-496,811,-390,-1000,-953,182,771,45,-1000,-1000,-557,-212,-268,1000,1000,379,537,29,229,103,800,-619,1000,958,-1000,1000,463,1000,-174,-776,-484,108,-518,780,618,-1000,678,-922,499,-1000,-503,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{541,1000,-1000,-181,-107,-917,-1000,140,684,200,-322,-529,1000,-117,-398,-8,-1000,1000,192,-400,-400,-403,-948,-548,264,-1000,171,-1000,-469,-75,957,180,143,-185,1000,196,-1000,-370,-726,1000,369,-177,-591,1000,-1000,620,739,-543,383,-1000,1000,964,-400,-97,309,-225,-1000,517,139,140,-110,1000,866,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{1000,297,-779,1000,1000,421,664,461,-249,1000,-571,272,-852,-1000,1000,-580,1000,-1000,-153,1000,1000,-78,1000,-537,-321,1000,659,-177,-751,-993,486,-97,-151,-1000,-950,484,146,-263,1000,-789,-1000,943,108,-1000,771,610,1000,327,1000,29,-1000,-755,1000,369,231,1000,864,-749,472,-393,1000,-1000,-20,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{1000,-1000,-62,1000,598,-197,-75,-90,728,426,-161,1000,-1000,-713,788,-405,1000,-586,-1000,1000,1000,-472,1000,297,-1000,888,-358,-248,50,-993,-293,-568,-291,1000,-902,-495,146,149,1000,-1000,-821,652,556,-1000,1000,1000,970,1000,1000,492,-1000,-1000,1000,-12,-121,1000,1000,-749,351,-1000,1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{460,-987,299,-12,49,-378,154,355,761,-515,-326,61,-141,-1000,-677,-191,231,-783,-135,1000,216,-52,376,473,-68,568,-326,-240,-81,379,889,-103,-321,635,-400,-882,-603,373,107,-101,-589,400,840,83,151,545,597,641,-68,-149,-63,-578,484,400,-240,81,353,-103,-27,25,1000,-601,-926,-824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{959,790,-917,493,-169,733,138,344,-341,-328,-246,273,518,-303,-244,-416,473,589,522,61,748,391,341,700,340,141,613,-813,-713,-402,868,514,422,516,604,377,-794,-612,-109,885,32,400,-181,486,-759,-177,987,-1000,26,-963,-562,187,381,976,719,354,303,-123,69,194,561,947,130,234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{789,225,-799,-851,-816,-1000,-722,-26,152,-194,604,-109,-104,-25,-1000,311,1000,348,849,236,-926,1000,-1000,560,-390,-443,-1000,-469,808,476,1000,-1000,-354,-693,-198,393,154,-975,537,1000,1000,1000,495,428,456,-988,-376,-597,298,40,1000,1000,-970,1000,337,-559,-1000,-140,85,-462,-560,1000,873,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{-784,743,62,1000,185,-584,-1000,-809,477,-498,-163,-896,-256,814,-425,-124,-1000,680,-1000,-128,-1000,-867,619,1000,173,-201,-671,418,411,-1000,293,-201,1000,-105,1000,-881,-564,453,-490,-726,528,-835,1000,1000,211,-574,-292,-235,-174,-265,344,-693,-1000,-1000,-70,-1000,868,211,-789,-910,-615,122,-1000,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{587,-200,732,188,295,-531,72,702,647,862,-813,-2,-581,-968,-515,892,747,-458,293,-370,882,-305,-324,19,293,-373,922,85,458,-496,372,-442,-653,-198,196,748,340,-31,-240,228,-875,638,-706,147,-19,11,-272,-27,770,32,755,-16,961,516,-556,940,-961,-440,81,11,782,214,276,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{766,706,732,-908,907,233,-444,-93,-27,-603,984,-1000,-130,1000,1000,765,-42,527,1000,-1000,-1000,-433,-975,-259,847,-429,485,421,458,761,212,-941,166,-523,-1000,42,-442,-748,398,228,-225,638,424,592,87,-591,-1000,-206,770,-203,-249,-16,-128,635,1000,-1000,-1000,-1000,302,-958,782,1000,276,-198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{40,855,16,566,-57,-758,539,1000,729,1000,-118,-1000,340,288,-157,-888,-142,-585,115,654,-225,-737,408,-66,-57,609,380,231,297,-561,-287,-406,474,-883,-486,-290,236,683,172,-628,623,-659,1000,-315,-211,997,96,-497,-895,464,759,852,1000,-736,498,-50,837,-233,1000,312,49,-656,953,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{58,-241,752,-1000,-23,-1000,300,50,64,1000,501,-764,-141,-373,-682,369,187,-513,-89,-481,30,1000,1000,-415,-335,934,1000,-720,55,-753,191,637,316,-635,-145,1000,-882,465,-73,-648,-288,-672,624,828,-929,773,31,-543,-653,-443,1000,1000,-185,-793,242,466,-174,-600,119,599,638,-364,-25,-191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-1000,-683,1000,-872,747,233,758,1000,-921,1000,-118,-330,340,-1000,-110,714,1000,-1000,1000,1000,-225,-105,1000,1000,-968,1000,937,355,-455,-153,-1000,482,-802,-1000,-1000,-320,-1000,-1000,-171,417,-1000,-591,1000,783,33,1000,375,-497,-1000,-1000,-107,1000,598,-1000,1000,905,-119,-1000,1000,-626,49,1000,882,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-545,24,-502,332,159,-42,-774,-195,-729,-274,-804,893,936,795,-855,471,-1000,-432,681,-1000,173,1000,454,-1000,125,-393,1000,1000,-693,-1000,-421,728,535,11,733,1000,1000,1000,777,-1000,311,-1000,226,824,570,981,1000,-474,1000,901,-1000,-107,-1000,1000,-1000,-38,-193,-651,-1000,-718,972,-1000,-1000,-652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-518,647,-903,-230,-1000,-829,414,460,359,351,267,410,604,1000,1000,238,-1000,-193,345,-784,-802,902,543,-1000,14,-326,1000,372,-385,-978,-6,-183,-408,61,-380,710,534,893,929,-1000,1000,-572,1000,1000,1000,-272,1000,-1000,593,1000,9,-652,-1000,-353,-1000,159,1000,-563,-1000,-45,963,-1000,-661,284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{317,672,335,114,-287,-737,539,361,110,284,58,-556,-78,111,679,-19,586,-518,103,-168,23,162,557,-234,-51,609,336,-676,-190,-807,159,204,-374,-446,-486,256,109,245,648,-934,259,-841,665,-86,126,184,-146,-944,-218,261,759,496,234,-504,498,-79,286,-543,338,-62,819,-678,635,-122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-130,475,312,-864,9,-124,453,-1000,778,-253,90,161,382,-347,28,-131,-502,301,-1000,-1000,-94,1000,-112,-625,-847,-628,946,-1000,190,-628,951,-92,838,267,133,954,412,416,164,-125,940,-705,-833,9,-541,-2,-317,-733,-1000,745,501,1000,-801,273,-533,562,719,-533,-670,-229,-576,-307,-323,-484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{1000,1000,-694,1000,-1000,-37,-58,293,492,-794,-1000,242,221,323,363,-1000,91,-1000,295,-131,180,-1000,-392,-753,-1000,285,-665,-160,82,-275,1000,-286,66,1000,291,631,1000,1000,1000,-1000,-345,-993,1000,-503,859,-728,1000,-640,1000,266,-820,-1000,-751,-530,-1000,426,296,-825,724,-1000,-503,-1000,-907,-610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-692,-1000,846,-1000,221,-511,1000,-265,-1000,1000,-179,-565,-1000,-1000,686,565,-633,16,359,-1000,1000,-798,1000,1000,424,1000,-1000,-1000,-1000,-191,-1000,1000,921,203,-923,1000,-1000,-655,-202,966,-1000,-855,219,487,-1000,98,-325,-62,-490,-794,1000,1000,-1000,-817,1000,-248,-170,-911,-588,-38,1000,1000,-180,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{166,135,556,290,819,-441,539,40,-566,1000,4,-1000,-1000,-117,-930,-393,1000,-367,115,479,1000,43,-118,1000,244,963,333,231,-847,-1000,-976,227,-176,-823,-486,-304,-303,-882,-120,227,-116,-969,362,-344,-807,1000,-1000,-293,-1000,-302,759,1000,1000,-1000,1000,-262,11,-1000,714,38,-219,1000,351,-707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-102,304,531,734,354,-448,-159,282,-351,-45,-273,-556,385,391,397,-159,972,-499,-138,1000,-254,-592,-57,1000,328,-252,-633,-514,-89,-860,-521,618,-245,-558,542,-1000,307,77,705,-814,164,-952,381,-956,334,-427,-718,-1000,-760,-429,154,-484,-696,326,-35,128,-201,-683,-317,-104,-996,-1000,-118,-480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-484,1000,449,431,11,484,-676,163,793,316,-9,27,-533,-151,376,1000,-671,-1000,-730,-1000,-844,1000,-80,-666,651,675,-316,756,228,731,-70,1000,-95,-1000,665,-60,1000,-186,35,1000,-318,-1000,-531,-35,1000,-878,-28,1000,-27,792,-1000,516,165,22,-563,-1000,-748,1000,287,-191,-1000,-234,585,234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-150,-152,-182,346,567,-491,410,-597,1000,273,939,642,-908,-47,-1000,-863,-313,386,1000,-954,1000,-396,228,-956,573,-1000,-840,-638,-682,565,-167,957,-304,298,463,-1000,184,99,-231,-786,-628,448,-1000,-1000,955,-626,-392,-524,-508,-816,999,-421,946,1000,-830,-1000,-959,755,779,1000,795,-207,709,837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{317,445,102,871,228,3,134,282,1000,-129,192,506,-110,-234,165,882,-672,-132,305,-1000,-309,-396,672,-1000,439,169,-510,1000,425,850,-740,563,-778,202,1000,-725,688,-419,-43,928,90,448,-1000,-1000,1000,-626,-1000,1000,-203,1000,152,41,719,356,322,-1000,-84,1000,766,1000,-1000,-691,733,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-383,1000,1000,712,-138,798,-802,525,793,536,-1000,278,-416,-196,600,1000,-611,-1000,513,-653,-844,1000,461,323,592,1000,602,841,1000,518,17,220,779,-1000,1000,375,1000,-155,-503,1000,-578,400,362,-35,942,207,35,1000,172,1000,-999,893,-468,-503,307,-902,-350,1000,-958,-968,-856,-125,-296,-380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-150,-4,852,-1000,684,-426,740,-265,-670,130,-428,642,825,231,-162,677,-141,73,-744,374,-56,98,-214,-769,736,282,-237,404,199,-802,-112,-675,102,-83,-72,910,905,214,-424,-786,-95,1000,753,-149,-199,-142,303,-524,128,306,-382,-421,-563,-833,-830,581,961,154,-174,609,163,-207,-847,-74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("java.lang.String:MHgzY2E=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{189,-620,751,431,347,-816,-197,-518,442,920,998,128,-50,-390,-948,855,-970,880,-43,944,-19,-970,-463,-132,988,279,-691,767,768,-622,-7,-412,-125,-606,718,-47,-762,334,548,-359,-735,528,262,-312,-705,-813,527,-468,-438,-589,-437,-619,-71,47,-816,36,-402,296,721,775,-198,701,-267,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-1000,414,1000,-472,566,66,552,-906,-194,1000,895,-361,196,28,-879,-288,-522,-181,1000,-369,493,1000,94,-1000,1000,370,-100,-1000,1000,-1000,-701,1000,1000,-1000,1000,311,760,271,682,-33,-746,721,86,-739,762,-730,536,-641,-907,1000,-1000,-957,290,278,-1000,321,822,1000,-542,-49,1000,979,95,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-170,1000,1000,961,521,88,279,-91,-421,311,-925,1000,212,108,624,-762,-38,-231,187,-21,444,400,1000,875,-466,-585,751,169,1000,-118,270,400,1000,-179,630,-565,329,-584,-644,-217,140,541,-1000,-738,310,1000,-698,-385,209,400,1000,809,-813,-289,660,-328,-1000,347,-1000,-223,-305,552,-685,272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{968,633,-842,58,781,767,-656,157,-302,477,889,431,-1000,381,13,-823,-1000,-388,528,891,-376,-96,-360,-630,676,699,-112,-333,-1000,1000,-477,516,802,374,-495,-168,398,392,-1000,-519,-274,-769,-210,-773,-368,31,827,-1000,-575,871,-459,-347,265,302,-1000,62,931,521,447,-62,369,854,426,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-249,907,213,378,-563,456,735,475,833,-15,279,613,916,460,2,-1000,15,1000,-1000,-473,490,-361,-518,-113,192,-44,-1000,-342,466,-647,-1000,-41,-1000,-211,592,-998,1000,35,590,-157,-1000,-1000,-790,-201,-1000,402,1000,-196,215,20,1000,-461,326,818,-973,-631,239,-909,385,145,-484,-1000,338,123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-848,1000,-754,-802,-201,-171,74,1000,-374,-827,-704,1000,-439,735,38,-950,1000,-1000,303,-189,-261,10,-1000,1000,759,-1000,389,-647,1000,-332,-577,1000,882,338,-127,1000,-1000,318,284,37,488,799,422,103,1000,-1000,-40,269,465,-161,-608,-65,761,-517,779,-939,28,1000,1000,-216,-1000,471,777,926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-373,-1000,872,1000,-1000,353,268,1000,-199,-152,159,-948,-540,-11,-917,-1000,-987,1000,150,191,553,1000,328,-379,-705,-316,-1000,-113,1000,482,-862,-37,-551,942,-704,-1000,1000,-551,745,-960,-992,-994,-637,-448,-1000,1000,746,76,288,1000,992,1000,-595,1000,-1000,-461,1000,-211,-1000,-441,-1000,429,-1000,-228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{155,564,414,-594,-319,-626,923,156,-1000,-345,-574,-133,-573,939,-192,1000,-175,1000,-1000,586,1000,143,-161,-372,672,-23,768,1000,871,-820,346,787,-533,-350,1000,-129,-622,1000,-1000,530,-1000,-733,-474,-619,-167,-174,-571,-528,398,-732,419,-1000,-31,-1000,1000,-700,129,777,-1000,-1000,-596,-882,1000,-241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-752,1000,-1000,-1000,1000,-11,-1000,-636,-1000,37,-779,1000,-885,-220,-721,901,596,-1000,740,-170,33,-584,-709,1000,1000,-45,1000,1000,296,-839,181,1000,1000,-519,-1000,656,-1000,-1000,1000,312,285,1000,1000,509,1000,-1000,-866,94,-16,-491,-1000,1000,1000,323,1000,1000,1000,804,1000,-660,-975,1000,825,178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-1000,250,941,-323,161,-957,197,-61,-573,-935,658,546,-837,-179,1000,-430,-131,415,-804,-424,-400,-782,-473,-208,-610,961,-606,1000,93,1000,159,1000,-1000,1000,1000,-895,-76,383,206,269,-1000,-898,241,-627,-636,648,-1000,-510,1000,-41,89,-1000,1000,-86,-1000,-939,131,23,940,162,-980,-1000,-400,-503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{333,458,466,-354,-430,653,684,794,-611,86,378,429,-509,420,-584,-662,23,-127,-117,542,626,1000,-263,-308,-449,-1000,-119,172,304,-43,-467,-148,-234,268,114,-709,-167,319,12,-626,362,173,975,-1000,-126,400,-34,373,422,819,247,-81,144,418,146,-1000,-149,955,-884,316,-1000,-166,323,692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-1000,485,-641,-654,-167,-795,-391,178,194,-488,-293,535,-48,104,351,-545,1000,715,157,-317,-700,260,-908,1000,780,-896,582,-18,238,-1000,-112,177,859,-459,-1000,1000,-1000,-905,886,-839,-411,790,1000,708,1000,-723,-308,-872,221,269,-367,956,-5,-22,418,984,741,131,612,86,330,87,-75,687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-620,-1000,824,1000,-916,-542,477,928,577,-333,108,-853,72,16,1000,-445,498,589,-1000,-232,-780,1000,-1000,-313,-692,-1000,-741,-95,-1000,-1000,-747,-93,-193,932,-285,-1000,1000,-57,722,-1000,-604,-506,122,-890,-301,663,-75,330,501,1000,1000,704,142,418,-290,308,-1000,-142,-1000,732,648,-96,-1000,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{1000,-655,886,551,23,353,-386,-390,-28,630,1000,175,170,-418,36,1000,-517,152,186,-109,-1000,-262,1000,1000,-763,-774,-600,440,-582,-389,1000,-771,-673,190,-193,-1000,-28,-1000,1000,-1000,-578,-91,350,-1000,-485,-212,-510,-339,252,192,506,241,493,1000,13,1000,-279,-777,691,772,94,934,-1000,508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{777,1000,1000,1000,843,414,242,217,303,-388,352,549,-882,27,-962,-9,1000,1000,-1000,-1000,-1000,350,1000,341,1000,1000,-1000,1000,502,-220,373,-390,833,-91,552,1000,1000,867,-647,-641,702,180,-373,-1000,-114,1000,1000,-212,-94,-104,138,-149,656,-1000,-1000,1000,1000,1000,22,-1000,415,506,1000,913}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-1000,420,-1000,-97,1000,823,1000,1000,8,254,1000,195,247,-1000,-308,1000,-327,-305,194,-893,23,194,1000,949,-1000,457,-1000,-989,767,-1000,58,-1000,1000,1000,-1000,1000,1000,1000,1000,-1000,-122,-1000,446,-95,-399,1000,1000,11,-625,700,-114,920,121,-1000,-691,340,1000,1000,-713,55,-256,1000,547,-627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-249,-376,-882,-255,487,864,608,316,-184,-43,279,-483,275,-829,-462,818,387,310,181,-251,-580,-1000,41,393,194,610,-978,101,691,-725,25,198,-17,-788,-869,1000,487,1000,219,-176,-44,814,764,-660,-897,-69,86,991,328,38,-824,-958,570,494,-789,287,-900,43,-464,-1000,-733,-588,192,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-960,-297,-1000,983,269,628,637,668,248,507,249,246,-695,-638,-877,909,-327,751,241,-800,-948,-420,420,492,-449,185,-805,27,787,-867,174,-913,393,753,-1000,1000,1000,549,840,-507,584,-1000,-535,-533,-97,960,519,-631,-271,-104,-581,248,36,-973,-552,297,440,622,721,-544,911,506,1000,-153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-995,1000,155,-573,965,874,-5,626,925,288,270,254,655,-933,-131,876,-327,423,-525,-185,382,67,186,1000,-709,-20,-640,384,490,455,91,-905,380,-76,-274,-128,445,241,-240,-524,-1000,-558,483,128,-894,-294,-429,712,-1000,53,267,610,-457,-487,-1000,-35,211,98,821,-234,-103,644,331,-163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-58,-1000,-1000,-1000,-641,-682,287,409,625,-72,-1000,-506,986,-725,443,973,-1000,-1000,1000,1000,1000,882,-1000,490,-785,-196,343,-994,918,1000,-912,299,-736,-234,-611,35,-1000,-680,-1000,1000,395,-648,433,1000,62,-1000,-1000,1000,-225,532,-1000,-643,-1000,1000,1000,-922,1000,-1000,-341,-378,-794,-1000,-777,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-960,1000,-816,45,-254,629,164,213,220,-657,-402,508,-785,343,-749,-266,777,542,-638,206,1000,1000,-492,-572,60,-1000,-741,970,1000,757,-533,-1000,1000,-1000,-549,656,900,1000,-1000,-463,522,-1000,-1000,116,-1000,-159,-772,-456,1000,-918,218,-570,16,-29,-741,75,1000,454,751,-88,756,-379,-236,511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{1000,1000,1000,-693,1000,-807,267,78,463,-1000,-355,1000,-83,-120,523,-222,-400,-403,-577,150,402,5,47,678,-268,-450,103,-76,195,1000,46,986,-155,618,1000,-400,247,104,-1000,250,-690,1000,904,616,286,295,-400,781,-728,-126,-151,824,-707,-181,93,772,1000,435,-146,-260,-274,-68,140,-425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{168,1000,1000,301,509,383,678,-436,1000,-363,-1000,-1000,-473,-1000,-170,-861,-887,-1000,-480,-1000,336,-1000,1000,-1000,-501,-982,-1000,-818,-579,1000,1000,-330,-189,694,698,235,-518,-1000,1000,-109,-1000,617,-818,1000,814,-1000,-1000,-1000,1000,-598,-676,-858,312,995,-1000,1000,1000,1000,1000,984,296,-395,758,-910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-518,-363,-493,-1000,-93,599,-611,-383,-1000,-1000,-472,1000,1000,1000,1000,537,-572,1000,422,414,709,722,-1000,582,898,217,398,362,-309,-770,-1000,-632,1000,1000,-537,-732,-1000,-267,-1000,928,1000,-686,69,915,486,203,183,-551,-1000,641,-1000,1000,-592,-478,1000,-1000,513,299,533,-92,239,902,-950,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-402,-354,-689,1000,187,-190,-1000,323,-426,1000,373,-533,-316,-400,-860,-732,969,-704,-500,833,-806,354,239,679,-809,-329,472,-19,1000,-931,-819,-472,-1000,-636,437,-1000,1000,267,-11,-120,-640,-1000,420,-1000,-812,-98,-84,1000,-912,247,77,-1000,361,-1000,1000,-616,-478,-387,-685,-745,789,-1000,-529,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{949,693,420,-869,-497,320,514,-969,-262,-850,468,-1000,33,-1000,-544,-291,509,100,203,-510,130,-1000,-1000,65,136,-1000,-552,-876,844,611,435,-788,478,-49,728,81,-1000,-1000,1000,706,-967,1000,272,141,888,176,-493,-110,1000,760,411,416,-625,-188,-337,-49,860,305,1000,1000,-721,814,137,-966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-667,673,10,972,386,-778,-61,868,1000,-1000,-1000,-285,939,70,-273,-963,1000,152,-693,-844,-915,252,972,20,-1000,-1000,-257,59,-1000,75,-611,-394,-1000,202,707,-1000,1000,-1000,660,-1000,628,-1000,992,-1000,414,-1000,-419,451,-1000,599,-1000,-535,616,867,1000,155,591,-531,435,-1000,878,-572,-433,268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-312,660,50,562,-199,524,957,0,376,0,233,-1000,-834,951,1000,-756,996,547,-1000,61,-841,-747,-1000,-531,269,-338,1000,-957,0,-307,-293,-1000,-831,763,-28,0,0,-763,569,0,558,0,1000,-164,-140,-394,754,356,545,1000,-788,0,447,-1000,-140,-373,-795,-60,-539,0,1000,-549,325,-841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{371,1000,-25,116,636,-856,1000,129,1000,-1000,-561,-1000,1000,-1000,-157,-1000,1000,-309,-449,-1000,-149,20,1000,-276,-501,-1000,-1000,-939,-1000,798,729,-303,-177,623,639,-1000,-351,-1000,1000,473,-716,119,-676,1000,-830,-1000,-794,-877,-695,794,-917,-178,-17,418,1000,785,921,-243,1000,-1000,479,-188,706,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-1000,-1000,-849,992,636,-987,-1000,-74,-1000,1000,-15,469,-817,-19,-193,-364,-1000,-200,-905,814,-996,765,171,176,-948,-385,522,432,1000,-608,-805,-630,-400,-702,243,-892,959,827,-1000,472,-808,-1000,-558,-1000,-882,611,-700,443,-1000,-657,-232,-952,912,-1000,1000,-1000,-615,-391,-1000,-676,1000,-638,84,589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-158,832,976,480,1000,-576,1000,-605,745,607,-832,-1000,-828,-950,-1000,-673,-908,-843,-647,-1000,264,-1000,1000,-1000,-1000,-1000,329,-309,-488,591,1000,-323,-189,571,728,-597,183,-757,735,-219,-1000,-555,-1000,1000,-242,-1000,-1000,-937,1000,-1000,836,-1000,355,190,-166,555,678,602,330,698,-34,-766,-108,-423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-863,271,-351,414,1000,-1000,-502,786,-156,1000,-153,325,-1000,982,-134,-8,-832,210,-1000,557,-300,-639,47,-680,-1000,-861,1000,140,-2,-1000,-652,-675,-151,432,656,-947,591,-759,-772,-1000,249,-1000,-256,-594,-386,-322,201,-354,131,-831,572,-941,754,-509,907,-139,-1000,101,-1000,347,701,-1000,-825,-36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-956,280,-1000,-1000,1000,624,-1000,307,-1000,1000,816,-410,-615,-456,1000,-696,-1000,1000,689,-332,-918,1000,1000,307,-654,333,14,325,-579,669,739,1000,-345,-899,-1000,-1000,-1000,-43,-1000,1000,468,-501,-701,581,888,-1000,-212,260,-153,1000,1000,439,-742,-714,1000,-741,-1000,722,670,-1000,431,160,1000,-444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{1000,7,-1000,608,-141,-1000,-1000,1000,-241,555,-785,-293,828,-543,-261,794,728,326,-215,-142,1000,-1000,590,361,1000,1000,-289,-1000,614,-1000,-653,-1000,1000,89,1000,1000,1000,1000,735,62,165,-299,-1000,-465,243,944,-1000,872,-956,-1000,532,-686,-1000,743,-200,919,867,49,-836,222,-1000,190,-713,140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-1000,599,-57,93,-762,-942,400,-468,999,476,711,-121,-391,-50,-473,311,-40,390,1000,-47,1000,-23,356,-155,-278,-523,-895,-1000,-618,-539,-699,-410,-899,1000,-192,-1000,-21,107,-675,804,413,-1000,1000,514,145,-366,1000,-267,-210,-473,-806,1000,195,-894,-32,-348,646,-597,162,-967,467,1000,-594,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{502,753,1000,778,-781,294,769,-510,408,193,-935,-1000,794,-986,-165,330,1000,-482,-1000,262,875,-723,116,-686,1000,-1000,351,-1000,-38,201,-1000,-822,118,256,1000,855,352,29,-131,-302,1000,1000,1000,532,-1000,-532,-228,-1000,-112,-1000,-45,-1000,67,19,868,1000,-67,773,-1000,-1000,-1000,1000,-1000,-625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{81,663,911,-210,80,-932,-779,-653,-102,-57,41,-592,1000,-70,1000,-709,937,562,-1000,-825,822,-1000,610,502,914,-347,9,-981,-988,-534,-544,341,441,-246,967,35,1000,826,-1000,1000,1000,730,46,1000,-1000,554,-722,-1000,-931,-753,544,-1000,-301,-357,668,1000,-416,-129,-913,-708,-1000,278,-495,-674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-858,268,-1000,-993,1000,-962,-804,621,-1000,956,728,780,-200,615,1000,-323,-1000,1000,1000,-123,-369,1000,749,478,-1000,1000,-471,866,-231,96,205,812,-282,-43,-972,-1000,-461,131,-373,1000,-434,-1000,-745,-319,1000,-254,513,824,-424,1000,487,794,-914,-585,156,-1000,-768,-210,1000,528,651,413,1000,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-1000,1000,994,73,872,539,793,-921,1000,323,1000,450,-1000,288,1000,451,-925,-82,-246,593,-549,1000,-359,663,-232,-1000,-88,-545,-939,1000,-944,-295,-772,-294,-1000,-1000,-857,-669,-1000,-590,184,-1000,1000,731,1000,-1000,1000,-312,-105,797,-1000,1000,1000,-835,666,-1000,147,-325,-11,-981,824,-453,-671,-207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-521,253,-741,494,573,-949,-951,631,-864,686,212,606,50,329,221,475,-184,671,-371,297,863,-98,215,557,-514,915,74,422,-110,-524,-528,52,926,106,450,544,755,575,-187,467,-101,-954,-589,-600,912,180,-557,408,-721,-233,-132,972,-988,-137,-36,-481,-62,-223,567,423,-353,801,-393,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{740,841,-715,-602,660,736,-497,702,-999,71,-663,-476,-505,-741,364,47,267,193,-996,38,-946,1,795,32,825,449,300,919,606,-474,-226,-31,379,794,878,689,-359,192,404,-717,357,-828,-773,-69,891,-747,-487,649,-204,414,603,-958,-724,77,901,926,-388,875,-360,481,-161,-124,420,395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-521,253,-872,146,-142,75,244,631,-372,400,-1000,-36,-1000,-505,-441,475,1000,-392,-200,482,738,-1000,645,-322,-202,1000,1000,772,933,-721,1000,114,926,278,1000,1000,-611,814,-1000,467,478,-492,-655,-564,978,36,-479,408,1000,186,119,-744,-387,794,-259,422,43,15,90,132,50,847,496,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-1000,1000,-1000,1000,1000,643,-498,-730,-1000,971,-828,-1000,-3,-787,115,-1000,-961,1000,1000,1000,385,-1000,1000,737,-1000,-518,218,-1000,17,-1000,-1000,-1000,1000,607,1000,-645,-688,1000,-1000,1000,411,1000,1000,-1000,1000,-937,562,1000,-950,-349,1000,-1000,1000,-577,653,248,-521,-993,982,1000,1000,239,-1000,375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-1000,1000,-362,1000,1000,-472,163,-1000,-605,-354,-22,-1000,91,-607,1000,-829,-1000,349,76,1000,-25,-849,1000,448,-667,1000,277,-917,108,249,-1000,247,-1000,629,978,325,-425,-604,-1000,1000,929,-894,414,289,1000,-595,614,1000,-639,-349,1000,-365,-47,1000,1000,-441,565,-1000,792,1000,369,88,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-304,1000,-1000,-1000,1000,-172,345,-909,184,-19,-10,-1000,1000,-1000,1000,559,1000,1000,-450,-392,1000,523,1000,1000,1000,1000,-381,-215,1000,1000,627,925,-63,859,802,366,-356,-748,1000,-371,-367,288,-1000,1000,-1000,-972,957,-1000,339,-1000,-144,-1000,-1000,1000,-997,794,204,-1000,728,851,-1000,-1000,170,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-956,1000,757,1000,-1000,-656,347,-236,-585,-56,553,-890,-1000,367,-1000,-1000,-1000,1000,737,1000,-309,-1000,380,1000,-1000,-839,968,-515,792,-1000,-1000,-608,-1000,-1000,-394,1000,1000,1000,-1000,248,-1000,-499,1000,-1000,1000,296,-707,1000,-321,-668,-18,882,931,-676,1000,1000,393,-1000,696,-554,1000,1000,130,371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-351,-270,1000,-1000,-591,537,209,928,354,-1000,-342,-1000,328,-405,1000,79,1000,-813,-897,631,1000,-1000,363,1000,1000,-942,1000,-1000,384,389,-744,79,-169,296,-377,588,66,-1000,508,-255,247,1000,42,821,-1000,-1000,-388,-456,234,-1000,733,431,57,221,626,-395,-1000,-1000,355,1000,-1000,-1000,240,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{623,-1000,-82,-1000,-1000,14,-73,-754,912,-370,918,108,-594,427,-76,-978,1000,-1000,351,607,1000,-544,-636,-361,1000,528,-690,1000,868,-43,-804,-284,400,-321,-169,259,1000,527,1000,-1000,-974,1000,-173,-949,-745,-117,-1000,-852,412,-1000,-1000,436,-577,232,399,298,34,393,-400,-242,-1000,-1000,1000,-818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-969,1000,-719,1000,1000,54,-43,-146,-1000,66,-316,-134,9,-194,369,-947,-1000,1000,-157,924,770,82,1000,681,-1000,461,-157,-368,-377,-171,-697,-263,1000,865,1000,141,-853,-556,-1000,846,781,-1000,1000,-1000,1000,226,642,1000,-639,-320,927,33,548,911,538,0,145,-235,1000,580,1000,825,-1000,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{431,-400,-103,-400,548,-471,342,261,145,436,836,1000,-183,379,-38,-515,-1000,-400,-28,-1000,770,990,-359,-198,37,-799,-1000,1000,428,186,127,1000,1000,742,367,-552,-207,-1000,400,-554,616,-266,424,-616,1000,182,642,-400,-631,-320,-1000,1000,-335,-464,-627,144,164,1000,-400,-820,-247,-685,-116,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{1000,-1000,196,-1000,-562,-866,-351,256,829,-1000,-268,446,-3,-209,352,-120,1000,-1000,1000,-782,414,-339,-1000,793,1000,185,710,415,17,-31,853,179,1000,334,-1000,-645,204,-1000,1000,-1000,779,1000,-1000,136,-1000,-511,-926,97,929,-1000,-1000,183,-930,1000,-918,405,1000,-488,1000,-609,-963,-1000,1000,375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{842,-1000,166,-807,-1000,-341,-1000,382,-449,989,84,1000,-714,-62,-679,-919,1000,-615,849,30,83,488,-1000,459,767,822,-803,925,-729,-1000,-604,780,1000,306,-1000,-642,431,8,1000,-1000,1000,1000,-211,-1000,-1000,623,-1000,-988,1000,-1000,-1000,1000,28,-567,-260,1000,-534,1000,-1000,-1000,-1000,-378,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{440,-47,-1000,-1000,-1000,50,2,687,66,239,-567,523,675,-661,-104,171,-861,528,-28,-505,1000,640,-31,1000,-637,-561,-29,42,-1000,103,348,-1000,178,443,332,-1000,-1000,-423,250,130,-435,-736,1000,44,-131,473,1000,-151,114,7,68,-386,136,-79,-773,-455,-1000,-263,99,-168,663,-186,-885,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-1000,-1000,1000,-147,-1000,-501,919,34,-131,-582,-82,1000,180,863,-60,498,-1000,-1000,895,994,-162,-1000,359,-672,943,-485,1000,572,395,-1000,293,-1000,-631,485,767,294,-882,816,312,1000,1000,-165,-1000,-753,-602,-1000,379,-1000,1000,-361,529,-907,52,432,-688,-831,415,588,-927,1000,-1000,-718,215,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-867,-620,467,659,-1000,-481,175,1000,518,-1000,-381,-171,1000,821,-201,-385,-920,-346,-662,431,-1000,-20,319,-1000,-957,-1000,-303,-728,969,500,689,-1000,466,-915,-259,943,-353,1000,1000,1000,1000,-855,-737,-1000,-1000,-1000,1000,-1000,1000,-116,1000,74,-978,-942,-1000,1000,1000,1000,-563,-476,-1000,-901,1000,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-973,-988,365,-998,-744,497,-279,256,559,849,-1000,250,-987,1000,-157,1000,791,-702,-232,362,1000,-149,594,-57,-876,-621,-1000,227,-762,576,-289,-1000,-1000,1000,-1000,1000,-833,1000,-677,-325,-171,776,100,-765,-1000,1000,64,-925,981,273,-990,-907,446,480,-1000,1000,1000,-298,-300,1000,-1000,-294,1000,-962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{642,-1000,486,575,-258,-301,-427,147,587,818,-1000,28,-1000,1000,-213,1000,1000,584,-678,678,1000,-540,161,691,-103,-1000,-947,-866,-1000,1000,-533,-842,-1000,-1000,-585,1000,-940,676,-598,446,-965,-230,1000,1000,-598,977,-6,366,507,433,-876,-539,511,-260,-460,1000,91,-1000,662,674,-698,-994,1000,-351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{655,547,-331,1000,727,923,425,1000,-26,-1000,1000,-482,49,1000,-1000,-1000,30,824,1000,-808,-1000,199,909,-101,578,-587,266,1000,345,555,421,-286,-1000,-1000,672,-49,526,1000,1000,-725,328,-34,383,-1000,-101,1000,1000,285,-1000,48,-327,1000,10,-963,-1000,-953,1000,1000,264,-1000,1000,-1000,-747,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-417,-909,109,-169,1000,-52,-1000,-1000,904,-1000,690,104,180,-945,-1000,-284,147,565,-256,-47,-1000,713,-1000,-809,779,343,103,-684,156,1000,462,-199,-467,-975,-175,-227,-1000,687,-1000,-543,-177,-746,-799,777,-1000,689,888,-1000,-863,71,-114,1000,186,-720,442,226,-152,-49,-152,-1000,650,18,-305,-779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-712,-1000,1000,195,-1000,-446,225,-49,1000,-692,-1000,703,1000,625,-34,788,-83,-1000,927,1000,-414,-539,-435,-503,-1000,-190,1000,700,379,-705,-394,-1000,748,39,-1000,-55,-1000,1000,-443,408,876,-670,-516,-1000,15,-1000,947,-1000,572,133,748,-934,-238,404,-941,-636,623,411,-1000,531,-1000,553,332,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{267,932,117,575,-439,684,-267,540,-1000,-1000,1000,28,-870,-232,-374,-1000,-1000,-342,-268,-1000,-551,-344,365,1000,1000,282,-45,917,-49,-697,956,384,-36,-1000,1000,-424,661,317,-250,-645,572,309,-311,753,453,-520,57,60,507,433,-681,930,-146,-652,460,-462,598,1000,662,674,50,682,-348,133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-1000,-603,1000,-954,-1000,-543,-551,-363,-337,-708,-208,769,1000,-507,-632,-300,-818,-1000,446,-667,-242,-1000,71,-1000,751,-264,789,541,-589,719,550,327,-976,-538,470,-616,-881,871,518,1000,964,311,-434,597,-1000,-157,922,-943,777,725,-621,854,620,-87,-375,135,531,266,-328,935,-1000,1000,10,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-712,-77,311,195,-664,635,378,113,657,-761,93,432,977,591,-563,-602,-437,-618,994,659,-403,-71,765,-503,-918,-547,978,944,623,-46,332,-34,191,-963,157,-285,-268,991,61,260,393,-198,-249,-799,15,-170,947,-931,572,-593,562,548,243,-230,-356,-822,952,411,-10,444,-640,192,-223,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{77,-123,160,142,383,537,-581,536,-204,-926,199,-494,-309,751,-282,-261,180,154,112,58,-686,325,1000,70,-586,261,269,942,80,113,16,177,-547,-1000,107,-497,-376,522,-789,-1000,194,-689,161,-20,-20,-450,340,-258,336,624,42,352,-460,-515,-231,-428,764,831,-170,-816,-265,171,-114,-207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-946,549,-527,1000,1000,-633,279,-661,-1000,-650,-907,1000,-479,975,-112,-519,216,95,-356,-1000,674,-630,1000,-485,-360,1000,-1000,-732,-646,621,611,-476,-595,-1000,-270,454,1000,879,443,409,-1000,-158,1000,5,34,-980,1000,1000,-702,-455,201,294,273,517,138,74,1000,271,1000,45,-360,511,-666,548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-1000,-587,156,-362,-1000,-449,38,799,1000,-1000,-806,1000,1000,534,-585,355,123,-2,928,829,477,573,-803,716,-287,1000,576,1000,917,-1000,258,-874,-242,-214,580,484,-742,-333,-1000,310,228,247,-1000,-1000,477,-1000,-1000,-725,146,-941,178,-72,953,-147,1000,-162,-377,-665,-149,1000,703,-780,712,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{414,615,-527,763,875,-286,-244,-66,-261,-320,-907,636,-829,-97,-461,-174,230,-1000,-545,-1000,55,-586,1000,480,-456,1000,-1000,-852,-803,-38,-553,-511,-951,66,595,177,1000,-382,345,498,-451,-158,765,-43,556,-550,1000,1000,-788,-455,-1,114,-10,17,845,246,1000,676,833,-143,-1000,1000,-412,886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-145,230,-533,1000,1000,-671,-258,-226,-1000,-74,-1000,1000,-1000,420,-132,-824,715,-1000,-1000,-1000,147,-958,1000,716,-714,1000,-1000,-1000,-1000,596,-312,-480,-1000,66,540,-205,1000,-430,912,1000,-289,156,980,661,460,-844,1000,1000,-852,-11,237,-308,-519,565,1000,998,1000,1000,1000,856,-1000,1000,-638,838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-1000,203,1000,1000,479,-1000,159,-901,-756,546,-20,-218,220,1000,797,-741,38,722,765,301,699,-1000,-488,-91,907,-1000,247,-358,285,815,1000,-640,-1000,243,-319,-849,759,864,23,-386,-828,991,-859,57,-425,-328,1000,826,-124,1000,546,83,-339,1000,-295,122,190,-697,250,-673,-1000,1000,-821,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-561,1000,-1000,1000,1000,258,-23,310,-863,211,-190,163,-1000,938,-455,21,627,-266,-175,-1000,776,-935,1000,1000,555,534,-1000,-794,-97,-621,-912,-560,-1000,71,70,183,-868,-37,1000,-848,-1000,-158,1000,540,-508,-393,1000,1000,-787,-227,640,55,7,321,1000,578,345,1000,1000,-506,-1000,1000,-1000,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-1000,-405,-858,1000,-78,-970,332,-1000,299,-937,-55,1000,264,586,64,268,283,-1000,209,-386,1000,163,-201,-941,837,-27,-1000,301,781,523,308,-388,-1000,-919,415,261,1000,576,-300,-190,-1000,568,1000,-1000,961,-1000,884,1000,-178,384,-507,-52,-172,149,-412,392,-62,120,958,1000,671,1000,-549,-504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{180,493,149,955,139,-388,344,42,322,82,-584,827,-675,-173,-1000,-749,-311,-484,-708,-463,1000,1000,-1000,1000,847,-298,-1000,-266,1000,-936,591,-319,-377,-620,-25,-405,440,-586,713,-726,-266,-414,169,-1000,-107,384,-280,904,-236,141,49,565,437,-1000,395,540,-265,-12,-725,188,-788,582,353,-441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-1000,304,-551,1000,1000,-947,-453,-810,-665,-573,1000,1000,-262,1000,-507,-337,1000,-91,-43,-1000,635,-1000,-201,324,-223,1000,-1000,-1000,-1000,1000,1000,-192,-1000,333,352,547,1000,678,688,-321,-882,-238,597,-206,-170,-1000,1000,1000,52,-143,-147,-803,122,256,1000,1000,1000,108,367,699,-552,657,-450,413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{-631,-1000,1000,3,1000,559,111,-234,1000,975,-984,1000,-1000,325,-1000,-262,-245,136,-629,-1000,-1000,373,175,-1000,271,-1000,-547,-645,1000,288,1000,-50,-58,66,-1000,1000,-315,-67,744,934,-1000,200,-524,424,49,1000,157,-681,-1000,-463,1000,998,-750,1000,361,519,790,450,-1000,479,300,747,858,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{628,-89,350,-1000,-1000,793,-1000,286,-861,331,-1000,468,-1000,-354,-395,-284,-275,169,-606,1000,953,1000,1000,-871,-1000,-222,-988,792,252,1000,-777,-259,-682,485,1000,71,-238,723,-750,427,-92,-289,-161,197,-523,469,-1000,1000,-904,1000,-924,211,676,186,-1000,1000,-1000,-90,962,-965,659,81,-51,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{104,-633,246,-1000,-607,-528,-1000,919,417,-363,251,229,-400,-548,-903,-492,1000,1000,-1000,-101,-765,-201,644,-910,-759,600,278,722,-1000,854,389,119,1000,639,1000,-699,-467,1000,-893,-96,-366,669,-149,-162,968,29,-348,-94,531,1000,-148,338,1000,-1000,1000,-841,-401,-59,974,-360,1000,-663,-532,-977}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{161,-35,66,-716,-673,133,-796,528,266,588,-1000,726,-1000,1000,-132,1000,-855,611,-1000,-591,1000,701,-487,1000,-1000,-1000,-576,600,1000,1000,1000,418,110,-1000,-106,-339,-424,562,-836,1000,-1000,-920,-338,-760,-1000,793,-1000,-1000,-971,1000,1000,130,253,824,-418,1000,516,691,-1000,-953,160,-594,-641,610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{52,-142,-910,-426,-1000,984,-766,244,-1000,-677,67,-123,985,-697,-192,399,-1000,867,1000,70,1000,157,772,-911,-946,502,-429,901,601,-772,-1000,1000,-1000,237,-1000,-569,-902,1000,1000,-347,-497,-1000,-1000,1000,-1000,-548,-428,-816,150,566,801,-265,-173,-145,-920,1000,-555,-268,1000,-1000,242,1000,-1000,-483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{-1000,-399,1000,827,1000,263,-123,471,373,677,1000,-336,125,-1000,-1000,-731,379,599,364,-296,-1000,-123,208,-1000,1000,450,674,-558,-609,969,579,-267,919,1000,-648,151,-1000,498,1000,-276,-336,1000,-531,1000,1000,-1000,896,311,803,-692,595,589,-421,431,1000,-266,291,249,-951,1000,512,669,1000,96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{554,-336,-1000,-647,-1000,-591,530,298,-1000,-1000,-156,-1000,56,-827,-788,823,-1000,407,614,131,1000,1000,-803,-621,-880,1000,-618,1000,-878,186,-1000,1000,-1000,1,162,-1000,-1000,870,-1000,-540,-152,-1000,-1000,-1000,-1000,-1000,-367,-672,325,1000,780,227,257,442,-1000,1000,-805,-441,351,-1000,949,-1000,-1000,-405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00334() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:IA==|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{89,57,-185,-79,251,560,889,462,93,-594,-970,19,-927,573,724,-846,-270,378,-648,972,182,-676,924,1,-96,473,561,181,765,706,759,-749,973,789,543,-956,596,-446,-967,250,-392,709,-343,58,-965,-213,249,-255,724,-435,-348,778,-525,954,-962,270,861,741,179,-270,905,-99,-867,990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00335() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:dHJ1ZQ==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-1000,-666,305,-611,15,207,-946,-576,249,-276,1000,-1000,-168,331,178,-948,212,-666,565,-1000,-330,-389,378,487,-109,868,678,695,539,1000,901,1000,-687,-394,1000,221,1000,1000,846,1000,1000,-319,196,1000,-599,335,-1000,1000,258,1000,897,-519,-224,-1000,-370,-298,-132,795,301,-1000,955,774,-907,-390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00336() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:dDZUOEFPSVw2eVlQNl8yaGI2|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-296,-1000,1000,153,-609,164,-1000,-932,-1000,-1000,-101,622,953,-239,1000,37,-892,-1000,1000,-620,-1000,611,-1000,1000,-1000,-1000,-768,464,334,-789,-165,493,313,1000,-158,789,-461,-163,612,-500,1000,-37,202,264,1000,-1000,428,88,863,1000,285,259,-680,-20,-457,-917,237,552,888,-62,-341,1000,-1000,-889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00337() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:c0dUMHVyRGE2|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{590,1000,-1000,546,-836,-468,775,1000,450,866,-762,639,430,186,-1000,-241,-540,-29,765,-426,-609,-115,394,81,1000,-46,749,395,-207,-126,214,110,393,342,593,722,95,-1000,-1000,324,397,556,-15,534,1000,-249,-126,540,643,-326,-500,725,-626,-400,-1000,-519,232,590,-386,-289,117,-831,-907,307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00338() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:NnZPNmQ2NgoKbU1aTExweDg1Cm90|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-417,-276,105,-624,757,783,252,256,-698,-676,-49,-1000,-1000,-889,1000,-1000,-1000,-1000,-909,32,179,-402,1000,217,-504,1000,244,760,1000,794,1000,290,-214,638,874,-875,-436,757,-663,664,-535,-631,-563,638,-331,-752,-693,-327,396,147,837,907,-952,-265,-325,-125,1000,706,-240,-1000,1000,236,-1000,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00339() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:MHg4|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{866,-957,-625,894,-166,-132,-217,387,763,288,1000,1000,230,98,925,-97,-92,1000,-771,1000,-531,-187,-911,744,801,-1000,-1000,252,-995,-241,-1000,-1000,1000,215,450,-196,804,536,133,-444,1000,1000,-174,-114,-1000,-484,1000,291,-1000,-587,-284,1000,938,801,-1000,46,543,1000,845,1000,-8,153,-71,-946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00340() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:KzB4ODAwMDAwMDAwMA==|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-695,-634,16,-1000,-913,1000,32,479,100,-260,298,-777,-124,-779,508,-1000,-1000,-1000,-1000,-475,-211,-388,1000,-38,119,1000,-212,1000,570,1000,1000,1000,150,29,1000,-723,-622,1000,55,825,-1000,-142,-802,1000,-46,-1000,-846,428,-154,627,879,211,-971,-698,-757,505,751,1000,-876,-1000,546,-741,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00341() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:LS0weDgwMDAwMDAwMDAwMDAwMDAwMDAw|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-240,649,-882,1000,1000,-1000,323,288,1000,479,880,555,-434,421,263,-623,40,1000,-1000,-355,-148,1000,1000,-701,719,1000,-15,-95,-500,842,334,-967,535,-605,539,-949,652,710,-864,1000,-586,-365,-1000,-129,-265,231,707,1000,-649,375,-32,1000,690,-928,-1000,480,-163,-992,-392,-151,1000,777,1000,-570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00342() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:bnVsbA==|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-111,140,-205,114,-325,-377,-404,250,-289,2,408,-1000,-170,511,-400,-289,109,5,123,-457,-221,1000,-175,667,332,-377,627,1000,409,871,346,419,-81,276,990,520,528,-315,-420,393,506,250,444,383,-189,-398,-454,819,-195,162,-124,-242,46,-907,-848,-335,-33,625,416,-1000,538,338,-249,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00343() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{984,995,-799,767,109,-567,235,1000,-391,1000,-1000,639,417,-490,-656,270,-408,1000,765,1000,-609,3,-874,340,307,-31,-642,467,-1000,-322,-1000,-1000,1000,297,-1000,-164,904,-1000,-1000,-780,636,1000,-441,-448,891,-756,715,-651,-261,-820,-500,359,87,749,-859,-56,-40,-643,-219,1000,-109,-407,-85,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00344() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:dHJ1ZQ==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{1000,396,-515,547,-326,-252,-136,400,-573,493,-285,408,155,-889,-329,1000,-57,400,-909,29,-1000,203,-1000,217,-31,-322,244,771,1000,-241,-533,-219,1000,169,-476,216,841,-684,-400,-842,745,257,-9,-386,-331,-752,794,-258,168,65,837,-310,-362,655,-325,-127,-1000,-150,-478,885,-482,-507,-359,863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00345() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemAge=java.lang.Long:MQ==|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{6,1000,-1000,1000,566,-146,-771,787,1000,1000,1000,1000,-1000,700,770,-308,1000,1000,-1000,-34,771,187,6,-1000,-224,-19,668,25,-987,-76,-212,-1000,204,1000,910,179,-1000,776,504,743,1000,1000,335,-1000,1000,141,-1000,-344,-697,307,976,1000,-27,597,-68,-1000,1000,-849,1000,-13,586,1000,-1000,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00346() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{976,184,-543,695,966,512,368,-702,854,1000,446,582,-1000,-1000,1000,1000,1000,1000,-1000,834,1000,-425,-1000,-1000,1000,1000,1000,1000,-1000,-713,-1000,-865,1000,498,-532,1000,-1000,1000,-872,-108,734,-526,-1000,-1000,349,185,1000,891,-1000,-907,-468,-1000,-1000,1000,1000,374,1000,1000,-1000,1000,920,285,-1000,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00347() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{-1000,1000,1000,520,1000,789,436,1000,-779,-511,-44,-255,228,-866,-1000,-773,-51,-1000,223,-414,-757,-608,604,1000,-531,-1000,-1000,-823,-183,-440,1000,977,285,-1000,-162,-1000,1000,-1000,-67,-224,-939,-886,662,-311,129,-108,-317,-1000,56,331,585,435,93,-1000,-1000,662,1000,252,1000,-1000,627,780,1000,-462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00348() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{-34,87,335,1000,757,1000,-376,697,49,1000,20,796,-1000,723,923,80,1000,1000,-195,702,-838,-1000,-209,-1000,777,-614,1000,-644,400,-320,578,-665,-1000,692,989,313,-822,959,-1000,1000,1000,-1000,-226,-1000,1000,247,-348,588,-92,54,580,634,-603,992,514,-1000,979,330,134,241,1000,1000,-1000,-640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00349() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{-819,831,-623,-764,836,81,-469,889,769,952,912,655,590,-700,586,-401,804,996,-1000,239,771,140,647,-721,-747,-267,-116,285,-987,-91,436,-1000,-209,-176,1000,-338,-781,633,383,564,429,1000,462,-740,1000,-1000,-885,-587,21,916,1000,832,82,631,-1000,-1000,1000,-849,742,-555,607,1000,-1000,218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00350() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemAge=java.lang.Long:MA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{266,-388,93,-1000,-902,-172,226,287,430,289,-609,19,113,-999,-1000,149,85,-670,214,-384,794,-112,-216,1000,-1000,-1000,497,1000,1000,212,547,400,-579,-191,-890,-997,-51,-153,-676,-271,-173,799,-122,823,522,24,844,-240,314,757,1000,-274,-937,-1000,170,342,276,931,-315,-19,217,82,376,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00351() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{1000,904,-1000,1000,-55,464,-690,-415,-1000,1000,-670,-90,-158,331,-31,638,-400,-153,-149,-698,-612,-321,-713,-584,582,-767,640,-20,-719,-419,-306,-403,172,-1000,443,815,-147,-941,512,-110,1000,1000,-234,-1000,-120,475,-317,190,-244,-1000,-424,1000,-358,765,1000,-597,504,-572,181,308,669,79,-1000,-257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00352() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{-766,-1000,267,-831,530,-293,813,-683,-95,-1000,-499,-1000,149,-1000,-293,230,369,-883,-799,71,1000,-607,-549,933,790,-822,-1000,1000,73,-533,-687,-423,325,-520,-478,746,-438,128,-1000,-1000,-619,-1000,-17,213,-26,-327,1000,-900,-351,-1000,-1000,-657,11,30,954,407,436,1000,-1000,-1000,380,-516,625,951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00353() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{752,861,-504,-707,38,228,514,729,487,258,436,21,1000,552,108,113,586,-507,935,-195,208,-395,711,-737,478,723,1000,688,611,177,282,-675,-298,403,418,903,342,1000,-91,-560,-818,-971,-497,-1000,457,488,-1000,-5,997,96,1000,745,-23,-85,130,-640,1000,757,-719,766,-434,10,925,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00354() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{1000,-330,1000,548,-1000,-805,271,394,950,289,999,-1000,-1000,79,-306,24,930,427,-1000,262,380,371,-12,-270,487,-33,1000,350,1000,189,-1000,-140,-569,911,-395,-1000,118,412,-142,198,-170,1000,-241,1000,-1000,-354,-286,-606,1000,-112,-43,-43,1000,450,123,757,627,899,948,384,-302,1000,720,124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00355() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MjE0NzQ4MzY0Nw==|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{626,538,-1000,-519,1000,1000,-1000,-551,837,941,1000,-1000,756,-1000,-1000,1000,639,-738,-1000,666,459,552,-1000,1000,-714,1000,216,208,1000,-1000,1000,-769,904,-59,-1000,-785,-1000,-1000,-1000,-1000,-564,-978,955,-1000,-239,102,-640,-820,676,632,-882,678,974,866,-535,650,270,-144,216,1000,-956,-1000,422,-630}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00356() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{889,-1000,1000,139,-675,-426,-894,37,1000,-365,862,-352,-422,-154,-17,693,331,746,-549,1000,-480,535,221,-339,-1000,-912,676,-827,1000,-20,-460,-240,807,710,1000,-1000,299,557,426,-966,989,1000,-620,1000,-264,-929,1000,-1000,471,938,-102,-1000,-1000,-637,-139,1000,-1000,460,125,1000,443,752,-348,259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00357() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{1000,120,132,933,-881,-162,839,-1000,829,433,738,-475,-530,1000,-130,-611,743,409,245,-586,79,-990,907,-1000,1000,-4,900,824,346,295,189,-502,-1000,1000,-1000,657,-155,1000,549,330,-414,-922,196,-24,-1000,-69,-1000,624,-762,-374,108,-911,600,84,628,206,665,1000,366,1000,136,413,571,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00358() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{1000,-1000,1000,-357,-92,-311,154,1000,348,-1000,790,-492,-476,739,-569,-565,1000,-555,-947,761,1000,1000,250,-316,-503,-240,1000,247,1000,-256,-686,-1000,170,-37,48,-1000,1000,975,67,149,-535,1000,-1000,819,-708,771,226,-274,1000,-265,464,-626,1000,1000,-401,983,-521,721,435,1000,-1000,1000,-733,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00359() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{1000,-1000,572,-849,161,-326,-61,674,563,-122,461,-517,279,681,309,365,1000,-111,47,784,580,-15,498,-150,-1000,764,1000,-48,1000,765,5,-134,867,424,1000,-1000,787,786,499,-685,-92,1000,251,678,82,35,695,-1000,1000,765,551,-1000,544,263,211,1000,-75,430,502,1000,-222,478,581,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00360() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{482,-460,-1000,-560,820,-162,-528,-877,444,-1000,856,-247,-365,469,108,100,-877,-685,692,-586,1000,-541,-98,-414,-25,-897,-921,-1000,-147,-785,544,-1000,-323,626,-295,58,431,-71,-334,-58,707,-586,-1000,83,1000,-183,-175,426,149,-374,-554,-911,-147,274,-386,-123,-700,709,-737,-1000,-382,410,191,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00361() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:LTEwMDA=|getItemCount=java.lang.Integer:OQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{400,-954,-362,-1000,205,-768,-644,334,1000,-257,-876,-928,-635,-1000,502,-1000,-833,-1000,-509,-902,-913,-874,-702,1000,1000,-1000,1000,-986,930,66,-1000,1000,-290,420,-300,-729,-750,-1000,-1000,956,-140,-881,362,-870,381,-1000,1000,1000,1000,1000,116,-1000,221,-1000,-577,53,-767,-45,1000,-51,-1000,840,1000,-495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00362() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:YWFhYWFhYWFhYWFhYWFhYWFh|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{357,-742,-53,-1000,-389,78,-705,-362,624,163,-347,-866,-600,-227,-106,-1000,-186,-682,-973,-768,-804,-688,-552,368,934,-491,173,-759,74,-390,-543,657,-200,58,851,-620,-1000,-241,-593,915,-82,-543,362,-374,444,-669,801,580,467,531,696,-694,522,-424,-451,53,-572,-129,917,-486,-984,324,645,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00363() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:MHg4|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{-233,-1000,-1000,-829,1000,24,-976,1000,1000,1000,-1000,-1000,1000,-982,-621,284,-90,1000,-190,1000,-117,-1000,-353,944,1000,-658,1000,-1000,335,-578,-611,674,-430,-1000,369,204,-242,-511,-988,1000,-1000,815,-31,-893,-1000,316,1000,61,675,-686,1000,-1000,283,-988,-1000,-1000,1000,290,971,-395,-320,1000,398,39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00364() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{1000,1000,-604,-1000,-778,-62,576,268,-1000,-771,338,-998,-902,-1000,-970,-1000,-641,-1000,955,-1000,827,652,1000,14,-508,12,-1000,172,156,-125,556,929,97,-17,769,-1000,232,-452,879,-678,1000,222,-1000,669,165,-1000,490,1000,-610,1000,-1000,1000,-254,-1000,-34,130,463,-1000,-419,-1000,-1000,259,1000,97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00365() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:MHgxN2Y=|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{474,-1000,1000,-608,293,-351,-1000,-1000,668,120,-538,1000,-165,-1000,1000,-618,1000,259,-361,482,-1000,-1000,-1000,134,852,-721,1000,-937,383,-1000,243,-61,-54,94,791,180,973,-1000,1000,1000,1000,465,1000,1000,1000,944,1000,187,-180,365,-1000,-1000,-416,534,-704,252,1000,1000,317,318,-1000,-1000,340,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00366() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:NTY4ZS0zNzg=|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{254,685,446,413,-713,422,774,-581,91,238,152,1000,-626,-779,355,72,207,-310,-1000,-243,-1000,88,-187,-799,884,-394,-267,-115,-349,124,134,568,-1000,-378,782,95,-794,300,405,328,644,31,397,169,-205,715,228,-506,-984,-1000,-982,290,-1000,1000,42,251,490,82,-776,-183,182,401,359,796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00367() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{818,-972,495,-1000,-125,464,-1000,-1000,573,-760,1000,639,-698,-1000,661,-1000,739,-353,-632,-902,-1000,-696,126,-2,1000,-661,803,-249,-231,-1000,-498,322,977,341,992,51,41,-1000,481,-128,833,-121,-1000,1000,1000,732,1000,1000,84,323,-1000,-842,307,543,-577,492,271,629,-555,-14,-927,-1000,380,35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00368() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:LS0weDg=|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{235,-933,-858,-322,218,-289,-1000,-405,943,78,-475,133,863,-1000,892,817,1000,681,-533,515,-709,-1000,-1000,205,887,-1000,989,-1000,507,-984,-238,532,-726,-333,-403,228,1000,-261,-63,1000,-708,1000,756,407,-879,742,552,-913,-123,-1000,318,70,-485,503,-1000,-1000,1000,601,484,-1,910,599,219,102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00369() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:KzB4ODAwMDAwMDAwMDAwMDAwMDAwMDA=|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{1000,1,-659,385,-380,-834,304,-581,-483,-641,-866,1000,-514,-1000,1000,-32,-887,-829,-791,37,-1000,-52,-1000,-131,604,-643,556,-1000,1000,-250,246,599,-956,1000,-1000,-1000,423,285,-386,1000,-30,-227,-74,-1000,-918,-154,-810,211,35,824,453,-1000,-1000,255,-726,-58,-1000,-524,385,885,-521,953,703,47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00370() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{91,-1000,854,229,-895,639,258,-1000,1000,-969,511,363,1000,-1000,-1000,1000,1000,-1000,981,-717,-336,1000,-500,165,-1000,-1000,-1000,-1000,-920,-194,-1000,-33,868,-231,479,-580,640,-800,-1000,-45,-292,-119,270,1000,-1000,1000,789,1000,-624,969,696,-1000,-958,-477,318,-1000,-1000,-1000,1000,1000,1000,497,-1000,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00371() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{-309,224,993,999,-791,404,-695,-120,738,-284,1000,-10,249,47,400,1000,-91,400,619,-1000,246,1000,-204,-1000,283,400,-113,63,35,-22,400,-228,-532,997,-653,308,-373,566,400,13,353,-1000,676,145,-42,1000,576,-314,-993,1000,1000,-999,-1000,-975,-281,46,-1000,-566,881,489,-400,-903,-87,-348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00372() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{106,-656,581,1000,-694,569,205,-438,331,-882,511,873,790,-684,-917,275,1000,-856,443,1000,-1000,972,189,434,-939,-837,-1000,-1000,-475,-833,-817,496,356,-210,598,-547,1000,-844,-547,-230,199,142,345,824,-364,1000,758,1000,-714,637,714,-1000,-1000,-1000,305,-1000,-886,-489,944,955,562,527,-939,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00373() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{-949,212,-822,343,-105,-201,-223,-97,-1000,325,86,781,-402,87,261,-122,-390,860,-1000,202,1000,-1000,565,774,-978,-791,479,1000,-966,-732,-999,-283,-1000,823,-285,-675,1000,-35,845,670,841,94,-455,42,1000,-391,-405,1000,-672,-579,-1000,-106,240,-196,344,-502,-858,-1000,-1000,39,88,822,215,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00374() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{1000,-46,-708,500,215,1000,1000,-342,331,-1000,-291,-207,9,-1000,-595,83,1000,-547,1000,1000,-725,-615,861,274,-1000,-442,601,-922,-1000,540,-998,360,-375,-754,919,-472,1000,-1000,142,13,-1000,460,685,226,-556,-1000,951,1000,-683,-282,-758,30,-389,-288,-1000,-1000,662,-1000,1000,1000,1000,564,-1000,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00375() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{-512,-715,878,-1000,-521,101,-646,-898,285,-331,1000,523,1000,78,-1000,918,1000,-1000,-770,-813,-664,-297,-728,986,-425,-1000,-1000,-1000,49,-463,-3,193,456,480,-150,107,956,-481,-1000,179,516,441,201,1000,469,1000,-386,1000,-1000,-87,419,-602,-1000,561,370,-103,-1000,-36,933,927,527,634,315,-619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00376() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{962,732,-1000,978,828,-901,848,942,617,-703,-12,255,-699,-555,261,465,-366,423,801,503,-823,-918,-219,-519,989,-246,465,504,165,-1000,423,11,62,415,985,727,532,-894,520,-664,-105,73,-588,47,-505,42,1000,898,-290,590,112,-360,686,139,334,-1000,1000,-1000,-283,848,193,514,599,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00377() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{216,1000,-76,-312,439,-27,-183,-557,-749,-782,-642,261,-1000,1000,851,1000,1000,-1000,-607,1000,761,-1000,-195,-1000,705,-248,-1000,69,1000,1000,-692,792,-1000,-1000,1000,533,-468,-606,-1000,322,-348,-708,207,552,953,-567,427,-90,1000,-152,-467,362,323,1000,249,-386,-1000,806,207,394,915,157,-382,510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00378() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{870,-1000,-1000,-83,1000,-406,-320,129,-711,1000,-122,1000,887,-1000,-480,285,-1000,647,-594,-593,-1000,-183,-1000,-1000,-116,-552,-542,54,478,-193,139,-1000,-173,826,-339,675,275,1000,1000,-1000,556,-939,383,-1000,-413,-624,986,912,-1000,-435,180,-581,365,-1000,-28,1000,551,-1000,618,92,-1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00379() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{1000,1000,-140,-1000,1000,274,-341,-684,126,-1000,347,1000,-1000,320,1000,496,1000,443,-1000,-1000,-696,-922,-101,288,1000,-1000,-1000,21,-353,1000,381,-546,-158,-587,1000,1000,-532,-748,-116,1000,-469,401,85,1000,-1000,-835,731,1000,-1000,-574,1000,1000,-81,-958,-4,1000,-659,83,1000,545,685,-48,-216,-213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00380() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{1000,238,947,-1000,1000,316,4,444,-624,-547,414,171,-756,-40,582,1000,768,853,-1000,-866,-1000,-756,277,1000,396,-1000,-796,372,-968,196,462,255,-804,-81,1000,553,-184,-677,-221,80,68,401,579,1000,-1000,326,-364,144,-394,444,269,1000,-484,-1000,-505,1000,-765,276,517,-811,461,-168,-1000,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00381() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{1000,-532,-832,-444,1000,212,51,-594,-1000,-44,47,1000,-871,-932,1000,423,165,-236,-1000,-601,-130,415,-563,-1000,1000,-1000,-908,2,1000,1000,-450,-1000,-1000,-681,799,918,323,-207,264,446,-206,-637,17,171,-72,-932,954,465,-1000,-946,-239,1000,485,-427,715,1000,-290,-177,1000,721,133,-314,219,-890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00382() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{768,-103,-1,-1000,-1000,-1000,4,444,-225,177,268,-841,-335,-17,279,388,-360,853,568,565,-805,-821,792,1000,396,-482,-967,612,-1000,114,201,-912,-113,-699,793,-607,-1000,-40,-1000,-1000,167,-1000,538,1000,-112,1000,-507,-604,-1000,949,-271,1000,-991,-1000,521,648,-1000,-15,321,-800,82,-288,297,-475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00383() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00384() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-163,37,-811,166,-911,669,-872,-782,-415,25,-154,-341,653,-684,35,-325,-951,268,864,658,657,584,-136,152,221,905,-421,-505,808,351,-742,-722,-310,-214,-246,-965,595,922,940,621,-149,-955,-519,-348,-111,-268,581,44,-276,-728,469,-779,616,637,796,-115,-82,-110,-130,350,-731,-357,718,-574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00385() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{319,-123,-452,-590,569,1000,939,637,-142,889,921,632,547,1000,-282,353,-769,711,-525,472,-1000,1000,-681,-181,-216,384,223,412,-449,-302,519,-1000,487,369,-1000,230,-468,810,466,-1000,-90,-128,-517,597,-1000,-550,76,853,-1000,183,-128,392,-742,-1000,-380,1000,-598,-594,-1000,85,-1000,-415,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00386() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-257,504,-395,-7,433,-136,-656,363,119,-795,-295,-244,-437,551,904,954,-81,-1000,243,1000,-236,263,189,-1000,854,312,-445,387,1000,292,307,45,-171,-1000,217,464,-1000,-299,-660,-414,-49,-1000,182,1000,426,558,-313,-104,-35,38,-1000,430,215,296,-241,332,212,199,630,-180,1000,-98,233,278}));
    }
}
