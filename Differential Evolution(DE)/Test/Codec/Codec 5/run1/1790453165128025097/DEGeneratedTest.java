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
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{1000,-1000,1000,-885,1000,946,-276,-1000,-696,1000,640,678,947,-1000,278,557,-325,-997,-129,354,1000,-1000,1000,971,-1000,1000,-1000,38,1000,-1000,309,54,807,1000,-1000,-495,-1000,328,-1000,-1000,1000,-563,443,-901,695,-624,-277,-480,545,1000,-397,-761,1000,-949,-884,-266,-1000,-1000,555,1000,349,-398,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{-179,-676,874,-856,-1000,151,-573,-1000,1000,-1000,-140,-816,-804,1000,-1000,-1000,278,326,-1000,1000,580,1000,1000,1000,-1000,939,-882,557,776,-416,1000,1000,635,-1000,1000,144,1000,-1000,382,-996,-1000,-1000,-447,-1000,-1000,-1000,-570,103,-1000,649,-942,-1000,-591,1000,-409,-232,-184,1000,138,-465,-1000,-1000,1000,333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{1000,1000,-329,932,-1000,436,611,850,-1000,-377,1000,-340,462,704,1000,-395,1000,891,-1000,-498,-1000,-390,-1000,376,-1000,-1000,-1000,-650,244,170,-516,-573,-534,-1000,1000,-1000,26,-1000,-931,488,-1000,166,-81,-708,8,130,328,281,-1000,661,-909,808,-1000,1000,-117,-1000,415,-216,610,-1000,270,230,-959,-885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:MTE5", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{506,-19,880,374,238,1000,826,-445,1000,55,-121,-323,308,-133,-166,-1000,855,791,-1000,216,-271,-179,9,690,-1000,-715,-221,-868,598,-1000,42,-29,-679,710,115,257,-741,34,42,-1000,1000,-1000,-62,147,910,38,-385,-416,1000,400,-1000,156,227,100,-333,-43,-694,-1000,-255,381,428,-108,-197,288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{819,1000,-1000,-271,1000,1000,-386,887,-466,-1000,967,-748,131,-621,944,1000,-13,982,-1000,-455,-1000,-313,-416,-597,518,-1000,1000,735,782,1000,-858,-9,-1000,-577,1000,886,251,-130,88,-48,-631,845,836,238,1000,1000,1000,243,411,-1000,1000,1000,-859,320,1000,-427,1000,-552,-462,-64,1000,-1000,-257,-195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTk1:19:java.lang.Byte:LTcy:19:java.lang.Byte:LTM0:19:java.lang.Byte:MTE0:19:java.lang.Byte:LTM0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{-268,-95,-983,-317,-511,728,-161,-896,-118,-376,-117,990,610,-959,-420,-134,-535,348,923,-440,-900,-366,171,-518,199,838,-475,903,438,-527,930,-263,-646,-989,-426,-995,-159,-914,-128,176,-649,44,777,-127,104,119,110,-949,-415,-486,-728,-309,469,6,156,-709,710,-639,-590,7,10,790,-743,-860}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.DecoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{-610,461,-870,379,-429,441,-805,-903,-299,463,-651,-977,-702,385,650,486,-859,335,167,-311,-135,360,-295,364,-691,-476,-984,950,814,-963,456,101,-585,779,470,39,-553,-290,514,-757,-538,-14,59,-728,565,393,-801,-842,279,666,4,711,-138,654,289,-670,654,908,737,-504,461,231,399,-58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTk1:19:java.lang.Byte:LTcy:19:java.lang.Byte:LTM0:19:java.lang.Byte:MTE0:19:java.lang.Byte:LTM1", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{172,838,216,308,1000,-586,619,469,-151,304,-595,171,909,1000,-86,1000,1000,1000,1000,54,-139,127,736,738,-297,-279,-562,37,1000,-797,-857,-947,-1000,-464,-1000,-361,-1000,-749,-738,650,-361,-1000,-227,249,273,1000,-938,-30,14,460,250,-565,632,619,-947,817,-926,-297,-385,-1000,84,-1000,-948,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTcw:19:java.lang.Byte:MTQ=:23:java.lang.Byte:LTEyMQ==:19:java.lang.Byte:MTU=:19:java.lang.Byte:LTc=:19:java.lang.Byte:LTIx", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{886,-105,1000,686,913,-753,-214,-360,-1000,291,592,618,-409,-110,-1000,-279,1000,-1000,-218,71,-971,1000,910,-659,-209,-676,-544,36,-1000,406,796,-341,909,99,-1000,-975,-269,1000,-832,-340,1000,-1000,-44,985,-865,1000,-1000,-250,-1000,580,-102,1000,1000,-38,230,766,1000,614,-821,462,458,290,1000,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("ARRAY:[B:15:19:java.lang.Byte:LTIx:19:java.lang.Byte:LTg1:23:java.lang.Byte:LTExOQ==:19:java.lang.Byte:MTIz:19:java.lang.Byte:LTY2:23:java.lang.Byte:LTExNA==:19:java.lang.Byte:Mzk=:19:java.lang.Byte:LTg1:19:java.lang.Byte:MTIy:19:java.lang.Byte:LTE1:19:java.lang.Byte:MTEx:19:java.lang.Byte:LTM3:19:java.lang.Byte:MTk=:19:java.lang.Byte:NjI=:19:java.lang.Byte:LTYw", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{989,374,-400,-225,-913,-834,83,-400,-398,379,-673,92,122,-961,39,384,-558,-627,-1000,740,-428,1000,1000,-893,-97,866,-916,-136,277,-518,400,1000,810,1000,363,555,-1000,-557,845,679,177,-490,-102,331,30,800,-1000,-94,-123,-400,-911,-937,585,1000,-4,-87,-12,1000,631,219,-65,272,-32,-641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{-314,-154,-906,-867,-111,652,393,960,-774,-616,-882,-954,-70,692,532,-849,-644,663,501,-36,486,336,915,876,-129,70,620,984,673,-373,272,415,562,-525,982,315,-622,-889,11,-377,-241,-19,340,-920,-842,-23,505,-140,117,-571,714,-356,-989,987,993,-962,30,-117,856,-223,658,673,-155,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:LTI5:19:java.lang.Byte:LTk4:19:java.lang.Byte:LTY5", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{500,1000,643,517,-456,752,77,1000,56,-1000,-198,182,-253,1000,-85,-586,-859,-869,-265,948,-214,64,20,-343,-338,-154,-48,-1000,529,-333,-759,235,1000,-262,-42,343,418,296,1000,-28,613,-189,-166,-191,-865,275,269,1000,515,-45,1000,-266,639,-237,1000,321,-165,490,477,-1000,-150,595,-1000,407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("ARRAY:[B:17:19:java.lang.Byte:LTIx:19:java.lang.Byte:MTE2:19:java.lang.Byte:LTcw:19:java.lang.Byte:LTIy:19:java.lang.Byte:OTA=:19:java.lang.Byte:MTI3:19:java.lang.Byte:LTI=:19:java.lang.Byte:LTQy:19:java.lang.Byte:LTk2:23:java.lang.Byte:LTEwNw==:19:java.lang.Byte:LTI0:19:java.lang.Byte:MjQ=:19:java.lang.Byte:MTI1:19:java.lang.Byte:LTIy:19:java.lang.Byte:LTUx:19:java.lang.Byte:MzQ=:19:java.lang.Byte:ODA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{84,979,-601,-995,1000,-486,-444,1000,-474,798,-535,325,544,-324,-332,-113,516,1000,124,1000,-707,480,1000,1000,447,-46,-1000,-785,-752,720,87,305,-696,-55,-143,-153,-198,369,-683,617,-950,92,960,422,424,1000,-666,-1000,423,380,-1000,696,-927,87,888,-659,681,995,282,276,-446,-692,481,-64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{555,193,97,373,587,-476,-1000,65,-849,-42,-942,-48,-1000,488,-568,-168,-353,-1000,181,232,712,825,956,449,-570,312,-245,136,417,-21,-417,1000,279,-464,-85,-325,-890,352,1000,-1000,-283,610,27,-709,-830,318,987,527,-1000,-989,-1000,356,975,-561,709,-46,717,968,1000,742,-492,129,-60,-2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{707,-738,67,504,-152,618,958,-1000,-721,-72,-769,-1000,90,329,-436,-432,-492,-1000,-458,1000,167,300,1000,907,-427,-995,-1000,702,-84,-145,-260,-160,504,-285,1000,967,-797,141,-593,1000,-220,117,-622,-588,601,-236,-159,817,1000,-764,277,1000,975,-279,-768,-878,756,-61,-1000,-50,-200,101,24,-841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:MTEw", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{-794,981,58,402,-264,244,-1000,-151,-553,-1000,-734,-354,-1000,-420,547,-710,-180,-1000,1000,232,167,1000,1000,777,-850,1000,1000,-436,767,918,787,1000,1000,-248,28,-335,-1000,471,726,-616,128,279,1000,-1000,-744,329,572,-360,-415,-683,-110,-337,806,399,709,430,1000,-295,1000,1000,1000,799,-1000,508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{777,393,-351,1000,1000,126,-505,1000,-1000,-867,-1000,-166,52,550,-1000,-814,1000,1000,294,1000,-318,119,1000,161,-150,704,-628,157,955,-887,-1000,-295,1000,-871,555,370,-271,-747,-397,-1000,-915,-385,437,509,1000,-440,1000,261,1000,-1000,464,-33,-419,-1000,-1000,1000,-1000,428,-510,427,-585,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTI5:19:java.lang.Byte:MTI2:19:java.lang.Byte:NTQ=:19:java.lang.Byte:LTEz", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{133,-434,380,285,944,447,-986,627,-383,100,88,919,684,22,69,-384,-468,782,483,-406,-827,-738,555,778,238,214,-287,962,-706,-532,-77,118,365,242,945,-751,-432,-39,-913,-501,-841,757,-206,598,949,366,501,-19,78,-423,572,938,384,-486,-112,42,300,-885,621,707,-941,-64,134,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{209,909,-867,869,951,-632,856,-844,-830,-326,-171,-421,57,-414,357,601,-736,-834,621,-512,-275,989,284,-873,-647,-603,259,-632,-13,-618,-161,-535,-302,-211,-449,-90,-371,-479,-471,627,906,-867,-971,-146,838,-185,727,291,629,-862,-81,147,-885,131,-626,552,674,-741,-298,-543,375,-284,350,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:LTMz:19:java.lang.Byte:OTQ=:19:java.lang.Byte:NTY=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{-971,-31,352,-551,828,640,-672,-945,750,204,366,-416,-17,781,467,873,-599,89,970,-237,879,-791,-40,-950,745,-150,-379,224,-716,-246,299,-414,-12,-986,191,-869,-726,17,-964,211,-225,-499,-37,-633,-11,755,-260,-13,61,-580,57,467,-215,-493,-788,-593,524,-721,567,-955,-309,-547,-500,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTMz:19:java.lang.Byte:MTEx:19:java.lang.Byte:OTQ=:19:java.lang.Byte:LTE3:19:java.lang.Byte:Nzk=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{934,329,-148,709,820,243,-805,-290,908,-370,-570,803,-120,282,-399,55,-37,-969,747,-410,-354,318,873,-632,-897,168,222,673,333,788,-320,484,-270,-903,297,-314,-97,488,-643,671,-105,851,213,-684,646,-529,-796,528,-448,806,-663,-395,260,321,-825,-485,-539,604,347,855,-618,772,490,715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:LTU=:23:java.lang.Byte:LTExNQ==:19:java.lang.Byte:MTE3", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{-822,411,153,486,65,-800,-913,-379,-670,532,-157,674,-134,300,-1000,457,-421,-712,-196,55,-1000,397,-479,-809,-8,-695,932,-780,-586,519,-559,357,-512,415,100,555,192,-906,599,-998,-133,-205,322,-590,-44,1000,-1000,-302,-246,-104,506,-704,-334,937,239,94,-603,-630,-292,-97,143,-392,-103,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{531,-338,575,-72,1000,1000,250,800,-282,-1000,1000,1000,1000,1000,-735,-321,269,918,-411,133,303,-954,-659,1000,528,1000,-1000,1000,1000,-818,358,-403,1000,311,800,-772,-1000,-445,-117,-866,1000,-1000,310,870,-176,-664,321,-14,-535,-241,-847,-571,-77,1000,538,-116,-1000,410,70,-714,-787,-836,248,107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{-1000,-901,395,-942,-1000,481,-480,118,-489,1000,-1000,-384,-17,-1000,1000,282,-679,164,1000,1000,-1000,-49,496,-1000,-217,-121,-190,10,951,131,331,789,-419,498,92,-151,1000,-853,-15,382,-557,416,686,-527,443,798,-695,-715,758,272,378,166,-1000,-1000,-102,1000,858,1000,856,612,-441,807,343,-863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MTE3", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{1000,1000,-122,817,1000,-314,-22,860,430,106,-164,890,1000,1000,258,62,843,1000,-382,730,1000,332,-1000,-158,1000,941,-343,1000,-214,-72,-877,300,1000,1000,1000,-1000,-438,-1000,-1000,-562,442,-1000,-1000,1000,302,-916,-914,1000,312,-636,239,745,-670,1000,606,1000,-280,-1000,958,-1000,-427,-197,32,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-347,-873,610,308,-154,-693,-153,-1000,-587,273,-1000,-406,-51,329,-14,720,-777,-882,324,1000,-825,20,-1000,-878,-1000,-1000,-762,1000,229,-1000,-261,1000,-140,508,1000,827,233,291,1000,219,591,1000,82,203,-1000,590,-40,-1000,-24,-572,202,-754,630,416,636,1000,31,-435,1000,-999,-1000,-284,290,69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjY=:19:java.lang.Byte:NjY=:19:java.lang.Byte:MTE4:19:java.lang.Byte:NTY=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-848,-236,-13,-114,1000,-467,232,-318,844,582,-1000,413,574,-250,405,-79,160,384,-715,520,-372,421,66,-170,97,-1000,-1000,556,485,-110,449,350,712,-217,481,413,-29,-230,763,-497,873,-502,305,-532,-476,813,448,-565,-1000,280,-552,569,-173,-562,300,568,-789,763,-867,48,-243,-435,112,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NzI=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-490,773,443,-515,843,900,304,260,939,-506,-350,-199,233,168,-306,-814,-227,-282,351,-901,676,377,312,-571,-992,57,-441,579,768,-901,155,320,816,-931,149,28,763,630,-657,-317,127,-367,780,185,391,-762,-129,183,980,-926,306,883,-368,251,80,131,-830,-633,300,421,232,-424,-225,419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NzA=:19:java.lang.Byte:MTA3:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{88,231,-662,-540,99,253,-304,-666,-587,1000,-218,-620,47,329,-993,614,-528,27,-126,114,-846,20,-939,-158,-134,-956,674,1000,588,459,-349,949,-998,512,651,827,936,41,1000,1000,1000,748,-525,756,159,590,233,528,529,959,202,-1000,-607,-25,-11,304,273,997,488,-1000,1000,86,261,-974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NzM=:19:java.lang.Byte:MTAy:19:java.lang.Byte:NTY=:19:java.lang.Byte:NzM=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-1000,898,-411,-863,1000,-128,-503,-1000,48,-1000,5,459,1000,785,-608,289,-433,-92,-777,83,-686,105,-312,-341,-206,-450,-499,383,1000,682,-625,259,933,342,-1000,-981,-515,-604,191,-698,1000,61,-749,482,212,-117,331,54,-981,-2,962,504,-383,-1000,-232,316,-1000,942,653,-1000,-432,814,-128,-652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-240,-26,443,409,969,-295,-1000,-1000,639,547,-1000,-1000,-346,1000,-1000,1000,-1000,-127,280,912,-1000,459,-1000,1000,-1000,-692,1000,1000,-236,-36,-1000,320,-487,-278,1000,1000,699,15,-971,1000,21,321,-1000,-439,1000,367,-609,1000,1000,1000,1000,-1000,-804,795,-88,1000,-547,335,-171,-639,327,-72,1000,-938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{-478,-461,314,-26,123,-500,-942,-400,-1000,744,1000,-1000,-110,1000,1000,293,1000,-617,655,-804,-455,-157,-1000,-287,674,1000,369,456,261,1000,333,751,216,-753,911,1000,793,1000,-269,282,-493,694,-1000,1000,-1000,646,-578,-848,-465,1000,-436,-981,-800,730,321,273,-1000,-1000,47,568,1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{-593,-724,-255,657,966,-715,-504,38,-283,915,-506,-676,467,-257,989,-204,752,130,-132,789,-350,-53,-640,-509,91,358,-545,941,-628,195,-265,26,594,627,658,946,892,-529,714,-202,464,-202,-656,771,-334,243,29,-268,-260,855,515,935,72,228,-186,-974,-646,-820,295,-429,-546,-707,-18,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:MTIy:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{873,207,-481,960,-908,772,765,-699,-40,833,-40,-93,313,156,-419,576,791,663,-934,712,-897,-909,-480,-117,-240,-967,58,-489,-444,-846,-587,373,-120,-114,775,-126,350,494,724,-594,-843,35,604,-433,47,-536,709,-549,-79,-143,255,-185,637,-373,-738,502,-171,-580,199,208,279,-610,-480,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:OTc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{-284,617,-716,-419,-774,-389,238,655,548,-29,-792,799,466,330,-427,-544,4,203,-154,-373,-362,170,-602,-419,-879,-571,-565,649,-920,411,684,625,-639,-396,-330,-380,453,-626,283,-423,-705,-813,888,59,-769,324,-791,-566,-7,-9,-166,107,-830,510,354,-922,-169,-789,-155,771,-257,567,-285,-195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njk=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{359,-94,-664,-720,-221,-944,-200,771,666,-360,-227,423,22,-354,11,-574,-382,-998,-32,-959,995,554,998,-451,1,189,-44,931,775,-211,-10,-660,-46,-768,632,-261,211,242,-783,436,203,-492,131,-327,-508,-771,-586,-83,74,-10,-149,31,777,-297,-171,-857,975,-748,-37,680,-173,421,800,787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:OTc=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{-747,-152,-469,639,152,-244,158,120,-930,560,-555,663,511,902,546,244,-116,-132,617,978,-13,-763,154,-928,-707,-302,-209,731,-244,98,6,521,293,290,-126,-291,-409,-788,-226,-282,430,460,-215,-29,-685,-195,414,134,-90,855,-764,51,469,374,47,499,293,-334,221,304,70,826,-897,340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{-626,-726,405,-274,-953,755,-843,893,-958,-715,795,-742,367,936,-636,541,525,-853,429,-138,-968,-911,-903,442,330,-28,42,-973,425,690,-952,373,993,734,-684,-828,-808,333,-472,384,973,-560,512,111,275,349,-778,853,183,-507,727,-714,851,279,890,-467,799,699,536,323,-955,896,12,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDg=:19:java.lang.Byte:NzY=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{899,87,569,669,-14,-282,783,516,294,-353,254,708,-769,-91,-659,-806,-93,-406,423,-884,-940,-225,-867,239,134,-51,297,774,1000,819,-230,-174,225,-281,61,-245,-722,92,403,-670,619,-992,762,698,-785,923,160,-56,-270,-346,653,254,-682,-196,-672,594,823,-196,-273,-830,967,-854,-690,-39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:ODg=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njg=:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{861,958,-446,-32,561,-289,-982,1000,-442,1000,-324,48,285,-543,-514,369,-867,723,-908,-200,-1000,-750,-1000,-764,-1000,291,-847,-528,637,-822,-1000,-373,1000,26,-423,-300,-349,1000,-755,258,-638,262,808,360,-365,35,-874,1000,284,925,276,-79,319,-111,-111,-22,459,1000,-147,286,-1000,-1000,400,-688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{-973,-429,-332,389,291,111,736,-891,-413,210,-468,-699,-715,-225,821,732,960,826,-317,126,-376,-31,402,920,732,94,-734,-567,185,388,643,751,-135,386,-694,-76,738,236,-30,-812,474,-409,-725,-206,684,-341,39,-169,382,772,483,889,-910,-425,384,824,-889,-583,-402,-982,-63,881,257,523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("ARRAY:[B:9:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{623,-58,-831,43,34,1000,1000,807,-8,161,-777,-708,1000,-534,806,-950,1000,-1000,867,403,796,1000,601,1000,1000,196,387,539,-960,-529,-945,-155,300,314,179,-985,1000,-1000,835,-440,1000,44,-439,1000,251,-1000,66,-855,-136,285,-336,510,-1000,-1000,-653,756,269,-1000,-333,-1000,1000,-301,-1000,-548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NTE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{-8,450,-213,581,-664,-27,204,-364,334,912,-228,404,-630,-833,-910,-338,-546,826,816,-380,-721,257,430,363,753,82,-816,763,-921,-320,-102,257,762,555,958,-809,-578,337,813,-489,-802,429,541,513,704,-608,-349,99,975,-924,190,499,662,-857,415,808,858,190,-143,828,360,-22,396,-310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{-422,792,612,-284,-64,801,794,-856,-592,164,-295,682,-196,-470,514,-965,-615,-350,-523,-304,-140,-501,-908,-697,717,-58,-390,311,351,715,416,987,-347,-852,477,6,-663,-533,990,36,-894,-185,-351,607,958,159,545,-694,432,181,605,-134,386,-92,681,73,29,-628,949,-422,-638,901,93,-298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NzI=:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{117,-115,-919,953,109,-1000,-1000,-1000,-1000,736,-448,754,832,-1000,-234,391,-1000,-1000,-1000,-1000,-580,-1000,289,-761,753,-1000,311,1000,-821,-790,-360,274,1000,301,1000,386,-1000,4,289,25,-1000,-406,-1000,1000,-187,280,-699,238,-856,-1000,75,-65,-442,636,-1000,-174,1000,-233,-401,1000,1000,816,-33,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:Nzk=:19:java.lang.Byte:MTAz:19:java.lang.Byte:Njk=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{749,-53,520,585,573,-915,62,586,-302,926,-947,537,-923,224,-81,-860,-221,-61,982,656,428,443,-945,69,74,-717,-745,-76,987,-590,593,-870,605,-256,143,699,186,-40,-533,-777,-423,-537,373,-252,984,657,-970,-390,-416,823,48,-188,-568,-807,-834,549,-791,112,905,596,55,-373,-284,482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("ARRAY:[B:9:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjY=:19:java.lang.Byte:MTA0:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{539,-295,524,-876,808,1000,-119,647,-253,1000,-688,878,748,935,-109,-88,1000,1000,-741,-300,1000,-1000,827,271,1000,-1000,-707,-497,-110,-1000,1000,-689,-295,311,-605,-1000,-840,508,408,226,998,-168,-75,1000,1000,-79,1000,-1000,-127,49,-926,-11,-343,670,-588,-1000,200,-1000,-668,537,-416,-1000,136,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{992,-783,-16,554,248,982,-461,-605,-829,-885,-709,-636,-632,-813,32,498,-817,-588,504,-532,739,901,427,849,-663,686,316,-435,-756,202,686,56,-374,904,461,-567,-292,-345,314,696,-888,-346,-981,-32,-227,-218,-89,571,-171,464,46,827,-552,-584,780,-551,-334,-45,-883,545,45,-29,418,-589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{-8,702,-535,458,951,-777,-440,-756,-895,-692,-632,-221,-532,-325,-501,828,-925,-939,-809,982,-548,-336,-641,-784,106,-594,-209,144,-35,169,-984,971,-519,78,624,831,-589,953,-326,42,-835,-908,-768,630,58,732,49,447,802,706,-64,-862,124,108,-534,-695,558,86,-688,316,107,288,-268,-647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{1000,-208,-1000,591,695,318,-295,473,-861,1000,-707,296,-25,-105,599,-185,-189,348,976,985,1000,780,161,1000,-206,-1000,-1000,261,1000,-1000,1000,295,1000,-407,373,273,-569,-186,-198,-256,716,-1000,548,-891,485,724,76,-1000,-1000,994,-830,228,-693,-696,-772,150,-1000,-177,465,-265,576,-829,93,2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{-387,102,204,586,-934,-963,451,-918,134,454,-463,-949,293,-972,-892,-357,-81,988,417,-976,-367,-569,-437,778,301,-128,18,-355,-919,-578,770,-602,-726,-43,49,-858,-809,120,319,351,-317,-926,-46,-461,-428,543,895,643,815,876,-569,900,903,-377,-766,285,748,-600,-225,-569,95,-257,-656,351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTc=:19:java.lang.Byte:ODU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{-218,-340,12,690,771,-684,-793,-536,-411,629,824,-658,702,-261,-925,-433,-931,283,-905,34,-428,48,-95,-492,991,814,-464,-140,-623,-285,-917,-953,13,-582,94,-942,594,890,-605,-919,737,629,-758,722,477,-188,498,-700,-972,54,441,919,743,-827,762,-653,-28,720,-691,-463,278,868,-742,61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{65,-1000,-524,-863,1000,-423,665,40,-575,-242,1000,0,427,750,1000,-1000,-939,-1000,1000,0,-360,517,-226,-915,694,-297,-485,562,560,1000,-962,247,0,0,1000,0,1000,-827,-308,-753,747,1000,-435,1000,-145,-782,-814,-554,-296,-1000,878,-1000,0,1000,325,-389,-402,-267,72,1000,204,292,684,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.String:b3dILw0K", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{-723,-933,370,247,481,424,-415,924,-221,-911,-744,-581,-466,205,-142,-667,291,-267,-47,240,107,-970,527,914,-760,-683,-920,-560,-358,595,313,-139,-119,761,519,456,-68,896,554,276,617,6,-286,143,-635,852,-305,-63,-545,-63,402,-263,-76,-813,-631,126,-69,442,217,799,498,445,761,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.String:L3dFQi93PT0NCg==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{868,316,338,-243,1,-369,-299,-748,242,-858,705,-926,83,-753,369,831,288,-822,-416,-523,-656,-869,252,-669,494,-171,-12,-484,738,-203,-279,-322,422,825,741,-692,814,-698,564,513,402,-580,-613,637,573,-383,801,-182,-200,21,-30,-773,482,182,-466,533,196,892,114,-44,-737,-462,506,-832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.String:QVFBQi93RT0NCg==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{323,-268,109,-574,570,-471,445,656,464,570,-971,459,863,642,-593,-53,987,-481,-171,-28,11,-984,-579,824,309,-131,801,779,818,309,-497,-62,-812,-649,-163,230,187,-950,-379,981,-837,291,611,45,-721,27,-643,-900,-295,318,774,454,983,986,-277,-553,-610,278,564,-612,252,-471,818,834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:Njg=:19:java.lang.Byte:OTU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{-411,-75,-479,753,210,446,891,-854,119,-922,-548,-437,934,-738,77,-957,-442,-598,-501,652,160,654,504,480,252,806,76,266,15,388,213,-346,-851,-7,425,385,-595,-12,962,-709,-275,-724,-266,-98,418,595,-432,-373,-810,-58,-45,-945,984,-853,765,-273,-421,-634,559,886,-321,-675,-968,175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODk=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{-61,-998,417,827,427,434,141,611,971,-989,-502,682,-382,453,137,-908,514,-23,520,-410,-633,837,-222,960,350,295,718,513,-917,-667,-305,-506,414,326,294,-610,650,81,879,-238,-391,110,905,90,424,-155,-378,346,337,-403,-703,-454,-44,518,-604,338,-642,536,309,875,-735,323,-842,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{352,-605,-111,978,209,-59,706,333,-353,-21,197,374,258,-771,-1000,-383,-1000,450,-995,341,279,1000,-379,-458,390,908,-1000,1000,-395,-133,-122,-335,-220,516,-66,773,-158,166,-561,16,-436,-637,409,1000,995,439,-1000,-995,395,-169,371,-467,-110,-25,-450,-185,70,-933,1000,495,576,-1000,-1000,896}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.String:X3dBQQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{615,309,-640,-380,270,245,316,-343,594,348,572,603,-29,22,651,-403,-154,-897,787,-797,-584,-679,379,-580,-482,-339,48,-22,892,-661,-838,331,-589,255,-427,682,-80,988,942,338,850,652,41,-228,-87,-702,130,-139,881,-113,705,-49,692,-638,708,-367,958,845,-374,-591,290,-443,816,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.String:d2dBQl93QQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{-355,-624,58,-203,-66,2,-539,-224,449,752,379,-645,49,-584,-76,-752,-194,521,491,616,-297,842,729,991,-402,-995,632,978,-771,242,667,-16,449,494,-185,151,924,-716,957,-363,51,17,126,-527,-996,-275,778,294,-502,-351,-483,451,108,-486,-595,-773,812,-142,-572,180,-224,382,-482,7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.String:QVBfX0x3", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{598,-894,-140,982,245,-188,-763,475,-374,8,494,769,635,78,-500,182,499,-292,-155,-849,440,849,-680,-400,-286,31,176,-602,-92,204,-801,-713,814,-569,614,574,-754,982,369,-123,-339,608,439,248,646,137,451,123,-43,-83,-858,711,-901,67,-26,678,214,58,-461,288,-555,-329,312,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njk=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{-366,899,-215,-577,518,1000,1000,-456,-794,398,1000,481,530,1000,334,-855,-910,1000,65,1000,-427,-664,-384,1000,651,-384,1000,-708,-1000,383,705,-1000,-832,-187,-823,-978,-344,-1000,-91,1000,1000,-1000,-88,-702,604,136,-83,194,198,-250,224,694,-499,-400,764,411,758,-418,184,-429,538,16,-150,842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTAy:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{-1000,670,-1000,-731,-98,651,870,-223,-928,-66,1000,-1000,-1000,-1000,-431,-935,-1000,-446,-525,-1000,1000,994,-230,41,-1000,476,-12,-1000,-1000,450,1000,557,-174,1000,-392,731,832,268,811,1000,119,-703,-412,731,1000,193,512,251,848,-536,-1000,408,-748,1000,1000,270,1000,-1000,-1000,219,1000,-70,-257,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{51,-1000,522,856,-810,297,377,776,-10,100,0,219,118,-563,499,168,-176,-474,734,-1000,217,-479,-179,-487,-1000,896,-514,821,237,1000,791,-377,387,156,-1000,409,1000,481,-1000,121,-1000,57,-550,654,-776,12,-835,876,939,432,407,-789,1000,1000,213,600,906,318,76,-347,1000,-273,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NTQ=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NTM=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{-400,281,1000,-313,-2,-847,-918,-257,413,-489,-1000,274,702,211,-101,842,1000,359,1000,497,492,-269,-81,-1000,45,-122,-683,1000,1000,-44,-1000,387,773,-421,684,480,506,-345,-202,-1000,-450,1000,-89,282,-1000,-54,-356,-1000,523,241,-47,-790,905,-431,-1000,1000,-1000,1000,1000,394,349,-32,-98,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.String:QUFELw0K", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{1000,-753,61,767,-337,-969,-1000,499,-815,1000,1000,-607,514,-262,431,-680,-766,83,196,960,1000,753,-600,479,-752,-355,-452,645,-235,-531,-467,1000,111,1000,674,-874,118,1000,625,-1000,-1000,-1000,1000,-1000,-483,896,-127,605,199,-475,-276,764,57,-943,777,134,1000,-213,316,255,-629,-466,-597,730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.String:QVBjQW5BQQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{1000,-231,-1000,38,-763,204,-1000,465,-972,-483,-864,-1000,1000,-1000,349,-1000,-720,-961,1000,1000,-265,107,-1000,1000,-1000,154,-1000,-440,848,-210,894,-1000,513,42,828,-1000,-1000,1000,81,558,-638,-847,1000,-427,-1000,1000,-489,506,340,237,569,1000,-1000,-186,1000,189,1000,-1000,-752,1000,923,388,-720,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.String:QVAvLy8vOD0=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{1000,273,-593,505,340,-293,117,-1000,1000,287,1000,-126,935,-1000,-799,1000,1000,-341,-364,-538,-1000,-83,-1000,787,-1000,-1000,595,431,615,-604,1000,506,-1000,1000,-468,1000,207,634,1000,674,1000,-739,1000,121,-832,-1000,1000,376,-998,-1000,93,-1000,448,-636,296,-752,-1000,-334,744,-923,469,-1000,729,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:X3dFQQ0K", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{1000,-839,-1000,-189,825,346,-604,-706,-923,-101,1000,-1000,377,490,90,-1000,-843,512,1000,1000,-420,1000,-1000,-1000,-1000,469,-1000,1000,215,258,780,-290,-478,1000,368,-1000,-1000,534,-506,-603,-1000,-982,283,-699,-803,1000,-930,1000,796,-1000,-237,847,-1000,540,1000,-266,768,-1000,-153,1000,659,77,-74,140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.String:LytqLy93PT0=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{1000,533,-135,505,-355,1000,117,-154,603,310,-21,-126,520,-235,-827,-327,1000,-341,915,1000,227,-397,848,935,-1000,92,-124,-994,349,1000,-677,-744,-1000,1000,-468,-505,-1000,58,-100,845,-588,-1000,899,-184,562,119,732,376,-159,-86,-829,-745,-750,-106,-226,-483,584,794,-816,1000,495,-1000,1000,-635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.String:QVFEXzJ3AADvv70A77+9", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{745,209,-905,428,305,-45,-17,1000,-603,-666,207,-740,1000,-1000,-285,-70,-123,-992,278,373,-1000,-378,1000,183,-374,118,727,8,-1000,-91,456,1000,-511,-1000,358,322,-1000,916,-141,203,-475,220,212,338,-376,1000,501,-789,367,-568,363,-186,-438,275,574,-18,798,-1000,-1000,1000,238,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{-1000,775,262,132,298,-841,494,-474,60,-440,2,-1000,-351,89,-194,171,-1000,138,553,402,1000,232,-762,771,154,421,-299,-176,-565,222,674,54,751,-324,456,-992,-112,-266,126,517,-678,448,-414,-154,-782,-82,-1000,-1000,-428,881,-400,290,493,637,-835,-353,-667,-94,63,217,-732,400,-126,-355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{-928,1000,-530,-999,-451,-492,-660,-1000,-290,-753,335,-706,-176,-723,680,419,-1000,610,711,1000,571,-1000,472,737,825,501,241,-1000,1000,-921,1000,-1000,165,-229,-552,-1000,58,-1000,-80,321,517,-1000,-114,-164,1000,-825,-1000,-1000,-142,1000,-112,1000,99,908,-773,410,401,-954,525,838,-1000,-600,-688,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{-111,617,862,269,298,-931,347,-474,-336,568,2,-1000,-372,1000,-1000,-98,-1000,1000,553,402,1000,232,-740,-1000,491,532,-299,-176,-541,10,674,-311,751,-395,456,-992,-112,-388,298,517,-678,441,378,-154,-782,-242,-394,-1000,-446,964,-271,-1000,212,640,-835,-353,-1000,-172,255,217,-732,400,122,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{804,730,-508,-1000,-116,291,533,65,32,-912,1000,-747,-677,938,706,94,-643,-1000,117,-448,-455,203,-558,-491,-370,518,-333,-445,225,-140,-526,1000,-98,441,-699,-465,-1000,-905,612,-471,1000,300,258,-674,426,501,467,167,1000,-118,-455,-37,300,1000,419,320,-271,-1000,-1000,1000,-385,1000,900,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{-261,430,827,545,38,-701,-466,-251,835,78,-662,-366,-339,986,496,93,369,-681,-318,971,347,178,-739,658,803,-215,747,-189,913,-734,-27,-179,944,-790,768,-729,138,655,476,-887,94,214,542,495,-348,-1,532,290,892,710,502,246,-263,804,393,363,884,29,272,-367,-698,-269,-344,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{-166,-46,106,-470,-759,1000,662,-1000,-157,618,1000,4,-425,-590,-268,-53,-587,4,-409,334,712,502,750,-69,896,-1000,-337,-87,-1000,-1000,606,253,-1000,-1000,-395,-359,1000,-554,467,480,-410,311,-1000,1000,660,422,683,-863,-1000,-709,1000,-1000,-1000,-937,1000,267,-75,-583,-1000,184,1000,-226,-1000,-534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{152,876,-313,-100,810,820,323,-682,647,334,581,456,-42,-165,-609,-716,255,768,-62,-516,507,43,692,-163,197,226,-500,-647,-547,-204,-716,-599,167,802,908,693,893,80,120,510,631,794,-864,-180,77,-441,-631,-945,-130,618,103,-782,-520,656,504,-273,-74,-311,-764,-46,290,-825,-820,-262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read():int",
            new int[]{819,-1000,1000,-633,-906,1000,127,-13,1000,189,-1000,-543,-785,677,-974,1000,470,1000,-1000,-753,-1000,420,-264,-1000,-38,-937,-750,1000,-310,1000,1000,-494,-1000,-1000,-1000,1000,518,-107,-658,-178,-1000,1000,1000,121,172,-1000,-538,-1000,266,1000,-445,559,321,1000,-1000,27,-31,-1000,-386,-366,-1000,109,-48,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{353,-1000,-1000,640,493,1000,-1000,-201,385,800,412,1000,-1000,-1000,562,-95,-1000,1000,416,607,1000,133,-897,1000,-1000,251,178,-1000,803,1000,586,-465,241,-1000,780,327,-585,-1000,-1000,124,-516,-303,940,-1000,735,-39,-446,960,666,-980,1000,-1000,-1000,-249,-193,801,1000,-1000,1000,1000,-762,1000,1000,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{249,-558,536,-656,907,146,-772,-161,694,681,767,-92,142,379,-60,944,128,-536,-364,-99,298,356,667,-163,-254,338,193,-178,-434,108,-377,-298,-248,-840,-444,-188,-65,165,-663,106,912,573,77,-652,-430,487,619,-938,-104,-448,-108,717,-944,70,280,287,188,839,-753,-244,544,318,294,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-851,-295,-797,935,330,-187,-712,-723,895,140,-514,285,514,-441,-652,902,625,-342,622,974,517,-794,235,-123,557,323,813,134,778,-24,-819,-393,-163,782,-26,403,415,365,-519,-984,554,533,143,438,124,420,760,976,-347,-635,-616,323,-936,-179,-881,-918,553,-157,-396,-620,769,840,842,-524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-763,-235,-85,-485,236,-1000,562,842,421,1000,-1000,-1000,14,-685,1000,-1000,1000,-167,-40,-1000,839,1000,1000,-811,391,525,348,804,146,-848,1000,113,-778,-1000,1000,-949,1000,1000,-158,52,265,-1000,-1000,1000,-426,1000,-68,-1000,779,-1000,362,1000,1000,-548,-882,1000,-883,1000,-1000,-1000,1000,-1000,-94,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{502,-1000,-1000,276,-203,1000,77,58,-213,-591,493,355,248,-1000,-594,-850,-1000,592,357,753,38,451,196,1000,-1000,-141,551,-725,40,484,-192,-742,384,-357,443,-613,-644,-481,-483,39,532,635,398,-1000,77,-1000,492,-287,-666,-1000,605,-851,-1000,-270,70,386,833,-487,84,980,-854,212,410,373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{360,-1000,1000,-1000,1000,-369,-272,-1000,-259,403,1000,-349,1000,1000,1000,922,1000,388,-1000,-558,275,1000,157,-1000,628,493,1000,1000,1000,-1000,494,-504,-681,-118,1000,570,190,-55,-595,1000,582,-859,-1000,-209,1000,-315,953,-387,501,-1000,-276,662,15,261,-849,-93,-477,873,-737,-298,-504,-1000,-468,395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-545,-1000,-111,640,990,1000,372,-426,-73,-242,-813,-400,751,737,400,-717,113,1000,-284,304,690,540,569,-137,-801,390,757,-1000,563,-1000,219,-956,241,-461,1000,-173,971,863,-155,-53,961,-643,-777,-1000,-564,481,640,-465,-40,-980,674,1000,400,-519,-415,801,-624,400,-515,-698,-762,-400,176,578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{988,476,-740,207,-280,1000,150,-310,853,793,-974,-247,173,233,169,1000,856,21,1000,630,-665,38,-1000,248,1000,-868,-946,-209,-28,-196,-820,518,256,-1000,724,-5,474,211,95,1000,-1000,419,412,-244,-214,-644,-1000,-367,-613,-677,-1000,144,-227,843,-447,614,541,598,292,-900,-7,214,-438,30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{-664,-737,-735,262,797,274,138,978,678,771,-109,264,-562,-834,826,-673,689,143,-886,-40,898,329,-617,752,-828,277,788,352,855,-426,-29,398,780,-119,217,-267,253,-778,-843,89,94,-49,-656,-464,393,480,-797,-295,45,-937,-8,30,-441,731,-342,-936,-817,-198,780,-680,936,308,-865,13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{0,-1000,-706,-703,-990,423,-255,0,-266,514,-677,270,582,-984,580,302,-306,795,97,745,403,-513,611,-477,315,146,114,76,-557,1000,1000,-307,-652,-1000,0,-483,1000,-352,-906,1000,-788,-907,567,-37,-708,-740,-510,-1000,689,-338,380,-847,-992,-1000,-103,168,1000,-535,-810,-1000,843,-1000,-935,-502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "flush():void",
            new int[]{-85,-343,941,-103,-542,-337,-72,-636,-578,667,269,-424,795,276,770,-345,-529,-1,342,817,-860,-983,668,-713,62,357,927,229,-945,952,107,502,520,-56,347,583,136,273,962,-619,-30,-865,-672,-204,200,-833,-359,-101,349,405,-290,343,46,968,-575,-711,-365,204,291,-533,690,-415,-477,307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "flush():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{1000,462,-1000,-777,317,1000,1000,576,-598,-395,-1000,583,-1000,49,534,-311,-994,570,5,417,836,555,1000,979,812,-1000,686,-284,975,-86,-366,1000,252,1000,-1000,1000,1000,-885,1000,-1000,-1000,-1000,815,640,-1000,-451,-988,-886,-1000,-1000,1000,646,198,1000,-281,881,977,-760,-165,-1000,1000,1000,-312,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{1000,1000,123,-1000,358,-563,-584,-6,229,221,-1000,1000,-1000,861,445,965,-1000,784,-39,1000,-344,1000,-864,-444,854,-103,1000,450,-268,1000,-388,38,110,-129,-1000,27,1000,-429,935,-1000,-885,-557,1000,249,-731,-1000,752,-1000,410,-35,1000,1000,147,-541,-1000,1000,-490,-640,1000,-1000,1000,437,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-924,-952,917,377,-432,-914,-512,481,664,789,-465,-276,-351,692,961,-52,-800,619,632,865,-201,756,-208,291,409,124,574,197,784,-199,603,-499,-445,562,698,-608,-25,505,761,403,139,-377,-899,381,356,-655,609,-931,-627,-432,-488,96,-806,-103,727,832,-673,-573,253,778,-493,732,-523,-35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-832,1000,296,-244,-248,-882,-871,-380,760,24,-1000,1000,-1000,387,673,739,-1000,843,125,1000,27,1000,-871,139,-212,-103,614,338,361,882,94,38,477,-129,-1000,20,1000,-454,662,-1000,-1000,128,1000,1000,-147,-1000,752,-879,-400,119,-935,-1,-342,-347,-46,761,-451,222,855,963,1000,892,998,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{144,-1000,-446,594,607,-688,-1000,629,861,-287,-263,545,177,-998,361,63,341,-321,975,-1000,-449,-1000,991,-1000,986,-423,364,-1000,658,324,-884,-580,-659,-629,-96,-793,-1000,1000,-685,-258,-365,-598,28,-73,-50,1000,-332,-63,-662,289,-588,1000,-241,-1000,816,-873,442,-921,294,364,1000,-153,65,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{50,-943,416,-369,724,-309,-148,-1000,310,529,-296,533,151,-622,511,1000,-505,940,493,1000,439,866,-491,-215,585,176,1000,-204,71,1000,-351,-886,326,867,1000,-1000,-1000,219,287,-219,-727,316,577,15,294,364,197,-707,296,-807,491,569,190,826,60,852,365,1000,1000,639,419,526,451,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-552,704,-687,149,485,218,-1000,30,-740,-772,-329,549,641,-705,-1000,1000,-955,-709,1000,-1000,-1000,-1000,1000,-1000,-84,-182,535,-1000,1000,-496,-1000,-710,103,-1000,1000,-293,-531,341,-994,123,-537,712,-1000,-1000,-930,-729,-501,448,1000,1000,1000,315,-50,728,329,-990,756,1000,1000,1000,445,-1000,53,61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{294,-103,-367,129,-400,689,-152,-651,73,1000,-453,459,-278,475,1000,977,-974,977,-450,800,-296,856,-1000,502,-385,-234,-702,1000,713,-24,124,-737,896,221,-57,-164,124,-408,-326,303,-8,152,127,-737,-111,844,-203,470,301,945,-530,153,-1000,88,286,717,-20,280,25,-148,104,1000,105,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{194,-463,-704,535,-274,641,-1000,-954,-641,-385,669,-51,211,446,-79,375,-1000,-119,-583,388,687,961,-486,-1000,898,-1000,1000,-289,706,-752,694,-1000,525,-1000,595,790,206,-477,892,850,-140,-1000,446,-1000,-1000,-1000,-233,-1000,596,-707,978,-214,-1000,-942,-368,661,-1000,176,-147,1000,968,110,-415,7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{-976,604,397,691,977,-698,637,-748,317,52,-672,-898,590,-246,331,-52,-159,810,-416,-618,178,-1000,170,714,1000,566,-1000,1000,1000,-854,1000,123,-1000,-625,-622,-1000,-128,1000,-678,-221,256,-802,174,-434,1000,871,362,1000,-404,312,-372,1000,-152,1000,-867,-373,1000,-880,425,-1000,-448,1000,-553,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{242,-751,-507,197,-46,71,-646,-815,67,-563,663,-81,-129,324,560,-666,-753,-766,-941,751,998,462,-171,-279,92,-624,748,-269,-18,402,924,-518,-289,-389,-272,198,236,-459,396,674,115,-795,-287,-344,-496,-868,412,-491,-718,-319,62,143,-973,939,-598,-328,-424,-3,355,390,931,608,-687,-705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{-492,293,903,-52,875,-1000,972,-935,-32,-610,154,507,-253,278,373,-163,674,-32,1000,-299,147,-578,415,657,248,603,-1000,1000,969,866,535,-670,-720,798,-679,-1000,-262,1000,-1000,915,287,1000,-898,1000,1000,426,34,272,-299,930,-155,1000,1000,1000,-736,76,1000,126,529,-1000,-594,496,-971,-338}));
    }
}
