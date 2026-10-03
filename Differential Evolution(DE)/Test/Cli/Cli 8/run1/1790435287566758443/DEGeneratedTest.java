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
        assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-458,1000,54,-257,106,-72,381,1000,-560,-492,-1000,-962,-1000,1000,-409,180,-1000,1000,607,976,-181,-1000,670,1000,-511,371,437,-400,-801,-1000,-604,-729,609,1000,-572,766,927,318,-778,-1000,167,-281,-944,179,277,539,-1000,-250,-406,-178,1000,-950,-69,97,-514,-341,892,419,-874,-571,1000,-1000,207,214}));
    }
    public void testDE00001() {
        assertEquals("java.lang.String:YWFhYWFhYQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-267,580,54,107,265,-260,1000,1000,650,-1000,-28,-39,-474,-98,224,764,340,-1000,607,-808,679,114,1000,171,-399,371,1000,-1000,1000,-299,-1000,-100,-511,-1000,-694,1000,868,-1000,-129,-1000,-1000,-337,-1000,-191,-1000,542,-210,-635,-1000,1000,-172,201,-87,-523,-1000,832,-83,-123,-1000,411,-44,399,1000,-996}));
    }
    public void testDE00002() {
        assertEquals("NULL", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getArgName():java.lang.String",
            new int[]{-1000,458,304,-455,783,-795,-1000,656,-1000,396,533,-1000,9,795,1000,516,-1000,-1000,821,202,459,-1000,396,335,-1000,-169,-624,-191,634,-312,1000,-1000,-140,781,904,849,-458,-159,-476,-29,-1000,362,-255,601,127,247,1000,61,537,-745,144,-973,-675,844,-510,-1000,138,99,348,1000,910,1000,-1000,775}));
    }
    public void testDE00003() {
        assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{-933,899,869,-93,457,-579,851,204,623,-406,768,-256,160,-476,799,822,-177,-919,675,-313,57,-743,713,-373,341,474,-75,-319,827,-79,458,-753,158,912,-969,-986,-676,-856,-952,842,-56,-272,-371,-523,462,933,-63,-660,-598,-336,847,-12,-960,-103,-720,330,-346,513,-679,190,-171,-722,621,372}));
    }
    public void testDE00004() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{81,-1000,124,-92,637,-301,383,-1000,-913,97,-400,521,70,25,-571,-1000,-788,788,111,-83,-512,220,-400,1000,-336,-605,-868,-440,-177,-1,-1000,713,-139,4,-874,988,474,-31,-1000,585,1000,-1000,2,693,1000,780,219,-1000,-74,-268,117,-1000,13,-1000,978,117,536,454,-770,-181,234,-1000,-252,-64}));
    }
    public void testDE00005() {
        assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getDescPadding():int",
            new int[]{1000,-776,-166,-10,1000,-1000,764,-1000,287,83,1000,-167,-591,487,218,-912,-949,1000,1000,570,-873,370,1000,724,1000,-628,-100,-1000,577,-529,1000,-482,1000,1000,-151,1000,-1000,-765,-1000,758,1000,-748,267,19,-339,494,139,-833,263,-1000,859,-1000,-1000,-1000,464,-364,191,827,136,-1000,-62,-854,420,-299}));
    }
    public void testDE00006() {
        assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{-857,1000,424,-232,599,-1000,-736,-990,-654,-1000,622,39,-383,-673,-189,1000,594,601,-618,101,-268,-396,-650,-1000,730,-1000,132,-1000,1000,833,-374,-544,312,-640,-664,276,707,-624,-525,216,457,1000,142,75,-151,-1000,-1000,226,1000,547,-777,1000,222,1000,988,794,1000,507,245,-1000,444,-677,467,618}));
    }
    public void testDE00007() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{474,116,714,-511,1000,341,-756,-1000,-774,-87,166,1000,-35,-773,451,245,-187,-504,278,-1000,972,-920,78,-806,438,-1000,-95,-445,311,1000,-584,352,420,-1000,796,597,183,477,339,-548,-724,1000,-1000,-75,-730,236,418,-698,9,471,-1000,1000,563,-182,1000,915,787,651,362,-1000,882,-734,-615,-746}));
    }
    public void testDE00008() {
        assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLeftPadding():int",
            new int[]{474,1000,714,-632,585,-1000,-1000,-1000,-788,-1000,622,-727,44,-839,-631,1000,477,1000,214,-216,-56,499,-877,-1000,909,-1000,-237,-1000,1000,-377,-344,-1000,166,-1000,108,707,995,-871,-1000,753,1000,1000,543,-75,-151,-1000,-1000,1000,1000,1000,-1000,1000,46,-182,1000,1000,1000,265,-551,-815,182,-644,1000,1000}));
    }
    public void testDE00009() {
        assertEquals("java.lang.String:LS0=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{444,-845,-386,-871,-706,48,-142,479,-200,-784,-564,804,-163,-65,900,-862,-253,397,715,-604,-272,-344,-319,489,-935,-522,63,957,-969,22,130,-790,948,697,-467,-746,-725,184,-443,863,837,-380,630,556,-658,258,-283,-553,-805,-499,175,83,278,-802,536,685,-168,-733,259,-103,900,323,-26,88}));
    }
    public void testDE00010() {
        assertEquals("java.lang.String:MjA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getLongOptPrefix():java.lang.String",
            new int[]{-160,-764,-51,-930,169,46,1000,197,-193,-52,-258,115,243,20,602,-3,-662,798,247,3,-338,259,-661,171,342,-509,154,-173,-154,307,628,122,499,-82,-251,-84,-546,438,-280,782,-648,-280,-80,400,-319,-590,7,81,-788,580,466,473,-151,-3,-55,455,-238,835,367,873,453,-109,515,-476}));
    }
    public void testDE00011() {
        assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{-559,812,504,640,-437,-956,490,-812,542,810,220,-376,194,942,-294,-109,661,558,-353,861,-927,846,393,-797,-931,-138,688,38,-86,683,-813,-654,-981,-819,-820,666,12,-520,741,75,222,684,602,-353,401,-110,466,629,-156,-322,-835,-551,-334,-734,-887,295,-66,-615,833,-471,592,-494,987,-545}));
    }
    public void testDE00012() {
        assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{461,-1000,204,-612,914,55,572,449,-1000,-1000,53,-817,763,843,192,356,-750,-691,930,-777,1000,-699,-337,340,93,548,-894,-825,369,454,-314,-166,-261,-606,283,-1000,-336,1000,573,-552,-420,-276,-236,790,-621,24,737,1000,-519,210,-766,-789,-280,653,470,227,-170,-170,-144,-348,807,313,267,307}));
    }
    public void testDE00013() {
        assertEquals("java.lang.String:NjkzLjA=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getNewLine():java.lang.String",
            new int[]{327,-949,-136,-335,39,1000,-425,246,874,-588,901,-693,100,1000,-1000,1000,-1000,-803,1000,662,-77,9,101,1000,646,-937,-985,-761,818,1000,-336,-897,-934,-367,731,-657,-272,1000,1000,-538,-1000,-581,-852,253,-505,-144,493,1000,-1000,-183,-642,-1000,-1000,1000,-796,-400,-1000,-776,1000,-34,1000,227,-650,-31}));
    }
    public void testDE00014() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{678,-860,779,11,-27,253,-489,-989,672,-105,-51,37,161,516,891,188,-881,423,95,-705,958,520,-23,885,240,698,-254,510,-28,-791,694,702,93,581,-472,-953,-99,402,-97,686,-296,662,563,944,-447,-765,-95,-292,-456,-436,-824,684,915,-350,10,-256,-121,-984,555,-843,-968,517,63,752}));
    }
    public void testDE00015() {
        assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-1000,769,-841,647,-133,-457,-839,1000,249,-122,59,539,772,-400,266,-819,693,554,19,1000,797,-55,-174,-129,-90,-33,-641,815,-1000,-824,755,-1000,618,403,-55,-375,-1000,-539,476,414,1000,-529,-37,-667,307,-1000,-443,-716,-30,-146,967,-400,745,-1000,-1000,360,1000,-775,-1000,153,921,-1000,-1000,-1000}));
    }
    public void testDE00016() {
        assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getOptPrefix():java.lang.String",
            new int[]{-145,-610,989,323,352,-29,-1000,-870,-1000,-396,-1000,-1000,123,-795,-539,-395,-275,-214,-937,550,142,-721,202,20,182,52,648,-1000,-1000,-260,-125,-223,-219,860,1000,719,741,13,-723,406,469,152,93,665,446,179,845,1000,977,991,681,-1000,666,131,-1000,-437,633,-133,-707,87,-233,-201,666,1000}));
    }
    public void testDE00017() {
        assertEquals("java.lang.String:MzU3TA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{861,513,189,-167,-794,184,478,-999,-357,48,737,307,-773,-565,-985,819,796,-526,-186,-675,-654,-321,-351,419,-409,-139,-990,-874,-160,-240,173,397,459,254,-320,-351,-821,710,-621,665,298,-869,940,-352,431,-909,-561,984,-124,164,246,-647,-114,519,-203,254,196,-324,-970,692,-11,38,296,-183}));
    }
    public void testDE00018() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{101,940,199,631,-230,719,824,-291,-223,-644,-736,-516,-106,375,-45,661,770,127,-383,-318,-1000,-51,-416,-1000,129,410,1000,-846,-271,831,78,326,-59,-1000,133,28,625,488,-324,-347,96,1000,-504,-118,-1000,-378,-613,1000,680,-53,637,-172,-569,-64,861,536,-288,-976,429,-654,102,23,1000,138}));
    }
    public void testDE00019() {
        assertEquals("java.lang.String:dXNhZ2U6IA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getSyntaxPrefix():java.lang.String",
            new int[]{1000,1000,-36,-318,1000,-339,859,-1000,-450,-1000,1000,-403,830,-1000,1000,-420,-1000,639,-1000,-1000,-303,329,18,419,218,115,-161,-796,-290,766,304,-8,-912,562,-1000,755,-342,1000,-1000,1000,-339,91,1000,-217,-493,-1000,741,1000,810,1000,1000,-652,1000,-86,300,1000,-916,-169,-613,333,-949,1000,1000,-888}));
    }
    public void testDE00020() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{-39,837,-321,-371,412,536,916,-121,33,663,522,835,95,-218,14,794,47,-994,-3,653,976,15,39,-275,-105,-149,-325,996,827,313,-706,-519,391,226,-89,-470,819,-753,-418,142,143,451,-395,-338,-298,-533,-499,598,-989,618,304,-289,-414,-799,671,19,335,-192,-56,-90,-206,-553,456,163}));
    }
    public void testDE00021() {
        assertEquals("java.lang.Integer:NzQ=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{0,-102,99,-1000,969,-631,-698,0,94,-140,773,-615,-180,-740,-397,0,-616,-533,-1000,272,-409,542,1000,-745,-592,-828,0,-417,-362,-180,225,443,-702,673,-895,-400,-662,-194,-344,0,2,-953,-351,-963,-359,0,569,625,-186,-838,-667,-300,923,107,-365,-787,-1000,-527,735,-691,-398,-742,226,265}));
    }
    public void testDE00022() {
        assertEquals("java.lang.Integer:NDI=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "getWidth():int",
            new int[]{196,467,744,879,421,-146,209,537,622,230,-701,-252,-81,44,236,125,317,647,-615,1000,-224,742,1000,111,955,1000,-963,305,795,1000,-238,-858,-468,-1000,110,-393,91,164,955,847,825,1000,-695,1000,-377,-346,-912,102,-720,1000,-204,-1000,-1000,-171,-641,506,315,-483,-204,351,861,-71,-307,-702}));
    }
    public void testDE00023() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{1000,458,903,-1000,174,-1000,667,-547,-782,-188,713,-237,-167,-572,-488,-201,611,612,246,-931,-438,-333,-1000,1000,27,174,-1000,-344,295,-446,47,-435,1000,1000,870,-119,-1000,126,217,-356,-1000,1000,1000,-771,1000,1000,-779,-1000,-1000,-1000,206,-403,-539,-1000,256,-179,742,-744,-326,159,38,-1000,-1000,1000}));
    }
    public void testDE00024() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-780,458,-177,451,-652,-546,-745,-1000,-212,-725,713,-237,466,-572,90,1000,-583,-659,514,-957,150,-944,-1000,-1000,-487,-638,816,752,1000,56,1000,-42,-274,-566,-762,-450,-244,-893,144,1000,377,1000,-396,-518,139,1000,833,81,-419,938,-1000,-1000,-1000,-127,256,-747,-1000,-744,850,159,-153,-601,-269,-811}));
    }
    public void testDE00025() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-379,-869,369,-400,-689,-270,-800,-314,602,-704,-1000,495,-196,479,645,59,219,-247,-868,23,-633,-585,1000,-62,471,-457,712,-153,-648,210,127,611,-66,-1000,686,-492,311,-73,-196,-305,455,295,-503,533,-400,-1000,533,-1000,-122,502,206,140,-745,320,17,265,-312,-935,391,-345,131,-65,1000,-764}));
    }
    public void testDE00026() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-71,-1000,-582,-977,-1000,504,523,423,266,842,214,-59,-971,1000,910,310,284,-1000,-923,1000,-962,-211,400,-877,1000,-336,18,764,-70,-1000,251,1000,-573,-102,-1000,-492,978,158,-62,-152,-572,-631,-1000,1000,1000,-420,1000,-743,400,1000,-112,776,153,1000,755,1000,-1000,84,-1000,-1000,926,1000,1000,-891}));
    }
    public void testDE00027() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-445,-800,-743,-9,174,-946,-108,994,-826,-184,-751,387,-361,914,783,317,611,-1000,669,-89,168,-845,1000,-515,-544,-1000,139,-226,-496,140,850,-300,-1000,-164,-968,-119,148,126,740,592,-244,-292,-640,1000,214,79,-779,233,1000,-205,156,-96,-479,703,168,1000,-636,160,46,-721,1000,316,956,149}));
    }
    public void testDE00028() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-327,-579,284,-317,-903,45,-196,-319,388,-145,-1000,-873,387,-11,-265,-3,1000,537,-571,192,287,-170,-607,-173,463,1000,432,-405,-541,-337,495,-1000,258,1000,144,-200,500,109,951,-1000,-819,743,-217,176,-246,-281,-91,-1000,-433,1000,-351,1000,-488,-4,344,324,82,373,112,-467,1000,1000,424,-45}));
    }
    public void testDE00029() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-72,588,-1000,987,682,46,1000,991,571,436,-72,424,359,-1000,1000,-1000,281,1000,235,-441,-58,-187,177,1000,-460,55,-1000,1000,-83,675,-652,342,838,715,170,-296,-374,510,-399,718,-449,-681,-293,1000,-845,-1000,554,105,-707,-410,-168,874,-492,1000,335,636,-1000,-1000,-132,-478,-798,400,-1000,385}));
    }
    public void testDE00030() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{22,345,339,-809,-224,705,-798,191,811,-572,396,-329,-235,-705,883,-707,-158,65,-678,-151,-130,-499,842,46,-852,470,-385,-83,-511,133,823,-891,-317,252,-459,-270,160,24,-166,-782,-893,564,920,797,-529,-905,654,-539,-656,-736,-222,166,97,-409,-620,-93,-753,-999,852,731,570,631,251,349}));
    }
    public void testDE00031() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-887,533,173,65,-448,-1000,-705,622,-767,-178,393,322,-279,-474,470,-108,449,872,-578,17,587,-896,-407,197,-1000,-1000,-1000,-1000,178,1000,-1000,-113,-583,588,-918,-20,579,946,-691,-32,-1000,-38,-960,-77,4,55,1000,-533,516,-990,-403,105,187,95,-44,1000,307,-516,-416,1000,-1000,-80,133,-435}));
    }
    public void testDE00032() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-964,864,-1000,1000,682,46,1000,274,645,42,141,718,313,-774,1000,-1000,1000,101,-443,-702,786,-187,160,1000,-935,-607,-1000,1000,-223,254,-497,342,1000,1000,143,389,-843,1000,-1000,467,-373,-99,-293,1000,-464,-1000,436,-461,-1000,462,-275,1000,-492,950,-1000,1000,-1000,-1000,268,-539,-1000,1000,-1000,375}));
    }
    public void testDE00033() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,-1000,1000,-709,-1000,-1000,-1000,-308,-1000,782,297,-1000,664,-889,-389,1000,251,-818,566,745,1000,-664,249,-1000,-403,1000,614,-1000,950,581,-303,63,-1000,-13,-1000,-1000,971,1000,310,1000,1000,-1000,-44,-615,-459,803,391,293,1000,-1000,1000,-1000,-1000,823,-1000,948,-336,-418,-1000,-1000,670,-1000,1000,-753}));
    }
    public void testDE00034() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-378,-518,-258,-890,-258,-1000,-172,100,515,1000,-278,-1000,622,-235,-573,66,-943,-333,570,654,1000,-1000,1000,-157,807,408,1000,-104,78,61,-710,-165,-499,1000,-1000,-715,725,1000,74,758,680,174,-909,888,-604,318,1000,-539,341,-1000,370,349,-1000,631,-1000,111,-1000,-83,-1000,-1000,-409,-920,481,993}));
    }
    public void testDE00035() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-128,418,-982,-1000,-704,708,172,671,-717,-277,-163,215,262,-969,1000,46,-66,1000,-462,-549,-1000,-580,83,513,-430,1000,-496,104,-869,-262,-68,312,-45,-385,1000,298,-320,62,-568,-245,-667,837,-36,1000,-389,-318,616,-327,-341,-29,-343,1000,-483,666,-344,119,-103,-401,-857,-32,-69,-508,-481,-625}));
    }
    public void testDE00036() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(int,java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,-384,-426,1000,-1000,1000,1000,991,586,436,-1000,515,1000,-1000,987,-1000,828,1000,235,-1000,-1000,1000,549,64,-1000,-1000,-829,1000,-1000,483,1000,1000,1000,-1000,1000,1000,-1000,24,932,-1000,-1000,977,1000,-1000,830,-1000,518,-539,-846,-410,-1000,1000,642,-409,1000,1000,606,-196,953,-478,-509,1000,-1000,-1000}));
    }
    public void testDE00037() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{763,-715,-406,1000,-1000,-149,-566,-1000,416,1000,-929,109,287,1000,-27,616,-728,1000,-400,-682,426,-312,-821,-1000,-875,602,107,-1000,586,-400,276,575,-18,-367,527,-58,-1,465,396,942,-400,-668,-974,605,185,-698,-641,14,134,-264,-8,413,-185,-400,100,-339,-1000,597,443,-461,142,20,-979,-1000}));
    }
    public void testDE00038() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{654,-1000,74,-945,1000,242,-394,-1000,-369,-1000,-659,-958,-1000,-1000,-127,-700,1000,-111,146,60,-81,-788,1000,-534,-808,699,-475,-1000,-1000,1000,-1000,-1000,663,-1000,-698,-238,-56,-634,1000,-1000,447,1000,-446,245,-149,475,1000,-1000,1000,1000,716,356,1000,1000,780,-1000,1000,96,-303,1000,1000,913,409,1000}));
    }
    public void testDE00039() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{228,176,999,1000,-731,-505,-743,-27,-276,604,322,858,-586,912,643,-323,-135,-925,544,-1000,-666,-635,-675,1000,-2,169,-822,-680,-137,1000,66,254,123,559,153,1000,569,1000,-34,318,1000,-737,-431,1000,-263,693,-580,5,-470,-433,-275,711,-101,1000,-489,50,-248,-575,-519,543,326,565,80,-513}));
    }
    public void testDE00040() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{211,116,-1000,291,-297,323,-340,404,500,1000,335,-64,988,-4,-679,988,-125,176,-1000,799,-1000,1000,-251,92,574,-928,1000,-1000,1000,-1000,-179,-377,980,261,-265,165,-1000,334,-379,384,773,525,-614,165,499,-1000,-19,-766,-1000,-273,435,-1000,-356,-418,-1000,278,-1000,-304,-424,-327,287,280,1000,-272}));
    }
    public void testDE00041() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-19,-996,-756,-1000,727,627,434,-773,13,-1000,622,305,-319,-1000,335,-73,1000,-818,-963,635,553,-426,-1000,-552,117,-1000,453,-262,-711,1000,-1000,15,-309,-1000,-1000,-1000,874,-1000,1000,-1000,-1000,1000,-239,367,296,-459,555,-969,200,1000,138,-152,305,-400,-86,-940,-735,-1000,370,1000,1000,-132,476,1000}));
    }
    public void testDE00042() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String):void",
            new int[]{-843,477,483,237,-378,418,-552,-797,687,-437,-168,-578,-175,10,695,960,-883,467,806,-274,776,-133,808,-682,229,-466,-424,-63,620,-981,-48,300,-910,305,439,-745,-40,-479,101,360,-989,523,-82,-913,498,746,-857,490,194,567,-154,-793,-168,-880,729,-522,624,-61,583,490,196,-865,273,-186}));
    }
    public void testDE00043() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{336,342,653,-400,1000,488,747,1000,-996,-64,480,786,-474,-1000,7,140,-584,165,-471,-510,563,237,-1000,-953,-515,-322,-1000,498,211,1000,441,279,-617,426,443,331,-533,168,546,309,1000,592,-1000,-508,355,727,-830,996,-1000,-358,893,1000,-1000,-796,-1000,1000,701,543,-75,-946,-342,602,-1000,225}));
    }
    public void testDE00044() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{1000,13,477,-805,1000,356,346,291,-1000,952,161,1000,-262,78,-217,-514,122,-431,-1000,-661,1000,-797,-1000,389,-640,-320,-1000,61,567,1000,556,634,-1000,338,799,-594,-100,-440,268,135,562,308,-525,-526,307,727,-826,698,-886,-191,893,803,-1000,-715,-210,320,-110,543,-496,-1000,-354,602,-1000,-164}));
    }
    public void testDE00045() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{74,401,329,1000,-472,-1000,800,617,321,-813,92,-982,717,829,-775,265,-25,-540,-764,846,-465,-669,827,1000,1000,-1000,1000,-664,3,-1000,365,-1000,-34,275,-1000,-69,-705,-209,875,581,760,-1000,-804,-907,485,455,-506,703,930,-423,610,-1000,1000,544,330,-1000,-804,586,1000,1000,176,246,914,-526}));
    }
    public void testDE00046() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-383,143,619,377,-764,671,887,-1000,-903,-835,126,-378,285,482,-868,1000,173,-728,-91,862,-401,-349,1000,633,-248,-405,433,-449,211,-549,918,1000,514,194,-497,-666,-529,-770,-588,694,531,299,728,307,1000,710,-612,229,-777,-580,-751,-565,-659,1000,197,-1000,-800,1000,487,-137,24,1000,641,1000}));
    }
    public void testDE00047() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-1000,1000,886,1000,-758,504,869,-379,-132,-465,-53,-580,888,121,-1000,393,622,-285,287,279,-144,-66,113,-45,-30,-344,246,56,412,-110,1000,-383,610,1000,-420,367,-649,-549,305,426,773,-353,521,-1000,217,469,-486,110,-185,-550,442,-439,217,1000,161,-627,-232,843,934,-260,-502,667,320,434}));
    }
    public void testDE00048() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-675,693,662,757,1000,-836,378,-400,-378,21,860,398,-454,-193,-802,45,621,-73,-920,-475,564,-609,-1000,-1000,-839,-725,-740,325,604,76,174,-994,-164,310,-165,1000,-446,197,1000,158,-242,1000,-889,-800,290,1000,-984,1000,-1000,-595,828,-1000,-1000,630,-1000,1000,805,707,27,534,-708,735,-173,296}));
    }
    public void testDE00049() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-400,-230,-26,457,430,575,318,1000,617,-1000,806,-6,-1000,-545,126,-72,-973,-641,-1000,-150,87,419,-960,-977,80,-898,-939,-139,70,-17,-344,-371,178,-324,238,232,-927,656,1000,0,1000,-231,-1000,1000,-882,60,-428,1000,-568,-102,305,-40,-132,439,-1000,881,831,-645,-579,-117,905,298,-452,-26}));
    }
    public void testDE00050() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{-925,1000,674,1000,1000,-460,-926,1000,991,-713,1000,-1000,94,-801,-1000,434,849,-148,-1000,1000,-256,127,-240,329,81,-1000,-1000,-1000,-1000,-1000,989,-1000,-149,-208,-1000,1000,-1000,-890,1000,1000,998,310,353,-335,-586,1000,-1000,1000,-676,-1000,1000,-256,-494,-116,-618,165,-402,-627,-457,1,488,115,814,-50}));
    }
    public void testDE00051() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,java.lang.String,org.apache.commons.cli.Options,java.lang.String,boolean):void",
            new int[]{139,-941,69,-217,371,-497,-1000,980,-5,-1000,-443,-116,-801,-813,429,-662,-525,-465,-256,-971,-1000,1000,-176,-1000,1000,-694,83,1000,-491,-80,-791,-89,-219,-609,-674,-609,174,1000,279,-364,555,370,-700,903,168,124,-808,-190,230,438,1000,363,798,-484,-552,584,725,513,0,609,-36,143,-502,369}));
    }
    public void testDE00052() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-612,532,-970,79,158,398,970,-456,-1,-559,-329,424,-41,-173,654,741,-779,-588,820,570,457,413,-1000,-92,45,162,517,-1000,752,307,-1000,-190,-1000,-1000,918,-52,1000,-1000,-871,800,172,-875,-231,-748,253,464,1000,284,-327,-828,679,-857,1000,102,-843,758,-376,238,-258,-790,-3,608,3,1000}));
    }
    public void testDE00053() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{906,-1000,-642,-826,77,-895,-980,1000,-460,-720,-541,-289,139,-1000,620,597,-141,-189,880,-242,-1000,-357,233,1000,152,-1000,-770,-428,1000,-533,430,657,142,-1000,307,1000,1000,-1000,858,1000,80,861,-476,-225,564,-505,330,-771,-780,-527,-212,395,-1000,-204,-1000,-110,459,-344,310,263,-611,626,-97,969}));
    }
    public void testDE00054() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-1000,-1000,249,-318,-26,875,-221,-260,456,116,-969,5,390,515,-300,-961,456,-603,-290,-550,1000,-335,280,-818,-938,-1000,1000,181,-288,-533,56,361,-705,3,306,-2,1000,-1000,-357,409,-209,-414,-634,-856,-663,-998,1000,-773,826,168,519,1000,144,-486,411,556,486,741,276,103,933,861,1000,-260}));
    }
    public void testDE00055() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{-310,-1000,929,670,603,-135,1000,-547,-731,-602,-754,498,1000,498,885,361,-216,401,-1000,94,64,322,297,560,-741,-925,902,324,276,-1000,85,-388,-112,-287,-289,-1000,389,792,-132,426,903,286,398,-1000,370,-1000,957,-1000,639,-1000,952,160,1000,377,-157,908,182,121,805,-461,-38,1000,943,-509}));
    }
    public void testDE00056() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-990,-460,194,840,-173,-842,126,-188,4,-1000,792,-141,-805,904,-59,678,-570,647,-322,-270,-182,753,-728,-892,591,-738,-1,1000,-629,618,1000,-532,-933,273,86,510,-804,1000,-151,826,185,426,-960,-1000,-422,-59,767,-182,-680,-568,-1000,523,-819,-66,-211,-581,-389,461,136,-385,36,918,-430,-96}));
    }
    public void testDE00057() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{573,-224,-877,868,-1000,-638,-217,-541,-767,-575,-51,-699,376,626,1000,-434,79,-1000,418,167,192,1000,1000,977,497,1000,-1000,-333,633,254,-82,-270,1000,-1000,-125,851,-855,531,86,449,-848,-238,-698,120,1000,597,441,-993,198,-1000,-380,1000,124,698,-24,1000,9,241,409,196,-188,417,211,-93}));
    }
    public void testDE00058() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{659,-424,659,-798,-345,-463,780,371,80,-804,506,1000,1000,401,-628,984,-337,-483,-421,-1000,87,-309,1000,-702,498,-503,-201,1000,-547,288,439,-754,-143,403,762,457,-983,804,1000,771,484,1000,-895,1000,-321,-604,729,688,-767,-8,-936,928,-693,-1000,-718,-243,-424,733,-1000,-871,-660,691,-224,883}));
    }
    public void testDE00059() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-446,737,-323,-324,-29,-1000,-721,-467,-381,370,-1000,-386,695,980,723,-1000,159,-876,140,510,848,1000,-889,1000,314,1000,-892,-1000,-12,239,-360,1000,1000,-471,198,-609,184,-1000,-133,-1000,-1000,-608,1000,-361,-400,394,600,-540,-290,-1000,368,134,505,1000,1000,82,57,34,1000,598,1000,-181,241,-333}));
    }
    public void testDE00060() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{1000,612,894,-792,-405,60,1000,1000,898,-1000,44,399,1000,29,-527,81,50,205,1000,-1000,-254,-512,1000,-732,3,-761,-149,714,273,-1000,4,-763,353,-699,1000,987,-892,529,-1000,960,170,1000,-636,-1000,369,-687,472,576,-242,1000,-483,944,-1000,-135,-231,109,-93,-416,-347,-1000,-592,1000,295,23}));
    }
    public void testDE00061() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{-915,-369,-382,-134,-186,-534,343,-448,-147,-725,481,409,-1000,158,-308,-711,-887,754,290,-35,674,-876,-1000,-230,356,-496,885,533,354,-1000,531,-358,166,-83,599,283,-415,231,-739,1000,523,532,-788,-90,-697,-545,831,757,-1000,302,-1000,134,-297,-93,-154,-187,-516,370,171,-244,-105,1000,556,1000}));
    }
    public void testDE00062() {
        assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printHelp(java.lang.String,org.apache.commons.cli.Options,boolean):void",
            new int[]{44,454,-671,-823,-626,-833,-704,445,-1000,425,-51,464,875,845,234,383,59,-867,-502,225,989,796,-54,78,132,75,-965,32,965,0,-360,807,200,94,-366,-617,196,35,-352,-661,-267,-35,646,256,-890,-9,667,-98,-750,-983,1,19,590,262,886,-789,-14,656,20,582,510,-181,433,371}));
    }
    public void testDE00063() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{70,934,54,561,-293,-413,-546,-466,363,552,547,-358,-113,456,-239,-299,-472,-412,-258,382,494,-429,-700,-430,-622,981,-390,-55,-28,273,1000,465,-158,108,501,1000,-998,-748,349,-694,-735,187,-609,0,225,73,137,567,577,120,264,227,-50,-671,199,743,-409,28,-180,-279,-40,-24,674,1000}));
    }
    public void testDE00064() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{586,-1000,-161,759,-557,-1000,634,-700,-440,169,216,59,-264,1000,-709,-919,-1000,301,494,775,-10,-1000,-996,1000,343,864,-645,199,557,971,-179,345,-255,1000,467,29,-10,862,516,-1000,589,1000,-1000,-1000,-251,-1000,-511,802,-1000,-185,-882,498,1000,-1000,-637,1000,1000,-923,713,-854,168,-54,1000,-569}));
    }
    public void testDE00065() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{1000,1000,-391,1000,752,-1000,158,-245,-763,558,270,-90,-348,1000,-130,-497,-619,-118,946,1000,1000,-1000,-717,551,-156,1000,382,1000,937,216,-134,896,-290,-1000,1000,1000,-1000,-321,1000,-525,260,1000,-1000,-576,195,-225,-1000,1000,-193,-1000,-349,563,1000,-817,-56,1000,37,223,1000,488,-755,-1000,1000,-197}));
    }
    public void testDE00066() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printOptions(java.io.PrintWriter,int,org.apache.commons.cli.Options,int,int):void",
            new int[]{-223,612,269,1000,1000,-907,695,-1000,76,-1000,400,347,1000,655,-1000,-551,-866,1000,-374,399,-383,-569,-292,913,-99,40,-933,508,640,-354,-977,-284,259,-200,1000,29,-10,862,-119,-383,1000,1000,-1000,-1000,-653,-1000,619,946,859,425,-1000,1000,400,-669,-650,1000,808,671,432,-784,501,43,-315,-1000}));
    }
    public void testDE00067() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{224,1000,-952,-18,-418,1000,-200,-204,-861,-554,902,619,982,-1000,-1000,918,913,498,248,499,626,-139,-1000,-1000,-598,-1000,284,-92,844,-821,-238,-68,-456,-628,536,527,-132,-1000,290,-625,-1000,782,410,428,-205,-695,-540,-765,-1000,-308,809,166,605,-112,-286,6,547,-573,751,211,445,171,955,830}));
    }
    public void testDE00068() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{1000,1000,-247,185,1000,-216,1000,882,-23,1000,-130,-850,1000,838,-1000,-1000,1000,-108,-191,1000,-1000,1000,-53,-1000,-1000,-146,593,212,149,629,-1000,471,502,1000,710,426,344,1000,1000,-732,-249,-1000,118,-1000,-266,534,-1000,-740,-1000,-306,-892,1000,1000,803,-202,-1000,-1000,591,-903,-1000,-1000,318,1000,1000}));
    }
    public void testDE00069() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-1000,742,-296,-380,1000,1000,-998,1000,-1000,1000,-1000,304,30,-1000,831,1000,428,-1000,340,-630,860,-1000,-1000,1000,-807,559,226,632,-510,-918,992,-265,76,-1000,460,1000,-103,-1000,-380,259,-1000,-935,-853,915,-1000,35,224,500,823,621,-460,-1000,400,982,-665,89,1,-385,169,-368,454,-1000,729,754}));
    }
    public void testDE00070() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{237,1000,-371,677,32,1000,-267,-83,-611,-533,511,-12,-512,-668,-575,1000,567,-919,732,-1000,388,30,-1000,981,-778,720,333,619,-330,628,1000,-171,418,61,893,1000,666,-965,162,177,-845,546,-415,835,-151,135,295,445,-442,1000,292,410,1000,872,-1000,-433,-356,-125,-617,371,496,49,955,830}));
    }
    public void testDE00071() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{1000,-956,44,1000,-932,803,-829,-77,-523,-334,-181,238,890,-437,450,574,-71,-1000,-445,285,-242,1000,-380,1000,-247,-1000,-1000,917,695,868,181,1000,455,1000,398,-1000,-383,471,-956,1000,226,-1000,-765,-887,55,-1000,-1000,1000,43,1000,135,-230,-1000,467,1000,-286,1000,-919,-395,748,-660,410,1000,-193}));
    }
    public void testDE00072() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{284,-948,-971,254,-1000,425,-138,586,189,-912,381,-1000,385,-1000,-211,68,263,-1000,615,591,-270,-1000,511,-901,-240,-549,-1000,-1000,848,271,460,736,-3,228,1000,261,738,182,367,1000,1000,521,-999,31,-1000,666,1000,737,69,143,883,-971,-327,666,-1000,456,193,-120,113,1000,216,-663,899,-1000}));
    }
    public void testDE00073() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printUsage(java.io.PrintWriter,int,java.lang.String,org.apache.commons.cli.Options):void",
            new int[]{1000,-147,222,-673,-1000,362,797,-1000,54,-1000,335,-256,162,1000,-716,-824,-322,-450,844,990,-710,-865,695,-1000,-201,-796,130,860,393,1000,-1000,1000,70,611,1000,-826,-995,-687,916,1000,192,-671,-845,321,-1000,372,723,268,660,-1000,1000,36,-631,192,947,-218,956,856,-580,-1000,-1000,-844,-563,1000}));
    }
    public void testDE00074() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-754,66,417,535,-1000,255,-386,-626,-775,-837,530,714,706,-1000,-129,-519,-251,576,606,-9,600,538,1000,-468,-188,907,-28,965,-1000,1000,-674,351,1000,-778,209,-1000,-636,93,314,154,-9,266,749,921,129,135,201,908,160,70,695,927,-137,-14,-434,-1000,854,-241,73,350,-1000,1000,324,195}));
    }
    public void testDE00075() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-587,686,223,988,-1000,1000,-1000,241,-1000,-1000,83,596,-993,-86,-198,642,-778,585,-955,-777,47,507,-1000,-1000,1000,1000,-1000,1000,125,-364,729,-1000,122,1000,1000,-914,-102,453,782,-1000,-92,-897,992,-352,-1000,-899,-334,440,162,381,871,288,-740,-443,-1000,-1000,776,711,-720,-280,694,405,1000,-1000}));
    }
    public void testDE00076() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-650,234,-1000,1000,-556,152,-445,-770,54,-578,-1000,1000,-112,-650,-64,1000,528,670,313,1000,596,-686,354,72,74,-128,-485,1000,1000,198,967,234,659,-375,1000,-1000,-1000,13,727,523,-1000,-102,50,-65,-878,-555,333,371,691,-934,1000,-878,1000,722,-1000,-1000,320,-191,1000,651,-968,-509,524,-990}));
    }
    public void testDE00077() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-732,-374,114,547,-575,-1000,363,-1000,-772,471,26,1000,509,-1000,-104,-762,-3,765,756,-216,889,1000,38,536,-47,174,-387,269,-249,-254,-681,487,1000,-1000,1000,-846,-449,-85,319,490,-831,-72,958,923,106,795,465,143,-87,1000,515,-13,38,-163,-406,-32,9,-456,1000,-867,-314,421,-119,625}));
    }
    public void testDE00078() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{1000,-3,-429,-543,196,-52,489,120,-1000,-3,1000,1000,292,-371,99,-388,-590,1000,462,877,511,1000,-394,260,-556,-209,740,252,-1000,-192,-1000,-566,693,-843,159,-946,-735,-591,215,24,-640,1000,713,407,1000,-615,642,-909,-48,1000,-113,289,-612,-706,457,815,1000,-173,1000,-1000,303,603,727,-970}));
    }
    public void testDE00079() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-468,264,66,-810,-1000,667,-456,-685,37,-638,420,159,-993,196,57,401,-770,474,-536,-1000,-153,-390,-359,-647,1000,1000,-712,27,-233,-671,231,-852,1000,1000,493,-441,479,140,50,-1000,327,-116,632,203,-817,-641,-563,93,452,668,247,175,1000,53,-1000,-1000,293,1000,-1000,-415,14,460,1000,-267}));
    }
    public void testDE00080() {
        assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,int,java.lang.String):void",
            new int[]{-872,182,-826,266,398,620,125,417,-631,957,-149,407,-406,-697,539,521,-263,-609,-1,926,287,315,-315,557,-866,248,217,-967,-541,-723,788,-586,-996,614,-875,545,-850,-192,-346,101,-569,329,-562,-986,137,887,59,926,457,-778,-855,555,266,720,-504,389,104,189,898,38,150,-592,-396,712}));
    }
    public void testDE00081() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-724,-401,-1000,-36,621,978,1000,872,1000,447,1000,-410,-460,578,-917,817,-320,645,-340,-1000,-192,497,26,-184,81,500,-284,52,1000,1000,-1000,-15,-585,-754,740,457,250,598,-557,-583,-210,709,222,-125,-227,483,-1000,-906,990,182,280,-31,-398,-1000,-1000,-367,-950,90,138,-12,1000,-348,-139,466}));
    }
    public void testDE00082() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{1000,-783,-826,238,69,674,-804,-820,-14,-326,-650,-1000,-237,619,880,-227,-36,-854,-1000,-270,596,-150,-1000,-607,-810,438,161,1000,194,-808,409,-995,278,-595,-617,-994,161,-180,1000,1000,429,-507,-813,102,392,-813,-10,-711,-960,626,510,189,564,552,449,905,771,647,302,-253,1000,-690,-450,-736}));
    }
    public void testDE00083() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{1000,-597,-108,1000,311,578,-1000,-1000,233,-229,-1000,-544,-502,12,680,60,-21,-1000,-793,-53,596,-377,-996,-547,856,949,-136,735,467,-687,164,-568,161,-240,-1000,-806,-285,-325,711,992,620,-1000,14,-953,229,-596,-421,-855,-377,672,184,92,-430,76,272,948,705,1000,1000,61,555,-93,-128,-946}));
    }
    public void testDE00084() {
        assertEquals("VOID", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{-1000,-786,-712,-311,1000,1000,557,574,484,1000,-483,-165,331,1000,377,449,-443,-674,1000,-329,68,984,626,-196,1000,459,980,-943,859,1000,-1000,615,678,69,-59,377,590,631,-118,-1000,-71,-1000,1000,248,-609,854,-753,-801,784,454,1000,284,-1000,-1000,-993,-1000,-966,493,841,-112,-298,-156,80,-891}));
    }
    public void testDE00085() {
        assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{646,688,-118,-757,17,-917,128,-108,853,238,-85,-397,955,151,1000,1000,-102,-592,434,-321,-1000,44,-437,-1000,-452,-223,-484,-188,348,-297,766,-303,-755,96,-146,255,-533,-340,1000,1000,192,-949,-228,-64,-415,126,-410,857,103,402,707,1000,1000,696,137,302,-66,-1000,-167,483,447,-1000,-87,1}));
    }
    public void testDE00086() {
        assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "printWrapped(java.io.PrintWriter,int,java.lang.String):void",
            new int[]{719,735,-653,78,-64,470,-54,535,457,498,164,-192,537,162,190,-187,148,-176,302,71,488,-44,-399,449,-778,138,5,-103,-96,-1000,1000,137,-592,185,75,145,58,-41,-131,434,578,-553,1000,-76,27,-520,243,-466,-438,-171,910,230,1000,-290,400,-122,555,240,525,226,-283,712,-579,195}));
    }
    public void testDE00087() {
        assertEquals("VOID|getArgName=java.lang.String:LTB4ODAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setArgName(java.lang.String):void",
            new int[]{970,-248,-21,187,210,-965,726,-815,-755,-57,301,-918,988,-267,586,202,319,-728,5,-255,432,-837,-486,597,808,-53,359,-346,-648,-461,-663,444,105,530,-602,883,-150,950,-338,-546,-61,690,-811,264,-308,-112,-853,-961,852,-740,443,370,738,-356,322,-708,983,811,-33,-371,-954,-17,-309,-556}));
    }
    public void testDE00088() {
        assertEquals("VOID|getArgName=java.lang.String:MHg4MDAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setArgName(java.lang.String):void",
            new int[]{1000,401,-641,193,687,-940,430,-846,315,-1000,-948,25,516,688,1000,-417,-1000,-728,-1000,850,468,115,-302,1000,683,-711,-1000,438,-726,362,-1000,-241,-878,1000,103,1000,-198,-379,723,848,-74,443,-112,1000,-380,257,-864,-375,915,-539,-370,-310,10,65,96,-1000,849,1000,678,-155,-1000,-986,17,-416}));
    }
    public void testDE00089() {
        assertEquals("VOID|getDescPadding=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setDescPadding(int):void",
            new int[]{135,-573,-991,-58,650,-460,-274,479,639,487,-24,11,892,-997,645,-683,322,-447,-91,-635,280,-686,-266,502,-670,525,-97,-582,325,-94,740,-336,-725,-342,-355,-205,266,816,576,59,467,-345,-305,-52,133,-766,542,911,422,-775,286,-946,858,-325,897,-252,285,539,-601,-258,299,459,-247,551}));
    }
    public void testDE00090() {
        assertEquals("VOID|getDescPadding=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setDescPadding(int):void",
            new int[]{-611,1000,-421,194,-24,-1000,219,-1000,518,816,-892,308,301,-1000,751,-1000,1000,-1000,207,-271,696,-904,558,-751,-1000,1000,1000,1000,-1000,-471,-32,1000,-567,648,-1000,-926,938,1000,-736,730,-1000,-964,1000,1000,-1000,-81,730,-4,-5,361,-740,-583,457,315,71,560,-1000,37,1000,-790,-14,713,-473,-211}));
    }
    public void testDE00091() {
        assertEquals("VOID|getLeftPadding=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLeftPadding(int):void",
            new int[]{273,252,284,-440,-772,-1000,8,-594,-113,-738,1000,445,1000,343,-642,6,-724,-206,160,-799,1000,-285,349,749,714,-1000,-1000,470,-209,-569,-62,593,988,44,108,-736,123,738,420,-809,-164,-104,-806,584,-712,-562,-1000,-1000,-1000,-1000,-817,-1000,-591,518,-201,281,-671,-841,438,58,141,780,-311,71}));
    }
    public void testDE00092() {
        assertEquals("VOID|getLeftPadding=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLeftPadding(int):void",
            new int[]{-112,769,-11,-1000,-1000,535,-277,824,497,1000,1000,35,-188,-1000,1000,-170,31,1000,352,486,850,619,1000,1000,150,-1000,-877,1000,1000,173,-803,-1000,-1000,-132,-1000,536,-1000,126,-1000,-464,-1000,-391,1000,-1000,665,-1000,-1000,402,732,1000,-129,831,1000,-486,584,-187,-1000,-329,370,1000,-1000,575,-1000,86}));
    }
    public void testDE00093() {
        assertEquals("VOID|getLongOptPrefix=java.lang.String:KzU0M0Q=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLongOptPrefix(java.lang.String):void",
            new int[]{-619,101,679,790,86,893,-171,757,-628,332,-424,32,-563,856,-971,-611,-56,-654,-126,209,-416,217,543,794,717,203,83,857,-723,-31,-571,-156,6,-308,537,-108,-800,529,-96,-812,-371,-487,347,203,-927,-206,-401,-77,991,-573,306,-490,-759,-99,-739,-931,501,473,379,-799,40,55,-172,822}));
    }
    public void testDE00094() {
        assertEquals("VOID|getLongOptPrefix=java.lang.String:bTZlNFRieEpsUWlXNV93QWR4ZC8=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setLongOptPrefix(java.lang.String):void",
            new int[]{-202,474,-131,-15,71,-400,389,-476,849,400,-289,-1000,272,-1000,767,19,-1000,465,49,-6,-1000,878,-528,-107,963,-400,891,-63,120,69,-1000,-945,-400,-35,270,7,95,803,1000,-57,-706,-300,-983,-38,400,-334,194,18,-297,431,-1000,32,36,-768,317,368,706,252,863,-242,1,223,-345,1000}));
    }
    public void testDE00095() {
        assertEquals("VOID|getNewLine=java.lang.String:", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setNewLine(java.lang.String):void",
            new int[]{-341,672,979,725,546,-120,119,874,-496,807,112,813,373,350,70,163,601,324,629,630,412,-945,-211,-152,700,-716,249,-971,683,-263,-241,797,426,-502,564,-885,952,130,-799,-182,-127,411,-367,-37,-806,472,957,909,296,179,-190,-128,940,649,965,-461,605,-51,-382,-735,-535,236,-633,339}));
    }
    public void testDE00096() {
        assertEquals("VOID|getNewLine=java.lang.String:MHg4NA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setNewLine(java.lang.String):void",
            new int[]{-537,-528,-626,233,-472,-187,531,176,703,455,934,275,401,107,-329,895,-834,423,132,-204,918,932,-9,-879,-923,534,154,-28,708,483,218,-477,811,-464,625,-871,576,-199,800,512,575,-302,330,799,619,387,492,-116,332,65,296,-574,367,589,631,-379,-369,-966,38,-152,872,-996,197,221}));
    }
    public void testDE00097() {
        assertEquals("VOID|getOptPrefix=java.lang.String:YWFhYQ==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptPrefix(java.lang.String):void",
            new int[]{-306,-552,354,882,-631,-36,-287,668,-332,280,481,-922,-329,-314,151,-434,585,428,-35,-292,629,994,-782,757,807,474,-349,-944,-40,69,-288,873,-267,-632,916,159,-765,-115,799,-958,-936,531,-960,-573,-904,-810,-821,986,-1,-624,106,46,760,-947,954,-630,-301,168,394,721,412,138,427,191}));
    }
    public void testDE00098() {
        assertEquals("VOID|getOptPrefix=java.lang.String:NERabSBxUy4=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setOptPrefix(java.lang.String):void",
            new int[]{-20,-186,424,347,540,-520,400,-677,364,-279,-378,-5,274,-313,790,230,127,668,238,334,270,-217,-342,785,607,203,22,-999,-400,-443,-788,449,978,-447,964,457,719,429,-47,1000,-326,422,-968,225,-627,-179,40,170,91,468,585,43,-206,-696,329,-867,-682,-136,-103,650,-589,289,291,-257}));
    }
    public void testDE00099() {
        assertEquals("VOID|getSyntaxPrefix=java.lang.String:ICsxMzkg", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setSyntaxPrefix(java.lang.String):void",
            new int[]{-742,-732,-701,752,-558,-154,-349,827,288,-497,-464,-873,256,-362,-844,817,433,529,938,139,294,430,367,-896,457,236,254,-951,938,253,63,-811,-445,-729,143,-844,-126,787,415,-496,-650,960,431,-866,179,-309,553,-272,-223,79,474,374,773,407,-720,794,-64,848,832,858,-442,61,-625,-628}));
    }
    public void testDE00100() {
        assertEquals("VOID|getSyntaxPrefix=java.lang.String:MTAwMA==", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setSyntaxPrefix(java.lang.String):void",
            new int[]{-476,418,-596,-591,66,-438,-419,507,184,685,708,90,-41,-1000,686,134,1000,958,838,-460,-1000,-1000,-171,-229,68,-1000,530,1000,1000,578,841,1000,443,-1000,74,-233,874,-313,-1000,-653,99,919,545,798,970,1000,1000,637,-1000,990,-831,-289,1000,-1000,1000,-253,-28,1000,375,1000,847,340,138,374}));
    }
    public void testDE00101() {
        assertEquals("VOID|getWidth=java.lang.Integer:Mzc2", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setWidth(int):void",
            new int[]{192,-925,-551,-566,-274,-689,-411,203,-957,-978,-649,481,218,-229,798,-608,-621,79,376,-409,570,325,231,469,902,-91,312,254,79,636,839,46,141,-91,-730,-492,100,-572,-496,90,-956,-148,-270,-574,232,-853,-920,-137,-291,192,37,940,87,-375,-607,106,-760,868,-488,-71,880,-161,-399,-351}));
    }
    public void testDE00102() {
        assertEquals("VOID|getWidth=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.cli.HelpFormatter", "org.apache.commons.cli.HelpFormatter", "setWidth(int):void",
            new int[]{252,387,894,164,1000,-778,296,241,19,-295,-71,-689,172,-350,188,-90,-1000,-226,-174,-767,-697,714,-381,260,340,28,-819,-379,107,1000,-295,22,216,416,-508,-788,-59,-978,623,905,-323,156,410,-119,-110,-278,379,-222,554,181,519,574,500,474,-905,1000,-1000,-437,-407,-758,429,991,-800,927}));
    }
}
