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
            new int[]{1000,-250,205,347,1000,-734,-1000,328,-243,332,532,-154,-1000,-1000,-60,1000,1000,371,466,-236,785,324,-883,627,-1000,234,-1000,605,95,311,-879,1000,862,1000,-1000,715,-251,239,518,493,-371,-1000,-703,1000,1000,-1000,-701,714,-628,440,-1000,272,-1000,-531,-889,-284,1000,1000,490,-413,61,-55,-386,32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{-38,36,182,371,348,-267,-210,198,-154,348,544,1000,-761,227,22,-52,-96,279,1000,123,785,601,213,720,352,577,-361,-503,1000,-741,457,270,373,146,241,1000,-421,949,-230,714,109,-523,-588,561,492,-360,-202,1000,-521,-770,-332,-844,371,-890,311,65,-613,877,-206,-256,-268,25,-860,-303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{1000,-58,-151,-1000,-1000,-752,-463,-544,1000,-666,797,-868,-451,-217,223,-611,235,-1000,-667,472,-398,836,-258,-408,68,723,-975,1000,302,1000,-146,1000,415,952,1000,-316,1000,-1000,745,547,-133,-490,-1000,-146,-686,1000,1000,-703,-1000,920,68,713,856,-522,-1000,807,-1000,-767,1000,-1000,-1000,-30,-596,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{980,-692,-372,1000,-400,508,913,-1000,-105,805,-873,319,-732,753,466,597,-1000,-644,-690,-732,-1000,628,-996,1000,1000,-981,-695,-336,-295,1000,632,-999,375,1000,-1000,-141,-1000,250,1000,269,780,-133,-248,-374,248,-771,-1000,-54,1000,1000,672,-254,220,-1000,644,849,-1000,906,-273,45,932,-1000,-244,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(byte[]):byte[]",
            new int[]{476,-1000,188,1000,657,217,-233,-946,-546,805,-388,515,-992,1000,371,1000,-165,-206,1000,-571,-1000,277,-1000,1000,879,-353,-1000,-706,430,1000,322,-111,1000,1000,-1000,1000,-1000,1000,1000,1000,7,-765,-315,-30,875,-1000,-1000,1000,1000,-173,-502,-642,-1000,-1000,539,29,-666,754,548,-1000,830,-1000,-592,-39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTk1:19:java.lang.Byte:LTcy:19:java.lang.Byte:LTM0:19:java.lang.Byte:MTE0:19:java.lang.Byte:LTM0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{804,-480,690,-740,-241,611,186,26,-196,-357,834,-444,318,672,772,226,-425,55,806,-793,27,-521,972,833,-112,365,-118,-778,880,295,-361,-746,-275,971,-238,3,961,868,-554,574,-951,239,796,-98,323,-606,145,-478,361,-674,514,-400,-405,604,-597,-446,-441,254,615,-488,116,840,622,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.DecoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{899,341,-638,-810,183,-123,-2,-934,281,-849,394,692,151,-484,364,-792,-326,725,-234,973,976,-620,572,-242,-814,907,513,-741,315,19,-100,-668,-382,-515,-142,-636,-120,679,145,47,-648,-116,-36,413,-255,-416,-275,468,-350,122,-386,-990,12,-28,-32,-708,-111,642,977,64,625,744,-971,251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTk1:19:java.lang.Byte:LTcy:19:java.lang.Byte:LTM0:19:java.lang.Byte:MTE0:19:java.lang.Byte:LTM1", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.Object):java.lang.Object",
            new int[]{-548,303,870,-595,377,-389,352,-660,-186,19,218,419,-827,-735,-175,-332,-617,-98,-908,345,232,25,526,251,-575,-176,395,454,634,-440,297,471,-420,-616,964,-34,-844,-877,116,823,990,618,-621,-898,-553,474,-126,-171,-915,-772,-65,834,-197,-188,-407,923,-449,-65,969,543,344,-429,-485,-186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTQ=:19:java.lang.Byte:MTE3:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTkx:19:java.lang.Byte:LTU0:19:java.lang.Byte:MzM=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{746,1000,-132,623,191,295,-889,493,-1000,885,853,-361,1000,-143,982,-238,328,338,-881,1000,11,-273,-437,161,579,-235,874,1,-906,-666,737,-548,-414,-521,1000,-628,-661,809,-988,-762,802,-236,-888,-702,-854,-1000,397,-721,-663,821,247,812,804,992,373,248,-672,72,809,1000,316,-5,-952,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{128,-31,275,94,1000,1000,-1000,140,-399,-101,-462,883,-431,-1000,1000,209,1000,-706,-1000,1000,-306,286,-256,690,1000,1000,1000,-1000,-1000,-1000,-64,883,-1000,1000,1000,-1000,-1000,1000,1000,1000,-19,673,1000,1000,1000,-1000,1000,-1000,408,-1000,1000,88,229,1000,-1000,667,-727,-1000,588,1000,1000,830,-861,28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTU=:19:java.lang.Byte:MTI1:19:java.lang.Byte:NTQ=:19:java.lang.Byte:LTE3:19:java.lang.Byte:MTI3", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{441,-524,561,772,-347,-302,-90,739,-602,-251,563,-362,747,330,151,37,-416,855,-984,-610,278,-503,-261,840,886,702,-918,-448,135,87,-181,-157,964,-484,-177,892,-819,-164,12,168,932,-715,-750,-105,-903,-109,-817,845,472,313,-918,-472,672,-457,279,-529,24,-380,-468,685,-558,-897,749,-357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:23:java.lang.Byte:LTEyOA==:23:java.lang.Byte:LTExMw==:19:java.lang.Byte:LTIx", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{-885,5,-754,-690,-634,-822,-519,-268,-240,-1000,169,-220,-394,-257,368,-172,-1000,355,21,-927,362,-89,853,-73,733,-612,-1000,609,1000,-327,-413,-1000,1000,325,-837,325,193,-952,670,-199,-39,520,210,1000,302,1000,-853,970,-984,-752,-805,-33,-110,-606,290,-22,387,781,138,-10,-405,515,-23,-863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:LTU=:19:java.lang.Byte:LTE3:19:java.lang.Byte:MTIx", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decode(java.lang.String):byte[]",
            new int[]{461,-275,-352,474,95,-969,-506,-153,49,820,-518,597,-760,74,-283,-231,-763,665,907,-53,52,749,581,-804,-631,405,-450,-898,79,-209,202,38,460,-53,-929,280,188,72,-611,-50,-563,636,808,-42,-199,984,-642,62,483,-773,822,979,-734,-811,-808,-933,-844,-109,652,523,259,-314,585,-724}));
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
            new int[]{796,-196,-313,-456,323,85,-617,676,614,995,-22,-670,-150,-811,149,933,279,135,382,-208,625,-685,36,-541,530,-440,990,930,-779,768,789,-711,-293,-135,-106,-50,652,766,217,-560,804,286,-548,777,-641,497,-112,-48,-56,867,-627,-263,63,641,-122,127,-216,-576,-274,-926,-796,-333,948,864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{1000,1000,710,616,10,-797,-1000,-960,486,679,1000,104,-177,-671,-224,757,571,-1000,-105,689,-1000,502,648,-5,-721,-178,1000,-435,-108,-876,-1000,546,-14,493,394,221,-20,-1000,-232,612,-650,-1000,278,-1000,-44,-140,51,-417,-495,880,492,-231,-900,1000,1000,-400,208,103,-28,364,818,752,279,163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(byte[]):byte[]",
            new int[]{1000,-393,-622,661,17,719,1000,894,-469,-216,-81,1000,-77,-1000,-215,-1000,1000,1000,75,1000,408,1000,1000,1000,1000,-1000,365,-1000,142,586,-1000,1000,-177,1000,893,1000,1000,1000,-1000,-1000,386,-1000,-1000,-337,-1000,526,-1000,-483,403,848,1000,-559,-403,-1000,-747,1000,1000,-904,798,-67,-781,-1000,-577,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:OTY=:19:java.lang.Byte:MzE=:19:java.lang.Byte:Mzc=:19:java.lang.Byte:NzY=:19:java.lang.Byte:NDA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{-67,-264,298,-340,557,-886,647,638,-427,-192,126,-930,-317,373,-581,747,-793,-756,-585,672,308,35,294,-553,745,5,372,462,-645,815,732,-365,-524,391,-351,-749,898,-857,7,-898,-480,-886,-502,-444,921,734,-743,461,-727,747,-251,859,642,642,-752,-646,-379,-581,26,-297,437,-79,515,680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{-463,-744,-731,-755,317,34,-142,-766,396,-103,94,971,-603,-38,974,-737,-895,714,-315,739,-636,-497,-241,-154,780,900,967,815,-659,-526,924,764,132,223,342,931,-366,361,-929,673,765,-515,657,296,-234,-799,998,71,-666,245,-131,-172,-42,849,579,430,-644,638,820,-885,85,-294,909,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTEz:19:java.lang.Byte:MTA5:19:java.lang.Byte:MTE3:19:java.lang.Byte:LTIx", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{309,821,1000,-840,607,-1000,1000,1000,-1000,219,-753,-978,-518,-461,-285,-569,-895,-285,-1000,828,-590,734,134,-584,870,464,722,-938,-447,1000,650,557,-167,529,656,425,956,-900,-794,-508,-72,-802,-305,583,1000,333,-1000,725,-393,1000,-1000,331,475,1000,-1000,-1000,-1000,-1000,-569,-231,-173,238,433,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("ARRAY:[B:15:19:java.lang.Byte:MTAw:19:java.lang.Byte:LTc2:19:java.lang.Byte:MzA=:23:java.lang.Byte:LTEwMg==:23:java.lang.Byte:LTEyMw==:23:java.lang.Byte:LTExNw==:19:java.lang.Byte:LTM0:19:java.lang.Byte:Ng==:19:java.lang.Byte:LTY1:19:java.lang.Byte:OTI=:19:java.lang.Byte:LTcy:19:java.lang.Byte:ODY=:19:java.lang.Byte:MTA3:19:java.lang.Byte:LTM=:19:java.lang.Byte:ODQ=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{-753,370,-425,-851,345,-95,975,-128,-759,876,555,-450,713,371,-984,211,-1000,561,-580,-308,-977,200,-132,-1000,-922,-441,631,-362,-858,9,-236,-969,-185,361,-108,-39,1000,-1000,-823,650,96,745,-239,-1000,660,-642,454,1000,339,877,-1000,555,531,1000,-1000,-582,-475,-1000,1000,-380,-972,111,-565,-660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:LTU=:19:java.lang.Byte:OTM=:19:java.lang.Byte:NTI=:19:java.lang.Byte:LTQ1:19:java.lang.Byte:MTEx:19:java.lang.Byte:NTg=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{661,-1000,-518,286,188,298,-975,-1000,215,656,1000,-866,810,-1000,481,416,-917,-828,-615,-145,778,-1000,-146,1000,-1000,350,165,89,-466,-1000,-1000,-1000,-95,76,-456,-626,-764,1000,-895,-627,-278,-522,43,1000,-160,785,-1000,-139,-209,-958,-1000,345,401,-962,3,303,726,825,1000,1000,-144,508,497,-171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeBase64(java.lang.String):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MjU1", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{1000,546,1000,229,-93,-929,-277,436,946,-259,-448,1000,598,1000,1000,58,787,-1000,1000,-26,-1000,-1000,-502,-1000,1000,921,701,-1000,-655,400,461,-245,-1000,-810,583,-1000,-149,1000,280,-122,1000,409,-536,255,-1000,972,197,-1000,1,-872,514,1000,-839,1000,-739,-251,4,-346,637,1000,1000,1000,-363,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{706,617,-314,229,779,-400,-378,152,191,-1000,870,285,256,504,146,-1000,1000,-1000,-17,989,-1000,-1000,-755,-1000,667,710,-121,-845,207,908,758,-37,-1000,-916,-1000,-400,290,400,280,-814,1000,400,-536,-160,-861,-631,1000,-208,-1000,-1000,265,-618,-47,981,-848,516,527,-307,-885,1000,719,785,-1000,660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{706,181,1000,127,448,-129,-277,1000,-950,212,-94,-1000,814,-240,1000,1000,-970,671,1000,-548,-1000,-1000,-580,-394,593,-1000,142,979,871,-1000,461,767,-1000,-810,402,-512,-1000,-346,945,-988,1000,409,-991,-30,714,-94,31,150,1000,802,1000,797,-1000,969,221,-251,1000,-1000,-450,-157,833,-872,-363,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "decodeInteger(byte[]):java.math.BigInteger",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("ARRAY:[B:13:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=:19:java.lang.Byte:MjQ=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{663,53,-325,956,377,-72,776,-121,-574,-828,237,-394,800,-1000,-1,-31,1000,-200,1000,422,-1000,-463,93,637,1000,1000,-1000,-1000,810,-926,-582,738,324,-1000,-764,596,-588,-1000,-695,-650,-162,842,-1000,-446,-1000,147,-758,-1000,982,1000,-114,76,-151,972,1000,151,-545,1000,-72,455,577,-1000,-476,-445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NTQ=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjY=:19:java.lang.Byte:NTQ=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{328,335,-155,203,1000,-373,-95,387,-1000,-659,1000,-13,-1000,759,-455,-608,321,-1000,1000,575,-544,1000,827,625,1000,-365,643,-153,181,642,-1000,1000,993,-496,1000,-110,137,1000,774,-936,-169,188,-652,987,698,-749,-227,-992,-1000,-544,-336,-185,-1000,-415,867,-439,-1000,419,69,-1000,-370,585,51,519}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-923,-309,-1000,688,564,-1000,1000,243,566,302,-276,1000,-1000,381,-976,-1000,344,-393,1000,-271,-878,-607,-135,-828,744,891,-129,-185,-1000,26,909,623,106,598,1000,-1000,-595,356,1000,-1000,373,-1000,-1000,-503,-538,304,162,693,-744,805,903,-77,-1000,531,-16,1000,227,-325,1000,-614,-1000,-715,-979,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("ARRAY:[B:11:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTM=:19:java.lang.Byte:MTIy:19:java.lang.Byte:OTU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{-223,479,1000,862,101,683,1000,183,570,-408,989,147,43,556,1000,-668,424,322,-15,118,-1000,694,1000,833,-751,-269,508,-207,790,296,-431,-729,-782,-164,1000,99,866,1000,191,-180,667,519,-738,647,68,-802,328,939,333,-1000,316,-779,1000,154,-742,-444,514,1000,588,-261,-412,1000,552,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("ARRAY:[B:9:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{622,721,-332,-197,-199,1000,1000,-1000,-427,-624,597,855,-1000,-1000,1000,-60,1000,787,28,-597,-688,271,597,-216,372,693,-278,421,1000,-1000,-357,-320,-277,-33,-1000,-1000,716,474,-1000,-1000,106,-460,-66,-337,-695,-663,324,691,1000,-65,660,949,1000,311,564,-873,320,402,-1000,-63,-1000,-704,-519,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTIw:19:java.lang.Byte:MTEx:19:java.lang.Byte:NjU=:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(byte[]):byte[]",
            new int[]{1000,-22,-866,-1000,-1000,-1000,1000,-351,-1000,-1000,-393,120,-232,282,-925,1000,1000,171,-435,798,294,628,976,115,1000,-1000,1000,1000,-1000,707,908,111,63,1000,-1000,1000,-57,-1000,-1000,1000,-406,1000,1000,830,832,-1000,1000,466,-1000,-884,-1000,837,-1000,-36,136,574,577,-1000,-1000,-1000,1000,289,-52,946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{-801,534,1000,-974,-1000,-558,-518,1000,736,-296,-286,15,-14,737,635,609,-518,-710,-177,1000,-413,-1000,-13,-131,401,455,-625,-1000,-529,1000,-363,-960,-151,-101,107,-520,1000,740,-1000,866,-527,1000,330,311,204,-600,-1000,-2,476,353,-741,1000,1000,-1000,-1000,268,449,-1000,-444,314,1000,305,-396,-900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.codec.EncoderException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encode(java.lang.Object):java.lang.Object",
            new int[]{-630,-394,-718,264,68,-667,-156,-56,-519,-995,900,597,-656,614,518,458,-475,-906,714,478,621,348,253,-531,170,575,507,-975,-247,-582,-581,423,-569,527,266,-554,482,30,-249,-394,-817,78,232,-408,-490,-849,-634,948,-920,-846,-672,958,361,-576,876,-500,283,-239,-967,-761,-294,316,899,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODE=:19:java.lang.Byte:MTAy:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{-169,298,-195,87,842,359,7,655,-566,627,-226,236,-109,671,497,-968,509,-232,683,-711,-991,830,-7,534,-482,-96,-100,638,995,-349,65,-359,-645,-658,-997,791,672,-670,979,-893,867,707,170,-104,40,912,99,147,-772,-585,-902,475,-219,304,-873,403,783,573,-323,-861,-32,235,648,-255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{-909,737,-706,181,-936,478,-921,-759,-796,-685,-939,10,-216,478,-534,7,476,-915,-533,446,-941,-316,331,-510,659,94,482,525,-674,124,-19,-970,-916,839,427,163,-240,517,597,21,-105,-868,279,668,799,312,720,496,452,38,-175,-724,-666,-928,-122,-697,520,-933,877,486,-587,636,-174,-82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTIx:19:java.lang.Byte:MTEx:19:java.lang.Byte:NjU=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[]):byte[]",
            new int[]{166,-237,-1000,-726,995,319,-92,58,341,1000,287,-841,349,-529,-52,357,726,1000,630,-649,-43,294,851,-355,-165,423,-1000,-325,-412,691,-1000,1000,-920,609,546,1000,-1000,1000,-814,793,-331,1000,79,1000,-1000,-379,728,1000,-806,558,339,804,363,-486,-715,276,-1000,-1000,-1000,1000,1000,251,571,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:MTEy:19:java.lang.Byte:MTAz:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{58,-909,466,768,-894,-98,553,235,765,-498,-596,281,-755,-642,58,-899,-890,-617,-830,776,729,-578,-703,-791,768,-703,795,510,54,-463,657,-901,494,-779,-40,-611,633,751,-422,-453,-76,-253,-733,536,788,-212,334,411,-841,244,703,670,370,-932,-596,355,-68,-913,-774,551,72,63,-359,952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{243,-615,-667,-986,644,440,-567,-661,837,-945,480,-969,-965,651,382,-97,-952,-619,33,-270,183,-688,156,587,-652,28,-177,-604,-524,549,-295,-331,-336,-280,-495,83,403,647,945,121,-325,-869,-809,-264,789,-449,660,763,379,222,-217,625,-842,-184,-509,-214,764,-845,-73,-658,-717,394,-777,166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean):byte[]",
            new int[]{-955,331,-403,-750,595,-386,-558,-2,-979,-575,297,560,388,-581,146,-690,-225,-144,553,176,525,-677,-793,628,735,-460,-924,680,-606,-47,540,-259,-106,-904,-741,727,586,-896,463,-487,488,129,-137,474,-682,952,442,-344,577,593,-994,-53,625,-654,570,-505,-652,357,415,-408,-642,555,946,-750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{376,-302,-391,-463,-186,-144,-204,80,-764,970,989,938,-334,-994,-831,-252,-317,363,-626,-615,361,-157,938,251,-441,857,526,-704,6,111,-899,-352,-241,-791,-116,91,-30,680,714,833,-361,-232,-168,-973,42,1,-985,315,862,31,283,-699,-287,-471,-107,326,692,-980,261,148,-119,462,612,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTAy:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{-783,-870,85,279,56,-80,-828,-411,794,69,-531,-345,758,451,705,22,-730,-461,895,-927,-988,-513,287,-374,-251,-494,-527,138,504,-612,-642,-4,363,257,-277,770,495,663,-611,503,669,-10,635,-889,592,-179,-568,-214,-272,-622,-687,-36,890,-565,263,-191,894,228,849,478,-205,780,37,478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:ODQ=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{575,765,-422,-167,-67,691,-315,-893,967,771,-300,-60,-495,605,-723,-1000,223,-1000,-229,-239,392,-790,1000,-1000,-263,-825,-1000,-43,-387,-1000,-469,-793,-338,-472,-301,-1000,-610,424,-778,-1000,-482,-231,961,-495,-272,-311,-149,-26,197,637,1000,-761,-1000,-24,1000,145,-740,-375,-723,811,470,62,1000,-870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("ARRAY:[B:9:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NzA=:19:java.lang.Byte:NzI=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{575,520,232,-464,-923,327,659,-893,789,-162,-674,-630,-768,605,-435,-965,590,733,62,-338,748,326,787,-176,-301,-722,-591,-267,-943,895,-469,-555,463,-966,-900,-852,-896,-481,-371,-789,-756,415,495,713,-272,445,-479,-26,729,268,-730,-761,671,-328,67,145,218,800,-404,811,511,-518,-866,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:MTE5:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean):byte[]",
            new int[]{1000,469,1000,900,-667,481,366,-850,-1000,1000,-990,-145,428,-162,257,901,696,1000,-615,361,1000,1000,-708,307,-412,-1000,596,-219,-133,-638,-815,38,1000,-1000,-337,-997,496,666,-1000,-822,-1000,-29,-297,1000,-16,-1000,-1000,-71,122,1000,-1000,-143,564,-720,-428,215,734,1000,-666,-1000,1000,34,1000,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{743,-393,-1000,790,-183,-181,-355,-643,1000,921,307,1000,895,144,-940,750,1000,-144,-471,820,1000,-947,1000,-169,-851,574,-1000,6,379,900,207,629,-1000,-1000,21,1000,-878,180,298,795,249,681,459,-930,-1000,257,107,1000,104,-852,43,-10,-367,-194,-1000,-218,-242,400,151,616,-59,125,-419,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:OTU=:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{483,-535,-1000,668,-547,-181,52,-913,1000,908,371,1000,1000,-176,-940,982,1000,-933,-804,713,1000,-947,1000,-2,-1000,1000,-601,-341,1000,583,-1000,346,-1000,-1000,-373,789,-324,957,840,1000,249,449,636,-973,-1000,130,374,852,-646,-1000,362,-10,-702,-830,-817,-298,31,-116,86,648,-373,618,90,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{-616,-355,-629,-443,-631,696,-756,809,-24,500,-406,-442,754,537,497,911,-64,-154,-323,270,632,171,556,-317,-666,76,-824,-159,949,-60,496,-545,-998,-613,-53,535,450,-943,820,644,-369,-73,36,-410,-729,-632,164,230,179,12,180,-950,-695,-827,-627,13,144,-992,63,818,145,950,-644,131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{400,-376,472,-182,-576,-135,698,-556,-1000,-1000,97,-826,-1000,-1000,632,-925,-1000,-194,330,-833,-1000,1000,-1000,648,1000,-1000,1000,116,-1000,-640,-438,1000,400,-230,736,-1000,859,840,-1000,-810,-161,622,-621,1000,91,295,-252,-1000,997,62,568,297,1000,291,348,491,-576,756,-308,-1000,138,-1000,682,-702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("ARRAY:[B:9:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTA=:19:java.lang.Byte:ODE=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{767,5,281,1000,-50,-789,283,-1000,1000,945,176,826,1000,587,-1000,-907,1000,-139,-690,983,1000,-1000,1000,-541,-1000,871,-1000,-267,823,634,210,765,-369,-1000,-51,1000,-1000,660,611,920,-318,1000,459,-1000,-808,-297,69,1000,338,-862,174,-304,-520,-436,-1000,-648,-766,998,-5,840,-552,588,-704,787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTAy:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64(byte[],boolean,boolean,int):byte[]",
            new int[]{1000,610,25,-596,-127,1000,918,85,69,-354,-1000,-1000,459,665,-60,596,258,350,960,-753,178,234,-947,215,1000,-420,59,-153,-175,-368,710,221,1000,365,-786,-866,-368,-1000,589,634,-1000,703,1000,530,-6,512,970,111,605,644,-729,-1,-1000,1000,1000,217,-1000,37,635,856,-179,106,433,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:Njg=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{365,-938,-898,-357,172,-651,341,-347,-920,-236,584,698,-564,739,956,801,756,74,-968,303,-40,-807,433,193,81,661,901,-521,-866,-1000,800,-656,-736,772,451,965,-758,759,677,-894,916,-883,550,836,326,-634,-606,69,842,-755,853,13,918,-389,-970,-937,809,775,557,-803,986,-976,-192,-483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NzU=:19:java.lang.Byte:MTAy:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{279,413,658,-871,-862,600,-916,-405,289,-381,-960,-529,675,930,936,-699,701,-878,-578,-775,874,-263,-982,17,-409,-908,345,472,-90,559,-431,-200,585,640,-899,-181,581,124,608,-46,844,560,-32,310,179,349,-115,404,-233,-124,-371,-428,-957,-739,564,-526,480,-449,977,-327,126,480,78,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("ARRAY:[B:10:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjY=:19:java.lang.Byte:NzU=:19:java.lang.Byte:NDc=:19:java.lang.Byte:MTE5:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=:19:java.lang.Byte:MTM=:19:java.lang.Byte:MTA=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64Chunked(byte[]):byte[]",
            new int[]{-230,-965,305,-31,144,743,-374,357,737,-469,-157,467,-516,-658,-407,-444,-80,-107,-463,130,299,-778,358,667,-30,-923,-232,-8,727,-233,-431,632,-652,-998,206,-409,420,111,286,205,148,-921,78,6,-94,831,-857,845,951,-649,949,922,956,-626,-459,-169,782,-380,-806,-770,271,858,-677,-648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.String:L3dBQUFBQT0=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{-679,-860,554,-919,-437,-163,136,109,-774,517,-48,-530,-973,-340,371,501,-480,775,974,485,-341,847,-535,-885,416,-192,979,-793,827,965,-271,-668,-960,-43,-424,-707,-467,-751,-929,-512,-472,444,-203,134,800,-250,424,12,-421,846,789,994,-939,63,531,-96,141,-671,-674,-518,438,629,623,-833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.String:L3dBQQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{513,426,-607,-239,-137,801,883,529,-292,991,-210,-618,615,930,-52,763,866,-747,551,155,-340,-50,-349,406,633,96,-224,-416,-530,515,-971,931,662,901,-283,814,980,323,-534,640,-848,-646,-882,-371,366,-923,-769,-918,736,-832,956,-698,696,-102,-756,-316,-640,-559,870,668,352,187,613,-787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.String:QVFBQS93PT0=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64String(byte[]):java.lang.String",
            new int[]{424,965,-455,749,775,-106,-639,585,656,419,510,367,-151,-214,939,-453,715,476,-266,-286,667,-95,919,-137,101,-165,218,-992,-9,739,545,113,520,-2,609,576,749,252,5,635,-873,-496,-573,-43,-106,-374,224,418,855,812,-561,-389,-125,224,646,972,505,-421,-522,595,-367,745,-474,-236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("ARRAY:[B:7:19:java.lang.Byte:MTIw:19:java.lang.Byte:OTU=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{-637,-572,118,-393,-340,361,330,975,321,-910,-856,631,-54,-745,971,581,46,614,439,311,87,-20,724,-199,238,509,380,-623,-806,-919,-941,-603,497,-364,834,426,-100,39,260,743,474,-731,725,850,-580,573,-805,-38,818,573,15,-619,-72,281,-925,142,-927,-546,366,-689,621,807,-300,960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NTY=:19:java.lang.Byte:NjY=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{-195,667,-303,906,869,584,373,51,215,654,-486,94,-353,-477,416,-128,967,361,126,401,-454,208,-911,731,726,611,534,716,677,-451,272,-54,-608,769,-153,-280,601,330,433,-809,-434,-567,-712,-430,962,-600,-904,-364,-677,-949,-871,-294,-66,695,-210,-322,423,393,788,-176,852,-713,0,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("ARRAY:[B:6:19:java.lang.Byte:NjU=:19:java.lang.Byte:Nzg=:19:java.lang.Byte:NDg=:19:java.lang.Byte:NjY=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafe(byte[]):byte[]",
            new int[]{-866,-787,-776,-35,359,-779,-791,-485,-584,-41,607,490,268,-175,830,721,-639,224,329,546,180,64,657,54,403,722,-59,940,-707,746,931,-20,52,705,125,-191,-53,-736,-126,-103,202,-154,820,395,-117,-988,-462,-108,-691,-881,-179,994,872,703,-281,-851,576,594,117,146,106,-237,416,317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.String:X19fX0FBRQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{695,441,-88,364,591,898,-21,-160,-819,-118,-11,431,495,-176,422,271,759,138,901,-210,-904,-533,-47,-947,179,881,-57,-859,-496,-768,509,-698,740,108,-196,511,379,-211,960,-579,-235,970,-694,705,541,87,107,294,495,585,838,-868,869,645,325,700,-717,-458,-475,-177,623,72,-886,-990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.String:QVJ2Xw==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{9,-856,421,275,298,-419,-388,-680,-79,809,506,-908,-685,-588,-338,358,9,-852,-851,-958,-350,643,6,413,566,-206,-620,192,859,833,-36,137,-220,379,370,538,926,629,-860,-570,-334,-681,-726,327,-876,-944,327,809,28,-783,-602,-628,-200,-292,697,-365,-654,-415,-286,-803,210,794,521,-759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.String:QUFBQV93", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeBase64URLSafeString(byte[]):java.lang.String",
            new int[]{514,115,426,-587,642,-634,-204,-182,-619,-937,409,-516,667,192,456,717,713,199,-798,-349,48,831,-347,-247,-949,-445,-602,555,-179,129,-401,269,963,-770,-896,803,693,887,-263,87,-500,945,-21,-395,532,-241,-560,546,-119,898,-209,99,-46,750,-821,65,-729,633,502,-823,-846,-759,-591,314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("ARRAY:[B:8:19:java.lang.Byte:NjU=:19:java.lang.Byte:ODA=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NDc=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjE=:19:java.lang.Byte:NjE=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{1000,635,-1000,-645,-706,45,-999,-1000,-462,399,1000,-291,-1000,-852,347,223,-217,-1000,-1000,1000,-1000,1000,-439,1000,-1000,-1000,1000,-549,-281,1000,38,-268,1000,-506,-2,-424,1000,1000,418,-549,520,-244,-394,-1000,-1000,481,641,-767,-213,1000,-291,1000,745,1000,1000,-6,-492,1000,-847,579,1000,337,-338,906}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:MTEz:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=:19:java.lang.Byte:NjU=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{1000,82,-1000,-1000,-856,851,165,390,-107,1000,-1000,-1000,-697,-45,-1000,-127,-628,-1000,-944,-9,-341,-197,204,-878,-18,-568,253,481,333,1000,-1000,691,-845,816,322,-107,413,53,-979,-221,-446,-426,856,-826,-396,357,-909,1000,1000,1000,-1000,569,-96,1000,-174,-1000,1000,-697,905,-1000,1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{-206,376,-466,732,859,827,846,-41,-677,375,520,238,-496,-950,-575,-643,805,-842,-848,-294,-454,-75,156,-833,60,-543,868,-267,-714,-872,457,387,222,-331,740,815,547,655,319,-106,-515,-82,459,-406,552,441,-560,901,222,-257,146,-303,723,435,549,-853,-783,687,343,-940,-602,-345,-466,-34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeInteger(java.math.BigInteger):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.String:LzJRQS93PT0A77+977+977+9", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{-348,-502,713,-1000,1000,-1000,-677,-511,-650,204,-1000,-826,-1000,897,1000,-43,5,1000,-398,1000,1000,650,-898,-1000,1000,-39,208,271,1000,-658,-740,1000,1000,897,-1000,177,-276,-926,469,-1000,-1000,952,812,1000,750,-477,-288,66,-1000,-844,613,328,-1000,517,-839,-803,-844,-1000,1000,635,-1000,-507,90,260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.String:QVA4QS8vOD0=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{-735,1000,1000,-361,-1000,234,-446,293,1000,1000,34,867,666,-1000,467,1000,-498,-641,-488,683,-365,1000,-1000,1000,-173,325,590,749,707,-1000,-116,1000,215,1000,-1000,1000,524,1000,592,-912,1000,-1000,1000,-1000,1000,-1000,-762,-1000,1000,-1000,204,-40,1000,26,77,-1000,-857,-1000,-81,1000,1000,-1000,1000,-449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{203,716,806,421,540,-517,781,656,1000,-309,809,541,255,1000,452,1000,355,-1000,-1000,919,-863,-751,-746,1000,-1000,-92,858,205,-1000,-1000,352,15,-1000,1000,393,183,-407,558,977,366,136,-1000,1000,-1000,155,-488,-816,-670,-106,-1000,-61,767,1000,485,-567,-508,-41,-397,916,-54,558,-279,253,-279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.String:X3hEX0FB77+9AO+/ve+/vQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{19,764,698,-553,-944,1000,-1000,366,900,-970,10,1000,-1000,-1000,1000,1000,-869,-1000,16,251,450,-436,661,1000,384,-994,-51,-159,290,-1000,-804,-587,609,931,-444,1000,896,266,528,36,-75,-507,506,-413,-1000,-4,-1000,129,810,-511,1000,-128,-19,-1000,-242,-914,235,-565,505,649,1000,-1000,12,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.String:eFBfX193QQ0K", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{119,-9,32,276,485,-572,-469,953,-835,-1000,530,-631,-1000,1000,510,-489,661,797,-115,300,446,-1000,-931,-685,-1000,120,621,-211,-842,219,581,-297,-1000,-693,87,-703,-519,10,-460,1000,-989,-141,137,928,-945,447,748,229,-1000,-366,-155,131,150,300,491,249,645,349,-223,-637,-732,-135,-693,501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.String:QVAvL++/ve+/vQAA", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "encodeToString(byte[]):java.lang.String",
            new int[]{124,-622,1000,44,856,25,-202,400,434,1000,-893,1000,102,836,471,1000,-30,-245,-1000,1000,230,178,-1000,-1000,-1,-116,1000,844,1000,-1000,-1000,1000,-233,1000,-1000,214,-672,-413,1000,-1000,-66,565,1000,80,819,-692,100,-701,-683,-624,333,1000,-147,1000,-1000,-1000,-1000,-1000,1000,811,681,181,128,97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{1000,671,-578,-57,529,348,-655,-887,526,-210,428,-197,-75,-616,-1000,361,1000,-1000,300,1000,-892,-269,983,985,251,-1000,-671,-24,198,-827,-1000,-151,-675,355,557,570,190,-1000,478,-275,383,908,0,654,125,117,1000,219,56,-247,-465,-1000,234,140,-661,-1000,-358,500,-328,-349,272,1000,657,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{949,321,-578,1000,521,-409,-747,-870,-107,414,-273,-435,-75,982,-623,-250,487,-255,300,1000,-1000,1000,734,119,-601,383,-932,751,-1000,53,-1000,23,90,-500,-377,1000,-1000,-394,1000,135,-124,1000,193,1000,-1000,183,1000,191,-1000,-1000,-516,-1000,-244,1000,876,-838,407,547,-1000,1000,913,849,684,51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{100,1000,574,-359,-187,-225,1000,-257,-1000,316,-134,-1000,-994,-1000,-815,821,-818,605,-874,-928,221,-1000,1000,-1000,771,-874,163,1000,-1000,256,655,1000,-1000,-723,1000,-256,1000,-178,444,-684,-1000,-126,470,-980,1000,-193,711,1000,-1000,430,1000,20,-160,540,1000,277,811,-118,407,-58,618,-329,469,683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isArrayByteBase64(byte[]):boolean",
            new int[]{301,891,887,-476,-513,639,1000,1000,-616,1000,-796,1000,-354,-470,201,-400,-413,400,-1000,984,213,-1000,248,-1000,-995,-104,1000,1000,-291,-91,-1000,-400,-180,-356,-1000,1000,299,1000,1000,-80,159,-881,-811,-1000,285,63,286,-210,-1000,-724,101,-359,710,78,-412,-1000,568,224,234,194,449,277,160,759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{-974,-49,-204,-369,509,424,691,446,885,-147,-288,943,578,-425,944,-600,224,738,596,-651,-861,762,278,-915,-706,-480,-118,209,26,198,919,469,-382,-780,600,397,428,-810,-736,813,776,846,849,-891,986,227,24,444,-883,-108,323,-555,-261,-95,-835,822,870,-487,3,-558,-355,-16,998,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{127,983,-1000,-400,-400,-400,1000,-457,-624,-3,-1000,254,854,-400,-228,400,-509,1000,-87,226,-311,-12,-1000,-1000,960,833,1000,230,400,-322,825,391,400,-502,126,1000,-966,-400,-603,-1000,546,400,-404,-969,-368,400,-1000,-644,876,-1000,-1000,-400,-93,1000,-199,-1000,326,-400,916,-400,555,-1000,-426,451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte):boolean",
            new int[]{361,794,-872,661,-570,529,338,-23,-203,-167,191,-230,206,-963,-471,-147,-516,-694,-796,-639,-678,903,925,-760,390,503,-967,967,-252,20,256,-249,-633,-222,961,-319,-791,-534,713,847,-258,-682,-924,337,52,-791,-534,195,769,968,-357,-98,-95,-924,925,771,425,-826,-410,757,844,288,90,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte[]):boolean",
            new int[]{-921,769,346,522,-94,628,927,912,274,-817,864,702,628,-673,-953,-503,491,365,210,-229,-782,868,187,176,349,576,949,-983,-266,-20,904,-699,723,-116,323,960,-126,507,214,653,-110,505,-518,890,760,664,-207,130,-513,958,206,-22,-836,-843,340,624,-332,259,-392,-702,809,612,882,632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte[]):boolean",
            new int[]{44,1000,442,1000,-812,-275,400,1000,775,-1000,595,77,521,998,-1000,26,-39,-214,897,-544,-161,-824,746,-624,570,-1000,-148,1000,-270,-17,-97,-695,1000,-637,1000,1000,-1000,227,-1000,371,139,1000,134,132,-1000,389,-621,-344,886,122,-1000,280,1000,-1000,893,-532,-1000,584,-1000,208,1000,-87,239,-980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(byte[]):boolean",
            new int[]{205,1000,442,1000,-1000,461,317,785,502,-417,537,-928,1000,766,-1000,-417,227,-1000,1000,-569,-958,-1000,496,-1000,147,-994,807,854,-957,-327,-340,244,1000,-786,1000,837,-895,1000,-1000,464,-236,1000,407,811,760,889,-1000,-124,-513,-140,-1000,503,-836,-568,1000,-1000,-1000,70,-1000,-702,1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(java.lang.String):boolean",
            new int[]{575,-1000,1000,-459,-332,-161,-1000,1000,1000,984,188,137,93,-1000,-291,-990,-963,1000,89,1000,1000,315,290,-358,-803,708,341,115,568,812,-461,622,217,-657,385,264,-1000,1000,-114,-467,261,-293,-384,320,1000,909,-240,-428,-463,846,873,-1000,922,935,261,65,-593,4,-1000,83,1000,-481,-890,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isBase64(java.lang.String):boolean",
            new int[]{-918,760,298,765,-815,636,-466,-38,-466,-604,-919,891,-996,-440,-459,119,806,-293,-698,873,-313,120,-917,-342,-815,783,478,302,968,9,292,-285,-131,-378,336,814,817,-198,204,-248,-448,-560,531,923,-257,190,241,13,919,250,-622,806,506,-616,-744,-591,-837,-354,-147,110,-394,893,676,145}));
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
            new int[]{-469,-241,765,334,566,-381,417,168,-376,-142,-591,183,379,-77,568,260,773,-399,404,72,618,344,574,-533,-880,-404,-571,-551,-346,-750,787,655,980,275,633,-946,-193,750,804,726,-897,81,-133,983,-111,13,-616,-147,-628,-813,558,17,-765,470,-154,619,655,-946,543,-892,-818,413,571,-793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.codec.binary.Base64", "org.apache.commons.codec.binary.Base64", "isUrlSafe():boolean",
            new int[]{688,-754,103,212,880,992,177,950,-100,83,693,830,445,890,-16,162,-244,-925,686,-578,-144,-253,-192,-207,924,142,881,-145,-826,535,367,892,524,-14,126,133,986,699,20,-699,271,363,-37,-672,391,-377,652,818,174,8,-966,-115,-407,102,174,383,511,519,-427,224,-930,-670,-760,-555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read():int",
            new int[]{548,-925,-670,-181,-700,-159,259,94,-449,-12,-463,524,-982,788,966,-559,248,908,747,243,-875,755,128,-464,475,-688,920,152,625,394,559,818,-38,100,-66,-400,886,-443,-221,144,-127,-952,61,205,816,-917,-491,-983,280,-961,-811,-400,-137,648,868,314,466,-238,-205,656,-41,-384,495,-420}));
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
            new int[]{833,59,-591,447,199,-657,-894,0,0,0,-1000,430,-702,44,1000,162,666,808,-639,-486,1000,-106,246,1000,1000,0,236,49,1000,699,456,-123,-1000,1000,411,-629,356,-484,890,-1000,708,-1000,239,-625,467,-985,968,122,-1000,1000,1000,815,195,-35,0,-16,-507,-589,-306,-870,-143,0,-1000,771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-642,47,-1000,-698,529,1000,-1000,1000,652,-289,-1000,112,85,-479,-903,1000,-1000,315,-1000,-79,449,-682,320,-1000,280,877,-1000,-1000,-55,14,-200,-1000,-263,-1000,-4,973,1000,-645,-1000,-637,-348,-1000,-1000,-91,114,-741,647,-224,1000,-258,1000,-365,918,-703,1000,444,-933,-847,-392,1000,777,294,195,-850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-598,-15,-389,-8,831,-405,-238,-770,943,-508,-590,-967,225,302,704,884,68,116,-343,936,-481,-18,54,-389,391,-400,259,-735,186,49,108,175,761,-153,-236,129,123,-259,-774,485,-720,233,-57,-252,-271,36,948,406,-223,-576,823,81,-315,-272,-131,-143,374,-862,-94,-899,331,-630,279,-977}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{744,-1000,-465,-142,671,-1000,-1000,1000,166,-1000,717,-493,-1000,1000,-1000,-99,-1000,-624,328,764,578,278,-528,-1000,-371,975,220,996,94,763,188,-158,1000,-192,-303,89,1000,-297,14,-144,-802,-672,-1000,-149,863,250,-152,-216,-29,624,0,-1000,296,-1000,1000,942,-505,-269,173,1000,-1000,-107,-1000,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{177,47,-401,291,-1000,1000,-1000,543,-986,41,-1000,504,85,-479,267,1000,-1000,-295,462,644,1000,-1000,1000,1000,-467,877,-1000,-1000,-188,-429,-69,-1000,298,-611,-342,1000,340,-181,257,-1000,-348,-739,35,-244,-665,-1000,108,155,1000,-552,620,-293,-17,-533,-152,926,-668,-344,322,982,785,667,73,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{163,-1000,911,729,221,449,1000,1000,179,1000,717,522,674,102,-21,-403,-323,472,186,-1000,102,-620,-137,814,-1000,1000,-1000,-701,-1000,-8,-867,-1000,293,221,353,299,610,-493,488,-1000,426,-492,918,301,-1000,-517,-159,100,-215,333,-602,-323,-254,1000,1,-797,-555,1000,51,1000,616,822,-1000,-494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{-843,-211,-776,395,303,725,-1000,900,367,209,-837,1000,-49,-74,497,148,-145,-751,-205,-782,1000,-682,440,64,659,1000,-1000,-817,-374,240,-659,-1000,-193,-1000,380,632,607,-90,-619,-130,546,-644,-56,553,109,-848,690,-41,1000,-1000,959,-617,494,670,265,587,-607,-130,451,1000,637,-175,-736,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.codec.binary.Base64InputStream", "org.apache.commons.codec.binary.Base64InputStream", "read(byte[],int,int):int",
            new int[]{374,-1000,1000,205,79,940,335,285,-1000,-229,-1000,496,762,-991,841,53,-838,1000,-409,186,371,-1000,1000,1000,-113,33,-656,-131,-1000,-576,-261,201,-1000,-18,528,1000,431,1000,-376,563,457,379,259,425,-1000,-1000,-124,-697,950,-173,-1000,-549,-1000,594,-11,1000,-766,934,-64,1000,395,454,-1000,-585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{82,-442,-102,144,-21,57,495,848,-918,20,-296,273,-625,491,-382,-728,-641,24,895,-846,319,-28,83,417,-562,870,642,56,-169,756,230,592,192,-896,976,-449,948,944,179,822,-446,681,164,-980,887,177,-138,830,-441,693,-904,-875,-558,-529,-469,601,-354,931,-245,-361,-183,-305,90,181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{-772,-71,629,739,672,58,769,538,67,-331,995,-488,449,-772,589,-411,940,-361,-697,267,-706,937,93,-977,-778,-651,-542,649,-244,967,-395,566,-519,120,-194,-258,-298,416,65,559,-683,389,464,-336,524,-307,417,-326,344,-115,-520,446,380,-50,-697,549,253,957,890,573,690,487,377,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "close():void",
            new int[]{266,56,-186,-310,-521,941,-496,-937,911,228,164,-602,578,41,-28,-395,-708,-959,-446,-933,986,-544,-412,-612,551,-71,568,124,-418,-900,-877,-797,97,717,478,-627,-551,906,319,-313,413,258,-351,-230,744,-668,-958,407,393,319,131,857,-24,-299,101,-771,-871,993,-456,-778,-86,872,-928,602}));
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
            new int[]{-600,641,-821,-857,-807,82,388,-716,-507,279,-828,-76,-81,491,1,-560,568,81,88,-884,586,494,-817,-82,633,226,-471,333,507,975,965,-686,742,-957,-885,-97,-229,-234,-252,-552,373,251,-17,-486,559,-970,811,558,786,364,-436,-774,-541,-119,-558,-266,29,-992,-51,-777,-296,198,-773,187}));
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
            new int[]{904,-307,-327,-1000,134,419,163,-1000,-102,638,642,186,-439,-1000,1000,-406,1000,524,-491,69,1000,-610,-1000,648,804,817,-339,1000,419,797,313,1000,1000,-477,37,337,-63,-81,434,-355,-1000,157,-453,-199,1000,184,-1000,-68,611,515,2,513,1000,668,792,992,-158,-120,-1000,29,229,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-216,-285,1000,346,-1000,1000,-1000,-171,738,-192,-157,-1000,-923,-71,-1000,-333,116,-1000,964,-388,132,-148,445,-340,410,334,875,356,-468,-970,-286,146,299,829,1000,-57,-724,-348,282,-912,840,482,888,-759,-319,365,-919,366,-144,-401,803,-162,444,-1000,-266,-271,-1000,-1000,-609,-87,-478,-849,1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-414,-13,-129,-835,-344,1000,149,-900,-622,1000,623,-514,-494,177,832,-193,1000,-511,-972,324,838,16,-768,178,505,881,-720,1000,1000,911,-7,-241,1000,-346,840,772,-52,912,-127,1,644,1000,-838,-199,787,113,-925,296,6,732,-347,-773,136,349,32,292,307,-249,-487,-533,170,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-1000,-1000,575,-855,-444,1000,-1000,-1000,628,-1000,190,-531,-690,129,581,751,-816,334,1000,-1000,-1000,-1000,838,-9,201,1000,1000,132,1000,75,-1000,-14,-517,1000,276,-324,821,-925,1000,1000,-695,-140,824,-1000,-614,517,-484,-393,141,240,485,-1000,-1000,-432,-576,-285,-984,-1000,18,1000,-1000,495,1000,978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-437,59,350,-343,4,719,-610,-607,571,-45,-260,391,-39,-231,-672,-67,-819,747,832,-983,-806,-345,838,365,449,924,227,-77,542,-222,-422,209,803,571,83,110,-55,-964,970,242,-900,-449,769,-187,-487,281,-146,112,-8,142,-302,-827,-221,-153,-21,-39,-39,-981,291,512,727,341,570,853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{-414,-1000,-402,-1000,-561,-645,-775,-739,-753,-476,-254,125,25,1000,359,-655,46,656,886,426,-1000,199,-932,157,856,242,239,-599,-551,-652,396,634,86,276,-246,-662,1000,-206,10,722,747,1000,-32,-1000,317,0,48,-928,109,750,1,389,-720,-412,400,662,139,-7,-954,324,-209,329,21,163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(byte[],int,int):void",
            new int[]{676,-1000,-529,-7,180,1000,-819,-1000,233,-103,-11,-1000,-891,-840,413,414,-976,-744,-523,-172,-215,30,838,753,380,752,-83,1000,-49,414,519,1000,1000,-25,-292,309,655,114,318,-548,-933,-388,1000,417,387,770,572,-664,513,127,-192,0,1000,401,855,378,605,-867,-14,679,460,-11,735,428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{1000,-295,-33,-357,-130,100,-111,901,126,35,-346,-77,384,92,-56,811,1000,528,930,946,-847,-961,-290,-748,-127,-1000,-62,-345,32,-544,1000,-393,1000,448,-1000,138,552,1000,-502,1000,163,1000,-434,100,-99,518,1000,595,644,-1000,-775,349,-393,-208,237,45,-583,651,-265,326,-568,41,298,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{-97,-684,-745,572,-309,483,819,-77,-597,335,-6,-601,34,722,111,496,-636,25,-783,851,734,849,439,557,398,428,938,-654,-536,-687,347,651,625,312,-122,-647,830,-555,-561,-787,851,-297,36,-593,-687,764,501,-595,155,-475,-843,-528,724,-710,429,515,751,-254,-435,-313,301,-483,-286,-811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{722,221,257,874,796,1000,307,1000,396,-1000,-807,906,869,-99,538,74,-325,-904,1000,-399,-700,-868,230,178,545,-1000,-1000,454,1000,-65,-395,-684,394,974,-397,15,1000,763,-1000,466,271,885,-490,-654,-325,5,863,-864,269,742,478,-867,-925,-81,415,-996,-1000,539,310,800,-863,-410,1000,-115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{296,-1000,-629,447,217,1000,369,-331,954,673,329,243,261,1000,-456,96,-1000,-484,-1000,-5,725,-710,-645,-23,258,254,527,-1000,-893,-1000,-986,-100,829,-225,235,-285,454,-394,-1000,-140,974,-660,-207,-670,832,647,-83,-1000,-435,251,-301,665,1000,-623,97,904,354,945,58,534,1000,-1000,162,-74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.codec.binary.Base64OutputStream", "org.apache.commons.codec.binary.Base64OutputStream", "write(int):void",
            new int[]{-462,512,-600,175,-801,503,-745,-11,780,167,-538,455,-745,-930,-150,-791,-185,599,-219,406,-422,357,283,109,692,-215,130,840,404,632,-699,377,-140,343,-410,659,340,153,248,-147,-538,750,514,92,475,736,629,-854,-839,791,507,405,-860,-557,630,-60,-644,654,909,116,-576,-945,300,266}));
    }
}
