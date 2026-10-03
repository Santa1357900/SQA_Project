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
            new int[]{-441,-892,813,-196,554,563,1000,1000,-1000,666,592,76,-1000,837,-302,324,-954,-1000,-1000,27,-410,-380,692,1000,-1000,1000,1000,-1000,1000,-65,58,-189,-230,389,275,94,-1000,46,-862,776,-10,-1000,419,293,-993,450,-350,661,-62,-1000,558,-988,769,497,-1000,-707,776,-688,-179,800,753,-1000,-1000,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{1000,-895,-875,-729,910,-539,616,-662,-1000,1000,-1000,-773,-1000,-444,904,328,838,475,-1000,246,-258,-1000,-148,-896,-1000,-489,-12,-768,80,-209,-1000,-548,344,-302,1000,540,-108,-191,1000,287,-1000,-313,841,409,-712,496,-630,-1000,-998,1000,429,-1000,-186,1000,479,-458,80,-1000,-1000,-573,837,704,594,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{-69,-784,148,-747,-136,951,207,1000,-98,-1000,-83,510,687,-667,-721,-157,-1000,223,-641,995,-297,-103,-1000,785,-789,-1000,1000,-460,258,1000,-1000,-1000,1000,-933,390,456,-1000,719,870,1000,879,179,-777,504,951,205,-1000,-1000,-876,133,1000,101,-135,1000,-1000,-679,-1000,1000,-1000,-51,-909,142,1000,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:ODM=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{72,23,-61,1000,-429,-626,569,-123,-319,512,720,6,263,857,898,964,1000,-1000,-1000,556,622,-1000,1000,159,449,1000,-850,223,-341,-697,538,1000,81,-1000,-1000,-1000,630,-123,-1000,-975,230,-849,-673,490,729,-814,-75,707,-448,-727,1000,668,-47,-127,-448,241,586,1000,669,-322,876,7,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{-621,65,-358,1000,316,46,-123,-301,1000,-212,124,-85,222,56,-740,56,-55,333,384,684,-300,183,-451,-538,416,-238,530,-1000,-69,618,-438,-468,503,-559,217,-55,796,-899,275,168,-165,86,1000,98,-62,49,84,-1000,-333,192,-1000,-895,892,301,141,802,98,-299,-484,-270,-415,-57,-199,-870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTk1:19:java.lang.Byte:LTcy:19:java.lang.Byte:LTM0:19:java.lang.Byte:MTE0:19:java.lang.Byte:LTM1", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{-890,705,42,-266,523,-875,618,-705,575,964,-633,556,-911,-405,-83,-799,-532,-21,151,722,-496,-954,32,-928,-110,927,-610,-621,6,278,890,455,113,-206,-231,-259,728,-479,-672,-126,-314,-427,-332,87,-887,499,717,-704,418,649,-826,927,-730,-340,714,276,484,-561,-509,-497,778,870,802,-743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.DecoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{-1000,-171,-559,-1000,-22,985,-812,1000,-640,-1000,1000,-903,-370,-1000,168,-200,324,-1000,268,-763,-1000,-543,298,1000,-1000,1000,170,-961,187,100,445,210,1000,-513,-35,898,-1000,690,-272,-938,-1000,570,-7,-1000,-62,1000,371,497,1000,663,-1000,31,-5,-1000,619,111,793,514,1000,-535,-1000,8,-266,-538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTk1:19:java.lang.Byte:LTcy:19:java.lang.Byte:LTM0:19:java.lang.Byte:MTE0:19:java.lang.Byte:LTM1", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{979,-534,-360,-77,460,237,-512,458,-27,140,6,619,-375,-507,-497,-183,-570,-172,-579,-421,-533,456,-731,891,717,-974,247,837,-643,-769,-541,-287,-125,780,39,-240,983,-822,885,135,-942,-91,-774,-184,563,-776,-239,778,-236,-140,-256,-68,925,777,49,105,-859,-745,-3,-357,969,-452,512,-929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTE3:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTU=:19:java.lang.Byte:LTQ1", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{979,1000,408,837,-718,68,-699,1000,121,775,885,-497,870,-1000,287,-561,-1000,-439,-920,-1000,252,-630,-400,182,438,1000,-700,-49,-400,-1000,277,159,481,-1000,956,-993,498,-92,-772,1000,57,-1000,59,-32,149,-352,1000,-950,-688,-628,-305,498,404,-1000,-855,-892,-490,-630,44,-1000,-443,1000,865,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{136,-911,84,154,1000,-1000,1000,-1000,312,-1000,-217,-870,-788,1000,400,-386,1000,597,51,1000,-1000,-713,106,727,-535,141,-609,719,1000,560,240,132,-180,-1000,711,865,854,258,-900,1000,778,-593,-495,1000,1000,-1000,-611,-192,1000,46,-1000,709,-480,938,240,659,-221,-226,-435,-1000,1000,646,-854,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{-1000,908,-440,-582,1000,517,397,118,-258,231,125,-173,1000,-974,-863,-655,-634,-1000,-1000,-1000,-134,745,-1000,-336,-713,1000,-303,504,-665,-300,-1000,1000,1000,-1000,-1000,110,-1000,127,6,-1000,-1000,465,520,-1000,1000,-725,1000,-600,-184,-884,-393,712,1000,1000,-1000,-1000,1000,1000,-729,499,-1000,-872,-552,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("ARRAY:[B:14:19:java.lang.Byte:LTU=:19:java.lang.Byte:NzY=:19:java.lang.Byte:MTI0:19:java.lang.Byte:LTQ1:19:java.lang.Byte:Nzc=:19:java.lang.Byte:NTI=:19:java.lang.Byte:LTQ1:19:java.lang.Byte:Nzc=:19:java.lang.Byte:NTI=:19:java.lang.Byte:LTQ1:19:java.lang.Byte:Nzc=:19:java.lang.Byte:NTI=:19:java.lang.Byte:LTQ1:19:java.lang.Byte:Nzc=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{1000,101,-497,-209,1000,-241,601,215,-637,366,635,-209,481,-195,324,-573,-29,808,-580,370,-578,-380,102,324,-263,583,607,977,-401,605,840,419,101,-479,-54,971,750,492,777,-196,-85,-128,266,668,-71,-183,14,-168,-656,770,-54,317,652,-47,-405,-495,82,-167,70,-887,-219,820,174,-266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:LTQx:19:java.lang.Byte:Nzc=:19:java.lang.Byte:NTI=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{705,-1000,1000,-710,1000,-428,955,-1000,488,-397,-938,700,-295,381,149,453,-342,310,-470,1000,-436,-1000,1000,82,-842,-71,1000,-453,177,1000,-732,639,-521,13,-125,595,598,442,594,-340,417,-6,-313,832,1000,676,-417,-369,-191,1000,-836,-353,336,-581,-891,-169,-113,210,621,-824,250,193,-164,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{171,-1000,807,563,911,1000,-701,12,-727,1000,5,-46,454,1000,-546,362,316,13,1000,548,993,-747,-1000,78,-507,258,258,8,10,1000,662,917,262,1000,1000,920,-1000,283,-185,-1000,-23,1000,193,-344,-713,324,-935,467,-462,135,1000,663,1000,752,142,-240,-1000,318,-1000,-18,-1000,1000,419,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{667,-393,887,-882,-161,741,-24,1000,-1000,308,169,-322,892,702,675,-340,-1000,-684,-1000,-92,129,0,-1000,-128,74,-1000,850,-164,243,1000,553,743,155,1000,-828,-752,-213,572,838,319,-605,1000,243,726,177,538,-201,968,143,1000,354,-690,330,-268,713,-118,-662,-776,-1000,-79,-87,-1000,-1000,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:NDg=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{352,-979,511,623,646,772,-710,-956,251,2,481,-294,-202,523,89,361,-214,359,124,986,364,-317,-989,-176,446,700,-319,-759,-485,643,-373,305,-381,454,93,476,-875,-305,-537,-246,87,770,922,-777,-724,623,497,45,-702,-241,702,657,610,615,-802,-837,-329,152,-918,729,-970,808,-244,193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{573,780,-1000,-363,725,779,-263,-1000,1000,137,285,-368,-429,300,270,-997,-17,-200,-120,-195,592,-879,140,74,-802,1000,394,609,-365,-725,-103,-327,800,-903,56,-222,293,1000,-594,843,784,-759,841,55,-279,223,450,-732,-1000,-688,910,1000,-1000,580,-638,-799,-178,134,277,325,147,841,1000,249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTU=:19:java.lang.Byte:OTM=:19:java.lang.Byte:NTI=:19:java.lang.Byte:LTQ1", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{341,1000,-671,1000,-1000,-370,1000,-1000,1000,-808,-1000,-616,185,-808,1000,1000,1000,-1000,-1000,-7,394,-32,-1000,1000,65,513,-602,1000,-1000,1000,1000,1000,-479,1000,-1000,-1000,-1000,-1000,-1000,-699,-1000,6,1000,-1000,357,547,-222,-191,1000,-285,213,-1000,1000,-1000,-691,-1000,-215,-1000,-1000,-1000,1000,1000,805,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{556,1000,-260,-324,-1000,-1000,-44,-374,817,1000,613,857,662,-756,356,-544,728,-1000,134,1000,1000,262,1000,776,-992,1000,-68,-310,-969,546,-110,1000,1000,835,-1000,182,-787,224,-1000,-1000,-545,-129,150,-447,1000,-984,193,-406,1000,-902,747,1000,967,-397,1000,-307,780,-152,-442,400,453,1000,-365,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{622,-826,910,175,498,415,117,-756,-4,857,534,327,-594,475,-800,212,379,93,800,232,-117,645,-305,-956,-53,394,-315,749,-857,373,687,549,662,-787,-438,-638,-570,507,-146,45,761,943,342,-741,-259,-511,993,352,-272,-847,-833,122,147,-965,727,-173,111,-313,78,398,-706,-805,123,-561}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:LTU=:19:java.lang.Byte:LTE5:19:java.lang.Byte:MTE2:19:java.lang.Byte:LTQ1:19:java.lang.Byte:NzE=:19:java.lang.Byte:LTY2:19:java.lang.Byte:LTMz:19:java.lang.Byte:LTY2", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{-618,-1000,-25,-375,375,-505,39,534,736,183,526,-586,-614,-465,-704,-214,-710,-441,703,180,-496,-266,967,-100,-633,-412,-795,-215,-289,-130,-194,-613,-45,230,832,191,467,260,-321,758,684,841,-631,45,-859,-179,540,-516,-253,240,-561,915,-502,552,-964,-50,78,582,784,943,-216,334,440,376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:LTU=:19:java.lang.Byte:MTI1:19:java.lang.Byte:LTcx", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{666,325,233,-327,-1000,-275,-748,-340,142,530,-96,383,-715,972,106,-104,-499,875,147,-405,911,1000,-285,48,401,-270,-547,155,-319,84,-1000,1000,634,-267,24,-744,707,-483,-710,-534,1000,-622,870,-493,124,-143,537,6,707,125,-348,1000,-381,478,-212,833,992,1000,473,-270,314,-73,223,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{-471,-659,671,1000,555,-672,453,449,176,546,-926,839,-241,573,-1000,-171,-1000,722,214,-79,-1000,295,-197,-557,196,341,-706,-802,-1000,-364,-396,-1000,-802,536,220,-1000,975,1000,1000,1000,-908,-878,-493,1000,-1000,-1000,284,-37,727,-303,-193,20,-1000,-1000,-1000,-345,223,62,975,1000,-806,-458,-1000,-931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{637,659,-206,-889,-328,275,645,180,-458,665,1000,-759,151,1000,1000,1000,1000,-631,62,1000,1000,-268,163,-118,771,-618,1000,835,1000,847,1000,1000,1000,526,545,-642,-883,-1000,-718,-1000,-1000,759,1000,-764,-240,1000,1000,122,-541,-1000,-1000,-889,912,-267,-147,-342,398,707,-1000,655,536,-456,1000,-482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{-958,123,491,-14,-485,73,645,806,80,665,368,289,-304,118,807,413,433,75,22,359,-325,375,163,383,914,763,-452,-946,256,621,713,626,678,-196,722,845,87,-778,-718,-418,245,963,-457,-661,-240,398,-141,-103,-484,-996,237,-830,-861,433,714,656,398,-275,497,796,-181,531,319,-977}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MTE5", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{-225,1000,118,-211,-841,-692,302,752,780,452,-1000,-890,279,152,765,-885,-968,-332,1000,730,85,463,-1000,-148,383,-15,1000,304,62,645,1000,727,-262,892,-111,-910,877,-1000,1000,-792,-1000,1000,295,-564,367,955,264,-848,504,-499,-1000,-1000,-485,-973,691,510,120,-38,756,-334,1000,997,-510,263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:NTE=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-489,-678,-251,356,60,-309,-133,-159,613,-146,32,-46,-758,495,357,785,935,-710,334,-432,-556,-457,-842,571,972,447,-943,667,872,-283,643,-842,-288,171,-202,-218,-581,185,218,751,-475,-92,344,-876,2,622,153,862,618,-319,23,-509,-648,116,715,500,-61,159,438,-745,-618,-299,-130,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjY=:19:java.lang.Byte:NzA=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-301,-161,169,1000,-298,343,-568,-55,-471,1000,-961,-445,283,-1000,612,1000,698,-218,1000,169,-573,508,307,1000,-1000,-561,-630,1000,602,-222,-359,426,397,-126,1000,-1000,-1000,284,1000,975,-822,-1000,39,-329,1000,-1000,-1000,-1000,-7,-715,998,1000,377,53,1000,762,-237,-1000,-1000,832,83,144,-880,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{0,868,1000,325,-1000,0,1000,246,-1000,579,-49,669,-1000,-664,1000,1000,0,1000,-1000,-203,-678,-868,465,-948,95,0,-1000,0,22,-308,1000,0,-402,-80,-263,-1000,-1000,-1000,-50,-477,-176,1000,-774,-153,-1000,420,929,-1000,185,436,903,466,-50,819,494,-1000,-639,-365,1000,1000,0,-4,-1000,545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-251,-529,-1000,899,-867,-927,-442,1000,-228,303,36,-706,853,-723,120,980,454,-703,979,-407,-761,-477,-205,549,-654,-1000,-77,304,829,1000,481,499,-729,-1000,-801,788,-246,611,411,1000,-204,-399,1000,532,-662,-650,-666,-498,467,-1000,701,488,-963,-904,430,495,-1000,-662,-846,342,430,1000,-987,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njk=:19:java.lang.Byte:NjY=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-183,653,533,-623,-308,-173,-305,246,-1000,-749,-1000,-383,-474,-664,1000,1000,1000,-261,-167,1000,-419,-290,-691,102,-320,-464,466,140,416,-1000,564,-210,-266,-569,-742,-1000,402,-1000,-349,221,259,500,414,-153,-1000,616,1000,755,-27,-114,663,-674,-757,963,-555,-83,-570,-145,1000,-235,115,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-741,91,-612,337,526,568,905,740,614,-1000,1000,-176,961,-731,-516,-699,109,-537,1000,-867,1000,1000,-148,-129,695,-895,441,-1000,68,1000,-152,-940,-232,-606,277,1000,929,-234,-625,-322,1000,-64,628,287,-87,-61,-498,1000,-662,-1000,-39,-141,-1000,-1000,-782,446,303,1000,13,-238,845,572,376,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-299,-252,-619,751,-391,-899,467,548,-366,437,42,-706,1000,-887,-1000,-577,-1000,194,678,-203,825,-277,703,510,-304,-992,37,-507,747,-283,-373,217,-729,-827,-851,675,-106,611,277,483,358,184,403,222,1000,51,-282,-498,395,-823,618,488,-963,-1000,-170,517,544,64,-74,356,556,-343,-987,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{-1000,704,-133,-1000,1000,399,-834,107,-503,-472,-56,-1000,-153,652,1000,-686,146,-1000,1000,-1000,924,967,-294,1000,-845,-1000,246,1000,-407,852,-1000,756,668,-797,-1000,-1000,256,-1000,636,69,1000,282,800,524,-1000,-481,1000,855,1000,477,1000,839,1000,-1000,778,1000,-43,-444,8,170,-793,-943,-852,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{-447,199,-199,-31,-542,-452,998,-131,-217,777,906,-359,-957,-762,422,318,651,-584,142,463,-743,-360,-484,-731,-296,102,194,-438,944,-69,596,-679,790,956,-298,-158,159,-493,-765,-962,-416,-921,943,-481,210,43,-471,-899,238,-302,-70,153,-181,907,96,-184,792,342,-789,-461,327,-97,373,-406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{-204,991,-275,46,-488,-690,92,-154,412,510,3,-346,-510,184,-980,-752,-921,448,112,-237,997,-871,21,-405,-951,-169,849,-49,-395,-305,777,-766,446,601,-267,-11,-122,248,35,-508,-746,20,559,-764,449,941,-173,-472,-406,-928,551,-773,-579,-274,-368,-762,962,-420,-591,-205,983,-621,3,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NDc=:19:java.lang.Byte:NTU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODQ=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{-819,640,-777,944,467,193,-626,198,-953,845,33,565,936,-533,-516,18,485,797,-617,125,-124,127,-285,-89,-861,-861,778,-423,685,653,-257,110,-774,-613,881,-979,524,-546,769,34,911,-671,944,-598,-248,-766,369,-296,-898,550,793,-503,704,-312,-351,-39,148,130,-517,-158,-447,-169,45,-608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{437,177,367,202,878,-214,-316,459,593,234,-212,-302,-873,-349,-257,208,391,-119,-406,830,597,83,-980,907,537,835,333,-685,946,-32,-411,-447,-516,-297,96,-880,-439,-720,-104,-55,614,275,856,540,758,-950,-6,-313,-228,370,-690,-7,-725,122,-987,-557,-351,-696,319,198,-222,-52,217,327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDk=:19:java.lang.Byte:NzI=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{-788,-601,-208,818,-530,-894,-31,-481,-705,-2,263,560,-41,-497,905,648,-321,874,841,-103,-927,-470,48,312,321,676,964,-240,85,-782,-877,-767,63,-642,-411,434,666,-105,-931,-719,790,210,-328,785,-120,-788,-774,-50,-426,-918,767,-169,-903,-426,371,-310,-726,837,-152,-681,532,-752,978,16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{621,430,-1000,-955,-788,1000,-1000,-372,-1000,920,717,12,46,-55,465,-168,488,1000,-778,263,444,1000,673,-219,-233,1000,254,-1000,230,531,475,-575,-474,1000,901,-670,-831,210,954,-692,-668,540,181,914,104,699,558,315,-74,-188,1000,-50,-167,-1000,-144,469,-9,211,-1000,956,1000,-418,-677,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{598,114,-876,-692,-693,626,-927,-169,-900,795,610,-483,109,-59,438,-139,614,772,-317,-89,146,824,604,-699,90,834,447,-913,4,287,179,-345,-575,593,731,-272,-333,96,351,-263,-188,440,-347,711,-103,662,60,210,190,-125,460,-170,336,-656,119,320,160,384,-867,448,964,-419,-935,425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NjU=:19:java.lang.Byte:NzU=:19:java.lang.Byte:NzY=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{257,-431,-221,-94,35,-180,-40,367,968,-268,302,12,140,-326,-54,270,367,591,384,410,514,-746,763,828,755,344,254,180,230,131,-966,658,-808,-239,-721,-536,-592,859,-463,785,-707,508,-126,-551,80,522,-752,828,183,-613,733,-472,825,225,394,-37,989,710,-98,955,-169,-992,-706,228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:Nzc=:19:java.lang.Byte:MTAy:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{-471,295,-30,-57,215,992,641,1000,1000,-1000,-78,190,673,280,657,360,152,-410,218,1,-46,-353,367,-452,660,865,1000,90,653,908,-148,-663,1000,-527,47,-668,1000,634,425,168,239,-897,259,-17,184,798,741,-143,32,599,86,-812,887,295,221,240,247,843,-167,-866,467,-936,611,186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{743,188,-600,47,294,-1000,-1000,-43,409,-168,1000,955,337,-1000,663,-804,227,-19,-419,622,1000,-843,-499,1000,22,-534,-649,587,132,-1000,-86,719,971,427,-920,134,-538,-221,-1000,87,282,1000,-444,-163,-944,-1000,958,-1000,630,-1000,176,878,-1000,-43,-664,-548,-446,-961,38,-715,-23,1000,574,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("ARRAY:[B:9:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njg=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NzE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{773,365,555,-1000,1000,-1000,-382,-1000,995,755,1000,1000,-810,-1000,215,-1000,-53,728,496,453,1000,-706,-681,-145,-767,-1000,-1000,186,-61,-1000,-814,1000,1000,1000,-1000,390,-1000,-993,-1000,-640,421,1000,-207,-605,-1000,84,1000,-1000,-9,-1000,235,437,-1000,-165,157,668,-1000,-1000,1000,-541,-467,1000,77,-1000}));
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
            new int[]{550,-515,494,528,44,358,288,510,964,-668,-36,-582,572,607,865,478,-660,-804,-126,124,-31,191,129,402,-229,140,886,779,-318,410,523,-293,925,-184,327,-122,446,42,-270,459,628,395,-64,841,-468,322,546,-613,902,744,656,-453,537,777,208,297,-729,687,901,320,767,-990,828,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{1000,182,-523,-1000,1000,-458,320,-1000,448,832,107,-72,141,-1000,-154,-271,547,137,-487,-544,231,-54,-257,391,97,962,806,-886,-1000,-56,306,-308,88,-530,-507,449,-1000,-1000,-585,-254,-519,839,-714,-323,-27,84,-465,-922,96,-386,919,695,146,-237,-907,761,-434,-1000,-737,-438,-1000,291,-1000,-786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjY=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{-471,-26,-1000,-306,-450,-375,73,-548,330,981,683,410,243,1000,160,400,415,-4,-1000,1000,625,-957,533,185,1000,-1000,73,-790,-756,1000,-128,-160,-293,731,-747,886,-819,-544,-412,400,173,-1000,663,-1000,-568,-111,-913,1000,949,-436,374,400,-683,1000,115,-355,554,-482,693,-1000,69,-457,249,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{-818,685,-345,-479,-540,-1000,-11,-1000,136,-157,601,632,584,385,452,830,179,58,-138,710,-1000,-789,-709,-66,11,-1000,-885,-1000,111,1000,961,-213,105,531,-529,-1000,-80,635,-1000,1000,-579,480,1000,1000,-538,637,-723,122,411,-1000,124,1000,-173,1000,13,-319,698,-385,1000,1000,-297,-1000,123,-890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("ARRAY:[B:9:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njk=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{-691,588,1000,1000,25,-1000,-440,-400,1000,-1000,-94,1000,1000,984,-1000,-133,1000,1000,534,1000,-1000,-1000,-1000,1000,-939,-678,-1000,-1000,-571,1000,1000,539,567,1000,713,-1000,-1000,1000,105,-727,1000,781,262,473,-1000,-381,730,929,-565,-1000,195,522,-1000,1000,1000,1000,-379,-1000,1000,-506,1000,-1000,-1000,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{622,318,991,-61,-411,-180,108,361,870,-292,-933,-201,844,-342,-793,-732,-990,720,82,586,-39,-489,897,675,78,-260,906,-93,-652,-888,-440,922,257,-657,-344,-816,-339,-416,566,-704,-292,900,-284,-450,423,-393,956,-450,-574,263,170,-140,-635,893,-450,584,-854,-376,326,-459,791,-666,-581,277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{1000,-1000,458,333,-1000,401,1000,1000,373,-1000,-1000,245,587,-824,-1000,342,240,344,-1000,-1000,-266,942,-993,415,-1000,1000,1000,-938,395,-1000,936,1000,772,-971,733,278,98,1000,1000,-1000,304,1000,-1000,458,-178,-1000,-1000,-684,-948,400,-1000,-1000,-895,-329,-39,306,-834,-572,-282,-28,1000,870,-856,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTIy:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njc=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{-637,-385,231,-460,-73,-1000,594,95,706,111,846,456,975,1000,-1000,768,1000,1000,-481,1000,12,-603,-85,1000,520,-1000,-391,-1000,-835,894,137,342,-16,762,-623,-827,-1000,1000,-790,-226,649,-563,186,-1000,-838,-560,-564,705,-155,-1000,54,454,-1000,1000,362,-573,-189,-587,1000,-178,-577,-973,-10,-853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:OTk=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{-327,-195,404,16,424,285,-902,892,985,-834,-391,-484,-760,-708,311,694,-632,-72,559,-44,548,-627,739,2,-46,-619,919,231,-451,-446,6,-114,-204,-422,-228,248,-735,-295,-26,-31,-634,-733,-147,201,-56,-850,-231,444,310,-863,464,209,-561,836,488,-480,524,-507,713,-949,854,-584,519,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:ODA=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njc=:19:java.lang.Byte:Njk=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{-415,-452,59,963,-520,-949,33,-883,765,336,-134,-769,-903,-107,-398,515,-49,-424,-733,107,-54,796,721,48,-781,259,36,122,-567,263,874,-546,630,-751,859,-392,-988,-118,-279,-341,971,267,125,-795,185,-919,-484,612,464,-619,73,914,-833,636,-103,744,461,123,-43,436,502,-716,-30,-8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDg=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{-8,441,-593,494,-339,756,407,-249,-312,396,-198,-963,-348,-486,561,621,669,-61,-765,738,-175,-688,-663,-400,-293,923,-773,860,506,117,-107,-711,775,882,-554,-688,-203,-281,29,716,-241,319,895,-600,-574,-326,880,311,877,263,219,881,992,-253,-264,219,-161,-819,767,42,925,-796,-96,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.String:L3dELw==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{-321,128,914,672,-260,-450,-885,880,-377,86,-951,-378,-603,-697,256,551,870,-68,217,-194,614,587,874,692,-834,519,-956,-447,-480,755,-298,608,851,557,422,-208,-207,655,-792,-955,-493,-603,388,869,325,631,293,500,756,929,37,-434,329,569,867,728,-96,-398,279,-547,822,-728,371,843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.String:QUFEL0FPcz0=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{143,882,-642,-422,331,-928,927,20,-608,-789,227,420,141,320,265,150,-592,43,542,-605,-328,95,870,-339,-275,988,527,-567,-95,-949,627,-103,-761,324,-92,935,-786,-552,-932,-20,-157,-666,230,-279,179,-892,169,-847,-482,-433,518,-4,-867,-759,-673,-856,-414,37,89,-549,-583,-611,594,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.String:QUl3QnlBPT0=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{-686,-132,856,140,-241,-726,745,712,-433,391,-77,-736,-249,-228,-760,725,-291,-800,-193,501,844,-199,-342,364,438,-826,100,996,-174,359,665,402,-237,-20,892,-329,216,-512,-899,960,-200,56,779,966,-478,405,235,669,610,-417,768,649,814,-430,777,915,-261,-537,-89,840,-449,-54,713,543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{-771,-992,-955,-821,616,-841,-656,-294,-124,250,-427,947,-728,110,-581,809,269,-806,-546,-600,685,318,-216,-548,-79,-947,-817,-146,-968,403,583,118,643,-32,384,-152,-725,265,-931,437,-488,-848,828,86,-397,-661,533,6,-365,-116,-724,-827,166,259,894,-619,-651,-925,-543,-498,34,891,-500,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTAy:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTU=:19:java.lang.Byte:NTY=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{965,-28,373,-242,746,-545,528,741,269,-659,-818,671,207,571,-409,427,139,-613,394,791,-95,860,562,-383,-85,362,-50,12,-614,-439,-987,-228,749,-899,-89,-327,987,-363,161,249,557,101,-699,-328,-267,-814,597,-979,688,890,-270,-189,-550,-308,625,-15,976,-596,-920,728,-961,97,-264,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{-902,-944,868,-768,188,514,917,299,-756,-486,716,188,-29,-892,-858,-438,733,-526,-336,158,-882,86,-192,-82,-338,976,986,-334,-434,-775,-415,719,-548,-507,-115,-930,-196,-204,-695,923,573,36,793,239,-377,848,-271,-282,-816,217,-376,148,297,-998,116,-567,-671,33,281,-244,100,761,116,-665}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.String:X3dBQg==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{-399,-74,-718,-936,504,638,-803,-596,95,-746,800,-80,-236,608,-980,-376,387,-542,-29,738,645,639,298,934,235,-341,-361,42,414,-59,292,438,-383,13,733,858,105,398,-81,218,549,-312,-531,-848,-748,348,747,477,-677,-831,-359,-460,530,957,-606,-492,772,-940,-759,-951,-824,616,-208,570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.String:QVFBQV93QQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{917,652,-851,-969,846,-486,223,-491,-7,605,180,-953,-970,-320,445,424,-323,-588,-107,332,-85,774,-358,-235,526,976,-575,-104,225,733,-6,759,985,-213,-482,-427,342,-999,-711,-632,-557,129,-864,746,370,383,-63,53,-93,314,-636,28,-688,195,-443,69,887,94,-181,679,195,112,-367,332}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.String:QVBfX193", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{-134,-453,619,818,602,881,-16,-215,614,779,-578,-475,368,178,497,-864,906,589,691,-43,554,-169,338,-28,-436,-537,-639,-503,-808,-15,-476,710,81,-64,-596,-893,-36,496,638,809,720,-91,-884,-218,-445,135,-252,-487,724,-527,178,454,618,-406,-582,-382,242,-566,-274,143,-808,-323,275,320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:MTIy:19:java.lang.Byte:MTAz:19:java.lang.Byte:NzI=:19:java.lang.Byte:NDc=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{-552,214,-113,67,-502,178,495,457,-644,830,-281,68,270,627,-411,-673,51,-395,194,-130,715,-564,-273,-540,-184,-612,170,161,-210,921,-818,457,458,25,-557,594,884,-304,706,786,199,905,-899,145,723,-640,868,190,723,941,-375,622,505,-766,-485,676,167,-165,-862,819,321,234,-958,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:ODM=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{352,970,732,-722,388,-495,-366,606,-970,794,285,134,-864,-115,-876,529,444,36,669,-965,690,838,664,-810,-5,610,777,595,789,-76,-162,109,-708,564,277,-628,671,426,-593,983,388,-88,-487,834,973,-762,338,-890,475,718,453,-695,181,-403,287,-785,850,-738,-885,347,670,-215,-811,396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{440,-706,-463,-1000,1000,-1000,164,949,-621,106,-545,-119,-1000,1000,1000,-376,378,-1000,1000,-1000,685,-827,698,740,-230,745,909,782,-170,994,-48,750,-1000,53,-103,495,-806,-934,-597,-597,1000,751,1000,-786,-1000,-610,-713,-554,1000,2,408,501,792,660,1000,198,542,-527,1000,1000,382,1000,1000,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("ARRAY:[B:168:19:java.lang.Byte:MTE1:19:java.lang.Byte:NTA=:19:java.lang.Byte:MTIy:19:java.lang.Byte:OTA=:19:java.lang.Byte:ODI=:19:java.lang.Byte:Nzc=:19:java.lang.Byte:Nzc=:19:java.lang.Byte:MTA1:19:java.lang.Byte:MTA0:19:java.lang.Byte:NTE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODg=:19:java.lang.Byte:NzM=:19:java.lang.Byte:Njc=:19:java.lang.Byte:ODE=:19:java.lang.Byte:Njg=:19:java.lang.Byte:MTEw:19:java.lang.Byte:MTE4:19:java.lang.Byte:NTc=:19:java.lang.Byte:NzY=:19:java.lang.Byte:MTA2:19:java.lang.Byte:NzE=:19:java.lang.Byte:MTA4:19:java.lang.Byte:MTAx:19:java.lang.Byte:MTAy:19:java.lang.Byte:MTEz:19:java.lang.Byte:MTA4:19:java.lang.Byte:Nzc=:19:java.lang.Byte:MTE3:19:java.lang.Byte:NTY=:19:java.lang.Byte:NTA=:19:java.lang.Byte:OTA=:19:java.lang.Byte:MTA5:19:java.lang.Byte:MTE0:19:java.lang.Byte:Nzk=:19:java.lang.Byte:OTg=:19:java.lang.Byte:OTc=:19:java.lang.Byte:MTAz:19:java.lang.Byte:MTIy:19:java.lang.Byte:NzA=:19:java.lang.Byte:Nzg=:19:java.lang.Byte:NTA=:19:java.lang.Byte:OTg=:19:java.lang.Byte:NzU=:19:java.lang.Byte:MTA5:19:java.lang.Byte:NTE=:19:java.lang.Byte:NDc=:19:java.lang.Byte:ODQ=:19:java.lang.Byte:NTc=:19:java.lang.Byte:MTA2:19:java.lang.Byte:MTA0:19:java.lang.Byte:NTM=:19:java.lang.Byte:Njg=:19:java.lang.Byte:MTAz:19:java.lang.Byte:NjY=:19:java.lang.Byte:NDk=:19:java.lang.Byte:ODc=:19:java.lang.Byte:Nzc=:19:java.lang.Byte:NTM=:19:java.lang.Byte:NjY=:19:java.lang.Byte:NTQ=:19:java.lang.Byte:NDg=:19:java.lang.Byte:MTIw:19:java.lang.Byte:ODA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{731,1000,-385,-649,-175,-1000,-695,999,-875,984,1000,-25,-1000,-615,-587,37,522,-1000,968,-294,1000,1000,-103,-1000,29,1000,818,360,363,-1000,-147,523,-1000,1000,252,839,489,-202,-1000,1000,848,653,-690,1000,1000,-1000,-199,-634,230,141,-377,-731,20,-1000,-668,-878,1000,-565,-155,-95,628,-1000,-678,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:QVA3X++/ve+/vQA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{-452,-101,25,551,327,745,-82,-1000,-1000,804,-174,1000,217,-39,292,-413,-20,346,957,302,865,400,-427,370,190,-963,-804,-308,831,-344,-166,70,-92,363,-713,-75,1000,470,-859,-376,-338,276,79,-788,855,533,160,-1000,-463,948,-206,612,-750,-622,-436,1000,-1000,-130,-204,1000,-1000,18,-640,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.String:UXFQL0FBPT0=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{308,970,1000,-446,662,-554,-932,418,657,-1000,510,1000,157,-225,1000,-746,674,-571,-1000,-1000,1000,-522,719,-377,237,1000,-1000,973,-492,-312,-1000,-274,198,-650,523,-375,229,-113,-1,-515,-228,1000,17,-815,-64,96,373,-591,1000,310,-181,-75,132,-1000,-292,1000,-89,1000,107,1000,1000,474,-1000,-753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.String:X3dDRV93QQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{1000,-321,638,-323,278,-1000,-1000,-249,-692,1000,-1000,-361,-209,-544,185,1000,644,575,578,-1000,474,400,-722,-466,-177,-957,1000,-985,623,-278,-1000,-1000,-1000,-1000,-1000,9,1000,-639,-789,-35,-1000,-394,-909,-85,-1000,55,-681,-569,1000,141,1000,-1000,-829,630,-1000,204,577,-66,586,1000,1000,1000,597,271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.String:QUFBQQ0KQUE9PQ0K", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{543,492,69,778,108,-110,-576,655,544,-594,38,189,-532,-432,289,-704,-241,-569,-44,-529,286,47,381,-9,-436,226,-787,157,257,-175,-745,-810,-5,981,389,689,-191,-349,271,-296,-391,659,-425,-579,636,997,909,-778,-436,601,-777,447,15,-1000,-485,576,685,323,953,741,938,-39,-853,-504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{183,-544,1000,-64,-474,-219,-31,152,1000,1000,-656,-751,557,468,-916,206,-35,476,-1000,-511,1000,874,-1000,-559,-253,188,-1000,1000,-107,58,-378,-793,-1000,-1000,-394,-101,776,-583,726,-530,228,-542,-315,-524,-270,492,33,-995,1000,976,-197,-23,737,292,559,-904,-885,-724,670,-815,-238,-314,970,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.String:QUFFPQEAFH0=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{181,-612,-1000,-1000,958,400,-95,-153,201,200,-986,-643,-565,-189,-1000,-608,-437,1000,-227,1000,573,631,-331,-726,-683,-260,-1000,-406,1000,951,1000,-1000,273,1000,-814,1000,62,1000,1000,-62,-1000,-544,-1000,1000,-202,1000,240,-1000,601,-1000,-1000,-425,500,-387,-509,885,-20,-539,-1000,1000,211,-1000,-1000,-825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.String:QVA4Ql93", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{-435,-866,1000,-563,-1000,893,188,-1000,954,-1000,160,1000,1000,1000,1000,-1000,-348,877,-1000,-1000,1000,1000,1000,-654,-705,1000,-1000,1000,13,834,-502,-915,-1000,-361,1000,-231,213,-544,-367,1000,-811,-651,682,108,-710,-1000,1000,-912,1000,280,-373,445,1000,644,1000,-587,460,1000,-1000,1000,979,-1000,330,-833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{-20,93,310,435,-1000,353,-685,474,-1000,597,-318,-972,724,-873,1000,1000,-918,75,684,314,767,708,-312,663,-309,806,313,319,627,-283,-352,-159,-918,355,747,-987,-550,-120,-412,897,-609,593,330,-111,-400,-473,-117,119,-136,310,-819,537,-297,740,-38,419,-1000,529,1000,407,-672,719,90,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{811,1000,-878,1000,-839,543,626,787,1000,-391,338,-159,177,-275,-314,-1000,-1000,122,-610,-481,519,-254,831,839,-1000,139,235,95,-580,1000,512,-795,311,-807,-257,541,735,-691,-138,137,-1000,-1000,-745,789,-388,118,-287,-1000,1000,889,-349,-1000,-131,-267,228,-1000,-1000,-688,456,-1000,167,857,1000,-558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{308,887,191,623,696,-719,380,-220,-603,-227,-445,419,-619,-724,-742,-827,-760,482,-873,274,-103,-1000,475,-986,919,-334,1000,440,-949,-537,426,-309,-193,167,-1000,-302,-16,835,232,1000,544,-560,-300,663,-355,530,831,314,-168,352,703,1000,-274,1000,-84,422,350,-1000,720,408,58,1000,-96,-744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{327,161,359,-716,-666,-287,-546,-417,452,-601,-420,-451,15,187,-935,-621,-198,793,461,-204,966,-681,915,-559,-726,-417,456,-646,575,-896,-993,642,-752,-364,-951,842,-792,511,962,473,987,134,299,-220,-599,-528,520,787,337,-460,-973,-572,344,566,740,740,842,-218,-81,905,41,-429,-564,-761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{760,909,-211,-213,533,928,535,432,-733,238,493,407,214,101,-598,540,-431,1000,-325,1000,-1000,458,478,1000,272,937,-763,237,811,-345,-68,1000,81,377,-646,28,1000,-561,-1000,-617,625,48,-639,862,-1000,-350,293,281,400,467,-400,-749,1000,148,-960,-335,483,-1000,-876,468,-296,933,-25,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{-630,-979,-339,-818,489,-784,-319,-107,-313,-729,343,-249,-603,949,392,-45,875,564,-54,233,269,353,289,-214,-725,823,609,402,-440,-191,-335,529,-940,-516,-839,752,-543,952,-252,642,-332,-809,-24,284,14,-895,-712,888,-97,241,201,-114,289,-682,159,612,994,132,280,-691,15,-361,151,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read():int",
            new int[]{4,830,-26,-54,867,969,1000,-882,590,738,-1000,-316,980,76,518,-212,-1000,320,1000,178,-660,861,-55,-887,59,335,-249,172,231,-463,-1000,105,-203,-1000,-409,-445,224,-175,-470,-80,-510,615,296,112,374,-327,-182,-631,-1000,-74,228,-1000,-821,-1000,-116,408,475,-265,179,842,51,-1000,-112,172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read():int",
            new int[]{-339,-1000,602,886,-595,-181,129,1000,-248,-1000,1000,1000,-725,-670,403,-451,-480,374,563,-494,285,-1000,635,-41,309,-1000,470,-689,517,544,-1000,-1000,-1000,-1000,1000,-814,469,-1000,1000,-980,-1000,-1000,479,-338,549,1000,1000,-767,-238,-1000,1000,-171,-198,887,-244,-213,406,-926,795,147,-1000,-1000,850,-828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-792,-766,316,-356,905,820,-1000,541,-486,442,-256,-321,1000,-553,-39,-380,591,-152,-1000,-674,788,-99,-317,54,477,541,-373,229,770,-881,-606,979,-1000,-280,-1000,542,-353,55,-1000,-812,-183,-976,954,783,382,-124,805,-109,90,328,-1000,-123,885,765,-1000,601,275,846,371,367,934,-1000,51,-816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{612,-18,174,759,-537,546,259,-231,246,-927,-639,889,444,242,63,553,802,-973,-734,772,-912,400,662,-205,-636,300,-563,-805,-610,-659,934,722,631,756,405,-857,807,274,969,-790,-94,-208,-590,-116,-838,990,551,990,606,-639,603,594,505,-473,91,-454,-743,-846,-220,919,-901,-686,311,302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{829,-1000,307,468,165,-1000,527,-504,-898,263,-726,-1000,-1000,1000,585,991,1000,-690,1000,-1000,-531,-1000,1000,271,-756,797,-999,-931,1000,1000,757,326,1000,1000,500,-1000,720,-804,318,1000,-1000,-1000,1000,-900,-733,-1000,537,937,-1000,-1000,-1000,28,-985,-1000,1000,-1000,1000,897,-1000,26,1000,364,-569,-157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{470,-1000,-523,1000,797,1000,197,1000,-584,-175,144,1000,1000,-1000,-200,-1000,1000,-189,-1000,-625,1000,709,320,-484,375,-527,-562,-27,-737,-1000,1000,-509,-1000,-369,-422,1000,24,-339,-1000,-1000,-1000,746,-301,983,68,1000,1000,-158,423,-1000,911,297,1000,1000,-1000,830,-1000,-1000,1000,-27,-1000,-1000,254,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{322,-157,568,721,-723,261,-872,843,-924,-155,814,96,-608,39,-354,-662,-661,290,-81,-418,255,965,-158,-437,-982,-800,-486,570,-78,774,859,76,-18,209,-436,-490,-967,412,37,489,-69,391,-67,-215,-234,853,886,367,118,806,742,-60,-948,-310,-168,-370,-150,-983,-433,-356,-774,-989,24,62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-161,-766,785,-356,548,323,-524,-1000,-395,1000,-91,-229,-838,187,-785,-380,-236,-86,344,979,-1000,-99,468,1000,139,-351,811,229,-643,-881,1000,66,-456,-1000,1000,-486,-37,-625,-205,773,169,-976,954,762,-694,-362,1000,46,765,-182,1000,-974,-421,835,-1000,-730,196,-1000,-261,-311,360,756,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{381,740,-134,-1000,-1000,-471,-91,-555,1000,504,-761,-1000,-400,1000,385,885,-1000,-237,-312,-1000,-1000,-1000,1000,524,-1000,73,-902,-460,10,793,1000,-956,1000,1000,1000,-1000,208,355,-251,356,458,1000,1000,-571,-793,-1000,-1000,1000,460,305,-1000,351,-235,-704,1000,-1000,65,-279,-40,363,164,-286,-1000,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{1000,-1000,1000,-723,99,-1000,71,432,-379,-585,922,-1000,1000,-1000,-1000,-701,-579,203,-120,1000,400,-516,-1000,1000,148,475,-65,1000,1000,773,1000,-585,1000,-423,-1000,-1000,-251,-481,-927,455,71,-1000,1000,-641,-1000,803,-498,-1000,363,1000,-1,174,-1000,993,1000,629,416,-358,-329,-1000,891,537,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{205,-311,945,703,643,-989,934,-366,678,-978,478,-214,958,-693,163,214,-907,-60,-940,792,115,-211,-229,839,860,962,338,-101,-120,825,64,430,961,-659,-846,-870,37,-350,-809,-588,359,-290,970,150,-855,616,-40,-765,631,759,-238,709,-594,399,327,-725,254,395,426,-149,332,-132,855,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{-1000,-736,-1000,507,350,-1000,-114,933,575,-1000,-894,-100,-427,165,-413,-1000,-696,973,1000,990,-865,-1000,-902,500,61,-230,-107,781,-419,227,-525,-602,310,-1000,-1000,414,-1000,-1000,-1000,-509,370,233,569,958,-357,927,-971,185,634,708,1000,797,1000,1000,-1000,-306,123,556,-117,-866,-276,664,-364,362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "flush():void",
            new int[]{-407,455,179,-122,-567,-188,974,-472,140,386,782,-185,-448,807,467,944,-76,961,412,272,40,642,-200,639,-158,830,660,173,239,982,30,562,-970,473,-65,794,753,73,700,-526,-368,268,603,927,816,-880,-139,556,-406,-659,-507,-874,-601,340,-251,45,-951,86,476,44,-102,-201,-949,154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "flush():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-1000,86,-780,302,-1000,1000,-1000,371,-1000,129,1000,731,400,-1000,206,166,-47,-901,718,213,39,435,-395,424,897,-234,-726,697,-1000,1000,934,-194,-189,319,649,831,-1000,83,1000,967,-841,681,-105,1000,-341,-1000,-992,-1000,-1000,1000,222,-378,1000,-135,532,-1000,300,-205,-1000,-89,-241,345,407,14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-266,-741,-70,-45,-1000,1000,-711,-730,-1000,-666,120,421,1000,-1000,-1000,1000,-789,-1000,1000,-1000,1000,641,-966,-734,1000,1000,-600,1000,-1000,-124,1000,-933,-1000,287,1000,854,-266,1000,851,843,-241,-235,-307,-1000,-1000,-1000,109,-1000,-1000,1000,1000,377,872,-1000,465,406,892,-562,-1000,1000,-1000,1000,49,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{714,-100,884,-884,2,262,275,-927,809,-9,-377,595,4,-505,27,-430,-996,304,-548,-163,-459,-154,-311,69,-642,870,-474,346,-470,-162,124,40,-162,-12,-204,-179,825,243,-809,819,-974,641,218,-703,-804,550,950,-488,348,-417,231,915,858,801,-264,284,17,-390,-179,952,993,-461,678,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{230,-58,1000,-786,1000,-326,1000,-114,1000,-192,-760,617,1000,-884,967,-169,-1000,304,-1000,-949,-1000,-834,-30,612,-1000,1000,335,-789,700,236,216,-9,-205,-471,246,-686,-382,-834,-1000,1000,-1000,1000,805,-581,596,550,1000,95,1000,-1000,-397,77,1000,404,-804,390,-1000,-244,-761,-211,1000,-237,1000,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{987,-652,-368,-314,-970,951,1000,-256,841,-628,-552,-141,-161,-362,-57,-191,780,-621,-1000,128,-734,-105,-79,-383,810,-1000,-951,-446,-1000,20,596,310,1000,145,-606,-835,971,746,-855,820,248,32,1000,611,1000,981,219,-168,-400,-43,984,-575,-318,793,-561,482,-1000,-1000,-592,131,560,-953,708,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-122,-517,-933,-111,-597,-493,-300,83,-6,-873,-720,961,704,-131,-628,563,910,477,991,701,665,577,538,358,-222,-129,-971,-826,391,734,678,-780,516,-515,78,426,258,-923,-937,-736,242,-580,638,-413,-829,-807,276,895,344,592,382,712,-500,202,-277,239,392,920,225,22,140,-292,721,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-748,65,-544,957,-933,-7,978,-524,-843,497,-55,-647,-308,-534,-131,64,-11,618,692,-846,805,792,-988,678,-912,710,48,-493,639,-8,559,-904,-213,-396,135,489,470,-629,-922,-434,-107,711,432,-151,337,-687,-390,-474,627,-850,-424,-199,249,-121,533,-863,211,504,685,526,-675,959,803,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{241,131,1000,-1000,102,-79,1000,703,1000,4,-1000,3,643,-1000,222,176,-31,-979,-667,-1000,-1000,-835,-313,1000,-322,17,1000,-1000,160,-390,494,459,464,840,-73,-1000,-1000,-89,473,-261,-590,533,532,-704,695,-728,355,-23,-48,429,-1000,-581,-436,-1000,37,312,-1000,475,372,-384,-685,261,870,281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{146,-160,-703,875,-1000,1000,-828,16,1000,-227,-663,-1000,-522,-289,757,-663,-493,-1000,-772,571,1000,-599,-535,349,1000,662,-409,-523,-714,-950,-835,756,400,1000,-1000,920,127,-24,635,-462,642,-1000,851,-903,56,523,1000,-1000,251,-138,407,1000,504,1000,985,894,567,852,178,413,290,444,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{-934,774,-139,-892,226,885,765,-795,300,232,289,-525,217,106,442,895,424,-29,-612,-476,-562,932,973,796,-329,241,43,-68,-300,694,-599,-53,432,-429,573,780,-562,-493,445,-872,-828,597,-748,723,728,920,405,982,-641,-315,-780,252,-969,-773,-272,166,995,-786,439,54,-477,574,-827,788}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{582,-1000,537,-737,-462,346,-1000,-1000,-90,-1000,-519,1000,-1000,-1000,-995,-762,-315,-30,-423,-1000,408,-238,-766,-659,682,66,815,-1000,110,-349,1000,-605,-1000,251,582,170,106,17,319,-577,-1000,-755,371,-1000,-731,-723,-105,-779,579,1000,171,902,-146,165,296,1000,1000,999,-837,408,-463,569,-1000,601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{-1000,623,537,-1000,-315,357,704,-643,-369,489,504,-53,393,918,-181,93,1000,560,-423,-1000,-1000,755,1000,955,-989,-240,315,419,236,606,-790,565,943,197,51,146,-372,17,319,-363,-1000,985,-613,666,1000,682,-837,751,-375,-986,-441,-341,-721,-507,-315,-460,496,-882,69,-292,-860,137,-1000,431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{969,-385,-672,-44,-658,500,-480,-377,-538,-679,-147,-267,-749,-666,-729,-658,-974,540,-911,-815,966,-982,-798,-10,444,-836,-222,910,608,968,532,-183,-66,-795,14,-778,-731,-799,-798,-340,322,701,-850,520,-471,-695,-584,516,-272,-101,587,247,-785,570,336,-362,-642,617,117,-562,231,416,886,412}));
    }
}
