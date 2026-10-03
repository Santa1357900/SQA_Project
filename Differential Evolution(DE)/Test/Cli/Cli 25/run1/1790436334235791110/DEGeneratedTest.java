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
            new int[]{542,-1000,-136,291,973,471,785,-1000,-876,-550,-302,-89,308,-72,1000,806,997,745,-105,480,-677,1000,698,683,-214,242,-21,-384,-47,94,139,468,-764,-592,-886,87,-417,-361,-331,581,443,1000,-60,-14,296,-483,-907,-1000,-868,686,-1000,-1000,1000,-62,167,663,-219,-1000,-238,-530,-941,-988,-4,-614}));
    }
    public void testDE00001() {
        assertEquals("java.lang.String:LS01ODkuMzI1", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-407,-128,949,869,854,102,815,271,-617,-783,322,-548,837,901,-589,699,325,271,746,-458,-394,754,293,46,-983,139,-163,67,-304,-758,-138,782,215,-673,724,698,651,-8,814,36,366,-440,593,895,-764,-801,740,-75,-40,-30,-546,-619,-803,770,-20,-642,-242,786,805,781,844,446,-355,-696}));
    }
    public void testDE00002() {
        assertEquals("java.lang.String:YXJn", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{961,-88,534,-264,-1000,-1000,470,403,-1000,-668,-1000,-303,1000,-1000,-1000,-497,-365,576,-874,-1000,1000,-206,-1000,211,1000,-1000,-1000,681,-129,-52,1000,-249,-51,-1000,1000,-1000,1000,1000,-68,-360,-804,-1000,1000,-152,-924,-126,-152,917,441,794,760,1000,-225,384,-813,-389,128,208,343,-633,-10,1000,-1000,-405}));
    }
    public void testDE00003() {
        assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{695,-431,-621,123,-314,240,-972,-69,-282,-50,-636,-514,-1,-818,587,-22,-833,-737,499,-875,-977,-20,-528,440,-502,769,15,846,-945,906,-783,762,-765,544,-202,-511,-969,-90,616,-501,445,565,54,41,661,197,-66,-599,-942,-880,-643,70,517,382,-851,132,357,177,522,-771,627,-885,-57,624}));
    }
    public void testDE00004() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-1000,-129,-491,492,-90,810,584,-1000,463,-1000,-1000,953,-275,-292,-682,544,1000,-186,-37,-137,498,677,-505,-1000,324,192,154,-1000,1000,-490,-365,-982,661,158,1000,435,1000,482,-1000,-251,-463,-411,-1000,1000,281,-135,-244,-308,633,682,-119,-766,-651,-367,-1000,-397,-416,-835,-998,-512,-733,694,333,-57}));
    }
    public void testDE00005() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-88,1000,-182,942,-214,-1000,-445,961,1000,411,348,-351,661,-1000,-256,-61,-1000,-1000,1000,-1000,715,1000,801,1000,-1000,88,-1000,1000,-1000,-191,92,335,312,1000,311,343,-1000,-1000,-136,-1000,242,-694,920,-1000,-403,302,436,-1000,-1000,894,-278,-850,-1000,-1000,533,-1000,-495,-732,951,52,969,-1000,-1000,112}));
    }
    public void testDE00006() {
        assertEquals("java.lang.Integer:OTk=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-936,573,-951,-968,-352,-322,-412,-816,219,-722,227,-122,-934,994,94,798,-413,-802,659,343,-582,318,602,-742,713,775,648,-28,-372,943,518,290,-666,-827,-208,313,434,369,480,631,-478,-152,-686,-309,807,-693,417,-993,-769,-400,-135,414,290,707,901,-103,-84,824,63,-257,-817,-215,333,676}));
    }
    public void testDE00007() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-352,-1000,19,1000,1000,29,-252,400,-472,533,-1000,1000,-1000,790,-1000,507,-584,1000,-220,-639,1000,905,1000,165,689,-1000,-1000,824,1000,1000,-632,-308,-843,628,622,-1000,147,1000,-1000,-1000,-788,-1000,261,1000,295,281,-357,664,-617,-132,363,-58,82,-560,-682,883,610,395,133,142,-123,1000,-71,-1000}));
    }
    public void testDE00008() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{15,1000,-951,-137,-511,573,-412,-816,107,796,-1000,-1000,-292,163,-68,-1000,-372,-1000,-1000,565,725,440,602,-305,-246,208,-291,741,679,-968,-930,-184,81,1000,-208,-1000,1000,369,668,-1000,710,-3,-686,54,1000,644,-150,-993,448,-1000,106,712,-498,996,901,-893,1000,824,-587,1000,-817,-8,-56,337}));
    }
    public void testDE00009() {
        assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-706,-92,-736,-199,719,700,-237,-96,-997,811,230,-270,-756,502,627,481,-650,-442,906,-71,-866,-601,814,527,-506,-232,562,-317,547,-150,-314,595,-739,-815,852,568,815,-270,-170,-223,-317,-950,434,-902,294,-232,-179,785,970,121,-785,552,841,746,-128,-100,-573,-310,-120,-548,670,-979,269,-547}));
    }
    public void testDE00010() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{460,-571,374,-656,-453,-190,204,281,-1000,423,13,130,474,-236,1000,430,178,920,198,1000,-104,1000,-174,-189,1000,831,-718,-145,-83,1000,-226,549,1000,-71,-311,-381,-1000,-442,770,622,1000,426,-4,1000,-445,354,1000,613,-686,648,1000,-652,-611,-814,4,1000,-662,663,-304,-31,1000,791,-411,678}));
    }
    public void testDE00011() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{551,-862,664,-938,-967,-5,56,641,-1000,1000,-103,-984,689,-366,1000,-1000,346,1000,708,1000,-552,1000,-56,-604,1000,745,-895,33,-790,1000,574,1000,674,-71,484,-791,-1000,-1000,507,966,838,671,-4,1000,-823,1000,-300,846,-1000,894,1000,1000,-1000,-926,-167,1000,-839,302,212,1000,1000,791,-265,751}));
    }
    public void testDE00012() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{811,357,909,-61,348,-339,601,-831,-157,-700,-885,244,-704,-730,82,543,614,-757,-630,394,816,311,-978,-857,188,-226,530,427,-758,-917,801,-251,611,-611,143,-331,48,-322,282,-602,629,-579,0,398,-85,846,303,-301,-642,-523,220,99,563,-558,306,-875,132,153,-299,720,-204,-508,210,965}));
    }
    public void testDE00013() {
        assertEquals("java.lang.String:Wl9fTzI1X1pmUTY=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{904,1000,229,-1000,467,-1000,630,-1000,-27,-400,-1000,400,-274,-102,87,-114,61,-1000,-1000,192,144,-208,-1000,-365,-908,904,1000,-66,185,-1000,1000,-990,400,-400,-639,-545,-500,148,390,-619,826,-1000,-530,912,203,1000,556,-1000,-107,521,145,256,-595,-325,524,-164,325,-663,-808,1000,-1000,1000,-316,74}));
    }
    public void testDE00014() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{479,-198,894,-435,-213,-1000,235,-1000,-898,-364,34,-200,-672,-524,473,1000,588,-489,-1000,734,1000,1000,173,-1000,200,-999,-804,848,-873,-493,222,-363,838,-30,763,-226,-578,-1000,-744,-1000,1000,439,-1000,1000,-887,1000,-519,-375,-1000,-282,1000,-376,1000,500,-22,-1000,-158,295,96,428,676,-780,434,1000}));
    }
    public void testDE00015() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{793,317,-541,297,-384,21,6,-700,798,-511,-75,-557,263,-803,498,-1000,-117,1000,-286,459,1000,-861,1000,-336,-1000,-932,-982,-317,-1000,-506,-868,954,-1000,379,703,-914,-353,1000,-823,613,369,470,-3,574,-1000,-65,122,89,638,-417,1000,-1000,-253,159,-564,437,-836,-85,-1000,-339,134,1000,1000,183}));
    }
    public void testDE00016() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{113,1000,-461,418,776,-1000,280,-581,723,-922,-78,807,931,-1000,-582,-501,7,1000,325,-102,566,453,334,-778,-661,-1000,491,1000,1000,-141,-237,644,1000,-548,306,995,-1000,-332,-616,1000,-464,663,-970,1000,-153,-146,-1000,834,1000,200,1000,-1000,-1000,-1000,-144,773,661,43,-1000,1000,716,-1000,182,608}));
    }
    public void testDE00017() {
        assertEquals("java.lang.String:OVhhMFhacgo=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-503,-300,-386,-710,822,-995,-373,527,256,-183,-717,-346,-154,-203,-284,-864,-365,666,425,-1000,956,392,267,-241,-1000,-599,1000,903,1000,528,-1000,799,914,-154,806,-193,985,1000,1000,-177,-156,-169,-432,910,-1000,5,-566,-557,8,-71,192,-1000,-1000,-904,298,398,-116,-84,-13,477,957,-1000,322,1000}));
    }
    public void testDE00018() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-839,192,434,-8,-230,-457,-167,-87,363,-704,-990,-866,-284,774,259,222,237,-314,-285,-630,531,656,55,297,-324,-673,633,624,-206,-319,843,-673,908,763,179,-124,587,-980,254,306,393,106,-137,568,-203,-784,61,-713,-133,-281,182,285,-197,225,-236,119,-845,46,-610,-747,-548,507,-376,-518}));
    }
    public void testDE00019() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-743,-202,-96,-1000,195,-890,183,343,164,-350,374,-1000,-315,-695,621,864,640,318,-855,269,764,157,650,-17,-164,-409,1000,548,-595,-267,-220,101,710,1000,-766,146,1000,-663,15,-616,1000,176,-615,-248,574,45,-830,447,662,-837,377,-625,-1000,-401,1000,-684,-850,-745,-179,708,-1000,-1000,-1000,-62}));
    }
    public void testDE00020() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-383,1000,149,-222,-712,1000,796,-1000,339,992,1000,61,1000,597,-220,-752,-424,321,682,583,225,-1000,-863,-1000,-109,529,347,-1000,386,1000,488,-865,-1000,91,1000,-95,-1000,1000,-649,1000,-225,-1000,-660,540,-1000,138,-1000,1000,-214,442,780,-474,5,-610,394,-368,1000,-1000,617,1000,389,-1000,-211,1000}));
    }
    public void testDE00021() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{-602,379,324,-1,856,-180,348,-36,-2,-183,1000,-1000,-310,1000,-864,830,55,-80,811,534,121,1000,224,-814,-1000,426,390,-237,-118,-149,-1000,1000,-328,586,292,6,-234,275,-29,875,230,-567,1000,-100,60,1000,150,750,81,-300,-1000,-159,556,-1000,1000,1000,-603,-44,1000,829,-924,-831,-995,-113}));
    }
    public void testDE00022() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{1000,-688,484,1000,-308,-507,-34,-1000,1000,-789,37,-452,-11,-725,-230,1000,-468,828,829,169,1000,-684,-1000,372,-1000,164,1000,-506,400,-1000,-756,814,-321,-315,804,859,-1000,707,1000,602,-1000,284,88,718,1000,-820,-734,-1000,825,-1000,335,-1000,1000,-635,-198,512,1000,494,-400,785,-259,179,797,-465}));
    }
    public void testDE00023() {
        assertEquals("java.lang.String:LS0weDEwMQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{584,770,9,6,1000,-1000,65,583,279,-257,883,-491,1000,404,833,-1000,-1000,468,-719,-82,-954,-253,592,3,-781,-943,-623,-528,-1000,-268,496,1000,370,-947,-317,1000,42,595,1000,808,511,1000,479,492,-517,591,1000,247,-507,-520,-314,-374,53,-461,734,-1000,693,-90,272,-1000,-1000,-283,411,-1000}));
    }
    public void testDE00024() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{688,1000,-801,-798,569,960,-444,-325,-4,253,-740,169,-1000,-155,-349,-806,788,-120,25,-622,1000,444,1000,-1000,-85,1000,-64,-142,640,518,-1000,634,-1000,387,901,257,-1000,226,266,350,1000,-339,-701,692,212,-145,776,1000,130,786,-433,503,-361,1000,-1000,-1000,1000,-1000,326,715,-418,-1000,-1000,-304}));
    }
    public void testDE00025() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{900,-402,-121,319,708,40,957,324,341,363,-1000,1000,-1000,560,-59,-471,159,1000,-655,-1000,-1000,353,461,91,86,1000,1000,-1000,-303,1000,366,-271,-79,645,880,-1000,-1000,-312,-1000,405,797,-853,278,-1000,610,-133,-1000,605,1000,349,689,93,554,644,-832,-1000,604,-696,274,1000,1000,400,-1000,228}));
    }
    public void testDE00026() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{-486,-583,-276,492,-551,1000,722,511,565,-484,-45,-356,1000,-755,-2,-520,-527,853,-1000,-440,-1000,754,-75,1000,-423,1000,1000,-1000,-846,1000,26,-1000,716,-53,-55,-1000,-1000,-1000,903,-112,161,-1000,1000,-995,1000,-848,-1000,-445,1000,635,-1000,-1000,-605,-1000,1000,-293,1000,-54,1000,-1000,1000,-1000,281,1000}));
    }
    public void testDE00027() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-368,15,1000,171,-2,-147,9,337,748,698,-6,1000,35,-15,654,-455,1000,-194,-628,-1000,-1000,-641,-929,20,303,-524,-968,-643,-281,-681,600,-1000,-839,76,175,1000,-829,-1000,-1000,-867,777,-263,-394,1000,82,522,1000,-653,577,634,1000,-727,1000,-1000,573,354,-55,-1000,-988,568,918,-1000,-39,817}));
    }
    public void testDE00028() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{617,877,418,195,-928,1000,800,-69,23,873,-364,1000,243,-443,-1000,23,1000,-296,352,-855,-934,10,-365,416,-424,-731,425,-843,384,-33,-8,687,-281,1000,-555,146,141,-849,-1000,409,1000,321,-1000,-116,354,1000,-1000,-786,-694,-453,1000,-1000,933,-1000,-64,-164,-135,-1000,-394,459,-1000,-1000,14,71}));
    }
    public void testDE00029() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{20,1000,154,-612,-362,-645,765,-589,275,-397,31,-64,143,-201,-807,-250,273,713,917,-898,-671,-399,493,-231,-772,-498,-967,-601,-93,70,-180,-170,-712,385,-414,850,-197,520,-1000,447,880,-316,-137,163,491,180,-1000,914,637,363,401,-829,354,-240,226,1000,94,-863,-959,216,53,-822,778,494}));
    }
    public void testDE00030() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-649,-487,-133,387,297,47,519,167,-59,1000,-1000,274,1,210,-1000,-941,1000,430,278,-615,-4,-108,299,926,1000,-733,-489,234,-1000,-363,332,-640,-130,580,328,300,-901,-142,-783,-1000,708,-1000,-1000,1000,-400,1000,-65,968,89,223,1000,52,988,-528,-823,240,-116,-736,-933,-560,695,-852,763,464}));
    }
    public void testDE00031() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{517,-184,1000,-644,-767,111,-192,912,542,-1000,-96,-542,-1000,737,171,-180,-291,94,-1000,-924,-139,-717,178,-825,-243,-379,-53,-40,401,-891,-587,-500,-431,-879,-505,819,-1000,121,273,134,-670,911,1000,909,-780,158,1000,232,768,400,215,-276,12,-528,846,-70,-829,-876,-871,463,848,468,904,520}));
    }
    public void testDE00032() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{1000,1000,875,871,-88,-98,1000,-73,-690,-157,-773,1000,-173,93,-244,392,-57,-1000,621,-95,-1000,-567,420,865,656,-411,723,-1000,368,1000,335,692,-330,222,-922,695,1000,-556,-853,-408,842,892,-1000,119,760,460,-942,-1000,-406,-183,658,-1000,528,-99,77,-315,970,-1000,-667,-167,-1000,-927,-765,-644}));
    }
    public void testDE00033() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-368,-26,439,-222,-1000,1000,1000,1000,-828,-496,-1000,-66,-1000,1000,654,1000,-1000,170,939,-179,834,-309,1000,16,-1000,-233,1000,-643,343,-13,-1000,1000,855,430,-902,-786,187,48,1000,-867,-955,308,737,-372,-1000,221,773,-653,-381,-1000,-761,-108,-406,1000,-290,-1000,-660,-1000,-1000,-135,-1000,950,1000,-1000}));
    }
    public void testDE00034() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,428,-992,1000,-965,356,1000,-1000,-617,-791,-170,354,-566,692,344,887,524,890,285,-83,39,-340,-460,-144,-61,-122,336,-526,236,785,530,744,543,672,-850,-441,888,333,-1000,-369,-1000,-74,-463,532,294,-435,-468,-1000,1000,-384,-759,592,-531,1000,-229,1000,821,-832,-1000,-1000,-1000,184,836,-906}));
    }
    public void testDE00035() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{517,499,439,-1000,-1000,1000,194,529,198,35,-900,-513,-1000,219,-283,1000,417,-330,3,-789,-139,-395,-20,228,-1000,-882,1000,-832,824,-880,-1000,813,38,54,-780,-786,-259,48,1000,-354,9,282,737,-796,-947,934,-321,-567,-381,-1000,59,-857,515,-316,-547,-1000,-1000,-834,-1000,1000,-552,305,1000,-680}));
    }
    public void testDE00036() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-346,901,-51,-158,665,603,952,418,-605,-663,-756,576,893,81,219,-685,-178,54,647,324,782,912,947,664,-366,-600,731,-84,431,-505,-545,533,798,439,-907,646,-862,-782,224,904,-144,-968,630,-949,-51,-922,591,-114,-200,-408,-605,43,731,-524,-694,559,38,124,195,-396,546,22,350,954}));
    }
    public void testDE00037() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{390,613,56,-445,1000,392,-386,-674,803,382,-618,192,376,-498,-640,-327,556,-501,209,-108,566,461,746,-50,-214,-984,564,1000,291,-701,-456,864,1000,115,-677,-174,-1000,-999,-1000,1000,-535,826,18,-1000,728,-829,84,-1000,852,1000,-264,-290,778,46,-426,1000,82,1000,-891,-197,151,-1000,-1000,-694}));
    }
    public void testDE00038() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{885,664,801,609,95,-302,-1000,-827,-136,-822,-1000,580,-1000,1000,47,-1000,-373,-767,1000,176,1000,-10,557,878,-562,1000,374,1000,152,-125,-1000,-1000,577,-503,-919,-115,-1000,-1000,-454,466,291,494,26,-432,728,-287,108,258,-362,-163,657,-1000,436,116,-979,137,-483,-99,-268,812,-1000,-27,-1000,310}));
    }
    public void testDE00039() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-27,-177,169,1000,608,353,-363,-795,-87,580,-108,1000,-868,895,462,-1000,-331,-1000,-1000,817,1000,1000,180,510,369,-258,845,50,242,1000,-558,-1000,-387,-1000,-194,534,257,-1000,247,-1000,1000,-348,468,1000,-697,-1000,247,1000,-198,-1000,-496,117,540,-468,-385,258,685,-557,1000,491,-1000,1000,-732,759}));
    }
    public void testDE00040() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,1000,107,589,-471,-195,1000,-310,746,622,-636,-475,-1000,226,-533,223,-754,-546,-1000,-114,455,-833,1000,-709,-527,566,533,879,334,-347,-1000,-1000,-32,688,1000,-412,-1000,-849,-930,992,-570,1000,111,-432,1000,794,679,-127,-330,331,-1000,445,-21,339,1000,986,-431,-225,185,1000,-506,-660,-165,141}));
    }
    public void testDE00041() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-426,180,694,473,665,958,1000,1,-1000,72,-509,576,893,225,219,-695,-193,-433,521,617,-505,1000,291,910,-567,-861,873,-84,528,-398,-142,250,466,218,-1000,646,-729,-1000,606,-204,741,594,783,-277,-902,-1000,505,1000,306,-1000,-605,292,1000,-1000,-933,-45,14,207,541,-527,69,262,516,827}));
    }
    public void testDE00042() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,-166,247,841,732,-1000,653,-1000,-251,630,-377,727,133,829,1000,-74,990,10,-83,-372,-1000,-351,286,-1000,1000,964,1000,156,-1000,-58,-325,354,-1000,1000,-1000,1000,-181,513,-1000,-340,-1000,1000,893,-1000,-526,1000,-1000,626,1000,294,1000,410,-91,1000,1000,1000,-560,316,1000,309,-1000,313,166,-415}));
    }
    public void testDE00043() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-651,468,-521,444,-596,27,571,873,515,276,1000,-94,-131,328,-47,170,-193,-479,274,521,-171,566,520,515,-82,-171,267,-1000,779,293,-114,233,-287,-425,490,-495,-188,-748,-112,-472,315,-410,482,170,-753,127,36,687,-986,-711,-354,563,-478,-717,520,-535,-422,-759,277,187,-876,774,169,822}));
    }
    public void testDE00044() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-492,-445,794,809,-604,1000,1000,-64,-1000,261,237,-431,649,865,-902,466,-60,-354,274,566,1000,1000,-389,688,-339,-98,-318,18,-151,-650,43,287,377,-1000,132,307,-560,-1000,596,-607,648,-1000,330,220,-850,-366,31,-1000,18,-891,606,1000,639,826,505,864,-874,-54,307,-760,431,697,959,-280}));
    }
    public void testDE00045() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{1000,485,167,-35,-400,-647,-9,174,-78,-115,-600,-633,-34,337,30,277,72,-162,-29,68,646,184,269,-84,444,-1000,-191,308,-644,602,547,1000,-3,34,1000,688,-419,-70,-166,-527,-494,-655,496,20,-286,453,-399,-363,1000,-671,554,417,-80,749,-552,373,-33,260,-345,608,-793,-916,-404,-580}));
    }
    public void testDE00046() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-66,930,-371,219,154,380,1000,-607,-878,-651,177,-1000,1000,325,245,-92,735,495,856,-260,448,183,-306,186,975,178,193,765,150,-266,746,621,580,-214,1000,-542,14,-632,-190,-355,-992,649,147,-256,588,-515,-636,-111,-335,-447,748,641,1000,454,261,624,79,-965,379,705,158,-1000,188,-1000}));
    }
    public void testDE00047() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{25,315,-451,277,412,243,-152,1000,841,1000,-164,-1000,-290,503,919,-1000,150,913,-243,-73,765,168,418,112,435,-766,-362,-734,-945,1000,496,1000,-94,-473,108,346,-1000,436,-737,-1000,-16,-1000,-10,87,-443,-19,-134,-701,1000,-252,-347,-439,-706,1000,-287,-273,648,-19,95,129,1000,-556,1000,579}));
    }
    public void testDE00048() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{1000,-534,-361,-804,144,-1000,359,1000,1000,-92,239,-450,-1000,780,1000,873,-897,1000,387,-1000,521,-1000,-1000,-1000,-290,229,749,-628,-696,1000,526,192,-567,-164,346,966,-17,387,1000,-663,1000,-51,-1000,1000,-306,1000,1000,-343,1000,-751,-1000,-1000,834,875,224,-1000,14,-1000,399,-180,1000,-862,-623,-1000}));
    }
    public void testDE00049() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,332,868,-432,19,-1000,-487,-1000,47,-673,1000,8,408,1000,-491,1000,-251,-784,-823,-6,45,-238,-400,-958,-3,-529,848,310,-1000,571,394,63,-1000,1000,1000,-118,-145,-215,700,-1000,411,204,-680,643,1000,1000,1000,-1000,930,-1000,-789,-904,289,1000,-666,-938,371,440,851,233,297,-1000,-573,-390}));
    }
    public void testDE00050() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-639,983,287,-376,153,-473,-668,145,-308,515,-302,-64,763,744,-288,902,504,-343,-305,612,405,996,872,-607,198,-709,-971,115,-823,208,234,992,-62,365,387,-491,-666,-330,-22,-851,-499,-575,473,-14,227,524,-973,-620,640,-109,-132,69,-780,-12,-415,-273,490,-302,-146,710,355,-821,644,-821}));
    }
    public void testDE00051() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,761,64,-27,486,-430,-406,888,-120,122,-667,-95,819,744,332,1000,-366,208,631,-7,405,1000,888,-680,-549,-379,-784,-212,-771,454,420,1000,828,486,-362,-414,-619,-1000,243,-851,-324,-685,473,1000,91,1000,-745,-63,640,310,-67,69,-1000,118,577,-687,1000,-110,-146,456,635,-694,79,-821}));
    }
    public void testDE00052() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-700,27,-1000,1000,-233,633,-766,509,-226,-506,-767,282,-1000,-84,-979,-1000,-1000,850,-254,-482,1000,-373,294,1000,-18,371,-1000,533,397,1000,-1000,1000,-486,-480,-1000,-163,293,1000,-1000,1000,758,-834,-533,898,63,249,476,-189,758,662,496,236,871,-953,1000,-640,-491,929,-1000,936,-287,975,673,137}));
    }
    public void testDE00053() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-115,328,424,1000,1000,-763,-48,862,610,31,232,-1000,2,-515,-333,-41,-781,-83,70,-1000,-568,-310,-1000,-697,-662,-251,-504,224,96,-90,-1000,587,1000,-1000,464,398,869,-433,-870,31,197,-627,32,978,-1000,834,229,956,526,773,26,-489,884,560,-403,-384,778,1000,-512,795,-247,1000,-371,-1000}));
    }
    public void testDE00054() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-484,1000,289,696,74,-1000,-960,1000,149,720,560,-23,-35,95,578,-962,-1000,1000,629,-1000,1000,657,-1000,-324,-398,-432,-1000,-1000,-19,687,-1000,1000,-1000,-50,-433,-1000,-685,571,-69,-60,-1000,-237,1000,-25,952,-119,-647,-91,844,-13,245,280,646,-899,-703,1000,1000,-193,-664,421,-413,722,-572,689}));
    }
    public void testDE00055() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{140,811,624,81,1,-905,198,616,923,568,594,-704,-113,285,-168,-961,-887,938,-193,-366,775,118,-199,-834,693,-117,-745,48,-463,-339,-985,347,-362,117,-448,-399,-995,612,764,220,-629,126,880,745,193,-860,-287,-290,-624,-168,57,702,238,199,370,277,700,197,56,875,-974,868,-371,588}));
    }
    public void testDE00056() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,-272,264,890,-533,-666,-935,-596,1000,-28,-489,332,1000,64,-612,735,466,-110,-19,569,450,-378,-213,-87,425,-487,505,-727,634,-237,946,679,609,440,-1000,-36,-132,572,-934,1000,-92,775,-671,-385,400,-284,800,-812,-522,-241,833,1000,23,-335,-391,311,-645,-350,439,-578,-191,-1000,228,1000}));
    }
    public void testDE00057() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,-592,1000,-801,-691,-415,838,-531,-419,127,815,261,796,-162,218,1000,1000,-1000,-285,-1000,-384,1000,-1000,-697,115,906,-179,-1000,96,273,407,601,1000,-349,1000,-834,283,-176,977,-898,629,-1,695,-655,-381,-13,822,-64,1000,380,-645,-329,1000,1000,11,149,647,-804,-696,-29,-297,4,-201,-570}));
    }
    public void testDE00058() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-363,14,-336,625,-765,535,-636,-64,-146,621,-489,85,-322,957,-261,-652,-640,-155,472,-116,-134,-665,-744,431,-767,261,-164,248,840,273,-701,103,-524,-325,-83,630,878,781,416,272,-279,-195,323,215,92,-96,756,178,-653,-149,-413,775,-999,-930,229,-756,740,264,-496,-200,34,-287,987,-407}));
    }
    public void testDE00059() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-1000,720,941,744,-513,-219,465,-676,141,693,191,-1000,-832,835,-410,-425,-1000,9,-903,-960,137,584,1000,1000,1000,742,122,-1000,399,853,-1000,-756,-79,462,72,-929,-224,-1000,-400,941,-766,195,235,-803,1000,101,783,-1000,-1000,-1000,-1000,-669,358,-417,-587,1000,346,562,-802,1000,1000,437,105,-832}));
    }
    public void testDE00060() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-1000,231,469,-912,-166,-1000,317,-1000,1000,1000,-1000,-581,-864,-392,-742,38,-802,-1000,-1000,-52,2,560,1000,1000,1000,1000,826,-268,-108,602,-440,-1000,162,-16,1000,1000,-736,-520,954,-809,-176,-339,37,-986,317,-348,391,-1000,-636,-372,-1000,-652,1000,-1000,473,1000,1000,-959,-657,479,-45,515,256,-58}));
    }
    public void testDE00061() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{991,-468,-76,813,-1,-171,-111,738,64,-123,385,-954,259,965,715,916,246,-255,293,827,448,200,981,-802,-935,-927,75,222,-320,691,-825,-729,-294,-861,-53,819,-554,-591,-402,-961,169,301,801,912,-729,631,749,-30,-671,600,25,-329,-351,69,90,778,-221,904,279,-896,379,-417,-540,511}));
    }
    public void testDE00062() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{45,-640,-994,-414,414,-1000,778,-723,-984,-157,-530,425,660,33,-1000,-1000,-1000,470,-184,1000,-783,1000,877,1000,-1000,-1000,748,-757,-1000,37,852,952,1000,-1000,280,965,-913,-511,933,488,945,76,-521,1000,1000,-1000,-1000,966,-615,-982,-725,-1000,1000,336,603,289,-679,501,1000,777,584,526,169,177}));
    }
    public void testDE00063() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{110,-1000,-727,-516,239,-36,-183,-140,-1000,-1000,-493,131,-322,-504,-1000,-487,-468,1000,1000,312,58,977,-29,853,779,-1000,93,-973,-1000,796,661,27,722,-295,-142,90,418,-442,459,1000,795,641,164,1000,1000,-1000,-339,1000,-1000,-914,67,-734,348,1000,1000,289,-830,1000,1000,468,545,-262,-220,253}));
    }
    public void testDE00064() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{589,484,397,-448,-513,319,-172,1000,1000,-65,-3,1000,-426,191,705,769,-68,192,-683,-214,-1000,584,-36,-485,1000,27,557,943,1000,460,-755,-476,875,335,72,1000,377,156,13,-1000,-878,-586,-400,-1000,-360,257,131,-135,629,-1000,-1000,-282,-429,-1000,-533,502,991,-723,-1000,-529,150,-653,-558,50}));
    }
    public void testDE00065() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-61,-72,123,-885,-458,1000,-1000,871,-348,-997,803,174,188,-273,-575,-250,35,501,-46,619,-842,909,1000,1000,169,-923,118,-446,-441,612,561,505,713,200,-547,357,855,-282,-421,693,1000,840,-1000,1000,56,-314,-474,231,327,-756,-262,26,765,18,-228,643,-455,1000,805,461,986,662,652,-1000}));
    }
    public void testDE00066() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{452,-740,-685,-419,1000,373,447,-935,905,-1000,-616,993,54,29,282,-10,-460,-1000,7,1000,-295,-102,-943,887,1000,-356,806,-419,-722,-444,-1000,-1000,-377,182,1000,0,-1000,701,-19,0,-508,650,290,1000,-385,-812,-768,-444,106,807,768,144,-282,1000,1000,-867,-522,-210,-362,-1000,1000,-1000,0,-573}));
    }
    public void testDE00067() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-53,624,824,436,-485,675,599,353,-570,-418,85,-640,-653,998,-286,-853,-447,-449,-707,543,-112,-933,-574,881,-710,-884,457,908,-407,441,-851,-19,868,-260,-818,605,88,-468,-861,11,170,-624,-863,-521,571,605,493,-679,-482,-105,-105,-907,-312,-506,442,459,-314,-245,603,359,-972,701,651,660}));
    }
    public void testDE00068() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-797,-299,-361,1000,265,408,-254,-1000,-1000,1000,-1000,723,-1000,1000,129,1000,-196,1000,-973,-175,372,-550,-896,-1000,-814,1000,907,385,219,287,-107,1000,1000,97,-550,-211,99,-1000,-1000,51,309,1000,-1000,-871,549,1000,1000,-745,166,-447,-728,882,-1000,-475,-1000,246,-318,-1000,1000,1000,-1000,684,-1000,1000}));
    }
    public void testDE00069() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{1000,-175,-656,-799,34,13,1000,-368,1000,-1000,119,178,429,-25,-511,-126,304,-1000,151,1000,-535,954,-164,-27,1000,67,-1000,-1000,595,-119,-928,328,646,476,1000,-1000,-335,-303,-313,-362,-173,87,28,212,281,-838,-950,78,71,336,1000,-236,273,1000,664,-1000,78,-51,-457,-1000,54,-1000,563,1000}));
    }
    public void testDE00070() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{740,-293,-428,-1000,-235,-416,-1000,-1000,-119,-163,-1000,-142,-820,1000,496,203,536,-431,546,-1000,-722,-866,-991,295,1000,588,852,-1000,-761,-1000,-1000,-807,278,-817,-1000,1000,-492,826,1000,1000,1000,1000,-235,1000,-545,-1000,1000,1000,-1000,-982,-231,1000,629,305,326,1000,1000,-190,1000,773,1000,-310,317,-1000}));
    }
    public void testDE00071() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{544,-152,-463,-9,425,418,1000,-600,-510,67,-853,466,142,320,979,962,-463,-126,-220,480,-655,-255,-750,69,682,-355,465,295,-314,-1000,-464,-535,513,88,-885,552,-36,319,66,-673,702,769,-208,537,302,-832,58,-105,-303,114,989,1000,455,962,218,584,307,29,631,500,918,-959,18,-272}));
    }
    public void testDE00072() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-398,-118,13,767,-448,-819,861,-317,-898,-69,-790,642,-995,435,-62,785,-441,453,831,-216,800,-122,-108,-433,-402,291,-561,-481,-969,450,452,-133,27,-367,-557,-735,788,-964,762,267,-243,982,-303,334,-962,541,-18,292,-954,382,-591,741,-57,-874,-883,-164,913,322,-176,-950,710,-748,-204,-930}));
    }
    public void testDE00073() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-785,-11,-371,1000,-454,1000,573,531,-1000,379,-485,821,311,-530,653,1000,-705,-625,-557,-919,745,15,-457,-310,190,-1000,438,1000,-277,-496,527,-135,463,-713,-626,-552,33,-336,-487,-1000,990,-204,225,-591,1000,275,347,-1000,-297,534,255,45,-839,915,54,-81,-348,544,-155,1000,97,441,-395,767}));
    }
    public void testDE00074() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{220,-106,-906,530,-1000,235,11,-326,669,50,40,732,464,307,-732,-74,-156,-957,-163,858,-83,-49,1000,179,-898,-11,216,1000,-669,-56,-204,-200,690,373,702,-1000,680,542,1000,-100,-374,-576,-333,1000,277,-509,-770,-383,-189,883,-488,-1000,164,726,-1000,-108,1000,230,-136,-794,137,-417,-115,412}));
    }
    public void testDE00075() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{992,536,509,-279,986,152,738,-902,-862,-639,-560,-485,-287,852,-348,-127,965,590,764,860,-133,712,-469,-396,730,-75,-762,-492,165,-497,246,-449,299,-132,608,-530,82,625,412,-546,-913,-50,119,-763,-710,-773,-759,-174,-38,12,-554,-536,-948,490,-633,-86,-801,-90,-726,-359,-129,-279,92,-929}));
    }
    public void testDE00076() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{-967,-47,193,-172,-834,131,-608,-326,-522,-19,195,200,61,-69,-324,-1000,325,-840,72,-56,-535,706,-247,-382,-464,205,-711,113,1,-56,359,-1000,-443,-209,903,58,326,201,-34,-531,-1000,-660,-124,-671,377,-87,-931,-843,-69,169,-578,-586,-73,32,-537,416,169,616,472,-672,-474,-397,20,-439}));
    }
    public void testDE00077() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-1000,1000,738,1000,-1000,-1000,458,-1000,650,-1000,-703,-85,-437,1000,-39,-1000,1000,-1000,301,-323,-1000,538,1000,-86,-472,-449,-1000,761,-820,1000,62,820,-622,993,-1000,-1000,-149,1000,1000,1000,-1000,-123,-1000,1000,1000,836,1000,270,-1000,519,-476,107,-1000,660,-737,734,142,983,-861,409,420,1000,156,275}));
    }
    public void testDE00078() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-960,-997,-354,777,-879,-424,-700,663,49,-406,-1000,109,-698,-341,464,-80,312,-1000,1000,-446,-1000,960,192,-253,-250,-984,-951,-24,749,776,913,1000,-272,1000,94,-913,-797,1000,1000,1000,-597,-672,-1000,249,1000,826,-1000,128,-535,101,-657,-7,-898,557,-716,-914,751,654,-708,-51,-249,-105,-312,323}));
    }
    public void testDE00079() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-652,685,144,636,-184,-929,383,956,419,-766,484,-586,-163,702,-310,-483,349,-544,-186,-677,-1000,999,593,985,673,-416,-966,1000,477,-400,547,612,520,261,-617,-9,-871,-265,340,-846,-376,83,642,1000,773,-195,478,-413,-33,-949,-804,984,255,816,63,-1000,-456,-615,383,-560,817,485,766,-254}));
    }
    public void testDE00080() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-276,178,-57,1000,-812,-934,752,193,920,-929,203,-734,-303,663,-804,-969,-135,-1000,239,352,-1000,448,64,824,424,-674,-1000,1000,174,629,-817,1000,-860,-430,-525,-993,209,68,974,605,-425,-793,-623,1000,320,65,259,-85,283,-752,296,-74,435,407,-829,449,-51,103,173,25,1000,1000,12,-1000}));
    }
    public void testDE00081() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{490,-1000,-642,96,-50,-383,-525,1000,-123,-58,312,-390,-668,-306,-33,1000,-401,-122,795,-658,-427,-123,-160,115,1000,-126,917,-409,1000,-912,1000,1000,-936,81,1000,-18,-487,526,167,397,636,-821,168,-593,1000,-340,-1000,40,1000,-907,-927,431,995,823,76,-450,534,-685,400,-1000,105,-480,-276,-616}));
    }
    public void testDE00082() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{1000,644,-602,-1000,-480,376,254,235,-473,362,472,-109,-696,-1000,727,-465,-724,104,626,-218,391,58,394,-252,37,-440,233,35,-410,-181,654,470,-301,-485,1000,514,631,345,454,699,-677,-174,606,516,1000,-246,-898,-188,-1000,856,599,1000,467,-63,0,556,357,-1000,409,504,880,-271,-833,-429}));
    }
    public void testDE00083() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{1000,-696,-533,267,-860,149,516,22,266,553,1000,1000,-1000,-1000,172,911,-715,-778,217,-332,563,170,351,346,-345,-553,563,-547,-2,-116,604,1000,-397,-278,1000,880,308,-591,1000,1000,-1000,440,1000,261,763,196,-1000,-414,-183,1000,43,587,-365,-436,-328,-305,1000,-1000,902,442,-101,910,143,-1000}));
    }
    public void testDE00084() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{861,999,22,-920,734,1000,38,134,63,1000,198,-569,1000,-1000,441,-750,553,-729,1000,-864,1000,-1000,-495,-1000,-744,-236,-45,904,-104,-1000,-499,89,871,832,-918,0,1000,182,-1000,-1000,707,-479,791,-274,1000,268,1000,1000,-1000,-993,-660,1000,1000,1000,501,1000,556,817,-686,-67,79,-119,-400,12}));
    }
    public void testDE00085() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{82,285,545,781,1000,181,576,161,-203,-1000,-431,-1,258,1000,820,340,992,1000,-1000,1000,-152,19,904,-146,540,1000,-1000,843,-289,1000,1000,-984,-222,-338,291,883,696,665,-342,432,-242,-25,473,760,661,-1000,18,549,76,-1000,883,117,452,-1000,-1000,38,961,168,-214,-1000,-986,687,-82,-615}));
    }
    public void testDE00086() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-228,-460,970,44,-368,-800,-1000,196,663,-827,-130,-940,-645,580,1000,1000,-735,1000,-427,709,-1000,-176,-731,-160,1000,1000,-712,-1000,-942,1000,225,138,-1000,218,373,772,-1000,516,1000,695,586,-1000,-573,-810,-876,-854,-1000,-1000,-635,-1000,1000,-1000,-979,-181,240,-145,-15,-715,1000,709,657,-385,-223,1000}));
    }
    public void testDE00087() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{2,-1000,-569,-731,302,141,-358,1000,820,-6,597,419,946,-502,-19,1000,87,184,-681,546,1000,-996,-139,-57,-310,-140,-504,-737,-833,-1000,947,262,23,-578,1000,533,631,382,367,-1000,-352,891,993,-1000,46,-186,248,936,-174,-973,66,1000,556,682,-573,-718,1000,-1000,882,-80,-591,-49,550,530}));
    }
    public void testDE00088() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-58,-1000,415,-1000,104,949,1000,222,-131,867,-215,-301,225,1000,-877,-611,-1000,-399,-903,405,733,-653,-1000,-594,-642,-147,-1000,-719,-990,400,879,-1000,-110,546,-1000,-1000,944,1000,-423,482,1000,-1000,816,-279,-1000,689,-1000,-850,-548,1000,254,-833,1000,-166,-962,1000,-920,-1000,128,-1000,859,-926,-412,-1000}));
    }
    public void testDE00089() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-125,-220,-631,-383,-1000,-948,-1000,1000,398,239,-730,1000,225,-643,-129,854,-1000,-399,-256,326,-197,970,-1000,65,44,687,723,512,-990,461,647,-1000,-931,-437,-307,202,-542,537,-949,648,-951,164,-713,-232,-1000,689,937,771,-898,-1000,264,188,-244,-166,-456,192,-514,1000,1000,-1000,-493,-413,605,86}));
    }
    public void testDE00090() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-666,431,-801,710,339,658,-528,104,-1000,-624,-290,1000,-1000,-1000,486,163,-64,42,1000,-31,-1000,-966,-58,-120,497,1000,1000,-693,176,-1000,995,-719,1000,-860,39,311,80,409,1000,-780,-1000,-262,360,-171,816,-987,1000,1000,-740,234,-882,-183,870,265,146,-1000,-93,-54,793,914,-810,1000,710,1000}));
    }
    public void testDE00091() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{1000,1000,-666,333,-172,-1000,-1000,675,-259,-19,402,578,453,-1000,358,230,-305,-335,689,-450,607,452,-816,194,-976,309,1000,53,-1000,850,-513,-694,-1000,-286,141,-77,-308,124,640,552,385,1000,-437,-1000,1000,-491,1000,-167,-1000,-1000,-244,750,134,252,1000,-251,659,1000,809,-211,-398,1000,528,-882}));
    }
    public void testDE00092() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{28,-922,89,-395,-347,-1000,-83,500,-528,1000,-183,-385,951,400,-486,90,-126,-599,-638,786,854,-103,133,641,-1000,149,-553,-1000,245,649,224,-1000,-345,349,-1000,-1000,251,536,1000,-510,1000,560,210,-1000,-201,422,-237,-926,-802,-731,-511,-756,-606,-502,-381,1000,845,-322,-1000,-1000,561,1000,-1000,-1000}));
    }
    public void testDE00093() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-46,50,1000,-699,218,1000,1000,-509,-705,-723,766,-1000,-510,1000,-1000,-594,-939,-1000,-546,319,-197,-53,-890,-70,-898,-1000,-1000,-73,-589,548,1000,-585,1000,-613,1000,-268,-141,682,902,996,-63,-856,1000,1000,-1000,1000,-1000,859,1000,1000,787,-327,128,475,330,-366,-1000,-1000,-599,-1000,1000,-1000,-792,-565}));
    }
    public void testDE00094() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{510,-251,-301,-556,-1000,-354,-541,436,-1000,119,-541,164,1000,130,-486,567,692,437,-555,-599,775,-50,614,-763,144,-498,1000,-257,334,-439,-1000,-927,-666,-530,-892,-1000,476,543,1000,-10,1000,-681,272,-284,299,-565,958,583,-1000,-1000,-600,-717,1000,369,-302,-985,53,-88,-742,-76,-335,486,-736,138}));
    }
    public void testDE00095() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{9,-79,379,-425,-329,-636,95,269,-1000,455,1000,807,-914,790,-798,-24,1000,-213,693,469,-911,-662,-53,1000,1000,540,9,-574,-624,-270,-724,401,1000,652,-335,-746,926,-252,-461,-464,258,446,557,159,469,-630,149,-1000,-452,333,-337,-370,-1000,596,1000,1000,-800,-748,1000,207,-918,-355,-122,1000}));
    }
    public void testDE00096() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{329,-507,578,664,698,1000,820,-948,-608,606,-173,72,-689,-1000,-724,-999,410,947,854,979,-893,680,153,288,549,648,608,-969,-798,73,-949,505,552,1000,-1000,-282,993,851,856,-418,1000,-355,460,1000,1000,574,1000,-612,423,882,-1000,1000,-965,126,900,1000,-1000,910,1000,-830,-924,882,-120,1000}));
    }
    public void testDE00097() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{224,1000,-209,-924,595,727,94,-559,571,-374,157,-88,-298,907,-110,25,935,117,851,151,3,1000,683,141,17,288,332,-86,-369,181,-8,38,-181,104,17,282,1000,-1000,411,-303,140,95,-945,-295,-1000,-76,503,-155,-655,-472,766,546,76,727,-200,526,-477,36,329,531,638,71,970,-211}));
    }
    public void testDE00098() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-434,-81,-981,316,1000,1000,-495,-923,88,675,-156,74,-1000,950,-290,-778,456,1000,442,-374,-1000,-1000,190,1000,-276,714,-902,-1000,117,-87,-1000,547,-488,-201,-568,141,235,-1000,-323,-1000,243,708,-1000,-358,-846,1000,1000,-1000,307,1000,-356,1000,79,599,432,730,-886,452,-224,189,-664,205,1000,1000}));
    }
    public void testDE00099() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{616,38,-39,-443,995,-467,469,-1000,756,-879,157,-65,-597,-234,-136,176,311,-501,675,337,-209,803,-534,-48,658,398,1000,424,-831,-714,-416,344,-22,-204,-719,-458,754,-1000,529,-198,-526,-270,-1000,-219,-501,519,-608,307,-458,513,1000,-56,509,193,702,769,-1000,-92,-396,177,1000,338,768,-25}));
    }
    public void testDE00100() {
        assertEquals("VOID|getArgName=java.lang.String:MzE0ZS05ODY=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setArgName(java.lang.String):void",
            new int[]{-823,130,-146,-420,1000,-79,-441,-719,-64,328,626,492,799,98,-87,409,-138,-314,1000,-986,124,-241,369,325,283,-1000,825,-263,529,912,657,-577,294,-630,-632,875,88,569,-827,100,339,227,-1000,-73,-276,-207,-467,1000,301,965,-105,-1000,358,492,-679,787,420,250,-433,-80,-76,145,-1000,264}));
    }
    public void testDE00101() {
        assertEquals("VOID|getArgName=java.lang.String:LS0zNDUuNjY1", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setArgName(java.lang.String):void",
            new int[]{469,118,-286,-453,-482,-932,-397,-138,287,1000,270,-747,-421,-1000,299,149,345,-625,665,697,-1000,-1000,313,461,679,-418,-1000,-791,1000,468,-511,396,1000,-1000,-279,-394,697,-156,-380,307,685,766,-150,702,-1000,129,190,-959,-1000,264,-436,-316,35,481,712,-451,32,-421,-894,1000,-379,1000,57,-344}));
    }
    public void testDE00102() {
        assertEquals("VOID|getArgName=java.lang.String:NzU0", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setArgName(java.lang.String):void",
            new int[]{-102,-407,-481,798,176,-227,-201,-568,-911,-991,-191,-446,-970,461,-816,800,-829,-780,-281,-653,-346,915,-264,204,-989,933,65,643,754,-408,-457,-620,914,198,918,-869,981,-103,858,-488,180,-565,437,909,-702,-483,873,-119,-618,322,-418,976,558,903,-324,386,937,713,-81,-202,-890,287,-157,-13}));
    }
    public void testDE00103() {
        assertEquals("VOID|getDescPadding=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setDescPadding(int):void",
            new int[]{-526,531,-376,850,220,-117,207,-937,599,-999,-929,239,131,-75,-325,-408,-824,-659,-955,-550,-124,809,-565,508,-634,-40,858,-247,73,-63,-916,-37,-262,-472,330,905,-286,145,-498,-924,20,97,-376,-836,-330,-553,-127,283,863,159,483,-533,-226,190,741,-92,871,744,-746,0,-12,973,-277,-622}));
    }
    public void testDE00104() {
        assertEquals("VOID|getDescPadding=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setDescPadding(int):void",
            new int[]{-357,580,-576,-286,-1000,1000,816,241,1000,386,-1000,663,486,70,-1000,-1000,-1000,-1000,1000,818,-831,-1000,1000,691,-597,-1000,1000,756,654,813,-1000,-404,-235,1000,-1000,1000,1000,194,-389,902,-207,931,490,53,1000,-1000,-1000,1000,-1000,1000,1000,1000,-1000,-293,-277,1000,-967,385,-1000,-1000,966,1000,817,576}));
    }
    public void testDE00105() {
        assertEquals("VOID|getDescPadding=java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setDescPadding(int):void",
            new int[]{-699,-824,204,-345,426,-1000,717,-1000,71,-1000,1000,-374,331,-719,259,170,-1000,445,272,514,-549,1000,-905,-274,-437,-284,-1000,165,-141,-1000,-400,998,284,-323,-501,-653,566,802,1000,-455,400,-46,-651,-470,-395,-401,175,534,661,374,1000,-535,362,444,562,-96,-1000,-256,-280,149,1000,921,-1000,175}));
    }
    public void testDE00106() {
        assertEquals("VOID|getLeftPadding=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLeftPadding(int):void",
            new int[]{-711,-468,89,241,-917,-640,-818,-768,-683,441,634,35,-636,574,348,680,318,656,-120,-377,872,-586,-568,-99,-143,614,903,546,-435,388,-869,409,-725,-341,438,742,-562,175,-729,-663,-116,617,303,869,106,-590,698,-806,176,-569,-592,-224,-807,618,-632,167,270,-350,205,-457,-972,-673,-258,620}));
    }
    public void testDE00107() {
        assertEquals("VOID|getLeftPadding=java.lang.Integer:LTYy", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLeftPadding(int):void",
            new int[]{-485,245,554,-582,428,-596,-1000,-1000,-387,618,-865,816,1000,-785,-1000,-374,262,686,1000,100,-620,250,-1000,1000,-163,426,916,364,563,261,1000,853,-369,491,87,964,-722,492,-187,-484,-996,-1000,525,17,88,-544,1000,-1000,-368,-13,968,-204,548,984,1000,777,831,807,740,62,1000,1000,-88,-1000}));
    }
    public void testDE00108() {
        assertEquals("VOID|getLeftPadding=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLeftPadding(int):void",
            new int[]{-1000,-1000,64,-813,-704,-1000,-1000,-260,18,158,1000,243,118,-205,697,193,331,847,-759,-531,690,42,-663,488,-513,1000,1000,1000,1000,847,-1000,-22,-1000,-737,831,1000,-487,-392,-141,-389,-366,599,12,957,426,-137,-885,420,-515,-826,-237,-119,-1000,-861,-1000,85,403,-579,1000,-1000,-1000,-1000,-1000,51}));
    }
    public void testDE00109() {
        assertEquals("VOID|getLongOptPrefix=java.lang.String:bnVsbA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLongOptPrefix(java.lang.String):void",
            new int[]{-504,215,524,1000,-298,-951,60,-803,936,-397,612,-12,-7,1000,204,-437,48,648,-218,1000,1000,-109,230,811,620,207,139,490,-970,911,1000,598,-542,-688,139,-547,-1000,-167,1000,260,136,240,-500,170,628,-1000,304,221,-937,610,-1000,411,-305,-854,-271,-230,-795,434,436,755,-180,113,-200,69}));
    }
    public void testDE00110() {
        assertEquals("VOID|getLongOptPrefix=java.lang.String:LTI1OEQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLongOptPrefix(java.lang.String):void",
            new int[]{-1000,-800,24,-409,977,-708,737,576,-537,45,-1000,-1000,-22,-94,-894,633,258,-631,63,-458,-667,-1000,-131,1000,544,-87,757,612,1000,1000,651,-135,-378,805,-160,1000,-148,1000,740,653,-278,-148,413,684,8,-1000,-119,1000,-566,374,1000,-1000,439,-1000,-380,1000,615,1000,-447,-466,1000,1000,144,41}));
    }
    public void testDE00111() {
        assertEquals("VOID|getLongOptPrefix=java.lang.String:NDUxRA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLongOptPrefix(java.lang.String):void",
            new int[]{111,-480,-126,193,927,-255,217,1000,432,-84,159,-673,349,-966,-55,-1000,-1000,457,451,-1000,-543,-523,1000,107,-1000,-327,111,302,-345,-1000,294,635,591,202,329,-32,-612,-964,-314,-343,191,1000,-657,-63,692,-279,998,245,270,985,601,489,-772,-281,-1000,287,-876,-221,1000,573,-611,-11,1000,-462}));
    }
    public void testDE00112() {
        assertEquals("VOID|getNewLine=java.lang.String:Njc3ZS05NTI=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setNewLine(java.lang.String):void",
            new int[]{-395,-283,104,393,488,1000,-713,579,-1000,402,1000,-785,824,-276,416,-229,-78,-778,677,-1000,-952,-486,681,312,-49,35,-59,37,703,-308,846,242,-633,1000,-535,-350,-693,-746,-296,1000,-153,502,245,962,-278,-259,-988,-196,729,-247,-6,364,116,-275,-1000,345,-339,531,-1000,115,548,494,-227,-879}));
    }
    public void testDE00113() {
        assertEquals("VOID|getNewLine=java.lang.String:MHg4MDAwMDA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setNewLine(java.lang.String):void",
            new int[]{477,-859,764,988,642,769,-1000,711,-562,238,1000,1000,1000,-820,-645,-1000,-252,-933,1000,-1000,-1000,425,877,-170,-716,-268,276,502,51,-986,1000,1000,-464,1000,129,-1000,-1000,-822,121,1000,766,-54,-749,757,-1000,-420,-391,-602,764,36,249,1000,-1000,-1000,-695,964,97,481,-718,605,68,537,-126,-1000}));
    }
    public void testDE00114() {
        assertEquals("VOID|getOptPrefix=java.lang.String:", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptPrefix(java.lang.String):void",
            new int[]{1000,370,99,888,-977,-205,232,-238,-362,1000,1000,-40,-1000,-735,-254,1000,472,-340,-477,-576,1000,922,-429,-238,614,-286,-175,-320,727,1000,-3,-526,1000,11,-711,934,1000,1000,-1000,-1000,-676,259,1000,1000,403,-172,618,718,588,-27,1000,-247,-899,156,-147,-146,704,1000,-1000,-162,301,-952,1000,-379}));
    }
    public void testDE00115() {
        assertEquals("VOID|getOptPrefix=java.lang.String:KzEwMDBE", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptPrefix(java.lang.String):void",
            new int[]{332,-370,-666,343,-1000,220,218,-455,-277,332,699,1000,-540,-654,-285,1000,761,-1000,-890,-321,237,558,-261,322,-870,-178,254,-498,681,-206,-528,-995,-88,691,126,626,797,723,-385,-970,276,-217,137,142,288,113,-717,590,991,201,-52,889,-137,1000,-77,-589,-75,464,-1000,-564,95,507,1000,-1000}));
    }
    public void testDE00116() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptionComparator(java.util.Comparator):void",
            new int[]{471,427,-551,111,394,22,402,-154,320,866,520,-17,-209,-270,-739,933,544,83,976,897,544,929,361,984,917,-364,-31,379,325,-839,-154,-346,-919,-707,-276,434,775,-946,-647,-905,509,-749,557,853,-4,307,-643,726,698,967,140,-827,946,257,249,917,-426,150,598,-551,958,-195,-19,-989}));
    }
    public void testDE00117() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptionComparator(java.util.Comparator):void",
            new int[]{1000,178,-176,118,1000,756,920,-1000,1000,175,86,-344,219,-82,-63,166,302,-147,-774,-480,-400,763,-833,537,1000,-176,829,-1000,980,-120,-843,-527,-1000,-656,884,95,989,595,1000,-620,798,-1000,-848,-166,1000,762,-293,-1000,936,-1000,-1000,967,1000,604,-1000,-758,-676,-400,-1000,569,-1000,-806,691,1000}));
    }
    public void testDE00118() {
        assertEquals("VOID|getSyntaxPrefix=java.lang.String:LS0zMDds", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setSyntaxPrefix(java.lang.String):void",
            new int[]{628,-376,-921,-765,1000,976,691,173,-805,886,1000,87,-433,24,-581,583,834,788,-439,-307,15,976,1000,-787,332,41,803,-763,-105,44,-846,752,-1000,40,-615,-901,-602,-679,3,894,-158,780,-178,253,102,491,-698,308,200,-121,-1000,-471,-76,-998,-454,-805,-919,1000,-88,106,205,-297,1000,992}));
    }
    public void testDE00119() {
        assertEquals("VOID|getSyntaxPrefix=java.lang.String:ICs1NjQg", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setSyntaxPrefix(java.lang.String):void",
            new int[]{7,322,-151,-195,-1000,352,-690,-397,-1000,-1000,1000,964,797,269,677,602,-292,554,564,-894,-500,663,-259,-1000,-848,-592,-404,23,139,662,104,737,-132,497,-500,503,399,418,-346,-1000,976,-417,978,-747,1000,669,-1000,-122,1000,-26,911,-385,854,-52,-383,64,-1000,-221,-1000,800,-890,-350,156,1000}));
    }
    public void testDE00120() {
        assertEquals("VOID|getWidth=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setWidth(int):void",
            new int[]{183,-318,114,316,1000,-1000,-338,-780,-1000,1000,852,407,1000,-731,-660,-644,-675,-1000,-43,-1000,-113,-370,123,-374,1000,-5,-732,1000,420,-1000,-745,503,-427,-758,760,845,-590,1000,-702,-202,1000,-566,-1000,1000,-1000,-279,1000,1000,-1000,549,-1000,-1000,195,-361,562,1000,1000,-573,1000,766,941,1000,-840,294}));
    }
    public void testDE00121() {
        assertEquals("VOID|getWidth=java.lang.Integer:LTM5", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setWidth(int):void",
            new int[]{648,1000,664,-252,332,-1000,223,394,905,-1000,-1000,-1000,975,-528,220,82,1000,561,-101,422,25,1000,628,-39,563,317,1000,126,-317,-404,-1000,41,-622,679,-1000,-1000,-1000,358,744,-1000,-1000,1000,-1000,24,844,-915,-380,802,-1000,1000,1000,841,513,-918,68,-568,1000,1000,596,-833,-653,-1000,635,164}));
    }
}
