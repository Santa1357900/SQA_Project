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
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "addProperty(java.lang.String,java.lang.Object):void",
            new int[]{88,-54,568,-412,180,-745,-1000,1000,-723,-984,-280,-332,836,-1000,70,1000,-1000,757,716,400,-351,-906,265,-192,-1000,-883,-311,-289,-300,364,-94,-977,710,39,580,-1000,-439,-711,-249,50,74,882,726,271,-867,1000,-381,1000,-431,883,-991,-1000,-1000,-23,1000,480,-307,35,1000,-1000,832,1000,-696,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "addProperty(java.lang.String,java.lang.Object):void",
            new int[]{1000,-864,649,-1000,-1000,-357,-116,656,566,-529,-612,83,-1000,220,-1000,1000,1000,586,-27,-351,-137,439,619,-971,-501,861,177,1000,83,-1000,226,287,-805,-1000,-607,-102,1000,-1000,-1000,695,1000,1000,1000,-219,-102,-38,-1000,-352,893,163,1000,-565,167,-636,778,-1000,-448,455,783,681,630,-548,652,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "addProperty(java.lang.String,java.lang.Object):void",
            new int[]{-414,-105,-247,48,1000,500,-337,753,569,-46,395,-127,70,-1000,490,-802,-807,-243,-779,-54,-83,652,-787,1000,-603,-1000,26,-829,-613,-1000,-838,-175,-3,479,1000,-1000,-680,911,190,-274,-1000,-629,-710,-212,601,367,85,-830,-1000,487,-92,-590,-389,66,18,959,693,442,370,-33,486,655,-596,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{-1000,735,-247,497,1000,-668,1000,1000,1000,-23,1000,813,1000,640,290,-312,-1000,302,1000,-304,1000,-424,1000,-52,858,1000,736,1000,-61,1000,400,506,7,-687,152,-256,344,-1000,955,-391,981,-48,-1000,1000,18,102,-198,1000,259,-424,679,114,36,298,-1000,86,-1000,1000,-411,755,-1000,832,1000,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{-784,393,-31,890,359,-1000,383,100,478,-319,-469,17,214,784,-222,-438,-502,-740,436,483,43,-1000,424,1000,590,140,836,696,-319,546,-1000,-115,-1000,-223,-1000,150,349,-725,-453,-390,807,577,-74,786,218,-990,-1000,43,-96,151,0,-228,-1000,778,171,1000,-625,45,-915,844,-1000,-94,-326,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{840,381,838,-354,334,7,-160,-11,-311,438,-716,-1000,-1000,342,-62,-229,-424,452,-1000,386,110,380,-1000,-417,1000,-1000,817,426,-1000,278,1000,1000,1000,-136,-1000,-1000,-591,-1000,-231,443,649,-834,-364,-1000,1000,-1000,975,1000,-1000,253,-778,-333,-758,-723,-1000,-1000,642,745,-428,-736,1000,1000,542,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{-176,-267,-666,224,-255,814,-535,1000,-947,629,741,1000,1000,316,772,-31,634,264,66,1000,-165,-183,399,957,-344,63,-656,-93,570,108,-730,-709,-23,185,-89,473,-625,300,259,522,-855,25,1000,397,-579,203,61,-91,1000,-81,-12,-226,-193,777,213,590,-345,727,278,1000,-751,-398,-860,277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{-936,168,-701,-196,-459,792,-1000,345,-494,1000,-94,308,-400,-408,-230,384,855,777,309,1000,460,-342,58,323,-702,-1000,-911,407,85,-513,-174,-738,-66,248,-1000,398,-771,-207,-161,754,-600,-562,392,519,-769,-51,940,1000,518,-174,-34,-613,-1000,1000,213,-23,110,1000,-160,466,623,217,-1000,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.ExtendedProperties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "convertProperties(java.util.Properties):org.apache.commons.collections.ExtendedProperties",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "display():void",
            new int[]{461,-330,-882,779,977,1000,-76,922,599,1000,1000,-850,513,338,-1000,1000,599,1000,-1000,-5,-375,404,855,-579,-1000,-1000,-1000,-540,265,-721,965,-498,458,375,4,368,-1000,-465,-830,-908,-782,1000,-600,-1000,-835,-1000,420,1000,1000,1000,-1000,-794,1000,-821,629,952,-1000,571,1000,-1000,1000,1000,281,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "display():void",
            new int[]{364,-741,-161,479,-39,241,-1000,-189,33,-707,948,542,286,52,-782,938,-40,724,649,585,-305,104,-609,311,285,-986,654,268,-199,-264,656,-342,-78,-1000,1000,1000,-80,-385,-409,282,1000,220,98,-177,540,-395,510,-837,712,1000,217,1000,-974,265,-451,428,405,-244,324,446,6,-908,-512,795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String):boolean",
            new int[]{913,-561,14,-1000,-1000,-30,-775,-203,-411,338,-134,-1000,-1000,-650,-1000,-62,338,-53,351,-1000,134,40,565,-1000,-53,-735,341,801,884,-242,467,-688,303,-897,-1000,-483,814,854,-1000,1000,371,289,534,-406,-559,-793,228,-646,505,132,-5,-958,500,-398,-899,-795,-855,-189,-29,-959,-1000,-881,427,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String):boolean",
            new int[]{207,-282,146,18,-511,-748,357,-463,-1000,401,-181,-786,-400,424,-486,414,469,83,-499,-241,-399,455,1000,72,-719,-610,-299,839,-814,-739,-123,670,936,-801,-1000,-666,493,-950,-825,724,1000,-373,-896,-516,-1000,-398,-273,503,-50,1000,-827,-393,-155,-195,-410,583,-1000,-1000,163,-889,-1000,-451,-1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String):boolean",
            new int[]{517,-492,-532,-918,-295,324,-775,110,163,-143,703,-476,-400,-1000,-1000,781,-808,-690,-315,-491,961,-1000,-598,-72,334,34,1000,1000,1000,557,878,-266,514,-1000,-509,-1000,1000,748,-1000,537,185,717,1000,-1000,-427,-1000,-101,-1000,1000,-375,-108,108,-1000,582,-635,-110,-694,-572,-1000,-471,-565,-285,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{838,-777,-822,827,-14,802,-494,1000,-483,-343,89,-193,789,-627,-553,-488,-284,400,227,-1000,-270,-179,351,-1000,-1000,-766,-863,676,975,-49,1000,637,-945,1000,585,-341,-505,-186,-593,614,-726,-859,244,216,99,-441,-1000,-843,654,-615,-158,573,-62,1000,-1000,1000,271,-408,122,-200,1000,-734,569,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{734,393,584,1000,250,897,-1000,1000,1000,438,65,403,17,-647,1000,-832,524,-400,325,50,-1000,-1000,234,-1000,-1000,158,-1000,1000,-516,-86,-191,829,-44,1000,-1000,-1000,1000,-273,-716,1000,-1000,1000,350,-330,-371,1000,-1000,731,827,-1000,-1000,1000,-1000,1000,284,-386,-528,1000,1000,-1000,152,-131,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{-295,-423,-116,-626,831,-250,-1000,865,-1000,-1000,-1000,70,563,-947,317,-1000,1000,501,1000,-962,1000,1000,802,246,-1000,208,632,-1000,452,327,-1000,160,-971,-277,440,170,1000,1000,753,-1000,744,434,-1000,-1000,1000,-1000,413,608,-1000,-1000,1000,-903,409,-554,-1000,-1000,-1000,-720,508,-1000,618,-766,1000,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{-559,915,109,1000,-672,977,220,53,51,907,872,-473,-1000,979,-1000,-429,-1000,219,-630,-1000,180,-959,-391,-1000,836,-848,385,1000,-482,-497,-1000,168,211,627,1000,-878,-1000,-583,94,1000,-1000,-258,-217,1000,-86,1000,376,-99,-171,234,154,1000,365,741,557,-272,-287,1000,-219,1000,356,-287,-1000,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{-1000,-510,-182,755,1000,-1000,-378,-166,-1000,1000,-1000,1000,697,1000,-726,-804,1000,1000,585,920,62,1000,-1000,-1000,362,407,-906,-924,820,315,1000,746,1000,200,1000,1000,1000,477,-611,340,-1000,-684,1000,-179,1000,455,-1000,-264,-895,94,1000,-674,635,216,167,940,-755,299,1000,1000,-1000,-754,583,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{-787,-786,44,-492,459,503,-1000,574,-1000,861,-50,-960,277,-547,603,-236,1000,-243,-892,467,495,-1000,-1000,461,334,253,-927,-80,-832,-339,1000,-644,1000,240,796,-721,466,-1000,571,-1000,1000,436,400,778,-644,914,-690,403,-570,-464,-310,596,778,-387,-789,-1000,-829,-1000,67,-20,941,497,-1000,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{-1000,-477,-671,1000,324,-915,245,-18,563,181,-608,-965,-218,-146,1000,819,-307,-1,-133,305,114,-673,1000,100,145,376,945,-770,-924,-987,-828,-1000,443,-807,447,-432,-1000,-380,1000,-711,991,1000,-437,97,-30,-1000,1000,1000,60,-1000,-695,-388,187,-815,-79,794,-840,-484,-861,134,786,-914,-342,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{257,-915,-511,263,-303,-374,50,-542,598,-115,630,-120,1000,46,-756,-502,1000,-404,1000,-399,839,-1000,918,569,-1000,-351,860,847,-133,-1000,-366,1000,-428,-703,-968,-115,-258,474,55,-614,-205,-223,736,-812,-611,-468,1000,-1000,308,550,821,-557,171,828,-680,57,-751,-1000,-216,-129,-183,-1000,-922,-157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{-281,699,-752,64,96,-241,-1000,-491,101,231,-35,-376,571,-646,1000,-127,442,1000,-1000,-172,666,794,-1000,-1000,146,-1000,703,-12,-51,-766,209,743,610,-899,-49,-247,1000,-862,138,558,1000,-534,682,-1000,732,216,-639,-806,-671,-921,-996,694,872,-514,975,-1000,321,-770,1000,-756,-741,902,71,-189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{-130,441,-886,-715,-1000,-378,-314,-1000,-1000,262,521,-1000,863,-382,-1000,1000,1000,658,352,380,-576,48,53,-1000,-211,1000,-434,-518,966,-289,-858,332,1000,-502,161,900,-1000,-226,863,369,249,293,-321,-101,-691,339,1000,207,107,400,278,366,176,-1000,-1000,99,-299,-461,-1000,-1000,-314,787,-753,304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTY=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{-549,-15,309,677,-963,-1000,-931,34,-468,-692,1000,-69,-47,-618,-84,-312,-444,-1000,-1000,1000,-711,158,700,504,-1000,-336,172,-385,-873,-1000,-808,-663,1000,-272,-339,800,309,726,859,1000,-279,-59,471,141,-757,71,-300,319,-798,-489,54,-69,34,-833,-241,587,-11,529,-466,37,-1000,-649,-360,569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{1000,945,304,653,841,-1000,-1000,685,-42,-25,895,-856,1000,-778,-1000,686,973,174,244,-585,-1000,-285,1000,172,560,766,-1000,-1000,-405,1000,1000,1000,-340,-1000,-28,1000,1000,-1000,-115,1000,1000,1000,1000,-655,1000,-1000,291,-1000,-738,-1000,1000,-1000,-1000,-1000,777,1000,-1000,-392,521,-939,-1000,-806,671,-399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String):double",
            new int[]{1000,-90,-981,1000,1000,344,-193,-1000,893,179,310,865,-1000,736,-610,-533,-61,-54,-1000,1000,-1000,1000,792,694,471,-52,-1000,-141,-1000,-183,1000,-48,-633,-1000,-848,1000,-585,1000,-131,414,-1000,702,223,1000,-239,1000,611,-1000,521,-715,233,-1000,1000,711,-1000,1000,250,-1000,-842,520,11,1000,257,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String):double",
            new int[]{-1000,585,-121,-127,1000,543,-1000,1000,-1000,-36,1000,-1000,-1000,-1000,-575,-241,-1000,1000,-360,918,1000,-1000,-886,72,-1000,619,-820,1000,949,-40,595,848,-1000,88,78,531,1000,-682,679,-196,1000,1000,597,496,823,1000,63,-444,443,367,-30,1000,-1000,-997,-768,661,840,-335,-1000,-1000,1000,-903,432,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String):double",
            new int[]{-574,939,803,1000,816,390,-735,1000,-1000,-867,1000,198,-1000,463,-785,-632,-67,64,-545,435,-78,823,-357,-614,774,22,256,97,-610,42,1000,-417,379,-980,-220,636,-993,1000,-854,122,270,400,1000,1000,-204,400,181,-1000,852,329,692,-1000,232,-188,-537,860,-386,-1000,-621,168,161,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{223,-768,768,534,1000,-1000,-1000,1000,-1000,791,-1000,-663,-444,1000,-1000,1000,-1000,-1000,-779,625,-1000,799,1000,1000,-1000,1000,1000,1000,1000,233,-1000,-183,-69,71,247,1000,553,-1000,1000,-502,1000,-1000,-1000,-787,1000,-325,-328,-1000,536,-1000,-716,-1000,1000,1000,1000,-1000,1000,1000,827,-802,-223,-1000,504,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{-418,-696,-741,-751,1000,-138,-1000,-72,-146,417,-949,-150,-428,304,258,1000,-670,-830,374,1000,-352,1000,-90,-332,677,-240,120,782,458,-565,-626,1000,-1000,-63,185,236,-274,108,-246,-579,-425,-950,-1000,-1000,-80,953,358,-119,279,-1000,-784,-674,741,863,190,-503,530,-823,1000,815,1000,175,271,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{-1000,-261,747,311,1000,-1000,-1000,1000,-811,1000,-1000,-358,-1000,1000,-1000,228,-1000,-1000,-564,1000,-500,-1000,1000,1000,-469,1000,663,1000,-840,-1000,-816,48,-688,1000,-310,896,-278,-1000,672,-1000,825,-819,-1000,-1000,771,141,-228,-809,512,-1000,-1000,-1000,-1000,1000,1000,-1000,162,-8,1000,-214,-1000,-1000,706,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{-1000,249,-86,425,-547,1000,425,825,376,-1000,1000,212,1000,142,-389,110,1000,-476,465,914,-326,-807,758,1000,-1000,-1000,906,-952,1000,-775,-896,-726,-641,-993,-265,-403,651,-616,130,-96,138,-281,-720,-350,977,-458,275,252,56,-1000,279,497,856,964,553,612,410,-711,-345,-336,-667,724,793,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{-1000,138,-508,251,380,278,330,-395,-1000,-271,1000,-160,267,656,-520,-442,-942,-328,-282,-87,-1000,-687,289,162,339,-703,-262,924,533,-637,144,-264,-455,225,-76,20,-522,-290,-576,673,-108,-824,105,251,-1000,628,525,732,-964,75,570,1000,-87,-60,1000,1000,183,307,509,158,-1000,-609,-37,-98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{-1000,243,153,79,857,1000,-931,123,1000,-1000,-1000,-123,-1000,-525,-571,259,1000,-728,599,1000,1000,-1000,843,-685,-1000,708,749,419,542,-1000,1000,-1000,709,-1000,681,-82,197,638,185,-961,414,774,-608,855,753,201,-242,-33,874,0,1000,-528,1000,789,-1000,-32,-882,1000,-992,-377,-960,891,1000,-922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{1000,249,-491,365,775,1000,-980,843,-823,665,64,685,-813,-217,366,64,417,1000,712,230,-102,709,1000,747,-77,406,-1000,-644,600,-150,108,-71,359,-640,-326,-1000,1000,-472,-500,951,1000,322,75,159,-708,-663,-835,530,-555,-1000,-1000,-239,-163,681,-1000,675,208,-847,578,-350,1000,377,-726,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{1000,126,-312,360,-79,23,322,-386,136,-404,-444,1000,284,815,-161,1000,-158,1000,-385,394,-85,214,208,-47,-298,-9,-1000,-853,-507,-938,477,-1000,-149,-1000,-1000,-286,-418,921,-720,1000,1000,838,-616,1000,-1000,-298,-1000,1000,345,-789,1000,314,446,1000,-1000,184,765,6,1000,-1000,917,-382,607,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{263,-180,-436,-74,244,316,112,447,-271,-466,273,874,224,-189,-270,173,-1000,704,-967,711,-1000,-135,867,-225,552,-455,-1000,-612,-402,-1000,-435,373,375,-969,-1000,-718,295,-200,-1000,1000,652,893,741,1000,-1000,385,-1000,96,606,-852,-43,-154,1000,1000,-893,1000,286,-413,858,-1000,1000,-922,865,-255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{749,-57,213,726,-876,-339,1000,-1000,552,-397,-904,1000,650,724,-737,867,-691,481,-1000,-610,-764,-894,252,-1000,546,-1000,-958,-798,-1000,-1000,-26,-1000,-712,-1000,-339,1000,-721,1000,-390,1000,959,838,-670,1000,-1000,1000,-1000,1000,1000,-318,1000,-21,713,437,-644,184,1000,1000,1000,-1000,1000,-687,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{150,-243,-652,-828,226,513,-571,320,-1000,-370,1000,-747,-535,274,1000,517,-1000,-298,305,-598,-814,1000,1000,407,-505,1000,907,-840,-1000,-574,1000,405,-70,-179,-230,-775,1000,392,-801,-1000,834,-1000,1000,407,-782,-406,-134,-92,414,-859,-1000,343,-1000,426,-702,2,192,-1000,47,107,-1000,374,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{633,390,288,410,-670,1000,-487,270,114,-48,143,-649,-1000,-521,-344,1000,-1000,-641,692,-1000,858,774,-850,203,834,767,-789,-500,-827,-1000,-56,1000,-566,-693,-837,1000,-937,1000,-481,-1000,235,-90,-724,950,410,-372,612,-957,-15,-1000,-830,1000,-1000,-1000,853,-120,-1000,1000,-1000,-1000,-853,-42,-736,224}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{726,189,84,-1000,-21,-229,-873,1000,79,996,-88,683,355,882,-415,210,993,-445,1000,561,269,-647,-206,363,463,-671,655,431,656,1000,819,-1000,819,-684,-848,-566,-708,-1000,664,821,131,-453,-677,-502,401,-138,42,-750,-1000,-384,940,418,884,1000,-131,-108,1000,292,1000,-397,409,-919,1000,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Float:LTkuMjIzMzcyRTE4", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{753,-189,-537,-358,27,837,756,-682,260,154,-32,768,-366,685,-625,-470,341,-613,-38,1000,-577,-909,-600,606,-801,-411,-32,-221,423,164,-353,-248,-94,-301,-498,146,1000,-992,920,837,-229,400,443,1000,954,-501,309,263,684,902,1000,-643,559,-511,-334,13,1000,-574,823,387,486,-1000,998,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Float:LTkuMjIzMzcyRTE4", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{421,504,-291,-49,842,-1000,-1000,-254,371,-824,-207,-795,913,-22,-1000,470,1000,958,-214,4,-572,397,1000,-499,493,-866,-866,673,-539,409,1000,1000,368,-645,167,77,-453,1000,-120,-194,233,973,148,-1000,-260,-414,-154,368,1000,-58,1000,340,498,-1000,1000,466,-1000,-1000,-500,-408,-1000,-394,394,164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{396,-675,-48,601,-685,-212,-253,424,-1000,1000,104,753,-1000,446,-605,308,1000,-1000,717,-251,-1000,1000,613,-1000,680,325,-1000,1000,875,338,356,1000,-12,199,-1000,-596,126,-206,-825,1000,232,809,-707,-1000,752,1000,721,393,1000,1000,-1000,726,815,1000,1000,-749,-57,256,26,-719,324,168,1000,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFh", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{-10,-993,-621,-98,440,1000,3,537,-1000,1000,1000,1000,908,-1000,1000,-88,827,1000,1000,585,-1000,910,1000,-1000,-774,1000,1000,-166,-1000,-1000,1000,-145,-368,-1000,316,1000,56,119,-887,1000,-1000,-1000,157,1000,-236,1000,1000,-300,-204,232,210,-567,1000,-225,-1000,324,1000,-1000,1000,-1000,-9,827,390,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{-482,174,283,-764,-776,111,709,-820,450,-738,683,-643,-455,-404,348,278,595,-733,-507,798,343,247,-809,327,593,-574,-148,-417,964,452,-745,350,748,10,-737,-398,-448,263,-867,923,373,720,573,237,-188,-68,-911,-463,255,107,-520,-519,6,-8,863,-434,-402,-828,84,885,-177,317,-37,-598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.String:aW5jbHVkZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{113,-273,179,-25,-1000,343,507,-1000,-1000,236,-491,-520,-335,-194,383,554,183,48,384,665,-781,1000,98,-1000,-1000,217,-104,185,633,-895,817,-1000,-96,131,-683,-516,1000,40,-153,62,-572,-252,629,-268,30,743,316,1000,498,694,351,-318,1000,602,-209,-509,1000,-554,356,182,-1000,21,-120,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{369,249,623,-460,-482,-1000,-661,989,531,835,1000,-1000,-1000,-228,-563,-968,-505,-1000,-405,155,1000,-645,-991,1000,429,-282,110,-1000,-498,-205,772,-1000,-970,798,83,-176,1000,999,-315,-1000,365,331,49,221,1000,149,1000,-427,-677,-162,83,-143,1000,-7,421,-1000,325,519,-260,220,33,-239,364,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{-252,-633,598,-410,0,-1000,-73,1000,863,47,1000,-782,-527,140,-508,-1000,-1000,-129,-1000,1000,1000,-79,-1000,1000,-288,-1000,-241,-1000,-1000,-298,840,741,-1000,1000,-237,-503,1000,1000,-444,-1000,-43,-72,-699,119,507,596,713,114,-1000,805,82,700,544,-116,1000,-1000,122,1000,-833,958,-123,-889,-104,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{-745,87,729,1000,1000,14,-1000,1000,-1000,-1000,-1000,-1000,-1000,759,791,1000,-1000,-769,-829,664,-1000,270,-1000,1000,168,-869,185,-462,958,-1000,257,-1000,-1000,-1000,-1000,1000,-966,-348,1000,194,-1000,-1000,-1000,-1000,-1000,-807,878,-1000,-40,1000,-93,-445,1000,1000,143,120,-446,-176,-1000,918,-1000,316,926,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{-1000,-879,743,167,658,317,-348,652,141,528,-446,91,649,1000,362,427,1000,-214,-137,440,-551,31,-889,-344,-245,-1000,478,120,354,-216,-139,506,-319,-504,613,1000,519,228,958,907,-1000,33,342,-146,-608,-1000,72,-408,-152,-285,1000,53,1000,-485,647,1000,941,574,-120,309,-720,-249,138,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{-1000,24,389,1000,587,-1000,456,82,-691,886,21,871,815,425,-568,209,-1000,1000,-1000,1000,-235,917,-86,-816,-1000,448,-610,-1000,294,-1000,1000,181,-1000,528,-271,420,1000,1000,1000,-1000,-1000,-1000,356,1000,-636,-987,382,-1000,1000,-511,-1000,953,1000,1000,-446,1000,280,992,-251,-999,555,-474,532,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{765,441,-677,594,-382,-1000,160,-116,-672,-138,466,661,669,1000,-564,-826,640,-601,-39,-596,-687,402,-60,-137,528,-1000,794,-741,811,-681,240,-120,-1000,460,7,420,661,-521,1000,1000,511,495,608,-484,874,-1000,-696,326,401,1000,-516,-583,-106,-781,-701,-156,125,471,-1000,1000,-1000,1000,-648,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{-9,894,884,419,-1000,-4,-1000,922,-1000,382,237,661,345,381,-8,422,-444,906,868,-1000,293,429,104,1000,248,187,180,-786,-554,10,1000,-762,-1000,38,993,-654,-657,-729,603,-754,-315,45,1000,-370,1000,-722,907,173,1000,853,-1000,-216,-1000,-1000,-652,829,-1000,-770,-704,1000,-598,1000,-803,173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{-1000,-426,649,358,-304,546,-909,357,-86,-173,737,-407,-378,588,1000,-40,-1000,1000,665,-1000,292,-997,-983,918,-16,1000,1000,-458,-977,49,1000,-324,141,5,14,-623,-1000,-472,1000,-701,-307,247,-89,47,874,1000,706,1000,1000,-337,866,-300,-1000,259,172,-78,-899,173,-1000,247,-1000,480,825,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{-368,906,-98,-180,81,1000,-1000,619,-1000,-1000,334,-759,445,349,716,-1000,208,710,9,-311,1000,-246,462,-1000,1000,-288,498,1000,-819,683,1000,96,-648,-337,280,-1000,-1000,-1000,-772,-192,-784,-767,-671,119,-1000,-446,-578,534,633,-348,-13,496,-346,-323,-336,637,-512,331,250,516,460,538,-722,627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{100,-888,664,-148,1000,573,-180,-1000,1000,74,405,1000,282,917,-152,614,92,-1000,443,-431,1000,866,-592,-342,-1000,-1000,-199,-1000,-595,375,-639,-1000,309,200,-345,1000,-1000,413,984,-1000,-102,-834,399,-496,-156,1000,-427,-975,-591,-64,1000,-631,1000,-847,12,-48,585,-966,-515,-160,-1000,93,-280,882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{400,-726,974,259,1000,-195,-506,-304,-1000,479,571,1000,681,557,682,435,-674,-672,-313,1000,411,1000,-74,1000,30,-1000,267,-103,-634,733,799,-1000,455,924,-23,1000,-1000,1000,656,-262,-97,178,-121,857,1000,1000,191,-1000,-1000,-1000,1000,240,148,364,508,-799,377,-1000,72,76,-885,791,677,823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{100,636,-467,1000,-660,1000,-1000,373,917,1000,-1000,1000,1000,832,1000,-1000,1000,-123,1000,-66,-1000,-1000,1000,-285,300,-1000,890,122,-1000,827,735,55,-120,-33,760,-1000,1000,644,-1000,-1000,-148,-31,648,1000,-1000,-431,-557,-361,-591,541,1000,998,-1000,1000,12,760,1000,1000,1000,-160,628,-1000,710,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,java.lang.Integer):java.lang.Integer",
            new int[]{495,-621,959,-1000,-799,914,-28,812,-352,-757,1000,-998,1000,-1000,1000,1000,570,56,61,744,-1000,-488,-859,478,-799,400,-1000,1000,1000,1000,-134,557,-567,-172,-365,-489,1000,-1000,199,1000,1000,71,242,391,-1000,-1000,-954,-814,1000,313,-1000,-1000,-860,940,390,-779,17,-1000,-1000,-682,-7,-1000,987,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,java.lang.Integer):java.lang.Integer",
            new int[]{1000,-390,-86,71,368,1000,-106,633,232,-160,-969,-1000,1000,-909,1000,1000,822,-695,-1000,1000,-182,522,-1000,969,563,1000,-617,1000,1000,1000,-54,122,-924,856,133,-1000,47,-1000,28,808,1000,-381,1000,-484,-598,1000,-1000,-1000,1000,576,-411,-617,-1000,1000,1000,-1000,-1000,-419,-1000,-1000,-305,-47,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,java.lang.Integer):java.lang.Integer",
            new int[]{-1000,-684,277,437,1000,-1000,816,1000,809,-244,-304,1000,-1000,1000,-1000,-1000,1000,466,-265,1000,263,-482,-1000,-331,-803,-1000,1000,-556,-1000,-1000,-1000,-1000,533,-605,908,717,-925,1000,-381,-1000,-949,723,-889,1000,620,1000,1000,1000,-1000,764,1000,-657,-1000,-1000,17,-240,1000,1000,1000,878,-667,-625,-1000,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys():java.util.Iterator",
            new int[]{1000,-534,699,-10,-432,1000,798,-115,1000,1000,-961,-60,-766,1000,-379,-125,653,-438,1000,299,-862,-909,-1000,-1000,-917,-266,-1000,-1000,-272,488,1000,-206,-1000,-243,-1000,-90,-1000,595,-1000,-316,483,-1000,-237,-1000,457,477,-450,-1000,-148,-1000,303,-426,1000,-4,455,888,29,-351,-720,88,1000,796,1000,267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys():java.util.Iterator",
            new int[]{978,-696,-987,1000,94,-1000,440,-565,322,-89,-908,-837,-622,1000,400,376,-845,-120,-1000,-1000,773,562,576,-253,-468,-587,487,801,-607,-523,1000,-1000,1000,797,1000,752,-1000,919,1000,1000,496,-1000,127,1000,1000,428,-1000,-1000,527,-204,-973,-965,-597,-1000,-1000,1000,-1000,1000,-22,1000,-1000,318,-1000,613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{524,24,-846,-320,-656,-1000,1000,-85,-1000,1000,75,1000,-1000,-377,932,-1000,-1000,195,-541,279,889,1000,-695,1000,310,-51,-1000,-164,1000,1000,644,890,412,-1000,1000,-1000,-935,-709,1000,1000,705,-400,-37,1000,1000,253,-736,-1000,971,-58,1000,-16,-272,-1000,1000,-1000,-44,363,1000,-1000,7,801,1000,-986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{218,45,-287,-698,-995,197,582,295,-659,-402,-1000,140,-342,-847,937,-144,-783,384,-277,-1000,829,599,170,1000,215,-348,-320,497,1000,684,109,636,110,-391,-52,-1000,573,-950,413,1000,-371,-348,153,1000,521,264,873,-581,-378,334,282,1000,-405,-652,1000,-407,496,363,828,466,-886,868,285,-936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String):java.util.List",
            new int[]{-1000,-63,469,-1000,-219,-1000,-704,-1000,593,875,-1000,-185,362,751,819,-1000,567,200,1000,-380,-827,-40,1000,718,-160,773,-1000,858,-1000,-643,-654,-7,1000,-1000,-336,-1000,1000,552,-1000,762,63,-1000,-1000,1000,-1000,1000,-442,1000,-650,1000,-1000,-506,-341,-26,1000,-351,-381,-21,-603,1000,-371,432,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String):java.util.List",
            new int[]{152,-528,548,-1000,-373,254,-468,-862,131,597,-544,1000,820,-852,895,-60,402,194,72,-1000,762,467,449,931,406,-678,-836,-290,-483,-683,-26,865,-1000,708,1000,722,693,807,-325,1000,-524,-400,-636,-510,-830,406,367,532,-1000,1000,435,-1000,1000,304,1000,740,-244,-481,68,-858,-42,976,-266,457}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String):java.util.List",
            new int[]{1000,492,-407,-96,62,-1000,-661,-1000,527,607,396,735,-469,-287,1000,-1000,-1000,743,1000,-1000,-98,861,-654,382,58,96,-644,250,-663,-1000,1000,-349,37,-1000,1000,-1000,212,706,457,-220,-1000,731,-320,-342,-757,414,-342,970,542,-1000,-543,-1000,400,1000,237,208,1000,-927,142,-690,-784,38,-953,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String):java.util.List",
            new int[]{765,-204,-513,269,-766,-101,1000,-352,811,-14,352,94,-1000,-984,914,809,-573,117,-286,-307,284,-1000,-621,-274,-88,378,332,180,1000,78,190,330,990,1000,-1,764,-902,26,511,-6,-289,959,1000,-640,18,-480,-233,-814,-1000,1000,1000,433,-118,-872,7,-363,-500,1000,-160,-646,-196,-105,-282,-817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{1000,-966,-386,-685,1000,-868,-1000,-1000,245,13,-1000,-1000,590,1000,128,717,1000,1000,-98,1000,-288,-943,-1000,390,-1000,916,381,1000,-220,-850,-912,-1000,-1000,64,68,1000,-510,248,1000,-1000,1000,1000,1000,322,-1000,-818,-578,-781,1000,118,-1000,569,1000,743,1000,896,1000,-122,-1000,-1000,-58,-389,-868,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{770,-414,433,626,11,-102,131,-1000,-284,440,-1000,586,-183,1000,180,-1000,102,980,937,1000,-615,961,-838,-1000,387,169,1000,1000,754,170,1000,-165,-273,80,-431,-160,240,-1000,1000,1000,-665,-198,-753,-1000,-212,-46,176,61,945,204,585,152,615,1000,-1000,-663,436,595,-569,1000,-952,1000,-666,-756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{-58,-879,-111,-37,-355,280,-969,-119,-701,-940,452,-497,807,897,442,-235,342,544,452,746,-122,-400,-805,-490,-272,-957,400,113,196,1000,400,666,-212,-512,462,66,-586,400,832,-783,-794,-1000,-358,962,-400,1000,-659,226,-405,115,-797,-803,359,-242,1000,-832,-663,210,-400,1000,-464,288,-750,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{-1000,-909,719,-1000,1000,1000,34,347,-103,-529,-1000,-272,-809,291,-627,-715,485,-663,325,-1000,-617,-1000,-889,-382,-163,408,-1000,-357,893,-1000,1000,1000,1000,-1000,-1000,390,849,-383,-180,1000,573,258,-1000,-1000,1000,-224,576,-157,1000,1000,-718,262,701,1000,-504,978,-980,1000,-185,-1000,-222,-1000,42,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{-1000,300,459,653,1000,-67,-1000,648,1000,-1000,-680,192,624,393,411,-540,851,80,-561,-1000,-1000,745,-403,-818,226,537,-1000,-430,-233,320,1000,537,969,840,1000,783,-472,-1000,-268,-585,1000,188,-1000,273,1000,-504,-1000,-527,1000,925,194,326,-451,1000,-423,-85,-111,577,-243,-674,-288,-52,1000,475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{-828,735,364,-520,-343,948,-885,1000,-36,877,774,1000,232,141,-1000,199,-157,1000,-473,1000,-753,-1000,-779,-1000,975,-653,-885,-1000,1000,-245,-1000,720,-458,-361,-755,169,59,-424,-1000,-503,-382,-1000,1000,114,1000,-1000,-243,-1000,938,-119,613,-53,67,-577,413,1000,-515,-324,931,1000,752,1000,1000,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Long:LTEwMA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{-55,489,-437,-948,-3,1000,-260,632,176,458,-82,1000,-174,-751,-1000,-32,12,-505,-851,-851,186,-1000,-326,-550,677,-868,-389,-83,1000,-867,623,416,262,-404,264,131,-162,-1000,454,1000,-185,986,716,517,1000,-870,-80,-778,747,590,66,382,-319,-985,899,-1000,394,-1000,1000,-497,1000,1000,205,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{-633,972,504,-641,-571,62,-175,570,-983,362,181,722,-334,-181,-1000,631,-678,282,-568,19,-285,-1000,-590,-987,1000,-1000,109,682,1000,65,-1000,100,-728,288,70,1000,1000,-1000,-217,-580,-365,-1000,155,939,1000,-1000,37,-952,140,922,710,-80,83,-1000,617,104,885,-248,1000,31,1000,1000,636,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{237,-252,948,179,1000,400,1000,678,1000,-621,-640,-469,-363,1000,888,-790,1000,-604,844,-82,42,112,580,-939,-1000,1000,598,-164,1000,539,-1000,1000,-906,1000,-698,363,-211,-1000,-1000,-154,-1000,-247,-460,-596,-105,1000,313,-548,384,546,-728,-1000,-446,-776,-725,-183,1000,1000,1000,1000,-1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{1000,-294,-229,-102,545,-119,-244,-262,-459,545,204,237,-168,1000,-155,734,473,166,300,331,-1000,679,-219,-1000,73,-86,461,-1000,342,1000,-198,-438,-1000,937,1000,953,-112,-33,-423,911,24,-1000,-1000,-1000,-1000,714,244,-524,-367,-500,-471,-27,-112,978,-234,-634,1000,400,-294,-26,-1000,-280,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{402,504,554,-18,358,-108,-126,1000,430,642,-81,79,-316,934,-1000,354,-984,1000,536,87,-1000,1000,1000,-400,-560,110,1000,-1000,104,1000,-1000,-148,-1000,1000,596,1000,1000,302,98,-52,1000,1000,-1000,-1000,539,879,569,953,392,-969,-1000,1000,932,-1000,-836,1000,1000,-965,-1000,-915,-1000,-1000,-619,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String,java.util.Properties):java.util.Properties",
            new int[]{-124,666,-32,-517,1000,-1000,-67,1000,-377,145,-50,1000,253,654,-621,-1000,415,-1000,-198,-505,-311,-559,623,572,1000,13,-1000,385,-242,1000,-411,527,-10,-555,692,1000,441,-69,-86,127,384,983,922,1000,-63,-687,596,1000,261,-995,26,-211,440,-443,620,-46,590,261,-1000,541,-765,-920,42,591}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String,java.util.Properties):java.util.Properties",
            new int[]{-124,441,499,-701,1000,-640,388,845,-786,145,-25,1000,436,1000,-1000,-1000,249,-1000,-50,-333,-375,-330,612,572,894,-573,-1000,1000,-486,1000,-490,936,-645,-1000,538,1000,-305,411,52,-111,487,977,508,1000,-705,-11,1000,1000,-118,-949,111,-146,423,-47,1000,-196,391,656,-1000,292,-717,-1000,42,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperty(java.lang.String):java.lang.Object",
            new int[]{-487,573,-561,-200,-979,692,1000,572,-36,335,-1000,1000,892,387,-1000,145,1000,-1000,-28,-1000,927,92,-932,692,-287,1000,831,-1000,1000,153,-844,795,-547,823,-238,775,-374,-209,-228,-320,514,278,466,596,-493,-152,-462,331,-1000,50,458,454,-427,-224,701,-174,-226,-839,-586,490,834,-289,575,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperty(java.lang.String):java.lang.Object",
            new int[]{-1000,573,74,-340,-1000,513,1000,400,-71,-1000,-1000,-336,382,-19,-209,-817,-1000,434,248,-421,-36,-671,-128,846,889,-579,298,-308,0,-1000,-710,380,-899,883,388,1000,-446,-1000,576,1000,252,-147,292,-790,-549,-437,-1000,-1000,168,-1000,-615,-546,-1000,496,267,-1000,242,-99,-631,-1000,-492,-396,1000,-617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String):short",
            new int[]{470,903,-607,-1000,-469,-552,-847,587,-768,-1000,-929,1000,-508,198,-869,525,-1000,-649,-391,903,191,279,-465,-566,-320,624,-762,-1000,585,1000,-447,414,652,223,261,-225,-86,-842,-1000,482,-448,-136,-1000,119,-109,313,720,92,467,295,-648,-694,-1000,1000,-348,-630,544,971,-474,-224,-105,794,-350,-586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String):short",
            new int[]{-63,-114,-136,-648,942,-550,-822,1000,-1000,307,869,-369,-40,-723,1000,-1000,77,-613,1000,-219,890,489,1000,893,22,-468,1000,53,-826,401,-202,-7,-322,1000,-248,98,344,5,363,20,80,676,613,-145,253,-877,602,37,-529,496,419,-743,-466,-1000,-885,152,-1000,426,-924,-604,131,99,-517,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String):short",
            new int[]{836,-426,-153,269,-33,-112,205,-357,-395,704,1000,-920,1000,-857,-688,-606,1000,1000,-757,447,976,1000,-1000,88,297,-1000,1000,1000,-458,512,1000,-786,-1000,-661,-245,1000,435,682,-975,-274,1000,-1000,1000,-427,350,-1000,-272,-750,657,-266,45,-438,888,-1000,-4,1000,1000,-733,37,-1000,-1000,650,-140,-559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{-1000,576,234,-645,-1000,-186,-947,-64,-253,114,175,765,-1000,1000,1000,24,-854,1000,5,-1000,762,-1000,224,236,502,-685,627,584,517,-774,-189,400,1000,58,-663,588,922,-1000,722,-292,-154,-13,1000,963,69,-63,-930,537,-1000,511,1000,-770,504,-81,263,-920,-393,-473,263,232,-1000,-157,415,653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{-1000,609,927,-426,-443,-597,-925,337,-1000,448,-122,432,-479,-376,1000,798,179,-405,-488,-58,254,898,-39,-325,385,217,-914,-444,-956,757,141,1000,-1000,157,787,796,-778,-769,483,-808,361,1000,-231,-1000,224,235,-81,1000,308,-794,-435,787,-4,838,622,-126,409,1000,1000,-23,212,174,-139,427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{1000,84,-341,-158,-782,-191,319,-115,-105,-1000,29,266,-1000,316,1000,-1000,959,171,-1000,95,-1000,300,874,1000,23,697,-725,716,682,367,-664,523,1000,-1000,829,1000,1000,423,-888,-1000,-762,567,1000,-748,429,-1000,916,1000,-1000,1000,948,-1000,-825,399,-339,271,692,-380,-966,-141,1000,775,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{943,-336,-327,1000,1000,-562,-671,-115,233,335,-420,262,1000,-88,-1000,654,15,1000,749,-1000,1000,-1000,575,749,626,80,1000,709,-1000,495,-1000,372,-357,-8,472,490,25,-388,-888,104,1000,-1000,-1000,-812,-49,-1000,591,-372,960,487,-1000,960,-638,1000,248,1000,416,566,-966,665,-1000,-1000,354,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{1000,156,-771,-286,-715,-814,324,-191,-110,938,1000,115,412,-208,-158,-438,-432,959,-383,-703,-668,-709,957,-202,308,-39,165,-605,-417,-273,819,392,-510,-980,-213,375,672,1000,1000,911,-422,418,-1000,-1000,184,-79,272,922,-1000,1000,37,-877,72,978,-121,-1000,147,-406,604,-1000,-1000,1000,-57,-813}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{1000,84,-6,-149,-984,-631,-826,343,-700,-1000,622,926,-503,-562,972,636,163,-103,-1000,-703,-224,65,-852,114,363,697,-264,1000,322,272,637,1000,-159,248,859,-3,1000,49,154,-167,1000,632,213,-542,-649,-427,136,590,-1000,94,510,-1000,-825,285,-591,-606,859,-1000,-624,241,380,462,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{911,753,554,-7,1000,-731,907,-1000,1000,-482,1000,1000,1000,1000,-283,190,-706,302,-981,1000,-1000,951,-1000,-392,-953,-1000,1000,-805,41,638,661,-776,966,-1000,-88,73,-905,827,-11,-1000,683,204,-211,-668,-1000,274,-55,918,572,-355,78,684,977,-422,-591,-1000,463,-126,1000,1000,-39,-1000,1000,741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{253,-354,64,378,-469,-1000,1000,-1000,925,-876,1000,323,915,683,674,-956,-267,-31,-566,1000,53,1000,-494,-374,1000,-1000,860,-14,807,808,372,265,-61,-769,-1000,850,-731,623,-812,347,802,-364,108,-612,-1000,473,-1000,542,-607,-918,1000,1000,1000,1000,536,-987,1000,1000,247,1000,1000,-757,967,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{180,714,408,1000,-544,-295,125,-400,-419,-15,-328,-227,-298,-208,912,568,-382,244,-14,-1000,-270,716,-479,-930,-209,-233,-1000,709,-703,569,1000,1000,-398,-323,1000,954,-950,1000,362,-474,-1000,-528,-86,433,-703,-140,-348,1000,-951,531,-1000,179,637,93,740,-11,-274,-132,645,-1000,1000,-666,-688,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{-305,-267,504,381,296,-199,-529,663,-580,-857,112,1000,390,451,895,-25,-433,223,-767,-727,-296,-819,-1000,-954,-198,-1000,638,-119,309,175,-678,526,713,718,-400,-379,-115,343,300,-505,-746,-546,378,-84,400,-447,39,202,-200,-164,-261,888,-326,59,-35,-880,-687,-19,-832,-136,449,-709,-95,135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{1000,339,984,-394,639,1000,-1000,765,-1000,1000,-606,-1000,159,-667,818,-616,82,1000,1000,1000,1000,-433,-273,-1000,-1000,1000,400,1000,-29,-937,-633,-1000,-773,-687,1000,766,-286,1000,19,365,844,1000,1000,325,-734,-557,-2,1000,-158,259,1000,-500,1000,-228,-1000,263,684,9,1000,-558,-1000,-145,-71,-696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{1000,123,523,1000,98,85,-1000,1000,-486,-123,1000,46,864,-28,-158,732,-698,-61,-6,-1000,434,-1000,1000,-1000,-1000,594,1000,442,-198,414,-1000,564,-635,1000,-490,888,-240,398,1000,-1000,393,-1000,1000,-271,1000,1000,-457,640,-812,-1000,1000,885,1000,-624,750,-108,-892,492,-376,-1000,-247,-415,334,-584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:0", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getStringArray(java.lang.String):java.lang.String[]",
            new int[]{-577,-867,-472,718,659,556,-919,590,-259,-613,437,-718,66,-605,-166,173,-99,713,-549,-257,-696,-418,-440,-267,440,641,311,315,-698,-405,-919,-680,-5,-77,355,466,233,-149,-486,-665,424,-728,-465,-624,857,492,656,-355,-131,-265,-673,-865,612,194,398,-387,-972,74,-556,-337,-979,568,339,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:0", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getStringArray(java.lang.String):java.lang.String[]",
            new int[]{-656,-135,-621,-649,-1000,84,-1000,-508,1000,-512,-294,-980,1000,142,1000,-1000,171,29,-321,-1000,-442,-40,-10,-1000,1000,-824,-549,-809,577,381,210,-566,678,-493,-767,1000,882,935,-515,-542,-1000,-195,348,-533,606,-183,-49,-604,340,1000,-1000,-1000,-80,728,1000,46,-887,991,241,-400,-823,-607,-773,183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:0", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getStringArray(java.lang.String):java.lang.String[]",
            new int[]{-1000,489,-362,1000,1000,1000,-592,1000,-438,-1000,375,-1000,-138,-569,11,1000,-400,1000,-1000,-951,-1000,-159,-1000,-15,-1000,-467,-700,-344,-821,700,-1000,-1000,749,1000,600,763,221,-1000,434,-294,-220,-249,341,-1000,443,-1000,-1000,-1000,-1000,1000,-1000,-1000,1000,1000,1000,-1000,-1000,979,1000,-1000,-1000,1000,277,920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{-1000,-543,-877,-586,-734,-1000,419,-156,-1000,-1000,-1000,850,154,-1000,-707,1000,-1000,803,1000,-84,-54,634,-846,-1000,1000,1000,90,1000,-613,1000,-701,-276,-1000,1000,-1000,-1000,-303,505,-141,118,-1000,-262,-1000,1000,1000,601,1000,-694,1000,-1000,849,-506,-454,1000,-1000,-481,372,894,175,1000,1000,-1000,-208,-520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{570,-246,939,-378,-527,-215,647,1000,1000,-229,513,13,-1000,653,-468,-464,-59,576,940,192,1000,1000,-601,-849,222,1000,-54,-400,33,300,444,-494,-402,-103,292,615,402,-308,-173,890,51,1000,-147,23,-300,1000,-456,-481,-463,-1000,-583,384,-1000,442,-1000,-59,-1000,-1000,-302,-1000,440,745,650,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{-293,558,-286,803,-804,-661,-959,293,1000,-286,394,-578,-652,409,-633,-1000,427,-192,-303,319,240,1000,-449,-509,-21,739,-850,-955,-682,-566,-601,311,277,-776,-288,-427,655,-370,-172,487,-489,712,32,-100,529,995,-40,908,1000,-1000,-1000,308,-191,-298,-1000,-81,379,-842,-860,-286,-55,-53,453,45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{-205,-213,504,644,921,-96,-905,156,-297,827,589,982,-367,702,-451,899,431,341,-771,883,-382,544,841,535,700,-897,-292,-248,-161,-167,21,-307,-412,-503,-138,-975,-159,-717,-192,620,476,-386,-17,-580,-653,512,-296,8,-739,457,-969,593,358,775,476,557,-73,-80,-130,-497,100,729,-34,833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{-1000,-237,-76,826,424,308,-20,905,-88,-187,-781,418,128,-681,171,337,1000,154,-552,329,-403,-376,1000,-810,508,-191,1000,6,-488,-617,-242,-543,-902,-887,467,1000,33,826,260,133,-460,640,530,-826,82,77,1000,-978,-818,1000,930,748,-986,-804,971,-1000,87,643,582,578,94,-921,755,819}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "isInitialized():boolean",
            new int[]{81,108,99,476,-1000,815,-105,0,-137,373,71,25,-221,-148,261,-125,49,862,-202,388,-677,357,-610,1000,-1000,796,90,883,-734,278,310,137,393,-143,-647,1000,1000,97,-1000,201,772,49,-11,-214,-230,28,564,-1000,-252,360,110,680,501,-475,168,-26,-978,340,259,720,-198,-318,984,66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "isInitialized():boolean",
            new int[]{16,-876,-576,-786,-195,-294,383,229,71,-608,300,-104,300,258,195,-351,-126,29,190,-62,-848,93,484,558,418,-10,-300,-574,221,51,138,-17,849,335,-352,85,-563,133,250,-645,-541,-1000,-431,-10,32,231,1000,-221,781,52,-29,85,484,275,462,-158,-260,-69,-127,-326,451,-162,1000,164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream):void",
            new int[]{508,3,173,1000,-1000,-477,790,39,-1000,181,-1000,761,157,419,-287,-1000,-1000,-246,1000,-9,214,505,-977,1000,-835,-222,-191,546,-637,1000,-392,914,673,1000,-369,851,-741,103,1000,667,1000,-368,594,32,333,455,618,-1000,481,1000,-920,-2,-33,946,-453,342,850,-45,1000,189,-176,995,1000,-239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{-26,672,854,943,1000,-347,981,91,-75,-1000,307,-1000,-391,210,1000,1000,1000,467,302,-1000,-1000,998,-316,-477,-696,1000,-1000,-755,625,1000,-57,910,786,1000,69,155,986,-197,587,-267,-227,572,-112,1000,-16,492,1000,447,503,-110,-1000,244,1000,-889,-492,-1000,-1000,605,1000,-1000,-54,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{747,-270,-632,-22,619,-460,654,-557,-175,35,-850,663,1000,-1000,15,326,-132,432,-447,773,1000,906,1000,1000,894,-1,-485,47,-785,-685,-858,-1000,-531,1000,-536,-314,632,-503,-118,322,649,384,22,-548,221,-1000,-44,-898,-274,864,-178,-507,20,-125,71,1000,445,-550,-376,-972,478,-316,-793,-303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{-98,-45,-161,1000,-80,1000,-114,-616,25,-478,1000,-281,1000,-291,306,66,1000,1000,-928,324,-927,-285,627,1000,594,-128,-914,-587,861,896,-437,480,-205,-241,47,-1000,1000,-624,408,968,139,764,-5,636,-230,-624,1000,-305,-618,711,4,225,387,-93,-112,-513,11,33,1000,-481,722,1000,-347,-60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{299,69,-21,281,-99,-421,-470,1000,-1000,398,-284,991,1000,-276,-443,1000,1000,716,-442,1000,-662,1000,167,82,-711,-566,-1000,189,-1000,-864,1000,626,-968,1000,1000,867,477,-246,-3,-147,626,-796,-629,-1000,31,1000,-302,990,-743,-414,1000,-1000,941,-274,418,-996,-178,1000,-852,-10,18,-181,-193,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-257,489,-196,14,-957,328,985,-387,-71,972,-692,1000,77,-362,-1000,1000,-848,1000,-1000,-858,1000,-303,591,160,886,779,-709,802,-535,335,1000,1000,600,-219,-1000,-158,1000,451,-1000,-675,318,-931,850,377,1000,-526,-57,151,497,664,1000,-821,516,-574,-1000,-601,1000,-870,-460,196,368,501,1000,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "putAll(java.util.Map):void",
            new int[]{85,270,-106,449,527,184,816,39,-529,416,-335,-659,798,-776,15,259,-374,-32,-415,955,-821,-662,643,-655,902,-332,-614,-636,112,-770,128,-45,922,116,-377,849,383,299,290,-429,839,758,283,863,-646,199,-655,695,-296,807,242,412,-58,723,176,184,815,506,-963,910,-584,-324,-387,1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "putAll(java.util.Map):void",
            new int[]{-1000,273,-347,475,-931,974,-974,-807,-1000,485,-842,-216,-644,650,-623,-1000,1000,463,277,-409,796,-1000,-452,308,-658,927,938,-795,1000,-96,765,348,-371,1000,1000,63,-1000,1000,781,467,-302,-85,-1000,-698,-333,-115,-421,-745,1000,271,-1000,1000,-1000,-629,560,-648,400,-1000,505,1000,-1000,-166,-577,-379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "putAll(java.util.Map):void",
            new int[]{-84,-699,-63,558,1000,343,1000,-532,-900,1000,-544,-1000,1000,-1000,-281,-698,-142,-285,-498,1000,-1000,-124,1000,-992,1000,-1000,-542,-237,-362,-1000,248,-387,1000,-23,511,1000,-1000,825,1000,-347,1000,1000,765,1000,-1000,-355,-1000,1000,-296,1000,956,189,843,1000,672,1000,1000,1000,-1000,1000,-771,-1000,-1000,1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "remove(java.lang.Object):java.lang.Object",
            new int[]{-232,-405,-776,1000,595,-710,-1000,903,-453,-1000,-93,34,-1000,-506,686,-397,-13,-180,881,-481,-1000,161,322,759,1000,62,394,-409,225,1000,-478,555,1000,160,-592,1000,159,299,-573,-512,1000,1000,-1000,-666,-942,-658,-1000,1000,-708,1000,1000,1000,-629,-176,500,995,-47,457,-16,-189,381,623,361,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "remove(java.lang.Object):java.lang.Object",
            new int[]{345,168,19,580,-583,-226,-666,346,-410,-35,-584,-1000,53,-623,-245,691,-138,-294,591,-441,-383,580,1000,896,85,725,651,-726,494,-439,-230,-116,106,-446,-53,255,219,1000,-439,-129,-598,-122,87,-339,-254,345,-51,-994,-487,175,18,580,244,-249,308,-507,404,-76,-247,13,-790,511,335,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "save(java.io.OutputStream,java.lang.String):void",
            new int[]{-968,-606,-646,-124,763,626,389,-517,-434,476,-1000,1000,-1000,1000,870,1000,-1000,-909,-67,-1000,493,-1000,-313,145,386,-160,-494,-1000,1000,719,-875,1000,938,1000,585,-1000,352,-614,1000,-1000,1000,1000,-1000,1000,1000,-1000,-1000,-1000,-494,-668,-1000,-694,-1000,-686,1000,316,274,-1000,1000,-1000,-1000,-1000,832,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "save(java.io.OutputStream,java.lang.String):void",
            new int[]{273,369,607,0,-446,-127,880,-329,-199,478,0,-938,-4,385,-848,867,854,0,-234,-137,-207,0,-34,663,-202,353,0,492,-129,-235,107,169,375,-55,522,-404,1000,-837,-905,552,198,277,4,113,-523,-213,347,-657,273,422,0,593,0,267,221,432,136,0,-515,-170,-403,174,-985,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID|getInclude=NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setInclude(java.lang.String):void",
            new int[]{-1000,-225,828,-1000,-55,-504,299,1000,-86,-1000,-1000,1000,-1000,833,371,964,-60,1000,-158,272,11,1000,-1000,979,1000,456,1000,-1000,-1000,-191,281,1000,-832,-591,-988,606,-559,-1000,720,-1000,-329,-1000,479,405,-801,1000,55,-321,888,1000,-633,-682,-107,-272,-461,-111,-1000,1000,-240,453,695,-539,390,667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("VOID|getInclude=java.lang.String:ICsxMDAwIA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setInclude(java.lang.String):void",
            new int[]{178,735,69,-121,-785,145,-59,1000,1000,-950,986,-79,-251,1000,-828,-1000,1000,-270,80,60,-1000,-637,1000,-518,-1000,-234,-1000,1000,181,1000,291,-161,1000,364,839,42,1000,1000,-1000,1000,852,199,852,-807,229,-1000,-939,1000,-782,-645,-1000,623,1000,1000,-507,1000,-8,464,619,-1000,-498,893,-965,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setProperty(java.lang.String,java.lang.Object):void",
            new int[]{-113,939,-181,808,-944,-378,-1000,-127,699,-1000,-335,878,110,1000,129,1000,446,2,122,-550,1000,501,-1000,26,555,-1000,1000,-178,177,517,145,187,-400,181,-747,81,1000,-873,-1,-1000,-1000,-682,408,982,-141,-1000,1000,689,-1000,334,-1000,736,-332,392,894,-834,-347,-350,1000,132,-864,1000,897,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setProperty(java.lang.String,java.lang.Object):void",
            new int[]{-109,-432,-646,-1000,616,-57,326,-197,137,1000,797,-844,151,-351,-1000,278,-65,55,-20,-230,-1000,257,439,721,1000,1000,487,-658,-658,1000,117,-1000,-552,-843,942,-35,-145,1000,920,148,-1000,-331,-1000,-648,1000,-442,907,-855,1000,2,-805,-153,-606,679,146,1000,568,268,309,-43,885,527,-792,-723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.ExtendedProperties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{7,-240,-307,533,-20,-1000,-1000,-357,-512,-59,-1000,-571,-1000,1000,-970,-633,-748,-75,-703,1000,726,520,-84,408,-875,490,-1000,285,-384,-1000,-402,-908,-562,-1000,173,471,115,267,1000,-1000,381,1000,246,637,522,-1000,951,1000,1000,-43,-1000,-1000,19,304,845,707,1000,-275,601,438,289,-397,503,-267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{-207,96,-341,184,176,-192,-525,805,144,34,-565,-1000,-539,975,-1000,732,-35,100,-978,657,-655,-557,-1000,135,1000,322,-962,429,85,-1000,98,-165,-377,-115,-1000,-618,1000,980,911,400,-853,141,357,440,-199,1000,1000,167,284,-358,-950,99,-31,276,243,1000,293,-1000,420,770,-709,517,468,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{626,-21,-27,-245,653,230,-113,-26,-155,-779,-188,-385,869,-857,-285,-257,-771,801,73,27,20,-380,-706,277,77,-313,-346,793,112,-167,374,1,-595,-88,67,509,-448,408,-351,-310,139,711,-338,128,170,-742,27,-81,-498,610,701,-554,257,-114,-130,-14,-400,-192,710,-41,150,-201,96,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{980,339,-756,749,-823,767,530,718,-250,-508,-80,328,425,-495,533,-999,230,262,-775,-774,470,-169,992,693,-268,-751,-277,-770,-249,-807,58,-603,-958,479,-72,-975,-665,-141,-701,-978,81,-849,-380,307,527,791,525,-137,-776,595,-546,-408,-794,708,26,922,-474,519,242,897,383,662,-124,-109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{191,-324,-836,-24,892,235,479,1000,-593,537,146,-655,363,-1000,-874,212,821,1000,-1000,-1000,293,-686,1000,843,1000,-84,691,-1000,1000,-1000,-967,-7,-703,402,-1000,1000,-1000,-641,112,699,1000,-621,268,-1000,1000,141,1000,-1000,-193,1000,-1000,1000,-983,308,-994,953,-898,-711,573,-932,1000,871,-1000,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{-896,-201,-756,749,681,-194,94,-147,-1000,984,-127,-878,425,-1000,-388,-999,963,133,-1000,-774,-935,155,71,-1000,1000,-595,979,-1000,1000,-1000,-506,-1000,-1000,585,-1000,858,1000,1000,179,1000,1000,462,-75,-848,373,1000,525,-1000,-931,-85,-288,737,-399,-468,365,1000,-473,-847,-180,300,768,406,-1000,-848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{1000,-570,-406,1000,-352,36,610,-514,-140,-499,-356,10,-985,-1000,750,-729,613,-877,-679,-59,-395,-255,-342,-567,-1000,-857,-1000,-636,-605,314,-257,-1000,-1000,-383,87,789,1000,1000,98,-682,-1000,66,-364,-640,-207,43,-550,-1000,-197,357,464,488,535,-352,823,741,270,340,-580,1000,22,-603,185,-149}));
    }
}
