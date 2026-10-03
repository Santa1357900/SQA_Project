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
            "com.google.javascript.rhino.jstype.JSType", "", "isEquivalent(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.JSTypeExpression", "com.google.javascript.rhino.JSTypeExpression", "evaluate(com.google.javascript.rhino.jstype.StaticScope,com.google.javascript.rhino.jstype.JSTypeRegistry):com.google.javascript.rhino.jstype.JSType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.Node", "com.google.javascript.rhino.Node", "checkTreeTypeAwareEqualsSilent(com.google.javascript.rhino.Node):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionParamBuilder", "com.google.javascript.rhino.jstype.FunctionParamBuilder", "addOptionalParams(com.google.javascript.rhino.jstype.JSType[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.FunctionParamBuilder", "com.google.javascript.rhino.jstype.FunctionParamBuilder", "addVarArgs(com.google.javascript.rhino.jstype.JSType):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "canPropertyBeDefined(com.google.javascript.rhino.jstype.JSType,java.lang.String):boolean",
            new int[]{255,-844,-172,-245,-217,306,-333,818,203,-334,89,681,975,680,47,670,-324,966,-892,-429,601,786,171,-996,715,-974,-746,-615,898,660,496,-387,380,-803,-189,-222,-672,798,-61,793,652,-631,-946,110,-727,241,510,442,-671,-157,873,773,-900,142,916,-172,-212,-416,-67,-330,-280,759,-589,-899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-840,-400,573,-10,493,-1000,400,1000,-761,315,9,-283,-89,1000,-898,304,-154,400,-20,-965,400,-434,-731,496,-400,-1000,549,744,-400,-205,400,400,-447,524,176,-348,-528,6,-1000,558,-1000,291,-26,-399,890,-864,339,885,883,400,1000,1000,400,765,480,-310,-516,400,390,276,-364,140,-759,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{627,582,895,564,-214,-244,533,988,-319,64,-804,128,583,647,-640,364,818,350,975,142,-73,-298,-266,97,56,391,722,650,549,414,133,-96,-998,-154,22,638,879,-49,966,-679,-799,-769,929,189,-203,-70,-365,-381,120,-513,324,350,754,-503,871,-633,362,-160,-29,-285,-913,-187,-695,916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorType(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-581,-76,24,957,-687,255,931,203,-845,164,-658,-104,-89,628,-741,-811,647,-481,-137,823,-835,-47,660,-343,-298,58,-741,14,288,424,-666,425,-487,-967,293,-75,-204,-441,-755,-827,-509,-413,-41,370,-361,532,673,172,571,800,867,-340,-881,92,393,745,749,-328,740,-620,604,864,-399,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{504,-1000,-17,-228,-229,-884,-344,-61,-127,-377,998,-864,822,-847,-1000,266,-498,-1000,-906,-429,52,-74,713,404,-1000,269,94,-1000,-1000,496,-584,1000,421,819,529,-943,203,-1000,-432,-11,429,1000,460,-999,903,1000,1000,42,-70,-522,-51,463,143,664,1000,651,-1000,457,-834,-214,-696,784,887,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createConstructorTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-266,601,359,44,-812,-976,-433,-310,-527,-705,662,-224,654,881,-199,-345,868,-305,-14,113,382,-251,739,-479,-290,-952,-592,-504,-464,288,132,-389,-891,-168,491,-152,-615,459,-582,802,-784,380,-911,939,-418,-836,205,-376,82,381,303,90,-334,997,576,17,-47,862,52,994,369,-3,906,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createDefaultObjectUnion(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-12,-715,-726,-812,893,422,-415,32,752,301,-643,-168,635,712,-445,230,-671,383,468,779,72,-441,-620,-74,497,-479,128,464,861,-140,778,-955,-992,-938,420,-239,-807,562,-847,-981,-120,789,757,999,957,-78,-750,-188,-596,105,546,157,394,-332,789,750,52,-450,-444,90,658,-441,-871,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createDefaultObjectUnion(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope):com.google.javascript.rhino.jstype.JSType",
            new int[]{487,885,856,504,1000,-363,602,863,-578,555,-327,-391,286,-536,-146,-617,897,-1000,294,-1000,-514,413,43,-1000,-1000,1000,-1000,748,896,-1000,-1000,710,552,400,-1000,-257,681,1000,377,-1000,580,1000,-111,-1000,-1000,772,400,-187,1000,269,-1000,181,7,-535,-288,1000,-370,-1000,-1000,-380,-777,953,459,-205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope):com.google.javascript.rhino.jstype.JSType",
            new int[]{-418,-738,-939,-669,701,982,1000,38,88,986,382,418,-693,813,-933,-191,46,-233,-219,266,1000,254,1000,-154,683,-90,-775,-290,-801,-1000,-1000,378,253,-778,-86,1000,-946,53,573,1000,-740,85,128,265,-311,869,626,-49,1000,-209,-704,590,174,1000,446,497,-961,-603,-696,-1000,-690,1000,419,761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope,boolean):com.google.javascript.rhino.jstype.JSType",
            new int[]{-634,835,702,189,-494,120,-769,819,-1000,1000,-180,-277,236,-332,312,606,-269,-716,757,81,-1000,179,-709,237,111,430,1000,637,-500,-334,454,-1000,1000,1000,-973,-779,-262,1000,-1000,-291,-213,-300,87,-310,-789,-330,-445,-1000,258,1000,623,-1000,-1000,-157,423,-889,962,-457,-283,522,-290,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFromTypeNodes(com.google.javascript.rhino.Node,java.lang.String,com.google.javascript.rhino.jstype.StaticScope,boolean):com.google.javascript.rhino.jstype.JSType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{219,448,-106,-490,-366,-385,17,442,-782,734,350,-169,-569,-900,456,673,-97,-341,-771,74,-1000,-30,277,714,298,536,658,1000,332,61,516,-435,-83,-245,501,86,44,829,64,165,1000,-299,199,-101,-521,75,-299,-83,-141,186,-526,507,-236,-409,568,402,739,599,108,42,-169,-284,-51,868}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,boolean,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-289,-64,419,747,872,-464,-381,-262,164,649,-611,210,-838,764,32,599,367,538,-300,-2,11,-700,-683,-704,-53,-515,-507,-796,294,509,-212,106,935,77,-592,-303,-561,11,-814,-10,-454,284,-481,-613,-52,-850,-172,290,-217,-985,82,-57,996,994,-104,106,507,-6,-133,-476,-997,583,867,150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-56,-776,-596,387,-936,-932,-421,164,893,-970,520,-59,-822,214,393,-784,-842,-264,80,836,-477,-426,-14,-167,-783,702,337,900,526,36,-372,957,-675,244,-404,-679,-146,512,824,572,930,-5,944,364,-890,878,-702,151,4,710,-153,-892,-320,6,162,318,-127,962,397,-240,-394,607,290,573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{160,-220,528,-130,771,-465,267,-429,156,-485,1,-560,570,959,328,-548,653,-498,281,-119,-798,621,-551,-559,642,-606,-432,22,867,-239,253,-989,-261,310,668,769,-279,-506,693,527,889,-839,-608,701,862,-735,-547,-597,74,739,-326,-178,255,-370,-464,-963,986,-184,449,-159,-238,-126,-665,-830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionType(com.google.javascript.rhino.jstype.ObjectType,com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.JSType",
            new int[]{-1000,240,-916,580,-1000,-1000,-772,126,-365,424,184,304,639,783,-435,200,794,-664,-871,-1000,1000,1000,-58,1000,-644,-311,-1000,-1000,261,98,-1000,-384,511,679,248,-369,-323,79,761,-1000,503,496,599,425,-290,609,642,125,-599,208,66,959,-1000,-155,-851,-992,1000,-277,1000,-355,-295,-559,116,222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-844,392,349,328,-164,-870,-311,-235,-619,-937,-576,-338,828,-143,272,-34,-437,-32,786,-662,-119,463,-575,-908,644,-381,209,511,-49,-783,-698,764,-192,-96,-304,-932,962,-59,102,730,-791,-802,162,289,-172,348,-901,99,43,-119,-908,-627,884,758,-222,974,-788,335,566,-954,227,-489,718,-49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.FunctionType",
            new int[]{-658,1000,319,866,-1000,1000,-797,578,126,-417,-1000,145,953,-559,-653,501,0,1000,50,-14,0,-372,0,960,-177,0,-296,129,1000,-1000,0,-670,591,1000,0,-1000,1000,-649,-783,-515,-329,-512,-760,227,5,-726,-1000,-1000,843,239,-1000,0,-4,-129,0,0,-600,0,-1000,-1000,713,600,115,-428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.FunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createFunctionTypeWithVarArgs(com.google.javascript.rhino.jstype.ObjectType,com.google.javascript.rhino.jstype.JSType,java.util.List):com.google.javascript.rhino.jstype.JSType",
            new int[]{-1000,-76,-601,-1000,714,-721,645,114,-758,-979,-840,-865,-441,678,862,240,-1000,340,404,436,1000,-214,-763,242,-306,-592,-185,614,-707,768,493,-1000,-1000,-87,-841,342,688,-1000,1000,-1000,-320,-713,1000,-392,-492,-275,-254,53,-900,-616,239,-1000,440,-1000,739,-769,-928,-327,704,-128,1000,-853,397,-290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createNullableType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{466,318,809,555,-93,592,-456,611,-678,-326,344,869,-594,-80,948,-585,-751,-332,95,-42,-324,171,951,973,786,-814,267,-55,645,-1,-833,948,-145,-488,133,-230,710,688,656,671,5,-868,-713,445,-791,860,346,930,683,440,-534,372,-121,-427,-723,-889,-922,-62,-927,-62,85,649,81,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createOptionalNullableType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{479,876,514,-311,112,785,-403,456,699,-902,-668,-641,196,356,97,306,13,316,16,-865,-841,-394,-996,908,118,754,-202,-952,559,530,391,-787,-43,464,238,-5,-260,387,217,-184,-354,875,-581,705,-313,632,561,-961,268,-48,36,805,854,-285,448,417,-118,602,862,-257,-455,-377,923,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createOptionalParameters(com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.Node",
            new int[]{-1000,-1000,-46,1000,-1000,-9,1000,-514,1000,-1000,50,303,1000,53,-368,357,1000,599,1000,396,-256,-871,-1000,-885,474,870,-90,1000,179,-9,857,-1000,467,119,211,-1000,-782,-533,-424,1000,172,613,749,-411,595,-1000,893,389,-870,-2,111,-1000,250,389,461,-402,728,-491,-765,-538,1000,674,55,-176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createOptionalType(com.google.javascript.rhino.jstype.JSType):com.google.javascript.rhino.jstype.JSType",
            new int[]{-649,832,-741,-467,841,893,371,311,-204,194,413,71,300,-284,-460,263,-378,717,-417,-281,4,426,-155,-190,792,577,-589,-350,-572,502,147,306,851,362,-686,-158,-75,482,623,-534,662,-246,-541,-950,-797,-410,-4,-138,802,0,968,-518,378,-970,993,531,-406,31,-521,-850,879,990,-575,915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParameters(com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.Node",
            new int[]{800,-1000,-111,-1000,-232,-1000,1000,1000,-1000,390,-988,-12,-1000,1000,-1000,939,-1000,739,-839,184,1000,-291,473,417,661,-1000,735,1000,895,1000,800,528,1000,590,1000,-1000,1000,1000,816,-531,1000,1000,436,-1000,-1000,858,198,-1000,-1000,418,1000,-1000,-543,-1000,1000,232,-304,434,-391,-48,-832,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParameters(java.util.List):com.google.javascript.rhino.Node",
            new int[]{-1000,-310,-296,-1000,159,-1000,-807,-1000,-845,-332,-72,-424,-1000,-559,1000,431,-1000,801,-20,166,298,-580,-1000,139,-1000,-32,-1000,-1000,1000,-919,-753,515,1000,60,-1000,-1000,801,134,1000,-602,1000,15,62,-1000,-971,-1000,-158,-1000,1000,-12,-29,713,38,-1000,-1000,-806,613,-45,1000,1000,-438,-1000,584,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParametersWithVarArgs(com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.Node",
            new int[]{672,1000,-591,-400,557,523,-516,109,-806,-725,280,-220,606,537,1000,-388,1000,-470,-146,-23,409,213,-570,155,-1000,227,-376,-188,600,-330,263,332,1000,-974,731,57,991,815,190,959,-501,-780,658,35,1000,170,206,549,-1000,982,-297,25,-299,12,-895,-469,-324,-75,-740,-7,-383,-984,-215,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.Node", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createParametersWithVarArgs(java.util.List):com.google.javascript.rhino.Node",
            new int[]{1000,-1000,468,1000,-740,-285,-113,-841,1000,-1000,1000,1000,1000,-749,-1000,207,-595,-1000,-739,-1000,-779,-657,-542,-354,822,989,-453,-253,1000,-611,429,-1000,-959,-423,656,365,1000,556,262,-471,1000,386,676,-1000,845,-1000,449,1000,-1000,-923,-852,-238,1,-220,41,694,409,790,-389,-884,1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{1000,-184,17,1000,-365,47,1000,-105,-417,688,-583,-953,249,412,934,343,436,-887,-1000,-587,-1000,-971,-942,-1000,241,701,-144,895,-929,861,-52,685,-105,636,10,1000,57,-977,226,-1000,-520,-495,1000,-527,513,-657,700,-1000,-127,-170,-124,81,799,-638,355,475,-604,0,-1000,-185,906,-591,947,-578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.UnionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{1000,912,816,-125,398,-691,85,-445,684,315,644,-441,620,642,660,57,526,-1000,-351,175,1000,514,1000,-328,970,213,-1000,1000,-1000,-806,-81,572,-1000,-713,1000,625,-781,-275,198,-17,344,469,555,-587,469,635,-40,-1000,242,783,1000,-1000,867,-1000,-1000,1000,419,-454,-79,-1000,705,-875,92,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.ErrorFunctionType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSTypeNative[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{495,-789,-617,164,633,-2,-781,279,-615,491,99,669,-768,940,207,256,654,983,447,-848,-976,-459,-945,-887,-575,-837,-295,-341,453,242,-21,85,-253,900,-782,-244,-751,804,-381,320,-996,-313,-270,-215,694,-65,-537,-262,29,-768,162,-566,632,-26,326,-34,-885,918,-818,251,-339,-626,449,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.NoType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "createUnionType(com.google.javascript.rhino.jstype.JSType[]):com.google.javascript.rhino.jstype.JSType",
            new int[]{-612,782,873,770,-475,-60,729,686,4,-203,774,-218,-606,-857,-641,-927,-653,-586,-851,266,-141,175,34,241,178,-491,-507,-783,-149,469,-707,130,33,457,-85,-358,-972,958,-94,-97,316,393,462,681,-231,-980,-13,-183,-346,-189,-874,88,150,994,-199,500,558,582,182,-192,183,606,-826,-372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.NamedType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getForgivingType(com.google.javascript.rhino.jstype.StaticScope,java.lang.String,java.lang.String,int,int):com.google.javascript.rhino.jstype.JSType",
            new int[]{-287,-988,-156,39,89,-274,-474,-478,-378,-856,-608,-846,-845,1000,-285,0,-1000,-719,132,691,-172,982,1000,-810,0,-329,-131,73,-1000,-1000,756,479,-198,98,-1000,796,0,-1000,-1000,231,962,-243,-266,202,0,-394,0,-1000,487,-597,-261,0,444,-556,1000,0,-429,140,157,-43,71,-30,-661,275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.TemplateType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getForgivingType(com.google.javascript.rhino.jstype.StaticScope,java.lang.String,java.lang.String,int,int):com.google.javascript.rhino.jstype.JSType",
            new int[]{-937,577,-539,-47,1000,-1000,-769,993,1000,-1000,-238,-1000,-123,1000,582,1000,-3,-1000,902,-1000,221,238,688,1000,-1000,-1000,-760,197,781,562,-172,148,-488,277,-907,-326,-69,-503,-1000,511,1000,143,19,-410,-436,-607,-1000,-1000,-1000,661,242,293,548,-606,1000,225,10,-14,-335,-1000,-1000,411,1000,383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:com.google.javascript.rhino.jstype.NoType", DEReplay.run(
            "com.google.javascript.rhino.jstype.JSTypeRegistry", "com.google.javascript.rhino.jstype.JSTypeRegistry", "getGreatestSubtypeWithProperty(com.google.javascript.rhino.jstype.JSType,java.lang.String):com.google.javascript.rhino.jstype.JSType",
            new int[]{616,1000,-812,911,17,-514,454,-513,-210,1000,-120,901,-1000,738,-811,-723,71,833,-48,-1000,470,1000,1000,-698,302,1000,-173,-335,-1000,-764,1000,556,-199,-867,294,-380,-595,787,479,1000,-693,-1000,-177,64,-16,825,1000,43,-536,-1000,-800,-1000,-741,-308,231,-797,1000,-161,153,-800,1000,-897,732,853}));
    }
}
