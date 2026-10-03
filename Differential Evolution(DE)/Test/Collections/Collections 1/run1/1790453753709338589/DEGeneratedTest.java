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
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clear():void",
            new int[]{18,129,-686,-1000,262,869,-1000,829,665,-752,-1000,672,972,-994,-1000,719,-731,427,-1000,569,-494,-796,207,-603,1000,365,1000,1000,678,365,-858,644,1000,-881,740,-687,-1000,-990,-159,447,-1000,-1000,-535,333,232,-1000,151,-257,-620,-1000,-1000,-1000,-116,-1000,-649,1000,-1000,245,613,316,-503,420,451,187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clear():void",
            new int[]{-504,333,399,1000,-906,-31,-18,-836,-924,341,963,305,-396,246,-295,39,-1000,123,273,-233,-823,-603,-384,402,1000,819,-890,-215,-658,488,397,-166,-956,1000,-303,-656,1000,1000,880,904,249,120,-640,-360,-841,-756,169,-9,264,-156,611,94,-958,577,-236,1000,692,17,-364,969,290,-25,354,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clear():void",
            new int[]{-893,-515,-433,-56,-100,-637,1000,-947,-1000,-583,-1000,-477,84,1000,265,-945,-65,-199,1000,-361,1000,316,432,829,-1000,-372,-98,-1000,-711,-1000,1000,-342,-102,469,714,-1000,351,1000,723,-481,-1000,41,-649,-1000,-328,1000,1000,-1000,322,-41,100,1000,-602,-1000,-927,-552,225,299,-609,835,150,610,-144,-393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clear():void",
            new int[]{-939,-471,-653,112,500,-155,917,-281,-612,-561,-1000,-1000,839,1000,-88,-745,465,1000,1000,1000,53,1000,1000,-222,-338,-1000,581,-272,-1000,1000,586,-1000,-213,1000,-651,-1000,-11,1000,1000,1000,-1000,-459,-648,-758,-926,280,1000,-1000,450,-429,-117,1000,-1000,-489,260,-534,312,517,-241,555,-1000,910,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clear():void",
            new int[]{-25,486,-107,-379,-385,612,-942,326,816,-222,-580,672,107,-1000,-607,1000,-1000,16,414,-277,-715,-1000,-1000,1000,1000,1000,-107,625,1000,-305,-917,1000,449,-881,983,-446,-348,-990,-513,210,-108,-1000,-589,164,181,-1000,-461,-567,-235,-526,-368,-1000,-116,-260,-1000,1000,-518,-173,1000,628,345,483,375,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clear():void",
            new int[]{-952,771,854,1000,-708,493,-1000,-1000,-1000,-144,834,-608,-148,186,437,598,-1000,70,-1000,-62,-1000,1000,-531,158,495,719,-1000,-1000,-1000,-47,387,-1000,-1000,1000,-1000,-731,992,1000,1000,-308,-565,200,-183,-785,-1000,-1000,62,-1000,217,-628,-207,434,-343,1000,-677,-1000,1000,-1000,-234,1000,145,259,1000,734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clear():void",
            new int[]{-848,-785,-28,-509,114,319,1000,-103,-1000,197,-989,-797,624,590,1000,304,936,1000,701,1000,774,260,564,-186,-827,-1000,345,-792,-1000,-1000,1000,100,-112,797,-1000,-1000,568,964,870,1000,-624,208,124,-302,-866,-766,252,-1000,1000,391,-1000,1000,-664,-1000,553,-1000,-108,192,-241,1000,33,442,1000,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clear():void",
            new int[]{-931,333,564,1000,-906,259,889,-54,-1000,439,356,479,-156,-106,600,929,-580,-101,273,-233,-381,769,-945,1000,-194,819,-1000,-974,-696,-839,397,-493,-1000,13,-879,-435,1000,775,653,1000,443,206,79,-644,-400,-756,-470,-1000,264,-200,162,374,95,1000,-609,-912,54,-1000,156,1000,481,-59,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clear():void",
            new int[]{-697,-489,876,679,-70,249,-117,479,1000,-36,-621,-419,481,-148,700,724,-241,-837,-126,-409,857,-1000,-716,-1000,-68,1000,-942,-541,80,-1000,-993,581,-768,-1000,-185,-227,842,-1000,-687,414,604,-188,-128,-614,246,-1000,-887,913,647,-29,243,-315,1000,-1000,-1000,924,-1000,-1000,52,-855,1000,-1000,597,479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clear():void",
            new int[]{-31,25,-653,771,138,-375,-59,176,508,-658,-1000,33,897,1000,-698,-542,465,-143,428,258,-160,-288,228,211,-319,832,-394,284,562,119,-1000,422,181,-431,951,-90,-528,-148,841,-779,-753,-459,-1000,-1000,479,-143,1000,-413,-491,-672,690,166,-193,-1000,-1000,1000,312,-312,-241,36,92,478,-264,305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clear():void",
            new int[]{58,-769,-390,138,769,-345,818,-352,305,678,1000,-124,489,-588,-1000,-244,-35,877,384,65,-795,789,1000,-835,1000,-1000,760,604,-90,467,683,833,279,1000,294,-494,-78,1000,773,1000,565,46,-590,333,232,1000,-54,1000,-620,726,-128,68,-1000,-1000,828,1000,102,1000,613,658,-741,-169,-772,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{1000,49,-187,-141,-785,597,123,-341,-980,959,-276,-811,-286,331,-1000,-238,-145,-955,79,915,-1000,-675,567,-103,1000,586,-79,-847,-817,-880,1000,-307,-870,883,-362,-940,-695,823,733,766,-899,169,384,600,459,583,1000,327,-1000,182,614,-95,440,-921,1000,924,816,895,287,1000,858,1000,-852,284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{-164,-3,-507,-417,392,-172,-1000,547,1000,-139,1000,918,338,-1000,43,530,189,1000,-1000,-533,69,991,-1000,218,-584,222,-1000,-250,342,-1000,-857,90,558,475,1000,847,-1000,-494,398,-1000,425,1000,-36,114,-604,163,-373,-88,730,-105,1000,-44,-195,182,-720,-615,-211,-771,-824,-162,-821,-1000,588,-14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{503,-429,-536,-57,165,322,156,-105,-1000,762,394,-760,1000,475,-418,-382,1000,1000,814,446,-781,-796,1000,-196,229,701,-341,-292,-43,708,1000,-848,-167,1000,-257,-1000,104,530,302,-215,-1000,710,917,-1000,931,-97,524,525,-523,-703,1000,1000,807,-236,975,-216,1000,-304,1000,-30,759,-232,-453,-740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{-209,955,-198,1000,-444,-309,1000,826,-1000,138,-1000,-417,374,278,-1000,-523,-67,-1000,1000,4,-492,-73,1000,-1000,1000,-133,-311,-1000,-1000,1000,1000,1000,80,975,-1000,-632,659,1000,-229,1000,-1000,-1000,1000,888,1000,1000,436,174,-1000,588,-412,-1000,-23,-1000,1000,1000,920,1000,441,758,733,1000,-1000,-835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{632,393,40,559,-469,-729,678,408,-1000,1000,-859,-1000,684,-275,-428,82,967,-1000,422,-224,-874,-914,1000,-1000,1000,-151,77,-618,-415,1000,1000,-722,-667,1000,-418,-1000,673,173,1000,822,-926,762,-1000,-1000,893,-351,1000,1000,-1000,-587,871,-900,181,-271,1000,115,64,-139,1000,909,742,571,-1000,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{-1000,43,-424,1000,-928,-1000,724,-425,-739,-1000,-739,1000,714,-181,636,-178,190,-1000,711,-1000,781,1000,524,-709,1000,-1000,834,-1000,-554,1000,887,1000,1000,696,-1000,1000,716,-1000,-1000,1000,-1000,-1000,1000,112,1000,1000,-1000,114,-407,1000,-956,-1000,1000,778,-819,1000,-734,1000,-1000,-373,-645,1000,59,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{757,64,-196,-1000,-856,-412,-108,400,-470,721,-405,-1000,-507,706,-875,-385,-224,52,-903,483,-1000,-592,-581,1000,996,1000,-343,-800,-457,-1000,-225,-1000,-1000,349,251,-694,591,203,1000,-4,-280,302,-53,-400,-456,-951,1000,338,878,-66,1000,310,698,-714,628,-163,776,-585,1000,45,895,274,-407,517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{33,-215,-691,-1000,-563,1000,-344,-1000,776,287,815,1000,204,-1000,-1000,-168,-660,1000,261,641,-535,304,-1000,1000,1000,1000,1000,-1000,-1000,-1000,110,576,-953,1000,420,1000,-1000,741,-236,948,-598,-1000,-331,1000,872,575,-809,-697,-588,1000,348,407,-54,-1000,-182,1000,1000,782,-833,-906,-289,772,-381,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{1000,317,-183,-141,-862,597,393,-484,-1000,959,-287,-717,-571,331,-1000,-404,-127,-955,369,915,-1000,-1000,567,-453,1000,659,1000,-961,-817,430,1000,-307,-1000,1000,-362,-922,534,848,804,1000,-1000,-400,384,1000,459,-282,1000,530,-1000,182,558,-147,458,-921,1000,924,905,1000,1000,294,908,1000,-1000,628}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{-1000,577,-836,149,482,-224,-56,25,140,-694,1000,1000,257,-1000,1000,239,-74,400,-150,-925,624,1000,-1000,-496,-1000,-534,-400,-319,1000,-880,141,717,1000,1000,38,1000,-413,-160,-594,695,-987,-852,378,932,18,591,-1000,48,211,576,185,-265,504,727,-1000,1000,-946,-170,-1000,-438,-1000,-1000,989,-337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{73,-491,-597,432,-1000,-41,508,-877,-629,811,-419,-471,549,-295,-646,45,-2,-1000,643,-516,-645,354,430,400,681,398,1000,-1000,-861,341,1000,511,-1000,1000,-483,-266,-146,1000,322,1000,-1000,-360,901,400,402,831,244,-115,-1000,-138,558,-578,-142,-541,493,1000,722,933,332,572,92,676,-736,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "clone():java.lang.Object",
            new int[]{757,841,821,533,583,-412,-213,683,234,162,-524,-1000,-148,-675,-127,567,739,-1000,33,74,-285,-627,1000,-1000,294,861,-226,372,243,872,1000,-343,-99,-539,327,-334,-275,-1000,-77,36,580,655,-888,-641,19,-46,926,855,-919,-66,378,308,-1000,-44,782,-455,-552,163,425,973,823,-386,314,-191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsKey(java.lang.Object):boolean",
            new int[]{-42,441,323,-237,376,-270,670,-481,-8,201,1000,-157,274,298,1000,-596,-952,209,392,-512,1000,-163,394,-314,207,683,120,204,-869,779,862,-430,553,-1000,454,-156,44,-1000,-1000,958,-90,-85,-556,843,-372,312,-151,-903,-1000,-78,48,541,-868,559,336,-387,-1000,-417,-1000,764,-8,-301,-178,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsKey(java.lang.Object):boolean",
            new int[]{762,287,-251,-163,1000,-442,1000,-715,1000,618,634,1000,-46,-923,1000,408,19,-130,-1000,-51,-809,415,276,-197,1000,-956,-1000,-1000,816,-760,-131,-409,240,-746,-531,1000,1000,-1000,-140,1000,1000,-173,-1000,-166,81,-784,-150,-391,257,1000,1000,636,-185,-670,766,-1000,-39,-494,1000,74,-313,-618,-195,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsKey(java.lang.Object):boolean",
            new int[]{1000,27,367,-98,1000,4,1000,195,946,234,1000,-1000,-836,148,849,-822,-1000,1000,-1000,-349,1000,-156,1000,961,-269,1000,483,731,1000,1000,113,-196,1000,-745,100,792,963,-1000,520,23,1000,-1000,-797,421,-980,1000,-1000,-1000,-1000,1000,1000,1000,782,1000,795,1000,1000,-1000,-1000,1000,-900,-362,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsKey(java.lang.Object):boolean",
            new int[]{691,-207,-529,1000,547,-468,453,412,183,-1000,201,30,-197,-604,-1,283,-542,-237,-375,-145,113,-156,473,572,840,970,690,642,338,88,-967,-663,190,-390,354,-833,900,-387,817,-859,471,69,-471,493,-735,320,-58,-485,-587,731,-237,-50,-746,199,-1000,-166,579,331,-329,756,-545,-954,109,-752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsKey(java.lang.Object):boolean",
            new int[]{-847,773,499,-1000,48,-734,849,1000,728,811,-416,-953,727,510,-249,43,1000,1000,333,-1000,-562,86,709,202,-14,369,1000,-1000,-768,372,714,-493,-8,883,658,876,-1000,-760,124,805,-68,-544,-547,71,1000,1000,-1000,-727,-221,-252,-271,294,95,1000,-448,347,-708,-611,114,-1000,-100,894,-735,699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsKey(java.lang.Object):boolean",
            new int[]{1000,37,412,903,797,-1000,911,1000,517,-622,1000,-1000,-592,176,1000,167,-1000,-157,-1000,-342,451,314,1000,708,-320,1000,192,137,1000,1000,-570,-923,684,-1000,1000,-2,1000,-714,667,842,236,-1000,-1000,486,-1000,85,-1000,-1000,-1000,806,136,1000,-1000,504,-141,-1000,1000,-1000,-1000,892,-1000,-723,-1000,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsKey(java.lang.Object):boolean",
            new int[]{-59,34,-556,25,459,53,655,-821,964,216,417,215,-39,-999,-187,410,108,1000,-861,1000,700,1000,43,-833,-322,53,649,-309,-627,-1000,-25,273,1000,20,-1000,-703,541,-75,654,-411,1000,391,-160,400,-617,0,596,1000,540,1000,1000,-823,975,-266,101,1000,-596,-969,-902,1000,-58,349,738,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsKey(java.lang.Object):boolean",
            new int[]{1000,305,-456,691,450,-232,406,-1000,1000,58,1000,-157,274,-1000,1000,-32,-779,1000,21,595,-96,-331,1000,430,705,-624,-1000,-180,1000,-1000,570,477,553,-828,357,175,1000,-397,419,958,-587,-697,-1000,-664,-1000,-559,-151,-903,263,1000,692,1000,990,316,-317,-672,1000,-1000,-1000,764,9,-1000,-635,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsKey(java.lang.Object):boolean",
            new int[]{1000,-647,-72,-615,1000,224,1000,-791,562,-637,970,-934,-127,-431,260,-759,484,561,-1000,-1000,741,559,596,816,488,287,366,521,-1000,-125,857,599,466,-323,-579,513,-400,-1000,766,-377,1000,-751,-1000,-535,-359,315,-903,-1000,305,931,-69,-788,542,-474,162,721,-891,-1000,652,-131,-803,-902,-741,-949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsKey(java.lang.Object):boolean",
            new int[]{1000,45,-9,482,1000,-1000,913,1000,162,49,1000,-1000,-1000,-1000,-1000,348,-649,536,-1000,-529,255,681,1000,1000,-267,607,1000,1000,882,1000,570,-166,1000,-520,1000,175,1000,-238,746,958,1000,-1000,-999,-264,-1000,1000,-1000,-1000,-836,1000,233,1000,975,316,-1000,255,738,-1000,-1000,675,-1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsKey(java.lang.Object):boolean",
            new int[]{1000,-573,-40,779,186,-721,-54,903,1000,0,955,-753,547,-775,1000,230,-1000,931,401,287,-443,-704,1000,698,241,251,-1000,-32,1000,-519,900,255,432,-1000,778,260,743,305,514,1000,-1000,-1000,-1000,-731,-1000,-725,-151,-1000,799,721,10,977,373,-573,-534,-1000,1000,-1000,-1000,256,-444,-1000,-1000,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsValue(java.lang.Object):boolean",
            new int[]{1000,81,389,337,15,-714,-181,-100,276,784,639,-217,-1000,-1000,-320,469,932,-143,-942,330,457,-74,-1000,-480,1000,774,-236,104,-1000,-1000,263,352,-750,214,-130,-379,809,-43,427,-375,-72,-1000,280,-401,1000,564,-199,-1000,-397,-822,1000,-48,727,248,62,-603,-554,194,-733,763,-162,133,1000,440}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsValue(java.lang.Object):boolean",
            new int[]{488,-19,-682,-981,-5,873,336,-933,-832,959,-518,-65,-346,-913,514,-362,250,293,209,-69,924,551,743,715,-88,-482,1000,144,165,250,858,1000,520,620,313,-963,569,180,-581,-470,801,-181,774,-578,803,-920,1000,325,320,-672,802,870,885,-246,82,-770,-160,737,-700,-484,304,435,940,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsValue(java.lang.Object):boolean",
            new int[]{-1000,193,-361,-546,1000,981,-387,-604,887,100,1000,-445,137,546,212,-88,10,78,827,502,-100,-365,173,-117,182,-423,-1000,-292,590,-835,294,-160,698,-842,-1000,386,1000,524,1000,1000,591,490,731,763,-39,355,-687,-251,-563,-550,1000,-326,403,-649,-57,-373,-1000,-1000,200,659,-267,-23,-377,429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsValue(java.lang.Object):boolean",
            new int[]{390,493,-533,-741,-1000,238,611,-558,-853,-591,472,1000,-902,-1000,788,-8,-555,763,607,456,188,-516,1000,-337,-1000,-890,-285,508,-281,426,200,-289,1000,-574,-1000,-1000,1000,810,99,781,166,1000,1000,-1000,914,-1000,280,260,443,-924,1000,769,1000,-739,1000,-962,39,194,-890,-1000,1000,122,347,373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsValue(java.lang.Object):boolean",
            new int[]{1000,441,-569,-1000,-517,-214,323,-340,-1000,-648,210,1000,-498,-1000,-56,-751,-263,1000,1000,850,918,519,1000,-262,-234,-1000,-690,697,-927,22,263,-407,1000,-319,-425,-1000,1000,882,-833,1000,1000,214,1000,-1000,1000,-1000,696,-130,0,-398,763,1000,863,-520,1000,-1000,-747,641,-1000,-1000,1000,1000,612,415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsValue(java.lang.Object):boolean",
            new int[]{621,1000,-236,-1000,-978,-309,-225,-1000,-191,30,-519,1000,-225,-278,-914,142,-963,-389,86,-1000,-577,-499,625,-204,-1000,-220,746,-241,941,1000,982,-1000,864,-773,582,383,-212,-192,295,812,-1000,1000,587,379,236,-655,735,1000,1000,444,678,484,1000,-1000,-492,802,1000,-307,-880,-936,1000,-365,15,-916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsValue(java.lang.Object):boolean",
            new int[]{1000,-19,-676,-302,-1000,873,336,-1000,-917,543,1000,1000,-380,-979,886,464,-239,154,249,-247,348,-1000,743,553,-1000,14,1000,291,165,1000,86,160,784,-261,-556,-774,-331,751,553,-438,258,852,1000,-867,803,-1000,-226,325,871,207,693,498,1000,100,1000,-260,807,-1000,-773,-118,1000,-456,218,294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsValue(java.lang.Object):boolean",
            new int[]{1000,-275,821,356,941,-1000,-119,122,276,-873,207,-217,-612,-809,98,460,999,-143,-942,432,456,154,-1000,346,1000,956,-121,-249,-960,1000,-635,-19,-750,272,969,-694,809,-43,222,-21,109,736,246,-401,1000,564,-212,-1000,527,-277,483,160,377,480,62,-740,-1000,-59,-1000,638,1000,66,1000,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsValue(java.lang.Object):boolean",
            new int[]{1000,661,-142,-1000,-218,870,206,-586,-1000,389,28,885,-594,-1000,546,270,8,490,1000,357,435,-15,817,508,-1000,-68,1000,102,-211,1000,671,330,1000,291,-738,-412,131,241,-19,156,873,919,756,-1000,1000,-979,336,-147,910,-142,1000,685,765,-489,983,-623,-135,889,-269,-1000,1000,232,1000,927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsValue(java.lang.Object):boolean",
            new int[]{-1000,719,1000,33,-85,1,-871,-894,1000,232,733,-682,-558,-122,-124,-57,767,-107,-546,159,-342,206,-678,-734,1000,100,-1000,872,-543,-1000,494,825,-490,-260,-827,118,612,-247,250,1000,-809,-681,-195,931,674,599,-23,-250,-563,168,1000,-764,493,-651,-514,-878,-52,-211,-462,1000,-524,-263,-321,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "containsValue(java.lang.Object):boolean",
            new int[]{1000,-947,132,-968,822,1000,-313,981,-800,-98,326,302,53,273,-473,524,-722,1000,924,830,1000,-259,601,-43,-6,764,923,1000,285,293,-1000,131,204,-564,-62,-871,-116,1000,-18,392,283,159,1000,-400,1000,-954,-1000,-821,-742,769,-131,112,-643,104,553,-1000,-742,547,1000,-843,136,-213,289,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$EntrySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "entrySet():java.util.Set",
            new int[]{1000,-305,64,-497,-827,1000,1000,150,-111,-1000,984,788,-474,1000,520,-591,-80,758,-495,599,902,657,945,598,-622,-396,-18,-811,-612,945,144,739,1000,-1000,778,-572,137,714,1000,-903,237,-41,-607,-344,475,-874,1000,-778,-652,-424,137,-1000,-1000,-470,1000,1000,-1000,701,-53,61,-933,527,-1000,-587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$EntrySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "entrySet():java.util.Set",
            new int[]{1000,-421,513,-648,-1000,1000,1000,643,-358,-768,1000,310,-421,1000,307,-623,-109,831,-660,517,931,290,1000,215,-735,-577,223,-867,-728,713,733,979,1000,-1000,844,-872,-31,-247,1000,-882,-134,-329,-725,-806,1000,-823,-496,-968,-902,-597,-923,-1000,-857,-876,869,1000,-1000,190,-1000,4,-1000,-434,-1000,-719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.AbstractHashedMap$EntrySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "entrySet():java.util.Set",
            new int[]{1000,773,-406,-1000,371,-478,-628,-303,-525,-982,-778,-281,59,1000,1000,-249,-1000,832,-1000,1000,720,-1000,-1000,-18,-1000,1000,-370,-1000,-1000,393,-1000,-1000,74,639,732,-568,-723,1000,-277,-1000,-644,-1000,-1000,994,-816,-1000,1000,1000,170,-611,1000,-1000,1000,1000,967,1000,-1000,-740,17,231,735,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$EntrySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "entrySet():java.util.Set",
            new int[]{21,468,469,-328,-24,-39,-338,-33,758,100,-728,-360,759,-445,935,-832,-1000,-53,-612,1000,1000,-1000,-1000,358,1000,-1000,-517,-115,-306,889,794,-376,628,-745,-436,-624,-1000,990,-623,-400,-1000,-565,-1000,-916,-1000,-1000,1000,-655,540,-670,899,-998,552,-417,-1000,620,-1000,621,1000,-804,814,372,269,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$EntrySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "entrySet():java.util.Set",
            new int[]{567,81,662,400,-1000,1000,1000,1000,-623,-786,1000,-381,-974,457,-246,-103,520,784,-691,295,839,1000,1000,-349,-278,-272,749,-326,-49,998,972,1000,1000,-1000,1000,-1000,21,-73,1000,-474,-182,-565,-638,-536,1000,-261,-486,-744,-1000,-986,-1000,-224,-857,-1000,328,479,73,-1000,-1000,-212,-1000,-1000,-987,-799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$EntrySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "entrySet():java.util.Set",
            new int[]{882,131,-626,-865,1000,-546,-980,-1000,513,-777,-963,-384,-155,578,1000,-301,-491,-182,831,803,925,733,-474,859,-120,1000,-79,-542,525,628,-763,-967,-165,1000,-1000,381,912,-480,-360,-1000,1000,1000,1000,-836,-172,-1000,-682,694,1000,1000,708,-386,-432,-308,-458,-755,75,911,1000,20,581,-856,661,-337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$EntrySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "entrySet():java.util.Set",
            new int[]{1000,687,-192,232,881,1000,899,270,-328,-895,1000,918,-751,13,315,1000,1000,549,320,1000,1000,1000,1000,-418,-522,400,-294,-841,92,587,-1000,1000,547,-1000,501,223,1000,-1000,1000,-1000,591,1000,1000,758,600,-874,602,-1000,-506,-303,-154,-680,-348,-1000,945,265,-871,400,1000,-1000,1000,-1000,-987,203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$EntrySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "entrySet():java.util.Set",
            new int[]{921,885,-448,-1000,917,-910,-980,-677,1000,-33,-1000,-1000,-956,564,1000,-673,-795,-707,-1000,725,797,837,-955,458,-261,1000,-598,-1000,525,228,-1000,-1000,-394,1000,-944,763,1000,-418,-692,-1000,976,-140,-1000,-1000,-812,-1000,-1000,1000,1000,1000,427,-175,-159,-887,-1000,-1000,930,977,1000,824,1000,-856,-1000,-176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$EntrySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "entrySet():java.util.Set",
            new int[]{1000,941,917,-1000,-1000,419,1000,1000,-160,54,155,-261,-523,635,-531,565,-537,414,-499,843,1000,-361,1000,-689,-1000,-1000,-813,-1000,-1000,275,-867,673,1000,-1000,1000,-326,764,976,449,-698,-1000,-1000,-1000,1000,-844,-350,1000,-1000,-1000,-709,88,-1000,343,-66,171,862,-368,-1000,106,79,772,-6,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$EntrySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "entrySet():java.util.Set",
            new int[]{-392,491,-840,-961,706,586,-132,-547,-878,-740,-194,-969,-373,768,213,-715,928,-260,190,-478,265,333,-751,855,-299,919,32,-27,-236,-487,-322,604,664,129,908,871,356,305,1,-913,431,-857,532,905,996,-609,-1000,-778,509,325,-713,725,-120,492,-747,-428,39,724,724,-678,674,285,-352,946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "equals(java.lang.Object):boolean",
            new int[]{-1000,-197,808,629,-1000,-804,1000,763,1000,222,-1000,-743,108,-847,-536,704,786,692,39,297,-191,-900,-1000,227,-1000,-551,1000,632,-477,764,-140,70,-1000,627,-610,-1000,1000,-139,869,-490,812,550,490,1000,-274,-735,-529,-182,-1000,782,-36,-1000,420,-470,-566,1000,-1000,-1000,75,-924,-39,-1000,-1000,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "equals(java.lang.Object):boolean",
            new int[]{1000,233,-646,-1000,335,971,-1000,624,-914,57,841,643,-1000,453,159,309,-1000,863,-668,906,1000,374,-962,-664,456,-1000,-182,-462,-1000,-437,1000,-1000,1000,914,1000,1000,-608,-354,-667,1000,-380,253,178,-1000,278,-560,166,-170,-623,946,-489,288,-1000,371,1000,-535,-203,273,-629,1000,333,975,415,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "equals(java.lang.Object):boolean",
            new int[]{-338,887,949,830,-886,-599,801,-28,1000,-842,-1000,-261,-51,-679,-1000,647,81,181,19,1000,-1000,366,-784,-696,-1000,-374,1000,1000,-125,-1000,-271,381,-1000,-94,-1000,-631,1000,131,731,120,605,472,1000,1000,69,353,-1000,-134,245,-971,-125,64,1000,-427,-468,1000,-1000,-85,47,-225,-492,-1000,-1000,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "equals(java.lang.Object):boolean",
            new int[]{-783,-663,889,820,-535,-1000,1000,146,845,290,-1000,-1000,1000,-1000,-82,-177,1000,-1000,1000,-937,429,-583,-436,919,-522,82,683,755,1000,-960,-18,394,-1000,593,-1000,-1000,1000,506,-168,-763,42,644,404,1000,325,-108,-808,-166,136,-1000,-70,-1000,1000,-462,-1000,1000,275,-1000,475,-1000,219,-1000,-1000,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "equals(java.lang.Object):boolean",
            new int[]{1000,-547,-940,-96,-1000,-804,822,154,-545,-933,-1000,652,1000,-414,-47,141,602,-1000,39,-1000,-191,107,-534,-709,73,-1000,664,-393,-190,-359,-140,-1000,-52,1000,-325,109,213,-139,-110,-445,416,94,-87,1000,661,-1000,-698,434,-1000,-1000,81,-700,981,-524,920,-430,-1000,-55,175,-1000,694,-648,415,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "equals(java.lang.Object):boolean",
            new int[]{27,915,473,-263,-886,367,59,242,1000,-462,-636,272,499,-26,-1000,541,626,348,-455,1000,-472,-47,-81,-1000,-555,-545,477,311,-326,-1000,181,-695,-265,-109,-841,38,604,235,745,727,348,276,908,564,-219,1000,-622,99,245,-890,144,273,786,-347,932,995,-1000,-129,481,470,-288,-1000,-1000,-479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "equals(java.lang.Object):boolean",
            new int[]{-21,-663,-669,820,-243,44,748,230,845,-198,813,1000,1000,100,-340,-817,-678,-1000,1000,-198,662,510,-614,-484,-126,-374,-170,-327,1000,1000,311,-44,-360,414,133,-906,1000,-849,-386,-522,713,658,-121,1000,250,-1000,-808,-214,1000,923,-292,-21,-614,717,-595,-727,-1000,-432,-1000,-627,609,34,1000,35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "equals(java.lang.Object):boolean",
            new int[]{-1000,235,949,-607,-161,-1000,1000,1000,1000,392,934,952,-41,-356,-905,1000,-976,1000,299,1000,1000,-315,-1000,288,-1000,477,918,1000,-1000,1000,483,1000,-1000,-31,325,-1000,989,-1000,108,474,1000,941,826,1000,184,934,76,-1000,-912,230,-706,792,937,1000,-1000,841,-1000,71,-620,-26,719,-382,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "equals(java.lang.Object):boolean",
            new int[]{-783,-663,889,348,-860,-1000,1000,657,1000,290,225,-1000,-122,-1000,-1000,383,-11,1000,48,1000,600,-445,-1000,399,-522,-229,909,1000,-1000,1000,-185,1000,-1000,210,-467,-441,-321,-1000,-168,-292,1000,999,410,16,-112,-1000,-753,-238,-1000,1000,-689,-357,-6,221,-1000,905,-126,-1000,-1000,-463,-359,-300,-349,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "equals(java.lang.Object):boolean",
            new int[]{-783,-663,889,820,-535,-1000,-344,146,845,290,-1000,-1000,1000,-1000,-82,-177,1000,-1000,1000,-937,429,-583,-436,-757,-522,-939,683,755,1000,-356,-18,394,-1000,593,-1000,-1000,1000,506,-168,-763,42,100,404,1000,325,-108,-808,-166,136,-1000,486,-1000,759,-462,-1000,1000,275,-1000,966,-1000,-28,-1000,-1000,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "equals(java.lang.Object):boolean",
            new int[]{1000,-1000,-351,722,-454,1000,-191,-371,-1000,93,168,844,651,-558,1000,-1000,473,-1000,1000,82,99,933,107,423,698,284,-1000,-1000,1000,-1000,275,-737,180,1000,-495,269,-263,-131,-1000,-712,-1000,648,99,-257,-497,-922,58,6,790,-781,882,-156,-1000,-52,-184,-728,545,344,-99,-875,1000,88,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{-463,-265,524,-5,-1000,-864,-242,-75,-884,-108,-228,-58,-478,493,-170,-117,-11,143,615,820,373,774,770,222,-456,37,1000,92,-99,688,-312,884,435,-595,232,-805,203,142,226,630,1000,-1000,-862,-613,142,1000,-99,448,-393,977,-198,-43,197,-8,-367,-243,-712,-1000,240,670,-825,190,-809,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{-497,-661,49,-894,579,518,-716,-268,165,-162,-72,-619,480,-1000,1000,-891,-873,-858,613,860,169,1000,292,-375,8,-1000,281,-1000,204,-186,-83,-453,-263,239,-1000,835,1000,-811,-193,-270,184,-605,-545,-666,-398,-126,-413,104,-131,-1000,118,837,276,1000,-1000,1000,350,689,-20,671,54,1000,1000,-954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{576,-679,524,-1000,-1000,499,-1000,-1000,-884,-891,40,-1000,-1000,-288,-651,-56,-13,9,1000,406,-1000,774,1000,-1000,-1000,292,1000,15,508,1000,246,-58,-574,-1000,-234,-805,1000,-609,142,-1000,34,-939,-683,-697,471,1000,-986,169,1000,360,719,729,335,-8,-803,299,-507,-1000,374,38,-897,732,-393,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{-400,865,-288,272,-157,-614,-578,-860,536,-587,-884,175,12,-278,-222,-910,480,122,499,578,56,455,-89,-671,-453,-1000,261,-348,1000,-38,1000,321,-1000,474,213,90,-260,927,-521,-585,1000,1000,1000,1000,-140,-958,-930,1000,-470,-594,-1000,777,-810,468,-1000,25,-67,-199,-1000,-215,219,1000,400,-830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{156,-339,519,-260,-336,-1000,-421,-365,-1000,417,1000,531,-207,1000,186,-497,-1000,1000,591,543,1000,738,143,336,544,1000,1000,1000,-967,1000,-235,-735,801,-1000,-918,106,-1000,-608,-224,620,-1000,9,-1000,-1000,-322,1000,1000,299,17,1000,402,298,872,-518,-28,-1000,-1000,187,1000,1000,-982,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{-6,293,-657,205,748,250,-332,337,-571,-209,-738,734,563,918,-742,-199,451,888,-379,-606,516,-511,-657,820,-541,103,837,546,-699,21,706,-315,-283,493,897,147,-86,66,643,218,-235,-80,606,-781,-81,-896,111,925,139,845,522,427,297,445,402,-724,-909,650,-613,891,-161,-148,-579,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{-264,415,143,239,22,-80,-950,-126,-701,-524,88,-489,644,-565,557,-30,-682,289,-643,755,-973,610,95,-653,402,-176,1000,-100,135,-29,754,-1000,-1000,-630,-968,713,602,333,-476,-1000,-881,742,1000,1000,-191,126,297,664,698,-368,-102,932,974,119,-1000,243,1000,511,360,671,220,773,876,494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{31,333,54,933,-631,-908,-456,-95,1000,-666,-820,-7,-1000,276,-1000,-826,868,-224,1000,512,1000,217,756,-526,-1000,-375,586,-298,58,1000,1000,901,-1000,-422,228,-1000,419,1000,-10,-740,886,-559,1000,1000,540,-211,-830,509,-184,1000,-787,349,-184,-147,-791,-188,-1000,-1000,-1000,529,-855,1000,-727,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{395,-141,-553,-147,452,-1000,-873,-790,-864,419,-316,-111,-29,-767,1000,-148,134,-863,-150,156,-293,507,343,-250,317,-557,-27,671,-498,579,-421,-269,-1000,511,-319,1000,-364,1000,-1000,-1000,-362,536,466,1000,-554,670,702,542,843,-710,706,565,352,219,-695,386,210,-800,645,-332,307,600,-364,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{352,-193,11,895,-1000,-251,43,-349,-173,438,-513,1000,-983,466,1000,-97,942,-186,1000,260,-44,-1000,467,907,-47,278,-263,152,-987,456,-580,1000,-76,-1000,895,367,-1000,-326,347,690,-110,200,64,1000,13,-118,-206,712,-345,-186,117,1000,-1000,284,-358,-127,-617,-253,59,-1000,113,-192,-1000,-238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{-943,45,-494,-9,1000,1000,-786,326,465,-435,-638,-1000,-271,-1000,651,-1000,-555,-1000,-210,921,196,1000,506,-350,301,-1000,281,-1000,503,412,1000,-1000,-1000,498,-1000,1000,1000,125,-428,-1000,48,425,1000,1000,66,-820,-960,1000,-179,-1000,-1000,966,751,949,-1000,-391,453,565,-660,-471,1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{269,353,1000,469,802,946,956,863,-1000,897,303,474,899,525,-884,985,-4,-944,-659,-1000,-399,632,-1000,576,1000,795,-793,-136,-424,2,222,214,373,-825,-700,-191,-1000,-28,1000,1000,-65,125,-1000,-841,-152,-116,1000,184,-98,799,49,39,472,-732,333,-642,1000,404,1000,577,-320,-904,790,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "get(java.lang.Object):java.lang.Object",
            new int[]{191,1000,-11,1000,230,-1000,374,576,-423,-162,-1000,1000,560,-719,353,-1000,480,403,-214,788,1000,-810,-286,593,331,-727,-718,-644,469,-740,1000,358,-1000,1000,360,723,-1000,1000,-1000,355,419,1000,1000,1000,-204,-1000,-850,1000,-1000,-1000,-1000,306,-810,-385,-989,344,530,724,-1000,-989,1000,563,-179,896}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{410,-925,99,-485,-1000,1000,283,487,-931,351,-1000,1000,-1000,439,853,48,1000,-1000,-1000,-218,533,411,8,-164,237,-588,-589,-424,336,-1000,-531,43,868,634,955,-139,-250,-1000,-484,-47,-159,-1000,-407,721,10,-537,-513,1000,352,-593,-245,-423,982,804,-177,124,-571,509,-811,-618,1000,-493,-601,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{616,715,214,-563,-606,-354,91,-775,711,8,545,115,1000,-547,879,-727,449,563,-1000,243,-404,92,740,1000,276,-92,10,-250,1000,92,1000,11,203,1000,1000,802,-1000,457,513,0,-1000,-929,448,867,470,61,-1000,926,-190,-452,-1000,-341,-550,-188,263,-366,27,-16,-818,-639,-8,997,151,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{68,-379,-818,-235,-155,162,-421,-26,-528,-196,-452,959,-437,578,450,-340,-400,136,-197,153,474,657,63,156,-15,-899,-197,290,82,186,73,-877,-913,367,991,-276,-384,-649,596,137,-796,-398,-266,149,35,-327,-417,1000,296,696,273,29,-253,-2,-463,-431,-203,396,-585,-669,200,156,-1000,-952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{71,-181,-1000,-801,332,-409,-449,-405,278,996,-558,907,-43,970,-284,-121,450,-487,365,591,-569,12,-580,-277,-881,888,589,-526,-703,-250,-231,-597,23,815,-706,638,469,-1000,774,-514,64,-367,-412,711,146,672,438,390,122,-878,57,1000,214,520,361,-22,544,-597,251,175,-488,-929,-482,768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{371,-283,-709,-695,-1000,794,194,743,328,1000,69,935,-141,106,-35,170,-461,-1000,-1000,-435,-737,882,-319,879,-1000,49,-1000,-149,-256,-565,580,473,698,-76,-495,1000,-840,-331,422,764,-27,-557,322,1000,-1000,191,473,813,-764,-1000,1000,-1000,1000,787,303,-385,-77,-439,-1000,172,1000,-451,-984,-554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{-995,-237,-157,-472,-84,669,-208,-54,-624,-24,349,656,-829,194,-291,-543,243,-491,595,537,1000,-1000,171,-58,-1000,-180,-45,-188,-17,-396,-873,14,-842,-8,292,-1000,1000,190,-1000,-608,591,64,-546,770,105,-115,-625,-8,-185,-402,-386,-176,1000,307,-834,-319,501,-700,283,-860,-991,-186,-607,325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{308,-911,692,-215,-969,1000,-84,-120,-1000,-241,-1000,1000,-1000,-67,557,-319,836,-518,-941,133,-835,142,-603,-666,175,-967,-56,-408,414,-1000,-1000,419,620,1000,1000,-897,-406,-1000,-1000,-1000,-936,-1000,-447,-55,909,-960,476,1000,1000,335,156,113,480,229,-177,687,-836,212,-404,-1000,-12,-622,-20,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{-447,-473,181,-572,-1000,843,12,518,-795,93,-305,1000,-1000,802,164,-330,1000,-1000,-269,-97,1000,-429,-230,-354,-640,131,-513,-19,-145,-694,110,230,-761,1000,97,-669,991,-867,-1000,-719,381,-452,-1000,938,507,-556,-159,968,187,-1000,-443,21,1000,367,-592,251,474,16,678,-558,143,-501,771,310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{-394,-615,174,-679,-1000,-444,-292,794,-529,209,-1000,827,-1000,802,-297,-286,351,-1000,308,-304,551,78,-726,-329,-417,895,-35,94,-679,-656,110,1000,-624,1000,-574,-90,1000,-1000,-746,-862,461,207,-839,1000,254,-61,735,1000,-258,-934,-1000,1000,660,474,-370,618,1000,-285,1000,-318,-1000,-1000,504,925}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{131,719,703,-670,-970,-784,-33,-727,495,-371,-657,-656,964,853,790,-652,31,922,-507,574,-996,560,142,751,-361,-651,674,156,533,362,868,269,826,-701,400,897,-306,-634,-197,131,-571,-525,153,147,-177,672,-782,460,43,141,-651,961,-796,-387,-586,-156,478,-438,-263,329,-316,177,-832,-970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{89,307,678,-215,207,-355,-994,-213,-219,-689,479,942,-196,1000,368,-885,-1000,623,878,433,915,-77,-153,103,-1000,-314,46,1000,-509,1000,973,49,-234,1000,149,-864,799,-366,543,-456,-529,409,-799,122,542,-255,-21,1000,106,841,-1000,667,-765,-785,-1000,-541,1000,-144,1000,-632,-1000,426,-76,379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "isEmpty():boolean",
            new int[]{89,-1000,-966,-349,-535,-1000,-227,1000,-620,947,-794,-339,-1000,-96,-795,208,-806,-1000,308,-623,282,-291,-283,-964,-74,1000,-317,-578,-899,-946,-199,1000,-501,790,-1000,-174,600,-597,-68,-1000,597,485,285,955,110,-286,1000,1000,-650,89,-568,399,-1000,1000,181,935,560,-31,1000,-296,-632,-1000,618,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$KeySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "keySet():java.util.Set",
            new int[]{-542,-1,-852,-352,560,-1000,291,-839,-878,-1000,-107,-915,-546,-1000,-1000,446,19,-953,722,-135,654,1000,-175,416,-82,-1000,-1000,1000,-261,-1000,1000,499,-1000,-1000,1000,-751,501,-460,-354,26,-991,1000,-1000,-124,127,1000,-628,984,530,-980,-738,-1000,1000,600,214,926,-1000,634,-48,312,-1000,-108,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.AbstractHashedMap$KeySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "keySet():java.util.Set",
            new int[]{95,113,184,-623,506,-77,-426,-795,913,-864,1000,-156,438,-233,-532,597,-818,-969,-52,-711,668,594,-768,-99,-713,-225,-88,1000,36,-1000,-398,-1000,-1000,-428,862,-112,744,298,-436,-324,-1000,-486,-1000,-799,-224,-105,-289,-487,1000,2,929,42,83,400,-873,435,-181,-125,555,172,787,87,565,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$KeySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "keySet():java.util.Set",
            new int[]{-142,475,-473,753,518,162,-1000,-969,788,803,-898,-800,-492,-852,-127,-672,669,1000,9,-246,1000,-415,630,1000,1000,948,-1000,57,-1000,-328,-10,-475,-1000,284,1000,-222,944,-866,536,102,-169,135,-362,-962,-942,-1000,970,-320,492,1000,497,-942,679,-458,-87,858,-692,-242,776,-1000,362,-253,1000,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$KeySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "keySet():java.util.Set",
            new int[]{-885,509,-113,58,740,-312,-1000,-203,-247,-596,1000,-1000,259,-1000,-831,708,-468,431,-715,-388,752,-148,344,-78,-150,35,-666,1000,-1000,-1000,516,-1000,-955,-714,341,1000,1000,1000,-150,-379,-37,756,-726,-1000,211,-503,669,-474,1000,710,144,-1000,7,383,88,1000,-262,-286,-4,-728,1000,-173,1000,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$KeySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "keySet():java.util.Set",
            new int[]{-521,285,451,-888,-448,-1000,708,-235,71,833,-53,368,-790,1000,1000,-597,142,723,674,-659,216,-232,-623,-143,-1000,-1000,656,537,-603,-842,352,-43,303,277,-311,1000,-1000,1000,662,-369,1000,99,1000,1000,206,1000,-358,1000,-994,87,-76,379,-382,-86,-809,-556,718,551,-1000,-505,-560,-518,-674,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$KeySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "keySet():java.util.Set",
            new int[]{1000,301,1000,-381,253,259,-1000,-146,-446,-27,-121,-312,39,183,23,390,60,-798,1000,-250,40,-134,-1000,-885,-512,1000,195,229,1000,-628,-977,-673,257,572,-395,937,117,-178,43,-346,216,-423,976,-1000,1000,-326,-697,-1000,1000,385,407,-993,-1000,-965,-1000,225,1000,-90,1000,-663,955,-245,-555,960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$KeySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "keySet():java.util.Set",
            new int[]{1000,193,586,-800,-1000,-794,704,-46,439,935,-1000,953,908,-1000,1000,-1000,1000,1000,1000,-411,266,-459,1000,223,157,1000,526,-1000,-1000,1000,-505,313,-124,-584,-309,-47,583,-1000,1000,-749,413,-465,-423,827,284,-1000,-296,-476,200,-602,451,1000,-794,-174,-360,-317,28,-585,853,-663,-300,-702,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$KeySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "keySet():java.util.Set",
            new int[]{1000,-208,559,672,-1000,121,-734,-983,-1000,335,370,-549,-540,466,1000,-591,664,1000,277,-504,706,1000,-546,1,-337,467,610,-673,-172,-202,-1000,866,1000,-831,1000,492,1000,814,420,572,-167,75,-1000,-518,-752,82,-135,-791,899,-71,627,-676,-60,1000,956,541,-1000,-451,971,-315,-457,-232,623,296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$KeySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "keySet():java.util.Set",
            new int[]{-858,-797,999,547,632,-769,-725,-819,-509,-357,723,-238,-864,979,449,283,159,-60,129,215,878,-788,-194,531,-749,-716,-269,-939,509,864,-890,-330,468,935,592,814,-688,-236,-398,963,834,235,223,-870,-375,769,202,246,-517,789,912,-508,463,-784,-412,895,-599,-244,-994,-595,-511,-62,-58,536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$KeySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "keySet():java.util.Set",
            new int[]{1000,-877,778,-278,-63,253,539,-46,-451,-1000,497,800,44,1000,394,512,-1000,-427,-818,153,266,-11,-1000,-472,-1000,-42,999,240,480,-150,-895,-360,211,407,-356,341,368,-235,-87,-181,-333,-796,351,-91,422,-187,-386,-1000,1000,-52,-172,615,-999,103,-884,-370,1000,-322,735,400,220,-122,-794,173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$KeySet", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "keySet():java.util.Set",
            new int[]{1000,615,659,-697,-1000,-1000,1000,-324,-891,909,-1000,-219,-572,4,95,-711,1000,860,-17,1000,-222,1000,-623,-805,-841,-1000,1000,-1000,987,-439,-318,1000,1000,-909,-67,1000,-904,1000,496,-12,235,-686,602,991,420,1000,-1000,1000,-406,-1000,-663,-1000,-211,898,-1000,-761,-555,905,-884,1000,-1000,-42,552,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.iterators.EmptyMapIterator", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "mapIterator():org.apache.commons.collections.MapIterator",
            new int[]{1000,487,-646,-996,-429,1000,1000,-995,768,192,-599,-300,-1000,-900,-679,234,703,-56,-1000,-532,-1000,-616,-1000,-1000,-1000,-368,-895,-762,-390,-857,297,775,1000,-838,-190,-130,-104,-22,-622,161,1000,1000,521,-911,-886,309,-1000,-702,157,1000,-951,-1000,827,-280,880,-1000,774,875,920,-247,76,1000,288,794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.iterators.EmptyMapIterator", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "mapIterator():org.apache.commons.collections.MapIterator",
            new int[]{-1000,475,-161,373,992,142,266,-988,742,774,221,1000,425,-1000,-280,174,1000,-882,-112,1000,-1000,-74,950,120,-676,-475,-199,-1000,1000,497,-798,-254,-680,178,-26,-650,672,-814,75,458,-1000,1000,-877,-243,464,-1000,-1000,973,-581,870,572,-1000,1000,924,-993,-802,-619,-963,1000,-39,-828,1000,991,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$FlatMapIterator", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "mapIterator():org.apache.commons.collections.MapIterator",
            new int[]{902,-85,-608,-552,-429,-724,1000,-1000,863,-542,-796,-262,188,-390,179,-45,656,-349,988,-1000,-847,-1000,-918,-767,-673,219,-1000,-676,-311,-360,-223,1000,613,-917,-359,-227,1000,-447,-466,-268,1000,1000,819,31,-465,648,-1000,-744,-738,1000,-534,-20,546,308,427,0,807,668,918,-505,529,414,-856,815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.iterators.EmptyMapIterator", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "mapIterator():org.apache.commons.collections.MapIterator",
            new int[]{-892,-715,-579,-24,-878,-208,-346,-179,523,-475,1000,222,729,-1000,-276,819,1000,-113,-1000,821,-574,-1000,-1000,95,-1000,-1000,334,-1000,-158,-1000,1000,1000,1000,995,546,1000,172,-368,-431,-1000,-384,-53,-850,104,-275,-1000,227,-1000,997,-468,-1000,-731,314,-517,1000,-199,-1000,47,1000,-839,-307,1000,-337,503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.iterators.EmptyMapIterator", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "mapIterator():org.apache.commons.collections.MapIterator",
            new int[]{455,-507,-244,838,1000,275,564,-949,271,-1000,-176,-330,-1000,126,912,153,277,-1000,391,-994,853,-727,367,1000,44,-60,-722,258,-422,1000,-19,638,396,-611,169,-804,812,-1000,-746,223,-317,129,1000,-261,209,1000,-748,384,-653,1000,655,1000,-170,233,-381,781,1000,779,-984,-956,1000,-568,832,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$FlatMapIterator", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "mapIterator():org.apache.commons.collections.MapIterator",
            new int[]{-794,-283,-797,-583,-1000,-30,-1000,400,549,59,1000,-330,332,-616,285,945,160,139,-559,-323,120,-579,-1000,97,225,-975,419,486,-741,-456,595,1000,695,631,758,990,480,877,-364,-401,-321,-1000,329,400,-142,-31,743,-675,1000,-1000,-195,-681,-400,649,1000,-271,-1000,574,-480,247,400,433,-281,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.iterators.EmptyMapIterator", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "mapIterator():org.apache.commons.collections.MapIterator",
            new int[]{-605,836,-191,-58,1000,-572,1000,-996,86,32,-581,558,-168,-424,-174,669,573,-748,-364,-515,-338,599,542,-428,91,-506,400,1000,229,291,114,466,368,-1000,-53,-861,-57,-1000,-33,-277,-163,33,-535,-1000,993,368,-1000,-81,-1000,2,1000,1000,-193,-1000,316,346,1000,-311,1000,-272,353,-148,-777,-797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.AbstractHashedMap$HashMapIterator", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "mapIterator():org.apache.commons.collections.MapIterator",
            new int[]{1000,-681,-976,-168,30,-145,654,-1000,482,-65,-599,-300,-834,-269,999,276,493,-349,224,-794,-385,21,154,-453,-630,831,117,-171,126,-719,297,1000,623,-838,-266,-130,1000,-1000,-309,-1000,-400,310,-329,-1000,514,309,-699,-788,-1000,212,-773,-1000,-282,-1000,257,-218,774,401,1000,-247,76,361,210,-365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$FlatMapIterator", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "mapIterator():org.apache.commons.collections.MapIterator",
            new int[]{432,-397,-378,-259,164,241,1000,-1000,501,688,-604,373,-1000,-1000,-879,274,948,-1000,1000,-912,-1000,-887,320,597,-1000,937,-226,781,821,1000,-1000,-984,771,1000,-219,-1000,250,-1000,93,1000,-39,1000,1000,385,-532,1000,-1000,1000,-1000,1000,1000,-1000,1000,1000,-1000,679,1000,781,-790,-1000,385,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$FlatMapIterator", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "mapIterator():org.apache.commons.collections.MapIterator",
            new int[]{902,101,-829,68,459,-361,721,-1000,1000,-427,-720,-243,-1000,-24,-590,324,958,-838,457,400,-886,137,61,-365,-460,-867,-1000,-412,-290,-162,-92,1000,-31,1,-1000,-391,1000,-1000,-246,-1000,-97,886,-187,-1000,260,517,514,-1000,876,1000,-583,-1000,260,751,1000,930,842,-216,1000,-473,353,4,161,-882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.iterators.EmptyMapIterator", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "mapIterator():org.apache.commons.collections.MapIterator",
            new int[]{-219,817,-305,-176,1000,-203,1000,-1000,389,461,432,1000,439,-599,-1000,339,1000,-859,-69,797,-1000,297,480,-568,-1000,-504,1000,-783,1000,-381,-475,-354,304,-473,339,-1000,-618,-1000,215,144,-1000,1000,-899,-1000,544,-1000,-1000,312,-387,586,-117,-945,1000,-748,-1000,-540,214,-1000,1000,-427,-1000,1000,-129,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-856,-345,-376,-144,1,-507,253,-1000,531,1000,-267,14,-164,588,176,-203,-388,-57,645,-1000,1000,-262,-251,-941,-75,-123,-341,-1000,-337,584,-98,-763,815,-1000,723,-64,-1000,440,-46,603,268,-175,-1000,211,-327,277,-77,-796,833,-86,-956,-493,150,-67,650,-1000,-1000,-1000,427,722,-83,-246,400,933}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-90,757,-276,-430,-697,-493,98,1000,-609,-801,-357,-89,-348,-521,-48,-182,564,1000,-26,-639,944,923,245,58,-415,-129,-237,302,1000,-759,324,22,-407,-888,-435,656,241,156,674,-339,-614,-6,-15,-1000,-456,-880,-677,-1000,-405,280,-584,157,45,326,100,261,393,-242,-315,-242,952,617,455,-250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-778,-589,-594,332,-692,-165,-922,-31,1000,1000,645,47,426,-862,363,1000,689,80,328,-196,349,-1000,-876,-150,-75,-781,-742,167,522,524,-141,151,458,-382,1000,-703,-1000,782,648,-611,472,-533,-610,497,1000,307,603,-456,773,130,-108,-462,526,244,267,-829,-400,-233,117,462,-671,-317,113,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-321,53,-686,-54,-1000,979,691,929,-562,-801,644,1000,-873,-1000,1000,409,1000,69,350,1000,-768,1000,-456,1000,-1000,-81,424,1000,1000,1000,403,1000,40,-111,-1000,966,603,787,745,713,-1000,-1000,-1000,-1000,152,1000,-246,532,-640,267,-965,59,749,-643,1000,1000,-170,-1000,1000,1000,1000,-690,457,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{426,415,998,557,-1000,579,722,958,-698,-161,634,993,-446,-1000,572,-171,1000,329,-164,1000,-909,1000,1000,842,-1000,-273,645,1000,1000,348,1000,1000,-388,346,-1000,542,766,519,561,20,-1000,-1000,-1000,-1000,-1000,745,457,554,-788,276,-414,-585,564,-73,562,1000,312,-1000,507,689,921,6,38,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-502,2,-461,230,-1000,151,-555,177,-955,1000,-19,148,559,-1000,492,-699,51,169,-646,317,-867,-1000,-852,45,897,-665,892,124,329,-296,80,1000,1000,75,309,-921,627,-126,493,561,56,683,-623,155,821,-1000,155,463,-58,-337,597,-1000,223,1000,-1000,-256,903,-454,-197,-555,-381,834,-813,824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-510,329,-2,123,-560,-567,-1000,-473,-66,815,187,527,1,-1000,1000,-264,972,489,1000,106,768,55,64,-68,-609,-691,-1000,-929,1000,359,51,-213,-1,-708,59,342,-949,441,772,90,-426,-831,-772,-694,-993,710,-397,-473,42,1000,-447,-275,168,-175,385,-265,-1000,563,244,971,441,-780,1000,801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-738,295,-402,-50,-384,-1000,-1000,-1000,-517,1000,8,341,179,-1000,1000,-301,961,553,1000,-252,1000,-324,-311,-432,-453,-166,-1000,-1000,1000,363,89,151,155,-1000,483,262,-1000,410,856,119,-196,-763,-681,-572,-990,695,281,-884,374,1000,-460,-736,-258,75,314,303,-1000,1000,139,1000,249,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-68,-907,-951,-699,681,-883,872,-276,-51,290,-760,-426,204,588,-709,-966,-763,-469,150,-460,1000,-1000,503,-1000,316,-421,1000,-289,-936,-465,-440,-816,-318,90,926,-250,134,-243,-656,-933,-126,386,1000,-571,1000,-967,-311,-368,-530,-21,969,-221,-63,150,382,-684,497,-148,190,185,-1000,824,108,518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-397,721,84,501,-1000,1000,708,68,397,1000,-433,-282,107,-1000,1000,-309,1000,301,1000,466,-960,671,1000,-737,-903,-432,-776,-236,1000,178,1000,776,212,285,-418,-773,344,441,172,-417,-881,-455,-589,-835,-833,-514,-948,-166,-413,1000,989,-452,281,-35,210,-16,-993,235,546,550,-99,16,843,610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-83,355,298,253,-692,-223,-484,-44,-256,1000,321,667,-134,-1000,872,-236,981,441,651,374,176,338,345,205,-727,127,-507,-350,1000,355,753,1000,-117,-391,-259,402,-435,464,709,69,-598,-882,-841,-786,-995,720,879,-165,-207,783,-437,-953,19,799,438,809,-607,94,323,886,585,-544,711,861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-800,561,209,1000,-1000,1000,575,1000,-660,-1000,989,745,-586,-1000,391,314,1000,570,-275,1000,-757,1000,1000,1000,-1000,325,682,1000,1000,610,550,748,-592,353,-1000,501,924,894,1000,1000,-1000,-1000,-163,-1000,-1000,881,-1000,810,602,-11,-558,-684,1000,206,60,1000,149,-544,666,350,1000,328,-354,900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-502,349,610,-402,-675,395,-36,177,649,85,-661,722,990,408,-836,-69,51,-618,-923,343,674,-784,106,-456,483,-665,278,-901,54,13,167,-459,351,216,215,-779,466,-126,-668,-894,180,-752,217,740,821,334,-855,463,-8,-703,531,-249,-699,821,-508,-952,986,973,850,835,-509,661,513,836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{1000,159,378,1000,-91,-1000,171,-1000,-95,-856,-598,532,523,-1000,-939,699,-295,-583,603,900,1000,-1000,-679,98,-1000,22,100,429,71,-267,716,-1000,252,-1000,-147,523,-1000,-441,-779,432,1000,-1000,-1000,1000,-1000,-1000,414,1000,1000,-135,-536,515,-1000,-1000,363,-1000,25,71,1000,-781,-923,-1000,578,-331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{-20,-917,564,5,1000,-485,367,-1000,-525,74,20,581,688,537,-524,579,-616,-583,-269,-642,447,-337,-785,-1000,-594,-906,-108,1000,-306,952,294,465,394,-243,482,523,-903,1000,-378,396,610,-557,390,-616,-1000,-840,1000,-212,408,254,-473,1000,-892,-889,1000,131,724,829,933,-554,-923,-1000,545,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{1000,499,174,202,-306,-1000,-647,-1000,-831,-993,-562,1000,-562,-156,-854,158,-1000,-1000,5,20,1000,-887,-1000,-1000,-1000,-480,-1000,-517,-367,1000,-448,49,996,-458,-618,201,-869,-6,-819,-3,1000,-1000,1000,1000,-1000,-874,1000,675,816,-986,-326,83,-501,-1000,301,599,743,1000,749,141,-740,-330,746,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{1000,-695,378,1000,492,-1000,-240,-1000,-848,-1000,-1000,1000,509,-1000,-939,699,-875,-1000,458,-988,1000,-1000,-721,5,-771,76,529,523,368,-267,-9,-684,444,-1000,-147,523,-1000,-13,1000,486,1000,-1000,-1000,1000,-1000,-1000,414,1000,1000,-1000,-463,603,-1000,-1000,-328,-488,-108,1000,1000,-1000,-577,-700,411,808}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{-499,-396,894,1000,393,-205,633,-354,72,0,-90,126,117,-720,-184,1000,921,533,89,-48,-211,-401,-539,-278,901,-203,-869,720,-89,-46,1000,221,908,-328,-127,505,-1000,-1000,-86,167,863,-280,188,-580,-466,-1000,657,-93,477,1000,155,1000,433,-599,34,893,377,80,-111,210,222,-1000,1000,-239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{95,507,634,419,-701,31,-108,-1000,-191,-455,-416,-244,-69,-695,99,121,292,-613,-124,163,99,-222,-1000,-22,215,-410,-977,1000,-869,-369,548,36,753,-584,-463,895,-629,-294,368,342,181,-146,-300,702,265,-358,-663,184,217,-598,-419,700,-1000,-925,-539,761,-240,891,141,442,-503,-1000,984,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{379,-197,-742,-67,1000,106,-354,397,-742,-990,-967,-817,-807,400,638,-1000,62,149,-408,-873,-109,1000,-766,893,235,-407,-378,570,555,349,-299,947,694,843,293,-330,686,-185,218,-70,-417,75,307,867,206,-258,-709,13,213,-1000,-783,608,-660,-739,321,760,-526,620,-825,29,-629,627,517,-321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{1000,-85,118,316,-1000,-738,-540,-1000,378,-484,-289,-907,-643,-292,-375,537,-245,-423,588,-90,84,-921,-272,363,-372,937,-1000,-1000,-168,-534,1000,-818,1000,-692,-1000,1000,-509,-1000,247,347,774,-788,791,1000,898,-912,653,1000,867,-881,32,-141,166,-1000,-1000,-18,905,-171,403,130,-421,-198,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{635,-681,378,746,87,-601,472,656,613,1000,889,550,-531,-1000,1000,954,-295,13,985,-189,-491,394,998,-109,-277,690,971,266,1000,-359,716,651,1000,98,-106,511,-765,-441,-779,180,271,-537,-329,-375,1000,-783,-20,-50,794,317,-561,22,-59,-1000,-46,1000,400,954,490,24,1000,945,173,823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{1000,-307,-2,837,-260,-1000,503,-1000,792,-973,-263,223,299,-973,-182,1000,618,174,1000,4,596,-1000,-602,1000,-381,886,-369,-1000,-61,-1000,1000,-1000,1000,-1000,-504,1000,-1000,-1000,952,657,1000,-1000,-1000,1000,898,-1000,-329,1000,739,-412,-210,433,-365,-1000,-946,-472,-20,-523,1000,-453,-71,-571,684,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{1000,-323,121,630,-392,-1000,77,-1000,464,-484,-315,932,299,28,-388,705,-223,-423,979,-391,84,-1000,-602,363,-246,660,-816,-438,-102,-622,1000,-899,808,-1000,-473,1000,-658,-901,1000,708,937,-1000,96,907,898,-1000,644,1000,867,-664,75,410,-28,-1000,-804,-788,629,403,494,-464,-421,-571,916,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "putAll(java.util.Map):void",
            new int[]{-1000,-571,-518,-1000,1000,43,-15,397,-866,212,-151,-1000,-534,700,405,-1000,-36,316,-1000,-736,-686,1000,-895,-507,267,-1000,-728,1000,104,1000,-299,1000,502,1000,871,-1000,686,1000,-1000,-157,-1000,578,1000,-452,-1000,-132,143,-1000,545,-11,-833,1000,-126,-480,1000,608,188,-667,-872,304,-844,326,583,211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "remove(java.lang.Object):java.lang.Object",
            new int[]{-850,331,83,-443,1000,1000,-1000,247,495,-122,1000,178,-246,-988,795,-114,649,-421,-1000,-1000,90,66,-988,562,1000,-120,-1000,1000,1000,1000,928,-675,-750,1000,836,-1000,-1000,567,-1000,286,1000,-401,865,314,1000,-513,791,254,1000,-640,1000,466,-1000,1000,791,-45,937,-827,966,-366,1000,64,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "remove(java.lang.Object):java.lang.Object",
            new int[]{-1000,345,-111,-322,-101,-126,-571,338,-403,1000,696,253,306,-992,-1000,-388,-1000,-799,-1000,251,-23,723,-173,-406,-151,-1000,-1000,-255,460,-139,168,292,-819,-296,417,-324,-363,1000,-1000,237,600,314,-564,498,535,1000,709,258,549,-1000,-225,-111,703,-289,1000,-1000,1000,-266,-294,-1000,528,993,-1000,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "remove(java.lang.Object):java.lang.Object",
            new int[]{-1000,-143,57,334,-129,1000,-1000,-391,1000,-586,1000,739,835,-536,-57,-346,-525,-210,-930,-1000,1000,684,199,-347,18,-1000,-1000,-118,1000,402,286,-9,-135,809,-344,-879,-1000,1000,-1000,-375,895,-1000,714,-444,100,-50,1000,46,126,-196,724,-371,-1000,926,885,-1000,1000,-1000,841,465,1000,147,-1000,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "remove(java.lang.Object):java.lang.Object",
            new int[]{-84,-577,2,41,-25,1000,721,721,-423,128,-182,-813,-400,272,1000,441,1000,-241,1000,-146,-400,-1000,303,-236,234,-550,1000,1000,-1000,923,248,54,-1000,-195,-276,454,1000,-1000,659,126,-1000,505,145,244,860,527,-601,-144,1000,74,1000,1000,-62,-859,-1000,426,-164,-845,-1000,-629,-1000,-652,-361,44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "remove(java.lang.Object):java.lang.Object",
            new int[]{-339,194,-391,654,-1000,640,-68,-771,278,-339,546,207,1000,345,-656,43,-1000,161,-3,-1000,920,871,1000,-1000,-1000,-1000,228,-1000,-400,118,-254,1000,-1000,-376,-1000,98,359,458,67,27,-353,-19,-325,-903,-1000,1000,829,-794,-1000,-448,-676,-1000,400,-474,-276,-1000,211,-1000,371,1000,-366,802,-102,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "remove(java.lang.Object):java.lang.Object",
            new int[]{-519,-559,249,1000,-1000,22,483,-369,391,-442,-182,-1000,1000,460,750,-147,14,1000,676,162,1000,-345,841,-911,-187,-301,1000,-96,-1000,837,166,598,1000,-502,-1000,321,1000,-1000,659,-177,-856,62,1000,-800,112,471,-1000,365,889,-370,-144,215,-517,-949,-176,-958,-67,-1000,-1000,71,-1000,-133,-897,-555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "remove(java.lang.Object):java.lang.Object",
            new int[]{-815,743,296,-1000,344,-400,483,-230,-480,-143,212,-1000,-382,-84,-783,-74,881,709,-415,-782,-645,-778,-1000,66,1000,575,1000,1000,-934,1000,651,-82,848,-106,-832,180,-996,-1000,588,-638,-144,154,609,-692,1000,-385,-1000,707,1000,-481,177,1000,76,-462,-436,385,159,-816,466,-895,-1000,-544,21,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "remove(java.lang.Object):java.lang.Object",
            new int[]{3,-367,-351,546,-400,-400,-240,496,-920,642,861,417,-272,535,-847,-872,985,-554,318,-219,-623,-653,82,-990,-1000,-56,-1000,-451,405,-112,-369,1000,368,-304,-297,535,-293,1000,-335,-1000,-774,371,-1000,-511,317,1000,-640,-369,-51,198,155,-816,888,252,-1000,822,-1000,-861,-304,-658,1000,-462,1000,-125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "remove(java.lang.Object):java.lang.Object",
            new int[]{940,183,-611,-1000,783,-1000,637,-443,-1000,512,-945,146,-202,160,-1000,-184,-674,-744,688,1000,-1000,-22,178,-102,-923,-88,263,-190,-948,-1000,-546,-460,-1000,-527,-410,682,474,-724,833,-823,-1000,1000,-1000,501,755,118,-1000,-306,-205,1000,-1000,281,1000,-977,-908,-42,1000,-471,874,-1000,824,643,730,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "remove(java.lang.Object):java.lang.Object",
            new int[]{-687,-397,-790,-783,-916,-819,-666,356,-46,931,-138,-1000,805,-1000,-1000,-742,-1000,-889,-1000,-977,-503,798,461,-338,398,-294,-798,-1000,-380,3,239,107,759,270,637,218,-249,1000,-1000,-759,-128,1000,-894,36,1000,1000,831,23,76,-1000,-1000,-1000,918,-719,1000,-962,1000,-1000,645,-68,-104,1000,-179,-969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "size():int",
            new int[]{1000,499,-787,146,618,852,26,-696,43,1000,875,97,329,-25,1000,1000,348,-118,-361,589,233,1000,-1000,-579,-366,418,-191,348,705,-1000,-838,26,192,-1000,-239,1000,256,-334,-115,791,988,228,1000,-478,72,1000,210,-712,-1000,183,834,-51,-56,506,-100,522,30,-1000,-254,-67,-610,927,390,759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "size():int",
            new int[]{1000,-151,-901,915,-516,-1000,906,-1000,1000,309,782,-97,-717,272,275,358,988,-1000,567,-39,4,-1000,24,-496,1000,-130,-849,-603,-1000,-1000,347,-1000,-860,1000,-1000,731,-609,726,-1000,967,-49,610,937,-49,-1000,603,633,129,-688,-29,-877,247,443,1000,348,1000,-441,77,-1000,2,77,1000,-808,128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "size():int",
            new int[]{-356,-61,-787,-759,688,1000,707,-1000,43,1000,1000,-426,77,1000,632,1000,-1000,-1000,-361,705,-12,1000,-777,-579,13,478,422,766,1000,-206,-1000,-870,257,805,-1000,1000,434,-813,1000,202,988,1000,1000,-1000,72,1000,-830,-880,-324,431,569,561,-327,276,1000,522,-941,-1000,-1000,1000,245,1000,390,112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "size():int",
            new int[]{1000,-413,-79,1000,-712,828,-486,-628,199,707,171,1000,-361,-1000,571,-550,711,1000,-120,271,-203,-119,-665,-822,-1000,783,1000,211,-509,-1000,-1000,-543,-297,-62,-363,281,428,-1000,-151,605,-1000,845,888,1000,238,-1000,-306,-66,318,1000,-424,804,179,-1000,-1000,1000,368,1000,95,-414,-1000,-698,235,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "size():int",
            new int[]{595,349,-546,-291,990,-517,-14,-1000,301,-583,1000,-19,-1000,-409,881,-61,215,-349,627,-707,-916,-1000,-86,-1000,1000,-48,-1000,339,-5,-397,-703,-1000,-446,285,-952,890,-805,-19,-39,1000,545,775,176,-1000,496,43,175,407,-580,-177,-313,105,-418,969,1000,-1000,-343,-158,69,-136,703,514,-815,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "size():int",
            new int[]{829,-146,-351,-704,167,-887,-297,-1000,-770,-159,7,-338,-310,-252,795,215,810,-708,666,-136,-720,-1000,-911,-733,659,37,273,146,-558,-128,-10,-318,-312,1000,-1000,81,1,220,-167,-333,-233,-252,928,-1000,-969,148,-485,888,-842,668,-1000,260,-6,1000,88,468,-1000,290,-439,-338,1000,937,23,622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "size():int",
            new int[]{-1000,865,-869,-1000,1000,-1000,308,-1000,-765,120,-161,-1000,937,-476,525,543,431,-1000,397,-687,-943,-217,-461,-602,1000,-163,-605,-21,1000,1000,-1000,518,-640,184,-1000,398,-900,-193,1000,-1000,-1000,104,-266,-1000,-425,-205,-231,383,-982,495,1000,-171,-193,90,1000,348,38,-331,921,-1000,1000,208,-641,-13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "size():int",
            new int[]{-580,655,1000,-136,-101,-1000,661,-58,668,38,555,-896,-281,602,-477,-516,270,898,195,-783,88,-561,557,760,1000,382,-604,15,-21,1000,715,955,1000,1000,760,-199,366,439,-151,-283,197,-983,-141,468,792,-422,-300,-577,-559,-370,-1000,480,-86,140,777,-567,-394,-148,930,-315,-396,966,-887,-117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "size():int",
            new int[]{-585,-73,-63,-782,-1000,-968,151,-454,-369,-906,449,-1000,-636,-640,-1000,-1000,-1000,-488,639,-1000,431,-929,-32,687,1000,141,282,-418,1000,938,-87,871,-695,-27,-1000,-968,1000,-607,191,-1000,149,172,-452,-369,-65,-370,-828,-856,-768,975,-1000,693,-799,510,359,847,-223,-186,-750,361,849,151,-1000,-593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "size():int",
            new int[]{-1000,295,-132,146,1000,-290,-292,-200,998,292,-1000,-113,696,-1000,156,-1000,107,1000,-383,-1000,-1000,-190,649,-1000,571,111,-624,-364,705,974,-1000,26,-690,-1000,125,304,-534,-582,98,904,258,821,-1000,-626,72,-1000,749,595,774,1000,863,24,-151,-1000,336,-459,338,678,1000,-1000,12,-1000,-1000,815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.String:e2tleTQ9LTIxNDc0ODM2NDgsa2V5Mj0tOTIyMzM3MjAzNjg1NDc3NTgwOCxrZXkzPUluZmluaXR5fQ==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "toString():java.lang.String",
            new int[]{-659,683,-496,-102,-965,-1000,907,1000,97,387,167,318,154,80,1000,-377,-337,35,-696,-912,-313,306,1000,120,-1000,437,-575,936,-1000,-1000,-911,955,321,70,-794,-648,-227,-385,400,-142,-147,-714,1000,516,-402,697,-764,-459,1000,-1000,-1000,-94,-667,407,164,147,-573,-84,404,-695,-1000,-633,-690,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.String:e30=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "toString():java.lang.String",
            new int[]{-177,-435,-96,-350,977,107,-418,656,-1000,826,116,13,-481,1000,926,629,-1000,-463,-1000,35,558,272,-1000,-21,-756,276,-1000,1000,-1000,1000,-426,972,1000,1000,-1000,1000,-63,362,-341,-327,-227,972,305,-943,268,-1000,-683,-881,1000,714,-301,-601,-443,827,151,76,125,1000,20,182,-747,-991,337,194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.String:e2tleTA9MSxrZXkzPUluZmluaXR5fQ==", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "toString():java.lang.String",
            new int[]{651,927,-813,388,-224,-1000,1000,-507,-167,282,-226,453,-156,1000,782,841,-323,-556,-1000,-375,340,-553,729,-396,-1000,482,-1000,1000,-624,261,-1000,1000,491,1000,-1000,267,-376,213,342,-420,-284,126,127,-757,-270,432,-1000,-999,1000,260,-1000,-11,-813,-1000,-85,-1000,-230,-1000,-513,-1000,-1000,-925,82,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.String:e2tleTA9MjE0NzQ4MzY0N30=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "toString():java.lang.String",
            new int[]{-1000,-55,387,-1000,623,-987,1000,-939,-249,-332,1000,-1000,-1000,-1000,568,-777,-836,-570,-620,765,748,951,287,-807,-767,-239,-1000,735,-671,908,280,36,-372,-402,652,-528,-176,-536,-1000,-1000,468,1000,-672,308,133,-234,267,-1000,-1000,560,805,1000,968,-1000,-1000,-479,499,393,-74,1000,443,-463,-135,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.String:e2tleTA9TmFOLGtleTE9MjE0NzQ4MzY0NyxrZXkzPTF9", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "toString():java.lang.String",
            new int[]{-872,-419,-141,518,-542,-159,463,1000,-275,-154,-190,-261,825,-925,991,298,1000,715,-383,-779,-25,396,652,141,-829,870,-393,117,-919,-802,-37,348,-287,-734,-563,-156,137,10,1000,226,333,-200,731,696,1000,-374,-127,309,1000,-799,-700,184,371,349,57,1000,82,-1000,257,313,-280,-448,134,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("java.lang.String:e30=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "toString():java.lang.String",
            new int[]{251,283,425,-866,-93,-1000,-873,482,122,368,-1000,661,306,1000,465,1000,-226,-132,-692,-583,-765,396,-984,233,-586,706,260,598,-522,-802,-455,-400,-179,140,-84,475,-203,747,1000,517,-1000,252,454,-700,159,-272,-388,472,1000,1000,-1000,-1000,-62,1000,114,-138,-652,1000,545,-293,-960,-895,36,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("java.lang.String:e2tleTE9LTkyMjMzNzIwMzY4NTQ3NzU4MDgsIGtleTA9NDA4LCBrZXkyPS04LjAsIGtleTQ9SW5maW5pdHl9", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "toString():java.lang.String",
            new int[]{1000,-167,-291,142,-80,-362,-366,1000,-1000,-124,-1000,198,1000,408,971,-357,-1000,200,-122,-259,58,-1000,-962,-119,-1000,1000,-575,645,-1000,-261,-919,-179,-346,127,-694,866,696,588,1000,784,739,160,257,-139,1000,-1000,-1000,-198,1000,-129,-1000,-1000,-639,1000,-1000,138,886,37,-410,-444,-1000,-688,85,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("java.lang.String:e2tleTQ9MSxrZXkwPS1JbmZpbml0eX0=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "toString():java.lang.String",
            new int[]{1000,519,-267,-1000,1000,-1000,-311,207,-263,-1000,-751,-327,288,83,1000,732,-591,-1000,-1000,986,356,-1000,124,-168,-993,996,-5,1000,-249,936,-1000,-193,-1000,-197,400,1000,600,-350,1000,119,979,252,-1000,-365,558,-1000,449,847,972,-108,133,-1000,155,1000,164,126,608,362,-1000,-487,-1000,-1000,-141,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("java.lang.String:e30=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "toString():java.lang.String",
            new int[]{949,-158,364,-1000,-84,-387,-215,385,590,-658,-142,-497,-520,-354,120,493,-22,376,1000,-597,299,129,-1000,-1000,-636,579,-671,-465,954,1000,-248,1000,-1000,30,-676,56,-748,416,15,-118,-7,1000,-1000,-333,497,-766,655,455,-1000,1000,-1000,-36,-234,474,-1000,-1000,-168,343,-1000,1000,1000,-539,-902,611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("java.lang.String:e2tleTA9OTIyMzM3MjAzNjg1NDc3NTgwN30=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "toString():java.lang.String",
            new int[]{-580,-285,-89,-1000,398,-115,-403,523,-299,-774,878,-56,-39,-567,647,841,-576,519,678,-785,362,921,-282,-855,-653,478,-1000,298,-258,1000,-424,-152,-792,-455,-1000,226,-928,264,-163,-1000,-178,1000,-625,-880,1000,-1000,216,341,-165,1000,-1000,607,-300,751,-1000,-595,-222,95,-532,452,557,-571,268,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("java.lang.String:e2tleTA9OTIyMzM3MjAzNjg1NDc3NTgwN30=", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "toString():java.lang.String",
            new int[]{-1000,353,286,-190,-677,-523,588,622,1000,-352,961,-418,-387,-1000,399,-887,833,1000,184,-877,521,1000,249,-579,-365,209,-441,-599,-968,754,436,89,-646,-51,-1000,-1000,-1000,-594,-963,-719,-1000,1000,-289,428,266,452,904,1000,-1000,484,161,1000,-180,105,-1000,-412,-401,-1000,-98,1000,1000,-328,-492,-151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$Values", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "values():java.util.Collection",
            new int[]{-198,171,-922,-168,1000,-165,-791,1000,-400,-405,-400,133,-687,-844,1000,1000,228,-1000,417,-215,-61,-992,-83,-369,443,-278,-948,-186,917,770,308,-960,144,486,5,-149,-178,461,24,-120,-15,11,-1000,-816,138,824,60,-300,-181,1000,187,-336,86,101,136,400,446,212,-416,139,834,-1000,1000,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$Values", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "values():java.util.Collection",
            new int[]{1000,475,264,598,-852,-655,-796,-124,194,222,64,-309,1000,-950,83,619,2,-668,-103,513,-979,1000,-337,1000,1000,831,276,-69,-712,-20,149,635,-939,475,97,842,101,-699,-511,-851,1000,2,380,364,-146,-688,-257,1000,813,-1000,548,-336,-139,-130,-597,-216,-196,-626,-393,129,72,1000,-378,390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$Values", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "values():java.util.Collection",
            new int[]{636,205,-437,264,14,642,-831,1000,-1000,-1000,-1000,1000,664,-230,1000,1000,404,885,765,-1000,173,-828,1000,-1000,1000,-1000,-1000,1000,-242,1000,-1000,-431,727,35,828,-313,-1000,1000,767,-1000,-686,-445,-266,-1000,44,312,994,-1000,-1000,659,881,706,157,-887,887,1000,652,797,-393,164,964,-967,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$Values", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "values():java.util.Collection",
            new int[]{-300,-13,-534,-247,520,605,-572,1000,240,-616,400,398,681,406,584,-156,193,-377,-95,-474,-350,-848,890,699,1000,-497,-1000,1000,1000,966,-1000,167,1000,0,399,1000,-950,-324,316,-321,501,-124,66,-139,-8,1000,-315,1000,43,513,-766,83,-44,38,-333,384,1000,543,1000,-707,1000,-856,1000,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$Values", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "values():java.util.Collection",
            new int[]{841,282,689,246,271,-820,-762,-1000,357,-783,-1000,1000,1000,-157,-201,1000,719,47,1000,1000,692,-807,628,-1000,117,-1000,759,1000,-1000,1000,-1000,701,-510,-295,1000,-11,-260,1000,1000,-1000,-880,-1000,655,-818,182,184,1000,-1000,-485,899,1000,1000,-66,-1000,921,1000,-776,-1000,-1000,-1000,700,-1000,400,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$Values", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "values():java.util.Collection",
            new int[]{-1000,-921,-862,-684,533,-246,-539,322,381,330,-184,1000,419,-964,-94,-25,-117,1000,93,1000,777,-970,-1000,889,-204,129,61,93,1000,300,-741,-868,238,398,-371,106,-84,-1000,-81,-752,1000,89,-967,-366,691,1000,66,-923,-1000,277,803,-906,42,-302,-32,1000,-392,-463,-383,663,646,-1000,111,-293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$Values", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "values():java.util.Collection",
            new int[]{-344,-253,-424,-83,295,-1000,-567,-49,-990,93,-605,1000,-741,-1000,160,1000,475,310,440,-182,300,-548,-764,-1000,-249,517,419,-426,1000,-376,936,-218,-1000,1000,-938,-620,365,954,-492,-499,-1000,-435,262,-177,122,-778,220,-1000,-298,1000,1000,-589,459,-402,137,31,-224,7,-1000,-815,897,-756,215,-148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$Values", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "values():java.util.Collection",
            new int[]{-295,-859,300,-137,520,-565,-227,-881,-811,-1000,-1000,398,-240,303,-1000,-320,-998,-1000,-3,531,566,372,-1000,-681,-1000,-271,835,-51,-1000,1000,-34,-576,-1000,-491,-52,-922,682,1000,588,-531,-1000,-204,-275,-1000,851,394,318,1000,-1000,827,733,-76,-1000,-1000,713,1000,-1000,-993,-1000,-229,-67,-252,-276,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.AbstractHashedMap$Values", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "values():java.util.Collection",
            new int[]{-325,219,564,400,1000,-1000,-794,940,-362,608,915,-1000,109,-1000,1000,570,1000,-657,315,-104,-514,-364,-780,-1000,117,905,433,-212,-819,-1000,779,163,-586,1000,-52,608,1000,260,1000,-501,-1000,-785,-183,500,-561,-293,184,-1000,255,921,450,-929,1000,309,-1000,-1000,-8,358,-1000,275,-909,56,631,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.map.Flat3Map$Values", DEReplay.run(
            "org.apache.commons.collections.map.Flat3Map", "org.apache.commons.collections.map.Flat3Map", "values():java.util.Collection",
            new int[]{477,-449,-132,-468,138,-853,-657,-463,-1000,-469,-1000,944,174,-622,-201,1000,-214,-448,1000,-400,731,-663,-772,-1000,91,-807,1000,459,-823,1000,280,303,-1000,-300,1000,-12,346,1000,412,-1000,-631,-745,222,-22,340,305,-154,-1000,-803,68,1000,243,-367,-1000,1000,1000,-1000,7,-1000,-827,246,-981,-131,-1000}));
    }
}
