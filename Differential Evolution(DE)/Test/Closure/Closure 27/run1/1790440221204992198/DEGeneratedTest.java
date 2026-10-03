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
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "add(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{620,1000,-122,64,-166,-23,-665,939,-607,1000,-174,687,829,307,812,-1000,599,1000,-461,37,1000,914,-1000,-1000,-109,391,289,197,1000,-699,255,-1000,-742,1000,-1000,346,9,-260,439,-1000,-1000,-47,-1000,257,-487,425,-182,607,-266,413,-307,-1000,576,714,551,247,1000,472,1000,474,1000,-157,-682,-490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "and(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-709,98,-649,77,-376,-270,211,-1000,-400,90,400,-106,1000,-1000,-802,135,335,400,329,-928,-1000,1000,910,402,277,254,37,896,326,-732,-400,453,879,-129,113,705,1000,-231,-399,254,665,143,608,-764,-884,-1000,880,-1000,-636,-333,-724,288,-1000,259,982,31,-49,-636,-1000,502,-210,814,-473,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "arraylit(com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{-614,-110,649,16,847,88,-834,-583,549,-257,-529,183,-802,952,257,-293,529,-854,-478,-47,-502,-128,-593,-759,630,936,1000,-37,331,819,649,624,775,-267,-989,219,-987,-122,-966,308,-582,533,-579,-653,231,-555,-318,873,40,660,-112,-62,-650,407,403,814,32,946,494,-524,-556,-601,-441,587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "arraylit(com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "assign(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "block():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "block(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "block(com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{75,957,-622,703,326,-118,46,-747,-762,576,-354,-2,201,784,-254,619,-965,-759,771,-723,-73,-234,-517,537,219,974,-617,-295,110,711,-347,-258,952,568,-870,-353,495,-475,785,444,-842,-865,-813,813,224,-498,509,764,709,-230,-226,748,557,832,-747,-873,-154,-478,-804,-982,696,707,-615,188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "block(com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "breakNode():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "call(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{510,-491,1000,148,-674,-142,49,1000,528,-124,114,-711,579,-883,312,-784,-330,-1000,1000,-1000,258,-1000,942,-671,179,-70,-373,494,1000,-1000,141,140,-45,-361,-590,405,-619,471,313,-875,1000,324,45,1000,-983,-76,784,-1000,678,377,-1000,482,867,-422,17,721,311,301,1000,-825,327,-499,370,590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "call(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "caseNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{441,383,-554,510,518,-161,721,-927,-446,-230,-1000,-441,-1000,-682,298,-675,-397,890,932,-549,-535,-881,1000,61,601,356,-319,579,-992,-381,-929,-512,283,385,447,1000,-773,-43,582,12,300,-530,1000,-991,-683,-619,-591,541,-712,363,828,790,852,-879,847,485,47,-602,-594,-494,-205,-170,205,102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "caseNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "comma(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-777,288,586,468,-659,904,-192,103,-250,-914,-1000,1000,-1000,398,-309,157,-723,878,1000,-1000,-161,659,1000,115,237,-755,-424,-1000,-237,-1000,-1000,1000,1000,-1000,130,808,-1000,-1000,139,1000,183,-713,264,345,826,759,-1000,394,1000,-1000,-1000,-1000,-869,-165,-258,546,-1000,753,-85,1000,-573,-168,708,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "continueNode():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "empty():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "eq(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-700,87,-433,38,-1000,382,-1000,430,963,768,-416,-1000,-1000,167,661,1000,-1000,612,-1000,722,-1000,-156,842,600,-490,1000,513,-1000,-906,400,-1000,-724,-1000,541,-1000,1000,452,-308,960,975,667,1000,1000,-1000,-1000,662,-400,1000,1000,-854,-1000,-121,1000,835,1000,817,-1000,-748,-1000,-1000,1000,-1000,-57,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "eq(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "exprResult(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "falseNode():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "forIn(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{840,33,227,1000,-398,699,318,818,-597,-1000,-445,542,212,316,-278,-1000,-25,941,751,-186,756,120,372,-1000,-125,-314,-494,-109,-1000,-735,342,26,-190,809,-104,-261,-762,-976,735,411,-686,601,-1000,-393,939,-757,137,1000,-88,-690,-86,530,533,206,-257,1000,-641,-58,285,-121,-179,183,-680,-516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "forIn(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "forNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node,com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-1000,1000,-422,-1000,-689,-1000,-557,-1000,-1000,-1000,728,93,-22,1000,626,-112,-1000,-927,-1000,358,-289,-318,249,-1000,359,993,223,9,-389,-1000,-1000,110,-1000,-81,317,836,799,-107,-137,-1000,66,-969,-1000,1000,-402,-1000,-901,766,-523,-593,334,136,-445,-418,300,91,-1000,1000,-300,-540,-102,1000,419,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "forNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node,com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "getelem(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-583,1000,670,770,1000,-199,1000,1000,1000,122,-1000,-138,-1000,-268,385,-203,-1000,-1000,-890,-52,-844,-259,155,454,1000,1000,495,1000,71,436,-143,-671,-1000,1000,-463,654,1000,-1000,1000,1000,1000,-1000,-674,863,344,1000,302,-619,-1000,-1000,-912,41,844,713,-1000,1000,-1000,-567,1000,243,1000,1000,1000,-102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "getprop(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{818,1000,-674,881,496,-605,-1000,-502,-940,-104,1000,168,-1000,-46,-1000,1000,-679,324,435,-10,176,-1000,524,-1000,1000,1000,1000,1000,-529,638,-1000,-1000,1000,252,-393,1000,-409,-644,-1000,-1000,-1000,1000,979,820,1000,-889,129,1000,-1000,570,-340,570,-654,992,-1000,-1000,517,344,940,-550,424,-378,-1000,-854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "getprop(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "hook(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-986,189,442,-1000,-646,-222,410,874,860,1000,22,-1000,679,565,-1000,34,257,258,15,636,1000,500,-438,-809,-204,1000,-1000,-669,-158,13,-54,318,752,-31,498,130,-1000,10,941,-62,-400,-120,317,157,-970,-379,-798,-706,36,-222,1000,1000,-151,743,62,-935,252,-1000,-1000,-627,-625,628,-173,296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "hook(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "ifNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{737,304,946,-183,530,-908,-941,-744,-136,-538,838,-631,-466,424,-478,79,225,7,644,420,-954,-626,464,815,-789,-894,922,-550,167,-572,-293,-425,996,369,651,244,176,-570,142,927,-104,-826,-537,749,-941,-634,-923,-339,-212,-240,733,-235,-156,444,990,232,-312,779,-760,762,78,-607,661,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "ifNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "ifNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-469,636,946,161,1000,-1000,-1000,-69,-1000,230,775,-126,-631,-1000,138,-430,174,454,796,439,232,603,640,1000,-167,-1000,1000,1000,303,1000,-288,-1000,977,-618,-1000,271,1000,-572,-120,262,340,-427,-352,-658,-82,751,789,1000,-582,-532,141,-135,414,-754,-438,129,-392,1000,-1000,-691,-654,1000,-1000,-846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "ifNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node$StringNode", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "labelName(java.lang.String):com.google.javascript.rhino.Node",
            new int[]{42,20,-139,841,179,653,-219,-909,781,-610,-715,507,-121,307,566,-682,-332,545,443,830,322,-662,948,-618,-224,978,-432,835,-503,-56,968,-765,-955,997,-619,-420,608,-412,740,-15,654,569,135,970,-674,-70,-827,-929,-726,-979,-576,525,243,-440,328,403,-61,630,593,-778,-565,-196,-533,819}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "labelName(java.lang.String):com.google.javascript.rhino.Node",
            new int[]{991,877,-835,-1000,1000,691,-506,895,1000,678,-125,1000,1000,-396,-1000,678,1000,-565,-276,-1000,348,-822,190,1000,887,-1000,-1000,-871,587,244,-289,-1000,1000,940,178,-1000,-870,-170,763,-541,-615,-906,751,-1000,1000,1000,-56,1000,556,-704,-576,730,1000,197,-1000,-1000,-1000,799,69,1000,485,556,960,650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node$StringNode", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "name(java.lang.String):com.google.javascript.rhino.Node",
            new int[]{319,-984,215,203,6,-773,-118,455,-185,285,3,738,-670,-598,-44,41,-719,213,-737,-127,452,862,-464,-854,123,-474,-58,31,-312,-679,-191,896,451,839,385,984,492,-72,-316,156,-15,731,-135,-305,-633,-476,-350,974,362,-603,928,-403,22,-530,846,-489,662,996,495,564,-146,430,550,-18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "neg(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-920,14,-25,-1000,-53,-542,-493,327,-587,1000,525,1000,720,-121,125,1000,-1000,-486,60,-327,-517,116,-129,289,-304,914,101,-188,451,-924,929,989,-182,-400,-545,207,-532,59,-789,-594,-775,962,-1000,-715,-619,675,-400,1000,645,1000,-554,-838,-265,322,1000,-1000,840,-789,-456,304,318,404,-195,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "neg(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "newNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{-572,-536,162,131,-164,1000,646,-172,-328,-209,722,623,-506,-268,221,509,75,25,-610,-669,291,-165,-63,128,545,-58,596,91,1000,986,-387,-223,393,47,1000,-658,-1000,-857,80,-1000,-240,106,209,307,-908,-1000,-460,-1000,-495,-960,312,636,-768,-23,-452,-71,986,290,652,587,-728,-516,384,417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "newNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{-975,807,-3,-797,-1000,-456,-186,-1000,-1000,-17,283,374,814,-898,-82,-836,298,-702,1000,-283,731,531,-1000,748,322,-892,-96,-664,-946,-1000,227,298,-99,-430,-510,9,108,-334,-263,-484,-865,1000,126,-939,-788,807,1000,98,-486,-851,739,320,-67,1000,-363,-53,599,-343,971,-188,1000,-58,-601,795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "newNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "not(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-627,962,-278,-506,-231,-397,896,-890,-1000,7,985,396,755,1000,1000,-1000,-1000,1000,-473,-1000,-958,499,-1000,1000,1000,530,-1000,151,1000,285,-728,1000,-613,536,1000,521,347,-1000,-205,-634,545,654,384,1000,843,-534,-790,1000,1000,357,-1000,-551,650,374,-1000,-1000,-1000,-1000,-1000,-881,210,-255,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "not(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "nullNode():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node$NumberNode", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "number(double):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "objectlit(com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{-233,550,-736,197,-381,500,398,913,577,448,-387,71,-58,166,688,168,-28,-384,-746,272,-13,-363,-787,867,605,969,179,976,955,794,157,-561,-848,694,773,-226,-550,678,-88,527,57,773,807,690,-171,551,-674,400,-215,-54,512,893,717,-299,-763,453,-973,879,288,-985,-612,30,124,926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "objectlit(com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "or(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{235,1000,-374,857,-301,310,1000,-873,-1000,-414,-1000,671,414,-85,126,-247,-1000,-166,-1000,-1000,-86,-1000,-41,789,180,-1000,516,797,691,839,-222,-914,-598,-490,-185,-695,-42,-1000,-848,-553,-19,425,-844,-1000,344,-1000,1000,1000,241,617,878,-524,-564,1000,-761,-354,459,379,-764,-290,303,-133,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "or(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "paramList():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "paramList(com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "paramList(com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{241,-304,280,-537,-852,-170,-472,-450,361,890,-379,183,-89,-750,622,81,216,705,808,438,799,-57,-155,-258,547,-627,558,212,483,655,137,783,214,458,936,37,24,-788,-225,950,-88,-106,-929,-25,798,-705,-942,-447,-423,587,-155,-105,732,-468,-867,642,835,-782,40,-198,403,-209,-326,315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "paramList(java.util.List):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "pos(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-957,1000,22,394,301,813,-774,494,1000,-436,-1000,-1000,836,714,134,94,1000,-92,734,-85,996,1000,-570,1000,1000,720,1000,1000,-346,-301,-674,-1000,-1000,-1000,-400,758,-630,1000,445,1000,-1000,1000,-1000,-957,468,-509,-401,179,-255,-180,-507,-1000,1000,872,-1000,-621,-1000,-1000,-809,-647,35,-229,-52,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "pos(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "returnNode():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "returnNode(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-440,265,202,-576,890,-167,124,-97,136,-205,691,163,-565,477,-520,-43,174,144,-192,514,403,-1000,-360,265,194,-337,578,-717,1000,1000,-1000,-107,-241,-1000,-299,675,329,1000,-1000,-328,-1000,853,-574,1000,-501,1000,1000,-1000,172,1000,-730,-389,-1000,-545,-595,-661,-597,1000,685,677,-1000,788,528,-57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "returnNode(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "script(com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{-566,-601,-412,522,-788,112,336,954,842,11,-235,426,636,355,898,212,859,435,-303,-605,812,-823,965,-497,69,128,-390,-391,-692,-129,-117,-69,-593,-70,-466,-653,-473,-455,67,-48,-269,-715,248,-153,-611,-575,-124,-376,-932,241,440,727,913,-51,738,776,626,-134,798,267,-940,-440,512,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "script(com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "sheq(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-1000,1000,-554,961,-1000,1000,1000,155,1000,-326,-176,-1000,60,664,1000,-1000,-280,547,-717,-1000,684,14,-606,-84,-1000,-988,-41,881,332,-389,-1000,318,505,-1000,575,158,321,1000,-449,477,-222,-331,726,-778,-546,-985,-968,-841,589,-901,-22,20,422,356,228,-130,798,-106,-1000,20,-934,-336,-885,36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node$StringNode", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "string(java.lang.String):com.google.javascript.rhino.Node",
            new int[]{53,-329,-124,258,982,299,-344,-18,-635,978,221,-595,-14,-716,587,135,-439,-892,-790,267,148,-126,777,440,172,182,-516,929,-258,453,845,112,257,-625,-211,-501,908,944,831,189,611,712,450,84,-831,-485,442,481,-5,364,-15,794,615,-527,-572,-977,329,-551,415,-632,941,-426,835,895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node$StringNode", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "stringKey(java.lang.String):com.google.javascript.rhino.Node",
            new int[]{793,789,927,-990,377,-302,-751,343,-224,-556,445,-570,-182,121,221,794,676,-880,988,608,-111,-27,-150,-511,-533,-954,864,-4,883,-989,112,907,139,865,-416,514,-912,207,851,-620,-292,457,94,-743,-879,-239,169,-802,828,-573,-931,308,-178,-82,-576,996,53,-345,462,-854,39,207,258,184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "sub(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "switchNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{-847,326,646,-282,-400,-999,400,-400,1000,986,687,215,-285,-695,153,341,345,-1000,-865,-464,-1000,562,960,544,-58,294,-803,235,497,-135,292,112,374,525,1000,-848,-455,400,469,-378,-659,1000,995,-1000,-593,1000,-657,-143,-212,400,418,400,-1000,269,355,-1000,-706,-175,695,-1000,-831,89,-1000,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "switchNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{-781,218,850,-806,-1000,-843,1000,-1000,820,1000,588,-363,21,-214,542,823,1000,-911,-451,-1000,-1000,937,943,651,-371,896,-1000,89,453,-1000,-12,560,963,938,1000,-1000,-678,1000,808,-357,-635,1000,-174,-626,-418,1000,560,-468,126,1000,1000,1000,-912,-550,-588,-1000,-1000,-172,609,-1000,-842,-334,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "switchNode(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node[]):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "thisNode():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "throwNode(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{660,976,-98,602,393,-658,705,675,-637,23,18,60,-269,-567,82,-178,-61,824,-412,771,-1000,-837,-288,313,-1000,58,668,205,-570,76,551,-24,-1000,-1000,-964,-606,-638,252,352,919,-328,660,-1000,-1000,126,-771,215,636,-1000,30,625,-349,212,545,552,-782,730,412,-1000,414,-783,-1000,735,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "throwNode(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "trueNode():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "var(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{774,380,874,445,209,-123,318,-1000,751,-341,750,-28,-1000,-792,-47,519,-293,-284,-1000,456,-402,-794,1000,400,-87,1000,-453,-315,-1000,-276,-531,-89,-264,-211,-1000,1000,281,209,132,-246,1000,313,-545,-181,444,815,1000,-929,-1000,485,-1000,-1000,1000,-1000,-590,59,1000,735,-694,649,-621,-777,144,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "var(com.google.javascript.rhino.Node,com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "voidNode(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{374,187,442,933,1000,789,544,264,-1000,119,-403,-208,267,-688,887,-700,255,-36,1000,-1000,-47,1000,281,-739,-1000,-66,793,297,-1000,-676,-671,657,-109,1000,-1000,-506,982,-501,-787,1000,-70,-549,-428,1000,1000,1000,154,923,1000,-969,429,-109,-621,-177,896,1000,785,1000,593,-1000,152,-502,-338,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.IR", "", "voidNode(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{-745,-258,-739,309,655,-277,-461,-456,-392,-853,136,-969,-372,-253,686,-284,-597,-873,95,854,-657,419,-561,-170,875,880,888,519,-808,-859,534,295,-153,-949,564,227,-508,-789,-929,-728,-602,-831,6,-664,934,18,-763,-24,-894,630,-490,-802,-541,-843,-779,308,-819,578,-675,135,-920,-961,-516,-972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{-953,-739,-700,-608,-517,-887,-725,-770,-542,-188,522,-359,757,-596,286,559,-122,-188,188,-586,-302,558,16,333,983,-689,629,135,196,-568,-912,-828,678,-62,-354,542,-746,-560,141,446,-403,504,488,-471,-752,636,464,-50,-872,596,-190,-412,540,153,756,-654,-753,-498,-463,-199,982,-414,-805,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{-179,920,908,-289,-858,-932,453,792,-276,-305,-375,752,-309,-550,844,147,643,4,-367,691,693,-928,-777,900,-52,-632,-485,519,647,-55,282,-896,883,-332,-884,-405,-240,690,417,964,-880,692,976,-908,577,382,139,-72,470,-691,382,-339,-414,369,-762,-833,-983,-415,-63,289,340,668,-127,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{-758,-1000,-136,383,299,1000,-1000,-178,-327,210,224,-930,902,622,143,282,-107,-33,920,166,-765,295,947,-97,257,971,-317,-835,-631,854,-1000,1000,28,-422,395,4,-347,-336,700,-1000,560,-631,398,838,-464,-111,348,661,-735,285,-1000,-729,-294,-950,-241,150,1000,807,965,-1000,-14,-87,-1000,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.RuntimeException", DEReplay.run(
            "com.google.javascript.jscomp.JsMessageExtractor", "com.google.javascript.jscomp.JsMessageExtractor", "extractMessages(com.google.javascript.jscomp.SourceFile[]):java.util.Collection",
            new int[]{-802,349,-879,326,219,546,-348,-454,63,-158,-848,-670,734,-309,-771,210,956,-369,-662,-94,-571,42,630,-165,488,-65,-396,889,468,-899,79,-966,-948,-481,-694,-645,943,54,-681,-133,-704,-848,81,-394,-475,515,380,-707,348,514,-26,-762,217,-480,73,40,-354,244,860,-908,-626,682,477,-19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.RuntimeException", DEReplay.run(
            "com.google.javascript.jscomp.JsMessageExtractor", "com.google.javascript.jscomp.JsMessageExtractor", "extractMessages(com.google.javascript.jscomp.SourceFile[]):java.util.Collection",
            new int[]{790,-666,-320,424,593,-14,162,-444,268,543,477,-884,634,-863,-578,-660,466,-945,761,-764,-23,-68,-731,307,817,44,193,773,169,-411,445,-133,-500,-624,113,875,587,-522,-797,-743,545,110,902,399,-257,203,-863,331,-246,20,700,95,774,477,789,347,-707,-185,-519,-744,-753,-254,-683,-201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.RuntimeException", DEReplay.run(
            "com.google.javascript.jscomp.JsMessageExtractor", "com.google.javascript.jscomp.JsMessageExtractor", "extractMessages(com.google.javascript.jscomp.SourceFile[]):java.util.Collection",
            new int[]{206,773,-619,-610,-451,-865,323,-752,676,827,272,99,-110,-832,-619,-996,840,712,-319,976,72,-466,-480,-523,539,-15,929,-693,-226,-102,-651,-392,412,796,-571,-221,799,-202,925,745,545,-417,-854,281,35,41,798,-177,164,41,668,-401,856,148,-904,776,274,-975,875,-187,-912,-213,911,-752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.RuntimeException", DEReplay.run(
            "com.google.javascript.jscomp.JsMessageExtractor", "com.google.javascript.jscomp.JsMessageExtractor", "extractMessages(com.google.javascript.jscomp.SourceFile[]):java.util.Collection",
            new int[]{-394,-376,727,719,166,644,747,193,770,68,180,110,624,-328,-725,401,-11,-323,-267,211,-458,-834,168,557,299,-446,607,-885,98,-381,-734,-120,599,203,83,518,-165,-223,828,911,-571,777,991,-413,798,875,132,542,868,-855,855,-719,-840,469,181,-171,425,481,721,-68,477,-179,767,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.RuntimeException", DEReplay.run(
            "com.google.javascript.jscomp.JsMessageExtractor", "com.google.javascript.jscomp.JsMessageExtractor", "extractMessages(com.google.javascript.jscomp.SourceFile[]):java.util.Collection",
            new int[]{-689,565,159,-571,-805,-707,663,-19,-27,489,-557,-201,-441,667,184,218,276,-27,445,529,-461,-127,-109,197,213,973,207,-568,-84,33,-317,-104,-478,-216,-253,389,-843,-701,-954,45,-896,343,-937,-223,-788,407,-291,-513,-388,-100,-124,-82,-125,-595,465,-88,-899,785,101,348,-181,-507,417,-996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "newExpr(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{-423,285,-614,319,-181,1000,730,238,1000,1000,471,-1000,210,1000,1000,750,941,-1000,1000,-812,365,-1000,-1000,1000,3,1000,-576,-20,695,1000,441,-1000,-1000,1000,-1000,1000,-1000,562,969,-1000,-788,427,-189,-411,-514,382,157,999,-377,1000,-1000,-270,-850,-441,-780,1000,686,1000,-1000,237,-146,430,623,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "newExpr(com.google.javascript.rhino.Node):com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.NodeUtil", "", "newQualifiedNameNode(com.google.javascript.jscomp.CodingConvention,java.lang.String):com.google.javascript.rhino.Node",
            new int[]{852,-492,546,-749,698,-554,-487,91,181,73,-147,549,-311,420,-276,468,318,179,716,849,-794,458,-269,252,623,98,98,730,-62,372,-372,948,-633,-167,580,-105,-299,102,360,-615,-912,489,189,-596,404,426,-70,-703,718,-32,821,-247,-48,-176,-74,-376,935,-251,816,-312,-227,-499,163,681}));
    }
}
