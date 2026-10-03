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
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "add(java.util.List,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{539,-1000,-571,267,-1000,-835,508,511,-1000,-592,-817,1000,303,209,-511,821,404,-715,1000,-346,-572,-1000,187,-142,1000,800,1000,-1000,397,1000,-459,1000,115,-445,-886,-1000,-999,-253,1000,252,1000,-758,-1000,-1000,443,-1000,54,1000,-778,295,-371,1000,-1000,-1000,-894,-1000,-1000,-1000,120,-635,-774,-1000,-42,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "add(org.jfree.data.statistics.BoxAndWhiskerItem,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-204,-1000,184,-735,-52,-647,-203,-351,469,-1000,-104,833,-448,187,1000,1000,342,-198,-489,-404,-646,-178,-46,-285,-1000,-606,481,172,-1000,1000,679,373,-929,213,-1000,163,-359,-1000,-271,-798,41,694,-245,-56,-136,-680,587,-154,333,-295,-1000,-673,-139,348,-563,436,584,-1000,850,385,-43,-76,703,-606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset|getRowCount=22:java.lang.Integer:Mg==|getColumnCount=22:java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "clone():java.lang.Object",
            new int[]{477,1000,309,131,-848,255,136,849,-88,-1000,-573,508,1000,-1000,1000,1000,-856,1000,-618,275,596,-973,-57,-75,1000,41,-360,1000,1000,-992,381,1000,76,734,685,-950,-1000,-1000,797,-77,-400,397,1000,957,-871,-174,880,-108,1000,-1000,773,-419,495,174,-727,-1000,473,-342,1000,1000,978,1000,63,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset|getRowCount=22:java.lang.Integer:Mg==|getColumnCount=22:java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "clone():java.lang.Object",
            new int[]{-540,59,-791,-417,-264,730,-989,-255,-329,126,341,350,-530,138,753,72,901,-854,819,-747,-99,397,-711,508,142,81,70,-173,418,-654,-403,117,-444,476,77,-389,97,901,871,307,465,-671,853,726,533,320,938,659,-454,916,224,-720,234,-439,-856,45,575,-520,886,133,-815,-85,-40,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "equals(java.lang.Object):boolean",
            new int[]{577,226,433,-252,481,-221,460,142,-78,-103,-758,-4,-477,-893,168,-756,-74,-1000,1000,-778,-1000,-363,-665,1000,-1000,894,368,-896,-373,-202,-422,-414,-481,-640,-250,1000,-445,-1000,-267,-715,485,-73,-1000,976,126,-264,-1000,694,1000,-119,-150,-238,-747,-80,346,-1000,-1000,306,63,-382,-1000,365,747,-390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "equals(java.lang.Object):boolean",
            new int[]{-249,232,997,-219,-331,-297,-886,951,-262,-367,-1000,-615,-299,127,-197,28,-1000,-17,1,-753,-967,1000,844,-67,-504,1000,1000,805,534,1000,-952,121,-118,-1000,617,733,-905,1000,1000,-433,-367,405,-387,-401,-53,-185,-619,891,-802,-721,-1000,-746,-1,834,-370,-755,121,199,354,106,-685,1000,765,60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getColumnCount():int",
            new int[]{-25,338,-462,96,-631,157,1000,566,-320,-120,293,84,1000,100,1000,504,407,275,184,605,-697,-494,-867,-141,66,-518,800,-45,379,758,-373,271,8,-1000,-983,-6,-1000,-323,670,-540,525,-422,135,102,1000,540,82,-427,491,-34,-986,287,474,-144,758,-134,644,-103,1000,398,41,504,-276,-222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getColumnIndex(java.lang.Comparable):int",
            new int[]{278,1000,-361,21,3,1000,-1000,-973,1000,-145,79,-504,-852,-993,984,1000,-908,-1000,-183,-1000,1000,996,1000,-190,292,790,-885,879,-177,-2,56,-976,261,-1000,-1000,1000,-56,838,1000,-164,-389,-649,-1000,-739,1000,-46,760,630,671,1000,-142,448,770,-508,828,345,-1000,1000,-86,690,166,-798,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5Mw==", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getColumnKey(int):java.lang.Comparable",
            new int[]{113,-919,54,474,822,1000,-816,-868,-207,1000,-55,-946,-105,-752,573,129,-842,-263,-1000,-536,-650,867,462,-420,802,157,115,-742,-691,-229,-136,-362,-101,405,537,18,481,378,778,-1000,420,-318,502,422,-1000,-1000,753,-76,615,-1000,-618,248,686,1000,-236,-231,-1000,-720,-265,-228,-901,-298,414,941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5MQ==", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getColumnKey(int):java.lang.Comparable",
            new int[]{-985,1000,949,307,640,-959,-90,-181,-963,691,123,-498,-1000,-1000,-1000,-1000,1000,1000,1000,572,-588,-1000,-1000,1000,528,1000,-1000,-151,-18,-330,-1000,904,-544,-1000,1000,-344,1000,-860,936,-836,614,890,1000,-161,-214,213,-796,136,887,-706,1000,881,-627,334,606,-1000,-127,1000,599,-1000,1000,967,-1000,-462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getColumnKeys():java.util.List",
            new int[]{-565,-1000,-821,483,-1000,-1000,80,640,1000,-243,39,117,-760,-213,-666,-102,-969,1000,-850,485,-53,-84,722,-1000,1000,-845,-1000,-133,817,-1000,-83,939,-690,731,537,179,455,386,133,62,-200,180,-374,-906,18,383,-682,-1000,553,1000,-403,1000,-35,1000,-542,-794,-605,829,1000,-327,-576,-293,860,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getItem(int,int):org.jfree.data.statistics.BoxAndWhiskerItem",
            new int[]{-704,-18,-31,384,-633,-205,520,255,-397,-857,577,-1000,651,748,36,1000,850,-705,-78,1000,-79,928,-44,264,102,-539,840,-78,75,-148,602,-465,940,-611,-72,-1000,-814,-388,-857,-893,-536,645,-1000,310,-636,205,784,-1000,-185,167,-485,-594,303,-1000,-847,-407,-12,641,-1000,394,-1000,-276,-87,396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getItem(int,int):org.jfree.data.statistics.BoxAndWhiskerItem",
            new int[]{525,496,-781,384,-516,1000,423,863,642,-933,-396,-1000,133,-62,1000,-185,-216,-672,-898,170,-263,-323,924,-687,561,-138,561,-1000,429,710,1000,637,-616,-1000,-1000,-1000,-199,256,-1000,1000,288,-762,-581,-1000,-591,-1000,1000,-154,525,-872,316,-1000,788,-334,-269,628,-947,-544,-930,1000,-1000,820,175,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMaxOutlier(int,int):java.lang.Number",
            new int[]{-1000,266,393,-585,-476,588,-836,-579,4,-765,1000,912,-852,1000,1000,204,-611,3,1000,1000,-1000,-1000,1000,125,-474,1000,-1000,-767,141,878,1000,1000,516,367,-541,-571,-1000,1000,-796,445,125,-1000,-1000,-1000,1000,-20,330,-240,-475,162,-1000,-366,-780,-1000,-1000,-77,1000,1000,-437,400,-1000,-661,281,-345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMaxOutlier(int,int):java.lang.Number",
            new int[]{-401,-378,793,210,1000,-1000,-145,965,1000,328,1000,499,-10,-761,1000,-348,845,1,-1000,1000,-812,1000,-8,844,110,1000,745,-188,365,-650,-444,164,774,-77,1000,-1000,34,-842,901,-317,411,731,-342,157,431,841,904,-283,492,375,-1000,976,1000,695,1000,206,169,-205,840,-787,-621,-672,516,-303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMaxOutlier(int,int):java.lang.Number",
            new int[]{-956,816,-131,-738,-1000,-470,-804,-525,-563,-793,1000,877,-1000,1000,366,15,-176,-501,1000,1000,-461,-708,685,-739,-1000,422,-1000,-377,-558,-153,1000,1000,11,1000,-1000,-589,-804,866,384,825,-473,-1000,-950,-1000,879,305,-364,-134,-269,-844,-1000,-394,-226,-437,-1000,-571,1000,1000,546,204,-1000,106,-295,-128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMaxOutlier(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{1000,-768,908,-27,-121,-181,999,88,-1000,-1000,-894,-727,1000,-57,446,439,1000,978,825,291,219,-28,1000,-1000,1000,-1000,-37,1000,765,-1000,400,1000,-1000,1000,-1000,-29,-334,-427,-423,-79,372,-1000,1000,-400,702,1000,1000,-306,-1000,1000,-1000,420,-474,454,627,1000,-1000,-980,-116,169,-299,-1000,38,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMaxOutlier(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-374,-318,-711,1000,-210,86,819,-125,1000,1000,424,-808,-920,742,-1000,-1000,-1000,1000,-198,-800,-423,-626,-993,488,-292,1000,-844,-1000,-389,53,-1000,-876,313,130,690,-131,139,565,327,-1000,309,84,-451,-1000,19,1000,-1000,448,1000,-433,927,-818,25,133,-549,697,-341,3,-853,1000,-968,555,1000,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMaxRegularValue(int,int):java.lang.Number",
            new int[]{921,-176,-272,585,249,37,-1000,-83,-1000,-347,1000,420,-372,886,1000,562,-717,-286,-856,-238,-547,-761,927,115,-433,-937,-251,357,-1000,-1000,1000,353,54,599,783,927,-19,-1000,300,193,488,-642,-650,340,60,434,-886,-1000,1000,-430,702,-524,-1000,-1000,85,24,878,-303,670,-371,597,-539,623,388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMaxRegularValue(int,int):java.lang.Number",
            new int[]{717,-701,-186,440,841,-297,-1000,-167,-558,326,1000,834,-932,603,1000,897,-374,-260,-926,658,-221,-906,1000,408,134,-882,-739,22,-918,-1000,997,982,553,568,1000,1000,-507,-972,487,-212,497,-1000,41,630,-125,695,-196,-1000,455,-1000,1000,75,-1000,-1000,1000,53,829,-756,427,-1000,281,-906,27,870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMaxRegularValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{565,511,-246,287,867,56,207,-269,-382,-297,1000,1000,1000,352,831,-1000,237,1000,-65,-727,-1000,340,-314,998,-306,-819,739,-136,-542,-1000,622,-85,1000,-970,1000,1000,-1000,883,-35,755,-513,-596,1000,389,1000,-842,-111,302,271,-1000,511,-764,-344,-581,-1000,641,1000,1000,-409,-524,471,1000,-213,-256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMaxRegularValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-185,-147,68,-777,501,1000,1000,1000,847,1000,-1000,876,864,149,299,760,2,801,111,86,-95,-1000,-936,-1000,393,593,962,378,531,1000,59,-532,-747,772,-36,900,39,595,768,-691,-82,-1000,644,1000,1000,-1000,1000,865,-645,-1000,77,914,306,-1000,318,1000,623,611,1000,-60,-1000,1000,1000,793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMeanValue(int,int):java.lang.Number",
            new int[]{-1000,-193,-537,-147,1000,-615,-1000,-384,-1000,380,-257,1000,1000,-1000,-1000,1000,821,1000,606,-1000,805,732,-155,-1000,-844,303,218,-958,906,-1000,-506,1000,-878,-1000,1000,-1000,1000,-1000,-1000,-1000,-200,-1000,666,1000,-1000,-17,1000,-466,-117,-52,-989,158,-1000,-1000,-207,-942,-1000,897,692,-1000,275,-513,-1000,269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMeanValue(int,int):java.lang.Number",
            new int[]{541,6,762,-291,552,-975,1000,632,-122,99,-436,-1000,-121,148,-351,-347,-8,254,-245,413,318,73,-285,-1000,481,393,-1000,335,586,-65,290,-1000,250,503,-160,-903,-948,-1000,973,-760,-328,-184,-117,-391,-232,204,1000,-1000,-1000,1000,215,-1000,-80,333,891,-221,869,-459,-1000,1000,526,817,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMeanValue(int,int):java.lang.Number",
            new int[]{-414,-9,-863,316,390,-966,-243,978,-529,933,234,930,7,-296,-912,193,522,843,-31,-727,-433,930,172,-9,-137,988,-72,-925,-52,291,-665,803,74,-928,774,202,795,-97,-334,208,-477,386,-507,575,166,241,176,-712,-94,-135,86,792,-862,-968,-775,-586,-344,-518,485,-964,285,-106,-772,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMeanValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{970,-1000,-166,342,1000,250,-277,-234,-386,963,908,-849,-1000,-1000,-490,1000,-1000,-637,616,656,-857,371,423,128,1000,-530,-775,-1000,-376,715,-1000,1000,597,-1000,1000,-479,-209,-1000,-893,-189,-1000,-372,163,605,883,-267,-857,-146,412,-139,-711,-1000,-93,-552,321,-64,-1000,-349,1000,381,-1000,-305,729,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMeanValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{264,-314,284,-336,-776,-814,1000,-477,-682,-420,-267,-1000,-1000,-183,-958,736,-501,-611,719,-821,-380,164,470,-712,-54,178,-272,-611,1000,-519,-221,-128,1000,-166,1000,-745,250,-656,-498,-959,-657,-1000,-327,-822,-492,-142,-333,-1000,-987,12,269,-1000,797,-110,-284,22,-1000,-401,948,1000,118,-844,-281,-100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMedianValue(int,int):java.lang.Number",
            new int[]{-40,82,474,-72,-1000,1000,-199,248,556,543,494,-788,538,-44,235,517,1000,-186,-600,1000,1000,-1000,414,772,-587,-1000,-444,765,-1000,1000,-365,-1000,1000,1000,1000,-232,693,-45,630,-359,-1000,-397,1000,-505,1000,-400,809,566,-1000,-525,555,-281,-509,611,334,1000,-608,-1000,-305,-207,48,-505,176,75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMedianValue(int,int):java.lang.Number",
            new int[]{530,-1000,379,-128,1000,382,582,-358,209,-89,1000,-1000,309,529,506,49,-268,-827,-1000,1000,1000,-1000,62,735,-1000,-432,1000,12,-1000,997,458,-574,835,895,1000,1000,565,-1000,876,-1000,475,-1000,1000,-1000,580,-1000,174,-211,-1000,240,-1000,-1000,-76,1000,95,727,159,1000,450,586,664,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMedianValue(int,int):java.lang.Number",
            new int[]{-626,639,-928,-66,127,211,-147,-1000,-9,745,1000,7,1000,-462,-837,553,-389,461,1000,-710,1000,-605,-1000,-126,940,-63,-455,-1000,495,281,-827,414,804,-1000,-968,-1000,-965,-1000,-482,-79,1000,203,812,752,54,508,-1000,-144,2,-1000,-224,-620,-152,-141,-1000,-397,-1000,-531,242,1000,-563,670,1000,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMedianValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{202,416,-861,-228,-8,-65,-299,1000,-642,360,887,-340,-26,-1000,-273,-84,-1000,1000,423,788,-805,-336,-586,339,709,796,-1,1000,-433,-901,39,-68,1000,329,-294,195,94,880,746,1000,715,426,-240,1000,158,-1000,971,-67,-139,510,573,-937,1000,192,1000,-499,899,-116,1000,81,8,1000,1000,754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMedianValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{115,1000,-846,910,-651,1000,207,778,75,851,150,-624,-173,-1000,-88,108,898,666,974,119,115,-353,1000,2,1000,259,-579,1000,-1000,-1000,-1000,195,326,1000,-539,-1000,-111,369,190,1000,-253,-677,-123,-198,421,-1000,546,322,546,126,615,-314,1000,-179,1000,-414,965,-1000,1000,295,-1000,47,797,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMedianValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-24,-1000,-971,-720,-263,1,-97,1000,-271,851,1000,-690,122,-29,-908,100,-1000,1000,1000,511,995,-704,1000,-218,842,273,179,1000,-493,-1000,1000,-497,1000,-1000,-450,-1000,311,1000,467,1000,1000,-677,-123,1000,287,-1000,902,-234,546,881,-102,-772,1000,1000,1000,-152,1000,-593,1000,-624,501,1000,533,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMinOutlier(int,int):java.lang.Number",
            new int[]{-667,201,163,1000,-478,670,-537,428,-491,-147,32,1000,1000,-324,1000,1000,-789,-1000,-821,-1000,-689,-613,-944,552,-1000,419,444,-420,370,-688,833,1000,373,681,-840,523,-1000,543,200,-552,291,-748,645,-673,-874,-290,-88,1000,893,-665,-333,647,1000,244,443,919,657,370,436,70,1000,-288,-400,-161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMinOutlier(int,int):java.lang.Number",
            new int[]{1000,184,518,-789,510,896,-33,434,-385,-5,269,1000,-1000,558,928,-153,-584,190,-764,-893,472,-1000,677,-643,-845,271,44,-144,795,-592,1000,456,-174,-1000,-88,275,8,-245,-1000,-548,83,-782,-821,-1000,-233,1000,892,644,577,-1000,-835,-228,-855,804,722,1000,-343,1000,962,1000,1000,905,1000,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMinOutlier(int,int):java.lang.Number",
            new int[]{1000,127,349,-789,-485,96,1000,-654,-385,-15,513,48,434,-730,-201,-153,220,624,-356,457,-362,436,677,-240,-845,-948,729,650,1000,172,-382,-963,1000,-234,-88,102,-168,691,-631,716,1000,-1000,-821,340,-1000,1000,-232,-387,577,1000,-835,356,298,749,-916,689,-168,155,880,-218,-251,-51,220,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMinOutlier(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{118,964,-226,837,-1000,-1000,-318,-306,1000,1000,-720,684,-72,1000,108,206,-1000,-227,-20,-709,-758,-1000,-20,-279,-352,-854,338,-963,709,310,-133,702,-131,-1000,-1000,-25,-447,251,643,203,1000,369,936,-66,-678,-919,-670,757,1000,307,626,-152,-513,931,792,102,-777,980,-141,-476,-1000,501,-704,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMinOutlier(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{1000,379,-746,420,-932,-626,-189,679,1000,1000,-527,990,-161,-369,-786,898,-1000,-405,-1000,-1000,-1000,-452,-782,262,-1000,-833,-1000,767,-708,344,-2,-360,-445,-1000,-243,59,1000,1000,13,-1000,1000,-791,1000,210,-54,-1000,-1000,602,-128,1000,1000,-1000,-345,1000,-307,235,184,-1000,-856,47,-617,822,-1000,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMinRegularValue(int,int):java.lang.Number",
            new int[]{-346,-232,-321,-189,-1000,-601,-55,743,-404,60,257,976,-690,749,-52,-367,-23,590,-1000,-1000,1000,185,30,55,-371,-1000,348,-483,-250,1000,-1000,1000,148,893,-180,-695,-937,-1000,1000,227,238,-853,1000,1000,868,1000,391,400,-592,-890,-142,-776,-226,-483,-409,-496,-1000,-148,255,-688,760,-824,-1000,-456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMinRegularValue(int,int):java.lang.Number",
            new int[]{-381,569,-86,18,-1000,-1000,87,-591,398,323,212,1000,-743,738,516,-1000,-133,-162,381,-964,108,235,57,-30,1000,-802,171,-538,-738,764,-81,1000,-231,-615,105,297,-937,-1000,53,636,18,114,253,533,1000,683,85,659,858,-1000,-1000,-773,-400,-1000,15,299,-598,252,269,-425,402,-154,-743,23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMinRegularValue(int,int):java.lang.Number",
            new int[]{26,-508,-877,-362,-664,-33,-837,619,-571,-764,1000,1000,-931,553,324,-830,-33,1000,1000,-648,-274,-450,529,30,-755,-169,1000,-351,-604,1000,182,1000,1000,-823,-973,66,-727,-436,334,-506,-667,592,60,733,739,778,715,1000,-1000,-478,748,-448,-1000,576,-348,-593,-426,-512,-654,-401,1000,208,-801,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMinRegularValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-1000,1000,349,342,-387,-1000,-686,-579,-1000,1000,1000,51,-1000,-972,1000,-277,-274,154,-1000,1000,-427,-989,-1000,-1000,-812,-1000,1000,-1000,-520,-606,1000,-511,1000,-1000,131,322,-1000,-811,1000,665,-1000,-1000,-1000,-1000,1000,-1000,-1000,660,-1000,-861,1000,1000,1000,-73,275,-1000,-1000,1000,1000,-855,1,-413,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getMinRegularValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-38,-833,338,-111,-70,1000,-615,-80,1000,-1000,1000,545,3,295,-752,801,-872,-307,1000,529,351,658,51,-47,-1000,424,-1000,792,853,-553,27,1000,-719,541,-1000,-113,700,1000,-935,-244,738,1000,656,444,612,1000,-620,-217,-417,-252,-409,-1000,-1000,-25,571,1000,1000,-547,-943,908,-452,-708,-1000,-609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getOutliers(int,int):java.util.List",
            new int[]{692,-831,924,309,1000,1000,-598,1000,885,-333,1000,-109,1000,1000,-515,-1000,923,1000,-400,1000,1000,-1000,1000,112,-1000,1000,1000,-1000,-558,1000,29,357,1000,-1000,100,-1000,-837,949,-778,1000,1000,-1000,1000,1000,-1000,-1000,1000,-1000,1000,934,1000,563,-1000,-965,-599,42,1000,1000,189,1000,1000,662,-805,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getOutliers(int,int):java.util.List",
            new int[]{-431,500,353,1000,-95,-377,-642,-848,1000,-158,102,764,69,373,246,76,-445,261,-339,-100,54,-83,-306,298,-132,-1000,-1000,-1000,-500,-1000,718,-442,-531,1000,247,-995,293,681,-290,328,-1000,902,-468,-1000,564,-545,-1000,454,-1000,202,20,55,137,135,171,-797,-190,194,649,-814,-628,131,453,15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getOutliers(int,int):java.util.List",
            new int[]{-1000,-758,-537,-571,873,106,1000,22,885,403,-586,-469,235,-646,1000,1000,-180,-902,1000,-377,764,-978,573,292,1000,-95,1000,-1000,-1000,-924,403,660,-557,-1000,857,-978,1000,1000,1000,1000,-1000,206,-23,-937,637,1000,-1000,-688,406,651,66,-643,165,-1000,833,392,-432,-1000,-587,-1000,-620,-489,-562,-886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getOutliers(java.lang.Comparable,java.lang.Comparable):java.util.List",
            new int[]{104,-899,329,-882,-1000,-330,57,-641,-634,531,-392,-1000,289,-858,-511,-421,697,-1000,-518,221,-1000,-1000,-123,-681,-298,849,306,1000,-738,-513,-796,-63,455,191,-125,-247,-1000,-618,-317,-99,621,1000,1000,678,-633,-1000,-1000,141,-504,795,-929,196,1000,947,1000,-950,139,-754,-1000,19,1000,-204,30,108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getOutliers(java.lang.Comparable,java.lang.Comparable):java.util.List",
            new int[]{243,798,424,156,-9,-377,1000,-458,660,-1000,1000,797,1000,-915,318,385,-162,942,958,-403,799,-638,-525,1000,1000,-392,275,-697,577,-54,308,965,-443,-409,-317,-955,-206,311,-479,-45,-407,-1000,-592,278,1000,-1000,194,-1000,-608,-231,1000,357,151,-1000,-1000,354,816,771,934,-1000,-1000,994,19,-258}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getQ1Value(int,int):java.lang.Number",
            new int[]{28,1000,-341,728,-143,462,147,388,-1000,-684,-1,-832,-667,139,-469,-51,-612,-629,-1000,424,633,-1000,394,919,518,-960,543,0,72,-647,-281,871,429,-488,-232,-23,-221,-708,-973,-506,188,245,1000,-567,-723,-692,170,982,210,-95,-425,-1000,-176,-244,-801,-1000,-983,528,615,-484,-1000,151,801,-668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getQ1Value(int,int):java.lang.Number",
            new int[]{4,-87,-377,-204,1000,-475,593,315,-576,-269,61,113,-437,1000,708,639,-357,926,-737,-144,-588,-819,872,-277,-1000,41,252,84,1000,-117,675,11,492,-85,-676,932,-400,-731,-647,322,-995,-79,-1000,559,858,9,789,1000,1000,351,5,-1000,279,1000,1000,425,806,187,627,341,193,-225,469,294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getQ1Value(int,int):java.lang.Number",
            new int[]{-418,359,-211,696,-1000,122,-1000,296,-1000,-714,-667,42,486,-571,-1000,1000,535,-732,186,411,72,222,-551,-374,1000,-1000,348,-666,-725,-776,4,400,1000,-1000,297,-149,1000,-1000,400,-31,1000,968,1000,-118,-788,-844,-629,1000,-1000,459,-1000,126,335,-814,-1000,-1000,-1000,934,617,387,-1000,-108,579,-796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getQ1Value(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-220,-1000,-242,825,1000,-1000,116,-1000,1000,273,1000,-740,-1000,-1000,-319,1000,-763,-780,-263,1000,417,-412,78,-925,754,1000,-660,366,1000,-374,972,-1000,-50,282,-1000,-1000,1000,-804,-966,316,1000,-366,1000,-167,258,-401,1000,859,-1000,1000,-751,-359,-1000,-111,-1000,91,-779,701,121,1000,520,521,1000,-298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getQ1Value(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-1000,-202,844,-108,768,-523,-401,-143,-253,122,-252,-717,-1000,-207,366,916,-763,882,-263,-1000,417,-1000,-125,969,754,-151,-656,-294,-401,-828,-238,-224,-1000,-1000,182,195,67,-170,-483,202,682,-366,719,-167,53,-773,561,952,-654,-1000,206,-602,311,1000,-1000,106,-1000,874,1000,-676,-198,1000,485,651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getQ3Value(int,int):java.lang.Number",
            new int[]{-849,514,873,906,-962,1000,-192,617,241,172,-114,-16,170,786,44,1000,969,1000,-534,-852,169,-515,1000,756,1000,1000,-942,291,-491,83,557,1000,-706,611,444,-722,1000,495,-616,403,-515,1000,-1000,98,1000,-661,-459,37,-719,223,217,1000,-1000,-1000,23,-1000,658,875,667,-327,-933,637,-320,26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getQ3Value(int,int):java.lang.Number",
            new int[]{-1000,-712,324,424,1000,-1000,687,315,-1000,-1000,1000,-1000,229,1000,1000,-1000,-1000,-1000,1000,-1000,1000,-96,1000,-829,-851,-1000,-1000,-1000,1000,-1000,-1000,-1000,577,-1000,240,-28,-1000,-487,-134,-1000,1000,631,554,-224,1000,-1000,-555,-378,-1000,-173,1000,-1000,-424,101,1000,1000,-332,-629,-775,87,301,-1000,-101,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getQ3Value(int,int):java.lang.Number",
            new int[]{1000,1000,233,1000,44,242,1000,-405,-1000,15,281,-220,490,-247,699,126,-69,-834,160,156,432,1000,-1000,1000,905,536,-632,805,-1000,309,1000,-96,-456,-364,-1000,-669,-672,1000,-255,-31,-1000,1000,1000,149,-286,-884,-1000,119,814,-732,834,580,1000,169,1000,-1000,-602,-976,-1000,-932,1000,-1000,-78,408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getQ3Value(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{472,272,579,-623,-752,-1000,-969,495,-377,968,-321,0,158,904,0,865,-1000,158,808,949,-404,-534,-536,-1000,0,580,210,0,-471,520,-263,-1000,890,1000,-603,515,-804,-656,0,434,185,-93,-97,881,1000,718,-154,0,-15,-187,349,-284,684,0,1000,-545,0,925,-51,-430,745,1000,-528,-111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getQ3Value(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-98,-1000,714,283,-265,-197,252,209,-550,-441,394,-487,-835,256,972,855,193,-1000,1000,-1000,351,-235,-542,-665,-1000,72,-1000,-597,1000,-281,661,914,29,-1000,1000,-646,651,-78,-507,-820,1000,364,145,-1000,-997,-1000,-469,655,-455,-400,705,1000,421,1000,924,-443,-703,526,1000,287,541,-846,1000,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.Range", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRangeBounds(boolean):org.jfree.data.Range",
            new int[]{191,-78,-297,147,785,1000,1000,552,-594,1000,1000,-1000,-210,588,652,-313,-919,-764,-1000,-626,-1000,-70,919,-811,-1000,811,-517,-1000,-662,252,339,1000,1000,1000,990,-547,400,-426,-536,197,76,-337,-486,-88,699,781,1000,997,-871,-1000,3,1000,339,966,-1000,-953,-977,858,1000,-56,-1000,-658,25,-877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.Range", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRangeBounds(boolean):org.jfree.data.Range",
            new int[]{-395,81,568,-627,711,197,1000,943,102,664,17,-371,842,-262,-1000,-825,-293,-547,192,-755,1000,1000,840,-183,954,121,72,896,-743,112,-528,-882,-1000,-642,-782,499,1000,-537,-1000,1000,1000,-1000,778,277,37,-1000,-549,869,-277,1000,913,-1000,3,-113,1000,644,1000,-395,-1000,68,12,-461,719,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRangeLowerBound(boolean):double",
            new int[]{1000,1000,-846,321,-295,-1000,180,442,-1000,-36,773,-857,-149,422,17,1000,343,-820,1000,-321,-1000,-1000,-902,1000,1000,-1000,1000,-76,134,-275,1000,-289,298,-333,-476,-212,-1000,321,-1000,-1000,1000,231,444,879,1000,-898,1000,1000,1000,513,-340,-1000,-110,1000,-988,1000,778,-330,-67,1000,-1000,435,344,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRangeLowerBound(boolean):double",
            new int[]{-1000,1000,-846,173,-295,-256,1000,-155,-534,-24,355,434,445,-50,194,371,312,-13,1000,-656,-645,673,-902,0,389,314,576,1000,-112,962,-267,-80,1000,344,-16,-478,143,321,-1000,-381,456,231,-898,-690,552,-898,855,724,-17,-1000,-503,-1000,-624,-162,75,747,-296,773,850,1000,-367,435,-64,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRangeUpperBound(boolean):double",
            new int[]{24,-208,689,426,-550,-494,635,957,422,-663,53,371,-1000,42,-163,24,-861,-533,-453,-46,169,-791,363,-668,-764,-744,179,664,-150,-1000,142,-892,-112,308,-743,538,476,-733,572,685,-223,-985,-51,-182,-216,-314,-837,677,265,1000,-1000,-270,-478,718,860,-731,-48,-233,-413,-1,-505,127,443,254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRangeUpperBound(boolean):double",
            new int[]{-494,-129,-477,-44,-950,195,-867,1000,183,-832,45,-1000,823,-513,-252,-672,235,-1000,-1000,112,-968,-1000,-1000,1000,1000,-447,-1000,-667,-1000,1000,-663,1000,-783,1000,504,763,1000,-811,-900,588,744,-193,-32,231,231,-725,-747,1000,-328,-1000,-421,-1000,-665,344,-900,960,-1000,-1000,-510,1000,-158,1000,-296,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRowCount():int",
            new int[]{-516,229,-646,-396,-195,-930,-411,277,-1000,393,-1000,727,104,-834,61,-507,-384,-523,-11,116,-290,-922,923,-1000,625,-173,422,-68,1000,-363,6,44,-927,891,397,-1000,-194,-502,913,-1000,879,-854,-1000,1000,-822,-761,-910,95,966,439,-1000,1000,86,1000,1000,740,-487,616,-65,-243,581,-354,344,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRowCount():int",
            new int[]{-1000,1000,769,1000,818,431,-510,-1000,1000,-1000,-1000,1000,1000,-1000,860,537,-1000,283,-875,1000,988,1000,642,55,-752,882,196,-514,1000,747,1000,-663,-841,-145,-510,-296,-384,312,337,599,-1000,-577,1000,417,624,1000,338,192,-194,-284,1000,-938,-1000,-220,-1000,-620,-946,-107,973,-176,1000,899,-816,758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRowIndex(java.lang.Comparable):int",
            new int[]{259,-556,-17,-306,-142,-76,-693,-961,-453,-460,468,11,27,617,-604,-602,626,799,-154,-17,566,-555,627,844,-931,-515,659,-559,178,844,-446,652,553,-471,566,826,834,716,642,-917,-830,797,-967,-407,-557,233,-505,-617,388,256,-573,302,-659,19,478,-737,-363,609,-864,-429,-150,492,594,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRowIndex(java.lang.Comparable):int",
            new int[]{-911,-697,523,900,914,321,-923,-389,-505,-568,-830,767,221,-885,-651,-95,-860,465,445,-478,195,329,-768,711,300,-718,312,-393,-572,375,241,650,-699,543,-955,-474,574,848,173,835,431,316,674,-970,432,-987,906,-24,128,-752,-528,606,-521,-712,214,-441,423,-185,76,460,-908,-477,-528,-466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRowKey(int):java.lang.Comparable",
            new int[]{1000,-322,-111,357,-868,-355,-1000,947,103,201,1000,-263,705,-684,397,234,1000,712,453,9,-483,-1000,275,-495,1000,-1000,929,1000,138,951,456,-1000,936,-906,402,1000,-481,-825,-184,-309,-187,195,-740,1000,-72,549,101,-708,1000,-637,-290,-1000,474,517,-1000,1000,-199,-1000,-869,1000,810,-1000,662,969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5MQ==", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRowKey(int):java.lang.Comparable",
            new int[]{744,-343,-917,144,-87,-679,-654,904,554,426,954,-395,359,-526,223,-914,798,996,-263,-790,-863,-1000,268,-284,827,-1000,423,658,-1000,358,87,-1000,-351,83,945,808,-544,-486,-693,-302,-281,150,-36,762,-719,517,42,-99,976,-56,585,-302,-764,-405,-1000,607,193,-201,-814,1000,470,650,93,767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getRowKeys():java.util.List",
            new int[]{-933,-907,64,-846,982,-115,1000,155,466,-795,847,-117,41,-810,-378,666,-550,-1000,-279,62,-163,-823,-848,-3,213,1000,80,37,-771,-158,-667,-542,718,-971,443,517,293,23,-783,1000,1000,-1000,-1000,1000,-981,391,-1000,-361,106,-129,749,1000,1000,-1000,-898,998,825,-1000,847,183,1000,389,93,-798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{458,-36,-722,-744,-782,1000,-1000,1000,1000,-725,-462,188,119,-1000,187,-756,1000,-323,1000,-1000,-115,91,846,-1000,1000,-424,-546,133,-386,1000,945,-291,-266,134,-494,-423,74,43,1000,-630,1000,535,-819,-824,1000,1000,973,-1000,554,475,1000,809,949,-676,-846,-695,-1000,-742,-1000,1000,-893,-386,-421,-593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{521,253,38,144,-15,-611,-293,-977,-413,293,832,-567,-863,477,48,58,678,-484,367,-337,-772,-428,983,541,891,380,-874,-855,-782,965,-280,-484,311,-472,280,-495,-568,-924,-275,221,628,-380,618,-325,247,60,702,169,-886,243,-443,-389,623,100,-315,-358,-976,249,426,257,-224,-982,-310,-628}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{199,-3,-147,567,468,519,-823,-751,-108,-714,-923,-54,-995,-285,709,-212,515,393,156,-338,467,-622,-978,609,825,-514,483,-957,536,550,-279,-911,-222,-251,-638,706,322,-717,-196,743,-290,-96,-899,566,320,-891,-510,501,262,167,-445,-48,-435,572,530,791,98,12,326,345,-688,460,-537,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getValue(int,int):java.lang.Number",
            new int[]{-875,-570,-827,1000,-654,-422,844,1000,1000,135,-329,-795,-118,416,-1000,-372,-442,583,-461,756,-539,669,-65,-691,-206,-18,807,-217,115,517,-1000,-746,1000,323,1000,-24,-79,-480,-361,16,889,1000,338,-385,156,642,-166,-610,1000,-34,82,-843,-548,-491,-940,-548,-435,1000,-285,1000,295,-210,-35,740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{-1000,791,-632,42,1000,-1000,-1000,-18,-966,756,997,-341,434,-760,1000,-983,1000,412,1000,-1000,-1000,-561,-1000,1000,-326,-696,140,-164,1000,1000,-1000,830,-222,52,1000,-865,600,-233,1000,701,-731,478,-936,628,-293,-4,-526,-571,-1000,-1000,-802,1000,511,986,723,-1000,420,1000,659,428,592,-1000,402,478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset", "getValue(java.lang.Comparable,java.lang.Comparable):java.lang.Number",
            new int[]{138,-670,824,656,-241,-436,-484,-127,967,285,-140,242,994,-657,382,-780,564,-828,420,-368,933,-939,227,-149,169,-974,558,-2,-491,-607,-333,338,182,-988,-742,-653,-557,-888,517,376,-299,-713,626,-274,654,800,-687,-922,-930,479,-61,-323,527,-166,378,471,-229,715,-476,-532,-355,807,-364,981}));
    }
}
