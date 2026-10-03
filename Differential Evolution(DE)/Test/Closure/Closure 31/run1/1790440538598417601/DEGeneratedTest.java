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
            new int[]{753,-831,253,-804,-969,139,821,349,584,291,-856,188,-633,740,-493,-860,-625,-257,-516,52,209,-462,-466,928,952,-425,-441,402,80,-383,-886,264,37,-39,114,516,953,-937,809,-93,546,-753,-427,658,700,829,612,172,-595,-1,-909,960,-188,124,384,662,4,-293,-867,-855,-267,-767,653,926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{-120,-681,-156,878,663,178,-781,-914,-272,-736,-97,-951,-815,142,-429,-40,-647,-870,-889,751,562,-934,-423,734,653,-343,426,-725,-941,-669,-578,709,242,214,-322,882,141,-51,281,179,810,-576,-308,653,898,157,-307,-41,824,-908,35,215,-455,-148,124,-387,672,120,130,613,-640,766,-323,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{439,681,329,-338,100,-629,-511,-565,-283,305,693,450,-395,-299,-107,-825,601,-557,825,-814,273,76,-786,-477,33,7,305,-90,-585,48,-965,811,857,157,314,-593,25,288,816,66,-614,-343,-561,-348,127,382,-818,93,-263,467,552,-952,-364,-602,-176,200,-942,-749,480,-237,88,-876,990,-911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "addNewScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{-970,579,-3,-806,986,-360,315,-610,670,-818,-645,110,768,-849,273,-120,-881,-552,179,-101,311,763,-910,387,534,200,109,-831,-435,880,721,-492,728,442,-768,-650,-514,439,306,-389,-101,958,-660,914,-386,522,968,260,276,284,385,-473,-703,-145,-638,410,758,-280,-141,-583,-93,121,935,-99}));
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
            new int[]{294,762,626,-516,-603,759,873,560,281,421,-251,167,-120,-46,-809,-905,-447,-987,-756,-79,-773,165,126,-764,-695,736,-841,459,739,-331,-922,-384,-997,772,-954,922,432,312,777,200,-661,-700,-462,231,-330,-996,-426,970,-465,231,-530,-754,928,758,-668,-980,159,910,315,582,-915,737,-24,-825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-1000,962,-1000,548,-201,96,1000,846,1000,671,-264,-991,-366,-1000,1000,-609,1000,1000,-78,-1000,1000,1000,880,857,-1000,141,-411,-821,691,-594,1000,201,1000,1000,-1000,-763,-1000,690,-615,49,229,-377,-314,-456,317,1000,-1000,1000,1000,564,1000,-372,1000,1000,-1000,1000,218,-784,-488,1000,-306,249,713,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile,com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{278,-312,-407,-174,-651,554,-649,64,227,-138,-208,458,-143,-783,443,-956,-461,-178,-726,-388,-449,-746,189,796,-410,576,662,-693,-334,-611,-221,172,392,174,714,288,-646,848,-242,-222,-792,-160,419,733,-549,-796,-194,730,-539,272,-417,-791,-692,-805,-944,271,-913,343,-344,-277,-908,-14,14,-657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{495,-159,-53,410,-942,-423,440,128,681,543,-516,407,184,-947,964,-738,849,-39,917,845,389,-885,383,552,-8,-492,-506,537,360,21,-556,-232,-761,-126,54,791,-942,291,348,494,989,-783,-257,333,988,-575,257,715,187,843,-522,533,396,529,-67,216,-586,351,-993,-932,318,-873,537,-831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{801,-1000,-1000,-20,-1000,-1000,-1000,-491,1000,606,1000,1000,-88,-383,867,-1000,933,1000,-1000,399,-1000,-1000,812,-1000,1000,-1000,1000,1000,-1000,-884,-1000,888,1000,-958,676,1000,1000,966,786,1000,531,-327,589,-1000,863,876,1000,693,-429,359,149,-898,-986,-9,-1000,656,295,125,-559,47,66,1000,-615,1000}));
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
            new int[]{281,-1000,552,-936,-173,-622,475,-114,585,-75,113,-525,682,1000,-805,251,981,10,-732,240,741,1000,-577,-811,-334,785,-757,847,-764,283,-977,696,135,584,602,25,900,-1000,-266,59,868,619,-1000,-1000,-331,-1000,705,1000,620,-1000,142,716,-585,651,-486,-956,-740,-443,39,-333,529,133,889,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{-722,-264,-505,-346,422,-117,-172,274,424,5,-416,-640,312,-491,-367,840,-826,-509,320,596,905,546,980,-626,-178,365,-608,974,-602,44,-512,939,-202,67,933,-848,-401,-217,801,-233,-898,-144,-473,-285,-998,-351,531,672,790,788,-503,806,274,280,-877,-99,836,-567,387,504,672,589,-190,650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{481,-762,345,-462,-510,-818,654,460,-498,590,-449,-19,556,870,-113,-224,659,642,-844,778,581,925,-53,-891,-437,473,-959,913,-267,943,-517,565,-553,641,279,-28,455,-545,-10,-347,855,230,-583,-856,-906,-677,808,861,-33,-619,611,59,-507,474,-389,-265,-156,-369,519,-582,86,-431,555,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{1000,-762,843,521,110,19,81,460,-498,208,746,-353,-712,845,104,395,-1000,523,-76,558,-213,56,241,570,-532,473,180,51,286,-335,83,432,687,-229,739,-28,455,748,1000,-29,618,714,-632,-84,-717,-856,-299,68,-747,719,312,-442,222,190,832,-265,-485,999,367,-694,858,193,555,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.SourceFile,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{977,-756,410,12,552,882,166,-534,-111,-1000,-820,130,324,638,657,-156,1000,-282,667,-767,1000,819,436,236,-308,290,-970,-642,132,1000,946,-165,-646,-348,396,-711,-1000,472,-1000,-448,-275,985,-854,-123,375,-383,-342,-545,272,-1000,273,-466,-547,1000,-421,-259,-557,-283,1000,-912,779,129,1000,1000}));
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
            new int[]{1000,230,-920,512,-1000,-1000,-1000,654,-797,270,-1000,1000,950,-1000,919,-413,41,236,718,-1000,-827,-134,-276,-244,-729,-413,1000,401,-71,-343,-107,195,-565,500,1000,649,-786,-506,517,-858,-1000,-1000,1000,-162,762,758,-634,-137,-1000,-543,252,-1000,1000,1000,1000,954,40,502,1000,716,1000,289,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{807,-396,-216,159,-848,109,-769,-116,-737,-149,-974,902,-303,-387,-202,-569,-778,186,367,-733,-711,-395,120,11,-219,-280,991,404,719,-556,-632,602,200,924,656,193,459,347,588,-771,-329,-861,-77,-370,873,-110,672,-61,-790,525,639,-394,606,-74,711,-300,924,528,78,-696,670,232,378,700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{246,510,876,941,426,-786,-587,830,984,-876,-392,498,-27,-531,-584,-584,558,-254,-943,235,-307,-636,818,-826,-356,-114,546,566,602,-712,-261,448,205,521,-507,-929,-556,176,-998,657,125,-952,-225,836,818,-985,300,-210,84,-201,486,341,-382,886,888,974,-57,191,466,70,-709,-136,414,-446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("CLOSURE_SOURCE:java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "compile(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):com.google.javascript.jscomp.Result",
            new int[]{1000,30,-863,612,-1000,-251,26,674,-1000,-548,-646,1000,251,-1000,-175,213,-1000,-207,-63,30,-465,662,-620,-337,-928,-536,1000,68,721,-1000,-250,997,-689,-9,1000,785,18,-25,1000,-247,353,-1000,546,137,-380,-398,938,210,-686,-750,128,-311,1000,144,-383,302,-261,504,659,-634,1000,280,933,880}));
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
            new int[]{-1000,671,1000,-622,-183,-81,1000,-75,332,-838,392,-1000,-1000,530,-1000,-212,194,1000,1000,322,-49,-261,-64,-75,-1000,495,1000,-1000,1000,-1000,-486,897,653,-412,-813,-144,-180,556,507,-493,671,862,-641,-1000,607,-1000,1000,362,729,311,181,-225,1000,-1000,409,653,586,-61,-1000,-738,1000,-303,352,-381}));
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
            new int[]{-22,-1000,1000,-58,1000,-451,1000,526,-89,-87,-245,-657,-857,502,-51,-770,257,459,579,-6,852,107,498,-304,-1000,3,-677,-277,-192,-631,601,0,-107,-102,0,482,-350,-711,-479,33,97,145,-948,507,0,780,-472,-895,-497,1000,-528,-896,1000,-716,593,889,-744,0,1000,363,-140,-88,294,-445}));
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
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getResult():com.google.javascript.jscomp.Result",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getReverseAbstractInterpreter():com.google.javascript.jscomp.type.ReverseAbstractInterpreter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getRoot():com.google.javascript.rhino.Node",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceLine(java.lang.String,int):java.lang.String",
            new int[]{-701,-378,-400,415,163,446,-196,791,784,-127,-187,-400,400,-851,-895,-264,446,-519,962,1000,252,1000,668,293,-230,341,-49,-361,-1000,400,-33,-1000,-44,121,1000,-265,-1000,-1000,400,1000,200,992,-1000,1000,968,-674,1000,-618,68,400,1000,1000,-112,424,-494,-1000,-400,-21,-1,1000,-634,303,325,801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceLine(java.lang.String,int):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceLine(java.lang.String,int):java.lang.String",
            new int[]{827,-717,149,-194,-448,-388,33,659,-469,-599,-599,-847,670,-149,-269,-916,-327,-788,156,-11,-763,-235,-429,-210,-622,126,99,-771,877,366,-573,209,-605,-721,282,390,151,229,-222,893,-791,457,-284,827,440,435,-958,-514,959,975,-378,524,497,98,243,219,-468,-598,-668,-18,-717,643,260,-291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceMap():com.google.javascript.jscomp.SourceMap",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceRegion(java.lang.String,int):com.google.javascript.jscomp.Region",
            new int[]{532,-888,176,692,-415,-474,-71,922,-145,182,-106,760,-19,-72,127,228,-125,-982,-110,-227,781,-553,115,-878,708,900,646,-865,378,-305,684,-852,-306,251,488,529,-400,-658,-921,874,-818,664,381,634,847,-887,-921,796,-471,560,-681,709,997,-116,286,-856,-459,-435,695,-774,588,-776,-714,-974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceRegion(java.lang.String,int):com.google.javascript.jscomp.Region",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getSourceRegion(java.lang.String,int):com.google.javascript.jscomp.Region",
            new int[]{130,0,72,-1000,-488,287,140,-1000,0,-551,-6,534,1000,-716,44,0,511,254,77,1000,361,925,-697,-101,-1000,-490,-677,-306,-791,163,0,0,1000,1000,89,-367,-255,-827,-1000,202,1000,397,-135,126,496,-716,-1000,820,-1000,0,427,-793,0,0,79,636,344,-165,1000,0,-1000,899,1000,-293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.Compiler$IntermediateState", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getState():com.google.javascript.jscomp.Compiler$IntermediateState",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getTopScope():com.google.javascript.jscomp.Scope",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getTypeRegistry():com.google.javascript.rhino.jstype.JSTypeRegistry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getTypedScopeCreator():com.google.javascript.jscomp.MemoizedScopeCreator",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getWarningCount():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "getWarnings():com.google.javascript.jscomp.JSError[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "hasErrors():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{641,582,-666,1000,-249,194,-471,251,-191,261,-745,-681,753,318,994,-884,-96,-1000,-257,-1000,230,-115,-1000,1000,637,-687,-519,396,-312,-683,864,1000,205,-62,637,-1000,6,963,294,-292,-735,-167,222,1000,-704,874,-360,418,255,460,314,213,-506,1000,-879,899,-238,44,787,1000,795,-485,-101,734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{178,-1000,1000,112,571,-503,-1000,491,-612,-972,-456,1000,1000,-1000,1000,1000,1000,-1000,137,-917,1000,-1000,1000,-1000,-246,1000,159,126,1000,1000,780,1000,1000,-1000,1000,1000,-733,-378,966,-507,-216,-1000,-1000,813,-1000,1000,1000,-1000,-1000,-217,1000,1000,1000,-468,-511,1000,636,175,690,-1000,1000,115,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSModule[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{-684,-1000,-110,-130,271,-1000,-1000,303,1000,1000,-124,869,199,864,224,202,1000,-345,3,-1000,511,282,575,-166,92,931,1000,1000,-118,1000,-598,-578,-53,474,554,1000,908,592,-798,-262,65,-955,48,806,-451,240,544,-512,-59,271,319,1000,194,151,1000,-1000,529,-273,-473,-572,574,650,-62,49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{-160,-24,-1000,-342,374,-1000,-418,-1000,874,-1000,1000,762,-8,963,1000,-1000,450,585,438,124,-1000,-122,-105,-570,-808,-448,-586,3,-223,-265,-569,1000,635,1000,-42,-890,697,218,593,-6,-109,1000,669,-436,535,-939,-164,-961,234,1000,-1000,-425,702,907,-1000,359,222,-198,785,104,883,1000,-270,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.JSSourceFile[],com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{-741,-129,-983,-597,538,408,-214,881,835,-424,106,322,-144,466,246,-564,468,58,-855,73,-357,898,-804,107,19,-466,40,-54,-171,-375,952,145,-406,-660,350,-189,-246,9,-56,-592,168,-129,179,-87,298,-845,709,-713,-183,804,892,751,-643,651,529,-570,166,23,424,618,-442,429,-279,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "init(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{-1000,998,-1000,-1000,-1000,989,-272,-340,-1000,1000,1000,-968,914,-1000,921,88,-83,-1000,274,-1000,-1000,-1000,-1000,718,-1000,1000,-42,1000,755,-1000,198,253,-1000,-1000,-1000,1000,1000,-1000,-622,-133,-73,1000,1000,-1000,1000,538,856,43,-1000,664,-287,1000,1000,423,862,1000,1000,1000,258,-1000,-41,-154,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initModules(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initModules(java.util.List,java.util.List,com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{-1000,-1000,1000,140,461,23,1000,160,-807,-30,864,-989,-1000,64,1000,766,-1000,-724,-1000,1000,-1000,905,-340,1000,404,-949,272,-701,296,333,421,531,864,-1000,1000,-618,229,43,528,1000,283,4,537,-956,361,824,286,127,-482,342,585,-369,1000,412,1000,-709,-829,378,-995,926,648,629,-539,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initOptions(com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "initOptions(com.google.javascript.jscomp.CompilerOptions):void",
            new int[]{512,-610,302,-324,250,492,-312,416,-495,616,166,-90,504,-855,913,-297,-453,130,571,-968,-383,-82,30,513,-444,-940,-540,-117,-804,-599,190,896,-234,804,-707,591,-22,-899,69,786,306,-453,-983,885,-875,310,-676,856,634,338,446,-386,-226,-675,901,902,-277,439,-934,284,956,972,461,858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "isIdeMode():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "isTypeCheckingEnabled():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "languageMode():com.google.javascript.jscomp.CompilerOptions$LanguageMode",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "newExternInput(java.lang.String):com.google.javascript.jscomp.CompilerInput",
            new int[]{876,-636,-778,-116,223,504,-932,-542,130,817,605,-976,-228,904,-226,-809,912,-541,546,28,125,676,-987,-365,-956,762,466,282,738,-940,-229,-113,-501,206,270,607,493,-670,433,-133,-971,365,535,307,495,196,-273,-212,-559,632,-936,868,-294,-82,283,957,-966,-115,-582,-230,-811,-264,693,-416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "normalize():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "optimize():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse():void",
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
            new int[]{-414,-586,272,-712,-994,216,-298,875,-506,-719,899,-865,-940,609,-689,-167,-707,-752,-875,962,-853,909,-682,753,415,-249,145,0,676,-530,816,-289,320,85,-73,-379,-434,-899,586,-338,-680,-898,101,-859,183,222,-365,-913,954,-520,245,-16,-447,-542,407,-62,-511,442,266,-771,-337,828,-364,150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{895,-897,-748,-247,-322,698,-200,322,-252,-295,-545,547,69,-766,-359,18,-205,683,-318,871,-478,809,-781,-514,49,-101,-695,-261,31,-465,29,-333,-283,686,333,904,284,139,-965,-137,-780,-314,-944,-782,-362,-157,-696,30,-530,437,772,542,-926,-119,-995,-436,944,-395,298,226,926,-214,-271,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{-215,-573,-749,256,-1000,592,-544,641,961,106,145,68,536,388,104,-1000,435,-652,-783,39,-1000,18,-243,-354,282,667,-449,-248,-799,-762,935,797,335,1000,455,146,-289,-226,-901,-266,-228,-41,967,-1000,375,-300,534,11,1000,62,-1000,1000,192,-655,880,-636,481,686,-189,-105,-902,251,-123,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{329,-1000,-1000,-90,-305,985,-398,-165,40,-1000,-461,520,322,-519,301,201,-705,-301,-886,468,-376,644,-1000,-726,89,-469,-994,-785,-851,-913,1000,12,194,915,527,1000,-427,-573,-1000,95,-1000,-438,-651,-1000,3,301,5,-92,381,894,-442,1000,-336,255,346,-59,1000,-225,1000,-27,215,-492,-1000,-953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "parse(com.google.javascript.jscomp.SourceFile):com.google.javascript.rhino.Node",
            new int[]{-226,-570,-152,-358,940,294,607,280,-282,101,325,117,491,536,-867,-296,-361,926,-22,620,-1000,1000,-976,-266,365,475,-616,-203,573,-304,-831,-172,-35,768,736,288,23,433,406,37,-94,-449,-792,-276,-301,279,-1000,242,-612,-366,598,650,-461,-575,-1000,-475,166,-408,360,-108,-24,104,-650,173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "processDefines():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "rebuildInputsFromModules():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{628,-630,902,844,-50,-113,80,-221,-962,-622,-240,163,-994,-739,630,-428,244,-932,-679,562,616,-50,-878,926,703,36,-120,-339,784,140,777,-49,-790,47,-189,-345,-363,678,-224,-761,-838,-196,260,860,849,649,800,427,344,-286,-342,811,-678,490,-55,-846,356,-692,-225,201,493,526,-19,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{-73,171,-73,-174,-434,-433,693,823,382,240,-380,-325,-835,-58,-887,-129,-402,-686,-210,614,-981,945,-319,-450,-648,-63,-687,-503,107,-265,-334,-272,394,704,55,-451,628,-4,978,-645,-747,-369,730,140,-794,-626,122,500,-938,16,-923,604,869,934,27,-722,517,-530,79,-588,-861,551,719,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{434,-918,1000,782,392,-182,191,128,-1000,-1000,-698,457,-812,-1000,1000,-59,284,-1000,-917,553,646,-254,-1000,69,480,-303,113,-476,858,371,614,-526,-662,294,-433,-796,-236,544,-337,-1000,-607,-29,234,890,760,-72,-222,568,542,-553,-361,936,-1000,503,163,-1000,223,332,-263,-405,583,545,-306,770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "replaceScript(com.google.javascript.jscomp.JsAst):void",
            new int[]{463,138,1000,-47,1000,518,563,1000,161,-1000,-1000,352,265,-106,1000,652,246,-1000,-735,605,1000,562,-1000,190,756,-1000,80,-994,410,216,1000,-639,315,617,243,-1000,285,887,-845,-1000,434,246,-611,838,1000,777,-652,-612,959,-788,-332,1000,-822,629,1000,-786,-1000,-746,-652,168,-920,351,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "report(com.google.javascript.jscomp.JSError):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "reportCodeChange():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.Compiler", "com.google.javascript.jscomp.Compiler", "setErrorManager(com.google.javascript.jscomp.ErrorManager):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
