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
            new int[]{-907,136,-841,862,1000,1000,-25,-940,-620,-1000,753,-1000,-138,108,-953,537,583,-102,-17,-782,-117,-1000,-854,-951,-420,908,1000,1000,-453,-1000,-1000,-1000,1000,-438,-1000,-261,220,-766,964,-297,692,146,-136,707,334,297,706,1000,-1000,1000,1000,492,-1000,260,528,-301,-480,530,-182,183,-486,1000,71,1000}));
    }
    public void testDE00001() {
        assertEquals("java.lang.String:IDQwMCA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{1000,-106,9,-1000,602,-400,384,358,311,186,-647,-329,188,224,906,1000,-1000,814,120,-85,868,1000,298,347,715,-1000,25,-420,17,1000,580,930,-400,-803,323,-1000,-1,150,-375,-404,-171,-402,212,-716,-175,1000,-1000,1000,947,1000,-763,-1000,350,-434,-1000,-563,1000,483,-760,-1000,-215,66,-858,-763}));
    }
    public void testDE00002() {
        assertEquals("java.lang.String:YXJn", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-943,-587,-271,635,-174,-509,1000,-6,1000,-175,-1000,-462,199,-1000,1000,776,-1000,1000,1000,-1000,213,719,83,-1000,-935,-401,400,868,816,566,-431,463,706,-1000,467,-1000,1000,-34,-1000,-27,753,1000,220,1000,1000,86,775,-977,-892,882,91,-364,-80,743,-753,210,1000,834,-1000,-372,-267,810,-832,1000}));
    }
    public void testDE00003() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{11,552,989,-605,-865,531,-436,-456,-344,-686,943,465,-791,376,-829,206,284,863,133,-834,321,21,213,-914,-333,-147,-31,-903,510,971,-86,633,681,-158,250,-446,-410,-75,825,396,-586,546,-499,304,-810,-194,618,934,458,-715,332,-668,693,851,187,-330,958,-487,518,602,894,912,-997,-74}));
    }
    public void testDE00004() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-452,-758,569,-342,-253,174,-435,994,-739,-129,487,-534,769,-951,-938,-671,-967,-641,498,608,-597,-189,453,162,144,-843,-328,55,-194,-999,943,382,608,713,843,-745,-803,447,115,196,-990,-993,-532,844,-39,651,83,594,-793,-535,185,537,-665,-548,644,920,-533,-869,-205,-790,520,-940,376,-164}));
    }
    public void testDE00005() {
        assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-838,292,-306,261,34,213,409,-389,484,-638,-194,741,-774,-372,66,-18,117,-982,-754,-714,-983,-587,-54,-615,-64,675,-695,-1,-666,-191,-864,657,236,-852,-159,210,-868,-221,93,-275,588,-159,230,50,921,-286,811,80,-635,-378,839,510,701,-290,783,676,-462,281,-271,-997,-886,541,-269,523}));
    }
    public void testDE00006() {
        assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-721,-748,-461,867,42,-1000,-16,294,1000,617,-1000,-715,1000,-234,651,-1000,1000,1000,-122,-1000,-607,1000,57,-12,1000,-346,-1000,74,-1000,703,-54,650,187,-815,724,113,15,1000,-1000,-990,-721,-388,1000,530,-273,-920,256,136,-458,178,-624,-965,92,-119,856,170,-173,-1000,-906,551,157,-111,-1000,-1000}));
    }
    public void testDE00007() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-463,-602,-86,758,-20,192,313,718,543,1000,-211,333,467,-229,701,-1000,-134,-803,-758,466,-525,1000,1000,-1000,-157,-163,-616,912,160,-710,-8,-944,181,-498,-381,413,0,-803,-714,-680,-978,502,-716,-294,531,1000,-507,1000,-21,641,180,338,542,66,759,71,1000,398,190,-66,-236,48,-785,-1000}));
    }
    public void testDE00008() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{1000,43,618,529,-401,-1000,-665,566,527,-532,-425,-463,167,-1000,-37,-724,793,-1000,716,-376,-866,531,266,-103,1000,-569,653,227,-375,60,-193,472,413,-988,-945,245,-178,783,-519,416,608,221,48,1000,-30,792,894,-377,522,-37,467,-738,635,3,802,293,-339,-1000,136,-897,-112,-1000,-848,-766}));
    }
    public void testDE00009() {
        assertEquals("java.lang.String:MHg4MDAwMDAw", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-38,70,839,867,1000,-581,970,-319,466,-549,30,-799,13,1000,772,632,766,-199,-620,654,5,922,-631,-773,214,-1000,110,-118,-1000,-740,694,793,-589,-1000,-1000,745,131,328,738,-1000,380,553,-769,-345,-268,-877,-655,-29,934,-932,1000,-30,-1000,-290,-91,-794,643,-167,519,368,-614,-390,-608,1000}));
    }
    public void testDE00010() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-1000,-651,429,918,-87,672,71,-1000,-43,646,645,1000,589,-1000,-95,1000,-1000,509,452,-48,847,408,-600,304,88,-481,1000,-587,13,-588,39,-218,25,463,-336,285,947,156,711,1000,-996,936,-300,-1000,570,768,1000,101,-333,-516,-195,1000,-1000,-1000,-1000,413,-305,-732,-418,-169,-709,420,-50,-1000}));
    }
    public void testDE00011() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-1000,221,-501,-663,409,-167,-610,721,-848,-872,-178,542,203,594,-57,417,247,-199,-620,0,-13,-566,-334,-773,-871,-262,110,-160,-733,1000,173,364,34,-524,-60,541,131,-339,1000,1000,1000,606,-535,519,136,-984,-1000,79,379,-446,1000,386,-196,535,-37,-658,205,-858,221,-26,-474,-114,-109,578}));
    }
    public void testDE00012() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{327,830,-716,-663,-264,495,-933,-77,-1000,1000,-178,1000,-361,-693,359,-427,-1000,-251,-1000,389,-1000,-566,1000,-713,217,108,-547,1000,89,1000,725,-165,1000,521,1000,507,-1000,894,-568,400,-630,-721,1,409,30,-136,-1000,131,-1000,-222,-1000,144,-196,-1000,364,-317,242,708,-63,-933,-455,-1000,-505,390}));
    }
    public void testDE00013() {
        assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptSeparator():java.lang.String",
            new int[]{-261,-917,-381,-702,-639,644,659,-513,166,-476,681,-165,-877,-283,-516,-124,-374,17,-972,792,575,529,-104,600,410,946,340,571,915,70,555,-968,915,-335,-245,-926,991,-646,-946,84,804,982,-759,-448,685,644,-910,-900,798,383,-976,-665,341,-507,674,-229,29,-731,-850,832,-593,-889,-312,698}));
    }
    public void testDE00014() {
        assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptSeparator():java.lang.String",
            new int[]{-930,1000,314,161,565,-204,85,128,1000,-240,432,1000,44,1000,355,-1000,719,291,-472,-52,-277,251,871,-910,-1000,-270,768,83,-68,-902,-322,-1000,1000,652,-1000,-615,1000,-220,247,-180,615,-974,-92,446,-1000,1000,-392,-1000,-1000,-864,-824,1000,501,187,1000,147,-109,95,866,778,70,645,-94,47}));
    }
    public void testDE00015() {
        assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptSeparator():java.lang.String",
            new int[]{217,90,-6,144,-127,-241,-746,779,487,1000,596,-1000,-1000,347,107,-163,-376,197,-387,938,752,646,-1000,114,-580,-513,1000,-574,-659,-704,-241,-626,909,-477,705,-546,582,1000,-995,-1000,-656,115,-687,-1000,182,67,-741,-547,-503,223,-419,-544,1000,80,179,-1000,757,-781,-980,908,-606,425,-709,-629}));
    }
    public void testDE00016() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{700,0,949,332,-196,-380,-573,-492,793,510,-573,-255,-780,-393,-244,208,498,592,-994,329,-444,-376,-350,232,846,-312,-620,-524,-484,-726,397,152,188,249,-992,766,39,-60,-846,-212,-379,-312,323,274,248,434,639,-461,650,-742,-101,499,91,-792,812,27,155,259,-243,400,-976,-233,-998,785}));
    }
    public void testDE00017() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{456,867,719,-419,-146,-586,766,-824,159,-138,-573,-377,-1000,102,62,44,-633,592,689,329,220,731,-1000,-91,845,474,-539,-524,-1000,-296,-244,624,1000,930,-336,173,1000,185,514,101,-1000,1000,644,-502,746,256,1000,-461,-101,-578,883,799,700,-717,-217,195,-716,-509,-440,236,-1000,311,-226,688}));
    }
    public void testDE00018() {
        assertEquals("java.lang.String:LS05MjRs", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{49,-196,-796,309,1000,-321,-773,635,425,924,-333,-170,1000,-599,-156,-218,-633,340,689,-481,-900,-1000,455,-895,727,-743,-628,-524,1000,907,961,-1000,-289,-416,-1000,41,-191,864,-1000,208,1000,-1000,153,1000,-121,822,-1000,1000,414,780,-705,-116,-71,1000,-217,-651,656,346,52,-400,1000,492,-1000,-1000}));
    }
    public void testDE00019() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{-163,-263,-691,-3,458,-329,972,-485,24,866,1000,-145,1000,-289,-97,1000,57,-1000,-9,131,-427,-672,-540,-603,472,-1000,-1000,1000,1000,1000,144,24,-864,299,-834,19,273,1000,-124,-454,1000,1000,315,835,-246,-436,-1000,409,-314,-572,-32,-119,-67,750,469,-587,221,559,1000,499,1000,1000,1000,-1000}));
    }
    public void testDE00020() {
        assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{986,449,-971,-645,60,-56,-224,-648,-118,860,643,407,566,1000,-1000,960,-385,-519,-41,-634,542,-1000,-919,356,-1000,953,-149,-1000,-632,-587,871,-1000,-144,915,-1000,80,-582,-375,-388,178,679,680,-40,1000,1000,702,367,251,630,-833,-1000,-1000,-646,-463,-1000,1000,-617,44,614,-213,53,-798,202,-614}));
    }
    public void testDE00021() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{369,-1000,-901,648,513,-1000,-226,411,-353,-176,-797,123,-1000,-102,-312,380,-1000,959,1000,-509,1000,-1000,706,1000,147,-1000,-1000,-759,-233,-866,-1000,-688,327,255,1000,1000,-775,666,627,1000,145,-702,598,1000,1000,242,1000,-1000,136,-1000,-949,-627,-1000,182,-90,862,-1000,-1000,339,-289,-535,-1000,361,191}));
    }
    public void testDE00022() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{851,-268,-791,-466,-70,59,550,-38,355,119,-309,219,-390,829,-754,-192,147,-110,938,-795,86,-795,729,-149,-859,-889,-122,-994,-544,-905,-327,-117,836,984,40,710,-496,-734,-462,955,965,153,332,789,810,268,684,-947,372,-556,-807,-496,-977,0,-412,889,-393,-912,214,-440,16,-774,521,-281}));
    }
    public void testDE00023() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-201,1000,414,-803,221,282,383,838,-1000,-173,-956,478,739,753,-970,792,-132,-446,-53,3,-512,671,149,-187,-148,816,378,425,76,1000,-668,339,-1000,-569,-69,-36,1000,-141,990,-100,-3,-1000,-133,-43,307,147,402,380,676,-815,1000,406,-981,-882,917,721,788,-145,-498,426,27,411,833,630}));
    }
    public void testDE00024() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-1000,780,-481,-274,568,-526,557,602,1000,-842,1000,-392,1000,832,666,-1000,664,541,1000,-396,-386,-197,319,-998,1000,-971,776,1000,-849,379,-1000,-770,-1000,-1000,-1000,-1000,-1000,-986,460,-490,789,978,1000,866,883,-924,344,139,-1000,913,517,-884,-676,-1000,843,-1000,1000,756,-1000,-23,999,497,1000,262}));
    }
    public void testDE00025() {
        assertEquals("TYPE:org.apache.commons.cli.HelpFormatter$OptionComparator", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptionComparator():java.util.Comparator",
            new int[]{-1000,712,219,915,-317,-927,-91,-256,1000,-425,440,67,597,213,107,-1000,47,-511,-681,-1000,593,-713,-157,-181,211,201,261,-579,-58,-85,-485,-863,-1000,-116,-822,-107,19,-1000,1000,-421,1000,-50,350,282,-983,-4,954,-689,347,-329,665,-1000,-1000,-191,-537,-438,-284,580,-1000,-276,1000,1000,-37,526}));
    }
    public void testDE00026() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{992,-20,-311,-53,-69,-593,533,895,-237,-862,909,1000,1000,176,-373,609,-114,815,-707,389,-880,278,-680,1000,262,-199,-2,223,-868,309,-220,410,-270,1000,410,-850,1000,572,-23,487,213,146,-18,788,-85,628,-517,73,402,-1000,534,962,504,674,-5,-1000,-451,-166,997,-36,642,319,-254,-711}));
    }
    public void testDE00027() {
        assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{651,620,-461,799,1000,-778,-257,1000,934,-326,145,-612,481,-743,-442,-64,391,770,-210,407,233,43,303,582,1000,938,-1000,-533,77,129,134,240,-853,496,-26,-208,209,-838,-268,-147,475,1000,73,-796,-589,463,-1000,-420,1000,-1000,691,1000,345,1000,312,-743,-506,882,734,18,424,1000,-1000,-516}));
    }
    public void testDE00028() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{420,-660,934,-78,937,465,79,-480,-39,-636,-327,-11,-793,238,-233,970,-282,-595,234,-390,819,877,-467,-1000,-1000,-592,1000,393,-881,-1000,-366,101,31,-514,137,-512,273,-47,-106,632,401,132,841,-994,-332,-781,201,-498,-101,-696,-132,-1000,1000,-121,-43,-177,220,300,-559,-16,267,-44,-410,882}));
    }
    public void testDE00029() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{542,549,-361,691,-968,837,477,503,-245,-452,-387,854,-325,680,204,745,-859,530,417,-432,-544,-177,-487,242,-225,630,-852,655,591,619,-518,189,608,-822,-751,-620,-112,990,51,97,829,748,-146,995,-259,-110,903,504,-426,442,-927,-944,702,386,-913,-84,-438,-696,928,-449,443,493,-687,235}));
    }
    public void testDE00030() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{511,-911,-701,-514,199,263,-595,-368,16,753,649,0,-395,1000,-419,-1000,1000,-257,658,161,96,962,569,739,-299,-901,-205,920,-635,740,533,-1000,-812,-1000,265,-400,1000,-621,-1000,-37,-1000,-248,-723,911,787,112,-1000,-1000,-871,-516,1000,-311,544,-564,-459,1000,-277,-67,-259,545,221,-126,1000,288}));
    }
    public void testDE00031() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{338,1000,-246,497,340,-1000,435,783,-938,54,893,-451,-1000,1000,-52,868,934,-807,-330,976,-145,-1000,-1000,700,-622,-851,-1000,1000,-572,122,-1000,-618,1000,556,269,1000,-652,212,253,733,746,-330,-430,-1000,-1000,-1000,398,1000,-1000,-823,104,-1000,-371,1000,-994,-1000,194,-1000,-556,-906,-520,1000,-616,760}));
    }
    public void testDE00032() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00033() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{822,-433,-537,-574,-189,-775,156,46,-567,1000,933,656,1000,-312,117,-197,-740,1000,554,629,919,-337,394,753,732,-936,345,1000,72,-85,1000,344,-1000,-1000,381,-1000,1000,646,-832,-1000,-851,104,-1000,-1000,-378,-671,34,-606,1000,237,-811,-470,1000,572,849,1000,442,689,-1000,1000,-1000,764,833,985}));
    }
    public void testDE00034() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-652,897,431,501,-698,515,594,-157,598,-22,-68,645,-947,-135,-94,-586,-64,-650,462,449,-252,727,128,-239,-854,-113,-623,-646,635,498,354,-204,719,-809,-368,167,-316,340,477,457,900,185,-757,272,-412,981,-986,-679,383,618,344,-445,-313,-270,78,69,812,701,596,-77,-782,-838,-442,881}));
    }
    public void testDE00035() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{645,26,757,-27,372,143,732,-453,-243,-223,198,545,557,-526,-804,586,-120,-575,441,-518,-958,183,659,-640,986,192,294,-780,264,-694,-935,-778,-354,931,404,-808,903,787,-369,-241,26,-607,869,-609,-791,335,181,145,-716,-849,-829,-595,-998,214,380,79,-486,-603,-773,-215,-256,488,922,414}));
    }
    public void testDE00036() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{933,-644,849,-908,611,-344,-792,88,-562,-470,-604,474,969,185,545,440,228,503,-90,131,174,899,417,527,-363,537,527,511,104,707,-143,-458,-152,-324,447,-407,347,648,467,925,-348,601,260,110,450,-273,115,-716,916,940,907,-941,144,516,862,-288,924,283,74,357,966,-992,-856,-389}));
    }
    public void testDE00037() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-905,-218,706,-850,-517,-915,941,-463,467,830,-50,808,-636,367,-297,-528,637,116,-388,-364,-366,-90,-316,425,652,468,203,-285,-585,-446,129,386,-745,877,855,-522,646,131,859,-415,-459,681,-83,-354,981,-622,988,867,827,715,-306,945,166,-867,-625,-27,-112,661,113,473,-694,267,831,-176}));
    }
    public void testDE00038() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-652,61,-842,970,-120,170,1000,-724,-361,-594,351,-1000,-912,-210,-94,803,-922,567,335,65,-492,727,347,-239,652,1000,384,-1000,-399,-418,1000,-1000,-83,371,392,167,1000,493,471,1000,-517,527,-328,-1000,-498,981,-20,-191,995,125,99,-227,-313,158,1000,69,1000,1000,-1000,-423,1000,-1000,-1000,219}));
    }
    public void testDE00039() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-93,-697,-453,-187,-881,-508,264,-539,644,86,-271,181,-862,-872,-928,-59,270,1000,-785,465,-958,-1000,-1000,-51,317,848,-564,1000,862,702,1000,206,-27,732,-5,-488,775,769,872,110,-969,418,580,-185,835,-734,742,1000,1000,-934,309,785,109,-390,417,-549,321,1000,-951,257,1000,-243,-583,917}));
    }
    public void testDE00040() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{894,-697,149,683,664,-458,518,-1000,-671,101,779,-501,-41,269,-928,211,-188,736,742,220,-47,48,915,368,287,-212,-180,269,966,372,810,-170,-672,215,875,357,1000,629,-339,-100,-725,-90,-118,-428,-629,380,-645,-569,1000,927,-431,-474,972,44,1000,112,920,823,-951,815,-407,-725,-28,-224}));
    }
    public void testDE00041() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{1000,1000,502,-913,779,-252,543,-866,-206,-802,-57,-427,1000,-672,-711,-738,-781,1000,-682,-1000,-260,-304,979,1000,-1000,-12,1000,232,1000,-638,-322,-1000,685,-1000,162,873,364,-1000,472,-958,337,51,1000,-715,6,60,-917,1000,-659,-1000,-1000,474,-230,-160,-262,-1000,-864,1000,-220,-236,267,-643,125,23}));
    }
    public void testDE00042() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{1000,-53,769,-471,373,74,-64,-1000,-777,-278,1000,-479,719,-46,103,766,1000,901,-137,-522,505,273,382,1000,-1000,-788,612,-684,1000,704,17,-467,595,163,-1000,-643,1000,434,-261,-411,-704,222,-1000,-155,1000,721,-784,979,424,-596,-868,808,-1000,52,480,-714,-1000,-66,-564,-1000,-1000,-892,358,714}));
    }
    public void testDE00043() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-63,-1000,-601,1000,-1000,-1000,-1000,1000,967,924,-361,238,-1000,548,1000,666,-875,-753,-848,1000,1000,1000,-222,-1000,-728,-1000,-840,595,-382,141,1000,1000,-1000,533,-298,789,-310,315,-1000,1000,-933,1000,-1000,-220,1000,-1000,-1000,-503,1000,1000,1000,-371,-1000,1000,-1000,224,-1000,-1000,933,-353,888,1000,1000,737}));
    }
    public void testDE00044() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-397,-317,-141,-185,-1000,-151,-249,-477,453,280,45,147,-1000,12,616,577,92,692,-197,397,-404,868,-1000,658,-223,1000,-627,44,-135,-2,786,-44,519,400,-859,55,820,-153,-290,183,-199,462,-1000,-203,300,-21,-28,-253,200,-80,1000,709,-407,184,-27,-631,132,-12,1000,223,224,227,-98,150}));
    }
    public void testDE00045() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,-944,50,434,-1000,492,-65,1000,907,464,584,-269,-1000,646,1000,956,380,-357,876,1000,-189,-480,-1000,637,343,-766,-1000,-1000,-157,1000,645,755,-836,1000,-774,-1000,103,1000,-445,1000,-1000,739,-1000,1000,457,224,1000,-1000,1000,581,1000,-1000,-136,231,629,1000,666,-829,-75,729,-1000,160,-70,-106}));
    }
    public void testDE00046() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-144,-1000,375,339,441,339,-272,470,63,418,-192,95,-345,-759,1000,82,103,710,457,-561,860,772,-86,-1000,343,1000,5,512,671,60,-74,-252,403,1000,993,1000,-348,-1000,-283,163,653,755,1000,-1000,-157,-501,447,593,345,1000,-1000,517,1000,755,-1000,-1000,-243,-787,1000,32,-772,981,859,-861}));
    }
    public void testDE00047() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-1000,383,-581,-472,29,149,736,-364,-1000,438,-70,744,-1000,-79,896,1000,146,191,-851,1000,-845,-1000,-1000,-302,-786,-1000,171,-1000,-1000,-21,1000,1000,816,338,-1000,-1000,1000,1000,942,-642,-1000,568,-1000,1000,1000,882,201,-1000,1000,-266,676,1000,-440,1000,998,-416,-909,-1000,-1000,-483,-1000,-1000,-114,-194}));
    }
    public void testDE00048() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{0,990,-472,267,1000,1000,677,278,738,481,86,799,-615,-95,740,-1000,1000,546,-865,-989,-496,-146,-1000,-323,-627,-712,-816,854,-191,265,-128,-56,-157,723,-63,-138,748,725,-270,684,1000,-789,-172,466,1000,473,-297,-331,-610,-130,1000,415,1000,155,143,-447,-433,-80,199,-184,402,-301,-691,342}));
    }
    public void testDE00049() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-469,-272,-771,-65,-912,-451,-980,-674,962,950,236,798,430,-730,-4,-178,-72,-369,246,-268,-14,330,43,411,-876,-605,-303,762,-925,854,286,-386,-375,-922,426,116,603,-773,835,16,-881,-790,147,290,1,-80,773,-469,584,-516,-867,-783,-512,225,532,-481,819,-307,-879,675,361,-368,-39,-881}));
    }
    public void testDE00050() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-989,142,-472,-241,1000,578,677,494,164,41,-403,797,-161,-1000,980,-882,1000,220,-1000,-470,-807,-750,199,-217,-229,317,-927,-546,-1000,392,701,1000,652,-938,187,-734,444,-57,1000,364,920,29,938,-525,270,429,-676,576,562,185,1000,275,-92,794,108,-553,-537,-213,-812,350,-386,22,-1000,-676}));
    }
    public void testDE00051() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,206,458,960,-100,609,699,-76,-357,1000,996,1000,156,482,36,-207,558,-537,-1000,-1000,92,65,-1000,103,1000,-986,-1000,1000,-363,495,-1000,1000,-621,1000,-1000,163,1000,1000,-1000,364,1000,-60,-579,-902,1000,266,23,-1000,-254,314,970,842,442,-1000,1000,564,-1000,-458,-1000,-1000,1000,-1000,52,1000}));
    }
    public void testDE00052() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,1000,465,799,1000,905,335,-287,-1000,669,42,318,345,1000,-357,-1000,320,-815,-253,-627,1000,560,-1000,373,1000,-413,-266,1000,1000,-221,-1000,569,-1000,420,-1000,720,1000,1000,-1000,573,404,593,-1000,1000,1000,1000,-905,-1000,-1000,70,1000,-325,231,-1000,991,1000,-1000,-183,-1000,-1000,1000,-1000,723,-743}));
    }
    public void testDE00053() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{220,963,-141,-137,793,709,147,-907,-1000,217,-411,-484,1000,637,-663,-560,-1000,-711,534,876,780,-90,-171,222,338,-1000,-72,531,-418,109,79,-1000,-699,-1000,-954,814,1000,-621,-814,525,-1000,177,-1000,667,24,1000,773,-524,-263,83,55,-1000,-658,-200,697,449,531,-830,-1000,215,-34,222,1000,732}));
    }
    public void testDE00054() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{400,1000,408,165,699,31,835,234,657,968,-53,804,-254,744,-659,119,1000,-163,140,52,407,-416,-193,803,-632,428,833,-259,189,-42,675,757,-247,400,-96,1000,-344,-157,-126,-416,32,-704,-157,1000,-40,129,-199,244,-917,380,67,378,-215,-240,445,647,-576,-372,372,879,-510,-160,-916,-607}));
    }
    public void testDE00055() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-685,428,119,-495,837,-673,-1000,-733,-350,445,-441,-1000,846,198,1000,-361,203,1000,-390,-162,514,576,-236,-1000,-1000,-751,673,9,-1000,-455,-939,436,165,512,209,105,650,-294,874,-391,-755,-993,538,746,-348,658,-2,527,-329,849,-87,20,-843,361,-1000,1000,407,1000,-1000,23,260,-278,-338,-866}));
    }
    public void testDE00056() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-1000,942,-307,-587,-1000,450,-583,591,292,-383,344,-713,-993,185,-375,1000,-271,-1000,466,1000,359,910,-650,53,702,-891,-511,460,-454,-789,312,-519,666,-1000,-594,-554,-574,993,146,-1000,279,1000,-201,-1000,-107,-365,169,589,537,125,627,-1000,1000,1000,-1000,904,-358,-220,322,-630,823,-961,1000,-963}));
    }
    public void testDE00057() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{1000,1000,1000,-193,802,274,764,-94,673,1000,-1000,-2,-225,892,1000,-89,833,195,448,566,351,-279,-158,898,-1000,-6,1000,48,-345,-410,654,1000,354,1000,-566,711,-50,-1000,-608,-550,723,-480,85,738,28,142,144,-270,633,970,-332,775,-893,-696,529,21,-635,211,490,570,-657,-641,-712,-967}));
    }
    public void testDE00058() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{877,806,328,-902,417,211,764,-609,-806,-383,564,-519,822,-428,-218,344,833,-667,-335,461,-405,910,-650,898,-189,-6,327,128,-429,-789,-447,495,282,140,-261,-362,224,993,-934,272,968,930,303,268,-545,-365,-807,194,-897,-86,-200,-867,-958,-106,529,-63,-728,-340,490,-120,-657,-929,8,100}));
    }
    public void testDE00059() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-365,968,976,-322,-49,29,93,-578,-269,93,48,459,62,-264,242,801,550,417,391,-175,87,70,846,471,693,811,314,-153,189,-461,-331,426,-140,-838,204,-515,657,668,381,483,-6,334,865,650,732,-11,-473,231,566,-572,-79,556,-309,856,745,362,-626,-490,-205,678,-162,522,-3,-44}));
    }
    public void testDE00060() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-196,-1000,-482,-945,-749,-229,-925,336,-755,224,981,247,402,-845,578,-1000,-574,207,416,307,-942,1000,21,-135,247,-407,632,1000,813,55,-66,-81,-704,-46,817,608,299,-1000,558,594,-59,644,-292,-489,-656,129,-239,713,624,955,152,433,1000,-1000,-285,-29,-1000,1000,-437,-200,1000,-1000,-153,249}));
    }
}
