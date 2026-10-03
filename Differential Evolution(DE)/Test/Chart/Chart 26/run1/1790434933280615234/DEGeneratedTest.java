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
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{908,285,904,-602,755,96,109,-335,598,4,619,383,-705,-1000,1000,-594,-237,892,70,388,709,-1000,-775,400,454,-11,100,1000,1000,-178,-587,-886,-263,652,174,449,-769,-1000,-654,775,848,-793,103,-557,-144,1000,855,-220,-123,417,350,-721,483,-621,-427,741,440,597,1000,-83,-1000,199,270,-542}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{-997,409,-530,248,409,2,-212,-804,-876,-1000,375,-83,-929,-528,-1,-511,1000,561,-28,-254,1000,-466,1000,449,308,-585,271,-325,487,-619,-545,344,1000,870,-1000,-778,-753,818,-579,-373,584,267,47,407,395,92,827,451,1000,121,-697,298,-807,-639,796,1000,464,727,212,-406,-1000,252,855,-579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{-39,-1000,-806,-694,1000,-790,-191,-117,494,421,-691,837,-321,-956,670,-1000,427,344,-1000,460,583,-657,-917,-659,-564,1000,657,783,-381,206,1000,1000,588,1000,-1000,585,-952,-699,-1000,498,349,-489,-1000,1000,-1000,-1000,-1000,-269,116,74,428,668,-663,-1000,456,112,-1000,1000,1000,-351,311,1000,-51,927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{1000,-1000,-751,191,1000,-1000,241,-577,-230,121,13,-215,330,335,555,-915,120,1000,-322,506,1000,-1000,488,-410,-663,-163,-137,-318,259,-796,115,368,-370,1000,-754,304,-984,-350,-1000,454,376,109,-276,558,-240,-702,-543,206,-1000,929,358,18,-636,-1000,547,23,270,1000,1000,-156,-575,994,550,952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{744,-932,24,-1000,-163,-1000,618,-359,714,273,70,-420,780,-368,14,-1000,104,-340,-541,1000,474,515,-972,-1000,-878,1000,1000,-345,-1000,-159,1000,885,-1000,418,-556,1000,-333,-992,-1000,1000,-1000,-1000,-1000,1000,-1000,9,-785,-97,-96,1000,1000,191,-260,-1000,132,-398,-725,781,1000,-577,3,1000,-745,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{1000,-460,803,-1000,109,-387,-532,279,1000,1000,-909,975,-598,-1000,-37,-1000,-1000,-1000,-648,900,40,1000,-1000,-415,-211,811,1000,1000,-1000,-1000,1000,721,-1000,708,-46,1000,-604,-1000,-1000,1000,-1000,-1000,571,1000,-1000,28,-398,476,1000,1000,1000,730,-840,-1000,-354,-701,-1000,499,1000,-184,1000,473,-1000,359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{1000,915,1000,-796,534,1000,-536,-452,1000,560,13,410,-253,-1000,383,-301,-740,522,-213,302,-110,-76,-1000,-58,663,1000,99,1000,424,-151,-147,-923,-370,553,1000,116,-520,-1000,1000,-829,376,-604,-276,-1000,-170,-121,-71,482,-1000,-1000,-614,-201,201,849,959,-185,906,747,-45,-67,-512,-1000,754,461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{1000,415,1000,426,65,1000,-221,-575,425,-128,-430,495,-344,-766,-650,-802,47,337,70,663,-771,-489,-678,1000,139,-259,-424,961,1000,-324,-1000,-1000,52,848,1000,1000,-648,-878,359,679,-650,-183,745,-1000,632,711,992,232,-292,-1000,38,-697,-276,596,397,1000,1000,-344,-225,405,855,-1000,591,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{665,-230,324,-1000,1000,969,1000,1000,352,1000,124,-130,-739,-394,263,52,-1000,-252,247,-126,1000,-367,-181,-865,-225,896,1000,28,-461,639,-766,966,-1000,-382,-1000,274,-293,-523,-1000,1000,-287,-742,-294,-1000,-1000,603,-254,-994,386,1000,1000,-76,-462,-1000,-1000,-308,-803,955,1000,-1000,-465,1000,-1000,-147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{-367,-362,919,42,32,-686,1000,-1000,1000,35,765,-1000,1000,128,609,-857,833,-527,238,404,-1000,-832,-1000,-989,-721,1000,465,-925,-381,-1000,568,-820,132,521,-411,520,-29,-1000,400,498,57,547,-1000,111,1000,-939,-976,-442,229,423,-904,-991,1000,-472,990,-292,-157,462,-93,989,440,-187,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{1000,-1000,34,-795,43,96,420,-304,1000,653,-409,-1,979,-10,350,-594,-358,39,-928,1000,114,172,-1000,-778,454,1000,100,214,-1000,-67,1000,596,-1000,695,174,1000,-607,-1000,-1000,1000,-729,-1000,-862,1000,-926,-471,-862,95,-1000,1000,1000,195,-257,-1000,158,-288,-510,649,1000,-184,504,1000,-577,969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "addChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{792,121,468,-708,759,-607,899,282,174,419,44,-93,93,-595,750,204,-322,726,281,127,691,-612,-473,-891,-119,1000,1000,524,-810,-166,152,-307,-735,94,356,-246,-887,-1000,19,662,-726,-346,-942,309,-612,384,-196,105,651,625,417,-466,-465,-354,-505,282,-345,617,1000,-599,-1000,857,-390,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{1000,214,539,376,-1000,-881,-708,588,-386,522,53,1000,176,-129,-126,6,334,1000,-194,416,121,-143,655,-18,1000,1000,-1000,-1000,831,1000,-1000,273,20,-1000,1000,-606,1000,-251,134,-213,1000,282,1000,287,-569,830,845,-326,679,-843,894,203,983,-613,-1000,-1000,-295,-103,-858,298,166,-128,1000,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{-1000,1000,-881,-1000,-1000,-1000,405,659,385,-327,-405,1000,-1000,-613,1000,125,-857,-1000,784,-1000,305,1000,-408,-516,-1000,595,1000,1000,-1000,1000,310,252,1000,-91,1000,-1000,-103,345,924,988,-1000,721,984,-683,664,143,547,1000,694,-319,-931,-217,-1000,-987,-450,177,-1000,233,636,493,-56,-1000,698,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{-1000,-1000,143,-843,473,-1000,1000,1000,504,1000,-405,1000,-156,-506,-1000,-1000,-1000,234,-292,1000,305,856,-314,-141,-80,450,584,1000,848,1000,-122,252,698,-628,-74,-161,410,-201,1000,-85,-419,1000,1000,1000,1000,-1000,-1000,1000,-803,338,-639,621,376,-1000,-458,626,-1000,1000,636,1000,-56,227,764,-700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{1000,-428,834,-353,202,-238,-382,-276,-345,-180,670,374,-219,834,283,617,1000,921,-11,872,58,-320,-181,-465,721,-149,-1000,-448,115,-1000,-1000,1000,-1000,396,-115,1000,864,-544,-498,-469,-54,-1000,-372,311,-716,-87,-740,-901,807,-244,1000,883,1000,880,185,975,1000,-1000,700,-965,978,359,-617,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{-552,1000,-11,-38,-1000,440,678,-521,1000,579,-454,420,-203,804,585,528,146,921,431,-37,473,-320,1000,379,1000,1000,-867,-360,494,1000,1000,-623,-572,-878,1000,-626,864,-1000,874,-469,1000,-596,1000,-297,1000,496,672,-159,1000,-244,134,432,1000,-402,-1000,-872,-563,-120,-638,-267,-365,-904,413,-529}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{-704,779,-659,-30,-308,-192,-396,1000,-1000,196,-905,292,28,941,-103,316,-354,243,-971,-54,609,-135,-251,216,-1000,708,-1000,-1000,1000,878,43,-795,260,-324,928,-634,379,-324,-889,-1000,285,73,481,1000,-427,483,-283,1000,656,-1000,1000,811,1000,-477,-866,-324,-295,643,-1000,364,-130,-112,546,877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{688,845,27,-809,968,-446,448,728,-1000,340,142,24,-1000,1000,862,857,652,165,624,400,988,135,-1000,294,-840,795,-518,346,-758,-1000,993,-968,-840,159,-289,-273,-1000,-1000,-910,-399,-553,-512,-561,422,94,-554,-1000,1000,758,-804,1000,290,314,945,579,-382,1000,-949,1000,-1000,-572,-613,-1000,908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{-1000,214,-547,-604,-1000,-1000,749,-400,1000,532,176,1000,-563,-842,28,-754,218,-667,1000,204,56,866,61,-580,104,916,827,1000,-290,1000,929,-10,2,-274,833,-667,-87,-411,1000,1000,53,-139,833,-136,347,133,1000,-400,-160,-576,-821,-369,-31,-1000,-316,-971,-980,-169,-1000,86,-74,-151,434,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CyclicNumberAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{-554,-738,796,-706,642,-1000,839,-424,794,629,961,611,-326,-218,-141,-445,88,-445,784,836,24,924,-366,-522,732,-14,576,678,-423,232,158,817,-832,325,-19,257,145,-396,899,772,-185,-703,354,285,1000,-781,857,-357,4,-153,129,408,122,-217,75,196,106,-394,567,-143,1000,95,-36,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis3D", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{-835,291,1000,-549,767,-193,264,1000,-730,816,-1000,-925,738,-221,-564,-603,-220,603,-768,1000,-182,-40,-226,92,-373,817,-467,-140,736,723,1000,-854,79,-61,-737,-540,38,-1000,-924,-1000,997,-924,423,558,-380,638,-701,786,-1000,-839,165,760,1000,-918,-594,-1000,124,392,470,-2,-182,-1000,167,428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{-1000,1000,-11,-202,642,-1000,678,-487,1000,1000,1000,1000,204,-1000,-607,-1000,88,-981,-928,334,-873,965,670,-347,1000,1000,577,-1000,-64,232,607,388,498,-945,-622,-258,1000,-869,874,757,-522,1000,1000,572,1000,-906,857,297,-1000,-33,965,13,-761,-1000,135,123,106,825,555,1000,1000,182,1000,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CategoryAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{1000,1000,-872,509,-657,767,-652,525,-430,-886,903,-390,757,1000,1000,-361,1000,-569,-718,-142,625,572,-58,-126,-459,-966,-1000,-622,-182,-746,1000,-1000,-533,692,212,474,824,325,-1000,-387,-12,-1000,-1000,-508,62,124,-19,1000,1000,-85,-141,1000,1000,1000,-1000,198,1000,-1000,-685,-358,422,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.CyclicNumberAxis", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "clone():java.lang.Object",
            new int[]{926,214,-205,1000,-728,-1000,749,-1000,448,1000,155,-945,850,-986,405,-31,958,1000,1000,-684,56,701,1000,-611,241,1000,-990,194,-63,1000,-442,-10,-992,-55,581,-667,1000,-1000,1000,-494,1000,-1000,458,-1000,-1000,-125,-827,-729,-497,534,-255,-676,1000,-1000,-1000,-913,-1000,-169,-259,261,410,970,1000,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{1000,-1000,169,-79,-475,593,-576,-935,994,-152,-875,-695,-763,-1000,-1000,1000,1000,-303,-451,941,-1000,346,1000,392,-1000,292,-421,-1000,-514,-1000,651,541,942,596,116,512,615,-164,535,-655,-1000,963,-43,788,99,-1000,-690,-1000,-1000,1000,-602,1000,1000,-68,-431,-564,531,-1000,870,-1000,497,-505,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{265,-1000,-131,477,-557,-202,874,730,260,154,-1000,-154,142,714,545,179,820,1000,-310,1000,522,-167,709,-664,847,1000,1000,-863,-141,310,991,1000,-6,1000,1000,-1000,203,262,-594,160,501,-1000,-396,-667,-757,-1000,150,562,-67,532,712,-21,191,504,578,-483,-243,904,-872,-332,-995,-248,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{131,-944,-766,-141,-54,-156,-184,-205,191,569,-1000,-607,-1000,948,152,685,-61,-213,-1000,1000,-416,716,1000,-157,-980,1000,-1000,-244,115,-467,-1000,-824,1000,1000,1000,1000,1000,-24,-1000,-1000,-586,-806,-1000,-1000,711,-137,-1000,-1000,-356,835,-365,1000,843,-171,-1000,832,1000,79,792,-1000,703,-1000,1000,-726}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{-567,585,-331,-275,-125,-72,608,527,-263,1000,422,-477,586,43,536,-694,-623,1000,549,-854,853,-114,-882,-703,560,-386,342,551,-231,-193,-563,221,-502,-367,-1000,-721,-638,-114,1000,370,353,-298,481,-259,1000,-710,-628,1000,-26,462,-287,-441,308,-418,425,-749,-352,-125,193,847,555,673,-3,-148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{684,-1000,478,-646,-735,1000,-346,531,1000,68,-1000,-93,-1000,1000,-1000,1000,1000,762,-1000,1000,-1000,1000,115,-607,-1000,1000,-804,-1000,-85,-1000,-484,-331,895,717,1000,1000,858,353,-835,-887,-1000,-462,421,954,-89,-659,578,-1000,-1000,1000,-1000,345,1000,-1000,-583,1000,1000,-1000,-349,-1000,1000,-976,-813,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{1000,-1000,628,-595,-514,884,581,410,-169,-748,-1000,-587,-1000,811,-1000,829,-547,985,227,1000,-1000,-554,1000,-750,-1000,-400,-415,-1000,959,-316,373,-353,1000,1000,-400,157,735,230,-615,-112,-539,-959,-384,705,884,-46,756,-1000,-1000,1000,-734,1000,710,-1000,-171,1000,451,-160,-721,-1000,1000,365,-400,624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{1000,309,-1000,-1000,720,-560,189,-830,793,-861,-466,433,1000,315,294,-1000,-322,779,-591,1000,722,857,307,-1000,1000,1000,14,338,1000,530,-380,-76,-983,1000,1000,1000,1000,338,-269,-910,795,-362,481,485,-1000,-784,-737,562,981,281,-509,320,786,-1000,1000,-145,-559,-116,1000,-1000,1000,-1000,846,-484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{1000,1000,-721,-189,-13,-1000,72,-629,1000,-841,-1000,704,-1000,1000,-605,1000,1000,1000,-1000,1000,-591,1000,1000,-396,-986,1000,400,-599,-783,-802,-146,288,1000,1000,1000,1000,1000,893,-1000,-1000,-1000,-1000,-1000,-751,919,-870,-795,-1000,152,1000,-696,1000,914,749,-893,374,1000,-484,661,-1000,-380,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{1000,-628,144,-312,262,-273,-499,-321,-318,-552,-86,-1000,-416,-1000,-424,-123,643,-652,-323,248,89,367,680,106,-174,-27,-143,-331,-16,-825,-654,817,897,38,-334,-279,-733,497,1000,-387,-1000,1000,699,1000,842,-187,-738,-908,-1000,585,-541,-400,1000,650,684,1000,-296,-708,1000,-329,441,-149,-1000,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{1000,-961,-208,202,-338,-652,-88,-1000,1000,-235,-1000,250,-1000,1000,-322,1000,1000,151,-775,1000,-591,1000,1000,63,-986,1000,515,-573,-837,-867,116,241,1000,1000,1000,1000,1000,270,-1000,-814,-1000,-717,-1000,-1000,945,-913,-400,-1000,-192,885,-138,1000,730,410,-177,66,1000,-345,254,-1000,-706,-1000,1000,-808}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{469,-239,439,857,-536,-456,-794,-400,928,738,-1000,-414,-1000,315,102,995,-497,-1000,1,-173,-102,413,134,632,-976,-609,771,338,-1000,-1000,23,941,739,-943,-209,935,46,-647,-176,1,-1000,-76,-394,-1000,1000,-1000,-761,-462,490,861,823,-578,1000,1000,-424,18,-492,-231,319,-557,-38,-701,1000,-501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "configure():void",
            new int[]{469,-1000,-551,-374,519,-611,-443,-1000,-510,-1000,-628,-998,-740,1000,-1000,606,1000,177,-723,1000,-1000,933,1000,435,108,1000,-125,-1000,926,-535,53,221,-197,1000,806,201,298,477,-652,-1000,-204,999,-110,1000,1000,-297,-737,-1000,-605,756,-800,76,-740,531,480,-295,1000,-576,1000,-1000,-1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisState", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{-756,849,-294,941,-995,-506,388,-485,-712,969,626,-725,590,9,-909,-835,-139,-617,-153,-15,-89,-226,-58,180,-31,-604,74,-39,-635,-611,-514,730,-301,-487,-45,807,-633,765,-502,-948,-155,-872,256,-857,-242,-115,911,971,431,659,-77,-79,-651,-328,-58,-358,-242,-426,-231,-693,-501,655,421,355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisState", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{159,417,-296,420,228,309,516,-491,204,242,389,578,673,-912,-997,120,-639,794,-41,245,-896,283,728,442,-210,1000,681,730,-104,-213,345,-720,138,-668,120,-881,-251,-11,1000,-20,-567,746,933,-288,-355,688,663,-470,86,-744,635,-309,480,-77,1000,-502,1000,-818,-99,-95,-810,-850,-664,-406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisState", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{811,592,-1000,-131,-1000,-1000,429,-479,-563,-767,759,-1000,-1000,1000,1000,1000,-597,-677,561,-1000,-835,-1000,288,1000,1000,-692,1000,-1000,-1000,-813,106,-256,-1000,843,-653,540,227,457,963,36,-1000,-1000,160,697,-1000,-1000,-1000,345,1000,25,-1000,198,-249,13,173,1000,236,1000,1000,987,1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisState", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{-14,-413,-1000,-505,-538,49,-648,-1000,708,-590,-509,-237,-263,600,182,-115,-372,95,907,227,-224,-322,998,-221,-322,-662,186,-344,389,628,430,567,127,701,-924,-78,80,603,-1000,-490,950,0,174,91,-371,376,-383,1000,-325,-561,193,-219,-328,580,169,752,331,370,-484,-58,-168,-642,193,918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{92,337,400,391,-1000,294,118,-482,110,410,121,94,773,-748,-1000,804,-1000,-160,-31,544,-896,-36,827,-123,-1000,178,147,33,400,40,-461,-881,990,325,-215,-358,-603,-20,-1000,-325,148,183,769,-326,210,102,-275,402,57,-128,1000,-36,329,-444,47,-550,-175,-944,-828,-592,-1000,20,-406,831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{1000,-1000,718,-244,1000,579,222,-377,504,181,-184,771,298,-1000,270,1000,-486,1000,-242,192,-806,-410,1000,200,-720,1000,566,-147,480,411,-483,-1000,1000,999,-192,-1000,846,-235,1000,-2,-519,1000,891,787,-127,-219,-511,-402,-369,-1000,812,1000,1000,646,636,40,-515,-488,1000,660,294,-159,-778,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{1000,-793,-185,-676,-808,-646,572,-935,26,-153,55,-813,-1000,145,73,1000,-129,942,118,-932,1000,-404,1000,607,82,571,1000,-613,-452,-418,227,-873,169,641,-363,-1000,64,-1000,939,1000,-694,313,1000,1000,-805,-240,-118,1000,44,633,-911,437,779,798,-401,1000,-504,390,555,461,1000,-126,262,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisState", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{-456,-538,-761,560,675,444,-403,-45,653,-84,-746,978,-411,387,914,575,-987,794,-1000,466,-1000,87,1000,-377,-1000,867,321,-1000,-86,854,918,-1000,918,237,-659,-1000,-1000,312,1000,-486,472,819,1000,-1000,516,1000,214,-577,-1000,-1000,1000,552,689,3,1000,-937,1000,-1000,180,-910,-1000,-1000,-852,780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisState", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{-658,-15,-1000,1000,-195,-32,423,-231,-344,506,361,194,1000,-1000,-998,-707,-634,58,39,1000,-928,483,1000,-1000,-1000,-670,93,840,-1000,52,580,-737,1000,-367,-164,-917,-233,928,1000,-1000,610,1000,595,-1000,714,538,592,1000,-842,-205,1000,1000,678,109,1000,-1000,979,-1000,138,-1000,-1000,1000,513,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisState", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{342,934,-1000,1000,-1000,-1000,237,-452,189,-263,397,251,449,-813,227,-234,-669,-1000,-672,535,-626,-1000,981,-711,-1000,-1000,260,-1000,-1000,81,390,503,775,1000,-1000,-423,-682,1000,385,-1000,1000,-845,64,-1000,571,-1000,334,1000,-405,368,1000,1000,113,414,956,112,389,-1000,283,-1000,-1000,682,102,-626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisState", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{268,961,-1000,1000,-795,-602,276,-332,-115,-212,388,-564,583,-817,-253,-160,-403,-653,646,-18,-1000,-489,-290,-251,-829,-562,555,-360,-700,154,393,52,373,600,-709,-1000,-615,1000,217,-922,626,482,303,-576,226,-427,602,1000,-176,-64,1000,903,447,794,1000,-199,511,-932,-222,-1000,-1000,191,-639,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisState", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{-266,-821,-586,158,1000,929,-477,117,653,21,-559,1000,915,-1000,-1000,235,-1000,1000,-1000,557,-896,366,1000,-205,-1000,1000,-546,1000,891,965,740,-1000,840,822,-222,-1000,-700,-1000,1000,478,465,1000,1000,-1000,420,1000,479,-1000,-746,-1000,957,403,783,-268,1000,-1000,1000,-1000,748,339,-272,-1000,-561,326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{-1000,309,767,539,748,1000,203,636,-783,629,-831,1000,1000,-581,-1000,-1000,-528,174,478,-1000,322,961,-961,-982,-31,73,1000,1000,1000,804,-171,52,-918,-500,1000,-78,0,-936,-1000,-325,157,-138,672,-1000,602,53,589,-654,-872,138,-1000,-1000,512,295,-926,-1000,474,-430,-458,-991,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisState", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{111,849,28,903,954,537,891,-141,-712,661,1000,689,590,-1000,-987,771,-586,393,-1000,1000,-1000,-226,-525,-243,-911,1000,-685,1000,-1000,-611,439,-298,-1000,-1000,556,532,622,-1000,1000,698,-1000,1000,-595,-973,877,213,628,-1000,22,-790,-77,-366,-651,-943,776,-1000,724,-158,542,-452,-1000,-84,-823,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{-107,334,-991,287,906,857,-722,-286,-376,14,255,-120,-646,-63,-648,48,-302,114,-984,101,-441,751,-319,402,609,89,-817,775,331,-156,894,-305,-894,-578,596,821,352,-348,-191,579,236,375,-807,-313,115,-552,-535,-790,523,-559,-708,36,257,657,-410,-502,-75,938,-242,269,167,210,-470,660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "draw(java.awt.Graphics2D,double,java.awt.geom.Rectangle2D,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.plot.PlotRenderingInfo):org.jfree.chart.axis.AxisState",
            new int[]{-623,151,-1000,831,-499,879,-581,-389,1000,-187,-1000,868,1000,-1000,-169,-148,-713,-91,-743,1000,-1000,544,280,-610,-1000,626,-1000,429,875,1000,1000,-449,820,278,-506,-1000,-678,1000,744,-1000,441,685,1000,-1000,732,139,-1000,-704,-1000,-1000,1000,1000,974,309,1000,-1000,1000,266,-1000,-1000,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{1000,1000,-681,75,347,1000,-802,-494,-5,129,1000,1000,-1000,-756,510,-1000,-352,-872,728,-1000,-1000,1000,-14,-1,-23,384,1000,-1000,674,1000,728,-584,486,-1000,452,915,353,1000,296,1000,-305,1000,56,937,-370,507,-569,-1000,1000,-1000,-1000,522,-1000,1000,901,1000,-1000,-766,-762,-926,-632,-1000,-291,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{-223,-466,-526,-674,-73,-609,551,-218,-853,961,-1000,-562,267,627,233,-491,999,-182,-751,-794,-1000,-989,-381,-559,-460,-109,-50,-525,1000,1000,75,-364,98,-582,-25,1000,-280,-789,-1000,-204,1000,657,-1000,356,1000,-771,-1000,1000,-74,254,-1000,-1000,216,-970,191,-800,-723,353,-296,790,983,98,-1000,-489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{1000,1000,-681,-572,-1000,-99,1000,252,-931,3,-703,55,-244,-666,37,-382,-316,120,-346,-957,-1000,1000,-14,-629,-1000,-603,1000,-1000,803,1000,-175,400,-509,-756,-261,93,-149,93,-1000,-651,909,-210,139,1000,494,12,106,1000,781,623,400,400,753,400,1000,1000,-1000,1000,-495,220,1000,-1000,-585,-623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{-1000,537,-231,-664,-1000,637,693,-664,-20,-21,-702,-654,-1000,-1000,230,1000,-489,0,1000,-1000,-630,179,-714,0,970,-1000,1000,1000,-593,277,-1000,-703,-1000,513,-603,-32,165,-734,346,-1000,300,-531,-827,1000,-272,-607,537,174,-1000,-889,1000,736,-437,1000,-87,962,-75,169,1000,782,-529,-532,-1000,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{231,360,-26,1000,-94,150,370,1000,235,869,-38,539,469,518,120,-993,206,734,-758,-237,-1000,-239,-992,-1000,1000,95,-278,-254,870,-577,663,492,994,921,413,485,65,-307,679,-440,467,484,-1000,534,1000,-1000,-1000,1000,660,252,-493,-1000,347,-19,-636,294,611,-1000,710,-164,467,408,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{1000,733,-1000,-1000,-95,-213,59,-501,-140,-342,-1000,250,1000,955,-1000,-712,481,-274,-13,-1000,-1000,125,-936,-659,-750,-939,-96,-835,939,1000,1000,-1000,-714,-1000,347,1000,-1000,39,-150,4,-345,1000,-273,401,869,-1000,-1000,-88,1000,-356,-1000,400,1000,284,635,814,-683,1000,-1000,-731,-98,291,-584,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{388,1000,-231,637,-620,598,-129,772,15,-21,497,487,-785,-1000,230,-267,-1000,173,529,-1000,-691,174,-465,-909,307,-579,1000,-766,905,137,-1000,326,98,513,-392,-557,76,340,672,-98,799,207,379,1000,-271,1000,-44,403,-1000,441,1000,317,-149,640,371,-117,198,-651,496,67,548,-1000,-781,-464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{1000,1000,-776,161,-239,965,351,-1000,-866,810,1000,1000,-1000,-91,-554,-1000,824,-795,1000,-1000,-1000,762,356,-255,-1000,373,1000,-1000,976,1000,-498,-1000,1000,-1000,-341,877,1000,1000,-1000,291,1000,543,-349,1000,469,647,234,-383,-511,-299,515,414,1000,-494,857,470,-1000,-1000,-509,-248,102,-1000,-492,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{-1000,-712,-57,309,866,-539,18,148,437,918,-264,-757,584,-639,718,205,-276,-501,-489,-345,1000,-361,-169,1000,351,-498,759,-557,45,-465,-657,12,423,796,-541,-1000,1000,-484,-214,1000,922,-34,-38,609,-408,281,135,554,-1000,1000,258,-1000,1000,-1000,-518,-1000,-999,779,756,932,1000,-728,-1000,-736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{42,541,641,51,486,317,-523,-1000,-218,681,-1000,-810,-332,284,168,270,202,329,-608,-80,-1000,-481,523,-512,-901,977,-366,1000,605,1000,-493,559,-551,476,399,-535,272,-1000,-876,-1000,1000,918,-379,846,252,-913,-255,824,851,-1000,189,-371,-54,-567,505,-317,-1000,365,391,816,570,-378,-1000,177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{-1000,-370,-31,-352,1000,217,-273,440,376,-915,213,36,167,-495,722,320,-1000,101,-585,55,506,1000,284,-360,1000,-163,-1000,841,-1000,394,-767,-274,-100,1000,-98,-1000,880,-31,1000,1000,799,177,-276,-157,-1000,652,566,-1000,-1000,-425,-193,-464,138,878,-431,-301,-393,1000,-82,-1000,-1000,291,-734,-453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{-1000,82,74,-784,-536,-29,95,-508,843,49,-1000,-1000,-260,-523,85,1000,-293,444,-159,-806,-971,-1000,-1000,-446,1000,-958,-109,1000,-164,459,-448,-839,-1000,696,-39,49,-2,-1000,145,-1000,353,-230,-1000,190,-457,-1000,178,439,-1000,-299,165,736,-376,643,-711,962,229,-1000,1000,1000,-815,371,-683,-218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "equals(java.lang.Object):boolean",
            new int[]{-99,194,704,1000,1000,-1000,328,509,-515,-750,224,1000,767,1000,-879,-1000,259,836,-795,-367,-1000,1000,-663,552,902,1000,-1000,-437,-868,1000,-1000,212,672,-1000,1000,-489,-599,326,1000,1000,-23,277,874,2,-715,1000,-104,-788,636,-442,-1000,-882,336,1000,263,645,-321,-1000,-181,-1000,-783,785,-404,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLinePaint():java.awt.Paint",
            new int[]{-1000,-1000,-636,-1000,-368,-1000,-45,911,636,1000,-189,1000,33,935,-1000,1000,68,883,-337,-1000,-507,-584,-622,476,-478,665,278,-479,1000,-1000,34,740,-744,956,-1000,1000,492,-387,-181,74,-26,-686,1000,855,-92,112,-1000,-418,-781,-1000,-203,896,-1000,-9,172,-181,265,-1000,952,1000,-1000,-212,-233,345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLinePaint():java.awt.Paint",
            new int[]{201,917,-623,-405,-574,-767,352,-460,486,7,-639,917,355,-432,822,-222,-994,-549,973,-919,49,-484,-2,312,-357,404,-544,-517,-821,-743,872,152,544,783,-934,978,-616,-212,-177,965,-281,-896,-165,62,503,479,-502,37,-695,-553,129,693,98,447,415,-640,-598,-457,509,-633,-551,969,99,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("COLOR:-3041816", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLinePaint():java.awt.Paint",
            new int[]{1000,745,-1000,354,391,746,-252,952,1000,-633,-868,563,209,-107,488,-793,-567,-1000,-1000,777,1000,46,-228,-810,140,524,-13,756,367,1000,-651,-1000,-65,-491,117,719,-164,834,-386,-642,-644,657,1000,970,-247,-1000,-911,-593,-772,-51,125,-721,-1000,706,640,237,-3,-1000,728,-458,-858,-229,188,428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLinePaint():java.awt.Paint",
            new int[]{-756,-62,-782,95,861,-525,-606,-930,-143,562,-32,-205,693,-242,-89,-1000,-820,1000,1000,-481,360,-384,-393,-97,-655,574,-295,-446,-806,-733,1000,-183,610,-25,-890,285,-471,151,-755,1000,-1000,-1000,-711,777,-266,737,-919,-336,1000,875,785,779,-1000,498,954,-151,-841,1000,503,-798,-378,-722,479,886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLinePaint():java.awt.Paint",
            new int[]{1000,917,109,-1000,-797,177,-74,-1000,-316,-981,-725,1000,637,-1000,1000,-545,-1000,-943,1000,-781,66,-42,-216,56,589,301,506,-612,-1000,-1000,1000,-388,797,283,-277,287,-594,468,541,944,-215,-965,-599,-394,666,607,66,507,38,90,549,351,864,689,917,-89,-827,552,-516,-431,320,269,-188,-139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLinePaint():java.awt.Paint",
            new int[]{-1000,759,-392,-322,-1000,-1000,664,-355,1000,690,423,276,861,228,-457,-383,865,1000,-388,-508,84,24,-1000,-71,-1000,344,844,-726,726,-200,1000,739,58,461,-414,336,-70,-355,-1000,-573,-1000,-1000,1000,731,280,787,-76,51,-781,1000,-90,1000,-768,424,-216,810,1000,-1000,353,58,-1000,451,-787,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLinePaint():java.awt.Paint",
            new int[]{-1000,-45,-723,-688,1000,-1000,-502,-271,-997,1000,60,871,-148,982,-164,436,-741,1000,806,-1000,-1000,-861,213,666,-1000,-177,1000,-1000,44,-1000,1000,49,551,1000,-1000,1000,-902,-1000,287,1000,-1000,-902,-448,658,-349,1000,-1000,-141,-356,1000,739,1000,-496,-679,112,-1000,372,1000,737,299,-1000,171,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLinePaint():java.awt.Paint",
            new int[]{338,-342,1000,1000,-436,776,-398,431,74,-180,-252,-616,349,577,79,-681,590,470,-865,642,658,71,-703,-836,-334,499,525,281,706,811,550,664,-297,-1000,810,234,229,686,-276,-1000,-261,-142,924,495,863,215,-687,-178,867,296,-459,-634,310,-405,72,-832,779,255,1000,1000,-252,-609,-437,779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLinePaint():java.awt.Paint",
            new int[]{-1000,129,-709,-357,-1000,-1000,793,-264,264,1000,-320,1000,927,124,-669,548,-1000,953,1000,-956,510,-1000,-958,960,-1000,1000,-1000,-814,347,-1000,607,1000,606,-508,-1000,-22,75,-1000,-1000,879,-277,-1000,-96,942,-708,533,-1000,-776,-1000,-1000,739,580,-1000,299,694,-843,-363,-715,835,274,-1000,-787,-87,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLinePaint():java.awt.Paint",
            new int[]{201,356,238,1000,-474,885,75,968,-538,-539,-639,-557,903,222,500,-222,-290,-782,-925,1000,711,217,-1000,-669,-286,919,-358,179,1000,1000,10,152,159,-433,1000,541,531,457,-320,-1000,-615,-41,1000,1000,1000,-810,-777,37,-302,-533,-141,-478,-659,991,798,719,-598,-229,721,-438,-1000,969,-542,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLinePaint():java.awt.Paint",
            new int[]{-927,797,648,703,-212,118,528,-45,-285,662,-485,-331,914,442,297,-912,696,272,-884,660,-582,564,-306,-282,-723,342,920,-478,807,349,873,-242,114,-699,466,484,-517,-611,-961,155,928,-93,-440,-154,508,564,717,78,776,438,-800,-240,-393,849,-304,680,-171,425,738,-311,-426,600,-563,285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{-1000,-176,569,1000,-644,793,-1000,-269,1000,-990,969,-541,324,-58,-1000,-970,-1000,-1000,1000,-1000,815,-948,-1000,-410,-756,-1000,-947,1000,-1000,-1000,1000,1000,-1000,1000,-312,-1000,1000,720,1000,1000,518,1000,-1000,-817,1000,-1000,-1000,1000,141,1000,-1000,1000,-127,107,473,-1000,-473,-1000,325,895,107,873,1000,41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{-861,400,-215,351,610,-675,-477,1000,-107,922,-197,-1000,596,299,-1000,-970,-1000,-1000,576,565,-353,-1000,818,-1000,-283,-1000,-1000,1000,-572,-1000,419,1000,-1000,1000,209,98,-400,430,1000,-347,-424,1000,17,-847,-105,8,-668,1000,-1000,931,-1000,-865,-125,-987,172,-934,-1000,-1000,1000,1000,1000,1000,1000,-586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{605,-807,831,986,1000,-273,162,289,294,-506,-151,686,-108,-475,1000,1000,328,387,571,1000,-260,811,-565,889,-474,255,104,-715,291,979,-818,428,166,-705,-591,312,-400,-1000,-1000,-193,143,630,58,1000,-1000,1000,908,-269,-235,-1000,-196,290,-501,332,294,16,145,807,-312,-1000,322,-1000,-918,-928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{-1000,-1000,-152,-368,-100,-424,-383,680,732,-742,152,-1000,-609,1000,-802,-1000,-1000,-1000,1000,-745,837,1000,-806,-197,-863,-1000,-1000,1000,-862,-1000,122,443,-1000,390,953,-667,799,506,1000,649,-93,1000,-731,-1000,1000,-1000,-1000,666,1000,1000,-1000,766,-686,459,27,-1000,-1000,-785,1000,284,852,130,1000,575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{-1000,-558,-147,329,-416,365,-152,549,-307,-400,507,585,-1000,226,-527,-79,-850,-8,1000,-915,796,226,-400,231,-1000,-188,52,428,-650,239,-1000,-1000,-817,-1000,798,977,1000,-247,-139,840,-445,655,-571,-1000,1000,1000,-1000,-746,806,700,-51,1000,-1000,1000,579,-643,504,-884,1000,-389,-201,146,-262,-352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{-1000,88,574,902,-456,-155,-352,442,-622,527,1000,-1000,-1000,-459,-1000,-1000,-1000,-1000,-140,-1000,41,1000,831,-1000,162,-1000,-301,1000,-994,-1000,-384,1000,-1000,238,1000,-440,1000,27,1000,712,-745,281,-1000,-1000,1000,-1000,-1000,463,400,1000,-757,-124,-462,-36,494,-1000,-687,-1000,1000,1000,-173,-202,1000,402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{573,-1000,-161,986,1000,-273,-311,-770,1000,-1000,349,786,-108,-1000,213,694,1000,-314,-107,-335,584,93,-1000,1000,-655,-496,1000,-227,291,1000,-632,-674,-100,-190,-738,-1000,1000,1000,-1000,1000,238,1000,-777,-556,1000,-877,-383,-510,-778,-1000,-12,1000,-84,1000,1000,-387,305,1000,-41,-1000,200,-1000,-154,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{1000,-631,1000,682,816,-418,-233,475,945,-33,-282,686,1000,-709,1000,1000,988,507,636,1000,-891,485,-396,-864,-129,317,-348,-715,390,1000,210,-1000,486,57,-1000,116,-1000,-98,-1000,199,476,-529,58,1000,-1000,1000,1000,268,-492,-1000,62,-10,95,-468,449,62,229,1000,-916,-1000,378,-262,-918,-815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{-1000,422,539,609,680,-636,-377,1000,-648,946,-1,-383,-597,-193,-1000,-457,-1000,-1000,1000,180,336,-176,873,-864,-1000,-482,-317,470,-619,-1000,-663,400,-1000,-266,1000,309,647,-35,1000,13,-599,655,141,-1000,390,306,-1000,-21,-400,1000,-1000,247,-1000,-362,405,-1000,-963,-1000,1000,-64,1000,-291,60,947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{-1000,-1000,899,1000,-716,-301,-397,-49,1000,-1000,955,-1000,-256,207,-324,-66,-1000,-1000,384,-304,869,968,-260,613,266,-903,1000,26,-452,-1000,-591,13,-581,691,-533,-379,1000,739,1000,1000,-276,1000,-500,-1000,769,-1000,-464,250,355,-378,-1000,-233,-484,889,1000,-224,-44,-278,-52,-247,-369,-1000,1000,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{112,535,190,1000,1000,-1000,-242,-269,-601,1000,-1000,-788,671,-734,303,-970,-230,-589,820,1000,-953,268,1000,-773,-756,-357,-227,200,203,-336,183,1000,-1000,1000,-114,-82,-1000,-528,663,-1000,-394,741,757,-817,-1000,1000,667,1000,-1000,319,-1000,-1000,118,-1000,-322,-1000,-1000,131,348,627,1000,924,1000,-536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{-1000,-176,769,888,287,20,-899,234,298,-990,-209,-541,-716,-498,-1000,-970,-1000,-1000,1000,-745,727,-1000,-1000,-410,-943,-795,-1000,1000,-813,-1000,1000,593,-1000,1000,442,-1000,505,459,1000,533,190,1000,-731,-1000,912,48,-780,1000,-201,1000,-1000,902,-462,325,-172,-1000,-559,-1000,932,895,1000,919,1000,67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{172,-1000,-987,-288,-448,251,-167,370,941,-209,275,512,-699,-663,-549,-328,-121,124,91,-773,-893,-961,-944,420,-987,-355,-295,719,-1000,-49,50,-935,-1000,-25,356,-632,1000,1000,542,672,-118,692,-1000,-1000,1000,-1000,-1000,337,-1000,110,354,922,-248,674,988,-705,-211,655,1000,-360,582,-666,18,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getAxisLineStroke():java.awt.Stroke",
            new int[]{1000,-723,-1000,-589,-1000,-991,-139,-532,602,1000,342,983,270,-501,-327,389,1000,-572,-787,513,196,-1000,-1000,1000,-785,-970,1,-89,-1000,1000,-468,-56,-621,-526,-442,-968,165,1000,-1000,-242,541,655,-339,-507,121,967,446,931,-1000,-334,401,-1000,-30,1000,1000,-16,-1000,1000,808,-1000,1000,-119,-717,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{-1000,269,-257,1000,1000,-335,112,-961,-1000,1000,-454,599,-871,-567,-203,741,775,740,282,-623,-200,199,806,233,101,524,-646,-1000,751,295,-53,-202,893,515,-854,-1000,-319,101,397,-695,1000,-737,576,-1000,-245,-339,307,-1000,1000,1000,-1000,-1000,-565,-379,808,-1000,-702,-810,-1000,-775,-936,276,-897,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{-57,504,454,1000,397,-4,286,606,-427,-752,1000,-18,92,-104,625,-46,-397,400,1000,-443,-39,378,245,-769,-1000,4,851,247,699,1000,-503,416,-299,333,518,695,-419,-70,400,-255,-733,-312,637,-295,131,1000,-455,191,-526,781,450,591,-576,895,507,-17,-308,-157,445,1000,-532,-370,-755,-460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{740,-290,24,573,-591,-843,-837,-430,953,-763,307,746,943,-757,-612,202,-1000,-1000,-830,1000,-1000,30,-79,-1000,660,-366,904,1000,-761,-817,57,713,751,715,-508,638,-380,-166,-1000,-1000,-826,1000,-287,850,-974,-1000,459,540,-1000,263,553,-466,1000,139,589,-293,-1000,-1000,461,-709,1000,504,-664,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{740,-368,444,169,686,-253,-581,-571,-211,-311,12,643,118,-246,34,-629,-875,-655,-756,167,-25,237,403,-1000,16,-50,904,816,-619,-817,-838,406,351,822,-60,1000,772,-5,-518,-474,-742,379,-374,850,-1000,-832,160,504,-572,-134,553,40,-31,288,586,-289,-682,-555,830,-646,328,686,-525,889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{226,-836,311,11,-132,1000,-94,247,953,979,-607,952,-979,1000,-612,1000,954,385,-698,1000,1000,30,662,-750,-726,1000,-202,-287,-657,-992,-1000,713,345,1000,460,281,1000,303,665,598,-306,130,843,245,121,-284,459,1,52,263,389,1000,-901,1000,200,-439,81,273,-72,-823,-900,1000,-291,97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{237,-489,34,-363,-154,579,154,-725,-581,805,-753,500,-1000,407,982,519,532,253,-949,-1000,1000,55,943,-767,-518,974,776,-101,-774,-1000,-672,716,-41,1000,123,-300,1000,317,511,374,-172,-746,-52,-48,-556,-547,153,64,445,-186,-126,87,-816,770,274,-569,-199,16,194,-1000,-610,781,-462,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{-1000,-205,288,864,795,913,1000,493,-1000,79,-907,-195,-929,362,1000,144,1000,1000,-131,-1000,1000,385,923,-347,-1000,228,79,-1000,1000,490,-742,101,-949,1000,815,-505,457,381,1000,1000,-21,-1000,891,-909,620,982,-691,-498,121,959,-501,610,-1000,619,515,-191,56,-657,-1000,-1000,-895,797,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{-388,1000,994,465,1000,-237,737,-1000,82,676,13,-664,-1000,-941,528,-987,-917,529,822,-18,-1000,589,561,-1000,-1000,-76,1000,69,1000,556,-735,1000,-1000,-161,974,906,320,322,262,-383,-69,-952,-321,-776,-1000,470,-925,-206,-306,-117,-269,-240,-1000,307,768,246,-867,-600,630,352,413,-767,-1000,618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{1000,350,-877,615,363,-1000,1000,867,-875,173,49,-711,-971,-733,249,-239,-1000,1000,1000,-957,131,-495,-299,-388,-526,-774,661,-75,248,1000,-127,871,-419,-43,781,-1000,-1,212,-468,-511,443,-874,605,-1000,641,995,-349,19,-339,187,-1000,-539,-1000,-50,860,537,449,17,770,783,311,55,-677,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{636,-146,-256,-517,-1000,-787,-472,-218,1000,-59,-1000,154,523,-916,-1000,-32,-280,-1000,-435,934,-818,44,-162,-298,1000,-428,618,706,-1000,-390,249,1000,1000,688,-357,-762,107,65,-599,-8,-512,884,-354,388,-914,-804,550,501,-794,-315,-489,-993,941,-1000,782,193,-1000,-417,299,-1000,867,906,-1000,-211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{-448,-205,402,689,702,589,180,-101,-1000,1000,-1000,1000,-1000,373,243,1000,1000,420,-131,-1000,1000,232,895,89,-107,1000,-520,-1000,-185,-413,-1000,-450,-329,1000,-9,-699,724,481,450,153,1000,-195,1000,-909,31,-708,316,-737,517,1000,-501,187,-641,359,235,-647,-302,-282,-1000,-1000,-931,797,-387,-623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{331,-33,-256,710,-63,-885,-812,-218,737,99,-1000,1000,824,196,-439,1000,-372,-1000,-1000,772,-603,44,-230,-1000,70,265,963,519,-355,-1000,-284,-100,1000,858,-489,641,-317,-321,-604,-294,-512,823,675,931,-1000,-471,391,285,-554,664,1000,267,454,794,459,-1000,-1000,-460,-341,-1000,729,399,-1000,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{-928,1000,-91,-313,169,-115,1000,-1000,359,-783,576,-362,-940,-757,16,-113,769,1000,943,-1000,166,373,-184,-856,660,333,650,-1000,1000,756,365,1000,751,133,441,-806,-26,639,387,-729,694,-568,405,-1000,345,515,-595,-1000,-174,516,-1000,-1000,-864,326,1000,135,-795,-933,-543,99,218,-571,-1000,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getFixedDimension():double",
            new int[]{1000,323,1000,-170,-643,345,-656,558,171,-1000,-53,699,329,496,-203,-629,-1000,-837,-249,-609,897,362,771,-1000,-1000,-12,-328,1000,-1000,-740,822,619,-921,381,1000,-1000,-616,101,-199,654,-1000,46,-199,1000,-557,-262,-149,1000,24,-343,1000,-921,-478,1000,-349,532,-248,362,961,96,355,21,-36,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDgwMDAw", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabel():java.lang.String",
            new int[]{1000,-1000,-746,9,-1000,967,-361,-836,244,960,1000,-1000,-44,-859,1000,-1000,1000,-1000,-1000,-908,1000,1000,-1000,1000,1000,1000,759,307,-223,989,-1000,850,-1000,-798,428,289,-1000,-416,-1000,1000,-1000,-74,-1000,-1000,1000,-146,330,-747,1000,1000,436,1000,-367,-1000,177,-503,-594,-825,663,-632,-416,140,1000,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.String:KzB4ODAwMDAwMDAwMDA=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabel():java.lang.String",
            new int[]{89,-197,984,639,-194,850,224,246,-729,966,46,-186,615,359,977,-1000,-1000,-1000,952,-619,425,1000,527,159,393,-14,-142,-1000,-1000,1000,-1000,639,-192,-277,204,715,-110,-589,-536,32,-316,371,721,377,380,-1000,-912,96,1000,485,192,-816,-1000,-1000,955,-626,-1000,-883,-1000,594,-627,989,-726,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.String:KzB4ODAwMDAwMDAwMDAwMDAwMDAwMDA=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabel():java.lang.String",
            new int[]{801,745,1000,-436,-162,879,-107,-244,-1000,767,379,-482,462,-1000,354,-136,960,144,-811,797,366,428,613,-1000,-85,1000,1000,630,43,1000,1000,-425,-938,-40,250,1000,-545,34,-574,1000,-564,1000,-740,-1000,1000,86,1000,-1000,1000,-887,690,552,225,401,-909,310,-1000,-575,1000,-1000,482,259,1000,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabel():java.lang.String",
            new int[]{-900,828,404,263,-627,444,271,567,-1000,1000,400,804,-608,-939,229,-966,-922,-1000,914,-908,-574,586,963,-1000,409,1000,-409,-893,-650,1000,-79,1000,-1000,-754,-816,228,1000,-1000,438,68,77,1000,-337,185,1000,-1000,-566,-111,830,-356,-218,-1000,-90,-1000,-845,1000,-681,-1000,-580,-144,-1000,165,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabel():java.lang.String",
            new int[]{-1000,679,-95,1,-406,-31,190,-631,-913,399,242,1000,-573,-846,-735,559,-805,71,448,-852,549,-1000,-3,-1000,94,-1000,-516,117,255,-54,-827,399,105,-667,-50,-756,1000,-623,523,83,598,-47,-586,794,-194,-454,-1000,-890,214,-1000,-733,-241,847,466,985,510,-1000,849,656,1000,195,362,-1000,283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.String:TmFO", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabel():java.lang.String",
            new int[]{315,778,321,-127,-417,343,-509,-245,1000,-469,-323,-356,143,-1000,-807,-1000,547,-68,-67,305,768,403,251,1000,-87,448,614,-350,-448,-1000,-240,-681,522,-81,-26,97,400,-218,-1000,77,-793,-1000,352,-850,1000,68,459,-567,1000,-61,-169,-314,-392,-1000,-369,-158,1000,688,466,-524,13,164,1000,-3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.String:V2w2TzUuU044XFVmSnEJN2VY", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabel():java.lang.String",
            new int[]{708,-815,541,-248,-1000,843,-297,-476,503,405,-563,206,551,-590,8,-429,269,-127,-97,-116,921,78,-483,343,433,325,-142,-382,543,685,-166,-289,731,-221,-164,-1000,1000,-440,-816,135,-664,202,1000,-851,-500,-396,-241,-863,988,-289,524,1000,-1000,-504,1000,-626,-1000,502,-42,-659,801,186,798,-525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabel():java.lang.String",
            new int[]{1000,467,-305,-1000,-551,1000,-476,-694,1000,-634,1000,-1000,-1000,300,1000,691,-481,-892,665,89,855,1000,-1000,919,966,-1000,126,-1000,789,-1000,-1000,-789,351,-182,-738,799,-365,1000,-1000,-940,-1000,-868,-897,411,94,766,-354,1000,1000,1000,-1000,417,78,-616,1000,-1000,1000,659,745,-349,-172,708,1000,376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabel():java.lang.String",
            new int[]{-400,872,864,299,-468,1000,1000,1000,-442,1000,-231,889,-1000,-1000,1000,-1000,-400,-1000,1000,-282,-187,351,1000,-400,-81,-573,164,-822,-545,1000,1000,588,-139,-1000,81,-197,1000,-1000,35,1000,1000,783,47,-356,1000,-1000,1000,420,834,-387,-740,-1000,-1000,-1000,-245,146,-1000,-1000,-159,-1000,-1000,60,1000,414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabel():java.lang.String",
            new int[]{-115,-96,-66,273,448,-498,229,335,42,249,657,447,-168,924,581,565,-343,-1000,914,7,718,-668,-1000,661,348,-718,-1000,-998,-558,46,-653,858,-227,-165,-721,590,1000,88,-574,-662,377,-971,1000,939,63,-482,-917,1000,177,939,-1000,-416,-101,-586,348,-587,-241,-735,-1000,1000,-340,927,-514,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabel():java.lang.String",
            new int[]{-1000,-86,87,-273,192,245,-116,690,-400,532,-892,400,12,-1000,-821,-213,1000,-161,-408,-400,-559,-219,307,443,241,541,-709,583,972,253,-445,9,553,537,-23,-1000,400,-400,113,14,255,400,458,400,-873,-362,-78,634,881,-1000,512,-314,573,-135,-1000,497,-328,-191,-916,317,-100,-763,-400,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-1000,-1000,-712,-607,327,-300,-1000,358,480,1000,-1000,-385,-895,46,-715,1000,-312,306,573,-1000,-1000,-1000,773,-42,-816,-1000,1000,902,1000,946,-951,1000,399,919,1000,313,198,424,1000,1000,1000,-889,-1000,-1000,20,-138,-1000,730,-1000,-1000,-1000,-1000,960,1000,-796,1000,-1000,811,1000,1000,-131,807,-414,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-1000,-62,389,101,-481,1000,542,-209,1000,962,1000,-1000,-726,1000,982,-379,-340,-1000,876,-814,820,588,-1000,-977,-806,1000,-161,257,-1000,-207,79,204,-1000,969,-254,-1000,-1000,980,1000,856,1000,40,-497,111,708,-409,857,1000,-480,1000,412,-107,-579,968,-713,-548,-1000,479,-757,247,735,499,836,706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Double:MzkuNQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-904,-649,769,-326,-368,154,791,-193,-780,1000,1000,-575,-216,1000,714,1000,-1000,-1000,1000,395,946,177,-1000,-767,-1000,1000,925,1000,400,-483,-588,-1000,-1000,904,1000,-1000,454,432,1000,-151,447,-1000,-1000,-751,462,-981,-220,1000,-1000,1000,-88,-1000,686,1000,-400,228,-293,180,541,-610,264,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-1000,-1000,-577,213,-263,574,-933,-1000,-816,1000,1000,933,-168,-635,-360,119,-1000,-1000,417,591,-924,-1000,1000,851,-1000,1000,-847,511,593,-993,1000,-1000,-834,213,1000,804,1000,1000,-716,-1000,580,-1000,270,-1000,-654,-1000,-719,-1000,-280,655,-1000,-1000,1000,-499,-1000,1000,1000,217,1000,-200,580,761,-286,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{382,-1000,28,430,-236,751,-249,-286,1000,403,719,-681,-592,495,525,-692,1000,22,278,-389,-272,323,603,-641,-7,970,-937,-767,-1000,-392,28,1000,-532,791,-1000,-178,-1000,1000,330,1000,1000,1000,495,1000,-119,-197,1000,-247,1000,91,948,540,-1000,-130,-520,-1000,-1000,946,-1000,1000,253,-140,-5,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-1000,-823,1000,-139,329,1000,-181,-784,-464,824,662,-175,-325,481,306,718,-501,-1000,-346,-1000,171,-1000,-578,1000,-1000,1000,815,991,227,308,-926,-1000,-894,41,1000,-1000,-342,708,-211,212,545,-1000,-245,-1000,645,-790,527,1000,-773,1000,234,-1000,1000,152,-1000,206,-69,208,-493,185,-283,-10,444,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-644,400,-102,145,34,1000,-190,-455,994,657,168,973,1000,-1000,-720,-1000,-138,423,649,936,-559,-512,1000,136,-742,756,-853,-437,-616,-895,73,402,632,1000,-346,1000,1000,688,-693,-717,743,346,-105,-241,96,-411,51,-1000,1000,-408,1000,-470,288,-108,-722,157,601,-346,352,-180,149,825,-654,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-1000,-600,-837,884,1000,177,619,-129,-92,444,104,1000,1000,-1000,-176,-333,-132,813,36,-854,-167,-399,1000,-406,-1000,-941,-646,497,1000,-850,-710,-1000,361,-56,1000,1000,1000,570,965,-1000,-1000,-494,-817,-1000,-191,-552,-945,-222,-1000,-591,156,-65,1000,1000,454,328,1000,-605,1000,1000,251,305,-414,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-1000,-1000,-581,-1000,-342,-8,-1000,82,-92,1000,-321,-9,110,-25,-113,1000,-790,-509,256,-854,-1000,-1000,893,664,-738,-1000,894,-79,1000,1000,27,-67,-89,170,1000,431,762,570,965,343,863,-1000,-817,-1000,-131,-464,-1000,-187,-1000,-591,-1000,-1000,981,1000,-906,1000,-527,487,1000,1000,-201,430,-285,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{553,442,-337,424,-181,1000,584,-88,1000,523,1000,-714,-562,555,525,-605,749,-389,395,103,820,999,-797,-990,-372,1000,-1000,599,-1000,-372,28,790,-1000,791,-1000,-1000,-1000,646,808,1000,749,894,-131,231,200,150,1000,697,306,1000,948,540,-1000,330,-235,-904,-1000,1000,-1000,237,1000,-140,473,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-1000,-572,524,-102,869,408,828,-573,-1000,548,983,144,-157,-163,-1000,1000,682,26,656,-277,306,-171,-218,366,-467,-57,860,1000,1000,-123,-1000,-412,678,1000,1000,-241,618,-86,-335,709,401,-1000,-627,-1000,210,-803,-464,1000,-209,-92,81,-456,1000,-556,-873,805,-947,450,-274,-1000,339,970,-100,995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-1000,-1000,-712,-607,-608,408,-1000,-286,1000,993,447,425,1000,-279,222,2,-1000,-1000,23,416,-1000,-852,1000,1000,-738,566,-506,-959,793,791,766,-1000,-384,-330,1000,632,198,639,136,-1000,-525,-889,-28,-1000,-311,-805,-1000,-1000,-945,347,-1000,-1000,1000,908,-975,1000,1000,161,583,1000,-131,148,-291,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-492,442,-337,-133,-542,359,225,1000,1000,781,1000,137,-1000,-850,-1000,-605,638,1000,404,-407,1000,-957,108,-1000,-364,-849,1000,1000,921,-372,-1000,1000,-1000,1000,385,463,1000,-853,686,1000,1000,327,-1000,-844,456,150,423,955,309,-475,-274,540,-506,1000,-235,-79,-955,298,338,-1000,132,1000,76,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-755,-397,205,-186,127,549,940,-66,-764,702,662,-91,167,481,142,-96,-848,-808,1000,949,1000,412,-1000,-981,-912,444,552,1000,1000,-765,-926,-1000,-836,698,1000,-1000,-237,-523,558,-664,-288,-834,-874,-1000,920,-667,-539,1000,-1000,1000,234,-988,807,1000,212,206,9,34,855,-986,328,1000,998,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelAngle():double",
            new int[]{-1000,-202,-338,-276,-708,-456,491,-870,285,844,9,-528,-301,-801,-1000,1000,1000,820,1000,187,355,-335,361,-1000,-276,-1000,735,-726,-1000,-1000,-458,1000,1000,1000,-551,490,899,425,94,1000,1000,854,-786,615,-242,-860,-49,-28,871,-836,1000,-690,-391,-869,-1000,-1000,-1000,796,-251,-1000,1000,1000,308,-426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelFont():java.awt.Font",
            new int[]{-519,1000,-31,-73,-316,-28,473,255,-876,888,872,86,-15,438,-1000,918,-1000,77,240,-651,-184,-997,-624,51,-198,1000,241,940,-816,-29,1000,-278,-129,-505,643,-390,-245,-400,-215,-400,43,-69,-400,693,436,437,-1000,-1000,1000,1000,312,407,639,400,1000,-31,1000,928,-170,-35,-223,-389,-355,6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelFont():java.awt.Font",
            new int[]{-303,245,-319,-866,-214,388,-54,416,280,400,-78,-400,-639,566,87,439,487,-440,418,-156,866,403,-149,23,-246,841,683,982,233,-400,64,752,-86,793,1000,720,533,-20,830,1000,-350,-400,514,-828,-119,519,1000,1000,175,-445,306,-22,498,-1000,-538,-134,-252,489,5,837,616,-975,89,-948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelFont():java.awt.Font",
            new int[]{1000,714,-381,325,256,613,1000,428,-159,1000,-949,-1000,-492,-512,198,194,1000,-1000,720,-1000,-561,-310,-247,-273,1000,-1000,-1000,829,-1000,-498,895,793,-379,-619,-1000,1000,1000,-389,-1000,-5,-393,-1000,1000,855,1000,1000,969,-1000,1000,1000,1000,-1000,913,814,432,-929,1000,1000,-1000,-1000,-1000,-1000,-1000,-641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelFont():java.awt.Font",
            new int[]{1000,792,464,-196,-886,1000,1000,515,1000,1000,-1000,-912,-1000,1000,-625,-232,-466,-1000,-402,-1000,1000,937,1000,1000,-430,-744,-1000,1000,-1000,-921,973,112,-1000,1000,637,1000,921,589,-1000,1000,-1000,685,1000,452,1000,1000,605,-1000,1000,1000,1000,-841,250,-1000,-1000,-391,1000,881,-1000,-768,1000,-33,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelFont():java.awt.Font",
            new int[]{1000,-334,-451,-581,816,868,-280,-608,471,-770,-815,1000,-303,-427,1000,-757,576,-312,617,-766,-117,-308,-558,-332,-457,-37,-897,728,-308,495,563,-1000,115,441,-222,536,-349,-1000,-918,325,1000,1000,221,-955,947,-334,356,1000,1000,-335,-598,436,131,-38,468,-472,412,134,979,73,-373,-1000,-444,-516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelFont():java.awt.Font",
            new int[]{633,73,8,3,712,139,613,-579,-338,-199,-1000,-228,26,-933,1000,1000,1000,-630,-90,433,359,-83,-170,-1000,-189,-594,-1000,698,-386,-150,-715,-584,132,-468,-268,500,1000,-1000,-946,1000,-323,400,482,437,115,-162,1000,597,270,-79,551,-1000,-617,-337,389,-623,11,405,-151,649,-785,-839,-617,-7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelFont():java.awt.Font",
            new int[]{-257,67,450,264,163,-126,358,-326,-530,112,316,254,-707,-455,-600,1000,-161,-949,272,576,-693,488,394,-412,119,-803,375,-45,-599,-606,685,672,-74,-946,543,-737,-643,124,216,-432,-391,802,-453,463,-62,1000,947,-1000,290,143,-141,609,-975,-929,336,-1000,-649,118,-448,-343,-14,47,-136,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelFont():java.awt.Font",
            new int[]{-4,1000,834,-1000,-663,443,411,1000,-656,1000,-62,-1000,-100,1000,427,1000,608,-1000,671,293,934,17,-343,433,-1000,-306,-172,728,-844,-1000,-1000,-335,17,333,658,765,370,1000,-21,1000,-1000,-1000,1000,-366,1000,18,1000,-1000,405,711,1000,1000,-204,-531,886,611,552,772,288,116,809,-1000,-1000,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelFont():java.awt.Font",
            new int[]{33,-122,-357,459,467,756,-1000,-85,138,-51,-550,-312,73,-22,70,651,275,182,-402,1000,370,-138,-269,-1000,-417,597,-121,832,308,-7,-519,-31,346,-466,1000,-306,188,-1000,140,-104,-59,335,1000,-220,-697,-547,760,539,-15,-421,-1000,605,-1000,48,-570,115,-61,240,565,549,-211,-1000,179,269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelFont():java.awt.Font",
            new int[]{-647,1000,-86,-127,-666,-981,784,1000,-901,1000,1000,-354,29,-219,786,968,280,-704,1000,92,-566,1000,-820,597,42,-204,133,840,91,-1000,-1000,447,508,479,-939,-314,-980,461,724,965,-1000,-1000,118,1000,598,185,876,-1000,-373,1000,721,1000,-21,194,772,-630,30,1000,1000,925,612,163,-813,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelFont():java.awt.Font",
            new int[]{1000,1000,-752,-829,-868,987,1000,1000,-458,1000,1000,-1000,-338,868,-213,107,539,-1000,1000,-953,1000,918,298,819,-1000,-142,-74,792,-1000,-1000,191,-1000,-567,1000,1000,852,446,-38,-934,1000,-1000,-1000,1000,-131,1000,1000,41,-1000,1000,955,-1000,428,212,-1000,505,367,1000,1000,-57,-444,1000,-1000,-433,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{976,-423,-271,520,-6,-816,-999,-1000,-636,-1000,424,-409,2,-189,-1000,997,1000,737,58,123,-258,1000,-288,-634,-234,-487,1000,967,1000,867,775,580,-505,-1000,49,272,-501,-831,-596,-1000,170,963,334,-558,-580,-329,1000,141,-850,580,584,-198,-1000,580,910,547,812,366,-580,-752,784,-853,961,-587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{1000,-675,-1000,358,-186,-1000,234,-796,-1000,-1000,1000,-376,-1000,-964,-547,-584,494,63,630,-592,364,1000,-833,-294,1000,86,216,823,-470,259,1000,606,-675,-209,-104,244,-1000,-744,48,-1000,867,-392,1000,601,-554,-1000,773,501,-131,1000,1000,-21,-1000,1000,432,-355,503,-824,-1000,-186,200,-386,-152,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{-139,-17,361,350,32,518,154,819,878,1000,322,1000,1000,-424,-630,895,-1000,-131,-922,-685,-85,-691,153,899,110,-689,-380,-1000,318,-1000,27,-949,1000,728,-531,-66,1000,-149,950,285,180,1000,-1000,-1000,1000,833,-230,1000,972,-1000,-683,-861,-526,-1000,254,-281,582,-1000,1000,-590,-185,224,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{-429,-269,-717,1000,-545,-3,933,565,409,659,-18,234,980,-660,658,307,742,597,-376,-368,331,517,948,-1000,735,228,-1000,-893,-1000,385,-526,-1000,-908,185,387,1000,1000,-812,-1000,-1000,450,834,1000,-1000,355,85,-1000,1000,340,-421,1000,-1000,-349,-323,572,-1000,-591,-862,309,-784,316,-1000,-86,-907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{-1000,-1000,984,750,-984,-405,828,-529,-220,600,322,287,412,-436,1000,950,270,-745,-859,-441,-1000,471,144,57,-1000,647,801,-1000,-59,-1000,27,-783,-695,-15,693,-275,323,709,735,1000,409,1000,-43,934,777,833,-416,-975,172,-1000,-63,-965,-1000,-891,707,463,-204,-669,1000,-777,193,34,51,411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{313,-352,-341,383,35,-1000,-999,-1000,-1000,-1000,20,-1000,-1000,-189,1000,851,1000,1000,81,421,-1000,1000,-630,-1000,61,199,1000,1000,369,635,1000,1000,-799,-1000,355,79,-1000,-1000,-1000,-1000,356,924,1000,-281,-1000,-698,1000,-295,-757,1000,652,869,-985,1000,1000,-145,812,730,-1000,-480,1000,-1000,-153,283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{-1000,-21,804,391,438,-6,952,190,1000,1000,1000,542,1000,-1000,134,772,-1000,-245,127,-1000,-928,-738,1000,-69,-1000,1000,-1000,-732,-799,-1000,-1000,-1000,1000,-715,1000,-188,1000,53,1000,1000,282,-395,81,836,1000,1000,-805,-304,247,-1000,-1000,-856,1000,-1000,-1000,810,-1000,858,1000,-1000,-1000,1000,853,755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{-1000,76,574,427,-349,8,1000,194,831,465,-570,838,1000,-410,1000,551,508,-1000,-1000,-677,-1000,9,1000,-968,-1000,1000,1000,929,-596,-521,-878,-834,-1000,253,845,816,651,1000,108,1000,250,279,633,286,447,594,-929,-1000,-887,-1000,-63,-785,-630,-914,372,-457,245,-595,1000,-496,33,-527,254,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{-771,-778,493,705,-604,-731,475,2,-553,727,-468,685,249,-951,322,631,-710,282,-244,-765,-806,87,598,-811,-367,-79,324,-743,-336,-860,135,-216,-674,-66,159,448,325,-86,349,376,46,970,355,164,810,574,-267,-431,-741,-924,263,-333,-907,-241,854,157,-18,-581,538,-882,415,55,405,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{-915,226,918,855,99,527,754,163,522,1000,-611,1000,808,-740,570,946,-1000,275,-1000,-1000,583,-876,1000,-696,-421,277,-1000,-536,-1000,-616,-720,-1000,736,765,508,797,998,-517,-818,-419,76,523,241,29,1000,1000,-1000,-435,-553,-1000,484,-1000,-930,-946,572,-41,-418,-885,1000,-259,7,-168,352,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{-1000,795,1000,260,1000,-279,194,128,645,407,560,16,233,-1000,1000,767,-1000,244,-484,-431,-897,-505,1000,-1000,-947,1000,-1000,304,-1000,-560,-1000,-1000,1000,-604,785,710,288,-293,-475,1000,298,-650,1000,677,474,316,-1000,-400,-589,-793,-1000,193,1000,-923,-1000,474,-444,-254,1000,-745,-683,419,333,961}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{213,-116,-696,-178,-1000,330,167,-253,333,917,-616,-1000,1000,-1000,277,-378,4,568,226,546,678,-931,-305,-180,-1000,1000,1000,1000,771,-1000,869,32,1000,114,-832,-646,588,-1000,-568,165,-143,1000,-244,1000,-283,-467,-440,137,1000,628,876,173,-1000,-88,62,-489,-534,-615,613,-608,697,-661,-905,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{600,-94,434,-730,1000,655,-923,191,572,-978,275,-284,-924,384,-522,-799,-213,1000,233,397,48,-11,829,8,-193,-918,396,1000,-50,-400,91,-246,1000,-202,-53,907,-1000,486,-1000,129,-321,15,1000,380,-403,82,377,1000,400,-120,-911,-349,1000,332,-208,-965,-77,44,-962,603,24,335,69,-105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{863,375,9,388,1000,1000,-769,62,1000,-1000,1000,1000,-1000,1000,-354,-396,853,944,-224,1000,1000,-253,1000,-1000,-772,-1000,-1000,-1000,-1000,1000,-1000,1000,861,260,-1000,1000,-1000,1000,-691,1000,-1000,-708,924,380,-1000,-737,280,1000,-1000,400,-1000,411,1000,607,-898,-1000,779,1000,-1000,1000,-322,471,-1000,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{665,546,-601,827,65,319,548,91,888,857,741,1000,482,-822,-933,-598,1000,553,-241,1000,251,1000,974,-187,1000,-1000,-443,-455,-1000,1000,-35,1000,-1000,722,-796,777,-846,191,212,1000,-760,-703,-77,1000,-1000,35,-96,1000,-694,1000,487,1000,655,-222,-1000,-270,1000,947,-182,251,-1000,-172,-759,-701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("COLOR:-3074024", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{441,-888,709,-541,1000,1000,26,-487,1000,-257,824,472,-977,-303,-1000,-1000,-224,1000,1000,1000,-701,666,1000,-97,-870,-1000,-498,-547,-988,547,114,1000,119,-737,-821,324,-641,680,-1000,449,-503,-1000,1000,416,-552,373,1000,-726,-1000,-403,-327,369,1000,-86,-757,-1000,1000,482,-367,1000,-1000,1000,-787,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{842,30,-927,-9,-536,-328,248,778,-141,-75,54,-619,-297,-484,-84,359,-767,742,368,-871,-982,-446,-959,-738,286,-833,673,-414,211,-346,-701,187,252,600,50,977,-959,872,111,340,603,833,-705,563,-245,-102,419,-740,-591,269,205,-213,-786,-146,807,771,-529,-61,185,-675,581,-10,575,227}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{461,860,-437,423,-863,-110,-346,398,506,674,-87,-645,1000,-1000,-83,-1000,1000,696,451,34,884,498,-1000,698,1000,1000,993,1000,514,-104,475,79,687,1000,-110,-253,-1000,-1000,-645,-511,698,1000,-1000,353,-934,-638,-1000,-1000,937,1000,1000,707,-1000,-265,-1000,-324,-285,-153,896,-394,655,-1000,1000,643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{353,-93,567,-1000,275,569,-76,1000,738,113,659,-1000,1000,496,-32,-641,-543,486,-927,624,924,88,1000,-1000,-1000,-1000,223,926,-773,-567,894,-763,629,917,-906,74,-1000,-91,169,158,-1000,116,248,1000,-609,1000,90,709,689,1000,519,-153,-275,1000,482,1000,606,-686,297,-959,-73,-27,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{785,-182,-136,-219,524,1000,1000,-1000,493,-217,225,1000,-355,546,-994,-815,168,897,-1000,1000,-294,1000,915,964,-1000,-1000,674,417,173,666,342,1000,-519,-1000,-1000,-839,553,1000,-741,456,329,-1000,930,-597,348,-374,544,1000,-694,1000,-1000,-269,546,-1000,439,-975,-377,53,-890,600,-1000,1000,-1000,-893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{461,-410,768,-564,451,602,-1000,540,794,-471,406,-703,-1000,-584,-528,-984,36,1000,1000,1000,25,-11,535,184,1000,-500,389,1000,-184,-695,297,-874,1000,303,553,868,-975,-223,-1000,-117,82,-219,469,590,-715,78,204,-207,587,460,-440,139,1000,5,-956,-1000,-582,466,-1000,663,-35,-881,-1000,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("COLOR:-14619857", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{313,-586,-156,-541,216,671,-131,-501,323,607,32,235,-977,-1000,-1000,-1000,241,944,1000,201,-768,1000,177,1000,1000,-293,990,428,501,-460,907,102,244,-278,500,-779,732,-365,-1000,649,1000,-919,142,528,-227,267,153,-726,770,-171,-327,307,387,-1000,-757,-1000,278,583,-1000,250,-882,-738,1000,62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{484,-134,-632,-778,1000,747,631,-1000,5,-116,-155,834,-576,383,-991,-736,-113,853,-217,589,-735,1000,596,748,-712,-889,1000,-1000,692,-39,613,1000,-630,-1000,-364,-723,695,647,-630,1000,424,-585,900,228,219,273,400,999,503,-1000,-1000,-391,354,-533,311,-636,1000,-20,-788,163,-799,1000,-1000,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelPaint():java.awt.Paint",
            new int[]{502,-920,-129,-795,-292,892,-974,4,524,522,428,-1000,-51,-1000,-815,-928,-3,473,464,441,-1000,-53,215,644,-89,-708,893,1000,400,-400,1000,-400,415,353,461,-114,117,-288,-633,1000,453,-768,-1000,-45,-582,134,503,-400,1000,-587,-64,677,467,-851,-852,-677,192,196,-470,453,-1000,420,-179,619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelToolTip():java.lang.String",
            new int[]{644,-523,924,-203,542,836,-776,685,665,821,506,-863,505,167,-954,273,566,-103,440,-1000,-614,-877,-692,776,-478,-503,520,-930,-891,-344,512,-198,-779,-116,196,-3,1000,161,752,-714,-474,626,28,-146,-673,-479,-801,-616,137,89,847,1000,869,420,156,575,-677,-797,1000,315,994,-855,1000,-486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelToolTip():java.lang.String",
            new int[]{-992,346,-316,13,-906,-909,-838,-265,65,563,-629,-166,601,154,83,378,-848,-144,331,756,-205,495,-113,-715,80,-767,-203,274,-185,-681,-557,-574,674,-606,551,103,-40,-881,-955,692,-102,351,-268,-910,435,658,426,110,668,563,-707,-435,-246,-402,-29,695,429,961,-677,631,-876,397,560,690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("java.lang.String:Kzk3OC41MzA=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelToolTip():java.lang.String",
            new int[]{-13,399,-541,-967,-987,-978,-930,530,275,-154,-440,944,425,-482,602,-165,-326,179,-125,-83,849,359,-236,-1000,1000,-1000,-1000,332,-496,959,-576,-1000,480,-1000,-586,1000,1000,-529,-467,-41,-468,141,358,-132,382,1000,339,699,932,-65,393,-460,-924,-1000,771,681,430,1000,-687,-281,-464,780,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelToolTip():java.lang.String",
            new int[]{-1000,1000,-896,-516,-817,378,1000,618,210,-1000,1000,-1000,-1000,186,-954,255,-656,-1000,1000,356,1000,746,-909,309,189,343,950,-358,-1000,113,608,1000,-266,571,-1000,-1000,-860,-285,-218,28,-708,-291,1000,674,354,845,-298,-369,-370,-383,324,-1000,-315,1000,1000,478,-505,-582,-938,1000,667,356,674,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelToolTip():java.lang.String",
            new int[]{1000,-992,59,-1000,335,-334,593,176,901,-224,1000,-342,497,455,-477,161,693,-271,737,279,-334,-541,-1000,-180,-1000,-338,454,-1000,-1000,-1000,1000,691,-654,226,66,-560,1000,463,-121,-90,-447,1000,-153,-1000,121,-516,-1000,190,-400,1000,1000,-72,161,-995,585,431,422,-146,313,398,472,-472,-1000,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelToolTip():java.lang.String",
            new int[]{-424,1000,127,-876,-1000,-109,-1000,383,-410,-88,-493,872,596,1000,-196,-263,-1000,349,497,671,759,1000,-242,-1000,1000,-1000,-1000,1000,-452,-681,-547,-574,1000,-1000,1000,-38,1000,-930,-1000,465,195,16,437,-428,640,739,-135,977,1000,1000,233,-1000,-471,-739,731,1000,-720,1000,-339,444,-1000,1000,1000,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelToolTip():java.lang.String",
            new int[]{211,778,203,1000,1000,924,-396,1000,828,-1000,728,1000,-1000,-146,244,164,-1000,-1000,826,-1000,1000,1000,-163,144,531,780,356,-505,-901,1000,-383,-294,-1000,839,-353,-678,46,1000,926,-513,-372,-1000,105,1000,567,477,949,95,523,216,-231,220,512,-1000,831,-1000,-1000,68,-210,-1000,1000,1000,880,-906}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelToolTip():java.lang.String",
            new int[]{592,400,-896,-516,128,-3,-646,-268,1000,538,888,-1000,993,1000,602,1000,1000,-1000,655,-586,246,756,-874,1000,-1000,554,-647,-1000,-1000,-1000,1000,200,-1000,1000,853,-146,995,357,-67,28,-708,-358,733,-132,-605,142,-1000,-1000,1000,1000,324,-1000,120,-995,1000,-671,-204,-1000,139,1000,62,356,-1000,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelToolTip():java.lang.String",
            new int[]{-1000,1000,-186,527,1000,-3,-1000,-565,807,538,-944,-333,-332,339,1000,787,723,-1000,341,-280,228,678,218,992,-1000,637,-796,-1000,-185,-901,42,602,-1000,1000,387,308,-760,187,-82,1000,-708,-358,463,1000,-937,382,1000,-1000,1000,-1000,-1000,-233,1000,911,-83,-1000,-955,-818,-836,1000,-140,356,-1000,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelToolTip():java.lang.String",
            new int[]{-303,1000,-58,619,-1000,-1000,-395,-176,525,-787,1000,-482,-20,1000,17,942,-281,-997,997,-962,1000,1000,617,-67,232,-863,-1000,260,-1000,-685,-802,1000,512,67,1000,-825,1000,-968,-866,201,-1000,-1000,70,1000,-296,1000,-481,719,1000,719,-1000,-1000,-1000,-1000,1000,-57,-446,27,-1000,229,-1000,1000,751,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{-399,175,5,-268,-688,-829,-16,-322,-467,1000,-638,-129,636,961,146,-330,128,495,-25,-604,605,734,649,-152,-308,-861,-915,-195,1000,-633,23,374,-419,-593,-539,1000,-853,962,-1000,-219,-723,709,1000,-756,1000,65,109,1000,1000,-600,-1000,539,594,247,-543,-746,-817,1000,1000,-1000,647,-605,831,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("java.lang.String:Nldp", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{581,175,-978,-249,-180,324,-136,-582,-662,361,-638,-285,-272,750,1000,152,-1000,230,332,491,-410,734,175,628,-779,-861,-700,-299,-646,-935,64,718,-419,431,-700,-1000,-853,275,-951,657,1000,489,-351,186,23,-1000,-645,142,859,1000,103,1000,413,89,-543,493,-801,1000,-129,-54,-1000,275,308,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{-105,78,-41,-947,-291,242,-921,388,113,420,32,1000,413,-357,61,-378,-249,-691,-648,73,-1000,919,400,633,-996,671,201,929,-491,-786,-44,598,-824,1000,-478,-717,-1000,477,-155,-206,196,140,-73,760,-1000,-5,-1000,902,-591,144,-1000,756,-711,-870,-750,-215,-30,309,-278,485,-458,222,-771,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{361,498,663,46,1000,1000,-705,1000,-343,-1000,593,-203,-959,-1000,-1000,13,210,587,-168,-41,-64,-576,573,-514,-207,1000,-1000,968,-687,1000,665,-1000,-1000,757,-1000,494,-446,-609,482,812,458,-1000,-1000,1000,150,1000,-363,470,-1000,-256,926,988,-1000,1000,1000,847,974,-1000,645,1000,1000,1000,19,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{-399,0,939,358,1000,1000,-601,556,-916,-1000,-942,-1000,-1000,-382,-1000,309,928,-463,1000,1000,1000,-894,-1000,2,-421,-893,-915,398,1000,1000,-69,-1000,-325,-1000,36,786,451,-873,-861,928,-723,709,-1000,-432,1000,65,111,628,592,-297,677,135,-738,-549,1000,24,353,467,1000,-1000,647,86,934,190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{472,1000,-331,-387,-730,-150,832,-1000,1000,-1000,-1000,-322,1000,1000,102,-1000,798,779,4,-183,-18,1000,-541,-770,-1000,-1000,-732,-1000,-883,103,872,973,-200,-880,1000,1000,-695,427,-301,-530,-744,1000,869,-1000,1000,-1000,112,-1000,592,-1000,27,-1000,1000,1000,-1000,-1000,-686,734,214,-257,-1000,620,974,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{1000,533,-899,-375,-507,419,77,-1000,972,-599,-882,-306,792,814,-402,627,638,-129,732,1000,854,366,-1000,628,-264,-631,-601,-1000,482,-536,274,-562,-740,-391,884,273,1000,-161,-14,-536,-427,1000,392,-1000,-479,-1000,-197,547,612,-1000,1000,-534,792,13,680,-938,1000,1000,1000,14,-172,-128,-647,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{-164,-820,843,-204,617,1000,783,294,1000,-1000,-35,-971,499,332,-1000,-908,1000,757,409,316,23,240,-622,-1000,-801,284,-46,-1000,42,1000,-882,-979,-607,22,234,1000,-498,461,393,491,-1000,-862,-1000,400,930,581,341,-1000,-141,-539,99,-210,330,822,107,287,-40,-1000,-1000,1000,114,988,563,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{-276,-1000,263,-481,-796,1000,787,-1000,363,-738,-173,53,298,752,-1000,-1000,1000,353,170,-83,1000,327,-3,-983,-606,-690,-88,-1000,826,117,-151,-1000,-598,-1000,313,1000,-711,1000,-89,-881,-1000,534,185,-544,-75,600,272,112,-138,-1000,242,-817,200,428,1000,-1000,226,-123,-192,591,1000,-170,-33,249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{1000,-1000,-198,-321,-528,1000,-342,-5,382,1000,-1000,-249,1000,46,-621,893,-1000,876,-904,-337,-1000,1000,616,3,427,1000,503,-1000,-1000,23,-1000,1000,-1000,-1000,-1000,159,-12,427,400,445,862,43,-891,951,-997,1000,-736,-122,946,814,1000,1000,7,-662,-150,620,609,1000,-1000,1000,-78,1000,-130,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{530,-657,-295,15,899,1000,464,101,204,-716,-197,644,413,294,875,-600,-579,959,-1000,-743,-1000,798,1000,-256,-1000,1000,464,619,-929,1000,-1000,1000,-1000,1000,-825,-529,35,599,400,201,677,330,-1000,1000,-457,-20,-368,-152,702,-190,880,1000,157,-197,-500,8,400,-400,-1000,1000,-697,1000,-376,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getLabelURL():java.lang.String",
            new int[]{1000,1000,406,-796,-168,1000,-583,-1000,653,-872,-738,-751,267,1000,23,306,322,-174,795,1000,755,-576,-407,278,-28,-175,-1000,-1000,-294,251,1000,188,134,-1000,1000,830,954,623,-1000,84,678,1000,266,-1000,-762,-1000,308,-747,1000,512,1000,-751,1000,-359,345,-824,-85,1000,1000,484,-959,-727,443,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getPlot():org.jfree.chart.plot.Plot",
            new int[]{1000,460,584,-1000,36,-1000,-1000,326,10,144,140,1000,-18,443,515,-522,-1000,611,119,435,-585,187,-1000,1000,612,-1000,-243,-193,706,-1000,-29,-848,1000,-937,-627,-491,821,-600,830,1000,-357,479,-461,68,-527,-1000,-726,1000,1000,-139,514,-369,-398,1000,681,-620,-812,228,-1000,-676,464,-1000,102,190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getPlot():org.jfree.chart.plot.Plot",
            new int[]{1000,713,-1000,-1000,1000,1000,4,98,-547,-393,363,1000,1000,443,735,-449,-1000,611,1000,435,-585,-573,67,423,-1000,-1000,594,-178,122,-457,-1000,-1000,-650,1000,-400,-845,1000,-339,-18,1000,69,-443,-400,-1000,262,-706,-1000,1000,217,-1000,514,62,1000,-840,1000,1000,-1000,228,107,167,-529,-681,-971,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getPlot():org.jfree.chart.plot.Plot",
            new int[]{1000,-501,791,167,451,-466,-436,-1000,1000,-401,193,-939,1000,-574,699,204,-1000,303,-115,-1000,1000,-932,949,-843,1000,1000,241,-968,-627,687,556,-956,-139,265,1000,-796,647,1000,-176,585,1000,-198,-901,-1000,-186,749,-116,976,291,-885,-549,-915,284,-321,1000,-323,1000,-295,292,-1000,257,1000,366,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getPlot():org.jfree.chart.plot.Plot",
            new int[]{1000,-407,771,229,1000,609,-245,-760,1000,925,271,-440,1000,-792,1000,308,-1000,956,1000,-366,406,-369,565,-315,-713,1000,-55,55,-820,-174,-294,-1000,-876,-1000,-157,-438,644,836,332,387,1000,-842,248,-1000,668,-1000,-1000,1000,1000,45,-251,-1000,435,-605,781,592,1000,-766,894,-1000,-66,575,-233,-291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getPlot():org.jfree.chart.plot.Plot",
            new int[]{-412,299,518,-1000,1000,-560,-852,357,376,-393,46,918,1000,-145,-536,-460,875,1000,1000,576,566,534,-1000,-686,539,-964,807,-405,852,-143,-309,-660,1000,16,-125,-1000,1000,-627,1000,1000,-11,112,-372,-1000,-206,-1000,-1000,1000,459,159,936,195,-976,1000,-444,-391,-254,11,-702,109,-73,-664,308,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getPlot():org.jfree.chart.plot.Plot",
            new int[]{-268,871,-606,177,-183,-588,968,891,-306,-955,-931,221,180,178,410,-318,146,703,995,881,-844,-607,143,-150,646,353,672,451,351,109,-405,571,531,-645,854,-191,-514,-619,813,890,-171,770,487,-860,34,-932,-167,999,-102,846,812,-208,-290,-207,-616,745,-820,319,121,-422,-541,-487,703,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getPlot():org.jfree.chart.plot.Plot",
            new int[]{732,-815,-606,1000,-183,203,334,241,-221,-955,-5,520,279,-102,72,-196,-29,428,-628,24,-507,-607,-796,619,-150,-80,-765,143,1000,109,-1000,1000,-35,1000,-1000,779,-1000,-402,-803,836,-916,-376,590,-667,-390,-442,804,281,-1000,-1000,751,460,134,-173,500,-905,-488,-950,294,-587,62,-1000,325,-7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getPlot():org.jfree.chart.plot.Plot",
            new int[]{-1000,1000,814,662,-198,-1000,1000,1000,-1000,-1000,-1000,1000,-772,-44,515,-1000,400,149,-556,1000,-399,213,-206,843,614,-542,48,448,575,434,733,-400,402,480,931,-184,-914,-1000,-445,-692,-1000,-1000,483,68,-710,-1000,1000,-257,-706,-376,1000,924,-398,-224,-252,936,-812,483,930,891,464,-1000,224,-872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getPlot():org.jfree.chart.plot.Plot",
            new int[]{-412,1000,518,-615,-287,-995,-854,1000,-1000,-732,-1000,1000,-587,650,-330,-174,875,-514,-1000,-165,-186,534,-834,695,217,-734,-457,909,852,-711,1000,1000,1000,16,109,635,-31,-1000,479,44,-1000,56,-32,837,-879,-1000,1000,-37,459,-1000,1000,195,-976,672,-444,-796,-830,-71,-704,1000,1000,-1000,105,-121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getPlot():org.jfree.chart.plot.Plot",
            new int[]{-1000,386,904,690,-949,-275,-1000,-204,-470,-1000,-214,10,-288,675,92,-862,1000,-681,-1000,-1000,214,960,333,725,810,-106,-853,250,726,89,1000,969,500,-961,1000,961,-610,44,-546,-1000,-358,-754,1000,1000,-1000,167,1000,-983,-345,784,-84,-414,-350,163,-777,-74,131,462,430,-596,1000,-449,65,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{320,-542,214,629,693,-750,-1000,-407,216,193,947,938,-618,966,741,239,195,-346,356,299,85,-613,-62,-476,-344,272,-685,887,-320,-251,154,-2,404,-833,248,-197,389,319,195,871,1000,-509,-803,-669,1000,-733,649,-1000,-491,1000,-613,-317,825,279,262,-346,-437,-130,1000,-517,438,299,244,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{-900,483,-658,635,374,-726,268,-938,189,-277,-764,-988,-158,-355,717,-751,297,-344,311,-583,-640,988,108,-243,339,795,974,-920,-574,-157,-911,-140,87,986,-883,118,329,-634,-983,589,890,891,-308,552,-897,212,438,655,461,-824,443,96,-209,-501,-498,590,305,-707,-498,702,-190,-3,288,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{-1000,-734,904,515,-913,-135,-1000,-356,209,816,634,1000,-1000,960,1000,-1000,-538,632,277,208,-1000,-1000,-291,-195,1,816,-755,595,-433,366,257,-106,945,-685,444,-671,187,1000,-489,1000,927,-347,-799,-667,1000,-984,1000,-719,1000,575,-967,-1000,515,286,-565,86,-466,-1000,689,-1000,436,-134,1000,53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{-1000,-289,-312,-162,575,-1000,-771,-434,376,795,-433,-383,-995,576,-471,344,-735,883,1000,-651,1000,-793,-851,-852,-128,591,645,-1000,-861,905,-530,408,412,1000,389,153,580,-1000,-522,-193,896,270,-685,79,-103,-1000,1000,-405,1000,-418,1000,-621,-26,-592,333,-296,-299,-1000,-620,-117,1000,-587,1000,-941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{542,-1000,-472,1000,-162,270,-1000,-652,-266,713,1000,-3,-539,795,904,-100,637,-1000,658,-50,719,-479,862,690,-861,72,-1000,1000,-58,-705,1000,-282,386,-1000,-589,-670,1000,230,831,1000,1000,168,-535,343,1000,-1000,960,-1000,-1000,1000,-1000,1000,1000,610,403,-700,-625,1000,1000,-752,1000,1000,288,-537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{-1000,-281,563,1000,-362,-503,-220,-1000,-458,103,506,-433,223,-314,1000,-700,1000,-1000,298,420,-1000,-103,11,-759,818,1000,-263,-275,-1000,-499,-1000,-452,-161,396,-1000,-32,230,-78,-1000,1000,1000,1000,-1000,1000,-208,-1000,526,-1000,276,-976,-250,244,283,197,329,955,-412,-111,737,-44,941,-6,422,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{-1000,-188,89,774,-145,-63,373,-1000,-315,-772,-268,-400,-24,-336,873,-693,538,-525,-681,-333,-1000,189,346,-60,713,534,211,6,-280,-1000,-490,-1000,-147,424,-1000,-746,488,397,-639,980,1000,1000,-1000,1000,-364,-276,436,-434,82,-229,-1000,-54,1000,654,932,658,-378,-119,437,-371,-1000,389,311,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{-471,680,202,-79,166,-277,921,-938,140,105,-963,-1000,184,-515,1000,-892,297,-57,850,-1000,-393,1000,96,1000,-224,982,1000,-920,103,911,-911,563,-179,1000,-661,317,205,-217,268,-123,890,1000,682,1000,-1000,212,-13,936,588,-1000,443,522,-691,-639,-961,738,886,-233,-1000,662,746,-321,790,-512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{697,-1000,709,-338,40,-994,-341,-1000,-204,-341,232,1000,-959,794,1000,-164,-303,-1000,-1000,-1000,-1000,275,1000,-1000,1000,1000,225,930,158,-1000,-1000,-1000,1000,-1000,-1000,-8,181,-140,-1000,1000,244,1000,-1000,1000,832,731,1000,-705,732,-956,-493,130,737,-462,-1000,1000,-394,-517,1000,-939,-1000,409,215,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{21,483,-1000,-1000,1000,-1000,268,-453,1000,-775,-1000,-1000,-155,-982,307,-492,-1000,1000,1000,-1000,795,1000,-398,1000,-1000,133,974,-357,49,1000,-94,1000,-293,1000,228,353,773,-1000,-983,-972,-908,52,1000,110,-897,212,-94,1000,535,-630,1000,689,-1000,-1000,-1000,-444,1000,-707,-1000,1000,744,-408,288,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{-964,-1000,49,1000,369,-693,-265,-1000,359,-458,105,1000,-1000,1000,1000,-711,-1000,-960,-552,-1000,-495,-158,979,-991,722,663,402,1000,-1000,-1000,35,-1000,1000,-1000,-1000,-13,869,-1000,-793,1000,927,819,156,709,1000,-550,1000,-1000,127,205,-379,903,706,-673,-490,342,-78,-1000,-40,-662,-1000,622,816,-880}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{697,-1000,-112,1000,251,381,275,-1000,209,799,-695,1000,-779,1000,1000,-1000,424,-1000,68,-320,86,-913,1000,-989,152,1000,-755,1000,-290,-1000,-1000,-540,1000,-1000,-646,526,187,1000,-483,1000,-348,-378,-331,1000,1000,67,180,-1000,-1000,1000,-967,432,1000,29,-46,302,-1000,-530,1000,-1000,-97,1000,172,377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{18,1000,-301,-1000,359,-595,1000,-345,698,-309,-1000,-1000,-415,-412,435,-450,-1000,1000,1000,-1000,1000,890,-375,1000,-1000,560,1000,251,623,1000,1000,757,355,1000,714,287,38,-728,123,-924,-1000,-299,1000,117,-1000,1000,479,1000,1000,1000,1000,410,-1000,-1000,-1000,-332,1000,-991,-1000,1000,221,-663,692,-862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("TYPE:java.awt.Font", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelFont():java.awt.Font",
            new int[]{-602,-739,287,196,908,694,-221,-911,256,115,-181,663,972,773,-152,348,844,-906,63,-824,152,659,-97,-756,-654,761,329,-329,-277,-715,537,-246,-307,-921,794,810,840,-391,-275,989,100,-769,554,253,-856,142,581,-309,-693,-228,-547,305,269,-806,700,-457,-924,997,-581,956,-898,-39,-983,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{160,-189,734,-1000,94,862,-156,1000,-680,-211,-712,760,180,-1000,351,425,118,-19,-212,886,1000,-551,-1000,834,-288,-515,-311,-374,-1000,385,838,258,-778,-1000,1000,200,775,-251,-866,205,243,705,-621,-308,146,-1000,-1000,1000,35,435,-88,-14,25,-953,-539,697,-788,-730,-320,-183,890,-540,-419,-550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{740,575,787,383,-247,-146,-559,-21,-261,175,-175,-1000,-889,390,835,229,-1000,11,959,286,219,-545,-627,-358,262,-1000,24,1000,45,266,610,-438,-1000,938,-701,-561,-417,763,806,1000,-40,392,348,-1000,470,1000,618,418,-456,-844,-222,-217,15,134,522,-987,-1000,715,489,-358,810,-950,-356,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{683,228,184,637,-1000,566,42,-496,-284,714,-283,-803,-1000,989,425,325,-601,-1000,369,815,790,-401,-1000,600,-174,-821,520,-670,-146,-1000,604,-256,-1000,505,-570,-1000,-405,-430,1000,-437,465,325,-253,-746,-666,751,441,656,441,-1000,-745,25,368,-269,1000,115,-1000,401,926,-760,65,-934,-727,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{301,563,1000,-1000,55,566,-286,706,-264,653,-475,-151,-589,220,1000,425,-1000,-264,860,1000,1000,-1000,-812,233,-274,-1000,253,-325,-203,253,395,290,-1000,105,158,-1000,-237,752,-489,1000,-791,816,-601,-414,444,-334,-194,-170,-1000,-257,-178,83,1000,3,452,-579,-371,-510,353,-458,859,-1000,-240,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{-109,-822,-186,1000,1000,-1000,-1000,1000,1000,11,-126,-400,-901,421,641,-1000,677,-1000,-705,-411,1000,304,1000,-1000,1000,877,323,1000,-1000,-1000,-6,1000,-1000,-151,-453,-435,1000,301,-798,3,-858,557,-790,1000,-512,933,16,-186,-81,-539,-1000,863,767,1000,588,110,-791,826,784,105,549,-620,798,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{-16,112,82,342,1000,-941,-1000,909,611,154,-1000,1000,-201,-271,403,-778,758,199,-381,-21,1000,242,1000,-1000,1000,1000,-305,995,-1000,101,-561,863,-391,-1000,148,-435,1000,490,-1000,-958,-686,547,-1000,905,-273,-6,267,-1000,-469,247,-235,726,806,780,-363,789,150,307,-529,108,386,-611,1000,-764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{344,-80,-441,-212,256,279,-742,227,-713,-326,-1000,686,603,-698,-107,-554,554,386,-625,221,875,-169,815,-30,807,464,-937,995,-1000,95,12,-528,-391,-1000,148,521,774,-564,-358,-974,714,475,-779,1000,-54,-467,228,3,206,1000,-1000,1000,-489,-328,-655,475,-131,-398,-529,-30,-188,-611,348,-213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{384,247,-260,400,94,376,-156,531,-165,-211,376,41,79,-112,151,893,-545,-773,-807,-18,1000,-427,-20,583,243,216,42,571,-863,38,496,298,-391,-574,64,-150,1000,-679,-1000,-792,392,864,-621,581,244,-183,127,-204,-176,169,-1000,-264,483,-953,930,142,-413,-730,-320,-251,27,-611,1000,-213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{708,586,922,659,-189,524,-900,512,-407,103,-353,25,-1000,-92,1000,-7,-260,-156,788,176,1000,-693,681,136,702,-800,110,-110,-278,-1000,643,876,-803,-1000,37,-915,1000,-242,-1000,-382,-691,949,-919,555,-807,660,416,-96,-1000,-777,-1000,570,-160,-990,755,-1000,-506,-514,1000,-1000,1000,-457,1000,-707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{-811,-1000,999,-383,766,112,-211,957,261,220,660,400,1000,-400,433,-334,853,-1000,-959,433,400,55,567,-315,704,-603,-631,174,-1000,-901,45,1000,-296,-400,179,-435,-28,361,63,53,-838,282,-1000,400,-167,370,-1000,130,-33,-387,211,-126,1000,-134,59,1000,-509,-225,-492,599,278,-418,-641,-391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{49,-267,-759,-433,347,294,-261,653,-356,-635,-833,180,988,-698,-305,-307,844,231,-949,266,539,3,623,338,1000,647,-1000,773,-1000,176,-110,74,10,-544,234,914,567,-245,49,-382,504,363,-288,597,-47,-707,-314,98,405,721,135,179,-666,70,-580,955,-324,617,-929,319,-284,-662,-312,-141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{597,264,359,314,237,-798,-520,662,-7,-826,236,-1000,392,-589,-746,376,-403,21,-468,163,-119,-253,-920,-345,813,-372,-1000,1000,-339,1000,-97,-1000,-490,1000,-717,163,-227,905,70,1000,1000,492,600,-595,1000,-21,483,-140,870,1000,-632,9,-1000,1000,210,-504,-265,1000,-1000,700,-642,-1000,-634,-691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelInsets():org.jfree.chart.util.RectangleInsets",
            new int[]{751,9,-237,976,684,-457,-950,1000,-265,-671,-288,-237,-753,236,-287,-60,-108,-316,-527,621,510,-835,-960,-317,247,-237,-631,1000,-670,940,668,-216,421,344,-93,-160,1000,235,-241,443,411,925,-385,-250,847,230,-35,764,841,915,-829,523,-878,-1000,-34,597,161,798,-43,300,280,-929,-671,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("COLOR:-13920915", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{852,-469,-6,778,-1000,-781,-407,-1000,1000,-342,-790,-654,-122,-601,-318,-969,1000,-1000,555,149,-915,-467,490,863,400,251,908,665,807,363,1000,750,58,-213,-98,409,-757,1000,811,-875,-295,-827,-147,925,164,1000,199,233,1000,383,834,-1000,220,1000,71,-1000,1000,1000,383,298,-1000,677,-1000,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{56,-250,-991,-723,-1000,1000,-747,-1000,359,-342,-948,-142,446,260,-318,177,-76,-156,-825,544,-400,355,-1000,-874,400,271,-441,665,1000,-282,-856,874,58,-664,737,702,125,1000,658,949,-295,-366,54,925,-108,-1000,868,233,197,-943,0,47,4,137,85,-218,242,-446,383,-755,-400,-1000,-22,-93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{-296,148,-831,-938,-681,274,-1000,-455,-906,-655,796,-1000,-808,878,-847,-189,1000,-1000,-1000,1000,-678,-800,423,1000,1000,1000,337,250,1000,460,-1000,240,-1000,-1000,287,-17,-129,-779,399,-1000,-865,-613,-1000,916,-1000,-1000,394,1000,47,818,-687,-1000,-692,147,-790,-1000,-556,-18,639,-1000,290,-224,-962,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("COLOR:-7715388", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{-248,1000,409,-1000,-630,325,708,65,143,30,983,-844,316,-69,-31,1000,-593,-1000,781,720,28,-927,190,-52,-650,-258,328,313,645,65,-617,-269,-56,-875,1000,-262,-367,781,-739,1000,-244,502,16,967,-418,-969,-1000,-1000,-21,384,114,-251,-862,-356,112,1000,-400,-1000,-477,-1000,-121,-8,1000,616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("COLOR:-5942552", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{-309,1000,122,-286,681,-366,-1000,-1000,1000,1000,-323,74,-73,-1000,-277,578,447,421,-942,1000,1000,1000,385,766,-1000,-538,605,-1000,-481,-887,-182,-29,-1000,-1000,700,149,1000,-837,1000,-31,1000,-954,489,1000,-1000,-24,-14,1000,899,-594,-1000,-909,1000,1000,549,-1000,314,899,747,-44,904,-634,-877,-8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{-203,594,749,104,-665,-484,708,-771,-84,30,-777,53,-814,-636,-1000,1000,-1000,808,-1000,1000,28,1000,1000,-482,-650,312,-864,-741,-404,553,-617,-200,-634,811,296,-262,400,547,-567,1000,-1000,772,-690,814,-1000,-273,-1000,-1000,-692,898,1000,-186,266,-1000,-639,1000,-1000,-110,-1000,178,1000,1000,1000,-427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("COLOR:-11711668", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{-383,485,1000,1000,-1000,1000,-572,-1000,845,843,-948,178,-791,-334,-1000,-229,610,-537,-110,518,268,1000,1000,1000,-1000,-385,740,-957,383,-728,1000,-1000,-1000,-510,737,-783,-803,1000,960,-1000,444,-642,-151,321,-721,1000,-1000,1000,429,-119,-1000,-658,1000,110,-236,-107,-661,846,-78,580,148,610,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("COLOR:-1519964", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{-1000,-878,763,-1000,1000,206,-348,454,261,1000,349,1000,1000,-1000,-826,1000,-1000,1000,-766,1000,1000,1000,-1000,-1000,-755,-1000,-1000,649,86,-1000,-1000,595,-1000,-628,1000,471,1000,-1000,-552,-208,-1000,-499,1000,781,-1000,-1000,-263,-147,-60,-1000,-1000,1000,1000,47,737,1000,-1000,-197,-122,-163,1000,-919,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{931,-957,-252,-946,-1000,-797,-53,-72,156,314,640,-1000,-781,107,1000,-406,-1000,-1000,913,-199,-1000,-1000,1000,1000,1000,1000,331,-1000,1000,1000,1000,-561,1000,-659,199,-899,-1000,1000,0,622,-952,1000,-1000,-277,-629,1000,-873,1000,-628,1000,1000,-1000,-1000,365,972,-1000,1000,-1000,256,-897,-1000,1000,-285,417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("COLOR:-7817139", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{661,189,705,-820,-1000,-426,-850,-831,1000,-268,180,-1000,-376,-840,845,308,-1000,-352,-241,736,38,364,637,1000,-560,657,256,-1000,1000,35,1000,-796,47,-1000,1000,-686,-816,1000,353,1000,355,916,-847,1000,-938,399,-1000,1000,51,1000,868,-1000,-197,96,585,-736,773,-302,-400,-105,-756,951,-819,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{1000,-1000,-841,-572,-1000,-1000,-639,-1000,1000,-562,1000,-1000,-1000,800,-437,-544,1000,-1000,-609,62,-1000,-586,-192,173,400,1000,1000,978,889,1000,1000,-103,91,-725,-1000,1000,-736,276,831,-1000,-1000,-1000,-386,1000,361,254,576,321,1000,422,1000,-766,-150,680,228,-533,1000,-1000,106,-1000,-794,-946,-784,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickLabelPaint():java.awt.Paint",
            new int[]{-1000,1000,-368,-286,1000,-1000,-946,1000,1000,1000,2,-1000,1000,-1000,671,659,-1000,-1000,390,735,1000,-1000,-2,-375,-1000,-1000,1000,-784,-1000,-175,-1000,1000,508,-176,1000,487,894,-829,-429,1000,1000,179,-1000,-218,-352,-742,-1000,158,454,801,-959,-1000,519,563,1000,-1000,197,235,298,-1000,1000,-64,927,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkInsideLength():float",
            new int[]{1000,-1000,-146,-7,81,-1000,428,821,-802,261,790,174,619,113,487,1000,-443,363,418,-741,-90,-888,76,-121,1000,-1000,-1000,-569,-1000,115,203,1000,858,1000,63,855,484,-1000,972,1000,1000,-1000,-8,-1000,-843,1000,1000,1000,-221,1000,-1000,87,-1000,-583,-1000,1000,1000,-490,691,-505,1000,-262,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkInsideLength():float",
            new int[]{1000,1000,-871,-308,256,266,740,-883,-41,179,505,1000,291,-806,202,188,-303,498,883,-301,-549,-80,1000,842,1000,98,526,-1000,691,891,-160,-828,-400,-351,71,914,40,-1000,1000,-95,-888,-1000,365,-703,-571,-1000,-228,-335,-855,-818,-191,26,330,-1000,1000,1000,-623,-198,-1000,40,-1000,154,-247,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkInsideLength():float",
            new int[]{400,1000,-197,-810,-775,-534,-580,-392,-354,-371,642,292,-108,-360,746,-649,258,-417,894,-202,226,-339,904,711,-85,400,-221,-831,560,510,557,-310,-23,-67,818,567,623,-1000,339,13,-171,-253,554,-695,470,324,-413,-1000,-647,410,232,-246,580,288,-784,1000,-251,-331,280,-232,-334,313,460,45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkInsideLength():float",
            new int[]{-327,1000,689,-64,-567,-324,-1000,-583,-153,-403,-1000,-839,-376,-817,45,-1000,343,589,-426,12,-752,391,-660,617,-1000,-2,550,-62,1000,97,988,-1000,-1000,-333,1000,97,41,-797,-530,-958,-1000,-228,771,-240,679,-577,-780,-800,-593,-883,768,299,1000,-215,625,-77,-333,-304,-1000,308,-752,286,1000,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkInsideLength():float",
            new int[]{-162,-1000,476,-602,388,-1000,1000,518,-1000,1000,663,296,-89,578,-552,100,-1000,481,-349,-202,-882,563,484,-190,1000,-225,-221,-544,-1000,-374,339,540,-1000,1000,-399,-413,-455,901,221,-1000,819,-684,231,-1000,196,-356,1000,1000,340,-418,-430,353,-1000,982,-784,96,1000,-530,280,202,1000,-1000,-1000,383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkInsideLength():float",
            new int[]{-1000,270,229,-539,-730,-964,-580,-110,-1000,-264,642,-719,-108,-331,944,168,258,-417,894,-963,-751,-470,91,345,350,-1000,-382,-248,431,709,418,727,-23,30,818,4,974,-446,694,-577,153,-314,649,-751,414,310,177,-1000,-301,307,153,19,580,144,-644,277,-217,-278,-975,-58,961,249,-156,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkInsideLength():float",
            new int[]{1000,875,-118,-926,-1000,-481,-816,-1000,-508,319,-825,-256,-375,-1000,-48,-552,734,781,-163,-861,137,514,163,865,123,725,1000,-430,847,924,688,-1000,-1000,-672,313,-42,1000,-1000,633,555,-651,-700,223,-635,-215,-1000,-1000,-1000,-221,-1000,1000,542,663,-583,1000,1000,-603,-1000,-1000,299,-785,55,633,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkInsideLength():float",
            new int[]{-1000,-1000,769,355,-698,-764,978,700,-643,854,1000,700,-718,1000,-1000,74,-995,314,-1000,-20,-741,-564,-622,553,305,507,336,-827,-1000,1000,194,-206,540,889,87,-864,72,776,-873,198,317,-470,-9,-518,-156,-308,-1000,685,897,140,35,305,-834,1000,-469,-120,207,-187,253,437,867,206,-1000,-880}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkInsideLength():float",
            new int[]{-675,999,158,-1000,323,-351,1000,21,-816,1000,-1000,121,387,120,-194,1000,-1000,865,210,-539,-934,-595,1000,903,592,-387,-608,-1000,578,636,-16,-166,764,158,-820,-20,1000,-801,1000,673,-681,-1000,1000,-1000,-715,-1000,-771,605,-767,-1000,-739,-1000,901,-1000,-1000,1000,44,-412,-1000,520,-641,-379,648,994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{-1000,1000,884,-1000,-516,665,466,1000,-379,1000,-904,1000,1000,-235,961,-1000,-897,787,-145,109,-33,290,326,-58,1000,1000,-36,-1000,-518,801,-1000,418,-521,-901,1000,-1000,-894,469,-1000,-345,764,1000,1000,1000,589,1000,-147,79,383,-636,1000,506,713,-1000,1000,-1000,1000,1000,-354,286,-400,-1000,345,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{611,903,-472,1000,-1000,-1000,40,1000,-566,-436,1000,819,-1000,-1000,-1000,-460,-1000,-1000,1000,-1000,-33,1000,570,1000,896,431,1000,1000,1000,-74,1000,-187,1000,-142,-1000,1000,1000,-901,-469,726,-901,454,-1000,328,1000,-1000,1000,764,1000,-1000,-651,-601,166,-669,517,1000,-178,-898,-322,521,-1000,1000,-1000,-810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{-1000,-235,1000,-441,604,727,304,-137,-1000,406,-380,499,-400,140,213,400,-694,1000,-199,656,-539,497,448,1000,706,726,-544,400,-389,322,-746,145,-332,-272,450,217,-279,313,147,653,744,-203,77,1000,-642,1000,-6,-124,798,1000,-336,1000,129,-781,-605,-641,148,-400,477,-1000,-106,363,1000,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{-1000,-497,685,-821,734,109,-125,-1000,-586,1000,-1000,-1000,-1000,-1000,-869,729,-682,-266,-1000,-219,1000,139,-578,-513,-1000,-1000,1000,1000,-813,-1000,940,1000,902,489,-465,1000,-93,-1000,1000,-105,-1000,-1000,-1000,-1000,-59,1000,843,-1000,1000,-1000,-1000,-1000,-1000,1000,552,1000,-1000,-1000,-1000,-583,-1000,476,400,-434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{-924,-498,69,-1000,-882,242,651,343,746,1000,-1000,-701,-920,-295,826,-103,358,762,-190,-219,-267,-1000,-943,-400,142,-871,-103,-1000,-957,-1000,289,297,-436,-1000,-389,-468,-91,367,135,1000,-559,-504,184,-1000,-972,1000,-1000,-1000,1000,514,-1000,1000,-755,1000,-417,93,-54,-1000,1000,-649,1000,845,1000,-648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{-691,1000,183,-869,298,1000,296,1000,-1000,1000,-151,1000,1000,609,1000,-1000,352,1000,-1000,1000,-680,109,1000,-402,1000,1000,-863,-1000,11,463,-1000,-124,-1000,-881,1000,-791,-512,661,-478,-238,485,1000,1000,1000,-804,1000,152,887,-10,599,53,1000,549,-1000,-52,-944,578,1000,384,-1000,1000,-1000,1000,-832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{836,368,-636,1000,-1000,348,646,135,-1000,-439,1000,684,-30,-586,543,-254,-769,-1000,-32,-877,504,-487,971,310,151,1000,-350,-643,-1000,702,97,1000,-134,619,1000,-1000,343,248,828,85,-182,1000,447,681,89,800,775,379,770,244,298,579,753,735,407,670,723,-468,509,1000,-605,-1000,-1000,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{374,364,373,570,-926,63,1000,-1000,-1000,334,-34,-794,714,-1000,-79,-840,1000,-732,-601,263,-872,1000,69,-1000,33,1000,-192,-1000,1000,1000,-29,485,32,-295,-523,-121,805,-853,767,1000,525,622,1000,-756,-548,-77,-1000,1000,-1000,90,531,739,539,740,281,-1000,-296,1000,1000,-354,1000,-1000,869,-749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{901,368,-322,-628,-1000,-503,-205,359,369,-220,78,-730,485,-819,384,-578,529,309,1000,-1000,-548,-157,300,310,627,-935,-1000,-166,1000,-970,-430,1000,-206,-193,1000,-1000,1000,-574,-951,899,-883,-193,673,12,270,-525,149,-634,-387,-342,-108,-948,653,735,1000,-727,-156,-468,-193,-137,567,817,5,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{-1000,1000,754,-763,108,-1000,-84,1000,1000,-601,-657,-457,-263,-1000,1000,-1000,-649,900,992,-1000,1000,-610,824,-1000,-788,-849,-350,679,-957,234,842,1000,-262,-479,97,886,-1000,-1000,-199,-82,-548,1000,-105,-328,1000,-600,599,-248,499,345,44,-1000,-1000,164,25,661,-1000,194,-1000,1000,-605,-757,-765,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{-499,-131,562,-1000,-1000,-657,-451,-647,1000,1000,-870,-1000,-596,-908,62,97,98,479,1000,-1000,210,-26,-628,-357,-536,-1000,-20,1000,30,-1000,308,26,83,-150,-28,328,938,-1000,88,238,-1000,-1000,-727,-772,-225,875,610,-1000,707,-420,-1000,-974,-747,1000,430,314,-336,-1000,-699,-1000,-201,1000,913,-646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{-1000,-380,279,-301,-98,209,1000,174,-31,152,-534,400,400,-464,934,-17,-931,-188,1000,-224,277,592,400,578,1000,400,275,-400,-715,261,-339,593,-23,-177,-719,-400,184,30,60,-11,0,562,400,400,272,1000,905,94,568,595,400,425,199,-400,524,-209,400,400,-351,-580,-580,-400,508,105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkOutsideLength():float",
            new int[]{727,876,924,1000,-912,40,766,363,532,363,-1000,-9,-209,-696,-800,-1000,1000,-119,1000,-61,556,1000,-183,-1000,67,-332,1000,-1000,972,1000,516,-591,311,662,-9,365,428,-899,908,-40,574,384,462,171,-782,869,-490,347,163,-399,479,983,679,-102,-1000,565,-146,1000,1000,-471,-352,-812,-110,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{-1000,332,204,-1000,563,375,1000,649,-478,-365,390,1000,42,1000,311,-1000,-1000,-400,-321,401,-143,1000,-206,-1000,1000,-836,-1000,-1000,-718,-410,694,516,-935,-682,-1000,-1000,590,591,-397,646,-843,-97,1000,-1000,969,1000,-624,-930,591,1000,1000,-868,-583,751,912,-1000,491,231,475,1000,-83,827,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{-1000,1000,-696,672,409,697,-857,168,-446,-1000,-404,400,-584,114,-911,-855,-135,1000,794,1000,-859,-525,196,-263,400,273,815,-448,-1000,-1000,1000,891,417,848,385,-820,471,-493,-1000,1000,-1000,1000,298,-1000,784,875,792,270,1000,496,977,-1000,-1000,532,453,-1000,21,1000,1000,1000,-1000,-659,886,-723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{111,114,-356,1000,132,-173,-1000,-168,50,1000,404,-1000,-151,-1000,-504,100,1000,1000,800,886,151,-579,375,-81,-1000,85,760,729,-158,-253,304,792,453,481,488,44,-49,-391,-1000,-69,-240,990,-652,-212,-457,-130,713,359,495,452,-423,-58,276,614,-874,1000,382,338,890,-211,-548,-37,-299,-56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{-782,-644,-121,-1000,-546,-12,-1000,-500,859,-152,-97,-36,-1000,654,488,1000,-1000,452,-400,-365,407,-1000,1000,-592,-262,636,1000,-48,-1000,-645,-1000,-5,396,443,187,279,-1000,-601,-1000,-81,-522,285,-543,-310,28,418,1000,-383,563,554,-274,-133,-576,-434,-300,-372,-637,62,551,904,289,67,-801,-242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{37,114,-166,1000,-7,-633,-724,657,-546,311,1000,846,-494,-772,-164,371,-400,938,1000,386,355,-1000,654,164,-1000,571,480,507,-132,-32,-130,119,292,-25,927,163,-366,-297,-957,-69,-162,728,-517,-110,-396,453,971,-129,166,315,-423,358,276,542,-874,449,503,338,712,-211,52,-83,-755,-56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{545,-1000,79,-482,-217,985,-352,242,537,-647,1000,-1000,1000,-518,86,727,1000,-776,113,-400,1000,-506,312,-620,892,221,-167,1000,-1000,271,-1000,-178,-1000,-888,-759,1000,458,-395,-180,-1000,-152,-526,-245,519,-1000,-574,-68,-327,-400,338,-1000,729,400,1000,-1000,-331,662,1000,-400,-1000,261,836,-881,274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("COLOR:-2009456", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{-1000,655,-126,-110,982,589,124,236,-698,737,-170,400,-173,817,-323,-446,-135,1000,319,1000,566,1000,-19,-829,400,-840,113,1000,-978,-871,900,779,-785,-179,-1000,-586,516,151,-988,1000,-1000,77,298,-428,-319,551,130,-329,1000,-30,977,-1000,-1000,-715,526,452,625,705,890,414,-1000,574,664,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{665,-796,-513,400,698,-283,-1000,-55,-56,1000,865,-1000,722,-746,-82,324,1000,1000,1000,1000,772,125,609,-357,-1000,-266,1000,1000,-586,-890,-1000,1000,134,584,49,-34,-324,-1000,-1000,-195,-312,1000,-1000,124,-1000,-480,1000,999,516,-60,-1000,-80,-399,422,-1000,1000,137,-55,1000,-1000,-1000,396,-620,-339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{1000,-1000,-456,536,-315,-573,-692,-1000,527,-540,1000,-1000,1000,-1000,-612,1000,1000,-735,222,439,1000,457,-1,-710,-1000,299,-285,1000,802,1000,-1000,514,571,-1000,145,1000,481,-5,-1000,-1000,1000,-429,-1000,1000,-1000,-1000,-418,1000,-626,-419,-1000,1000,1000,-207,-1000,279,-548,-311,-1000,-1000,1000,719,-1000,-108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{-112,-644,-121,-1000,1000,100,-628,517,-426,451,855,-96,857,654,488,-446,1000,452,1000,1000,566,-506,202,-1000,0,-812,113,1000,-978,-1000,-1000,810,-838,-231,-993,142,123,-942,-1000,306,-522,309,65,-428,-319,311,645,96,1000,324,-274,-647,-1000,519,-739,521,991,-131,1000,-765,-1000,1000,289,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{148,954,423,121,1000,968,-422,676,-277,-342,-646,-1000,-1000,605,-776,-159,-400,-863,119,11,-1000,99,564,-887,-925,-732,340,810,-1000,869,-532,743,-898,874,-23,-333,-522,-1000,-1000,-843,-1000,1000,250,1000,-364,349,-807,-439,1000,44,-1000,1000,148,-1000,184,297,141,-420,1000,-1000,-1000,776,1000,406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{505,68,-522,-47,-440,-1000,-1000,-762,1000,107,658,-484,449,-1000,-504,656,622,-314,954,-44,621,125,383,593,-1000,1000,-535,1000,640,327,-174,796,240,-420,-528,506,-327,-181,-214,-568,-422,739,-1000,541,-627,-1000,-498,419,-372,-403,-559,1000,1000,-1000,-1000,284,-566,-372,-60,-211,762,-445,-873,599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{222,-24,73,-790,518,-1000,1000,-324,518,-637,774,400,-395,-1000,1000,-377,-400,424,-82,208,370,530,1000,-140,400,-441,-1000,-210,373,869,-977,379,-401,-1000,-414,-667,642,1000,1000,424,290,26,-1000,-298,-78,-830,28,-888,92,233,-4,781,-112,-1000,837,-189,1000,215,-1000,-138,1000,654,-66,-915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("COLOR:-8355712", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkPaint():java.awt.Paint",
            new int[]{795,-773,-623,-630,-350,-152,-605,-706,-678,-1000,887,-819,1000,-591,734,-610,1000,485,718,1000,406,746,896,22,-1000,-184,-287,1000,1000,-924,545,596,1000,-799,-1000,-392,652,179,-122,-636,868,-1000,-846,713,-747,236,832,142,149,-1000,-1000,1000,502,1000,-1000,270,1000,-507,-11,-1000,259,640,872,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{608,1000,164,-195,-1000,-830,-388,400,-410,-219,-196,-1000,29,-144,1000,-1000,351,-703,1000,-1000,-466,1000,-1000,-340,-1000,-178,590,1000,1000,526,790,-1000,-599,502,737,111,824,-12,1000,567,-75,17,-505,205,-873,221,-709,-1000,129,-1000,532,496,-1000,889,1000,-91,883,873,948,-6,-169,-291,294,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{692,1000,479,-798,-1000,354,-1000,234,-831,824,-970,-886,1000,1000,1000,-905,-252,-1000,365,238,-389,1000,-209,292,-347,278,139,1000,-336,806,1000,-1000,-726,604,-157,794,436,-76,1000,499,220,236,-1000,-1000,-1000,179,-254,-619,-1000,-1000,226,1000,-1000,-226,1000,-1000,855,1000,-370,508,760,570,306,-76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{-204,-628,584,201,501,-707,-387,-243,501,246,-557,-539,-717,163,-468,-490,-562,-358,-19,123,363,577,141,-78,-425,-401,-789,1000,578,372,-167,1000,481,314,495,1000,-1000,201,540,-946,-1000,-109,999,38,1000,-385,243,600,459,1000,-624,742,-464,1000,744,585,-1000,-2,352,-900,-746,378,-1000,-345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{184,-986,553,707,865,758,-415,520,-47,143,207,564,-471,968,203,956,-940,207,-546,666,425,-351,-84,-895,-95,-239,-49,-768,155,334,-811,-136,353,-288,102,-697,32,445,-206,549,-114,52,101,411,818,-745,58,618,865,516,-2,710,271,-992,-850,-112,-56,249,428,293,-188,-455,-691,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{-300,-1000,-167,-44,908,-368,1000,268,815,-259,740,614,-491,-1000,-678,-207,-259,-474,-87,-1000,-1000,-1000,-911,223,-800,-575,509,-516,351,-659,-500,-276,-81,-541,657,555,627,-1000,226,638,21,62,1000,59,1000,1000,-951,456,922,36,1000,-143,-262,987,-7,1000,-849,-1000,402,235,36,29,265,-84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{1000,934,373,34,-6,-557,272,461,-517,-188,-233,-365,-1000,123,-72,-237,348,-230,884,-1000,709,149,-811,-340,-1000,-1000,294,-96,1000,1000,51,-1000,62,1000,819,248,-884,487,305,-182,-637,-785,867,1000,-475,83,-245,933,95,-467,-457,997,-478,991,-217,-681,-632,809,688,-221,-689,-276,-1000,-990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{-904,-341,1000,22,-32,-1000,809,224,201,736,1000,-10,64,-1000,543,-1000,-283,-757,-1000,-54,468,-402,-286,-838,959,-104,-1000,-983,394,258,-436,679,1000,565,379,-471,-1000,1000,-706,-1000,-331,-326,1000,-553,-300,153,-913,316,117,372,-821,844,105,1000,-270,415,-976,-1000,1000,-1000,400,-671,-167,-841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{-400,352,193,-185,-994,-83,582,-90,295,-334,-478,-694,-44,-208,-610,-1000,-1000,-280,-4,-158,-50,488,-344,-162,-390,66,-109,950,441,-51,855,400,-88,-148,278,400,-229,-642,1000,-112,-981,-342,703,-400,-214,352,-301,217,343,-187,146,-218,-1000,-557,678,-162,-1000,-16,544,-751,-106,-107,-530,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{987,1000,417,779,842,494,-394,679,-48,-161,44,478,-628,460,775,1000,-928,294,680,667,686,-13,-667,-802,-730,-351,-49,-706,400,587,-1000,-611,326,-970,406,-840,102,-3,-68,549,81,-41,101,556,700,-1000,-1000,569,464,759,98,787,-659,-404,-1000,-164,-68,728,-37,516,-308,-438,433,-600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{-398,-1000,-1000,235,-323,-1000,812,669,444,-278,-902,985,-1000,-998,897,-1000,-1000,-1000,790,-1000,-656,-1000,-1000,-469,-759,-1000,384,-233,669,-386,1000,-476,1000,220,1000,141,290,746,548,299,246,-446,534,617,986,454,-1000,-266,1000,-1000,665,541,-853,1000,-657,66,-991,-965,-155,-378,-363,-306,288,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{1000,1000,164,-195,-658,-400,52,984,-272,-319,-1000,-725,-76,-144,-344,-854,454,-631,1000,-1000,1000,-640,-436,-366,-852,-724,663,700,967,1000,877,-1000,-599,400,1000,-167,824,41,893,-400,-469,-1000,410,1000,-831,-733,-709,819,-268,-155,-741,496,-872,-74,-353,-933,145,1000,-252,57,-400,-767,360,-609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "getTickMarkStroke():java.awt.Stroke",
            new int[]{-1000,162,-847,-288,544,1000,272,227,-1000,479,496,1000,1000,-205,-216,-1000,-1000,-598,-973,-1000,-1000,-719,65,1000,168,-466,1000,997,-1000,-724,1000,433,-529,-1000,345,1000,1000,487,847,1000,218,-57,-550,-1000,1000,1000,-1000,1000,271,208,1000,-133,-1000,-111,1000,-128,432,-591,-518,1000,-1000,388,1000,-909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{-1000,-1000,-211,-1000,-705,1000,35,-717,-1000,-1000,-1000,-1000,1000,1000,230,385,-283,-1000,-393,-1000,1000,1000,1000,1000,825,-406,446,-656,-1000,-1000,-1000,-1000,-1000,-283,1000,-1000,1000,-427,113,-1000,1000,315,1000,354,-1000,-1000,419,-19,-1000,1000,1000,1000,-1000,-713,-1000,100,-1000,-1000,319,-1000,1000,-358,217,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{952,440,-296,989,381,-961,559,541,864,838,-1000,-995,-1000,-801,-394,820,909,297,-344,151,-459,692,-476,-1000,-1000,21,-201,715,1000,-385,-615,264,900,-262,-1000,-193,521,565,272,-20,337,-281,94,669,-720,648,332,346,924,-478,184,-785,1000,-280,-461,336,1000,843,817,907,-738,383,-842,-624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{-768,1000,333,804,-537,-118,790,573,512,276,737,264,-717,-1000,272,-337,857,864,171,622,-1000,-515,-831,-291,636,-672,-765,-355,675,507,-181,793,-16,657,-367,-563,-560,308,-1000,535,-204,-580,-649,-513,602,719,-288,-751,449,-888,-453,47,895,-982,508,1000,-363,-177,-614,431,-908,-325,382,-549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{-89,282,389,-938,-1000,-961,-1000,-769,667,706,624,-606,-1000,418,1000,820,415,1000,833,136,1000,-1000,1000,-770,-1000,21,-1000,1000,-709,-1000,-43,1000,-342,-1000,585,-1000,571,-86,272,1000,-919,1000,394,-1000,1000,238,1000,1000,1000,1000,-1000,-839,-259,926,466,336,-66,1000,96,895,-1000,-1000,1000,558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{369,802,-202,-244,-392,-118,198,-241,735,1000,-529,-786,465,792,-373,1000,1000,889,578,-311,693,-524,975,-1000,-1000,1000,-586,1000,425,-889,945,1000,755,-738,-265,-858,-774,799,872,1000,230,-118,-201,103,-519,822,1000,1000,1000,1000,-200,-12,-86,-368,-1000,447,-438,1000,551,1000,-1000,-17,1000,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{-1000,-1000,328,-1000,-1000,1000,493,-592,-1000,-1000,383,-674,871,467,1000,1000,-283,12,-393,-1000,1000,1000,238,1000,-237,278,-102,-664,-1000,-234,-811,-1000,-1000,-262,1000,-320,1000,-427,-812,390,404,169,1000,-1000,65,-1000,-417,525,-1000,501,-84,1000,-977,380,-552,117,-1000,-1000,319,-1000,1000,144,217,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{-599,122,898,195,-386,-701,646,-392,345,-489,-657,-431,377,922,33,191,-705,297,20,708,-459,999,184,-897,773,21,703,194,-204,-571,-615,-528,-549,768,641,-933,-193,422,-288,540,768,-281,190,945,746,-367,-904,-473,-506,370,826,793,316,797,-875,-725,-40,-269,903,237,-753,428,-780,848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{-1000,683,1000,1000,-537,-118,624,1000,-1000,-1000,839,264,-757,-1000,1000,-657,127,900,-842,1000,-659,-178,-418,564,1000,134,-1000,-801,437,-238,-1000,-20,-711,284,-195,-92,-560,309,-1000,-248,340,-676,-400,-498,1000,-221,-1000,-840,-789,364,-836,432,1000,1000,1000,1000,-171,-1000,-278,-113,-120,153,-1000,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{769,477,560,-95,-668,-756,-909,51,895,188,-533,-707,-505,-123,491,275,830,-284,-90,-1000,-1000,111,-1000,-350,64,-189,166,1000,297,-426,956,365,843,-136,363,-560,350,-666,-267,310,-30,512,886,1000,239,705,438,-1000,808,-34,-268,464,114,-1000,-1000,1000,-555,924,315,-688,-302,550,348,741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{-1000,-1000,159,-782,-619,55,91,-445,-1000,838,-1000,-297,1000,833,332,641,-626,-1000,-1000,-541,-70,1000,1000,996,943,-1000,1000,-775,-338,-1000,-615,-1000,900,-262,1000,-1000,1000,515,-553,-1000,1000,-242,292,1000,-912,-580,-523,346,-1000,1000,1000,1000,-759,-1000,-461,-313,-1000,-1000,8,-1000,973,-593,-30,-624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{201,-254,-606,-400,-578,1000,921,116,20,135,39,-530,-886,439,258,285,590,228,737,-623,1000,-575,-535,253,74,325,-848,-708,-961,264,-1000,259,-1000,-472,421,-446,537,-558,-49,697,-2,-64,852,-1000,-280,-1000,575,1000,324,209,71,7,-308,-571,4,744,-522,8,584,451,439,43,314,344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{-320,-572,-106,540,-705,-190,-77,530,74,-3,-1000,-1000,556,1000,-488,473,119,-430,-1000,-1000,540,790,1000,-632,-1000,-33,446,654,-301,-1000,-1000,-650,272,-136,-260,-1000,724,-427,169,-371,1000,-339,526,820,-874,-575,419,-1000,-302,1000,759,-692,580,-19,-1000,575,544,1000,1000,936,22,-377,217,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "hasListener(java.util.EventListener):boolean",
            new int[]{1000,44,-756,-826,-488,-508,-47,-471,1000,1000,-93,-805,-983,1000,165,163,806,-87,1000,162,-16,-562,651,-636,-1000,905,318,1000,-1000,-659,-173,697,-289,-1000,-265,-1000,-658,343,1000,1000,-1000,685,920,-1000,-754,351,584,1000,1000,1000,-499,-40,-670,-459,-723,709,-87,1000,1000,240,-96,-293,82,916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isAxisLineVisible():boolean",
            new int[]{-1000,977,-730,690,1000,-177,-946,441,-1000,-107,-658,-979,-488,634,-19,673,-1000,1000,-574,-489,330,-734,116,562,310,-254,-283,1000,868,-433,-255,-931,1000,-745,-880,-848,247,523,582,-245,448,-1000,-707,46,684,-1000,400,-331,886,-660,-276,178,-817,501,-851,-1000,-19,1000,-260,356,742,-326,-267,252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isAxisLineVisible():boolean",
            new int[]{-1000,599,868,878,618,839,279,-1000,791,4,102,-979,-433,332,1000,673,-1000,-1000,1000,-489,-252,982,-1000,-964,-976,-1000,1000,742,-402,603,-255,-982,695,1000,-172,-848,855,523,801,419,448,-642,425,-311,684,849,-1000,-668,-1000,736,1000,-378,772,-759,-78,829,843,-394,402,-285,-567,1000,-267,-93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isAxisLineVisible():boolean",
            new int[]{-1000,89,951,1000,1000,29,22,240,797,218,-392,-1000,-433,250,788,819,-1000,274,605,-1000,-95,227,-885,-576,91,-823,294,1000,303,409,84,-140,1000,251,-607,-265,795,966,-348,45,171,-1000,1000,-1,-106,-204,-656,489,-886,199,1000,380,185,-539,-349,1000,644,-115,1000,221,225,593,-574,-100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isAxisLineVisible():boolean",
            new int[]{-387,831,-1000,-516,-628,309,-736,-561,-199,-427,-803,681,-1000,992,908,1000,669,-58,123,276,-598,551,667,-493,1000,-437,-848,338,77,-1000,1000,-872,528,1000,-83,312,455,-443,610,1000,1000,-1000,181,-735,-1000,1000,173,725,32,614,1000,-210,-447,-277,1000,544,-517,-236,-1000,926,-297,1000,95,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isAxisLineVisible():boolean",
            new int[]{-970,-72,1000,1000,-161,-676,-668,375,672,-153,-1000,-489,362,830,1000,1000,-435,325,1000,762,-542,756,-1000,1000,954,-1000,583,852,403,584,942,-937,1000,140,-552,-915,706,7,-336,-793,935,202,-180,-139,-102,1000,-1000,796,-997,513,126,769,-986,-57,799,1000,-966,143,286,967,-105,317,310,-600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isAxisLineVisible():boolean",
            new int[]{672,298,444,-396,1000,-1000,1000,554,163,-57,1000,-1000,1000,792,-1000,-707,367,-333,-481,-1000,440,-502,551,-1000,-711,1000,-698,-64,569,55,313,1000,-430,-43,-650,1000,-589,-548,-163,-1000,-782,-366,553,-215,-641,-784,967,-662,-456,-978,-405,-1000,-396,-329,-25,135,663,1000,890,-623,583,-1000,1000,-602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isAxisLineVisible():boolean",
            new int[]{1000,-1000,-626,-1000,178,-1000,-309,-944,-681,955,113,-206,-19,-1000,-751,986,1000,-838,-539,822,-517,381,640,-1000,665,-56,-1000,-156,211,-257,1000,1000,-626,607,-312,-270,-664,-1000,368,1000,-30,1000,529,215,-8,849,-460,-396,-112,-188,-1000,-1000,379,-776,-480,-459,-432,85,-769,1000,-914,-314,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isAxisLineVisible():boolean",
            new int[]{1000,-451,-1000,-808,709,-519,203,37,-1000,119,311,366,-801,-661,-1000,1,820,434,-1000,-443,775,-632,950,-795,274,1000,-1000,366,817,-769,1000,1000,-65,14,40,561,-298,-521,756,727,-714,77,-320,-249,-189,-204,660,-746,1000,-321,-816,-1000,406,90,-696,-421,258,-156,333,280,-579,-718,1000,-548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isAxisLineVisible():boolean",
            new int[]{-780,-163,-415,728,366,119,-436,-628,-399,591,15,-940,-830,86,597,800,-358,-492,351,-81,36,301,-3,-249,-340,-717,94,742,86,-223,386,-1000,695,775,-182,-254,680,-297,1000,713,263,-352,-688,-23,58,820,-413,-808,148,264,961,-554,483,-540,80,807,212,-71,-385,44,-526,772,242,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isAxisLineVisible():boolean",
            new int[]{378,-565,-203,-869,-1000,309,-104,-1000,464,-719,-923,991,-1000,363,922,673,1000,-873,58,149,-1000,1000,-33,-1000,-976,-781,-814,-520,-857,-637,926,-16,695,1000,638,-848,515,-610,202,419,1000,-642,1000,-311,-404,1000,-1000,484,-633,924,1000,-277,772,-49,1000,829,198,-934,-985,373,-567,1000,426,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickLabelsVisible():boolean",
            new int[]{53,-232,244,1000,-900,-1000,161,279,-552,-610,-54,137,-444,1000,634,-1000,-724,325,266,-51,848,-1000,727,-527,699,-810,1000,-290,-1000,1000,349,898,1000,-253,1000,1000,-834,422,308,-1000,-932,-705,-1000,-890,-934,514,1000,34,-1000,-645,1000,999,-1000,933,-1000,227,-1000,1000,-1000,-756,1000,1000,-814,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickLabelsVisible():boolean",
            new int[]{-220,450,-691,-1000,-666,354,-879,-724,-295,-692,747,-166,329,498,-1000,-506,842,400,-548,384,-704,0,319,101,796,-708,727,-126,-1000,561,-191,74,-1000,-857,938,-404,-1,-1000,744,-162,-1000,-159,-977,-116,15,450,746,241,-961,820,1000,1000,-464,1000,-1000,-257,-628,-537,-807,-396,1000,882,-447,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickLabelsVisible():boolean",
            new int[]{-261,-713,-1000,-1000,-209,787,-693,-912,-1000,912,1000,597,244,42,-1000,-227,1000,-564,-444,1000,-562,170,355,117,-806,-760,-9,-393,-1000,344,844,1000,1000,-241,-617,-668,313,-1000,1000,517,-1000,423,-1000,-1000,69,-1000,88,324,-507,-277,-426,-378,-12,1000,-830,-1000,-287,-902,-1000,-327,-498,-316,-996,503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickLabelsVisible():boolean",
            new int[]{252,-1000,-676,-450,-252,-79,297,-651,-1000,1000,876,-595,-444,325,-896,293,581,-693,-422,584,-643,-936,291,134,-902,-31,430,-260,173,-400,358,386,205,1000,-737,-987,848,-1000,869,972,-940,355,-831,-1000,649,-1000,88,1000,190,-1000,-1000,-378,148,1000,78,-771,152,-261,-262,140,-739,-1000,-1000,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickLabelsVisible():boolean",
            new int[]{285,-1000,-471,1000,-1000,-1000,976,226,-247,1000,-610,-171,-1000,635,400,-181,-105,-514,-1000,243,-400,-1000,1000,481,-1000,880,1000,-74,-186,-497,-405,-1000,608,1000,449,-455,1000,-514,975,1000,-337,-393,-554,-354,-38,274,952,1000,103,-1000,-1000,-78,-348,118,-503,-537,-883,985,-701,728,-1000,-229,-385,-499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickLabelsVisible():boolean",
            new int[]{1000,-400,413,872,-679,-835,1000,279,-539,-288,240,438,-587,861,-307,-944,-927,-363,543,673,654,-302,-23,-403,563,393,1000,-169,-777,735,380,1000,1000,1000,243,1000,-621,422,957,-903,-153,-166,60,-283,-707,1000,290,-41,-1000,-857,-789,9,-1000,90,-437,-644,-914,1000,-499,-707,904,911,-1000,297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickLabelsVisible():boolean",
            new int[]{-587,334,-431,206,-269,-500,-371,-618,626,-482,1000,296,-961,711,-973,213,-634,307,-541,-498,218,1000,-383,-411,325,-233,1000,528,-672,492,265,1000,1000,872,340,-54,254,-720,103,-1000,-1000,-827,-1000,-91,-1000,37,-484,427,-1000,-262,-30,-532,-668,799,-94,-74,-41,-384,-185,-76,673,161,-145,433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickLabelsVisible():boolean",
            new int[]{442,-1000,125,1000,-802,-411,-268,972,-917,946,987,243,12,576,-1000,-573,-309,110,275,737,-878,-1000,87,-387,-1000,-337,881,-947,834,1000,749,895,1000,1000,-878,-102,-335,-294,-1000,-229,-777,842,-764,-1000,44,-346,-173,348,-614,-313,32,-1000,-668,939,-684,-1000,17,386,-824,-528,656,-851,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickLabelsVisible():boolean",
            new int[]{710,-510,1000,567,600,-1000,956,825,-71,-1000,1000,-695,-791,340,-688,-809,351,339,1000,-432,573,678,252,-605,-1000,-112,387,209,1000,537,443,623,543,382,115,982,-3,1000,-1000,-1000,-489,566,-939,-517,-827,-320,-797,42,303,-378,-925,-556,351,75,604,-73,46,-226,748,135,-378,-1000,647,703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickLabelsVisible():boolean",
            new int[]{1000,496,894,293,255,-469,727,993,20,-665,546,582,-1000,-1000,-1000,95,842,-950,-548,151,1000,1000,-1000,-1000,796,384,-222,-371,428,485,1000,1000,1000,658,-1000,1000,-793,826,1000,-1000,-1000,845,-143,-98,-943,1000,-1000,241,-995,-1000,-1000,-1000,-504,-232,294,-877,911,179,-807,-396,-559,-156,-151,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickLabelsVisible():boolean",
            new int[]{971,1000,1000,-614,-1000,-1000,233,1000,1000,-1000,-1000,1000,-267,-488,-40,1000,-1000,453,1000,311,1000,1000,-1000,-1000,400,-63,497,-1000,312,-497,545,-595,-91,931,-292,1000,-234,326,318,497,786,910,-728,870,-1000,1000,-1000,-880,-776,438,730,-1000,-384,-832,-601,-683,457,-1000,1000,-630,-757,1000,-75,23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{521,-1000,289,900,708,-79,-1000,-602,847,-1000,1000,400,601,-89,590,-1000,340,480,15,101,453,-363,981,232,-1000,34,-159,537,1000,-510,-667,-302,-687,-224,-795,834,1000,841,29,-666,-1,-201,-533,-197,1000,1000,772,966,1000,975,1000,148,-355,-284,-848,1000,710,88,-1000,-1000,-366,-1000,1000,-476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{1000,-1000,159,-361,720,-79,-904,-879,-176,-806,-707,-327,-295,-396,12,-603,-225,160,-182,469,587,762,981,498,-326,-205,-159,-124,-20,419,509,-892,-678,234,-608,349,1000,478,295,514,13,-585,140,-168,1000,812,450,-1000,1000,-1000,228,148,-153,-929,-451,788,-414,-196,-1000,-1000,-188,-1000,-975,-812}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{1000,-82,834,682,-1000,85,96,1000,-24,-432,4,199,179,391,254,842,634,-402,-859,-750,-811,-1000,931,-938,1000,117,-336,335,-1000,-851,-1000,200,-827,957,-18,-167,1000,1000,-1000,-356,-591,-1000,327,-83,774,558,359,-338,614,-445,1000,754,20,633,-540,1000,369,-38,-181,-531,33,-583,-716,617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{400,-695,884,213,-193,-982,-849,425,-449,-264,-619,-161,378,574,275,328,744,-211,-901,-176,-868,-1000,627,-261,156,-281,-569,805,-201,636,-1000,-133,-432,920,-178,-1000,780,921,-255,728,-698,-812,290,186,339,439,551,63,445,37,-146,496,-274,339,-78,555,154,-327,105,-71,-277,-611,-30,428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{424,-1000,763,241,-639,-1000,128,995,-409,1000,-402,634,121,480,334,1000,477,487,-1000,-241,-837,-1000,1000,-309,574,-795,573,755,-1000,-1000,-1000,898,340,-1000,517,-423,1000,1000,-1000,753,-1000,-858,1000,881,-1000,221,1000,167,-326,-338,272,34,-653,532,-367,1000,-573,-819,760,-314,-89,-404,177,665}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{932,-864,289,-220,708,1000,1000,-1000,-221,-718,828,1000,-1000,-1000,667,-704,340,-324,-1000,432,1000,1000,1000,1000,-1000,-534,-159,818,1000,-380,-442,912,-189,-1000,-416,1000,1000,-1000,1000,1000,-549,-201,-783,966,-974,-1000,-702,152,-1000,-136,-1000,-1000,-192,-1000,1000,-1000,710,1000,233,1000,-47,-1000,1000,-970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{-558,-1000,703,-90,113,-880,-344,-705,-876,-356,-1000,-803,-377,864,-189,260,-173,560,-1000,98,-1000,214,512,-100,1000,-527,960,787,-1000,3,446,444,157,1000,370,420,711,1000,378,-789,1000,-638,213,274,-97,933,1000,65,1000,-732,1000,-287,-1000,56,-599,1000,71,-498,-427,-638,-488,-692,-1000,-646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{1000,-1000,3,317,856,52,-1000,-320,77,-206,-511,388,-295,-804,375,75,-575,-39,-281,630,915,-1000,1000,65,35,-1000,464,-124,265,-35,544,-274,-30,804,-1000,840,1000,-1000,-139,1000,-950,-993,1000,1000,1000,1000,1000,-857,-375,-1000,481,333,-198,-801,-976,566,-1000,676,-1000,-1000,130,-1000,-347,-751}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{597,-1000,293,-30,-148,-732,317,82,-417,603,-657,1000,-561,-241,454,1000,-285,451,-754,951,-286,-36,516,622,639,-1000,793,908,-187,280,-477,338,68,-1000,-69,-89,468,257,-507,1000,-590,223,850,1000,-1000,-323,1000,-18,-1000,-688,691,-714,-239,-161,303,1000,-946,138,232,-520,-554,525,785,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{424,-59,1000,-1000,-639,555,93,-3,-549,-838,183,-1000,-406,608,484,614,1000,1000,-883,477,-1000,-1000,577,100,-1000,-731,334,1000,-968,-64,-925,-1000,-1000,-1000,-498,-978,1000,1000,-1000,159,-1000,-1000,17,-711,400,818,322,-920,1000,-751,149,-175,286,905,592,890,-827,1000,-258,-634,-723,643,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{-1000,-487,1000,-397,-1000,91,-611,197,-327,1000,-257,-828,1000,1000,139,563,1000,1000,-529,-961,-1000,-1000,-42,388,-637,-177,-958,939,-400,-218,-1000,225,-223,-313,822,-1000,1000,1000,34,-1000,-758,-817,-356,-398,-1000,653,777,1000,1000,1000,636,-374,-1000,1000,983,-542,173,-1000,1000,1000,-707,-1000,-686,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{1000,-914,134,1000,1000,-1000,-1000,-550,1000,-1000,438,400,601,-604,259,-1000,-116,59,151,-353,1000,479,1000,-283,276,-177,778,-285,-40,-1000,-439,-227,-1000,-355,-400,1000,1000,1000,-354,-1000,346,82,-434,-573,1000,1000,772,615,1000,-208,1000,-374,242,-666,-1000,1000,1000,88,-1000,-1000,-286,-1000,485,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{-1000,-668,844,507,720,-982,-849,-809,-615,402,-1000,145,378,834,294,-576,892,506,-901,147,-471,-949,-58,1000,-551,-1000,-1000,1000,-62,636,-671,-483,263,715,-299,-1000,39,-109,1000,728,-820,-154,-117,498,-677,442,1000,1000,152,1000,-146,-189,-967,73,1000,-576,-566,-1000,274,126,-845,-611,-148,428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isTickMarksVisible():boolean",
            new int[]{1000,-42,578,-309,-1000,-1000,-161,-1000,1000,-1000,-1000,-842,-160,-140,-33,842,-121,-58,-859,-701,884,-902,762,116,1000,-1000,554,-929,-1000,-1000,-651,-1000,-1000,1000,1000,-400,1000,257,-223,-1000,499,879,-972,1000,1000,-543,1000,499,1000,-759,-1000,-1000,1000,-1000,-530,1000,369,-1000,-181,-1000,-1000,160,-716,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{-1000,-1000,184,-616,-1000,-395,-315,-637,320,-504,-1000,-902,-235,-1000,-143,539,-62,365,1000,-1000,-1000,1000,-347,-504,166,218,632,-1000,1000,852,900,-49,307,1000,-784,-554,-262,300,908,183,1000,1000,277,-497,-1000,797,1000,304,-648,-850,1000,-1000,-526,-372,-1000,445,196,-1000,-1000,-541,-883,-1000,1000,902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00334() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{-1000,-842,629,-354,-48,-836,1000,-598,345,1000,311,281,-889,372,-1000,1000,-10,-326,-400,-362,1,679,459,-505,-68,-497,379,-427,-181,263,-25,407,-486,151,364,719,-229,-1000,-516,2,21,81,1000,472,557,-211,-324,337,-627,16,30,-920,100,-58,-1000,231,225,-685,400,-229,-485,400,862,-606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00335() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{-443,247,630,-451,-3,637,279,-434,-502,-725,-442,-812,578,-902,-161,618,-328,986,837,-434,-508,333,-492,207,-320,422,517,-784,926,38,901,-131,706,107,-63,296,-914,-609,-48,-219,687,241,820,-320,-199,250,252,-976,-64,286,-384,-852,579,-189,983,788,875,684,-596,-978,63,687,968,144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00336() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{-680,-512,54,519,766,643,221,-516,-476,-727,-399,-63,277,-574,551,511,-712,-967,-858,-465,608,-73,612,-711,915,-485,-949,995,-229,178,-467,198,-918,-181,-635,-793,-165,194,-217,-861,-186,673,491,760,950,-116,754,809,-849,351,416,772,-915,-338,175,181,-812,-27,718,425,405,-264,914,-203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00337() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{617,265,1000,255,257,812,-548,322,73,-24,-377,718,-644,1000,421,1000,-1000,-1000,1000,-141,1000,-1000,793,-148,-310,-1000,-638,-260,-212,-1000,483,-345,-973,525,-117,-1000,453,1000,-581,-1000,-1000,-57,571,453,221,984,260,-1000,-806,600,226,879,-350,-1000,1000,110,-1000,-1000,1000,-930,523,19,727,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00338() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{-941,-969,444,-1000,-1000,119,473,-1000,491,7,-1000,-974,-1000,-814,-214,1000,-637,-238,993,-988,-1000,966,-918,-1000,-217,-356,68,-654,1000,651,847,542,847,925,-1000,245,862,-1000,1000,-1000,514,797,1000,-466,-448,1000,1000,652,-1000,-1000,831,-935,-485,-1000,-994,910,-99,-1000,-1000,-556,236,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00339() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{441,140,149,-417,778,453,147,-746,140,-504,105,-396,914,-65,-116,257,135,-75,-82,711,-38,-238,-60,-650,-333,-317,29,275,-170,-619,-67,-81,260,-188,651,472,78,-259,134,183,-3,398,982,106,1000,-600,1000,-232,111,-850,-1000,-667,531,153,479,-107,845,416,345,878,566,626,174,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00340() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{-191,-590,-51,-877,153,-381,942,70,273,220,211,-380,-1000,178,81,11,191,-877,-94,541,519,-65,156,-323,-220,-1000,288,698,400,202,-241,-712,-332,307,71,93,1000,-66,-390,-239,-934,282,-199,-730,257,-444,581,-672,314,-214,130,-523,-919,291,80,-128,-334,-179,521,-132,1000,-181,-108,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00341() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{-1000,136,-86,1000,-806,-668,-146,944,-911,-1000,-244,351,-391,-559,754,483,45,623,312,-524,-1000,588,-385,1000,1000,-189,-1000,-1000,428,1000,1000,-887,495,1000,-438,-546,324,769,-464,1000,-309,1000,200,-276,-1000,1000,64,-33,1000,1000,710,-868,-763,1000,-992,601,-1000,-685,32,-1000,-660,400,-438,-606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00342() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{1000,960,389,-308,576,1000,-659,-496,-791,651,230,1000,-917,-230,-190,-775,-50,-348,-959,621,229,-1000,405,686,-286,-1000,-20,1000,-50,-1000,-273,-379,-1000,341,110,-84,-183,997,-935,-287,-731,219,190,-796,-897,-262,-860,44,-365,465,-457,400,523,523,1000,-525,-1000,-306,1000,-387,1000,77,249,-941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00343() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{-176,-543,-424,264,82,-160,-483,-202,794,-305,-334,-371,972,-689,467,35,339,-227,93,-68,641,1000,-196,-2,-535,-154,-1000,-82,-692,-682,-376,-84,-63,154,-798,188,177,62,20,-284,-47,1000,128,-465,559,280,415,-242,23,808,41,-1000,95,-187,-204,219,596,170,-831,-454,-233,-608,183,977}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00344() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "isVisible():boolean",
            new int[]{-54,-204,693,-717,312,977,552,-1000,-238,-109,-381,-184,-44,-192,-401,618,176,1000,237,-184,165,-242,584,-953,-442,-403,1000,120,103,-11,-507,-48,-422,245,330,8,10,-518,-129,-228,526,457,799,90,376,-84,99,-113,-1000,1000,226,-397,514,-202,-164,457,435,-285,14,-859,1000,313,-105,278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00345() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-1000,-1000,-156,-338,1000,-1000,-1000,-854,1000,392,-277,471,-1000,-1000,150,474,-904,1000,966,-1000,-1000,1000,-979,-732,568,-1000,693,1000,-1000,-1000,-255,-1000,-240,-304,695,173,-1000,98,-911,13,159,1000,215,-1000,-1000,-990,328,86,-1000,1000,-1000,1000,-700,849,-428,-882,-39,1000,1000,635,673,-1000,282,-64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00346() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-1000,241,-455,-1000,1000,-1000,-96,291,-863,585,-54,-58,-1000,-1000,1000,405,273,1000,463,-1000,-104,-902,-156,1000,1000,-1000,1000,1000,-975,-1000,-110,-79,371,869,476,-676,-531,1000,1000,1000,955,1000,-1000,-1000,-418,-1000,217,1000,824,574,-751,542,1000,-947,-1000,1000,-1000,343,1000,1000,686,-841,-126,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00347() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-822,451,183,-846,331,-706,120,594,232,876,-86,-338,-229,602,267,-112,-702,-90,-289,925,951,-440,387,661,85,271,-541,-674,297,670,-833,-928,438,498,-690,-368,96,-186,-32,-361,526,530,-321,-101,793,-967,923,-466,-65,-114,810,260,-597,624,126,290,-280,528,560,-680,-673,-77,-190,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00348() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{860,1000,964,296,1000,158,884,1000,919,1000,-138,374,-710,-1000,416,-418,-114,274,-165,-813,-1000,283,1000,286,-817,-1000,-847,723,-40,-712,-1000,-963,58,619,-1000,863,761,466,721,-1000,344,563,1000,-1000,-318,-464,-112,464,9,99,298,1000,-640,-901,-1000,678,-785,794,-13,127,-186,-773,-218,-474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00349() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-572,-552,39,-54,-1000,-1000,-1000,1000,-842,-1000,-814,-751,-199,587,266,-388,125,217,843,-592,612,-649,89,-301,916,1000,1000,-820,1000,641,-165,303,-415,-1000,1000,400,-1000,1000,-332,1000,-555,-566,-1000,159,880,1000,1000,-602,11,-1000,-492,-1000,891,1000,1000,-395,-111,-895,-1000,369,374,643,999,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00350() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-891,-800,378,187,435,697,301,15,486,-1000,-161,-309,-353,16,-126,1000,-795,416,640,380,-449,-868,494,-1000,-585,929,-101,-330,-340,695,1000,-876,876,-658,520,-574,-940,1000,-558,-122,733,1000,374,470,-732,1000,1000,287,367,53,461,1000,774,283,-338,-281,-1000,695,-804,-942,1000,-1000,-741,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00351() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-844,-151,-377,51,270,864,40,-503,-479,-713,548,37,-271,-294,257,651,390,-153,756,1000,6,-1000,-690,14,1000,-71,376,-561,848,540,993,177,877,-664,269,-855,-914,701,-305,1000,787,622,149,620,-617,979,1000,529,683,-488,71,-109,20,-429,650,824,192,91,-719,-1000,1000,-239,-734,525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00352() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-1000,198,448,570,1000,-1000,-1000,-474,317,1000,72,-92,-1000,-1000,740,577,-928,91,1000,-1000,14,498,-359,114,227,-745,-217,1000,-1000,-1000,-274,-1000,-8,880,263,672,-15,553,129,-680,552,1000,1000,-1000,-1000,-1000,84,892,293,1000,-553,1000,-79,-482,-1000,292,-32,1000,1000,575,-59,-1000,-338,180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00353() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-1000,-860,604,187,-47,-836,-90,-454,-143,-1000,-113,-416,611,1000,-1000,1000,-298,-409,402,1000,-561,-1000,-357,-472,-696,929,-930,-191,-464,796,821,-730,606,-1000,789,-1000,-110,1000,-914,-826,-210,-501,374,1000,-1000,1000,1000,161,1000,53,1000,589,1000,434,-2,-615,28,780,-804,-676,-428,-48,-357,125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00354() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-1000,-1000,-373,804,-99,-998,-137,-767,-1000,-1000,23,-474,719,1000,-1000,1000,277,-605,711,1000,521,260,-1000,-208,38,1000,-563,-191,-1000,1000,1000,-430,1000,-950,1000,-1000,-779,740,-997,-519,64,-694,278,1000,-1000,1000,1000,490,1000,1000,1000,202,1000,873,9,-497,-180,420,-1000,-1000,-225,227,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00355() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-1000,-1000,-612,407,1000,-1000,-151,-794,215,392,589,1000,-1000,-711,924,890,-363,960,1000,-277,230,-400,-776,-72,559,-1000,568,1000,-709,-724,1000,-1000,1000,-304,1000,-54,-1000,-214,-111,164,1000,1000,948,-1000,-1000,-695,392,1000,-1000,490,-1000,1000,-990,306,-637,-149,-1000,1000,1000,374,1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00356() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{676,973,837,22,485,691,984,942,-131,725,-89,-54,-659,-619,379,-418,162,-139,-275,-813,-675,283,356,675,155,-410,-299,678,658,-48,262,-851,-387,227,-815,-337,540,-160,766,-397,138,48,748,-820,561,-32,-112,801,9,-17,298,685,-529,-833,-634,846,-785,248,670,-476,611,-743,-746,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00357() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-1000,-1000,438,102,1000,-1000,-1000,-1000,1000,-486,-624,1000,-1000,-1000,-1000,1000,-1000,1000,85,-1000,-1000,1000,-1000,-1000,75,-1000,292,274,-1000,-1000,-1000,-1000,-537,-254,263,-1000,-987,368,-1000,-938,-501,1000,161,-1000,-316,-1000,530,-31,-849,1000,-1000,1000,-114,805,-382,-1000,-32,1000,1000,-4,985,-1000,498,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00358() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-891,-1000,-6,1000,983,1000,301,-1000,-62,-1000,359,-238,639,1000,-126,1000,-95,-171,1000,549,596,-1000,190,-633,566,1000,-1000,-1000,-425,1000,1000,-703,-58,-658,520,-1000,-240,950,-558,-1000,371,107,1000,-1000,-1000,895,1000,345,473,1000,461,1000,774,195,-1000,-802,389,1000,-1000,-941,-261,-1000,-286,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00359() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "refreshTicks(java.awt.Graphics2D,org.jfree.chart.axis.AxisState,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge):java.util.List",
            new int[]{-141,1000,600,-517,-1000,-1000,-967,1000,-1000,1000,-1000,-1000,890,1000,416,-436,770,-435,-210,0,1000,283,457,-306,666,1000,958,-1000,1000,1000,-622,784,-706,-1000,869,-194,-532,1000,-260,953,-1000,-1000,-1000,1000,1000,1000,1000,-1000,1000,-1000,908,1000,-640,1000,1000,-477,616,-1000,-1000,-515,268,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00360() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "removeChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{301,898,-91,-400,1000,-602,-1000,-849,-394,932,-584,-542,913,-852,479,-411,-315,402,-1000,772,504,-372,47,-831,350,92,-293,-1000,115,-991,1000,753,-31,288,-531,1000,-788,-839,517,-661,-342,730,-1000,270,-445,-339,-1000,-592,-861,400,367,-108,1000,1000,890,375,-97,-182,-341,100,-1000,-1000,-222,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00361() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "removeChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{679,-162,69,-425,-262,267,-503,-535,-563,272,702,-712,-75,-650,-439,-183,1000,31,-979,-647,-573,-951,1000,1000,248,-1000,548,-257,-408,1000,422,942,-788,-250,-136,1000,696,706,-955,81,-133,407,338,-629,353,-1000,625,618,-684,583,-1000,84,-1000,-1000,1000,-249,775,-87,1000,87,-248,1000,-61,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00362() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "removeChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{305,996,459,596,-132,-347,253,786,895,-60,-1000,-205,1000,-316,-55,-1000,-1000,-187,353,808,-341,907,-460,-1000,-150,357,-224,-504,-645,-1000,281,263,370,-915,400,-319,-137,-1000,715,246,81,208,28,577,-22,1000,-1000,-733,396,-1000,612,-525,1000,1000,-689,-191,-123,668,-1000,-363,-594,-1000,-667,-112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00363() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "removeChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{-712,-695,-11,-841,-1000,1000,174,-1000,-1000,-1000,-248,-711,341,-345,-977,1000,672,-985,-1000,248,1000,-737,1000,-992,-19,101,-919,1000,742,165,-857,1000,358,1000,-603,214,-91,-696,-59,-1000,1000,1000,-1000,-409,178,-665,-45,-1000,101,477,1000,-1000,435,-128,1000,-1000,-346,-1000,855,6,-926,852,892,-759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00364() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "removeChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{409,488,-596,145,-274,358,799,-370,-131,462,702,-1000,-404,-650,713,391,1000,-753,-528,-1000,400,-173,1000,1000,1000,-909,1000,-437,1000,904,1000,1000,-1000,1000,128,-1000,594,24,397,-623,458,257,338,-1000,-853,-1000,929,618,-1000,-345,867,564,266,-1000,816,-380,191,-1000,-404,225,-1000,-736,-61,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00365() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "removeChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{45,-1000,-11,-841,-1000,476,437,-514,-108,-425,5,-338,189,40,-445,-102,-458,1000,-1000,1000,923,-1000,1000,-152,211,-200,-227,1000,-1000,-471,-289,845,612,-242,-603,1000,-1000,-778,405,43,744,1000,-742,1000,705,-329,-866,-612,-271,183,1000,-1000,589,1000,477,-1000,-97,941,178,290,-519,578,45,-892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00366() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "removeChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{1000,434,74,-514,652,-420,671,-483,-146,440,-411,-1000,1000,-876,263,-176,1000,325,-48,50,-178,839,201,1000,559,-592,480,-1000,-1000,-1000,791,1000,21,-590,203,943,-928,-1000,83,33,34,177,199,-209,-198,-263,-1000,-54,-849,-622,231,-501,1000,1000,570,-1000,724,1000,-721,332,-742,-875,112,-598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00367() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "removeChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{204,699,695,-662,-462,49,-328,22,-198,-483,-368,17,598,309,164,-673,-1000,87,-928,1000,-454,1000,-810,-432,-1000,575,-349,-567,-546,-1000,-257,514,1000,-781,-757,1000,-1000,-1000,524,-443,446,484,-1000,1000,231,218,-1000,-519,331,-785,562,-1000,159,1000,-58,664,247,-229,-432,-1000,-1000,-1000,-1000,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00368() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "removeChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{-242,-555,848,387,-133,34,-941,-1000,744,-655,-78,-1000,1000,-1000,73,-1000,-1000,-766,-1000,1000,-425,645,-356,342,-12,-978,428,-1000,-1000,-820,193,1000,-434,674,-746,545,-1000,-803,-173,-1000,497,1000,698,488,119,-527,-1000,824,-793,-1000,303,-212,1000,1000,883,-243,1000,972,-674,980,-1000,-1000,-324,-907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00369() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "removeChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{-875,-1000,-27,-632,-489,1000,-1000,-661,-1000,-1000,282,5,651,140,-675,988,1000,-853,-1000,657,696,-442,297,-1000,-493,266,-1000,1000,-570,302,-1000,590,-176,185,833,296,534,79,-707,-416,742,1000,-657,96,-730,-174,-893,-520,101,1000,-373,-750,-443,-62,964,-1000,136,-872,1000,124,33,684,275,-372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00370() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "removeChangeListener(org.jfree.chart.event.AxisChangeListener):void",
            new int[]{-694,967,-979,-801,170,-202,559,988,-876,11,-282,-427,335,-945,-456,697,-491,118,-444,-182,-400,644,-539,-220,-373,-483,363,242,372,592,993,85,-145,-437,-875,103,196,588,-413,475,-322,498,28,-243,-310,-16,-14,-604,-421,695,-325,184,-629,845,399,447,859,-365,540,266,-428,544,-367,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00371() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{683,231,-416,70,-322,-873,1000,165,-767,-1000,-388,213,-591,-510,-83,-501,-565,-148,-1000,-66,-219,-1000,-197,140,296,385,-315,-217,1000,1000,-1000,-1000,-1000,1000,-389,-1000,-217,-508,701,-231,441,1000,153,-253,-521,-1000,743,-1000,137,872,-568,311,-984,-1000,-458,705,-1000,441,1000,25,1000,45,-25,378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00372() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{-435,-533,-1000,1000,145,-449,-202,-879,-412,-1000,120,-282,753,81,680,-386,400,-1000,1000,202,400,626,368,400,-751,148,400,400,-203,-425,283,-159,-560,-180,341,-793,529,937,262,-777,-770,-1000,-88,269,-518,-484,-7,-1000,-484,1000,-483,400,270,786,-22,-223,448,269,-91,494,292,981,-580,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00373() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{1000,-553,-1000,1000,635,-578,959,-550,36,-1000,-1000,-1000,890,544,145,-4,-866,-1000,-400,430,1000,93,1000,1000,430,-812,1000,1000,1000,1000,-1000,-1000,-1000,218,916,-1000,-17,715,1000,-848,-154,-760,1000,-253,-1000,-1000,1000,-1000,-1000,1000,-119,1000,-430,-614,1000,-1000,-1000,441,1000,-450,1000,-1000,316,939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00374() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{231,1000,840,-328,220,-873,-411,1000,-1000,153,400,1000,624,76,730,1000,1000,1000,1000,86,-400,-785,-239,20,-146,-213,-105,-59,-121,-1000,1000,-267,-386,-1000,-52,-362,1000,371,312,627,180,455,-628,-973,-56,680,-257,1000,518,-424,-333,-28,301,1000,-50,159,1000,209,-600,1000,147,969,-697,355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00375() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{548,-1000,-437,-526,-1000,139,723,400,-945,-348,-896,-1000,618,-1000,-506,-1000,-110,-1000,-707,-690,-1000,1000,-1000,-1000,-1000,1000,-22,-842,957,1000,-165,-738,-618,344,-128,259,-311,-1,-98,1000,-496,623,1000,-562,-256,-85,100,602,285,502,-805,873,-1000,-67,-539,1000,-1000,-1000,217,-561,-111,-1000,1000,-936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00376() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{-1000,1000,-912,1000,-479,-1000,-506,355,-1000,648,1000,1000,436,-446,591,1000,-499,-918,1000,-260,-1000,400,-769,-400,-400,137,-578,-512,-411,-1000,1000,48,-123,-670,-587,-88,1000,403,17,620,-1000,-1000,-839,-1000,349,941,-796,1000,816,-32,-883,-469,-150,1000,-1000,400,1000,215,-596,1000,140,1000,-371,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00377() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{662,-690,26,839,-555,-654,-947,-864,-572,-797,332,813,478,-631,639,914,738,530,922,26,-966,-369,794,628,981,-129,-11,877,-594,-496,441,-878,-381,-318,516,-907,519,-7,496,397,-416,-173,-570,-261,-650,286,128,-903,-821,916,-775,-8,819,250,-955,-64,-459,872,-748,161,481,516,-787,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00378() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{-421,1000,673,676,492,-931,207,-1000,-790,-1000,1000,947,-733,142,262,499,-223,-242,-399,183,-429,-1000,-615,-201,1000,643,1000,-383,655,405,-338,-267,-420,-961,1000,-877,1000,142,1000,215,1000,-1000,-1000,1000,-86,1000,-10,-1000,1000,-95,895,-911,-1000,-1000,1000,-357,436,-261,1000,346,1000,534,-13,721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00379() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{-605,528,-387,1000,-377,-455,240,-1000,-1000,-1000,161,1000,760,-648,294,1000,-593,-918,1000,-330,-1000,-667,-981,-306,1000,551,-578,-512,-224,-1000,522,-148,344,-1000,-587,-400,1000,154,372,620,-637,-832,-715,73,140,1000,-677,572,992,1000,-130,-15,-715,628,-984,-708,529,-251,-209,744,480,690,6,961}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00380() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{418,-405,-641,-254,-1000,-221,-219,-722,-804,-1000,234,831,855,-1000,-233,-881,-1000,-1000,-435,-1000,-1000,627,-955,-1000,75,1000,-1000,-1000,-139,178,-288,-62,1000,-838,-1000,381,746,-413,48,1000,-110,-174,-303,500,742,1000,-419,715,745,1000,-180,-1000,-616,-308,-398,748,-260,-834,-255,273,257,-356,1000,-680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00381() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{1,1000,113,-384,1000,-212,1000,-38,-1000,-376,-763,409,-142,-589,-721,1000,461,-191,1000,-975,87,-1000,-835,1000,1000,389,519,-45,477,-659,-890,-588,1000,-925,745,-629,862,696,1000,-724,78,-243,383,-631,-1000,1000,-148,130,712,-1000,493,-719,-1000,401,-74,-1000,-822,-361,-538,864,1000,-284,966,807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00382() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{-76,350,-256,805,373,-1000,-768,-667,23,-601,-72,550,469,-100,684,-231,944,945,1000,-664,50,9,593,650,187,-1000,1000,666,159,-536,469,-956,886,-546,-289,-825,-531,223,7,-836,384,366,168,553,-259,-370,787,-457,312,1000,378,330,1000,-326,757,-54,694,585,-129,-231,-1000,572,-459,594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00383() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{-717,1000,1000,1000,772,-1000,-717,-1000,-474,-1000,80,780,1000,1000,1000,1000,1000,1000,914,1000,931,-1000,698,1000,1000,-364,1000,1000,406,-28,781,-1000,-413,-769,1000,-1000,724,1000,1000,-1000,824,-1000,-982,54,-1000,97,707,-1000,369,587,413,1000,57,-854,1000,-1000,806,441,839,1000,1000,457,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00384() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{-38,982,845,284,1000,-1000,-69,-299,-632,-1000,-444,823,1000,1000,818,1000,866,1000,1000,1000,1000,-1000,1000,1000,1000,-923,1000,1000,312,-521,463,-1000,-985,84,1000,-1000,759,918,1000,-1000,1000,-450,-909,-396,-1000,60,707,-1000,-425,97,773,1000,462,39,1000,-1000,429,331,501,1000,824,369,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00385() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "reserveSpace(java.awt.Graphics2D,org.jfree.chart.plot.Plot,java.awt.geom.Rectangle2D,org.jfree.chart.util.RectangleEdge,org.jfree.chart.axis.AxisSpace):org.jfree.chart.axis.AxisSpace",
            new int[]{-13,-429,-641,1000,-992,-199,-1000,-1000,-890,192,754,871,706,-827,415,-1000,-531,-637,940,-1000,-595,1000,-805,-1000,-1000,876,-875,-921,-1000,-541,847,497,1000,1000,-869,110,942,147,-405,1000,119,-1000,-1000,5,927,727,-394,295,605,499,-79,-952,326,6,-63,1000,830,-986,-851,1000,-228,578,-101,-404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00386() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLinePaint(java.awt.Paint):void",
            new int[]{177,-3,-646,189,113,-516,-503,-29,-304,765,514,-697,-100,-123,626,400,334,-490,-2,1000,-341,-138,199,-293,-606,-175,-998,-1000,-151,-1000,608,275,45,574,498,-596,-531,-1000,974,-98,550,402,-550,-280,-479,-508,202,37,-349,-37,826,837,-788,567,-356,113,-1000,164,-1000,407,-194,-631,-109,164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00387() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLinePaint(java.awt.Paint):void",
            new int[]{-727,35,893,358,-239,244,120,277,61,400,252,-668,15,-509,692,400,1000,1000,-647,-859,384,-542,-191,-293,-606,-891,131,-672,461,417,-534,275,-400,847,367,-579,-14,753,-20,1000,1000,-109,296,93,-143,586,-93,1,-675,151,295,-292,1000,-339,-755,905,-880,-112,-91,132,-182,-405,98,284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00388() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLinePaint(java.awt.Paint):void",
            new int[]{324,41,-1000,-127,1000,589,-156,-943,-1000,569,-1000,-1000,-1000,-1000,245,855,-491,-729,1000,-1000,385,-750,769,386,-1000,-378,-1000,-1000,-479,-385,-1000,-1000,-1000,333,-244,-758,-1000,488,85,-212,1000,-153,72,-1000,865,-1000,1000,833,1000,-680,-862,1000,976,214,1000,-74,-286,-184,29,-701,770,1000,889,-926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00389() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLinePaint(java.awt.Paint):void",
            new int[]{-871,-100,-77,-616,186,527,168,1000,-1000,975,-614,-520,185,-1000,55,270,565,-253,632,423,207,-267,348,402,-1000,-977,-361,-952,197,1000,326,129,-1000,1000,269,-195,-1000,-376,546,430,331,-279,-318,447,-819,-1000,458,297,-24,-95,659,542,-357,792,1000,-130,-831,-397,656,-182,90,-571,165,709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00390() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLinePaint(java.awt.Paint):void",
            new int[]{-489,676,-1000,-1000,1000,1000,-106,549,-276,-1000,1000,1000,620,807,-1000,-199,-768,82,890,-924,-1000,399,1000,-544,816,1000,702,686,-1000,957,-1000,-1000,702,483,-1000,925,870,1000,-1000,1000,1000,-1000,-1000,1000,1000,-231,1000,364,1000,823,-63,238,486,-1000,-813,138,1000,-1000,-1000,-1000,1000,426,-557,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00391() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLinePaint(java.awt.Paint):void",
            new int[]{-75,879,-1000,-497,139,317,-866,178,-1000,-1000,1000,-815,552,1000,-318,-984,51,-315,1000,639,-1000,639,1000,972,878,1000,-1000,-261,-1000,-1000,-1000,-822,1000,400,-1000,214,992,126,-213,685,489,-356,-138,1000,1000,1000,-327,209,1000,585,541,689,-214,-1000,-1000,78,596,-886,-1000,1000,1000,386,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00392() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLinePaint(java.awt.Paint):void",
            new int[]{552,-982,884,-638,234,355,1000,965,-1000,1000,-90,-838,-788,-1000,605,1000,1000,323,-283,-588,-177,-850,-1000,254,-1000,-1000,-291,-1000,1000,1000,180,-8,-1000,552,-342,33,172,-95,-654,869,451,-380,218,711,-341,-1000,969,1000,820,729,1000,504,705,1000,1000,-810,-186,-738,-51,-1000,558,-102,-578,-99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00393() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLinePaint(java.awt.Paint):void",
            new int[]{-911,120,-862,465,-606,-158,80,-191,259,-388,134,-1000,586,518,436,-820,-584,-1000,-255,613,-384,-49,465,-917,-203,-9,-1000,-346,-242,-972,625,-99,400,618,471,-1000,-695,-244,1000,-1000,-535,-491,115,-1000,-439,-1000,258,-385,-311,-12,431,82,-1000,-554,-159,903,-759,1000,413,1000,-300,-583,-105,95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00394() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLinePaint(java.awt.Paint):void",
            new int[]{-735,-1000,369,-1000,221,-131,1000,-57,400,1000,-1000,1000,-671,-1000,-400,1000,-791,907,-877,-102,1000,-1000,-1000,-8,-826,-1000,891,-842,1000,1000,1000,1000,-1000,-1000,1000,1000,-989,-296,-1000,-408,-190,1000,-1000,-495,-933,642,1000,-191,-229,502,1000,588,477,982,528,-1000,840,-441,1000,-18,24,1000,293,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00395() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLinePaint(java.awt.Paint):void",
            new int[]{181,-1000,309,-861,809,333,1000,383,-469,-400,-20,925,-240,400,-1000,359,-762,1000,261,-531,578,109,-440,-1000,93,-114,-89,78,-400,787,1000,-341,400,-980,815,1000,-1000,898,-3,-1000,670,1000,-947,-68,264,1000,-67,1000,166,-835,628,482,15,1000,796,-1000,174,-1000,57,-127,1000,1000,712,422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00396() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{-948,971,-775,197,-1000,405,449,-803,243,190,623,367,1000,689,-980,-530,-782,-1000,-365,314,-1000,278,1000,580,169,680,1000,585,-1000,-1000,115,-145,533,-1000,-260,1000,-1000,9,-1000,238,851,-316,1000,437,-1000,972,-874,547,-134,-1000,-685,-771,1000,205,-1000,509,-533,484,970,597,-349,542,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00397() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{-480,-285,-1000,-152,-41,708,-291,741,-81,1000,927,1000,474,1000,227,558,744,-90,541,978,372,476,650,-456,-336,1000,-409,126,76,-595,171,1000,460,637,214,-92,901,-223,-531,701,-405,179,-751,450,705,458,-489,-856,14,-673,-837,-1000,128,-1000,-112,75,-502,440,-170,-734,-366,-997,-531,811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00398() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{-999,1000,-16,-20,-729,-211,821,-6,638,1000,461,653,504,334,-80,-719,-740,-803,-98,-290,-859,-93,649,484,-23,-269,977,-271,-1000,-602,-268,-785,340,-556,-610,951,131,475,-869,189,788,409,661,1000,-853,1000,-699,419,-576,-321,-417,42,783,388,48,167,-493,-78,1000,-1000,-365,-98,-814,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00399() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{104,65,-987,-811,704,-325,643,669,1000,156,-930,1000,791,1000,1000,523,-1000,-1000,-1000,506,-42,-1000,500,-1000,-180,937,-1000,-150,1000,-374,-1000,767,919,-908,957,-1000,338,525,-1000,0,1000,-1000,-1000,146,200,498,32,-1000,-137,-1000,-666,-777,-267,-1000,-1000,1000,-304,-590,1000,-838,296,1000,904,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00400() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{-626,642,-1000,-154,-231,889,372,857,529,-1000,1000,-1000,1000,995,-1000,269,-328,-441,-1000,835,-218,517,1000,-813,1000,773,71,-12,-1000,-1000,-390,488,618,-169,335,808,1000,-696,-1000,443,1000,-606,928,1000,175,297,-1000,-657,569,-900,-20,-1000,696,-1000,-864,221,118,-44,903,1000,-508,340,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00401() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{-1000,395,1000,825,734,-21,109,-947,470,643,-691,-269,-402,158,-84,-740,-1000,-35,-170,-40,-870,-1000,-970,358,-399,129,632,296,56,471,-260,-801,-243,-1000,-609,439,-1000,1000,729,-43,120,176,-250,825,-790,981,-668,912,-412,943,-1000,50,691,891,1000,579,-414,391,145,-247,481,983,117,-862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00402() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{-95,-999,-1000,-1000,255,904,-27,1000,-1000,-1000,839,-790,-488,1000,-302,-4,1000,-592,209,1000,1000,-341,1000,-290,-457,517,-1000,937,516,-93,1000,1000,1000,881,1000,-834,1000,69,-1000,440,-1000,-1000,228,972,-858,-1000,-194,-488,1000,-1000,-1000,-1000,457,-1000,-1000,1000,-712,1000,275,847,392,-265,117,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00403() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{-903,-1000,-176,-90,171,826,-793,187,1000,1000,-293,89,-414,218,325,778,-45,1000,476,1000,-435,-497,252,318,-1000,224,-315,661,709,805,347,1000,-627,-291,973,-1000,-333,-384,-575,861,-946,464,-1000,-42,673,257,173,-277,716,-1000,475,-1000,51,-657,561,574,-331,-232,-743,43,151,-165,869,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00404() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{-764,759,-29,736,430,-191,821,-38,1000,1000,1000,653,670,942,284,-724,-1000,-1000,-77,-70,256,-390,649,-367,665,-727,474,-468,-847,-1000,-525,-973,1000,326,-1000,1000,-133,683,-370,189,634,295,626,1000,351,1000,-912,-98,-1000,389,-766,54,749,400,48,-133,-843,1000,964,-1000,-249,-205,-1000,-757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00405() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{-64,759,130,-12,-935,544,-41,168,277,234,900,653,-154,-717,-721,391,280,996,-84,814,-299,724,-163,475,238,-727,710,-978,-212,654,-681,-901,-785,195,-23,790,-912,683,758,-962,401,671,626,102,351,408,-878,580,-765,920,140,-257,-216,707,478,-932,-147,244,393,-340,-731,-985,-864,-979}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00406() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{-947,-402,829,899,20,285,-362,-988,-369,243,-534,-780,-809,-411,-229,2,33,-397,-436,285,-1000,-1000,-1000,759,-397,-745,379,614,483,1000,613,20,-885,-149,1000,-349,-820,144,-334,305,-1000,370,-987,389,-313,211,266,735,1000,-917,-690,-273,602,155,1000,840,-377,117,-232,-67,592,270,1000,-253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00407() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineStroke(java.awt.Stroke):void",
            new int[]{-710,1000,388,1000,754,-309,1000,-78,1000,1000,1000,594,-140,902,635,-1000,-1000,-1000,-65,-103,-22,-358,821,-367,940,-436,561,-1000,-1000,-1000,-791,-1000,1000,419,-1000,1000,-24,1000,-660,97,772,379,-233,1000,39,1000,-1000,-189,-1000,343,-1000,65,1000,400,-616,-268,-1000,1000,1000,-782,-208,346,-1000,-794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00408() {
        org.junit.Assert.assertEquals("VOID|isAxisLineVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineVisible(boolean):void",
            new int[]{213,793,-681,-15,-47,464,-466,-165,-500,146,-289,143,530,-324,673,-9,-783,-537,862,558,-246,-977,321,-239,600,402,661,-138,747,344,-358,-844,179,950,-464,709,-621,791,-213,193,-735,974,651,-685,-744,-520,-512,-919,-704,251,-844,194,-383,971,564,237,-216,-741,860,-999,128,-927,963,297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00409() {
        org.junit.Assert.assertEquals("VOID|isAxisLineVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineVisible(boolean):void",
            new int[]{-164,-838,-116,-120,-978,152,398,1000,1000,418,-66,81,37,685,-1000,-686,-1000,706,-501,-854,-208,-732,402,1000,-1000,378,-733,949,-49,-79,1000,-513,-1000,8,-109,-196,-397,-506,707,-631,22,1000,-668,74,596,-921,424,-462,-416,-216,-130,-243,1000,895,-4,-431,58,-1000,1000,-409,-168,-1000,-862,-145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00410() {
        org.junit.Assert.assertEquals("VOID|isAxisLineVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineVisible(boolean):void",
            new int[]{-81,-399,-116,101,-1000,1000,-1000,457,322,-1,-154,-17,1000,365,195,-259,-1000,884,732,1000,-939,-416,-652,268,-112,700,647,475,1000,327,1000,-1000,-467,369,-1000,964,-1000,790,-93,1,-700,866,-368,-511,-100,-292,466,1000,-1000,60,-1000,713,169,1000,-57,331,-313,-938,-1000,-325,-653,-1000,1000,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00411() {
        org.junit.Assert.assertEquals("VOID|isAxisLineVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineVisible(boolean):void",
            new int[]{856,-81,-160,-264,-550,-991,339,-140,136,971,-921,564,-196,928,686,-203,211,-303,-955,-307,575,304,460,-15,752,413,60,-597,-677,783,-417,990,773,812,250,41,-319,-618,769,-475,-694,806,-306,-745,-687,-534,-665,-164,501,906,-809,462,-660,-395,517,-1000,-112,428,12,-496,-764,-167,-129,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00412() {
        org.junit.Assert.assertEquals("VOID|isAxisLineVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineVisible(boolean):void",
            new int[]{106,-219,175,-15,164,122,-801,605,814,581,457,174,-939,-90,469,106,-309,239,429,-25,-270,-1000,-165,34,578,467,661,-138,61,344,626,-242,179,950,-671,-154,-405,234,514,-769,-685,-319,-274,-181,609,-520,742,364,-41,-391,35,876,554,-267,-453,-149,-427,995,-499,263,-345,-217,459,495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00413() {
        org.junit.Assert.assertEquals("VOID|isAxisLineVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineVisible(boolean):void",
            new int[]{-299,1000,-501,-351,-255,-991,335,-140,978,132,459,-524,116,1000,-1000,1000,-698,-687,-412,-486,69,-1000,-257,-15,-537,796,-970,901,-148,376,-1000,990,-33,977,-31,809,-290,-601,769,429,1000,-1000,1000,-815,-1000,-843,92,-278,4,296,-39,24,-660,-310,-937,136,926,-32,882,68,856,-2,-436,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00414() {
        org.junit.Assert.assertEquals("VOID|isAxisLineVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineVisible(boolean):void",
            new int[]{-447,63,759,-1000,1000,109,-476,-120,96,-840,755,-1000,443,-1000,313,-628,33,-496,-361,-349,-442,468,756,-16,922,-313,-209,-743,120,465,-410,-1000,253,-292,163,-244,69,481,-384,-186,528,-381,1000,-127,-1000,-908,-509,1000,617,0,96,536,-415,-52,-800,655,630,-1000,-297,21,-1000,100,-1000,339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00415() {
        org.junit.Assert.assertEquals("VOID|isAxisLineVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineVisible(boolean):void",
            new int[]{-368,1000,188,388,679,1000,-1000,-372,-286,-783,197,-796,-197,-1000,266,1000,558,-736,1000,1000,-1000,-967,-1000,-1000,-145,-64,62,-770,1000,266,333,-1000,-221,-346,-396,64,-967,1000,-719,947,278,808,993,1000,286,447,1000,-405,23,-135,-52,1000,-503,-565,-773,-1000,-210,-894,534,411,907,-740,713,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00416() {
        org.junit.Assert.assertEquals("VOID|isAxisLineVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineVisible(boolean):void",
            new int[]{-28,-219,1000,-1000,164,40,824,185,498,-1,1000,-46,-71,-661,585,1000,-309,-55,247,-91,-927,123,352,34,746,-487,-1000,261,-163,-227,-238,543,954,419,-510,-1000,-390,712,-202,-444,-685,-1000,-368,-177,1000,1000,1000,1000,77,266,33,1000,156,-760,-1000,-625,-996,1000,-1000,1000,-653,583,274,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00417() {
        org.junit.Assert.assertEquals("VOID|isAxisLineVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineVisible(boolean):void",
            new int[]{437,-46,264,-538,-705,-685,1000,112,771,178,-199,965,-827,689,847,1000,-13,71,-46,432,-25,-78,13,-72,671,-209,-620,-140,-663,237,7,1000,854,1000,-449,-996,-749,102,737,-625,-1000,149,-919,-425,1000,881,1000,271,146,1000,-244,1000,122,-941,-466,-1000,-1000,1000,-781,772,-488,-59,466,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00418() {
        org.junit.Assert.assertEquals("VOID|isAxisLineVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setAxisLineVisible(boolean):void",
            new int[]{-28,-103,547,-863,732,54,626,-188,406,-113,-508,-12,-650,250,-42,1000,28,-120,42,1000,-1000,518,-73,-22,491,-608,-1000,-210,-267,-188,-228,876,1000,19,-288,-1000,577,957,-353,-427,692,-1000,-245,396,1000,1000,1000,1000,280,325,456,1000,-148,-1000,-428,-106,-1000,946,-1000,1000,-621,1000,524,320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00419() {
        org.junit.Assert.assertEquals("VOID|getFixedDimension=java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setFixedDimension(double):void",
            new int[]{1000,870,-731,-739,-901,-154,789,-298,323,-596,-991,1000,-1000,98,859,465,-1000,227,724,252,1000,-531,-944,940,-1000,1000,-78,-672,648,185,-383,1000,-880,369,-652,1000,1000,-399,662,-756,-1000,-544,1000,-386,-994,-109,616,1000,-575,-964,-439,1000,-72,-504,-292,428,-1000,-1000,917,1000,-1000,-880,-583,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00420() {
        org.junit.Assert.assertEquals("VOID|getFixedDimension=java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setFixedDimension(double):void",
            new int[]{-1000,1000,-516,-1000,940,1000,1000,-255,1000,-1000,902,-559,-750,-1000,1000,1000,-1000,1000,720,370,-165,1000,-1000,-1000,1000,-1000,524,1000,571,-1000,1000,1000,-214,570,-658,-172,-819,-995,-66,-386,429,1000,-87,-409,302,982,1000,-1000,-950,978,1000,1000,992,-1000,512,609,-935,1000,-1000,-765,868,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00421() {
        org.junit.Assert.assertEquals("VOID|getFixedDimension=java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setFixedDimension(double):void",
            new int[]{977,1000,-686,-922,1000,-364,789,-614,1000,-641,-1000,602,-705,110,935,-129,-583,560,542,-45,1000,565,-944,-9,-1000,893,-306,-964,776,-257,-1000,1000,-880,545,-717,441,218,-911,704,424,-161,-696,724,-747,-994,-189,721,721,-875,-935,240,416,33,-987,-374,-135,-473,-1000,-1000,460,-977,248,-852,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00422() {
        org.junit.Assert.assertEquals("VOID|getFixedDimension=java.lang.Double:LTE1LjI=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setFixedDimension(double):void",
            new int[]{-368,-316,-146,-557,-548,-448,124,-584,605,237,540,1,-520,-527,-725,-434,-553,284,84,-387,578,783,-899,-152,430,537,-498,-908,380,798,-869,995,-42,-355,-159,296,-474,-648,44,-322,-309,151,-239,-865,-75,-42,339,-781,997,-381,892,539,26,-265,549,424,-755,259,-386,-166,-124,-318,-137,-765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00423() {
        org.junit.Assert.assertEquals("VOID|getFixedDimension=java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setFixedDimension(double):void",
            new int[]{84,711,-770,-1000,-1000,655,-464,-6,-1000,-608,757,84,98,1000,-536,337,809,-736,-962,576,1000,-491,244,13,-878,-1000,-583,97,-910,-1000,1000,-701,1000,-185,359,-370,-1000,1000,549,-1000,1000,-814,-27,249,-262,958,1000,585,82,1000,-579,39,1000,850,1000,-111,-853,294,1000,560,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00424() {
        org.junit.Assert.assertEquals("VOID|getFixedDimension=java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setFixedDimension(double):void",
            new int[]{-1000,-872,38,-1000,1000,579,1000,620,-3,-430,-935,-559,-750,-527,658,117,-1000,997,1000,370,-743,98,-939,-515,1000,-1000,-636,797,757,-1000,1000,727,-214,-42,-838,217,-32,-572,-316,588,-861,1000,-562,167,302,33,635,-969,-300,-877,798,808,992,-669,468,288,-572,929,-809,-765,1000,-405,-513,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00425() {
        org.junit.Assert.assertEquals("VOID|getFixedDimension=java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setFixedDimension(double):void",
            new int[]{283,64,1000,-1000,1000,728,944,-1000,-1000,-256,-999,760,602,36,1000,595,-242,-734,1000,687,85,236,-942,-1000,1000,-801,-866,-34,-709,-1000,606,-35,-1000,-800,-12,-783,-1000,-349,-769,481,-1000,1000,-455,-298,-996,72,605,199,220,901,1000,142,628,-1000,-104,586,-450,962,-1000,-1000,904,-507,-508,-626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00426() {
        org.junit.Assert.assertEquals("VOID|getFixedDimension=java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setFixedDimension(double):void",
            new int[]{-968,93,726,-1000,1000,868,804,-87,-616,-253,-1000,-1000,-380,371,543,-266,-614,267,-168,177,-938,40,-875,-350,1000,-77,-576,349,404,-1000,-292,95,-1000,-543,-319,-610,-1000,-379,182,508,790,219,-301,106,-1000,167,609,-51,-146,65,334,59,-783,-563,-641,-111,113,112,-747,517,970,-250,-526,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00427() {
        org.junit.Assert.assertEquals("VOID|getFixedDimension=java.lang.Double:MS4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setFixedDimension(double):void",
            new int[]{-968,76,709,-739,211,868,1000,580,-855,-253,-894,-431,-688,-1000,892,474,-614,525,-168,781,-938,-565,-1000,-317,1000,388,62,780,229,-369,542,104,-1000,30,-566,8,117,-269,409,508,-802,615,-199,106,-1000,207,577,-493,-146,-166,30,616,-783,-309,-883,512,-699,887,-505,262,967,-1000,-371,-219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00428() {
        org.junit.Assert.assertEquals("VOID|getFixedDimension=java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setFixedDimension(double):void",
            new int[]{358,76,-770,-1000,232,1000,769,-195,-68,-253,-316,844,138,-1000,1000,861,-392,-13,426,1000,-702,-417,-968,140,1000,-1000,1000,519,94,-621,1000,782,-642,1000,-39,138,646,-269,202,-3,-802,615,872,-198,-759,207,648,1000,-517,991,30,373,726,-1000,580,-244,-883,-552,-152,-427,-891,-1000,-1000,-152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00429() {
        org.junit.Assert.assertEquals("VOID|getLabel=java.lang.String:MHg4", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{1000,-233,-7,-749,918,449,-366,-54,162,20,-119,770,804,460,1000,-917,-458,147,737,386,618,835,1000,1000,797,457,879,323,-419,-523,-160,-847,-727,1000,114,-428,1000,-621,-884,-258,-569,-724,961,1000,314,520,219,66,160,-172,1000,780,-599,902,1000,-611,864,758,900,1000,-262,19,-539,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00430() {
        org.junit.Assert.assertEquals("VOID|getLabel=java.lang.String:MHg4MDAwMDAwMDA=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{1000,-316,629,-691,-205,618,1000,649,-1000,1000,-1000,-535,1000,-497,244,-1000,-373,1000,-6,-448,1000,-1000,838,160,721,-1000,-614,808,768,-962,-1000,-199,-1000,1000,1000,-936,1000,-1000,755,202,-1000,-1000,1000,356,-520,1000,1000,345,830,407,-755,1000,469,1000,292,-992,1000,-514,280,1000,-1000,-1000,-949,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00431() {
        org.junit.Assert.assertEquals("VOID|getLabel=java.lang.String:", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{-11,-233,174,605,-318,-634,1000,-796,162,-286,-626,770,-323,958,-400,304,30,-408,138,-110,-301,835,-256,-203,-301,457,-130,661,-904,-349,-880,521,-727,-400,-946,-500,-471,338,1000,1000,-1000,-339,-108,-1000,165,-395,-1000,-304,908,-172,-288,-520,1000,-25,1000,-73,-355,1000,900,-686,557,1000,-925,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00432() {
        org.junit.Assert.assertEquals("VOID|getLabel=java.lang.String:IA==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{1000,344,-181,67,-727,-470,1000,1000,-1000,-830,343,-633,-303,518,322,-1000,-94,-1000,-127,-1000,-837,-1000,-742,64,-995,-492,1000,-576,-322,1000,-1000,-1000,33,-64,-262,391,1000,247,-141,945,-1000,-339,82,-948,572,1000,-1000,-683,209,-695,-696,-294,-138,-578,-1000,-70,344,-58,213,-373,-97,45,-511,-31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00433() {
        org.junit.Assert.assertEquals("VOID|getLabel=java.lang.String:IC04Mzkg", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{353,90,-572,-648,140,95,-564,-143,-3,668,1000,325,42,1000,514,-694,-241,-662,839,717,225,1000,-206,17,509,-43,-345,210,929,335,365,-18,-54,-400,-1000,-86,-213,21,790,-104,720,-235,-900,-139,-148,95,-1000,-1000,408,109,653,-383,582,-607,-482,979,-523,593,-74,-879,1000,907,-349,71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00434() {
        org.junit.Assert.assertEquals("VOID|getLabel=java.lang.String:IC03NDIg", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{1000,-338,-731,994,-1000,-1000,-13,-251,623,-41,-617,-1000,-465,1000,1000,778,742,-407,-139,-602,-1000,-627,-329,-176,-917,406,1000,-573,-302,1000,-106,1000,466,-1000,-428,207,-30,-119,516,-48,-433,-98,-471,-73,-214,1000,386,142,1000,431,-841,-502,1000,-246,478,492,-1000,229,-423,-180,235,-334,-82,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00435() {
        org.junit.Assert.assertEquals("VOID|getLabel=java.lang.String:MHg4", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{400,791,1000,-917,1000,1000,-556,521,-897,1000,-1000,1000,1000,-1000,596,-1000,922,1000,20,1000,-789,-649,1000,1000,1000,-487,1000,-1000,1000,-1000,1000,-1000,-1000,1000,1000,504,1000,-1000,60,-756,701,-1000,1000,1000,-56,401,1000,1000,510,415,1000,1000,114,1000,-265,-1000,1000,-1000,936,1000,-1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00436() {
        org.junit.Assert.assertEquals("VOID|getLabel=java.lang.String:MHgzZTg=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{1000,-923,-261,-691,137,85,588,206,277,666,-846,-2,812,540,-176,-639,-425,-530,912,-301,828,342,-329,1000,412,-257,1000,721,-683,-349,-1000,-259,-1000,1000,-114,-979,840,-903,582,699,-1000,-1000,671,312,375,321,632,261,1000,694,597,662,825,828,316,269,807,1000,-399,696,-448,-615,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00437() {
        org.junit.Assert.assertEquals("VOID|getLabel=NULL", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{-20,-54,526,-942,1000,907,-689,440,-96,1000,-1000,1000,1000,-808,717,-473,207,1000,314,1000,-252,-697,728,1000,1000,-1000,1000,-322,728,-1000,1000,-1000,-526,1000,1000,203,1000,-1000,-501,-914,1000,-1000,1000,1000,-1000,920,1000,1000,1000,457,1000,1000,324,1000,1000,-1000,1000,-1000,998,1000,-1000,-1000,-511,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00438() {
        org.junit.Assert.assertEquals("VOID|getLabel=java.lang.String:LTB4ODAwMDAwMDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{-314,937,526,-518,-883,54,566,-505,-188,1000,-1000,605,-622,-500,-47,942,221,1000,-1000,-654,-824,-1000,-795,-917,-1000,-283,175,-1000,-822,-1000,-76,790,468,-400,1000,997,204,-1000,-730,1000,-445,-474,208,506,-583,1000,1000,1000,1000,1000,-731,255,1000,196,-731,-186,210,-1000,-520,631,435,-769,-117,956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00439() {
        org.junit.Assert.assertEquals("VOID|getLabel=java.lang.String:LTB4OA==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{449,-181,-686,-1000,-291,148,-564,421,-367,1000,-1000,1000,1000,-620,1000,-558,-307,1000,1000,-156,1000,60,1000,1000,912,-71,1000,-74,-414,-1000,-641,-217,-1000,1000,1000,-426,1000,-1000,-944,219,-773,-1000,1000,1000,153,1000,1000,1000,923,536,864,1000,84,1000,884,-1000,1000,236,536,1000,-1000,-287,-495,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00440() {
        org.junit.Assert.assertEquals("VOID|getLabel=java.lang.String:MHg4MDAw", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabel(java.lang.String):void",
            new int[]{-1000,-1000,-686,-1,-1000,-1000,-538,-1000,-1000,488,-76,-1000,-1000,1000,-1000,-126,1000,1000,-252,-637,275,19,-403,-918,275,-1000,-614,1000,-93,-1000,-1000,1000,-368,-1000,-513,-1000,-254,1000,1000,1000,-231,312,-565,-1000,87,281,-1000,-1000,596,515,-583,-1000,84,-1000,164,524,-1000,1000,536,-1000,-1000,1000,-661,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00441() {
        org.junit.Assert.assertEquals("VOID|getLabelAngle=java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelAngle(double):void",
            new int[]{-691,790,-286,854,-1000,550,164,-499,323,625,-863,-939,1000,-157,995,-273,-260,-598,745,308,160,-135,1000,-688,371,1000,744,-316,1000,113,-251,6,1000,-320,-1000,-803,1000,-291,1000,1000,-211,-257,179,-1000,169,835,358,-488,48,-806,1000,-1000,-646,822,615,1000,1000,960,871,913,682,451,-580,622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00442() {
        org.junit.Assert.assertEquals("VOID|getLabelAngle=java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelAngle(double):void",
            new int[]{684,-299,-856,221,360,614,889,266,-427,-937,329,1000,675,60,999,468,362,-628,185,-1000,1000,570,-876,-603,-1000,329,949,-998,920,1000,1000,-96,-921,1000,377,-923,-997,443,556,-551,1000,1000,-1000,-1000,-1000,-1000,608,988,-392,-56,917,-76,-1000,1000,354,996,-387,-1000,76,-1000,1000,221,-1000,596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00443() {
        org.junit.Assert.assertEquals("VOID|getLabelAngle=java.lang.Double:LTI5LjM=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelAngle(double):void",
            new int[]{-1000,-363,-402,564,-1000,464,-41,-269,809,471,-1000,-864,1000,644,759,-828,88,-837,-324,-254,133,-302,1000,-800,-363,440,1000,-756,571,459,-770,898,457,50,-688,-841,947,642,421,653,-406,362,604,-1000,-293,310,-372,-172,-887,-128,43,-1000,-329,1000,1000,831,590,472,1000,310,102,737,395,133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00444() {
        org.junit.Assert.assertEquals("VOID|getLabelAngle=java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelAngle(double):void",
            new int[]{-320,-696,-237,739,-1000,593,194,-261,711,-100,-73,-298,1000,519,1000,1000,-23,-263,330,-1000,1000,430,394,-1000,-1000,-597,-611,-986,1000,1000,182,-567,830,546,591,-1000,93,846,1000,1000,1000,748,734,-1000,-813,-281,-826,163,-1000,478,1000,-396,-1000,199,629,1000,823,168,778,27,-842,722,-1000,193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00445() {
        org.junit.Assert.assertEquals("VOID|getLabelAngle=java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelAngle(double):void",
            new int[]{679,831,-761,-160,1000,-732,749,-261,-1000,16,-230,592,-1000,-1000,-685,1000,175,960,4,772,-340,37,-630,490,143,948,-611,1000,545,-822,1000,-682,-565,-447,572,-220,-1000,493,-1000,299,-649,748,571,577,44,381,1000,176,200,-1000,-274,557,428,-1000,-1000,-1000,-459,523,-122,140,1000,-1000,327,738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00446() {
        org.junit.Assert.assertEquals("VOID|getLabelAngle=java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelAngle(double):void",
            new int[]{-411,138,-462,309,274,-109,1000,-470,-813,-123,-598,450,-207,-1000,-6,1000,443,494,-186,-18,125,-65,241,202,315,839,-1000,466,692,-264,295,246,-554,-278,-240,-350,-756,967,-600,581,-400,-43,1000,75,-105,262,1000,413,-727,-695,-168,330,-448,-1000,-202,-168,-413,650,-122,102,705,-349,208,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00447() {
        org.junit.Assert.assertEquals("VOID|getLabelAngle=java.lang.Double:LTEyLjc=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelAngle(double):void",
            new int[]{152,321,-687,95,108,-821,-678,-61,655,313,958,857,454,-232,-70,339,-127,-926,501,-228,779,-259,-414,225,87,527,330,564,-413,296,834,-105,-999,395,668,-723,-761,-268,-652,-423,369,969,-964,-542,-39,-425,394,-447,132,911,-267,63,-97,-77,-603,-728,-746,-415,-365,-258,676,-223,-821,-666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00448() {
        org.junit.Assert.assertEquals("VOID|getLabelAngle=java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelAngle(double):void",
            new int[]{-1000,293,-535,-84,-323,35,683,266,-663,-34,329,-766,-700,-1000,-382,-1000,-159,-69,-424,1000,-717,329,-876,-60,890,1000,-1000,-555,442,-1000,-133,821,-574,-471,631,628,-997,1000,-1000,-883,-1000,-300,525,612,31,542,1000,1000,-392,-1000,-1000,168,1000,-1000,173,-484,778,1000,332,342,37,-409,1000,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00449() {
        org.junit.Assert.assertEquals("VOID|getLabelAngle=java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelAngle(double):void",
            new int[]{1000,-207,-1000,1000,1000,412,-836,897,107,-901,697,905,668,512,1000,-268,-279,-579,791,-581,982,1000,-1000,-271,-1000,604,-231,-100,746,-660,1000,-154,-1000,1000,1000,-994,-1000,454,-273,-1000,1000,1000,-1000,582,-811,-1000,751,420,559,118,5,-9,-1000,471,-1000,1000,1000,-1000,-452,-1000,849,-862,-595,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00450() {
        org.junit.Assert.assertEquals("VOID|getLabelAngle=java.lang.Double:LTEuMA==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelAngle(double):void",
            new int[]{-700,1000,-391,1000,-179,-357,194,102,83,628,-1000,-1000,1000,1000,128,569,506,798,1000,369,-1000,782,521,827,741,367,-1000,-1000,520,-538,-1000,1000,-267,-488,888,623,-756,869,-574,-1000,-1000,78,734,-1000,-26,730,818,-452,-48,-383,-390,-1000,684,973,-781,-205,-252,-522,362,219,227,1000,1000,626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00451() {
        org.junit.Assert.assertEquals("VOID|getLabelAngle=java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelAngle(double):void",
            new int[]{684,-64,-756,1000,559,260,-1000,1000,-926,-1000,329,1000,921,365,1000,-147,453,-464,560,-1000,1000,1000,-876,-166,-920,330,-231,-1000,1000,1000,1000,314,-1000,922,712,-1000,-997,443,593,-1000,919,1000,-1000,-1000,-1000,-1000,1000,1000,-454,-56,257,-9,-1000,914,-473,969,-232,-1000,-144,-1000,967,221,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00452() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelFont(java.awt.Font):void",
            new int[]{-1000,882,74,761,-1000,-414,-86,-1000,846,-623,269,95,-352,1000,377,294,-155,-62,373,-415,-804,325,-148,-1000,100,-1000,73,419,255,211,-1000,144,-746,-866,-259,-869,-190,-990,-367,-366,-875,-1000,549,629,207,-50,-50,-242,25,350,-1000,511,1000,-738,-1000,320,1,-661,-1000,1000,-721,-1000,-1000,-18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00453() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelFont(java.awt.Font):void",
            new int[]{-181,-38,-934,198,296,-1,332,119,-1,-541,115,599,1000,829,-613,-413,-345,1000,338,-184,6,-861,-602,1000,-1000,-357,-756,891,65,147,533,1000,690,440,-247,-279,450,735,-156,-1000,-991,-382,608,-1000,266,-684,-137,-1000,700,-78,-278,-198,585,941,396,204,-651,-293,-611,462,135,-541,595,-510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00454() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelFont(java.awt.Font):void",
            new int[]{-1000,-73,-133,974,-211,-751,-366,-646,1000,-129,71,515,779,395,-853,-448,647,283,-482,-612,-1000,-1000,-624,313,580,-228,411,1000,814,-413,-446,562,948,44,-586,-981,1000,-712,185,-1000,-1000,267,24,-158,430,-385,-801,-594,-321,-573,-712,929,1000,1000,-153,1000,-1000,-1000,-78,516,-627,-1000,-270,-790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00455() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelFont(java.awt.Font):void",
            new int[]{669,1000,-791,50,-1000,-554,338,-834,-955,-548,-1000,-372,-45,606,1000,1000,-1000,-46,781,209,296,1000,-211,-720,-688,638,-146,235,-1000,738,366,1000,-1000,581,1000,206,-572,-637,-577,329,7,-549,874,-840,-636,-320,396,-842,1000,440,-105,-344,-113,-1000,-176,-1000,1000,505,-1000,551,-369,-14,-789,158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00456() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelFont(java.awt.Font):void",
            new int[]{-1000,728,23,259,-1000,-286,845,-706,431,-1000,-922,-141,273,1000,402,1000,-436,154,1000,-569,-426,9,-747,-791,236,-1000,-974,1000,-880,392,-479,1000,-612,1000,-538,-385,-268,-106,-1000,-798,-1000,-1000,782,71,600,217,415,-691,515,840,-1000,249,1000,-1000,-350,-68,774,-489,-1000,1000,-516,-955,-1000,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00457() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelFont(java.awt.Font):void",
            new int[]{180,1000,74,906,-1000,-1000,8,-1000,805,392,-1000,1000,621,573,804,1000,-1000,-507,811,103,-1000,-279,-148,-1000,100,-48,91,1000,-1000,-114,530,481,-746,2,1000,-285,747,-1000,17,-1000,-1000,-1000,549,-1000,-720,-548,-840,-242,25,146,-1000,675,1000,-195,-954,276,534,-1000,-1000,587,-721,-1000,-388,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00458() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelFont(java.awt.Font):void",
            new int[]{-972,385,-72,-236,73,17,959,-491,-677,488,375,-401,195,486,-949,1000,-373,495,-433,1000,903,-231,-407,-34,264,-858,-878,-667,-570,141,71,578,1000,108,152,-1000,-572,611,321,564,732,325,-258,-35,823,-362,-226,-1000,1000,211,979,838,25,882,-607,741,482,763,-1000,796,652,1000,1000,673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00459() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelFont(java.awt.Font):void",
            new int[]{-568,1000,-563,-948,-1000,-246,1000,-825,-44,431,137,-618,900,1000,1000,195,-356,440,943,-1000,-627,510,449,-1000,-496,-787,-1000,1000,870,600,-1000,-44,-659,962,-1000,-639,856,-798,-1000,722,592,704,697,-630,-351,852,-1000,-363,431,-95,-1000,1000,-802,-580,-950,-970,-925,-709,-600,378,-529,-1000,-1000,-454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00460() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelFont(java.awt.Font):void",
            new int[]{-882,-490,-408,145,-805,-987,338,-652,324,-1000,342,627,-334,1000,-1000,-819,84,367,157,-1000,951,-284,433,280,-338,-532,659,-708,1000,846,536,-15,-321,608,-1000,-1000,15,-822,613,-287,-738,731,163,-592,-450,-634,108,-971,-2,204,-1000,1000,-775,130,-698,-498,-457,-1000,-634,884,171,-1000,-685,-923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00461() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelFont(java.awt.Font):void",
            new int[]{1000,-311,-717,958,1000,-457,-1000,-99,685,721,913,1000,-152,-520,-693,-1000,67,-289,-863,862,-697,-908,264,1000,-1000,1000,1000,80,1000,-967,643,-804,1000,-850,1000,-622,344,-412,991,-744,-903,1000,671,-1000,-731,-804,-625,-448,-68,-778,804,-346,457,1000,297,1000,-1000,-863,591,-1000,-313,-125,1000,-493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00462() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelFont(java.awt.Font):void",
            new int[]{-508,245,-503,-930,-876,-790,658,-847,-52,-1000,218,-278,-108,881,-684,-192,-806,-375,1000,-1000,-627,1000,1000,-1000,-1000,73,-802,-218,1000,350,-1000,-1000,-1000,610,-556,-996,-1000,-1000,-1000,1000,656,1000,306,-1000,-1000,1000,764,-109,517,212,-469,1000,-978,-1000,-1000,-1000,-786,-709,-889,-63,-628,-1000,-1000,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00463() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{-587,400,-146,-145,-194,1000,1000,512,494,806,-951,579,-1000,485,-400,-559,1000,218,1000,-1000,655,-1000,-1000,593,-175,515,1000,38,-1000,894,-755,852,700,741,-54,-1000,-814,-896,-1000,649,747,1000,-620,309,-32,-1000,1000,12,-434,784,-754,88,772,675,1000,-698,303,830,-460,298,1000,523,649,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00464() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{81,-158,-141,-1000,-614,190,-68,-434,430,-486,196,-699,1000,921,-944,400,-1000,-881,304,469,-722,-1000,215,306,-711,-1000,-1000,678,195,-752,-698,85,431,-44,-1000,1000,21,-1000,806,55,-248,-1000,139,-46,-565,-350,620,796,644,1000,728,233,-253,-637,-135,-1000,-740,232,1000,-166,-609,-969,-1000,108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00465() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{551,84,402,1000,394,1000,809,1000,983,921,263,1000,-1000,356,-1000,218,1000,420,328,-452,-263,1000,1000,-628,172,-228,1000,-3,-910,11,-472,522,717,1000,1000,255,-632,-869,-1000,239,417,-116,-265,-350,293,-1000,1000,-253,85,-256,400,7,1000,-1000,-121,157,392,279,-896,912,1000,312,667,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00466() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{1000,-1000,-856,87,-1000,-637,-1000,-580,-279,-594,1000,-655,-81,1000,748,-1000,-1000,1000,-264,-1000,-1000,158,-63,-1000,-61,866,-800,1000,730,361,-4,-729,243,-172,1000,1000,1000,-1000,945,1000,-1000,462,-791,64,1000,778,459,-1000,-1000,390,-1000,71,1000,1000,-680,52,-856,1000,-261,1000,634,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00467() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{1000,806,194,-773,-249,178,279,129,-54,1000,-355,-25,-1000,-1000,265,234,154,-500,-403,1000,832,-279,657,-234,-372,-942,-122,308,297,-912,-129,-53,-102,785,-316,510,842,630,534,-630,51,-840,-274,-292,-87,529,-410,647,913,600,787,-742,257,-827,-456,177,709,-435,690,-170,709,-191,-821,-124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00468() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{1000,-20,-921,454,27,57,121,295,-612,670,-328,1000,-1000,1000,20,-1000,159,1000,1000,-704,-259,227,-1000,-872,-215,1000,-298,5,-1000,1000,234,-912,823,1000,1000,-542,-1000,-1000,-1000,1000,-1000,807,-128,78,274,-1000,1000,-929,-1000,946,-1000,292,-710,1000,-9,-487,-749,1000,-1000,1000,1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00469() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{713,-553,-335,-145,-915,347,506,1000,-553,893,160,1000,-1000,1000,-1000,-954,-53,1000,326,-1000,772,-261,-1000,-1000,-931,1000,1000,275,-16,20,-614,-296,626,1000,-188,-944,-1000,-873,-1000,1000,397,600,-130,-40,145,-156,1000,-28,-190,923,-1000,-254,578,857,1000,-698,487,900,-154,1000,817,923,1000,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00470() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{1000,-1000,-847,-1000,-781,-1000,-398,593,269,254,-11,1000,-1000,1000,606,-1000,-741,1000,1000,-550,791,-251,-1000,-311,-350,901,1000,234,351,1000,-442,-434,-295,64,-170,-1000,-490,-1000,-1000,1000,115,1000,-265,-47,599,373,296,-55,-1000,-40,-265,-203,691,-1000,1000,3,484,1000,-371,1000,867,998,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00471() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{745,-553,-327,-668,-82,-790,-436,920,495,184,622,1000,-1000,1000,157,-1000,-303,930,371,-343,1000,-318,-1000,-29,-154,-499,158,526,357,1000,-614,-394,24,-35,158,-944,77,-851,110,345,620,600,206,-930,913,574,34,-360,-1000,-69,-82,631,-680,1000,178,72,818,900,-532,1000,979,-402,1000,-321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00472() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{713,400,-357,-145,-325,826,483,512,42,806,-605,565,-1000,888,-400,-1000,459,591,1000,-64,159,-261,-1000,-330,-266,515,1000,275,-1000,708,-274,852,646,1000,576,-453,-560,-1000,-1000,649,-653,600,-430,-116,-3,-1000,1000,-135,-567,923,-715,400,578,1000,354,-698,-137,936,-846,654,1000,621,1000,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00473() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{1000,1000,449,-1000,-300,293,-1000,821,482,851,-62,-1,-1000,-425,-969,-270,-343,-87,1000,1000,1000,-959,-176,998,-905,-1000,70,661,-716,241,-42,1000,393,631,-493,247,1000,-684,399,-1000,-646,-413,-702,-1000,-419,180,-355,942,192,1000,1000,1000,-1000,1000,-544,-833,160,461,-55,-30,613,-1000,-14,975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00474() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{-492,1000,-447,923,-184,1000,-756,1000,-658,55,-672,-802,-107,593,-1000,-1000,509,-260,1000,-704,-900,873,223,1000,184,133,85,649,-1000,-459,676,190,1000,771,1000,-906,208,-1000,-9,-367,-188,-504,57,400,-295,-1000,133,-341,112,-414,-44,135,-1000,1000,-818,219,-371,321,-533,-496,46,-7,342,30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00475() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelPaint(java.awt.Paint):void",
            new int[]{549,704,464,-136,76,221,-351,-356,21,-915,929,327,560,185,203,1000,53,1000,501,944,-525,-186,1000,-508,-217,802,262,654,-567,661,842,122,902,-293,-355,-436,511,369,-683,-430,-69,-249,359,515,14,656,541,278,248,-221,-3,-838,684,-842,-486,219,544,744,707,-335,-84,1000,14,-350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00476() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelPaint(java.awt.Paint):void",
            new int[]{113,-367,-1000,64,764,62,509,-569,1000,-463,423,-783,248,-323,-895,1000,250,641,-1000,-921,444,1000,-497,-233,746,-93,-168,334,335,-572,-976,636,-23,-756,474,317,497,810,-363,-502,1000,-656,-1000,-1000,-125,199,-1000,-198,-58,328,-653,63,-504,136,430,-685,-254,-728,-762,-520,11,171,-227,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00477() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelPaint(java.awt.Paint):void",
            new int[]{184,672,-141,-232,754,160,321,-691,566,-737,456,-897,214,244,209,852,460,825,-693,424,711,986,274,-784,302,-65,-623,94,546,-596,-487,464,-431,-708,325,302,913,862,-125,545,570,-813,-871,-829,-302,362,-847,486,-417,94,-515,-331,-393,734,431,-525,-58,-444,183,-877,488,132,-410,881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00478() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelPaint(java.awt.Paint):void",
            new int[]{1000,-165,-525,673,126,824,1000,729,936,-219,-444,473,-345,-725,-197,73,-941,256,491,-1000,-409,417,-174,263,-581,-796,479,-1000,-726,1000,-637,-1000,401,-1000,-1000,454,-1000,-875,-689,-410,1000,1000,838,-971,-1000,190,1000,-501,-303,1000,766,-200,1000,509,1000,24,-216,37,-753,544,-195,-1000,105,-576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00479() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelPaint(java.awt.Paint):void",
            new int[]{925,735,471,-750,275,764,167,210,460,-714,620,132,-563,867,96,994,952,-459,895,196,-681,-118,985,-876,662,-79,-638,741,-916,-126,173,-946,-639,-549,913,-459,-384,766,74,254,227,-694,-490,178,703,250,469,661,655,-442,-227,-754,131,652,-67,-920,582,275,116,-785,509,-820,-137,-410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00480() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelPaint(java.awt.Paint):void",
            new int[]{920,1000,793,642,1000,1000,1000,68,383,-1000,1000,-392,592,1000,-436,1000,-535,-121,543,55,363,550,435,-571,-608,-329,60,-1000,-1000,829,-371,-681,-44,-1000,-1000,1000,33,-201,-274,-207,1000,935,339,-978,-1000,1000,-672,-869,-724,1000,1000,-56,1000,1000,1000,-459,-1000,-493,80,-253,-248,539,-1000,169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00481() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelPaint(java.awt.Paint):void",
            new int[]{770,1000,353,-1000,174,189,465,210,641,-895,885,-1000,1000,318,1000,1000,-127,-13,1000,1000,-1000,-401,985,-681,979,-79,-899,1000,-377,297,-1000,-956,-354,-100,133,-594,-384,766,44,254,440,-24,139,91,523,285,1000,1000,408,-766,-147,-1000,1000,-525,-622,464,300,373,539,-866,427,-533,-137,-222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00482() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelPaint(java.awt.Paint):void",
            new int[]{709,253,-807,-214,931,-271,989,742,1000,457,230,449,58,-939,-1000,-636,-267,541,533,1000,533,332,-41,190,376,360,-104,-847,459,1000,-79,-1000,-202,-1000,-807,651,-1000,-346,-1000,-436,736,956,1000,350,-85,485,-1000,-134,-769,-75,226,-627,1000,1000,1000,715,-241,-64,462,279,625,116,423,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00483() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelPaint(java.awt.Paint):void",
            new int[]{832,633,16,1000,-209,824,20,1000,-44,-286,-1000,1000,-345,-401,130,-46,-1000,16,435,-737,-524,-25,-174,-439,-700,-696,1000,-1000,-883,1000,-371,-1000,709,-1000,-447,193,-737,-1000,-1000,-729,485,528,1000,-971,-1000,169,1000,-732,169,1000,1000,213,728,11,845,88,447,97,-296,544,-812,-1000,-481,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00484() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelPaint(java.awt.Paint):void",
            new int[]{392,827,45,-302,993,-816,924,-692,588,821,285,-315,59,-623,-97,-202,-565,-278,298,63,934,181,848,-182,865,860,-532,-976,741,888,761,-115,-935,251,-154,377,-871,87,-909,250,938,792,903,973,895,98,-593,313,-783,-586,-644,-255,257,124,-59,745,851,330,301,166,619,518,485,-651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00485() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:MHg4MDAwMDAwMDAwMDAw", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{-1000,-515,689,856,-134,129,-338,729,299,-1000,-1000,1000,-1000,1000,-169,1000,-780,-244,-272,836,-474,-879,287,640,-93,903,-109,280,-458,1000,612,-606,726,1000,581,1000,739,-530,909,258,-720,725,775,483,21,1000,-1000,383,1000,557,-1000,210,141,-373,-1000,1000,196,-1000,-818,807,21,-450,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00486() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:YWFhYWFhYWFhYWFhYWFhYWFh", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{-1000,1000,69,-962,-325,51,377,-87,-500,-418,12,338,-1000,-792,-620,-830,1000,169,-272,836,1000,101,287,849,-650,-212,-165,-748,-32,-794,-395,-326,-408,-817,-374,643,-1000,461,443,415,-378,1000,904,-563,21,476,-1000,-1000,537,-217,466,165,-798,-754,546,-899,752,-979,-124,-998,361,766,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00487() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{-1000,964,79,-261,979,-211,597,-899,-1000,-1000,-154,63,-1000,-1000,-788,57,802,115,-1000,1000,187,691,850,1000,-1000,943,-1000,295,-171,-1000,-997,-1000,384,391,397,1000,-183,865,1000,229,-1000,1000,1000,-730,593,1000,104,-1000,239,-744,665,-701,-586,-545,-767,-605,777,-1000,-150,-1000,774,21,1000,826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00488() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:V19GamoJNl9KU2VYNmlfNi5UNlc=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{-1000,-34,-341,187,420,943,-482,344,-239,-307,-587,219,-1000,575,-235,352,570,-368,-1000,-953,303,19,353,1000,-1000,684,54,866,627,1000,-195,-1000,1000,632,481,1000,697,-1000,-447,-99,-1000,920,950,1000,770,678,-934,590,507,743,-510,862,-218,-1000,-723,795,456,-954,552,346,-1000,-369,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00489() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:MHg4", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{-1000,209,615,164,-946,34,695,-107,-233,-1000,-568,278,-701,-103,-556,-1000,-651,608,-272,1000,-1000,-1000,740,-356,-93,794,-795,187,-625,1000,135,-403,295,443,945,767,-264,-879,1000,1000,-720,780,810,-330,-149,1000,354,-730,813,-98,-375,-884,254,-15,545,20,-436,-1000,-1000,318,248,499,1000,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00490() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:IA==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{-1000,-346,-218,636,1000,1000,-497,-3,-430,-800,-798,-1000,240,1000,-593,1000,-292,-926,-170,-409,-319,-571,831,1000,-29,797,-176,862,13,1000,-107,-1000,966,402,844,1000,1000,-854,658,201,-871,938,958,1000,628,751,-1000,135,834,734,-705,-533,40,429,-1000,1000,-1000,-985,-263,1000,-317,-1000,1000,587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00491() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:IA==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{-1000,-118,599,-193,1000,744,-88,15,-487,-27,-632,650,-740,903,-806,1000,159,-755,-594,1000,1000,400,578,261,-1000,-173,278,-178,-17,-173,-419,194,-122,-859,-595,1000,73,734,-464,-735,-653,1000,989,339,561,492,-753,433,1000,-1,275,1000,-1000,-483,-378,416,1000,-810,897,957,631,277,1000,680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00492() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:MGRXOU4z", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{356,-203,-45,-149,301,71,273,-439,-885,28,236,-17,-961,-513,-569,-497,794,-13,-559,-732,-352,-328,922,723,-459,-251,-280,607,-8,366,-429,-785,854,-709,1000,344,-421,-710,-310,-14,-571,221,980,845,-26,239,-298,-661,-568,-180,686,-268,28,-1000,-574,-240,-1000,-18,-42,-210,-702,-996,809,115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00493() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:LTB4MWM4", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{-1000,-34,-782,32,-89,1000,-637,487,212,-276,-375,-397,-758,807,456,101,786,-833,-715,-1000,-157,-548,-322,480,-481,941,48,1000,1000,1000,-64,-480,1000,325,703,465,1000,-1000,-1000,-316,-936,1000,810,1000,1000,307,-491,209,-497,946,-732,792,-256,-1000,-1000,909,-466,-799,-70,1000,-1000,-891,710,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00494() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:LS04NjZlLTc0Mg==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{978,-501,-488,-254,-741,218,463,-869,-359,521,729,-896,668,-280,358,-396,-20,-137,39,-847,0,903,691,-565,289,-667,704,-616,793,752,-414,813,-603,705,472,-738,-45,112,276,520,-709,-403,-924,-975,-474,-866,-261,-742,-472,-455,785,-867,295,609,-15,-616,-357,793,-679,-987,-321,-40,-437,-58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00495() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:LS0zOTQ=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{-1000,407,529,-593,1000,-326,614,54,27,-128,-299,761,-648,-192,-593,355,-118,861,746,831,601,-936,621,1000,101,-347,668,-1000,-1000,404,394,-261,46,-1000,506,1000,-812,125,672,730,943,1000,678,242,-930,-81,-76,-1000,1000,-429,475,828,-447,-473,720,-54,-100,-848,-263,-96,831,133,938,587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00496() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:MHgyZg==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{-1000,-20,809,608,852,-232,-1000,1000,1000,768,-1000,1000,-1000,1000,669,-656,822,788,151,-47,1000,-722,-1000,438,277,-447,1000,-1000,-54,1000,1000,375,1000,-158,-1000,26,777,18,-1000,-709,247,888,667,192,-806,831,1000,716,1000,1000,338,1000,-998,-1000,56,1000,1000,-924,1000,787,-463,1000,1000,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00497() {
        org.junit.Assert.assertEquals("VOID|getLabelToolTip=java.lang.String:MHg4MA==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelToolTip(java.lang.String):void",
            new int[]{-220,1000,198,30,919,235,1000,1000,107,-1000,-1000,386,-1000,961,-61,362,-484,-209,-1000,-431,147,-1000,-719,1000,-1000,1000,25,1000,296,1000,-323,-940,318,585,393,1000,-960,-1000,-914,-1000,-119,943,199,1000,-899,1000,1000,1000,839,-670,-1000,-1000,1000,710,530,1000,-530,-1000,-1000,1000,-224,248,290,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00498() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{349,405,-969,-742,1000,584,319,846,1000,1000,-598,664,-1000,-780,-308,132,448,-1000,-530,-126,-991,-113,-1000,174,504,875,1000,-739,-201,-563,-649,214,120,395,-482,883,-19,-1000,234,140,-1000,1000,702,-1000,547,-1000,-267,-913,521,-612,-317,36,-281,320,367,-144,1000,-557,192,1000,360,-898,-611,954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00499() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:LTB4ODAwMA==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{-1000,1000,149,-137,987,682,-107,557,-137,-1000,486,83,-1000,-205,-534,893,1000,-594,-499,-137,-920,244,-462,1000,656,745,-762,-193,-10,-389,655,-157,-569,-936,-119,808,-851,1000,775,437,-1000,600,820,-1000,38,253,-49,-5,755,196,-350,-838,-283,989,-748,-561,-266,642,-756,445,-290,34,-841,-47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00500() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:X19fR0huMjZIQl82NjZcSVBfNno=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{-933,1000,-565,-1000,347,1000,-641,-1000,1000,1000,1000,635,-994,-295,-970,1000,1000,-797,-987,1000,-1000,875,-1000,365,1000,1000,-630,-787,-1000,-1000,-739,824,-332,-495,1000,469,-176,-1000,1000,1000,-207,494,967,-730,-1000,1000,958,-1000,827,-263,-1000,-934,1000,1000,-1000,-736,-5,-1000,1000,525,289,-362,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00501() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:MHg4MDAwMDAwMDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{108,479,59,-1000,-676,-303,822,-100,-171,440,644,174,-681,-745,-813,885,-501,1000,173,1000,-635,1000,-1000,401,536,1000,1000,-1,-1000,-1000,-1000,-392,770,359,-218,1000,-903,-205,737,727,-382,-413,1000,793,680,-1000,1000,-1000,1000,-382,-135,-351,-999,-683,-56,-213,1000,-625,1000,-46,939,-807,-62,919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00502() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:MHg4", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{-1000,1000,313,-508,1000,-1000,-921,-1000,885,150,402,1000,-1000,-284,-1000,1000,705,-65,-343,1000,-766,1000,-1000,1000,868,-400,-1000,-821,-600,-776,-739,1000,-169,-784,1000,1000,-615,913,1000,1000,-1000,1000,1000,-990,-467,1000,-508,-1000,852,170,-689,-1000,1000,1000,-1000,-606,-1000,-1000,465,1000,969,-1000,-334,243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00503() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{-1000,1000,-652,-196,1000,-1000,-312,-531,985,178,-502,1000,-955,-469,-1000,977,169,-305,204,1000,-766,1000,-1000,1000,656,-96,-926,-1000,-307,-995,-1000,-1000,464,-1000,1000,947,-1000,78,1000,1000,-1000,1000,931,-980,542,603,-1000,-1000,1000,286,-800,-938,1000,1000,-1000,-76,-559,-1000,507,1000,20,-1000,-1000,218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00504() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:ODIybA==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{-652,1000,149,388,1000,-1000,34,51,975,-1000,-887,1000,-1000,-415,-1000,1000,1000,-162,-215,822,-1000,1000,-624,1000,1000,785,-1000,-777,122,-1000,1000,-1000,-1000,382,808,1000,1000,1000,1000,1000,-786,1000,970,-1000,-186,1000,779,890,367,1000,-1000,-43,1000,1000,-1000,-908,-1000,450,-569,1000,-1000,-1000,-1000,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00505() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:LTg3Ng==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{993,660,693,-1000,675,598,1000,867,-1000,439,78,-60,22,-870,771,-876,197,-763,-996,-1000,-879,-410,400,164,41,354,1000,1000,-874,-852,-1000,1000,-714,-569,-736,-187,1000,-20,-292,-1000,-277,805,229,-454,790,21,622,-522,927,-171,1000,458,-1000,-1000,-680,328,-1000,-924,382,-1000,555,1000,1000,490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00506() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{-1000,1000,684,-137,1000,639,-341,-344,1000,1000,1000,821,-1000,379,-319,280,1000,-1000,-654,-1000,-935,667,-1000,963,910,664,-712,-1000,-711,-720,-965,1000,-566,-608,99,194,-667,-1000,1000,428,-228,381,1000,-890,-1000,784,1000,-504,431,-812,-139,-1000,551,-1000,-313,-780,57,-881,666,558,1000,-865,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00507() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{-63,601,-609,-443,-885,658,841,926,-1000,894,816,-906,-519,-366,68,-41,629,-1000,-841,-922,-1000,-1000,1000,-716,313,721,1000,1000,-260,-342,-108,1000,-465,-1000,147,716,-51,-1000,210,-221,-309,600,549,-985,239,-705,1000,-961,-129,-433,61,-425,-675,-265,395,-330,1000,126,-243,-1000,24,836,570,713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00508() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:KzY5NmUtMzQ1", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{-803,916,684,-137,631,612,694,-803,-1000,894,486,209,-899,-366,-125,145,1000,-716,-1000,-420,-935,-1000,-100,-348,678,696,-762,-345,-1000,-955,-913,1000,-501,-1000,147,260,1000,-1000,810,120,-228,600,842,-277,-801,1000,1000,-504,98,342,137,-970,-156,-1000,-313,-968,-557,126,467,-1000,819,1000,-550,584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00509() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:LTEwMDA=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{-148,1000,144,-1000,593,1000,1000,74,-1000,560,23,30,-563,-584,359,-365,947,-1000,-1000,-1000,-1000,-1000,1000,599,458,1000,638,582,-963,-931,-778,1000,-583,-1000,-736,10,1000,-1000,400,-482,-90,600,609,-257,-348,21,1000,-428,1000,74,490,-410,-1000,-1000,-680,-714,701,-1000,382,-1000,281,1000,-119,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00510() {
        org.junit.Assert.assertEquals("VOID|getLabelURL=java.lang.String:LTB4M2U4", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setLabelURL(java.lang.String):void",
            new int[]{615,12,-781,127,-1000,670,-787,396,-552,-1000,1000,-464,480,-604,-297,1000,-75,-1000,-718,148,-119,285,1000,-1000,922,-715,1000,-125,-21,-508,-1000,930,840,428,421,-252,-1000,345,464,-437,1000,-1000,313,641,-585,-1000,-1000,-547,484,467,60,353,-94,-190,763,-270,-10,-626,-572,368,569,-100,526,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00511() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{-1000,-660,-576,-451,1000,630,563,627,317,-204,-638,-941,-745,-295,145,-733,322,-652,-685,815,-241,-231,301,126,755,-1000,273,742,-222,-631,565,-850,130,-109,-1000,264,-472,1000,656,-449,960,761,836,-226,1000,945,407,494,-246,-583,-861,-747,-480,-363,-980,-1000,386,-475,92,129,-145,-992,361,318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00512() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{463,-123,293,-1000,331,675,1000,941,-213,-1000,-370,1000,-869,-473,-688,451,1000,483,292,160,383,653,898,219,74,693,419,-99,816,401,-277,707,-1000,-617,-1000,-1000,-1000,503,632,-410,-625,497,-310,40,232,-226,-1000,936,1000,-352,-1000,556,-727,659,1000,592,-1000,354,1000,655,1000,-544,-191,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00513() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{-1000,-115,-499,421,128,-160,281,6,263,573,-76,-1000,221,-1000,246,-765,450,-164,-924,999,415,-881,131,704,-1000,-929,-135,635,78,3,-283,-88,1000,-657,-499,861,784,272,589,-682,676,332,778,692,1000,1000,595,-275,-492,-1000,153,-617,-1000,699,-1000,-84,1000,-1000,-2,-782,716,-321,807,149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00514() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{-1000,1000,99,1000,-289,597,-367,677,-964,1000,844,-1000,-624,766,19,-834,326,244,-738,304,-212,-888,-343,7,-17,-985,-267,-555,320,-465,-15,-350,1000,438,1000,1000,1000,-419,9,-1000,228,-912,1000,-168,74,-61,1000,-1000,-1000,-1000,1000,-1000,750,330,-653,-750,1000,-643,-562,-1000,742,474,-1000,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00515() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{305,-1000,-127,-854,-120,424,484,498,224,376,194,28,-856,622,315,-1000,643,245,-322,151,-801,-358,427,-842,146,-326,-338,1000,-1000,-625,-617,-1000,-802,-261,486,24,-299,134,-1000,-917,342,1000,870,1000,-133,-722,-18,-262,-316,1000,-799,-1000,-1000,-1000,225,-603,-130,761,-1000,1000,-304,502,-44,57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00516() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{-719,1000,-81,-244,962,304,883,542,438,-1000,-1000,542,-157,-1000,-476,727,-6,-69,-94,700,1000,-266,669,949,284,-1000,581,-672,-1000,929,-415,1000,-425,-548,524,-959,-548,124,1000,-195,-312,-584,-77,111,1000,332,-987,-172,193,120,-925,481,-514,1000,757,1000,-194,-1000,1000,72,1000,-486,14,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00517() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{901,-688,-911,-1000,-291,-646,562,834,-708,54,439,1000,181,-1000,933,-635,507,-58,616,507,10,-309,795,364,-910,-326,241,1000,-1000,-409,-1000,466,-1000,-224,171,-244,-590,-52,-841,-357,-83,722,520,761,-739,678,-1000,-159,872,1000,-905,-156,-1000,-554,1000,797,-1000,1000,328,1000,263,801,73,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00518() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{132,90,89,81,178,695,385,-675,-948,935,705,-141,-1000,1000,34,-566,939,392,360,92,-221,-218,276,-478,-34,588,39,-304,-401,-1000,-782,-79,-479,298,396,540,442,-77,-655,-1000,-203,-366,1000,352,-534,-1000,164,-1000,355,563,-728,-892,-614,-95,-18,-93,-50,740,-400,488,-1000,449,-1000,-219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00519() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{604,580,774,-1000,-1000,-308,373,795,599,-955,776,-124,222,-1000,1000,334,449,-725,-328,228,-255,-506,977,659,-527,76,-113,400,-400,1000,-125,414,-1000,-1000,1000,-940,206,-436,-145,-743,-416,400,60,1000,99,-1000,-261,226,-1000,31,157,-515,-1000,-74,1000,1000,-1000,-1000,715,958,-480,-214,1000,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00520() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{112,160,878,395,783,1000,-193,-622,1000,-271,-682,-281,-1000,1000,-137,577,853,-668,-947,452,-1000,819,-285,162,235,714,37,-1000,840,1000,893,431,823,-478,748,-799,1000,838,718,605,-519,-927,-292,-744,1000,-1000,613,-530,-993,-276,703,-1000,1000,1000,281,-837,602,198,-247,-1000,-499,122,430,412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00521() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{-1000,606,363,451,-612,223,244,11,1000,1000,-531,-1000,115,-1000,514,-1000,733,55,-1000,-918,-1000,-971,565,-288,-162,-1000,621,515,-560,-916,224,-1000,440,-918,-130,1000,1000,30,267,-878,1000,1000,1000,-1000,1000,114,83,-205,-1000,64,966,-187,-522,-312,-1000,-747,1000,-1000,-1000,37,920,301,994,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00522() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{-444,-641,-725,-916,878,-252,538,737,-994,-262,-38,230,-62,-783,-6,-627,449,-526,263,228,237,-123,690,774,-344,-608,413,773,-976,-592,-125,130,-525,323,-845,-31,-816,719,34,-82,385,575,630,-388,85,957,-482,325,619,-125,-848,-194,-727,-407,177,-104,-933,598,715,308,-480,-395,86,-938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00523() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{-343,-895,-554,-987,1000,-678,739,134,-1000,-1000,-273,715,247,-654,-80,-467,127,212,250,1000,1000,41,401,1000,-1000,-81,-378,813,-25,374,-878,1000,-186,-143,-930,-496,-737,322,629,-82,-348,476,210,1000,56,1000,-5,149,619,-1000,-848,-321,-1000,958,455,1000,-1000,598,715,308,22,-784,360,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00524() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{-387,1000,-152,968,878,1000,440,22,-564,664,79,-1000,-1000,-783,-968,-287,764,967,-612,-286,40,-353,132,-1000,-344,320,812,-1000,1000,-34,774,30,1000,-556,185,864,1000,100,1000,-981,370,-761,947,-388,1000,264,844,325,-6,-1000,-674,-651,619,1000,-1000,-1000,-933,-401,680,-1000,87,-484,-523,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00525() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setPlot(org.jfree.chart.plot.Plot):void",
            new int[]{-66,-759,-999,-303,617,-80,94,-675,-948,715,-833,-600,-381,562,-683,-412,768,1000,111,1000,135,-705,281,-534,454,-1000,71,1000,-936,-1000,-567,-595,-397,356,-1000,915,-281,251,-765,-1000,912,1000,867,1000,279,1000,47,-1000,571,1000,-988,-720,-758,-833,-219,-268,953,49,-800,1000,165,363,-378,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00526() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{1000,-252,239,-822,-495,530,-684,453,569,900,-1000,1000,-612,-251,-1000,-1000,656,-813,-478,-792,997,76,-880,270,1000,-361,1000,446,698,-238,-1000,1000,655,-585,1000,1000,370,-1000,-291,-106,-1000,-745,-817,-1000,-709,-1000,-470,-1000,-17,-331,1000,-1000,-145,1000,695,1000,-677,421,1000,488,165,-302,-696,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00527() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{-1000,-21,232,98,754,584,349,-829,-153,583,1000,505,182,259,1000,1000,480,51,-71,-680,-466,-1000,301,841,-1000,292,64,964,1000,466,1000,-243,861,169,126,-810,-1000,1000,549,-170,1000,-400,1000,986,-1000,546,400,-400,-1000,1000,-1000,668,-661,812,-299,-1000,-1000,-529,-915,1000,71,1000,330,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00528() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{-393,1000,232,-1000,-211,1000,349,-697,1000,1000,-400,1000,375,259,-638,216,480,-199,485,-712,69,-726,235,937,-820,1000,-1000,850,1000,1000,427,1000,-486,264,288,215,83,357,1000,-170,1000,-1000,1000,383,-90,-425,1000,-400,-413,1000,-576,-435,-1000,1000,-614,-1000,1000,1000,-873,1000,304,1000,1000,752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00529() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{-1000,-144,758,-186,694,1000,723,-910,777,-675,-262,1000,-445,11,1000,431,1000,-479,990,-526,995,-1000,-1000,686,-524,-1000,583,232,1000,239,-694,-614,-761,813,400,663,1000,656,369,783,703,-400,1000,-521,-655,-465,109,-1000,171,835,386,-475,3,215,149,-8,-991,-517,24,754,262,891,-702,493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00530() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{-87,-395,-166,-404,-279,-522,-958,765,192,697,1000,80,493,429,-479,-301,148,-69,-1000,-583,-661,-615,111,782,-124,934,-871,625,225,628,475,1000,276,-172,616,182,-358,394,276,-363,-400,303,208,-7,-267,-272,644,-450,-160,216,400,57,-312,918,-696,-1000,847,-196,400,211,-507,-400,45,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00531() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{-675,-230,969,1000,129,-1000,180,-448,-1000,-1000,752,95,-1000,1000,1000,-658,774,1000,1000,-809,684,-816,780,604,299,-1000,1000,576,203,-1000,-998,-1000,-349,494,-1000,-332,541,661,-1000,864,-309,1000,-4,-39,-435,778,-1000,1000,-521,238,156,-40,197,-1000,1000,496,-1000,-1000,454,67,224,-561,-390,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00532() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{20,206,-396,-722,14,751,231,-140,1000,759,926,372,149,-1000,-641,-326,509,48,-938,-720,723,-552,172,661,84,1000,244,446,869,1000,-338,1000,1000,-275,-293,-342,10,245,479,-514,536,104,658,-427,-151,-409,400,999,-17,489,909,-738,38,-176,97,791,-1000,242,533,1000,-332,526,-281,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00533() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{757,140,-396,-124,580,400,426,963,438,-848,23,574,693,-150,-537,820,919,-912,1000,513,1000,-1000,172,-791,-373,1000,-107,-594,590,400,532,631,1000,-1000,-1000,-644,-662,-580,681,239,1000,-400,1000,-296,1000,-1000,-448,-400,879,-507,-193,-405,-621,1000,620,-1000,1000,-125,677,1000,-767,1000,291,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00534() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{-1000,258,819,-269,445,531,929,-981,86,-92,1000,771,-13,-425,1000,1000,790,765,588,-798,6,-1000,979,1000,-1000,628,1000,1000,1000,431,1000,-243,-1000,1000,-648,1000,1000,1000,25,-1000,1000,-862,1000,1000,123,1000,-588,-216,-1000,1000,-1000,728,-1000,-736,-364,-1000,-451,69,-1000,842,161,1000,785,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00535() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{-675,-230,-962,-130,-563,-383,-305,260,-18,659,83,248,-1000,-506,314,-658,989,1000,-71,-1000,493,-90,301,1000,143,-219,888,1000,562,-131,-1000,631,861,746,914,-53,1000,1000,-935,-471,-333,1000,-295,-248,-1000,612,-991,999,-780,753,1000,-425,38,-1000,297,501,-1000,107,496,1000,359,-1000,-390,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00536() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{305,-464,-31,231,-621,-683,-379,819,-93,-505,370,153,-882,-272,-560,-859,511,780,-1000,-816,656,-451,331,688,562,-12,-212,464,21,-551,-1000,633,-44,247,-129,178,1000,134,-1000,-90,-1000,1000,-468,-439,-930,-79,-585,1000,200,-124,1000,-822,-17,-853,461,1000,-669,-44,1000,679,-147,-1000,-757,-667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00537() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelFont(java.awt.Font):void",
            new int[]{-1000,484,827,-252,1000,1000,1000,-1000,-28,872,1000,938,-1000,152,1000,-120,676,40,1000,-667,-922,-1000,756,1000,-1000,487,912,1000,1000,1000,1000,-857,-225,696,-237,-235,-1000,1000,1000,-807,1000,-1000,1000,1000,-263,1000,398,-1000,-1000,1000,-1000,1000,-1000,894,-876,-1000,-847,-449,-1000,1000,287,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00538() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{1000,-556,519,902,-1000,657,1000,-768,852,-29,496,-642,-377,-178,-222,708,512,-579,-1000,1000,-489,972,-167,-312,-1000,-420,829,-1000,-508,1000,-524,947,454,201,-492,-1000,-910,-544,1000,532,16,-691,-1000,1000,387,-867,-348,788,325,912,306,-521,-1000,-121,-776,527,625,47,436,-488,566,465,159,-149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00539() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{1000,-689,298,1000,-1000,-911,1000,-1000,771,-331,-41,-1000,-935,-744,-296,829,1000,-1000,-683,1000,-195,1000,88,260,-1000,-814,-329,-1000,870,1000,-1000,939,317,511,226,-1000,9,-1000,268,1000,199,934,-125,639,235,-1000,92,1000,937,860,215,-718,-1000,-939,484,1000,350,854,-390,684,752,241,391,-206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00540() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{-839,-265,615,105,212,-911,-350,312,771,-459,-750,800,-547,463,801,829,854,-990,38,-827,790,-674,119,-464,917,817,-329,521,870,193,684,-957,-600,894,542,246,9,-600,-485,-834,-898,934,-456,-343,-252,-890,356,438,-166,-816,560,491,-393,746,674,518,350,925,-161,-341,-650,-521,331,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00541() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{1000,-614,554,636,-848,473,1000,-1000,-839,-380,-173,113,-542,-1000,-1000,-400,-359,-360,-1000,1000,1000,1000,-960,-1000,-1000,-1000,-57,-1000,1000,1000,-670,1000,1000,1000,-1000,554,-1000,-1000,1000,861,-79,-70,-1000,1000,-50,1000,1000,1000,320,-201,1000,-20,-898,-1000,151,1000,-782,-1000,-1000,-383,488,56,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00542() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{1000,-1000,-677,457,-1000,-184,1000,-1000,-1000,-129,891,-767,-193,-1000,-1000,-1000,-836,1000,-1000,1000,258,1000,-1000,-637,-1000,-1000,20,-1000,546,1000,-1000,1000,1000,970,-1000,722,-1000,-1000,1000,1000,700,-851,-330,1000,26,1000,1000,1000,1000,758,1000,-1000,-1000,-1000,-1000,1000,-1000,-1000,-487,-820,-1000,845,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00543() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{-839,-845,-509,1000,172,-296,-47,312,607,-883,-173,776,-1000,177,589,1000,423,-839,38,-827,170,-674,863,-827,917,1000,-337,521,249,-675,1000,-976,-600,1000,496,554,-1000,458,-485,-1000,-79,805,-1000,-453,-50,-1000,-1000,1000,-782,-697,714,1000,-524,-1000,674,-206,1000,1000,1000,-773,-1000,546,-42,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00544() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{-11,748,404,-295,-836,380,432,-919,-1000,1000,584,1000,-376,425,-139,-1000,-169,1000,1000,-287,-180,553,-979,-1000,-793,143,-608,-353,761,235,1000,378,681,475,425,511,937,133,382,1000,-842,-121,-579,-368,764,1000,-56,-817,-278,223,-642,1000,-299,-81,908,-669,-865,-559,-963,-288,-371,246,-174,62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00545() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{1000,-425,-940,-705,-846,-196,1000,-206,-223,-492,-580,-92,-105,-344,536,-293,812,150,-1000,1000,1000,538,-361,15,-1000,-1000,-116,-1000,1000,580,-588,1000,1000,57,-780,611,-23,-270,1000,257,-1000,204,-495,1000,141,-495,1000,557,780,-969,708,-245,-915,-1000,506,911,-871,-545,-122,-33,-721,260,76,-15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00546() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{-471,317,1000,-1000,836,-908,-687,-899,176,-244,-324,1000,482,1000,-511,-867,-500,-742,503,-400,42,519,-54,-141,-629,-805,-1000,1000,690,153,573,27,797,563,90,-97,-152,-957,481,-727,46,1000,508,-648,929,-1000,1000,-1000,482,-1000,-547,-1000,1000,-1000,122,-1000,-993,298,-5,-656,130,-1000,628,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00547() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{614,-618,-399,1000,-231,-171,158,-529,629,-1000,-320,573,-381,-869,-77,-11,205,-1000,-782,452,853,612,-279,-625,-1000,272,-67,-681,909,949,213,231,282,896,-307,39,-725,-720,422,20,-1000,532,-778,668,-50,-436,549,1000,-833,-500,1000,1000,-657,-24,1000,706,-774,-408,-1000,-227,-755,577,531,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00548() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{-1000,519,1000,-579,152,-1000,-661,9,-632,786,-1000,1000,-137,1000,-403,-122,83,-1000,497,-1000,1000,312,-310,-1000,-1000,107,-861,1000,1000,1000,-320,-443,329,1000,680,1000,302,-1000,127,210,-355,885,-701,-723,186,400,1000,-183,53,-1000,231,-1000,1000,-651,852,-169,-71,-94,-1000,16,-82,-1000,858,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00549() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{929,-336,289,-970,445,-71,202,-379,-1000,1000,565,608,350,626,-384,-580,-249,36,-545,1000,-1000,251,-593,340,-629,-1000,-1000,-353,-365,153,768,1000,884,-11,-731,1000,-1000,-637,711,-970,69,1000,299,533,1000,732,1000,-1000,328,-323,-494,-1000,86,-927,-1000,-564,-576,-1000,1000,-1000,-145,234,139,768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00550() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelInsets(org.jfree.chart.util.RectangleInsets):void",
            new int[]{1000,-817,1000,1000,36,-159,27,121,738,-750,-245,-246,-158,219,360,1000,1000,-128,400,-526,976,483,927,-354,-1000,204,-905,-155,-522,1000,-662,-837,66,-18,548,-571,-564,452,553,569,-159,-733,64,853,694,-145,-94,279,237,723,759,1000,-421,481,-1000,-1000,1000,839,623,-1000,943,285,614,-743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00551() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelPaint(java.awt.Paint):void",
            new int[]{-1000,181,-328,-264,128,251,-146,1000,334,217,309,-1000,-21,114,-43,237,413,-461,785,753,1000,374,-28,-106,935,88,227,-472,801,14,-87,-210,896,218,931,-335,-404,52,314,-235,507,-1000,513,531,-116,-76,-453,-210,-149,-28,984,712,27,1000,-164,-306,-957,-949,-449,-763,-419,-1000,56,-814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00552() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelPaint(java.awt.Paint):void",
            new int[]{-1000,629,-588,567,-314,-117,-869,1000,-6,-49,1000,-829,-496,-110,403,-519,119,-1000,-347,-359,1000,520,254,-827,530,361,-53,298,442,264,29,-991,-205,-270,1000,-196,-14,-797,240,338,-286,-1000,619,299,219,-110,-28,656,318,-259,-955,784,211,304,-463,-336,-421,-211,47,-234,-784,-400,-314,587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00553() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelPaint(java.awt.Paint):void",
            new int[]{400,1000,-731,-734,-347,524,178,161,368,972,978,-1000,683,1000,1000,-56,375,-880,-161,814,1000,1000,-742,-131,-78,961,-88,-1000,1000,732,-1000,1000,1000,-204,193,451,303,1000,740,-514,-73,400,-325,875,-266,-967,-1000,14,615,-1000,776,10,-23,1000,459,883,-1000,-1000,-493,-1000,-142,-1000,246,-349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00554() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelPaint(java.awt.Paint):void",
            new int[]{247,-459,-617,1000,-154,-553,872,903,-1000,-1000,-159,173,-1000,-842,710,1000,-1000,-749,-1000,-736,-275,913,90,-1000,131,-1000,219,1000,210,683,658,-767,-152,-176,515,-242,-1000,-611,315,-284,575,158,887,902,1000,82,159,1000,-782,539,-692,547,-116,-1000,-986,-297,752,752,-1000,749,-52,842,703,-645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00555() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelPaint(java.awt.Paint):void",
            new int[]{249,-1000,294,-361,-974,-438,-463,938,-845,-999,-61,399,-280,-1000,1000,-208,-532,-1,-614,-901,-998,226,1000,168,348,398,772,951,-561,488,838,-894,-846,219,574,472,-701,-1000,-883,-465,-567,-592,94,-380,1000,20,1000,554,-973,1000,-897,919,250,-795,-823,-1000,425,1000,-223,1000,-579,993,-875,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00556() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelPaint(java.awt.Paint):void",
            new int[]{-1000,-471,-284,1000,-1000,-486,96,1000,-849,-1000,331,1000,-1000,-1000,406,798,-609,-40,-1000,-1000,-378,-106,84,-726,934,-1000,-119,1000,-715,1000,1000,-68,-1000,-282,742,-155,-658,-316,264,26,789,-1000,1000,26,1000,957,1000,1000,-1000,523,-1000,725,32,-1000,-1000,-1000,1000,1000,-1000,1000,401,1000,379,734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00557() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelPaint(java.awt.Paint):void",
            new int[]{-938,86,-298,613,128,230,27,229,12,400,652,-1000,-275,-546,-487,-400,-126,137,-386,-898,1000,1000,-830,58,-190,-176,-1000,-50,-823,158,178,-958,-368,-176,826,753,-449,444,318,277,1000,-75,290,107,1000,943,855,-76,-486,687,-875,969,772,-535,-663,-731,-284,563,-64,617,909,364,1000,-356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00558() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelPaint(java.awt.Paint):void",
            new int[]{912,-104,138,-170,465,-406,308,903,-119,-441,309,1000,-353,190,161,-649,-526,-343,242,63,151,-187,-1000,-775,-194,339,337,-1000,-32,26,81,-1000,-504,-312,-487,875,-24,-559,825,661,329,1000,-277,203,841,-92,100,-757,-370,61,618,-307,1000,-895,-738,-178,21,186,735,-423,251,-31,-471,-757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00559() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelPaint(java.awt.Paint):void",
            new int[]{345,20,-356,1000,426,241,503,794,-469,964,283,385,96,315,167,206,-988,-1000,820,-340,-12,908,-313,-890,-356,-380,241,472,440,799,-294,323,-323,-285,-40,66,-1000,-401,1000,1000,356,688,698,179,1000,-160,167,-120,-353,472,-703,934,-1000,-376,-1000,-152,423,1000,-806,-61,-432,132,1000,-526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00560() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelPaint(java.awt.Paint):void",
            new int[]{1000,34,-23,-1000,1000,463,1000,296,943,1000,15,-132,1000,1000,10,-163,-535,-475,1000,1000,446,1000,-537,15,112,-105,369,-1000,1000,-947,-1000,-1000,1000,-1000,839,47,23,-316,709,-1000,899,1000,-530,1000,192,-1000,-310,-1000,-503,-950,-856,-140,1000,124,829,178,239,-1000,-58,-1000,-429,-1000,176,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00561() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelPaint(java.awt.Paint):void",
            new int[]{-1000,-540,214,374,-224,-262,-278,1000,-277,-321,1000,-33,-588,-918,-349,442,-1000,483,998,867,752,-873,-703,-969,1000,-487,-708,-315,465,201,626,-99,572,949,884,-121,-649,-74,887,385,639,-1000,1000,921,1000,1000,-57,-475,-1000,-672,-468,1000,455,489,497,-621,176,-730,-1000,303,941,-575,-420,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00562() {
        org.junit.Assert.assertEquals("VOID|isTickLabelsVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelsVisible(boolean):void",
            new int[]{-1000,-1000,-831,569,902,293,-220,-1000,230,829,-405,-968,-217,-344,-708,-181,1000,1000,-174,1000,1000,308,-869,235,27,-452,1000,-807,1000,-416,1000,704,1000,174,791,558,-101,-29,305,-338,-961,-1000,345,-1000,-416,806,592,-405,8,819,-602,227,-306,831,768,502,-1000,-392,184,129,-480,693,1000,572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00563() {
        org.junit.Assert.assertEquals("VOID|isTickLabelsVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelsVisible(boolean):void",
            new int[]{347,471,439,-1000,-1000,-1000,1000,358,333,-1000,-281,1000,1000,1000,1000,-943,-400,-546,1000,-126,-1000,-1000,500,-1000,1000,970,-400,1000,-647,486,-430,-964,-1000,-1000,-1000,-1000,660,738,-401,-120,671,1000,-808,1000,1000,-1000,1000,-1000,252,-882,421,-1000,1000,130,-656,-1000,-45,1000,605,-1000,-1000,-264,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00564() {
        org.junit.Assert.assertEquals("VOID|isTickLabelsVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelsVisible(boolean):void",
            new int[]{1000,-130,-866,-613,-1000,920,5,-1000,1000,400,918,-1000,-420,-1000,-1000,771,1000,676,106,692,-408,-875,-1000,1000,-525,-812,-1000,-323,1000,141,303,569,-1000,-1000,1000,108,363,137,1000,755,183,-1000,-202,-1000,879,-380,605,-1000,169,820,-1000,-1000,-1000,970,59,31,-388,-712,-875,-618,-943,1000,-222,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00565() {
        org.junit.Assert.assertEquals("VOID|isTickLabelsVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelsVisible(boolean):void",
            new int[]{-1000,-774,-991,36,888,293,-75,-991,33,829,17,-968,706,0,-1000,-566,1000,1000,-435,881,827,221,-573,235,-56,-553,1000,-892,580,-642,1000,1000,860,140,1000,1000,-101,-127,532,-1000,-914,-396,266,-965,-456,838,607,717,348,650,9,396,-534,830,856,222,-1000,-583,322,460,-935,669,868,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00566() {
        org.junit.Assert.assertEquals("VOID|isTickLabelsVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelsVisible(boolean):void",
            new int[]{400,69,-337,-1000,-365,-1000,894,978,-179,-1000,-427,1000,1000,839,253,83,-1000,-549,480,-195,20,-618,516,-1000,1000,10,-1000,-68,20,-152,-955,-400,-59,-320,-1000,-653,952,388,-1000,1000,288,-418,8,1000,1000,-155,320,-1000,12,848,169,698,154,433,321,72,220,329,-897,-653,594,-1000,-309,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00567() {
        org.junit.Assert.assertEquals("VOID|isTickLabelsVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelsVisible(boolean):void",
            new int[]{-1000,-1000,159,36,1000,-204,47,-1000,-137,-478,-418,-400,706,0,21,-566,-716,1000,719,1000,1000,477,-589,-499,546,-799,-56,-1000,1000,-418,537,1000,1000,937,141,493,697,-231,-832,235,-1000,-1000,754,-1000,95,1000,855,-567,-696,1000,9,1000,-939,1000,970,1000,-1000,-63,-405,652,1000,-542,1000,-63}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00568() {
        org.junit.Assert.assertEquals("VOID|isTickLabelsVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelsVisible(boolean):void",
            new int[]{-1000,-460,-511,1000,1000,281,-575,-1000,-460,730,-1000,-480,706,-1000,1000,-1000,-701,291,-141,269,1000,-618,359,-460,546,-730,515,-1000,20,-689,1000,1000,1000,1000,605,522,952,388,-939,-1000,-1000,835,813,1000,-518,1000,1000,-1000,-484,852,-682,1000,154,1000,354,1000,-1000,-715,667,1000,1000,-1000,1000,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00569() {
        org.junit.Assert.assertEquals("VOID|isTickLabelsVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelsVisible(boolean):void",
            new int[]{669,-43,46,430,11,-552,541,-32,290,917,-512,627,742,259,151,-674,-116,-1000,370,-533,204,-1000,-128,-152,602,-656,284,-612,256,-877,506,744,37,351,-456,513,572,370,-391,-219,-319,629,-281,-564,630,66,932,-1000,-71,492,-601,-101,23,706,-263,49,-448,-20,294,-61,-686,-342,209,132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00570() {
        org.junit.Assert.assertEquals("VOID|isTickLabelsVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelsVisible(boolean):void",
            new int[]{-1000,-1000,-763,-331,1000,-300,897,-963,-309,-1000,-260,320,1000,770,1000,-279,990,764,1000,-877,1000,534,-974,-220,917,-216,1000,-1000,1000,-1000,1000,1000,1000,1000,-755,587,978,-199,-1000,85,-1000,-116,795,-1000,-216,1000,1000,271,-1000,1000,-694,1000,1000,1000,217,596,-1000,-13,-80,347,1000,297,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00571() {
        org.junit.Assert.assertEquals("VOID|isTickLabelsVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickLabelsVisible(boolean):void",
            new int[]{-543,-630,939,-532,-498,293,-65,-224,540,1000,347,-1000,-683,-506,-1000,783,580,81,-636,474,251,518,-650,1000,-280,-1000,580,-724,753,-628,1000,499,130,-579,1000,896,-147,102,1000,-21,-281,-1000,-74,-1000,910,-90,-122,717,661,1000,-1000,-107,-1000,748,656,152,-1000,-924,-702,442,-794,485,1000,862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00572() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:LTkuMjIzMzcyRTE4", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{728,251,421,794,306,778,734,-310,809,-286,-690,-143,-43,76,-590,-138,-416,-195,-170,308,570,-590,720,-646,392,-240,745,-510,-302,-681,-1000,-611,-717,-1000,-381,198,1000,437,-350,537,-940,-949,-860,781,-832,-542,-195,405,190,-644,168,1000,-8,-1000,257,-1000,495,163,-991,-840,211,149,240,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00573() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:LTM0OS4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{-1000,251,457,661,202,1000,734,-714,-954,3,-522,-143,183,85,-349,-61,-195,-1000,-303,308,1000,-1000,524,-426,542,-697,789,-835,-342,-725,-1000,-961,-717,-1000,-246,276,1000,241,-385,778,-938,-812,-645,827,-771,-542,-96,436,-457,-219,252,-441,-8,-1000,99,-51,495,316,-1000,-553,211,640,-94,-952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00574() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{-1000,899,-704,272,-1000,983,774,102,1000,-753,-259,725,679,-451,-1000,86,620,-95,543,-1000,-472,-70,-1000,-554,-381,718,-152,151,-783,-820,-538,-47,388,267,201,-927,942,428,-253,1000,-1000,1000,880,-362,-362,1000,568,-1000,85,-1000,1000,500,-443,-326,-291,-1000,-1000,312,1000,-889,454,276,-61,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00575() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:OS4yMjMzNzJFMTg=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{-400,-213,-252,280,-725,579,-384,718,1000,-1000,-1000,-1000,-389,150,1000,-580,1000,685,-808,-1000,-1000,49,55,761,-953,1000,593,63,-979,1000,1000,951,-739,-531,-423,-1000,-531,744,-1000,-58,792,1000,-782,-1000,-1000,618,800,118,143,-1000,1000,972,-1000,-1000,-1000,-1000,-1000,-1000,678,-1000,-1000,1000,-764,-911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00576() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{728,1000,79,-269,-297,1000,670,-1000,879,424,326,718,1000,198,-419,1000,416,-583,377,-919,1000,-1000,-1000,-74,230,802,29,-393,-312,-1000,-1000,-1000,-251,459,423,-803,705,-213,-232,1000,-994,1000,1000,684,-788,1000,1000,1000,-466,-132,1000,-51,-140,-1000,-936,-1000,-114,1000,981,282,1000,900,-952,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00577() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{1000,380,854,9,756,-480,239,532,1000,-194,-405,1000,301,-1000,136,-1000,-774,-410,1000,-1000,-468,1000,402,-971,-76,510,1000,910,16,33,-1000,-272,-1000,-1000,-1000,-5,101,723,-269,-891,-697,-261,1000,388,-1000,1000,-1000,760,1000,-918,-71,-1000,-20,-717,-1000,513,1000,183,1000,282,-89,-1000,890,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00578() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{181,-1000,-706,542,44,-316,-379,-307,979,404,-556,-1000,-1000,177,-382,-61,763,752,-1000,705,489,-223,778,-426,-557,-992,789,-1000,-31,-1000,1000,440,202,-541,674,-507,-405,-378,-393,-189,755,-1000,-434,-1000,581,-1000,-683,-256,-104,1000,-1000,-54,173,208,1000,568,-445,-392,-700,897,-857,-760,753,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00579() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:MS4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{-384,703,1000,663,-424,-804,-192,-237,-56,-162,-333,309,343,952,-1000,-827,-427,793,-1000,500,447,-95,402,-1000,-1000,588,-291,411,351,-1000,-164,-261,-289,420,-274,659,821,-177,348,821,1000,-391,455,-878,453,23,854,284,897,-1000,-781,-14,-406,-271,248,-926,82,692,261,-630,-237,-390,439,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00580() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{1000,403,-250,1000,391,-1000,834,-767,-263,-294,-1000,-495,-942,-1000,-905,-775,-1000,-730,-800,950,-836,576,1000,340,815,1000,1000,-1000,-194,-933,1000,-1000,-1000,-786,-550,101,570,-382,-665,1000,556,-1000,-1000,-346,724,-933,-1000,612,794,624,-698,-1000,-565,-178,105,1000,2,-1000,-1000,-378,-480,65,1000,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00581() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:LTEuMA==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{162,1000,-964,849,-46,247,-1000,-725,1000,583,-756,-1000,377,-575,-551,280,-207,-1000,551,-622,-400,-853,-374,-866,1000,363,-774,-904,-523,-1000,195,-661,-242,-445,-521,278,360,-13,-317,1000,860,206,-151,-175,146,-1000,152,-811,866,-654,-10,693,-1000,1000,-311,-17,-1000,-114,-533,-829,732,552,-754,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00582() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:Mi4xNDc0ODM2NUU5", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{-1000,-465,-1000,637,-586,675,53,369,573,-812,-966,-858,-55,98,-222,-246,1000,-386,-1000,-226,-498,-265,403,671,-682,849,654,-457,-1000,414,996,730,-282,-531,203,-440,255,525,-880,1000,-1000,462,-81,-1000,403,277,6,-1000,-141,-754,801,807,-687,-285,921,-535,-1000,-1000,102,-1000,-956,491,-665,-967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00583() {
        org.junit.Assert.assertEquals("VOID|getTickMarkInsideLength=java.lang.Float:Mi4xNDc0ODM2NUU5", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkInsideLength(float):void",
            new int[]{588,-281,498,-299,849,-782,-961,582,437,393,-931,-48,-715,-1000,-418,-993,-874,242,277,179,-1000,1000,-264,-1000,976,664,432,-226,101,-1000,610,-1000,-746,-487,-426,-605,-258,540,-775,-482,371,-379,-400,-1000,298,-944,-908,865,1000,697,-157,-652,-1000,-583,-656,425,-322,-801,86,304,-573,-536,961,643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00584() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{-600,-558,-531,849,-871,-188,-1000,406,854,1000,903,-408,883,-579,-438,-1000,-905,238,-762,819,823,605,-290,7,557,1000,-222,513,-372,689,370,1000,366,-942,161,144,-944,743,269,-1000,610,-675,-1000,-777,-577,24,-829,841,690,-37,-700,-919,-643,396,-731,-465,259,175,-1000,589,960,-297,-54,921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00585() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:OS4yMjMzNzJFMTg=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{815,-177,-231,-481,1000,1000,276,445,603,584,-734,-788,-705,-345,937,-1000,399,15,-1000,1000,1000,-879,-66,1000,1000,989,-836,-429,-1000,1000,284,-361,1000,298,1000,1000,1000,-549,-300,582,1000,-1000,-1000,515,-587,1000,-489,1000,1000,1000,-1000,-434,-574,1000,-1000,-1000,558,-1000,-347,-324,573,873,-700,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00586() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{-95,-1000,-501,125,1000,221,-770,208,763,1000,890,-65,177,-147,-273,-1000,-621,-253,-857,934,322,520,441,343,834,1000,-1000,127,98,-492,877,224,-561,-179,-109,-641,560,641,-54,782,442,246,-816,-1000,-132,-64,571,-792,1000,242,-222,-545,-498,757,467,-866,1000,-1000,-285,-27,380,854,-616,289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00587() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:TmFO", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{-460,-616,319,-459,1000,-187,959,624,-642,217,-910,-573,-258,572,-207,1000,180,32,-411,756,113,-233,-521,-41,592,64,-1000,-434,-493,249,825,-562,-301,-400,-1000,1000,-372,-354,-832,254,-759,1000,-120,1000,1000,-248,-400,400,178,813,-567,1000,-826,-534,-378,-642,96,-621,-183,659,590,-8,-655,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00588() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:Mi4xNDc0ODM2NUU5", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{431,-588,-171,173,1000,1000,-739,471,1000,931,814,327,-282,-800,358,-1000,-558,426,-1000,1000,1000,322,717,1000,721,75,493,338,-553,1000,384,1000,779,-682,1000,-388,399,1000,-540,-120,633,-1000,-1000,-737,-864,549,-656,270,600,506,-757,-901,-524,945,-465,-1000,729,-257,-477,143,264,1000,-607,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00589() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:LTkuMjIzMzcyRTE4", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{-191,168,498,699,-400,563,-480,-997,1000,197,327,-876,-570,-511,-388,-601,82,925,-1000,547,-372,-45,-813,1000,1000,956,1000,-398,-1000,49,-216,1000,961,-741,470,1000,-268,-112,634,-514,950,-1000,-674,-699,-1000,908,-801,1000,546,679,-992,-706,195,-129,-875,-926,-419,-379,-389,-301,362,-445,189,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00590() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{-1000,-111,-1000,-523,-1000,19,-976,1000,-1000,1000,-1000,986,1000,-105,-579,148,-333,-525,-113,947,-164,1000,-581,-1000,2,522,-1000,402,686,1000,744,-1000,-914,-117,596,27,-443,890,-1000,660,-1000,220,-1000,-107,64,-1000,-280,-375,1000,-119,5,1000,376,1000,-293,-149,552,-612,-1000,1000,801,-79,-1000,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00591() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{816,443,294,-389,1000,972,569,-581,817,-734,1000,-170,-735,1000,1000,-602,1000,-428,675,648,-1000,-1000,1000,-808,-125,267,-264,-1000,534,-1000,-151,554,-470,1000,-1000,-937,1000,-613,-59,363,329,1000,717,-49,-1000,809,1000,-220,-484,1000,-618,-593,-583,350,1000,-1000,1000,-521,1000,-1000,17,-179,917,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00592() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{-1000,-1000,-746,-1000,-287,596,-216,30,-1000,449,-1000,658,848,1000,428,148,1000,-1000,489,867,-1000,410,323,-1000,85,166,-1000,-658,956,638,370,-1000,-1000,1000,146,-322,917,246,-885,1000,-1000,920,-140,383,-232,-792,1000,-1000,925,382,96,1000,507,881,878,-510,1000,-1000,211,1000,383,-182,-408,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00593() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:MS4w", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{-1000,-1000,-891,4,-728,-807,-860,724,35,968,1000,930,1000,341,-786,-88,-419,-1000,-495,782,-1000,917,424,-1000,378,887,-1000,258,1000,12,1000,-1000,-1000,-86,-1000,-1000,-161,-113,176,-1000,-868,1000,-10,-686,1000,-420,1000,-1000,676,-557,645,1000,-179,104,1000,-174,1000,852,298,669,1000,-168,-652,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00594() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{-756,-623,839,-712,579,504,993,-942,668,-97,893,812,-708,1000,478,107,1000,-1000,364,1000,-1000,92,1000,444,-557,-494,-537,-1000,21,393,899,374,1,1000,-849,-1000,416,446,-517,670,-958,1000,-377,164,-1000,28,1000,-1000,1000,801,-464,78,-754,1000,1000,-523,705,-449,1000,139,-429,225,303,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00595() {
        org.junit.Assert.assertEquals("VOID|getTickMarkOutsideLength=java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkOutsideLength(float):void",
            new int[]{-276,-575,-499,-1000,443,1000,814,-37,342,194,-1000,792,70,392,478,-1000,101,-857,-1000,1000,604,120,631,327,1000,1000,-1000,-661,136,1000,1000,374,1,1000,1000,-235,1000,1000,-641,499,1000,-227,-940,-1000,-284,-148,1000,-1000,1000,880,99,267,-311,1000,1000,-1000,1000,-1000,-232,107,461,1000,-126,551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00596() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{-396,682,919,194,-356,-1000,882,96,-1000,-384,1000,-734,-576,1000,586,1000,-606,905,1000,871,-676,-1000,918,-375,-795,415,659,301,1000,-1000,-715,1000,781,-694,-1000,1000,-681,-1000,-173,-96,1000,-456,-403,-1000,-1000,-1000,-423,-77,1000,-1000,1000,-876,-1000,-815,623,1000,-953,494,-552,-1000,-504,-1000,1000,126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00597() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{352,3,-401,-935,128,954,-288,-90,412,135,-766,604,-594,-485,-244,212,562,-77,473,-144,-74,-779,-666,286,-131,-440,675,122,678,696,78,-323,-919,-580,199,-66,-289,-103,667,-264,-224,432,792,530,730,548,-212,-884,-366,575,-493,768,-925,1,-777,-182,-22,718,972,338,500,998,-895,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00598() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{223,-404,-491,-1000,-1000,491,-611,-1000,1000,906,-1000,-957,-574,-1000,511,-1000,1000,1000,-1000,-314,378,919,-761,-307,-141,-1000,766,-1000,-274,1000,-822,-1000,-1000,795,1000,-1000,1000,1000,1000,595,-489,-436,1000,1000,1000,1000,925,1000,-1000,1000,-406,1000,1000,462,848,-1000,833,-537,1000,-107,-1000,-478,-1000,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00599() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{-496,-111,381,-32,-739,-925,734,-833,573,74,-183,-1000,-216,-831,541,11,1000,80,-295,516,360,206,-164,-1000,-237,-406,893,-887,4,-82,-1000,-50,148,418,600,-1000,702,-467,800,-128,321,-242,126,947,87,805,1000,-744,-447,-43,357,959,74,-802,-111,151,-891,-271,1000,-1000,-1000,-1000,-683,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00600() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{736,333,-625,-955,315,-1000,-421,313,190,-73,-583,358,-1000,-295,246,212,25,1000,473,-166,-1000,-763,-561,57,209,-599,488,-255,835,115,415,-456,-487,-1000,400,-1000,-620,-517,489,677,-203,-69,1000,378,520,-642,-565,-289,-886,575,496,394,-1000,-1000,-704,204,-215,498,986,0,405,-1000,-170,388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00601() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{-628,-902,-471,-116,927,954,333,241,259,-224,-1000,719,362,-29,-1000,7,562,-442,653,167,-676,-1000,-672,867,694,173,1000,828,598,272,242,353,-1000,811,199,868,-289,-249,456,-1000,-1000,1000,-191,530,1000,632,-212,516,-302,716,-1000,633,-1000,364,-272,516,-541,1000,1000,1000,788,-1000,-989,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00602() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{400,294,24,-1000,-1000,1000,-1000,-773,1000,797,-57,-651,-957,-1000,977,-707,1000,1000,-1000,-248,1000,324,-367,-857,-953,-952,18,-543,-775,1000,-1000,-1000,-347,-988,1000,-1000,1000,1000,1000,689,778,-779,1000,1000,-97,628,1000,-1000,-432,309,-53,1000,320,-471,-621,-1000,932,-768,-899,-1000,-574,-126,-689,381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00603() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{524,-494,-128,653,839,-903,37,-356,651,675,103,-207,236,-183,-423,212,-397,-1000,-144,-232,839,-727,-286,749,-604,-440,675,1000,-760,312,-552,89,-429,-652,433,1000,438,286,553,-870,-1000,1000,-675,-118,146,296,580,-884,-366,373,-436,752,939,1,1000,395,-223,1000,972,759,812,377,-393,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00604() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{-1000,-528,439,887,142,1000,-713,-697,209,29,-104,-674,1000,1000,-881,-1000,24,-43,-640,899,-1000,-889,93,117,-95,159,605,1000,-997,360,-1000,-823,-442,-1000,1000,1000,1000,-32,72,-1000,68,1000,-1000,-187,828,1000,1000,360,499,-54,-313,1000,1000,1000,83,246,-1000,92,-980,518,-717,-55,-853,742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00605() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{-983,-1000,543,921,283,-418,-471,-1000,728,1000,-1000,123,1000,-643,-1000,-1000,1000,1000,-1000,-1000,808,1000,868,1000,492,523,-1000,1000,-775,1000,-1000,-909,-347,1000,1000,543,1000,790,1000,689,-444,1000,-1000,1000,191,1000,1000,1000,375,544,-1000,1000,1000,1000,515,-451,269,625,-91,1000,-1000,-499,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00606() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{692,3,85,-624,128,-1000,314,-1,-38,-76,963,-228,-1000,-537,276,1000,-22,896,729,15,546,-474,-666,286,-119,-486,675,-738,1000,-1000,-373,-603,577,-580,-791,-66,-991,-1000,709,921,81,-580,792,-726,-886,-289,-264,-1000,-466,181,1000,-1000,-1000,-1000,219,491,-526,657,717,-1000,500,152,791,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00607() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{1000,-604,-751,-41,658,-1000,-870,-103,1000,1000,-1000,922,-1000,-1000,-976,400,283,1000,148,-1000,1000,1000,1000,1000,835,34,-1000,508,-191,48,218,-1000,-1000,400,-400,-1000,-400,-118,881,954,-1000,416,-19,1000,-567,-185,-400,899,-848,1000,-1000,-284,-400,-154,430,-277,1000,1000,1000,1000,1000,-718,297,-879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00608() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkPaint(java.awt.Paint):void",
            new int[]{-672,-217,-338,-474,-367,1000,893,-368,527,-281,-88,-321,604,286,449,-1000,24,-931,109,949,-1000,-1000,-1000,132,-463,-280,605,612,185,984,-964,990,-283,29,1000,400,708,877,380,-1000,-73,1000,267,-481,1000,1000,768,-1000,-81,-395,-86,1000,207,1000,-1000,316,-841,470,1000,109,90,1000,-1000,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00609() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkStroke(java.awt.Stroke):void",
            new int[]{-168,81,-29,-559,-798,-366,1000,-50,-57,672,-50,-51,-133,1000,702,-331,1000,-219,-1000,-750,-218,1000,579,-57,482,109,-434,-326,483,323,1000,401,279,764,-333,-650,452,158,-28,120,-285,208,866,146,-410,735,1000,281,393,-532,-1000,447,157,-34,-785,428,325,-384,580,996,216,-427,-871,-909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00610() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkStroke(java.awt.Stroke):void",
            new int[]{-1000,-1000,434,190,-993,-1000,1000,498,1000,-533,-806,88,-497,867,-1000,484,613,-1000,-683,-707,1000,145,476,-717,1000,-416,-1000,-843,1000,771,463,570,-265,-398,44,332,9,286,-807,-949,-1000,1000,-1000,42,-1000,-757,995,1000,1000,-456,-683,-192,-430,-95,329,-314,-124,1000,1000,591,-1000,-371,95,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00611() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkStroke(java.awt.Stroke):void",
            new int[]{-513,816,964,-484,-1000,-1000,605,-214,-404,421,357,-575,-100,342,849,215,523,-645,-30,-906,-226,737,1000,-1000,1000,-845,-335,-376,994,955,975,600,261,-534,-1000,-526,-79,-649,28,-882,457,1000,-1000,-693,-742,-1000,997,736,205,-681,-893,-859,-722,-367,126,-372,240,-367,841,592,-599,-625,-76,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00612() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkStroke(java.awt.Stroke):void",
            new int[]{1000,994,199,-143,998,1000,54,-831,-1000,1000,1000,265,433,-750,-821,421,129,-779,-841,295,1000,454,-29,1000,200,443,1000,1000,-1000,633,619,-1000,934,1000,-44,-306,-288,-338,1000,264,111,293,779,-243,1000,-125,-301,550,1000,721,1000,-680,920,-1000,-573,-675,1000,-1000,-115,1000,665,1000,411,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00613() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkStroke(java.awt.Stroke):void",
            new int[]{-956,-607,1000,1000,-27,342,364,968,-797,-654,-298,1000,-781,613,-319,1000,-552,-236,-1000,-400,273,1000,324,-872,-560,-892,-1000,534,66,1000,-296,-266,-910,575,-792,547,-371,-365,623,-1000,264,1000,200,-1000,414,-77,890,1000,-157,-738,-180,1000,368,-848,-52,-444,621,-670,200,1000,700,-502,1000,-965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00614() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkStroke(java.awt.Stroke):void",
            new int[]{1000,1000,234,-921,-321,-817,-1000,372,-384,367,526,-1000,1000,-1000,1000,-667,-435,-535,-382,-222,-1000,804,-874,-259,1000,-1000,-1000,699,1000,773,568,1000,1000,16,-1000,707,-400,-530,1000,153,1000,-164,-568,-1000,-1000,-1000,777,358,-1000,-292,-1000,-684,-1000,412,994,-63,584,-1000,869,-1000,1000,-1000,-611,15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00615() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkStroke(java.awt.Stroke):void",
            new int[]{-192,-738,-42,-479,-90,-586,989,137,576,567,-138,1000,-839,693,-821,890,661,-934,-1000,-652,1000,412,708,557,259,-455,-902,52,488,831,713,319,544,266,116,442,599,191,-770,-569,100,891,632,-29,-489,-160,1000,1000,1000,52,-493,-220,346,397,-639,-486,548,423,-91,677,-982,-68,909,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00616() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkStroke(java.awt.Stroke):void",
            new int[]{-264,-590,-517,-524,-67,-47,785,574,362,509,196,-186,1000,1000,-214,1000,1000,-685,-1000,-1000,1000,1000,531,-506,40,-1000,-1000,-327,-77,509,1000,-78,357,-700,-6,-307,1000,-196,-99,-1000,504,848,468,178,1000,1000,1000,865,393,-258,-1000,405,1000,-1000,-1000,-761,1000,348,-679,1000,-1000,194,221,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00617() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkStroke(java.awt.Stroke):void",
            new int[]{-712,-1000,314,342,-1000,-903,1000,1000,1000,-952,-1000,-51,-1000,1000,-1000,1000,1000,-1000,-1000,-596,1000,150,1000,-627,590,-218,-1000,-1000,520,868,1000,51,-649,-458,523,1000,632,614,-1000,-1000,-1000,1000,-946,572,-720,-145,995,1000,1000,-404,-254,85,157,-844,-341,-762,-315,1000,658,1000,-1000,427,816,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00618() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarkStroke(java.awt.Stroke):void",
            new int[]{-1000,114,434,190,-1000,-1000,1,-575,435,542,-281,-400,-223,393,71,-79,898,-1000,-667,-919,-30,755,-53,-717,1000,-1000,-1000,-1000,1000,753,463,1000,504,-1000,-823,-241,106,-1000,132,-1000,1000,1000,-764,-561,-1000,-757,1000,1000,621,-1000,-1000,-99,-1000,-1000,73,-1000,235,-420,1000,-9,346,-1000,95,975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00619() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{872,700,-976,649,-616,853,717,-354,1000,99,-994,348,184,-676,-367,1000,-866,-129,-27,-523,-457,-183,219,-727,-135,798,-361,-1000,192,-429,331,-75,699,-1000,486,-75,-308,861,-330,-769,-87,1000,-328,-529,910,252,-264,-816,-55,38,115,350,1000,174,-1000,773,87,901,-96,-891,155,-590,406,860}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00620() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{-1000,-638,239,944,595,174,563,577,1000,-1000,1000,967,-1000,1000,-550,-1000,438,294,129,1000,-966,-744,-684,1000,-670,-1000,180,-537,-503,-487,-1000,403,-42,1000,1000,-101,-1000,1000,-659,62,782,-1000,1000,739,-819,955,781,-958,-20,-195,1000,-381,-337,1000,191,-1000,1000,697,-688,1000,-290,-493,-538,377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00621() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{-400,-766,389,111,795,-292,-153,1000,-86,-1000,1000,937,428,471,301,-400,514,-687,333,400,-78,-1000,-926,1000,-836,-143,653,400,-432,715,-354,-1000,868,-1000,346,94,-1000,516,-1000,-809,-566,530,290,-532,-274,-414,-73,172,1000,-209,1000,-1000,-176,379,-118,-1000,-304,584,139,400,672,681,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00622() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{916,-369,79,-181,-95,-963,-1000,288,-1000,-489,-653,-1000,1000,-1000,1000,1000,-467,-1000,864,-1000,1000,-1000,-1000,861,-731,834,749,-1000,141,1000,842,-1000,1000,-602,329,-75,-674,299,762,-769,-87,771,-1000,32,1000,-1000,-264,980,-55,-185,-741,-1000,578,-953,-794,1000,115,-261,434,-1000,-1000,-590,241,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00623() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{-1000,621,-476,-553,-228,-766,-39,894,804,872,375,44,-246,752,89,36,653,1000,-1000,655,-257,-743,-439,-762,1000,-160,334,1000,1000,857,-561,358,369,874,207,-1000,235,-525,145,-77,-728,-383,597,275,-1000,-156,-315,328,357,139,-400,-498,539,-215,-122,309,460,-911,-483,-90,-301,1000,354,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00624() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{-1000,1000,-528,440,-32,657,401,-333,-246,-459,-477,-262,-335,254,-156,1000,-281,907,-1000,380,-672,-727,1000,-480,427,-774,-426,-786,556,664,-77,578,-300,-704,-492,-270,-40,13,-20,-77,431,-534,-235,386,-329,-370,-949,699,-292,-673,938,1000,596,-400,-402,626,291,-42,105,-758,489,-303,673,-598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00625() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{-1000,3,-175,1000,1000,-62,-278,233,1000,142,-397,1000,-1000,1000,-1000,-1000,260,645,-349,1000,-966,212,-841,346,-102,-1000,439,1000,261,-79,-1000,1000,579,772,-1000,-970,-690,-380,-336,803,909,-1000,1000,901,-819,336,240,-954,1000,-319,1000,165,-783,70,-288,55,429,1000,-1000,1000,-888,453,22,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00626() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{711,1000,-1000,-751,-1000,-14,386,-689,-400,1000,-1000,-919,1000,-1000,1000,1000,-1000,169,-800,-1000,328,-165,558,-1000,1000,1000,148,290,1000,734,1000,-1000,1000,-1000,596,511,990,209,344,-1000,-477,1000,-1000,393,1000,-909,-1000,584,-563,232,938,381,1000,-1000,-1000,1000,-875,-480,-114,-758,1000,109,1000,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00627() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{20,514,-976,637,137,-1000,108,-386,-1000,37,-1000,-652,-335,-250,-156,-734,421,757,109,-515,-295,-554,-241,-480,427,377,96,-438,441,664,373,578,-257,-1000,-492,34,587,-93,513,-349,107,-188,-558,580,790,-327,-173,77,404,-233,-642,348,-18,-760,280,780,-14,-284,-956,-20,-25,-561,673,-598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00628() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{-1000,-812,74,388,1000,-1000,-1000,1000,465,246,722,875,-113,986,-629,-116,1000,-629,972,862,-248,-1000,-1000,1000,-518,1000,1000,1000,469,1000,-1000,1000,1000,1000,-505,-642,-1000,-628,-1000,-934,-1000,-808,1000,1000,-1000,-458,-519,295,1000,-641,799,-1000,-1000,-373,-869,-37,652,-244,-1000,285,-1000,72,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00629() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{1000,522,-586,-100,272,-452,-92,140,89,413,-323,-68,-55,672,-98,1000,361,97,-443,649,-428,-906,841,-290,287,507,420,522,977,481,-105,630,661,-504,151,-591,-258,-706,-323,-563,-1000,-399,-606,140,-397,-915,-811,1000,822,-423,1000,-1000,41,-655,-893,690,395,-542,-136,-901,-8,-374,-709,-565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00630() {
        org.junit.Assert.assertEquals("VOID|isTickMarksVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setTickMarksVisible(boolean):void",
            new int[]{-1000,1000,-557,623,-608,357,1000,-848,789,274,-283,766,-648,519,-1000,-371,173,886,-1000,977,-687,183,757,-1000,92,-631,-377,839,-25,-592,-368,935,-204,-941,-810,-1000,-129,185,-1000,369,882,1000,223,353,0,-20,-163,-363,-225,-289,930,960,103,1000,-356,279,749,1000,-703,642,854,-253,124,835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00631() {
        org.junit.Assert.assertEquals("VOID|isVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setVisible(boolean):void",
            new int[]{-919,-946,-41,-295,536,-308,-528,-343,-503,634,870,913,292,743,283,839,-87,5,104,202,-34,974,-864,910,881,641,675,410,638,497,-861,440,112,116,195,-936,524,-869,281,-651,-627,147,835,-586,-194,815,622,-911,359,-928,133,-17,-290,-608,-832,338,214,-246,464,-245,775,-976,505,-407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00632() {
        org.junit.Assert.assertEquals("VOID|isVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setVisible(boolean):void",
            new int[]{132,924,-491,737,-738,428,-499,370,-18,1000,150,-581,-1000,163,-852,1000,-760,240,-944,-505,336,1000,-1000,846,-263,1000,616,507,1000,843,-1000,464,231,-757,20,-460,521,-351,-94,-900,-1000,323,381,-626,145,353,1000,291,84,-287,-431,743,1000,144,-1000,-57,1000,-888,-1000,-1000,931,-892,129,455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00633() {
        org.junit.Assert.assertEquals("VOID|isVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setVisible(boolean):void",
            new int[]{-737,645,854,275,41,-868,-1000,122,939,534,1000,272,1000,-137,32,154,-988,-129,878,-594,757,-805,1000,-971,335,-661,102,281,313,-1000,450,-788,814,597,226,170,118,-982,448,623,55,276,-904,587,-274,-1000,-1000,103,647,311,-601,-22,952,-1000,-457,780,-1000,691,490,-634,-927,-1000,1000,-474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00634() {
        org.junit.Assert.assertEquals("VOID|isVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setVisible(boolean):void",
            new int[]{1000,92,904,94,1000,550,-715,-301,-477,634,1000,52,675,-229,108,-322,351,-549,-1000,-46,556,307,294,-232,1000,547,702,-242,-587,-301,1000,-294,-463,429,412,-752,622,36,533,475,-896,112,-688,934,-258,-382,-672,-234,-251,-621,1000,-152,1000,139,1000,601,-717,203,507,-569,-1000,-470,844,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00635() {
        org.junit.Assert.assertEquals("VOID|isVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setVisible(boolean):void",
            new int[]{523,1000,-776,1000,-1000,647,-412,388,391,676,397,-1000,-1000,-875,-1000,923,-310,110,130,208,621,-413,-72,-748,-685,1000,-105,834,1000,-22,-805,20,-790,-1000,136,-523,272,-358,-9,807,-978,835,-233,-109,1000,-760,761,924,734,211,497,1000,484,-817,-694,-173,97,-784,-877,187,-332,429,874,-757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00636() {
        org.junit.Assert.assertEquals("VOID|isVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setVisible(boolean):void",
            new int[]{-1000,1000,569,-262,-1000,-1000,-1000,316,-503,634,580,475,-443,-12,1000,1000,-1000,5,1000,202,-1,974,1000,-418,-495,-1000,-1000,1000,-199,1000,39,1000,112,-350,195,-74,1000,-368,281,789,-627,147,1000,-1000,-126,-566,811,1000,-561,-636,133,-174,-1000,-608,-661,1000,247,-246,-1000,-1000,1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00637() {
        org.junit.Assert.assertEquals("VOID|isVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setVisible(boolean):void",
            new int[]{-1000,236,-797,300,-746,-894,-561,430,83,774,1000,667,-773,161,617,-38,120,692,201,-625,-298,308,-290,468,666,-664,-792,-1000,94,-236,-665,117,212,-347,-385,-186,99,-919,-1000,49,-1000,431,1000,-762,-466,-24,173,-118,1000,735,-1000,515,-672,705,-803,74,601,49,664,319,1000,728,167,-281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00638() {
        org.junit.Assert.assertEquals("VOID|isVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setVisible(boolean):void",
            new int[]{-507,879,121,-34,-658,470,-596,-161,-400,374,478,-46,-622,-1000,-840,1000,-750,-85,-676,498,910,-49,7,-279,-363,480,410,526,504,-76,-1000,640,944,-929,531,-816,104,-15,294,156,-27,84,400,-332,975,-175,778,30,126,-839,420,240,-1000,-855,-820,369,697,-173,-896,798,310,45,1000,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00639() {
        org.junit.Assert.assertEquals("VOID|isVisible=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setVisible(boolean):void",
            new int[]{293,-600,122,-206,65,917,-813,-67,-729,-617,136,-647,778,-1000,-1000,-853,838,-1000,113,-793,1000,1000,1000,-869,676,1000,731,-197,-1000,-1000,250,852,667,-508,851,-1000,611,-1000,407,1000,822,739,-1000,914,1000,-1000,-793,-217,220,-732,1000,-1000,1000,397,1000,-601,1000,949,193,-400,-826,-325,144,-355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00640() {
        org.junit.Assert.assertEquals("VOID|isVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setVisible(boolean):void",
            new int[]{-553,1000,-532,494,-609,-1000,-933,337,-138,446,-211,602,-350,192,992,154,-949,1000,812,-711,-340,515,295,841,-90,-636,-453,987,14,1000,267,876,-1000,1000,-1000,46,1000,-1000,728,261,-216,-823,987,-1000,-1000,-606,-278,1000,21,555,-1000,-6,737,-300,486,279,-88,-1000,122,-1000,1000,-1000,390,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00641() {
        org.junit.Assert.assertEquals("VOID|isVisible=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.axis.Axis", "org.jfree.chart.axis.CategoryAxis,org.jfree.chart.axis.CategoryAxis3D,org.jfree.chart.axis.CyclicNumberAxis,org.jfree.chart.axis.DateAxis", "setVisible(boolean):void",
            new int[]{-850,-384,-442,633,-834,-725,32,-167,-1000,-878,-218,672,899,-1000,-413,-74,432,-284,79,-1000,1000,1000,-211,-1000,356,659,-442,-1000,-763,-1000,-675,254,-55,66,291,-715,563,-896,-1000,1000,1000,1000,160,240,380,-892,-374,-1000,970,-173,389,-298,806,1000,920,683,-823,870,706,-348,-9,1000,665,-898}));
    }
}
