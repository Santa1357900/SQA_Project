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
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{-237,423,147,-179,1000,-373,304,-225,305,395,-319,-335,126,-108,1000,-411,-1000,-1000,463,-395,780,629,427,581,1000,936,1000,-671,1000,278,-255,1000,670,-766,52,645,-440,-1000,207,-871,-529,1000,-1000,226,-1000,90,-517,-1000,1000,-1000,805,-587,809,-321,128,1000,-1000,305,-355,-1000,-741,-135,-693,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{755,-10,-242,25,1000,1000,-1000,-800,400,-919,1000,-369,-1000,-1000,-1000,1000,361,-1000,-932,-325,-939,1000,383,60,392,-664,-230,831,1000,-105,-1000,555,35,-767,808,-1000,928,-632,695,-393,-68,904,-1000,-249,-916,-92,647,-1000,1000,-665,1000,159,1000,313,-989,1000,-932,532,178,-1000,-92,519,832,-927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{204,-220,799,-372,1000,-106,-574,560,930,-408,-414,-321,-263,-216,856,551,-110,-38,345,-945,543,423,614,774,-182,755,-972,-713,955,-28,15,99,359,101,481,69,-740,-1000,-619,-1000,117,254,-1000,-98,53,213,-96,-191,-954,-1000,973,-238,523,-183,909,-617,-371,683,-749,-561,-748,-179,-193,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{164,169,834,-684,-22,1000,897,-434,-271,227,944,-944,-341,-638,1000,-1000,-759,-1000,69,394,250,717,-288,-810,1000,476,164,-117,-1000,325,-56,462,1000,-695,988,-126,-6,-950,317,1000,-276,314,-1000,459,-216,307,-362,475,1000,-1000,425,-596,151,518,66,19,-762,1000,141,-1000,-261,412,-253,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{-509,1000,-131,-1000,1000,-373,282,660,1000,875,420,-1000,-377,644,684,-294,-1000,-985,476,-731,980,813,544,-393,1000,880,1000,-1000,1000,-28,-278,507,1000,-234,693,1000,-1000,-1000,474,1000,-741,1000,-1000,-244,-1000,-1000,-788,-924,1000,-1000,144,-530,1000,-183,909,1000,-1000,469,-802,-1000,-1000,272,-651,-348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{-434,1000,834,-133,-512,-446,897,431,-76,1000,944,429,926,-1000,571,-1000,-1000,-1000,901,245,-367,1000,-371,-1000,1000,1000,1000,-604,-458,992,369,-92,1000,-897,320,1000,452,-495,1000,1000,-1000,1000,-617,459,-1000,-1000,-300,861,1000,-1000,425,-1000,1000,-349,1000,808,-1000,-587,559,-1000,-78,921,-311,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{302,413,138,-72,853,930,-966,824,929,355,-482,-588,-896,524,-959,633,80,-654,404,26,810,363,-564,-69,763,-334,-70,-100,374,-146,-239,-625,678,-176,746,-339,-36,468,316,436,-65,28,-668,-382,287,802,-499,259,886,-792,-242,339,800,-248,-293,805,-681,374,-256,24,-192,-128,-124,930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{-696,988,114,-399,-245,-446,1000,448,-105,16,1000,450,1000,-1000,1000,-1000,-1000,-1000,1000,433,697,-396,34,-1000,1000,1000,1000,-1000,-330,1000,392,132,1000,-1000,710,1000,163,-530,853,1000,-1000,1000,-789,420,1000,-1000,-434,656,1000,-1000,929,-1000,1000,-316,-694,808,-1000,-752,318,-1000,-88,949,-510,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{626,193,177,-42,382,1000,-51,-654,48,-1000,305,-1000,-905,966,207,-334,-360,-1000,-432,-789,735,-424,-780,165,618,-932,463,404,817,-220,-1000,680,217,15,-32,-298,519,-951,302,-415,-320,787,-755,541,-737,185,199,-708,305,87,-351,-513,616,-865,-777,1000,-1000,1000,921,-903,-616,-390,-143,-552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{-297,-230,648,-670,-426,871,418,1000,357,889,-674,-835,-325,1000,-575,-218,-986,-318,864,93,1000,1000,-1000,-770,1000,-731,534,-828,1000,356,-1000,-680,1000,-1000,-612,756,-553,-5,491,1000,598,533,-1000,61,-143,1000,142,955,1000,-1000,-1000,-1,826,-761,173,960,-757,66,364,241,-531,198,425,-257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{800,-51,-594,-6,-686,-722,-18,-670,-927,518,611,-124,34,-544,1000,-690,555,555,362,554,188,-72,109,236,-1000,-665,-349,330,-1000,134,1000,300,-533,-148,-45,-152,-191,252,-826,-147,1000,61,1000,1000,1000,607,-1000,1000,-327,75,1000,-629,-367,634,-1000,-132,690,258,-139,421,1000,11,-515,-292}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{-437,-1000,882,-911,956,605,-704,387,750,376,534,-1000,189,722,754,-558,834,77,1000,-288,1000,-1000,-743,164,-1000,-612,-21,-1000,811,420,-247,-286,280,-61,822,-124,-513,303,-1000,664,-140,80,685,515,-254,-674,-794,-931,-1000,-1000,236,-54,33,432,-408,783,-592,387,-527,687,-706,-264,-1000,-68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{1000,1000,-451,591,-1000,-152,-1000,-850,-323,-1000,1000,133,-1000,-1000,848,-1000,-604,-580,-1000,-289,-1000,528,433,452,-86,741,-94,123,-1000,-660,638,888,-526,-499,-1000,-1000,1000,739,1000,-1000,760,843,-880,964,469,113,190,-802,-84,-301,1000,-242,1000,1000,-1000,670,-710,758,-627,-1000,885,-207,1000,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{603,-345,731,-30,-174,397,434,923,714,494,-1000,-617,98,1000,-458,-906,1000,-148,913,-404,628,-982,-842,-61,746,-533,-99,-133,-205,197,138,-343,200,22,-152,359,208,-912,528,-157,-258,-18,861,-265,-344,-348,-751,-251,-452,-155,-972,-253,518,377,1000,1000,-1000,121,328,487,-116,74,-553,944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{698,70,-137,508,-125,971,1000,943,961,-304,-812,-353,-1000,825,-1000,-219,-81,-293,-40,-523,-879,758,-353,578,470,-463,-1000,650,343,-464,-420,-299,592,515,-394,-20,550,-693,651,-351,-318,484,-538,-683,307,1000,118,765,192,357,-712,-231,907,-163,668,551,-1000,451,94,-713,-109,300,785,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation):void",
            new int[]{-1000,-826,348,743,-604,214,153,953,-924,-430,29,281,171,55,200,785,-291,766,350,-566,-1000,564,-839,-464,-137,-616,-432,-693,-600,-16,-680,1000,1000,-1000,-8,-22,389,659,372,971,-944,-143,950,773,1000,597,12,628,1000,-310,-1000,122,-736,616,-423,-742,1000,-681,730,147,848,643,644,910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{-126,612,-881,377,-632,-689,1000,488,-1000,686,-1000,-1000,-1000,-979,280,-218,511,-924,-1000,555,-1000,344,-1000,-20,1000,-44,-1000,150,560,-1000,-392,-605,-679,1000,1000,-677,450,859,-111,676,-1000,-1000,-988,-73,-930,747,-604,7,1000,1000,1000,1000,-1000,-75,1000,-1000,-1000,-1000,1000,276,5,48,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{1000,164,-422,574,-1000,-882,1000,777,-309,-118,-849,-975,-1000,-948,79,23,366,-1000,-1000,-254,-331,240,-1000,-39,508,-747,-1000,-510,207,1000,-393,-605,-517,206,788,-391,1000,568,421,1000,-1000,-1000,745,867,-6,-299,169,-162,683,814,1000,943,-496,-754,803,-1000,-532,-1000,1000,-51,-895,-431,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{-930,-111,101,453,589,-656,-344,318,839,-156,4,1000,425,-265,268,1000,-314,-241,804,-1000,726,852,1000,1000,-716,-868,401,180,-1000,-672,-206,-248,-562,-1000,-741,1000,-944,310,945,-1000,1000,936,218,169,1000,269,508,775,-805,-1000,-113,-924,1000,-1000,-1000,615,812,618,-332,1000,178,-965,997,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{-249,-8,79,-228,757,-316,508,30,215,593,-503,-304,897,610,-71,412,183,-768,433,37,196,996,-1000,-417,-119,-692,566,1000,-999,1000,-1000,895,-611,-1000,505,776,-918,708,-1000,-701,560,1000,-734,498,-536,-427,-892,1000,-358,-113,310,327,34,-864,1000,-1000,794,388,431,-526,1000,-709,537,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{558,518,598,107,-10,-1000,461,73,-610,-335,-988,-844,-988,284,381,97,133,0,-1000,226,-1000,279,-1000,444,849,643,-9,653,830,-1000,-1000,-614,29,1000,633,-166,920,-22,286,1000,-1000,-1000,-788,-1000,-804,349,-409,919,1000,610,-214,1000,-764,1000,1000,-505,-858,-247,343,-396,961,669,-799,-922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{-119,1000,-86,-880,1000,-267,272,-672,-1000,496,-199,-1000,960,-788,1000,1000,-303,-1000,-779,1000,-165,871,-1000,1000,1000,-58,-248,1000,-999,173,71,-640,-1000,-926,970,1000,1000,452,-844,547,688,134,-1000,-363,-890,552,-1000,1000,608,773,1,1000,350,63,561,-888,987,1000,600,-564,813,-1000,309,895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{817,-13,257,462,617,-1000,-267,390,284,439,84,-644,496,1000,-512,-778,438,-506,448,-469,-1000,14,-341,758,-346,-154,184,-886,917,436,162,-614,-222,-4,633,-1000,468,-974,1000,-130,-655,-460,-219,-1000,-33,-1000,150,-805,1000,-898,310,-450,169,33,-69,-1000,-130,22,-233,-24,-598,255,-827,-922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{356,-189,592,-137,145,-1000,229,-262,-744,-767,-1000,-299,-928,-1000,821,416,1000,-374,-1000,-1000,-1000,-177,-1000,1000,1000,1000,191,670,1000,-1000,-102,-1000,573,1000,1000,-795,1000,-243,1000,1000,-994,-1000,-974,-475,-828,937,-485,1000,1000,789,-900,1000,-480,1000,948,-544,-375,-423,317,-635,355,621,-1000,-964}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{-1000,635,341,-1000,1000,-11,33,-834,-514,574,-867,-418,254,-348,524,1000,-1000,-262,208,711,26,1000,-1000,-417,-683,38,1000,1000,-1000,97,-1000,1000,-607,-649,806,1000,-602,722,-1000,284,1000,1000,-1000,-473,-792,858,-1000,1000,-445,493,-234,-1000,-103,-141,1000,-407,1000,1000,328,618,1000,-547,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{-393,117,-755,1000,-556,-875,536,328,-85,1000,-375,-245,35,967,-458,-735,511,-782,-1000,-489,-1000,617,755,1000,-229,-44,417,-48,446,-164,-437,470,-744,115,842,-677,-904,396,132,-887,-1000,-68,-576,-73,-1000,-411,-605,-1000,1000,1000,847,-367,-406,-167,601,-1000,-622,-601,88,276,590,164,-694,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{471,-353,-939,-38,-633,299,-482,274,71,990,72,-652,692,942,-91,8,-50,-474,289,-287,-345,999,1000,-188,-455,-814,-301,-231,-954,-1000,-279,872,-1000,-1000,-114,419,-1000,-541,323,-947,864,1000,-287,-177,505,-809,-161,-917,-14,-646,1000,-1000,1000,-1000,-1000,-618,1000,539,-56,105,-986,-514,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{83,943,-303,-612,1000,-168,759,-323,-1000,400,-391,-535,-1000,87,957,1000,49,-135,-1000,882,-59,718,-898,1000,1000,1000,-591,1000,-728,-1000,556,-842,73,747,1000,1000,1000,597,-418,1000,432,-1000,-1000,1000,-388,1000,-776,1000,767,1000,243,1000,659,205,661,301,198,50,1000,-667,-534,-354,-693,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{307,360,-301,748,-1000,-297,-400,525,364,-886,-514,-365,-388,-391,320,263,731,-1000,-943,-829,662,-22,400,-36,-283,-951,-1000,-1000,-274,1000,675,-605,-464,-1000,-52,-780,1000,-482,1000,-348,247,-1000,830,1000,819,-420,1000,-516,178,-296,1000,-41,157,-1000,78,-517,-106,-742,40,-90,-1000,-533,-289,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{-608,56,308,777,667,565,763,611,-434,681,-190,121,-973,-511,700,386,165,40,116,-644,73,144,-971,-994,428,131,777,491,-821,-506,-645,3,-243,246,781,975,407,-600,-563,911,556,44,-704,-825,-567,161,-916,-166,549,564,579,830,129,83,492,-358,64,-498,694,-84,562,-339,873,-494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{72,360,-301,-139,-792,-297,-822,79,-222,-642,101,-266,960,1000,169,94,-36,-1000,505,-287,-290,55,207,-37,-180,-823,-219,-499,-642,1000,675,1000,-662,-926,-128,106,-1000,-482,1000,-1000,317,1000,647,-967,642,-181,-301,-1000,178,-296,972,-638,0,-1000,-805,-1000,987,217,-325,197,-438,-662,-110,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addAnnotation(org.jfree.chart.annotations.XYAnnotation,boolean):void",
            new int[]{408,-163,-276,-37,-761,219,-872,133,394,466,459,-645,1000,1000,-484,-834,-284,-837,388,-473,-1000,429,-927,-346,-571,-201,533,681,-672,-306,-472,659,-607,-964,209,-628,-1000,-914,1000,-1000,458,967,-734,-1000,525,-1000,-693,-127,376,-656,466,-1000,1000,-705,-1000,-1000,1000,-31,-320,1000,-801,-297,-312,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{183,1000,-666,1000,-464,-281,-374,363,569,118,-689,735,-997,-257,444,531,980,376,731,-1000,-330,-286,1000,15,1000,-364,-742,532,494,473,-251,129,-207,-395,231,287,-657,-130,-116,-139,175,27,-411,208,78,351,832,-671,-785,847,-317,363,234,803,785,483,-763,-412,949,-493,881,734,-919,-195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-59,155,-1000,-431,-922,581,657,-659,-503,189,127,1000,-714,340,-1000,-479,239,-1000,430,328,598,473,244,-924,-963,553,852,310,859,-1000,-1000,-512,221,41,257,-1000,0,1000,1000,1000,838,-977,54,-14,328,172,-572,62,774,1000,-1000,969,-1000,808,-1000,38,605,-783,371,-1000,532,194,1000,-524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-976,869,200,-101,781,1000,-516,747,-216,1000,-1000,-1000,335,-61,304,-885,812,128,1000,-890,1000,-748,1000,570,-1000,927,-1000,-791,-1000,-22,-984,-408,-1000,1000,-1000,-1000,330,-873,413,850,-134,-236,1000,-606,607,1000,-215,425,1000,766,-1000,1000,-534,-937,322,986,341,664,1000,-1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-981,-969,514,-220,288,-212,824,-1000,469,-601,390,54,547,-412,122,872,492,618,236,-217,-1000,1000,-381,120,580,-382,1000,-1000,-74,1000,-550,-1000,-71,-604,740,-1000,-1000,1000,-877,163,43,-862,58,839,530,110,233,-92,1000,1000,-398,457,-318,510,-662,1000,153,-1000,82,-501,901,-1000,1000,294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{406,-220,-111,-685,-255,643,829,-169,-603,384,728,350,-917,-139,-673,-425,-112,-976,1000,1000,853,-176,-288,-593,-1000,1,1000,-534,654,-1000,-674,79,-22,517,505,-1000,0,1000,1000,1000,1000,-621,574,327,-92,-106,231,62,774,1000,-578,1000,-974,148,-778,-976,1000,-243,-29,-1000,142,-359,1000,105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{799,410,514,-401,422,-1000,-1000,175,815,270,-562,-64,418,-412,583,872,-215,618,-124,-217,1000,634,1000,588,-174,100,-369,143,-854,1000,1000,752,1000,43,-106,13,-1000,-710,-865,-99,-263,243,1000,446,331,195,233,-510,-514,-362,550,-959,-55,-385,852,868,153,-253,-436,1000,1000,-60,-617,396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-951,-731,1000,-696,-101,146,718,-79,1000,-683,905,-1000,-1000,337,465,459,-1000,1000,278,248,-1000,1000,-285,1000,1000,-1000,277,-1000,-1000,-172,735,-838,-207,548,1000,113,-1000,-804,-1000,-278,524,167,-388,-319,-837,493,1000,-433,-32,972,1000,-1000,1000,-776,-110,634,1000,291,-495,1000,-673,-773,580,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{240,1000,-871,43,-889,-278,199,29,-388,-254,-383,1000,-1000,178,-575,-450,-244,-1000,902,-1000,667,585,1000,-1000,-88,171,-65,604,1000,-490,-1000,-309,894,334,-41,-285,43,1000,839,785,344,-1000,-42,-160,96,13,535,72,457,1000,-705,1000,-1000,832,120,262,-261,-896,509,-1000,1000,587,61,-543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{240,1000,509,176,1000,-206,234,541,-564,790,298,-919,-1000,-1000,504,1000,-594,239,896,-246,-643,585,529,608,-400,-1000,383,-650,237,-790,-32,607,317,-446,365,-432,-1000,-5,-459,-253,-215,553,-206,821,272,1000,1000,-1000,-897,981,158,524,1000,489,-367,-454,444,556,989,-396,-315,-392,-1000,883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-657,-866,-466,92,-291,1000,968,-804,-178,-54,596,-9,-1000,756,-484,-21,-429,165,424,1000,-324,1000,-457,-278,-399,594,1000,-265,-223,400,-658,-1000,-467,-1000,845,-1000,-430,935,95,1000,767,74,-366,642,225,377,-494,-1000,770,1000,-812,453,-19,75,-1000,-81,1000,-656,812,-1000,1000,-370,1000,328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{354,-870,184,-512,-1000,-1000,-899,454,720,-244,-237,575,567,829,-5,-127,-268,-270,-211,459,1000,1000,1000,80,-819,169,-70,530,-210,1000,1000,-506,1000,324,-681,262,-700,693,84,1000,1000,206,670,239,-1000,-95,-865,558,233,-631,-251,-780,-538,-1000,-183,-328,407,-315,-239,7,1000,-322,-527,-220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-1000,-461,118,-184,1000,1000,-931,945,-1000,1000,-782,-883,995,-641,-361,-627,-565,315,1000,-770,1000,-1000,572,405,-1000,1000,-1000,-638,-1000,-694,-1000,-841,-955,1000,-1000,-855,1000,-602,967,1000,-870,-540,1000,-630,-283,782,-60,1000,1000,378,-342,297,-444,-1000,98,407,-534,508,892,-1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,797,84,-767,8,-1000,-440,92,-137,487,303,179,35,-619,-117,460,252,183,-1000,-544,175,395,1000,742,-884,-200,-187,-50,133,-317,959,1000,823,607,425,398,-1000,-94,555,-409,126,1000,177,185,98,366,-122,-1000,-1000,426,-864,1000,-600,179,150,-52,9,-70,-487,-1000,975,316,259,24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-1000,-343,358,-1000,292,996,-1000,569,-1000,-751,717,-308,1000,-825,-592,358,-24,555,577,-603,893,366,476,-1000,726,499,-299,-467,-65,245,-1000,-4,-1000,986,75,-730,-109,-38,402,1000,-267,-728,740,145,-538,-907,-689,225,972,-888,1000,-1000,-209,-617,1000,189,-1000,-363,-798,1000,551,-942,126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{19,-932,-347,733,91,252,-336,-635,-989,172,-981,250,340,-207,1000,-105,132,270,1000,-54,-643,-152,-1000,-105,-884,383,415,-1000,-598,-356,-515,-720,-261,-1000,647,1000,79,303,-772,-182,-1000,-358,-327,495,-127,1000,1000,-906,-691,1000,-685,-492,1000,747,-896,904,-938,-3,1000,495,143,43,-122,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-265,-1000,-468,609,889,343,371,-703,-470,-1000,-397,388,-617,522,325,-28,391,-322,1000,920,-398,-330,-697,-1000,-716,1000,936,-444,-869,209,-1000,-424,-1000,-1000,910,-1000,-26,390,-623,248,-222,-1000,-193,-86,665,325,7,-988,482,911,-1000,45,517,1000,-1000,1000,-143,-1000,-144,-1000,-381,2,913,348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{1000,576,84,-863,-616,433,-897,-1000,-746,-1000,-847,-1000,569,1000,689,959,417,307,-1000,941,-1000,-858,-706,1000,-1000,-1000,1000,-513,846,1000,1000,-1000,474,1000,-395,820,-1000,1000,-196,1000,-431,296,-1000,784,-221,-238,-242,-206,233,77,571,-239,-479,-770,1000,1000,993,-164,-1000,940,259,741,-81,-803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{650,-1000,-472,1000,44,1000,-25,-1000,507,-1000,1000,-1000,-563,1000,1000,60,1000,-1000,-1000,1000,-1000,-34,-1000,-166,-1000,-1000,-417,-1000,1000,1000,1000,-916,-80,1000,-1000,1000,-242,1000,921,-155,-1000,-228,-1000,829,-83,627,-1000,-1000,-1000,1000,708,351,-1000,-1000,1000,1000,662,-1000,-1000,1000,-1000,50,-142,457}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{905,-984,624,162,-509,-753,584,635,-210,402,235,570,151,-447,-445,197,-445,963,470,257,737,-919,448,194,-867,932,-580,594,-191,-213,-193,-139,-372,720,-559,-163,-510,-16,-1000,0,-272,-665,886,-64,-159,-439,446,584,-235,-883,-576,232,379,-147,-531,-205,398,-114,606,-909,872,384,-423,-322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{1000,-76,209,12,283,247,290,-120,-493,400,-743,-190,651,743,-271,193,-344,331,-11,-116,-200,-1000,-71,739,-1000,400,340,99,80,-1000,-158,211,-669,896,-716,555,-571,-400,797,835,409,1000,-300,-400,-565,-282,649,-870,999,-230,-67,960,-991,-44,-400,-444,1000,-415,-495,958,63,188,-891,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-391,-1000,-16,85,201,-30,1000,924,-210,121,-41,739,1000,-990,437,-318,-698,852,313,-39,737,1000,394,-39,-1000,330,-275,1000,-191,-1000,68,-139,-273,-75,-1000,-833,88,-856,-1000,676,434,-1000,-474,709,-403,839,750,-400,1000,514,-1000,259,698,-891,-963,145,398,850,1000,-589,-494,541,-217,-465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{734,-723,-319,307,-509,848,-446,-1000,-246,-1000,1000,-972,-1000,-742,1000,1000,1000,-1000,-1000,1000,-1000,-1000,-869,36,-977,-978,-647,-506,1000,1000,848,-1000,-980,213,-601,893,-39,917,660,1000,365,-636,-1000,-510,204,540,-1000,-299,42,1000,96,777,-511,-1000,555,922,-291,-1000,-354,851,-1000,695,-532,-399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{862,1000,784,-972,-1000,-611,-1000,-270,-811,-800,-1000,234,119,-729,63,955,-184,885,-1000,-96,221,-1000,-101,1000,-236,261,965,-473,-1000,-400,439,-323,726,-142,92,-1000,-1000,90,-128,572,1000,1000,76,896,-772,250,1000,514,490,-855,784,770,1000,-106,263,-284,-276,644,-167,-152,1000,836,-952,-791}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-391,-1000,-16,-254,-1000,-30,422,168,221,-1000,689,-254,-1000,-294,1000,506,1000,-616,-413,-302,1000,308,394,1000,343,-753,-670,1000,-772,-769,273,-308,976,-637,1000,-728,259,-133,-650,309,107,-1000,-474,1000,-686,46,158,103,-923,514,-676,-341,1000,-891,634,-177,-1000,427,229,617,23,541,111,-137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-1000,-1000,-386,-782,-914,1000,90,948,1000,-565,1000,-1000,-936,-26,700,-131,879,-486,294,1000,1000,-1000,-502,-1000,-409,-1000,-415,213,42,-2,180,-308,-72,542,-163,522,820,1000,-156,-1000,94,-322,-327,1000,-999,-1000,276,-1000,-1000,1000,-71,-865,-535,-951,1000,34,79,-749,993,936,-713,-122,-40,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-1000,-1000,-36,78,608,1000,189,194,-373,126,398,-494,1000,-407,901,-253,150,-45,38,761,-458,-803,-1000,-470,-993,-398,724,966,1000,-513,242,-654,-273,570,-472,-250,-220,-28,-1000,374,-247,-375,-1000,675,-115,981,112,-706,1000,551,-172,720,-338,-1000,-1000,-747,1000,362,-903,192,-463,216,-449,-354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-235,877,546,111,701,728,-607,275,-786,1000,-699,-237,1000,4,-250,-983,403,728,649,-889,-445,-1000,54,653,-51,-541,443,442,-772,-611,-65,265,92,969,-471,491,-239,-257,-964,362,-456,-1000,-376,-725,-140,558,-14,-569,1000,-418,-486,651,-1000,-929,-453,-356,813,327,28,-1000,-274,-347,-445,-211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-844,-607,274,-675,-623,198,693,412,664,768,912,-556,-952,273,814,-32,322,-570,453,207,368,-621,-487,-917,-135,-326,78,896,75,-52,-257,-139,-988,157,-135,-527,732,5,-709,-254,673,-861,-655,-220,-926,4,984,-168,279,886,99,79,478,-415,-265,592,-150,399,624,730,-599,689,-84,66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{555,217,271,210,-571,684,861,157,752,-218,-421,301,1000,276,769,858,-67,791,123,1000,-134,-1000,197,-1000,-1000,1000,-326,1000,-171,-273,38,-1000,69,1000,-765,-384,-844,696,-594,-1000,443,43,579,248,-542,-59,174,24,490,-883,-191,806,360,-851,-566,-758,498,173,454,-426,550,522,-939,-280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-1000,-1000,-131,-140,497,334,1000,1000,761,425,-130,-1000,422,-1000,1000,-496,-315,824,931,-39,1000,1000,-1000,-1000,-748,563,-1000,1000,-382,-1000,-273,79,-419,-230,-66,-858,806,-1000,-1000,23,264,-1000,-98,764,-632,484,580,-686,137,941,-1000,-528,898,-886,-1000,-330,-355,854,1000,-752,-976,227,81,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-785,-394,79,-828,-571,1000,-13,494,506,-565,669,-1000,-541,-578,769,746,879,168,-117,1000,854,-131,-486,-1000,-640,-135,-819,236,-425,58,423,-11,64,156,334,281,45,603,-615,-677,89,641,-159,1000,-1000,-1000,262,-842,-998,-43,-80,-487,508,-207,805,-393,-748,-749,582,582,311,297,-574,577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-535,1000,894,-441,458,479,-965,576,70,-507,-1000,-659,109,-943,1000,-424,1000,236,-817,-53,476,1000,-1000,1000,9,-474,101,-161,592,209,665,-45,872,247,-1000,-524,-675,-294,614,256,678,190,-458,575,-760,-177,-205,-616,-1000,-739,359,-24,1000,164,1000,-585,-326,-315,-1000,483,1000,146,-1000,2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{250,1000,238,127,-75,1000,827,1000,-946,513,223,1000,162,-609,-804,-114,1000,385,1000,-823,-482,-78,-824,1000,858,-851,1000,-76,534,465,459,1000,711,-376,-143,-549,-410,-217,-391,-1,-322,893,965,680,2,217,537,1000,431,1000,-839,-326,-1000,-1000,-741,-302,398,707,-118,-672,397,-1000,-349,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{7,20,319,274,-868,1000,-1000,717,-1000,211,-119,805,-381,711,-916,91,-729,-707,-1000,-341,-1000,638,-582,-1000,667,881,812,1000,-317,768,-1000,-600,295,-1000,88,1000,-515,-1000,-1000,-1000,28,36,-42,-1000,57,-1000,-800,476,-1000,1000,1000,-466,1000,840,430,116,1000,429,399,-914,1000,306,-994,767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{987,159,1000,-456,259,377,-313,-398,-22,540,1000,243,152,-614,456,-254,1000,180,1000,-262,-240,37,1000,407,604,-1000,-364,-341,-214,10,512,770,119,-248,1000,53,-862,1000,-329,-386,-355,-718,-493,1000,148,878,111,634,484,422,10,1000,-20,-995,-861,-1000,-477,-244,129,634,225,-1000,-1000,-890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-307,1000,-191,533,-1000,-690,1000,-433,-47,587,922,910,-1000,-986,-286,105,-196,1000,1000,559,1000,848,-1000,1000,-836,352,1000,208,-210,219,366,103,1000,278,-957,-720,740,491,-752,681,748,1000,442,1000,113,-223,1000,145,315,1000,-1000,-1000,400,-481,-1000,-947,1000,499,1000,-1000,-1000,-633,790,-461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-471,59,-238,-1000,-133,539,-226,289,-372,-1000,-231,829,-34,32,-1000,-197,1000,449,845,-1000,-13,-1000,960,597,653,-1000,-256,-215,-1000,-152,-1000,-1000,105,-1000,1000,-97,-1000,-1000,-884,-690,222,-1000,1000,235,-1000,170,971,884,-1000,1000,548,-1000,-1000,849,-28,-1000,-393,-1000,-271,-179,-652,352,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-550,398,-901,139,-52,925,190,-512,598,31,1000,1000,-457,-1000,-1000,-753,-493,349,484,-542,1000,303,-117,-746,-686,242,52,892,-334,162,181,-396,505,-849,-346,1000,-405,-1000,-932,-571,1000,525,152,126,-1000,-212,244,86,-223,1000,951,383,-596,358,-476,-311,1000,587,392,313,-41,1000,-516,880}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{1000,-835,1000,-645,-186,-396,-775,-528,464,1000,1000,-744,-825,-1000,-15,-705,-671,290,736,1000,957,834,1000,134,-670,-411,-1000,318,403,234,-166,-754,776,-345,528,632,-432,1000,-784,-222,1000,-1000,416,939,-156,1000,157,399,64,-24,994,1000,1000,258,-703,505,316,-897,606,1000,1000,1000,-1000,782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{184,398,508,-645,-217,909,-619,984,-514,4,1000,-219,40,194,360,887,-393,349,133,1000,-830,-64,1000,-699,528,-647,-1000,360,-263,200,-883,-429,-985,-1000,1000,1000,-846,-56,-1000,-1000,-388,-396,381,62,-518,-241,244,1000,-197,1000,1000,955,482,130,-20,-183,443,278,-172,-8,1000,1000,-937,782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{508,1000,-726,162,12,-487,190,263,-762,-1000,-386,-479,-515,794,1000,1000,-1000,948,188,844,-758,60,-190,-410,-501,254,-366,379,-218,-213,-1000,-713,-981,-782,-580,-588,531,-262,-737,-1000,-446,-923,702,-482,267,-1000,898,-943,-902,1000,249,-419,1000,1000,124,-1000,445,101,877,-1000,465,786,1000,478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-1000,198,458,55,-508,-160,340,-731,235,547,1000,826,49,-1000,-710,-1000,-42,231,254,-745,-299,-306,303,-90,-193,-162,-397,-197,-215,-253,827,386,365,-102,535,883,-629,-1000,-611,-668,885,197,233,1000,-832,-770,523,-35,-968,598,317,536,-352,69,-607,490,939,-169,-276,635,1000,-1000,-1000,988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{140,334,-381,-700,111,47,47,-169,-437,-232,-548,437,-30,-313,158,-218,-396,831,230,-669,-89,-116,348,-422,-219,-343,-400,160,-1000,-387,36,-400,-284,-743,604,574,-359,-400,-709,-601,571,-569,517,470,123,107,477,239,-382,636,592,363,-312,400,-381,-1000,280,-315,5,321,400,-588,-7,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{551,1000,434,628,188,459,36,761,-1000,1000,1000,522,-710,-615,456,-254,-36,889,408,938,1000,1000,330,297,-846,34,407,1000,643,558,221,306,1000,-784,-545,669,-119,1000,-1000,-290,1000,609,79,537,-84,364,157,-1000,-697,1000,525,410,1000,495,-1000,313,1000,334,-443,755,142,336,-652,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{283,197,484,-1000,-786,284,98,-522,-151,938,1000,1000,-276,-426,-632,-669,578,647,883,-23,21,-114,370,-143,-1000,-1000,-853,1000,-238,699,268,-1000,694,-1000,1000,992,-1000,1000,-23,-650,994,-1000,1000,404,-1000,1000,238,1000,-236,1000,1000,-412,-1000,680,-79,-50,1000,-1000,-397,879,-316,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{224,-287,294,-1000,418,1000,-1000,656,269,-1000,-503,677,113,-164,-823,78,656,-947,179,-972,-1000,-1000,714,-586,1000,-944,-1000,82,-185,-31,-952,-717,-798,-514,649,120,-1000,-699,-384,-676,-388,-945,68,-180,-653,119,4,296,-1000,326,692,326,-1000,-494,322,-502,-1000,-602,-754,543,397,22,502,866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{1000,159,696,155,126,259,-828,-398,422,553,1000,41,-117,-284,1000,526,23,99,317,378,-210,321,1000,-57,704,-491,-1000,-222,108,-86,-18,770,-652,134,937,-162,-175,975,-329,-491,-829,-642,-1000,420,705,-258,-34,297,379,422,-719,1000,1000,-596,-667,-947,-292,-39,635,559,-238,-792,-792,-890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-664,1000,372,344,130,-650,790,-566,213,-433,381,497,-339,-430,-295,-569,247,-176,803,233,-1000,378,757,-1000,785,471,-609,-381,133,-587,-417,-318,296,1000,246,573,-340,-61,-216,118,1000,42,-732,486,736,-1000,115,-497,-458,872,495,-465,441,-53,-20,-20,841,632,-576,-417,1000,-229,-664,-3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-623,694,-228,1000,890,-1000,-192,-921,678,-952,389,-1000,298,1000,535,-669,609,833,10,-400,397,-697,1000,570,337,78,-938,-1000,439,-1000,1000,903,531,971,-42,-25,1000,-931,1000,-1000,107,1000,117,-905,1000,-1000,668,-1000,-1000,-1000,518,339,1000,-902,1000,621,-835,377,-1000,686,-1000,-710,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{168,1000,253,515,-846,-1000,-759,816,-133,-1000,1000,25,1000,-1000,-1000,301,-1000,388,-788,1000,1000,-1000,-1000,819,-543,-602,341,-1000,1000,-1000,-95,-325,40,-215,-667,7,1000,-217,537,-1000,1000,357,83,797,-1000,1000,1000,622,726,-407,443,799,1000,-1000,951,-821,765,161,1000,582,-1000,1000,280,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-513,195,-405,741,139,-548,-78,547,743,-461,326,547,70,228,402,-793,-882,-857,4,-97,742,-682,435,-982,3,565,-264,859,821,-584,847,-43,96,-291,-441,308,-457,-819,-215,-734,244,859,934,946,-900,-551,-67,240,235,505,812,-142,-240,-332,-361,-861,-771,966,102,848,-338,835,-957,599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-305,-490,259,-2,197,228,-15,559,-87,929,-623,-1000,-1000,-309,58,-625,732,-168,498,819,3,-485,1000,783,-536,258,3,-931,-206,642,369,148,288,-253,-754,-1000,-800,-456,228,329,199,354,1000,-199,151,249,623,179,-1000,329,-848,-1000,-726,159,-343,221,-379,1000,60,107,-498,-93,-473,44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{519,-555,1000,1000,794,1000,1000,266,-120,1000,-433,784,910,1000,-69,-1,1000,610,313,-594,-431,-266,268,484,-638,53,-493,-398,-1000,1000,51,-1000,426,363,-950,1000,-1000,1000,-1000,688,-328,388,569,-787,-915,-406,-1000,-796,1000,1000,-1000,1000,1000,1000,-1000,1000,1000,-74,41,-162,-711,-714,566,-755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{366,-271,-478,-1000,-187,115,-591,-674,-869,314,74,-1000,-1000,-882,6,535,1000,783,232,498,-424,1000,-821,987,-374,-452,465,-901,-571,247,-1000,748,-385,912,263,-1000,-225,1000,-655,354,-1000,-905,-265,117,573,46,462,55,-1000,-170,-783,-672,-457,261,959,60,-91,-298,377,-478,-130,-562,-54,-510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-1000,657,-890,-276,627,739,699,174,1000,-10,-961,-836,650,-272,-905,1000,510,-12,-1000,941,819,-921,-358,-804,-482,-551,1000,-738,-948,899,1000,1000,-686,-1000,247,-1000,-642,-648,937,1000,662,-402,-731,558,382,674,668,1000,1000,-1000,-1000,23,1000,-1000,1000,-759,-965,612,1000,968,590,467,-56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-400,234,35,1000,1000,-26,-512,109,-1000,-689,1000,118,-33,153,-664,-1000,-742,404,1000,146,30,-1000,-307,616,1000,-1000,400,-332,196,280,-653,989,173,-372,1000,-544,-100,-76,-1000,324,-49,1000,102,-517,-1000,-1000,775,-447,-357,-414,198,635,920,-1000,905,-815,-490,310,-58,237,611,619,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{890,812,-561,540,197,224,1000,-525,356,-4,-424,412,1000,1000,464,357,-248,-370,-304,819,-881,331,1000,783,382,269,3,-140,123,887,601,-671,-714,465,-485,328,-523,123,-868,-599,-628,-541,303,555,-485,620,-485,-452,264,-710,218,626,884,748,-360,773,1000,1000,999,316,-1000,-854,365,56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-848,-1000,644,-747,661,1000,87,197,-509,1000,297,-711,-1000,-411,349,466,569,1000,822,-587,172,712,190,352,207,-645,363,-938,-1000,397,-358,853,1000,485,404,-261,-199,1000,-1000,1000,-787,440,0,-484,139,1000,98,625,-436,400,-1000,662,206,999,-1000,1000,16,-1000,746,-488,1000,-929,86,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-612,847,1000,1000,558,-49,1000,-651,-741,-208,552,658,237,-493,-835,1000,400,552,808,-111,-523,-424,-705,-1000,680,-109,553,-1000,-762,-80,-881,-629,665,1000,664,1000,847,1000,423,845,-236,343,826,-344,-797,1000,6,376,-814,-919,-627,-183,47,-426,-400,370,45,-599,77,-846,-535,-897,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-758,77,-963,-327,-323,-1000,-796,-464,-50,-994,9,700,-587,-1000,-1000,115,-1000,541,177,1000,263,-283,-1000,391,465,-385,654,-843,-1000,-278,-866,1000,-116,505,800,-1000,820,333,-369,-610,-1000,-568,-64,527,-94,-1000,265,616,-507,280,991,-289,-979,117,1000,-1000,-1000,669,-175,1000,-220,1000,-892,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{374,1000,383,55,-995,207,-217,122,274,-345,-41,1000,1000,492,-358,-54,275,-39,-943,-982,878,220,338,1000,-348,186,139,146,-125,1000,238,362,714,-353,1000,-98,271,498,901,421,-8,-235,-52,929,171,1000,281,851,799,-92,-47,710,291,114,-658,847,729,-1000,1000,-699,975,593,1000,452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-540,395,1000,-1000,714,458,704,-1000,-654,-249,-871,-257,580,728,-466,793,238,506,-1000,-713,183,-486,-1000,119,412,-642,295,-1000,-1000,-120,159,118,562,986,-190,781,1000,431,866,670,696,-1000,0,-264,-280,-584,-196,956,-661,-161,-627,766,1000,880,-187,329,-1000,-768,242,-924,-454,898,-807,-885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{323,736,-566,691,166,-561,130,-884,1000,-263,-735,588,740,1000,900,-746,1000,-1000,27,-375,-797,1000,1000,-438,148,1000,-316,985,909,500,946,-900,-578,962,-1000,530,-971,-1000,-1000,75,-449,327,627,951,606,510,-90,-16,757,-228,1000,672,102,-288,-418,-168,911,1000,470,994,-1000,-854,-717,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addDomainMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-922,224,-336,-684,188,-947,264,-727,-115,245,1000,143,511,6,428,-1000,182,-857,483,587,333,302,987,448,-481,145,-1000,-273,247,-1000,516,827,-223,263,517,831,1000,130,358,-1000,365,874,1000,-1000,700,133,1000,-218,-428,-462,654,-457,85,288,-250,-245,57,-1000,-136,610,1000,-54,-510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{726,578,389,-192,652,1000,214,241,-27,49,594,546,785,-583,299,-81,-752,-608,7,1000,368,-443,204,894,-1000,273,-178,449,862,-734,768,-629,-17,451,498,751,539,568,-946,162,405,1000,222,8,245,-426,137,-410,753,450,611,-475,-28,-974,658,-459,679,-909,709,665,935,-716,-350,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,1000,-151,17,-1000,354,943,1000,-412,631,-1000,1000,-27,-583,-399,1000,-1000,-608,1000,672,760,1000,-625,894,1000,273,457,-1000,185,-1000,565,-629,1000,-1000,-1000,751,539,734,-946,-1000,-643,-128,1000,461,1000,42,-1000,1000,753,450,-1000,-850,-1000,1000,693,1000,-108,-909,-124,-425,955,-1000,-1000,495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-933,-105,265,-252,-156,-205,713,777,-266,812,32,-243,-610,-359,-385,1000,-620,-220,551,-370,1000,1000,-507,768,203,809,-976,-1000,-409,832,-829,846,82,-731,513,-562,198,-598,384,-819,-571,128,901,1000,187,-578,-1000,509,-32,-547,-861,-722,-130,1000,-246,889,-797,-257,-974,-417,794,332,-218,915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,1000,-127,205,-1000,125,1000,2,-761,482,580,927,345,-1000,-1000,1000,-53,-1000,1000,861,1000,1000,693,994,-491,1000,-1000,-1000,-643,-32,-1000,1000,-259,-1000,531,-718,1000,-1000,232,-1000,490,741,-1000,1000,-496,-1000,-1000,1000,-363,-486,-861,-724,-654,1000,1000,509,-1000,1000,-1000,354,794,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{289,548,-526,196,-448,-916,587,-456,-964,34,-621,-526,504,827,-524,710,-960,-129,-32,-757,177,-871,-276,93,723,458,625,757,-309,-806,42,624,-826,-241,723,604,-263,-782,435,34,985,-320,113,-503,-710,-857,108,637,-615,-979,-845,-739,509,922,126,61,-926,-520,324,155,490,20,99,295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{126,1000,804,-721,-1000,-1000,1000,389,758,970,460,927,693,-1000,-1000,1000,1000,-960,931,-400,1000,1000,-324,1000,327,1000,-295,-300,892,-1000,-1000,1000,475,-1000,-1000,-1000,804,-963,-1000,-1000,-37,194,-541,1000,-1000,-1000,-1000,1000,-511,24,-1000,894,-1000,1000,1000,-122,-1000,1000,-1000,507,1000,-926,1000,815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{425,8,203,882,-1000,761,1000,-853,-1000,-3,1000,-211,-321,165,140,959,1000,-1000,1000,-1000,1000,739,870,940,690,-860,809,-935,-1000,-410,-1000,379,-1000,-1000,1000,605,1000,-1000,759,-1000,497,-214,-408,841,57,-77,656,-55,-1000,-933,356,-1000,265,1000,-1000,1000,339,829,1000,41,1000,-262,617,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-538,-1000,499,-1000,1000,409,64,461,186,1000,1000,440,-204,-1000,-379,1000,-328,-568,-369,210,1000,670,-321,1000,444,-7,567,-978,1000,1000,-1000,1000,605,-1000,-748,296,590,-800,285,180,-797,1000,-186,651,-1000,-1000,-1000,-873,275,-420,-742,-284,638,-1000,-1000,-66,-333,-460,-753,217,1000,437,304,-499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-523,776,-457,-831,-157,-410,220,647,901,728,-725,492,-290,-445,-429,76,-994,-152,-531,500,299,999,-646,694,93,15,-769,-639,822,-201,410,517,570,-570,-806,-650,-612,377,-398,922,-957,453,-373,828,-557,-855,-686,352,645,-537,-979,884,334,896,627,-593,-996,-791,-819,717,654,-139,-533,684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,1000,114,-1000,195,-1000,-270,1000,1000,-9,-753,1000,-1000,-1000,-1000,-492,731,798,-1000,1000,-1000,-147,-1000,-1000,-951,482,-1000,800,985,354,863,-30,264,1000,-1000,-1000,-1000,374,-1000,194,-1000,1000,-294,-200,176,-979,-1000,653,1000,-668,-1000,197,487,-56,1000,-728,-1000,-1000,-929,686,-270,-1000,-1000,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,1000,-431,-56,-1000,625,433,-699,-85,-61,1000,1000,422,-320,-813,1000,-821,-630,1000,745,1000,1000,392,1000,1000,-185,44,-1000,-1000,-1000,-1000,-352,1000,-1000,-403,676,1000,465,759,-416,-74,-692,101,1000,1000,-102,-353,482,708,-255,356,496,-431,1000,856,641,339,570,-1000,-928,1000,-1000,589,781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{944,1000,289,-30,-531,714,745,-1000,-128,-527,1000,307,614,-1000,180,1000,906,-1000,798,614,1000,692,734,846,-445,-675,-221,229,343,-316,-1000,719,-773,-514,1000,-797,1000,-1000,213,-1000,1000,611,-856,1000,-1000,-1000,939,802,-1000,-550,447,46,292,-642,1000,-163,316,1000,93,1000,1000,-522,1000,359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-838,1000,613,-538,554,1000,1000,-1000,-128,614,1000,1000,1000,-1000,-659,1000,-1000,-1000,798,1000,1000,747,1000,1000,-1000,-675,-1000,-466,312,-236,-890,219,266,-739,759,-444,947,-650,646,-968,900,721,-856,1000,-593,-1000,-1000,285,-1000,-539,267,-546,292,-257,923,-718,-1000,1000,-1000,1000,749,-1000,976,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{666,-801,490,-253,-36,-1000,69,-456,-423,-36,779,-862,-854,-20,232,-101,1000,-137,-57,-1000,217,-710,-949,-1000,-1000,458,1000,1000,-309,1000,42,559,12,-241,-353,-1000,-802,-1000,410,-241,-347,341,43,-1000,-12,-1000,1000,637,-198,-1000,-436,-1000,-446,1000,-1000,61,997,-445,1000,50,490,179,99,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{716,127,-241,-612,-441,-325,261,-1000,-192,650,418,-276,-33,-799,70,22,84,113,-118,-680,134,-56,-157,1000,-199,185,713,450,148,416,-55,190,-741,-83,-525,-376,-231,-105,-489,-391,28,-362,434,-644,-151,48,551,742,-296,-193,-635,-831,-109,356,-378,272,50,-255,649,318,-56,-86,-777,69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{107,167,-579,432,-845,-182,456,231,-471,872,973,-507,-4,431,1000,642,772,-869,60,-1000,1000,734,210,-21,744,-364,1000,394,616,1000,3,481,237,-1000,131,-356,-40,-1000,845,432,581,-891,76,229,-789,-354,759,286,-981,-1000,-755,-1000,842,1000,-1000,388,1000,263,872,-478,1000,735,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-498,441,-248,922,-374,837,-296,1000,889,-1000,175,-1000,108,-777,331,1000,1000,871,43,-59,234,364,-675,1000,93,-418,-1000,815,-558,-361,1000,196,1000,-1000,-285,-1000,-838,-382,-850,-250,1000,487,-863,801,314,-604,-487,-946,-529,1000,-742,-1000,-1000,620,833,1000,-155,-1000,414,-973,-557,487,273,945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-774,122,353,-677,323,-1000,-8,41,1000,608,-931,-129,-641,-1000,-312,833,710,-1000,-1000,-1000,1000,838,128,1000,958,933,670,364,-1000,-1000,378,-78,-878,487,-332,-479,-312,468,371,780,-864,-120,-1000,722,-724,-1000,-298,307,-797,-1000,71,-517,-1000,1000,-121,-338,610,-523,501,558,-495,1000,-367,-13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{1000,122,-176,-538,-468,288,442,324,460,-168,173,787,-176,-110,854,211,-69,329,565,-1000,686,68,-398,-383,-882,206,-68,-804,764,-317,-241,-806,-5,739,-434,554,37,250,-288,-678,762,-2,1000,1000,177,-585,-942,-629,590,130,-917,-379,-1000,53,214,1000,-468,-426,312,-1000,-756,1000,-143,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{208,-67,1000,-124,654,-557,-48,-422,1000,-526,339,387,1000,-80,688,934,911,-547,760,320,-211,-123,-282,188,-504,703,-670,-515,894,544,-438,-1000,-663,-19,-380,1000,1000,-1000,308,351,529,72,-493,1000,1000,-277,-19,-532,-386,-651,-267,-45,-912,1000,336,827,-490,-602,819,29,510,-1000,801,-152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{15,353,-948,-306,1000,-3,539,1000,689,-938,375,847,1000,-105,1000,966,1000,-243,1000,869,838,957,-673,120,-1000,-144,-285,381,1000,-590,-646,-1000,-1000,1000,-967,-1000,719,-221,-98,-728,454,495,-1000,1000,1000,-407,-1000,-1000,-355,1000,-1000,41,145,1000,564,728,-965,-686,1000,1000,-279,-1000,1000,-258}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-371,-770,949,1000,-102,1000,-69,1000,177,-651,677,-294,-397,234,-189,698,1000,-56,1000,-970,118,638,-461,-161,-291,992,-954,-1000,1000,482,-302,692,-684,562,-26,-461,-117,-1000,615,-1000,447,-354,-235,528,400,-729,-1000,89,964,903,354,-144,-649,-174,-994,407,-290,-407,-169,735,-555,331,-2,525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{559,-501,-805,-892,639,104,118,1000,763,-840,1000,-222,153,78,840,184,758,-119,774,1000,341,-4,-1000,201,-502,568,-826,97,781,-403,-655,-1000,-71,-678,-1000,-555,414,-729,-47,-594,32,-1,-1000,1000,380,-454,-872,-318,-144,-60,-557,56,-697,1000,694,753,-1000,-82,1000,132,131,-1000,1000,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{292,-488,-456,-585,-212,379,-256,909,707,837,965,-29,-1000,210,370,448,663,337,998,660,653,221,-955,365,-1000,-306,-743,-192,389,-306,-508,-1000,1000,-55,-882,261,296,-567,20,-696,-594,62,-1000,757,938,138,-894,-731,177,302,-613,92,-371,1000,866,849,-998,-175,1000,268,174,1000,926,791}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{405,1000,-306,482,1000,305,539,880,1000,848,-193,1000,-134,-1,1000,1000,1000,1000,1000,-245,1000,892,-866,-1000,-1000,-976,485,62,1000,620,-646,-1000,-1000,1000,-644,176,-535,-1000,680,-1000,1000,1000,1000,1000,1000,-566,-1000,-1000,-97,-727,-1000,918,145,894,-481,1000,-455,-1000,1000,1000,-251,-1000,-99,-889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-257,595,-501,637,1000,499,538,-666,689,-1000,-133,481,737,-103,331,990,1000,-155,1000,509,508,1000,-499,202,-478,-400,-205,-361,1000,126,-150,-81,-394,-299,-967,-1000,197,-110,-143,-921,1000,849,400,1000,1000,-480,-597,-946,-796,1000,-695,-304,-596,216,396,728,-339,-953,213,1000,70,-383,273,-468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{341,1000,439,-157,638,-263,136,393,474,-393,-90,655,29,686,618,-232,1000,1000,270,-422,1000,536,-1000,-1000,-426,-730,551,81,1000,734,-1000,81,-1000,1000,-149,867,59,113,808,-351,243,723,1000,186,560,315,177,-71,76,-809,-154,807,515,-251,-244,358,-625,-6,1000,1000,-144,-826,-443,-585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-1000,-552,479,91,-1000,80,-312,-166,845,-456,-178,-509,440,-1000,660,82,422,-278,-1000,-777,289,308,-433,908,20,276,-198,-208,-870,-1000,1000,-642,-1000,-1000,-720,20,-149,786,-982,-893,261,-36,-896,-513,-798,-850,-642,-730,-101,579,-250,-1000,-819,417,-398,83,-324,-906,513,-1000,-756,-135,314,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-1000,-120,-446,549,-1000,-109,-747,-1000,1000,507,-643,-1000,340,-1000,-135,-71,280,1000,-1000,-1000,-10,-672,106,1000,96,-223,-1000,189,-1000,-990,1000,-283,1000,-1000,-21,43,-587,-377,-1000,-896,186,256,-1000,-1000,-1000,-981,-239,-17,-200,1000,570,-1000,-174,321,625,-1000,-321,-6,267,-1000,551,-209,-1000,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{286,1000,-707,492,1000,266,884,1000,433,-788,-450,1000,1000,60,712,966,1000,-115,1000,1000,691,1000,-1000,-184,-967,58,249,940,1000,-86,-707,-633,-1000,-1000,-934,-1000,935,255,236,-1000,80,1000,-1000,1000,1000,-863,-1000,-496,-712,-259,-805,200,187,1000,10,456,-599,-905,754,1000,264,-1000,330,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{-88,930,269,-503,409,-678,651,-285,454,-783,661,1000,-119,-1000,533,-222,-74,1000,-580,862,656,300,-727,-1000,-949,-410,499,-204,1000,-310,-656,541,-578,-59,-1000,431,1000,-90,583,-371,-1000,848,452,-798,822,-16,-122,23,258,-854,-27,468,33,728,440,929,-1000,29,-168,1000,868,-1000,850,-895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(int,org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer,boolean):void",
            new int[]{1000,-889,201,-677,21,-442,-8,-666,956,-768,1000,205,82,935,552,1000,488,1000,1000,-309,1000,236,-340,-974,92,-1000,-562,1000,420,941,-1000,-859,1000,235,-600,454,-29,-1000,158,93,-206,1000,1000,1000,737,1000,-509,-295,495,18,-966,-517,-1000,-1000,880,1000,-797,-523,278,400,-1000,357,427,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{221,738,554,868,-1000,537,-621,-456,1000,278,-1000,-297,-1000,48,713,-349,329,621,-1000,-326,-644,-806,-522,-823,-806,-83,1000,720,-1000,-358,-846,-570,1000,-25,-720,-189,-684,508,679,1000,-786,-418,173,-1000,-509,-272,135,-303,-1000,1000,460,570,221,-960,-943,1000,-1000,345,1000,201,513,1000,487,-391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{639,39,858,-994,-1000,-281,874,619,233,197,236,-76,-438,1000,487,-444,746,-616,178,389,-803,-212,-671,1000,-106,-91,505,274,827,394,-779,116,1000,-106,678,613,709,63,1000,656,-635,760,-495,510,-567,-327,802,-695,-159,1000,1000,590,-316,-1000,-856,-356,123,-117,275,-680,738,-374,-278,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-48,-51,-344,1000,806,309,275,-1000,-756,-994,1000,-208,277,171,-807,-459,-256,-895,1000,628,1000,-429,-418,-718,1000,-399,-1000,-748,405,464,-25,1000,-905,-313,221,-1000,1000,166,-132,-684,-434,1000,-215,519,-287,-84,798,299,519,-505,-133,-139,763,541,-583,61,1000,-280,-609,-556,539,-499,-739,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{1000,-1000,959,181,-373,-741,330,696,-1000,-760,379,-1000,-837,-1000,-801,-1000,-851,-451,1000,-363,869,-168,183,568,1000,1000,-705,-1000,1000,-403,1000,1000,-376,-1000,363,825,1000,-810,142,-725,-699,1000,-672,989,-897,955,1000,1000,-248,-217,1000,1000,559,-84,-888,-636,818,-104,-1000,-374,-1000,-952,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-513,-306,-26,955,-105,-16,225,-814,-379,-461,465,-479,-324,108,-902,-636,-306,-596,659,555,611,-475,-418,-590,525,-100,-349,-97,279,464,6,743,-648,-275,-75,-494,514,-203,411,-932,-526,975,176,705,137,-213,278,-148,300,-483,590,-253,561,842,-734,233,-140,202,-656,-248,414,-91,-848,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{304,8,784,113,578,-224,1000,734,112,-574,1000,798,52,-523,143,1000,-400,-807,622,1000,994,-34,1000,817,274,-924,34,-234,348,1000,-347,1000,256,-214,1000,-206,-120,374,-794,886,611,885,-1000,-578,-795,-230,743,474,571,506,213,443,1000,-754,-784,-761,629,-717,744,-75,519,-298,158,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-386,-385,-668,-763,424,-192,874,946,192,-606,494,-322,-972,-641,-30,-65,548,-596,126,923,-558,-207,-979,852,-673,427,-895,926,688,113,-946,-59,667,-319,464,831,-36,-749,630,-5,-839,760,222,787,-582,-326,830,156,682,705,67,97,301,-702,-791,-684,235,-296,201,-680,-43,-524,-755,965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{221,-556,554,87,186,-614,148,535,-191,-898,-302,-467,-1000,48,153,-349,-883,495,14,-326,-644,-308,-320,-112,8,296,1000,296,340,-53,437,-94,1000,-25,337,-23,391,904,683,-548,-918,-124,173,730,-1000,61,-1000,359,-438,234,460,199,221,708,-857,586,-288,345,-39,-922,-643,-236,8,318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-88,-356,49,313,594,-353,1000,314,-442,-784,1000,466,-1000,-855,-543,446,-565,-1000,1000,1000,1000,-33,447,594,690,-743,-257,-414,988,-79,-26,1000,-475,-298,1000,-525,338,32,-596,-158,443,1000,-674,172,-532,0,910,1000,-359,-317,47,55,1000,103,-720,-761,834,-717,-315,-75,463,-602,-424,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-282,1000,379,646,-500,740,1000,-53,1000,-369,-859,583,-866,217,946,681,500,308,-1000,589,-929,-780,106,-823,-1000,-948,1000,1000,-1000,397,-1000,-828,885,366,-460,-520,-1000,579,58,1000,-619,-752,101,-1000,-470,-902,-384,-759,-447,300,-357,428,562,-1000,-1000,1000,-1000,-293,-730,201,1000,1000,959,-793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-1000,-335,-1000,1000,864,44,492,696,-829,-996,1000,491,-303,-139,-894,255,-186,-726,926,967,1000,-417,-782,-521,191,-1000,-1000,615,384,549,-562,750,-1000,202,148,-1000,367,174,545,-1000,215,1000,402,663,275,-645,450,210,961,-293,-1000,-750,559,1000,-695,-258,1000,-1000,-562,-728,1000,-649,-817,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{156,616,-401,-370,1000,-867,904,445,-1000,-1000,1000,383,-837,-9,276,505,-768,414,807,269,1000,184,1000,974,339,-751,67,-1000,375,737,55,929,-1000,277,-426,37,-908,1000,-301,35,1000,-407,-650,-282,357,-253,-240,114,-626,-115,368,-149,-475,1000,-52,1000,1000,-550,-1000,-1000,979,-1000,493,-810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{197,218,823,239,-836,102,370,619,258,309,-906,-32,-595,-223,558,344,-5,-45,-853,365,-157,-345,619,546,-483,-601,340,732,-450,644,-341,807,984,41,210,-194,-375,509,496,1000,298,-205,-532,-724,-333,-563,515,-987,-651,999,518,628,594,-864,-1000,-329,-1000,183,1000,-940,-11,-73,264,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-136,-328,804,730,686,205,-428,384,39,-379,-6,-818,-607,1000,-260,0,-221,-425,-228,546,-760,-311,-830,-242,-145,-631,-742,-251,-866,171,-309,-59,-918,-566,479,824,-602,-875,-911,599,-1000,226,884,323,-724,180,-768,-202,750,-280,-1000,46,913,-419,-712,59,-288,-487,1000,-595,701,17,-614,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{-674,736,263,852,-423,388,-135,56,1000,195,-400,903,-1000,938,276,1000,459,754,-1000,213,-558,-682,-66,-276,-663,-1000,816,1000,-1000,375,-1000,-467,1000,523,-627,-1000,-1000,903,828,1000,441,-677,207,-1000,-257,-1000,-253,-1000,-1000,1000,-582,772,-289,1000,-1000,400,-692,-292,1000,-198,1000,834,872,-548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker):void",
            new int[]{563,730,879,449,115,-1000,31,627,682,-154,-725,776,991,449,-1000,-149,866,-690,309,-331,164,456,1000,-262,408,-179,10,496,1000,576,-251,1000,449,1000,52,-808,-880,-562,-437,-527,1000,-113,-887,1000,-211,-799,592,-280,96,-1000,21,-856,344,225,143,-983,901,904,-204,166,-685,696,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-741,156,462,-112,579,-319,191,537,568,-841,-142,303,-475,-849,-1000,-314,-1000,-624,-1000,235,-16,1000,550,-916,-1000,1000,-617,-1000,337,-619,397,-959,-946,-498,1000,-800,58,-1000,-828,-320,-1000,1000,-848,-1000,-1000,-302,942,156,-1000,44,-145,70,-648,904,1000,1000,-609,41,140,86,-404,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-619,-1000,568,-397,786,9,661,1000,625,942,617,-1000,-927,125,1000,1000,-884,-94,-68,523,820,466,-1000,1000,-785,246,874,-617,1000,24,1000,437,-452,-233,51,-486,-824,-38,426,-449,354,1000,347,-621,-1000,-746,1000,-198,-779,1000,-153,-515,-156,-920,956,-223,-1000,-787,1000,134,-725,802,-388,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-631,-71,18,116,1000,-807,-958,-701,12,-1000,32,-20,-1000,-870,-1000,25,-1000,-1000,-1000,1000,-513,1000,-552,-925,-1000,961,-1000,-1000,1000,-539,-198,-1000,-1000,-927,1000,-345,217,-1000,-610,-29,-1000,825,-860,-1000,-122,-313,1000,-205,-1000,1000,973,844,715,925,1000,1000,506,-206,413,748,-1000,994,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-1000,54,101,-101,1000,-949,-87,625,109,-841,13,400,-139,-923,-834,61,-1000,-415,-1000,-40,90,1000,-296,-893,-1000,819,-617,-1000,24,-422,146,-865,-534,-549,1000,-903,-55,-1000,-427,-1000,-1000,377,-473,-1000,-962,-596,942,1000,-742,-56,-279,179,-82,956,1000,1000,-928,145,458,-725,-404,747,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-856,-197,-7,-386,138,-1000,-744,839,-189,1000,723,1000,897,-1000,-208,-1000,-1000,-277,-1000,148,-420,1000,-1000,355,-519,475,1000,-1000,-829,-1000,1000,-724,-1000,-877,756,388,310,952,-1000,-1000,-1000,1000,1000,-1000,1000,-1000,-9,1000,1000,1000,-1000,748,-283,1000,1000,1000,-1000,-246,-32,-1000,1000,275,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{706,622,759,-538,769,162,984,1000,743,1000,232,-744,-1000,147,477,260,-693,-508,-287,-625,720,317,-1000,1000,-849,167,295,792,799,-39,457,365,-687,-449,390,-460,-863,58,-422,-506,503,943,886,-1000,957,-149,1000,1000,-770,132,983,961,-563,-495,-1000,-996,-1000,-852,944,-123,-144,762,-34,960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{837,-277,154,101,-627,760,-174,-683,253,914,246,360,-176,190,-636,-155,61,-471,904,-749,-326,90,-305,-856,-894,-158,-312,-617,78,-250,-684,-104,-272,-98,218,807,-903,758,-93,738,-237,390,945,-134,729,-962,403,31,359,-379,-56,-438,-913,917,748,-187,-92,-928,666,-587,-725,810,-510,-825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-373,-385,-167,-333,-176,5,-47,-376,43,-238,400,-600,130,64,-76,-1000,301,-1000,192,400,-34,441,-400,-487,-876,-250,159,-812,380,-87,-125,-509,-99,-12,93,671,-501,870,208,115,-563,400,345,67,-588,-1000,-115,329,738,46,50,221,-556,175,317,724,1000,-178,413,24,426,400,41,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{546,-1000,489,-73,451,147,-1000,44,698,306,-932,-94,284,-607,-579,107,-408,-1000,-960,-1000,556,-278,1000,288,-537,-431,1000,-608,-933,1000,261,959,-782,-1000,-1000,574,-141,-381,-365,-1000,-1000,-1000,377,-636,-1000,-263,-557,296,1000,-183,295,-942,348,-827,472,1000,957,-672,2,850,-952,-448,739,-509}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-794,-631,531,-556,-980,-312,-1000,-607,698,-356,-13,691,997,-31,-542,800,-709,-1000,-448,-1000,-67,543,1000,781,736,-1000,580,-638,-465,667,-104,1000,529,514,-1000,-140,-104,113,1000,-963,-580,1000,-1000,351,-868,68,-1000,-1000,-817,138,23,-1000,-204,-1000,11,-1000,-1000,-839,-353,324,-1000,34,-300,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-133,-423,-960,-840,874,-508,-160,481,-1000,446,-1000,873,-778,-74,736,1000,215,-464,-138,-576,1000,-575,-384,332,827,1000,-775,1000,200,1000,874,1000,168,-524,-1000,-1000,-86,815,349,-371,-850,472,704,-619,355,-462,659,161,162,282,891,362,502,514,-592,-1000,-638,1000,1000,-346,1000,-110,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-1000,-1000,-481,-502,-62,-235,-390,-259,-914,-464,-487,-1000,-881,1000,1000,234,241,-308,203,-513,1000,-612,-1000,310,-791,-165,1000,-962,942,425,0,-781,-341,1000,436,793,-831,-52,858,437,-1000,-651,-942,275,415,195,332,1000,269,69,-291,1000,-555,210,-372,699,-695,101,211,172,1000,157,-594,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-403,1000,-1000,-947,600,164,-265,907,1000,-491,-867,-731,-1000,-790,451,-689,-1000,-1000,-9,-36,-6,225,1000,136,-971,723,1000,78,-11,1000,1000,212,572,-1000,694,681,-968,999,-1000,-246,-1000,-451,536,-1000,1000,-1000,-27,39,556,-379,593,1000,-1000,784,1000,1000,-689,-1000,1000,-939,-683,731,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{-981,-629,-412,-869,-845,-617,-413,-138,-214,-204,407,214,177,959,-74,-73,364,-773,875,274,196,735,-869,115,-502,327,-876,1000,453,-1000,1000,-241,292,-168,-554,481,-554,-612,1000,441,336,-890,-225,-611,227,773,-169,1000,-55,651,-320,-219,122,-509,-843,-231,-228,-538,579,320,-494,119,395,561}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-171,154,355,-612,478,-1000,-1000,-612,635,-1000,1000,897,-488,-1000,291,-861,-1000,-1000,-1000,1000,-838,1000,-1000,-454,-1000,1000,619,-1000,873,-1000,1000,-1000,-1000,-1000,716,35,-314,-700,-1000,-836,-1000,1000,-662,-1000,681,-89,1000,1000,-1000,1000,89,1000,501,1000,1000,1000,595,-193,-69,452,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "addRangeMarker(org.jfree.chart.plot.Marker,org.jfree.chart.util.Layer):void",
            new int[]{1000,-1000,-117,390,-1000,1000,-1000,-1000,1000,-494,-720,-360,1000,1000,-1000,-1000,-134,-1000,391,-1000,-838,771,770,-1000,-925,-1000,42,-732,-821,-931,-1000,-198,-1000,-44,-179,453,-1000,214,-821,1,-775,-665,-36,836,-400,-973,-1000,1000,1000,-524,-350,-654,-449,163,1000,117,154,-1000,-11,160,-1000,310,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{1000,2,294,331,-61,-3,-1000,599,-986,-290,464,1000,-361,-370,-1000,-194,895,917,-717,-1000,-1000,1000,-1000,649,-462,589,-1000,-582,-1000,996,804,-1000,544,135,-200,-1000,-1000,1000,-1000,-1000,-1000,1000,1000,265,862,-519,-734,-145,-1000,346,173,-549,-1000,-303,-132,805,-643,1000,1000,1000,-241,-68,1000,-503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{400,56,-972,377,-1000,-97,1000,574,11,1000,962,720,-1000,-1000,-1000,382,1000,991,716,1000,80,687,-811,-259,-1000,1000,114,-521,-1000,737,1000,-1000,468,262,-102,-1000,-558,972,-1000,-1000,-301,897,434,-816,650,-1000,1000,-717,-1000,968,182,-1000,-802,1000,363,-67,-96,1000,-96,36,798,273,89,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{804,116,-921,179,-772,-386,1000,60,-38,692,1000,191,-354,-698,-446,184,1000,277,-362,922,1000,765,-720,-1000,-323,228,-243,-443,-703,371,890,-284,-242,792,-676,-1000,-773,319,195,-623,-282,790,-13,-85,-172,394,157,-759,-1000,414,-166,-160,-192,917,-34,-794,296,1000,-1000,729,-142,235,483,-654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{1000,1000,534,-922,107,634,35,-806,359,-946,1000,956,-951,-1000,-1000,-940,544,-672,-1000,-606,-1000,1000,-544,-1000,1000,1000,127,-210,-345,828,-119,-1000,-1000,60,-1000,-14,-1000,-158,-802,-1000,113,826,-316,-964,-919,1000,47,-1000,-884,910,-322,-1000,374,1000,920,-802,-1000,-779,-381,5,-74,292,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{286,-681,-216,304,209,485,-911,177,-22,-499,859,904,-575,-1000,-911,336,1000,1000,-430,-1000,-329,-1000,-126,-320,-1000,-732,32,212,-380,-438,431,-204,-629,-53,-511,-1000,731,1000,-172,-1000,40,657,890,-585,546,1000,-974,-941,-1000,1000,672,237,-150,474,1000,104,1000,760,-168,50,1000,660,599,348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{520,-479,-972,377,-1000,-367,-980,574,-635,-343,374,566,-251,408,-742,382,44,319,-642,-757,-884,758,-271,1000,-171,103,-738,26,-789,1000,1000,-903,468,625,565,-224,-926,-1000,119,-613,-879,649,841,-1000,650,-460,-1000,-153,-865,-16,344,-215,-1000,-510,-307,1000,-18,-93,1000,1000,-84,49,848,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{-1000,834,683,-522,-346,-989,1000,14,841,1000,-144,-1000,542,1000,1000,-246,11,-1000,-362,922,1000,-834,1000,-1000,-330,175,1000,746,-354,-96,-777,44,-242,1000,-425,855,1000,319,1000,-212,807,-848,-1000,601,-789,-235,802,-1000,911,-45,-1000,140,396,-396,-273,-949,-80,-1000,-1000,-114,225,-241,109,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{-782,323,-262,231,354,565,-139,284,-18,-251,-691,89,70,167,175,324,94,715,274,-536,928,273,703,30,-1000,-281,-121,115,30,85,-556,255,-640,-696,-233,-137,113,342,-692,382,591,-198,326,-285,816,-1000,764,1000,-573,-34,-445,616,120,-917,-1000,-395,-296,333,-413,418,-989,-192,530,634}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{1000,209,113,151,-697,-6,-1000,-437,-315,-996,1000,819,-366,-430,-1000,518,742,485,-235,-896,-411,565,-205,-70,-122,-1000,-1000,-381,17,340,547,-106,-347,481,-546,-566,-1000,760,426,-1000,-1000,1000,1000,-1000,358,930,-1000,-855,-1000,1000,-560,396,-961,609,842,-224,-28,1000,995,207,1000,291,548,595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{-637,-1000,-811,495,851,408,-680,525,166,30,-563,-71,111,-493,-68,-73,655,1000,-1000,-1000,-644,-1000,-14,493,-1000,464,826,1000,-818,-1000,444,161,-331,17,277,-847,1000,1000,146,-462,838,-621,466,265,284,681,-555,-566,-646,-365,1000,-770,965,114,1000,1000,-128,592,-248,723,-119,794,680,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{359,225,-1000,548,-736,40,-492,707,-355,769,-203,611,-306,267,-313,206,161,201,-868,-535,-255,1000,-819,752,-836,1000,-493,-175,-1000,523,936,-1000,849,348,59,-218,-447,-1000,-661,-324,-180,261,174,-669,190,-1000,212,219,-452,-908,741,-1000,-199,-540,-1000,343,95,-370,213,1000,-847,8,662,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{-263,828,964,-196,-386,525,-1000,-869,78,-780,345,1000,-407,-201,-114,229,402,201,128,-855,-411,694,8,-70,397,-459,-833,-369,39,334,-58,158,-652,511,-773,179,-999,1000,380,-1000,-182,692,291,-755,240,-46,-410,-521,-842,953,-448,-192,-344,667,825,-170,-685,1000,995,736,931,324,1000,898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{1000,-756,-541,513,-228,862,-1000,1000,-1000,-1000,-338,1000,-354,926,-819,819,722,1000,-1000,-1000,1000,765,-1000,1000,-627,-67,-1000,-355,-1000,371,863,-932,1000,-107,479,-635,-1000,-939,-1000,-623,-1000,1000,1000,-200,1000,-1000,-814,961,-1000,414,799,189,-1000,-1000,-1000,1000,93,439,-1000,1000,-315,-287,1000,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{-64,292,999,38,-226,511,180,70,496,-56,783,903,-479,-975,93,90,1000,172,324,320,907,700,-306,-1000,291,-372,-487,-698,100,296,-135,591,-166,628,-1000,-365,-892,1000,434,-831,-331,791,-39,-114,-335,552,400,-945,-936,1000,-805,-154,222,1000,1000,-1000,-466,900,-405,-396,890,454,-575,679}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{-783,-372,-81,76,-707,-3,81,40,-939,125,368,-946,-329,277,441,383,694,-10,260,-1000,349,-1000,673,-473,-1000,872,475,615,-1000,-625,-407,-740,-588,767,139,-1000,-254,1000,750,-1000,-225,158,64,-524,1000,983,-457,-889,-537,914,-12,-528,78,42,-617,-695,1000,-561,-780,1000,-656,493,317,118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByPoint():boolean",
            new int[]{-596,369,-1000,470,-511,-58,1000,225,344,1000,-400,-769,-798,304,-18,353,687,285,98,1000,1000,500,-312,-550,-516,1000,899,-279,-737,880,383,-909,1000,640,87,-341,-256,346,-425,22,202,200,-938,364,-384,-783,-1000,246,-977,382,191,-154,573,478,-988,-704,-384,-254,969,64,-723,-137,799,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{-284,-89,-156,516,41,-1000,-1000,760,-37,989,988,-279,1000,1000,82,-400,1000,-755,-712,-197,-1000,-508,-408,1000,392,-1000,321,193,-1000,1000,285,-791,142,492,-401,-84,40,705,-182,-42,-404,-733,-1000,550,1000,1000,62,-170,723,-952,-1000,-1000,488,979,217,527,863,59,-895,700,-880,-428,217,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{-238,-130,109,-309,316,756,-111,-859,-995,-944,-553,-624,-360,-494,-123,453,-378,285,478,635,408,913,341,-872,15,-20,383,-430,60,-167,-943,-112,-380,771,632,-314,272,-514,-34,-276,-914,-412,-42,-747,-950,-153,-473,-41,-576,496,872,122,-120,-609,-989,-801,-93,127,164,-496,954,-698,421,-78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{-8,-569,-122,1000,1000,-890,-280,-121,1000,806,349,-499,852,525,767,-925,402,778,75,-502,-175,447,-1000,1000,434,-334,679,-186,-625,-310,1000,-883,204,-262,317,876,-849,-943,79,-1000,-101,708,-1000,-10,-49,583,1000,316,1000,-976,-689,-376,1000,-123,-423,520,360,-364,671,1000,-657,-464,-158,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{-112,764,658,-606,-145,-1000,-336,-193,1000,-466,-525,-1000,-24,320,-2,-646,-791,-196,-992,-197,-761,-1000,400,-144,400,-493,66,1000,-371,184,1000,1000,937,-1000,-1000,-1000,805,1000,1000,922,-404,297,19,1000,1000,168,420,-744,1000,26,-1000,-380,-284,1000,1000,1000,-228,-455,101,-324,-1000,-360,-51,-611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{1000,-1000,-11,-380,-917,-361,5,233,-511,456,167,1000,747,-28,-993,922,-180,-223,-551,650,1000,200,210,-515,1000,-1000,-284,-674,-349,-1000,-1000,348,-926,393,473,170,-496,-200,140,-919,-708,-497,981,-685,-554,1000,994,356,-769,-659,872,-1000,924,-500,-189,-1000,132,369,-757,-745,916,-1000,-472,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{247,-25,439,-54,-831,954,-223,-435,494,534,470,-262,751,646,-308,-190,-642,-718,-1000,813,-586,-1000,454,-448,1000,-1000,-199,-332,-342,1000,-848,504,578,-629,-576,-565,1000,1000,662,1000,406,-12,405,995,1000,498,1000,-975,104,-844,-1000,-529,205,1000,555,400,664,-224,-797,-1000,-603,-1000,43,-846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{1000,-277,86,-316,-356,288,-895,-126,241,1000,1000,-555,287,-89,-40,-375,-549,519,636,817,-310,-906,-968,444,1000,-817,859,-1000,-1000,-223,230,1000,186,97,184,1000,-773,-699,-519,375,1000,-894,-42,776,-1000,-48,637,560,863,-75,451,-958,399,1000,77,2,810,-975,477,829,-1000,-1000,15,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{340,-893,-269,604,-356,-361,-320,64,-1000,448,158,1000,747,-28,-765,873,-165,-468,-349,1000,408,200,504,-515,989,-1000,86,-995,-657,166,-1000,34,-981,1000,892,327,-401,-254,-688,-883,-1000,-894,-42,-1000,-554,600,488,303,-769,-832,872,-958,921,-515,-1000,-1000,1000,485,-1000,-706,1000,-1000,15,40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{490,1000,404,-353,1000,366,-1000,151,-391,12,166,1000,-237,738,55,-1000,-152,644,436,-262,-158,35,-893,1000,-344,374,-362,-117,-1000,-372,70,86,30,972,658,1000,557,-42,-835,535,966,-390,29,279,-1000,-726,-601,211,-866,1000,-1000,-674,232,-558,862,1000,-1000,-1000,1000,1000,-653,713,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{-103,184,863,-728,41,-625,-54,440,122,-938,863,591,229,-934,-50,501,1000,52,1000,-466,-193,1000,-637,643,-384,556,1000,193,140,-373,-169,-1000,-93,-1000,-1000,-1000,835,356,1000,-757,678,268,-1000,238,750,174,62,-563,636,-952,-799,562,-1000,-1000,-325,238,398,232,-135,416,-880,-176,-886,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{-1000,656,179,-865,1000,-1000,46,-1000,1000,-1000,-489,226,-739,280,762,-1000,562,-98,-560,-1000,-287,726,-445,158,-997,1000,372,673,602,1000,1000,-994,1000,-1000,-621,-1000,1000,-127,1000,1000,262,56,-1000,910,496,236,1000,-1000,1000,1000,-425,919,-1000,-1000,1000,920,-681,-837,1000,738,-918,673,957,-329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{1000,-1000,684,-268,-1000,-63,123,1000,-1000,343,483,-433,701,-874,959,1000,-559,928,356,1000,1000,-1000,419,-764,1000,-1000,605,-865,-238,-1000,-1000,1000,-1000,167,-613,-49,-1000,172,-909,-264,672,100,830,277,907,-71,-804,1000,-88,-1000,456,-329,1000,1000,-1000,-1000,1000,131,-1000,-797,-44,-1000,-1000,277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{-835,152,-276,647,654,-792,-690,172,168,955,1000,-554,112,495,1000,-1000,971,222,-278,-1000,-776,36,-1000,959,434,583,945,316,-477,1000,383,-1000,735,-90,-796,256,-210,200,-337,1000,-334,-255,-1000,881,697,467,-637,139,1000,-554,-1000,-236,391,1000,428,1000,-99,-252,177,673,-1000,-28,459,79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{1000,-1000,264,497,-1000,-825,-320,1000,-586,697,176,1000,1000,646,-1000,1000,-642,-681,-833,1000,408,200,504,-327,1000,-1000,-683,-927,-532,-1000,-1000,483,578,540,698,103,-497,-178,-181,-1000,-1000,-250,-211,-769,-261,881,1000,295,-1000,-863,1000,-941,997,-761,-486,-1000,630,464,-1000,181,1000,-1000,-902,-481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{446,-1000,384,-1000,-1000,105,582,606,-167,376,-310,-1000,712,-817,99,-1000,117,-989,391,1000,410,-1000,1000,490,1000,-1000,1000,146,25,460,-197,1000,649,-1000,-1000,-1000,124,234,753,547,-601,1000,-488,542,1000,834,1000,-287,1000,944,930,909,-1000,1000,-998,-1000,1000,-34,-644,-1000,439,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "canSelectByRegion():boolean",
            new int[]{-587,694,174,430,1000,1000,-998,-1000,359,-1000,-821,46,-1000,-260,616,-1000,-206,1000,1000,-360,-542,703,65,784,-1000,1000,-445,45,-484,657,576,330,1000,909,683,948,1000,-493,-1000,1000,463,977,-324,372,-1000,1000,-1000,-217,-739,1000,-976,717,-1000,-680,1000,1000,-1000,-1000,-1000,869,-203,695,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{-1000,-1000,554,1000,-831,-428,229,-1000,609,758,-1000,-1000,-334,1000,-325,631,-566,-80,-1000,222,371,1000,-752,1000,791,1000,-47,765,154,964,-1000,-724,-77,1000,-382,-359,875,-1000,786,-719,-788,-1000,-220,184,1000,398,-1000,-108,1000,663,-1000,610,1000,873,-1000,1000,-980,-733,607,-1000,-1000,156,1000,413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{575,-282,-91,273,-167,1000,-468,258,-808,-808,581,994,-1000,837,-1000,1000,-556,1000,1000,1000,-214,-19,-1000,164,-229,-37,1000,556,753,587,-476,-794,979,-65,548,-407,578,297,-719,553,-192,797,105,-1000,1000,1000,502,-482,-555,1000,-428,1000,-302,519,140,-836,875,-804,101,-1000,446,-888,232,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{386,629,323,-511,931,-850,1000,823,546,-425,1000,1000,71,-1000,430,1000,540,87,-740,-451,542,21,1000,951,1000,1000,-234,657,-890,71,194,100,872,-1000,-805,527,-1000,400,592,995,-1000,603,-183,621,-1000,-1000,349,-746,-1000,-164,1000,-416,-1000,571,349,-101,1000,943,-857,157,375,721,-1000,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{112,767,-397,-996,-516,-992,342,-306,-920,476,625,482,518,-495,279,213,-521,-872,35,-129,3,-849,-146,357,-170,158,521,-404,-405,-753,22,280,-346,46,-454,-926,529,265,-403,363,555,435,682,753,-872,-228,-697,430,199,-830,567,227,92,-796,-352,775,577,738,-478,497,225,-780,-69,319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{509,-198,4,-173,-304,1000,-466,833,-1000,-206,-554,992,-461,-563,-494,1000,-273,87,1000,100,-357,-115,-1000,138,-449,502,-1000,-917,711,-110,-1000,-711,1000,-4,603,-865,748,-753,-1000,150,276,1000,240,-1000,987,1000,43,-746,-372,1000,-34,623,-926,571,-405,-168,803,368,230,-996,927,-922,-249,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{-1000,-995,746,-179,1000,-992,-198,214,1000,-754,1000,1,-1000,-321,378,-1000,433,-441,-1000,1000,1000,-1000,1000,1000,-287,1000,920,578,-1000,440,355,260,-35,-792,-1000,942,-1000,-14,1000,1000,-1000,-614,-1000,1000,-775,-1000,307,466,-712,-559,782,-683,-740,228,-494,1000,186,484,248,-6,-1000,109,-243,-545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{-1000,-534,209,794,255,-1000,627,-196,1000,118,168,-811,1000,-886,-1000,-1000,-535,1000,-1000,288,976,1000,918,1000,855,1000,-363,437,-509,226,-971,-218,421,-254,-1000,369,-677,-530,-339,154,-1000,-1000,-495,1000,-918,-599,-894,64,-136,-1000,600,-683,-88,-663,-563,1000,160,677,496,90,-839,866,-316,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{-1000,-1000,839,-593,240,352,-18,-1000,970,-51,1000,612,1000,286,42,-1000,-679,331,-1000,651,138,1000,-307,1000,-813,1000,206,174,1000,201,-57,-339,-1000,831,-1000,-769,481,-392,759,818,-15,112,-265,840,495,218,427,267,-1000,38,400,-741,744,561,451,874,-1000,-639,-862,220,-1000,8,1000,594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{345,4,-587,455,389,-312,97,463,-202,-266,-442,-1000,400,20,-115,-423,702,-293,1000,1000,414,572,241,-134,379,165,53,-284,-1000,280,555,382,538,-731,-127,960,-580,-1000,33,832,388,-1000,-156,-183,-336,-960,-71,-274,-844,1000,-1000,-297,-769,-137,-562,-382,965,-521,756,-743,633,49,-624,-718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{31,-130,-597,964,-332,323,672,-431,541,370,516,3,1000,-400,210,-400,-477,413,-400,587,-499,1000,400,287,1000,683,-133,436,413,611,-1000,-612,34,-85,247,-47,117,-1000,-144,-226,470,-640,554,400,1000,-400,754,-191,-103,1000,516,916,65,-843,239,-558,-929,-305,-42,-240,-510,1000,823,302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{162,-404,823,-12,1000,-1000,981,90,130,-991,1000,1000,-1000,-1000,372,-1000,210,-972,-469,1000,862,87,573,447,-951,890,-1000,406,-336,422,1000,-32,-127,-1000,-1000,-45,-1000,-965,-272,334,-1000,173,349,1000,-1000,-1000,-8,1000,-1000,-156,1000,423,-1000,-1000,1000,-240,122,276,-532,-1000,-151,792,-40,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{-825,-527,-162,614,97,1000,-468,-491,-295,-615,581,310,51,536,-106,1000,115,218,873,1000,538,1000,-337,1000,-229,158,1000,990,362,809,-715,-526,272,-275,53,69,578,-1000,545,757,-192,-315,91,-1000,816,956,-19,-472,-583,1000,-642,-219,-187,519,47,564,239,-281,634,-638,-795,-264,-60,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{-453,-370,-91,1000,-1000,526,-398,-866,-119,640,-986,-570,1000,1000,-412,1000,-1000,920,-359,540,-992,1000,-1000,767,30,70,1000,1000,1000,820,-1000,-1000,-397,1000,794,-1000,1000,-1000,-592,553,1000,-957,1000,-1000,1000,929,236,-788,1000,764,-609,947,1000,170,-953,-181,-1000,-1000,148,-1000,-530,727,858,585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{-144,-287,291,706,590,359,1000,876,592,-831,1000,730,401,-1000,-134,544,522,-204,-569,769,670,262,-134,850,125,173,266,1000,-53,389,-730,-111,681,76,588,-231,-115,-468,137,346,-1000,465,-305,-550,-193,-400,-433,-390,-1000,1000,697,36,-526,-238,-656,497,1000,231,92,-237,1000,46,-628,-871}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{220,-283,644,-372,513,-400,548,715,-591,-334,-261,996,-461,-1000,-520,-400,263,-1000,-28,100,396,-398,101,336,-954,1000,-1000,-1000,-51,-226,33,-177,226,-659,-481,-611,-357,-1000,-688,-3,-290,563,1000,400,-413,-400,-314,-390,-683,191,965,219,-1000,-492,197,249,276,1000,-213,-996,509,46,-440,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearAnnotations():void",
            new int[]{589,-45,-538,1000,-1000,1000,-246,281,-859,191,-674,502,400,1000,-530,1000,-859,819,1000,455,-1000,572,-1000,164,76,-56,384,187,1000,315,-1000,-1000,628,642,1000,-1000,1000,-1000,-1000,205,1000,443,1000,-1000,1000,1000,579,-1000,450,1000,67,1000,288,195,-537,-700,-525,-521,213,-1000,633,-53,108,-298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-605,359,-707,-1000,1000,119,1000,874,-513,-701,436,1000,-1000,936,992,855,256,661,-282,475,1000,-779,464,-671,-609,-400,1000,-77,232,-831,-198,247,-1000,-874,-335,1000,1000,734,1000,1000,1000,-135,806,-985,-392,-126,-832,-207,-551,160,185,973,-1000,-763,1000,1000,1000,-264,-671,550,237,879,813,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-1000,704,-452,546,-570,21,-745,740,-749,-109,560,811,-832,455,1000,505,1000,1000,647,257,779,-565,-932,-1000,582,257,1000,-148,619,-23,748,-1000,1000,-753,758,260,-28,58,1000,755,1000,1000,-1000,1000,-223,116,470,720,509,678,-665,1000,598,579,-345,-1000,-170,-374,-239,537,-115,72,1000,-574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-1000,-756,889,960,-33,1000,-314,-299,251,792,-99,-292,-49,-188,834,-723,753,-278,412,-524,-747,1000,313,-1000,-393,-1000,-564,-1000,415,-514,-605,832,-128,-192,29,-1000,899,-830,37,-672,-1000,-1000,-156,1000,634,639,-403,752,-965,-331,1000,563,155,-243,-230,-502,-620,-651,360,679,1000,-1000,322,975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-1000,-496,539,176,953,350,-106,317,80,-74,534,195,77,118,667,91,481,-479,653,105,-865,511,-524,-945,187,-590,867,-312,-399,-530,132,215,-564,-352,-273,517,629,-113,353,79,-425,-511,283,582,85,295,360,595,531,332,892,123,-71,-963,774,86,-580,-470,-108,1000,894,-123,441,290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-1000,867,-82,194,-173,162,425,-591,491,415,-7,-272,580,127,11,886,1000,-376,-364,-387,23,-1000,8,668,822,-173,472,-267,-908,-77,867,66,1000,311,593,-464,-608,487,616,-1000,816,-129,-508,250,413,521,761,-116,1000,1000,-215,-664,170,88,384,-1000,56,-243,680,-406,-568,1000,395,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{200,669,-297,-534,-613,-291,433,279,619,1000,-361,-35,593,-215,238,1000,-44,211,-319,-1000,-606,-1000,1000,1000,243,-24,1000,-920,-899,-669,1000,94,-1000,260,1000,293,-426,-453,287,859,1000,-694,70,-725,-837,134,-39,-359,-201,-25,209,525,-454,131,527,1000,742,-1000,-583,-364,-1000,586,804,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-534,56,782,943,1000,871,638,912,-849,127,779,1000,934,1000,894,-362,-184,-799,726,1000,484,1000,-243,64,-1000,-1000,1000,827,1000,-1000,99,216,-1000,-1000,24,1000,1000,777,-529,-614,-558,-906,1000,150,-1000,288,-1000,-207,-892,-721,1000,1000,-1000,-1000,283,1000,-417,49,-1000,-204,1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-395,-197,824,-864,944,-886,-390,-261,409,22,11,329,-47,-188,-1000,-284,-661,-478,595,661,-747,-723,313,-515,258,155,-564,220,-475,286,-9,-232,212,-192,-963,177,42,-791,-1000,400,963,-769,-686,359,634,-757,-447,-689,-532,499,831,563,155,64,530,-148,475,-73,167,-10,-404,818,861,-464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{26,1000,289,-8,-202,-630,306,603,-184,505,-347,-280,-484,152,553,371,425,-1000,-253,-300,527,-1000,872,1000,1000,297,-101,514,-918,292,1000,115,553,-1000,-506,198,-728,109,-112,-79,1000,-1000,262,-754,681,156,601,-351,1000,1000,628,1000,-109,-227,99,959,-168,-555,-981,-629,-519,664,619,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-810,-414,-617,-744,1000,60,104,1000,-379,-1000,-49,811,243,474,636,-13,1000,197,1000,257,779,-333,-161,-1000,265,-191,723,1000,1000,-811,-686,-1000,-64,-1000,-1000,1000,572,-79,1000,1000,-216,1000,0,1000,-42,867,-1000,343,63,-186,-169,-64,-602,-927,648,-200,29,769,-851,1000,518,-253,-331,823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-488,-265,-146,-792,816,344,1000,990,-741,100,-943,-28,-103,1000,174,-629,468,-659,309,-62,-859,1000,1000,-529,-1000,-1000,1000,974,873,-805,-189,-838,328,-1000,-502,400,977,-689,269,74,-682,698,1000,1000,-1000,-88,-1000,506,-363,-531,1000,1000,258,-1000,620,138,-458,-78,-1000,369,1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-824,1000,9,142,-51,-269,271,822,-18,-729,492,-384,755,-30,-203,1000,1000,806,686,-849,1000,-1000,457,1000,1000,705,850,-773,-1000,31,410,226,-538,428,-654,479,420,1000,1000,1000,1000,-1000,31,-1000,629,325,1000,-288,1000,1000,-600,451,-370,-223,-1000,1000,613,-669,869,394,-489,1000,662,-937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-1000,1000,-82,401,-1000,237,-883,418,385,521,1000,1000,-527,455,889,1000,897,1000,88,1000,1000,-857,-932,-938,1000,305,-709,-1000,-117,161,948,-464,397,-623,297,204,-66,566,616,32,1000,-38,-1000,1000,-103,852,1000,159,221,1000,-669,-113,200,858,-245,-397,726,-677,52,-20,-751,1000,1000,-574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-412,-160,574,-161,953,296,-106,382,636,39,-234,-100,-1000,-42,70,107,291,-136,246,-8,744,296,676,-611,88,-167,867,-7,-169,-21,132,504,62,-75,-1000,-400,-516,-110,-295,-242,-425,-511,-109,211,460,384,-435,-588,-270,175,-250,-64,-662,213,-240,133,-580,99,-240,-359,-740,679,518,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-1000,125,-625,490,108,1000,-426,615,-918,-242,418,1000,-538,1000,1000,-59,931,-346,1000,243,572,1000,-1000,-1000,-620,-799,1000,91,1000,-766,596,-855,-89,-1000,1000,40,1000,-171,1000,651,-118,187,-437,1000,-1000,324,-454,1000,-935,-128,43,850,302,-25,-24,-992,-32,-217,-52,1000,1000,-770,-136,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainAxes():void",
            new int[]{-471,256,-637,-932,972,166,1000,868,-130,48,-467,280,-372,1000,767,78,509,-210,-766,-269,-301,-286,1000,-446,-1000,-931,1000,308,650,-956,10,-107,-75,-874,4,320,1000,-48,877,626,-140,386,1000,-464,-968,-347,-1000,-295,-648,-307,1000,1000,-119,-1000,910,815,280,-645,-585,401,92,-481,345,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{-502,210,-841,812,1000,326,-1000,640,-566,533,-173,1000,262,748,587,-228,-814,-504,758,-273,1000,698,71,18,-11,1000,733,958,1000,-128,747,705,271,197,-31,265,-292,-1000,-618,764,-553,-415,-1000,667,1000,-380,-730,-15,1000,-440,1000,388,-678,261,840,-49,131,-377,-808,-956,283,-1000,154,809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{629,-873,-334,-236,-50,438,276,629,359,532,-505,919,-644,56,-377,494,11,-442,-201,-2,319,-157,410,100,-206,742,-727,944,634,-253,1000,457,-498,-967,96,100,-594,-15,-1000,1000,230,-1000,-52,388,718,-238,-976,667,410,62,689,1000,-155,707,-79,1000,555,-134,424,-1000,-258,131,537,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{-1000,786,959,-745,-1000,-231,-476,-63,-75,442,354,252,934,6,460,1000,767,-102,-478,508,198,-598,1000,-569,39,-720,554,765,-325,-1000,73,156,-351,397,828,735,-625,473,-1000,892,100,1000,1000,983,-40,1000,-609,433,-916,570,484,-46,1000,666,-1000,840,-1000,1000,1000,-1000,-445,-1000,128,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{-1000,527,245,-751,-886,-513,245,285,280,297,509,95,396,712,1000,963,1000,-885,595,347,-581,-498,126,-1000,115,-631,426,467,-728,-511,953,-627,569,-293,903,970,-859,-169,-863,787,1000,1000,1000,184,-196,1000,-95,72,-821,448,288,364,-264,-6,215,439,-304,388,1000,-1000,-947,-233,-96,-663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{100,-894,-712,-485,-91,-74,415,1000,667,825,291,1000,1000,1000,918,963,-299,-557,-42,113,-60,-498,75,-1000,138,-631,-698,952,1000,-944,1000,-209,507,-233,1000,196,-895,-656,-1000,787,1000,1000,-355,789,-196,920,-765,68,1,-458,1000,1000,-207,1000,-190,439,297,-707,961,-1000,-679,-729,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{-285,-1000,-396,-479,-134,827,1000,-177,-1000,-1000,-801,-835,-574,-860,-592,269,-358,-1000,-1000,-617,-88,-822,1000,815,-1000,-820,-772,737,959,-346,835,1000,-1000,142,-1000,-1000,-86,1000,-1000,1000,-1000,1000,1000,213,1000,-1000,1000,947,-681,1000,-1000,1000,1000,-1000,-1000,-460,1,413,-814,1000,-253,1000,-1000,-784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{86,-328,-396,-157,436,1000,676,851,287,-942,1000,695,-721,-599,83,-115,-21,-340,-502,-1000,-1000,-639,-1000,444,-177,-312,-1000,735,-541,-540,751,210,-1000,-1000,-1000,-1000,267,760,28,-257,532,211,-1000,-823,338,-806,-780,886,-189,761,294,1000,-399,-977,-222,815,-242,63,-5,165,-226,1000,59,15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{45,-873,-493,-297,364,258,-856,-168,433,-1000,-1000,-820,-644,660,-472,948,-643,-281,317,-35,533,369,108,688,-1000,1000,64,627,-460,669,1000,555,10,18,-472,-442,-815,-998,-336,414,-289,910,50,378,396,-416,1000,131,-4,1000,-272,1000,-1000,-1000,487,-1000,-233,-350,-1000,-536,348,653,-1000,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{12,-880,-356,667,-1000,832,174,1000,97,-850,-354,-340,1000,51,-24,582,868,-284,-349,-32,-291,-350,-1000,-651,-794,328,-354,1000,-827,607,584,480,-670,383,-1000,-956,-499,1000,-498,488,719,717,-603,1000,1000,770,1000,-453,1000,813,-930,1000,561,-932,-90,472,943,-159,-344,680,-818,902,-276,-439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{-370,-221,822,839,980,-314,-86,-538,1000,-138,-982,224,-145,-329,42,326,-386,-843,-245,979,746,1000,-1000,374,-616,673,959,-422,913,-347,520,1000,-39,-47,358,665,499,412,-390,-477,-1000,-1000,-1000,1000,-33,-731,-511,49,1000,-431,575,-320,-413,679,-97,412,407,-715,-852,-330,692,-601,-497,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{-744,551,-712,762,383,-1000,-915,-847,-266,825,77,-106,722,1000,154,1000,-100,-557,233,957,747,626,1000,-461,-70,445,1000,480,576,-631,-207,-704,1000,125,1000,439,-1000,-1000,-1000,775,550,213,-355,643,-298,652,-875,-213,91,-203,1000,-934,-207,400,1000,74,-1000,441,12,-1000,53,-1000,-156,365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{-409,-646,-711,510,767,-1000,-394,196,-719,-245,-891,-1000,1000,741,-71,1000,901,26,356,1000,71,-364,488,234,-844,-309,821,1000,-1000,159,207,-446,1000,885,-155,392,-1000,-1000,1000,-723,918,1000,386,865,332,1000,878,-493,-142,1000,790,-345,-512,-1000,827,1000,1000,1000,-73,-1000,-692,-478,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{-500,210,-181,-216,-1000,393,699,1000,255,1000,1000,1000,1000,1000,1000,1000,1000,-482,1000,967,-651,-1000,-244,-1000,1000,-1000,-62,660,1000,-1000,770,-1000,1000,-166,1000,1000,-1000,-777,-912,658,1000,1000,-317,1000,-950,1000,-207,-405,-493,-458,1000,-154,-157,1000,-28,1000,237,-453,1000,-1000,-226,-729,1000,713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{-11,739,-660,506,384,218,755,1000,763,1000,1000,96,1000,1000,179,1000,-737,-768,738,425,901,762,647,-1000,1000,-142,-219,843,1000,-1000,1000,-478,732,-379,1000,1,-1000,-1000,-1000,779,1000,-230,-1000,1000,468,1000,-1000,79,205,-1000,264,1000,-448,1000,-372,1000,440,-448,1000,-1000,-655,-660,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{-1000,-588,14,287,-401,1000,-629,-928,-166,-252,-924,-409,-275,829,803,883,322,-1000,-744,696,-224,1000,805,133,-929,1000,1000,489,-796,-755,-76,273,1000,849,600,556,-296,-780,-1000,1000,-3,1000,1000,199,-457,6,884,-529,69,757,241,-350,387,-1000,756,-684,-1000,530,-556,-1000,-208,-1000,-1000,-998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers():void",
            new int[]{12,488,448,-344,-1000,-610,278,981,-1000,1000,1000,323,1000,996,-967,949,1000,-971,969,580,-702,-1000,-210,-1000,795,-1000,788,674,-616,-1000,561,-1000,-949,176,648,772,-1000,167,-1000,824,1000,1000,613,1000,-9,1000,875,-142,-256,32,961,-649,573,805,-743,1000,-255,58,1000,-958,-1000,1000,1000,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{1000,-176,339,989,1000,435,971,-631,1000,23,-1000,963,809,482,64,-614,-1000,-1000,-1000,-1000,307,74,-1000,-1000,362,-782,-1000,-1000,1000,305,-434,-1000,360,-1000,1000,1000,1000,-937,-1000,328,1000,980,-1000,1000,-132,-800,-1000,258,-1000,1000,-1000,1000,1000,421,-1000,1000,683,-429,-1000,395,194,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{-284,1000,149,469,-445,1000,498,566,285,742,345,207,224,711,-1000,1000,400,400,230,-665,-447,-204,400,-1000,343,-601,364,-419,-549,72,1000,201,-724,-546,-92,1000,1000,-705,-207,-252,-617,-259,-1000,-236,1000,800,-1000,-760,-1000,687,-4,-500,-299,533,232,-818,174,-1000,400,-791,-1000,-943,306,601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{-309,155,76,400,-1000,723,-91,-199,-904,-442,-846,-95,737,-115,-276,-954,198,361,0,88,452,489,319,738,960,765,-870,467,-576,-191,387,1000,1000,417,-1000,182,-626,-623,555,-215,1000,820,6,528,-895,-185,-50,-970,108,-746,-846,-773,474,386,1000,-1000,504,489,683,-72,973,461,245,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{-1000,603,392,877,-1000,1000,429,712,677,257,1000,85,408,841,-604,1000,1000,1000,874,1000,-1000,-458,1000,596,-863,230,1000,1000,-82,-71,1000,1000,34,37,-1000,-799,-667,-107,1000,772,-554,702,-356,750,20,-87,394,-1000,-711,-1000,915,-1000,-74,-601,1000,-1000,1000,-597,1000,-1000,-215,1000,-1000,547}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{1000,477,547,-59,-105,1000,429,156,677,432,-1000,574,408,77,-78,-1000,286,-1000,874,1000,-104,142,-883,281,1000,-355,-1000,-243,967,-71,603,-400,32,-1000,529,739,730,133,-368,1000,661,319,-215,725,-685,329,-1000,-756,86,-591,293,-113,881,525,50,-1000,1000,-518,-764,-1000,5,-804,366,547}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{-1000,929,239,468,-984,853,283,598,-841,259,921,47,-6,1000,-755,1000,1000,1000,436,296,-1000,-187,1000,27,-971,-125,949,173,-683,58,815,829,-496,-179,-1000,-144,11,-578,731,61,-963,-40,-937,-658,963,714,816,-656,-1000,199,708,-823,-465,-1000,1000,-1000,50,-1000,1000,-1000,-931,390,9,963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{-46,809,-141,-65,879,720,-995,129,-811,-977,1000,-385,-1000,1000,-828,339,390,1000,-371,-485,853,-917,1000,-788,338,-642,1000,-978,1000,125,353,-215,-137,-397,-526,1000,792,1000,105,-396,-1,-608,-930,-1000,689,-425,-939,862,-900,-255,-604,-768,92,-1000,-104,12,1000,-400,-838,-897,-1000,-519,-566,297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{623,1000,904,-400,-456,-334,134,585,456,-262,-892,-107,1000,188,7,-658,-1000,280,602,550,-82,-339,-1000,-184,390,177,-821,329,383,-826,373,1000,1000,-781,-242,443,994,-824,256,1000,1000,-262,-281,1000,271,1000,897,-1000,-867,1000,822,-145,163,-246,275,-480,299,-681,-94,-506,184,-1000,1000,627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{625,-225,360,1000,-837,-404,415,954,229,-3,-866,-643,1000,-224,-219,-95,-1000,-683,-1000,-962,-210,134,-951,187,-355,575,-1000,-1000,1000,-619,1000,508,425,-214,-671,-184,537,-1000,3,873,1000,1000,-1000,-230,-332,1000,876,-1000,-800,898,1000,898,-72,-31,692,-783,-1000,-650,-20,1000,309,54,667,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{968,-574,404,-98,879,-744,466,228,191,411,-469,720,-229,-384,-542,-894,-748,-972,-229,-485,853,-205,-779,-788,404,-735,-372,-905,929,588,33,-458,-335,-631,181,755,154,-323,-529,-932,-797,-716,-890,-97,-76,-717,-986,-26,813,389,-865,188,875,-821,-602,262,183,272,-838,484,-62,-972,245,-675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{763,-834,632,-359,316,-334,735,849,6,1000,-628,1000,587,-688,636,-732,400,-1000,-551,319,-159,-320,-1000,-465,210,187,-561,-348,958,-71,-875,635,222,-816,407,384,-662,-546,347,348,-88,-608,-799,845,20,-1000,251,-165,1000,492,250,149,1000,647,-748,730,757,848,-1000,421,82,297,471,-786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{968,-595,124,557,1000,-175,-46,-364,787,754,-841,608,-510,-636,-177,-601,-1000,-669,-1000,-962,1000,86,-1000,-1000,589,-486,-1000,-662,548,953,-336,-1000,387,-1000,97,755,143,-643,-459,-1000,-21,-720,-890,1000,-13,-295,-986,383,598,388,-1000,165,1000,-481,-384,1000,837,551,-838,-39,208,-556,773,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{-261,1000,323,148,-1000,1000,607,650,851,144,345,298,901,809,-269,1000,1000,380,725,-392,-1000,-414,245,-469,575,-786,364,-485,-499,-453,1000,248,-1000,-809,481,1000,1000,-725,-25,576,757,-145,-1000,202,1000,1000,-1000,-1000,-1000,1000,845,-404,-746,407,-46,-1000,399,-1000,-1000,-744,-1000,-785,306,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{590,-932,514,-1000,727,476,283,-141,-680,411,-361,600,49,196,571,-928,652,308,-858,185,45,-479,1000,132,974,-1000,-193,-737,1000,58,33,809,1000,-596,-328,283,-291,293,-345,288,-164,-292,-372,530,-712,714,-344,596,832,199,708,-1000,1000,68,-181,-204,596,893,-807,-619,73,236,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{-781,-454,69,806,774,-1000,275,156,-976,901,-533,-705,197,-725,-1000,-912,-533,-85,-458,-1000,686,302,88,-229,-27,690,-686,1000,-1000,1000,-1000,-292,1000,1000,-946,-674,-646,-604,406,-803,184,909,-58,-441,-501,-108,1000,-459,830,145,142,76,681,-311,882,412,-1000,1000,47,231,1000,-298,-610,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearDomainMarkers(int):void",
            new int[]{1000,192,398,292,933,-297,1000,-135,1000,-317,-809,669,1000,-1000,-145,-340,-1000,-1000,-574,-1000,1000,58,-1000,-1000,1000,-426,-1000,-661,1000,270,-285,-1000,234,-1000,1000,1000,1000,-1000,-1000,365,1000,-202,-1000,1000,-237,-200,-1000,-191,-142,1000,-995,973,1000,252,-1000,833,381,-601,-1000,798,378,-1000,1000,-748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{-217,1000,554,1000,1000,-230,-541,260,27,-775,1000,196,591,601,1000,333,-1000,173,-254,108,713,462,-471,-1000,1000,-333,-1000,636,731,-1000,-330,-730,-1000,960,-580,-456,-965,1000,492,1000,1000,-1000,313,-554,797,-1000,57,444,65,1000,1000,327,826,-24,764,1000,-582,344,-380,-674,1000,-293,-262,-26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{549,-201,-941,-488,-100,-700,250,-7,649,-148,-830,-784,-548,-64,-181,657,746,-416,297,-893,656,-647,733,479,724,656,902,-236,-566,914,273,-14,763,-879,245,115,342,51,459,582,-839,572,230,-683,260,-694,407,-984,-678,-776,237,-262,-319,-329,186,-131,-85,-218,-904,993,-136,-698,543,255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{821,-982,249,9,933,-1000,1000,-843,405,-1000,-300,-962,-773,1000,1000,693,-180,-879,-387,-715,1000,-913,1000,780,1000,173,278,-34,51,-416,-1000,-1000,-962,-883,-1000,787,-397,676,-367,-21,-666,626,-1000,-634,1000,-1000,387,165,-456,670,-100,-301,1000,-1000,476,1000,1000,-417,-1000,-900,344,-739,498,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{162,584,-167,-478,983,-1000,113,-662,1000,-1000,-881,-152,-1000,-317,266,1000,935,-1000,-266,-1000,-799,-1000,1000,-704,1000,223,1000,543,-1000,569,71,-1000,299,-676,-314,115,-410,-145,-85,-586,-1000,572,222,-894,384,-631,-390,-1000,-1000,362,1000,1000,462,-1000,1000,1000,-456,-200,-1000,504,-538,-1000,537,-661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{89,-1000,-686,563,-172,1000,492,-58,649,1000,375,292,-278,58,-181,-330,578,170,-57,742,-1000,492,502,-145,-886,-993,553,472,134,409,1000,222,-761,1000,515,-1000,-1000,-1000,-545,-818,727,-246,542,-633,-817,611,1000,457,140,260,237,-262,377,-377,9,-1000,-1000,1000,-1000,97,-292,516,-1000,255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{132,-1000,408,923,-444,-340,1000,-373,1000,454,174,-773,-601,1000,-81,-24,92,-1000,-22,-12,698,-1000,175,950,-793,443,256,919,889,1000,206,-1000,808,-910,185,1000,-169,981,201,310,-923,504,373,-887,-550,-1000,-139,-926,-172,264,-841,-603,1000,-783,399,-351,652,-443,482,428,438,-637,1000,504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{713,-611,797,-521,606,-836,-592,-579,-887,-516,-881,354,-104,-317,530,463,856,-103,755,-597,-799,980,147,-164,948,-11,-879,739,645,-244,71,855,-710,165,-567,783,843,388,57,-112,26,-418,222,-531,644,320,864,437,119,362,899,123,537,312,-142,796,-845,-858,-494,-53,-14,620,-427,-518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{-835,815,1000,949,-971,182,2,-19,717,-12,338,-315,-317,1000,-453,198,-471,-877,816,243,852,-751,-238,913,-154,385,-741,300,-425,-25,-852,-204,736,925,-6,957,710,1000,499,-42,593,-612,-21,21,200,-768,701,-1000,601,114,578,634,320,-330,671,640,618,-1000,669,568,-52,-797,511,-864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{548,-1000,918,789,386,745,634,-371,1000,1000,60,-422,-504,948,-15,-683,778,-863,851,898,-732,-678,-675,58,-813,309,-2,1000,440,1000,1000,257,1000,-892,101,514,-221,-333,-655,-110,172,-208,-233,-737,-669,-288,1000,-1000,1000,27,80,-542,-11,-682,227,-541,449,195,228,927,-543,-43,-398,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{591,-982,519,322,933,-424,1000,-217,535,-378,189,-176,1000,562,-208,693,-823,-18,1000,-478,1000,-731,-1000,934,281,1000,-1000,-403,734,1000,252,-1000,1000,-589,66,36,696,1000,1000,1000,-233,-797,1,300,785,-1000,-146,-662,732,381,981,555,348,1000,1000,1000,1000,-1000,854,881,964,-971,139,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{772,-112,554,387,1000,41,-541,-869,643,-476,-114,1000,303,627,1000,-59,578,-1000,604,108,-466,-287,-471,-1000,987,-13,728,1000,81,-168,42,-730,-111,-286,-891,260,400,273,-901,-437,-369,67,-538,-770,203,-483,57,-752,-19,1000,1000,511,500,-1000,-93,646,-700,-55,5,203,-560,383,-429,-26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{70,705,1000,313,219,-109,-382,-831,147,-429,68,308,-544,1000,91,573,680,-516,285,-445,-118,-814,779,102,1000,-578,-262,1000,-745,830,-32,922,-244,1000,-917,-248,-717,360,78,-1000,-164,-816,-834,-663,903,350,1000,-1000,114,-95,-154,-943,1000,-369,1000,1000,-1000,-540,-882,243,-197,-167,-840,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{679,609,-112,200,1000,-663,-1000,-806,362,-980,-293,-212,723,138,-1000,832,-440,134,-193,-1000,1000,-542,-89,-36,1000,172,-270,46,452,1000,384,-1000,-328,-371,-386,-939,-650,1000,1000,826,-973,-709,40,-395,1000,-639,-173,-264,-390,94,1000,447,972,654,-129,1000,1000,-144,-611,-24,1000,-980,-547,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{1000,-1000,-101,-128,-77,1000,858,-517,345,791,-221,547,-237,712,405,-533,998,-744,956,-22,-507,-379,-323,163,-450,1000,379,-14,656,1000,722,734,1000,-1000,-397,692,1000,360,-249,639,-1000,727,-533,-403,-699,-786,891,-332,460,312,-830,-705,-637,-361,-835,-1000,723,-67,359,1000,-464,481,363,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{185,-345,-546,445,145,901,-185,401,-607,863,416,-441,738,70,426,-17,-563,980,394,612,-417,1000,91,663,-542,39,-539,-700,513,-73,609,860,-1000,473,414,-824,187,-375,-545,968,1000,-281,433,234,473,18,934,1000,441,482,-860,-877,-51,349,-467,-733,173,840,-811,277,133,-449,-394,891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeAxes():void",
            new int[]{-880,131,-324,-866,-512,606,74,0,-281,-405,545,56,493,447,619,515,655,291,687,868,-277,-532,-879,594,110,595,-777,-79,-777,-318,409,913,-93,885,137,347,19,65,170,-518,309,619,953,-901,243,-245,-896,-998,-81,800,-655,-455,807,985,-480,681,690,648,-185,-445,877,827,748,694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{87,289,-87,1000,-1000,437,-217,629,65,429,-162,-366,-901,899,478,638,-273,331,-31,-9,205,-944,450,-1000,183,770,586,1000,-165,-537,197,117,120,969,-1000,1000,-1000,513,713,-1000,508,1000,801,306,-1000,-942,18,581,-1000,926,815,106,-1000,663,959,-131,-608,894,767,-1000,373,1000,-159,67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{-1000,965,-72,-859,927,-540,-1000,289,-355,-698,-508,618,606,-300,-275,-253,4,82,-326,308,354,970,-1000,1000,-518,-669,-437,171,-92,453,63,-409,-1000,-339,1000,-824,269,404,-1000,1000,267,-861,268,925,787,614,892,-230,1000,848,-118,-757,1000,-993,-1000,207,601,-901,-381,866,370,-1000,-675,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{867,-112,269,-615,133,-464,339,677,64,-863,-1000,1000,305,-16,-364,884,1000,139,-463,1000,264,-957,-302,1000,-1000,749,174,483,-998,58,-6,-918,1000,474,99,42,-158,223,809,-259,-704,-621,-1000,138,-473,-324,-382,88,1000,666,476,-407,-434,-1000,-418,590,984,-370,620,-852,-486,-838,936,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{-1000,1000,979,-607,322,-235,-1000,252,28,-169,-532,742,-104,765,1000,-1000,-452,-706,-1000,0,-1000,-1000,134,870,760,-824,270,932,907,610,156,721,-516,522,83,-224,508,103,-1000,20,938,170,215,-1000,524,638,-409,-288,124,-176,1000,-151,909,48,-844,123,-683,73,-695,782,-536,-159,-1000,-642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{-35,289,-593,-1000,533,-785,-481,263,-268,-1000,-547,-46,68,-386,-1000,1000,1000,855,545,941,783,1000,-1000,1000,-933,116,-86,-825,-773,48,-584,-770,-598,-405,1000,-614,342,471,847,406,-1000,-1000,117,378,420,-482,933,315,1000,1000,-439,-1000,32,-1000,-1000,426,1000,-1000,-171,825,-94,-1000,178,-70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{-291,496,-412,-987,253,-281,-124,412,-256,-952,-519,59,-99,-1000,-1000,1000,793,608,247,400,783,546,-1000,1000,-1000,124,106,-222,-1000,-103,63,-758,-1000,-34,909,-257,-261,456,1000,375,-1000,-1000,-89,322,-94,-527,430,315,1000,1000,-615,-1000,-35,-1000,-1000,246,847,-1000,-171,234,745,-1000,1000,152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{665,-702,-202,-982,-90,623,-773,-250,-1000,341,-103,-949,-590,-521,-266,-11,793,-190,-129,-1000,32,821,-824,-24,-185,269,684,-732,-351,284,-318,-150,65,-1000,938,131,-395,1000,-88,232,-596,-268,-119,-475,894,-1000,411,236,781,774,-1000,-674,0,-485,-632,-118,201,-1000,-971,833,503,-895,796,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{44,843,1000,-450,-371,693,-1000,-132,-1000,204,-172,-27,-803,674,1000,-1000,747,-1000,-1000,-1000,-1000,-1000,207,-612,1000,-125,1000,522,-1000,1000,-626,369,-233,-695,-50,569,-930,854,-1000,-1000,658,4,297,-1000,1000,-758,-272,117,-624,-676,1000,1000,63,54,268,-272,-349,93,-381,815,138,-593,-1000,-561}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{1000,516,-401,798,-483,-119,485,771,-754,-435,-423,876,-152,-336,-116,638,517,658,49,585,300,-1000,-272,-200,-749,1000,314,1000,-1000,-862,702,-507,460,502,-160,979,-1000,474,1000,1000,257,-1000,-275,964,-1000,-1000,-467,714,-277,1000,771,-370,-1000,-293,200,249,469,-232,1000,-1000,523,562,-18,744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{298,1000,844,-919,-444,-790,447,312,-283,-1000,-933,332,-262,-6,-673,609,953,-442,-737,1000,-270,19,-93,1000,-1000,752,585,677,326,729,-295,-1000,254,1000,724,1000,759,199,-213,614,-1000,-384,240,-442,-406,-669,-538,-939,977,1000,-58,359,-209,-1000,-1000,1000,188,760,798,564,-94,-1000,792,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{-301,379,550,-563,-846,396,796,-372,-502,980,221,0,-1000,-987,56,708,1000,-629,23,358,458,137,1000,-340,-1000,1000,-1000,41,371,412,38,380,-595,266,-229,324,-583,1000,-541,-340,-129,560,-1000,-364,-1000,-951,-1000,-563,-257,-621,859,80,-765,-626,618,-146,-369,-1000,-678,-1000,888,914,-1000,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{-1000,1000,-656,-73,248,719,-743,-786,-260,-594,-541,-524,-162,-1000,-607,301,-1000,668,1000,-268,1000,1000,51,-400,264,379,-245,-424,523,162,-19,-415,-1000,-364,295,56,1000,163,156,722,174,135,1000,-480,799,396,783,250,-349,272,-1000,-1000,816,400,-695,100,-90,-315,286,562,611,53,154,381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{-1000,749,-1000,-1000,324,915,105,-232,-1000,-1000,-499,-495,-294,-1000,-1000,1000,-246,1000,1000,436,417,1000,-741,478,22,-111,-351,-818,-313,-409,-27,-891,-1000,-599,899,1000,730,337,1000,615,-1000,-845,105,423,541,-414,1000,49,631,1000,-1000,-1000,177,-95,-1000,-274,737,-1000,-1000,852,1000,-927,164,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{-291,435,-514,-618,253,-281,194,204,-111,-1000,-667,41,492,-1000,-1000,1000,289,608,375,400,1000,1000,-1000,1000,-1000,526,-204,-35,-1000,-103,63,-1000,-516,-34,909,-257,234,422,1000,892,-1000,-1000,-180,252,-94,-100,258,-309,1000,1000,-835,-1000,-35,-840,-1000,482,1000,-494,-1000,-49,775,-813,1000,152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{1000,-547,-985,-881,739,-446,1000,495,288,-940,852,1000,806,-987,-466,1000,663,841,-4,468,519,613,-802,247,-453,1000,-57,-284,-411,-1000,748,-447,61,1000,258,-161,-404,330,1000,791,-1000,426,-1000,1000,-483,-244,-978,-25,276,-131,742,356,692,-492,1000,277,613,-1000,-80,210,189,1000,164,-670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers():void",
            new int[]{-397,1000,-266,966,-742,682,997,-172,-247,-334,-1000,-1000,-1000,-742,-673,515,-871,826,1000,-1000,850,-802,919,-686,140,1000,-9,1000,1000,-259,1000,-949,-245,1000,-930,1000,1000,-213,1000,-1000,-1000,845,240,138,-1000,-365,-231,-51,-774,1000,-384,-316,-1000,775,-63,408,-25,1000,586,-858,-171,705,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{733,-286,-496,-412,-542,1000,793,-715,-1000,19,-541,1000,-1000,249,-8,-576,-1000,-617,-760,-136,60,-1000,-488,-975,-1000,-1000,115,-370,72,-296,314,1000,-957,-458,-1000,-338,-517,-386,1000,1000,1000,987,-1000,400,1000,190,1000,-1000,-473,-871,-571,-633,71,1000,993,-827,-352,-1000,-1000,-1000,-71,1000,-645,-786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{1000,553,-74,248,-1000,558,1000,1000,-98,1000,317,272,-995,1000,-1000,-562,136,278,984,-493,951,139,1000,-191,94,1000,-623,1000,572,-307,85,-504,911,1000,135,1000,-847,-1000,692,372,803,-748,-585,-560,143,-906,-574,-212,-1000,318,-1000,1000,-1000,929,-710,94,560,-520,984,97,640,-832,889,-499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{620,-1000,629,-295,-487,629,333,-908,-1000,602,-9,1000,-1000,445,-481,-829,-1000,-1000,-1000,-643,-625,-1000,-534,-1000,-1000,-1000,420,-494,-820,175,939,612,-1000,-960,-185,-559,257,706,773,655,1000,509,-1000,1000,20,-373,1000,-1000,-849,-973,266,-1000,1000,1000,-407,-1000,-1000,-1000,-1000,567,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{-54,-665,1000,450,399,438,-247,1000,-1000,974,-435,137,-108,-843,191,240,750,-400,-342,-81,-641,-820,-226,438,-1000,615,405,-328,-1000,-261,548,-994,-403,170,777,298,279,929,-819,-69,86,759,905,-1000,473,-103,-280,-1000,444,-19,1000,-740,707,-447,-1000,-1000,472,-817,-738,-186,-340,-319,-324,-610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{1000,24,154,-321,-701,813,635,423,-1000,59,-594,-1000,186,495,-5,5,32,-674,1000,696,-356,-43,1000,1000,-580,-1000,-1000,-151,46,795,-367,-1000,60,833,633,1000,574,188,43,-308,1000,1000,301,-383,-167,-1000,-107,-1000,167,-531,-415,-856,-675,503,-258,-1000,702,-1000,1000,1000,465,-921,36,-3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{-341,297,-382,218,-1000,56,1000,-597,-50,-640,-300,-329,76,-106,-1000,-71,93,-151,-331,-389,293,438,-222,-201,404,-224,43,517,-155,301,314,396,-459,-283,-727,-739,344,-623,1000,446,-688,-259,14,266,-377,-166,-177,708,-1000,34,-120,211,173,-191,605,-94,127,599,-164,-1000,-110,345,463,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{810,-402,-941,-929,-1000,-149,-775,1000,-1000,834,-377,138,-1000,1000,-1000,-655,-235,492,984,-273,-449,-324,134,863,-517,1000,-1000,1000,846,-1000,213,-817,-249,847,727,-1000,850,1000,766,614,998,-757,136,-1000,1000,-1000,-334,-268,-1000,171,-1000,427,-1000,491,-730,47,742,-550,-432,23,13,295,151,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{511,189,-603,1000,-110,1000,659,-573,-780,-545,-165,960,414,128,-447,-505,-310,217,-376,-497,804,-1000,-354,-1000,-48,328,-251,502,1000,774,937,389,-201,-525,-1000,414,-972,-22,431,1000,-507,210,-545,-389,165,472,489,-1000,-917,-827,-1000,-1000,407,883,1000,58,570,138,153,-1000,-150,486,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{-42,317,842,-698,-890,-402,769,-691,699,307,226,97,390,986,-304,-867,359,707,784,56,271,937,-753,882,493,400,-716,679,608,-182,-14,-660,185,305,397,-30,435,417,-28,-556,936,375,431,189,881,-179,911,948,-521,-381,-809,351,-935,666,-696,492,-760,-534,308,395,940,-700,185,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{623,1000,-171,-89,-25,-281,454,204,86,147,-344,-708,-1000,182,-1000,-291,638,937,1000,785,870,1000,1000,873,913,-241,-1000,126,1000,839,-663,-213,1000,-75,-268,-197,-489,-1000,-599,643,-137,443,-374,-560,-898,-1000,-772,895,-767,664,-1000,1000,-795,211,97,-246,1000,1000,1000,1000,1000,-972,1000,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{370,-1,327,-440,-1000,-620,679,1000,244,945,-307,-331,1000,650,-1000,-633,1000,1000,605,-1000,-689,276,732,715,787,-35,151,1000,389,-341,-260,-1000,153,1000,1000,609,91,672,725,-561,341,928,744,-867,1000,-828,-790,187,-1000,-286,-92,-169,-994,-277,-1000,226,400,50,1000,462,613,-641,162,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{462,568,-611,-1000,-1000,-381,975,85,-565,-219,-1000,-232,-493,892,-1000,-1000,94,751,1000,903,-1000,-34,605,1000,-311,379,-1000,1000,1000,1000,270,-1000,-656,-593,644,-840,-261,481,666,1000,997,908,579,-370,1000,-828,193,46,-1000,-49,-1000,-325,-330,273,169,275,771,-358,-189,462,103,610,1000,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{-951,-202,557,232,384,-576,-1000,850,-1000,375,-1000,-1000,-396,-758,-189,-387,266,-492,-775,-736,-574,110,-762,604,-131,35,148,-451,-1000,629,230,-1000,-68,-270,396,-682,124,-41,-1000,783,-918,1000,1000,-646,618,-427,-583,339,107,930,817,743,975,-981,-309,-489,765,443,-1000,399,-150,236,485,-104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{-619,-64,479,850,-84,215,-704,755,225,402,-307,-869,1000,-648,-104,812,1000,269,-75,1000,365,1000,-319,449,913,-1000,525,267,-822,-344,-1000,-246,1000,1000,309,497,529,-364,-442,-561,-1000,993,549,-1000,274,-530,-1000,855,73,363,770,579,-233,-1000,-756,-51,561,1000,1000,763,942,-1000,-5,564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{901,-116,-461,585,-139,1000,-253,-1000,699,-745,-22,1000,-695,-74,478,444,359,-508,-63,161,-710,-712,1000,-1000,-1000,533,-248,679,-62,-351,1000,360,185,-762,-170,-85,435,-387,500,568,1000,431,394,562,-1000,91,1000,-514,732,-381,-1000,-1000,-935,789,1000,-552,602,-534,308,395,512,-57,155,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearRangeMarkers(int):void",
            new int[]{376,1000,798,146,-817,-196,778,-327,50,218,-244,-172,-1000,705,-498,-1000,210,-65,369,-322,628,430,319,-366,169,-245,-766,856,479,-1000,-273,-440,-420,-153,-257,-271,-709,-640,2,1000,876,1000,-172,396,82,-219,488,271,-602,-623,-1000,1000,-20,1000,605,468,229,-672,-644,-1000,438,181,-669,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{459,-139,-668,542,594,-160,-773,479,764,-278,686,-304,55,-840,-779,-148,-602,-470,-638,465,421,-56,254,-912,794,460,-321,494,-944,-841,-112,-440,-868,851,918,28,534,-505,841,820,580,-370,-176,-834,-122,557,-260,-534,-204,363,-331,470,663,-571,-405,-812,36,560,310,-297,-433,-487,679,-990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{741,202,-426,482,-352,1000,-265,187,963,89,-352,-513,599,864,-716,-184,-121,-1000,-1000,994,878,442,-102,-1000,1000,1000,-531,831,-277,-289,989,37,-572,718,427,-278,171,-1000,957,785,1000,-605,608,200,-739,-546,798,40,-1000,638,-386,1000,706,-983,-53,-489,-100,-554,-72,-979,-989,156,627,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{297,202,199,98,635,482,701,39,-47,-424,101,619,-349,-272,-408,-402,161,710,304,-764,-2,60,716,131,201,-859,470,-137,-880,-85,-801,-915,307,337,169,445,-12,-456,-484,650,176,469,-667,480,-126,798,459,62,-486,-1000,927,-505,24,-364,-237,343,-689,1000,-309,681,-458,27,-538,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{-477,-1000,539,-824,-350,929,1000,-1000,-642,-783,-927,-296,194,1000,-479,200,618,587,572,679,304,288,956,471,401,-1000,-432,184,-451,878,-196,-493,1000,946,652,872,523,14,-195,223,-872,422,286,-1,-999,-989,1000,93,174,-1000,390,550,-433,-155,277,96,-1000,442,-921,545,570,-118,862,484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{53,71,-668,797,594,17,-781,569,1000,-831,538,-336,290,-634,-1000,-256,-139,-654,-638,518,361,198,238,-882,1000,460,-421,375,-944,-841,121,-226,-1000,1000,947,80,1000,-778,910,895,893,-523,-147,-694,-449,-712,-359,-223,-208,-901,-373,864,176,-520,-581,152,36,560,310,-355,-609,-425,165,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{-282,-571,170,-221,-582,824,952,-818,-452,-925,31,-309,231,471,336,181,51,130,712,148,878,-481,-102,663,278,-412,795,756,537,724,-524,-488,589,-106,-349,-391,889,-434,-861,-470,-360,327,229,-716,-326,-546,798,108,772,-876,-471,307,235,441,642,-804,-892,999,-813,881,631,321,-882,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{274,-469,494,-45,60,363,-867,11,654,498,701,-861,463,631,-707,226,-533,-1000,994,881,-130,-525,315,-824,283,-223,-1000,407,-964,300,-256,-436,-39,1000,21,841,-469,-963,1000,1000,886,94,282,544,47,-1000,-76,655,514,-53,-1000,1000,174,-612,315,899,-699,385,131,431,657,-344,495,717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{746,475,1000,84,-13,652,172,-281,344,-1000,284,-164,1000,1000,-821,211,939,1000,1000,-559,-841,286,1000,496,-30,-1000,-937,519,-1000,1000,-525,-655,943,1000,1000,1000,1000,-513,-703,1000,305,627,-382,-35,-1000,131,677,62,119,-1000,-441,692,-283,-465,521,-120,-1000,1000,-1000,998,352,-118,-733,-701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{451,936,129,207,-26,728,226,414,655,-571,547,137,682,309,-659,-33,383,569,319,425,119,-117,98,-262,-200,-422,191,101,-740,836,-742,-844,639,11,775,271,111,-401,-717,294,456,888,-271,740,160,462,804,569,-114,-1000,-10,-60,-87,-105,406,-695,-683,757,-819,-194,-396,-469,-223,-236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{717,613,-1000,820,359,268,220,831,943,-860,640,-610,239,-440,-884,-404,-374,787,-1000,-237,399,1000,148,-177,-643,-331,-131,-56,-986,112,-506,-645,208,896,267,431,1000,-541,-145,408,-181,456,-493,-1000,-742,1000,615,-924,13,-1000,223,629,-84,-850,-693,-1000,-485,282,319,-43,-1000,-1000,-169,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{-141,-646,494,-640,-27,834,793,-886,-644,-965,-305,-176,125,735,-324,68,424,768,825,304,-101,179,1000,533,256,-1000,-586,419,-565,989,-690,-694,596,983,594,841,869,151,-306,97,-489,780,282,-525,-648,-441,837,418,321,-931,-108,217,-669,61,512,-155,-699,984,-1000,1000,458,-344,637,491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{-226,-349,828,-608,-37,620,344,-361,-334,111,504,-123,-208,667,199,-28,17,-1000,994,307,116,-394,337,-246,-88,-1000,-423,14,-250,1000,-878,-828,690,369,267,350,-1000,-280,6,760,355,1000,-1000,1000,832,-1000,806,754,-633,-938,-592,125,988,8,-744,1000,-960,480,-786,727,534,-442,54,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{734,-596,-816,-583,-428,-747,-123,-672,-41,337,938,-516,-410,-805,909,-182,-785,-503,-872,620,440,-598,-444,-936,-832,-728,970,815,548,-481,352,-902,-432,561,-485,37,164,68,-143,-391,-881,-987,482,134,878,-639,896,858,-81,706,954,-100,321,601,-171,-407,-196,-98,387,84,-388,69,-466,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{-561,468,4,-456,722,747,1000,-316,-967,-648,323,263,-903,-1000,509,375,13,905,-140,-536,773,352,496,500,-547,-1000,799,-621,443,1000,-1000,-938,768,-1000,-1000,926,-1000,516,-1000,-1000,-903,1000,-754,553,1000,39,1000,1000,181,-1000,1000,-1000,176,243,120,191,-1000,372,-1000,872,-35,-655,-169,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{-516,566,518,-506,325,970,627,-688,259,-627,-666,-703,-181,820,-669,592,-647,-803,354,392,467,-5,-449,-357,-817,855,-914,-735,746,203,852,832,273,539,-590,98,205,105,-889,250,859,-986,-312,404,300,-753,601,-570,217,374,-130,-36,-474,309,229,187,-996,-996,40,-189,435,550,617,-425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clearSelection():void",
            new int[]{215,274,889,741,796,-807,-34,432,-390,-511,-358,216,529,475,-180,322,947,793,404,-712,-608,-524,-541,991,-833,403,58,-58,221,748,-67,-939,-322,193,-699,545,233,922,-943,977,979,702,-105,430,-885,711,872,-85,-513,224,-598,268,-82,-275,-229,318,-106,576,-141,-992,331,989,-40,-948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-115,267,-806,-933,-791,236,95,63,-521,23,400,-486,1000,59,648,28,353,-1000,486,2,-703,400,1000,0,791,1000,165,-335,620,465,-423,23,128,-758,97,1000,673,187,46,-440,-945,119,-642,-456,-257,-36,542,753,-555,384,503,-400,1000,-193,-323,183,-237,-366,-417,-943,-290,-1000,559,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-15,948,-867,848,20,370,887,249,1000,160,-294,-117,-1000,-739,90,1000,-355,-1000,-1000,-618,-679,-253,124,-272,-543,-262,833,-215,-1000,561,-1000,1000,-196,1000,843,-1000,-1000,516,-792,1000,955,453,-1000,365,788,-1000,-255,-60,-54,1000,1000,13,-691,-239,-312,309,-350,-451,-114,236,-470,627,1000,850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-189,-829,-246,-386,-264,-73,-1000,-1000,-131,599,-374,-820,317,108,566,-1000,175,-187,-743,-390,376,-882,975,-1000,383,1000,44,-1000,23,1000,464,-1000,792,634,235,-475,85,-676,-468,115,-902,392,-551,-1000,-704,-587,-1000,411,813,-118,102,-136,-729,-611,-848,-708,-307,-775,-63,-610,-926,228,-882,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{572,288,-31,971,-521,734,661,320,-199,211,-1000,504,-574,-69,1000,1000,-350,274,400,-421,-679,-741,155,227,-9,-67,833,997,400,177,-1000,493,1000,-400,612,-242,141,105,-194,1000,-393,643,54,-764,805,-408,-147,-200,-766,513,-100,844,197,-306,-97,1000,860,-643,-686,243,-456,572,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-412,-752,-956,365,870,-556,-352,-10,-439,749,1000,-513,185,-218,-556,-1000,1000,607,-1000,-337,-205,235,1000,0,-229,-73,165,-1000,648,951,1000,-397,-953,1000,76,-1000,-353,-27,842,-454,405,-534,24,-174,-772,-1000,-145,-60,-555,-659,-433,-807,802,571,-971,-1000,-1000,-295,-955,-1000,403,-617,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-973,960,-96,817,325,-117,1000,-140,1000,484,316,-50,-14,-1000,-227,1000,474,-1000,-1000,-562,-793,140,-12,-756,-866,42,722,563,92,1000,-374,1000,-196,811,-130,-982,-381,273,561,1000,1000,-268,-79,592,933,-719,13,-461,579,-400,-566,-61,497,47,63,-4,-350,98,-708,-360,510,-846,108,-57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-801,57,640,404,93,380,-191,59,-951,-194,-783,296,94,-173,678,-445,-178,161,1000,-128,255,-208,-435,1000,272,1000,-365,-61,772,-1000,131,148,703,61,660,411,201,-804,1000,-1000,-860,-506,1000,-937,236,668,-129,159,-32,-1000,-769,-236,481,956,-697,383,926,-915,-108,-507,219,779,415,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{1000,243,82,175,54,-1000,669,-368,1000,-99,1000,-246,753,-984,-1000,1,1000,-151,-984,-829,-177,769,-821,-1000,-879,151,1000,930,481,73,-1000,-182,-713,-1000,-172,-658,282,113,601,-1000,-599,-342,-86,569,-1000,1000,-74,-189,237,-1000,1000,-1000,415,1000,-1000,220,325,465,-767,-978,846,698,-1000,299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-1000,-1000,393,1000,1000,-25,95,-74,-383,-469,-1000,-508,1000,1000,-204,-1000,423,-1000,425,-1000,-204,-1000,1000,306,-598,1000,1000,617,1000,-1000,-404,-399,1000,148,369,68,-223,-1000,595,40,-1000,1000,3,-956,-593,-173,-1000,712,164,-773,-1000,1000,-250,-480,-1000,-592,940,-819,-266,-1000,212,1000,559,678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-1000,-516,-427,92,576,863,250,532,-231,51,-903,-159,-550,-252,-150,-1000,-346,-597,337,-332,-1000,-59,455,67,982,739,733,89,1000,256,-646,57,15,497,1000,-130,-441,-530,-576,-1000,581,134,-398,160,-351,1000,-620,99,-727,887,-253,270,-794,-764,-1000,-197,213,-1000,-233,-485,-1000,-1000,579,288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-756,1000,-401,-628,811,446,178,452,-320,-41,802,-826,53,-684,-602,711,-17,1000,165,700,-1000,421,-271,944,-765,139,-87,-1000,-694,199,131,319,-328,163,1000,-639,-699,930,1000,-317,-1000,-540,1,389,344,-1000,1000,-530,-539,601,-769,-505,-55,211,-697,341,926,155,232,149,616,314,729,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-587,-1000,528,655,-1000,900,-1000,603,-1000,763,-1000,-728,27,1000,1000,-1000,157,672,-1000,4,-1000,-1000,1000,300,1000,1000,-881,-951,-572,12,-399,-1000,1000,-37,824,617,474,-1000,-1000,-1000,-625,-267,-301,-1000,-717,370,-61,432,-178,345,-975,710,-1000,-889,-958,609,979,-1000,-312,-118,-1000,-12,-400,-284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-397,-294,-871,824,472,307,94,541,397,-463,-251,-99,-724,-20,-586,-505,391,-820,-820,-772,-1000,-118,263,-1000,-143,527,1000,-1000,-400,146,-146,536,76,1000,881,-429,-505,19,-170,-1000,404,-202,-785,51,-321,-1000,-509,595,1000,522,55,-188,51,-206,-920,-1000,-1000,-636,-583,-545,-16,-103,286,-777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-353,1000,-786,-261,-455,257,1000,842,-61,-672,628,-251,105,1000,570,442,-53,-324,461,468,-330,1000,-12,300,-871,261,-62,-855,-174,1000,-393,1000,-879,56,481,336,-610,691,-14,-608,351,1000,-901,838,152,-268,719,331,-688,569,486,-792,468,612,-253,428,-143,-405,-741,-145,284,-846,1000,758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00334() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-994,-1000,249,-209,714,187,-70,24,-1000,917,-495,-66,451,101,-686,-1000,837,-396,2,-243,-543,234,-90,-262,929,553,688,-703,690,280,841,-1000,141,301,-650,267,463,-1000,58,895,-1000,1000,1000,-395,-461,96,-927,1000,399,-1000,-605,-973,1000,1000,-308,-715,-258,-444,702,-1000,262,-194,-841,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00335() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.plot.XYPlot|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "clone():java.lang.Object",
            new int[]{-1000,267,309,106,-1000,1000,124,388,-441,-755,-1000,-152,1000,631,1000,-134,-1000,247,341,229,78,-625,1000,689,172,348,-192,-156,-1000,465,-1000,23,128,91,1000,364,-698,-474,-1000,1000,242,1000,-796,-389,-238,0,-964,890,-555,1000,-617,918,-1000,-1000,-323,710,1000,-1000,-651,546,-290,-530,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00336() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{929,-503,957,0,-574,-109,-21,229,-962,-940,-503,-743,231,-412,33,866,-154,-528,514,936,-171,93,77,190,571,-922,-820,14,-453,-38,-393,659,966,-406,-363,-12,942,-428,122,-769,-629,-361,-509,-268,97,924,836,-427,-743,-791,123,-898,-275,966,-693,-71,-499,-157,-323,724,-455,442,-890,-702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00337() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{1000,-508,903,-31,-657,6,-126,653,-555,-1000,-537,-1000,351,-540,33,225,-91,-560,348,914,-577,1000,-527,116,742,-1000,-1000,32,-293,305,-65,549,633,86,-1000,-332,1000,-796,1000,-850,574,-931,-647,-199,110,922,-101,-511,-1000,-745,-173,-634,-47,817,-760,-289,-425,36,-623,802,1000,957,-1000,-702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00338() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{929,-395,958,138,-388,177,523,229,-1000,-940,-597,-91,79,-249,329,866,-143,-815,-203,1000,156,-419,243,-179,767,-493,-981,218,-832,414,-831,265,1000,-406,-1000,-554,1000,154,-419,-99,-1000,326,-449,-543,97,1000,643,-417,-923,-1000,-25,-717,7,858,-368,-406,-90,-157,-511,1000,-787,-257,-890,-255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00339() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{-300,875,-1000,-530,-1,442,415,-1000,1000,1000,632,1000,-607,1000,415,186,166,541,-156,-1000,655,-1000,100,-242,-525,386,228,-993,310,146,-1000,98,149,1000,530,1000,-485,853,-9,709,167,1000,1000,-1000,-822,-709,-465,1000,663,338,-47,570,-1000,-519,940,-782,-1000,1000,-309,-1000,-1000,-391,1000,950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00340() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{1000,-1000,238,-979,-1000,584,236,635,291,-1000,-185,-247,108,802,-145,634,130,-581,1000,532,88,-881,-317,629,22,-966,-1000,652,47,338,-205,483,1000,187,-1000,1000,1000,-998,1000,404,143,199,396,-284,592,826,534,-511,-753,996,-546,-634,-1000,1000,-760,301,-1000,-849,-740,-406,1000,957,-1000,320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00341() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{29,-203,-12,-182,-211,1000,-1000,-291,201,-1000,-189,230,330,244,-292,-919,1000,-830,-844,569,-883,1000,-37,-123,881,434,383,-194,453,-182,-381,358,-444,1000,-996,-840,458,-835,-457,-385,1000,-916,-1000,792,649,943,-1000,1000,-295,-848,-827,-307,-460,-1000,1000,-993,695,-11,-371,576,-840,303,76,-823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00342() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{-572,291,-1000,-787,734,307,-1000,-49,1000,-1000,538,230,678,-48,-691,-1000,1000,-154,-1000,-771,-1000,1000,764,-19,358,722,816,-916,480,257,365,86,-1000,1000,-996,-138,60,-978,-929,-490,9,-307,-1000,621,133,-402,-1000,1000,241,373,-1000,220,-1000,-872,832,-830,292,-718,765,-686,-1000,225,684,-238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00343() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{-491,784,-886,-473,569,158,339,90,678,115,-582,-96,-66,152,492,-1000,124,-531,-64,-730,-442,917,-301,-879,764,-167,-984,-412,983,-33,-641,-269,-276,746,114,-443,-368,-39,-450,-583,447,-791,177,-200,413,-539,-1000,376,-286,-527,-1000,308,728,229,682,-445,-499,1000,-149,64,-669,442,582,-702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00344() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{123,311,-1000,-935,-446,417,-306,863,1000,-1000,-195,-800,309,-234,397,-1000,342,-122,-447,-921,-993,400,-1000,-262,795,757,-636,-259,422,455,1000,-65,-319,862,-1000,361,1000,-1000,14,-538,1000,-293,-1000,-298,859,-440,-762,-120,-450,130,-698,7,-602,925,-454,447,-1000,-467,482,-457,-147,998,10,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00345() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{-353,-722,-996,-230,-488,-128,-313,997,-625,-744,-197,-154,260,1000,-1000,1000,192,-898,844,505,944,-1000,1000,-1000,-19,-124,-1000,818,-1000,905,-73,1000,187,-597,-99,1000,292,-218,1000,949,-242,495,1000,-212,703,1000,1000,220,335,-1000,373,-351,-236,307,-412,-226,-44,1000,-482,-483,1000,1000,-911,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00346() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{-849,613,-794,-210,796,-491,-600,-1000,-321,1000,-153,1000,75,88,-594,428,-102,-144,-486,-983,623,1000,1000,261,795,757,1000,-1000,422,-1000,-447,1000,-1000,862,496,31,-1000,529,-540,-927,368,507,548,-298,-930,-1000,-387,1000,1000,-773,673,-264,-967,-1000,792,-706,1000,-426,-475,-798,539,473,1000,-75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00347() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{-482,536,-531,-221,804,817,1000,-1000,543,1000,558,1000,802,493,-580,404,-619,899,-787,201,-676,772,1000,-1000,-861,129,601,-1000,419,1000,-1000,-549,531,343,514,-644,-235,-23,-1000,-1000,-210,-18,735,-1000,-741,-274,-104,508,-391,-1000,-675,-196,-1000,490,695,-1000,303,894,-74,-580,-1000,-69,837,-406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00348() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{-542,-572,-101,-490,416,-379,-543,747,291,339,881,-724,600,802,805,388,107,-906,793,-830,-764,908,55,629,136,-878,935,-476,429,-58,-923,-33,-365,355,831,-184,132,-998,-31,-889,-894,199,48,-645,429,826,-119,-659,-369,53,-546,-643,-767,483,421,-846,-438,171,775,-183,-102,218,-291,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00349() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{-533,60,14,614,553,831,-712,-363,178,-327,301,499,-195,-158,-494,-21,872,-459,-1000,195,-885,1000,84,-1000,1000,689,409,1000,657,-192,-486,-519,-1000,928,-267,323,-298,-462,-9,-896,803,-1000,-1000,616,634,532,-786,1000,245,-908,-79,-251,-334,-102,1000,-1000,983,532,-152,615,-1000,-97,440,-947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00350() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{1000,-401,370,-138,-92,355,564,399,-374,-605,-84,-354,-326,300,-292,753,-176,-385,897,243,-84,-235,-246,-83,-172,-945,-874,595,-17,-672,-365,-25,1000,-61,-554,871,1000,-391,872,-42,-205,495,749,-844,630,1000,679,-947,-1000,-72,-214,-569,-521,-787,-1000,561,-649,190,814,668,-356,317,479,-823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00351() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureDomainAxes():void",
            new int[]{172,-357,-469,-228,-158,549,38,1000,798,-727,275,-794,266,256,-12,-919,516,122,-1000,288,-1000,400,395,39,244,-136,-467,226,517,616,221,-359,622,-91,-454,-53,456,-779,611,-203,67,-475,-660,-445,516,-105,-350,-277,-894,1000,-698,-69,-634,586,-1000,17,-1000,-467,1000,-314,-18,585,-585,-468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00352() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{76,-9,-259,1000,-764,454,720,-934,1000,28,479,466,369,-107,819,761,1000,-683,301,-617,-957,898,925,-503,-465,-396,-543,1000,-102,29,-1000,552,1000,837,15,1000,381,-468,1000,-1000,312,-37,-747,-316,626,523,266,-274,551,-399,-457,-675,-585,-581,-855,-344,151,493,312,-611,176,-117,242,598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00353() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{581,-712,-71,-819,25,1000,544,979,-683,-513,-356,-10,-759,149,-605,-96,-568,250,684,571,829,-88,1000,-760,-754,-168,492,-1000,-823,-413,56,-836,-82,-226,-130,437,-213,-893,-34,1000,-447,-203,1000,-447,-936,1000,683,357,-454,-682,-220,-845,95,499,32,6,762,162,1000,-960,97,880,-404,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00354() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{949,-170,169,793,-211,215,-244,1000,-140,-333,-168,-971,110,687,-400,1000,425,-401,-232,651,-981,-13,-734,-1000,447,-142,-529,273,388,426,-1000,-323,-554,896,-101,-1000,714,757,152,22,-602,-113,-133,402,395,-1000,254,-704,674,-626,-4,-130,-363,40,-520,454,-393,-455,-301,-507,-264,137,983,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00355() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{982,-254,-661,658,-1000,821,1000,-282,198,313,-312,4,-288,-1000,5,480,-27,-768,891,57,966,1000,544,620,-32,48,108,122,-630,-556,-28,-97,-9,1000,864,1000,70,-4,1000,-1000,-437,1000,426,647,-530,268,413,53,491,-1000,-457,-1000,-55,457,233,-1000,-321,-898,287,798,1000,-716,103,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00356() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{-162,1000,238,-774,262,-13,-1000,-571,-725,227,602,-920,395,165,-1000,42,-1000,1000,-1000,908,1000,-1000,453,-574,1000,652,-276,21,-565,-364,-163,39,-1000,1000,77,-759,1000,12,-544,1000,419,-197,267,-385,-25,-511,868,-234,-663,-790,-1000,1000,204,187,321,-661,578,111,-969,364,-246,-1000,-109,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00357() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{136,934,534,171,-393,-58,-485,-1000,73,-255,277,-313,728,1000,1000,109,63,-111,-1000,569,-903,-715,-987,-698,576,-325,-1000,617,592,26,-502,718,576,864,-627,-228,698,716,621,-603,227,-1000,-734,272,1000,-921,611,-343,-57,-570,-647,-214,-299,154,-59,-123,381,838,-774,19,-740,-438,92,125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00358() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{888,-928,-596,-1000,-1000,1000,1000,1000,-1000,176,-412,192,-1000,-1000,-1000,-460,-10,-17,1000,272,1000,1000,1000,640,-270,272,1000,-1000,-1000,-1000,414,-1000,-794,272,1000,-454,-471,-1000,66,1000,-1000,1000,1000,428,-1000,1000,286,614,-341,-1000,678,-1000,418,862,381,-1000,-116,-1000,1000,-882,1000,950,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00359() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{653,895,-1000,332,884,2,-1000,366,1000,-370,530,-950,12,843,-1000,720,-825,973,-845,791,-1000,-980,-449,-594,516,339,709,388,-240,-613,-257,-133,-462,-193,75,269,745,-968,-878,690,-185,630,589,-853,18,-715,528,-959,496,-427,62,1000,-552,-340,-1000,438,585,157,80,1000,-516,-848,1000,877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00360() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{-333,691,250,684,749,549,-1000,-321,961,-564,668,-114,17,1000,758,1000,-158,423,-406,87,-1000,-1000,147,-1000,-552,-642,-543,824,333,684,-759,1000,-1000,759,-821,400,768,-333,294,-1000,463,-1000,-1000,-339,531,-114,377,-490,315,-1000,-257,-75,-865,-792,-1000,465,828,1000,-368,-184,-1000,-463,847,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00361() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{765,967,1000,311,-226,-203,-639,-440,189,175,745,52,1000,307,1000,472,85,206,-555,1000,-379,-821,-561,-228,493,-253,-1000,306,1000,-196,-1000,944,503,950,-1000,-665,740,694,824,-805,241,-1000,-270,684,806,-345,509,-569,164,-777,-72,162,-436,657,766,-47,341,893,-418,101,-774,-124,-235,-355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00362() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{452,-44,-466,-740,400,598,292,1000,421,275,425,-613,467,280,-378,602,185,14,684,140,-720,-88,915,-400,25,-182,-1000,-109,814,126,-186,-662,-176,-536,-132,-1000,67,-826,-527,346,182,-527,249,-1000,-379,-974,124,597,725,-515,846,25,-231,-208,-869,-624,193,185,637,-365,-308,572,572,337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00363() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{298,-461,-916,1000,-653,1000,1000,-137,198,595,180,353,-331,-494,-739,313,1000,-1000,942,737,-555,1000,989,257,-456,-1000,-80,-1000,-100,-392,-931,-245,399,193,910,-54,198,-618,-458,-318,-1000,310,404,-586,-848,-7,-395,1000,28,-462,997,-967,648,-43,-960,-725,-464,45,168,-1000,401,1000,-382,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00364() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{-341,-136,593,378,-1000,727,368,-1000,-1000,-567,136,1000,-216,265,1000,1000,-1000,502,-519,-243,975,298,747,753,-471,-986,-1000,-322,-668,-1000,884,442,1000,665,-698,102,0,-1000,693,77,468,-439,-87,-140,591,1000,915,968,-1000,-394,-1000,-908,242,758,736,-1000,1000,1000,345,-1000,282,-960,-1000,-727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00365() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{-655,785,1000,-293,804,227,-1000,219,-67,283,1000,-179,1000,476,1000,325,-1000,1000,-1000,667,5,-1000,621,-744,526,174,-1000,492,200,250,-1000,1000,1000,628,-1000,-578,1000,-88,158,-145,816,-1000,-1000,-439,649,-48,282,-635,-303,-378,517,1000,-411,-70,-365,-1000,1000,1000,-495,556,-1000,-1000,-890,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00366() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{455,357,299,-620,1000,-108,-1000,356,-946,-11,74,-927,-97,315,1000,235,302,253,-938,516,-524,-1000,-1000,-698,1000,-153,-1000,624,-435,64,493,840,-136,574,-1000,-1000,934,658,-805,225,452,-521,-412,542,-79,-1000,-363,-951,-56,-936,1000,1000,-26,1000,652,597,-1000,188,-422,958,-200,-965,1000,-134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00367() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "configureRangeAxes():void",
            new int[]{623,337,456,1000,569,23,-862,347,1000,-422,715,-811,-358,-274,-1000,-23,-654,567,-271,400,-98,1000,-83,225,224,734,876,-622,227,448,-681,-414,-1000,529,-158,80,1000,-617,-714,1000,1000,235,489,-359,-592,7,630,-392,2,-58,-1000,400,-367,84,196,-381,96,659,84,-60,667,-750,857,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00368() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-149,23,-163,344,-751,532,484,506,919,359,-235,-1000,-1000,137,1000,944,199,321,109,229,-173,727,-1000,973,-33,-1000,609,-816,603,503,-187,2,-250,227,527,-385,653,-422,265,-148,596,-469,239,902,-82,-179,-1000,-929,604,-800,-644,907,-367,484,804,-242,763,623,-771,510,-888,-459,-1000,-240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00369() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{231,-1000,-441,1000,-1000,-71,808,1000,334,-79,-797,-356,67,-722,681,664,1000,1000,759,-22,-681,1000,-1000,91,1000,-1000,257,116,1000,457,-725,1000,-989,594,-254,-870,1000,-305,-1000,-215,19,-556,2,59,-1000,1000,-1000,-125,1000,1000,-592,1000,-927,1000,-1000,-1000,738,1000,-204,-501,209,-881,-1000,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00370() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-1000,1000,184,-809,261,703,-475,177,723,720,797,520,250,-342,-163,1000,-605,262,1000,621,-1000,544,891,590,-73,-465,-672,-378,-1000,-71,-500,-890,828,532,39,-324,-1000,-648,765,-218,235,204,-558,-7,-1000,-1000,1000,-1000,586,538,933,1000,942,-201,-11,1000,181,-757,1000,1000,-1000,150,1000,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00371() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-121,723,-413,857,480,-453,-181,107,275,162,1000,-331,-1000,-128,-432,-45,-206,739,55,-341,154,-201,-161,151,310,-82,-1000,907,-12,1000,-126,310,-774,205,-328,-324,-282,219,-302,180,-969,268,-386,-840,622,772,-313,857,-445,-660,557,655,-107,391,722,-404,535,189,-84,218,107,-599,-186,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00372() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{345,163,-167,1000,-782,619,-62,964,616,242,284,-331,-894,-755,20,589,340,739,759,6,-948,-837,-96,-116,310,-1000,-914,131,-12,652,-690,483,-93,608,-62,-331,-25,-817,-302,-37,-165,-177,-461,-630,65,559,-381,241,189,-660,460,358,473,859,574,-673,285,515,537,416,337,-571,-241,-401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00373() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-738,640,748,344,337,786,-275,354,-274,618,1000,1000,-209,267,-821,853,-201,-15,1000,304,1000,1000,1000,-610,-834,-507,-1000,1000,-517,1000,500,-262,-1000,-869,-1000,-614,-628,54,1000,-131,-1000,1000,-631,-758,259,-1000,661,1000,1000,510,1000,906,-148,305,-1000,798,-784,594,-9,1000,103,-1000,197,284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00374() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-671,1000,773,609,950,290,-366,34,-904,732,1000,51,122,375,-427,-122,422,-1000,-564,-720,1000,474,1000,305,-1000,-320,-1000,380,-914,1000,528,-901,-30,686,-296,-365,-751,-180,883,91,-1000,-266,302,-307,-529,-473,1000,350,606,-234,385,-262,-143,148,-2,387,-507,107,708,487,151,-1000,996,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00375() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-237,1000,-731,321,109,900,468,-318,1000,898,1000,232,89,-96,856,567,41,-584,109,214,307,1000,400,161,-1000,-614,-791,-803,495,429,-364,-1000,878,-284,242,407,-747,-651,806,-252,522,-483,-284,-394,-1000,-1000,354,-812,1000,174,637,-493,1000,-471,6,644,347,-375,-172,1000,297,-341,-641,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00376() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-495,826,849,-677,815,-901,-353,-169,623,450,-22,985,-324,401,-87,741,-322,-507,859,477,-1000,885,472,260,-1000,76,-1000,497,58,505,-204,-704,-515,-787,-933,-184,-63,864,946,888,800,-259,-14,269,372,-777,1000,-159,555,-111,-663,-548,1000,-286,210,884,144,635,-505,28,119,-676,-32,998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00377() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-166,1000,-96,-1000,488,320,-242,-164,890,572,545,51,250,-283,56,496,-339,-373,1000,111,-1000,620,730,547,-712,-558,-672,-313,-738,-71,-548,-630,670,686,703,-5,-951,-137,276,203,527,119,-913,-119,-824,-883,851,-150,198,300,673,-1000,1000,-242,184,1000,77,-515,926,1000,-789,-24,622,-149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00378() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-926,539,577,514,-546,1000,-521,-164,455,497,948,988,250,-1000,56,496,333,80,1000,28,-1000,-585,1000,547,-557,-1000,-611,-749,-1000,-180,-641,-482,858,258,-702,-1000,-1000,-1000,1000,-938,-581,119,-181,252,-1000,-342,1000,-1000,1000,776,1000,895,594,469,-309,1000,-51,-526,1000,1000,-1000,155,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00379() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-703,-318,669,723,-1000,666,1000,1000,61,116,-702,1000,-180,-956,584,1000,1000,256,1000,-264,-566,725,-100,420,449,-1000,1000,-62,771,22,-587,928,235,-267,-164,-1000,912,-813,687,-831,397,642,-252,966,-990,-192,-484,-673,640,417,-99,748,-1000,835,-122,91,338,1000,414,954,-1000,-295,-600,-995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00380() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{587,-180,-561,503,-142,204,-458,493,391,831,520,-232,-1000,746,-2,-1000,1000,1000,-583,647,-118,-260,-697,-619,368,652,-1000,1000,-43,1000,-178,-195,-1000,516,-289,-1000,1000,130,-1000,-390,-191,-1000,283,-1000,883,-130,-966,301,-520,-965,296,715,-840,586,-119,-42,-590,-227,-563,482,986,7,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00381() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-688,-675,79,1000,-641,-398,1000,631,244,141,-682,-442,-1000,-36,846,-33,1000,1000,456,429,163,-461,-1000,873,1000,-397,443,300,1000,400,-369,932,-1000,2,-36,-662,1000,158,-491,-146,-191,-451,-147,249,818,1000,-1000,-334,-490,-1000,-646,1000,-688,856,833,-1000,968,1000,-903,-572,-157,-1000,-1000,-134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00382() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{-695,805,-377,-266,-537,-479,-226,424,1000,620,-970,-321,-403,-242,1000,763,145,719,0,546,-1000,-186,-1000,1000,809,-1000,756,-1000,383,-1000,-898,-186,120,-1000,869,-302,497,-343,1000,213,1000,-981,-425,962,-511,109,-128,-1000,-83,-841,707,1000,360,118,971,1000,912,-138,620,-179,-1000,115,-170,-773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00383() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "datasetChanged(org.jfree.data.general.DatasetChangeEvent):void",
            new int[]{756,600,639,997,939,-14,764,873,-412,224,-527,-620,-619,-754,-756,888,696,320,-104,623,-858,614,678,98,458,-856,194,454,-274,940,-912,-916,287,922,-979,-2,975,996,-166,-215,310,-851,-990,756,-851,1,-473,861,988,128,50,313,-466,786,-975,-486,507,-828,-316,-174,707,-745,245,25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00384() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{150,-77,1000,-449,-231,-1000,-791,762,-724,1000,-551,132,436,-439,748,1000,870,-94,-1000,331,-114,405,432,-118,818,1000,1000,917,467,-458,531,9,1000,1000,272,-1000,-202,-567,1000,611,-651,225,427,-1000,-66,-465,-985,159,-129,-264,1000,-802,1000,76,186,1000,-935,-787,1000,-1000,-277,180,471,-257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00385() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-281,-25,29,250,-642,-487,-478,-1000,-636,24,676,-329,-1000,650,236,-399,99,-74,-1000,542,58,-442,-808,-206,-530,-665,-1000,-166,582,214,-605,-606,522,106,547,45,308,-148,531,-793,1000,-242,212,222,-713,419,448,504,-4,-123,-364,-80,-351,-1000,518,162,1000,133,358,961,-106,-65,-587,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00386() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{935,-150,-936,973,-401,155,-453,-983,-564,236,451,513,-20,96,-81,-735,-759,527,904,597,131,361,220,60,859,-348,-299,-982,900,-928,-862,-625,-795,-710,-222,482,889,651,-251,80,831,-919,997,51,-707,814,-937,666,-22,298,330,90,801,388,273,920,14,-145,656,736,-651,107,-354,806}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00387() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-1000,-571,-122,-213,-642,186,-991,79,-91,533,344,-1000,-171,417,236,1000,-562,6,403,-95,-166,-1000,-730,385,-242,304,-1000,-944,1000,1000,73,-296,-836,-361,107,499,-519,-588,1000,-42,537,17,212,-918,-748,243,-694,-1000,-628,87,-1000,32,-1000,157,1000,554,1000,499,-432,-1000,-712,-948,372,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00388() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{544,-803,-677,506,875,912,-40,-536,921,747,-811,885,496,-275,-120,-658,-768,102,99,63,-448,33,103,398,192,712,238,28,-442,165,-436,589,214,374,-855,-848,420,-49,-465,-180,-211,-787,-805,-377,-167,133,-691,808,492,449,827,-317,537,941,-621,206,-807,-193,-813,701,255,-55,95,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00389() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{242,-606,128,-722,-33,294,310,-3,-1000,70,-1000,142,564,572,139,910,173,635,-682,-669,391,758,687,303,753,975,1000,28,240,-514,334,-12,173,-24,36,-795,-554,657,1000,33,-980,865,93,-1000,317,1000,522,770,78,719,1000,-467,1000,145,-425,412,-17,-934,-523,-1000,1000,1000,445,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00390() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-464,88,978,845,548,-998,-967,-230,497,-865,537,-653,-534,505,76,777,51,252,-1000,-45,165,-1000,-286,371,-623,468,-1000,-1000,1000,220,-363,-649,-1000,-137,82,932,-411,-599,743,-909,1000,-609,1000,935,-431,-174,152,-1000,-642,-79,-911,673,-267,-575,147,700,729,962,380,21,-186,-3,-159,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00391() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{369,26,739,415,-1000,223,-48,-521,64,-284,418,380,-808,609,-68,-662,167,351,707,431,304,405,-128,-176,-400,-946,-1000,-394,282,-815,-258,-469,-639,287,-55,329,1000,-33,-499,611,442,-356,427,239,-600,477,985,-265,-115,-353,-694,309,-13,-299,625,256,782,-102,1000,-1000,-307,421,-346,-120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00392() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{1000,-349,788,717,-1000,469,-1000,224,53,489,666,1000,-578,1000,-430,-794,1000,987,992,511,355,1000,-123,-817,1000,-925,-389,-335,219,-815,-247,-904,-767,-606,96,1000,1000,-156,-402,23,1000,-1000,341,715,-744,1000,985,79,-1000,-946,-744,821,1000,168,625,1000,123,-205,1000,998,-438,421,162,972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00393() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{953,-281,-557,1000,34,-350,-1000,-6,-307,1000,598,768,-130,848,-144,73,838,1000,1000,716,676,-572,143,-1000,1000,-951,-922,-1000,710,-540,-303,-537,-560,-781,-776,1000,942,-638,58,-445,1000,-1000,918,537,-569,-206,696,-228,-1000,-998,-996,1000,414,209,987,-78,-149,-417,-767,29,-188,-214,873,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00394() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-1000,247,1000,-496,304,-1000,-728,499,843,-1000,-28,-1000,-44,-733,593,873,1000,-437,-164,-234,-565,-818,-747,919,-23,-879,-143,11,975,1000,522,16,912,11,257,61,-1000,-892,1000,-107,-1000,810,1000,571,113,-147,202,-1000,-523,-84,-953,-194,-1000,1000,848,115,400,807,1000,-1000,655,-117,-163,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00395() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{505,472,-181,-473,625,411,313,-644,-898,1000,-1000,223,822,838,146,601,-286,187,-797,-706,-761,-121,106,-733,858,1000,910,737,423,-457,-141,-54,-678,-56,-376,-67,376,92,375,-1000,778,288,659,-901,698,-212,-1000,876,-65,202,1000,-722,39,-1000,783,375,-1000,-961,-1000,869,1000,286,199,771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00396() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{1000,-199,-677,1000,77,688,-104,228,49,-1000,-442,506,-995,274,270,373,229,1000,208,93,-74,1000,-183,1000,375,696,78,10,716,1000,378,-404,-945,1000,1000,21,315,-439,312,1000,481,-475,-113,574,-1000,1000,-165,257,234,-717,-69,-593,479,-690,-768,-204,255,-113,298,794,1000,-72,489,368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00397() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{482,-363,-1000,1000,-541,289,-601,-952,-699,-980,-625,935,-331,392,46,-281,-1000,617,-269,329,-54,-25,240,492,594,-872,387,-560,219,-1000,-532,-758,-585,-1000,-413,923,1000,-199,179,704,719,-1000,-589,-344,-1000,1000,-109,306,-398,-570,-553,-185,716,363,1000,339,-663,-1000,975,-55,-392,143,-67,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00398() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-445,51,-997,-1000,1000,652,303,-361,-1000,757,-85,381,219,1000,560,324,943,431,-491,331,-575,-564,-297,-1000,900,1000,1000,836,-913,-946,99,366,-233,-646,-990,135,-144,-552,-150,-1000,-359,264,839,-1000,519,300,-1000,1000,-345,995,1000,-452,-1000,-769,848,171,-1000,-155,-1000,856,832,1000,-177,441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00399() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "draw(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.awt.geom.Point2D,org.jfree.chart.plot.PlotState,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{1000,-433,-947,647,-373,775,-1000,-126,-11,1000,-698,1000,66,640,-46,223,-1000,1000,343,1000,18,712,583,-833,811,607,857,-100,-1000,-1000,81,37,975,-545,-472,1000,976,230,562,468,356,-1000,212,-1000,-338,225,700,1000,-1000,16,1000,900,1000,957,751,1000,-422,-371,-945,-763,89,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00400() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-1000,-606,969,-162,-1000,-586,472,334,200,583,-307,533,815,-19,145,-374,85,-1000,-84,-166,-714,-442,-316,284,927,-906,-118,-881,216,-776,-964,-651,904,-538,304,1000,590,-775,-650,283,201,397,441,1000,533,-269,587,1000,586,1000,-762,382,353,906,1000,-758,-149,979,-417,678,409,1000,583,-972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00401() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{1000,586,-161,215,-488,1000,-353,-953,-59,-922,-872,-605,-602,-709,-380,-793,-539,1000,945,674,1000,526,96,-670,-1000,863,-1000,338,1000,810,1000,1000,-5,555,71,-1000,112,1000,-693,625,-305,119,258,-840,-785,-295,-656,-1000,72,791,1000,-171,-63,-77,593,775,-19,512,207,-1000,-339,71,408,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00402() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{644,539,441,-124,-551,469,813,-93,327,-389,-236,-170,1000,-730,-1000,-535,-357,1000,463,-602,639,-265,359,-381,1000,-404,-856,579,649,416,-863,318,-85,120,352,-605,486,114,-805,-37,248,-87,682,-395,145,-1000,-495,-1000,352,-296,382,717,464,1000,287,-309,-184,440,338,-303,350,973,675,500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00403() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{281,437,98,-29,-612,409,-723,-336,38,-697,-846,-215,-731,-23,422,-729,-280,603,936,959,871,776,-825,-939,-895,929,-998,-978,567,-434,949,502,805,403,505,297,195,625,-765,703,298,176,-71,20,-559,217,455,-311,72,742,492,910,-281,681,913,467,-233,783,-785,-998,-331,71,115,961}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00404() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-401,934,188,25,-912,138,806,817,-334,732,-713,1000,812,-295,398,-751,1000,-1000,736,-971,730,-843,-267,495,14,-950,-198,-453,445,-111,-827,855,635,-424,-300,-47,629,-512,541,1000,-755,-878,265,1000,923,-452,1000,1000,4,630,-83,1000,-268,-256,-430,-293,-189,52,-1000,999,-264,981,506,650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00405() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{780,-994,-61,717,451,-560,845,-757,343,-87,-498,-565,-27,-491,-7,-371,189,-324,744,528,304,-769,-270,-265,-656,-25,-786,-292,810,407,-289,-170,362,536,66,-671,899,-42,-74,504,-417,32,248,880,567,-754,934,742,-609,683,230,800,329,-126,81,-313,335,248,-721,437,632,-79,-38,-872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00406() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{537,166,749,-184,-1000,14,71,220,-114,44,-414,771,216,246,316,-384,84,-1000,168,157,-173,-39,-558,-114,327,-319,-383,-921,329,-795,-364,-51,805,49,561,1000,446,-191,-243,157,458,-5,383,1000,679,-127,247,1000,602,1000,-614,91,320,771,-503,-214,-207,938,-392,174,369,1000,468,-372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00407() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{699,713,180,77,924,-211,-792,-614,-357,-364,959,497,-739,-835,-212,-869,-737,914,-246,774,780,428,860,-284,-925,25,-774,690,-762,5,178,901,-688,914,-455,-717,-823,606,742,-486,592,-454,-354,-468,-26,-970,-297,-936,759,-170,224,49,439,-861,-562,720,782,-369,451,604,116,-45,-954,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00408() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{1000,147,662,816,-187,-1000,-956,-21,1000,-764,497,-509,1000,327,-1000,1000,177,1000,1000,-317,-5,-131,-237,-1000,-1000,972,-117,1000,-35,303,-264,-206,-175,229,-827,-1000,561,-1000,-486,1000,-101,30,60,310,-118,-1000,130,670,423,-1000,-1000,-1000,905,-482,-1000,177,557,-1000,-1000,56,1000,480,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00409() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-370,448,324,-134,237,984,-775,-984,-1000,-571,-1000,-215,-669,-746,1000,-1000,-1000,-608,852,1000,1000,1000,-878,-901,-550,1000,-569,-1000,1000,1000,1000,1000,1000,-419,-1000,782,175,1000,-280,1000,-852,853,-530,724,-1000,438,-296,300,412,1000,-366,1000,-320,-1000,1000,1000,-125,1000,-749,-1000,-1000,-737,639,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00410() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{427,961,728,-298,-528,1000,274,214,-871,-929,-636,-102,-155,-111,-68,-282,127,-1,585,630,1000,-212,510,-623,-1000,73,-1000,467,1000,939,332,95,-375,1000,33,-529,377,-1000,-842,414,-235,-770,781,455,-118,-642,-1000,-744,665,-59,766,180,729,-494,865,684,-581,369,1000,679,708,987,393,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00411() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-253,-1000,-152,-156,-516,94,1000,392,887,1000,-556,443,1000,-1000,-928,-618,760,576,1000,-1000,240,-1000,946,769,-160,-1000,-529,907,1000,1000,1000,804,1000,-1000,-716,-1000,870,-523,-97,822,-1000,338,846,140,671,-1000,127,-131,-350,-328,1000,1000,476,577,561,-739,-572,-342,-1000,1000,224,-1000,643,-370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00412() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{433,492,-207,441,0,448,75,-119,-391,-287,-573,79,212,-848,-978,-864,-398,256,669,-93,836,272,4,-609,327,67,-691,90,423,436,-268,676,377,-543,-926,-707,37,642,-223,482,17,-21,-345,635,-460,-289,335,-409,699,-627,-1000,865,472,459,461,457,361,1000,-161,-513,-59,313,190,678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00413() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{670,-88,134,929,-548,-608,-285,282,322,-398,452,-628,915,1000,-837,842,-411,253,480,-635,-72,-352,-602,-232,1000,536,-422,224,507,-772,479,-401,130,-827,-1000,-931,480,-174,-1000,-22,-792,-1000,-198,-346,200,-1000,-94,-1000,-1000,-258,-998,-882,700,994,-730,113,147,-1000,-385,-119,649,1000,1000,-770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00414() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-152,-148,749,-120,-1000,-495,-760,455,996,-203,-1000,160,-1000,681,295,-741,1000,715,901,1000,1000,416,-134,-603,-990,255,-1000,40,187,177,269,25,370,1000,-597,-598,250,312,262,1000,441,1000,-747,1000,-83,1000,1000,1000,1000,1000,1000,1000,453,-511,1000,875,-263,1000,-76,-121,-712,-636,451,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00415() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawAnnotations(java.awt.Graphics2D,java.awt.geom.Rectangle2D,org.jfree.chart.plot.PlotRenderingInfo):void",
            new int[]{-351,1000,394,-150,-830,1000,-806,-165,-483,-1000,-738,692,-996,500,595,-539,-68,-1000,830,931,1000,850,-1000,-1000,-1000,1000,-1000,-1000,582,-672,1000,1000,668,1000,892,1000,144,947,-1000,452,745,-483,118,754,371,210,-135,939,405,820,45,27,28,548,1000,813,-297,818,-520,-1000,27,1000,165,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00416() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{495,-555,-217,305,416,701,-222,-701,-643,-310,796,-139,-267,535,167,158,-735,-966,422,-986,8,-310,-631,-708,896,-246,565,56,-395,-782,560,569,-175,-294,156,981,-47,-714,292,-567,615,372,-806,-799,-551,104,-103,531,153,842,404,-188,898,740,354,-102,930,783,331,-471,-578,-901,698,-950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00417() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{-93,358,969,-785,-512,-821,500,888,322,430,-649,412,126,-66,838,-295,-183,-335,-300,257,-518,-501,480,1000,-1000,-587,-447,-1000,225,1000,-506,-1000,153,-262,43,-1000,-579,89,-1000,572,659,-1000,-953,-114,1000,1000,-141,-299,-931,-586,-1000,-765,-182,-498,-634,-151,-598,-79,1000,-795,-20,-589,-697,721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00418() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{59,333,-531,-688,739,834,500,-245,-16,189,739,99,1000,-941,-137,61,-85,660,-218,-593,927,420,543,-1000,1000,-659,-842,861,53,651,895,-448,-1000,-214,-1000,-714,216,1000,1000,-418,-1000,493,1000,1000,-1000,84,-1000,-833,-342,323,1000,102,-182,-216,644,-656,1000,1000,-1000,898,-541,-1000,825,-395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00419() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{-514,735,626,463,-172,-241,396,-937,1000,-353,-743,431,1000,-755,150,119,180,611,-15,-114,-268,-627,1000,-1000,-240,-719,-1000,284,470,1000,-1000,-6,-1000,22,-827,1000,-86,-1000,-109,-293,-870,597,1000,270,200,-727,-1000,1000,432,-1000,43,486,1000,-427,-1000,684,-141,1000,-778,1000,614,-262,487,-257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00420() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{480,-171,-269,254,-40,704,335,-480,-145,-961,786,-108,228,958,263,-401,-251,-1000,998,-1000,-431,352,-820,-909,1000,-26,441,459,-301,-700,421,361,-421,160,-517,723,-200,-914,-169,34,-20,986,-621,-615,-443,46,-14,525,74,893,555,687,1000,700,988,-267,843,1000,-603,-448,-729,-1000,735,-999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00421() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{1000,571,-885,325,-946,-332,736,-180,-46,1000,-1000,-431,-373,436,273,1000,-153,-483,-207,-188,11,-82,-184,-660,-100,-563,-708,5,-1000,1000,-123,-1000,144,-40,-504,281,-67,132,-882,-704,1000,-624,-952,-1000,249,1000,-34,1000,242,628,-1000,-80,1000,245,681,-656,389,-79,546,283,-413,576,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00422() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{253,979,-1000,-202,-1,-196,-66,-157,798,492,1000,-799,-178,-37,57,1000,668,-91,-145,313,-400,-481,-348,139,85,-131,-1000,880,-1000,1000,-1000,-316,-1000,-230,196,9,-490,-156,-952,-83,272,-166,-112,-691,1000,1000,-251,473,-375,-88,-342,138,1000,282,-388,-1000,527,-109,-397,-353,-385,113,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00423() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{834,173,10,90,123,341,613,85,19,310,739,-232,-36,321,-224,-615,-161,-392,-48,331,647,-324,520,197,1000,-297,31,391,-296,5,-60,179,-57,-755,-72,49,-190,517,217,-501,-159,-427,901,938,173,-86,-297,-714,-1000,703,283,-206,132,307,325,-656,791,-79,136,-1000,-1000,-519,280,-296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00424() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{385,312,-546,-570,1000,1000,-190,34,-770,278,1000,778,839,-292,145,-37,-633,-540,-269,564,1000,-440,-1000,1000,940,-699,-1000,763,-1000,-749,565,95,-1000,-815,1000,-1000,881,400,252,68,-1000,-760,189,1000,-1000,283,-158,-1000,-1000,-484,999,-445,-732,-1000,-41,-930,686,-567,622,-1000,-969,-856,613,-312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00425() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{402,62,319,294,450,814,-714,-596,-473,-219,-356,668,-240,216,226,97,-1000,-941,20,-594,608,-1000,-404,-1000,933,-71,1000,514,-1000,15,7,950,-175,-422,488,854,-388,-578,-285,-745,615,-300,-806,-799,-594,-672,76,1000,-475,858,151,-811,-132,647,29,-572,1000,467,1000,-1000,-1000,-901,-282,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00426() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{924,231,-1000,-712,-396,-305,1000,768,-117,582,1000,227,-316,160,167,424,354,-344,165,44,-139,440,-786,-708,964,-479,1000,139,-670,-125,250,-1000,289,87,-325,981,270,385,-808,632,-492,372,-1000,-321,1000,1000,60,-223,-683,1000,606,-202,898,-468,1000,-1000,271,-484,753,-858,-1000,-599,-86,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00427() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{1000,47,-21,460,-1000,-1000,524,958,-101,587,-1000,-600,-329,562,206,994,-157,-412,-336,-824,-882,87,-640,-209,-998,-127,-195,-936,-953,928,252,-952,470,-515,388,-136,404,-640,-534,-244,652,-114,-1000,-1000,469,1000,202,570,168,419,-1000,-462,961,-372,594,-1000,394,-1000,780,-1000,-328,122,564,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00428() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{141,-1000,-481,161,118,376,-1000,-839,-1000,-611,704,73,-413,56,634,-434,-1000,-1000,737,-834,-618,-605,-1000,-197,251,-212,1000,-903,-840,-784,784,230,111,198,469,898,37,-329,-40,-371,1000,77,-1000,-1000,-976,675,151,887,782,540,-406,-555,1000,985,521,691,673,314,1000,589,-252,-181,80,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00429() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{498,697,-735,360,-731,-361,920,-559,-611,534,708,496,-651,46,318,1000,-858,-1000,1000,-134,-1000,-1000,-817,-896,479,-143,567,-429,-1000,803,-214,-152,230,28,-520,423,-492,-411,-926,122,696,-345,-1000,-1000,695,652,305,1000,217,1000,-709,-1000,767,1000,-203,-354,908,-314,368,171,-559,223,-604,-119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00430() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{-41,724,-217,-64,971,701,1000,344,1000,72,-912,-505,940,535,-35,158,749,312,-807,171,496,1000,-631,387,726,-626,565,1000,569,-782,-704,-596,-1000,-388,-373,-498,154,577,-132,-567,-1000,290,1000,1000,-18,-290,-1000,-1000,-1000,-636,1000,756,898,-1000,327,-901,-232,683,-1000,-313,-823,-1000,787,-425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00431() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawBackground(java.awt.Graphics2D,java.awt.geom.Rectangle2D):void",
            new int[]{483,-1000,493,305,437,-1000,-222,605,-916,-709,547,400,-430,-1000,657,-555,-797,155,-240,1000,276,-168,-659,-296,1000,-537,785,75,-211,-266,603,-848,1000,-271,1000,-1000,-448,-986,-1000,90,-215,70,-1000,-483,-567,838,1000,1000,-413,-590,-665,-102,246,587,248,1000,924,-224,1000,-557,-400,707,-1000,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00432() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-80,182,138,302,910,554,-541,1000,-869,-394,541,1000,524,1000,-1000,-814,-1000,128,-1000,-1000,1000,-1000,234,-119,-1000,517,829,1000,202,-530,-1000,-896,311,-352,-1000,-36,507,-335,-772,-864,-1000,-1000,1000,790,-321,199,-643,1000,1000,-1000,-999,-1000,-825,1000,-628,-776,1000,427,90,-154,594,-1000,-1000,-786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00433() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{71,247,110,194,379,-691,-554,508,240,1000,12,426,231,12,434,541,-211,-1000,24,-203,500,-371,542,1000,-471,258,250,-156,-444,453,112,-256,888,-246,-144,-1000,-271,218,-1000,308,-738,419,-68,468,1000,-705,52,-308,-1000,251,505,744,-519,583,-261,-564,749,441,-638,844,-1000,297,789,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00434() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{631,-174,819,288,536,522,-141,313,537,380,244,431,167,385,126,255,-562,-658,-114,-82,625,-114,568,1000,39,62,-207,-33,-969,621,492,289,742,-1000,285,-1000,-894,1000,-764,604,-279,-15,-83,774,894,-176,-415,-383,-1000,14,1000,1000,771,248,462,1000,749,671,-1000,606,-1000,757,1000,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00435() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-1000,576,-322,229,-295,252,555,565,683,-1000,10,-169,-352,-537,560,-49,1000,744,15,-1000,-686,-217,-865,442,400,-1000,691,374,1000,1000,144,828,686,-720,-537,-1000,1000,-559,873,1000,506,-1000,-645,303,-1000,-864,1000,36,995,714,-1000,-1000,887,-752,154,1000,305,-1000,505,-1000,620,678,-1000,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00436() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-843,1000,-72,444,-141,242,711,508,216,-327,188,-375,231,-1000,332,-575,198,722,1000,-534,782,733,542,596,-431,-484,1000,1000,142,286,-93,-432,1000,-603,-237,-993,-416,712,1000,489,-738,-648,-68,468,995,-1000,387,-117,585,789,-210,-1000,776,583,389,-564,-855,-595,994,844,-767,472,792,935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00437() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{207,-651,1000,-375,69,133,-741,712,171,-417,286,841,21,625,614,-349,-1000,-612,-465,-872,-164,-492,27,460,-287,-272,227,-61,417,391,-245,415,370,-319,-438,106,924,-449,-1000,482,-1000,-919,390,215,-775,-592,511,563,102,-49,-227,258,-578,427,121,238,139,671,139,-507,-464,127,-301,337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00438() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{56,-919,726,664,-900,243,1000,-19,1000,-534,-133,-106,-811,-548,1000,264,1000,152,607,-507,-1000,1000,-310,166,1000,-1000,-683,-1000,-26,1000,1000,1000,1000,506,881,-920,-655,1000,319,1000,1000,-320,-1000,-1000,583,-947,1000,-860,-779,1000,320,1000,1000,-1000,1000,1000,-1000,-1000,-383,-364,-647,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00439() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-329,1000,379,312,449,1000,658,193,476,180,666,323,-337,201,-301,-898,-1000,932,257,-548,21,68,1000,509,334,-68,-390,726,-627,411,-1000,327,1000,1000,-673,-1000,-522,218,1000,-441,-690,-1000,-193,381,-182,-1000,16,715,1000,-149,-215,-1000,743,852,-561,207,-255,671,83,-493,376,-271,122,-135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00440() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-729,-25,888,930,-193,706,567,389,230,-1000,19,621,54,-85,-69,-473,549,1000,1000,-1000,522,291,-90,-190,797,-1000,566,1000,325,972,-318,1,860,-1000,140,-806,-231,1000,1000,864,892,-1000,-578,-975,-276,-556,31,411,1000,942,-768,-833,1000,-1000,1000,647,-1000,-770,165,-445,56,971,-236,708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00441() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{294,-561,1000,172,453,922,1000,-30,1000,-866,645,-1000,-729,-960,1000,-1000,54,-515,-673,-528,-672,-1000,-450,1000,888,-1000,1,-354,-1000,910,539,1000,1000,-1000,291,-235,-1000,415,194,1000,-1000,-189,-930,733,823,-1000,1000,353,-1000,731,1000,960,1000,273,-539,1000,-969,-652,772,232,-803,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00442() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{1000,-684,863,285,-520,166,1000,86,173,992,-156,646,231,-1000,589,281,613,-433,134,243,522,587,-457,284,784,-402,242,-846,490,286,1000,470,642,281,855,-36,-739,1000,-935,806,-54,-524,-546,-580,676,-559,196,-566,-148,789,-388,1000,517,-209,550,1000,-619,-181,503,-84,-170,741,611,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00443() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{752,-822,-262,-338,1000,-452,-1000,710,-109,1000,-175,273,-254,-151,-878,567,-1000,-1000,-725,-149,260,-970,248,372,-970,650,-207,-1000,-775,-595,684,-112,-515,-448,-415,-606,-805,460,-1000,-170,-1000,-975,1000,1000,470,1000,-589,-165,-1000,-828,1000,1000,-629,1000,-254,0,1000,1000,-944,1000,-450,-483,563,-812}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00444() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-440,-725,-268,745,-320,-1000,89,442,-401,-1000,484,88,-293,175,1000,-240,1000,248,-449,-1000,-965,-452,-1000,250,1000,-1000,740,242,1000,76,1000,1000,571,61,-802,-121,965,-288,236,1000,54,-786,-152,57,-938,119,1000,410,-154,552,-1000,205,-301,-226,-337,870,-703,-1000,1000,-333,1000,522,-1000,907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00445() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{644,-173,-1000,374,284,-545,606,344,612,297,870,-837,-708,-816,538,315,952,201,1000,-439,36,362,-1000,417,-164,-890,1000,-953,44,-71,407,188,71,233,-96,378,-982,61,272,371,-943,-914,-87,-675,31,-303,54,-468,-900,904,-723,152,328,473,687,425,-255,-217,431,342,465,-130,438,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00446() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{752,-280,-492,288,819,-62,-288,661,261,877,456,317,17,441,-910,966,-1000,-596,179,-82,985,-114,84,210,-616,631,525,391,-964,-196,107,-763,75,-1000,285,-829,-1000,1000,-926,-170,-320,-200,507,117,1000,349,-886,-667,-1000,-110,1000,617,764,405,1000,0,1000,1000,-1000,1000,-1000,-251,1000,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00447() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawDomainTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-15,542,244,305,352,775,991,30,935,-356,998,-663,-572,-739,470,-925,-145,268,604,-335,-377,618,-326,832,184,-690,233,-359,-427,380,-215,896,968,762,-240,-814,-993,298,925,416,-918,-869,-553,531,57,-998,783,335,-52,542,233,-210,992,565,-394,975,-793,-422,752,46,82,245,894,907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00448() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{775,1000,-7,303,-437,253,871,129,-865,36,436,-702,1000,-778,-552,-139,-431,-85,308,-400,272,-271,-97,-1000,451,-546,253,707,-1000,279,539,-370,863,904,-147,835,-160,-1000,-400,505,483,459,577,201,828,49,580,-131,-1000,-465,-751,187,-903,-1000,686,211,-539,1000,-866,-917,-545,12,951,-661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00449() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{93,-170,-301,226,5,-540,1000,-346,-232,818,436,465,110,-576,138,-829,512,-1000,10,1000,-1000,-630,-1000,-708,146,-1000,946,986,-796,-1000,-747,595,7,1000,-1000,699,-189,-1000,1000,370,-601,1000,262,-1000,276,-228,851,248,-756,-1000,5,187,497,-68,293,763,147,704,886,-1000,708,1000,-1000,739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00450() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{1000,1000,-571,-298,1000,212,399,-847,65,-185,-550,-1000,64,861,-1000,203,769,-24,813,-1000,1000,457,378,469,1000,667,-158,-576,-565,1000,1000,-1000,1000,276,975,772,242,-1000,-1000,1000,608,-261,-46,670,-109,61,-393,-361,-12,1000,-1000,165,-1000,449,1000,-516,76,1000,-1000,1000,-1000,-181,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00451() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{854,-1000,-451,845,87,-723,1000,483,-1000,691,58,86,186,-689,-260,-296,-957,-884,149,1000,300,-426,-1000,116,582,-1000,391,1000,-106,-1000,-169,634,663,1000,-1000,820,-982,-500,1000,-178,250,1000,353,-436,299,175,1000,458,9,-1000,111,533,1000,90,1000,73,-1000,-334,1000,-1000,684,111,-1000,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00452() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-761,871,-11,-1000,417,-248,1000,-227,-336,719,765,-920,780,-731,-686,583,454,-520,967,400,-371,-1000,-1000,-1000,248,-1000,155,1000,-1000,-201,931,-604,59,1000,-860,1000,-1000,-762,146,689,487,488,193,-598,42,-894,302,-439,-1000,-794,-671,-466,-428,-1000,1000,853,1000,1000,980,-1000,-655,1000,25,92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00453() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{777,-1000,-1,957,-749,-966,-246,769,-1000,-704,-793,-839,1000,918,-837,-1000,-1000,522,762,-1000,183,308,-988,-818,767,321,-536,176,-1000,741,851,541,-635,1000,-95,-1000,676,-1000,1000,-412,521,437,629,969,780,749,645,1000,588,-364,-1000,1000,-912,-582,-503,-819,-1000,-858,804,1000,-793,-366,385,614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00454() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{775,-1000,339,-619,939,-607,684,-1000,433,784,-530,1000,-1000,-260,961,-307,1000,-577,530,1000,-139,770,-612,1000,87,-1000,1000,477,1000,-941,-226,-894,796,450,-214,317,-118,-1000,304,100,982,737,547,-1000,-292,731,934,71,-1000,682,1000,392,726,1000,1000,779,729,668,-719,-878,466,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00455() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-100,786,-592,643,-235,-324,-698,92,-329,-376,-423,-740,345,260,-731,-254,-353,-641,-815,47,136,935,412,313,-894,520,-214,-695,-251,234,537,-221,803,563,401,-690,-145,-346,-485,-973,327,360,592,657,106,274,59,857,743,563,-880,849,223,-468,19,-899,-600,530,-801,950,371,-902,-531,-100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00456() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-87,-951,43,264,-237,511,-1000,654,-979,176,-815,794,-2,-569,-152,901,-1000,224,-1000,398,1000,1000,1000,88,-33,885,297,-593,923,48,262,-225,199,-1000,920,-1000,1000,1000,-925,-1000,340,-674,-118,519,-610,-282,1000,797,465,535,388,977,-30,-806,512,-1000,-1000,-948,-797,23,636,-1000,82,676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00457() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{105,445,298,-251,62,258,-25,-776,-373,-885,-388,-466,450,215,478,132,279,-279,-521,-225,-947,-36,-105,-781,1000,-114,71,138,59,-532,-461,-417,164,-376,-652,-388,99,-656,156,-800,-244,-633,522,666,869,-44,-575,-875,-848,97,-1000,1000,-1000,196,-699,889,96,646,356,-525,46,172,-589,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00458() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{1000,-489,-183,143,936,486,519,-609,200,549,-1000,-654,23,693,-846,-332,1000,387,520,143,1000,259,168,863,621,-374,-377,-343,443,302,-404,-953,1000,635,101,210,-640,-660,-219,58,703,269,-395,-142,-623,717,22,329,452,716,-710,126,31,-1000,1000,-319,-13,37,-1000,1000,-1000,808,951,257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00459() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{1000,-143,542,351,-237,424,-541,182,-602,751,-254,-79,23,-1000,-947,1000,-364,-326,553,339,-652,82,904,-1000,-515,1000,-89,-553,-164,-375,-946,-1000,675,-543,760,-756,-405,806,433,-566,378,854,-881,519,-919,-282,479,617,1000,-624,-496,1000,377,-852,-1000,-1000,-891,-559,-1000,423,606,-1000,50,887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00460() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-759,92,-671,1000,-1000,-1000,-488,1000,-1000,439,-281,-786,1000,228,-904,-262,-730,653,203,-281,270,-1000,1000,-513,711,1000,-40,113,-507,1000,-557,602,-982,549,-554,835,-257,1000,-1000,-1000,146,272,-569,1000,-207,42,761,1000,1000,-328,-537,542,-599,-672,-1000,-1000,-1000,-1000,1000,376,754,-428,1000,75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00461() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-79,994,-71,660,1000,-935,166,482,99,-974,-1000,-977,-493,-824,32,-546,298,-743,-1000,199,-955,88,1000,-1000,-141,-922,-63,-382,516,246,-607,-53,-214,1000,-629,-391,-1000,-594,-549,-1000,-244,-465,774,348,639,-542,-717,700,-726,88,-393,449,-425,852,-517,592,-192,1000,-67,-277,1000,468,-996,589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00462() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{1000,-1000,-451,353,619,-78,576,-147,-658,904,-598,783,-1000,576,-559,340,513,-216,-160,81,-44,128,903,1000,601,-206,1000,571,-106,-952,-1000,-521,1000,160,-276,395,647,100,213,380,510,1000,-305,-392,-428,191,1000,54,-516,-217,484,895,796,-361,1000,-404,220,1000,-906,-1000,98,262,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00463() {
        org.junit.Assert.assertEquals("VOID|getSeriesCount=java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "drawRangeTickBands(java.awt.Graphics2D,java.awt.geom.Rectangle2D,java.util.List):void",
            new int[]{-559,-224,-451,72,-90,-635,338,168,219,199,-473,86,225,576,318,-803,-68,634,-361,81,300,-513,100,1000,935,-206,220,-13,-106,435,935,743,-270,694,-655,820,150,-580,-877,380,250,-40,745,-92,885,135,201,376,9,463,586,-1000,-501,-163,-451,402,220,-334,718,-378,168,111,552,-181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00464() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,784,-231,-689,179,890,-930,-3,1000,186,-898,1000,-210,-625,-830,634,46,138,-911,-1000,1000,-1000,-220,1000,580,559,87,700,582,399,49,-438,1000,1000,94,-770,1000,660,855,911,944,1000,1000,-1000,222,-202,-373,180,1000,603,132,-701,1000,1000,-197,1000,155,-136,-922,-562,788,1000,-864,-640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00465() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{890,-616,-386,-640,463,107,-105,-511,-212,1000,-952,478,-136,-780,-1000,-704,-65,-673,-1000,-660,575,-1000,-1000,329,867,-537,-11,-386,1000,-312,1000,-145,-110,150,839,-1000,-400,574,309,274,178,174,-208,-175,307,150,-665,1000,761,63,156,-1000,1000,430,-658,815,355,-538,-377,132,-114,498,495,-28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00466() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{934,207,-161,894,-859,955,-941,-1000,788,941,818,960,540,-617,-913,-303,-371,-42,-477,-908,1000,-1000,373,209,-1000,-1000,68,179,947,-786,101,-664,1000,799,1000,-1000,-891,844,-366,485,131,-787,-631,-960,38,-1000,-108,348,1000,-53,930,1000,1000,1000,-818,1000,1000,-303,-78,781,-776,1000,-754,-289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00467() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{691,675,733,-727,49,347,1000,-431,498,-1000,539,-560,-1000,-960,1000,-423,299,683,-140,-127,-311,1000,720,321,182,-863,248,183,-491,810,-319,-438,681,432,-1000,332,235,401,689,383,337,-893,180,1000,-317,1000,-377,-1000,202,172,-768,-978,-1000,833,375,-487,-149,762,-945,-806,1000,705,-741,524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00468() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,-616,224,-988,-3,-184,7,-612,-354,834,-383,228,-136,-818,-913,-1000,323,152,-839,-340,575,-997,-1000,384,1000,-1000,-237,-384,270,-577,1000,-145,-110,533,233,-971,235,280,825,274,280,-246,180,1000,189,1000,-809,1000,719,103,156,-1000,968,349,375,683,425,-319,-801,132,-114,713,495,524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00469() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{-255,-1000,-117,155,1000,-1000,-242,1000,312,-1000,-936,287,1000,-829,1000,-449,-283,-1000,-1000,-733,-54,-1000,1000,632,-1000,1000,-707,179,1000,-934,598,-114,-792,-1000,-508,-346,-413,987,-1000,1000,131,77,-362,-305,674,-847,664,1000,1000,-268,553,1000,900,566,-789,-1000,-725,-488,1000,129,702,131,1000,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00470() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,400,-886,-169,-229,510,-616,289,729,500,-298,338,-291,50,888,89,-727,-1000,-217,-1000,404,1000,593,595,-764,1000,560,453,532,-450,-438,-11,586,-56,-644,240,928,-485,-44,384,-176,282,842,-919,-216,-628,553,99,469,468,-9,-241,808,602,-230,612,402,262,696,-785,-226,528,-400,-526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00471() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{844,-109,102,-181,952,982,-131,-295,600,480,-619,1000,621,-1000,-1000,-302,-50,762,-1000,-1000,925,-1000,-1000,1000,1000,-752,587,333,1000,-21,369,-974,1000,257,785,-825,88,1000,-97,1000,1000,1000,767,-478,669,-442,-1000,1000,961,-20,1000,421,945,935,-692,760,513,198,-878,-51,217,1000,-595,-358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00472() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{-960,-928,428,-767,-217,383,-259,-329,498,-510,-32,-1,738,650,-3,-231,346,-541,-91,-127,564,-1000,215,-107,-714,-478,-775,42,999,-557,-336,-438,-865,-233,891,342,-1000,-680,483,-121,-807,-1000,-454,370,379,162,-377,227,-439,-175,-1000,97,818,595,128,-777,1000,-534,1000,615,30,175,-40,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00473() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,1000,229,142,1000,489,-1000,738,45,-58,331,129,210,-1000,-471,988,935,-101,-875,-1000,-371,-1000,-538,1000,608,748,400,1000,1000,1000,-255,531,294,1000,-507,-922,1000,544,487,1000,1000,1000,464,-1000,679,761,544,-366,-32,948,1000,-408,185,1000,541,1000,-114,257,1000,-1000,740,1000,-754,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00474() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{712,-1000,-668,-18,1000,-890,-616,693,580,-200,-1000,338,485,-940,965,-144,-587,-781,-1000,-1000,404,-549,910,1000,-1000,1000,56,4,1000,-226,678,290,-260,-1000,-394,-521,8,1000,-1000,863,-181,677,432,-806,-163,-696,521,1000,1000,194,933,1000,938,602,-818,-198,-431,64,768,-239,485,266,1000,-433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00475() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{-203,897,-587,-632,-556,-10,984,-151,-1000,1000,-1000,-560,-368,656,698,-423,299,909,-324,-344,-285,926,-149,259,928,-52,1000,-1000,-51,-457,562,78,322,-677,-1000,915,728,604,-961,-1000,-1000,-328,-371,878,-379,-867,-1000,-505,-353,548,-768,1000,-225,-538,375,-364,270,-674,-160,-806,524,-678,-572,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00476() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{568,1000,-456,-188,-275,1000,841,-449,-270,762,539,173,-1000,917,338,-111,-691,-601,771,-1000,-1000,1000,629,809,-72,385,248,-873,-491,-582,-1000,-438,1000,432,-132,-119,74,401,21,-1000,-126,1000,262,400,939,-401,-640,-1000,-941,835,-951,-1000,-954,833,812,-487,532,-185,685,-806,1000,391,-1000,524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00477() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,-984,663,288,-219,854,-453,-236,1000,-1000,437,65,-573,215,1000,519,307,1000,-1000,-354,455,493,-959,666,410,-93,424,1000,-255,1000,-1000,-1000,429,162,250,1000,1000,-364,35,459,894,1000,1000,-823,-407,-8,-146,-379,-453,546,-70,187,549,243,196,857,638,-978,-160,-970,1000,574,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00478() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,1000,258,655,-807,964,1000,-1000,-393,155,22,445,-1000,-10,367,536,-673,1000,1000,-1000,-636,1000,-430,-682,197,474,378,263,-1000,777,-1000,-1000,1000,1000,-1000,-623,1000,-313,164,-302,1000,1000,372,-329,99,73,-543,474,-1000,503,-325,-1000,-1000,1000,1000,1000,593,1000,463,463,-1000,1000,-1000,-125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00479() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "equals(java.lang.Object):boolean",
            new int[]{1000,-924,-221,488,757,698,-1000,-3,-380,-170,-566,1000,218,-538,-1000,770,-95,-143,-553,-1000,-82,-1000,-1000,519,573,1000,548,-181,508,712,-742,1000,757,1000,240,-902,949,609,336,1000,1000,1000,956,-1000,1000,-24,-322,211,474,686,-336,-662,653,704,-75,1000,150,-578,-143,171,-950,1000,-902,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00480() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{-19,-704,-176,-26,-467,120,785,-757,182,222,159,-545,841,-1000,506,-1000,-767,-31,874,888,384,1000,618,1000,111,-581,-227,599,1000,925,-754,-1000,482,-1000,-840,328,1000,-658,1000,1000,996,-121,458,725,496,-1000,-1000,-515,345,-260,-707,912,-61,-1000,-2,-1000,401,258,-204,-1000,914,739,1000,723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00481() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{286,-1000,-346,1000,-3,86,785,-703,-389,1000,363,-780,404,-885,963,116,-1000,410,1000,1000,-648,1000,-782,402,94,-1000,-841,-801,650,-265,-535,-1000,605,-1000,531,383,138,491,1000,-95,1000,1000,1000,865,-904,400,268,265,1000,96,-69,-347,-1000,-1000,-247,-1000,1000,-555,-879,-261,851,-74,1000,-677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00482() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{858,149,-604,714,844,-711,520,259,-970,839,-385,438,-658,-7,-226,494,-606,-32,-278,400,160,-27,162,-565,-143,449,-205,-223,-395,870,-780,633,-511,-750,-272,-699,693,875,-140,-7,542,805,-472,-752,472,159,-518,349,251,356,955,258,-953,85,432,-718,-594,-952,-350,-48,-421,267,-798,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00483() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{1000,-1000,-371,504,7,-96,746,246,1000,-921,-772,262,1000,-312,60,-1000,-327,-51,1000,852,170,1000,1000,-210,464,-812,-445,721,229,464,-226,-1000,1000,-543,-984,-486,175,385,1000,-480,1000,-1000,46,254,555,-576,-721,609,222,-392,633,930,-494,-1000,-384,-221,1000,-1000,-585,-1000,-304,406,1000,683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00484() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{915,1000,-41,-426,460,707,1000,-232,-442,931,-281,-180,1000,-1000,-836,-783,780,-911,1000,461,1000,-123,1000,416,1000,1000,291,1000,963,1000,-982,236,389,66,-1000,642,1000,-1000,1000,1000,507,-1000,-134,-593,1000,-1000,-1000,711,-1000,-18,-362,1000,1000,-1000,741,482,-1000,175,766,-1000,-913,992,263,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00485() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{265,-1000,-531,42,-247,1000,1000,-830,-919,805,1000,-228,1000,-782,-817,-1000,-329,-394,866,1000,1000,280,253,1000,1000,-909,-459,319,1000,908,-1000,-1000,-354,502,-302,-702,1000,-595,1000,1000,1000,-1000,623,153,-518,-1000,-1000,-660,744,-723,-999,1000,-134,-1000,634,-1000,-1000,103,236,-1000,1000,1000,253,732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00486() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{-657,-253,-995,-690,-331,157,386,-1000,629,-177,-854,-1000,408,690,-861,-1000,264,-395,54,570,378,299,-299,823,734,-109,-1000,785,-429,772,55,-140,1000,-383,-683,-609,729,12,601,-663,147,-746,139,612,-198,-479,-424,-523,1000,-718,-637,-711,145,-461,1000,73,-470,-578,-455,-1000,341,340,-127,477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00487() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{588,1000,-797,378,339,-41,730,245,607,-830,-1000,-347,1000,-852,-20,-687,615,-375,967,501,495,-152,994,-43,682,1000,-547,-92,-121,1000,36,651,890,809,-924,153,96,-708,158,1000,130,-1000,-117,-24,749,-1000,-1000,-206,-536,-570,-549,1000,1000,-154,675,93,-1000,-173,-280,-1000,-351,229,180,688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00488() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{330,455,-846,-210,-1000,752,1000,-1000,-536,556,-328,-1000,1000,-356,828,-452,288,-805,-379,415,1000,-409,-138,1000,-908,281,-654,1000,-417,783,-519,585,-416,792,166,-555,1000,341,-566,-414,402,-833,1000,359,337,-774,-1000,-1000,-619,-768,-638,1000,713,-1000,-602,-1000,-1000,1000,-455,-1000,-87,1000,-538,7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00489() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{109,-1000,-96,-929,460,946,963,717,234,-1000,57,161,209,257,-1000,-1000,880,-332,873,836,1000,320,-84,-112,1000,1000,-1000,-645,940,-974,-292,-1000,-60,122,157,-1000,-793,-673,1000,-624,1000,279,953,632,-493,-246,1000,1000,1000,-557,-876,182,-971,-801,-79,579,1000,81,184,-130,882,-127,1000,919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00490() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{497,256,-887,-75,824,-1000,215,143,-244,415,-1000,-28,1000,1000,-33,525,-512,-211,-1000,285,-686,-152,-634,-594,-338,612,-545,-408,-1000,711,103,1000,136,-1000,-182,-605,422,1000,-419,-527,-41,1000,-551,-309,438,1000,-229,462,407,190,1000,-600,-1000,323,651,-406,-24,-1000,-908,169,-1000,-399,-964,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00491() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{-93,-116,-521,650,272,-726,236,-539,83,756,-1000,-553,-192,290,489,255,-712,-31,-255,70,-167,124,133,228,-239,430,-208,98,-1000,532,128,125,992,-839,-840,234,1000,888,-222,-725,-251,524,-103,-438,70,339,-1000,-77,40,-2,612,-173,-61,-369,-452,-293,-300,833,-652,-344,-1000,142,-589,-507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00492() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{1000,692,-206,-191,302,1000,1000,487,-534,205,508,430,626,-1000,-1000,-667,1000,-81,-51,343,1000,-876,1000,-644,1000,565,-116,-278,947,-385,-484,40,783,495,-112,308,-278,-931,1000,1000,1000,-426,-865,-1000,558,-688,-179,629,278,-101,-971,1000,202,-1000,147,550,-1000,-482,618,-973,1000,606,-847,825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00493() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{237,1000,-371,146,-606,-944,866,-1000,258,-921,-1000,-183,-940,-564,1000,816,311,-361,-1000,-764,-104,-184,-276,350,-1000,1000,400,1000,91,762,-955,1000,-1000,-977,-984,265,1000,-263,1000,-857,-616,-1000,322,254,1000,-576,-626,-1000,96,745,733,586,-796,-1000,157,-1000,-400,-263,486,371,1000,698,-957,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00494() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{-1000,-959,-1000,1000,-791,-471,517,-1000,705,-914,-1000,-1000,-646,1000,416,-1000,-576,280,-41,1000,-393,437,-1000,1000,461,-1000,-1000,-691,-307,1000,1000,-365,1000,-166,-1000,-1000,297,1000,-1000,462,540,392,415,839,-1000,-320,-1000,-868,1000,-1000,-1000,-173,-483,-512,1000,-1000,-1000,-699,-1000,-993,-1000,-809,-17,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00495() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAnnotations():java.util.List",
            new int[]{1000,-1000,-131,-352,774,115,1000,111,-174,-384,732,-319,103,-77,-541,-260,-1000,364,320,1000,-360,102,-400,-18,1000,-1000,-1000,-661,-314,-443,-192,-1000,904,-1000,371,-793,613,1000,1000,-1000,1000,432,887,560,-654,813,268,1000,1000,-640,-707,-259,-261,-947,-1000,-127,1000,-205,-944,-261,-1000,802,1000,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00496() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-1000,315,389,888,-761,831,515,760,877,-949,-1000,-718,1000,-9,-1000,-999,0,149,1000,1000,-178,1000,847,-678,-1000,416,-164,-849,352,1000,-5,-18,1000,-1000,-1000,-720,751,-420,-802,1000,1000,-1000,-584,882,-357,-38,-539,382,-812,926,400,709,893,1000,66,-915,-949,-1000,-1000,345,490,1000,1000,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00497() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{209,818,114,-176,719,796,152,1000,394,365,54,-1000,-274,1000,767,727,-766,-701,-123,1000,1000,-237,67,1000,983,-304,-17,293,1000,343,-25,-1000,-281,-1000,-136,271,-10,-205,229,-311,252,-91,1000,979,-1000,685,-912,1000,488,514,-1000,208,674,-729,-939,1000,-1000,663,332,86,-336,-1000,1000,206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00498() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-883,636,-146,1000,-560,548,108,-513,868,-1000,-1000,290,766,-351,-1000,-1000,-732,-226,1000,863,-34,1000,1000,-586,254,1000,281,1000,677,1000,104,-645,846,-343,-671,1000,1000,25,-1000,959,1000,-630,-677,577,670,-204,241,-34,-1000,482,-617,1,235,1000,-169,-461,-896,-1000,-1000,1000,877,1000,335,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00499() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-834,-27,235,-207,333,-525,447,358,110,-474,-66,-103,-876,474,492,365,342,124,-28,625,1000,-739,-887,270,-654,194,491,-34,-1000,337,-83,-200,-1000,192,-427,-83,509,-179,-819,767,-733,1000,1000,-440,-864,193,1000,341,512,-416,-537,969,249,1000,-797,740,1000,318,982,263,909,-417,-1000,-445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00500() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{904,-389,229,-1000,450,-457,-1000,573,859,248,169,520,-523,382,738,999,-199,282,267,-709,-23,1000,-327,-73,338,-161,-915,-1000,118,18,235,-281,41,642,649,-894,-293,-1000,-1000,-695,-982,976,453,510,490,-101,-1000,155,-670,421,86,-574,112,-659,112,391,160,-1000,-294,662,-65,-567,-671,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00501() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{279,295,-794,-836,512,499,-927,1000,-40,685,-566,-1000,-612,1000,1000,461,-44,-485,-687,-499,228,-1000,464,605,-657,-553,-739,-936,1000,-132,174,-1000,690,-1000,1000,-640,-180,83,17,-1000,-654,-258,68,925,-649,459,-863,-256,1000,869,-117,-1000,801,-534,-197,149,-521,653,-924,16,-312,-1000,1000,618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00502() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-251,795,79,-429,708,1000,-187,1000,177,-165,-357,-772,412,941,1000,727,736,-1000,-953,345,1000,-148,104,924,1000,-552,450,-486,578,-19,-533,-374,73,-1000,1000,423,-473,1000,-499,-1000,291,-125,1000,716,-1000,451,-1000,1000,449,83,-560,-718,336,-303,-1000,848,577,413,305,-55,-1000,-1000,1000,673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00503() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{343,321,-65,658,314,380,221,552,-317,-726,-47,-258,426,401,175,1000,-190,-1000,262,1000,950,-241,-31,247,1000,-384,368,875,594,265,-622,-574,-174,-900,-694,998,-126,-328,453,-40,356,134,-25,371,-263,230,-271,442,944,390,-1000,212,145,8,-287,1000,-316,241,-475,-22,-249,-778,783,449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00504() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{154,567,88,-1000,-756,166,-407,39,-681,-179,-430,-521,-415,-273,-237,-1000,809,798,265,-1000,-1000,-1000,296,38,-118,328,-865,-354,-555,821,1000,38,-213,-651,950,-544,960,116,-1000,261,-424,-675,-276,879,-357,-17,-642,-360,118,455,1000,-445,258,528,-36,-1000,-129,-836,156,194,991,-1000,492,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00505() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-724,-6,843,-880,-1000,79,473,-928,888,-880,-1000,-598,-383,474,-667,556,716,877,-166,-1000,37,-1000,-1000,81,-1000,-293,1000,-375,-1000,662,1000,869,-589,1000,9,-881,492,174,-924,1000,-1000,1000,769,-730,-523,-640,455,341,411,-548,195,-152,-689,-120,-300,1000,856,-589,50,-240,1000,231,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00506() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{1000,657,309,-985,1000,-340,-851,1000,-366,279,67,-263,-914,758,1000,555,-487,787,-1000,-269,-365,-1000,-287,908,207,709,-965,-1000,1000,-196,410,-360,59,-479,1000,884,1000,324,-183,735,-1000,1000,1000,542,34,598,247,357,921,-60,-825,-694,602,-1000,-929,1000,-1000,1000,373,1000,836,-1000,-67,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00507() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-827,-130,909,-658,-524,210,-735,547,596,461,659,6,-106,120,45,457,66,-155,237,327,748,511,522,128,254,-464,-969,358,-957,808,907,-466,-915,-595,519,607,-458,-836,636,668,875,134,818,590,855,-50,-933,67,789,247,-758,-651,753,387,-331,-79,573,-35,-54,325,927,823,-984,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00508() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{1000,904,423,534,-665,64,1000,35,1000,-1000,-161,-1000,-351,-1,-193,357,-425,194,1000,216,370,-208,-182,622,-889,-300,-827,1000,-224,-23,909,-605,364,332,284,-148,1000,-579,-1000,1000,231,60,604,758,-462,-1000,62,-145,221,-954,-288,-1000,-9,171,-910,3,-1000,84,1000,127,1000,-592,-637,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00509() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-927,618,309,580,-358,632,116,-463,1000,-805,925,-229,39,68,-780,-1000,-584,-93,1000,486,0,763,506,-59,511,1000,235,1000,-20,981,447,-360,268,-413,-845,884,1000,35,-1000,735,755,-378,-517,478,154,107,-270,357,-1000,203,-261,328,277,558,-460,-121,-1000,-745,1000,1000,836,1000,765,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00510() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{-511,286,209,1000,-634,499,401,-776,654,685,1000,131,49,-309,-1000,461,-1000,-93,1000,945,-34,698,412,-533,511,1000,177,1000,-9,1000,385,-500,95,-343,-1000,1000,1000,-894,-333,1000,801,-197,-1000,236,670,-48,241,-34,-654,418,-569,-1000,801,775,39,-15,-1000,-866,454,1000,1000,1000,613,-773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00511() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleInsets", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getAxisOffset():org.jfree.chart.util.RectangleInsets",
            new int[]{702,205,958,-903,484,-306,-507,578,-400,298,-622,-918,-953,1000,693,934,-557,-87,-951,371,-387,-889,-412,1000,274,-228,-319,-217,884,-27,31,-1000,-1000,535,560,-1000,-381,-99,72,-231,-108,1000,888,-364,-450,-77,200,767,1000,-107,-1000,-840,15,-1000,-546,653,-321,1000,-358,121,439,-1000,-618,175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00512() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.Range", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-1000,679,1000,462,359,516,-1000,-109,805,229,-1000,173,457,-1000,74,-50,-383,-638,33,312,-1000,-137,-854,-453,-1000,-326,-230,457,15,364,-1000,0,-84,1000,562,-406,-1000,-1000,-1000,-610,754,-457,170,604,983,-458,-346,390,542,-351,1000,541,-149,7,-1000,-1000,-218,-436,-1000,131,238,-816,-99,-428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00513() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{668,638,-411,-437,7,286,444,-397,-378,1000,-179,-1000,552,-833,-1000,52,627,-677,-285,293,356,-1000,-493,1000,-1000,363,1000,730,-360,-962,1000,-139,-144,351,1000,386,943,1000,40,474,-1000,-589,-836,987,1000,-219,-1000,-867,1000,842,-254,-362,390,-891,1000,821,1000,-628,1000,-198,-485,-643,-227,376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00514() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.Range", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{444,809,-344,-1000,-390,829,-310,-699,689,-96,1000,1000,-859,-609,-981,-1000,-1000,547,-602,353,-560,-418,-600,-623,-812,389,513,-469,212,196,230,-196,-781,283,224,-69,1000,542,-680,744,-527,422,795,1000,469,-1000,-905,-484,814,-1000,884,246,-766,345,-93,294,-330,-21,1000,-857,932,-130,-815,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00515() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.Range", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{714,-827,-148,-1000,-7,503,623,-793,-345,666,-642,985,-935,-941,-1000,-1000,-1000,376,342,394,-701,-1000,-24,1000,-915,77,1000,-322,840,-214,521,-508,-587,104,1000,155,120,-490,-1000,925,-463,904,1000,1000,161,-710,-714,-52,1000,-1000,-300,-78,-1000,871,-312,-58,-59,313,-482,3,932,77,74,438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00516() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.Range", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-1000,-703,938,-569,28,-19,-832,488,70,353,650,-909,756,827,-983,408,189,1000,-24,1000,1000,597,1000,1000,-36,417,898,366,-726,-223,27,-591,774,1000,190,697,120,-192,459,-724,-76,553,-974,912,1000,163,-805,-229,352,882,37,-672,1000,-1000,-155,-716,781,7,228,-136,-789,-144,108,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00517() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.Range", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{109,-21,-1000,1000,499,704,650,-482,370,701,-417,772,-1000,1000,1000,-9,389,-717,-883,1000,-1000,-892,99,-1000,-1000,-744,-230,1000,200,-202,-1000,1000,621,-617,-1000,-533,-585,-787,141,760,-385,13,276,-881,-946,-1000,-1000,1000,-943,-1000,-533,253,93,523,1000,39,-782,-798,-177,-370,1000,-250,-133,-873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00518() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-513,-886,189,661,-897,844,183,-1000,647,-365,-4,1000,252,-1000,1000,-890,278,-824,-1000,-647,-675,-1000,309,106,53,261,470,53,-1000,914,709,-1000,-1000,151,-41,-1000,-575,1000,1000,768,-187,-1000,-981,-18,1000,-409,62,-253,-99,-392,851,91,72,-30,-517,551,846,-1000,724,-7,680,-352,-1000,56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00519() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.Range", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{137,9,-237,-741,550,796,-66,353,515,588,-40,400,943,-1000,-673,-117,-307,320,-183,437,-681,-105,583,-481,-319,-466,449,380,59,-472,14,-838,-237,699,-68,668,502,-312,-618,-392,-585,248,-22,788,1000,-516,-602,377,80,-400,814,408,-637,-155,90,-213,-495,-239,75,-52,-835,-312,25,-34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00520() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.Range", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-681,143,298,1000,508,492,-554,-826,974,-457,121,-120,11,279,1000,80,241,-650,-444,714,164,-51,-85,-423,-352,-177,-754,782,-317,161,-1000,289,98,858,-317,-187,967,104,264,311,368,-402,-64,-237,641,358,-1000,1000,-85,-222,429,379,727,-599,-1000,160,-440,-973,122,-595,720,-886,-472,-936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00521() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-93,1000,479,59,76,857,235,-895,422,137,497,926,89,-540,-670,-1000,-844,-1000,-462,945,-1000,-1000,-1000,-1000,-968,130,68,-318,1000,-90,-1000,-362,-347,-5,1000,-1000,-822,-1000,-1000,-158,6,929,1000,1000,412,1000,-1000,-314,1000,-996,145,485,-1000,803,-510,-28,35,280,-891,-1000,972,-706,-439,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00522() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.Range", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{924,-925,-1000,851,943,-424,1000,542,494,785,-532,-467,-761,485,787,-77,845,150,-735,1000,-571,-204,-1000,-70,-101,-1000,-609,511,1000,-1000,-310,-213,502,-666,-381,496,1000,69,805,-104,461,1000,518,-31,223,-849,-604,720,-840,-941,994,300,136,224,672,1000,-1000,174,-397,-343,1000,98,774,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00523() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{389,490,-322,-763,321,776,-68,-537,130,409,651,686,-613,-944,45,-917,-295,214,-353,729,-320,-1000,-266,-347,-509,-101,126,418,-55,534,716,-299,-701,246,498,1000,-352,-513,142,626,-335,-160,-226,73,615,-259,-780,-526,400,-574,-661,-420,-252,553,-249,-83,235,-556,3,-917,124,-387,152,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00524() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.Range", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-833,879,341,243,474,959,-565,171,-780,471,360,345,1000,287,-1000,628,789,517,-182,-182,1000,1000,-215,780,-1000,27,1000,224,-485,317,389,-777,1000,632,356,925,787,1000,644,-828,-1000,657,-1000,1000,358,551,-259,-833,-255,1000,104,-344,571,-881,203,-636,219,1000,525,321,-300,325,648,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00525() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{125,-1000,-292,667,168,-336,1000,63,752,826,-1000,-431,163,841,1000,131,405,488,-584,-925,-1000,403,-1000,-1000,408,-1000,-81,-64,574,-1000,-354,-591,-310,-1000,-1000,-359,913,800,-615,-422,848,1000,292,-479,-48,406,-754,479,-1000,-991,1000,30,435,237,-288,-26,-1000,399,-677,325,-204,763,896,-811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00526() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{823,-116,-776,-613,329,338,312,8,-62,-660,-157,-491,-905,376,508,-764,568,898,107,992,182,662,-704,-775,-371,-49,509,58,1000,-778,-109,805,52,-179,228,530,768,-987,-857,230,-148,1000,658,725,-701,-1,-1000,-463,1,-326,-266,126,69,632,124,-515,-1000,464,-227,-837,1000,31,367,323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00527() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataRange(org.jfree.chart.axis.ValueAxis):org.jfree.data.Range",
            new int[]{-326,-1000,659,-1000,-420,-369,-227,955,-508,1000,594,-1000,503,2,-1000,395,970,991,565,20,891,-1000,-173,1000,-214,159,1000,139,-4,-474,632,-497,1000,684,397,1000,588,-1000,-99,-487,-328,276,-645,989,412,250,-240,-750,343,1000,-759,-875,848,-466,325,-564,520,929,514,-178,-769,266,1000,879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00528() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-350,-514,624,711,-393,-367,739,841,1000,-34,1000,198,-1000,836,-11,238,1000,698,289,-967,-300,-175,-194,-433,-763,7,233,-726,-270,-72,-415,827,596,-858,747,-700,1000,-309,-591,491,119,-679,-181,-365,207,-130,502,-463,674,-162,-56,-57,713,131,-311,1000,-1000,-15,-456,886,-493,68,280,-25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00529() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-1000,192,374,173,197,77,-803,-390,1000,678,1000,903,-795,-1000,-1000,367,1000,1000,-798,-243,-64,-1000,136,735,-1000,904,-945,-1000,771,969,-605,873,-396,-858,755,-151,663,326,-195,-308,347,-1000,-1000,767,-127,-816,1000,-408,-888,-155,1000,-517,1000,-1000,-732,495,-1000,275,-525,920,-1000,812,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00530() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.xy.XYSeriesCollection|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{296,79,1000,507,374,-396,1000,358,64,114,303,444,-1000,286,-969,-641,-790,767,304,693,1000,-1000,504,-125,-1000,91,-1000,-375,600,-1000,-992,-545,-883,-411,-9,-1000,532,762,-375,504,733,-542,-1000,-678,-353,864,383,-647,252,233,-1000,154,1000,-851,-794,156,140,-669,457,699,1000,267,431,310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00531() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.xy.XYSeriesCollection|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-763,-805,-716,552,-1000,-536,-539,-374,-782,-1000,1000,834,-283,-848,1000,-559,-240,-334,494,863,759,821,-1000,-1000,839,-1000,377,1000,-1000,-937,-829,191,245,-784,-1000,1000,578,1000,824,621,-141,-239,522,-443,376,391,790,133,-354,-1000,-128,-815,-829,-273,713,-1000,-1000,-729,223,1000,645,-654,808,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00532() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.xy.XYSeriesCollection|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-308,-493,420,700,-892,209,610,-375,188,-903,1000,434,-1000,438,505,308,-240,514,1000,219,-203,1000,-565,-1000,-395,-1000,341,387,-1000,-1000,-577,333,-37,-685,-951,-14,1000,876,-290,351,620,-247,813,-1000,411,1000,441,-381,545,-791,-867,-609,370,-539,-204,38,-1000,-1000,329,1000,1000,746,491,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00533() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-1000,-1000,-721,950,97,-500,828,-47,44,-636,24,1000,-851,-1000,-33,-15,-498,-913,-45,-374,-747,-1000,-647,498,994,-629,572,-458,-33,-504,152,227,-819,-1000,-1000,-550,953,762,-24,-114,704,-423,116,-504,159,552,228,592,-66,362,159,-977,373,-1000,-949,-1000,-126,173,204,273,-729,857,692,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00534() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.xy.XYSeriesCollection|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-1000,155,-875,-436,508,209,-954,-375,589,300,577,390,46,-1000,179,-752,674,-314,-960,127,-203,-586,-565,595,602,194,-813,753,172,-30,-1000,-303,189,-1000,-494,-14,-155,876,1000,351,-622,-765,-901,1000,3,-1000,1000,-36,-465,-791,588,-609,-346,-539,827,-1000,-213,260,-576,1000,-861,746,-74,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00535() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{1000,-1000,-191,464,-565,-1000,598,590,-178,-1000,43,400,396,1000,1000,-320,-503,-1000,701,-275,-315,1000,-429,-1000,483,-1000,970,475,-1000,-988,47,511,779,105,-400,-1,395,103,127,1000,-1000,44,166,-627,-437,676,-286,-75,299,454,-33,820,-1000,1000,451,822,-92,267,-281,209,881,-1000,988,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00536() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-71,-968,-66,-935,1000,-364,-256,-732,532,301,-884,267,1000,-721,-1000,-825,389,-563,-1000,-431,-508,-971,342,239,232,1000,-825,-995,1000,1000,-1000,538,78,-266,1000,-12,-113,-145,650,-182,-1000,-685,-1000,1000,-1000,-1000,829,-300,-909,652,1000,1000,10,373,-132,-143,923,1000,-923,478,-1000,-509,-211,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00537() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.xy.XYSeriesCollection|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-211,-227,655,-225,-307,-272,1000,-46,367,477,841,484,-472,-420,616,769,-507,184,-592,895,709,-505,394,672,-853,496,-972,-116,865,93,-515,-78,-999,170,-490,-1000,499,302,-4,154,930,-56,169,281,-281,305,-90,-115,-56,25,-756,-326,1000,-731,-930,694,312,-531,831,-86,544,656,354,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00538() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{156,1000,453,997,164,-722,-547,-783,59,-109,-1000,-1000,-347,876,-409,789,-797,244,-227,-1000,157,741,610,-1000,-689,429,1000,-1000,547,-7,-782,1000,-288,1000,1000,383,359,815,715,-492,-348,540,-289,177,300,1000,-1000,-237,-410,664,-218,1000,-298,-1000,-76,1000,-755,-340,325,388,-19,-1000,-66,956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00539() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-1000,-136,-86,705,-195,-748,762,-367,132,-488,730,-891,-134,1000,488,-259,81,236,-30,-257,636,552,146,-1000,-734,-1000,-1000,177,-814,-1000,-245,223,417,1000,-628,-1000,-197,999,-357,1000,-119,256,456,105,-341,1000,870,-96,-298,199,-405,256,37,-146,304,1000,549,-860,322,-675,1000,-621,784,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00540() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-1000,-1000,-981,-738,1000,1000,-1000,-1000,1000,239,-82,-369,236,-1000,-906,-748,274,53,-1000,-37,-812,-857,72,1000,-395,1000,-1000,390,1000,1000,-564,320,-176,-1000,353,1000,-279,1000,769,-1000,-833,-625,-1000,1000,-846,-1000,1000,89,-1000,-668,1000,-436,-82,-1000,325,-323,-290,1000,-1000,845,-1000,272,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00541() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.xy.XYSeriesCollection|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-1000,291,-24,-341,206,139,-628,-885,891,679,259,-956,-203,-1000,-1000,107,753,228,-1000,-80,-56,-765,65,705,-1000,947,-724,-439,1000,921,-113,351,-254,-450,368,184,292,822,211,-381,61,-918,-1000,-1000,-365,-773,617,-82,-884,-335,671,-239,1000,-1000,-365,921,-244,411,-479,375,-1000,471,-708,822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00542() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.xy.XYSeriesCollection|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-385,-319,-1000,608,-592,-122,-422,293,982,666,762,-1000,-222,-559,-373,-686,1000,-1000,-614,-345,-646,-430,-373,459,-36,48,440,758,-109,314,225,518,708,212,-490,226,-137,1000,398,-44,41,-16,-275,-667,455,-407,-588,541,-861,967,-56,-899,-66,442,603,1000,-844,-11,311,-801,-1000,465,-543,117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00543() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset():org.jfree.data.xy.XYDataset",
            new int[]{-686,-1000,-796,-597,-921,-494,224,56,1000,-35,428,1000,659,13,-413,-278,645,-732,-400,186,-625,668,-413,-275,311,-339,-723,741,-299,143,-384,806,-417,-1000,-639,374,251,579,459,-159,-196,-423,-118,346,61,-197,1000,511,-714,-465,404,-489,-82,677,270,-908,-448,37,466,-291,-647,541,367,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00544() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{-1000,1000,-381,106,113,-1000,1000,256,-742,-995,-561,576,690,544,760,452,99,-732,-695,-767,-135,-447,-1000,-754,898,445,-1000,226,1000,1000,-611,981,121,-584,828,857,-611,1000,-194,-356,891,1000,-303,253,-960,-574,-21,-636,1000,-1000,894,96,1000,-458,33,522,-480,1000,1000,1000,-472,-1000,49,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00545() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{-1000,1000,-186,303,801,-1000,461,1000,-704,-782,602,-42,1000,1000,853,1000,659,948,991,-1000,418,112,-1000,492,-584,-499,156,540,879,1000,-56,-1000,998,597,-622,-1000,398,322,626,-72,-1000,-957,36,-838,-791,457,-514,115,1000,491,437,-342,865,-583,-17,185,927,-1000,-773,168,-886,421,728,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00546() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{-23,-543,103,-16,707,-537,893,-361,-606,-997,-415,-406,195,-815,783,53,840,326,-380,-598,1000,-246,-328,-279,-1000,-702,-334,-1000,528,295,-904,20,9,-1000,1000,-497,787,659,-105,765,-411,-90,-296,-1000,-760,-1000,1000,-598,227,522,717,753,997,-488,-830,32,-1000,22,-394,-903,-366,994,-1000,330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00547() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{680,-1000,-446,503,402,-55,782,-685,-589,1000,-536,-112,-643,-1000,1000,387,674,-376,-606,-179,-492,-755,972,-660,-1000,-392,-77,-1000,37,-491,-804,-66,-650,-1000,537,179,919,304,-282,521,69,491,-338,-1000,-545,-599,689,-504,238,480,422,537,995,45,-987,-80,-1000,128,-91,-1000,91,-477,-1000,-136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00548() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{-1000,914,779,282,-130,-1000,-308,1000,563,389,748,-575,1000,450,1000,880,711,176,1000,-1000,-612,242,86,87,-1000,-712,999,-780,153,684,-251,-1000,524,1000,-763,-1000,1000,214,64,71,-1000,-805,-763,-1000,-730,1000,-1000,1000,709,44,680,132,905,912,-1000,-1000,529,-1000,-241,-465,-635,903,-432,528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00549() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{-711,1000,774,28,770,-1000,-28,1000,-659,-276,794,-812,1000,710,796,-691,617,1000,1000,-1000,770,1000,-881,1000,-460,-1000,452,268,1000,1000,-153,-612,1000,143,139,741,482,331,582,136,-1000,-1000,365,-816,-1000,462,-338,307,457,1000,469,794,814,-1000,-501,160,770,-1000,-1000,-153,-851,195,141,-323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00550() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{-1000,1000,-231,-264,-291,-972,-904,719,1000,-545,-98,-266,560,821,1000,1000,-381,-1000,1000,-366,-683,-1000,-147,-1000,-431,666,169,474,-1000,-1000,867,444,220,601,-402,302,185,470,462,-511,-1000,243,-1000,164,-389,669,-605,585,677,-931,470,-1000,-1000,895,1000,1000,-943,-878,555,-66,-195,405,625,112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00551() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{448,1000,-761,-42,1000,797,-968,-508,-578,774,147,1000,-231,1000,-154,-1000,-377,1000,852,821,1000,579,-78,299,-75,831,1000,315,194,992,41,-1000,1000,748,-1000,-1000,763,-1000,-6,-370,290,-83,1000,921,63,843,-214,480,-310,-447,-1000,-1000,276,-393,-530,-929,-448,-1000,538,545,1000,1000,344,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00552() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{-1000,983,-1000,196,-612,-735,-1000,725,595,-742,-349,-253,437,686,-632,823,-884,-810,349,452,-277,48,-102,-191,931,627,-196,820,-522,684,1000,214,811,710,-437,605,-1000,477,362,-326,-275,263,-440,1000,-537,896,-545,173,205,-1000,159,-1000,-850,-394,1000,1000,1000,-5,381,1000,-152,408,1000,-697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00553() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{629,364,-51,793,-72,-770,1000,105,505,931,-673,-1000,1000,-728,1000,314,708,-76,41,-497,-409,529,-957,844,-1000,-1000,-581,-912,730,-289,-241,769,-1000,-267,896,-931,-653,926,481,914,-1000,-947,-539,-1000,-716,-521,296,-534,578,-63,1000,-455,40,-874,-735,322,-958,-410,-1000,-1000,-1000,-790,-467,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00554() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{-1000,1000,234,-522,925,-609,203,1000,-72,919,571,496,907,505,853,717,1000,81,-162,-1000,-180,-652,-942,-1,-1000,-1000,440,-486,836,1000,-307,-540,-105,342,-440,-821,1000,337,307,-174,-950,-437,-177,-1000,-825,393,-100,-641,463,-129,454,-217,563,-89,588,563,1000,-919,-685,-561,-127,162,-104,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00555() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{1000,-1000,-381,446,1000,842,1000,-882,-879,335,-887,164,163,-850,952,-1000,534,1000,-1000,467,-106,1000,-1000,841,-1000,-1000,-241,-1000,1000,-239,-1000,1000,-964,-873,869,-542,-611,211,-194,1000,891,-58,913,-1000,-392,-836,752,-1000,-94,1000,758,-660,1000,-970,55,-1000,-1000,234,312,271,-953,-1000,-774,481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00556() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{829,1000,-626,579,-920,-727,-50,207,462,-384,-640,179,-995,906,-58,-877,-327,-812,-469,-784,891,-1000,-1000,-1000,-13,1000,-1000,150,-876,1000,145,159,864,-665,335,850,455,1000,344,-528,-1000,298,-185,345,-544,7,365,-745,1000,-1000,368,-849,324,-1000,532,1000,300,1000,411,-217,992,-637,652,-895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00557() {
        org.junit.Assert.assertEquals("STATE:org.jfree.data.xy.XYSeriesCollection|getSeriesCount=22:java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{-128,-395,1000,386,-246,-264,862,-713,514,1000,-785,-573,-120,-1000,1000,1000,856,-1000,-246,-947,692,-961,-45,-1000,-1000,-402,-650,-1000,311,-56,-1000,1000,-884,-806,1000,-447,1000,1000,-410,622,-266,852,-1000,-1000,-660,-526,13,-1000,134,-858,1000,464,1000,708,-650,-1000,-1000,24,605,-689,-687,-1000,-1000,47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00558() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{-1000,-610,433,1000,-612,92,545,-919,-979,239,-628,625,-638,-368,1000,144,570,-828,-893,-78,-740,-706,974,-817,-618,-51,-315,-844,237,684,-605,214,811,176,252,-142,678,6,-485,-35,672,1000,30,-970,-595,896,-545,173,1000,-1000,429,27,1000,66,-780,-805,-254,540,894,-181,407,-91,-906,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00559() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDataset(int):org.jfree.data.xy.XYDataset",
            new int[]{-981,1000,779,-23,287,-1000,1000,446,438,-30,-922,-353,1000,-392,1000,1000,972,-1000,6,-1000,-1000,-1000,-1000,-1000,-736,-275,-992,-1000,1000,586,-1000,1000,-1000,-893,1000,325,-39,1000,-190,208,-160,677,-1000,-1000,-1000,-1000,195,-187,1000,-1000,1000,554,656,179,93,1000,-1000,1000,1000,-286,-1000,-1000,-1000,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00560() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{648,-243,-411,-780,-404,75,114,390,-851,680,-590,-306,1000,756,422,-876,-30,83,179,-921,-164,156,-261,-359,305,978,-1000,224,-593,476,-531,54,886,-682,22,720,-792,326,348,-918,941,579,287,-438,-917,-130,216,514,-7,706,-71,621,-486,842,-557,822,-404,12,-311,618,142,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00561() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{-133,-1000,-216,-533,1000,-322,1000,-485,827,1000,110,-1000,-1000,-1000,-791,1000,904,-1000,1000,882,-371,-1000,714,1000,-59,-1000,1000,-418,515,-633,794,-407,380,1000,1000,-269,1000,-915,548,1000,-1000,436,957,634,1000,-236,616,-1000,-1000,-1000,409,694,-1000,-1000,-623,-1000,677,-239,-331,-982,-1000,1000,-474,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00562() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{1000,-92,-246,649,647,-279,281,-767,929,-300,282,-1000,-183,-429,-991,-101,977,335,425,148,-993,-1000,1000,445,222,-341,159,531,822,-408,611,414,923,-21,1000,-285,701,-1000,335,257,-169,370,347,1000,-117,-72,784,-742,-588,-324,375,872,-2,276,-772,18,781,-95,-78,193,-376,10,-17,-212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00563() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{-979,955,102,765,-991,852,297,940,8,-554,-470,252,788,499,784,684,-134,-43,516,-647,-577,658,-410,-483,794,-92,-710,-467,-973,204,-829,-105,275,458,-446,581,126,747,563,656,-642,-180,-199,-691,72,-606,-10,334,-343,601,180,418,638,-134,-468,-433,-907,-36,-941,-517,-972,-963,234,769}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00564() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{-202,-1000,-216,721,-356,199,1000,453,362,-698,-1000,-1000,1000,392,1000,-37,383,-55,634,-436,520,-464,-1000,-945,-974,-406,760,-1000,-8,891,572,-775,140,868,183,1000,-184,-74,1000,299,-507,-339,1000,-643,-426,339,46,645,-557,867,623,778,-552,-520,-256,-809,-588,-266,-1000,-305,-290,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00565() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{330,296,149,-432,487,-1000,32,444,520,-272,250,-699,29,-306,457,433,-330,801,767,829,-781,-651,363,1000,991,-887,450,146,105,-735,265,-327,-1000,874,-742,387,132,105,363,464,-35,825,-414,43,1000,-265,678,-402,-886,-1000,905,888,-837,-1000,-590,-52,1000,587,632,-576,274,-721,489,-310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00566() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{-621,-236,929,431,-540,-482,667,451,-629,-54,-172,-447,-613,-476,784,789,148,520,616,514,326,707,-643,404,900,-907,0,-322,-194,-108,-369,-597,-1000,458,-776,125,479,1000,748,844,-360,995,41,-355,1000,-807,969,89,-110,-24,1000,666,-1000,-1000,-480,-1000,245,-36,221,-1000,-593,684,-174,769}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00567() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{1000,300,229,-110,363,653,-431,754,-231,-354,463,-1000,892,119,184,536,-950,-288,1000,348,-111,-1000,-838,-459,480,-818,772,132,40,-238,-442,-845,-513,680,-698,149,473,-145,504,496,-759,86,668,430,907,-282,-984,-147,-250,-439,219,940,599,-973,-497,-943,329,-132,-644,562,207,-451,152,-315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00568() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{1000,840,-336,-32,996,272,-401,-259,53,-522,-683,183,813,809,-426,74,-39,-250,1000,-1000,-418,-562,256,486,63,-740,-1000,73,-251,-155,27,102,1000,-1000,776,704,-237,-1000,589,-394,-359,-109,110,542,-1000,-209,-72,-786,-799,955,672,308,567,232,-290,1000,-465,-402,-961,987,-1000,-731,746,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00569() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{665,-857,83,-697,733,-526,1000,444,743,1000,82,-1000,29,-1000,457,1000,683,-795,1000,-262,-781,-716,125,773,272,-1000,1000,-327,44,209,-308,-540,-202,1000,555,514,544,308,644,1000,-1000,472,796,-550,1000,-265,134,-900,-576,-1000,618,286,-504,-1000,-659,-1000,320,-373,-663,-868,-1000,780,-55,-458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00570() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{988,454,-17,710,892,315,-358,-97,-768,-362,-1000,-9,587,1000,917,-1000,-549,153,-728,-1000,490,235,-356,-1000,-169,1000,-342,-206,-292,-176,333,-252,259,-1000,328,516,-131,-943,-47,-840,1000,-49,-171,68,-1000,-83,589,1000,10,1000,-13,1000,269,287,-614,836,-1000,-569,-472,523,-924,-413,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00571() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{986,1000,513,900,-470,1000,-491,1000,820,-255,600,371,-277,92,407,1000,-875,437,689,-203,-814,416,-529,330,1000,-849,-416,-321,-202,-481,-1000,26,-791,1000,-1000,596,939,1000,641,1000,-1000,-299,31,-987,1000,-762,-423,346,-495,-128,30,1000,634,-1000,-587,-1000,-155,668,-358,-1000,-576,-696,-458,-489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00572() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{501,449,-723,841,1000,-1000,698,-48,274,-213,-160,-1000,143,-686,-1000,1000,1000,-1000,1000,1000,210,-1000,1000,1000,21,-1000,1000,-859,-504,-1000,873,183,-839,1000,1000,-707,939,623,917,911,-1000,319,31,1000,1000,-769,678,-1000,-1000,-1000,1000,1000,-1000,-1000,-836,-887,882,631,-870,-963,-1000,223,-1000,211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00573() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{830,-181,-567,-792,37,-131,-14,138,1000,1000,589,-666,-886,52,-581,1000,-59,-366,961,640,-316,-326,256,1000,1000,-230,207,-136,167,-854,114,21,-1000,542,558,1000,619,-1000,166,40,198,475,309,652,809,188,-4,-555,-1000,-718,51,961,-351,-1000,-329,-437,221,-445,-136,-409,474,687,333,-844}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00574() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{8,-889,-806,-819,-250,297,1000,330,788,723,-498,-124,951,333,670,116,175,-255,-295,-417,-512,-340,-677,-552,-322,-341,-289,-186,-232,982,299,-542,469,163,530,1000,-98,10,679,39,-352,-39,1000,-728,-52,211,997,52,-540,330,99,508,164,-356,-350,-600,-677,-417,-917,-141,257,-323,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00575() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetCount():int",
            new int[]{1000,-771,-334,-797,804,-61,-396,260,-157,951,61,-1000,-252,244,-279,-655,-506,-343,518,-95,359,-1000,-196,-91,-50,342,154,323,587,-325,-260,-794,165,-303,-239,-387,-180,-378,599,-677,690,394,49,-130,-268,277,-209,73,59,-125,117,1000,-522,236,-623,425,777,432,-5,280,-642,-303,1000,-239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00576() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-400,-678,519,1000,293,-1000,-31,389,-791,1000,184,-1000,707,-5,61,-763,443,944,1000,773,736,96,123,-64,-360,-1000,-666,53,1000,1000,648,1000,171,841,285,-721,321,1000,-870,-344,1000,-230,-1000,905,-441,-815,151,474,-910,-537,691,209,1000,141,281,8,-1000,217,1000,435,-1000,444,-593,-206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00577() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-771,4,374,-309,1000,-251,613,105,-253,445,-79,-289,-374,-1000,-710,60,156,326,396,30,463,-247,3,81,397,-133,912,635,-144,-398,-233,312,171,-635,-550,-1000,218,-12,-456,546,-863,-221,224,-889,-997,509,75,699,445,240,568,383,676,158,-39,22,631,-18,73,-353,-814,143,157,-239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00578() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-161,-215,1000,-55,459,-320,-1000,599,1000,-809,-543,929,268,684,-476,1000,-1000,352,-828,799,-948,551,-1000,-1000,1000,-377,779,632,-49,153,197,59,-618,-962,732,1000,-538,-77,3,-73,-416,-30,292,69,639,368,-867,-766,-1000,546,-400,328,-364,359,213,-687,157,1000,74,-1000,-1000,-740,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00579() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-249,-738,274,455,-43,-886,-1000,647,362,179,-800,-172,721,-223,329,842,250,-19,-400,-412,137,-22,304,21,368,-323,-24,45,-1000,-1000,853,26,-303,-89,478,868,-903,-68,-374,79,-127,1000,129,-1000,278,655,-224,20,283,218,11,381,563,115,93,69,-565,-20,396,-276,-1000,-436,5,-53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00580() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-1000,-319,16,1000,-88,-631,660,526,-235,1000,347,-899,-503,30,-85,-25,-780,1000,163,1000,-247,378,-873,490,-917,-629,-853,553,167,1000,522,469,851,-91,798,589,664,373,375,-56,946,-49,-226,747,-370,-35,-250,491,-1000,565,-600,684,735,665,193,257,-844,-279,37,719,220,77,239,867}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00581() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-331,247,863,789,397,-765,-47,189,800,1000,-780,1000,-402,242,-128,164,-60,192,1000,-638,314,-700,-661,-232,-78,-800,226,735,382,-271,-240,192,467,-375,347,-746,-469,399,-704,-206,-330,417,129,-270,-267,67,-440,-349,480,-313,-555,182,1000,299,496,-193,-163,58,-60,245,-44,454,-47,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00582() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{833,-249,-632,569,-168,-894,1000,48,-763,1000,467,-918,-488,-883,276,-360,583,597,799,191,-247,96,-227,155,-1000,156,-1000,91,167,-222,522,469,516,-500,800,1000,-222,584,687,-56,-379,1000,-931,-1000,-370,163,418,710,-1000,240,1000,684,1000,492,-629,282,-1000,-82,37,-534,-340,1000,-976,829}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00583() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-516,-809,-193,221,28,-668,-417,638,-141,-12,-658,-1000,910,-130,253,-368,1000,15,1000,-519,1000,-772,344,1000,-698,-701,-122,228,-652,-1000,-1000,353,538,99,124,-1000,-870,452,-1000,216,904,1000,-408,-696,-681,450,2,1000,1000,-998,418,521,927,100,354,329,-472,-1000,325,883,-455,516,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00584() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{671,-1000,-361,-613,343,-743,722,914,-1000,-1000,904,984,400,-940,-1000,303,-359,70,-1000,646,-1000,820,-352,-1000,1000,746,964,-471,-927,532,-6,163,-580,422,-1000,-806,1000,-373,-184,1000,122,-1000,173,400,-676,177,-380,-362,-487,797,43,876,-120,-158,874,-465,837,269,-1000,1000,381,-1000,769,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00585() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-493,-676,479,-21,1000,-560,-453,841,814,-635,-1000,-412,488,-616,184,-378,-1000,152,972,-1000,1000,-1000,1000,1000,-28,-177,822,418,-641,-1000,-1000,111,-266,-317,-1000,-1000,-742,88,-1000,1000,-363,-295,545,-651,-1000,1000,-43,1000,1000,-1000,498,898,-712,771,726,256,1000,400,-649,1000,-157,-690,-453,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00586() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{1000,-8,224,428,30,-1000,233,-508,-394,864,186,-1000,174,847,234,563,940,24,893,-341,396,906,-521,-1000,-381,-602,-634,543,419,577,930,581,1000,1000,408,-131,-575,70,-333,-372,789,1000,-1000,528,130,1000,-237,1000,780,-509,-323,741,-988,499,362,254,-274,-1000,1000,-2,-28,-530,-1000,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00587() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{470,1000,19,-273,284,-365,382,-1000,-83,1000,235,-842,86,299,516,30,1000,-324,1000,-709,-159,-591,-492,-1000,1000,-23,-169,609,254,-491,47,-425,356,-690,469,-306,-988,150,6,32,-1000,1000,-896,-807,-122,57,-574,686,865,-473,388,0,862,633,-177,-89,-706,-191,727,-292,-866,925,-1000,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00588() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-1000,-255,230,-1000,-191,-315,-319,-547,365,-782,-791,987,-1000,-1000,347,24,421,-1000,-394,653,641,-317,607,-797,-348,-181,138,-1000,-185,-1000,-977,-321,-904,110,415,1000,-704,-909,288,-165,-1000,446,-524,-1000,663,54,468,-842,-697,718,507,447,423,-473,38,-509,536,656,-297,-1000,-20,85,-225,74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00589() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-516,-369,1000,350,28,-634,-685,611,-230,-12,-251,318,1000,1000,-123,-125,584,1000,825,1000,-15,506,-301,-952,-88,-701,282,243,-652,652,1000,956,538,1000,27,-478,-684,871,-1000,-997,742,-99,-1000,1000,128,-698,-345,303,-1000,-1000,-311,-54,-962,54,155,237,-261,763,1000,-454,-455,516,124,-609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00590() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{1000,-521,946,1000,132,-961,-407,478,100,610,-367,-1000,-10,122,-523,1000,-1000,1000,-806,-190,-327,818,-940,24,-1000,-796,-139,893,-667,488,696,746,1000,232,772,589,434,156,98,-286,925,361,216,883,24,700,-926,796,742,1000,-1000,726,784,649,211,221,-230,-1000,-288,88,-172,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00591() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.plot.DatasetRenderingOrder", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDatasetRenderingOrder():org.jfree.chart.plot.DatasetRenderingOrder",
            new int[]{-221,138,683,350,414,-375,-445,714,-138,-258,-255,135,836,1000,-230,392,-271,1000,239,1000,589,446,-1000,-564,-49,-1000,151,735,1000,279,972,430,127,1000,258,439,-455,557,-530,-959,1000,345,-645,1000,512,358,-562,314,-1000,-1000,-1000,-28,-1000,486,-79,412,-151,-509,694,-361,-338,-550,-694,142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00592() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{867,-39,-284,400,109,199,220,1000,-485,1000,-477,-1000,956,999,498,-509,915,-16,1000,504,801,-589,223,-450,-147,-663,228,-1000,-444,1000,17,-1000,1000,379,641,235,-58,-953,-640,581,-459,-1000,1000,-186,92,-904,1000,-1000,1000,-1000,-644,-202,448,411,312,569,1000,-587,1000,68,500,263,944,-584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00593() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{962,-301,921,-1000,487,199,-222,1000,27,1000,-507,-243,275,1000,440,-1000,915,-440,975,900,1000,57,1000,-698,443,-988,281,-1000,-194,1000,1000,-1000,1000,1000,903,-289,242,-140,-280,51,565,-1000,1000,22,-1000,-366,926,-901,976,-1000,-254,-202,754,-214,-1000,805,1000,-261,647,58,-397,-100,829,-65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00594() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{41,942,-911,812,825,-625,383,1000,-371,908,559,-675,1000,15,396,750,-433,48,742,333,658,-924,-1000,253,-832,-251,475,-521,-265,878,-648,-1000,938,58,-2,503,-1000,-1000,-980,417,-999,140,437,-596,230,-651,-272,-596,1000,69,-456,481,1000,-271,1000,305,-709,479,-936,-503,704,129,747,-822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00595() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{523,201,-1000,-187,993,-866,-531,623,823,1000,449,966,953,-1000,112,-355,-60,353,1000,495,477,-212,882,-1000,-120,-1000,1000,-1000,501,176,938,-1000,1000,546,1000,-1000,271,-14,63,-153,131,327,1000,-258,297,-1000,884,-821,1000,-350,-205,-173,1000,-1000,-1000,453,617,536,11,236,200,-77,845,391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00596() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{1000,-1000,-226,365,1000,-103,-731,1000,615,883,45,-395,148,216,-679,-1000,-485,309,817,1000,1000,-271,658,-1000,485,-1000,1000,-1000,709,178,1000,-1000,1000,1000,1000,-856,-380,-951,-481,-534,711,-1000,1000,-1000,-790,-139,864,-1000,976,-360,-435,461,754,-655,-317,1000,1000,-351,825,684,-388,-960,897,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00597() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-169,1000,-361,658,-300,1000,382,822,-339,914,-1000,39,1000,1000,1000,990,1000,-666,1000,179,649,497,737,-450,-590,304,-840,-527,-1000,1000,258,-1000,1000,362,57,439,1000,-1000,-699,1000,-502,114,552,719,-320,-1000,1000,-1000,1000,31,13,-1000,1000,-1000,312,161,-28,784,-65,161,-555,1000,833,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00598() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{867,-39,1000,-1000,109,682,-606,663,-186,-140,-838,1000,82,289,334,-393,633,-425,839,1000,1000,506,1000,-981,1000,-393,-267,397,-100,509,1000,-1000,684,1000,950,-54,563,-71,248,605,933,-155,1000,310,92,317,399,-721,1000,-348,-334,-296,448,-354,-1000,911,323,-587,1000,225,-755,707,944,6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00599() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{788,-341,383,-1000,487,-103,-247,-136,192,936,-227,-1000,370,1000,389,-292,915,-658,794,99,1000,-479,693,-698,401,-988,239,-908,-234,915,793,-1000,956,753,763,-232,-36,-140,-267,1000,566,-1000,1000,314,-800,-125,532,-618,976,-693,53,48,476,-206,-838,690,1000,-419,692,3,77,-448,681,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00600() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{1000,-1000,152,-130,183,-205,-140,792,-485,1000,-933,88,454,967,7,-1000,379,105,437,1000,1000,-161,836,-697,528,-668,513,-778,-511,-1000,882,-1000,1000,1000,1000,302,92,-534,-816,258,-354,-1000,1000,-659,-741,-670,1000,-514,306,1000,-644,264,-380,1000,-363,1000,852,-1000,1000,-492,-108,-628,884,-581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00601() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-539,1000,-256,593,412,867,1000,100,-102,927,265,361,1000,47,1000,-31,967,-796,1000,29,-471,-385,499,701,-1000,-246,110,-313,-713,422,-612,-948,580,56,-514,226,-104,526,-743,512,-928,1000,1000,227,7,-1000,-746,-673,1000,217,251,-310,1000,-912,691,-267,-1000,621,-722,156,-306,1000,578,161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00602() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-52,942,829,-430,36,60,19,1000,-502,562,-571,519,861,720,1000,750,1000,-601,1000,568,371,-204,262,-104,-832,68,-695,-440,-953,424,305,-1000,556,79,96,919,-133,-1000,-980,898,265,-193,437,656,-542,-104,-236,-1000,1000,-96,-91,-303,1000,-607,262,261,-709,-164,314,495,102,828,704,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00603() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{981,-248,-426,812,-139,540,-652,-392,752,884,-926,715,497,-516,510,-764,463,-706,437,465,1000,1000,1000,253,605,-668,-123,-715,-1000,556,1000,-1000,938,1000,601,-373,958,-310,-980,514,-234,689,255,528,-730,-471,21,-596,306,-1000,840,69,171,395,-668,1000,-268,-42,447,-167,-1000,110,578,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00604() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{962,-301,953,-1000,437,199,-855,419,693,856,-683,-243,275,756,493,-992,915,-1000,975,-456,1000,703,1000,-1000,788,-1000,281,173,-285,515,1000,-1000,1000,1000,920,-1000,541,-7,-34,152,889,1000,738,230,-922,-224,1000,-752,1000,-913,321,-202,754,-231,-1000,805,1000,-358,1000,58,-825,72,680,454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00605() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{-277,812,-951,1000,662,-951,330,1000,274,1000,664,-1000,1000,-570,558,20,367,420,560,-350,507,-575,-77,-107,-775,-574,500,188,-150,1000,-518,-1000,1000,-373,119,-38,-744,-1000,-1000,614,-1000,-889,1000,-148,400,-1000,281,-913,1000,-86,-254,564,1000,-215,498,256,1000,972,-866,212,562,567,515,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00606() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{213,-926,-266,-568,410,87,-1000,519,1000,1000,-130,1000,-118,-1000,648,-1000,280,-333,1000,201,1000,1000,-516,-1000,1000,-1000,908,-1000,695,215,1000,-1000,1000,914,1000,-680,323,526,-773,-72,347,61,1000,533,-1000,-628,495,328,505,-1000,1000,894,203,1000,-1000,1000,1000,-1000,323,-1000,-1000,-260,402,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00607() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis():org.jfree.chart.axis.ValueAxis",
            new int[]{463,1000,389,889,684,838,127,1000,-1000,1000,-511,-373,1000,-415,393,319,1000,1000,1000,622,1000,-721,236,-679,151,-663,438,-1000,-609,1000,360,-1000,1000,377,274,755,-637,-1000,-1000,1000,-879,-709,1000,-776,-472,-1000,1000,-1000,1000,-1000,-1000,139,904,78,807,1000,-1000,300,-1000,640,88,635,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00608() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{1000,-470,798,-79,-1000,-430,-717,-692,-640,40,-346,770,371,676,1000,426,382,21,-1000,1000,-1000,1000,770,-1000,-400,-1000,-69,-649,-132,689,-1000,316,682,101,877,-537,97,-495,-710,697,-1000,1000,1000,-136,-1000,-431,-925,594,160,1000,85,-1000,1000,128,-755,1000,913,-130,666,685,-908,-1000,580,-332}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00609() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{1000,628,954,-505,-173,303,213,537,-1000,-330,1000,546,-650,207,1000,-855,1000,407,-1000,990,-1000,-696,858,-1000,-2,-1000,1000,38,-901,778,-1000,-1000,-625,-282,-1000,1000,-1000,-1000,-1000,-1000,-1000,1000,347,530,487,929,-778,1000,697,1000,194,-1000,1000,1000,1000,179,933,-129,-86,-47,-371,433,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00610() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{593,-1000,-196,519,-322,929,-24,408,-36,451,-17,-999,1000,1000,873,-3,-1000,-429,-197,-287,81,1000,-223,-499,1000,5,592,1000,-833,876,-1000,-1000,373,-238,957,90,1000,743,-554,-53,-235,465,1000,148,1000,-629,-1000,-1000,464,637,1000,58,-874,694,-932,1000,1000,1000,690,-488,-660,-1000,971,135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00611() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{81,-1000,114,683,854,1000,-1000,-267,686,-355,-927,-1000,1000,1000,-1000,-59,-1000,-778,365,-145,232,1000,-1000,740,1000,931,-462,1000,-1000,-276,-954,-1000,-653,101,1000,-1000,-734,234,-643,599,78,-665,1000,537,1000,-1000,-1000,-1000,695,349,1000,251,-1000,542,-317,1000,1000,521,659,-1000,-1000,-1000,729,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00612() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-70,-1000,794,535,-95,863,-1000,778,-405,-886,-605,-897,1000,1000,-558,-542,-1000,-793,-216,266,-366,1000,1000,-27,1000,168,-858,350,-313,636,-513,-1000,853,532,1000,-1000,586,-960,-474,373,-554,681,1000,524,1000,-863,-778,-1000,217,912,1000,-359,-400,1000,447,347,1000,823,947,-891,-1000,-1000,790,273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00613() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{1000,-621,181,-356,-376,-101,-856,1000,-594,210,812,-299,128,1000,765,-420,-13,-413,-1000,481,-1000,615,-1000,-614,1000,-797,685,-117,-36,-159,-1000,-1000,546,-649,1000,-42,-556,-1000,-617,-613,-1000,1000,1000,873,872,-253,-1000,515,454,1000,815,-740,767,1000,797,760,876,225,564,-707,-1000,-960,459,-435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00614() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{182,-724,-196,-650,871,-965,1000,408,206,-569,1000,1000,188,-1000,663,84,1000,-429,-460,1000,1000,-1000,-223,-1000,-1000,-251,-744,1000,538,-1000,1000,1000,-1000,-386,-1000,26,1000,716,-965,1000,629,-734,-191,-1000,-1000,-629,1000,1000,464,-729,-708,-158,537,-1000,720,-1000,-148,-1000,-450,26,1000,1000,971,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00615() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{1000,-659,1000,-434,569,12,1000,-222,-367,-533,619,730,316,-400,584,37,4,533,-397,884,-44,168,731,-1000,858,-742,-137,-484,-642,-248,775,452,-1000,-290,-11,731,115,-129,-393,-790,-90,167,595,295,551,-452,-169,303,76,533,404,-1000,334,97,-901,-180,582,480,731,600,1000,400,408,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00616() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{1000,1000,834,-409,441,-674,-25,801,-27,-609,-659,-491,-70,1000,-608,-688,333,-370,-1000,425,-1000,1000,212,174,1000,-1000,727,920,255,236,-1000,-1000,1000,867,-557,-1000,1000,-1000,-721,-725,-696,198,1000,692,1000,20,-1000,338,-43,1000,562,-524,579,555,529,671,111,-302,-196,-592,-1000,-1000,-20,-222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00617() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{924,793,-225,1000,-142,227,-177,538,-1000,-843,155,-118,484,-724,1000,-947,-682,-671,-1000,1000,-403,26,257,-471,1000,-1000,-1000,1000,-745,326,-1000,113,-73,-1000,387,-954,366,1000,-1000,1000,-1000,-721,772,128,1000,-146,318,-752,1000,133,440,838,1000,-1000,-703,-415,1000,508,1000,640,-864,-150,1000,107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00618() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-208,-43,-37,270,-110,791,867,399,903,-513,-468,-263,-1000,1000,-866,771,-298,-123,1000,621,1000,-682,-1000,-497,-254,689,-797,-928,-294,-122,971,378,-1000,576,1000,303,-923,869,-370,684,-363,-279,266,-52,-520,-276,-307,-708,-61,-627,394,-420,247,759,391,-765,274,98,168,-497,1000,-387,779,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00619() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{824,-1000,-812,1000,854,1000,-846,624,-36,742,201,-1000,1000,1000,873,-684,-1000,-1000,365,-811,-7,1000,-805,-254,1000,1000,15,1000,-1000,876,-954,-1000,1000,-867,1000,90,1000,1000,-307,-7,-985,866,1000,555,1000,-1000,-1000,-1000,933,1000,1000,782,-1000,1000,-317,1000,1000,1000,1000,-488,-1000,-1000,1000,130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00620() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{439,-1000,579,705,-805,-400,709,533,-656,18,1000,1000,33,-1000,498,-588,435,750,-215,580,1000,-1000,1000,-1000,-841,-385,-1000,-1000,28,818,1000,-231,-1000,-17,-134,373,-1000,616,-193,248,-1000,1000,390,-787,-108,172,-963,827,-800,86,-164,94,1000,1000,-253,-893,1000,1000,1000,995,1000,1000,158,618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00621() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{909,1000,-741,143,169,-1000,-774,605,489,-69,101,-874,94,1000,1000,-63,-376,143,-948,425,-1000,1000,-1000,237,-1000,-859,1000,1000,-138,-575,-1000,-1000,1000,1000,-25,-1000,1000,519,-931,-338,-141,62,1000,1000,1000,-866,-1000,-326,775,1000,1000,688,272,87,876,1000,442,-579,-501,-1000,-1000,-1000,19,571}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00622() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{1000,-1000,-626,391,-1000,1000,-1000,984,167,-747,-130,-235,805,853,549,-1000,445,-244,-369,1000,-1000,1000,-131,-639,700,-56,-230,821,98,-63,-945,-1000,1000,-1000,1000,-552,-281,-1000,-794,-251,-1000,1000,1000,632,872,630,-1000,651,-191,1000,1000,-1000,601,1000,1000,1000,1000,480,370,-1000,-1000,-712,1000,-722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00623() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxis(int):org.jfree.chart.axis.ValueAxis",
            new int[]{476,-1000,527,650,-114,-476,454,763,-104,108,859,383,847,207,911,-46,777,-333,-361,25,127,1000,-223,-1000,-421,-178,114,-1000,433,922,-1000,-931,-688,323,957,579,-556,-326,-202,-756,-652,1000,501,-648,-236,297,-816,827,-803,785,169,-551,1000,1000,170,-9,408,697,690,26,630,405,21,-980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00624() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{280,209,-22,430,-364,-679,719,518,584,637,155,-106,-326,18,-764,928,691,-793,-1000,-1000,-331,-1000,939,841,1000,935,-400,-703,233,-187,-284,571,-130,-169,-611,360,295,154,199,-6,-531,1000,-551,-93,488,-1000,264,-438,752,950,271,178,841,921,272,-1000,-1000,1000,-39,44,-508,-1000,-1000,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00625() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{-1000,624,-636,28,1000,1000,-25,769,669,-906,-392,1000,955,-234,-738,-1000,630,-84,-1000,-498,804,197,94,335,1000,172,1000,-1000,792,-730,-638,604,1000,-577,-707,-1000,1000,-1000,1000,-1000,-695,25,1000,-353,69,-779,491,327,-35,-511,-1000,368,415,-1000,-1000,140,693,111,819,-1000,-962,-796,-895,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00626() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{735,460,-901,-847,-716,448,599,-518,-82,43,769,333,-1000,777,-453,455,-1000,1000,1000,526,-357,30,-1000,518,-626,-762,835,593,-385,-898,-594,1000,-567,13,-1000,-411,517,307,350,493,-97,-1000,1000,117,-819,365,-1000,-420,861,-742,438,7,-674,810,878,-601,-754,-846,909,-835,-720,-497,826,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00627() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{368,762,829,541,-80,-158,139,-898,-892,520,312,-44,-417,-487,-792,121,287,1000,-333,985,514,1000,-589,-486,-718,-618,835,79,408,835,-104,-668,-572,351,-386,-555,186,-122,519,133,-429,-541,-469,-146,-497,775,551,-550,1000,16,-412,-1000,-478,-970,723,397,989,-522,840,-858,960,343,183,-395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00628() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{1000,1000,459,36,-1000,-812,166,-1000,-126,1000,-539,-688,-1000,-197,-1000,438,-242,-920,1000,-264,863,-64,-1000,464,-1000,-800,363,-464,-66,-698,699,1000,-411,306,-1000,46,-240,-235,-1000,1000,549,-1000,-1000,-185,-212,1000,-106,440,-1000,-326,482,-709,-230,1000,1000,-1000,1000,-1000,856,1000,-757,840,-168,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00629() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{650,1000,-956,-688,-197,2,339,-124,-105,1000,497,1000,-1000,-487,-464,1000,-1000,868,-244,-164,-1000,-18,-1000,1000,-626,-290,567,1000,187,-1000,-646,1000,-1000,-512,-1000,-178,194,580,200,847,410,400,953,-146,-828,775,274,-1000,861,-742,616,872,-823,1000,461,-1000,-1000,-846,571,-484,-1000,-736,1000,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00630() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{-487,-425,752,-103,308,-328,818,-199,524,-10,1000,533,1000,-595,-53,-870,-345,-624,-936,-1000,-482,-1000,1000,1000,1000,-1000,-1000,606,124,84,-76,527,-1000,-1000,847,877,739,-1000,-1000,436,801,260,-1000,487,123,79,698,-1000,308,304,-177,912,1000,-231,1000,-1000,-568,807,-1000,289,-110,-641,-1000,390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00631() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{-258,-861,893,-1000,479,-404,813,-36,521,516,864,-60,615,-611,-1000,-923,239,997,638,-758,945,38,961,207,-76,-1000,1000,396,273,-881,-712,55,-806,-700,247,-539,360,-931,387,249,-10,-230,-823,-138,604,251,1000,-1000,588,-543,-1000,-637,905,-289,735,33,336,-1000,-1000,-845,-523,153,-11,456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00632() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{-796,1000,-376,-234,-212,565,-700,448,98,494,-810,1000,0,47,-832,-488,-770,-114,-927,-765,28,1000,-841,958,9,-556,-1000,-67,1000,-1000,-1000,1000,-335,-937,-1000,-990,677,-816,11,-59,663,25,721,310,-419,-170,-447,-438,-1000,-998,-764,793,-500,278,-437,-489,-707,964,96,-62,-1000,-1000,-58,23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00633() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{-699,-1000,-552,-528,4,610,890,1000,-1000,-553,-1000,-116,337,1000,-170,1000,-1000,-59,363,1000,-951,1000,-427,-1000,-817,439,-17,273,-1000,-190,-151,-683,315,917,66,-1000,-1000,-143,-232,-992,1000,-759,675,-86,26,-342,-1000,228,331,1000,-805,-791,1000,-533,-840,-489,1000,964,372,-413,-207,-1000,403,160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00634() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{-609,-834,409,155,-539,-182,232,371,-468,-84,-332,248,-120,149,-1000,248,-662,20,-76,670,-46,465,-1000,400,289,-911,101,288,-427,-1000,-430,-388,-256,-1000,476,600,-8,298,-1000,-400,-584,-564,-899,-373,-266,419,-1000,544,-186,-702,-573,555,35,-199,93,-462,-573,-493,189,15,-295,598,-489,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00635() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{-796,-160,-86,1000,-687,555,603,-1000,98,-311,-951,-399,-539,47,-1000,-556,-770,153,172,870,1000,1000,-1000,-231,-533,60,1000,-1000,188,456,-22,555,1000,60,-1000,-824,628,833,939,296,-1000,-1000,-159,-1000,-748,548,-1000,1000,94,413,304,-1000,-581,847,1000,452,965,236,1000,-132,927,1000,707,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00636() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{556,-762,453,-370,-433,-580,121,131,666,1000,-260,74,-302,-831,-70,-19,-632,190,-340,-640,358,-396,-264,1000,1000,-1000,-1000,775,634,789,-124,-506,-1000,-1000,1000,734,874,355,-1000,-189,-1000,-299,-1000,207,-218,517,1000,611,-239,-442,560,1000,371,810,735,-1000,-249,337,-777,1000,417,-394,-706,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00637() {
        org.junit.Assert.assertEquals("java.lang.Integer:NjI=", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{-1000,1000,-201,-839,723,-366,-554,-242,186,741,61,-217,-594,63,-556,-812,-381,801,1000,-811,560,-1000,-1000,860,-416,-1000,-104,385,-345,-1000,-841,1000,-1000,-504,-916,-405,728,-822,-29,768,1000,-1000,-432,690,-483,553,-74,-1000,-677,-427,-535,153,112,491,1000,-1000,-688,-1000,772,516,-556,139,737,-211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00638() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{-375,93,111,19,-292,528,34,40,-588,-717,-910,585,-248,682,-1000,889,-289,-64,762,365,258,-1000,-464,784,-8,947,560,104,-757,-1000,110,565,258,315,-1000,809,-312,765,-1000,-1000,-409,-425,-1000,-403,-133,476,-585,-379,-1000,262,-952,670,441,-699,-605,-542,-895,-431,221,760,-1000,339,140,310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00639() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisCount():int",
            new int[]{-392,1000,-273,-328,-374,314,-189,-522,128,1000,382,174,-1000,837,-955,549,-963,391,1000,1000,777,-859,-1000,934,-387,-370,1000,462,732,-585,-1000,1000,55,-515,-1000,-927,411,-805,287,-238,49,-1000,790,2,-535,462,-840,-84,-1000,-1000,21,146,-764,1000,-527,-664,-760,-1000,313,257,-1000,142,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00640() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{850,3,134,1000,289,76,-101,944,-741,-478,605,736,552,298,164,980,685,741,-264,-884,229,1000,-961,441,-275,694,-539,195,880,918,-531,841,-165,-165,993,-1000,153,65,-559,720,-747,316,-346,127,1000,194,1000,572,-705,-72,617,-739,68,557,-638,-1000,660,347,167,-262,518,474,358,113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00641() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{121,1000,354,-274,289,-904,-525,189,404,23,-458,-822,550,1000,303,-282,619,957,-79,-635,-362,-694,-23,290,-619,82,-539,-765,-240,-400,-481,316,350,-234,-935,117,214,275,85,558,1000,87,-613,625,-1000,751,139,-571,164,1000,656,-267,-161,-311,-511,504,-758,488,943,-919,575,483,-142,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00642() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-665,-1000,209,1000,-892,1000,384,-19,-355,12,410,664,-265,-590,-224,826,369,376,-1000,-865,580,1000,1000,-1000,586,1000,-1000,1000,476,1000,400,846,901,274,-754,-574,-616,466,-1000,84,-1000,-612,1000,-1000,1000,-121,-194,448,-358,620,-688,142,835,1000,1000,-185,1000,-1000,-866,-15,1000,-698,-66,281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00643() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,526,474,687,-974,-433,1000,-548,219,686,-847,824,-539,621,672,109,-325,817,-1000,-778,-533,1000,1000,-1000,894,1000,-862,-91,-46,823,1000,409,1000,-1000,-1000,1000,-605,198,-1000,-1000,588,-988,1000,-656,-698,-354,-403,-609,1000,-249,458,1000,1000,81,1000,747,-1000,-1000,393,-230,1000,370,-559,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00644() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,-1000,-567,701,275,997,208,813,-1000,245,-1000,1000,-1000,722,-838,407,-873,326,-1000,-575,881,1000,580,-1000,1000,711,-540,-60,-939,174,521,-335,685,-191,-130,517,-786,1000,-851,560,-1000,-67,-82,-1000,1000,1000,-942,-303,-921,-1000,927,341,1000,957,904,-608,132,-1000,-1000,855,755,377,88,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00645() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-483,79,753,-422,362,181,-525,733,604,-1000,750,617,1000,554,138,1000,1000,627,-57,-436,314,-1000,-611,643,-98,234,-539,-765,1000,-400,434,-559,1000,264,-436,-464,692,275,8,1000,653,87,-431,1000,91,-845,484,78,-63,506,1000,-267,-428,750,-875,-35,-701,457,235,227,444,712,-309,679}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00646() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{49,76,259,-732,674,-578,-63,698,-235,-638,126,-588,462,644,896,213,708,1000,-496,-215,26,-890,-462,721,-747,-971,237,-574,328,-1000,-1000,-1000,62,362,-165,-98,1000,-447,136,1000,1000,552,-879,1000,-599,-485,-171,-150,-156,676,636,-1000,-638,192,-661,206,-346,1000,1000,-302,450,60,-240,666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00647() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{391,586,693,859,52,-660,1000,823,-610,-102,151,-351,-78,464,1000,-217,536,846,-476,-628,73,-198,610,-380,-554,956,-601,-574,-447,-1000,-690,493,395,162,482,-602,-23,-320,-300,-135,6,417,302,-247,-318,23,-171,1000,-204,1000,-1000,-306,487,511,167,-584,159,-585,416,-1000,545,-947,-240,666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00648() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{719,650,659,980,-601,-9,-1000,929,-253,-603,877,640,41,547,88,417,421,569,324,-1000,-533,1000,-1000,-453,144,1000,-1000,479,1000,1000,1000,1000,988,-1000,605,-1000,-318,-472,-1000,109,-1000,119,1000,-893,199,10,1000,-609,-962,766,-611,605,1000,620,-1000,-1000,774,-875,1000,-1000,809,805,764,813}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00649() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-550,421,590,512,-801,245,-601,-1000,1000,-545,1000,485,1000,210,1000,1000,1000,113,-420,-1000,133,-745,1000,-913,141,1000,-1000,1000,1000,1000,1000,638,949,-68,-301,544,-407,-1000,-346,-405,750,-1000,1000,791,-203,-1000,-211,-236,973,1000,765,199,318,598,697,1000,-513,-821,-34,238,838,460,-115,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00650() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-428,239,315,-767,129,-781,-671,698,-2,686,635,-358,287,1000,-196,109,515,949,-91,-238,300,-476,-667,782,-555,1000,-13,-778,530,823,1000,-1000,853,-560,-1000,-43,1000,-835,-844,1000,1000,581,-1000,-656,-714,-374,1000,-77,289,-249,282,-161,-1000,400,-1000,-300,-861,1000,1000,-196,287,506,-324,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00651() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{16,302,148,-274,125,-608,-88,361,908,73,89,-146,686,508,415,799,780,903,-278,-596,996,182,-373,210,-382,-131,-405,-1000,156,860,-311,204,-334,292,395,-1000,160,68,94,1000,-223,445,-155,-83,124,178,-835,-700,-193,249,1000,-272,-331,-373,-258,-332,157,919,428,-402,589,775,-261,535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00652() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{696,1000,274,1000,-386,-244,817,60,-512,-345,-196,1000,-980,540,642,-19,-476,957,-691,-635,-202,1000,733,-630,784,1000,-773,72,-97,966,721,-1000,704,-1000,-935,400,1000,352,-1000,-490,607,-227,462,-1000,-234,49,677,-10,374,-400,-54,1000,-161,191,333,-233,-772,-871,174,-138,760,382,-178,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00653() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{45,630,409,-446,-46,-435,454,-1000,-355,30,410,272,1000,303,-519,989,942,1000,-648,-310,-878,-544,-23,134,-281,632,357,401,58,-400,387,-400,-702,-88,-754,1000,352,-656,983,-555,1000,-717,-1000,957,-394,-1000,-613,-1000,934,455,1000,-235,-181,-554,353,1000,-902,400,356,-15,615,883,-156,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00654() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{57,1000,159,-644,1000,-293,-362,-1000,-480,-822,358,-497,920,1000,889,645,1000,1000,-188,-134,-911,-890,-434,1000,-847,-1000,150,72,916,-1000,-77,-1000,-943,340,-822,-836,1000,241,-22,1000,1000,894,-1000,1000,-1000,-356,636,-87,61,-34,1000,-1000,-1000,25,-924,-669,-584,1000,453,-1000,226,242,-257,455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00655() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge():org.jfree.chart.util.RectangleEdge",
            new int[]{-485,239,-181,-1000,667,-781,-1000,698,344,26,-561,-631,496,1000,-500,900,806,951,-41,-205,113,-1000,-462,782,-555,-235,-267,-1000,667,-1000,-749,-779,1000,487,-880,-51,-663,-877,-210,855,1000,552,-1000,1000,1000,337,379,-457,-120,1000,666,-659,-494,556,-1000,292,-807,1000,1000,-439,421,494,62,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00656() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{1000,-1000,-676,-105,-124,173,485,1000,140,-397,421,1000,-1000,72,1000,1000,-1000,-1000,582,-1000,-1000,-834,136,400,-472,-575,-586,1000,-285,876,1000,-1000,-1000,894,283,-1000,1000,-805,205,-87,1000,-148,734,-35,685,1000,-472,-293,-1000,-619,774,1000,949,416,741,282,-1000,-479,-939,-297,57,-20,434,-993}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00657() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{685,886,-171,648,-141,301,624,1000,436,-1000,270,1000,-1000,-750,1000,1000,1000,-935,1000,-344,-1000,-675,-1000,458,-1000,-1000,-602,1000,516,1000,1000,-1000,-1000,894,905,-1000,1000,1000,443,276,-676,-982,388,-35,354,1000,-324,-593,-575,-136,1000,953,1000,-454,1000,748,-738,163,-1000,-284,-103,-74,357,-993}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00658() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-167,-89,896,-236,433,84,-16,828,-397,-997,877,465,866,-291,-297,699,232,-60,-327,901,-794,-414,418,-226,699,-611,-661,37,-296,991,511,485,867,-883,993,120,-73,509,303,-858,-585,-925,206,-295,972,633,-984,-412,-835,-348,-855,140,805,388,766,265,-615,-32,155,522,539,-865,527,-62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00659() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-472,-11,11,1000,423,-211,338,1000,850,-148,604,1000,237,1000,-26,1000,229,-1000,23,-902,-600,-339,736,400,-1000,273,-489,1000,-362,148,-289,-463,-132,-1000,-118,-505,369,-692,1000,-1000,-77,-152,682,1000,-241,1000,138,1000,-1000,-278,1000,-975,347,520,452,-215,177,1000,-824,502,-794,144,1000,-209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00660() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{292,569,-581,-14,-924,566,-214,1000,559,-1000,1000,1000,-386,511,1000,381,1000,-721,-538,211,533,-1000,525,1000,-471,-777,-298,743,-1000,-1000,1000,968,-919,542,-600,-692,1000,-1000,-1000,-821,-226,-1000,799,-1000,483,573,725,-352,91,-930,-299,1000,127,19,700,19,-851,214,813,-1000,-1000,-577,-499,137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00661() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{605,638,-346,-514,-846,-429,469,-168,-1000,1000,166,76,1000,-917,63,-534,-749,1000,-96,-1000,-281,-1000,1000,-1000,535,1000,1000,-1000,-281,217,1000,-747,-102,1000,-1000,-872,-1000,-1000,-1000,-423,523,689,-883,-592,844,-762,1000,-324,-472,-786,-531,964,-1000,771,-1000,-879,-1000,-235,713,-288,-732,906,281,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00662() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{550,-123,-279,75,-1000,-207,271,1000,-229,-1000,637,1000,-727,112,1000,-35,465,-907,146,-63,-347,-772,369,483,-713,-949,937,1000,144,-756,1000,-432,-1000,1000,-111,-1000,1000,-951,-660,-386,694,-476,963,-1000,527,609,1000,-939,172,-311,437,932,264,-243,923,142,-626,-83,592,-981,-988,-92,284,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00663() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{1000,-1000,-421,-21,-315,868,685,1000,-101,-449,-676,-1000,-1000,1000,331,549,-1000,1000,1000,-1000,-1000,-1000,136,1000,227,157,1000,-880,752,504,1000,-776,-1000,1000,-1000,-1000,-1000,-173,562,976,1000,-702,-1000,1000,1000,1000,1000,-293,-1000,-1000,-1000,134,949,1000,-1000,-69,-1000,-1000,-939,1000,-219,646,-200,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00664() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{1000,886,429,181,19,248,1000,307,-838,290,-979,-400,-467,645,111,559,-1000,-330,252,-1000,-1000,-167,413,-571,-423,825,-86,323,1000,1000,382,-1000,-218,-19,-179,-1000,-75,595,-666,500,1000,-1000,-217,912,1000,96,-428,877,-1000,-167,-416,535,578,1000,-659,-972,-1000,-1000,-1000,1000,620,932,-519,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00665() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{331,508,-827,-707,780,681,27,488,1000,-682,-128,-494,-554,-641,681,-745,637,564,-988,-221,1000,186,-352,1000,61,-96,507,-694,-649,121,393,1000,856,1000,-705,-432,-356,146,383,549,722,-307,-788,-262,624,1000,-1000,280,-933,837,537,527,-1000,-886,-98,539,-1000,130,1000,-1000,502,-23,202,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00666() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{597,-84,53,-17,-273,28,532,228,-575,552,146,400,428,-803,682,392,640,-1000,421,69,-611,664,73,-1000,-1000,578,-330,1000,-1000,249,-625,-33,-863,-791,962,-320,865,-164,-421,-637,577,-633,657,-1000,152,-1000,-875,51,462,580,264,925,946,163,346,-201,-738,189,381,-387,239,22,109,289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00667() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{975,797,-661,960,-1000,-482,-419,848,-607,-361,1000,1000,-405,243,1000,1000,798,94,-62,259,87,-1000,1000,495,-580,309,-208,590,-1000,-820,1000,-80,-1000,1000,-163,-1000,1000,1000,-131,-421,300,-195,153,-1000,1000,356,260,212,565,-20,439,691,-176,384,-83,401,-697,-231,1000,-1000,-737,-846,491,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00668() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{620,-148,428,355,-193,-932,-1000,-1000,1000,304,-710,-155,1000,-430,191,265,-1000,-625,-512,1000,-475,-695,719,-1000,-668,107,680,169,-435,1000,-227,-119,-419,-1000,-513,-223,525,-452,723,-492,-944,354,1000,1000,-950,-1000,-187,-500,-985,-697,-77,680,899,336,-154,571,-256,-138,413,1000,1000,988,-74,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00669() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{2,-814,-521,-400,620,909,61,-840,-172,288,152,974,-455,96,-515,-751,-979,329,-38,-321,254,-994,717,419,-758,655,-534,-751,-883,227,869,319,-526,948,952,-156,-939,382,514,220,403,-125,-122,174,915,459,368,757,-439,-738,418,-742,-744,560,388,-543,823,687,-168,-981,569,189,207,-714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00670() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-1000,1000,-147,156,-395,-622,-741,-355,175,-922,1000,1000,-616,-1000,70,-561,1000,235,-1000,964,930,-440,-778,1000,1000,-611,-114,-38,-960,-1000,-420,1000,607,-314,-81,359,1000,-1000,162,177,265,433,692,-1000,948,552,-868,-124,609,1000,596,-77,805,-729,806,682,-1000,515,1000,-1000,-428,-1000,-326,787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00671() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.util.RectangleEdge", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisEdge(int):org.jfree.chart.util.RectangleEdge",
            new int[]{-510,466,-202,886,-1000,-779,346,497,-786,-163,1000,1000,75,191,464,558,379,645,785,-113,-1000,-292,310,-564,-24,411,-788,1000,232,-495,276,-677,-970,222,-418,655,1000,-1000,-545,-634,-52,564,469,-648,-475,-162,151,545,233,-669,341,614,218,566,132,-751,-675,581,-994,604,-822,-214,84,-951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00672() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{374,1000,-6,812,-706,-601,653,-203,650,540,1000,-1000,-25,-449,-1000,-1000,670,728,199,673,1000,1000,-340,744,-1000,1000,-963,-511,-1000,1000,-1000,-1000,1000,-842,324,-535,-399,-167,-1000,-1000,52,-1000,-1000,-692,538,495,-751,-307,-810,1000,-1000,552,-62,-887,-230,-553,-597,-377,1000,1000,434,-437,1000,-507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00673() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{155,-288,-31,311,1000,-708,25,1000,906,407,-42,-261,-410,-243,-389,275,251,759,970,-258,-292,-1000,487,-1000,192,-688,-978,-151,807,674,-416,732,284,437,-107,-408,281,-627,-29,676,153,814,-1000,-92,-1000,678,-9,-69,-729,630,600,-479,202,483,-291,678,214,-240,-536,-836,84,266,-404,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00674() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{645,-17,639,1000,703,69,17,-520,578,486,1000,-632,1000,421,-28,-77,1000,350,613,-1000,323,-605,632,-366,-807,210,-1000,-617,-940,1000,-1000,-232,31,-948,216,593,256,7,-676,-265,-848,-1000,647,310,251,51,478,72,-688,103,-12,-1000,-322,766,611,1000,274,371,401,378,611,-603,-692,350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00675() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{437,404,854,594,-228,1000,-955,-1000,955,-94,1000,502,-658,-423,-797,-1000,1000,813,-1000,-1000,373,-214,608,264,-1000,1000,336,34,-831,-718,-1000,-357,-1000,-1000,966,679,1000,1000,-1000,-128,-910,-1000,-705,1000,-233,-1000,157,-1000,-511,584,1000,-1000,-996,1000,75,945,-173,-495,-165,-1000,-34,-953,392,-13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00676() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{686,315,254,68,-462,-962,-312,-668,928,-283,682,174,-172,-843,-1000,-140,523,400,-624,712,402,671,636,630,-231,371,-1000,577,-602,-383,-693,270,1000,1000,-1000,-557,-683,869,-875,556,1000,-1000,374,266,-83,879,-537,177,162,-208,-1000,1000,-1000,-1000,1000,-1000,366,-446,1000,553,699,1000,689,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00677() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{1000,612,-656,-173,-1000,1000,-67,-401,339,-831,-563,-36,269,1000,205,1000,-529,609,-870,831,-535,-339,-1000,612,581,933,919,878,185,-1000,604,-3,-1000,-40,-374,446,-578,331,106,1000,-165,-288,-1000,1000,211,-1000,1000,-1000,-698,-287,764,50,-679,14,405,-254,-1000,700,-1000,-791,-754,294,-909,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00678() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-33,-1000,-236,-132,1000,-589,-467,833,840,327,-746,485,-629,-578,828,977,-118,728,999,-589,-602,-1000,923,-1000,1000,-1000,-891,141,1000,674,34,1000,284,1000,-650,-598,739,-52,-29,1000,340,1000,-1000,98,-909,507,347,-61,-537,-917,1000,-608,-351,660,-248,1000,1000,-538,-1000,-1000,953,755,-1000,-441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00679() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{1000,340,854,222,214,-1000,1000,579,609,1000,472,-1000,-1000,-1000,-76,-1000,532,1000,1000,412,1000,-39,1000,-1000,-100,-895,-1000,-230,-1000,1000,-1000,-1000,1000,-340,272,-1000,-26,-354,-1000,-1000,-668,-1000,1000,-1000,608,1000,-910,569,-1000,1000,-1000,232,62,-610,-889,-97,1000,945,520,1000,999,-162,976,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00680() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-1000,-297,32,-176,-1000,-349,214,920,672,-545,62,666,581,478,449,260,1000,-1000,-835,350,1000,102,507,1000,1000,1000,516,607,1000,-689,-35,610,-554,628,110,1000,-1000,-7,-716,682,-1000,799,237,297,85,-1000,1000,-782,-3,966,1000,456,-1000,1000,619,-209,-278,1000,-599,-1000,-179,124,-865,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00681() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{887,1000,-623,-661,-1000,1000,-28,-1000,-350,-474,-480,-555,202,-337,-605,1000,-701,-361,-1000,962,-462,714,-1000,1000,581,1000,1000,705,-758,-1000,1000,-179,80,308,-1000,-608,-776,1000,-196,718,998,-310,-908,1000,1000,-1000,979,-1000,113,-893,-573,1000,-1000,-1000,1000,-1000,-1000,191,1000,713,-1000,489,127,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00682() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-170,-1000,84,823,612,1000,174,-1000,319,-1000,147,59,940,1000,1000,1000,744,93,266,-879,-1000,-415,-159,-834,-177,-706,1000,-625,-940,-1000,604,-341,-1000,-435,248,1000,-218,-537,1000,1000,-1000,1000,-1000,1000,-1000,-732,1000,195,198,483,-12,-546,98,753,1000,1000,-813,1000,-1000,6,-857,-535,-692,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00683() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-350,-312,-637,-172,-178,-751,157,420,557,-20,-436,401,-912,-1000,776,123,-269,788,199,715,519,1000,852,26,696,370,-768,15,-269,1000,-81,1000,1000,546,-759,-1000,-121,-441,-1000,-305,874,-218,-54,-725,-193,502,-396,-175,-410,-59,-645,954,-768,-1000,-430,-561,676,-1000,-320,877,1000,743,416,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00684() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{686,254,129,1000,568,-215,137,-763,960,1000,170,-2,-476,535,-1000,-872,1000,1000,94,346,300,237,476,-70,-342,371,5,316,-1000,845,-920,-1000,-420,-1000,1000,922,744,-42,-992,-1000,-549,-514,569,94,-54,-246,700,-494,-1000,1000,203,-875,409,1000,-755,910,262,-138,1000,-502,-16,-1000,739,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00685() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-312,-945,-535,1000,-71,354,107,297,912,973,-852,1000,-872,-428,87,-178,1000,1000,-617,688,484,-391,-296,-35,851,1000,-35,-320,730,651,-805,-657,-1000,-952,-263,1000,112,-194,-1000,-1000,-972,819,248,-369,28,-640,791,-619,-1000,1000,1000,-770,1000,1000,-1000,1000,-145,-152,126,-1000,-325,-494,374,-622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00686() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-374,-687,765,700,-331,1,915,-628,399,-639,238,-443,-129,559,-1000,-143,1000,376,278,599,-731,1000,-38,-156,-71,136,385,-190,341,-111,-132,331,-268,226,631,91,-1000,-1000,97,253,96,331,-38,50,-1000,30,372,-238,38,1000,540,-750,712,-355,38,-162,-357,304,-370,142,436,155,342,301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00687() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisForDataset(int):org.jfree.chart.axis.ValueAxis",
            new int[]{-1000,424,-501,-1000,-211,-1000,594,59,742,1000,-382,-738,-1000,-1000,-1000,-1000,-894,-66,469,252,779,1000,611,-88,265,-403,-1000,-804,-89,987,-654,-712,1000,1000,-811,-272,-162,172,-573,-360,1000,718,-378,-265,-909,1000,-591,-440,-60,1000,-1000,1000,50,-1000,-128,-1000,244,-1000,31,1000,-147,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00688() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-1000,-106,518,-439,-1000,-484,-682,186,236,903,-447,1000,-1000,979,-533,110,530,-1000,73,787,-60,1000,1000,-1000,491,-700,-966,-318,-354,-788,-578,1000,861,284,-646,1000,955,-848,1000,1000,-1000,1000,-708,-816,543,235,352,-1000,-503,-958,-640,-43,-523,-421,1000,-660,443,1000,340,1000,-56,856,-497,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00689() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-283,-1000,329,-718,-805,1000,-20,854,-55,873,734,1000,-22,411,-382,1,466,-296,-94,-868,660,582,789,-373,362,-414,-800,-872,564,-302,501,1000,-1000,585,140,1000,946,-643,750,1000,-784,782,-1000,470,117,-408,-1000,-719,-482,-780,608,231,-1000,-1000,1000,-419,241,1000,305,440,-783,153,153,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00690() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-343,-1000,-862,-895,-805,1000,-603,697,25,790,752,1000,-597,-1000,-707,-210,1000,-202,-302,-653,662,752,1000,1000,243,-356,-624,-647,796,-571,-825,1000,-924,488,52,-1000,1000,-1000,861,1000,683,782,-1000,923,10,-1000,-1000,-1000,-463,-978,-1,405,-1000,-115,1000,-470,122,1000,-174,-807,-478,621,311,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00691() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-1000,47,-57,950,-1000,472,49,-51,182,-283,-581,-69,-879,1000,51,469,-189,-810,-229,-894,-351,-204,-147,-71,492,179,-1000,-306,-230,-581,389,317,-157,-670,265,-364,835,-514,1000,525,-677,1000,260,-139,177,978,-311,-685,-446,-716,692,327,775,-799,851,-96,718,-80,19,545,60,431,974,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00692() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-702,-704,19,-309,1000,806,-1000,-902,-1000,-781,-1000,102,248,454,276,-997,-1000,194,171,1000,-102,208,-865,141,-1000,-661,555,-520,-900,-163,-292,-1000,-229,-126,-837,-694,59,1000,-716,-959,178,-1000,1000,-1000,59,-1000,752,1000,-771,87,1000,233,-512,576,603,-521,557,-136,1000,-1000,-1000,107,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00693() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-846,-1000,849,517,-1000,-218,-715,507,-525,-160,892,-32,-612,-152,-911,1000,1000,985,-763,-397,-498,512,744,-495,700,1000,-59,245,-208,-971,210,137,-1000,-104,-656,638,1000,-276,427,891,-1000,1000,-936,-189,-1000,1000,-1000,-1000,-1000,636,-921,-407,-383,-541,1000,352,367,577,-257,277,-22,159,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00694() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-196,-986,838,999,565,-353,379,547,-916,-636,1000,-462,-646,-1000,-1000,1000,257,-524,-1000,650,111,768,178,1000,202,967,1000,1000,-416,487,123,-104,-1000,-139,457,-375,685,-108,-1000,-334,-1000,-84,-703,-572,-894,192,-1000,-252,-549,4,-898,241,-512,1000,-107,133,-404,-1000,-654,-1000,732,1000,981,716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00695() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-1000,-1000,649,446,-1000,-1000,-313,-577,-96,-56,1000,1000,-1000,967,-798,647,-189,-1000,-329,159,-849,-1000,1000,-1000,312,841,-167,664,248,2,-431,931,-1000,-905,-1000,1000,-314,-438,440,812,-1000,1000,927,-158,-1000,1000,1000,-1000,-1000,1000,-1000,-906,-878,1000,1000,447,208,1000,-632,1000,361,1000,1000,-925}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00696() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-832,-106,308,-1000,-1000,463,-425,353,325,1000,-538,1000,-563,1000,-100,145,643,-745,707,-520,702,1000,580,-1000,484,-1000,-1000,-1000,132,-922,-198,1000,861,791,-385,1000,1000,-1000,1000,1000,-849,1000,-1000,-176,1000,-265,352,-1000,-322,-1000,231,725,-523,716,1000,-1000,645,1000,794,1000,-628,1000,-906,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00697() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-503,1000,509,-24,-707,527,-263,-722,-1000,268,920,-375,-91,-638,-854,246,-351,-428,-1000,650,1000,818,-1000,482,271,-1000,217,-56,-7,164,1000,596,-1000,323,670,-72,-314,-20,-673,129,-131,-84,360,-184,235,-482,-930,315,-431,446,306,-480,-60,376,15,-314,-652,-549,332,-865,-601,193,388,770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00698() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{60,-214,9,-354,481,1000,339,-296,53,1000,100,189,516,177,-503,390,1000,34,-363,-868,286,-283,309,-373,468,66,-760,-165,32,-302,-169,-99,300,311,197,700,1000,-287,299,327,-477,-305,-981,-195,397,-281,-232,-187,-42,-451,1000,-593,-849,-1000,1000,-569,-315,1000,546,773,-387,-806,-83,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00699() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-164,-1000,644,804,-390,492,527,188,27,-142,1000,157,-74,1000,48,858,-12,-269,-987,-159,-424,-511,347,322,157,1000,646,612,813,679,-1000,-226,-1000,-241,-877,610,900,247,-511,329,-849,203,796,1000,-1000,317,54,-525,-803,1000,-127,-808,-1000,595,708,-1000,-11,1000,-341,-792,-154,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00700() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{96,-661,-176,-951,-691,-937,713,-277,-422,1000,176,358,940,327,0,1,952,-24,166,-1000,1000,320,928,-721,597,-1000,-1000,-1000,171,-920,438,1000,219,916,522,669,1000,-1000,1000,1000,130,311,-1000,745,1000,-280,-749,-556,-293,-1000,739,1000,-12,-1000,1000,-611,398,1000,1000,954,-658,-666,98,-887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00701() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-124,768,759,654,-1000,-870,-272,-123,528,-70,-39,1000,-920,270,-1000,-581,530,331,-979,1000,648,-381,-1000,1000,-1000,-921,395,98,-548,200,-963,242,861,-770,676,-533,-1000,-609,884,-183,-958,-511,-613,-468,1000,138,-251,125,-169,281,-30,-875,-1000,649,-562,-1000,758,-817,157,-185,113,1000,-497,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00702() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{504,498,874,-62,26,-1000,1000,109,412,-1000,279,707,-1000,-1000,-802,1000,443,1000,-430,1000,272,-567,-545,1000,134,-461,1000,807,-322,1000,-1000,514,-1000,-875,1000,-1000,-1000,-598,-194,147,-1000,-1000,-333,84,-99,-1000,-249,973,519,-400,-1000,180,-1000,1000,-1000,288,111,-1000,-1000,-1000,1000,39,-454,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00703() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisIndex(org.jfree.chart.axis.ValueAxis):int",
            new int[]{-687,374,-976,594,-1000,647,-581,854,-1000,1000,1000,-908,-22,223,-846,1000,466,49,-837,-868,-610,424,1000,-1000,1000,711,-1000,-872,335,-302,992,1000,-1000,585,330,1000,1000,-1000,1000,1000,-197,1000,-1000,923,-1000,1000,-1000,-719,-1000,605,-1000,231,-91,-1000,1000,836,516,1000,-123,1000,552,-794,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00704() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-833,-377,571,225,-292,253,311,802,-652,-659,1000,-772,-53,71,-148,322,244,1000,-682,101,905,-235,551,1000,193,-361,171,-264,-582,-835,-237,-138,-302,59,512,-455,657,-345,812,346,398,231,-36,217,-31,293,408,-749,-1000,163,-509,-537,-457,1000,1000,774,196,-674,-29,-302,672,97,-609,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00705() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-61,-1000,163,370,942,433,189,211,-1000,-1000,-1000,-69,-460,100,-384,342,-1000,1000,-1000,-31,950,260,1000,1000,-1000,-1000,-537,-455,-1000,-1000,-1000,63,-447,491,517,-161,831,-1000,304,757,1000,1000,-1000,781,392,23,1000,1000,-399,817,-1000,-404,181,619,-146,710,47,-594,469,1000,142,-214,-983,-797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00706() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-256,229,1000,870,-606,43,-344,-309,-1000,-760,232,-418,409,-204,-484,508,-428,1000,-747,733,-13,172,-453,1000,-1000,62,661,-392,-312,-1000,-889,-103,270,-345,519,489,778,73,101,-231,180,1000,880,1000,106,-508,-106,1000,259,-671,528,-1000,-12,1000,-1000,824,610,-957,213,1000,-199,347,-247,-493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00707() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-155,-305,232,372,481,761,-322,-644,-1000,-819,-518,461,957,226,-337,420,-1000,1000,-943,834,899,-870,642,879,-1000,-704,890,-333,-1000,-1000,-580,452,670,327,-64,-493,1000,-488,877,439,591,614,880,-155,598,978,727,123,-1000,-640,103,-1000,-522,1000,756,571,-194,-1000,-271,1000,172,-72,-625,742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00708() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-155,-747,1000,1000,-606,350,-530,-886,-536,-693,232,678,879,375,-423,377,-1000,1000,-1000,473,963,-468,279,869,-853,-525,654,-379,-408,-1000,-580,678,181,-25,68,-281,983,-1000,877,439,858,1000,880,612,-27,786,1000,123,-448,-640,-75,-1000,-12,1000,-780,571,223,-1000,-312,1000,-915,-313,-625,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00709() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{823,-1000,749,-457,947,-624,1000,-387,-624,-703,111,624,-741,149,-693,-234,-590,-400,-1000,-282,-15,641,875,-201,-136,-843,402,306,-588,1000,-140,-975,-640,475,-274,128,531,-751,453,-305,49,-17,-168,185,-975,-966,-294,153,347,371,-1000,552,328,997,-1000,899,-956,-574,-803,902,-66,276,496,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00710() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{0,1000,-16,380,428,232,370,-420,340,-22,-245,-165,-57,628,-886,-401,844,1000,-524,-503,1000,147,-1000,-42,-483,2,1000,978,-100,279,-736,-545,424,528,299,-1000,1000,-40,-67,435,463,279,-119,528,-377,-208,0,685,549,-674,0,1000,388,953,-108,1000,-287,519,0,973,1000,1000,-457,435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00711() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{26,-652,254,545,953,532,135,-425,-913,-764,-1000,489,200,448,-304,288,-464,1000,-943,-187,950,-298,1000,908,-798,-664,526,203,-1000,-1000,-823,74,513,576,397,-310,831,-928,781,364,793,614,-480,468,295,344,603,988,-715,86,-449,-538,-123,767,268,710,-58,-792,-418,1000,221,-214,-681,-17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00712() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{764,581,-16,0,-411,941,370,-467,-331,666,42,-165,-57,1000,-190,126,-1000,-212,-247,991,-798,-1000,-89,-365,-483,-418,831,307,-477,-1000,364,-68,968,765,-369,131,606,140,433,435,-1000,-615,1000,-1000,656,-208,-32,-1000,-1000,-1000,1000,-448,-315,424,626,1000,-258,-916,-832,-1000,-533,-794,-947,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00713() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-385,534,-616,528,-779,608,-1000,445,959,-274,-578,-83,78,268,22,-589,-391,-91,870,34,1000,-527,1000,-391,-1000,-114,-236,731,-166,1000,-382,-620,1000,512,280,884,-361,338,-1000,-487,-331,-534,-897,1000,1000,456,-94,-6,232,-562,848,893,-952,-958,41,-285,12,1000,-16,-877,-325,92,-573,-24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00714() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-456,1000,373,-321,1000,-18,342,-202,1000,-430,146,-319,-1000,171,834,-944,95,450,-246,-268,173,310,-842,-437,942,1000,-724,454,-1000,-700,-223,416,-470,262,623,-211,609,-195,78,57,-563,142,351,-1000,-147,179,-795,-111,665,1000,-348,1000,1000,231,150,1000,167,484,-658,-1000,267,141,-1000,-373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00715() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{26,-741,-36,-207,196,-320,940,1000,-801,-715,1000,-542,-622,448,-758,572,868,1000,-721,-934,959,527,1000,1000,-268,-314,662,203,-707,-1000,-622,-1000,-112,540,666,-971,881,-928,645,-62,675,75,-469,723,-412,-622,603,582,185,371,-938,64,-1000,767,128,990,39,-365,20,89,1000,814,-178,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00716() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-725,-503,966,752,326,849,-218,-1000,-876,-901,-115,431,436,172,-6,24,-1000,1000,-1000,661,928,-617,488,869,-712,-719,322,-336,-1000,-1000,-653,915,135,55,179,-14,864,-1000,1000,468,713,1000,943,240,324,936,1000,-235,-769,-37,-212,-915,254,1000,16,585,42,-1000,-535,1000,-60,-734,-920,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00717() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,-134,589,752,155,849,-286,-67,-592,-494,190,-512,606,112,339,218,-389,1000,-682,588,870,-1000,551,1000,334,-499,-161,-336,-582,-1000,-113,614,135,111,462,28,800,-488,817,815,257,231,1000,-155,598,1000,727,-687,-1000,-37,-123,-1000,157,1000,192,571,211,-947,-145,-302,153,-1000,-1000,411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00718() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,215,144,-633,1000,-882,1000,-1000,-331,144,-493,1000,-864,-1000,-644,86,928,-1000,-952,302,-257,172,-1000,562,297,-181,-135,335,161,1000,76,-111,-1000,-305,-415,509,638,45,-475,-421,74,-226,-1000,-283,-539,796,103,-66,836,251,-391,1000,1000,-229,-4,-179,87,951,-1000,-590,234,235,857,-989}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00719() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation():org.jfree.chart.axis.AxisLocation",
            new int[]{47,-1000,339,75,-1000,-154,-958,276,472,-462,1000,357,-303,19,-1000,1000,-14,1000,-785,-866,216,948,745,1000,-1000,-807,1000,265,139,-1000,-485,-1000,-204,153,736,-1000,1000,12,480,-296,660,918,135,1000,-1000,-691,630,1000,496,-559,-199,-762,-1000,1000,-65,1000,-61,-712,544,1000,-174,1000,553,282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00720() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,-952,-436,1000,-575,-750,-481,-1000,-887,-310,28,-28,-1000,136,427,-708,-551,-1000,1000,-480,-590,788,-991,1000,-400,1000,-200,-628,-580,-332,-554,1000,113,1000,138,-11,-1000,309,-1000,-1000,-307,-324,-634,1000,59,-1000,-538,-1000,659,-1000,1000,934,-1000,-82,-1000,-208,-1000,-1000,-719,-285,923,-596,-376,712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00721() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-927,-147,258,-772,282,-778,-528,-356,158,188,-923,-275,-645,-411,-279,-402,-484,49,1000,280,427,-907,-1000,592,103,620,-530,-768,-47,517,368,52,19,100,-437,-565,-129,-591,308,-175,228,-247,-774,125,1000,43,843,-435,853,-354,-360,421,-536,-727,-240,-112,113,-438,-398,-21,70,-899,-280,582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00722() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{1000,-3,-768,70,979,-162,-998,486,207,676,479,369,-371,-963,633,-1000,114,315,765,815,-508,-192,459,2,1000,442,118,-1000,256,-669,-853,162,698,175,-690,669,-227,472,-799,-1000,4,625,934,512,556,768,-158,773,-902,1000,-148,-81,523,49,1000,-611,554,72,325,-238,-729,582,352,-232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00723() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{885,-69,-806,602,75,229,75,-1000,-37,799,450,283,-279,-514,125,-716,-1000,-511,698,278,-600,1000,838,127,773,669,-1000,-927,522,-1000,-1000,1000,379,1000,-41,-1000,-1000,275,-82,-934,-649,501,540,576,-392,361,-571,-32,776,74,308,502,-1000,454,140,-1000,-317,71,251,-612,444,-17,796,276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00724() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,-352,-591,135,-770,109,-1000,766,-440,855,330,-442,-1000,-310,-363,-1000,-254,975,1000,485,918,-1000,-1000,924,536,687,1000,-1000,-463,-168,-219,965,835,46,420,393,389,571,-1000,597,-547,-252,480,742,857,-303,861,431,-687,579,331,-242,895,-347,807,-946,-348,-1000,275,1000,-1000,351,-1000,623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00725() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{1000,-317,-1000,470,278,315,-438,-290,942,-193,775,114,-1000,-788,597,223,-723,886,459,1000,-243,751,959,-298,1000,724,-310,-1000,937,-751,234,-36,892,529,300,-506,-71,283,-835,-161,-460,408,715,997,5,1000,31,817,900,1000,1000,-178,279,-135,1000,-761,360,-745,-728,712,-845,726,862,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00726() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,-158,678,-114,-768,-22,171,-352,761,342,-1000,84,-547,-83,-1000,1000,-192,462,546,223,1000,-148,-1000,685,-550,767,-124,-13,-425,1000,1000,-190,-1000,74,-882,-914,320,-325,-129,1000,-484,-115,-503,-5,219,-261,247,-1000,556,-1000,-256,658,-556,-397,-1000,62,-850,-939,-172,1000,283,-760,-1000,352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00727() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{839,385,107,131,-41,363,-971,433,-1000,27,-1000,263,756,-128,298,-66,273,-641,510,433,-602,-338,-58,-342,-372,206,-248,-20,-563,-37,119,779,-853,115,-1000,51,-438,14,130,-562,-197,-508,53,-300,-677,526,417,1000,-74,544,136,782,-909,484,-471,-447,994,13,77,-1000,719,-233,-121,-368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00728() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-51,294,294,94,-313,-56,-1000,426,1000,960,487,-841,655,-915,221,-339,-998,108,587,-141,1000,-1000,-463,-18,1000,607,967,-314,-1000,-117,-305,658,650,-400,-374,70,787,396,385,44,-554,-725,-965,374,1000,1000,335,750,-683,116,16,-323,87,-967,1000,-1000,396,25,-604,569,-1000,-1000,-1000,-569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00729() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-480,-1000,199,296,-984,497,-186,-990,1000,591,-143,-172,-729,235,-282,-1000,228,-301,953,-184,218,1000,-655,1000,-66,545,-321,-1000,-795,-1000,-877,-114,-595,1000,419,-625,-394,387,-950,372,-1000,3,10,59,-159,-323,-424,-478,-214,240,839,486,-498,46,40,-910,-1000,-482,547,528,-350,-335,-212,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00730() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{1000,398,179,43,401,183,-1000,115,-1000,325,-1000,1000,26,-437,-504,-831,967,-368,456,1000,-1000,172,-58,-677,875,86,-657,-759,49,-543,-93,547,-853,584,-1000,376,-847,-645,-354,-815,-107,-362,386,-1000,-1000,1000,464,-1000,1000,1000,79,889,-1000,712,-471,-177,1000,-93,874,-684,863,-291,-120,308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00731() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-528,-346,109,691,-942,-361,931,-196,-1000,-325,-1000,-141,1000,783,-584,1000,-282,146,979,-1000,-50,-569,-78,994,379,328,-1000,617,-1000,-273,619,1000,-754,623,-1000,-338,-724,-621,1000,92,175,-272,-195,-289,329,-1000,995,-973,262,-1000,377,1000,-1000,237,-1000,-964,-1000,299,-370,-1000,1000,-1000,-535,-327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00732() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-228,337,179,-737,50,-1000,-1000,-1000,537,-160,928,-140,26,-1000,-747,-490,-1000,-534,1000,-68,317,-992,-1000,1000,155,811,-708,-841,376,-1000,136,445,258,364,160,-733,-731,665,-80,-1000,-382,-219,-389,413,1000,69,-2,-1000,918,-691,-86,261,-654,-949,-359,-156,-141,-1000,-446,-21,784,-940,-809,826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00733() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-1000,-245,1000,-79,-526,-739,-371,277,-555,-929,-1000,-422,707,28,884,1000,-358,-1000,1000,-1000,330,-1000,-1000,1000,-1000,630,-255,512,-1000,376,777,710,-1000,215,-682,536,-159,64,337,-516,9,-492,524,1000,856,-1000,824,-1000,-921,-1000,250,847,-751,-142,-1000,-210,-1000,-813,-1000,-1000,793,-918,-1000,97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00734() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-94,274,-766,-282,146,140,-850,142,-43,270,1000,-1000,-167,331,-75,74,-675,-217,-1000,-740,-24,586,298,934,94,-355,-453,-354,-52,-1000,-818,-940,491,665,652,27,-521,-591,134,598,-759,-707,-66,-218,-241,-538,-6,-485,-646,-285,1000,-1000,-122,763,-586,-444,322,-166,-537,-302,-31,329,596,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00735() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisLocation", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainAxisLocation(int):org.jfree.chart.axis.AxisLocation",
            new int[]{-566,-914,-143,186,-1000,1000,288,-1000,-611,549,642,-189,-7,1000,-399,-920,-674,-581,854,-325,-172,1000,616,726,214,765,-720,-1000,-424,-1000,-1000,1000,-75,1000,683,-99,-1000,505,-1000,-799,-888,248,-181,299,-1000,-422,-1000,-232,302,80,1000,693,-1000,104,34,-1000,-1000,-399,668,-1000,94,313,12,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00736() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{-301,989,-710,969,-276,608,-745,1000,1000,691,936,-763,8,23,1000,1000,214,738,901,-481,656,-489,-556,-438,-163,325,199,-558,-121,-499,155,-240,-1000,-838,-381,-105,992,637,201,-998,537,-1000,-1000,-204,-735,788,-592,-138,29,1000,-299,-211,-8,147,-663,-380,604,-950,835,230,-976,-182,278,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00737() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{-441,1000,453,831,-271,229,225,1000,1000,-83,1000,-1000,477,552,1000,1000,-164,50,1000,-747,872,272,-89,-85,-200,1000,262,-1000,953,575,-56,-609,-1000,-878,-30,-597,211,-341,120,-1000,256,-1000,-1000,-912,-501,1000,392,-855,313,1000,-1000,385,-122,27,-1000,357,977,-892,956,664,-320,-620,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00738() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{160,957,-1000,-655,883,299,401,941,620,-950,954,1000,349,1000,1000,170,-182,523,877,76,-376,371,-425,-1000,-737,1000,610,-1000,-608,-125,188,50,-1000,-722,108,338,629,-1000,846,25,-325,-463,-750,469,-996,1000,-801,272,85,824,-628,321,-740,-954,309,644,1000,-556,766,-63,1000,-82,812,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00739() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{-1000,362,-811,760,1000,931,-839,99,1000,-1000,392,-22,438,947,551,1000,-817,1000,1000,966,-547,417,-179,-565,-553,-266,-179,848,-991,-424,1000,-1000,-1000,-1000,-1000,-145,735,1000,-330,-780,1000,-282,-1000,-52,-1000,946,-661,-1000,164,824,550,-420,519,64,-1000,-690,817,-852,-59,-194,-100,-67,554,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00740() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{-198,-223,-996,279,-813,-73,75,-27,-386,742,-298,297,-749,-638,-260,-69,1000,1000,-111,-622,205,307,-273,344,-500,-185,-1000,-855,-920,-1000,1000,-756,-1000,562,-575,185,835,637,-468,181,-58,384,253,760,-458,-612,-218,-1000,-776,-243,-89,-1000,1000,258,69,-1000,-796,257,-76,-373,-1000,746,-893,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00741() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{-198,-1000,14,-201,228,-1000,390,-1000,64,-28,-1000,-234,1000,21,-377,393,534,-1000,-234,-173,-1000,258,785,214,-440,-237,501,-855,82,708,1000,-756,-263,562,706,-198,-1000,-1000,-1000,181,-51,1000,187,163,523,-90,147,-988,446,-278,279,920,1000,253,-916,423,-745,956,-1000,1000,38,434,-97,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00742() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{-781,524,-692,-192,-571,-1000,-411,152,-129,-178,-341,129,-261,-217,90,400,825,965,471,-541,105,610,-134,-883,-621,169,263,-929,-1000,-460,34,-428,-556,286,-797,1000,-388,-1000,-566,422,-323,-1,162,770,-424,-71,-348,-898,-1000,-1000,142,-589,1000,328,-23,278,-386,421,-998,273,-843,96,-739,-739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00743() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{-389,-554,-326,612,1000,197,1000,-810,-930,949,-1000,-189,-1000,-1000,-1000,-74,196,1000,-277,-590,-717,46,-1000,647,8,-1000,-1000,-148,-1000,-1000,920,-631,-719,1000,-1000,-521,69,-22,248,455,1000,148,164,1000,-764,-1000,-412,-1000,-1000,-620,914,-1000,-1000,85,259,-92,-1000,164,-529,-1000,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00744() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{120,-137,-168,-638,-400,-1000,-526,992,362,-1000,16,197,1000,1000,1000,-62,-36,433,395,837,-924,586,808,-1000,-584,530,1000,636,277,1000,-900,275,-795,-523,409,854,-1000,-400,-1000,205,-793,366,-105,1000,14,1000,-279,79,526,387,91,1000,465,-987,-557,1000,738,-191,-988,813,263,-1000,1000,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00745() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{-437,10,179,-682,-437,-80,351,-27,-253,-718,-298,297,-749,-638,428,-582,-345,547,-260,-622,-29,-45,-179,-710,-500,573,118,-855,-344,-195,1000,-464,-618,456,1000,674,268,637,122,605,-434,-192,253,760,-597,-85,484,225,-158,-510,-471,-126,421,-230,69,647,-1000,-54,-76,-704,626,746,-222,-484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00746() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{345,1000,-586,1000,988,1000,-1000,1000,1000,247,970,-403,762,641,1000,1000,-807,1000,1000,-321,27,-1000,-734,1000,476,381,1000,666,149,-339,867,652,-731,-1000,369,-199,776,469,-89,-1000,1000,-1000,-1000,-728,-1000,1000,-1000,268,-389,1000,-208,594,-1000,-118,-587,284,1000,-1000,163,757,-551,-967,635,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00747() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{613,-285,-1,556,1000,1000,-841,43,669,182,-184,182,-358,245,-900,344,-1000,678,-3,-1000,87,-200,-1000,-12,1000,-614,-10,-251,-1000,-1000,1000,-373,-378,-793,158,-287,1000,1000,-883,-704,1000,-1000,-1000,12,-1000,-250,-44,-730,29,461,-405,-199,-854,627,429,-400,-455,-639,1000,62,-1000,109,-1000,-181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00748() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{1000,537,1000,-1000,333,-596,1000,-42,-1000,-746,9,1000,359,820,616,-589,637,466,-183,-1000,-948,886,-942,-552,261,-107,263,-1000,-63,411,-610,-385,-455,1000,473,539,-1000,-1000,-32,448,428,-53,-111,1000,-1000,255,1000,541,-344,49,-531,1000,119,-20,241,1000,-634,449,-277,757,1000,-809,57,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00749() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{-1000,-820,109,579,544,229,-1000,-1000,-134,-840,-810,-810,137,286,-849,63,-532,758,-314,-855,-675,272,107,217,-257,-1000,-1000,848,-862,-651,1000,-1000,-618,206,-569,-455,187,1000,-1000,-319,942,742,225,320,-411,-454,599,-1000,-414,-452,501,-734,383,903,-1000,-1000,-583,237,-805,-338,-124,218,-714,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00750() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{1000,187,152,747,286,-1000,323,-332,-14,566,-947,-201,460,1000,691,631,278,-977,496,-691,-731,61,-1000,1000,159,-1000,-1000,-689,-904,-701,1000,-970,-762,272,-955,-576,59,-1000,-322,-534,1000,545,-832,-890,-866,-194,-105,-676,-580,1000,226,-199,381,1000,-948,-1000,-744,-191,-14,230,-484,1000,-619,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00751() {
        org.junit.Assert.assertEquals("COLOR:-16776961", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairPaint():java.awt.Paint",
            new int[]{-1000,1000,-752,-655,589,523,1000,707,1000,-753,951,1000,-987,1000,146,-168,653,-197,949,-698,143,574,-736,-1000,-360,1000,1000,-1000,-774,-623,214,-295,-1000,-51,108,409,872,37,1000,-1000,-325,-642,-243,1000,-1000,343,874,272,-207,144,-888,-240,-656,-768,700,644,291,-373,1000,-952,1000,-1000,168,535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00752() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{-738,412,204,373,686,436,-706,-235,307,-642,741,-492,659,-870,133,384,-906,-569,-237,-636,18,820,983,-88,999,515,658,-710,-123,-517,523,976,13,135,-1000,-389,-44,-379,482,779,255,700,121,-77,786,189,-369,-879,90,-919,97,-884,-111,591,947,-246,-957,-428,-247,932,18,803,-516,425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00753() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{1000,855,-1000,1000,358,-336,866,-983,431,782,-1000,327,-460,1000,74,-1000,-170,-779,-38,-1000,927,-42,-567,-1000,-1000,-626,-89,1000,1000,545,-1000,-398,606,-929,544,987,1000,709,-1000,-1000,-549,-1000,-648,-170,-1000,175,605,-886,1000,893,295,497,1000,-301,-1000,-845,693,138,-847,-1000,587,-1000,-697,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00754() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{-349,362,-751,615,99,578,369,53,558,782,-1000,-510,-880,381,-50,-949,1000,-152,84,-1000,369,563,644,-987,-332,216,-387,714,-107,246,-955,-204,183,103,706,1000,1000,709,-916,-1000,-482,1000,-685,430,1000,-472,605,-886,1000,772,725,1000,1000,-624,-1000,-365,1000,-655,553,-998,-293,-1000,-895,-895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00755() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{-758,-228,-97,-964,404,-577,25,-447,313,-457,-1000,-1000,1000,-230,743,103,303,-355,-126,-93,940,-636,224,-322,-45,1000,512,361,-732,17,414,-1000,-643,745,-74,-342,-381,-950,773,676,-50,446,944,-62,-972,-305,-164,203,-1000,-1000,-73,136,290,392,1000,372,-1000,20,266,1000,-899,1000,-1000,-105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00756() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{1000,1000,729,154,1000,342,805,48,895,-872,952,994,-69,219,-326,1000,-123,521,-862,1000,624,-752,-811,-123,-83,-1000,-127,-122,-53,-638,145,261,-241,968,-472,-1000,43,-529,116,440,417,842,-668,-1000,-1000,1000,-291,-341,-430,28,-1000,-400,-132,902,305,-53,-400,-293,-770,398,883,400,-152,98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00757() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{-579,-395,-648,-964,-441,-1000,528,273,313,-1000,-333,-346,-947,-660,380,397,750,437,-298,-854,34,943,-520,-75,-45,1000,816,669,-642,-1000,307,-1000,53,-607,809,1000,1000,-950,810,19,-703,726,-639,-846,-470,-532,617,975,-10,982,303,-598,290,874,-23,638,411,-724,363,304,460,-406,-1000,-535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00758() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{519,166,544,-197,712,557,763,-1000,428,-116,133,170,696,539,-150,1000,-671,51,-996,-800,430,-467,-569,11,-213,-874,-1000,-388,353,60,-188,368,20,-200,737,-40,247,703,481,871,830,291,-492,-618,243,926,566,-217,-555,1000,392,-167,264,152,634,-115,651,82,1000,-817,1000,-668,150,54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00759() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{44,-1000,-346,-532,-6,-1000,561,-521,-56,-26,923,4,-359,137,338,-664,134,-766,79,-859,709,-159,-1000,-662,431,-258,239,1000,-624,836,-210,-1000,-503,-1000,333,1000,957,-138,45,-467,-651,379,1000,9,892,-238,369,327,-58,139,985,-95,1000,195,-192,-704,261,393,-975,435,-923,-314,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00760() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{626,383,-886,1000,1000,748,-134,-605,57,215,-161,25,783,-429,40,677,-28,353,-677,645,359,402,695,-535,-999,50,-563,645,-737,-235,-396,1000,515,672,-931,-281,879,269,126,818,440,-273,-872,-580,775,241,483,-1000,907,-725,-705,8,392,-292,-197,262,-860,-649,113,578,233,778,341,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00761() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{1000,1000,-566,-743,762,1000,-60,352,-149,1000,-690,350,832,654,640,-1000,198,-316,-848,-1000,166,447,-376,525,-1000,-1000,-1000,553,1000,577,-790,562,-680,181,1000,-706,-114,1000,-1000,-1000,1000,-1000,-1000,1000,1000,926,-844,-701,1000,633,77,286,57,958,-364,-141,-160,699,-123,-495,333,-901,1000,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00762() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{1000,1000,659,94,534,227,-232,168,436,-266,646,909,-223,500,-874,-701,-1000,-1000,482,-1000,691,-1,-607,-815,-77,164,1000,802,1000,918,-491,-65,7,-1000,-790,529,-581,1000,-761,-1000,515,387,1000,-147,98,941,-826,-572,-301,481,1000,-881,788,663,897,-1000,511,747,-1000,-563,552,-1000,210,-398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00763() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{-1000,19,272,450,240,1000,-721,1000,-256,1000,-931,-991,-222,116,-1,-928,934,-104,-72,-1000,121,529,730,-672,-45,1000,-274,591,-1000,574,-689,919,396,-470,629,1000,1000,1000,-760,-1000,955,-1000,129,763,1000,-265,1000,-1000,1000,440,1000,1000,1000,-1000,-864,-606,1000,-282,1000,-997,-528,-1000,646,-303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00764() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{1000,1000,34,563,1000,503,996,29,277,589,-698,851,218,579,-771,816,1000,-25,-1000,-1000,956,-368,-421,-1000,-515,119,-1000,-54,-1000,-128,-323,371,1000,1000,2,973,1000,109,-438,-411,483,-72,-985,-876,-1000,271,1000,-1000,349,429,537,1000,983,-323,1000,106,589,-1000,1000,-878,843,-1000,15,38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00765() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{31,543,-1000,1000,609,-1000,477,-873,398,342,-1000,-565,676,269,464,-1000,-638,-1000,501,-1000,1000,149,272,-1000,-332,651,821,802,1000,715,-433,643,143,-807,-98,763,321,-144,-112,-811,-1000,-597,182,-24,-1000,-486,-191,-128,1000,-478,854,-30,1000,56,347,-547,-664,240,-1000,332,-694,203,-1000,-939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00766() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{534,156,-41,-196,455,840,580,969,854,-1000,-78,262,-1000,-169,-713,696,-935,-618,-996,-1000,-435,662,-46,271,655,-557,-788,349,637,-652,1,977,-229,-650,-605,-21,-308,941,-448,-790,855,1000,-174,71,396,1000,-694,-670,-197,759,406,-1000,-355,1000,634,-1000,861,-189,-769,-495,836,-668,-224,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00767() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairStroke():java.awt.Stroke",
            new int[]{1000,545,-1000,9,729,503,1000,376,628,589,-710,909,-1000,842,-987,1000,1000,1000,-855,144,599,-794,-697,-1000,-835,-627,-1000,-54,-707,50,-491,16,237,580,-485,1000,1000,-35,-169,247,-312,-536,-1000,-804,-1000,661,705,-836,658,1000,-380,1000,493,-323,-663,265,1000,-887,936,-932,486,-803,-207,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00768() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{501,-583,-636,-356,429,618,-475,746,-476,-1000,1000,-610,-336,1000,-456,-586,-1000,295,525,-494,316,-24,1000,151,336,-586,737,-824,-33,478,1000,-860,-712,904,162,869,869,96,305,-376,-501,-572,-60,951,-508,237,568,785,-591,223,-949,499,1000,378,1000,1000,1000,478,1000,-582,-337,-141,560,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00769() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{-183,21,103,-567,-1000,-877,-200,124,-173,-507,215,201,-722,400,1000,-705,694,-289,-1000,1000,-1000,266,-1000,-1000,-1000,-1000,1000,475,127,830,-392,-1000,-454,1000,-539,909,-922,1000,1000,415,-697,1000,1000,-1000,-536,153,114,-1000,-317,-1000,-127,439,743,427,-894,-1000,-381,48,235,119,578,58,841,272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00770() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{632,386,-201,-636,-941,746,887,-1000,1000,-573,-92,-767,235,-1000,1000,72,573,819,-20,999,-1000,151,-833,-365,-1000,-130,1000,-580,279,586,147,-637,155,637,-1,1000,-1000,1000,625,1000,0,1000,1000,-1000,-418,-388,386,-363,1000,-640,1000,1000,898,365,-385,-1000,674,407,-451,359,-184,-1000,1000,431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00771() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{613,1000,-456,64,-1000,769,519,-1000,1000,-552,-487,263,455,-1000,687,-217,827,498,-38,-1000,-1000,538,-833,-747,-645,980,178,-252,69,951,147,-419,909,638,-271,194,-1000,873,523,1000,69,-214,-1000,-1000,-269,-1000,285,-238,-906,-470,964,-256,898,726,-522,-1000,674,106,-1000,633,123,-514,98,272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00772() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{634,-88,814,819,-166,260,-893,543,-704,1000,900,627,-131,948,-467,14,-1000,-592,581,334,266,944,1000,132,1000,353,-1000,-155,130,748,544,-1000,188,1000,220,-425,-402,-229,-456,-233,-1000,-379,282,632,-570,-534,-596,473,304,1000,-132,731,-811,966,-324,165,716,-741,804,688,-1000,698,-1000,938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00773() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{590,-486,194,-871,-997,814,-1000,-780,-1000,324,-111,947,-79,-256,798,-455,1000,33,1000,362,-597,409,-419,-506,-475,-129,210,-862,366,820,-76,-1000,549,-410,-479,136,-1000,294,505,241,-282,-158,61,162,-1000,711,805,-1000,-204,-183,-170,861,178,-485,-877,-1000,89,147,-763,-1000,619,576,119,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00774() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{-397,692,93,-504,-70,569,1000,-866,-1000,-984,-230,-1000,240,-1000,1000,-124,274,1000,-1000,721,-246,-71,-1000,98,-573,308,216,-82,823,234,-190,-236,919,-2,-454,786,-773,639,317,1000,-413,-573,184,-1000,1000,-36,16,-490,1000,-1000,1000,-1000,786,108,-1000,-1000,-547,-51,-93,991,1000,-1000,1000,308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00775() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{477,-959,458,-424,74,-123,-409,889,-578,-23,858,205,-529,926,126,-842,-334,610,-442,207,-49,255,765,-255,875,-707,406,351,349,35,588,-462,-961,799,798,97,199,-487,-222,702,-213,66,728,851,-855,715,-422,-612,-782,-302,-380,877,319,-106,796,917,780,-181,115,117,-516,569,-716,911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00776() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{590,120,369,914,-671,463,507,228,-338,-813,-546,-404,-22,-679,913,818,633,188,-646,338,733,-195,-705,984,-88,997,-437,671,-550,-465,-284,709,549,-748,164,136,-641,476,-803,72,428,356,-844,-553,662,711,254,690,584,861,188,951,712,-413,624,-748,205,-164,-134,613,592,76,577,629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00777() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{-635,1000,-372,1000,-693,393,5,-1000,941,-448,-313,-52,1000,-400,-447,-316,-99,438,-390,-52,525,252,543,-582,530,1000,-1000,-419,-851,492,254,-509,567,-263,-741,-793,-1000,743,431,1000,-783,-431,-722,-377,733,-903,173,1000,878,591,400,-1000,1000,1000,-77,1000,514,-208,186,897,504,-172,-708,279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00778() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{632,755,-189,307,281,184,510,-951,838,-573,-92,-525,696,-706,-74,808,305,345,-701,-536,767,-208,341,225,-1000,1000,-1000,-142,-329,24,711,261,788,-639,661,-585,-960,-599,625,799,57,1000,1000,-695,869,-281,-161,1000,940,-640,507,1000,766,309,-385,-15,57,-262,211,425,694,-374,-575,822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00779() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{-1000,584,-957,-324,-176,692,-893,112,67,283,-1000,-1000,-626,-1000,-306,1000,1000,-831,-756,234,88,-899,-1000,235,-602,-1000,1000,1000,-1000,533,-550,-183,-859,-1000,-1000,1000,1000,1000,-701,76,58,797,-1000,-526,1000,-243,1000,-829,482,-801,696,-307,-400,1000,-151,-695,843,672,-1000,-1000,756,-1000,791,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00780() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{-316,-418,714,-290,-373,651,-1000,109,1000,-824,553,-778,-49,-127,-1000,-109,-8,-163,-1000,-626,51,346,1000,-906,843,715,361,-1000,1000,1000,871,679,-340,905,311,285,-157,-397,484,1000,-155,-716,321,28,1000,-1000,292,76,344,-274,561,-1000,780,1000,805,1000,-1000,235,499,-2,-719,-806,-1000,829}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00781() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{-734,1000,-186,-333,-708,142,1000,81,1000,-1000,-646,-1000,596,-1000,285,1000,1000,936,-1000,-47,-206,-407,2,-260,726,747,396,-516,-14,916,26,845,1000,-1000,167,822,-650,443,553,1000,-218,626,276,-1000,1000,-853,258,287,1000,-400,1000,-887,1000,759,-517,-1000,716,37,-507,1000,896,-1000,700,375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00782() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{1000,-1000,973,-604,555,-664,-360,767,-1000,-76,865,1000,-981,690,1000,-272,409,25,-766,-715,978,478,86,1000,428,-73,-810,1000,982,-1000,1000,783,-2,385,1000,-673,-563,-1000,-1000,-233,1000,490,-81,722,-1000,1000,-1000,-885,-873,314,-240,1000,-493,-1000,950,-1000,119,-930,-690,633,835,1000,-1000,964}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00783() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainCrosshairValue():double",
            new int[]{426,-1000,-331,-898,1000,433,-872,664,-1000,-970,-114,-138,-652,346,375,848,-152,-1000,-1000,37,511,-559,469,1000,843,-757,216,686,-1000,-202,555,-436,-1000,-467,522,1000,-767,24,-1000,-1000,-4,-353,-935,580,-810,779,865,964,-736,950,-966,-987,-1000,897,1000,-1000,1000,405,-1000,-419,675,41,-481,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00784() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-80,335,-1000,881,-886,592,702,-183,427,39,1000,-687,-1000,710,542,99,82,175,-489,1000,472,-1000,-116,-186,197,-230,210,532,1000,892,563,141,-356,-95,738,1000,892,-1000,-1000,1000,604,-1000,24,1000,317,-1000,1000,664,-216,178,-876,278,-873,-1000,-307,-282,682,1000,32,-99,-58,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00785() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-51,-1000,224,860,-77,-235,464,633,799,-1000,-1000,587,149,-569,-507,175,-352,1000,-49,527,15,-1000,-609,359,-942,799,-406,-613,-195,149,482,-84,-424,339,288,-1000,-613,-1000,-38,100,1000,471,1000,-1000,1000,-141,1000,112,-339,-1000,959,1000,785,84,920,991,116,-915,375,-639,441,883,-1000,-247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00786() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{410,-1000,-311,32,-664,-504,556,-1000,813,1000,435,-905,-695,-626,-984,65,186,-286,-1000,1000,-198,430,-921,-312,948,702,1000,-153,1000,376,579,553,-712,-911,322,1000,1000,-1000,-1000,1000,283,-371,-934,994,-811,-1000,1000,1000,1000,695,81,1000,650,-1000,1000,-634,194,-1000,465,-434,454,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00787() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-978,-270,-236,380,-761,-777,107,-266,713,824,1000,-541,-54,1000,-264,552,82,68,-1000,864,-928,177,-991,79,1000,190,788,224,653,488,444,281,-1000,-555,1000,1000,892,-462,-841,1000,-515,94,-459,662,-664,-1000,222,655,-183,-16,-1000,941,-135,-734,843,-841,764,-34,-686,1000,495,-1000,-1000,-483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00788() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-1000,119,998,-107,1000,-1000,150,689,104,121,-983,602,506,22,-1000,-954,1000,-3,65,-1000,-1000,1000,-1000,1000,1000,-973,624,-625,-385,-1000,831,-406,-592,69,688,-1000,-1000,1000,819,-981,-634,647,445,-1000,-402,864,-1000,-765,-170,-1000,-402,-1000,1000,1000,635,1000,44,-1000,-718,-1000,1000,697,961,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00789() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{876,-824,-696,127,-652,869,268,-184,-162,-341,204,-339,733,-100,-497,-440,-1000,1000,518,-1000,-1000,-622,-1000,-1000,-434,340,4,-297,-583,151,405,176,-713,1000,1000,158,178,-1000,-432,-475,-47,-810,657,-545,-264,-770,101,237,1000,669,-734,1000,437,124,-266,-48,-804,-217,-891,-554,-602,180,-377,-175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00790() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-616,424,968,792,-761,-493,-192,693,-408,-788,-637,826,608,1000,1000,552,502,68,963,-227,-1000,-676,297,79,1000,-369,-279,-47,-466,788,-1000,-232,-862,557,-86,-921,892,756,-841,-25,-1000,-52,549,-5,281,-972,-1000,-605,-183,-427,-1000,-452,-1000,187,843,131,-141,-376,794,385,-61,699,-1000,-68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00791() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-1000,-208,-121,-578,336,-342,-1000,191,-134,-283,-769,1000,554,-359,-1000,-489,-756,189,-999,-459,-874,1000,-310,1000,825,-611,-169,869,-906,-528,-278,-225,468,-110,-662,984,184,1000,219,-1000,-1000,80,-163,-1000,137,513,-16,-927,-1000,-852,-513,-1000,317,1000,426,1000,-389,-385,524,-720,773,-520,888,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00792() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-519,22,979,700,-903,-200,82,808,-430,-818,-746,738,41,-858,842,844,598,431,1000,-121,-1000,-773,550,39,-446,-410,-51,501,-282,828,-990,-650,-688,297,-304,-945,160,621,-688,204,-1000,-332,684,-180,-23,-1000,-1000,-272,-893,-599,-1000,-464,-1000,623,-753,137,116,665,868,456,209,754,-160,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00793() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{688,265,-123,860,-63,509,664,924,-154,-1000,-637,1000,-681,-277,1000,302,112,1000,684,1000,15,-1000,-609,-68,-710,1000,-954,-458,233,697,-572,226,-424,1000,436,-883,-613,-508,89,137,752,-667,1000,320,1000,-1000,1000,112,-720,-335,0,426,-1000,-1000,-883,93,116,578,1000,-74,-566,698,-495,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00794() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-415,-740,509,959,-478,-400,-163,-367,657,-819,-769,-193,847,305,-891,-646,-756,960,702,-9,-438,-973,-154,728,-375,970,-169,-532,-991,346,246,-770,-325,664,744,-184,-114,-809,-15,-25,-577,-853,601,-891,137,-614,985,-613,210,-835,-68,766,-852,858,728,893,-499,-385,-853,-930,560,-153,-997,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00795() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{37,-388,593,860,1000,92,959,1000,-10,-166,-1000,1000,-149,-1000,-344,175,1000,-389,528,-976,15,142,-83,960,562,-147,154,-1000,-355,-1000,1000,-1000,319,1000,577,-1000,-1000,1000,-38,100,1000,-447,1000,-1000,351,864,-222,-755,-339,-831,399,-1000,1000,84,1000,549,1000,-1000,-385,-1000,444,883,961,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00796() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-904,1000,-2,-62,191,-1000,-914,-212,903,-383,1000,-590,-41,-100,-1000,-750,-535,524,-70,1000,-342,452,-308,62,556,-140,89,582,221,-746,110,1000,-969,-165,1000,-494,-186,-531,802,362,1000,734,-980,-210,634,39,57,-156,-811,-345,221,908,-980,-1000,-778,52,-1000,-857,583,-168,286,38,-530,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00797() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{300,360,53,-394,509,-323,1,437,978,-765,-1000,1000,-150,-569,-915,175,-306,700,886,527,15,177,-609,-218,-797,-381,591,-1000,163,-251,1000,-167,580,58,624,-1000,-280,-1000,766,-372,479,153,90,-1000,657,103,-400,679,702,-692,959,-157,-300,84,920,1000,331,-1000,1000,-1000,621,658,373,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00798() {
        org.junit.Assert.assertEquals("COLOR:-15140904", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{-293,1000,-236,-777,-1000,-777,-808,-135,1000,-132,1000,-374,206,887,-1000,884,-1000,68,-153,949,-665,646,121,-570,-767,-845,1000,224,517,728,773,281,-520,-943,793,74,1000,-625,-341,1000,443,994,-459,-432,255,-1000,-684,1000,-183,-454,-921,1000,-807,-265,-171,1000,764,-1000,1000,674,1000,-903,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00799() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlinePaint():java.awt.Paint",
            new int[]{1000,-1000,-311,-428,1000,1000,888,1000,61,-1000,-1000,-905,-332,-1000,-344,65,4,171,1000,-976,1000,-623,1000,715,-1000,-174,170,-1000,-970,-1000,579,-1000,1000,1000,-637,-1000,-1000,1000,1000,-1000,1000,-837,1000,-642,1000,1000,-222,631,638,-831,1000,1000,857,654,1000,549,863,-1000,799,-1000,1,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00800() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{260,859,81,554,692,234,-687,-527,-24,2,-497,-179,-952,-259,-232,-277,-156,607,722,-329,430,997,-186,-140,-197,-617,-360,34,-552,955,464,1000,-1000,-585,-938,695,997,-399,812,148,-375,824,117,-669,-287,1000,-1000,506,267,-410,49,-502,65,-508,-261,883,759,-362,936,312,520,103,-278,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00801() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-9,440,-716,710,-519,-213,-441,-819,1000,602,910,-972,-296,-267,-838,463,393,-174,-470,645,-244,-1000,-564,683,636,1000,-330,-751,689,-1000,639,-158,-146,1000,498,-61,583,1000,625,-702,228,587,451,1000,1000,-590,677,1000,-512,45,159,-215,-182,148,142,674,379,-450,-297,704,394,-490,-281,456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00802() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{318,178,708,-235,-745,-197,-11,16,672,-324,773,-121,1000,434,1000,1000,596,-205,-337,408,899,-795,-769,714,-708,90,-1000,-1000,-21,-18,834,-194,1000,1000,-971,-50,1000,1000,-832,694,990,-1000,1000,-772,662,983,554,1000,-20,-481,820,-1000,-327,-246,572,1000,-297,-416,-980,863,31,-491,411,218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00803() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{1000,739,-971,1000,432,-767,-729,-878,574,-536,909,-378,-495,-206,959,-509,-1000,340,-4,125,-369,862,-909,-763,268,1000,-198,-805,-189,-555,122,-1000,-859,-665,-297,-600,990,1000,840,479,-800,-1000,-233,-920,-592,798,740,243,-27,-111,-526,-391,1000,-776,-193,225,911,289,1000,869,1000,1000,-544,284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00804() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{13,954,-581,-103,194,-688,-362,-816,187,-768,864,-381,303,197,347,142,-758,750,769,-104,-358,517,-23,-640,107,892,-248,-503,-56,-289,504,241,-1000,-271,-352,-127,729,800,-167,275,-1000,-1000,-30,-39,-555,561,-77,19,535,-707,-220,-367,1000,-810,-287,340,-217,1000,547,704,852,360,-545,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00805() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-505,-1000,-776,450,190,581,-805,126,-22,-560,139,-587,-894,-392,-708,106,19,88,849,-903,194,398,-618,-58,497,403,-687,-504,487,-8,812,47,-1000,-336,-309,330,416,-28,1000,34,38,1000,-660,247,-654,-14,-723,-582,-849,542,589,982,475,-1000,83,819,-299,388,355,107,841,66,-781,111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00806() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-487,-711,-913,-5,556,-794,-381,-628,200,304,2,-371,-1000,599,-239,-950,-628,1000,533,501,1000,902,207,-568,870,1000,1000,353,-403,-71,155,1000,-865,-78,-145,172,514,988,-745,-572,-597,-989,-819,-389,-84,-159,-178,709,378,1,567,-795,-701,411,656,-43,-264,368,944,756,329,-604,-952,-503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00807() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-391,-625,-297,1000,-42,7,-1000,469,812,375,1000,505,-676,-78,565,1000,-973,-673,-1000,-280,1000,-306,-634,-468,-471,192,-61,-861,-1000,-624,-322,-802,829,854,-1000,-115,-656,1000,-751,-380,536,388,-1000,-389,-853,117,-57,-1000,-213,523,-817,1000,531,262,-248,-668,406,1000,244,-160,1000,548,-94,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00808() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-380,660,-176,211,1000,-698,-599,1000,-541,86,-451,401,-958,-736,1000,-10,-661,209,-356,1,453,319,389,-833,-363,152,109,1000,-368,389,-910,903,-772,1000,-72,338,297,709,-1000,1000,385,144,-927,-328,-795,457,-90,42,-1000,-1000,-1000,-331,807,716,125,68,622,-881,-1000,-530,-1000,1000,767,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00809() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{1000,729,487,593,711,292,-273,448,-318,-499,-1000,928,-1000,-506,50,-57,1000,-709,-182,412,-182,1000,68,-533,-478,-920,126,350,-136,704,-255,740,-104,378,-381,378,1000,-789,-247,445,676,222,1000,112,-976,1000,-759,-226,1000,-1000,-408,-1000,-246,59,1000,-319,-384,-926,1000,-10,-215,834,-503,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00810() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{13,440,-141,443,-994,495,-573,-686,695,-291,970,-381,472,-230,98,1000,828,-1000,-356,-197,-804,-1000,-362,773,54,892,-1000,-1000,721,-671,548,-1000,245,754,1000,60,-220,917,591,23,1000,604,1000,1000,247,-1000,677,-709,-727,827,159,833,-182,-1000,1000,776,-766,188,-1000,344,394,470,-371,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00811() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-645,502,-842,799,-634,418,119,390,92,-80,1000,-112,-1000,124,-495,103,-34,1000,327,-1000,-193,1000,62,223,218,-657,310,915,-1000,-997,1000,1000,101,437,-986,201,795,-648,1000,-1000,-443,-14,535,215,-1000,509,-1000,1000,-518,1000,1000,-538,-380,-566,286,776,-64,-656,729,703,-898,-754,-742,569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00812() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-673,-1000,-886,554,-1000,-117,-296,-319,-776,1,529,-192,-886,978,-1000,279,483,-33,-1000,-14,-354,-1000,618,750,-563,1000,-252,-464,499,133,-94,-85,-912,-381,437,78,-886,311,-119,-987,-1000,-251,-771,-317,223,-1000,908,-154,-253,413,1000,1000,-1000,-233,-132,-1000,-1000,97,-238,-36,-1000,127,518,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00813() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-267,471,411,-827,308,-147,-587,-430,-776,12,-215,-223,59,-417,965,-933,-457,-942,449,251,-354,-552,102,-517,-456,937,609,586,-268,362,-790,-536,-867,-673,371,430,-886,818,-780,612,-2,-713,-771,992,-130,-634,908,-911,261,-417,-946,800,649,-233,508,-475,-391,200,299,-689,921,957,-450,-531}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00814() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{835,-1000,-491,72,-1000,-708,95,1000,835,754,288,-350,970,81,1000,494,1000,-1000,-1000,1000,672,-1000,940,292,133,1000,145,-184,1000,-183,-542,-961,829,1000,1000,216,-539,1000,266,-865,1000,-1000,795,517,1000,521,-3,-54,849,83,-522,-1000,-372,1000,1000,-151,-1000,-306,-1000,562,-397,393,-123,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00815() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainGridlineStroke():java.awt.Stroke",
            new int[]{-1000,245,-871,-114,250,-794,-310,-1000,343,-478,863,-124,315,363,43,220,-758,915,919,68,124,504,-78,-315,477,944,-165,-683,-56,-450,873,350,-391,10,-378,-290,1000,988,-13,117,-50,-989,-30,-1000,-215,742,-257,1000,340,25,20,-795,822,-434,-528,497,-201,525,547,695,656,-79,-574,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00816() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{765,1000,169,-851,-533,229,-1000,849,-390,-130,-91,1000,-35,-133,1000,1000,-1000,-1000,-939,-1000,-561,993,-532,-62,-754,746,778,-39,-1000,62,13,449,-1000,-374,111,-85,-1000,-1000,255,-802,-833,725,622,1000,813,1000,1000,967,-1000,-996,-413,-420,-180,534,1000,-1000,1000,-763,-1000,-537,132,-727,254,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00817() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{363,1000,214,-1000,-1000,-1000,-304,530,-679,-343,125,502,1000,-313,1000,915,-157,-1000,-701,-1000,-30,94,-1000,807,-395,-209,1000,157,-1000,-956,1000,925,399,157,-747,-299,-1000,-236,604,-1000,-768,982,916,211,281,1000,979,1000,-1000,-1000,-356,-1000,-1000,-4,1000,580,603,-1000,-270,-663,499,-1000,550,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00818() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-580,-976,164,-770,900,-460,410,1000,681,-127,786,-964,-744,80,239,1000,523,123,-141,-1000,-62,183,-413,884,431,-173,-425,959,690,114,447,-1000,496,443,-1000,-244,-1000,-1000,15,611,238,-381,1000,485,610,-1000,-1000,1000,135,-178,866,-589,-158,-303,-1000,-1000,1000,779,921,-1000,990,-1000,-1000,491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00819() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{400,917,-598,246,-266,1000,13,-377,782,996,-772,621,-735,1000,-1000,29,282,-274,-88,-56,-662,590,220,1000,46,561,356,-963,-223,1000,-581,-196,-957,-676,999,-323,928,1000,-504,-1000,-1000,-1000,-61,993,801,400,434,-678,875,164,-599,796,465,-1000,1000,-85,-1000,484,-228,1000,-947,1000,1000,-617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00820() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-718,1000,-532,-611,-1000,-106,619,-119,29,-845,468,1000,1000,7,1000,551,625,-1000,-5,126,713,469,-1000,1000,-1000,707,1000,-813,-1000,-1000,-374,1000,-1000,-1000,226,29,-659,-73,-273,239,-1000,1000,955,389,-423,1000,1000,-57,-731,-341,-1000,-1000,-347,-45,1000,227,-62,-1000,-215,1000,-29,-617,1000,721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00821() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{617,1000,439,-768,-493,71,-1000,-604,-1000,283,257,764,254,-800,1000,1000,-510,-869,-860,-406,19,552,-331,-944,-114,13,1000,-476,-1000,-265,93,-260,-636,-77,426,178,-875,1000,685,595,-707,-263,-665,-625,1000,1000,305,839,-1000,-911,59,-691,-981,65,839,-392,906,-905,-1000,-1000,31,-1000,-960,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00822() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{44,-151,-1000,-1000,-1,-500,-1000,1000,226,769,204,-1000,1000,125,248,1000,232,47,-351,-167,244,649,-338,-37,207,424,30,1000,-647,-290,147,636,527,139,-429,138,-405,-293,24,-337,-861,-869,400,80,-551,-648,-200,1000,-76,-1000,1000,-842,-56,340,-1000,530,603,-477,301,1000,27,-807,-551,-773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00823() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{1000,548,124,-831,-684,209,-1000,-1000,-1000,-11,570,482,1000,788,-88,591,-157,372,-966,326,74,-1000,-284,582,341,-771,344,-67,-451,-296,936,263,399,157,-769,101,-481,439,604,-48,-768,-1000,-1000,-674,-167,1000,1000,1000,-297,-325,330,-415,-1000,800,-27,580,603,-580,-1000,-1000,563,-1000,-306,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00824() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{890,1000,189,77,121,800,844,75,389,831,-94,525,-164,293,-549,1000,346,-342,-834,-615,-1000,804,210,-264,-71,901,-986,-820,315,915,-819,2,-177,119,746,-360,-466,878,521,432,-707,200,-336,-105,57,566,252,-83,-632,-777,-495,-503,-1000,-486,607,-811,689,-355,-347,-309,-802,105,-446,64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00825() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-133,425,1000,1000,558,1000,1000,-223,546,929,509,548,152,655,539,-34,930,133,-40,114,375,-1000,-575,1000,120,-70,-1000,-614,223,206,-103,-815,-744,-395,-471,396,212,295,903,87,467,-1000,-472,400,798,1000,268,1000,695,-321,-729,513,797,-1000,1000,-500,19,202,378,272,533,-628,319,-628}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00826() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-785,-665,121,-437,1000,-301,784,799,535,-40,546,-1000,-314,300,318,-19,1000,283,356,-1000,-38,993,-203,1000,289,482,-983,-39,402,225,340,-668,386,225,-824,-211,-386,-639,255,481,-45,-423,829,672,-408,-775,-835,768,163,-239,229,-191,217,-744,-976,-1000,360,824,997,-537,715,-1000,-1000,-246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00827() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-1000,-1000,-417,-773,380,-584,-51,1000,1000,-127,786,-1000,183,80,-962,1000,590,64,1000,-718,-573,649,-468,1000,530,36,-425,846,1000,455,83,694,-908,139,-1000,-20,-125,-1000,-675,-1000,-1000,-381,1000,814,-339,-1000,-980,1000,418,-178,389,-597,-272,-1000,-383,-1000,1000,545,301,1000,886,-321,339,-760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00828() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{616,-1000,-376,-196,-729,601,-415,1000,603,-387,-855,148,1000,1000,-818,413,444,731,-380,-952,-729,-618,-17,864,172,310,-830,452,1000,1000,-946,1000,-134,1000,-1000,-700,70,-1000,-761,-371,-834,-298,1000,1000,-1000,-1000,27,748,1000,-777,1000,371,414,-413,-507,392,-972,951,276,-688,510,-16,67,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00829() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{811,-93,329,-989,394,-176,-349,-79,-388,256,801,330,-340,-981,1000,1000,147,-846,-216,-291,-729,896,-247,-798,39,434,1000,49,-1000,-542,439,-536,-406,300,593,344,-1000,761,1000,608,-1000,3,-601,-105,845,-858,612,734,-1000,-1000,-621,-801,-1000,494,311,-1000,1000,-1000,-1000,-1000,67,-1000,-1000,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00830() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{607,37,1000,-15,777,227,555,-665,-826,-277,1000,-355,-343,-362,270,1000,1000,-453,-950,732,138,1000,-154,-1000,215,103,-696,-67,-938,-231,-971,-1000,646,1000,1000,311,-557,153,1000,633,-140,-934,-1000,-279,1000,1000,440,49,-297,-860,406,-466,766,355,-765,523,1000,-1000,-1000,-1000,139,-366,-1000,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00831() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(int,org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-164,1000,404,-646,-1000,648,-1000,-548,-1000,-55,-451,-334,1000,781,-1000,-88,-1000,-462,-1000,-835,1000,-260,-637,920,-1000,303,-59,-726,-1000,-71,-1000,702,-937,-1000,-326,-90,-313,7,-413,-1000,-1000,234,1000,-172,-100,1000,1000,1000,-659,493,-475,-200,10,-35,126,441,-435,-916,-1000,460,284,-676,949,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00832() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{295,-1000,-486,-269,543,327,-708,-277,393,-936,1000,-380,-224,-549,1000,154,-62,33,-12,1000,-853,677,-430,354,545,-175,-777,-552,526,1000,32,-364,689,595,350,274,-213,-443,-215,1000,-652,-302,-961,-162,1000,102,-462,527,821,1000,428,-1000,-1000,-639,-118,302,102,1000,-969,330,-610,-408,704,-302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00833() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-961,1000,219,479,705,205,-739,721,-435,-356,-76,586,190,282,1000,1000,406,-769,-1000,-1000,267,-1000,306,1000,1000,1000,-1000,1000,1000,99,595,1000,-547,-1000,-1000,-1000,1000,-245,857,-1000,-698,-1000,-598,673,-908,666,673,-1000,-874,-129,163,1000,-493,-286,-476,1000,-887,551,-10,-564,357,-574,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00834() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-411,-1000,-376,507,1000,54,698,-283,-238,-1000,308,-356,-351,-260,268,54,-44,1000,856,1000,-282,785,-375,-865,-313,-130,-144,-621,-781,792,-292,-307,-996,-174,89,631,685,126,-332,1000,1000,205,424,-1000,704,209,845,-125,555,-509,662,-549,-139,1000,-681,-373,52,532,551,218,-1000,1000,931,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00835() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-414,201,-929,-590,827,-671,13,726,537,-191,990,-488,799,-417,-40,279,-609,77,705,279,-317,628,32,606,394,948,-304,634,597,241,250,-203,147,131,-608,-994,100,-859,-751,-605,-362,-922,-435,472,-512,427,732,-732,925,577,247,599,37,-423,307,196,-839,974,993,-892,-886,267,762,-469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00836() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{702,-1000,-341,758,1000,-91,628,-99,31,-1000,1000,508,-729,657,-1000,-272,-963,1000,1000,1000,-1000,-905,648,346,188,-521,-151,78,-312,924,-1000,-1000,-1000,1000,1000,386,-187,-1000,-809,1000,1000,-127,445,-609,1000,-1000,1000,1000,1000,1000,273,464,-1000,1000,633,361,1000,1000,-981,-254,-859,-940,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00837() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{1000,-1000,-976,-179,1000,-945,-1000,-1000,970,-1000,388,79,260,-713,-1000,-202,-57,1000,1000,1000,-1000,1000,-397,466,-1000,427,426,-672,-705,779,-1000,-464,-1000,1000,594,0,-420,-1000,-1000,1000,1000,589,785,-1000,570,-974,462,1000,1000,398,1000,-1000,-407,1000,-423,191,-266,1000,735,-320,-1000,338,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00838() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{851,862,409,752,827,942,-828,-989,-897,-922,-16,-612,-431,337,258,749,294,-123,-766,-950,28,-930,441,910,351,888,-494,4,322,-571,-470,-10,284,-67,522,-986,848,-748,348,-836,49,358,85,-303,126,192,721,-659,-510,-83,740,504,-980,470,-725,867,-928,210,-765,-520,277,-616,-874,-761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00839() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-625,621,108,-173,928,-856,786,249,-215,-584,-95,-662,-462,-1000,1000,573,1000,547,1000,216,175,-1000,466,-68,576,1000,-209,-21,477,-1000,682,-610,-340,396,25,1000,1000,1000,-713,95,1000,-626,1000,-113,-1000,313,75,458,414,-1000,1000,-30,310,-415,-1000,-226,259,-734,268,-358,-117,-470,-1000,683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00840() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{590,-681,-807,-63,713,-528,841,-611,1000,-880,199,-28,592,-609,-301,64,833,1000,412,978,-1000,136,266,-70,-423,-714,-167,-302,-382,92,-752,-1000,-1000,-1000,549,1000,776,150,88,1000,-808,-509,1000,47,522,-1000,-223,1000,874,-203,-1000,166,202,418,325,507,1000,-124,-459,327,1000,-1000,-380,388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00841() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{1000,-1000,-736,1000,544,1000,-828,-1000,-196,-274,710,-544,-84,216,-1000,92,-118,-155,-766,1000,-557,907,-573,277,-507,-952,-1000,-294,-816,1000,-1000,-447,-339,-34,1000,-1000,-400,-1000,615,-836,-261,444,337,-931,1000,-398,1000,-753,404,-83,-38,-467,-1000,1000,400,1000,-915,74,-358,-367,-48,-607,299,303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00842() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{-403,-655,-943,715,424,-109,818,952,861,377,-946,615,472,108,-473,855,847,458,840,44,-88,-622,-213,-616,49,-812,-320,271,744,-644,-329,1,286,-466,-500,-266,429,-348,952,970,874,-441,828,231,909,369,211,125,-371,-541,-535,498,-230,888,-489,37,-594,904,-320,300,460,151,702,944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00843() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{588,1000,-631,-590,774,-674,275,58,1000,-340,990,750,1000,-417,-423,783,523,77,-299,279,-373,-250,778,1000,218,948,-304,949,692,-477,-325,-113,-180,-181,-404,-431,100,-242,777,-605,202,-1000,724,1000,-359,-487,-376,66,-129,119,381,705,303,-344,363,1000,-81,102,-386,-55,456,-877,-351,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00844() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{1000,-157,-1000,-755,774,-997,-558,-1000,1000,-584,596,398,1000,-595,-40,169,-217,764,1000,691,-1000,1000,22,1000,-1000,41,189,115,-501,768,-1000,-756,-1000,1000,517,-467,-594,-1000,-545,1000,-362,-377,1000,-80,-512,-1000,-20,1000,1000,1000,744,599,-174,738,767,1000,94,1000,479,-249,-886,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00845() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{791,69,-454,311,907,729,-471,-597,-726,-462,661,-611,282,450,-692,873,-991,1000,799,497,306,1000,-359,1000,-455,964,-1000,634,-565,826,-272,452,-784,596,-2,-1000,134,-1000,1,-1000,-622,-438,-289,-607,708,-1000,1000,-1000,165,314,640,931,-877,593,-346,1000,-1000,588,-809,-365,-1000,1000,1000,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00846() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{851,-156,-217,346,877,581,-743,-951,-176,-895,907,-1000,-239,-654,163,188,209,174,179,233,-644,-655,213,389,204,486,-551,-369,213,-387,-796,-1000,14,145,986,-348,815,-1000,676,449,425,298,566,-444,156,-701,794,434,752,355,709,-438,-958,536,-72,597,-89,294,-529,-476,-151,-1000,-668,-684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00847() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMarkers(org.jfree.chart.util.Layer):java.util.Collection",
            new int[]{1000,-263,-720,-275,774,-217,-134,-616,-206,-945,-293,-829,-188,-1000,-935,1000,-207,780,1000,691,-208,-119,33,-67,-568,636,270,485,-925,-552,-729,-793,-559,-204,995,-26,1000,-844,-499,792,628,159,1000,-80,-578,734,1000,-48,752,-1000,507,-312,-86,960,-690,566,122,-1000,576,127,27,47,-228,-587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00848() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{-1000,137,-708,-151,743,671,959,479,230,507,-1000,16,613,-475,-59,-953,164,-1000,370,-204,8,191,918,1000,-280,-468,-1000,468,-616,483,1000,1000,-1000,1000,-357,-467,-317,810,1000,-668,-1000,-287,553,-1000,190,606,-1000,-240,482,1000,-267,1000,-1000,805,-496,738,-563,-1000,-1000,98,86,-1000,-58,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00849() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{426,790,-362,25,-945,345,828,-138,-888,482,304,322,-643,-425,-996,-106,-434,1000,-1000,-129,581,722,-304,-563,-263,-386,785,-30,-515,331,280,317,917,230,868,601,338,-26,182,-1000,-542,-1000,-1000,1000,-441,573,-559,6,13,-157,-50,-853,-1000,-1000,-301,1000,-409,-104,445,765,-826,-238,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00850() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{-638,379,-384,-649,1000,-627,704,610,-776,876,-299,823,125,-991,115,-646,131,-865,-345,-381,390,844,-143,633,1000,-396,870,268,-477,-22,1000,568,-489,629,-51,256,7,619,-210,-412,-478,453,-70,-628,376,113,-503,-484,-603,1000,637,841,-1000,-365,26,-231,-10,160,-805,203,-692,-1000,-113,529}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00851() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{-731,-158,554,111,-48,1000,767,679,-658,-934,883,587,-255,84,-501,25,-937,-31,1000,121,19,-962,336,-249,-84,728,-271,-786,-522,-80,-29,49,598,422,-240,34,-48,-302,967,-51,-1000,-1000,445,99,-630,114,-20,-871,-1000,-998,1000,-853,263,739,-523,-424,-317,-1000,-525,790,1000,878,126,-50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00852() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{743,379,977,-254,-202,-742,-571,444,23,1000,-116,1000,-615,373,769,1000,131,984,-4,1000,-380,272,-698,-1000,1000,-704,870,-639,821,-1000,1000,-545,-1000,-142,-628,1000,-903,-52,-1000,-91,-243,1000,-70,1000,1000,-1000,-295,1000,240,1000,50,1000,389,-948,984,76,-10,-364,572,440,-792,-1000,1000,-324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00853() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{334,1000,-716,487,-959,134,1000,-738,-847,-906,13,162,-636,-547,-1000,-250,-491,1000,-541,-342,-373,-1000,-275,15,-525,58,1000,-860,-780,295,151,1000,1000,-31,767,492,678,245,290,18,-112,-891,-991,1000,-935,-1000,-379,-293,-113,-259,933,-1000,-400,993,-393,-159,-132,451,183,607,282,-251,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00854() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{-322,786,629,-388,-153,1000,239,-127,203,406,-425,-811,-99,1000,-78,1000,-588,-341,26,943,381,-310,-20,-919,-610,931,-306,-1000,227,211,852,-583,-1000,54,361,716,-726,-821,1000,-439,35,-1000,505,1000,767,1000,-704,39,-801,-803,530,768,-1000,-874,271,987,-1000,-1000,-108,1000,547,442,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00855() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{-142,1000,-732,215,693,-771,-94,-444,469,538,100,-64,-823,-680,441,-1000,-95,74,-543,-79,1000,-811,-860,-658,1000,-182,1000,-1000,-455,283,-153,53,327,-54,-16,858,98,1000,-1000,1000,-610,54,-898,148,-726,-433,-1000,-271,-172,906,1000,-139,-1000,47,1000,-707,-90,-631,-530,482,997,-76,528,-68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00856() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{-215,1000,-516,195,159,-880,-209,-661,-278,875,-258,-229,-508,-209,-1000,-335,-1000,470,629,-256,941,-1000,-675,417,-1000,-312,577,-1000,-793,525,-131,1,761,-786,-101,1000,147,688,-263,860,842,-996,-800,262,-54,-165,261,-302,-692,-922,875,-845,1000,680,-834,-644,-101,473,-86,800,855,-926,558,163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00857() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{1000,205,184,-876,-626,-1000,1000,-717,-86,806,191,1000,-1000,17,-271,-690,-472,-1000,130,431,-191,-847,-121,-162,239,-492,-95,-186,146,95,135,647,61,-1000,32,583,190,1000,-1000,-1000,1000,796,645,850,727,-1000,1000,798,-1000,-517,733,-199,1000,-742,45,-961,-481,-1000,1000,-660,1000,-964,1000,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00858() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{322,1000,-352,149,-165,-737,-118,-1000,-328,-1000,98,-1000,-706,-610,-943,-1000,-278,370,-345,-1000,589,-299,513,1000,188,-692,408,726,-1000,1000,78,-23,1000,1000,694,-108,1000,507,-978,649,1000,679,-502,-628,-650,4,630,-192,-403,-311,-357,-1000,815,768,-988,-1000,596,850,218,-769,1000,-808,-113,307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00859() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{21,-636,184,86,-754,437,-146,1000,493,860,-1000,-521,118,1000,383,1000,-737,-195,370,1000,738,149,-830,-1000,-363,711,316,-1000,1000,-811,237,-87,-1000,-924,-102,1000,-343,-599,549,-765,-1000,1000,140,1000,1000,848,-1000,303,307,29,503,1000,-1000,-734,1000,544,-1000,-604,-515,710,-956,-89,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00860() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{-638,1000,509,-368,-1000,646,704,-127,646,875,-299,-487,-253,886,-943,905,-773,625,430,796,401,-165,-339,-764,-738,479,116,-875,-521,-22,34,-1000,706,-1000,566,898,-526,272,-210,132,183,-567,-265,1000,376,113,968,-446,-558,-825,359,-1000,400,117,-236,408,-551,-825,205,203,1000,-301,-113,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00861() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{-66,1000,-166,766,-1000,971,288,616,192,-1000,-1000,-1000,-70,-548,-1000,-349,-1000,317,566,563,1000,-54,559,886,-550,-377,-957,1000,-674,1000,412,1000,177,345,150,857,-533,-1000,1000,-716,-203,1000,457,-841,433,1000,-731,36,404,517,-1000,-1000,-69,1000,-262,738,-665,542,-702,-131,1000,-1000,404,849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00862() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{-247,291,-52,-1000,697,685,11,738,306,964,-266,858,-85,-527,134,-1000,736,-1000,-791,-1000,622,-793,-21,1000,302,-256,362,1000,-1000,-481,835,117,-479,-137,58,-566,648,1000,-758,-448,454,1000,208,-1000,324,-1000,-135,-1000,-1000,875,1000,-1000,-1000,495,-151,-309,1000,532,-873,-1000,400,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00863() {
        org.junit.Assert.assertEquals("COLOR:-1", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlinePaint():java.awt.Paint",
            new int[]{-467,1000,-561,-237,839,-43,931,114,-1000,-410,859,152,-454,-1000,74,-1000,321,201,168,-1000,795,-915,-64,496,89,-219,557,92,-1000,595,563,1000,1000,1000,1000,736,-342,982,-784,-269,649,-907,-336,-887,-1000,-999,-96,-1000,-1000,108,991,-1000,-97,957,-1000,-10,730,-800,-626,228,1000,-164,-26,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00864() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{603,-180,174,363,395,-751,25,1000,-770,-681,108,15,-31,166,-1000,493,-1000,1000,957,206,1000,1000,-1000,-217,-1000,-916,-440,-1000,42,-360,-442,1000,-1000,-1000,25,-286,-510,332,875,-1000,-1000,-63,-362,741,-1000,-361,-424,-1000,-1000,136,-699,1000,-1000,-1000,118,1000,1000,735,514,-1000,1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00865() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{-653,-237,359,-1000,-1000,271,-84,967,464,1000,-208,-22,200,529,-830,-124,-691,-138,-424,147,1000,341,-1000,-1000,-433,604,773,989,961,1000,-148,152,638,-537,-240,-991,1000,-193,1000,-75,-1000,705,839,79,120,-403,-855,103,-261,-1000,855,213,-1000,1000,238,545,-705,712,-29,-433,546,1000,675,-286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00866() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{307,-506,774,-531,-963,-280,70,-306,-712,-1000,-813,-299,-403,-958,1000,260,-742,-1000,1000,271,-1000,-817,567,947,867,-704,-953,-1000,-129,108,-1000,-981,-1000,-327,505,1000,-259,-1000,-402,731,-199,-720,960,-1000,-675,1000,-1000,641,-1000,-3,-1000,-1000,614,-182,-1000,1000,-360,-939,-1000,1000,-778,-51,107,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00867() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{-611,1000,779,-912,-645,484,-152,1000,955,774,-580,-627,-981,-185,-827,96,-48,-167,3,872,681,974,-1000,62,-338,754,833,717,-310,1000,-272,1000,668,-1000,-234,-840,968,-271,330,-325,-577,980,313,222,7,381,-977,205,-509,-39,273,1000,-1000,987,517,566,-705,440,253,-418,-83,345,1000,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00868() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{-471,544,743,381,683,-414,-283,117,1000,-294,-360,-178,994,-20,-548,-251,-694,935,481,-1000,221,865,-153,-98,-620,-56,144,-525,1000,-295,521,1000,343,-407,441,-386,-389,-444,-314,-858,-226,-650,-476,1000,-579,-618,174,-281,531,676,-154,-338,-302,-1000,142,613,-125,1000,-666,-313,790,-235,220,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00869() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{-652,1000,359,405,1000,37,1000,739,988,1000,-544,-985,83,-868,-1000,344,1000,1000,333,-247,1000,1000,-1000,929,-1000,-126,-612,-922,-1000,-457,871,1000,914,-1000,-947,-1000,-871,1000,707,-1000,441,1000,-1000,1000,-265,345,1000,-1000,21,1000,-405,1000,-554,-668,1000,686,1000,-398,524,-1000,622,-1000,737,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00870() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{356,48,619,-160,-194,-517,815,-228,-398,985,-55,774,1000,1000,368,318,954,734,-435,-624,-39,437,-540,114,-1000,701,-1000,985,138,1000,1000,490,676,-885,118,-927,-327,-553,758,183,327,1000,1000,698,1000,-246,-188,-741,-1000,-63,1000,-877,-519,788,230,-139,552,-292,204,-1000,459,306,-668,454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00871() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{-530,833,811,-493,536,-806,408,634,-268,-9,-531,-754,122,-468,-506,-97,304,952,260,-806,964,1000,-743,1000,-1000,559,-497,321,-213,831,589,1000,275,-1000,1000,-815,-391,402,20,-1000,-127,514,-1000,1000,669,550,725,-916,-15,552,-209,775,-557,-1000,402,302,562,702,13,-1000,208,-483,351,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00872() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{-530,-1000,-728,584,-263,-751,604,-110,-936,674,370,572,951,1000,-758,1000,304,976,22,-806,982,-223,-743,-589,-1000,-271,-940,219,816,331,698,-212,480,255,-522,-619,-354,799,581,-370,161,608,214,161,294,-775,650,-959,-518,-697,849,-280,-108,-52,-58,-186,1000,-443,63,-1000,703,-65,23,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00873() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{810,111,-96,-809,174,-681,675,-11,-776,-989,14,646,450,418,-959,1000,957,-367,-227,18,1000,-25,-1000,207,-1000,527,-309,945,180,926,1000,152,950,-122,28,-961,-456,-550,266,-546,330,837,-520,353,1000,1000,1000,-1000,-500,-251,830,86,-85,-588,75,-372,1000,-208,-195,-1000,1000,-113,1,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00874() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{-162,321,554,-42,258,-1000,1000,-101,-886,889,164,121,1000,932,-830,551,-142,983,-424,-806,988,816,-1000,544,-1000,1000,-612,1000,499,945,1000,1000,1000,-537,536,-1000,-58,886,707,-1000,500,1000,-470,1000,645,-103,1000,-971,240,301,-460,388,-816,-336,774,-145,1000,1000,524,-1000,306,-363,737,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00875() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{818,61,483,-1000,-1000,-326,-92,843,283,231,-698,-726,705,661,327,750,-846,-572,-162,1000,668,856,1000,-36,-5,891,1000,-528,945,582,-703,1000,860,-966,-210,-788,537,-565,1000,-1000,-328,1000,1000,80,333,-1000,-962,387,-693,-1000,77,249,-1000,1000,-190,590,-474,201,327,-286,-781,412,-397,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00876() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{-1000,211,590,735,-551,192,-1000,873,867,-1000,-1000,-1000,-138,-956,-506,333,304,-867,766,-317,964,560,1000,-1000,-33,-13,987,-691,320,-400,-1000,473,32,-447,-12,-197,285,-1000,-186,-1000,-1000,780,-738,732,-807,-1000,-1000,659,-171,529,-1000,1000,-453,398,714,818,-872,1000,-688,165,-24,1000,1000,-871}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00877() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{1000,-986,-886,-219,-303,-518,519,69,-1000,832,683,1000,403,1000,-1000,1000,69,1000,66,1000,1000,-235,-1000,-984,-1000,-622,-1000,219,651,143,455,-212,6,255,-1000,-390,-210,962,1000,-53,-196,579,527,-246,-173,211,256,-1000,-1000,-1000,849,-170,-325,-52,-299,-107,1000,-696,242,-1000,1000,-318,307,660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00878() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{-673,-251,-403,-527,645,-467,303,1000,254,540,-176,-152,1000,166,-1000,-919,69,1000,82,206,1000,367,-669,-62,-1000,-307,-114,-640,1000,-209,196,152,-4,-122,-116,-948,-968,109,-180,-749,-1000,474,-474,741,21,216,798,-1000,523,-1,-418,136,-582,-753,-125,980,1000,735,-480,-1000,1000,-345,-222,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00879() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainMinorGridlineStroke():java.awt.Stroke",
            new int[]{129,-1000,-886,151,-253,-703,-106,-1000,-1000,832,-58,739,406,292,470,1000,-1000,349,589,-1000,-820,-235,71,-720,38,-622,-1000,-881,651,-781,144,-1000,-743,1000,-510,1000,-752,20,-860,382,-158,-543,400,-1000,-829,398,256,-1000,-684,49,-342,-1000,1000,-509,-1000,-1000,671,-1000,-1000,301,941,-318,-267,162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00880() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{234,-1000,-671,-77,959,-1000,-752,1000,-717,929,1000,18,-1000,1000,-61,981,-878,-492,-630,1000,1000,-1000,-1000,-1000,1000,-582,558,1000,-1000,1000,57,-287,1000,-714,-415,-648,-513,-1000,1000,203,175,348,1000,1000,194,1000,458,2,1000,-233,1000,-682,849,1000,400,142,1000,-188,-1000,-542,1000,-1000,1000,-348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00881() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{260,-893,-957,-954,746,-59,588,-362,377,76,-365,703,-728,1000,136,-1000,-1000,-302,1000,-1000,1000,-359,-440,624,717,391,1000,1000,-1000,1000,-800,19,844,48,-1000,-447,-36,-1000,447,-147,-652,847,1000,265,1000,-633,830,687,296,-1000,1000,-815,-413,887,1000,363,78,-1000,-1000,-532,-226,-1000,818,-100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00882() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{735,621,166,-77,-48,808,312,383,718,929,4,976,533,756,-938,-612,473,494,-670,-126,-771,-407,339,175,-694,83,-298,383,217,-663,611,-3,-515,-83,-96,-455,-913,178,-842,-375,528,735,-2,978,197,517,-619,320,-151,166,-980,26,-878,-163,-703,-500,-162,-933,18,78,-485,313,198,-353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00883() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{1000,233,1000,-346,-21,197,1000,-589,-353,-1000,231,-188,-1000,-413,798,970,-715,790,1000,469,544,335,214,-1000,-602,133,546,-559,-1000,-112,-941,1000,853,-878,-1000,-236,-385,-1000,1000,331,-313,-41,771,1000,868,-125,-1000,252,812,-515,188,-737,-802,-114,931,-695,413,-598,475,1000,970,-254,1000,-345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00884() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{964,-927,939,-248,-696,344,943,-761,23,-28,-236,298,-818,335,175,-71,-270,-840,927,-44,919,529,238,-531,643,-537,134,-303,-523,-863,414,234,124,-485,-894,635,-202,-717,291,902,-339,-710,-132,121,850,-583,-542,637,855,-236,-17,-485,-933,-620,-295,-224,145,-647,456,922,528,19,688,-433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00885() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{-728,708,153,738,1000,347,31,-299,-224,533,852,1000,498,1000,311,-979,-214,-878,-10,-558,1000,-779,-237,1000,-406,-101,510,1000,-239,1000,599,-1000,-188,333,-82,1000,-497,-638,1000,-855,-34,-686,1000,906,346,-99,338,131,543,-140,1000,858,1000,-111,-1000,-1000,875,59,-1000,-980,1000,-1000,1000,-808}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00886() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{-7,-361,-919,-309,-733,-196,54,13,-304,493,-265,-933,201,-1000,335,-401,-114,-390,1000,185,387,-134,472,-108,76,-816,-490,-122,447,-862,-663,1000,217,-353,-77,52,271,1000,634,-400,-511,1000,-1000,-1000,-322,-171,890,-798,-607,579,845,-519,-1,344,417,191,-458,626,-330,-368,-511,146,826,-569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00887() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{924,-508,-536,-584,48,388,-1000,-670,1000,146,-666,-124,990,309,1000,-1000,339,361,1000,-1000,93,404,831,558,-840,994,479,-600,384,401,-860,-757,-308,1000,52,1000,-394,-627,480,1000,-984,143,-400,-400,-642,-1000,884,-695,-803,-1000,299,750,1000,-103,400,-852,774,-34,176,-1000,717,-264,-1000,905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00888() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{162,-67,-149,426,539,-651,42,-495,833,-139,38,234,-612,243,-10,479,399,-972,870,729,753,149,-960,922,-78,-561,823,924,502,-353,202,-343,971,364,-132,9,-990,-690,805,-882,-617,798,372,409,724,-110,-613,841,862,726,-882,219,323,838,-714,-841,521,-128,189,288,796,-938,-124,-370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00889() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{563,-814,-921,-718,1000,-1000,-292,494,-935,320,-460,-924,-736,-1000,818,-435,-148,320,115,429,-150,-142,853,-893,447,-310,-764,28,-148,-1000,-1000,1000,678,-816,111,-596,-857,1000,11,203,-999,1000,-1000,-298,-278,963,1000,444,-325,149,334,-1000,-151,1000,970,1000,-283,814,938,218,-677,-346,1000,528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00890() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{788,516,529,-603,-863,260,42,673,64,177,253,-618,-675,399,-162,-1000,371,61,634,-254,475,-481,654,-304,-560,318,-156,-610,-295,-132,-880,674,374,543,-695,1000,-271,-290,-46,44,-875,289,362,-168,41,503,-252,-549,-249,456,172,-845,150,353,878,-156,-185,447,29,-1000,1,-111,475,-357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00891() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{-247,-883,-857,78,591,-868,448,103,-692,1000,-213,-226,-1000,-974,-6,550,-1000,-237,94,917,498,-468,95,399,-394,-1000,-572,50,-398,-897,-856,1000,388,-938,544,-564,-1000,220,-763,-1000,-569,1000,-676,-320,-96,1000,1000,303,542,64,50,-1000,1000,967,651,966,927,1000,576,210,-220,76,1000,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00892() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{804,-818,278,101,628,-588,-638,271,833,953,79,-426,-1000,599,-362,479,663,-969,728,1000,811,-9,-469,600,419,312,397,1000,502,-395,-136,165,1000,364,-914,-39,-620,-679,360,-173,-886,-654,402,253,894,290,953,848,862,726,-1000,-741,259,824,258,281,262,396,107,51,670,-748,681,-372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00893() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{-330,-596,523,-600,-309,-227,-963,322,732,977,928,292,-311,13,544,1000,787,140,-957,1000,804,-789,-738,-850,1000,186,-1000,663,-872,1000,-1000,-1000,-45,-47,-1000,632,-60,-124,306,291,1,-1000,711,1000,561,933,828,1000,1000,389,978,817,953,-932,-1000,-814,928,1000,-682,-605,211,-586,-919,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00894() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{-144,452,928,-10,-935,869,-572,-455,-345,210,244,1000,416,688,484,-342,1000,-731,64,310,1000,-1000,-800,1000,-102,-695,67,-110,1000,1000,596,239,-464,968,484,1000,-11,358,-198,-14,-643,-1000,357,662,8,-1000,521,-1000,388,531,-128,673,731,26,-1000,-996,718,233,-370,-1000,1000,59,-1000,100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00895() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainTickBandPaint():java.awt.Paint",
            new int[]{-64,-175,-614,832,1,339,-233,-1000,1000,-162,-564,523,-164,-769,-933,-756,295,-419,600,810,757,781,-975,899,-472,-1000,603,1000,615,-224,351,49,399,1000,77,-603,-963,-1000,1000,-1000,-37,57,155,-389,111,247,-1000,1000,858,486,33,964,-12,543,598,-1000,568,-374,-262,913,552,-836,-273,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00896() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{-537,507,77,-695,-888,4,589,-584,-643,682,233,353,-230,-1000,904,838,-283,123,-140,-626,995,-836,-626,1000,-376,441,520,-684,-658,371,-331,-1000,1000,568,-336,-877,-568,1000,124,341,610,-139,-424,-1000,417,260,-173,-729,745,-1000,1000,301,-759,121,723,-414,-47,-343,-714,90,-416,804,516,187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00897() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{201,314,-811,649,-1000,-640,-1000,67,-984,-819,-884,-171,511,-163,-1000,-1000,-305,-307,619,1000,-1000,-9,-927,-111,272,-351,1000,-1000,1000,546,1000,1000,84,917,-213,-368,1000,-1000,-429,860,-903,-416,-1000,1000,1000,818,1000,1000,868,922,-1000,1000,-842,48,367,1000,-989,-113,1000,-1000,309,-1000,-856,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00898() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{-45,1000,349,-125,77,-302,-101,389,-59,264,609,-805,185,-129,-1000,453,-798,-1000,856,-312,-1000,-747,-123,-181,119,-251,-775,900,-428,-1000,-1000,129,-122,13,276,-161,-284,-303,1000,502,995,-501,1000,-687,912,587,-926,341,-1000,-244,-359,85,-115,-313,569,489,1000,1000,-301,1000,616,717,-406,569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00899() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{-178,894,-216,-1000,-1000,370,-96,433,-850,478,205,497,479,-1000,673,1000,-811,504,-870,-564,-245,-249,-130,-1000,-275,455,353,-361,-102,-793,-137,-259,449,1000,-507,-1000,-1000,332,605,-355,922,-16,1000,-535,109,1000,83,-956,745,111,-83,1000,-741,-43,1000,599,136,-562,88,-491,627,138,-453,-380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00900() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{225,-217,176,-214,57,-866,-1000,-612,56,392,-523,23,-438,-90,-1000,300,655,-1000,1000,822,52,-104,-1000,-1000,281,-145,240,347,311,1000,418,1000,-717,171,432,376,25,-412,-203,246,-26,-1000,1000,570,-99,-611,400,1000,-1000,300,-1000,350,-302,13,-379,1000,-1000,758,1000,-1000,56,951,-65,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00901() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{292,-144,204,-647,-377,383,255,143,-551,-353,-255,-628,-523,414,1000,1000,-341,-928,106,-262,435,101,386,-332,-69,160,-531,-308,-334,-509,-430,-1000,-107,-4,467,-53,-1000,1000,73,132,1000,359,656,-343,-346,-299,-396,-611,613,620,396,-724,401,-311,115,-472,341,696,-1000,654,434,1000,520,524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00902() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{497,983,-865,255,-21,-640,-1000,462,146,-641,-328,-171,873,517,-1000,-1000,-59,-115,394,487,-1000,-1000,-507,-111,163,-681,964,1000,190,445,-68,1000,-230,838,764,-392,870,-1000,526,860,-903,-803,1,335,1000,818,-386,1000,388,76,-292,1000,-1000,-337,-912,1000,821,-300,345,335,239,-452,-856,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00903() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{-575,-71,-137,-517,-1000,-287,301,-541,-820,52,571,158,327,-927,292,625,-551,177,-389,-769,422,-784,-615,495,-258,142,-428,-190,-194,-324,-367,-213,741,776,-610,-758,281,1000,-275,-7,-13,66,-1000,-101,965,84,247,149,981,-682,-635,332,-809,453,1000,-522,-214,-1000,-71,529,-216,-392,-68,115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00904() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{-344,-199,-90,-443,-866,-879,-569,-368,260,320,297,1000,-1000,-1000,-170,-292,-139,-490,746,1000,-76,-660,-1000,-574,-1000,-782,1000,275,518,1000,-154,911,241,1000,-360,-1000,1000,-613,-404,657,18,-707,-1000,-40,978,1000,333,1000,1000,-960,52,971,-1000,-110,-699,55,-57,-520,378,546,49,-653,417,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00905() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{134,716,-572,-622,-328,569,2,-323,1000,-90,360,-58,556,28,1000,-1000,-237,1000,-367,-158,968,-30,1000,1000,-1000,-1000,1000,-143,-660,380,-1000,-1000,1000,685,132,-1000,1000,57,-94,1000,1000,28,594,-1000,1000,1000,-750,-1000,1000,588,1000,488,502,-328,-955,543,1000,1000,-1000,782,-445,620,1000,-798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00906() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{-69,201,-1000,262,-566,1000,821,73,1000,-888,542,-420,924,-588,-872,-633,-839,434,-345,310,-1000,789,825,837,-693,461,-649,575,1000,46,-864,330,-290,653,-916,40,59,-32,1000,-99,-658,238,176,171,-145,1000,499,-872,-192,968,347,-189,209,-111,854,-627,286,1000,-35,-783,-307,-885,-775,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00907() {
        org.junit.Assert.assertEquals("COLOR:-12384750", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{-156,357,-557,-285,-1000,720,903,-336,-510,-276,-272,-453,-243,17,-400,863,579,6,274,-654,1000,-809,-465,1000,-275,-955,551,-1000,-733,259,-513,-1000,1000,479,152,-497,-928,574,-142,539,988,787,-768,-564,199,461,574,-921,1000,-1000,1000,85,-295,-925,206,-786,560,-350,-1000,735,-591,839,661,18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00908() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{444,1000,38,151,-556,-613,-1000,510,87,-604,-530,-1000,488,662,-1000,-400,184,-381,652,525,-344,-841,-379,-842,414,-634,1000,717,-167,634,-1000,1000,-461,760,835,-244,349,-1000,-242,852,-450,-682,1000,483,975,1000,-570,1000,1000,-71,-504,1000,1000,-577,-1000,1000,-33,-648,177,61,84,-40,-612,-524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00909() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{-268,-497,-458,-1000,283,-52,-432,236,-502,-33,652,-468,-505,-716,-274,-587,-320,-649,294,-532,-436,1000,547,641,-1000,787,-703,207,419,664,168,666,-502,-86,-451,-362,1000,92,-654,-418,-453,948,-1000,136,893,916,978,58,274,-277,747,-1000,981,478,1000,-326,599,-1000,1000,47,-338,-1000,-816,212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00910() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{351,-266,964,114,412,798,1000,-238,546,-351,-248,-1000,-1000,696,-521,1000,-250,-1000,1000,-858,351,-702,-46,-1000,496,-373,-1000,395,-1000,-1000,-1000,-500,-187,-441,715,425,700,114,102,-410,1000,-707,1000,-1000,249,458,-1000,95,-795,-560,883,-980,-143,-1000,811,-445,1000,1000,-971,-432,-537,1000,730,-36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00911() {
        org.junit.Assert.assertEquals("COLOR:-16777216", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselinePaint():java.awt.Paint",
            new int[]{41,1000,-407,-166,460,-56,-808,-315,274,1000,886,956,862,-1000,348,-415,13,-119,121,-856,-587,-269,-315,1000,-1000,-729,1000,-326,933,-382,49,-1000,1000,643,-792,-22,-345,1000,1000,1000,1000,-801,1000,-1000,422,633,525,-1000,291,-1000,387,289,1000,-226,667,-369,-98,1000,-954,-1000,746,775,-263,170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00912() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{-563,649,63,-627,-203,-702,-1000,-601,179,814,-788,236,1000,50,620,-1000,-1000,152,313,176,115,-272,1000,338,1000,-775,-608,1000,-587,-587,1000,23,835,449,556,1000,-293,-296,-298,549,758,-32,-1000,113,386,931,1000,558,309,393,2,464,-261,-81,1000,-294,14,128,607,779,829,-847,714,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00913() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{45,417,-664,535,917,-101,801,-873,-509,-29,71,-227,-122,64,-143,-249,-808,1,879,-64,861,-963,-461,302,-779,668,828,-688,283,493,30,-406,-583,839,-91,-685,-378,-151,-690,155,51,309,327,913,-512,623,556,405,-404,-983,326,568,-60,319,262,430,990,469,47,-8,662,-700,840,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00914() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{80,-204,-261,-324,-1000,-3,-1000,78,-20,1000,-365,628,679,371,-435,354,-595,1000,-112,-1000,-1000,690,1000,-396,288,147,-323,215,95,-154,391,598,-395,620,-964,325,-958,1000,-515,15,-1000,-739,126,196,-1000,-823,-506,-1000,-63,-447,771,555,1000,-1000,-585,244,-1000,503,179,209,20,-42,619,-300}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00915() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{-461,-635,-117,770,-211,1000,-1000,579,635,335,98,-683,1000,-702,78,-227,84,-871,451,-512,1000,1000,1000,197,1000,-795,36,671,-1000,-759,-106,-321,1000,-6,118,1000,-426,-635,-134,-278,496,-708,115,-543,750,-1000,724,-14,-204,1000,-627,1000,550,-828,1000,-1000,-707,1000,1000,368,999,-1000,84,605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00916() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{89,1000,-526,-251,826,1000,-154,-242,-300,-572,-1000,-159,1000,-936,373,-491,-1000,255,289,1000,664,-272,405,-935,621,-1000,-332,19,-1000,-497,1000,737,-64,235,373,1000,-99,-644,-200,-1000,-427,27,-1000,421,372,972,961,122,-17,70,344,967,-815,922,1000,296,894,-472,379,-48,829,-884,1000,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00917() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{-558,-966,-426,242,3,-661,-717,-567,-99,-270,56,1000,1000,-346,-81,-1000,-597,381,1000,929,-497,-179,802,-512,-1000,-139,261,-515,-189,-116,384,359,862,299,-431,36,-1000,-559,4,1000,-8,-676,932,364,72,691,504,-605,-140,1000,753,586,854,-919,-15,952,146,-149,761,-327,220,66,648,772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00918() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{-628,-1000,-727,-485,209,-464,-882,-914,-1000,534,-134,98,776,79,-1000,-992,55,1000,784,-584,-292,-593,725,230,-1000,1000,-317,-565,362,661,66,-173,-926,734,-1000,-647,-1000,-144,435,1000,-1000,-1000,957,244,-1000,-722,-1,-1000,-831,-552,839,1000,1000,-1000,-875,1000,146,-85,32,506,406,1000,904,747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00919() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{15,-204,-426,175,400,-911,294,-968,-595,571,89,621,-400,519,-1000,-540,461,1000,163,-8,327,-376,-400,-128,-1000,1000,239,-1000,996,823,-56,-182,-1000,1000,-952,-1000,-1000,1000,96,1000,-1000,142,169,807,-1000,513,340,-669,-167,-536,1000,45,689,-319,-526,1000,146,-176,-56,221,186,1000,1000,-297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00920() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{1000,1000,-487,-528,61,49,1000,992,893,-38,237,-969,-1000,917,-249,1000,249,1000,-1000,-393,-645,1000,-1000,-863,-305,243,-30,-1000,1000,1000,-960,-1000,-1000,-14,857,-1000,901,721,-586,-1000,1000,1000,-1000,1000,-411,-290,-19,873,-31,-180,-567,-1000,-212,1000,-899,281,-553,939,329,-487,1000,-987,84,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00921() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{440,-26,-836,-543,1000,1000,191,-19,360,-455,-477,-1000,430,-1000,-842,1000,-242,-589,-96,-1000,811,1000,141,-1000,1000,-939,223,11,-1000,-337,-451,-158,-82,296,520,1000,-548,-497,-873,-871,91,187,228,228,611,-681,445,-930,568,78,218,1000,-494,390,1000,152,686,493,1000,296,850,-1000,645,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00922() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{-322,499,-104,-1000,-470,-16,-902,1000,-340,792,365,897,-104,1000,504,-1000,1000,151,533,530,484,-1000,821,903,-1000,578,-152,-35,-18,148,462,139,427,-584,-932,-982,-552,839,591,952,42,-1000,-363,-989,1000,-194,86,1000,-1000,-641,-461,781,1000,-432,-1000,-1000,-1000,899,-812,-66,302,574,-630,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00923() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{11,-1000,-271,-67,1000,685,-85,944,-738,795,-1000,70,716,-309,-608,-1000,-1000,-202,1000,-64,-1000,-1000,1000,114,-779,-96,261,-628,-593,208,908,134,1000,824,-1000,-200,-551,279,-367,369,-602,-178,-714,405,-250,1000,796,546,-625,-1000,-7,1000,1000,800,378,643,1000,-17,-149,5,582,-646,1000,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00924() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{691,1000,63,-627,1000,-54,992,-601,639,-640,670,-1000,1000,324,-668,112,1000,-518,-271,-132,83,358,-930,1000,402,1000,1000,39,325,961,-1000,-1000,497,-734,1000,-1000,298,-296,550,84,596,141,-1000,-30,1000,-918,807,1000,-668,393,-1000,-774,97,1000,939,-987,652,1000,1000,921,1000,-1000,-257,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00925() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{-362,109,756,-543,-786,-486,-541,307,336,1000,237,-1000,-104,931,-532,1000,249,-284,-482,-1000,33,-226,297,1000,1000,520,558,1000,605,-210,-528,-1000,944,1000,141,-35,89,1000,-149,41,1000,-738,-441,-180,1000,-952,-783,1000,411,-455,-567,-352,-97,-573,986,-1000,-1000,722,691,1000,229,-1000,606,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00926() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{-136,-635,-47,-552,-574,-297,-1000,579,635,828,-704,114,775,-295,-172,-52,-717,-871,-466,-400,-736,911,1000,-571,1000,-925,-871,849,-761,-557,574,601,359,183,-182,1000,-1000,389,-437,882,-267,-708,-658,-142,90,-305,-55,-842,598,458,322,797,550,-478,-82,-233,-707,273,1000,556,143,-480,733,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00927() {
        org.junit.Assert.assertEquals("TYPE:java.awt.BasicStroke", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getDomainZeroBaselineStroke():java.awt.Stroke",
            new int[]{-461,727,1000,-844,-211,351,450,1000,846,-625,98,-1000,-510,-21,-1000,1000,1000,-190,-1000,-708,-768,1000,-453,470,433,1000,1000,-112,151,991,-1000,-1000,20,-1000,1000,1000,-575,-9,410,418,-429,-694,115,-285,1000,-1000,-248,1000,-380,799,-1000,-442,918,693,-142,-926,133,1000,1000,698,460,-1000,-238,502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00928() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{256,1000,473,-686,482,71,-1000,-635,-639,129,368,-446,393,-306,-362,225,512,693,-272,-498,876,742,-729,-750,1000,14,-142,1000,-548,903,1000,-973,1000,51,-169,1000,1000,-854,190,-973,-1000,-843,-711,-1000,790,-157,-607,1000,-525,55,-185,-1000,-1000,-950,-1000,381,-116,233,25,274,-173,-682,-393,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00929() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-301,1000,-451,-400,1000,-1000,899,-513,-935,522,-555,-629,86,916,-819,1000,-253,-1000,14,102,346,112,-1000,-1000,-80,519,1000,-393,-1000,-136,-1000,319,691,1000,1000,931,-798,950,1000,479,-1000,657,1000,-108,-80,565,222,-254,1000,64,-1000,-882,330,-377,1000,252,35,899,-844,424,-1000,1000,97,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00930() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-918,-156,-566,-13,-428,-268,-818,-275,106,-677,1000,-1000,-14,381,644,244,462,-702,587,-188,245,479,-74,1000,826,476,-176,650,1000,-886,368,45,-137,-1000,1000,-228,1000,-748,880,-525,-33,-843,-182,-1000,1000,386,-428,888,-525,1000,-75,-1000,-865,-1000,-1000,376,346,-1000,-304,-121,572,907,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00931() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-264,1000,979,-692,1000,337,-960,-42,-911,593,412,-1000,62,-709,-1000,-158,688,1000,142,-498,1000,657,-919,-1000,1000,47,268,1000,-545,1000,1000,-1000,1000,10,42,1000,1000,-1000,190,-1000,-1000,-1000,-526,-1000,1000,69,-1000,1000,-570,409,386,-1000,-1000,-1000,-491,216,395,548,158,428,88,-1000,-803,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00932() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,-59,609,-380,99,621,-802,-565,204,-77,572,1000,799,-474,-783,296,806,260,-528,-873,-587,839,-341,-1000,521,-770,-146,-44,-92,1000,275,-1000,911,1000,-246,340,1000,-1000,371,-1000,-1000,-191,-120,286,331,320,117,571,-666,-367,-948,-767,-11,-633,-644,1000,-1000,-499,-320,1000,-129,-428,-757,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00933() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,587,87,-1000,344,-446,-651,-635,281,-208,1000,115,870,-306,400,273,52,801,-634,71,1000,742,-1000,-1000,1000,14,-151,470,-763,1000,969,-1000,1000,313,475,1000,1000,985,190,-630,-1000,-341,-711,465,271,-157,-458,549,11,-32,-335,-243,-1000,-1000,-793,856,-270,-160,101,538,-525,-896,-509,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00934() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{749,-952,-366,-372,208,127,-330,-433,-1000,89,154,-78,482,-12,162,433,352,291,-223,-970,410,539,-175,-533,436,-69,-292,-1000,-783,648,309,-577,1000,141,160,744,1000,440,1000,-375,122,63,-833,-1000,-200,-52,11,-62,97,132,-460,-246,-625,-809,840,484,-258,-966,148,133,-813,1000,-243,-909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00935() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{830,-1000,327,-356,1000,515,516,1000,-1000,993,-1000,601,1000,-305,-488,539,-14,605,287,-22,766,-556,-857,-308,113,-286,50,1000,-1000,334,-1000,554,171,574,383,-655,-1000,-987,819,143,563,-31,575,-107,251,-645,-220,-1000,-92,291,-65,1000,619,843,1000,-416,937,1000,-654,-659,-401,-1000,205,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00936() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-537,599,193,32,1000,-1000,941,-466,-58,-1000,259,-652,-325,1000,268,1000,718,-83,539,-218,-522,-320,1000,-613,-669,248,1000,-517,124,148,-1000,1000,-1000,581,-292,-810,-618,233,140,485,-873,940,945,-388,-664,-331,-812,363,713,663,-1000,-396,32,-367,909,271,343,341,-353,-338,-456,1000,258,46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00937() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-1000,-108,-207,396,580,-481,833,188,-166,-61,437,-1000,-798,821,562,1000,878,421,537,41,497,617,-1000,936,-80,-823,618,-490,916,-154,-946,744,-646,-240,289,-1000,710,214,-291,527,-605,125,937,-548,382,106,-350,963,431,723,52,-635,98,248,-29,-215,-9,99,169,337,558,735,-642,-312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00938() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,-1000,154,-529,1000,-134,1000,40,-320,487,-1000,880,1000,686,-552,1000,62,-1000,-1000,234,1000,1000,-1000,-1000,721,686,639,-1000,-1000,1000,-882,617,959,1000,1000,1000,-628,1000,131,-593,-516,1000,-1000,1000,-946,455,-469,51,1000,-1000,-1000,-201,1000,-611,1000,1000,-1000,1000,-528,1000,-1000,-1000,1000,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00939() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,10,323,-925,-13,682,-980,6,-151,757,-400,321,1000,-1000,-531,150,-596,-304,-1000,-341,764,1000,-1000,-1000,900,24,-651,765,-1000,1000,1000,-1000,1000,1000,1000,1000,802,1000,1000,-590,-20,681,-1000,1000,184,881,717,56,298,-717,-400,-343,-372,-705,234,1000,-798,777,-202,1000,-1000,-1000,516,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00940() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-769,1000,914,844,456,4,112,321,-400,-762,187,-122,-828,1000,-614,1000,1000,939,820,-772,-692,395,-563,-617,408,-1000,541,106,886,-1000,1000,74,234,-1000,74,744,874,1000,1000,-744,-852,705,1000,-1000,1000,309,-459,1000,-156,488,-103,220,-1000,-1000,66,312,223,-124,-431,53,1000,466,-663,-730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00941() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-412,294,-491,-493,421,-864,798,-1000,-166,-1000,1000,-470,-431,1000,1000,1000,1000,1000,686,-293,859,562,-1000,686,-80,-725,839,-381,1000,1000,-162,50,1000,-500,-624,-1000,1000,-319,-291,83,-1000,-518,1000,-1000,837,-718,-760,1000,54,1000,-1000,-469,-1000,-410,-1000,941,-830,-13,705,222,950,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00942() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-164,423,144,-135,-846,714,96,247,-497,-385,-829,-54,53,-1000,-969,273,906,801,-1000,71,595,926,-673,-648,-1000,-10,-473,-582,-763,1000,938,-1000,1000,448,191,508,527,986,442,757,1000,982,-98,375,582,1000,629,938,-569,-284,-335,-407,537,-165,366,1000,-1000,584,17,652,-286,-1000,366,-908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00943() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedDomainAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-498,1000,-557,485,344,-1,-651,-737,896,-1000,1000,115,-287,-7,-118,343,1000,107,475,-748,-1000,500,-116,379,-100,-14,-118,-168,1000,-527,1000,104,27,-739,175,-1000,421,486,-154,-1000,386,-292,1000,-931,544,277,1000,893,-786,1000,11,-243,-502,771,-1000,856,-688,-160,-20,-143,633,1000,-1000,-847}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00944() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{1000,1000,414,670,-1000,-1000,242,1000,890,-76,-1000,-806,128,1000,229,1000,1000,773,267,786,-336,-465,-1000,-241,-85,115,333,1000,-1000,-814,1000,-81,234,-843,300,-533,-221,739,-1000,-1000,32,-1000,-967,908,-1000,482,1000,126,-111,-409,-1000,720,1000,-102,-232,-36,-389,-1000,-778,133,710,-1000,-481,681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00945() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{543,-43,759,88,34,-135,-291,-121,713,-513,977,974,-31,618,-978,888,-472,132,191,-537,881,764,469,-979,178,-118,971,602,-413,382,-278,599,192,-615,-303,-748,967,-309,-978,448,-906,121,-681,-944,948,-196,669,-348,449,226,455,876,156,-561,408,903,472,-795,-408,-427,-353,54,782,-466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00946() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-632,1000,414,-1000,505,451,-487,1000,886,-745,-721,1000,-290,-1000,-221,883,-438,-1000,1000,1000,-1000,-1000,876,-691,-85,1000,333,-586,-1000,-126,-21,-227,-156,1000,-441,1000,112,562,-758,-39,1000,128,-967,-197,539,-279,-1000,1000,-191,-1000,365,-783,1000,-1000,-1000,-415,-389,321,-1000,204,-498,1000,-1000,-981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00947() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-289,-356,-141,-436,-98,263,234,174,498,5,264,233,-586,-251,225,182,-1000,-1000,1000,1000,497,983,-278,-144,-82,-1000,961,334,-458,1000,-475,1000,-1000,-328,243,-454,-340,-922,-1000,-1000,-1000,433,498,-1000,-629,-1000,63,620,117,119,467,92,-361,964,-1000,632,585,200,-1000,-31,639,400,-323,994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00948() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-508,435,-753,532,-459,599,729,908,-605,-772,-35,-50,-641,-575,-142,-299,822,299,427,1000,-1000,-1000,431,-347,672,-350,1000,-949,170,782,497,-981,-978,468,1000,243,1000,588,-282,-408,-361,-571,1000,-1000,22,-260,-816,1000,-264,-283,882,186,944,-1000,-279,-679,197,568,-1000,648,-127,54,-860,-100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00949() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-1000,-376,219,676,-511,-806,362,224,234,114,898,-503,318,905,-267,-131,228,607,145,1000,-307,-728,-645,957,310,-1000,-135,139,-294,-274,214,-46,-885,-52,562,-1000,-363,-138,-190,-754,660,-926,1000,698,-863,187,-400,467,-53,542,-977,978,-1000,-149,-646,-401,-220,-25,-572,8,-1000,482,129,969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00950() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-1000,-322,219,156,-287,889,690,-182,-1000,-537,898,-337,-789,22,935,-772,-607,1000,-1000,-1000,-9,520,250,151,511,-1000,-91,-1000,-376,1000,-1000,753,1000,64,1000,-1000,-246,544,983,1000,-1000,644,-19,240,804,370,-32,-444,672,1000,847,254,-1000,-1000,878,-301,-971,834,395,1000,-1000,482,187,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00951() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-755,220,-156,-74,-492,-363,436,792,-324,-405,-116,586,-792,-201,754,1000,-218,-827,-380,640,393,-835,-563,-869,658,670,21,-167,-652,-268,-78,731,-19,-646,596,-833,377,197,-390,-1000,408,-684,29,143,-402,242,-101,209,-601,-577,562,-275,286,-84,-581,603,387,135,-617,739,-404,-945,-610,-370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00952() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-705,356,414,5,-189,-1000,507,1000,-735,-798,-140,137,-501,-421,938,1000,1000,-132,-1000,640,-395,-1000,-548,-241,-85,1000,333,-534,192,-596,32,244,668,-278,1000,-561,-221,1000,428,-746,1000,-703,-967,803,-662,1000,-344,-129,-766,-1000,100,-774,470,-1000,-486,-36,68,695,-457,1000,-1000,-1000,-1000,681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00953() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{0,710,314,709,-1000,-805,-334,967,-329,96,-1000,-780,611,215,-273,1000,1000,328,1000,632,-347,-236,-313,-120,0,0,0,938,-125,-463,899,-405,-208,906,0,399,-121,1000,-946,-1000,1000,-1000,576,1000,-210,911,701,790,0,-356,-740,164,1000,816,-397,-1000,-317,0,-1000,-214,0,-593,-222,393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00954() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-971,220,-316,191,-489,-469,-385,910,1000,122,-504,404,615,-945,833,1000,597,-1000,931,1000,-257,-1000,743,-1000,713,670,-347,-52,346,-922,48,635,-1000,129,224,323,882,197,-578,-1000,1000,-403,249,406,20,653,-268,588,-601,-577,562,-725,286,-93,-804,-88,379,32,-1000,1000,-233,-1000,-1000,-370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00955() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-501,550,-167,952,-775,61,144,543,205,-729,-496,1000,115,-480,-558,1000,1000,-1000,1000,207,-12,-716,166,-406,608,1000,1000,1000,945,231,1000,-1000,-273,1000,759,108,882,693,-1000,-66,783,726,422,-1000,1000,-1000,322,1000,-420,-1000,-1000,-1000,1000,-547,671,-419,773,632,-1000,242,440,231,-529,663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00956() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{1000,727,-365,325,109,-671,729,455,-118,-478,-1000,-206,-647,1000,-186,-683,23,1000,1000,-748,-180,438,449,1000,-561,-1000,-687,1000,-893,1000,1000,-1000,-508,-525,392,-669,-1000,-601,-1000,822,-1000,-818,1000,-1000,841,-1000,587,1000,1000,1000,-1000,-450,1000,449,-701,503,-457,345,69,279,-347,1000,565,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00957() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-328,1000,599,551,-664,322,2,1000,75,-785,-570,291,-303,-621,190,1000,524,-235,1000,-612,-583,-101,244,-1000,285,904,1000,727,-205,291,742,-274,800,464,439,692,414,1000,-1000,345,32,594,-217,-645,804,162,1000,513,367,-903,-114,-179,1000,-933,950,-227,-333,892,-1000,453,-832,593,-599,-425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00958() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-847,-545,134,-1000,341,449,-215,306,299,-746,264,1000,48,-906,-358,211,-1000,-1000,-513,430,23,-1000,1000,542,854,1000,-708,-1000,1000,153,-495,258,922,-320,-178,671,-1000,536,453,472,518,129,498,817,539,379,-1000,1000,482,-534,511,688,1000,-1000,71,-478,111,1000,-814,359,-227,400,-628,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00959() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-753,-700,-156,-1000,568,434,360,422,-324,74,1000,865,-502,-1000,754,671,-1000,-1000,-393,1000,-16,388,-22,166,137,472,-1000,-167,839,256,-1000,1000,-19,507,-195,1000,88,-21,-137,-1000,579,348,29,-872,-740,440,-1000,302,-947,-877,562,-1000,-1000,-2,-774,603,920,135,-527,219,-711,-457,-940,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00960() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-1000,252,84,-133,308,433,-825,763,-584,-70,-431,-225,-607,-283,-191,188,-459,546,633,-1000,-304,-509,-66,31,-1000,-38,743,540,-400,-3,572,-419,653,1000,238,500,-323,1000,1000,472,-688,709,-1000,87,769,533,-514,-486,1000,922,-1000,-371,-626,449,1000,611,48,305,1000,703,78,466,-664,598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00961() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,-667,-470,463,-781,1000,-1000,-975,-615,-1000,-1000,-1000,1000,310,1000,-1000,1000,718,1000,521,-1000,-238,1000,-1000,-138,-912,1000,1000,1000,-1000,-650,-5,765,1000,-1000,791,-578,1000,-1000,212,-1000,1000,-1000,-1000,-537,-256,-465,956,568,522,848,1000,658,-1000,-499,-894,-1000,-1000,909,1000,1000,-435,-1000,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00962() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,585,-21,-554,275,-1000,269,585,-292,890,736,-375,181,-1000,-1000,436,-84,-609,-150,-988,316,-370,-229,565,1000,-679,966,815,-679,261,-510,-799,1000,-513,212,-1000,136,999,450,-399,1000,-211,817,-260,-22,-933,1000,730,-1000,759,-1000,-442,133,372,-188,-28,348,657,1000,-1000,-588,1000,13,363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00963() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,757,700,503,113,-560,297,-504,-228,-340,-1000,1000,-149,-1000,-93,-159,105,-314,11,-577,548,-566,825,-350,1000,-1000,117,1000,965,-420,322,301,1000,-617,-788,-54,-468,1000,-1000,-269,854,324,413,-1000,-399,-1000,920,993,-332,-11,325,15,177,-1000,-964,-233,-1000,-796,777,-588,1000,-340,-190,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00964() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{577,1000,544,-540,-516,402,636,1000,-318,-231,-97,1000,-1000,-1000,-668,592,-1000,988,-1000,1000,1000,91,-1000,667,-1000,-1000,1000,1000,81,-206,1000,-728,1000,-1000,254,-1000,513,84,1000,321,1000,-1,1000,-134,1000,-958,1000,-538,-409,-461,-940,669,-898,-864,1000,427,-393,693,1000,-1000,-484,184,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00965() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,-488,-556,-603,-384,-91,141,329,-344,1000,-232,-1000,-499,443,-1000,1000,-29,-938,-507,-571,-723,732,-152,1000,462,-1000,518,-696,-1000,1000,-984,-1000,189,888,1000,-1000,335,971,1000,-557,-134,-426,920,1000,-514,-180,493,344,-1000,-1000,-369,-370,219,959,-145,299,862,1000,-227,-514,-325,1000,286,29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00966() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,-626,84,70,-1000,963,636,-883,-490,166,368,-566,1000,1000,302,-426,857,988,-1000,-283,-1000,636,1000,-561,78,650,114,-289,-328,-206,-1000,645,-1000,1000,-15,499,-500,1000,-660,321,-1000,151,-1000,394,-1000,-958,1000,705,-409,-149,127,636,637,379,165,-363,-410,-724,-286,1000,626,284,-514,-294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00967() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,1000,-556,-570,-786,-562,684,585,-326,28,736,-167,-352,-1000,-725,762,-591,-618,-372,-988,593,394,-421,944,462,-1000,1000,957,-707,36,-510,850,1000,-513,343,-1000,-205,1000,620,-476,1000,-644,817,-260,-22,-904,1000,291,-1000,291,-1000,29,-577,-36,-106,124,102,1000,1000,-1000,-226,1000,-213,363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00968() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-1000,269,-106,-939,-980,-1000,-517,-131,-291,23,-1000,-524,1000,-783,-484,958,1000,85,70,1000,-926,1000,191,-294,1000,-241,112,796,400,1000,-1000,291,1000,766,-150,-1000,1000,-1000,-575,-32,-895,617,1000,667,-742,-909,-471,552,-549,455,-890,-884,1000,412,882,-302,38,-542,-400,-634,-557,-102,421,-178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00969() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-179,402,494,-523,978,304,-324,217,27,-300,124,288,754,-98,-514,148,-215,527,-330,1000,-110,-255,-308,604,-747,-1000,802,454,-1000,92,1000,258,575,-780,712,-1000,1000,-800,-215,-152,498,874,970,-25,-230,-1000,112,639,-179,64,-41,84,516,-278,180,-189,-241,513,340,-896,-588,223,-450,603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00970() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.chart.axis.AxisSpace", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,489,1000,-468,-826,259,277,-510,-804,-246,-1000,1000,181,-188,-594,328,457,-147,-920,1000,1000,-313,206,-66,1000,-1000,-374,323,1000,-30,-90,594,-83,-710,-467,-1000,480,-1000,-174,-100,825,-1000,1000,-599,-425,-723,-469,-164,-772,-346,893,1000,452,-925,-196,-1000,-867,-1000,737,-835,-763,-767,-295,158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00971() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,1000,-206,-310,637,128,135,1000,-504,206,787,70,-434,-150,-592,-179,-825,-44,-283,-1000,758,-1000,-316,1000,-89,-1000,943,1000,-1000,-286,1000,-707,1000,-183,642,-782,711,1000,819,-86,1000,-520,396,55,630,-463,1000,735,-1000,49,-959,834,-275,-86,1000,286,544,1000,1000,-1000,-795,1000,-1000,956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00972() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-1000,112,-26,-782,1000,573,438,950,-489,78,1000,371,-907,400,-925,1000,-1000,50,-873,994,-181,291,-269,769,-1000,41,649,-167,-186,947,1000,-235,403,-701,1000,-476,1000,-898,1000,849,605,1000,1000,861,1000,-520,-1000,-660,827,18,-640,-4,-405,133,1000,861,813,-8,-227,-684,-1000,-838,208,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00973() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{-1000,-443,-430,-933,234,1000,-224,236,-973,-62,450,486,-830,1000,-959,1000,-441,-477,224,1000,-915,700,587,14,1000,1000,-171,-1000,1000,1000,1000,1000,-507,526,1000,-785,-107,-1000,1000,1000,-929,911,783,1000,230,314,-1000,-1000,1000,-286,349,413,-327,158,1000,1000,-831,-940,1000,-114,-671,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00974() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{469,198,604,248,624,19,-910,-805,-316,-215,-417,38,1000,310,768,-1000,1000,-271,1000,-801,-615,-829,39,-1000,400,-775,212,870,468,-673,-735,950,-316,1000,-1000,393,-67,1000,120,-271,-955,853,-1000,-1000,-714,-705,-191,1000,510,-211,345,222,1000,-60,-576,-842,-481,-1000,737,-782,927,487,-291,-501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00975() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getFixedRangeAxisSpace():org.jfree.chart.axis.AxisSpace",
            new int[]{1000,1000,874,-169,1000,-1000,269,808,-265,-787,-1000,1000,-591,-183,-1000,-1000,-603,679,764,-595,960,-1000,633,-458,506,-1000,849,1000,960,-1000,1000,-799,1000,172,-1000,-1000,453,1000,-375,4,1000,718,121,-1000,1000,-933,1000,1000,-286,-56,-171,786,133,-1000,533,119,-801,48,1000,-259,986,529,-1000,481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00976() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{-766,-151,-614,57,218,24,-1000,-642,877,378,-447,755,1000,-320,351,-77,-741,1000,272,1000,444,-233,-1000,-740,1000,823,959,322,1000,-707,54,-435,-237,-310,696,-568,-510,-1000,-649,181,-84,-456,-865,-625,679,294,1000,1000,439,-603,836,-1000,39,-1000,445,-276,-1000,-128,-133,812,425,-810,-587,-133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00977() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{-1000,-878,-377,99,-200,394,-456,-1000,-105,378,794,118,1000,137,1000,-478,-1000,642,624,1000,-956,-444,-716,-406,1000,823,-48,559,676,-518,54,-481,-12,464,780,-589,-663,-463,737,999,-93,805,-459,-300,1000,-383,-94,1000,1000,132,-133,-42,28,-1000,1000,-1000,-1000,1000,-52,443,-164,-810,253,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00978() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{-641,-505,867,-776,-628,-589,-786,-606,941,-995,310,714,498,137,212,-481,-1000,474,-776,-104,444,946,-494,-761,479,-723,505,839,207,-125,832,-420,-12,-732,509,616,-603,-666,-663,-365,347,-375,-454,-770,836,-760,-110,782,780,-96,-176,-726,954,-822,565,-503,-808,55,432,193,690,590,-867,-88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00979() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{1000,-400,649,1000,-1000,304,383,-399,-560,-389,-658,-324,-1000,1000,-876,-1000,-420,-1000,-144,-1000,-548,528,1000,561,-1000,-276,-1000,645,-989,483,-991,958,1000,75,-70,684,1000,1000,480,-135,-415,314,1000,491,-1000,483,-1000,-968,-1000,206,-1000,796,571,-748,256,503,209,544,509,-1000,10,867,-287,487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00980() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{856,-1000,284,1000,-1000,1000,71,-862,-907,-347,-855,44,-360,1000,47,-1000,-168,-1000,1000,786,-1000,-647,1000,212,-1000,-1000,-1000,190,-1000,-189,-1000,494,926,569,172,-664,532,1000,1000,-644,-1000,644,802,1000,-975,1000,-279,1000,-433,275,-351,1000,-490,-1000,1000,-705,-201,1000,294,251,-320,-1000,276,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00981() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{-1000,-66,-891,-574,378,580,-1000,-1000,1000,-57,809,492,1000,-925,-73,289,-1000,1000,1000,1000,76,-1000,-1000,873,1000,1000,1000,464,1000,-921,666,-1000,-485,106,1000,-1000,-1000,-1000,190,1000,-85,626,-487,-1000,1000,-674,234,1000,1000,905,931,-1000,-358,-1000,1000,-1000,-1000,1000,-325,822,157,-1000,77,873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00982() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{393,1000,9,-60,217,108,-327,-606,-878,-667,-436,-1000,893,675,209,-306,1000,474,-369,79,508,1000,-280,-761,560,249,-811,-1000,962,953,514,-1000,1000,-732,-215,-568,936,-629,462,748,137,672,-454,-1000,-364,-1000,-832,-1000,1000,443,260,745,-436,411,-1000,-328,-1000,-157,-963,-1000,190,-686,-794,-88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00983() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{-27,22,9,-911,598,-461,-170,387,-1000,481,-1000,-1000,1000,675,-359,176,1000,1000,-1000,994,508,92,-280,-761,653,466,-294,-368,1000,-376,1000,-1000,930,-36,-304,-652,-435,-629,-279,748,137,76,-839,-1000,474,-280,1000,-70,329,443,568,-426,93,1000,-765,-1000,-1000,-443,165,-31,1000,502,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00984() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{340,-878,-251,237,-110,268,1000,-831,-1000,717,268,-1000,-395,-699,34,440,494,-1000,532,-490,-1000,-444,1000,416,-103,-355,-1000,559,-1000,257,1000,-792,938,1000,-748,-1000,-868,-196,1000,999,-1000,1000,1000,-298,599,-232,-1000,-641,754,997,-133,1000,136,1000,23,-984,755,1000,436,-856,-625,907,1000,862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00985() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{-537,253,-564,-251,534,-717,156,-20,-362,360,287,-524,541,1000,217,235,175,-160,-558,-622,656,503,-647,494,1000,-456,-436,44,973,-177,689,-532,1000,-747,-377,101,0,79,107,616,43,270,276,489,93,-1000,-982,-1000,294,117,530,-315,401,592,-1000,280,-570,-410,-658,-455,234,878,-856,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00986() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{-647,-595,-30,127,234,241,296,-563,-1000,-190,582,-1000,389,-13,32,-484,574,139,-637,864,-58,299,203,-1000,225,438,-570,389,1000,141,1000,-1000,438,524,369,-1000,-397,-816,861,656,48,354,340,-1000,1000,-236,-175,100,1000,-437,-228,-233,-328,922,-653,-875,-690,715,592,-956,-247,-141,-381,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00987() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{-1000,-446,369,-74,282,631,-254,567,877,378,850,420,1000,-339,1000,-630,-787,536,624,1000,-94,-847,-925,-828,1000,1000,959,798,1000,-710,1000,-1000,-258,332,-374,-589,-1000,-30,737,561,-26,654,-225,-738,1000,312,-191,1000,185,352,671,-35,-13,-1000,984,701,-445,1000,-340,730,-164,-810,253,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00988() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{1000,-152,308,-110,-533,-941,71,927,-1000,-347,-456,44,-360,1000,-827,-472,1000,-161,-1000,-1000,-355,1000,995,336,-1000,461,-1000,320,-1000,567,808,-555,1000,40,-887,225,663,1000,564,-644,-571,-51,328,-900,-590,-643,-402,-1000,408,231,-890,1000,513,-1000,1000,-187,13,-358,670,-1000,499,1000,-593,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00989() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{1000,1000,279,757,-338,-50,-1000,892,-86,-347,-1000,-583,-220,1000,-1000,-369,1000,474,-681,-426,1000,935,-109,282,-848,-1000,-267,-1000,1000,1000,353,-686,933,-1000,-384,702,1000,-1000,-869,-293,788,644,877,-1000,-1000,15,-234,-1000,156,-266,-374,689,39,25,-1000,800,-708,-1000,-822,-1000,1000,-1000,276,784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00990() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{-79,-175,-232,-776,138,85,-136,-12,-715,-1000,228,-952,98,137,-75,-481,138,653,-1000,728,224,1000,171,-1000,-542,871,505,95,836,-125,-542,-699,-12,418,799,-603,1000,530,949,56,-52,398,-454,-1000,181,291,-63,-692,841,-1000,-267,-653,-34,-838,-1000,100,-1000,55,555,-632,258,-225,-1000,-88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00991() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getIndexOf(org.jfree.chart.renderer.xy.XYItemRenderer):int",
            new int[]{102,141,-300,122,-222,697,1000,-373,-675,-568,-361,-736,305,831,-468,-1000,-286,-31,-497,306,112,380,208,-374,-81,1000,-922,1000,583,744,-1000,305,969,-79,581,67,-228,188,440,1000,496,616,-12,258,-386,113,-512,487,704,-711,-285,783,-105,-760,-286,277,-1000,329,-509,-1000,-654,532,-687,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00992() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{186,-841,-866,769,490,1000,-303,319,112,-278,1000,-234,569,400,-1000,1000,789,-175,50,-53,-1000,-768,-1000,707,1000,-462,-270,-1000,-1000,-1000,496,968,-1000,64,-746,1000,1000,-219,839,-689,-1000,-818,1000,323,-148,522,125,1000,515,-309,-100,-849,735,1000,1000,-373,1000,-1000,-419,-400,-876,-596,340,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00993() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-1000,1000,814,110,5,-1000,-691,910,-275,1000,-844,-736,-248,-1000,-857,566,-745,1000,1000,642,704,-346,680,-1000,-1000,424,1000,142,1000,-637,418,1000,1000,124,-523,-1000,-574,1000,-1000,-971,-1000,582,1000,18,-732,714,393,922,1000,192,-475,-1000,-361,85,216,481,-624,860,1000,873,569,306,333,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00994() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{663,631,-203,-853,-840,1000,-581,-943,729,-105,-428,-276,-283,1000,1000,-163,-921,1000,-682,150,-1000,745,-485,-270,-75,-58,-960,-892,47,-630,-796,-1000,1000,-511,45,-170,395,1,-982,1000,280,-588,254,-240,1000,28,-709,363,-155,1000,-1000,861,-243,927,-913,957,299,-98,-776,-394,216,458,-424,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00995() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-254,-804,-206,887,704,887,319,1000,-117,105,-253,-1000,1000,-1000,-1000,1000,1000,-1000,436,588,-946,-1000,-646,-1000,1000,-716,1000,-247,-1000,-599,-740,1000,-1000,595,-547,1000,-38,-816,1000,-1000,-1000,170,1000,879,312,106,1000,621,-1000,-1000,1000,306,1000,120,1000,640,1000,-1000,971,394,-444,-355,-943,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00996() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-397,-971,-1000,411,-406,1000,-1000,1000,-103,946,1000,-79,1000,-1000,-1000,-293,1000,-913,-510,838,-1000,-19,-1000,-321,1000,-126,-191,-343,-1000,-1000,-593,-52,-594,1000,895,1000,882,-968,576,-1000,-946,488,1000,20,294,28,1000,707,-1000,-1000,1000,-476,360,1000,289,-810,1000,-1000,107,1000,212,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00997() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{1000,1000,189,-1000,-490,-816,929,-939,1000,-362,-1000,-22,-1000,1000,1000,-441,-1000,1000,1000,243,-332,-1000,1000,1000,-1000,510,939,794,1000,1000,277,620,541,-1000,-1000,-221,-1000,944,183,1000,1000,-1000,-567,-322,611,-929,-1000,911,118,1000,-1000,1000,556,-1000,-1000,885,-1000,1000,726,-1000,-1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00998() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{1000,-106,-206,-823,246,1000,378,294,433,-1000,-253,187,77,1000,-70,506,254,302,-581,308,-1000,-312,-132,3,-39,29,-278,-591,-250,-304,-753,-594,-730,-662,-1000,709,-304,185,232,276,-323,-1000,374,400,312,-654,-587,1000,-321,1000,-290,167,513,47,59,640,957,-683,-284,-1000,-852,1000,-943,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00999() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-773,708,814,802,617,-1000,265,594,-390,1000,-522,-1000,26,-499,-866,566,-140,1000,1000,582,807,-1000,223,-747,-653,-26,1000,-465,1000,-142,38,733,399,475,-523,-859,-956,1000,-341,695,507,1000,745,-17,-1000,-41,701,1000,333,192,-327,-525,36,-405,-103,345,-560,488,1000,1000,-90,688,-629,-415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01000() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-32,-219,185,-1000,-316,-1000,473,796,-772,-696,-440,269,-1000,926,-510,-672,613,392,229,633,-670,-740,471,1000,243,-777,-262,1000,1000,453,961,-933,-1000,653,-1000,320,-677,-798,750,-508,-484,914,26,591,-76,-757,455,182,221,226,531,-455,-201,-448,-479,-502,-8,-1000,-59,-424,-967,-513,1000,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01001() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{308,-816,-636,-549,-744,648,-617,243,-818,-925,1000,385,-85,-166,682,-1000,357,-853,-1000,148,-936,1000,-412,590,85,569,-1000,-685,-330,-564,-166,-1000,447,671,967,-160,-325,-780,648,1000,-485,-103,358,515,1000,-234,-631,794,-1,910,429,-582,599,480,-1000,1000,1000,-576,-246,452,-141,-1000,1000,117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01002() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{453,567,439,325,414,1000,358,86,-676,-301,758,-1000,36,-339,251,-532,-164,-163,24,-961,556,36,-166,-1000,20,-480,449,-254,422,-611,-827,-597,372,-1000,-900,-346,-1000,430,-946,585,520,-91,221,566,84,-1000,-711,1000,-588,-737,-1000,1000,1000,-716,639,779,-334,-437,718,43,-728,1000,-1000,167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01003() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-825,943,882,522,137,-776,634,-205,-273,968,-217,-362,-822,-607,26,-412,-785,839,723,385,826,-45,566,-388,-485,-508,973,250,602,646,185,805,632,581,133,-853,-956,548,-341,752,839,841,-93,-350,-789,-280,379,-940,837,384,-560,419,-156,-895,912,317,-652,701,805,722,-109,688,-823,772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01004() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{28,707,393,-342,-298,1000,929,-21,-863,-1000,352,-187,-229,212,616,-1000,-138,-638,-1000,-629,-616,-484,-759,1000,194,286,-738,-523,-879,1000,-595,-1000,0,66,-191,192,-629,-716,1000,-1000,-483,-1000,544,423,502,-890,-1000,1000,1000,1000,-337,-106,1000,78,-1000,296,-355,-325,-316,-1000,-267,-60,207,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01005() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-129,-856,-872,-214,1000,-89,-742,-114,-19,-987,980,470,-4,-977,-62,387,1000,-778,1000,1000,-466,102,1000,630,-230,453,892,119,1000,72,222,399,-122,1000,-294,375,-377,1000,-1000,488,-666,115,1000,-1000,-691,177,-898,1000,995,-539,1000,-969,-1000,1000,438,195,461,120,83,-849,273,-227,1000,-65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01006() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{42,1000,-636,-509,49,357,-330,243,-1000,-1000,-428,385,-475,-1000,1000,-1000,357,-1000,-1000,20,-483,1000,1000,687,85,1000,-1000,-685,197,-564,-137,-1000,272,1000,1000,-160,-325,219,460,1000,-403,-487,219,-245,399,-164,-922,794,731,362,526,-1000,-35,553,-1000,1000,1000,1000,-246,166,-298,570,1000,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE01007() {
        org.junit.Assert.assertEquals("STATE:org.jfree.chart.LegendItemCollection|getItemCount=22:java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.chart.plot.XYPlot", "org.jfree.chart.plot.XYPlot", "getLegendItems():org.jfree.chart.LegendItemCollection",
            new int[]{-549,-349,-990,-116,418,-30,-577,892,-676,892,758,323,36,-699,-348,546,625,735,24,967,452,601,546,582,295,459,-193,543,393,-898,458,212,777,894,701,-346,-640,594,-946,78,-174,707,483,-663,-392,311,726,275,-48,-737,911,-542,-593,696,406,589,936,-195,272,824,-698,64,627,-927}));
    }
}
