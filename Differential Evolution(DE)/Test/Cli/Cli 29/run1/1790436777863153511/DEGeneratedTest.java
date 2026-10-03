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
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.CommandLine", "org.apache.commons.cli.CommandLine", "getOptionObject(char):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00001() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.CommandLine", "org.apache.commons.cli.CommandLine", "getOptionObject(java.lang.String):java.lang.Object",
            new int[]{42,-877,-323,26,410,0,-213,290,-507,0,-845,-159,362,0,813,1000,-989,267,-93,631,-770,1000,-522,624,1000,-58,-821,350,1000,-1000,-770,-1000,-495,1000,-451,161,1000,1000,-1000,-392,453,341,-357,-323,-230,429,604,-1000,-350,0,316,468,-154,0,78,-646,0,-707,-766,1000,960,-452,-133,-517}));
    }
    public void testDE00002() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.CommandLine", "org.apache.commons.cli.CommandLine", "getParsedOptionValue(java.lang.String):java.lang.Object",
            new int[]{415,-958,-512,259,-533,-122,799,-964,468,200,655,-630,-212,563,960,-228,-167,299,-497,829,-277,30,-730,-119,836,497,729,-338,3,609,790,352,61,-84,413,404,-395,747,-549,243,-698,-860,183,468,938,-572,222,549,-542,790,-985,-833,-260,-534,-25,-663,534,-588,436,231,100,-842,105,-763}));
    }
    public void testDE00003() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.CommandLine", "org.apache.commons.cli.CommandLine", "hasOption(char):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00004() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.CommandLine", "org.apache.commons.cli.CommandLine", "hasOption(java.lang.String):boolean",
            new int[]{-458,-149,298,-7,745,-833,-567,56,471,-947,-939,-674,852,-769,571,926,-422,-850,143,409,-184,32,889,-452,-570,146,-480,370,-536,926,823,-876,794,185,130,723,-752,168,221,818,-879,484,671,-897,984,-952,492,-263,-58,82,-859,803,-887,969,408,644,-18,-736,565,590,490,-446,222,-885}));
    }
    public void testDE00005() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.CommandLine", "org.apache.commons.cli.CommandLine", "hasOption(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00006() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[]):org.apache.commons.cli.CommandLine",
            new int[]{731,-714,-784,-734,795,-206,326,666,59,709,-171,-497,-792,574,893,-942,-21,307,996,327,-997,31,-504,-698,-564,-513,-282,-386,432,34,329,-232,-908,503,-149,286,-397,-504,-165,-829,32,-866,448,263,445,-311,972,325,848,-928,-891,975,-157,-281,849,942,803,54,-839,561,-115,435,783,-111}));
    }
    public void testDE00007() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[]):org.apache.commons.cli.CommandLine",
            new int[]{-208,-717,104,-435,-253,287,-327,133,679,-900,267,-459,-8,549,965,-416,-558,889,279,711,743,111,-162,-320,-379,331,675,-12,244,-873,766,-781,509,983,-452,-524,820,300,818,191,659,-151,-375,766,514,976,-450,-253,-468,423,120,-466,-338,-855,11,965,970,-776,157,581,323,-377,-76,816}));
    }
    public void testDE00008() {
        assertEquals("TYPE:org.apache.commons.cli.CommandLine", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[],boolean):org.apache.commons.cli.CommandLine",
            new int[]{664,116,77,611,127,7,915,-499,-272,54,560,161,981,-792,-248,492,834,-651,354,-927,-435,-895,-472,148,-829,572,-838,-781,817,-861,773,-63,-540,-452,434,-5,-549,770,-242,147,594,335,-598,600,-99,-679,-863,633,152,393,-872,474,210,984,743,-752,38,3,-52,-911,-638,-697,149,-582}));
    }
    public void testDE00009() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[],boolean):org.apache.commons.cli.CommandLine",
            new int[]{174,996,231,-100,451,-812,-944,891,-909,373,-489,338,-492,-224,-525,982,81,-795,-356,-355,23,148,-525,742,193,506,281,-646,-182,-897,-854,-507,-274,-969,-356,186,290,366,835,452,11,810,423,359,436,-577,-409,938,929,-246,-446,937,694,409,-375,-847,-552,-612,-735,-608,96,-666,418,967}));
    }
    public void testDE00010() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[],java.util.Properties):org.apache.commons.cli.CommandLine",
            new int[]{153,288,499,-969,-830,-502,155,-405,-378,-389,-747,-260,260,79,-624,44,938,-640,545,629,932,568,725,-824,-546,-896,263,-765,-467,-287,400,395,-280,728,-651,505,62,586,-616,746,-820,177,-118,-935,378,9,339,275,281,-215,-33,-85,-345,-447,732,-114,797,292,381,827,-842,-499,-634,-797}));
    }
    public void testDE00011() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[],java.util.Properties):org.apache.commons.cli.CommandLine",
            new int[]{696,11,-30,316,-541,765,-94,146,877,-213,450,-421,594,-246,130,667,117,59,-478,36,161,-49,-581,-615,-147,156,205,713,-107,119,16,-879,158,-647,671,-450,-907,-289,-730,-495,-244,829,-118,-514,-629,-518,-202,631,-126,-861,454,917,70,602,308,-871,-244,322,-267,-218,444,-332,273,201}));
    }
    public void testDE00012() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[],java.util.Properties,boolean):org.apache.commons.cli.CommandLine",
            new int[]{-958,-505,27,-489,533,-910,662,-927,68,-510,897,391,-781,-925,760,642,664,99,-426,-338,490,381,184,-873,311,937,-500,527,-524,671,-489,531,-823,-405,244,-295,-143,938,488,-504,927,-4,-454,905,-453,-415,-272,307,964,588,891,-456,742,-807,920,562,-893,207,-296,-342,-402,46,938,685}));
    }
    public void testDE00013() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[],java.util.Properties,boolean):org.apache.commons.cli.CommandLine",
            new int[]{337,8,184,362,135,-84,-456,987,-269,-887,933,-361,342,816,184,-796,960,-735,-446,347,728,-77,190,-840,-134,-631,823,399,529,824,-543,-785,-494,631,-900,737,-771,893,-42,717,223,-33,943,31,834,-483,825,832,816,451,-103,814,212,209,652,222,-148,601,634,-3,736,658,363,760}));
    }
    public void testDE00014() {
        assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getMatchingOptions(java.lang.String):java.util.List",
            new int[]{775,-55,870,-712,100,-675,-627,701,-163,762,-289,528,-959,160,-173,318,-902,-48,-473,-141,961,-827,853,813,86,833,-887,100,863,-680,-420,-737,841,-738,-259,232,675,-621,-169,617,408,681,-608,943,415,-361,566,87,152,-143,-68,-423,-295,191,-145,408,59,-715,-513,-472,4,37,856,-726}));
    }
    public void testDE00015() {
        assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getMatchingOptions(java.lang.String):java.util.List",
            new int[]{696,-562,-873,138,796,-365,-43,29,-968,-80,583,-85,-779,666,-350,-158,224,371,347,-301,-396,80,847,-769,480,-240,-921,-582,-716,812,-174,-102,673,-449,938,-711,-533,657,356,-29,140,-633,597,188,152,493,369,159,189,-793,64,-699,255,-998,-989,652,-545,210,-404,92,-651,63,600,71}));
    }
    public void testDE00016() {
        assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getMatchingOptions(java.lang.String):java.util.List",
            new int[]{627,664,793,-322,454,-314,840,787,-498,428,761,-67,702,462,743,-780,475,745,-975,-577,609,199,-734,-65,984,-49,-277,462,-792,-482,36,756,750,859,-720,-199,-206,-601,816,-361,676,-119,-566,-725,546,-778,298,935,-348,416,-713,404,544,986,463,-913,-614,451,609,14,-425,-102,-967,-927}));
    }
    public void testDE00017() {
        assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getMatchingOptions(java.lang.String):java.util.List",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00018() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getOption(java.lang.String):org.apache.commons.cli.Option",
            new int[]{996,895,-899,-569,383,-368,-465,-879,-524,-881,-948,506,-856,-301,330,-957,280,296,-51,-964,741,710,-647,-111,-264,484,-652,580,-954,-386,785,271,862,215,526,151,-989,-806,-256,784,-132,-24,-756,513,-669,335,-447,-203,936,-440,259,-234,-644,-710,-693,704,-645,647,791,-280,588,-444,-848,421}));
    }
    public void testDE00019() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getOption(java.lang.String):org.apache.commons.cli.Option",
            new int[]{-133,840,-298,-1000,568,1000,-122,-729,-759,-1000,-356,-723,-783,-779,866,176,43,-908,561,-344,1000,-292,-119,217,1000,208,-1000,580,-762,245,299,-74,-1000,-593,-994,1000,-1000,-806,-789,331,1000,-607,659,1000,587,1000,-630,761,69,-277,-13,-234,-1000,-698,208,-506,-530,-1000,791,576,480,-504,-435,-183}));
    }
    public void testDE00020() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getOption(java.lang.String):org.apache.commons.cli.Option",
            new int[]{137,386,-974,-649,707,863,-108,-817,-443,-42,701,-44,-604,-855,33,700,-294,34,1000,-367,249,-422,-105,-443,613,-786,-699,-734,-907,-647,719,-422,-391,-498,-788,707,-677,217,-455,-75,164,82,-123,705,560,942,-807,906,-676,-241,344,-95,-117,-411,-178,-932,-194,-540,199,469,-65,-796,-443,-673}));
    }
    public void testDE00021() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getOption(java.lang.String):org.apache.commons.cli.Option",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00022() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasLongOption(java.lang.String):boolean",
            new int[]{-503,132,-944,645,-581,12,-23,698,684,678,971,878,-383,472,-847,-951,-797,-103,-185,-409,426,762,-559,-42,-819,-173,463,264,-341,-408,542,-784,490,408,40,1,862,-232,856,-801,356,317,-204,286,266,-103,278,795,-162,788,-710,876,26,-850,288,609,122,-301,955,213,937,596,-364,-945}));
    }
    public void testDE00023() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasLongOption(java.lang.String):boolean",
            new int[]{-801,-625,900,-312,921,575,108,-398,-187,836,-636,559,-835,884,126,-592,-698,660,463,-114,140,-301,106,830,-872,-269,284,750,-303,-914,848,517,-296,-920,-927,-724,-661,-799,-975,-886,-586,584,-619,-506,55,95,-777,-971,439,106,-546,-225,376,-1,209,-112,345,324,-49,233,-152,-369,-269,-658}));
    }
    public void testDE00024() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasLongOption(java.lang.String):boolean",
            new int[]{912,917,-33,546,-991,-651,397,564,842,385,-728,-265,422,-965,-386,-584,930,344,-118,-657,950,-764,695,818,336,-950,968,473,-687,-436,-89,-933,-225,910,274,142,700,-602,-340,800,82,997,799,-682,-127,-37,252,-96,23,-48,153,-521,925,854,-632,-938,300,504,679,-939,893,499,-515,-423}));
    }
    public void testDE00025() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasLongOption(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00026() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasOption(java.lang.String):boolean",
            new int[]{-383,-480,-955,903,-572,177,323,867,798,347,-553,-257,-386,-52,483,651,-794,522,-323,911,97,-244,227,941,525,810,227,-450,433,-989,-666,-633,577,-229,-582,-501,-579,-581,-705,-624,-443,-941,-559,161,560,528,-417,142,347,-946,-7,57,417,-660,382,-563,-328,405,403,-241,883,-199,943,421}));
    }
    public void testDE00027() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasOption(java.lang.String):boolean",
            new int[]{558,-666,-519,354,56,608,193,-334,-582,446,567,-185,-623,151,-312,980,-231,-41,563,-316,576,-781,-88,-62,702,863,-757,37,-279,538,-224,-951,-245,-977,519,-644,702,-601,614,-512,78,-121,-90,932,897,-860,-343,-429,-772,-455,192,-972,-703,755,559,-740,381,-134,76,-529,102,432,87,-346}));
    }
    public void testDE00028() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasOption(java.lang.String):boolean",
            new int[]{-813,-468,-679,-114,-708,-635,622,882,53,-215,151,-750,179,638,593,378,99,-185,-624,986,454,519,-509,-45,-458,-595,69,268,-272,-25,579,643,-223,893,224,741,-911,726,942,722,904,-971,234,358,663,667,-989,907,956,-212,-392,524,-297,-29,180,-882,-400,-279,-214,-767,210,-298,-331,-201}));
    }
    public void testDE00029() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasOption(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00030() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasShortOption(java.lang.String):boolean",
            new int[]{-439,-880,30,-13,-83,-571,-678,690,192,-291,223,-86,-867,-52,-121,-942,-964,-287,-393,-552,-932,248,929,-283,-151,-303,-258,-780,781,-930,281,-273,453,-3,650,-363,408,336,-47,-527,-495,231,-732,524,190,-610,-928,147,-923,-2,116,-531,433,960,-819,21,394,-30,-307,-836,13,291,-273,80}));
    }
    public void testDE00031() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasShortOption(java.lang.String):boolean",
            new int[]{383,685,-105,373,455,-629,397,-617,637,-942,-100,-412,82,-318,287,-849,-804,977,-297,-168,271,655,-299,-72,192,628,-731,-423,-436,739,259,571,-509,943,900,766,-867,-145,264,-257,179,169,832,-541,404,-441,632,-737,-400,-624,813,918,-650,696,983,2,-681,104,-857,-550,-906,-40,-358,883}));
    }
    public void testDE00032() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasShortOption(java.lang.String):boolean",
            new int[]{-748,354,-655,1,921,12,-864,-427,-913,-715,-525,816,-762,-39,380,-679,-857,404,-465,830,-755,633,-417,302,516,-128,669,-488,-416,-980,-795,-746,-211,-123,-39,203,728,21,798,-998,-120,737,-383,-745,-102,-418,-548,753,311,812,-15,489,529,181,271,-204,277,-575,-73,-712,-87,112,647,-807}));
    }
    public void testDE00033() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasShortOption(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00034() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.Parser", "org.apache.commons.cli.BasicParser,org.apache.commons.cli.GnuParser,org.apache.commons.cli.PosixParser", "parse(org.apache.commons.cli.Options,java.lang.String[]):org.apache.commons.cli.CommandLine",
            new int[]{961,603,557,-847,753,-749,-983,-807,-18,-168,-532,-553,810,186,-816,-924,-9,871,-378,241,-522,-271,-127,-423,-993,200,415,493,887,955,765,-770,-525,284,150,-42,-869,-4,-91,-921,770,575,-53,413,582,-204,-683,934,977,-887,714,-654,-664,-281,557,949,794,-604,175,-66,927,-4,-403,580}));
    }
    public void testDE00035() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.Parser", "org.apache.commons.cli.BasicParser,org.apache.commons.cli.GnuParser,org.apache.commons.cli.PosixParser", "parse(org.apache.commons.cli.Options,java.lang.String[],boolean):org.apache.commons.cli.CommandLine",
            new int[]{-880,1000,532,96,-302,833,-435,568,-1000,191,-685,-376,77,-99,-554,-62,-992,-751,-209,660,-331,1000,-718,-473,43,-398,-874,-1000,1000,-538,-268,669,-1000,-485,1000,-616,608,-92,645,-380,-560,-638,226,191,-691,-655,927,361,-808,-190,539,-177,-1000,807,978,-136,-804,864,528,829,-136,837,466,1000}));
    }
    public void testDE00036() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.Parser", "org.apache.commons.cli.BasicParser,org.apache.commons.cli.GnuParser,org.apache.commons.cli.PosixParser", "parse(org.apache.commons.cli.Options,java.lang.String[],java.util.Properties):org.apache.commons.cli.CommandLine",
            new int[]{755,-8,489,827,-289,772,503,114,-735,956,-597,358,276,-641,-940,825,-123,901,-88,711,-936,-11,-473,792,866,-240,-104,-283,-309,-781,-714,368,310,-915,-635,761,-680,-957,734,-846,914,9,18,189,326,991,-766,190,394,-793,-740,-949,-642,210,-55,-442,-764,-56,-362,434,-124,910,722,898}));
    }
    public void testDE00037() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.Parser", "org.apache.commons.cli.BasicParser,org.apache.commons.cli.GnuParser,org.apache.commons.cli.PosixParser", "parse(org.apache.commons.cli.Options,java.lang.String[],java.util.Properties,boolean):org.apache.commons.cli.CommandLine",
            new int[]{-1000,1000,-201,954,-296,-1000,-104,659,421,-602,-1000,-123,-1000,333,-1000,66,-1000,926,-1000,839,-478,915,-1000,632,-345,-28,-1000,133,1000,1000,1000,-341,330,359,-240,-294,1000,-510,146,-30,-510,-1000,-555,-243,255,67,1000,863,-1000,70,-1000,1000,203,-379,-1000,-513,1000,1000,-213,8,422,937,168,576}));
    }
}
