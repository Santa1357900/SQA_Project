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
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "addValue(java.lang.Comparable,double):void",
            new int[]{842,-413,-56,467,375,612,82,-1000,-601,-788,556,1000,171,799,714,-200,1000,-468,236,-528,215,-405,980,-499,587,749,-547,1000,-103,32,266,1000,259,1000,428,473,169,739,-743,43,-381,-212,1000,-476,129,-807,-1000,-17,102,914,882,-528,-53,158,552,-573,-920,82,-726,781,5,131,32,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "addValue(java.lang.Comparable,double):void",
            new int[]{-455,-652,459,-172,-470,-662,-264,189,-164,661,1000,-507,-839,250,172,20,-534,-951,1000,224,-29,581,203,-323,-268,-796,-304,-1000,449,-839,-444,-161,279,-1000,82,-415,-727,-293,759,100,768,542,-555,512,814,-350,-557,25,-332,316,-1000,-576,665,579,-208,720,-238,-1000,1000,-60,-628,274,-882,30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "addValue(java.lang.Comparable,double):void",
            new int[]{581,-522,244,942,1000,769,1000,-1000,-470,-604,527,-297,431,158,1000,-602,611,-251,-400,-684,152,-1000,770,1000,286,-670,1000,-1000,-1000,640,1000,646,-819,-234,1000,515,-133,1000,-132,-637,-355,-973,1000,-1000,-810,-1000,-97,-722,448,1000,307,-244,-45,-288,-140,-937,806,43,1000,-110,-1000,570,689,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "addValue(java.lang.Comparable,java.lang.Number):void",
            new int[]{-827,474,-952,315,1000,395,-1000,827,-937,-1000,-1000,270,-805,-967,-260,1000,775,-707,-1000,1000,165,-725,1000,-392,465,-538,-885,-1000,1000,-15,876,612,-764,974,-1000,1000,-1000,41,-270,-895,-164,-1000,-136,-1000,1000,1000,-674,390,407,-996,-1000,1000,19,-428,-1000,353,548,667,-522,-1000,-839,1000,-1000,-996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "addValue(java.lang.Comparable,java.lang.Number):void",
            new int[]{-578,-919,194,29,7,-1000,580,124,1000,1000,-973,-682,565,-495,43,-987,-697,1000,1000,-291,-414,703,-1000,-1000,566,739,1000,825,917,1000,-242,-204,-1000,-60,-397,88,-272,1000,450,168,-932,-1000,-679,203,-431,1000,260,-1000,580,57,1000,1000,-1000,-1000,199,1000,1000,-1000,-1000,-1000,60,1000,-472,-683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "addValue(java.lang.Comparable,java.lang.Number):void",
            new int[]{-95,-834,819,249,368,120,422,870,-470,-1000,350,114,969,494,-58,493,292,-519,345,-1000,486,-823,39,608,500,-1000,1000,-931,-615,-822,827,-21,-843,259,-142,1000,-920,465,-798,-1000,733,263,89,-767,397,842,-85,-159,490,-293,-518,-214,831,333,476,81,-1000,-1000,-411,-328,387,835,334,533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "clear():void",
            new int[]{-1000,656,-151,-170,915,482,1000,-115,357,-651,-119,-142,-600,452,-1000,-344,1000,995,400,-651,-282,749,76,333,-152,420,-94,-4,-203,-274,22,-1000,-292,-400,-36,-453,-291,-29,982,1000,-400,-1000,734,-848,204,216,676,-163,-777,323,254,-1000,561,-508,113,-1000,416,-121,-502,-472,-664,-187,-115,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "clear():void",
            new int[]{-545,-488,674,-1000,-103,-387,834,327,-418,-386,-1000,-689,-58,1000,1000,471,-1000,729,1000,812,57,640,-87,-910,-1000,-784,-1000,561,-738,-755,983,-1000,66,-574,-935,-697,-1000,-727,1000,-591,-866,-247,-663,-623,-192,-1000,525,-919,-276,360,1000,-81,703,731,302,-354,1000,141,-501,-15,-557,-380,867,-824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.DefaultKeyedValues|getItemCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "clone():java.lang.Object",
            new int[]{-52,488,829,1000,-256,-865,-618,-856,-1000,-553,-1000,273,-1000,-82,-868,39,809,-1000,32,675,254,117,-875,-186,-1000,-431,-265,1000,840,322,674,421,-605,1000,-415,-274,-1000,491,-28,-299,935,-340,-322,1000,226,118,187,-900,-253,584,402,-243,-568,-77,560,-531,1000,-72,12,-570,742,-502,-992,704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.DefaultKeyedValues|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "clone():java.lang.Object",
            new int[]{78,1000,684,1000,-324,-1000,-1000,-75,-1000,-205,-373,317,354,-175,-1000,-308,1000,-499,-139,-732,-165,-945,-133,1000,351,-784,-527,1000,993,-337,372,259,-774,604,-554,-1000,-410,778,-640,227,561,-184,-1000,1000,25,945,-629,-59,893,421,638,-281,-1000,403,1000,-821,872,1000,-487,59,510,-211,-1000,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.DefaultKeyedValues|getItemCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "clone():java.lang.Object",
            new int[]{548,-561,-281,1000,-96,-491,-379,-1000,601,621,-123,-787,-1000,546,-66,439,220,646,508,-553,406,450,-605,49,-1000,61,-1000,1000,600,-921,-1000,376,-646,-13,102,-615,-358,-1000,-137,208,699,-43,378,797,-175,-604,-53,-1000,221,538,146,155,30,9,715,-318,872,72,660,691,204,-771,-601,272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "equals(java.lang.Object):boolean",
            new int[]{-496,-240,419,98,-242,838,358,733,859,-415,144,165,968,-518,-261,268,979,-536,-1000,35,196,-282,15,-1000,-366,16,58,708,-320,-561,759,436,-255,136,-293,283,568,376,-1000,144,854,-1000,83,-692,-500,-555,-1000,-1000,1000,-185,-361,-326,-168,1000,425,-37,-1000,306,388,-146,-614,822,150,-337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "equals(java.lang.Object):boolean",
            new int[]{442,-262,-146,520,-294,-423,-74,213,142,137,1000,292,400,-439,-293,1000,1000,39,-241,1000,951,181,-573,-115,260,-154,271,779,-805,-209,603,1000,-533,-84,-412,-591,-60,362,-1000,-1000,598,-48,176,-1000,167,-1000,-335,-943,-1000,-37,-278,-214,-1000,877,1000,384,-1000,-534,-557,-689,-961,1000,630,813}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "equals(java.lang.Object):boolean",
            new int[]{413,-408,-66,761,-1000,1000,1000,33,948,-670,144,627,755,-144,-538,612,1000,436,1000,-482,1000,902,-539,807,1000,16,48,1000,-320,-4,-45,356,-167,-1000,-110,-250,-306,1000,-663,1000,165,489,-138,-536,-407,-1000,803,-701,396,615,-727,-469,-1000,968,795,-916,-1000,587,-1000,-371,-1000,-104,39,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getIndex(java.lang.Comparable):int",
            new int[]{-702,1000,299,345,490,-1000,-1000,1000,-680,-462,147,-701,-948,-1000,-1000,-1000,-1000,-1000,773,93,-968,-1000,-781,91,-1000,-849,1000,866,-1000,-330,-92,975,-970,-1000,-1000,-821,1000,-1000,1000,-1000,-68,-1000,-1000,-1000,311,-606,900,-730,532,1000,-733,-1000,73,-1000,1000,91,1000,-1000,-138,338,-1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getIndex(java.lang.Comparable):int",
            new int[]{78,203,709,705,1000,-650,-1000,1000,-1000,-369,1000,-360,285,-1000,-1000,-1000,-97,-1000,-264,-1000,-1000,-1000,-559,34,-1000,1000,1000,1000,-1000,-983,-746,-1000,-1000,-431,-1000,-1000,837,-1000,977,119,-68,-957,-1000,-694,1000,-1000,1000,1000,1000,633,-1000,-1000,40,326,1000,1000,313,-1000,-320,7,521,1000,-138,-801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getIndex(java.lang.Comparable):int",
            new int[]{-745,1000,879,-269,1000,-755,-1000,945,-240,-1000,-564,-14,-291,-941,-1000,-1000,-414,-1000,1000,-1000,-765,-1000,-504,-517,-1000,1000,1000,1000,-1000,-893,-652,-644,-424,-1000,-1000,-710,-1000,-1000,861,-1000,564,-1000,-918,-928,106,-1000,-1000,964,1000,743,-507,-1000,137,10,1000,-1000,-435,-1000,-729,305,916,1000,474,-861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getItemCount():int",
            new int[]{-1000,-1000,-101,548,-1000,1000,-1000,-950,1000,596,341,1000,-24,-1000,221,1000,-1000,-636,-576,-1000,-795,-1000,182,-237,1000,-311,-505,165,-1000,-1000,435,1000,-1000,1000,1000,996,-939,-25,-398,-295,738,1000,-511,-346,1000,189,71,-380,1000,-1000,-1000,-312,-246,-940,1000,-941,-497,1000,1000,-1000,1000,-1000,505,-896}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getItemCount():int",
            new int[]{-513,-140,344,-881,-1000,187,-901,-338,990,290,1000,64,-1000,-1000,-167,193,-1000,891,-1000,-1000,-582,1000,1000,715,1000,-337,156,35,571,327,1000,-618,33,-173,617,-76,-676,233,-170,-12,208,1000,658,990,-224,911,560,611,806,148,-512,1000,-1000,-163,665,422,954,-70,-513,-712,250,-1000,1000,-967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getItemCount():int",
            new int[]{-201,782,84,84,400,1000,-432,319,-410,126,526,356,-317,-521,279,-103,239,-145,-45,400,-721,-400,428,134,26,-877,764,649,926,340,158,-1000,341,-51,-596,-1000,628,-240,-1000,70,-45,-400,820,1000,-1000,23,350,983,234,410,53,82,335,-248,-544,617,96,-270,400,688,425,400,242,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getKey(int):java.lang.Comparable",
            new int[]{70,467,-306,-359,220,-757,-968,663,-552,-79,-1000,70,-82,542,140,739,1000,226,-1000,111,-755,-193,-633,-368,284,-857,239,67,-788,-1000,562,178,1000,1000,-226,-101,79,-110,270,-1000,-1000,1000,860,-57,-1000,891,-926,15,490,-1000,215,-551,-1000,-463,-325,-1000,117,-1000,-608,-219,1000,22,446,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getKey(int):java.lang.Comparable",
            new int[]{135,1000,-91,870,-161,-944,939,1000,-24,-58,25,756,-55,1000,-868,-406,499,-754,-209,-535,381,-910,-1000,-790,-522,-1000,618,81,-1000,-797,-329,-1000,-11,-313,-1000,69,-494,-1000,720,-228,225,1000,656,1000,-1000,46,-1000,1000,356,-1000,-186,666,-421,809,4,-504,58,-429,-47,288,1000,-849,70,822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getKey(int):java.lang.Comparable",
            new int[]{1000,1000,59,719,749,786,-473,1000,195,-1000,1000,40,1000,1000,-360,-417,-496,834,-459,-144,995,-1000,-1000,808,-48,1000,1000,345,-1000,929,-580,-1000,-822,-213,-533,550,294,-234,-1000,-845,-152,793,-1000,173,-1000,343,929,1000,-920,-284,-609,1000,-546,1000,13,-616,-1000,1000,-465,818,-1000,378,-1000,843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getKeys():java.util.List",
            new int[]{-635,154,519,-1000,952,-588,235,202,14,893,-369,1000,482,-855,-422,-340,-850,-749,925,-526,576,-294,1000,-904,-49,-644,576,565,105,-1000,-527,-194,1000,292,275,-1000,274,-911,-335,-969,-1000,495,97,-551,-127,-136,-1000,1000,-29,-626,655,570,-429,-644,982,512,-792,-750,-817,183,183,-934,-232,57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getKeys():java.util.List",
            new int[]{-71,-931,454,64,973,1000,557,586,628,632,297,753,-350,702,258,-169,-236,347,-66,-553,377,-385,424,1000,-562,-720,143,123,565,-1000,1000,-527,-620,-403,-175,-262,-416,-531,1000,803,-577,-855,365,-220,-256,692,113,219,1000,-488,789,-326,-90,-785,514,-740,156,-902,-364,700,874,-277,-302,458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getKeys():java.util.List",
            new int[]{449,186,504,-479,970,-1000,612,571,512,851,-213,840,1000,-1000,-147,-117,-485,179,-640,-618,32,-803,756,581,-605,-523,625,-601,-997,-213,-717,-656,1000,-1000,-298,1000,1000,-1000,580,-1000,-421,-20,878,134,467,-751,-59,-771,-20,-471,-698,860,640,-287,-147,1000,-687,-752,-501,197,160,-937,292,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getValue(int):java.lang.Number",
            new int[]{698,-780,-511,-622,-505,1000,-27,-1000,-226,1000,-927,1000,-793,-1000,1000,-542,515,-576,-1000,13,166,160,1000,-780,13,-86,-890,-37,-677,289,-400,-228,-1000,830,-400,205,702,-199,-1000,-414,388,-181,486,472,-870,-506,-1000,661,-419,-642,202,779,-420,811,-380,827,134,-1000,-145,249,-114,-99,-451,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getValue(int):java.lang.Number",
            new int[]{-400,-121,-431,1000,1000,-1000,925,587,1000,988,-488,402,1000,744,926,741,-1000,-179,30,-1000,329,1000,-679,1000,-1000,-1000,1000,202,-1000,841,1000,-944,-266,-970,1000,827,-962,280,319,-1000,-370,1000,-1000,-366,-634,-880,815,670,-1000,1000,244,1000,-1000,1000,-1000,887,1000,21,-324,-621,-1000,-525,867,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getValue(int):java.lang.Number",
            new int[]{296,-742,493,-443,-515,404,-165,-840,107,350,-361,77,225,519,90,236,420,-939,368,-962,999,466,435,-308,-464,723,-529,600,-603,-795,951,-18,381,-133,690,312,-781,209,-483,-946,304,925,671,-274,-272,239,-496,342,480,458,-384,946,-534,487,-836,111,-57,739,-239,58,-128,167,110,-666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getValue(int):java.lang.Number",
            new int[]{-253,281,-926,515,1000,-951,1000,566,1000,578,-1000,943,1000,1000,-1000,663,-1000,101,296,57,-211,774,-1000,-710,-1000,-571,1000,-748,-677,-1000,1000,-1000,-160,-1000,1000,-1000,-411,17,631,87,296,627,-1000,-277,-1000,-800,1000,-963,-1000,721,-1000,862,-1000,1000,-1000,759,821,1000,-427,-140,-1000,497,-237,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getValue(java.lang.Comparable):java.lang.Number",
            new int[]{601,-1000,-316,-1000,-1000,-497,-1000,-44,27,191,-1000,-1000,1000,-111,1000,696,-1000,216,-192,227,242,-1000,52,-1000,1000,-1000,1000,1000,1000,303,1000,-1000,-271,1000,-1000,684,833,1000,534,-1000,-1000,1000,-212,-44,-1000,-728,130,-67,-1000,467,1000,386,-1000,758,1000,701,1000,-1000,1000,-1000,-400,449,400,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getValue(java.lang.Comparable):java.lang.Number",
            new int[]{1000,-1000,-316,-584,-487,-663,-862,985,-293,460,-440,125,1000,942,-885,80,-713,488,-116,4,358,-1000,-242,-373,576,-1000,-1000,1000,-38,290,26,-626,-55,1000,-831,844,-719,-1000,433,-1000,-526,469,233,-52,-953,-665,494,-174,-198,634,923,31,-832,475,1000,76,202,319,818,-343,80,-21,-365,893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "getValue(java.lang.Comparable):java.lang.Number",
            new int[]{169,763,374,1000,-139,-360,1000,-1000,1000,-1000,292,-822,65,868,273,520,1000,-1000,-307,1000,-1000,716,-622,41,-1000,1000,580,-837,-1000,-352,-195,-400,-1000,-1000,1000,771,-1000,208,-414,1000,744,-896,1000,-1000,1000,-1000,1000,1000,427,692,-580,-710,-193,-1000,-349,1000,1000,629,-763,-1000,-1000,-1000,1000,-60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "insertValue(int,java.lang.Comparable,double):void",
            new int[]{79,1000,599,323,-454,706,-1000,1000,106,-332,991,1000,-387,380,-976,655,531,1000,-239,1000,1000,-1000,-934,-185,-1000,-1000,-756,739,627,1000,-1000,-178,1000,1000,1000,-937,-719,1000,-11,380,-648,1000,1000,-1000,-1000,424,183,614,-186,72,-1000,-1000,-628,-1000,736,-687,724,1000,-561,-1000,49,-1000,-726,799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "insertValue(int,java.lang.Comparable,double):void",
            new int[]{-530,-290,-421,-107,492,408,-1000,373,348,-134,880,-779,-322,17,-943,301,562,-276,-578,-1000,294,-958,-621,1000,625,300,329,-1000,57,-1000,689,-1000,-744,-924,-243,-762,-795,708,243,-996,-935,724,874,504,-1000,222,-543,21,1000,1000,-438,93,491,491,1000,414,438,-56,906,-195,-531,629,-856,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "insertValue(int,java.lang.Comparable,double):void",
            new int[]{-888,-1000,-951,-618,1000,514,-96,-381,150,601,964,-779,-1000,-1000,-856,-974,562,-819,438,-216,-330,-671,-968,194,-734,-708,873,626,-149,-861,-385,-5,-347,-1000,-676,102,-791,-188,-1000,-996,-642,209,-462,504,-1000,1000,146,267,838,1000,75,1000,1000,-844,-997,414,-86,1000,-271,1000,54,633,-383,759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "insertValue(int,java.lang.Comparable,double):void",
            new int[]{-1000,-1000,-881,-903,32,-1000,-266,-715,-520,152,-351,-675,-24,291,0,29,-225,-1000,185,298,-224,1000,-1000,113,-434,364,1000,138,-886,-703,532,-681,-786,-388,-635,431,-791,0,-165,9,8,-655,149,1000,917,549,-106,808,-562,578,754,1000,-1000,911,1000,539,-209,-671,11,510,39,365,154,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "insertValue(int,java.lang.Comparable,double):void",
            new int[]{616,1000,-891,1000,-1000,-1000,-404,1000,-878,237,0,116,-1000,-168,554,438,-885,-1000,1000,1000,1000,-279,-372,-1000,-1000,557,-1000,1000,1000,1000,-530,436,1000,1000,1000,586,334,-675,-1000,-1000,917,-1000,596,-1000,972,-1000,-11,-700,-1000,-333,-112,-62,851,-1000,141,-456,1000,-918,-1000,-1000,1000,-1000,-804,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "insertValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{448,-326,409,-125,-55,-744,-296,-563,1000,795,-256,149,389,-257,-420,-342,-280,-625,-313,-1000,-1000,605,205,1000,-549,-263,-972,853,-1000,948,-877,-340,-1000,-58,722,327,-394,-1000,-754,-80,-39,434,431,-850,-554,-249,-151,-254,46,399,1000,-37,184,1000,-1000,-1000,864,-685,-454,242,883,534,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "insertValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{-350,1000,704,-1000,-66,-613,-393,-1000,-1000,235,-904,-807,-1000,568,-1000,-192,199,-799,-78,1000,1000,-621,964,638,-797,-1000,-676,488,-393,804,783,-1000,1000,-279,-652,-1000,1000,800,-416,271,-1000,-1000,-383,1000,-78,-677,68,436,1000,-186,-743,386,-910,885,-270,1000,-1000,215,542,962,-121,360,-644,-202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "insertValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{1000,400,-816,-896,-923,1000,-1000,-1000,-132,285,-1000,-315,1000,665,-683,-164,-188,-284,-990,5,877,-857,-731,-1000,60,463,-1000,-479,1000,445,-1000,-1000,486,111,-293,-534,907,-358,-357,-985,490,129,-744,1000,963,-95,664,-382,732,-631,-625,-1000,252,-657,1000,400,-745,132,1000,-288,1000,-253,833,-775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "insertValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{413,1000,719,1000,709,-1000,1000,90,1000,-83,667,690,-276,-94,678,-429,-141,-41,-360,-917,-1000,465,112,-357,34,-628,162,1000,-75,601,-1000,-1000,-1000,-160,1000,1000,-567,-665,-198,-387,-1000,709,-840,-1000,457,736,656,85,-454,695,612,479,-505,916,-596,-1000,1000,-419,-503,962,-619,-120,-1000,-838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "insertValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{223,-992,-837,551,-96,-665,-539,832,1000,506,-84,151,-866,-402,1000,-199,-882,-499,-624,-1000,-999,581,666,1000,-748,1000,1000,-103,-1000,846,-819,-935,-1000,151,-335,1000,524,-1000,-903,438,236,686,-490,-372,284,530,845,6,-646,889,1000,-588,-652,983,-637,-1000,-708,-1000,-125,962,-932,49,405,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "removeValue(int):void",
            new int[]{682,-481,199,70,473,515,940,-59,-1000,-1000,-1000,1000,804,-1000,-1000,1000,1000,1000,342,63,372,90,-1000,-1000,1000,1000,-5,-1000,447,-1000,-1000,-515,-1000,1000,1000,601,-905,-449,1000,336,-598,13,1000,-1000,-354,77,1000,-1000,1000,85,-243,-1000,-1000,-708,-694,-47,508,-74,-822,608,861,984,-714,443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "removeValue(int):void",
            new int[]{-581,-993,-426,86,-341,-86,-1000,-961,-66,-421,-1000,-81,-1000,-1000,391,-1000,-784,681,23,229,1000,1000,-1000,-1000,1000,-1000,619,-1000,-1000,1000,-738,-132,-874,311,1000,1000,1000,-801,259,-625,12,1000,-115,399,290,1000,421,-1000,853,368,-463,1000,1000,1000,1000,-1000,-1000,157,-143,-1000,1000,990,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "removeValue(int):void",
            new int[]{-51,997,639,470,306,-349,-61,278,618,373,825,881,693,-439,220,-276,181,-743,130,-309,909,13,746,-445,87,-445,-637,-122,-47,-503,-349,645,236,-670,-395,-995,990,702,-498,-518,788,-390,-388,742,-937,-453,-925,602,-542,854,323,332,-535,607,-219,518,306,-829,-834,-267,94,276,637,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "removeValue(java.lang.Comparable):void",
            new int[]{-92,-541,-26,-330,-620,307,861,-64,183,566,4,-766,-306,887,1000,1000,441,960,-331,417,615,1000,294,-244,-400,295,-1000,473,-105,278,580,724,-517,-893,-301,385,-854,459,252,-643,-341,-161,-757,12,887,1000,613,1000,-1000,-691,915,554,-1000,247,-213,-704,-1000,-198,1000,843,-866,-1000,-1000,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "removeValue(java.lang.Comparable):void",
            new int[]{-1000,-878,249,-1000,-1000,-565,-1000,-553,-1000,1000,-1000,65,-36,1000,475,315,1000,1000,-1000,66,-249,308,920,-14,-57,278,-1000,-1000,-690,-10,1000,861,-1000,-1000,-693,1000,-616,-450,44,-565,1000,354,1000,1000,1000,510,-736,-1000,-1000,-1000,-600,912,-60,1000,-1000,-1000,-1000,1000,1000,-1000,-1000,380,1000,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "setValue(java.lang.Comparable,double):void",
            new int[]{1000,94,509,-1000,-1000,1000,84,1000,888,-1000,229,-1000,1000,-1000,541,-143,1000,-110,808,786,1000,-254,899,224,1000,-873,1000,-1000,-442,-8,-1000,-291,550,-455,-356,1000,823,1000,413,411,1000,1000,-721,-1000,991,1000,-175,-946,-816,-464,-38,723,850,1000,508,-1000,969,1000,413,365,-952,-351,-572,1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "setValue(java.lang.Comparable,double):void",
            new int[]{266,385,94,643,-502,-543,-596,35,888,96,892,-89,-346,-400,1000,819,-1000,1000,1000,105,-326,-219,217,248,559,341,-650,255,-442,-597,686,595,428,-911,1000,230,1000,1000,413,-205,1000,1000,-970,1000,137,655,849,-702,-1000,1,-38,264,850,-592,445,-963,-1000,-85,-215,90,405,-351,-549,-749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "setValue(java.lang.Comparable,double):void",
            new int[]{-112,-1000,294,-490,637,612,-387,438,922,-1000,155,-1000,1000,1000,-1000,-1000,-826,-744,-843,849,-212,422,1000,-224,239,-1000,-834,-396,-602,596,-1000,-55,-957,940,-1000,1000,-1000,-913,581,1000,-949,18,174,-263,994,-1000,-76,-848,-811,-368,-443,1000,905,360,-766,-841,978,-4,313,-423,-574,-33,745,301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "setValue(java.lang.Comparable,java.lang.Number):void",
            new int[]{1000,-162,-261,250,-928,192,-200,582,-1000,1000,75,-975,-96,330,-1000,-45,-1000,-743,-1000,-463,-29,-370,33,230,-33,666,887,-51,1000,-1000,-488,966,478,515,-491,845,-844,-261,1000,959,-380,940,108,92,-1000,-349,615,307,-781,569,-1000,-479,610,-1000,836,-608,956,-835,968,943,939,-1000,19,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "setValue(java.lang.Comparable,java.lang.Number):void",
            new int[]{-735,814,419,-21,447,969,-724,0,-1000,-710,495,-94,620,-894,1000,-1000,-655,-1000,-1000,-133,-545,1000,265,-570,347,1000,903,664,820,-146,-473,987,-270,-564,-138,1000,153,1000,-217,1000,1000,602,-1000,376,455,-246,-981,1000,-16,-278,-1000,541,-670,-615,-88,-1000,1000,-1000,1000,-763,1000,594,365,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "setValue(java.lang.Comparable,java.lang.Number):void",
            new int[]{371,980,374,270,50,1000,-1000,-186,-1000,-351,-284,-1000,322,-745,655,-524,-1000,1000,-1000,-191,-167,744,244,-668,1000,1000,1000,270,1000,-1000,-128,1000,-635,-903,921,1000,-1000,520,-64,1000,1000,1000,-962,643,-183,-830,-569,1000,-121,454,-1000,696,-226,-804,-5,-1000,1000,-1000,-592,-351,1000,180,573,-71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "sortByKeys(org.jfree.chart.util.SortOrder):void",
            new int[]{-871,516,-491,-163,814,877,300,-689,1000,-896,1000,232,-730,794,291,282,-674,279,-565,-847,-500,1000,-1000,400,859,-1000,-420,403,147,-1000,1000,-1000,838,814,-332,-203,-227,1000,-371,-1000,-299,-949,-825,-856,941,246,542,-1000,975,-244,-1000,-581,400,-698,529,722,323,-400,-792,27,533,-242,-485,543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "sortByKeys(org.jfree.chart.util.SortOrder):void",
            new int[]{42,-1000,-381,-1000,-327,-503,-146,-331,265,1000,1000,-1000,-292,-792,-1000,-269,1000,20,137,1000,598,-1000,-454,-296,-1000,-1000,-1000,125,-31,-1000,797,273,75,1000,190,1000,865,1000,-255,1000,1000,-784,-649,-1000,220,394,-685,443,483,584,-748,1000,-1000,-1000,192,500,-509,152,-529,-1000,-1000,-224,840,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "sortByValues(org.jfree.chart.util.SortOrder):void",
            new int[]{1000,1000,-866,-660,-933,1000,980,-1000,1000,318,167,-154,51,-870,-339,-710,-188,250,-1000,-548,-854,422,-1000,-136,351,-1000,-1000,-588,-490,220,-301,-1000,-383,370,-497,-142,1000,48,-516,1000,-1000,224,-981,-1000,-323,-843,108,545,-402,-1000,-318,155,855,910,-1000,1000,847,1000,-205,-168,805,147,-490,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "sortByValues(org.jfree.chart.util.SortOrder):void",
            new int[]{1000,919,-211,-692,-848,1000,1000,-274,1000,786,177,-519,-285,-815,-1000,382,1000,518,-1000,279,-869,-399,246,785,961,-1000,-646,718,-981,285,-462,-1000,-281,227,-495,-7,1000,632,190,-389,-671,1000,-1000,-773,-633,-707,275,-278,-559,-240,-147,1000,983,-457,-1000,140,593,716,-344,1,496,518,909,96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues", "org.jfree.data.DefaultKeyedValues", "sortByValues(org.jfree.chart.util.SortOrder):void",
            new int[]{905,1000,-866,-466,-848,736,964,-681,612,786,382,-218,6,-836,-1000,451,841,250,-905,777,-847,-158,21,475,932,-769,73,535,-944,718,-552,-1000,-52,556,-364,-7,1000,215,418,-383,-671,739,-920,-877,-405,-585,108,-436,-402,-401,358,904,1000,-357,-742,225,387,372,-300,1,488,339,909,-328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(int):void",
            new int[]{-775,-710,139,1000,237,511,-975,-278,462,187,-426,855,120,693,-1000,1000,1000,-403,-63,-757,76,888,612,1000,492,1000,190,89,-1000,922,-985,-1000,-1000,855,287,-271,-1000,977,316,-324,259,818,-31,443,1000,1000,472,1000,-606,298,1000,163,-424,-1000,979,334,-739,-1000,451,-779,522,609,1000,-229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(int):void",
            new int[]{-532,-229,-480,434,112,-741,835,-106,95,774,-89,995,178,834,-971,-500,-99,120,1000,414,-93,1000,-892,141,454,-346,267,-411,-675,543,-62,-55,55,-76,-405,593,-236,823,-605,459,414,217,848,-64,604,320,465,1000,-170,-1000,330,-484,256,653,648,18,-440,-948,-78,444,-725,-497,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(int):void",
            new int[]{-256,-752,-236,967,295,-787,-779,-844,-386,611,-168,1000,-597,-578,-140,251,19,179,103,464,-548,757,595,289,580,-237,-308,933,443,-357,-537,-252,-266,-612,-1000,632,628,-113,-157,-1000,-108,818,-912,90,331,840,-371,568,-380,-807,1000,914,-143,-308,-478,1000,-458,442,155,108,24,-678,-23,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(int):void",
            new int[]{845,-711,-417,-601,-744,636,551,-1000,-1000,-232,1000,656,-1000,-1000,719,1000,303,-627,-899,785,408,121,880,170,483,78,-858,1000,-475,-577,-495,-25,-968,105,596,926,-988,85,1000,-486,950,583,-1000,-871,442,3,766,225,-426,143,1000,445,14,-1000,-92,1000,-237,1000,-857,-1000,-1000,1000,20,-860}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(int):void",
            new int[]{1000,345,-1000,-561,-896,613,-1000,100,1000,-67,1000,898,808,64,526,-150,-1000,-1000,-1000,-159,801,-476,1000,1000,435,-537,-866,-780,-2,-843,-719,1000,1000,-1000,-1000,1000,-459,49,32,1000,1000,-73,1000,116,1000,-399,1000,1000,-1000,-1000,569,-126,1000,655,-960,997,-872,1000,-1000,-1000,-1000,1000,950,-613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(java.lang.Comparable):void",
            new int[]{-649,56,-11,-1000,408,79,720,-148,-1000,491,1000,-1000,467,-98,239,-1000,622,-770,-889,1000,1000,375,-90,-1000,1000,-558,-1000,1000,-1000,483,785,1000,-364,708,-283,-294,-1000,-89,215,-1000,912,1000,-1000,445,1000,1000,404,797,105,-690,-584,-1000,-1000,1000,210,781,-1000,-89,-711,331,-788,-1000,801,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(java.lang.Comparable):void",
            new int[]{404,47,-308,-961,-245,1000,28,-392,-901,-208,846,-1000,-270,-820,-568,-1000,1000,-1000,18,1000,624,161,225,339,-320,213,-752,683,-428,647,601,957,375,1000,-498,-1000,-1000,629,-1000,-947,572,716,-400,-115,1000,398,318,-185,1000,-1000,-587,-1000,-1000,468,602,292,-1000,204,-811,-335,-2,-377,605,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(java.lang.Comparable):void",
            new int[]{-1000,-418,-586,1000,-56,542,-534,-1000,-2,-633,535,915,1000,217,-528,607,-55,1000,-430,106,737,865,-341,-63,1000,-862,492,673,674,71,-1000,1000,-281,656,-1000,1000,-987,740,-1000,-1000,1000,801,727,908,803,153,20,793,935,1000,-1000,-170,1000,628,-683,1000,-797,1000,587,-170,-951,-564,1000,56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(java.lang.Comparable):void",
            new int[]{1000,482,558,1000,-1000,338,-415,-634,991,-41,-1000,1000,-814,-966,-127,-348,-533,-607,-107,-606,-513,-624,-720,715,-762,514,542,-683,1000,-1000,-625,-1000,-60,441,-1000,1000,1000,1000,467,801,251,-310,482,-720,-343,-1000,1000,-870,66,-8,-144,2,1000,-506,-733,-215,124,159,693,-808,54,30,341,426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(java.lang.Comparable):void",
            new int[]{-32,-1000,149,1000,1000,-307,540,-1000,-1000,-1000,1000,406,1000,947,-328,-987,420,1000,-771,1000,1000,1000,449,-1000,1000,-1000,1000,1000,-1000,-47,-641,1000,-1000,793,-1000,-934,-1000,714,-1000,-1000,1000,1000,-637,1000,1000,156,-724,-566,-837,1000,-658,259,1000,923,-318,1000,-1000,255,-622,1000,-1000,-1000,825,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeValue(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{1000,-1000,-556,-1000,645,240,1000,1000,566,-1000,-816,-432,779,-431,1000,-1000,27,991,-769,157,1000,-1000,-1000,1000,-657,-113,-238,1000,1000,-1000,-1000,-1000,745,-1000,-667,-87,507,-347,1000,288,1000,828,1000,135,4,400,-800,-214,-1000,-1000,607,432,-639,931,648,-1000,-228,281,-125,1000,-898,370,-1000,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeValue(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-539,-36,358,624,275,395,-1000,-768,591,-162,-34,205,-425,-222,701,-545,-365,-258,750,-97,-186,482,-31,205,-113,700,-187,692,855,102,-647,-1000,-273,580,929,45,-446,-410,752,-755,509,-705,528,342,607,632,625,-130,987,-246,457,7,153,-478,927,5,-642,684,934,306,179,458,-207,-647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeValue(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{77,-643,-876,-121,743,247,1000,-874,-1000,-503,336,-534,-600,-885,-1000,-394,906,-1000,-1000,-325,552,-401,562,-1000,1000,-443,-164,-367,-1000,293,1000,1000,1000,-436,-763,-488,665,425,143,901,840,1000,197,-333,622,-383,-761,46,-390,483,-91,1000,-75,91,-1000,222,341,-927,-813,-864,954,670,171,112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeValue(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-904,-18,164,-561,107,395,-1000,-722,591,658,-76,256,-114,206,701,124,93,-384,-454,-256,-39,127,132,84,-1000,-203,197,692,633,102,-320,-927,87,307,119,226,-696,-1000,650,-303,-4,-1000,943,-169,-134,-514,31,-113,147,562,755,248,343,-804,620,158,-1000,-716,57,-540,179,9,-646,-23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:NA==|getColumnCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.category.DefaultCategoryDataset", "org.jfree.data.category.DefaultCategoryDataset", "removeColumn(int):void",
            new int[]{-1000,542,1000,-241,1000,336,-173,67,676,-481,799,340,-746,-1000,268,-86,713,744,9,645,-1000,-1000,73,303,-345,426,228,-668,1000,77,-590,-183,-56,-135,-46,-113,-1000,105,701,-666,-376,-884,611,923,-226,607,34,-650,569,-240,482,282,-1000,1000,-1000,-142,-524,920,575,320,1000,-189,1000,-193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:NA==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultCategoryDataset", "org.jfree.data.category.DefaultCategoryDataset", "removeColumn(int):void",
            new int[]{-773,152,1000,-813,542,893,-866,375,142,48,100,-121,185,-1000,-427,80,690,-400,-191,-1000,875,123,636,1000,815,-857,-596,580,778,1000,-634,-334,-1000,-377,214,1000,-1000,-789,397,-1000,-1000,-598,-633,970,-1000,-305,360,-1000,-1000,-216,1000,1000,-998,162,1000,171,-412,415,1000,504,461,-710,1000,243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.category.DefaultCategoryDataset", "org.jfree.data.category.DefaultCategoryDataset", "removeColumn(int):void",
            new int[]{-440,-29,247,-248,562,-232,-462,-156,-207,164,203,-195,304,1000,-56,-261,96,1000,366,32,1000,-476,-336,-500,-362,-9,303,-22,-139,722,30,-530,-279,642,-523,36,768,910,-39,823,206,348,315,-113,289,-29,-222,154,1000,-63,-708,129,-518,106,-1000,-431,370,-227,-563,198,-1000,308,-959,-995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mw==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultCategoryDataset", "org.jfree.data.category.DefaultCategoryDataset", "removeColumn(int):void",
            new int[]{62,139,-1000,243,-370,-211,-1000,705,-1000,-357,-1000,739,1000,1000,-1000,663,907,-329,865,-269,1000,-52,-510,-576,241,-1000,344,698,838,-232,827,921,-776,376,-1000,113,1000,169,814,-293,322,1000,-381,-1000,237,-1000,-625,287,683,531,-664,586,-1000,-1000,498,644,599,-1000,583,856,-678,1000,-1000,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultCategoryDataset", "org.jfree.data.category.DefaultCategoryDataset", "removeColumn(java.lang.Comparable):void",
            new int[]{-174,-302,-177,295,925,323,-1000,0,-1000,-1000,-883,-1000,827,614,-1000,45,-4,385,222,-951,-511,-370,721,-8,-712,192,-849,1000,-526,580,-911,-656,647,-1000,-50,427,-10,321,-746,953,-399,785,-71,259,-154,-1000,-401,-865,-116,-606,500,75,-1000,518,-750,-253,943,-778,720,-378,-42,180,-119,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mw==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultCategoryDataset", "org.jfree.data.category.DefaultCategoryDataset", "removeColumn(java.lang.Comparable):void",
            new int[]{716,1000,-156,-1000,-77,-211,-664,-161,-1000,251,-902,-550,692,1000,-1000,-1000,222,1000,458,-742,320,-215,1000,-583,34,-1000,1000,1000,-1000,112,1000,-348,1000,218,-315,1000,-1000,-1000,-1000,-222,13,130,1000,-1000,-562,-1000,-716,-1000,-556,-1000,1000,-1000,-1000,1000,1000,-1000,1000,-1000,438,-1000,144,174,-683,-346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultCategoryDataset", "org.jfree.data.category.DefaultCategoryDataset", "removeColumn(java.lang.Comparable):void",
            new int[]{-1000,-1000,-1000,-52,1000,626,-810,-1000,-546,-1000,517,-1000,4,196,19,462,-221,-1000,124,-789,-713,522,-332,406,-416,-64,-41,1000,-989,597,-1000,-1000,1000,-738,-1000,427,1000,1000,248,1000,-587,121,-1000,1000,-753,-801,-942,-1000,-135,372,-80,-1000,-695,578,-725,115,943,-1000,971,631,-158,-269,573,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.category.DefaultCategoryDataset", "org.jfree.data.category.DefaultCategoryDataset", "removeColumn(java.lang.Comparable):void",
            new int[]{-169,-468,1000,813,348,-502,452,725,547,-767,-540,557,492,-1000,898,-958,381,224,-1000,-759,-418,-626,-359,-13,-1000,1000,-36,-946,1000,-1000,806,-255,-364,-34,651,-697,257,603,41,659,-179,-52,-949,1000,748,1000,882,911,-270,924,-712,392,1000,-941,-1000,1000,574,-61,-1000,305,213,777,-164,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mw==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultCategoryDataset", "org.jfree.data.category.DefaultCategoryDataset", "removeValue(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{1000,682,1000,-1000,865,-1000,-46,564,-981,906,-99,0,1000,-165,823,-776,-405,-1000,513,-1000,761,-976,1000,-617,-921,-1000,-385,1000,400,-490,-1000,-778,26,-1000,-563,-317,16,-669,1000,-664,896,-1000,-868,-162,-182,131,111,-602,785,407,1000,-48,-39,701,-412,-94,-659,-103,-1000,-304,186,298,-151,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.category.DefaultCategoryDataset", "org.jfree.data.category.DefaultCategoryDataset", "removeValue(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{714,-76,728,900,209,-626,-246,618,1000,1000,-9,-690,504,838,979,250,180,39,-1000,-1000,-1000,-952,665,-788,-93,-1000,1000,1000,-65,174,990,-1000,-371,-154,1000,-353,-499,775,1000,-1000,316,540,1000,1000,-17,-559,-697,-1000,-417,-7,-1000,290,857,18,1000,1000,-802,-576,-469,244,-968,738,623,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "insertValue(int,java.lang.Comparable,double):void",
            new int[]{265,906,-948,1000,299,-711,-840,-843,-1000,248,39,181,-110,-49,1000,1000,-355,-502,-431,-487,-1000,722,-42,-555,726,-270,-1000,-669,968,155,1000,199,-604,180,-1000,-712,-703,1000,-1000,491,-72,376,117,561,197,-352,-493,1000,-1000,-359,9,559,1000,-393,1000,1000,1000,-595,324,179,-320,1000,-22,626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "insertValue(int,java.lang.Comparable,double):void",
            new int[]{-1000,400,-1000,243,-793,400,73,786,77,-734,929,-179,-522,1000,-598,630,-6,-330,120,-1000,1000,961,-642,1000,772,433,555,400,1000,400,469,1000,1000,389,672,-916,675,1000,-684,1000,-757,-1000,-503,962,304,-132,-122,-890,-200,441,-556,-214,400,138,636,-793,-1,-1000,-1000,529,55,-407,-271,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "insertValue(int,java.lang.Comparable,double):void",
            new int[]{328,209,-489,-58,-540,438,-495,654,1000,-162,413,-547,-845,-103,1000,-316,895,-1000,731,104,576,-455,-446,193,-752,-151,51,341,602,-1000,-34,912,870,-312,-201,-1000,-272,-918,-727,5,-1000,-676,408,3,192,-623,-761,-518,383,-354,-188,-1000,368,-908,843,-201,-1000,493,-97,897,686,-561,-1000,633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "insertValue(int,java.lang.Comparable,double):void",
            new int[]{-842,-130,-271,424,-891,193,-225,1000,-724,158,316,259,45,-576,745,714,1000,-237,721,-618,394,395,168,507,-1000,1000,1000,-553,944,-280,93,1000,814,-215,-914,993,-1000,-295,-1000,-136,-143,-1000,246,-32,1000,-1000,-320,-470,-902,-1000,233,-1000,96,-903,674,141,-949,-327,291,1000,1000,-821,-1000,-7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "insertValue(int,java.lang.Comparable,double):void",
            new int[]{-981,7,-1000,337,-677,-127,-857,-1000,60,342,310,519,-1000,920,561,-1000,-797,-1000,-635,945,1000,892,-1000,653,228,-1000,-1000,615,-1000,1000,113,-705,630,1000,158,1000,1000,904,1000,637,1000,1000,755,1000,-1000,-427,878,151,789,1000,-1000,1000,1000,1000,801,-182,-708,-328,-1000,-723,-1000,-556,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "insertValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{916,207,-956,-166,-19,728,504,307,-398,-561,-699,-664,341,97,-253,672,-399,545,1000,-879,312,-52,-869,-144,-985,33,212,-160,-818,972,-1000,-102,350,-671,927,-197,496,1000,1,134,47,-1000,99,-343,139,35,177,-613,324,-511,-1000,-713,-461,925,79,-1000,-65,-526,-185,911,580,942,-1000,-537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "insertValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{456,-203,1000,-1000,-288,444,1000,763,1000,1000,634,-161,-1000,-992,208,-264,-1000,-1000,-1000,1000,-492,1000,892,-397,1000,-1000,-470,906,1000,927,-554,536,-1000,-633,-581,-17,-9,-1000,1000,791,1000,1000,499,-1000,1000,-552,-654,-321,1000,846,184,16,1000,-790,841,-926,-157,957,760,-1000,-832,-147,327,630}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "insertValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{606,504,-1000,-947,-554,-436,-814,-866,180,-982,-493,1,1000,-783,479,-362,-218,513,1000,-166,1000,-1000,922,-443,-923,-778,161,-258,1000,263,885,756,-1000,-902,-999,1000,763,816,-743,-463,334,838,796,65,561,235,-423,42,-967,-102,925,-704,-262,-41,1000,-391,-966,532,566,538,-615,-621,199,747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "insertValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{-572,-977,-656,547,861,234,459,850,142,-260,945,-161,-94,-992,-388,-264,-186,346,-40,-319,-421,-106,-946,-397,572,949,1,127,-404,-798,90,356,-264,428,-110,452,741,867,-949,791,-667,-909,384,186,20,-494,897,198,-465,-211,931,828,846,-441,224,-926,-157,-646,861,-259,-726,-399,-635,-321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "insertValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{187,-527,16,847,865,914,739,533,-729,-975,-308,-167,-192,-582,-694,424,-596,289,3,-919,256,-1000,-11,-899,-350,-1000,-971,-205,1000,-649,18,203,-95,-239,685,-988,-1000,552,-728,399,-92,-396,-71,-129,-915,-999,1000,723,446,-893,850,848,-298,-807,741,17,317,-577,328,719,-759,-865,566,-444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "insertValue(int,java.lang.Comparable,java.lang.Number):void",
            new int[]{1000,851,1000,996,659,443,-497,-289,-552,-951,1000,-273,-1000,-169,-1000,274,1000,145,204,1000,1000,-722,233,282,1000,-175,417,-559,622,123,1000,-1000,-722,-516,268,905,544,-391,1000,-1000,810,1000,-596,-1000,654,1000,-99,-748,-461,722,-494,-122,-166,36,-397,1000,932,376,-655,-310,300,671,-508,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "remove(java.lang.Comparable):void",
            new int[]{-555,-1000,1000,999,-586,1000,-1000,1000,657,1000,78,657,-353,1000,438,282,1000,725,-1000,1000,-644,-1000,1000,1000,-1000,-905,-1000,-582,-1000,-742,1000,1000,873,723,-1000,1000,308,308,394,450,24,1000,249,-523,706,-12,-1000,1000,369,-430,-680,-229,-1000,-466,363,-340,455,-1000,584,-1000,-1000,-269,391,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.general.DefaultPieDataset", "org.jfree.data.general.DefaultPieDataset", "remove(java.lang.Comparable):void",
            new int[]{946,1000,608,484,-196,-1000,1000,733,647,-495,740,1000,816,-924,-585,-1000,1000,-141,341,-1000,-1000,746,-720,1000,1000,718,1000,598,516,663,861,-1000,-1000,232,1000,-1000,-451,861,-1000,199,816,-1000,899,309,-170,730,1000,-1000,1000,-1000,226,-365,1000,536,-1000,954,-951,1000,230,1000,806,-275,763,829}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeTableXYDataset", "org.jfree.data.time.TimeTableXYDataset", "remove(org.jfree.data.time.TimePeriod,java.lang.String):void",
            new int[]{-854,594,981,-979,393,76,-497,97,-219,-616,842,-993,-686,646,-381,891,-68,703,549,-812,624,357,98,822,-754,476,-405,-216,241,518,-736,354,870,918,-932,351,519,-301,252,15,-799,-351,-934,-628,-539,-547,-761,282,530,304,489,756,827,648,596,572,674,2,394,758,-576,44,383,-921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeTableXYDataset", "org.jfree.data.time.TimeTableXYDataset", "remove(org.jfree.data.time.TimePeriod,java.lang.String,boolean):void",
            new int[]{427,90,-986,-267,-478,-727,287,902,-412,453,514,617,703,708,17,817,914,-503,596,780,869,-342,-419,238,439,-560,-377,-46,-155,-359,97,871,309,-699,306,-767,-163,-726,33,-911,388,-499,-596,-969,-629,818,-477,-855,-783,-253,-125,716,-463,856,128,-525,-642,16,599,883,-204,-845,-563,-705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.xy.CategoryTableXYDataset", "org.jfree.data.xy.CategoryTableXYDataset", "remove(double,java.lang.String):void",
            new int[]{1000,868,-363,-707,1000,-1000,-1000,642,-99,132,1000,1000,282,-1000,547,-623,887,1000,-1000,-789,-1000,1000,-1000,-1000,-1000,117,702,-1000,884,-451,-1000,4,1000,1000,-72,-1000,-1000,400,372,-1000,298,1000,496,1000,1000,-1000,1000,-1000,-1000,1000,294,-636,1000,-1000,-655,1000,-365,769,603,1000,-93,-135,142,-490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.xy.CategoryTableXYDataset", "org.jfree.data.xy.CategoryTableXYDataset", "remove(double,java.lang.String):void",
            new int[]{1000,847,-866,-448,851,-344,27,1000,-1000,-738,1000,-300,1000,-1000,-1000,-546,372,724,-1000,-744,-1000,1000,-1000,-1000,-1000,-352,692,-638,1000,-1000,846,657,752,1000,-1000,3,-779,-1000,1000,-1000,896,372,-462,465,1000,-425,1000,-6,-1000,-216,937,-1000,1000,-1000,22,1000,-510,1000,1000,1000,1000,-1000,-736,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==|getSeriesCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.xy.CategoryTableXYDataset", "org.jfree.data.xy.CategoryTableXYDataset", "remove(double,java.lang.String):void",
            new int[]{651,285,-121,-308,1000,1000,290,564,-381,449,1000,525,984,-1000,972,473,35,888,-1000,-777,-1000,-444,-1000,-1000,-1000,-190,212,-1000,919,-1000,-211,532,630,414,-1000,-1000,-1000,1000,734,-861,-991,1000,-920,1000,1000,-1000,660,168,-1000,-62,751,271,955,-965,-387,1000,-301,1000,1000,1000,1000,-686,842,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.xy.CategoryTableXYDataset", "org.jfree.data.xy.CategoryTableXYDataset", "remove(java.lang.Number,java.lang.String,boolean):void",
            new int[]{221,213,-346,-896,-1000,-872,-1000,-101,1000,40,1000,-1000,497,1000,-1000,222,1000,74,-252,-369,-1000,928,1000,-305,-1000,1000,815,-1000,377,1000,-821,1000,1000,310,1000,-1000,1000,1000,-1000,-808,-718,-770,828,1000,1000,-687,1000,1000,909,-1000,-140,-1000,-1000,-1000,-1000,89,339,657,142,-724,-1000,462,379,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.xy.CategoryTableXYDataset", "org.jfree.data.xy.CategoryTableXYDataset", "remove(java.lang.Number,java.lang.String,boolean):void",
            new int[]{152,-321,-691,-98,-1000,-1000,-1000,-491,849,140,1000,-733,1000,175,-473,-998,1000,46,-362,-1000,-972,1000,287,492,-1000,1000,-339,-367,368,-238,-580,1000,1000,-228,949,-302,1000,565,-1000,-995,-1000,109,774,1000,897,-375,746,1000,211,-622,-158,-1000,-519,-1000,-246,1000,922,432,-974,-441,-1000,1000,-165,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.xy.CategoryTableXYDataset", "org.jfree.data.xy.CategoryTableXYDataset", "remove(java.lang.Number,java.lang.String,boolean):void",
            new int[]{-389,1000,-288,-700,71,1000,1000,232,423,-878,-1000,-55,-1000,1000,-227,1000,11,229,-1000,1000,-643,671,-770,-1000,-16,1000,-1000,-928,-603,1000,-721,-171,-1000,1000,-1000,1000,-829,1000,-830,-1000,1000,-1000,-1000,-1000,1000,-21,-1000,-1000,-199,-1000,550,-605,-1000,1000,626,876,807,1000,1000,-1000,2,-420,1000,788}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "addValue(java.lang.Number,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-614,1000,-157,-1000,462,-723,265,770,-338,1000,-517,-1000,-279,-678,1000,-1000,527,696,-456,-1000,248,-721,-861,300,-915,672,-526,830,-1000,563,167,-115,-1000,1000,-723,540,689,-1000,-337,-648,-87,-442,613,-1000,-1000,-219,114,1000,-512,-1000,1000,411,-1000,-1000,663,-235,-148,270,-617,-1000,-1000,-1000,238,-392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "addValue(java.lang.Number,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{65,899,-1000,73,1000,-205,355,-496,1000,854,-852,-1000,364,-66,408,244,-342,882,-427,-629,-346,-713,510,72,-16,-403,-602,-291,-465,-38,356,316,-952,474,-153,515,-182,-1000,400,-1000,-958,-396,-548,-287,534,609,-408,656,328,-102,357,-540,-673,-249,244,44,250,61,424,50,-861,-405,-899,869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "addValue(java.lang.Number,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{272,472,-318,1000,678,-862,1000,89,90,838,-533,-1000,264,944,760,385,306,932,-840,756,-93,-383,-896,486,-704,836,938,-273,158,1000,-297,-648,-705,680,-431,-109,1000,-491,500,534,-948,-374,-282,453,-71,-819,502,-914,-872,-348,1000,461,-409,-340,633,-311,-202,301,1000,764,116,744,184,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "addValue(java.lang.Number,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{1000,302,984,-87,-1000,1000,-1000,813,-54,-74,1000,-765,511,-794,346,793,784,1000,-1000,402,-1000,-1000,1000,-1000,1000,-1000,640,-1000,-1000,-1000,-999,770,-1000,291,-1000,904,-1000,-542,1000,274,376,-11,-1000,-1000,1000,289,-1000,1000,676,-1000,1000,738,-706,759,768,1000,694,670,437,92,-560,1000,-1000,637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "addValue(java.lang.Number,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{937,-726,669,1000,6,713,389,-355,1000,-1000,400,-233,121,333,-576,998,-178,70,-843,876,-1000,236,1000,-1000,1000,-1000,-502,-1000,1000,-1000,214,491,72,-393,33,990,-345,568,397,1000,176,1000,-1000,-1000,1000,700,-493,-350,-162,640,564,-1000,-1000,-414,478,-334,91,724,-280,893,316,635,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "addValue(java.lang.Number,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{394,-920,973,-864,-369,831,-112,739,-624,755,-884,-884,-560,-205,688,-192,713,101,-1000,-480,204,-141,-652,449,-273,30,367,288,-621,266,788,-551,-359,626,176,279,210,-777,-947,-777,-541,-597,207,221,-193,-344,-150,-23,-763,-884,796,-834,-338,-812,-174,116,951,-387,46,-921,-973,-870,156,-211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "clear():void",
            new int[]{-759,-445,360,-831,511,-483,-982,-386,-936,-794,370,-404,683,-484,-738,364,587,146,425,-203,-656,-953,960,331,359,-766,-34,-483,-991,-418,-142,443,-188,-509,-730,857,936,-302,395,-558,842,916,114,-562,-144,476,332,-465,90,691,549,-192,272,-712,917,371,-229,-104,747,351,391,-644,-224,829}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "clear():void",
            new int[]{773,-1000,744,210,124,1000,-1000,-403,-624,-1000,-329,-1000,-1000,1000,-1000,696,-315,1000,1000,-1000,-1000,-479,149,1000,1000,-11,352,223,-630,-1000,-469,-630,-2,244,1000,-62,1000,-267,1000,118,1000,914,-1000,1000,-1000,-532,915,622,445,-940,782,168,1000,-698,-872,-776,1000,1000,436,-1000,1000,121,615,11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "clear():void",
            new int[]{-1000,587,-1000,-331,1000,-1000,-1000,54,-1000,-82,788,835,234,-968,414,105,-835,1000,825,1000,-1000,-1000,1000,-45,1000,-1000,1000,1000,-306,-787,998,822,1000,1000,-318,903,906,-1000,-811,1000,184,682,-1000,1000,1000,1000,-1000,648,-1000,1000,536,-373,1000,-1000,1000,1000,-1000,1000,-1000,-1000,1000,-558,-1000,-959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.DefaultKeyedValues2D|getRowCount=22:java.lang.Integer:Mg==|getColumnCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "clone():java.lang.Object",
            new int[]{-1000,-1000,-776,1000,-268,-768,-1000,1000,838,-180,-18,346,683,696,319,-1000,-1000,-664,-432,-671,28,321,128,-904,-102,1000,-129,806,653,403,985,1000,885,-666,-272,-444,437,-436,1000,1000,1000,-162,331,-983,-433,-154,-265,-162,-1000,-76,175,1000,-374,1000,200,44,-986,-685,-1000,-776,-496,319,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.DefaultKeyedValues2D|getRowCount=22:java.lang.Integer:MA==|getColumnCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "clone():java.lang.Object",
            new int[]{-1000,-895,-168,664,295,-576,-434,375,131,925,172,-650,467,1000,93,-700,-400,-248,18,-263,1000,-278,328,-530,460,971,-1000,1000,-17,88,464,670,1000,-684,-534,654,660,1000,183,977,505,-261,979,43,229,-42,-799,-404,-816,375,937,249,-855,400,462,355,487,-1000,-722,-336,-384,417,570,-690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.DefaultKeyedValues2D|getRowCount=22:java.lang.Integer:Mg==|getColumnCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "clone():java.lang.Object",
            new int[]{664,-970,-841,1000,-28,734,-1000,148,1000,1000,673,356,-3,1000,-706,-292,396,-839,-93,-168,731,-1000,1000,-1000,-1000,1000,-466,391,1000,562,-462,1000,-374,1000,374,-629,356,-469,-385,1000,1000,96,1000,-332,-527,64,-554,-211,-419,-1000,980,622,468,1000,-1000,-185,-842,-528,517,-1000,-463,-148,134,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.DefaultKeyedValues2D|getRowCount=22:java.lang.Integer:MQ==|getColumnCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "clone():java.lang.Object",
            new int[]{-518,-1000,-766,568,-926,-1000,380,954,-638,89,-686,-890,609,614,279,-1000,-752,-1000,-176,-1000,424,321,-341,-1000,1000,1000,-1000,1000,-123,966,1000,-1000,954,-1000,-1000,573,1000,1000,1000,995,1000,-1000,277,232,-344,-906,-1000,-1000,-1000,1000,322,473,-1000,292,1000,44,-666,-1000,-1000,192,-383,-1000,1000,618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.DefaultKeyedValues2D|getRowCount=22:java.lang.Integer:MA==|getColumnCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "clone():java.lang.Object",
            new int[]{-1000,-1000,-151,-24,-421,-844,-903,1000,46,1000,-373,-953,843,190,-245,-1000,-927,-580,-874,-777,379,-102,447,-904,-102,889,-1000,886,-153,441,1000,-130,391,-787,159,506,639,924,1000,826,696,264,358,-1,143,-154,-423,-770,-1000,20,697,1000,-1000,439,555,-54,392,-685,-1000,21,-429,55,1000,303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "equals(java.lang.Object):boolean",
            new int[]{781,-877,884,184,232,935,1000,403,-674,419,582,38,-590,-104,-978,-262,658,1000,-901,92,-149,-85,316,529,-138,-1000,-594,264,-626,-787,906,-104,-657,110,-208,55,-6,266,-226,-207,466,544,-389,50,-449,134,570,1000,1000,477,-1000,367,-1000,255,-537,-711,21,-68,638,155,177,1000,97,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "equals(java.lang.Object):boolean",
            new int[]{374,478,-21,-728,-95,417,-1000,1000,1000,963,1000,1000,-855,1000,276,-1000,230,1000,5,1000,1000,-1000,1000,-1000,239,1000,-890,-741,-1000,-704,-138,-1000,-1000,1000,-1000,1000,-1000,867,-880,-964,-406,-719,-629,-118,-1000,545,507,224,-893,-546,-1000,-89,-489,-1000,-319,-1000,-776,-1000,800,523,-243,1000,165,342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "equals(java.lang.Object):boolean",
            new int[]{43,-922,309,-199,-174,689,15,-288,-549,109,-525,290,30,-582,-259,-7,146,677,-999,276,164,-361,316,-543,254,-95,-594,-282,368,-787,1000,-188,302,110,-354,164,-114,415,-226,-133,-68,48,-703,544,197,134,735,687,1000,-49,-1000,169,-925,1000,139,-711,271,-426,-49,217,-148,512,305,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "equals(java.lang.Object):boolean",
            new int[]{-1000,-593,-1000,509,-1000,-715,-305,305,-688,258,57,127,-619,1000,1000,-1000,-155,800,506,87,420,1000,-638,192,-350,563,-935,-167,465,-164,1000,-47,1000,-1000,13,719,-996,29,338,-323,322,-393,72,-400,435,-724,1000,1000,910,400,863,551,1000,722,-691,-1000,-459,-188,-147,974,282,-600,74,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnCount():int",
            new int[]{-1000,-341,-1000,-661,440,552,769,-513,-704,90,-880,234,1000,-1000,-671,427,-219,-434,1000,-125,-1000,174,-788,91,-374,-1000,-400,-156,-42,407,-686,-919,205,-395,-712,-1000,-101,1000,-394,-56,-74,177,-767,614,708,223,-500,1000,-87,346,816,149,789,-698,104,-1000,-817,-274,644,-269,-1000,-1000,97,761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnCount():int",
            new int[]{1000,1000,248,65,752,-663,-58,832,70,-778,397,-1000,341,752,145,365,-563,-1000,834,-1000,-254,249,427,-164,-749,-134,-314,-319,-347,549,-792,191,572,29,99,113,1000,1000,615,1000,-988,70,-187,-631,337,-454,602,-714,-852,-688,480,157,-675,-48,674,-409,-948,1000,169,-1000,464,-811,-530,-196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnCount():int",
            new int[]{938,947,86,618,826,685,78,919,-211,-638,357,-751,342,972,88,126,-181,179,694,1000,-67,394,427,-139,-888,-1000,-81,-319,-419,362,-968,191,759,338,71,-276,478,917,726,166,-1000,161,-187,-533,185,-472,602,-714,-797,-448,195,62,494,496,674,-399,-1000,850,169,-1000,331,-654,-703,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnCount():int",
            new int[]{-875,707,-783,-682,1000,-125,-1000,693,299,350,526,753,445,-272,912,-322,-1000,-274,-329,-637,-750,115,-754,-233,-414,3,1000,456,-455,885,-202,441,630,-1000,-645,-644,36,654,-852,947,380,601,-660,840,348,-1000,613,215,200,1000,778,1000,902,-63,158,-804,-372,-489,-198,-534,-976,-752,-198,-62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnCount():int",
            new int[]{-784,-753,1000,-467,638,957,-114,-1000,-931,394,-425,-226,550,-858,-236,1000,290,255,788,821,-1000,-363,-959,-556,-158,978,377,1000,-1000,1000,58,694,-134,-457,-511,780,-773,843,-841,191,843,401,-886,171,627,-200,-1000,1000,-1000,820,-154,849,-235,372,1000,-1000,-1000,-736,241,178,1000,174,45,-546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnCount():int",
            new int[]{259,1000,189,166,1000,37,-1000,734,-55,1000,941,-221,-412,-518,1000,-1000,-48,-171,-170,-223,-151,211,-47,599,264,170,859,1000,-365,131,-337,370,417,-1000,-345,724,-272,231,-243,-220,760,457,294,-42,-994,-761,768,242,1000,1000,-931,-234,-166,1000,459,-597,140,-709,-304,-674,729,705,-749,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnIndex(java.lang.Comparable):int",
            new int[]{4,162,-261,-545,762,-817,1000,-1000,1000,614,676,1000,-923,533,842,-498,1000,-1000,-542,490,-1000,-1000,858,-1000,1000,160,1000,-79,1000,-1,-584,1000,-1000,1000,-737,216,-485,517,-1000,352,536,788,-557,-305,25,-609,-824,-170,1000,-288,188,137,-966,-1000,680,-886,661,483,1000,1000,-1000,733,-985,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnIndex(java.lang.Comparable):int",
            new int[]{-1000,-513,232,-736,1000,1000,1000,-524,1000,799,-856,1000,885,430,348,588,654,-54,783,-984,-370,-257,-517,933,428,685,374,-502,227,1000,1000,243,-875,-917,-1000,-885,-1000,-121,-562,1000,-225,391,-1000,-846,1000,-707,580,1000,-174,-1000,47,114,616,333,643,1000,672,-198,1000,1000,48,280,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnIndex(java.lang.Comparable):int",
            new int[]{-1000,209,18,-736,743,552,833,-1000,1000,855,216,-12,188,-1000,-210,801,654,-287,783,-221,-1000,-1000,452,-501,445,-80,436,-539,-486,956,962,486,-44,1000,-605,448,-616,-245,-412,1000,-225,-90,-860,-1000,-513,-908,163,-243,-515,-1000,-815,1000,-297,-284,-566,145,267,-198,956,1000,-843,62,389,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnIndex(java.lang.Comparable):int",
            new int[]{1000,759,-599,64,-1000,-416,1000,1000,-1000,-1000,-443,1000,588,238,254,218,1000,639,413,-99,1000,1000,-1000,533,-26,-29,-225,-693,-1000,914,-299,-1000,1000,-1000,-448,-1000,-799,747,1000,-1000,980,876,-754,420,861,1000,907,-1000,-1000,-553,-385,-907,919,1000,-709,781,-840,-1000,-1000,1000,1000,-1000,-1000,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnIndex(java.lang.Comparable):int",
            new int[]{185,316,-681,-713,-343,619,-155,1000,-821,-502,151,428,-392,-240,596,-378,740,786,122,-505,925,1000,-1000,-1000,428,248,-105,1000,-18,-389,1000,-785,-595,-917,-611,-1000,-901,1000,804,206,111,450,-601,290,424,673,22,-353,-642,1000,1000,-1000,1000,939,115,-150,37,-1000,-598,-630,1000,-465,-131,-293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnIndex(java.lang.Comparable):int",
            new int[]{118,1000,-621,-121,-872,738,-208,-5,-2,232,462,-155,392,-567,-775,-538,-1000,-524,-1000,956,835,266,208,1000,461,191,577,718,195,-253,190,221,1000,-758,18,-638,-1000,157,241,-310,-129,472,-732,-1000,-131,-412,-416,-528,551,-912,852,-520,113,404,1000,-1000,-1000,-782,-194,75,439,344,978,-534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnKey(int):java.lang.Comparable",
            new int[]{-955,933,1000,-716,481,-1000,-386,203,935,101,259,-16,230,1000,1000,446,808,-1000,-1000,954,-1000,-322,-400,-581,688,-380,316,-215,35,1000,-1000,-882,-881,1000,-531,-1000,-256,1000,245,-362,-352,-1000,-608,41,-758,662,1000,296,-721,-1000,357,192,-209,661,-400,985,-81,1000,-1000,63,147,-163,1000,319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnKey(int):java.lang.Comparable",
            new int[]{-1000,1000,734,-1000,1000,-473,282,-550,1000,-878,-292,1000,-188,542,1000,-663,-679,-1000,-1000,-155,-1000,-1000,1000,1000,-1000,835,-601,809,1000,-1000,-1000,1000,-192,1000,-1000,-1000,1000,-609,-1000,-1000,-1000,-1000,-1000,-1000,489,1000,-1000,1000,-751,870,1000,477,258,-603,1000,1000,-1000,1000,1000,-621,-1000,511,1000,-263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnKey(int):java.lang.Comparable",
            new int[]{214,-707,1000,234,-125,832,-1000,-284,-857,132,1000,867,543,-174,1000,-648,-534,-1000,-1000,1000,-1000,246,-1000,1000,416,876,-1000,1000,-836,1000,-1000,1000,264,1000,163,790,-1000,1000,269,409,-1000,-1000,-121,-895,513,977,1000,1000,1000,-702,966,-766,-119,-11,-1000,876,-1000,-97,-1000,-281,213,-170,1000,693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnKey(int):java.lang.Comparable",
            new int[]{-60,1000,-256,-1000,1000,-10,-1000,-180,557,-398,-803,1000,70,-185,1000,-308,-590,-810,-1000,1000,-1000,-1000,491,1000,-863,323,-37,60,668,-1000,-1000,567,98,1000,-921,-711,1000,776,-1000,-1000,-826,-1000,-218,-481,507,1000,-860,579,-1000,938,1000,1000,14,-361,896,563,-476,966,1000,-19,-444,410,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnKey(int):java.lang.Comparable",
            new int[]{289,-1000,714,1000,-1000,-916,1000,1000,-157,-264,136,-1000,-1000,635,137,-730,691,1000,1000,-1000,570,291,-203,-1000,-366,-785,718,-948,1000,897,1000,-1000,-320,-1000,611,-1000,-958,-178,748,507,767,536,-1000,55,-800,-1000,449,-1000,-1000,525,-1000,-680,1000,-878,1000,-671,438,-1000,-978,624,-531,-673,-119,889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnKeys():java.util.List",
            new int[]{-1000,1000,413,-1000,-305,-803,-153,226,106,492,186,558,-857,-1000,775,125,-236,-857,-211,769,-376,707,-724,-230,-14,20,563,-126,-197,-1000,-16,209,-448,1000,-273,-119,96,-583,-1000,-1000,-381,-286,-1000,1000,659,-1000,17,-769,-554,568,816,404,-324,-5,-900,536,-548,26,-35,201,53,817,-24,440}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnKeys():java.util.List",
            new int[]{-1000,379,-104,494,1000,-321,-814,-1000,983,982,-1000,1000,41,-223,311,-644,-1000,-173,-123,-877,1000,987,-930,-477,-518,-365,683,828,547,-763,-337,827,-530,765,475,-403,1000,-177,-1000,-1000,179,-474,-1000,572,-270,57,1000,651,-978,279,616,1000,950,-412,-578,-247,-943,-700,1000,472,384,882,818,-536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnKeys():java.util.List",
            new int[]{-105,-220,-837,832,7,713,-691,-279,200,-644,-928,-656,300,202,-371,163,-117,578,410,252,-348,-815,120,642,514,112,-115,-278,-1000,516,-842,-446,-276,25,538,363,-1000,-59,1000,242,-50,-35,-144,-1000,573,48,396,184,73,-898,-143,-411,-282,379,25,179,995,-470,-332,869,193,-214,-1000,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnKeys():java.util.List",
            new int[]{-560,376,503,95,-1000,-1000,-1000,-1000,-897,1000,1000,1000,-1000,-395,1000,-720,-413,-424,-299,-661,334,1000,-1000,175,-80,549,906,677,8,-1000,-412,653,-298,899,123,-1000,1000,-536,-835,585,-1000,234,-35,1000,1000,-734,-49,-1000,381,-1000,50,-888,34,-1000,89,975,-1000,-274,-361,104,1000,21,946,181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnKeys():java.util.List",
            new int[]{899,339,1000,898,-1000,254,602,807,94,566,1000,238,406,223,-451,-1000,157,-1000,-1000,-1000,-597,309,-364,-1000,-875,672,245,273,-171,-1000,186,469,-708,298,-83,18,-283,-315,354,-67,225,-1000,325,-689,1000,-776,570,-1000,-494,-439,-536,-347,-181,-664,-769,-1000,-1000,446,-13,-3,308,-888,57,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getColumnKeys():java.util.List",
            new int[]{112,-250,433,-456,-490,-661,1000,938,798,-724,667,-149,-401,-572,-317,176,-916,-1000,-1000,-543,-1000,-68,96,-1000,-72,38,-124,-289,-129,-312,273,-164,1000,881,-85,345,-388,-351,-1000,-1000,1000,-1000,-478,-568,-11,255,59,-77,-587,457,124,1000,-826,-416,-524,-1000,-300,1000,150,953,639,-260,151,329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowCount():int",
            new int[]{-667,963,-219,134,-1000,-769,1000,-1000,-519,1000,147,199,1000,-590,502,-1000,-389,428,-1000,774,-672,-1000,209,626,-1000,-71,539,-544,426,-1000,-575,459,-414,-600,1000,502,-839,-739,-721,-756,-1000,1000,1000,301,819,-1000,-20,696,-1000,-656,922,994,1000,-955,629,1000,1000,877,622,-775,-488,750,584,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowCount():int",
            new int[]{-71,-599,-165,-536,928,51,-128,-274,492,806,104,479,-683,258,490,475,354,67,-184,-495,-842,388,807,-707,743,221,285,1000,-1000,-1000,-128,-880,-1000,462,-335,819,-339,-175,253,-557,567,-1000,-969,-441,-19,-15,-865,1000,-752,-543,-529,208,-380,-256,-779,-158,490,264,-9,-1000,-1000,-117,-207,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowCount():int",
            new int[]{1000,-401,-808,-937,97,-1000,-475,1,1000,-188,-50,-19,267,591,-977,751,-1000,166,-668,351,1000,-660,1000,1000,505,1000,184,20,651,674,-1000,-574,-400,63,-167,-713,-104,193,-999,-346,-184,424,88,-458,285,-14,241,768,860,-45,1000,665,590,-1000,1000,523,678,584,841,141,808,225,85,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowCount():int",
            new int[]{-728,990,-176,-1000,-95,-155,-468,427,-10,-1000,175,-227,-80,755,500,-1000,868,697,-328,-515,-447,1000,772,358,489,-389,126,1000,-1000,1000,449,-27,1000,240,516,290,394,1000,859,-577,43,105,-555,396,-1000,26,-291,-409,170,-41,-791,144,-910,578,-386,562,-26,36,-605,227,-316,720,183,480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowCount():int",
            new int[]{302,1000,-37,751,-1000,-345,1000,-1000,-427,1000,806,993,355,-1000,-626,-1000,614,320,-636,1000,-1000,-190,275,588,-1000,-1000,801,-160,-651,-1000,-1000,93,-1000,-1000,1000,889,-1000,-361,-1000,-985,-1000,400,1000,-700,894,-774,-700,836,-1000,-646,821,752,226,-1000,-96,877,392,-29,-60,-530,-1000,766,597,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowIndex(java.lang.Comparable):int",
            new int[]{1000,353,308,9,367,-115,-648,180,-521,-189,294,1000,-75,682,-362,1000,110,-56,-823,-52,-857,216,1000,284,-602,-75,-1000,719,318,-956,-7,-20,-311,-28,205,6,26,279,1000,26,-336,649,400,-633,-645,636,-45,-83,782,817,200,-131,-356,-648,184,586,326,-306,501,-855,666,-356,415,-586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowIndex(java.lang.Comparable):int",
            new int[]{-1000,272,-421,841,64,-1000,1000,902,-494,608,294,1000,1000,682,-362,1000,-225,-1000,-1000,-52,-345,268,888,1000,1000,1000,-948,670,318,-438,729,-400,371,237,-391,-575,-27,-575,1000,-1000,-1000,649,1000,-1000,-576,1000,-508,-502,336,817,503,1000,-356,-1000,1000,541,767,275,-1000,-202,979,1000,415,-421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowIndex(java.lang.Comparable):int",
            new int[]{-196,-441,-441,809,392,-673,215,1000,-1000,1000,289,158,0,-331,-181,1000,831,-1000,1000,-1000,-997,1000,667,1000,385,-1000,-1000,441,340,-559,95,316,1000,641,-1000,-1000,-1000,-587,1000,-1000,-364,1000,-87,-1000,-881,-678,-48,836,1000,-586,569,1000,-1000,-292,516,590,1000,460,-1000,-99,913,-877,155,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTI=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowIndex(java.lang.Comparable):int",
            new int[]{-1000,-821,-586,-92,-393,483,-306,1000,-1000,926,-84,-970,15,-1000,-1000,-1000,250,64,1000,127,1000,412,-1000,1000,930,1000,747,-1000,567,-221,1000,603,294,866,553,-1000,76,124,-1000,-1000,543,235,400,-755,909,-182,-1000,-1000,-917,-1000,-788,948,442,-1000,1000,655,-1000,164,-293,921,-882,664,-1000,-693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowIndex(java.lang.Comparable):int",
            new int[]{-786,167,101,409,-448,15,1000,667,-838,483,-367,1000,1000,1000,-920,296,-195,-840,-1000,-86,386,-223,724,1000,1000,1000,-447,453,116,-577,839,471,137,-271,-269,-825,-313,-244,490,-1000,-948,323,1000,-1000,-363,1000,27,-410,229,1000,763,827,-125,-1000,1000,802,659,-186,-1000,-945,274,799,765,-309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTI=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowIndex(java.lang.Comparable):int",
            new int[]{312,387,-338,54,-847,-175,528,-429,1000,-1000,-630,-766,1000,-846,37,-1000,-672,287,841,1000,1000,-1000,125,-673,-441,837,1000,-1000,-952,1000,-1000,580,-268,1000,951,-1000,-356,993,-1000,184,-355,-324,-1000,1000,1000,-400,-1000,-888,-972,-359,-325,-1000,399,-174,431,222,-453,665,-31,231,-837,-358,459,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowKey(int):java.lang.Comparable",
            new int[]{-1000,174,989,55,-1000,1000,649,1000,-14,164,-306,342,-175,-142,-343,26,166,593,-955,841,-763,422,-148,519,805,167,-93,-320,1000,104,225,-521,907,860,-1000,1000,1000,-1000,465,349,1000,1000,-223,707,26,627,68,-651,1000,-461,-1000,-117,-1000,589,165,937,778,1000,-746,-1000,-659,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowKey(int):java.lang.Comparable",
            new int[]{-239,353,446,-537,-1000,1000,-186,809,-389,192,354,1000,-11,-89,-827,-926,-598,636,709,276,-521,992,33,-597,990,-349,-712,951,-594,-812,209,244,509,1000,409,-750,1000,-564,156,563,-113,793,-1000,1000,339,-702,567,-1000,994,964,-159,593,-1000,261,-798,671,-632,1000,211,41,-888,255,-574,-441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5Mw==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowKey(int):java.lang.Comparable",
            new int[]{-403,-217,1000,68,-1000,-287,281,423,157,317,-284,-130,-742,669,-960,-197,837,941,-955,144,1000,858,257,-46,259,1000,-464,-153,1000,780,568,-1000,744,529,-400,1000,1000,-713,74,130,694,847,-746,-339,-125,-295,-221,-134,-400,-622,-427,-382,-80,600,81,1000,334,416,-1000,-249,-307,-452,1000,-664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowKey(int):java.lang.Comparable",
            new int[]{-74,585,723,-697,-369,-488,728,999,806,179,-592,-866,617,496,-469,-218,1000,-8,-1000,651,1000,422,1000,1000,-1000,70,-144,-771,1000,1000,458,-1000,42,-1000,-1000,1000,1000,-562,23,511,905,586,414,-910,-1000,208,930,61,533,-269,-402,59,-1000,795,834,1000,1000,-836,-582,-1000,693,-902,-615,-950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowKeys():java.util.List",
            new int[]{1000,844,-876,-1000,-615,194,82,-1000,191,-1000,-381,-1000,1000,798,325,29,1000,744,1000,-1000,118,-1000,348,7,931,-1000,601,1000,734,767,1000,-505,1000,-497,778,441,928,-979,-932,167,-89,1000,300,-908,-246,393,734,-1000,885,1000,929,-1000,-1000,1000,694,-929,334,-1000,60,859,1000,-619,466,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowKeys():java.util.List",
            new int[]{859,-1000,359,-1000,787,-472,1000,-727,1000,-74,-959,1000,-423,-702,552,-570,-1000,-236,881,-180,-1000,463,300,1000,165,-503,-599,342,-1000,-1000,-597,1000,-208,-1000,-802,1000,208,351,-253,-154,-284,-328,33,-32,1000,515,-37,-442,-1000,916,34,175,-85,-1000,-322,-740,276,748,-391,1000,997,725,-66,53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowKeys():java.util.List",
            new int[]{834,-993,616,-931,959,-825,526,160,562,-74,-931,431,-410,-268,352,-223,-843,-157,415,313,-844,613,-710,713,434,-718,-692,-426,-620,-765,-446,548,-792,-782,-180,708,-696,467,536,360,-431,245,-279,-647,844,-88,246,-163,-746,315,391,366,-446,-985,56,-741,436,693,-194,224,-850,-103,417,378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowKeys():java.util.List",
            new int[]{701,-881,5,-927,344,-994,531,731,38,555,-584,-363,26,735,530,917,-716,64,-198,-565,-888,-1000,221,133,734,-905,609,-1000,-875,831,662,690,-160,-310,725,244,-659,465,670,158,-312,716,591,-311,576,-232,-37,-771,369,292,589,293,-698,144,249,-802,-90,-732,318,-1000,-34,10,1,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getRowKeys():java.util.List",
            new int[]{-77,-993,-1000,593,983,1000,1000,-1000,-140,1000,611,1000,-733,-1000,190,302,-1000,-1000,415,-1000,-817,1000,1000,-1000,236,1000,1000,1000,-1000,-1000,-200,812,291,-782,-851,1000,1000,155,-1000,-1000,763,-1000,386,1000,1000,1000,-1000,-664,-1000,257,-1000,816,-60,-836,-1000,-181,-909,898,-113,753,-1000,1000,31,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(int,int):java.lang.Number",
            new int[]{-1000,100,-637,695,-461,1000,-581,189,695,-1000,-252,-241,312,-770,189,204,-312,1000,253,49,-192,656,-690,298,1000,480,-366,-186,482,1000,723,253,1000,-204,-654,-370,472,-514,735,-751,379,-583,-893,-127,656,120,-1000,1000,-532,-383,-1000,164,1000,-307,-95,-533,51,494,-1000,-190,1000,-308,-420,67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(int,int):java.lang.Number",
            new int[]{-302,-75,-198,529,-465,-136,-958,-810,-617,858,484,-862,766,-677,-457,-181,-628,-500,-30,-284,29,608,974,531,961,-930,-576,-899,-596,-588,453,268,-132,-81,648,-846,-801,-373,-710,-428,917,-164,-925,706,-710,-72,157,557,-916,40,448,206,-12,-153,658,-556,-638,-407,741,-60,-873,353,-872,815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(int,int):java.lang.Number",
            new int[]{-238,172,-716,-1000,414,-120,-487,-728,1000,-544,566,1000,454,118,1000,845,-703,1000,-372,-586,615,-847,-515,112,926,-484,-859,232,1000,781,877,205,369,968,-561,439,733,-991,-830,119,-256,-914,-243,-1000,123,-464,-922,-214,-661,811,-973,1000,1000,70,-1000,566,469,1000,-25,751,1000,-740,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(int,int):java.lang.Number",
            new int[]{-1000,-175,563,-426,302,437,1000,-721,-1000,1000,-570,667,1000,-1000,-275,1000,384,-1000,535,49,1000,225,1000,168,-666,-822,1000,1000,233,1000,1000,-1000,1000,1000,-1000,-1000,-387,996,27,526,859,-583,-1000,-127,594,-655,-1000,772,311,-383,-1000,1000,953,-1000,226,-1000,773,452,-901,1000,711,-921,-420,67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(int,int):java.lang.Number",
            new int[]{-1000,46,849,1000,-779,805,388,-831,-1000,1000,-1000,-1000,518,-1000,-474,359,308,-1000,-30,912,270,1000,-867,590,-675,-1000,324,65,-1000,-82,-598,-1000,-820,-110,471,-1000,-728,1000,-559,254,1000,-381,-733,1000,-572,-591,-180,1000,-581,159,1000,-842,-565,-1000,133,261,252,-878,636,837,-303,119,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(int,int):java.lang.Number",
            new int[]{-912,1000,-716,118,96,1000,968,1000,36,-486,-1000,-259,-402,-51,-836,352,1000,-1000,1000,-453,96,1000,-328,-918,-1000,891,214,-194,-201,1000,-322,-838,740,-271,-1000,1000,-674,1000,1000,-27,-127,-610,402,263,445,-325,-1000,1000,1000,-695,-1000,-582,391,-759,-421,947,-495,-124,-546,4,-299,49,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(int,int):java.lang.Number",
            new int[]{-1000,742,-376,-212,315,1000,998,400,-1000,815,-1000,-162,368,-243,-456,405,892,-1000,1000,433,816,740,961,-668,-1000,853,271,1000,-530,935,414,-1000,278,-527,-263,74,-535,1000,878,45,813,-501,-278,775,921,-1000,-1000,1000,761,33,-752,259,166,-1000,-200,-15,807,-388,-402,662,-1000,-157,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{405,-60,709,-985,-1000,-224,-1000,-1000,1000,-440,-89,1000,-583,888,1000,-951,-608,-381,310,1000,1000,-1000,389,-471,-566,243,372,-1000,-1000,1000,-375,-14,-249,-126,-468,184,336,-309,-1000,564,288,-497,905,-975,130,-1000,192,1000,1000,-179,718,-467,-1000,-1000,192,541,778,1000,-656,1000,-64,-993,-1000,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{581,163,640,994,-1000,-1000,-1000,-1000,1000,-459,-920,1000,-966,1000,1000,-1000,-725,25,-117,-1000,1000,-706,1000,442,-1000,1000,-95,-1000,-564,1000,912,56,253,-45,-1000,-787,-107,180,-1000,1000,447,680,990,-1000,-785,616,806,1000,1000,396,168,1000,-53,129,-125,39,483,821,-612,1000,-1000,203,-1000,-360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{523,160,-161,-1000,-1000,-1000,136,163,809,-408,211,1000,534,-1000,726,-445,-897,42,1000,-678,-282,327,14,-580,1000,1000,-944,-1000,-405,959,-1000,1000,354,-299,-570,1000,-867,-407,301,843,-184,-1000,1000,820,893,-1000,-1000,466,170,255,352,-850,-333,-131,-140,976,223,-380,231,161,-783,-1000,-1000,-694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{1000,429,321,134,-465,827,430,466,-845,-11,-685,106,974,-876,-114,899,857,-448,-393,869,337,-723,902,-305,109,303,138,1000,167,-725,67,-123,1000,-202,-197,37,279,810,-224,498,1000,-619,-111,-1000,-417,102,-304,4,-381,-569,17,-203,-63,172,13,37,-328,-785,932,-453,-57,-258,957,-125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{720,238,-261,-865,1000,-1000,741,1000,-122,-468,1000,-1000,-1000,-1000,-36,75,-273,939,-1000,-1000,-1000,-286,-1000,652,-1000,-1000,-1000,925,-1000,1000,-1000,-1000,1000,-1000,1000,1000,-107,336,178,1000,-441,-685,-494,-1000,248,-473,-565,-526,314,-400,168,-1000,-53,-120,1000,-154,203,1000,911,-610,-1000,385,498,-850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{296,490,-46,-1000,252,-1000,-215,1000,-845,-11,1000,-981,-1000,-919,506,-602,-1000,-235,-444,-60,-1000,-814,-1000,958,-891,-318,138,-22,167,1000,57,-123,170,1000,-197,37,664,154,-86,1000,-486,-627,732,-298,1000,-427,99,-1000,1000,1000,-1000,-997,416,419,1000,-632,969,1000,559,83,-897,-46,179,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(int):void",
            new int[]{-775,-710,139,1000,237,511,-975,-278,462,187,-426,855,120,693,-1000,1000,1000,-403,-63,-757,76,888,612,1000,492,1000,190,89,-1000,922,-985,-1000,-1000,855,287,-271,-1000,977,316,-324,259,818,-31,443,1000,1000,472,1000,-606,298,1000,163,-424,-1000,979,334,-739,-1000,451,-779,522,609,1000,-229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(int):void",
            new int[]{-532,-229,-480,434,112,-741,835,-106,95,774,-89,995,178,834,-971,-500,-99,120,1000,414,-93,1000,-892,141,454,-346,267,-411,-675,543,-62,-55,55,-76,-405,593,-236,823,-605,459,414,217,848,-64,604,320,465,1000,-170,-1000,330,-484,256,653,648,18,-440,-948,-78,444,-725,-497,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(int):void",
            new int[]{-256,-752,-236,967,295,-787,-779,-844,-386,611,-168,1000,-597,-578,-140,251,19,179,103,464,-548,757,595,289,580,-237,-308,933,443,-357,-537,-252,-266,-612,-1000,632,628,-113,-157,-1000,-108,818,-912,90,331,840,-371,568,-380,-807,1000,914,-143,-308,-478,1000,-458,442,155,108,24,-678,-23,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(int):void",
            new int[]{845,-711,-417,-601,-744,636,551,-1000,-1000,-232,1000,656,-1000,-1000,719,1000,303,-627,-899,785,408,121,880,170,483,78,-858,1000,-475,-577,-495,-25,-968,105,596,926,-988,85,1000,-486,950,583,-1000,-871,442,3,766,225,-426,143,1000,445,14,-1000,-92,1000,-237,1000,-857,-1000,-1000,1000,20,-860}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(int):void",
            new int[]{1000,345,-1000,-561,-896,613,-1000,100,1000,-67,1000,898,808,64,526,-150,-1000,-1000,-1000,-159,801,-476,1000,1000,435,-537,-866,-780,-2,-843,-719,1000,1000,-1000,-1000,1000,-459,49,32,1000,1000,-73,1000,116,1000,-399,1000,1000,-1000,-1000,569,-126,1000,655,-960,997,-872,1000,-1000,-1000,-1000,1000,950,-613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(java.lang.Comparable):void",
            new int[]{-649,56,-11,-1000,408,79,720,-148,-1000,491,1000,-1000,467,-98,239,-1000,622,-770,-889,1000,1000,375,-90,-1000,1000,-558,-1000,1000,-1000,483,785,1000,-364,708,-283,-294,-1000,-89,215,-1000,912,1000,-1000,445,1000,1000,404,797,105,-690,-584,-1000,-1000,1000,210,781,-1000,-89,-711,331,-788,-1000,801,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(java.lang.Comparable):void",
            new int[]{404,47,-308,-961,-245,1000,28,-392,-901,-208,846,-1000,-270,-820,-568,-1000,1000,-1000,18,1000,624,161,225,339,-320,213,-752,683,-428,647,601,957,375,1000,-498,-1000,-1000,629,-1000,-947,572,716,-400,-115,1000,398,318,-185,1000,-1000,-587,-1000,-1000,468,602,292,-1000,204,-811,-335,-2,-377,605,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(java.lang.Comparable):void",
            new int[]{-1000,-418,-586,1000,-56,542,-534,-1000,-2,-633,535,915,1000,217,-528,607,-55,1000,-430,106,737,865,-341,-63,1000,-862,492,673,674,71,-1000,1000,-281,656,-1000,1000,-987,740,-1000,-1000,1000,801,727,908,803,153,20,793,935,1000,-1000,-170,1000,628,-683,1000,-797,1000,587,-170,-951,-564,1000,56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(java.lang.Comparable):void",
            new int[]{1000,482,558,1000,-1000,338,-415,-634,991,-41,-1000,1000,-814,-966,-127,-348,-533,-607,-107,-606,-513,-624,-720,715,-762,514,542,-683,1000,-1000,-625,-1000,-60,441,-1000,1000,1000,1000,467,801,251,-310,482,-720,-343,-1000,1000,-870,66,-8,-144,2,1000,-506,-733,-215,124,159,693,-808,54,30,341,426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeColumn(java.lang.Comparable):void",
            new int[]{-32,-1000,149,1000,1000,-307,540,-1000,-1000,-1000,1000,406,1000,947,-328,-987,420,1000,-771,1000,1000,1000,449,-1000,1000,-1000,1000,1000,-1000,-47,-641,1000,-1000,793,-1000,-934,-1000,714,-1000,-1000,1000,1000,-637,1000,1000,156,-724,-566,-837,1000,-658,259,1000,923,-318,1000,-1000,255,-622,1000,-1000,-1000,825,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeRow(int):void",
            new int[]{261,-735,-612,-271,665,-322,184,389,-1000,-40,552,800,-549,-50,-985,678,591,-284,-50,-5,570,-335,422,217,430,169,163,642,515,514,-457,-982,-788,1000,-74,-628,1000,-1000,841,319,717,-151,-608,-580,712,568,795,1000,984,-355,1000,-62,782,-763,-492,435,1000,427,-1000,-561,518,-673,521,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeRow(int):void",
            new int[]{-805,1000,-601,-176,-58,1000,1000,1000,-777,-1000,323,-1000,-1000,-134,597,330,-831,1000,-345,1000,-940,1000,975,116,-431,121,-1000,-426,-77,-1000,-1000,1000,689,-1000,-382,1000,-272,245,266,638,593,107,1000,1000,498,207,409,39,-1000,-1000,676,-510,1000,-289,-1000,683,-153,-1000,1000,-905,-168,-1000,-480,-851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeRow(int):void",
            new int[]{680,-197,918,-866,1000,-776,62,-547,202,286,1000,-774,-90,229,30,-1000,-1000,664,-1000,-894,-486,-839,563,1000,155,-388,288,-624,1000,1000,-1000,94,11,239,710,-769,-4,-293,-1000,11,1000,762,-1000,-1000,1000,-1000,72,1000,208,83,215,-1000,-140,-730,492,-126,538,-746,91,-1000,1000,480,928,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeRow(int):void",
            new int[]{383,598,4,-281,177,-1000,124,-325,-138,-544,220,-162,-560,509,-142,949,400,22,-345,850,433,1000,50,367,-42,-717,-368,900,-752,-373,-1000,839,-474,572,-1000,597,-82,-331,-334,562,387,-79,88,-247,535,-70,15,446,130,898,381,-387,954,-289,980,414,285,-228,-1000,-78,763,-491,-203,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeRow(int):void",
            new int[]{-1000,1000,-321,46,472,-1000,-1000,-174,-935,-1000,-1000,-699,-510,128,988,-1000,-1000,-1000,736,-1000,570,-335,422,249,-1000,-1000,-1000,-1000,824,35,-457,27,756,-1000,1000,-628,-1000,621,1000,319,434,-1000,1000,921,-1000,81,-278,30,-1000,-151,-46,-430,-1000,741,1000,-576,-1000,-984,369,1000,1000,1000,1000,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeRow(int):void",
            new int[]{899,-1000,348,-1000,1000,-702,-229,-639,-1000,335,552,144,-852,194,-880,-126,298,-176,-825,-813,-651,-1000,513,1000,1000,-503,550,198,999,1000,-826,-856,-868,1000,284,-1000,561,-578,-559,202,1000,470,-1000,-1000,928,-643,459,1000,1000,420,480,-1000,-15,-706,-556,335,953,-253,-1000,-561,754,-741,1000,562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeRow(java.lang.Comparable):void",
            new int[]{164,71,-136,-831,1000,-1000,1000,617,-987,-677,-112,582,661,559,-807,-1000,1000,386,-406,119,1000,-537,-396,1000,-259,904,283,-1000,254,-621,-1000,-1000,-532,-435,1000,-184,1000,14,272,-252,942,-957,590,-789,-174,-133,476,-274,402,-1000,985,-1000,581,252,-440,-1000,1000,492,1000,0,-1000,904,-1000,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeRow(java.lang.Comparable):void",
            new int[]{-885,896,123,-841,82,-111,331,741,-1000,1000,277,-970,1000,-682,-1000,-643,1000,1000,272,134,-193,813,-400,1000,-1000,-1000,1000,481,66,-26,106,-738,-417,564,-216,-603,1000,-305,-30,-1000,1000,-410,-202,-349,-660,-256,247,1000,987,-696,-405,150,347,-66,-113,-131,383,1000,2,1000,239,1000,-936,-617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeRow(java.lang.Comparable):void",
            new int[]{-514,1000,633,-944,1000,-1000,-101,1000,-1000,1000,674,-378,1000,606,-648,-300,1000,-1000,-790,1000,42,848,256,1000,-1000,-350,1000,-786,507,200,365,-136,-401,-1000,-74,-273,176,-1000,-1000,953,747,-576,-9,-789,-698,-104,1000,-1000,1000,-1000,-751,39,-27,202,632,-773,869,-1000,1000,576,-793,1000,-281,579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeRow(java.lang.Comparable):void",
            new int[]{-66,172,892,-1000,1000,-1000,467,1000,-854,608,-252,-703,1000,1000,-880,45,1000,112,-509,1000,-177,1000,455,1000,-859,-1000,1000,-415,793,27,333,-1000,194,-1000,956,40,1000,10,-1000,-533,652,-775,-177,-963,-1000,-529,873,-621,-850,-1000,-887,-286,812,397,1000,-983,1000,-1000,1000,1000,-1000,1000,-354,826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeRow(java.lang.Comparable):void",
            new int[]{-1000,-336,574,-1000,166,1000,1000,338,-261,864,-26,-574,1000,715,309,-530,596,20,-545,-187,697,777,-1000,1000,488,-636,-1000,-45,-1000,-1000,-275,-352,244,535,278,-22,703,1000,845,1000,284,-639,966,-872,799,1000,-417,-4,-1000,-386,958,647,-351,1000,-665,-1000,474,-5,807,1000,-1000,20,-1000,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeValue(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{1000,-1000,-556,-1000,645,240,1000,1000,566,-1000,-816,-432,779,-431,1000,-1000,27,991,-769,157,1000,-1000,-1000,1000,-657,-113,-238,1000,1000,-1000,-1000,-1000,745,-1000,-667,-87,507,-347,1000,288,1000,828,1000,135,4,400,-800,-214,-1000,-1000,607,432,-639,931,648,-1000,-228,281,-125,1000,-898,370,-1000,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeValue(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-539,-36,358,624,275,395,-1000,-768,591,-162,-34,205,-425,-222,701,-545,-365,-258,750,-97,-186,482,-31,205,-113,700,-187,692,855,102,-647,-1000,-273,580,929,45,-446,-410,752,-755,509,-705,528,342,607,632,625,-130,987,-246,457,7,153,-478,927,5,-642,684,934,306,179,458,-207,-647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeValue(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{77,-643,-876,-121,743,247,1000,-874,-1000,-503,336,-534,-600,-885,-1000,-394,906,-1000,-1000,-325,552,-401,562,-1000,1000,-443,-164,-367,-1000,293,1000,1000,1000,-436,-763,-488,665,425,143,901,840,1000,197,-333,622,-383,-761,46,-390,483,-91,1000,-75,91,-1000,222,341,-927,-813,-864,954,670,171,112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "removeValue(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-904,-18,164,-561,107,395,-1000,-722,591,658,-76,256,-114,206,701,124,93,-384,-454,-256,-39,127,132,84,-1000,-203,197,692,633,102,-320,-927,87,307,119,226,-696,-1000,650,-303,-4,-1000,943,-169,-134,-514,31,-113,147,562,755,248,343,-804,620,158,-1000,-716,57,-540,179,9,-646,-23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "setValue(java.lang.Number,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{213,-926,-266,735,470,396,11,-517,-301,1000,-822,-580,174,-498,-675,834,1000,-902,-1000,686,1000,635,291,-637,1000,1000,-762,775,283,383,-14,-645,-941,380,-348,1000,1000,-287,-860,898,-1000,-303,363,-332,943,719,-737,-626,-386,124,1000,-647,857,1000,500,932,-696,-392,-244,289,113,905,-636,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "setValue(java.lang.Number,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-999,465,48,94,44,-661,301,-337,-400,534,1000,-721,-682,-260,273,-87,19,-1000,-505,853,-362,-649,891,-81,-1000,-1000,1000,517,-400,48,-831,-100,-1000,-116,331,-820,408,24,131,-1000,283,247,996,341,1000,-456,-185,60,-1000,685,-400,753,-126,-1000,-674,-621,-12,249,-1000,561,617,785,-443,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "setValue(java.lang.Number,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-975,1000,243,-1000,1000,-26,714,-150,1000,-1000,683,-1000,1000,-530,837,-167,591,-255,1000,1000,168,-765,48,-54,-1000,-980,193,-874,-1000,-565,-231,1000,-437,501,-884,-1000,1000,-608,-126,-1000,400,617,-1000,914,-993,654,-100,-1000,1000,-1000,-1000,1000,370,-242,89,861,779,811,486,-846,-187,968,-229,712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "setValue(java.lang.Number,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-930,1000,-162,-1000,-1000,-632,-1000,-1000,933,405,902,110,226,1000,-882,221,-1000,154,-518,253,-1000,-153,-27,922,-724,-1000,906,112,1000,406,-1000,161,359,-1000,1000,237,-1000,596,-128,651,-667,-566,407,-234,-930,1000,82,939,-560,387,758,18,-279,-1000,-153,-619,-378,-82,-651,530,539,-1000,-944,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.DefaultKeyedValues2D", "org.jfree.data.DefaultKeyedValues2D", "setValue(java.lang.Number,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{421,363,-653,-971,0,-394,565,-767,763,-618,1000,1000,246,366,-827,-291,-214,-1000,-1000,1000,-248,1000,-889,1000,-1000,-1000,1000,-1000,569,665,-801,-248,-1000,-931,-924,1000,-1000,-191,-367,278,-1000,-463,-871,-278,-1000,-682,-76,1000,-1000,1000,-1000,-1000,1000,-1000,-1000,434,434,890,-1000,1000,310,-1000,-1000,516}));
    }
}
