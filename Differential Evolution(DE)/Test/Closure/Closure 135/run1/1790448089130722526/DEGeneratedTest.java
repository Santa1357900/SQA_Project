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
        org.junit.Assert.assertEquals("THROW:java.lang.RuntimeException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "check():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.RuntimeException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "normalize():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.RuntimeException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "optimize():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.RuntimeException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "processDefines():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "canBeCalled():boolean",
            new int[]{-487,1000,998,-1000,1000,-1000,-1000,562,940,-20,-771,834,-635,17,-669,-1000,1000,1000,-860,-1000,-689,-354,-465,183,718,163,68,413,1000,-1000,-102,-545,1000,67,127,381,1000,1000,1000,-788,-117,123,900,-436,129,-7,-423,-1000,-1000,433,858,292,-204,202,-431,1000,-1000,733,1000,-920,-587,261,-903,431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "canBeCalled():boolean",
            new int[]{-458,1000,-1000,-99,908,1000,1000,372,111,390,-754,262,-80,548,636,845,1000,144,-1000,1000,56,110,-31,-991,-252,-100,-1000,-555,-391,-1000,178,901,-1000,27,-546,823,-348,-802,-21,217,997,25,954,1000,840,650,442,-313,-260,-790,-994,-942,-570,-1000,590,1000,728,-472,-843,-54,-546,-169,-850,529}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "canBeCalled():boolean",
            new int[]{-251,777,-400,-513,-858,400,-1000,-1000,-641,-320,116,-1000,1000,17,435,1000,205,-55,-691,400,283,-327,497,183,151,367,-400,-1000,855,54,-565,336,-686,-110,-1000,135,-1000,14,-764,-788,786,-115,395,-288,56,84,-423,396,218,-1000,-607,292,-699,265,396,1000,-571,-783,-398,1000,-682,1000,568,482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "equals(java.lang.Object):boolean",
            new int[]{1000,599,-651,1000,9,-794,-52,-1000,-707,1000,-970,1000,-295,360,-464,773,1000,-1000,-844,692,-1000,-812,320,-811,784,-73,-300,1000,-748,653,-452,-317,-397,1000,-745,882,-1000,1000,-706,63,121,221,-500,-130,-249,-572,-320,-488,103,1000,790,1000,-633,204,-43,-574,-1000,1000,-1000,1000,942,1000,-1000,-151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "equals(java.lang.Object):boolean",
            new int[]{-847,943,143,-1000,-145,-224,807,1000,-699,634,-632,-849,-716,-603,386,1000,-1000,1000,-151,-1000,-144,-103,-767,1000,642,-244,-121,-1000,320,474,-550,889,1000,1000,1000,-428,1000,78,-301,519,1000,-600,-760,1000,1000,8,843,708,-1000,-1000,-283,-112,311,1000,-1000,-867,1000,-1000,830,-1000,404,-1000,678,-991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:java.util.HashSet", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getAllImplementedInterfaces():java.lang.Iterable",
            new int[]{1000,471,376,-124,1000,-617,-50,-746,-699,-1000,659,1000,134,753,648,-209,1000,51,1000,1000,538,-35,-1000,439,204,56,-753,529,1000,-541,1000,-166,1000,-1000,1000,1000,757,-407,-181,-123,-475,-807,145,499,1000,-851,459,1000,1000,-618,748,1000,-290,184,471,667,-439,1000,-61,-34,256,-607,1000,-839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:java.util.HashSet", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getAllImplementedInterfaces():java.lang.Iterable",
            new int[]{-582,-439,-611,-1000,746,331,924,-1000,1000,1000,119,-180,601,-726,1000,217,838,-278,-568,-1000,931,-1000,104,1000,-1000,-1000,-808,-735,-1000,-441,-993,1000,-654,-360,185,-136,349,907,188,884,972,-599,-72,-64,-727,1000,-1000,-927,-962,-1000,289,-889,-321,-1000,1000,-1000,133,-84,825,-1000,-1000,27,-748,639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getGreatestSubtype(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-75,-663,1000,763,533,183,542,-1000,-363,375,276,-1000,411,-202,-1000,-196,303,593,-488,143,-824,639,335,270,377,458,948,-460,1000,894,686,8,-1000,-980,-717,425,553,-905,-417,-485,-608,-1000,624,376,-104,450,-4,1000,-212,-1000,-404,1000,-5,-626,444,-502,-290,101,-985,-1000,-983,1000,-885,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getGreatestSubtype(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-845,523,558,1000,190,-1000,594,73,173,84,26,1000,248,-422,129,-187,-259,1000,-686,953,455,-1000,-221,-1000,-552,673,-1000,382,24,504,215,-749,796,1000,-1000,-394,-124,1000,-703,-819,-1000,727,-112,-1000,-74,-1000,-372,-416,1000,217,1000,-237,-1000,277,1000,-279,482,203,-141,335,1000,1000,737,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:com.google.common.collect.EmptyImmutableList", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getImplementedInterfaces():java.lang.Iterable",
            new int[]{1000,-1000,-206,4,292,-112,332,-1000,1000,86,-369,68,-136,-373,468,633,-232,563,240,-146,-867,340,693,-18,1000,-666,1000,181,-55,472,869,-761,-145,206,-119,-78,1000,813,-479,-276,-557,-249,-908,919,192,-1000,1000,-234,1000,-1000,-1000,957,412,275,376,224,1000,-290,-602,-1000,1000,410,-226,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:com.google.common.collect.EmptyImmutableList", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getImplementedInterfaces():java.lang.Iterable",
            new int[]{912,-383,-404,-587,-1000,44,307,-789,1000,258,-369,-898,-850,-275,-932,437,-390,1000,349,936,-637,204,804,-146,1000,958,-256,-623,518,-52,361,123,-939,-315,-685,698,1000,281,289,309,-667,-249,-1000,383,-1000,152,302,-234,434,-578,-77,164,-494,250,-340,152,825,-179,1000,-742,-400,-197,350,-433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getInstanceType():com.google.javascript.rhino.jstype.ObjectType",
            new int[]{1000,489,-643,750,-760,1000,213,1000,-253,684,-1000,-1000,-415,244,522,696,-329,-498,476,974,584,264,460,80,-1000,-737,1000,-343,86,498,1000,-1000,420,74,915,-390,1000,-1000,-999,-1000,1000,533,509,-937,96,-1000,564,363,-183,1000,1000,-42,928,-890,1000,43,926,-376,-436,-933,914,1000,79,-684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getInstanceType():com.google.javascript.rhino.jstype.ObjectType",
            new int[]{-499,-1000,181,477,-402,-658,-521,1000,590,20,632,-845,-254,495,-813,-270,-242,1000,1000,895,-734,309,-684,361,-563,-966,-428,453,193,-1000,-246,1000,-484,981,-1000,314,-128,-137,744,-541,-448,-808,52,-345,392,323,-80,-153,-275,-792,-458,228,-303,646,-535,-245,-197,415,-215,895,363,-909,453,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getLeastSupertype(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-1000,-405,410,-558,-879,-1000,-1000,-606,895,307,203,519,99,-565,134,408,-1000,936,-611,181,-1000,-257,-81,-603,379,-1000,-1000,472,615,368,1000,-617,269,381,-297,-1000,-1000,-1000,-446,815,-730,-1000,967,443,157,786,-242,-841,-767,-1000,335,-536,1000,1000,-194,899,-408,875,-1000,-779,1000,-756,-188,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getLeastSupertype(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-253,-749,1000,-1000,-1000,-965,-1000,-494,780,-382,-866,-1000,941,-565,134,-263,-1000,365,-716,-1000,136,898,-292,-317,-1000,-1000,-1000,-489,-318,1000,649,61,-556,-586,-214,1000,-1000,-116,-407,1000,-1000,1000,1000,-449,87,-1000,1000,-825,-1000,-1000,1000,-1000,-257,892,-194,899,336,1000,-352,-601,627,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getMaxArguments():int",
            new int[]{-155,848,-14,-174,-41,-360,332,224,-930,-983,67,-207,-900,147,-569,-480,408,1000,-260,-377,789,714,1000,309,-173,-330,617,-519,-184,462,-219,-390,-190,965,154,-118,788,957,-49,247,-416,-194,-801,-253,481,-403,1000,-114,-1000,94,-164,-1000,525,713,-400,-144,583,48,-572,64,-126,29,806,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getMaxArguments():int",
            new int[]{-1000,-951,-548,-588,-974,985,1,-1000,112,909,-1000,-135,-344,791,-9,-701,-189,-234,-1000,466,325,325,547,-92,-1000,-1000,687,369,-391,49,1000,1000,-350,629,682,-490,161,-445,570,1000,746,383,-1000,1000,-1000,-379,-235,624,-338,532,1000,-218,-79,275,1000,-1000,-1000,1000,504,368,-441,-438,-1000,-718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getMaxArguments():int",
            new int[]{59,-81,-14,-174,-41,212,496,-1000,-163,148,-355,110,-638,334,551,852,408,967,940,-465,-132,1000,851,378,883,-46,-20,-390,285,95,30,-618,50,610,-160,22,996,279,353,546,241,411,561,547,-586,-468,-292,237,-177,-358,-795,-403,366,891,742,-144,232,85,-572,559,-464,44,171,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getMinArguments():int",
            new int[]{1000,777,-711,-411,516,-868,467,1000,-720,1000,-185,353,1000,1000,-1000,-309,1000,-230,-1000,-1000,20,-1000,635,678,902,-310,-346,1000,686,-573,390,-1000,-679,-1000,-517,-89,71,319,603,-1000,-840,-467,780,1000,-127,-604,1000,1000,59,-1000,117,36,-737,456,1000,394,-979,64,417,1000,-1000,952,-237,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getMinArguments():int",
            new int[]{372,-751,-101,-1000,-1000,170,420,118,-1000,-904,92,981,1000,866,368,319,-636,-194,294,-416,265,-1000,-350,147,904,-258,-316,756,-852,241,-1000,958,482,271,1000,-1000,411,492,-1000,-967,308,-513,994,1000,-555,-996,-1000,1000,1000,-1000,393,569,170,272,-278,1000,-522,-801,-1000,678,-1000,1000,970,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$EmptySet", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getParameters():java.lang.Iterable",
            new int[]{1000,-333,1000,278,-1000,634,42,1000,234,-510,-1000,-646,10,-40,-1000,-366,594,279,1000,263,-1000,-357,758,-649,177,1000,74,750,840,-1000,463,-326,-78,345,1000,873,438,-420,769,994,847,-1000,345,-466,1000,-630,846,1000,-963,210,-825,-493,-106,-771,540,127,776,551,635,-1000,-5,-516,1000,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$EmptySet", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getParameters():java.lang.Iterable",
            new int[]{-480,-434,-431,-912,-292,-313,992,7,-96,632,77,537,-145,513,-785,-839,925,704,-948,74,-176,-855,-883,-858,-130,-106,-208,279,785,-248,-615,885,-302,-781,309,-790,-701,-531,-878,-315,-112,-436,565,612,458,692,-387,-56,-141,879,38,-697,641,303,-979,-124,-827,125,933,401,334,580,-424,-982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getParametersNode():com.google.javascript.rhino.Node",
            new int[]{1000,249,-120,-781,-945,-190,899,244,894,875,-1000,122,1000,-586,114,1000,1000,-209,534,-25,57,502,-392,1000,-1000,-1000,960,1000,117,-394,-480,152,-502,161,168,-643,-890,-670,-168,-1000,-257,-180,1000,-562,-869,151,-996,739,84,724,-535,545,1000,-73,318,-272,-147,-855,1000,-937,54,-325,45,186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getParametersNode():com.google.javascript.rhino.Node",
            new int[]{-458,1000,1000,-479,1000,1000,57,216,771,-490,145,416,-565,-362,204,-333,1000,-590,4,1000,-555,604,-1000,-290,421,-142,-1000,-1000,-1000,-569,-825,-1000,-60,-68,687,-1000,-549,-83,-814,130,-573,795,639,465,-87,546,-4,-797,-1000,811,-653,-956,-537,485,1000,-402,-177,408,888,1000,-494,-115,-410,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnknownType", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getPropertyType(java.lang.String):com.google.javascript.rhino.jstype.JSType",
            new int[]{166,-751,406,-63,992,491,-1000,-1000,-1000,441,-1000,-527,-671,1000,-369,279,-1000,1000,89,-942,-101,225,-318,-1000,585,1000,-23,83,1000,-803,-868,-891,-717,-871,317,149,737,-307,-1000,400,-600,177,326,-361,-1000,261,794,908,1000,-681,1000,-315,905,-692,818,617,-294,894,-647,327,362,-1000,418,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnknownType", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getPropertyType(java.lang.String):com.google.javascript.rhino.jstype.JSType",
            new int[]{29,201,1000,-603,1000,592,700,-1000,35,968,798,-1000,-1000,1000,-1000,1000,-623,1000,-1000,-423,488,1000,-769,-642,511,1000,1000,-1000,1000,1000,-1000,-1000,-599,-1000,818,107,1000,499,-782,-1000,-545,-252,1000,-848,-368,-218,1000,-749,1000,574,1000,1000,1000,-1000,-1000,-1000,-582,1000,-1000,1000,271,988,190,578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionPrototypeType", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getPrototype():com.google.javascript.rhino.jstype.FunctionPrototypeType",
            new int[]{94,801,1000,-842,346,388,-1000,432,-1000,-215,-796,1000,1000,-1000,252,1000,-595,437,418,-999,1000,1000,369,1000,-360,1000,-796,-1000,975,80,-1000,1000,-1000,-495,977,758,-1000,-1000,475,902,649,379,-1000,-293,-550,222,579,378,1000,462,1000,1000,-1000,1000,363,1000,1000,-393,1000,-230,1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionPrototypeType", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getPrototype():com.google.javascript.rhino.jstype.FunctionPrototypeType",
            new int[]{733,728,55,414,1000,-1000,-1000,-665,-453,-1000,-1000,-477,-478,636,-1000,-405,-391,-1000,-960,-545,478,767,-486,209,-201,300,541,1000,-644,-193,-301,-992,-256,-1000,-1000,-429,-829,-429,-1000,-94,846,-644,-136,790,1000,-958,-290,-1000,480,860,-1000,470,253,1000,-1000,354,416,-1000,1000,1000,1000,538,1000,-849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getReturnType():com.google.javascript.rhino.jstype.JSType",
            new int[]{-215,-1000,-850,-531,35,1000,-669,1000,-1000,800,10,-209,-465,1000,1000,-364,638,-470,-327,-1000,-342,1000,-1000,-1000,-1000,-366,761,421,-415,-1000,-1000,242,-1000,-719,510,-21,-586,-477,229,856,908,526,790,-1000,-3,-682,-640,1000,-1000,243,15,1000,-297,738,-1000,1000,280,543,78,-356,-1000,-350,-453,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getReturnType():com.google.javascript.rhino.jstype.JSType",
            new int[]{1000,209,-257,-1000,-132,-861,53,-1000,676,-1000,-322,-1000,1000,-934,614,-842,492,61,384,-622,-522,-677,-353,-1000,-665,409,-626,35,848,-794,1000,-797,-744,1000,223,-12,-757,-435,857,-749,-337,-736,-1000,708,265,-386,1000,-54,300,60,824,-870,905,88,-213,-1000,311,1000,71,-530,-1000,-1000,672,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getSource():com.google.javascript.rhino.Node",
            new int[]{-1000,-117,992,845,837,-1000,-1000,-113,-1000,879,613,-605,994,-1000,782,-961,-832,-445,-206,1000,1000,-1000,-496,449,783,-1000,-466,346,-124,1000,-274,517,178,-658,776,-657,134,8,958,737,-1000,-405,-1000,520,-682,910,-720,814,-1000,-1000,-1000,1000,-1000,-299,136,-813,-1000,1000,348,-1000,279,1000,1000,-757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getSource():com.google.javascript.rhino.Node",
            new int[]{-1000,589,-673,701,363,515,100,425,-549,878,81,-119,-642,139,-871,850,-1000,459,-91,903,1000,-105,-376,-1000,574,-483,-911,-92,271,1000,49,-584,-202,-262,1000,-789,555,40,1000,-803,135,1000,330,825,-292,1000,404,-448,-276,-286,-241,569,341,-191,-890,-985,122,221,-679,-343,220,545,-159,847}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getSubTypes():java.util.List",
            new int[]{251,-697,1000,-656,1000,-347,1000,-72,1000,-659,80,-457,-1000,304,736,-631,851,-334,-449,-157,-30,956,-1000,1000,502,-331,1000,-1000,911,590,-754,975,-469,862,-951,1000,831,-838,504,802,4,-994,-999,-869,-859,-1000,640,1000,123,-1000,-321,1000,-106,-1000,624,-1000,756,325,400,-827,-388,-911,-940,38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getSubTypes():java.util.List",
            new int[]{-812,-1000,68,-734,-1000,-217,-1000,-621,1000,-222,-255,-45,218,652,-1000,1000,-831,1000,605,1000,-868,-424,-600,97,1000,498,-1000,1000,-1000,-1000,1000,-879,364,258,978,-651,-366,-1000,-915,333,-83,360,1000,1000,1000,1000,1000,186,-800,-548,-603,-1000,1000,792,272,-291,-883,689,-115,270,-100,938,-55,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getSuperClassConstructor():com.google.javascript.rhino.jstype.FunctionType",
            new int[]{933,-695,-1000,-1000,-573,-714,-181,-338,1000,882,-1000,-78,-1000,417,-159,518,-1000,847,-1000,469,1000,361,-281,-1000,1000,733,-1000,-1000,69,-948,1000,1000,-118,1000,-543,161,-296,1000,1000,-1000,-711,-643,440,-1000,1000,-742,-348,429,955,973,1000,-806,1000,-1000,-1000,-1000,-61,-625,-1000,942,1000,-1000,-513,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getSuperClassConstructor():com.google.javascript.rhino.jstype.FunctionType",
            new int[]{304,783,-160,-524,-293,247,346,-49,1000,937,-199,-819,1000,150,-1000,822,-186,479,-826,215,1000,-692,-207,24,86,-326,-1000,-1000,-510,-166,129,500,400,223,414,183,389,98,482,-957,-241,164,-82,257,667,-348,-360,369,45,319,-838,-130,74,563,20,318,654,125,346,653,504,312,343,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getTemplateTypeName():java.lang.String",
            new int[]{77,-681,-36,-1000,786,-15,789,-408,-181,-1000,-725,-839,-580,797,341,1000,1000,-225,659,-893,-9,-293,-241,-1000,180,686,-1000,-1000,896,-44,-276,-871,-753,1000,-316,468,169,-771,684,961,36,-607,-586,1000,-702,252,-1000,1000,-167,488,-816,149,513,1000,-1000,-871,-76,949,-335,524,407,900,21,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getTemplateTypeName():java.lang.String",
            new int[]{-919,-681,-968,-932,411,-1000,133,-787,530,-766,-655,-393,-1000,510,-64,802,939,-609,630,-624,436,1000,135,-938,-142,824,-1000,75,416,449,697,-962,-944,-940,-581,67,-102,1000,546,746,-1000,-968,-1000,976,-641,-1000,1000,945,-276,1000,-889,-1000,578,-1000,-1000,-923,298,763,-464,981,-179,155,475,365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getTopMostDefiningType(java.lang.String):com.google.javascript.rhino.jstype.JSType",
            new int[]{887,783,177,349,1000,-751,135,-797,-195,1000,-1000,-1000,-1000,-733,-225,-74,-1000,-666,-864,808,-1000,-118,-40,-845,1000,139,1000,535,30,-1000,-208,543,114,-172,-1000,762,962,906,-598,375,554,-692,-392,-1000,1000,-708,-51,1000,892,-305,-1000,-707,511,-108,834,643,1000,454,-74,-91,203,-2,912,-654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getTopMostDefiningType(java.lang.String):com.google.javascript.rhino.jstype.JSType",
            new int[]{242,457,-742,-281,1000,-1000,-156,-21,1000,464,-422,-481,215,-119,-1000,470,121,-1000,-937,1000,-308,-485,381,-729,116,194,238,1000,-1000,-555,402,407,-4,-998,-1000,-100,1000,867,-1000,-152,933,-1000,-286,-959,1000,-171,2,1000,1000,-475,-216,942,418,335,142,-756,1000,814,-389,328,686,-279,899,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnknownType", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getTypeOfThis():com.google.javascript.rhino.jstype.ObjectType",
            new int[]{982,-437,1000,-317,-246,729,1000,977,-1000,-886,-1000,-893,978,808,-640,-941,1000,-265,266,1000,-389,189,1000,635,-1000,-1000,-45,1000,199,226,-746,-905,-330,-1000,299,-147,-1000,1000,88,-248,-489,1000,1000,-1000,884,572,180,59,-804,-186,1000,1000,753,740,1000,-130,-1000,670,147,-636,-17,-885,488,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnknownType", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "getTypeOfThis():com.google.javascript.rhino.jstype.ObjectType",
            new int[]{555,-1000,1000,-449,-1000,-735,-1000,679,557,-508,-992,-368,1000,-779,-345,1000,823,-578,736,296,1000,-1000,311,1000,407,-949,-227,1000,-1000,1000,-1000,-1000,-885,-237,1000,-1000,-1000,-966,1000,-1000,108,-1000,-928,1000,-849,-1000,-1000,1000,96,349,288,-271,687,-1000,-273,455,1000,572,754,-1000,-1000,743,-497,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "hasCachedValues():boolean",
            new int[]{333,-264,168,-1000,243,-1000,-934,-522,1000,553,350,524,-726,-1000,-922,66,-295,1000,-1000,-1000,675,-641,-366,931,-711,-1000,107,617,-576,1000,209,822,-635,1000,238,1000,-804,1000,-343,-508,-1000,-1000,1000,401,-756,243,-832,-948,-1000,-1000,-1000,1000,726,1000,1000,-282,438,-541,855,505,1000,-814,-99,-336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "hasCachedValues():boolean",
            new int[]{837,-117,829,-643,-617,580,-448,-803,400,1000,-410,877,-967,-824,-127,448,-707,1000,-1000,-787,1000,559,584,706,-112,-202,-626,-580,-1000,-250,-530,852,618,618,-782,1000,-1000,394,145,-123,-847,-1000,250,125,-273,282,-1000,35,310,333,-1000,1000,763,429,820,-299,601,-1000,-138,907,790,710,-886,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "hasEqualCallType(com.google.javascript.rhino.jstype.FunctionType):boolean",
            new int[]{-218,-923,-417,-548,370,360,-1000,-569,1000,299,-421,44,-342,459,1000,693,-1000,545,785,-987,-28,311,684,1000,304,384,-1000,42,1000,1000,-45,-53,-507,168,232,-786,356,296,-1000,-125,-939,1000,660,813,1000,356,505,179,245,562,472,-1000,-1000,451,726,-295,670,-708,-823,284,553,-520,975,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "hasEqualCallType(com.google.javascript.rhino.jstype.FunctionType):boolean",
            new int[]{-804,-223,714,-1000,-1000,1000,-554,-1000,798,-229,-21,-969,-20,539,1000,-275,429,-111,333,-507,278,173,-549,84,-899,-1000,-294,-52,1000,111,-466,-827,930,210,203,82,-574,1000,561,-430,-522,585,-642,-414,795,-305,587,153,722,195,-821,546,323,-631,365,-528,-1000,-893,-192,648,211,936,-394,-726}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "hasInstanceType():boolean",
            new int[]{-1000,992,-676,853,-444,865,905,-525,-363,-358,310,-371,-591,-283,177,-1000,-732,-847,-304,632,-1000,1000,149,709,-1000,59,-705,-251,-42,95,-795,-434,1000,30,380,643,224,428,-113,-710,70,213,450,500,592,588,-13,77,-1000,414,-35,-847,-605,-569,241,478,-839,-707,1000,257,795,902,-407,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "hasInstanceType():boolean",
            new int[]{-455,-921,400,-878,719,363,507,331,-388,-35,-248,546,-528,-76,884,72,-266,153,454,-238,860,391,478,66,-554,-581,-227,-829,585,205,854,-483,13,-294,544,952,-2,483,-522,-868,79,-25,-465,-644,821,307,902,966,-470,868,-67,-289,-471,-492,-694,989,-939,200,-981,-214,91,-740,730,-708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "hasProperty(java.lang.String):boolean",
            new int[]{-307,77,90,1000,1000,-405,331,1000,-1000,1000,1000,853,-474,-317,-362,-258,1000,298,-754,-908,-1000,61,-1000,913,126,1000,-1000,-436,779,-1000,-1000,-792,470,-1000,154,90,1000,877,-922,834,1000,547,66,-54,-106,-741,-367,1000,-764,1000,832,1000,-1000,-964,-449,-291,-721,-555,-1000,-1000,186,-1000,429,-78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "hasProperty(java.lang.String):boolean",
            new int[]{1000,399,883,-1000,-1000,-662,-694,1000,712,-1000,-499,1000,-1000,236,-1000,1000,386,1000,851,538,933,812,39,-283,-862,385,1000,429,-1000,-1000,211,-981,167,377,-319,630,-580,-63,-1000,1000,1000,-1000,790,-465,-716,284,988,-1000,935,1000,-1000,-360,-459,842,771,26,459,1000,-671,-604,-794,-346,1000,-120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "hasUnknownSupertype():boolean",
            new int[]{-987,-331,-1000,719,-186,563,271,-334,751,373,449,-1000,1000,-467,-342,526,1000,178,-387,-35,1000,-362,-622,93,-1000,-580,-1000,-1000,90,860,-142,472,-1000,279,638,1000,-145,-360,280,733,-935,-466,849,-602,89,-237,761,134,256,648,33,860,438,-1000,-377,-286,-952,658,-1000,51,422,-31,-1000,650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "hasUnknownSupertype():boolean",
            new int[]{121,309,-42,64,412,-694,-182,-778,-660,337,187,978,-156,-1000,-907,-922,-772,-85,-1000,267,-358,789,251,-38,-1000,624,31,-954,633,900,-1000,-93,-363,360,-1000,762,554,599,357,-4,-878,735,1000,-519,-156,-67,-916,-790,-92,-847,1000,1000,-620,891,322,505,-84,178,329,983,-223,9,-129,882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isConstructor():boolean",
            new int[]{194,-587,844,492,-1000,-774,-917,1000,-1000,630,-1000,675,275,-751,562,-281,1000,-539,-842,315,770,-696,382,779,-1000,-910,1000,-735,-116,88,-1000,569,583,-1000,277,1000,-293,1000,-1000,-332,327,-1000,-627,957,-1000,-214,-902,1000,827,821,89,910,536,-1000,-821,-779,809,-1000,-1000,460,-1000,387,461,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isConstructor():boolean",
            new int[]{412,-587,221,655,251,384,-1000,1000,-959,395,569,972,179,-73,-152,-58,-264,-539,-918,315,913,851,1000,1000,-153,-531,1000,-735,-116,1000,729,-386,583,389,22,438,525,-198,-750,336,-1000,1000,-627,-528,-722,-228,-605,631,1000,554,89,20,268,-6,-489,-660,1000,-907,-757,546,1000,849,192,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isFunctionType():boolean",
            new int[]{651,-105,543,-1000,1000,-475,-114,-1000,1000,309,-523,-531,137,-578,-856,-412,-134,1000,-1000,-226,817,371,-288,64,467,-1000,-1000,-434,-1000,250,1000,1000,330,-136,1000,-374,799,-56,-622,1000,1000,-552,62,-1000,1000,167,106,-715,1000,1000,-1000,-19,-217,1000,1000,-653,-1000,-254,-1000,-691,1000,-410,-1000,649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isFunctionType():boolean",
            new int[]{-1000,899,400,-422,1000,192,1000,-294,-319,-1000,-305,830,454,-1000,728,-621,934,231,201,-792,745,-164,893,408,-833,-1000,450,-953,-168,349,-466,-626,-176,61,574,234,-1000,-162,215,-839,-724,-1000,-896,1000,-1000,-422,-246,156,411,24,-780,288,634,-90,-308,-1000,973,857,817,1000,-105,478,-855,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isInstanceType():boolean",
            new int[]{555,-274,130,377,-126,839,1000,186,979,638,-963,-598,-859,-1000,0,1000,-401,-944,-1000,1000,411,-370,-1000,-697,-1000,152,-664,1000,1000,228,-1000,-119,1000,31,-680,1000,821,1000,-1000,-280,-897,27,648,93,-352,0,-637,-501,-646,180,593,1000,-684,87,706,1000,-322,0,90,268,373,123,924,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isInstanceType():boolean",
            new int[]{315,-57,-143,186,977,-197,414,-67,-371,-547,1000,-992,870,927,-430,766,557,389,99,-1000,513,40,-584,-255,400,-406,-786,87,458,1000,-996,555,1000,184,1000,209,269,1000,689,488,994,315,347,-1000,-263,286,-23,212,1000,539,-140,-692,262,-482,-868,1000,1000,822,-129,-915,-738,-399,-790,-738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isInterface():boolean",
            new int[]{1000,559,-350,-247,1000,915,-1000,1000,173,-522,50,1000,1000,45,1000,393,1000,-1000,1000,-515,1000,671,384,1000,-51,-568,-1000,-1000,1000,-1000,1000,916,397,-1000,-1000,1000,217,-1000,-1000,711,-202,-1000,1000,173,-658,-1000,-921,551,-1000,-771,1000,-1000,1000,1000,1000,-801,157,-539,509,-1000,-382,-227,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isInterface():boolean",
            new int[]{-318,-559,-11,380,-959,946,503,21,-1000,849,77,-1000,145,-243,-749,4,484,772,1000,-168,447,-940,-1000,1000,-737,-750,606,-292,294,848,715,999,-1000,408,-611,-1000,635,-541,454,468,681,-756,542,1000,381,1000,-648,660,834,953,894,-1000,-222,1000,1000,225,-700,-966,494,846,412,-1000,-153,-641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isOrdinaryFunction():boolean",
            new int[]{-565,571,490,1000,-1000,465,-183,1000,564,-351,106,-945,592,193,457,535,-950,128,-772,624,-1000,-299,-404,923,-259,1000,1000,675,-563,575,-128,-716,133,85,1000,-88,1000,908,920,326,1000,487,78,133,-690,-868,-1000,414,-1000,-251,74,-406,-116,-779,582,-1000,544,872,-604,-127,313,906,-949,301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isOrdinaryFunction():boolean",
            new int[]{-255,697,-197,-38,559,943,-1000,666,514,1000,-1000,185,253,-110,301,998,964,1000,-419,-1000,863,-856,1000,-585,654,-632,204,-1000,-822,471,-566,1000,1000,279,-647,-376,-457,-1000,1000,-74,168,-606,1000,212,-2,147,989,827,568,-76,449,-955,976,1000,679,-298,-1000,-964,-333,-911,394,617,-538,-148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isPropertyTypeInferred(java.lang.String):boolean",
            new int[]{537,99,-1000,-1000,980,1000,722,701,-1000,-1000,-41,-87,1000,107,-1000,205,-691,1000,1000,-193,1000,-1000,8,717,-570,-1000,23,1000,920,-1000,-547,287,1000,812,-420,474,-314,-1000,-715,-987,756,289,623,-150,1000,-647,-248,-1000,421,227,-1000,-419,1000,-515,-6,394,-849,173,-1000,-211,-1000,-157,-1000,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isPropertyTypeInferred(java.lang.String):boolean",
            new int[]{488,247,-166,-208,1000,1000,1000,1000,-1000,-1000,680,-518,-983,-1000,-871,453,-781,-45,-146,-1000,-64,1000,-1000,628,-299,-355,1000,218,-798,28,-1000,-1000,1000,514,-74,-323,130,-1000,498,-816,162,-1000,439,-809,37,250,1000,-203,-1000,1000,189,649,1000,66,-1000,-1000,167,400,-1000,-841,-598,-174,-1000,-736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isSubtype(com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{-912,-301,86,-662,-165,306,1000,-775,-211,-908,438,525,-699,261,-311,371,-495,-581,-647,289,233,573,388,780,-525,-882,1000,-582,-27,671,56,-896,863,-428,438,104,-486,-143,-760,-357,-148,1000,600,-382,283,-834,77,167,327,-791,-124,151,-1000,187,154,-515,-231,662,-312,709,-463,690,-869,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "isSubtype(com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{-469,-365,-362,-559,-663,-255,-500,960,-188,618,-58,859,159,21,-57,188,-171,220,71,-195,-611,177,-397,754,-582,-150,495,-338,23,59,38,-76,741,-838,469,-263,-11,-202,-87,-458,1000,522,41,11,283,225,-117,290,156,-335,-548,-252,374,533,411,518,221,-686,542,539,-1000,918,1000,60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "setImplementedInterfaces(java.util.List):void",
            new int[]{-986,-59,-74,-729,-1000,-510,20,907,-266,458,-466,-608,710,-46,734,645,-420,554,-840,-162,-992,-932,822,-236,-224,959,-617,-458,351,-131,236,847,105,338,714,-28,-587,-120,-68,-327,988,289,231,982,982,-690,576,-62,-404,596,819,-449,-722,287,314,-788,-91,442,-994,226,840,-902,-181,-796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "setImplementedInterfaces(java.util.List):void",
            new int[]{426,-138,318,1000,-1000,222,1000,-487,409,1000,-247,400,372,-374,88,1000,-124,322,-541,-117,1000,243,-145,-139,443,1000,352,427,641,-569,6,538,1000,68,-744,-315,1000,600,-52,-857,-295,-26,1000,-245,-613,813,-552,58,-354,1000,-13,571,-1000,-283,1000,212,373,-356,-104,741,-907,869,-59,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "setPrototype(com.google.javascript.rhino.jstype.FunctionPrototypeType):boolean",
            new int[]{1000,225,-1000,-1000,-746,-1000,249,-224,-1000,310,466,242,544,-1000,1000,-664,552,906,972,441,-281,1000,1000,234,-432,453,-1000,793,202,-160,-1000,1000,1000,137,-1000,840,-1000,821,-991,-1000,926,-1000,-700,510,-1000,-1000,-73,-34,-928,1000,936,-828,737,1000,105,-1000,-1000,1000,1000,259,296,48,-226,374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "setPrototype(com.google.javascript.rhino.jstype.FunctionPrototypeType):boolean",
            new int[]{107,-933,519,985,-774,1000,1000,-1000,705,-376,1000,-434,-1000,1000,-662,166,49,354,-608,-127,691,-487,-87,1000,1000,-446,-419,2,-839,207,292,-638,-610,-1000,-1000,1000,190,35,1000,1000,-952,769,1000,-310,1000,413,-468,1000,87,-950,-1000,844,536,-118,1000,1000,40,-883,71,459,-379,316,-1000,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "setPrototypeBasedOn(com.google.javascript.rhino.jstype.ObjectType):void",
            new int[]{933,-765,-1000,894,412,-526,1000,1000,-1000,-479,-1000,394,1000,-198,-123,962,-822,-874,1000,916,-1000,-781,1000,-1000,-180,-878,-1000,373,-1000,-411,208,-78,689,1000,90,847,198,-1000,-1000,269,-715,1000,267,453,-1000,-1000,394,997,32,1000,379,-1000,1000,-212,-392,-670,740,802,-1000,-247,89,831,394,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "setSource(com.google.javascript.rhino.Node):void",
            new int[]{21,-565,336,353,-977,560,684,-3,193,-44,-2,-289,-867,912,920,875,-360,625,341,-945,-93,232,748,318,220,-597,-746,759,-328,-485,165,809,830,379,-909,-205,581,812,-532,-595,425,873,145,-827,402,-464,-826,947,-500,-238,348,394,-63,-555,-849,40,-993,-419,-205,74,314,114,806,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "setSource(com.google.javascript.rhino.Node):void",
            new int[]{-439,-281,578,-105,-475,396,1000,-493,911,-722,-862,300,-1000,138,994,666,157,-365,214,204,-545,196,826,808,-334,-934,-767,1000,549,74,-183,-37,319,188,1000,-108,1000,644,-1000,-1000,903,817,1000,-97,730,223,-934,-1000,-1000,-521,-210,-961,66,-34,-947,451,-795,-1000,471,-38,610,-663,508,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.String:ZnVuY3Rpb24gKCk=", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "toString():java.lang.String",
            new int[]{-1000,-766,527,-174,-1000,533,177,-552,264,-312,-740,299,-970,520,-296,1000,9,-991,529,379,-231,754,-1000,96,-903,282,612,-411,1000,359,307,908,-47,375,310,-730,873,-1000,-549,-919,637,-1000,-1000,293,-1000,196,287,89,1000,-433,479,178,-1000,447,-800,322,1000,-1000,-125,-122,-1000,567,451,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "toString():java.lang.String",
            new int[]{690,-595,-232,204,471,773,-787,25,-627,-301,198,-636,-631,1000,786,529,-690,1000,1000,-175,-1000,-1000,-116,506,-974,1000,-421,-320,-310,-451,-993,222,415,-83,1000,-259,916,-391,-440,808,647,-59,0,-117,535,-1000,-342,-1000,221,-1000,-610,-359,-13,-510,-174,-1000,-215,-642,1000,336,414,790,345,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "visit(com.google.javascript.rhino.jstype.Visitor):java.lang.Object",
            new int[]{-155,159,-792,470,-632,25,-1000,354,-1000,267,490,269,484,35,243,147,-1000,577,-439,-245,-497,661,-188,-691,-269,-59,1000,948,545,1000,913,-807,414,141,-428,-166,661,-726,-362,-268,382,137,535,-67,350,954,894,384,716,-744,1000,-562,-99,744,-67,615,-858,-606,20,-562,67,-1000,-1000,-354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionType", "com.google.javascript.rhino.jstype.FunctionType", "visit(com.google.javascript.rhino.jstype.Visitor):java.lang.Object",
            new int[]{-1000,45,848,269,-208,1000,-1000,-69,-1000,-405,989,1000,232,591,1000,-978,-967,1000,-108,531,-1000,-780,1000,-1000,435,1000,1000,1000,-478,686,-308,-1000,-699,784,-120,1000,-7,-506,-1000,-152,359,44,769,-1000,282,297,1000,1000,196,1000,-400,134,3,1000,757,450,-987,666,-1000,279,44,52,-949,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.ClosureCodingConvention", "com.google.javascript.jscomp.ClosureCodingConvention", "applySubclassRelationship(com.google.javascript.rhino.jstype.FunctionType,com.google.javascript.rhino.jstype.FunctionType,com.google.javascript.jscomp.CodingConvention$SubclassType):void",
            new int[]{-1000,-367,1000,747,-1000,-580,-481,-178,-1000,395,1000,-1000,1000,624,-1000,1000,-1000,-379,-972,-1000,-363,1000,955,-1000,275,712,963,-427,69,-720,261,-567,-704,1000,-804,-94,-1000,-575,-554,-549,-494,1000,-520,-429,-1000,937,1000,-164,928,-819,960,-334,519,421,88,47,-613,1000,-284,393,356,625,1000,-56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.ClosureCodingConvention", "com.google.javascript.jscomp.ClosureCodingConvention", "applySubclassRelationship(com.google.javascript.rhino.jstype.FunctionType,com.google.javascript.rhino.jstype.FunctionType,com.google.javascript.jscomp.CodingConvention$SubclassType):void",
            new int[]{-832,-720,732,-3,-331,-62,-723,191,-981,575,545,-79,-65,696,388,8,645,131,-888,-90,-754,102,-865,235,19,344,239,291,-935,951,970,562,-875,795,-297,914,-618,-477,406,-719,-314,-543,254,709,-311,-985,890,658,137,673,362,-286,-243,-48,519,-961,-950,-573,-951,-771,-372,-679,180,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "resetForTypeCheck():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.testing.BaseJSTypeTestCase", "", "addNativeProperties(com.google.javascript.rhino.jstype.JSTypeRegistry):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
