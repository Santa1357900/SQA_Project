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
        org.junit.Assert.assertEquals("java.lang.String:Sk5GUFRQU1RMSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-15,167,281,-553,250,95,110,-827,-479,466,-975,520,-822,86,-1000,321,602,-342,155,238,-585,160,810,-1000,-95,-232,-1000,-200,-135,116,-64,-663,-1000,82,1000,202,-38,840,-382,-1000,-1000,130,-1000,255,-212,-1000,-201,-298,-629,36,-702,-166,1000,398,-140,-409,359,-304,-201,1000,1000,265,-19,545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.String:RktTSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{407,-189,1000,126,-753,564,468,-149,137,1000,-710,15,246,-723,296,-44,734,728,-1000,-655,-240,-200,-60,-294,-446,97,-1000,362,-1000,-823,-519,43,439,-508,1000,1000,-822,334,361,-610,-536,787,298,-1000,416,-986,672,-448,90,777,170,568,1000,140,-628,-156,-182,-863,1000,391,410,563,-625,69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.String:U0tQTQ==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-1000,1000,-830,703,-1000,-1000,-730,-1000,-79,-721,217,700,-400,-161,-273,729,-617,-212,1000,-1000,-304,1000,-466,1000,1000,-1000,-394,191,622,-437,1000,81,-933,803,727,1000,252,121,-142,-9,-400,-256,448,64,-709,932,-492,158,-439,-703,-257,984,270,1000,1000,-1000,734,1000,403,249,76,454,831,718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.String:QUtLSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-861,739,-1000,-833,-1000,-1000,-729,-41,-786,-738,430,174,-1000,-118,-1000,-491,-516,-267,1000,-339,-1000,1000,460,1000,1000,-1000,-1000,371,1000,-1000,1000,-237,-1000,1000,285,1000,725,658,-649,-718,-420,-1000,1000,1000,-1000,-73,-781,177,-994,19,-168,247,436,452,940,-1000,824,1000,-453,-425,909,-111,711,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.String:VEZLUg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-1000,1000,-1000,-833,77,-1000,-729,-1000,-1000,-738,-326,1000,-1000,315,-1000,862,-402,-1000,1000,-3,-630,1000,438,-922,917,-1000,-683,-320,1000,29,1000,-49,-1000,1000,70,1000,714,1000,753,-1000,-1000,-600,-1000,1000,-1000,1000,-1000,126,-1000,-1000,-1000,125,1000,1000,1000,-1000,1000,1000,-591,362,1000,188,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.String:Tkw=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{158,541,-567,745,-520,-603,-591,1000,-832,-1000,76,527,-405,-719,-1000,932,285,-75,1000,90,-189,1000,-683,307,-280,172,905,-741,1000,-626,1000,-247,-327,442,-135,628,521,1000,-1000,-225,535,-1000,-669,-454,-543,1000,-124,-202,-1000,246,392,-781,988,763,208,-1000,28,1000,-1000,-345,227,-915,-376,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:S0tMUw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{1000,-325,1000,350,-625,428,470,1000,1000,-780,-73,-270,97,-421,-570,330,718,1000,-1000,21,1000,61,-136,-493,-1000,1000,1000,-214,1000,114,1000,-688,1000,-1000,-15,4,1000,636,428,-1000,827,806,-1000,-188,23,1000,1000,-628,-596,1000,932,-728,-31,-1000,-48,-1000,-523,-1000,-1000,-1000,703,367,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.String:S0tL", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-1000,-318,-400,-259,771,-597,-342,-739,-1000,1000,154,1000,953,-1000,819,534,-712,-1000,-546,-522,-1000,-21,-332,-1000,159,1000,1000,-1000,-1000,-222,-1000,-753,-69,1000,-1000,-1000,-512,790,-249,-681,-225,-120,-999,685,975,-1000,1000,-104,-26,-1000,-965,915,1000,-18,1000,-44,1000,-274,547,1000,12,681,328,-979}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:U1RLSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-517,-400,570,622,-922,-10,-58,28,2,496,421,439,1000,-1000,1000,-71,326,208,-1000,-871,63,-966,-1000,946,-254,230,-230,-260,-778,-1000,-400,-578,-97,522,418,-400,-465,-25,-811,433,1000,-116,449,-858,691,-468,351,-347,242,-87,748,985,-89,655,728,-331,734,-400,1000,-704,-616,365,23,-682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:S1JK", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{257,984,1000,-259,738,349,796,1000,1000,-258,-785,-163,621,382,-645,-281,516,714,-1000,146,1000,-1000,568,718,-1000,1000,1000,142,1000,-830,1000,985,204,-125,1000,1000,994,790,707,-1000,-225,-294,-810,371,-183,1000,1000,-940,-948,-35,479,166,-455,400,1000,-1000,-118,44,-1000,-446,1000,1000,-1000,-979}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.String:TUtLSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-310,-1000,1000,446,189,414,231,661,-885,1000,-33,627,1000,-884,819,-281,132,-400,-1000,181,-106,-842,-325,-1000,-874,758,-449,-964,-1000,-775,-1000,-991,194,599,-371,-1000,-312,790,-202,-369,1000,-400,-999,-317,1000,-1000,205,-596,-26,-120,435,128,488,-997,612,-44,1000,-1000,261,257,12,62,-154,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.String:TlNQRg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-964,592,-1000,-754,-582,-942,-730,-758,-1000,-817,-3,930,-579,877,-56,-421,-848,-212,1000,-44,-179,1000,649,-28,1000,-1000,-266,69,1000,-542,1000,267,-671,993,132,1000,709,1000,-142,-400,-968,-616,-711,797,-1000,1000,-1000,751,-859,-1000,-460,904,-256,1000,1000,-769,511,1000,31,516,680,1000,1000,718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.String:U0tT", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-912,-1000,1000,-259,582,436,881,317,-935,1000,-1000,663,1000,-521,873,-281,1000,1000,-1000,-107,1000,-1000,-363,-1000,-874,1000,-44,435,-1000,-1000,-1000,-302,1000,751,324,-1000,-733,790,-156,-507,48,1000,-810,-35,1000,-1000,1000,-981,-695,-472,1000,1000,455,-997,1000,85,311,-1000,1000,-410,395,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-864,997,352,475,-721,-974,-293,757,240,-894,-451,837,-309,-981,-618,902,274,518,233,-954,-887,870,-695,419,430,-422,964,4,782,-827,766,-384,616,367,312,270,382,-7,-791,-653,303,-853,93,155,-5,738,-223,-594,-334,248,-58,-93,327,944,449,-686,587,970,-618,-785,-12,832,204,-31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.String:QVNOUA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{-1000,1000,-830,-273,-1000,-1000,-730,-396,-607,-721,217,700,-8,-396,324,729,-48,-212,207,-1000,-304,1000,-591,1000,1000,-1000,-394,191,622,-947,1000,81,505,803,-510,1000,252,121,-142,-9,-221,-256,448,64,-709,932,-470,158,-439,-703,-257,984,270,1000,1000,-1000,734,1000,443,-631,76,454,831,718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.String:UEZTSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String):java.lang.String",
            new int[]{239,-520,630,557,-156,-589,594,1000,522,593,325,196,760,-978,1000,-87,398,1000,-942,-201,847,-632,478,-1000,-452,673,222,-1000,403,-128,-187,679,699,-479,-1000,-1000,507,-1000,893,-1000,853,871,-722,-250,1000,616,797,-468,-666,161,-415,757,-1000,-473,1000,-1000,-42,-922,152,-747,290,429,-410,-849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.String:S0xUUw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{1000,-133,-1000,366,-1000,-96,767,-384,873,-855,942,629,446,-820,706,-1000,-1000,742,-154,495,14,-772,1000,-278,524,134,-593,178,-481,870,788,-483,849,-892,404,-146,559,647,-627,502,619,-1000,862,-194,901,742,390,934,105,-1000,1000,747,-457,-511,735,-221,-1000,-667,-1000,567,-390,-13,677,955}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.String:U0tUTg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{130,-1000,-1000,351,-78,104,517,-17,1000,750,-622,-1000,719,-1000,-124,95,-755,678,-1000,799,813,1000,708,-874,1000,641,-30,1000,-420,462,992,-760,230,211,-1000,-698,-1000,-1000,326,170,87,-825,-287,280,-79,346,1000,-310,-261,-650,-597,-535,-709,289,634,681,215,521,-718,-96,-1000,-1000,441,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.String:S1BQSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-453,110,-1000,47,-1000,-215,-276,-122,-487,-506,844,-1000,-34,-91,-134,-793,24,-858,482,-155,503,1000,838,-680,864,-1000,288,-724,-642,-997,585,-541,420,667,1000,726,1000,509,-440,1000,-1000,-881,14,198,269,-627,459,-265,-534,-811,937,1000,234,52,626,335,-1000,319,-894,-958,632,545,438,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.String:S0ZGUw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{457,125,-1000,15,-1000,-226,146,-1000,793,-1000,850,-177,228,741,-532,-1000,-1000,880,268,-462,-253,-1000,697,-400,527,-883,1000,-223,45,-78,32,31,445,-521,1000,-527,1000,116,237,1000,-414,-1000,1000,-113,502,-1000,-1000,395,-1000,-860,1000,1000,-208,651,646,-673,-1000,1000,-1000,255,637,1000,523,828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.String:TU5GVA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-737,-1000,1000,-323,1000,-832,310,-23,-732,299,-1000,-1000,1000,623,706,-566,-920,940,-1000,115,-1000,1000,-1000,146,-111,-177,-1000,1000,586,-213,387,1000,-1000,-879,-308,-1000,-1000,-1000,1000,-418,-1000,285,205,-1000,-207,377,1000,-572,-1000,-889,-1000,-1000,-938,1000,-989,-1000,121,1000,-903,-169,-1000,-1000,848,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.String:Tkw=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{221,818,650,667,298,835,-903,-256,72,-458,-126,-262,224,-820,706,829,388,-862,831,557,565,-34,270,-438,-584,-975,-596,178,502,-42,-326,-492,-687,-421,-146,263,10,13,-627,-62,-783,949,457,189,901,742,477,-796,-594,-266,831,747,-948,674,919,136,348,-608,-651,-86,846,-13,-651,183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.String:QUtSUw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{1000,-1000,-1000,-1,247,-224,-303,227,1000,-13,-844,271,857,-451,892,-754,-1000,880,-1000,-42,775,-1000,888,272,1000,982,331,569,45,700,537,-9,806,-699,1000,-375,73,-199,-557,723,869,-899,698,254,918,750,-530,546,-47,176,-29,554,-451,158,-355,-425,-637,-466,-892,-889,446,-927,-133,260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.String:VEtK", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-645,1000,-1000,430,-1000,-51,56,323,-13,-380,557,-631,-336,545,-1000,-437,-263,-1000,247,331,1000,-1000,749,1000,1000,-197,-160,-665,-317,-198,481,-1000,1000,-471,703,-1000,950,226,-649,389,693,-1000,-557,1000,-859,776,-487,95,-449,-299,1000,914,1000,-1000,1000,708,-855,-398,-1000,-477,766,1000,-591,576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.String:QUpMUA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{890,-398,95,191,372,298,734,-666,516,-1000,-492,757,366,301,-296,51,-1000,1000,-338,705,-566,77,-142,-400,-455,-403,-434,653,489,1000,17,542,-142,-1000,-275,-493,-798,-588,632,47,757,-322,1000,-1000,109,-653,787,290,-257,-343,634,-123,-833,-516,-233,-1000,-454,-239,-731,982,-705,880,941,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:QVNLSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-218,-670,-1000,733,-831,192,35,-603,1000,-767,231,-1000,138,-81,-303,-414,-779,337,156,-22,323,77,1000,-400,1000,-403,1000,-102,-203,145,334,-344,171,-433,675,-493,380,-588,93,677,44,-1000,449,-47,227,-467,507,-61,-816,-698,930,379,-94,438,543,50,-454,885,-647,223,84,788,523,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:QUxUUA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{1000,-689,-1000,479,-831,222,544,-665,944,-797,959,1000,380,-1000,1000,-1000,-138,774,142,46,-281,-1000,1000,-494,373,-413,-462,1000,-372,1000,254,-558,895,-1000,376,16,726,132,798,1000,1000,-1000,1000,-416,1000,819,509,1000,256,-1000,722,85,-944,-315,629,-50,-217,-534,-850,1000,-641,-206,1000,968}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:Uw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-656,832,-1000,430,-758,130,152,406,338,-1000,-541,-953,202,-1000,-1000,400,-721,-52,-81,1000,857,-193,616,567,943,-249,613,735,-774,-1000,704,-599,-184,-27,700,-742,65,112,-1000,449,127,-1000,-196,635,-1000,395,-377,-418,-187,-275,900,26,865,-1000,1000,59,-76,-1000,-496,-589,65,697,-424,295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{454,981,-403,-787,-260,-925,990,-623,-972,161,-716,867,166,962,-813,987,-641,-917,670,-746,-786,-604,-143,-907,-246,710,290,-265,910,-166,-195,-81,772,-425,-75,552,-49,-565,-329,909,840,814,3,121,-763,383,717,714,839,-59,560,-207,458,576,-432,93,-557,-785,177,-436,765,764,563,429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.String:Sw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-1000,-1000,-1000,205,-831,160,-447,-865,-388,-1000,-212,142,843,-453,891,543,-1000,-1000,-421,684,127,-595,1000,-997,667,47,1000,488,-23,558,225,155,-177,-804,883,105,415,-46,52,924,348,-1000,1000,-1000,1000,-747,443,409,-494,-432,930,439,-1000,-955,-142,903,-556,697,-647,-1000,247,215,914,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:VFJLRg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-400,-847,-1000,430,-185,-53,669,323,-13,-231,-463,-1000,636,-19,-1000,-157,-482,611,-581,396,329,283,680,106,1000,-2,381,-665,-42,-490,489,72,-346,132,125,-479,950,-566,-398,-474,-225,-929,482,-980,-310,-648,624,-925,-799,-651,314,166,143,681,361,-531,-454,792,-921,-217,-134,247,361,25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.String:Rk1MRg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "doubleMetaphone(java.lang.String,boolean):java.lang.String",
            new int[]{-480,1000,-890,717,-530,973,-351,0,-295,-700,-127,1000,133,545,48,1000,926,138,1000,331,1000,-352,612,287,-527,-848,803,799,-55,-471,-705,-1000,62,-153,1000,-189,1000,-259,1000,-26,693,529,-557,51,-104,1000,-547,850,-449,653,1,914,-552,-324,-916,-892,1000,-398,811,-437,297,51,670,835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.String:QVBKS1Q=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.Object):java.lang.Object",
            new int[]{-1000,422,-18,-1000,557,163,811,1000,-733,718,-1000,1000,-315,-724,-826,261,482,1000,-695,-899,-1000,-1000,8,641,-291,-548,-539,876,1000,-515,-541,-1000,415,-203,-370,-24,-1000,-1000,-957,1000,-1000,506,650,-1000,-1000,850,-1000,964,-1000,279,-827,-706,-485,1000,565,-1000,-1000,-859,-927,1000,988,-488,682,-878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.String:QVBKSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.Object):java.lang.Object",
            new int[]{-64,375,-874,-91,47,-986,-508,-20,-1000,-474,-251,225,-879,280,-89,786,-921,620,-564,399,-375,-1000,-106,721,868,-355,1000,-579,495,656,212,-858,-1000,139,415,395,-535,429,1000,1000,372,-1000,-899,82,-1000,-224,-305,-69,-612,-30,722,-518,-68,-454,379,719,-400,601,212,128,54,210,546,-658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.Object):java.lang.Object",
            new int[]{671,516,-168,-684,99,-778,383,947,29,540,-976,302,741,225,963,824,551,-90,970,-24,618,231,332,-148,129,-834,-870,-284,-634,438,721,655,372,629,770,862,247,-952,803,991,472,-680,-147,482,-941,176,-482,-201,831,973,-519,102,691,-867,392,-935,-684,-175,906,-778,435,-971,-392,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.String:Sk5TRg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-448,-24,-820,511,56,839,809,516,-1000,-739,449,667,329,-325,-837,244,-317,-15,856,-529,-54,399,-810,1000,669,-1000,-1000,690,97,1000,-899,231,82,1000,633,-1000,-1000,1000,686,847,-848,1000,-684,-254,177,-1000,153,-40,-1000,-400,-87,375,-823,906,1000,-665,-139,-1000,97,-79,-961,1000,521,-538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.String:VE1QSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{850,-493,-1000,686,329,809,719,431,426,-513,-181,-520,335,1000,-1000,1000,-208,-1000,-296,-1000,523,1000,-1000,-996,727,486,-482,1000,565,-759,105,-481,774,616,1000,-1000,19,-1000,191,-371,425,-1000,-1000,264,-1000,47,133,-791,295,-1000,25,-40,358,1000,678,-1000,-771,-402,753,-1000,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.String:TFNQSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{135,654,1000,-98,405,231,140,425,-263,73,349,573,674,-472,-530,-1000,23,1000,-837,-147,96,403,814,1000,-212,-1000,-36,-250,-332,181,247,1000,1000,633,-1000,26,-1000,1000,235,-350,-811,1000,-261,-611,923,-186,-811,766,-323,302,-504,739,-1000,345,-127,1000,706,126,-11,445,1000,-293,-492,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:TlBGSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{600,-358,-1000,607,-1000,-170,970,378,-912,128,-453,-1000,117,1000,461,721,863,-530,400,-40,1000,193,-393,326,1000,115,-1000,-1000,812,400,-655,-1000,-794,1000,400,-311,44,-1000,1000,746,-400,-861,-777,-266,-921,-400,-365,-105,-709,-1000,-376,-334,-712,1000,412,-266,854,-76,85,-1000,-887,-1000,644,889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.String:UE1L", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{207,-225,-1000,-211,72,408,-315,690,-923,360,66,-378,417,-491,263,1000,655,-816,47,113,-252,758,499,-1000,-400,1000,-1000,-826,113,157,-783,-730,-4,518,-50,41,-496,-228,318,-475,-804,-483,168,359,919,-654,-571,400,-664,255,657,-383,435,607,-411,-1000,490,-476,80,485,-1000,-663,559,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.String:Uk5KTQ==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{792,-837,-1000,-419,-294,-125,423,1000,-825,-1000,-519,624,-1000,1000,-922,1000,-1000,-1000,-407,187,-1000,758,-321,-1000,709,1000,-1000,238,-1000,-228,640,-1000,1000,-791,1000,-69,-496,-1000,-290,-775,525,-176,-162,-1,1000,-654,1000,445,-668,-906,1000,1000,-456,605,500,245,1000,-1000,-211,-951,-1000,168,1000,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.String:TFNU", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-1000,801,1000,-529,348,1000,168,802,-618,140,1000,1000,383,-1000,-634,-1000,215,1000,-681,-147,-13,-240,635,1000,-212,-1000,-970,-144,55,390,-699,1000,578,657,-1000,26,-1000,1000,741,-377,-1000,1000,-294,-611,1000,-269,-1000,766,-1000,302,-744,1000,-1000,345,-148,-84,423,73,-118,1000,-705,-111,-492,903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.String:QUZUUw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-164,256,-820,79,-817,277,537,912,71,-582,-283,697,-610,419,-840,673,-652,-595,-145,696,44,397,-393,-325,664,-645,-1000,1000,-118,488,19,1000,775,308,633,-395,-367,930,-185,196,-1000,20,-505,-385,304,-716,358,-325,-460,-320,37,158,-401,361,434,-107,-171,-815,-416,-546,-802,986,672,338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.String:VA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{1000,-233,-557,-591,-674,1000,1000,1000,-767,-1000,1000,769,367,-1000,-355,124,-316,184,1000,-1000,-373,1000,-238,747,1000,-721,-172,1000,611,1000,-447,1000,22,1000,811,-1000,-383,-295,-2,413,1000,1000,-1000,325,-349,-536,-518,-1000,-976,-1000,-399,1000,-549,116,1000,-419,-1000,1000,239,245,-455,446,500,344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.String:S0tTUA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-519,843,580,-723,-1000,911,157,797,-251,-33,882,735,46,-1000,853,-1000,-172,1000,-181,-264,49,694,46,581,302,-1000,-846,784,77,631,-395,307,948,525,-364,131,-1000,1000,267,17,-2,580,-285,-813,1000,-178,-593,617,-471,313,-376,1000,-469,-619,291,367,-982,-448,-669,1000,-733,37,-269,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.String:TEpNSw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-640,-358,580,607,-172,1000,970,1000,189,16,-453,-165,-806,-1000,-1000,-1000,-498,1000,-544,-685,-1000,1000,-393,569,344,-1000,-689,784,-599,-87,-11,1000,1000,248,-364,400,-1000,1000,-325,175,-865,800,-91,-1000,1000,-1,-398,-280,-409,313,-376,1000,-504,-494,7,367,-841,-448,-515,1000,198,1000,-365,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.String:Sw==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{663,-392,-1000,607,-295,330,952,180,271,-389,12,-391,1000,-942,-798,1000,855,-1000,433,-1000,686,-755,335,-1000,980,826,-334,-628,1000,-702,-11,1000,-983,1000,820,-1000,-1000,-1000,251,1000,-1000,-1000,-1000,876,911,-146,841,-1000,33,-752,460,1000,203,1000,970,-1000,-159,924,1000,1000,-981,-74,765,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.String:QUpMSg==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-397,640,-820,910,897,1000,719,663,400,-356,-137,402,1000,400,-1000,291,-828,184,-400,-12,824,1000,-1000,-400,-245,160,-883,1000,-217,1000,400,13,774,223,706,-353,-954,1000,846,-1000,-146,-400,-482,-852,343,-174,133,62,171,-1000,112,318,-306,1000,223,-201,-771,-161,-297,-122,-864,1000,606,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.String:TktMUA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{-938,-24,-820,511,-469,528,672,449,-1000,-1000,762,1000,757,-1000,780,-80,-782,-15,1000,-356,679,-1000,761,1000,247,-1000,-1000,-1000,417,1000,-1000,-831,193,1000,949,-1000,-1000,452,1000,-432,-1000,1000,-962,-223,356,-1000,-119,-40,-1000,-400,81,287,-1000,906,1000,-1000,741,-580,352,-79,-972,-482,668,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.String:UFRUUA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{1000,-523,-1000,-658,-1000,1000,719,889,675,-513,739,813,-342,-20,-1000,1000,-367,-275,-78,-1000,523,1000,-175,-190,727,-350,-1000,-111,1000,-218,37,-1000,-227,827,1000,-1000,404,-1000,191,1000,40,-117,-1000,226,-1000,47,1000,-1000,-407,-1000,-1000,905,-716,614,678,-1000,-1000,1000,258,-673,-1000,1000,878,484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:RlM=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "encode(java.lang.String):java.lang.String",
            new int[]{1000,-1000,-1000,350,-1000,-760,110,31,107,3,-1000,-1000,1000,78,-891,-1000,-178,-1000,-1000,-440,209,-945,1000,-1000,86,1000,992,-826,642,-1000,82,-1000,-1000,608,804,-678,-1000,-1000,-292,-321,-615,-1000,-226,1000,842,-101,784,-732,736,-402,1000,-1000,-1000,1000,-19,-899,1000,94,1000,-133,1000,-482,1000,-484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "getMaxCodeLen():int",
            new int[]{-314,508,-746,-215,724,621,-535,-170,965,370,-421,217,-173,746,-66,448,856,336,-536,763,-929,744,403,-269,-861,360,159,428,-36,715,682,-379,716,496,-838,489,-729,260,841,-354,588,-176,-689,-529,-379,409,-209,-898,-951,670,576,-161,303,-23,-922,-864,-832,-500,-272,-83,578,-28,-648,497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{1000,390,1000,-435,23,-1000,987,-693,-671,1000,-26,-552,-195,-21,281,1000,-4,188,-474,-1000,1000,-300,788,453,1000,719,-688,415,-11,-759,-861,-308,1000,-170,-378,915,48,-559,1000,1000,-680,-580,1000,-555,644,-978,-1000,-523,-950,-596,1000,-543,487,-1000,1000,-1000,1000,810,-1000,-168,244,-565,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{682,-1000,334,813,329,-454,380,343,455,-604,1000,268,223,1000,-746,-755,-670,-348,196,-376,-82,-439,-856,-151,178,-273,-501,-1000,-344,127,-240,107,233,-799,-49,489,1000,-145,-60,397,-74,-571,-787,-1000,25,-822,-304,-160,-138,255,-648,-563,232,-50,84,1000,-39,580,-468,-300,10,-73,-452,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-773,1000,-1000,-338,-1000,1000,297,-717,313,-954,-1000,958,-86,198,159,-459,-242,1000,-1000,378,-1000,-498,444,-258,-80,56,1000,788,-1000,721,749,-443,-887,731,475,511,-1000,345,-1000,-112,681,841,10,-634,-299,679,-324,700,-934,-494,1000,1000,-66,665,-1000,299,-1000,-47,1000,-827,-1000,123,1000,95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-46,-431,-1000,350,-1000,-403,-463,-253,1000,-727,1000,1000,447,354,-1000,-729,-890,-400,328,1000,-1000,917,-1000,63,-1000,-1000,-54,-78,69,1000,1000,-929,-956,-1000,686,-883,1000,1000,-1000,-1000,1000,-334,-1000,-8,524,137,1000,750,-539,-110,-1000,635,-1000,-425,-1000,1000,-1000,878,-1000,-46,-1000,-576,-603,821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{26,1000,-950,-497,-688,-400,235,-638,464,532,-571,-5,-1000,-178,864,210,320,444,-1000,-792,400,-319,1000,-98,1000,-982,-427,1000,-55,-77,-348,-778,-524,584,282,650,-486,-351,857,56,-971,-308,400,69,380,429,-1000,-468,-287,-733,237,-117,492,513,-33,-138,402,451,400,136,271,-779,147,-750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{214,-742,-1000,457,-1000,-189,-113,47,1000,-73,237,695,406,1000,-1000,-78,-872,-601,892,172,-963,-478,-466,222,-889,-1000,74,559,-1000,1000,1000,-1000,-742,-1000,-189,145,309,1000,-1000,304,1000,-533,-993,-445,1000,-281,610,1000,-569,-502,-637,511,-337,-1000,-992,615,-1000,-301,-246,-545,-1000,-160,-756,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{387,608,-365,-1000,23,-1000,860,-837,-671,635,-368,-377,284,-16,207,483,-477,-231,489,-698,351,-282,472,479,580,719,-1000,-37,916,-61,-274,-71,1000,-170,-91,31,325,-589,683,1000,-490,539,1000,-222,860,-1000,-1000,-523,-950,457,515,-466,-302,-292,1000,-1000,641,386,24,-522,811,-1000,-152,-631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-267,1000,1000,-692,582,166,743,-786,-661,897,23,1000,-552,655,856,74,-58,206,-454,-1000,742,134,1000,294,1000,563,1000,-418,-817,1000,-1000,528,-825,9,33,154,1,-1000,1000,1000,-1000,-589,1000,122,392,-538,-1000,-1000,147,-402,1000,1000,337,390,396,-842,1000,1000,872,82,1000,-1000,488,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{133,906,-400,-275,-986,400,776,-723,65,-368,-917,-582,-467,337,645,-210,-1000,744,-1000,-543,-400,-769,760,-428,854,76,-688,1000,-312,74,276,-1000,573,125,44,1000,-1000,-161,-106,620,497,78,307,-1000,143,-66,-1000,149,-1000,-1000,1000,-543,933,-747,-387,-91,-939,77,-1000,-1000,-1000,-247,-147,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-892,-573,-1000,-755,-823,1000,-568,409,1000,-1000,943,-732,296,-546,-736,-591,-574,1000,1000,-35,-1000,-825,-411,-513,-1000,-557,-936,254,1000,771,1000,-434,235,577,973,-1000,-434,885,-1000,-1000,892,461,-1000,-93,-307,1000,942,896,-959,357,136,-997,-381,1000,-1000,1000,-1000,-458,-1000,-122,-1000,1000,629,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{624,655,1000,1000,-91,-1000,1000,-785,-1000,756,380,713,-631,-876,819,-367,442,188,-1000,-1000,51,-1000,356,-162,1000,-268,736,476,-1000,207,-852,-166,403,167,-552,1000,541,-959,580,1000,-1000,261,1000,-1000,149,-537,-1000,-523,29,275,-579,545,487,507,641,-373,957,810,1000,-168,1000,-621,-518,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-889,624,-960,-307,-566,1000,62,-620,165,-481,-381,1000,-329,857,624,-1000,-361,774,-480,-1000,-658,67,585,-129,51,200,1000,419,-1000,1000,137,102,-966,223,337,-260,-467,-523,1000,-370,-44,128,95,213,368,1000,-291,-406,321,-249,983,1000,-166,836,-878,136,-339,394,1000,-216,420,-513,1000,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-1000,528,-615,-690,-492,1000,321,-406,327,-1000,-1000,1000,622,1000,22,-1000,-961,1000,-76,964,-1000,149,-104,-158,-901,1000,1000,96,-1000,1000,760,560,-1000,67,727,101,-552,-65,-1000,-91,1000,1000,122,-214,-829,911,415,547,107,265,1000,1000,-717,1000,-1000,443,-1000,-96,1000,-813,-896,-367,1000,531}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{-1000,1000,440,-610,-37,1000,-270,-470,632,45,-97,951,-1000,417,1000,-967,5,665,-1000,-1000,-334,-228,1000,-385,1000,-661,989,38,-876,-62,-166,-291,-1000,-4,491,75,-735,-960,1000,591,-865,-197,1000,36,205,345,-1000,-1000,-148,-1000,1000,1000,606,1000,-1000,558,523,1000,-51,-167,509,-788,1000,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{713,1000,615,13,-407,-814,743,-786,-675,897,23,-400,-449,-505,993,1000,660,86,-1000,115,742,-512,1000,195,1000,-442,-112,760,299,1000,-988,-757,575,1000,-324,565,-447,778,-120,841,-1000,-897,887,-2,1000,-770,-1000,-584,147,-1000,260,-400,988,-30,791,-986,1000,1000,-438,67,1000,-510,-492,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String):boolean",
            new int[]{46,312,-1000,-1000,-1000,-1000,-1000,-946,1000,1000,-535,956,127,-1000,-940,1000,-659,1000,-756,-1000,1000,-525,39,944,-1000,-1000,285,1000,913,843,1000,-1000,-217,1000,608,154,634,1000,-795,-896,135,860,1000,-45,512,-538,590,868,-572,-597,-1000,678,-1000,878,-163,375,-1000,694,-95,745,541,125,-8,892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-66,-40,-111,315,882,-546,675,903,-669,-692,-872,-367,862,416,-854,82,687,495,-35,119,-961,724,59,-14,-801,247,-439,-66,707,-354,-907,-192,-821,381,-588,766,840,-584,-358,459,391,767,27,-595,-816,553,129,42,753,-729,-845,-976,758,497,-255,-347,128,965,-71,-945,-255,-911,214,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{422,-399,-1000,1000,1000,-136,-466,-115,1000,1000,562,-525,658,1000,1000,32,403,1000,-436,-1000,35,-1000,1000,-672,633,-128,802,-1000,255,567,989,1000,-1000,1000,1000,-488,1000,-479,541,788,-1000,1000,-347,-1000,-70,1000,-322,647,215,-789,73,-511,-412,638,-1000,921,1000,1000,1000,683,578,-471,790,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{206,384,-1000,863,740,-136,558,1000,543,975,551,-1000,-383,484,890,-336,1000,777,-537,-607,-1000,353,1000,-1000,773,-384,351,-1000,-352,207,1000,338,-1000,1000,1000,-101,535,492,1000,-374,-828,1000,-1000,-758,-64,801,-1000,-228,-363,-935,-1000,-80,497,944,-1000,-541,1000,6,688,1000,228,192,200,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{479,129,-1000,831,-50,-180,624,45,-270,1000,1000,-983,1000,1000,-1000,1000,-30,160,-340,-1000,484,-1000,186,-203,-100,1000,-54,-1000,-445,-1000,705,383,-1000,185,841,-15,685,1000,73,130,-702,559,-667,985,-1000,1000,-596,610,247,-129,-1000,1000,438,434,-1000,285,-1000,75,-608,974,-1000,1000,951,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-659,1000,-1000,701,-660,-136,622,1000,1000,758,127,-1000,-317,-262,1000,32,1000,773,-1000,373,-1000,-907,662,-1000,1000,-716,466,-1000,-1000,601,301,1000,-1000,472,1000,-488,38,-400,663,788,-1000,1000,-1000,-1000,-70,801,-1000,-545,-734,-789,-1000,340,1000,1000,-1000,645,435,-723,433,1000,331,-433,790,-449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{554,-502,-860,379,-447,678,507,1000,1000,975,1000,-1000,-227,-606,646,-1000,1000,-39,-396,922,193,565,-20,-1000,452,-380,219,106,-824,-391,609,-237,-1000,-341,-193,-1000,-1000,-2,1000,165,-87,503,-727,-758,-3,801,-1000,128,176,-316,-446,873,79,541,400,-1000,1000,231,497,617,-773,-220,-1000,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-253,588,-1000,1000,101,-108,116,574,1000,-328,-62,-862,-317,371,321,-607,1000,-1000,-1000,394,-926,-907,1000,-1000,569,-593,466,-1000,-1000,74,1000,1000,-370,1000,954,-443,705,-947,285,-206,-1000,1000,-505,-1000,-409,906,-1000,-545,256,-1000,-1000,234,80,-1000,-1000,-1000,548,57,315,978,524,-433,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{876,-169,-115,334,562,-28,-490,493,543,647,1000,-329,-1000,-825,1000,227,968,322,-267,-607,-36,52,947,-1000,1000,-69,50,604,-352,-259,682,499,10,611,1000,-101,-1000,1000,1000,-374,-605,-432,-282,-59,64,-73,-1000,-94,-661,-45,-477,-80,619,422,-399,-1000,1000,-886,688,1000,321,1000,200,170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-366,1000,-1000,222,1000,-1000,818,-739,-10,1000,251,-370,-1000,1000,484,341,141,1000,185,-1000,-520,-71,1000,-987,693,-590,861,-1000,-602,718,1000,731,-1000,862,1000,112,1000,-438,-954,-696,-880,1000,-799,-1000,-344,-153,-417,-135,-957,-913,-1000,-1000,-81,961,-11,870,1000,304,782,58,1000,482,901,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{587,299,-1000,797,374,-427,-168,-125,384,1000,614,-618,-1000,-83,30,57,1000,-81,-1000,122,-623,1000,914,339,-587,-825,-1000,-400,1000,-1000,-2,296,453,727,578,-98,624,218,-657,-1000,76,-179,-293,-1000,-316,741,-429,-1000,414,-944,-1000,-225,154,-582,-1000,1000,1000,319,26,841,151,1000,322,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{214,106,-1000,1000,315,-702,179,318,-259,1000,835,-735,59,435,-666,256,882,-184,-1000,-1000,275,479,1000,-196,344,172,-54,-953,473,-1000,849,769,-573,407,1000,683,617,356,-597,-636,-514,898,-269,652,-606,453,-565,-369,54,-247,400,-535,-10,165,928,1000,169,854,1000,818,-61,-173,-163,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{1000,-1000,1000,-521,1000,-702,222,-240,-1000,-669,-226,907,113,490,-1000,34,845,-90,1000,-855,246,1000,782,1000,-1000,855,-651,1000,1000,-1000,-1000,-789,564,-60,-1000,813,-1000,-1000,-1000,-1000,1000,358,892,652,-606,755,1000,-1000,702,-476,414,-573,-736,-1000,1000,-639,1000,862,541,-1000,-1000,431,-961,-222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{-96,384,-1000,863,18,-136,238,959,1000,1000,161,-933,400,488,-400,-1000,909,1000,-450,511,-512,-1000,1000,-1000,1000,-384,1000,-1000,-1000,-296,1000,1000,-1000,1000,1000,166,265,644,38,105,-1000,1000,-835,-1000,-64,1000,-928,-228,-236,-1000,-898,-248,226,1000,-1000,-372,13,294,745,1000,356,192,-389,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{916,-208,-860,1000,1000,1000,178,894,1000,984,667,-1000,1000,1000,1000,897,135,1000,-1000,-1000,-20,-951,1000,-889,1000,133,1000,-1000,-1000,264,1000,958,-1000,858,932,-406,1000,-550,932,-114,-1000,1000,-920,-502,-1000,418,-808,951,415,-1000,-320,986,-478,1000,-1000,-92,236,1000,1000,1000,616,-827,-799,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{842,-1000,1000,-572,1000,-97,511,684,-1000,-686,-540,86,191,462,-1000,499,845,170,1000,-1000,-31,829,782,1000,-1000,533,-517,1000,866,-1000,-1000,-362,179,-60,-875,269,-1000,-1000,-551,-1000,1000,222,1000,189,-756,615,843,-1000,558,-431,1,70,-66,-199,1000,-1000,1000,324,487,-1000,-974,-135,-931,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "isDoubleMetaphoneEqual(java.lang.String,java.lang.String,boolean):boolean",
            new int[]{1000,377,-1000,24,-555,726,387,967,477,-112,1000,-1000,-1000,-1000,1000,1000,911,-1000,-1000,1000,-158,318,59,171,879,-1000,-1000,-66,-1000,-1000,1000,218,-1000,-390,1000,-611,-1000,1000,779,-1000,-656,767,-959,-595,-1000,715,-757,-1000,1000,-9,-460,1000,213,-946,140,-1000,234,391,772,1000,-797,713,-566,-247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID|getMaxCodeLen=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.language.DoubleMetaphone", "org.apache.commons.codec.language.DoubleMetaphone", "setMaxCodeLen(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
