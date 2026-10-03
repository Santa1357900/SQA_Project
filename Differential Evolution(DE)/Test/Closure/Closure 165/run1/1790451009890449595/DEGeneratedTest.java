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
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "canPropertyBeDefined(com.google.javascript.rhino.jstype.JSType,java.lang.String):boolean",
            new int[]{193,-440,374,473,563,-329,790,-144,-408,438,-568,-638,603,-489,481,-513,65,298,906,81,763,12,-498,377,991,-492,381,-681,-454,368,-758,811,445,219,245,-338,680,547,-838,-11,-876,462,628,624,691,773,-226,-330,-703,-492,-697,793,-228,589,157,-976,941,-253,-675,-74,-653,-28,44,809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "clearNamedTypes():void",
            new int[]{661,-494,-741,1000,1000,-975,-294,-349,-520,-231,691,128,-654,-1000,471,171,563,469,-236,-459,-1000,569,-154,-270,497,-286,-212,301,452,-673,386,336,203,81,855,1000,687,428,-507,-32,708,-207,654,-1000,1000,113,190,-11,961,1000,856,167,133,-405,-1000,-576,819,259,1000,-41,439,-252,-81,-821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "clearTemplateTypeName():void",
            new int[]{640,992,489,115,-451,-773,782,740,339,-844,537,-27,916,613,65,-687,-917,-430,530,-167,-704,-794,346,-603,-482,147,904,848,633,728,665,382,252,739,732,8,117,-607,216,244,-233,-25,-64,-971,649,40,288,190,440,886,-510,-252,978,-474,-172,860,921,408,6,237,8,723,-731,526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.PrototypeObjectType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createAnonymousObjectType():com.google.javascript.rhino.jstype.ObjectType",
            new int[]{916,1000,-477,-418,1000,-327,1000,907,-726,-807,592,-179,1000,1000,744,531,-492,-130,1000,-602,924,886,1000,645,-873,418,-419,295,805,-1000,1000,-375,-896,-308,-381,62,-67,-1000,-701,-586,919,-825,1000,-222,-1000,1000,-1000,1000,-1000,1000,366,165,1000,-1000,1000,1000,164,-46,-1000,-246,1000,-281,1000,-220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-972,828,99,1000,1000,-1000,-1000,-247,-799,748,-342,511,-827,177,-979,-41,673,-1000,11,816,-1000,107,389,-499,1000,-1000,50,261,-836,-291,326,107,1000,1000,-707,807,1000,-273,-1000,558,-921,237,352,-293,764,598,-1000,-547,1000,-303,-72,-543,1000,-174,669,-742,-17,-505,-746,-158,-1000,295,1000,-327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-73,-1000,629,702,-198,402,234,-1000,-1000,1000,88,-530,1000,-221,-1000,846,1000,-1000,923,983,-1000,-151,142,-1000,77,-206,-483,-1000,597,-633,-247,-1000,-259,1000,847,-372,793,-1000,148,-1000,785,621,-1000,586,-1000,-298,97,1000,1000,-1000,-1000,288,454,-671,377,-1000,446,-324,209,75,187,-871,-737,281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{440,-794,974,794,-806,-538,605,-158,-900,950,672,-245,906,57,-797,824,203,-42,296,-187,327,85,932,-833,184,-702,-438,475,-811,75,895,-754,-798,815,741,-294,965,-587,981,-660,-87,-280,-207,-803,-585,-854,100,-400,541,753,-129,-173,238,469,-586,596,469,-255,-958,-293,-714,-80,-356,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-677,-174,744,-647,-85,-168,-484,428,568,283,-656,-740,224,331,227,229,985,860,-601,739,394,-579,-314,112,255,372,-751,788,978,715,-924,454,834,648,498,-931,-561,527,-530,-792,278,643,-586,-389,165,944,-13,374,131,726,-688,-159,462,-853,-68,-74,317,413,18,316,-290,493,-778,831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(java.lang.String,com.google.javascript.rhino.Node,com.google.javascript.rhino.Node,com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-534,-1000,419,612,-1000,193,471,-495,-993,533,986,1000,1000,-278,573,-1000,-454,0,354,991,-33,513,-1000,836,814,-756,174,542,-53,-650,445,0,-257,-314,623,957,1000,1000,-756,-1000,356,11,0,696,754,723,-511,-557,-1000,0,287,1000,165,934,594,-277,-981,92,61,-576,915,769,0,923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-1000,-664,-776,-137,365,-1000,831,627,281,-413,742,-1000,-170,303,-352,789,1000,-1000,-1000,-1000,-741,400,-389,494,501,-791,-306,-684,270,1000,513,-98,348,783,-431,1000,891,-320,1000,279,732,1000,-404,643,-203,313,-1000,-294,90,-763,-937,-23,980,207,-431,805,773,-468,542,-118,-161,-1000,349,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-264,-392,-426,1000,63,812,-1000,1000,899,-549,-1000,-948,-984,-426,-153,714,-129,1000,182,281,1000,411,1000,1000,173,1000,-558,-252,-876,390,19,1000,1000,-1000,576,588,562,-1000,-1000,-24,676,-507,298,670,-620,-1000,538,-267,-119,947,1000,303,-1000,-299,1000,515,-1000,1000,-385,-97,-36,1000,1000,-371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createDefaultObjectUnion(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-974,251,1000,-746,-707,1000,600,-272,-1000,-136,1000,218,1000,-906,118,-168,1000,1000,-685,-544,-606,-447,645,-1000,120,-965,-1000,1000,-134,492,1000,-587,965,-1000,319,1000,-1000,1000,-1000,806,-11,1000,982,-62,1000,-772,521,-549,530,-596,-814,-966,-1000,484,-1000,1000,173,1000,-773,1000,-333,-608,794,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createDefaultObjectUnion(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{652,-464,488,-233,443,990,217,17,-973,478,983,-584,-322,-795,-743,-547,-857,567,4,-459,-380,172,-928,-777,632,608,227,339,158,-11,566,567,19,-974,611,609,378,-94,-880,39,458,-363,-180,-698,-917,652,-322,-322,-507,484,67,-808,49,66,-738,351,-127,858,-961,-615,287,396,-148,236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createEnumType(java.lang.String,com.google.javascript.rhino.Node,com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.EnumType",
            new int[]{-488,922,-306,-393,738,560,389,831,-815,-135,-635,330,729,16,775,-481,-227,82,-888,-185,770,809,-699,995,-638,351,-607,-167,73,-340,131,9,-937,-727,-767,-487,500,264,-654,-883,-201,225,-667,881,183,358,790,-910,817,-60,-488,-426,-921,-223,-981,918,500,576,155,708,-702,-367,-687,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope):com.google.javascript.rhino.jstype.JSType",
            new int[]{-301,26,228,-1000,-1000,105,873,-1000,1000,-867,609,562,-1000,622,1000,-968,-220,602,186,-663,519,-399,-1000,-626,1000,341,-28,59,627,-277,75,677,-264,905,-169,1000,637,341,-129,-367,-129,248,234,88,210,-572,-537,-1000,-164,-422,243,-12,-231,-668,611,1000,-895,1000,360,-396,-1000,-355,1000,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope):com.google.javascript.rhino.jstype.JSType",
            new int[]{298,-636,22,-1000,-812,-1000,418,-783,176,0,-96,509,-137,-144,1000,-225,770,540,243,1000,1000,126,-679,-758,559,-478,-736,350,624,-877,104,1000,334,520,-555,518,797,-629,261,-59,1000,-521,38,0,96,-202,1000,-733,-662,-361,299,309,442,-974,727,286,-148,1000,633,-1000,-287,-694,91,717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-686,-226,913,977,-310,-389,549,501,396,-560,-36,893,-462,77,-480,-973,449,-384,-20,225,904,226,247,122,40,-166,995,25,-844,-527,-135,637,-7,782,-532,-950,330,-892,808,350,-240,-3,-52,-125,362,446,749,-811,-328,-741,-666,181,806,-946,557,-656,901,64,-783,254,134,710,-993,544}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{853,-738,-171,-377,-298,294,-443,-1000,-717,-560,-578,958,1000,-1000,976,-973,948,-384,-20,-337,1000,915,-337,738,552,-136,511,761,-1000,1000,732,986,1000,-56,695,238,134,543,808,433,-514,1000,-525,-408,-341,1000,749,-1000,-318,-595,-1000,708,-158,-445,231,575,-711,-12,-331,254,-36,559,-488,-643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.Node):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{184,-372,-947,530,479,829,998,765,-328,636,-710,-414,750,414,-376,489,731,763,275,476,524,-950,567,577,600,660,70,578,-510,211,-330,7,-493,-259,462,-195,741,-545,-529,351,394,989,638,-426,-184,-645,-801,-309,236,-251,-373,826,94,-795,-377,208,133,729,-679,954,986,996,-192,541}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{76,670,249,400,-183,-77,-128,-151,-919,-551,-112,-63,942,-423,375,862,438,375,-445,-351,-902,821,-931,-293,789,-502,-53,769,528,263,-37,922,138,-995,770,667,181,26,-872,105,475,857,841,230,-318,381,591,49,-60,-968,-225,784,-652,-477,-362,784,-827,84,551,-93,167,-519,86,140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-628,-986,353,1000,-239,-409,-624,55,-119,795,223,-1000,-597,-1000,-712,198,626,310,-1000,119,-29,503,-46,-576,-867,547,-337,-222,393,150,-25,-549,-41,-1000,-818,-38,98,-951,-679,-172,435,284,83,-54,302,-404,1000,1000,-595,-13,422,-754,1000,-334,830,-1000,-709,-1000,-1000,-1000,1000,767,-164,-46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.ObjectType,com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.JSType",
            new int[]{682,-704,-241,-679,843,-441,-72,740,579,-562,-503,-267,354,-192,195,430,-738,-490,962,855,-414,879,-996,884,61,646,-891,1,-243,50,-639,-899,318,803,266,-371,761,-594,766,528,-60,-775,572,637,-636,-246,-134,-529,-861,-490,510,638,437,-871,-400,-464,-828,413,474,441,-719,-702,53,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithNewReturnType(com.google.javascript.rhino.jstype.FunctionType,com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-882,586,569,-383,144,518,-533,936,-45,-393,-806,987,-33,303,-69,-688,178,-634,-660,-85,-290,-684,-625,149,-270,530,-888,-253,-522,-279,153,-823,271,986,-514,-650,-672,-647,830,-172,-936,-191,-567,167,102,-847,-966,807,-348,-281,-686,-14,-838,988,-498,-417,169,-572,-983,-518,-875,510,-944,-897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithNewThisType(com.google.javascript.rhino.jstype.FunctionType,com.google.javascript.rhino.jstype.ObjectType):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{495,-194,-756,744,-386,-302,9,298,-125,-505,-79,-828,-974,932,618,195,682,-425,-175,-808,-138,625,-987,690,-41,-772,572,248,-904,733,-125,390,-540,-202,-950,-397,919,-730,-238,-44,-31,398,-158,493,935,-959,-758,768,181,301,419,-389,104,270,-948,574,-350,431,728,-664,202,-332,152,-191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{641,256,579,-959,-1000,893,-56,-1000,260,-851,87,824,-1000,-1000,-310,-608,1000,-1000,-49,-551,-735,-1000,146,-573,924,-393,-305,1000,1000,1000,-881,9,50,485,486,1000,-918,292,102,-720,1000,27,349,1000,-953,1000,1000,-797,71,1000,110,1000,881,-810,-1000,-1000,138,-1000,-273,-1000,1000,-241,-794,877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{667,542,543,258,89,-118,-1000,-585,-574,175,202,-378,60,-51,304,-918,-806,-384,-370,331,76,1000,-925,670,658,-228,-251,770,299,-628,-832,553,698,-958,-1000,-636,-543,-1000,-492,-1000,-400,-768,132,598,-312,844,-883,367,1000,15,515,-1000,60,-1000,-349,-508,1000,-1000,-133,652,-629,-441,448,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.ObjectType,com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.JSType",
            new int[]{354,-398,214,1000,-1000,-486,-393,-1000,156,1000,326,-405,-1000,1000,-446,-1000,950,1000,804,-157,-435,-319,-400,-1,636,-927,-613,1000,-1000,-523,-402,1000,-495,-1000,751,-502,-923,633,19,464,343,-1000,-575,615,129,-483,1000,-1000,345,1000,-1000,311,-1000,-465,-1000,-386,1000,803,-558,937,11,78,-844,928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createInterfaceType(java.lang.String,com.google.javascript.rhino.Node):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{721,-650,184,3,731,406,556,985,-668,-754,-577,19,-604,169,348,867,273,-466,-670,-151,895,741,193,-89,-131,459,750,480,165,563,-1000,100,157,-1000,-1000,-122,787,1000,-157,284,253,156,-504,168,-111,675,201,300,1000,-346,-302,-151,-98,-480,231,-1000,224,573,60,828,-1000,108,158,-807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.NamedType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createNamedType(java.lang.String,java.lang.String,int,int):com.google.javascript.rhino.jstype.JSType",
            new int[]{-100,592,-766,-1000,-13,1000,-105,361,-892,-554,-524,-87,503,1000,-284,-839,-1000,-726,676,29,375,387,-55,-655,-1000,858,-797,252,573,-137,-738,1000,430,214,372,851,-1000,46,560,-587,1000,820,422,1000,50,1000,1000,-105,359,253,-836,813,231,62,281,-412,536,59,-541,733,998,-504,-483,-375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createNullableType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-345,-218,-137,259,867,846,656,773,-988,689,-98,-801,389,-198,940,-506,752,650,450,-409,-529,652,85,-675,316,675,400,854,-524,846,316,-841,-568,912,9,625,-982,207,516,-660,-386,312,-451,-219,751,-54,99,837,-330,311,226,-251,324,143,-887,-519,172,-935,-54,-732,-259,-512,450,-333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.PrototypeObjectType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createObjectType(com.google.javascript.rhino.jstype.ObjectType):com.google.javascript.rhino.jstype.ObjectType",
            new int[]{-880,-686,283,-398,-338,-501,0,-550,-212,1000,-217,-230,-918,1000,0,0,544,940,-624,-1,-395,0,-744,1000,118,183,-54,203,1000,-589,215,-459,843,1000,123,-1000,637,-512,-711,999,-232,313,-110,-590,221,-389,870,22,75,38,0,-1000,-582,-1000,1000,-16,472,-360,97,0,0,454,0,589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.PrototypeObjectType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createObjectType(java.lang.String,com.google.javascript.rhino.Node,com.google.javascript.rhino.jstype.ObjectType):com.google.javascript.rhino.jstype.ObjectType",
            new int[]{-360,-862,-631,131,751,-52,991,438,-448,663,-182,-725,-83,-192,130,569,328,-133,846,-179,-27,-969,121,-768,-701,-140,-555,623,-229,805,-329,685,-172,-723,-300,-845,892,739,443,552,705,-832,96,281,-801,141,-198,103,694,812,-697,236,-736,-925,329,-310,-202,-684,-836,828,-508,355,-931,590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createOptionalNullableType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{1000,-1000,-946,-51,-1000,-1000,470,218,-463,-245,-98,-13,-108,-1000,1000,520,-1000,256,925,-1000,-1000,1000,-1000,844,-1000,-1000,-366,-782,-1000,399,-468,1000,-12,-628,420,-1000,-425,600,1000,489,707,-446,1000,-1000,1000,-36,-778,1000,884,96,807,233,-579,-187,-168,-1000,1000,-119,-603,-531,-1000,-826,832,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createOptionalParameters(com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.Node",
            new int[]{-115,712,13,117,-376,77,-32,-740,531,973,216,575,-1000,-1000,-826,-852,-1000,633,29,859,-163,393,793,1000,-1000,417,-824,-1000,1000,-1000,-278,-621,690,-179,-310,-386,-576,-628,94,388,757,-404,709,635,642,-154,1000,-296,230,-313,166,-586,-422,8,-643,1000,-781,-803,-840,149,716,-1000,-294,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createOptionalType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-849,-212,209,-506,-507,769,211,-726,-375,872,245,786,-435,807,131,135,922,980,776,111,-933,373,-26,1,568,930,334,-298,426,948,583,550,-149,651,392,-82,-708,-809,510,566,-706,628,492,955,-629,-895,-443,-684,523,382,285,116,319,204,-304,749,492,-490,54,790,-80,446,231,-75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.ParameterizedType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParameterizedType(com.google.javascript.rhino.jstype.ObjectType,com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.ParameterizedType",
            new int[]{435,884,719,-176,-838,543,958,822,748,590,585,-191,-226,464,-431,167,486,887,88,-220,87,277,-682,241,649,484,-258,160,298,-240,-910,-235,925,-292,212,-398,-141,-162,-122,811,735,315,794,185,35,506,982,31,726,12,-960,701,817,5,828,597,657,-80,595,418,401,-227,-286,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParameters(com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.Node",
            new int[]{941,-288,-62,-454,408,558,-785,695,-77,387,-534,-387,821,-550,776,-688,-1000,321,-451,-932,-548,540,-156,-131,-1000,352,-1000,603,-1000,151,1000,743,-422,-138,536,-1000,158,-232,1000,222,-818,25,538,-796,869,451,-1000,-86,-44,1000,-343,-293,1000,-1000,744,484,-138,858,-299,250,410,-1000,58,-213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParameters(java.util.List):com.google.javascript.rhino.Node",
            new int[]{-144,4,-952,-1000,160,-222,-1000,-305,-811,258,-245,-155,877,681,-89,147,-1000,-801,545,341,883,-581,-1000,1000,-690,-239,1000,545,-375,651,293,-396,320,728,43,97,-657,1000,-1000,-1000,-195,1000,-59,-1000,1000,526,-924,330,-482,1000,109,1000,-1000,150,-72,1000,145,-210,-194,-821,-233,-1000,626,40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParametersWithVarArgs(com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.Node",
            new int[]{-658,-142,-406,-2,440,935,-423,377,-801,-629,208,-511,-396,565,238,-464,761,982,491,-729,505,768,-867,754,-425,-829,418,897,-304,-776,755,-998,-23,-728,-687,146,101,-360,-278,-493,25,954,930,493,75,461,775,-113,666,78,-617,127,-780,834,-786,318,-719,839,198,-411,-682,54,250,-967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParametersWithVarArgs(java.util.List):com.google.javascript.rhino.Node",
            new int[]{-901,-1000,-72,-1000,1000,1000,-886,744,-960,940,1000,474,1000,1000,1000,-620,-1000,387,-1000,-1000,373,1000,39,1000,-1000,-820,-219,1000,935,-942,1000,557,371,-949,-1000,-507,-1000,-1000,1000,-488,-1000,81,-1000,1000,565,-1000,-923,-446,1000,-525,197,434,1000,-1000,682,1000,-1000,-481,197,-1000,234,1000,-1000,-889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.RecordType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createRecordType(java.util.Map):com.google.javascript.rhino.jstype.RecordType",
            new int[]{-253,534,-187,-725,641,-1000,-587,-1000,-658,-18,141,-1000,-1000,-633,796,975,422,768,434,575,-74,-1000,732,93,-934,345,-463,100,-421,273,590,403,1000,-841,-65,-431,1000,-218,1000,1000,565,1000,-1000,-462,681,629,155,-1000,1000,736,656,768,-1000,-143,-482,1000,-990,270,-1000,410,606,-729,-72,694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createRecordType(java.util.Map):com.google.javascript.rhino.jstype.RecordType",
            new int[]{991,-604,-556,-901,-722,-699,-552,-684,864,-1000,652,113,-1000,-1000,1000,383,-322,-477,565,-814,-640,-1000,-279,964,-75,-34,-557,239,-74,-244,-964,-873,1000,-1000,308,819,676,-378,-212,-51,218,-342,-728,-618,671,-161,-713,-464,205,689,615,441,188,-182,722,-313,-769,-989,-1000,1000,142,225,-792,-958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.PrototypeObjectType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{-984,244,953,-642,-1000,-121,1000,102,459,753,1000,-1000,1000,-208,1000,-914,-587,-1000,-711,205,1000,-676,-121,-222,-351,-706,-560,344,-221,-427,749,643,-1000,58,-258,-164,-1000,799,1000,-1000,-654,508,-1000,-34,1000,155,567,1000,1000,46,-70,1000,-509,884,-1000,413,-196,589,-45,-934,282,1000,818,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.InstanceObjectType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{-380,-1000,163,-846,750,601,-205,354,-707,-231,404,417,-891,787,-1000,834,-1000,1000,1000,821,-661,1000,-584,270,211,-639,461,-309,1000,1000,1000,232,1000,-1000,1000,681,986,-1000,-672,736,-145,-1000,49,-377,302,623,-1000,233,623,-446,-1000,-817,455,-316,531,136,-1000,-1000,665,-1000,-1000,-533,893,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{-1000,-44,-922,1000,937,-931,753,20,-858,729,-94,1000,708,-904,1000,-959,300,1000,1000,782,583,-562,-167,175,728,453,-696,1000,-978,-1000,-1000,257,-216,827,-304,-117,-942,-760,-717,-155,1000,-1000,-1000,416,1000,-760,601,917,869,-961,744,515,-258,-110,1000,1000,188,1000,-1000,-497,-550,-830,-338,220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "declareType(java.lang.String,com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{857,76,959,-338,-695,-63,-236,789,-433,947,419,-127,487,421,-833,587,-443,-1000,-1000,-749,231,-669,819,191,410,-520,101,-528,51,551,282,-482,233,173,-373,297,860,440,-649,459,-682,-989,-970,418,388,-429,740,494,666,-792,-936,-811,-826,-1000,180,-473,-580,-564,87,-1000,-818,928,259,-966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "declareType(java.lang.String,com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{-13,-1000,209,-1000,-349,866,926,190,336,593,357,1000,-140,-210,-1000,279,-1000,1000,-821,27,119,-158,726,-983,82,-496,318,-1000,509,1000,-818,-1000,1000,-779,242,1000,604,-141,-758,530,-560,-1000,1000,-72,485,443,841,1000,1000,-1000,-1000,338,570,807,-1000,-757,1000,205,286,-1000,-374,587,890,117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "forwardDeclareType(java.lang.String):void",
            new int[]{-870,698,-22,-823,-697,223,842,865,0,-459,229,376,-320,-276,-373,653,-957,164,-122,-327,-810,-521,-551,-859,481,-564,212,-789,-857,-580,403,-946,-965,733,229,489,-322,-407,-312,129,619,-929,542,544,555,-571,-884,779,-801,-721,-377,-754,870,878,-964,-518,969,-556,186,-442,114,856,-258,-988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getDirectImplementors(com.google.javascript.rhino.jstype.ObjectType):java.util.Collection",
            new int[]{510,-488,-362,927,-468,-1000,-1000,813,794,-196,-533,-287,-444,-308,-1000,-522,-255,-679,-804,1000,-839,349,979,-9,612,489,411,465,-184,-253,210,-608,921,-90,-114,1000,-109,-876,35,-259,247,-281,-385,-742,-1000,926,-20,52,-335,-379,-623,136,444,-883,348,-444,-591,-259,225,1000,311,-1000,387,525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:com.google.common.collect.EmptyImmutableList", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getEachReferenceTypeWithProperty(java.lang.String):java.lang.Iterable",
            new int[]{-4,-1000,139,1,-775,987,-1000,-364,1000,-1000,-669,286,-136,348,134,1000,-535,232,-772,-673,-1000,-35,-761,-1000,-790,-1000,700,209,867,-438,993,-1000,595,-523,-41,-188,1000,-1000,-217,552,624,-199,-439,691,398,602,-1000,40,-826,692,1000,691,967,-880,-894,851,-1000,-857,-963,-981,-122,852,-702,562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getErrorReporter():com.google.javascript.rhino.ErrorReporter",
            new int[]{599,758,-187,1000,-228,-343,659,165,-1000,978,662,278,-417,-729,-841,-136,-1000,-1000,-573,-467,-136,-1000,-578,285,713,-113,803,-888,924,-980,-781,1000,-948,582,1000,1000,-912,-250,-966,-169,-763,955,693,1000,-323,115,143,-964,-541,863,-1000,793,4,953,-333,307,-1000,-235,13,594,-427,991,296,-644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.NoType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getGreatestSubtypeWithProperty(com.google.javascript.rhino.jstype.JSType,java.lang.String):com.google.javascript.rhino.jstype.JSType",
            new int[]{-375,-410,-941,858,728,-744,799,419,-430,992,-57,892,-179,-78,-826,-186,480,-529,186,-638,42,136,-641,190,284,-244,196,727,984,-499,-273,473,504,950,-267,214,-551,-232,925,555,-676,743,-989,650,-470,-748,-35,-358,-110,-441,-243,-102,398,809,-705,-93,917,-974,408,-589,-880,14,-692,202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getNativeFunctionType(com.google.javascript.rhino.jstype.JSTypeNative):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-1000,-744,-606,234,-1000,-851,1000,-258,572,-760,1000,84,273,1000,844,-767,32,-692,6,-1000,1000,-137,514,-1000,-40,-1000,630,1000,-209,-208,-145,379,1000,-217,1000,-1000,-1000,-725,-774,461,246,-186,-1000,-420,67,-365,-323,-981,29,393,-750,-991,1000,-1000,250,-788,1000,711,-1000,-434,-10,1000,180,884}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.InstanceObjectType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getNativeObjectType(com.google.javascript.rhino.jstype.JSTypeNative):com.google.javascript.rhino.jstype.ObjectType",
            new int[]{-521,-410,-451,-562,-536,240,-887,357,943,-880,45,686,-22,81,-945,347,-256,356,894,415,-278,830,-358,-385,-77,-499,480,962,334,-458,-975,-686,793,393,1,-475,-686,-306,485,-851,-465,-538,-741,-810,376,-84,920,-739,-703,625,-733,-289,588,-774,-712,757,6,875,532,-533,802,807,-931,-428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.PrototypeObjectType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getNativeType(com.google.javascript.rhino.jstype.JSTypeNative):com.google.javascript.rhino.jstype.JSType",
            new int[]{-41,24,168,322,708,38,-873,-387,975,-693,-487,674,1000,14,47,728,830,-714,848,487,-466,-515,622,-500,-860,568,-37,-113,543,-687,906,-926,16,-505,724,81,-280,268,97,725,-320,931,252,717,22,-980,447,17,999,-742,257,-932,283,-747,530,-447,340,50,573,-823,-700,-353,-820,-348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.NamedType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getType(com.google.javascript.rhino.jstype.StaticScope,java.lang.String,java.lang.String,int,int):com.google.javascript.rhino.jstype.JSType",
            new int[]{401,56,-836,-744,387,-481,-655,279,636,565,-587,-742,-814,-1000,108,919,899,20,119,-129,-18,-349,120,-21,326,-705,74,-308,417,774,457,564,835,46,540,-492,659,166,-536,-748,434,-7,482,-65,-203,659,-550,-195,-645,850,-591,588,925,618,-63,-749,-114,-397,843,594,64,-701,721,413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.TemplateType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getType(com.google.javascript.rhino.jstype.StaticScope,java.lang.String,java.lang.String,int,int):com.google.javascript.rhino.jstype.JSType",
            new int[]{731,-986,64,-1000,1000,-1000,-956,977,102,-1000,1000,-1000,-1000,-936,-258,840,1000,-1000,1000,-1000,1000,-1000,1000,-1000,-1000,-698,-1000,-1000,-760,749,-1000,53,-712,-311,1000,-872,-1000,-1000,-90,-909,-1000,488,-92,1000,-200,1000,-1000,822,1000,1000,1000,-142,-608,-113,432,-1000,381,-226,297,943,-1000,-774,-1000,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getType(java.lang.String):com.google.javascript.rhino.jstype.JSType",
            new int[]{-753,-298,-567,496,269,206,-602,798,876,229,-366,161,-860,507,-841,591,80,27,-63,348,-154,648,-794,803,-138,754,-145,-578,981,-526,718,-276,-863,-432,-996,827,-208,540,776,735,532,-273,382,946,-424,172,46,-872,699,-48,441,-373,42,271,22,404,-318,-860,-898,-388,436,-281,243,-495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.TemplateType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getType(java.lang.String):com.google.javascript.rhino.jstype.JSType",
            new int[]{-647,-1000,414,-437,1000,27,-747,-1000,888,642,-702,-1000,963,1000,-1000,745,250,-1000,-1000,-1000,-247,-1000,-673,115,-1000,679,151,-1000,513,-512,460,-1000,1000,-313,-1000,-227,652,-884,448,991,268,-463,242,731,-1000,585,-72,-1000,855,246,353,134,1000,595,-1000,144,-1000,104,936,48,1000,-556,348,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:com.google.common.collect.EmptyImmutableList", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getTypesWithProperty(java.lang.String):java.lang.Iterable",
            new int[]{-886,-392,334,-155,207,819,-296,-824,-504,62,383,-423,264,-455,-397,922,-697,-81,-209,53,-363,-902,-678,281,-538,750,143,-93,741,-20,924,205,348,239,98,-612,-903,-428,920,-364,-407,-75,492,-546,404,437,622,-681,-231,380,312,884,757,-262,-309,845,-386,-717,-682,-37,-102,-549,880,-250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "hasNamespace(java.lang.String):boolean",
            new int[]{-891,988,334,257,426,332,328,-572,287,807,-372,284,-826,670,270,-867,382,245,133,98,594,-319,726,862,-967,369,-537,-300,-620,686,400,407,892,462,245,362,-872,569,118,893,534,389,32,347,774,558,333,848,118,-645,475,-445,486,358,377,468,-200,613,-620,-738,200,431,334,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "identifyNonNullableName(java.lang.String):void",
            new int[]{262,-328,183,-686,558,140,-589,47,-709,-849,328,-484,56,221,810,60,-346,452,283,-560,-721,-471,325,-410,-122,339,-370,-76,-563,-80,-97,241,-627,47,-419,7,996,184,-18,380,-899,-259,-526,729,138,-349,-434,586,-869,-905,420,982,545,-96,248,-45,-386,612,-717,-375,331,468,562,713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "incrementGeneration():void",
            new int[]{-173,-856,-352,-210,644,979,-616,536,-806,969,-831,-810,-932,-224,726,994,986,-65,-105,-337,262,701,291,210,183,23,24,542,-637,14,291,-110,159,846,929,-742,-757,134,332,707,560,542,-461,541,-915,-380,754,231,981,687,-930,240,137,-65,760,-140,331,907,121,612,782,955,-403,-206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "isForwardDeclaredType(java.lang.String):boolean",
            new int[]{301,454,-406,-1000,843,-1000,-103,982,-919,325,-1000,128,1000,520,-1000,-108,-5,622,154,-620,369,66,-803,-975,102,-1000,808,-232,-392,-154,-1000,-690,276,446,-728,480,869,-380,-420,-1000,-620,-154,1000,-478,990,-20,-1000,38,1000,-277,-160,-1000,-1000,840,1000,673,1000,430,454,-911,126,560,-455,723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "overwriteDeclaredType(java.lang.String,com.google.javascript.rhino.jstype.JSType):void",
            new int[]{-264,97,1000,-941,-164,-1000,-1000,577,132,-165,-700,1000,1000,1000,-1000,702,-891,-165,-1000,286,447,1000,-697,-1000,349,-673,-797,1000,-770,-1000,454,1000,-1000,-1000,935,-280,-589,-176,601,432,-652,-1000,-1000,-95,680,-91,850,-1000,1000,-1000,1000,1000,1000,357,1000,-448,-508,-556,1000,997,863,852,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "overwriteDeclaredType(java.lang.String,com.google.javascript.rhino.jstype.JSType):void",
            new int[]{-600,626,658,-122,572,-814,6,982,1000,-500,318,967,494,-855,-886,273,-936,-995,33,117,91,-441,126,-109,528,-384,-164,-220,389,-856,98,785,-690,-687,148,-157,-709,750,-394,410,-143,-863,-804,962,823,-249,74,-747,604,-690,140,807,604,602,935,160,563,733,794,645,-12,-405,734,-856}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "registerPropertyOnType(java.lang.String,com.google.javascript.rhino.jstype.JSType):void",
            new int[]{-947,1000,389,698,-149,591,-22,-123,821,-1000,-81,129,-358,-321,-746,211,525,-536,-616,-970,-1000,-1000,-481,-607,-1000,-1000,769,-719,825,1000,-176,-320,225,-824,-945,-1000,-209,-8,-952,1000,-674,-1000,-1000,-381,296,1000,-343,-157,-448,158,-191,635,-798,-321,1000,25,518,321,-170,437,61,-185,-767,-921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "resetForTypeCheck():void",
            new int[]{839,-194,3,906,-618,145,-44,-346,733,-883,148,-946,235,-858,-989,-430,-185,-951,908,-739,-779,690,-176,537,805,-809,430,-714,-375,-923,140,-368,-286,595,-91,-75,-949,-379,-796,-43,-40,762,-169,-151,471,452,127,-434,-501,-816,-614,619,638,-654,335,122,-210,-426,997,117,934,11,291,-200}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "resetImplicitPrototype(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.ObjectType):boolean",
            new int[]{833,142,419,-702,889,-943,642,356,-766,682,-154,631,822,-418,106,-580,-579,118,629,373,-683,-631,915,402,-324,-412,653,-517,-788,-646,-841,-641,-450,-717,944,109,942,-116,-704,552,-146,-64,647,-444,327,-350,930,453,18,-223,-856,-44,-673,-174,885,272,134,-405,-234,398,-673,334,-711,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "resolveTypesInScope(com.google.javascript.rhino.jstype.StaticScope):void",
            new int[]{-28,-638,669,-576,857,-302,707,120,749,122,113,413,-456,510,528,56,15,-15,-266,374,991,-100,-982,701,828,-392,-272,-92,86,-598,-237,412,-627,-942,869,390,18,-503,-699,313,-569,-695,-340,377,-201,-973,527,-89,109,515,908,436,-595,-92,420,695,-351,-711,-334,646,-412,-457,309,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "setLastGeneration(boolean):void",
            new int[]{-973,-858,359,-731,-78,-423,357,437,380,-832,524,750,-566,914,275,174,505,-382,-585,-67,-385,623,815,-904,-21,255,238,373,850,-19,174,733,241,862,84,69,976,67,101,322,-569,916,851,253,257,219,850,-676,424,-508,699,71,-23,89,163,-246,-944,215,162,-903,933,333,663,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "setResolveMode(com.google.javascript.rhino.jstype.JSTypeRegistry$ResolveMode):void",
            new int[]{-21,-1000,-362,0,-850,132,982,29,1000,126,577,-705,-385,0,513,822,-545,392,0,-701,129,-149,-295,620,376,-460,-17,-1000,-1000,0,-176,-435,-437,524,-413,631,-826,-728,242,762,149,898,0,0,-433,0,135,-1000,-328,-286,-535,484,-1000,-949,-466,-817,817,375,-787,-701,90,667,0,-157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "setTemplateTypeName(java.lang.String):void",
            new int[]{418,94,-646,-689,633,76,467,818,-179,478,-698,528,-302,-1000,470,21,617,-291,-112,8,271,-559,133,104,-292,-310,45,17,-86,296,959,325,775,-560,367,-639,-939,-569,-431,846,-784,-474,913,871,209,-310,605,569,-518,543,747,412,-908,-949,-746,-568,-407,479,614,-117,-143,-956,59,858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "shouldTolerateUndefinedValues():boolean",
            new int[]{-347,882,-447,-929,-381,-951,-656,-934,42,-659,-451,543,-63,179,-233,-705,-475,761,81,69,-140,191,-298,993,237,197,-625,308,867,576,758,456,-804,379,-84,707,-382,658,-540,-417,309,557,888,616,-543,-521,-746,-814,251,149,398,547,-373,476,685,-477,43,300,423,844,275,346,974,-454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "unregisterPropertyOnType(java.lang.String,com.google.javascript.rhino.jstype.JSType):void",
            new int[]{-457,-782,309,687,507,-632,-255,780,-82,809,320,1000,-50,315,-69,630,862,58,-392,-599,803,-792,55,-552,-632,-1000,-109,-678,-198,124,673,1000,-618,-391,-792,-111,-921,361,464,-172,550,-34,-220,912,-852,81,-464,-169,-737,-985,-648,769,-1000,-984,-899,-523,214,211,507,981,-70,760,-743,-302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.JSTypeExpression", "com.google.javascript.rhino.JSTypeExpression", "evaluate(com.google.javascript.rhino.jstype.StaticScope,com.google.javascript.rhino.jstype.JSTypeRegistry):com.google.javascript.rhino.jstype.JSType",
            new int[]{466,889,370,-301,566,-540,508,895,9,-841,-208,-657,451,-834,-36,191,98,946,97,-237,-94,-327,978,-665,48,625,873,-742,-162,-519,-966,805,-717,-356,-163,128,-988,-21,-467,743,-554,704,-735,91,-234,-476,201,-124,499,612,-548,-871,344,-504,748,-746,230,-458,-213,-355,-430,336,622,922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.rhino.jstype.ObjectType", "", "cast(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.ObjectType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.String:KG51bGwp", DEReplay.run(
            "com.google.javascript.rhino.jstype.ObjectType", "", "createDelegateSuffix(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.JSTypeExpression", "com.google.javascript.rhino.JSTypeExpression", "evaluate(com.google.javascript.rhino.jstype.StaticScope,com.google.javascript.rhino.jstype.JSTypeRegistry):com.google.javascript.rhino.jstype.JSType",
            new int[]{466,889,370,-301,566,-540,508,895,9,-841,-208,-657,451,-834,-36,191,98,946,97,-237,-94,-327,978,-665,48,625,873,-742,-162,-519,-966,805,-717,-356,-163,128,-988,-21,-467,743,-554,704,-735,91,-234,-476,201,-124,499,612,-548,-871,344,-504,748,-746,230,-458,-213,-355,-430,336,622,922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionParamBuilder", "com.google.javascript.rhino.jstype.FunctionParamBuilder", "addOptionalParams(com.google.javascript.rhino.jstype.JSType[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionParamBuilder", "com.google.javascript.rhino.jstype.FunctionParamBuilder", "addVarArgs(com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "canPropertyBeDefined(com.google.javascript.rhino.jstype.JSType,java.lang.String):boolean",
            new int[]{193,-440,374,473,563,-329,790,-144,-408,438,-568,-638,603,-489,481,-513,65,298,906,81,763,12,-498,377,991,-492,381,-681,-454,368,-758,811,445,219,245,-338,680,547,-838,-11,-876,462,628,624,691,773,-226,-330,-703,-492,-697,793,-228,589,157,-976,941,-253,-675,-74,-653,-28,44,809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-972,828,99,1000,1000,-1000,-1000,-247,-799,748,-342,511,-827,177,-979,-41,673,-1000,11,816,-1000,107,389,-499,1000,-1000,50,261,-836,-291,326,107,1000,1000,-707,807,1000,-273,-1000,558,-921,237,352,-293,764,598,-1000,-547,1000,-303,-72,-543,1000,-174,669,-742,-17,-505,-746,-158,-1000,295,1000,-327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-73,-1000,629,702,-198,402,234,-1000,-1000,1000,88,-530,1000,-221,-1000,846,1000,-1000,923,983,-1000,-151,142,-1000,77,-206,-483,-1000,597,-633,-247,-1000,-259,1000,847,-372,793,-1000,148,-1000,785,621,-1000,586,-1000,-298,97,1000,1000,-1000,-1000,288,454,-671,377,-1000,446,-324,209,75,187,-871,-737,281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{440,-794,974,794,-806,-538,605,-158,-900,950,672,-245,906,57,-797,824,203,-42,296,-187,327,85,932,-833,184,-702,-438,475,-811,75,895,-754,-798,815,741,-294,965,-587,981,-660,-87,-280,-207,-803,-585,-854,100,-400,541,753,-129,-173,238,469,-586,596,469,-255,-958,-293,-714,-80,-356,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-677,-174,744,-647,-85,-168,-484,428,568,283,-656,-740,224,331,227,229,985,860,-601,739,394,-579,-314,112,255,372,-751,788,978,715,-924,454,834,648,498,-931,-561,527,-530,-792,278,643,-586,-389,165,944,-13,374,131,726,-688,-159,462,-853,-68,-74,317,413,18,316,-290,493,-778,831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-1000,-664,-776,-137,365,-1000,831,627,281,-413,742,-1000,-170,303,-352,789,1000,-1000,-1000,-1000,-741,400,-389,494,501,-791,-306,-684,270,1000,513,-98,348,783,-431,1000,891,-320,1000,279,732,1000,-404,643,-203,313,-1000,-294,90,-763,-937,-23,980,207,-431,805,773,-468,542,-118,-161,-1000,349,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-264,-392,-426,1000,63,812,-1000,1000,899,-549,-1000,-948,-984,-426,-153,714,-129,1000,182,281,1000,411,1000,1000,173,1000,-558,-252,-876,390,19,1000,1000,-1000,576,588,562,-1000,-1000,-24,676,-507,298,670,-620,-1000,538,-267,-119,947,1000,303,-1000,-299,1000,515,-1000,1000,-385,-97,-36,1000,1000,-371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createDefaultObjectUnion(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-974,251,1000,-746,-707,1000,600,-272,-1000,-136,1000,218,1000,-906,118,-168,1000,1000,-685,-544,-606,-447,645,-1000,120,-965,-1000,1000,-134,492,1000,-587,965,-1000,319,1000,-1000,1000,-1000,806,-11,1000,982,-62,1000,-772,521,-549,530,-596,-814,-966,-1000,484,-1000,1000,173,1000,-773,1000,-333,-608,794,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createDefaultObjectUnion(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{652,-464,488,-233,443,990,217,17,-973,478,983,-584,-322,-795,-743,-547,-857,567,4,-459,-380,172,-928,-777,632,608,227,339,158,-11,566,567,19,-974,611,609,378,-94,-880,39,458,-363,-180,-698,-917,652,-322,-322,-507,484,67,-808,49,66,-738,351,-127,858,-961,-615,287,396,-148,236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope):com.google.javascript.rhino.jstype.JSType",
            new int[]{-301,26,228,-1000,-1000,105,873,-1000,1000,-867,609,562,-1000,622,1000,-968,-220,602,186,-663,519,-399,-1000,-626,1000,341,-28,59,627,-277,75,677,-264,905,-169,1000,637,341,-129,-367,-129,248,234,88,210,-572,-537,-1000,-164,-422,243,-12,-231,-668,611,1000,-895,1000,360,-396,-1000,-355,1000,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope):com.google.javascript.rhino.jstype.JSType",
            new int[]{298,-636,22,-1000,-812,-1000,418,-783,176,0,-96,509,-137,-144,1000,-225,770,540,243,1000,1000,126,-679,-758,559,-478,-736,350,624,-877,104,1000,334,520,-555,518,797,-629,261,-59,1000,-521,38,0,96,-202,1000,-733,-662,-361,299,309,442,-974,727,286,-148,1000,633,-1000,-287,-694,91,717}));
    }
}
