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
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "compareTo(java.lang.Object):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "compareTo(java.lang.Object):int",
            new int[]{526,-321,1000,-120,-148,1000,-823,103,-234,640,-876,719,-97,154,729,-697,-153,-1000,-222,368,-199,-1000,1000,-834,-503,168,-856,-365,-123,956,1000,1000,957,402,-714,-924,76,-503,1000,1000,74,300,-214,-725,160,-610,552,-1000,-1000,919,712,832,263,43,119,-741,112,129,359,-925,1000,-304,1000,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "compareTo(java.lang.Object):int",
            new int[]{-94,722,-278,-307,587,282,-877,-505,972,-405,-438,-268,-247,-728,76,-515,177,-373,-816,547,637,-348,276,-404,507,-531,369,829,-163,-268,-392,341,-785,-164,-863,-283,-636,-734,-264,-464,840,921,965,-232,590,-171,474,-552,60,-689,-410,374,-950,-623,350,-865,566,872,718,247,565,-263,911,860}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "compareTo(java.lang.Object):int",
            new int[]{106,-843,138,-37,-24,954,-1000,-344,416,23,-214,-440,-88,-952,-376,-656,435,-61,-1000,390,479,-257,-253,289,-728,-148,-1000,1000,1000,-369,-436,-927,-14,-54,-690,881,456,-823,-796,-189,-598,1000,-761,-872,-255,-706,88,756,-702,-1000,-304,-802,-282,16,694,-675,-998,307,1000,1000,895,-781,383,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "compareTo(java.lang.Object):int",
            new int[]{-73,-658,176,-1000,1000,1000,-415,448,-45,217,-722,-424,-240,-868,275,-1000,252,492,-942,553,661,-1000,1000,-821,-39,12,101,368,1000,726,243,534,-517,826,-1000,-477,-400,-1000,-729,297,-326,482,-30,-24,-744,322,20,594,-947,-527,-335,272,302,389,187,-1000,128,748,838,-429,35,149,-573,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "equals(java.lang.Object):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "equals(java.lang.Object):boolean",
            new int[]{-620,45,-844,306,438,-507,814,494,11,-942,-784,111,-61,629,-297,-891,-524,240,-990,-921,159,-799,-163,-553,-772,491,-848,-505,-384,321,-820,382,342,379,-966,357,-179,-799,-243,-301,86,-631,190,-452,213,769,-472,796,313,-956,668,-567,-578,531,560,-624,-704,474,-381,669,-304,119,-28,192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "equals(java.lang.Object):boolean",
            new int[]{-979,356,-789,115,-711,242,-897,338,361,941,-213,403,-795,224,-234,-657,-518,308,684,-960,640,-679,690,-263,-653,67,985,937,-641,-183,101,-787,753,-515,451,463,-863,489,-478,483,-70,814,-278,382,72,399,320,330,-337,-223,205,399,911,-868,-762,846,-472,-592,870,100,231,897,-608,898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "equals(java.lang.Object):boolean",
            new int[]{-625,-815,25,238,-1000,227,-202,-184,-884,88,-1000,-1000,482,-655,-1000,45,1000,444,656,185,1000,1000,495,1000,-1000,402,-509,-382,377,1000,736,1000,108,254,-1000,148,-1000,-133,123,99,-1000,-1000,47,289,21,-829,-1000,-74,-61,1000,-1000,-1000,-1000,916,-400,1000,-1000,616,1000,-1000,-1000,423,1000,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "equals(java.lang.Object):boolean",
            new int[]{427,879,-664,891,-992,-735,377,-384,647,-372,-156,648,-697,938,500,590,-776,53,783,843,-399,-696,267,91,649,-439,-208,671,772,-134,-85,-824,368,322,323,686,458,-108,-346,-673,-167,972,-151,-458,681,184,-440,398,-234,-316,-566,-465,159,-515,141,-49,-771,760,-85,-272,162,931,60,-8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "equals(java.lang.Object):boolean",
            new int[]{610,-574,403,-49,-731,471,-27,-321,-884,-26,-659,-535,880,-344,-13,-981,-119,931,839,581,-181,-276,682,-900,292,-585,-479,13,904,391,206,897,-370,-667,-404,-726,815,-746,393,-804,630,600,399,-117,-883,234,-605,657,58,-970,-890,-321,799,-39,803,-414,-80,-929,-110,-955,-836,-476,-275,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getFirstMillisecond(java.util.Calendar):long",
            new int[]{-867,-347,-364,1000,-1000,598,-1000,-1000,-641,-733,1000,861,64,90,-342,1000,-93,778,-726,-53,-920,-716,-1000,-1000,-1000,-766,233,-154,-1000,-1000,519,1000,679,126,114,-122,589,-865,36,19,929,-904,-1000,1000,-520,-717,190,3,767,-1000,-1000,-373,-351,1000,194,-797,-667,883,-65,-325,-123,1000,-1000,832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getFirstMillisecond(java.util.Calendar):long",
            new int[]{776,63,848,554,548,-253,281,-939,41,-513,-441,650,92,-422,-532,346,-33,953,103,-838,56,-185,-565,788,39,269,849,-227,-322,-24,-445,310,-575,731,210,330,-568,689,879,45,191,-512,-465,-712,78,-69,722,70,336,81,-304,-420,674,-381,-605,576,585,151,-78,542,734,791,886,318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getFirstMillisecond(java.util.Calendar):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getFirstMillisecond(java.util.Calendar):long",
            new int[]{-679,-232,-106,981,183,94,-229,698,606,385,674,983,-718,812,-842,332,678,874,479,579,-816,160,930,-543,851,-369,-151,-146,277,-941,97,22,-771,214,-750,-389,1,-853,-462,-179,149,512,652,184,268,212,-78,-479,762,-102,-885,511,-813,-185,93,-80,680,-641,926,59,227,-705,-282,-684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getFirstMillisecond(java.util.Calendar):long",
            new int[]{-609,8,181,-325,-620,-726,-328,-911,757,427,747,751,559,-711,-232,499,-413,477,-706,-276,-984,625,427,-790,-495,78,369,-730,-379,-11,-755,-312,-583,-378,-336,-417,-601,500,336,752,804,-452,-926,229,58,798,976,889,662,418,255,754,-693,820,-782,306,-10,452,-754,651,131,477,264,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getFirstMillisecond(java.util.Calendar):long",
            new int[]{-1000,39,994,335,1000,676,-728,-695,27,232,61,532,-1000,35,-899,217,1000,638,1000,-1000,-1000,-91,1000,-1000,-691,833,972,-1000,1000,85,-524,-337,-86,660,219,375,-88,-232,273,-1000,-1000,266,-747,438,79,147,-400,326,-950,65,-571,336,-688,-549,295,882,758,964,296,341,-400,-151,-509,120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getLastMillisecond(java.util.Calendar):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getLastMillisecond(java.util.Calendar):long",
            new int[]{-461,-921,156,309,360,-495,-843,-398,30,-418,-474,91,-237,-603,-159,163,569,676,474,-436,807,540,-473,-801,177,894,-516,-443,-333,-789,237,-397,755,-206,-859,-162,-88,-535,-147,-367,374,83,847,-89,12,288,-314,-210,166,420,433,-119,-671,-277,877,-383,-309,-407,-331,-811,-285,907,903,459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getLastMillisecond(java.util.Calendar):long",
            new int[]{1000,-1000,-858,-860,0,539,125,-373,763,-524,1000,1000,-1000,383,-225,-512,-99,1000,-103,-712,-674,1000,-851,-1000,-355,932,-1000,-998,-107,273,-374,-480,-193,56,-291,422,56,551,11,304,-564,-1000,67,1000,1000,205,318,-461,-866,-1000,-809,622,136,72,-169,-1000,626,543,-502,-331,303,-318,-330,-346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getLastMillisecond(java.util.Calendar):long",
            new int[]{783,512,92,-969,674,-392,-344,659,471,819,-21,-24,727,452,-654,-159,-2,-952,-430,-940,-957,-136,-516,501,497,132,141,418,454,753,259,720,93,-647,-553,-268,309,563,-174,-541,153,-232,-166,277,820,610,-850,-442,896,77,-994,781,-354,142,-561,403,-779,-391,439,907,-174,59,-575,312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getLastMillisecond(java.util.Calendar):long",
            new int[]{686,663,558,-309,404,-723,-1000,240,254,134,-1000,-264,677,-222,-1000,126,-1000,-90,-1000,-1000,-441,603,-1000,-608,342,-83,1000,302,-660,106,280,375,475,-914,-1000,-1000,475,-650,352,-138,-897,133,340,217,794,631,-1000,-549,222,1000,-658,806,23,1000,-1000,541,-447,382,-261,917,-418,101,-87,523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Long:MTA0MzA1", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getSerialIndex():long",
            new int[]{-840,253,-731,-91,-322,-368,416,-25,-131,97,-351,-1000,147,242,-301,-337,-62,1000,62,62,1000,-579,38,-665,-754,104,1000,798,-1000,-281,-391,278,142,-441,-1000,1000,825,304,-1000,1000,710,678,-110,15,750,713,-1000,-148,-100,-407,469,994,-410,60,152,997,-1000,-155,21,204,1000,260,-787,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Long:MTA3Mzc4", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getSerialIndex():long",
            new int[]{-283,609,-391,444,96,-359,1000,-316,20,-1000,713,818,-1000,270,-568,-472,-132,60,469,544,-96,-733,232,285,-39,-375,1000,29,-1000,-998,-657,-835,203,-1000,-996,932,-1000,-388,-959,478,396,-313,-6,-40,243,-243,-542,-94,49,286,-603,1000,-316,555,-1000,583,64,372,-142,1000,71,356,442,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Long:MTA3NDE3", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getSerialIndex():long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Long:LTUz", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getSerialIndex():long",
            new int[]{-389,698,991,1000,-262,170,-775,-206,597,-66,1000,743,1000,763,-282,388,-345,581,803,-932,636,253,-749,356,-1000,-346,1000,312,-276,-231,204,-1000,-219,-668,-1000,991,-141,789,-609,1000,210,-821,-551,-163,397,-155,1000,491,-22,507,507,61,-209,-391,-233,-326,578,-1000,-596,-282,-225,-418,-765,502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Long:MTA0NDY0", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getSerialIndex():long",
            new int[]{-834,339,1000,685,846,697,1000,-106,727,-1000,476,-1000,-1000,-1000,-1000,-1000,-838,648,492,1000,-918,190,971,593,-528,1000,1000,-1000,44,-1000,-501,297,-948,-1000,1000,-842,459,1000,474,944,-1000,-49,-1000,-1000,268,-190,-1000,497,-490,1000,-1000,-1000,-225,1000,-1000,1000,114,652,-272,-186,1000,-7,1000,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getSerialIndex():long",
            new int[]{-186,-166,-659,-424,128,405,781,229,-428,-918,-181,225,-1000,23,555,381,-239,-632,384,226,973,-615,784,-358,311,-707,429,541,-679,-419,-60,-921,305,-902,536,1000,-1000,-718,291,967,-523,-763,-600,-465,461,-629,-440,181,304,-620,-714,1000,1000,-102,-1000,928,-858,1000,-148,1000,-199,-283,-191,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getWeek():int",
            new int[]{-82,745,1,516,27,-348,1000,289,673,-365,915,-231,762,-352,215,-905,-135,-556,-670,781,627,-503,-131,6,236,813,-1000,-271,-1000,-1000,926,-199,-989,-148,-871,428,1000,-1000,511,-198,-474,-857,465,-119,1000,311,-1000,493,219,758,747,-1000,175,341,-265,519,540,817,-128,417,109,38,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getWeek():int",
            new int[]{-207,-501,911,-680,1000,754,-220,1000,368,157,88,-15,-755,-762,110,-613,1000,286,-344,1000,-439,0,899,494,685,-1000,140,876,116,667,-609,0,-213,0,1000,1000,14,-156,0,-461,-431,-674,0,-184,429,837,659,45,238,0,0,-122,-1000,365,-266,0,870,682,0,1000,-217,505,212,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mzk=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getWeek():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getWeek():int",
            new int[]{-347,-478,-416,821,214,967,950,323,-526,-443,-327,306,492,-742,593,-541,646,-305,-47,-105,966,-532,666,-101,-614,546,673,72,-739,-831,51,920,-196,-874,-652,-879,-144,-171,939,51,814,159,-869,-524,472,-302,-812,-245,-968,-311,503,-607,-400,817,727,-692,120,-265,-168,818,-115,-845,-208,-997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getWeek():int",
            new int[]{-488,476,194,-69,-743,-790,30,-87,471,-686,883,-536,500,912,655,395,512,-400,-617,-315,-692,-517,-363,-354,-676,405,-278,-459,64,-325,-782,-419,-544,-947,-777,-595,471,273,943,-491,655,951,255,-610,242,-611,275,648,-47,-529,-960,732,692,-379,987,-402,-54,640,-918,-868,-74,88,-848,-112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getWeek():int",
            new int[]{831,-969,986,584,-1000,-717,459,-613,1000,-293,327,90,1000,1000,1000,-803,-523,887,-553,-288,-1000,-1000,368,-1000,1000,410,-628,-1000,-333,-341,-818,589,1000,109,-490,954,-337,128,269,-973,334,-1000,542,-113,-142,-432,-526,1000,357,201,1000,-320,-867,-1000,589,1000,15,-331,995,308,-396,-752,245,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Year", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getYear():org.jfree.data.time.Year",
            new int[]{-15,-707,1,-529,951,80,307,-57,906,-7,245,317,-18,-665,-569,-597,-701,-905,930,461,-717,-214,-186,-383,988,-300,229,437,-462,-390,732,-444,85,321,-798,-842,-954,439,314,-390,818,177,229,-463,629,-860,963,-735,-246,-385,680,681,-61,108,96,316,-474,596,-906,704,531,-692,-302,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Year", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getYear():org.jfree.data.time.Year",
            new int[]{-659,-381,-852,533,630,-939,-982,-530,741,-228,832,249,424,-763,780,-422,813,730,945,-450,41,505,-130,-829,848,142,-387,142,-4,-419,239,-207,644,694,-684,-77,120,-767,571,807,44,454,634,642,-845,818,-505,321,-194,770,-818,-448,673,-210,47,-908,912,685,975,-866,704,-630,893,-282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Year", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getYear():org.jfree.data.time.Year",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getYear():org.jfree.data.time.Year",
            new int[]{777,-370,749,787,645,-537,-665,-402,968,216,561,384,140,413,-332,-904,-471,407,-56,879,-191,-47,-886,-338,954,-111,-955,-648,-468,724,-471,-179,-778,-655,-494,767,-136,946,-276,-991,743,838,-469,-174,-665,-191,-702,502,-757,-621,739,447,711,-547,347,955,-262,627,94,937,-418,750,546,855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Year", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getYear():org.jfree.data.time.Year",
            new int[]{1000,93,-447,956,-934,-1000,-223,293,-1000,-181,-672,1000,122,-40,159,1000,1000,1000,-336,-1000,357,-507,1000,-513,-1000,791,230,-127,273,1000,-187,338,-1000,-1000,1000,-449,1000,-982,-797,1000,171,548,165,-43,-704,1000,918,1000,-1000,-399,64,450,-662,-215,-1000,600,561,356,477,-1000,-634,-999,669,-879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getYear():org.jfree.data.time.Year",
            new int[]{-270,-1000,-757,-1000,-125,36,386,447,-566,-154,-230,1000,900,-291,24,-275,-570,-354,-370,1000,-1000,-640,-868,-552,597,228,758,1000,-55,323,-296,-244,586,-1000,-178,835,897,1000,796,-378,95,1000,-296,225,817,-1000,300,-141,68,-1000,1000,431,858,-10,-495,184,-816,-724,-385,323,-688,-528,372,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getYearValue():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getYearValue():int",
            new int[]{96,-231,-936,-610,-1000,184,959,645,567,155,228,-848,-1000,-373,34,582,-47,114,527,-470,650,-332,-579,-296,590,-1000,-65,117,828,-404,-164,-487,1000,-428,1000,-136,1000,277,261,-417,-37,-343,-218,678,1000,-626,61,900,-210,-187,-661,-697,78,444,-158,-1000,-135,-78,-562,-27,-663,-850,-1000,-359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getYearValue():int",
            new int[]{91,968,-341,352,1000,-36,315,-2,683,-633,-1000,-418,1000,741,-34,-534,159,-1000,-90,-1000,244,1000,-1000,-1000,-673,1000,729,929,-818,527,114,478,-1000,803,-829,779,-525,-150,221,170,-135,84,1000,-197,938,216,-68,-244,-236,-1000,715,675,898,-1000,-893,1000,104,-298,-273,161,471,179,402,427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getYearValue():int",
            new int[]{-669,692,120,-613,490,-93,195,132,-146,732,743,763,-750,617,-276,309,445,225,892,33,-61,898,-311,374,-595,-524,230,356,483,-453,-530,-448,515,-526,-34,-358,999,590,186,300,-989,-127,-740,715,-331,82,-749,-621,828,853,562,955,-372,591,940,16,248,-902,-332,-762,-700,857,18,222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTk3MQ==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "getYearValue():int",
            new int[]{860,207,8,-568,-202,649,-1000,1000,835,691,-287,684,1000,1000,-1000,-112,1000,511,264,-1000,123,-1000,-592,-831,1000,-556,-479,1000,-907,-1000,-906,1000,-629,1000,899,255,73,-581,1000,-164,151,-367,751,1000,465,483,-5,-66,-1000,346,727,533,-231,-1000,263,-137,-106,-790,189,-1000,-413,-905,930,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Week", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "next():org.jfree.data.time.RegularTimePeriod",
            new int[]{-604,-561,488,-571,-842,-133,-549,-294,772,576,-845,635,752,-603,-658,87,-937,-396,626,390,899,40,-820,-381,-769,923,7,-260,-45,-856,321,419,-511,101,51,-740,203,355,648,322,217,752,-35,852,774,-970,254,-654,670,376,66,298,-997,-49,-785,289,918,933,861,423,-65,-677,243,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Week", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "next():org.jfree.data.time.RegularTimePeriod",
            new int[]{-1000,883,-716,654,-796,1000,506,-300,-59,958,-313,-1000,1000,-47,577,-1000,-686,698,74,-1000,-1000,-138,-260,-347,-657,-790,-560,-733,1000,145,196,-1000,-537,-79,301,-1000,1000,1000,-1000,884,-747,-609,-403,-290,427,-24,1000,117,421,-23,-541,-1000,-295,1000,-1000,846,1000,1000,616,1000,1000,-466,453,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Week", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "next():org.jfree.data.time.RegularTimePeriod",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Week", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "next():org.jfree.data.time.RegularTimePeriod",
            new int[]{237,363,621,-697,1000,-201,-877,-1000,-259,-890,300,272,-203,-1000,-1000,-495,208,235,764,509,332,299,105,-823,963,798,-148,-1000,-777,1000,1000,95,476,523,1000,300,688,-596,-1000,-737,784,696,-430,1000,651,-1000,422,286,873,603,1000,464,-1000,-493,-702,742,-469,570,-724,1000,914,-75,288,-828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.time.TimePeriodFormatException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "parseWeek(java.lang.String):org.jfree.data.time.Week",
            new int[]{325,-367,1000,526,-1000,-588,290,231,-591,1000,-574,-69,-180,-1000,-1000,1000,641,-449,2,1000,1000,-1000,805,50,-977,-931,-371,-1000,110,371,736,1000,723,1000,188,1000,293,-541,-593,-183,-685,-1000,881,601,888,993,-307,702,-33,-866,1000,347,-1000,-865,-145,998,-625,-4,1000,-991,1000,1000,484,514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.time.TimePeriodFormatException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "parseWeek(java.lang.String):org.jfree.data.time.Week",
            new int[]{-630,871,-807,342,825,-647,-281,648,818,530,877,622,625,-507,-141,-185,-540,-601,731,250,-75,96,729,-600,-30,140,-129,422,468,751,-127,-477,-814,-620,63,665,132,712,-782,339,50,183,-907,-411,-805,-615,-259,-884,-602,82,726,-138,929,6,-240,244,689,-940,-566,-67,-151,-862,-723,920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "parseWeek(java.lang.String):org.jfree.data.time.Week",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:org.jfree.data.time.TimePeriodFormatException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "parseWeek(java.lang.String):org.jfree.data.time.Week",
            new int[]{-41,-923,50,-873,381,-838,2,-667,918,511,203,803,354,139,-141,775,-135,950,-758,-631,534,982,-75,-554,-631,-809,540,-132,843,-219,975,425,700,152,-351,-878,920,-228,-277,952,-425,453,949,-193,629,865,211,-231,636,-566,-236,633,-218,242,-311,-733,-261,-13,117,-861,383,649,-568,-812}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "peg(java.util.Calendar):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "peg(java.util.Calendar):void",
            new int[]{-546,-663,-615,-862,168,898,-884,959,608,99,193,662,-680,61,-937,-619,723,-822,-737,436,659,44,-994,-685,732,-964,-397,-330,-932,-276,-434,-669,-97,-42,960,-682,-351,224,-108,-186,950,-342,-81,162,645,-42,-440,-902,525,371,-851,609,-686,912,-225,113,377,29,238,948,343,-983,580,571}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "peg(java.util.Calendar):void",
            new int[]{613,-448,-176,206,-20,-356,368,-349,-148,67,412,255,-942,486,-785,-668,-988,-923,-371,-653,554,838,811,265,984,640,418,-23,896,-356,50,-744,-879,353,-543,624,-618,-652,344,-430,112,-489,-535,-401,-715,-569,-827,-240,-913,609,-454,488,790,-962,-176,894,278,824,-14,-84,-660,-408,-8,-810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "peg(java.util.Calendar):void",
            new int[]{-234,482,895,793,927,-842,957,340,379,-819,-705,48,-864,461,-98,315,351,509,142,-47,989,785,-230,611,-569,-475,-498,-913,801,-670,119,674,525,508,792,-375,-101,868,646,-170,-685,-511,451,-95,-828,-170,-214,12,-500,487,996,943,829,-137,851,-736,964,109,-73,-336,938,-253,429,-990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "peg(java.util.Calendar):void",
            new int[]{-444,-567,-867,-1000,82,-446,274,-470,226,132,714,516,-864,1000,329,-1000,-91,365,-98,621,-756,3,1000,60,1000,-527,-381,-642,167,187,-623,-94,1000,-423,-1000,132,251,-502,-452,540,1000,-593,144,-765,71,262,-209,185,-653,1000,540,-228,-151,-261,1000,418,247,-223,731,-1000,-787,-239,-261,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Week", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "previous():org.jfree.data.time.RegularTimePeriod",
            new int[]{-1000,-207,1000,438,38,713,689,-1000,361,-136,715,-268,1000,-104,-523,1000,228,952,419,537,-542,189,-930,1000,-543,-658,-1000,176,-753,386,-629,138,564,-574,-1000,-1000,-897,1000,-1000,-1000,352,217,-743,886,-618,-1000,958,-236,-272,297,-1000,797,-1000,-274,-343,481,849,468,-630,435,259,790,921,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Week", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "previous():org.jfree.data.time.RegularTimePeriod",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.jfree.data.time.Week", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "previous():org.jfree.data.time.RegularTimePeriod",
            new int[]{-1000,-141,110,-227,0,1000,-539,317,1000,-12,-1000,-951,1000,292,-537,-1000,436,-328,-575,488,382,1000,590,1000,-1000,-591,-539,689,-1000,-452,132,-108,101,202,-400,-1000,-72,-320,-580,-143,1000,-751,815,19,-1000,-1000,481,734,-1000,-1000,-596,727,-1000,844,-1000,962,1000,-34,1000,385,-125,-315,-346,-17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "previous():org.jfree.data.time.RegularTimePeriod",
            new int[]{-152,284,171,-755,-906,682,189,-70,640,236,-349,-155,-205,1000,-867,-94,-537,613,-1000,274,-1000,-90,91,598,-430,415,-197,-1000,-47,-270,-872,-1000,1000,139,440,-1000,250,-305,-481,-1000,395,-880,-679,948,311,287,-309,-1000,701,-340,505,113,433,925,145,1000,-215,-925,-548,-142,-935,-659,-2,168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.String:V2VlayAzOSwgMjAyNg==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "toString():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.String:V2VlayA1NiwgMjAyNg==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "toString():java.lang.String",
            new int[]{633,-399,-200,-1,860,-508,61,-369,-673,-786,-827,778,147,115,793,213,327,127,-549,685,-920,662,152,21,399,-379,883,-11,532,422,978,-76,878,-932,-124,317,-340,11,-483,-665,723,851,-683,510,814,-242,-488,764,-227,-91,468,347,-791,197,519,258,461,-987,856,323,-132,-3,-932,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.String:V2VlayAwLCAw", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "toString():java.lang.String",
            new int[]{-537,950,-564,-114,424,-978,578,-822,43,-335,-168,-707,559,659,541,430,-585,946,-966,883,-965,-217,-850,-896,-802,524,737,118,884,862,-189,773,314,545,618,-494,-234,-712,-433,-985,-260,-112,-296,609,-964,387,210,819,-605,563,267,337,-483,-35,557,328,-575,-742,638,-258,483,-251,514,-644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.String:V2VlayAtMSwgLTE=", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "toString():java.lang.String",
            new int[]{-756,626,-63,459,975,-1000,61,-1000,-911,-561,479,1000,449,-35,-91,-1000,327,-810,-643,-921,126,-80,-44,117,-400,-1000,1000,-1000,-575,1000,978,-972,1000,-457,1000,1000,1000,62,162,-223,-1000,851,-683,556,1000,1000,670,927,1000,278,-80,-138,1000,-189,26,-1000,461,1000,1000,1000,-132,253,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.String:V2VlayA1NCwgMjAyNg==", DEReplay.run(
            "org.jfree.data.time.Week", "org.jfree.data.time.Week", "toString():java.lang.String",
            new int[]{-1000,561,545,178,-1000,-61,-201,692,-208,335,874,910,-302,-553,637,226,968,10,-817,247,653,-829,-1000,578,1000,-667,-417,-393,-721,-277,-790,-580,585,1000,340,-383,775,879,650,1000,-544,-1000,1000,-263,-335,602,817,-1000,735,-300,633,-784,227,866,-857,69,1000,-14,422,-171,-509,82,124,0}));
    }
}
