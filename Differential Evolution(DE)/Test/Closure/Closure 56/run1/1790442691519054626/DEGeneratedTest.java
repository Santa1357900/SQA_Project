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
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "clearCachedSource():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "clearCachedSource():void",
            new int[]{-193,-932,917,-376,-829,-162,-555,584,-92,706,-672,-745,147,-713,364,83,-829,444,414,79,-867,-246,-290,-864,-924,125,552,75,-721,906,37,-176,-869,-546,699,-987,-558,782,656,-774,379,-331,-819,-823,-924,-608,-484,-854,366,112,147,-842,43,-136,793,602,629,94,-93,-770,669,-262,634,-157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "clearCachedSource():void",
            new int[]{280,921,-177,-677,-129,-423,0,-845,-249,-522,960,-906,-330,493,58,199,-723,-758,203,111,906,742,57,162,-819,-880,11,647,-514,24,142,-546,-464,979,526,103,981,594,55,229,-726,-733,-782,558,-118,853,-847,-527,-697,591,-229,-534,356,-475,-299,926,720,-346,397,-4,-197,-940,-114,596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "clearCachedSource():void",
            new int[]{618,-436,819,772,-54,831,-536,489,608,757,555,-189,582,-305,-250,-440,-532,572,-465,682,-145,157,-74,306,10,159,-841,304,64,644,132,846,-983,849,-388,-613,-431,138,-90,-563,137,618,-905,-499,186,689,-348,672,475,-309,971,-538,-250,-280,3,696,-40,-310,-957,-440,-821,736,-325,190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "clearCachedSource():void",
            new int[]{-193,-458,905,363,-98,483,-976,559,-92,481,414,-841,-329,-502,-142,-1000,-486,898,414,79,238,15,49,-586,69,125,324,1000,904,914,-127,-130,-981,-546,-535,-1000,-363,782,29,-1000,382,-288,-1000,-616,540,1000,527,1000,366,-435,317,135,-882,-414,-740,644,-104,-246,-454,-770,-1000,107,-927,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$Preloaded", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromCode(java.lang.String,java.lang.String):com.google.javascript.jscomp.SourceFile",
            new int[]{-599,-496,175,93,894,-691,-412,-978,391,-833,409,-457,508,-218,-176,709,-84,-198,914,-181,-649,-4,824,-503,635,437,51,109,-871,724,-54,644,539,-202,-333,-388,-625,729,812,-735,411,-972,-250,-506,525,483,274,132,6,293,609,71,2,-633,-964,595,175,68,-545,638,-188,364,26,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromCode(java.lang.String,java.lang.String):com.google.javascript.jscomp.SourceFile",
            new int[]{-68,-955,-106,-1000,557,72,164,660,423,197,28,-492,157,-299,-1000,1000,-536,-332,442,-730,275,-41,-559,177,1000,82,-632,-222,440,-252,-200,-776,-192,414,724,546,-30,801,-970,-437,-674,99,-224,127,-949,243,-727,426,-980,360,695,181,193,-695,147,-114,971,-385,893,-218,298,183,-957,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromCode(java.lang.String,java.lang.String):com.google.javascript.jscomp.SourceFile",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$Preloaded", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromCode(java.lang.String,java.lang.String,java.lang.String):com.google.javascript.jscomp.SourceFile",
            new int[]{-356,-202,220,-186,431,-154,26,-168,-95,679,805,-27,915,671,807,-927,884,-972,835,-261,981,-992,-624,-590,480,141,-666,755,-113,-709,197,419,-945,47,-287,677,962,-86,289,-576,84,-982,-787,114,361,660,558,754,-354,925,140,-135,-776,-207,-721,-559,491,-298,253,372,214,-643,346,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromCode(java.lang.String,java.lang.String,java.lang.String):com.google.javascript.jscomp.SourceFile",
            new int[]{721,-725,-784,-954,431,681,-939,907,459,-630,-1000,631,1000,152,-168,-927,-221,-307,323,1000,262,1000,-762,-853,939,310,-195,755,-818,1000,212,757,753,-1000,590,1000,137,218,460,-847,-924,-982,-599,1000,989,525,558,-1000,-626,1000,557,-31,-358,257,898,-627,298,-298,253,-527,-252,-337,-677,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromCode(java.lang.String,java.lang.String,java.lang.String):com.google.javascript.jscomp.SourceFile",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$OnDisk", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromFile(java.io.File):com.google.javascript.jscomp.SourceFile",
            new int[]{-505,-142,692,661,118,329,-667,-825,438,-180,141,-852,-143,-521,-934,-108,525,374,21,584,-95,756,-679,-544,-678,243,468,-210,-701,-229,643,-829,-962,-79,967,637,751,-598,-921,901,-281,-168,933,-327,-80,785,819,-762,883,55,586,919,-4,504,796,827,-51,529,93,-602,-815,161,491,-398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromFile(java.io.File):com.google.javascript.jscomp.SourceFile",
            new int[]{-220,-436,-817,165,-325,24,826,691,-135,-132,49,-497,11,-444,373,-426,-123,452,174,-633,-165,-626,815,126,-969,455,-634,152,-404,-775,-197,-444,-186,-400,-671,-778,520,-420,912,200,-837,-79,-481,622,939,-251,-395,357,216,177,-596,-896,43,-672,-704,-614,974,-130,-870,-4,664,-857,730,-143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$OnDisk", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromFile(java.io.File,java.nio.charset.Charset):com.google.javascript.jscomp.SourceFile",
            new int[]{-854,593,595,-858,108,403,169,-505,-557,-421,495,318,-888,-240,-718,-10,301,991,44,-557,-939,431,651,-595,887,986,149,-437,978,992,128,237,904,547,999,962,-950,-339,-853,170,-680,4,140,84,331,-324,-91,874,-386,-797,137,150,162,718,-38,-57,91,-806,-447,-523,153,471,111,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromFile(java.io.File,java.nio.charset.Charset):com.google.javascript.jscomp.SourceFile",
            new int[]{656,-66,-400,-18,125,-97,654,-69,248,396,-194,-814,899,-415,-134,-433,822,-693,241,-933,869,420,-434,55,-187,92,452,40,-332,-870,373,40,500,925,-260,430,272,148,881,931,-535,926,-194,314,899,921,393,343,499,-542,-49,-502,562,660,-3,-896,-482,379,505,-372,-880,692,-94,108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$OnDisk", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromFile(java.lang.String):com.google.javascript.jscomp.SourceFile",
            new int[]{931,-847,-546,46,-40,-818,128,-183,-818,337,-972,-219,-792,795,-784,-865,560,291,890,814,-941,814,-215,-170,-179,454,392,-505,-197,-42,-602,-542,660,-226,550,959,-23,691,-433,347,-112,-488,609,-511,-448,639,361,-549,-38,932,-180,-440,215,759,-477,-173,-535,744,-872,561,556,-583,-977,991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromFile(java.lang.String):com.google.javascript.jscomp.SourceFile",
            new int[]{-351,-419,-965,350,-241,-775,-204,-781,850,-548,-454,767,-434,868,482,812,183,-665,-544,503,-65,-899,86,822,-889,-643,979,-263,-129,475,916,111,464,-554,713,-957,923,330,-199,-146,203,876,-463,604,-823,923,758,-286,988,-362,144,40,-212,-748,584,-424,-880,499,938,-141,-145,-294,335,94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$OnDisk", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromFile(java.lang.String,java.nio.charset.Charset):com.google.javascript.jscomp.SourceFile",
            new int[]{-603,556,796,-501,-195,213,-894,-50,-681,-804,-994,-428,-199,-504,-422,551,846,-668,165,-10,-139,-803,618,-27,520,939,101,94,-911,110,401,-739,-923,-787,63,645,988,651,396,-490,-439,-772,-585,289,-178,835,-872,799,-479,614,-782,-264,-937,337,147,711,943,983,972,20,845,592,-249,35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromFile(java.lang.String,java.nio.charset.Charset):com.google.javascript.jscomp.SourceFile",
            new int[]{929,863,763,-357,654,946,423,-488,385,-432,-720,694,593,648,179,783,-344,-788,961,508,-699,-972,508,-961,-742,823,-785,-51,890,873,570,674,-449,87,-16,385,697,-32,670,26,-536,-221,430,-76,-726,-872,-459,885,351,-236,568,-48,-469,-139,-95,133,649,-683,134,289,-46,-353,-159,-864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$Generated", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromGenerator(java.lang.String,com.google.javascript.jscomp.SourceFile$Generator):com.google.javascript.jscomp.SourceFile",
            new int[]{636,771,371,358,314,36,372,-63,-310,-535,665,-306,-373,-858,-317,424,988,-718,20,203,514,242,786,800,-223,709,811,973,694,-663,-869,-651,796,-34,-186,-370,-523,214,82,688,789,-290,-20,-267,995,-450,350,194,44,623,-234,-774,-566,-599,261,790,494,-491,175,-815,-978,-82,244,574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromGenerator(java.lang.String,com.google.javascript.jscomp.SourceFile$Generator):com.google.javascript.jscomp.SourceFile",
            new int[]{-207,416,844,-362,67,-631,294,-439,-471,-40,-990,-80,-21,164,-65,192,519,367,-81,-814,23,619,-372,915,-470,473,-385,-646,216,-885,-356,-315,-718,-684,-37,705,-838,-604,170,-176,743,887,-460,778,985,-127,265,598,-734,-932,-110,627,-948,-374,-543,479,104,404,-690,-307,-708,-57,-620,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromGenerator(java.lang.String,com.google.javascript.jscomp.SourceFile$Generator):com.google.javascript.jscomp.SourceFile",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SourceFile$Preloaded", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromReader(java.lang.String,java.io.Reader):com.google.javascript.jscomp.SourceFile",
            new int[]{287,768,899,329,-368,386,394,-675,940,-712,-790,-280,771,-505,354,-953,526,-847,-495,-710,116,78,756,-109,-684,161,-830,-537,910,449,531,167,555,876,266,871,-829,-659,224,996,654,99,-302,248,674,-708,-117,316,-537,-142,857,-947,577,-995,-964,-329,874,989,-896,-974,187,32,-591,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromReader(java.lang.String,java.io.Reader):com.google.javascript.jscomp.SourceFile",
            new int[]{719,62,927,825,432,681,393,-1000,328,-1000,-961,-923,177,128,-454,-459,-410,-1000,-1000,-1000,412,1000,-355,27,899,-422,138,-742,680,887,-653,-635,78,-568,865,893,695,-563,-108,39,-100,-636,634,395,92,-477,447,-195,-1000,258,974,-20,-792,-624,-766,-447,1000,1000,107,-110,607,-520,-1000,-365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "fromReader(java.lang.String,java.io.Reader):com.google.javascript.jscomp.SourceFile",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:ZnVuY3Rpb24gZih1bnVzZWQsIHVzZWQpIHsgdmFyIGxvY2FsID0gMTsgcmV0dXJuIHVzZWQ7IH0gZigxLCAyKTs=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getCode():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:ZnVuY3Rpb24gZigpIHsgdmFyIHVudXNlZCA9IDE7IHZhciB1c2VkID0gMjsgcmV0dXJuIHVzZWQ7IH0gZigpOw==", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getCode():java.lang.String",
            new int[]{143,-549,-135,-196,837,498,875,0,-664,-806,298,216,-131,-175,-324,-15,363,-108,678,-968,27,647,715,-879,-576,883,505,44,-284,-414,148,-18,-183,774,180,301,507,264,-675,463,-775,-539,-756,211,218,-686,407,-595,-16,537,434,1000,-432,-103,717,-491,-874,935,973,10,431,810,454,-736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:ZnVuY3Rpb24gZih4KSB7IHZhciBmaXJzdCA9IHg7IGZpcnN0ID0gMzsgcmV0dXJuIHg7IH0gZigyKTs=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getCode():java.lang.String",
            new int[]{-582,507,20,-656,299,752,-719,762,-307,-904,876,970,-46,891,667,314,233,915,126,337,-628,607,373,638,216,-991,-227,-751,-127,-637,443,56,443,960,986,657,-897,344,-42,-363,-943,-770,-825,-265,-137,619,-178,605,868,-477,-497,-55,-869,401,872,681,-926,-7,277,562,-689,-448,5,-959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:ZnVuY3Rpb24ga2VlcCgpIHsgdmFyIGRlYWQgPSAxOyByZXR1cm4gNzsgfSBrZWVwKCk7", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getCode():java.lang.String",
            new int[]{735,164,-741,-668,25,-375,125,680,-862,798,-570,847,490,-413,911,-133,-622,-698,-799,992,-721,-672,340,-153,754,960,996,-97,176,800,979,-227,-452,790,561,673,-13,-554,352,-968,-858,-873,-960,-352,729,-701,-809,-901,680,-350,-558,-858,964,-350,973,-260,-937,702,901,-936,279,-640,-615,-705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.String:ZnVuY3Rpb24gZigpIHsgdmFyIHVudXNlZCA9IDE7IH0gZigpOw==", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getCode():java.lang.String",
            new int[]{271,68,480,284,67,853,-755,299,-548,-762,-136,459,333,366,713,-775,-259,451,-782,722,820,583,-85,468,-742,60,-967,527,822,627,623,-472,699,-391,170,-162,661,-559,-109,795,-252,-618,-545,-643,107,291,758,-216,-97,-731,-468,55,-185,-292,105,579,522,-196,388,481,-203,969,329,244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:java.io.StringReader", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getCodeReader():java.io.Reader",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:java.io.StringReader", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getCodeReader():java.io.Reader",
            new int[]{751,-519,-577,-712,-652,339,-661,-292,-57,420,258,642,31,925,17,-63,437,-508,-593,365,-23,964,-963,779,-825,-485,-427,-298,-667,-522,-973,-789,731,-829,902,930,975,-713,-99,-944,-403,-970,-369,-119,-123,-959,-721,-924,-427,411,-398,-915,750,-437,725,-532,-793,-345,-788,715,164,294,-306,-833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:java.io.StringReader", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getCodeReader():java.io.Reader",
            new int[]{-94,-211,976,449,54,862,66,-223,-787,-311,-738,157,585,-755,547,237,-939,89,-869,260,574,-658,961,-468,495,-598,-993,335,696,-438,314,-988,364,629,-68,811,883,-63,751,247,224,-260,-23,-742,-893,-163,-451,-791,-56,-455,-860,-60,59,619,554,-520,-994,-429,-698,-220,-57,-997,943,897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:java.io.StringReader", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getCodeReader():java.io.Reader",
            new int[]{-611,-132,-473,-104,860,-498,6,419,217,270,-326,414,586,49,660,987,656,-939,-344,198,59,360,-769,-862,536,-233,161,-698,-223,948,567,-977,-690,-731,743,-428,53,356,476,-270,-790,-144,390,-875,100,-179,390,-567,180,322,623,654,52,444,-445,-866,-446,651,-265,702,174,644,595,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:java.io.StringReader", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getCodeReader():java.io.Reader",
            new int[]{-24,-683,-730,-403,-22,187,802,21,-487,744,343,183,461,-407,12,997,459,966,-102,38,-792,185,399,-237,-210,29,-487,321,581,590,62,80,764,160,-150,924,-284,-21,742,494,-375,-737,347,96,-445,817,519,777,-101,-237,270,-815,648,438,851,-721,-749,-654,-300,-310,-58,-787,-196,-417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.String:ZnVuY3Rpb24gZigpIHsgdmFyIHVudXNlZCA9IDE7IHZhciB1c2VkID0gMjsgcmV0dXJuIHVzZWQ7IH0gZigpOw==", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLine(int):java.lang.String",
            new int[]{-929,76,-602,-670,-461,-214,623,748,-172,882,-525,772,-369,-866,-458,788,-572,-764,-284,-606,-469,194,645,-294,279,749,-689,934,295,-503,-167,-634,748,600,-844,892,100,208,-665,-764,-38,-909,-170,-809,379,-677,-582,312,79,-639,113,-234,93,340,857,-659,-525,-982,613,960,-174,-584,992,-511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLine(int):java.lang.String",
            new int[]{634,-600,-850,-104,404,947,319,837,482,932,-162,-58,268,250,-750,681,881,739,-499,272,-434,-614,-107,-624,-925,122,833,-780,138,585,688,979,142,-594,-442,-768,-306,-154,-95,-643,885,-46,-394,-171,735,-214,-760,594,585,713,197,-130,985,327,-466,88,34,-942,-584,-998,-972,-745,-536,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.String:ZnVuY3Rpb24gZih1bnVzZWQsIHVzZWQpIHsgdmFyIGxvY2FsID0gMTsgcmV0dXJuIHVzZWQ7IH0gZigxLCAyKTs=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLine(int):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:ZnVuY3Rpb24ga2VlcCgpIHsgdmFyIGRlYWQgPSAxOyByZXR1cm4gNzsgfSBrZWVwKCk7", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLine(int):java.lang.String",
            new int[]{393,484,1000,-228,865,-577,-15,371,-701,-776,-553,-939,1000,1000,714,828,508,-26,-2,-233,789,-508,-176,-357,-132,-1000,1000,-203,-554,118,827,278,-501,-91,251,353,-661,740,321,471,-111,551,55,603,-949,506,-384,-932,-318,1000,649,942,458,333,-113,-256,-393,133,-1000,-112,-196,-445,-795,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.String:ZnVuY3Rpb24gZigpIHsgdmFyIHVudXNlZCA9IDE7IH0gZigpOw==", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLine(int):java.lang.String",
            new int[]{-184,273,1000,-548,-47,-1000,234,1000,75,247,-418,-1000,1000,-614,841,-860,-561,1000,-696,-1000,-1000,273,83,153,-172,-1000,1000,1000,-554,179,1000,-761,-961,939,492,888,713,-1000,-102,-1000,-867,-411,306,68,-1000,-97,38,-1000,-1000,1000,649,794,281,333,-660,-256,854,-646,-281,-204,-70,-549,511,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.String:ZnVuY3Rpb24gZih4KSB7IHZhciBmaXJzdCA9IHg7IGZpcnN0ID0gMzsgcmV0dXJuIHg7IH0gZigyKTs=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLine(int):java.lang.String",
            new int[]{469,-688,-1000,-920,-779,-1000,1000,-540,626,505,-1000,-610,-782,-336,-1000,-978,-642,449,52,-20,673,280,1000,-1000,-930,351,20,1000,-148,1000,-93,-1000,-594,-1000,-1000,1000,-340,-1000,611,-575,-352,-105,-430,-392,973,1000,-387,-742,-511,-45,-94,76,711,323,244,-1000,617,427,-679,-1000,-954,-241,-140,-175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLineOffset(int):int",
            new int[]{562,168,-617,216,345,14,-225,-153,624,-425,-419,261,487,26,-50,690,-507,-220,-970,-19,-256,295,-381,-447,-186,92,-586,-819,645,3,906,909,-625,290,-976,-589,-238,-781,-600,448,-709,882,824,737,-605,-331,-160,492,287,-392,-246,186,-161,-988,350,-335,657,348,520,649,-937,46,-292,856}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLineOffset(int):int",
            new int[]{644,618,842,-1000,817,-876,72,-972,1000,-142,-1000,-262,-547,409,-1000,-285,-1000,-836,578,-359,-223,772,-976,-63,439,-121,-629,-515,992,-850,-448,1000,277,298,-26,212,506,-684,964,453,452,15,-894,102,1000,-902,-143,-180,441,479,160,-689,-195,-1000,1000,345,165,261,-86,-792,-852,-64,-153,368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLineOffset(int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLineOffset(int):int",
            new int[]{-111,979,138,517,-401,-623,436,747,53,323,-804,524,-237,491,742,-777,-328,27,313,733,872,-62,-904,-377,-360,127,-811,-192,-604,-683,519,732,9,715,-226,107,568,-771,-793,580,198,-760,829,350,807,556,932,726,-276,-16,-835,894,-847,-822,-564,-271,-955,-112,810,418,560,-436,-714,-147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLineOffset(int):int",
            new int[]{-550,696,234,-901,155,-894,-204,-325,949,-478,-974,-48,-580,807,-944,16,-707,-834,872,65,-332,738,-302,107,-102,416,-578,68,294,-240,-279,925,-255,-509,556,-450,968,-22,537,239,610,387,-720,596,702,-307,-662,376,63,662,549,-826,-818,-948,691,437,-167,807,-737,-820,-801,-193,-145,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getLineOffset(int):int",
            new int[]{-119,632,-1000,1000,-1000,-1000,810,933,-1000,389,469,868,64,427,1000,-793,-576,-894,-1000,-19,947,-568,381,-1000,-1000,-250,-1000,1000,-1000,181,906,823,-433,1000,-493,384,-238,-533,-1000,-138,-293,-244,1000,737,722,-82,-239,-659,179,43,-101,710,-1000,-109,-707,-971,-1000,1000,520,105,1000,128,-359,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getName():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getName():java.lang.String",
            new int[]{989,-72,829,-818,-58,940,-774,-891,-399,-798,-881,722,-827,-43,647,277,-786,227,609,-404,-946,235,712,934,-878,-768,226,907,-556,243,964,343,-714,-58,-743,859,-819,569,-87,981,-527,636,-988,719,862,8,-151,-365,447,866,-675,-336,-722,-930,643,199,527,463,328,-709,-948,-225,-770,-797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getName():java.lang.String",
            new int[]{-663,-26,456,-975,-417,-873,-8,-451,-926,145,118,205,-162,147,-290,499,101,596,981,-914,76,850,-705,392,195,323,-675,-473,81,49,990,-886,-731,-855,390,623,-50,-639,237,-175,-114,-253,-812,-971,34,-693,-92,-46,386,432,-302,245,-914,-107,-912,-42,-52,-760,916,-387,191,533,-883,871}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getName():java.lang.String",
            new int[]{-340,-724,-26,-459,-375,-520,54,-30,-879,721,-546,-964,-908,121,781,243,-923,242,652,69,432,-880,61,-548,-379,517,-129,236,963,45,-514,533,-72,467,-518,937,368,252,-674,-454,-97,-778,-848,-194,-889,14,31,-637,119,6,162,288,740,-688,972,-679,-613,552,-749,-884,-335,-840,-877,-163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getName():java.lang.String",
            new int[]{316,292,-821,468,430,-386,-656,178,381,-772,-768,141,505,677,880,-281,142,-609,224,-74,-183,343,-221,-821,-394,41,-257,-836,169,-662,154,-878,-360,230,499,-170,856,-457,473,-410,-765,-797,472,-436,47,653,830,996,767,-99,843,39,-250,-328,140,-847,561,181,137,-578,-65,154,-35,-529}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getOriginalPath():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getOriginalPath():java.lang.String",
            new int[]{-565,-252,-938,-427,639,-259,251,182,750,-926,-553,-387,702,441,181,303,-960,323,915,-507,791,675,405,163,-698,-657,-80,-736,-376,-635,473,-328,228,497,-269,-927,-933,-821,-283,331,277,-518,-927,-181,-797,-437,227,622,-171,-380,566,613,209,421,-553,929,12,106,-272,608,-65,-852,-608,-201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.String:NzUw", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getOriginalPath():java.lang.String",
            new int[]{-987,472,386,255,-908,-750,468,-729,-801,-470,686,903,600,-67,945,147,-38,75,872,-734,-579,-447,100,-482,-626,-903,-926,-246,928,816,-119,111,-934,-277,-952,-153,877,-458,770,-336,-552,177,-387,808,-578,154,374,-308,609,858,-496,-180,-438,453,-765,393,-461,-21,414,-874,924,355,913,-462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.String:LTc2MmUyNjg=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getOriginalPath():java.lang.String",
            new int[]{-373,741,747,-868,886,-643,442,21,-128,-682,-762,-935,268,-227,63,-781,-662,909,427,-923,630,584,-360,744,306,473,664,255,-324,565,250,-821,-317,-899,465,-159,-691,856,214,937,-821,67,983,-769,11,-800,620,471,971,-689,716,729,-530,-227,902,-56,-480,788,-798,598,345,-702,-521,197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getOriginalPath():java.lang.String",
            new int[]{443,534,1000,-513,165,-698,97,-597,-748,1000,-1000,-813,468,-105,339,-609,-457,1000,-707,140,1000,-230,209,230,-771,567,415,-20,-167,-609,793,-627,726,-1000,-323,229,72,-171,1000,710,-41,525,841,-929,27,-932,1000,324,-205,-824,604,-512,252,-1000,-622,325,46,777,425,990,799,-104,950,562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SimpleRegion", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getRegion(int):com.google.javascript.jscomp.Region",
            new int[]{-466,511,-780,-263,184,-471,-267,-855,852,396,-693,-544,-152,874,-640,-470,22,-502,850,-92,-94,467,-211,413,243,288,437,439,-141,-273,421,472,-871,-545,996,-846,-557,943,-517,-760,998,-414,-516,185,-967,855,-70,756,180,334,220,84,-20,908,-254,-140,-171,-911,-12,-2,627,-525,-20,-957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getRegion(int):com.google.javascript.jscomp.Region",
            new int[]{-734,680,814,-637,940,-49,896,527,-825,900,-941,-525,-952,552,-390,-347,640,295,610,359,-133,187,49,193,258,-337,365,301,-405,900,-982,200,642,967,807,-289,326,-997,435,563,682,-852,-611,618,-365,738,-342,358,260,331,15,-777,-453,-443,639,774,-100,497,710,177,604,303,-737,-466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SimpleRegion", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getRegion(int):com.google.javascript.jscomp.Region",
            new int[]{-804,-301,-524,800,220,-159,384,274,852,285,192,831,-562,230,990,701,-158,-316,426,424,779,669,492,-343,-771,553,375,-133,522,-20,770,878,-972,-311,-959,-21,486,675,-584,349,-17,587,-27,950,331,68,620,683,183,-212,-504,-376,-399,368,-754,-535,980,-950,-510,-127,-737,-940,-525,-543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SimpleRegion", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getRegion(int):com.google.javascript.jscomp.Region",
            new int[]{-383,137,-344,3,943,-869,-372,-766,-400,-457,-952,297,168,-422,-766,4,872,651,-404,884,-36,-41,-17,583,977,-157,-199,-390,39,757,-589,-767,766,-540,270,-918,373,-752,98,-939,598,463,-66,544,786,-695,-639,-474,69,-921,432,-636,940,378,-160,-917,-270,-557,741,816,516,947,326,889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.jscomp.SimpleRegion", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "getRegion(int):com.google.javascript.jscomp.Region",
            new int[]{-258,-2,-576,-696,-424,279,-23,-562,-157,298,60,380,-288,-388,-778,-418,877,364,237,3,-720,555,189,-758,-424,-629,-397,-70,837,-821,-560,-3,340,-350,743,-802,364,596,-480,-715,926,-394,32,-354,-883,314,-894,-132,-734,-782,279,-319,-443,-596,-337,-925,-74,186,-761,959,-941,758,-29,-292}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "isExtern():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "isExtern():boolean",
            new int[]{278,424,-61,-486,800,538,428,-28,461,-662,-796,-749,-797,130,189,-412,-448,-230,514,903,914,-131,-895,-948,-169,942,831,-485,-185,-36,638,799,69,220,259,23,-340,751,912,374,514,-754,728,446,988,795,-443,795,-287,-435,273,-480,897,-833,741,111,-648,244,198,-598,128,651,-59,-962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "isExtern():boolean",
            new int[]{-744,517,369,-365,-707,-198,-453,919,515,-445,868,-593,-481,-333,-939,-109,495,288,856,620,604,-129,351,-51,-244,733,830,-710,15,-69,516,-525,520,685,961,856,93,990,581,-322,-513,-381,157,550,635,-732,59,-758,-827,257,402,-595,-965,-117,-611,-654,-37,326,-317,513,420,-213,-190,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "isExtern():boolean",
            new int[]{-330,-269,720,412,586,-434,596,-32,150,-787,219,527,-748,-133,759,-732,-239,58,-668,-611,-15,91,840,759,-660,870,-648,-708,500,486,554,692,-6,-655,52,-514,467,62,81,398,-359,-145,-492,512,378,788,955,950,-805,809,308,283,-910,266,-69,324,580,339,-514,550,499,-343,-568,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "isExtern():boolean",
            new int[]{-981,488,-61,363,568,-719,-195,572,817,-1000,80,1000,-938,-509,1000,211,-448,-1000,-1000,903,-149,231,765,1000,-943,520,-322,624,106,692,197,799,-1000,220,-675,-644,-613,108,104,753,335,-754,305,446,-365,398,354,756,-287,1000,404,-42,-108,215,37,111,-226,-264,-447,-168,128,-69,-744,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID|getOriginalPath=java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "setOriginalPath(java.lang.String):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID|getOriginalPath=java.lang.String:V0g=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "setOriginalPath(java.lang.String):void",
            new int[]{-828,-998,-792,638,841,130,-833,64,-320,-956,-309,-253,371,-528,715,403,-178,145,-908,652,-936,611,242,521,-416,-962,-713,-658,476,86,-236,309,66,-736,138,-503,-885,-621,-230,423,629,627,735,411,-635,705,-71,-78,667,896,-699,824,984,517,711,98,-465,-666,-108,-113,-20,-404,-653,-997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID|getOriginalPath=java.lang.String:", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "setOriginalPath(java.lang.String):void",
            new int[]{680,186,579,978,128,-927,23,-380,506,695,270,610,941,229,741,-817,-981,775,-170,-854,376,429,-262,-403,-213,-80,-333,555,-943,43,911,-545,-747,-526,453,926,-686,-296,123,618,-54,837,134,-19,824,-252,832,831,623,735,906,302,-861,202,-48,369,883,-868,305,-504,-103,159,-656,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID|getOriginalPath=java.lang.String:KzB4ODAwMDAwMDAw", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "setOriginalPath(java.lang.String):void",
            new int[]{293,-96,-909,-958,-1,-106,878,-589,567,869,-923,27,-459,-420,-547,-872,-175,267,578,504,800,942,908,623,496,-690,-364,-288,-950,719,229,378,874,882,-313,856,501,-737,-28,-696,889,-987,839,704,-844,147,-694,852,-879,205,418,711,871,-874,605,950,598,231,950,889,-598,-708,984,255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID|getOriginalPath=java.lang.String:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "setOriginalPath(java.lang.String):void",
            new int[]{-578,498,239,180,-263,-202,719,-317,752,584,718,-484,498,-26,933,699,-808,34,-284,334,-301,-781,-511,351,-876,-961,820,-258,655,-519,494,-493,-991,83,-999,80,-674,-277,-674,-708,226,-836,644,-906,889,180,-405,-816,-551,-305,174,-545,-176,796,966,758,135,679,-573,843,306,669,-753,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "toString():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "toString():java.lang.String",
            new int[]{620,-594,321,564,-711,-157,-356,-325,292,-813,841,-611,-918,-816,-793,-17,-828,239,173,705,300,-216,-968,-513,-226,963,-994,428,367,-220,-900,-25,-669,-869,670,569,-693,-85,-876,836,-764,851,788,306,-293,-494,566,155,624,-407,-213,-569,-440,-370,-667,899,694,-300,-966,-272,868,213,162,9}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "toString():java.lang.String",
            new int[]{941,-641,-159,364,-604,226,194,729,902,447,-883,-144,-551,679,-243,979,-442,-784,-95,-312,65,607,857,-895,-961,263,-427,-858,-505,753,381,818,-268,712,919,-574,-629,123,-465,-354,466,82,-65,-345,422,-863,-489,-287,-131,-247,502,-235,141,-459,-110,182,-447,510,765,-73,-729,-201,807,-845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "toString():java.lang.String",
            new int[]{257,-922,451,527,505,-852,362,-618,570,-880,881,-702,-726,16,-703,572,378,622,-57,647,-992,992,-548,-358,-338,127,775,777,-248,-946,996,720,-219,755,-577,656,-471,244,-55,-774,878,-551,-771,-595,316,480,-17,834,-470,-590,201,-819,-967,711,-922,671,-714,146,132,-875,409,-896,-83,528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.String:ZGUtaW5wdXQuanM=", DEReplay.run(
            "com.google.javascript.jscomp.SourceFile", "com.google.javascript.jscomp.SourceFile", "toString():java.lang.String",
            new int[]{-778,92,727,440,-866,-540,-346,445,145,-752,480,-830,-852,-445,994,-539,576,-233,578,-243,482,-405,-897,864,-844,960,581,941,503,-506,-637,-240,589,-363,-869,73,973,154,-516,-729,779,-746,-544,412,786,454,-32,199,-733,-204,-71,478,773,142,-559,-537,-376,843,425,-971,-537,-685,-143,-866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerInput", "com.google.javascript.jscomp.CompilerInput", "getLineOffset(int):int",
            new int[]{706,-787,544,-555,-540,846,-291,79,-595,903,-307,625,-158,462,-205,364,783,387,276,-703,373,-399,485,-874,-150,-566,-969,-54,-610,324,4,484,-906,-38,579,-825,-391,595,998,-322,462,-333,-99,-472,-975,222,213,549,-778,-424,-568,353,594,646,-982,-171,-879,9,828,-157,-652,309,49,-429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerInput", "com.google.javascript.jscomp.CompilerInput", "getLineOffset(int):int",
            new int[]{218,-787,-1000,990,525,846,-291,269,532,907,-1000,558,-273,-1000,679,321,-162,-617,-995,274,2,-753,452,-36,-1000,782,-969,-611,985,834,458,1000,986,-1000,820,113,-12,851,172,1000,462,933,-832,-1000,609,-921,1000,-559,-1000,499,-568,353,-315,-185,-460,1000,-1000,-1000,129,1000,-142,219,-570,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerInput", "com.google.javascript.jscomp.CompilerInput", "getLineOffset(int):int",
            new int[]{489,-301,515,16,-517,-878,758,130,692,539,-446,-131,109,-1000,-293,227,525,-522,-482,221,895,-508,120,-377,-1000,228,-433,-555,-21,288,457,1000,851,-127,-788,-387,117,403,-202,70,753,1000,-1000,-1000,551,71,789,-488,-900,937,-240,1000,-451,-626,-369,230,-1000,-1000,-15,695,-117,474,-481,595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerInput", "com.google.javascript.jscomp.CompilerInput", "getLineOffset(int):int",
            new int[]{-81,1000,430,-103,-1000,1000,-325,168,823,-454,723,969,66,132,-1000,-152,183,909,-1000,-1000,526,861,797,-656,760,205,174,148,-546,79,795,908,-289,825,-348,-680,94,869,-619,68,-713,631,971,-526,-141,-1000,-521,972,517,136,-377,-121,904,-631,-436,-269,-754,-61,1000,383,918,-155,398,-632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerInput", "com.google.javascript.jscomp.CompilerInput", "getLineOffset(int):int",
            new int[]{-640,1000,515,1000,-1000,-745,-1000,-489,-896,-537,627,455,856,-367,-1000,240,1000,1000,-1000,-774,811,-942,105,-1000,908,827,-767,1000,-1000,-1000,109,535,216,1000,-1000,-406,581,-330,-530,-705,-1000,1000,-32,-50,-431,-1000,-566,1000,1000,248,474,-712,685,-1000,-851,-833,-936,-373,1000,400,-1000,15,1000,-633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerInput", "com.google.javascript.jscomp.CompilerInput", "getNumLines():int",
            new int[]{435,-302,481,635,15,812,-149,836,-647,-729,626,-907,94,-378,543,-716,212,-354,98,866,-912,-700,-257,-420,-184,255,-564,-655,-882,104,-534,250,-981,259,807,-695,-283,-414,-956,-609,0,892,376,-269,-39,537,-872,98,802,631,-234,-996,38,571,495,-882,-403,-42,172,-689,938,380,-508,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerInput", "com.google.javascript.jscomp.CompilerInput", "getNumLines():int",
            new int[]{687,-200,544,-692,433,82,709,819,-356,351,-697,-333,-20,-37,-260,812,-315,864,-891,736,220,96,778,517,845,784,621,-978,-337,795,-53,281,-614,-789,-521,800,-592,-203,577,-603,635,-294,311,-12,-31,798,-37,693,-655,193,65,605,-220,-107,-826,600,23,688,255,726,83,917,-252,-178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerInput", "com.google.javascript.jscomp.CompilerInput", "getNumLines():int",
            new int[]{169,131,184,-706,-621,-792,-419,267,-131,71,-292,-321,-846,-224,842,331,-297,-537,621,365,-933,418,-780,-787,-618,-663,230,-339,294,757,337,-646,101,593,-816,-210,228,729,-358,664,10,682,132,276,224,526,600,647,782,-567,284,972,595,494,511,-929,-291,778,-571,-350,-459,3,-239,309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerInput", "com.google.javascript.jscomp.CompilerInput", "getNumLines():int",
            new int[]{-611,130,-12,-991,100,-377,673,-376,26,-281,104,268,40,419,459,-708,-744,-392,955,414,-476,-554,-7,-725,-685,999,-481,-2,-233,545,886,64,-605,-39,849,-502,-695,606,115,-774,-389,-808,-674,-743,202,-701,279,314,-998,-645,-240,519,443,-787,-572,-250,-17,761,-111,453,-804,-608,257,824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.jscomp.CompilerInput", "com.google.javascript.jscomp.CompilerInput", "getNumLines():int",
            new int[]{-416,490,-66,1000,-574,121,-1000,526,379,291,184,-1000,-43,-559,281,-1000,1000,303,-963,736,-77,-361,513,-837,-1000,-827,-249,275,-646,-1000,-262,413,56,-1000,-166,-646,720,-1000,-1000,27,434,1000,87,535,824,145,-297,306,331,-194,-352,-405,-684,14,418,-326,-890,-865,-308,-726,920,-205,-964,-199}));
    }
}
