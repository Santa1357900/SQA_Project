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
            new int[]{-906,602,-216,-314,-754,309,497,-87,-119,-623,520,234,-854,-783,194,586,-964,-440,403,438,-328,-933,-899,871,630,542,974,122,748,-217,852,-714,690,571,-420,841,464,-719,-687,-787,-905,-822,-586,-341,-328,567,514,-550,146,681,649,-497,373,430,-138,-557,-813,-65,176,399,-458,-816,406,-263}));
    }
    public void testDE00001() {
        assertEquals("java.lang.String:LS0weDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-1000,-264,-431,-891,-895,-615,-182,-216,-538,-781,563,853,-273,-866,887,-315,-1000,162,517,502,-722,-1000,-1000,1000,580,713,970,56,614,291,1000,-1000,527,286,-1000,1000,409,-719,-1000,-1000,419,-1000,-1000,-278,-319,564,224,-744,84,13,749,-1000,543,-51,-611,-1000,-1000,549,179,399,-305,-800,224,-263}));
    }
    public void testDE00002() {
        assertEquals("java.lang.String:YXJn", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{69,573,-151,-333,465,-981,1000,-609,-818,52,36,-203,654,-69,744,-1000,-521,1000,-42,-631,-407,142,193,530,-694,-715,-1000,48,-1000,-465,-105,-500,121,29,441,445,600,-517,-940,391,-235,1000,253,-898,218,159,1000,579,820,1000,66,396,-349,-1000,162,-1000,369,199,-1000,-347,291,95,-1000,803}));
    }
    public void testDE00003() {
        assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{1000,832,274,-751,300,385,341,-543,1000,980,-867,-1000,553,555,708,-1000,-647,-1000,904,97,-1000,-176,1000,-939,-1000,299,-1000,-351,740,-188,969,405,-1000,765,-366,-1000,574,-215,238,1000,601,-662,406,73,314,190,913,1000,758,-1000,-709,-778,485,-203,-1000,-561,1000,-459,-90,-701,-453,481,1000,-27}));
    }
    public void testDE00004() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{993,96,-346,528,430,-979,496,227,870,-449,-387,-650,577,521,40,885,-312,517,956,-683,-306,-441,368,733,548,145,-860,897,913,-32,119,-788,805,710,595,534,-391,-139,-123,463,327,-294,387,885,771,-681,332,-684,372,-297,779,-79,-669,-948,-288,-83,858,524,569,573,-558,870,620,943}));
    }
    public void testDE00005() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-821,-204,-836,-18,59,-538,40,-305,-828,-928,-949,666,-482,-807,-455,567,-466,-504,691,-618,-213,-828,327,-356,-7,373,74,-356,-440,894,627,-88,-453,-599,604,367,690,66,901,130,991,-338,114,220,-750,847,-769,-927,-122,701,-412,143,-657,-612,824,-253,-314,230,-529,-280,-318,-521,-269,-522}));
    }
    public void testDE00006() {
        assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{136,-674,84,-448,-719,-1000,186,225,-1000,-529,-413,-967,-743,744,238,479,526,413,-214,458,1000,-1000,-1000,-40,933,1000,43,-930,-480,-649,676,392,152,-700,-197,440,-319,1000,-180,-1000,-823,-851,-436,-823,-217,-1000,-936,-1000,365,-996,1000,191,-110,156,-480,1000,-1000,861,271,727,-867,103,36,62}));
    }
    public void testDE00007() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-426,49,-936,-276,-41,491,714,-585,975,1000,1000,138,1000,873,38,-158,-419,-768,8,-1000,-370,516,914,-146,-55,-451,976,410,1000,100,503,-481,-1000,-1000,226,-55,706,-1000,907,-218,1000,705,625,805,10,999,-683,31,-304,145,-1000,744,-296,-385,-450,-1000,469,-1000,548,815,15,1000,-676,-614}));
    }
    public void testDE00008() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-240,-900,99,-1000,-141,785,474,688,-665,-459,-483,371,-651,1000,-713,412,-683,223,891,87,829,-834,-335,574,452,300,89,422,750,-779,420,-644,255,-780,-1000,-172,295,-1000,696,349,1000,-32,796,676,207,-300,682,-434,-933,581,-890,790,-143,406,-403,937,473,-325,289,-69,125,418,-943,-360}));
    }
    public void testDE00009() {
        assertEquals("java.lang.String:KzB4ODAw", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{701,195,439,554,69,632,937,493,72,321,218,-233,193,744,-169,-742,-598,701,-213,169,-641,-429,623,715,506,-694,-174,358,-112,284,-680,-218,-3,90,-162,-329,202,595,-62,-496,382,-727,112,-878,280,-409,6,591,-201,552,-888,19,930,948,469,422,-610,-234,865,-546,-881,305,-150,-77}));
    }
    public void testDE00010() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{23,146,-141,868,-1000,1000,464,-329,506,1000,-179,-334,594,929,485,-830,-1000,-748,937,-846,924,103,-519,75,1000,-871,876,941,-1000,-295,-22,-925,508,1000,1000,675,269,-1000,-1000,-130,715,-1000,-245,158,1000,235,-1000,492,-70,623,-735,-649,1000,508,640,-116,-427,1000,529,572,327,802,61,-18}));
    }
    public void testDE00011() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{111,-971,-596,-153,185,-400,183,-661,1000,106,190,-659,-269,389,909,-22,-405,-249,-183,-178,-1000,615,107,329,-399,-8,-74,849,266,264,-164,-124,-915,-262,-486,65,840,-84,-1000,833,48,305,-224,-300,261,729,400,230,1000,1000,-1000,534,33,469,-947,-375,538,-624,246,1000,911,1000,1000,-448}));
    }
    public void testDE00012() {
        assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptSeparator():java.lang.String",
            new int[]{895,-97,-246,596,612,-372,672,638,-679,728,134,732,757,430,-381,-453,978,53,327,-461,936,-500,133,-355,-646,848,-772,305,791,718,-82,-155,-722,273,-537,588,-235,-352,-369,163,-237,440,638,455,31,-538,566,-592,-82,-603,2,-584,91,-794,-353,494,733,-803,-58,545,-934,503,399,-889}));
    }
    public void testDE00013() {
        assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptSeparator():java.lang.String",
            new int[]{1000,-924,29,119,586,-305,1000,-30,-1000,195,-497,-624,1000,648,-449,-395,224,1000,-1000,-883,-119,-317,-91,-683,-95,1000,-1000,465,643,-622,-276,-626,-390,839,-866,931,-167,516,-1000,1000,-6,-798,150,111,-1000,-410,-1000,-242,1000,529,490,-1000,845,-455,-1000,760,-311,-1000,-792,939,-283,958,47,-894}));
    }
    public void testDE00014() {
        assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptSeparator():java.lang.String",
            new int[]{547,-32,-766,815,974,-85,-1000,-957,-183,-764,-390,-910,1000,-1000,1000,447,-592,-61,261,-382,28,-673,-19,399,138,-1000,647,-458,-911,-1000,1000,702,1000,-337,-1000,882,131,132,1000,-61,495,-440,98,-59,433,918,113,263,1000,-228,973,200,-525,613,959,-640,-531,981,955,1000,302,295,-1000,529}));
    }
    public void testDE00015() {
        assertEquals("java.lang.String:MTAwMEY=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptSeparator():java.lang.String",
            new int[]{291,641,-496,1000,974,618,160,176,-1000,-423,418,818,1000,-719,854,-727,-1000,-64,-83,-382,1000,-985,-1000,575,-1000,30,-1000,1000,1000,-1000,-1000,-400,310,1000,-1000,816,581,332,-871,-698,844,-526,-502,442,-908,-421,-1000,41,-424,-750,-1000,-968,-499,-724,1000,1000,-89,-1000,-1000,853,535,1000,413,-275}));
    }
    public void testDE00016() {
        assertEquals("java.lang.String:MHg4MDAwMA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{223,-232,-491,-606,-437,1000,1000,-789,469,714,-1000,95,-1000,79,1000,364,-793,-1000,-1000,-1000,473,34,-853,860,-1000,1000,-656,845,808,23,-424,-518,-1000,-1000,-172,-780,-863,-6,931,-682,-29,-68,-358,-292,1000,1000,1000,-102,1000,-734,-970,886,1000,-460,-618,524,184,40,-67,685,-1000,458,257,-668}));
    }
    public void testDE00017() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{-1000,-218,-126,1000,621,-567,712,1000,133,-858,60,369,238,-694,-1000,-1000,-378,-261,8,292,904,-180,-1000,-193,-1000,-1000,-626,1000,-116,869,74,519,-845,899,-502,1000,1000,-903,-878,-796,-386,152,1000,1000,-651,-357,466,-512,-356,920,739,-1000,-353,508,-89,1000,426,-733,1000,-842,-1000,-1000,785,794}));
    }
    public void testDE00018() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{-1000,-739,859,-46,-1000,536,781,519,732,-142,74,-119,1000,-1000,-1000,-1000,991,-865,-982,-1000,50,485,1000,852,425,-52,-1000,656,-1000,-1000,-1000,719,-595,486,692,537,750,115,1000,-1000,1000,1000,175,-324,1000,-998,-1000,683,-373,-541,1000,1000,-1000,1000,-1000,272,-476,1000,75,-113,-777,420,-1000,916}));
    }
    public void testDE00019() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-148,-605,469,-821,570,-103,878,-623,-816,-710,-726,528,215,904,577,56,733,-551,-349,-750,874,-977,972,-628,-709,66,809,311,-610,749,529,-226,33,-277,-690,544,427,-649,-474,760,-643,-378,402,-670,-394,772,-861,-364,-547,-690,-360,-510,450,-358,-780,-77,373,-255,-761,-113,152,488,447,135}));
    }
    public void testDE00020() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{1000,1000,414,932,120,1000,831,79,-544,805,-1000,783,531,-231,-1000,26,-236,1000,1000,1000,-555,1000,-820,-219,1000,998,857,-1000,1000,-1000,-1000,49,-858,1000,-526,-130,263,805,1000,-1000,1000,-652,500,365,1000,1000,23,1000,-502,1000,1000,1000,1000,1000,1000,-224,1000,312,319,-420,-1000,1000,621,996}));
    }
    public void testDE00021() {
        assertEquals("java.lang.String:KzB4ODAwMDAwMDAwMDAwMDAwMDA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-1000,-140,-571,-203,21,-1000,-305,186,1000,-806,222,976,513,968,437,-195,-19,-538,-552,-115,792,-384,11,-1000,-1000,-668,233,869,-127,-458,1000,-620,570,-660,-1000,912,-1000,-718,-1000,-332,-953,-1000,-719,-1000,-1000,-201,-1000,-1000,1000,-629,237,222,697,-140,-345,-420,-249,-784,297,-102,-519,239,613,325}));
    }
    public void testDE00022() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-863,295,499,124,-425,-1000,-717,963,511,-1000,1000,357,-1000,1000,-1000,-1000,-1000,1000,495,67,1000,516,320,617,116,-298,507,-410,657,-75,-455,-722,-669,492,-817,-183,586,1000,-1000,-1000,1000,455,-1000,-584,377,1000,-241,-958,99,179,963,1000,-753,587,744,-1000,117,-1000,354,957,735,-1000,530,76}));
    }
    public void testDE00023() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{580,89,499,-454,-646,-828,-727,359,282,-1000,845,657,-529,-20,-919,-480,65,81,-986,-75,830,210,196,-24,-916,348,342,-544,-4,-292,199,-388,-490,597,-669,800,-1000,46,-331,-166,-746,547,-784,-859,-454,-20,-627,-802,1000,20,1000,-229,-874,81,-230,78,-4,-1000,-1000,202,-370,-865,-1000,-275}));
    }
    public void testDE00024() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-899,-857,-806,298,327,146,-832,55,-1000,-49,52,906,200,90,58,895,732,-421,-89,7,625,576,802,-480,1000,1000,-179,-635,-578,1000,-996,-175,-492,168,22,-407,-129,215,-335,-694,917,261,-521,39,510,-437,-172,-314,459,-1000,-803,305,-866,-160,-640,-1000,643,725,228,513,304,-1000,263,-537}));
    }
    public void testDE00025() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{-43,225,959,-593,675,1000,-798,332,1000,-1000,-647,177,95,341,302,-364,990,-964,292,87,298,475,168,1000,19,463,-135,268,-807,-1000,336,215,721,21,1000,-213,47,739,-1000,741,511,276,-854,-893,-816,39,1000,-686,1000,-1000,-1000,269,13,-1000,133,233,261,701,1000,447,712,54,658,1000}));
    }
    public void testDE00026() {
        assertEquals("java.lang.String:dUpRX1RvaV9Dbw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{-316,-582,704,-544,-453,-173,1000,-1000,543,627,758,-809,-996,534,-108,-1000,1000,48,-114,-384,385,535,-396,-807,-90,-1000,694,-260,-834,-1000,-743,-686,1000,-532,-250,339,-1000,-515,210,-393,-500,-684,-191,-419,-1000,-569,248,398,-834,-1000,-858,448,601,274,-86,-517,-216,-121,-829,-85,-1000,-523,-752,-1000}));
    }
    public void testDE00027() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{357,220,-871,-454,-950,-355,-111,221,35,761,-210,820,-633,-19,-951,78,-3,-956,-550,166,-940,333,39,-114,-763,-746,-567,-933,706,-204,-39,-909,365,-611,776,-515,-806,-299,65,-941,902,-907,537,221,-659,157,-531,772,536,-79,779,746,-330,-867,-187,661,-145,376,-144,-45,103,645,713,268}));
    }
    public void testDE00028() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{-386,-455,-86,492,603,755,-1000,727,97,-584,205,584,-644,-287,-50,-993,93,-490,-969,573,1000,478,-278,624,-378,-4,-875,415,-1000,-687,-1000,-768,36,-479,1000,-1000,1000,-1000,-1000,423,-614,639,366,1000,924,643,514,1000,-1000,-1000,71,1000,-1000,-623,1000,-835,226,1000,1000,-1000,-235,-744,-768,148}));
    }
    public void testDE00029() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{-1000,-114,184,803,-891,-1000,23,970,-84,-288,-588,-5,-506,682,-844,840,377,-313,-753,714,-830,-454,-224,79,-170,76,-920,361,-329,1000,-103,-402,-397,1000,-1000,-332,331,-1000,-667,-429,1000,-129,363,320,1000,-1000,-1000,1000,-31,-92,-1000,-129,-864,868,1000,-1000,-297,-378,118,-644,1000,-86,1000,923}));
    }
    public void testDE00030() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{919,-453,-206,-821,-972,-947,368,-728,407,-762,-14,-717,-974,149,-589,-530,-877,636,50,-310,129,-167,3,395,-888,-493,-989,-214,38,115,941,-422,432,-90,-821,-913,788,920,-302,-379,502,234,436,543,8,-357,123,776,315,-589,695,-967,201,707,-940,577,-41,61,119,-62,748,109,-321,-304}));
    }
    public void testDE00031() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{-1000,267,-91,361,-143,-148,-738,779,-1000,-423,-1000,-626,-343,-79,-799,-223,-1000,-436,-33,-265,-913,-1000,6,465,-2,-338,245,-886,134,709,743,-1000,112,210,883,226,-908,-1000,-667,-1000,356,186,136,-101,1000,-1000,-45,-644,80,234,-596,370,-864,429,1000,-1000,-498,-471,183,-436,830,-86,124,923}));
    }
    public void testDE00032() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-927,-849,507,-753,-74,-65,-332,-250,13,764,-639,190,716,-474,885,139,-111,661,565,-363,592,340,-244,771,995,-624,755,-664,-64,-783,742,687,-755,-56,-526,-236,-931,-226,-362,-877,184,-711,-450,788,-449,307,177,-257,214,-877,345,970,52,731,-568,76,-124,513,-147,-237,-127,927,205,660}));
    }
    public void testDE00033() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-223,427,-927,-33,-9,656,-172,657,442,37,804,-303,-60,-973,178,618,-976,-190,622,-178,-891,-602,627,633,-648,670,-497,-394,-345,160,-897,-332,846,923,937,472,224,-541,531,274,389,-691,-364,-448,-455,709,-783,649,311,-548,11,-238,-499,-992,902,-224,-711,-162,352,628,703,847,684,143}));
    }
    public void testDE00034() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-890,116,-647,563,542,-737,814,902,-369,755,-4,-723,-102,712,-113,-732,136,876,470,896,-991,147,-225,441,-279,-585,-686,-20,-622,167,140,-127,272,905,-627,-707,-543,33,250,-228,719,-489,-522,694,-676,359,18,-317,772,844,-729,912,185,-916,99,516,957,-310,-886,463,-227,-897,-781,548}));
    }
    public void testDE00035() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,115,-529,284,577,-508,542,352,491,269,76,-1000,475,2,-1000,-661,205,869,258,834,-1000,891,-483,706,-97,-1000,-44,-192,-690,-763,-396,621,1000,804,-948,-136,-836,120,22,-136,-163,-265,-205,1000,-905,-201,883,-816,1000,813,-821,187,231,-717,73,-374,380,-931,-588,954,244,-221,-638,331}));
    }
    public void testDE00036() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{682,679,368,-237,-9,25,-1000,379,-1000,899,-685,738,971,320,994,-896,-281,-190,678,-178,1000,-1000,-823,1000,634,213,-826,-141,203,1000,1000,106,-1000,923,902,-1000,224,-1000,-226,3,1000,424,-385,-649,443,-886,-690,-147,-1000,680,-686,1000,726,-368,474,7,1000,-394,-470,-146,-1000,-1000,19,-908}));
    }
    public void testDE00037() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{72,175,-169,532,251,1000,-246,345,304,-1000,-167,-670,215,-39,853,-115,-380,-1000,-24,-209,-122,352,-24,81,485,-95,-571,317,-356,-469,-196,422,844,320,-745,1000,551,-71,875,480,-508,396,1000,-329,-243,389,1000,379,179,939,-548,-165,160,-362,525,594,-319,-1000,-269,392,-438,-257,151,-634}));
    }
    public void testDE00038() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{484,-604,970,-737,243,-971,837,459,-351,172,576,-595,-569,-584,530,686,155,615,-103,988,926,-972,-934,-581,-922,322,835,-833,-413,38,827,870,-429,695,-887,-97,359,289,195,-312,-625,-358,-728,-921,325,778,-969,-108,-944,305,-938,746,-163,656,-537,-503,799,-63,-828,770,-202,149,162,-638}));
    }
    public void testDE00039() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-494,-32,-832,653,170,-23,-491,-942,-137,-200,627,464,-831,841,414,68,-411,124,531,496,-208,-572,239,483,-949,4,798,-519,-668,-45,972,-486,-904,-88,865,495,43,241,-546,40,736,-304,979,-179,336,-828,619,-496,-492,-996,813,378,-2,123,-31,-48,-687,307,-646,-9,494,738,-514,-285}));
    }
    public void testDE00040() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-456,-1000,-1000,-776,-549,918,-875,470,323,-1000,76,-947,1000,1000,255,-277,-294,-183,1000,637,787,-323,1000,-186,-1000,-1000,1000,929,-430,-29,901,173,396,-1000,1000,730,966,200,1000,-869,683,1000,-462,-1000,-162,266,1,1000,185,644,-620,-1000,743,1000,1000,-35,-285,-416,1000,225,-1000,-760,-485,1000}));
    }
    public void testDE00041() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-105,-794,61,-676,1000,630,-2,-813,-600,-863,622,1000,642,-787,-228,-1000,327,-748,212,167,521,-992,1000,1000,-363,577,738,-885,188,-188,-618,-221,501,-1000,126,-237,1000,845,-150,170,911,-1000,1000,-1000,580,-225,-146,-72,571,893,-154,-440,-247,1000,1000,-715,-475,81,637,-276,810,454,-948,233}));
    }
    public void testDE00042() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,1000,392,-253,-261,750,941,-282,-861,-417,1000,-49,105,-706,-1000,-1000,-372,55,-370,-10,835,-159,981,-548,1000,153,-359,-945,111,1000,-150,-471,738,-440,-1000,-8,34,500,-735,962,-1000,-526,-399,-74,314,23,790,-787,-113,-158,42,-67,-1000,-379,267,1000,754,-267,-619,-648,-555,825,-247,-600}));
    }
    public void testDE00043() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{432,-261,-906,1000,-645,742,36,-845,34,194,-254,-817,-669,-226,-91,197,-665,-635,945,451,-603,-398,-304,-441,733,-643,206,-214,-85,952,-183,-1000,33,358,-163,-352,-246,-711,116,1000,-23,1000,-1000,-485,859,341,905,-960,701,303,-469,-331,-1000,-1000,-551,788,-775,379,-1000,-58,-877,508,107,-688}));
    }
    public void testDE00044() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,-1000,-648,262,500,-398,-145,1000,49,49,90,-491,815,-160,-157,0,114,1000,27,-235,136,-877,519,-524,-1000,802,342,513,956,247,640,213,-1000,-846,537,350,741,848,-479,36,190,-81,650,-223,-1000,-1000,526,62,-223,-921,675,-506,498,1,168,-92,949,-797,601,-489,325,-740,-387,250}));
    }
    public void testDE00045() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,1000,176,320,-261,798,957,-282,-558,111,813,9,241,-841,-1000,-1000,-60,-143,-669,306,596,-368,562,-704,1000,-554,-712,-808,205,554,639,41,1000,-440,-1000,170,206,1000,-1000,413,-1000,-46,319,198,751,-1000,573,-787,222,360,178,155,-1000,-714,395,711,754,-229,-1000,-152,-555,1000,-342,93}));
    }
    public void testDE00046() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,-1000,-3,143,1000,-298,-1000,504,-214,-521,675,281,377,121,426,624,290,0,277,-684,338,-1000,354,106,-1000,372,1000,622,32,119,738,351,-1000,-946,-982,175,817,398,616,-610,1000,-396,281,1,-702,160,121,1000,-713,-568,-329,-1000,1000,1000,834,-124,-946,-319,1000,-927,-808,-1000,-395,-35}));
    }
    public void testDE00047() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,-1000,-889,-20,1000,114,-1000,1000,338,-697,138,-172,1000,-399,198,810,334,455,276,-738,448,-1000,314,306,-363,-238,1000,681,1000,-459,873,236,-1000,-1000,1000,654,1000,974,879,-867,891,-551,956,-519,671,-681,-278,1000,-572,-332,-154,-440,1000,1000,710,-398,853,-242,1000,-441,1000,-1000,-1000,233}));
    }
    public void testDE00048() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{89,222,412,-253,-672,-989,362,-1000,675,-1000,697,-131,270,288,355,362,565,-1000,1000,363,148,-285,-641,177,-788,-804,361,405,-507,643,-150,-223,-137,472,625,-283,-194,-1000,64,-38,177,-722,-261,-76,-249,492,60,-215,-717,-249,-571,443,389,50,28,-346,-1000,611,-385,178,-1000,-21,-912,457}));
    }
    public void testDE00049() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-71,-21,-339,110,1000,-466,-1000,1000,-284,-1000,948,-318,620,-487,78,-227,134,284,-240,-1000,873,-703,1000,711,-760,965,445,167,365,420,1000,-518,-983,-1000,-1000,384,755,286,1000,369,263,-1000,-123,-388,266,461,-246,1000,-872,-789,480,-1000,240,1000,442,1000,1000,-207,545,-709,1000,-1000,-865,-479}));
    }
    public void testDE00050() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{1000,-714,-902,-903,-495,1000,1000,703,128,497,-974,-93,667,573,531,179,288,-853,513,-913,-526,-639,-1000,18,-9,-1000,255,-1000,-366,769,-1000,-317,334,-636,-520,534,-370,-962,-591,-666,-170,846,-1000,-675,223,-952,-556,-836,-1000,1000,-589,692,-742,1000,1000,839,-1000,-321,727,939,-1000,760,-1000,-730}));
    }
    public void testDE00051() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{723,178,-767,452,-667,748,953,-292,-786,-529,139,9,49,430,-289,948,639,116,921,-913,-544,-758,-359,-945,-160,-252,113,-221,-371,675,-699,973,-64,268,534,445,313,-663,-892,-706,-428,717,-865,-440,-799,-554,76,-431,-541,215,-868,-164,-973,583,-80,-853,-619,460,742,247,924,456,-870,-592}));
    }
    public void testDE00052() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-960,-588,325,687,-268,-220,186,-72,-495,150,307,-871,67,-638,-953,741,211,210,-686,-684,-1000,145,702,-161,269,1000,-1000,-277,-795,-167,701,-29,-485,-829,572,-655,-400,1000,86,565,-59,-400,925,1000,605,-432,-209,545,1000,-393,-512,494,78,-129,792,1000,986,1000,859,-622,-78,-498,585,-641}));
    }
    public void testDE00053() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,51,429,36,-459,1000,533,422,-685,1000,631,1000,-891,-716,578,1000,762,-1000,-326,-757,-78,-34,50,-728,-1000,116,960,-591,409,572,1000,402,-411,1000,-275,-75,453,635,-815,-957,1000,1000,438,-600,1000,-554,-1000,-818,480,-643,-538,-1000,-893,133,-1000,1000,-1000,799,-709,1000,1000,596,1,-1000}));
    }
    public void testDE00054() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{59,320,184,265,-910,444,-717,-882,646,-379,-554,-355,-329,-697,-336,-876,399,312,-48,-624,-510,370,759,319,885,-58,-849,-690,-820,-363,-996,-33,-657,88,-64,-399,-963,915,-287,862,-92,-850,-509,772,-173,-675,377,903,-762,-660,-447,157,-458,151,462,322,-248,-642,400,384,-468,845,296,443}));
    }
    public void testDE00055() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-768,-1000,199,997,-1000,-1000,1000,408,241,-642,1000,-985,-847,-864,-350,864,423,1000,-166,-550,-1000,272,1000,-1000,205,1000,-1000,-1000,-1000,-524,-41,658,-1000,-831,182,-855,-107,1000,177,1000,1000,-1000,1000,947,-451,-27,208,1000,842,-870,-209,1000,916,-175,1000,969,1000,-97,-35,-1000,-1000,154,1000,-46}));
    }
    public void testDE00056() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-12,-867,91,157,-910,444,850,394,-956,872,-339,-336,104,-697,-636,-597,473,400,-579,-913,519,-89,-359,-391,287,1000,276,251,-936,857,610,1000,-38,-478,1000,-703,33,646,354,207,-92,-37,-509,-1000,417,-728,-58,282,-190,24,-1000,-450,-1000,269,-3,340,-4,1000,1000,62,-221,-84,195,-1000}));
    }
    public void testDE00057() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,-200,677,537,399,104,546,-400,-1000,501,65,374,222,-56,581,-507,156,400,-1000,-910,117,199,975,-157,-559,1000,517,156,-1000,705,1000,315,-471,-365,698,-1000,-222,1000,391,400,-373,-189,1000,288,219,264,-650,-134,205,-190,-764,503,-1000,-658,-466,-1,-604,551,84,-298,-105,-179,1000,-1000}));
    }
    public void testDE00058() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-687,-793,547,843,-446,-410,-896,375,766,420,506,756,-752,446,305,-44,80,-1000,-10,-264,-605,-8,-561,564,1000,749,99,845,-916,-767,-640,-567,14,-657,-658,-356,-1000,-89,-1000,-976,1000,1000,-858,-723,-198,927,527,-1000,890,-611,556,1000,-736,79,-618,326,273,-194,-183,1000,-745,-944,42,242}));
    }
    public void testDE00059() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,-782,449,539,-530,13,387,563,1000,461,569,189,-450,-628,140,851,524,-1000,-111,122,-693,1000,-442,1000,1000,-35,-769,967,386,-559,-634,-1000,310,-321,-318,-740,-1000,546,-686,-19,1000,1000,-1000,-419,150,1000,515,-1000,1000,-730,887,1000,-969,571,-1000,148,879,265,-988,1000,-1000,-1000,-157,176}));
    }
    public void testDE00060() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-344,-870,624,-248,36,774,791,-506,152,-766,485,-374,855,-1000,-199,358,286,-305,105,610,154,-570,531,-193,604,864,103,-195,-1000,-783,68,-705,584,426,169,765,-68,-99,-14,-242,1000,1000,802,-518,101,659,393,-313,-251,1000,-537,-668,1000,441,-343,397,-89,-141,-59,808,-998,891,606,333}));
    }
    public void testDE00061() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-65,-1000,-588,748,1000,205,-627,-1000,-1000,-369,650,-909,-861,1000,-589,-893,-870,445,-484,-617,820,-428,-196,1000,1000,-178,442,-386,1000,-485,-237,23,-73,1000,-1000,915,244,-1000,-324,-1000,579,1000,109,-596,-351,-346,-1000,279,-636,1000,-138,-497,666,-370,-324,460,-44,-1000,793,-1000,-816,230,-152,119}));
    }
    public void testDE00062() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-483,-141,-496,920,43,-476,-910,838,346,-855,187,-1000,620,-296,341,-150,150,780,356,-1000,-840,-1000,427,-927,-217,317,2,-217,-346,651,-182,-619,-413,883,884,-96,-61,1000,76,366,-870,1000,-422,247,-749,660,206,920,249,-320,261,-188,-657,464,-1000,343,-605,87,-1000,-386,-990,29,-460,-163}));
    }
    public void testDE00063() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{802,-1000,-1000,-1000,1000,476,666,-321,1000,-499,998,780,-1000,995,-1000,-710,-1000,59,851,-45,-132,-435,-909,321,-220,-1000,-994,-479,774,-400,-683,8,870,498,-1000,-1000,-246,-32,562,-381,1000,-67,-528,-885,-800,-29,-1000,639,291,872,344,-168,-748,1000,253,-1000,-177,169,-88,-285,-304,-3,759,1000}));
    }
    public void testDE00064() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{561,944,636,215,1000,1000,-605,-526,1000,-266,1000,-246,-66,544,-205,169,-217,-1000,810,-99,-406,-925,-248,-114,885,451,-251,-366,-1000,-697,-273,-694,1000,-191,-490,-964,-624,233,519,-1000,960,1000,-785,-1000,-132,766,1000,-437,382,-573,-148,141,-503,979,-439,-266,-1000,-139,-409,1000,-1000,194,895,1000}));
    }
    public void testDE00065() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{802,-1000,-886,-263,738,-1000,1000,465,872,81,1000,1000,-1000,1000,137,-1000,30,400,1000,493,700,-229,503,1000,-21,-1000,-1000,-25,1000,-712,-646,-103,-253,1000,-1000,-1000,-328,-137,-858,-735,1000,440,-348,-1000,38,-304,-1000,425,688,1000,22,-5,-845,721,-71,357,-1000,220,-1000,-1000,-341,-935,1000,1000}));
    }
    public void testDE00066() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{1000,-731,-526,-1000,686,24,-568,91,-25,-224,462,-987,-426,1000,-83,-235,-413,331,-1000,52,856,-826,292,88,-1000,-215,908,-280,-1000,826,41,-153,-49,-384,1000,360,210,-84,-257,-962,-1000,-1000,-377,-1000,-284,195,-696,-211,473,-806,321,1000,6,1000,-1000,-951,-170,-995,-568,425,-539,839,-74,4}));
    }
    public void testDE00067() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-282,363,-191,103,315,317,-642,-461,-901,-965,805,931,-708,721,-1000,-499,-1000,-409,796,-356,1000,294,-524,-80,458,-572,-17,-186,-1000,18,40,23,428,-585,753,419,-328,-656,469,-1000,748,615,-859,-465,-38,790,25,-99,278,1000,584,531,-1000,448,916,1000,-925,-426,553,-195,682,-1000,719,-197}));
    }
    public void testDE00068() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{644,-891,-206,1000,281,1000,-392,870,64,-674,961,-1000,126,-372,-555,279,-597,1000,257,-1000,-1000,124,1000,359,1000,-396,-22,1000,552,1000,-831,404,-1000,-663,-97,-1000,-378,-432,1000,623,1000,-562,-537,-768,1000,562,1000,1000,-161,76,-619,55,779,1000,1000,710,1000,-703,-159,-981,-609,189,966,28}));
    }
    public void testDE00069() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-447,284,-633,322,-246,-1000,-802,-475,-599,-364,240,-303,-428,9,-1000,-100,379,30,388,807,875,-1000,-932,-927,-881,392,-246,-898,505,-1000,-518,-665,1000,-1000,806,914,744,-101,624,-470,-576,-159,-637,1000,-480,239,887,-501,-795,975,-21,258,-615,-361,508,-51,-465,-1000,506,378,690,-1000,-419,-997}));
    }
    public void testDE00070() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{1000,1000,-109,1000,260,46,-141,46,-178,95,724,1000,-563,397,-553,-1000,-1000,573,871,-1000,1000,-107,79,-999,-425,89,-1000,400,-274,239,-1000,287,-151,-641,391,295,-1000,-572,397,-704,1000,1000,-1000,229,-430,253,71,-172,1000,1000,1000,217,-780,-606,1000,1000,-42,-1000,384,-194,1000,-925,1000,-998}));
    }
    public void testDE00071() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{141,-1000,-812,949,452,46,-711,1000,-104,-649,-518,1000,-659,-406,179,-207,-1000,326,-101,-4,1000,-1000,888,-289,354,608,-1000,890,183,180,-409,710,-950,707,-594,-334,822,1000,548,789,-339,-788,-1000,-359,-430,-1000,1000,-172,-292,1000,-546,74,-780,-606,-1000,-218,646,1000,-1000,-933,-1000,829,-64,264}));
    }
    public void testDE00072() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-576,-1000,-243,1000,528,623,-796,-783,-832,176,147,1000,949,-112,-55,301,537,541,-1000,1000,-318,-828,-83,251,-236,672,-722,-551,-120,-46,161,-609,-359,55,-146,-336,1000,169,-122,-16,-858,-345,-700,-768,137,-379,675,1000,-352,221,-129,-695,1000,-841,-721,-1000,-48,313,572,41,341,103,-1000,834}));
    }
    public void testDE00073() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-334,-74,-946,1000,-839,594,-1000,-182,-1000,-1000,467,27,984,-242,-417,-923,-315,524,1000,129,-599,908,1000,1000,-853,161,243,-965,1000,466,-1000,-1000,1000,250,1000,1000,1000,-1000,1000,1000,975,-1000,1000,598,79,1000,93,-1000,498,1000,-564,449,-1000,-446,-812,531,-347,-1000,1000,809,-1000,-961,-878,1000}));
    }
    public void testDE00074() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-1000,-972,829,567,604,237,389,-816,27,-570,-1000,-779,-537,-997,160,-1000,-622,488,843,-1000,-1000,805,-269,-580,-387,289,1000,-168,1000,20,-1000,-130,1000,-1000,1000,190,-323,-1000,2,920,338,-764,141,600,1000,627,311,-1000,-813,-644,-685,458,427,-234,919,876,-218,-1000,1000,1000,800,-837,-1000,849}));
    }
    public void testDE00075() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-1000,245,-71,29,935,1000,-299,-962,112,188,173,834,-281,-252,326,776,848,-1000,-243,421,16,-702,405,810,-73,919,-159,-1000,-1000,-764,742,-905,-1000,-199,-1000,-1000,273,73,-1000,-840,-713,77,-1000,-759,-1000,-434,1000,636,79,-609,675,703,1000,947,152,-796,893,480,-1000,-724,-804,-334,1000,-1000}));
    }
    public void testDE00076() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-555,37,-743,-4,633,802,-113,-993,-258,278,-505,254,595,-768,1000,1000,1000,-387,364,440,152,-615,351,112,421,289,-661,-161,-965,7,273,215,-893,241,-554,-824,1000,399,-1000,-732,-617,-1000,230,-1000,-732,-66,756,389,-1000,-649,361,392,494,66,868,-966,687,518,-737,-797,495,-660,678,-344}));
    }
    public void testDE00077() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-1000,-802,567,-803,-983,479,-88,-1000,547,543,-494,29,-281,-996,326,611,848,263,-547,-613,-146,-620,108,-273,-532,-1000,211,-97,-169,-546,135,1000,-106,-785,106,-613,-1000,-1000,-873,-1000,391,204,-668,-425,-304,-662,596,-895,-283,-101,843,490,716,-557,1000,-267,474,-918,-787,131,659,-724,-698,-762}));
    }
    public void testDE00078() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-998,-43,584,-416,-400,1000,640,-1000,889,-134,-750,858,-765,-214,-659,-150,-74,-1000,-644,-279,-747,1000,-757,1000,-1000,-14,1000,-665,287,-1000,-78,-548,-991,-1000,117,-306,722,-871,-234,-718,-743,52,699,804,-915,619,797,334,574,527,663,1000,1000,581,1000,-424,1000,-237,-531,519,-351,-1000,-1000,-1000}));
    }
    public void testDE00079() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-843,-17,731,759,-658,-3,431,-561,987,-184,292,600,-651,-585,-341,-97,47,-203,-627,-72,760,-112,75,-807,-610,565,786,122,788,828,-351,-119,529,-642,939,-718,688,-507,-342,-252,-926,971,-671,718,-26,-264,-19,-190,-911,-454,-272,-440,52,166,828,-379,696,517,-317,-71,46,-672,-198,543}));
    }
    public void testDE00080() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-1000,-1000,773,-373,-1000,140,-716,-1000,-522,620,-828,-131,885,-976,1000,1000,1000,866,382,-1000,73,-677,-106,704,398,-1000,-873,156,521,450,-904,1000,-247,264,328,-1000,-1000,-1000,-1000,-1000,961,-887,-1000,-1000,-491,-252,905,-1000,348,-367,-343,149,12,-1000,1000,-533,118,-1000,-1000,-240,-285,-873,-778,320}));
    }
}
