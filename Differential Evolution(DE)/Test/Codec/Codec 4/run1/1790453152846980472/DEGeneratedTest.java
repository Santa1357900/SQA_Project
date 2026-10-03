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
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:ODc=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{-1000,1000,125,857,718,580,578,-1000,-938,755,68,1000,571,934,-358,-911,149,159,321,-300,1000,1000,-529,400,1000,1000,-747,-38,1000,-216,896,1000,307,-609,-1000,-386,1000,27,-150,1000,1000,1000,-1000,-1000,-26,531,572,1000,-462,-841,26,1000,-916,-720,461,-321,-483,302,-630,-842,85,400,364,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{-865,378,-1000,561,413,437,1000,-1000,-1000,143,477,-56,631,-343,-606,-1000,150,-1000,1000,-115,-1000,393,1000,-1000,-380,817,-179,-159,470,-496,-334,-1000,5,783,-1000,-1000,30,-248,-1000,1000,276,647,-49,0,864,707,59,1000,-1000,307,8,85,-423,-458,-405,40,-403,920,-471,458,518,-1000,-154,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{-370,84,1000,-561,1000,-1000,1000,400,783,-917,-422,364,1000,474,648,-658,-893,-670,612,-362,190,-609,234,1000,654,1000,39,947,1000,-1000,338,-312,-255,301,0,1000,626,137,1000,84,-1000,-977,-1000,576,-741,458,138,-497,283,-545,-660,400,-1000,1000,1000,-1000,-404,-390,-152,-924,279,1000,773,287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("ARRAY:[B:2:19:java.lang.Byte:MTE4:19:java.lang.Byte:ODc=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{634,1000,20,191,1000,586,876,827,620,44,1000,-914,-1000,-512,-400,-672,-78,543,-686,-1000,127,-152,25,-818,822,-111,-1000,-1000,-527,1000,742,20,-861,400,300,-747,516,1000,13,148,-1000,547,-466,244,580,885,1000,-300,-866,1000,-533,384,23,-800,-400,1000,-659,-766,-239,-1000,-309,-775,-555,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{-790,305,-498,1000,-367,580,1000,-182,-1000,-1000,-452,1000,665,237,700,1000,-935,-563,-952,828,434,-863,500,1000,-263,-299,-320,-126,92,1000,-96,-85,-1000,145,309,117,652,-1000,-1000,-145,-341,-1000,-561,1000,-653,-762,-1000,77,911,-923,742,682,-1000,1000,1000,1000,-613,325,-241,989,1000,1000,1000,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{308,-460,6,-307,759,1000,807,938,979,-203,1000,-1000,-897,-553,-122,370,-337,216,-206,-1000,-167,-298,-546,288,23,-169,-238,-1000,-1000,-325,-13,772,-899,1000,20,124,-781,1000,400,-157,-1000,225,52,-359,-152,1000,762,-195,-609,954,-744,-1000,-39,-114,220,459,-347,-1000,-29,-1000,-88,-1000,-1000,941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{1000,217,-86,-1000,146,-974,-428,-155,1000,713,922,-413,-1000,-728,921,-1000,-82,1000,543,-859,1000,-563,-542,-525,3,-1000,291,-923,-1000,-1000,-7,1000,-644,-788,394,-460,1000,1000,1000,661,-930,-105,-328,-945,130,902,1000,-834,-486,140,-577,580,-677,-1000,-420,1000,-159,-1000,-587,-15,-1000,-70,-117,939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTk1:19:java.lang.Byte:LTcy:19:java.lang.Byte:LTM0:19:java.lang.Byte:MTE0:19:java.lang.Byte:LTM1", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{-147,-616,-269,-170,-74,1000,-1000,-940,80,-389,35,1000,943,-193,-854,1000,802,316,-327,47,768,-1000,137,-444,1000,338,-682,544,679,-1000,-1000,583,1000,1000,721,731,594,-601,276,-465,-447,-1000,950,-1000,332,-1000,853,-788,-357,-1000,-814,843,128,46,-1000,830,606,-943,-409,-137,459,55,841,-371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.DecoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTk1:19:java.lang.Byte:LTcy:19:java.lang.Byte:LTM0:19:java.lang.Byte:MTE0:19:java.lang.Byte:LTM0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{-728,192,435,-971,-407,145,-391,-890,548,281,110,459,-216,-866,-134,673,-193,-809,101,702,426,860,-220,693,-885,-152,923,-83,-487,-683,-452,-837,-254,-164,516,110,453,-840,-18,556,-564,182,322,-891,-253,992,-575,-20,628,-53,-917,-164,268,862,-202,333,-434,-966,-168,-79,-749,-381,531,757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTk1:19:java.lang.Byte:LTcy:19:java.lang.Byte:LTM0:19:java.lang.Byte:MTE0:19:java.lang.Byte:LTM1", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{811,-736,-70,396,-176,216,-711,715,1000,112,-528,1000,-1000,398,-789,737,-165,86,-136,-1000,-872,-1000,728,91,-412,-1000,1000,616,-1000,1000,538,-1000,-757,-345,683,-856,128,75,231,1000,669,-27,197,1000,-925,959,163,-285,-1000,-538,-1000,103,-146,1000,115,475,-1000,-1000,-1000,-167,96,-453,-1000,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTI1:19:java.lang.Byte:MTEx:19:java.lang.Byte:NTY=:19:java.lang.Byte:LTI5", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{-30,-227,796,295,518,1000,161,459,78,-866,-427,528,1000,442,-859,-1000,1000,-756,-756,828,1000,-1000,1000,-379,-670,1000,49,-899,-894,222,-701,-558,-549,-173,360,1000,-294,497,-588,121,632,113,-294,-788,1000,-886,400,272,315,164,-1000,17,209,1000,-65,-1000,-829,447,-805,-350,798,644,1000,-308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{411,645,-689,545,-980,-142,-673,-217,-94,142,540,-583,757,-517,267,-272,-631,982,151,14,-280,-628,590,-284,168,717,-731,680,814,-311,120,863,30,-318,-942,-970,-282,-907,378,-775,928,59,847,-840,139,122,-798,41,-272,427,-967,808,-76,312,807,-724,-95,-569,228,268,589,947,993,447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:MTA=:19:java.lang.Byte:MTA4:19:java.lang.Byte:MjA=:19:java.lang.Byte:LTM=:19:java.lang.Byte:MTQ=:19:java.lang.Byte:LTcx:19:java.lang.Byte:ODQ=:19:java.lang.Byte:LTY4", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{-630,-703,333,-169,544,-449,-793,-452,337,748,164,-678,-86,-217,567,407,645,-421,767,-947,-535,993,-728,-289,865,-565,-657,-176,-552,-295,772,196,982,531,-438,-878,396,-782,833,735,273,915,383,352,-964,93,7,14,268,12,979,-168,-635,-819,860,-691,-914,120,-796,-649,-540,-899,-331,368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:LTY1:19:java.lang.Byte:LTM=:19:java.lang.Byte:LTI4", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{787,-764,180,-453,430,595,379,557,-537,-1000,-356,3,375,348,-244,-1000,-30,633,-510,450,1000,-445,969,-749,785,-931,-248,-273,-809,58,1000,579,-430,269,438,1000,-260,174,-99,1000,-61,-267,-218,-497,286,-434,-681,289,579,-881,-445,265,-366,819,241,-875,-1000,1000,1000,-968,963,858,166,-327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("ARRAY:[B:9:19:java.lang.Byte:LTI2:19:java.lang.Byte:Mjc=:19:java.lang.Byte:LTcw:23:java.lang.Byte:LTEwOQ==:19:java.lang.Byte:LTQ=:19:java.lang.Byte:LTkz:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTk0:23:java.lang.Byte:LTExNw==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{606,-832,246,951,-1000,1000,557,-307,-752,1000,-673,-1000,-592,139,218,585,314,1000,-903,-1000,531,-691,-573,-1000,1000,-96,757,135,-1000,773,1000,-1000,1000,-598,886,144,1000,123,1000,-313,1000,129,-54,56,1000,152,-580,291,1000,-692,-296,-776,-1000,710,1000,-1000,-1000,1000,755,-1000,-678,-638,750,925}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{-583,393,-757,34,-1000,194,-452,-1000,622,592,-720,-604,-1000,417,567,1000,1000,-1000,-687,-410,-570,-285,124,1000,-1000,789,-1000,567,-316,-1000,-1000,196,1000,372,-101,-1000,1000,-1000,266,-1000,168,1000,753,-385,-1000,-115,1000,-744,-1000,-339,266,-964,-625,-901,1000,-1000,-44,120,-1000,1000,-1000,-1000,-77,879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{953,392,540,-849,-97,-56,-1000,1000,754,272,-1000,-424,1000,-485,231,88,1000,1000,286,-173,121,-934,549,-1000,-763,810,-728,-1000,-1000,249,-1000,-514,-742,-72,-710,-141,314,965,276,1000,-176,802,1000,734,-513,225,-171,-1000,552,-449,777,-101,-482,-603,-215,-853,711,346,131,-988,376,341,235,-507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{995,49,-539,1000,-751,1000,1000,619,838,1000,519,-350,-102,792,672,13,355,1000,180,-1000,993,473,124,366,-522,-608,-637,-15,-764,1000,1000,-602,-506,1000,-824,1000,-747,869,790,-25,-811,1000,985,-650,-888,382,-625,-466,-75,-115,-191,-751,-638,-966,1000,-1000,1000,-929,-348,584,-505,72,413,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:MTE2", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{893,-356,584,1000,75,1000,754,742,-446,420,387,-958,20,-230,898,1000,83,619,895,-606,911,262,500,128,-400,524,13,450,-503,1000,1000,-430,-177,1000,-449,1000,307,618,267,-1000,-48,902,928,975,-1000,-1000,-469,662,97,376,-684,-968,-554,-1000,722,-25,567,-84,78,-22,-565,-313,-369,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{-986,1000,1000,1000,1000,1000,844,127,707,365,1000,178,-937,1000,-450,909,307,1000,839,1000,12,413,15,674,-105,177,-473,-1000,417,-1000,-364,1000,605,-788,-337,-1000,-1000,-1000,68,1000,-620,-1000,-1000,1000,-1000,-1000,189,-1000,-1000,-1000,209,-535,1000,-929,1000,-437,930,731,507,-988,-385,694,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("ARRAY:[B:16:23:java.lang.Byte:LTEwNg==:19:java.lang.Byte:NjY=:19:java.lang.Byte:LTcy:23:java.lang.Byte:LTEyMg==:19:java.lang.Byte:LTc1:23:java.lang.Byte:LTExMQ==:19:java.lang.Byte:LTI=:19:java.lang.Byte:MTA3:19:java.lang.Byte:MTU=:19:java.lang.Byte:LTI4:19:java.lang.Byte:LTE0:19:java.lang.Byte:NjM=:19:java.lang.Byte:LTM3:19:java.lang.Byte:NzM=:19:java.lang.Byte:OTY=:19:java.lang.Byte:NjI=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{669,784,-515,599,92,-477,330,430,443,-399,58,-373,-502,-148,-859,-191,28,406,999,406,-737,280,712,-284,-334,-978,-375,-902,649,-592,-158,68,146,453,309,919,-408,-68,203,-822,-204,-496,-304,660,424,-891,793,-818,368,785,-56,-884,161,-701,-649,-148,754,77,-468,997,-504,-227,-437,-354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{-241,-981,-636,975,-855,100,360,343,-671,109,605,-797,298,870,689,552,-520,-94,-660,-41,-303,311,208,946,-779,540,464,-141,-451,-106,661,245,769,610,38,576,62,538,448,-184,-252,-834,-550,851,-385,411,737,47,-264,23,49,322,916,-259,996,-953,69,364,-220,794,-696,-158,743,510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTU=:19:java.lang.Byte:LTk4:19:java.lang.Byte:MTIy:19:java.lang.Byte:LTQx:19:java.lang.Byte:LTM1", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{-619,-556,137,-810,-1000,1000,-693,521,324,-413,1000,-380,1000,-113,1000,365,-892,-1000,-1000,-645,-435,-807,-594,782,-551,-337,150,-282,114,1000,657,653,1000,576,921,-1000,1000,-721,549,295,-1000,-1000,899,-630,872,833,-1000,504,-1000,-1000,1000,1000,101,-539,-157,-1000,5,280,-978,44,-666,-705,-15,222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:LTU=:19:java.lang.Byte:MTI2:19:java.lang.Byte:LTY5", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{677,-367,-71,1000,-500,-747,-296,-1000,-624,583,-240,1000,124,595,661,-712,84,-558,746,-324,234,-1000,-908,-641,-454,1000,-418,74,1000,-213,1000,89,-135,-358,-401,-436,-250,-326,353,-494,-116,65,-516,187,-175,153,281,-45,-1000,922,538,-366,-241,-27,-1000,594,531,-11,-350,-848,-460,-252,794,-49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:LTU=:19:java.lang.Byte:MTI3:19:java.lang.Byte:NTQ=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{442,382,-10,749,-224,-300,-577,-373,-611,687,157,26,285,-431,304,-138,-390,-387,821,608,-847,186,374,-118,-497,394,-318,897,-752,-626,173,-279,450,-430,-286,-86,117,-997,125,-467,-222,-609,-194,1000,267,-84,290,-1000,-1000,823,544,308,-708,-373,-790,-279,615,-196,-191,-117,94,-430,484,-594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.math.BigInteger:NTM=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{659,780,-398,870,635,398,499,-879,1000,-1000,-1000,-1000,975,-9,1000,-1000,-1000,-1000,-1000,297,647,620,1000,838,-1000,1000,-1000,-834,-1000,-1000,-1000,-915,-1000,1000,-1000,-1000,-1000,-759,1000,-1000,-1000,772,990,-1000,345,1000,1000,-750,-1000,1000,238,-1000,-735,1000,1000,277,-1000,-1000,-1000,-181,-1000,-14,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{-835,-147,-563,175,1000,-793,355,573,299,-1000,544,-357,389,-245,-943,-402,216,-377,-472,1000,-67,-129,-400,388,-627,396,1000,-86,1000,-733,424,773,362,234,214,7,752,612,-449,1000,-1000,-187,-949,600,-15,-1000,-482,1000,90,1000,361,621,-984,16,452,-496,-172,59,63,758,-787,1000,-864,-771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{369,-160,-434,1000,298,-642,-241,-584,730,-1000,-601,-787,-506,300,-483,1000,507,121,1000,391,984,709,31,20,-405,-489,-625,-572,-456,-368,-79,-1000,480,-678,415,-1000,-155,-1000,974,-539,-941,-92,-468,619,-770,-440,254,-1000,282,313,-823,-539,-534,57,391,-366,-452,-1000,1000,510,-810,-60,-96,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-1000,-782,999,-1000,-3,166,1000,-983,-871,487,1000,905,-735,302,681,-597,-490,220,76,-953,1000,-262,1000,-1000,1000,-602,424,823,74,580,-337,452,-1000,42,-392,410,-119,-83,43,1000,1000,-67,-80,50,-673,-379,51,-252,94,-93,474,-158,-9,-1000,-252,-721,-1000,-512,-101,-557,-362,1,-711,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{1000,269,-226,-253,765,876,1000,-957,-810,890,248,624,791,1000,43,1000,-676,1000,965,-67,536,-408,601,-1000,427,-1000,835,820,-11,242,1000,857,-656,-266,-1000,-736,-1000,620,-691,-866,-630,1000,-338,920,-499,1000,1000,1000,548,-1000,153,-319,-538,-252,1000,-259,-463,-552,-1000,-729,822,-486,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:OTA=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-330,-408,602,-407,451,290,1000,-914,133,210,-429,-1000,-433,-20,-431,1000,-575,424,-721,416,327,-1000,1000,-29,1000,-498,-493,335,-61,-610,305,465,-131,-1000,-576,1000,607,950,222,-164,248,220,311,-122,112,-726,555,96,780,-801,72,-1000,1000,874,-429,-1000,-928,365,-801,-355,-28,-153,264,-138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:MQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-650,-241,999,399,-45,585,-67,-983,-871,239,961,404,-870,285,370,-825,-210,780,728,-375,896,16,606,-661,781,328,833,913,-562,986,562,800,-707,120,214,410,-80,-528,-584,585,471,329,377,275,-673,431,908,-149,600,-133,188,870,-498,-986,781,-721,-699,-562,-52,104,-362,-686,283,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-1000,153,457,-11,776,-238,701,-1000,-1000,21,1000,1000,-89,411,51,-798,-619,235,474,-1000,1000,-968,1000,-1000,258,-671,-112,822,323,1000,-589,454,-1000,156,-531,81,299,-129,-612,1000,174,-854,119,-464,-804,-407,-20,-808,-567,-4,1000,-886,-1000,-1000,33,-567,-1000,-452,951,-530,945,-758,-622,20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-163,-921,474,-143,99,25,592,-562,-236,-314,417,360,515,-448,789,-822,35,665,414,-982,586,-410,823,-99,-940,-255,767,356,631,-616,161,650,941,530,-553,88,939,939,-708,794,-70,-228,-87,137,178,-916,993,-470,486,-560,173,737,602,-944,358,0,961,-346,-233,-685,-639,946,-486,-991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{338,188,-823,89,-44,-735,-773,-59,-952,747,-933,723,-529,717,-378,-148,-263,-921,405,-855,-879,-752,-540,-338,-820,-64,576,-158,43,951,-774,13,-258,-374,-700,94,-218,-193,509,635,-813,-857,-940,210,330,861,124,627,-999,-846,-491,-597,364,856,-49,543,-261,176,474,-241,450,-95,-608,981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{283,232,-853,-403,755,651,-157,-199,681,-878,188,-85,-458,-730,-136,77,747,-8,312,-56,227,187,-198,413,86,999,-126,-721,-929,-572,20,832,582,-132,152,-826,-139,-567,115,309,-971,764,334,992,-286,416,-474,705,164,-89,333,-824,948,60,-270,-613,583,-740,92,-820,-192,-876,932,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{-121,-761,-629,841,220,-225,-297,924,195,1000,-1000,842,-726,1000,-1000,1000,1000,-1000,-1000,-1000,-1000,-424,-625,-220,-758,-51,4,-132,137,-915,813,-521,-269,429,301,612,-1000,1000,-532,-1000,964,4,-593,-143,1000,1000,666,1000,-920,-235,39,235,-621,1000,-1000,711,-182,924,-577,-501,792,1000,815,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NTU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{-813,-114,-140,-789,620,571,-1,45,847,207,938,427,603,116,-541,291,751,364,459,630,448,305,-139,-854,552,513,-548,222,-183,605,716,-49,901,910,-719,620,-734,391,-363,862,612,806,-578,-517,-331,-318,-247,886,877,363,829,-52,-216,138,369,249,820,-787,296,-384,518,221,532,-923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:OTg=:19:java.lang.Byte:NDg=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{83,-439,-995,445,-841,542,966,692,124,-481,-723,1000,17,198,392,-780,846,-803,-487,443,360,674,-872,262,-861,-208,-198,712,363,-443,-362,111,-951,9,251,781,614,201,231,-651,195,-4,-347,-809,323,271,-605,-533,-720,845,790,135,-280,-877,426,-629,-977,-682,-189,882,-780,-101,42,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{964,-25,998,304,90,-630,-821,-1000,-429,409,1000,-25,-946,-555,650,532,370,0,686,-708,-1000,171,-586,-781,0,185,-844,-213,-675,-48,474,1000,252,-293,-551,16,82,544,-779,1000,-1000,-543,458,419,626,1000,383,1000,-756,-150,-415,-272,639,0,-713,1000,-628,0,-688,0,837,-182,322,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njc=:19:java.lang.Byte:MTAy:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{-651,36,0,585,472,-978,-482,-1000,-513,724,549,1000,204,84,1000,1000,-97,0,-333,-502,-511,449,505,-37,-806,277,282,0,610,0,-225,1000,529,1000,-58,-831,-234,101,0,658,-56,0,575,-1000,443,0,157,-142,-348,196,711,-458,-810,-56,-25,-621,860,-41,1000,1000,110,654,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODk=:19:java.lang.Byte:MTE4:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{863,463,253,907,-577,-284,-436,34,907,-974,354,759,-575,-936,-262,11,881,-199,528,12,-479,865,-744,-535,302,-440,-921,-704,-509,440,-550,-218,791,725,-583,-482,-216,-776,-98,426,-505,952,367,704,490,-841,910,-467,69,-894,28,-841,849,-405,-979,9,-705,-997,346,444,-375,349,-919,890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDg=:19:java.lang.Byte:MTAz:19:java.lang.Byte:NjY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{-206,231,212,720,-818,-700,-155,-781,-528,288,-231,925,-310,-794,-330,307,-419,-827,-949,-692,625,-181,-517,340,-779,816,985,-538,747,281,-279,404,113,-901,-797,-320,-100,-315,-353,818,-782,160,213,-580,991,488,486,664,549,-298,732,709,141,-933,-755,-562,230,702,-635,-10,-689,-948,682,597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTAy:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{825,11,85,498,-499,904,560,628,-448,282,-69,214,-15,-660,-342,-467,106,-822,-400,-56,-167,-780,884,281,-165,265,481,73,-538,781,248,82,-553,-667,370,-704,236,-333,160,589,-546,-122,-9,570,-497,-617,773,797,574,925,-166,-479,-542,-270,316,-649,459,123,-707,867,-557,-608,-341,-920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:NTQ=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{239,658,138,-280,904,-65,922,155,660,-135,883,-265,-435,-1000,102,-326,327,-692,161,504,707,-489,-523,260,-919,338,219,193,966,-140,912,-444,341,417,-990,732,447,-773,-267,-16,447,740,-711,-547,749,4,240,677,-848,895,210,877,17,916,395,510,-372,-532,-585,200,611,967,923,-959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("ARRAY:[B:9:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{-487,119,1000,1000,197,904,980,-671,219,-653,777,586,-448,-660,-955,168,1000,-551,1000,-744,817,-623,-1000,796,-1000,-107,-783,-995,1000,-610,1000,1000,-1000,-667,-789,-28,714,-809,922,-730,-178,23,106,-559,199,-32,-1000,58,-1000,295,579,460,-573,-227,1000,-649,-961,-513,1000,-479,-14,621,944,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{-770,-1000,233,1000,-1000,540,-461,-287,-956,-284,-144,331,597,106,-337,362,72,331,-892,-608,-315,-219,1000,-266,1000,-37,-899,-1000,-1000,1000,-179,1000,-774,-1000,1000,-1000,-255,1000,469,-724,-1000,-1000,464,819,-1000,-457,-1000,-734,1000,-1000,-47,-1000,224,-228,-376,-790,545,1000,484,105,-905,-733,913,-49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{1000,-92,-1000,-1000,-259,578,855,-585,-947,304,-525,-46,385,224,1000,993,-1000,-532,-692,-503,740,-725,26,-289,432,-92,1000,1000,-607,90,-1000,-1000,674,1000,-201,-230,376,-816,-1000,505,151,694,-606,-1000,311,-736,1000,-329,813,-973,80,-355,901,394,-671,-369,1000,740,-1000,901,-218,-878,-326,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{57,-791,31,134,-46,11,918,806,-566,658,-74,-298,109,150,418,-566,961,61,379,-79,-299,844,707,750,-672,923,-935,-344,-48,-17,811,931,-952,46,-709,-335,257,362,-584,-930,-364,912,817,832,798,443,151,419,784,316,126,598,719,-818,-308,995,-725,-468,-489,-476,836,-526,-717,691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODM=:19:java.lang.Byte:ODg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{1000,1000,-503,377,778,608,-1000,-1000,96,-957,-493,1000,224,195,-1000,1000,-684,1000,-1000,-620,237,-959,-1000,-1000,-738,1000,-285,403,-375,813,-1000,-244,1000,257,553,332,-354,-456,750,1000,107,15,938,-1000,1000,-890,-1000,-1000,-1000,-1000,294,944,-956,-443,335,-62,1000,747,54,880,400,600,-189,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("ARRAY:[B:9:19:java.lang.Byte:NzE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTM=:19:java.lang.Byte:MTE5:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{-403,-1000,59,-541,468,-843,918,265,-535,-1000,-194,890,-562,-360,-172,241,-635,1000,-351,-231,-454,-562,-101,378,16,-680,-935,286,298,640,-738,-1000,935,163,278,-335,-155,727,-584,-930,-585,667,654,-814,1000,-504,60,-693,31,-132,-427,-109,-1000,-403,176,878,1000,-524,493,495,106,-384,-737,691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{-1,-667,13,725,138,-924,-670,957,-607,912,-695,-338,989,297,700,317,-815,-736,499,891,-979,129,694,52,738,-250,864,-702,966,356,-867,405,-71,-950,854,-365,842,601,-7,-457,455,888,591,-137,258,-532,909,985,257,-889,917,826,-595,-104,619,500,818,-558,388,169,-302,-758,963,-356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:NzQ=:19:java.lang.Byte:MTIy:19:java.lang.Byte:OTU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{544,1000,48,-1000,178,814,182,-1000,1000,-1000,-1000,992,-1000,-1000,98,786,1000,702,19,-1000,1000,-1000,-442,-402,-298,1000,-1000,480,-1000,-285,-523,957,-22,260,-531,-989,-330,1000,-1000,-609,-698,113,913,311,1000,206,-1000,-743,-1000,-780,-883,819,782,-22,25,-522,659,1000,683,305,265,-787,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NzE=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{-67,927,342,943,-1000,-276,397,-738,288,1000,106,-1000,77,1000,-901,-449,1000,-615,575,-710,-379,1000,-605,-602,-759,146,1000,403,607,-232,-62,557,-288,-386,-498,-908,-763,-1000,384,1000,1000,-148,716,675,-738,-717,249,239,493,972,284,-313,333,531,74,430,634,-161,-778,-1000,-117,-441,103,-153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NTc=:19:java.lang.Byte:OTk=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{-783,278,315,254,140,920,-266,-63,-148,-71,-555,-160,-617,647,-102,409,-584,4,49,291,224,-527,-373,-686,-128,-316,-193,9,-116,127,-763,-596,-505,-449,396,-584,323,168,-805,-960,855,-422,393,804,477,434,929,-591,-45,-670,570,381,-744,-902,460,665,367,793,-972,-429,409,995,-448,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{179,-494,-520,-508,66,-394,365,692,-383,676,-425,651,-830,759,-586,-583,246,635,-518,939,-318,-480,-407,-257,754,283,712,-842,790,405,178,635,695,-875,-494,299,563,676,988,-250,-953,347,973,-619,569,198,-755,-35,-452,731,-772,123,885,-489,865,-553,652,-177,-986,-522,597,-183,899,-449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:MTAz:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{628,-911,-898,-504,-481,485,-437,741,697,581,789,-261,-932,342,-411,710,-365,-20,238,-564,-661,605,-857,685,704,920,-303,-546,-736,414,-424,291,-343,-294,-274,546,-969,-832,-768,-701,908,772,928,-63,35,-769,-882,863,912,917,-881,-333,-353,804,444,-763,607,727,-583,-251,-84,623,754,255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.String:eGdELw0K", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{-99,-584,778,936,604,-290,-700,753,82,-826,988,764,55,-19,257,-284,334,429,452,197,751,-232,-839,-617,-814,688,-93,-223,-656,-146,53,-672,-101,642,-882,849,-162,-76,333,-87,-572,140,-755,522,61,-803,438,760,-551,-218,-966,368,-895,587,-453,711,875,594,854,980,395,144,595,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.String:Ly84QkFBPT0NCg==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{910,121,279,-707,782,-411,-491,917,664,-908,104,952,986,81,412,76,235,35,463,7,-568,-430,-911,942,47,-231,-185,-574,-107,677,-262,171,417,741,895,817,-901,-383,-677,931,202,-825,945,-401,-12,-146,-299,-50,698,-473,-60,-383,-896,-28,-38,-279,207,-288,859,-706,824,-498,265,-757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.String:QVA4Qi8vOD0NCg==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{857,-878,-567,886,572,129,769,-47,284,487,75,-149,-300,769,931,549,772,630,484,-803,-504,453,-243,424,-174,-500,133,497,-583,-480,914,306,872,537,-787,649,-591,-799,81,554,401,-455,-282,-490,135,-795,-79,-635,313,-702,-516,-603,-713,967,185,597,874,361,-805,563,661,286,266,552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTIx:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{483,-234,-703,328,-314,-584,-183,948,-279,72,-698,172,564,-581,849,222,105,227,-230,-326,273,-294,328,600,-619,-459,-10,-484,-423,-986,-410,-502,-775,-42,770,395,-513,-244,522,-154,423,-963,775,-487,504,301,545,825,626,313,-608,374,-610,763,-336,-896,731,438,-676,-597,-194,-44,-967,342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{869,358,-535,-75,489,-843,-999,285,536,221,716,739,-658,-154,559,-675,652,-289,-115,-334,1000,542,449,-169,376,845,-887,-477,-673,-656,913,-835,548,157,83,123,504,-872,-852,964,631,250,237,457,279,652,417,-173,719,-773,25,686,-755,-332,-268,996,682,-602,384,-541,488,-308,-449,802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NTE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njk=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODI=:19:java.lang.Byte:MTAz", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{1000,-362,-170,-691,-419,-440,592,-698,-949,1000,1000,1000,-740,1000,-118,-1000,-637,403,372,-303,1000,-198,423,604,-585,904,-404,-1000,-658,730,835,-1000,871,45,857,346,213,-67,24,700,1,-214,48,1000,675,133,978,288,-204,-884,-286,-460,-538,-681,-690,-54,1000,38,-735,-651,380,188,248,14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.String:QUFEaQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{189,399,-42,-456,943,-306,-206,-886,-412,-164,215,937,-387,-886,699,-775,-485,-374,-807,-368,606,864,-596,389,-30,91,555,-528,502,-221,497,-105,735,-375,-144,-188,520,943,-717,645,510,615,-190,-894,-105,188,-179,-290,-146,-813,-794,-892,-221,298,-104,432,-493,-363,170,705,-508,32,-132,-645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.String:V2N6X0FmOA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{-643,890,-518,-521,-110,-979,843,-211,-551,383,614,-979,373,977,541,320,889,653,-408,-219,174,-353,81,399,665,-403,842,-706,-277,-302,959,-768,494,922,-593,-984,204,518,-677,879,991,-638,573,654,-130,166,769,214,463,-975,967,-567,-298,337,-98,746,-111,-249,-196,-707,224,-543,-419,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.String:X184QkFB", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{148,964,968,870,-562,-383,853,-286,177,-649,45,767,-484,828,172,-231,578,-990,-499,-508,333,307,517,720,443,247,681,482,-952,-770,-217,-528,-783,148,-893,73,168,975,-996,3,243,-241,408,242,108,914,338,-404,676,148,-525,22,850,965,-439,688,-666,231,-478,598,500,34,-4,-397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTc=:19:java.lang.Byte:OTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{1000,1000,-719,596,568,1000,-1000,459,892,-326,-1000,966,24,-403,-471,1000,-186,-288,-769,-593,1000,1000,-488,-179,1000,88,806,400,-260,979,36,-755,-420,62,1000,518,-907,-883,-45,-795,-782,413,-474,-1000,1000,-600,995,-1000,98,-768,-961,119,-1000,1000,-127,358,-757,956,-56,-712,-1000,-178,-999,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTAy:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{1000,-211,-1000,-587,-1000,-1000,-397,1000,-1000,553,-1000,1000,814,1000,295,-1000,-1000,-9,239,1000,1000,-228,847,1000,-141,139,-108,1000,-536,-856,413,-580,-1000,342,-968,-1000,684,-1000,1000,417,-167,291,-877,942,837,-1000,-166,-799,806,-776,242,1000,-497,1000,623,230,-47,-755,-443,-372,-997,-201,410,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{-376,-773,-286,832,198,658,991,-7,-461,355,815,-213,-965,-845,-324,-474,-706,-286,662,-25,-85,-265,-587,894,323,-568,-564,467,249,-895,74,-123,-998,652,502,-54,657,735,-582,-756,-506,894,782,341,598,-618,885,-988,393,742,-649,95,-934,228,200,-560,-668,-953,-932,-73,-214,-898,840,886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{160,629,-1000,681,-1000,-1000,-1000,1000,-431,642,-1000,1000,169,1000,232,-424,-758,-771,58,206,1000,1000,959,-1000,1000,873,678,-151,322,979,-442,-250,-420,722,-1000,-1000,66,1000,1000,-795,638,-41,-474,-1000,179,-731,137,173,112,-1000,784,1000,-1000,489,-330,358,-1000,1000,590,-1000,-884,1000,1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:QWY4QQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{863,800,360,249,-746,661,62,411,-793,-819,-875,-826,249,622,-690,368,547,694,116,-467,506,-265,-536,-844,-31,187,-700,-563,-812,948,-589,-524,699,-373,356,-855,-28,522,838,566,-690,-797,52,159,-182,15,-396,-529,492,499,444,-334,-524,779,-90,453,351,278,487,-501,-43,795,-869,914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.String:QUFEXzZB", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{-865,904,-103,117,-585,-504,87,-463,-91,896,385,-1000,759,1000,-851,127,-207,-473,1000,-681,1000,359,1000,-1000,643,1000,-642,-917,219,-184,-1000,-429,-1000,1000,337,-72,-1000,36,-576,-810,-641,-623,341,-1000,740,1000,-346,-410,-845,-394,-667,372,915,1000,994,105,273,-147,-1000,860,243,1000,-511,-357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.String:QUdUSS93PT0=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{715,662,-1000,1,217,1000,-1000,823,1000,-518,-560,-338,102,512,396,530,-358,454,-1000,4,284,838,-537,218,-607,-1000,-232,1000,-1000,-372,603,30,113,787,307,-1000,-871,383,1000,569,-691,-1000,-1000,-417,271,1000,-32,-1000,-205,-576,-659,-325,-881,1000,-145,107,749,-1000,252,296,-264,-931,269,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.String:QUE9PQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{1000,568,42,370,-60,-375,-665,-176,684,374,102,464,-439,606,-690,14,547,694,-429,-413,-998,631,147,-251,-675,-760,-212,-983,-812,-372,-593,1000,-172,-462,-181,332,67,-97,107,-376,329,-312,292,527,-707,881,-121,608,-284,900,-397,-389,-17,496,-585,604,120,-299,-146,-783,891,-724,225,843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{989,783,-1000,317,1000,851,-771,1000,588,-219,1000,593,-1000,-366,738,-1000,-1000,-21,-1000,-40,94,1000,317,919,-1000,-1000,1000,-134,797,-1000,-616,-295,-529,-1000,-547,702,704,1000,1000,-389,1000,329,-355,1000,-1000,602,-493,539,496,1000,1000,-1000,-1000,3,636,977,-842,-936,-651,1000,-259,-1000,-756,928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.String:X3dBQe+/vQE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{671,19,1000,447,-1000,-75,800,-625,-407,-1000,-1000,-435,1000,260,-1000,1000,1000,1000,1000,271,182,-1000,-1000,1000,592,1000,-351,776,-1000,174,-2,-419,122,815,-350,-1000,-832,-1000,-824,1000,-1000,-568,748,-1000,610,-112,519,-314,-616,-1000,-1000,1000,698,-584,255,-707,1000,672,554,-1000,-767,1000,114,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.String:QUFBQV9fOO+/vQAA", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{-770,964,6,-1000,915,109,-1000,1000,1000,-94,438,894,-198,-463,747,271,-409,414,672,604,792,-271,919,-33,-662,-1000,583,-567,222,-1000,314,-246,-577,217,-34,991,256,400,-336,-1000,-535,-1000,-668,-509,776,1000,-221,960,182,331,448,-28,-448,559,-292,613,-363,-1000,-445,498,196,-1000,81,-784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.String:QUFEL0FQOD0=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{220,-1000,205,-763,1000,1000,1000,1000,-229,-535,729,40,-1000,-1000,294,63,-393,1000,43,1000,265,-725,1000,-266,-1000,1000,562,181,578,249,-642,-465,-63,-1000,-628,421,-64,576,-938,736,-1000,800,12,-138,314,-710,590,-865,502,-490,528,-174,-1000,-997,1000,755,471,687,627,1000,-574,-316,-878,514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.String:QVE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{708,-256,-947,1000,1000,863,-717,639,23,-718,1000,1000,249,-308,256,-887,-280,517,-734,-159,-601,565,-719,-464,-1000,-1000,581,1000,234,-82,-98,221,-287,-1000,-1000,343,639,-25,1000,427,1000,-797,70,1000,-182,258,-152,569,-101,692,-42,-334,-524,-1000,-1000,-258,-469,-371,35,1000,-430,-507,-590,-15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{-688,1000,-134,-7,1000,-184,701,-1000,582,835,-950,-786,951,-406,381,976,-302,-137,-745,541,203,642,-690,792,1000,418,1000,-223,475,-487,630,-904,-49,-666,-260,358,-374,-186,-1000,868,-1000,-1000,391,-223,-1000,-881,497,-1000,7,319,-417,-823,708,-318,-319,-43,-266,-155,23,621,592,1000,-659,-144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{-1000,705,934,-567,-355,-1000,142,-1000,710,1000,-1000,627,-635,-888,220,-656,-564,49,691,216,-209,763,-650,725,20,1000,1000,-400,-496,-677,1000,-807,1000,-260,-1000,1000,-248,-1000,476,1000,-633,-1000,13,-1000,-44,-478,693,-84,-655,627,-756,-1000,1000,1000,-7,-370,-921,310,-771,1000,-591,1000,659,744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{-893,371,83,-262,-398,-520,632,1000,-1000,-1000,1000,1000,-1000,301,882,-661,352,-1000,-156,-381,-791,-1000,1000,-226,433,-124,-586,400,-1000,1000,-478,775,627,234,641,-1000,27,-194,213,-1000,-906,900,-924,591,712,653,510,699,-338,-600,-1000,347,-764,57,-312,1000,-150,604,980,1000,-283,329,731,-652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{107,95,496,1000,1000,-243,-1000,-1000,-555,-574,-73,795,-857,262,-199,796,1000,325,-182,-283,-585,-703,371,-388,598,-225,-1000,476,-991,-119,-907,-339,227,-1000,860,-75,-1000,-1000,-78,484,568,-1000,-222,-395,935,1000,-308,-520,-298,-246,-999,-889,1000,-174,705,-270,-78,434,-549,407,27,465,-150,-918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{-132,923,-1000,61,-753,-933,165,-847,1000,-1000,397,-673,209,1000,310,-78,-1000,-100,304,-984,1000,346,-412,971,-221,-460,883,-619,-355,479,-1000,-457,716,-1000,535,-327,-948,57,-859,220,409,-795,-1000,1000,-94,-249,1000,-87,-865,-1000,230,-831,-552,-1000,-13,1000,301,-171,-172,873,-41,608,77,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{-898,-861,147,145,-907,-885,-325,631,-643,583,-747,-932,-185,-167,-453,-361,-528,844,-335,748,-636,-433,-207,-528,-112,-245,-959,-844,269,-484,-46,-395,463,62,241,-642,-66,422,-91,643,-586,314,257,-214,-963,-204,-81,-917,-980,860,518,445,-538,687,405,493,-226,952,-598,-823,-275,244,532,-476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{-390,853,1000,175,-199,-435,771,127,-385,928,1000,279,359,-871,398,1000,55,-495,-1000,330,614,-17,448,1000,-1000,773,-1000,1000,-68,710,1000,1000,443,709,-479,696,80,-1000,1000,-454,-51,-74,460,-400,1000,-299,1000,-1000,873,-603,772,-725,1000,312,44,-130,894,1000,-882,-1000,1000,824,-1000,-664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{-162,-799,-762,-344,-759,-148,972,9,156,-752,-877,-776,819,940,901,295,143,-888,965,60,-960,-394,-737,161,-445,-380,-851,877,-513,-741,298,-220,696,-851,-555,-596,396,339,144,486,389,-156,421,-321,-583,-638,436,-850,601,-99,-902,-60,-744,-595,951,-411,312,-225,572,-872,-975,141,-749,-714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{193,-153,730,-2,860,-827,19,980,360,368,-306,-283,210,647,-651,537,496,305,-453,47,-789,684,-446,692,-375,-649,4,-310,-765,580,886,147,361,191,-238,-549,-433,-128,-724,334,747,-806,718,430,907,-272,-384,563,507,900,188,567,-487,-493,-161,-33,281,201,828,-453,798,648,-258,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read():int",
            new int[]{117,-646,-358,-87,258,563,636,911,-799,724,-894,311,-482,-387,-995,505,951,491,258,783,149,-383,374,-574,724,532,296,879,523,-179,-399,285,743,-888,675,-259,-749,648,-985,241,328,936,-564,489,927,710,-382,498,-489,-304,-813,199,-454,-453,304,-805,-219,-295,604,-813,-354,918,-752,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{90,-124,-63,-1000,314,1000,355,801,674,1000,-1000,-278,-30,-953,-1000,370,1000,997,1000,-1000,942,-215,-1000,268,401,949,-746,123,-870,1000,1000,45,-506,-587,1000,-1000,488,-478,-1000,924,-1000,728,-661,-209,-788,581,1000,-635,-1000,-1000,362,-369,777,1000,757,587,-420,528,-1000,-700,-543,-1000,-386,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{126,-24,-597,-902,843,-740,637,-257,-422,798,99,-432,544,-343,714,-729,-276,-932,397,-698,203,-226,670,-54,-683,-269,651,-722,-941,113,-496,628,591,-254,373,-853,751,267,-605,29,950,608,465,-998,-943,-299,73,826,480,714,774,-322,-27,577,626,572,463,-922,-725,-755,-408,369,-722,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{208,758,-329,920,-298,-39,-285,333,-812,-1000,216,-615,113,-129,-859,-1000,406,960,1000,571,-289,-1000,261,207,1000,621,311,-441,363,125,-1000,93,-1000,-654,-1000,431,4,-324,-1000,-370,609,245,-158,-892,1000,802,-938,406,-447,1000,60,-967,-697,-783,-763,-221,779,317,1000,-210,171,285,947,352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{644,164,1000,-1000,-212,1000,-576,-862,-400,952,-1000,793,-228,-1000,-533,752,1000,-1000,814,48,592,673,-469,-616,803,874,488,-144,-1000,1000,934,803,-842,1000,977,-1000,49,-372,-512,211,-415,-205,-739,278,651,-621,-1000,-30,682,-1000,1000,525,-36,1000,1000,-218,1000,1000,-486,-592,-1000,-202,-1000,-321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-356,626,-1000,-1000,126,-938,1000,-676,-1000,727,-561,-450,46,-316,-828,580,240,700,583,-948,-1000,-1000,784,1000,1000,-78,739,384,-785,507,-48,-515,-993,37,496,-276,-702,387,-568,-407,301,-41,387,44,-181,1000,1000,1000,-676,-870,268,1000,-480,-549,-584,-1000,-93,416,-259,300,2,-1000,464,-133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-341,98,-1000,-1000,728,-933,1000,-795,-1000,1000,-638,-902,-669,-1000,-995,718,-225,855,-71,-1000,-1000,-764,778,1000,1000,-549,1000,-445,-1000,824,846,411,-335,388,753,-1000,17,227,-369,116,615,-766,381,-643,-132,962,1000,595,-987,-1000,-73,1000,-565,-791,-346,-1000,73,1000,-1000,605,-127,-1000,214,310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{1000,-529,-1000,-809,-1000,-580,-207,-1000,-1000,606,523,673,-909,-1000,-687,978,1000,-1000,1000,795,120,1000,-1000,-602,932,1000,-661,-245,-1000,-170,-906,435,-1000,499,1000,-1000,-55,-677,-61,-3,-1000,-681,-848,1000,-406,-906,-1000,453,956,-245,529,-510,416,-324,1000,-1000,1000,513,-882,-118,-575,1000,-734,-667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{-1000,692,-198,380,-1000,-1000,-1000,-1000,-192,846,-1000,1000,1000,-1000,232,682,-475,316,847,864,-61,-29,252,1000,-833,903,-1000,60,-478,-137,-303,-1000,1000,-136,-451,-747,817,-355,-1000,-1000,-496,124,-1000,-266,116,470,-911,674,-1000,1000,-1000,-1000,596,496,-400,-862,-453,377,-782,1000,-465,99,213,-458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{-259,-329,-667,-563,765,-813,182,324,-545,-746,-522,322,720,-522,527,-78,743,-658,-476,426,-754,131,-200,-96,-659,-203,-112,-503,-290,800,842,451,-388,619,974,163,-556,-641,488,-423,484,682,386,322,819,-828,-572,147,432,-880,-27,-594,976,764,47,-405,838,8,-851,-687,-237,795,967,-630}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{512,644,372,679,237,280,902,649,699,-245,787,-991,-582,439,-398,-625,81,-752,635,-598,87,698,-545,-520,626,208,870,-913,-967,387,179,355,-154,723,340,-635,305,819,21,739,-147,137,356,119,-367,-958,683,329,912,903,-470,161,936,947,689,46,788,660,-662,163,479,598,-129,-50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "flush():void",
            new int[]{-992,-796,911,400,142,-374,750,277,-277,218,543,-869,558,-588,-127,-85,894,-184,-763,385,574,-959,960,30,425,-542,-978,238,-649,-769,771,-861,333,561,18,-797,-589,-758,630,-146,568,340,403,-890,434,108,761,-414,-279,68,580,-941,-623,-369,338,238,620,570,-301,399,-830,209,-376,-923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "flush():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "flush():void",
            new int[]{-681,281,-849,-826,756,-927,-894,-511,501,-321,174,976,-273,997,987,798,786,416,-793,-336,-686,65,923,859,-36,-22,-891,-127,-14,-462,392,-388,660,-828,898,-211,428,-573,584,297,-573,282,-874,-900,-865,-187,-832,-487,790,205,531,-405,403,494,-140,-158,244,-617,93,349,959,42,-868,-800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-760,1000,-1000,1000,-302,-219,977,-629,1000,-748,1000,-1000,-233,-529,126,49,-374,-778,-1000,-909,-318,331,1000,-526,400,-67,148,1000,-527,604,-81,1000,-839,-130,-842,801,935,28,-810,169,-1000,-676,1000,-95,-1000,-326,-270,-580,-781,-1000,-532,-566,-596,1000,-378,-986,570,-316,-601,-230,-997,998,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{288,-346,-703,-584,210,-104,-965,-1000,-1000,126,1000,381,-558,-802,1000,-1000,-329,-951,47,-1000,253,517,-280,-1000,400,1000,1000,-151,639,459,55,400,-967,227,423,1000,1000,208,-51,468,49,-477,18,-581,-5,-498,-482,-206,228,-56,724,1000,-1000,1000,-400,5,-243,771,-1000,-1000,514,-1000,462,13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{556,537,753,-383,-716,1000,633,804,902,181,-700,-857,-849,-1000,-1000,-865,1000,-686,1000,1000,676,964,461,97,-1000,607,-386,-30,892,1000,-374,-778,798,1000,1000,1000,606,-1000,355,-1000,571,-1000,-915,-508,943,404,-1000,1000,630,1000,-1000,-956,633,-1000,1000,-953,1000,-1000,-153,1000,-355,-40,-935,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{385,353,242,462,-625,-646,246,551,124,-495,879,-994,239,827,867,372,92,-669,194,-729,-884,623,850,-302,411,201,424,379,210,368,-300,-91,-625,-757,-947,-830,-771,-49,-374,456,-234,-263,21,668,556,-479,-455,-42,-480,164,952,-693,127,619,-983,621,-417,-968,343,-823,-924,861,297,495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{67,-475,572,-538,-378,477,-439,-726,903,-829,842,-977,-896,-441,-114,937,191,-918,654,992,346,-394,-494,-911,-346,-519,66,-236,-315,-514,794,-507,215,-20,-359,-560,506,189,376,-592,-739,-779,904,-289,693,157,-784,691,-65,-646,-507,688,-516,949,-682,422,-696,-506,959,938,769,-368,-761,-456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-771,620,427,1000,-64,-1000,501,-606,418,-288,376,-1000,236,-1000,787,1000,-913,385,663,-1000,-1000,504,1000,564,1000,-348,15,135,-680,324,-1000,-142,213,-1000,-1000,-570,-1000,171,-1000,1000,-548,799,1000,-311,323,-391,-210,-218,-594,-115,1000,-400,77,-315,-915,-1000,731,1000,25,-829,-1000,1000,1000,815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{940,692,60,269,-232,839,-931,-355,-845,553,778,570,-788,-183,89,993,691,643,-382,-980,-506,-125,-957,-805,-683,-163,230,116,-780,967,-433,-667,-450,191,539,-720,769,-658,783,-70,-521,-339,594,-230,221,-904,194,742,444,969,926,859,-506,-613,995,370,391,754,-632,-485,-254,-323,13,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-1000,560,870,8,414,-483,-439,258,1000,-829,-506,-697,131,-1000,-1000,-1000,-44,-1000,1000,-264,223,423,-494,764,581,428,-1000,-1000,1000,761,-781,1000,-340,83,906,1000,-488,-490,-450,-1000,1000,526,-1000,-450,693,148,-806,-1000,-499,647,-1000,-1000,-148,-480,-572,422,371,-619,-312,-897,769,799,214,350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{973,524,809,801,162,328,37,-131,-1000,215,-53,1000,-587,375,-910,754,707,-162,155,896,1,576,-158,-1000,-471,613,-280,480,647,-461,83,-1000,-465,-660,-170,-117,406,851,833,-507,108,346,293,-353,525,-64,554,290,1000,167,1000,1000,812,-80,1000,-1000,-207,1000,32,-207,-1000,11,393,372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{1000,-1000,41,-647,570,-1000,-201,152,-838,-27,-1000,839,-757,-1000,957,-939,1000,-808,344,518,319,527,-1000,-222,-835,689,1000,1000,196,1000,848,1000,477,1000,1000,256,1000,467,885,-864,-382,879,759,-1000,-670,147,-1000,1000,200,163,641,318,-824,1000,-1000,-770,-316,-1000,-219,-255,798,727,359,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{446,612,-113,393,-799,439,768,683,263,-496,117,-189,290,962,617,-261,-4,56,-416,-526,-428,-314,910,-620,-202,24,-788,825,-946,980,718,-723,-863,-262,567,159,-963,-976,25,-992,-356,-716,-539,776,-273,-453,-178,-528,480,-639,818,769,408,-73,80,696,422,307,128,-936,607,405,-850,904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{-17,605,-271,968,-1000,-463,371,1000,-412,-691,464,902,-506,1000,553,-1000,282,-113,-113,-835,-300,-1000,499,-1000,843,850,-1000,717,317,1000,1000,-1000,-50,-1000,1000,-382,-1000,-732,-154,-810,-663,514,-1000,1000,-267,-963,389,-958,91,662,792,1000,369,-762,-209,861,-1000,-1000,551,-1000,1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{1000,704,437,-406,-932,-195,1000,424,231,200,12,-1000,1000,756,647,646,-296,1000,602,-169,-478,256,570,390,142,423,1000,-24,-901,662,316,-403,-847,427,686,-531,100,-393,410,732,1000,-372,1000,-1000,-971,-289,-374,-456,729,-566,-708,-1000,-840,1000,-450,490,1000,1000,877,168,236,-821,-185,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{600,-913,126,-881,1000,-729,-1000,284,-118,651,-551,1000,-800,-1000,261,-843,469,-1000,-1000,334,-368,299,-1000,-854,-910,123,-270,1000,1000,996,57,827,1000,695,-135,760,1000,101,212,-1000,-811,888,147,-561,356,340,-253,554,267,378,1000,1000,-318,-10,-1000,-608,-547,-1000,-521,-850,-19,1000,-570,745}));
    }
}
