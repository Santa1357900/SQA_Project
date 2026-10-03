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

public class DEGeneratedTest {
    @org.junit.Test(timeout=60000L)
    public void testDE00000() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "addAuthor(java.lang.String):boolean",
            new int[]{-977,-702,850,-374,-575,-348,835,844,916,-53,112,243,-890,30,-752,-232,-78,549,172,-951,-43,490,523,-653,266,-320,516,-847,-893,503,126,-83,-799,114,398,-161,-487,832,411,-554,-951,34,-998,760,360,-80,-238,254,-487,-237,984,518,-895,941,-725,580,-394,-75,356,160,-246,678,-789,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "addReference(java.lang.String):boolean",
            new int[]{-247,-92,255,-863,-256,226,-600,-952,-72,-719,-430,668,659,-332,-609,749,500,493,153,-558,64,527,966,-597,-372,-10,-932,863,-19,359,236,919,211,176,-753,-172,387,-103,375,-738,-597,485,-985,-878,691,676,-275,800,915,635,-827,-428,-697,-955,826,-812,-140,608,-484,-342,-686,-617,515,752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.JSDocInfo", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "build(java.lang.String):com.google.javascript.rhino.JSDocInfo",
            new int[]{461,-300,967,587,701,-5,360,78,12,-942,196,-302,-526,-207,-356,478,755,661,107,742,546,479,387,-245,566,754,747,891,-129,702,137,965,204,531,960,76,-269,182,-659,188,-323,-725,74,542,66,-281,164,-44,-897,-844,-797,86,-481,-765,688,-193,659,-459,-162,1000,-956,-37,452,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "build(java.lang.String):com.google.javascript.rhino.JSDocInfo",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "hasParameter(java.lang.String):boolean",
            new int[]{-178,-933,-489,913,-63,291,-546,623,138,-768,170,852,490,193,188,257,460,918,526,-780,-148,-603,-891,-886,731,-57,-393,534,579,-72,-31,-512,683,-854,-598,146,-417,937,-781,-57,-687,-91,612,-425,112,-597,-841,758,-698,670,-325,477,112,-846,-130,-87,378,-770,-799,-794,-774,-706,309,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "isConstructorRecorded():boolean",
            new int[]{372,595,-233,-186,-355,-811,672,14,996,331,525,-877,294,-151,-905,-457,-261,-871,673,446,-585,-417,628,637,176,-429,720,447,-840,-644,439,727,941,542,203,964,-627,100,232,405,-527,-439,110,-309,-610,-735,647,-51,-568,-366,-514,-447,435,377,-890,-153,-708,234,378,-16,-570,454,539,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "isDescriptionRecorded():boolean",
            new int[]{67,49,311,893,-117,-243,-96,-537,533,-170,-715,791,423,-955,-332,536,-621,-104,344,-46,966,-316,975,-888,996,-46,825,795,-952,-303,-476,943,-228,-361,-994,-307,-659,-72,779,-649,-559,-412,838,-40,901,-787,231,-483,-745,547,511,342,-670,-158,147,-367,-716,167,328,813,431,-781,543,906}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "isInterfaceRecorded():boolean",
            new int[]{875,300,-521,48,757,474,-337,467,730,616,-367,-7,-417,-884,401,-502,28,711,649,421,281,657,391,-860,533,-403,-499,-783,812,941,481,121,629,-521,317,-161,479,-839,690,907,813,-488,937,823,-622,-48,131,556,987,-503,-521,904,717,-267,-206,-156,288,-243,-758,684,-40,841,-205,824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "isPopulated():boolean",
            new int[]{463,-728,291,-252,-509,195,-518,-100,-96,273,241,-94,-212,146,-878,850,549,-811,62,-877,-750,503,-951,-987,674,-835,551,487,259,523,866,-767,447,-610,916,281,723,-188,-441,474,-380,778,496,919,-691,-298,-976,176,63,808,-489,385,824,-357,273,-215,-616,-130,-234,172,-708,214,477,701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "isPopulatedWithFileOverview():boolean",
            new int[]{-997,-947,818,-457,-863,-996,-202,453,945,126,982,873,973,-826,778,553,-961,-628,706,913,360,276,-308,730,672,-366,-941,405,757,-46,-606,939,-586,533,516,-603,-116,-516,123,-848,-84,-19,-686,468,282,325,162,-926,370,766,-195,-656,698,-969,-932,358,672,-584,-422,-262,-293,-994,-493,-579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "isPopulatedWithFileOverview():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "markAnnotation(java.lang.String,int,int):void",
            new int[]{-456,-16,-860,-966,716,-400,215,256,-166,-337,476,-450,-124,-941,-873,176,-464,667,-152,-943,398,-815,101,-817,-904,724,-786,-828,66,-849,-122,-93,163,-276,-185,381,-566,-39,749,-67,626,867,533,-85,-377,670,-669,487,-34,22,41,-280,820,263,862,478,-676,799,-193,-351,-855,-100,-712,227}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "markAnnotation(java.lang.String,int,int):void",
            new int[]{792,-109,-857,-522,-895,-970,590,-133,801,-254,-470,-531,-468,603,-793,-653,-560,-25,-455,369,-272,-678,250,108,919,470,-295,-59,40,507,-72,903,-365,-543,-33,-950,503,-699,494,143,388,99,-922,-695,432,-23,645,-10,157,570,968,55,-17,146,672,-962,-159,-925,-768,-157,-692,585,663,-63}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "markName(java.lang.String,int,int):void",
            new int[]{985,39,494,-551,-220,-533,-644,441,960,-281,-57,-429,-232,522,572,74,237,-270,-232,-438,-470,-305,-182,892,137,197,-514,-90,130,-794,983,-97,521,-966,534,494,966,-488,507,-550,540,-296,-82,-590,-875,-646,-592,-97,-933,811,237,182,191,-558,752,368,-2,-258,-130,-764,946,-761,-370,502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "markText(java.lang.String,int,int,int,int):void",
            new int[]{-52,228,-460,-962,-462,874,-686,271,-737,-729,-494,842,-73,-414,720,-301,-557,365,-812,223,639,-800,336,237,794,399,958,-707,-973,-837,-508,356,285,99,-397,795,108,-381,-111,-325,543,742,-114,734,646,-856,-246,-851,863,896,35,-764,-774,-711,710,-465,812,366,-627,-595,171,346,855,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "markTypeNode(com.google.javascript.rhino.Node,int,int,int,boolean):void",
            new int[]{-166,-691,52,82,923,-756,894,-990,427,-948,599,374,-596,-471,89,-351,925,-840,-960,-278,370,54,-911,-897,825,819,614,-163,341,-984,-29,-634,-863,876,-151,-567,-292,-987,-115,951,-780,24,-419,710,-394,-917,-842,-252,307,-124,35,308,-242,-422,-698,358,400,-47,-222,-123,229,-871,-999,-697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordBaseType(com.google.javascript.rhino.JSTypeExpression):boolean",
            new int[]{-875,-211,-721,203,-592,608,954,136,-21,-280,43,-25,484,-250,-127,-352,-422,186,147,801,-556,175,375,-227,678,-452,245,-348,-879,983,603,-469,459,846,330,-639,-50,681,403,-67,-467,-394,673,-613,744,-79,-507,441,-656,127,-766,-2,400,-736,-74,-784,650,-38,810,-769,437,944,-616,-251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordBlockDescription(java.lang.String):boolean",
            new int[]{-310,-942,906,157,873,229,-335,345,-642,-926,-428,-702,994,-304,904,-928,216,116,-459,-246,436,444,-281,-581,378,698,416,855,679,-882,271,785,214,622,239,-451,-484,-705,-782,-172,-630,241,-203,-124,233,980,947,-61,451,663,16,79,441,816,-321,-569,-36,74,656,-880,410,-580,-64,-538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordBlockDescription(java.lang.String):boolean",
            new int[]{782,816,-961,-688,793,511,-313,-68,292,-296,884,117,600,94,259,-362,-463,-742,813,137,-123,765,270,-175,128,998,350,155,302,310,214,-3,436,997,558,-903,-460,777,941,-790,-626,-75,769,193,-993,-942,119,-460,462,652,17,-607,896,813,-807,886,784,592,-608,-182,327,143,325,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordConstancy():boolean",
            new int[]{810,-883,618,-573,-204,-341,-439,174,-87,-109,466,973,757,590,-637,-998,395,-243,921,193,-107,-567,807,-500,504,-130,403,561,-583,678,-384,-965,-629,141,494,239,328,-440,-212,-835,-41,-845,648,-582,-289,-146,242,-837,883,-901,-773,811,318,733,747,809,53,927,901,658,-372,194,-544,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordConstructor():boolean",
            new int[]{942,-386,-660,732,-306,50,924,905,-969,99,-185,-258,203,997,-279,85,-139,887,-118,-547,-165,394,-379,479,775,-992,-365,-759,-158,119,-969,889,-705,258,66,-782,563,-537,-429,694,-802,510,-874,-131,-62,-757,-829,48,71,183,-513,649,-749,-774,-311,767,963,294,770,-889,981,-444,64,-279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordDefineType(com.google.javascript.rhino.JSTypeExpression):boolean",
            new int[]{953,-705,474,-336,-282,853,974,982,83,194,-977,763,-73,-470,-469,-596,-809,180,535,58,567,-64,-470,-977,714,-477,53,375,107,-638,-203,-431,-185,-577,-298,-352,204,-343,-785,655,-132,211,250,-171,901,-553,-776,-703,-920,-368,-978,-931,442,-912,704,380,-671,-388,372,-329,-977,959,-292,845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordDeprecated():boolean",
            new int[]{732,-335,174,-371,945,-140,-962,-688,95,-43,-659,530,258,-594,-697,-591,-142,895,-508,722,593,238,-570,859,-525,873,873,-526,492,398,-800,-206,654,728,-400,30,-76,696,-443,867,-81,215,-492,562,143,490,-506,-151,-984,-821,-881,-461,-36,901,-791,466,460,38,169,285,43,360,243,485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordDeprecationReason(java.lang.String):boolean",
            new int[]{-964,-564,-120,-447,37,803,72,532,973,-612,-852,-794,833,696,-220,661,324,-179,-704,-563,-400,-942,391,-592,-978,-90,666,307,641,-505,-21,850,-974,261,538,-396,-385,-64,286,42,856,-349,-789,-472,-938,320,926,-623,129,506,119,607,36,-733,272,-515,421,742,-570,582,400,-371,753,676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordDescription(java.lang.String):boolean",
            new int[]{736,286,551,-457,833,-15,992,-370,853,714,-320,124,-134,-896,381,883,-337,-315,503,976,2,244,884,570,-931,-760,133,280,450,530,-459,465,472,928,-321,-963,-75,434,-554,-572,-170,-608,558,566,208,-748,-750,-68,-411,176,319,-950,-614,-751,516,168,-790,-232,807,-727,694,721,10,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordDescription(java.lang.String):boolean",
            new int[]{-969,725,-424,417,-992,-817,435,468,435,-484,819,-673,-785,-744,-866,195,284,9,-492,267,-931,-76,-895,-222,-164,962,-31,800,-726,-201,-559,828,259,-570,348,285,852,889,-214,163,-725,895,-453,200,865,-324,28,-768,-179,-251,807,-355,931,-993,968,-768,410,134,742,165,481,-986,-950,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordEnumParameterType(com.google.javascript.rhino.JSTypeExpression):boolean",
            new int[]{-185,722,-236,654,-977,776,140,-511,452,61,741,818,-619,335,-760,-469,468,-700,278,993,-45,900,15,288,317,-195,-998,678,518,356,281,729,467,-853,-915,-289,187,302,-495,-897,805,-471,47,267,-482,-952,147,707,374,632,-625,-282,916,152,348,690,-469,261,-856,-173,-320,150,-580,-345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordExport():boolean",
            new int[]{962,66,279,-861,391,452,724,749,766,875,-416,-963,363,25,-367,-558,152,-471,-257,-680,42,979,-301,237,-559,-272,154,205,299,-328,118,-701,784,789,-323,161,804,328,915,-427,701,-741,-526,487,-408,-194,576,-298,106,-275,886,708,-481,371,-828,120,-292,514,351,-372,-299,382,-791,-621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordFileOverview(java.lang.String):boolean",
            new int[]{-406,-191,-428,584,183,-709,-223,301,647,18,-139,-861,781,531,-530,115,746,74,113,57,468,950,-422,-262,604,211,271,-762,990,460,640,973,849,-629,-743,-41,26,487,-853,102,43,-173,-496,563,-128,-122,-915,-435,-25,673,232,440,-628,813,753,111,312,-452,679,-213,-578,314,322,-828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordHiddenness():boolean",
            new int[]{978,550,-427,-566,-949,725,-917,141,-967,312,-626,-637,-221,210,-764,146,-144,144,351,227,-132,-750,138,-75,266,-423,-968,822,969,-583,969,-643,245,-271,-314,-71,-574,-60,384,82,-761,913,302,-64,806,969,767,-875,-317,-474,-582,-785,725,107,482,-181,992,293,-814,439,-551,507,-269,-552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordImplementedInterface(com.google.javascript.rhino.JSTypeExpression):boolean",
            new int[]{554,-661,405,912,623,406,-439,-630,-394,258,215,429,396,-917,432,-232,-948,962,-340,-208,853,803,261,303,9,-561,-133,695,-246,-118,846,-654,-602,-544,819,613,930,823,901,188,-303,-248,162,-602,-138,892,-362,529,-600,780,-838,205,-350,-145,-734,453,692,327,456,-802,136,433,-725,-395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordImplicitCast():boolean",
            new int[]{-318,717,-591,499,-423,-181,157,602,-427,-661,-374,-520,325,-379,746,791,-432,-532,869,-136,-284,957,-433,-857,928,930,-597,761,-551,590,986,-315,465,289,-848,-975,898,-402,530,-581,662,-932,-75,-802,711,-328,-3,-998,899,909,-526,-121,143,732,-153,-436,-336,68,883,-442,-783,-463,-422,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordInterface():boolean",
            new int[]{-886,62,12,-802,-278,-189,526,255,-966,-830,535,-576,935,149,-94,492,715,798,832,-660,-387,-238,-197,820,-592,796,-420,784,31,-339,-627,-597,-576,-826,535,384,-605,341,-482,-957,18,483,-432,319,346,-921,-273,-257,584,610,30,179,730,-857,-485,666,700,-390,620,-859,-577,-236,-123,-39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordNoAlias():boolean",
            new int[]{-815,177,-981,-946,-899,-107,920,-269,-113,73,190,158,-830,170,-635,97,623,334,-901,456,-147,744,151,299,812,508,-879,-38,340,-150,907,-320,394,-407,-466,-524,-468,789,864,-96,-687,769,-491,725,-597,-176,221,-463,-610,-701,-989,-995,-421,956,-701,-587,-644,-876,-504,999,131,-619,625,-280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordNoShadow():boolean",
            new int[]{-56,98,843,-743,11,-173,-674,-945,-436,765,-514,-896,389,-4,-338,-889,-425,139,-414,-657,217,-185,619,-20,-942,572,-536,809,-878,-548,-535,-46,-643,-566,511,669,-415,-44,687,-767,-358,406,369,915,488,-970,218,647,277,67,680,563,-78,-930,4,-114,503,358,567,-224,-975,-659,-688,701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordNoSideEffects():boolean",
            new int[]{368,-330,57,842,-208,859,398,624,759,727,715,906,-579,-19,-514,-235,735,510,202,375,-74,849,-764,-78,-395,-22,-97,637,995,418,-591,412,282,190,261,575,693,-605,573,-837,-402,931,673,-725,-343,185,418,-39,-160,-608,725,-494,3,815,-44,-76,-373,888,994,-967,-672,-959,130,550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordNoTypeCheck():boolean",
            new int[]{894,-441,-124,949,-664,-414,399,-47,648,-915,-931,-846,-274,780,52,-61,887,-696,-405,24,145,-492,693,-563,909,-943,453,-273,973,791,-79,-904,-874,1,193,634,-177,-410,-791,364,-70,-139,808,449,800,-380,-684,552,-359,-812,-745,-892,489,-16,564,42,956,-630,-816,-664,360,-365,781,-215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordOverride():boolean",
            new int[]{145,-641,-382,-562,-755,-303,816,-527,615,-641,868,327,895,-260,857,776,-789,-195,-177,-189,-913,-761,-493,592,356,-177,684,725,682,68,-930,630,-353,332,502,618,923,-347,-885,997,24,-646,579,-41,357,126,-896,-686,847,700,-104,255,298,-453,4,217,799,30,109,-346,-981,413,-323,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordParameter(java.lang.String,com.google.javascript.rhino.JSTypeExpression):boolean",
            new int[]{84,777,-977,-486,432,998,923,307,-350,155,-204,-808,-943,-409,-758,603,28,351,364,-966,167,283,770,-620,402,-635,-226,-12,-47,507,351,358,35,808,14,134,-894,524,-443,408,461,-150,392,159,-901,-979,83,166,896,-773,-437,-334,-658,345,573,-350,-649,607,-152,769,5,967,-277,-441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordParameterDescription(java.lang.String,java.lang.String):boolean",
            new int[]{221,647,-45,354,-880,133,-393,710,-526,388,-344,-611,-100,-637,-789,417,401,-406,-14,-989,653,809,-308,-774,-152,-374,-729,716,968,-332,227,101,753,34,180,-201,536,674,-700,-397,661,-63,-759,-225,225,329,-142,960,539,-657,-902,-129,880,-879,195,326,857,-345,-402,980,-960,-741,872,-105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordPreserveTry():boolean",
            new int[]{-27,-685,-79,-136,239,61,-329,-649,-794,780,-665,-782,529,-720,442,261,871,-575,695,820,306,-115,-614,431,-197,71,-976,300,-808,837,184,-568,40,-594,-570,128,917,382,-32,980,-288,-349,-334,-481,819,-443,-987,235,333,-211,676,793,-324,-903,411,-22,-502,173,245,-842,-858,-185,-886,848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordReturnDescription(java.lang.String):boolean",
            new int[]{27,911,522,722,-171,278,-45,799,223,488,364,-878,620,555,772,618,-180,-897,-961,558,-745,711,-648,-354,-561,-650,-980,-22,448,896,478,-875,674,737,-355,-147,-212,-639,458,923,-89,465,-702,308,-728,612,289,-236,-657,744,992,-871,-635,-26,-106,21,-539,989,282,-360,-868,133,-441,290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordReturnType(com.google.javascript.rhino.JSTypeExpression):boolean",
            new int[]{617,96,604,-961,723,-212,-862,66,-785,875,453,-652,-643,146,296,-620,618,-101,-495,680,196,-137,950,172,-960,897,-615,936,224,403,386,-163,803,-183,-660,804,-904,-900,785,-198,-302,-297,323,939,481,123,-213,-470,-653,889,-621,-539,-458,569,96,729,989,-770,-89,650,-729,666,-82,889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordSuppressions(java.util.Set):boolean",
            new int[]{-580,282,214,-386,-28,-410,752,-604,-106,-643,862,295,-352,-152,997,-812,456,-758,448,-405,-805,836,867,-349,-790,328,-215,900,675,-537,958,-442,644,-787,303,389,732,350,386,728,-508,-208,946,329,983,-582,-206,-296,759,193,-452,-587,-407,371,-835,-646,-422,320,966,531,964,90,-24,11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordTemplateTypeName(java.lang.String):boolean",
            new int[]{661,-977,-364,-832,505,-817,754,-742,-254,-875,498,-467,-920,803,-194,917,-454,-2,117,-763,817,891,351,-155,-331,-479,298,-967,-377,275,67,-93,336,455,-402,-682,964,-523,-310,945,-590,-767,462,-304,-850,804,710,233,-828,-187,262,-432,415,477,-923,509,100,661,-415,957,-672,-888,564,15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordThisType(com.google.javascript.rhino.JSTypeExpression):boolean",
            new int[]{-51,720,-34,-147,361,419,-798,-732,821,-716,844,818,262,651,814,693,-943,868,705,872,748,-361,-545,547,-562,90,-616,-363,847,-984,475,-713,-676,729,425,-328,591,406,-291,-510,437,937,653,123,-842,845,-644,571,141,108,-821,-965,650,-606,486,-738,-365,599,-657,640,-843,396,-69,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordThrowDescription(com.google.javascript.rhino.JSTypeExpression,java.lang.String):boolean",
            new int[]{-831,541,31,-41,-479,699,-762,-610,-513,202,-67,944,-565,363,867,373,-902,365,837,-740,934,-666,-187,308,302,59,-883,-686,-5,-180,914,-66,436,953,-77,542,778,-485,-82,-784,82,660,318,-788,-223,221,-11,-820,668,-623,198,-231,-71,-737,633,-670,-300,914,-637,-66,-827,-970,-353,-908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordThrowType(com.google.javascript.rhino.JSTypeExpression):boolean",
            new int[]{-23,-487,-528,-507,440,462,70,-796,822,798,-729,791,-454,-457,-273,262,545,-912,-208,727,-15,366,-426,-187,-59,-851,-886,-180,876,-474,-935,-65,-450,-20,-241,81,-336,-889,800,-479,964,29,459,196,794,704,36,996,180,168,-891,611,365,-661,-893,-556,-518,464,-994,-811,481,713,129,79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordType(com.google.javascript.rhino.JSTypeExpression):boolean",
            new int[]{-598,-612,313,564,-882,912,-210,177,969,-132,-671,-727,50,558,-661,-652,-799,225,788,-337,960,554,235,633,926,-526,588,-866,344,870,405,536,-885,157,928,781,296,-998,-25,530,-327,911,-679,79,986,900,-719,506,997,982,783,-633,759,855,-515,113,137,-180,161,-595,-59,-292,969,-534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordTypedef(com.google.javascript.rhino.JSTypeExpression):boolean",
            new int[]{-830,684,636,-282,117,-252,323,-42,-251,-427,-549,-854,744,-946,544,-666,-434,-366,343,-799,-524,584,-492,-289,-226,769,463,71,446,76,-842,492,-449,944,927,-973,-84,-750,-841,-898,12,163,75,262,-759,561,-445,-862,302,-953,644,664,760,-274,-841,-839,522,482,-372,-910,-475,721,696,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordVersion(java.lang.String):boolean",
            new int[]{110,903,969,719,-792,-325,508,84,881,477,828,-119,34,231,527,334,-720,-170,-244,-488,-573,208,182,-303,771,-434,-391,925,601,4,217,-966,-712,-89,-761,794,586,-611,123,-202,-628,-297,666,-367,-45,310,451,-409,293,349,-982,667,-24,-341,651,-94,691,-636,-626,-676,-999,-149,850,-458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.JSDocInfoBuilder", "com.google.javascript.rhino.JSDocInfoBuilder", "recordVisibility(com.google.javascript.rhino.JSDocInfo$Visibility):boolean",
            new int[]{-456,-976,963,-761,-814,-207,249,929,813,872,480,303,483,-40,-531,909,-395,150,33,814,-595,125,-773,31,-313,465,62,574,302,103,-357,907,790,56,962,-778,-248,306,-981,-812,697,-110,-650,-408,-651,125,597,-335,690,248,316,752,360,-304,863,506,510,-263,-89,-224,628,-732,-92,968}));
    }
}
