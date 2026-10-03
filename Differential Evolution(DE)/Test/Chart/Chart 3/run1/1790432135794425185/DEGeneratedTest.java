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
            new int[]{-1000,-1000,-32,-83,-921,959,272,117,-340,-665,488,330,-104,-251,-792,946,-96,22,449,-897,544,-60,-170,475,1000,-252,-642,-314,403,985,771,-1000,1000,412,-428,-468,720,60,680,-133,1000,1000,340,-589,-573,137,-100,-153,110,-422,-18,-56,256,610,-919,704,18,-124,364,-1000,-209,135,170,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{-1000,-895,-201,154,-1000,-76,54,-155,-187,-207,0,-1000,89,137,-668,523,529,363,77,-1000,106,-3,-12,0,-612,-335,-270,0,-53,0,273,-553,286,-429,-113,-49,270,215,313,-181,-103,669,329,-581,-338,-112,-598,-698,-272,-1000,-71,241,0,394,556,417,137,-328,208,0,-60,0,494,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{-729,-384,-778,-45,-453,923,-203,-836,641,262,506,-305,-587,837,971,964,-405,-502,135,-809,643,-699,755,512,782,906,-843,63,455,530,64,735,156,902,525,710,-197,140,-506,-989,-817,-171,452,-711,782,-325,-393,-120,-641,98,-311,740,986,-151,-903,-706,-41,-662,285,-524,997,-272,-357,-746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{-1000,-1000,-542,-445,-730,84,1000,82,644,-637,598,-9,-403,-342,-631,1000,-413,22,-580,-601,356,1000,852,897,264,-372,-671,606,559,995,473,-1000,1000,1000,512,839,95,791,384,-133,1000,293,776,-638,260,-664,-421,-802,292,-806,659,-717,-69,537,-829,179,154,126,643,400,-81,-729,362,448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{-729,-772,-402,-182,-1000,-341,424,547,-207,-751,-691,118,-1000,-327,519,1000,-825,-1000,135,3,643,929,755,1000,896,179,-1000,993,-513,1000,-444,156,-47,902,789,456,-1000,-950,751,-381,166,-1000,350,-45,49,-180,-586,-431,662,-203,-855,-498,-420,492,644,-531,-1000,-662,621,701,652,-183,174,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{-201,-229,1000,349,-1000,779,-17,327,-669,-486,642,-521,-29,-476,772,495,544,389,578,-475,705,-165,-142,599,937,-476,-296,80,-55,389,608,-1000,531,-312,-104,-380,804,-258,635,-437,1000,1000,704,-112,836,131,-681,-682,377,5,-146,-339,139,955,-412,193,-124,-421,366,-488,87,106,765,-23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{565,-58,-560,-397,-368,374,897,514,-666,-742,276,326,641,-40,-6,253,-792,-928,176,-543,872,711,-21,980,967,-584,23,382,-436,840,634,-745,600,74,913,-873,-47,11,74,-557,-52,410,-589,289,-449,535,364,313,529,109,-391,893,253,-843,-86,-951,-523,819,-460,-229,448,660,-586,-466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{840,-909,309,655,-491,-2,959,51,-476,799,-216,-169,-288,-365,455,-336,-119,-841,901,677,765,174,368,573,733,-256,-592,817,-599,227,67,-133,-859,-628,780,723,-426,-844,-25,278,-426,312,273,-84,52,686,-736,-421,842,476,-652,-840,401,167,-113,-532,-324,-433,-3,248,-468,719,-278,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{1000,1000,-497,761,-1000,944,1000,1000,-1000,1000,1000,544,-893,-1000,-646,-466,1000,1000,1000,646,1000,-506,-175,1000,171,47,1000,1000,-1000,-1000,-691,14,-1000,-1000,782,-583,-305,-1000,-1000,-553,-144,280,1000,955,-393,662,-574,-86,598,-264,-562,-1000,-313,1000,1000,-958,-1000,-62,-371,988,1000,1000,500,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{-1000,506,-677,265,11,959,392,-706,-438,589,-318,950,-92,-251,215,-554,-1000,-1000,665,-350,599,-850,-124,-1000,500,-341,134,-314,-778,148,-143,479,1000,480,362,45,720,60,-291,-407,503,-163,-1000,581,46,137,266,587,449,755,-665,-56,1000,-1000,-1000,-367,695,-5,364,-952,-1000,404,-340,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{-721,-229,-1000,169,-1000,959,342,-247,-934,204,-158,-1000,-72,217,-651,177,416,22,1000,-1000,504,-487,-100,-414,58,-417,-384,585,-814,311,-229,-316,466,-643,-123,-434,46,26,-266,-152,1000,669,340,-428,-679,817,-620,-523,-176,-1000,-990,311,915,-448,172,166,137,-396,-395,-770,-731,-68,321,-181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double):void",
            new int[]{-297,-696,974,51,173,-42,723,-802,-1000,-606,-781,620,-1000,-184,1000,203,-1000,-1000,1000,454,1000,854,1000,1000,770,-60,-524,221,972,112,-882,-612,-689,-501,729,1000,-465,965,-32,1000,-350,-696,-397,641,-141,546,350,496,292,1000,-238,-769,543,-873,-161,-869,-27,-177,-304,-56,-87,-655,579,-315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{217,332,-84,277,-799,344,226,27,-404,493,623,-710,-135,921,-30,614,-744,1000,-415,-20,623,-513,146,652,366,27,-1000,-1000,-411,655,-450,-122,-1000,-1000,-222,628,-1000,-1000,217,-1000,-1000,407,1000,-1000,-923,762,359,-1000,1000,515,462,-1000,-506,-1000,150,-1000,961,287,-988,-146,1000,-628,360,-426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{660,346,286,-92,55,-924,390,-401,331,961,94,622,814,-356,-341,677,607,804,-300,-305,-407,-880,6,819,698,-663,883,-525,-559,-229,-453,-84,891,401,634,130,-258,-502,-890,643,428,632,-473,412,965,-115,-94,-264,477,-932,-670,-74,294,870,-222,305,-92,-39,662,-922,95,321,-902,-445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{-11,-296,-1000,314,-201,613,-2,1000,-606,-988,-1000,-1000,-1000,158,-875,175,52,-676,-1000,-396,569,416,1000,122,-1000,1000,-784,-489,109,452,-471,1000,-748,-1000,755,536,-625,606,744,-185,-611,523,-919,-219,-1000,479,-786,-1000,-628,-50,1000,1000,-254,370,1000,85,325,-840,313,646,-420,-474,-412,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{369,199,286,-44,-277,-475,352,-249,-68,152,63,-276,-69,455,-254,870,324,1000,-1000,-647,-130,-380,1000,1000,-687,-663,-366,-698,-1000,-133,-682,1000,115,-324,545,250,-311,334,-40,323,-206,298,-901,-442,965,-142,56,-1000,695,-1000,512,-74,148,279,954,12,-376,-1000,964,-550,435,297,-1000,162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{433,1000,45,-334,-888,489,217,-131,-138,271,-960,-1000,903,1000,542,1000,-396,716,-1000,-261,553,-1000,1000,454,831,220,-1000,-706,-483,602,-666,754,-300,-561,1000,-318,144,-580,840,-120,-1000,1000,-341,-303,595,694,-1000,-149,295,-1000,992,-51,-1000,-253,766,-791,1000,229,-307,-34,-310,-31,-1000,-719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{656,242,818,1000,-965,-736,-357,394,-269,206,1000,-72,637,1000,282,645,507,804,-1000,-574,1000,-241,356,1000,-95,-308,-1000,-911,-1000,-405,658,1000,-1000,-1000,615,1000,-948,-1000,-150,-994,-626,-1000,1000,-889,1000,467,628,-701,1000,-271,570,-1000,-307,949,-366,-1000,-238,163,-640,1000,169,-945,-1000,-694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MTA=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{90,-856,-281,1000,-246,314,271,1000,-962,-605,-790,-1000,-434,-305,-802,-152,-117,-616,171,-819,518,1000,1000,1000,-191,1000,-1000,-1000,938,-293,-379,6,-1000,-1000,1000,642,-1000,93,96,-1000,-144,823,-737,-1000,507,-230,-1000,-1000,-883,386,363,1000,-885,150,788,74,172,-955,-484,618,-161,-1000,66,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{74,158,-652,-316,12,756,72,-127,435,124,409,240,-1000,374,-535,375,514,438,-860,-82,405,880,717,1000,253,-58,344,600,-715,760,-795,4,-168,708,-1000,581,737,273,723,510,-973,-161,132,218,1000,915,350,349,808,-271,234,-800,260,815,-110,-944,317,-64,248,1000,-311,409,-275,-480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{577,346,110,897,-124,1000,390,-421,1000,961,-16,1000,-1000,-934,78,1000,1000,804,-1000,-717,48,-335,128,819,919,-185,845,-976,490,738,-1000,280,345,401,634,496,-359,741,-630,-206,1000,591,659,817,381,-1000,1000,-453,131,758,758,266,30,240,-555,-242,-382,489,-470,1000,622,1000,-48,104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{177,545,-451,-808,101,446,793,-872,1000,291,876,589,-695,1000,17,33,852,953,-860,412,1000,1000,-197,938,788,-8,-377,1000,-1000,846,-554,500,-462,512,-1000,1000,1000,184,1000,674,-1000,142,604,604,1000,1000,738,286,444,-876,163,-1000,-500,815,-1000,-959,894,595,-112,1000,-374,748,-330,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{272,1000,-329,-236,-236,478,-288,-529,150,583,231,425,-74,73,-1000,804,-666,863,505,127,312,-1000,-93,1000,-156,-764,318,-989,-967,538,-738,-504,-315,-579,-307,3,-367,-1000,-581,-526,-904,891,477,-193,-798,-302,1000,-1000,634,-80,-561,-352,1000,-837,358,-86,-875,-51,179,-260,1000,111,1000,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{485,1000,-639,220,-1000,588,-313,224,-36,-68,610,627,-989,-415,400,1000,-686,804,-252,141,55,-1000,-134,819,18,-436,1000,-1000,-1000,-576,-936,-265,-729,-937,778,3,-725,-903,-1000,643,517,565,1000,-265,-1000,-891,1000,-1000,-321,929,1000,644,1000,-859,962,663,-270,-107,848,-981,1000,13,927,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,double,boolean):void",
            new int[]{-205,158,-815,-1000,-67,1000,-1000,-991,775,320,-124,622,-1000,378,97,677,276,876,-1000,-209,-374,127,1000,1000,879,-976,417,492,-566,1000,-1000,-851,532,1000,-1000,150,1000,382,1000,1000,-1000,371,662,364,895,-115,205,543,1000,-532,656,-1000,408,798,545,-995,332,-245,553,526,-575,1000,-12,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{982,-1000,1000,627,-476,104,810,-54,831,-26,-647,160,-1000,1000,-987,-210,823,26,1000,282,-942,-630,879,204,735,-698,1000,663,243,1000,-1000,-590,663,186,365,-50,-333,-318,403,-1000,1000,-728,-512,978,321,1000,35,-136,-1000,-734,372,995,200,-147,1000,827,-1000,-11,118,-1000,-404,503,-73,319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-1000,650,-416,894,-188,-987,381,439,12,-1000,-170,-402,-952,-339,0,-256,-1000,662,-200,-925,-859,513,-600,-338,0,733,964,1000,-753,0,1000,-878,-613,1000,1000,295,-1000,-538,20,1000,0,0,192,263,11,858,-620,1000,-1000,-611,1000,-272,0,1000,162,199,-813,-1000,0,745,1000,-583,300,-709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-277,-234,-649,91,-359,-1000,-1000,-11,-329,-357,-127,-470,1000,-201,-770,815,-63,1000,806,272,-116,-1000,162,736,-167,573,1000,289,152,411,474,-1000,-667,-298,124,1000,616,977,467,-1000,466,-707,-676,1000,1000,708,-537,-439,25,-156,-1000,982,-1000,-1000,398,588,815,-43,113,260,-1000,1,746,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{249,-1000,1000,-292,-727,-162,156,-1000,1000,889,-905,58,181,-386,645,-276,-1000,198,-810,1000,731,867,694,-1000,1000,1000,562,-747,-682,943,584,670,1000,-25,1000,270,388,-98,-83,-614,-761,-556,346,-44,-699,-416,-1000,933,-2,-1000,1000,338,140,539,955,919,409,557,-153,409,1000,1000,557,257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{1000,-255,1000,-1000,-608,1000,-261,293,1000,987,-958,1000,415,660,-933,998,722,-1000,825,1000,608,83,256,346,60,-1000,-1000,753,1000,612,542,323,629,-1000,-1000,-1000,1000,-1000,-330,271,287,-1000,1000,-1000,-896,932,947,-925,-636,-549,1000,522,-340,1000,664,-124,564,1000,703,181,715,174,820,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{649,920,1000,-730,-1000,-351,-1000,-669,397,943,281,664,-1000,356,-1000,396,-204,85,-265,190,-654,-1000,1000,1000,-297,773,858,212,-714,293,-587,-1000,-717,491,-1000,1000,1000,260,478,1000,913,-1000,1000,594,1000,900,-666,-640,-357,-900,-179,-580,-1000,-1000,524,1000,1000,1000,-1000,295,-1000,1000,683,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-68,1000,22,-1000,-608,734,-445,-654,-996,379,946,-665,1000,-262,-1000,998,874,-727,-134,1000,-450,-1000,260,1000,-935,-310,-1000,-743,-137,-709,542,-783,-214,859,-1000,488,1000,1000,468,537,-298,-304,-800,-853,1000,202,-1000,-832,108,714,-865,-779,-340,-1000,610,-97,1000,1000,-1000,1000,796,174,1000,-223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{383,-396,-1000,-461,562,-662,326,1000,-313,-1000,-404,-729,222,804,-459,-442,-1000,-1000,-467,-382,48,-448,-952,906,-153,39,-809,-959,653,525,-113,968,-478,-113,-1000,-723,-1000,-960,-570,869,1000,255,652,643,-382,-952,208,-50,-169,-834,-78,-787,824,-105,-749,789,782,207,352,-173,-307,-1000,1000,-558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{575,-1000,667,-46,-1000,549,-576,-865,1000,163,-299,1000,207,371,-1000,-1000,-512,-714,-854,711,447,-9,662,1000,1000,-528,1000,50,515,1000,53,-967,-932,-692,120,387,400,-616,-1000,-1000,1000,291,-12,329,-223,676,155,-525,348,-694,-245,444,-150,-400,28,1000,400,1000,1000,439,401,1000,1000,563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{694,68,498,-1000,1,-122,831,1000,12,-1000,-512,243,-800,600,-1000,-84,548,-986,-200,909,-68,-391,-770,1000,-615,-1000,-1000,-1000,870,-174,-187,-1000,-613,-467,-1000,-1000,-317,-538,1000,478,638,-354,256,-1000,-544,-552,898,-374,-1000,760,319,-541,594,803,1000,1000,7,1000,1000,-777,1000,-151,300,-31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{884,-729,1000,-961,-847,347,-122,-39,788,-62,-334,757,462,-201,-1000,-547,464,-199,-11,1000,213,-1000,490,510,1000,188,1000,-743,152,659,1000,-1000,390,-298,-115,523,1000,983,678,-1000,167,-728,-102,-917,629,437,430,175,25,-156,173,1000,-562,-453,579,-530,108,-159,505,1000,-290,1000,192,968}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{435,1000,1000,172,-283,-248,-581,536,-516,-228,295,-662,391,-990,-612,728,680,540,624,-879,-980,-848,-109,1000,-183,1000,761,895,-1000,1000,1000,549,-1000,439,-540,-489,912,1000,327,-1000,-444,-199,-427,841,1000,1000,-534,-721,-609,-214,-1000,-885,726,-1000,-167,-663,463,-661,-776,740,-591,795,605,71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{918,-19,-469,738,1000,-819,159,-378,754,-570,545,-504,900,-1000,-1000,-412,-61,891,-320,631,-1000,235,927,-616,-379,1000,514,728,24,1000,510,734,-350,-358,-121,981,334,1000,-1000,947,730,1000,582,-1000,-1000,1000,598,695,-1000,-448,1000,-7,-751,-101,159,873,521,-33,909,745,1000,1000,-119,-741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{253,-68,271,280,-913,554,-1000,-197,-249,-543,459,294,-39,97,327,-162,-279,-310,225,1000,809,114,847,1000,1000,-316,-267,30,323,841,-98,-31,-447,1000,7,-743,-344,-1000,136,329,-312,-866,-103,56,80,-341,-87,928,778,144,-3,-772,-1000,-31,369,777,562,702,637,473,162,-175,711,267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-1000,-347,32,-1000,-1000,1000,813,412,-1000,-696,431,-1000,-933,-1000,-1000,-247,-462,-67,869,571,1000,1000,-557,-1000,-1000,664,531,-1000,1000,175,932,-54,-1000,-769,-471,113,-1000,60,1000,-1000,1000,1000,710,26,-1000,831,770,572,-737,-865,902,169,300,34,-1000,-1000,-724,133,-485,114,-61,-84,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-1000,-30,-226,-1000,-338,691,1000,574,-286,36,121,-732,-710,-1000,-840,768,-711,231,-244,-510,289,1000,-811,-1000,-629,664,364,192,834,72,1000,-590,-348,-1000,1000,1000,-1000,627,1000,-1000,1000,1000,357,-594,-1000,902,772,-41,472,-1000,234,774,300,-1000,-302,-633,-724,-291,-432,1000,-275,-84,-115,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-996,-442,1000,-864,-1000,-502,-752,-61,-694,-331,-703,235,-252,-483,843,1000,-629,-351,-31,1000,1000,-271,738,-468,-173,-600,-152,-1000,124,-932,-727,-1000,-355,530,795,-898,-396,-891,1000,-1000,-38,-1000,94,1000,-351,6,407,727,1000,560,386,-7,197,1000,-456,-450,-1000,800,913,-240,918,631,1000,799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{906,202,656,445,-1000,236,-1000,-157,-93,-347,228,757,-355,567,1000,-230,-275,-641,455,763,728,-486,781,789,879,-880,-810,-125,32,773,-793,-66,-210,706,-381,-944,-143,-918,157,730,-891,-1000,-460,508,543,-1000,-601,897,1000,1000,-432,-789,-1000,298,956,955,1000,574,946,180,-197,-377,193,220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-21,-628,78,313,289,-536,-277,49,237,-725,-392,-552,-286,-1000,-1000,461,162,196,-927,85,-100,405,723,-1000,-1000,1000,1000,-230,632,1000,394,166,-1000,-450,908,932,-173,1000,-357,1000,1000,1000,1000,-1000,-1000,1000,1000,754,-1000,276,1000,146,-1000,180,-1000,-119,521,249,961,829,577,1000,-12,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{-556,-716,-473,330,-145,688,-534,-477,280,-533,968,13,-444,-483,-896,-270,-47,-395,-72,54,692,889,785,557,59,830,761,618,874,745,270,930,-1000,323,-39,-774,-915,-349,-469,222,458,2,14,-1000,121,183,42,753,248,-680,273,-838,-1000,-216,-476,804,-720,995,0,712,362,-622,609,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{503,-332,-838,1000,1000,-236,-81,-968,1000,-268,-423,855,-995,-1000,-1000,-1000,282,-811,1000,-616,-1000,-96,899,-330,22,1000,1000,1000,-357,355,-1000,55,971,506,-471,-156,92,1000,-1000,1000,791,112,1000,-1000,96,1000,901,767,-1000,-865,712,-491,-1000,-10,-265,1000,-1000,-6,755,1000,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{702,-466,119,1000,68,1000,-1000,-131,1000,-1000,334,773,-169,-1000,-1000,-998,563,-631,-333,-320,-324,15,1000,-15,-910,1000,1000,652,116,937,-402,-563,-534,270,-512,-593,-58,813,-1000,1000,664,552,1000,-1000,-580,1000,1000,866,-1000,-502,563,-847,-789,-299,-637,1000,-1000,1000,-547,694,1000,-677,-88,493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.RegularTimePeriod,java.lang.Number,boolean):void",
            new int[]{1000,206,858,168,14,-1000,-679,876,501,46,-86,364,477,151,-93,-121,-692,911,9,-169,-42,-630,307,309,-662,-400,220,465,-335,-1000,909,-895,324,-604,-40,1000,835,379,369,-4,-549,152,-33,-645,-500,46,800,861,-93,290,-382,649,-169,-942,558,493,-1000,235,1000,717,33,697,-345,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{691,-1000,-1000,-712,-109,109,-582,-591,-213,-400,-58,951,-57,67,-989,382,128,1000,787,-292,757,256,-1000,620,1000,-220,663,1000,1000,-496,965,638,-328,-602,-20,174,862,327,412,-413,1000,958,1000,-1000,658,-8,372,-1000,-90,-284,146,127,-266,-945,1000,679,-405,-1000,-105,213,1000,-1000,-442,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{-1000,-902,-1000,-1000,-951,-992,1000,-241,50,-966,-1000,493,-774,-1000,-1000,1000,1000,-383,1000,-1000,-663,1000,-141,1000,140,398,1000,552,-500,1000,1000,-24,370,247,-1000,-1000,951,1000,-41,6,257,1000,1000,-1000,1000,-1000,627,200,-1000,1000,1000,1000,903,707,289,291,1000,379,-1000,1000,1000,213,981,-261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{878,428,-1000,508,1000,833,537,280,-1000,837,-92,-466,-445,922,1000,-198,-225,572,855,327,1000,-913,709,-328,1000,-1000,-766,1000,337,-1000,-940,852,-1000,-1000,731,1000,-1000,329,1000,-959,1000,-433,-313,-1000,1000,1000,-764,-532,171,-1000,-515,-1000,-1000,-1000,824,407,-18,-446,9,-797,1000,-1000,-1000,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{-1000,-654,-855,-980,-366,-172,964,179,364,-1000,-1000,373,-678,-1000,-637,19,862,-970,418,-1000,-1000,75,-532,984,21,636,1000,689,-18,1000,392,-293,-240,-381,-1000,-1000,-214,1000,-879,936,-122,569,181,-724,512,9,-284,-672,-466,50,1000,-109,532,329,-862,74,550,1000,-1000,798,-383,641,707,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{508,-1000,-1000,-739,260,-766,-757,830,-1000,1000,-827,-130,137,282,1000,55,1000,-260,135,-366,70,589,-772,-1000,657,-1000,-157,-556,39,246,15,639,130,888,-640,1000,642,216,-818,-152,-682,811,996,577,-492,-878,322,-641,729,-487,878,825,562,-165,814,-1000,32,-441,324,316,1000,-803,-56,715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{242,-310,661,824,-10,-76,119,-661,153,1000,-626,-535,-171,-393,476,-403,381,-36,-1000,494,-1000,-399,68,-1000,26,-201,-334,-1000,-836,1000,1000,622,318,1000,598,452,607,634,-1000,-748,-1000,224,-559,1000,-1000,-1000,-333,-1000,-681,212,-914,109,573,1000,-303,-1000,-962,464,-161,-785,-122,-306,1000,553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{-1000,-857,-296,-441,-626,-926,-953,-825,493,391,15,313,-1000,-1000,-1000,-158,273,-1000,-18,-1000,-1000,-647,864,-782,-538,-293,383,563,-603,391,-530,105,204,897,-1000,-496,346,441,-142,586,-231,-238,-280,-97,883,497,-275,1000,-473,351,1000,-136,321,544,21,-283,1000,772,-1000,279,-3,1000,678,-482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{202,-1000,-1000,-1000,-455,-281,-436,344,-564,-1000,-962,495,722,-456,-190,1000,854,-292,-341,-613,-314,1000,-837,831,373,345,1000,672,-80,733,1000,-213,525,534,-781,-1000,1000,1000,-879,320,-409,1000,1000,-353,-303,-1000,966,-1000,-856,803,1000,1000,1000,1000,150,-623,-346,-400,-270,1000,640,-404,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{-651,-1000,405,-746,785,-807,-358,-597,618,183,-1000,1000,1000,-616,-361,455,-1000,-1000,-650,106,-811,-31,609,613,206,1000,-171,-1000,-1000,1000,1000,503,686,-80,-1000,-1000,29,865,331,925,-1000,452,619,-156,-840,-1000,-171,422,-1000,-668,1000,1000,1000,1000,-1000,-771,1000,-956,-295,509,193,839,806,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{682,95,-179,1000,934,723,-953,73,-761,391,1000,-681,61,430,775,-157,-679,383,-274,438,474,-1000,-436,-782,711,-1000,-979,415,821,-1000,-530,886,-1000,-440,1000,1000,-1000,-782,-250,-217,401,-772,-621,1000,-1000,1000,-687,-246,555,-640,-751,-1000,-205,-1000,21,-168,-827,41,-60,-1000,590,-303,-1000,95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{-1000,-1000,248,-746,-585,109,5,-112,382,-1000,-581,1000,-75,-791,-1000,91,-321,-1000,1000,185,-311,0,559,786,0,-970,51,1000,-928,328,605,639,268,1000,-1000,32,1000,448,44,-286,-231,680,1000,1000,1000,426,1000,1000,-1000,1000,925,855,175,899,-627,-491,974,-846,-1000,1000,312,979,653,267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem):void",
            new int[]{-495,245,-425,-211,-1000,-972,401,-288,813,-972,-214,120,-940,-216,280,358,313,-1000,1000,-138,-415,1000,-1000,1000,-1000,-148,323,941,-762,854,220,110,-385,20,-1000,905,404,1000,893,561,55,-1000,1000,-965,982,559,-469,744,-1000,-990,-779,1000,1000,-215,592,-931,134,-171,-1000,-475,-1000,1000,715,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-313,-1000,631,-1000,-750,249,358,-625,507,118,791,-1000,-126,306,-569,539,-1000,1000,799,1000,-1000,500,1000,-601,-1000,-56,-973,-159,-1,1000,1000,-1000,273,-972,-607,-612,211,-537,-936,-79,-1000,-1000,-357,-743,-175,1000,1000,1000,-607,246,-575,266,-1000,261,-1000,-1000,1000,239,-1000,-1000,1000,-325,-636,-357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{1000,-120,1000,-461,139,-941,-159,898,666,-1000,1000,-651,360,-404,723,-739,309,1000,42,-990,1000,987,464,858,-473,1000,222,-1000,337,1000,782,413,413,834,960,-33,-802,1000,-1000,524,1000,-1000,1000,-1000,-291,45,770,-812,630,1000,385,695,-692,-1000,1000,-695,-1000,-913,-537,1000,1000,-1000,1000,-379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-1000,1000,-1000,201,223,-881,-792,-308,-108,-140,-311,-503,176,1000,232,-70,-1000,-249,578,-1000,-1000,-1000,-1000,680,237,-805,1000,896,1,-1000,209,-1000,-612,-422,-391,-1000,-182,-774,1000,430,-1000,406,-142,1000,385,-1000,1000,366,972,-1000,-671,989,1000,-307,-64,-1000,596,1000,1000,-440,1000,1000,643,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-706,844,-539,564,-656,-801,-612,-646,-719,-895,-160,577,322,-28,-939,633,486,-852,175,996,756,651,570,124,523,-684,131,675,-788,-823,979,691,530,769,221,947,537,-233,483,-991,912,855,477,-273,161,62,-283,-154,312,-80,-344,-764,655,768,-64,820,-517,264,-719,-296,-544,-253,-397,-743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-1000,-1000,1000,1000,-1000,-801,880,1000,930,-226,-1000,-853,-1000,-211,-1000,222,-548,1000,1000,1000,-1000,1000,423,-1000,-1000,364,-1000,-1000,967,1000,411,-412,1000,1000,-591,-1000,-1000,-1000,-529,1000,-1000,-1000,-1000,-210,-1000,1000,-215,1000,1000,330,1000,-761,-1000,-1000,-64,-1000,1000,-261,-719,4,-544,1000,428,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-673,593,721,1000,278,-87,-766,789,337,-328,-1000,837,1000,-203,192,269,1000,-1000,461,113,1000,494,-113,665,1000,196,-427,102,-10,-934,601,1000,-198,-329,533,730,-454,1000,-213,-950,1000,185,775,257,-519,15,-1000,-739,1000,1000,794,-1000,525,-438,918,601,-104,-657,-390,827,-1000,-505,561,987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-1000,-794,91,-632,396,1000,-190,-832,-90,-483,-186,-976,738,90,-786,920,-581,390,855,25,-671,-599,1000,-601,-583,-58,715,1000,-85,-120,249,-1000,-814,-1000,-529,1000,720,-749,18,229,-1000,118,-217,979,-758,1000,126,1000,-1000,-918,-612,-752,-919,1000,-444,132,-193,1000,-1000,-558,608,-210,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-527,-733,391,-232,-890,-529,878,226,728,61,1000,-199,-101,1000,276,-222,-725,1000,1000,271,70,-449,1000,-951,-1000,-116,-374,-1000,264,1000,609,-661,921,-248,-435,42,184,141,652,307,-495,-1000,-239,-463,-616,1000,844,920,-600,59,-373,327,-849,-187,-443,-1000,914,-152,-1000,-20,551,606,93,591}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-730,-1000,549,-455,-1000,-159,875,-1000,493,508,7,-810,-1000,344,128,1000,-1000,935,1000,1000,1000,659,1000,-220,-1000,-367,-1000,16,-35,-70,1000,-1000,902,-1000,-1000,-315,193,500,-955,-1000,-1000,-994,-557,-820,-351,1000,564,1000,-73,382,-1000,72,-1000,483,-1000,-760,1000,130,-1000,-1000,-654,36,-1000,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-1000,-212,-257,1000,296,458,249,-163,604,-1000,-1000,-566,413,235,719,500,1000,-457,536,-45,894,339,410,1000,-501,154,1000,1000,7,-1000,-584,1000,-1000,-96,-439,1000,213,876,488,-514,755,-148,-353,1000,-965,497,-1000,-572,1000,1000,-190,-1000,1000,1000,-31,1000,-60,-1,-1000,561,0,-44,-645,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "add(org.jfree.data.time.TimeSeriesDataItem,boolean):void",
            new int[]{-784,-33,-334,-78,-259,-1000,130,-308,224,-324,-160,-527,-932,-1000,-430,823,-380,-122,-417,753,1000,261,-326,-610,237,628,-498,238,96,-1000,474,-1000,280,-116,654,-537,537,-698,483,-1000,912,406,477,-772,77,-400,404,-420,972,272,-671,260,-46,-307,-64,-208,596,-144,400,-112,-411,-677,88,181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mg==|getMinY=25:java.lang.Double:NjIuMQ==|getMaxY=25:java.lang.Double:NzcuNA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{-1000,1000,1000,774,621,234,887,-1000,1000,-1000,389,-883,637,-1000,-1000,-725,1000,1000,-1000,1000,1000,-1000,-249,1000,-392,-1000,978,-1000,427,1000,-788,-1000,-688,-1000,-744,-516,1000,29,-332,812,-98,-759,-1000,919,154,-1000,-685,-468,-985,-1000,1000,-1000,1000,-1000,1000,1000,860,1000,-347,1000,1000,594,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{1000,-157,844,1000,-1000,1000,-457,-791,-317,-115,-1000,-312,-6,-96,116,-519,-220,-73,76,-1000,-596,1000,-193,-1000,-1000,267,-1000,457,-830,-276,68,523,19,-652,-985,-983,-1000,-1000,1000,-732,-369,771,1000,864,-492,1000,1000,607,1000,225,-1000,1000,-1000,1000,398,506,-600,324,1000,-80,-1000,1000,-202,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{714,1000,1000,300,-84,634,-272,-521,585,-885,-140,549,574,419,-325,-332,-44,210,403,-1000,112,233,180,294,-840,306,87,-477,-661,398,-122,407,171,-312,252,-84,621,-300,-206,273,-171,122,984,651,592,126,469,308,-111,-1000,346,-751,-890,-329,1000,190,455,941,-751,521,-449,300,31,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{-876,-60,1000,677,-935,219,-811,-985,1000,-723,-1000,103,-385,-504,-597,-417,-291,180,-1000,1000,-1000,51,1000,746,-131,1000,-874,406,-59,340,-332,389,-744,-857,-619,-1000,-312,-310,-723,-1000,-1000,1000,333,748,1000,-1000,1000,596,-587,341,1000,1000,856,934,1000,1000,-1000,561,1000,946,50,420,650,-644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{559,615,891,1000,-506,1000,-21,-1000,78,-380,-354,-539,933,1,352,-1000,499,128,470,657,565,1000,-78,889,-1000,-617,-1000,-322,-801,107,-252,-39,-251,-856,-985,-746,-1000,-1000,1000,1000,188,-305,400,1000,788,303,1000,391,173,-787,-114,-945,-400,-76,347,68,893,527,401,282,-400,1000,-179,332}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{-17,546,1000,700,-371,1000,736,-792,149,-462,-718,-1000,1000,-1000,-1000,-156,1000,247,1000,1000,792,-227,951,-1000,-346,-723,-187,-285,-813,1000,-1000,-452,-1000,-187,-1000,-1000,515,-631,-841,358,-416,-236,-1000,913,80,-281,-648,485,-235,-450,-63,-270,1000,-1000,-1000,1000,771,1000,-434,900,61,700,445,-437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mg==|getMinY=21:java.lang.Double:MC4w|getMaxY=25:java.lang.Double:NzcuNA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{52,1000,1000,0,774,234,170,-230,1000,-620,546,-242,575,-375,-1000,428,967,856,102,-709,112,-922,-249,1000,-426,-709,1000,-1000,-101,1000,-788,-371,-688,-98,-574,226,1000,257,-332,1000,-174,-709,-244,294,154,-1000,-391,-833,-984,-1000,619,-751,-115,-1000,683,812,902,668,-493,1000,660,0,111,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mg==|getMinY=25:java.lang.Double:LTEwMC4w|getMaxY=25:java.lang.Double:MTAwLjA=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{-984,-108,1000,1000,-1000,898,-867,-1000,1000,-1000,-1000,259,386,-1000,-146,-1000,-287,590,-215,-1000,-669,1000,910,-1000,-1000,707,-1000,266,-557,-287,-465,-308,-400,-943,-687,-908,1000,-1000,1000,-394,-367,337,332,1000,1000,942,1000,472,1000,248,-1000,1000,-400,647,761,803,-158,270,1000,99,-663,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{1000,1000,653,0,48,334,-199,-181,1000,170,-266,423,169,55,-713,175,664,511,-233,15,606,-235,-746,-103,0,18,-1000,-582,-788,862,12,523,-338,646,-302,-92,-24,61,0,399,-116,560,1000,215,342,209,771,-460,0,-1000,-376,-563,640,-554,-161,316,-335,330,0,833,-1000,0,-496,-183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addAndOrUpdate(org.jfree.data.time.TimeSeries):org.jfree.data.time.TimeSeries",
            new int[]{926,1000,653,0,308,334,606,-126,767,-294,372,-57,332,-504,-805,-78,1000,180,489,1000,1000,-316,-746,746,98,-729,553,-1000,-59,1000,-517,-91,-907,146,687,207,873,0,-723,1000,99,-64,264,215,-1000,-1000,-375,-290,-587,-1000,1000,-889,1000,-1000,-685,316,735,608,-500,901,-213,0,-154,-644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,300,1000,1000,-1000,-147,-1000,-23,-476,839,-345,236,1000,-318,-518,830,831,-1000,-474,392,1000,312,1000,-1000,-279,428,1000,-654,-652,234,-1000,-916,312,318,218,1000,383,-63,-490,-300,-331,1000,-133,-101,146,-710,-875,-357,381,1000,942,-941,411,535,-10,1000,-1000,446,-1000,923,-604,-1000,300,52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-121,-1000,532,459,-451,-252,-1000,-590,88,-120,-82,-258,927,-314,150,170,1000,-688,30,-137,-220,353,-305,-1000,-37,-285,627,-732,-1000,-755,195,-983,463,-252,242,-150,-267,455,-733,297,628,-332,-671,858,-1000,-509,700,-1000,-211,-1000,998,309,111,1000,-86,-480,-695,1000,-449,273,-582,286,933,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.TimeSeriesDataItem", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,1000,-992,1000,-1000,-977,1000,-1000,-38,1000,-1000,867,371,-512,1000,1000,-1000,801,-316,1000,1000,-534,1000,1000,879,498,1000,-930,1000,-572,-863,1000,140,1000,-1000,-1000,-1000,176,1000,729,68,1000,1000,-1000,-1000,-1000,-1000,-273,721,631,598,-1000,1000,-295,1000,1000,340,1000,-1000,1000,-1000,687,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{41,622,373,777,-151,344,992,-699,284,1000,-648,5,-357,895,-1000,1000,1000,436,30,824,307,353,-473,796,140,1000,148,-1000,-639,-755,-937,41,-1000,331,-812,72,-587,-1000,741,297,273,1000,456,-155,-3,269,-764,-16,-211,-208,-19,309,1000,-1000,53,1000,-98,332,-1000,885,-902,183,1000,390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{1000,1000,869,-580,-625,-31,122,-1000,-748,-150,-678,365,213,380,-230,1000,780,819,-548,869,204,593,-1000,-873,-573,420,371,1000,-1000,-1000,-989,-1000,254,984,-1000,-691,-584,-141,854,-339,-130,1000,-420,375,999,939,-545,707,679,-861,1000,-337,1000,-1000,-812,-219,415,-47,-1000,1000,99,215,107,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,1000,104,1000,-1000,-81,197,-310,-151,1000,-987,818,1000,-644,-208,1000,334,-182,-524,1000,896,320,670,235,217,471,645,49,748,63,-1000,224,-397,1000,-651,405,237,-890,723,89,-941,1000,1000,-1000,1000,-1000,-1000,-357,1000,1000,627,-1000,1000,-146,668,1000,187,446,-1000,1000,-896,-641,-108,-425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{1000,344,315,679,833,-381,1000,-423,-1000,1000,-206,592,-834,1000,-918,623,1000,801,897,-208,666,-534,-1000,1000,-312,210,59,-930,-825,581,-863,468,-954,-914,-295,1000,-974,276,-230,-993,906,381,-520,-409,-1000,-118,-764,-273,721,-328,-111,455,590,-1000,323,457,-425,753,-88,651,-1000,740,666,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,140,155,-821,-166,-467,-1000,95,-501,1000,63,85,-98,84,-273,419,1000,-76,-60,406,-1000,758,-1000,400,826,-168,-293,-213,156,-748,-164,-1000,-156,823,-543,-1000,-816,-156,440,-402,-303,278,583,588,493,1000,-510,-83,-398,182,665,-187,378,-911,-754,273,-1000,-47,-615,524,962,-800,757,772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{1000,-1000,714,1000,-470,-207,1000,-379,346,139,367,-470,-154,182,-378,-65,-41,-54,1000,71,186,-584,234,1000,160,356,109,-838,423,-6,160,-199,-1000,-1000,466,1000,-573,-358,-646,61,439,-816,161,-272,-1000,134,407,-1000,618,-1000,-771,863,-45,-242,442,14,811,244,-1000,238,225,1000,-930,-13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{590,-658,414,793,529,851,470,419,61,514,2,52,-68,-524,-1000,-103,405,-423,-38,54,1000,321,175,1000,-299,-304,430,-978,821,887,108,-1000,-516,337,-52,1000,549,-633,-534,-358,117,-435,201,-479,-348,-75,-969,252,-241,796,-163,170,-1000,-334,-429,-514,-16,-302,583,-333,-970,299,-381,-296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,397,-883,865,-1000,-1000,18,-1000,571,556,-1000,462,1000,-980,1000,1000,706,733,1000,1000,-1000,754,-899,-215,643,-450,285,-800,65,-1000,225,868,795,1000,-1000,-1000,-833,74,-627,1000,-150,848,916,-229,191,-1000,16,-1000,942,-1000,1000,-975,-165,37,1000,975,1000,1000,-1000,1000,-885,967,883,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,double):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,1000,-217,-583,-550,-857,-335,-192,-563,1000,-407,394,-120,303,322,1000,420,510,-69,1000,-1000,511,-1000,400,474,170,-71,1000,156,-919,-1000,-641,-113,1000,-1000,-1000,-886,25,1000,-339,-310,1000,1000,-13,1000,723,-1000,800,154,182,667,-1000,1000,-1000,-178,1000,-667,-47,-1000,1000,893,-1000,107,631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,468,-1000,-1000,140,-16,471,-1000,188,-1000,1000,-1000,1000,248,1000,1000,-399,-1000,1000,1000,107,-1000,54,1000,-1000,-1000,-233,497,377,1000,472,300,1000,-1000,1000,-683,-1000,-1000,1000,1000,1000,1000,1000,-12,-1000,1000,1000,-1000,-1000,-586,-1000,1000,626,1000,-1000,916,1000,-1000,-994,1000,351,698,-590,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{754,209,305,-45,-862,208,299,-426,-412,-1000,791,-374,-51,-117,282,-467,-664,359,-761,184,381,519,-679,-71,33,-594,-1000,967,285,1000,-797,-322,204,1000,224,91,-466,-579,-504,-534,99,1000,1000,-708,480,-125,118,-615,-1000,-236,696,359,-1000,184,-582,100,241,197,199,1000,-366,368,-1000,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{419,-1000,-1000,1000,-458,-791,-930,1000,720,958,-1000,-298,391,1000,24,-525,117,-385,-601,-1000,-518,12,-310,-616,-366,1000,1000,30,-68,-1000,699,403,1000,513,-720,255,862,112,1000,1000,125,-1000,-1000,-534,-268,-499,694,1000,1000,-1000,-1000,570,673,-224,1000,-1000,-1000,1000,694,-951,1000,-457,-298,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-75,-284,56,-145,151,339,153,-306,-531,478,-77,450,-608,99,-141,299,36,151,853,-422,-303,328,465,462,1000,371,449,828,74,610,847,-13,767,646,257,-224,-335,830,73,560,-362,-536,-612,71,-4,329,412,764,1000,-192,-1000,107,-163,-964,441,-156,-54,-463,-356,-128,1000,-714,-13,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{926,98,681,904,-517,504,-630,-261,-313,375,1,336,-634,-252,-580,-52,-582,32,-486,-538,-92,-504,-606,-71,590,140,-998,108,224,-300,-651,-475,-477,675,-485,237,-100,248,-653,-74,-660,-2,485,-914,1000,-233,-792,-66,-747,959,1000,-6,-905,-155,-56,187,263,455,-28,-223,-225,-809,-1000,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,-56,-1000,-1000,449,-862,-87,-720,669,-609,-109,-1000,1000,889,1000,1000,93,-1000,1000,1000,-354,-1000,385,1000,-1000,-864,1000,71,128,-920,1000,1000,1000,-1000,1000,-1000,-865,-478,1000,216,1000,251,580,231,-1000,1000,1000,-850,-484,-576,-1000,-282,1000,1000,108,855,-359,483,-1000,-366,1000,-45,-180,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{4,-62,415,-291,215,1000,-38,128,-886,502,-308,1000,-1000,-484,-967,454,-815,-390,1000,338,-238,911,-11,-184,1000,271,-288,-405,488,207,-572,-978,717,619,-235,-132,81,1000,-1000,-91,-1000,-272,-309,928,844,-233,-91,192,778,-11,750,1000,-1000,-1000,514,447,303,-541,-206,2,559,211,-394,-465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{222,-388,-1000,182,-292,209,129,-261,375,293,660,255,-1000,509,-784,522,-774,411,-307,465,615,747,-556,-71,-375,231,284,80,529,574,-318,-849,650,747,-1000,213,1000,518,-927,1000,-281,-518,-569,-117,7,-721,-30,293,255,679,384,-134,-502,-525,142,-891,-405,345,-3,-260,-28,272,-591,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{0,-195,-1000,-502,-653,303,252,6,-414,-1000,1000,-178,-1000,341,-101,352,-1000,577,-491,1000,1000,633,-157,-1000,-693,-431,-758,-613,343,-244,-732,-669,633,942,-710,-63,804,9,-1000,1000,251,280,576,-150,64,-404,42,-315,-869,1000,1000,-7,-1000,34,-324,-720,-205,-64,-485,1000,-182,1000,-1000,137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.RegularTimePeriod,java.lang.Number):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{893,25,804,-37,-204,-265,-884,-574,-1000,211,-168,-54,-301,-978,-570,-665,-250,-583,-1000,-313,-728,1000,-138,472,-1000,-190,-21,-559,945,1000,97,-1000,1000,1000,545,554,-519,283,-1000,-1000,-335,204,962,-1000,573,-554,-1000,-1000,-582,-1000,930,-1000,-687,868,-25,385,39,1000,221,338,-82,-275,-586,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.TimeSeriesDataItem):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{169,64,-299,880,-486,-231,732,42,449,1000,2,-535,691,-582,271,308,538,-429,-378,171,-808,145,922,530,-1000,873,547,-621,-886,848,697,286,-523,269,28,154,541,550,904,415,1000,-811,185,-129,-199,-657,-864,-141,-646,612,482,32,807,-41,-290,-526,389,1000,-289,128,-986,367,-203,-970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.TimeSeriesDataItem):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-926,-582,-1000,745,-1000,1000,290,928,-569,-1000,1000,155,-1000,72,-249,-258,-654,-1000,1000,1000,-187,346,-473,454,615,-809,333,-345,-1000,702,-1000,-556,-353,173,291,-919,604,981,-1000,-184,866,766,-1000,-54,1000,1000,1000,850,457,-1000,115,1000,522,-522,-993,737,-601,-306,459,844,1000,-191,-1000,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.TimeSeriesDataItem):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-454,-405,-1000,5,-560,1000,-483,1000,-642,-1000,1000,973,741,328,940,-497,-393,-1000,676,0,1000,593,-740,272,1000,-430,-1000,868,422,-330,-1000,-586,700,919,62,-103,1000,161,-1000,172,-60,1000,-1000,457,1000,1000,0,1000,713,-1000,878,929,1000,789,-333,1000,-1000,-1000,867,807,1000,390,-660,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.TimeSeriesDataItem):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{1000,188,693,1000,100,939,190,604,777,1000,-1000,-644,1000,-228,158,844,563,83,663,-529,-379,-797,553,506,-1000,740,-624,237,-519,1000,1000,265,424,804,223,690,818,181,1000,969,1000,-1000,852,-137,1000,-1000,-1000,-170,-266,1000,401,1000,595,-376,-180,-1000,217,1000,-917,238,-1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.TimeSeriesDataItem):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{656,-1000,-796,932,1000,568,-773,1000,-853,-1000,1000,692,556,-477,470,21,77,-356,1000,1000,863,1000,808,970,43,-821,-474,180,-947,-163,-1000,-830,278,405,1000,-1000,1000,151,-933,371,-328,-896,-965,-312,362,944,567,706,795,-1000,1000,-296,1000,-269,188,1000,950,-1000,-332,1000,1000,1000,683,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.TimeSeriesDataItem):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{519,-648,-834,397,-638,673,-565,1000,226,-924,1000,1000,-111,238,1000,60,569,-1000,-677,1000,958,1000,-676,-172,952,-392,-747,-664,-994,-614,-1000,-472,278,-347,-347,-1000,659,-514,-1000,-103,7,117,-468,-364,492,1000,-101,-275,369,-1000,918,990,1000,264,1000,1000,1000,-1000,-662,846,554,846,745,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.TimeSeriesDataItem):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-223,-478,-1000,-135,-185,450,1000,-103,-1000,14,-235,-333,20,72,-839,-318,17,-237,1000,597,-109,342,357,594,4,664,420,-5,-500,702,-407,1000,288,1000,177,644,185,530,-202,-203,1,-995,-517,-833,595,742,-300,-471,-52,-1000,-994,1000,102,142,-447,548,-601,803,-671,401,-451,-191,-1000,-339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.TimeSeriesDataItem):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-155,-139,-420,107,-599,-115,497,376,387,-1000,-410,354,1000,-379,613,36,962,10,1000,992,31,221,728,1000,49,801,773,1000,83,-482,-1000,547,1000,928,152,-223,850,-371,-503,839,93,-723,185,-689,761,1000,-1000,-283,-47,-873,65,661,1000,-520,-209,1000,347,1000,-1000,919,-529,290,487,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.TimeSeriesDataItem):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{237,-315,-1000,92,-767,568,-466,1000,-474,-1000,915,1000,1000,906,1000,2,-119,-1000,302,639,861,722,-811,-310,1000,-264,-1000,940,-66,-45,-1000,-412,1000,424,-422,-830,859,-901,-282,1000,-772,269,-307,-716,1000,1000,-877,206,1000,-1000,1000,1000,1000,498,905,766,1000,-1000,-279,1000,359,1000,-48,-483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.TimeSeriesDataItem):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-293,214,-743,256,-599,489,430,434,-1000,-1000,869,26,1000,-304,498,-476,118,-297,1000,828,-379,431,553,1000,571,118,773,1000,-78,-506,-1000,31,424,675,492,-448,818,3,-1000,-108,490,-332,-964,-72,954,1000,-1000,390,-102,-1000,465,661,1000,-193,-674,1000,-664,1000,-917,855,304,256,-4,-438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "addOrUpdate(org.jfree.data.time.TimeSeriesDataItem):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{359,-92,-247,90,-1000,461,-442,-213,-101,-292,289,-454,-1000,578,-411,-742,-1000,-936,-218,-68,2,-159,-1000,-477,1000,-1000,225,54,-601,150,-635,632,66,1000,-44,316,1000,745,-933,-1000,282,-465,-962,47,440,60,1000,592,975,-729,-1000,278,-1000,-113,-823,272,-1000,-797,370,625,628,112,-158,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{-168,-676,65,300,-145,44,-214,-447,-895,-717,-681,490,-1000,-636,-89,-698,-476,-1000,-233,-18,456,983,-6,869,1000,-273,286,-946,-1000,-901,-553,56,-977,1000,-72,-464,738,-923,-366,183,-1000,-109,614,-792,-935,1000,-167,-371,-424,-312,326,-101,366,768,-282,-434,-716,-206,471,-277,396,-1000,-1000,710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{293,-974,180,-157,-655,927,-309,-348,83,-918,-252,227,-1000,1000,-72,-191,-389,-434,-337,266,506,636,-365,515,698,-1000,-369,-854,366,-465,-50,222,-682,-379,387,85,-37,-618,-418,-91,-1000,314,230,-559,-872,1000,-928,-1000,-180,491,552,603,835,665,-829,-460,-428,130,849,-512,83,639,-368,160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{976,-1000,577,14,29,998,-897,404,1000,541,-880,-376,-676,721,486,1000,1000,835,-1000,601,-750,-285,-113,-1000,-712,-1000,-1000,993,-1000,-354,937,-765,-1000,23,-141,1000,-441,417,167,918,591,587,-685,253,1000,-1000,674,-1000,524,1000,499,64,-1000,-1000,-735,549,21,-62,1000,-1000,-1000,-1000,468,30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{680,-1000,1000,-361,-1000,879,-744,-91,-1000,-528,-561,361,-802,550,322,1000,-1000,-1000,-1000,588,-1000,865,-150,-224,-539,-1000,269,-755,19,-831,-1000,-810,-685,1000,-1000,343,-777,954,-439,33,-1000,-1000,950,-1000,-824,279,-129,125,1000,1000,1000,785,-706,621,1000,-103,-775,-596,1000,-584,1000,-202,-736,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{1000,-768,1000,-762,-568,998,-214,-105,689,298,-270,-99,-593,844,-40,1000,-476,510,-1000,-119,-1000,-126,-1000,-1000,1000,-1000,-1000,1000,-772,910,1000,-565,-1000,-974,1000,876,-167,109,505,234,1000,495,-748,237,774,-982,1000,-1000,-755,1000,940,925,-1000,-744,-1000,407,324,696,611,-1000,-738,-417,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{695,146,-71,-162,-183,-433,223,-292,116,-1000,388,400,-456,-332,595,636,492,546,752,4,439,-598,-840,428,-896,320,234,-1000,151,-319,-695,-152,-161,1000,-607,7,382,428,166,289,397,245,-426,1000,55,-362,232,-507,-608,-55,264,312,-149,80,480,-1000,-271,324,436,107,-287,-386,563,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{118,-974,809,392,-723,405,-381,239,-91,-36,-229,-522,-1000,-67,925,-969,-584,-1000,-58,149,397,168,414,333,1000,-1000,-542,-213,572,-593,-573,-1000,-682,-1000,530,-600,-444,-1000,-541,-1000,-1000,-954,794,-1000,-1000,161,-146,-717,765,-385,-41,161,784,599,-611,293,-135,-217,849,-83,479,708,-674,180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{633,-563,-176,-885,-1000,215,-33,193,-1000,-205,-489,1000,-1000,-316,764,1000,-1000,-483,-1000,708,-1000,-917,1000,-524,-440,-721,-128,-338,-1000,-456,-488,-740,-1000,1000,1000,1000,366,-174,-454,494,-1000,-26,2,-944,498,-104,866,-1000,165,1000,786,677,-29,-223,1000,-37,-1000,-1000,1000,-950,1000,-567,-168,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{221,-829,-123,481,-263,482,-1000,127,-317,-338,-324,361,-530,101,251,1000,387,-798,-30,803,307,232,1000,-204,240,-101,834,-531,628,-1000,-925,-318,-176,588,-1000,1000,-967,954,-439,710,-1000,-214,123,-132,12,279,-129,503,1000,1000,689,217,367,-82,824,84,-687,-895,1000,-273,624,164,-602,845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{843,-555,879,-760,-762,256,19,-587,-529,8,-568,295,-549,-1000,-252,1000,-224,-760,-1000,8,-714,1000,-226,-1000,-9,-954,-209,869,-529,79,595,-361,-1000,-812,517,332,286,16,63,251,-325,-671,299,67,-36,377,1000,-1000,-407,663,1000,850,-917,202,-871,763,-58,-144,-68,-923,618,-506,-837,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{50,-756,-671,660,435,382,-1000,234,152,-506,-159,-32,-575,824,315,51,436,-171,385,402,506,-460,751,410,193,135,197,-1000,674,-804,-1000,-212,169,1000,-807,171,-1000,-40,-551,-23,-1000,-145,184,-142,-364,413,-834,400,1000,390,13,-229,806,-129,1000,-440,-636,-241,1000,118,4,168,-88,-469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clear():void",
            new int[]{889,-354,-670,37,370,88,233,-697,548,-705,648,-410,-223,605,-324,-100,665,405,654,-370,-81,-372,-458,97,-689,-140,-581,1000,1000,1000,503,501,496,-1000,115,-563,-223,1000,-131,-996,813,268,-350,402,-487,199,202,-1000,-497,-202,188,767,662,657,-131,-571,96,1000,-289,-257,-286,423,468,636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MQ==|getMinY=25:java.lang.Double:MjIuOQ==|getMaxY=25:java.lang.Double:MjIuOQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{1000,1000,1000,420,430,-267,457,358,-35,-192,400,-583,-116,229,338,977,-6,1000,1000,113,420,-38,1000,-299,-854,1000,984,-1000,1000,249,1000,1000,-638,36,1000,-1000,-890,989,1000,-900,236,-88,-330,767,-65,-283,-1000,-1000,420,-1000,-542,571,207,-521,296,-301,172,830,-524,746,554,57,580,-604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Ng==|getMinY=25:java.lang.Double:LTc4LjY=|getMaxY=29:java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{558,-473,611,-786,1000,-322,1000,-401,49,109,-925,593,1000,-795,279,251,182,303,903,570,525,-798,-926,-658,900,-941,-1000,78,-585,319,512,142,-660,1000,-714,1000,-766,-1000,-614,-416,-769,-521,303,-1000,-1000,-1000,704,-87,-1000,-1000,775,-417,-1000,-160,-511,222,1000,63,-1000,-788,-422,-819,10,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mg==|getMinY=21:java.lang.Double:Ni4w|getMaxY=21:java.lang.Double:OC43", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{117,1000,946,-817,-392,-494,-830,-444,826,87,60,1000,-1000,-1000,464,-521,979,98,38,-131,1000,-1000,1000,-601,-1000,1000,714,660,-477,279,-678,1000,670,-1000,1000,-1000,1000,-443,-160,-1000,-1000,127,-1000,850,-1000,363,-1000,-1000,-297,312,568,-443,673,-493,-1000,-1000,-624,957,-737,398,336,139,556,-117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mg==|getMinY=25:java.lang.Double:LTQ0LjE=|getMaxY=25:java.lang.Double:ODIuMA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{-710,176,-284,-441,820,673,946,-639,-137,-712,-999,1000,860,-571,1000,-345,-411,-606,-732,1000,-827,-1000,365,-755,133,893,1000,-118,-1000,491,-1000,1000,2,818,-10,486,1000,-1000,-1000,196,-1000,-1000,-1000,-1000,-1000,-1000,821,-1000,-804,423,1000,-424,268,-885,-1000,-25,1000,-68,-538,-1000,220,712,-473,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MQ==|getMinY=29:java.lang.Double:SW5maW5pdHk=|getMaxY=29:java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{-1000,1000,263,-1000,287,-377,-778,-1000,1000,-11,-90,1000,-763,-1000,502,-1000,950,-1000,-260,1000,1000,-1000,695,-277,-1000,767,-63,1000,-1000,840,-1000,1000,1000,-672,1000,-637,1000,-1000,-1000,-1000,-1000,574,-648,273,-1000,-1000,-1000,996,-856,1000,1000,-595,341,-806,-1000,-1000,-403,382,-463,921,-163,56,222,-57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:NQ==|getMinY=25:java.lang.Double:LTY2Ljc=|getMaxY=25:java.lang.Double:MTAwMC4w", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{315,527,401,-466,1000,-367,512,-667,233,-1000,-892,340,1000,-489,110,-627,614,-1000,775,1000,-253,-531,-620,-436,-666,113,-969,781,78,437,-920,-174,-135,1000,48,1000,-843,-590,-777,108,-511,1000,402,-429,-937,-755,-203,686,-407,-1000,329,113,-225,-675,-650,-387,396,2,-553,292,-369,391,571,-519}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{1000,587,-166,930,1000,1000,1000,598,-321,127,-451,180,156,-795,1000,1000,-360,784,293,-1000,-410,-727,288,-91,-534,1000,1000,-642,-265,-782,512,1000,-1000,-838,1000,-1000,-209,-1000,1000,-452,291,-695,-844,37,-1000,-51,607,-1000,-182,-651,767,-523,671,352,89,-408,842,511,-862,-725,-274,276,-2,-767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{752,625,371,222,870,-514,424,239,115,-1000,1000,1000,-56,-1000,900,263,183,1000,700,-570,733,-554,1000,-1000,183,975,654,781,-1000,1000,44,1000,445,-42,1000,-218,1000,241,589,-1000,-553,-147,-1000,465,-1000,-1000,-806,-896,-609,-433,98,1000,-367,-408,-630,-60,-23,584,-861,-42,100,-660,-368,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Ng==|getMinY=29:java.lang.Double:LUluZmluaXR5|getMaxY=25:java.lang.Double:NjUuMg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{1000,826,-47,652,-551,186,-47,634,-453,1000,1000,-1000,-907,721,825,474,-400,1000,-338,524,-1000,573,-469,767,-830,869,523,189,434,197,-1000,390,-391,-404,1000,-1000,-799,-459,-310,1000,418,590,330,563,-154,419,1000,530,617,-1000,-929,-439,565,-365,1000,-398,-794,724,-112,205,-520,1000,380,601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MTE=|getMinY=25:java.lang.Double:LTEwMC4w|getMaxY=25:java.lang.Double:MTAwLjA=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{921,587,-91,458,-810,62,-579,569,-987,840,49,-738,-363,745,825,445,-148,-409,-146,1000,-1000,1000,-510,454,-706,440,259,552,-335,72,-886,35,-690,101,533,515,-859,678,-651,1000,266,917,59,-77,242,669,1000,-214,736,-926,-882,-421,491,-539,306,260,-528,322,255,-513,-723,860,-80,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "clone():java.lang.Object",
            new int[]{941,698,-23,-311,961,575,682,-47,-364,-269,-1000,-150,952,-1000,741,582,315,779,414,113,533,-446,501,-482,-1000,735,595,-987,129,-303,83,1000,-1000,-44,327,-10,331,-411,658,-919,-751,-534,-1000,-633,-436,380,-556,-1000,-425,-906,858,-159,249,-567,-783,-96,1000,330,-711,-396,-597,-553,-211,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-240,305,1000,-165,754,48,-311,713,-219,3,-1000,-1000,1000,-199,478,518,1000,766,303,996,-1000,1000,-664,-680,-1000,932,-517,-16,1000,1000,364,-867,-1000,317,1000,1000,636,1000,-1000,-1000,327,1000,-615,-561,434,39,-665,473,72,1000,1000,388,763,-1000,375,706,-1000,1000,1000,-1000,906,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-113,-136,193,125,232,-841,526,-40,499,-433,458,746,-874,712,-707,-24,-467,-737,235,48,239,-893,-343,-44,483,-449,-464,-398,575,-630,-540,811,1000,1,13,46,-1000,-787,1000,1000,121,-442,130,-764,-448,-749,495,-862,-520,-203,-124,-503,147,1000,1000,1000,-150,264,-398,658,-661,202,1000,335}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mg==|getMinY=25:java.lang.Double:LTU3Ljc=|getMaxY=25:java.lang.Double:MTAwLjA=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-474,690,-4,-577,651,-848,-216,-528,728,-1000,-931,-731,1000,-398,-1000,1000,122,-1000,-233,24,-664,289,-65,539,-1000,542,687,1000,1000,1000,762,-1000,-1000,1,180,312,-849,1000,-525,1000,361,1000,277,-232,1000,-749,566,-1000,-1000,912,700,48,1000,-1,1000,1000,-1000,1000,1000,-617,-589,-745,-486,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-604,449,1000,360,510,453,309,750,486,268,-400,-863,400,-335,1000,-440,614,842,912,744,-910,1000,-1000,-1000,-725,1000,-463,-695,-132,412,1000,-1000,-1000,910,1000,1000,826,364,-1000,127,953,1000,-1000,-287,-1000,1000,-189,-62,729,599,215,-582,-928,-480,-1000,1000,-344,1000,824,-1000,1000,-1000,88,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-1000,-1000,-914,1000,-1000,9,82,921,623,331,1000,1000,-898,-1000,563,-350,-475,-852,-752,-674,1000,281,-692,-104,1000,-513,-501,633,-1000,-1000,240,433,1000,1000,-631,389,-920,-1000,565,1000,1000,181,54,-475,-998,-1000,-270,89,-997,440,468,-73,-1000,1000,-1000,1000,1000,309,-1000,1000,-10,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-60,-91,-959,-1000,472,130,294,552,-48,-1000,1000,-820,-1000,-551,-909,-1000,-818,919,-1000,-523,-38,146,-1000,757,1000,-713,-81,-114,-537,508,-770,1000,1000,-43,145,133,-986,118,75,389,-1000,-533,485,-1000,807,1000,-34,678,-508,-505,-1000,-201,171,556,1000,649,-906,-1000,-1000,119,59,38,-806,23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-655,-1000,272,965,-707,-981,697,1000,88,795,676,97,-247,-1000,987,1000,474,1000,-429,696,-752,881,-524,-638,985,735,-1000,-581,-1000,1000,487,14,-137,149,742,687,-120,427,-1000,124,-622,1000,-890,-562,-1000,354,-1000,1000,1000,698,-173,168,-1000,-467,-1000,-330,1000,416,-417,-207,1000,-40,-1000,-918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{178,-946,-615,933,227,-858,207,-56,1000,-1000,1000,-501,-1000,-560,350,-226,-686,1000,-1000,-1000,1000,-1000,1000,-654,1000,-1000,517,276,59,-530,-1000,1000,1000,-9,-1000,-320,-1000,-1000,1000,586,-400,-271,1000,-1000,920,-696,-11,392,-480,-207,-1000,-84,-1000,1000,795,652,-1000,-521,-1000,1000,-199,1000,1000,517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-717,-1000,-1000,-872,-1000,-841,1000,403,-237,493,617,1000,85,-1000,-707,679,-75,-1000,746,-903,239,-1000,-115,-78,-637,-1000,-1000,-398,396,-1000,762,1000,1000,514,-817,-665,-1000,1000,1000,299,121,-304,130,-564,15,-1000,-824,-271,-158,-203,-1000,66,-531,523,830,1000,-599,28,387,1000,-400,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-997,-790,-109,271,-891,678,716,-773,367,-864,808,703,-71,-999,-723,-738,709,-770,-985,576,619,-604,-234,-312,953,122,-928,-926,996,300,-91,48,4,465,298,673,-948,460,954,-266,-45,96,123,-416,431,-97,148,680,5,-752,27,-475,-240,-863,257,-647,983,795,578,-596,984,810,-102,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-1000,-191,-694,435,-1000,-405,-311,39,998,-299,412,774,-1000,-362,506,52,797,-534,-858,-289,192,-1000,-559,-585,886,-746,-575,154,270,-633,-153,415,893,763,-416,-312,-1000,-38,1000,161,-943,-339,277,-875,155,-1000,-343,-131,-875,151,-419,-446,-618,-166,552,747,78,369,-126,686,-1000,945,492,938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{-427,910,1000,-749,559,-854,-333,211,358,-1000,-1000,-1000,1000,1000,-130,1000,661,-258,164,571,-1000,1000,-1000,-139,-1000,1000,-142,405,1000,1000,213,-1000,-1000,1000,1000,809,486,1000,-1000,-1000,1000,1000,-730,-395,647,-327,381,-830,-992,806,1000,-37,1000,-980,1000,952,-1000,1000,1000,-946,163,-1000,-1000,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(int,int):org.jfree.data.time.TimeSeries",
            new int[]{338,72,253,-1000,690,-762,474,294,251,361,-5,246,358,1000,161,-505,514,456,-214,258,-610,203,327,-515,-1000,-941,-639,-607,1000,1000,-416,1000,-206,-1000,514,659,-846,116,623,-383,-976,588,170,-1000,-968,328,-435,761,871,589,1000,264,47,-674,311,1000,-793,-70,263,14,315,219,1000,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-171,-510,1000,367,-633,279,-977,493,732,-582,-1000,759,1000,-859,194,-1000,-593,824,-1000,1000,572,31,-240,-634,-1000,805,311,-742,-880,699,298,-105,-505,706,456,1000,-808,-619,-700,-55,1000,962,-1000,1000,771,1000,-238,-156,570,-965,-1000,-1000,-124,-545,-984,-18,-970,-394,1000,542,1000,-1000,-1000,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mw==|getMinY=37:java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=|getMaxY=29:java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-672,-1000,328,300,-605,-881,-944,166,488,-742,772,44,571,-130,759,-596,-1000,1000,-1000,442,623,-59,-310,-1000,-882,585,712,-1000,-1000,1000,-1000,-579,-371,1000,141,1000,-78,-1000,-1000,-396,35,1000,-587,303,-805,1000,-899,639,489,-341,-857,-1000,-250,750,71,184,-1000,-357,84,-1000,1000,773,-372,144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{827,841,-50,-1000,1000,-789,204,-973,-846,570,1000,-236,105,1000,55,1000,793,-1000,-673,-597,-1000,-199,95,1000,596,-865,-575,985,456,-818,-1000,1000,226,-1000,773,-1000,1000,795,597,325,-1000,-1000,-308,610,-329,-1000,531,-1000,735,-287,1000,1000,1000,1000,24,-1000,369,1000,-294,133,-1000,-422,72,711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=25:java.lang.Double:LTQzLjQ=|getMaxY=25:java.lang.Double:NjcuOQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{882,-641,472,4,216,-540,679,-434,178,-788,-841,838,-82,-934,688,-931,-534,389,409,-504,715,435,471,-107,-108,-328,-229,-793,-896,-39,-930,371,-526,933,-941,835,607,545,635,682,713,212,578,-746,553,-769,123,-835,584,-786,485,-701,142,-808,322,-85,338,-286,-696,-638,448,-211,-495,-959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{733,1000,124,336,-972,929,1000,-289,364,118,-1000,-1000,705,-111,-736,173,609,-709,7,-816,-1000,-761,-1000,1000,-428,-385,1000,-895,1000,-1000,-946,543,634,463,82,1000,-27,-547,-359,1000,-173,-788,-415,500,576,572,179,326,-1000,1000,1000,-880,99,-229,-428,1000,182,189,2,-201,344,-293,503,232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=45:java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4|getMaxY=25:java.lang.Double:MzEuMA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-171,-1,394,76,-361,-1000,-1000,310,42,-983,-1000,1000,1000,-984,343,224,-655,782,-800,1000,16,390,83,-685,-472,1000,198,-590,-1000,-68,1000,-1000,-533,720,41,651,-765,-676,401,-132,535,962,-1000,1000,-1000,1000,-329,-434,462,-884,-1000,-928,1000,-784,-780,-1000,-400,-939,1000,95,1000,794,-560,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=25:java.lang.Double:LTkxLjM=|getMaxY=25:java.lang.Double:LTkxLjM=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-898,737,-164,-697,-876,-913,-336,-973,1000,-443,995,1000,-504,470,497,-421,-27,-1000,-339,-551,-264,-592,-439,833,78,21,458,1000,-903,414,1000,618,386,273,337,-1000,575,-792,-38,-11,-177,820,-320,265,409,-900,-1000,-547,975,877,-219,28,-560,1000,930,503,-1000,1000,-8,-1000,-456,154,820,952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-1000,647,811,-694,1000,-334,231,-795,-12,1000,-250,-190,1000,779,-863,1000,1000,-556,-381,456,1000,-587,-172,1000,218,139,-1000,363,793,-997,826,1000,-302,-1000,877,-1000,335,1000,-596,-674,-649,-1000,-1000,1000,-121,-2,1000,-1000,-402,-1000,986,1000,1000,47,-1000,-1000,306,1000,596,1000,-1000,767,-1000,161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=37:java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=|getMaxY=37:java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{1000,321,-199,-330,-140,-295,828,-226,-1000,1000,1000,712,894,997,-358,1000,1000,-1000,-879,-994,1000,-1000,774,899,52,-1000,-1000,-36,1000,-947,633,1000,-354,-756,577,-1000,191,1000,1000,37,-1000,-481,-90,-54,-1000,-741,1000,-811,-1000,302,155,1000,236,1000,-308,-743,1000,1000,-521,1000,-1000,71,-745,704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MQ==|getMinY=21:java.lang.Double:MC4w|getMaxY=21:java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mw==|getMinY=45:java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4|getMaxY=21:java.lang.Double:Ny4y", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-10,1000,-199,-788,-140,-372,86,-1000,-23,72,-230,712,-306,347,-40,452,834,-1000,415,-526,-122,-630,-234,1000,696,-230,-479,1000,-621,106,1000,1000,182,-756,238,-1000,368,-295,1000,-194,-52,-481,-1000,652,1000,-1000,215,-1000,41,570,155,1000,236,-167,255,23,-248,740,371,-160,-1000,816,812,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:OQ==|getMinY=25:java.lang.Double:LTEwMC4w|getMaxY=25:java.lang.Double:ODUuNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "createCopy(org.jfree.data.time.RegularTimePeriod,org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeries",
            new int[]{-1000,266,569,-583,-1000,-1000,-136,-973,732,39,-419,253,-202,521,857,-708,-357,622,-1000,13,435,-195,-1000,319,-498,98,-316,411,-644,1000,703,-576,-523,98,-10,-264,-295,-1000,18,-1000,-1000,1000,-993,286,142,953,-543,1000,-391,811,-1000,544,-740,673,563,260,-846,565,-25,-344,-362,1000,179,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{-1000,-137,330,-810,25,299,1000,251,583,-1000,-886,556,665,710,604,1000,-126,37,368,-191,-954,283,852,-1000,30,-951,5,313,1000,-1000,-1000,124,422,1000,-417,1000,204,-1000,-614,-1000,-895,-195,324,925,-1000,-772,1000,544,22,-935,58,1000,-220,620,306,-165,227,-1000,814,-243,340,-881,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{-1000,-97,358,-838,-190,181,749,337,619,25,-704,492,732,-341,434,989,218,-48,-150,-999,-833,-148,321,-855,-373,-1000,497,349,605,-453,-1000,99,-442,901,-167,634,693,-754,-708,-875,-667,-121,-347,795,-845,-438,818,180,283,-1000,-138,1000,-152,176,315,-75,-357,-649,508,-34,201,-289,-974,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{21,268,573,-953,-79,779,-1000,504,138,486,545,-548,216,-175,510,710,-351,172,-896,1000,-155,759,-335,-684,-305,-457,-919,461,-419,1000,-375,-144,-1000,-664,-631,351,-153,575,1000,578,-661,-1000,-206,-387,944,-561,-1000,122,75,-751,-42,580,1000,246,483,-1000,143,412,754,-699,674,1000,389,528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{517,-15,568,-510,-800,284,-688,786,490,168,1000,-756,-1000,1000,130,-717,-1000,56,417,866,864,620,-356,746,8,530,-628,731,-1000,688,674,1000,47,-810,-323,-8,-1000,1000,1000,-133,611,-920,1000,-1000,573,1000,-213,803,587,896,-891,-1000,-364,-1000,1000,26,-984,1000,544,-966,52,-765,871,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{-737,-678,1000,-151,-1000,-1000,253,-26,-1000,1000,-153,-1000,18,-238,476,759,86,-422,225,658,140,480,-1000,285,-714,-770,-433,-50,-196,439,856,1000,89,-1000,-847,-148,-167,-854,-926,-1000,598,-126,697,673,233,1000,705,938,547,661,61,-460,1000,-1000,1000,-606,162,-387,820,-1000,-626,1000,435,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{-1000,-373,341,-1000,810,921,1000,-701,-963,149,-1000,492,624,797,1000,1000,-724,211,-150,-693,-1000,672,-927,-1000,-341,-1000,-456,482,1000,-453,-1000,143,595,1000,-809,1000,693,-307,-491,-758,-927,-121,204,1000,-14,-840,1000,1000,144,-1000,-944,1000,-4,2,664,-75,-1000,-572,363,-502,538,-917,-504,-562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{340,914,-556,-1000,-99,86,1000,49,-1000,135,-1000,184,816,215,1000,-1000,448,1000,-143,293,306,1000,120,-1000,-775,-777,444,1000,1000,-1000,-1000,-1000,1000,1000,-1000,1000,-486,-78,-1000,661,-1000,-1000,294,504,1000,-1000,913,188,266,972,246,749,788,-201,-92,-1000,-1000,-928,1000,-177,1000,-1000,896,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{-1000,-233,725,-1000,-1000,1000,-769,178,-671,149,1000,580,1000,-452,-172,501,542,530,146,375,-375,-518,-823,572,-328,677,-1000,1000,1000,89,901,495,1000,1000,-470,-531,74,269,1000,-893,-746,524,-1000,-925,1000,-1000,-814,-1000,-214,-1000,-197,-325,-54,-30,-322,-26,-995,-1000,-21,-265,846,-330,-471,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{1000,1000,-762,-329,-680,-11,-300,1000,1000,-1000,923,782,984,1000,515,-814,700,998,247,-500,1000,-103,1000,-855,-113,384,654,742,-295,-895,-1000,-1000,1000,767,-689,665,-593,-177,-1000,1000,-1000,1000,-155,-999,449,-1000,-768,-117,-1000,-80,1000,288,622,925,-695,151,289,-610,1000,329,1000,-1000,1000,-513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{1000,1000,-370,-443,-472,543,-863,786,97,-962,1000,246,1000,1000,911,-1000,677,1000,79,-401,1000,-658,960,-820,-515,698,-350,1000,-413,-242,-565,-1000,1000,-30,-396,467,-26,491,-1000,1000,-1000,384,-611,-1000,1000,-1000,-1000,-189,-1000,165,898,44,1000,589,-883,-496,-751,-364,1000,36,1000,-98,1000,11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{-400,805,-823,-1000,-247,816,-476,1000,138,486,838,1000,1000,61,614,579,1000,1000,-492,-1000,78,-907,850,-1000,-697,183,829,1000,829,-1000,-1000,-1000,972,-664,-557,409,592,575,-1000,1000,-1000,1000,-1000,257,137,-1000,-154,122,75,-1000,1000,1000,715,246,-1000,418,-193,-1000,898,1000,1000,-1000,688,-513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int):void",
            new int[]{695,202,-917,379,52,428,0,499,485,-585,709,188,-272,1000,300,179,-615,-300,-653,-40,-101,1000,127,-826,474,-164,23,12,-293,251,-995,-175,342,390,-473,1000,-901,802,267,567,-777,32,1000,-806,920,-710,-660,1000,-569,-645,61,896,1000,1000,587,-850,638,716,800,-452,593,-870,985,893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{1000,-359,-1000,628,60,1000,-731,160,-1000,-1000,-923,-513,-1000,1000,-706,-782,969,-937,-1000,-428,-866,-859,400,1000,933,1000,-292,-904,448,-908,1000,-509,-451,1000,-1000,165,-360,-661,-210,-1000,323,-1000,-466,1000,150,-397,84,-74,-1000,491,601,-18,-1000,-964,-1000,-409,455,575,-1000,511,984,-965,499,-100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{166,711,890,434,-489,-537,658,305,-321,-335,-542,54,192,-108,1000,20,-84,114,-858,-1000,376,-55,-324,94,-155,-59,-516,-22,-1000,314,694,235,-572,-652,2,587,44,-682,-304,-693,-725,96,707,-1000,-350,-1000,585,115,-312,1000,550,335,-976,300,-265,14,-246,-1000,827,595,-711,107,-60,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{31,928,-367,1000,1000,-366,742,-169,-1000,-486,-825,-899,-1000,1000,595,-1000,1000,-209,-1000,-1000,-1000,-147,69,245,211,741,408,6,-1000,-749,668,155,886,1000,5,-209,-214,-1000,-930,-1000,68,-1000,476,-1000,1000,-1000,767,1000,-1000,561,340,353,386,-873,-275,-993,575,-25,-1000,-90,-818,-1000,-910,589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{-903,13,-881,-686,301,900,87,280,-362,-887,-287,608,853,394,834,473,644,-856,-773,-786,-141,663,90,-91,349,-660,-104,-504,-996,774,976,-542,-411,-113,342,417,628,186,-82,387,-149,-478,625,872,458,894,-430,-471,-169,-803,-40,691,-794,130,-682,-318,-673,-78,-945,-431,84,21,-447,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{134,465,878,1000,-1000,1000,-362,-619,1000,1000,-631,-862,917,-578,300,1000,-1000,1000,-550,1000,1000,893,426,-769,941,-1000,1000,-112,67,172,-1000,1000,1000,-574,1000,-585,-1000,-438,-732,1000,-735,784,-962,-315,-811,693,984,-424,643,-1000,1000,605,1000,552,799,1000,-192,-626,1000,35,1000,-641,622,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{293,486,-167,-135,-632,655,-1000,-367,328,337,595,-794,-1000,-1000,600,499,291,396,-502,551,897,-422,-742,-406,1000,-766,1000,-1000,1000,1000,92,1000,1000,-540,-68,-267,-1000,-888,-825,1000,229,359,-236,334,-1000,506,85,-1000,-1000,-1000,416,1000,813,236,-912,429,-251,1000,639,151,256,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{-116,-369,-317,-645,-1000,292,-731,756,569,1000,605,177,1000,-698,-715,1000,-1000,-937,-917,582,1000,129,63,-16,933,1000,-292,-921,98,813,-1000,-155,-451,-445,-1000,429,265,93,-837,798,-286,-1,-800,238,-41,1000,431,-1000,215,-1000,1000,295,-466,497,-282,-409,-1000,928,964,1000,478,-945,-247,117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{928,591,1000,1000,400,1000,1000,-5,13,127,-984,-434,73,-76,1000,-19,-378,-228,-437,-436,-323,-566,992,-16,-255,729,-381,563,-1000,-1000,-931,-551,-120,13,-385,1000,419,937,-804,-1000,-95,244,897,-238,-264,-398,196,873,88,963,625,373,-1000,-435,-282,406,550,-1000,-1000,568,-812,-702,1000,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{854,508,-131,-664,109,-801,112,-1000,-273,-234,742,307,-1000,575,1000,-886,597,434,-416,-440,-313,1000,-374,700,261,-318,84,111,-734,-1000,-265,364,1000,-735,1000,-320,-1000,107,501,1000,-597,-269,-273,1000,-819,-1000,661,623,-1000,1000,592,605,-291,1000,-815,-1000,177,-367,-890,-101,1000,-235,-351,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{1000,-743,1000,215,-1000,-638,-119,182,74,1000,-1000,-1000,20,-660,-296,1000,-1000,999,-1000,909,1000,-1000,916,38,1000,18,-767,-1000,1000,-741,-810,-235,-213,254,-1000,1000,487,-1000,-1000,1,348,272,-526,-1000,-502,1000,202,-1000,8,-1000,1000,1000,-1000,-690,-808,1000,-683,952,579,1000,-407,-768,930,543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MTA=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{658,125,843,485,-271,-456,284,-212,-560,-499,-1000,-739,-527,-423,651,-400,308,278,-947,-927,109,-766,6,195,341,89,-636,-434,-629,-117,757,208,-676,-21,-522,567,674,-921,-644,-750,-250,-134,204,-1000,-405,-233,408,-82,-655,-7,818,910,-994,-305,-785,-394,-193,-818,934,584,-287,388,351,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(int,int,boolean):void",
            new int[]{690,-286,-945,-380,-632,1000,-1000,-256,689,309,1000,-906,457,-1000,-505,902,717,1000,-196,711,897,1000,-1000,105,1000,-1000,1000,-1000,1000,1000,494,1000,1000,-206,-535,-1000,-1000,-1000,-1000,1000,-872,742,-956,1000,1000,449,73,-330,-1000,-1000,74,1000,1000,-96,-1000,1000,-22,1000,580,1000,113,-842,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{-29,-231,-1000,696,569,260,198,210,-202,-226,1000,325,1000,-783,-82,399,1000,1000,294,-906,942,-1000,10,1000,-313,1000,-1000,-6,1000,288,-196,-909,159,972,1000,-913,1000,1000,-674,-772,1000,-1000,1000,-1000,150,-943,-1000,-637,-1000,384,-662,-1000,803,1000,-179,1000,309,360,-981,-360,-960,997,612,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{494,230,102,753,380,734,546,304,677,-634,571,445,-400,999,-1000,208,-400,121,393,-685,-480,-36,276,764,-394,-227,184,849,178,146,-1000,201,-398,278,-400,333,276,-89,216,513,-167,128,440,-494,665,887,470,-216,54,1000,-936,410,863,483,1000,-809,-248,-1000,-1000,641,-822,-199,126,547}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{1000,112,-535,1000,884,493,20,862,733,-983,334,762,232,999,-1000,-1000,1000,170,610,-1000,-665,-704,370,939,-281,342,59,1000,1000,-459,-579,126,-58,278,-136,667,583,530,380,833,-66,-19,198,-778,1000,492,211,736,-61,625,-936,-93,1000,879,1000,-756,12,-578,-1000,121,-1000,773,-201,601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{-45,-832,-90,-199,279,524,236,-97,919,-79,588,547,97,1000,-627,-392,571,763,690,-71,-25,-46,865,396,-1000,42,-644,55,1000,655,-44,-163,515,1000,-256,-201,561,116,766,-352,-307,-790,-20,-594,148,613,789,-24,-494,-464,-1000,-282,477,435,-492,-606,629,176,-190,256,-581,-388,-353,923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{-779,-281,-170,1000,-570,-71,-1000,-86,-341,-291,642,-340,430,-1000,566,91,863,739,-264,61,528,144,-304,-1000,-306,326,-821,-628,493,335,-332,-918,-713,-702,1000,-404,-887,-275,-1000,224,736,451,-442,72,-493,-1000,-1000,-596,121,-295,767,407,-579,37,624,1000,297,1000,1000,-794,89,-1000,-393,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{-1000,1000,661,1000,-1000,334,-937,1000,-1000,435,-942,-1000,1000,1000,1000,632,1000,1000,1000,1000,1000,-1000,-1000,1000,-1000,426,-1000,-1000,1000,1000,-948,-1000,-1000,-1000,1000,-1000,-1000,-1000,-1000,75,-307,1000,1000,1000,-1000,-744,-1000,-1000,-213,499,1000,-1000,-1000,1000,1000,1000,-252,0,-1000,-144,1000,-1000,1000,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{309,-224,541,431,586,883,1000,716,-36,-1000,271,72,-1000,1000,-1000,420,-831,2,879,-999,-468,291,323,1000,-436,-805,184,1000,-62,-127,-1000,825,-315,139,-1000,471,276,-630,556,-1000,-486,660,442,-482,1000,961,1000,-288,407,1000,-1000,891,1000,357,1000,-1000,-1000,-1000,-1000,622,295,-313,-93,547}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "delete(org.jfree.data.time.RegularTimePeriod):void",
            new int[]{-1000,605,680,52,-751,-280,-164,-247,384,445,20,-373,-772,400,1000,747,878,1000,929,-198,522,904,251,-443,-890,-9,-1000,-663,1000,88,572,-643,163,-921,-10,-248,-13,683,122,-54,549,324,-845,-110,-976,-431,-847,397,522,18,45,56,-961,282,-666,541,-520,863,1000,-1000,-694,451,-361,852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{-258,541,-1000,-343,-986,-647,-76,-602,44,1000,337,-106,-38,-393,-1000,-243,-262,-658,120,1000,-805,-226,851,-19,-1000,-217,-247,-842,-586,-65,-377,501,1000,-315,-202,-213,-468,-42,-665,884,-57,-416,394,954,749,-438,15,-1000,1000,-40,938,95,304,1000,-350,50,1000,-359,-1000,1000,-788,-229,509,-46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{1000,731,-1000,-128,-193,-525,878,-602,-281,-873,-96,473,-337,-1000,-793,-318,-594,-478,-132,509,-885,686,-81,-730,709,-646,-247,-842,277,1000,-278,-627,720,500,978,-637,289,-1000,-766,1000,-781,176,-1000,-29,1000,-619,228,-1000,1000,-40,-146,-322,879,252,-208,-410,514,-43,-1000,1000,-788,271,-729,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{499,1000,287,-437,-210,-821,-1000,125,920,614,533,533,-1000,-148,297,-330,-1000,-83,-679,300,879,416,-596,-858,-1000,-640,939,840,344,209,988,246,-214,1000,1000,-66,50,-34,1000,1000,-451,-339,-692,-1000,449,357,-608,-112,570,-642,-945,330,1000,-11,-261,-1000,-66,1000,41,-65,249,1000,226,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{0,-1000,-388,-348,-755,-216,413,848,-353,697,-505,-315,0,-15,-40,-959,267,182,-149,-249,0,0,0,-235,724,895,193,404,1000,-637,91,-1000,-1000,-88,-585,240,537,1000,-412,-1000,87,0,1000,-491,-443,460,220,1000,-886,-885,994,138,-984,-272,1000,-601,482,-288,694,-1000,781,-509,507,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{-603,385,1000,107,-644,575,844,151,-545,-482,-1000,1000,-341,56,923,-1000,-982,-131,87,969,1000,977,-295,30,496,-1000,758,1000,656,-13,1000,-953,-335,988,-31,503,79,-1000,910,-611,565,-1000,102,-72,350,976,-428,-7,467,-1000,674,604,-476,-461,931,-305,-928,997,-1000,-1000,196,-848,-223,-78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{620,541,-1000,-730,-993,-693,-334,292,-548,-1000,-610,-106,123,-77,-1000,-503,-262,330,1000,733,-994,559,962,-1000,-981,-135,-259,-888,1000,598,-501,768,1000,-400,-811,-213,165,-642,-1000,296,358,-416,-1000,-175,278,-168,1000,-1000,600,-368,1000,471,293,969,1000,-681,382,-806,-678,630,-1000,-26,234,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{-612,-1000,-474,77,-1000,-556,479,156,-614,592,949,-594,261,-322,517,-538,-62,-767,-897,278,-867,-1000,58,577,394,883,-369,-545,-779,-1000,-882,119,-136,-878,-113,1000,115,892,184,659,901,670,-515,426,-289,-2,-109,-927,43,371,-321,-456,-896,-216,-120,-151,177,-383,401,737,-207,600,1000,-623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{707,-561,-388,-305,245,-399,38,57,-547,-230,-1000,-70,-501,-306,597,-1000,-309,333,363,-995,533,521,73,-544,1000,-1000,390,919,1000,31,607,-1000,-769,557,300,-543,1000,276,-248,-1000,-796,266,413,-738,-443,486,-29,1000,-899,-489,105,-473,-513,-616,746,368,320,224,694,-1000,516,-407,-163,-917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{-1000,-1000,1000,-855,-1000,-421,1000,1000,-556,1000,897,-990,-14,1000,1000,-243,237,262,-632,-673,584,-1000,-145,815,1000,1000,1000,1000,-363,-1000,709,-1000,-1000,-1000,-1000,1000,432,1000,695,-1000,1000,-307,1000,-265,-1000,1000,15,1000,-1000,-1000,195,426,-1000,-1000,729,-531,434,25,1000,-1000,1000,-247,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{-543,-345,-363,-1000,-278,-848,398,653,-172,550,1000,-398,-147,684,1000,303,44,913,513,101,492,-717,-936,-871,-121,1000,616,1000,344,-169,-129,1000,890,-356,101,138,954,765,1000,-1000,-379,-341,1000,-380,83,1000,-1000,-1000,-428,-1000,-450,875,-449,-383,324,-699,-208,521,1000,-626,-43,1000,1000,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{-173,-400,-388,-403,-762,-556,685,119,-675,1000,-969,-845,146,118,-40,-1000,365,129,539,-249,-30,-379,1000,-47,724,-583,-29,515,667,-161,-157,-195,172,-364,-541,-236,823,271,-177,-1000,-136,409,1000,-523,-619,673,159,1000,-886,-684,1000,-132,-1000,-272,792,354,482,-300,694,-1000,-140,-103,748,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{1000,1000,-1000,-593,713,-421,-157,-360,-114,-1000,-551,1000,-1000,-1000,292,967,-390,249,36,-205,-209,1000,-681,-1000,-474,-1000,756,-746,1000,1000,583,-1000,1000,1000,1000,-1000,984,-1000,-1000,1000,-1000,-733,-88,-485,1000,638,-192,-548,1000,-1000,-1000,-1000,343,1000,659,-715,1000,-674,-1000,205,1000,-973,-682,-530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{-1000,-834,698,49,-827,178,1000,347,878,666,-635,132,969,360,-550,-1000,188,-746,-196,476,35,-749,502,1000,1000,533,-433,607,-632,-1000,-229,97,-232,-824,-1000,1000,-352,328,1000,-788,1000,299,-302,787,-970,849,-315,-277,363,567,782,805,-1000,-1000,-239,-43,-1000,895,473,135,-269,954,1000,-899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "equals(java.lang.Object):boolean",
            new int[]{449,-1000,-431,-973,-1000,-776,188,-12,-1000,1000,305,-1000,-162,513,407,-28,462,-5,-590,305,422,-184,-818,-320,921,1000,1000,-11,-67,-458,941,-520,73,-1000,-540,519,1000,-1000,-254,74,-604,-125,766,-1000,1000,837,674,18,-932,-432,-381,65,1000,-959,-404,-1000,1000,-274,1000,51,656,-169,-590,-614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-463,1000,-1000,621,-480,-606,-544,298,1000,38,-271,440,165,432,700,-656,1000,-591,887,638,102,-137,1000,589,-1000,241,1000,-677,455,866,378,-152,903,722,-31,-168,-1000,476,23,194,-941,1000,-893,982,636,-766,1000,-248,-1000,1000,876,86,-219,325,-1000,703,488,825,-1000,1000,-226,146,-1000,-719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{221,-822,740,1000,46,508,68,-731,-753,219,13,165,-1000,159,-1000,1000,-708,262,147,399,-437,351,-1000,-1000,-159,-1000,-478,-138,-752,-522,-1000,1000,311,-1000,-289,-1000,1000,331,1000,-97,544,-1000,365,-1000,-256,9,131,1000,181,-1000,94,555,814,379,1000,400,-1000,-914,-58,104,376,-1000,-22,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-503,626,-1000,1000,-111,-861,-783,338,482,1000,-425,763,-29,1000,1000,697,-160,380,711,1000,30,830,689,-248,-1000,-19,1000,-608,-1000,642,-1000,-988,1000,-1000,76,-1000,-1000,1000,126,-420,-1000,1000,-167,319,1000,843,289,924,-188,167,935,1000,52,1000,1,989,-343,-1000,-944,1000,43,-507,-1000,707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.TimeSeriesDataItem", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{371,-674,136,28,98,213,139,-413,-283,148,-218,561,-1000,-179,-864,622,-469,256,984,53,-787,1000,-486,-982,282,511,-157,521,-725,728,-904,-449,212,-493,80,-1000,281,-248,854,0,-281,373,491,-73,27,52,549,669,-463,18,-251,-381,438,1000,-58,186,-10,300,-179,-147,-158,-130,-309,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-624,-14,-1000,893,-1000,-444,802,-31,-204,954,-594,57,-1000,47,228,664,-160,-176,708,1000,776,777,-1000,-244,72,774,-369,-816,-995,-738,-863,-280,164,-321,760,-1000,1000,-937,701,-652,-675,1000,231,321,-223,-217,154,-385,359,268,363,-602,-723,1000,-1000,1000,42,-228,323,336,-1000,-1000,-1000,241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{755,-829,-220,75,-1000,-186,1000,605,599,686,90,1000,-842,-1000,981,1000,-326,656,-924,-852,620,959,1000,330,677,842,1000,-109,-454,84,729,-892,-777,-40,81,431,-615,-1000,1000,777,1000,309,1000,912,-643,-1000,-155,-1000,661,479,-277,-879,-52,-1000,-1000,-671,937,180,-1000,57,-1000,407,634,-895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,762,443,1000,879,-1000,-309,699,1000,369,-245,804,-189,1000,414,-13,-469,-943,-206,794,-930,-107,1000,-11,-987,-682,1000,-799,-75,1000,-417,858,212,-641,-148,-650,-1000,-248,747,92,-867,396,-271,-383,27,52,-321,261,382,-712,1000,1000,1000,921,946,1000,-1000,-826,-818,-147,668,-4,-124,286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{1000,-559,-362,-29,219,-17,-238,457,479,422,943,-1000,657,-375,1000,427,-574,546,652,-310,1000,565,-1000,170,-1000,1000,22,1000,483,658,-215,-1000,660,1000,325,235,429,-231,-1000,901,-1000,842,450,898,1000,143,1000,-373,-309,1000,-463,-370,-1000,-414,-335,-626,1000,394,326,-851,-211,1000,-687,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-638,52,-242,-78,153,368,466,101,165,134,405,1000,595,-628,248,955,-1000,-462,-777,-803,211,-801,-150,-1000,-107,318,-403,469,229,288,-627,-363,185,33,398,-5,176,-680,817,1000,1000,-1000,1000,-338,-89,-449,-650,-5,1000,-800,487,-46,1000,-217,257,-22,-600,723,-338,-610,-280,327,949,-555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-454,940,-235,-168,-71,149,149,1000,1000,184,259,1000,956,76,1000,-225,659,-917,-769,-1000,-689,-546,1000,245,-51,479,1000,-283,229,1000,1000,-293,149,673,-387,1000,-1000,-740,253,1000,719,-420,450,650,1000,-310,-415,-1000,201,241,933,373,935,-1000,-1000,-277,-29,1000,-1000,502,-621,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,-14,-284,966,-1000,-613,831,184,-204,-568,-341,449,-874,366,-393,1000,-1000,-462,-84,1000,25,173,-1000,-1000,458,-772,-370,-764,-643,-1000,-1000,449,170,-1000,530,-1000,1000,870,1000,-226,263,1000,860,-558,-241,596,-654,-448,1000,-494,449,37,130,586,-998,1000,-1000,-680,323,108,842,-1000,-606,326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(int):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-366,-809,-92,11,-111,213,1000,338,248,931,320,1000,-171,-469,1000,1000,-1000,-829,-1000,-126,576,30,-889,-843,-288,668,-485,263,-1000,-284,-1000,-742,38,-148,76,-823,-30,-223,587,850,533,-1000,1000,-683,1000,-718,-1000,28,1000,-1000,8,245,793,-287,1000,22,-1000,52,-98,-845,-162,-27,692,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{184,844,1000,1000,724,-476,-801,-1000,1000,-994,488,93,-711,-1000,-1000,974,-475,441,-1000,204,1000,-1000,-441,-256,486,1000,1000,-388,1000,1000,-251,-1000,1000,540,-1000,825,394,1000,-307,-260,1000,-1000,-1000,-1000,-1000,-815,428,1000,-35,-61,1000,-387,1000,1000,1000,528,-1000,-172,866,-1000,876,-1000,-614,-590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{1000,-237,327,1000,571,-387,-179,-976,819,544,1000,360,730,-1000,-544,1000,-886,1000,-271,-1000,891,-782,-1000,878,-15,54,1000,153,903,906,-1000,-610,915,927,-1000,1000,676,310,183,1000,900,-385,1000,-777,-1000,-1000,1000,193,1000,1000,634,-350,450,404,1000,1000,-1000,881,631,-1000,996,208,277,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{335,-1000,1000,1000,847,129,-192,-1000,307,1000,1000,1000,1000,-155,-450,878,492,1000,1000,919,1000,74,-1000,1000,-630,-608,417,630,1000,-189,806,511,122,1000,-1000,1000,1000,-1000,171,1000,1000,294,1000,895,-1000,-1000,849,-504,523,1000,-263,-1000,106,-254,335,75,-1000,1000,844,710,233,1000,793,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-136,-112,66,359,555,-802,673,-497,365,40,1000,567,-223,139,455,275,-532,73,756,79,612,-300,-1000,1000,-1000,510,-991,383,21,570,-1000,-295,865,1000,-250,162,674,-887,77,113,126,-624,487,875,-194,-838,-433,955,460,-107,62,-1000,122,-476,478,1000,-407,34,855,0,-781,-585,768,-379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-474,-436,807,64,804,-217,755,-952,238,1000,585,-89,449,417,-965,222,1000,345,1000,-907,746,1000,-730,-48,-1000,1000,-972,498,1000,96,-1000,905,-432,1000,218,641,1000,-1000,1000,512,1000,474,303,1000,-714,-821,49,-8,589,1000,-372,-1000,-120,-746,634,-699,-151,-960,-136,1000,179,64,585,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,55,658,-658,-191,-1000,12,1000,157,64,-206,-108,-1000,-55,1000,791,-899,-1000,-370,1000,692,-1000,1000,287,181,19,-642,-321,1000,-502,485,327,376,-450,-1000,-1000,-1000,1000,-916,-1000,-735,-1000,-1000,-535,-728,200,39,904,-919,-160,-76,1000,544,-652,-196,282,492,-724,-1000,-796,676,195,1000,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-35,-32,466,883,510,89,1000,-1000,786,-182,-628,239,394,-400,-588,-424,-397,470,334,-1000,103,-40,-877,1000,481,1000,-427,833,920,1000,-635,-27,228,332,-158,746,569,262,-322,669,36,208,315,-561,268,-1000,273,194,799,904,1000,-938,892,246,427,503,-937,400,1000,18,182,498,483,-266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.TimeSeriesDataItem", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{145,203,232,67,985,-212,-1000,-92,-767,588,1000,521,652,450,387,559,-457,-25,-678,-32,1000,-484,-32,-153,-9,-49,867,-691,-363,-69,-1000,374,222,177,-605,217,379,-372,-863,905,364,-741,1000,1000,-1000,-759,54,96,457,408,-575,415,-1000,-686,947,467,239,-101,-494,536,-166,-224,198,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{344,707,2,209,911,-1000,-825,922,659,246,761,416,282,-1000,457,884,-1000,485,-864,127,270,-689,28,225,291,-352,929,521,259,866,-1000,-443,912,-132,-1000,-588,154,154,-362,1000,76,-319,176,-1000,-764,171,1000,487,611,-96,477,720,214,641,916,651,-1000,764,473,-1000,994,-217,-477,335}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{706,844,-1000,342,1000,-476,-740,52,1000,-1000,488,811,759,-552,1000,974,-1000,-274,-1000,-235,-255,-1000,-441,1000,1000,-1000,935,-382,1000,432,-773,249,-200,-1000,-1000,59,394,898,-1000,604,1000,-49,1000,343,-245,746,1000,134,511,-206,984,621,-648,371,264,1000,5,1000,1000,-1000,1000,-127,-1000,796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDataItem(org.jfree.data.time.RegularTimePeriod):org.jfree.data.time.TimeSeriesDataItem",
            new int[]{-1000,311,854,-283,833,-1000,726,-872,448,922,271,733,-41,-1000,-1000,1000,-1000,1000,702,-498,647,1000,-399,389,-1000,-485,-1000,1000,986,-1000,-1000,1000,83,969,-1000,-632,-533,-299,-721,1000,-80,1000,-603,278,377,-1000,1000,135,1000,1000,-390,-1000,1000,-1000,7,920,-247,286,-370,-504,-211,1000,1000,-472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{186,972,1000,199,-1000,-546,-786,326,-1000,-796,1000,163,-745,-429,503,685,-402,-732,1000,-1000,625,-1000,751,-943,85,272,1000,852,-1000,1000,287,-177,497,-616,-475,-613,986,1000,56,282,-87,-135,224,-1000,236,-443,1000,-157,1000,-281,-948,1000,248,-1000,-580,1000,-33,627,908,99,505,312,647,435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{123,-146,448,-627,-141,262,-522,-541,-384,-1000,1000,273,479,-990,187,764,543,230,571,-1000,-1000,-75,-88,-160,1000,1000,-1000,-1000,-625,227,841,1000,1000,-1000,645,1000,284,1000,830,-1000,-424,-505,20,-575,1000,-299,-43,-442,237,-619,851,49,-1000,-214,-1000,37,533,-37,-1000,-399,1000,-407,995,26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{-604,938,-619,250,-402,175,-257,859,-138,-1000,972,84,675,-571,-1000,-487,1000,-935,1000,-169,1000,43,1000,-94,1000,-233,-567,999,465,593,1000,1000,1000,-822,798,1000,1000,-153,-564,-1000,112,-1000,1000,-1000,1000,-595,-586,790,1000,-20,777,-1000,-1000,-777,-1000,-48,1000,-37,-897,-1000,1000,-569,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{-302,359,-941,-224,598,-229,973,-1000,-551,-621,545,662,-1000,-1000,-888,618,-118,963,1000,1000,724,-464,498,519,-1000,-211,1000,-536,-445,-126,-1000,-1000,976,487,-221,-1000,-327,-151,754,384,731,1000,580,1000,-289,1000,703,-1000,417,-1000,-1000,995,968,-1000,1000,-373,470,432,540,-533,428,245,487,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{-341,364,673,-134,-20,399,-577,-2,0,-1000,1000,-57,905,304,-594,361,1000,624,713,-968,-1000,504,1000,1000,497,106,-1000,-639,265,3,526,1000,1000,903,1000,756,333,-725,224,-1000,-139,-728,1000,-152,1000,203,732,520,477,-170,679,-1000,-1000,-221,-954,-888,705,724,-1000,-1000,1000,-839,438,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{457,432,448,-114,-947,809,-622,-14,-377,-1000,972,273,479,580,187,1000,314,-45,571,-531,-1000,681,288,-160,1000,304,-1000,-1000,145,-233,1000,1000,819,-1000,609,1000,562,919,257,-1000,-424,-1000,488,-1000,1000,-91,1000,1000,-27,167,1000,-922,-1000,692,-1000,-108,501,495,-1000,-399,1000,-956,362,198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{1000,-848,-315,-903,-1000,-711,-79,0,-608,-716,738,515,-279,-228,492,1000,-650,-727,900,-1000,-474,-1000,-294,-604,1000,416,-568,-765,-889,-1000,939,1000,1000,529,-790,1000,572,232,213,-903,-547,-781,-279,-640,1000,-1000,534,336,66,38,299,754,-1000,155,-979,1000,-101,-691,-412,-852,976,-103,1000,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{859,612,1000,324,-1000,-457,-621,-2,0,-656,400,731,-618,304,1000,-363,-1000,-468,305,-1000,1000,-278,-132,-77,-314,584,1000,1000,-400,1000,-258,-443,424,-552,636,-873,333,1000,790,497,-1000,-217,-744,-47,122,-436,218,-312,77,96,-1000,1000,332,291,-1000,1000,-672,724,1000,1000,-254,-839,438,-82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{441,364,228,446,-690,369,-786,-81,-1000,-1000,919,450,-643,-1000,112,1000,-178,-425,388,50,1000,-939,408,-1000,471,106,1000,695,-1000,1000,12,-96,-68,-813,-848,-770,185,1000,1000,-27,11,685,-349,-612,-319,131,732,520,811,-279,-1000,990,290,-884,818,1000,497,724,288,886,1000,782,945,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{1000,-1000,224,-1000,-517,579,-332,-1000,-862,-1000,1000,985,71,-249,1000,1000,-857,617,136,-1000,-1000,-400,-612,-965,1000,1000,-1000,-1000,-1000,-661,757,1000,825,1000,-205,1000,-124,930,1000,-985,-1000,-330,-1000,-440,1000,-1000,99,563,-716,-1000,1000,1000,-1000,489,-1000,1000,-333,-399,-1000,685,668,-388,897,-676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{13,536,104,-785,598,-208,526,-1000,0,-775,1000,-57,-1000,-1000,-357,1000,1000,1000,1000,54,461,-1000,483,-614,-427,679,1000,-638,-1000,-126,-1000,-812,1000,388,-1000,-729,-75,656,1000,203,591,1000,537,656,232,203,1000,-1000,495,-1000,-1000,1000,904,-1000,564,-185,187,448,476,-256,1000,351,1000,-144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("java.lang.String:VGltZQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getDomainDescription():java.lang.String",
            new int[]{-1000,1000,1000,1000,-488,106,325,621,-647,-644,-439,-655,1000,1000,-1000,490,314,-11,-91,1000,282,967,1000,744,-353,-1000,-588,-392,1000,1000,602,330,768,-965,1000,512,476,-100,-318,-767,349,-307,57,-1000,-388,-367,-1000,364,-4,1000,1000,-1000,-733,336,495,-1000,255,1000,-107,-489,955,237,244,-416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{496,-1000,-1000,-373,-185,-656,-145,-323,-1000,431,-230,-187,-128,941,841,-215,1000,930,1000,837,857,889,654,-520,354,-147,-1000,1000,1000,1000,-1000,-1000,521,-1000,-973,900,1000,-562,727,419,1000,-19,-1000,368,-236,670,246,280,-55,-110,1000,-285,32,-1000,352,-997,-1000,-868,-684,-760,-652,20,-18,149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTM=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-339,-820,-1000,732,-356,614,534,-1000,-1000,1000,-1000,-670,1000,1000,1000,-515,-508,-284,820,-578,419,-755,148,-1000,944,-1000,-1000,-2,1000,1000,596,-1000,-1000,-242,-1000,-1000,1000,168,1000,865,-297,-829,-1000,-259,954,-393,372,1000,-697,-998,330,1000,-1000,-365,1000,-892,-139,-1000,858,-1000,-1000,1000,516,60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTQ=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-47,189,-1000,343,659,-102,-363,-794,-70,547,-9,463,-262,581,841,-101,100,3,222,557,243,818,-127,70,823,-1000,-1000,189,740,-91,342,-596,-78,-409,-1000,-548,864,425,-82,-19,142,219,-777,765,-31,-1000,-306,-329,-46,-258,774,547,-485,933,627,-249,-279,285,945,-500,-246,392,-846,-377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTQ=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{890,849,1000,732,-603,427,-612,-184,-1000,-666,441,-311,533,-1000,28,-120,-1000,-477,-736,885,-646,-1000,249,-784,785,211,852,-1000,-327,345,328,374,521,1000,1000,145,-162,924,179,398,902,-70,-881,-706,-185,-891,1000,423,71,-415,-1000,-871,-813,568,-465,785,1000,1000,1000,1000,72,-829,-642,-98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{120,1000,-530,1000,741,-812,-534,934,-876,-1000,506,718,-1000,-704,1000,20,-1000,-1000,-869,34,-93,-1000,-775,1000,1000,-1000,290,-1000,-207,-632,1000,1000,-817,-151,-374,-892,348,1000,601,-531,-712,-1000,-611,292,297,-1000,-111,866,-1000,-571,-1000,-108,467,308,-515,-763,1000,-466,311,1000,1000,961,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTY=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{65,-981,-1000,10,60,-408,-1000,-138,-547,174,-461,169,-1000,1000,764,-158,400,496,1000,796,1000,1000,1000,277,773,-1000,-1000,686,1000,1000,736,-1000,65,619,-1000,330,954,518,282,1000,362,580,-531,1000,-286,169,-422,-78,-909,-265,1000,-86,-61,-453,1000,-1000,-590,1000,-179,748,-653,1000,-578,-391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTQ=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-289,-860,-1000,874,-621,-328,-64,-943,-295,1000,-1000,-187,28,1000,1000,-89,144,-552,71,164,393,-726,72,-584,336,-1000,-1000,-413,1000,538,-310,-689,-1000,206,-1000,-1000,1000,317,886,481,-724,-239,-409,25,507,-593,-219,323,-682,-320,484,491,-546,316,1000,-188,328,-1000,822,-476,-974,831,354,782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTQ=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-139,-115,-1000,184,-750,1000,-558,161,-1000,-138,-523,365,-630,285,-868,256,1000,1000,-238,430,1000,1000,96,-152,727,425,158,1000,57,-267,574,485,380,434,395,222,-1000,481,-1000,853,124,75,1000,647,-236,1000,-1000,280,85,118,1000,222,-375,-179,129,1000,179,858,-684,-918,104,-38,-31,235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTI=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{1000,-715,-326,613,434,695,-551,1000,391,-83,598,-105,-885,392,-1000,1000,235,648,-261,237,665,-400,73,400,590,-270,39,-169,-655,497,78,142,1000,-689,297,821,-629,120,-806,48,808,338,908,683,74,160,-1000,373,525,898,-400,-359,471,431,468,-208,-199,-318,38,400,-380,-490,530,835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-269,986,825,441,-1000,720,-558,-716,83,521,-978,-311,1000,-1000,-235,157,-580,-540,-297,1000,922,-24,502,-1000,277,1000,490,-780,433,-689,-1000,1000,-345,166,1000,-709,-514,-801,434,153,-775,-706,71,-1000,126,-418,749,45,337,-396,-826,-366,-1000,-224,-1000,1000,713,651,1000,-224,876,-1000,707,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{298,-870,-155,29,161,100,-555,-26,121,-969,538,912,-868,126,295,180,-246,431,-134,326,-577,-316,-415,112,616,-99,-119,417,-254,-378,481,-527,491,1000,-294,-28,-289,830,-154,391,424,322,-352,669,-125,565,-377,-258,604,-281,474,-3,-114,-213,-576,436,-140,685,1000,-350,857,607,-584,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getIndex(org.jfree.data.time.RegularTimePeriod):int",
            new int[]{-959,-870,-816,-570,-1000,760,-1000,-608,944,61,505,1000,11,-218,131,413,402,-323,41,1000,-1000,1000,1000,-1000,482,1000,164,1000,977,-1000,201,-393,-423,1000,-814,-566,-1000,749,614,978,-1000,228,236,12,372,1000,254,-1000,388,-1000,1000,-21,963,-1000,-49,1000,-218,1000,1000,-1000,1000,94,-664,106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{1000,-1000,524,563,1000,-722,-720,795,-516,529,-1000,-1000,-469,642,-1000,1000,782,-947,415,1000,399,133,184,-1000,-1000,424,-699,230,-503,415,-613,-831,-153,-147,-749,-752,524,0,-396,522,-778,726,1000,1000,-1000,-422,-288,-160,-613,-1000,1000,-329,-339,-1000,-655,786,-67,60,-1000,1000,333,92,-677,603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-597,-591,997,-585,470,219,519,-67,90,0,170,-340,-697,809,683,636,-301,650,-160,403,-465,178,676,-433,-349,-549,-557,-41,-84,-702,337,594,-514,200,-192,394,-378,-705,797,720,1,-921,645,-827,537,-278,-371,833,525,492,76,130,-336,756,824,-132,915,-769,448,-409,-502,-252,-894,-561}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{536,565,-1000,563,536,115,-701,366,29,326,-1000,-54,-234,-428,-714,270,1000,-358,92,-451,124,213,-340,-969,-653,-899,-591,1000,-90,-330,-593,381,-119,247,364,576,974,453,806,1000,635,-250,-1000,916,-571,1000,-288,265,-803,-941,-713,-973,694,-902,-6,124,-1000,-715,70,1000,-352,-665,158,343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-867,-420,780,-33,53,-772,720,1000,-530,45,-482,-768,-400,1000,1000,540,91,839,-245,102,-400,274,-1000,724,-123,253,746,582,-607,-870,-1000,-517,-937,721,1000,418,1000,873,337,-578,5,1000,609,-1000,976,-660,-172,300,1000,1000,-154,506,-542,1000,1000,-122,1000,-605,654,1000,-287,-73,-913,-731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{728,-577,-430,-534,-378,-1000,-442,373,-36,-64,-234,-568,931,895,-459,-390,1000,101,460,-400,1000,-27,104,-696,-839,-608,-318,246,-498,1000,-74,-160,1000,-492,-1000,-1000,694,39,-1000,451,-1000,-228,-230,1000,-693,-274,-762,-304,-613,-928,-81,-737,-520,-363,357,-28,-1000,779,-840,1000,598,-806,-1000,64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{883,1000,-173,510,331,918,-750,922,-150,41,987,-1000,-640,-124,-697,-159,14,-797,-278,-90,-1000,256,-1000,-1000,-876,-374,-126,-35,242,-229,-921,-398,-406,1000,1000,229,780,105,-54,936,457,1000,-434,1000,-1000,338,542,-595,-1000,-485,490,1000,1000,-796,-998,1000,-806,473,-4,-271,1000,559,1000,277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-658,-356,-403,-7,573,-62,-113,94,-170,135,-119,-686,-1000,-339,-154,751,98,-82,320,272,-1000,378,676,-792,-312,-940,-1000,1000,562,-984,-584,825,-1000,200,432,1000,421,-724,1000,1000,1000,-786,-359,-551,408,1000,76,799,-564,-197,-272,-635,649,-576,824,-132,-386,-1000,-113,-25,-432,-1000,91,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{1000,-1000,853,866,710,224,-863,977,-311,1000,-1000,90,1000,1000,-1000,1000,-399,-971,214,1000,344,-412,1000,-1000,561,1000,-1000,-1000,-20,308,770,-11,4,-655,-1000,-1000,-231,-1000,-82,967,-1000,1000,535,1000,-1000,-1000,-1000,-1000,-1000,-1000,1000,-827,-1000,-1000,-243,522,1000,395,-1000,362,715,435,-930,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{-1000,-166,371,-1000,-773,-647,-948,869,602,-1000,1000,-60,389,-412,-907,-1000,-715,1000,287,841,359,463,469,-184,1000,-1000,381,1000,-1,628,520,-1000,784,-400,-558,-1000,304,-980,824,-527,502,-975,-47,-1000,1000,-102,843,-692,-540,-51,-859,185,586,-776,210,-1000,304,433,-1000,-1000,-500,-26,872,908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{1000,1000,395,-1000,-66,447,-552,159,402,-889,197,-543,-467,23,217,-431,-959,871,-711,841,603,536,-1000,-82,-165,-916,1000,665,-1000,399,183,-35,401,1000,-28,642,-448,-1000,-1000,-85,569,-337,239,15,446,-477,715,-112,753,-214,-834,1000,238,1000,-504,-1000,-176,650,-594,-773,-333,653,470,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItemCount():int",
            new int[]{1000,-1000,1000,-1000,-935,274,-1000,983,235,-1000,1000,-114,79,1000,-1000,-611,-1000,392,-14,987,135,447,921,-451,491,467,546,-913,7,1000,402,-1000,1000,-392,-1000,-1000,-101,-1000,-464,-985,-624,532,1000,1000,1000,-1000,-516,-1000,-1000,-605,1000,1000,221,-1000,-1000,-1000,381,1000,-1000,-1000,711,817,-169,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{1000,1000,-1000,-447,886,179,512,-1000,1000,-1000,-858,-131,1000,782,86,858,-42,760,1000,-401,-358,1000,395,-1000,-1000,170,397,-869,-787,1000,921,-922,693,375,-1000,-1000,-1000,-95,1000,-703,-260,-613,1000,-375,1000,120,-1000,-1000,-1000,-686,407,-345,245,936,-853,1000,1000,-1000,-1000,427,-1000,-372,229,613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{784,-1000,-1000,-682,-137,797,-1000,400,23,-561,963,1000,367,-1000,1000,1000,-1000,-789,864,845,-874,-1000,-1000,-208,-239,123,-816,149,-190,-742,-670,1000,945,292,-1000,-1000,-1000,-797,764,1000,967,-1000,-1000,260,-806,-1000,-1000,-646,-1000,1000,-443,-1000,1000,142,-1000,-742,-1000,261,-813,-460,981,-543,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{586,655,-336,1000,841,580,870,400,-91,820,-171,203,37,465,-560,240,-520,-299,-261,132,369,705,621,-23,-219,1000,309,-1000,-483,-544,834,-453,-1000,-463,-959,-376,-790,407,203,-541,-838,-1000,1000,-1000,64,1000,-1000,-610,-1000,1000,-285,203,-864,-596,-376,31,368,-549,217,1000,-738,711,183,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{1000,598,-1000,1000,623,-838,-569,604,-415,-1000,-920,-481,1000,108,405,944,611,586,750,-524,676,1000,959,-1000,-887,842,644,304,-326,-1000,1000,117,-1000,-1000,-464,-1000,-1000,-290,963,-863,546,-608,1000,-325,660,1000,-1000,-536,-32,-806,120,819,-609,111,426,-680,-590,-732,-1000,15,752,1000,591,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{1000,1000,-1000,1000,886,-846,629,-1000,518,-556,-451,-379,1000,858,114,862,165,7,431,-765,-346,1000,867,-1000,-1000,328,753,-367,-1000,1000,757,-674,1000,-1000,-253,-1000,-1000,284,492,-645,-535,-30,1000,-632,672,482,-1000,-807,-488,-843,449,359,-263,411,68,1000,1000,-1000,-1000,663,-908,121,449,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{723,-1000,355,633,612,188,852,-1000,-22,-350,-849,-36,638,1000,-446,-618,-663,685,-949,-140,1000,-1000,611,-1000,-786,911,524,-642,-64,-1000,58,-1000,1000,-763,1000,176,455,668,-18,501,548,-185,1000,396,973,298,-1000,-213,1000,-934,119,627,-318,1000,497,920,1000,-1000,-1000,1000,-1000,63,1000,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{-688,-1000,347,-297,1000,-846,404,518,933,1000,701,-9,-813,858,-105,-1000,-662,7,241,1000,-146,-748,-891,1000,-981,781,226,-1000,-651,906,-36,-784,1000,1000,459,-730,1000,835,492,-63,-859,-763,-724,-272,560,-1000,1000,-681,-1000,1000,-1000,-1000,1000,626,-767,1000,1000,-546,-1000,792,920,518,-237,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{104,471,-1000,449,295,-718,-159,-1000,-197,-1000,471,190,-578,410,276,1000,-916,953,-526,-5,-616,1000,461,258,-1000,697,-95,-252,-70,-1000,267,295,961,-463,-653,-376,-323,-48,202,571,-521,-735,807,-754,-696,772,-1000,-102,488,-1000,69,456,-839,-1000,150,-414,645,195,1000,84,-611,557,-304,737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{-658,-1000,1000,288,1000,-846,1000,-1000,815,1000,675,-1000,-1000,-59,-913,-399,260,7,-990,957,657,393,324,1000,1000,328,683,-392,-578,1000,-749,-365,1000,1000,1000,573,401,1000,-1000,-645,-1000,933,-418,-1000,-1000,-230,400,-319,148,133,-479,-21,190,411,376,1000,-158,-7,1000,1000,492,1000,506,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{784,-158,-970,1000,476,-576,656,32,592,155,138,400,-213,-621,438,-38,-606,425,-265,-682,-322,-636,480,868,26,-790,-221,119,1000,988,-113,669,976,246,-707,1000,300,173,-1000,-286,-740,-136,1000,-1000,-805,-287,-357,-1000,-1000,239,220,-46,0,666,-453,1000,-148,-1000,-813,1000,959,1000,-685,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getItems():java.util.List",
            new int[]{-671,-745,-308,-211,851,289,-1000,400,-657,1000,720,-790,367,-522,-743,427,-399,833,-1000,1000,-874,829,-22,1000,-239,501,-816,-1000,-400,-494,-670,-507,-704,-373,-1000,-376,400,-797,203,-375,-1000,113,-265,-396,-806,-38,-20,241,411,-751,-1000,97,-8,142,-317,409,-784,261,1000,1000,558,1000,-123,408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{1000,1000,-1000,963,-1000,938,-346,-371,-1000,464,32,361,-1000,-553,501,-431,1000,128,-418,1000,68,-877,-1000,-1000,273,830,-717,-399,-594,824,1000,464,-1000,1000,-159,209,-41,1000,-1000,-1000,-17,663,1000,1000,-802,-1000,120,412,959,-332,272,1000,-448,-440,-1000,352,-718,195,1000,-929,-957,1000,782,-488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{-765,-208,-698,-202,1000,818,-819,-56,-876,272,1000,139,1000,361,-156,-565,665,-942,-368,499,1000,403,154,1000,1000,395,773,-382,-1000,-673,-216,-1000,-147,-1000,-145,398,-786,998,-602,867,1000,-855,1000,-542,196,1000,256,-72,-389,-187,1000,-971,728,1000,760,1000,7,-85,-1000,760,445,-749,-123,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{-955,-903,853,-1000,1000,16,-416,373,1000,190,1000,-282,1000,750,-675,147,-1000,-269,-1000,-1000,731,407,1000,-780,-92,-906,201,-713,1000,-661,-197,910,-1000,-393,-560,-271,-78,21,701,1000,-1000,-36,-350,-1000,1000,1000,-285,-1000,-1000,-950,668,-1000,-194,-616,-448,-1000,-1000,-1000,-1000,1000,1000,-948,893,-323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("java.lang.Double:MTAwLjA=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{-263,295,684,-149,-769,-658,-276,1000,893,-927,804,-938,1,13,342,-565,148,-138,-706,907,199,-258,736,336,-92,-131,-924,42,-719,-70,-377,-677,178,-943,61,77,-688,-1000,1000,1000,973,-212,-1000,-1000,196,-728,61,356,-656,1000,-179,-1000,-421,46,346,1000,1000,104,-1000,-15,-1000,756,-31,437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{-612,-828,1000,-798,545,269,-446,1000,643,-773,352,-605,1000,-893,1000,-141,-496,593,801,210,374,105,1000,1000,420,400,747,-50,-697,580,-359,-1000,1000,-1000,793,50,-1000,-484,1000,-156,1000,-236,-130,-1000,-556,446,426,160,-1000,-646,-483,-1000,-405,140,398,1000,622,360,-1000,-1000,323,153,-221,560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEwMC4w", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{-1000,1000,550,-1000,946,-312,680,-93,949,-1000,1000,-1000,274,-1000,-1000,-448,-401,-309,-1000,-1000,-1000,-1000,-463,1000,32,-600,1000,93,681,-1000,-683,-3,-358,-1000,-565,192,-1000,-855,763,1000,148,830,575,-917,325,436,-1000,-8,-1000,1000,1000,-1000,-233,817,-72,488,177,-463,-1000,694,-1000,922,8,-136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{533,861,-620,577,-683,1000,14,860,-1000,157,-1000,118,-797,1000,-621,-1000,-1000,1000,1000,1000,159,-1000,-229,-950,1000,1000,-697,764,-902,1000,43,270,-179,924,1000,-262,-333,1000,142,-1000,-384,286,158,1000,-717,-532,-974,-917,-1000,-593,-483,963,424,-988,-1000,-250,1000,242,1000,-1000,1000,1000,779,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{131,-348,839,457,-1000,832,-115,1000,-414,335,-392,-119,-1000,1000,1000,-1000,-444,1000,712,132,-602,-1000,-652,-987,46,1000,-1000,1000,-1000,573,951,703,-502,1000,1000,-355,50,-676,-1000,226,799,275,1000,-193,-180,20,-207,-1000,-1000,1000,-491,-716,-1000,-19,-788,1000,947,504,-20,-652,756,578,951,578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{-1000,-400,1000,-798,-27,-931,-622,337,1000,-523,1000,49,263,-650,1000,-815,313,-884,-456,207,-9,279,578,610,5,-427,-420,508,-776,-1000,859,-174,400,-1000,-245,-174,-1,-1000,76,1000,831,597,-1000,-1000,717,-314,1000,-453,-989,1000,548,-1000,-155,-71,673,1000,-190,-8,-1000,381,-848,88,653,-586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("java.lang.Double:NzAuNA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{-475,-852,685,246,704,489,-709,-456,1000,-104,1000,686,-100,-634,-526,-1000,-1000,109,0,162,-230,-453,-1000,810,339,800,-299,365,-719,-782,168,26,270,-1000,247,197,-1000,298,107,632,9,1000,-8,-66,862,-156,256,-1000,-709,-364,704,-785,-751,-194,-937,702,-355,346,-1000,229,-179,206,1000,693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("java.lang.Double:LTI5LjM=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{-498,909,-898,268,670,-293,-142,-1000,-150,1000,486,985,-263,325,782,-342,-963,-545,-1000,-659,225,69,-400,-391,1000,-580,-1000,-1000,1000,-1000,228,766,-400,400,-1000,-375,-694,1000,-261,409,-1000,1000,863,347,1000,702,2,-865,106,-524,1000,400,251,52,5,-1000,-343,-1000,73,1000,-327,846,858,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{-720,137,832,-142,-577,1000,-126,-288,-721,-215,1000,599,466,-203,1000,-710,516,-450,-527,558,-107,-445,-350,-780,788,-4,-1000,567,-99,-1000,1000,874,-197,200,-209,-362,137,-585,-1000,-534,309,751,-1000,-542,915,154,211,-1000,-784,881,548,-816,-1000,-785,-711,822,647,256,-1000,447,-805,208,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaxY():double",
            new int[]{-1000,861,-50,-333,152,-1000,-106,-906,-640,-1000,1000,-1000,1000,985,-1000,-653,-1000,-232,-289,-509,1000,-153,1000,-147,1000,-126,343,-480,1000,-1000,150,495,-936,517,-660,-1000,-1000,114,1000,266,-1000,915,-1000,-1000,1000,1000,-1000,780,-694,-778,52,201,-598,-439,-361,-1000,13,203,-1000,481,353,1000,471,-830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-553,-1000,1000,-1000,102,-792,622,-942,-1000,-416,921,1000,-368,-1000,-1000,1000,212,-479,-383,-1000,-136,-1000,1000,-371,1000,-159,-823,54,-1000,814,-1000,986,-1000,376,734,1000,-468,-868,1000,-864,-1000,-767,-784,-932,-144,1000,927,-1000,1000,-1000,-1000,-361,265,-1000,-620,142,8,-1000,-407,-1000,708,-1000,54,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-1000,-1000,254,306,90,-342,831,131,115,-433,517,-14,718,-993,-160,865,-212,-427,-236,-636,-328,-342,921,486,-363,-669,-899,113,136,1000,-898,244,-850,-9,-279,377,-997,-683,388,-537,-239,-270,261,-1000,-228,279,159,44,1000,-992,-227,-579,1000,-805,-793,960,-287,-857,-273,-902,561,-793,-380,806}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{76,-1000,0,837,984,-356,64,245,193,18,-962,657,-470,-990,200,816,-570,-751,-803,-567,726,-216,887,-119,-192,-304,-971,-352,622,-248,-854,606,-785,-813,324,-464,196,32,-334,39,88,-290,349,-64,931,-278,-404,40,1000,-988,104,47,1000,674,-958,563,557,-796,-48,-954,-13,155,-545,767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-341,135,-744,1000,-826,-687,172,945,539,-826,-221,-190,-887,509,-179,1000,720,-1000,1000,1000,-701,-465,-1000,489,-1000,182,415,258,-370,1000,1000,72,1000,285,497,-1000,203,-1000,-746,-1000,-1000,1000,524,-372,561,-320,-443,-136,-1000,-697,-1000,-267,612,-371,-856,1000,-1000,-48,274,-860,1000,-530,1000,433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{1000,118,-161,-28,1000,554,555,-281,583,89,-534,261,379,-420,-400,1000,1000,-299,-766,-1000,1000,-1000,149,-923,838,125,751,-249,-849,361,148,1000,65,-976,-1000,915,772,-180,-242,-792,1000,-417,-532,696,1000,831,-850,-1000,-550,-1000,-400,-165,-1000,1000,-359,-352,1000,413,623,-670,-1000,596,-169,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-1000,-1000,-1000,302,-1000,399,338,450,1000,-999,-560,366,-368,1000,1000,-506,1000,-1000,887,-1000,-1000,-482,1000,-33,136,-816,-560,-229,1000,1000,1000,-941,1000,1000,734,268,-1000,-490,-1000,-282,1000,1000,-1000,-1000,-1000,-1000,927,-80,-996,894,747,-198,1000,-1000,-411,-566,8,403,-204,833,639,1000,-81,-705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{1000,669,-30,-221,1000,-679,429,944,794,-192,-885,-1000,950,-1000,30,-139,-1000,-442,-705,-876,1000,734,1000,692,-776,-808,645,-1000,97,-377,-1000,-1000,276,-252,-803,-207,753,-482,575,1000,-447,-1000,-794,518,-290,497,650,869,550,-1000,-58,-146,533,1000,115,-732,557,-1000,-811,-261,460,-555,-1000,118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{353,-8,1000,-225,-297,-552,-269,467,484,-602,-556,-526,273,-993,-160,854,-998,-551,-832,-512,808,609,921,525,1000,-552,157,-683,136,-525,-898,-793,-850,-227,-540,780,297,-268,-581,706,-239,-503,-312,-1000,1000,18,-10,799,-37,-992,-227,-487,362,-1000,-146,-180,561,-857,114,-619,391,674,-991,-352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{814,358,813,113,358,401,803,-806,636,94,-257,-646,84,-172,-180,-758,223,198,-318,-449,979,-723,115,324,-20,190,963,890,548,642,-694,731,-407,-123,-640,759,-483,-495,557,-379,-180,604,-177,1000,164,545,-787,-872,-681,-883,732,-809,-348,689,457,700,568,-601,-167,-530,691,-376,-539,-597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{909,1000,920,97,-1000,-276,1000,-54,615,-280,-230,-291,496,-1000,-651,-363,-582,-14,-574,464,432,-134,1000,-73,-378,-314,802,101,-176,368,-1000,-152,-204,-206,-486,634,-92,-1000,1000,296,-139,-124,-425,565,-68,261,1000,-196,-221,-1000,511,-361,75,671,365,379,734,-1000,-189,-640,1000,846,-283,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{-892,-610,-38,-674,-317,513,-131,-762,-192,141,-435,323,558,-453,-657,404,692,81,-87,-806,95,-845,683,220,398,948,735,744,-503,894,-544,437,-946,886,212,696,-931,-932,458,-309,-868,48,-742,-482,46,433,298,-869,207,691,-838,161,300,-484,-89,138,-304,-875,-189,-799,250,-824,417,-98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemAge():long",
            new int[]{1000,392,-463,-875,768,93,-726,1000,594,149,-885,-44,831,-234,385,-999,-410,204,-964,-820,1000,977,1000,644,177,239,417,-367,1000,-796,-1000,-1000,-354,148,-331,-105,679,-34,302,829,180,-1000,-625,60,-685,1000,378,180,710,211,1000,202,592,1000,212,-901,1000,-1000,-515,270,-95,-274,-852,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{-1000,-93,724,-689,1000,875,-684,-245,-513,-820,-338,-111,-544,1000,-196,88,788,-307,-901,-969,567,-1000,538,-1000,-49,340,-1000,-534,-671,228,-1000,-311,-849,-58,-702,-115,-499,-382,-473,-136,-890,-613,297,-1000,-226,193,1000,402,-757,18,-1000,-116,-563,537,-791,-995,189,-1000,-183,-1000,-613,-996,924,881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{-597,260,721,-901,1000,682,-555,-602,324,-15,-138,-480,-347,814,1000,1000,1000,185,961,-120,149,-890,841,-405,715,1000,361,-1000,-960,-116,-847,-170,-977,-135,-357,179,-731,-272,-1000,-993,546,-493,-420,-521,-1000,1000,780,-392,45,1000,-1000,-779,-697,907,-1000,-1000,899,-847,-748,-600,-276,-1000,1000,574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{315,243,125,-296,-389,-307,1000,1000,224,680,625,-683,-119,-856,-919,607,-1000,-606,724,195,-1000,552,-526,626,877,900,68,1000,-1000,-903,-157,259,1000,580,-365,223,-453,368,1000,-1000,1000,332,-271,-748,-736,-627,573,776,744,-657,486,800,1000,1000,807,-669,-934,-1000,-790,-464,357,133,471,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{550,620,246,393,948,198,918,-433,-396,1000,-40,-1000,-277,480,-1000,365,430,867,-1000,720,38,738,-150,-284,-928,-1000,940,397,1000,-444,1000,775,-328,765,315,-553,-309,140,1000,-997,719,-401,1000,-332,1000,-1000,884,926,-7,-864,1000,-553,938,-1000,386,1000,1000,-140,282,1000,-623,-399,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{-424,-97,694,215,-310,86,-121,133,539,-666,831,-154,-586,-81,734,989,-1000,183,768,-169,163,-167,819,-828,802,1000,215,108,-548,110,-590,-559,-554,-205,-309,1000,-412,109,283,165,1000,1000,-1000,-461,-849,1000,1000,-123,-751,538,-1000,66,397,996,265,-231,-117,126,473,-74,550,-124,621,517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{-637,-124,-603,93,115,-741,-177,594,-604,-804,273,25,-325,-26,-727,-898,-581,-184,-845,14,382,593,-501,-773,-235,-275,-980,-490,931,707,-436,-957,265,39,-138,95,234,803,166,494,-252,571,-37,-164,503,-87,-406,-733,158,-644,779,501,-81,-648,825,740,-634,-494,595,-834,-767,165,-819,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{151,270,-506,-150,-937,-1000,814,749,385,16,633,-485,-301,-888,179,252,-982,-27,478,930,-508,714,-697,-844,851,622,646,139,-120,117,-287,-745,653,-462,290,696,22,1000,459,1000,1000,745,-1000,-232,-570,1000,780,-662,280,73,832,342,428,630,807,184,-363,549,614,597,-406,252,-604,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{-1000,-785,741,68,265,391,-800,322,-236,-1000,271,673,324,556,-320,-250,-251,-108,-333,389,520,345,-675,-1000,-450,370,-1000,-584,168,545,52,-633,-758,202,-686,1000,-497,-175,114,806,-1000,-344,-343,-229,-74,554,704,498,-1000,-623,-96,285,757,-1000,1000,1000,-983,-503,923,-1000,-1000,891,527,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{-106,-248,259,473,1000,359,1000,714,-450,871,345,-1,-104,843,-1000,1000,-581,-1000,1000,-1000,-1000,-1000,403,1000,1000,994,93,728,-1000,-751,-1000,-7,953,-1000,-382,95,234,156,1000,494,693,-373,168,-1000,-1000,-381,1000,462,-623,-922,402,1000,-259,1000,905,-1000,461,-1000,711,-834,534,-1000,399,254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{652,-7,-405,-835,650,-16,79,201,-79,1000,358,20,918,907,259,-35,123,1000,-616,-717,-187,1000,-341,540,-640,-67,676,-1000,-1000,484,493,-314,-177,-469,118,-1000,-1000,-63,-1000,-1000,1000,921,404,793,-919,-602,-1000,-289,1000,146,481,-1000,-979,1000,-196,-1000,45,-1000,-1000,-66,371,-257,283,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{-851,358,76,-537,1000,-358,146,234,-6,679,539,-1000,-355,905,330,-255,931,-727,646,-440,-811,-1000,719,-740,1000,212,34,803,147,-589,-1000,-548,423,-939,-432,-277,-731,458,400,28,546,-28,7,-977,204,-40,636,-1000,975,-142,-768,442,192,777,-1000,-1000,483,390,634,235,8,-1000,-400,977}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMaximumItemCount():int",
            new int[]{-603,-289,602,-408,561,422,-460,98,-122,-860,169,133,220,1000,-11,654,154,104,-439,-1000,114,50,-753,-585,-135,368,-103,-643,-735,186,-132,-311,-622,600,-465,179,-519,104,-516,-1000,482,60,-155,-256,-546,362,369,112,45,-144,-1000,-536,-237,1000,-416,-905,-158,-847,-601,-767,-588,-332,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMinY():double",
            new int[]{839,1000,1000,1000,601,454,-52,1000,-1000,971,671,-192,884,-1000,266,-652,-664,-1000,1000,-1000,-1000,1000,1000,-986,1000,1000,1000,82,-1000,-1000,614,397,-1000,-553,1000,-1000,369,-730,-1000,-1000,-1000,-676,1000,-80,-847,165,1000,-208,-1000,416,580,719,654,-53,166,-842,-237,1000,132,1000,-903,-1000,1000,796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("java.lang.Double:LTYzLjA=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMinY():double",
            new int[]{-1000,-1000,-10,-388,-630,-306,-1000,-953,-1000,-1000,190,597,-1000,693,-274,149,1000,661,596,-1000,95,-60,-1000,1000,-1000,-305,74,-1000,1000,1000,-1000,-1000,-619,63,-893,1000,232,-33,705,-1000,-879,294,-1000,-207,748,1000,794,1000,876,-802,172,-1000,-1000,1000,-569,783,975,549,-1000,-1000,616,1000,-613,-859}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMinY():double",
            new int[]{-1000,-916,-581,-470,-211,529,-785,-716,-1000,148,-802,-1000,-574,610,-930,-470,1000,-1000,556,94,-1000,-578,-1000,-315,-688,-1000,-106,-409,-867,71,258,-1000,-1000,-1000,324,236,-271,721,600,660,-407,782,-642,599,-776,656,-1000,-833,608,1000,883,-1000,-907,1000,-747,843,595,-1000,-1000,119,1000,1000,-930,389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMinY():double",
            new int[]{186,-1000,-800,-555,1000,644,-58,-721,-545,-686,408,-893,302,-82,-53,-294,909,-548,648,83,-812,-330,-523,324,-292,330,391,-854,842,77,-901,-653,-903,143,-218,-164,-169,256,203,-1000,-534,951,137,-795,-776,-240,-165,478,851,-446,590,-1000,-838,1000,-1000,1000,696,-614,-1000,211,-139,424,-145,-946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("java.lang.Double:LTc3Ljc=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMinY():double",
            new int[]{-675,-1000,-1000,-399,-777,529,-432,-918,-1000,198,-802,-1000,-372,709,-513,-264,1000,-1000,527,656,-944,-24,-1000,-437,-464,-939,-156,-557,310,366,540,-1000,-1000,-565,-440,985,-568,721,767,1000,-407,913,-312,474,-521,1000,-654,-800,450,789,1000,-864,-1000,767,-944,400,636,495,-1000,-115,1000,1000,-610,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("java.lang.Double:LTc0LjY=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMinY():double",
            new int[]{-470,364,-443,-746,577,-51,-416,-755,535,616,500,584,-389,-516,547,536,550,476,151,-1000,42,870,-681,-907,-467,591,-401,-419,553,418,-127,515,-878,386,547,119,-208,-532,1000,-1000,-374,167,-763,-655,82,392,145,1000,44,-462,714,556,-794,689,-1000,416,-491,734,-1000,480,-146,-897,117,698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEwMC4w", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMinY():double",
            new int[]{-1000,347,944,-7,-807,388,-1000,-96,554,-1000,-87,991,-626,479,-224,-757,947,138,392,-759,-72,-55,-679,960,-1000,105,981,-993,-310,615,-291,-640,65,-424,-566,1000,368,-845,168,-718,-1000,398,-664,-628,83,-51,1000,148,1000,-774,-220,-82,373,8,-20,222,1000,919,-543,-259,-251,518,-377,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMinY():double",
            new int[]{535,347,1000,-122,149,1000,-296,1000,554,98,1000,-214,372,-780,-281,-706,-616,-619,506,-806,-338,208,811,189,-144,751,1000,219,-1000,-1000,-291,322,350,-440,-426,-617,629,-1000,-423,-293,-1000,813,244,-813,-426,-197,1000,441,-391,-698,-495,246,373,369,-20,191,-1000,1000,-84,364,-890,-1000,865,-576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMinY():double",
            new int[]{-1000,618,971,-176,-560,-306,-783,754,-797,-933,-672,-1000,-1000,515,-1000,-332,1000,-1000,558,-1000,-667,-60,123,-183,-503,-535,978,-234,-388,-1000,-1000,-446,-635,-715,-109,1000,281,-28,-756,-1000,-879,521,-18,-40,-551,619,794,-464,685,290,-308,-1000,255,1000,-11,1000,1000,549,-526,-413,616,448,-63,-859}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getMinY():double",
            new int[]{-1000,1000,1000,1000,-251,-223,-200,1000,-228,342,-219,-303,-699,-222,-299,-650,553,-1000,699,-804,-758,949,1000,-46,-503,-804,800,218,-533,-1000,-221,300,-220,-1000,-109,538,-514,133,-1000,-699,-779,-543,227,104,-706,-95,818,-488,753,-230,-400,-828,1000,1000,410,1000,1000,465,874,-719,1000,448,-274,340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-362,1000,-556,-1000,-724,134,1000,47,143,-684,-1000,1000,-531,-1000,5,-1000,-738,-147,1000,-57,1000,-989,-632,438,-1000,1000,-614,757,-437,-248,-1000,-1000,1000,-781,657,-1000,685,847,-825,1000,-436,-495,-89,1000,-1000,-432,-1000,-1000,-249,-295,964,844,232,0,-518,479,-895,1000,941,-445,-630,623,431,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{244,136,99,-1000,782,-167,-1000,-98,-782,20,677,-356,-432,-366,-1000,-47,435,258,487,-788,-1000,65,408,-1000,20,294,-344,-568,-1000,-533,846,-1000,1000,105,808,1000,34,65,-1000,752,-336,1000,20,341,-547,-24,-369,-259,-1000,316,-748,-220,916,-666,413,491,-76,-866,-282,359,347,163,946,-581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{982,-57,-21,-226,-757,353,237,1000,204,482,139,18,-190,-713,-395,-13,58,-114,1000,-625,-1000,-167,-39,-228,-632,730,230,-319,-686,-92,871,-468,727,-711,-674,567,73,945,-327,-205,-180,234,-1000,600,110,1000,-1000,272,703,672,-760,50,1000,-870,269,995,-854,750,-427,-81,-202,244,1000,-219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-477,458,-779,-702,-1000,119,1000,723,17,-418,-866,549,-929,-732,1000,-620,-1000,372,1000,757,1000,-799,-1000,1000,-1000,1000,327,1000,677,1000,-1000,-277,168,-770,792,-1000,-940,926,-230,730,79,-1000,-803,1000,-1000,224,-951,-259,949,-61,954,642,95,861,-531,-203,-142,1000,1000,-553,-920,112,-355,-353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-910,712,-768,363,-724,-541,852,-666,727,-846,-539,-193,600,-53,630,152,-627,716,255,945,885,-405,885,298,-8,669,-520,878,883,520,463,-151,-200,77,915,308,-391,557,-238,568,493,-894,-107,104,887,250,-714,-86,-296,-766,411,587,875,887,487,-512,309,806,443,-912,-565,306,-123,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00334() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-1000,706,-1000,-833,191,1000,1000,293,-676,-1000,-1000,-733,-347,922,749,-1000,-1000,122,1000,678,1000,-1000,-950,-58,-1000,-217,-782,506,552,1000,-371,-974,1000,142,927,-1000,-428,1000,-172,1000,-1000,-1000,-410,40,-906,-550,-673,-1000,245,-326,1000,606,1000,-343,-443,-512,-143,1000,523,-711,-1000,1000,-693,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00335() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{71,191,-832,-308,-164,-298,835,513,188,31,1000,-772,87,997,699,-179,-335,-121,546,509,-357,-183,-270,-496,534,-529,-253,196,427,569,1000,-30,294,-418,152,-128,-843,436,532,-193,131,-909,-276,-762,936,1000,-293,-102,-5,-133,887,268,281,-322,-452,-423,368,-389,-790,-692,-531,186,383,893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00336() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{437,389,-1000,235,-654,901,-26,1000,-1000,166,-677,-588,-778,-405,958,649,-924,-719,690,824,695,-1000,-646,308,-341,885,1000,829,1000,570,-61,692,86,1000,-787,-1000,-510,1000,-107,-976,-1000,-791,-1000,-22,-302,17,-1000,-1000,436,-297,1000,-31,490,-1000,512,319,61,874,642,-609,-1000,-562,-924,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00337() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{894,918,-451,548,965,-35,-457,614,996,-531,-924,824,-682,-635,-597,-637,-712,-377,483,11,-69,-615,-54,-233,-174,-283,-760,741,-458,-100,-916,-211,602,984,-679,708,687,-389,-723,-393,-407,-4,-205,499,312,413,-705,-864,536,504,-627,626,-135,-677,745,475,-944,-871,-327,-646,-255,659,381,-883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00338() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-1000,1000,-719,910,-408,29,1000,302,1000,-1000,-886,212,1000,1000,1000,-1000,-1000,1000,-233,1000,435,-747,668,558,-335,819,608,714,-887,1000,-272,-215,-372,-311,643,-180,-326,541,546,402,301,-1000,22,-378,1000,340,-1000,-982,1000,-1000,815,1000,904,371,105,-1000,258,1000,764,-1000,-1000,1000,-601,512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00339() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{889,824,-560,1000,311,-704,578,420,1000,945,-953,1000,-411,-654,1000,875,117,-398,-332,609,1000,-1000,468,1000,474,1000,904,1000,1000,-543,-471,856,-1000,-725,-532,279,452,1000,-1000,-1000,-399,-1000,-1000,1000,99,854,-158,-340,1000,-671,-10,769,-438,85,1000,917,-316,1000,52,-585,70,-377,-235,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00340() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getNextTimePeriod():org.jfree.data.time.RegularTimePeriod",
            new int[]{-68,156,-172,-1000,77,434,237,153,-117,-409,-398,195,-550,-767,-270,-81,-76,77,1000,-850,-633,-167,-806,-1000,-632,739,-434,-408,-1000,-92,-54,-1000,1000,-434,-407,365,431,945,-324,635,-180,-48,-980,753,-783,725,-906,-8,587,621,-727,65,1000,-525,-318,779,-127,488,-418,5,-202,629,1000,-780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00341() {
        org.junit.Assert.assertEquals("java.lang.String:IDkwMiA=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-653,1000,221,128,1000,-222,-368,-1000,901,955,314,-245,-22,902,1000,-225,209,-1000,977,-863,-841,405,-1000,-1000,-1000,-800,250,679,608,1000,915,-90,-413,61,883,450,1000,-1000,631,-430,1000,1000,1000,1000,-966,988,-380,-445,-1000,564,-1000,-862,91,349,344,-1000,-1000,637,-83,254,246,126,-868,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00342() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{356,1000,156,-389,1000,594,-1000,-1000,952,-71,689,-677,-678,1000,314,-1000,214,-1000,127,-1000,-196,-357,-1000,-1000,-677,-114,914,1000,1000,1000,-1000,-504,1000,-1000,21,-327,758,-1000,742,592,1000,400,48,1000,-572,1000,340,-174,493,1000,1000,915,1000,1000,593,-1000,363,-544,1000,167,166,389,-1000,495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00343() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-269,898,471,-47,102,-693,1000,-881,1000,1,482,-899,-652,44,452,-222,149,-189,426,-1000,-429,135,-1000,442,-549,-844,492,1000,-395,1000,-748,-1000,-427,-326,273,90,-420,-660,219,359,-257,-308,1000,784,-190,428,-245,-184,-269,-877,-851,-785,39,321,-686,-630,-122,1000,-194,772,-783,-170,-574,-535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00344() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-665,-311,257,-711,1000,-1000,648,-860,1000,937,1000,211,-457,311,1000,-491,-741,625,1000,363,-918,788,-891,415,-1000,-1000,575,-417,876,449,-1000,-805,-1000,1000,1000,540,1000,-1000,133,592,1000,398,1000,1000,-58,-898,-1000,-1000,493,-757,1000,-39,-1000,331,-549,-176,-1000,698,-1000,692,-361,261,-534,962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00345() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{698,103,-208,235,394,-222,-749,400,-137,717,448,-330,878,245,-336,12,352,-1000,-913,218,179,141,453,921,-1000,-76,-406,1000,1000,-1000,-227,-90,400,84,227,-916,-716,484,-17,-714,-106,826,-400,67,520,-315,-483,547,373,564,1000,320,714,734,1,368,1000,300,385,367,1000,19,-212,469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00346() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-653,-277,-102,-315,-655,-801,677,-1000,709,-134,713,-575,-482,192,-725,296,-628,995,-82,-478,834,71,-502,-52,681,-1000,-997,-326,-388,-586,274,-799,-1000,1000,722,-8,-1000,215,1000,759,968,-1000,1000,1000,-1000,21,-656,414,633,-473,-199,-885,-890,-222,926,890,-1000,1000,-545,-96,-1000,1000,-45,-148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00347() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-1000,-786,-1000,-511,-655,970,677,649,-841,1000,713,-36,-482,-1000,848,-1000,-467,966,798,-478,-1000,71,461,-1000,-29,378,413,1000,1000,-183,274,929,-915,-345,746,26,592,947,-86,-1000,-1000,183,-497,-1000,1000,757,315,-283,-589,474,1000,-1000,1000,-587,-1000,-604,1000,988,1000,-96,1000,-927,-45,-567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00348() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-653,-15,-65,-244,-345,-1000,169,872,1000,320,661,-543,-42,691,-725,-824,136,772,-82,-459,813,-57,-625,-329,-22,-21,-997,-865,-859,-577,1000,-1000,-993,627,811,206,-1000,-57,1000,290,1000,-527,1000,-1000,-1000,1000,-745,293,255,-220,-557,-883,-827,-97,926,854,-1000,822,-610,-462,-1000,1000,-578,51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00349() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-511,465,145,-16,366,11,668,-301,1000,1,421,-906,-815,-116,1000,148,-100,-931,977,-903,-797,666,-749,-435,-603,-844,490,1000,225,982,109,387,-427,-518,379,14,1000,-445,219,528,-93,34,1000,420,-85,-29,-200,-198,-229,48,-268,-865,-37,95,-634,-926,-261,1000,50,1000,489,17,220,-650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00350() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-653,-457,313,399,-718,1000,1000,686,-507,-1000,-127,-1000,-1000,-1000,995,1000,-1000,743,335,-863,-1000,405,1000,731,-122,-1000,43,-1000,276,388,-1000,-41,-1000,-1000,1000,-832,1000,446,453,1000,-1000,-1000,1000,-1000,970,371,-728,1000,486,-1000,1000,-862,-1000,-1000,371,-128,-1000,1000,360,254,-216,-416,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00351() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{-712,1000,-242,-520,-335,118,177,-215,413,-49,799,-598,-1000,76,-808,-930,-321,-490,-583,-1000,1000,-250,-205,-762,340,-52,-204,971,-1000,-270,1000,-795,301,-591,-44,506,-1000,455,99,231,423,208,-20,1000,-69,1000,291,977,-534,27,-916,166,568,145,-561,349,-117,13,418,-760,-545,815,-606,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00352() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWU=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getRangeDescription():java.lang.String",
            new int[]{1000,439,39,-646,1000,-327,169,162,609,544,1000,-125,1000,1000,-362,-386,229,-835,464,-871,813,-1000,-1000,280,-20,-45,-997,1000,1000,-1000,-376,-204,712,1000,776,-342,-1000,114,-452,290,1000,503,-624,1000,-889,-400,-745,704,255,193,-557,872,465,1000,-709,1000,-1000,328,-281,-235,-247,601,-982,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00353() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-174,-13,1000,179,-691,-1000,3,728,-318,138,-1000,-880,-1000,-929,-1000,-1000,1000,445,-164,182,1000,-474,-14,820,-335,-1000,-384,796,-620,-4,-246,-763,313,-310,-515,1000,140,375,551,-273,-1000,-657,-635,-690,1000,558,1000,296,-741,1000,-725,1000,-829,-655,-70,684,-659,-1000,-511,175,-213,-976,538,126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00354() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{252,865,-1000,-360,-784,1000,-162,875,535,-869,575,-777,883,314,392,600,-1000,-234,-450,-761,-1000,1000,589,847,-249,116,-628,845,-422,-88,-1000,-94,285,780,760,170,330,738,245,777,104,994,38,905,-262,570,-159,752,-722,-1000,459,170,1000,-222,-360,336,-653,613,587,1000,640,213,-301,-26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00355() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{1000,1000,-1000,-806,-805,614,-1000,66,210,1,448,-1000,565,1000,1000,1000,-354,49,-486,1000,-1000,1000,1000,1000,-593,-839,89,1000,-1000,-299,-1000,1000,9,450,-1000,1000,208,1000,1000,1000,-1000,1000,1000,175,-345,628,-37,910,-1000,-521,-118,796,976,-61,-32,941,-1000,-557,724,1000,1000,1000,-373,448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00356() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-995,858,-970,888,-30,258,89,-76,904,356,147,-9,72,675,324,125,-83,-45,749,-836,797,119,789,-776,-230,-127,540,-547,304,674,737,-816,791,-865,431,-707,636,523,-206,633,323,-33,-770,701,379,-785,-379,409,-600,-653,-42,-267,-542,-272,-109,330,69,629,-212,773,-311,-494,-205,-9}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00357() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-665,76,1000,-564,159,-662,3,339,-830,-828,-1000,79,-917,-984,-1000,-1000,1000,291,-787,1000,18,-594,-712,817,560,-1000,-338,356,-620,-540,677,-709,585,-1000,-733,371,849,745,519,-1000,-1000,-1000,-967,-855,679,782,-210,45,-172,1000,-1000,1000,-1000,-1000,-440,352,-3,-1000,-311,215,-899,-1000,520,177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00358() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{472,698,-1000,-395,65,350,-83,683,-73,-800,900,-243,786,-458,-743,1000,-655,-35,173,-352,-900,-363,-26,43,-87,310,-277,-133,627,733,-30,-819,558,1000,-435,-499,719,277,-140,-37,1000,-235,521,371,48,-129,-1000,-379,-675,-1000,1000,-297,560,1000,-677,234,-590,38,55,768,517,-57,426,642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00359() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{1000,914,-1000,-1000,235,-579,-1000,-196,-531,-95,369,-628,-18,763,943,896,1000,272,-1000,1000,689,-400,590,995,-41,-1000,750,661,-1000,-523,400,1000,327,-813,-1000,1000,939,1000,1000,-40,-1000,26,957,-1000,-18,910,-1000,172,-644,592,-1000,1000,47,-447,-385,924,-892,-1000,736,1000,-40,131,-311,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00360() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-565,307,234,161,-260,-52,666,626,-421,-871,-650,120,-1000,-714,-583,-911,-866,-22,-955,-529,170,-1000,-625,387,310,328,316,-545,732,-651,252,-757,845,715,1000,-560,165,0,-758,-538,748,-956,-742,-932,-408,167,-224,-698,759,-583,-342,-252,-701,-525,-334,-8,340,961,-221,-103,-1000,-996,640,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00361() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-259,1000,661,346,583,-866,-386,1000,-467,26,1000,-1000,1000,1000,538,1000,-1000,-949,-1000,422,-370,1000,400,-81,511,-332,-1000,-360,-1000,195,-1000,1000,-1000,910,-1000,-159,840,-1000,-261,1000,-193,1000,590,653,-579,276,1000,-187,313,605,681,-229,424,-801,1000,1000,711,711,-116,735,826,198,52,-57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00362() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-1000,683,-1000,-719,-339,-161,-1000,-281,-731,648,-720,-482,609,-756,1000,-733,-60,-93,862,75,-1000,-665,1000,1000,-1000,-948,-514,944,60,-632,-1000,47,421,1000,-1000,223,1000,98,-436,109,-54,-33,-939,-140,856,-249,-179,905,-1000,516,-365,952,751,-448,-392,748,-1000,-1000,409,-602,1000,54,187,-104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00363() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-738,829,-570,-830,721,206,197,-798,-583,-204,28,619,-166,22,-398,167,476,-24,-58,-741,-207,752,858,883,186,274,-879,-392,-174,-752,-606,666,67,-835,-857,-198,-920,-105,773,-378,-4,365,24,504,341,-973,-647,773,824,-590,406,-170,-361,-104,-994,-830,-622,56,-887,-157,-646,360,-876,781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00364() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Day", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{361,656,622,98,-1000,-951,-1000,289,-364,510,434,-1000,-457,881,538,-433,-345,228,-1000,129,-467,712,661,948,-593,-768,-214,624,-1000,-43,-1000,1000,-213,910,-944,711,-842,231,153,1000,-895,1000,1000,-206,-522,319,1000,-102,-300,974,-118,550,9,113,704,1000,-73,29,-105,-760,918,677,-373,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00365() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriod(int):org.jfree.data.time.RegularTimePeriod",
            new int[]{-581,-155,804,698,592,-1000,-769,944,-699,-617,-1000,604,-1000,-1000,-1000,-663,1000,877,-221,-1000,-208,-1000,234,1000,772,-1000,1000,571,356,-213,116,-1000,545,-149,938,-619,1000,831,428,-1000,52,-1000,-1000,600,558,637,-730,38,-868,-590,-1000,1000,-1000,-1000,-1000,-369,-1000,-1000,-815,-867,-527,-1000,940,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00366() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{1000,-1000,1000,-516,1000,963,-170,405,1000,422,-1000,-278,1000,736,-762,-1000,820,-39,40,1000,935,524,-294,116,-384,1000,-1000,-1000,-1000,1000,-751,716,371,1000,-1000,-80,-153,-285,-1000,1000,-650,898,-97,-952,-223,-161,943,-87,-798,-311,-790,484,-232,-524,-191,-1000,-493,-431,-621,1000,41,-420,4,49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00367() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-313,-561,-917,176,553,-220,309,677,-431,-489,-238,-329,-739,-29,662,1000,-648,-400,-485,-1000,-746,-957,190,-414,-828,-1000,1000,621,1000,-85,364,442,590,-238,977,574,-597,-16,387,-887,-578,-233,159,1000,1000,-773,332,-291,654,58,625,-671,-606,196,626,337,-446,650,-803,-1000,-1000,-399,249,-522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00368() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-400,839,-465,476,55,-202,347,-53,-641,683,575,-1000,322,-553,-428,-1000,852,411,-50,-1000,-837,-380,424,1000,-959,-1000,940,-525,-1000,-1000,-1000,-350,-392,-1000,673,-422,-769,-47,-220,-532,330,557,1000,-264,695,-409,-89,-240,1000,-201,284,77,-616,-417,521,-1000,730,-11,1000,-488,613,39,-108,-652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00369() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{370,-1000,-97,43,1000,108,417,459,595,-385,1000,-705,-62,-390,842,242,623,-660,544,64,815,534,-831,-529,737,1000,214,1000,-971,630,-45,554,84,-575,-424,223,-845,-29,268,724,-354,-470,-116,342,445,-146,674,-59,-1000,-569,53,105,1000,194,484,-324,-350,26,-1000,800,-1000,-450,-788,-122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00370() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-274,-60,-729,646,421,-172,-333,44,-411,24,241,-108,-275,-385,-236,865,-821,162,208,-1000,-1000,-521,303,775,-878,443,1000,-576,-223,-773,822,302,-166,-914,1000,1000,-1000,750,597,-822,-328,-179,1000,251,476,-759,61,-216,1000,-189,1000,-760,298,-151,1000,956,1000,805,480,-429,-145,-575,-178,-898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00371() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-400,50,-249,-140,55,193,106,9,190,-627,987,-515,225,-555,-1000,-734,-1000,-847,148,543,249,915,1000,-499,-93,1000,-592,442,-1000,220,-610,284,-292,-25,-2,325,-673,73,-1000,-308,-137,-1000,44,-720,-383,-641,-189,271,-150,265,-197,183,-104,-829,-38,-238,116,1000,308,-165,-838,465,-115,102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00372() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{47,-313,-563,16,1000,199,-123,-99,462,-1000,253,-755,-244,570,309,649,236,-412,920,820,-1000,690,261,-318,-1000,508,972,-1000,-926,-1000,62,1000,-166,-129,207,1000,-803,805,717,479,-1000,484,917,-647,-378,-688,671,-60,-641,-827,1000,-663,598,201,-817,-312,1000,-77,8,-338,-659,-952,-47,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00373() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-548,861,-284,-516,-555,-156,-82,38,-602,1000,-259,-698,670,-587,-1000,-1000,-546,249,-1000,-625,-654,-127,494,1000,330,-1000,810,1000,-947,520,-3,-507,749,-1000,701,-791,-261,150,-1000,-1000,-320,-828,-60,-1000,1000,-724,-360,483,1000,845,123,483,-330,-1000,-65,1000,-1000,-45,636,-781,749,643,37,-200}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00374() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{1000,-1000,1000,-1000,1000,963,-1000,195,1000,-1000,258,30,-949,273,429,-374,116,-862,880,64,-686,1000,-1000,-1000,737,1000,-1000,589,400,957,-1000,148,134,1000,-512,1000,-815,1000,-367,1000,-1000,313,382,359,-1000,-183,998,-193,-1000,526,948,-1000,1000,1000,939,-1000,-602,-524,-1000,156,-1000,-1000,-788,314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00375() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{820,-885,1000,614,419,639,-794,-362,753,-392,-607,67,889,474,-1000,-1000,-71,-281,-611,176,-192,409,732,-1000,-1000,1000,-1000,-568,-441,747,-1000,841,560,1000,-741,233,-45,863,-1000,187,-755,-750,154,-1000,-1000,-940,394,444,-546,1000,-799,37,1000,-348,-423,-1000,-1000,436,-590,115,-420,-171,-1000,40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00376() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{-307,786,-651,1000,252,722,-27,-207,1000,-995,416,108,-498,-366,-737,-145,-588,325,1000,-427,-965,505,-274,-135,-1000,1000,-333,-951,-1000,-476,652,405,-324,1000,-1000,1000,-726,43,1000,-28,-288,1000,1000,1000,424,-423,1000,-1000,629,-365,977,609,744,-723,1000,29,1000,-896,266,419,-839,-1000,-325,90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00377() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodClass():java.lang.Class",
            new int[]{1000,-1000,849,-1000,1000,558,-1000,317,465,-429,-838,602,-401,687,487,78,-1000,-923,418,-269,-852,575,-967,-652,813,212,-446,556,400,992,-1000,187,575,678,-299,980,-1000,1000,-161,1000,-1000,466,168,331,14,239,1000,305,-1000,828,1000,-1000,1000,1000,969,-921,-786,193,-1000,-70,-638,-1000,-556,-200}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00378() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{263,-644,1000,715,841,173,534,595,1000,-1000,602,-1000,741,1000,311,-400,359,475,-1000,14,-324,26,761,1000,401,-1000,-346,919,-7,591,-17,-352,225,286,1000,1000,-895,87,616,532,-1000,1000,229,394,446,-610,-472,-1000,-123,64,85,129,176,299,-322,-116,-847,-990,-859,126,798,298,40,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00379() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{639,575,360,860,675,301,772,-991,69,-1000,805,-440,-1000,581,498,-15,-246,54,-263,-639,351,81,883,682,706,-767,743,266,252,216,502,-455,-172,474,737,795,-949,-127,-153,438,-945,-230,622,703,602,-570,279,-1000,187,-756,-426,573,-898,-252,336,316,-247,-995,-931,-448,901,-364,-210,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00380() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-1000,119,-1000,-399,-412,1000,534,1000,269,-90,197,720,-304,411,5,-518,-28,-215,-277,-850,-132,570,-120,-1000,609,576,890,-73,-110,189,-298,1000,-390,286,-1000,-1000,896,319,-1000,1000,1000,255,-7,1000,446,163,-301,-306,-972,1000,-158,-280,176,-579,-322,-992,93,-700,-1,439,109,-932,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00381() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{1000,100,1000,-8,2,389,-159,-541,-245,491,-857,767,698,-472,1000,-168,768,-650,-341,552,-1000,-1000,1000,-367,158,1000,-1000,96,-498,-506,664,-210,783,836,-103,-258,-1000,-931,-543,-116,-851,974,-607,-1000,25,-1000,-1000,-615,449,-470,1000,796,42,-737,-647,-311,957,-338,1000,-187,-686,-634,658,-328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00382() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{668,975,713,18,-622,1000,983,-1000,473,368,-1000,-977,-922,7,791,906,1000,-1000,-130,66,295,-388,52,80,-444,-936,-1000,-1000,852,-1000,-810,-1000,1000,1000,1000,-1000,739,462,490,-113,259,1000,410,-1000,-464,1000,-809,1000,644,-1000,1000,-474,-1000,24,313,316,756,1000,1000,-162,-1000,998,-1000,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00383() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-154,759,-1000,-203,-1000,1000,-834,-498,657,-786,-356,1000,856,484,175,158,1000,-1000,-599,-708,-570,-188,731,-1000,386,1000,818,-97,-1000,48,257,1000,468,599,-821,-1000,760,570,-509,946,941,1000,173,291,-1000,-943,-1000,-557,-698,1000,-159,-259,192,-1000,322,-1000,888,-37,1000,-543,1000,-401,-155,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00384() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-879,-138,-744,-890,-79,796,45,814,509,298,121,-680,-906,772,-203,-938,-84,123,-534,11,3,679,-998,114,-69,-313,719,93,909,471,-925,45,-707,-158,-414,-395,57,-81,-641,757,639,-818,131,844,128,263,-301,784,-221,775,-747,61,-492,278,278,-332,-812,-603,-614,858,-690,48,768,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00385() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-231,-184,-278,-216,96,776,-772,-31,391,1000,-316,-1000,-763,999,-484,1000,517,-112,-1000,14,-793,4,776,716,493,-164,-405,657,705,820,-5,-615,1000,768,1000,927,-527,662,616,532,-370,-203,778,-533,446,-1000,-840,1000,69,-333,-76,238,-747,-189,406,-469,-403,-158,73,126,937,1000,-493,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00386() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{418,1000,-383,-337,-709,758,674,-1000,684,-91,-162,1000,1000,-98,525,773,1000,-517,599,-903,298,-799,1000,-1000,-165,1000,-355,198,-1000,-316,925,1000,736,877,-437,-1000,443,900,-363,916,-263,1000,-14,-393,-440,-1000,-1000,-629,-90,580,-1000,228,412,-1000,-1000,-296,1000,87,1000,-1000,315,-67,-654,-987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00387() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-483,-1000,19,-1000,1000,689,302,1000,1000,1000,-610,-1000,-910,1000,435,-1000,469,1000,-1000,1000,-513,1000,-1000,1000,-1000,300,-1000,-1000,1000,-982,-1000,-1000,228,-389,1000,-76,404,878,-151,5,1000,618,-1000,-854,538,-156,-499,1000,275,-888,470,-896,-1000,1000,538,831,-1000,1000,-130,356,-562,1000,-495,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00388() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{-503,708,-648,-1000,-1000,944,380,-1000,509,-558,-546,-902,282,1000,-203,-237,1000,-985,-1000,216,137,679,-530,684,-1000,-313,957,-73,1000,240,-633,-457,-442,950,904,331,444,-81,894,431,-311,-532,879,137,-590,-801,-995,1000,-363,775,-1000,821,-1000,-744,-424,-58,-65,30,-49,84,-627,928,-1000,-561}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00389() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriods():java.util.Collection",
            new int[]{679,1000,1000,-705,-751,717,919,-1000,-719,-232,-185,-232,177,-590,135,186,89,-1000,46,1000,550,43,-357,564,-163,50,264,-291,811,-64,73,-663,-530,1000,554,49,-512,-294,-56,177,-924,-678,911,102,387,-746,30,1000,771,212,-1000,837,-1000,-532,479,252,418,93,26,-733,0,91,1000,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00390() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{233,-922,1000,1000,589,1000,21,-57,-658,855,-1000,-1000,1000,1000,394,-1000,319,4,416,-522,-33,-1000,-1000,-308,915,216,-625,-1000,-955,-26,-1000,-780,1000,380,-830,1000,-505,-528,980,1000,-291,-494,1000,-9,-2,1000,-196,-1000,900,462,777,1000,640,16,0,573,276,-841,1000,-1000,1000,-454,283,-149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00391() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-202,-496,51,-349,928,104,-393,-342,-59,71,-333,-1000,177,-384,1000,93,-76,303,1000,-66,141,-365,-331,-917,-519,312,400,-233,-987,-247,-753,-72,900,-638,-583,1000,179,-361,318,477,456,-120,623,-364,-842,-482,659,-406,318,623,785,1000,772,148,-336,268,421,1000,1000,242,-486,-581,-660,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00392() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-1000,-196,210,413,613,579,21,-490,-205,1000,-964,-475,764,422,479,-1000,-847,1000,-600,796,337,53,170,-175,1000,-820,17,-327,-1000,-176,-242,-494,1000,23,-732,1000,-6,-859,1000,-400,-427,642,306,79,255,528,-1000,155,318,1000,-343,466,716,836,-860,1000,607,-1000,251,-109,961,30,-35,-143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00393() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-768,789,-129,-1000,166,-508,459,-431,635,938,374,532,-962,175,482,426,-666,973,-99,1000,458,1000,1000,-742,122,-480,1000,1000,-668,449,270,790,873,-719,-46,457,486,-852,623,-1000,806,1000,-309,406,-633,-1000,-887,1000,-1000,501,-599,238,1000,1000,-276,1000,623,470,-69,1000,-189,-271,75,-992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00394() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{400,-1000,-543,-599,855,-301,-118,764,-221,-435,241,-787,-737,-552,552,1000,1000,-419,1000,-1000,-393,-607,-170,-952,-1000,127,493,1000,-874,1000,632,940,667,-1000,-974,-1000,-1000,-1000,-717,1000,1000,-1000,1000,-568,-1000,1000,1000,-1000,-400,-522,1000,500,923,-9,1000,-710,-184,1000,1000,-457,-791,-812,-986,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00395() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-956,-585,737,1000,-348,314,894,220,104,-380,-51,178,1000,359,1000,388,-580,-34,-810,-665,-205,-573,-352,-168,4,-140,-1000,-238,1000,-133,-400,-889,839,395,842,273,270,586,-181,1000,-948,-416,44,709,1000,1000,-443,-344,388,666,55,-359,0,-490,-574,1000,1000,-1000,-977,-921,575,980,843,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00396() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-916,1000,150,-1000,391,169,-49,-950,510,811,-331,-268,-374,448,1000,-136,-878,1000,74,1000,1000,1000,532,-917,272,264,922,1000,-1000,-391,-111,82,664,-1000,-403,620,1000,6,829,-743,467,1000,-135,729,-394,-825,-665,128,-1000,1000,-52,934,574,1000,-276,1000,465,29,64,1000,-37,686,-866,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00397() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{658,-1000,547,196,171,884,589,188,369,-194,151,-276,-625,1000,-453,-431,1000,-1000,441,-919,145,-1000,-983,283,-1000,-454,-393,-983,-650,617,-426,-584,413,104,-814,204,159,-605,586,1000,786,-697,998,5,-962,546,1000,-1000,888,-258,870,1000,91,-1000,402,-1000,510,559,1000,-1000,277,-1000,1000,-855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00398() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{133,-45,669,87,-13,586,-209,-1000,533,728,-125,-235,282,990,-97,116,-351,-313,80,283,969,369,-1000,-599,-731,-166,-600,1000,631,-538,-572,-639,566,-396,680,204,582,98,1000,508,-190,256,801,517,-27,1000,850,-734,-396,385,1000,1000,-1000,-1000,24,-334,684,-390,575,-817,352,-364,378,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00399() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-202,-887,-804,516,1000,7,269,328,73,-324,-8,1000,-743,-384,657,-188,695,284,1000,-393,-828,-516,-331,-515,-932,-297,380,-400,-1000,797,643,-219,857,-455,-1000,-149,911,-1000,-126,1000,985,1000,723,-871,-1000,16,1000,-89,347,452,1000,929,1000,-215,577,-310,1000,1000,-534,-673,-403,-616,-218,852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00400() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-558,-430,1000,1000,698,1000,-649,-57,-1000,855,-1000,-1000,1000,974,-201,-1000,-587,4,-552,254,1000,-1000,-1000,-308,1000,1000,-1000,-1000,711,-1000,-1000,-256,1000,262,474,1000,-989,-366,980,1000,-1000,268,1000,392,1000,1000,-413,-1000,-41,1000,1000,-562,-1000,16,-1000,791,340,-1000,746,-1000,1000,868,-672,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00401() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getTimePeriodsUniqueToOtherSeries(org.jfree.data.time.TimeSeries):java.util.Collection",
            new int[]{-1000,588,-393,-760,702,-422,-442,-1000,67,40,-190,-570,163,406,1000,629,-1000,1000,427,1000,687,441,438,-1000,-600,849,586,1000,-25,-792,-298,192,900,-1000,420,1000,568,-175,332,-743,-40,926,147,43,-172,-812,208,384,-1000,1000,360,81,6,302,-1000,566,693,-81,496,798,-737,217,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00402() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-960,895,1000,-1000,97,1000,-357,-432,534,358,906,-1000,361,-365,585,1000,399,-1000,160,-445,510,-560,69,659,-1000,-185,1000,83,311,340,228,-1000,290,306,841,1000,683,-17,-913,-175,-949,472,-1000,523,85,-133,774,-749,945,-8,-1000,-480,-156,-1000,491,-900,-731,-336,938,293,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00403() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{1000,-997,225,-334,217,-84,1000,752,264,-248,-400,-191,374,1000,156,-251,836,1000,-500,640,-685,590,-825,-1000,-265,256,154,562,-907,355,799,84,-222,1000,-671,-657,-1000,-199,1000,968,-1000,149,192,-462,-1000,-40,776,1000,-643,703,1000,572,-1,539,818,957,115,-1000,985,1000,962,743,1000,-96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00404() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-512,802,735,-589,-260,166,-479,-813,941,343,115,-491,-465,344,925,-4,-871,-577,-65,488,637,-869,82,-489,-817,-673,545,146,-626,587,-190,-985,-81,946,649,729,218,-805,699,594,-517,50,-808,640,-726,436,581,82,872,866,-722,-751,-151,-601,560,-333,-619,70,781,533,-600,797,-505,-885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00405() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{318,-1000,715,730,121,603,432,-416,-572,571,-772,-376,-558,-17,-777,648,823,-279,135,823,-743,1000,-597,-785,643,441,-283,-576,-363,697,-1000,-743,-117,-648,-13,-1000,-192,429,323,166,181,-360,418,528,-510,13,-612,8,79,-604,559,-1000,-456,504,115,-947,-469,-1000,-757,317,772,127,1000,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00406() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-1000,670,194,-285,-149,40,-720,-301,946,-324,1000,310,407,-436,715,646,734,-362,928,-368,1000,-461,724,-37,-1000,-127,445,371,498,-279,1000,-298,1000,-468,1000,431,1000,1000,-87,593,-69,-114,986,1000,844,-942,469,-212,-376,398,281,-1000,-874,-1000,513,-1000,-396,-932,-112,-389,-88,-238,-692,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00407() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-1000,1000,-186,-125,-767,-32,-440,-96,56,-895,1000,82,372,80,-930,29,1000,-133,477,-1000,1000,-1000,1000,1000,-1000,536,-861,733,469,-1000,677,-1000,-1000,-999,-293,1000,521,-1000,-263,-1000,1000,811,-537,1000,1000,493,885,-1000,46,-1000,-1000,52,1000,-175,278,254,1000,1000,-1000,-889,-870,-401,-1000,-184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00408() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{1000,72,-179,-92,-161,139,83,1000,1000,1000,-630,-564,-1000,-875,-1000,-318,-577,244,790,637,-1000,324,-951,-80,-64,414,843,228,93,1000,-510,-1000,-388,-831,-1000,-234,-336,-261,1000,-152,179,771,705,-496,-1000,864,364,57,295,-899,1000,-758,-1000,1000,-1000,-100,-1000,115,-176,1000,193,1000,-745,703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00409() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{1000,802,735,-59,-82,309,371,959,791,1000,-1000,-855,-1000,-1000,-223,-666,-1000,621,354,786,-1000,458,-1000,-513,603,292,545,-247,-283,1000,-801,-1000,-709,-615,-1000,-1000,-552,696,973,1000,-145,138,330,-859,-726,663,14,82,693,-288,1000,-751,-1000,1000,-164,-157,-1000,-644,781,1000,-600,1000,-189,-885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00410() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-1000,1000,1000,-420,-851,784,-966,-1000,536,1000,1000,1000,96,357,1000,-927,529,1000,758,-912,-607,-1000,1000,-663,-1000,-562,-350,378,-565,-533,781,-255,-428,521,1000,1000,400,-525,434,661,188,-103,-808,1000,469,-503,958,-410,804,1000,-1000,-616,-818,-1000,715,433,602,186,346,-713,-300,387,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00411() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-1000,895,1000,-939,-245,482,-640,-1000,314,50,1000,-1000,615,-415,1000,1000,-747,-807,11,-885,627,-1000,507,659,-1000,-282,469,100,-297,216,-348,-1000,354,897,1000,1000,827,474,-1000,276,-333,62,-1000,1000,-123,1000,560,-1000,1000,532,-1000,-480,225,-1000,673,642,-220,0,857,-185,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00412() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{16,-728,1000,488,213,224,-459,-1000,-952,154,-104,-34,669,-747,1000,-967,-403,856,-56,-300,196,251,-12,-1000,358,161,4,-393,12,749,-1000,-390,-241,-134,551,-948,-35,687,-1000,1000,316,-1000,415,397,-871,-357,-1000,-539,335,374,-157,-1000,495,-331,1000,90,-408,-880,-130,-844,-128,112,217,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00413() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(int):java.lang.Number",
            new int[]{-206,360,-526,-781,-592,-591,-164,850,648,571,543,-517,-271,-738,-156,-597,823,-279,177,-763,-669,-982,225,806,-578,548,-283,757,-902,121,460,-719,-630,-831,-627,926,-212,-60,817,895,548,618,418,528,-371,231,741,-284,58,-50,653,-116,-746,902,-935,873,-355,998,-184,577,-741,417,-85,-510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00414() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-487,706,980,782,-306,1000,1000,763,-280,476,1000,1000,-366,754,445,1000,1000,46,-913,-188,-1000,5,-1000,318,-1000,-452,389,190,150,226,532,-1000,1000,1000,1000,935,-388,-191,80,790,-334,-9,-925,546,-1000,-607,484,-823,-236,602,-649,828,90,557,793,-1000,1000,320,55,975,-467,1000,410,304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00415() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-261,11,-1000,-968,1000,-1000,1000,-391,874,316,-1000,-1000,1000,-1000,-450,-1000,706,-563,-292,-502,1000,35,1000,-1000,1000,500,-174,-1000,524,1000,-1000,394,-1000,-1000,-1000,-1000,-210,600,129,-1000,-657,-21,-688,740,1000,669,-1000,672,-289,-1000,1000,150,-1000,-777,-1000,646,-1000,746,550,-1000,1000,-1000,1000,411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00416() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-1000,439,780,624,86,1000,-946,-48,192,63,1000,1000,-752,1000,149,1000,666,-574,-1000,-780,-1000,586,-1000,335,-1000,-1000,-80,848,-20,-204,1000,-1000,1000,1000,1000,1000,-967,446,369,253,550,789,-1000,653,-1000,-1000,667,-1000,-963,650,-343,1000,-70,1000,1000,-1000,1000,391,-140,1000,-1000,1000,587,-247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00417() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-20,462,142,90,150,775,-422,344,266,1000,355,524,142,763,-376,1000,331,22,-975,252,-1000,34,-931,299,-437,-878,-919,108,-90,453,757,-994,223,958,654,228,333,443,784,133,114,146,-1000,853,-676,-891,-17,-1000,183,232,-312,550,-113,955,1000,-979,700,-175,132,669,-14,565,95,-544}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00418() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-771,-56,-606,-1000,1000,-281,-529,-168,1000,-1000,-1000,19,16,60,-914,-105,-189,-660,-1000,-684,107,1000,469,-1000,277,-619,-384,-905,498,815,235,406,-842,-813,-898,97,11,864,-328,-1000,259,885,50,609,-143,-354,-476,341,-770,-258,743,1000,46,560,-77,-577,-153,-7,-107,-848,-380,-142,960,553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00419() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{415,-398,-1000,35,80,-378,-550,203,966,1000,289,-190,1000,-1000,-1000,288,1000,456,-1000,294,-134,-345,-239,-871,645,-873,-707,-898,77,1000,-274,-1000,742,1000,-161,-971,931,959,391,-423,-1000,-154,-925,1000,-197,-661,173,-1000,469,-355,-6,219,-580,806,1000,-659,530,1000,278,-176,745,-178,-296,599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00420() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-187,799,243,-141,-306,-400,1000,973,-181,-1000,801,-400,727,-646,770,-158,1000,-357,-424,-1,400,-347,352,-211,356,331,638,-316,473,788,-868,-1000,67,395,40,-465,-23,-471,-219,530,-848,-283,-814,638,275,-233,-203,-334,261,-34,532,1000,-237,-379,-4,105,-890,416,526,-101,507,-400,410,388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00421() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-632,140,355,1000,261,829,-352,-995,6,-838,-677,1000,-950,678,138,432,1000,328,-217,-424,-1000,-197,-1000,-705,-522,-168,-590,994,-880,-16,1000,-1000,1000,622,1000,1000,-662,1000,1000,272,-1000,-909,-782,1000,-1000,-180,526,-656,-930,-447,175,848,-202,51,-17,-1000,-35,1000,-30,254,-180,1000,463,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00422() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-943,-47,-606,-23,1000,843,-946,-856,1000,-49,-1000,667,204,1000,-477,654,460,-1000,-973,-1000,-1000,606,-883,-588,-543,-534,-474,15,441,338,830,-990,-211,-165,368,459,-843,1000,404,-1000,324,781,-847,789,-594,-278,-371,19,-1000,-471,811,703,-833,529,138,-917,708,690,207,-333,-380,695,1000,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00423() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{195,107,161,264,-1000,-204,672,-70,-586,-544,1000,227,-1000,-1000,-464,-342,786,1000,-931,731,250,-521,-516,-1000,46,-445,944,-1000,484,-24,429,71,1000,503,915,-1000,198,-720,-783,574,189,-1000,-873,286,-326,-410,155,-13,231,-135,-774,685,347,385,578,-949,665,1000,-477,852,-236,678,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00424() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-476,526,-696,-130,782,-772,477,-601,336,87,-961,-674,427,-549,2,-1000,-791,-377,434,-549,1000,1,-87,-716,1000,500,-320,-277,298,912,604,-1000,-555,-896,-780,-250,-446,826,842,-800,-451,-1000,-688,-118,1000,669,-1000,349,-607,-1000,615,310,-521,-777,-963,193,207,808,479,-659,1000,72,899,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00425() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-582,355,441,-987,407,707,967,752,914,-965,1000,-823,-1000,-1000,9,-57,1000,476,-543,59,-27,501,-19,-786,555,281,479,508,-327,-14,-1000,79,193,-643,431,911,8,-548,-653,-243,380,921,445,-1000,-212,-301,199,827,-1000,1000,-487,-434,1000,-816,366,202,1000,-163,-347,-1000,-861,656,1000,655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00426() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "getValue(org.jfree.data.time.RegularTimePeriod):java.lang.Number",
            new int[]{-20,-496,-372,-763,1000,-754,-640,-502,666,-1000,-800,-8,454,-159,180,-620,-22,-367,90,-581,400,267,361,-995,-377,644,-349,-282,509,629,-88,-25,223,958,-583,-995,-446,1000,-133,-1000,-316,168,-239,-334,1000,197,-1000,-60,432,128,1000,1000,-93,-508,-492,-1000,-1000,-51,177,-266,529,-52,55,136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00427() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-1000,-703,1000,-11,-593,-477,292,-111,1000,565,1000,-142,1000,-302,377,1000,645,-556,502,1000,1000,-477,-1000,-1000,901,-149,388,-127,481,-1000,1000,-1000,-229,-999,-771,1000,1000,-57,-1000,-710,1000,-681,131,-723,-78,327,1000,1000,749,717,-307,-1000,1000,-878,1000,-293,30,1000,1000,-955,791,-651,-587,885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00428() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-1000,-471,865,-825,-1000,8,-456,-1000,211,1000,1000,29,1000,503,1000,1000,1000,-555,235,621,-130,484,-1000,964,1000,-365,-1000,417,21,-842,-881,-1000,-1000,421,-1000,1000,490,-340,-1000,-898,1000,-412,689,279,0,529,-745,1000,1000,1000,434,588,1000,-1000,1000,-566,-505,916,1000,-1000,-397,-1000,-923,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00429() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{465,548,-964,-1000,-133,484,-665,602,-581,349,1000,1000,-1000,988,814,-777,707,-162,1000,-513,946,577,-622,-34,851,821,548,803,-655,-66,-926,1000,-538,748,-711,-1000,-1000,-1000,312,-385,-579,62,1000,270,426,1000,-834,888,930,923,785,1000,-723,-742,-934,175,389,-419,306,-421,718,1000,-556,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00430() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{774,-88,37,-314,783,338,-486,1000,582,-700,846,165,122,-122,-276,-866,-74,90,1000,-203,525,198,379,-260,730,22,1000,266,281,548,507,551,621,-453,375,-355,-61,-1000,1000,-409,176,68,-1000,-108,737,1000,760,189,366,-144,206,-1000,-1000,1000,-627,-614,1000,428,-166,877,-900,1000,-609,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00431() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{854,616,-261,-202,832,484,735,1000,-1000,-781,524,543,-1000,156,-138,-1000,707,-162,1000,60,-97,19,-622,-543,1000,709,1000,270,-635,-386,-1000,1000,467,748,281,-901,-1000,-158,1000,16,-850,62,250,-629,407,527,236,410,930,64,55,1000,-1000,-156,-678,175,744,-115,144,690,619,1000,-525,-212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00432() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-723,-1000,347,-1000,-1000,-96,336,-1000,513,-944,1000,-213,186,1000,1000,967,1000,-734,-638,-1000,1000,1000,-1000,573,704,-1000,-836,584,1000,1000,-1000,-1000,-1000,-1000,595,712,-589,-542,-442,797,-517,797,-455,766,-426,968,-9,1000,589,1000,-175,-230,737,-607,-211,-1000,-944,750,-688,-1000,-171,-979,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00433() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-407,-41,191,-1000,-1000,578,-1000,-626,-81,794,1000,244,858,629,1000,1000,1000,-103,583,-221,639,957,-211,-532,1000,-245,-725,692,-119,242,500,-778,-1000,803,-563,72,-253,-1000,-881,-688,633,112,-102,709,571,1000,-913,666,1000,1000,794,588,1000,-498,254,-791,175,516,229,-638,45,-483,-939,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00434() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{-125,491,-319,-372,330,150,-380,1000,-276,-205,766,-204,628,193,-215,-906,189,440,673,-844,-1000,577,748,547,979,315,1000,876,-1000,1000,511,685,749,661,318,-548,-363,-1000,1000,-49,797,-282,-1000,1000,677,967,-457,-953,1000,-651,1000,-1000,-1000,1000,-771,-1000,1000,814,-192,721,160,1000,-381,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00435() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{1000,678,-1000,-981,560,597,635,324,-1000,-182,1000,372,-1000,107,653,-1000,369,158,516,-1000,133,1000,-474,433,543,-363,910,782,-114,818,-1000,560,-502,1000,379,-989,-1000,-616,839,1000,-1000,1000,-26,690,494,1000,-1000,351,-129,583,452,1000,-1000,-74,-1000,79,431,104,-1000,289,74,1000,-844,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00436() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(boolean):void",
            new int[]{1000,296,-47,380,474,-257,1000,545,95,487,-704,-105,-766,-165,-820,-523,-690,165,1000,984,-1000,203,1000,-907,-16,1000,1000,-1000,1000,-368,-1000,335,1000,-459,160,539,-179,-597,650,-180,396,196,-486,-832,587,-1000,105,-98,-1000,-420,-57,-845,-450,-293,386,99,258,652,-116,-84,-617,817,447,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00437() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{404,380,-525,-76,571,84,-310,384,-882,-57,29,-126,1000,-446,1000,-813,-108,1000,1000,-823,201,1000,676,-181,-771,-688,711,1000,200,-229,-850,-724,37,673,-723,0,450,-889,-1000,955,-313,60,-109,-328,-684,400,-734,-1000,-19,379,206,-231,-261,654,-1000,597,816,-410,-1000,-648,1000,-232,-225,-892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00438() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{-517,-913,570,-1000,802,853,-940,386,658,917,328,281,-23,-342,-212,-245,1000,-74,-126,584,1000,-670,-83,-18,113,-417,667,172,-130,1000,-1000,-722,1000,-1000,-589,443,898,642,269,404,-1000,-1000,371,507,-1000,192,1000,-754,1000,1000,1000,1000,-117,349,1000,-1000,-541,1000,-705,860,1000,-703,589,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00439() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{-1000,-850,1000,-1000,459,995,709,372,886,1000,-40,1000,628,688,-310,-629,1000,266,134,1000,1000,-713,-275,281,416,-705,897,-150,-205,1000,-1000,-256,946,-1000,-755,-73,1000,1000,-1,-352,-1000,-1000,272,664,-905,649,1000,-1000,1000,1000,1000,1000,765,648,1000,-1000,-748,1000,-815,1000,-1000,-909,839,-590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00440() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{543,-43,-1000,-157,528,-739,-461,654,-269,-60,95,-57,439,145,333,-659,-298,-415,764,-1000,-487,1000,1000,-1000,-443,-230,180,805,-439,298,-772,-1000,627,1000,-750,205,411,-901,-1000,214,197,-29,1000,-1000,-725,179,-1000,428,249,-99,-11,478,669,492,-1000,333,-206,-142,-739,281,-214,-234,-311,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00441() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{1000,857,1000,-139,-1000,-1000,223,827,-1000,-122,-77,945,-62,-1000,-203,254,1000,541,-654,353,-231,446,-1000,933,-153,41,272,1000,885,148,876,1000,-605,-1000,-662,79,-364,1000,897,1000,-743,-100,234,-1000,402,606,1000,-1000,171,-483,43,845,-55,-207,1000,-376,-621,189,603,-562,825,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00442() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:OA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{211,-913,-1000,6,1000,456,-29,715,-596,93,237,-493,-23,270,402,-534,-343,20,36,-293,-48,597,844,-314,428,-417,-260,1000,-102,-544,1000,-1000,855,482,-394,543,285,-1000,-1000,-1000,768,64,177,-847,-684,192,-831,1000,-20,888,-570,564,-101,316,-752,1000,1000,-262,-815,-138,-639,-222,589,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00443() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{-671,383,-883,-106,718,115,-234,294,59,989,214,-138,-292,-971,-388,230,256,-210,-872,-970,-798,716,-964,391,758,565,-215,-852,-33,-910,-42,599,-62,799,-695,219,485,-391,-859,-394,-324,459,-678,117,891,-448,736,131,878,788,782,-293,986,158,678,-483,245,417,-414,-658,-668,-846,-464,-801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00444() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{42,82,173,-464,-101,-59,-308,492,83,693,181,-941,457,334,315,-1000,-15,131,472,129,366,535,400,-290,-751,-981,101,270,76,968,-71,-106,-592,-170,-482,680,227,-231,-325,541,354,-118,400,1000,166,260,71,-232,-318,496,712,377,-393,-162,132,434,189,587,890,-385,232,-418,674,170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00445() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{-18,-1000,439,333,151,563,181,354,8,153,92,-717,195,999,-487,-158,103,487,941,-506,1000,-1000,150,616,743,-1000,976,-428,-376,1000,-1000,-385,-277,-619,-746,292,-202,256,-441,-1000,-1000,-1000,1000,119,-1000,734,-953,-232,232,430,712,513,-1000,635,684,-141,-28,695,-338,949,-767,353,282,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00446() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{-195,1000,330,1000,292,-982,623,32,-885,148,694,-737,594,-796,1000,-664,-778,-662,-1000,-1000,-1000,1000,-1000,100,-1000,1000,-546,1000,1000,-1000,1000,748,498,807,727,-717,266,256,926,-58,1000,1000,-1000,-616,1000,-1000,-530,-97,-401,-35,-589,-587,1000,-109,-1000,-900,504,-236,-230,-1000,-1000,-998,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00447() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "removeAgedItems(long,boolean):void",
            new int[]{768,-734,1000,749,-875,677,-160,739,273,966,-341,1000,202,686,-1000,-1000,1000,-1000,15,1000,1000,-1000,-279,1000,1000,-1000,703,-986,-700,1000,-1000,372,-771,-1000,-1000,-31,-347,1000,0,1000,-1000,-1000,1000,554,-1000,1000,1000,-1000,723,815,1000,1000,-1000,96,1000,-1000,-726,688,424,1000,1000,-821,-343,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00448() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:KzB4M2U4|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-494,103,766,136,-1000,942,14,1000,184,965,249,-335,-873,372,-203,343,-989,-979,787,952,-498,635,622,-1000,-1000,234,775,1000,482,-675,788,-489,83,755,-54,-596,-1000,-582,-746,-511,-256,187,-662,-593,-400,746,403,240,-277,-329,599,-482,-203,-869,489,-756,-1000,-340,-710,-461,-419,1000,-1000,-423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00449() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:KzB4OA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-1000,-50,702,206,-1000,354,-993,554,-226,1000,383,114,-1000,-426,744,-155,594,-1000,364,-107,632,128,1000,-1000,-1000,334,1000,1000,1000,-1000,1000,-1000,325,871,452,499,-413,-119,-278,-323,-1000,31,-1000,-74,611,843,227,558,-768,-329,470,68,-415,-1000,697,-1000,-1000,-106,-1000,100,-112,1000,-1000,-671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00450() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:TmFO|getItemCount=java.lang.Integer:Ng==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-72,-239,934,-456,-508,-965,-651,-662,428,313,-910,951,685,-37,-882,-220,38,-115,-697,-985,199,679,-177,740,228,117,230,-982,-64,188,-471,-335,858,-885,975,-753,833,-167,309,818,192,-336,-348,-157,-844,-826,-599,843,-855,-478,-901,-622,-583,-977,-497,-806,673,-925,-175,546,-885,-855,506,-936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00451() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:IA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-254,1000,779,1000,328,-306,967,-1000,218,262,1000,-950,-1000,225,1000,-146,-248,113,1000,1000,-334,306,190,-218,-417,778,454,990,-502,-48,1000,-1000,615,283,140,450,-615,-472,84,-273,469,1000,-8,-5,891,832,11,-346,114,-536,593,838,1000,-211,1000,-123,-1000,855,482,-1000,920,265,-989,-613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00452() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-266,-361,503,-1000,-1000,-1000,-519,-1000,-711,1000,-1000,1000,500,203,435,1000,-149,113,-659,-1000,-334,934,1000,-1000,-319,-1000,128,585,431,-1000,744,1000,-54,283,-1000,108,1000,1000,1000,-1000,469,-1000,-842,-2,-856,-703,100,996,-537,-608,-895,-1000,-543,-1000,-1000,676,-399,-885,-344,1000,-670,-893,-81,-210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00453() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:MHg4MDAwMDAwMDA=|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{400,364,-61,773,-683,358,-247,925,-743,-520,1000,-1000,207,1000,-1000,281,-108,-351,1000,1000,-1000,368,-695,786,-685,1000,-548,-372,-1000,558,485,175,921,280,-717,-514,-1000,1000,-1000,-1000,1000,1000,-601,-1000,-922,826,857,-92,1000,140,834,75,-228,574,1000,-253,847,330,-78,-990,-1000,-243,37,309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00454() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:dlpuXzBUTlNyX09mYV96WVYu|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-902,-782,778,-235,-1000,-214,-499,-522,438,784,985,108,1000,819,1000,1000,1000,-1000,-600,88,-990,1000,-353,-370,-252,-382,954,416,-545,-1000,994,-939,262,551,-44,-1000,547,-979,-61,-1000,106,-579,-937,135,-1000,-1000,-154,1000,-300,-357,821,-1000,-486,-497,1000,-196,-979,-868,-1000,-392,-1000,-1000,-882,-482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00455() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:ZktDc2k3CmhaVU43|getItemCount=java.lang.Integer:OQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{202,770,-80,-381,-484,543,610,98,222,-550,786,-763,811,943,-651,680,288,-327,641,741,-829,952,-863,767,-186,597,-358,-754,-892,541,162,-56,472,-317,28,-763,-490,-427,-196,-294,695,759,78,59,-994,-130,-714,-487,583,-153,419,-177,142,128,652,400,151,40,742,-882,-426,-819,370,-239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00456() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:LTB4OA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{221,992,942,1000,-27,-446,-477,365,-1000,-62,1000,-1000,-555,783,-372,-364,498,-77,1000,705,1000,-38,-759,1000,-153,1000,-764,-372,-1000,847,634,-141,1000,-80,-363,603,-814,1000,-164,-626,650,1000,-799,-425,58,761,741,-564,-430,59,727,1000,614,971,-43,83,847,1000,593,-1000,-344,-1000,37,-998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00457() {
        org.junit.Assert.assertEquals("VOID|getDomainDescription=java.lang.String:LS0weDgwMDAwMDAwMDAwMDAwMDA=|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setDomainDescription(java.lang.String):void",
            new int[]{-750,1000,819,690,-63,-207,-315,199,-453,41,1000,-894,-1000,62,693,-578,-123,-588,1000,531,771,55,530,740,-240,1000,466,1000,0,-262,1000,-940,883,241,452,1000,-735,710,449,24,-756,1000,-943,228,1000,653,237,-436,199,-445,447,1000,1000,-301,-1000,-277,-1000,1000,249,-629,517,-147,-1000,-456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00458() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemAge=java.lang.Long:MA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{-1000,-818,-1000,-1000,1000,1000,-516,84,-107,823,839,-356,-463,555,1000,-805,-296,-1000,1000,1000,583,1000,-698,-325,-1000,-557,1000,913,939,695,1000,-589,492,-125,-36,-1000,-530,399,69,-414,-975,76,-153,1000,312,-1000,1000,1000,554,-461,654,1000,-142,22,69,547,1000,-1000,-751,-1000,215,77,-616,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00459() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemAge=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{95,-326,-34,-1000,1000,-124,-419,849,700,1000,-1000,-808,-404,-645,-203,-939,601,-763,194,-100,0,300,773,18,-1000,290,-691,1000,1000,777,-76,1000,-988,951,-241,-1000,-513,-376,362,-954,-831,-810,-20,58,-29,236,347,1000,-165,-143,1000,-1000,1000,-533,1000,-628,385,701,-308,-418,-1000,853,-401,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00460() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{768,670,-268,-1000,-468,1000,-360,-189,-904,965,559,1000,339,1000,28,816,132,674,853,-1000,960,112,4,245,-577,424,-562,-118,1000,196,-76,-1000,631,1000,-664,390,400,28,-850,-1000,872,-260,-1000,-137,-803,220,758,-1000,493,-400,171,685,-715,673,120,-201,897,373,673,-155,910,931,1000,-82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00461() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemAge=java.lang.Long:NzA=|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{-551,425,-537,381,1000,487,197,512,215,-325,632,882,797,-182,131,751,-1000,-339,-631,51,487,-391,700,-338,827,-277,-72,980,581,95,-198,459,-895,-839,-150,-886,-437,-551,1000,-954,459,-144,-826,-88,-292,-753,-640,301,71,330,-241,-270,577,297,-99,620,-734,-945,872,303,782,-256,433,908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00462() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemAge=java.lang.Long:MA==|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{390,579,406,1000,-717,-976,219,887,-556,-697,21,-360,581,-817,30,965,-212,1000,-1000,-337,618,-1000,1000,75,382,-345,-401,-32,254,366,552,1000,-617,735,232,-678,1000,1000,-50,-317,216,911,-1000,-1000,316,-1000,-636,-1000,-68,-171,671,-569,239,508,332,-894,-183,101,688,1000,616,-387,913,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00463() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemAge=java.lang.Long:MA==|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{1000,-178,-314,-291,320,144,1,703,879,1000,-1000,-134,-404,-817,388,461,743,-1000,-484,-51,1000,129,773,-493,-192,1000,-446,422,684,782,451,160,-1000,906,453,-432,439,990,-20,-711,-600,-495,-276,58,-485,114,98,-386,81,433,1000,-1000,-103,-343,311,-399,1000,-267,-601,1000,-440,1000,257,-487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00464() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemAge=java.lang.Long:MA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{400,-69,-1000,-1000,1000,487,-324,1000,252,127,733,882,141,-170,-252,-1000,266,-339,406,1000,487,1000,-375,1000,-1000,-608,-306,1000,1000,-313,-1000,219,-351,-358,-266,-1000,-1000,-966,1000,-1000,-1000,-927,106,986,-502,-753,649,1000,100,-553,717,492,1000,116,1000,-769,250,-1000,-1000,-1000,-220,-166,-762,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00465() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{1000,126,-109,767,-1000,-889,1000,-391,-754,-869,-1000,1000,1000,-1000,92,1000,141,1000,825,321,-914,-1000,1000,-134,-462,616,-1000,-1000,-109,760,-686,48,496,797,-217,-475,1000,919,419,844,-1000,1000,-793,-1000,-249,1000,1000,734,1000,84,385,-798,-673,795,105,-565,1000,974,1000,1000,58,1000,1000,-129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00466() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemAge=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemAge(long):void",
            new int[]{813,-567,-377,-1000,931,1000,-31,-1000,91,1000,-1000,-633,336,1000,934,-420,20,319,581,-490,-359,743,-247,-248,-1000,1000,-955,1000,277,-133,755,-1000,692,320,-129,-624,-400,808,-1000,-226,-363,-810,106,-83,-473,-879,918,874,1000,351,491,-402,629,92,79,705,1000,710,-180,-1000,-1000,1000,-568,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00467() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{1000,657,1000,-372,46,-82,-921,466,-934,682,-152,241,-268,456,930,425,-1000,-1000,831,1000,-972,-991,-1000,-819,262,59,607,-924,4,-122,500,-903,-1000,-389,606,719,593,-1000,-1000,-169,-1000,231,-589,-840,-523,236,1000,1000,615,-894,-984,-1000,-1000,106,294,-628,-358,-607,-1000,-1000,-664,348,302,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00468() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MjE0NzQ4MzY0Nw==|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{-726,21,-1000,1000,-1000,663,44,39,836,-251,557,489,-1000,-163,-1000,317,622,505,-498,-262,1000,10,-150,-379,-196,658,29,592,-1000,-765,-1000,-936,431,123,-1000,71,-1000,-750,710,291,-29,345,128,-1000,380,-448,-383,-1000,464,632,1000,1000,1000,827,385,509,1000,-276,850,1000,185,24,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00469() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{-1000,-428,-783,676,593,889,-1000,228,-1000,837,593,1000,-120,21,-549,-253,900,-1000,-390,-354,-791,16,-1000,-422,-640,1000,1000,1000,1000,-1000,-729,-393,-1000,280,85,-126,-466,-743,1000,-21,1000,1000,-144,-909,378,-983,1000,-1000,-604,1000,343,743,-403,-1000,302,-1000,1000,92,-196,1000,-1000,1000,269,-108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00470() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{58,-1000,-1000,687,1000,874,-114,-928,623,67,374,1000,-609,-78,676,665,781,1000,-429,-415,787,-279,263,1000,1000,-1000,1000,-1000,42,1000,-1000,573,769,-1000,814,182,-147,-687,-674,1000,-181,514,1000,807,-338,1000,-489,-1000,-366,-1000,627,1000,31,-809,1000,363,1000,1000,915,-1000,-262,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00471() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{1000,725,1000,808,283,-800,-578,975,-1000,1000,-1000,-8,1000,-282,773,-1000,28,-1000,1000,-98,-1000,110,-1000,-734,-848,417,114,-1000,756,-137,1000,-518,-1000,824,1000,561,10,157,-101,645,268,565,-1000,-207,274,-946,1000,716,-538,-12,-1000,-1000,-1000,190,383,-1000,-1000,-906,-1000,-734,-1000,949,1000,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00472() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{237,-52,1000,-710,-340,98,-129,1000,-1000,-175,-20,-437,676,-338,177,-1000,-41,817,634,-89,-1000,185,-919,-258,-636,1000,-417,-253,534,-873,1000,-601,-1000,400,391,-360,-67,-1000,200,-733,405,267,-787,-1000,172,647,-488,508,-645,-271,-10,-475,1000,146,27,-745,-755,-1000,-996,137,-882,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00473() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MjE0NzQ4MzY0Nw==|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{94,-777,-202,647,576,983,-149,926,-279,284,526,199,227,-1000,-11,-1000,7,-895,-260,-643,-158,-118,-856,190,146,876,1000,59,574,-433,176,-63,-552,499,143,-330,-946,-676,293,-21,640,13,99,-448,123,-924,302,-590,-936,373,367,688,733,381,428,-547,-129,-623,-624,1000,-585,968,610,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00474() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{814,-19,399,773,90,395,-417,182,-105,-239,316,-12,-441,1000,723,767,973,138,-415,1000,23,-132,-103,458,812,677,-105,-1000,215,990,-520,185,-337,-1000,656,18,-372,828,-1000,899,-460,421,-48,-21,-371,1000,-28,-277,432,-671,-1000,-386,549,-167,-33,-336,529,-344,-258,-1000,436,-423,-959,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00475() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MQ==|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{708,783,1000,440,-1000,-1000,-1000,1000,614,1000,-1000,-823,789,-1000,-419,-173,335,-1000,1000,-771,-764,342,-455,-1000,-1000,810,-1000,-130,745,-846,1000,-170,-1000,1000,501,-500,-584,1000,59,-1000,152,345,-1000,-1000,1000,-1000,1000,1000,163,362,-1000,-606,113,608,-682,-1000,-1000,-1000,-1000,300,-549,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00476() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MQ==|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{734,527,516,808,367,-195,-887,975,-336,515,-430,-85,128,1000,-241,46,28,-414,-450,1000,-259,240,-157,-256,331,64,-18,-756,1000,-42,-283,385,-543,-471,668,-404,469,-332,-874,645,-229,521,71,-458,482,711,741,-294,527,-530,-1000,-689,-529,-298,-212,-889,431,69,-363,-734,43,529,-203,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00477() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{1000,395,1000,330,-1000,-36,478,345,248,-49,-598,-53,491,987,592,-304,973,326,1000,1000,-600,-1000,76,-398,199,-1000,-980,-1000,495,-34,1000,-222,-250,-135,305,1000,-201,575,-1000,-316,-1000,-1000,25,337,-576,388,-279,1000,1000,-1000,-1000,-1000,549,-486,-1000,88,-1000,-862,-234,-1000,440,-377,531,371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00478() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MjI=|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{-178,976,1000,465,-194,-591,-1000,765,-1000,512,-556,-903,-107,603,-1000,-52,-1,-1000,-322,411,-1000,193,-592,-1000,229,622,267,141,213,-1000,271,-127,-1000,614,252,-250,398,-686,178,238,376,351,-231,-1000,-147,481,1000,147,425,-1000,-1000,-1000,1000,-544,-294,-1000,-828,-873,-1000,-715,-1000,1000,596,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00479() {
        org.junit.Assert.assertEquals("VOID|getMaximumItemCount=java.lang.Integer:MA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setMaximumItemCount(int):void",
            new int[]{-344,-1000,-1000,1000,1000,856,-134,-890,-443,356,1000,1000,-762,1000,496,-273,614,-568,-1000,657,321,476,-1,1000,1000,1000,1000,-874,-1000,1000,-1000,1000,-31,-301,508,-483,188,-244,-650,1000,791,265,1000,418,-475,1000,174,-1000,-393,-1000,309,1000,98,-877,924,-484,1000,1000,556,-643,-244,-453,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00480() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:NTE4bA==|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{382,-1000,1000,-1000,1000,279,53,172,-728,42,1000,566,52,756,-174,-547,949,-2,1000,-126,-1000,1000,-732,-1000,-665,-1000,1000,-455,-518,-1000,484,-1000,-530,-1000,378,-1000,646,201,255,794,-407,383,1000,1000,656,-821,-207,-514,-221,1000,553,702,-211,792,-1000,402,25,403,-1000,116,-202,707,-842,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00481() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:LTEwMDBm|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{-105,901,-9,244,-397,392,-901,526,410,-677,-1000,412,633,-808,-846,-90,-93,1000,-903,-126,-457,1000,827,960,-101,418,-411,-37,233,1000,-747,618,377,1000,-255,-781,-718,-494,-424,747,-666,571,-1000,-1000,13,732,1000,1000,-221,118,-791,1000,-974,622,163,-301,1000,288,-187,-541,-701,707,235,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00482() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:KzB4OA==|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{395,228,-679,349,1000,-787,-512,1000,211,-236,1000,908,593,122,-1000,403,736,876,-584,-560,-622,1000,-313,-544,400,-236,873,-27,-306,1000,-654,-203,-1000,-315,1000,-692,-594,-220,200,1000,-1000,337,577,-321,1000,1000,215,28,-1000,-53,-1000,1000,-1000,353,315,770,946,974,-1000,-775,-153,924,-290,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00483() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=NULL|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{611,683,-77,590,1000,-260,-181,91,-841,-776,1000,-442,222,-767,1000,-1000,-96,1000,1000,-113,-538,-1000,-1000,1000,912,-701,1000,-1000,-777,817,438,1000,-1000,310,1000,-932,516,1000,619,1000,167,-1000,633,574,593,136,-642,-1000,-853,125,-1000,-862,1000,870,-462,-1000,-1000,-1000,544,706,179,-1000,-124,-850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00484() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:LS0weDg=|getItemCount=java.lang.Integer:NA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{441,-1000,-496,-763,220,-531,373,604,-227,-285,-424,177,49,-1000,123,755,-464,195,-905,-28,-1000,-659,498,-32,-499,-24,-1000,-433,893,865,-328,161,767,1000,-717,-1000,980,-101,-674,520,-362,-1000,-1000,-335,-260,1000,-696,-857,-1000,-265,-1000,924,-780,448,583,551,329,148,-993,-55,731,-204,1000,436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00485() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:KzB4OA==|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{716,-1000,616,287,22,-376,-30,1000,120,-80,1000,1000,-717,1000,-1000,435,1000,47,263,-1000,402,1000,-825,-1000,-45,-886,1000,0,-1000,-583,-112,-1000,-555,-1000,806,-813,799,-455,442,794,-1000,1000,1000,762,1000,-248,289,205,538,1000,-98,1000,-1000,469,-1000,1000,-359,1000,-1000,-432,242,1000,-1000,-658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00486() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:MHg4MDAwMDAwMDAwMDAwMA==|getItemCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{1000,-1000,434,-795,-1000,954,-192,1000,-286,-583,1000,1000,-870,1000,-1000,-822,1000,-247,735,-1000,-1000,1000,-1000,-1000,-903,-1000,636,454,-770,258,-557,-1000,1000,-95,-1000,195,287,-652,-1000,-782,-674,689,1000,1000,-439,679,-1000,-230,-166,784,972,539,-1000,-1000,342,1000,-88,46,-1000,-718,865,1000,-450,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00487() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:OV9ENl9rL19YbnRfMGY2Nk4zbVk2X1ZB|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{716,1000,-732,287,22,-721,-65,-1000,-653,-319,153,435,-717,-1000,1000,-634,-1000,805,765,246,402,-229,-1000,1000,915,-1000,843,-1000,545,-583,864,1000,-1000,-1000,267,-897,-1000,1000,455,1000,-517,-1000,-692,281,740,-71,782,-1000,-1000,-1,80,-1000,607,1000,-1000,-51,-359,-1000,201,591,242,-1000,355,512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00488() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:NDEw|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{1000,-681,-842,737,1000,-782,-579,955,-996,-1000,1000,1000,-57,1000,-1000,632,1000,291,226,-1000,-1000,19,-1000,36,410,-804,1000,75,-594,-411,260,-1000,-1000,-1000,1000,-937,-913,424,-1000,314,-1000,340,1000,703,144,-87,-867,-910,-342,595,186,865,-636,812,231,991,363,1000,-1000,-398,1000,847,-1000,-357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00489() {
        org.junit.Assert.assertEquals("VOID|getRangeDescription=java.lang.String:ZmFsc2U=|getItemCount=java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "setRangeDescription(java.lang.String):void",
            new int[]{43,726,-155,16,533,-8,324,-518,237,-876,80,161,-747,-627,1000,-900,-716,395,703,709,31,-392,-92,928,1000,-153,448,-1000,264,-1000,1000,1000,-954,-1000,1000,-261,188,573,-122,1000,-557,-571,-965,-359,1000,6,1000,-981,-21,-356,475,167,311,640,-1000,117,337,-534,391,466,-314,-909,-671,-990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00490() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MTE=", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{-361,663,-972,-996,391,258,996,-355,534,-32,-407,-133,-526,-85,-236,-1000,-1000,670,-684,-368,336,-229,-196,367,-242,1000,1000,-468,-949,1000,114,-443,303,800,427,209,571,-54,280,1000,-5,386,520,1000,-481,604,529,197,-148,229,60,-118,-560,311,138,-1000,481,-441,-11,38,-288,-731,-1000,-882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00491() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{-52,-903,-446,147,26,79,-713,-160,-331,-773,677,-515,672,183,-673,175,928,-732,-679,-35,-947,802,636,-499,399,318,-72,-682,965,386,-3,-182,-154,434,649,119,17,241,-881,-224,-500,561,-268,-289,246,-281,73,-403,440,-19,571,405,-647,-33,94,878,346,-528,-49,-737,-802,672,500,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00492() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{-1000,1000,-82,-961,639,179,1000,-27,1000,882,-565,1000,377,761,26,-146,1000,1000,22,983,1000,149,-1000,-1000,433,-235,305,1000,-832,1000,474,827,1000,-923,-1000,1000,-1000,569,1000,1000,1000,-1000,34,96,-1000,-403,-1000,1000,218,918,-1000,-619,739,-204,-1000,-1000,-1000,-860,715,1000,225,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00493() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Nw==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{-511,115,-453,-78,1000,783,472,-453,49,-87,22,477,523,259,-64,-1000,-822,483,-772,276,47,145,234,133,-145,-283,67,188,-580,453,-104,-59,192,-827,1000,245,-968,863,-81,890,507,453,-331,184,-269,1000,-669,426,151,368,340,-644,-373,302,1000,-276,-839,-726,640,-19,339,-941,-867,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00494() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{207,-1000,-1000,-1000,944,219,87,-876,-668,-1000,706,-919,16,-461,-325,-228,775,-731,-1000,-1000,-1000,43,1000,-637,-962,698,461,-1000,281,172,162,192,-1000,902,1000,-1000,-280,-100,-1000,-276,-685,1000,-655,666,522,119,337,-691,741,-284,1000,-374,-339,360,266,957,965,-40,-757,-1000,-364,331,208,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00495() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{-972,-195,-446,1000,644,70,-433,765,-163,-14,-678,1000,-920,-996,373,1000,961,768,-679,996,617,558,-31,-419,-193,158,-275,-206,-756,-1000,924,-1000,-154,-237,286,11,17,-953,335,1000,324,211,-387,319,246,-543,-607,-319,-951,-19,-705,405,-647,-429,96,97,-1000,-916,75,485,-802,672,162,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00496() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{-750,361,570,643,-351,-983,42,749,882,-442,-151,221,209,276,-511,-425,673,7,67,1000,415,821,-298,224,945,457,237,314,774,1000,763,-297,1000,244,295,1000,11,-513,664,-68,-55,-885,14,-567,-796,-471,-400,86,-367,485,-74,491,95,-595,-338,-157,115,-619,12,223,-431,661,310,148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00497() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{87,-428,-994,-259,169,-226,398,-1000,-892,-447,-538,-520,209,188,-511,-425,678,-1000,-557,-973,-865,-98,-298,-1000,720,146,-399,-497,-433,-181,-586,-297,-215,523,-394,-204,11,313,-1000,1000,-867,1000,315,1000,-796,1000,1000,86,280,-1000,132,203,818,-715,-338,-221,-570,23,736,223,-281,661,-862,244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00498() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(int,java.lang.Number):void",
            new int[]{-163,-333,1000,630,301,-855,-152,718,-266,-1000,-171,-591,988,1000,808,-393,271,-390,1000,-23,335,1000,706,1000,945,-846,-1000,739,1000,-1000,830,1000,56,-960,-929,1000,1000,-944,15,-605,851,1000,10,-1000,1000,-481,-701,107,888,-832,-577,-980,-400,665,1000,782,-348,-1000,939,-326,-1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00499() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-444,-728,-480,77,113,814,-79,-745,851,-936,409,-441,-691,-1000,-539,-507,1000,-190,1000,-59,641,-515,-832,-764,-181,1000,-361,67,-724,225,-217,971,-615,-156,260,627,-885,536,-260,-364,891,3,-3,1000,-100,919,-142,1000,-632,-1000,748,-667,359,-457,28,234,179,46,-265,-210,-841,708,-742,-373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00500() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{355,-397,-537,434,-664,468,106,-27,-862,721,-127,-144,442,400,-408,87,260,317,-326,-567,-63,128,-509,1000,992,168,567,388,-580,-251,82,-898,-677,502,-1000,-684,-126,-48,-737,-1000,602,264,-459,-748,-889,44,-770,512,-136,321,-201,-1000,889,-438,-1000,963,-501,487,-661,1000,-365,928,-292,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00501() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{508,-75,279,588,-1000,175,349,80,464,762,693,434,965,247,491,-1000,5,628,388,1000,196,212,-569,-335,264,-814,347,586,717,402,348,-580,-562,-9,-671,60,422,-402,-221,-661,-887,464,-629,892,705,497,463,832,-926,-208,-799,323,384,-828,659,-302,1000,169,-57,941,310,-131,736,-921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00502() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{1000,450,532,191,-1000,626,-6,-36,-233,814,1000,757,1000,247,227,-1000,28,271,388,1000,525,93,-122,891,405,-634,385,586,1000,-172,-279,-1000,-755,109,-956,138,-133,172,-138,-597,-778,-52,-553,612,699,403,297,642,-953,-286,-606,323,307,-663,267,-196,131,-190,-437,1000,-90,-131,483,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00503() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{6,-924,954,-148,-985,979,749,-917,625,-796,-501,600,239,401,165,98,-480,89,-617,-817,487,435,586,-915,689,-241,333,458,720,-877,-924,-582,802,657,-593,-39,-109,-564,970,-329,812,-359,-291,-749,317,532,-115,833,-380,299,379,-791,979,263,212,230,-817,88,-89,-587,-574,674,-609,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00504() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-333,-691,-1000,764,426,1000,-441,1000,896,-485,-638,-979,-184,-959,-440,779,845,1000,-37,-752,0,175,-721,-593,-590,443,230,-1000,676,548,322,838,287,191,226,16,-314,-423,-249,-1000,1000,-940,-666,636,-167,-433,20,547,10,28,292,-883,427,531,-125,589,565,443,338,153,-229,505,-286,18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00505() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-162,-924,-292,1000,-1000,979,448,-834,-226,-177,212,319,-75,-1000,-370,-1000,966,424,1000,352,531,-385,-628,255,776,315,333,-1000,-1000,59,926,-139,-705,-230,-593,-322,-109,-564,-81,-997,584,1000,-701,1000,-602,1000,-275,982,-962,-887,318,-1000,1000,-709,-440,371,-281,1000,-457,916,180,1000,-306,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00506() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{1000,349,-408,1000,-847,503,-111,1000,-1000,1000,-348,-1000,-25,1000,-747,971,1000,1000,-1000,-192,-1000,1000,21,955,1000,571,685,-883,760,173,1000,-1000,-207,588,-1000,-1000,999,-1000,-317,-1000,-1000,1000,-650,-1000,-610,-887,-155,209,513,1000,594,-515,574,-893,-1000,322,-686,413,41,1000,228,204,782,-972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00507() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{-1000,-1000,915,155,190,-196,658,-58,-203,-1000,25,-632,-1000,464,-1000,-776,1000,-514,1000,-203,1000,-1000,-1000,-152,-77,1000,-670,-229,-1000,-74,-156,-584,-1000,-519,-156,-612,-981,194,691,-584,1000,44,569,1000,-575,684,-901,1000,-327,-1000,1000,-1000,452,-678,-729,549,711,-58,-326,644,-444,829,-1000,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00508() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{1000,201,-720,1000,495,332,-323,974,464,1000,-1000,-538,1000,1000,1000,748,-1000,1000,-1000,-633,-361,1000,-902,-788,-384,-1000,-247,63,-765,1000,1000,973,-614,594,-364,-1000,1000,-1000,-1000,-15,975,124,-731,-1000,822,-82,920,-163,886,1000,1000,692,206,-1000,273,675,-1000,-672,-752,1000,1000,73,1000,-910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00509() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{548,407,-1000,584,157,616,797,386,559,863,-519,-207,609,1000,17,528,-348,1000,-841,-786,-1000,688,-438,-840,-259,-438,794,-1000,-1000,815,1000,693,-140,376,-314,165,997,-299,-1000,-6,348,1000,-413,-1000,-840,-141,-226,-22,148,814,-716,74,791,64,113,723,-1000,1000,83,1000,-615,879,457,-839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00510() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.general.SeriesException", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{440,-691,1000,155,-443,306,-148,-440,1000,-573,-638,434,-91,523,354,-299,-172,671,-318,-531,-59,644,169,-1000,392,443,230,659,-587,-626,-231,-591,573,461,564,-262,552,-309,651,-1000,-778,-369,-446,-836,412,281,303,754,-358,92,225,132,635,-85,580,440,-1000,662,728,-494,-411,-4,536,-269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00511() {
        org.junit.Assert.assertEquals("VOID|getItemCount=java.lang.Integer:Mg==", DEReplay.run(
            "org.jfree.data.time.TimeSeries", "org.jfree.data.time.TimeSeries", "update(org.jfree.data.time.RegularTimePeriod,java.lang.Number):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00512() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.ChartPanel", DEReplay.run(
            "org.jfree.chart.demo.TimeSeriesChartDemo1", "", "createDemoPanel():javax.swing.JPanel",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00513() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:Mw==|getMinY=25:java.lang.Double:LTg2Ljg=|getMaxY=45:java.lang.Double:LTEzLjI5OTk5OTk5OTk5OTk5Nw==", DEReplay.run(
            "org.jfree.data.time.MovingAverage", "", "createMovingAverage(org.jfree.data.time.TimeSeries,java.lang.String,int,int):org.jfree.data.time.TimeSeries",
            new int[]{253,119,-868,602,-195,-725,528,-322,608,548,22,328,617,937,476,-72,-565,521,-338,-991,252,313,53,410,-197,817,-958,12,345,-204,-14,-836,-884,755,538,744,-26,881,-327,8,-983,814,530,-134,834,482,737,15,766,-526,-763,512,-949,938,800,760,-956,831,387,668,-142,-503,-683,-242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00514() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeriesCollection|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.MovingAverage", "", "createMovingAverage(org.jfree.data.time.TimeSeriesCollection,java.lang.String,int,int):org.jfree.data.time.TimeSeriesCollection",
            new int[]{407,136,-120,-484,-334,-913,337,734,-624,-373,-367,267,738,-512,-370,555,-43,-555,-53,-685,966,-499,692,-582,225,-497,838,575,165,436,-224,-575,-941,193,-421,181,374,-277,567,-914,-19,93,850,678,991,248,-259,50,-542,-983,-884,-845,-317,-737,142,564,-560,995,-870,-983,-889,-300,-298,-381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00515() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeriesCollection|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.MovingAverage", "", "createMovingAverage(org.jfree.data.time.TimeSeriesCollection,java.lang.String,int,int):org.jfree.data.time.TimeSeriesCollection",
            new int[]{-286,297,-529,-497,-926,-1000,226,751,-926,-814,-1000,824,1000,-707,-610,359,-613,-311,265,-1000,978,-409,27,-351,-87,-188,565,259,812,622,315,-603,-848,732,-1000,593,1000,-736,1000,-305,-483,416,1000,223,651,-169,450,348,-1000,-1000,-1000,-31,-920,-105,29,-82,-263,813,-1000,-1000,-957,-549,342,-810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00516() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.time.TimeSeries|getItemCount=22:java.lang.Integer:MA==|getMinY=21:java.lang.Double:TmFO|getMaxY=21:java.lang.Double:TmFO", DEReplay.run(
            "org.jfree.data.time.MovingAverage", "", "createPointMovingAverage(org.jfree.data.time.TimeSeries,java.lang.String,int):org.jfree.data.time.TimeSeries",
            new int[]{728,972,-27,324,-658,-452,359,-843,662,-831,-493,-872,-848,-764,-687,-361,-585,-204,461,-755,-329,892,-430,-681,637,914,32,-273,-976,-312,-292,640,-760,931,241,75,285,-728,482,787,-667,-962,285,736,-965,-330,740,506,181,-189,835,768,-942,871,469,-814,-455,-847,-233,977,-191,-437,727,768}));
    }
}
