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
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{-153,137,1000,20,1000,214,102,-420,356,558,417,197,-201,47,-53,344,195,-1000,96,502,-366,454,-588,702,-556,-429,-171,635,103,-530,861,-468,-563,1000,-401,633,902,-181,652,229,-108,-164,-813,-755,903,433,311,-905,-508,-476,392,-196,117,329,-207,239,-582,202,446,49,192,-238,910,495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{180,-492,553,420,-410,-418,832,-143,839,-256,-506,-162,372,-973,413,-5,49,342,173,183,505,781,-548,-12,-155,143,-18,100,-117,878,1000,-343,-758,1000,-154,-1000,547,-507,149,744,-755,-392,-187,739,-569,31,-467,-346,-617,-760,-605,-825,566,431,-319,-437,213,-42,-576,90,-189,199,165,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{-154,77,-138,129,-36,83,-27,1000,-1000,7,49,-328,314,971,-824,1000,747,-342,1000,-378,-760,-334,448,1000,750,-73,1000,16,1000,1000,-1000,474,1000,-342,-1000,-233,613,-945,736,-58,319,-40,309,-369,645,-810,1000,52,-152,223,1000,1000,-331,-1000,1000,-83,-426,349,1000,-238,-197,1000,883,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{-1000,-1000,89,-1000,-1000,83,48,237,357,1000,-1000,1000,-184,-1000,-127,-1000,-523,-362,1000,757,-161,-706,-456,1000,-1000,-479,-279,16,-456,-109,1000,-992,-994,-311,-84,70,613,-277,268,1000,113,-40,-423,273,-902,92,-1000,-207,-1000,-466,-1000,-1000,-635,825,-1000,1000,-1000,-275,1000,-183,-537,1000,883,822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{124,1000,-136,1000,1000,-1000,593,-523,967,-1000,-717,-628,-1000,-597,341,400,-994,1000,-1000,-1000,1000,189,-137,-1000,1000,1000,-185,406,-712,-274,-677,943,848,74,1000,81,462,-426,510,-39,-1000,-86,632,362,1000,312,-1000,1000,180,-750,173,66,486,750,702,870,1000,-370,863,-646,-611,-1000,-1000,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{-1000,71,1000,-662,669,-1000,-798,452,-308,-17,-474,38,790,680,-49,647,-129,-837,-596,44,-743,-203,603,-241,389,120,43,231,355,912,-194,209,508,-675,-183,-1000,-213,-855,789,691,-1000,1000,-597,-503,31,-315,956,805,165,-862,1000,478,-1000,-20,792,812,-727,182,285,-1000,-638,86,-789,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{269,-763,-1000,130,-262,563,1000,181,1000,279,909,722,-1000,-1000,-886,-917,87,293,1000,53,97,-1000,-826,1000,-569,-920,670,-384,-318,-184,-430,-1000,91,86,-1000,944,591,-904,-577,-797,1000,-996,1000,846,108,253,-823,-658,-1000,1000,547,-308,962,1000,1000,390,-804,-783,219,-606,-385,-971,1000,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{682,653,-796,-662,968,-1000,-798,509,1000,-772,458,38,-591,-1000,-363,124,-659,1000,-445,-176,1000,-203,-773,-241,389,120,43,89,-1000,-830,-49,106,86,552,-1000,1000,-104,-1000,555,-108,365,-1000,1000,-503,552,-311,956,823,-1000,857,400,-721,811,521,1000,40,234,-1000,-668,-1000,-1000,-1000,-577,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{-1000,43,1000,-892,69,-925,737,400,-229,288,-632,549,435,80,23,153,-216,-707,-117,-520,-511,-712,300,359,-28,50,29,191,-622,791,322,-389,59,-702,-334,-1000,327,232,644,783,-684,905,-424,-121,-228,-225,-616,609,-413,-702,400,35,-1000,309,-695,921,-862,-200,628,-900,-930,619,-809,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{402,179,1000,963,1000,-48,576,-511,513,579,514,-866,43,481,-149,855,596,-628,-525,-239,-127,1000,-750,23,-12,-173,184,472,566,-886,861,143,-389,1000,183,1000,781,-112,999,229,-453,-796,-1000,-604,1000,372,972,-1000,220,-714,545,251,1000,-173,70,-572,-10,693,-140,462,305,-1000,377,112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{-624,1000,209,-580,1000,-1000,-606,748,-88,-653,-432,422,808,246,-116,923,-865,-256,-987,-445,-366,-1000,777,-962,817,552,-407,415,-489,644,-847,924,1000,-861,-542,-1000,902,-1000,955,424,-901,959,703,-145,313,433,1000,1000,-247,-281,847,226,-838,259,1000,892,-313,-667,-529,-1000,-1000,-332,-1000,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{186,-954,-316,160,572,964,905,333,-44,1000,-708,214,139,-1000,-413,-918,-178,-999,1000,-251,-740,1000,-753,994,-1000,-1000,-744,978,602,-1000,1000,71,400,-957,6,1000,1000,-404,287,-804,330,-1000,-1000,-329,-1000,1000,-1000,-1000,-559,-625,-1000,327,1000,1000,-796,1000,-1000,450,757,419,19,131,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{-346,-574,-367,-497,-582,-136,386,-623,52,411,-349,1000,-1000,-1000,-258,-917,-485,432,545,-253,137,-1000,-397,600,-248,-237,99,26,-519,-992,-314,138,91,215,-662,138,288,-345,-52,-70,451,-191,679,354,-422,-280,-823,334,-1000,-664,-853,-308,985,-371,-126,748,-569,-541,258,25,238,-230,601,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{-117,-1000,-42,82,572,-240,818,-1000,281,3,-1000,-805,-4,-990,-321,-739,-229,294,33,-202,400,-296,-687,127,-169,-81,778,-397,197,-880,688,-540,-532,957,347,1000,516,246,1000,1000,302,-1000,-463,384,-91,-405,253,-943,-165,-433,-1000,-66,1000,-218,-619,-755,127,-406,79,-964,-718,-900,296,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{-691,-730,633,-521,84,-215,62,-1000,958,143,-768,607,-641,-848,376,-12,-1000,-93,-158,20,437,-229,-731,-264,-363,227,-232,-11,-1000,-240,686,-1000,-1000,104,138,-95,575,1000,283,558,-698,350,-581,-1000,-487,83,1000,439,-295,-838,-1000,-749,186,783,-1000,531,-319,38,-142,-368,-195,-775,-1000,192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addAnnotation(org.jfree.chart.annotations.CategoryAnnotation):void",
            new int[]{-583,-1000,104,2,626,-572,704,163,337,1000,204,284,1000,-409,-827,647,-279,-377,382,-947,-225,-207,-918,491,-542,-767,96,148,-192,295,60,-588,-561,-267,-701,-1000,28,-135,482,324,960,-1000,443,177,302,-527,-92,-741,84,417,651,62,489,-423,293,318,-874,279,-142,-1000,-562,-1000,466,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,258,-446,1000,-147,812,-322,358,-741,1000,-348,1000,450,-723,-613,489,-700,292,-1000,-843,-856,522,306,758,-1000,-100,352,391,240,-1000,630,949,161,162,1000,-247,-1000,1000,896,552,-36,983,-1000,-917,564,-494,191,-598,111,989,1000,995,-270,-918,-408,1000,1000,-402,-244,268,-1000,-564,651,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-400,125,60,-400,-753,-404,-1,3,49,831,400,109,49,-491,-378,-13,143,-273,-440,114,-246,-181,-375,-317,313,-98,613,-184,382,-589,498,-1000,289,863,-400,-984,-1000,140,1000,1000,15,497,-179,-692,11,523,729,-12,302,55,0,-400,72,-22,251,194,-287,-378,149,-187,-249,538,315,-595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-666,59,-1000,-1000,-210,427,18,494,526,1000,-939,469,925,-111,-740,375,-819,-273,977,-781,-975,-91,463,1000,-850,1000,-692,1000,-647,1000,-400,660,1000,-827,-226,-1000,-458,-1000,1000,-24,-371,168,-560,-291,514,794,521,30,448,438,-758,532,75,-52,-507,-646,-853,584,-245,1000,1000,1000,63}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,899,-130,-1000,-233,-725,546,-624,471,819,273,-518,265,520,-111,883,364,-833,-1000,-49,-266,1000,673,26,546,-380,1000,-1000,951,-618,1000,-38,-116,-43,-644,-66,8,-318,-932,683,-5,461,-97,-371,-11,1000,692,450,-225,843,263,-758,75,-248,-92,349,-1000,-796,1000,-148,719,1000,845,308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-608,394,1000,-177,495,-1000,358,-987,437,-1000,961,-189,852,-1000,1000,575,1000,409,-1000,1000,1000,29,-1000,-669,274,-290,1000,221,-1000,582,1000,-194,-616,1000,622,-1000,343,-918,-1000,1000,1000,-648,-1000,-428,1000,851,-898,1000,695,146,1000,-1000,-248,1000,1000,-672,-175,-731,-1000,59,-486,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-763,-274,-1000,-996,-1000,486,-293,846,1000,-593,-1000,-36,1000,-111,-672,383,-819,-1000,591,-706,-275,-83,-720,1000,-358,753,-692,301,-455,-200,-1000,-224,656,-1000,-383,-1000,-105,313,258,-407,-732,-188,-1000,-70,-1000,677,520,962,-486,-1000,-874,185,283,-70,358,1000,-207,785,-357,-1000,738,878,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{768,191,-1000,6,186,-292,-916,1000,-497,421,994,-164,304,-193,888,-607,-1000,593,-653,-127,372,416,-903,597,1000,256,-606,553,586,454,-706,-869,-28,-526,-126,418,-252,959,633,398,-905,-588,460,-1000,350,-312,615,-1000,-1000,-283,-46,798,530,-702,233,-464,823,565,192,1000,-1000,-404,833,-228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{121,-1000,819,-400,-232,147,-60,858,-294,483,226,-57,-596,1000,-378,-13,-128,-273,400,114,-246,-181,-773,-317,-273,343,819,-184,217,-750,1000,-375,173,708,-319,331,-1000,721,1000,366,15,337,67,-692,82,597,625,-749,302,555,662,-390,72,296,251,194,-1000,-1000,-768,-187,-151,795,315,-595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{308,122,-256,-1000,1000,125,-624,344,401,-18,922,500,278,-607,149,-12,-504,-1000,-792,726,-151,691,1000,1000,-191,102,591,-1000,688,-1000,697,-892,-1000,746,-829,-753,669,589,459,1000,-605,907,610,-171,306,792,-714,-681,-1000,1000,858,-33,868,-308,-1000,-315,648,-577,-49,1000,955,341,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{121,-1000,719,-400,-766,-46,-102,1000,-31,610,-380,-395,-806,1000,-378,532,-115,-264,627,562,-554,-1000,-1000,-839,45,358,646,31,-238,-635,160,-1000,97,1000,-568,109,-1000,870,1000,69,-266,976,4,-1000,41,-803,614,-701,1000,-375,-221,-471,149,668,267,201,400,-588,208,-1000,-1000,611,338,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{256,1000,-691,1000,-922,-292,-916,-864,-497,935,-751,454,733,171,-1000,1000,963,1000,-792,-1000,1000,1000,597,-1000,168,-355,-585,1000,457,-623,-706,-869,-28,-395,885,-14,-1000,-487,39,-1000,1000,529,-1000,-1000,-530,894,1000,155,1000,-20,-800,986,-1000,-702,1000,1000,347,714,578,-1000,-81,-852,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-965,228,-1000,-523,-1000,864,-1000,494,6,-256,-1000,-795,845,-54,-1000,-745,-1000,-570,1000,-429,58,-1000,-1000,709,1000,-27,-437,-949,735,498,-1000,260,477,-1000,-926,400,659,-1000,-197,201,-1000,-457,5,1000,261,-723,-348,-579,1000,-608,-1000,1000,870,202,77,-1000,605,1000,399,420,770,-478,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-860,1000,754,-934,33,-561,1000,-999,-200,789,1000,409,1000,-349,-618,962,504,-1000,118,1000,843,771,1000,1000,-250,-220,1000,-986,1000,970,606,-792,182,129,-457,-938,-1000,-1000,-34,591,313,1000,855,-214,370,151,1000,741,-435,1000,-71,-489,818,-1000,-20,-670,33,-586,591,179,1000,1000,282,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{390,-155,136,-334,1000,130,-265,290,272,632,1000,118,632,237,-222,-380,-188,-881,-949,631,-951,355,1000,463,-300,-509,1000,-582,563,-1000,1000,50,-191,22,117,631,-29,309,53,215,-356,681,-486,-562,17,792,419,176,-484,1000,-316,-929,94,-216,-1000,-1000,998,-1000,19,309,351,377,584,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{331,1000,48,1000,-1000,486,-1000,-821,-1000,597,-232,1000,1000,35,-761,0,1000,1000,-646,-1000,584,928,744,-1000,90,-1000,-988,1000,988,-761,-538,-141,1000,-351,-174,-202,-1000,-1000,480,-1000,1000,0,-792,-1000,-1000,-448,-1000,729,1000,-98,-25,1000,-1000,-1000,1000,1000,421,569,208,-1000,-772,-1000,-937,-891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(int,org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{299,-1000,-176,-106,-677,807,-647,426,-28,1000,361,-275,53,678,-484,489,13,768,646,-741,-132,421,-696,-357,205,-71,772,-632,346,-550,1000,504,54,455,832,415,-48,141,-423,-519,-36,341,-703,-1000,564,400,945,-289,898,-311,-373,-397,-864,53,733,167,-326,0,969,-938,-577,-829,651,-536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{112,791,1000,1000,406,-455,75,793,-30,184,-214,1000,466,-842,584,639,-353,-481,565,-523,-689,-864,1000,56,825,8,4,-403,-990,1000,-1000,-276,1,-157,365,-623,1000,-487,-121,-772,-759,130,175,1000,609,149,297,1000,171,-1000,2,551,-194,1000,257,259,-1000,711,-1000,947,-1000,-569,-449,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{1000,1000,124,-474,-812,-1000,-1000,222,-381,283,-547,1000,-1000,1000,-893,135,1000,379,21,873,-1000,-1000,1000,-1000,1000,297,-182,-978,-420,1000,353,-945,-673,396,-550,-1000,1000,-309,-1000,-952,-861,-340,-384,-1000,1000,618,-175,-562,-536,-1000,471,-851,483,-237,-294,276,90,1000,-641,379,498,-1000,1000,512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{1000,-157,-1000,-387,-603,381,-1000,-334,249,-291,-43,637,-582,1000,-675,-972,187,34,1000,1000,-152,195,5,-119,1000,-563,877,-1000,753,1000,1000,479,155,-633,844,288,271,-769,-221,548,-796,-331,-431,-1000,-27,820,-83,-934,-855,759,-464,-245,446,-550,427,-1000,132,46,890,-861,1000,368,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{9,-638,3,-571,48,705,-663,54,934,66,950,-247,-300,394,-657,-985,-612,677,-892,-737,700,520,136,357,344,86,-62,-368,548,567,150,-114,-796,187,-884,563,-787,-831,297,859,733,32,-832,-552,-774,-837,-716,-893,-498,-334,-804,-365,-766,168,46,-833,871,-524,849,217,279,769,656,605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{539,-34,-401,-124,-751,98,-1000,188,395,1000,335,58,-871,1000,-1000,-1000,425,577,10,1000,480,1000,560,-267,-339,229,552,-1000,670,989,367,-133,-285,-180,1000,630,-178,-1000,23,612,-412,-1000,1000,-1000,-985,1000,-792,-682,-126,1000,-661,719,-668,-1000,10,-1000,921,-573,1000,-903,604,864,1000,843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{-424,1000,-81,397,5,-970,854,-790,-647,1000,82,533,-971,1000,1000,-213,346,630,-505,-129,-1000,882,94,-952,-488,638,83,954,-25,168,-792,925,263,877,296,-393,-1000,92,61,273,785,-531,1000,146,-261,-1000,891,-346,1000,542,1000,468,-1000,-1000,-1000,1000,-449,1000,-726,890,1000,-1000,-1000,289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{779,199,7,-795,-691,401,-1000,448,-13,-83,-169,559,-1000,-610,9,-393,678,-495,1000,934,-6,-575,493,-696,1000,248,972,-712,607,1000,1000,-404,838,-922,332,-102,1000,823,-454,274,-425,-282,-1000,-883,669,512,430,-1000,-1000,-276,1000,-230,1000,-7,941,-1000,1000,-511,1000,-1000,782,-351,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{679,-392,-646,16,-257,674,-1000,-7,688,14,-260,1000,6,1000,-1000,-626,-701,-171,760,854,657,-78,100,258,1000,596,842,-1000,578,1000,685,-349,142,-451,-654,570,283,-602,672,-900,-643,-856,-1000,-1000,-545,-44,347,-73,635,200,-809,239,193,-312,561,-1000,278,-355,995,725,1000,987,947,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{9,1000,353,-259,71,-557,-21,58,-249,220,138,622,-188,-274,449,130,396,298,451,-169,-439,-469,933,-537,470,858,-216,-124,573,317,-462,228,-296,192,931,1000,336,-735,1000,-1000,-127,342,967,400,682,8,-38,-303,372,-551,-431,820,-192,301,-90,107,1000,-732,-918,-734,-513,-844,-11,-633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{1000,-203,-595,-1000,-649,1000,-1000,-1000,904,711,-106,156,-37,947,-1000,-563,680,473,784,251,1000,-1000,1,-545,1000,-356,574,-1000,1000,1000,1000,-1000,-1000,-94,-1000,611,153,-1000,-239,798,1000,-1000,-478,-1000,-375,-9,1000,-436,-1000,-762,-1000,-1000,959,-1000,1000,-1000,1000,1000,1000,-895,-95,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{208,-40,869,920,896,700,-755,-118,1000,823,452,321,-971,697,195,332,-768,482,144,-193,711,-925,1000,360,561,-571,676,-1000,-493,747,-956,-826,-921,-569,-1000,737,-576,-984,281,529,810,-961,-825,723,-642,-819,1000,967,-1000,-1000,1000,105,-835,-397,951,-754,-1000,1000,138,101,-1000,952,52,-308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{-683,531,-572,-443,1000,-441,-154,-1000,286,-87,781,157,935,531,-479,522,-1000,-1000,327,941,108,-220,-680,-315,896,994,297,125,1000,855,687,167,-1000,-224,-640,-435,889,-1000,668,-612,370,625,-1000,1000,820,-1000,410,-1000,-178,-586,157,1000,883,-176,728,-868,869,-324,-408,-797,422,-323,300,745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{838,-1000,-756,970,84,1000,-1000,-713,1000,846,949,-717,453,523,-1000,-1000,-500,256,-61,410,1000,-234,600,405,1000,-176,732,-1000,1000,737,924,-1000,-1000,-323,-1000,1000,-1000,-1000,669,1000,1000,-957,915,-1000,-1000,-1000,649,-1000,-1000,-326,-1000,-54,15,-390,-611,-1000,-813,-1000,1000,-546,800,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{-642,635,-77,51,1000,860,-955,634,496,107,-318,-9,1000,-768,783,770,-388,-1000,1000,876,231,-1000,398,-86,621,763,347,-416,462,138,64,-167,-1000,-277,-893,-223,914,-1000,823,-903,1000,755,-254,1000,1000,-1000,1000,-1000,-514,-1000,-1000,591,1000,587,1000,-1000,897,-254,-948,-1000,-661,-150,643,520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{668,-154,-756,-725,-648,695,-1000,-412,1000,1000,337,-456,-1000,1000,-1000,-1000,1000,602,176,228,457,1000,391,-367,-1000,585,85,-1000,399,1000,846,751,-698,1000,299,-287,39,-651,-123,-1,43,-786,47,-1000,-325,-1000,-1000,-1000,-673,1000,1000,627,35,-880,5,-1000,1000,-1000,1000,-419,973,507,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker):void",
            new int[]{562,367,-542,-108,394,779,-1000,-896,-172,-378,-7,370,25,-382,762,-112,74,-749,932,571,-266,-266,-89,-5,787,58,99,-740,-282,891,1000,342,-462,-217,-1000,-190,582,-29,33,-650,22,1000,65,134,1000,17,175,-1000,-986,-89,-84,157,976,1000,383,-1000,244,-137,35,-1000,1000,-349,1000,209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-329,-453,1000,-1000,1000,-630,-614,-1000,549,446,-1000,149,383,552,323,49,-381,-1000,753,590,1000,-108,-6,574,1000,309,-227,-954,131,1000,981,413,-175,210,683,-858,-582,378,-538,77,966,937,-657,1000,-1000,-1000,1000,-159,186,-692,-83,572,477,-1000,-649,-245,-664,-1000,-523,-499,-573,985,631,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-666,-305,117,-765,97,224,-402,785,847,-156,429,-97,450,1000,-91,92,912,-23,514,-12,-1000,375,1000,148,1000,831,931,-400,-69,466,-46,-266,1000,-400,490,515,1000,-577,374,-1000,-554,-797,703,1000,-1000,325,-558,246,-129,1000,-1000,-1000,827,101,856,184,239,879,-666,70,-400,-740,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,484,-527,725,59,1000,604,928,-509,-1000,1000,836,1000,-1000,1000,1000,-660,816,349,475,969,1000,-1000,507,598,-1000,1000,953,-1000,-331,1000,-567,967,537,62,821,616,-680,-1000,787,256,1000,1000,-1000,1000,-1000,-408,1000,1000,-1000,1000,-957,252,-1000,784,240,-632,1000,539,-1000,533,-1000,1000,797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-217,-761,-1000,-269,-1000,-75,366,-1000,780,1000,-1000,-636,164,-1000,-167,757,17,-1000,-858,-657,-518,702,-1000,-868,-492,-443,-93,410,473,139,515,-1000,-1000,-418,1000,-489,1000,448,-111,525,-1000,250,1000,-1000,722,-1000,-203,489,70,47,593,-617,-263,-603,627,-432,146,1000,1000,122,462,-376,347,724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,84,-31,1000,-20,223,552,753,786,-1000,983,-1000,705,-20,1000,665,-376,1000,998,1000,997,796,-106,1000,-775,-363,1000,879,-1000,-881,880,436,-197,384,-701,1000,-83,-982,-1000,-806,806,914,-64,285,993,583,-198,1000,1000,-533,772,582,1000,-1000,197,1000,891,456,43,-1000,452,-618,1000,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,416,-21,592,-155,348,589,404,516,-666,838,346,794,-953,850,983,-73,1000,580,601,693,770,-1000,605,376,-997,1000,1000,-1000,-279,505,78,-438,614,-375,510,107,-693,-1000,-613,563,873,788,-380,1000,182,-184,912,1000,-1000,547,282,942,-609,355,716,1000,965,-95,-696,653,-810,1000,759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-272,151,-826,-92,-1000,-1000,-978,1000,486,1000,-948,74,70,-949,-303,1000,-299,-1000,-362,-364,72,444,-1000,814,-903,-1000,-268,-348,400,-521,945,-144,-1000,555,733,968,946,341,-98,776,-805,273,1000,1000,-27,-85,219,1000,706,-438,41,554,-451,-522,223,-552,-355,325,1000,-1000,1000,-701,182,879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-57,494,-962,-278,-816,890,664,-723,-969,763,312,442,807,-507,-579,-724,380,672,327,-290,-588,-560,441,-321,-797,-422,-280,-49,718,884,512,384,-208,751,-611,18,605,391,633,-796,-935,-347,650,-208,921,-950,-183,-721,-851,-778,972,795,-583,851,112,814,-687,-188,715,549,390,-786,550,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{381,1000,194,215,1000,-573,-341,1000,807,-1000,564,475,-441,780,1000,796,-1000,1000,-660,1000,419,1000,507,1000,-34,332,1000,289,-1000,818,988,-404,267,-1000,159,-1000,540,-1000,-1000,-681,398,216,-748,258,214,399,-1000,1000,1000,171,188,-205,1000,-1000,-796,225,540,223,-1000,-1000,-746,167,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-1000,363,-490,963,-239,835,245,848,-925,288,160,675,201,651,-332,382,-888,706,360,920,-596,532,238,914,1000,-892,-439,-321,-552,381,1000,-588,400,-62,-335,-1000,-271,435,815,1000,-79,583,-380,911,98,1000,993,-113,879,9,-1000,415,-710,-316,-419,591,-559,-927,-355,-279,240,-134,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-874,154,258,205,-1000,-510,348,-621,1000,390,-30,-391,492,-669,-234,820,510,446,989,557,-385,-133,-527,636,-758,10,623,-853,-407,-589,378,1000,366,528,-251,533,-254,-633,271,155,629,453,622,550,-255,-322,795,-28,830,257,-304,-191,69,-18,147,-43,721,-82,964,313,884,-16,25,272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-1000,319,-1000,232,-1000,-646,112,1000,-191,-1000,-177,-529,248,511,909,-1000,-1000,-1000,549,1000,880,73,10,-219,169,959,173,-383,772,995,-658,-994,-1000,970,-173,-279,240,-861,1000,159,971,-1000,-400,-590,270,152,1000,1000,755,1000,97,1000,983,-778,-117,-841,-657,888,-963,1000,1000,1000,686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-766,93,-1000,-970,-1000,1000,72,-313,844,-395,1000,419,-17,-750,359,1000,179,1000,855,744,-1000,452,-209,926,-768,-679,1000,897,-1000,-440,654,-1000,-340,-1000,-920,-155,313,-1000,-796,-689,303,265,676,271,795,656,-203,256,1000,64,105,-1000,653,110,506,661,1000,691,949,-524,1000,-414,-1,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{366,992,448,-1000,395,106,-1000,-504,1000,535,-1000,505,463,-641,-128,265,-438,-1000,713,-327,1000,197,-877,549,-115,135,315,-1000,545,-781,378,-538,-1000,177,963,574,-331,710,-762,-378,-805,1000,331,550,-1000,309,38,69,584,1000,-1000,987,1000,-948,457,-1000,-867,-179,964,209,-855,796,41,-621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-426,934,-210,1000,66,315,-318,-3,-1000,-557,-60,245,632,407,-550,126,-453,1000,1000,1000,-1000,351,1000,1000,-4,-633,74,-560,847,804,331,226,1000,72,-1000,-1000,289,-899,1000,1000,989,-508,1000,-205,-1000,1000,-258,417,-1000,-775,1000,139,-722,-1000,-620,24,-206,-856,-1000,654,1000,550,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addDomainMarker(org.jfree.chart.plot.CategoryMarker,org.jfree.chart.util.Layer):void",
            new int[]{-369,-373,-1000,-149,-1000,232,735,-1000,704,1000,-1000,-636,252,-1000,-91,883,-561,-958,-517,-12,-145,1000,-1000,-868,-1000,-973,-93,446,473,43,887,-1000,-1000,-400,1000,-489,877,460,-390,661,-1000,1000,1000,-1000,1000,-1000,-102,538,70,-923,1000,155,-263,-1000,856,184,70,1000,1000,61,626,-490,826,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-678,-311,529,232,384,776,168,-811,-280,569,310,345,-919,82,-96,240,-515,499,830,-363,-43,-816,-368,482,854,-655,-655,31,-880,897,-347,-948,-232,-910,252,-83,772,812,-203,649,-871,113,-908,553,342,480,267,-80,-194,-353,-359,477,668,-666,669,753,611,-815,910,-660,-296,-590,-667,-868}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-545,159,100,-920,196,35,949,244,-23,-424,601,848,-309,-891,672,158,-626,511,-1000,-370,1000,-54,-212,-920,-566,38,-692,-582,938,-786,81,740,-355,884,708,-1000,764,-954,-701,-48,30,-1000,430,163,607,-288,-1000,-670,496,657,-157,-838,-363,-329,-397,180,682,-885,-1000,-721,-258,1000,-160,-641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-852,839,95,-995,1000,665,-248,106,212,-1000,870,-242,-401,-240,342,698,-171,-1000,-514,-746,-239,1000,793,-731,-329,1000,313,1000,-919,-645,342,983,1000,-716,422,-130,-1000,-170,-154,953,-672,1000,-642,-1000,206,-1000,-401,1000,-515,581,-1000,592,1000,-1000,-1000,699,184,781,1000,-969,444,1000,-377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{72,-991,205,650,-48,-940,794,167,-29,667,-390,-551,361,-776,-221,382,857,234,233,169,-776,492,886,680,-294,898,683,176,728,-857,-442,-281,1000,529,-445,344,-329,-495,338,-650,1000,-467,811,561,74,763,85,306,-224,168,273,367,56,-130,-634,39,-16,-1000,743,429,-599,-203,1000,-738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-678,-207,544,-1000,-903,776,-984,-535,-497,16,53,345,-988,1000,-96,-626,1000,-215,576,1000,-43,195,-228,1000,558,-1000,-655,652,-913,-1000,475,-948,-232,-866,225,-278,-392,237,-898,1000,1000,-1000,961,-573,396,-1000,-1000,-165,-194,-353,205,-256,1000,-666,-477,-1000,1000,-8,1000,603,659,-358,-667,-810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-255,1000,-1000,-970,1000,-447,254,85,897,-416,924,923,804,377,-628,360,-264,-427,115,789,-416,1000,1000,-972,-1000,1000,515,1000,-321,26,509,1000,1000,209,425,-1000,-234,-1000,132,1000,-1000,520,-1000,-985,-1000,-1000,1000,1000,-979,-845,-1000,84,520,-1000,-1000,600,936,435,1000,-768,-28,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,1000,529,126,1000,-29,-509,-370,991,569,310,-1000,5,243,-1000,-1000,-1000,1000,1000,-363,-43,854,404,-939,-185,533,-1000,537,-1000,1000,1000,1000,327,-1000,698,-167,772,1000,-26,-1000,-1000,1000,-1000,1000,1000,541,267,-80,-1000,1000,-830,755,206,-1000,1000,1000,-1000,-815,-936,-535,-158,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{50,-515,-488,-664,-448,-635,1000,165,-451,97,-550,919,-159,-1000,902,-18,251,1000,-520,-586,439,-1000,-16,-93,63,83,-10,-766,390,-596,-771,226,888,746,-118,-785,241,-1000,675,717,15,-1000,-605,400,646,-1000,-1000,-695,584,250,-647,339,744,-400,61,-502,1000,66,400,316,-456,-520,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{687,-1000,344,-486,-441,-591,433,100,72,186,-792,192,-997,-143,-560,316,785,-171,-109,-804,-1000,-908,755,386,471,153,452,-237,673,-919,-983,-984,454,1000,-630,277,825,-234,311,750,1000,-546,520,312,-523,275,-663,-862,456,-638,855,-529,1000,676,-1000,-202,600,-1000,839,677,201,305,663,-746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-966,1000,809,40,752,1000,-185,-185,241,143,1000,57,205,400,64,-112,-334,358,-894,1000,1000,1000,-125,-312,-813,-413,-1000,762,-927,1000,1000,1000,-834,-1000,1000,-624,-887,1000,-105,-526,-3,-952,334,-652,-224,1000,939,691,-1000,193,-299,22,-1000,-173,884,1000,-1000,1000,-887,-1000,-391,-333,-817,546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,37,738,-1000,-555,43,-402,-446,-182,318,-864,259,226,668,193,-94,1000,-806,-191,691,199,-1000,730,933,-414,-394,786,737,403,-1000,18,193,1000,337,-246,480,-841,202,-640,1000,1000,-1000,1000,-1000,-543,-1000,-1000,452,895,-597,300,-1000,1000,1000,-1000,-1000,274,150,951,866,166,463,815,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{412,-1000,609,-53,-460,562,-193,-554,1000,163,208,-30,800,-901,481,913,1000,-289,-1000,288,-154,935,614,933,-603,700,1000,-765,1000,-1000,58,286,1000,1000,-257,-748,-146,136,-186,-676,-11,-1000,1000,1000,154,-33,-1000,-1000,248,403,938,-348,-408,-297,-168,-729,502,-1000,590,664,-392,549,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-544,-1000,739,450,117,-67,732,-421,1000,430,980,77,855,-744,806,859,1000,180,-710,596,516,1000,-172,1000,-335,135,896,-281,1000,-48,515,311,1000,152,360,-1000,-184,-888,-546,-747,-460,-1000,791,489,697,111,-1000,-982,-312,417,89,332,-640,-375,1000,-61,510,-991,639,-273,-739,-77,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,55,102,236,361,-6,692,-337,-376,93,915,365,-742,-619,-129,658,-500,499,-234,-363,-352,-215,-372,-361,33,267,-864,-347,-702,818,-634,-1000,-591,-869,1000,100,1000,812,175,224,-871,-1000,-631,51,-36,872,450,55,-194,107,237,-272,-393,-139,332,753,65,-221,-179,-247,-262,-626,-463,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-674,368,-962,-893,-581,560,-333,-334,954,-1000,73,-948,-310,-257,90,349,-264,763,-443,-869,-1000,1000,767,248,308,387,652,-581,-668,-1000,-565,456,498,-32,140,-38,442,-368,1000,-123,423,382,276,-358,-858,662,710,1000,-559,229,158,1000,259,-1000,652,1000,-1000,-556,-393,166,915,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-380,983,-1000,-183,1000,-596,-414,709,-645,-519,207,-1000,1000,-1000,-938,-925,840,898,959,299,1000,859,850,669,-1000,-73,-479,108,1000,515,391,901,-85,206,524,981,223,-586,-93,407,-84,-213,489,-142,-529,-1000,-1000,572,-725,-447,74,864,-189,-267,-361,986,1000,119,399,42,-805,10,-236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-1000,-859,-59,493,48,-400,-1000,531,337,-920,1000,273,-602,-622,-363,16,671,-89,352,238,-1000,-679,996,824,-1000,239,-1000,31,-704,-1000,473,657,929,-749,90,157,442,847,-1000,858,554,569,38,204,-208,-667,-298,-327,1000,-1000,286,-385,1000,1000,984,317,1000,-589,781,-914,-1000,-731,497,-981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-191,776,169,89,-1000,172,-350,-352,-1000,-626,-86,362,-647,-1000,175,912,289,789,255,102,-470,-1000,-717,-478,177,-707,861,-362,-368,-792,736,98,452,505,648,531,695,-599,1000,614,1000,224,-175,771,-1000,-464,-1000,143,-268,-341,-859,370,-1000,-274,1000,1000,811,-1000,-153,-329,-33,-811,729,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{610,-853,207,45,-735,291,-1000,-709,406,1000,-706,-206,1000,-1000,-363,16,-1000,-793,-1000,169,-612,872,-365,-688,1000,1000,-888,1000,-1000,-329,473,651,481,648,-1000,1000,-1000,429,-379,304,688,1000,588,-910,-872,-1000,-360,-321,-167,1000,-428,-212,-1000,382,-791,-738,1000,-132,781,1000,1000,1000,-400,-480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-997,344,229,-552,-929,390,793,-382,910,-735,-354,-13,-120,-460,-976,489,-263,19,469,-957,-378,-319,-642,-1000,1000,1000,282,1000,1000,1000,1000,371,617,818,-502,524,28,-323,711,1000,-544,1000,1000,120,564,793,629,-536,951,867,153,306,171,-520,-844,339,598,-200,857,-133,931,-26,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-888,1000,609,209,-1000,150,28,-256,-1000,42,422,159,1000,-1000,966,648,829,433,-105,279,-1000,-1000,22,-228,161,-430,689,-642,139,33,268,-860,97,309,-80,-39,15,-617,1000,25,1000,-37,-651,389,-1000,-772,-1000,646,-369,-602,-1000,-8,-1000,-112,1000,614,1000,-910,-334,98,533,82,-633,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-805,-580,954,195,709,1000,-940,205,-1000,1000,-49,341,324,-740,1000,118,431,-6,-309,71,-1000,-457,922,1000,-1000,-686,-267,-1000,-1000,-1000,-1000,445,707,-639,1000,501,162,800,-445,533,1000,-227,-1000,254,-1000,-789,-1000,-608,-586,-924,-598,-487,1000,430,1000,187,531,-601,-79,-321,519,75,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-1000,-921,149,325,-668,-929,307,200,-125,1000,382,323,1000,-589,236,-191,566,-793,-893,323,-603,859,704,-533,160,1000,-197,-923,-77,-1000,-689,492,31,768,693,54,-1000,577,991,-91,1000,943,-428,-894,-1000,-713,-1000,-58,-1000,-472,-1000,-1000,-990,297,450,294,958,-516,-354,1000,817,1000,598,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{140,-11,318,219,-74,-64,9,-1000,471,574,-111,90,-457,230,-279,266,-1000,207,-329,-1000,-229,-601,84,149,335,-26,623,101,441,119,351,285,297,244,-12,39,85,200,1000,736,92,186,-191,331,-938,-69,-99,1000,-749,-402,-976,-251,-489,-670,612,445,554,-881,-201,321,438,-499,-11,123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{427,-145,413,-218,343,304,252,316,-188,171,-441,188,43,-740,140,19,-137,375,346,-534,-484,-430,382,971,-1000,-296,-99,-90,-181,452,-1000,85,373,-639,-199,-566,486,800,166,748,-562,434,79,227,-1000,135,287,1000,-187,-416,203,-487,302,-352,446,187,-182,-169,-356,-456,-119,-413,-542,294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-191,-1000,-221,-162,-974,-582,421,-824,-250,-1000,-703,-949,993,-1000,-1000,567,-1000,182,520,590,327,443,-1000,-798,1000,-201,80,-192,12,714,1000,-114,647,619,-198,580,-1000,-737,-1000,162,1000,631,1000,-919,-383,232,9,-1000,1000,1000,115,1000,-724,82,-745,264,1000,-326,1000,1000,118,832,-1000,681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{239,-536,-186,-164,-177,-357,-940,207,-311,-72,59,364,1000,-945,566,152,-472,-295,168,526,-682,1000,922,-208,283,638,-68,-810,-726,-236,-181,555,128,570,693,299,-332,401,363,-35,873,1000,-93,-990,-993,-12,-647,-1000,238,376,-152,105,-1000,923,-834,294,876,-178,67,160,442,1000,598,25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-334,71,1000,597,189,411,114,214,804,-845,-83,862,-304,-22,250,362,-858,489,883,-1000,-413,389,-446,1000,-563,-635,-417,-858,-1000,-708,728,-466,906,-407,922,-369,341,-443,-840,843,87,1000,-732,881,-961,-185,-287,-93,367,154,313,347,413,570,991,977,209,552,189,-941,-522,-332,635,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-191,-1000,329,173,-644,-1000,488,768,-1000,1000,567,-965,1000,126,796,-1000,927,-1000,-1000,1000,-497,647,-1000,-1000,1000,80,-438,-201,-299,714,-1000,490,-776,1000,1000,145,-1000,330,1000,-130,493,-554,1000,-1000,-1000,-1000,-655,1000,-1000,-630,-1000,-1000,-1000,508,-409,-1000,450,-516,-884,340,1000,1000,-148,668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-757,474,264,178,728,-520,-394,-551,328,-1000,-658,901,-897,231,146,171,-1000,731,-664,-1000,224,-275,245,1000,188,51,-783,23,-1000,-708,-48,-644,1000,-1000,-781,-47,601,-177,-1000,593,-1000,1000,448,526,531,921,1000,-393,367,367,1000,39,783,672,-961,-500,-793,779,1000,-615,38,-524,-865,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{770,-104,19,300,712,1000,-221,507,-1000,-1000,115,1000,-438,-1000,890,1000,-483,1000,1000,-169,-817,-752,-437,1000,-824,-1000,293,-50,-499,92,-275,581,1000,-1000,1000,1000,1000,552,-1000,1000,372,760,-82,815,-990,533,-496,-1000,769,211,212,1000,-400,349,-835,1000,555,-517,1000,-1000,-277,43,1000,-695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-635,-63,439,260,-244,650,-705,-150,-1000,-700,77,1000,-189,-1000,966,648,-52,842,617,-584,-1000,-1000,488,814,-1000,-945,-306,-642,-756,-1000,-1000,104,893,-529,1000,224,885,32,-681,797,410,316,-179,987,-1000,-432,-1000,-290,-33,-810,267,-8,741,16,1000,1000,83,-340,115,-1000,-579,-647,712,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-405,-847,287,310,-1000,-1000,793,1000,-104,-768,1000,679,-121,-155,799,718,549,-961,-1000,-931,-1000,-638,-558,-1000,1000,492,-305,1000,1000,136,1000,1000,-1000,1000,-671,193,623,-858,768,-1000,399,509,-499,263,-625,-499,-61,764,63,403,-1000,743,348,895,434,-250,-1000,420,1000,698,1000,498,-56,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-1000,399,-785,-430,-333,713,-331,-1000,-725,1000,311,1000,617,553,292,1000,281,835,-1000,-1000,817,-721,-450,1000,649,42,1000,1000,1000,-5,-852,-246,-560,-1000,194,-55,-41,1000,-945,-1000,-74,257,1000,422,466,255,-222,-439,391,-589,48,-921,1000,-154,-119,930,-811,60,-1000,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-607,418,-305,331,647,558,-701,-1000,44,20,254,1000,929,-258,-363,443,802,1000,-533,-623,1000,-937,451,20,143,647,20,1000,949,-963,-852,463,-955,-699,126,-413,-41,858,-374,-1000,-813,248,522,238,469,-532,-493,-502,-297,362,153,-1000,253,-857,-206,664,-961,-751,-1000,814,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-91,487,490,338,-37,334,693,551,-336,-888,-178,799,-1000,408,229,-53,-309,-442,-52,328,30,-558,123,-282,-580,-329,662,-67,-402,-475,-611,666,592,627,340,-748,-1000,-989,-707,46,16,-279,-1000,-916,582,-802,711,-414,-373,-875,199,-97,-137,-287,-409,-410,-205,-436,188,256,294,651,1000,428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-457,181,313,-579,407,485,507,381,-771,-732,-1000,679,-229,817,1000,-686,-804,196,732,46,-42,600,-741,116,-762,-1000,1000,-756,-361,351,1000,-226,1000,-122,126,193,-1000,-915,237,271,16,-295,-1000,-1000,934,-499,711,-519,-1000,403,331,-6,-430,895,434,80,-263,-580,188,-876,688,860,-103,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,1000,9,992,-458,-1000,768,1000,-147,-770,1000,-1000,255,-1000,311,1000,-681,-1000,-764,-545,-275,-1000,1000,-1000,54,1000,1000,641,-1000,-280,1000,1000,-1000,1000,1000,-892,181,-202,-913,59,1000,-62,15,-716,603,-8,-1000,1000,529,1000,-1000,-32,1000,1000,-649,91,-900,564,1000,1000,-220,-574,476,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-405,-847,667,684,-420,805,620,614,-1000,-57,192,-116,-400,-155,-772,277,-1000,102,-700,96,477,-604,728,-87,-507,386,296,1000,-1000,136,1000,709,578,53,282,-1000,-1000,-858,127,-1000,2,-932,107,172,-193,-524,15,128,26,-241,104,-149,-1000,-862,-1000,-1000,-495,-598,-61,-575,325,-317,774,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-706,-743,205,-1000,1000,1000,475,-1000,-1000,1000,-1000,322,-789,1000,-938,-1000,-583,934,-1000,804,-871,665,359,1000,-1000,-858,1000,-1000,173,104,-1000,-325,-969,-1000,-708,553,-1000,436,-269,-368,-1000,-1000,1000,-1000,140,1000,1000,-1000,85,-1000,1000,-824,-1000,-290,-1000,502,-442,-1000,-1000,-937,229,1000,477,372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-184,-1000,259,123,-446,-474,423,-331,-669,-603,1000,-206,573,-125,668,762,861,-350,1000,-969,-989,49,447,-756,660,1000,877,1000,1000,312,-5,-288,521,-63,-1000,-608,-114,418,1000,-253,-1000,-244,968,1000,634,905,-73,-222,257,560,-589,-278,-51,1000,-499,-25,1000,-690,60,-954,821,696,-561,130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{130,-1000,14,-62,-1000,-1000,447,1000,-126,-1000,1000,359,1000,-422,1000,1000,823,1000,-1000,-1000,-1000,-826,-359,-1000,1000,1000,-1000,1000,796,179,1000,690,-1000,654,-1000,925,1000,-771,1000,-1000,291,1000,-1000,-1000,561,-333,-1000,1000,-526,1000,-1000,544,701,1000,848,267,-1000,664,1000,690,1000,386,-1000,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-723,417,429,-338,-37,694,384,144,-1000,-175,-591,68,1000,914,-258,-586,-595,574,1000,-331,260,1000,-1000,-282,-162,-415,1000,-310,-48,1000,-1000,-703,1000,-704,195,-190,-413,-973,465,844,-83,-736,-46,-213,-245,-802,-1000,-414,-373,-524,198,-97,-1000,-382,-409,-456,-529,-566,188,-1000,601,765,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,876,359,264,395,-487,696,1000,-987,-683,-40,-641,-187,-91,259,-224,-1000,-1000,-814,7,-345,221,-29,515,-371,452,1000,-487,1000,-278,-452,1000,725,1000,893,-487,-504,-995,-910,-375,567,-1000,-129,-1000,-775,-250,-1000,865,291,-1000,458,1000,-1000,217,-1000,-371,-1000,73,-356,-765,580,365,-197,-139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-636,647,405,265,-855,-803,316,29,-668,-449,-534,757,242,-396,107,-954,-1000,-814,-1000,-1000,320,-149,-1000,-676,903,159,-54,714,-316,-376,1000,388,-686,693,730,-1000,512,-469,1000,-21,-66,1000,84,-178,-522,-1000,-39,974,-242,455,-938,617,560,356,-231,189,-1000,970,1000,707,1000,-434,364,-696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-966,364,294,806,-403,-433,247,-475,-791,113,667,479,132,492,829,-241,-290,393,-848,594,-900,904,923,-496,-572,-659,-138,-236,-988,-939,-923,888,298,-347,-408,-246,-44,215,141,-730,255,76,193,-1,243,-679,-722,-944,-443,-304,-376,762,-277,-890,-215,-958,-283,-185,567,338,251,-416,824,-39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-409,-71,272,-702,-434,1000,529,-741,-597,1000,-680,-312,133,494,-1000,-1000,153,520,1000,139,-805,164,16,1000,-1000,440,-432,-1000,1000,-976,-1,491,264,-180,-167,-67,-740,-858,-554,-393,-939,-1000,280,-392,-495,788,328,-276,724,-1000,1000,-199,-60,570,-316,154,527,-344,-1000,-125,1000,141,-400,-105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-501,775,547,449,-451,-259,423,1000,-669,-603,570,-856,273,-125,-1000,113,-487,-143,-1000,-759,178,49,-1000,-125,400,-194,199,913,627,-684,431,769,-861,1000,548,-984,-114,-1000,-80,295,953,-40,-434,157,-926,-1000,-73,1000,-62,225,-400,1000,-470,-364,-410,-957,-1000,880,60,-352,1000,-452,-1000,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{-1000,417,-1000,61,-608,398,98,882,-1000,1000,-349,625,-461,905,579,-363,-391,484,-1000,505,192,729,-772,1000,-189,-52,1000,-345,291,-128,272,-543,414,547,-471,-804,348,-158,734,-1000,926,683,226,-771,-202,676,-346,604,-1000,-367,-493,13,-569,-1000,1000,1000,374,402,-713,1000,-1000,654,-413,762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{92,-848,-816,-1000,625,1000,-813,-148,-438,274,564,-1000,-481,668,-339,358,292,1000,1000,358,126,534,1000,-893,353,73,-582,-479,-378,309,669,-327,485,-1000,-97,195,49,302,746,-328,-755,-565,1000,-1000,74,-25,-141,52,496,21,-1000,1000,411,110,-1000,483,-122,-462,735,-1000,-8,395,1000,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{-917,417,-1000,-600,-694,398,98,505,-1000,1000,-606,458,-976,600,1000,-556,-686,918,-1000,723,-656,301,-637,1000,233,389,307,-486,651,127,22,-97,1000,-183,-1000,-1000,662,-198,-61,-1000,1000,1000,-64,-1000,160,589,-368,584,-95,71,-639,-24,-961,-1000,1000,862,1000,991,-1000,1000,-1000,1000,220,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{-1000,49,-1000,-396,-1000,640,-62,601,11,1000,517,1000,-362,-469,527,419,-309,123,-71,1000,57,468,-1000,-814,343,343,1000,-228,651,-432,470,-720,453,412,-34,-478,428,394,567,-1000,1000,146,782,-545,150,157,-166,122,-1000,-909,-183,-176,-1000,93,1000,194,1000,1000,-1000,1000,-957,1000,136,-497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{-528,222,-296,289,-744,-285,-208,1000,-561,298,-197,81,-445,958,-136,169,-570,-22,-257,5,214,266,-694,704,992,363,-246,77,146,27,79,140,319,-177,-1000,-1000,197,-906,-489,-1000,1000,482,37,-403,-61,-680,-261,331,-1000,-374,122,161,-1000,-835,1000,-1000,1000,1000,-639,89,-1000,561,797,636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{-296,-109,40,1000,799,498,-813,1000,-1000,624,-96,1000,-315,1000,1000,-792,-113,838,-1000,-595,-1000,534,-387,561,353,1000,1000,-456,-378,1000,-1000,376,485,500,-398,-435,350,484,-568,142,-755,-372,195,-744,1000,654,16,1000,-381,1000,-1000,159,-597,657,587,487,989,383,-989,265,-234,42,1000,870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{314,-302,694,-299,662,273,-781,-748,-477,-509,151,-396,282,390,-448,237,-413,367,-547,-447,-241,-594,8,-174,287,-263,904,228,-193,835,-466,101,287,-773,731,190,-311,-186,162,1000,298,-822,777,110,534,-699,-154,678,-191,1000,-1000,781,826,1000,-46,104,-655,-211,1000,-1000,-105,-771,-268,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{1000,260,564,354,960,-1000,617,-965,-834,-1000,-1000,506,-481,960,-339,-1000,-650,1000,-1000,-874,-1000,357,1000,-271,54,-492,-559,-1000,1000,1000,640,1000,1000,-1000,393,729,-1000,302,177,1000,-616,157,726,-407,202,-292,-802,318,1000,21,-1000,-172,217,795,-962,-604,-613,-1000,1000,-922,-8,-1000,937,247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{6,-666,-341,-350,-98,1000,-1000,70,-116,682,1000,-961,-37,-142,-201,1000,678,445,973,863,-1,469,516,-923,-24,10,443,-479,-1000,499,612,-707,327,-530,-117,338,205,249,746,-89,-450,-932,729,-714,533,-298,-235,-166,-509,-361,-819,661,282,230,-113,732,-128,-230,514,-1000,-343,771,948,209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{-535,-228,-862,-256,-333,135,-868,744,-181,984,1000,550,746,345,-414,147,313,636,203,471,287,679,-399,-452,-227,10,1000,-94,-206,-223,649,-639,346,-530,-578,358,156,695,858,-521,-77,-932,1000,-649,-516,-298,-92,247,-755,-557,-763,455,-698,-121,1000,268,-135,41,267,-1000,-463,771,244,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{-1000,792,-617,91,778,-396,-732,1000,-985,1000,-996,1000,-1000,619,-729,-908,-382,1000,-120,304,351,572,-523,-24,-664,-566,426,-190,752,-1000,958,198,474,457,-1000,485,205,-224,975,-861,482,99,1000,-445,-1000,-140,-604,185,394,-779,227,-659,-478,-394,779,-400,-672,-643,40,940,-343,1,-638,577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{-332,424,119,189,-511,592,-803,-165,-160,-290,412,-244,-320,-53,-336,-636,239,-69,-14,-41,-563,400,-1000,1000,717,380,521,-176,-300,184,30,-386,-259,-545,1000,-540,-522,621,1000,-150,902,348,498,168,728,46,-83,179,-1000,-478,-707,511,-1,63,394,816,-233,568,-87,-385,450,270,637,266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{476,1000,168,358,-624,-514,1000,806,-1000,432,-336,996,-739,884,1000,-352,-1000,-502,-1000,538,-1000,76,-434,1000,497,-342,-525,-1000,225,1000,-372,388,346,-1000,-40,-1000,9,-119,-179,-741,1000,520,-1000,-502,1000,270,-1000,190,-573,26,618,-853,-1000,-1000,1000,1000,959,469,670,1000,-829,-590,-690,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{-120,584,284,-507,299,-624,1000,1000,559,677,-552,867,-1000,-285,-1000,15,-1,-881,1000,713,320,133,-168,-647,-429,-1000,-860,-283,-117,-769,1000,426,262,-306,-1000,840,-484,-1000,1000,-496,307,-1000,91,-125,-1000,-1000,-861,-416,991,-1000,849,-839,-870,144,684,-1000,-879,-138,638,4,-344,25,-300,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{926,-800,424,-1000,860,1000,-1000,-1000,-1000,632,969,625,-139,117,185,-363,364,921,-1000,429,-869,-647,-772,-475,-247,-147,616,-837,-793,1000,-44,-114,855,-1000,419,24,-62,-552,496,917,926,-1000,725,-957,1000,-449,-404,604,57,1000,-1000,773,1000,865,-302,1000,-562,-828,-713,-1000,13,285,947,-143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearAnnotations():void",
            new int[]{305,766,198,28,445,399,-723,631,-346,-762,-224,38,-777,938,-796,-671,820,-252,212,-580,-127,907,-401,407,404,-93,-278,-987,202,962,17,365,-338,-771,553,-387,31,129,734,-300,-654,432,595,-30,927,-287,-524,252,-98,-781,-691,536,-2,262,-796,-544,-460,-186,602,-611,485,-849,-144,184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{-1000,559,805,-298,-978,-528,-597,56,-23,1000,-485,143,-1000,39,743,1000,456,50,804,1000,802,-867,-769,751,142,-1000,-1000,-1000,-1000,-845,-1000,-1000,767,769,-560,-757,-575,-171,1000,-881,500,39,-1000,1000,989,-164,-1000,-800,244,524,-781,-1000,679,1000,768,923,-846,-1000,968,-393,385,-666,-471,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{-733,-152,-777,699,295,-795,50,36,1000,-93,-1000,639,709,-31,-924,-477,-1000,1000,278,-1000,-693,1000,-1000,821,-818,1000,1000,-1000,916,1000,1000,-1000,-789,-570,1000,-598,1000,-839,-1000,410,-123,453,527,-1000,149,-1000,-1000,593,-1000,432,647,-174,-342,1000,-1000,-1000,-1000,1000,-1000,-139,1000,578,453,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{322,-1000,-841,309,415,11,566,99,-58,-992,860,-348,377,-64,768,1000,524,1000,197,1000,570,-1000,861,1000,-546,-305,-689,896,-713,-878,-127,-62,26,-285,-456,-1000,-839,-182,817,1000,-886,-44,144,986,-613,-311,488,226,-661,652,-955,-370,-889,577,1000,885,-390,-1000,756,-1000,-347,-1000,-88,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{71,-862,48,2,1000,255,1000,721,516,-1000,480,415,697,-257,1000,-633,68,1000,-1000,290,27,-789,1000,434,-511,312,243,1000,464,-900,1000,1000,-269,-1000,-546,-1000,-402,-500,139,1000,-1000,-942,1000,288,-812,-641,1000,341,-1000,1000,-254,811,-1000,786,152,879,-548,870,146,-1000,-19,-900,82,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{-440,-152,309,822,-157,348,221,497,728,1000,-103,-692,-414,445,-924,-112,-1000,-669,609,-481,751,367,-1000,600,-136,301,1000,-805,218,760,1000,-1000,-136,526,488,-598,501,-137,-24,-1000,-289,729,-133,661,-533,-1000,-1000,289,212,617,304,-898,262,818,-1000,-637,-1000,-541,702,-488,1000,-642,-696,198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{367,138,-506,-283,272,-152,-1000,297,1000,571,-1000,1000,502,144,31,-466,-1000,-1000,-625,-1000,-1000,1000,-1000,-566,1000,946,1000,-1000,792,1000,716,822,-112,-601,1000,-19,1000,-1000,-352,321,1000,-375,302,-1000,1000,-881,-915,600,-232,133,1000,-473,874,433,-1000,-1000,-9,1000,-1000,1000,572,1000,35,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{179,-262,-977,-328,417,-875,182,245,-199,-848,809,64,-591,85,1000,942,743,760,-581,1000,446,-1000,340,-280,149,-1000,-982,420,-1000,-1000,-188,115,82,-64,-978,-729,-684,515,396,124,-311,-600,228,956,-111,53,569,-226,112,862,-850,-153,101,264,156,772,-212,-579,787,126,-571,-231,-638,-695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{-143,-400,109,479,270,-44,-434,354,1000,1000,-553,-877,286,61,-1000,1000,-1000,-753,-7,-708,71,1000,-1000,682,722,781,846,-397,1000,633,-361,156,156,-495,1000,-299,846,-1000,-656,-7,-623,229,182,330,-145,-1000,-842,-221,58,-37,648,-780,-279,703,443,-1000,-26,142,-295,-272,1000,-520,-27,35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{-794,931,340,-96,-215,-208,142,413,645,539,-630,937,-1000,472,1000,-925,-593,-845,-869,-873,-81,362,149,-1000,113,-235,873,-1000,384,-308,1000,-415,192,471,-102,-278,552,1000,-239,-805,393,-341,465,-264,200,908,-860,336,-250,1000,682,179,1000,372,-1000,-418,-554,938,30,1000,666,1000,-928,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{-631,-113,1000,32,-555,-162,603,658,1000,774,-760,-957,-379,-663,165,1000,-718,463,54,744,-531,290,-595,671,728,287,-152,-270,469,-461,-463,-457,517,-732,424,-771,547,-1000,180,840,712,186,447,-829,-191,-1000,-828,-781,148,-224,-234,-773,-641,-267,863,70,254,-264,165,1000,883,-1000,401,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{966,-1000,-951,-3,700,-437,-802,60,1000,677,-485,-925,-38,473,-1000,1000,456,50,-970,-51,139,-254,-436,-117,744,-246,-130,585,1000,-845,-743,-452,-73,769,47,67,283,-758,-391,386,-627,-791,-224,-654,-645,-482,-1000,-94,-225,345,1000,-796,-737,259,755,-1000,-846,-880,275,-861,-249,-852,-552,-390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{1000,126,818,0,-331,-1000,691,-199,1000,-1000,-1000,406,484,-1000,1000,-846,-81,792,-1000,-71,0,87,616,-3,706,428,-383,671,123,162,642,1000,0,-622,-121,-950,730,205,-249,1000,-835,-731,1000,569,-966,-308,1000,1000,753,1000,0,563,-963,403,-72,-119,744,1000,-258,-1000,223,-1000,0,125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{-491,-203,-249,123,-131,19,111,414,-131,-58,302,336,-591,213,999,942,265,415,255,594,586,-728,340,676,-513,-571,-503,-321,-663,-417,72,-998,36,466,-597,-944,-684,515,896,124,-128,-62,-218,825,24,312,-806,17,-487,868,-593,-504,29,170,356,934,-891,-967,878,-813,176,-807,-498,-708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{626,530,-911,281,156,-582,-877,-740,773,188,-957,145,327,607,-357,868,808,790,-717,959,-844,-989,-595,-52,-151,-458,-306,232,-814,779,-898,-254,826,733,930,79,887,-772,144,507,717,38,-770,-173,297,-335,981,-138,-823,400,873,-988,811,-55,-112,-659,220,-57,-958,356,27,-522,-788,821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{501,363,-236,1000,-1000,-1000,375,227,1000,9,-1000,-72,-316,-1000,720,-702,647,-29,170,-566,700,-375,296,623,-766,1000,-550,-387,171,-690,-537,484,-598,-196,1000,747,229,253,-1000,1000,-62,-950,1000,-38,189,682,-1000,1000,-85,1000,205,730,-1000,792,-135,-84,562,1000,-88,-959,-914,-714,1000,500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainAxes():void",
            new int[]{-141,308,-677,232,465,-446,-564,386,1000,677,-1000,1000,-550,676,812,322,-935,-1000,-908,-1000,-906,1000,-703,-1000,289,281,763,-1000,747,-332,1000,262,-48,220,739,-450,-105,213,-881,109,489,-257,648,30,231,618,-754,732,-395,703,819,68,1000,434,-1000,-792,-323,1000,-953,1000,834,1000,-950,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{1000,733,5,957,-642,-74,-73,462,118,603,273,-450,23,-779,715,-124,490,412,363,1000,1000,-358,-1000,-1000,1000,463,-587,993,4,-460,-1000,-1000,448,488,501,-482,-916,-881,-933,407,-393,84,996,-1000,-608,-300,-880,893,-978,723,-135,-1000,638,830,-278,-124,-1000,277,-1000,-19,674,-19,-584,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{-13,-294,134,212,1000,145,-935,475,1000,862,-455,258,-508,-292,-176,1000,-998,-1000,-1000,-1000,-212,722,1000,741,-572,-1000,-784,-492,996,695,1000,1000,-694,2,-392,1000,1000,-363,-415,-136,-104,-112,-934,1000,104,-539,1000,270,-258,-1000,212,308,-806,308,-551,1000,518,50,1000,-1000,174,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{355,-197,1000,-862,-198,-791,891,1000,1000,-108,832,764,-954,-1000,-525,604,975,-1000,-453,-789,326,-313,1000,1000,835,852,-1000,-291,145,372,908,-1000,444,383,-527,-1000,684,-287,1000,259,709,-113,969,-198,371,1000,90,1000,-322,-641,-928,150,-819,-107,725,90,-31,429,1000,-767,875,-384,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{510,-945,-1000,-342,-404,-88,491,1000,873,-253,170,364,-357,563,-199,92,847,-648,-899,-639,1000,1000,73,677,-777,844,208,1000,-24,103,1000,-502,995,-724,301,-208,957,13,60,242,1000,-317,-466,295,-705,-135,-279,1000,-425,431,1000,-75,-1000,897,-1000,25,-338,-899,-235,-814,-707,-1000,-821,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{227,-243,-166,316,724,117,534,1000,733,379,-834,129,-409,68,-918,650,730,-131,-520,-698,-104,499,-138,502,-420,-441,-184,128,1000,191,1000,-1000,-264,-463,51,885,-477,-204,-677,617,366,878,-311,741,-137,-599,832,533,-342,633,439,138,690,1000,-731,210,301,27,732,-909,90,-18,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{677,121,-890,-166,1000,-391,-1000,1000,838,286,-1000,582,203,388,-1000,192,596,-59,-1000,-628,1000,1000,1000,1000,-296,-892,-1000,-426,772,283,1000,818,39,-540,-329,870,985,-1000,161,699,-139,-113,-83,-142,-888,-949,43,1000,-1000,364,358,-660,-819,1000,-831,-491,-621,413,-479,-712,875,-389,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{-991,-573,452,-395,1000,285,-1000,-137,1000,1000,1000,449,-795,-1000,679,894,1000,-1000,-612,-1000,-827,248,1000,1000,-505,-1000,-1000,-668,1000,705,-353,602,-1000,-140,-996,868,-916,-388,741,11,-1000,698,-719,1000,248,-350,1000,20,134,-1000,-776,1000,-350,331,-209,564,714,293,1000,-977,98,-264,-530,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{1000,1000,559,124,1000,-1000,-1000,1000,833,893,-1000,79,-29,-1000,-22,-913,712,-311,-1000,-1000,843,897,952,806,372,-854,108,-766,1000,241,748,504,-709,205,-1000,597,230,-1000,344,533,-1000,-1000,254,-1000,-1000,-1000,330,855,-1000,830,-844,711,-1000,1000,-1000,-1000,-1000,1000,-1000,640,660,303,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{160,733,965,143,-143,-882,-275,135,-392,759,-451,363,-615,-329,219,-987,-687,797,894,966,72,43,-328,796,-549,162,-106,-693,-139,-246,-194,538,-552,-174,762,-672,-647,86,878,302,-393,-602,-264,-544,177,-302,-430,-613,-255,72,-802,658,958,-340,922,778,-903,591,-431,876,-649,-756,921,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{719,969,1000,-187,1000,-242,-1000,420,1000,1000,-1000,479,-288,-1000,-1000,-217,-880,-653,8,-1000,-578,784,1000,1000,640,852,-1000,-1000,1000,809,1000,1000,-1000,75,-1000,1000,1000,-1000,982,478,-1000,-78,-701,5,-60,-1000,90,164,-1000,-1000,-973,1000,-979,-107,-528,-253,-553,1000,908,28,832,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{85,-1000,-771,957,562,1000,508,520,486,-758,718,-1000,-1000,13,-1000,1000,-1000,412,-1000,-1000,1000,-1000,1000,298,-723,-421,1000,993,237,795,-1000,1000,-376,1000,501,1000,791,1000,476,732,-393,1000,-1000,1000,1000,1000,903,571,-686,-1000,1000,-1000,-650,1000,-27,1000,1000,212,1000,-1000,-78,-325,177,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{187,833,798,-544,-1000,-281,1000,219,280,424,1000,1000,198,-1000,1000,-745,854,342,1000,1000,561,-358,-1000,-1000,896,1000,410,837,-921,-455,-727,-1000,1000,-296,306,-784,-1000,-800,125,1000,-702,1000,1000,-1000,274,385,-943,612,78,507,-1000,-711,188,1000,411,-1000,-722,-336,-1000,668,1000,1000,667,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{794,774,303,1000,-862,-286,-101,572,1000,1000,-1000,-1000,634,-378,1000,27,597,-966,1000,163,23,-184,-1000,-1000,26,-391,-652,1000,917,205,930,424,272,-1000,25,678,195,199,-575,513,-1000,448,346,705,-724,-1000,270,1000,151,994,-837,743,-875,1000,-1000,348,-1000,-826,-573,461,-793,834,-1000,-674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{421,977,748,-435,-454,-366,239,496,710,810,593,511,-998,-721,-951,429,577,-297,-424,-272,845,-38,546,715,620,-162,-490,-476,598,406,218,-49,386,631,-327,-738,172,-616,659,-76,-901,-588,-90,-531,415,581,-72,624,-460,-819,-737,-758,-626,12,48,597,-690,513,514,-846,758,-690,310,-930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{463,-449,42,-100,-1000,166,597,717,-21,-736,940,265,74,124,477,-234,-701,786,-347,818,-104,-269,-835,-1000,184,1000,1000,420,322,-537,-505,-19,784,-121,627,-1000,-1000,-204,-496,602,905,-101,505,1000,163,493,-533,942,-623,315,439,-1000,-725,798,-201,-217,-549,70,-999,-612,661,-18,310,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers():void",
            new int[]{-202,-1000,-466,392,-664,463,863,872,644,-939,1000,1,-635,994,-689,1000,278,-253,-1000,-164,71,337,93,-45,481,943,1000,-241,-853,35,378,-121,691,758,857,-523,464,190,-354,79,709,334,487,837,537,1000,397,942,243,-964,1000,-1000,-727,23,358,1000,979,68,401,-855,86,-1000,683,-84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{948,843,294,-661,702,-845,21,-655,101,-105,493,-395,699,-761,-27,-363,-291,-268,463,56,-513,-551,146,-665,463,257,-319,240,-643,395,400,695,596,-520,-416,-873,98,-366,-1000,-760,757,-306,-985,364,-635,1000,607,1000,-381,994,431,821,13,-43,-832,-356,-819,-933,62,241,536,588,271,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{-14,689,-1000,722,-510,-451,-1000,-686,-80,-1000,1000,-811,364,706,-831,90,-547,354,-1000,274,441,-1000,1000,664,362,1000,1000,-1000,-914,-1000,239,1000,-1000,-1000,-696,1000,-1000,-672,362,-27,352,-1000,-1000,-593,-92,1000,429,1000,205,1000,818,652,585,1000,98,-1000,565,179,-1000,229,-746,1000,316,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{950,1000,859,120,-75,-304,1000,-786,253,1000,-316,299,965,-723,212,-583,-248,-586,1000,673,1000,495,-1000,-523,630,-1000,-203,1000,1000,449,-439,61,382,-184,747,576,1000,-60,-1000,-550,267,719,-762,1000,-173,-1000,-110,768,-522,-372,118,285,-556,-1000,-298,576,-351,444,1000,698,1000,-528,-755,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{546,153,-925,410,148,-365,-1000,-1000,-1000,-1000,719,-1000,1000,-582,-434,-573,-645,-444,-354,1000,180,-817,655,219,696,1000,978,-781,-76,-1000,-645,1000,-713,-997,387,-340,-598,-93,1000,95,743,-834,-881,-110,48,1000,1000,1000,-823,984,340,650,343,291,-577,-568,68,-235,-40,1000,-37,1000,-643,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{274,472,-527,599,316,340,526,-637,-416,16,506,1000,474,999,-807,243,-543,497,-622,987,282,-172,-421,566,-42,-346,-431,1000,468,-348,-1000,-46,-1000,-541,300,1000,-505,946,-1000,-613,-978,455,-849,1000,1000,-1000,629,207,1000,-1000,897,-229,-261,321,400,1000,312,529,924,-479,281,-830,-711,245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{-1000,524,704,179,638,-1000,683,-774,561,975,211,-365,779,503,-1000,58,279,-36,196,451,-1000,-1000,349,-741,143,629,-169,-66,-527,670,-174,271,70,-753,-406,-154,249,-477,-154,-1000,857,150,-12,692,-767,-509,-426,-486,-90,1000,-453,1000,-78,411,-1000,-684,-673,-400,588,-83,1000,-17,629,501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{-823,-1000,-602,-512,967,-93,782,1000,-177,-1000,271,-1000,-1000,1000,-1000,1000,621,1000,-1000,-135,822,765,-1000,676,-661,-876,-514,-1000,1000,435,-1000,-1000,22,-1000,-472,1000,336,-358,-1000,-1000,-1000,-1000,624,545,319,1000,1000,950,-1000,832,-19,415,779,845,1000,-44,641,-1000,-201,-931,-490,-616,1000,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{-746,337,609,515,625,-629,492,-309,-1000,-58,825,-986,513,-854,-640,39,1000,1000,372,799,224,192,-170,-127,427,240,13,-906,1000,76,-1000,216,-232,-410,325,1000,-610,368,-969,1000,259,361,609,-44,1000,1000,164,48,-439,450,1000,765,190,-344,725,-975,-1000,-695,972,378,1000,-71,1000,724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{1000,1000,178,-42,82,-407,973,-668,-537,1000,-65,561,1000,-1000,107,-880,-514,-934,1000,541,43,-422,569,-29,810,-688,1000,1000,-545,208,-19,124,658,593,479,269,814,-262,-158,-1000,437,-378,-486,969,665,-544,-734,-224,1000,-393,704,10,-169,-964,-29,455,-433,761,1000,356,1000,223,-1000,869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{269,1000,149,403,-537,-817,418,-854,-649,-669,-5,235,423,-1000,856,-244,410,67,-365,903,204,383,-711,240,333,-783,581,-755,1000,-742,-1000,-137,-904,-1000,898,-112,-670,-67,-183,-311,-583,-868,-1000,940,1000,1000,620,1000,581,-368,608,53,-921,-1000,272,-206,498,-837,674,518,-587,36,-644,302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{898,815,973,-892,753,-973,437,-565,791,592,333,-416,513,1000,-81,-328,-330,-598,987,-207,-528,-625,287,-1000,738,240,-215,720,-1000,781,1000,504,1000,1000,-839,-1000,609,-1000,-969,-1000,1000,-574,-48,372,-1000,1000,-737,-879,-372,1000,125,915,446,26,-1000,-390,-948,-387,209,223,778,1000,248,932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{403,1000,-368,234,-683,-817,1000,-830,208,1000,-502,502,-11,324,64,-361,36,-234,623,680,1000,-23,-111,-522,553,-1000,574,482,-214,280,-523,-702,280,494,1000,-819,1000,-94,-1000,-1000,-439,-213,-1000,1000,440,-1000,67,988,1000,991,-144,-89,-600,-1000,98,-804,469,176,789,572,-68,-887,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{948,184,668,173,909,-845,-159,105,630,-493,-84,320,-976,220,-743,-54,-291,390,-634,-596,1000,-1000,632,-71,808,106,-1000,764,-780,1000,400,544,596,1000,-24,943,1000,-84,-1000,-513,545,-182,-1000,409,-1000,1000,607,249,-492,950,-1000,821,981,912,-313,-662,-183,-188,29,662,-37,-459,-308,879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{1000,706,-177,-575,1000,-845,-292,-999,-172,-755,837,580,872,22,-128,-434,-414,-703,173,-148,-1000,-489,143,-665,321,935,-509,654,326,70,1000,1000,594,-821,-751,-873,-578,807,-783,100,757,-306,-380,-5,-1000,1000,236,-188,-692,994,801,907,156,-1000,-832,-227,-1000,-1000,-628,369,509,744,514,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{-338,589,260,1000,-1000,819,562,-603,-478,1000,-635,8,377,-1000,-807,768,-155,-481,-146,1000,1000,-293,-524,881,434,-1000,1000,-26,1000,-395,-1000,-572,-1000,-589,1000,1000,1000,-521,-602,-1000,-953,-1000,-1000,425,1000,1000,1000,1000,876,-969,-75,-584,-662,321,627,769,-816,1000,1000,645,503,-341,-1000,501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearDomainMarkers(int):void",
            new int[]{823,29,-1000,-844,424,-845,-1000,-599,-1000,-1000,1000,21,699,-368,-27,-212,-746,-245,-862,638,-180,-1000,603,-232,257,1000,-319,682,58,-624,-66,1000,485,-1000,-60,-340,-1000,726,1000,388,592,-609,-662,-569,8,-253,729,364,-381,994,677,741,313,620,-832,-356,-295,-316,-594,251,262,366,-296,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{739,817,-1000,1000,1000,443,1000,-34,-359,-1000,-847,507,658,-249,574,184,-950,-907,-664,1000,-147,1000,90,-236,247,-1000,4,293,-379,-559,293,530,910,-1000,-1000,614,1000,-702,-589,-1000,961,-46,1000,-984,-490,207,298,588,1000,1000,-402,-939,-66,1000,459,-625,304,925,-1000,-1000,-520,-119,-2,467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{878,-844,-947,624,-554,671,240,-1000,311,-83,171,342,1000,525,538,-1000,-1000,544,-1000,1000,-112,1000,-89,-495,-820,-492,391,-996,-362,-1000,-316,553,985,-1000,-48,1000,831,-1000,250,1000,235,-1000,-681,463,728,339,-1000,767,-1000,1000,1000,-1000,1000,-381,677,1000,315,1000,377,330,-327,492,-371,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{684,1000,-286,624,301,-957,38,8,-199,-1000,171,9,-359,-1000,155,184,-1000,74,-175,1000,-112,172,1000,-1000,-820,-492,-879,131,-737,-240,-711,631,1000,-1000,-1000,1000,352,83,-1000,-1000,1000,-200,588,-1000,-1000,-664,-251,1000,428,-99,-541,-1000,643,263,-861,1000,1000,133,377,-1000,72,682,1000,684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{1000,-855,41,455,-670,684,531,-78,403,-458,-150,893,764,359,597,376,-1000,602,164,683,370,1000,443,-284,417,-876,473,-270,-1000,-1000,-1000,223,312,-318,-372,-956,1000,-1000,1000,1000,1000,-1000,-437,-1000,534,-307,-40,130,-668,1000,-185,302,644,-1000,-191,532,671,890,-41,-1000,-448,626,432,784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{207,-1000,-516,-477,776,1000,-156,427,1000,-134,514,219,523,-78,852,-1000,-347,-58,-744,1000,-1000,1000,-893,-150,-517,-931,21,-357,-68,-664,-589,708,-563,-1000,-78,695,1000,-837,-393,327,571,266,526,-73,159,149,374,748,942,1000,20,-1000,1000,345,198,890,-256,1000,722,-158,-1000,-50,-736,-244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{1000,-282,824,-809,-956,464,-260,-514,104,-1000,1000,1000,716,492,101,-300,-429,348,-1000,196,-456,1000,888,12,-930,-1000,1000,-980,-1000,108,178,384,555,-8,271,1000,762,-1000,1000,1000,965,-1000,-1000,140,1000,-181,-1000,754,-978,773,292,581,1000,-1000,-1000,1000,1000,1000,-467,-687,-57,1000,265,241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{1000,-1000,-887,533,-523,-299,1000,232,641,-1000,-1000,-627,1000,336,1000,-1000,-1000,602,-386,902,-14,-183,155,-532,1000,-298,-872,0,-1000,-637,936,295,-1000,733,-640,-1000,760,152,872,-787,723,-1000,-78,166,1000,-316,-294,-20,-414,1000,-435,844,-379,-764,-243,-104,-115,36,1000,204,395,142,-400,484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{542,1000,-411,48,27,-28,567,-636,749,-916,-889,-178,584,-1000,533,184,-1000,493,-1000,1000,654,172,986,-1000,52,-355,-246,-681,-1000,-891,-73,715,1000,-1000,-761,862,481,52,-650,-674,925,-1000,181,416,-284,-85,-947,1000,-233,230,182,-1000,817,-52,-914,520,1000,290,917,358,-139,438,-268,-468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{563,-1000,-443,-307,445,-28,1000,-235,723,1000,777,-418,978,1000,533,-1000,-600,191,-1000,1000,-670,1000,238,653,52,-355,-246,779,-121,-903,608,124,1000,-488,-522,1000,481,-1000,927,1000,-1000,226,-250,416,-400,246,-1000,-791,-905,1000,131,457,1000,-52,42,581,-283,636,-324,358,-139,438,-1000,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{41,-1000,-816,-35,-487,-831,710,15,839,-1000,218,-541,408,8,710,-683,-520,159,-750,424,497,-304,86,-929,-352,287,-542,-1000,-1000,-696,399,966,-1000,148,-669,-1000,648,-13,858,92,333,-376,-182,-794,727,272,-179,-129,43,-143,-190,865,121,61,-449,261,155,-515,1000,624,356,-136,-149,164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{717,777,95,-482,-139,-653,-288,895,-94,-514,-329,56,-984,-322,-284,409,-432,264,163,-352,914,-776,895,-120,267,-31,129,299,-655,306,130,451,-660,75,-32,-156,236,413,-96,-708,545,49,94,-790,105,-374,-124,-139,-226,-390,-860,485,-455,-935,12,164,899,-731,-616,-685,73,-17,738,781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{1000,-1000,4,-271,-1000,-653,-807,542,-1000,1000,-240,623,248,996,-231,-609,-828,-101,445,867,247,686,-108,1000,-285,-31,129,475,102,306,-746,757,-582,-1000,923,220,549,400,-110,-124,760,-743,-783,-1000,1000,-374,-21,-167,-1000,246,-294,485,178,-935,1000,624,746,-731,-1000,-1000,-594,-42,738,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{-371,-553,439,-745,-1000,1000,-812,-671,3,211,-584,-116,1000,-41,-115,-1000,-533,-443,-262,-842,1000,1000,-1000,-150,-749,254,743,-1000,-428,-749,-347,89,-205,898,275,862,286,958,99,1000,-441,-447,366,783,1000,696,-12,-828,-227,554,189,-172,1000,-533,-914,937,-187,1000,704,1000,159,-883,-1000,-861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{582,-544,359,332,-670,832,996,-480,724,-647,1000,1000,1000,-38,-264,-389,-772,-23,205,211,105,756,-107,-708,294,-486,473,-1000,-1000,-962,398,-91,-178,-318,-314,-888,273,179,752,1000,1000,-1000,-125,1000,1000,454,-112,139,772,210,-795,561,1000,-329,-836,287,603,1000,519,865,-178,298,666,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{185,-525,-1000,-585,1000,32,742,1000,-606,208,-1000,-1000,-532,944,797,166,169,-108,-722,1000,-1000,1000,-214,1000,-376,-1000,1000,1000,1000,1000,32,490,1000,-1000,225,-1000,1000,-879,321,-1000,-1000,1000,119,-992,-1000,-534,6,-1000,-959,1000,-1000,-723,-693,41,1000,656,-902,-79,-1000,-1000,-1000,-532,-907,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeAxes():void",
            new int[]{385,896,-491,985,27,587,567,895,1000,-514,-1000,125,1000,-1000,899,-306,-1000,-450,-1000,-352,859,172,189,-120,873,-355,-864,-894,-655,-1000,545,339,-660,-836,-1000,-156,481,52,-1000,-408,1000,-826,1000,1000,89,406,-124,828,1000,230,42,-1000,1000,10,-883,-461,634,702,1000,1000,-139,-213,-301,-807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{-625,711,-1000,-386,-211,817,-1000,423,398,127,1000,-366,-700,-59,-463,-1000,348,-526,363,1000,-1000,360,284,-136,-284,1000,-1000,-349,-1000,1000,956,61,462,-226,424,-781,83,-175,-61,-539,326,-87,-283,-445,-407,-841,548,-397,-1000,1000,-1000,-434,1000,-117,5,-434,-325,-809,622,721,271,1000,876,-503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{-489,1000,-296,55,431,-225,510,869,-1000,240,-544,218,1000,-1000,92,-270,-100,-203,1000,-1000,198,-540,127,743,229,323,-495,-478,499,-300,-1000,-3,731,-384,-590,-262,26,674,36,122,649,-719,-777,-409,912,-775,-48,-206,1000,1000,526,-813,-116,170,86,-943,457,540,614,-253,-239,415,-776,-809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{-361,-257,-983,589,1000,1000,-437,139,435,541,70,951,-340,-414,-1000,856,289,188,826,674,-361,839,-1000,-417,534,925,69,553,-669,214,1000,37,-195,651,-157,629,653,-140,-713,-319,1000,-94,-840,128,1000,-958,643,-1000,-1000,-1000,-294,614,529,-510,19,260,913,101,49,-142,-1000,-204,478,-651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{433,-15,-336,-1000,-62,126,-1000,-86,-2,98,828,-879,-305,-1000,1000,-198,-262,-415,1000,484,-1000,-581,1000,-459,-1000,599,-1000,-1000,-245,339,605,-190,161,-1000,1000,-1000,-1000,542,-1000,-719,-87,-247,898,-480,-19,-1000,-82,-810,348,1000,119,991,181,229,-136,-1000,0,301,-627,1000,357,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{-330,256,-296,486,483,-101,282,793,-853,-1000,1000,156,361,-823,278,-1000,224,-968,1000,-664,-207,-367,-641,-318,671,542,732,-243,164,-177,-874,478,361,-130,-89,-216,-369,-159,208,168,-12,-1000,1000,583,-84,-1000,-288,-556,-725,1000,460,880,-36,-1000,-1000,581,373,-1000,541,1000,390,532,-897,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{-440,-619,404,-136,-1000,202,-188,-56,-905,349,-367,625,202,384,-291,379,629,371,157,55,-600,299,298,720,440,-3,398,73,-667,523,233,-319,578,447,457,889,-245,431,221,-45,-97,228,98,-459,-1000,81,812,-391,41,808,-626,250,706,-90,878,-350,639,-164,-1000,217,1000,789,396,-470}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{-746,537,-280,-539,-1000,-26,-1000,-591,-66,79,961,38,-1000,-1000,-1000,-156,1000,54,-517,-258,-62,63,513,503,-961,1000,391,-1000,-1000,1000,181,287,1000,239,977,-603,-1000,674,847,-1000,979,898,59,-829,-768,-213,855,-1000,-689,-160,-480,-974,1000,-857,1000,-61,303,1000,602,1000,735,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{466,1000,-631,239,163,654,-129,1000,237,32,391,-494,-1000,-1000,605,-1000,-663,402,600,727,-137,-506,295,-1000,173,-380,-1000,-619,-591,316,1000,230,-752,-910,1000,-836,-1000,370,438,-526,-359,-617,-121,-179,466,42,-608,-724,-1000,1000,1000,-226,22,364,1000,-661,-399,-659,-145,1000,1000,115,-16,32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{-414,-1000,-296,1000,211,-299,1000,145,-524,477,-1000,1000,611,-120,-816,-815,-325,926,-277,48,177,1000,-75,1000,1000,-694,660,901,-1000,-718,1000,-303,560,1000,139,1000,1000,705,617,14,-97,720,262,-312,1000,-713,90,302,-297,-854,-805,1000,-174,-140,-85,-271,1000,-694,-1000,-451,-175,296,647,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{1000,1000,-36,53,554,-271,647,1000,-1000,-1000,-147,-331,-609,-730,1000,625,-1000,330,1000,-301,1000,-977,694,-1000,-1000,-1000,-792,-882,1000,-1000,357,227,-870,-867,168,-699,-1000,615,986,-713,-1000,-1000,412,655,-156,1000,-1000,-189,217,1000,1000,77,-1000,-736,1000,-63,218,866,-1000,1000,1000,31,-1000,373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{1000,1000,-546,-840,829,611,-252,1000,-274,32,1000,771,-958,-417,688,-1000,-1000,-274,718,429,-476,-245,-932,-1000,-767,1000,-1000,-929,-686,-350,764,1000,-787,-384,-89,-1000,-1000,-484,395,-1000,-694,-1000,-529,67,364,48,-1000,-990,-986,967,502,259,-23,-1000,1000,451,-1000,333,-373,834,784,-429,569,-334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{1000,539,-759,-715,656,913,-953,-107,-411,-221,636,19,-611,-1000,-230,-959,-576,-38,1000,652,-773,-308,15,-926,-637,763,-496,-918,-110,-132,932,356,-267,-749,434,-350,-504,343,-1000,-9,201,-670,-238,-42,-22,893,241,-950,-167,1000,-710,-127,420,-373,-46,266,-333,573,-431,947,191,137,140,-802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{-1000,624,-596,-783,-787,766,-944,319,283,202,924,-163,-214,-1000,39,-1000,-493,-1000,405,-1000,-1000,-313,403,207,353,1000,-80,-839,1000,669,-1000,-117,868,-719,972,-788,-1000,162,317,859,-1000,-795,-225,-848,-579,-527,-807,-1000,-653,677,-626,-698,451,-868,-1000,-804,-333,-1000,1000,869,698,282,363,-807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{964,-848,-57,997,897,-538,1000,1000,-504,-751,-77,581,346,1000,299,-736,299,734,1000,555,753,1000,-115,480,1000,-898,-755,1000,-721,26,1000,-1000,-155,631,-1000,-1000,1000,-1000,456,-1000,-518,-325,-150,502,-19,-1000,1000,453,-656,1000,-622,1000,611,569,-698,-1000,497,-881,-755,-557,264,120,-555,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{190,-15,169,902,286,-226,1000,1000,-163,-50,-129,649,-305,1000,-291,-736,596,-35,857,82,224,519,-188,903,1000,-470,-151,1000,-245,-327,961,-1000,-388,631,-145,658,1000,225,-71,-745,-87,-45,-139,351,-19,-1000,391,-10,-655,546,119,991,63,-180,-136,-1000,0,-460,-627,46,-276,296,-294,263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers():void",
            new int[]{722,-624,263,751,1000,-900,1000,1000,-1000,-675,-273,825,670,-679,-816,212,-790,467,1000,-700,1000,567,-1000,-646,-401,-984,393,-180,-371,-1000,-21,-48,-885,203,-1000,-739,100,-116,580,-8,209,-897,-127,358,1000,-1000,-167,-22,-46,-682,1000,1000,-668,-460,-427,-8,63,331,-761,-429,957,-1000,-1000,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{-671,-113,863,-522,310,-1000,-457,-325,-933,282,69,510,458,-672,-1000,1000,-249,563,-1000,622,-1000,-377,-212,956,976,-377,9,-744,-465,768,-519,162,-923,-1000,-179,385,-582,98,-29,144,1000,-1000,-1000,803,-158,233,-83,-368,-796,-225,-1000,371,-1000,687,39,-323,-1000,-761,631,260,1000,1000,384,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{104,930,-496,930,-1000,-1000,1000,290,249,782,-697,-1000,-26,-96,-233,1000,-475,-529,-1000,733,-590,-171,-1000,1000,1000,-595,-1000,-1000,-93,-387,276,425,-1000,819,1000,1000,-1000,1000,1000,510,1000,-1000,-596,260,-1000,180,1000,-436,1000,587,-1000,554,-1000,86,-137,363,-1000,-141,1000,-754,90,-163,621,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{1000,-1000,-571,789,166,29,1000,184,-931,-1000,-928,-127,551,-747,-554,292,1000,211,-121,-1000,-1000,317,1000,-88,484,-675,1000,1000,-1000,-961,105,-1000,-1000,-394,-1000,-498,23,222,-1000,1000,668,-400,603,-1000,814,1000,-421,1000,-1000,-794,-471,-34,1000,-887,560,110,327,112,420,318,-256,867,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{139,1000,-401,151,999,893,-54,-404,485,603,-308,739,-1000,880,953,916,964,-779,-1000,536,-458,-816,-561,-447,301,188,-456,41,664,54,-254,658,678,-617,314,-37,-811,769,810,733,-21,661,-442,573,-1000,-466,-1000,-607,219,-796,1000,-589,-122,182,488,1000,985,-455,926,433,-403,-583,1000,-255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{547,-643,-1000,789,1000,-88,231,-1000,-931,-1000,-1000,-681,505,-509,871,292,1000,-685,-871,-139,-1000,-1000,1000,-496,289,-100,330,1000,-1000,-762,-1000,-1000,-925,-394,-531,-1000,-831,739,-128,1000,-324,-195,-1000,-1000,511,944,-1000,1000,-1000,-1000,-850,430,-858,586,560,439,831,-493,1000,1000,226,-903,-774,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{-93,-21,-504,332,8,-1000,885,1000,-771,165,-370,541,290,-541,-421,411,273,1000,-1000,99,-632,998,-788,1000,-546,-886,-802,-1000,-759,-236,-18,-1000,-1000,-143,-524,648,-191,829,1000,-639,910,-1000,-442,-632,-24,920,-345,129,304,2,-1000,585,-1000,946,-439,439,-953,-116,-307,-1000,352,1000,595,-512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{359,651,-946,1000,55,-200,141,1000,-274,-222,-808,-72,504,-76,382,98,526,1000,-259,-142,-308,-402,-1000,661,-246,-1000,-1000,-754,2,-508,179,-570,-627,1000,-141,226,84,471,1000,1000,-136,-1000,526,631,-1000,619,20,1,821,-58,171,78,-1000,-1000,-162,860,-787,-185,-146,23,-827,1000,169,119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{-46,-113,-495,-569,853,768,-23,129,-906,-503,-816,423,-51,-556,188,714,743,146,-752,241,-955,484,406,154,648,-377,272,-607,699,545,-258,53,-676,-983,-917,811,946,-817,-722,294,96,-373,-183,776,-2,688,-763,477,-558,-881,-391,-75,547,-256,-87,329,886,-313,-221,-277,167,934,434,-257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{-27,-113,1000,-438,38,-537,-999,924,-536,366,984,958,437,-938,-38,993,-288,1000,-225,375,187,1000,366,1000,-541,-709,-1000,-1000,952,482,1000,508,34,-688,-348,981,-103,-1000,662,-144,1000,-1000,423,1000,-1000,30,659,-1000,704,374,-316,-790,-836,33,-201,-741,-1000,-1000,-1000,-714,563,-435,385,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{151,176,-76,-548,802,610,206,523,297,387,-511,673,30,-76,265,1000,346,-750,-1000,442,-511,45,-606,180,1000,-408,-197,-1000,1000,359,592,739,-627,-1000,-92,994,-162,-507,360,-163,1000,-655,-932,1000,-1000,128,-675,1,842,-718,-603,-354,-646,854,659,1000,848,143,195,-543,609,-137,1000,307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{-234,-1000,-52,312,1000,1000,-817,-442,-657,-1000,361,679,-884,-74,-190,-86,380,256,27,-103,1000,523,-613,-589,-700,461,815,-20,228,739,5,546,697,-1000,-1000,475,182,-1000,-56,147,317,49,608,230,-224,481,-804,-332,-635,-832,504,-1000,-109,-163,-519,-1000,654,-1000,-1000,760,9,81,-938,-284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{-351,-307,1000,1000,-622,-613,-1000,829,-1000,49,853,1000,-446,-823,1000,50,-755,1000,-869,-90,-1000,1000,-1000,572,-99,-1000,-638,-1000,878,810,425,141,351,-1000,-388,397,1000,-1000,438,-376,1000,-1000,890,1000,-214,-1000,1000,-1000,144,830,1000,-142,-1000,-627,-83,-1000,-1000,-1000,-551,-244,1000,-359,10,582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{-815,-1000,968,-939,281,507,-1000,-1000,-28,938,-1000,-1000,-240,-819,420,480,-320,-1000,-22,-688,1000,-1000,1000,-1000,-372,1000,458,1000,-7,1000,-661,1000,-205,1000,1000,-1000,113,-1000,-1000,-187,-1000,1000,-624,943,-1000,-1000,438,1000,-586,-420,-13,159,680,-302,486,-854,1000,104,759,668,-1000,-1000,-280,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{451,851,-519,1000,-1000,-1000,488,-193,-126,1000,-941,-416,-98,-685,193,776,-126,262,-1000,1000,-897,-293,-1000,769,-368,-824,-714,-1000,-496,-389,-246,43,-716,328,454,1000,-919,1000,1000,1000,992,-1000,-414,36,-1000,189,-421,1000,-337,1000,-394,51,-1000,-543,-285,-183,-697,-1000,1000,-723,578,450,354,-952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{-436,1000,9,952,-1000,-1000,922,678,658,1000,-345,-1000,425,619,159,1000,-1000,-1000,-673,970,-799,313,-1000,1000,460,309,-1000,-864,1000,-63,912,1000,-1000,1000,1000,659,-723,966,1000,82,1000,-278,-1000,-262,-1000,-736,1000,-497,1000,957,-1000,1000,-1000,492,458,-133,-1000,367,624,-1000,874,-1000,902,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clearRangeMarkers(int):void",
            new int[]{-984,-568,-761,-7,-414,-893,-607,-1000,132,938,-1000,-1000,728,659,801,558,-829,-1000,-143,81,902,-1000,677,-1000,-1000,1000,-74,1000,52,769,-738,1000,-832,1000,1000,-1000,707,-154,-582,21,-1000,1000,227,935,-1000,-1000,896,1000,327,282,-637,1000,-314,330,784,-897,150,-169,824,-362,-368,-126,-280,871}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{-708,853,-1000,-1000,393,247,303,406,1000,-1000,-322,272,-712,-1000,949,-507,-1000,-1000,77,923,492,-1000,1000,-297,1000,-1000,159,1000,304,-719,1000,-428,-931,-1000,389,-1000,-654,-661,668,-1000,1000,30,-1,-1000,1000,-661,646,-1000,-1000,-1000,-434,553,-757,1000,1000,-1000,78,-1000,1000,-6,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{-47,252,9,-1000,1000,884,818,-489,-672,-148,-427,142,117,105,125,-37,-908,452,-263,1000,606,-784,1000,-133,1000,-1000,-114,-436,-481,-3,-184,-62,-229,1000,369,813,-1000,-467,435,718,-490,-1000,-111,543,156,29,-302,979,197,-489,1000,-785,-325,-431,-1000,520,-121,-868,845,-263,900,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{558,216,559,268,-220,733,-113,164,-425,253,-74,395,-261,9,-703,-495,537,-3,-348,305,649,854,211,-681,-144,-774,-461,-105,-284,-365,570,726,-570,863,989,187,-885,-481,-457,-154,489,77,164,674,157,635,-370,630,1000,92,380,83,-267,-448,-1000,281,-290,917,39,165,-56,316,590,-661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{-578,239,-958,936,581,329,630,710,-76,707,-902,262,-697,-80,42,352,574,-86,17,-158,294,495,-485,-806,-587,-15,-741,-126,-953,-775,730,225,-409,473,204,-812,-625,493,-312,426,897,197,96,838,-742,-427,-230,-746,815,294,-983,-828,339,-575,730,-197,-23,-75,340,409,-830,-720,-632,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{-1000,-95,-1000,-1000,-340,-1000,-79,120,1000,-37,-702,-526,-1000,-1000,-776,290,-393,-1000,38,285,-789,-1000,1000,-22,1000,-281,683,685,254,-347,92,-1000,-258,-816,-1000,-1000,1000,-661,1000,-970,958,-344,-746,-519,1000,-592,-212,-1000,-1000,-732,788,311,-868,1000,612,-1000,106,-1000,1000,-535,483,-274,-1000,996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{-96,-773,-788,903,385,-570,-1000,148,749,165,627,979,-1000,-769,-1000,-903,802,55,-1000,890,532,-69,666,-789,-67,117,-86,1000,-1000,-185,410,-981,-80,784,360,-1000,1000,18,-750,17,1000,1000,1000,-588,1000,-328,1000,-272,-736,-315,-98,1000,-416,467,-931,-1000,325,442,778,202,522,19,-284,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{516,629,-1000,828,944,-303,702,-206,-92,-148,-74,417,-751,-544,-703,-252,-44,-3,-164,-1000,-133,1000,-818,-681,-1000,234,-1000,-139,-563,705,-353,-903,-265,299,-394,17,163,448,689,-762,-270,25,-758,1000,157,366,-833,-485,197,1000,-1000,402,-51,-1000,793,-392,-290,875,-1000,476,-1000,-1000,457,867}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{8,-272,-936,1000,563,-760,-13,235,-425,592,-250,519,-1000,9,-430,-303,1000,148,-572,-1000,-307,811,-879,-670,-1000,636,-928,590,-1000,673,-360,-1000,-34,330,-487,-1000,1000,687,269,-356,489,698,7,1000,134,635,-382,-665,203,1000,-1000,664,-267,-1000,-538,-799,-416,1000,-745,374,-1000,316,734,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{190,943,1000,-669,165,518,157,743,-959,922,349,658,400,1000,-955,74,610,1000,-348,801,609,-360,4,-490,415,-440,-692,-3,20,-334,1000,929,390,667,331,-455,-280,-402,-865,874,-500,-163,758,881,157,-202,375,1000,1000,-123,380,-724,807,229,233,943,546,114,668,-1000,680,-173,451,-853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{-8,-385,939,-851,-193,384,-13,365,-656,734,71,992,63,481,-518,-389,283,801,-245,1000,942,-358,256,-357,832,-484,-337,546,297,-75,1000,913,-76,978,45,-232,-808,-299,-712,419,-944,-128,654,744,-315,12,341,921,1000,-436,688,-57,199,263,-1000,785,305,86,809,-695,664,-509,16,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{-9,-304,939,-971,-338,283,574,164,-520,253,300,460,-260,84,-517,-417,537,-927,-314,75,332,748,322,-327,-268,427,-233,440,-39,-75,570,563,-606,978,-87,187,-132,-299,-712,310,255,-139,654,687,304,473,-25,361,442,-694,380,-143,-707,90,-1000,-20,324,635,453,19,-108,-360,290,-37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{-1000,79,-428,-842,390,21,642,446,-387,1000,-1000,-339,-504,649,-806,621,1000,216,336,-1000,-154,-1000,986,161,172,773,161,247,1000,777,759,-338,-2,1000,-688,-617,895,335,-3,1000,303,-1000,409,774,251,128,-265,30,-139,561,-1000,-1000,509,1000,-1000,-324,415,-173,1000,-665,1000,-521,329,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{-603,815,-982,-310,1000,851,1000,-250,14,-1000,-635,883,-437,-1000,379,-495,-916,-542,-628,-955,793,279,1000,-411,-54,-35,-988,1000,-481,-365,79,726,-1000,394,192,355,-885,-100,268,-976,203,79,-168,-745,762,934,-192,-573,1000,593,304,1000,-980,485,400,-1000,-536,-188,-519,165,-733,-495,-400,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{-316,103,1000,-103,657,932,-312,513,-1000,1000,149,-274,-43,1000,-1000,612,1000,1000,679,1000,235,-27,90,-402,16,-977,-932,-1000,876,-846,699,-480,641,1000,993,379,-920,-851,814,997,457,-1000,314,1000,-528,339,-655,1000,1000,-190,557,-1000,794,-292,1000,1000,-525,1000,508,-1000,783,629,989,-792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{379,-1000,-51,493,455,-319,73,-243,-351,111,-378,946,-503,-570,-1000,-934,1000,-358,-1000,-271,-12,233,78,-242,-1000,1000,-464,1000,-159,36,-1000,-1000,-584,1000,-983,-935,1000,-12,150,131,-158,606,473,888,1000,1000,-386,139,367,1000,-1000,566,-464,-11,-1000,-248,-118,339,-115,-178,-666,-1000,1000,849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.CategoryPlot", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "clone():java.lang.Object",
            new int[]{-1000,-1,1000,-45,-805,1000,1000,649,-1000,1000,-564,-69,370,1000,-679,258,1000,1000,907,207,336,-1000,14,-176,-438,-96,-781,-643,739,489,355,1000,-150,-17,474,654,187,-450,-592,884,264,-1000,79,1000,-805,978,-566,1000,1000,645,-4,-1000,536,727,457,1000,6,1000,392,-799,167,-9,1000,-401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{-1000,724,919,0,-841,-1000,510,941,-1000,354,-36,-205,-715,491,-155,-117,-422,1000,1000,1000,1000,509,-1000,1000,-41,-1000,276,-353,-351,-178,272,344,-561,-1000,930,-634,-665,-455,1000,1000,-158,-1000,-563,784,886,1000,617,-1000,79,-1000,857,-460,494,-1000,-1000,-569,-897,2,-1000,-778,1000,-175,-190,15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{-55,-345,-446,856,1000,337,-1000,-100,-194,74,429,-424,71,-1000,266,264,84,120,-529,77,-49,-384,978,-714,760,400,-255,-386,499,-1000,-699,498,518,1000,-1000,212,607,469,668,158,227,468,-400,-63,269,193,-1000,-400,-866,-510,-473,50,400,-183,1000,400,55,-228,-379,1000,-333,299,-437,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{263,546,353,-597,577,-309,3,1000,-1000,400,1000,-304,814,534,1000,-43,513,-364,905,539,648,21,33,-366,-699,12,-1000,729,696,130,463,821,-123,774,434,-577,-256,-4,-322,1000,24,638,1000,-63,-241,532,-561,-614,434,610,1000,1000,1000,1000,353,-1000,1000,-1000,-154,22,-373,929,-1000,614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{120,-235,77,1000,782,-1000,-638,-264,1000,1000,-421,-1000,-420,-1000,-158,-736,392,-1000,-470,-631,-400,-1000,524,437,-309,762,-1000,-113,-969,-480,-961,-983,518,576,-1000,957,-651,-631,734,-1000,-1000,-325,-1000,-448,829,95,-1000,-1000,-1000,-193,-619,1000,878,-1000,-454,1000,336,528,-1000,1000,1000,299,-1000,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{-1000,-589,-549,70,1000,1000,-1000,-482,386,1000,-524,-30,205,-959,284,152,1000,-854,-1000,-1000,-1000,-835,1000,110,1000,459,-508,25,91,-417,23,-286,490,899,1000,1000,223,207,-266,-539,257,-399,-629,-221,968,600,-513,-413,-1000,-227,-1000,515,754,-174,-1000,1000,1000,-97,1000,1000,-1000,560,-1000,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{1000,-390,-301,-655,-82,-309,-1000,-119,-1000,866,-530,-1000,756,1000,775,-273,490,-363,1000,1000,1000,21,-1000,-628,-538,651,-842,759,1000,1000,-453,428,135,-85,304,-260,953,91,-1000,1000,1000,-1000,-661,-231,635,592,-1000,-363,1000,903,1000,-62,1000,-1000,710,598,827,399,682,279,-1000,-821,-772,979}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{-81,-635,-324,1000,248,16,-1000,-152,134,1000,922,-1000,-82,-354,525,-290,954,-961,417,-319,-498,-977,140,-172,658,1000,-796,-123,483,-138,-57,-80,210,459,-693,373,439,242,-50,-203,-104,-1000,-1000,-519,637,789,-445,-1000,-614,-161,326,565,1000,-690,705,1000,607,328,13,1000,-137,864,-941,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{-290,-1000,704,388,-766,506,228,-787,643,-444,-179,211,62,-1000,581,-598,-641,-1000,-897,416,-1000,-1000,1000,74,874,-50,-297,-1000,-1000,84,1000,-1000,1000,-563,-1000,321,-247,750,178,-60,-798,471,-921,240,1000,-155,-737,-834,-1000,-1000,-1000,451,-349,-91,84,253,-829,-516,-871,1000,1000,-425,381,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{-817,-477,-936,1000,1000,-36,-157,-1000,1000,-1000,-100,994,-310,-1000,-62,29,-632,1000,-1000,-1000,-1000,-299,1000,100,1000,64,-239,-598,-130,-692,-184,-876,1000,1000,-1000,931,-243,1000,1000,-1000,-1000,1000,-733,702,-583,-165,-1000,-445,-1000,-1000,-841,55,429,631,305,-157,-48,-1000,-1000,1000,-314,-91,515,-363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{-290,-754,664,821,-687,506,449,-407,518,-444,193,214,-35,-1000,274,-390,-30,-1000,-897,1000,-1000,-1000,1000,406,1000,-958,-652,-990,-1000,9,960,-683,899,-563,-953,493,-247,324,178,12,-596,471,-904,134,1000,-184,-505,-965,-1000,-1000,-1000,567,-349,-91,84,309,-1000,-139,-1000,170,1000,-21,388,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{608,-635,1000,-1000,-490,-1000,-379,-152,-740,1000,-884,469,753,1000,8,-290,-496,35,-790,1000,1000,1000,-1000,263,-822,-421,921,-123,1000,789,-321,-505,795,13,1000,-559,-101,387,-50,610,55,370,-180,1000,-608,-1000,-1000,-1000,1000,350,1000,-1000,66,-202,675,-1000,392,-773,-286,-653,-448,-1000,1000,784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{327,-945,828,327,710,-383,-1000,-626,-395,1000,428,-520,394,-545,-607,141,1000,-913,50,119,1000,-1000,-190,-386,-309,659,-1000,299,-525,535,-1000,163,447,472,-538,1000,666,-631,435,470,480,-579,358,-448,1000,959,-384,-11,-270,-96,-924,-223,-186,-787,283,80,144,528,-156,736,270,305,-1000,599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{-673,1000,-331,-1000,667,-1000,58,453,307,-330,-278,23,-367,1000,-783,988,-471,541,265,-180,822,119,-809,322,-1000,-662,335,491,819,-1000,74,650,-615,423,984,66,543,-214,1000,-39,-165,-387,125,1000,-367,114,-325,268,428,197,901,-37,732,-396,-122,-7,1000,753,34,-321,-256,-218,-415,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{-1000,-1000,334,1000,577,1000,1000,-990,-1000,-591,-67,-1000,-1000,272,599,374,-1000,1000,-1000,932,353,296,1000,-1000,1000,-612,748,-1000,304,-265,936,642,-670,-228,1000,405,949,1000,694,670,-803,759,449,1000,932,-196,-756,175,391,-1000,-1000,-705,-869,856,-314,-645,-1000,-1000,-254,1000,-722,-1000,236,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{-57,-1000,-317,779,239,83,201,-1000,1000,-1000,-1000,990,329,-1000,603,-871,-750,-525,-1000,-1000,-1000,-634,1000,-1000,1000,219,238,-232,-651,-480,973,-1000,1000,158,-1000,855,-332,1000,424,-1000,-1000,1000,-1000,619,8,-1000,-1000,-597,-1000,-913,-1000,365,-530,695,-119,83,-1000,-1000,-490,1000,-128,-1000,802,-315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureDomainAxes():void",
            new int[]{-1000,-420,183,1000,186,-972,-695,207,-861,1000,929,-879,-688,-747,912,-1000,945,875,1000,1000,815,422,-619,908,629,-190,640,3,1,-692,463,-412,286,-909,300,-1000,-15,414,821,-1000,-574,-1000,-1000,46,1000,1000,192,-1000,-1000,-1000,878,-273,1000,-1000,-275,164,-268,-773,-569,497,1000,597,-500,117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{31,-735,-773,-578,-383,-647,899,363,-254,9,865,-763,338,-1000,498,775,-225,-361,-803,-293,-569,-655,-206,-781,127,183,-774,-1000,-690,-491,-999,1000,-1000,837,966,-364,1000,-224,342,1000,47,144,-695,105,-1000,1000,299,-256,-600,409,289,244,811,-565,487,-512,400,595,-380,579,-859,-996,-263,-242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-456,-366,-141,1000,823,-769,-1000,689,-649,-733,-663,229,1000,-1000,-1000,-400,582,-319,-396,730,537,298,995,528,-453,266,-299,724,-127,-1000,-20,-472,222,948,477,-658,-739,1000,581,452,-921,-128,-954,-334,386,-1000,-1000,430,-719,-1000,-97,963,-283,1000,352,-1000,-1000,-532,545,1000,-9,-755,102,286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-194,-1000,-141,151,1000,-1000,120,325,-957,-1000,1000,1000,959,183,1000,646,582,-319,-1000,58,-132,317,-1000,-1000,-978,266,-1000,-1000,305,70,771,1000,385,-705,790,-1000,848,-815,-163,1000,-67,-776,-954,-995,386,1000,-323,1000,-1000,547,-97,-228,859,285,-1000,-1000,1000,1000,392,-36,509,-1000,182,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-58,847,606,741,-777,1000,-336,-422,249,994,-1000,-986,874,-1000,-1000,570,236,53,1000,185,-12,-87,1000,1000,-512,-48,1000,855,-864,-61,1000,-1000,-1000,1000,-422,-512,-333,1000,661,128,-333,634,733,-474,1000,-598,-1000,321,-49,-1000,-317,512,-798,290,-228,672,-1000,-1000,1000,694,-1000,845,617,-171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{663,-645,272,-162,430,-138,1000,311,-649,-1000,1000,143,248,-1000,1000,775,-429,-198,-1000,1000,-436,81,-1000,-781,127,183,-1000,-1000,-163,1000,-1000,1000,-502,-128,204,-1000,1000,935,159,1000,-370,-400,-1000,-1000,-1000,1000,299,43,-1000,1000,861,-855,1000,-1000,-599,90,650,1000,-1000,1000,-419,-1000,150,-242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-341,-1000,-126,-268,414,-8,1000,818,69,563,-387,1000,258,-1000,-601,870,945,-327,-273,731,-88,-704,367,-708,-1000,995,-1000,138,-1000,-1000,-1000,5,395,-822,1000,-325,810,1000,-212,1000,-822,523,-1000,-759,-280,1000,-994,166,-1000,-848,573,662,821,535,616,-472,-895,975,131,1000,-1000,-1000,959,105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-454,-1000,554,-969,-565,-252,306,369,-1,70,617,886,-1000,928,840,-32,-412,-214,487,-579,-453,-461,-1000,309,-607,508,523,1000,-1000,647,-1000,824,-132,-612,341,-161,800,-841,-672,771,1000,-238,-273,-236,-617,319,817,661,124,934,185,-545,366,-802,-679,1000,1000,386,-1000,-249,-164,-397,-594,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-492,338,-482,-401,207,-587,-1000,-541,-589,817,-597,-310,825,88,-212,64,1000,-1000,444,1000,412,576,-708,1000,-1000,-689,-27,459,473,-341,673,-502,-834,266,-809,-1000,-1000,146,-855,356,970,965,-251,-741,-59,-96,-1000,437,-1000,-1000,1000,-309,-1000,418,-873,30,-660,-574,1000,1000,57,-862,79,-886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-309,-759,501,-1000,-533,-343,-52,-510,528,743,842,567,-1000,774,355,78,920,-857,-232,-809,-284,-660,-513,823,-287,508,30,-696,-966,555,-999,703,-1000,-737,-413,-1000,420,-528,-715,959,443,-228,-373,-499,-1000,-242,151,-342,-548,-99,406,-1000,-1000,-1000,-146,1000,-353,-965,-551,366,-372,374,-336,-717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-411,1000,849,-115,-108,-694,-1000,-502,-1000,-1000,617,1000,212,1000,-254,-828,1000,-942,1000,1000,-453,1000,-1000,1000,-854,-976,877,1000,1000,830,1000,-235,326,-474,-1000,-1000,-675,127,-816,-33,-1000,768,1000,-702,1000,29,94,1000,-35,-306,919,-754,-1000,892,-1000,237,-66,973,-696,786,460,-918,-712,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-52,664,334,-5,188,-210,-899,-554,-802,-132,147,321,815,690,264,64,651,-1000,444,1000,412,1000,-1000,970,-1000,-689,-27,459,1000,129,673,600,-541,178,-722,-1000,-549,-167,-1000,606,1000,954,1000,-1000,-205,435,-671,836,-468,-32,1000,-571,-1000,-58,-1000,283,254,556,131,1000,80,-1000,-288,-971}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-924,-138,-591,-714,-968,1,269,689,-447,-733,-663,-128,1000,-1000,-963,-400,889,-319,712,-215,-1000,-652,995,-698,-1000,-405,400,763,-127,-578,338,-472,308,400,987,248,724,1000,-812,605,471,-96,-496,1000,308,-400,-869,462,-627,-897,129,1000,-13,449,-75,-885,-408,882,576,433,-1000,-1000,102,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-275,-986,-381,-727,1000,-317,1000,677,27,-179,1000,-257,1000,-1000,-166,53,-1000,-585,-1000,-293,-1000,-1000,275,-1000,374,41,-1000,-1000,-1000,379,-1000,1000,-265,540,1000,-364,1000,1000,753,1000,-620,-504,-1000,313,-1000,-955,299,-455,-600,195,-22,1000,413,-775,-125,-668,-131,595,-804,579,-1000,-931,810,275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-578,-1000,669,147,-438,-862,343,757,160,-25,218,910,-682,436,831,708,-424,-227,-935,-606,-115,-444,-595,59,420,1000,-377,-316,-1000,-208,-1000,9,-264,-919,1000,-425,63,-459,-119,1000,63,-958,-1000,-298,-801,684,468,1000,-571,298,311,-773,1000,-381,700,-461,536,476,-1000,-249,-166,-574,-829,-47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{-966,84,-6,1000,438,-764,0,725,-173,341,-1000,305,-225,789,512,-850,-1000,-1000,-2,625,-582,1000,-1000,393,-505,1000,-665,534,0,1000,129,-740,0,966,-816,0,72,104,0,-362,-656,-181,0,0,-268,0,-250,1000,-205,0,528,-1000,88,675,-758,-221,415,0,-90,-933,143,-1000,-193,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "configureRangeAxes():void",
            new int[]{330,-642,538,449,806,312,-1000,-323,-356,-267,-293,-139,1000,40,-465,1000,1000,-1000,-561,1000,1000,397,-411,1000,-394,561,-189,51,-400,-162,-510,-43,-1000,-56,265,-1000,-1000,58,-146,732,-215,151,-159,-1000,-179,1000,-1000,571,-1000,-1000,1000,38,-256,300,197,-777,-1000,691,131,1000,234,-538,-434,-290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{73,609,-1000,1000,-967,-249,1000,226,977,96,1000,379,23,266,503,-1000,712,-93,-795,27,1000,-256,-1000,-5,-263,686,1000,1000,278,727,-1000,-1000,-423,397,366,566,-54,-29,1000,229,289,340,775,851,-7,259,190,-608,123,-286,127,-236,726,-1000,-1000,-47,961,-359,-510,201,-651,288,-80,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{997,914,-626,624,893,5,-210,128,884,22,1000,1000,1000,394,-471,-440,147,-613,994,50,-31,776,790,1000,598,-435,748,1000,-1000,-462,-953,222,-600,-22,-801,-160,307,-680,72,-1000,-1000,-831,-592,400,-1000,-5,1000,-1000,-1000,-23,-514,-664,221,-598,-280,920,-694,-394,-128,1000,-647,1000,834,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{1000,384,-182,362,1000,-146,705,1000,685,-299,-156,344,1000,-103,-1000,755,-900,497,1000,1000,-1000,1000,1000,-494,908,-1000,-18,-520,-1000,-1000,-84,1000,76,-1000,-1000,-131,460,-1000,-1000,-1000,-1000,-1000,-1000,-1000,920,-1000,625,-1000,-1000,1000,-856,-665,-721,713,1000,1000,-1000,501,497,-441,323,-501,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-519,-523,-850,1000,-1000,-884,379,-408,1000,45,1000,39,120,-185,-164,-829,181,-41,-359,974,-191,-1000,393,-1000,-861,903,586,-697,673,930,-1000,-4,1000,-203,-990,1000,104,-717,850,796,1000,671,567,-986,1000,-1000,-156,-516,416,742,765,744,-308,-410,-27,383,1000,395,-270,-7,619,374,-53,-656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{4,437,-1000,1000,-1000,-1000,1000,558,1000,-48,1000,74,-153,78,535,-320,571,-74,-1000,713,1000,-565,-1000,-363,179,1000,1000,1000,326,1000,-1000,920,-423,574,373,391,-30,170,1000,567,877,583,1000,779,170,-887,318,-1000,-286,-466,-37,889,-190,-1000,401,-281,1000,134,-742,789,-917,891,-72,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{317,138,-516,624,1000,712,328,854,404,659,-1000,649,210,1000,-728,869,1000,30,716,-1000,-740,435,162,781,-1000,-1000,345,310,-415,-769,-305,309,-100,1000,64,-1000,694,251,614,-1000,-1000,-504,-1000,1000,-862,602,1000,-134,-622,-1000,350,42,-1000,-248,-532,431,44,364,142,-16,353,1000,527,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-243,-242,-161,962,-1000,129,120,570,228,-37,410,-1000,-1000,465,1000,-1000,-27,247,-1000,301,1000,521,-1000,1000,-1000,-509,18,892,771,1000,-277,298,-756,-403,-993,813,331,-282,-56,609,-23,706,75,214,816,561,1000,147,184,-1000,-1000,192,-572,-201,-703,-1000,415,-337,-151,-266,-1000,-457,-168,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{1000,1000,-766,262,1000,736,1000,704,977,-684,340,782,1000,579,276,-45,696,-872,1000,419,-73,897,595,1000,1000,-775,528,1000,-814,727,-424,-1000,-1000,-812,-1000,-142,-574,-1000,-532,-1000,-1000,-573,-779,241,-976,-577,1000,721,-1000,912,-1000,-236,742,-515,771,967,-534,-162,-150,-295,-112,-71,-258,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{54,1000,109,5,605,1000,34,-8,174,425,162,1000,314,396,904,-213,1000,-169,694,-792,-166,897,274,1000,1000,-775,-18,1000,-98,-674,-491,-111,-1000,457,393,-716,-214,-518,1000,-857,-1000,-388,-779,1000,-1000,587,169,935,-1000,-327,1000,915,206,-1000,59,-425,-534,-405,-205,-854,-305,-1000,-116,464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{78,749,-844,1000,-814,-15,-1000,-1000,668,-379,1000,344,-317,-286,1000,393,141,-667,-786,1000,999,64,-726,778,-3,-121,386,-200,1000,-41,-779,111,-825,-1000,81,184,-484,-486,-532,279,303,98,192,-1000,920,-996,63,856,687,1000,-567,-665,1000,258,308,-528,-694,439,-124,-705,-490,-398,-948,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{1000,350,39,-1000,1000,1000,-566,-15,54,1000,666,1000,65,799,-534,310,1000,1000,19,-1000,-1000,1000,-601,250,592,-850,-158,1000,-900,-518,476,1000,-1000,-1000,993,-1000,141,-28,931,-1000,-899,39,-526,-826,-1000,735,1000,-30,-1000,269,1000,-228,61,-668,-1000,-568,-366,276,394,570,344,832,413,663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{150,-862,804,289,1000,483,-910,-669,-141,331,347,-597,-1000,-618,-195,598,-920,1000,1000,-504,698,1000,-34,304,-116,-1000,-912,1000,1000,-237,539,732,184,-138,287,140,814,189,-1000,1,-716,489,-917,-1000,-1000,105,-433,1000,325,-1000,-586,1000,-805,999,697,-1000,-1000,1000,562,-548,-666,-786,685,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-1000,-1000,-521,-151,18,647,-400,-400,-191,1000,143,813,-237,-465,-1000,-505,407,1000,-455,342,-1000,1000,1000,-1000,-896,-1000,459,-268,-1000,568,-525,-669,-1000,958,740,-1000,1000,-540,1000,-1000,357,-91,81,-888,-1000,252,-305,-567,-1000,382,1000,113,-1000,-803,-842,-519,-541,77,-557,-1000,354,-1000,1000,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{1000,1000,79,-478,1000,1000,-92,853,191,58,-890,-789,1000,585,978,520,-168,-281,-7,731,69,868,560,743,-621,-1000,-912,310,-562,-1000,459,1000,-1000,-1000,-369,-228,106,-1000,-1000,-1000,-1000,-1000,-1000,-896,-1000,-372,1000,-842,-1000,168,-1000,-1000,159,379,1000,316,-1000,19,372,260,-927,-75,-50,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{361,1000,-966,-1000,681,707,937,716,989,234,-377,379,23,1000,-125,-992,712,-313,1000,-189,-1000,-256,585,115,1000,686,1000,674,-877,727,-1000,-55,184,833,-1000,-157,-574,-1000,1000,229,-880,340,74,1000,-13,259,930,-328,-1000,1000,1000,-691,-655,-1000,-1000,1000,893,-449,-38,-621,1000,307,-420,518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{354,787,172,-78,-1000,822,553,307,-632,-930,-736,565,-414,815,1000,-379,843,30,-1000,-62,-740,278,-1000,296,-1000,609,-203,732,-106,347,-517,144,-100,-707,-183,-489,-672,-353,139,102,479,166,199,273,1000,-815,-700,-383,677,485,-150,-379,1000,-546,-56,-215,44,-77,-433,-132,-681,335,-988,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{490,115,1000,278,176,-960,-1000,323,656,-1000,-713,-66,432,-566,1000,689,259,926,236,-1000,15,-268,-120,-414,459,924,-1000,-656,1000,8,38,686,185,247,616,-1000,-753,-1000,-82,986,-1000,301,-1000,-190,-115,-137,549,-715,-1000,-356,-180,204,-283,-326,-1000,-215,1000,1000,915,-231,204,740,522,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-979,82,-686,743,-399,118,-358,1000,-672,-701,-710,913,913,143,29,1000,1000,-602,1000,581,-454,-712,387,-453,-722,-655,-152,-847,124,197,985,-79,1000,1000,1000,-895,898,61,-435,-302,107,-729,-452,1000,478,-1000,918,-543,-511,1000,894,554,959,1000,-586,-1000,295,-1000,-58,-91,954,1000,1000,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{896,-42,934,-533,929,295,-905,-926,-235,-834,-551,-317,-306,-640,404,695,514,-193,559,-717,34,-442,-285,-811,303,-530,-392,331,483,-814,286,523,-274,-554,-417,-664,476,-750,199,875,177,-65,-482,-910,-431,-149,-509,131,439,-30,-342,-800,985,542,-293,391,825,-338,824,-933,42,511,-439,-513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{1000,-755,790,596,604,-424,-1000,1000,193,-1000,-374,-311,-690,-1000,1000,1000,161,749,72,-251,637,-617,729,500,849,75,-1000,-282,1000,59,1000,1000,360,-144,773,-1000,-821,-767,-332,1000,-1000,-168,-1000,-774,553,-678,1000,-1000,-331,-624,263,-581,-222,562,-759,700,1000,184,1000,-140,579,1000,921,179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{68,-12,153,-11,1000,603,266,1000,-672,-1000,-1000,-1000,-1000,-1000,1000,281,1000,-1000,835,-787,1000,965,1000,-508,-185,-454,-1000,-909,-818,-1000,1000,176,-680,-124,57,962,95,-1000,-702,-605,631,1000,277,-7,-379,419,128,409,-522,580,-636,-184,608,1000,1000,121,-189,448,516,1000,-176,813,-475,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-538,-179,612,4,-4,-313,-428,-196,-354,-1000,600,105,431,-789,107,255,361,259,1000,-711,629,15,-473,-544,487,-72,-659,833,657,-24,-381,-580,789,744,84,-1000,399,-450,-206,998,-897,-548,-702,628,4,-173,238,-97,-118,798,533,-77,1000,653,-156,247,98,-21,163,-32,1000,-375,-481,-954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{63,-555,950,-1000,1000,-839,312,-229,-1000,-76,1000,-933,816,-1000,-207,783,-643,1000,-6,-363,309,208,557,-1000,-1000,91,305,-198,-246,-767,553,1000,-1000,-1000,-1000,-262,-222,-410,484,-23,305,-790,527,-653,985,1000,-670,849,-522,-562,-598,84,655,1000,1000,613,-125,578,400,-599,-427,-214,-1000,-355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{695,267,1000,283,-598,-727,551,-1000,571,488,-516,-168,-373,12,869,-844,284,69,-1000,-1000,455,-329,-1000,-292,692,1000,131,-42,-387,-571,-731,-464,430,-602,-519,693,-1000,257,482,863,-161,1000,-1000,-372,-1000,28,-578,157,-1000,-1000,-939,755,-1000,35,464,130,-301,902,-318,-295,-1000,-35,-581,269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{75,115,667,1000,493,-654,400,-18,656,4,622,-470,463,-566,-275,216,-250,331,-1000,-130,-83,223,-1000,-772,72,-236,101,-423,-1000,-329,490,686,-457,-576,-477,-265,-264,-282,312,209,1000,1000,-1000,-321,-48,400,-357,595,-474,-331,-407,204,-283,975,400,374,69,104,119,-497,-133,150,-504,-154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{392,-233,741,-102,611,106,354,802,-466,-618,-1000,-605,-1000,-801,1000,-886,289,-947,-14,-129,1000,81,294,-718,-84,1000,-766,-184,-883,-1000,198,-25,400,-940,-605,849,-449,-900,-132,-706,332,1000,295,-719,-692,894,-385,751,-364,-173,-1000,-278,-91,72,248,546,-206,1000,203,917,-811,-128,-909,-433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{1000,620,-512,-862,-598,-937,-297,-231,-630,-600,1000,-882,-177,-461,-733,-1000,-542,-25,5,735,-594,1000,-254,-930,280,-314,423,340,793,135,-750,895,-5,-1000,504,-404,-233,549,55,-1000,-730,-548,-7,-787,-223,595,-357,1000,1000,-1000,-986,-421,715,154,286,1000,805,97,844,-426,-914,-403,-1000,130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-611,185,674,122,703,-529,601,416,-764,899,-371,-716,-425,-650,823,563,626,-880,422,-273,-800,-921,-27,-260,-348,-816,-719,-591,559,510,-814,-899,-144,-598,675,-579,912,-185,-107,-825,-401,481,-744,-184,583,-856,-947,421,-221,766,-324,250,-807,370,-74,900,222,-47,-262,-800,-95,795,80,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{227,403,-43,-1000,212,244,560,-283,-1000,-335,854,-1000,-780,-380,-717,-871,62,-842,-951,297,-799,1000,633,-1000,-690,-1000,725,-1000,-1000,-572,673,1000,-430,-75,-567,1000,938,-62,322,-413,1000,261,-802,-696,-63,975,-1000,1000,-412,-331,-1000,206,783,1000,-329,1000,-795,-584,64,-108,-1000,-362,-1000,-352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-470,612,9,1000,-868,-813,934,-229,880,-205,-1000,-727,774,513,707,-537,891,-86,408,-759,837,-257,-778,-892,923,1000,-97,-1000,-82,-58,-1000,-714,1000,754,605,-946,-480,-1000,-52,279,18,1000,-945,863,-1000,-275,-268,307,-1000,330,-404,1000,-249,-1000,-428,-1000,-127,740,-143,-11,-303,-179,859,479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-423,-248,-323,596,299,-859,-785,1000,906,-1000,-769,913,913,-961,1000,12,501,683,1000,960,62,108,660,-453,534,387,-816,-458,665,197,449,645,-299,618,616,-895,-39,-1000,-227,-302,-430,-446,-414,884,524,-182,901,-226,-929,725,504,-196,999,521,-804,-731,295,114,-58,-13,1000,1000,584,-439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-369,191,548,-614,205,-1000,-1000,-440,1000,-560,-668,129,-232,-852,398,501,-334,1000,-761,799,-527,-832,318,178,728,1000,-1000,-1000,1000,482,492,1000,659,497,635,-1000,-1000,-1000,-216,214,-403,-314,-1000,-1000,224,-412,1000,-564,-1000,-1000,-535,731,-1000,-1000,-724,-405,1000,38,-143,-159,73,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{1000,459,107,60,-1000,-438,576,-266,589,-52,-952,-602,150,-618,62,605,-35,337,32,599,1000,266,246,-265,-843,-243,-273,679,1000,-149,-561,401,1000,1000,-361,289,72,-185,-1000,-1000,-138,-481,68,-55,-732,533,-801,486,-1000,972,865,-156,1000,-1000,992,1000,642,938,1000,-504,-307,69,338,-50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{130,-1000,859,723,-753,287,-1000,139,-718,188,-150,-545,-637,-770,-101,507,123,-704,-144,1000,712,992,-116,786,112,-877,-998,616,-217,-214,228,-206,-467,-461,1000,-786,-490,1000,26,-1000,1000,1000,617,-46,-837,-1000,-388,-993,-1000,-583,-1000,618,-1000,628,478,-334,-1000,1000,512,-1000,-731,295,-469,134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{-230,371,688,-1000,-363,-1000,-562,-201,1000,-771,400,-471,403,-1000,-138,7,234,249,-788,-335,679,199,-281,-1000,126,-682,-1000,268,747,-1000,-745,499,804,1000,-1000,1000,512,-1000,296,-896,-1000,-1000,-695,-800,-268,230,-163,-219,-1000,1000,-111,-232,755,-1000,295,310,1000,50,1000,-1000,1000,69,-429,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{-166,468,-421,-1000,223,6,-166,-1000,926,-26,362,1000,243,-908,-52,-532,537,599,262,-938,100,890,-33,-418,-271,321,-240,1000,698,-834,-1000,149,1000,-210,-311,119,861,-1000,-88,576,-135,-1000,-435,-830,-77,-79,925,-819,1000,457,-898,-1000,-553,-481,-600,-266,637,-810,1000,-258,505,-244,-203,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{698,377,1000,-1000,-535,-102,321,-1000,275,376,-1000,244,-3,-79,-155,1000,961,1000,-1000,-882,939,1000,-60,-685,-1000,-794,-563,63,1000,210,-277,-15,251,580,-1000,-181,1000,-743,-876,-728,-414,-636,-104,318,686,409,-453,123,-1000,150,-1000,337,1000,-46,371,1000,966,551,213,-578,1000,-723,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{-562,235,-751,1000,868,960,-290,18,-1000,737,271,343,-279,1000,297,-240,-8,-229,-585,408,-692,-35,75,1000,-238,-1000,-626,666,-1000,1000,402,-289,-1000,-1000,65,-1000,1000,357,-736,715,977,8,350,128,1000,-1000,374,299,-456,-995,-372,986,-845,425,-690,-632,651,1000,-217,1000,-1000,-1000,520,750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{104,1000,-21,-920,-390,-153,477,-53,-117,54,-61,412,403,513,1000,-107,830,1000,-291,-1000,-820,-774,-735,-1000,-380,1000,-271,642,828,-689,-939,1000,502,-399,-1000,724,836,-1000,256,-927,-1000,-1000,-1000,223,608,426,300,-416,1000,1000,144,195,379,67,-150,1000,1000,-782,475,-859,503,-956,-1000,569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{1000,-167,-991,-258,-1000,-1000,1000,-486,764,89,-1000,-1000,720,-1000,96,655,951,726,-1000,-617,1000,464,-108,-892,-1000,-1000,-1000,611,-1000,-521,-514,1000,862,1000,-1000,1000,283,-429,354,-1000,-146,-637,-27,765,-1000,1000,-1000,1000,-1000,1000,1000,-157,1000,-1000,1000,1000,1000,1000,1000,-1000,431,306,-621,-647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{-186,277,-158,-1000,223,1000,146,-742,276,-453,1000,-71,-952,-801,-425,316,-23,391,1000,-1000,496,872,1000,-6,-1000,263,108,1000,278,295,-1000,-159,627,91,492,-1000,1000,-623,-723,576,425,-1000,291,-1000,586,-1000,1000,-1000,-63,-342,-484,-965,-553,-200,-1000,445,481,-676,1000,-499,462,-74,739,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{341,459,-855,-727,-631,-269,678,-266,278,68,-870,672,679,83,633,-453,962,912,-979,-549,134,-251,-304,-363,-713,751,-273,764,894,-940,-646,-194,767,-89,-580,689,81,-698,-590,-853,-855,-424,-247,35,754,367,-129,405,916,876,-9,-13,473,-829,239,445,747,330,679,-261,-307,-373,-927,227}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{225,582,-991,-1000,-1000,-923,214,15,-193,302,1000,35,1000,-1000,967,-534,951,1000,-338,-617,1000,-754,-859,-1000,-56,-1000,-666,1000,-1000,-1000,-1000,-1000,1000,1000,-314,1000,-1000,-1000,354,-1000,-1000,-1000,-1000,-677,-183,500,191,71,-888,1000,420,-665,-1000,-1000,845,636,1000,409,1000,-1000,-128,244,-291,-302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{1000,417,-256,-1000,-1000,-1000,565,-336,1000,-61,554,-667,471,-1000,574,237,943,1000,-1000,107,1000,130,-727,-1000,-896,-682,-1000,1000,1000,-1000,-1000,538,1000,1000,-1000,1000,396,-1000,-716,-1000,-1000,-1000,-1000,-228,-717,1000,-329,1000,-1000,1000,690,-943,338,-1000,1000,965,1000,1000,1000,-1000,1000,722,-1000,429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{957,582,-991,-60,-942,-923,860,-1000,-72,791,-1000,151,314,-182,967,497,798,761,-811,349,1000,916,321,136,-1000,-1000,-519,1000,983,149,-143,-1000,459,596,-135,71,512,-1000,354,-571,215,-692,700,-91,385,388,-1000,1000,-526,359,-301,-665,846,-757,1000,46,111,1000,822,-138,-109,-205,-691,-281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{502,-621,529,396,1000,-1000,487,-687,669,788,-12,698,1000,-443,-155,-447,1000,1000,-1000,1000,-92,1000,-1000,23,-314,-1000,-1000,554,-393,-1000,572,593,-467,-540,-477,1000,-754,-1000,15,582,200,-360,-362,-76,-1000,407,-884,732,1000,-1000,-1000,-331,-1000,398,463,432,-693,1000,172,794,299,-579,-1000,-23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{-743,589,287,-74,50,196,-943,1000,-1000,-750,1000,-567,-194,234,-85,-642,-478,-739,471,-220,-197,-846,610,-446,716,466,-631,-142,-3,785,-123,-1000,513,-333,-496,243,-1000,-466,591,-852,-528,-1000,-395,239,829,-401,208,-1000,-1000,805,692,1000,432,3,-527,-96,841,-357,746,-1000,-479,-403,873,419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{520,1000,-641,-1000,-1000,-118,422,824,-718,84,-620,-545,695,-72,1000,-376,822,1000,-330,-834,100,-1000,-576,786,-467,1000,-139,972,1000,-1000,-1000,-1000,-467,65,1000,-786,-709,-1000,394,-1000,-1000,-857,-1000,422,775,103,622,-504,681,1000,578,-13,1000,-1000,153,-322,1000,503,512,-1000,-866,-333,-268,134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{562,-663,-640,151,-242,97,-682,703,-194,919,-961,858,289,-61,274,571,528,838,-912,783,-556,506,-219,271,-805,-579,183,-330,-291,-46,-906,-369,-690,-226,563,-872,915,45,723,458,-529,-786,884,-383,691,858,-233,427,-481,254,-123,34,-812,-856,297,221,295,-514,436,-658,-140,928,681,-360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{-1000,-1000,199,-1000,-948,799,-270,830,47,40,-573,-494,1000,450,394,-1000,1000,-414,507,-920,1000,-71,974,-481,1000,-1000,509,-1000,-1000,-1000,-221,-1000,423,-163,-1000,-68,-1000,394,218,499,417,400,612,-1000,-1000,1000,512,-214,823,1000,-1000,1000,-720,-480,-343,1000,-576,1000,815,-620,-1000,1000,-897,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{728,1000,278,-157,789,304,290,-15,233,-16,1000,988,-301,-766,57,-127,182,-925,-693,164,-583,882,287,-394,-409,790,302,72,-175,54,554,79,786,1000,-143,631,400,-1000,-270,-400,340,-400,-41,-401,997,-396,-468,447,528,-262,-197,427,-72,211,-881,-24,-369,-400,-48,446,-183,-919,1000,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,866,199,-803,444,-672,-33,-1000,1000,391,1000,-226,-46,435,100,-525,29,-297,-374,-1000,732,802,974,812,187,1000,594,-451,-89,-410,1000,-324,466,360,342,-68,-1000,-1,-1000,-1000,-178,-1000,958,-516,-1000,1000,-583,1000,1000,884,678,905,-881,-509,-547,-94,-842,260,659,-63,-470,-837,428,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{-450,98,-891,-929,931,-605,-1000,239,-1000,551,-758,141,-1000,-101,-463,1000,47,1000,-96,-470,-522,-40,-551,-212,-505,-179,251,-318,-751,-431,-904,440,-990,-223,636,-917,406,322,1000,-125,404,172,683,62,584,1000,48,303,388,-306,626,-568,579,-1000,-447,840,83,-813,-722,-712,430,762,172,-82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{-938,-958,-766,-522,998,-717,-47,761,72,467,-1000,647,-1000,72,82,367,-214,295,-1000,1000,-668,1000,55,1000,-1000,-1000,-465,-787,-589,-136,-1000,-1000,-1000,1000,509,-845,346,313,57,-559,361,-255,761,-1000,1000,276,-442,1000,-1000,-1000,177,-1000,-888,-897,-74,85,-603,-1000,414,-385,-1000,1000,1000,-447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{908,1000,954,-977,1000,-1000,295,-486,-290,-452,1000,211,-524,-111,-253,-906,-7,-368,1000,-1000,1000,-335,717,-407,438,845,1000,-161,806,-894,155,20,591,307,92,597,-1000,246,-983,-653,435,273,-564,915,-1000,-1000,-1000,753,787,207,924,594,851,187,-1000,129,-745,500,-738,-424,1000,-1000,-1000,768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,1000,-551,425,1000,-1000,-766,-523,357,91,1000,406,-1000,-559,-190,917,174,370,-541,761,-833,-459,1000,66,-878,1000,770,262,927,425,174,691,100,1000,1000,904,477,-772,-108,-1000,472,-1000,-271,657,1000,-1000,-690,1000,673,-1000,1000,-1000,797,-312,-857,-385,-649,-1000,-1000,172,859,-1000,1000,811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{-389,834,373,-441,433,317,460,-40,588,-913,-712,202,707,142,-88,-451,75,-596,506,104,146,968,819,232,782,897,183,586,533,-242,-456,198,-606,-986,961,92,-287,-40,-39,-560,167,-39,-777,417,-947,-578,151,594,560,133,81,-43,-890,-582,-902,-529,393,867,-258,-548,-706,-668,-754,992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,-1000,-671,634,789,163,-682,-668,-272,1000,-669,1000,856,-434,88,429,936,-300,-857,1000,654,-786,-140,-620,348,-5,755,-187,249,-488,25,-110,-514,-1000,-257,-1000,1000,42,987,-178,-230,-1000,-213,680,691,-449,980,951,325,531,-347,89,-830,-991,-207,671,659,41,92,-1000,-561,1000,152,-198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{562,-663,-168,946,243,97,-621,1000,-610,807,439,714,-902,156,30,956,551,460,-890,623,-778,506,-38,-63,-969,-579,-81,-499,63,-260,-1000,-245,-931,724,491,1000,938,-772,1000,458,-285,-786,904,-251,691,-542,-124,358,-555,-26,-145,-1000,-635,-1000,200,233,272,-514,466,-991,114,928,681,-900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{825,701,159,-1000,812,-822,215,-831,-65,-75,806,-240,-725,-28,-268,-105,194,-300,0,-878,763,225,238,808,-289,668,354,18,61,-202,114,974,211,-354,334,610,-409,486,-707,-531,-224,39,-154,274,63,-367,142,372,477,138,248,-15,67,-352,-736,-116,-393,1000,-172,6,396,-896,-397,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,-243,-555,-803,-272,23,-567,-1000,-223,805,-233,868,899,-432,212,491,577,672,-336,415,238,-136,-458,812,-542,23,292,-451,-184,13,1000,172,466,-986,1000,-377,1000,-445,582,-142,-637,-1000,338,494,-1000,389,474,1000,170,520,-218,216,-881,-595,-547,293,-842,260,-256,-1000,252,12,-299,-605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{199,154,603,-538,-70,1000,163,-609,860,-79,164,-654,-108,436,96,-1000,1000,-613,-730,133,1000,942,-716,808,264,-638,781,-433,839,-507,-327,-477,336,499,-1000,-124,-1000,1000,-1000,-152,-619,-820,-202,-1000,362,-355,-171,-430,-20,100,51,-13,-932,540,-710,-458,-1000,-194,1000,968,-350,326,309,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00334() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,1000,199,-215,604,1000,1000,-292,1000,-390,1000,133,-86,-503,1000,-457,-301,-648,-857,1000,842,1000,1000,-485,0,1000,914,1000,1000,-1000,861,425,1000,1000,-396,1000,-102,-88,-1000,-1000,-610,-1000,-1000,334,1000,-1000,-762,1000,-616,-938,-889,-859,-14,1000,-1000,-1000,-1000,1000,1000,1000,-822,-1000,989,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00335() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "equals(java.lang.Object):boolean",
            new int[]{-1000,-580,-876,652,-1000,-647,-608,-511,-474,-37,-879,875,-892,255,219,-657,-79,243,-412,849,-466,1000,396,1000,-883,-436,-388,-1000,-264,-1000,-1000,-1000,-208,445,-1000,-1000,-575,1000,863,580,878,-120,600,-1000,210,1000,488,-795,-634,116,-179,-471,-769,-446,-628,821,-1000,-241,743,531,-1000,1000,444,600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00336() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{830,-133,-593,1000,-314,-1000,-1000,-261,-101,-654,-743,1000,-577,-1000,-539,795,-213,840,487,-573,-24,415,-175,-715,456,343,400,51,-438,283,-601,-1000,-278,-79,889,-613,-69,-1000,154,1000,-1000,-971,-95,678,-410,682,274,-285,462,-1000,941,1000,-762,608,-570,-510,661,1000,-150,-866,-883,520,772,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00337() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{689,-631,-1000,42,-79,-297,191,1000,684,-121,330,990,519,1000,-330,1000,967,544,489,633,-474,-50,-342,374,526,-797,867,-721,356,-1000,45,-383,-525,-100,-668,-184,494,-1000,157,-687,257,599,-515,169,-207,-463,380,239,212,856,-694,-556,-218,182,-233,-1000,1000,-668,-1000,1000,-348,-644,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00338() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{899,-96,584,133,223,-867,-1000,-520,-510,-605,-1000,877,-1000,-1000,-638,-436,-1000,79,-693,-615,-35,335,313,-1000,-620,420,300,1000,-175,1000,-278,-873,364,228,1000,-30,-267,171,-634,912,-577,-875,-339,69,-462,1000,-179,-532,435,-636,1000,1000,-524,761,-623,-212,312,118,141,-1000,-532,1000,-731,-324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00339() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{-1000,-1000,14,-331,-1000,586,-185,732,559,566,42,348,-196,643,-447,746,-189,364,769,351,-323,1000,1000,-768,-335,-692,-206,-1000,556,-26,-70,-997,-184,657,113,79,924,-1000,-120,-675,-772,-417,-148,803,-366,431,-150,-406,1000,332,-879,-480,652,689,-815,-1000,1000,-1000,-1000,424,-65,-699,128,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00340() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{-164,-803,-982,-290,-811,-919,-372,389,699,741,5,486,-704,889,-496,859,-706,847,-345,177,-630,310,137,-696,-380,-276,-978,-847,592,-525,1,-998,40,975,854,-948,869,-40,-227,-865,-755,-173,-677,915,-856,699,-204,-345,788,5,502,-300,-678,904,-842,-827,886,-430,-61,84,-236,-482,-17,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00341() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{726,-751,-341,-1000,513,-508,3,-1000,324,1000,-40,559,-65,-74,-998,923,377,-555,-912,-325,429,-591,-730,192,-5,-1000,1000,578,637,-1000,-534,380,-528,-110,-814,-583,848,-94,-429,214,728,383,166,418,-934,-707,-867,1000,648,1000,-944,-210,167,427,182,-1000,1000,-1000,32,1000,-427,-129,536,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00342() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{398,-473,-659,336,-149,199,410,53,820,-1000,644,387,329,1000,-522,614,1000,236,12,853,-662,-749,-196,557,92,-778,482,-57,388,-1000,413,-13,-102,655,-590,-447,892,111,-320,-1000,984,1000,-986,-365,-70,367,334,-261,296,-1000,-1000,-1000,-209,381,-464,-1000,766,-1000,-910,1000,-107,-983,-240,-519}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00343() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{775,-569,704,-1000,1000,-448,-655,806,-308,-1000,-591,618,-1000,-1000,509,-999,563,-1000,-1000,-359,254,-312,-677,140,-1000,-1000,1000,552,1000,50,494,996,278,-541,628,276,744,1000,-832,-416,294,-674,-95,-761,-1000,-446,-1000,1000,-503,1000,-442,-493,945,-38,1000,172,537,-1000,343,-19,46,477,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00344() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{-200,43,318,-228,-788,-415,681,-1000,-564,661,69,-489,-377,-400,-935,-5,-992,234,1000,-1000,-291,-134,712,-691,-930,881,-1000,1000,-621,536,-406,-198,135,-284,751,-892,467,-181,-657,119,416,-128,-877,-109,-1000,640,-225,-499,871,-44,1000,-941,-235,321,-773,375,-966,31,1000,-1000,-1000,151,-212,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00345() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{138,623,-63,542,-2,-220,-188,-1000,-556,106,-281,-156,271,-970,-154,778,-1000,-663,909,450,578,270,473,-47,1000,1000,598,91,-667,388,-250,-331,-745,345,841,-759,-930,-881,-229,774,142,-88,439,691,1000,-152,953,-1000,303,-1000,333,669,-1000,601,-1000,623,-172,703,403,95,-974,-313,930,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00346() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{1000,309,2,-127,1000,454,844,-194,-1000,6,631,568,-418,-479,-1000,-410,-699,-127,398,478,272,967,1000,864,181,-702,1000,1000,1000,-395,1000,1000,-914,250,61,-121,203,678,-1000,379,410,-372,-671,-223,1000,-1000,-599,-849,-1000,764,320,-1000,628,853,297,588,-521,-152,657,193,450,-519,-325,-755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00347() {
        org.junit.Assert.assertEquals("java.lang.Double:MTAwMC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{-317,88,-982,-226,-811,-919,-113,584,1000,563,340,139,-578,1000,-301,243,-1000,399,317,516,-422,695,249,876,-79,-505,-1000,-1000,436,-525,20,-998,-82,975,854,-1000,621,-674,-89,-668,-1000,-44,-442,1000,-467,1000,211,-345,589,-292,467,423,-678,904,-900,-99,881,-550,-352,1000,-74,-954,400,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00348() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{1000,-324,-691,151,-414,-291,-687,-83,102,1000,-965,128,-832,-1000,-540,897,531,499,-667,-431,-35,-735,34,-1000,-28,478,1000,1000,-583,1000,-500,-776,515,1000,1000,1000,593,171,-634,-426,-260,784,-821,357,-618,248,-179,-532,792,-636,-1000,-297,-991,1000,-1000,-1000,701,-521,-376,609,-538,391,-1000,-437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00349() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{1000,-648,-776,746,-28,-1000,-1000,-483,-371,-1000,-828,906,-305,-1000,-180,991,-821,461,367,-405,188,719,-59,-1000,544,373,571,-282,1000,498,-713,-1000,-403,-287,922,-1000,-218,-579,223,1000,-1000,-980,446,1000,-1000,1000,-628,-320,462,1000,1000,1000,-966,593,-408,-1000,1000,1000,-115,-906,-895,749,613,-875}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00350() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{-39,693,76,-428,-131,-1000,-964,-1000,-28,-578,-714,1000,-431,-970,209,131,125,1000,349,-1000,890,-1000,-1000,228,-44,-416,-518,85,-615,-988,262,-78,-372,1000,798,-395,-622,1000,-988,318,129,-203,-2,-148,93,-59,-145,213,-62,-25,100,756,-1000,1000,-51,400,-385,-498,1000,644,-1000,552,997,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00351() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnchorValue():double",
            new int[]{-422,147,-1000,-73,-177,-517,324,425,376,263,642,764,244,87,-397,755,-256,969,169,722,-229,502,0,-137,639,-214,-958,-43,247,-748,-506,138,-860,1000,-284,-1000,1000,786,-735,277,-1000,-231,-366,1000,-287,259,-159,185,1000,-412,251,220,-698,796,-369,-224,-172,-373,639,993,-534,-1000,985,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00352() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{-41,-1000,-881,1000,-346,-988,1000,668,-1000,525,-91,917,328,546,960,356,-1000,-1000,-790,-749,606,520,-1000,535,-34,-1000,647,1000,-1000,1000,-87,-61,-1000,-1000,-1000,255,-384,969,1000,-292,434,1000,1000,1000,1000,343,1000,-1000,955,-1000,842,207,-1000,-157,-1000,-501,397,-902,221,-1000,-273,340,-246,-535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00353() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{553,545,1000,-602,1000,1000,-271,40,939,-566,-143,-827,74,-308,-505,552,-356,575,-197,258,56,40,840,-39,-34,847,-1000,-942,1000,-671,-378,329,92,995,1000,271,-52,684,-1000,-1000,-127,547,-124,-580,682,321,-401,188,-375,990,-665,-93,335,840,1000,-421,-381,656,405,879,480,-93,-370,911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00354() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{686,-55,1000,-983,-5,304,167,-89,1000,-801,433,573,-46,-625,451,-681,-174,1000,1000,-753,-537,1000,563,1000,1000,1000,599,-996,1000,799,531,-363,-8,1000,654,-1000,15,168,-197,1000,-250,958,-1000,-158,-694,1000,-1000,1000,243,1000,-1000,-894,533,291,1000,30,1000,-68,528,909,370,-1000,-850,-609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00355() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{-427,186,914,205,-310,1000,84,939,513,-100,-37,-506,-1000,938,739,323,1000,683,1000,-143,-985,254,-147,225,1000,266,811,-1000,498,337,-1000,-377,-362,-178,115,-317,150,464,429,-450,580,-391,-837,-527,629,520,-406,776,652,641,-677,-1000,868,-426,533,-371,705,546,-303,969,555,-20,-1000,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00356() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{-673,-770,834,-990,451,187,-213,-346,123,-40,-162,-649,-665,702,168,347,855,575,-637,836,-934,-166,-543,-39,268,266,-68,-942,151,-200,-898,-118,-309,-296,537,663,988,684,-533,-1000,849,-373,-742,-496,869,100,-509,-92,-794,-101,212,-273,-219,-449,336,-421,-272,940,-935,517,480,626,-449,37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00357() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{1000,-55,1000,-864,-609,-401,438,939,1000,-100,245,1000,-858,-757,53,-1000,-357,1000,1000,-1000,-771,1000,-487,902,1000,266,1000,-877,688,1000,956,-128,-645,1000,-70,-1000,264,168,802,1000,-560,940,133,-34,629,743,-1000,1000,652,932,-1000,-775,533,314,611,232,1000,-148,661,454,-263,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00358() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{-592,1000,-11,-135,1000,569,767,61,-755,-3,-596,-1000,-151,-391,1000,278,616,-1000,-1000,1000,123,-1000,1000,-1000,-1000,-1000,-1000,-574,-1000,-1000,-1000,642,-652,-1000,507,1000,1000,575,-1000,-1000,766,896,1000,644,1000,-918,434,-1000,-1000,-778,730,1000,-1000,-398,-876,66,-1000,1000,334,-125,-424,1000,638,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00359() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{-106,888,-571,226,-854,872,731,835,-296,-58,77,134,292,-285,768,354,-400,558,881,-953,583,845,328,35,479,-274,-890,-153,-358,-601,23,-702,272,635,-13,-1000,608,-571,913,-685,-1000,62,-798,1000,-410,-901,149,764,774,-1000,-44,-832,-90,1000,755,751,1000,-188,271,76,529,-1000,-443,-851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00360() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{390,1000,404,320,963,457,882,949,-762,-730,-265,-128,679,648,-241,226,-581,-1000,-794,997,352,142,856,2,-911,-1000,-910,-347,-705,-97,-1000,300,7,-1000,479,1000,15,703,-1000,-1000,225,958,1000,361,979,-102,1000,-1000,-1000,-620,-489,813,-1000,-78,-705,30,-993,260,71,-1000,-60,760,729,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00361() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{-330,1000,899,-749,-34,302,928,379,1000,498,123,365,-1000,-1000,-179,-112,1000,1000,999,-126,-514,67,1000,-807,-1000,648,229,-1000,-1000,-1000,-429,-608,-252,678,1000,-1000,1000,-1000,-1000,-129,-1000,-892,1000,424,264,-977,-1000,1000,-387,319,502,-251,-1000,-1000,623,-390,286,270,201,963,57,-1000,114,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00362() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{-1000,-1000,353,966,807,288,-81,-1000,-1000,-670,-142,-1000,408,883,808,1000,-460,-951,-1000,620,108,-81,628,7,-129,-614,-879,788,-732,383,-1000,-314,309,-1000,345,1000,205,1000,-382,-1000,1000,993,-638,17,1000,813,1000,-1000,-384,-796,1000,-134,-17,-67,-12,-748,-900,935,-652,328,1000,-625,304,764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00363() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{-39,36,-491,490,1000,871,314,-120,-425,-698,-316,-437,43,223,-306,589,-205,-754,-373,-36,274,-178,510,-55,-92,-149,-1000,-86,-243,430,-705,478,-939,448,-116,97,-213,977,400,-643,949,913,558,42,1000,-78,434,-1000,346,-564,359,-55,-730,380,-88,-335,-855,118,405,101,301,952,204,315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00364() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{464,-1000,269,25,1000,841,1000,-721,-591,-248,-675,-128,-160,485,126,47,345,-1000,-794,1000,243,-414,-667,-973,-1000,-1000,-1000,1000,-1000,-959,-1000,816,-1000,-1000,809,1000,840,1000,-1000,-1000,448,885,1000,425,979,-1000,603,-1000,-1000,-533,-678,1000,-1000,-231,-1000,564,-1000,260,320,-1000,-620,882,1000,88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00365() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{-1000,-1000,258,134,486,-300,327,-1000,-833,-1000,-325,-1000,97,193,325,659,-203,-825,-1000,1000,-774,-1000,1000,-139,-349,-1000,14,345,1000,-525,-898,-56,92,-1000,372,1000,667,1000,-855,-1000,1000,302,-650,74,1000,667,-401,-983,-917,-811,1000,550,-532,840,-333,-472,-1000,1000,-290,253,418,1000,399,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00366() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{-673,-945,804,643,451,386,138,-346,452,-655,-170,-649,-985,660,692,655,992,575,-507,836,-934,-166,692,-39,868,266,-68,-935,122,-200,-898,-303,-357,-178,840,663,352,464,-551,-537,860,-373,-631,-840,629,325,-406,260,239,269,723,-472,868,-449,678,-371,-272,721,-435,969,919,605,-361,188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00367() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAnnotations():java.util.List",
            new int[]{688,586,559,-945,38,2,825,118,466,-686,20,-183,-140,-829,-410,-589,640,505,102,517,-689,771,984,512,-3,-286,599,-876,608,-415,-424,8,630,-127,398,-769,-196,245,-659,305,-623,-362,-954,622,62,195,-788,998,-641,118,-745,376,-536,-609,61,-300,-362,814,465,468,-372,-191,-495,525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00368() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{329,755,310,538,-1000,504,1000,448,-774,579,419,-1000,-1000,-1000,-1000,823,1000,1000,-553,185,-1000,492,-1000,114,-394,-1000,-641,-840,-757,-1000,-1000,810,93,417,-1000,743,1000,-1000,1000,-1000,-144,1000,1000,1000,1000,-1000,-502,-405,1000,1000,-1000,-483,1000,1000,-303,1000,-956,-1000,-61,-1000,1000,-355,-617,-265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00369() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{116,282,129,-525,-1000,691,1000,284,19,162,378,-1000,-993,-1000,-1000,1000,1000,-125,-662,185,-587,-780,-109,-16,883,-1000,-806,1000,-757,-541,-1000,636,249,881,-1000,520,1000,-110,1000,-1000,-372,1000,66,1000,-194,-975,-1000,-257,1000,1000,-347,-137,491,1000,-166,804,-1000,-1000,-42,-1000,1000,888,445,774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00370() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{1000,-325,448,373,-1000,-940,205,1000,1000,-546,-646,-168,234,-235,113,-28,58,886,869,-1000,129,-136,-912,462,-1000,-17,-215,778,-1000,-325,1000,-1000,777,-1000,-725,306,-378,-1000,-783,-1000,265,-636,886,-276,1000,-1000,1000,135,714,409,-16,-1000,897,-1000,477,333,927,402,738,-375,1000,-165,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00371() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{187,-560,259,791,1000,-1000,812,-502,570,751,-391,-1000,463,-736,216,-302,648,62,-103,-261,-513,1000,-219,950,-1000,-288,-959,1000,-417,-1000,846,-529,-396,131,41,410,-449,-989,-1000,-157,1000,-251,-1000,-1000,1000,-110,70,-1000,891,-1000,-676,-963,771,-681,484,695,1000,-590,626,15,908,-567,-1000,-30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00372() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-251,1000,438,427,-888,247,1000,558,1000,-21,-142,-979,-447,-565,-550,413,1000,1000,-1000,-449,-503,-372,451,725,33,-941,-425,1000,-291,178,-727,425,911,-1000,-1000,1000,1000,163,661,1000,1000,1000,-48,-163,243,-1000,-824,-718,617,1000,694,-1000,1000,1000,649,652,-795,-956,-1000,-1000,1000,-116,980,976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00373() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{1000,-331,-530,-673,-1000,730,-441,1000,1000,-1000,397,351,871,313,-372,207,1000,-130,-141,-408,0,648,-34,-191,347,-454,-1000,-288,-939,1000,-385,821,1000,-434,-999,1000,-1000,296,1000,-866,467,-682,584,1000,32,1000,-359,-824,1000,1000,1000,-204,784,254,-401,-66,-810,692,-1000,71,357,189,980,144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00374() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-502,-1000,19,-1000,1000,-1000,-863,-963,-1000,857,1000,-910,1000,83,1000,-450,-1000,-1000,-1000,1000,73,-1000,1000,495,1000,608,-189,-1000,1000,-258,1000,573,-1000,-312,963,-75,-1000,15,-1000,684,866,-129,164,-1000,784,1000,1000,-1000,304,-1000,-638,126,-73,-1000,-983,-199,979,-235,-1000,872,-306,-570,1000,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00375() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-129,1000,-591,-688,-1000,748,1000,462,1000,-146,-173,-983,-329,19,-724,628,1000,932,-266,-1000,-491,-704,516,-152,401,-1000,-850,891,-263,438,-1000,639,1000,911,-1000,1000,1000,-29,1000,-977,1000,1000,364,781,-504,-1000,-1000,-994,626,1000,1000,-634,1000,1000,678,1000,-1000,-981,-1000,-1000,1000,656,1000,827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00376() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{425,465,994,-301,-643,-200,-443,-784,868,-986,-75,-153,562,694,524,-566,-618,322,-442,776,11,-323,424,-371,352,972,352,-667,373,423,-627,695,985,174,871,-988,112,306,251,903,-430,-39,508,30,-980,568,898,705,-153,594,-735,-108,548,291,143,-823,-9,500,-765,896,-306,-252,521,817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00377() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{162,357,706,818,-400,-1000,-168,-502,-152,-204,-391,707,604,338,539,-761,-154,62,96,578,-55,347,939,-772,-1000,356,359,691,723,-319,-412,674,212,261,566,-895,-381,179,-636,373,297,137,37,-1000,585,568,499,-1000,274,471,-658,-323,460,-370,-538,-516,1000,-348,-153,15,-299,-642,-1000,-58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00378() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-396,282,-197,-541,400,929,301,-35,-641,972,728,-910,-1000,-1000,-1000,319,757,-125,-946,754,-743,-1000,14,-372,297,-950,-806,224,102,-966,-1000,1000,-19,1000,176,749,985,-618,1000,-1000,-471,1000,1000,1000,-525,-513,-1000,-139,-200,1000,-1000,87,491,1000,-532,772,-920,-1000,-42,-38,705,-163,360,774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00379() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{1000,338,342,-331,-1000,-227,-474,1000,357,-1000,236,-78,119,99,178,884,242,-608,270,-451,152,-1000,450,-110,-76,548,926,-350,-1000,766,756,-1000,985,674,-1000,-484,-215,-847,-294,-1000,-692,-116,-1000,1000,378,-654,-794,311,-9,1000,119,-349,-282,357,-561,-679,-473,880,-400,-1000,-182,294,1000,-578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00380() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-306,-724,-216,-807,1000,54,-517,-1000,-1000,808,631,-1000,200,0,208,-222,417,-661,-174,633,-45,-672,230,-678,1000,-525,-1000,170,830,-797,-114,1000,-818,1000,851,1000,150,-266,790,400,-657,847,758,-1000,-597,382,-851,-1000,-200,-591,-289,334,487,176,-457,930,-75,-1000,359,355,613,-814,1000,792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00381() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{159,1000,-152,-9,-1000,908,1000,523,282,-186,-79,-933,-1000,-666,-1000,1000,1000,803,-220,-515,-969,-475,711,-921,863,-1000,-638,802,-834,-547,-1000,602,579,401,-894,680,1000,-38,1000,-962,-278,1000,-117,1000,-700,-1000,-1000,-257,878,848,66,-545,1000,1000,-176,804,-1000,-956,-1000,-1000,996,1000,408,524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00382() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{1000,295,-102,447,1000,-585,-795,1000,433,-846,-477,-88,464,-6,4,925,58,376,454,-169,496,160,138,29,-395,1000,-215,-331,-1000,414,1000,-942,617,-1000,-827,-1000,-1000,-875,-1000,-1000,-836,-21,70,-1000,630,241,1000,222,714,177,-343,18,-503,-190,384,-818,1000,1000,-192,1000,-259,188,-990,-823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00383() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{283,-964,-547,-210,442,-701,-615,-364,168,-1000,1000,81,1000,313,1000,-731,-536,-144,-559,-715,1000,-607,210,-490,-106,1000,655,-634,-136,112,17,1000,632,29,661,923,-1000,-661,-1000,-295,-889,-630,-179,-518,1000,-133,-327,1000,422,-446,-266,88,943,-267,1000,-943,928,1000,462,671,312,-703,-603,-406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00384() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{-266,551,-492,-31,1000,224,-812,-3,1000,198,-511,-1000,469,-507,809,-305,186,1000,-80,471,625,-1000,-203,1000,-352,549,850,-284,-1000,210,13,-19,-955,225,1000,220,1000,231,-475,978,515,-192,-433,1000,-1000,-1000,1000,-796,-190,742,-224,812,598,-871,484,18,304,402,-1000,1000,-614,-1000,-735,330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00385() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{310,1000,-301,-700,399,983,-483,1000,1000,-208,-401,-216,1000,-1000,-88,-1000,533,1000,118,188,814,-1000,-481,1000,-446,411,207,5,-752,-214,-1000,993,-384,1000,46,-952,905,-281,-1000,1000,1000,978,-827,-1000,-1000,-1000,-114,-868,-590,1000,-839,565,1000,-1000,1000,-453,156,1000,168,-343,-800,317,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00386() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{315,1000,89,-790,1000,988,-638,1000,320,416,-581,-776,1000,-647,393,-1000,530,486,-100,194,369,-1000,-261,757,-1000,-430,679,-202,-916,-449,-715,995,-666,820,-268,112,-42,-652,-1000,1000,1000,246,-879,-1000,-804,-825,627,1000,-441,1000,-511,696,1000,-1000,1000,-617,419,1000,-195,603,-733,-57,405,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00387() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{277,-226,784,-125,1000,-234,324,-287,-1000,749,-852,-410,-1000,-168,-1000,305,142,-1000,-1000,117,-1000,1000,-1000,-1000,-617,-784,1000,-5,1000,-372,-454,877,-146,222,-189,28,-865,-1000,152,-680,-914,-1000,-1000,561,-460,-423,915,-282,433,-914,-106,245,-45,143,388,684,1000,-85,-1000,1000,1000,-881,952,-71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00388() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{1000,-785,1000,248,290,-1000,-492,-472,-1000,-978,-6,-846,-375,-1000,-750,923,-198,-1000,-441,987,-722,1000,-1000,-1000,-723,764,627,-388,657,-1000,-1000,1000,555,1000,-28,382,-1000,1000,-757,-1000,438,858,-859,-44,220,305,-129,-770,1000,240,-529,1000,1000,573,179,929,777,-722,-1000,997,336,-1000,1000,534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00389() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{-710,547,-1000,917,355,-942,1000,667,644,-1000,1000,524,-630,1000,-567,779,291,498,509,-1000,-371,722,788,1000,-255,-691,790,376,-603,778,500,-737,-1000,1000,-488,308,-1000,214,567,890,-1000,1000,886,773,791,-785,-1000,1000,-1000,-391,304,1000,-638,1000,57,464,499,-297,1000,-1000,1000,1000,1000,-995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00390() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{793,-397,327,-41,-78,-1000,-567,104,-761,418,210,-141,1000,-438,-4,593,320,-1000,310,242,241,480,-1000,-207,-2,-271,124,-885,-326,-406,-1000,1000,1,1000,888,-136,-1000,-1000,-733,-852,931,399,249,-448,-574,40,-968,-984,259,-36,-1000,1000,908,-345,520,-498,517,-112,411,-296,-68,-374,-125,812}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00391() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{-701,589,-301,301,1000,224,-1000,-509,1000,475,-776,-251,-318,-663,137,-407,606,1000,-264,471,814,-651,217,1000,-446,411,366,-260,-1000,303,-413,481,-1000,225,995,760,-400,-281,-652,601,-281,315,-954,1000,-1000,-1000,935,-818,-361,541,-839,1000,-576,-1000,-161,-148,747,87,-1000,580,-800,-1000,-735,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00392() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{-858,-751,309,220,399,-69,9,211,-1000,-738,87,638,-1000,1000,780,133,1000,-1000,550,-1000,-1000,836,471,-1000,-446,443,207,872,-752,-148,937,-287,715,-1000,-870,-952,-769,178,1000,-485,-330,500,-847,56,292,369,-313,1000,41,-638,78,-12,-499,238,-204,-116,670,-785,557,-406,1000,891,-1000,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00393() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{529,629,1000,-1000,-758,1000,-1000,1000,102,-706,-1000,-311,-613,-1000,-772,191,291,659,1000,663,185,722,-712,1000,851,1000,-356,795,-34,-1000,-1000,1000,1000,1000,-1000,-414,1000,1000,-1000,883,1000,1000,-1000,-1000,-83,1000,-1000,-631,-21,1000,-222,1000,1000,84,-234,-1000,-581,-307,-785,-473,-307,1000,1000,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00394() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{717,408,-186,-329,733,823,-1000,-256,557,571,-511,-1000,1000,-647,1000,38,583,881,368,1000,1000,-1000,-836,1000,385,793,1000,-649,-1000,-94,13,995,-802,1000,359,207,1000,69,-1000,1000,1000,264,-1000,520,-1000,-1000,406,-730,332,1000,-1000,1000,1000,-664,484,-835,541,359,-983,1000,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00395() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{-341,-226,-721,718,233,-234,611,-84,-262,14,602,100,-1000,184,-369,1000,859,-367,-1000,-462,-1000,-864,359,309,-888,-750,1000,-5,-350,458,756,-211,-146,-43,-304,-220,-865,-1000,164,124,-572,326,48,1000,-460,-477,239,505,-651,-927,-230,245,-186,1000,237,808,1000,-85,580,-368,848,88,952,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00396() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{1000,369,1000,-1000,1000,1000,-63,-202,-598,1000,-1000,-311,-648,-1000,-850,202,-605,-487,-1000,1000,-58,651,-1000,-1000,-729,-889,-356,-871,1000,-676,-1000,1000,-760,1000,447,-156,535,-1000,-710,883,-133,-1000,-1000,-669,-1000,-820,1000,-1000,1000,-243,-310,1000,1000,-67,1000,-6,-581,965,-1000,1000,-307,-1000,-448,797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00397() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{-144,128,-341,36,1000,-392,243,-254,-1000,514,-218,-483,-629,905,1000,-769,1000,-1000,-1000,-573,-1000,-1000,109,-1000,-1000,-727,243,-106,576,1000,551,-311,-1000,-1000,-64,-402,1000,-1000,969,84,-1000,-431,-902,704,-306,-1000,1000,623,176,-1000,-605,-239,-1000,-1000,260,500,1000,719,-122,1000,623,-1000,-607,597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00398() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{247,-1000,-586,130,1000,935,-1000,-866,237,1000,374,-267,-243,1000,288,-445,-927,-160,-908,510,285,402,-123,-401,-1000,208,752,616,582,564,1000,620,-303,-1000,1000,916,1000,-31,21,-1000,-532,-593,71,513,-1000,-846,952,-94,1000,-268,1000,1000,-754,932,182,-43,969,-1000,-793,-537,9,-4,-1000,450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00399() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategories():java.util.List",
            new int[]{-100,1000,999,-672,214,905,-935,616,-203,764,-1000,-96,471,-753,321,-1000,461,411,-697,724,-529,-63,41,-121,-1000,799,978,689,-220,-133,-1,619,434,874,-1000,-1000,144,471,-381,1000,1000,-565,-717,-676,-505,454,-90,519,-135,514,483,308,696,-1000,1000,148,807,1000,-703,1000,-847,-573,-16,994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00400() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{-1000,235,-1000,-213,-1000,253,1000,472,-671,-514,-303,-159,7,-7,663,-41,52,-664,-725,-1000,-697,474,-781,-803,934,-1000,-733,-155,-577,1000,-1000,124,938,-943,953,-98,-1000,149,-84,-603,343,-966,1000,664,-467,1000,-1000,-550,539,1000,-541,-633,-1000,-655,920,526,-1000,-522,-87,512,1000,-184,18,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00401() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{480,-640,-371,-1000,1000,37,-219,-111,1000,-619,-343,142,306,1000,281,-538,537,15,473,-1000,-188,-1000,-368,-918,457,-875,-211,-364,-97,168,1000,31,311,-970,-1000,-1000,507,-376,-68,1000,147,-1000,6,-110,846,1000,476,-258,-247,143,-197,-426,1000,-1000,372,-874,-713,-785,-655,-1000,1000,91,279,-783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00402() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{-292,396,538,472,1000,400,-380,-220,-85,307,-852,722,-480,1000,782,795,-717,-606,1000,266,612,-1000,-563,-152,-301,364,-324,1000,-261,120,463,-123,502,53,-1000,-959,622,6,-871,1000,-245,-242,-773,755,542,358,615,-241,-443,498,23,-648,-20,-1000,170,-790,199,-1000,-276,-524,-173,-110,510,66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00403() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{688,168,-642,-350,-318,-238,382,-613,-1000,-716,-124,-254,807,407,867,1000,-526,180,208,932,-1000,1000,-377,176,-491,130,-236,-53,-220,290,-810,16,920,-450,434,1000,-800,-483,114,-130,-591,-1000,1000,842,-1000,-365,-956,-24,725,856,-485,-687,-868,-34,266,-402,600,-1000,814,1000,373,-669,-9,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00404() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{225,-217,-855,-827,-1000,693,823,677,-15,-244,884,-936,124,-320,-529,187,-76,-698,-222,-500,-632,1000,-141,-1000,92,671,66,1000,-107,681,447,-79,323,-20,-203,278,-587,-62,-501,-23,-196,-381,644,-336,313,1000,292,-943,293,-965,-1000,-821,84,-517,979,-386,14,1000,50,-1000,1000,1000,-407,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00405() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{1000,10,874,-409,-1000,-908,418,-1000,-1000,277,-467,-506,-670,-1000,1000,-76,769,953,-962,1000,-1000,1000,1000,-923,-1000,291,1000,-375,965,-635,-1000,-532,897,-14,375,1000,-1000,79,250,-584,-575,-709,302,765,-288,-1000,-1000,-1000,948,1000,403,838,716,129,426,-307,1000,-460,1000,1000,-154,-665,-207,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00406() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{1000,683,1000,-461,1000,232,-1000,-1000,-986,804,-905,1000,-657,-869,-335,-339,1000,1000,114,1000,-48,859,-1000,1000,-1000,-1000,-1000,-396,1000,-1000,-351,-1000,586,66,-233,902,584,-280,798,1000,-1000,-558,-793,448,781,-1000,-364,1000,747,633,1000,503,-138,-394,-182,-896,1000,-1000,-546,851,-1000,456,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00407() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{807,922,433,-832,994,-920,-868,-835,841,665,-523,-158,175,65,-826,-290,1000,1000,1000,-265,261,-1000,252,727,-201,-1000,-190,-809,411,-591,936,-553,-218,-739,-663,-456,1000,692,33,878,-461,-1000,-82,178,-814,-374,88,102,547,184,-87,-1000,1000,-639,424,-221,-476,-1000,-1000,-1000,1000,1000,1000,-128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00408() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{1000,-305,-1000,-494,-1000,803,1000,99,1000,-707,15,-549,853,-155,-778,-939,-772,-1000,214,-1000,-995,1000,201,-1000,1000,1000,-836,1000,-109,1000,530,-282,356,-446,1000,584,-364,818,-319,369,264,131,326,702,595,1000,472,-1000,-710,475,-1000,-1000,84,400,-735,-846,-976,1000,-1000,-72,870,1000,-383,-139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00409() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{-515,501,-209,-358,-580,-399,-171,160,-1000,-401,-1000,552,-1000,-1000,186,120,616,139,-1000,-95,-880,-988,-1000,286,267,-1000,-245,-937,486,-306,-1000,-937,959,-1000,1000,294,-708,597,251,-177,967,-739,455,376,302,341,-1000,699,826,1000,859,-320,-1000,-1000,1000,1000,-196,-1000,-586,672,207,260,1000,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00410() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{-1000,-387,-584,-373,-671,-157,-313,-372,717,-183,-1000,69,-498,-1000,318,-1000,-11,-500,-932,-1000,-824,273,1000,-194,-1000,-631,-1000,-554,625,34,-1000,-616,808,-222,1000,881,-557,1000,-139,815,1000,106,157,613,310,-1000,-824,-370,-293,1000,594,-743,-531,-95,1000,1000,-1000,497,-1000,1000,77,-93,898,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00411() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{-986,126,289,-1000,784,-1000,-720,-275,705,118,-696,-46,702,651,86,670,469,959,-765,-1000,521,-1000,167,484,-390,-1000,-1000,-1000,416,-902,392,-1000,531,-800,823,365,-446,-291,998,1000,215,-720,300,191,819,757,795,555,626,428,1000,-307,183,-297,506,-584,-1000,-1000,-834,0,-64,374,1000,-943}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00412() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{1000,-312,-472,-15,-1000,-353,1000,-723,-1000,-736,1000,-1000,296,-893,488,-1000,-58,-117,1000,1000,-601,1000,580,-1000,1000,146,600,219,557,473,260,1000,-1000,580,-153,644,-478,-202,-1000,-1000,-731,-1000,465,-241,-1000,273,812,-1000,-311,-1000,-998,480,-259,-311,-13,90,648,-1000,-684,510,107,-499,-413,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00413() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{1000,1000,499,-1000,381,-1000,-502,-1000,-1000,-127,-418,1000,-733,-707,-18,855,149,1000,39,1000,-295,691,879,1000,-1000,-1000,600,-541,966,-234,269,-1000,1000,-377,964,566,14,96,104,343,-404,-946,995,358,1000,-325,-1000,1000,1000,953,572,-1000,-1000,-164,982,148,-38,-1000,-380,1000,-260,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00414() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{895,-286,138,-973,-1000,-425,1000,-876,-1000,236,-138,-1000,-977,-398,1000,226,-92,477,-962,1000,-1000,1000,307,-1000,-804,-701,973,237,1000,95,-423,29,1000,-127,659,1000,-1000,-422,-533,-1000,-790,-791,600,1000,-915,-374,-1000,469,1000,1000,-482,838,-34,691,-6,-454,1000,-675,1000,1000,12,-1000,-794,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00415() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getCategoriesForAxis(org.jfree.chart.axis.CategoryAxis):java.util.List",
            new int[]{-14,413,190,432,997,55,-1000,-431,-1000,256,-481,1000,-179,234,-251,799,-405,-397,-486,-54,1000,-531,400,664,-930,-269,-709,869,133,303,123,-254,1000,379,-663,168,69,-232,16,1000,-1000,-179,-317,258,499,-123,-440,788,1000,195,-781,-566,278,-949,-62,-47,370,-1000,343,-919,-36,1000,1000,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00416() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{997,727,156,1000,176,331,1000,-991,-1000,728,-276,840,-754,1000,-454,-795,-613,1000,906,1000,358,-358,-345,-372,86,-506,-1000,380,1000,-960,543,1000,-1000,1000,639,62,188,-294,235,215,621,-186,1000,681,-512,636,250,-623,1000,-1000,-691,1000,-993,565,726,-369,-754,-933,-24,392,-647,255,-1000,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00417() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{-613,302,-101,-429,-66,610,-565,-919,967,-524,740,-276,210,296,-787,549,946,164,-1000,-174,-459,84,-810,-147,585,576,1000,274,521,981,-978,-451,4,-262,-744,438,546,-301,-801,160,1000,-770,-1000,-391,-660,691,605,1000,538,-284,614,-1000,165,-410,148,-1000,-969,1000,1000,-636,1000,-1000,263,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00418() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{198,469,334,-400,47,-953,549,-860,168,-480,206,84,72,1000,-790,289,1000,68,-717,1000,-76,378,-42,-785,127,-153,555,888,337,-39,-585,656,-109,-504,251,-281,939,-251,1000,42,130,-1000,-33,-110,178,1000,-770,72,232,133,-8,-516,-273,-180,244,230,-849,155,-85,-1000,-671,-136,-953,-238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00419() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{469,1000,34,-265,217,658,-408,162,51,1000,-968,61,1000,-1000,-239,-904,620,1000,1000,-298,1000,1000,418,-911,582,1000,1000,-759,654,-1000,1000,531,1000,1000,-529,-918,-588,-713,-44,401,711,585,703,1000,-793,-221,-111,1000,1000,-1000,-84,627,260,1000,764,-1000,1000,-1000,-1000,343,334,865,908,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00420() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{-320,1000,669,-400,-421,893,1000,-1000,-1000,537,-787,1000,542,939,-904,-652,-163,1000,840,1000,489,-644,-1000,-936,741,-160,-1000,759,1000,-972,223,33,-1000,-191,387,-257,889,546,-1000,517,357,-836,1000,48,-535,481,1000,-359,642,-1000,-475,1000,-988,-47,362,-1000,623,-598,836,-1000,-51,-77,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00421() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{-34,930,-96,866,368,594,754,-208,886,1000,-899,655,-134,356,-466,-778,-258,1000,1000,-267,748,286,-259,-869,783,621,-1000,-475,802,-1000,491,532,-981,419,631,291,76,-447,165,533,1000,315,-988,584,-1000,-569,434,-1000,120,-1000,-1000,-119,-414,247,862,-664,1000,-401,1000,86,-160,-548,-46,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00422() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{-958,681,352,348,-6,901,869,469,-497,997,-287,793,-479,925,59,-437,534,340,475,-65,-149,632,712,964,-56,-779,871,415,-261,-18,752,-790,-966,175,-335,335,362,175,210,269,-522,-549,645,598,-570,741,-704,-608,-315,181,-790,102,-861,269,-266,291,6,954,17,-661,582,-72,-401,-594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00423() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{23,817,-562,400,489,612,-95,-686,-183,1000,-1000,-471,-1000,-618,-357,-558,-129,926,940,-192,851,-517,-937,-326,658,290,-532,172,194,-261,-186,-1,-196,197,266,-277,-49,-1000,-1000,282,433,-206,255,-316,-954,-476,-379,-164,-462,-1000,-53,565,13,801,1000,-748,1000,-663,1000,286,801,1000,500,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00424() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{633,572,-252,163,-109,-829,880,-632,393,9,-140,-959,1000,1000,-840,-593,146,674,-32,-521,96,661,-529,-533,-315,-162,238,657,822,-330,-844,-178,-202,-775,1000,31,472,-838,1000,347,237,-909,-208,219,220,860,-728,-655,-1000,-914,-518,-1000,-883,999,822,-454,-1000,-869,573,-1000,-400,330,-1000,-82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00425() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{-835,614,-77,538,461,641,292,-527,888,729,403,155,564,110,-291,235,1000,864,-71,42,260,49,-463,-417,1000,-1000,-400,-48,748,-373,-937,785,-269,969,-479,277,639,-22,-100,641,975,-329,-660,108,-1000,1000,-130,-589,-894,-1000,-6,-860,407,-429,-173,-337,-505,1000,887,-266,832,1000,-252,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00426() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{43,8,348,-1000,853,-1000,-663,-1000,1000,-1000,967,-954,832,251,-1000,1000,1000,-666,-1000,-592,681,-871,-538,-355,763,366,686,1000,-611,1000,-1000,-1000,1000,-825,480,-1000,1000,-1000,480,-325,1000,-1000,-1000,-611,604,995,-164,974,-1000,740,797,-1000,1000,-199,104,522,-116,689,764,-1000,250,58,-266,66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00427() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{-375,318,383,-298,679,99,533,-425,543,56,-5,426,-275,657,8,358,946,523,-11,1000,195,-386,70,-899,1000,180,547,296,152,-595,-694,601,-257,444,-49,291,827,737,740,102,532,-357,1000,305,-656,1000,-471,8,-429,593,-714,1000,285,-1000,-327,501,-1000,1000,352,-580,-641,-321,-248,-589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00428() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{74,536,-192,20,343,865,1000,-985,168,537,-697,-532,205,957,-72,289,-217,1000,840,1000,391,378,-42,-909,772,-160,-20,642,337,-960,223,323,-1000,166,387,-115,806,-251,-730,466,357,-728,1000,-110,-784,481,935,72,749,-1000,-475,1000,-988,45,414,-845,261,-684,783,-1000,-156,-77,-1000,-238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00429() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{-738,956,-17,-9,-242,241,-158,-735,-877,-216,-148,-555,1000,-781,-1000,767,1000,430,22,850,285,1000,-348,-747,344,573,-635,168,108,-468,-1000,939,-509,-956,-705,-385,878,-1000,-1000,150,1000,-165,954,-1000,-990,-20,1000,-345,-1000,-1000,926,-335,440,-282,952,-1000,1000,201,1000,-1000,1000,1000,1000,-973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00430() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{-602,381,-51,-613,263,-275,-967,-763,389,-909,792,-959,599,-1000,264,985,942,292,328,-19,340,514,-180,-311,-675,192,221,670,-411,180,-531,-570,652,140,-71,-332,1000,80,800,-217,-270,-758,628,-448,-503,-1000,-27,1000,1000,-211,839,-1000,1000,23,685,-39,-796,-1000,-558,-782,481,-116,803,194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00431() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.SortOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getColumnRenderingOrder():org.jfree.chart.util.SortOrder",
            new int[]{-1000,311,315,373,101,1000,220,-517,115,-41,667,-129,268,-326,-904,-503,-91,1000,136,-446,454,-310,-38,367,191,155,-1000,125,1000,239,-55,437,-224,400,-268,-283,-74,-503,-1000,-76,783,-652,-277,-160,-724,96,644,-350,200,-393,252,61,-109,640,-159,-705,241,-71,-1000,-26,773,207,249,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00432() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{390,651,1000,-1000,629,587,-196,1000,-95,-706,-185,-739,-883,-242,30,796,-830,257,-361,-1000,489,177,-213,-316,-901,-908,-133,-1000,-1000,-269,1000,-341,159,265,-257,-259,-52,938,164,-402,-595,-433,-387,0,-374,127,400,-529,321,-466,-126,13,-688,-45,701,1000,-644,-413,702,-1000,-340,-367,-339,-147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00433() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{450,938,-746,-580,-932,59,-966,977,224,-186,-661,-688,-593,149,147,-466,686,-743,-495,-45,-136,-391,-26,653,507,-187,679,23,299,738,419,802,-634,-778,52,-261,-52,-368,-388,811,134,574,-501,675,-769,-873,-553,931,796,104,-2,663,-660,-926,699,746,308,-872,-648,425,33,698,-689,809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00434() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-233,821,-1000,179,-721,823,-1000,-1000,-1000,-1000,-762,317,-380,-295,775,421,462,-447,147,559,-1000,-1000,-975,-227,-135,1000,-168,-1000,-632,-219,-1000,1000,-1000,321,-546,1000,1000,-1000,-702,-7,380,-27,-908,235,-1000,1000,-1000,-1000,1000,-281,84,-199,792,-1000,-1000,-706,1000,272,625,-405,998,-948,1000,-212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00435() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{285,969,-1000,-312,-1000,229,-1000,-769,-546,-1000,-14,-539,472,-282,-191,-80,1000,-956,-659,583,-1000,-337,-299,228,809,-933,36,-666,34,545,-962,1000,-653,-651,280,639,-697,1000,-96,944,948,155,-191,192,-1000,62,-1000,345,882,775,805,898,354,-793,-354,-777,-483,607,-193,195,1000,682,278,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00436() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-230,190,-496,286,-1000,530,-162,-886,-992,194,-57,-212,-765,-254,353,640,-145,-684,1000,49,-570,-692,-351,-373,612,310,-563,-297,-1000,-478,-1000,-332,-1000,-1000,-38,-279,-1000,1000,371,-873,144,-356,-265,-208,50,354,-545,-993,713,734,324,51,377,554,192,-1000,-715,745,653,-371,907,-470,1000,-880}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00437() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{306,33,553,-89,871,889,-372,609,-839,601,400,-768,-112,-331,-11,916,-267,200,-552,-1000,-44,337,-88,-386,-972,-326,-665,-1000,-1000,-738,951,-499,358,1000,45,-110,-1000,659,531,-1000,133,-1000,-1000,-860,268,810,-1000,-535,321,-991,-27,-485,-629,37,-152,-69,-534,142,1000,-1000,-396,-33,-135,-270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00438() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{154,33,834,-286,-493,643,-372,886,-221,-1000,-1000,-64,72,-232,855,916,-755,-48,-1000,-298,57,-598,-726,-748,-1000,198,-816,-1000,-437,212,79,1000,571,1000,-1000,-272,-605,1000,-1000,685,133,-137,-1000,-860,-1000,610,-1000,-1000,967,-991,260,-252,-125,-1000,-152,1000,-1000,-399,210,-1000,-396,-33,-459,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00439() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-311,148,619,19,235,616,-1000,977,-993,-386,-591,-237,-261,-295,699,846,-415,-80,-1000,-563,-217,-474,-440,-750,-1000,-197,147,-932,-1000,-404,1000,459,41,667,-589,1000,-472,1000,-323,1000,-498,-907,-793,-183,-304,923,341,-1000,813,-722,138,-394,-183,-875,-133,20,-453,-412,824,-968,112,-525,230,-207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00440() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-943,311,-481,215,1000,1000,-280,-491,-1000,731,1000,-180,644,-1000,-7,179,-921,62,-230,-1000,-923,173,126,502,-1000,-730,-1000,1000,-875,-979,528,487,223,913,508,156,352,390,1000,-1000,961,-1000,183,-1000,-140,1000,-924,-1000,723,-1000,935,-178,-459,-743,-79,-836,-219,710,-1000,-1000,-305,603,-129,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00441() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{396,458,-106,535,-334,502,149,272,92,787,415,-626,-883,-251,-570,796,-86,-308,-361,-742,-696,292,-479,130,-218,-1000,304,734,-1000,-737,-560,-341,-552,-254,280,354,-722,-334,146,-847,330,-61,-511,-132,-271,-107,183,-1000,667,391,444,383,-514,555,-309,-20,-1000,366,441,-914,403,-245,571,-574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00442() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{209,627,686,-186,1000,921,425,88,919,-386,614,-734,337,-186,-689,144,68,-584,-176,-730,102,-626,-318,135,-524,-569,417,792,149,-342,1000,170,848,-133,56,851,1000,980,1000,444,367,47,315,-467,-276,-229,516,333,1000,-682,783,26,-426,370,-314,-1000,1000,-415,-775,-946,207,689,-323,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00443() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{355,1000,259,121,-1000,-65,-749,814,63,-615,-1000,-54,-802,-153,444,247,-631,-910,757,377,-239,-1000,-732,-390,-54,-411,350,-950,-487,221,-673,1000,-1000,-779,-626,786,-486,-602,-416,1000,-619,874,-480,1000,-1000,-232,-1000,-991,1000,926,537,513,332,-481,219,527,-554,-32,-448,-372,1000,-948,709,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00444() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-329,856,239,306,-1000,15,-749,963,-648,-182,-1000,100,-1000,-368,883,-39,-1000,-642,951,528,-141,-940,-326,-640,-54,-124,167,-1000,-696,591,-509,590,-1000,-735,-615,46,-385,-645,-620,388,-1000,813,-778,1000,-930,-68,-1000,-661,849,926,129,249,155,-606,-274,527,-1000,-387,-448,-372,620,-956,946,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00445() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{68,57,-230,-175,710,756,-618,-257,-294,-610,400,-727,27,-284,-400,871,-8,-302,-120,-1000,-911,-380,-452,41,-310,-846,279,-938,-61,-1000,-295,200,358,1000,307,659,-417,88,-519,-840,400,-838,75,-860,-118,684,-45,-1000,1000,-202,904,19,-652,277,-317,-400,-222,819,642,-984,921,4,417,-804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00446() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-331,-308,989,-953,710,1000,-1000,400,-162,-566,137,-1000,807,-103,736,262,-1000,544,-580,-261,198,649,366,-662,-478,-1000,-1000,-279,-467,-5,551,-363,788,-791,-39,-429,-1000,1000,630,278,-445,-435,174,328,179,85,-143,1000,-234,-96,-77,-310,-41,-855,1000,268,-1000,379,354,-745,-527,258,-306,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00447() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{981,1000,-527,-318,-1000,502,-1000,-503,579,-681,-277,-1000,885,96,-1000,100,-418,-1000,-1000,346,-1000,-615,-230,25,-584,-1000,630,-407,479,379,-990,1000,-547,-1000,239,1000,-866,1000,4,-214,1000,502,-11,267,-1000,-545,-1000,242,1000,543,783,1000,284,-484,1000,-652,497,687,298,-922,1000,1000,537,-493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00448() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultCategoryDataset|getRowCount=22:java.lang.Integer:MQ==|getColumnCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{1000,-387,114,-381,182,1000,559,1000,-1000,383,-109,-508,208,1000,-70,366,436,-4,-492,-596,573,15,-14,-1000,355,-1000,-170,87,967,-133,1000,434,1000,1000,-77,1000,-1000,-256,1000,350,-945,-440,54,429,1000,-113,-528,580,1000,-1000,693,1000,-1000,648,-1000,-1000,-1000,49,606,-1000,1000,287,-937,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00449() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{-275,648,-341,651,-348,16,830,552,-688,398,851,-698,-425,-1000,-406,514,498,565,-1000,261,488,516,10,-506,150,-399,-203,-412,-193,-167,587,-1000,158,239,-785,339,663,589,-215,624,629,-177,133,454,160,138,556,-16,-1000,1000,235,61,653,-337,-391,282,204,-398,350,1000,-107,-1000,1000,198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00450() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{-568,80,749,-86,-957,962,-671,-700,-1000,793,1000,-251,-880,428,-1000,-1000,-1000,634,-258,89,962,1000,-115,-977,1000,-1000,-1000,1000,29,-205,1000,-1000,1000,1000,-667,1000,498,-390,616,1000,-1000,-868,1000,958,1000,-1000,7,621,-686,-763,-47,-611,-242,305,-695,-853,-775,1000,1000,200,1000,-357,350,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00451() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultCategoryDataset|getRowCount=22:java.lang.Integer:MQ==|getColumnCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{18,33,357,-1000,843,-202,222,-290,-36,-572,98,254,-277,738,-672,1000,957,576,1000,-361,227,-603,-106,626,60,1000,513,220,960,-1000,-1000,-63,883,286,230,921,84,1000,227,-1000,-1000,-611,-1000,811,733,-62,995,811,758,-804,-524,1000,-37,-292,51,-512,509,-1000,359,-626,703,-835,640,108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00452() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{-1000,874,-927,678,-1000,995,-1000,-1000,245,-643,981,-929,-1000,-517,-961,-748,-457,1000,1000,1000,-551,1000,-1000,-919,353,-228,367,233,-566,-810,-779,-1000,1000,1000,-551,866,1000,1000,-721,-289,-1000,-1000,-918,1000,41,-1000,-1000,905,-213,1000,1000,-26,-78,-1000,-185,-1000,-541,-13,1000,1000,91,-1000,58,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00453() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{122,572,604,-613,1000,660,-957,586,-1000,27,15,-522,-699,194,-1000,891,593,477,378,-341,-351,-110,-175,305,172,-155,-633,429,1000,-709,273,-457,55,638,-947,472,264,1000,411,95,629,-136,-938,880,1000,506,87,321,528,-890,629,958,-40,-272,-310,-587,-55,-513,767,-337,1000,-976,205,-82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00454() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultCategoryDataset|getRowCount=22:java.lang.Integer:MQ==|getColumnCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{741,293,-195,115,392,533,1000,929,-576,270,99,489,268,-968,-868,435,910,565,-151,-804,-54,283,-782,221,-172,-329,532,202,1000,-1000,70,604,-471,657,-970,-233,51,382,390,298,-147,233,-1000,1000,-96,-697,-901,-259,-326,-454,-523,1000,-370,162,790,-379,-144,-963,654,-708,637,-293,791,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00455() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{-163,44,444,-764,1000,-14,-820,-283,213,-844,688,-520,1000,-573,-574,606,501,152,-155,-878,244,-1000,-14,698,1000,896,-154,802,246,144,-668,705,-1000,-58,-208,172,168,460,124,-593,-743,463,-1000,328,-306,1000,775,472,366,-1000,-508,847,-464,-109,-690,288,1000,-1000,-132,-139,-667,-202,339,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00456() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultCategoryDataset|getRowCount=22:java.lang.Integer:Mw==|getColumnCount=22:java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{1000,-841,308,-1000,-61,1000,619,772,-449,321,414,264,208,-605,-611,-257,146,-462,-1000,-1000,1000,-640,-832,-577,1000,-134,-32,903,-1000,-469,-191,244,272,847,-703,915,-230,1000,1000,1000,-143,-689,1000,889,422,-25,-906,88,214,-745,-204,121,505,99,-856,-57,-726,-256,574,-493,1000,880,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00457() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultCategoryDataset|getRowCount=22:java.lang.Integer:MQ==|getColumnCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{114,293,-195,-386,-451,-634,-222,929,979,-22,-226,95,688,209,-868,-1000,-680,-696,-436,-717,466,-949,423,-144,961,1000,532,171,1000,195,-865,-196,-806,361,-245,-67,756,-130,117,955,403,494,724,-549,-96,616,922,-11,-89,-367,-523,-895,985,-421,-570,350,514,-396,654,446,-76,133,496,-363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00458() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{-380,148,174,634,-1000,-128,830,19,-45,-415,666,-385,473,-654,-1,-1000,-784,-299,-1000,-573,564,-182,152,-185,1000,-36,-304,271,-1000,710,-334,-611,-484,894,-970,-530,716,-877,-242,1000,940,122,1000,-258,-612,-860,181,-233,-1000,-500,101,-1000,585,-374,-438,300,474,49,-356,-807,84,77,776,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00459() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{-645,1000,-352,395,-100,-400,546,-81,-708,3,-511,-1000,-1000,1000,-300,1000,902,1000,1000,1000,-194,1000,-427,-1000,-668,-1000,329,-1000,1000,268,1000,887,1000,590,-735,288,-691,586,-1000,-855,-967,-240,-1000,187,410,-438,-16,1000,464,766,946,772,-1000,-231,55,-1000,-1000,328,-60,-34,-348,-568,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00460() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultCategoryDataset|getRowCount=22:java.lang.Integer:Mg==|getColumnCount=22:java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{83,-337,-455,-202,-181,299,1000,359,238,114,759,1000,635,-258,-752,-926,51,-371,-1000,-1000,1000,-435,-885,656,1000,796,532,780,-92,-1000,-1000,-65,-1000,453,-1000,-361,1000,10,863,1000,402,26,-694,732,-96,-301,-420,-259,-1000,615,-1000,-982,1000,-547,287,862,227,-1000,-39,404,14,491,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00461() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{134,-74,597,463,-366,59,-630,-21,109,281,907,706,-238,276,50,-233,-215,-904,-901,-503,334,137,573,-41,508,-656,287,388,-596,-269,-344,91,-497,-182,520,201,616,-751,-145,832,396,-34,-269,-376,49,-335,-383,-532,-726,-56,-300,-723,704,530,-718,60,-859,-677,-481,-707,762,-445,750,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00462() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{403,776,208,359,-1000,1000,-366,1000,-1000,741,435,-28,881,-189,-604,-362,-445,696,-795,-263,-278,498,417,-372,377,-811,-1000,453,114,30,1000,-534,-29,1000,-979,579,527,-496,43,1000,-1000,-137,830,449,1000,-788,187,-242,-386,-767,56,218,781,414,565,-509,557,439,758,-196,819,-1000,336,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00463() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset():org.jfree.data.category.CategoryDataset",
            new int[]{339,80,-96,-377,1000,-606,79,-700,29,-435,528,-640,-1000,60,-1000,1000,1000,496,1000,259,-572,-723,-740,-124,-1000,922,710,401,-105,-992,587,865,-135,-532,192,270,-401,1000,84,-816,629,922,-1000,533,109,681,784,570,1000,-1000,133,747,-1000,-665,230,14,271,-955,-56,124,582,-824,608,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00464() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{-276,-1000,-221,-435,1000,3,259,867,453,-436,-656,-197,-379,158,-580,-136,527,732,-704,-681,-88,-348,-327,-1000,-964,-875,571,-852,-286,-717,231,109,-892,106,-347,-230,-549,1000,-340,-145,-345,-306,-1000,-596,428,-72,-579,568,340,-429,185,1000,313,-425,1000,-268,340,-597,620,-323,-963,-1000,676,757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00465() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{620,-567,-853,-841,-1000,-119,-1000,-893,-226,-1000,182,-651,828,-265,-531,995,-42,-1000,249,804,-1000,1000,-1000,594,854,-150,1000,1000,672,-933,1000,1000,-1000,953,-1000,-1000,266,-1000,1000,-1000,-90,1000,232,-1000,-892,-313,-1000,-1000,1000,-1000,-845,697,1000,-491,-1000,-466,225,-1000,-458,1000,873,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00466() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{10,-1000,-207,216,179,-264,120,14,2,142,-1000,-604,-322,-75,128,-29,690,1000,67,-874,291,-348,-1000,134,-480,403,571,640,-122,-884,-663,1000,94,-271,-650,298,407,-84,293,-321,415,-77,272,535,-226,-1000,-90,970,340,-429,113,267,996,-425,-41,-706,-1000,-558,1000,327,296,304,-28,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00467() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.category.DefaultCategoryDataset|getRowCount=22:java.lang.Integer:Mg==|getColumnCount=22:java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{-1000,833,1000,746,-133,1000,-328,-459,-588,-174,1000,-646,-330,-901,596,-82,1000,-1000,-421,991,-702,846,-710,-799,-828,-681,-1000,-1000,-498,-761,1000,-54,-214,-129,-582,-552,-183,1,-1000,188,1000,858,-1000,-921,1000,1000,949,-683,-428,689,929,84,726,-211,909,1000,-394,-184,-716,-643,1000,158,1000,585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00468() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{823,-840,-446,-611,-79,-1000,-373,-9,6,-500,-340,-286,399,1000,-454,-69,-136,224,848,-562,169,93,-1000,619,413,935,1000,503,1000,339,-388,1000,-799,905,-1000,-600,-69,-432,1000,-649,-366,300,1000,-309,-700,-947,-718,522,1000,-1000,-258,449,-83,-1000,-1000,-832,-551,-824,536,485,-384,1000,671,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00469() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{-120,1000,337,915,211,81,-1000,-710,-616,102,503,-839,-570,-794,1000,-346,1000,-144,-291,1000,107,325,-126,185,-5,-997,-1000,-1000,-1000,-920,364,-1000,260,177,-541,-63,-563,-433,-1000,1000,1000,222,-1000,136,486,786,885,-510,-1000,1000,-467,-1000,813,-182,1000,674,-53,959,-562,-1000,1000,-536,586,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00470() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{-367,-412,469,1000,-979,24,-660,-45,-302,361,-256,-1000,-583,-41,830,730,1000,628,-3,-883,469,-1000,489,-1000,-47,-729,-819,-794,-1000,-1000,-688,1000,1000,-1000,-262,1000,-294,-397,-211,1000,-756,-197,-1000,977,1000,133,1000,791,-1000,994,744,-353,614,53,1000,220,-1000,807,-110,-1000,578,-1000,-1000,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00471() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{-352,860,-396,656,1000,-275,198,-64,-545,-232,205,-134,32,816,1000,13,1000,15,-330,-224,376,-1000,357,-1000,-141,-708,8,-1000,-1000,828,-155,-1000,1000,370,-697,-133,-600,389,-192,786,-708,179,-1000,409,-18,-339,1000,318,-240,556,464,-984,249,-274,1000,-63,-702,746,-493,-754,1000,621,531,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00472() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{-272,-21,980,-580,530,744,184,392,-3,-337,342,-246,495,-525,918,-797,604,-270,-867,-231,216,-442,-789,172,-816,195,454,284,1000,197,-98,1000,270,-696,-271,-526,-220,786,-263,-237,168,267,-110,-46,24,-539,1000,-736,367,-150,706,-1000,-169,-195,429,926,20,-765,-806,-95,938,257,84,-203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00473() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{817,712,-142,586,-653,-221,-554,-923,-789,-343,-488,37,-306,938,1000,270,-117,13,322,404,-184,-255,-1000,459,1000,772,563,984,39,-1000,-810,54,-224,255,-1000,-515,-614,-779,1000,143,-570,1000,1000,-88,-353,-364,34,-238,646,-587,-682,-770,1000,-1000,-751,71,221,491,666,323,455,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00474() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{-440,1000,248,871,366,163,-766,-628,-624,201,710,-979,-492,-794,1000,-590,1000,-476,-583,1000,-123,325,59,-175,-394,-198,-1000,-1000,-1000,17,897,-1000,747,329,-390,-372,-732,-210,-1000,1000,1000,74,-1000,657,888,1000,1000,-820,-1000,1000,-226,-240,711,-168,1000,718,208,1000,-941,-1000,1000,-536,332,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00475() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{1000,-1000,494,69,-970,-1000,-751,-1000,-989,-109,-187,-311,308,1000,-293,492,612,504,1000,-29,-428,1000,-658,786,373,171,1000,492,-458,-1000,105,987,-14,688,-1000,-158,874,-1000,1000,-1000,-994,716,557,288,-463,-1000,-511,-261,1000,-1000,-1000,-266,1000,-1000,-1000,-1000,-1000,-962,1000,1000,375,1000,1000,663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00476() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{-222,1000,-831,495,110,81,-826,-710,-581,-446,761,-570,-222,-824,717,-327,1000,-717,-312,1000,-397,578,-625,-165,-501,-963,-1000,489,-986,212,1000,-933,-220,-254,-243,-809,-619,-433,-1000,432,1000,582,-1000,-604,754,973,784,-790,-699,440,-180,-241,388,-187,1000,674,-53,304,-778,-782,1000,-50,1000,143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00477() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{-174,-342,-492,-865,1000,3,560,1000,754,203,-494,-779,-717,-143,-400,-1000,-103,1000,-683,-1000,997,-1000,-864,-251,-468,-385,1000,-701,1000,160,-369,42,-406,55,-74,-3,-1000,1000,166,649,50,-179,-520,220,1000,576,-508,871,886,411,-102,168,223,-178,1000,775,1000,-54,-206,-714,-830,-1000,479,-57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00478() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{-495,1000,94,-101,-274,306,838,202,-616,-244,503,9,-210,-59,-1000,-953,693,700,-673,-954,-719,325,-11,-959,-323,-313,0,-187,172,-1000,745,970,-923,-56,-146,-1000,81,-502,-347,1000,-371,-556,-128,-1000,662,-367,-624,549,-450,1000,707,1000,444,-513,609,-468,-359,-637,-562,72,-1000,624,586,857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00479() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDataset(int):org.jfree.data.category.CategoryDataset",
            new int[]{1000,716,489,286,238,-177,314,-124,-164,207,840,-617,330,-265,70,-1000,422,108,-343,698,1000,-39,-178,1000,284,-371,-386,-136,586,-44,300,-857,322,1000,-626,113,743,-274,-407,409,639,-33,762,-179,-400,575,-146,-841,417,809,-1000,-440,-497,-958,46,335,479,495,-820,-844,544,-269,-214,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00480() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{1000,-1000,-212,956,-735,595,-647,-1000,-176,-434,1000,773,-813,-570,-1000,-207,-33,50,31,285,-47,1000,1000,-1000,-347,-275,-346,-729,153,-926,518,-535,1000,-142,1000,-190,-1000,-1000,-868,144,-117,-547,1000,599,863,-1000,-50,26,-1000,-557,-114,-738,-183,676,-656,510,-473,-1000,21,-896,1000,309,896,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00481() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{1000,-121,10,471,1000,406,-450,504,-70,285,655,48,-308,-224,729,528,106,880,-1000,-373,-505,-266,-721,-1000,193,1000,246,482,-232,-623,-655,447,252,-952,594,448,328,-1000,-480,249,-722,1000,939,-1000,833,59,1000,109,-1000,1000,-546,200,1000,896,837,766,-188,451,-583,-1000,70,-584,-1000,-389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00482() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{-765,-1000,504,161,1000,595,229,-1000,-145,446,1000,490,911,-570,298,592,-117,507,-649,-867,1000,4,130,-1000,-589,-425,667,-1000,-691,-1000,-59,167,-438,519,1000,366,978,1000,-37,100,-117,-547,957,886,520,985,570,-664,-1000,499,10,749,-183,450,1000,-654,-331,-1000,114,-315,-23,-930,896,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00483() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{-882,-1000,-306,527,1000,752,1000,-937,135,-580,267,197,1000,-585,-1000,654,895,-139,-1000,620,730,788,59,152,-116,466,610,-1000,407,-424,1000,1000,1000,1000,1000,591,348,1000,841,-1000,444,-125,1000,-126,1000,1000,-1000,-923,-1000,-1000,609,1000,-160,1000,820,668,-482,1000,-1000,-1000,1000,-558,-792,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00484() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{1000,1000,-426,709,-636,-787,-545,216,-1000,-119,-813,-310,-1000,-1000,224,-424,-406,33,-434,255,348,688,383,311,-1000,-237,-300,982,1000,-834,-230,-1000,-100,-494,385,18,866,-400,-1000,-178,342,-1000,-1000,-274,-32,-564,-186,183,101,1000,-1000,-856,212,-751,-279,471,-923,-970,385,1000,-1000,1000,725,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00485() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{1000,-555,764,1000,1000,671,-304,504,-604,652,852,-781,-308,-939,780,1000,106,474,-944,122,-124,-466,-1000,-170,404,1000,-59,-746,-595,-727,-1000,160,125,-1000,534,1000,1000,954,219,455,-1000,346,939,-963,406,685,1000,-129,-1000,1000,-605,299,1000,874,1000,1000,259,1000,-214,-780,7,-827,-1000,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00486() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{9,-1000,-552,669,1000,588,1000,-86,-249,-215,114,-1000,289,-413,171,352,1000,-15,-1000,620,940,1000,-182,152,-226,834,34,-1000,-771,-424,-124,1000,1000,726,1000,381,400,1000,458,-343,585,341,1000,-1000,854,1000,1000,-1000,-1000,-819,-206,1000,252,1000,942,1000,-514,1000,-915,-1000,587,-99,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00487() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{606,1000,-96,205,-43,211,460,798,-1000,680,-386,-598,-845,1000,981,-620,-1000,1000,-253,-587,989,975,-200,476,-737,525,-1000,1000,940,797,-339,391,-272,-177,-602,-550,733,926,-798,818,837,639,-1000,146,-807,505,-1000,-439,1000,1000,-1000,145,1000,-1000,784,-68,-977,232,562,1000,-1000,733,1000,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00488() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{609,1000,424,765,924,459,132,1000,-685,945,417,-707,-899,428,1000,393,-217,-1000,-606,869,-103,124,-977,-611,399,956,-1000,1000,742,150,-788,1000,-169,-673,1000,496,632,1000,-30,511,205,995,-1000,912,-807,1000,-1000,-443,1000,1000,-1000,521,412,-1000,621,1000,659,879,491,404,-1000,-1000,361,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00489() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{-841,4,278,-90,-48,403,-1000,-218,1000,-73,397,1000,138,185,-546,1000,245,126,244,-1000,931,-1000,-633,-1000,1000,726,794,325,466,1000,989,41,814,-1000,-421,1000,-874,-684,509,-570,-598,-263,653,-46,276,1000,-272,1000,167,-1000,1000,-342,-846,744,-168,-338,996,-841,210,-753,727,-1000,867,711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00490() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{-794,83,-191,967,1000,111,324,-1000,26,-467,523,-1000,1000,11,-546,221,1000,-151,-1000,-966,1000,306,411,328,-236,-225,429,-1000,-408,460,454,664,-991,443,1000,134,-62,1000,367,-969,-25,-194,1000,-1000,146,1000,-272,-495,-1000,540,373,868,-846,1000,1000,-221,-351,-604,-854,-1000,727,-774,-1000,-809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00491() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{724,-264,-212,1000,-735,854,-544,157,-583,644,230,-1000,-692,-1000,-1000,-207,-128,-280,31,394,47,139,-377,-183,206,356,-415,-429,298,-1000,-156,-1000,-1000,-1000,309,1000,1000,258,-273,261,-1000,-1000,-1000,1000,-294,-118,1000,595,-1000,552,-504,-1000,657,-1000,-373,-32,531,-304,707,316,7,309,476,88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00492() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{1000,839,-118,-381,-71,1000,334,1000,-758,738,-23,-848,-1000,1000,678,-27,-1000,580,742,-96,1000,278,-805,-64,-939,1000,597,1000,806,1000,113,275,601,255,-884,131,-82,220,-419,1000,774,1000,-1000,425,-146,619,-837,493,1000,-978,-1000,-1000,1000,-751,-93,533,-5,-1000,541,458,-1000,-198,870,-281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00493() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{-47,-750,414,101,24,308,132,-451,456,-779,1000,1000,266,-261,-1000,1000,-745,-313,1000,-1000,-273,-611,445,-398,346,-461,692,-294,147,193,1000,-52,1000,-424,91,243,-1000,-1000,-38,226,379,-109,683,561,366,-571,-1000,101,-291,-1000,1000,122,-788,834,823,634,-102,879,-467,-1000,1000,-476,772,310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00494() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{-1000,-1000,-306,1000,910,-268,219,-1000,655,-1000,438,197,1000,-1000,-1000,674,1000,-718,-1000,674,730,339,1000,382,-675,-1000,1000,-1000,407,-1000,1000,725,1000,653,1000,-435,-1000,246,302,-1000,665,-688,1000,-387,1000,229,-1000,-923,-1000,-1000,1000,1000,-1000,1000,-404,123,-774,528,-1000,-1000,1000,-334,-792,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00495() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetCount():int",
            new int[]{-317,-234,779,645,-1000,1000,666,-9,-1000,395,158,-508,-84,-824,-384,772,-447,-1000,1000,1000,1000,355,-454,1000,784,626,-745,-37,930,-861,689,-433,-431,366,-716,1000,1000,1000,956,126,395,-1000,-957,1000,-807,1000,-1000,-499,1000,-1000,-191,267,188,-927,-352,723,733,402,708,1000,-337,14,1000,572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00496() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-606,-325,239,133,312,-373,-94,-245,-143,-855,-1000,-81,573,-1000,-193,116,1000,-61,-61,-541,39,-397,754,-524,581,-426,669,-738,-1000,1000,1000,-50,-1000,-542,504,-802,20,321,-491,-690,-321,276,1000,16,-809,1000,177,-1000,105,1000,-855,-1000,-654,-609,1000,-413,1000,-239,-68,703,578,-451,-317,496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00497() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{944,-1000,409,467,-353,-974,-74,-358,-276,-500,-1000,-480,1000,-468,1000,-502,548,-259,-716,317,-372,-668,-326,-108,958,21,1000,1000,-1000,57,1000,-103,-393,-588,236,-324,48,-774,-59,18,-666,26,1000,-370,-1000,113,68,612,177,556,-156,-710,488,-568,452,230,-199,764,157,141,-203,210,-1000,624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00498() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-796,1000,-236,50,435,96,-286,1000,-162,-53,-993,581,778,-431,431,155,1000,451,-455,467,1000,263,-592,-310,271,-1000,-88,-282,-943,452,532,-938,-493,-833,325,-597,-1000,-71,804,183,1000,298,611,60,-1000,314,-888,938,-146,513,-822,-501,-613,270,340,-293,-944,1000,-516,703,881,311,-613,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00499() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-1000,-177,-338,520,-18,1000,-870,1000,-277,309,764,138,-746,-18,-990,1000,-18,1000,198,716,-478,185,-6,-200,-380,-1000,-1000,-246,-37,-221,-775,208,1000,-316,618,-629,1000,874,233,380,1000,-498,-323,-739,1000,1000,597,-669,-14,-595,859,-638,-107,1000,687,-483,512,-98,-65,501,1000,-268,200,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00500() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-629,1000,324,-575,1000,956,120,31,-650,-173,-136,729,251,-572,-738,677,526,-265,-260,-615,1000,590,659,-553,-591,-305,-1000,-1000,-765,760,994,-678,-957,105,919,-678,-714,1000,85,-542,261,-443,1000,-261,-625,1000,-149,-1000,-179,256,-139,-400,-450,-231,1000,-1000,1000,-74,-233,1000,1000,-536,85,144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00501() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-503,1000,23,380,4,503,139,656,291,-9,-678,366,508,-119,-93,140,412,559,-641,244,-346,-668,-30,284,-1000,-487,-131,378,-528,-442,141,-165,-469,-341,311,-92,639,86,35,-189,-268,-22,270,-740,-659,59,1000,-199,478,183,-159,-463,-692,-1000,497,144,233,-68,-361,-104,586,-66,-257,107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00502() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{1000,-1000,-946,133,830,-373,1000,-1000,-964,504,-380,-81,-1000,586,1000,611,-1000,-61,-164,-317,848,224,-179,-572,-157,-715,388,-438,-841,-1000,1000,-957,-1000,970,279,129,1000,610,-491,218,-1000,-1000,1000,-1000,-809,-780,177,-663,1000,1000,811,878,-261,-1000,-8,-779,1000,611,-1000,292,-757,-1000,-242,826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00503() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-669,949,812,452,435,230,-478,480,-420,-580,-833,-308,1000,-1000,-622,264,1000,-186,-455,-606,595,183,880,-365,179,-728,-1000,-47,-1000,1000,1000,-62,-1000,-1000,1000,-1000,-666,-71,298,-534,332,276,1000,180,-625,1000,-436,788,-755,-56,-948,-1000,-719,96,1000,-675,-944,1000,480,996,1000,311,-478,426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00504() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{1000,-1000,434,-305,-683,-400,561,-1000,-485,-193,-400,1000,-858,1000,457,197,-1000,238,-17,152,-572,-1000,-1000,-573,489,-1000,195,1000,161,-1000,195,-418,1000,784,49,619,1000,465,-491,-135,-942,-1000,542,-1000,50,-1000,559,642,698,186,1000,980,1000,-324,-1000,-21,154,988,-833,449,-1000,-128,-199,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00505() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-886,1000,574,351,170,-878,554,-889,378,-649,-864,-1000,838,-479,30,-525,-964,-977,-1000,689,-718,-285,-81,-1000,-954,-41,-436,1000,-1000,1000,534,979,-149,-692,1000,-168,847,-1000,-1000,-714,-975,744,1000,1000,-10,1000,578,1000,-1000,-353,742,-1000,-629,-1000,1000,386,-1000,946,513,1000,490,-289,-21,266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00506() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{1000,1000,-492,-1000,-213,-1000,946,1000,129,809,373,-742,385,414,1000,259,-264,1000,-749,-527,1000,603,77,-526,-868,-1000,1000,519,-884,-667,274,-1000,-469,-351,-559,207,-1000,525,705,1000,286,-684,-8,-907,-1000,-615,320,1000,1000,1000,1000,566,248,-688,-348,484,712,794,-348,376,414,-471,-696,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00507() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{192,315,-587,309,177,1000,946,397,-658,-22,180,-192,-689,334,-777,1000,-793,-1000,536,-130,482,-123,273,105,-945,-856,-309,-549,-130,-341,281,168,-528,-321,-585,-619,1000,1000,980,30,54,-511,-385,-829,327,506,-117,-603,129,806,520,134,422,583,939,-1000,731,-492,-471,722,836,-369,-364,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00508() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{468,893,97,-241,813,910,471,31,-1000,-245,407,729,539,-705,73,774,109,-127,-506,-434,1000,925,213,-553,-809,-1000,143,-1000,-973,661,1000,-1000,-757,-1000,657,-790,-1000,903,763,359,442,-1000,1000,-767,-1000,1000,-533,-1000,3,562,376,-805,-105,256,1000,-1000,977,289,93,1000,1000,89,-690,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00509() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-864,-400,239,-488,1000,501,-232,-1000,-1000,125,-217,1000,563,-475,658,165,52,1000,-1000,-759,1000,464,541,-803,446,-1000,669,-738,-1000,259,1000,934,-1000,-341,504,-250,1000,321,116,525,-871,-1000,1000,-697,-1000,483,1000,-475,389,1000,805,-326,-221,-1000,150,445,589,835,-68,1000,1000,-245,-690,-281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00510() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{972,1000,623,-850,1000,1000,992,663,-700,1000,1000,1000,-562,1000,709,-134,-1000,-690,-1000,-16,1000,1000,-1000,178,-1000,490,103,-1000,-325,-610,179,-1000,48,1000,-757,112,-1000,233,1000,573,473,-1000,-225,-1000,-871,-259,179,1000,635,251,1000,1000,1000,-153,-1000,-1000,-631,1000,-559,356,-459,-295,-93,-303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00511() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{770,330,-952,469,-1000,60,51,996,-484,-146,-683,-577,-596,1000,289,57,-704,1000,375,1000,99,-162,-787,-300,348,70,217,494,105,-607,-430,-189,1000,-9,-1000,220,-321,634,445,276,890,26,-759,-370,411,229,-323,1000,366,-116,1000,242,1000,799,-189,63,-822,-278,-8,677,554,-98,-428,-632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00512() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{-871,-850,469,649,-1000,98,286,-280,-1000,-225,1000,-338,723,20,298,168,1000,955,-833,1000,1000,-1000,1000,590,-381,-240,311,-792,1000,-804,-599,437,-791,-1000,294,-978,-35,-733,302,1000,1000,-542,20,-579,-539,-446,-1000,1000,675,-950,-557,-1000,-231,565,306,1000,-760,222,5,-1000,1000,1000,861,565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00513() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{-896,-22,218,-342,225,-317,-567,-172,627,-867,819,-818,624,-198,631,-1000,-966,-493,1000,246,-612,594,-235,48,-319,-1000,70,513,-1000,1000,184,-739,-385,-679,-495,-131,-95,965,1000,-270,-174,202,-205,202,246,311,1000,-530,-402,407,-47,949,-611,-1000,-554,428,760,-434,1000,295,-947,-4,-667,-426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00514() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{-1000,863,-975,574,-1000,817,47,-1000,-495,1000,-66,-947,586,156,507,1000,-1000,-868,828,-967,-1000,1000,-1000,-205,-402,930,1000,1000,-1000,1000,1000,636,-1000,-763,-1000,-1000,390,384,112,1000,-1000,-1000,725,-868,-1000,116,-375,-1000,266,527,1000,-551,-1000,-416,1000,708,-1000,1000,-1000,-1000,1000,999,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00515() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{504,1000,-461,-13,690,885,-121,1000,1000,-247,819,-470,1000,80,-531,-688,-1000,-1000,1000,-717,-1000,319,-740,759,-683,-801,397,40,-568,1000,990,-573,-494,110,-114,1000,252,1000,727,-365,34,494,-1000,735,1000,264,1000,295,-577,1000,-449,1000,264,-563,-1000,-47,860,-121,1000,1000,-1000,-131,-1000,-260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00516() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{622,-215,-111,1000,-39,98,153,-956,-292,1000,-621,-211,710,586,713,553,395,-908,400,-1000,-553,418,-26,-1000,-850,-171,-247,1000,-1000,557,1000,1000,-791,-691,-527,-1000,1000,-733,-243,826,-1000,-1000,647,-465,-1000,175,-738,-1000,467,824,1000,-547,-22,565,1000,642,-1000,449,-322,-1000,1000,1000,-1000,565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00517() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{284,-320,-881,1000,-28,484,-60,1000,1000,-1000,1000,597,480,-1000,-1000,-1000,-1000,-225,-958,1000,1000,-1000,539,1000,-852,982,-192,-1000,1000,-608,-1000,-1000,539,924,-1000,1000,-1000,-47,-55,-42,490,1000,-1000,1000,1000,-1000,278,1000,6,-19,-840,1000,736,-536,-1000,1000,1000,-476,-344,1000,-1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00518() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{105,820,129,318,-469,535,-162,-1000,-57,1000,74,-837,755,357,297,-275,-281,-812,485,-1000,-859,1000,-573,-57,-594,341,1000,1000,-961,913,797,468,-1000,-170,-1000,-641,561,113,18,618,-1000,-234,181,-620,-315,-144,-64,-1000,63,483,1000,166,-1000,-48,583,81,-482,720,-1000,-1000,458,768,-600,-750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00519() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{-226,374,-171,706,189,892,475,761,1000,78,-72,-863,817,530,316,-1000,-1000,-1000,179,-310,-618,211,536,-185,-1000,1000,-335,381,202,939,-848,69,-974,-1000,-1000,-440,-19,-64,681,448,71,829,-1000,379,-492,-483,919,444,136,357,122,33,-467,111,805,1000,-186,-221,-145,47,-1000,138,700,373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00520() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{1000,736,89,680,-989,-77,-1000,-1000,-57,1000,-217,-1000,1000,1000,1000,-275,29,-1000,1000,-1000,-1000,1000,-1000,-622,-940,-716,1000,1000,-1000,1000,1000,432,-1000,-954,-1000,-1000,1000,93,79,925,-1000,-422,1000,-1000,-173,380,602,-1000,305,990,1000,645,-1000,-649,122,-169,36,914,-1000,-1000,440,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00521() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{156,-1000,579,-753,1000,-636,-684,-1000,1000,-1000,1000,-483,914,-251,-95,-1000,-966,-585,1000,421,-612,118,-53,722,-515,-1000,-352,-159,-698,-1000,177,-1000,-30,-68,125,1000,-41,1000,1000,-1000,550,1000,-1000,1000,1000,415,1000,377,-992,738,-1000,1000,274,-1000,-1000,-101,1000,-1000,1000,1000,1000,-795,-667,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00522() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{469,228,129,-48,-482,535,-823,-266,1000,-1000,819,121,987,357,1000,-1000,-448,-1000,821,270,-413,682,625,-1000,-1000,341,-1000,-46,-318,835,213,49,-672,-1000,-99,27,-66,-71,633,618,366,1000,-682,-245,-98,-144,594,-182,381,130,184,1000,-1000,278,-479,504,551,-1000,912,710,-1000,-102,-332,-609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00523() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{-960,1000,-526,-58,-312,363,291,-1000,726,73,-191,-1000,510,-71,529,-192,-980,-645,741,-134,-1000,1000,-1000,-162,-460,-1000,1000,825,-1000,1000,924,-799,-997,109,-978,-48,174,1000,381,-434,-699,21,1000,-448,-5,-100,1000,-1000,-942,871,1000,33,-1000,104,131,654,492,714,-226,-93,-218,-275,-1000,-794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00524() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{-1000,-1000,-491,-864,1000,-235,691,741,-214,912,182,-1000,1000,-17,-307,269,-478,-298,745,87,75,548,527,1000,405,302,231,600,-580,498,-1000,-87,901,-717,-1000,-614,674,1000,-145,-1000,369,-441,-540,840,769,752,1000,323,-80,845,-285,-129,266,-1000,-1000,424,-1000,433,-717,-123,-1000,-414,572,547}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00525() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{-950,85,1000,1000,-307,627,-66,279,400,-943,345,-473,1000,1000,-796,-1000,297,1000,-1000,1000,137,-89,1000,-381,-1000,-150,-942,-481,1000,387,-494,665,-1000,-1000,295,645,852,-902,-299,218,-304,858,-813,-821,-355,-1000,-23,1000,1000,-62,-440,400,-496,1000,200,706,333,-980,999,-495,-400,1000,1000,-171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00526() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{1000,1000,-721,-290,60,1000,-23,-1000,905,1000,-721,-1000,772,842,998,690,-1000,-1000,1000,-1000,-1000,1000,-1000,-921,-836,1000,834,-824,-263,1000,-225,209,-1000,-200,-1000,-764,601,1000,0,171,-1000,-340,11,-476,-442,-272,1000,-1000,-196,1000,1000,849,-1000,-325,1000,11,-171,1000,-1000,-1000,-284,351,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00527() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis():org.jfree.chart.axis.CategoryAxis",
            new int[]{-687,282,473,728,-183,594,304,-1000,-46,1000,-72,-1000,699,1000,1000,287,327,-1000,240,-967,-517,1000,774,-1000,-803,1000,-578,914,-197,1000,-517,674,-1000,-806,-797,-1000,362,-191,199,220,-1000,-46,207,-1000,-783,-1000,370,-870,605,584,1000,-86,-904,797,755,654,-356,668,-400,-1000,400,866,1000,-570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00528() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-475,329,-72,-142,-196,-1000,168,-391,-1000,441,-3,-18,-816,-24,-250,319,694,-486,810,1000,161,-671,867,1000,-1000,-1000,1000,-844,1000,166,-578,1000,-487,526,-392,359,1000,1000,213,-1000,-286,-740,208,-770,-499,-1000,-1000,127,300,374,1000,-606,-713,-512,-1000,320,1000,-177,-1000,-1000,1000,507,1000,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00529() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-784,1000,-176,-365,812,-1000,-278,1000,-1000,708,-412,-868,-735,334,-482,-375,565,1000,717,1000,746,-947,-519,1000,289,-1000,1000,-1000,228,1000,-1000,1000,-641,1000,-1000,687,1000,1000,858,-938,383,-1000,1000,-173,-650,-1000,-1000,-450,249,765,1000,175,-495,376,-1000,-1000,1000,-424,-1000,-5,1000,-197,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00530() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-526,677,-542,-131,1000,-100,-519,1000,-3,849,-971,-380,194,-309,-776,-777,-368,-468,671,868,245,453,-533,451,-22,393,1000,406,872,395,-977,1000,-826,1000,-262,-499,1000,280,-522,-974,626,324,345,-1000,-745,-645,-46,-1000,-1000,-887,1000,1000,399,517,-535,1000,-120,121,-778,400,855,4,678,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00531() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-51,973,757,-732,1000,1000,206,299,823,382,722,-470,808,-335,-996,-1000,-1000,-571,-836,-882,748,1000,-1000,-136,1000,1000,1000,535,868,432,-662,1000,-927,706,-5,658,686,740,-1000,420,460,519,-445,602,599,349,773,-772,-1000,-570,-1000,1000,1000,1000,-632,-601,-467,1000,1000,1000,-1000,280,477,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00532() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{446,189,-369,-294,-636,-380,173,-804,-164,263,540,69,-13,-662,-68,1000,877,232,-136,-388,-274,-51,1000,378,-1000,-13,187,-433,671,-1000,1000,-126,-1000,-762,791,722,-62,-64,150,275,474,-1000,-1000,-186,1000,-120,-143,255,559,496,-370,-982,-861,-860,406,-993,21,590,-519,-990,419,1000,299,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00533() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-1000,-691,831,-1000,1000,864,372,-75,-464,629,1000,-881,-696,1000,-326,905,-485,-1000,749,-536,1000,713,347,1000,-334,-964,1000,-598,615,1000,-1000,-73,-1000,1000,-1000,1000,1000,1000,-670,1000,-5,-564,277,-527,73,-230,-664,-277,153,667,1000,572,945,-854,-1000,1000,529,377,13,677,481,289,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00534() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{9,746,-501,-358,-411,785,126,-795,855,464,574,-136,863,-457,-934,-848,-796,-700,-106,212,136,913,-962,-175,156,984,843,692,667,-424,145,615,-712,297,259,-142,82,-510,-828,824,531,372,-43,-566,-566,87,944,-900,-937,-917,494,16,904,953,376,669,-909,934,671,898,-757,654,-86,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00535() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-313,-851,144,-435,-434,-1000,-266,-11,-800,598,184,169,-811,-355,-119,1000,1000,78,606,257,-10,-590,1000,759,-738,-1000,483,-1000,228,-161,431,669,272,172,113,1000,592,1000,149,-1000,1000,-403,-619,-25,828,-679,-1000,130,753,1000,609,-542,-1000,-1000,-838,-1000,1000,-148,-1000,-1000,1000,991,1000,-831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00536() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{677,661,-84,236,-1000,-651,867,-1000,-626,17,-874,293,692,-419,-609,707,190,-66,47,202,-785,-727,723,-622,-245,-975,-139,-540,650,-904,1000,-342,-1000,-473,987,887,240,-490,771,-1000,268,-1000,-1000,-696,-696,643,-258,910,26,768,-918,245,-369,-711,769,-1000,-87,-526,-335,-1000,594,1000,223,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00537() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-54,1000,-507,499,-216,772,1000,431,120,126,755,394,532,233,535,-1000,-1000,-22,261,670,-198,-109,574,380,61,350,689,369,-612,461,-656,-28,-506,-135,-252,1000,-3,282,666,-968,1000,-102,-1000,-1000,-817,-84,-1000,89,-18,234,1000,229,660,1000,985,20,-258,703,-687,-612,-261,82,322,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00538() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-714,973,-791,-259,686,286,-463,-250,716,856,603,-791,692,-634,-1000,323,-973,371,435,1000,-161,855,-1000,-285,895,981,-16,-1000,724,180,-792,1000,-415,795,248,-1000,749,-395,-570,878,1000,519,484,-1000,-1000,-448,363,-783,-1000,-812,1000,783,1000,-861,-226,1000,-655,256,492,1000,-665,-208,541,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00539() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{652,-184,-676,-959,-711,-1000,-159,-280,-1000,3,370,-594,734,-715,-273,-947,-582,-94,-760,-309,13,1000,-869,-175,872,984,843,733,280,-824,376,-533,329,-547,1000,307,-671,-510,-1000,-247,31,823,487,279,308,429,944,-1000,-447,-478,350,222,-809,949,706,-198,-909,467,674,1000,-1000,615,-86,722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00540() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-461,974,-167,219,-376,-590,756,-516,-928,236,-1000,-294,-1000,601,-465,-154,786,-1000,1000,622,250,-1000,751,1000,-962,-1000,1000,-891,1000,966,-1000,1000,-1000,1000,-1000,807,1000,1000,908,-1000,-379,-1000,1000,-1000,-1000,-1000,-1000,845,82,562,1000,-354,495,-519,-1000,1000,1000,-1000,-1000,-1000,658,16,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00541() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{9,-254,-226,73,-267,-1000,69,-125,2,517,309,-1000,752,-1000,-627,809,549,1000,192,479,-1000,-590,313,373,845,-986,1000,-830,817,-1000,1000,-1000,584,-998,957,-817,0,-590,95,63,-290,-367,-1000,5,224,-751,-664,75,-111,442,-1000,802,-1000,243,678,-806,52,614,68,-539,-213,829,775,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00542() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-284,1000,-252,-435,-936,-570,70,585,-803,-729,-347,-875,-511,-135,-13,-739,1000,1000,697,-366,-1000,-434,1000,759,643,-795,-1000,-1000,1000,-1000,431,-1000,-1000,-1000,1000,766,377,78,849,-537,-490,-403,-1000,-503,1000,1000,-1000,838,753,1000,-570,-542,-713,-1000,1000,57,677,-931,-1000,-1000,1000,991,1000,-831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00543() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxis(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-907,149,108,-97,-91,-327,-184,-269,-545,746,-826,-672,-354,465,-711,-852,476,-1000,1000,1000,632,-487,-533,650,183,-416,1000,-708,872,792,-977,1000,-1000,1000,-1000,697,1000,890,-515,-576,723,-994,732,-566,-870,-476,-456,7,-480,483,1000,97,410,8,-1000,577,491,-466,-729,119,643,202,678,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00544() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{765,-1000,593,-915,1000,-840,307,-169,459,493,-96,-309,-364,-498,102,-666,-363,-721,-177,-50,557,-858,-1000,-1000,46,-1000,-884,926,-98,1000,-572,555,306,511,-539,-779,641,-1000,1000,-564,520,110,-411,-158,-178,-42,-1000,-173,-959,-198,1000,-666,-577,-435,624,-824,237,454,-397,440,980,376,-40,-727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00545() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{1000,-75,973,-191,719,-545,-69,-662,-1000,939,368,-380,-174,-988,677,74,-351,476,-1000,8,690,-539,-703,-107,374,-88,-780,333,267,5,1000,-1000,734,699,-764,-430,-473,180,519,875,-76,-1000,401,-330,1000,1000,-154,-572,-569,-435,1000,-1000,-1000,-811,499,-169,636,780,1000,804,1000,1000,-429,911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00546() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{677,-205,200,-591,126,-1000,351,-357,495,-300,-399,-961,1000,-595,807,21,-582,-581,344,-757,464,-610,-320,1000,-246,-412,-907,419,639,-242,100,551,-311,973,-719,-605,545,-501,663,-1000,178,755,-733,-1000,-27,-240,-558,-852,-865,-704,-119,-617,-474,-235,-54,252,-90,2,-346,-374,666,543,582,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00547() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{1000,-1000,-411,1000,417,-487,-722,-47,1000,394,-616,-580,-473,1000,1000,339,414,937,405,994,-456,499,702,683,-59,199,-217,397,-194,315,-353,329,-889,-193,888,-67,1000,-778,988,-939,-54,1000,-1000,1000,-808,-461,-535,-633,443,-545,-886,1000,1000,-388,-627,1000,-48,-624,-1000,-451,-665,-466,674,-72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00548() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{777,-286,-21,479,719,-142,726,-70,283,853,-406,-226,376,-988,-807,-298,-526,-426,111,529,346,131,-391,-335,246,-13,-52,387,260,242,-100,109,-332,-2,162,324,-18,-155,1000,875,532,44,401,38,-860,1000,-772,-572,-414,-66,119,-423,203,-603,339,-252,-101,-549,-152,224,959,337,126,-218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00549() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{587,-461,-525,1000,-1000,318,877,-160,-427,-829,189,-72,291,-295,87,635,-402,963,-365,192,67,1000,-3,1000,-817,1000,-651,682,1000,-302,1000,-615,815,-546,683,1000,-1000,1000,374,-527,257,-685,670,274,394,511,-111,526,810,-182,-842,-410,521,-866,-178,195,-99,-794,1000,5,-834,607,712,625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00550() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{556,628,-388,29,-1000,-21,104,418,552,278,149,-759,1000,-901,251,924,-74,-804,-697,821,206,-700,205,-283,-717,282,244,547,876,-1000,-572,-210,-721,369,275,634,-865,484,700,1000,-123,-287,448,-31,64,-653,22,-646,-245,-736,-636,-1000,272,-1000,-442,1000,508,-165,292,-96,294,526,156,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00551() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{689,-234,-12,52,719,90,888,-690,110,242,-244,362,946,-901,778,-1000,-1000,-1000,-76,958,550,-605,-1000,358,1000,492,306,221,201,1000,436,-474,676,-439,104,693,-490,40,1000,830,1000,-721,677,1000,-893,45,-367,949,-456,502,679,-319,-936,-807,1000,-229,-319,804,299,1000,264,509,565,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00552() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{1000,819,-914,9,-400,-1000,-287,325,-595,711,481,-731,120,99,-400,1000,18,-226,-280,-1000,-536,65,744,400,-1000,215,-598,1000,1000,-1000,1000,-8,-50,612,-26,-798,-22,196,636,-909,-953,208,-557,-1000,835,272,-594,-1000,-873,-1000,-1000,6,458,-517,-1000,1000,241,314,-131,-460,1000,273,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00553() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{657,-358,874,-713,1000,-1000,297,-237,717,157,-389,-523,-800,-463,1000,-659,-1000,-1000,0,-11,702,-1000,-1000,-1000,805,-988,-1000,981,-456,755,-395,210,-746,389,-438,-1000,-468,-1000,1000,-1000,515,301,-564,-1000,-1000,-356,-1000,-639,-85,-64,-1000,-1000,-1000,-224,60,-452,12,526,-126,909,929,442,430,981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00554() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{899,-420,114,1000,1000,-109,-643,1000,755,128,-603,-1000,115,-50,1000,163,-418,-921,219,-670,-905,-1000,-8,-1000,226,-1000,-907,0,398,-626,-925,1000,-1000,1000,-221,185,1000,-567,492,-493,-169,1000,-1000,-638,-265,-783,-283,-1000,266,-1000,-474,-1000,-668,-657,-585,1000,68,914,-1000,405,346,559,-767,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00555() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{1000,31,12,-1000,1000,-1000,279,140,-632,20,-340,-1000,-62,-587,1000,468,-492,-921,236,25,-18,-1000,-158,-1000,46,-844,-1000,926,694,-459,234,510,-1000,927,-395,-1000,831,-319,317,-280,-50,602,-411,-1000,215,-42,-393,-903,-660,-1000,-138,-666,-751,-447,-612,724,237,301,322,421,472,673,-668,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00556() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{784,-19,1000,-1000,1000,-1000,-25,310,780,363,-204,-526,-1000,-414,1000,523,-863,-1000,50,-320,808,-1000,-3,-1000,1000,-1000,480,409,-719,911,-448,129,-535,-124,-959,649,-278,-1000,1000,-1000,339,296,267,-1000,-328,-359,-1000,-956,-661,-663,1000,-1000,-1000,-82,439,749,-293,999,-56,448,-81,564,-972,-109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00557() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{-535,-415,-558,827,-798,-320,328,812,-781,-697,44,-279,607,-422,-188,-150,-540,-909,-471,202,290,673,-581,907,-339,732,935,234,611,303,917,-251,244,-754,875,645,-806,299,595,-77,582,60,247,665,-429,-598,-354,414,618,309,-598,-605,171,-334,301,172,-330,-853,477,207,-540,23,652,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00558() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{454,-795,1000,-1000,1000,-1000,763,-432,876,-268,-1000,-576,-722,-1000,1000,-1000,-1000,-1000,765,643,913,-1000,-1000,-265,1000,-1000,-1000,-195,-547,1000,-1000,836,-1000,926,-1000,-1000,1000,-1000,1000,-960,1000,650,-467,-100,-1000,-704,-1000,75,-768,-55,1000,-1000,-1000,-61,1000,-1000,-413,126,-826,181,624,605,1000,-966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00559() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisCount():int",
            new int[]{492,23,-454,-1000,-249,-846,1000,-784,-458,939,-296,-572,485,-1000,724,-355,-810,-456,509,-81,1000,-443,-237,1000,-238,-142,-651,270,696,118,824,35,244,436,-621,-435,256,49,519,-499,645,50,109,-370,-124,-25,-369,-96,-1000,-279,-213,-453,-1000,-468,603,-612,-350,780,1000,50,-86,607,1000,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00560() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-904,789,684,-430,407,-966,-61,653,339,-1000,-1000,-201,267,1000,-982,-297,-321,-484,249,892,691,636,813,1000,-611,1000,-60,-1000,-909,-980,-883,650,-1000,220,-728,-1000,683,779,-667,-1000,1000,1000,87,316,482,936,362,1000,590,565,896,-110,-306,-183,-737,-300,-1000,87,1000,-444,-88,-922,734,789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00561() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,1000,-12,506,1000,-1000,-887,-780,496,-1000,-769,32,984,646,-1000,-396,-702,-1000,-399,1000,321,1000,-698,1000,-1000,1000,591,-452,-1000,-1000,-1000,-528,-1000,235,-549,-838,1000,-410,-1000,-714,930,1000,1000,-71,814,1000,-175,-464,836,-1000,919,574,-1000,407,-1000,763,-1000,781,-50,-783,-289,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00562() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-984,366,-676,378,576,-655,-717,802,-137,-303,195,-206,1000,283,-167,228,-857,-263,662,1000,-1000,399,-334,301,-332,427,650,779,-1000,-1000,-1000,1000,517,390,-86,175,136,-918,-422,628,266,990,1000,-447,-748,899,-268,-585,-61,14,82,1000,-1000,290,-1000,445,-500,1000,-857,-965,-247,-675,541,303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00563() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-992,1000,-41,-14,866,-865,-1000,787,291,-71,493,-576,-558,-86,-1000,-684,-1000,-1000,1000,694,-26,246,-777,942,-373,619,1000,-950,-1000,-239,-620,41,-115,520,117,546,500,-578,-607,749,-114,38,-179,998,861,40,-1000,674,881,400,-326,34,-139,907,-1000,-270,-1000,268,-519,-1000,-799,173,826,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00564() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,1000,74,213,-1000,-693,-1000,673,1000,-352,-373,-1000,189,-83,476,-1000,-900,-841,-692,1000,-56,1000,924,371,169,-109,366,351,63,553,-1000,308,662,696,-17,-718,-1000,-1000,776,-741,678,-671,1000,887,-51,-675,-297,-728,1000,-293,695,1000,453,552,-1000,-583,-1000,-468,-615,-461,-492,153,43,722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00565() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{1000,1000,-811,968,1000,-950,-1000,869,-389,-155,546,-391,-460,-530,-382,-1000,1000,-1000,-740,691,57,1000,-163,1000,-740,207,1000,-1000,-1000,948,-379,-395,-1000,-1000,-690,870,833,-320,-827,1000,-172,-390,-1000,1000,1000,40,-1000,-241,1000,932,-1000,-1000,-110,1000,-498,-1000,-814,1000,-1000,-822,-607,-468,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00566() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-721,738,-246,-113,690,-780,-448,869,264,-693,-533,-169,545,771,-1000,-492,-1000,-890,316,1000,-350,445,487,1000,-867,1000,677,-950,-1000,-972,-924,582,-1000,250,-318,-299,470,-306,-758,768,1000,1000,312,339,872,833,-1000,973,1000,843,-315,171,-506,691,-1000,319,-1000,257,34,-1000,-1000,-1000,185,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00567() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,386,-17,-810,-345,-624,1000,522,1000,-1000,-123,-1000,-124,822,400,-486,95,-356,602,678,-986,-244,1000,-1,-21,1000,561,450,-20,-750,452,-113,648,1000,-54,-19,-576,212,-78,-101,815,-117,1000,607,-382,-4,-79,-86,-161,-400,679,306,191,-493,31,290,-20,-716,203,-659,-323,-173,-1000,614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00568() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{419,-439,56,-828,-1000,-135,-932,-304,148,-785,-8,-411,504,-23,306,368,-250,-169,-520,-289,-544,-611,1000,458,-479,370,-313,-145,211,-412,-737,-382,-1000,1000,-228,-864,523,847,-1000,-111,-135,400,567,-474,1000,294,171,56,400,189,488,-195,164,269,1000,210,569,-1000,1000,-642,-841,411,-170,546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00569() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-265,1000,-677,523,1000,-992,-208,611,-1000,-769,198,988,60,615,-1000,-488,-565,-358,-103,233,102,1000,-669,355,-1000,710,504,-1000,-1000,-484,96,1000,-797,-985,-208,-100,816,103,-740,1000,386,1000,-930,60,267,1000,-976,-66,828,1000,165,-156,-767,1000,-990,-157,-1000,1000,-578,-641,-588,87,1000,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00570() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,987,282,-117,-536,-651,-729,-10,264,-286,61,-984,-717,410,-991,-904,-667,-785,557,302,977,1000,525,1000,-898,1000,591,-125,244,-1000,-177,-228,-1000,471,-148,-1000,-306,1000,-920,57,-255,1000,-391,335,927,1000,-332,659,799,357,1000,-360,6,540,-871,-189,-20,395,1000,-517,-553,-962,170,228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00571() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-906,153,-516,263,1000,-722,117,1000,-416,-727,-1000,821,638,512,-491,397,-1000,-776,-750,522,203,791,-196,462,-1000,1000,100,-1000,-633,-1000,-1000,1000,-348,118,-827,-1000,1000,902,-479,52,457,1000,681,-1000,6,1000,440,-85,659,-251,1000,-15,-808,542,-737,32,-717,1000,884,-122,-40,-756,526,895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00572() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-923,1000,177,-619,-1000,-548,222,1000,765,-475,-1000,-825,-332,89,-1000,244,144,88,-942,50,-402,-1000,766,-861,-55,-260,106,858,1000,501,-742,-683,1000,1000,244,-329,-1000,346,811,-1000,-585,-1000,1000,-194,-1000,-373,300,-1000,-608,1000,1000,460,1000,-603,1000,-192,-1000,-1000,951,-752,-284,678,-27,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00573() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-28,856,-351,442,-730,-769,-835,653,-97,-7,-159,292,-502,240,-1000,163,-1000,-79,400,541,-94,288,-1000,900,-717,-666,1000,-1000,-686,-80,-171,650,494,-447,340,1000,956,-1000,-297,287,118,286,-317,300,482,328,-804,-1000,576,307,1000,640,535,-1000,-1000,-300,-1000,613,-1000,-1000,-259,-179,734,789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00574() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{1000,912,417,822,943,-756,-624,499,-723,12,-324,771,175,-550,-648,-156,-1000,-892,336,573,289,565,-196,1000,-1000,-901,986,429,-378,-280,-549,779,-876,-934,171,1000,487,-1000,-491,1000,778,-263,293,482,383,40,-1000,-923,1000,1000,-566,513,-79,1000,-784,-516,-314,440,-1000,-1000,714,-554,1000,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00575() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-340,828,-266,-15,1000,-934,-481,1000,-856,-71,-315,415,828,1000,-1000,-506,-1000,-890,409,694,888,246,547,970,-531,416,680,-950,778,-211,-544,-377,-545,417,-7,316,372,600,-364,218,1000,38,-635,974,1000,40,-1000,1000,642,1000,517,-658,353,691,-1000,434,-1000,777,-563,-1000,-1000,-1000,928,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00576() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-4,609,-472,485,1000,-1000,-318,72,-184,20,-1000,1000,573,410,233,-1000,-697,-119,285,801,236,556,-1000,-62,460,-235,-82,-893,91,1000,408,336,211,1000,-551,-1000,1000,1000,1000,-274,-1000,537,-1000,1000,660,121,-823,-606,-928,1000,-268,530,1000,-1000,597,1000,-434,226,-797,979,-241,1000,187,41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00577() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-783,-872,319,-503,1000,-832,-29,-1000,-578,-36,1000,731,-121,-871,-777,-697,-456,1000,467,-842,-115,1000,945,566,381,700,603,1000,-83,828,642,-410,187,-1000,-802,-889,70,-696,-1000,370,451,-1000,567,-971,-400,-89,-266,-21,-827,-1000,296,1000,550,339,1000,323,-708,-679,841,740,-381,1000,676,-583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00578() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{120,-485,-1000,952,67,1000,1000,1000,1000,-1000,-929,-955,-259,-360,-159,-631,-190,196,1000,-79,-13,-873,-460,-259,-1000,1000,-1000,-518,-982,-1000,-378,-320,1000,437,997,1000,-257,-603,-651,53,-711,-1000,72,-1000,-714,-1000,966,-413,1000,-534,-300,-10,230,1000,247,-1000,676,-1000,156,-692,644,-29,-703,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00579() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{70,-49,230,347,-53,-861,47,132,165,-227,-695,1000,1000,112,-382,-270,152,594,-22,-956,1000,520,971,-449,421,-950,549,-550,-475,537,541,611,1000,665,434,-435,1000,595,1000,870,-468,579,-1000,417,-1000,781,-1000,-758,-1000,325,-691,-535,1000,-653,1000,-247,-511,824,-534,690,-994,857,-749,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00580() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{290,624,-36,561,-1000,-381,-356,148,-394,290,803,-650,-480,1000,1000,920,1000,145,-1000,623,1000,111,1000,1000,200,167,-663,-362,432,-88,208,810,419,-230,785,640,-1000,413,897,-641,-50,-188,280,-167,-575,1000,943,-7,-682,-792,-862,-1000,904,614,-495,-10,1000,-681,421,728,353,-1000,752,726}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00581() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,-465,-253,1000,1000,-1000,694,753,-647,1000,-1000,-141,-808,718,495,-1000,-869,33,980,440,-117,-543,-707,-193,1000,831,97,267,-17,149,1000,-967,-370,130,-21,-1000,-31,1000,948,-560,-603,-652,240,1000,1000,209,361,-240,791,76,-868,1000,666,-1000,-206,1000,-593,-115,-739,1000,343,571,-1000,-525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00582() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,828,604,256,1000,-1000,-668,-1000,-1000,-382,1000,815,-986,312,34,-14,-449,1000,15,-860,593,-115,362,1000,1000,-1000,1000,435,1000,1000,262,734,-370,-773,-518,-1000,-1000,357,-1000,1000,-1000,-1000,1000,489,52,789,170,-1000,244,-1000,-205,678,440,-1000,1000,1000,-1000,-602,1000,1000,-1000,422,65,-967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00583() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-968,294,999,841,-233,-1000,-849,-800,-1000,1000,577,-531,-731,268,1000,-692,-477,839,696,1000,-234,799,-1000,735,1000,-309,-484,930,370,996,1000,227,-1000,-540,-65,-1000,-295,450,510,-768,-387,-592,1000,1000,1000,410,977,-91,457,186,-533,1000,874,-1000,-494,970,-492,378,-1000,1000,-921,766,-80,-975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00584() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{1000,662,-96,-718,-1000,1000,454,-1000,879,-1000,101,-143,1000,-792,-1000,-732,317,-988,782,596,-1000,-417,316,-988,-1000,619,-1000,-222,-123,-431,-1000,334,451,-224,232,470,278,-302,-694,-1000,109,-233,-972,-1000,-510,-1000,423,834,-641,417,1000,574,-94,1000,-936,116,1000,-765,368,682,469,-542,1000,-590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00585() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{422,-215,-251,-420,571,-1000,-250,-333,-597,390,-183,1000,215,968,1000,33,-39,718,-839,1000,742,571,-478,458,574,-235,1000,-1000,473,1000,-578,747,-142,436,30,-753,297,1000,731,-107,-1000,195,-691,1000,116,1000,-324,-1000,-1000,1000,51,6,1000,-1000,281,1000,-387,-269,80,397,123,-163,201,-696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00586() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-692,1,180,-442,-338,-691,-29,-1000,-411,-413,1000,720,-436,-871,-1000,-165,22,144,151,-842,587,459,380,498,482,333,956,-797,346,1000,-283,328,1000,-1000,-417,253,267,-585,-897,665,451,75,-550,-886,-1000,-39,-1000,-690,-514,-1000,517,55,550,420,907,323,-445,-1000,841,-214,-312,1000,212,-244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00587() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-97,-168,384,394,1000,802,-29,-1000,13,481,22,-197,-824,-604,-1000,-662,-223,-1000,1000,-1000,-1000,65,284,825,-705,102,449,236,664,-776,-1000,-714,-591,-322,-498,939,-180,-534,-807,159,235,288,325,-402,311,54,-496,1000,604,-1000,-556,213,-1000,-109,-597,-983,-428,-316,261,-1000,-7,570,-422,-583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00588() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-324,126,619,-180,-1000,619,474,78,-625,197,1000,-1000,1000,122,1000,648,894,866,4,1000,285,340,308,623,-164,-145,-16,101,-71,437,608,1000,-1000,-971,108,-912,-172,-1000,-221,-544,92,-1000,1000,-722,-307,644,1000,-592,474,-1000,-1000,343,114,916,-392,-738,906,-534,107,105,-873,113,-87,375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00589() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{126,-49,826,735,-539,85,681,76,433,896,-11,-376,-988,-31,-342,894,385,-602,732,-956,135,52,-823,-36,-264,-788,403,-184,239,-636,-495,119,956,351,815,815,708,-110,774,436,-199,539,-659,514,-777,591,-753,642,-175,109,-814,-757,196,-687,764,-974,-205,887,-534,-656,-458,412,-786,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00590() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{12,947,-37,485,545,-1000,268,-1000,-665,-308,-176,1000,1000,189,-866,-1000,-760,-193,-237,400,114,1000,189,206,460,-713,-227,-868,810,1000,97,704,686,-3,-1000,-1000,1000,331,-293,-18,-257,548,-1000,-660,-259,-637,-1000,-893,-1000,294,103,802,695,-357,726,1000,-1000,503,533,1000,-161,292,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00591() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-924,923,1000,-4,-1000,-802,-377,-1000,-231,1000,591,-512,-447,554,723,894,851,-761,608,131,917,1000,-915,1000,787,-1000,-382,371,1000,592,-981,1000,-111,-297,625,976,-97,1000,760,-542,-257,-429,282,423,-58,481,-753,-690,324,589,-884,-942,527,-384,-696,-10,-355,-88,-111,-430,-787,-35,-639,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00592() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{415,-187,529,746,-77,-566,-590,843,-567,-124,1000,-671,-698,610,-378,-351,-122,662,401,865,-86,-400,-15,291,-321,-364,-10,-230,446,295,508,387,438,-363,598,-400,100,874,52,-349,-607,-400,560,-560,572,380,366,-822,56,-408,-157,-113,-15,-262,27,4,-462,715,400,312,-127,-162,30,14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00593() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-759,1000,273,716,411,-816,1000,136,155,592,-1000,-121,-1000,-127,-74,366,-914,400,-163,1000,558,504,-144,-634,904,-937,533,-1000,-789,-1000,102,-246,-688,242,-755,580,-29,916,-572,160,520,-1000,-81,949,399,-524,-1000,-1000,26,-40,222,-611,115,-117,103,-234,586,563,742,1000,-573,708,-926,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00594() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{1000,-90,534,582,297,21,-1000,1000,413,176,1000,-565,378,-437,611,-1000,-1000,-1000,-1000,-1000,-941,205,1000,1000,-710,1000,-123,882,1000,1000,474,1000,379,466,157,-223,-126,447,43,-1000,-1000,1000,936,-395,1000,-957,1000,869,-1000,-477,-1000,-60,952,-324,-1000,-458,22,-1000,-1000,-1000,-1000,-4,789,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00595() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-1000,-889,1000,1000,-62,-511,-497,-547,-1000,-732,-1000,-453,-1000,-503,-458,507,-1000,606,-1000,400,920,1000,-994,-749,1000,-1000,452,-617,-123,-85,-879,137,1000,-501,-52,1000,745,1000,-849,1000,982,-1000,-108,-426,567,-723,-593,-296,1000,-1000,-157,367,172,-673,1000,456,-462,823,1000,899,460,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00596() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{1000,-1000,559,895,325,671,-1000,-406,-1000,-330,177,-131,161,-255,600,157,997,36,-399,-553,-498,-509,-69,-124,-2,-15,602,444,-528,745,841,639,69,-686,795,521,1000,894,691,-222,-162,-7,248,-1000,771,837,1000,1000,1000,-1000,-1000,414,659,527,-1000,561,-1000,-256,555,-361,926,-223,331,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00597() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-1000,-994,-291,568,-1000,-231,-862,862,-1000,-772,1000,-618,-1000,684,1000,-1000,-156,1000,670,200,-452,-706,-30,247,-148,1000,765,-1000,1000,1000,263,986,829,-936,845,-551,-92,1000,699,294,1000,431,-466,-1000,463,-391,801,-1000,213,-1000,-240,393,-198,-660,-278,137,-297,637,-167,-864,-130,-724,1000,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00598() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-462,543,-922,613,1000,1000,1000,-442,681,-343,449,-100,55,-1000,-929,594,-273,-1000,-437,1000,893,96,216,-453,664,409,885,-271,-196,-127,335,821,-744,-262,-307,336,256,-381,1000,-5,191,674,95,-25,631,-96,-700,-611,984,-364,-181,-15,1000,144,1000,71,398,1000,-353,253,724,-435,321,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00599() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{808,81,207,670,752,-125,-714,334,-739,-318,150,200,-684,206,-155,389,-131,400,966,883,-1000,-1000,-359,148,-116,-1000,352,-740,-866,-612,822,-336,-783,-905,845,-1000,520,964,76,-186,520,-1000,29,124,512,1000,-248,403,-358,-914,-61,-347,115,1000,507,101,-205,-332,1000,1000,-133,-1000,-172,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00600() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-1000,-165,320,805,-177,-205,-1000,129,-1000,-799,-534,269,379,-791,970,1000,796,-241,-489,675,-366,186,-408,958,524,276,232,623,-1000,-68,-1000,-243,-458,-297,1000,1000,-130,-473,-720,-417,898,-851,-1000,-305,464,1000,-520,-826,651,1000,-939,-388,255,805,877,456,602,52,101,733,758,-178,481,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00601() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-759,-1000,273,671,-94,69,-1000,1000,-857,-962,1000,-1000,-1000,1000,857,-1000,945,636,966,-1000,-1000,-1000,-104,1000,-1000,-937,533,-1000,1000,1000,1000,831,1000,-905,1000,-1000,-214,1000,676,-1000,520,842,-81,-1000,512,1000,1000,-1000,-358,-1000,-144,33,115,-269,-1000,-262,-1000,-1000,153,-783,-351,-827,933,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00602() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-1000,-165,875,896,341,-70,67,-102,-646,-555,-524,-435,-1000,-290,-331,488,-1000,480,-142,872,602,844,-977,-530,630,-1000,364,-714,-149,-267,-613,-149,942,-940,34,702,189,586,-89,1000,177,-1000,138,-23,352,-737,21,-710,1000,-1000,682,55,513,-1000,1000,125,0,1000,902,862,546,582,-862,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00603() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{953,705,-123,675,793,-386,92,259,-9,162,-1000,149,-565,-1000,570,-370,346,-1000,-760,-340,317,128,1000,-1000,-583,1000,282,-1000,-569,-424,-746,687,-79,432,-1000,1000,195,927,-814,-736,434,412,-121,599,580,-356,-1000,-695,-958,-1000,-453,-232,1000,-379,1000,-109,1000,-84,-444,180,-654,16,-891,511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00604() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{-1000,892,-302,567,-252,10,1000,-291,43,340,-951,692,-662,-1000,-298,549,-1000,53,-1000,1000,1000,1000,590,-1000,1000,-260,925,-516,-235,-522,-20,366,-756,146,-411,1000,188,-611,376,1000,1000,-11,-422,639,629,-1000,-1000,-1000,-52,-126,-302,193,687,-715,1000,294,1000,1000,-658,212,478,381,-147,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00605() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{1000,1000,-123,718,1000,-476,386,115,-126,467,-951,922,-474,-1000,-814,488,-100,480,-875,1000,1000,-155,677,-888,-525,291,32,-51,-1000,-1000,-365,-350,942,544,-692,1000,704,880,-119,16,211,-1000,52,-23,1000,-4,-1000,-284,238,-585,-499,-252,909,630,1000,266,1000,887,778,1000,110,582,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00606() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{1000,207,-1000,543,1000,1000,-623,213,993,-1000,1000,279,1000,-1000,-497,86,1000,-1000,-776,865,-897,-551,-15,683,80,1000,1000,757,-496,514,955,799,377,-363,474,241,-285,874,1000,-1000,-18,-400,560,-560,545,155,688,367,216,-738,-157,241,1000,1000,-23,46,2,-19,-1000,-1000,-127,-1000,966,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00607() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.CategoryAxis",
            new int[]{1000,-661,-142,806,135,1000,-1000,-63,-360,-772,-122,-468,520,-458,644,-681,1000,-1000,-1000,-1000,61,96,1000,-154,-140,1000,546,910,330,1000,938,1000,-104,-996,106,794,-80,56,977,-1000,839,421,584,342,1000,156,789,1000,429,-1000,-1000,304,972,204,-1000,237,-1000,-593,-864,343,257,174,862,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00608() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{-499,-244,-506,-663,290,-410,34,599,-454,823,141,264,-439,499,-176,331,-279,-213,-84,1000,-1000,1000,-90,901,-1000,146,565,1000,470,-195,466,-651,89,-572,1000,694,-754,-1000,-454,778,484,703,-379,338,231,-15,-702,195,745,-314,601,639,-582,-409,-553,248,-303,182,-733,-1000,199,523,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00609() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{-648,-563,713,82,-488,986,-487,-221,-1000,670,-224,-101,-643,931,-112,1000,463,-1000,-770,-1000,-567,777,-1000,-74,926,1000,396,-236,887,917,1000,286,368,-964,-738,-1000,-638,-29,249,39,1000,705,-1000,387,598,985,-46,-1000,582,916,-105,767,820,-967,-1000,-967,300,298,152,-1000,-468,148,-611,-905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00610() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{-315,460,114,35,-107,-1000,319,-125,-358,-114,1000,-641,308,-726,193,-623,-629,81,1000,-517,-1000,814,-563,297,1000,-954,618,-248,-717,-16,-1000,220,832,-1000,535,420,683,-146,220,829,1000,-367,-158,488,150,-1000,312,425,1000,-423,199,-53,154,317,149,-23,-1000,-713,-719,-1000,-310,-704,194,928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00611() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{-852,121,-14,112,117,-312,-142,874,960,184,1000,-946,-423,-856,627,-471,-124,519,656,358,-1000,1000,-501,933,275,-1000,555,562,-1000,-175,-892,-399,407,-1000,-200,1000,-568,-930,-130,1000,947,-222,43,878,-257,-1000,-790,487,1000,-423,1000,-95,-223,-80,189,850,-291,-345,-1000,-1000,413,-144,-580,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00612() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{-328,869,-52,-388,-13,-725,197,-82,997,-105,263,-674,599,-802,-606,-589,-840,403,914,-505,-621,4,391,105,306,-801,273,-735,-787,-174,-816,327,-191,-20,650,645,79,671,-394,629,-50,-414,-267,421,-56,-695,392,784,967,-827,738,-343,488,419,446,269,-492,-876,-599,710,-102,-884,718,423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00613() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{-136,-914,-466,-312,144,195,147,262,1000,337,-166,-483,61,1000,-1,-445,46,393,521,381,235,257,-807,640,-502,-411,5,209,202,-433,-504,147,45,400,-294,360,-676,-316,37,-212,182,-95,-261,-1000,752,-174,-308,699,306,-827,435,141,369,123,6,182,318,-547,-560,-72,340,-351,-97,-112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00614() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{-29,-230,-181,-915,-374,104,-650,-1000,-17,633,-291,-362,26,-203,-360,124,-960,1000,10,-3,-437,-330,-79,-122,-607,-652,-598,617,629,-261,-274,640,279,-419,-526,-274,-766,-579,-428,-521,-502,442,-170,767,592,-352,-1000,950,833,636,-566,930,-124,597,-832,-342,-130,386,-508,890,-123,-267,106,524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00615() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{-1000,-547,-175,-335,378,242,283,495,932,481,-190,-491,-965,299,-868,365,278,-975,-1000,-482,289,269,418,314,194,1000,-221,-726,1000,-533,1000,-742,-989,109,299,-720,-269,290,-120,-868,708,590,-1000,-344,1000,813,892,-464,-227,294,404,-157,-86,-265,30,-649,662,88,-184,-854,190,1000,-364,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00616() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{-1000,-581,-252,-777,-538,-473,-743,176,398,1000,824,-542,139,302,114,-752,-288,-18,-920,-1000,-1000,958,-931,-734,-699,-928,-947,-1000,953,-514,-60,111,831,-1000,-647,-731,-890,-434,-97,452,1000,1000,-79,986,1000,536,70,-399,1000,-532,1000,300,991,-323,1000,-847,-199,-692,-477,-1000,-110,-645,-626,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00617() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{250,730,214,-251,191,14,605,-1000,-1000,-321,-1000,632,-485,1000,-1000,32,-1000,-1000,135,-703,1000,784,-1000,921,874,1000,755,-354,1000,598,1000,394,1000,1000,1000,-842,5,991,224,-344,1000,316,-825,-920,1000,1000,1000,-1000,-357,-716,-1000,-553,579,-1000,321,-1000,646,428,1000,-347,-816,145,352,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00618() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{-16,688,-206,-293,-13,396,59,-506,-144,160,-947,-145,131,871,-928,122,-582,403,-501,-226,652,-122,-164,871,598,529,273,51,85,-656,1000,-29,293,112,650,-578,-596,54,-5,629,473,264,-195,-292,17,368,848,-653,376,-66,-300,-1000,-38,-907,-149,-526,218,-66,251,-254,228,-294,792,-340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00619() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{482,-296,-481,-1000,730,-1000,606,-881,562,883,512,1000,924,577,-1000,-757,-1000,1000,-436,1000,442,1000,-1000,713,-1000,-938,-436,1000,1000,-385,980,-1000,1000,774,1000,192,-511,-760,-47,-261,690,279,-180,-360,420,406,1000,473,-173,-845,-1000,-16,-1000,1000,-248,-926,-302,-722,-225,-1000,-653,-56,919,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00620() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{-854,491,-67,155,46,-972,332,26,-1000,297,1000,-1000,-214,-192,-52,-566,-16,-443,718,914,-1000,870,-238,400,1000,-337,65,-654,-668,-9,-122,-161,156,-1000,-36,195,548,-71,-104,994,1000,-206,-715,-1000,364,-403,757,13,1000,-462,376,478,249,-640,56,-127,-626,-1000,-869,-688,180,934,163,679}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00621() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{241,400,169,-544,-113,242,204,-125,1000,-515,171,805,874,299,-336,-383,-1000,1000,1000,996,239,269,-65,20,-1000,-1000,914,-153,-321,-291,-1000,551,536,109,299,556,167,450,-14,-55,-740,-343,-188,-468,-44,-1000,-93,999,-286,-435,385,-1000,179,1000,454,142,662,51,-261,397,-715,-596,438,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00622() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{354,1000,-956,-464,493,-585,1000,-1000,-841,6,-236,-797,1000,141,-908,-878,-567,-557,1000,-691,336,-410,966,476,876,185,448,-1000,1000,492,-5,592,-722,1000,521,-502,-408,1000,295,899,1000,-460,-958,415,98,590,1000,803,814,-1000,802,-40,1000,-220,612,-11,157,-463,661,-1000,38,-1000,1000,-651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00623() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisIndex(org.jfree.chart.axis.CategoryAxis):int",
            new int[]{613,271,-854,1000,685,1000,957,573,-1000,-666,349,-1000,-1000,-877,-708,-324,1000,-1000,799,406,460,-638,864,954,-478,-1000,197,-382,-881,-261,698,-641,-1000,458,520,709,1000,-138,-197,-319,-1000,-1000,206,-514,-1000,-1000,-615,-74,-1000,146,-1000,571,-1000,833,-514,1000,-210,30,-1000,1000,55,979,427,710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00624() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{671,-647,577,-368,-314,1000,676,-787,-968,394,357,71,-39,-522,281,-772,231,-408,-100,-131,-400,531,-280,-855,-387,1000,-526,-142,-383,126,236,103,16,509,-302,-797,473,264,260,226,-552,-618,242,-98,438,-205,-425,-336,-572,-400,558,-831,928,400,602,388,-104,-72,162,56,234,172,-9,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00625() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-221,-258,-731,-21,246,-687,-559,175,739,1000,263,750,-587,574,-526,-668,-764,450,-447,1000,518,-803,736,531,-571,-233,-110,561,13,277,137,218,1000,-361,-352,-609,1000,700,-208,-1000,526,763,-149,752,-617,-77,64,-456,-540,1000,-444,739,288,-373,-414,-588,664,342,561,-913,42,-868,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00626() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{483,-868,-591,-213,527,-694,1000,-424,578,1000,744,-9,-1000,1000,223,301,-1000,-232,-1000,1000,690,-286,406,1000,-99,233,-1000,-533,506,1000,530,-196,-189,397,-1000,-555,897,-27,1000,77,1000,391,419,-499,-458,-1000,803,-1000,-976,457,-273,589,836,-650,346,-486,272,548,753,249,-1000,-719,1000,540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00627() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-280,1000,-441,-41,-1000,26,508,-15,-1000,-186,414,-140,1000,458,-461,399,-818,1000,1000,537,624,323,-132,-429,-572,-1000,667,-150,1000,789,925,-676,1000,-1000,111,-52,113,-311,-148,-1000,759,1000,-614,464,-413,963,-1000,1000,-157,-1000,1000,-36,-302,-723,575,904,675,1000,765,771,1000,529,906,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00628() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{588,-1000,549,-144,-76,1000,309,-1000,-443,-513,673,-458,-824,-1000,-317,-453,543,-1000,-183,-341,-38,268,-271,-1000,-803,355,-441,-210,-179,1000,157,756,-700,1000,-1000,-756,689,774,364,-150,-372,-1000,-310,-414,1000,-68,-274,-766,-931,457,163,-1000,587,-650,-194,163,-548,-497,674,168,-996,-719,-263,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00629() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{291,-1000,-691,413,1000,969,-1000,-11,304,-395,1000,583,-672,-682,-348,-1000,396,-530,-160,-1000,-491,-851,245,-526,-565,-1000,601,561,-1000,-233,-1000,-315,1000,1000,947,254,-265,572,1000,-224,-1000,-360,-113,635,555,257,295,-1000,-1000,-757,-1000,-1000,580,-1000,-1000,-1000,-926,454,1000,-834,-494,-1000,6,-261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00630() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{671,-911,434,-292,890,221,704,-346,207,322,921,147,-171,-453,-562,-533,-608,-735,-42,-92,168,-1000,-495,883,-45,788,-610,464,993,679,-131,213,339,804,-1000,407,-298,708,1000,1000,763,133,608,-58,-585,-1000,1000,-688,-1000,-379,212,-1000,-394,-984,-941,-357,-66,1000,958,1000,-346,-617,304,-905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00631() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-195,-522,-451,-353,-9,321,-359,2,-225,854,575,964,559,-1000,235,-192,699,-707,784,-577,-381,1000,985,-680,-133,-138,577,183,-756,684,-1000,-770,1000,560,949,-881,-241,213,388,333,700,35,468,165,522,216,1000,118,-430,-1000,-899,-997,653,-443,-344,-482,19,352,226,-744,1000,107,42,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00632() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{127,1000,538,391,-801,-404,446,-973,70,422,-555,733,1000,754,1000,95,-230,1000,-478,267,-272,1000,-140,-619,250,1000,293,-64,1000,1000,1000,-759,-628,-1000,464,-870,107,-624,-1000,-290,1000,1000,441,561,-1000,432,-25,847,336,-886,1000,625,476,1000,1000,1000,1000,202,-379,1000,1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00633() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{71,-1000,-311,-456,406,400,546,-906,151,608,1000,-907,-1000,733,-941,-435,-238,-1000,-997,573,1000,-369,-1000,261,-799,753,-1000,-1000,313,649,157,1000,191,1000,386,-175,814,1000,1000,-636,445,-389,-197,-641,274,-666,-52,-1000,-1000,1000,-819,-811,-316,-1000,-923,-486,-337,596,753,24,-1000,-1000,485,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00634() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{820,-531,-802,-202,1000,-465,1000,118,670,830,559,978,490,-842,-442,-325,-1000,-62,-598,376,-1000,1000,-1000,1000,-274,-181,-508,1000,1000,924,605,220,680,-299,-870,623,492,-762,764,886,1000,1000,480,610,-1000,-1000,1000,-258,62,-430,1000,-1000,-355,429,-1000,112,908,1000,797,1000,104,-343,806,633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00635() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,-408,-731,433,1000,-1000,924,55,51,-506,1000,-368,-20,139,-870,788,-359,-869,1000,15,70,-1000,-982,1000,103,-651,455,1000,9,498,-804,643,588,503,-102,225,-63,735,1000,569,-1000,-244,146,672,-371,-1000,488,1000,-1000,-579,731,316,267,-1000,-795,-1000,-1000,382,1000,1000,-213,-930,-66,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00636() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{898,-1000,-626,-272,693,1000,646,-806,163,167,635,-173,-1000,-13,-24,-194,-481,-574,-228,-155,376,20,331,130,-290,761,-549,-319,763,800,18,314,-892,874,-1000,-310,167,677,1000,363,420,-204,425,-293,959,-435,-228,-928,-965,225,419,-689,-292,-500,-568,-265,-211,355,647,591,-1000,-599,411,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00637() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-131,907,-993,-248,572,-900,267,481,85,502,154,557,694,-186,-621,248,-587,569,619,215,-539,-553,-280,672,-120,33,839,1000,1000,371,-471,-384,902,-623,329,312,209,301,369,55,469,1000,751,375,-702,-998,-1000,61,-801,-801,767,400,-843,-146,-18,-832,129,751,776,932,780,-414,716,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00638() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{623,1000,-211,-53,-276,-201,145,-733,209,1000,345,431,473,1000,335,-411,-1000,1000,-1000,210,227,406,1000,-503,565,1000,79,-988,1000,771,-468,-631,-811,-1000,78,296,-864,33,-757,199,1000,1000,1000,173,-613,-114,-1000,277,-3,-456,423,-223,-49,315,805,580,983,955,-261,-427,1000,-487,1000,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00639() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,-242,914,-133,-1000,-1000,-736,961,-1000,-474,249,-174,1000,-900,348,254,1000,-906,-160,-1000,-168,18,-1000,-838,-484,-1000,-3,5,-408,-1000,-1000,-877,475,499,1000,1000,-592,-1000,503,-897,-542,-618,-369,-764,-711,941,1000,101,412,-993,207,720,-320,99,1000,-849,99,1000,-50,259,1000,60,897,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00640() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-620,-225,617,-1000,770,87,-136,745,-329,891,-196,-684,1000,109,318,950,25,-130,99,732,662,795,-143,-857,456,-559,-1000,-1000,-658,393,136,1000,219,-1000,286,981,-579,1000,-57,-268,-371,-471,355,-236,-814,472,183,380,-95,722,186,323,869,-1000,1000,465,619,129,-314,-74,950,-493,-1000,-578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00641() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{36,-255,-283,-972,1000,-786,-248,751,202,1000,522,-801,-1000,1000,943,-880,-324,-727,-348,-234,-312,781,141,-957,-510,691,-996,694,-554,-916,-879,302,1000,479,-758,-1000,1000,-562,471,-476,1000,-654,1000,980,-1000,1000,-1000,1000,-1000,-4,1000,907,-26,-1000,7,1000,-1000,217,-950,-1000,-674,716,-1000,-297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00642() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,-1000,229,-1000,-904,-21,-283,738,-1000,1000,525,-1000,-1000,354,1000,-236,272,745,133,1000,538,-626,425,-1000,-962,541,-1000,318,-847,-730,-807,36,281,-1000,-574,781,-291,764,-167,-1000,-439,-1000,149,-177,-1000,257,-945,-1000,-445,1000,903,975,653,87,1000,735,-622,351,-110,-673,-306,264,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00643() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-650,-255,102,-1000,-1000,-235,-585,709,-406,1000,698,-801,-838,821,1000,-1000,-309,-413,-48,-234,-90,1000,-493,-1000,978,922,-955,831,-628,-773,539,162,768,524,-759,-1000,1000,-746,471,-959,628,-681,1000,728,814,1000,-1000,-557,-1000,42,1000,1000,61,-1000,7,1000,-1000,-74,-790,-1000,-524,561,-1000,-968}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00644() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{703,-1000,899,539,764,-648,297,598,-302,975,-795,-1000,170,808,313,-545,-1000,-1000,-897,881,707,-583,1000,360,-811,-989,-511,-466,-142,-930,535,-370,14,141,364,-628,-1000,-306,66,411,1000,-477,-744,427,-612,-635,125,1000,-208,432,-158,1000,347,-435,-142,354,-853,147,-1000,1000,1000,-675,-1000,853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00645() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{314,67,-434,29,-1000,-514,624,-1000,336,973,-1000,-761,-633,-585,-849,1000,964,-221,1000,107,723,-482,667,771,1000,-1000,287,-1000,-124,-989,1000,-383,-420,-1000,955,121,-1000,916,479,-209,-157,396,-1000,-919,447,-1000,1000,-1000,1000,-814,188,-770,-349,1000,677,587,-182,126,755,-3,409,-1000,177,474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00646() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,-523,292,-456,-12,84,159,-506,-752,451,-421,-416,-682,903,187,-129,-443,280,-259,100,296,445,105,-121,446,-439,-245,-373,-242,-266,-444,288,141,-643,236,-184,-983,113,205,626,364,-16,142,87,-380,-64,-204,147,13,404,189,593,0,1000,-34,300,-723,-93,-210,202,573,168,-381,616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00647() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-515,-1000,-636,-955,-1000,341,1000,-1000,536,668,-1000,161,-143,-804,-1000,1000,1000,1000,1000,-1000,915,-200,872,1000,1000,551,501,-1000,-775,887,-413,-373,715,-1000,1000,-76,-1000,1000,703,-23,190,1000,-1000,-1000,917,-1000,-221,-1000,1000,-270,-356,-1000,-325,1000,1000,688,-169,-1000,1000,298,617,-817,204,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00648() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,208,249,-385,-625,191,718,-86,160,35,884,569,400,-400,563,-1000,129,339,351,-841,572,991,309,341,-666,941,31,-466,-180,1000,-1000,-42,-583,441,-56,-64,-616,314,197,341,-136,-641,930,890,368,-158,-1000,-897,-413,938,41,673,25,851,-168,572,449,-77,508,239,1000,1000,42,-726}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00649() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{540,-1000,-11,-873,496,-506,-811,-42,-128,1000,-692,-1000,-1000,1000,182,318,-827,-1000,-547,108,459,-750,1000,-589,161,-1000,-1000,-466,-475,-867,535,691,1000,-930,756,-1000,-1000,-259,-712,103,784,-1000,930,-471,-1000,842,-496,1000,991,-130,1000,263,177,-545,732,1000,-1000,-131,-9,-459,-694,112,-1000,582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00650() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-756,-407,-1000,-1000,-494,-477,-276,560,-302,-369,266,452,-1000,618,-187,846,1000,-41,1000,-1000,-1000,-1000,1000,-612,1000,68,-692,8,-462,418,125,166,809,-1000,903,-1000,1000,1000,-841,550,-382,759,1000,-173,199,1000,-278,-337,1000,-1000,931,529,720,-1000,1000,931,-1000,-1000,334,-444,-1000,678,-1000,-584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00651() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,-103,-326,-1000,-969,-60,679,-1000,-773,490,-488,-101,-528,-789,58,416,993,51,985,808,294,202,577,-85,-696,275,-1000,-417,-49,623,-158,450,-114,-424,643,1000,-744,1000,458,-499,-382,64,460,-488,-40,369,-1000,-549,613,641,527,587,-746,1000,651,664,-830,-279,97,-232,466,247,116,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00652() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{1000,1000,-642,370,-1000,-129,768,-1000,1000,-582,-1000,715,-1000,-1000,305,-323,1000,-180,823,-429,132,1000,555,1000,745,-579,-218,-882,909,-796,-331,194,627,-486,-107,-225,-924,-278,1000,303,612,1000,-1000,-626,1000,-1000,1000,-1000,1000,-112,-475,-1000,-843,610,-915,188,1000,296,778,1000,1000,-248,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00653() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{420,-556,724,-281,422,-780,-54,-544,1000,1000,-1000,-632,1000,-820,355,-341,1000,1000,-504,-1000,1000,1000,846,-1000,-657,1000,1000,-1000,-878,-416,-1000,541,79,-863,-683,-373,-854,-1000,-87,112,-655,238,-1000,1000,-975,-327,-564,-231,-805,1000,66,-490,-784,446,-1000,1000,1000,-1000,-260,-118,-252,-212,610,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00654() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{371,185,553,-867,-389,-84,282,394,-48,54,-365,48,315,159,99,-558,-1000,-1000,-864,775,374,996,396,-106,-435,-863,108,-792,272,309,-51,467,-227,600,110,-123,-1000,144,-712,609,-127,-431,930,660,-148,-522,-61,635,-79,848,-2,1000,-500,986,786,572,227,292,-190,1000,-765,33,-295,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00655() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{580,-1000,-876,260,-904,-1000,18,-744,460,793,-1000,-1000,-1000,1000,-1000,1000,-827,-1000,8,1000,103,-917,-31,754,1000,-1000,-1000,-1000,-124,-1000,118,469,1000,-1000,1000,766,-1000,700,-167,834,549,-943,967,800,-275,641,492,1000,-771,-1000,946,433,-883,87,1000,937,-1000,-755,-336,-715,-306,-583,-1000,689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00656() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{767,271,-308,-616,-1000,-695,-703,-1000,272,-902,-1000,-781,107,-195,954,1000,1000,1000,-235,-124,258,164,127,-7,928,609,609,-868,-22,418,445,923,389,-381,1000,-287,-226,-109,-400,-535,-1000,208,454,-551,-324,80,-760,600,778,-1000,144,562,-1000,700,440,978,686,-364,-848,-781,254,-1000,-216,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00657() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{1000,480,-891,-877,-210,55,-575,-1000,1000,-1000,-662,-1000,-63,-860,909,-1000,-477,387,-251,110,-480,-1000,1000,-99,129,-125,1000,-154,449,1000,-698,822,394,1000,1000,1000,510,604,1000,-1000,45,-1000,667,-1000,-1000,-365,-517,1000,684,1000,-43,-492,-646,1000,402,-165,954,403,-672,-1000,256,289,-1000,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00658() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{873,414,-236,-625,-401,-150,-292,-984,890,-1000,-1000,898,91,-803,848,-498,17,1000,-780,-319,-745,-1000,842,-372,-488,291,104,79,1000,754,-685,778,352,1000,589,1000,765,780,1000,-1000,-290,1000,-534,-631,-444,-1000,-306,1000,320,433,84,-125,-510,502,69,-301,1000,534,-150,-137,849,288,-1000,297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00659() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-41,740,-791,-685,-107,48,77,-984,600,534,-166,788,91,800,-18,122,519,1000,-718,92,-440,-501,-172,140,-1000,293,-677,237,689,557,-60,778,75,322,-274,336,1000,575,172,-82,-1000,686,676,509,-444,-1000,442,191,-1000,433,-188,-12,701,609,69,-49,1000,1000,720,977,827,-540,572,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00660() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{113,-265,441,517,-1000,-137,-701,-164,708,273,-471,-497,-762,-199,-573,1000,524,802,1000,-188,799,331,422,852,-915,-748,1000,-1000,789,867,-153,-444,1000,130,-1000,-349,342,-1000,-418,-395,1000,1000,-189,93,536,1000,-286,-298,1000,1000,-417,-376,13,72,1000,76,-338,476,-715,-158,1000,721,-400,-585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00661() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{68,599,546,-731,-654,166,-56,-771,846,-971,633,-754,-285,-27,5,811,-91,299,-474,-704,-758,-708,486,-706,-221,364,-96,-471,685,769,985,81,936,872,541,787,-398,609,920,-619,-163,-984,160,-815,-872,-530,-698,979,905,628,113,50,-823,978,-163,-649,505,-631,-14,-757,325,919,-293,-709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00662() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-458,-905,645,-52,290,-759,357,-986,-46,-97,895,-36,862,-554,736,-869,-193,-644,513,665,-414,-811,368,175,234,143,-58,-626,-935,870,861,698,-134,-765,596,-27,947,375,721,-90,-872,657,-264,-526,205,879,871,-49,-50,457,597,-56,-719,-814,-91,455,896,694,-114,-273,331,-305,-491,-445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00663() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-349,-1000,-236,759,991,549,969,-402,-718,1000,-139,898,1000,-587,219,-1000,-154,-1000,1000,769,691,-5,-224,68,85,-373,654,345,-1000,532,53,573,-316,-1000,-21,-621,1000,-339,156,-335,-290,1000,-534,326,727,817,911,-1000,-565,397,1000,-125,-87,-904,69,1000,1000,840,-150,38,567,-886,108,297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00664() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{627,162,-966,-1000,-860,11,385,-447,-130,1000,1000,1000,-68,339,-398,-1000,-464,-957,-133,675,-954,-730,-1000,-598,1000,-1000,-1000,973,-1000,107,788,9,-1000,-107,-1000,70,-12,925,-417,631,-912,549,857,-58,-123,-1000,250,1000,-322,-404,583,-10,-1,497,-173,330,892,-262,127,754,1000,-638,899,378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00665() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{836,561,-865,-1000,-1000,-132,323,-141,-318,827,1000,52,489,-625,-919,-1000,530,-603,-386,-439,-1000,-1000,-9,-1000,-222,-1000,246,534,128,347,1000,-985,-500,16,-825,144,783,1000,57,100,-775,-1000,1000,120,420,-713,-92,1000,-618,-691,1000,-573,-140,695,-123,716,30,341,18,917,1000,385,167,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00666() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{99,183,-134,-265,167,-54,154,-134,-1000,726,159,1000,-717,168,-1000,-651,228,-400,62,1000,-907,-331,-378,-1000,15,-1000,-1000,-376,-411,38,1000,-1000,-1000,-371,-921,-635,291,698,-622,400,243,274,1000,-156,506,225,-318,172,361,400,1000,200,-114,-255,-55,906,-1000,381,939,-189,-25,817,400,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00667() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{663,-1000,-901,-622,-1000,-56,659,-54,-585,-74,227,178,-281,32,-188,-138,-296,205,-308,-274,-938,-44,-632,-1000,833,-404,-1000,1000,-1000,-112,1000,-1000,-587,-694,-108,-556,-645,711,-317,322,-966,-1000,971,-176,-7,-802,-560,384,995,-1000,1000,359,-372,628,-497,1000,170,-1000,-55,775,362,-1000,384,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00668() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-168,36,573,-105,-606,-54,506,217,469,400,-405,1000,-151,-533,-739,-756,201,278,-1000,-793,-733,-314,246,-1000,-1000,-1000,-542,-201,280,609,-225,-136,471,149,-1000,374,519,490,700,9,-94,1000,833,-79,1000,445,-803,-183,933,-910,486,-467,-391,329,-171,-112,15,182,593,-53,363,551,-505,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00669() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-553,-12,434,-877,-306,1000,1000,1000,-110,1000,-662,788,579,1000,-1000,-1000,-844,-1000,-472,956,-805,-331,-926,-446,15,-61,1000,-154,-940,222,633,-1000,-636,-748,-274,-89,291,494,78,1000,-747,-684,950,675,-1000,-634,467,-639,-935,-407,-5,-249,796,-10,-230,-81,390,370,1000,1000,210,101,1000,2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00670() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{345,404,-98,-324,-1000,-864,-1000,-219,-20,1000,1000,954,-72,-977,-1000,1000,1000,398,-52,-1000,-275,-1000,439,-60,-1000,-1000,920,-1000,1000,744,1000,-1000,1000,150,-724,-438,1000,275,-805,-281,1000,1000,1000,669,1000,1000,-38,668,370,346,1000,430,71,1000,998,832,-1000,454,-899,-1000,74,1000,-191,29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00671() {
        org.junit.Assert.assertEquals("COLOR:-10190312", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{929,1000,-971,-1000,-343,-118,-764,-924,898,-1000,1000,-1000,3,-654,1000,-7,-419,-426,-52,-250,-275,-1000,687,192,-28,45,920,-321,1000,1000,596,584,334,1000,1000,951,236,772,562,-774,-200,-1000,310,-1000,-1000,-316,14,1000,370,1000,-76,760,-382,1000,388,49,1000,454,-1000,-673,486,-80,-1000,-472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00672() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{-964,-269,-41,1000,841,1000,1000,1000,458,-11,-1000,778,-716,923,216,739,-349,-686,-857,20,-911,771,-576,81,825,23,-1000,967,342,-166,-1000,43,785,-903,162,872,707,-246,907,676,521,539,-699,440,906,446,-299,286,755,-53,-815,21,-312,-11,-946,694,828,834,-402,184,-1000,-1000,-987,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00673() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{-425,440,-431,-888,506,590,319,1000,1000,926,290,-110,-49,1000,-1000,-836,-781,431,-174,-561,-1000,1000,-1000,337,1000,664,-109,-47,-1000,-1000,-206,194,-716,205,-311,1000,399,-1000,799,876,-1000,1000,-1000,1000,792,-193,-495,258,6,-926,-1000,-144,809,568,-264,400,1000,146,205,-358,-1000,-48,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00674() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{-425,1000,119,-291,825,1000,-97,925,1000,1000,691,-784,555,1000,-1000,-1000,-1000,711,-237,603,-1000,1000,-1000,660,1000,775,-109,-112,-1000,-1000,-206,14,-1000,234,-666,896,-176,-1000,-170,899,-788,-326,-1000,1000,494,-193,-1000,-266,-124,-828,-313,-424,1000,1000,50,-1000,1000,409,-92,761,-1000,1000,593,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00675() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{-1000,400,254,730,218,-131,-722,-579,-824,-7,-43,-1000,1000,-143,-41,166,30,302,-625,775,738,723,1000,675,-562,933,-1000,159,358,502,-1000,-1000,-278,-960,-795,349,-456,917,-1000,-968,807,-303,-219,-118,573,1000,-322,251,-543,693,1000,327,335,775,1000,-1000,-686,1000,-1000,-522,779,-717,-116,-3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00676() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{-736,291,1000,344,998,1000,807,635,594,-31,524,829,-370,781,299,179,-237,-67,-1000,7,-117,158,-676,243,624,-469,-283,564,492,-113,-1000,627,738,-249,-456,-74,-21,-886,148,872,-31,745,-129,220,209,127,-487,-590,384,-533,-946,-321,-399,117,-658,-316,745,216,-530,435,-580,-824,-1000,922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00677() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{132,249,633,-347,541,-1000,73,321,-363,-347,424,755,1000,-143,-722,-769,-773,-213,-1000,1000,146,1000,1000,-84,272,-239,-626,-29,-1000,-572,474,424,533,-254,85,-979,157,-806,-8,-968,-1000,-78,610,-118,-225,-705,-322,251,-642,693,-467,-90,48,-456,-363,400,277,548,-928,-138,152,445,-437,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00678() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{597,412,-422,54,187,-1000,713,514,594,273,-68,78,1000,490,-476,407,-214,249,-824,771,-911,-51,786,913,800,-689,-1000,-302,-54,808,-159,-15,-409,-837,-984,-107,-1000,1000,-28,-832,693,8,539,-293,295,546,374,-480,-539,885,19,377,183,713,405,-580,266,-359,-1000,1000,569,-946,-1000,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00679() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{-220,-1000,119,-1000,-430,-472,99,118,1000,639,-445,850,-189,-42,-693,583,734,-98,-477,-453,-1000,697,561,-550,795,180,-878,-460,-1000,-1000,-444,85,-1000,-57,-666,293,-204,-20,1000,-997,-1000,1000,703,-369,532,777,1000,844,-611,-1000,-1000,1000,-675,-509,-50,1000,1000,-1000,-236,-296,1000,-885,755,746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00680() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{-1000,-1000,124,-358,-1000,-716,-1000,-1000,-138,-1000,-1000,-44,137,-1000,1000,1000,1000,-1000,-920,-623,286,1000,1000,-637,-686,689,-283,634,84,-166,-1000,-1000,-1000,-710,581,354,1000,995,-513,-1000,-779,-223,661,223,568,1000,1000,1000,-1000,82,605,1000,-876,-1000,717,-316,-1000,1000,-653,457,1000,-1000,1000,-109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00681() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{193,517,119,-561,-343,-186,-516,934,-116,322,-376,-665,884,1000,-510,-1000,-342,58,772,336,-782,1000,-1000,-209,351,1000,502,61,-1000,-1000,202,275,-933,1000,352,1000,539,-45,427,484,-1000,-1000,-367,599,1000,-981,41,334,-350,-921,-351,-174,996,685,-12,-294,-221,-413,209,-243,-1000,1000,1000,152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00682() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{122,1000,-351,-664,1000,-764,434,1000,1000,1000,1000,-84,1000,1000,-1000,-1000,-1000,1000,-419,1000,-1000,1000,-1000,646,1000,522,153,-405,-1000,-1000,531,748,-67,89,-577,640,-518,-1000,438,990,-1000,513,-1000,1000,543,-1000,-1000,-642,-143,-1000,-1000,-1000,1000,1000,-227,-1000,1000,326,-54,1000,-1000,1000,-242,964}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00683() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{-1000,-400,-371,-115,139,-6,110,544,1000,32,-1000,179,1000,271,-809,400,-23,470,-968,-1000,-1000,1000,1000,400,1000,1000,-673,52,115,-1000,-1000,-980,543,-485,-288,1000,1000,-518,-1000,-1000,407,409,-1000,1000,1000,1000,348,1000,-438,1000,-821,987,866,167,1000,1000,724,1000,-1000,697,400,824,638,858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00684() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{-736,580,629,-300,165,1000,1000,792,120,531,-263,1000,-1000,-25,33,-219,-637,-253,389,-117,-903,569,-137,-918,-851,92,273,280,27,-334,-224,962,738,386,266,299,-140,-18,1000,511,-285,493,-271,-293,588,-225,-34,-16,800,-399,-1000,428,257,-1000,-843,-537,-98,-1000,-502,87,-580,388,-998,305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00685() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{-930,-684,629,-127,28,-388,-442,-721,-893,-791,-959,340,-1000,-962,1000,1000,1000,-1000,-765,1000,411,-375,785,-420,-851,92,-444,597,-205,-773,-558,-305,-1000,-124,368,-615,246,107,-667,70,-538,-238,836,-233,46,228,702,541,-624,-399,126,893,-798,-748,-360,-458,-932,680,-987,-797,718,-987,393,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00686() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{-152,860,-516,-1000,1000,-1000,540,-1000,-940,1000,1000,-110,-49,1000,-1000,-1000,1000,678,-436,-404,-1000,749,-1000,565,1000,598,-1000,-32,-1000,-1000,-487,479,-1000,586,-455,1000,851,-1000,-1000,854,-1000,-532,-1000,1000,753,-855,-970,-213,92,-1000,-1000,-1000,1000,726,-826,508,1000,-497,744,708,-846,860,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00687() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAnchor", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlinePosition():org.jfree.chart.axis.CategoryAnchor",
            new int[]{1000,1000,-793,-281,48,-1000,-173,598,413,1000,691,-1000,1000,1000,-1000,-1000,-822,812,-237,967,-572,513,239,1000,1000,1000,-1000,-762,-598,804,355,-514,369,-626,-1000,421,78,-536,-61,-691,391,-45,-68,133,554,341,-1000,-338,70,473,790,-671,1000,1000,909,-1000,187,-53,-647,631,106,697,-923,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00688() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-1000,405,78,879,436,-644,-936,-412,-1000,-1000,-1000,-948,-815,-313,-427,-1000,-65,-188,371,339,394,-31,854,-274,848,-626,-823,457,1000,317,35,1000,-759,-1000,1000,-1000,-72,843,534,188,-264,590,-216,201,-357,-115,-1000,1000,699,-48,1000,562,-1000,-669,264,-1000,-1000,-1000,-129,220,-822,637,-88,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00689() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{147,400,-412,141,-1000,-460,-601,0,-1000,555,289,925,855,1000,-372,1000,-1000,1000,51,1000,-1000,-396,513,-883,-1000,749,-1000,1000,-843,-737,1000,1000,1000,919,-396,-444,-135,1000,270,-988,1000,1000,429,268,984,1000,1000,-1000,1000,556,-1000,1000,586,-216,-559,148,50,800,1000,968,273,-1000,-278,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00690() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{118,28,-421,652,-54,574,-657,-1000,-415,466,-214,1000,-101,-709,-1000,-488,-550,382,1000,-1000,1000,-695,240,-542,946,205,1000,211,-206,455,-272,269,881,197,278,-34,287,-282,740,-47,-1000,330,1000,-725,-1000,309,-239,-1000,-488,682,197,286,-68,635,143,1000,-944,-378,205,219,-1000,809,-148,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00691() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-309,-450,-161,1000,582,-215,-542,-378,-1000,0,-729,1000,-110,-876,-224,615,-1000,-697,1000,55,992,-478,304,-154,1000,587,-747,224,1000,-31,678,1000,-333,659,-176,-1000,-1000,776,738,708,-806,1000,195,-549,42,-592,-9,-132,570,-800,-400,375,-733,257,-529,-1000,-1000,276,527,-147,87,-497,494,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00692() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-798,-55,262,72,-929,-1000,-688,-702,522,-1000,-1000,96,1000,788,-1000,1000,403,890,561,-834,-736,326,751,-827,1000,-297,-1000,1000,456,142,-514,500,593,36,-635,9,1000,859,632,-459,1000,1000,-1000,1000,-231,419,997,363,-142,1000,-580,-894,-145,-204,118,-1000,775,867,999,486,860,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00693() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-323,-400,-126,95,26,24,251,-379,-88,-726,374,-487,-543,421,-1000,-375,1000,-506,-826,-19,-1000,915,767,-86,-1000,-663,925,-196,223,533,1000,-379,-617,-796,610,1000,-496,28,-469,18,734,-22,-846,969,-436,-71,-82,-466,694,-136,-718,154,-414,-1000,-620,-362,79,1000,-650,-367,-141,384,661,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00694() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-798,338,89,1000,194,-18,-170,-24,-57,192,-811,644,-294,113,-1000,-692,-1000,393,561,1000,673,-111,494,-343,864,-297,102,710,1000,142,688,1000,-348,403,38,-501,-1000,1000,269,286,-832,724,298,-601,-231,305,-545,3,90,42,-400,1000,-262,161,-1000,-1000,-850,-378,144,416,-620,-101,405,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00695() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-1000,1000,144,1000,103,1000,-877,319,180,283,-1000,567,1000,379,-1000,37,-1000,1000,1000,906,1000,-116,646,-926,1000,453,-1000,1000,1000,-1000,76,1000,1000,1000,-1000,-1000,-400,1000,1000,632,-678,1000,70,-725,1000,410,-1000,1000,1000,780,129,702,-106,741,-879,-1000,-1000,-648,-374,1000,-376,-1000,-764,-765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00696() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{35,-1000,-421,367,-629,-84,318,-703,-13,555,134,333,-1000,-298,-30,67,927,-292,-1000,-215,-1000,740,646,-48,-448,-355,763,-423,-1000,570,-805,-900,-930,-1000,-806,760,1000,272,-201,-129,154,-1000,-229,978,768,228,257,-1000,-472,-216,-802,198,-930,-1000,-559,679,484,1000,-78,-297,145,-314,458,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00697() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{1000,-1000,824,-427,-998,-506,-895,-1000,576,1000,-221,1000,204,-1000,345,1000,223,-1000,-73,-1000,76,814,268,-883,962,692,703,-967,-1000,-181,-1000,154,518,232,1000,589,1000,-796,760,279,-219,-759,570,1000,-1000,-629,1000,-1000,-937,-276,625,-1000,1000,-15,611,838,592,1000,791,-507,273,-130,-278,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00698() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-1000,1000,49,1000,-950,820,-879,729,-1000,-108,-1000,-650,323,1000,170,-1000,658,-284,-104,720,-1000,728,1000,-1000,-543,-1000,1000,784,1000,1000,761,-286,-310,-1000,-443,-1000,1000,759,277,264,575,-613,-739,1000,668,953,-311,810,237,1000,-533,277,-573,-743,-411,-1000,-1000,-983,-600,1000,-318,384,-203,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00699() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-1000,383,721,255,-754,-445,-877,100,66,-1000,-682,611,-624,304,-61,-398,-547,-64,186,702,180,-552,997,-47,381,-2,-1000,352,400,-247,362,1000,-524,119,-691,-809,-400,1000,677,-30,1000,1000,-1000,133,571,-125,-303,797,-1000,-937,-484,808,-541,-104,-944,-600,514,-400,964,449,817,-350,-545,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00700() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-75,-1000,164,-120,520,-638,-305,-1000,1000,-1000,713,149,673,173,-132,1000,1000,742,-31,-1000,-330,1000,667,-340,-604,-434,-891,217,-387,-557,1000,-441,560,721,-1000,1000,-838,-43,328,331,1000,1000,-1000,352,1000,-1000,1000,150,-173,-261,112,-1000,264,120,-71,-1000,917,1000,400,-1000,1000,-1000,345,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00701() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-281,-400,64,-616,313,208,-1000,-396,-164,351,1000,-770,-1000,-368,400,-1000,1000,-478,-826,-1000,-1000,1000,767,310,-1000,-1000,1000,-1000,416,1000,335,-1000,-1000,-1000,980,1000,-835,-1000,1000,6,237,-339,-535,1000,-1000,-1000,-508,-669,-32,-1000,188,-514,-783,-1000,120,981,149,1000,-1000,-925,-682,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00702() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-566,320,204,773,-301,203,-1000,-862,-590,-859,-727,1000,565,-376,-1000,-645,-1000,113,1000,-1000,1000,-1000,196,-718,1000,952,49,809,1000,-405,-213,869,1000,1000,-77,-324,371,221,1000,289,284,1000,1000,-1000,-311,516,-467,359,-496,902,154,501,-231,1000,-598,-404,-961,-325,1000,-213,-1000,-54,-1000,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00703() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-7,676,19,666,-825,-956,-760,130,-348,-376,-693,-660,689,633,-224,1000,-365,-18,264,-1000,-529,-704,1000,-748,1000,-497,-1000,234,-342,-524,-489,317,1000,-295,15,-867,1000,575,749,-711,1000,118,-1000,1000,402,52,225,1000,524,828,-711,-777,-576,-342,-142,-670,531,-1000,437,799,214,-640,-1000,-884}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00704() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{261,967,290,-1000,649,-198,-835,-519,951,909,-689,473,77,-885,1000,190,-674,374,-841,-724,-994,-421,-877,312,552,-1000,506,-416,1000,445,409,1000,152,-1000,-1000,-309,-695,-1000,-770,-491,-1000,-1000,225,478,64,74,-863,1000,440,-176,995,409,-1000,1000,-915,605,-81,-1000,515,-1000,241,31,193,752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00705() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-423,-1000,544,-424,-544,-621,1000,-458,563,1000,22,236,277,-837,1000,704,-66,890,-620,580,152,-991,-500,1000,-281,-1000,-681,-1000,-551,1000,-602,1000,-403,-1000,-1000,375,-568,-32,364,688,-1000,-1000,1000,274,507,615,-1000,-894,-657,-323,-19,-588,-1000,971,-782,-377,-516,516,199,-999,1000,541,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00706() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{25,-1000,279,600,635,744,766,-553,-534,1000,22,627,13,-280,546,1000,-987,1000,-1000,-1000,1000,989,-1000,-402,-1000,-797,-1000,-1000,1000,557,1000,1000,1000,-1000,-138,72,-796,-29,1000,-778,-689,-1000,-588,-1000,-500,839,-380,-894,-566,-1000,1000,-812,-1000,-832,-1000,1000,-369,1000,356,-994,-502,1000,-213,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00707() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-746,-689,762,79,-386,824,801,-766,-547,245,-707,90,1000,454,-213,662,-692,635,-990,-976,524,61,-1000,-1000,-1000,467,870,-830,373,-1000,128,710,883,-904,-156,154,319,1000,-1000,311,-117,-35,831,-968,-605,607,-403,-805,-1000,-265,150,-1000,462,-1000,743,-569,1000,1000,378,-718,442,300,-10,488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00708() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-113,967,416,80,-1000,25,241,-129,-252,-946,-166,-86,129,-1000,-201,-202,-1000,232,-226,220,-1000,-412,-867,-143,1000,581,370,490,173,-945,-507,562,-75,113,-1000,-213,-70,-572,-1000,394,-642,566,1000,376,-142,-1000,-1000,1000,-322,268,149,1000,-346,-29,545,-1000,325,-1000,165,-1000,-598,-652,35,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00709() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-735,345,975,801,-997,891,855,90,-859,-226,-717,-715,283,-209,-1000,800,27,-148,-360,-317,-1000,947,-769,-1000,-146,359,-425,-234,307,-874,305,-787,-284,664,1000,-44,658,116,-104,134,144,161,331,-842,703,-372,193,138,-931,-948,283,339,498,-511,-1000,-822,-290,-208,-440,-626,-333,250,1000,965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00710() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{204,-560,168,539,-1000,-5,77,-626,454,-823,-239,768,1000,-1000,269,402,-1000,842,-986,236,105,497,-1000,-828,-1000,40,384,-862,498,-1000,462,1000,972,-904,-765,-119,-342,-1000,-1000,487,-823,-746,781,-147,-453,374,-517,900,-177,-129,855,364,-1000,898,-989,1000,1000,952,1000,-712,-1000,-898,784,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00711() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-113,-1000,-501,133,-1000,-72,-588,846,-89,-123,-197,141,1000,-830,201,881,-1000,997,-663,-293,644,-412,-346,-217,-726,-501,216,-714,976,-1000,1000,3,850,-1000,202,-1000,756,-470,-469,-186,-1000,-1000,411,-583,302,-1000,254,1000,775,-745,457,-336,-346,-1000,58,1000,325,1000,1000,-1000,-1000,671,469,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00712() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-151,-112,-132,1000,-1000,-71,1000,1000,-324,-831,716,604,1000,-1000,298,102,-1000,-670,418,1000,760,38,-482,-322,-461,-470,1000,854,-387,-1000,929,-709,575,-458,-495,-508,-1000,-260,407,178,-504,1000,-351,-225,485,662,510,761,-212,-1000,-818,-619,-1000,323,8,1000,-398,-628,513,-433,-692,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00713() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{547,-1000,289,449,-646,-200,-484,-156,-430,1000,-349,-329,1000,-1000,673,503,63,378,-831,512,530,-722,-789,981,23,-179,-864,101,716,-1000,-275,797,784,-1000,-1000,-1000,-597,-513,-1000,447,-1000,-815,225,621,-525,-614,-858,1000,470,1000,893,1000,-328,543,-915,-726,1000,312,1000,-718,-1000,-1000,-336,938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00714() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{0,-1000,699,58,533,534,972,-567,701,279,-805,36,-530,-573,384,933,-464,1000,-990,-1000,761,-1000,-1000,-73,-465,-325,492,-525,1000,46,836,797,845,-933,-578,383,-164,-454,-32,-493,-718,-839,-196,-507,-674,-185,-562,-479,-524,625,902,207,384,-604,-621,-48,-170,-1000,123,-996,163,158,35,339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00715() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{226,154,874,-259,-832,320,665,566,-1000,-628,-1000,-73,1000,-883,79,813,-586,-93,-222,412,-1000,-330,-1000,-943,215,-934,497,-272,1000,-1000,788,1000,-101,1000,229,181,725,-1000,-828,-488,-159,-1000,-674,-193,645,-1000,147,1000,83,717,991,881,-850,193,-647,-979,-463,748,969,-1000,1000,452,1000,847}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00716() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{167,967,517,-734,-10,-18,1000,-1000,-153,1000,-496,-374,9,-512,682,1000,-484,1000,-1000,-52,-742,532,-1000,19,-402,-948,-659,-1000,1000,-464,941,1000,953,-1000,-890,468,-606,-771,-251,-838,-972,-1000,281,-700,-1000,832,-642,-1000,-256,-1000,1000,-480,-1000,6,-1000,-158,-756,662,454,-1000,254,1000,-431,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00717() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-613,-1000,223,-76,-1000,96,50,-331,49,345,-319,406,90,-185,-8,552,-834,623,39,-197,1000,-76,-707,-842,-1000,-1000,-465,-759,-574,202,125,649,1000,-837,-282,761,-301,881,-269,604,-287,-1000,-355,-611,272,1000,-356,-711,-518,-577,325,-622,46,-857,270,304,691,1000,1000,-862,-472,289,1,414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00718() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-353,57,1000,629,-1000,-198,443,-637,509,-887,-56,314,1000,-934,-134,190,-290,227,35,906,-501,161,-732,-398,-75,-182,585,-489,41,-1000,-1000,-48,183,98,-171,591,381,77,-1000,978,-592,-324,980,175,170,-547,-503,1000,-245,-206,596,1000,-256,376,-1000,-522,969,31,852,-461,-1000,-1000,780,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00719() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-500,-809,645,-76,-1000,-45,282,-627,-138,583,-835,-283,143,-488,120,552,-362,1000,-986,-305,416,-238,-865,-533,-811,-124,-825,-1000,-236,505,-113,710,756,-904,-397,910,-209,-329,-695,482,-596,-771,-194,-495,282,781,-614,-1000,-433,-1000,855,-796,-318,-275,-907,2,-232,939,1000,-994,479,814,-251,821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00720() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-1000,-1000,634,-556,-651,12,88,-258,1000,-705,-507,841,-990,1000,-567,259,1000,521,1000,947,869,-1000,-1000,-1000,-331,736,613,-383,160,1000,360,-1000,720,163,-949,-760,-412,-20,562,412,-683,346,153,558,361,-1000,832,470,276,-368,-614,550,255,-760,278,284,-196,-215,400,-594,-908,670,572,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00721() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableCollection", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-283,-917,-882,-66,465,215,726,1000,1000,179,928,983,75,1000,164,593,680,-939,-250,-1000,-1000,898,1000,1000,-29,626,-501,1000,570,-383,-834,-92,-344,10,1000,499,862,-1000,109,-1000,-753,702,281,905,-332,1000,30,633,1000,-209,849,-634,1000,589,54,-337,609,-377,-399,242,163,-332,-1000,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00722() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-1000,-882,254,-883,61,-495,315,379,417,50,-246,140,-456,-358,-377,649,900,20,625,1000,-314,-276,307,-458,-80,605,817,216,203,1000,290,-275,605,1000,-443,230,-400,-1000,866,-824,-1000,288,-194,998,1000,-728,-520,-1000,705,-520,-505,379,694,-887,424,542,79,-853,400,787,-551,-568,-881,66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00723() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-923,-576,-716,60,589,-712,340,351,-112,-841,-177,669,-838,1000,-1000,-77,495,-893,761,-92,-692,-1000,598,-413,891,989,-31,393,832,541,76,-723,105,-132,588,-37,412,1000,812,-716,-533,-139,1000,-4,387,627,348,-390,172,-764,271,462,390,-603,813,-1000,645,234,-1000,1000,402,96,-454,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00724() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{579,-1000,-746,314,672,319,743,721,-167,-265,400,-353,-306,788,-272,-1000,495,-938,13,-1000,-595,391,1000,960,-39,841,-630,649,603,-383,-337,-297,-257,-329,967,-202,1000,313,81,-1000,-301,808,901,593,-786,1000,290,633,1000,-545,566,-387,475,693,-503,-337,331,221,21,272,-21,294,-270,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00725() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-758,255,1000,-836,-42,-915,-361,549,-582,158,115,381,-260,859,-162,-592,915,-779,-492,1000,-618,324,243,-180,324,871,515,685,442,1000,833,-434,1000,842,-592,1000,-755,-685,368,-117,-1000,410,-188,838,1000,-1000,-862,-964,1000,-501,276,80,-138,-901,1000,93,-96,-1000,-364,775,-86,-1000,-678,830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00726() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-1000,1000,948,1000,1000,-367,-1000,655,4,308,-1000,753,165,1000,135,382,169,-1000,-942,1000,129,-15,459,-376,-10,723,-230,816,1000,1000,842,-1000,717,1000,-660,-191,-1000,-312,-332,1000,-1000,1000,-1000,-254,208,-799,-1000,-631,-57,1000,-559,-954,-1000,1000,-432,-736,169,-66,1000,610,1000,-1000,422,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00727() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{857,-748,449,60,362,115,263,-504,-232,913,854,-842,397,-127,-126,900,-621,231,-54,147,-234,-70,-496,-643,-336,-929,-828,-882,-808,-835,-915,-116,534,-970,-203,154,845,-506,-314,-707,379,-976,-822,-889,839,808,489,-147,-354,916,-901,-723,598,695,-288,144,235,778,-941,-352,88,752,604,32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00728() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-1000,183,-125,-326,-1000,-990,-392,1000,483,185,1000,43,447,298,683,-640,743,217,742,-1000,911,-116,-76,-358,-275,-488,-508,695,1000,-60,-1000,44,-1000,-864,112,597,940,-1000,-1000,-1000,-1000,1000,-883,871,-494,1000,-324,355,-789,1000,-394,-608,1000,831,692,-624,-195,-377,757,284,828,1000,575,-348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00729() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-1000,-1000,-961,-126,-222,71,412,-1000,-535,-690,-507,-230,962,960,-554,-1000,993,220,683,-1000,367,-1000,231,-163,-184,585,-246,454,473,-1000,-485,-529,-398,544,1000,111,1000,1000,-451,-1000,-191,956,487,-219,-500,-1000,656,155,235,-842,418,253,1000,-997,1000,-523,1000,627,-408,833,71,1000,50,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00730() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableCollection", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{959,-421,1000,-514,-561,-406,356,343,420,-744,-1000,988,-358,-530,-710,912,452,22,685,853,434,-219,-137,-461,123,913,1000,-423,249,627,500,-1000,587,276,-1000,536,-635,-722,1000,515,-438,-83,445,515,270,-1000,47,113,488,-143,-615,357,-774,-140,-432,493,-539,232,1000,-384,-477,-349,352,-112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00731() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-1000,-656,-711,410,267,877,-342,295,352,-804,-56,335,856,-11,353,-801,495,22,681,-1000,1000,-636,854,-472,187,-457,-268,657,1000,-632,-1000,-1000,-1000,-1000,-271,484,-1000,-832,926,-1000,-1000,1000,-1000,-56,-1000,210,680,-175,-180,592,-381,-1000,734,1000,243,-170,-702,536,-21,962,919,1000,594,-99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00732() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-1000,577,-800,1000,-748,886,315,162,818,-881,739,1000,1000,747,348,-167,861,-241,677,-1000,-314,-141,-158,-709,931,-231,809,97,1000,-1000,-1000,-720,-1000,-1000,789,925,-909,500,-1000,-1000,-956,425,-1000,78,-1000,1000,227,905,-586,-520,-505,-16,246,1000,-587,-316,44,1000,400,1000,1000,1000,1000,754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00733() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-971,-1000,-588,-431,64,893,-508,739,202,-162,289,782,-530,-1000,-178,-823,227,1000,1000,-472,1000,-1000,532,-1000,-135,1000,310,-104,874,768,-774,-988,-835,-594,-1000,-1000,-1000,-1000,442,-529,-880,1000,1000,-362,-1000,210,-868,505,217,59,-645,-584,871,-433,-247,124,-1000,-311,959,389,520,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00734() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-1000,642,-326,874,-472,-249,-460,-693,1000,-390,318,616,1000,1000,150,1000,1000,-878,618,-416,-379,734,-1000,-283,546,35,1000,345,1000,-1000,-386,-104,-1000,-177,1000,1000,-850,804,-598,-1000,-228,118,-1000,1000,-141,565,476,-302,-323,469,-330,793,-339,633,368,-90,1000,279,-273,1000,-813,-407,-162,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00735() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{487,-915,-695,307,1000,-47,71,-1000,-450,-603,-507,549,-990,1000,-670,-1000,849,537,-426,-294,-1000,391,954,-1000,-57,1000,-1000,-103,603,577,-467,-983,502,5,1000,-876,1000,756,-226,-821,-451,829,425,1000,-24,1000,2,633,496,-817,1000,-242,723,165,276,-1000,465,-371,-664,75,-357,69,-644,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00736() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{400,1000,-41,60,-319,-43,567,613,-27,426,-124,-383,-673,-312,400,-213,-541,860,891,-1000,-674,-589,-411,-16,896,-290,-872,-400,114,-1000,464,1000,-377,-1000,-471,211,-417,183,467,1000,855,-313,-981,1000,-594,-240,1000,1000,400,302,-332,400,-99,219,350,1000,-240,902,814,303,-1000,236,450,-253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00737() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{-400,281,-583,-73,-320,-935,-414,-162,930,461,572,400,412,160,204,209,44,491,714,145,1000,822,-647,-33,573,-51,67,194,268,400,-240,396,-74,115,366,247,-21,204,100,437,-378,400,969,-803,-38,123,-742,373,-400,70,438,534,713,-141,-696,-1000,89,298,-845,-29,831,-72,-616,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00738() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{1000,-1000,-606,-672,-1000,-43,213,-1000,1000,731,316,-1000,1000,-321,1000,-657,-679,860,633,-531,-154,-488,-711,1000,622,-234,2,-850,-536,-40,-1000,-686,-377,-1000,-430,1000,978,1000,1000,1000,-793,-20,1000,-262,-1000,-589,1000,651,238,-1000,1000,400,324,523,76,46,-240,-943,814,-142,61,-1000,-275,-186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00739() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{-1000,-400,-186,239,55,-1000,306,-578,184,436,-411,1000,-549,394,1000,-48,1000,-99,422,295,456,458,-954,-67,1000,-761,-638,374,-135,990,-469,705,-159,183,1000,-141,736,258,-126,177,-542,544,1000,-1000,975,1000,-301,224,-1000,454,643,-1000,663,741,-1000,-1000,-1,273,-936,390,823,762,-955,-600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00740() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{-1000,1000,-66,-821,-704,1000,137,645,710,772,-849,-1000,-529,-693,1000,-705,-775,1000,630,-1000,-1000,-904,-1000,1000,842,-428,-1000,-1000,424,-1000,276,746,378,-1000,-252,955,-690,-1000,1000,1000,1000,-1000,-1000,1000,-1000,-1000,800,840,1000,-30,1000,1000,-235,274,-1000,1000,-1000,-76,1000,-984,-1000,289,203,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00741() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{-1000,1000,-88,2,-23,-827,493,-392,44,362,70,1000,-744,394,-1000,-48,1000,-316,1000,443,302,389,-891,-238,1000,-1000,-762,374,1000,1000,-352,340,-695,183,784,-274,402,483,-195,11,-303,605,1000,-1000,1000,1000,378,468,-1000,756,131,-1000,745,-97,-575,-1000,26,20,-542,574,147,762,-1000,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00742() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{1000,44,443,234,87,-1000,861,-411,-475,-420,-766,-436,-267,-748,671,-1000,467,988,-871,909,433,233,-1000,97,-181,1000,-679,-786,-765,40,-674,-927,231,410,-1000,1000,616,-707,1000,255,-747,1000,-1000,-1000,-734,-547,-411,-281,553,-1000,-332,976,45,819,681,906,462,-1000,-588,-782,-241,-766,-1000,-394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00743() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{1000,1000,169,-597,-216,-43,567,433,-27,441,26,-383,-472,-1000,400,-1000,-1000,30,1000,-1000,-288,-358,-576,-16,457,-290,-422,-400,114,-321,108,-678,-377,476,-339,211,-417,-191,438,-3,286,-313,-1000,795,-1000,-240,1000,-88,400,321,-332,546,43,219,632,1000,-430,-175,884,242,-1000,236,230,-253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00744() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{35,1000,999,-37,-533,297,282,473,-367,284,-649,679,-1000,-836,-626,-771,125,-360,76,865,605,924,56,-381,323,-241,241,235,846,21,-322,1000,-255,-71,1000,79,-17,382,-817,120,-833,124,1000,138,-90,884,-144,432,8,1000,-344,276,-310,1000,342,-1000,323,658,-1000,550,995,189,-434,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00745() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{443,-357,-311,-587,-689,-519,-73,74,370,514,850,542,421,1000,-922,61,1000,243,760,-1000,557,985,-1000,0,359,-588,220,579,1000,941,232,-49,97,63,756,-512,-805,-303,523,51,-111,270,1000,765,758,386,-539,28,-1000,501,1000,-567,772,-1000,-1000,-718,-421,-819,-1000,-215,735,1000,564,-768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00746() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{443,-357,1000,-587,-792,1000,546,1000,-642,-892,-340,542,-517,-576,847,61,313,548,-1000,-976,1000,1000,-249,712,-1000,-659,-681,-562,223,-1000,53,-398,-448,-940,-654,-874,-264,192,592,529,152,270,-658,1000,758,740,15,342,866,739,43,1000,-915,-78,-522,1000,-591,-819,-1000,-387,-372,1000,712,22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00747() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{240,711,-209,-718,428,321,-283,-762,733,1000,-264,689,-87,-82,71,-269,-382,85,756,894,-599,-444,643,-704,573,284,-308,101,-46,99,543,575,217,515,1000,660,-439,-537,154,-275,-615,-405,1000,-1000,11,-501,225,-1000,-226,-801,-575,-172,-473,-275,356,-492,667,876,129,297,-234,435,257,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00748() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{848,1000,684,742,205,1000,1000,904,298,-57,-1000,36,-1000,-657,1000,-1000,-1000,121,-48,-476,-819,-1000,742,-667,702,-214,-1000,21,-106,-1000,637,886,234,-793,-793,-7,70,653,280,287,779,107,-1000,862,-271,374,953,500,414,185,268,-33,-1000,781,1000,-251,21,781,1000,1000,-960,238,182,-214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00749() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{-790,-669,-175,-949,-719,-699,-197,243,549,-186,-174,1000,-245,72,97,566,511,-47,724,390,1000,1000,-552,-33,-30,-1000,792,194,149,400,-389,455,-1000,-623,-74,-841,-51,678,-446,437,-327,1000,-634,-626,1000,1000,-654,1000,-493,1000,-191,172,1000,40,-387,1000,467,-41,-952,-379,145,525,-1000,-805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00750() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{-607,-207,-201,1000,-270,-729,1000,364,5,511,-355,879,118,400,-599,340,818,-738,1000,327,-874,-359,-13,-1000,1000,-1000,412,1000,1000,873,-1000,-251,-661,931,-37,273,308,1000,-388,-973,-760,876,448,626,120,1000,564,-614,-400,-317,-828,-400,608,-400,-629,-264,3,440,-1000,1000,-1000,924,-574,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00751() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getDrawSharedDomainAxis():boolean",
            new int[]{-1000,1000,929,-68,646,-729,1000,247,-739,19,-1000,1000,-1000,-304,-1000,-669,286,-223,130,672,738,606,411,-1000,508,-1000,-409,-1000,227,643,-17,446,-235,-230,591,-139,224,396,-638,-407,-775,1000,573,-1000,596,1000,690,929,-857,679,-815,1000,382,1000,825,-511,659,99,248,574,581,13,-1000,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00752() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-1000,843,-999,-1000,1000,257,604,-1000,-902,-888,-292,209,-1000,-17,-573,670,1000,465,856,-1000,-1000,-682,-1000,-254,-688,711,-1000,1000,1000,-680,597,631,1000,1000,1000,-210,458,504,-189,-515,1000,-942,1000,269,1000,1000,215,-667,-432,1000,194,289,1000,1000,563,206,-1000,1000,-129,1000,1000,1000,317,-996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00753() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,212,-791,-90,327,-49,811,51,1000,331,-617,-52,234,497,-4,123,-782,919,1000,-1000,-898,-1000,261,-1000,-1000,393,-1000,779,-580,-780,1000,-209,1000,366,1000,-1000,697,126,-508,756,-1000,-731,543,1000,1000,235,1000,524,605,196,1000,339,1000,-200,1000,-105,246,920,-1000,363,200,813,-144,-136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00754() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,-1000,-901,645,1000,236,1000,458,191,341,-353,932,431,-1000,-924,1000,-46,576,-336,241,779,41,-475,-1000,533,-937,322,130,-839,-1000,-335,-912,-633,-449,315,-438,-613,608,-196,646,546,31,535,1000,388,1000,711,492,-1000,-39,544,475,-1000,-1000,-549,-1000,-153,-1000,-1000,1000,-1000,215,272,811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00755() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-405,590,-31,-1000,320,118,-249,-147,451,-911,247,-986,-412,660,434,483,393,400,397,-1000,-995,-1000,138,-260,-1000,1000,-532,580,998,-380,333,-209,859,1000,295,-506,623,-412,309,-780,164,-1000,893,-186,959,-336,119,-521,181,544,160,222,797,318,1000,329,-851,1000,1000,868,285,828,-443,-794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00756() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{635,-584,79,-866,597,339,365,36,491,1000,1000,-71,-580,-652,-1000,1000,734,119,839,1000,119,480,775,348,-285,566,-338,-1000,48,-233,-1000,-6,-544,-270,-1000,1000,311,470,-6,-1000,-286,529,296,1000,-3,643,-1000,-519,273,-22,-1000,1000,980,-56,-714,-1000,289,-1000,1000,1000,-96,290,247,-804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00757() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-232,507,-1000,-1000,1000,118,1000,-749,77,-800,579,633,-1000,426,-566,558,393,-83,163,-782,-1000,-653,-234,-944,-825,543,-1000,1000,998,-293,636,650,1000,1000,1000,-600,623,-880,-379,-151,164,-586,893,338,996,1000,673,-956,357,445,759,-990,797,299,928,-148,-80,329,428,868,1000,1000,686,-734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00758() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-756,363,-563,-213,1000,-128,112,119,-1000,407,96,1000,-763,-194,-1000,846,327,986,249,-134,385,162,-552,503,-172,-1000,-440,-309,-323,-304,618,1000,12,251,108,471,-425,45,-273,125,1000,-1000,1000,870,-184,1000,91,504,-493,1000,27,737,930,-679,-499,-692,-730,-708,472,217,1000,185,347,623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00759() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{591,-723,-311,1000,229,-718,-237,-69,-988,418,91,1000,-88,-14,388,-583,-1000,-149,24,1000,1000,394,-712,1000,1000,-1000,594,-964,-347,-1000,-779,497,138,-1000,-175,-237,-824,1000,438,-164,731,1000,-666,739,-1000,437,-209,1000,566,-778,-376,-580,-394,-8,-1000,-973,404,-1000,-1000,-1000,437,-1000,507,435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00760() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-1000,1000,-901,-302,115,386,31,584,338,121,-353,-1000,-777,526,-924,496,73,-504,-336,241,-1000,165,-286,-1000,533,1000,912,-713,1000,-604,-231,-452,-73,-266,315,67,434,1000,642,646,36,-1000,140,-52,388,-839,711,492,272,-39,544,1000,-699,1000,165,258,-153,1000,816,1000,-1000,356,-1000,-969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00761() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{215,496,-166,611,736,1000,1000,567,-271,-63,-872,-42,-577,524,-431,635,-960,865,179,-670,-50,-215,-657,-546,341,-477,-786,219,-893,-670,380,137,-510,-196,953,-916,-844,396,-590,-178,1000,-284,498,84,53,929,1000,-659,982,-153,763,26,889,-960,-11,-593,-2,-33,-533,861,-530,46,-1000,-677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00762() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{402,-920,499,-148,125,-594,-1000,78,-530,-704,-835,606,-271,221,478,-79,-1,921,25,781,230,-1000,434,404,-659,-1000,-119,56,-1000,-53,802,710,666,105,-173,-361,113,232,17,914,277,932,703,252,-147,292,716,854,-152,856,1000,-42,922,-1000,130,105,381,-290,-731,-1000,621,71,677,272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00763() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{554,-1000,328,917,-1000,1000,1000,304,211,677,-758,-252,-246,123,-531,964,-688,-529,600,671,213,578,221,-542,-399,-96,355,-851,-723,-546,-766,-349,-971,-1000,-372,154,-474,1000,224,-464,-74,-218,-339,1000,-512,218,-89,221,494,-186,-502,179,309,-1000,1000,-825,544,-1000,36,1000,-1000,-202,-698,-115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00764() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-234,396,69,-1000,387,-134,-447,498,280,-131,247,128,-256,651,-232,560,393,763,346,199,-124,-557,414,181,-663,-370,-425,-196,72,-63,565,339,373,476,-132,-188,-3,-699,1000,-231,331,258,893,196,277,189,200,195,85,714,1000,457,820,112,406,-90,-662,-108,1000,320,442,332,330,-144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00765() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-955,-1000,-696,-893,863,-693,-1000,-407,-736,-486,638,1000,-1000,-1000,-23,701,944,1000,-1000,1000,725,140,-947,-596,-907,-611,-481,927,-1000,-1000,-55,696,1000,1000,-90,1000,458,504,102,247,597,1000,1000,984,-867,1000,-174,370,628,1000,203,-1000,1000,-483,561,-1000,-1000,-1000,-468,128,1000,206,1000,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00766() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,-50,7,647,-262,381,421,452,1000,-541,-1000,-534,-349,993,306,369,-1000,-180,1000,-205,-652,-968,1000,-807,-856,355,-1000,-452,-880,291,479,-134,930,-927,-84,-389,195,1000,193,539,-754,-272,-256,-9,576,-472,784,-633,1000,32,798,188,832,-717,1000,-176,1000,-158,-714,361,-624,410,5,169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00767() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-584,1000,-581,-757,636,-402,323,768,-226,-1000,-55,619,-980,1000,-407,15,409,1000,231,-1000,-687,-1000,-466,258,-1000,-634,-1000,1000,171,132,1000,1000,1000,1000,1000,-1000,130,-943,-696,666,1000,273,1000,-916,386,549,942,-361,345,592,1000,-927,1000,1000,1000,872,-203,1000,-122,176,1000,945,537,-680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00768() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-215,727,-528,-595,-714,-565,-432,326,-481,931,202,1000,221,18,62,-968,-357,-975,-186,-371,-10,393,-640,-208,-817,445,-158,-1000,-1000,985,-209,946,32,-42,-929,7,451,-306,1000,-168,-169,-1000,-1000,-669,880,120,362,-228,337,595,-388,-900,325,-337,-1000,-355,-1000,-36,476,1000,-8,192,670,376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00769() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{1000,-424,614,1000,-1000,-501,1000,925,-1000,1000,1000,538,461,-299,-79,-628,82,-46,1000,-66,122,1000,1000,-1000,155,-841,389,-1000,-800,57,-517,-425,-705,1000,-1000,-445,1000,-1000,-205,-41,-764,493,-1000,-14,-1000,-875,120,-204,-419,1000,1000,-472,987,-1000,-1000,1000,-1000,781,548,-96,-1000,667,-554,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00770() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{316,-716,499,-888,-196,-957,468,645,491,-7,101,56,725,-146,-268,-908,1000,72,919,1000,-136,418,93,-122,-779,-549,-541,-1000,-852,287,-92,-313,-644,-678,-1000,801,753,734,-211,-181,707,602,643,1000,-451,-262,1000,-340,72,1000,42,-40,1000,41,321,-1000,315,178,-589,-35,662,11,-708,-745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00771() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-25,-1000,499,1000,565,-265,1000,94,1000,-960,1000,-1000,-280,179,-1000,143,1000,72,1000,1000,-169,-1000,770,208,-647,-1000,-463,28,126,-65,-710,-1000,-1000,-784,-1000,266,1000,1000,-211,263,979,1000,643,1000,-935,56,-66,-33,-704,657,1000,-1000,616,-904,1000,-1000,1000,166,-955,-1000,590,1000,-1000,-745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00772() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{381,56,-286,116,-503,-153,139,0,-173,-1000,-573,411,-886,162,-1000,-599,347,-614,111,-352,-555,256,483,-943,-262,-602,36,-168,-156,970,-1000,640,-679,606,61,550,1000,-449,1000,-164,99,-332,478,-317,-820,-1000,300,-175,-352,56,323,-655,332,-405,-887,258,-1000,-704,-8,1000,65,404,276,661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00773() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{717,-1000,-141,-313,646,-1000,573,-29,394,418,1000,679,1000,436,46,-467,1000,-464,1000,1000,-654,-575,207,-328,-685,-289,-617,-781,55,-49,-662,-832,-906,-326,14,808,647,620,-730,-715,705,1000,367,85,-828,54,1000,768,-719,534,171,13,1000,-221,1000,-657,879,978,-665,-24,517,792,693,841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00774() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-545,-89,37,-400,-10,180,48,-498,-443,1000,870,983,-738,-21,536,24,-88,-867,423,-261,-522,337,-83,-1000,-56,258,-469,75,457,45,-829,298,176,1000,-918,-2,627,25,-467,-310,-714,344,314,-1000,-168,-159,553,321,-281,161,6,379,-96,-130,-362,744,-369,-427,231,586,-504,392,400,841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00775() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-1000,414,549,-153,340,-501,-470,-1000,1000,-721,-903,-1000,461,206,-1000,616,93,592,-845,-1000,122,-628,106,126,1000,1000,1000,825,-279,1000,-316,-425,-942,387,88,162,433,616,1000,1000,535,-306,-106,-568,667,-780,-823,-472,-299,-33,367,-1000,987,-497,688,-1000,25,781,548,1000,295,-22,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00776() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{243,75,-266,263,-1000,304,573,604,394,-685,-1000,549,243,27,-1000,-663,-385,-764,-272,-978,-112,1000,207,-1000,-112,-222,1000,-44,-1000,837,-1000,999,-355,901,-1000,-204,647,620,1000,1000,-249,-110,367,166,-828,-1000,1000,-1000,-50,587,340,-630,1000,-719,-1000,959,-1000,-1000,279,1000,-934,360,62,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00777() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-886,-266,-102,-835,-176,-957,-185,127,718,-1000,1000,166,91,9,-1000,-1000,1000,-303,385,706,387,454,767,-122,-1000,353,508,-660,-1000,763,-362,-347,-544,-678,-692,529,835,1000,1000,-276,592,-548,1000,626,16,-211,1000,-532,-96,805,52,-1000,1000,366,132,-1000,-928,-609,-369,386,1000,-222,-711,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00778() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{424,898,549,-37,-626,830,-243,435,443,-1000,710,-104,-1000,-164,-1000,-1000,-340,204,-170,138,643,1000,1000,594,215,-1000,1000,-1000,-1000,1000,197,-28,-500,-397,-151,900,1000,-397,1000,-106,30,-210,620,361,6,-706,-823,-472,413,435,563,-1000,814,-398,-445,-1000,-1000,-1000,414,698,-520,-736,-1000,-453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00779() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{1000,-138,139,732,458,-1000,319,116,-677,1000,579,1000,-261,-38,610,-614,541,-152,1000,538,-214,-209,-36,-97,73,-1000,-669,-859,218,340,-1000,-816,-41,1000,539,-995,1000,215,-1000,-1000,-922,638,-1000,-659,-1000,-17,504,-1000,-931,256,1000,-276,1000,-676,-478,493,256,1000,271,-312,-334,763,243,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00780() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-337,492,229,-551,-1000,-652,-235,893,-121,352,-457,281,180,-438,-257,-1000,50,-285,88,63,-76,1000,214,-38,-872,-35,1000,-1000,-199,1000,225,1000,13,296,-1000,240,616,86,1000,203,95,-1000,-314,582,744,-216,554,-1000,811,1000,-350,-1000,544,-41,-1000,264,-1000,-532,209,681,294,-409,-724,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00781() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{69,-531,774,634,-988,1000,1000,-371,-299,1000,1000,180,-310,-134,1000,1000,-861,103,797,-470,399,993,339,-178,716,432,-492,400,590,-1000,-250,492,77,1000,-1000,-1000,-93,-271,-1000,847,-1000,1000,-1000,-781,84,202,-623,-1000,47,853,1000,728,-1000,-503,-633,909,-369,-894,-68,-921,-1000,929,-200,752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00782() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{534,-424,69,-924,1000,171,-916,-713,943,-413,640,775,-413,319,-382,597,1000,-650,1000,1000,-522,-394,-22,732,-962,653,-1000,-1000,-437,936,-670,-775,-711,-913,-50,1000,1000,143,-211,-1000,201,18,-730,367,653,1000,1000,1000,-240,34,-600,-755,1000,1000,1000,-1000,722,-51,-293,283,1000,-643,-1000,-682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00783() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-365,573,-894,-1000,458,-1000,-649,-473,466,-43,809,803,-1000,328,451,-908,487,-786,-32,166,-485,-209,-755,286,-1000,205,-1000,-9,498,1000,-878,488,-637,-61,1000,1000,667,1000,186,-1000,61,-1000,1000,-760,-373,163,756,1000,-414,-104,-300,-34,1000,412,-365,-737,256,1000,283,1000,1000,-157,1000,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00784() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{402,-211,-568,-683,-195,39,69,414,-482,940,764,-506,174,540,-196,880,-231,149,-354,42,958,561,313,980,-328,-84,-1000,651,207,-378,-1000,-545,-876,-305,152,-590,1000,48,-670,-601,354,729,-144,1000,473,-834,91,1000,156,205,525,-726,-866,1000,-160,-1000,280,-198,-248,519,784,757,-465,-503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00785() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-80,834,-476,10,-606,-219,-869,-1000,-1000,1000,-300,-1000,-654,1000,-1000,-428,911,338,647,1000,-176,831,821,-685,1000,-1000,1000,36,-535,621,1000,-1000,-1000,1000,-750,-162,-61,434,-28,-1000,1000,630,869,-1000,684,-607,-95,-173,1000,-645,-213,-308,-221,249,-440,238,963,1000,781,973,-432,-469,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00786() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-1000,919,688,-1000,-662,93,-520,-474,1,221,510,642,363,-152,1000,-872,180,-98,202,-675,-575,-1000,-502,-105,361,132,513,-1000,319,-577,1000,408,-1,-815,-584,970,1000,760,-400,-1000,-1000,-630,1000,-1000,-1000,580,528,1000,556,327,-369,-272,211,181,349,-1000,854,-159,461,-65,-1000,-1000,-662,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00787() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-296,417,1,-448,-450,-377,-641,-793,-688,553,65,-701,-217,383,-426,-56,911,599,132,257,263,-445,-34,-400,729,-606,1000,-341,293,-438,811,-1000,-757,1000,-440,90,139,1000,-1000,-630,-23,-471,795,-816,-162,-628,532,-191,1000,-844,-149,39,-636,550,-386,-157,856,672,138,437,34,-407,-1000,-752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00788() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-213,-233,-43,441,-519,1000,223,521,378,768,728,-6,-846,1000,903,-32,522,-970,702,-447,1000,-723,-294,1000,-538,-1000,-927,1000,1000,755,-577,1000,116,-1000,675,-1000,-400,1000,913,-270,599,-1000,-455,1000,26,228,371,-1000,623,-694,-211,-272,-450,784,974,909,276,265,645,1000,-352,1000,-1,709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00789() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{178,-74,-736,-148,-62,190,564,888,1000,-1000,896,699,-515,-453,310,86,-1000,122,-489,-1000,971,-1000,-1000,168,-1000,556,-400,462,1000,42,-831,522,722,-960,726,-202,433,496,-1000,288,-1000,-1000,-270,1000,-1000,-766,557,-210,-1000,-45,122,-25,-295,402,-753,-821,172,-188,-1000,-1000,256,676,-250,723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00790() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-89,-1000,-291,1000,-129,-592,-537,288,166,1000,-130,142,1000,-381,1000,1000,975,143,-590,-534,1000,-34,154,1000,-289,49,-1000,-72,809,-349,-1000,-504,-95,-28,-174,-277,1000,894,-1000,-90,-680,1000,-24,851,397,-159,346,-764,1000,-239,1000,-396,-196,1000,945,-1000,-122,-1000,260,1000,1000,189,47,-743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00791() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,-175,187,183,1000,-1000,996,574,967,634,-532,459,-969,-730,718,1000,-452,-1000,-739,-720,1000,-764,-1000,1000,-1000,717,-1000,1000,720,1000,-995,987,722,-1000,1000,-1000,-23,375,1000,-567,-915,364,-1000,1000,-1000,-967,224,99,-1000,-1000,1000,126,107,-88,733,-1000,-1000,-1000,52,-1000,234,1000,-530,930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00792() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-776,408,473,-405,-316,1000,-521,446,-450,937,409,-791,-322,51,211,-271,-273,1000,282,-975,583,-653,280,-464,-168,32,817,-613,579,-1000,409,-1000,-653,955,-442,311,565,-24,-380,-785,-770,-630,1000,-562,-663,-502,396,936,-346,290,-393,-316,-642,1000,-425,-844,675,770,-338,-338,-1000,-705,-982,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00793() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{656,-1000,489,-148,611,955,564,205,846,-771,-14,353,-273,-389,279,1000,-1000,-495,-58,-1000,1000,-1000,-1000,1000,-1000,65,-1000,516,1000,305,-547,107,722,-1000,427,-742,1000,728,166,904,-812,-1000,-638,1000,-913,-766,173,388,-310,-1000,880,-25,12,223,613,-821,-1000,-400,-1000,-1000,256,381,6,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00794() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-296,-604,-281,-1000,-456,621,-476,-949,291,797,-760,513,505,-346,1000,603,-443,354,1000,-1000,133,-445,-531,1000,460,-231,-325,-1000,697,381,1000,-680,-40,507,-471,1000,1000,1000,-1000,-1000,-1000,803,795,-774,154,526,378,1000,-315,-844,108,457,494,226,782,-546,970,-1000,138,383,1000,-1000,-963,-953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00795() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{583,-193,-281,770,-1000,4,949,1000,1000,152,-37,1000,325,1000,-150,-66,-446,819,279,-519,-156,613,-500,517,961,-1000,-1000,-103,1000,773,587,908,552,-1000,416,-538,-161,-720,-194,577,379,-314,-57,-949,1000,98,-130,-1000,196,193,-1000,82,440,-851,146,-19,100,321,-86,1000,-225,42,372,-953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00796() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-829,-1000,47,-65,-187,-1000,-1000,-662,507,538,-1000,792,1000,-1000,-635,1000,1000,-664,175,-715,813,1000,775,1000,343,157,-1000,183,1000,26,309,-1000,1000,815,-512,-782,1000,482,-1000,62,-1000,1000,155,-1000,-276,1000,-66,1000,1000,-993,1000,626,1000,99,1000,-1000,-927,-1000,1000,1000,1000,-1000,393,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00797() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{556,-418,-11,124,764,-785,623,-368,44,-1000,-1000,1000,-874,-218,-667,-320,-766,-852,770,464,474,-264,-464,-10,-1000,362,-323,364,-589,656,561,974,-1000,-1000,402,-203,489,-230,-796,84,-417,30,-402,-819,192,-1000,177,-746,-105,-1000,1000,-417,-905,525,-211,-1000,-36,-525,-1000,-231,-397,1000,-893,172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00798() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-258,400,869,1000,236,-13,410,-375,-930,-172,-376,-1000,235,580,-278,-528,1000,174,-453,992,796,644,971,-113,-578,-716,350,-292,-185,597,-279,-849,-755,-20,-413,-1000,-211,56,701,1000,449,169,-1000,914,-537,-1000,-43,-580,515,-499,72,96,72,138,828,441,-272,1000,-1000,502,-1000,545,-1000,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00799() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-674,-1000,709,35,480,-206,546,-828,-830,-444,25,-1000,289,-1000,1000,229,1000,539,-549,340,267,75,678,344,-880,-517,738,-1000,-640,-659,454,-1000,-876,776,-819,62,946,-613,-89,774,-1000,1000,-78,214,-1000,-205,115,1000,958,-106,-509,-1000,16,500,-919,-927,-11,405,-1,782,24,-1000,-970,-961}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00800() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{-445,-671,529,-1000,-511,226,464,1000,-313,548,-1000,-751,-555,68,-1000,845,-1000,-39,395,1000,1000,-586,285,434,959,-1000,-228,118,1000,301,272,-232,70,-1000,1000,-1000,-686,234,-1000,490,-854,-504,742,955,-584,-539,-440,-375,1000,1000,-296,75,-983,-1000,-263,763,64,-1000,-48,391,-798,130,531,478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00801() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{-56,-194,-256,-1000,1000,615,1000,867,-456,-1000,-846,622,1000,-1000,790,429,103,1000,425,672,514,1000,-544,1000,-292,652,1000,-232,154,-217,-1000,782,-152,-1000,230,-245,-85,142,87,1000,-1000,-1000,-746,147,-863,-485,-352,-91,-324,1000,725,-1000,510,651,-1000,163,1000,-161,-435,666,-358,-1000,-162,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00802() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{-56,157,-103,48,-538,-751,48,171,1000,-4,-4,-420,429,-270,-727,696,103,518,161,434,366,-454,-120,359,-410,-1000,-1000,1000,366,-217,-432,292,1000,-105,515,-245,-575,-204,206,-824,1000,-1000,-657,-716,-402,-159,-1000,94,-1000,-560,-497,-213,510,-593,130,-1000,828,-575,-566,666,-358,-1000,-1000,577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00803() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{683,-960,529,147,1000,591,538,309,-244,-1000,-852,-775,558,-1000,-224,-134,363,-39,698,620,-476,-604,-913,-831,630,842,1000,159,573,687,-368,-993,641,-1000,-197,-698,-700,803,681,538,-940,-504,-1000,878,-584,-812,152,905,-387,763,-226,-400,-569,1000,93,763,1000,-158,-66,-209,47,-1000,-781,269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00804() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{-608,-1000,-177,-367,-1000,-65,115,936,-67,1000,76,-862,-1000,1000,-1000,-309,-851,-1000,-1000,965,688,-1000,-199,-665,-78,-759,1000,469,916,-411,1000,-1000,405,-670,439,-388,-1000,-132,-1000,-98,-640,-472,256,1000,1000,-459,960,-190,1000,1000,-769,106,-807,257,451,530,606,-481,1000,409,-928,386,531,28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00805() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{180,-995,-640,-186,962,-513,68,-556,-152,133,838,-165,943,-53,-266,568,-1000,-39,-240,-1000,1000,-464,146,620,524,-837,585,-773,-604,786,446,36,219,-608,-28,791,1000,199,903,1000,-898,340,1000,612,486,56,70,-169,583,589,250,-1000,-84,1000,-1000,-1000,656,601,-510,-282,-585,-964,312,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00806() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{-546,135,-1000,813,-255,-261,-1000,-111,-259,489,789,679,-959,566,309,-417,622,-504,-628,-457,-355,353,247,494,-361,-690,-426,719,-322,-558,973,144,-487,1000,-757,-1000,-63,-659,559,-611,917,631,708,-474,406,-376,-645,-671,-82,-276,-775,1000,-228,-630,492,-661,73,-203,385,-130,-509,1000,787,615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00807() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{-1000,290,93,-362,-121,-85,-1000,-239,1000,824,705,-989,-21,444,-1000,1000,-116,714,-287,-1000,1000,-830,194,485,-560,-1000,755,246,-440,286,848,-944,48,-738,-400,361,-109,-511,-174,-151,1000,329,-133,252,383,507,-752,741,-77,-968,-258,-1000,24,1000,-852,-1000,1000,269,-614,-283,402,-398,381,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00808() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{329,-616,-286,-213,-717,110,327,488,-333,433,59,-233,-638,1000,-356,-383,-1000,-993,-107,965,949,-1000,220,-1000,-150,-51,-88,26,1000,-694,416,-1000,-84,-162,634,-427,-539,462,-985,-169,-1000,-632,-218,1000,597,-682,163,-746,649,462,71,474,-535,48,148,951,144,-430,350,43,-655,-222,-38,-222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00809() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{-256,73,-910,359,714,-246,804,4,-675,-47,-167,6,-74,-175,224,23,-21,-416,822,-656,536,-314,362,644,355,-683,-730,-357,795,473,-365,48,-412,580,-277,9,987,88,-330,928,-622,-239,544,-82,-536,-521,-177,-533,363,297,529,-63,-438,238,746,410,-413,-196,-929,-912,-571,-393,-367,-31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00810() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{1000,-1000,-656,490,-1000,-775,1000,838,-575,1000,711,-1000,1000,-196,-87,736,1000,-963,7,660,-1000,-1000,-1000,-1000,-1000,-1000,491,1000,974,-1000,-369,-161,1000,-614,-512,1000,-689,275,-1000,-841,140,-1000,-1000,472,-1000,-1000,-964,787,-1000,561,-1000,1000,25,833,1000,-582,1000,-1000,611,-545,-705,-1000,-1000,159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00811() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{-549,-114,-636,-38,703,374,-93,328,212,-843,134,184,855,566,-118,186,825,509,-124,-286,227,806,-636,908,-637,-1000,758,674,-601,22,74,158,405,-940,-757,-624,-391,-554,859,478,400,471,260,132,77,-777,-373,-772,-1000,-136,-495,1000,117,400,680,-400,1000,-190,704,-805,-267,-152,438,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00812() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{-343,1000,14,-344,7,-711,-943,-485,1000,370,-712,165,-437,-341,-1000,1000,-1000,1000,519,-265,1000,1000,1000,1000,960,-682,-1000,7,-52,1000,-482,550,-79,355,1000,-1000,144,-554,-990,-601,1000,-934,-65,-1000,629,429,-1000,-438,68,-1000,1000,-1000,699,-1000,-1000,-1000,120,81,-1000,1000,-449,634,-1000,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00813() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{196,-964,-378,-833,52,314,1000,1000,-505,117,-91,556,-353,12,-244,430,-1000,-438,-830,1000,1000,-27,842,378,-341,438,491,-296,1000,170,-343,-172,-610,44,634,-1000,-158,488,-1000,418,-1000,-632,368,799,-19,-238,-438,-1000,979,893,849,453,-510,-15,-695,-582,144,-919,-438,40,-732,-347,-19,175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00814() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{1000,761,-262,321,-343,-118,858,208,-367,-83,731,-451,453,-755,194,-1000,499,1000,1000,1000,-1000,272,511,-230,-334,369,-903,-5,1000,326,-1000,-356,1000,1000,1000,-592,-433,143,913,-727,274,-1000,-1000,-270,-1000,-758,-1000,1000,-1000,63,850,1000,-347,401,1000,1000,-447,-333,-1000,1000,-863,-345,-1000,-986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00815() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getIndexOf(org.jfree.chart.renderer.category.CategoryItemRenderer):int",
            new int[]{1000,-496,743,-397,-314,-265,1000,1000,-247,333,1000,-505,178,-798,-339,-408,-1000,50,1000,1000,129,-509,779,-297,1000,-263,-776,-313,787,197,692,209,912,-898,-145,19,43,1000,-1000,728,-942,-1000,-1000,195,-1000,-808,-975,-806,794,295,842,-292,628,-223,406,-1000,-821,-107,-922,57,-732,-1000,-1000,585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00816() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-1000,773,-328,846,1000,-256,-1000,723,1000,1000,-114,-881,-732,956,1000,168,-389,-960,-519,903,1000,1000,353,787,-718,-1000,-578,901,-1000,1000,-932,176,982,-298,-805,-971,-538,723,426,-1000,610,-1000,-317,-663,-359,814,-997,402,940,183,-1000,-709,96,-1000,1000,1000,-327,1000,440,-747,1000,820,-465,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00817() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-1000,902,-162,1000,644,-453,-358,1000,883,693,-329,-1000,140,1000,-460,243,-1000,1000,836,1000,1000,1000,1000,1000,381,-314,-719,69,-1000,892,-1000,-101,633,-780,1000,-1000,-1000,661,-1000,-780,1000,-1000,-1000,-841,-1000,1000,-1000,284,1000,1000,-613,-1000,1000,-1000,-995,1000,638,1000,1000,1000,872,467,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00818() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-1000,499,-38,631,12,-303,378,1000,1000,-16,-148,-1000,738,1000,-937,-20,-1000,1000,734,875,233,893,1000,1000,476,145,-549,1000,-561,856,-1000,-33,455,-793,544,-1000,-1000,47,-1000,397,935,-815,-627,-819,-1000,1000,-857,-11,1000,1000,-195,-1000,1000,-207,-895,589,-1000,1000,1000,254,298,222,1000,994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00819() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-381,226,-11,-72,-96,208,93,-723,-139,-1000,-427,274,-534,-719,-241,-725,413,-20,-127,182,-527,-1000,-317,-787,52,-219,-538,267,1000,923,-607,-943,520,-223,75,-203,-212,266,278,-19,-615,-257,476,-1000,677,-814,648,-941,-940,-353,-497,689,123,403,-384,78,-556,292,-492,-259,-705,302,-223,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00820() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-258,350,29,107,824,213,1000,-596,-244,-354,-256,-58,-198,-66,-110,-570,830,-100,-838,586,111,-619,-783,163,319,-314,-627,671,-148,1000,-503,-328,764,-630,-590,-393,315,743,604,-33,328,-77,574,-814,846,-1000,-2,-402,-810,-972,-487,338,-461,-443,19,431,308,-255,-1000,-752,426,538,-849,-882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00821() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-391,-178,309,131,-125,230,431,999,503,-481,342,-1000,704,193,-482,-477,-594,500,286,540,-328,1000,338,1000,404,-99,-733,838,212,893,-809,-136,323,-614,-583,-895,-850,-5,-585,327,105,174,-521,-772,-1000,1000,155,-544,1000,580,-195,-633,1000,223,-701,354,721,475,802,771,-285,-198,1000,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00822() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-66,-76,569,-271,-522,553,503,-400,202,-1000,-85,-473,146,-352,-586,-1000,383,286,-31,424,-831,-400,-553,349,206,-19,-646,291,1000,897,-809,-799,374,-1000,-415,-439,140,160,-93,839,-452,652,536,-1000,177,-178,902,-858,57,-281,171,235,698,706,-674,77,558,-428,-248,546,-1000,-36,566,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00823() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-209,-218,-162,-29,391,9,446,882,-181,579,-462,-1000,-208,148,586,-111,-712,658,607,298,267,1000,892,-3,795,113,-851,371,-408,-110,-984,576,-494,69,-910,-1000,-727,220,-1000,236,409,-405,-1000,382,-1000,1000,-293,124,670,881,-675,-658,635,-347,-791,765,32,688,1000,516,679,-491,438,836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00824() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-108,394,-196,962,-209,630,-1000,-1000,177,-277,-88,-69,-1000,-309,537,-455,1000,658,-126,373,695,-60,78,-1000,-60,-635,-419,875,-372,1000,-503,-1000,1000,-1000,175,-356,502,179,-296,-686,-557,-1000,499,-923,882,-705,163,-195,166,-1000,-44,1000,184,-1000,-371,689,92,-462,865,171,881,466,128,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00825() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{1000,-1000,-926,-294,-443,341,588,-83,879,1000,-393,-905,-836,-899,1000,435,416,907,128,-1000,1000,-30,1000,-1000,1000,977,-728,-445,-522,-559,-479,1000,-1000,1000,-937,-653,291,124,-1000,1000,-955,117,-1000,1000,-1000,1000,-24,-1000,-1000,-872,1000,622,-231,-1000,-1000,101,450,-464,403,-513,1000,-786,246,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00826() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{194,-251,63,-67,-96,208,1000,1000,-741,16,-216,-888,697,-269,288,-428,172,1000,800,477,-169,-235,470,-168,1000,456,-795,344,-721,96,-797,122,-361,-223,5,-719,-212,-16,-975,865,279,-257,-685,-759,-995,65,17,-115,1000,159,-237,-1000,437,-854,-1000,1000,-523,164,467,304,253,-456,866,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00827() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-763,730,384,-339,962,124,-280,-165,-260,693,-403,-176,-408,382,994,-968,439,-1000,-954,1000,289,-956,-1000,1000,-1000,-627,-719,69,337,943,-1000,-465,339,-454,-1000,-998,553,1000,920,-504,-449,-601,854,-1000,784,266,308,-360,-707,-1000,-964,-297,-709,378,572,345,-1000,86,-284,-739,310,944,-938,-824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00828() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-61,-101,475,208,432,461,393,-112,876,-782,-276,154,167,-442,-574,-1000,400,6,-299,1000,-1000,-532,-713,70,423,-40,-974,1000,-136,1000,-128,-1000,1000,-1000,-1000,-383,-798,23,-875,1000,-910,-278,1000,-1000,303,-304,433,825,1000,1000,-860,-220,1000,716,1000,-93,301,741,262,-536,-583,-877,920,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00829() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-1000,499,-1000,773,1000,-547,-388,312,625,1000,-844,-980,-247,1000,-676,-402,-1000,-273,-1000,365,740,73,-1000,1000,-1000,-1000,237,1000,963,383,575,835,904,-664,-322,-1000,181,47,618,-1000,935,-815,-693,358,-85,285,58,-987,373,-401,-1000,-250,-621,336,1000,541,-765,753,-535,-1000,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00830() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{142,-478,923,-873,912,1000,1000,489,-629,97,-197,-56,1000,-476,1000,-1000,728,-199,-1000,1000,-1000,-735,-283,668,-344,738,-83,-1000,353,581,-1000,-1000,-676,55,-1000,-758,3,1000,-307,1000,-714,-591,340,-415,-73,-183,967,836,1000,-916,-1000,746,-424,360,351,550,-1000,269,484,-978,629,-861,-927,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00831() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-164,-611,27,-322,940,93,1000,605,-870,490,-813,-921,580,83,544,-883,-180,707,-396,-327,637,-1000,-1000,183,-170,-27,42,977,1000,-783,657,1000,-577,-66,-321,-212,824,584,-69,151,-281,1000,-779,425,-400,-837,1000,-1000,915,76,-685,-561,-797,284,729,541,-1000,-128,-773,-311,435,354,-851,-779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00832() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{-355,747,-1000,517,303,-110,-866,-13,-373,-169,773,-593,567,1000,-325,783,671,-1000,220,530,-71,907,-772,-643,1000,-249,1000,-186,1000,-1000,-1000,-282,1000,412,-429,929,-1000,-828,-103,511,-823,797,478,108,826,-119,-1000,-137,-1000,846,-86,-138,1000,-298,-1000,-1000,-430,388,795,481,-1000,1000,396,-482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00833() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{-761,1000,-27,-863,-665,-249,-579,55,-927,357,1000,-600,841,1000,-29,1000,145,-1000,313,108,219,597,-403,-1000,675,-1000,619,-75,1000,-133,-1000,-1000,1000,-245,208,1000,-374,-1000,-1000,1000,542,641,23,-887,170,-1000,-1000,-166,-1000,1000,305,477,1000,1000,-1000,-695,-453,1000,417,1000,-1000,1000,-1000,-331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00834() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{1000,958,29,780,-773,252,-214,1000,-906,-669,-1000,301,-382,-652,1000,-1000,-920,529,-360,-1000,-43,60,491,-1000,724,1000,-1000,-457,-59,-716,1000,791,-1000,616,105,887,1000,36,278,-1000,719,35,-837,1000,-1000,-54,-683,148,789,-1000,432,929,-941,477,1000,189,-270,680,951,1000,324,39,-260,-468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00835() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{1000,-502,-927,-757,1000,363,-143,-113,-876,784,539,-1000,-682,-648,23,271,-817,-363,-522,-1000,1000,1000,216,497,1000,1000,-603,-962,-350,108,833,646,-1000,57,-611,1000,-260,345,-479,-1000,-1000,-56,-859,-619,-893,517,1000,-1000,507,-987,-1000,-1000,438,-1000,-1000,419,45,-455,-392,-351,420,-66,718,701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00836() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{176,-1000,34,-270,506,550,-228,-1000,-40,119,727,-85,371,-92,423,-81,-1000,1000,-311,791,1000,-1000,856,-10,990,-1000,-1000,1000,226,-23,-1000,-766,-548,-1000,177,368,-692,278,272,390,-848,1000,697,-609,721,-1000,-503,971,-1000,752,206,-1000,1000,819,-688,308,-962,-1000,-77,-411,-879,892,-826,339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00837() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{-1000,928,-611,632,-1000,-407,336,23,533,18,943,1000,-306,1000,40,831,1000,-1000,613,933,-1000,517,-101,699,391,-370,1000,-701,1000,575,-1000,512,1000,1000,475,36,-1000,-566,-1000,1000,-240,-477,-1000,-358,714,629,-1000,356,-708,296,-740,282,-211,-426,-1000,-758,864,673,863,-108,-886,327,541,-15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00838() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{-699,264,234,313,140,-653,459,-999,6,822,562,467,-812,130,923,-78,1000,-1000,-157,-179,-1000,-301,-369,1000,-412,502,322,-964,318,629,447,-2,537,-556,504,-41,-163,-395,-1000,-688,-780,479,-799,764,-524,734,1000,322,360,-494,-932,-173,-605,-739,751,-155,390,-164,710,-523,-558,-934,830,662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00839() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{667,58,234,282,-716,-54,-646,-298,-963,119,105,82,371,200,525,-771,-302,252,-311,-494,929,471,72,-241,1000,-525,-920,-189,226,-465,-685,-466,393,-897,177,368,-934,-799,240,-185,-181,1000,-1000,-160,721,-1000,-636,-220,-277,752,176,-471,567,586,-520,-581,-82,568,632,442,-558,502,78,-629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00840() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{1000,-717,-317,590,866,-9,-906,781,1000,125,-1000,839,16,-154,597,-122,953,152,895,536,-1000,288,729,1000,-854,856,464,-111,652,-107,775,839,-156,1000,-382,-670,-1000,-659,-1000,-1000,-863,-1000,-683,-225,-1000,1000,1000,-52,235,-1000,-424,663,-1000,-1000,-679,-382,1000,-115,487,146,518,-1000,-40,346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00841() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{176,58,234,-779,221,-54,-646,-679,-963,965,1000,-819,-36,-195,-1000,1000,367,-771,985,142,103,-63,-427,1000,885,29,905,-284,318,587,-93,-2,1000,-301,103,-41,-1000,-1000,-988,1000,-1000,-565,-445,-1000,701,157,773,-754,-362,259,-1000,-1000,516,-1000,-1000,-822,390,-398,-254,-1000,-558,939,78,599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00842() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{993,-60,-807,74,-1000,36,1000,506,-1000,920,838,14,-564,499,-46,-1000,684,-867,425,-722,907,-1000,823,-1000,-1000,-505,-83,1000,-218,398,-985,50,-341,22,1000,797,-217,-799,48,-668,381,1000,-1000,1000,-428,-1000,115,11,-654,487,580,-589,732,981,822,114,-1000,-485,677,-1000,-1000,1000,106,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00843() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{235,-228,489,28,940,-1000,-995,1000,702,451,-517,346,-88,-147,557,407,335,-360,-576,-520,-1000,1000,-693,649,-585,763,-158,-1000,279,325,572,148,265,1000,-1000,586,-139,313,-1000,-33,537,-1000,-150,-1000,-1000,1000,387,-887,459,-1000,-363,442,-661,-335,-225,-853,1000,658,343,1000,350,-1000,-399,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00844() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{690,114,-727,-549,-123,926,-159,922,-314,-36,824,646,-110,591,881,584,-196,1,-951,174,-597,-151,979,-400,810,292,-725,-622,-363,157,-72,846,-748,422,815,-733,-100,416,482,-855,788,270,34,114,737,981,-146,-728,-179,-814,83,205,518,-755,-679,104,337,537,-162,353,635,-970,-245,773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00845() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{-10,-278,-771,-195,1000,981,-793,-1000,-243,532,-419,282,524,166,899,-912,-528,37,-71,-227,335,-241,-117,119,-72,811,-269,-147,196,-94,431,-156,-984,24,-1000,-39,1000,498,862,-978,-1000,-288,11,-137,-399,252,96,352,900,-110,535,189,-661,-386,-516,125,224,-251,-546,-351,-197,10,-91,-511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00846() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{-699,-107,-728,-372,-216,596,528,-862,682,-764,-474,367,658,125,-38,29,406,-603,676,667,-393,-529,42,-695,-670,-854,-705,774,609,56,-499,-243,477,-408,-148,-268,595,-287,231,-106,818,108,584,864,514,-934,-454,550,277,105,967,850,-229,932,-492,87,-101,-391,-659,-989,-784,955,-903,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00847() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.PlotOrientation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getOrientation():org.jfree.chart.plot.PlotOrientation",
            new int[]{886,117,-596,404,-871,-37,586,565,-396,582,404,922,29,658,990,-626,773,-420,343,-968,-43,-85,170,-206,-859,289,-781,637,-94,706,-83,-479,-773,786,64,938,349,-326,-358,-994,117,424,-626,789,-837,-435,544,29,180,243,847,-168,-376,850,531,49,-376,366,934,-982,-379,779,-318,179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00848() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{-306,1000,-441,-49,-166,40,1000,-1000,-915,314,-1000,1000,-783,284,-488,-673,707,674,469,30,792,-175,552,1000,409,469,662,-498,-167,130,964,-954,553,-262,39,295,-1000,-206,523,-1000,-899,1000,1000,-136,904,464,-1000,329,-212,1000,1000,895,-749,238,389,-374,416,-698,1000,1000,-8,89,983,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00849() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{1000,-1000,-441,-376,175,-181,-424,1000,197,192,1000,-1000,1000,-1000,652,498,707,-386,-1000,-652,296,1000,-332,371,409,395,662,-498,1000,130,-27,200,-361,585,-1000,-1000,1000,1000,-691,197,94,-752,-200,984,-1000,768,1000,207,1000,1000,-400,-626,559,343,1000,503,-1000,-698,-500,-467,-396,-253,-48,447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00850() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{-435,-255,-53,-241,1000,-1000,-1000,-1000,-1000,-1000,-1000,389,-974,79,234,14,1000,-253,683,-1000,651,-814,742,468,1000,-1000,1000,-10,886,1000,887,461,1000,-88,172,27,-1000,-450,-217,-829,271,-125,128,125,-296,611,542,290,340,1000,789,828,-1000,-90,899,-2,350,147,843,1000,19,-581,-236,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00851() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{1000,-1000,699,-153,-221,-213,-836,35,266,-329,924,-1000,1000,-1000,419,1000,-258,-1000,-1000,-231,102,1000,181,371,146,930,-1000,574,749,-91,-205,-455,-1000,-564,-1000,-767,1000,1000,-668,777,-384,-1000,-663,1000,-1000,538,1000,-281,874,400,-170,-529,936,40,866,315,-1000,176,-390,-1000,-492,279,-48,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00852() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{1000,-578,699,370,1000,-418,-1000,35,-1000,-593,-489,-806,1000,-863,712,1000,-527,-884,-287,-1000,-1000,181,-689,-806,146,930,-810,1000,1000,1000,1000,-496,1000,-564,77,-1000,344,1000,-397,919,528,-876,-828,514,-658,764,719,-888,1000,400,-1000,-178,-115,547,159,752,-562,176,-3,-1000,-1000,91,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00853() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{-1000,1000,-236,-544,184,724,776,-1000,-941,1000,-1000,1000,-1000,684,-326,1000,1000,1000,805,-669,102,-1000,335,1000,785,628,1000,-536,-212,666,975,142,816,-717,1000,72,-1000,-1000,1000,-967,-626,1000,1000,-1000,1000,880,1000,-281,-886,1000,1000,1000,-1000,466,274,-1000,876,-1000,1000,1000,767,-605,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00854() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{-1000,756,-762,164,-531,1000,879,-955,-256,1000,33,1000,-1000,389,-315,-697,489,825,1000,1000,-158,-1000,740,-432,940,-1000,-474,-1000,-1000,1000,-418,-503,687,-721,-345,263,1000,-400,314,929,-1000,337,1000,-83,933,-646,581,209,-178,142,1000,1000,-292,-870,-824,-1000,691,18,1000,-929,1000,-174,-387,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00855() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{-306,-255,-53,415,-129,321,600,-439,-915,599,-642,529,304,22,557,-550,1000,321,315,-342,-224,-595,-631,-131,-188,-1000,-605,-226,886,1000,964,-1000,791,-953,793,-469,-1000,29,423,103,-255,-170,130,-42,-296,355,-254,-228,-212,664,360,71,-223,192,-158,410,397,-163,298,285,-654,142,983,6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00856() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{-452,516,183,666,-555,821,787,-817,400,789,-584,1000,-1000,-555,-571,-735,120,-684,980,-27,1000,-284,-1000,1000,-578,-250,674,-324,370,701,-243,1000,-271,-1000,-237,1000,-985,-532,-536,-232,-1000,1000,1000,295,1000,-145,664,1000,968,1000,-1000,1000,-1000,-718,598,-1000,1000,-449,1000,-1000,881,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00857() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{-952,1000,464,-525,1000,-5,283,-1000,-1000,832,-1000,1000,-123,-174,35,-1000,1000,247,-223,-1000,9,-402,-638,1000,169,1000,771,181,605,212,1000,1000,1000,-239,859,-835,-1000,-347,934,-899,397,855,1000,-582,1000,796,-128,973,-37,1000,1000,523,-1000,1000,378,-1000,-115,-1000,710,1000,72,-203,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00858() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{1000,165,-11,-836,-112,-685,1000,614,-645,117,220,138,-452,-176,-510,36,1000,-144,-775,-1000,1000,992,807,371,847,1000,347,362,972,-699,1000,-357,-408,870,-1000,-270,611,523,-1000,-1000,-929,184,853,1000,-968,1000,-163,1000,274,-127,700,600,-234,627,1000,-479,-581,119,849,604,-682,316,910,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00859() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{-452,807,662,1000,1,821,1000,-648,378,278,-1000,-776,-650,-1000,-433,-1000,212,578,656,-176,1000,-213,223,1000,596,-250,693,-23,370,-46,1000,1000,-400,-1000,279,962,-843,-563,-183,-1000,-431,1000,1000,501,-863,-605,-1000,971,-735,1000,980,824,-1000,-718,1000,-432,1000,-856,969,1000,-1000,-1000,349,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00860() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{500,74,174,463,1000,-829,-1000,42,-1000,797,-17,-15,1000,-733,1000,77,-474,-1000,61,-949,-1000,-117,-1000,-264,-1000,1000,-1000,608,1000,1000,1000,-546,1000,-651,1000,-1000,856,902,160,161,1000,-1000,-1000,620,-937,1000,1000,-1000,1000,-299,-527,-1000,20,779,-418,1000,-864,126,-430,-1000,-1000,69,69,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00861() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{-120,686,144,-648,427,63,702,-425,182,628,-469,1000,419,-488,-881,-489,1000,190,-223,44,1000,743,1000,1000,812,1000,936,-144,424,-725,-20,1000,1000,744,-915,610,-106,44,127,-1000,-1000,1000,1000,573,260,1000,-564,1000,-237,293,1000,1000,-351,-88,881,-1000,-115,-1000,710,1000,532,27,922,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00862() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{798,-498,-56,-162,-544,-475,-241,-305,400,-367,-210,-1000,244,-39,452,804,-150,-560,-117,-1000,-283,443,-295,-862,156,330,693,334,346,971,198,-671,-163,-9,-246,-285,753,956,-227,1000,5,-944,-342,860,-566,480,979,85,-79,-249,-438,-69,919,447,1000,-43,-713,422,-152,-1000,-1000,747,205,-597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00863() {
        org.junit.Assert.assertEquals("java.lang.String:Q2F0ZWdvcnkgUGxvdA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getPlotType():java.lang.String",
            new int[]{775,101,-568,10,-445,-95,850,351,-879,-593,-607,-594,56,368,-38,646,162,26,-287,-504,286,181,285,734,284,107,-197,38,-335,536,69,757,362,742,-461,-450,344,659,-226,51,-532,-147,403,262,-917,365,-107,-504,183,400,258,770,-115,-211,752,33,-562,143,543,-626,80,91,760,289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00864() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-78,525,13,1000,153,222,-471,346,-1000,-56,-124,1000,-1000,767,-331,1000,639,43,857,-16,724,862,-1000,-58,-971,825,-21,780,1000,-554,-60,-199,304,813,-428,-5,-1000,666,779,580,-1000,475,1000,466,890,-137,-417,-796,279,1000,636,887,-672,-1000,465,-488,427,-880,1000,-1000,444,-1000,433,898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00865() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-165,-974,-381,-508,450,594,-574,-470,-675,1000,421,-145,619,38,-14,-561,-435,-493,-820,-317,388,1000,228,-1000,580,538,81,656,-613,390,204,-54,-815,284,-558,491,443,215,-550,681,48,1000,366,-163,-826,-854,164,747,282,-490,-293,-1000,-318,275,366,325,908,773,874,120,-239,513,761,-640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00866() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-217,1000,-541,-646,22,85,745,-430,391,181,-71,7,487,-168,656,-1000,290,-76,353,-517,-311,-40,598,-318,571,985,43,-245,922,466,-398,404,297,510,115,-96,624,-43,-236,51,-535,468,-627,-1000,-47,-791,-1000,-348,-578,951,490,-524,533,-1000,559,-1000,929,760,556,743,161,-10,-57,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00867() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-674,1000,619,1000,262,535,-635,941,-1000,-844,-601,1000,-1000,1000,-781,1000,1000,-392,1000,468,348,788,-1000,776,-1000,1000,684,821,1000,-441,-60,107,1000,1000,-932,163,-1000,1000,-30,603,-1000,1000,807,504,-549,-738,-1000,-1000,553,1000,1000,1000,-232,-1000,-804,-1000,-332,-1000,774,-1000,441,-1000,487,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00868() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{464,-285,-1000,189,292,560,1000,-180,179,674,-54,-655,-680,-443,1000,-349,-812,674,150,-227,711,1000,-974,-137,526,1000,-1000,14,-421,-165,-793,-484,110,-148,-671,-514,349,-778,-600,183,-34,927,-298,781,490,-1000,-592,-1000,-212,382,344,-1000,-814,-1000,720,-570,976,-865,302,898,-55,417,1000,-18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00869() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{470,185,-19,-382,-1000,-874,-1000,-22,-1000,379,-272,311,-492,1000,-232,-24,259,-734,-38,350,1000,1000,-60,20,292,596,-224,1000,98,-654,-296,1000,171,780,328,1000,1000,612,-731,1000,-62,464,1000,-804,-578,437,209,1000,166,-191,102,1000,81,146,306,-229,726,-882,970,-301,1000,173,942,-595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00870() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-658,787,133,1000,-572,82,-1000,338,-1000,-241,-444,408,-533,1000,-553,1000,391,-1000,334,480,309,813,-613,192,-40,1000,695,936,1000,38,-269,747,948,830,-142,1000,-247,999,-1000,942,-888,1000,547,-613,-407,-508,-496,214,671,1000,654,1000,881,-737,-377,-712,-116,-111,677,-704,364,-963,643,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00871() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{563,-854,-937,-722,8,-88,260,10,947,69,-155,-557,-137,-93,702,128,142,-109,-722,-991,-395,-514,-629,975,425,31,-815,53,-322,-256,-668,916,-365,-177,520,-177,694,-982,818,-933,842,88,-405,527,-523,-900,494,56,488,-346,-864,-971,-404,821,630,675,145,-193,-554,525,-251,214,-313,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00872() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-905,2,749,1000,-420,-11,-1000,105,-591,-38,-216,1000,-941,1000,-1000,1000,318,-1000,-156,1000,29,937,-466,-21,-691,630,594,751,730,-631,447,704,938,1000,100,825,103,988,-950,655,-749,1000,1000,-96,-631,-223,285,1000,1000,1000,521,1000,601,80,111,236,-262,1000,362,-216,101,-1000,973,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00873() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-402,-274,-151,539,-425,1000,-654,-281,673,521,-32,829,-340,935,-303,-232,90,-825,-1000,203,-108,440,-327,-729,92,226,-423,322,-592,-661,-233,303,37,876,-169,603,915,280,-1000,-93,122,286,407,390,-254,-251,164,746,634,17,-372,742,84,275,-677,856,188,986,-519,-824,-270,394,359,276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00874() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-1000,44,-281,-382,-1000,457,-1000,235,82,376,-145,-463,905,131,-789,462,-45,-1000,-200,448,1000,1000,-53,-579,1000,814,1000,607,289,1000,-377,1000,285,504,-1000,1000,-329,1000,-1000,644,-601,-322,-1000,-1000,-808,486,-62,533,1000,701,521,-219,1000,-126,-644,1000,726,584,355,-74,-1000,-596,416,-718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00875() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-658,717,579,1000,-379,564,-514,-107,-548,-209,-266,93,-896,1000,-584,1000,-576,-198,232,195,-172,1000,-589,-375,14,128,695,921,98,8,-347,1000,1000,983,-30,402,1000,687,-1000,1000,-336,1000,415,-613,-668,-1000,-480,1000,571,348,762,509,1000,-701,-1000,-1000,334,206,60,-1000,518,173,1000,-770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00876() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-379,752,-366,245,1000,115,-488,681,-1000,517,-374,737,65,446,-413,776,587,-111,478,554,663,-244,-395,127,-482,552,-21,725,1000,144,-230,-489,313,575,-26,427,-1000,1000,-369,56,-1000,1000,561,459,-663,-1000,-186,-317,772,1000,470,345,-320,-700,-52,-76,-207,-509,1000,-282,-602,-1000,-725,832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00877() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{21,513,779,-84,512,-36,126,555,-1000,191,-629,1000,578,1000,-1000,462,1000,-441,-278,872,1000,-354,-1000,822,-1000,1000,-1000,1000,1000,551,-573,-489,1000,1000,679,39,170,636,-246,-68,-969,1000,1000,1000,-524,-1000,-1000,-317,596,1000,212,1000,-876,-1000,-692,-298,-59,-632,547,-1000,-720,-1000,276,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00878() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{1000,-375,206,-254,52,210,581,121,-197,-790,-108,-301,-616,-167,1000,170,67,305,27,-369,47,573,-479,280,-417,926,-462,-367,1000,-202,393,501,-804,74,479,-644,-58,-582,969,132,-123,201,1000,566,191,34,373,-775,-46,-397,254,1000,-762,73,1000,-396,614,-876,-187,363,31,-33,310,699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00879() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-124,-440,-652,-72,52,5,41,111,121,31,-146,85,-436,165,392,758,291,-63,-248,949,-59,-101,-898,665,6,269,-577,271,369,-345,-486,582,-164,120,236,-125,-57,919,-920,-479,120,204,451,509,-99,-671,221,-200,879,937,-414,-413,196,-391,581,326,230,-275,209,-302,-43,-660,-89,773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00880() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{134,225,-1000,923,-1000,-298,-421,-975,-215,-671,1000,-205,-896,328,-551,-257,1000,1000,-271,164,-1000,-453,448,-1000,1000,409,847,1000,-1000,551,-1000,725,651,-1000,1000,1000,-243,621,-1000,1000,-354,-599,-1000,-640,569,-892,-344,1000,26,295,986,-461,1000,-1000,167,-477,1000,-28,-516,-401,-1000,-1000,-883,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00881() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-713,1000,-782,866,1000,-406,-263,268,214,104,-507,-554,194,-752,-1000,1000,674,652,-978,-340,122,1000,-831,-1000,1000,-59,-83,469,-429,994,1000,-18,1000,110,-810,-212,124,268,-1000,-864,-817,1000,3,1000,-268,1000,572,-364,-157,-666,258,-481,-225,1000,-542,10,-898,-203,-827,538,-215,1000,1000,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00882() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-291,969,-55,558,1000,-1000,162,268,214,356,1000,-525,420,432,-745,1000,674,446,-1000,-603,-174,1000,-942,-710,1000,-514,-133,229,-789,904,1000,330,1000,269,-1000,-315,538,-1000,-1000,-1000,-935,1000,-117,1000,-451,1000,62,-391,-173,-871,-147,-195,-225,513,-488,213,-1000,-627,-882,1000,361,1000,1000,694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00883() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-291,-881,-295,-1000,-989,1000,-1000,-578,679,974,1000,-704,1000,432,4,-1000,270,-1000,-690,1000,-215,-718,690,513,-1000,704,-213,-1000,835,-703,724,330,-698,948,-298,-315,-1000,-1000,1000,-565,-128,-1000,-773,-22,-451,-514,62,-1000,-1000,1000,341,-749,-387,513,6,983,-757,1000,825,-166,233,764,828,282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00884() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-1000,-839,-244,-1000,-567,164,-604,61,29,590,1000,-389,1000,-509,-369,-560,677,-1000,-537,1000,-84,-926,89,554,-891,-342,-441,-1000,773,-1000,1000,98,-702,514,-374,-546,-570,-1000,575,-528,-524,-1000,-1000,1000,-242,-431,-78,-911,-617,1000,862,-986,91,-1000,400,590,-1000,874,-250,497,188,655,488,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00885() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-200,150,-967,23,3,-845,71,671,-351,159,357,-205,163,-840,-827,429,736,174,-266,139,-978,-41,-171,-805,359,-522,94,171,-500,56,-124,198,349,-968,-89,335,-6,-578,-521,531,-664,-1000,-870,50,-173,-138,-532,182,216,154,840,-463,1000,-390,47,-290,-283,-119,-859,1000,-1000,261,-117,-698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00886() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{891,-1000,124,-1000,195,-881,846,1000,792,-696,501,1000,-1000,-1000,787,-131,920,-29,-925,-704,-1000,26,1000,1000,-1000,-1000,-192,546,-1000,-618,-561,694,-1000,427,-506,1000,-989,-545,-298,1000,241,-309,-704,-274,274,-1000,692,-1000,-1000,1000,844,-1000,-1000,-1000,-1000,-731,-674,853,-955,1000,-195,-1000,1000,-24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00887() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-1000,-1000,294,-885,-1000,99,-658,-615,480,974,812,-919,1000,-391,-513,-868,749,219,-600,1000,-615,-718,1000,1000,-1000,360,623,117,1000,-1000,52,787,-911,482,839,13,184,-343,-528,448,3,-1000,-889,-378,-1000,-479,-534,-72,-853,1000,778,-321,321,-758,6,983,-136,903,164,-1000,-187,663,828,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00888() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{481,-780,-683,-938,-682,-443,-1000,1000,1000,-649,786,-777,-966,-43,-1000,153,1000,1000,-808,204,-962,38,1000,-651,-1000,11,1000,1000,159,-77,-1000,1000,189,-873,1000,1000,-558,-196,-1000,1000,-1000,-276,-939,772,-872,-55,205,234,-661,786,964,724,1000,-1000,205,-1000,283,676,438,-418,-1000,-355,-379,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00889() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-1000,-1000,-311,-484,-567,199,-1000,-684,200,1000,812,-1000,1000,-450,-369,-1000,926,-177,-793,1000,-1000,-479,1000,157,-1000,980,826,-400,773,-1000,1000,1000,-134,514,609,310,490,-862,-825,434,-848,-1000,-880,893,-1000,230,-380,-239,-585,1000,1000,-986,1000,-525,923,464,-949,1000,393,-1000,188,655,-261,-438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00890() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-867,-1000,-341,1000,-236,-245,-60,-400,1000,656,-844,-1000,375,366,33,758,1000,1000,-620,421,-911,-523,-363,-36,-344,1000,1000,1000,784,-588,46,1000,189,-840,1000,1000,491,-930,-1000,360,-872,664,-889,191,-1000,593,-331,688,-1000,493,-59,743,1000,1000,1000,-978,644,189,-588,-329,-401,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00891() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-722,-899,-244,1000,493,-759,164,1000,6,-525,-1000,-996,52,-846,-1000,1000,344,727,-1000,-157,71,735,-42,-29,695,-116,-49,370,-405,280,932,147,-189,633,-754,-112,-408,-721,-741,310,-619,664,-518,284,-91,673,1000,894,-1000,-414,461,-529,29,282,-572,409,-242,221,-1000,-1000,128,1000,146,414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00892() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-200,-252,-421,-557,-1000,147,-738,-964,676,605,732,-79,508,10,956,-1000,920,742,-22,1000,-1000,-768,1000,731,-1000,1000,1000,713,1000,-738,-1000,1000,-1000,-968,1000,223,914,-578,-1000,926,18,-1000,-1000,50,-744,-1000,-689,593,-188,1000,1000,-36,1000,-1000,983,-686,250,361,969,-355,-685,92,643,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00893() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{234,158,-486,-285,943,-1000,1000,538,59,11,449,304,-696,-468,656,1000,784,774,-107,-1000,-1000,-546,64,-56,709,-1000,-305,726,-1000,492,-221,256,-161,-814,41,1000,-15,-77,-696,659,167,1000,-879,251,-102,-1000,709,67,-183,-50,-219,660,322,-520,-204,-949,607,-971,-942,1000,-108,238,874,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00894() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-333,33,-515,-971,221,-52,-700,-900,486,828,732,79,508,-467,-594,248,-375,184,536,937,954,438,-796,-359,920,-928,352,714,355,656,-875,-829,-948,-194,-972,-461,323,92,393,926,136,-909,-909,-481,687,-779,-870,593,899,46,793,-263,252,-461,313,-467,-374,-563,-253,-831,-654,-736,643,616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00895() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-200,-641,-1000,-316,-796,-872,-263,246,437,38,723,-262,-646,200,-61,274,784,443,-798,396,-1000,-705,1000,-669,-597,59,474,759,0,501,-408,956,-223,-471,311,940,-8,-302,-611,1000,-384,-677,-1000,980,-347,-355,-624,-275,-576,786,645,-398,1000,19,983,-396,-374,201,-431,369,-1000,-3,35,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00896() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTAx", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{-15,835,-1000,1000,410,-771,-908,513,153,-1000,-1000,-936,-805,-1000,-466,455,779,-1000,552,1000,825,998,-827,-183,381,-599,1000,-74,-61,-1000,723,675,-121,-298,834,-49,-1000,-952,-333,-179,558,562,1000,400,-381,-594,104,-768,755,-847,-81,-738,-57,1000,-61,-1000,-913,-809,-1000,1000,-1000,-531,-224,-624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00897() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{147,1000,-72,48,898,-1000,-1000,1000,-486,1000,-934,184,496,-990,117,-839,82,1000,1000,917,-362,957,912,-871,233,-100,1000,-206,-398,-1000,-879,79,-51,-859,-611,-1000,-927,723,-441,-152,-1000,-540,1000,1000,323,1000,-564,122,126,439,-396,-482,-181,-790,1000,15,880,175,-897,-1000,778,-1000,-1000,-256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00898() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{434,-501,-71,-142,596,-1000,-266,436,847,741,372,933,931,-369,-76,-643,317,-497,-316,1000,-159,1000,616,-131,607,475,1000,-328,-1000,-1000,-438,18,177,200,-289,-862,-1000,-950,1000,-190,732,-999,844,133,-1000,-164,-22,-577,543,792,-370,1000,-409,1000,-96,-710,376,-856,532,-532,1000,-715,-81,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00899() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{244,509,-966,429,1000,-865,-1000,770,-551,1000,159,126,746,-1000,-203,-742,-646,940,-314,64,-1000,375,1000,-228,873,746,-141,124,-683,-950,-1000,-400,570,-324,-872,-1000,-42,682,-19,251,-662,-413,227,1000,-5,-478,-233,-251,-504,513,343,-58,42,-887,145,410,-550,-1000,773,-400,1000,-428,-361,355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00900() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{-808,380,-802,245,110,-776,319,-412,886,495,990,-467,277,-993,449,-659,255,-223,975,657,857,829,-784,-489,385,530,952,315,151,-893,-547,192,-131,-16,762,-523,-202,162,522,371,771,-37,292,334,-626,-810,679,74,701,956,83,-271,-647,302,-642,-778,637,52,284,-358,314,-664,-631,7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00901() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{-540,-1000,729,205,-21,-15,687,-275,116,31,-1000,565,-1000,1000,200,1000,-473,-152,-936,371,758,-157,367,856,971,-1000,-101,-795,-670,-24,394,203,-753,219,550,417,-1000,631,220,-359,-46,-89,-596,536,1000,-567,-1000,-745,-260,-1000,821,405,357,-687,115,420,1000,1000,-758,979,553,536,38,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00902() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{177,940,-156,-112,669,298,-26,-311,-777,491,-282,-123,826,-182,-875,115,-277,1000,-265,-1000,-1000,-183,1000,-747,293,1000,-788,136,49,389,-1000,-505,1000,233,-583,-616,1000,335,-529,175,-739,72,941,-82,-691,-433,498,232,-400,784,680,-855,-696,777,469,1000,324,-1000,1000,-638,542,632,471,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00903() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{841,-177,-618,501,339,34,269,-361,198,-4,500,-727,583,-664,-135,267,92,113,400,-74,-44,629,-172,-338,303,-56,389,268,208,762,-68,257,463,260,535,-343,884,-584,-363,-87,414,352,-116,-104,-46,261,366,-402,28,381,189,-606,843,437,-1000,-86,-1000,3,524,197,-396,-142,-146,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00904() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{-139,343,-4,-30,344,-484,-464,326,757,522,314,880,3,-884,-204,-874,-582,514,-575,185,544,-286,39,-122,771,687,210,204,-810,-521,-712,-534,925,-714,-851,-199,-819,115,932,-21,336,-214,-656,994,462,685,-472,174,-119,-12,-38,351,-438,-176,-687,-54,-503,276,-164,-251,522,30,461,643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00905() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{919,-846,778,38,856,951,196,-676,-367,-692,655,483,-748,651,-427,146,549,453,-1000,-540,199,-243,-127,450,958,-398,120,213,-824,1000,250,52,802,-179,-254,284,-109,-724,-365,-1000,-200,232,360,-883,1000,-910,-539,-315,-1000,-946,-219,3,672,-968,378,549,464,1000,-778,376,574,944,923,567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00906() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{-392,507,-433,570,552,-1000,-1000,584,-801,1000,-1000,19,-102,-74,-811,-380,165,1000,1000,1000,512,968,-128,-478,670,-633,-319,-110,-712,-1000,-918,707,-1000,-22,473,-811,-1000,1000,-804,-932,-1000,528,861,1000,26,-1000,-915,211,865,-788,-216,-1000,-149,-615,982,1000,731,-1000,-498,-135,62,-1000,-1000,-66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00907() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{107,-437,-71,326,283,-793,-336,-179,344,434,-887,335,-16,-369,-793,-643,-852,567,-383,534,-320,1000,249,-546,886,475,24,402,-921,-224,-1000,170,-168,-170,-722,-196,-1000,-96,1000,-390,328,140,-691,1000,430,-164,-22,114,-242,792,-671,362,-636,657,-477,-267,-721,-856,-706,390,9,-604,266,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00908() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{-4,13,-388,600,374,-490,-403,-440,-831,192,-1000,-696,-66,393,-180,360,160,996,561,245,215,484,-131,-80,494,-368,-395,2,-175,-196,-918,594,-1000,2,674,-328,-590,450,-894,-962,-787,797,172,1000,280,-822,-544,92,77,-1000,73,-1000,350,-208,527,253,153,1000,303,448,-239,-1000,-891,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00909() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{817,-526,-312,596,701,1000,260,-852,-1000,-843,150,-615,878,-113,-541,1000,247,479,-425,-1000,-1000,223,995,483,41,404,-251,-34,490,1000,-1000,41,996,474,374,-309,1000,-280,-1000,-495,-334,457,-44,-883,1,-387,598,-504,-1000,210,876,-1000,1000,-219,227,1000,-681,-1000,1000,483,-143,1000,503,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00910() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{782,-911,546,-78,454,-284,-646,94,-740,-331,-699,1000,493,1000,-692,-1000,-1000,1000,-994,-230,-1000,937,1000,172,923,240,-747,38,-1000,33,292,-558,-639,1,-1000,-858,-713,394,338,-542,-1000,-675,-791,1000,911,631,-1000,116,-1000,-85,3,-315,566,-1000,191,996,-208,969,284,-63,1000,-495,21,614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00911() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisCount():int",
            new int[]{-428,1000,804,-964,956,-598,-13,-592,820,-21,897,252,-266,503,500,-58,-42,386,171,-45,506,-19,-411,-86,293,444,-922,-194,41,-377,-583,127,-986,-881,978,65,-13,-456,1000,852,96,-209,-711,604,-281,-220,348,697,-280,513,-20,638,288,268,367,-1000,1000,416,486,269,1000,-999,-1000,642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00912() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-93,-1000,654,291,-473,798,273,467,705,-813,495,668,-136,-928,-249,1000,-1000,899,-795,-848,1000,-1000,1000,480,-1000,-958,1000,-1000,-483,48,1000,-31,-96,1000,1000,211,-928,219,-369,1000,-362,1000,832,-521,-1000,-443,1000,-597,-1000,419,161,-885,1000,-584,-1000,-186,-1000,-700,-470,3,580,1000,-1000,514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00913() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{92,749,13,1000,-558,-40,-503,336,-976,-426,495,-356,118,957,-581,594,-318,-834,1000,-475,1000,-260,-228,-400,-643,326,-975,-410,-548,-307,-542,-292,12,-765,914,164,670,-854,-483,-616,-67,587,354,735,-64,-230,-139,-299,24,109,-710,-493,547,-218,-1000,-1000,887,403,-707,-234,1000,-566,-677,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00914() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{617,-1000,-477,-427,120,755,-243,-535,-793,86,-1000,-768,12,-1000,-1000,328,-296,752,351,-165,883,783,1000,-1000,-602,-787,1000,-638,337,746,687,-143,-502,411,420,-252,-843,885,207,704,556,1000,135,969,-150,0,1000,396,-1000,1000,104,690,654,-70,-914,-398,-57,-260,-970,635,532,-259,323,368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00915() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{646,1000,699,824,-1000,-875,728,708,-1000,-76,659,1000,445,1000,1000,327,222,-972,757,332,226,958,-537,-225,278,-136,1000,532,573,-537,-643,523,-287,-1000,243,326,1000,-1000,585,886,-153,609,1000,-362,624,390,158,-165,258,-1000,-944,-801,-1000,-298,786,-417,1000,573,-529,133,-703,1000,-570,205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00916() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{499,-1000,-492,95,191,950,142,-83,-560,587,-1000,-578,789,-928,-567,328,-1000,752,1000,-385,1000,-244,308,-100,-1000,-787,1000,-799,163,746,687,-31,-249,658,-62,211,-935,1000,207,1000,218,908,398,676,-189,-443,595,-765,-655,-147,540,426,846,-348,-1000,177,-865,-958,-817,641,743,1000,-145,668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00917() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{631,-1000,7,411,-143,327,-937,-728,170,-872,-467,-542,321,368,194,132,273,-556,757,-1000,1000,-979,876,509,-1000,284,1000,-248,-77,149,-463,-1000,-122,-1000,1000,-1000,1000,442,280,886,350,-787,62,493,-397,-1000,-1000,-841,-63,-363,-371,976,853,613,-914,64,143,901,-1000,388,-73,336,-22,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00918() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{21,-611,660,689,-833,1000,-1000,204,-537,247,-1000,705,-467,886,881,-46,57,-52,1000,-627,996,594,-209,-904,-143,413,-466,1000,-591,-640,528,1000,-826,-543,54,212,412,-537,-619,-37,-1000,708,815,1000,272,-923,-879,-418,-1000,-1000,-899,-240,406,-1000,-647,-1000,921,-660,-677,1000,1000,558,-138,-240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00919() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{103,-810,-538,-188,849,118,-502,-956,984,-299,-169,-1000,252,-891,-478,-657,-329,-246,-1000,1000,-23,-169,-889,164,148,734,1000,-948,-114,385,-632,-906,677,201,-1000,-1000,-30,-1000,-773,-573,-142,-1000,-496,705,196,780,923,-100,52,299,1000,-816,1000,-1000,-725,-267,-728,1000,-414,205,1000,-876,-1000,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00920() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{1000,-823,-1000,866,1000,1000,-962,-751,-543,-1000,-514,-1000,1000,-217,-1000,13,-80,-791,639,-480,832,1000,573,-987,-1000,187,1000,-746,806,251,-299,-1000,856,-1000,1000,-1000,672,34,156,1000,1000,619,-91,24,725,96,741,301,-1000,-917,1000,1000,136,591,-940,598,-981,413,-1000,1000,673,385,119,5}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00921() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{648,-1000,-636,605,500,716,-1000,-768,-956,-486,-1000,-1000,-1000,206,-850,-30,-229,-724,1000,73,1000,845,-684,-1000,757,298,573,-1000,-1000,-34,-482,89,566,-1000,183,-194,968,-511,-694,1000,597,-155,-237,1000,-139,-230,-202,678,-733,-770,-209,691,868,-490,-1000,-1000,1000,-171,-1000,-271,1000,-1000,-228,-133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00922() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{617,-735,-451,11,84,673,-312,-262,-1000,-159,-467,-1000,219,-869,-1000,498,-106,149,484,-577,883,783,1000,-825,-880,-285,793,-638,323,282,-70,-393,-434,-133,874,-362,-138,552,135,410,759,1000,56,493,0,-1000,1000,201,-1000,767,205,431,654,-52,-914,-398,-28,-102,-896,538,532,-285,81,368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00923() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-36,705,1000,1000,-1000,100,-400,1000,-4,-101,-84,-542,933,368,925,735,273,-1000,757,-1000,667,280,823,59,-1000,-81,-931,1000,809,-996,-45,-199,-1000,-1000,1000,24,1000,72,388,-47,-1000,1000,759,488,1000,-1000,-557,-1000,-1000,-1000,-1000,-1000,-868,-115,-914,-1000,143,85,-543,388,-143,739,-595,-740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00924() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{322,-932,-56,-749,774,-218,410,-1000,1000,-508,-31,-643,944,-679,-1000,558,-171,-329,-750,1000,-304,54,-480,-86,-281,26,635,317,1000,-116,-709,-218,-416,504,-1000,-1000,-173,-1000,-320,768,676,-1000,491,283,160,-240,431,984,-932,1000,282,162,1000,-743,-279,-437,-807,566,-353,714,613,-482,-374,360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00925() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{588,-749,272,-447,-367,1000,-67,-893,196,1000,-1000,333,-209,-1000,-203,201,-799,1000,-986,1000,664,546,449,-1000,590,-424,1000,1000,-135,144,125,1000,-1000,1000,-1000,1000,-1000,890,-512,-619,-941,679,25,1000,-118,738,142,-420,-1000,477,-1000,565,-1000,-73,-337,-913,1000,-1000,-878,702,-130,-401,820,780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00926() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{675,170,269,-99,-172,310,40,582,-1000,1000,634,-786,1000,-888,-195,429,-1000,1000,1000,-53,877,-257,872,-568,-628,-153,164,-1000,-57,460,583,1000,-773,1000,-1000,1000,-1000,-826,936,-696,77,258,-109,1000,243,-1000,1000,-958,-840,1000,43,249,67,-459,-1000,-716,-400,59,-649,-153,375,257,-34,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00927() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{720,-623,822,505,-711,486,-1000,-656,-480,1000,634,38,-576,368,415,708,-271,471,1000,-837,1000,78,530,-455,-997,233,164,-93,-981,-56,389,-300,-1000,-780,282,642,-213,-826,-683,-163,-1000,-514,-123,1000,1000,-893,1000,-913,673,-721,-978,249,64,511,-1000,-538,1000,-18,-966,115,-279,249,597,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00928() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-324,-797,24,1000,1000,129,755,-296,1000,-491,802,-501,1000,290,336,1000,-985,-1000,682,-1000,-977,784,-178,-1000,-800,-363,-1000,-272,1000,778,51,-281,-1000,89,-439,94,-1000,141,410,268,1000,437,1000,-942,-151,-90,-783,584,939,443,783,884,1000,1000,-1000,-41,1000,333,1000,-1000,1000,325,1000,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00929() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{801,-415,958,-881,242,354,477,-845,-556,-846,-509,-63,-170,949,656,-357,185,-591,-591,-260,-1000,-23,1000,-212,1000,743,-1000,817,1000,577,-523,-381,-1000,-638,-27,141,-761,-609,-1000,-667,572,-922,584,-274,-861,-696,20,245,-334,-889,678,648,470,746,-878,-854,661,1000,157,91,1000,256,-115,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00930() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-297,1000,64,351,93,-283,-371,-875,18,413,-761,165,-599,-846,-1000,-994,-570,1000,-378,1000,491,-1000,1000,235,-523,634,739,644,-655,-853,602,1000,1000,-911,143,1000,-905,300,1000,-93,1000,-1000,-979,153,-113,-761,898,-474,-120,-35,607,721,-620,573,1000,566,-576,328,-1000,1000,-346,1000,-942,808}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00931() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{290,-955,100,-193,-1000,440,853,-556,213,-879,1000,584,285,559,563,-22,693,-1000,931,-670,-691,531,739,-484,1000,334,-207,1000,1000,372,-646,-604,-1000,735,889,-283,-867,-359,-302,480,-319,611,1000,487,-1000,32,260,-446,-399,-1000,871,-551,1000,1000,-1000,-924,1000,510,1000,200,578,-76,186,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00932() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{728,58,479,-351,-1000,-451,1000,-476,-368,-437,1000,-128,-479,-286,616,-450,634,-413,485,-463,-54,716,1000,419,617,1000,166,1000,-9,-851,-841,143,-427,396,710,-191,-1000,-1000,311,831,-139,-573,-18,769,-665,-1000,608,-1000,-910,-1000,1000,-307,1000,1000,-138,-640,1000,1000,243,-786,-412,541,-794,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00933() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-693,-1000,994,-239,242,631,-80,684,-556,-943,517,47,935,734,-217,-664,1000,-1000,1000,283,201,-398,1000,-1000,-873,-880,210,817,350,804,5,-533,-430,-638,-743,1000,-786,1000,-863,-384,-597,-922,884,-555,-129,1000,-398,1000,1000,1000,-375,657,-993,620,-1000,-122,-224,-988,1000,-1000,1000,312,1000,967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00934() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-806,234,94,578,341,-608,181,-741,1000,-397,-47,1000,612,-1000,-1000,-249,-185,138,1000,655,746,327,739,188,-1000,-140,-145,-144,-956,-1000,56,1000,716,1000,-323,1000,-1000,300,395,914,-77,-239,-150,-647,174,-1000,856,-420,-295,744,475,563,170,937,-253,417,132,51,-95,-1000,25,1000,234,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00935() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-825,630,-451,-590,-533,811,-1000,-378,450,40,668,827,-150,-930,-321,-71,31,-1000,546,-1000,-787,-186,408,-444,1000,722,306,1000,35,0,769,288,-1000,-168,436,-319,-748,423,879,458,-311,-439,313,423,-645,-862,25,-589,-517,303,1000,-1000,759,1000,-823,-263,1000,980,510,717,583,-116,922,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00936() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-288,-832,94,-292,135,82,719,-696,-843,-1000,2,1000,612,-233,-321,-913,206,-449,700,294,81,-398,861,-1000,1000,198,-120,921,400,-781,-197,-533,36,-152,1000,-293,-633,-302,-1000,1000,-1000,-170,378,-804,-391,-1000,716,-281,1000,-116,627,245,400,993,-253,-122,645,1000,540,-1000,25,948,546,556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00937() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,-1000,389,174,512,214,89,636,298,-1000,1000,791,1000,-94,-901,-1000,1000,-901,1000,924,958,252,-1000,-608,-873,-1000,600,-872,-630,-300,-55,232,295,1000,-25,111,-884,807,-863,783,-1000,1000,739,-926,201,787,89,250,599,1000,-411,375,-1000,798,-563,181,-235,50,1000,-1000,376,833,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00938() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,-510,739,32,-745,468,1000,684,527,-663,652,-1000,678,118,558,588,880,-589,1000,-758,425,-88,-259,621,-103,274,128,-201,580,-120,258,-799,-500,1000,-238,-434,-1000,-568,28,416,-513,490,631,-119,24,129,-587,170,505,573,-108,313,839,883,-396,-542,641,44,555,-570,694,-15,683,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00939() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,-832,119,151,671,170,-37,-273,-105,-986,639,1000,1000,-419,-1000,-1000,1000,-537,1000,1000,845,218,742,-58,-197,-149,569,680,-824,-1000,-239,367,426,1000,-226,-417,-321,-764,-904,474,-1000,396,1000,-862,72,99,1000,-826,357,706,-111,180,-41,771,-253,-19,-212,-345,1000,-1000,25,1000,546,49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00940() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,61,46,1000,683,1000,24,3,1000,-827,-495,-129,1000,-141,-342,371,-1000,-1000,867,-1000,-591,211,627,-1000,-1000,499,-692,571,1000,33,919,27,-1000,439,1000,643,-941,857,874,393,1000,-554,1000,-569,-166,-610,-260,436,494,775,94,260,1000,781,-1000,370,1000,594,1000,-593,470,536,231,850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00941() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,89,527,-439,-436,-1000,246,-1000,-81,-148,934,47,71,-1000,-958,-576,915,224,1000,1000,388,638,1000,892,-20,112,-216,1000,-1000,-1000,-454,1000,915,433,-142,-716,-627,-1000,-122,493,-110,1000,-378,-245,26,-1000,1000,-934,-706,-379,1000,908,11,1000,755,-86,18,880,1000,533,-306,1000,-503,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00942() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{642,-187,-556,296,187,634,-25,-507,17,984,-989,-290,352,456,383,-524,992,164,-269,-264,-813,349,690,855,-453,811,493,359,160,562,931,-499,228,178,229,459,-946,-792,-745,524,12,-323,-270,983,249,-734,602,565,-697,51,-387,317,-444,804,213,526,512,848,-540,962,-748,-822,295,-914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00943() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-526,302,-301,-1000,850,239,468,131,-16,-765,695,-1000,593,669,547,-173,915,-1000,922,263,433,1000,-156,-1000,659,-408,384,-456,242,758,-268,-961,-701,1000,-284,165,-405,-970,-595,219,1000,-246,1000,358,-1000,1000,-1000,-863,443,-296,394,356,413,-35,-1000,-1000,-809,-1000,1000,-626,376,-375,703,-827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00944() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-254,791,-32,1000,-1000,203,471,764,-1000,314,480,86,-294,323,107,-394,-424,349,-15,1000,581,182,-720,1000,-754,1000,607,-118,1000,17,-699,22,-955,-968,-1000,-950,101,-1000,-628,-337,188,-522,-925,102,-145,-231,-1000,22,528,-263,275,656,-1000,136,1000,989,-1000,659,293,-127,-308,-558,1000,398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00945() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-580,1000,312,794,316,-1000,-780,1000,-991,-30,527,363,553,1000,-752,535,280,318,531,743,1000,-1000,-1000,-403,1000,-845,26,631,-18,408,755,-950,-225,-703,853,565,325,-585,1000,-1000,349,1000,-928,-1000,660,1000,-911,1000,-451,-1000,-77,-57,898,450,-1000,-437,-1000,943,1000,1000,1000,-1000,224,-856}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00946() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-165,101,19,653,-236,-680,1000,654,-1000,415,-673,604,572,85,484,84,456,-303,-136,-360,411,347,-422,401,82,330,220,-158,827,489,-1000,403,-67,-792,-1000,-712,-328,372,-1000,82,-642,-1000,-68,-296,-363,309,-1000,-1000,193,496,570,483,-634,-280,-982,195,-394,214,-675,440,-298,-409,375,822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00947() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{990,377,896,-143,237,235,117,340,-287,-1000,16,-526,77,918,-450,-193,-164,-699,324,1000,590,-826,-280,592,1000,47,138,737,257,-423,-1000,-1000,-16,-117,1000,-381,1000,319,5,-1000,-44,-763,-976,-177,-1000,1000,-581,903,564,-79,-895,-541,-1000,-86,-790,73,-127,794,-166,389,281,544,772,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00948() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-290,-980,-646,697,829,-260,810,-26,-34,-709,-952,872,-68,546,76,346,723,104,629,-12,895,-17,383,274,393,-776,-738,-40,-70,-517,-566,628,897,-929,532,43,-713,-691,-864,-323,-886,-434,-374,-468,-559,886,-881,-779,259,731,426,146,188,-974,-308,-397,-725,447,-300,-397,967,-323,-295,-647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00949() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{580,1000,403,1000,-1000,-113,-412,429,-1000,78,117,15,219,296,-444,113,-632,1000,-278,1000,-111,742,-1000,223,16,1000,1000,-197,824,-721,-1000,-669,-1000,-1000,-1000,-799,603,69,-430,566,857,-524,-1000,516,-224,-1000,-911,614,1000,-1000,903,1000,72,665,319,99,189,-526,-248,-890,106,733,667,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00950() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{1000,998,429,25,152,-990,-1000,-8,-803,-398,-441,611,780,584,-358,1000,438,463,255,372,-159,-262,-256,-758,1000,-45,-862,307,-130,-171,179,-1000,242,173,-451,314,678,578,-864,214,-485,663,39,-55,-881,-326,782,784,963,-916,737,624,1000,301,-1000,347,911,-554,-398,320,895,704,-251,-145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00951() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{863,-67,586,1000,-758,387,970,-805,-810,723,-740,577,219,318,-196,-405,478,1000,-1000,460,440,995,-1000,1000,-781,151,-248,168,474,-721,-1000,179,-1000,-1000,-99,-239,-343,892,-1000,606,40,1000,-928,821,-1000,-1000,-911,212,1000,1000,1000,1000,-1000,325,319,287,-178,-768,-1000,-1000,668,417,224,-856}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00952() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{1000,282,138,859,-232,-299,-272,-267,129,209,-478,594,-1000,346,-377,287,-440,919,-1000,518,-132,1000,-977,459,686,1000,951,-906,677,465,-1000,130,-1000,-1000,-872,-302,1000,507,817,1000,813,884,-720,395,-845,-533,253,-637,789,-475,669,520,-54,545,242,-65,59,-1000,140,-1000,688,862,-350,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00953() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-1000,955,-758,181,-580,28,492,1000,-1000,-296,523,-837,-80,70,907,245,-861,-1000,881,690,212,-630,539,1000,-335,824,815,-347,1000,454,-95,140,319,-31,-1000,-1000,682,-1000,-269,-844,-38,-1000,56,-397,918,327,-622,-819,249,-263,193,272,-650,-775,524,546,-585,1000,313,882,-1000,-854,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00954() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-993,14,78,-381,-141,1000,1000,-99,-739,-407,-303,-400,-280,-263,400,-41,-880,-400,267,162,61,-1000,400,-717,1000,34,31,312,753,1000,224,-619,668,400,-1000,171,126,-65,-154,1000,-143,342,1000,110,1000,447,-1000,-1000,581,400,-347,1000,-1000,-58,-397,30,439,817,-53,892,-267,-269,698,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00955() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-165,367,47,653,23,226,1000,-994,-1000,-90,-701,604,525,464,484,-87,396,-104,705,865,129,-503,-377,968,-318,330,275,-158,649,410,-1000,-294,-237,-792,-1000,-577,-384,-1000,-1000,-913,-284,-1000,680,-321,-363,357,-705,-1000,724,735,-5,730,-1000,-335,469,43,415,214,-862,440,-298,-7,510,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00956() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-932,713,-1000,-176,-192,610,-529,-406,-274,519,169,-723,35,366,758,163,-896,-646,60,1000,-1000,267,595,114,-253,102,359,-1000,362,-1000,1000,274,428,854,209,-456,249,-701,303,347,1000,686,678,-469,1000,-1000,1000,-1000,982,478,236,712,-68,-104,-999,-680,1000,-650,961,-714,-4,945,216,-793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00957() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-537,124,-491,1000,-1000,553,641,-1000,-946,1000,-788,963,461,-49,488,-1000,694,1000,-856,1000,-257,-317,-1000,618,-1000,827,504,-1000,1000,-1000,-847,1000,-1000,-1000,-822,-579,-1000,-508,-842,1000,674,884,-295,313,-1000,-1000,-1000,-1000,1000,658,1000,1000,-1000,1000,320,208,-185,-454,-888,-1000,359,-89,933,-88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00958() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{1000,-302,838,313,-85,-65,539,-842,-713,174,-1000,1000,-308,365,-1000,361,285,400,-1000,1000,581,618,-608,805,497,-207,-188,327,124,-40,-1000,-308,-434,-261,848,-44,420,1000,-49,443,153,-101,-925,124,-64,-231,22,697,950,795,-109,656,-647,251,17,696,-1000,-848,-894,-620,834,1000,-225,-454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00959() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-930,167,-451,-170,-159,136,1000,-69,-1000,-148,-679,-743,525,-43,947,509,-146,-1000,281,18,-88,-165,798,1000,-182,-101,50,-158,526,-96,-137,346,819,339,-748,-845,486,304,-487,-118,-284,-285,474,108,519,-203,-370,-989,601,1000,431,451,-500,-977,-361,-346,415,214,-971,440,-464,-7,265,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00960() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-666,265,-393,-94,237,-1000,346,827,-837,602,-505,-1000,-66,-206,155,1000,880,573,-106,-1000,376,14,421,-426,-261,-814,-925,552,-303,20,1000,-277,198,-13,706,957,999,-192,764,1000,645,-927,34,-1000,-1000,-55,-208,440,-63,283,-374,-1000,808,29,-137,-321,-1000,-1000,-897,-738,-1000,779,-664,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00961() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{424,998,174,-214,312,-1000,-309,1000,-400,508,-227,771,-271,-252,22,-59,617,947,1000,-1000,-404,-183,773,-104,-461,-693,340,-7,-235,1000,433,-1000,190,-460,-145,881,-1000,-241,274,-384,-464,-77,218,493,-400,-940,266,231,-400,186,-288,847,-133,-602,-670,212,244,-111,415,255,400,219,-107,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00962() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{1000,643,537,791,-149,-391,575,1000,-316,849,1000,-412,629,-977,990,-400,1000,573,259,-1000,391,89,-814,-527,887,-592,-119,1000,-411,926,898,-929,-370,-70,431,376,1000,-1000,-627,404,114,-690,-90,-556,-774,963,154,-98,70,-191,-450,-1000,382,370,830,-465,-321,-601,-877,484,-1000,1000,199,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00963() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-1000,75,-1000,158,1000,-1000,-286,1000,274,1000,-1000,-1000,-1000,112,-626,1000,1000,799,-1000,-575,1000,-431,25,-1000,-62,-422,-1000,-416,-1000,677,1000,1000,1000,1000,1000,459,1000,197,1000,1000,601,-1000,147,-1000,-1000,654,288,319,-1000,1000,152,-1000,1000,594,-1000,-130,-1000,-835,-1000,-1000,-900,395,-1000,-607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00964() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{873,1000,529,-229,-1000,-464,405,-507,-365,358,179,1000,1000,1000,-1000,-978,198,1000,1000,403,521,1000,753,401,1000,-1000,44,251,348,-1000,-641,958,-283,986,273,-994,-1000,-363,-959,-1000,-658,645,1000,1000,1000,-336,-504,-1000,-983,1000,-1000,1000,-1000,-1000,-916,1000,132,-1000,854,13,-444,1000,1000,314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00965() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{1000,971,635,677,-1000,450,697,479,925,-554,1000,913,629,-975,1000,-909,291,574,82,774,755,-126,62,-17,-137,-511,1000,1000,90,604,898,1000,-370,17,-742,-987,466,-804,-723,-544,-217,-756,-118,-176,1000,-599,-358,-368,1000,-198,-450,537,-844,68,1000,-284,946,-255,692,530,-1000,1000,317,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00966() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{956,1000,579,1000,-639,-88,677,-299,1000,987,1000,1000,901,-159,1000,-1000,926,1000,1000,1000,1000,870,-699,-734,1000,-605,212,-678,-664,1000,310,1000,126,1000,150,-170,548,-1000,-835,-816,109,-462,832,-7,590,661,-321,-1000,-747,826,255,-258,-404,-438,-1000,918,-29,-1000,335,963,-640,909,-435,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00967() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-719,379,529,-465,581,-1000,172,-507,-365,854,179,-592,6,95,-757,259,1000,1000,1000,-355,1000,630,1000,-381,-554,-958,-885,-1000,-252,-462,-641,232,888,367,1000,0,371,268,1000,61,339,-344,1000,-329,-1000,-662,-558,-594,-1000,1000,-834,-380,808,-788,-1000,577,-1000,-1000,-561,-296,209,339,-156,-998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00968() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-313,-1000,-732,145,891,-786,-598,-1000,113,642,-1000,-284,-485,1000,-640,-69,354,-127,367,240,715,-659,-441,-1000,-537,-534,-704,-1000,-409,-502,565,67,404,-212,-173,636,225,1000,-344,542,89,-734,-1000,-271,-934,-978,530,1000,936,-115,781,-224,-137,71,-59,703,-1000,540,-1000,-1000,-447,-1000,53,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00969() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{929,973,-1000,-380,-146,-445,364,874,19,-202,483,851,275,421,353,-548,956,1000,1000,664,1000,-14,525,32,-451,-918,518,23,-1000,1000,201,1000,326,1000,-182,469,-432,-806,112,-938,-87,-176,1000,182,510,-436,-255,-1000,-563,582,-299,588,-953,-925,-1000,662,460,-1000,561,-258,132,920,443,-632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00970() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-539,645,-9,-314,-513,-611,666,874,107,-414,390,397,526,32,-167,-306,197,122,987,-540,-182,470,878,412,-1000,-854,-838,-1000,-638,18,289,222,-99,-734,-539,127,626,-503,1000,327,-40,-1000,66,-1000,257,-182,-764,468,857,-311,-874,-413,-297,-407,-1000,-273,290,-1000,381,-369,-1000,831,-449,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00971() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{398,830,733,-149,-331,-876,68,-424,-78,720,164,416,349,443,-435,251,209,1000,371,114,507,611,753,136,311,-1000,23,-472,-544,674,295,958,42,868,738,-464,-228,595,-109,-107,-26,-489,533,-400,202,-129,-19,-339,-613,1000,-827,624,-434,-483,-916,439,370,-1000,68,-260,-808,902,-5,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00972() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-581,705,-1000,52,1000,-1000,-48,642,-639,901,-131,-463,-504,790,664,979,940,859,88,124,1000,-321,187,-786,-715,-441,-958,-382,-756,953,874,440,1000,746,1000,-82,1000,-415,1000,117,1000,-885,461,-613,-75,13,-222,-9,-761,1000,-249,-47,237,62,-1000,252,-442,-765,145,-728,-857,336,-818,696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00973() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-622,995,168,-456,981,-218,-271,643,-1000,1000,134,171,-206,590,-81,592,1000,1000,1000,61,-237,-49,586,-446,-1000,-997,-923,910,-585,763,372,-1000,730,-577,568,1000,-1000,-101,1000,-182,38,-70,860,323,-1000,-928,-35,123,-1000,925,-829,477,299,-1000,-1000,565,-512,-632,111,-269,652,121,-339,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00974() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{818,1000,-192,0,48,-971,-112,41,506,-613,241,-463,347,-808,614,-678,496,1000,1000,400,33,63,1000,-149,-715,-788,666,-1000,-296,824,258,-106,541,249,-402,-73,-1000,-244,285,-1000,-544,19,894,900,731,-1000,-189,-686,-839,689,-702,1000,-1000,-1000,-1000,845,850,-111,1000,490,652,336,-818,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00975() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{775,714,369,-380,-1000,-570,-592,-1000,-849,1000,79,737,1000,1000,-1000,-1000,1000,1000,-1000,667,1000,1000,1000,522,1000,-918,-880,-362,1000,-1000,-1000,1000,-236,1000,1000,-1000,-731,478,-1000,-1000,-884,1000,-400,1000,1000,-387,-1000,-1000,-1000,1000,-1000,486,-953,-1000,-1000,1000,-625,-1000,719,-258,-683,1000,1000,40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00976() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{264,-699,-773,-504,482,549,176,979,6,-636,-1000,616,297,-672,-501,-122,-981,-426,-828,-145,733,-189,-523,261,246,1000,410,-29,82,634,273,-573,-1000,-834,-738,-548,-1000,821,-984,-129,-354,-742,-1000,-173,-355,83,-611,1000,-358,949,-297,376,271,783,-291,419,-976,-141,-1000,527,1000,-1000,862,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00977() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{851,492,389,638,-415,497,893,-214,-33,-477,302,309,342,643,765,538,41,-957,351,-755,-587,449,531,669,367,-273,-857,-1000,-647,-629,-107,-1000,-249,553,468,-45,662,-305,1000,-353,566,255,465,735,-350,-648,629,-705,829,1000,893,376,1000,-783,-534,-268,563,976,-1000,303,89,1000,790,-379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00978() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{807,-1000,-871,77,1000,1000,485,1000,298,-1000,-1000,-12,84,-672,-27,-797,-632,-483,-1000,65,61,1000,-964,1000,1000,-1000,442,-574,376,995,-56,-1000,-1000,-1000,-714,-1000,-1000,821,-820,551,-426,-560,-1000,-631,-355,-13,-1000,1000,-580,-646,-472,862,1000,1000,32,-351,-1000,94,-1000,158,1000,-1000,978,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00979() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{67,309,9,1000,-401,1000,1000,499,234,195,-388,74,971,-128,150,334,-495,-970,566,-93,-555,124,-281,-114,-1000,0,474,-302,-891,-1000,1000,108,157,37,-494,-127,268,222,-217,137,-1000,311,70,527,1000,646,262,383,-394,673,774,1000,168,35,-75,-139,-567,636,-250,-356,425,526,458,128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00980() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{595,679,-861,-562,622,1000,925,1000,-276,380,-4,475,596,-986,-490,-425,-489,-1000,1000,-713,1000,121,-830,-868,-536,-467,1000,-692,-1000,-339,399,895,-1000,-19,-1000,-866,-1000,-227,-653,56,-583,134,-184,308,1000,918,-66,-264,-1000,174,30,731,-105,930,-1000,-106,512,250,680,560,679,-1000,797,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00981() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{703,-646,-467,483,892,765,1000,-75,-232,-1000,-287,566,-163,190,255,218,-331,-1000,-1000,-638,275,215,-801,889,-256,896,-154,-1000,-109,874,176,-1000,-1000,-875,-395,-1000,-1000,-513,-393,104,605,-395,-1000,64,-540,-1000,-870,457,553,1000,-125,943,1000,815,-811,161,-1000,1000,-1000,812,1000,-257,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00982() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{383,518,48,-622,-706,189,-257,-435,-45,841,-387,534,968,-633,-336,-63,-359,1000,1000,503,212,238,521,-172,-143,-78,-1000,430,-374,-583,74,1000,809,730,46,515,830,422,-87,-682,-701,259,763,396,60,830,956,933,-473,-439,-446,-1000,-587,-625,-361,101,444,-421,1000,-213,-432,-13,106,-591}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00983() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{25,168,203,-861,408,-400,658,1000,-63,467,1000,-516,589,-977,1000,-1000,299,-991,1000,-1000,1000,869,922,771,400,-1000,1000,-503,400,521,167,-1000,-1000,1000,-351,-1000,676,1000,268,473,76,963,497,186,-400,1000,994,-1000,400,-534,-1000,1000,848,867,-1000,520,494,511,-1000,-521,64,942,268,-998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00984() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{998,-161,8,1000,-623,497,803,-739,-235,-292,302,583,428,643,750,915,361,522,351,-1000,-963,726,545,669,367,-273,-719,-1000,-1000,-1000,-97,-1000,540,836,819,33,1000,-164,967,-824,399,255,-480,1000,-355,-398,627,-1000,829,863,863,646,1000,259,-534,-268,1000,639,-1000,303,-77,1000,568,-803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00985() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{609,122,-51,-307,75,-400,-485,-347,-661,425,293,-331,-1000,-566,502,-662,1000,74,796,-1000,343,1000,-338,930,-595,502,202,-942,400,183,662,-1000,-557,-1000,211,384,110,266,-159,-425,11,833,640,800,215,81,266,1000,-445,-1000,-221,231,1000,1000,670,-526,-1000,-112,-322,-1000,735,53,518,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00986() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{276,354,74,463,164,844,1000,71,-155,-505,65,344,1000,-490,358,-110,-299,-1000,835,-1000,273,821,66,504,-794,0,933,-1000,-562,-325,643,-672,-1000,623,-328,-79,-212,-208,108,-170,-458,1000,146,423,535,627,280,13,-394,837,-30,1000,1000,121,-1000,-795,-551,1000,-1000,-368,1000,217,619,-151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00987() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-32,-240,-746,-1000,-1000,308,377,-379,-805,1000,-861,1000,686,-799,172,67,-317,-623,-1000,-646,621,-129,-40,-97,565,1000,1000,-208,-1000,70,1000,443,442,275,-206,818,319,380,-786,-1000,-963,204,-172,1000,152,764,1000,-1000,-480,-763,-125,-318,-365,-258,-927,400,314,-1000,-243,318,1000,-1000,108,-652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00988() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-289,39,-374,-818,-256,-127,-1000,-311,720,13,-1000,502,909,-657,-970,667,-1000,336,-37,415,139,-713,1000,140,1000,-63,886,1000,498,17,-477,1000,-159,-21,-485,733,196,415,-1000,-488,-452,-1000,-565,-939,-1000,760,431,608,-23,-562,-1000,103,-600,-461,553,1000,650,-445,-1000,1000,-432,-110,-119,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00989() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{1000,-14,-226,724,-415,1000,1000,-924,-304,475,104,421,-27,697,938,-83,1000,-1000,108,-718,-339,624,418,588,1000,407,-209,-1000,1000,-649,529,-1000,-492,812,362,-505,237,-471,1000,-166,428,604,302,1000,264,-881,115,-1000,460,1000,1000,95,1000,-226,-1000,-1000,422,790,-1000,503,1000,361,1000,-129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00990() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{1000,667,349,-504,482,900,1000,79,-807,920,-1000,854,271,43,1000,-409,1000,23,976,788,14,675,-230,464,-915,99,-1000,-1000,-1000,-844,828,-1000,583,758,816,-318,870,-877,1000,-873,103,960,1000,1000,1000,-471,1000,-752,-684,667,925,-345,378,-800,-1000,-573,111,-264,513,155,412,290,1000,-300}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00991() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{104,-88,-841,-590,971,355,-264,679,567,-704,-276,270,17,-276,-612,948,-980,-179,223,-713,682,-363,255,-868,1000,615,1000,61,132,961,562,-272,-1000,-462,-969,-260,-1000,1000,-1000,-413,460,-1000,-1000,-967,-1000,-71,-66,-264,960,53,-651,1000,-411,414,-384,627,-593,787,-1000,994,1000,-768,232,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00992() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-183,-74,524,-844,754,-2,365,18,80,-860,-131,-533,-600,138,-288,91,105,-851,366,270,-713,-245,182,408,131,-424,302,-110,333,-164,45,161,-535,689,-349,-61,983,-248,-335,802,326,-476,259,-282,-524,300,-388,-15,-481,-50,-167,866,730,716,-369,643,264,-554,-995,191,-749,58,-447,-697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00993() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{642,125,1000,-925,635,349,519,18,832,-771,-413,-395,-754,173,-129,409,-126,-603,211,1000,-1000,-577,209,-249,356,-1000,633,81,39,1000,-85,604,-837,364,-803,336,948,-120,1000,645,326,-139,440,-146,-463,65,-749,108,-385,-317,-1000,876,570,787,-1000,1000,269,-273,-995,539,-711,449,-1000,-697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00994() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{649,-74,959,32,626,-303,365,-575,-110,-1000,581,-764,-357,-123,79,-326,40,-851,447,54,-37,290,182,1000,111,564,-695,-475,422,271,133,19,356,904,460,-942,973,-84,1000,889,-787,-1000,-287,117,-667,-642,207,-18,-540,349,1000,674,925,289,-63,400,599,-1000,-995,-549,-1000,294,291,-898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00995() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-459,68,589,573,181,-786,260,416,137,154,814,799,1000,3,-1000,91,705,-392,-78,-742,125,-245,-793,-588,474,222,-136,-587,-441,-299,298,-1000,1000,566,-349,-333,-799,81,-55,161,33,-476,259,-133,-670,300,288,-70,-481,-136,284,495,730,448,1000,-265,-279,696,129,-715,11,-197,445,-438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00996() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{1000,-778,-261,1000,603,-214,-269,-1000,-939,-550,-430,-24,304,-521,-872,-16,-542,-1000,-156,1000,-1000,-712,-531,-72,-893,-713,-272,-1000,-742,1000,-122,-1000,-737,567,-62,995,1000,1000,-310,-497,-453,207,263,-123,-224,-663,-1000,310,-679,957,453,1000,-196,233,-1000,1000,1000,-660,-451,-1000,-1000,1000,-861,159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00997() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{58,139,594,-93,1000,-353,-168,-74,264,-564,-241,-373,-1000,-399,-684,154,454,-513,450,332,199,-214,65,-249,-155,-808,-50,992,245,-212,479,-416,-552,581,22,606,948,-14,678,247,-202,647,55,-1000,-796,367,-432,-785,-845,-27,-1000,570,645,672,-295,18,44,-215,-1000,-339,-245,92,-602,-800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00998() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{1000,-1000,-396,-355,325,745,-925,-841,-939,-1000,30,-742,-322,-1000,-524,716,1000,-182,570,-1000,614,261,-1000,1000,596,538,-457,-786,576,309,697,-1000,-57,826,304,783,1000,-1000,-71,957,-589,850,-647,-527,-1000,425,1000,-1000,-1000,670,-1000,-404,1000,-1000,1000,-1000,-1000,-824,-1000,-390,544,-1000,777,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00999() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,414,-486,-116,348,-28,-74,917,531,207,746,-583,-599,-493,505,-701,424,1000,515,-312,1000,1000,-145,-1000,965,-505,3,460,738,-847,542,-756,1000,-392,730,-121,1000,-815,-400,661,-630,168,-501,-71,-511,496,636,-601,-315,244,-851,-756,1000,164,215,-855,-1000,432,-865,-62,-106,-899,360,-486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01000() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{857,-1000,828,134,201,-406,1000,18,80,686,685,727,-1000,317,242,1000,-559,-1000,-337,823,-1000,-41,-325,-349,257,-59,-136,-1000,-287,-209,-319,-389,44,-1000,427,-578,346,483,-1000,168,147,-476,843,58,939,-128,-768,1000,-1000,-1000,-270,409,-1000,1000,389,993,1000,469,1000,191,-749,212,-1000,-215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01001() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-403,-1000,-666,412,603,-650,-591,55,-230,-811,1000,-1000,-331,-1000,-924,-1000,882,-819,863,-458,784,1000,1000,1000,-1000,175,-32,-932,1000,-252,1000,-767,1000,1000,1000,-1000,1000,-454,1000,893,-1000,-1000,-1000,-1000,-1000,788,1000,-719,-679,625,-866,-1000,1000,380,152,-1000,498,-1000,-1000,-1000,-376,-795,1000,-536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01002() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{1000,-898,-626,742,320,-590,532,-1000,-729,902,98,176,-980,-288,-881,1000,-675,-892,206,1000,-342,-105,-1000,-793,1000,-1000,-50,-1000,627,-428,229,-814,-421,-171,596,928,669,884,-1000,-522,79,961,593,136,448,-387,-1000,-401,-738,-1000,110,-450,-899,220,-29,767,558,740,-239,225,-331,811,-621,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01003() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{992,-1000,-482,897,706,-294,421,-1000,-1000,268,226,192,-1000,-614,-1000,215,-830,-1000,-38,415,0,142,-1000,315,-751,52,-617,-1000,-470,-1000,155,-1000,-21,188,1000,874,1000,1000,-856,-423,-468,-121,603,172,124,224,-1000,302,-1000,-1000,691,478,-346,674,153,767,722,-379,-468,-407,-1000,1000,253,-474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01004() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{177,-398,974,-1000,1000,902,-360,-238,-1000,-799,81,-362,-382,62,1000,-504,288,-565,9,203,-1000,-371,847,1000,-1000,1000,-305,-574,-591,764,472,-25,278,-604,-831,-920,-1000,-394,1000,718,-221,-1000,446,387,211,440,-669,676,-66,603,-756,1000,381,-295,52,1000,129,-1000,-348,-137,-1000,-818,-645,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01005() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-807,-30,254,-21,894,-900,-1000,762,435,-940,1000,-1000,511,-475,1000,-732,1000,921,1000,-728,987,979,-1000,610,-77,-247,397,762,1000,-780,1000,-558,966,842,802,-1000,-23,-1000,686,1000,-998,-691,-1000,-1000,-647,247,1000,-710,-403,1000,-393,-1000,802,34,153,-1000,362,-545,-1000,-931,817,-1000,-120,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01006() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,-1000,-236,-37,325,364,644,-557,-496,-1000,-700,447,-839,-448,400,-644,1000,-461,-1000,496,114,1000,649,1000,455,858,404,-288,-1000,-51,1000,-1000,1000,-821,-378,-849,-1000,219,-447,-68,-23,1000,-917,125,1000,-1000,-1000,-400,45,-1000,-1000,-298,146,1000,705,1000,908,245,1000,-111,692,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01007() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{1000,-391,-249,891,632,-1000,179,-706,-468,1000,369,-177,-962,-155,-664,1000,-963,-769,897,1000,233,339,53,-1000,580,-1000,-366,-1000,-674,-270,642,-870,83,188,1000,-69,908,1000,-1000,-829,-409,-149,114,-281,483,-970,-1000,1000,-602,-1000,1000,-157,-1000,633,-536,963,1000,374,-217,-407,-105,1000,-420,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01008() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{-1000,-283,256,623,-407,-714,-1000,1000,-1000,-1000,-748,164,627,383,1000,-235,1000,-1000,579,-478,294,-554,33,1000,340,280,338,-755,733,1000,-1000,14,-202,1000,248,-216,28,-690,-1000,-1000,-688,-1000,1000,1000,-518,-1000,-1000,-1000,652,-1000,846,149,-600,-239,598,492,-715,-930,-1000,-1000,-70,-315,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01009() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{613,-1000,-306,139,938,-977,-318,-543,201,310,-49,305,-1000,-280,544,-734,975,-1000,520,-597,1000,-388,-733,1000,-350,401,1000,264,-1000,-369,1000,-364,-1000,-232,684,-1000,105,-1000,-287,-1000,-750,-581,-659,-502,-296,1000,-1000,-1000,-274,-1000,-550,-113,-662,-614,-317,-146,-1000,179,-323,-490,-863,-137,702,964}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01010() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{-1000,-504,409,-71,-297,-1000,1000,359,-181,-977,-674,590,1000,-401,302,-114,952,-1000,-318,-1000,1000,400,-189,1000,-897,1000,-186,-406,143,19,1000,-1000,-1000,-1000,-1000,-1000,293,-118,-1000,-346,-524,-496,856,573,363,-444,-1000,-767,-1000,-1000,-1000,-518,-1000,126,-288,1000,-1000,-367,-168,-1000,646,69,-1000,-729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01011() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{399,850,-61,-456,688,196,798,452,-1000,-715,43,1000,606,1000,-258,424,170,1000,-197,-54,-955,-1000,-817,285,779,796,1000,-1000,1000,1000,-933,797,69,1000,962,1000,1000,-483,-1000,982,-1000,-515,-306,1000,1000,391,-640,-662,665,917,708,-62,787,1000,-1000,-1000,1000,-1000,-621,412,-260,-35,847,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01012() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{-117,372,-206,-124,-712,267,160,-382,-968,-233,878,516,214,923,-47,874,749,574,-720,713,-1000,-830,-993,879,331,634,1000,-227,370,1000,-1000,797,684,1000,1000,396,1000,-684,-248,314,129,165,-92,864,247,-16,-638,-1000,577,116,1000,22,849,1000,716,-1000,-106,-920,-621,-953,-260,805,847,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01013() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{-590,738,-486,17,-854,0,435,896,-630,987,-995,-372,387,-988,-319,-922,18,-946,971,524,-594,-933,146,-62,552,-87,-694,294,549,905,-388,-6,-798,-195,601,940,-337,-396,-383,-281,-427,-637,620,-74,-776,-1000,-118,730,-716,12,427,455,-242,-193,-546,166,15,601,-571,-972,943,613,82,570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01014() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{-986,-1000,329,610,999,-75,124,-767,-466,-967,206,-135,1000,-753,1000,907,680,16,-889,-404,1000,1000,-551,1000,-1000,1000,-1000,-50,-252,703,1000,-869,-395,-901,-307,-1000,-1000,-935,-82,-1000,400,656,423,-486,956,565,-1000,-1000,-889,-1000,-994,-418,-1000,-1000,828,1000,-1000,-207,1000,-252,-1000,-729,-1000,268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01015() {
        org.junit.Assert.assertEquals("COLOR:-1791828", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{-625,-1000,614,147,931,-972,1000,-138,-1000,-1000,-730,490,803,-667,752,-722,196,-598,345,-540,936,-852,215,845,-557,615,-134,245,-1000,96,1000,-733,-1000,-1000,-369,-400,753,96,678,-1000,-1000,-1000,18,-290,-587,363,-400,-400,-517,-454,-1000,-904,-1000,1000,-1000,1000,-1000,477,-517,154,-304,-1000,-1000,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01016() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{-618,-698,-601,-225,-1000,-861,572,135,-1000,123,1000,-152,-1000,1000,-279,-65,402,1000,-313,432,1000,-238,-196,806,-525,71,-219,-44,-1000,30,763,762,-258,122,-134,-500,-1000,-834,553,526,192,-920,-1000,299,-537,274,-1000,205,-932,534,-387,-327,-16,-572,-1000,-369,-246,-142,349,194,-248,395,-309,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01017() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{1000,-629,-194,-446,-1000,-1000,504,-214,-1000,454,325,1000,-1000,-448,-327,-362,847,-669,-1000,-966,-54,-244,-1000,1000,-559,86,514,540,222,1000,-349,194,-697,-462,266,1000,744,370,-1000,311,-381,-1000,-234,-531,671,635,-1000,-972,999,945,-735,26,1000,557,148,594,-37,123,-873,-255,1000,-1000,1000,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01018() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{410,716,174,21,-1000,-431,169,104,-1000,454,808,-22,-1000,-988,-235,134,1000,397,-216,880,-867,-933,-548,1000,704,325,916,646,537,699,-543,688,597,1000,81,-751,696,-166,-1000,-37,243,-881,-742,-467,-765,-12,-672,-1000,-196,-340,1000,364,400,-15,-546,-389,-1000,-1000,-571,-1000,740,1000,827,576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01019() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{-1000,1000,209,938,1000,602,471,83,-439,-1000,-721,-621,1000,-1000,909,-1000,-83,-216,175,134,789,938,435,55,-344,990,-1000,167,274,-256,-705,-1000,-206,-740,-1000,-1000,-616,-832,266,-1000,724,686,1000,-465,-195,-580,72,-562,-1000,-1000,-509,-531,-1000,-1000,24,1000,-502,407,1000,-641,-1000,-5,-1000,-72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01020() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{-990,836,414,560,238,-4,387,-20,-588,333,-239,176,-920,-1000,179,335,-467,645,-802,-399,-211,-569,-604,-247,-370,86,-1000,842,961,-345,177,-449,-129,-689,-807,-975,-1000,-24,-85,-728,1000,203,685,-1000,395,-228,1000,1000,-597,945,-637,-197,-690,-1000,280,791,258,1000,1000,-406,-1000,-279,-336,1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01021() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{1000,-443,-31,-749,-712,-571,-187,-1000,-994,-1000,1000,798,-998,-1000,909,1000,1000,806,-1000,-104,-369,-301,-1000,1000,-665,990,974,149,-157,1000,-705,1000,-206,-740,1000,890,1000,-691,-596,603,-201,135,-540,65,1000,1000,-1000,-562,1000,951,-11,84,1000,1000,1000,-948,-832,-1000,-830,-432,257,-332,1000,988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01022() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{-650,290,534,388,-332,-3,-715,87,680,-440,983,-477,127,-778,-157,-196,-166,-521,906,-135,16,194,496,-350,125,-186,858,-267,20,265,173,-450,34,-19,175,859,370,-33,769,8,-819,-220,247,101,694,-95,957,294,-241,100,-374,58,-665,7,910,-489,590,530,230,671,-462,360,-571,862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01023() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairPaint():java.awt.Paint",
            new int[]{-117,583,-151,-421,-600,-201,160,-382,-968,454,878,311,-998,923,135,874,671,574,-986,434,-784,-363,-993,910,-9,728,678,365,370,879,-740,843,684,815,937,40,711,-353,-248,196,123,165,-742,86,247,479,-701,-916,589,-134,665,-29,636,384,716,-671,-334,-920,-445,-821,497,392,847,854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01024() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{71,427,-687,1000,137,979,1000,-976,-1000,451,-1000,990,-1000,-935,-304,-1000,338,91,-1000,988,-1000,-444,-1000,-1000,620,30,-617,-1000,59,-697,-92,756,-634,1000,346,-712,151,-182,961,-911,1000,711,1000,-905,-104,1000,644,335,878,1000,125,1000,-308,1000,937,1000,1000,1000,-1000,1000,-9,280,-1000,773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01025() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{764,-254,-476,3,-176,-1000,-1000,-366,237,-20,382,-191,-215,-427,232,420,-435,-247,-60,-847,440,264,1000,-227,-1000,30,966,572,436,396,-741,-1000,801,-535,842,-220,-57,-467,-125,-454,204,-941,-338,857,455,-360,-437,577,387,88,1000,838,274,1000,-453,-223,-754,530,-503,-631,-244,-611,396,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01026() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{-384,101,404,-714,-211,-243,1000,273,415,923,-29,-267,-391,-444,-383,-1000,448,773,-591,458,-792,-736,-69,912,-214,1000,852,-211,508,187,-212,557,-257,1000,-134,-538,-1000,-511,560,964,119,1000,768,-1000,-147,643,-95,760,646,88,1000,-535,514,780,1000,-311,197,-984,-770,1000,-1000,-942,537,346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01027() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{-46,-26,354,847,1000,1000,954,1000,-293,273,-346,-771,-1000,432,149,-1000,1000,668,586,1000,284,710,1000,-1000,1000,-1000,1000,-703,-269,-1000,-463,58,297,1000,-751,-829,396,1000,796,-961,-885,782,1000,208,731,713,1000,43,-598,722,-798,550,202,1000,-1000,1000,1000,-764,-1000,1000,198,-81,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01028() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{-423,-650,354,400,-204,-243,-471,273,872,-79,-294,298,312,-574,216,891,129,763,-680,-555,363,-793,97,757,-385,688,-53,-400,995,258,-738,-155,-123,-400,-152,-770,-235,-951,-95,-764,-735,198,-40,-276,-763,-381,-628,399,797,-361,940,-1000,753,515,438,174,-175,-1000,-671,-291,-530,-545,766,125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01029() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{228,-1000,-551,-428,-585,696,672,-429,-95,597,327,326,-146,-1000,-622,-556,-451,-730,-611,1000,-943,404,-629,-61,-867,764,867,-868,60,-833,208,759,800,827,721,518,-57,-325,1000,1000,178,-53,922,193,-167,404,503,487,337,969,157,480,-1000,1000,-16,433,704,1000,-1000,1000,1000,398,198,971}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01030() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{-435,-1000,203,-641,-661,-1000,-1000,1000,237,-15,1000,784,146,367,1000,-717,-46,1000,-314,-1000,979,-1000,741,1000,174,1000,1000,1000,694,1000,-630,-1000,-392,-699,-322,-1000,-690,-1000,-269,322,-690,-1000,-1000,-245,-1000,-1000,-762,650,1000,-1000,1000,-978,666,1000,653,-1000,-1000,-1000,-224,-1000,-530,-837,162,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01031() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{189,-712,679,252,-664,-272,413,-514,770,311,399,104,-937,-653,-954,692,-682,862,-309,56,-964,874,205,791,495,464,464,-121,482,-697,-833,-675,-203,501,893,-792,757,-426,-962,625,-926,-840,354,959,969,684,125,684,-866,609,-224,-544,258,137,619,-342,220,-753,822,-259,781,839,-746,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01032() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{441,-322,-342,962,46,67,-1000,-607,-166,56,276,357,-942,-504,-47,409,-627,-859,243,-931,-73,762,-334,-385,-592,350,38,514,890,158,-74,-607,883,-507,85,-169,58,-881,359,-904,-400,-729,-192,737,330,184,560,781,-476,1000,532,1000,-39,992,-676,-356,-214,328,-361,-12,910,-118,522,-696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01033() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{-882,101,404,-20,-99,-243,-471,273,-824,-720,-294,414,337,-255,-383,238,798,300,-591,945,-551,-1000,-145,-699,1000,-1000,-53,-400,296,-1000,-475,-1000,-1000,-75,-161,-971,1000,-888,679,129,-735,1000,-40,-147,98,852,-95,227,1000,492,-572,-535,525,1000,595,629,833,-984,-1000,-291,-1000,1000,235,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01034() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{432,-407,236,-1000,-63,-710,86,462,-511,-1000,467,492,754,-294,-561,103,564,-585,1000,727,1000,352,232,-53,141,-1000,844,-213,13,-1000,-246,-282,432,-1000,32,608,1000,845,299,741,180,909,531,867,146,187,-48,631,834,638,-1000,908,-229,26,-498,-381,107,275,-57,-812,366,143,-339,158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01035() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{142,38,-592,610,1000,628,141,382,-1000,133,-465,853,-1000,-560,-54,-275,-1000,-14,1000,-344,-348,1000,-755,-574,-991,97,-1000,-957,1000,-646,934,-573,1000,-1000,202,585,1000,-1000,938,-298,245,303,-826,577,428,1000,767,1000,-718,971,-242,643,132,616,-207,-1000,1000,824,252,-472,1000,430,-54,-383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01036() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{144,428,-446,-590,-855,534,1000,-931,-611,982,-551,1000,-1000,-1000,313,-702,645,139,-312,1000,-1000,-439,-1000,-577,122,1000,-244,-400,-1000,-441,943,-1000,-20,1000,475,282,-465,-1000,1000,-725,1000,119,1000,-573,957,1000,1000,757,-485,1000,-69,1000,-611,33,-14,292,875,1000,-591,-1000,1000,686,-1000,818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01037() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{-104,-517,-304,-240,-833,17,482,-324,-846,284,1000,327,3,-1000,-348,-317,949,594,-680,549,-1000,-89,-645,-798,199,702,1000,-316,995,-548,-738,24,-482,962,887,-126,-220,-1000,1000,25,282,-475,624,79,-1000,-525,-1000,1000,797,670,337,-80,-234,1000,479,326,1000,541,-591,657,971,-139,-511,747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01038() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{486,-977,-209,-49,140,-882,77,-887,507,9,-294,-536,665,-911,-70,238,-693,66,362,-378,-1000,593,1000,642,23,321,852,-100,1000,426,-344,-81,-90,-580,538,465,353,1000,-827,-362,-1000,156,-40,238,10,-381,-825,965,-241,-361,661,214,942,1000,-504,-27,-175,671,-1000,-291,-936,-809,1000,-444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01039() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairStroke():java.awt.Stroke",
            new int[]{-91,-66,-888,781,-14,356,1000,-1000,879,1000,-1000,437,-1000,65,278,-1000,-218,3,-486,-308,-669,169,-1000,-1000,-59,1000,-1000,-1000,23,681,1000,-38,116,888,225,132,-958,-80,839,-1000,1000,-1000,1000,-535,1000,1000,967,512,-1000,1000,1000,1000,-1000,1000,283,335,603,1000,274,-1000,450,-862,-1000,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01040() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{-824,306,-971,464,-306,696,-191,669,998,-961,-2,770,-403,126,-303,415,274,317,1000,-446,271,-626,-663,816,-234,242,-726,-144,-827,970,-50,-432,286,277,-266,142,-4,522,992,306,446,678,787,-173,1000,37,1,1000,-1000,108,287,1000,-432,-211,-533,89,-906,35,275,-282,455,268,-506,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01041() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{703,13,799,-27,-1000,1000,-497,1000,284,430,57,339,-1000,1000,-1000,-934,-378,839,64,-514,-895,-731,-832,-850,-850,502,1000,-755,1000,436,830,-383,396,287,-640,179,-951,1000,-453,-1000,308,106,457,-1000,-1000,243,-1000,-1000,1000,-716,-598,-1000,933,1000,27,-305,161,-1000,-834,-1000,-447,740,375,-449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01042() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{-77,176,-266,133,1000,1000,-1000,645,-471,714,-354,-548,396,-844,637,255,790,-742,452,1000,-394,598,739,459,495,108,142,-1000,-915,-1000,-829,83,-369,1000,758,-188,1000,-1000,323,97,-80,-600,-42,164,139,-291,460,167,1000,-51,913,457,-614,610,853,-203,-332,-1000,374,674,111,-544,1000,-668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01043() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{1000,-457,510,-190,-19,290,40,388,109,755,1000,979,944,939,-869,-800,-1000,442,-381,528,-1000,271,-132,-562,-927,29,770,-176,815,336,831,375,-1000,689,5,-639,-1000,961,918,-998,380,-820,492,-289,538,-137,-991,-1000,1000,-1000,-608,-175,998,651,931,-396,-843,-885,-963,-1000,18,358,501,-111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01044() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{1000,-278,833,538,511,172,594,-918,1000,339,424,339,-1000,-1000,1000,-934,-507,-1000,-1000,743,-1000,576,644,-345,173,502,508,659,-250,581,-564,575,396,287,1000,-254,-173,-912,-453,709,-699,-1000,87,1000,-884,-411,548,-365,1000,-708,624,-1000,-432,-718,1000,1000,-1000,1000,-964,1000,710,22,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01045() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{-855,-644,833,-496,-277,327,594,-499,-493,234,762,994,-479,288,-996,552,73,205,197,-257,-272,-510,-775,-345,-285,-612,508,367,42,533,-564,189,-345,745,-255,902,-886,-597,770,240,493,-658,-467,-583,-548,310,647,-365,753,-279,129,-808,929,594,-591,63,213,-575,-964,-352,386,17,-116,-257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01046() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{-439,-610,-451,1000,347,530,-1000,1000,-1000,638,-651,4,170,658,-1000,523,-818,155,1000,1000,412,277,753,-631,-1000,-351,585,1000,-779,-60,-1000,186,-551,-289,190,-802,-1000,685,-704,253,536,380,-122,81,1000,88,-1000,-222,663,-527,454,30,-694,-354,686,734,-183,-389,-1000,-1000,693,603,-697,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01047() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{35,-266,502,647,-306,213,-877,-6,998,-961,-647,781,578,-912,400,-424,-606,-729,571,-446,-137,-694,686,-504,501,923,832,-697,-113,589,174,-120,-137,290,426,694,16,832,-506,-684,-323,678,787,-201,-17,449,-241,909,825,831,327,869,-63,-96,25,569,-177,648,-62,-11,723,754,-189,957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01048() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{-155,34,-667,-107,-612,-1000,-431,-1000,27,146,663,801,-1000,-754,-902,-696,-467,-602,856,-605,-8,-373,68,-164,456,-570,654,450,-10,1000,61,416,-559,-465,-565,168,-1000,-92,210,488,-764,-890,696,79,-968,-471,1000,-997,1000,-140,-638,-1000,452,-946,264,1000,-1000,831,-1000,195,555,705,216,-335}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01049() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{-578,75,521,-1000,209,-503,1000,-1000,-1000,239,987,512,-1000,1000,-999,725,945,452,-86,-556,-832,-40,-1000,549,-986,-950,-537,812,-397,135,-72,-348,-645,-453,-865,613,-455,-1000,1000,165,55,-988,-780,-303,-952,-508,1000,-367,-353,-68,143,-1000,726,190,-563,857,193,-993,-877,188,160,-80,-421,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01050() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{570,-826,-881,767,338,-918,-215,-726,750,-535,633,-775,-760,-672,689,-539,-216,-505,-583,816,-742,430,817,167,-636,254,-531,545,-484,119,778,226,144,-474,614,-822,30,119,-609,213,-513,-964,678,794,-534,-4,429,-467,992,-54,85,-850,-256,-680,914,866,-959,759,256,64,985,454,587,-600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01051() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{-758,-73,-667,993,799,-328,-806,862,-193,-337,908,-851,-592,609,-809,698,157,-440,856,782,39,27,528,-164,-900,-1,-271,961,-895,-679,-862,-74,-285,258,-278,-208,-165,99,-648,316,-33,141,-516,-155,685,-476,-432,540,-327,-991,924,449,-838,-946,-970,225,-944,291,-627,64,555,870,-654,-148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01052() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{-359,30,-332,1000,-47,720,-806,1000,362,-184,641,-510,79,589,-336,-597,-762,-56,1000,472,-896,-550,528,-421,-390,799,-131,393,83,-92,-1000,113,-137,1000,-373,-637,-547,931,-903,676,250,1000,-670,-643,1000,-290,-1000,926,-80,-944,811,1000,96,-379,-1000,-1000,-379,987,-346,-768,649,1000,-1000,290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01053() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{268,660,-181,235,777,-88,1000,-322,866,-967,-688,-744,-681,-1000,987,-782,74,291,696,-768,-268,-332,535,1000,705,860,-203,-349,-756,-135,85,-197,1000,-1000,512,507,35,-310,-492,574,-137,592,313,619,1000,12,774,1000,-798,480,-37,492,-253,-181,780,473,-922,595,203,524,858,-339,503,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01054() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{124,899,1000,-1000,138,371,1000,-1000,-1000,1000,802,512,-410,-765,-999,294,-759,-650,-872,-187,-171,201,-853,-34,1000,-924,561,-7,-75,477,-1000,-348,-1000,573,-154,757,-424,-1000,1000,1000,-421,-1000,-662,140,-884,-799,1000,-549,-353,-68,-84,-1000,699,1000,832,227,-1000,-118,-1000,1000,160,-336,146,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01055() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.CategoryPlot", "org.jfree.chart.plot.CategoryPlot", "getRangeCrosshairValue():double",
            new int[]{830,-665,213,505,-466,-636,-1000,494,663,198,1000,146,944,686,-1000,-830,-1000,-9,-444,1000,-1000,894,937,-804,-1000,-106,417,497,-511,-820,558,379,-1000,169,861,-1000,-578,898,-329,-1000,632,-955,830,303,-444,-302,-1000,-1000,1000,-872,51,-107,400,261,946,434,974,-885,-862,-1000,200,533,-912,-614}));
    }
}
