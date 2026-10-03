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
        assertEquals("TYPE:org.apache.commons.cli.OptionGroup", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "addOption(org.apache.commons.cli.Option):org.apache.commons.cli.OptionGroup",
            new int[]{-804,895,389,-1000,-1000,1000,-1000,728,151,-1000,15,1000,842,-234,-1000,1000,1000,1000,823,1000,-415,1000,-984,-1000,-1000,1000,-1000,-985,-887,-1000,-1000,1000,-603,1000,-785,340,-545,227,1000,-1000,74,780,-1000,1000,-1000,559,-1000,-673,932,-1000,295,680,-230,-1000,1000,622,-1000,-493,-1000,443,587,-121,1000,-891}));
    }
    public void testDE00001() {
        assertEquals("TYPE:org.apache.commons.cli.OptionGroup", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "addOption(org.apache.commons.cli.Option):org.apache.commons.cli.OptionGroup",
            new int[]{-1000,1000,769,-1000,-1000,1000,-1000,1000,280,-1000,-271,-192,1000,1000,-1000,1000,-2,907,1000,1000,-1000,1000,-1000,983,-1000,1000,-1000,-1000,-348,-1000,-971,911,404,1000,-1000,1000,83,-1000,1000,-1000,-1000,207,-1000,-441,-1000,170,-1000,-1000,1000,-1000,146,1000,231,-1000,1000,1000,-1000,686,-1000,1000,1000,1000,1000,-1000}));
    }
    public void testDE00002() {
        assertEquals("TYPE:java.util.LinkedHashMap$LinkedKeySet", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "getNames():java.util.Collection",
            new int[]{-12,309,354,673,1000,-1000,-773,-740,-399,131,-703,-366,352,-439,-1000,-610,174,-41,-1000,-1000,1000,-490,474,-712,503,893,757,-869,1000,-657,578,-375,-121,216,-535,-943,-297,1000,1000,1000,-594,-529,205,67,215,428,551,568,-771,145,143,1000,-111,658,-301,-474,931,-1000,668,1000,495,607,-123,1000}));
    }
    public void testDE00003() {
        assertEquals("TYPE:java.util.LinkedHashMap$LinkedKeySet", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "getNames():java.util.Collection",
            new int[]{520,879,-241,191,-59,157,169,-931,1000,-334,-880,1000,-225,-82,634,-1000,-593,1000,390,-524,-363,-866,689,-215,-1000,325,-606,-1000,1000,352,-1000,-314,1000,1000,-738,751,988,-177,-612,-763,-1000,-473,-200,-425,-1000,609,-183,-1000,-371,927,-103,-671,-1000,-1000,1000,-774,1000,-606,1000,-457,-63,-539,192,630}));
    }
    public void testDE00004() {
        assertEquals("TYPE:java.util.LinkedHashMap$LinkedKeySet", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "getNames():java.util.Collection",
            new int[]{-245,900,848,-499,637,-498,198,-78,1000,-334,-134,480,35,-532,634,-1000,-484,1000,526,1000,83,252,667,-289,-700,465,-848,-154,1000,337,-155,-236,632,556,-935,826,1000,526,-243,-831,-741,-36,-1000,287,-1000,715,-196,-471,-1000,1000,-1000,-671,-225,-920,1000,-905,1000,-1000,1000,-751,1000,-794,-910,524}));
    }
    public void testDE00005() {
        assertEquals("TYPE:java.util.LinkedHashMap$LinkedValues", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "getOptions():java.util.Collection",
            new int[]{1000,-1000,54,-439,1000,-9,1000,-1000,1000,258,830,-701,228,-810,-383,-337,-1000,-253,-1000,-726,128,517,-922,-1000,-1000,35,-1000,323,-221,-1000,-1000,-934,-1000,79,-243,1000,-785,-1000,-967,613,-680,921,-1000,-1000,-1000,-1000,-245,-624,-1000,-1000,-1000,1000,-373,940,-725,527,-372,1000,1000,-73,-230,1000,-458,760}));
    }
    public void testDE00006() {
        assertEquals("TYPE:java.util.LinkedHashMap$LinkedValues", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "getOptions():java.util.Collection",
            new int[]{-677,-729,79,1000,-596,-982,810,849,-396,-803,-305,-112,-1000,-1000,-562,412,-211,638,-1000,-1000,-470,-1000,-1000,1000,-391,-730,564,-400,-659,-924,573,1000,162,-1000,1000,-85,-1000,1000,1000,1000,-379,534,-1000,656,838,478,400,1000,-1000,-1000,397,-130,282,-534,-892,185,1000,288,-516,395,-166,-147,1000,-260}));
    }
    public void testDE00007() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "getSelected():java.lang.String",
            new int[]{816,-1000,739,-889,1000,-1000,1000,1000,1000,-1000,-1000,876,-1000,755,-1000,-1000,353,906,-56,-1000,-1000,-1000,-331,597,716,-398,510,1000,-1000,667,904,211,836,-1000,977,178,-80,1000,-436,-1000,-650,-852,947,-1000,-1000,-1000,-1000,-547,747,72,1000,878,1000,-1000,-74,1000,705,-1000,-710,676,-1000,-301,-1000,-400}));
    }
    public void testDE00008() {
        assertEquals("java.lang.String:YWFhYWFh", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "getSelected():java.lang.String",
            new int[]{-1000,1000,984,-452,215,-400,239,-836,-223,220,-194,1000,531,822,-1000,1000,-389,565,869,-1000,-681,-983,-48,-1000,-340,555,1000,836,587,558,-1000,-730,-885,428,-974,-417,-195,-1000,-1000,1000,978,-290,706,791,-536,1000,1000,-705,692,-920,476,-588,-1000,1000,1000,-1000,1000,1000,977,-893,683,1000,1000,1000}));
    }
    public void testDE00009() {
        assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "isRequired():boolean",
            new int[]{-1000,-862,179,824,218,-814,-482,759,634,-1000,1000,764,-1000,-1000,1000,543,-1000,92,491,-564,-1000,69,-989,1000,-1000,593,-588,1000,1000,1000,-1000,731,-1000,-1000,-151,1000,-1000,685,1000,-1000,1000,-487,-1000,872,-489,35,-666,1000,-1000,-1000,1000,135,-578,-834,1000,-532,-1000,-28,-459,832,-75,-646,-1000,623}));
    }
    public void testDE00010() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "isRequired():boolean",
            new int[]{-223,-334,-681,-1000,817,1000,-1000,1000,1000,-984,512,1000,1000,-1000,1000,1000,-978,-1000,-47,1000,1000,452,-310,54,206,1000,286,1000,-448,749,-608,187,-251,-1000,1000,1000,-1000,1000,783,1000,1000,469,-1000,1000,-395,626,700,1000,-718,-470,935,1000,734,1000,1000,989,-105,791,-736,290,-1000,1000,-67,245}));
    }
    public void testDE00011() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "isRequired():boolean",
            new int[]{-793,-413,-71,-1000,87,130,-157,491,1000,-832,1000,-698,674,700,122,-697,99,-241,751,702,1000,119,-748,1000,512,-158,336,137,-192,98,-643,1000,353,-1000,1000,-303,-1000,676,7,202,-7,-1000,1000,93,22,-403,1000,774,-420,-3,865,477,-1000,226,45,162,520,1000,670,1000,24,579,-330,-309}));
    }
    public void testDE00012() {
        assertEquals("VOID|isRequired=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "setRequired(boolean):void",
            new int[]{1000,-1000,789,-960,755,1000,-1000,292,796,1000,471,336,-801,1000,538,-1000,848,-760,-49,-381,-137,-322,913,-1000,-897,-977,302,-1000,-468,691,127,-1000,-506,-847,-1000,-1000,-46,257,1000,1000,735,1000,349,-49,1000,1000,350,836,-1000,-729,-1000,931,68,1000,706,-957,-1000,-383,-81,659,77,1000,1000,545}));
    }
    public void testDE00013() {
        assertEquals("VOID|isRequired=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "setRequired(boolean):void",
            new int[]{-50,44,654,-130,1000,1000,-396,1000,1000,140,-496,-1000,1000,1000,-967,-1000,1000,-1000,90,-1000,-1000,-610,-132,-963,-853,20,803,-1000,-359,1000,1000,-350,1000,-540,-1000,-865,-238,629,1000,1000,365,822,1000,215,1000,597,-515,-770,-1000,1000,939,998,-6,1000,-579,-1000,-1000,-919,1000,209,-808,1000,1000,-220}));
    }
    public void testDE00014() {
        assertEquals("VOID|getSelected=NULL", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "setSelected(org.apache.commons.cli.Option):void",
            new int[]{65,-998,509,1000,-634,-1000,179,-1000,1000,1000,-821,622,-653,1000,-1000,280,837,1000,-817,1000,915,-343,1000,339,1000,-1000,548,-1000,325,26,-28,491,482,-118,1000,-564,-128,-160,1000,585,-1000,129,1000,218,55,1000,450,275,-451,830,-1000,62,102,902,1000,-489,1000,1000,-349,-1000,-56,235,-823,-1000}));
    }
    public void testDE00015() {
        assertEquals("VOID|getSelected=java.lang.String:", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "setSelected(org.apache.commons.cli.Option):void",
            new int[]{56,-1000,459,331,804,248,622,-754,1000,1000,-1000,1000,1000,178,-1000,1000,-621,1000,-1000,810,1000,-997,1000,1000,349,-953,867,-400,884,359,1000,700,-25,-153,751,-1000,-1000,-437,1000,503,-1000,396,1000,-46,1000,1000,1000,1000,-1000,1000,-639,-1000,1000,687,402,-556,297,449,-1000,-1000,1000,-692,-426,-443}));
    }
    public void testDE00016() {
        assertEquals("java.lang.String:Wy1kX3YgMHg4MDAwMDAwMDAwMDAwMDAwMCwgLS1udWxsXQ==", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "toString():java.lang.String",
            new int[]{-528,114,4,770,557,1000,-1000,-843,637,237,-180,148,-103,-574,992,-1000,355,-677,-457,1000,664,-55,1000,1000,-30,901,-1000,932,138,-939,796,994,830,-828,-364,-278,341,-121,-818,242,537,-536,-433,27,-1000,-297,-413,-1000,386,-660,-1000,-197,416,-444,-438,1000,288,-1000,-524,-437,-128,234,-715,420}));
    }
    public void testDE00017() {
        assertEquals("java.lang.String:Wy0weDEyZCAweDgwXQ==", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "toString():java.lang.String",
            new int[]{-129,665,-291,-322,816,1000,-1000,1000,-535,-244,1000,690,589,-937,1000,-287,-1000,715,-13,1000,297,-927,1000,379,788,21,202,711,301,-584,540,-1000,-585,-1000,601,-452,-120,963,162,-1000,522,-312,175,-687,275,-1000,1000,-328,196,-345,-478,837,-1000,-1000,139,-255,298,-962,555,1000,-788,-448,-34,-985}));
    }
    public void testDE00018() {
        assertEquals("java.lang.String:W10=", DEReplay.run(
            "org.apache.commons.cli.OptionGroup", "org.apache.commons.cli.OptionGroup", "toString():java.lang.String",
            new int[]{960,-1000,-402,-1000,290,-1000,1000,91,689,-372,-755,-400,-1000,-696,-233,434,-394,-43,686,-927,-555,-437,271,-190,-243,-1000,404,-1000,-1000,-304,-1000,698,-1000,926,-711,-978,1000,-1000,-1000,404,-789,1000,282,-493,-213,-542,80,1000,905,-14,-346,1000,1000,-847,-1000,-1000,-322,27,231,-1000,-388,636,346,-1000}));
    }
    public void testDE00019() {
        assertEquals("TYPE:org.apache.commons.cli.Options", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "addOption(java.lang.String,boolean,java.lang.String):org.apache.commons.cli.Options",
            new int[]{297,-367,604,-382,-222,11,-611,89,113,-261,237,220,343,-330,137,-978,583,224,626,-580,473,-467,907,-607,246,-532,-976,-964,-700,455,-204,506,49,-482,962,-517,57,359,-859,312,964,-560,630,311,580,10,812,-157,639,807,-3,-274,-808,-208,898,305,-613,-347,-392,-939,-523,-930,-39,57}));
    }
    public void testDE00020() {
        assertEquals("TYPE:org.apache.commons.cli.Options", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "addOption(java.lang.String,boolean,java.lang.String):org.apache.commons.cli.Options",
            new int[]{-269,909,-586,-903,1000,-254,621,65,-135,-390,-578,-113,853,-1000,-1000,4,-70,479,-303,-445,-209,-424,-201,591,692,-632,835,-607,572,717,172,125,-204,-226,891,-270,426,227,843,-836,-422,-213,-1000,883,-25,-671,447,716,121,-124,-1000,-872,-626,-1000,45,907,764,1000,-1000,535,-960,276,-42,-458}));
    }
    public void testDE00021() {
        assertEquals("TYPE:org.apache.commons.cli.Options", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "addOption(java.lang.String,java.lang.String):org.apache.commons.cli.Options",
            new int[]{-789,-850,-102,-124,-207,183,-1000,908,-990,582,548,-833,945,182,5,-893,1000,-1000,-1000,1000,-717,-1000,1000,810,-1000,366,328,898,-92,-1000,687,1000,-1000,-21,-104,709,1000,-928,58,6,-767,-813,555,1000,866,-275,-634,1000,-96,-418,-814,-1000,215,683,-525,-1000,-230,120,1000,907,-1000,-388,-175,-225}));
    }
    public void testDE00022() {
        assertEquals("TYPE:org.apache.commons.cli.Options", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "addOption(java.lang.String,java.lang.String,boolean,java.lang.String):org.apache.commons.cli.Options",
            new int[]{148,398,-611,48,303,-286,575,178,7,972,-586,415,-1000,355,965,-580,813,-173,648,142,3,-448,-669,-152,-1000,719,-506,-28,-624,-1000,-665,-30,-165,-833,-409,-1000,-1000,-1000,-1000,622,-206,-276,253,47,78,-21,-780,327,186,-1000,-959,-124,-405,202,1000,-666,-429,-894,1000,-1000,1000,169,-1000,391}));
    }
    public void testDE00023() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "addOption(org.apache.commons.cli.Option):org.apache.commons.cli.Options",
            new int[]{-1000,-442,79,797,329,-1000,1000,-860,1000,689,211,-1000,283,1000,-990,-1000,547,252,-822,1000,557,1000,113,-626,1000,1000,1000,-1000,1000,-211,1000,-1000,-597,711,-304,1000,-169,-1000,193,-345,711,1000,922,-1000,-1000,-1000,-413,-1000,-599,-312,1000,1000,608,1000,-692,1000,-92,-259,1000,-271,274,-471,-888,-63}));
    }
    public void testDE00024() {
        assertEquals("TYPE:org.apache.commons.cli.Options", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "addOptionGroup(org.apache.commons.cli.OptionGroup):org.apache.commons.cli.Options",
            new int[]{668,941,-66,278,656,-1000,-515,-1000,-784,1000,382,754,64,-1000,-219,-217,-924,-552,621,-274,-495,-629,1000,-758,359,-408,671,252,1000,593,-509,155,1000,-1000,741,-642,1000,652,-1000,-1000,-1000,392,-72,-120,-242,1000,1000,-559,1000,-628,458,-675,66,-1000,406,-171,5,586,-901,163,-559,702,1000,-120}));
    }
    public void testDE00025() {
        assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getMatchingOptions(java.lang.String):java.util.List",
            new int[]{-474,-357,-556,-1000,1000,-269,-420,-853,-535,1000,-934,-1000,-209,-702,-1000,-444,1000,1000,1000,-229,-576,206,-859,-623,196,-1000,-109,322,-1000,-666,1000,-510,597,1000,-375,-694,349,-889,2,-516,-283,-567,-556,1000,982,395,44,-1000,1000,390,1000,-112,-505,-1000,-1000,448,453,-124,-1000,-273,-555,-118,-1000,1000}));
    }
    public void testDE00026() {
        assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getMatchingOptions(java.lang.String):java.util.List",
            new int[]{1000,807,58,670,-1000,-1000,-1000,1000,942,-1000,-61,-1000,593,-1000,-1000,-444,-788,-1000,631,1000,1000,-1000,1000,-169,381,1000,247,-1000,330,1000,-339,-179,-1000,-342,763,-1000,1000,-464,-329,1000,-1000,1000,-1000,1000,-1000,-241,1000,-672,1000,29,1000,1000,-311,1000,76,-1000,646,-426,-389,-1000,81,-502,497,-1000}));
    }
    public void testDE00027() {
        assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getMatchingOptions(java.lang.String):java.util.List",
            new int[]{254,1000,-251,-1000,900,555,792,-1000,-1000,217,-825,772,-431,-965,-723,754,-474,-181,440,-1000,-1000,13,-623,703,-1000,-998,-1000,1000,-974,-1000,657,-991,33,308,-860,549,-1000,-889,845,-413,636,-859,255,-341,179,1000,-260,677,753,-651,932,1000,-408,-1000,1000,145,483,-758,-76,1000,-1000,-234,-1000,1000}));
    }
    public void testDE00028() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getOption(java.lang.String):org.apache.commons.cli.Option",
            new int[]{-1000,-200,-206,427,706,-498,-604,652,218,-955,-1000,10,-615,663,1000,-1000,1000,-461,-835,-558,-1000,709,-272,1000,-1000,569,-1000,1000,1000,-170,-656,545,282,160,683,-854,429,-1000,475,1000,28,-863,-164,407,-403,-1000,-233,1000,-1000,-1000,236,808,57,-249,-90,-1000,1000,-38,1000,-590,-554,-1000,1000,1000}));
    }
    public void testDE00029() {
        assertEquals("TYPE:org.apache.commons.cli.Option", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getOption(java.lang.String):org.apache.commons.cli.Option",
            new int[]{346,-1000,-516,1000,80,442,80,114,18,642,458,791,731,209,-647,320,-468,-30,103,247,1000,957,126,-1000,825,-422,1000,-209,453,-673,866,-1000,332,-1000,-1000,352,263,1000,-1000,-1000,672,141,-1000,-1000,-177,-197,401,-914,220,514,-804,69,-1000,-500,-579,5,-85,-1000,-675,-898,-1000,1000,-1000,-166}));
    }
    public void testDE00030() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getOptionGroup(org.apache.commons.cli.Option):org.apache.commons.cli.OptionGroup",
            new int[]{845,348,833,-768,-721,155,369,-1000,347,-1000,853,1000,1000,366,-910,-708,29,698,12,-1000,936,-1000,1000,-1000,50,900,-695,923,-1000,1000,1000,47,1000,-874,-677,-499,1000,-175,179,-268,-10,-801,991,253,-350,716,801,541,840,1000,-312,-1000,649,-487,-906,1000,-642,-980,-1000,944,-949,1000,-880,-1000}));
    }
    public void testDE00031() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getOptionGroup(org.apache.commons.cli.Option):org.apache.commons.cli.OptionGroup",
            new int[]{299,202,-612,-679,-879,-190,-1000,1000,804,887,-1000,910,-703,-1000,-469,-1000,-1000,900,1000,-775,212,682,-1000,-436,923,-1000,313,-423,41,-1000,-3,-736,-822,39,1000,-1000,-181,461,1000,-1000,781,32,344,-1000,1000,-378,320,251,-924,-1000,-828,1000,-907,641,-326,-1000,1000,1000,-636,-773,275,348,-92,626}));
    }
    public void testDE00032() {
        assertEquals("TYPE:java.util.Collections$UnmodifiableCollection", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getOptions():java.util.Collection",
            new int[]{-924,-1000,-311,-164,-815,-965,275,98,-1000,301,-522,-911,-341,330,925,388,1000,308,603,-1000,171,-251,614,350,636,244,106,-816,-642,868,-117,-725,-671,974,-769,674,424,53,-1000,-1000,188,-1000,182,-504,-33,-870,951,913,-1000,-400,-68,624,362,707,1000,541,-272,-876,-356,-981,-1000,-1000,1000,-698}));
    }
    public void testDE00033() {
        assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getRequiredOptions():java.util.List",
            new int[]{-760,197,559,-1000,-1000,170,-261,-1000,1000,-1000,555,-1000,-962,1000,327,364,-1000,-775,-470,237,662,955,-3,946,209,1000,-979,-848,376,293,225,1000,1000,-1000,848,35,1000,-36,1000,1000,1000,-1000,-773,856,679,1000,-325,799,-336,619,763,-1000,-1000,-661,34,-287,427,1000,-1000,201,602,-757,1000,795}));
    }
    public void testDE00034() {
        assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "getRequiredOptions():java.util.List",
            new int[]{-1000,957,-642,-897,137,545,-400,1000,-1000,64,804,587,158,-1000,-1000,-1000,1000,53,-154,70,1000,623,-643,944,108,-414,1000,-681,-1000,248,596,-1000,-27,20,-1000,-480,-1000,918,775,-1000,-172,-239,495,-38,-631,-1000,-804,-393,1000,-106,379,181,487,-681,1000,1000,354,-1000,210,294,-834,1000,-1000,15}));
    }
    public void testDE00035() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasLongOption(java.lang.String):boolean",
            new int[]{-266,370,-291,1000,0,-344,-1000,-567,17,-312,148,582,-1000,331,539,606,379,454,808,1000,-567,1000,814,343,-1000,243,1000,125,-769,547,-590,-675,1000,146,1000,-51,1000,682,-530,-326,-1000,1000,-999,793,1000,275,291,75,361,350,-386,288,-326,62,-528,34,1000,-1000,211,974,-772,856,528,-327}));
    }
    public void testDE00036() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasLongOption(java.lang.String):boolean",
            new int[]{-186,787,-566,-740,1000,1000,391,-677,-359,-516,-598,-1000,479,-206,-1000,-193,-135,364,1000,-211,845,619,-400,-1000,1000,-1000,-325,-152,711,541,781,-53,-1000,-1000,-579,1000,1000,516,-346,731,1000,-575,539,-1000,-170,1000,-823,-420,-591,-439,958,-1000,170,116,116,-400,66,274,-770,-494,-719,565,-488,212}));
    }
    public void testDE00037() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasOption(java.lang.String):boolean",
            new int[]{-526,-397,219,-685,1000,-891,-1000,1000,860,153,-1000,-283,718,-1000,715,-348,-398,-1000,-561,1000,-499,1000,1000,-436,1000,289,-735,-287,-1000,203,1000,-1000,857,-1000,301,937,-1000,-1000,-2,-945,952,-953,-774,-860,1000,840,1000,1000,1000,777,-369,850,460,1000,-755,-1000,-1000,-1000,892,871,-1000,-998,-448,984}));
    }
    public void testDE00038() {
        assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasOption(java.lang.String):boolean",
            new int[]{-370,39,774,921,566,-151,736,563,-1000,-1000,114,113,90,-44,-622,27,291,-429,200,990,687,-318,-1000,254,690,-256,-1000,-943,502,1000,1000,-389,-305,-1000,-289,582,-32,-1000,-1000,-1000,915,-733,-438,718,-30,1000,1000,-849,663,720,-1000,-469,-764,662,-97,-1000,810,-736,-1000,-67,-1000,1000,159,-621}));
    }
    public void testDE00039() {
        assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasOption(java.lang.String):boolean",
            new int[]{-1000,-180,47,-834,332,-247,-1000,1000,1000,881,-1000,260,161,-1000,563,-1000,20,-753,1000,913,-714,992,1000,193,302,-45,-546,-603,-67,-1000,1000,-1000,1000,-1000,279,336,-113,-1000,400,-813,-109,-1000,-425,-1000,669,934,476,1000,-208,1000,-1000,308,488,1000,-180,-1000,-1000,-1000,296,813,-852,-1000,-1000,652}));
    }
    public void testDE00040() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasShortOption(java.lang.String):boolean",
            new int[]{1000,49,524,-274,-230,-480,1000,-661,996,-743,-1000,83,494,891,-492,-148,311,-1000,1000,-593,-374,-829,911,62,-1000,-351,1000,-192,-408,-1000,-507,1000,-1000,1000,-412,-990,72,-1000,1000,619,11,-557,-1000,38,-451,779,50,899,-416,408,-508,771,1000,164,1000,-342,-1000,700,-615,-1000,-1000,255,154,795}));
    }
    public void testDE00041() {
        assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "hasShortOption(java.lang.String):boolean",
            new int[]{697,1000,-582,-181,584,-26,812,1000,999,-1000,-810,343,-547,1000,-955,-410,1000,-584,213,617,77,222,-184,1000,-1000,203,-1000,88,424,-99,-219,129,469,1000,-373,-992,-1000,58,994,1000,-1000,-600,-1000,529,504,1000,-580,611,-1000,-571,-359,534,-46,-424,230,-831,266,1000,419,-31,1000,1000,58,541}));
    }
    public void testDE00042() {
        assertEquals("java.lang.String:WyBPcHRpb25zOiBbIHNob3J0IHthYWFhYWFhYWFhYT1bIG9wdGlvbjogYWFhYWFhYWFhYWEgIDo6IDB4MTI3IDo6IGNsYXNzIGphdmEubGFuZy5TdHJpbmcgXSwgMHg4MDAwMD1bIG9wdGlvbjogMHg4MDAwMCAweDgwMDAwMDAwMDAwMDAwMDAwMDAgIFtBUkddIDo6IG51bGwgOjogY2xhc3MgamF2YS5sYW5nLlN0cmluZyBdfSBdIFsgbG9uZyB7MHg4MDAwMDAwMDAwMDAwMDAwMDAwPVsgb3B0aW9uOiAweDgwMDAwIDB4ODAwMDAwMDAwMDAwMDAwMDAwMCAgW0FSR10gOjogbnVsbCA6OiBjbGFzcyBqYXZhLmxhbmcuU3RyaW5nIF19IF0=", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "toString():java.lang.String",
            new int[]{-85,1000,284,-93,197,-1000,348,-1000,813,661,939,359,-295,580,98,-1000,-1000,-455,316,244,-1000,1000,-1000,-62,670,-992,369,1000,-1000,824,-947,430,-944,-17,-1000,-548,-559,1000,1000,-178,-497,-605,-68,274,178,480,-965,633,-202,-43,-769,1000,196,-878,15,662,1000,-63,-432,-48,-277,-177,108,-847}));
    }
    public void testDE00043() {
        assertEquals("java.lang.String:WyBPcHRpb25zOiBbIHNob3J0IHsweDgwMDAwMDAwMDA9WyBvcHRpb246IDB4ODAwMDAwMDAwMCAgOjogYUlfbGJfcFZWQ29fdVIgOjogY2xhc3MgamF2YS5sYW5nLlN0cmluZyBdLCBNWVlIek51bV9ZX2M9WyBvcHRpb246IE1ZWUh6TnVtX1lfYyAgOjogMHgyMjggOjogY2xhc3MgamF2YS5sYW5nLlN0cmluZyBdfSBdIFsgbG9uZyB7fSBd", DEReplay.run(
            "org.apache.commons.cli.Options", "org.apache.commons.cli.Options", "toString():java.lang.String",
            new int[]{-1000,1000,964,941,562,-272,-93,-1000,303,159,243,563,-841,-1000,1000,-475,-1000,69,-137,-162,1000,1000,-111,-416,-524,-1000,-50,863,-1000,664,625,980,322,592,-1000,-41,763,417,831,-824,-613,112,190,-366,-437,-951,-462,191,101,235,-1000,699,-1000,296,-713,552,-152,331,-523,-398,-345,294,406,493}));
    }
    public void testDE00044() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[]):org.apache.commons.cli.CommandLine",
            new int[]{-753,-121,13,830,-131,821,-520,-587,-913,-540,-203,-234,-41,-181,-669,-207,-811,-532,26,-23,-536,821,-434,507,-353,663,249,607,396,-4,85,244,750,681,-949,426,574,-480,-876,-852,321,466,771,-262,-456,-560,879,791,817,-91,-215,205,276,774,21,-74,-135,-573,647,918,-432,-130,665,-394}));
    }
    public void testDE00045() {
        assertEquals("TYPE:org.apache.commons.cli.CommandLine", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[],boolean):org.apache.commons.cli.CommandLine",
            new int[]{441,379,18,-817,680,102,-166,41,588,47,-707,-447,909,133,-761,-349,990,120,498,-590,841,991,886,405,182,93,7,884,-530,-167,-542,-390,-984,339,-764,74,66,128,-128,-215,-55,-390,181,-691,279,30,343,-136,647,-695,-502,361,45,156,39,-953,-959,911,-666,-881,607,-600,-241,50}));
    }
    public void testDE00046() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[],java.util.Properties):org.apache.commons.cli.CommandLine",
            new int[]{-177,-301,-513,-936,513,996,-506,506,-363,980,457,-881,876,121,-7,-714,322,-372,987,680,-686,816,195,-950,-438,-96,-134,893,-687,314,289,949,-624,-928,-67,936,-303,866,-622,735,891,504,-417,-608,630,29,204,692,123,617,-328,841,-952,-495,-505,-182,243,676,901,-644,244,799,295,-830}));
    }
    public void testDE00047() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.DefaultParser", "org.apache.commons.cli.DefaultParser", "parse(org.apache.commons.cli.Options,java.lang.String[],java.util.Properties,boolean):org.apache.commons.cli.CommandLine",
            new int[]{-770,-79,966,576,-505,235,36,258,-92,-459,-306,417,423,848,-151,125,-1,-940,-979,-484,941,-256,-441,79,603,-566,90,346,-812,192,-388,977,750,896,-644,643,172,-688,682,361,-95,378,-366,957,551,-665,-486,184,994,-654,-135,712,710,71,-438,-349,-859,284,-604,597,565,91,-527,258}));
    }
    public void testDE00048() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    public void testDE00049() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-821,453,-527,-981,-597,631,851,-673,-225,-496,715,746,-446,-198,498,-875,277,-998,-218,660,-87,518,246,118,179,-705,365,-709,102,108,-335,283,820,-936,-586,813,-674,270,512,-350,431,468,-962,-527,548,546,-576,-11,80,243,545,-11,-829,-841,-839,-842,366,-815,73,926,-667,870,-868,412}));
    }
    public void testDE00050() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.Parser", "org.apache.commons.cli.BasicParser,org.apache.commons.cli.GnuParser,org.apache.commons.cli.PosixParser", "parse(org.apache.commons.cli.Options,java.lang.String[]):org.apache.commons.cli.CommandLine",
            new int[]{89,-120,-649,-682,-463,-327,-249,-559,-108,-353,84,545,618,951,110,832,227,797,526,-899,37,-678,-403,-92,-25,645,938,119,-934,-653,-205,918,-579,-361,-927,308,705,966,-621,-260,369,906,-630,598,-760,-106,-167,-13,460,499,-939,506,214,792,365,-499,-773,652,841,-318,556,-189,-139,923}));
    }
    public void testDE00051() {
        assertEquals("TYPE:org.apache.commons.cli.CommandLine", DEReplay.run(
            "org.apache.commons.cli.Parser", "org.apache.commons.cli.BasicParser,org.apache.commons.cli.GnuParser,org.apache.commons.cli.PosixParser", "parse(org.apache.commons.cli.Options,java.lang.String[],boolean):org.apache.commons.cli.CommandLine",
            new int[]{-571,905,-143,-408,73,951,424,953,464,-937,469,501,-585,481,431,180,653,-558,785,225,-978,-983,-693,-25,-991,630,-616,-448,637,-451,-148,305,747,38,-743,955,14,-946,149,225,-367,371,24,-605,577,-143,902,-271,544,-757,-853,-22,-712,184,-891,298,-117,667,453,806,-344,-105,-384,-238}));
    }
    public void testDE00052() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.Parser", "org.apache.commons.cli.BasicParser,org.apache.commons.cli.GnuParser,org.apache.commons.cli.PosixParser", "parse(org.apache.commons.cli.Options,java.lang.String[],java.util.Properties):org.apache.commons.cli.CommandLine",
            new int[]{-742,-92,1000,27,928,636,-188,-119,-761,-38,1000,-1000,-568,-304,-739,-565,-197,197,-715,-514,83,-751,557,-1000,-194,-286,-1000,1000,-886,-681,-469,-697,534,1000,901,-635,1000,-362,-661,1000,852,-115,125,-258,-1000,191,-82,9,-580,-1000,302,-853,-1000,933,-804,-259,-571,453,-1000,-247,-699,1000,-1000,-604}));
    }
    public void testDE00053() {
        assertEquals("THROW:org.apache.commons.cli.UnrecognizedOptionException", DEReplay.run(
            "org.apache.commons.cli.Parser", "org.apache.commons.cli.BasicParser,org.apache.commons.cli.GnuParser,org.apache.commons.cli.PosixParser", "parse(org.apache.commons.cli.Options,java.lang.String[],java.util.Properties,boolean):org.apache.commons.cli.CommandLine",
            new int[]{-1000,1000,718,-903,-1000,-1000,-101,149,505,7,-1000,742,-698,-305,96,-451,110,-108,-798,-1000,-399,-615,-706,-296,-1000,-1000,-418,-577,-320,183,-327,729,1000,288,-84,-1000,-465,601,-273,1000,-152,625,785,-995,389,-488,100,-412,933,-1000,1000,-1000,939,931,-173,-1000,-28,-280,-1000,74,-1000,274,9,-815}));
    }
}
