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
            new int[]{365,1000,999,645,1000,-114,909,-686,-309,335,198,-1000,84,489,-1000,-216,-1000,-514,-1000,-376,851,-1000,1000,-1000,-509,-1000,-1000,-55,-1000,1000,-716,773,-373,-1000,-1000,-1000,210,-39,962,-212,-724,412,-707,697,1000,1000,659,13,287,196,281,1000,352,709,164,-1000,382,-207,1000,-189,-28,-1000,-340,426}));
    }
    public void testDE00001() {
        assertEquals("java.lang.String:LS0weDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{1000,-742,-186,146,526,198,-1000,433,650,-27,-1000,-1000,-225,-520,-824,743,-662,-440,531,-400,754,455,-1000,45,955,892,-97,336,-638,668,-875,550,-53,-571,-35,-487,328,1000,-210,1000,-735,-517,-1000,99,206,1000,-280,1000,715,1000,342,-12,-924,815,43,1000,1000,-1000,-151,-1000,69,-817,-603,8}));
    }
    public void testDE00002() {
        assertEquals("java.lang.String:YXJn", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-1000,1000,34,-1000,-1000,-1000,-210,-67,-872,-1000,148,1000,1000,753,1000,146,1000,-12,1000,1000,450,1000,-1000,1000,-1000,1000,1000,-513,1000,-1000,-1000,127,-994,1000,1000,1000,1000,-1000,-201,86,1000,1000,-1000,233,-1000,-1000,-1000,842,1000,-758,-1000,-1000,1000,-1000,-1000,273,-1000,-721,-649,-1000,-533,262,111,-895}));
    }
    public void testDE00003() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-1000,-458,-546,367,-542,-298,404,-1000,210,355,-354,-223,-388,394,1000,121,-732,-941,-854,-27,536,-83,1000,908,-296,-874,-363,-57,-84,100,-888,329,-538,560,1000,-133,-186,753,-43,-365,-351,171,114,-51,-343,-357,763,135,686,-171,100,829,-390,263,758,-817,737,-239,-352,-106,707,-403,-815,-272}));
    }
    public void testDE00004() {
        assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-760,-862,-121,-341,1000,1000,390,964,-1000,315,-257,-189,173,-951,-778,-640,-731,1000,-166,56,-777,513,-997,998,489,317,458,-702,-637,-1000,-1000,372,557,1000,-648,769,-604,379,82,-683,-1000,-947,-775,-116,1000,97,631,59,-1000,-503,-54,-1000,-1000,1000,720,286,52,-1000,740,1000,169,-215,-911,1000}));
    }
    public void testDE00005() {
        assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-81,-1000,369,279,-474,528,361,-1000,12,147,799,-1000,-588,1000,413,692,518,573,749,-481,-272,941,-278,1000,477,545,-100,-729,111,-34,1000,-390,-45,13,-1000,-295,699,1000,-124,305,-809,-112,425,547,898,-1000,979,559,1000,96,-1000,1000,-1000,-624,338,-1000,-535,729,-1000,-1000,-721,-616,1000,-80}));
    }
    public void testDE00006() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{914,188,504,790,-76,126,-1000,510,485,-4,440,992,-696,373,1000,1000,66,9,-212,-313,-781,753,-230,121,-1000,1000,980,546,559,-717,-790,790,714,-638,814,-102,570,-301,-968,-299,241,-916,-192,-680,-499,199,-773,-326,-384,230,-239,494,195,-1000,-821,660,973,-1000,-114,815,-260,-40,-764,-286}));
    }
    public void testDE00007() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-903,-66,529,576,-93,-778,-345,-267,1000,-907,1000,-326,-986,-611,105,-460,-339,715,-667,-1000,377,-162,-1000,380,-1000,1000,-1000,-1000,1000,379,-281,1000,371,-1000,-717,-911,1000,553,1000,-410,-1000,-1000,85,28,248,-1000,-1000,-237,-708,488,315,659,435,65,-266,-526,-544,1000,919,206,924,-1000,-425,118}));
    }
    public void testDE00008() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-27,-523,774,933,-28,-828,-850,248,364,-351,585,-593,-215,-320,-1000,1000,-1000,659,-735,-608,-127,950,-460,-418,-1000,1000,-1000,-115,499,131,469,1000,742,-620,-273,39,1000,-95,1000,-1000,-644,-294,138,1000,-339,-223,600,-403,-468,694,-790,-179,1000,-922,51,909,150,1000,1000,-134,638,-1000,-458,-260}));
    }
    public void testDE00009() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{830,-616,-16,-803,1000,-1000,1000,1000,1000,1000,796,-93,-241,-1000,1000,-1000,-1000,1000,857,158,635,-952,74,526,-650,-329,176,-326,-844,-1000,1000,282,1000,-87,1000,-973,-572,-725,775,-972,1000,1000,105,1000,-1000,-1000,-31,-777,1000,-562,55,-41,-1000,-361,1000,1000,1000,825,1000,-642,-815,-513,1000,-674}));
    }
    public void testDE00010() {
        assertEquals("java.lang.String:LS0weDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-205,-906,-121,-738,656,-690,839,4,670,912,10,561,307,-687,350,-348,163,-2,389,593,855,-292,353,-280,-493,-149,102,39,-8,-565,1000,215,799,196,1000,-1000,-251,88,114,-363,998,-164,-515,545,-786,-24,-766,85,-279,-735,-323,-698,-216,56,-86,1000,-128,561,402,-553,-1000,369,577,-926}));
    }
    public void testDE00011() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-495,-582,-786,-399,202,199,-400,-94,374,590,-143,38,-524,-21,494,-626,439,-43,-148,-130,420,-440,646,-18,-864,266,126,-544,1000,-70,992,-701,-262,-364,1000,-1000,104,334,212,-171,210,348,1000,163,-781,-17,-803,407,-573,-330,311,-900,25,-162,769,947,-433,-495,-60,-676,-1000,1000,-143,-1000}));
    }
    public void testDE00012() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{-42,932,-831,1000,939,-941,-597,-189,-721,1000,418,507,-351,-1000,1000,-413,-340,609,711,-952,-1000,-85,-567,-853,-358,1000,-1000,-1000,775,-382,-602,865,1000,562,126,-1000,1000,-1000,-140,-710,-801,-259,-195,-1000,159,-268,290,-665,120,-1000,-116,222,-367,530,258,464,1000,710,-706,471,1000,980,-1000,195}));
    }
    public void testDE00013() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{-999,-309,-416,-876,-751,750,57,1000,-1000,-20,-95,219,953,-1000,-1000,1000,1000,419,-677,571,113,917,-709,-81,1000,-364,522,-758,477,-656,-865,-961,1000,-4,753,1000,-147,616,-789,1000,-529,1000,1000,1000,-1000,-895,-608,-217,-1000,-76,1000,1000,-39,265,-1000,-412,-1000,-788,390,-730,-952,-1000,1000,-945}));
    }
    public void testDE00014() {
        assertEquals("java.lang.String:aw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{249,48,-407,-642,-942,859,-796,937,467,-212,-433,-863,465,-399,588,-906,358,621,-558,470,-970,850,-587,196,-358,-36,786,900,777,538,789,-370,59,7,-38,-867,-557,539,668,190,-527,-739,-39,271,-882,88,-486,-863,28,775,-94,-94,-544,780,-797,594,503,971,477,143,-180,-647,-334,588}));
    }
    public void testDE00015() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-870,1000,964,-326,146,-885,382,-395,5,-1000,510,-495,-427,-686,912,-44,-1000,86,1000,-326,-238,-113,251,348,1000,634,423,-659,1000,240,259,-77,-81,-487,-540,-888,-213,-206,887,55,-59,234,-776,313,116,-969,-999,-474,-367,-697,525,1000,-552,-1000,-919,324,-121,-598,129,426,625,-1000,-1000,450}));
    }
    public void testDE00016() {
        assertEquals("java.lang.String:NHE1V3AwMF9aeUlOZHFTS08=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{446,989,-716,-492,-523,320,538,-161,-1000,-430,622,679,370,-142,-757,527,110,664,584,417,-564,-400,502,-439,664,355,-213,846,274,-108,399,901,652,-45,551,188,192,671,1000,-916,-246,267,-400,1000,950,-325,1000,362,-996,-218,480,-663,-1000,-400,-110,1000,-1000,-48,-161,-50,-400,-1000,-179,-921}));
    }
    public void testDE00017() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{939,139,319,686,913,-550,942,-908,1000,224,-1000,199,-1000,648,817,835,489,1000,-1000,1000,-401,-1000,951,-517,1000,1000,-1000,945,91,-1000,1000,-720,-1000,1000,-1000,1000,1000,184,-257,-68,1000,982,-669,-1000,-1000,730,1000,-452,1000,-8,368,73,111,-1000,-1000,1000,1000,512,1000,312,-959,-1000,1000,213}));
    }
    public void testDE00018() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-772,661,84,979,-874,-383,293,-356,-329,-353,668,-82,-681,-895,-997,980,311,552,-645,5,19,439,794,-885,186,496,912,-50,-766,64,869,-497,-355,-314,-869,694,862,-606,50,136,198,369,939,-529,-258,-120,-569,-238,448,764,-468,480,-785,106,17,-147,-927,-801,611,-458,377,424,-299,-74}));
    }
    public void testDE00019() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-619,-1000,-381,-927,1000,1000,-842,924,920,-1000,-1000,584,-1000,215,1000,-324,462,-1000,-301,-888,1000,-1000,-2,-178,1000,-1000,1000,189,-1000,-1000,-520,-670,-1000,1000,-1000,196,54,293,1000,-248,-1000,1000,131,128,-766,306,1000,-726,-539,1000,1000,881,791,1000,1000,927,1000,1000,1000,999,1000,-1000,1000,1000}));
    }
    public void testDE00020() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{85,400,744,-430,-604,-372,-831,-258,125,1000,155,-756,596,-263,-379,-452,78,-535,-1000,-543,-805,1000,379,700,-175,10,185,-1000,1000,249,178,-1000,1000,-343,319,-1000,811,1000,1000,-231,-558,-941,900,820,454,-409,-514,772,704,-141,-116,419,727,-1000,-1000,262,-228,188,-66,1000,-292,982,-1000,84}));
    }
    public void testDE00021() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{-182,-834,-971,716,-259,-692,-257,-455,-475,541,83,461,-332,144,294,-661,265,910,-702,-813,416,-897,646,-992,123,288,-471,-120,-583,-862,961,-307,-320,-883,744,299,-536,-664,421,-219,-583,786,-931,-30,410,688,457,-572,-621,-255,-94,641,-989,713,987,-598,-32,-104,96,21,27,-80,899,308}));
    }
    public void testDE00022() {
        assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWFhYWFhYQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{1000,-232,-561,1000,-1000,373,672,472,-672,117,-227,196,524,615,463,444,-31,517,76,1000,-318,-770,642,-788,-332,-375,-714,-54,-945,598,1000,-598,897,-224,357,-94,585,862,-485,680,-904,-213,-940,-27,754,827,100,-624,-97,1000,83,65,-963,43,-2,230,660,-500,-1000,-330,272,-111,-391,809}));
    }
    public void testDE00023() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{-94,874,299,717,-644,377,983,483,-279,684,278,859,491,477,-429,215,213,260,179,1000,-715,438,373,-457,-685,-577,-117,117,2,394,1000,-528,-313,-78,1000,-347,673,-104,490,241,-539,-213,508,-313,283,201,668,210,-383,34,-271,-250,-347,-700,-502,-295,-810,169,-484,-914,1000,327,-42,203}));
    }
    public void testDE00024() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{134,1000,-846,614,50,148,-266,-124,132,-246,141,557,-959,205,-1000,-1000,819,240,881,-138,-757,987,-608,-702,876,-290,268,-214,1000,538,-310,-421,-357,123,238,-221,495,-966,281,-145,-827,1000,77,165,-332,309,-526,155,87,-619,-842,65,683,111,1000,-295,1000,-466,105,-863,1000,-800,-226,-418}));
    }
    public void testDE00025() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{143,123,-256,375,-452,527,309,-271,1000,174,1000,-576,-632,577,-710,221,1000,663,408,229,-51,-252,-147,384,473,-1000,1000,-1000,594,-278,-26,-575,-749,-245,1000,-707,190,-448,412,-439,-95,-975,1000,-719,1000,-18,-93,103,-1000,325,1000,-599,332,-270,1000,-653,115,2,-1000,-440,1000,935,783,-394}));
    }
    public void testDE00026() {
        assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{741,-1000,524,324,-325,-229,-771,44,1000,1000,833,-58,-598,483,434,-217,-924,-569,547,593,1000,633,-95,-1000,1000,674,593,-1000,1000,587,1000,384,-1000,-203,439,746,443,191,29,1000,-385,-760,1000,-1000,1000,443,255,971,-90,1000,769,-248,141,-1000,506,1000,753,639,-418,1000,-551,1000,-572,-806}));
    }
    public void testDE00027() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{193,-906,-1000,-355,-441,746,403,480,-401,882,-1000,96,425,-1000,-757,311,-876,654,-304,634,1000,367,53,-30,371,474,-591,-663,88,1000,-597,550,-231,25,-1000,-1000,-984,189,530,-147,-491,-554,-1000,-44,867,502,1000,-1000,1000,1000,-1000,412,967,-988,192,-735,464,-124,-660,264,-291,183,1000,-866}));
    }
    public void testDE00028() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{464,549,-441,-60,-310,-205,513,-462,-250,927,295,73,-81,-330,959,576,206,-522,632,-155,141,-298,197,-289,351,84,190,-81,-853,-510,719,-920,-235,-768,284,482,-939,-7,-780,-872,-502,458,143,-649,-923,-973,-717,547,494,-323,840,170,-741,997,-354,-35,769,-321,-705,-378,399,361,-345,-475}));
    }
    public void testDE00029() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{631,237,-857,-1000,-157,-161,63,-997,-642,223,-1000,-193,596,-734,-569,-1000,-1000,757,-129,-1000,1000,754,-539,-321,374,10,-335,625,554,-934,-718,999,-1000,-541,-467,620,520,712,85,-1000,13,-101,-1000,736,484,1000,977,-266,1000,-80,-419,1000,1000,-1000,1000,-1000,834,-1000,440,-494,-1000,-409,4,293}));
    }
    public void testDE00030() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{503,881,482,-741,556,98,298,-1000,704,-210,185,358,432,64,599,-898,156,-384,179,-400,-382,-238,-1000,-243,537,195,382,221,54,-690,209,-123,-400,51,-578,780,-131,-330,-249,400,-528,-295,1000,-498,75,-641,-1000,1000,639,-997,1000,-815,198,910,-208,228,-480,-181,300,-801,434,95,-1000,40}));
    }
    public void testDE00031() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-157,-410,808,-427,746,15,883,346,336,863,652,-796,-188,780,123,376,-976,417,-138,-697,449,-336,-985,-652,-915,557,-146,156,-118,160,-943,514,-301,133,963,852,-17,403,-115,151,-622,-631,-729,960,289,440,935,456,55,325,-108,-947,-147,465,-552,926,684,-710,-920,990,-107,-450,400,395}));
    }
    public void testDE00032() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{1000,803,-1000,-84,-892,-551,-285,-928,960,-177,-1000,415,492,-837,601,53,-1000,1000,353,-475,1000,335,-575,-1000,-226,623,-1000,1000,948,361,-1000,-703,-802,738,-1000,437,351,-51,1000,472,115,-64,-466,-804,452,-315,1000,-945,1000,407,-1000,-162,-815,-1000,-402,-1000,1000,-387,668,272,-811,155,1000,-1000}));
    }
    public void testDE00033() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,-199,618,741,-128,-1000,-4,-154,1000,1000,366,-126,164,-19,-891,-760,777,1000,433,368,925,196,31,-720,-795,-141,-739,-172,354,-734,-976,4,-576,-409,-779,700,-1000,403,-958,544,-1000,1000,1000,-1000,-1000,227,113,22,1000,129,239,260,-535,1000,931,1000,-1000,-1000,-1000,1000,-1000,-1000,-1000,874}));
    }
    public void testDE00034() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-650,-720,773,-1000,1000,-933,1000,480,-1000,429,1000,145,-211,-633,1000,311,-876,629,-304,634,-1000,921,811,1000,353,-1000,-591,-581,-1000,-1000,-597,1000,60,-946,24,-1000,-5,1000,530,1000,369,420,-5,854,-1000,1000,-1000,713,-524,-559,1000,747,-27,1000,1000,568,464,-671,145,-930,-925,-1000,-1000,1000}));
    }
    public void testDE00035() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-177,-1000,-182,-912,-173,557,519,445,226,913,-488,258,845,-593,-473,804,-32,-1000,-1000,893,-453,467,499,769,811,132,-343,-1000,413,857,277,1000,1000,-946,-732,-654,-149,-668,256,-1000,-85,-337,-750,293,520,15,13,-727,-90,1000,-532,537,1000,-66,-739,-82,-371,1000,-1000,-642,1000,670,136,520}));
    }
    public void testDE00036() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,-658,62,-661,-119,-355,-486,831,53,275,-527,-752,524,-1000,1000,-1000,-498,-13,-1000,-400,685,623,-702,-1000,286,593,-285,270,117,431,764,1000,1000,1000,831,764,-1000,1000,-1000,-1000,188,-1000,-423,-1000,539,1000,-1000,620,-385,1000,1000,1000,1000,820,-164,-255,-556,1000,1000,1000,396,328,-866,449}));
    }
    public void testDE00037() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-628,658,529,421,-2,365,523,293,445,-536,1000,166,881,-483,142,-890,333,-210,-1000,350,830,1000,469,1000,718,240,-522,784,99,835,213,55,-461,-323,1000,-372,-707,-519,366,-641,1000,-54,-113,-693,918,34,-398,-1000,-1000,-219,-336,709,-759,466,-745,286,-554,-83,-195,-935,405,-1000,-1000,-451}));
    }
    public void testDE00038() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{222,-1000,571,-1000,-708,-1000,141,286,-264,-1000,-789,-594,-36,-1000,87,-304,-859,-876,-1000,674,423,906,-313,-1000,385,1000,-902,-522,-251,728,-152,-400,764,558,510,1000,-1000,167,389,-202,1000,-623,510,-1000,570,89,-1000,455,554,1000,-400,1000,-101,453,354,-88,-727,71,-367,89,568,1000,-579,-349}));
    }
    public void testDE00039() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{402,-153,14,-490,-440,-949,-864,-891,-610,26,-771,355,743,119,1000,-15,-443,-225,-695,270,-203,-573,92,-1000,-1000,350,-711,189,397,-113,321,-96,-166,922,-822,319,-595,980,-356,-245,385,-71,790,-236,-1000,386,381,258,325,798,295,445,630,-184,388,-1000,-99,469,1000,89,-222,489,1000,633}));
    }
    public void testDE00040() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-218,-516,-122,980,-1000,618,-687,-1000,2,-78,-803,-1000,1000,-489,195,48,576,-935,171,-707,-372,201,-184,1000,218,-789,773,1000,-1000,-565,-696,-549,1000,28,394,764,-842,603,183,918,-600,-89,1000,-279,99,1000,-655,117,751,882,-900,-160,35,960,708,-1000,257,780,107,1000,574,-666,661,970}));
    }
    public void testDE00041() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,1000,1000,705,311,-1000,-364,22,572,76,391,-130,1000,-363,896,-44,-325,-282,213,991,169,-1000,-677,1000,251,-1000,-997,483,381,1000,873,1000,-995,-175,-675,-887,295,879,-1000,96,-188,450,79,-1000,-319,-150,238,-884,-786,378,1000,1000,60,-965,555,-1000,-1000,-716,446,765,492,-308,-54,-594}));
    }
    public void testDE00042() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{269,929,-757,421,538,402,-267,-12,838,-975,211,650,-209,499,1,713,433,-544,825,-245,557,396,-155,-569,-433,274,828,622,409,-293,-395,-929,803,-269,-181,19,512,248,586,983,615,908,-838,791,918,985,928,259,513,32,131,-506,228,-424,-355,689,278,-92,771,-492,-263,-826,553,666}));
    }
    public void testDE00043() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,-101,656,-639,470,-743,-535,1000,729,1000,-433,996,498,-229,1000,-1000,540,956,-1000,-1000,558,1000,-327,-1,350,282,-445,1000,-2,622,1000,1000,448,1000,1000,-636,-1000,1000,-1000,-1000,375,-1000,-714,-717,-848,1000,-1000,895,649,315,1000,1000,1000,1000,-1000,-818,-940,1000,1000,1000,1000,-490,1000,1000}));
    }
    public void testDE00044() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,-219,-1000,767,-1000,973,-930,-1000,-537,-1000,-73,319,723,-1000,-1000,1000,-1000,-385,1000,1000,1000,-1000,-1000,-532,-1000,-202,-557,-235,874,-1000,-1000,-1000,813,163,-1000,1000,1000,-127,907,1000,-597,1000,-67,508,1000,-133,1000,-1000,1000,1000,-617,-1000,-213,-1000,1000,-195,991,-408,-1000,-935,-846,503,1000,629}));
    }
    public void testDE00045() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-745,-71,48,416,613,-120,-1000,-281,-428,-513,-421,-281,861,88,491,-894,-848,167,-803,304,-161,-732,135,844,-903,-35,-591,782,733,-231,664,-549,-830,1000,-56,-136,-203,1000,-1000,-1000,-1000,-203,-1000,72,-633,734,483,-995,-404,314,-900,709,1000,97,211,-886,17,1000,953,759,-470,361,284,686}));
    }
    public void testDE00046() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,-742,1000,526,1000,-117,-385,-996,1000,-259,1000,1000,-353,496,-684,-95,1000,-854,-1000,365,-1000,41,-1000,694,-660,-1000,-1000,-1000,338,1000,-1000,-1000,-853,-1000,-37,1000,560,1000,1000,1000,1000,-1000,-1000,1000,-1000,-851,-1000,-1000,628,-1000,-215,-471,-1000,-1000,295,-194,1000,748,-1000,-778,-738,-1000,-1000,795}));
    }
    public void testDE00047() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,378,-616,999,-17,-64,291,-120,-245,-291,935,-160,-1000,-764,-1000,696,-133,-954,1000,-163,-498,277,-121,-653,1000,925,977,143,-128,992,218,-1000,544,-860,-1000,-214,-1000,946,1000,-481,367,-1000,-863,382,1000,-529,-1000,-1000,1000,1000,1000,-359,-864,-1000,-624,-296,1000,-1000,-311,-77,-439,-616,984,947}));
    }
    public void testDE00048() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-181,190,588,-195,-9,543,146,-425,-192,-476,1000,681,541,1000,-777,1000,1000,-48,-580,258,-162,-22,-252,-771,1000,-192,417,-1000,-1000,517,1000,427,538,-917,426,549,1000,424,-185,303,-298,-696,-699,-17,1000,727,616,-205,1000,1000,-1000,1000,768,-72,35,-759,-1000,-459,1000,924,-758,628,92,680}));
    }
    public void testDE00049() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{180,678,854,689,-467,546,730,-104,-569,148,913,-312,640,674,-930,665,454,1,-798,370,514,193,-392,-591,-8,856,295,-52,647,-419,570,532,-133,-48,-370,270,875,950,-329,-500,40,945,182,-745,-586,959,-51,48,490,-627,-406,-158,509,-165,390,-616,-385,-207,580,549,-365,745,434,-223}));
    }
    public void testDE00050() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-655,-907,-88,301,695,304,-94,-600,-336,990,-181,646,163,-554,459,-176,-270,-769,119,993,-387,443,-901,44,-694,-517,-101,-987,587,309,855,-596,672,446,69,825,237,-640,166,333,-265,-827,602,109,696,182,-938,241,-482,125,797,-226,-386,767,702,486,-260,-46,-642,309,-181,72,-661,779}));
    }
    public void testDE00051() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{829,-656,-1000,-179,-1000,655,-464,-715,686,-603,-1000,-677,-319,-476,275,-148,-319,-38,-751,-475,739,588,-755,789,-594,696,780,-998,-506,-455,-162,-25,-71,-285,1000,418,-648,-892,-68,207,-463,403,1000,-426,355,1000,956,645,-893,528,-499,939,923,176,-203,-1000,402,894,1000,-578,-1000,1000,-7,-148}));
    }
    public void testDE00052() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{538,-592,476,-795,1000,319,-931,-649,-661,822,167,568,-1000,-384,1000,312,-133,1000,-1000,575,576,940,1000,-1000,714,-1000,-182,-350,152,-1000,209,1000,912,-1000,-763,-1000,-148,629,278,961,463,-129,358,971,1000,-1000,1000,575,863,-758,-658,59,-825,289,568,-278,-777,-1000,-857,173,-308,-682,-453,-650}));
    }
    public void testDE00053() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-434,-1000,108,971,105,-11,952,666,-159,-28,531,542,-299,-337,-1000,918,1000,-1000,730,-674,-1000,-652,-415,458,601,-227,508,-1000,316,230,-334,-966,-663,-1000,233,1000,587,162,-564,281,533,-905,236,924,493,749,247,-1000,1000,717,-78,124,1000,-543,-278,-576,-786,194,419,-1000,-247,-643,-853,1000}));
    }
    public void testDE00054() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-74,-174,-517,-190,1000,1000,92,-1000,-276,-773,-895,299,495,337,642,-105,-1000,699,-887,1000,-74,930,602,-1000,-990,140,1000,469,-91,-632,68,1000,900,-297,-929,-647,-1000,480,84,181,461,1000,-231,-1000,239,190,129,-533,622,13,-732,-530,566,-844,1000,-81,1000,491,-531,-658,-1000,-928,362,-1000}));
    }
    public void testDE00055() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-155,1000,292,726,-653,-761,774,324,0,210,1000,-1000,746,1000,-617,-518,188,0,-632,699,1000,-508,-749,257,-153,-204,-703,1000,0,288,290,-120,0,721,-953,-723,1000,529,538,-1000,-207,1000,-165,-458,-164,390,-1000,212,632,301,16,-419,1000,0,209,288,-420,-468,43,1000,-629,1000,342,-384}));
    }
    public void testDE00056() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-380,-648,66,-198,711,-1000,1000,-482,-215,-593,61,-395,610,225,-244,-214,468,-712,-399,-623,-527,-570,-787,1000,329,1000,949,-316,-526,-203,-580,864,-1000,1000,-429,-495,-579,-1000,-314,-578,606,-1000,1000,-373,273,-969,747,856,433,298,-1000,-691,-427,1000,553,12,-44,806,182,-750,933,479,942,445}));
    }
    public void testDE00057() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-410,-382,54,-703,-59,-803,1000,-235,590,-705,452,-595,470,-357,-99,-990,-638,318,-1000,-490,-267,110,-301,699,-1000,596,1000,-384,-387,184,-1000,949,-570,1000,-317,147,-256,-784,0,-549,1000,356,-564,405,946,-1000,300,449,-1000,214,-298,-479,429,710,315,-1000,131,1000,-145,-1000,566,-70,-470,1000}));
    }
    public void testDE00058() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-273,73,64,751,-112,-834,146,-175,-323,-689,347,-350,1000,401,-383,-816,-499,-310,-415,667,-696,836,59,670,586,48,525,-986,183,1000,-1000,1000,-403,-1000,-1000,-630,1000,-990,-1000,-1000,1000,-1000,-805,62,-241,-875,839,217,-1000,-303,-593,-167,-300,720,-669,-259,79,-210,-360,53,630,110,675,1000}));
    }
    public void testDE00059() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-264,605,672,11,692,-257,-650,-81,-623,99,-149,44,75,1000,402,-55,-512,279,-196,738,290,411,371,-174,379,671,1000,-1000,-709,-474,398,203,-1000,211,-1000,734,-546,-706,1000,647,-423,-1000,889,138,-346,-205,744,837,-163,1,-805,-272,-1000,847,974,1000,131,-888,882,299,-260,-509,1000,1000}));
    }
    public void testDE00060() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-413,258,689,-64,-300,-400,81,1000,140,-168,-17,-129,1000,295,-400,545,-868,110,-400,1000,-269,210,-307,329,384,-1000,-507,-581,449,-257,-1000,300,145,400,-956,536,-675,-342,-418,-295,399,-400,-102,1000,183,175,1000,706,-1000,-425,763,488,191,966,-21,548,458,-717,279,920,-720,324,105,-406}));
    }
    public void testDE00061() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-441,-715,-651,345,655,-281,736,-915,-837,656,-852,-832,-557,-47,-79,-48,-592,-272,116,363,-962,573,720,-44,630,328,-357,415,-741,-586,-121,194,679,205,-896,-120,-579,710,660,-898,195,998,-591,-692,888,181,-769,582,-599,551,108,831,-617,-499,-338,117,-489,-749,773,-301,-611,-950,-498,431}));
    }
    public void testDE00062() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-249,739,956,-551,545,364,-299,162,289,-52,-173,234,933,769,879,246,253,268,-74,438,413,29,-795,-37,357,979,278,-130,-231,-931,336,-244,-926,146,165,944,-517,-436,979,235,-548,189,838,-177,-921,-118,703,785,-52,301,-713,-266,720,897,848,654,371,-615,787,734,-600,598,519,93}));
    }
    public void testDE00063() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-702,-121,-161,-559,-656,981,-54,-496,659,795,348,403,455,-317,-240,624,278,502,-431,-758,-158,-332,394,-855,-334,787,235,-812,-55,-723,525,-633,-200,-102,282,80,622,-90,263,-305,-451,714,522,211,290,244,106,617,502,-868,747,-295,-302,-283,-628,560,-726,361,985,-270,675,170,87,-850}));
    }
    public void testDE00064() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-493,1000,488,1000,554,310,-822,856,-218,-839,799,-404,1000,414,293,-466,212,1000,1000,-331,-1000,283,-123,504,16,-1000,-2,-1000,-449,-974,-1000,-75,999,484,54,24,-339,280,51,-525,-491,595,67,371,717,-544,-716,79,1000,1000,-796,238,-247,161,-103,-119,1000,-153,1000,-1000,-64,-463,1000,-155}));
    }
    public void testDE00065() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-1000,-1000,254,-408,-895,1000,-178,-70,-262,-1000,361,498,603,372,-325,687,667,-338,1000,860,162,914,900,396,-705,-1000,492,259,185,-1000,-919,-1000,159,-73,-40,-440,-1000,-679,1000,-1000,333,-922,-415,-322,-912,-970,-923,1000,-1000,170,-414,-1000,-1000,-318,458,135,828,368,834,671,-662,140,224,-276}));
    }
    public void testDE00066() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-212,-321,-201,-454,-382,76,272,268,374,-206,633,64,794,231,108,742,680,-12,625,530,-79,467,154,473,210,-315,387,45,-1,-987,-153,-319,483,11,-616,-91,-434,-474,656,-428,213,-282,233,-455,-556,-565,-247,351,64,-3,-249,128,-404,-128,-396,116,358,85,506,473,436,-37,-311,-575}));
    }
    public void testDE00067() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-318,923,304,-33,1000,830,1000,-3,1000,884,-350,450,872,599,251,-706,-174,914,-302,-1000,-409,-596,-671,-316,1000,988,-658,-750,-291,-424,976,680,283,217,-1000,1000,1000,-427,-1000,1000,-1000,819,1000,114,212,-23,1000,-1000,573,311,-518,1000,379,582,-1000,-752,-297,351,930,-781,1000,-490,213,554}));
    }
    public void testDE00068() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{712,-369,-246,-1000,17,-762,-822,856,1000,1000,217,-404,189,-99,47,-81,630,-531,-680,-331,-409,283,-476,-125,16,539,187,622,-959,415,910,485,547,979,-1000,95,820,-522,-582,504,-697,497,963,371,-709,-544,1000,-1000,303,-623,647,353,196,424,-1000,196,-416,-318,-454,1000,1000,440,-1000,-366}));
    }
    public void testDE00069() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-277,485,803,-1000,85,1000,-375,-102,505,58,-822,-160,-386,746,117,50,227,312,1000,431,-409,492,550,992,382,-606,-189,-1000,-1000,-436,-897,-705,755,917,-128,52,290,-995,919,-1000,-1000,1000,-882,-150,111,-832,-653,-269,-306,615,3,612,103,679,205,551,357,-998,712,48,-905,200,1000,-92}));
    }
    public void testDE00070() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{1000,-553,363,-236,-418,-244,893,-209,-949,239,-958,-156,-190,-1000,-285,-1000,-1000,25,689,1000,1000,-274,1000,1000,561,1000,1000,-1000,-528,-18,177,821,1000,-188,-479,-774,-1000,724,-895,897,625,-225,-665,1000,-757,-876,-200,-1000,194,-862,743,-655,99,1000,27,1000,-383,-1000,-69,715,-763,-438,189,499}));
    }
    public void testDE00071() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-1000,226,653,-1000,-174,-408,-117,687,-604,166,-731,-854,272,-601,69,1000,971,-1,-1000,-337,-1000,904,-1000,304,-811,-224,440,1000,1000,169,-345,1000,-435,-88,296,-122,1000,-1000,-238,188,400,33,-1000,-162,51,850,1000,1000,1000,-274,353,-1000,-524,802,740,1000,1000,-20,686,-419,-753,-600,1000,-919}));
    }
    public void testDE00072() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-27,-436,704,-115,-1000,673,-272,-525,105,1000,-382,-578,-161,-1000,-239,796,-92,1000,685,-37,842,0,-395,10,-1000,40,157,-202,505,-681,-423,1000,-1000,-381,21,-346,171,78,-152,-646,844,665,-1000,-339,-761,66,1000,1000,-81,-106,1000,-183,-149,495,-55,896,-27,-199,402,-671,-624,-1000,719,762}));
    }
    public void testDE00073() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{541,-1000,-1000,638,-969,860,849,847,-388,353,-1000,652,-1000,15,-1000,-83,83,819,-269,-93,250,-387,-1000,586,1000,1000,786,-996,-1000,-967,353,625,747,283,-1000,-980,-836,-509,-873,76,144,-697,932,-8,279,-702,1000,381,714,-259,1000,-760,-1000,451,1000,1000,1000,-691,-516,-1000,-422,557,302,783}));
    }
    public void testDE00074() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-1000,-904,888,-278,-658,-54,1000,1000,-242,-1000,69,82,-314,464,592,-744,-413,352,-633,915,881,1000,1000,103,1000,1000,671,-514,-1000,233,53,-367,1000,-713,-51,-508,-574,247,-683,1000,-1000,-1000,346,761,602,-303,-272,-1000,359,-1000,108,-1000,-254,885,277,-43,28,-1000,1000,-1000,241,491,489,-537}));
    }
    public void testDE00075() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-372,-414,469,12,364,595,5,-967,222,656,-20,-1000,-571,209,840,1000,829,354,659,-1000,-466,363,-1000,-1000,392,51,-729,565,990,-1000,203,585,-1000,-50,1000,256,1000,-404,340,-1000,-345,1000,-1000,-1000,-953,-253,1000,982,-200,-10,-1000,1000,-1000,949,111,-15,574,1000,402,-649,-778,-1000,943,391}));
    }
    public void testDE00076() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-1000,-125,-671,-18,-174,788,-277,566,-1000,-198,-731,-82,616,99,-292,1000,1000,-469,-1000,282,-1000,203,-1000,921,419,314,859,-11,-200,394,-345,1000,-524,797,394,255,1000,-1000,-259,530,252,-1000,400,-17,764,850,1000,1000,1000,-40,711,-1000,-524,971,1000,1000,1000,-20,-377,-667,-291,-1000,1000,1000}));
    }
    public void testDE00077() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-921,-313,-932,789,984,22,-878,-1000,677,-26,-777,552,304,-529,-639,-1000,-1000,-997,348,-578,-996,-389,145,-23,1000,198,-1000,4,-433,245,-735,-1000,-749,730,-437,-746,-2,90,1000,-979,997,1000,91,45,224,-620,595,813,-202,1000,-1000,271,1000,-163,-799,11,667,-747,-2,1000,665,1000,-842,-364}));
    }
    public void testDE00078() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{-470,-232,-932,-453,1000,502,48,-420,-36,1000,-134,975,-899,292,295,807,726,-345,-927,-925,865,1000,226,674,-255,-1000,-851,-450,1000,282,355,647,1000,974,463,-259,-985,-458,690,1000,-511,-663,-10,-834,1000,-269,-343,1000,1000,6,1000,-6,1000,478,-1000,682,-1000,1000,507,-596,-412,1000,-894,214}));
    }
    public void testDE00079() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{777,12,24,755,402,176,830,313,-415,572,329,-1000,-220,-507,286,661,384,93,706,-63,942,-1000,86,-1000,-67,-729,-1000,710,181,289,228,303,209,794,1000,342,875,-400,-1000,95,212,451,1000,-351,371,-1000,-584,-1000,-395,-1000,443,-759,1000,-1000,37,207,292,-62,394,-273,597,1000,31,-1000}));
    }
    public void testDE00080() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{1000,362,-831,-522,997,-42,629,-94,81,1000,842,-1000,530,193,853,329,831,303,795,172,-914,-1000,-732,-494,508,-164,-642,760,-491,1000,400,-487,-300,1000,1000,463,546,-1000,-1000,293,321,119,1000,-11,-284,392,-1000,-263,-349,-53,-202,-985,557,-296,-541,-410,973,-474,-316,181,1000,941,412,-815}));
    }
    public void testDE00081() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-115,250,915,524,-458,8,659,-359,-613,-824,-405,-650,743,-528,46,19,418,-1000,148,216,-273,615,-1000,-478,-214,706,653,-343,184,688,-491,339,488,-251,-888,-754,28,-1000,348,-449,443,-171,-462,-405,362,-613,174,-494,1000,-293,-754,-786,132,993,422,-829,135,-851,244,466,340,-724,989,466}));
    }
    public void testDE00082() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-554,-1000,-171,-445,515,0,1000,-1000,750,350,-750,58,1000,696,1000,197,-460,681,-47,459,185,-995,-581,-836,643,-626,-240,-638,-686,485,-713,-62,625,-161,280,365,1000,260,-124,-453,-674,-660,1000,-761,-706,-343,1000,-1000,842,306,-982,-898,-742,-157,629,-579,-440,667,-597,-806,2,1000,-1000,913}));
    }
    public void testDE00083() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{1000,1000,972,147,-638,521,1000,-117,1000,1000,1000,431,-822,229,-700,-691,511,1000,359,-339,-1000,179,-400,768,1000,1000,-91,1000,427,5,-546,173,-459,1000,-208,-1000,-790,-1000,-699,-289,-142,1000,-802,-1000,1000,-396,-1000,1000,400,-1000,1000,353,175,1000,-1000,-226,-1000,964,-39,1000,1000,406,729,371}));
    }
    public void testDE00084() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{992,47,693,100,-73,-983,-1000,685,344,322,498,-349,-1000,7,-1000,180,-66,310,638,-800,-1000,-158,-441,163,1000,763,-118,333,1000,-877,428,-1000,-855,413,-829,525,-964,-1000,-23,-338,-248,706,-848,-15,1000,40,-1000,-557,21,-495,-328,1000,955,-792,-54,-352,-1000,463,501,1000,560,-117,1000,572}));
    }
    public void testDE00085() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{366,-993,-72,515,-1000,1000,1000,-1000,-327,-311,-1000,-1000,-52,-1000,-922,1000,402,-114,-148,925,17,634,1000,-686,115,-1000,-1000,203,-182,-915,-203,293,575,466,-215,-587,1000,1000,-437,1000,-265,-563,731,887,485,550,873,640,-1000,-1000,-1000,62,-108,-636,-636,-31,1000,-10,1000,191,-199,455,71,1000}));
    }
    public void testDE00086() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{776,-1000,-805,480,-158,1000,166,-817,432,392,-162,-334,20,-1000,-1000,1000,-101,-496,1000,-285,-31,199,1000,683,527,81,-1000,245,-773,-890,-361,-725,-47,-285,-301,-1000,-526,48,-313,359,129,-374,-136,-556,-682,959,-470,-47,-531,-289,-36,954,-1000,-579,963,295,818,-66,845,826,-1000,-986,270,1000}));
    }
    public void testDE00087() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{725,-1000,-524,-454,-144,-151,256,-407,525,91,1000,404,-961,-308,-716,-46,960,1000,-101,-385,-1000,-314,-835,1000,920,851,-892,833,-408,-654,670,-882,-875,565,-537,-865,-370,-953,291,223,-721,1000,-72,-411,1000,904,-681,1000,-665,-583,-81,1000,1000,6,-648,1000,-1000,945,571,640,635,632,-3,37}));
    }
    public void testDE00088() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-189,511,-1000,-1000,1000,-757,477,-27,1000,71,765,1000,557,562,-1000,-1000,50,1000,-9,-962,-398,462,-1000,-10,-406,1000,1000,201,-925,1000,-117,-388,-647,-1000,1000,101,-1000,-1000,-150,73,8,1000,-157,-290,-1000,277,51,1000,208,1000,434,-1000,566,462,617,1000,852,-281,-589,-1000,1000,-431,-769,-104}));
    }
    public void testDE00089() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-199,-212,-708,-382,1000,-1000,-654,-191,1000,861,54,-338,505,-1000,-840,-1000,-1000,812,493,-390,132,910,1000,-1000,651,528,890,1000,-934,-740,287,850,-849,-622,1000,1000,881,1000,638,275,-507,948,-966,-660,942,-418,-930,279,1000,827,-737,266,-257,844,-940,-411,-422,752,-52,860,605,439,-1000,-745}));
    }
    public void testDE00090() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-869,-1000,-777,-1000,471,-964,240,-483,796,1000,1000,776,252,-334,476,-767,-817,491,-802,137,693,-379,244,-616,-1000,-1000,1000,567,716,-431,-975,-738,-1000,257,1000,955,692,800,213,1000,-24,1000,620,841,-1000,-591,-274,-17,531,1000,-1000,1000,398,-1000,83,759,-981,456,155,-10,1000,-863,-39,-828}));
    }
    public void testDE00091() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-612,307,-641,-323,-199,-916,-377,1000,-531,490,-440,753,761,-310,781,-398,-798,-557,215,-357,-601,-881,-656,496,-814,-285,-81,-447,225,619,-645,-720,4,834,434,19,136,1000,-696,224,190,741,413,-101,-932,-527,381,465,1000,-140,-344,-619,284,-1000,114,-219,602,465,-314,-117,362,-620,-92,30}));
    }
    public void testDE00092() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{433,-120,-321,-856,-1000,-1000,27,506,1000,1000,987,38,134,-1000,619,-1000,-398,1000,368,-101,584,-1000,293,-1000,-884,-1000,1000,1000,-819,-1000,-408,-134,-944,621,1000,1000,956,-304,1000,-1000,141,797,-99,1000,-369,-1000,793,260,1000,1000,-827,1000,135,-1000,-135,-151,-857,621,-318,1000,326,-328,-72,-1000}));
    }
    public void testDE00093() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{291,175,-629,820,-201,145,-207,614,79,94,56,689,-653,1000,-13,-767,638,636,-1000,808,197,-42,502,-1000,850,983,-142,-829,-1000,-676,-199,235,-151,-927,24,796,811,190,1000,408,1000,-25,1000,-304,-1000,89,-829,1000,531,54,-306,-372,597,-649,83,-826,244,282,246,-10,687,-121,-260,1000}));
    }
    public void testDE00094() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{744,343,-341,654,-1000,139,-815,926,24,-543,1000,-1000,1000,-1000,66,34,-756,452,-574,-151,-376,-522,276,-227,-384,-339,-308,936,181,1000,328,412,466,-209,1000,506,631,-1000,788,-54,-1000,806,-1000,-225,755,463,566,-587,746,-101,120,353,-669,828,1000,-387,263,672,-1000,1000,313,-87,361,-303}));
    }
    public void testDE00095() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{165,460,-74,1000,658,-187,462,-857,238,287,1000,1000,-141,-936,1000,-477,433,468,282,-193,792,-1000,-360,432,797,993,1000,-70,-252,-836,-1000,-1000,649,-582,-7,645,1000,1000,1000,-591,35,-1000,-992,369,884,1000,-1000,749,519,-680,1000,93,76,810,1000,205,1000,-1000,-428,740,-1000,738,-722,-768}));
    }
    public void testDE00096() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-106,-435,-706,-216,-191,173,-530,1000,-345,-125,-1000,-128,136,-876,-1000,-395,973,-1000,-506,-100,440,849,1000,-1000,-624,-216,-1000,-324,-1000,-687,-178,1000,-965,-1000,-1000,-400,-1000,-415,177,483,-555,909,400,-784,-1000,1000,579,-858,-765,400,-1000,371,-49,-854,-1000,458,-424,1000,-470,-60,1000,-11,9,360}));
    }
    public void testDE00097() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{71,-430,543,329,530,19,336,-627,441,-725,955,43,-927,-193,-559,-202,27,659,834,-37,852,-806,972,75,979,-382,619,-678,-415,755,987,-688,535,303,-256,-140,584,-476,188,66,-328,-820,451,531,318,405,283,-735,881,-835,285,-449,765,-34,269,844,521,-425,-133,-350,251,617,-549,221}));
    }
    public void testDE00098() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{33,-1000,339,723,70,606,-451,-902,164,-715,1000,931,-1000,-433,-1000,-629,-629,-137,1000,-336,708,-320,1000,146,1000,320,1000,247,514,229,612,-1000,1000,680,160,-809,-906,-1000,-4,360,71,-631,329,-1000,727,584,-102,-239,549,35,811,-1000,1000,276,247,464,184,-750,-1000,-919,445,903,-23,500}));
    }
    public void testDE00099() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-724,105,-957,-1000,749,178,-156,646,-79,96,-598,231,-1000,165,-1000,110,519,-566,-669,419,1000,-21,1000,-774,1000,115,-1000,-1000,-775,102,467,-468,-944,-602,-1000,-790,-89,-1000,-940,-359,-1000,605,162,-689,-1000,-481,1000,-1000,624,167,-234,-502,-702,-1000,-1000,1000,-1000,903,383,71,345,187,-45,0}));
    }
    public void testDE00100() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-994,448,-659,-969,645,-564,-406,710,-166,767,-767,348,-894,863,-147,952,530,-327,-999,679,-40,677,-65,-390,451,508,-914,-412,-248,-807,-925,397,-699,-225,-852,8,181,-204,-497,173,-841,996,-853,-731,-916,-571,610,-34,707,206,-820,13,-735,-970,-899,264,-913,832,652,894,874,-89,-56,712}));
    }
    public void testDE00101() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{573,-580,-194,673,-829,1000,-453,718,-355,-884,261,210,-1000,528,343,52,1000,112,-173,-291,503,-98,1000,-623,298,-184,1000,185,560,830,-431,-1000,-57,-140,-1000,-181,862,-1000,-464,212,877,1000,-264,157,-715,-1000,782,-1000,1000,-1000,339,1000,134,-19,678,1000,-1000,104,942,278,62,-309,-755,-184}));
    }
    public void testDE00102() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{246,-772,359,-62,-915,1000,894,159,-96,-727,-294,-606,-1000,-960,1000,-1000,1000,-237,955,401,-759,-220,276,-1000,-381,-1000,154,-299,295,98,-864,-773,1000,237,-266,1000,568,-221,-211,-1000,1000,-962,-74,-537,-478,-1000,36,-926,-63,-976,-49,-1000,714,1000,-1000,665,195,-1000,31,157,-1000,-321,-1000,-564}));
    }
    public void testDE00103() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{1000,922,-438,698,-47,-1000,-307,-32,1000,-783,-1000,-981,1000,649,741,-1000,-753,221,-718,329,-72,-145,322,1000,-143,1000,962,1000,-1000,-740,-90,1000,507,-570,-465,-250,-1000,1000,-1000,-14,151,-243,1000,91,1000,627,928,1000,-1000,808,-784,-1000,397,-947,680,-1000,237,633,-474,-98,-146,-851,1000,792}));
    }
    public void testDE00104() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-1000,868,-186,-947,568,-536,1000,498,-415,108,-536,-828,474,-1000,-1000,128,-553,1000,-41,1000,-1000,477,-745,-171,-509,-1000,1000,1000,745,1000,-901,170,49,-620,360,237,-1000,-303,117,257,1000,150,1000,-591,438,242,1000,-737,-750,-1000,-282,-532,-948,-136,1000,-627,636,-230,447,1000,361,-454,-142,220}));
    }
    public void testDE00105() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{830,3,-677,62,-59,-468,-149,4,758,-792,-418,934,-728,218,-730,-624,322,851,-1000,-97,-1000,506,1000,228,505,468,394,7,-454,-475,736,494,-261,-152,-654,1000,325,-90,-138,-369,578,446,806,-24,441,421,525,-82,-1000,385,-301,384,778,-1000,-318,-496,249,561,-29,409,-241,950,944,844}));
    }
    public void testDE00106() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{486,585,1000,-793,633,113,-905,287,340,-784,-813,325,-478,-177,-73,528,965,694,-860,-538,249,-650,544,920,45,-133,481,270,688,346,-384,153,-712,-676,645,-303,-137,53,-112,72,983,-377,312,-926,-775,253,-3,759,383,703,-619,609,-173,730,679,668,652,795,-178,200,-649,806,980,-33}));
    }
    public void testDE00107() {
        assertEquals("VOID|getArgName=java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setArgName(java.lang.String):void",
            new int[]{-213,87,119,218,-485,282,1000,255,946,-1000,711,567,-48,754,-476,595,-26,361,-50,-80,-942,-181,-5,321,515,96,249,1000,1000,-36,-1000,-536,-845,-850,557,-773,-755,1000,-409,-107,33,321,-173,45,-105,386,-768,-463,-766,482,-618,844,-1000,-237,461,-257,1000,-238,225,421,750,-1000,-380,-99}));
    }
    public void testDE00108() {
        assertEquals("VOID|getArgName=NULL", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setArgName(java.lang.String):void",
            new int[]{-1000,-288,914,-895,-1000,168,100,-1000,499,1000,-16,659,268,-992,1000,596,-1000,149,1000,267,-205,560,441,-224,754,-1000,-82,186,-1000,745,-1000,378,-385,-1000,-335,-533,-101,-844,806,-44,-321,-248,215,-995,267,987,464,747,-451,1000,614,492,704,274,-1000,1000,527,1000,-346,-172,-177,-958,705,-1000}));
    }
    public void testDE00109() {
        assertEquals("VOID|getArgName=java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setArgName(java.lang.String):void",
            new int[]{-1000,-40,119,-1000,-11,944,1000,-679,-1000,408,-392,-19,789,1000,402,674,-980,-30,756,1000,-971,-71,-5,1000,1000,-2,-1000,-528,-946,-36,-1000,902,-554,580,127,-1000,-755,1000,296,-952,33,-830,1000,45,1000,683,554,-463,-1000,606,93,812,-555,658,-700,-257,1000,-1000,1000,236,-16,-960,338,-99}));
    }
    public void testDE00110() {
        assertEquals("VOID|getDescPadding=java.lang.Integer:ODM=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setDescPadding(int):void",
            new int[]{-460,1000,-976,-132,253,1000,414,-1000,963,383,634,363,-1000,-12,467,253,198,830,826,-277,533,-674,380,218,-126,409,-886,595,-325,186,6,-1000,141,883,66,1000,886,-863,279,-108,269,-363,848,359,931,788,1000,-305,142,-640,1000,196,724,-306,-1000,169,-932,208,-501,779,-953,-493,-1000,826}));
    }
    public void testDE00111() {
        assertEquals("VOID|getDescPadding=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setDescPadding(int):void",
            new int[]{-1000,-789,844,400,-1000,-1000,-315,-1000,-444,56,573,794,241,-647,-551,-925,886,-732,1000,-48,254,902,-670,232,745,-359,1000,144,-992,-638,-103,1000,428,-1000,-611,-584,466,-225,-248,378,48,921,-637,645,615,656,-238,933,-1000,579,-1000,474,279,-1000,-1000,120,610,1000,1000,479,1000,1000,-222,-639}));
    }
    public void testDE00112() {
        assertEquals("VOID|getDescPadding=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setDescPadding(int):void",
            new int[]{369,1000,-536,-622,701,1000,823,1000,487,706,634,887,-537,24,614,717,232,1000,-901,-309,93,-1000,150,470,-548,338,-1000,132,0,186,674,-1000,95,1000,-334,870,552,-547,279,-285,892,-677,531,32,756,1000,952,-954,-3,-640,1000,946,1000,239,-1000,553,16,-675,-627,383,-450,-1000,-1000,723}));
    }
    public void testDE00113() {
        assertEquals("VOID|getLeftPadding=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLeftPadding(int):void",
            new int[]{-21,-678,-406,870,322,747,659,-24,-934,-639,-99,-415,-763,-398,-796,-540,-917,510,-348,367,113,883,-916,-413,-175,-816,156,-533,-123,-188,413,750,546,312,342,15,-346,819,154,989,847,-341,-863,720,105,-640,804,-381,640,-432,795,-728,-999,-658,500,-454,-893,-872,-285,-970,172,386,306,110}));
    }
    public void testDE00114() {
        assertEquals("VOID|getLeftPadding=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLeftPadding(int):void",
            new int[]{-1000,603,-996,-14,-333,-213,744,-219,-595,-823,329,-870,-442,-314,-797,655,-684,356,922,-345,-670,-649,792,-877,1000,1000,498,-165,50,1000,-128,-46,-488,126,434,-497,-1000,-530,-666,-220,-794,-1000,-730,-78,514,-1000,-592,-60,-469,865,609,-960,592,556,-673,-585,-139,-799,-1000,-750,88,-379,-99,-126}));
    }
    public void testDE00115() {
        assertEquals("VOID|getLeftPadding=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLeftPadding(int):void",
            new int[]{-479,-1000,624,293,453,1000,-1000,-64,252,-51,931,96,-36,-188,548,-1000,880,1000,353,1000,730,132,-1000,174,-502,-326,674,978,-1000,568,-852,865,1000,1000,928,-460,-280,1000,837,1000,1000,555,-391,1000,-557,-349,202,-947,870,745,-786,-418,-1000,-590,177,-340,-4,89,246,564,-456,-400,-118,114}));
    }
    public void testDE00116() {
        assertEquals("VOID|getLongOptPrefix=NULL", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLongOptPrefix(java.lang.String):void",
            new int[]{-75,-1000,-31,-417,-355,-748,-788,871,1000,-711,864,-164,-425,-151,-888,336,484,-87,-767,-52,-244,-245,261,-657,-514,-19,-57,124,-1000,-614,-229,-856,-1000,1000,1000,-152,843,849,1000,965,-19,27,-662,-382,319,-783,-104,1000,210,-541,-290,-356,-320,555,-377,117,121,257,-423,1000,453,605,308,-18}));
    }
    public void testDE00117() {
        assertEquals("VOID|getLongOptPrefix=java.lang.String:TmFO", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLongOptPrefix(java.lang.String):void",
            new int[]{-267,-87,109,-693,227,1,922,203,-200,746,-468,-276,-709,-913,-362,-95,428,3,-545,308,683,-877,-382,138,155,-98,-18,707,-650,20,265,213,-16,471,-286,177,602,514,-850,-809,262,-582,202,434,-344,108,501,-272,312,-377,-146,-159,791,174,-828,652,-368,-218,421,971,631,341,-502,305}));
    }
    public void testDE00118() {
        assertEquals("VOID|getLongOptPrefix=java.lang.String:MHg4MDAwMDAw", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLongOptPrefix(java.lang.String):void",
            new int[]{-780,-1000,19,-1000,23,267,141,-833,-285,-1000,549,1000,-283,-459,24,1000,1000,-534,-386,411,15,-719,199,994,-1000,-261,-648,527,-1000,-1000,-1000,-1000,859,1000,1000,541,718,480,-46,1000,113,319,-1000,-1000,-894,-184,271,1000,-159,-61,-121,-722,-571,-729,-999,-148,-1000,304,955,1000,-1000,1000,275,-1000}));
    }
    public void testDE00119() {
        assertEquals("VOID|getNewLine=java.lang.String:aSBWKytHSm00T2VaWE9XT3hnOA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setNewLine(java.lang.String):void",
            new int[]{-283,-780,419,-543,561,-225,989,181,276,907,702,-854,-218,-429,-692,567,-779,413,922,-60,-106,-266,563,-582,-293,346,-29,-97,-972,998,-21,-980,-10,-935,-163,-510,902,-677,584,-205,672,804,-195,479,-85,-40,936,974,-221,-341,-358,-619,410,-97,751,-685,475,-413,-290,-402,-829,-913,612,378}));
    }
    public void testDE00120() {
        assertEquals("VOID|getNewLine=java.lang.String:MHg4MDAwMDA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setNewLine(java.lang.String):void",
            new int[]{-924,-1000,554,626,-762,909,-1000,732,-601,107,1000,-1000,1000,-1000,115,-1000,147,592,-402,-1000,890,-1000,-995,471,1000,367,456,-345,319,-1000,-1000,1000,-680,556,477,-666,-1000,903,-374,-405,1000,-635,720,-1000,-359,-1000,234,-352,909,1000,1000,-1000,359,84,389,352,1000,272,804,548,-1000,1000,745,-518}));
    }
    public void testDE00121() {
        assertEquals("VOID|getOptPrefix=java.lang.String:OTIw", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptPrefix(java.lang.String):void",
            new int[]{663,431,-811,465,-187,-202,48,498,-14,-985,773,871,-931,-326,166,591,-496,57,1000,-908,-386,632,113,492,-1000,132,-235,334,-558,-795,396,48,259,920,-832,-583,873,-126,682,-56,-633,915,-1000,-32,-726,514,-982,-643,663,228,146,639,308,329,512,653,703,-872,17,953,-130,7,611,436}));
    }
    public void testDE00122() {
        assertEquals("VOID|getOptPrefix=java.lang.String:", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptPrefix(java.lang.String):void",
            new int[]{541,451,69,-189,-1000,-1000,292,1000,85,-1000,-312,-1000,-356,-1000,671,795,262,-233,1000,-447,-1000,759,-1000,-851,-1000,899,-485,-559,-321,-821,1000,1000,-540,-519,-1000,190,1000,-1000,1000,1000,-528,1000,-1000,257,-20,-257,-1000,-309,33,-13,-98,344,1000,-225,806,176,1000,192,253,867,-325,1000,1000,1000}));
    }
    public void testDE00123() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptionComparator(java.util.Comparator):void",
            new int[]{500,-968,-616,214,265,-1000,298,351,-661,912,-413,-1000,1000,136,-1000,-995,-134,-836,1000,-1000,300,-617,-376,-945,-1000,-619,-203,762,-19,-1000,-592,-299,1000,-195,-1000,1000,-568,-428,547,-333,1000,-441,636,-533,595,253,880,-218,193,-190,-834,-1000,-325,-334,-938,50,-421,-552,411,-666,-652,-862,577,181}));
    }
    public void testDE00124() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptionComparator(java.util.Comparator):void",
            new int[]{255,865,-276,389,-137,-276,-1000,-1000,-478,-414,1000,1000,619,-1000,-473,248,-45,598,-447,-325,774,1000,1000,229,1000,-1000,-724,-1000,614,568,163,453,-1000,-1000,536,-690,442,1000,97,-868,-1000,1000,142,1000,866,-1000,-1000,675,-1000,-948,-78,-1000,-1000,1000,839,1000,-393,72,-534,-888,-290,1000,-1000,1000}));
    }
    public void testDE00125() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptionComparator(java.util.Comparator):void",
            new int[]{1000,-728,-631,802,1000,-180,-1000,1000,-1000,219,-548,1000,-1000,-283,-461,1000,668,1000,-1000,1000,1000,-259,1000,-1000,1000,-1000,-961,-336,538,-591,-1000,-1000,397,241,1000,1000,-502,1000,-52,-659,-1000,-52,909,903,-219,-1000,1000,843,-655,-965,-399,-63,-1000,835,848,-7,-497,598,-1000,-470,-363,989,-1000,93}));
    }
    public void testDE00126() {
        assertEquals("VOID|getSyntaxPrefix=java.lang.String:bnVsbA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setSyntaxPrefix(java.lang.String):void",
            new int[]{296,-298,414,400,-171,546,801,877,-525,-955,-43,380,761,-316,229,730,-885,816,263,717,977,-531,-75,484,-303,-709,532,-208,-677,997,509,-16,-687,72,555,59,-332,857,-130,19,-986,-314,82,-614,-230,254,144,-369,663,13,-363,151,-371,-911,-312,372,-644,919,19,-405,-376,621,-486,-744}));
    }
    public void testDE00127() {
        assertEquals("VOID|getSyntaxPrefix=java.lang.String:Nl82LmNzZVZrSkc2elBRTQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setSyntaxPrefix(java.lang.String):void",
            new int[]{-350,-39,154,-3,587,-1000,819,633,-540,-196,49,298,-760,-689,463,-995,-24,-59,1000,-1000,1000,561,-130,-469,-412,-724,-903,45,-313,1000,461,264,-19,-520,840,-250,-137,798,1000,-256,427,-344,1000,582,1000,99,-107,477,609,518,559,155,-1000,716,897,-1000,804,-81,793,298,-1000,415,-553,-221}));
    }
    public void testDE00128() {
        assertEquals("VOID|getSyntaxPrefix=java.lang.String:MHg4MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setSyntaxPrefix(java.lang.String):void",
            new int[]{-229,179,-591,-48,-1000,1000,-1000,165,1000,-95,-1000,-1000,-99,-194,-1000,-425,1000,-1000,691,-1000,721,551,1000,-729,150,1000,319,1000,594,120,800,-45,-1000,-10,806,703,1000,600,-173,336,1000,1000,1000,185,-331,-282,-889,28,1000,-1000,647,1000,-991,1000,1000,-977,1000,-973,93,-1000,536,-405,-132,527}));
    }
    public void testDE00129() {
        assertEquals("VOID|getWidth=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setWidth(int):void",
            new int[]{750,-2,309,-711,766,386,669,342,847,800,-75,628,-178,-761,749,-718,-915,480,46,-187,198,-460,779,-548,14,793,765,368,132,984,-792,555,-244,517,959,642,-213,678,-684,-579,-676,705,559,-571,-325,6,-664,596,-583,-745,-443,655,-867,-446,461,-204,-26,-575,201,-143,427,999,-545,-756}));
    }
    public void testDE00130() {
        assertEquals("VOID|getWidth=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setWidth(int):void",
            new int[]{710,315,-601,192,632,357,-103,41,-74,592,163,-770,-275,166,506,805,-784,419,331,204,179,-24,-1000,-489,589,-638,-752,0,31,667,-284,49,-272,1000,-217,-598,-21,-1000,196,712,-751,692,619,1000,-302,-194,-221,85,-307,407,360,745,-825,-134,246,328,-109,-246,908,82,230,-221,553,1000}));
    }
    public void testDE00131() {
        assertEquals("VOID|getWidth=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setWidth(int):void",
            new int[]{421,-11,299,-447,560,485,986,40,873,724,-493,470,-115,-765,424,-3,-886,247,471,-155,-441,72,243,-452,-403,-483,609,-456,557,411,-976,-702,-561,535,997,181,-1000,743,-592,849,-1000,560,-648,122,-422,-786,235,208,-873,-614,-719,968,-893,-588,1000,-940,-852,269,1000,27,429,946,349,-151}));
    }
}
