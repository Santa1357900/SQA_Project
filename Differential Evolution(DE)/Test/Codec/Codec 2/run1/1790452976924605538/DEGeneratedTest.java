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
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:MTE4", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{166,564,-354,-426,987,352,291,-95,226,367,98,-840,-350,-866,1000,-338,427,704,96,477,-402,551,522,-691,-1000,796,-348,-497,454,-348,937,-69,745,-1000,-824,-263,-306,1000,1000,365,401,-919,1000,1000,326,-400,441,-671,-111,796,-773,796,393,1000,-997,510,-249,139,-476,-3,1000,131,-339,76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{186,627,914,-157,869,335,570,123,617,-794,198,-990,-835,-1000,1000,-188,678,471,201,-490,-1000,103,472,212,-997,404,-78,-263,-267,480,-356,-276,531,892,-365,-1000,47,1000,225,-121,544,-523,823,473,190,630,-111,-913,697,257,407,-1000,416,1000,572,837,660,-138,703,1000,1000,-61,-799,-260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{1000,-562,765,-699,1000,990,111,-59,766,-655,1000,-87,-467,-667,558,622,22,339,701,-177,-935,132,-943,-352,183,-982,583,1000,-873,1000,-1000,174,724,1000,1000,-266,-944,178,-1000,-177,-548,-928,80,1000,-279,139,-472,-87,-719,-619,234,-1000,-487,-393,851,1000,518,1000,-1000,1000,1000,-907,-1000,-586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{-816,354,-71,270,-21,-1000,-205,-179,-295,505,-298,-896,-70,-932,579,-170,478,631,-1000,-623,149,-103,776,-158,-906,902,-313,-959,130,-679,874,-543,632,-98,-417,-447,216,1000,1000,634,-240,-22,450,-265,958,243,699,-506,746,1000,-208,1000,537,935,-465,-243,79,-420,-129,81,-388,1000,896,455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{1000,-852,380,363,348,627,988,-641,-53,-260,-221,639,251,88,1000,352,424,1000,-451,-234,-81,-138,-307,-295,-171,-787,-1000,447,659,741,-822,-389,754,-204,-327,-194,-751,178,46,-991,1000,-337,349,-484,-619,-793,-489,125,-659,867,394,-1000,-94,-844,-616,262,-964,821,145,1000,1000,-78,-222,614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.DecoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{1000,-182,-29,-463,-544,234,1000,863,-1000,452,629,-662,1000,-239,-38,-748,86,-368,-627,203,-648,-428,1000,-499,20,380,-1000,1000,138,100,891,1000,381,722,-982,1000,-414,853,131,675,-208,429,-783,658,-258,-120,-170,682,794,898,445,-418,-774,-75,768,-378,-340,1000,403,-118,927,399,84,956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.DecoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.DecoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{-605,757,943,565,-555,2,543,-89,-536,533,-130,79,-301,323,0,865,-985,-64,-70,-242,831,-114,-647,-673,782,-138,424,-216,18,485,800,908,158,-695,-639,825,574,846,683,557,-91,-296,629,679,-501,-80,-806,-45,722,-848,151,-586,-781,547,-34,949,383,608,959,-764,551,-127,-249,-453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.DecoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{804,609,98,116,-157,-344,831,-459,492,-1000,988,-554,-739,216,674,-178,-1000,-294,-60,55,-241,-972,-497,-134,-286,-1000,-28,191,-179,-1000,-526,1000,164,-1000,369,-710,1000,-326,1000,-278,664,-892,1000,-321,-739,-1000,409,75,-650,-351,1000,-404,-1000,404,591,724,413,-162,1000,-841,1000,960,-1000,119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{-643,-79,588,447,-368,194,147,-151,-980,841,287,353,-427,-193,-555,101,100,32,-684,811,-822,277,-430,794,-679,969,-196,-987,-981,323,-791,-413,891,-631,-773,936,670,-792,-990,828,925,-200,-83,12,-508,901,-552,810,-570,385,-956,-910,976,-886,-10,374,468,773,-685,-543,490,-375,-996,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{-325,-543,-1000,-205,330,670,-279,614,-446,-195,-985,189,-1000,-1000,598,-545,-1000,-511,461,416,-341,-1000,-304,12,-994,685,-571,-273,1000,292,524,998,-431,1000,505,240,640,-487,181,100,501,649,-192,-463,1000,423,796,293,-348,-437,655,-441,-1000,1000,-97,253,-47,287,1000,1000,289,-196,-169,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:OTM=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{370,88,-49,-1000,-1000,866,118,-1000,-669,1000,116,-1000,-273,1000,-377,73,37,-238,864,-338,-1000,149,-36,-841,-107,-1000,506,-594,-1000,1000,-929,241,-135,-58,-911,-530,238,-649,-744,-1000,1000,1000,-487,-701,1000,1000,1000,1000,289,-301,405,-1000,395,249,521,140,991,332,-98,79,671,-126,-1000,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.math.BigInteger:ODk=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{1000,-1000,61,875,-2,-1000,-1000,340,-85,-14,-167,-648,155,300,-883,-1000,-165,-399,1000,-695,-1000,683,-1000,660,156,198,-708,55,375,-946,956,-516,497,1000,-841,-20,1000,128,22,-305,-471,-824,-370,-153,-669,304,-866,86,101,-211,144,-51,524,8,1000,-1000,537,243,-587,672,-263,476,-282,327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{77,-18,248,766,-960,-886,-23,748,-122,-87,-654,635,-36,-934,-486,-963,33,-277,-72,340,82,-557,-157,211,855,-407,437,-121,40,-223,354,974,847,-889,595,816,784,523,339,-891,123,612,-505,-806,803,101,576,250,-659,374,-180,-878,-326,243,-600,-177,-161,-335,-967,-820,-161,-122,332,572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{-651,-694,-702,387,-161,179,-787,-377,-591,806,18,507,456,489,-966,-572,-913,758,-608,-846,959,502,710,-611,635,929,-853,686,-980,153,617,-735,-962,437,-937,-682,824,488,343,-516,388,570,-507,-631,-15,-341,927,216,204,-606,-531,-879,-802,-857,-973,564,-602,154,-808,440,563,459,70,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{1000,68,377,-671,-497,837,197,-1000,333,-119,428,458,617,1000,0,-1000,-103,-1000,-360,642,-65,1000,-342,-1000,805,-337,1000,1000,44,510,374,671,-466,59,-1000,-352,1000,1000,551,-771,419,-1000,1000,-683,-313,214,-63,65,-1000,-255,799,-454,494,-742,-742,1000,878,-241,-1000,374,-165,1000,636,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{1000,-784,1000,-810,-475,523,-1000,425,-1000,438,561,-28,1000,965,-939,1000,-468,-571,1000,730,478,1000,-940,-972,1000,1000,1000,-343,-1000,-621,121,184,-846,-1000,-1000,-38,1000,1000,273,-915,-1000,-1000,1000,-1000,1000,1000,27,-204,-583,315,1000,-847,-290,496,-332,1000,939,-992,-380,938,-121,-605,-251,90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{872,437,627,-217,6,-740,145,937,-1,-972,-38,700,-624,377,946,504,-3,301,173,-10,-79,25,-73,278,430,-406,-442,87,966,-352,195,-91,-666,117,366,525,-376,911,-699,223,-992,-952,-168,298,817,158,678,596,-881,-219,159,217,-607,-533,371,818,831,353,265,-615,310,562,138,-152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njg=:19:java.lang.Byte:OTU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-513,-479,-110,191,201,-655,-1000,0,-704,1000,416,227,897,0,-956,913,-141,-311,1000,-489,0,209,-505,-192,-14,985,-95,-435,-747,0,490,-302,0,-942,0,-69,-17,-1000,0,1000,-311,1000,-11,548,996,878,-1000,-706,771,826,-523,628,121,652,531,-1000,-647,-344,-491,1000,1000,-1000,-816,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njk=:19:java.lang.Byte:NjU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{167,-11,919,231,-499,-1000,1000,924,464,532,-803,-910,234,-916,544,890,190,1000,1000,1000,561,301,616,801,-705,242,-663,-1000,-795,-672,1000,-827,1000,119,1000,327,-1000,-185,682,-516,-1000,754,-540,-778,234,900,278,1000,840,557,-668,348,-341,510,-185,-948,-1000,-443,-547,160,1000,-400,-329,513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTAy:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{276,873,147,-1000,394,-47,-375,545,-1000,333,1000,310,1000,254,-427,166,-273,-36,1000,-262,-140,1000,-11,-48,891,1000,153,242,-52,485,1000,-536,-680,-1000,-66,-1000,791,300,886,97,-667,-770,1000,-973,1000,1000,1000,487,-476,-218,268,-173,-43,-600,-89,400,400,541,-331,159,404,1000,181,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{1000,-607,79,-1000,1000,-681,-51,366,-1000,368,130,637,791,1000,997,939,-983,583,1000,-276,166,-257,-995,-361,410,1000,-531,-153,205,864,-514,-200,99,733,907,-166,-1000,-841,-1000,-1000,666,-1000,1000,-945,1000,1000,221,-868,-1000,1000,1000,-1000,-1000,7,169,-1000,-1000,-1000,-1000,625,1000,-1000,1000,799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{-731,-712,588,58,70,181,182,201,-667,795,845,-698,-708,574,805,274,567,-188,-165,-515,9,-827,864,347,-326,682,643,592,-771,-498,-512,-808,-246,455,-591,-539,300,224,57,739,-123,-582,804,225,573,303,-963,-945,436,-111,-751,486,-609,259,909,-803,30,587,-793,-859,-598,87,-200,-888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{79,427,-358,24,-511,-370,-29,-382,-59,-789,-477,313,46,-978,-362,892,-567,-198,954,-970,-234,117,90,447,-476,-374,-798,-732,230,363,-186,-39,-544,372,-380,-257,-651,999,-963,235,726,406,-768,312,-187,-519,-955,616,-381,206,801,-34,630,753,337,-946,-5,116,733,-766,730,-80,242,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{170,904,-89,-16,536,624,-702,-641,989,-404,930,-663,-377,-606,-171,295,-104,-200,-831,-522,-116,-766,-498,-757,-614,941,-609,-109,445,-491,715,316,481,916,-773,248,670,293,-989,-264,-535,848,443,705,241,994,951,-88,909,611,441,427,62,-397,584,-104,707,421,-653,263,980,-926,-885,-617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{-495,330,821,-966,453,-734,420,-609,-245,-220,-853,867,-895,946,484,-876,-333,666,320,-36,-598,873,846,356,-420,-488,-362,507,254,955,-235,223,-225,-481,640,-335,293,-182,-305,406,-342,853,-27,-332,94,-63,239,582,-372,-196,314,355,-644,-393,999,362,316,-123,-831,-662,9,-997,405,-917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{64,489,150,-506,-100,74,-713,341,399,501,-582,936,370,413,-452,-218,-282,-611,-58,-575,-766,889,-978,889,-886,976,-171,375,-546,688,898,-786,248,-55,-807,-133,331,951,179,-359,118,-787,84,-326,17,472,-86,-227,934,143,-92,-286,-563,170,763,722,-182,594,165,-958,558,-397,-12,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:ODA=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NzM=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{317,600,706,526,66,-1000,344,-1000,-113,-14,95,274,816,1000,-192,41,881,1000,1000,-629,-1000,101,-510,-648,388,-1000,-435,753,679,103,575,474,22,899,106,-618,752,-322,-1000,470,-82,-958,334,932,503,400,-1000,-354,735,574,-18,-147,-1000,-466,1000,703,1000,619,328,-1000,-432,-1000,-350,-320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{405,755,-141,644,-653,253,724,200,-12,-1000,577,-398,-928,-427,-550,732,143,-1000,737,-308,952,-941,-78,1000,1000,-55,605,529,630,-1000,-810,-685,-77,-486,1000,293,192,-578,322,-81,-259,-533,-160,487,-442,-719,695,-691,-80,793,55,-261,-716,-1000,947,-1000,723,482,-400,357,1000,-623,443,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Nzg=:19:java.lang.Byte:MTAz:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{-319,414,-354,29,-929,-278,-964,-15,511,-296,-445,-95,310,829,-19,674,-723,-540,-703,-938,521,-92,567,-981,259,-582,937,381,-12,-886,526,75,-392,-923,-755,374,875,-38,384,236,871,887,-813,-610,136,-346,-419,431,-455,811,715,-79,40,-463,-957,-756,-29,892,-698,-206,-117,567,923,978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{946,197,50,-377,-406,-135,-591,920,693,-192,446,-205,404,-679,-760,866,897,-830,641,295,945,-442,566,23,-164,652,-127,-813,-760,963,810,40,832,-23,-654,-831,-686,-278,458,319,790,-770,854,713,-642,216,549,396,265,-263,-805,-170,12,47,-856,765,352,-543,854,-807,339,575,-565,-870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{-57,-759,1000,376,858,-572,257,-1000,-384,-713,887,1000,1000,1000,1000,831,-220,264,531,703,-128,1000,1000,90,-55,586,1000,673,-279,-717,1000,980,-1000,-436,-326,-271,-488,265,762,338,547,-836,-1000,-1000,167,535,-664,1000,-514,1000,-1000,137,931,64,-608,672,-830,1000,1000,-1000,-1000,-914,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Nzk=:19:java.lang.Byte:ODk=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{293,242,913,-1000,558,389,457,-330,403,-260,322,-853,-952,459,-414,-87,180,-898,-1000,-399,-873,260,-894,805,231,941,-646,742,-824,1000,-267,-810,-436,331,1000,198,992,-963,396,284,-568,68,660,-601,-80,-332,573,117,548,619,-1000,540,471,-122,304,508,-520,998,-902,-302,229,-1000,234,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{41,-316,-896,-941,679,-416,939,64,491,446,600,-112,875,-264,-237,-905,544,986,890,-235,-642,850,420,-559,824,-368,260,268,602,-527,-606,658,-882,44,2,-581,-804,-308,66,492,543,741,-853,182,-145,927,-300,84,337,787,-828,-207,-496,-773,-107,-94,-706,477,792,-146,-310,-838,871,632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:OTU=:19:java.lang.Byte:NDU=:19:java.lang.Byte:MTAz:19:java.lang.Byte:NjU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{-218,-187,353,-247,-62,183,-320,-783,86,-890,178,-349,207,919,506,878,715,-683,-582,878,-581,680,234,805,48,-322,-684,-941,566,625,-217,-635,-722,517,-755,-520,560,887,967,309,-50,-670,-485,-14,262,-170,708,-227,438,-143,-535,514,910,-360,31,-170,-617,951,957,-785,-812,-690,221,792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{-74,911,-513,-775,1000,-557,-108,149,900,1000,-275,-23,-365,403,-644,607,-299,-1000,361,-154,1000,5,-679,287,456,-593,-657,-943,248,1000,-184,-478,-142,534,599,278,48,-1000,1000,-195,535,817,385,1000,1000,-423,458,-1000,1000,-535,-1000,1000,-1000,-1000,-810,-804,-35,268,627,-571,783,-401,-1,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NTY=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjY=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{675,-136,286,-521,283,-727,589,139,-74,313,879,8,740,-689,-274,-199,-467,768,-583,742,-990,704,-654,-606,-575,827,855,-191,-755,-989,-720,-851,584,908,72,-504,-271,-311,307,827,-119,-92,638,334,405,-770,-216,-844,355,415,47,-610,-219,-660,11,-204,457,-936,-433,11,27,-988,740,285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:OTg=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:MTA2:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{-583,364,95,111,-280,358,58,11,-416,559,-892,510,544,-4,157,-128,-146,-457,148,-559,132,-880,822,-285,637,466,607,305,477,390,-368,936,-770,-160,-65,138,13,-774,401,-378,-161,460,705,-564,291,939,573,-31,-29,319,67,-359,973,-674,-726,-362,731,-896,-242,-493,-53,-398,-541,-444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njk=:19:java.lang.Byte:NjY=:19:java.lang.Byte:NTQ=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{1000,-1000,-370,16,-683,-555,-875,488,-553,355,117,646,-308,671,1000,944,962,-818,-662,742,-990,-574,1000,-399,-483,-542,-475,1000,-51,-740,-871,67,-324,-481,1000,794,660,-83,-48,-1000,182,7,638,-517,405,1000,-216,-566,-295,-47,47,-454,569,-660,-808,743,-201,-47,-433,273,1000,-702,740,-569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:MTAx:19:java.lang.Byte:ODE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{-411,226,-792,311,-937,656,791,218,-805,543,389,850,-669,203,755,528,182,149,498,464,-617,211,593,-583,744,940,690,-88,1,-527,-695,775,-349,22,425,747,-846,-210,631,-395,669,242,-545,400,357,-602,-660,-838,-766,-905,334,744,-399,2,428,-637,-869,591,-675,-179,852,238,-664,394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njg=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{-547,-859,98,951,645,878,-154,-20,575,-964,-904,-104,765,-395,760,-749,418,-405,-51,-709,529,-326,-974,659,755,547,830,26,682,-726,-585,-126,-183,710,-874,-221,-602,929,-597,126,-48,-445,301,-603,-583,737,374,-75,-392,-291,355,313,-189,-219,792,-396,-616,977,-258,709,381,528,-534,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{-170,0,206,633,161,-790,309,429,724,300,623,-766,110,519,500,-92,-856,-280,79,-996,-816,835,-240,502,-504,-294,-376,-718,-223,380,-417,532,577,-958,532,341,-826,-688,-691,-629,-750,-153,-96,-322,-367,-156,2,507,316,-609,-277,210,976,-601,163,299,322,300,-737,88,-309,-363,-585,393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:MTAx:19:java.lang.Byte:ODE=:19:java.lang.Byte:NzI=:19:java.lang.Byte:NDc=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{-776,100,377,566,-903,-685,263,-323,-229,608,-719,-473,-191,11,-63,-41,-394,-349,901,-870,364,320,355,520,-791,246,-705,706,-246,407,-500,787,889,-314,929,247,-184,441,288,-793,416,654,178,-488,950,726,821,924,-252,-812,-322,-230,97,136,480,-123,-179,32,121,-664,-791,649,-294,-305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{-88,-653,-741,-963,-447,-937,589,-428,28,-98,-980,922,951,-222,-816,-251,727,-759,-807,-794,743,182,-910,920,437,-692,-692,455,-69,-202,684,-230,-682,558,647,-989,-26,713,152,218,-651,-370,618,966,540,-398,-285,804,-311,-434,-840,-546,-281,-229,869,-827,286,-896,-212,425,906,974,507,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NzY=:19:java.lang.Byte:MTAy:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{392,299,451,-482,1000,-777,-1000,429,606,396,252,558,484,-362,1000,-1000,191,176,-21,-210,-409,-1000,369,-330,-837,57,40,-195,650,-164,-246,-274,-1000,-28,-619,-416,-817,-1000,-196,68,-118,-1000,213,929,185,-1000,650,-1000,-241,1000,825,-473,-627,-1000,-1000,-57,-1000,127,-228,-130,630,-567,-443,-594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{447,801,-842,706,866,-553,421,-418,-371,753,658,-523,-834,276,329,-914,681,476,580,70,-531,-60,638,-34,-364,349,-218,-229,-559,973,-799,792,998,632,-502,-260,919,26,-698,-884,-383,-859,-442,-43,477,-949,400,-299,-165,824,-804,-450,847,500,-630,-925,685,119,-117,1000,-777,662,904,128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{3,727,-350,409,1000,-817,1000,88,1000,1000,-122,-184,-87,233,1000,1000,1000,1000,201,485,19,-1000,-894,-498,-395,-254,-1000,-235,-718,381,-1000,836,430,-189,-77,169,699,-519,-1000,-1000,-1000,-1000,1000,-614,578,-1000,-1000,-1000,160,1000,-743,-837,-904,-1000,-1000,-1000,390,-965,-6,-112,-768,1000,-130,268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{-917,118,-121,1000,-168,1000,275,316,700,-669,-1000,-28,-795,884,310,270,-1000,38,-840,224,555,-591,1000,-256,366,-622,-818,-1000,55,350,1000,729,-804,718,-792,-1000,561,-1000,-741,-86,490,-267,-775,-1000,840,-1000,-65,417,671,766,492,-998,1000,-579,-69,-47,-1000,512,38,-879,91,1000,-351,148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{766,346,-338,270,-549,50,298,164,-978,907,961,-639,640,780,-727,-93,270,-368,699,-925,6,786,-921,618,287,689,-463,-985,317,365,-390,714,373,816,724,-428,450,458,-189,689,650,-639,-259,-338,127,535,-471,928,997,790,796,-708,-383,815,-128,-978,-96,17,-927,933,-60,415,209,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{661,-909,614,454,726,693,-778,-339,-136,-933,-301,-321,-925,7,874,-860,-653,252,258,-233,-928,-826,-385,439,226,-354,106,-888,-24,381,-358,-820,-250,85,478,-923,-467,610,-370,-94,-818,-30,669,-277,875,553,678,725,-518,-849,708,-950,576,-448,-58,141,-882,-679,980,-275,-599,163,98,-700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{-767,258,1000,1000,-974,-522,120,-1000,-1000,-796,1000,-645,575,-1000,1000,1000,565,-682,1000,340,-1000,372,-88,1000,1000,1000,-105,-423,-1000,1000,-701,1000,-384,-1000,119,1000,107,1000,-1000,117,740,-1000,-1000,-1000,-81,-206,779,-1000,-53,739,519,-899,619,-1000,-580,1000,1000,859,-1000,-197,799,-197,-962,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{-618,-214,806,-934,-321,169,-457,-599,228,-817,683,79,850,-304,52,498,501,-895,-93,-592,-457,-602,872,-715,-652,749,147,-441,-672,760,-240,-231,-522,607,168,-340,79,655,573,305,-669,-590,28,462,396,-909,595,-492,-453,542,-393,578,977,-264,434,793,-840,-315,73,-151,623,-757,-686,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{-38,-738,-741,270,945,400,-112,-76,-35,488,-726,213,498,-149,-43,-769,954,649,-592,990,202,933,-441,783,-561,-140,-616,404,260,-826,570,-193,-188,264,849,769,445,378,682,168,-575,-84,603,-393,-43,-916,798,270,12,696,-762,-840,277,375,59,-859,912,22,-234,582,743,-948,-663,828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read():int",
            new int[]{513,455,486,-732,-830,-644,-682,713,131,713,-338,223,-186,-564,50,-616,694,68,-130,-809,671,648,329,724,813,-79,-144,-368,-191,568,357,608,94,302,605,-238,285,-558,-990,-896,-718,-115,24,157,-777,284,533,-841,-699,-340,704,283,416,690,-5,-197,-417,487,-735,-722,-898,792,281,-549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-534,381,-985,-1000,903,712,-400,146,278,619,-93,-215,1000,654,781,-88,-383,5,1000,29,-1000,-250,-308,1000,531,241,1000,-748,987,-300,573,-229,-582,-1000,822,642,-313,159,-163,709,435,-131,-515,194,-785,-392,391,-957,-217,29,-1000,-343,225,-1000,350,-224,511,424,357,54,56,576,814,429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-34,-847,-533,-1000,1000,-902,235,-20,382,719,70,500,1000,-1000,85,92,-456,-359,-396,-810,-780,-728,-262,1000,373,272,-674,44,528,509,1000,-229,-181,-954,798,710,-164,159,-478,-1000,-565,-131,-349,933,-118,-1000,1000,-142,325,1000,-1000,-1000,1000,-230,191,590,-469,1000,1000,407,283,1000,-42,507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{75,755,354,1000,471,208,-845,-380,103,2,766,-258,-1000,49,253,743,-895,738,994,306,398,205,-189,1000,-65,864,-515,-150,346,-1000,-402,-1000,-398,514,-356,551,616,-1000,-549,-788,553,1000,-1000,-1000,-432,1000,-705,-1000,-1000,-1000,1000,751,-603,-230,-765,-553,-503,-1000,-682,-370,475,-777,887,500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-290,-1000,320,-1000,315,-1000,-44,-581,5,852,1000,-307,-1000,-1000,-907,1000,499,-272,-572,-786,1000,-1000,255,-587,-435,505,-1000,645,154,177,-288,-313,93,1000,626,-1000,187,-701,693,-656,-1000,491,-481,1000,500,102,-208,134,683,217,540,499,1000,608,598,893,-746,-160,463,-355,453,-162,-267,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-233,-595,606,-1000,224,1000,-414,1000,-861,1000,990,-460,-662,-1000,152,-1000,329,-858,-876,622,1000,-1000,284,644,-1000,-156,-287,124,-585,-520,-307,893,22,122,899,-1000,532,-722,-783,-887,-865,-1000,661,-540,299,1000,-101,408,-465,-1000,1000,1000,1000,724,1000,980,887,70,216,769,751,-1000,1000,88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-1000,-1000,-462,-1000,742,-1000,-830,-425,591,77,1000,-385,-224,-1000,-1000,391,1000,-1000,-1000,82,370,-1000,134,-1000,362,1000,-931,1000,163,-148,-310,478,470,1000,219,-564,-851,-789,1000,-1000,-1000,380,546,1000,1000,8,-202,709,1000,-158,-1000,941,452,-831,632,505,120,243,-832,-1000,-546,225,590,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{107,149,263,200,214,-99,-817,-921,172,894,1000,200,-1000,-945,-816,436,-466,431,460,-878,1000,-1000,-88,-41,-544,1000,-1000,-702,430,-989,-227,-328,-185,1000,-220,-849,203,-1000,1000,-1000,-475,906,-1000,1000,-211,1000,-1000,-1000,-117,-1000,1000,1000,-181,-146,170,-359,493,-585,-952,-1000,734,-1000,-445,487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-496,-523,467,868,681,-99,-817,-570,-712,77,-247,234,-58,-846,-1000,546,-119,-864,-1000,914,-54,139,-1000,-41,273,846,-245,267,213,-989,976,-578,520,1000,-220,1000,57,-56,1000,-1000,208,127,488,1000,999,-1000,-801,-1000,-167,-109,126,-360,452,-146,704,-359,959,1000,-25,-811,-235,-400,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{-139,-796,435,-477,-831,466,929,-982,120,357,176,911,19,-774,-510,707,805,276,710,725,-130,-660,-470,-201,378,484,-752,154,-360,99,-466,-14,469,-172,-55,15,816,873,88,-500,-555,-983,-437,23,-79,-952,-881,-253,-222,-839,-179,763,343,422,-553,-333,-543,-645,812,561,20,-625,-714,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{303,-958,-1000,439,390,1000,-708,1000,485,-725,-159,-98,-932,1000,-185,-1000,253,886,512,1000,667,-519,-51,-311,-198,-1000,-935,507,-383,1000,326,427,-568,-656,1000,-664,1000,1000,-91,-197,935,-384,-775,893,-925,958,-168,-969,-312,-1000,-408,-58,633,696,-668,1000,-298,31,-219,342,-17,495,-193,-569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "flush():void",
            new int[]{315,-880,-221,-1000,1000,-223,-149,-837,-1000,-144,-1000,-1000,-643,-25,1000,-1000,-302,-1000,-1000,-1000,1000,-1000,-305,481,1000,58,-664,466,885,-722,242,-9,162,-232,1000,1000,1000,1000,-554,1000,1000,-979,1000,505,1000,-1000,-165,455,-1000,-1000,-1000,38,-1000,-124,1000,-1000,-1000,-915,1000,-118,-832,1000,1000,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "flush():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{687,-1000,245,679,1000,-589,696,-368,-1000,825,36,-251,-486,498,-1000,603,1000,-488,-171,172,573,-635,122,-384,957,-569,1000,487,-52,-491,509,-259,-537,-589,-1000,-1000,-1000,410,-229,-1000,-865,-510,656,-61,704,-267,-1000,-730,-361,-1000,362,1000,714,-306,348,-1000,-309,103,402,571,555,1000,-566,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{1000,1000,26,1000,393,-963,817,-1000,-252,686,-637,-1000,516,-714,949,641,1000,-716,-593,1000,570,-263,879,-1000,1000,-650,461,-92,431,983,-1000,1000,-1000,1000,699,1000,-1000,-1000,-284,1000,47,-1000,1000,1000,-21,116,-1000,685,1000,-1000,-1000,1000,-1000,-543,625,1000,-896,-1000,-1000,795,197,-488,95,-2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{714,909,154,-93,-546,908,-537,767,37,909,413,620,-857,85,-479,-61,-662,399,718,961,-802,-884,574,-540,-123,-551,-269,332,-166,603,988,-377,714,519,-425,682,687,-670,-754,854,969,718,981,-624,681,176,-640,922,733,-490,931,672,995,-179,-721,-335,659,692,927,-254,-598,-676,673,-565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{126,-1000,1000,-429,-382,-1000,-816,-1000,-725,1000,-413,-1000,341,-559,1000,963,1000,-1000,-594,-168,-1000,-139,-350,-1000,1000,-439,661,1000,413,1000,-1000,1000,-87,-1000,1000,-351,396,1000,-579,393,-1000,-1000,1000,1000,-346,-199,1000,1000,-517,1000,28,-280,-1000,1000,-924,-157,-47,-840,867,795,1000,135,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{758,-1000,1000,-817,-51,-208,-817,-1000,-1000,1000,-536,-1000,211,-811,677,936,1000,-581,-431,445,-156,-524,711,-1000,1000,-1000,486,1000,357,967,-867,576,-519,-1000,440,-784,-87,813,-453,18,-1000,-838,994,1000,-77,-749,809,510,-399,440,-396,-145,-706,400,-716,-65,-338,-743,79,192,978,254,-909,955}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-966,-913,-174,-667,-874,-410,-635,-203,-617,72,907,-708,230,159,-843,-744,765,-429,960,988,-379,201,-136,442,144,631,981,-254,-174,-743,357,480,194,-645,-950,-71,-300,-992,-697,-856,531,-896,316,973,733,-656,376,961,-460,-426,416,474,401,-338,-592,-736,815,-412,449,261,820,480,-146,396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-377,500,880,331,-709,-349,-393,-203,-206,-393,-886,-228,722,612,-650,249,-705,-135,878,304,-796,-397,932,128,-611,-759,229,354,-380,930,145,-582,965,-10,882,773,615,326,-604,487,-116,167,-986,17,-320,-760,899,737,-749,840,827,197,-399,-824,-879,-970,595,-181,966,-86,507,-854,856,63}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{895,-1000,-1000,1000,829,-457,653,111,-879,-61,1000,253,621,333,-845,45,944,-1000,730,1000,-615,-992,59,-335,1000,-834,1000,-322,630,-66,-92,1000,-620,606,-1000,-1000,57,-371,-146,-1000,1000,-1000,1000,937,1000,-288,-871,1000,1000,-1000,544,1000,-827,-597,1000,-484,-142,-86,211,-106,923,1000,79,274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{1000,-931,1000,731,104,-1000,-1000,-1000,164,1000,-929,-1000,265,-1000,755,227,1000,-594,-1000,-921,-1000,189,1000,-1000,1000,-1000,230,624,-311,1000,-1000,1000,-1000,-985,1000,-568,114,-587,-1000,1000,-732,-1000,1000,1000,-714,-1000,1000,1000,967,1000,-316,-1000,-1000,810,-692,1000,-432,-1000,-1000,1000,1000,-1000,-812,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{-598,335,-299,-389,-540,-760,377,-405,806,78,0,84,-771,-433,641,246,941,445,286,321,-575,-759,187,639,164,-547,590,-73,-882,-870,24,-77,-834,681,319,-970,829,-108,-699,793,-433,576,-274,-619,-323,-895,432,-91,417,-911,-99,443,-656,939,-513,834,-940,-291,-604,-852,584,-325,-806,-608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{-733,-803,605,1000,581,815,-373,1000,-531,-174,-499,622,-290,1000,-798,170,-163,-1000,-1000,496,-894,1000,-1000,-1000,-563,151,811,-836,1000,1000,-460,792,486,24,-732,-410,853,-22,250,57,1000,-1000,1000,530,841,21,92,-282,21,251,-913,399,872,-164,1000,35,1000,-688,-972,1000,-1000,1000,331,840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{714,-1000,809,-502,99,16,266,-133,-1000,1000,645,143,-511,-1000,-613,246,731,445,-333,-1000,725,1000,393,-346,313,-547,1000,247,-110,-475,1000,-1000,-1000,613,-888,282,-1000,-1000,-113,793,-1000,-969,344,293,-49,1000,1000,990,779,1000,-211,274,-1000,1000,-85,-798,-1000,-291,-510,-1000,794,-1000,1000,-513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{-410,-1000,-2,1000,29,1000,-542,733,-302,190,-290,800,121,-580,-260,-154,-1000,-470,-163,-869,-47,140,421,-97,17,969,-685,-411,834,1000,-569,-194,1000,-872,160,596,299,970,1000,-198,-385,63,79,-358,36,102,124,-1000,-53,-229,-1000,271,344,-1000,-327,187,1000,827,466,15,508,-45,-407,248}));
    }
}
