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

public class DEGeneratedTest extends junit.framework.TestCase {
    public void testDE00000() {
        assertEquals("java.lang.String:YXJn", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-1000,-393,339,235,395,-353,-118,-457,288,-291,-551,-502,-327,-431,862,993,555,301,777,34,596,-217,388,-706,-302,-216,404,1000,-94,-1000,-769,548,-82,1000,636,-1000,-39,-979,-232,148,-986,751,628,-52,-1000,1000,495,-1000,-1000,-549,-407,1000,1000,459,179,-643,1000,-552,380,-740,-449,-86,667,-96}));
    }
    public void testDE00001() {
        assertEquals("java.lang.String:LS0xMDAwbA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-433,47,484,-453,-229,-30,-690,-457,-942,-872,133,-133,-561,-819,681,1000,-41,1000,777,196,550,-583,487,-706,-302,-216,404,270,-147,-1000,-274,1000,-117,547,967,-1000,-197,-492,-836,-760,-679,996,1000,-52,354,1000,311,-1000,-978,-117,-407,1000,1000,907,-867,-643,1000,-1000,433,-667,-109,-1000,1000,-309}));
    }
    public void testDE00002() {
        assertEquals("java.lang.String:KzEwMDA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{357,-340,-691,-966,-698,-357,-1000,-1000,1000,-567,-300,-1000,242,-952,-1000,657,-148,-563,229,1000,927,-400,988,261,1000,-138,674,1000,-1000,-714,940,-1000,-1,1000,480,-1000,1000,-1000,-395,-734,-161,-989,850,-750,216,-586,876,-1000,-1000,-1000,1000,140,-117,-1000,-4,-87,-102,-1000,-720,-422,32,1000,-259,-1000}));
    }
    public void testDE00003() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{1,-92,-306,957,745,-139,771,321,-1000,-874,-750,-750,-688,284,-8,465,640,-710,457,48,-494,753,-643,649,317,-1000,317,1000,206,482,-729,-483,-789,-1000,-617,-1000,460,63,1000,72,-883,-625,766,-684,363,-1000,936,-582,-1000,-1000,-352,-659,812,-279,-245,258,288,-31,1000,902,501,-1000,1000,819}));
    }
    public void testDE00004() {
        assertEquals("java.lang.Integer:LTI5Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-297,706,-361,-381,37,-293,-685,-365,510,1000,1000,-194,-159,-1000,78,-1000,319,931,-380,-310,580,1000,1000,-673,-617,151,574,841,1000,-536,-224,587,-87,505,646,-154,977,-857,718,-142,103,14,-1000,-1000,277,-237,-1000,1000,-630,-664,-536,176,525,-1000,679,-490,328,-1000,837,-555,1000,-702,-1000,450}));
    }
    public void testDE00005() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-297,25,599,224,-1000,-495,794,-365,893,31,326,816,-980,-346,-621,-379,-1000,653,-516,-961,-1000,361,1000,1000,635,641,-338,-697,-675,-788,1000,6,925,-98,407,-446,458,-356,718,855,137,-196,162,1000,862,839,365,302,523,452,1000,1000,444,678,-139,-412,80,-629,-414,-1000,-525,468,475,-11}));
    }
    public void testDE00006() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-46,-242,404,1000,238,652,-666,341,610,-1000,-215,-408,-500,-759,1000,-135,-1000,-88,-776,-472,269,-1000,168,487,402,1000,70,-189,-301,-635,8,911,-305,580,-335,203,-432,668,867,709,-757,979,1000,-643,-607,919,-387,183,-1000,206,-329,280,-332,-264,-205,1000,-146,1000,-1000,490,-170,189,255,89}));
    }
    public void testDE00007() {
        assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{908,-1000,-396,611,200,105,-24,-773,1000,729,-832,344,-443,-1000,376,-611,29,614,-1000,437,-701,-621,811,1000,1000,539,-692,-19,845,937,820,1000,-900,1000,879,-99,800,1000,556,1000,-59,1000,1000,-1000,-189,75,331,-892,-1000,-1000,740,-546,833,886,-942,-368,-1000,20,349,-775,535,147,-972,552}));
    }
    public void testDE00008() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{494,-1000,-721,388,700,168,264,583,-42,-805,-427,-190,-444,-63,-687,-790,388,337,-782,400,-269,-256,1000,-49,400,-282,569,420,752,400,1000,1000,400,755,-827,-143,515,668,-56,139,-955,687,21,-400,207,-17,969,-613,-786,-959,1000,-221,375,-669,-137,955,-1000,400,483,-819,593,-663,-3,307}));
    }
    public void testDE00009() {
        assertEquals("java.lang.String:YWFhYWFhYWFhYWE=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-488,-577,-671,-485,-556,949,728,-332,-467,798,750,-20,201,-912,-614,262,938,168,-435,-817,-457,515,-366,966,-934,-40,-950,-465,-722,94,624,-140,429,42,-383,-70,501,-762,454,435,-793,-690,-70,-936,585,734,-629,99,-815,534,-574,-67,579,961,-89,-953,-804,-148,-565,-973,-660,-871,-572,-720}));
    }
    public void testDE00010() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-1000,-35,-441,-41,-500,1000,840,916,-452,-74,705,569,1000,-274,-1000,632,432,318,650,744,26,-1000,-256,676,-1000,-118,34,-252,-465,891,61,450,911,1000,1000,-1000,59,111,1000,216,-422,-933,-641,-1000,-996,581,294,-469,292,-352,-246,1000,708,1000,708,-174,878,-831,-328,-463,-265,-644,146,-1000}));
    }
    public void testDE00011() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-298,-715,99,-678,1000,179,257,-34,377,-347,720,463,869,-588,-14,931,-202,-213,149,110,400,-478,-593,-263,198,428,-594,-217,234,1000,509,687,218,227,12,-750,-833,-602,908,-408,643,-121,194,-31,-1,706,429,-107,-663,-900,-275,384,-443,-196,216,425,837,-788,-497,-750,-95,-691,-462,-720}));
    }
    public void testDE00012() {
        assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{1000,811,249,379,330,-702,659,-587,1000,823,1000,-545,1000,273,-228,-440,-35,-642,1000,-1000,-848,-117,424,1000,124,-701,380,-165,-1000,937,54,-555,-116,-536,-251,-1000,643,724,-1000,-878,976,1000,-615,-211,155,-904,921,955,916,-172,904,-79,640,-1000,-1000,-579,513,-552,1000,-112,-738,142,161,-1000}));
    }
    public void testDE00013() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{-1000,-778,739,-616,1000,1000,-458,167,-1000,-876,230,-515,1000,181,-842,-1000,-279,-611,-164,434,778,-240,-1000,338,1000,-168,87,-436,-704,-1000,968,-1000,1000,1000,1000,808,-50,-537,151,-247,-1000,325,941,1000,-20,1000,-1000,-506,6,-980,-1000,1000,-148,480,523,220,-187,1000,270,341,-171,-405,-1000,-1000}));
    }
    public void testDE00014() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{404,-1000,-986,-175,-399,-1000,-1000,399,-1000,863,389,1000,15,1000,272,-1000,-928,-15,933,-27,1000,354,-553,477,-1000,760,-1000,125,785,-1000,62,-1000,204,-1000,903,-677,1000,-361,728,-1000,-411,1000,-405,836,400,-1000,707,1000,165,-1000,272,615,1000,-372,515,489,254,1000,1000,-328,-428,-1000,-658,835}));
    }
    public void testDE00015() {
        assertEquals("java.lang.String:Kzk0NUw=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{644,397,-591,105,230,-583,945,-214,233,-646,517,-495,-810,238,988,-355,725,-984,-947,599,588,-628,528,-16,800,-571,-532,787,-606,-40,549,-996,408,917,8,361,726,-486,-996,110,108,909,685,-450,348,585,475,-69,856,-42,149,692,-822,-557,86,628,-32,220,797,-529,755,-689,270,-337}));
    }
    public void testDE00016() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{32,709,424,377,381,803,-573,-1000,-1000,655,-31,97,1000,200,-491,-538,1000,-9,736,901,171,663,-280,-1000,1000,518,608,438,-418,16,-302,-735,-1000,403,-1000,759,-560,713,-1000,20,-417,-326,712,-1000,1000,-388,-382,-390,548,289,778,-382,-362,-947,643,427,269,-1000,1000,-295,-151,357,-397,-371}));
    }
    public void testDE00017() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-799,747,-146,839,-325,-169,-309,355,-620,1000,499,-1000,-92,175,-217,-654,678,-1000,-603,-67,-1000,-925,-661,606,-988,409,947,-762,-1000,-874,702,-103,1000,-354,-37,232,602,-77,429,-82,-612,-615,-400,730,-1000,-501,-463,-726,336,-689,-522,-1000,1000,-668,620,-483,-830,1000,-140,461,-459,271,-514,52}));
    }
    public void testDE00018() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-818,-464,-401,195,-927,-830,817,-453,514,70,198,-565,374,-374,-237,699,-124,-903,-678,-178,506,-30,-445,215,-226,-590,-15,775,-633,-717,-44,126,-668,124,984,-782,-170,-401,919,-485,759,500,246,961,-946,137,542,-924,-154,-925,630,75,824,632,-726,761,-891,-719,411,56,765,153,-146,-463}));
    }
    public void testDE00019() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{115,92,119,-969,400,-133,194,798,782,524,1000,-560,-584,-141,858,-321,-1000,-1000,722,-284,-379,-481,507,1000,919,272,919,502,-25,-1000,-147,123,-1000,524,1000,-1000,-142,-738,1000,825,462,609,403,961,454,991,-639,-1000,982,-156,241,847,-18,109,-326,1000,-1000,-863,77,-1000,1000,-1000,527,-588}));
    }
    public void testDE00020() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-705,284,-852,-516,329,559,476,-1000,1000,76,994,-307,-974,1000,-77,922,-778,-478,346,-1000,732,854,1000,-133,-110,155,134,1000,-889,145,550,82,-627,444,-1000,-907,-211,-877,1000,861,257,807,787,1000,286,915,642,86,-45,257,504,-851,574,137,-722,54,-221,-288,368,236,1000,113,-383,-810}));
    }
    public void testDE00021() {
        assertEquals("java.lang.String:IGs5eTc=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{930,-552,299,163,169,-164,543,959,838,-84,617,-705,-847,335,302,-10,-607,-880,654,-884,-619,-70,533,-448,216,-803,361,823,528,-533,-19,-157,-275,-495,-76,-51,-62,531,-64,977,637,350,-822,-970,-697,-933,-837,-938,-87,-432,-807,434,528,-956,835,-812,-224,427,797,957,-318,827,459,-863}));
    }
    public void testDE00022() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{1000,647,49,26,-148,-851,290,-449,580,-319,-878,-50,507,94,1000,-564,361,-1000,1000,405,206,1000,541,-126,-522,-566,-605,1000,323,-743,-587,-1000,-642,1000,-698,-6,-186,-336,-1000,-193,428,927,397,-1000,-233,-736,400,-73,-962,-32,-865,133,-226,-1000,994,127,978,943,1000,-288,-1000,-356,-85,276}));
    }
    public void testDE00023() {
        assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{572,-1000,-26,-83,-206,531,273,810,17,-80,635,933,-1000,947,395,23,-638,-345,1000,-246,-242,-20,293,-420,902,-982,568,596,-433,-533,-540,-1000,-411,-495,-511,-473,-62,257,259,1000,-285,310,-478,-1000,-850,-1000,-1000,-607,-180,-1000,-325,-12,652,-956,1000,-1000,-727,427,546,1000,519,202,-563,-742}));
    }
    public void testDE00024() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{-1000,92,289,-268,753,-125,126,1000,939,627,931,834,1000,-940,201,-361,623,401,1000,1000,999,-955,174,1000,1000,286,-421,1000,-657,744,1000,-810,-310,1000,-623,-1000,-526,1000,1000,500,759,-520,-1000,1000,-1000,156,-305,-589,-946,1000,594,1000,593,821,678,-338,-139,10,-302,209,1000,-1000,120,1000}));
    }
    public void testDE00025() {
        assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{851,-973,-231,-681,-512,-96,-439,-1000,-1000,617,-292,1000,31,-425,-428,526,730,377,-834,-923,-598,925,526,-333,-670,811,821,-661,1000,-462,-150,-189,991,-110,73,-433,1000,600,-272,334,-76,1000,-102,-876,690,732,99,-416,997,-529,-675,926,-824,134,-422,867,31,-618,-61,-48,355,-710,-978,-506}));
    }
    public void testDE00026() {
        assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{496,-515,-521,-1000,778,-372,-237,526,1000,-453,110,-1000,-722,179,-813,-262,-614,344,1000,476,158,128,-218,-419,34,-730,-745,304,795,169,228,171,-512,-820,709,-246,-824,-302,-789,211,-854,-678,-788,823,-947,61,-137,166,-876,-327,536,-123,-82,971,293,-1000,-280,160,169,258,1000,-908,698,294}));
    }
    public void testDE00027() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-113,-24,497,746,96,726,971,719,606,608,-13,-834,-112,692,-155,506,696,-387,-384,-721,591,-180,-861,-199,-394,-545,-705,-172,-335,772,366,43,447,605,-977,639,270,-696,-10,-882,268,-263,836,-486,-359,-473,-536,688,-915,-505,-113,106,637,-552,635,-474,-867,898,-230,441,-800,-895,882,-236}));
    }
    public void testDE00028() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{786,979,-417,295,977,-788,-405,-525,-700,1000,549,-418,-246,19,265,1000,239,1000,-303,762,258,357,-1000,-199,-960,-1000,-327,1000,-100,-1000,-728,742,447,363,252,490,-715,1000,302,-665,358,546,-545,224,-651,-778,-1000,-1000,537,-461,-1000,935,-1000,1000,952,-464,-45,-1000,-222,-1000,547,62,481,-236}));
    }
    public void testDE00029() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-623,1000,-136,671,1000,1000,1000,261,3,221,1000,-347,1000,4,-1000,1000,16,1000,-847,-1000,-278,-1000,-1000,-515,435,572,-1000,-22,796,-1000,-1000,729,871,206,1000,1000,463,999,1000,1000,-783,-652,992,447,-1000,-535,148,136,1000,-666,729,-804,501,1000,886,72,-1000,-1000,-387,692,613,1000,577,404}));
    }
    public void testDE00030() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{493,426,-332,1000,-763,1000,-306,1000,-1000,-670,-742,823,397,406,771,-514,-1000,-371,-960,-1000,443,-714,-22,-1000,-1000,-1000,-700,174,-1000,-112,378,-685,891,-668,-1000,502,507,106,850,-1000,-773,-1000,922,395,722,-737,18,1000,-1000,204,560,548,1000,770,126,237,-181,-1000,-172,-612,-1000,-1000,0,-1000}));
    }
    public void testDE00031() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{451,-102,-289,845,-282,337,116,347,-878,-630,-369,993,-891,-921,163,-414,-595,-364,-945,838,-320,110,466,-501,289,-865,251,-91,-941,-534,-973,-610,-866,22,-232,336,-171,902,872,-283,-920,-884,942,-202,629,-757,-259,319,-603,113,720,133,617,-275,329,-670,257,110,-83,-716,37,-413,-549,-302}));
    }
    public void testDE00032() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-482,1000,-119,-492,-1000,609,382,-203,1000,1000,334,-667,1000,712,-133,1000,441,-182,848,-995,353,-1000,538,811,-35,1000,1000,-1000,1000,1000,-18,1000,-166,-313,596,-712,-1000,-619,-1000,1000,228,-540,322,-359,-181,886,110,-1000,1000,-50,493,-302,-866,105,1000,-20,151,1000,-1000,716,-390,1000,1000,946}));
    }
    public void testDE00033() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-103,177,-689,713,96,583,971,719,-582,1000,-253,592,-368,-190,-155,397,-1000,-743,-384,1000,-657,-180,998,-199,-394,101,992,-745,435,149,-847,-163,-1000,-666,94,726,-715,-74,-207,-248,-675,-794,836,-971,-359,-1000,624,-643,-64,-669,1000,802,353,-216,635,-134,-307,1000,-692,-172,-896,578,152,-689}));
    }
    public void testDE00034() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-567,1000,1000,1000,587,878,1000,712,-110,-41,-733,-575,-117,-288,129,-501,993,-255,-1000,-1000,221,818,117,-421,-581,1000,764,1000,267,700,623,1000,1000,534,1000,600,1000,-222,-859,151,-621,16,331,-611,716,-19,1000,977,-821,214,1000,589,766,-967,-11,857,-1000,1000,-84,966,-266,1000,550,990}));
    }
    public void testDE00035() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-817,-507,-627,349,-588,126,552,-489,-627,790,-616,-512,940,-797,-3,-152,607,549,933,604,967,-340,277,766,78,-709,77,590,-91,-673,19,-109,-275,-518,-647,79,-24,-605,-970,-552,-902,884,-886,-389,-530,-436,-323,-11,-313,-951,576,764,318,495,790,812,-640,-594,-672,-190,-308,269,-909,880}));
    }
    public void testDE00036() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,215,-162,-342,127,-963,-1000,56,-59,-470,39,883,-219,-665,-985,796,-391,-157,1000,-392,1000,1000,-1000,235,273,-83,-528,-805,-513,-1000,-1000,-129,-1000,-1000,1000,-1000,414,-970,1000,-1000,-1000,-691,-352,-888,-957,779,998,164,756,735,918,-1000,448,-1000,-388,-373,1000,-225,989,-1000,108,233,-185,-1000}));
    }
    public void testDE00037() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{251,-871,390,-236,435,779,900,774,407,-502,902,-892,-219,-821,-36,-499,862,-647,242,-704,158,167,-302,900,-687,258,273,-842,-513,-322,864,304,-177,-256,-160,488,414,-970,925,-306,962,-428,-706,-722,198,-195,-85,322,37,768,-658,-451,448,-352,-539,-373,53,-120,989,-669,-410,-289,-42,301}));
    }
    public void testDE00038() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{416,421,79,-924,1000,-356,539,-398,32,383,725,-1000,-212,734,-355,-74,2,-933,-1000,684,-750,-1000,710,712,1000,-314,1000,-575,188,857,666,-860,1000,-46,-66,-402,1000,-945,-883,628,500,-724,427,-126,-224,-1000,-847,-31,-501,-1000,-745,289,-225,-791,1000,-1000,-330,-1000,-757,232,344,-698,-401,85}));
    }
    public void testDE00039() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{40,455,288,789,626,144,794,-42,-694,-332,959,-416,-493,-511,454,452,21,834,-362,118,530,-494,-324,372,-629,-330,-611,39,-750,219,-93,-244,279,-917,80,-300,465,-862,-310,25,-434,-707,-464,-762,-909,-291,-368,-339,-853,-832,960,-313,-171,-862,469,-363,885,-792,-618,-332,502,279,-788,405}));
    }
    public void testDE00040() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-991,-324,-84,166,-169,-37,-4,197,-49,275,253,-87,-991,-278,497,-37,523,1000,631,202,-416,-115,-53,-540,-228,-481,660,360,-255,-935,-927,261,-64,694,298,771,-356,-759,-684,-337,-664,112,-351,-1000,-176,379,39,328,426,312,497,1000,-381,1000,-103,643,-836,-391,127,-63,-818,187,371,-416}));
    }
    public void testDE00041() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{544,522,552,-763,914,262,618,458,65,283,693,563,339,-835,-596,-116,661,-730,317,-50,-629,-224,-989,-230,-133,-175,687,474,33,350,712,-781,182,-633,314,808,605,816,-960,-979,718,-582,488,-691,-717,-593,374,235,-805,-492,23,767,927,64,17,384,-105,843,-86,861,715,-287,721,522}));
    }
    public void testDE00042() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-395,-882,-911,-208,-964,-916,105,950,152,357,973,-872,425,-827,898,-187,579,171,-969,357,-125,5,470,-640,544,-700,13,156,339,813,581,-897,-997,841,77,-112,-258,-723,804,-352,760,-973,-502,-669,329,-51,-435,-547,-59,-40,142,-851,-339,500,-762,-736,-32,-844,424,-887,-912,922,249,-505}));
    }
    public void testDE00043() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00044() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{765,1000,-761,-678,-937,-753,800,-1000,725,20,-465,-1000,-1000,22,-17,228,-336,-873,386,-100,556,102,813,50,463,263,250,1000,-286,535,-375,-1,-126,-1000,1000,-1000,-1000,-193,820,-172,-694,284,-376,-58,1000,880,-458,609,-66,-492,399,-167,-402,-1000,-1000,-105,1000,-168,-680,-1000,860,892,-554,761}));
    }
    public void testDE00045() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{415,-923,944,-374,270,62,534,286,-908,993,658,-684,976,462,4,584,-919,197,-808,-451,-414,219,786,80,-942,-739,-810,-236,818,-449,422,-220,582,956,-579,419,709,103,-545,140,-296,-396,449,712,-713,797,602,-585,-711,506,543,-454,929,251,-562,550,-406,-936,-13,967,-833,614,6,-900}));
    }
    public void testDE00046() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,1000,-676,-580,-292,924,1000,-1000,1000,161,-668,-1000,53,1000,-38,-1000,-85,41,-119,1000,155,-1000,-472,1000,823,-618,-1000,-450,537,-429,193,-42,1000,-1000,-504,83,-1000,-27,-189,-41,-1000,-33,-46,333,225,-1000,117,-410,-694,422,1000,-822,-345,-123,-1000,-1000,885,-108,471,597,1000,-1000,-156,405}));
    }
    public void testDE00047() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,-7,-761,-741,209,-980,726,60,725,673,-808,-659,-639,-797,-553,-689,824,-1000,1000,-14,638,303,669,75,-882,400,531,1000,-300,400,-1000,10,-46,-902,393,361,-399,-759,-305,339,-1000,24,15,811,431,743,-378,254,247,-923,1000,-147,-1000,1000,-851,449,-1000,-385,-1000,269,197,-739,374,-1000}));
    }
    public void testDE00048() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{538,569,-1000,-20,1000,-599,785,400,763,112,-698,80,-979,788,-31,-615,341,-374,1000,639,555,100,-484,996,881,-168,543,485,377,535,-261,-257,-435,-1000,-455,446,-325,-925,697,295,-716,17,-650,-454,748,511,-381,378,537,-948,936,53,-586,1000,10,666,578,-323,-543,-967,828,307,487,102}));
    }
    public void testDE00049() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{656,570,-1000,278,640,-572,1000,-1000,-216,-400,-770,-483,-131,-388,-200,-294,-143,-970,-166,351,-619,54,-214,1000,327,-1000,742,433,-877,-1000,267,185,561,39,565,1000,-383,884,-824,261,209,15,-134,157,528,-572,677,404,-175,-274,10,-407,-664,-453,-132,-1000,1000,-1000,-543,400,298,-1000,335,-106}));
    }
    public void testDE00050() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,441,-430,323,121,-546,894,-408,63,-287,-1000,-522,522,-593,-336,930,708,-1000,-241,647,-925,146,-170,939,285,-1000,742,245,-980,-1000,267,-338,758,-578,873,968,-560,275,-502,-58,102,376,-581,681,621,-1000,706,941,739,-708,233,-689,-566,-453,-52,-1000,-134,-1000,196,-333,668,-1000,-260,997}));
    }
    public void testDE00051() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{826,-621,-757,-1000,-632,366,872,342,892,1000,-931,-780,-723,1000,896,416,-163,-72,-29,48,791,-318,1000,1000,5,-255,154,61,465,-7,249,-528,-241,386,-686,1000,604,-269,-1000,545,-1000,-1000,920,466,940,1000,823,515,-910,736,362,-392,686,1000,-1000,1000,284,-708,-1000,-549,844,1000,1000,-757}));
    }
    public void testDE00052() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{529,-339,599,-9,-702,-121,60,-220,-989,-560,633,71,83,-675,-640,-237,-553,671,450,187,144,418,-893,-149,-348,456,273,403,-523,-158,231,-694,-783,-521,672,-248,-622,-657,-290,-857,518,-187,612,124,-34,953,558,580,-676,675,601,-722,244,682,193,-85,522,-255,-865,198,-489,-509,609,300}));
    }
    public void testDE00053() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-254,961,-96,29,969,-656,-294,-1000,555,-532,-1000,1000,-915,582,906,-272,-789,-1000,-220,-104,394,828,695,464,-352,-561,88,-429,-42,134,-548,826,560,-690,824,-574,538,199,212,-58,-450,-518,-581,194,-1000,-1000,-1000,-781,480,-1000,534,1000,497,733,903,-932,-351,-764,-350,-438,313,-276,-319,-1000}));
    }
    public void testDE00054() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{685,920,846,-291,797,522,-800,966,141,748,601,813,-674,-927,-103,-303,883,338,-868,-60,-13,643,-31,767,-421,-985,-316,-784,-522,453,492,42,-589,76,-497,-722,-958,-486,-105,-81,77,-547,-942,372,306,260,388,771,-961,38,172,-979,-167,92,-403,-456,-399,-190,274,-930,-738,260,-121,-435}));
    }
    public void testDE00055() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{1000,322,-471,-749,198,891,-599,193,-1000,147,-548,-898,-1000,618,260,224,-18,1000,-758,1000,-209,1000,637,1000,-239,91,-1000,-1000,-1000,541,-23,560,-328,-906,-482,-512,-1000,745,-35,490,146,142,20,-771,-492,419,-692,1000,633,524,-445,-1000,309,-271,-369,-883,-478,-509,542,327,-1000,-425,58,-301}));
    }
    public void testDE00056() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{893,1000,213,589,109,-1000,-1000,189,-133,-843,-921,944,1000,-842,593,846,601,244,1000,-577,-141,-76,-225,-1000,1000,289,1000,501,335,-10,924,21,1000,-679,9,-535,-240,375,874,-835,-5,-422,-623,753,-576,-97,873,-647,754,605,774,-213,134,-718,-322,-1000,869,975,-1000,-681,-958,-279,-200,179}));
    }
    public void testDE00057() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-1000,-983,-646,-971,-258,162,1000,-316,-1000,-796,1000,170,-1000,1000,-454,505,-845,-1000,-310,-420,81,334,-1000,286,317,-1000,-1000,705,728,-345,270,-400,-1000,1000,-540,33,-768,861,260,309,-11,1000,617,831,-24,75,746,-231,468,-408,-254,1000,356,234,1000,-653,-515,1000,810,388,146,215,694,-181}));
    }
    public void testDE00058() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{1000,1000,388,66,-341,-542,55,-383,-195,-524,462,1000,-1000,919,-739,832,1000,113,-1,-824,30,1000,780,-290,-658,-993,1000,980,-472,396,-803,-744,-173,286,-740,386,-896,1000,1000,-906,129,-1000,-958,506,714,-853,386,434,504,1000,655,30,300,530,732,-733,1000,-741,330,-486,711,229,-669,-580}));
    }
    public void testDE00059() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{290,-545,-49,229,296,-737,-544,750,989,805,-890,790,-149,-842,685,-522,-925,935,-786,862,-726,443,382,716,-497,-644,813,-91,-950,399,924,333,791,-382,-518,915,-924,-723,350,-936,-912,137,-955,-929,-576,-41,-344,-579,-780,414,-552,-213,-413,-718,-947,-492,339,816,512,824,-958,439,-346,-216}));
    }
}
