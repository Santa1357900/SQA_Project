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
            new int[]{405,-428,309,-151,-38,-614,131,728,419,-853,-603,872,331,131,-309,-333,-624,307,256,157,776,-218,807,431,-690,191,-457,553,203,-839,5,530,443,837,-14,-830,-629,799,-767,558,-273,676,363,373,-199,226,-908,296,-223,741,-339,-639,946,-736,-829,235,-941,275,416,168,750,267,778,615}));
    }
    public void testDE00001() {
        assertEquals("java.lang.String:KzB4ODAwMDAwMDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{214,-550,-481,72,-1000,625,306,-302,-44,454,742,57,384,165,275,-245,-203,-81,17,-182,-39,-397,-558,980,390,-545,813,-787,-85,333,-317,-510,-670,-138,-1000,364,221,234,-601,-12,-656,-493,-311,-423,127,480,-4,13,25,-452,213,61,-740,-400,-382,-961,-200,88,-284,382,25,-624,-200,199}));
    }
    public void testDE00002() {
        assertEquals("java.lang.String:YXJn", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-45,1000,524,-408,-1000,887,-1000,581,541,-1000,-50,-925,-329,-1000,501,471,-319,803,1000,575,480,1000,-723,433,-1000,-574,495,1000,-1000,167,-592,-471,655,-860,323,1000,-1000,-1000,678,445,-1000,1000,-489,-219,663,-1000,-543,-7,1000,-144,-72,-487,1000,330,-982,-1000,-315,-1000,-577,1000,1000,-1000,-1000,-119}));
    }
    public void testDE00003() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-707,5,239,51,542,945,918,-236,435,-194,389,-216,568,-462,-98,-766,59,-541,525,250,677,-970,882,237,-482,-251,664,-761,-257,-594,503,-511,-939,-740,239,12,-132,464,-848,-502,253,-533,-717,-439,-546,-954,635,-30,767,143,730,-504,-617,-281,785,563,195,-503,580,-880,-773,-380,2,798}));
    }
    public void testDE00004() {
        assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{1,-718,169,1000,707,746,-1000,-1000,-855,-195,1000,-797,-1000,1000,-724,-1000,-1000,800,759,-933,-1000,-1000,-477,926,712,1000,-232,1000,1000,1000,-1000,-520,1000,-1000,-951,1000,-498,-1000,-1000,877,-262,-866,85,-87,-1000,-1000,-193,604,-487,-339,1000,-1000,-153,-1000,1000,-36,-1000,382,1000,-1000,1000,207,125,1000}));
    }
    public void testDE00005() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{1000,-777,414,-633,-1000,-1000,-49,310,682,-735,-501,-466,735,-445,198,232,-497,800,-816,1000,-365,-658,-8,-966,284,133,-271,-980,427,214,753,-1000,83,617,-737,226,-1000,93,397,-772,-997,763,-836,260,-225,1000,1000,-121,202,647,659,-642,568,-986,-361,1000,-606,237,-1000,181,1000,-238,32,-527}));
    }
    public void testDE00006() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{143,287,949,1000,-285,157,-1000,-714,1000,573,282,11,-492,62,335,-552,-511,-913,-590,172,658,-613,-1000,0,-333,1000,392,-443,-96,-1000,-476,-748,332,-896,921,282,473,-387,879,1000,-925,-132,-554,1000,-525,387,-133,245,-376,-1000,-151,753,-675,-438,602,432,-278,527,1000,-18,196,518,-1000,379}));
    }
    public void testDE00007() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{200,64,664,-176,-5,-831,215,961,-633,-684,-220,578,1000,88,542,78,1000,1000,57,1000,-44,1000,451,-411,-1000,759,-686,915,-680,400,435,-1000,722,327,-1000,157,-608,844,-19,526,1000,-915,88,-1000,279,-117,634,11,96,232,-56,-348,705,482,-1000,-133,747,473,-1000,-319,-235,-189,1000,146}));
    }
    public void testDE00008() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-1000,-402,919,-253,-778,-1000,104,-99,-267,-219,846,133,272,724,-354,-179,-320,591,462,112,-776,-1000,-250,482,-271,618,-912,-1000,833,14,729,-502,146,-192,-623,-81,-596,166,995,-802,-585,242,697,320,-664,-382,-634,330,826,93,749,-133,1000,672,-379,-912,-575,64,-432,-523,501,-546,30,-377}));
    }
    public void testDE00009() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{508,-59,-401,-401,224,-374,-887,945,-938,239,128,-866,169,250,-883,13,438,548,329,-986,-165,69,-945,735,-803,659,143,939,-568,-404,679,859,225,-529,678,903,-482,703,-673,-679,-173,-91,908,-772,232,-510,-162,464,-666,394,456,-750,830,802,167,290,-545,661,316,-932,-970,-565,-195,434}));
    }
    public void testDE00010() {
        assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-919,359,104,842,471,801,1000,-78,-225,417,-190,975,-1000,-819,-237,1000,533,510,-918,1000,-1000,-357,-1000,-817,-829,-919,-418,141,334,-407,29,1000,807,-1000,1000,-801,-456,856,-147,-811,1000,978,1000,717,536,-1000,-190,1000,-452,-1000,-1000,-1000,-1000,-1000,1000,-1000,292,561,-360,755,-660,984,-544,-1000}));
    }
    public void testDE00011() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-1000,-140,-836,600,585,-1000,1000,-221,-824,77,120,638,-1000,-480,101,689,697,1000,-1000,905,-1000,-405,-1000,683,-1000,-249,-301,-161,612,-301,242,1000,1000,-288,989,-217,183,920,107,732,1000,1000,1000,656,-188,-1000,-498,1000,-154,-877,-1000,-1000,-1000,-730,1000,-1000,199,445,375,623,-1000,820,-54,-682}));
    }
    public void testDE00012() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{-686,-783,929,569,-923,-903,411,-505,-340,-175,-132,724,695,548,91,496,-352,128,-914,-792,449,5,-852,-826,-176,-728,-353,-562,48,-465,-431,-712,487,-63,-199,430,-932,-987,-824,606,570,-79,359,848,-454,-956,-179,556,-451,948,328,-920,-630,-745,659,-377,-978,-883,-420,909,-677,649,-481,303}));
    }
    public void testDE00013() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{-255,1000,-156,393,-76,768,-719,400,-497,-416,-1000,-764,577,980,542,211,530,613,1000,1000,-369,1000,1000,815,1000,-281,-878,909,377,-49,-1000,-658,-730,-1000,-489,1000,240,-845,1000,872,-1000,-227,689,-767,-801,748,1000,1000,-673,-1000,-1000,-404,705,876,1000,914,873,449,-909,-960,-1000,-411,317,970}));
    }
    public void testDE00014() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{-750,-1000,854,9,254,-1000,716,-494,617,260,1000,432,20,-468,-761,128,648,280,-994,-374,843,672,92,-714,432,-1000,-776,-1000,-37,-128,447,143,-686,209,-179,-542,-95,-1000,-1000,569,-896,1000,-904,-694,-278,324,-132,-344,-960,458,-539,-1000,-888,-147,70,-21,-1000,-1000,-930,1000,-906,1000,523,95}));
    }
    public void testDE00015() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-1000,-709,189,-687,1000,650,-1000,880,-387,-930,388,-1000,-1000,334,1000,-756,1000,-252,-1000,-1000,1000,-454,-272,190,549,1000,-105,-542,-163,1000,631,-105,-902,-579,-1000,276,-1000,991,-1000,374,-1000,1000,49,-761,-1000,1000,-1000,-1000,661,1000,-298,-663,-1000,170,-874,113,-1000,-1000,1000,-1000,997,895,395,1000}));
    }
    public void testDE00016() {
        assertEquals("java.lang.String:LS0weDgwMDAwMDAwMDA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-326,1000,804,-51,-629,-41,949,1000,9,473,-1000,579,1000,59,-1000,262,851,489,146,-1000,192,1000,-1000,-830,1000,368,1000,281,-437,-623,-364,1000,-393,402,138,-305,-666,1000,-1000,603,1000,384,-318,-430,1000,-269,-214,78,183,582,436,53,273,584,1000,872,716,323,607,251,-1000,-283,-363,-1000}));
    }
    public void testDE00017() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{51,63,329,-989,704,-403,-678,774,-1000,-225,380,370,130,-107,60,58,737,910,213,-600,1000,-519,-1000,-902,895,576,-415,-1000,-543,-833,858,-166,-402,86,-65,-326,222,1000,-688,731,-1000,1000,1000,-512,-516,1000,-404,-1000,-1000,-170,671,-636,-601,-797,871,511,-570,-1000,1000,127,459,-373,-21,480}));
    }
    public void testDE00018() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-82,-390,224,985,-754,-523,-177,-475,-867,-191,-736,132,633,-548,714,-323,57,132,-594,-570,-426,-869,-899,-549,-330,580,493,84,904,-716,-870,-397,-509,281,-587,-508,625,-670,526,-982,-821,391,789,769,-540,-151,858,653,-11,-332,-584,-515,-16,994,-415,745,-859,-189,142,335,-393,-841,799,-747}));
    }
    public void testDE00019() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{467,594,159,-81,537,-833,834,522,147,961,352,-153,-803,-966,535,129,434,-726,-158,-87,308,881,542,-951,211,25,329,-929,988,-431,-599,-36,671,964,-33,742,798,461,514,-589,399,654,-254,-38,725,16,-965,-214,193,-428,-484,615,-401,-831,433,-516,664,-146,-688,-520,252,-151,-379,-843}));
    }
    public void testDE00020() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-165,-39,319,1000,908,-402,113,-1000,1000,-327,-769,-1000,-334,1000,222,428,111,101,-271,-20,1000,-766,363,1000,1000,-753,-434,720,84,-271,-217,40,803,-1000,-1000,22,234,-147,-1000,-545,1000,1000,474,-1000,472,271,161,603,548,910,-584,-316,368,435,1000,271,1000,-236,890,874,-1000,-1000,-737,1000}));
    }
    public void testDE00021() {
        assertEquals("java.lang.String:KzM1", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{-765,830,334,-425,-252,-35,-398,-356,-196,-977,-800,431,667,-91,865,-858,831,-227,-293,-678,979,-432,-770,376,929,591,-174,-855,589,-464,-7,305,860,140,734,385,-473,635,983,919,-743,917,-760,-580,18,-425,104,691,452,760,-127,-724,47,231,975,839,307,874,642,-350,549,468,933,-551}));
    }
    public void testDE00022() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{-371,372,-186,23,720,508,1000,486,-826,-840,-715,1000,-759,-510,920,-1000,-1000,621,149,1000,716,385,-235,-399,189,288,584,-1000,-723,1000,1000,718,-229,-250,-14,326,-738,-13,71,224,972,631,756,1000,-187,-177,1000,-492,-361,-224,380,1000,-1000,-804,-422,962,-152,-375,-1000,874,1000,535,-885,1000}));
    }
    public void testDE00023() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{-404,1000,-241,-772,675,-528,1000,-545,-382,-1000,-1000,-466,-336,-670,942,165,-1000,154,1000,670,-1000,-19,-329,450,304,-1000,1000,-1000,-1000,1000,-743,421,737,-261,1000,64,-518,595,565,-1000,-1000,25,-831,218,-1000,628,577,-124,571,-691,-162,-1000,-1000,-313,1000,-633,231,-1000,14,1000,666,1000,689,1000}));
    }
    public void testDE00024() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{-566,-1000,639,1000,-836,-1000,31,-639,1000,197,695,-1000,179,339,1000,-1000,434,-1000,-1000,1000,-218,-299,-300,1000,1000,167,94,741,77,605,-867,-558,-1000,-1000,-199,-960,-77,-232,-96,-10,-413,-1000,-295,-506,-197,-1000,210,-1000,-1000,194,1000,990,-1000,1000,1000,707,1000,1000,529,-363,-390,1000,-1000,1000}));
    }
    public void testDE00025() {
        assertEquals("java.lang.Integer:NzQy", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{-765,-363,409,853,587,-499,793,845,-1000,742,-745,-459,203,-1000,-1000,-661,-294,1000,1000,206,511,955,265,-850,153,996,165,647,674,1000,-921,367,-103,-930,-463,1000,-1000,630,-1000,-1000,1000,-570,317,-913,-33,273,1000,-898,1000,-235,317,522,57,207,-1000,1000,241,-278,-1000,-893,623,-925,1000,-578}));
    }
    public void testDE00026() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{-1000,-222,84,-905,1000,-505,-630,1000,-606,901,-858,-375,-697,92,722,-421,-89,1000,1000,479,350,-129,-186,45,61,-128,204,760,241,590,-1000,-1000,-897,523,-1000,-166,-666,-130,-209,199,478,-898,-173,-1000,-217,3,447,-237,1000,-1000,1000,1000,-366,664,83,1000,861,424,-894,-560,-895,381,400,-429}));
    }
    public void testDE00027() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{1000,1000,1000,351,623,-52,26,1000,471,143,-820,-553,-467,-972,296,495,-368,-175,995,902,-420,1000,-609,868,1000,-479,718,-495,831,-112,-1000,-704,404,466,-711,-861,-609,-143,42,-1000,360,-403,-24,-1000,1000,360,-1000,-1000,-657,-1000,1000,52,-274,-1000,654,342,486,1000,-880,-921,913,-1000,-983,1000}));
    }
    public void testDE00028() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-983,-243,354,-873,-1000,-1000,-957,-387,-336,-243,-419,1000,-1000,-564,515,1000,576,833,-109,-1000,900,1000,-338,-330,360,-1000,917,960,387,-194,220,-429,-977,88,-370,-1000,1000,1000,-275,-399,495,1000,118,-987,8,-741,-1000,1000,-753,-525,-1000,-90,120,434,200,-372,553,-1000,764,586,1000,-1000,614,-818}));
    }
    public void testDE00029() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,-1000,-763,-1000,-1000,-1000,916,-1000,179,1000,-1000,-366,432,-594,1000,-721,-1000,-940,415,-1000,134,-801,-1000,-1000,185,866,-724,1000,-1000,-305,47,-641,-1000,-1000,-1000,1000,1000,-358,-1000,675,-1000,-1000,-1000,575,-364,-334,1000,1000,601,-327,-411,1000,-168,6,94,-1000,-629,-1000,359,-824,-1000,1000,1000,926}));
    }
    public void testDE00030() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{1000,1000,1000,1000,-422,8,122,1000,422,-464,-1000,-897,419,-785,-857,-100,-132,-420,455,1000,329,886,-93,1000,1000,-144,-130,-1000,250,579,-652,-447,1000,1000,168,-1000,1000,498,1000,-1000,1000,-78,258,-589,1000,-938,-1000,-984,487,-1000,1000,-945,397,-670,153,301,-38,1000,-1000,-1000,1000,-1000,1000,954}));
    }
    public void testDE00031() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,-1000,-996,-1000,-1000,-1000,915,-561,493,1000,-1000,-904,0,-659,68,-1000,-1000,-1000,474,-1000,-413,-1000,-1000,920,400,851,-790,1000,-1000,-786,95,-958,-1000,-1000,-1000,1000,1000,-589,-1000,754,-1000,-1000,-1000,514,263,134,993,1000,971,-234,-1000,1000,-639,-271,247,-1000,-1000,-1000,-799,-1000,-1000,1000,1000,478}));
    }
    public void testDE00032() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{772,194,64,-48,-71,1000,156,621,389,571,-1000,-492,-141,-1000,-1000,1000,131,-842,-80,752,-704,257,-307,1000,-12,-1000,1000,-367,1000,16,-1000,-1000,1000,-1000,-287,-1000,-90,-209,-400,-1000,478,-486,-58,-376,1000,928,-1000,-547,-354,-1000,1000,-1000,-274,-1000,654,-244,988,516,-788,-1000,1000,-1000,-532,1000}));
    }
    public void testDE00033() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{458,502,187,-411,-1000,115,1000,938,-111,-350,-1000,227,688,1000,206,-1000,-618,-85,-1000,-433,-318,451,285,-19,1000,196,-686,611,1000,1000,-959,709,-66,-548,-616,1000,-376,260,-1000,-1000,-1000,850,202,-1000,807,-192,-300,-1000,-498,-1000,1000,-1000,506,0,1000,305,-1000,1000,444,-1000,-1000,896,1000,18}));
    }
    public void testDE00034() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{12,962,-181,545,704,356,1000,187,-1000,-257,-153,-867,-836,-1000,-161,-441,-1000,499,356,-76,-191,-340,178,-517,814,-288,20,469,-395,279,-467,639,-151,-772,-576,934,-719,718,-1000,-728,-1000,513,911,-788,1000,-157,-578,-706,-613,-159,-1000,-383,-355,-283,-730,1000,283,633,240,-1000,148,-958,1000,1000}));
    }
    public void testDE00035() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-179,-253,1000,-115,-100,-769,1000,-862,-776,380,-330,-903,-1000,-1000,-558,-657,-836,472,-231,566,-51,-365,370,29,172,-203,796,-245,157,-66,367,58,19,-860,-713,1000,-757,542,-169,-303,-1000,675,658,-653,605,-680,945,-1000,-692,-276,-1000,-694,-140,-65,-357,1000,110,-272,380,-1000,1000,-1000,919,848}));
    }
    public void testDE00036() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{302,1000,339,1000,-273,-894,-268,1000,-748,670,-275,1000,-754,282,954,-1000,1000,962,-1000,-483,-948,-192,-70,546,-818,-776,-1000,-1000,-1000,689,-141,1000,-769,894,-551,-674,-598,706,204,463,-1000,1000,-440,-235,989,218,841,913,595,-489,-3,-785,-141,-876,1000,512,-194,-610,1000,-1000,-1000,173,1000,-707}));
    }
    public void testDE00037() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-274,-942,644,206,275,477,-801,90,377,575,706,-856,28,-763,-951,474,422,968,295,1000,1000,-830,947,129,449,-534,1000,1000,-1000,-761,123,-241,481,1000,-571,-441,-235,945,145,1000,147,296,-36,-98,-1000,719,302,-475,-915,310,1000,-479,971,-503,-113,458,-1000,80,-922,66,-33,312,-222,478}));
    }
    public void testDE00038() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{73,363,339,734,-640,-1000,400,1000,-1000,865,-1000,-620,-31,-698,550,-685,524,373,1000,-957,-937,609,-626,762,303,-1000,-349,-400,141,284,-184,-1000,-856,242,-686,-479,-617,597,502,-202,-400,311,1000,411,509,-1000,-11,46,1000,-400,-838,-301,-668,-329,341,247,-99,1000,149,-1000,-305,-432,400,-6}));
    }
    public void testDE00039() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,-197,839,22,613,-1000,-717,-237,-328,236,1000,-144,-1000,-509,-350,-153,1000,879,765,-291,125,451,-178,519,1000,-284,190,1000,-1000,639,636,-1000,-570,652,-1000,-139,-1000,1000,255,1000,423,461,370,466,-444,-492,1000,354,720,20,-1000,1000,-86,-195,517,393,217,1000,-643,746,813,-15,-551,-226}));
    }
    public void testDE00040() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,-862,139,901,-1000,1000,747,1000,-1000,-203,-658,-848,58,-1000,-549,996,442,1000,-688,701,1000,-442,1000,-244,255,-1000,1000,1000,1000,-545,239,894,973,1000,-977,-782,-1000,1000,-830,1000,232,141,218,334,-845,1000,-589,819,304,58,-509,196,1000,-996,-628,93,1000,360,1000,400,278,169,23,521}));
    }
    public void testDE00041() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{1000,-1000,679,741,-1000,1000,-359,-347,650,506,599,-239,470,-875,1000,1000,645,-867,-229,-1000,42,652,-1000,-573,-702,1000,1000,-885,687,-670,698,-286,-564,424,1000,-163,-1000,121,607,289,225,1000,151,887,-1000,784,909,4,-369,1000,325,-1000,1000,-360,929,-1000,-539,1000,1000,1000,-272,-44,-422,-931}));
    }
    public void testDE00042() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,-322,14,-227,150,-363,600,25,245,631,517,-419,307,411,861,932,-610,-157,475,519,-699,82,1000,859,1000,-301,559,1000,65,391,-467,1000,-307,693,317,-562,492,-1000,387,-81,-546,-373,42,70,181,-839,262,901,579,-679,-1000,1000,904,567,-297,1000,-56,-887,300,-254,920,-1000,-110,225}));
    }
    public void testDE00043() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{91,-999,-265,-921,-88,-378,-48,536,679,-959,384,591,-542,52,600,417,311,1000,713,1000,577,324,43,-761,379,288,-745,455,-931,109,-814,582,52,418,157,56,-539,1000,406,-101,-653,-1000,-361,292,-119,-233,379,961,288,95,208,-694,669,-747,-937,-681,349,244,167,-313,735,388,-360,-14}));
    }
    public void testDE00044() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,13,314,507,1000,-200,-444,-203,-152,188,1000,-579,-18,-878,143,-205,-863,-406,338,184,889,1000,1000,816,1000,-1000,1000,462,319,-65,537,1000,-1000,1000,-448,-457,203,-319,597,801,-464,237,729,-231,-723,-311,501,370,-490,559,133,421,-1000,1000,-1000,261,323,-526,-99,562,-346,1000,363,-585}));
    }
    public void testDE00045() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,-362,259,-427,1000,448,368,-468,-6,-339,-374,-915,524,-135,610,1000,213,-55,203,741,18,281,-210,1000,-174,-398,-646,-398,1000,-265,971,-840,-311,1000,-82,-152,353,420,1000,480,-706,-224,-1000,-1000,183,-153,0,889,-739,748,-407,136,102,-784,-1000,703,-618,-457,-1000,345,566,734,-1000,-613}));
    }
    public void testDE00046() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-739,-976,-473,94,-1000,182,-709,822,306,-146,-7,400,327,144,-356,427,375,-543,-335,956,51,-563,133,775,-563,1000,-1000,-456,-1,-512,1000,400,309,-874,-186,-235,-838,-1000,-789,-327,933,-1000,45,-502,-764,500,148,-458,526,-98,-1000,-27,-984,-203,170,391,338,-755,-746,-327,238,913,560,1000}));
    }
    public void testDE00047() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-281,1000,408,-967,-1000,-1000,-178,-53,660,941,857,1000,-1000,337,-1000,-761,-37,-832,1000,1000,1000,-655,146,-25,-92,-1000,-994,-1000,-883,1000,571,956,-1000,1000,1000,-1000,-525,204,-699,1000,1000,-1000,486,-1000,-1000,-608,645,-1000,-1000,1000,-1000,-30,-1000,-1000,-420,1000,-299,-1000,-1000,-1000,-1000,1000,228,842}));
    }
    public void testDE00048() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-94,407,184,-1000,643,-896,250,1000,-1000,857,-847,865,-807,-229,76,-283,798,-1000,751,542,1000,-992,186,-809,1000,696,604,368,-209,1000,1000,1000,-854,797,1000,-859,-221,-1000,-336,1000,727,28,-169,-271,158,-696,605,-90,-835,-593,-365,-1000,662,1000,898,469,-823,269,-191,813,-775,699,-1000,601}));
    }
    public void testDE00049() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{311,-1000,-997,-666,-1000,-324,-1000,-204,-230,23,474,-1000,-613,492,-1000,1000,38,-1000,344,-528,333,1000,887,-96,90,-1000,296,-589,-8,953,-1000,-1000,645,475,724,-580,-1000,1000,320,341,1000,-184,1000,-1000,426,221,-640,-880,1000,641,-1000,1000,-1000,-790,705,825,-298,381,-128,-540,-1000,622,1000,619}));
    }
    public void testDE00050() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{693,755,-841,-83,-175,509,351,726,-1000,-343,283,-1000,201,407,541,-1000,-14,1000,-1000,368,-221,763,49,856,357,1000,1000,641,-1,247,-1000,-1000,1000,-2,-1000,530,292,-903,-1000,-103,-94,537,425,1000,1000,141,-379,-234,1000,808,120,-638,1000,-213,436,296,-671,-131,382,454,1000,-14,-58,-208}));
    }
    public void testDE00051() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{445,615,696,-1000,-1000,-1000,-566,-1000,-43,571,1000,789,-1000,-27,-33,-1000,775,-368,1000,409,388,-66,1000,-222,-73,-1000,818,448,-1000,1000,77,924,-1000,-199,1000,-1000,-26,1000,271,1000,904,-376,-823,-289,607,-695,-303,-492,-1000,1000,92,-30,-978,1000,-989,531,-1000,495,187,-780,-1000,533,207,461}));
    }
    public void testDE00052() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-113,-1000,168,463,-727,346,-1000,1000,-786,-1000,414,-1000,192,1000,1000,542,-1000,-190,-1000,-222,-1000,758,167,994,400,1000,-509,-4,-1,-1000,334,-1000,606,-1000,-1000,-235,-644,-786,-823,-1000,409,45,1000,400,283,1000,-793,-505,499,-24,-37,1000,-937,-912,1000,-556,53,104,399,-554,357,882,1000,554}));
    }
    public void testDE00053() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-465,-1000,-534,-85,-1000,218,1000,1000,-131,223,1000,-582,-300,749,-1000,-965,1000,-1000,353,-234,595,297,467,41,-291,-1000,254,-63,412,-1000,638,1000,-524,-518,-241,1000,518,1000,-42,-722,129,633,-429,1000,-855,1000,-1000,-235,768,827,-1000,-1000,-243,-267,1000,-1000,1000,-67,685,-398,-357,-1000,333,362}));
    }
    public void testDE00054() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{935,548,-531,-911,-198,-105,-839,20,687,336,981,552,-877,-469,147,415,-39,781,980,1000,480,962,947,1000,682,-1000,-559,687,27,1000,-655,557,-454,-1000,448,1000,-599,1000,-262,-751,-128,1000,-283,-654,-750,-874,-638,21,-132,-823,-1000,489,1000,-758,-41,1000,-644,762,-863,1000,830,145,1000,629}));
    }
    public void testDE00055() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-269,-727,399,891,-481,689,1000,1000,-462,496,140,-107,39,380,-449,-493,695,-415,-876,754,768,105,1000,408,7,-222,1000,869,696,-1000,761,33,137,-119,-119,41,223,875,-865,-919,28,704,-609,604,-313,1000,213,413,462,1000,-1000,-812,863,-790,881,-1000,913,-96,-161,-189,-1000,-699,494,-374}));
    }
    public void testDE00056() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{202,684,851,293,-757,-673,533,619,-355,50,-637,799,-221,-1000,-1000,-1000,-472,31,-63,1000,-508,-21,1000,420,765,110,-1000,638,534,-621,596,-933,475,-559,-244,85,371,1000,-591,-342,-750,-1000,515,798,1000,747,466,1000,-96,337,-859,546,1000,1000,-738,-1000,440,92,577,142,-392,-45,-1000,-1000}));
    }
    public void testDE00057() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-969,729,469,430,-1000,246,567,106,-327,-1000,402,-54,179,230,-544,-64,-918,-816,576,-499,815,-1000,-225,373,-582,-486,107,122,-1000,-828,-966,299,-684,219,-464,94,-395,757,52,-447,133,463,990,-293,-1000,-181,-125,-990,-526,-114,487,-408,-276,1000,1000,-818,1000,-317,967,-952,397,-106,-527,257}));
    }
    public void testDE00058() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{1000,-758,-801,-468,1000,-536,-169,1000,1000,-1000,918,302,-121,-262,993,-412,-1000,-402,-755,-175,618,664,-813,-600,-732,-78,639,-911,103,1000,-301,-278,-1000,42,-1000,-853,727,1000,-1000,590,68,90,-1000,1000,43,-172,-23,-976,85,-13,-436,-502,273,-429,888,-964,-28,589,0,-541,-670,-755,-1000,1000}));
    }
    public void testDE00059() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{374,-905,-356,-308,12,191,478,-1000,391,-1000,-810,-283,-280,199,199,1000,1000,1000,115,164,550,-1000,742,577,1000,-339,45,652,106,-949,6,662,-318,-191,1000,-334,368,309,-684,-446,526,-122,368,736,-758,-61,1000,1000,-191,-555,-1000,-1000,-452,1000,-1000,381,-538,738,-432,149,-415,-797,-866,119}));
    }
    public void testDE00060() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{796,-536,174,-978,338,415,306,-912,811,-355,1000,-522,-721,568,700,-373,-362,694,668,120,-359,1000,205,28,-77,455,-75,178,-75,-2,-308,169,-690,-275,-286,-595,-526,473,1000,1000,172,-676,-608,529,23,-100,-538,-529,528,-605,-711,-76,-160,307,1000,-801,569,1000,-492,-782,539,-908,577,-591}));
    }
    public void testDE00061() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-183,-280,204,806,121,570,640,-599,-749,557,1000,-740,537,321,260,-316,-433,-158,-2,859,-223,642,-449,-1000,407,237,671,776,741,-700,868,486,80,-218,1000,-33,113,331,378,-496,478,-212,795,900,-725,-1000,734,-920,269,-934,-553,-97,-882,1000,-947,611,693,-165,289,-452,501,-2,-52,516}));
    }
    public void testDE00062() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{696,831,879,747,-120,1000,881,218,-432,-270,1000,351,-1000,-457,-97,807,-144,129,167,-912,-208,669,-417,595,-235,654,-1000,-489,-510,-1000,602,781,212,839,592,1000,-213,644,-379,-570,-333,-1000,650,626,-921,350,1000,1000,-120,650,384,-1000,-205,-366,1000,502,559,827,-111,-1000,970,-296,-558,320}));
    }
    public void testDE00063() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{854,1000,419,-615,6,391,-202,844,-1000,51,-580,186,-396,-1000,1000,-532,-723,562,49,742,-14,-1000,-744,-582,689,1000,903,-606,1000,1000,1000,1000,-748,-436,322,1000,-1000,1000,517,1000,-626,-1000,-141,-1000,1000,-44,-137,-204,-406,272,-1000,1000,339,442,554,387,-1000,763,973,-1000,-1000,-470,568,-190}));
    }
    public void testDE00064() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{-288,794,354,156,296,-107,-847,-123,719,-141,-727,-439,-247,-289,1000,411,-632,253,-724,196,-344,-400,-252,-379,-191,536,84,-804,1000,-331,-103,270,-516,-483,664,369,-1000,516,-183,-1000,-1000,432,427,63,597,-225,436,-555,-456,-537,-554,-573,-860,-241,253,387,-1000,-302,-397,-352,-709,-858,-157,1000}));
    }
    public void testDE00065() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{650,792,-536,-449,-102,442,1000,420,-808,-560,-788,-622,153,-1000,1000,1000,-567,399,-756,19,-291,497,-410,462,-329,1000,-485,-828,750,-399,1000,-218,-280,-570,150,1000,-405,-15,491,-187,329,-849,-627,-375,-1000,-1000,250,-701,969,527,21,572,-398,500,389,1000,-188,-21,-617,-785,-1000,-1000,389,1000}));
    }
    public void testDE00066() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{196,1000,679,-1000,-1000,1000,1000,-406,144,51,432,-849,-658,-1000,1000,35,-723,764,865,742,-294,-475,-744,-582,1000,1000,46,-841,-321,621,-586,1000,-521,159,-403,-226,-1000,-553,443,-674,-1000,-972,-141,106,1000,567,-1000,-693,1000,191,178,44,-21,-1000,115,172,-1000,-332,837,572,-1000,-623,-265,1000}));
    }
    public void testDE00067() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-275,-235,-562,-65,-321,47,-281,347,-1000,-704,-291,139,-1000,768,-232,-63,691,-589,-4,-159,794,674,-959,1000,452,-300,392,-924,235,1000,742,-480,-460,-258,485,403,231,651,-137,-1000,103,416,140,54,800,309,-18,311,520,-239,151,320,-637,551,-29,45,187,302,-903,574,835,974,252,-383}));
    }
    public void testDE00068() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{160,26,-206,-510,-1000,-543,816,1000,-1000,1000,-1000,1000,-482,1000,1000,1000,-52,484,-836,1000,109,141,-1000,1000,-446,408,-1000,-1000,-102,-1000,1000,1000,-1000,709,1000,-314,-392,-606,-546,-99,1000,-606,-1000,-26,1000,380,214,-1000,-1000,178,-1000,596,987,-151,-645,-388,507,49,-613,844,1000,741,-543,-172}));
    }
    public void testDE00069() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{736,-1000,435,-1000,214,-148,398,-253,1000,-284,-189,-951,566,438,-119,-743,116,46,-112,-624,-1000,893,-1000,789,-120,-489,-255,-826,-196,1000,643,-434,-1000,-1000,312,1000,1000,1000,-703,-854,-345,1000,-391,-611,-148,-1000,1000,1000,-341,-91,-994,-96,-493,139,-181,775,966,-612,745,-364,-131,485,-287,1000}));
    }
    public void testDE00070() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-4,205,1000,135,599,353,708,-887,-1000,223,-1000,-585,-783,-631,1000,-490,-1000,539,1000,1000,-1000,230,276,1000,596,-91,384,57,-959,-357,-1000,782,-484,593,1000,482,1000,-591,-1000,-436,582,-669,73,-133,-314,1000,531,-1000,-741,-690,67,-1000,-48,1000,6,-1000,-322,376,1000,-1000,-1000,756,-237,-561}));
    }
    public void testDE00071() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{1000,-546,454,-109,1000,-380,-679,-729,-282,-382,-958,-38,-147,-784,970,91,1000,129,-27,572,-603,37,959,380,1000,-325,-653,-29,117,-445,-933,151,371,-145,-202,-585,334,760,-895,565,40,-332,-462,-546,-792,-89,-262,-339,1000,-463,443,-318,-8,1000,374,-687,-236,-801,-1000,-558,-224,786,-982,422}));
    }
    public void testDE00072() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{947,954,-218,501,183,307,-399,726,265,-535,988,814,1000,932,-188,-534,598,987,-179,-24,576,-244,-33,780,1000,792,237,-376,-573,-281,630,1000,-996,828,-218,-107,1000,1000,-796,15,1000,573,1000,-1000,-224,1000,1000,-531,-184,481,1000,387,429,-569,182,-657,1000,-1000,390,680,16,1000,1000,-1000}));
    }
    public void testDE00073() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-560,102,-115,-175,-736,13,1000,529,762,-437,104,-1000,-1000,1000,-594,1000,-836,133,1000,-1000,-795,580,-41,-957,-182,-262,1000,-228,-1000,-528,417,-332,-33,-1000,214,20,1000,-1000,1000,1000,-1000,1000,213,-432,-111,759,-1000,1000,1000,1000,362,-430,-53,1000,-1000,250,-20,18,-694,566,172,-43,-890,300}));
    }
    public void testDE00074() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-1000,495,-181,-669,-1000,-378,712,362,-578,172,37,-473,645,-505,38,311,1000,-322,47,27,1000,-1000,-808,319,586,969,-2,-1000,1000,-234,477,153,236,844,657,1000,-774,-222,-722,228,-662,-661,-1000,-232,-238,1000,592,687,-407,-684,913,-1000,-431,-604,459,969,-68,-254,1000,517,-1000,-1000,-312,-9}));
    }
    public void testDE00075() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{208,-1000,-64,-19,-1000,-633,899,-542,501,279,-488,126,49,204,-505,505,-408,1000,558,523,575,-613,398,-51,-298,129,480,-782,-543,-631,-390,435,-499,428,-522,32,-343,-158,-107,-614,-782,-19,-632,804,43,-258,891,124,797,2,-291,-481,382,-352,1000,-356,-292,608,-762,330,-838,889,146,115}));
    }
    public void testDE00076() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{776,-1000,-421,-230,-851,-1000,1000,1000,464,515,-919,37,12,443,-941,638,-664,701,434,600,1000,-958,636,-253,399,-366,-70,1000,627,419,-682,704,-1000,1000,-1000,828,-1000,-6,379,-1000,-856,1000,-909,992,139,-473,1000,337,128,1000,-167,-824,634,-586,1000,184,-1000,-737,-1000,306,-1000,269,327,920}));
    }
    public void testDE00077() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-85,262,147,-827,106,-277,430,496,236,-247,-297,-1000,-71,397,-475,431,359,-1000,1000,-1000,-478,1000,908,-1000,1000,-1000,-488,-799,-598,-12,730,469,115,-1000,1000,-569,504,-853,439,877,-696,330,-487,-718,140,1000,-1000,54,294,790,1000,-629,-143,1000,-1000,585,-597,-1000,-431,-1000,616,102,-1000,1000}));
    }
    public void testDE00078() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{527,871,-64,-334,-885,-606,801,-860,776,279,-604,483,396,204,-737,30,-213,193,411,66,224,-673,-838,330,-945,-468,211,-798,-623,199,-845,858,-358,-58,-424,-612,289,115,-164,-614,-987,923,-761,815,-876,-112,891,124,602,280,-291,-481,905,-328,337,38,389,232,-533,-314,-760,574,11,-757}));
    }
    public void testDE00079() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-1000,1000,123,-866,-264,-504,321,925,873,502,87,-899,-921,-129,577,-229,-95,-641,822,400,-1000,1000,1000,-383,1000,-888,1000,-621,-1000,297,840,225,1000,-817,978,368,-343,1000,1000,1000,-903,1000,-267,-1000,-933,3,-1000,951,1000,997,1000,-867,-748,1000,-1000,423,-85,-710,289,-41,572,-720,562,144}));
    }
    public void testDE00080() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{1000,-1000,741,-862,473,-572,1000,779,18,-1000,73,-247,-706,1000,-580,220,172,106,850,-1000,-1000,730,-628,183,-547,215,535,1000,317,-505,-560,55,-948,-1000,780,188,1000,-1000,320,-619,-963,1000,1000,-1000,477,448,-1000,-113,286,1000,710,449,-863,1000,-1000,457,-500,-1000,-1000,-848,1000,-145,-1000,882}));
    }
    public void testDE00081() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-169,-110,899,-346,516,-42,-130,125,-579,115,-213,-51,-157,-707,592,-283,-776,61,292,-298,-760,198,-1000,-618,-413,-319,1000,1000,-67,846,-344,-943,204,148,563,-218,-229,707,382,-229,-362,317,1000,-693,-196,-881,-711,361,-146,-286,-632,-351,375,188,-303,-1000,-51,1000,-374,256,769,901,-1000,-963}));
    }
    public void testDE00082() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{277,-405,948,1000,-49,321,-326,1000,-1000,-463,-1000,441,1000,213,875,-1000,-35,-917,782,-327,1000,595,655,-166,-1000,-198,-1000,653,-870,-116,-1000,-1000,-2,789,-1000,-743,971,-195,-328,682,-1000,1000,102,-1000,118,165,1000,-384,-779,1000,1000,-419,-653,111,159,-526,-322,1000,-541,550,1000,813,-486,-469}));
    }
    public void testDE00083() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-777,-16,899,-200,2,-118,641,-504,-210,-386,52,400,763,-321,235,-47,51,-283,-950,-524,-111,91,-954,410,404,549,386,104,266,137,78,-692,636,-120,-344,-96,-216,329,243,-375,-995,296,1000,-852,-112,-148,-170,-16,112,-99,-323,-529,490,-771,751,336,-187,1000,-1000,-365,571,-53,-657,-955}));
    }
    public void testDE00084() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{767,-982,-91,872,-122,126,-862,187,74,-743,-825,561,275,542,834,-977,655,-761,811,-666,942,228,393,32,-316,687,-702,-312,-612,-601,-783,-795,-520,726,-602,-517,-24,-804,-909,580,-615,842,-908,-379,929,-10,738,-861,-544,984,-286,-734,-429,-456,-185,268,-552,-146,398,269,551,-689,701,695}));
    }
    public void testDE00085() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-487,67,-102,-1000,674,-742,537,-915,-149,-980,116,451,1000,-1000,645,764,334,495,-86,-811,-445,1000,-934,1000,526,533,245,-999,37,824,155,-208,-569,236,-1000,-140,-604,-515,243,145,-335,-365,448,-346,269,-590,-838,-36,161,288,-103,-871,1000,-1000,371,157,-1000,1000,-110,-452,-400,-417,-510,-70}));
    }
    public void testDE00086() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-1000,57,11,1000,-1000,687,-912,1000,962,-17,-959,472,685,-322,-1000,1000,780,-584,1000,-1000,992,728,218,-1000,-277,595,-117,938,1000,-531,-87,-1000,-644,-63,-1000,-965,-927,1000,-185,-1000,-1000,-777,-4,-1000,1000,81,1000,-1000,-283,-764,829,-408,-1000,-148,-1000,1000,-1000,-1000,1000,-887,-900,547,446,-1000}));
    }
    public void testDE00087() {
        assertEquals("VOID|getArgName=java.lang.String:NzQ4ZA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setArgName(java.lang.String):void",
            new int[]{627,964,-251,-896,717,665,297,-120,-46,-231,539,-889,-139,82,-464,309,-489,167,-680,683,647,-763,-695,748,-708,218,185,143,-501,-240,716,303,-97,-717,48,-660,-361,747,-778,-492,797,-197,309,977,113,-818,290,-717,862,782,-787,-221,-980,-274,445,702,-301,-85,887,-957,556,27,918,328}));
    }
    public void testDE00088() {
        assertEquals("VOID|getArgName=java.lang.String:LTB4ODA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setArgName(java.lang.String):void",
            new int[]{835,1000,-521,-1000,-656,-499,934,-417,-363,1000,1000,-51,-1000,725,1000,1000,749,-1000,-750,1000,-1000,845,981,858,-1000,-874,209,-1000,-1000,474,-269,-492,-1000,1000,74,1000,1000,-124,658,1000,1000,159,887,-159,187,1000,-180,-775,453,352,688,783,-1000,1000,-1000,-151,-1000,1000,1000,1000,-505,-561,-1000,1000}));
    }
    public void testDE00089() {
        assertEquals("VOID|getDescPadding=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setDescPadding(int):void",
            new int[]{-820,-593,19,510,-418,663,-855,-668,417,1000,-329,-243,346,382,1000,487,-1000,-155,653,1000,-1000,-295,1000,295,-48,-355,-1000,228,-29,-363,-1000,305,78,-379,-262,-341,-300,481,939,563,-498,-320,-161,209,87,-227,28,-587,799,1000,-1000,159,-1000,-1000,-1000,-390,1000,1000,543,64,646,584,518,160}));
    }
    public void testDE00090() {
        assertEquals("VOID|getDescPadding=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setDescPadding(int):void",
            new int[]{-375,-657,-6,-201,340,-604,-578,769,-421,170,-418,-999,819,1000,-322,475,809,282,366,171,-805,-59,242,1000,232,-106,230,-438,-979,210,236,-547,-182,238,53,-5,-850,65,-791,979,771,-762,1000,-586,1000,1000,-740,1000,-588,83,-815,1000,-201,710,275,247,176,333,726,112,280,474,-288,-487}));
    }
    public void testDE00091() {
        assertEquals("VOID|getDescPadding=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setDescPadding(int):void",
            new int[]{-874,173,4,3,-1000,-8,-956,-769,8,39,-897,357,-74,897,1000,1000,-761,-489,607,1,199,-130,1000,632,-348,-1000,-1000,1000,228,-746,-1000,389,1000,321,-268,-473,-290,1000,357,-230,164,637,164,-1000,670,421,-1000,-855,-334,716,-13,712,-1000,-400,-1000,168,974,870,982,-255,-412,999,978,-369}));
    }
    public void testDE00092() {
        assertEquals("VOID|getLeftPadding=java.lang.Integer:NDI0", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLeftPadding(int):void",
            new int[]{743,921,674,991,428,-694,78,859,888,-15,76,533,455,-923,-431,-411,415,424,407,-637,284,-170,100,-190,440,747,400,758,484,-635,156,969,-994,292,-223,-435,-557,-391,373,914,-334,1,-826,-152,-419,853,179,-790,-432,748,-836,178,880,625,-850,-488,-707,-981,214,995,-797,253,158,638}));
    }
    public void testDE00093() {
        assertEquals("VOID|getLeftPadding=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLeftPadding(int):void",
            new int[]{1000,-136,-191,469,1000,-1000,27,912,1000,506,-308,673,1000,-1000,-487,-282,-357,855,759,-1000,-508,335,994,-184,1000,20,1000,879,1000,-1000,357,1000,-1000,-227,-969,-1000,-1000,-205,856,1000,414,142,-931,191,449,1000,-218,-880,-1000,1000,-1000,562,1000,1000,-81,-1000,-511,-1000,925,1000,-1000,598,345,1000}));
    }
    public void testDE00094() {
        assertEquals("VOID|getLeftPadding=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLeftPadding(int):void",
            new int[]{142,781,334,-699,-1000,-784,-957,-700,703,-1000,36,-481,246,237,145,685,-831,515,-1000,111,70,837,1000,720,658,1000,54,45,-101,-508,1000,-637,-1000,-922,-632,696,292,-687,413,-220,572,-1000,-913,245,264,-474,-1000,461,652,306,137,-495,29,-276,332,381,78,-126,416,-673,587,8,1000,-1000}));
    }
    public void testDE00095() {
        assertEquals("VOID|getLongOptPrefix=java.lang.String:NDQxZTQ2MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLongOptPrefix(java.lang.String):void",
            new int[]{-166,135,234,-958,807,1000,-143,584,-1,-37,288,-122,879,1000,-598,204,-58,-441,-1000,461,-667,327,-1000,-975,16,430,-665,427,335,697,-1000,737,546,-79,-1000,-934,-499,-1000,-40,-98,141,-501,-487,-214,-659,-307,307,386,713,-52,-346,-19,-1000,-111,78,221,-1000,-1000,-294,1000,-250,1000,-437,1000}));
    }
    public void testDE00096() {
        assertEquals("VOID|getLongOptPrefix=java.lang.String:NzU2", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLongOptPrefix(java.lang.String):void",
            new int[]{-1000,1000,-136,1000,449,642,781,767,-1000,-45,-192,-770,947,95,-1000,1000,-797,782,-1000,346,354,-74,-1000,-336,-717,756,-411,-179,306,459,-1000,-284,-274,-522,-1000,-1000,1000,-513,-1000,871,265,-330,-704,658,-1000,-340,282,1000,1000,410,1000,-1000,470,854,-358,1000,-1000,-1000,-1000,660,-1000,1000,-890,383}));
    }
    public void testDE00097() {
        assertEquals("VOID|getLongOptPrefix=java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLongOptPrefix(java.lang.String):void",
            new int[]{2,-31,109,1000,1000,-1000,200,-313,-959,-1000,1000,-1000,600,-702,1000,-776,-1000,-410,-1000,1000,1000,650,1000,-849,1000,694,1000,-457,70,-260,1000,-607,991,-381,358,1000,500,1000,539,-448,1000,-43,439,-352,-24,368,-1000,322,208,-546,528,-400,-276,-793,599,1000,-1000,1000,1000,1000,1000,-1000,1000,-1000}));
    }
    public void testDE00098() {
        assertEquals("VOID|getNewLine=java.lang.String:TmFO", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setNewLine(java.lang.String):void",
            new int[]{-708,190,234,-835,320,-438,-462,732,-90,-751,573,-937,-117,-923,-391,-986,-646,571,149,896,403,18,-143,427,-833,463,-328,-124,499,911,518,976,-951,371,274,-652,-159,-151,-581,407,-733,914,134,31,771,-977,545,-356,458,-528,-746,747,341,-509,-649,483,-977,-174,-308,-705,-770,939,377,422}));
    }
    public void testDE00099() {
        assertEquals("VOID|getNewLine=java.lang.String:IC0tMTAwMCA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setNewLine(java.lang.String):void",
            new int[]{240,-13,-231,-596,-1000,-1000,-861,346,1000,-769,-954,-1000,410,17,195,778,1000,-181,-30,-177,559,1000,-476,530,39,-19,-1000,-411,406,-548,1000,421,-437,838,721,-1000,-668,680,1000,1000,-169,67,982,-722,733,992,-439,81,185,-977,-690,-959,465,13,290,-571,-1000,296,-944,-718,-1000,1000,278,803}));
    }
    public void testDE00100() {
        assertEquals("VOID|getNewLine=java.lang.String:LS0weDgwMDAw", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setNewLine(java.lang.String):void",
            new int[]{-711,583,-966,987,-1000,953,355,-571,-787,-929,386,-1000,25,-1000,-701,-395,-18,1000,1000,271,464,-319,82,-1000,-1000,-1000,1000,250,406,442,1000,-131,947,-498,962,938,1000,-1000,360,-1000,-643,-8,44,-893,-1000,-935,1000,-1000,-1000,-1000,-457,1000,-29,-353,-1000,747,-870,-1000,596,1000,915,-302,316,-406}));
    }
    public void testDE00101() {
        assertEquals("VOID|getOptPrefix=java.lang.String:LTg1Ny4w", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptPrefix(java.lang.String):void",
            new int[]{1000,832,409,1000,899,-47,-195,-1000,-508,1000,-1000,-1000,-489,1000,-930,-539,-857,709,1000,-261,-139,35,1000,-1000,854,-1000,-1000,1000,-276,-591,-1000,495,1000,282,1000,1000,-1000,236,830,-36,734,-1000,1000,862,-944,-962,-512,-192,-828,1000,-416,-1000,-1000,504,854,842,1000,190,-1000,-757,-702,1000,-1000,-456}));
    }
    public void testDE00102() {
        assertEquals("VOID|getOptPrefix=java.lang.String:", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptPrefix(java.lang.String):void",
            new int[]{784,-1000,-331,-241,425,-531,247,797,-208,1000,-712,151,-715,676,16,240,-1000,962,209,-677,26,43,1000,-121,510,-425,-410,1000,1000,-268,108,-146,306,-300,1000,68,662,-1000,-1000,-348,895,-442,472,996,-589,1000,-511,-457,1000,153,-1000,-780,-203,608,330,1000,706,-535,33,1000,439,616,343,433}));
    }
    public void testDE00103() {
        assertEquals("VOID|getOptPrefix=java.lang.String:IDExNiA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptPrefix(java.lang.String):void",
            new int[]{530,587,-1,128,530,1000,213,40,-826,828,-416,590,144,1000,-355,-655,-79,-310,-116,-1000,-298,498,399,-957,920,-1000,383,1000,-1000,-965,160,879,1000,400,-152,780,1000,744,182,254,929,960,-175,-1000,17,-446,-1000,495,261,-400,-1000,-88,733,482,709,872,1000,497,441,-1000,534,-223,-564,146}));
    }
    public void testDE00104() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptionComparator(java.util.Comparator):void",
            new int[]{293,-223,639,116,168,355,-612,73,-184,-359,512,824,401,2,947,210,-755,114,-497,-964,-968,223,501,-576,620,-456,514,-573,-343,859,-224,411,64,-969,-714,-841,13,-127,668,-899,48,-466,444,655,642,456,-455,-735,-315,-214,578,-616,-883,129,-310,-602,261,527,680,301,-710,-386,438,-256}));
    }
    public void testDE00105() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptionComparator(java.util.Comparator):void",
            new int[]{1000,-260,524,533,-143,980,-1000,-687,-972,1000,886,-872,-203,438,-199,-1000,-529,598,182,-174,209,-886,-76,-460,510,-934,232,-639,-698,-837,334,-1000,31,506,484,-837,844,375,-1000,-391,622,-817,970,1000,772,-663,-757,1000,471,1000,-137,-857,1000,-949,757,388,543,-351,-62,271,-472,-281,-876,-544}));
    }
    public void testDE00106() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptionComparator(java.util.Comparator):void",
            new int[]{-234,-209,359,471,-1000,-261,-1000,-977,-238,-70,27,126,943,-744,377,-782,1000,-477,105,-676,191,-400,1000,458,-1000,565,-798,-1000,-747,185,478,-658,-1000,377,-292,-568,-402,891,-408,-1000,763,434,-324,172,-1000,-693,-1000,1000,264,-57,808,-460,126,-1000,1000,321,293,607,-385,400,371,705,-230,-596}));
    }
    public void testDE00107() {
        assertEquals("VOID|getSyntaxPrefix=java.lang.String:LTB4ODAwMDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setSyntaxPrefix(java.lang.String):void",
            new int[]{735,520,359,-1000,-524,41,459,-1000,290,-171,68,604,1000,820,-210,-328,876,985,535,820,160,-731,856,-891,306,97,-1000,176,858,388,385,-571,977,964,-380,-331,-792,-239,250,333,143,553,204,-959,763,-560,-394,453,478,-368,1000,379,1000,293,486,-619,416,770,365,598,-552,581,484,548}));
    }
    public void testDE00108() {
        assertEquals("VOID|getSyntaxPrefix=java.lang.String:MjE2ZS01MTE=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setSyntaxPrefix(java.lang.String):void",
            new int[]{302,-508,-996,645,-139,668,846,545,-383,-241,-1000,-886,138,780,617,261,1000,534,216,1000,-511,-529,1000,-635,1000,-756,-197,730,581,4,629,-1000,479,478,-150,-917,36,-922,1000,1000,1000,-41,-447,-280,1000,613,-1000,281,507,-1000,15,-492,475,1000,1000,45,1000,1000,192,1000,-886,-535,296,1000}));
    }
    public void testDE00109() {
        assertEquals("VOID|getSyntaxPrefix=java.lang.String:MHgzZTg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setSyntaxPrefix(java.lang.String):void",
            new int[]{360,64,-6,-903,-1000,-522,1000,-662,335,-1000,-769,109,-652,58,-621,-386,1000,106,-1000,1000,1000,523,1000,-324,-617,1000,-1000,635,-86,1000,1000,-153,1000,1000,425,-1000,959,582,-365,-204,48,87,40,767,1000,-522,-1000,1000,35,257,-304,-182,-183,-522,-126,-1000,-914,888,-880,1000,-436,-73,1000,1000}));
    }
    public void testDE00110() {
        assertEquals("VOID|getWidth=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setWidth(int):void",
            new int[]{785,120,354,482,646,43,-460,1000,-426,1000,-271,640,880,-620,526,89,121,-126,502,-479,552,983,1000,95,-214,-526,1000,169,434,-866,531,-34,-158,108,-679,123,-302,-583,24,-200,257,685,1000,604,-306,574,-712,200,100,152,466,425,550,1000,571,-327,1000,168,-406,203,-296,-903,-1000,-981}));
    }
    public void testDE00111() {
        assertEquals("VOID|getWidth=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setWidth(int):void",
            new int[]{685,581,-816,-117,435,-945,-840,-96,110,-162,-731,112,-721,469,-399,724,-78,718,340,-529,688,701,719,-233,-217,-286,-852,-140,-388,-673,825,206,520,-366,281,811,-517,-291,709,620,463,327,-291,-841,284,756,108,586,-676,77,901,61,139,858,-976,-70,-818,-717,-305,585,-799,834,-689,83}));
    }
    public void testDE00112() {
        assertEquals("VOID|getWidth=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setWidth(int):void",
            new int[]{-238,-1000,529,-426,505,1000,303,200,1000,850,274,-170,729,-1000,-400,-1000,1000,-1000,1000,-868,-955,1000,778,1000,1000,844,-258,250,-400,-1000,-418,-98,-1000,1000,-695,-100,-589,-1000,1000,-309,-147,1000,-247,540,-1000,1000,-529,166,1000,949,1000,1000,1000,1000,-347,-1000,-217,1000,-579,-1000,717,615,-235,943}));
    }
}
