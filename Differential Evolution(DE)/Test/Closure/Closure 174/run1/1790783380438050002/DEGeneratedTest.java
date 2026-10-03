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
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "clearAst():void",
            new int[]{613,-378,-726,-712,-651,-5,-189,982,-992,-659,991,-233,111,142,839,-487,378,-696,204,950,-855,-254,-862,423,-640,-630,218,120,-239,-270,-283,219,659,858,-920,-402,-353,-543,-102,-512,508,918,-733,-222,-382,-497,-777,553,-792,-593,-159,-115,7,413,448,895,990,-639,-550,678,-801,771,273,800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "clearAst():void",
            new int[]{-327,804,545,-54,936,-318,-775,-697,-963,-558,329,358,-862,657,385,-895,-564,-188,-451,621,948,838,-25,-296,845,554,-90,-824,278,422,234,-91,-524,178,772,-211,769,637,-600,-395,157,-782,-325,168,58,642,542,829,-32,582,518,999,-143,100,681,-686,-594,-824,-221,-406,380,749,824,928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "clearAst():void",
            new int[]{-181,377,-354,866,103,785,46,-28,375,-585,-546,805,385,390,-750,761,325,241,-689,160,935,-792,747,458,814,948,282,528,659,-894,278,-412,-997,-675,31,-469,868,31,612,-821,-245,591,-707,286,377,-721,367,-11,639,-84,-935,868,493,-988,162,-256,526,756,887,-58,392,-674,758,849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "clearAst():void",
            new int[]{966,-820,557,-139,822,429,741,622,293,778,-835,-985,284,26,110,-673,-345,-120,501,602,757,448,911,424,995,-336,-435,-931,-301,148,-109,-337,419,160,-192,176,-342,22,-392,810,-11,684,994,-305,549,932,701,648,969,161,-94,-794,-66,601,-371,-864,782,413,275,-788,-169,-818,511,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "clearAst():void",
            new int[]{128,-225,53,-418,-193,-316,533,178,-992,214,-782,1000,-307,142,-888,-487,378,-539,-259,85,-855,-254,1000,423,784,532,218,-743,1000,235,-283,-684,-553,-613,677,-285,-431,466,1000,694,-511,958,355,-1000,165,-497,-567,188,-306,-593,-361,1000,-300,-1000,1000,447,-1000,855,-145,678,-704,-1000,273,778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getAstRoot(com.google.javascript.jscomp.AbstractCompiler):com.google.javascript.rhino.Node",
            new int[]{143,179,-825,826,927,203,303,-589,834,-904,159,432,-686,703,21,128,551,847,-504,-151,-320,-595,677,430,911,-63,232,-955,-731,356,-76,-968,-745,-272,-103,286,322,-494,964,-405,723,235,57,539,60,-899,-948,-566,612,-456,-746,-796,441,-374,-64,-961,-867,825,-409,238,82,-505,767,-298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getAstRoot(com.google.javascript.jscomp.AbstractCompiler):com.google.javascript.rhino.Node",
            new int[]{-783,-629,-284,251,-773,-828,478,-903,487,611,-536,647,-867,941,-869,-883,-962,543,-81,361,-221,-10,256,793,-843,536,-846,292,253,-964,675,-382,-386,937,391,970,728,-842,827,-909,-346,438,-130,-439,-615,52,-897,808,331,643,149,743,183,-600,192,-92,-693,-221,-877,561,-147,-459,-50,-786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getAstRoot(com.google.javascript.jscomp.AbstractCompiler):com.google.javascript.rhino.Node",
            new int[]{-241,9,-500,801,995,145,-200,-130,516,-733,-3,-25,667,87,174,805,-933,-914,28,-532,347,209,993,-158,-427,353,110,-65,-356,-821,-293,-229,506,-566,784,-489,657,-882,-104,-792,-940,-381,-727,-104,344,224,-61,557,-41,-671,-335,902,805,55,-242,-96,803,-961,850,519,-162,-311,156,153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getAstRoot(com.google.javascript.jscomp.AbstractCompiler):com.google.javascript.rhino.Node",
            new int[]{142,-827,827,-987,128,-446,-351,146,-119,149,-302,353,514,-236,-74,289,95,-259,-896,341,968,-172,978,523,548,84,982,-118,422,408,140,37,190,938,-389,567,-937,-287,-764,-241,-106,798,-618,450,-893,331,-583,-430,-279,-122,212,-591,-412,-132,214,572,842,943,852,-848,-432,-430,-715,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getAstRoot(com.google.javascript.jscomp.AbstractCompiler):com.google.javascript.rhino.Node",
            new int[]{750,412,841,-559,636,-349,-104,684,894,688,377,606,85,486,-384,-875,370,-841,-156,921,647,-597,252,-562,-201,-187,-722,-93,-822,-764,-481,38,29,-87,503,-864,-845,-641,-532,-825,788,684,-784,604,397,378,-835,-482,927,-560,766,-752,-139,193,-367,366,381,-882,-109,672,-73,-394,-428,785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.InputId", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getInputId():com.google.javascript.rhino.InputId",
            new int[]{-860,-222,89,456,-922,540,-197,-175,-50,-957,298,-100,39,746,935,633,-302,297,-903,-452,-325,-763,-568,-974,549,-988,-632,378,930,-227,778,402,-637,882,-51,779,588,-660,-490,-893,708,-528,894,627,134,-902,71,-240,-239,168,765,479,698,-633,214,226,-873,546,617,-422,-625,23,858,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.InputId", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getInputId():com.google.javascript.rhino.InputId",
            new int[]{-654,-673,919,-14,-197,-681,259,-384,425,634,-502,22,-397,-892,524,-642,673,91,-181,102,134,-798,-109,-856,762,196,382,317,-965,684,-429,426,-329,-26,-799,416,-303,-461,-865,-205,449,-234,-792,682,174,541,-176,-87,482,-67,330,-465,817,820,-958,-286,307,-386,865,-421,-102,447,-73,866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.InputId", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getInputId():com.google.javascript.rhino.InputId",
            new int[]{887,-512,126,241,-867,153,-733,-98,-106,-437,191,891,-708,-782,478,810,-42,-963,-558,-611,-675,873,415,-726,196,524,601,-456,-264,511,-601,-42,-595,297,-627,-445,966,-25,579,689,692,-480,831,827,-851,620,-313,-463,-455,-146,397,939,-493,485,-93,620,-615,-466,78,516,692,928,-231,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.InputId", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getInputId():com.google.javascript.rhino.InputId",
            new int[]{579,-547,-431,-59,-449,722,381,714,678,0,-737,300,128,900,808,70,459,-316,-787,119,437,97,122,586,-988,628,418,485,331,-104,-921,999,18,-651,491,837,577,734,-857,-291,-252,935,-477,211,-54,633,-816,513,-565,46,479,93,885,479,-767,499,68,814,781,590,-934,-793,-811,-941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.InputId", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getInputId():com.google.javascript.rhino.InputId",
            new int[]{-1000,666,238,-307,509,288,194,181,-686,476,996,-297,-97,303,-1000,700,1000,557,-126,-182,-308,-501,-1,-1000,-1000,176,-1000,411,-334,-264,379,-547,-528,802,40,-379,-856,-372,-426,-1000,454,-907,385,1000,-148,586,-222,-124,391,77,1000,163,911,904,348,414,773,-603,14,22,-105,934,-779,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$Preloaded", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getSourceFile():com.google.javascript.jscomp.SourceFile",
            new int[]{310,372,-650,157,-966,-53,-803,-669,305,678,633,467,639,355,-369,107,-693,537,767,-716,-689,803,480,-622,-406,-861,-760,-465,102,906,-7,781,338,-621,541,-922,87,969,-182,-788,87,-55,434,-382,616,716,304,-945,-812,541,709,-627,862,-66,700,507,6,-61,413,341,817,982,549,-910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$Preloaded", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getSourceFile():com.google.javascript.jscomp.SourceFile",
            new int[]{-655,-944,77,-552,355,-387,997,-85,-60,-243,-60,-564,-502,-951,-693,759,407,570,-321,-218,-474,762,311,541,-462,-993,-346,491,-956,-105,-926,251,-685,708,179,65,-333,-219,-478,-78,-470,66,-182,-525,430,497,285,-315,-598,-432,222,467,568,899,-471,-480,56,-482,-486,-999,799,86,-978,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$Preloaded", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getSourceFile():com.google.javascript.jscomp.SourceFile",
            new int[]{401,-596,-869,-122,854,-526,752,931,224,-555,624,-734,732,344,55,105,712,899,962,332,-289,-7,117,-68,-849,-468,-803,672,22,-259,-682,254,-919,470,76,284,84,-759,393,-974,675,-578,-119,-833,739,188,894,572,254,-259,945,372,-5,636,754,-364,780,992,831,-675,385,-122,-790,304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$Preloaded", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getSourceFile():com.google.javascript.jscomp.SourceFile",
            new int[]{-984,-719,-560,-968,128,614,918,3,223,862,-192,460,-941,-864,-801,-274,-780,337,574,-759,880,787,884,7,841,949,-833,233,-111,306,985,-530,507,-938,224,-368,22,433,-848,-40,385,-11,991,191,936,782,742,-13,787,766,-495,-969,690,442,-884,-909,685,-435,-425,835,-808,258,-569,205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$Preloaded", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "getSourceFile():com.google.javascript.jscomp.SourceFile",
            new int[]{-262,-41,-930,-237,-972,-765,671,-427,704,699,508,-99,799,8,-816,310,-379,240,-522,563,620,832,-524,156,-358,-320,880,-726,-111,26,531,509,911,316,-498,-256,-17,-681,400,303,-648,525,-56,661,992,-946,328,795,-238,-853,-615,-379,140,680,172,-160,-911,-927,-784,-984,16,486,-627,722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "setSourceFile(com.google.javascript.jscomp.SourceFile):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "setSourceFile(com.google.javascript.jscomp.SourceFile):void",
            new int[]{-374,-199,-679,-131,135,605,-321,217,475,222,-32,647,-681,743,46,-704,-681,681,-137,944,166,-369,-452,2,-523,-16,405,2,694,-987,569,-957,-513,643,-74,393,8,315,322,370,-197,485,-984,-500,-805,515,-77,-52,-515,968,230,298,489,662,-323,-185,-422,-781,-25,546,-336,308,652,823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "setSourceFile(com.google.javascript.jscomp.SourceFile):void",
            new int[]{382,-704,919,40,-903,-399,-881,-61,296,682,-279,57,-554,-692,917,485,437,520,-572,-382,387,-483,-680,208,-263,603,329,-300,-637,284,474,550,-303,613,-386,-951,-226,-26,972,-980,511,-371,643,-305,576,-441,647,-969,737,407,-856,834,-289,-364,138,96,-766,-556,170,191,329,-596,-684,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "setSourceFile(com.google.javascript.jscomp.SourceFile):void",
            new int[]{591,-6,-985,927,505,-700,636,435,-242,751,445,932,-710,841,-948,247,-844,-943,498,888,-492,-189,-915,16,-889,-573,-58,69,814,-235,-686,-303,-286,26,-555,520,-376,417,308,24,895,-537,411,865,-357,953,-545,-146,-997,814,690,-106,-843,494,505,-700,562,-152,977,-913,68,976,-92,38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.JsAst", "com.google.javascript.jscomp.JsAst", "setSourceFile(com.google.javascript.jscomp.SourceFile):void",
            new int[]{1000,623,-130,-316,38,-176,-1000,-395,-22,1000,1000,-221,-849,-662,-986,968,-1000,-661,377,253,943,-178,-999,291,111,-780,1000,-930,-1000,831,-1000,-673,-1000,-1000,114,142,-748,-596,644,-479,234,-858,-457,1000,-345,-74,1000,-743,-151,704,237,333,-695,866,178,601,1000,-726,-747,1000,-185,559,-548,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{-165,9,-804,-857,838,-766,-413,-774,960,-730,83,-31,890,76,546,-410,488,507,430,-99,-815,-78,830,866,760,-275,-854,-750,-477,-877,335,691,-402,-165,-836,-8,-300,-447,-636,-460,559,-496,100,344,984,-317,-153,402,-553,-25,367,845,-969,734,-735,-376,580,186,383,-614,6,460,-840,618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{116,-561,-896,489,832,883,-121,-329,160,757,254,-696,-489,-342,45,-515,538,246,-737,-66,-160,309,709,42,170,93,844,123,741,233,-529,250,-117,-900,-802,568,9,-588,-859,14,-399,-471,-126,-972,-221,-943,224,14,-104,-947,-775,-938,-452,446,235,-233,-568,124,-944,843,533,525,-270,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{5,-783,-1000,-263,-47,340,-464,998,221,228,-189,854,28,473,506,487,-1000,178,-639,-223,-999,172,35,-136,-862,807,-917,1000,-102,-1000,1000,-339,299,546,-1000,124,790,1000,-54,-116,221,-1000,702,102,491,-976,437,-1000,644,488,-351,1000,-1000,-270,259,-97,-773,188,196,-288,158,489,403,-336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{120,837,314,-689,-1000,-257,-61,-1000,730,-1000,1000,-411,33,-351,-272,812,-1000,456,84,-358,934,-45,1000,169,-361,-927,-708,-397,684,1000,1000,1000,-137,846,1000,-130,397,-1000,-63,830,-554,1000,-299,-124,731,804,694,1000,-492,1000,656,-534,1000,-126,-1000,-141,146,900,1000,823,383,-178,1000,421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{458,-346,-15,-886,-288,-208,-238,-191,160,802,-50,358,-18,-883,-301,411,968,-60,452,200,-612,199,571,32,982,519,-895,-838,-409,973,667,-683,-676,918,-159,61,-843,98,-206,-513,879,376,-145,306,-64,951,695,306,338,721,-561,-645,-898,-427,556,669,889,-183,-297,-119,-538,-285,804,-391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{-592,-870,-359,-310,-883,661,-62,2,503,345,538,-87,97,-20,626,-236,262,427,174,-469,64,-115,769,-169,-943,-945,-179,758,-324,346,789,712,-751,845,-509,327,728,639,-156,-110,162,-743,-567,219,663,-517,211,886,-844,-373,327,-132,-446,428,-446,-754,171,-788,-814,391,163,-328,-689,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{-23,-265,-345,325,-171,-614,505,838,32,473,-634,-300,-547,-301,-491,-859,261,344,-159,-901,-643,620,-313,-287,-21,590,918,813,110,-728,29,-20,-348,54,149,-250,386,466,281,-241,-299,-505,102,-734,752,165,-430,-641,302,868,-752,359,808,79,-198,500,-486,-482,-581,749,271,549,45,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{-1000,665,-1000,-19,663,-214,1000,-274,-213,88,1000,886,1000,-138,612,565,11,-107,-907,-901,-1000,-168,-592,963,1000,1000,219,-1000,617,-1000,352,209,-581,1000,-757,1000,-819,466,-667,-241,-245,312,-289,1000,752,124,-742,199,-1000,1000,-839,519,-496,-148,-140,458,416,-518,946,-483,-823,1000,-96,-773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{273,-783,-417,458,-38,-40,-726,146,622,-304,-887,420,844,-415,-593,-787,856,-675,817,-30,593,500,0,-159,-858,-837,741,518,-491,-192,596,254,741,744,320,917,371,92,420,-545,-135,96,71,-876,301,-744,326,-24,875,902,44,677,954,-718,-682,532,-565,-55,-727,-229,899,294,-895,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{-190,521,-281,-101,223,774,959,678,142,667,343,-228,696,-177,825,-202,-632,-160,336,-872,961,184,354,916,-656,-640,-723,971,229,321,-68,837,-674,957,-216,-303,-831,-327,-468,361,-420,576,620,436,-656,591,896,28,-32,-70,197,-158,596,-196,-99,380,109,975,681,93,253,-176,597,-208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{18,-622,-550,-917,-835,-630,978,490,409,-823,-296,826,844,-916,212,302,-15,557,-675,-59,182,-21,-657,-788,219,-91,-75,-304,217,-297,-189,8,75,-792,849,490,598,414,-137,-215,178,471,776,-719,451,-760,106,-348,-295,-288,-536,217,952,25,997,718,608,-427,-140,226,-536,757,-249,-625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{1000,-459,1000,556,1000,529,-1000,-461,-1000,131,777,-767,-406,707,-588,-609,-299,-978,-1000,-964,200,-84,-198,-420,286,-326,-1000,-673,-1000,145,-210,-263,-1000,-355,-1000,164,-911,996,818,-48,224,324,1000,900,586,695,1000,626,1000,-735,523,1000,959,-1000,-208,273,1000,-703,-399,818,-161,293,599,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "getFunctionJSDocInfo(com.google.javascript.rhino.Node):com.google.javascript.rhino.JSDocInfo",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "getFunctionParameters(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "getInputId(com.google.javascript.rhino.Node):com.google.javascript.rhino.InputId",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "getNearestFunctionName(com.google.javascript.rhino.Node):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "getSourceFile(com.google.javascript.rhino.Node):com.google.javascript.rhino.jstype.StaticSourceFile",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "getSourceName(com.google.javascript.rhino.Node):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "isLValue(com.google.javascript.rhino.Node):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("ENUM:com.google.javascript.rhino.jstype.TernaryValue$1:FALSE", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "isStrWhiteSpaceChar(int):com.google.javascript.rhino.jstype.TernaryValue",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "isValidQualifiedName(java.lang.String):boolean",
            new int[]{541,-1000,-1000,-820,-612,274,-152,301,575,-765,-722,-450,-429,-1000,-282,743,-10,-927,-266,21,-1000,-688,142,-1000,1000,899,707,-944,75,-706,-603,723,439,823,-472,1000,-551,-559,282,133,844,1000,1000,-297,750,478,-107,-19,403,-336,984,714,468,-419,42,-796,340,506,-164,132,-119,-424,-654,529}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "isValidQualifiedName(java.lang.String):boolean",
            new int[]{971,-365,-734,-54,574,-450,-121,-279,577,-11,622,169,92,510,594,628,659,310,12,997,613,797,-459,-982,-988,-439,438,-65,967,-763,-708,-806,733,415,-829,-637,971,-451,-426,-1,218,630,772,-209,-190,-713,-192,-6,224,-66,-263,886,-851,599,511,804,-546,157,-651,154,669,811,366,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "isValidQualifiedName(java.lang.String):boolean",
            new int[]{-723,608,-310,-441,-575,-118,-18,-1000,252,8,-953,-491,533,377,-215,-517,207,-441,685,-742,-705,-21,-525,120,-485,-566,1000,274,293,532,633,1000,748,511,-450,-105,-771,-126,462,508,939,1000,309,-729,-1000,-223,787,-57,743,-232,1000,529,-1000,121,563,-876,-869,-540,-1000,475,768,1000,-294,295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "isValidQualifiedName(java.lang.String):boolean",
            new int[]{-412,-197,-597,-690,-513,-901,-414,610,94,350,-569,139,358,543,374,-340,-925,-92,-596,996,410,34,-768,-667,-446,-897,-82,805,-221,-610,-328,-802,-352,832,-474,107,191,-710,205,-573,512,908,-341,664,448,898,453,233,672,28,-507,-357,-40,-897,409,270,175,804,-676,272,13,-587,-296,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:java.util.HashMap", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "mapMainToClone(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):java.util.Map",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "newQualifiedNameNode(com.google.javascript.jscomp.CodingConvention,java.lang.String):com.google.javascript.rhino.Node",
            new int[]{286,-646,-371,-701,198,74,381,151,-996,-694,-7,382,878,907,518,930,174,-281,400,-798,849,-949,-848,799,652,-867,-210,262,-19,948,-732,-838,-865,-622,-486,-918,-951,-830,537,-512,-448,891,600,-180,-318,-278,436,-299,669,-288,-888,650,-199,-23,299,-668,604,-841,-589,36,-497,482,378,817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "newQualifiedNameNode(com.google.javascript.jscomp.CodingConvention,java.lang.String):com.google.javascript.rhino.Node",
            new int[]{-83,-1000,873,155,-241,284,993,-894,-858,-957,-647,-718,-218,204,108,-52,138,-305,350,-539,780,585,-40,239,-94,173,-804,-904,-403,639,-54,420,-779,-632,916,983,-239,663,-52,-497,-200,165,110,-463,385,-951,249,748,54,813,-701,-860,947,-724,762,-523,118,368,-443,764,878,162,-356,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "newQualifiedNameNodeDeclaration(com.google.javascript.jscomp.CodingConvention,java.lang.String,com.google.javascript.rhino.Node,com.google.javascript.rhino.JSDocInfo):com.google.javascript.rhino.Node",
            new int[]{-641,-445,995,47,789,-602,703,-764,-18,-453,833,214,-798,-891,626,-827,-366,-446,-626,-494,517,-478,724,-903,756,183,-859,500,-874,-555,994,-287,599,542,-157,-493,-217,283,-180,-889,-58,649,233,-606,-303,253,907,-347,-887,-270,-783,598,601,-904,216,210,-185,-264,843,752,-419,852,-207,425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "newQualifiedNameNodeDeclaration(com.google.javascript.jscomp.CodingConvention,java.lang.String,com.google.javascript.rhino.Node,com.google.javascript.rhino.JSDocInfo):com.google.javascript.rhino.Node",
            new int[]{-244,-111,305,-113,-104,768,-135,205,-390,593,607,640,-430,903,-911,143,293,-489,-7,-569,-10,-485,-660,-947,-916,-855,810,-965,-709,-213,-85,-682,-418,-271,903,-489,-229,-31,541,-742,-981,-237,-797,921,-902,-495,-935,193,927,999,-379,-209,-432,212,219,409,-326,-794,-108,-240,169,85,322,723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "verifyScopeChanges(java.util.Map,com.google.javascript.rhino.Node,boolean,com.google.javascript.jscomp.AbstractCompiler):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CallGraph", "com.google.javascript.jscomp.CallGraph", "process(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.ClosureCodingConvention", "com.google.javascript.jscomp.ClosureCodingConvention", "extractClassNameIfProvide(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.ClosureCodingConvention", "com.google.javascript.jscomp.ClosureCodingConvention", "extractClassNameIfRequire(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.CodePrinter$Builder", "com.google.javascript.jscomp.CodePrinter$Builder", "build():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-169,-145,730,888,520,702,-459,-718,56,544,849,-759,480,557,951,-311,913,-748,-141,-640,-373,671,430,-157,-288,763,533,899,470,334,-970,803,379,653,-875,358,-230,502,941,-692,181,-710,-943,-88,787,-304,192,608,257,-772,-803,887,926,-931,113,772,-729,657,-839,634,343,-494,219,-554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{798,-937,-271,-387,-701,-834,-156,-156,541,695,483,680,563,-499,-431,-976,-172,-586,-557,809,-159,283,644,881,934,15,-294,-27,727,-349,174,902,-539,-410,-443,-801,-820,639,-529,-26,562,-260,-170,-958,92,946,631,73,-771,-515,607,840,234,625,822,522,401,-566,-863,464,-407,510,423,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-623,726,-75,-997,-279,852,-732,741,378,673,560,858,-40,388,695,621,-998,691,791,-174,523,939,448,342,-203,18,-847,-703,-303,-823,999,79,351,315,-962,599,297,10,864,-939,867,881,-734,-132,77,-161,-782,231,971,-798,834,-456,61,-274,-552,676,259,152,499,917,-437,82,534,539}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-886,798,-744,694,-829,421,-447,47,630,99,748,262,818,442,738,302,665,-261,657,944,473,841,-306,771,520,989,-717,376,-441,35,517,169,634,737,796,388,89,-417,990,776,181,-37,658,-276,282,-620,247,632,272,917,-377,762,485,148,-246,882,-726,327,926,664,145,817,493,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-917,351,-699,72,312,939,844,-356,766,-903,-660,380,8,-521,-855,294,62,595,-556,246,119,337,-929,800,-356,306,540,340,292,-501,27,424,384,-865,-878,571,-819,150,497,541,-293,-710,863,-514,421,587,990,352,-254,516,-17,348,-914,-874,-521,435,-201,66,994,336,-607,-430,243,834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{896,72,174,16,414,-398,1000,869,1000,649,942,-883,-1000,-459,-268,56,-184,1000,-381,444,-31,-449,464,892,-715,-294,-789,141,928,-977,743,48,-581,-763,-145,698,-1000,482,1000,835,744,986,1000,1000,-30,1000,-168,332,1000,-268,1000,1000,137,-625,-320,1000,598,3,779,342,-818,-841,-1000,-107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-849,-207,-873,266,-603,-826,60,973,-525,-353,-158,-631,487,-67,929,-472,948,-861,399,-292,-700,717,370,2,-188,-551,240,273,531,-456,556,442,510,-214,-306,-863,183,185,926,199,-519,-413,969,-374,-985,-683,-360,149,521,76,-153,825,-376,22,-146,794,955,-716,-553,-362,654,-43,460,-305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{769,-372,-769,334,-572,522,888,532,495,-922,-833,-642,605,595,-697,228,-729,93,602,-741,142,378,616,280,-291,-714,-509,-212,-835,-301,325,477,-283,-110,791,-96,294,170,-330,370,-730,875,-552,-861,640,331,647,760,-135,-438,-778,-557,-467,-738,972,-810,-86,-188,704,693,517,983,637,898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-162,354,439,-437,-390,478,183,-848,-884,322,-489,-51,330,642,-669,-560,48,-425,495,-350,508,-11,-914,438,-510,-128,137,491,385,-567,975,-783,314,-540,-333,-404,8,961,-473,624,870,166,-892,-11,427,-475,634,-714,-830,542,480,-758,-293,-37,938,-811,-900,-853,-726,-664,-743,-715,-137,257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{372,534,68,-541,485,-875,423,-94,511,-994,699,-84,292,-301,-539,-6,-396,-282,-162,-146,405,-931,415,117,-904,-803,-631,3,65,50,287,712,371,-161,-535,114,69,-423,906,-113,-618,-533,714,-700,682,-157,-850,448,95,-490,207,-22,418,-962,-756,47,-8,-118,958,597,-788,5,767,-85}));
    }
}
