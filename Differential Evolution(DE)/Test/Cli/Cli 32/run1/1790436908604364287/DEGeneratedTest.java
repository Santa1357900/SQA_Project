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
        assertEquals("java.lang.String:KzB4ODAwMDAwMDAwMDA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-983,-544,-926,73,-890,679,276,252,565,-484,962,-407,-526,-520,632,880,-162,810,477,-43,89,62,909,-599,755,160,521,-388,-555,-552,-63,444,-881,283,474,527,-702,356,-930,-924,-236,-979,-404,166,135,706,270,210,712,-303,-297,-319,-44,-524,-156,14,86,-818,994,33,383,-261,-41,-709}));
    }
    public void testDE00001() {
        assertEquals("java.lang.String:YXJn", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{45,243,754,411,30,520,669,1000,-432,338,-879,-835,-96,122,-1000,1000,1000,-790,-1000,-139,1000,390,-1000,322,-1000,184,-385,818,1000,589,-1000,1000,-646,-632,719,-1000,705,-1000,-1000,801,-191,156,850,-769,-234,316,1000,488,-631,990,-797,-986,-1000,660,396,15,-856,49,245,1000,-179,-237,-602,-301}));
    }
    public void testDE00002() {
        assertEquals("java.lang.String:KzYzNy4w", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-798,861,84,1000,-171,637,86,1000,564,725,1000,497,-371,-204,574,-944,18,-916,-418,447,-411,-546,-1000,1000,-1000,-1000,-434,-322,364,-1000,-81,-425,-730,927,957,-154,-1000,-566,-1000,357,-1000,757,10,1000,814,1000,462,1000,-479,-319,-267,495,965,503,1000,643,1000,66,755,-846,650,-471,-952,208}));
    }
    public void testDE00003() {
        assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{921,184,339,-839,-273,163,280,-972,138,-331,-766,1000,-467,438,155,-173,378,1000,-415,-105,434,-810,-122,-608,632,867,737,742,825,-168,718,750,933,-672,-85,664,-1000,486,-646,882,-762,-1000,-1000,1000,-445,657,-1000,747,109,-1000,527,-593,-1000,-691,544,312,-762,-79,684,416,354,231,399,-66}));
    }
    public void testDE00004() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{207,-1000,-471,-494,703,107,-449,138,197,635,460,-1000,-1000,-591,-1000,745,-164,-398,-1000,-875,-671,-265,1000,-732,762,-881,-917,588,-565,-296,-405,-988,-239,-506,935,491,-252,-434,514,-1000,766,885,1000,47,721,154,766,1000,-709,1000,-1000,398,784,136,1000,-903,-37,205,53,-133,-913,576,-91,1000}));
    }
    public void testDE00005() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{1000,-126,734,-753,-431,874,-856,-942,666,-395,-943,1000,-1000,274,830,-900,-734,272,-376,-1000,1000,-809,-244,-1000,794,-762,932,607,1000,-225,1000,1000,590,-1000,353,408,-1000,-236,-1000,678,-374,-1000,-806,1000,1000,960,-148,747,445,-1000,981,-391,-1000,-88,1000,472,-1000,1000,-1,575,1000,469,1000,-413}));
    }
    public void testDE00006() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-533,-977,914,-246,978,-955,-234,-623,-499,517,-689,-904,-700,358,-422,-380,-454,351,124,-546,-201,-456,-714,362,-903,-579,252,-578,-816,781,437,-86,321,901,-321,286,724,523,-384,-193,203,374,-805,-869,838,576,-261,-8,648,-913,-739,509,112,-436,-232,-837,142,481,-797,-569,-370,187,-662,246}));
    }
    public void testDE00007() {
        assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-1000,1000,-116,262,-1000,612,-681,676,-941,-1000,-660,-1000,911,325,168,1000,-453,1000,379,-967,-1000,324,-261,-981,-1000,1000,-558,-565,-186,639,-1000,161,-727,-368,803,971,306,-207,178,1000,-1000,-961,119,-1000,-744,-1000,1000,-145,-1000,-1000,400,171,-630,229,-142,-181,1000,242,-843,-362,690,-919,-741,-492}));
    }
    public void testDE00008() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-454,-1000,-331,-1000,-930,-74,1000,-672,-350,303,-925,-105,341,307,251,-1000,193,81,-376,-334,-913,609,-1000,-802,879,-673,101,1000,-400,-297,-537,-1000,-1000,-351,143,-965,-989,-1000,-410,611,-1000,854,-1000,-400,-232,-1000,-120,-567,-586,-1000,-1000,805,-1000,-250,1000,-101,983,1000,-357,-1000,-935,-1000,1000,972}));
    }
    public void testDE00009() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{318,200,-766,1000,650,-1000,48,77,674,662,-636,-45,356,-529,-503,888,525,-400,-772,-622,-817,-48,1000,1000,1000,-644,610,1000,-1000,815,-916,473,263,-1000,833,-265,-1000,1000,-950,306,-549,620,-400,1000,-299,357,-391,1000,-1000,-598,-663,63,-635,-203,410,217,-1000,-810,-342,418,-171,581,248,-727}));
    }
    public void testDE00010() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{432,1000,374,536,395,935,987,-628,-855,643,-989,-438,-18,897,132,-301,361,-1000,300,991,-22,117,-843,-197,522,-37,-873,-81,-141,-664,-543,31,210,683,-775,59,-536,-234,348,105,-801,577,-683,406,1000,794,-1000,-89,524,35,395,-1000,-202,-713,-885,-578,1000,1000,-611,123,-263,-872,-104,-231}));
    }
    public void testDE00011() {
        assertEquals("java.lang.String:RF91ZjZDNi5ubDZFbVVIXzRqNlV0", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-1000,500,-516,1000,702,-938,545,-701,539,-504,-866,460,-462,-339,-155,788,3,-309,-1000,36,749,-314,1000,1000,1000,-236,-363,1000,-1000,-215,-507,-471,463,319,412,-313,-554,-387,-1000,-254,86,1000,-672,1000,561,94,-50,1000,40,-191,-86,-667,-1000,-848,-123,1000,-512,-610,-774,775,-73,327,-802,-222}));
    }
    public void testDE00012() {
        assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptSeparator():java.lang.String",
            new int[]{-930,403,864,87,-437,-234,-712,-831,556,941,65,356,139,857,302,387,617,351,358,-68,114,-601,490,294,-750,-554,965,-361,-933,-609,-210,252,-163,880,941,-14,949,-861,867,-854,-334,-174,-134,855,-747,81,-49,151,657,-695,-213,986,775,57,392,636,181,-110,434,68,87,986,-847,280}));
    }
    public void testDE00013() {
        assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptSeparator():java.lang.String",
            new int[]{-711,-188,-246,-344,682,972,755,530,-906,924,-108,-829,-875,-580,-793,96,419,504,290,282,-339,-877,911,-859,781,-308,-623,291,295,514,569,950,276,977,383,409,-65,-875,119,-829,432,-654,-724,-924,217,-299,135,20,-95,-276,571,703,535,388,557,-733,880,400,-960,656,420,947,998,-957}));
    }
    public void testDE00014() {
        assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptSeparator():java.lang.String",
            new int[]{68,-914,-886,542,-204,-43,681,122,-659,458,178,906,-1000,-103,1000,372,1000,1000,-416,-1000,-921,390,764,-1000,-515,-1000,-462,1000,-326,-456,-671,45,95,-407,-1000,-715,462,1000,-157,282,486,-1000,898,301,635,127,232,1000,-604,-124,701,-1000,-1000,-723,409,95,200,-369,-77,548,-902,-1000,-1000,1000}));
    }
    public void testDE00015() {
        assertEquals("java.lang.String:LTQ0NA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{-399,625,674,165,19,-444,98,423,882,503,-764,-103,118,850,1000,-8,-682,-1000,-231,-409,-21,-56,85,-581,545,-633,-714,-816,215,-216,-28,463,1000,-1000,-865,-704,215,-1000,655,-48,124,647,-869,306,-504,-590,-17,248,-458,-328,-326,-866,438,1000,-977,-429,-525,257,-587,270,1000,350,-1000,-369}));
    }
    public void testDE00016() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{173,-801,-131,-546,1000,139,694,-616,-1000,836,761,573,42,-25,-700,-209,206,724,626,805,-331,288,571,283,953,-65,-30,-355,-504,-710,-591,-830,142,1000,-826,414,583,484,-84,303,59,-305,822,-1000,301,683,884,789,1000,1000,237,943,-1000,-814,244,761,806,-477,921,-847,831,804,1000,690}));
    }
    public void testDE00017() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{671,-507,-766,756,-1000,1000,1000,118,-163,1000,-1000,459,-626,-811,-41,-1000,-1000,-292,76,-131,1000,945,-292,417,-364,-1000,-831,1000,-679,-1000,-1000,-283,-256,715,1000,-263,750,-42,1000,-70,1000,1000,-435,-196,697,-852,808,1000,1000,1000,766,130,1000,580,-1000,31,560,759,407,-1000,1000,-1000,105,-201}));
    }
    public void testDE00018() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-1000,405,-811,-781,-1000,-602,-670,-1000,541,-46,-143,-863,-666,-196,-617,-180,1000,886,-1000,445,-1000,486,335,-805,455,597,1000,579,560,-591,-1000,-785,-434,884,-1000,808,-1000,255,876,154,-166,513,355,-861,1000,-423,-952,233,-831,-923,1000,1000,1000,403,764,601,940,-761,684,-188,-631,-28,-276,-1000}));
    }
    public void testDE00019() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-299,-978,19,-497,-14,738,-50,-869,-562,-163,-248,158,193,165,-816,1000,-776,-583,513,394,1000,1000,-351,-136,-378,421,516,-1000,-107,-825,468,520,-108,-405,-311,5,210,915,400,655,161,954,-1000,-521,-17,-790,-179,-354,-429,-913,85,-393,680,-1000,1000,639,25,197,782,400,-580,-400,-537,735}));
    }
    public void testDE00020() {
        assertEquals("java.lang.String:OTIuNjUx", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-318,815,564,834,-1000,-766,-1000,-1000,-914,213,-92,1000,-349,257,588,334,-1000,-966,-991,445,-876,-1000,-798,1000,796,-406,-1000,-1000,-1000,-408,-825,1000,693,-242,-981,-1000,-540,1000,660,-82,-631,-557,-20,-1000,-114,-786,975,-579,-1000,-264,1000,-1000,321,-712,516,945,-381,32,306,-1000,543,1000,-1000,1000}));
    }
    public void testDE00021() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{433,1000,219,324,947,-200,825,-453,-265,-500,-799,-335,-1000,-274,-802,374,291,65,-853,-672,376,-655,451,909,-241,167,288,596,-309,-233,799,-848,-946,103,-1000,318,753,-349,1000,31,692,636,45,-364,491,800,481,472,635,620,-810,1000,408,81,-1000,74,784,-132,-559,-1000,-293,369,695,-1000}));
    }
    public void testDE00022() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{1000,-1,-856,1000,1000,972,1000,-353,-421,-634,-149,-562,-942,1000,-1000,763,-755,-114,123,443,-184,-1000,934,-441,286,1000,1000,874,-382,163,37,536,960,-282,48,-186,586,596,1000,215,58,1000,-145,-351,452,1000,517,1000,-440,463,-1000,1000,1000,-726,-368,215,-1000,-1000,1000,447,39,738,78,56}));
    }
    public void testDE00023() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-460,1000,-141,512,-1000,1000,971,-532,261,-874,-645,-944,-501,36,-1000,-805,324,1000,-1000,-1000,531,-1000,-401,459,-78,1000,-575,334,507,-634,-480,-1000,151,-649,1000,467,534,-1000,531,1000,483,-652,-94,684,1,1000,65,439,206,74,-204,-247,1000,804,-33,-819,-269,-815,-1000,-1000,-672,665,1000,-558}));
    }
    public void testDE00024() {
        assertEquals("java.lang.String:KzE3N2Y=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{903,669,9,447,786,-210,223,-439,488,-652,-641,-843,-186,537,-873,-566,240,-292,496,890,-173,-919,-842,-887,177,-478,-852,-319,-684,677,535,-853,-492,285,-403,215,-577,810,760,234,-156,-359,702,-132,-857,-890,515,-531,-880,274,-436,49,-495,33,793,-11,386,503,-376,-317,148,787,-714,128}));
    }
    public void testDE00025() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{142,284,479,185,194,-518,569,672,-638,871,-801,-228,-150,973,-867,-675,898,844,-550,-513,-358,382,784,-10,-602,-262,-807,-250,696,768,475,945,428,-998,267,827,-15,-73,-918,549,-56,505,-372,-966,947,-204,177,-450,-211,-668,546,-943,397,434,346,-384,-622,74,539,-787,-854,-938,-932,174}));
    }
    public void testDE00026() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{1000,-281,154,-297,-350,-599,-207,1000,-113,-1000,40,-1000,14,1000,1000,111,-179,-3,437,1000,-764,1000,549,-1000,-443,-1000,-165,-1000,-330,-932,-5,122,1000,-1000,1000,1000,1000,412,-1000,-168,1000,760,-476,-544,-298,51,907,-1000,-1000,417,817,1000,633,419,88,1000,-1000,464,964,124,-927,705,-827,1000}));
    }
    public void testDE00027() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{808,-535,-921,558,-19,-444,702,-755,-142,-741,-206,910,913,799,-727,-423,-660,885,-769,352,-606,-764,-192,-296,853,-743,332,-312,-643,256,-793,265,-452,554,945,924,-844,-610,-304,-687,393,587,733,792,-591,643,402,424,908,105,-740,249,-500,-138,355,-555,-122,-627,876,-609,496,-285,800,-431}));
    }
    public void testDE00028() {
        assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{-400,611,634,1000,-108,-666,1000,593,1000,183,-244,-52,839,203,-1000,781,-584,-1000,929,245,344,1000,-369,-652,-767,929,-84,286,-1000,135,-307,-659,-65,60,744,161,400,-613,-68,234,-561,436,8,1000,639,-1000,987,928,1000,1000,400,1000,194,254,-183,1000,-280,-596,-644,1000,1000,-1000,-386,-453}));
    }
    public void testDE00029() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{-357,143,-242,-553,595,1000,-637,180,-291,232,1000,164,770,-1000,799,-1000,-392,220,-340,998,899,1000,1000,-130,875,-315,1000,344,842,-482,-1000,-156,-996,228,822,-440,652,371,892,-1000,-143,-1000,200,-1000,-351,566,1000,-782,-550,264,859,417,-969,565,766,-851,56,922,77,-800,-998,1000,27,1000}));
    }
    public void testDE00030() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-273,-532,-352,924,-808,-169,199,11,726,-575,77,759,-446,303,106,-297,-467,-348,901,263,-837,-13,-317,-237,-118,-862,464,-945,-31,628,355,-491,-879,-514,-415,78,-692,407,-456,-662,861,237,-348,-79,920,-770,205,-392,331,-759,453,509,907,94,-859,-807,-477,-270,477,425,977,-586,93,-732}));
    }
    public void testDE00031() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-852,-357,-101,1000,426,-858,164,928,-504,51,-307,-477,21,739,170,779,-321,806,167,313,-47,209,521,-100,325,-388,-2,-433,376,886,-426,-115,-490,-344,1000,-407,369,-816,-156,-235,-46,-608,-309,-454,43,-146,73,-35,-738,-443,1000,742,700,-89,-520,-202,159,-55,-531,-392,-597,651,-37,1000}));
    }
    public void testDE00032() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-877,512,-867,-717,-771,-61,-690,1000,-253,-1000,-703,-1000,-861,1000,-85,1000,506,1000,1000,-67,-1000,729,1000,378,676,-305,-532,728,-714,-233,-813,175,367,719,1000,-201,1000,-1000,-1000,-1000,1000,-91,78,-938,-530,-780,1000,1000,-92,-1000,1000,1000,1000,588,-117,1000,-721,904,-55,-898,-34,716,-1000,867}));
    }
    public void testDE00033() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-807,667,-374,1000,284,-1000,638,-89,579,-410,211,-387,627,205,-89,563,-51,296,628,-1000,297,210,779,-551,11,-126,133,-585,-1000,-693,-900,-292,-626,818,295,-556,267,-189,-1000,-662,673,-178,-930,1000,-1000,-1000,-627,-1000,-344,-315,502,677,1000,-1000,-558,-987,497,-597,-1000,772,-691,-1000,456,874}));
    }
    public void testDE00034() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{188,-571,-492,-738,560,966,-885,803,-437,-784,-292,-457,-245,201,451,496,-258,765,511,625,784,710,-843,21,-286,66,-909,-676,161,-303,388,-210,-265,-66,-736,322,-579,-242,599,-7,753,138,-338,-889,529,-399,786,713,-228,-182,901,-461,-518,79,-241,974,273,-133,459,248,452,455,-561,259}));
    }
    public void testDE00035() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-322,-367,1000,1000,-1000,-531,1000,-794,-254,972,866,-1000,152,-951,1000,-158,-844,-1000,-149,930,-564,1000,-862,-1000,1000,-138,-1000,-953,638,121,656,-699,-1000,-497,-1000,-134,-618,1000,-481,226,604,-233,-1000,667,611,-423,294,-1000,-306,791,-1000,-1000,-187,-1000,-412,-1000,200,-780,-155,868,-665,-1000,1000,-210}));
    }
    public void testDE00036() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{51,-1000,463,327,1000,1000,-197,-446,-1000,1000,-648,957,850,-1000,630,-1000,-211,-1000,275,801,1000,1000,-79,-601,943,94,912,172,478,-1000,-171,-1000,-1000,343,26,-916,-644,1000,803,-242,-50,-1000,-722,571,615,-410,-863,-1000,-621,853,-1000,-855,-1000,-1000,-1000,1000,339,-1000,627,1000,-1000,-43,1000,37}));
    }
    public void testDE00037() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-169,1000,1000,-208,1000,718,1000,-599,395,-487,1000,-249,-485,-577,-91,-111,-982,-461,309,43,-153,-1000,-940,-198,535,212,-969,111,707,1000,987,1000,60,-229,1000,120,-171,223,2,71,-559,-701,-306,211,-742,1000,-47,-426,-360,677,-916,-387,-516,37,446,302,-487,-247,132,318,662,-1000,871,-234}));
    }
    public void testDE00038() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,576,228,538,-1000,-1000,349,431,33,-923,697,106,-770,1000,191,641,-139,728,1000,-503,-365,280,396,-672,-244,-578,-683,-232,-29,-224,-167,-673,147,17,-61,190,150,-436,-389,112,451,391,-679,37,-57,-1000,1000,524,-608,-1000,462,912,1000,103,-401,33,-271,602,114,515,309,-667,-705,59}));
    }
    public void testDE00039() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{98,-643,188,-584,-277,37,-960,7,325,167,187,-727,407,760,731,331,235,-312,508,902,-867,231,12,-38,564,98,117,194,-555,65,997,-282,-641,902,-330,118,579,-648,602,912,-822,596,-32,-686,842,-165,-718,367,523,-562,396,377,526,412,-859,-100,-758,-110,-765,-463,371,-215,-456,240}));
    }
    public void testDE00040() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-813,1000,-271,-609,-213,-672,-1000,1000,-53,434,1000,-266,754,-1000,-1000,304,997,1000,1000,-765,-9,-1000,-102,936,842,581,992,-367,-1000,-902,-512,62,-198,647,-31,-1000,766,-569,759,-727,1000,-646,1000,-420,-493,-834,-676,784,-12,874,-335,-1000,501,-344,1000,151,303,854,1000,224,-705,112,79,357}));
    }
    public void testDE00041() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{172,610,-911,4,1000,-44,-220,435,-131,45,-568,410,-58,-18,212,-780,-498,256,-256,-1000,1000,657,274,-1000,383,242,-111,-750,554,395,-335,668,-168,-494,-296,184,-70,224,-823,-1000,719,131,1000,485,-620,-510,629,254,-755,612,-689,2,-197,-660,-667,115,463,-320,-552,1000,-306,532,-714,526}));
    }
    public void testDE00042() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-854,154,-81,554,870,-135,-967,595,-133,-872,558,-159,-410,-98,-549,-797,-54,639,530,-181,-10,-323,878,738,986,-222,208,426,948,-846,273,763,-973,-168,433,-274,-657,317,-480,106,-791,153,257,284,84,-762,-345,-657,-964,488,-932,616,42,829,847,-865,-14,372,953,-599,-90,-874,242,372}));
    }
    public void testDE00043() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-84,1000,-623,-660,769,550,989,553,404,-589,8,-28,-1000,-60,-1000,-1000,-63,-66,958,341,1000,-1000,-6,946,1000,-1000,-974,191,348,63,784,666,-258,-1000,1000,83,-449,-812,-968,-1000,1000,-412,1000,-588,-632,-1000,662,-329,-1000,746,-181,-208,-1000,80,567,-71,-645,467,169,751,-994,296,291,1000}));
    }
    public void testDE00044() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{427,-238,740,-898,-1000,-452,338,-1000,-444,682,-812,811,1000,705,588,604,-1000,-636,-894,1000,-609,1000,-745,-1000,383,-365,1000,-647,-1000,-252,298,-1000,498,900,-589,-1000,-45,-553,-672,-196,-729,742,10,-775,-133,844,-396,98,-23,-1000,-444,954,54,-848,-667,1000,-169,-930,-1000,314,248,-118,-525,-607}));
    }
    public void testDE00045() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,-1000,1000,-303,-856,-1000,1000,-1000,-993,544,-1000,1000,1000,1000,-983,834,-1000,-447,-1000,1000,-504,1000,-790,-1000,-1000,-1000,1000,-260,-703,679,-1000,-862,874,434,-1000,-883,180,-462,-514,-225,-1000,-115,-1000,1000,-1000,772,-141,-744,606,-1000,489,609,1000,-694,-96,-195,361,-1000,-844,1000,713,-26,237,406}));
    }
    public void testDE00046() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{118,515,-873,-471,-777,-544,-2,-1000,-488,725,-656,-908,-501,-471,14,-283,1000,-960,1000,-507,-116,968,-147,-592,188,-356,-408,88,253,-925,1000,361,-781,-511,-203,1000,307,725,1000,102,264,712,924,-1000,1000,260,1000,517,529,1000,-1000,373,16,-434,181,-246,-246,701,-840,745,-654,424,-471,-629}));
    }
    public void testDE00047() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1,67,789,-544,-1000,-864,-91,-1000,1000,-1000,594,1000,-1000,877,-485,-516,106,-823,1000,638,1000,-495,1000,135,-745,815,-339,1000,314,510,-444,316,-455,629,216,1000,1000,888,-1,1000,-757,-843,549,-287,-139,-930,440,1000,-1000,1000,-38,1000,-457,-343,121,-528,943,42,451,38,-811,-1000,443,-1000}));
    }
    public void testDE00048() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-908,-5,34,87,-643,-387,-588,-1000,-431,-426,-600,-288,-1000,-860,584,-1000,132,-479,929,345,-288,-362,-493,123,-195,308,1000,167,-131,176,-380,962,-162,698,161,-215,149,398,-34,349,1000,463,-469,-1000,1000,-690,957,753,-326,1000,-392,810,-597,-122,742,-1000,-1000,787,590,421,-446,503,442,47}));
    }
    public void testDE00049() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-598,-650,930,46,-154,-716,-426,-19,690,-278,927,572,-435,452,95,123,-557,-451,-485,282,-18,-665,9,417,-122,339,822,508,219,424,-256,560,687,149,103,-687,-475,-268,-136,170,-1,-589,-1000,1000,-469,-747,-190,384,-288,-397,496,585,-22,56,117,-580,41,-573,557,-636,40,-555,395,127}));
    }
    public void testDE00050() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-393,10,-129,-825,-154,-716,-204,-913,-1000,318,-1000,572,-33,-1000,617,-237,-557,-1000,738,928,758,599,9,-767,188,-1000,-570,17,485,424,-256,358,-712,-284,-750,1000,408,1000,1000,109,1000,960,745,-486,1000,431,931,483,29,1000,-899,394,63,-467,117,-580,-294,1000,-285,-636,-674,1000,-697,-489}));
    }
    public void testDE00051() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{402,-683,-138,617,-181,-1000,479,-883,1000,706,374,616,980,1000,-144,-400,205,-791,251,29,471,-741,-893,916,-1000,764,-385,-310,216,-1000,-610,-248,508,-543,204,-687,1000,1000,487,418,953,-191,-641,1000,486,-1000,42,58,-404,-179,-293,1000,-9,-955,119,-215,-1000,-712,147,138,-1000,-855,-403,-1000}));
    }
    public void testDE00052() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{282,14,-117,750,-182,-69,177,-355,19,1000,-785,-373,224,-1000,-791,1000,1000,-749,814,-1000,-676,-1000,1000,-856,1000,698,-328,-184,-242,-1000,40,-269,301,-182,-286,1000,1000,-1000,1000,-825,-1000,-1000,-471,-39,1000,-744,200,104,-334,1000,-443,904,1000,-156,49,-234,-427,-1000,-384,26,-1000,-922,119,-1000}));
    }
    public void testDE00053() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,-165,-1000,45,431,1000,-485,893,386,33,-1000,-109,-624,245,496,-587,13,-161,786,1000,779,861,-1000,-334,1000,-933,-856,105,-412,-1000,-1000,1000,-1000,-528,-528,886,1000,-907,851,1000,1000,946,-376,751,-57,988,939,1000,-1000,-1000,224,391,-294,-512,-1000,-391,-507,737,-1000,317,326,1000,1000,-1000}));
    }
    public void testDE00054() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{174,-488,-377,1000,-1000,250,722,-237,306,262,-131,-963,157,-893,1000,-1000,-285,149,-1000,1000,1000,-219,974,-991,581,-199,-781,103,327,847,500,801,1000,-338,-1000,1000,1000,-736,548,-2,648,297,-1000,1000,-345,1000,-480,327,973,640,1000,-1000,686,1000,-468,-1000,44,528,-847,957,405,-462,691,-628}));
    }
    public void testDE00055() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-177,-327,-401,-645,406,145,-211,1000,-349,-638,-265,-483,392,-387,-681,-800,-605,-255,751,1000,1000,87,263,95,1000,-335,-1000,-437,159,-937,292,192,-1000,329,-934,602,-78,-1000,926,427,898,-812,220,184,381,1000,1000,240,-1000,-478,168,-227,-399,217,-34,277,-851,551,1000,806,-241,441,1000,-1000}));
    }
    public void testDE00056() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-703,83,-421,-262,-1000,94,56,158,-143,-1000,-269,1000,497,24,-365,-204,236,1000,-287,1000,-1000,672,415,-1000,-714,473,-782,-826,243,794,-979,17,1000,368,265,705,396,-248,-313,587,67,533,311,1000,-538,139,859,1000,-86,799,513,151,-871,396,-457,71,-1000,-900,-522,94,-485,1000,-589,525}));
    }
    public void testDE00057() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{615,-900,-952,410,1000,686,-503,65,-507,183,400,-1000,400,-885,-62,-173,-285,-1000,-307,-159,1000,-382,-1000,-819,-256,-746,1000,595,-303,166,244,-1000,35,-484,-41,-1000,1000,1000,-936,-89,-681,148,-481,1000,-852,-1000,-1000,-865,400,78,224,-607,676,1000,133,-773,935,1000,495,-228,-198,-761,-412,1000}));
    }
    public void testDE00058() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-176,-756,-641,-864,-560,752,119,-710,271,1000,-50,-160,1000,906,-1000,391,1000,1000,-1000,-784,-268,-382,-1000,-753,-1000,-374,817,528,780,688,-1000,-1000,1000,-98,1000,522,623,1000,-1000,29,-395,-373,614,202,-392,-1000,-1000,86,1000,290,223,-645,164,1000,115,-629,893,1000,629,-768,-81,-429,-542,522}));
    }
    public void testDE00059() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,-327,-1000,-246,-592,1000,10,-353,63,580,-275,-43,-624,-989,-285,626,859,458,-191,-609,513,642,-1000,-900,-436,-335,-617,-448,-11,-686,-1000,-851,341,399,697,723,790,1000,-625,505,700,788,247,751,100,291,471,743,-254,6,102,68,-711,364,-663,-862,627,527,-67,-543,-251,797,-188,1000}));
    }
    public void testDE00060() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-996,601,534,-789,42,8,-811,-786,-656,694,483,-891,693,-363,-143,-355,-583,781,275,-614,55,-258,780,423,911,-376,-343,-578,184,197,160,157,6,-289,-187,381,-345,-572,-363,-700,-559,-397,-718,285,323,73,35,495,-802,902,-76,-969,-36,566,-257,-21,-39,-923,-279,204,217,-871,-240,150}));
    }
    public void testDE00061() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{640,-595,-799,-105,539,-874,63,-91,-83,262,691,272,-212,-488,1000,-969,342,901,-465,457,351,731,-298,-427,-302,527,588,20,779,1000,-1000,46,-1000,433,-85,-820,1000,128,1000,89,797,-900,951,1000,325,-396,1000,-1000,-1000,-701,-1000,958,-181,1000,147,-1000,829,-134,-173,195,176,857,-1000,-1000}));
    }
    public void testDE00062() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{800,-1000,-821,907,112,722,-971,-742,138,1000,970,614,1000,1000,-439,-621,-1000,-145,278,-1000,-1000,1000,-1000,298,-242,-1000,-614,60,513,1000,-505,-135,-305,-1000,-1000,1000,-1000,466,-1000,1000,1000,-142,-1000,1000,-416,-1000,323,651,142,440,332,-843,1000,-1000,-271,757,-442,1000,1000,-555,652,643,953,491}));
    }
    public void testDE00063() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-437,-379,219,-586,209,-326,503,-675,331,737,206,805,481,310,848,453,-665,1000,1000,464,1000,461,-580,242,1000,758,-242,-1000,246,850,-1000,1000,1000,-209,-135,-1000,-330,514,1000,-1000,505,-178,1000,1000,-247,-618,643,-142,-1000,-1000,34,123,-891,-1000,519,527,1000,902,-161,-202,-1000,1000,-1000,-896}));
    }
    public void testDE00064() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-1000,-1000,374,-194,-95,-1000,-253,1000,445,-327,-629,730,251,1000,1000,851,-1000,849,23,1000,695,1000,231,31,419,573,856,-463,-816,-220,-1000,-383,-1000,567,-793,-399,298,954,797,-1000,-471,530,-631,-1000,723,-459,69,156,-555,-668,-262,-929,589,-396,997,-191,-344,-113,-304,751,703,1000,-1000,-1000}));
    }
    public void testDE00065() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{433,463,624,473,-303,-371,-600,-343,152,-1000,1000,-1000,360,1000,709,749,-143,799,-343,215,1000,-728,-171,-122,-318,1000,-222,379,-836,-1000,-1000,-1000,1000,1000,-1000,-1000,115,-257,1000,-444,1000,114,677,-23,1000,-1000,1000,-284,1000,367,1000,-1000,-188,1000,725,1000,-1000,-927,-1000,-138,1000,908,-538,1000}));
    }
}
