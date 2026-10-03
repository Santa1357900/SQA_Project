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
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "addObject(java.lang.Object,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-1000,96,59,-351,-925,-1000,-1000,211,-426,-936,-617,548,-912,752,404,172,-622,-200,-834,742,-737,903,-399,-1000,201,138,248,1000,-322,880,956,-794,-420,39,1000,871,-60,-287,269,471,860,-958,-1000,-810,-376,1000,205,-222,-601,-72,-452,-1000,350,-1000,-356,1000,-491,-855,440,-1000,659,932,1000,605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "addObject(java.lang.Object,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{1000,1000,74,1000,917,-349,-1000,1000,672,1000,895,-1000,-683,-659,708,427,1000,-639,1000,320,-1000,209,931,949,373,-864,-418,-1000,-32,-593,400,-1000,-1000,1000,-1000,-700,1000,-1000,309,-1000,-1000,418,241,1000,1000,-849,-447,269,1000,355,-1000,1000,-1000,1000,458,-1000,1000,991,-698,1000,-655,-762,-124,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "addObject(java.lang.Object,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-1000,-681,604,423,-414,-712,-343,790,-987,-369,-570,-1000,-593,-81,-88,726,389,-52,-743,-342,1000,815,413,-231,-95,340,-775,-310,-809,-692,1000,-796,-94,413,-415,450,739,1000,290,1000,206,-243,-1000,824,-938,1000,444,316,-770,-346,166,-5,328,15,-243,196,795,-1000,239,67,-262,70,362,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.KeyedObjects2D|getRowCount=22:java.lang.Integer:MQ==|getColumnCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "clone():java.lang.Object",
            new int[]{1000,945,879,780,-1000,-1000,461,257,-715,-882,-355,-405,-854,-151,-1000,-1000,510,949,-1000,1000,-1000,967,-1000,-743,1000,1000,-1000,821,757,-743,828,-762,-153,-1000,-1000,729,1000,135,1000,-866,181,-87,-1000,764,-388,1000,-1000,686,-452,-1000,729,-1000,-259,-958,-651,158,-293,-356,-250,1000,376,681,-125,-806}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.KeyedObjects2D|getRowCount=22:java.lang.Integer:MQ==|getColumnCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "clone():java.lang.Object",
            new int[]{948,-550,-26,-294,-674,-580,440,-32,-58,-185,317,160,140,276,-176,303,572,396,-1000,-1000,1000,733,-859,-1000,1000,1000,443,559,250,667,519,154,259,-1000,267,-438,492,-935,-111,-505,-334,-764,100,160,-677,976,391,699,-478,-1000,380,-1000,-520,-199,164,-846,-1000,-676,1000,542,-1000,199,-852,-935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.KeyedObjects2D|getRowCount=22:java.lang.Integer:MQ==|getColumnCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "clone():java.lang.Object",
            new int[]{-1000,67,654,129,501,-258,321,1000,-1000,11,-483,486,-459,1000,969,709,321,-714,969,-318,756,274,438,710,-1000,-306,790,221,-361,-1000,-186,845,1000,689,-825,1000,-987,1000,211,-375,861,1000,-301,331,-553,807,1000,419,-601,922,4,928,112,-237,325,618,43,1000,-220,-48,354,183,922,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "equals(java.lang.Object):boolean",
            new int[]{863,295,698,808,433,-119,-182,-747,-19,-1000,-195,495,-491,987,-1000,-460,201,765,1000,390,189,-875,-1000,-769,935,528,-400,716,1000,350,-612,22,214,-885,1000,835,56,1000,-512,-283,414,-1000,-441,852,1000,-50,-989,1000,99,-1000,-213,3,369,507,46,-639,-516,714,-281,-326,-291,230,-1000,207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "equals(java.lang.Object):boolean",
            new int[]{-224,-523,-416,268,353,-400,-644,-715,-980,-114,-55,-1,-400,894,-520,-546,280,-100,1000,-766,584,-1000,-594,-508,-1000,-428,212,147,911,-1000,-245,149,-661,-273,-620,-463,135,814,-705,911,873,-1000,-208,573,1000,-400,-578,481,287,-841,-1000,-740,881,1000,-63,761,-224,652,5,227,1000,400,-952,-611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "equals(java.lang.Object):boolean",
            new int[]{983,154,117,1000,1000,372,-1000,154,297,-1000,-684,-84,1000,-697,-997,3,-132,433,401,-43,353,-1000,-1000,-1000,-1000,643,-55,-964,-24,-924,-530,-900,631,-1000,1000,681,-664,1000,5,1000,-385,-696,1000,-196,-1000,987,-767,690,-1000,-246,34,543,1000,-444,716,-1000,-1000,-1000,-1000,987,497,375,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "equals(java.lang.Object):boolean",
            new int[]{-265,517,-451,451,-492,-1000,2,826,770,1000,-260,-1000,1000,291,1000,-709,-1000,-1000,957,-1000,1000,-415,367,377,-765,113,-140,-741,921,-1000,-820,351,533,296,-623,-1000,804,-1000,-693,-576,1000,-1000,305,-44,1000,-1000,-1000,-229,-1000,1000,-508,341,347,1000,-607,-26,336,284,632,-1000,587,1000,-1000,-939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnCount():int",
            new int[]{1000,187,-451,1000,1000,-1000,-446,189,-1000,-428,-221,-810,1000,906,-1000,885,-1000,-574,1000,306,933,168,-911,-703,384,-1000,455,1000,171,272,-85,-248,-330,982,-508,1000,-878,-894,-1000,1000,1000,1000,760,-1000,-565,-358,-400,-791,-513,97,605,1000,-192,455,-1000,-1000,1000,-272,1000,-139,1000,1000,125,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnCount():int",
            new int[]{145,1000,569,-263,446,214,-413,162,620,29,863,492,-79,-215,-67,558,779,673,1000,61,64,459,448,-215,390,571,29,-453,-650,435,29,-998,272,510,-20,64,-209,-784,-317,-515,74,487,91,-28,594,698,-127,507,540,-2,263,-27,-1000,-652,-477,957,-85,162,75,-768,-924,-1000,449,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnCount():int",
            new int[]{165,-274,919,1000,-328,-656,299,1000,14,103,-413,-737,1000,-116,-482,-867,-320,-819,-499,268,-321,1000,-1000,59,-193,-909,1000,54,-1000,1000,810,-782,1000,85,-1000,941,132,1000,-1000,250,732,946,-32,-934,-878,-995,853,-147,388,-1000,483,-375,963,1000,-1000,-951,848,-117,1000,-1000,-212,-1000,860,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnIndex(java.lang.Comparable):int",
            new int[]{1000,579,-812,-529,-607,1000,-1000,-536,180,618,-90,-306,740,478,-1000,-216,758,-1000,698,-400,1000,-691,452,936,905,-1000,-1000,-1000,847,1000,30,-314,-439,577,-712,366,1000,-1000,-1000,1000,1000,364,655,-574,-1000,1000,-1000,543,102,129,604,634,-208,574,364,37,-1000,-540,-723,76,-792,-313,531,-907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnIndex(java.lang.Comparable):int",
            new int[]{-517,-142,-76,-1000,-661,-1000,966,-190,-1000,1000,-227,476,-835,250,1000,-627,-319,-1000,1000,-1000,-148,1000,-626,-292,1000,-274,1000,-790,1000,1000,-383,1000,-1000,1000,-906,-1000,68,1000,1000,1000,1000,-1000,855,1000,-342,-974,-825,1000,-1000,-1000,1000,138,-1000,-1000,-1000,-1000,565,-914,-1000,560,33,1000,473,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnIndex(java.lang.Comparable):int",
            new int[]{-1000,576,-946,-529,905,-207,1000,-311,-47,960,-1000,985,-717,-292,1000,657,662,-617,1000,-696,369,1000,1000,-600,905,-979,119,-1000,1000,1000,-985,-597,283,1000,-848,366,-969,1000,-1000,418,705,364,-280,865,-929,-191,-1000,-774,-364,554,289,572,603,-532,-1000,-105,-52,-426,-515,910,-1000,-173,490,-954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnIndex(java.lang.Comparable):int",
            new int[]{184,397,644,843,445,-400,1000,-1000,-123,422,-226,804,-322,484,400,17,-506,-1000,334,479,-13,400,-696,-563,344,223,176,-622,-1000,508,192,228,660,-491,253,266,-256,212,-735,-360,724,-288,628,374,-867,-1000,-366,820,374,-277,244,-171,726,579,-66,1000,1000,1000,968,-77,-13,-756,-504,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnKey(int):java.lang.Comparable",
            new int[]{388,1000,-111,-712,1000,-693,-259,-1000,-410,-1000,702,-1000,374,-1000,-436,-177,12,1000,-854,707,1000,-266,1000,-860,1000,-304,370,-746,1000,-380,1000,-720,85,-372,-1000,663,-1000,1000,-1000,1000,-1000,643,723,-1000,-1000,-1000,-325,-6,-427,-1000,-583,985,556,334,-73,-180,-153,-1000,-1000,-507,746,727,545,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnKey(int):java.lang.Comparable",
            new int[]{33,81,-707,-799,-12,-1000,-910,-842,-1000,-1000,-1000,228,-391,-911,-481,-277,-54,300,-1000,587,-334,-723,1000,-1000,757,-60,559,881,-272,176,71,572,-308,-479,-1000,493,-744,719,-851,720,-252,750,-573,-1000,-1000,-783,207,-335,184,610,-278,517,1000,204,682,-936,-1000,-1000,-330,923,-350,548,1000,-58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5Mg==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnKey(int):java.lang.Comparable",
            new int[]{147,573,628,-742,-1000,712,860,-723,-120,78,-719,712,206,-60,396,-250,-317,1000,-185,697,579,-745,1000,-172,295,-268,1000,-340,-292,-733,597,-144,778,231,1000,148,-359,197,-859,-36,-483,-182,248,-1000,-748,408,-848,20,976,-1000,-310,817,-1000,-1000,-460,-106,379,-1000,-999,219,-252,-1000,954,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnKeys():java.util.List",
            new int[]{-1000,-1000,99,357,-171,702,-53,-1000,385,102,444,387,-554,1000,-1000,-1000,-671,752,847,1000,513,1000,566,69,331,-572,-344,110,-380,-9,446,-994,435,-251,1000,1000,-1000,1000,924,73,-1000,153,-1000,-884,-284,327,333,-1000,1000,57,96,792,-941,-1000,1000,-153,727,-155,797,-1000,723,1000,193,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnKeys():java.util.List",
            new int[]{-467,1000,-741,387,-400,1000,-1000,-18,454,-920,1000,-83,396,1000,-1000,-849,1000,124,1000,551,-761,444,-141,-1000,1000,1000,-420,-1000,1000,1000,-352,128,-761,-1000,1000,1000,127,221,-928,-1000,931,-1000,-759,62,-436,96,-1000,64,-1000,845,1000,574,-219,-1000,-468,-104,-1000,-520,140,-615,713,-558,-414,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getColumnKeys():java.util.List",
            new int[]{-763,401,554,-152,-1000,689,-308,-751,1000,-130,264,-1000,-20,633,-1000,-417,158,-185,706,1000,458,1000,1000,423,-289,-765,683,-328,240,-525,49,-1000,-52,-529,1000,564,-912,975,1000,78,-1000,742,51,-743,337,-442,6,-971,1000,-92,855,-500,-303,-860,-155,-1000,-420,-29,949,-1000,1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getObject(int,int):java.lang.Object",
            new int[]{-427,405,934,-254,438,402,-54,905,-329,387,-230,122,-1000,-295,-683,359,-1000,-591,-10,-236,834,-275,686,947,12,-1000,897,683,393,-144,456,-560,959,1000,-329,-761,-361,-751,968,-176,922,26,1000,-746,-721,-1000,-791,-756,-720,1000,-845,1000,1000,855,300,-1000,42,745,-1000,-376,-224,-380,924,-71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getObject(int,int):java.lang.Object",
            new int[]{-186,-605,934,734,-648,596,1000,436,398,1000,-47,441,1000,772,353,1000,194,-575,804,55,552,-210,-495,-42,-680,772,-58,239,-913,-1000,-507,1000,-1000,-897,920,1000,-41,790,-911,1000,344,-1000,1000,-1000,442,-921,-565,-302,1000,335,-200,846,440,-508,-194,-1000,837,123,1000,47,980,351,-1000,304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getObject(int,int):java.lang.Object",
            new int[]{-1000,-1000,594,-784,-998,1000,-238,794,594,-284,43,-210,1000,754,-970,759,-156,560,902,-20,316,-1000,-1000,-1000,1000,-911,398,-1000,-1000,-1000,-1000,1000,-1000,-1000,1000,691,-812,1000,-1000,-440,97,-1000,1000,-1000,1000,677,310,770,804,141,347,-361,1000,1000,-523,-362,123,-981,691,-1000,484,-1000,-1000,131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getObject(int,int):java.lang.Object",
            new int[]{0,-298,479,-358,294,661,240,1000,-864,-1000,-1000,874,16,-800,-725,-236,396,708,-316,-386,912,1000,-227,183,-529,-797,-1000,-934,1000,482,-1000,-260,-248,352,-350,-994,-98,-557,984,302,-845,214,400,-229,205,-741,-331,686,-1000,1000,-309,464,1000,1000,278,-556,-227,-1000,-619,243,-631,483,998,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getObject(java.lang.Comparable,java.lang.Comparable):java.lang.Object",
            new int[]{1000,-1000,-166,-123,1000,-871,-953,-1000,-1000,-1000,-1000,-370,-53,588,-284,75,312,398,-1000,-336,-1000,-1000,-461,-1000,995,-271,-66,250,-1000,-722,967,-189,-316,-1000,367,379,-1000,444,-1000,-119,-75,107,83,-480,150,228,-214,-379,-415,-801,-487,1000,970,594,0,-291,-31,-1000,643,-293,-420,-549,-542,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getObject(java.lang.Comparable,java.lang.Comparable):java.lang.Object",
            new int[]{-379,145,239,457,687,1000,-1000,404,981,923,-609,381,391,554,-440,-317,-563,136,386,547,-203,553,120,-350,-462,182,392,-264,-916,-541,-304,-517,1000,197,410,-141,-87,515,-204,-67,1000,-624,1000,-133,-646,-163,657,1000,1000,-908,234,1000,853,1000,1000,735,658,498,1000,945,-37,212,-396,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getObject(java.lang.Comparable,java.lang.Comparable):java.lang.Object",
            new int[]{-1000,600,-281,517,1000,403,-953,868,-879,501,-1000,104,-1000,-442,-617,710,726,253,386,-271,-1000,-478,-385,-1000,-381,-113,52,1000,-488,-745,142,1000,502,-1000,-308,1000,-351,187,-398,-394,83,-471,1000,405,-771,-175,209,-132,387,-1000,-570,516,-600,1000,1000,569,-251,-467,1000,0,304,-487,-139,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getObject(java.lang.Comparable,java.lang.Comparable):java.lang.Object",
            new int[]{914,-367,-431,-147,-295,575,1000,-684,813,912,1000,528,1000,-445,1000,12,177,390,-1000,1000,1000,-191,-1000,738,-100,-1000,1000,-644,1000,942,-1000,-991,1000,-547,1000,695,-420,-207,762,-87,740,204,-577,-251,-1000,348,1000,1000,-407,1000,563,81,-957,-935,-313,-609,-1000,826,-1000,-178,1000,1000,-111,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowCount():int",
            new int[]{1000,1000,-801,-351,-643,356,478,-1000,-228,1000,935,-432,-1000,411,-553,205,-738,329,-357,752,410,185,-473,201,502,-750,495,111,-404,-778,-1000,732,-281,-1000,-765,-595,-408,-164,612,-501,451,1000,-608,167,358,-400,-582,-915,165,534,-931,-531,-428,-961,686,-74,1000,781,-996,-141,-148,1000,-334,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowCount():int",
            new int[]{1000,608,-111,1000,-1000,119,1000,344,-371,110,1000,1000,185,-1000,160,-750,72,-276,-288,1000,794,882,-314,-1000,1000,750,-858,393,105,-1000,-130,-586,184,558,940,-813,62,-1000,-396,1000,783,970,-1000,-750,-780,777,861,-250,-309,1000,689,-301,1000,-1000,-725,1000,-242,-1000,1000,-1000,-270,60,226,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowCount():int",
            new int[]{-267,1000,-366,378,-1000,-450,1000,-106,-953,-999,775,1000,936,-1000,-683,-430,22,-451,1000,-54,1000,1000,1000,-985,612,-339,998,244,-1000,584,-115,-1000,1000,-1000,-993,-697,297,406,558,1000,1000,-1000,-993,-232,1000,-1000,527,586,-1000,-1000,-114,684,924,-1000,-1000,348,1000,-689,235,-1000,-1000,-256,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowCount():int",
            new int[]{-1000,1000,424,1000,-1000,-1,1000,1000,269,-845,954,828,1000,-1000,-434,-786,375,-522,1000,1000,1000,1000,1000,-1000,268,378,-1000,-637,-1000,-1000,1000,-1000,1000,743,759,-579,312,-173,1000,1000,1000,-1000,-993,-1000,-504,204,527,-101,-1000,-1000,1000,273,1000,-947,-340,1000,44,-1000,-761,-1000,-864,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowIndex(java.lang.Comparable):int",
            new int[]{-689,1000,94,-768,-940,-606,1000,-1000,1000,-111,-386,-1000,-1000,766,342,107,-861,-707,-513,-53,112,-75,-1000,532,435,58,-778,-10,394,741,-686,-541,-302,-101,-510,648,465,137,729,-499,-391,159,-437,33,-1000,-1000,-395,-455,-1000,1000,-1000,1000,832,-1000,-1000,-1000,-213,-114,-31,-456,-409,-400,679,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowIndex(java.lang.Comparable):int",
            new int[]{100,551,769,794,-364,-131,616,342,74,-729,-996,-511,-213,-209,128,933,-977,-510,719,866,-398,-411,-839,-198,-342,494,-604,-418,596,810,337,-770,100,774,-435,-771,883,-595,151,-860,-306,99,-424,-501,-953,432,-52,-556,-901,452,967,606,-820,-91,-577,-509,-443,537,-584,766,221,764,-90,666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowIndex(java.lang.Comparable):int",
            new int[]{820,319,-221,86,-1000,-127,-388,80,-20,849,1000,-950,212,-9,-459,-396,1000,-1000,-1000,1000,1000,524,-932,1000,-798,690,95,-506,326,1000,379,-1000,183,-104,797,-1000,-141,-441,-1000,68,400,-296,570,735,666,-469,120,259,1000,451,203,-469,827,-753,-1000,-151,167,436,1000,-1000,340,27,-210,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowKey(int):java.lang.Comparable",
            new int[]{-990,-843,324,1000,518,-421,-200,256,1000,953,1000,-570,-234,3,-244,1000,-867,-312,931,-63,236,-268,-110,-943,-1000,976,-716,129,1000,1000,754,-1000,1000,15,780,-1000,-538,400,633,223,-975,-265,-397,-1000,196,1000,753,818,-943,856,1000,464,-916,137,-566,-1000,149,449,-552,-1000,34,534,-938,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowKey(int):java.lang.Comparable",
            new int[]{230,568,483,1000,896,-1000,-1000,400,-46,475,-477,-751,1000,1000,1000,1000,-1000,151,1000,236,1000,683,-169,-1000,692,735,1000,-1000,-580,-260,-143,-478,52,-482,965,-1000,-113,743,313,-850,-375,-75,1000,-577,395,-885,1000,141,-546,544,842,538,-372,872,400,-826,796,1000,-502,1000,1000,-203,-357,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowKey(int):java.lang.Comparable",
            new int[]{-939,137,-482,709,839,-312,-653,-420,938,693,571,-207,593,751,722,888,-265,-701,652,1000,813,-174,-660,-172,68,473,-790,204,201,1000,-1,-478,918,-252,1000,-1000,-96,-1000,-24,-953,270,-369,206,-441,35,895,1000,742,-669,1000,1000,-720,-241,286,-564,-506,-231,825,-683,288,-490,-1,-347,403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowKeys():java.util.List",
            new int[]{-186,839,-601,-232,1000,-669,-916,599,-704,748,394,-1000,1000,-180,-502,-24,-336,212,-77,-836,-231,1000,389,-1000,-568,-1000,-413,1000,-199,60,-1000,28,-39,239,-445,220,364,610,-90,98,626,428,1,-219,382,-505,-569,52,1000,-1000,-402,274,866,-1000,808,809,1000,488,-695,888,986,-790,-1000,526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowKeys():java.util.List",
            new int[]{1000,101,49,847,-576,1000,-500,751,-1000,-2,1000,-173,-378,13,-1000,449,654,-1000,-411,-141,-317,-1000,-1000,-881,-1000,-371,-1000,-1000,-85,-163,857,-883,-649,1000,-399,103,921,530,-980,1000,-494,-1000,-662,-638,-1000,-1000,1000,1000,1000,-1000,-633,1000,-322,781,306,1000,413,-554,1000,1000,-384,-803,-1000,984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "getRowKeys():java.util.List",
            new int[]{-1000,-694,-682,-764,-179,-1000,576,1000,24,156,-477,-459,426,788,-29,-1000,851,26,328,1000,-1000,366,834,-73,57,1000,-1000,256,-991,-352,389,1000,-542,-148,173,-37,417,928,-15,-589,-115,52,-607,430,-207,-834,-123,-453,-116,-461,-198,731,102,907,182,-1000,155,1000,1000,178,553,-506,-142,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeColumn(int):void",
            new int[]{506,537,244,336,910,593,240,364,658,924,164,-95,248,344,661,-209,-226,537,-275,-378,-798,-646,-1000,-228,-811,935,421,-110,-18,-402,541,573,-338,249,226,-384,543,771,509,133,1000,482,-171,863,897,-682,-28,-657,56,1000,-481,408,-446,498,218,-729,703,718,454,-657,-107,-157,-831,843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeColumn(int):void",
            new int[]{-578,-1000,-26,-665,1000,-528,-1000,-856,492,1000,1000,1000,-1000,1000,824,-317,220,1000,-677,-1000,-255,-894,-459,-1000,1000,1000,1000,860,-990,-1000,-639,133,1000,1000,-806,104,-334,1000,-495,1000,-1000,1000,1000,1000,-770,-1000,1000,-1000,1000,1000,-1000,660,761,1000,1000,1000,1000,1000,-669,1000,1000,111,-1000,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeColumn(int):void",
            new int[]{-587,1000,-576,672,1000,996,-72,-1000,-75,1000,1000,-1000,1000,1000,1000,752,-1000,-407,-1000,-1000,-1000,-486,-1000,-1000,-497,1000,618,-990,-1000,-1000,-678,1000,401,1000,1000,792,803,1000,-46,1000,302,247,1000,677,-1000,-1000,919,-1000,-215,-89,-88,1000,-1000,1000,-411,-1000,1000,1000,-756,792,-786,-1000,-1000,-744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeColumn(java.lang.Comparable):void",
            new int[]{819,823,-806,-284,-757,-1000,1000,-219,-615,1000,973,1000,1000,738,1000,-293,1000,252,1000,210,1000,-17,175,1000,956,578,224,674,-1000,-978,0,-1000,-926,1000,238,-964,-1000,-1000,1000,1000,-446,-710,-946,-239,-76,-30,568,-74,-127,-1000,440,689,-119,169,41,283,1000,-1000,-284,-96,-635,682,-1000,670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeColumn(java.lang.Comparable):void",
            new int[]{620,-684,-717,202,-63,945,986,405,463,-622,-285,-101,-683,550,445,-674,452,944,462,913,397,-566,334,-855,-784,-360,-673,-936,397,-855,265,955,723,-829,31,465,696,70,108,981,-444,350,888,509,530,-152,-183,195,-394,-848,877,162,803,-610,-608,393,694,-895,-297,-1,775,99,-162,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeColumn(java.lang.Comparable):void",
            new int[]{1000,202,167,798,1000,778,-15,1000,47,-1000,-1000,253,812,1000,1000,-1000,-1000,1000,424,852,-1000,-839,1000,-1000,-575,57,987,-1000,1000,-1000,905,1000,1000,1000,-743,1000,209,1000,573,174,362,-497,1000,-497,383,-1000,-540,482,-526,-401,839,-484,-781,-1000,-429,209,393,19,-1000,237,681,-1000,994,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeObject(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{1000,214,304,1000,585,-1000,-1000,-1000,1000,-876,-93,185,483,1000,1000,1000,-1000,303,1000,-1000,760,220,-940,1000,858,-1000,-379,-524,-1000,-1000,-1000,421,426,-1000,1000,804,1000,-1000,-233,-513,1000,-134,20,912,-30,-1000,-73,1000,-1000,-949,-1000,798,1000,346,94,-851,1000,-1000,-367,-1000,575,513,-1000,-897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeObject(java.lang.Comparable,java.lang.Comparable):void",
            new int[]{119,-112,189,331,196,874,-1000,-499,-44,349,490,469,-452,557,-963,773,1000,-1000,186,507,-729,110,-162,-754,-552,1000,438,1000,429,973,-36,1000,-357,1000,-1000,1000,-275,1000,791,-334,335,405,316,-752,-496,1000,-360,-497,-211,332,593,291,1000,-1000,1000,433,-595,-320,-134,491,-706,43,1000,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeRow(int):void",
            new int[]{-1000,1000,229,-957,246,-137,-764,395,1000,1000,1000,-1000,-1000,-1000,1000,-305,1000,1000,1000,187,384,1000,1000,270,1000,-1000,-1000,1000,1000,35,958,-1000,1000,-1000,473,-1000,1000,392,-267,1000,-230,-368,1000,-1000,1000,1000,-530,-944,1000,1000,271,-1000,1000,1000,-1000,-1000,-1000,961,-1000,-932,-1000,-1000,-804,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeRow(int):void",
            new int[]{313,-681,374,1000,1000,1000,-1000,-833,-234,935,-1000,-455,1000,653,65,-1000,-9,-1000,-1000,-1000,743,-1000,1000,-1000,-394,999,-4,-569,-606,-1000,-208,-912,683,1000,-689,1000,-1000,1000,341,-1000,-1000,-820,-471,-870,-412,619,1000,-1000,67,-728,1000,-1000,311,-488,1000,1000,-689,-1000,402,-1000,1000,1000,150,-522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeRow(int):void",
            new int[]{-908,1000,-616,471,57,1000,-1000,14,955,1000,-305,-1000,-492,626,1000,965,860,275,1000,46,-691,1000,1000,-645,1000,-552,-1000,1000,1000,-618,1000,-357,1000,-1000,-852,-1000,1000,1000,250,-750,-312,-121,1000,-817,1000,-484,-1000,-1000,726,1000,-386,-1000,1000,1000,-1000,-1000,654,-339,-1000,-1000,-1000,91,-596,-761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeRow(int):void",
            new int[]{780,262,-192,793,-630,678,-596,-299,-157,86,-571,-486,806,801,-54,916,-477,-20,431,896,1000,-828,306,153,261,-320,-225,487,1000,236,504,-8,277,-963,-1000,825,286,493,-40,-1000,86,-157,-700,27,702,12,421,-1000,-20,324,-758,294,-469,387,184,955,-119,-1000,1000,204,100,497,-3,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeRow(java.lang.Comparable):void",
            new int[]{-1000,1000,304,-137,688,-511,-924,-374,-458,998,-155,-1000,-179,-77,1000,1000,-1000,1000,-602,-770,-1000,997,-548,-1000,647,-812,156,1000,-355,-75,-487,-304,1000,-1000,-283,690,-1000,1000,-640,-467,945,1000,1000,451,-701,-817,480,-771,-632,189,312,-518,-1000,-738,60,-1000,70,1000,-1000,1000,186,-456,260,442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeRow(java.lang.Comparable):void",
            new int[]{-1000,1000,-251,856,1000,-889,-1000,-1000,895,1000,353,-1000,-179,317,87,1000,-1000,1000,-1000,-1000,-1000,997,-968,-1000,1000,-1000,-264,1000,-867,-290,-706,-662,1000,-1000,-42,924,-1000,1000,-840,-1000,1000,851,1000,358,-956,-1000,-234,-1000,-671,664,42,-294,-1000,-594,697,-1000,-491,1000,-1000,1000,1000,646,575,794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.UnknownKeyException", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeRow(java.lang.Comparable):void",
            new int[]{1000,-881,74,28,-1000,821,914,253,204,-472,96,400,550,-215,-242,-1000,1000,-1000,1000,1000,1000,-516,1000,1000,-1000,135,794,-155,875,301,-590,934,-706,1000,-950,-1000,1000,-779,-800,-289,-1000,875,-1000,319,112,1000,983,188,908,-1000,-112,-528,300,716,-1000,1000,14,-1000,979,-1000,-453,-494,437,-583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "removeRow(java.lang.Comparable):void",
            new int[]{1000,-519,-781,-348,702,951,-445,370,678,-106,-46,143,-271,1000,228,256,-674,-489,-394,578,-148,850,-536,877,-229,470,1000,-503,-171,311,546,198,-174,91,-53,-297,-439,1000,-1000,1000,44,-90,-622,182,742,790,1000,619,380,-568,942,-1000,-555,-507,-626,1000,670,-319,-560,919,0,643,869,-950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "setObject(java.lang.Object,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-638,-1000,839,707,-263,259,609,-468,906,-713,1000,372,-786,-798,-797,270,-1000,398,-622,-756,1000,1000,406,-1000,493,-1000,-1000,-1000,-970,545,1000,-1000,515,1000,-875,-71,-11,-606,1000,-228,-1000,648,-326,-1000,-291,-1000,1000,16,430,708,905,938,-43,1000,1000,983,1000,-204,40,656,-446,-1000,522,694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MQ==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "setObject(java.lang.Object,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{-1000,-1000,-101,-491,67,-205,-339,382,1000,-502,912,-848,336,-158,-849,764,-842,-284,75,212,-3,1000,21,589,503,911,13,-516,-543,-336,1000,468,865,-359,492,484,148,40,319,1000,903,-507,-529,1000,-493,620,-58,1000,-144,249,639,-1000,784,956,534,-527,993,-504,147,228,277,258,113,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:Mg==|getColumnCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.KeyedObjects2D", "org.jfree.data.KeyedObjects2D", "setObject(java.lang.Object,java.lang.Comparable,java.lang.Comparable):void",
            new int[]{1000,94,-521,1000,1000,-189,700,-652,-796,-698,-172,270,170,-837,394,-918,-534,249,139,-170,630,307,1000,-422,0,-428,-1000,-1000,421,1000,490,-1000,303,209,299,687,1000,-803,131,-1000,-1000,1000,-425,-1000,375,-495,522,-768,-51,987,1000,1000,-1000,-444,-388,703,354,-612,-690,319,410,-1000,256,906}));
    }
}
