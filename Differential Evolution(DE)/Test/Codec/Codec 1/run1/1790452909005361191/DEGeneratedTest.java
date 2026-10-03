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
        org.junit.Assert.assertEquals("java.lang.String:MTExMTExMTExMQ==", DEReplay.run(
            "org.apache.commons.codec.language.Caverphone", "org.apache.commons.codec.language.Caverphone", "caverphone(java.lang.String):java.lang.String",
            new int[]{847,-115,346,-876,-803,-974,745,-425,63,717,867,962,442,706,892,995,-218,603,844,-248,111,-968,-692,496,922,441,-219,-845,641,294,968,259,-728,952,-87,-183,-351,727,451,-263,-389,-718,147,199,385,-120,623,-315,-548,775,969,-771,-720,497,-202,-70,438,727,440,731,804,-19,-269,-936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.String:MTExMTExMTExMQ==", DEReplay.run(
            "org.apache.commons.codec.language.Caverphone", "org.apache.commons.codec.language.Caverphone", "caverphone(java.lang.String):java.lang.String",
            new int[]{1000,-319,-75,445,368,-1000,-1000,1000,-1000,506,-144,294,455,214,-433,-14,-343,-473,-352,1000,-248,-377,-657,-456,-411,461,-1000,909,639,326,515,-262,66,715,-298,-1000,-343,1000,1000,15,1000,-767,-260,-8,-140,-283,-226,1000,-758,-271,945,-17,904,840,1000,161,36,-472,225,102,540,961,12,796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.String:MTExMTExMTExMQ==", DEReplay.run(
            "org.apache.commons.codec.language.Caverphone", "org.apache.commons.codec.language.Caverphone", "caverphone(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.String:QVBLVDExMTExMQ==", DEReplay.run(
            "org.apache.commons.codec.language.Caverphone", "org.apache.commons.codec.language.Caverphone", "encode(java.lang.Object):java.lang.Object",
            new int[]{0,24,627,-338,-779,890,-722,120,-448,999,452,-463,682,-532,-615,-931,942,-37,137,433,-371,945,857,-74,-697,935,273,251,221,-757,-468,752,404,897,-111,-786,722,-100,685,-948,887,-677,-431,405,-751,274,-43,406,899,286,-626,758,750,-674,505,-386,949,258,519,-277,-681,358,-428,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.language.Caverphone", "org.apache.commons.codec.language.Caverphone", "encode(java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.String:MTExMTExMTExMQ==", DEReplay.run(
            "org.apache.commons.codec.language.Caverphone", "org.apache.commons.codec.language.Caverphone", "encode(java.lang.String):java.lang.String",
            new int[]{226,-207,429,19,-16,704,426,-440,925,-319,-854,-508,-707,695,-322,402,-218,336,639,986,772,-237,-347,143,-223,-567,555,207,241,865,431,-503,936,858,389,-580,-795,-958,537,-279,-993,-590,-889,-833,318,581,-135,-54,-167,-72,923,363,85,-568,117,34,-862,137,518,714,-202,-574,-662,143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:MTExMTExMTExMQ==", DEReplay.run(
            "org.apache.commons.codec.language.Caverphone", "org.apache.commons.codec.language.Caverphone", "encode(java.lang.String):java.lang.String",
            new int[]{425,-207,335,97,450,967,1000,-524,-929,268,19,-601,-707,189,364,-120,457,-204,684,-90,1000,55,-601,408,-1000,-420,129,-764,-1000,-9,184,311,-1000,182,-19,-255,1000,-216,-628,470,1000,-796,201,1000,-279,-511,-135,104,577,-72,698,363,-423,191,451,-493,602,-450,555,-596,-202,-1000,-4,-406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.String:MTExMTExMTExMQ==", DEReplay.run(
            "org.apache.commons.codec.language.Caverphone", "org.apache.commons.codec.language.Caverphone", "encode(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.language.Caverphone", "org.apache.commons.codec.language.Caverphone", "isCaverphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-728,-686,-192,401,891,942,-861,580,570,-30,-380,440,150,91,417,93,-484,-170,-736,254,399,-436,-415,772,-12,202,-939,68,-886,-450,744,-231,-694,411,-635,87,-892,-506,597,825,705,329,-234,272,-863,548,694,-274,150,150,-762,894,-921,634,-591,972,84,-717,933,-102,505,264,-402,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.language.Caverphone", "org.apache.commons.codec.language.Caverphone", "isCaverphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-772,-302,-302,736,-90,516,-22,-369,88,-676,-758,848,813,-922,201,-411,-499,-798,-480,-105,-905,284,832,-867,39,-25,660,526,-48,541,949,917,462,-493,560,-787,-235,886,112,-693,-909,-416,329,-352,-480,-237,-749,101,357,-832,488,-763,-384,-274,-894,-202,-919,88,-374,701,881,208,153,663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.String:T0JKS1Q=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.Object):java.lang.Object",
            new int[]{-622,391,-556,-64,-814,-923,193,-592,408,451,423,-715,863,287,-829,757,-329,77,-525,556,-381,835,-155,-997,563,-667,579,-719,865,515,636,978,-206,6,905,-473,554,140,-443,77,-877,-267,876,-459,472,-64,492,91,859,-41,-776,-790,917,-830,911,-437,996,154,540,398,-127,678,-265,990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.Object):java.lang.Object",
            new int[]{-451,-878,767,-325,430,820,245,808,-112,-909,-143,179,34,169,-411,843,609,856,-571,-555,872,878,-192,783,-770,-461,484,596,-71,-799,928,-556,49,352,-647,-879,776,481,-635,-52,446,-203,989,500,-754,951,250,857,583,-651,880,291,130,-612,810,471,364,3,-927,-290,-26,288,-480,575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.String:T0JKSw==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.Object):java.lang.Object",
            new int[]{-17,618,270,247,-397,726,271,435,938,-541,488,-644,456,-429,-735,315,845,866,-749,-244,-657,200,868,321,-93,-550,794,-615,718,408,-841,853,-604,739,249,-452,78,-354,378,-543,992,-318,-700,-710,582,270,27,-647,334,930,273,542,-503,-46,825,-542,635,-692,151,-928,-429,949,263,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.String:U0tMRktSRg==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-530,-1000,24,1000,380,1000,-743,26,973,-1000,470,-273,-142,-558,27,63,-808,706,-977,-710,656,-1000,1000,-611,-885,1000,-1000,-95,938,929,300,-654,-41,-231,910,856,-1000,496,796,-1000,1000,886,721,-543,542,-424,181,-814,-532,294,313,691,926,887,805,-1000,-485,-858,452,-653,-297,-36,-1000,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.String:U0JTSw==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-573,582,953,-1000,-1000,97,149,973,599,-476,-850,428,-675,257,1000,-688,-1000,-284,438,-199,508,213,-1000,-1000,1000,-649,-775,-522,195,-1000,861,66,-1000,-1000,-379,-1000,-807,-462,99,457,-1000,-984,-1000,1000,-348,-810,-864,1000,1000,448,9,-1000,-1000,49,-504,611,1000,891,1000,1000,1000,-719,77,-390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.String:T0tQVA==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-120,-543,1000,-705,-492,80,610,-402,-374,1000,-375,-271,66,-159,1000,1000,-648,-1000,-345,-84,133,339,1000,870,717,1000,-1000,92,1000,-1000,905,-658,-49,489,-200,878,1000,566,-287,1000,-141,1000,-1000,1000,-1000,159,-1000,-171,664,-502,-248,-35,458,-65,-643,338,-1000,-997,-598,-169,-1000,532,-891,-440}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.String:WUs=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-573,907,953,-361,-1000,80,1000,846,-1000,1000,844,-909,-675,295,1000,407,671,-1000,-463,817,-359,1000,326,-818,1000,-603,-775,-618,-255,-1000,1000,671,-800,613,-379,-351,691,348,-994,1000,-1000,101,-1000,400,-348,-1000,-1000,243,1000,-930,414,-4,-330,-312,-254,-89,795,-997,625,1000,417,-168,521,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.String:U1BUSg==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-1000,-1000,-1000,-209,1000,-1000,-7,272,1000,-756,-894,702,1000,-1000,1000,-310,-1000,223,191,13,1000,-1000,-782,-793,274,582,44,586,-223,1000,1000,-811,-955,-126,1000,607,-1000,-667,616,-1000,603,-259,557,-36,441,-561,-966,1000,514,1000,-43,-1000,-628,344,-526,31,-137,992,-182,-82,438,-406,-1000,-123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.String:Sw==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{23,515,-518,1000,686,-1000,-708,289,145,-1000,328,-325,-1000,-37,-267,-1000,-79,1000,-1000,-262,-261,333,1000,-1000,-1000,-406,84,1000,-517,342,-313,140,143,-1000,1000,-100,-537,668,693,716,-321,-968,207,-1000,1000,-607,1000,-1000,-1000,-389,-19,1000,818,789,1000,-1000,679,-1000,-54,-14,699,-539,805,402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.String:RkJISw==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-504,567,-110,703,37,-1000,419,796,-202,-267,799,-552,-959,-73,-109,-812,570,-232,-712,121,79,708,326,-1000,-406,-794,-274,-367,-1000,-854,234,329,-171,87,821,-229,-252,116,-116,1000,-1000,-1000,-526,-1000,1000,-1000,45,-171,-340,-502,166,657,212,130,628,-938,903,-997,665,100,882,-535,412,-354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.String:TUI=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-307,1000,-1000,703,1000,399,-646,403,-202,-1000,799,-642,-337,168,20,-1000,570,-232,-712,-376,-10,-1000,56,-1000,-177,-1000,628,452,339,1000,420,568,-878,-1000,1000,287,-1000,1000,-116,1000,-1000,-1000,1000,-1000,1000,261,81,-323,-595,80,652,-20,-408,130,-175,-938,1000,-870,419,723,1000,-694,1000,174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{712,-824,-99,-8,-203,660,386,-589,709,-289,-227,80,605,145,335,165,507,688,-325,-978,957,-730,35,44,-374,-137,433,709,892,358,664,714,2,-570,-857,-844,254,-395,-140,691,868,821,-703,253,-336,-362,54,540,496,-29,148,-250,507,-278,-510,-716,705,12,-165,56,-920,-170,-276,-120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-17,-907,144,-592,-47,446,-731,-13,648,-172,-105,-348,-820,-751,183,-335,-269,989,-645,492,-730,-669,-81,130,-15,841,341,226,-345,377,-614,577,-264,-528,283,657,820,983,190,-238,289,990,-20,-906,-504,-207,-713,-377,-410,555,-27,945,252,671,-309,500,19,-772,47,-489,612,99,-953,-220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.String:TktQQg==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-1000,587,-610,-611,480,-1000,263,830,413,-493,-627,238,931,-13,1000,-1000,-912,-463,-162,-327,704,239,-727,-1000,1000,-524,769,-632,-82,627,1000,13,-973,-332,339,-475,-811,-263,65,476,-1000,-1000,31,437,-157,-810,-951,1000,1000,188,-85,-1000,-1000,19,-171,318,1000,599,1000,1000,1000,-424,456,-246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.String:UFM=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-28,537,-1000,-417,24,189,-520,-162,1000,-301,379,-1000,-1000,-1000,780,-261,80,819,329,-256,1000,-743,-43,-1000,1000,-78,739,-783,-590,-1000,863,585,-1000,336,753,222,-1000,935,283,674,106,396,421,-208,-1000,-1000,-523,639,384,-13,-73,-675,212,119,84,471,923,-1000,74,100,63,-87,-159,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:RkpG", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-1000,222,368,396,-1000,1000,745,1000,-313,471,111,310,-402,37,1000,-493,-226,-298,1000,185,-381,-172,784,-1000,1000,-600,832,959,-141,-276,1000,735,-942,-878,-278,-1000,-276,0,-781,155,-537,-1000,-438,1000,-447,-485,510,762,1000,-126,-646,-1000,-328,-338,206,-102,914,-1000,780,1000,500,-405,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:QQ==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{765,111,932,84,-605,-451,332,343,-338,-244,-290,-56,421,51,-508,-356,-68,-682,999,364,255,675,-707,785,-272,428,426,-780,-846,-836,-815,-562,889,-731,-555,-692,873,-557,-544,959,-150,819,-967,-30,-491,-778,477,552,78,-249,299,132,29,-569,319,-171,-15,956,561,-646,-341,-293,533,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-1000,407,13,-258,-194,-1000,799,1000,-1000,-487,453,-155,960,-249,224,-1000,-48,-1000,-1,-87,742,375,-925,-1000,219,-769,935,-261,-1000,44,464,-94,-419,-584,475,-681,-843,-536,4,1000,-1000,-1000,-362,318,-35,-1000,-296,697,1000,147,185,-388,-691,-93,331,-540,1000,-95,874,370,-63,-623,1000,-668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "getMaxCodeLen():int",
            new int[]{563,598,-862,-258,199,670,103,-830,-772,886,369,-736,-673,-729,509,-367,-133,-555,45,-866,-941,577,-636,-879,920,720,-628,-339,90,-329,534,-443,-201,-918,-452,303,820,-948,921,204,-882,-358,-76,-203,-912,852,-931,-534,-223,-516,66,647,-736,6,-495,-594,-458,-970,997,-678,612,-626,-663,-413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{1000,936,-987,-636,-387,-1000,141,51,-468,91,940,-158,-273,-665,366,-477,84,-1000,-651,-1000,-669,569,462,1000,219,1000,-647,-526,-858,-234,470,-738,1000,-367,13,1000,-349,-308,521,572,259,1000,-99,-1000,-1000,-468,443,1000,-296,-648,365,-275,-214,126,728,-653,-299,-519,495,617,1000,66,-699,423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{741,1000,-880,478,-226,-309,-910,718,-274,1000,1000,322,1000,1000,339,124,-1000,1000,-459,-897,775,473,-210,1000,-420,17,-510,-1000,-1000,547,-314,-13,334,1000,-327,981,349,735,-815,-567,159,1000,-1000,-1000,492,236,-1000,626,-156,-1000,878,1000,-81,-1000,-407,-1000,-1000,-459,-1000,230,17,-119,-425,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{480,789,-1000,-931,552,820,444,-53,-664,101,-49,363,-174,-308,76,1000,253,-1000,1000,-247,-1000,193,378,433,118,162,-240,465,-455,606,534,-729,112,-1000,78,-36,-70,-920,120,907,1000,88,1000,-891,-1000,-1000,1000,-208,-81,-617,555,-988,-1000,83,147,-1000,247,-291,663,1000,-189,-60,-1000,201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{575,456,695,-438,-750,871,13,454,-502,-302,-410,-808,-511,-840,271,528,415,-1000,-1000,-527,-348,-489,439,320,388,1000,-205,480,895,413,-19,-187,650,-1000,-313,-436,-733,-65,1000,654,-849,-245,1000,206,-1000,-893,1000,-447,387,-234,539,-951,-462,332,-912,1000,655,-800,1000,639,1000,-270,-236,-469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-883,961,1000,716,-1000,-716,984,-66,-468,-419,-603,-933,986,138,366,-769,-830,212,954,832,-1000,196,-1000,-702,645,-1000,-513,-395,1000,523,-404,183,1000,1000,1000,1000,1000,-1000,-1000,16,-1000,-207,472,-1000,-575,7,-1000,816,-400,-663,-391,-275,-672,126,1000,-811,607,861,-414,-570,-507,-1000,-460,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{480,741,-1000,-931,-102,793,-702,226,229,564,-213,581,513,-318,113,979,198,-1000,-1000,-200,-1000,-488,450,843,-57,495,-170,527,-790,-178,284,-887,370,-1000,78,-36,-854,-133,1000,696,-361,763,1000,-593,-1000,-1000,1000,-476,107,-856,905,-765,-1000,35,-436,1000,108,160,-75,1000,-189,725,-380,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{844,767,-1000,-403,710,1000,357,-114,-533,959,1000,540,-751,-398,293,-422,-320,-89,-1000,960,-350,-592,144,1000,123,534,-176,-485,1000,-940,-407,-276,5,-1000,-407,211,-875,466,1000,174,198,1000,509,-330,-800,-702,594,-38,240,-1000,805,-116,-887,22,55,486,-1000,-1000,788,-123,235,912,-614,-74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{329,789,-1000,861,-746,-1000,194,-296,-664,-725,1000,1000,-174,-517,435,-1000,768,488,1000,-430,678,-233,-496,433,882,372,-616,-1000,-1000,-1000,-547,-284,112,1000,447,624,-381,-292,120,-841,1000,1000,-1000,575,1000,562,-466,1000,258,262,-888,874,1000,-294,148,-1000,1000,286,407,278,-1000,463,433,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-738,1000,854,800,506,1000,-1000,964,1000,-33,-498,-1000,856,-643,-1000,-322,-541,1000,7,-237,-1000,535,-98,-626,-287,-1000,-1000,246,1000,1000,1000,-5,1000,347,328,1000,1000,-493,-1000,259,-991,-1000,-1000,-1000,-1000,335,217,428,658,-1000,460,201,-1000,-551,1000,-129,-420,290,-551,692,-737,-576,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-1000,-179,-361,1000,-880,-118,715,1000,855,84,511,1000,-293,-184,-947,-1000,-148,963,246,557,907,-1000,-897,-126,751,-401,579,-912,417,-1000,-1000,1000,341,82,1000,1000,-119,124,345,-642,-738,-35,-136,1000,1000,225,87,617,1000,79,-500,103,1000,-141,-1000,-881,802,-108,661,-1000,-1000,458,99,-49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{6,480,692,-636,-387,453,-104,528,-1000,91,940,75,0,283,481,-1000,189,1000,-651,-1000,-669,-820,-985,66,828,37,-328,-1000,146,-130,-1000,984,-330,1000,-224,-373,4,605,-580,-1000,55,703,-1000,1000,1000,-468,-1000,842,851,450,365,1000,1000,-875,-140,-1000,-15,-483,455,-471,-856,-528,1000,-19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-590,332,-889,1000,133,-909,-515,60,387,-537,-407,866,783,-379,-455,-1000,99,1000,1000,-227,-292,483,-872,-229,410,-1000,-1000,-1000,-926,-589,166,-156,357,1000,895,1000,832,-591,-1000,-1000,900,471,-1000,-270,1000,1000,-1000,1000,448,-275,-943,1000,623,-913,1000,-1000,247,1000,-679,315,-1000,249,-102,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-729,280,-1000,141,-807,635,885,-554,387,-425,311,1000,407,-938,-455,-1000,-79,-400,1000,502,400,723,-478,421,1000,-679,-1000,-1000,-1000,-1000,-825,-627,170,606,1000,1000,84,-946,-39,-481,1000,1000,-350,-176,1000,839,-826,1000,-78,320,-1000,-861,844,-189,750,-445,1000,1000,98,310,-605,1000,-102,549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{1000,653,-1000,-443,484,-191,35,-386,-185,267,-115,535,588,-422,-270,-252,-377,-225,-412,100,-1000,889,-49,732,33,-822,-1000,-499,-950,-506,104,-738,744,-352,920,940,598,-1000,-414,482,268,1000,-96,-1000,-971,152,209,805,-370,-1000,-362,-275,-214,-506,1000,758,-84,549,-329,759,-933,-124,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{32,454,-216,1000,-1000,308,105,-15,-687,-1000,198,575,-341,-639,368,-723,1000,488,896,-99,903,-974,-512,-44,1000,372,-307,-1000,-305,-954,-890,102,-133,1000,219,-382,-650,-122,456,-784,233,600,-1000,1000,1000,264,-76,779,736,551,-767,401,1000,-150,-1000,-1000,761,89,1000,293,-1000,227,758,-522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "isMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-1000,1000,1000,991,-816,-1000,-678,-690,1000,-1000,-939,-819,577,-728,-1000,-477,236,1000,1000,-158,-421,5,-493,-1000,1000,-1000,-1000,-246,1000,679,1000,-82,1000,851,1000,1000,1000,-1000,-1000,68,-1000,-1000,-877,-684,-220,688,-261,1000,583,-648,-714,-71,-547,-1000,627,-653,1000,1000,-90,569,-792,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.String:SU1KSw==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{1000,-1000,-943,-514,172,309,142,-271,-148,111,15,998,-882,-124,95,290,1000,1000,1000,172,277,360,145,-551,417,1000,261,-431,90,-552,1000,-761,-1000,-644,676,-612,163,-1000,-213,199,587,-1000,-1000,-122,-1000,-827,-105,79,380,-593,1000,-588,481,400,-195,-1000,474,275,-1000,233,-400,265,-401,402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.String:UEtLSw==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{-86,-1000,-1000,637,-103,371,895,1000,167,-1000,691,706,-485,-606,-162,-340,359,77,799,674,-338,888,-145,680,-467,-824,900,181,-1000,-1000,1000,-543,-929,845,-122,-693,-1000,-1000,703,-144,410,-519,-1000,-753,-1000,-214,717,-1000,-1000,1000,1000,25,-478,1000,1000,1000,15,569,-1000,1000,-1000,-1000,186,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.String:VEtTTg==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{408,-1000,-960,-257,35,233,294,110,-100,-223,1000,1000,-402,-1000,604,280,1000,994,853,94,-486,822,562,-1000,423,-218,383,-821,-1000,-687,1000,-548,-929,-96,-323,589,-186,-1000,-120,546,1000,-1000,-1000,-139,-1000,-633,-1000,-287,-34,-99,1000,-641,194,580,163,-400,580,667,-1000,463,-580,-1000,96,581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.String:S0hURg==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{-1000,808,-1000,63,-59,149,995,398,-19,-409,340,1000,-21,-229,1000,361,419,-1000,-85,-332,532,1000,810,954,15,-719,529,-1000,-849,-740,-859,435,-565,40,-663,-784,-1000,-1000,1000,-18,982,-1000,-526,-4,92,-11,1000,-1000,1000,-1000,920,-1000,-426,1000,490,1000,-364,1000,-37,143,-1000,-355,589,902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.String:RlJUUw==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{1000,671,-765,-914,-558,-837,-134,-953,-4,621,-697,170,83,244,265,103,616,1000,-574,-568,-600,-74,916,-1000,-175,-276,-859,-1000,1000,252,354,-128,807,-213,-343,-168,793,-997,-616,841,575,-965,1000,305,1000,-957,-20,1000,712,-881,-8,-992,484,-20,-1000,-1000,1000,-68,821,-1000,1000,290,829,-133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:SlBGSw==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{-1000,998,-1000,-3,-477,-127,766,871,167,112,985,1000,20,-1000,1000,694,1000,-528,989,-1000,1000,1000,1000,-184,-443,-1000,-571,-656,-190,-659,-1000,546,-913,-1000,-1000,-762,-458,-1000,1000,576,1000,-1000,604,722,47,-558,1000,-1000,1000,-1000,525,-484,393,1000,-339,-130,71,1000,-948,-456,-1000,-694,606,774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:TkZOSlRUVFI=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{-1000,1000,-713,-382,-772,-508,-1000,163,-1000,-643,1000,1000,544,-1000,1000,-571,-261,-48,-615,57,-110,-732,755,-1000,-697,-718,562,-1000,1000,-271,-939,-967,747,857,415,-784,-59,-1000,727,-202,802,-34,1000,239,92,248,866,1000,280,-1000,-86,-767,165,-1000,326,-1000,188,775,250,124,302,-1000,-364,622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.String:SUZSQg==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{805,-279,-195,-386,-218,-506,-956,-763,-569,1000,-553,-186,-131,1000,832,1000,875,1000,1000,-1000,-186,225,267,-1000,941,890,-980,-1000,427,481,1000,223,-203,-734,61,805,722,-1000,-979,1000,1000,-1000,286,752,612,23,-1000,650,1000,-1000,315,-659,116,-1000,-1000,-1000,698,547,-1000,-1000,1000,1000,-538,-894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{670,-392,478,50,-980,294,398,163,344,-152,-457,-974,-350,-587,576,721,-486,-259,40,297,-919,613,-367,35,191,881,792,-260,-397,801,-331,387,952,-784,402,-230,-434,405,-782,472,-465,974,342,408,-437,38,969,499,11,100,601,607,300,-435,-44,-248,-629,607,350,-125,242,226,-54,-99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{51,-152,516,-651,326,-842,-447,-550,-214,996,-808,557,529,891,197,237,-129,402,400,-274,200,-66,192,-56,-962,980,595,19,-359,-181,359,22,-834,-401,-638,935,883,20,234,545,-766,-666,548,-110,-403,316,205,-802,595,-28,704,807,-560,302,867,-520,422,199,-705,-255,940,720,-666,-608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.String:VFNGRlRU", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{-1000,539,67,819,-660,-794,-673,298,-433,271,-121,1000,587,1000,891,674,57,-282,-693,-1000,-1000,696,29,39,282,-1000,-873,-632,-518,476,-57,1000,1000,717,-1000,285,-201,-1000,-391,1000,1000,280,-1000,1000,109,1000,-561,-184,-114,418,818,-76,-1000,-1000,-149,1000,335,957,676,-761,1000,50,-9,-850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.String:SU5GTlQ=", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{-457,237,-2,1000,-551,-600,-372,884,-337,-423,30,507,-789,300,-645,-591,-939,-836,-515,57,-821,921,-1000,789,-271,369,-123,701,-1000,196,390,700,747,509,549,100,-679,253,-19,83,-949,1000,-858,270,-807,981,-683,-98,-678,385,899,553,-1000,-757,1000,1000,-163,336,166,1000,-448,-42,-619,119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.String:VA==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{1000,-471,412,-389,402,56,-958,-593,-1000,681,-615,170,-424,18,442,1000,1000,486,1000,-1000,436,48,-462,-909,208,1000,173,-656,-23,-723,1000,44,-1000,-1000,645,1000,435,18,-751,1000,-192,-1000,-100,503,-259,316,-1000,-416,1000,-1000,306,-680,559,146,405,-1000,149,507,-1000,294,-639,776,-1000,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.String:QQ==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{-749,-422,-611,71,-326,-1000,1000,-181,914,775,-889,-388,587,1000,538,652,-32,45,-156,-477,-73,357,34,655,973,-1000,-514,-1000,-764,980,-1000,20,28,478,-143,1000,1000,-1000,-834,1000,-153,-603,783,681,1000,-508,398,1000,52,-25,268,-405,-576,-1000,-945,-611,1000,71,528,-1000,1000,405,904,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "metaphone(java.lang.String):java.lang.String",
            new int[]{-973,776,446,-679,-659,-587,896,4,989,592,-377,-333,666,-64,-496,-549,-568,161,-112,226,725,53,-582,450,-907,286,-938,780,-284,-735,486,-683,-617,-864,-250,705,-866,341,897,-46,-983,657,508,-190,544,-480,520,788,337,619,669,-443,-828,-749,-149,279,193,-290,-671,696,402,125,281,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID|getMaxCodeLen=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.language.Metaphone", "org.apache.commons.codec.language.Metaphone", "setMaxCodeLen(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.codec.language.RefinedSoundex", "org.apache.commons.codec.language.RefinedSoundex", "difference(java.lang.String,java.lang.String):int",
            new int[]{77,1000,-1000,-132,-198,282,515,630,884,426,-853,-539,1000,-1000,-1000,-985,-294,-115,-17,27,458,-1000,-1000,1000,1000,-654,-1000,1000,-397,-143,-723,51,-635,-231,-626,798,-440,815,-563,977,-1000,-972,1000,-581,308,474,-93,-1000,-454,-644,-254,191,953,-329,1000,-797,-114,-126,-1000,1000,-1000,-321,278,-658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.language.RefinedSoundex", "org.apache.commons.codec.language.RefinedSoundex", "difference(java.lang.String,java.lang.String):int",
            new int[]{587,-394,-886,-82,35,1000,1000,944,-280,752,-1000,335,1000,-810,125,418,-1000,-684,-813,1000,667,78,-1000,-307,-393,-22,-111,-96,621,618,406,139,-851,-161,-506,789,496,-288,-380,257,-643,-684,-551,-654,-447,53,-246,-598,643,274,-164,780,651,-372,224,-835,550,-459,1000,912,-203,-915,-158,350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.language.RefinedSoundex", "org.apache.commons.codec.language.RefinedSoundex", "difference(java.lang.String,java.lang.String):int",
            new int[]{0,-442,510,-224,-922,197,-1000,-773,-630,-956,-622,579,-181,679,1000,476,660,731,-393,87,-243,-1000,1000,-1000,-1000,-182,1000,442,-452,-562,0,0,702,54,-807,-525,202,-1000,791,-1000,63,-565,-247,-794,656,1000,924,1000,441,0,-1000,163,522,653,-145,1000,-130,757,773,-468,438,1000,0,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.language.RefinedSoundex", "org.apache.commons.codec.language.RefinedSoundex", "encode(java.lang.Object):java.lang.Object",
            new int[]{272,-171,-592,-251,-256,-548,454,-154,900,380,-873,443,-198,-687,-50,955,-518,-272,-250,207,-84,-493,-409,857,-405,-306,-694,756,-527,-274,748,-109,963,394,433,-651,336,-7,-409,-137,426,-346,-194,-53,803,684,-585,-664,-223,-106,-377,555,-503,353,-120,-246,-813,785,-488,-327,-861,913,791,570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.String:WDU=", DEReplay.run(
            "org.apache.commons.codec.language.RefinedSoundex", "org.apache.commons.codec.language.RefinedSoundex", "encode(java.lang.String):java.lang.String",
            new int[]{-735,-322,137,888,-922,-288,-731,29,775,-898,410,742,898,-689,-672,563,-490,-278,-833,249,133,-754,925,-888,113,-261,393,968,-346,218,739,870,-960,-797,304,-642,-458,-168,-886,-410,-80,875,-884,-560,-490,192,208,-593,441,-213,755,12,-287,-800,-509,-737,585,625,-564,-317,-13,943,-218,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.language.RefinedSoundex", "org.apache.commons.codec.language.RefinedSoundex", "encode(java.lang.String):java.lang.String",
            new int[]{-162,263,-89,-104,-347,427,246,-644,-708,138,373,-602,-645,356,-933,520,-515,69,128,-983,355,703,797,394,925,474,580,-335,-585,-72,-95,938,-195,222,447,-719,-426,-910,855,464,873,-192,-843,943,330,12,143,-852,327,-264,693,28,698,-344,175,-340,557,-776,806,-308,-780,68,185,429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.language.RefinedSoundex", "org.apache.commons.codec.language.RefinedSoundex", "encode(java.lang.String):java.lang.String",
            new int[]{-274,-868,-272,447,-710,864,725,881,-385,-407,-418,-836,802,925,657,-251,-153,-291,176,879,-907,19,-806,-334,-984,-393,193,652,871,944,-657,602,-888,249,-156,-154,399,265,677,150,-548,-22,-359,774,88,945,-431,171,327,-542,277,590,-351,374,-31,749,183,125,589,-311,-111,-849,-93,965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.language.RefinedSoundex", "org.apache.commons.codec.language.RefinedSoundex", "soundex(java.lang.String):java.lang.String",
            new int[]{627,-691,-660,-223,367,154,795,-234,555,805,623,316,-797,-209,-424,-592,576,493,975,-157,364,-978,-579,196,-17,185,-519,-300,-445,558,-294,-603,570,281,-286,-334,997,619,-260,-192,-267,546,-939,730,-375,669,1,-1000,-919,642,-157,-459,-756,897,-78,-970,-905,-988,-622,760,457,-717,655,104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.String:TjgwNw==", DEReplay.run(
            "org.apache.commons.codec.language.RefinedSoundex", "org.apache.commons.codec.language.RefinedSoundex", "soundex(java.lang.String):java.lang.String",
            new int[]{416,704,405,987,97,268,-508,86,997,503,351,-892,-275,-613,990,98,-603,-218,-257,271,582,-340,-897,602,283,323,70,153,275,-60,-803,-217,-355,334,851,679,106,363,928,672,766,-510,-765,720,-553,143,54,-874,323,-654,26,78,712,759,-554,-110,-829,-262,-736,-993,702,340,-321,239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.language.RefinedSoundex", "org.apache.commons.codec.language.RefinedSoundex", "soundex(java.lang.String):java.lang.String",
            new int[]{-806,401,800,495,382,-11,-847,-930,922,701,6,305,-748,-979,238,961,654,-655,340,885,-567,132,-500,-617,-228,-241,828,759,50,321,-674,584,881,259,-411,87,896,107,-117,-440,390,-137,266,-895,-651,-451,702,219,-910,365,745,741,-241,515,-683,500,-702,-740,-684,-375,-901,-959,-622,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.codec.language.Soundex", "org.apache.commons.codec.language.Soundex", "difference(java.lang.String,java.lang.String):int",
            new int[]{-243,874,-2,248,902,-271,496,408,253,-107,126,-445,-471,-70,476,-869,-228,-366,-880,527,388,-893,-697,-537,-293,-963,851,859,-390,618,-274,-482,-652,266,-856,-185,591,-176,-14,-711,946,-695,340,557,733,399,-549,-744,-405,929,102,906,-262,-750,-501,476,993,-826,110,-748,-282,429,574,126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.language.Soundex", "org.apache.commons.codec.language.Soundex", "difference(java.lang.String,java.lang.String):int",
            new int[]{103,656,-1000,-67,9,529,1000,544,118,-336,887,-1000,-1000,-1000,415,19,-303,-1000,1000,-493,-551,786,700,-378,-795,504,1000,-58,476,666,190,-1000,528,-190,800,1000,320,1000,-986,-1000,314,-576,-1000,1000,-93,1000,603,1000,315,-594,338,308,1000,-302,-387,1000,-871,-245,912,-1000,-220,76,-767,494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.language.Soundex", "org.apache.commons.codec.language.Soundex", "difference(java.lang.String,java.lang.String):int",
            new int[]{38,1000,-134,95,-1000,201,-240,798,377,-1000,632,-1000,-209,-1000,-18,913,1000,-16,780,-36,-511,1000,794,-253,-7,-106,-872,-1000,237,895,-220,-710,-535,-20,635,399,1000,860,157,1000,-35,228,1000,104,-539,79,1000,1000,951,880,63,-1000,608,-1000,-345,-218,-184,1000,-319,-456,1000,-203,152,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.codec.language.Soundex", "org.apache.commons.codec.language.Soundex", "encode(java.lang.Object):java.lang.Object",
            new int[]{381,47,-475,-523,372,-78,-675,-373,830,443,-525,293,7,702,-1,-291,239,-990,-712,876,80,-753,133,812,524,931,-87,298,866,-471,-278,779,-919,-704,495,78,-614,-293,-900,-324,117,425,-216,-564,-16,665,377,891,309,788,212,-575,970,-964,-155,-929,383,405,-638,985,-858,-169,296,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.codec.language.Soundex", "org.apache.commons.codec.language.Soundex", "encode(java.lang.String):java.lang.String",
            new int[]{325,-741,394,-686,-680,123,788,706,-788,-140,944,726,560,291,309,410,-8,-896,-139,15,-915,220,-549,493,-983,385,-849,-513,-28,-430,702,51,253,-106,-53,501,346,-341,-593,-91,-253,-127,-435,-367,-730,120,303,953,776,-978,1,421,-229,-289,-729,-162,186,668,-965,905,766,-874,705,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.codec.language.Soundex", "org.apache.commons.codec.language.Soundex", "encode(java.lang.String):java.lang.String",
            new int[]{-69,-507,-171,6,-789,863,-785,363,-144,-543,-155,283,588,106,807,464,571,-580,246,-455,428,895,771,64,627,-598,-706,-216,550,732,-836,143,571,-14,409,848,698,-463,-987,728,957,976,690,-194,571,52,412,278,-783,902,60,590,165,98,-988,-879,-74,-25,210,115,220,504,-671,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.language.Soundex", "org.apache.commons.codec.language.Soundex", "encode(java.lang.String):java.lang.String",
            new int[]{-503,-407,-999,-372,-947,-912,-218,835,852,1,486,492,571,-479,868,699,101,205,-779,147,754,443,401,-181,-226,740,31,461,395,662,210,-770,-357,399,879,-785,385,808,-765,-426,667,522,-974,-73,337,279,408,343,114,938,-871,-241,-170,843,-739,-996,379,179,444,-272,70,727,643,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.String:RzUxMg==", DEReplay.run(
            "org.apache.commons.codec.language.Soundex", "org.apache.commons.codec.language.Soundex", "soundex(java.lang.String):java.lang.String",
            new int[]{449,-584,637,541,-126,-941,-472,-567,440,30,838,-91,-678,610,-830,-156,887,-36,-739,105,766,848,833,511,814,-28,926,882,-535,862,-451,-311,612,461,255,593,-451,364,812,-786,731,611,530,931,-827,216,-2,-840,-866,293,10,638,420,516,-754,-453,109,-73,956,-31,-207,655,-705,137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.String:VDYwMA==", DEReplay.run(
            "org.apache.commons.codec.language.Soundex", "org.apache.commons.codec.language.Soundex", "soundex(java.lang.String):java.lang.String",
            new int[]{12,-960,-480,-677,-379,-936,620,28,949,105,709,748,722,572,957,-197,-62,485,209,312,950,484,78,-449,219,-19,357,-184,125,-126,947,-621,781,677,-766,912,-764,-459,450,-156,684,-322,905,501,312,991,201,-387,-684,-682,530,787,224,-333,998,-135,-798,14,539,-217,267,839,419,459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.language.Soundex", "org.apache.commons.codec.language.Soundex", "soundex(java.lang.String):java.lang.String",
            new int[]{-467,-37,267,205,-348,256,242,-137,197,135,979,-993,-541,417,-301,-83,-704,-836,332,878,154,-841,-665,-945,674,65,646,563,-449,-495,-737,141,939,721,-281,-375,-928,-901,264,-526,-958,-101,-6,-962,690,779,316,947,-580,-635,194,-960,-570,23,404,-437,-690,-747,-319,70,-730,-334,-902,-648}));
    }
}
