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
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CommandLineRunner", "", "getDefaultExterns():java.util.List",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "check():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{829,-229,-960,-162,952,-810,-583,-664,27,450,-92,-370,873,945,-875,346,-33,346,-527,556,516,223,-990,614,750,914,302,-265,217,673,-639,903,777,741,-466,-562,-816,-737,356,6,701,-812,-207,-760,-4,260,49,-793,739,971,176,969,792,-999,-920,-799,-382,30,215,413,679,-924,241,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-947,945,751,730,-77,749,-640,249,823,574,-539,681,475,-498,848,920,-116,309,71,122,-78,519,-658,-764,-548,286,369,-877,277,951,264,167,164,-340,-591,-57,359,-225,-681,-941,-447,-9,-29,566,101,-962,800,-491,434,-367,-370,96,84,976,-833,767,492,-531,-619,-27,-121,585,64,-104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{844,831,-917,106,707,-60,224,-569,788,527,828,562,906,737,496,690,653,-878,359,-383,-788,-927,532,-474,-742,-259,-572,977,-243,325,-46,-494,846,674,-888,731,-876,625,867,-320,-311,-796,-734,242,23,902,-244,-926,434,-407,-624,-896,206,-655,-891,820,-211,-625,44,972,-546,-306,-804,3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{144,173,-1000,1000,107,-676,1000,-606,179,-258,590,283,-495,1000,-329,-133,630,1000,546,693,-237,1000,1000,-861,660,300,820,-181,-977,150,1000,1000,-81,527,272,-545,235,1000,-1000,-641,497,51,1000,-871,-289,-1000,-450,-723,1000,426,581,647,1000,-107,85,370,648,242,374,-167,-754,-451,503,-685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{472,381,338,-736,-621,-62,-334,-406,-308,-562,46,-341,173,45,-449,365,551,-160,793,288,-310,766,772,-990,941,21,-483,-627,-786,782,-647,-860,887,-531,409,346,-924,458,-465,713,-941,164,-819,-107,-377,678,9,1,36,804,-5,982,929,-751,-391,608,928,975,-699,639,-896,-733,-38,581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{22,267,594,876,-908,347,-91,496,-799,-853,201,370,576,-108,557,-177,413,209,804,888,-687,-565,781,195,523,562,-369,561,-264,904,194,24,-168,-22,-632,-127,335,754,850,-739,-379,-575,80,441,909,-843,381,-655,964,898,318,-739,-926,-745,-884,701,-622,-305,-392,213,-147,879,636,823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{1000,474,-804,-32,-905,134,-1000,-728,43,-1000,-344,875,217,546,-906,409,-247,75,705,-615,180,1000,579,-740,523,-406,-144,-327,-526,470,-531,-999,454,-863,106,994,-1000,902,185,881,96,1000,80,441,475,226,752,-335,-315,1000,24,-739,1000,-649,-633,533,1000,817,357,1000,-147,-274,-788,90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-1000,-330,452,-360,-816,1000,-1000,-874,-242,-1000,-886,957,21,-164,-318,425,-612,198,-1000,-1000,112,-1000,82,-1000,-1000,-1000,-479,92,913,-548,376,-78,321,-558,-1000,-1000,104,759,772,-941,-343,1000,-130,-715,1000,603,304,-1000,127,1000,141,-238,-1000,470,1000,652,201,-898,184,988,1000,1000,-140,-307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.Result", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compileModules(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compileModules(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{795,641,-1000,-1000,150,289,-1000,-303,-283,-336,-7,1000,-927,1000,-1000,1000,-1000,-1000,-90,-856,1000,-1000,-114,739,-1000,400,-908,360,376,-585,1000,887,-558,1000,358,-359,-567,427,455,143,1000,-1000,131,-1000,1000,-1000,-1000,-253,-1000,-1000,-1000,428,1000,-663,233,-715,-1000,-517,-1000,-768,-1000,886,719,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "disableThreads():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getAstDotGraph():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getCodingConvention():com.google.javascript.jscomp.CodingConvention",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getErrorCount():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getErrorLevel(com.google.javascript.jscomp.JSError):com.google.javascript.jscomp.CheckLevel",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.LoggerErrorManager", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getErrorManager():com.google.javascript.jscomp.ErrorManager",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.PrintStreamErrorManager", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getErrorManager():com.google.javascript.jscomp.ErrorManager",
            new int[]{361,911,852,-46,748,-754,-486,-68,915,352,-798,-360,526,-501,666,860,-483,519,766,222,926,403,428,-268,373,277,471,-709,785,42,340,527,208,-924,-984,750,323,-313,-790,-689,-380,-729,-702,279,117,-498,422,944,-53,314,653,-598,201,888,211,206,987,-539,-666,930,847,328,-408,365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getErrors():com.google.javascript.jscomp.JSError[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getInput(java.lang.String):com.google.javascript.jscomp.CompilerInput",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getMessages():com.google.javascript.jscomp.JSError[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getResult():com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getReverseAbstractInterpreter():com.google.javascript.jscomp.ReverseAbstractInterpreter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getRoot():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceLine(java.lang.String,int):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceMap():com.google.javascript.jscomp.SourceMap",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceRegion(java.lang.String,int):com.google.javascript.jscomp.Region",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.Compiler$IntermediateState", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getState():com.google.javascript.jscomp.Compiler$IntermediateState",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getTopScope():com.google.javascript.jscomp.Scope",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getTypeRegistry():com.google.javascript.rhino.jstype.JSTypeRegistry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getWarningCount():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getWarnings():com.google.javascript.jscomp.JSError[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "hasErrors():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{-428,-576,-408,-686,827,-596,172,86,-1000,-1000,1000,-832,-522,704,47,0,400,3,-397,369,759,508,-143,492,310,-656,384,-298,1000,47,-792,196,556,-1000,382,-615,-404,-818,400,1000,47,506,631,1000,1000,485,-342,1000,-344,1000,96,474,-638,-364,401,73,40,498,467,695,229,339,-1000,-815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{608,-1000,-1000,-1000,-55,-1000,1000,-718,414,-300,113,-1000,561,607,-261,1000,-441,-1000,-580,-694,-497,655,857,1000,1000,181,1000,-612,1000,1000,750,1000,-221,-234,471,-338,-1000,-446,-869,518,-879,-1000,1000,-174,1000,1000,762,11,714,1000,984,-1000,1000,-1000,378,-1000,1000,61,-1000,-859,-984,784,664,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{310,-93,367,-77,802,-708,-478,845,-46,-372,616,331,933,710,220,-263,-786,-210,897,538,444,238,-99,656,621,-645,-492,-89,793,-141,416,-320,219,-383,146,-53,-327,778,-94,525,402,614,4,427,171,118,-420,135,-250,672,-872,-202,-771,-43,132,288,-364,-338,934,-513,-550,-438,-51,-845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{-270,-496,-1000,744,-915,278,-385,231,-318,-132,605,649,142,600,376,540,-1000,1000,251,-638,448,-249,-670,-900,396,-1000,442,4,61,-470,527,724,564,-1000,-540,-895,543,649,-1000,1000,-410,-504,12,370,1000,-823,-227,-1000,63,-712,-719,435,735,385,-385,-1000,-194,568,-1000,632,-483,-407,323,-359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{-330,-468,461,537,273,336,-650,-886,243,695,-775,559,541,168,-167,-567,593,792,622,-715,-441,45,-873,-67,676,-489,975,-554,-476,673,-532,556,629,-130,-698,65,-966,4,-799,672,127,-686,-38,-363,337,-789,-244,-319,-841,33,-621,641,-665,-625,604,-505,-960,620,781,-373,-571,-730,394,-578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{479,854,592,1000,215,-238,-815,210,25,423,-712,-723,715,108,-182,30,-1000,-366,-50,-469,-665,-603,327,722,-940,607,616,835,-175,-978,496,-176,183,-87,37,411,62,607,619,1000,650,534,724,-672,-284,493,430,-819,-151,-759,-415,41,662,-346,-210,584,221,-456,-242,698,-95,269,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initModules(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initModules(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{-1000,-448,-1000,1000,-1000,141,-553,1000,-810,1000,-175,-932,-14,-70,469,-24,-837,1000,85,1000,687,1000,-1000,896,-1000,224,-274,-39,-943,-1000,-1000,-192,-37,1000,82,-1000,1000,231,452,-49,-760,-553,1000,1000,1000,1000,-1000,-350,-142,-574,1000,1000,-858,-1000,663,-1000,-187,-1000,397,1000,-1000,968,-20,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initOptions(com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initOptions(com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{867,-631,1000,-1000,-702,294,203,-60,-311,146,523,1000,-1000,464,526,564,868,193,662,-918,602,-1000,-139,1000,723,692,-977,449,-511,-1000,-163,283,-160,-47,461,-34,297,-109,836,-132,3,260,-124,-289,-772,1000,-968,-76,-223,932,-212,-368,-73,-75,-567,-612,-1000,-496,1000,498,-1000,-285,682,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "isIdeMode():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "isTypeCheckingEnabled():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "newExternInput(java.lang.String):com.google.javascript.jscomp.CompilerInput",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "normalize():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "optimize():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.JSSourceFile):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.JSSourceFile):com.google.javascript.rhino.Node",
            new int[]{-858,-499,552,264,2,-4,343,-226,-145,880,-383,-95,-760,610,547,426,-657,85,-3,-438,357,397,-461,-89,328,-758,171,180,-230,213,-239,-93,-484,955,87,986,-703,121,33,-294,352,-534,-54,285,-986,31,-513,-570,-341,340,-620,-686,669,-181,394,-437,133,217,633,-690,885,-279,717,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "processDefines():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "rebuildInputsFromModules():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "report(com.google.javascript.jscomp.JSError):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "reportCodeChange():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "setErrorManager(com.google.javascript.jscomp.ErrorManager):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "setLoggingLevel(java.util.logging.Level):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "setPassConfig(com.google.javascript.jscomp.PassConfig):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "setState(com.google.javascript.jscomp.Compiler$IntermediateState):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "toSource():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "toSource(com.google.javascript.jscomp.Compiler$CodeBuilder,int,com.google.javascript.rhino.Node):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "toSource(com.google.javascript.jscomp.JSModule):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "toSourceArray():java.lang.String[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "toSourceArray(com.google.javascript.jscomp.JSModule):java.lang.String[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:java.util.LinkedList", DEReplay.run(
            "com.google.javascript.jscomp.JsMessageExtractor", "com.google.javascript.jscomp.JsMessageExtractor", "extractMessages(com.google.javascript.jscomp.JSSourceFile[]):java.util.Collection",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "addWarningsGuard(com.google.javascript.jscomp.WarningsGuard):void",
            new int[]{703,-86,-641,-776,-311,634,-1000,576,-289,-12,-620,544,982,1000,457,-629,-986,92,159,-683,-893,-152,181,705,392,210,-1000,1000,-1000,222,230,435,337,707,362,11,39,-7,-568,294,-550,-892,-287,-369,884,830,393,756,10,339,-431,80,1000,-71,-1000,20,1000,-122,-191,-199,1000,400,143,363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "addWarningsGuard(com.google.javascript.jscomp.WarningsGuard):void",
            new int[]{-626,-681,290,239,587,437,997,1000,-446,114,-408,-845,-263,1000,-899,1000,-562,-9,280,-458,-830,541,-28,726,-1000,-1000,578,-1000,-1000,-459,920,70,-334,119,-1000,1000,1000,-983,697,-400,230,-127,577,-139,449,-182,-1000,272,-400,-263,-261,-350,949,56,228,786,341,-648,90,-114,-142,-393,-866,655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "addWarningsGuard(com.google.javascript.jscomp.WarningsGuard):void",
            new int[]{-1000,324,538,-343,328,-369,361,-308,-1000,353,-273,302,97,507,-28,-126,-209,-940,575,332,-135,921,390,-194,962,-63,470,-745,-50,-206,909,-1000,459,23,1000,338,-10,33,231,-967,-1000,-816,142,-94,-190,-476,-407,-313,-656,811,-169,-361,-1000,-186,685,174,989,238,-1000,525,-993,4,252,128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "addWarningsGuard(com.google.javascript.jscomp.WarningsGuard):void",
            new int[]{638,734,-572,-115,-1000,634,-1000,290,363,-446,-154,908,537,1000,539,-997,-946,-955,219,-957,-1000,-631,560,723,81,1000,-1000,437,-958,222,588,-433,-250,-614,920,734,-180,437,-1000,457,-388,-510,1000,-708,884,1000,1000,631,-1000,-475,-1000,-454,569,22,-1000,1000,-400,-620,-140,-55,1000,367,260,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "addWarningsGuard(com.google.javascript.jscomp.WarningsGuard):void",
            new int[]{37,943,408,-910,-514,675,-71,-204,-389,395,-876,534,-846,493,583,443,-158,-131,594,804,901,939,831,480,709,376,-754,-492,252,284,-911,774,-944,79,213,-224,-716,485,190,-840,-168,-270,119,-81,-594,288,-521,938,-184,314,-86,907,8,-676,-622,-462,-275,574,-253,490,-887,-874,694,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "addWarningsGuard(com.google.javascript.jscomp.WarningsGuard):void",
            new int[]{1000,943,-931,-714,174,579,262,-1000,640,532,-817,816,-1000,32,-1000,443,-118,-239,99,-167,777,1000,-34,-177,1000,-782,-606,396,1000,351,-1000,-421,1000,1000,891,-1000,-716,1000,-371,-840,-48,847,-1000,164,-594,-1000,-521,-1000,1000,1000,1000,1000,-225,-432,900,-1000,454,641,-237,575,-191,154,-267,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.CompilerOptions", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "clone():java.lang.Object",
            new int[]{-34,569,-584,-398,125,-313,-439,-900,-989,258,-261,50,501,-617,-870,339,-139,-186,273,513,487,595,722,151,718,75,-678,167,669,543,928,-137,-671,312,-677,-909,206,-353,153,255,-988,-363,-577,-484,-77,2,-54,-575,226,1000,-187,-731,66,-1000,-13,46,676,-564,20,646,-36,316,764,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.CompilerOptions", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "clone():java.lang.Object",
            new int[]{-536,-121,608,1000,793,-1000,-1000,922,1000,1000,924,1000,-559,-1000,395,-668,1000,-566,1000,-313,908,-1000,1000,-898,818,-1000,-1000,1000,424,137,1000,-1000,-163,-1000,-925,878,52,1000,715,1000,-1000,332,-1000,1000,-1000,1000,1000,1000,1000,37,-1000,411,599,1000,-1000,0,-1000,1000,475,-590,1000,796,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.CompilerOptions", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "clone():java.lang.Object",
            new int[]{-792,129,732,-238,-113,985,-273,-190,146,-429,466,49,-186,-628,146,199,-320,-134,441,-348,1000,-907,382,-446,1000,926,-589,947,207,-176,121,-1000,-116,458,99,400,1000,427,5,1000,437,-445,-240,-187,-1000,-134,393,1000,489,-157,479,108,-217,367,530,417,-1000,-426,-39,156,513,-57,612,-691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.CompilerOptions", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "clone():java.lang.Object",
            new int[]{-382,-46,650,622,-722,-1000,-1000,942,919,-92,-6,222,-196,-100,271,-509,126,858,1000,-1000,1000,-272,1000,-301,484,-956,115,-752,-791,-1000,-665,21,589,-1000,-567,1000,-215,446,377,709,-947,-256,-1000,889,526,557,-202,544,1000,-586,-619,1000,728,889,-1000,127,724,1000,-145,389,1000,-436,-595,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.CompilerOptions", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "clone():java.lang.Object",
            new int[]{152,264,457,-47,-307,351,959,-986,-1000,823,265,-336,12,-241,-522,-526,-59,90,-242,1000,-87,848,475,917,-154,702,79,1000,934,-90,95,374,-217,66,-1000,-1000,-476,-305,-119,71,250,-802,5,-937,509,-281,-636,-646,-17,1000,-654,-855,850,163,886,668,1000,103,175,463,-92,698,17,-9}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.CompilerOptions", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "clone():java.lang.Object",
            new int[]{-530,84,-84,-118,105,687,-349,-94,-397,-81,485,-75,-220,-1000,-1000,859,-471,-131,945,204,539,-580,1000,-400,1000,893,-638,793,-176,536,1000,245,-1000,704,-576,1000,1000,469,-69,213,-1000,-634,-955,-1000,-916,479,-672,660,847,398,-1000,-381,-68,-52,78,-307,-212,-816,-31,138,772,215,445,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "disableRuntimeTypeCheck():void",
            new int[]{31,-256,356,644,347,358,-75,-318,-101,-961,977,-1000,-729,1000,-1000,-189,28,676,-1000,57,-116,-860,-84,-95,-1000,-1000,-868,-157,-1000,927,-123,-533,-385,-782,-1000,1000,-348,-331,-712,854,-532,763,-461,-176,893,-1000,160,-47,-603,-343,-844,-444,-1000,-526,225,-359,105,-915,-386,337,-1000,-1000,1000,-59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "disableRuntimeTypeCheck():void",
            new int[]{-755,259,34,570,-185,-1000,998,570,1000,-435,-1000,638,-1000,25,-547,693,-1000,909,134,379,1000,765,587,90,-756,687,8,303,1000,-310,1000,634,371,123,700,-1000,283,1000,-206,287,-543,-516,-1000,-255,-628,793,270,-1000,-395,-1000,-1000,434,369,999,904,376,-231,1000,-99,526,626,872,624,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "disableRuntimeTypeCheck():void",
            new int[]{-1000,-146,343,1000,-699,-246,409,126,569,37,-1000,583,-1000,-1000,745,711,-740,-415,727,151,-166,1000,1000,-619,-869,-70,38,-451,-784,-1000,554,585,-276,-2,-372,-743,697,435,-696,1000,-48,-1000,-1000,-1000,-1000,1000,1000,369,122,-1000,-1000,1000,957,-168,927,188,-1000,1000,1000,600,527,328,1000,-927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "disableRuntimeTypeCheck():void",
            new int[]{-755,-146,716,644,-180,358,409,-597,-328,-435,-769,424,-729,-68,745,693,860,909,853,151,-6,919,587,-514,518,-382,38,-305,490,-619,629,605,-912,-782,28,-407,697,651,-338,854,46,-516,-994,-255,-500,-142,-391,-47,477,-521,-844,-444,-722,-496,-202,-599,-405,-781,-587,-375,-544,328,-465,-332}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "disableRuntimeTypeCheck():void",
            new int[]{41,734,-914,1000,499,179,-608,-192,744,-142,-1000,-1000,-153,500,-1000,700,-1000,347,621,1000,-962,-231,1000,480,-1000,-1000,386,772,-259,-316,861,1000,491,1000,-1000,-870,-693,1000,-1000,496,-162,-677,-808,-916,1000,134,1000,505,-943,-458,-123,950,-663,722,583,-182,-1000,800,6,961,-877,-638,-249,143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "disableRuntimeTypeCheck():void",
            new int[]{-1000,614,-525,-49,-152,-406,1000,833,-141,-212,-1000,806,-1000,-754,386,612,8,-836,128,1000,878,1000,1000,736,-359,373,-154,-665,346,-1000,1000,483,524,501,-279,-1000,-105,727,45,880,885,-990,-528,-916,-1000,909,484,-1000,1000,-1000,-1000,-21,-80,100,1000,-352,-330,1000,-241,973,298,778,274,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "enableExternExports(boolean):void",
            new int[]{163,649,276,29,75,168,-408,-268,-478,583,928,618,1000,-455,-179,-482,273,-950,-742,-406,174,748,-867,-970,499,-13,445,-1000,628,-270,-994,227,526,744,-478,-344,578,1000,-626,978,670,-133,997,246,148,1,-146,-55,223,-533,-267,102,116,320,-1000,1000,-3,-68,-79,411,704,165,1000,-468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "enableExternExports(boolean):void",
            new int[]{1000,739,124,539,693,1000,605,1000,-559,-622,1000,133,702,573,-643,-747,-725,660,-355,546,1000,365,-490,-970,398,326,-326,-572,-416,1000,-629,630,-11,726,-972,-539,649,1000,983,-467,-1000,-134,722,-713,482,-994,29,-189,1000,-349,1000,1000,-448,518,207,-467,-387,-1000,-172,-1000,-437,1000,499,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "enableExternExports(boolean):void",
            new int[]{600,49,-582,1000,-587,611,555,941,560,-894,1000,-678,878,-68,-1000,-994,458,214,1000,288,-1000,1000,-758,901,71,781,588,-1000,1000,-643,-123,413,58,1000,-472,-1000,-428,526,375,-487,348,527,514,-1000,614,403,-585,-99,969,22,1000,560,-546,-464,-457,-1000,56,-956,-684,444,-312,1000,432,897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "enableExternExports(boolean):void",
            new int[]{-412,723,177,-1000,415,209,-1000,-808,-823,307,429,425,1000,-455,-693,-482,-551,-846,-742,-381,174,238,-677,-970,809,-506,456,-998,-186,-164,-70,-611,1000,309,-178,-136,687,947,-1000,709,-213,254,997,60,950,-227,54,-606,-36,-679,-507,190,438,636,-722,1000,646,636,27,363,1000,303,1000,-916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "enableExternExports(boolean):void",
            new int[]{478,503,-97,386,-649,128,179,134,-40,33,1000,516,999,452,-668,-990,961,-931,-476,178,174,1000,-1000,-949,-113,498,649,-1000,950,-756,-1000,370,-134,1000,-807,-1000,344,856,-836,665,1000,312,1000,551,89,500,169,712,552,72,896,167,28,-99,-809,713,207,69,881,230,918,1000,602,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "enableExternExports(boolean):void",
            new int[]{-533,879,775,-962,-39,188,133,-829,-736,1000,-46,304,192,-186,1000,-689,217,215,-891,-18,1000,152,554,-1000,-324,-1000,-364,-211,-258,130,583,401,256,640,-783,275,838,-509,-657,224,945,502,803,68,-322,9,-1000,472,140,579,630,-476,67,268,-335,1000,-1000,-29,-490,297,633,654,207,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.CompilerOptions", "com.google.javascript.jscomp.CompilerOptions", "enableExternExports(boolean):void",
            new int[]{-234,-861,-1000,-17,-1000,896,-713,-807,-357,-592,-307,343,482,409,556,-329,-387,1000,1000,77,-676,-477,-467,657,22,421,-826,19,-749,765,1000,194,-507,-181,279,-650,-16,-147,-480,-354,-675,-754,-400,-89,56,1000,-388,-152,569,-345,648,-489,-172,436,616,-1000,-10,-369,21,-1000,-86,1000,-1000,80}));
    }
}
