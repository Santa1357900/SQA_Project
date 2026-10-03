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
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getGroupPaint():java.awt.Paint",
            new int[]{290,-351,-932,-586,588,-1000,-757,-189,-436,174,645,-729,-326,-644,-1000,1000,1000,-620,-554,904,643,634,-1000,-1000,396,-88,-309,-149,540,292,248,500,-238,-275,551,1000,-860,262,-933,462,580,-44,-715,-242,591,-376,-296,-116,1000,322,-186,-1000,-1000,382,444,187,537,-1000,-225,-780,453,443,-447,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getGroupPaint():java.awt.Paint",
            new int[]{866,-520,-606,917,-1000,1000,1000,1000,397,586,378,-341,-536,1000,-509,705,1000,-256,1000,1000,-1000,-1000,393,711,1000,1000,-1000,-886,579,1000,1000,993,961,1000,-149,-215,-1000,-1000,1000,-1000,-1000,1000,1000,251,1000,-819,-1000,-161,-1000,17,-1000,816,-16,-1000,1000,508,-1000,-542,1000,1000,-520,-1000,428,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("COLOR:-8330501", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getGroupPaint():java.awt.Paint",
            new int[]{112,73,-16,-1000,1000,90,-1000,-1000,553,123,316,495,-384,-798,251,-716,253,772,1000,-1000,-6,-400,-694,-1000,-1000,-995,-79,-705,696,11,-1000,1000,-1000,952,-120,386,1000,1000,399,608,1000,-204,-1000,-1000,-1000,-192,1000,414,1000,129,-550,-323,-881,1000,-1000,-1000,806,20,-1000,-1000,-822,-938,-448,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getGroupPaint():java.awt.Paint",
            new int[]{-670,-705,478,-939,903,-343,95,-17,523,35,-177,-455,209,-645,-682,195,-181,-95,721,720,243,253,-756,14,160,-860,818,726,-115,-724,-539,69,817,477,651,401,-259,298,-600,920,-174,-546,939,-244,516,697,-5,-903,744,-468,-549,562,-120,-815,903,12,672,580,39,188,36,708,-873,155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getGroupPaint():java.awt.Paint",
            new int[]{-1000,-334,-313,-184,714,1000,-1000,-667,1000,-105,-1000,1000,58,475,-1000,-1000,1000,-510,-266,-868,-680,-1000,480,1000,1000,-993,-1000,458,-799,1000,-662,565,561,1000,253,526,-1000,-720,1000,11,349,-526,-1000,491,-200,776,463,-1000,137,-974,-442,-1000,1000,-1000,951,888,-137,470,-217,204,-904,-1000,376,739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getGroupPaint():java.awt.Paint",
            new int[]{894,4,714,583,-1000,-273,-1000,1000,-363,467,-218,93,814,269,-971,-1000,569,-1000,-554,748,-10,-932,-177,-946,542,-1000,995,353,-829,-604,1000,1000,-1000,-1000,116,895,1000,-707,-1000,288,1000,-1000,-1000,-1000,1000,-1000,337,765,127,1000,-999,-1000,884,1000,-1000,-504,1000,-918,1000,-436,1000,840,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getGroupStroke():java.awt.Stroke",
            new int[]{-1000,-569,-701,513,-966,-1000,-1000,-904,54,-661,-303,-1000,1000,-165,862,764,-1000,-497,173,-406,262,-1000,179,-1000,1000,-659,-1000,1000,279,1000,-1000,-132,-863,-1000,-930,1000,-243,-1000,158,-587,-988,-1000,-1000,1000,-805,1000,-1000,-264,550,1000,766,-1000,36,-464,-1000,-1000,697,-7,-784,-414,-344,-273,301,583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getGroupStroke():java.awt.Stroke",
            new int[]{-333,-523,-821,106,-667,-633,-416,-350,-186,-752,-584,-390,270,-1000,562,-183,-538,-988,-495,404,-340,898,-1000,-92,25,1000,134,499,1000,38,856,272,-741,-225,-250,-400,820,-515,262,-450,-841,1000,-189,159,31,817,-407,-941,749,315,792,-46,175,-567,163,-383,407,-127,-705,-590,-841,-759,-357,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getGroupStroke():java.awt.Stroke",
            new int[]{-1000,-1000,-451,861,-667,-1000,-711,-1000,551,-1000,-637,-64,1000,-1000,1000,-575,-969,-988,-1000,-329,-471,914,-1000,-649,852,1000,59,499,391,1000,856,-155,-741,-847,-625,707,778,-515,-292,-1000,-841,287,-189,675,14,433,-407,-1000,1000,346,716,400,862,-924,-1000,-932,925,-810,-1000,-648,-841,-643,1000,536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getGroupStroke():java.awt.Stroke",
            new int[]{-891,-211,71,-405,-755,1000,-529,1000,-1000,205,1000,160,650,-804,1000,1000,1000,102,1000,34,-363,927,-276,-40,-933,866,-1000,1000,258,-468,-645,1000,-1000,-1000,-604,-1000,1000,-791,1000,175,-329,-34,262,160,-554,88,945,1000,-227,-554,133,-911,477,-1000,-147,-1000,-615,468,1000,-265,351,-286,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getGroupStroke():java.awt.Stroke",
            new int[]{-380,-823,307,584,-733,733,55,185,-581,-838,-345,-114,-554,400,1000,1000,370,420,715,172,370,34,730,-381,640,1000,-836,-463,675,220,373,679,-143,580,-332,274,-353,220,1000,417,-156,-570,-723,-325,881,536,138,389,95,-733,900,154,414,-201,-225,-738,-312,1000,-210,334,-1000,-133,-198,-356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$1", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getMaxIcon():javax.swing.Icon",
            new int[]{369,912,-942,1000,-1000,-226,462,-135,601,235,728,-168,885,-981,360,-371,1000,-745,-1000,-1000,-66,-1000,-32,184,460,1000,84,-259,-583,-323,-1000,-121,958,470,192,895,-325,400,-463,-648,-241,367,-282,-1000,-201,398,264,-599,823,1000,-1000,-5,554,-106,-1000,45,-1000,275,-113,-1000,-663,528,-400,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$1", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getMaxIcon():javax.swing.Icon",
            new int[]{-529,55,328,51,495,1000,1000,710,-76,705,792,-1000,-820,1000,1000,-849,-1000,-132,1000,-386,-925,-1000,-1000,720,1000,12,-399,-555,257,306,-841,-11,754,142,529,291,-6,1000,-1000,-1000,-914,-622,-260,236,-1000,-351,-138,-109,223,335,-582,1000,643,-774,-762,388,-685,-74,-782,-779,-160,1000,-207,135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$1", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getMaxIcon():javax.swing.Icon",
            new int[]{967,-82,-116,548,1000,-408,46,-387,721,-1000,209,1000,-26,-1000,504,41,491,279,-1000,365,1000,-400,-322,681,622,584,787,-902,508,-1000,122,-814,-923,888,87,-237,148,-1000,-299,439,442,361,314,-1000,-441,784,272,297,717,-39,-418,-341,-497,473,337,730,366,-755,-905,314,-1000,-1000,393,-295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$1", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getMaxIcon():javax.swing.Icon",
            new int[]{897,-51,489,-49,-49,-757,258,250,316,-1000,-500,125,780,-776,-920,1000,-498,181,918,1000,59,-168,-34,-115,-534,-471,1000,-374,396,560,1000,-957,-4,1000,-387,-697,171,-955,-393,723,527,-155,785,-764,-1000,968,423,248,-1000,35,521,-422,-765,41,-1000,-562,816,-822,-153,1000,1000,-932,-972,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$1", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getMaxIcon():javax.swing.Icon",
            new int[]{1000,410,-302,-492,322,-697,-796,-910,-270,-914,-809,329,279,-880,-757,785,785,-417,-1000,123,359,1000,-284,-384,-984,-143,810,595,82,395,632,-182,-1000,1000,-218,-1000,-594,-1000,1000,1000,-241,1000,-1000,-26,1000,577,447,101,-843,-85,-167,-961,-107,-18,-27,335,61,534,699,685,223,-733,-468,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$1", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getMaxIcon():javax.swing.Icon",
            new int[]{-339,160,-276,-316,457,1000,752,314,-1000,-82,-503,1000,-920,564,-724,751,-1000,-874,1000,753,-660,1000,-408,715,1000,-1000,-559,-18,540,-763,680,1000,71,398,522,-464,-1000,820,-162,-1000,-477,684,-1000,-102,-920,-881,-11,-786,-1000,-1000,95,-343,1000,286,213,-626,-402,-697,116,1000,-552,1000,-503,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$1", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getMinIcon():javax.swing.Icon",
            new int[]{1000,606,-656,225,808,228,-1000,316,761,1000,-247,-253,590,-1000,-849,-907,1000,-1000,1000,399,704,-484,704,17,-942,1000,1000,1000,1000,648,-1000,-1000,-98,1000,-653,1000,-901,530,264,-635,695,400,1000,-316,-343,246,656,-1000,584,1000,1000,-908,812,740,326,134,-555,-1000,-1000,-766,-1000,330,-514,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$1", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getMinIcon():javax.swing.Icon",
            new int[]{-957,-122,-946,-70,1000,440,83,1000,-332,-218,951,-396,-1000,1000,-1000,112,-790,956,-1000,-1000,-442,550,-1000,183,380,257,-709,111,-1000,277,1000,550,924,-466,431,1000,1000,-696,536,-659,77,256,-1000,284,1000,-179,-535,-1000,-307,-1000,612,528,-749,-116,880,-1000,-1000,1000,317,682,968,-153,287,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$1", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getMinIcon():javax.swing.Icon",
            new int[]{292,-177,379,-653,808,8,-1000,-1000,-820,163,-1000,-253,854,1000,-178,-72,1000,74,319,-576,1000,894,-1000,944,436,1000,1000,705,1000,316,-1000,-338,278,458,-144,660,-1000,715,851,-1000,-781,400,-123,810,1000,623,-59,-576,437,-1000,-1000,-827,-825,-805,-180,-601,443,1000,-529,-128,-800,-364,-546,209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$1", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getMinIcon():javax.swing.Icon",
            new int[]{720,828,84,645,-1000,-537,-1000,778,-287,1000,1000,-469,1000,-1000,659,-1000,-1000,-1000,-934,-1000,1000,-396,-667,1000,-342,10,239,1000,626,1000,1000,-461,1000,1000,-545,1000,-1000,1000,1000,216,563,-1000,-488,-1000,1000,-819,1000,-1000,831,-661,1000,-1000,1000,254,-501,1000,-216,1000,-1000,-1000,-1000,760,393,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$1", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getMinIcon():javax.swing.Icon",
            new int[]{-74,-1000,528,-306,-200,584,415,-1000,34,-264,893,241,-720,1000,-1000,-454,-393,504,-886,-246,-828,-368,-1000,-1000,-79,-843,-1000,-1000,-727,410,-1000,651,-262,-268,684,-1000,1000,-736,-695,874,-151,715,-309,201,-594,1000,-1000,932,-475,1000,-1000,779,-387,-370,-672,1000,-163,-437,957,-370,976,424,-868,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$2", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getObjectIcon():javax.swing.Icon",
            new int[]{837,-802,-761,1000,-1000,406,-253,231,1000,-1000,-1000,1000,-1000,1000,1000,496,-1000,1000,1000,-1000,-1000,-411,-1000,-1000,1000,-579,-960,229,-406,883,447,756,467,-1000,-1000,833,-557,1000,1000,-1000,-680,1000,1000,1000,-1000,-94,1000,548,330,236,8,748,-1000,157,-698,1000,771,-240,-1000,-1000,-1000,-980,-623,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$2", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getObjectIcon():javax.swing.Icon",
            new int[]{-924,-1000,563,535,-1000,-343,-1000,-226,406,-371,-880,983,-668,521,920,-10,-1000,1000,427,-1000,-52,532,-1000,252,1000,54,-936,1000,829,322,-403,1000,706,-1000,416,1000,-270,-140,523,-889,-567,605,1000,695,711,-353,-930,1000,309,-238,553,764,-163,214,-316,-310,1000,-264,-1000,796,12,-1000,-918,280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$2", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getObjectIcon():javax.swing.Icon",
            new int[]{597,-1000,563,-45,-1000,1000,-86,752,1000,1000,1000,662,460,359,503,-354,-1000,-171,1000,-1000,-52,339,-400,-1000,721,214,-936,1000,-806,1000,1000,432,-921,96,-454,1000,-519,1000,523,-852,-138,945,-851,991,510,1000,1000,-393,598,-1000,1000,764,-1000,214,1000,1000,1000,1000,-465,-1000,807,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$2", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getObjectIcon():javax.swing.Icon",
            new int[]{-130,-681,977,913,-969,506,615,231,953,-705,27,926,-626,895,49,-595,55,933,484,-119,-721,-724,486,223,894,-177,-995,-388,-583,501,163,305,-214,347,-586,672,-341,42,518,-707,-195,565,982,337,128,-289,450,730,-897,171,708,-334,-999,-435,-512,916,207,378,-967,83,-898,103,521,459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$2", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getObjectIcon():javax.swing.Icon",
            new int[]{94,838,794,565,867,-941,-131,179,293,-1000,161,-623,1000,1000,-594,-775,592,550,83,1000,1000,-1000,1000,336,-1000,-1000,283,-926,-759,-967,1000,-1000,-318,1000,-447,-954,-89,-254,679,-572,1000,-1000,-64,-1000,1000,-519,1000,1000,-1000,475,342,-82,-125,-855,-831,-506,327,-204,1000,182,-347,183,518,-753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.renderer.category.MinMaxCategoryRenderer$2", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "getObjectIcon():javax.swing.Icon",
            new int[]{1000,-1000,-811,1000,-1000,44,-905,-1000,928,-712,-365,638,-1000,1000,537,632,-1000,1000,1000,1000,1000,-1000,-1000,-1000,-83,-1000,-832,-536,-354,-467,-313,660,400,-870,-1000,1000,-350,1000,41,-781,336,358,1000,406,-1000,258,1000,84,440,-1000,-1000,1000,-1000,1000,-1000,1000,-502,-665,-232,-595,-1000,-1000,485,854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "isDrawLines():boolean",
            new int[]{723,333,333,103,320,627,-1000,-1000,503,-946,517,957,238,676,901,-1000,426,851,-246,369,-493,-15,-28,-425,-527,93,-886,-837,233,-555,266,-817,124,-374,391,842,-628,-805,-956,-480,-369,-450,57,-1000,41,-67,-41,-1000,192,866,31,744,-146,-441,1000,287,-132,-55,303,-861,-1000,1000,431,-690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "isDrawLines():boolean",
            new int[]{474,-316,-506,-918,-1000,-1000,211,-80,-1000,765,8,888,-13,474,437,-964,554,1000,264,-604,-440,1000,-696,-169,237,696,-378,-326,-36,-165,-809,-614,235,168,6,1000,-223,261,-798,-275,-731,-69,741,-1000,819,-612,-715,-1000,-878,-1000,-1000,1000,801,12,230,-189,-267,1000,362,-836,-337,330,-234,-746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "isDrawLines():boolean",
            new int[]{1000,-751,-413,-927,-297,1000,1000,-598,-1000,496,767,-1000,-356,-830,-859,-227,780,474,867,-99,1000,379,-126,1000,237,-771,-117,-523,-514,402,1000,-164,-1000,-434,129,-1000,-863,-642,-1000,218,-328,-1000,582,481,-2,48,-1000,424,1000,-97,-907,-177,947,-292,138,-198,822,885,133,791,295,-279,159,-789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "isDrawLines():boolean",
            new int[]{1000,297,52,-933,1000,1000,1000,-1000,-984,-766,1000,-922,-836,-1000,-859,-755,815,-61,-69,724,1000,-669,16,1000,-851,-1000,-1000,-1000,-514,933,1000,-1000,-1000,-704,-275,-740,-1000,-1000,-814,482,26,-1000,582,1000,1000,287,75,1000,1000,1000,10,-1000,720,-1000,674,-160,822,-848,-934,1000,295,233,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "isDrawLines():boolean",
            new int[]{-1000,481,-237,284,448,-1000,-965,1000,109,566,-415,411,1000,-1000,1000,-1000,413,851,336,1000,-13,-18,237,884,1000,649,497,908,1000,1000,-1000,-913,1000,345,117,189,994,1000,1000,1000,116,1000,606,155,254,26,-835,-1000,-1000,546,-741,-547,-201,-38,-102,373,598,-1000,732,-1000,32,537,-577,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "isDrawLines():boolean",
            new int[]{-1000,-186,188,1000,1000,-1000,-1000,-265,223,-128,-576,1000,1000,-480,638,-219,547,1000,1000,1000,169,713,983,1000,419,-810,675,1000,1000,1000,683,-515,-1000,-95,-973,1000,-75,464,-1000,1000,1000,1000,-972,-593,120,-567,-826,-223,-959,1000,-617,-454,827,-869,-15,922,397,-1000,-111,-1000,-1000,211,-1000,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID|isDrawLines=java.lang.Boolean:dHJ1ZQ==|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setDrawLines(boolean):void",
            new int[]{-274,720,129,-856,-104,-577,653,826,499,-631,-234,290,-69,836,-917,-622,651,472,-413,597,742,-578,647,297,-573,-444,329,754,-155,170,-918,-751,686,-2,63,-70,989,647,-52,-377,-686,-300,-63,91,-181,629,-141,242,-666,383,746,729,-564,87,-921,41,-259,-398,-977,-273,-855,891,-617,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID|isDrawLines=java.lang.Boolean:dHJ1ZQ==|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setDrawLines(boolean):void",
            new int[]{-213,816,499,404,-853,-241,-627,-72,-776,73,28,-445,-332,-263,952,848,-484,-24,-159,276,321,-912,791,767,672,-151,1,277,-295,941,-105,-888,584,339,-223,332,577,876,707,40,296,-694,-839,618,963,115,204,18,-848,33,406,150,-653,771,-452,-955,-136,-161,261,-960,-752,477,199,506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID|isDrawLines=java.lang.Boolean:dHJ1ZQ==|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setDrawLines(boolean):void",
            new int[]{-439,-37,129,-856,5,-642,661,1000,499,-801,-234,1000,499,861,-1000,-1000,668,294,-413,-205,742,-221,374,416,-572,757,-710,49,693,-659,-848,-394,380,-1000,101,-423,884,497,-201,168,-625,325,687,91,-561,272,457,383,473,-918,505,670,-50,-39,-1000,207,-660,265,-1000,1000,-386,-162,-618,388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID|isDrawLines=java.lang.Boolean:ZmFsc2U=|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setDrawLines(boolean):void",
            new int[]{595,-581,-517,136,842,-241,542,-1000,-1000,-69,-46,780,605,-235,833,-1000,173,79,-1000,693,788,1000,-501,-363,672,-240,443,-38,-295,941,1000,-1000,-798,510,-167,-149,930,547,-1000,-1000,-407,167,-454,614,-1000,798,-553,141,969,717,-247,946,707,435,-113,-341,1000,-632,-890,-231,222,-269,1000,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID|isDrawLines=java.lang.Boolean:dHJ1ZQ==|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setDrawLines(boolean):void",
            new int[]{124,250,64,1000,-1000,-315,163,-455,-572,-935,1000,473,-568,-688,745,-170,-762,647,710,-808,639,-279,-333,-908,-879,-44,-492,-1000,-294,63,87,938,-1000,489,446,424,-424,-235,-982,-717,521,-202,-1000,-533,-372,319,-26,-591,-776,608,27,692,167,450,555,-574,1000,73,539,-20,571,-1000,1000,602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setGroupPaint(java.awt.Paint):void",
            new int[]{-1000,434,139,854,226,51,305,-1000,297,400,409,-853,1000,-450,-194,108,-417,-858,-293,595,-685,177,-1000,1000,-827,-389,213,-693,1000,36,-345,-1000,-10,-337,148,-581,-99,470,195,1000,905,355,-869,285,430,117,606,444,-20,-272,-182,-328,285,1000,-792,-344,1000,-546,-1000,-953,-1000,-400,-706,-482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setGroupPaint(java.awt.Paint):void",
            new int[]{-230,814,-763,53,226,-20,613,-684,434,-1000,353,-1000,1000,122,-564,-844,889,546,-557,-344,550,501,-461,1000,-229,-472,847,-604,406,170,-559,-390,-686,675,732,-198,689,306,1000,1000,1000,1000,-152,461,-1000,1000,393,979,-1000,969,-847,-1000,-93,403,-170,-412,1000,1000,-916,-343,-178,1000,-20,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setGroupPaint(java.awt.Paint):void",
            new int[]{530,-644,-511,772,193,114,-772,-789,654,166,-780,327,960,122,161,-612,984,714,809,926,984,836,-2,-826,426,506,488,799,-184,35,438,403,171,-596,985,973,-287,-855,141,804,392,-144,25,-702,-69,-110,291,449,203,-259,-439,871,-15,-839,642,756,-608,-845,353,-710,607,-336,919,746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setGroupStroke(java.awt.Stroke):void",
            new int[]{-72,886,454,-766,509,766,897,323,432,-788,68,-568,320,193,633,-771,-359,-139,362,-19,-561,-748,-999,-885,-360,63,-509,-238,430,555,-115,-213,-605,-912,684,-387,-903,521,404,338,-468,-235,175,-927,525,-825,-111,218,547,100,30,-515,650,-53,671,-133,-734,412,336,-524,-537,121,427,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setGroupStroke(java.awt.Stroke):void",
            new int[]{-441,1000,-201,-491,-136,504,1000,-441,1000,400,-824,-1000,812,33,637,-439,303,-288,-387,-666,-1000,-964,-738,-810,-554,435,-592,373,-38,-132,-522,663,1000,-576,355,-1000,-582,1000,55,725,-540,451,411,-699,68,-1000,122,-586,368,236,929,-925,-897,571,917,-1000,-602,508,-848,106,329,-476,765,-444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setGroupStroke(java.awt.Stroke):void",
            new int[]{512,-773,-587,970,-1000,-19,-271,-779,-1000,838,1000,1000,1000,-945,-1000,461,-213,1000,-988,-1000,823,598,-1000,-1000,-232,58,-1000,261,-1000,616,-332,-1000,-1000,1000,1000,1000,-1000,1000,1000,-499,1000,-592,1000,141,393,867,1000,-811,-809,-949,-280,-1000,-1000,514,-1000,289,704,-65,-1000,1000,254,-758,-38,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setGroupStroke(java.awt.Stroke):void",
            new int[]{946,886,-871,-1000,1000,-461,487,1000,-1000,-308,742,462,146,-328,-715,526,-299,-139,717,-678,-1000,1000,284,1000,495,-744,414,-1000,603,1000,-1000,-339,-1000,59,458,609,-1000,-332,659,-187,-1000,-1000,-146,-380,-373,1000,-111,218,-355,-779,-1000,-40,650,-1000,-593,1000,241,622,1000,-679,-964,191,-42,205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID|getRowCount=java.lang.Integer:MA==|getColumnCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setGroupStroke(java.awt.Stroke):void",
            new int[]{798,1000,342,158,-969,681,-387,-579,-1000,909,1000,1000,1000,-394,-1000,-683,-184,851,-564,-800,792,-1000,-361,-1000,-517,-18,-846,-6,-1000,-1000,79,-854,-1000,1000,756,1000,797,930,1000,-343,807,-261,767,-364,1000,491,1000,-162,-44,-755,125,-390,508,272,-1000,730,151,-140,-577,605,801,-429,-341,435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setMaxIcon(javax.swing.Icon):void",
            new int[]{-1000,-896,-57,683,634,-1000,-314,884,-442,-384,800,-1000,-163,-1000,-571,419,-698,-22,-128,-183,-111,723,-424,-1000,-730,662,298,301,852,-402,-175,1000,-865,-601,747,-940,528,1000,521,1000,64,335,1000,-681,172,-127,561,892,1000,1000,-467,-541,87,447,-539,-1000,1000,-120,61,-1000,926,-173,-1000,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setMaxIcon(javax.swing.Icon):void",
            new int[]{-529,-1000,818,-946,-775,-135,-1000,1000,-1000,-1000,732,-929,1000,-1000,-990,39,-189,-816,-81,-1000,463,299,-974,-1000,1000,1000,1000,1000,-265,-1000,179,1000,-306,-1000,-52,-795,802,469,-522,554,-266,-957,1000,-1000,-180,211,-1000,516,751,662,-1000,-1000,-914,1000,354,-1000,-687,-1000,-473,-339,-270,-635,-1000,-140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setMaxIcon(javax.swing.Icon):void",
            new int[]{708,305,-947,-946,738,-48,277,-216,-155,-114,-1000,1000,-101,835,-759,-382,1000,-1000,-406,368,-37,41,-1000,-101,57,704,-159,-1000,1000,848,-1000,-341,738,699,750,-747,-1000,226,412,360,-260,-753,-1000,277,828,-280,262,-964,-1000,-96,1000,458,312,-1000,-375,1000,1000,836,-381,399,1000,-659,-434,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setMaxIcon(javax.swing.Icon):void",
            new int[]{-973,-475,-921,705,605,-108,-828,-400,386,956,-308,958,-471,-857,556,764,-394,196,-116,-199,719,-19,-752,-772,-114,474,-356,11,579,-946,-780,425,87,570,802,-292,145,502,341,344,129,0,536,420,-922,-444,-881,-949,733,-346,524,-52,882,-133,-982,863,395,542,751,-125,480,-513,-367,-744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setMinIcon(javax.swing.Icon):void",
            new int[]{-829,311,-877,495,-1000,228,-2,494,493,-666,319,-1000,-42,-1000,-309,672,1000,-178,-18,593,-274,1000,-156,-67,-555,-221,459,519,-194,-523,-960,-383,-65,966,-237,478,1000,-557,807,-963,-601,-704,1000,-1000,277,577,-282,-164,522,-934,-935,458,1000,-264,-1000,-577,414,-1000,341,-736,957,248,-454,678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setMinIcon(javax.swing.Icon):void",
            new int[]{-769,984,328,919,-323,512,288,-879,-623,-374,-136,-768,992,372,-119,-356,-944,-986,759,957,-498,484,-304,-588,-641,431,182,972,284,186,-306,148,-876,-717,-468,721,-149,-543,-597,-395,-93,-534,595,291,224,-478,-652,529,25,-357,396,224,869,-191,-465,-137,-638,-548,-427,-781,430,-477,36,590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setMinIcon(javax.swing.Icon):void",
            new int[]{-247,125,-146,448,1000,1000,91,1000,-355,-619,1000,-959,46,814,149,43,78,-800,-324,1000,-182,411,-270,-230,-85,-20,-939,-368,650,682,-349,1000,-219,-1000,-756,29,400,-1000,70,-493,-508,506,641,364,919,-792,105,989,1000,-100,-258,416,1000,248,300,-165,122,-897,-94,-444,1000,58,-252,826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setMinIcon(javax.swing.Icon):void",
            new int[]{1000,311,71,323,-849,181,-75,-572,-378,-257,-12,-189,564,-202,1000,-1000,-1000,-636,91,-356,1000,-291,0,-370,488,1000,-506,-355,-450,-354,790,1000,-257,802,395,-1000,-776,-41,-1000,1000,-601,-67,-1000,-145,-484,-403,-508,328,33,355,1000,326,-536,-980,-1000,-540,-833,-54,511,-1000,1000,-1000,-910,373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setMinIcon(javax.swing.Icon):void",
            new int[]{-146,1000,-858,321,-1000,718,1000,-491,-918,-300,-568,-1000,1000,-406,1000,-372,-362,-300,623,35,437,1000,-1000,-158,153,122,-1000,-53,535,404,-1000,640,-457,-803,-304,-332,-505,-870,1000,-767,-571,541,1000,-102,-517,-1000,-790,525,162,195,-1000,-975,-965,-578,-598,-40,-1000,-205,-1000,-375,858,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setObjectIcon(javax.swing.Icon):void",
            new int[]{1000,-1000,-281,-674,-89,719,-268,-1000,-134,692,-269,514,681,-1000,-187,1000,-311,1000,1000,-314,-552,234,1000,-702,60,-1000,-1000,-268,1000,-484,-489,1000,-100,1000,102,-1000,348,-624,-1000,662,-1000,-672,55,-869,570,-377,784,-99,-1000,1000,-334,738,1000,-1000,1000,-68,816,-1000,-1000,-718,314,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setObjectIcon(javax.swing.Icon):void",
            new int[]{-1000,-168,-637,494,610,879,-635,-1000,1000,1000,926,1000,1000,-534,-791,1000,748,-127,75,-286,838,-1000,421,-425,1000,-1000,-522,-735,-734,-873,-1000,757,-1000,-1000,1000,-505,967,479,-1000,158,251,-1000,1000,-882,281,24,1000,1000,-493,-357,90,-1000,439,-1000,968,-1000,1000,899,-839,-672,-767,-923,1000,-661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setObjectIcon(javax.swing.Icon):void",
            new int[]{-154,-316,591,-315,-1000,-855,1000,-492,-51,215,823,185,1000,425,-1000,-342,19,637,134,1000,176,-356,937,950,-884,1000,-38,438,349,-1000,-9,-1000,708,212,587,143,-20,-838,-1000,1000,-696,-464,1000,-551,-233,730,245,86,172,-1000,1000,-503,-948,-557,290,-631,492,-1000,-199,-837,63,1000,1000,660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setObjectIcon(javax.swing.Icon):void",
            new int[]{30,802,-147,980,-945,-930,699,862,-902,445,906,-630,481,951,165,-654,-644,984,454,-897,324,-282,548,-10,-520,791,230,203,-259,761,-44,-491,806,-523,-186,71,167,-669,163,92,-800,143,-474,-390,-421,355,-970,-542,-3,-863,947,-873,-524,68,-227,-925,-539,-932,-353,-108,293,-520,-81,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "org.jfree.chart.renderer.category.MinMaxCategoryRenderer", "setObjectIcon(javax.swing.Icon):void",
            new int[]{-749,385,474,494,-605,564,-894,-792,160,931,-821,-10,803,489,-244,-364,400,-333,-225,-118,229,691,-862,-789,695,-74,872,119,-283,-770,216,-919,-23,-949,510,966,519,-229,344,-825,-205,-370,-387,-598,453,-548,-190,595,916,363,201,-590,-894,688,360,282,-442,315,934,55,589,937,526,92}));
    }
}
