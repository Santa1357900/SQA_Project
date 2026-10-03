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
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "appendUsage(java.lang.StringBuffer,java.util.Set,java.util.Comparator):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00001() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "appendUsage(java.lang.StringBuffer,java.util.Set,java.util.Comparator):void",
            new int[]{393,145,-310,-922,-261,72,84,259,131,278,615,-215,319,436,-726,-244,-793,-435,489,-52,598,937,468,546,768,-730,-212,667,-854,-97,8,-82,211,-335,-194,-660,853,-85,243,-943,-68,-152,85,692,-469,569,806,-796,475,-333,910,-828,128,72,481,740,134,-179,-476,473,-146,-984,-437,178}));
    }
    public void testDE00002() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "canProcess(org.apache.commons.cli2.WriteableCommandLine,java.lang.String):boolean",
            new int[]{-435,-49,20,610,-573,-468,-464,97,618,-113,600,324,-218,-14,578,-1000,96,255,682,-172,-167,-407,212,234,1000,-174,-166,815,-572,-95,1000,126,-767,-1000,-1000,168,139,614,372,5,93,592,1000,443,-394,-537,581,-531,51,79,-884,-84,-595,99,1000,-452,708,669,878,-834,-53,-1000,272,904}));
    }
    public void testDE00003() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "canProcess(org.apache.commons.cli2.WriteableCommandLine,java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00004() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "canProcess(org.apache.commons.cli2.WriteableCommandLine,java.util.ListIterator):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00005() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "defaults(org.apache.commons.cli2.WriteableCommandLine):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00006() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "findOption(java.lang.String):org.apache.commons.cli2.Option",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00007() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "findOption(java.lang.String):org.apache.commons.cli2.Option",
            new int[]{634,-392,-197,681,917,-311,-400,-656,985,-555,-42,-742,-13,-748,362,-789,292,876,717,-381,696,586,-215,41,-279,-755,-900,492,-582,-674,952,434,-542,-634,747,-597,-171,359,-202,-204,-18,279,452,-496,881,87,-665,-181,97,-685,455,288,805,-332,-57,-925,-634,931,241,763,-991,927,-251,-470}));
    }
    public void testDE00008() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "findOption(java.lang.String):org.apache.commons.cli2.Option",
            new int[]{112,127,-815,-766,-833,-475,346,342,-292,251,-151,-232,526,-393,1000,1000,-719,631,-353,-397,-556,-72,682,782,-1000,-709,1000,-725,583,560,-400,938,-881,-80,-1000,87,492,582,582,121,386,639,-482,757,-143,-211,117,1000,-854,414,199,-674,-503,170,-915,718,-142,-1000,-604,173,-1000,995,851,459}));
    }
    public void testDE00009() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "getDescription():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00010() {
        assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "getId():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00011() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "getPreferredName():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00012() {
        assertEquals("TYPE:java.util.Collections$UnmodifiableSet", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "getPrefixes():java.util.Set",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00013() {
        assertEquals("TYPE:java.util.Collections$UnmodifiableSet", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "getTriggers():java.util.Set",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00014() {
        assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "helpLines(int,java.util.Set,java.util.Comparator):java.util.List",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00015() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "isRequired():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00016() {
        assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "isRequired():boolean",
            new int[]{-657,-78,-1000,780,1000,450,796,1000,539,-1000,865,-363,-1000,198,-1000,1000,-343,-1000,-13,1000,1000,-1000,1000,1000,-1000,1000,1000,1000,1000,-303,1000,-1000,670,-292,-524,-1000,118,-822,-188,-86,-1000,964,-1000,-283,388,-223,-914,150,-1000,384,-541,-231,1000,-120,-813,1000,-846,839,1000,-1000,-609,-596,-143,-1000}));
    }
    public void testDE00017() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "isRequired():boolean",
            new int[]{13,676,-156,440,-441,-740,982,69,106,406,-631,292,535,514,726,686,186,107,222,331,17,-552,-453,863,357,576,886,-126,-779,-353,423,122,58,298,-646,-515,76,-614,6,871,-187,881,676,404,-477,-5,-990,141,111,-347,394,277,-164,-675,691,-650,840,-70,-117,-691,-362,38,698,-522}));
    }
    public void testDE00018() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "process(org.apache.commons.cli2.WriteableCommandLine,java.util.ListIterator):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00019() {
        assertEquals("THROW:org.apache.commons.cli2.OptionException", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "validate(org.apache.commons.cli2.WriteableCommandLine):void",
            new int[]{984,-324,-1000,768,-495,-890,-6,400,96,1000,428,1000,-368,-770,-79,-273,-105,-640,-1000,264,343,383,-1000,-1000,-1000,-132,66,-771,703,-856,725,-843,-775,43,-357,389,477,-471,-1000,-214,-235,1000,528,391,-648,1000,260,-851,-1000,-1000,-601,400,-169,292,-680,-1000,-389,-1000,838,-364,-160,-646,929,-549}));
    }
    public void testDE00020() {
        assertEquals("THROW:org.apache.commons.cli2.OptionException", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "validate(org.apache.commons.cli2.WriteableCommandLine):void",
            new int[]{-633,-985,-755,654,665,-163,412,291,-522,-350,-413,873,478,-4,-18,792,28,817,-133,-624,58,590,627,-455,467,511,140,-434,-136,-852,433,-13,-706,-578,730,-228,-474,799,-805,237,-530,94,973,452,-666,477,414,-563,-751,-98,-198,713,258,-529,-657,-471,-194,-958,-317,468,161,-229,819,999}));
    }
    public void testDE00021() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli2.Option", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "validate(org.apache.commons.cli2.WriteableCommandLine):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00022() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "appendUsage(java.lang.StringBuffer,java.util.Set,java.util.Comparator):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00023() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "appendUsage(java.lang.StringBuffer,java.util.Set,java.util.Comparator):void",
            new int[]{521,-308,405,-212,80,-1000,-577,141,628,121,791,-854,-271,-598,-731,6,-484,892,-772,-613,-321,37,-154,-822,-943,-849,-733,108,-163,280,-731,291,-951,-320,-882,-1000,51,484,626,-4,-17,354,191,-390,-199,919,-396,443,-558,-387,-255,2,-107,166,-66,915,517,-540,699,-551,-161,-301,941,-27}));
    }
    public void testDE00024() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "appendUsage(java.lang.StringBuffer,java.util.Set,java.util.Comparator,java.lang.String):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00025() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "appendUsage(java.lang.StringBuffer,java.util.Set,java.util.Comparator,java.lang.String):void",
            new int[]{-257,41,115,-186,-684,-416,-414,-920,922,-708,791,827,628,67,488,70,408,-253,448,-622,-26,142,-869,660,-840,823,-479,971,601,204,-902,-139,-595,-580,6,970,53,455,980,-129,855,-944,-246,-530,952,-954,-643,-325,67,-140,-62,-498,-995,-672,-394,-701,-763,-943,-882,347,804,105,214,749}));
    }
    public void testDE00026() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "canProcess(org.apache.commons.cli2.WriteableCommandLine,java.lang.String):boolean",
            new int[]{314,880,-955,-423,397,88,-698,774,-916,-665,388,313,-503,-818,-807,337,-42,-848,933,-136,-772,825,-793,844,762,-986,819,-610,-65,-333,286,-860,-604,670,956,-727,976,-188,674,830,-99,836,79,-310,462,-266,768,770,-257,-803,800,-114,-475,-486,337,-964,75,-914,529,-163,-856,-777,242,335}));
    }
    public void testDE00027() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "canProcess(org.apache.commons.cli2.WriteableCommandLine,java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00028() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "defaults(org.apache.commons.cli2.WriteableCommandLine):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00029() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "findOption(java.lang.String):org.apache.commons.cli2.Option",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00030() {
        assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "getAnonymous():java.util.List",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00031() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "getDescription():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00032() {
        assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "getMaximum():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00033() {
        assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "getMinimum():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00034() {
        assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "getOptions():java.util.List",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00035() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "getPreferredName():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00036() {
        assertEquals("TYPE:java.util.Collections$UnmodifiableSet", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "getPrefixes():java.util.Set",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00037() {
        assertEquals("TYPE:java.util.Collections$UnmodifiableSet", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "getTriggers():java.util.Set",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00038() {
        assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "helpLines(int,java.util.Set,java.util.Comparator):java.util.List",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00039() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "isRequired():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00040() {
        assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "isRequired():boolean",
            new int[]{-373,495,45,58,-774,-603,-20,193,-369,551,348,34,-494,-996,-77,287,-554,-966,424,781,902,-731,-949,781,314,-585,-688,484,498,-393,-601,199,-171,8,245,-130,-582,501,258,322,-882,21,700,894,952,449,692,-32,-638,811,-107,-687,-495,10,74,129,-215,284,-24,218,777,-827,-858,-206}));
    }
    public void testDE00041() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "process(org.apache.commons.cli2.WriteableCommandLine,java.util.ListIterator):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00042() {
        assertEquals("THROW:org.apache.commons.cli2.OptionException", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "validate(org.apache.commons.cli2.WriteableCommandLine):void",
            new int[]{1000,-300,-1000,400,376,-480,-179,-289,-575,1000,-1000,-1000,-224,1000,290,-1000,-1000,-640,1000,-502,-1000,-48,-907,365,739,204,-191,29,-1000,971,-258,74,-1000,-318,597,-1000,1000,1000,-1000,168,-1000,-139,-119,-612,-89,590,-1000,487,-929,1000,18,-163,-763,-469,832,-1000,-144,-659,1000,-1000,-1000,623,454,828}));
    }
    public void testDE00043() {
        assertEquals("THROW:org.apache.commons.cli2.OptionException", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "validate(org.apache.commons.cli2.WriteableCommandLine):void",
            new int[]{774,229,475,1000,1000,-447,603,455,-183,1000,-436,-1000,-75,-283,-67,593,-794,-6,713,-1000,-1000,-887,52,-96,752,-123,82,-1000,144,-175,-1000,-553,-346,577,606,-1000,49,74,0,-593,-117,228,-445,465,-493,897,-532,37,-1000,742,-299,-418,394,-423,1000,-713,328,718,1000,-374,-1000,-54,680,-141}));
    }
    public void testDE00044() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli2.option.GroupImpl", "org.apache.commons.cli2.option.GroupImpl", "validate(org.apache.commons.cli2.WriteableCommandLine):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00045() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "canProcess(org.apache.commons.cli2.WriteableCommandLine,java.util.ListIterator):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00046() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "defaults(org.apache.commons.cli2.WriteableCommandLine):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00047() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "equals(java.lang.Object):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00048() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "findOption(java.lang.String):org.apache.commons.cli2.Option",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00049() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "findOption(java.lang.String):org.apache.commons.cli2.Option",
            new int[]{991,-531,-784,206,866,456,-739,377,-626,-885,550,-710,463,250,-310,832,980,-377,364,-119,-706,-103,259,-223,297,215,-563,256,772,474,-719,153,883,383,-889,612,163,-199,-670,350,-205,504,-492,567,-810,536,-767,-453,-84,598,726,-339,-93,-432,-772,524,-491,-861,21,-579,419,-146,-475,-275}));
    }
    public void testDE00050() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "findOption(java.lang.String):org.apache.commons.cli2.Option",
            new int[]{-560,509,-400,704,-7,-74,-258,-295,1000,-1000,348,-416,-400,24,1000,-101,-126,219,-478,224,666,-213,760,-603,1000,508,270,256,163,307,-284,-1000,-253,-798,-123,286,-152,-400,425,-366,-383,107,231,-939,-429,346,429,1000,-400,165,-180,280,-753,-126,1000,-232,-477,-346,-151,-101,1000,-853,400,1000}));
    }
    public void testDE00051() {
        assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "getId():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00052() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "isRequired():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00053() {
        assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "isRequired():boolean",
            new int[]{336,0,-505,-307,-1000,-270,-706,-1000,-49,0,14,1000,-1000,241,-97,340,-324,0,-656,482,0,-367,-978,681,633,-803,-91,680,-559,-1000,313,495,856,-635,-215,488,-1000,1000,-671,406,264,281,-614,0,112,60,-917,-637,1000,-1000,364,-1000,-1000,-283,-18,-1000,1000,-1000,1000,0,-704,112,1000,-965}));
    }
    public void testDE00054() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "isRequired():boolean",
            new int[]{259,492,-530,-48,836,-419,634,695,656,681,-235,-928,455,-751,-374,-502,623,73,669,-899,395,567,992,853,-780,-629,517,-575,980,642,-59,807,-925,-14,-128,-935,-638,-392,-933,-941,418,-6,701,420,658,-807,205,-370,-508,571,-638,-341,863,-145,-930,309,276,190,-523,-884,750,-22,-994,489}));
    }
    public void testDE00055() {
        assertEquals("java.lang.String:WzB4ODAwMDAwMDAwMDAwMDAwICgpXQ==", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "toString():java.lang.String",
            new int[]{-489,-347,1000,-1000,917,-724,-246,732,-853,-173,-96,53,-924,-1000,428,1000,-988,-762,283,-935,-1000,345,694,-1000,1000,606,-1000,1000,-444,-156,-71,-134,232,-644,36,-630,-345,-1000,723,1000,1000,1000,886,917,-1000,587,102,-1000,103,-1000,277,-69,-1000,-389,-1000,-1000,1000,-257,898,-586,95,-817,532,-364}));
    }
    public void testDE00056() {
        assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.cli2.option.OptionImpl", "org.apache.commons.cli2.option.GroupImpl,org.apache.commons.cli2.option.PropertyOption,org.apache.commons.cli2.option.SourceDestArgument", "toString():java.lang.String",
            new int[]{372,784,325,512,643,698,-551,68,416,-461,-777,-659,-832,-931,-566,633,-526,545,55,526,-62,-264,148,-319,-337,322,-726,258,867,-62,633,-534,-787,-983,-63,629,583,961,-106,-381,81,-841,-905,463,332,-95,98,-442,-654,342,525,983,-395,-754,-869,153,-626,-332,-47,-734,727,235,-107,-169}));
    }
}
