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
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "acceptConstKeyword():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "acceptEcmaScript5():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{-99,378,-218,123,-153,355,-608,476,810,278,-460,-901,-682,608,744,-859,-575,-762,391,820,-676,871,62,880,-814,655,-974,-515,-565,440,119,546,478,637,-193,-934,717,-207,828,313,-431,-250,-521,356,174,505,360,-170,497,-250,-828,662,-417,-506,-951,936,-88,104,-201,79,960,666,449,-894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{-744,-438,-615,409,-601,950,602,-907,329,981,671,177,-155,-641,-578,453,-271,-480,-771,-657,580,41,366,469,110,-255,295,903,-287,956,-297,-854,-155,-65,-383,488,638,671,581,-383,19,-39,499,-921,-416,-35,540,517,487,-736,253,-522,-315,894,-68,41,-984,-242,182,494,-185,369,-377,-131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{304,-735,-1000,-208,1000,-43,-388,155,-1000,729,382,713,-238,50,-735,1000,208,426,-600,-108,934,548,-165,547,-1000,68,602,-760,-37,-48,1000,-11,-1000,-642,549,112,-773,-1000,2,-952,187,-354,203,383,498,97,-930,-1000,72,328,697,80,-98,-1000,-1000,532,701,-747,-2,613,921,-35,553,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{-124,996,-450,-934,-555,739,185,215,861,60,-149,-755,-302,158,1000,-1000,-613,-869,-858,404,-552,1000,601,1000,-272,17,-730,-494,-204,946,55,745,340,1000,-93,120,-13,-779,1000,-33,-74,-1000,-309,-458,581,816,397,-107,-448,283,-1000,1000,-1000,688,-800,1000,360,267,-479,-670,73,1000,-164,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "buildKnownSymbolTable():com.google.javascript.jscomp.SymbolTable",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "check():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-625,-762,-13,88,296,750,-376,-497,350,-499,-737,489,-170,-864,312,240,-179,520,-497,352,391,-825,290,-390,360,346,-198,169,-743,169,769,-919,-645,-777,-204,-109,870,920,162,-954,291,-353,917,-729,625,332,472,709,865,218,110,644,421,-988,868,-77,403,-698,120,804,-303,-857,-170,902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{1000,-1000,-1000,-1000,-1000,-1000,1000,404,480,154,67,-543,462,-139,643,-1000,627,1000,-452,-353,1000,1000,879,1000,-778,539,573,254,1000,2,882,-145,673,1000,487,-986,1000,330,1000,485,-300,38,-1000,-1000,489,1000,1000,-899,712,-1000,-963,888,1000,-507,725,-973,-351,-569,-1000,-1000,-1000,850,-779,-608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-1000,20,1000,580,-1000,1000,1000,1000,259,-1000,-1000,-232,1000,-412,-12,-201,904,241,-1000,-395,1000,205,1000,-379,-623,1000,-1000,-1000,904,84,580,-923,-113,-1000,-609,479,687,240,-724,-983,996,960,-1000,29,1000,-9,-1000,-1000,444,-1000,-14,-1000,1000,403,358,1000,-1000,1000,1000,1000,-16,-1000,410,693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-898,-528,901,-58,-287,888,813,-723,864,240,461,-419,958,301,300,47,-762,-856,-899,720,231,-301,279,568,728,-192,-586,-278,-526,-406,-844,-716,-486,-452,-772,-238,952,-99,-134,351,-197,-430,698,50,541,-756,-189,-94,-10,26,-452,-433,271,463,440,752,654,741,597,-742,396,-604,878,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-735,383,1000,-1000,-56,-1000,-588,1000,742,-1000,464,-913,-1000,586,1000,1000,-1000,1000,623,-1000,1000,-561,1000,-1000,1000,-590,1000,1000,-937,579,879,688,675,1000,1000,-1000,77,-808,-1000,584,-395,128,-56,-965,-618,788,-1000,767,-731,-1000,1000,1000,-780,251,478,786,-1000,-1000,-1000,-808,-218,1000,-231,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-409,251,-1000,1000,-1000,142,-716,-503,-248,1000,-54,700,54,48,-524,512,-1000,41,-1000,487,106,778,56,101,-833,174,564,1000,444,374,-69,-268,-220,-675,-114,-519,-86,-242,1000,-49,650,-1000,-756,-21,-680,-43,-130,67,-782,-616,-717,-1000,-441,-539,534,223,-899,-846,1000,-525,69,569,-1000,739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{580,-690,496,-469,-399,-510,956,945,295,-395,-317,-30,-732,-587,-543,861,725,341,-519,-927,-798,452,-907,938,-566,88,-444,-665,842,-105,751,-89,647,604,967,-824,324,295,674,371,-745,155,-865,-623,802,-275,53,-856,-236,-454,-966,-865,170,720,-392,72,743,-567,-227,-52,-20,-259,-413,681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-882,-498,189,-547,-900,-918,-597,-290,-892,-907,751,236,703,540,163,-961,42,272,728,433,621,-722,-747,820,-983,538,435,-731,971,494,293,776,-407,-223,-138,815,231,-722,845,418,-325,845,906,-655,736,-58,-411,-850,951,480,476,-67,214,-349,-873,403,455,-874,-411,500,-868,220,808,-715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-39,-99,518,-661,-127,-532,62,258,-293,-340,337,318,-369,627,-576,301,-279,-697,894,497,-162,-897,861,-961,50,781,822,258,176,-247,-837,186,-508,792,-405,-904,396,-603,-533,-474,944,409,868,635,544,433,790,-71,827,-472,-825,-610,-278,-736,-322,907,-877,730,-864,-520,-842,-623,386,174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-689,471,-594,527,531,-611,-207,419,-712,-622,141,108,-842,-522,521,569,-714,-197,-82,787,823,-379,-305,254,-203,-506,376,300,811,-904,-543,692,398,-73,-694,699,-329,547,917,385,971,-805,-741,292,623,682,-733,-141,-376,-663,153,936,118,-612,-353,577,-160,715,997,927,-646,-864,162,-526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-154,-874,-520,-322,679,-800,456,-907,2,-599,82,604,526,809,-176,-684,296,-445,433,-251,952,16,747,-95,-414,902,-635,-9,-376,136,437,-938,-410,-989,-830,510,427,-449,324,996,105,-800,-9,720,654,35,-775,193,-508,-470,-258,-377,-87,570,814,-93,957,-93,737,806,542,508,-183,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{537,-444,-26,-180,545,369,263,-3,-684,-801,646,-355,-892,503,-286,-28,-769,-722,874,-778,36,527,770,-195,-410,212,-865,566,-853,-922,-459,377,42,279,-139,-4,-639,228,-58,-288,848,-757,-5,687,919,664,494,84,-649,-69,-986,92,244,803,-638,795,-807,350,-411,434,-741,-316,55,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-706,-558,552,-982,-569,1,703,129,165,101,331,-420,-659,113,307,-316,-441,-475,-496,399,-931,397,-97,-773,28,-479,-334,-603,-826,-217,-971,185,-592,-132,235,962,570,834,428,163,-223,323,-604,-288,77,217,389,677,923,-609,-177,926,943,-90,646,347,870,441,357,-117,123,-987,247,-957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-1000,54,666,714,1000,-463,-809,1000,-27,-1000,-734,-1000,266,804,1000,514,47,50,1000,-1000,713,8,-545,-30,496,-8,1000,-501,-860,410,-1000,-650,-309,266,-559,1000,107,-235,738,441,-464,979,286,-70,-252,-132,-243,53,488,1000,-662,206,378,653,369,-259,-711,1000,803,812,413,88,-338,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.Result", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compileModules(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compileModules(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{34,-43,1000,-1000,1000,913,-1000,-495,34,133,-1000,1000,-1000,1000,1000,-1000,38,-593,1000,-1000,-878,-1000,-813,-217,-351,855,-726,1000,122,1000,1000,-899,1000,1000,1000,541,-491,-103,-1000,-877,791,296,-1000,1000,-1000,25,-813,976,-204,1000,1000,-1000,50,252,1000,788,-288,655,574,-1000,1000,1000,413,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "disableThreads():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getAstDotGraph():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getCodingConvention():com.google.javascript.jscomp.CodingConvention",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getErrorCount():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getErrorLevel(com.google.javascript.jscomp.JSError):com.google.javascript.jscomp.CheckLevel",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.LoggerErrorManager", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getErrorManager():com.google.javascript.jscomp.ErrorManager",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.PrintStreamErrorManager", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getErrorManager():com.google.javascript.jscomp.ErrorManager",
            new int[]{-587,107,172,665,866,498,-872,724,-258,-793,104,-796,802,-861,168,470,-246,882,-715,-908,-815,-943,707,703,937,123,593,-57,-9,77,533,-306,-483,2,252,-389,-100,67,996,-123,291,217,812,41,-514,566,-717,829,-186,273,-858,890,-896,561,-383,590,-237,-544,-612,-836,562,810,-426,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getErrors():com.google.javascript.jscomp.JSError[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getInput(com.google.javascript.rhino.InputId):com.google.javascript.jscomp.CompilerInput",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getInputsById():java.util.Map",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getMessages():com.google.javascript.jscomp.JSError[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getProgress():double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.String:VW52ZXJzaW9uZWQgZGlyZWN0b3J5", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getReleaseVersion():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getResult():com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getReverseAbstractInterpreter():com.google.javascript.jscomp.type.ReverseAbstractInterpreter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getRoot():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceLine(java.lang.String,int):java.lang.String",
            new int[]{-780,276,144,282,217,1000,-1000,-1000,-1000,345,599,-1000,-388,287,1000,-150,-762,53,829,700,-147,845,-29,148,432,570,545,807,55,1000,-21,-268,1000,609,-8,598,99,-213,-2,-137,105,-447,-233,-367,-278,348,711,-637,-672,302,-1000,973,-1000,-816,1000,364,-942,608,739,1000,-1000,-175,391,878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceLine(java.lang.String,int):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceLine(java.lang.String,int):java.lang.String",
            new int[]{894,-510,59,-406,374,-318,-647,769,794,153,791,997,648,-69,313,-49,-885,-819,442,-560,-206,620,28,-892,-580,-187,-912,518,-131,-381,-551,-253,-259,81,-443,526,-362,-633,261,-790,900,53,-263,-572,990,-23,-255,106,8,178,393,-219,-161,26,263,570,388,-465,-663,-993,-14,-918,-537,39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceMap():com.google.javascript.jscomp.SourceMap",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceRegion(java.lang.String,int):com.google.javascript.jscomp.Region",
            new int[]{-123,-414,768,453,-391,406,-110,1000,1000,-886,1000,22,138,-70,731,869,-503,1000,1000,534,1000,-1000,1000,-283,-992,249,177,256,255,617,-67,-1000,-98,223,102,-1000,-1000,317,598,980,1000,462,647,685,-1000,-1000,309,-592,455,1000,-303,1000,-323,-914,-1000,-488,-484,663,-1000,161,-1000,-809,-588,-810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceRegion(java.lang.String,int):com.google.javascript.jscomp.Region",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceRegion(java.lang.String,int):com.google.javascript.jscomp.Region",
            new int[]{-576,-549,-435,-882,895,-290,-705,-767,-706,-694,770,-739,-329,-494,287,424,165,646,-23,-873,148,901,115,11,-843,-883,943,353,-477,512,345,-265,149,-842,902,927,-735,520,-71,-784,209,-918,921,45,282,89,535,757,543,771,-331,-530,43,-904,537,683,-531,-986,-291,-926,383,474,8,125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.Compiler$IntermediateState", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getState():com.google.javascript.jscomp.Compiler$IntermediateState",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getTopScope():com.google.javascript.jscomp.Scope",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getTypeRegistry():com.google.javascript.rhino.jstype.JSTypeRegistry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getTypedScopeCreator():com.google.javascript.jscomp.MemoizedScopeCreator",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getWarningCount():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getWarnings():com.google.javascript.jscomp.JSError[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "hasErrors():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{-798,-472,-1000,396,-618,1000,-703,-1000,-972,1000,-1000,1000,-1000,446,1000,537,-289,1000,1000,-1000,37,-344,343,1000,-1000,-1000,1000,466,6,-1000,959,1000,1000,1000,1000,353,196,-1000,-1000,-788,472,-6,-178,-1000,-960,1000,1000,1000,-661,781,-1000,-1000,1000,-1000,633,-1000,-276,-561,821,-400,1000,792,-1000,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{331,63,-17,-197,678,-503,891,-34,585,-557,685,203,152,762,-1000,875,679,724,-875,1000,1000,-121,-598,165,-189,-25,487,-608,-1000,-736,-353,1000,1000,836,48,782,717,52,998,784,-93,590,25,-18,1000,896,-955,-951,-841,945,-1000,133,272,-763,-1000,203,30,-349,-106,405,-244,-439,-1000,-285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{1000,578,1000,-612,-760,1000,-232,1000,911,714,-419,-462,1000,1000,113,-612,819,-1000,-1000,-166,-579,602,981,-144,-1000,-197,1000,920,-1000,1000,-19,-922,-950,842,171,787,-1000,188,-1000,-756,-347,27,-1000,-794,947,-1000,-1000,-1000,-1000,-190,-1000,-142,-679,823,-1000,1000,762,-194,1000,-702,-907,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{724,-471,994,621,-186,243,476,272,-986,-213,-76,299,421,317,463,-829,-298,-226,-536,673,-626,-672,-799,498,-292,-315,990,55,-949,-250,764,58,-705,945,401,909,-546,123,-228,185,909,-523,-572,-340,-320,-913,-868,-815,339,823,-986,492,618,-782,58,522,-395,-391,795,-754,-481,364,-345,-918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{-1000,-1000,-280,156,845,-334,-809,-594,1000,399,-15,986,-142,459,1000,-1000,-519,-386,-386,-13,-1000,-556,-1000,687,300,1000,1000,-226,-801,-828,1000,-1000,-798,179,-223,982,-700,520,545,1000,-396,1000,-807,31,1000,79,-511,139,726,-810,-1000,876,-440,-1000,432,1000,-1000,751,1000,1000,595,-166,147,-621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initModules(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initModules(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{1000,-109,-1000,-1000,441,1000,1000,-500,-1000,1000,-948,-930,-423,1000,129,1000,-1000,-1000,490,-1000,-1000,-1000,-1000,-1000,-920,679,-1000,636,-1000,981,1000,-189,-342,-31,-1000,-698,213,-1000,-311,-593,1000,-887,555,54,-256,821,-1000,-866,-214,924,156,-690,-339,-201,-1000,522,-1000,-185,1000,287,-324,-329,-1000,-840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initOptions(com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initOptions(com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{97,488,-388,-314,-453,636,-329,-263,-333,-455,218,-102,-210,-310,-479,82,903,202,874,-451,421,520,-549,-172,10,142,33,367,-110,-374,566,639,219,-125,221,73,910,693,869,-70,-433,-362,-203,-951,-924,246,843,240,-151,870,-876,23,702,-428,-939,-596,939,768,500,-567,302,-333,-617,383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "isIdeMode():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "isTypeCheckingEnabled():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "languageMode():com.google.javascript.jscomp.CompilerOptions$LanguageMode",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "newExternInput(java.lang.String):com.google.javascript.jscomp.CompilerInput",
            new int[]{915,288,794,287,-62,-556,997,-917,328,140,367,-796,361,766,473,6,-994,148,868,-626,781,745,73,-530,626,91,-328,130,139,-47,243,-907,504,968,541,533,-590,-641,-834,-744,-623,-265,802,-615,-967,-28,-936,-700,-912,678,922,-766,-964,854,-215,5,914,-977,-616,-938,885,-677,808,-700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "normalize():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "optimize():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{-252,596,-98,-684,-994,973,-147,792,570,-754,500,-936,442,978,964,554,-503,844,685,-322,730,-323,895,986,81,-346,361,-238,-977,19,311,659,377,60,-415,-539,294,-733,454,396,-795,534,342,551,691,-903,687,-123,-329,-533,-405,62,100,-938,-414,81,-540,465,-239,658,688,250,979,37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{471,639,809,332,75,-97,-530,-10,616,408,497,-747,264,-426,974,406,-144,-1000,-8,254,974,120,911,704,207,-986,754,-573,33,-250,-632,656,982,944,94,688,250,422,134,969,780,-168,538,-214,-26,852,798,-465,669,-771,-605,286,776,323,-666,-49,79,46,701,740,-926,116,566,778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{-434,-348,27,193,248,-739,-989,372,542,773,24,586,175,275,516,-785,154,568,-423,569,902,-915,855,597,694,658,-829,-86,970,883,117,-854,352,203,25,-972,678,-13,-821,-927,245,-146,-186,-284,-432,306,575,-173,-557,309,418,215,-124,-102,-340,328,-471,-903,-148,-254,717,494,-527,255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{737,-333,-954,290,300,-688,-775,-623,197,-594,638,-173,-30,-957,818,381,-666,-334,-552,-235,350,336,305,-800,-38,-575,787,-227,390,-356,-277,-706,-899,663,-722,-239,593,-214,-810,878,-931,63,-659,-778,231,-932,-306,565,-119,142,-984,669,809,-680,-511,-604,-508,-590,-718,387,600,-300,-496,-247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "processDefines():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "rebuildInputsFromModules():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{607,-657,-736,-433,-786,-757,990,44,980,248,71,-8,-72,919,-644,-299,-132,-702,181,-125,-527,-656,-758,-234,533,785,225,79,-534,23,887,-268,-956,857,679,-612,-279,912,-983,-360,-28,860,-225,-324,588,807,-266,-661,-959,-472,-672,-439,662,-357,-713,-623,-302,-733,531,-82,-934,202,-959,-888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{-496,618,681,-139,284,136,356,-433,897,-531,18,-237,111,451,-38,-100,-891,-638,-866,-310,939,-927,-641,-144,114,722,-154,861,572,-298,810,-209,-935,-151,-572,-532,-494,834,335,770,-204,982,402,-870,207,-996,-397,-759,-451,-537,490,80,14,178,-45,-421,-146,-759,792,465,865,813,881,117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{-201,-885,-910,454,-678,-750,864,-199,-581,-593,-260,990,-145,-234,-772,-865,-547,-299,457,214,577,366,-593,-773,-751,-123,-789,-76,173,-558,383,850,808,-132,281,337,-937,-968,73,-12,-791,91,984,187,154,-517,-270,217,-645,-904,-307,-322,336,390,-411,-923,21,-322,-794,-318,-71,-667,95,60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{315,-771,31,588,1000,1000,-1000,-30,-1000,430,-726,1000,-391,-1000,678,1000,1000,1000,466,-13,511,-315,988,-495,138,153,476,902,-505,-278,637,389,1000,-62,-39,920,-457,-652,769,384,-1000,-1000,-981,1000,232,-372,-363,1000,-898,556,1000,267,-425,1000,1000,1000,1000,-482,-1000,933,287,-1000,-339,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "report(com.google.javascript.jscomp.JSError):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "reportCodeChange():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
