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
            new int[]{441,240,-496,-76,-166,-1000,1000,1000,1000,-1000,965,1000,-883,559,416,1000,-199,-1000,-1000,693,74,-440,-419,-562,-14,0,1000,-578,97,-1000,890,-1000,15,385,865,-1000,255,-548,814,434,-246,-526,-415,-59,1000,-748,-319,-400,-1000,-383,1000,794,797,-978,979,715,675,-733,-845,-486,797,-140,-822,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "addProperty(java.lang.String,java.lang.Object):void",
            new int[]{-306,-879,569,-1000,-729,403,-1000,-307,-1000,-926,80,-426,1000,-1000,-273,-1000,1000,-616,1000,-981,1000,844,290,898,875,-1000,-1000,1000,-328,-527,-525,1000,181,369,4,482,-123,1000,-1000,137,-810,810,-1000,1000,-419,-731,1000,628,1000,-853,-651,-636,-1000,-926,-1000,-541,799,294,875,62,994,-1000,888,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{-793,354,84,837,-922,-1000,157,-666,1000,443,290,347,530,-54,11,284,-56,-10,1000,767,-1000,746,-891,-165,-680,-140,-485,-805,489,-530,1000,-1000,348,-1000,55,-940,488,602,27,-41,-937,221,-373,-286,-31,-1000,737,-589,-505,-715,586,-858,-1000,-1000,-910,-772,-843,-351,288,-439,1000,950,-1000,-93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{1000,165,-707,62,-970,-354,1000,1000,-1000,1000,1000,-565,548,-748,-1000,-322,937,-120,-948,-448,925,-873,555,-1000,76,-1000,138,962,215,1000,-1000,1000,-1000,1000,599,50,1000,956,-1000,323,424,1000,-289,158,-448,393,-1000,-596,448,1000,259,744,999,-89,1000,-1000,-513,-1000,-566,1000,435,-715,315,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{96,108,-731,-1000,-1000,-663,468,217,1000,1000,917,65,-717,86,-392,-1000,1000,160,-251,1000,-458,927,-999,-1000,-1000,-536,433,840,943,-155,984,-354,979,-802,176,-246,623,980,346,125,-1000,1000,252,-49,1000,-596,684,-601,-346,-1000,700,-911,-729,-1000,-1000,-950,126,-94,-610,-376,817,-668,370,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{-594,591,-607,-1000,-1000,489,-854,-1000,-963,-677,456,-391,-197,645,-172,1000,-232,-295,871,420,-1000,1000,443,751,-800,50,-247,913,292,-513,163,-443,258,1000,-205,-144,641,305,-243,-925,83,1000,-217,-577,957,1000,97,48,-82,737,-916,701,840,1000,952,276,230,251,1000,4,964,-1000,-812,435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{74,153,599,-1000,-1000,474,1000,55,-1000,739,1000,-926,366,1000,1000,576,1000,-252,-750,1000,-1000,1000,618,-1000,565,1000,-1000,808,1000,-1000,-1000,789,-302,627,-965,-1000,-357,-1000,-669,1000,1000,-528,324,138,254,866,-35,-1000,-1000,-490,1000,-1000,-954,-1000,-1000,1000,1000,1000,-579,1000,759,946,1000,-480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.ExtendedProperties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "convertProperties(java.util.Properties):org.apache.commons.collections.ExtendedProperties",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "display():void",
            new int[]{-640,903,-806,623,-294,1000,485,533,91,-548,-832,270,-921,154,423,-56,-341,-700,837,-280,1000,182,814,101,315,1000,-819,-699,-785,-11,314,1000,492,418,-581,87,1000,372,934,-797,782,-180,-228,667,-377,-1000,-279,111,-489,644,-476,675,-965,-666,259,-797,766,211,596,-515,430,380,-116,981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String):boolean",
            new int[]{-602,-936,-111,-280,745,322,1000,-439,-956,1000,269,119,-537,1000,-50,240,-103,347,474,-571,-1000,1000,751,-764,-949,-303,584,-660,1000,498,695,257,987,552,-746,147,1000,974,-432,237,640,821,-14,-85,-849,346,-804,-352,-553,-1000,-316,-84,272,-910,395,-16,684,593,-951,1000,31,-862,744,170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String):boolean",
            new int[]{-821,-903,418,1000,240,586,175,-967,339,-151,1000,-311,815,-288,-629,-486,-201,185,-1000,-264,564,-800,-926,-184,1000,1000,727,-715,166,568,-491,655,146,-1000,435,-983,-232,1000,-126,-694,434,-171,-898,728,-605,-766,683,373,1000,-1000,53,568,954,-683,1000,1000,303,380,257,-113,464,-218,651,389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{302,261,718,-382,317,-825,-131,606,-47,-52,-535,332,-1000,-947,-1000,440,1000,598,-824,-868,-1000,546,878,-193,1000,-935,-639,-1000,-410,-1000,46,-357,-607,-642,-173,847,271,391,181,146,-1000,1000,649,867,-801,-492,1000,-1000,-904,299,272,770,-485,484,242,-849,-147,410,635,68,-435,-840,-36,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{821,99,114,-348,1000,639,1000,253,400,-916,550,1000,-500,1000,424,341,587,1000,199,912,-694,-919,98,481,-34,131,-1000,-588,-833,-930,-1000,851,632,-172,-909,-1000,208,-1000,-1000,1000,-88,-1000,120,228,1000,462,-808,-133,-798,-197,59,1000,67,-1000,1000,-517,-274,923,-1000,1000,-1000,1000,824,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{509,552,-741,908,1000,79,1000,1000,1000,1000,1000,1000,-1000,1000,-1000,-1000,1000,128,194,1000,-1000,-1000,18,-481,-300,-373,-1000,-1000,-150,-197,-1000,1000,1000,934,955,-918,-308,597,-920,570,-326,-345,-868,-1000,1000,-1000,-748,1000,917,-329,142,1000,1000,-137,1000,-1000,-372,-733,-539,1000,-1000,9,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{235,81,829,10,-831,916,-13,875,-109,-1000,1000,460,102,-1000,1000,110,702,426,196,1000,124,-329,-953,851,-1000,-97,-883,811,408,-346,669,245,201,-607,286,-1000,-600,300,-1000,762,-89,566,1000,-740,-474,-494,-305,-922,-827,-255,-1000,1000,1000,-192,775,-1000,-330,-1000,-1000,-533,1000,1000,-867,935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{-1000,-867,-763,180,-95,-680,-651,-692,-215,400,-938,-1000,-1000,1000,-612,-1000,-1000,-1000,-1000,-1000,1000,-951,1000,109,673,-240,-314,243,1000,-1000,-1000,-1000,376,125,26,725,-593,441,1000,-1000,366,-1000,-413,1000,236,-1000,1000,18,-344,800,1000,-1000,-1000,216,178,630,912,60,1000,-326,-589,-905,8,-605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{-156,930,-721,431,-66,-1000,-908,-125,-895,1000,-498,-820,-108,248,-1000,398,-39,1000,-593,-1000,-1000,-502,874,-967,-1000,-100,-580,-860,-448,-717,-630,-238,311,1000,-417,724,-1000,-865,544,-529,-798,1000,687,1000,-1000,1000,414,-1000,619,363,1000,-853,-802,1000,960,-764,-358,554,386,-191,-1000,-101,-1000,823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{62,-348,-342,431,-548,-792,-716,-231,-930,1000,-704,14,1000,1000,-1000,701,-1000,1000,-1000,-546,203,-791,285,704,-809,288,-877,377,668,-820,-241,-111,436,1000,213,414,-400,-96,912,-751,618,615,963,427,-515,-338,-403,-658,381,753,759,-32,-516,1000,1000,-941,1000,-842,1000,-901,-487,-264,-554,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{-1000,144,-56,-40,382,-129,155,-62,209,972,1000,-247,-1000,-929,-1000,1000,288,-1000,-315,145,1000,-818,544,227,694,-595,175,-1000,-1000,1000,-651,-1000,447,-1000,850,1000,-275,797,400,-426,1000,-705,-1000,476,-731,-1000,77,1000,-130,499,927,-250,363,-951,-429,-512,664,1000,124,-953,400,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{662,-633,543,-595,-1000,117,174,1000,-295,-1000,-1000,-1000,1000,1000,-419,-185,54,655,419,1000,446,1000,-929,-1000,-1000,-899,1000,-1000,-822,-1000,-1000,-799,1000,1000,-340,1000,-705,1000,-562,-491,810,0,-1000,-1000,1000,-649,1000,1000,-1000,243,-1000,-347,-619,1000,-50,298,648,-255,-1000,-306,-1000,-774,944,-68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{1000,-696,649,-1000,-1000,168,468,-130,80,-1000,-950,219,98,1000,-644,-72,95,848,-34,400,-539,1000,351,-684,-1000,1000,886,1000,621,-1000,-46,1000,-348,-317,-1000,249,1000,978,-729,183,-542,904,-926,-1000,1000,-224,-241,22,-1000,-869,-1000,-904,-903,785,-517,921,174,-1000,-1000,1000,-1000,-220,-28,-612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{41,-597,-601,1000,-48,1000,-641,608,-649,-995,5,-533,-691,1000,913,-824,1000,-1000,338,-305,-1000,-627,850,581,-432,692,-848,618,677,-474,-270,-771,-458,514,156,-843,1000,-753,-1000,-1000,17,-531,-895,835,1000,362,396,203,-19,316,-162,-1000,952,-631,-1000,-948,-284,-218,-1000,-1000,-898,-406,1000,-147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{196,-867,-591,50,402,324,-3,-968,476,-971,-688,566,-974,-797,-377,-483,224,-425,621,-374,660,606,914,-418,-408,86,968,-866,-95,512,-158,-481,467,-312,373,-936,478,-677,-533,-547,863,869,-388,877,802,-718,813,-972,-902,-22,774,-260,215,-163,-665,-22,-437,-196,936,815,-120,346,177,-927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{1000,114,-733,-231,-453,951,749,657,775,-993,-1000,-562,-575,-692,334,-259,863,-1000,736,-148,1000,-569,391,-1000,-982,341,-848,-454,677,-639,-961,-822,89,-1000,771,-752,1000,-229,86,12,-666,1000,-895,-295,-40,-353,-332,-1000,-6,-226,-412,-863,-414,-415,-453,-854,422,-400,1000,301,-494,-558,-1000,626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{136,342,59,821,-292,-534,-1000,-1000,1000,-1000,-744,-18,122,1000,-1000,1000,-378,721,-1000,1000,1000,597,1000,-466,-1000,-1000,221,-1000,3,-486,141,569,-1000,-409,312,-1000,1000,136,-1000,-1000,-1000,42,-1000,598,-1000,-248,-353,-266,520,-1000,1000,-1000,-188,-332,-290,678,1000,361,-594,719,343,1000,-273,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{652,480,-167,1000,834,-79,-397,-119,-313,1000,-1000,1000,-1000,-896,560,-1000,-486,-1000,1000,-385,-1000,-250,1000,-397,-253,702,-205,807,535,411,-570,-1000,-644,-204,597,-931,822,775,1000,1000,552,-58,419,1000,-636,-920,1000,-885,-69,1000,-779,800,376,-398,124,-1000,-479,-1000,-568,1000,-194,-1000,-1000,802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{451,3,-902,888,351,-355,-1000,-580,-67,-1000,-1000,-371,160,-773,-1000,445,424,-155,-1000,309,47,980,513,668,15,159,642,-980,678,-678,132,-400,-1000,-119,542,166,688,492,-1000,-143,-261,-481,-1000,1000,103,-843,182,-1000,157,577,720,691,400,239,1000,-55,-85,-253,-946,1000,690,299,319,315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String):double",
            new int[]{-1000,-540,-106,-959,809,1000,823,-455,1000,-991,1000,-268,-955,-403,1000,-1000,524,94,480,-110,37,-745,730,1000,644,-1000,-476,130,-1000,225,353,144,1000,315,365,-1000,233,-1000,128,-585,-1000,691,1000,-16,174,-1000,71,-267,1000,-985,-69,-1000,-376,293,-283,1000,1000,-90,938,1000,-1000,-264,-612,605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String):double",
            new int[]{284,90,114,127,-270,-453,583,-206,-1000,-63,971,114,1000,221,526,427,246,-462,731,-900,291,-234,769,1000,400,-460,-274,-469,-565,293,1000,-1000,-344,514,-193,1000,-201,-898,-117,-338,-569,860,847,-39,909,-1000,1000,650,-94,-387,104,-1000,1000,647,-826,960,964,-400,70,1000,-899,-576,394,887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String):double",
            new int[]{-881,132,-461,-1000,677,922,315,-48,-285,-331,865,910,-208,-1000,939,-92,96,-245,866,881,513,-11,-678,826,-318,-509,658,555,-63,1000,-281,-34,1000,1000,87,-694,671,-730,-1000,-688,-1000,1000,1000,662,889,-215,629,34,1000,-952,542,-711,-636,445,-650,919,5,705,1000,902,-339,-369,577,558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{-415,-354,564,659,255,-677,1000,1000,432,-1000,954,-974,-1000,1000,626,1000,-654,1000,-357,-505,-1000,-634,1000,1,-1000,1000,-1000,-875,499,826,559,-1000,-1000,-1000,-576,-528,665,-219,257,366,-964,-84,497,189,1000,-1000,-1000,-1000,983,1000,-1000,-248,-749,610,276,-738,805,15,794,1000,103,-1000,-570,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Double:LTE4MC4w", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{-677,522,49,-129,1000,-828,1000,-349,905,357,-924,764,-752,388,691,183,-452,-272,993,-1000,-346,-925,552,273,-614,-637,-180,-373,828,1000,421,-374,839,191,1000,-589,241,14,1000,-1000,-859,-1000,1000,1000,-800,486,337,222,1000,-484,-291,-686,59,-847,1000,982,780,64,-1000,539,-294,81,-1000,75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Double:LTY0NS4w", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{-821,-651,684,1000,-400,-824,441,-474,1000,-694,286,-636,129,-131,522,51,-1000,943,1000,-1000,473,-1000,-268,1000,-645,-637,-274,-857,415,1000,1000,-538,-561,72,-387,266,-1000,725,1000,-525,-625,-514,1000,1000,-1000,261,318,-1000,1000,-266,-1000,418,366,-1000,1000,523,1000,-272,-822,-130,341,-428,-417,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{-1000,111,-57,1000,-1000,978,-1000,-622,-1000,1000,-1000,-919,-1000,133,-1000,-824,-1000,1000,-1000,-956,-1000,870,-717,-59,-1000,-112,536,1000,1000,464,-626,1000,1000,824,44,-488,-554,906,-950,-255,-868,1000,673,-1000,-130,-856,1000,-1000,-889,211,481,-8,-219,734,-499,140,879,1000,555,-308,191,951,881,301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{1000,708,969,-751,729,195,915,-505,-37,-1000,1000,713,-497,-915,-20,-292,1000,-689,1000,272,861,877,-48,-1000,979,439,-1000,-1000,-220,-1000,617,-1000,-1000,-242,427,1000,1000,-935,-242,1000,551,-1000,70,179,-1000,533,-1000,1000,-3,563,995,234,1000,108,1000,-1000,-634,-40,-1000,118,-941,-718,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{-136,492,614,1000,-204,183,90,509,-1000,1000,-107,-390,1000,53,937,-1000,-1000,1000,484,398,1000,1000,246,-1000,1000,577,766,1000,-119,-792,537,-243,1000,1000,-608,1000,-456,1000,-1000,1000,-162,1000,-27,-774,306,1000,-905,-1000,-977,-288,-830,-926,-363,50,-1000,108,-322,-1000,290,1000,-269,162,1000,686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{94,-306,274,-1000,1000,1000,-445,-1000,951,-1000,-187,-558,532,464,-89,-1000,342,1000,-249,1000,-1000,158,327,-1000,-475,1000,-1000,465,-1000,-270,-1000,-285,-1000,-340,977,322,-736,11,1000,246,-1000,-350,331,-1000,-271,-543,-271,1000,-344,871,-877,69,-1000,1000,-938,-936,1000,68,-152,-1000,1000,272,153,-11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4xNDc0ODM2NUU5", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{-968,-435,609,100,-400,442,-293,-877,1000,218,354,229,400,26,-144,308,42,1000,811,-117,-376,-320,57,-419,262,928,773,325,1000,-438,909,365,689,-87,176,1000,-894,571,-117,281,-510,-400,1000,-20,11,20,290,-258,60,-104,958,-105,253,34,881,345,-143,-1000,310,-296,-277,-62,359,939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{1000,756,249,78,258,-304,-970,485,-1000,-110,908,875,-274,-283,-765,76,-1000,-290,174,-615,711,647,748,-670,799,-906,-80,-621,-456,184,-145,-701,-736,-599,734,23,-178,1000,-1000,-733,474,-32,-1000,817,913,-648,426,-1000,-899,-1000,-803,-141,-49,-316,57,1000,1000,310,-1000,513,-649,258,465,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Float:LTkuMjIzMzcyRTE4", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{329,-945,-757,-172,-874,591,-655,1000,-66,1000,-222,-1000,-413,1000,-717,-1000,1000,-762,-307,1000,919,246,126,-902,1000,300,8,-26,-989,1000,-1000,-1000,652,-471,-1000,536,-63,-154,-1000,428,-138,298,801,-731,1000,-475,-85,-812,429,-1000,222,864,-559,814,1000,-529,627,334,813,584,524,1000,-326,244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Float:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{-582,387,-911,-511,929,686,-655,443,-90,90,444,432,1000,-400,-77,190,780,1000,1000,513,360,-578,-616,-12,434,-163,191,420,616,-400,-141,-1000,692,453,-218,1000,-430,275,-937,-360,-58,-268,348,-382,61,634,-262,-883,-611,526,-543,-376,-559,291,-149,22,-532,1000,-702,-462,-36,184,1000,-467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Float:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{-780,-672,-312,-253,110,1000,-1000,-257,-346,496,-780,-243,-410,149,1000,-302,820,-1000,-1000,279,1000,877,-917,323,400,1000,-50,1000,-392,400,439,-1000,315,505,-872,589,-1000,1000,-801,296,142,-138,-303,-1000,400,128,-1000,-269,-37,-1000,591,605,-1000,270,405,-1000,139,21,1000,-679,-123,338,-1000,905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{1000,-960,552,119,-1000,848,-1000,1000,1000,1000,-1000,-1000,-1000,1000,-721,-1000,1000,-1000,-1000,1000,1000,1000,-1000,-1000,1000,764,-188,35,698,1000,-1000,17,-175,76,-1000,-1000,-55,440,-1000,75,765,1000,1000,317,1000,-1000,22,-720,1000,-1000,-164,1000,-1000,1000,1000,60,-1000,-630,1000,1000,1000,1000,-692,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{46,-969,324,-1000,1000,6,-841,1000,-308,1000,-775,-155,936,-1000,-837,1000,140,831,-229,-1000,271,-176,873,1000,-1000,887,-37,-1000,-1000,660,777,1000,868,479,-1000,1000,1000,-1000,1000,-982,-600,719,223,-1000,386,-52,162,-876,-861,-808,-1000,-1000,860,-1000,556,858,170,-383,1000,448,949,1000,-184,918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.String:aW5jbHVkZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{-472,549,59,-857,-589,653,217,-720,1000,127,-133,-807,-671,-906,4,-1000,-834,273,1000,180,-1000,549,628,-1000,783,-1000,1000,903,527,-1000,-292,-13,175,-1000,944,-350,571,282,554,-407,15,990,1000,1000,451,330,-1000,-929,1000,1000,1000,-245,-1000,-426,-714,1000,-605,-172,281,339,-283,529,-68,5}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDAw", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{-1000,-219,369,-922,1000,-415,-1000,1000,-1000,1000,-1000,-724,1000,-368,-313,348,-180,347,-64,830,1000,227,1000,387,1000,1000,745,-1000,-1000,359,937,1000,1000,-105,-1000,1000,-198,-1000,-1000,1000,-1000,-355,-280,-1000,1000,-1000,-896,-873,-991,-110,-493,-1000,-264,-1000,985,725,500,8,1000,-1000,653,-616,1000,43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{1000,-570,73,274,-928,-420,388,-695,-289,-515,-1000,-778,-1000,-1000,874,-608,-167,526,637,-472,-1000,-250,547,155,-617,476,-28,-674,1000,-388,703,805,-75,-110,62,-745,-226,466,280,-1000,126,953,14,802,-1000,1000,427,-576,-62,259,-110,-1000,-1000,1000,400,451,1000,275,-204,100,372,963,-1000,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{-638,888,584,-270,-1000,-862,1000,-243,-386,173,-613,-93,351,1000,328,454,10,-722,630,955,639,991,-373,-1000,-875,-1000,68,384,114,-706,-445,1000,1000,201,-888,-472,-688,-168,479,468,995,932,625,738,28,-971,-1000,168,473,833,485,703,-148,529,430,-1000,-540,-383,-466,248,252,1000,232,-617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{-422,-147,159,755,-1000,684,103,571,419,917,1000,-336,-671,-370,1000,-481,626,1000,1000,270,644,-702,1000,-1000,-639,-866,845,1000,-1000,-1000,-357,896,1000,960,-1000,-1000,1000,-603,-572,-1000,-226,113,-1000,90,278,654,84,1000,-627,1000,1000,279,-108,1000,31,-525,41,-42,-168,61,888,1000,1000,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{106,-288,-616,-108,-210,-91,532,-606,-118,-291,-129,740,215,510,-112,-322,-980,-460,-71,633,1000,683,-713,-312,68,-429,-332,190,608,-780,158,1000,242,745,-1000,-179,483,-624,633,130,928,-37,-1000,-215,-1000,17,85,-197,164,-52,-882,-84,-692,752,892,-84,-601,364,31,-937,-651,563,258,-535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{-12,795,864,-904,286,-674,-222,910,481,-510,-1000,29,1000,-241,-195,1000,-358,-1000,-931,1000,613,123,302,-1000,-706,-969,1000,291,419,-887,432,1000,1000,-100,-1000,-432,-411,-33,-1000,1000,863,1000,901,1000,196,-1000,929,-293,-451,-1000,-298,1000,141,-48,-583,1000,48,468,1000,-370,-422,907,-1000,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{-434,-807,504,1000,-496,1000,-86,-1000,-970,695,789,-585,-1000,-378,1000,-1000,1000,-291,1000,-945,480,859,226,317,-52,1000,-1000,1000,960,448,474,-575,-725,-467,956,-727,-211,1000,-23,-1000,-1000,-698,-1000,593,-1000,903,-1000,1000,1000,1000,350,673,146,-288,-777,-883,515,-1000,-1000,-77,800,-338,1000,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{0,60,224,-240,-1000,1000,1000,1000,-1000,1000,-1000,63,71,-1000,1000,-604,343,-652,334,234,1000,756,595,-245,-1000,-1000,1000,1000,468,-1000,442,1000,200,1000,-369,733,-935,1000,-513,216,1000,526,-475,1000,-537,-264,1000,-612,-768,-498,-1000,1000,504,-1000,-1000,434,1000,-1000,-127,-940,1000,-1000,1000,553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{-115,738,-321,329,-303,-1000,-918,929,80,1000,-183,106,-83,1000,944,-30,-785,1000,-480,-155,-231,-1000,380,-364,-800,-437,584,665,532,1000,-882,132,-6,569,183,774,987,1000,319,385,965,-319,-706,-197,350,497,731,-72,502,-339,-1000,-908,-831,293,33,-129,524,-1000,374,301,-1000,-339,389,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{1000,819,-129,-793,447,1000,34,-1000,136,214,817,-901,-609,21,-732,-774,1000,-202,-392,148,-405,-1000,-1000,-970,570,-1000,-709,283,-404,-1000,400,-1000,943,-745,-1000,-762,-998,-1000,-455,-1000,-1000,1000,1000,-984,-214,915,-1000,260,-1000,848,1000,-288,890,436,575,969,-1000,468,-973,-1000,1000,-220,-394,45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{176,-639,741,1000,-992,-163,139,-292,1000,260,-648,-1000,-1000,1000,818,-1000,-1000,1000,-278,129,-1000,-1000,1000,-1000,1000,1000,-114,180,886,-92,-400,1000,-1000,-356,-150,-1000,-1000,1000,1000,1000,-477,-741,-1000,-247,-1000,-1000,-691,-1000,-836,-1000,572,-1000,-1000,191,-1000,-307,178,-1000,1000,1000,-252,-1000,1000,30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{966,-408,-91,1000,-798,-587,720,-1000,65,-801,-52,117,-492,1000,-1000,-381,-1000,-8,-912,341,141,-589,-547,-604,-6,-399,-1000,680,303,396,-364,-256,793,637,125,568,-521,-1000,-883,628,715,558,184,119,-1000,-637,-1000,946,-1000,254,121,424,1000,-879,-406,-1000,-885,-891,521,-610,1000,1000,-411,-292}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{923,-939,154,210,504,111,555,-314,170,488,132,718,-254,-400,400,49,128,617,822,-286,-1000,-217,315,540,152,1000,383,806,-1000,-329,-170,-195,-368,-146,198,778,-455,423,-5,49,1000,-183,-1000,838,455,-385,372,-481,-441,164,1000,-665,-568,249,-140,400,-744,146,210,478,-221,-400,-94,187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{169,477,-738,1000,-1000,277,591,314,-637,45,550,-344,-1000,1000,-1000,-532,212,-1000,-800,1000,1000,-355,-1000,-1000,-406,1000,-1000,1000,1000,-1000,504,-478,1000,1000,-873,-1000,-73,-1000,-1000,-107,-361,197,478,426,201,787,-554,1000,-198,141,383,-1000,1000,-38,-752,-624,-1000,-959,1000,-207,1000,1000,432,966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{845,-900,587,-47,1000,-96,750,208,-908,-411,-635,357,1000,460,-414,744,-605,-347,27,-93,863,88,101,-866,-124,-839,-813,-56,-163,1,-529,90,1000,44,191,26,-339,-304,-1000,600,1000,612,-1000,773,-583,-395,-714,511,349,276,-683,439,1000,-1000,602,-184,31,1000,59,-105,799,-350,-730,-138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,java.lang.Integer):java.lang.Integer",
            new int[]{-208,-576,-436,1000,-865,-922,545,-315,-1000,-825,-837,-717,147,-787,117,297,684,-812,934,175,55,-168,-924,218,-205,408,-199,-161,-509,1000,221,-1000,418,-40,387,-1000,-1000,952,163,-906,-549,539,-918,-412,-1000,-534,498,928,-1000,471,193,-8,-1000,-298,-268,219,600,-772,-437,-528,1000,755,305,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,java.lang.Integer):java.lang.Integer",
            new int[]{150,-183,-176,30,249,-412,-284,72,-106,-1000,707,-99,-52,825,-1000,-737,-653,-426,646,470,877,1000,-716,-447,214,-800,358,-158,754,-708,-1000,-920,212,487,-641,-111,-557,380,136,1000,304,-144,-1000,239,160,-308,1000,-372,719,453,639,342,355,-749,215,-305,-715,-1000,-1000,-625,85,1,-9,-767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys():java.util.Iterator",
            new int[]{15,144,164,-811,-648,1000,809,-43,255,737,342,-552,-1000,880,-1000,496,-1000,-1000,223,-716,-400,400,85,1000,495,-113,1000,-592,-155,756,-277,-73,-1000,155,-335,-360,34,-316,1000,-174,-674,-400,-59,-234,-565,-193,-400,175,-997,-400,-66,207,-882,-1000,-1000,151,-538,716,-1000,157,1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys():java.util.Iterator",
            new int[]{790,648,-856,-498,-238,-723,-530,271,-370,42,-389,539,220,-971,624,-878,-631,982,858,204,731,818,-426,-617,510,-594,-919,631,334,704,594,-205,-92,-14,-439,-657,295,911,641,476,723,498,656,417,692,-335,-101,736,778,937,-40,892,40,184,-90,-166,-775,-221,88,-548,-944,459,21,969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{863,483,93,-337,-1000,1000,786,-689,641,-1000,1000,-752,-797,-882,-1000,34,-1000,473,428,368,526,-628,820,-121,1000,230,-663,192,-738,-956,-162,-630,-320,-155,58,-1000,-1000,73,565,117,62,-975,-992,-350,1000,295,-567,-758,-1000,-188,-502,-898,-51,-429,-1000,5,1000,-773,514,1000,76,-398,-165,34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{922,399,919,-263,109,671,545,527,51,-1000,650,791,-610,976,-818,-575,-688,-25,-74,-3,-31,731,653,485,1000,118,-487,-885,-330,-1000,-916,-997,683,310,-1000,-1000,557,-12,106,275,668,-827,251,826,833,1000,-942,347,-548,-569,383,-269,-346,940,656,302,-350,298,590,1000,1000,-84,-1000,-851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{1000,930,82,1000,-20,-1000,-176,1000,-115,1000,356,-1000,-289,1000,1000,-1000,-607,1000,1000,-593,764,903,469,305,-1000,-1000,1000,-1000,-1000,1000,1000,1000,1000,79,-852,-1000,814,-1000,1000,-1000,-536,360,1000,1000,1000,1000,-1000,-1000,-482,1000,1000,717,-486,-1000,1000,-1000,-1000,-1000,-700,1000,452,1000,-1000,-422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String):java.util.List",
            new int[]{-814,135,474,-212,1000,616,-494,-278,365,837,-168,1000,177,-265,-757,578,-1000,-1000,-60,7,-708,-709,-916,-293,72,-872,-272,818,-424,-703,120,-603,122,191,-659,-1000,-1000,-1000,-116,844,1000,-797,-174,-356,-535,-472,-483,-689,80,-232,-348,331,1000,-1000,-852,240,411,1000,968,-27,1000,-544,-129,113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String):java.util.List",
            new int[]{1000,150,-786,-648,-1000,501,125,514,395,-694,-68,592,-403,1000,1000,-104,1000,1000,-168,-1000,-736,-592,1000,1000,1000,-533,737,-365,-953,1000,-1000,1000,466,-412,284,140,-873,183,-1000,-1000,949,1000,1000,1000,-1000,689,-854,-1000,1000,-154,-426,-993,-723,-927,-323,636,-309,-943,765,-159,915,-936,494,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{-1000,237,-396,1000,-680,-1000,-1000,376,-277,774,962,1000,369,406,586,-1000,-1000,121,18,965,7,62,1000,-235,-255,1000,-985,-1000,-1000,1000,-1000,1000,-848,371,267,76,840,-1000,-964,-1000,-985,-1000,-963,-1000,-689,1000,1000,68,1000,532,309,578,689,-478,-1000,-775,-1000,-144,1000,800,807,866,780,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{363,-540,-104,900,-143,66,-1000,-1000,-1000,-99,-22,-478,-1000,-33,701,-725,-894,-812,-610,346,-724,141,467,941,-1000,-304,863,-538,-1000,-108,971,570,-1000,1000,745,720,455,156,-289,186,661,-140,-757,728,-669,-1000,-404,-513,604,552,-259,586,423,-338,181,217,1000,1000,859,917,1000,394,188,-68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{-36,-858,634,894,-589,-719,-245,69,-1000,180,560,452,-341,1000,-528,-529,-379,869,-240,-1000,-868,-854,126,399,-1000,-1000,-451,43,-1000,-439,519,-398,-601,897,1000,-1000,803,738,-411,-1000,-227,579,-1000,797,-1000,23,552,-69,929,-510,464,1000,-745,-1000,551,-622,-261,515,437,596,526,-920,871,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{-1000,594,664,-1000,-9,282,760,-1000,101,-1000,1000,-1000,606,1000,306,713,1000,520,711,-892,826,338,-1000,1000,841,-604,-756,1000,430,-568,1000,69,-614,-1000,1000,-1000,-417,-900,1000,-645,849,-371,-824,-1000,62,-256,-932,-1000,-1000,-417,-1000,-896,-916,-582,-455,282,368,404,1000,-1000,-844,-338,-615,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{-525,753,-446,395,471,-908,-1000,725,-1000,-214,-875,800,-898,-343,-1000,1000,-919,291,-588,-1000,972,745,-217,295,1000,-1000,-1000,1000,-906,-490,-851,1000,-1000,16,-560,-1000,-89,1000,413,-1000,-1000,-1000,681,-420,-348,-958,-32,-929,-1000,-1000,-112,-911,863,-574,541,935,1000,-1000,802,-341,1000,-1000,-25,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{-400,-609,839,-1000,-229,1000,-96,-1000,968,-1000,1000,-1000,981,937,-968,-400,153,-621,-1000,-1000,53,1000,1000,1000,-1000,976,-896,-1000,1000,-409,1000,-358,1000,472,816,-1000,-374,-1000,1000,1000,1000,-848,799,-1000,457,1000,206,171,580,-1000,-975,325,1000,-1000,-1000,1000,187,726,-649,-148,-1000,16,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{-740,750,244,-529,-1000,-643,-1000,-1000,-1000,846,-1000,-902,-519,1000,741,-1000,-988,1000,824,626,-795,-104,1000,289,-734,219,260,12,-543,-904,-1000,-24,68,-443,-239,319,1000,-819,136,812,-838,135,1000,-400,-1000,514,-63,287,640,-430,-771,883,1000,211,12,-327,300,-386,1000,103,-1000,-810,127,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{-473,-834,284,419,-184,1000,273,1000,299,198,538,-22,1000,-818,480,958,-135,-864,-518,-866,-552,261,-111,321,-132,1000,-1000,303,1000,-208,1000,-224,694,81,-567,1000,1000,1000,148,231,1000,71,142,136,611,-316,-637,351,1000,-100,449,-185,-814,1000,-617,-163,654,66,-668,537,670,1000,-665,-910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{-586,261,-247,264,555,389,-1000,1000,-1000,1000,1000,419,542,-296,962,1000,162,651,638,-686,1000,1000,956,-96,1000,173,208,-262,-446,208,1000,-134,316,-1000,-953,180,1000,1000,855,1000,1000,-490,920,-266,1000,-499,-494,1000,-488,-641,-863,1000,-205,560,207,-413,-167,366,892,688,-29,-125,1000,-94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{-246,-795,-56,-1000,1000,-606,-409,102,-1000,-586,-1000,-400,1000,1000,256,668,1000,-51,597,1000,679,1000,958,-1000,-1000,1000,-984,-932,-274,534,-975,-303,-400,-645,1000,445,184,793,-449,482,1000,208,1000,-25,1000,-400,-1000,-481,819,804,122,-373,1000,448,1000,1000,-1000,-696,-991,1000,-305,-158,-922,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{949,-963,922,-721,498,-24,421,-458,740,-157,466,962,-766,-597,-87,-843,-989,-481,-326,208,317,-387,-22,-860,590,-717,-269,888,-867,997,-661,652,248,322,-156,-499,665,21,436,361,63,-449,-487,-883,-847,-48,-666,-544,395,-29,1000,289,-278,333,-986,-894,-171,-446,-998,-505,594,383,284,137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{-271,-150,844,134,-300,-439,-220,-768,-659,-1000,473,1000,-789,944,87,-400,848,299,-420,-138,954,1000,-1000,-518,233,-1000,1000,770,-972,-758,-657,1000,1000,-1000,-1000,23,-455,1000,-504,-1000,1000,457,1000,-441,673,1000,-1000,880,-740,-1000,1000,-943,437,-452,49,-1000,-563,-828,188,859,363,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{-952,-831,-733,617,-501,1000,-308,-218,-488,963,1000,-82,587,244,-1000,-469,-357,296,-358,-1000,-722,1000,580,-130,1000,288,9,-93,1000,526,152,-1000,849,507,-432,1000,-944,-541,859,227,-214,-309,-715,-1000,355,-57,-473,-857,1000,1000,-901,459,-1000,89,-525,113,277,568,1000,1000,-1000,289,999,179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{161,-795,438,947,-776,441,-541,-750,-406,-128,592,-897,750,781,-627,67,-790,487,81,-927,-733,229,-522,-58,978,394,417,413,868,959,-497,-590,346,911,-95,802,-380,108,-364,261,-1000,550,-887,-982,954,-658,-303,250,977,796,-995,665,-578,22,-697,-572,-197,-838,24,999,-249,-44,569,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{433,-309,549,-438,-655,-1000,-431,-221,-573,238,720,63,-130,130,60,1000,-937,1000,309,-824,247,321,205,-30,126,-410,567,410,295,587,-811,-1000,-67,1000,410,344,-433,-436,78,-188,-596,322,-15,-86,95,1000,-303,100,-637,890,373,-286,-22,-727,177,-468,63,412,446,-851,129,424,626,941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String,java.util.Properties):java.util.Properties",
            new int[]{-654,657,592,-564,1000,1000,-1000,260,-1000,-470,986,1000,1000,-1000,591,-1000,-1000,-537,1000,123,-392,959,-196,1000,-59,746,-711,-1000,-847,286,-1000,1000,-501,-558,1000,-1000,-1000,-565,1000,-65,-1000,-392,-1000,-1000,1000,1000,-489,-47,198,-1000,-652,1000,759,211,1000,-1000,1000,139,-91,414,-1000,1000,-1000,-663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String,java.util.Properties):java.util.Properties",
            new int[]{-234,-441,274,326,684,869,-206,317,-1000,-221,-149,1000,598,-1000,-234,-806,-400,-809,1000,-994,-143,689,236,1000,-1000,1000,-477,-1000,-802,1000,-663,753,-742,-1000,396,-1000,-1000,-980,1000,373,-814,-952,-879,-976,609,-28,-290,-300,961,-781,-618,1000,126,377,917,-18,1000,935,-882,699,-557,400,-887,-864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String,java.util.Properties):java.util.Properties",
            new int[]{-1000,708,372,1000,848,132,-1000,113,-833,-413,-461,517,784,-92,915,-1000,-15,-1000,143,-1000,261,-11,225,1000,-1000,1000,341,95,-918,888,-730,1000,-1000,-1000,563,-1000,-1000,-1000,1000,822,-1000,-1000,-859,-1000,1000,-415,-38,-1000,1000,429,-334,1000,317,1000,649,95,632,792,-899,1000,-1000,723,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mg==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperty(java.lang.String):java.lang.Object",
            new int[]{-887,915,203,-158,1000,-684,-1000,-412,887,1000,156,989,1000,-379,-728,-1000,811,-262,-1000,1000,537,1000,1000,-868,1000,-1000,1000,-1000,37,497,-1000,749,-511,-1000,-96,-1000,-1000,-1000,-592,453,-442,-1000,192,1000,-152,-124,766,1000,-1000,-1000,136,1000,-400,-888,-1000,1000,-1000,-242,1000,2,1000,1000,9,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperty(java.lang.String):java.lang.Object",
            new int[]{-553,-216,-551,-576,-1000,582,1000,-402,-1000,-1000,-811,-249,-1000,-1000,1000,1000,1000,176,1000,-987,-5,664,-638,1000,-1000,1000,-1000,-1000,-187,-1000,264,-1000,23,821,122,1000,-1000,-1000,-197,1000,-115,1000,287,-990,-1000,-209,1000,285,1000,1000,464,-927,-1000,-451,263,331,1000,-29,-894,-1000,-351,-1000,-930,824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String):short",
            new int[]{1000,-597,-186,95,137,-133,-1000,1000,-1000,-634,-1000,304,1000,-364,-254,-963,-506,-544,1000,10,-755,415,-675,1000,-414,356,-580,1000,-203,291,751,-506,1000,993,179,1000,1000,706,-1000,-1000,238,1000,-229,-521,-1000,-885,1000,-551,643,-411,-1000,322,-477,-228,-460,-122,17,-201,1000,1000,1000,-65,828,905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String):short",
            new int[]{-850,699,-181,436,768,-347,1000,884,206,-381,720,98,-1000,-297,59,1000,1000,-931,-1000,-147,1,-776,-193,-1000,-870,120,578,-1000,216,924,-1000,529,1000,109,-842,477,-1000,315,16,-1000,-882,-541,145,1000,-1000,-279,34,-693,-484,680,99,-1000,-146,-341,1000,-641,1000,-94,-528,-438,-789,150,329,-915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String):short",
            new int[]{929,-357,-914,-109,-1000,-1000,1000,-1000,-188,-324,-1000,951,1000,340,1000,1000,-614,-1000,1000,-193,-1000,770,-1000,1000,-1000,521,-829,1000,-854,612,671,179,1000,-68,984,1000,-241,1000,1000,-1000,14,1000,-818,-904,-1000,-299,-692,-369,-123,-1000,-1000,364,-1000,-19,1000,1000,506,-1000,188,-1000,62,1000,1000,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Short:LTEwMA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{559,603,-332,-733,-1000,-1000,40,491,-1000,-1000,236,-1000,-689,562,717,486,-88,-293,1000,715,111,690,1000,1000,1000,265,215,1000,717,-1000,190,-471,867,-878,-429,1000,-230,750,-457,-1000,368,-1000,-42,602,-1000,445,1000,877,-690,-328,1000,797,570,1000,691,355,1000,-1000,1000,1000,-1000,1000,588,52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{1000,-123,822,-573,-917,1000,214,-785,449,764,-1000,1000,-1000,-1000,-1000,-591,-152,276,373,1000,991,-977,588,1000,-1000,64,-1000,1000,-966,-64,-1000,-1000,76,-726,994,-868,-777,-1000,296,549,818,-1000,-1000,-1000,-102,1000,791,1000,475,539,607,1000,-471,727,1000,-135,1000,-642,283,-418,-1000,1000,960,271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Short:NDc=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{383,-972,-192,-184,6,1000,-784,-632,-343,1000,411,1000,-410,1000,-122,1000,1000,-1000,477,838,336,-157,-1000,1000,668,1000,-926,1000,886,307,-919,638,1000,1000,686,628,247,-500,620,1000,948,-1000,808,-797,-1000,7,54,-743,151,-1000,1000,-586,1000,266,145,-460,715,-294,-21,1000,81,-297,1000,-790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{-181,603,379,-1000,-1000,-1000,1000,1000,-1000,1000,1000,-523,-159,1000,-615,1000,-797,23,685,-401,-1000,-1000,767,1000,-572,414,-1000,1000,516,1000,885,1000,-1000,-239,-1000,-499,535,-1000,-699,-1000,-1000,-1000,552,-1000,-974,-635,-1000,784,89,1000,1000,-432,97,-743,-1000,-277,888,606,1000,-1000,-1000,-1000,-848,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{-1000,441,-672,-301,-738,1000,304,-885,-1000,1000,937,-303,-205,640,-700,536,-1000,-537,-56,471,-863,-793,-1000,-5,1000,-578,-448,1000,93,539,-211,-338,-1000,432,1000,-257,972,-1000,883,-188,-1000,628,-76,-1000,-763,1000,420,1000,36,717,650,-337,532,273,-323,-161,-735,1000,-83,-178,-493,1000,634,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{1000,-420,363,821,1000,-1000,-1000,-1000,560,-1000,-1000,205,187,-1000,1000,-1000,1000,-1000,-1000,1000,-145,1000,-1000,1000,706,-147,1000,-1000,545,-1000,-1000,647,1000,-1000,1000,834,-187,-1000,1000,1000,1000,1000,-687,-1000,1000,1000,652,-897,-1000,-1000,-1000,-4,186,1000,1000,1000,-1000,428,-1000,1000,1000,-281,581,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{-820,-261,287,-34,-1000,-328,-600,-1000,946,-805,-1000,1000,1000,1000,632,487,-1000,-1000,1000,689,-983,265,-613,-703,299,1000,550,1000,70,-1000,97,-1000,600,-1000,-1000,-1000,1000,668,211,827,1000,-254,-477,-406,887,-111,-1000,-1000,222,715,-1000,-261,-1000,-1000,573,-883,-293,-1000,-1000,-879,-1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{-1000,-693,4,-385,-1000,-1000,-1000,-1000,946,-555,-1000,1000,1000,1000,896,1000,27,-1000,-109,668,-903,813,-614,-726,326,1000,1000,1000,-455,-642,655,-1000,1000,-1000,360,-1000,1000,-12,1000,1000,1000,256,-1000,9,1000,178,-1000,-1000,-787,334,-1000,-891,-808,-772,957,-462,656,-1000,-1000,-1000,-1000,-1000,-287,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{350,894,-131,-541,1000,808,152,1000,-661,376,547,-1000,-476,295,268,-762,316,1000,-1000,-60,885,1000,1000,756,1000,-399,-1000,-1000,18,-489,-839,-179,-745,938,943,-287,887,530,87,-1000,-1000,213,-1000,28,716,1000,-186,-78,1000,710,770,831,-119,870,-1000,-875,-593,35,1000,1000,-716,1000,584,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.String:V0RUM3h6SWVTYXA=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{902,-369,944,814,591,-96,-266,-848,785,-14,-74,-850,-313,-643,943,686,22,-132,35,-727,-741,-860,-575,755,-731,603,583,498,-782,580,-223,496,-165,-304,-26,-417,-673,-984,10,968,-842,799,506,-291,-340,-480,-550,398,-951,895,586,342,749,-939,784,-180,-817,754,440,622,-842,380,-407,-992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{1000,189,-396,785,1000,-269,97,-1000,1000,420,-786,-252,-303,-715,-184,-609,-327,-303,228,-984,-1000,-697,-1000,1000,-1000,1000,1000,1000,-324,1000,265,24,1000,-182,1000,-651,-367,663,1000,379,357,-751,233,-15,-168,247,1000,1000,-594,1000,1000,1000,760,-1000,1000,-255,-199,737,178,36,-1000,-215,-1000,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{198,432,-271,1000,-400,-274,-477,-1000,-445,-1000,922,-438,20,-1000,447,696,1000,823,164,526,-1000,-928,-444,-62,580,376,-1000,-28,732,1000,-672,-66,-1000,645,-500,1000,-835,95,-1000,1000,-548,-155,1000,-486,-283,-1000,-89,-468,-349,1000,-1000,250,997,26,-1000,-196,224,672,-687,391,-539,837,1000,-83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:0", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getStringArray(java.lang.String):java.lang.String[]",
            new int[]{190,-744,-1,830,340,-473,-1000,-1000,-458,-332,1000,87,1000,-539,-583,-785,-1000,1000,45,-269,548,-360,-147,769,509,1000,366,-598,532,1000,-432,-30,158,-900,-670,918,-961,151,-1000,-1000,-1000,-1000,-1000,451,867,-451,-307,-498,-1000,196,819,990,-702,-123,-341,-1000,282,-135,-752,146,-920,-1000,740,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:0", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getStringArray(java.lang.String):java.lang.String[]",
            new int[]{-641,816,-711,1000,368,-1000,808,539,-1000,-1000,1000,1000,1000,1000,1000,439,1000,-121,1000,1000,360,1000,561,-214,139,-1000,-1000,1000,1000,-958,-1000,-68,-1000,-885,252,-1000,-617,1000,-1000,50,-351,1000,1000,132,381,-1000,1000,-332,755,-1000,688,-581,456,-677,1000,1000,-558,248,741,-410,445,757,-1000,789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{-684,297,193,-1000,43,-327,606,43,-1000,-46,-23,-272,-299,-1000,-84,700,1000,813,1000,-245,-401,-332,452,-141,763,-74,309,1000,434,-97,-400,1000,1000,401,168,-208,362,764,84,-405,1000,216,-68,-72,411,111,327,-213,-385,765,411,-114,1000,-194,-151,181,888,-620,77,-476,1000,-400,606,832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{-1000,-96,-711,-962,-418,-560,1000,43,-1000,-1000,-1000,-856,-622,-238,-84,1000,596,918,1000,1000,-1000,1000,1000,-1000,598,-976,-578,1000,733,809,1000,-282,1000,-929,-1000,14,1000,508,-576,-1000,-1000,-1000,-906,-1000,1000,-1000,237,-1000,-385,1000,1000,-961,644,-736,-151,-572,1000,-1000,-86,497,1000,1000,1000,832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{142,-774,793,303,153,935,442,-643,-840,1000,1000,1000,956,-1000,8,390,1000,152,60,-1000,758,-1000,-857,883,954,1000,869,-1000,1000,-1000,-1000,1000,-1000,79,1000,-1000,68,175,1000,1000,577,1000,-292,238,-585,1000,733,1000,-1000,-1000,-1000,-1000,1000,1000,1000,1000,-1000,4,388,-1000,1000,-1000,12,-554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{231,-636,-431,-891,-718,-999,1000,-725,-907,-457,-747,-79,-923,-993,-1000,-294,616,512,69,-786,-291,-753,-427,1000,319,37,882,724,891,-938,456,-562,265,23,-457,-1000,198,908,817,920,-324,-1000,785,-241,649,314,79,-242,-1000,508,681,23,522,-427,759,-565,-91,-59,-1000,-635,863,612,606,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{-570,93,544,-332,659,-1000,876,905,401,480,759,-1000,-521,312,-572,-204,1000,-638,-72,-1000,762,225,-1000,1000,-682,-919,-408,-181,180,-1000,1000,-458,-558,1000,-144,1000,-106,-1000,606,-713,1000,-19,-312,120,-116,629,-57,882,277,524,1000,1000,-1000,-215,484,-109,72,-31,19,-876,525,1000,-1000,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{222,-756,693,-674,-224,1000,-433,-1000,-709,-375,531,387,719,-1000,-463,-530,-411,84,222,-612,1000,-1000,-1000,-1000,-567,1000,1000,-1000,-1000,-850,-1000,471,1000,-1000,855,-1000,-1000,403,721,193,-541,-293,703,126,1000,791,-824,-1000,899,-131,-1000,265,592,-760,1000,848,706,529,163,911,203,-1000,1000,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{485,402,334,20,898,4,-358,-489,-49,939,542,234,-91,-97,173,172,799,688,-738,-400,-54,392,-271,909,-608,-686,234,761,-938,51,787,-875,-318,-350,-818,853,-870,-945,247,-362,-854,544,-312,-581,167,366,-367,608,400,382,925,-124,-783,-859,-372,644,419,-472,320,932,26,899,257,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "isInitialized():boolean",
            new int[]{101,90,-431,-979,-269,-1000,-310,-104,-643,1000,-950,1000,-1000,-1000,845,-1000,-104,-1000,74,-631,-1000,-1000,-744,796,-1000,433,-1000,-328,-1000,-110,1000,514,-1000,1000,1000,-519,6,351,-847,-184,233,-230,-214,-1000,-1000,-608,212,425,-436,-594,529,-1000,-430,-498,1000,-968,-1000,-1000,487,915,-1000,-1000,-294,-120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "isInitialized():boolean",
            new int[]{-613,-48,187,503,884,999,-697,-88,409,160,-192,-412,315,-119,697,226,140,212,281,43,525,516,473,-824,147,-746,776,-458,-429,206,-644,-827,404,-664,-658,231,-453,-394,965,444,465,-504,491,550,396,694,-978,-534,197,-463,205,370,148,-923,-361,681,422,404,326,968,-811,793,-63,254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream):void",
            new int[]{-575,-945,24,-232,-483,-626,1000,-1000,648,391,-19,-1000,-599,1000,1000,1000,-726,-232,1000,-1000,1000,-65,30,431,-718,157,121,696,-202,1000,-242,524,868,-145,793,1000,472,-953,-384,1000,1000,1000,-1000,1000,-1000,-1000,850,1000,-1000,187,398,1000,-1000,302,-936,1000,-1000,-729,-1000,-1000,1000,852,164,-659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream):void",
            new int[]{-1000,141,719,1000,304,630,-400,30,-447,-500,-19,823,-112,-495,-1000,530,99,-529,-961,1000,-773,426,-840,-670,504,157,-197,182,-328,1000,95,41,1000,-1000,-1000,1000,166,-953,496,1000,48,-1000,-1000,-901,-442,-344,610,278,1000,491,449,-219,656,-666,-828,193,48,-768,-1000,-303,1000,-1000,-733,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{151,-351,684,949,-58,-665,-1000,-409,891,-1000,-180,466,-277,-586,-44,907,247,-1000,467,-815,-1000,1000,155,-121,1000,-1000,-1000,-682,-505,1000,-711,-1000,656,-200,-105,-437,1000,-1000,183,1000,582,-199,856,-344,-320,-1000,926,-614,295,-1000,1000,-566,846,-660,700,-1000,-541,225,1000,584,-103,1000,-975,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{-233,-549,969,-818,-303,-548,-540,329,920,-785,-677,688,-420,-186,325,166,617,752,-778,-297,834,541,186,576,752,-732,-444,-808,-736,-544,925,-495,46,-738,719,354,510,-861,352,472,660,-588,807,-308,633,-50,-921,971,-36,80,-729,253,592,-811,374,-116,-439,114,-274,354,-39,315,-3,449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{873,243,-41,-630,510,466,-223,412,313,-1000,124,-569,-339,-1000,-577,-264,621,1000,115,-195,1000,-1000,-536,192,-954,1000,1000,230,-1000,931,-381,-1000,670,1000,1000,766,-1000,398,-1000,334,26,1000,297,-590,-355,1000,-1000,-170,1000,1000,-508,1000,-324,-1000,-1000,-1000,968,1000,498,-1000,-1000,917,271,-541}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{1000,-486,-461,-1000,-1000,-476,769,-110,828,-517,552,720,77,1000,1000,1000,-813,-1000,643,-1000,1000,95,189,1000,296,-286,-304,-1000,-1000,-907,-1000,-300,-103,1000,267,566,-1000,-669,104,1000,488,884,-120,191,-1000,-1000,1000,-390,1000,973,45,-959,-1000,-153,-1000,-635,33,247,79,-777,-1000,-1000,266,-525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{723,48,-642,-1000,-903,-128,368,401,1000,-626,266,-264,-846,-337,-374,-277,81,427,684,-1000,-251,-1000,231,-247,684,-276,-136,-461,-394,-642,709,-140,687,313,432,647,-351,11,-594,1000,166,-217,-392,1000,-1000,743,49,268,724,-673,1000,-411,-607,-616,-342,-781,1000,-504,422,-433,-804,322,636,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "putAll(java.util.Map):void",
            new int[]{-126,615,588,-211,201,-554,915,-570,-1000,-378,1000,109,1000,-1000,753,75,1000,177,-1000,-40,307,-796,-38,263,361,-769,-857,-436,-711,16,43,-1000,-79,336,360,-54,687,178,250,-437,287,-1000,-1000,-1000,281,213,-12,-551,-33,-730,223,-400,762,1000,-907,-650,-401,1000,287,1000,586,-939,1000,960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "putAll(java.util.Map):void",
            new int[]{-714,-405,389,793,-780,-543,-1000,-186,878,477,138,49,258,-311,526,-1000,1000,-1000,-512,-314,-732,-1000,-461,453,-919,-843,-1000,857,-1000,1000,-1000,720,-1000,-966,1000,151,-708,1000,-438,-951,196,-436,350,-982,599,488,-1000,-854,231,656,118,242,1000,-1000,317,-81,-944,1000,-213,-152,-388,-19,720,207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "putAll(java.util.Map):void",
            new int[]{-176,567,-756,-800,896,678,1000,-390,-777,15,413,-923,648,-861,-241,866,-1000,843,194,989,1000,581,-502,-490,521,-615,585,-557,-52,820,365,-652,370,-116,-251,130,584,112,-190,756,-709,624,-363,185,-42,-521,118,820,124,-1000,826,89,-769,494,428,-243,-266,171,-133,1000,576,-148,196,535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "remove(java.lang.Object):java.lang.Object",
            new int[]{880,-321,54,1000,-832,635,970,-6,-101,286,-1000,60,-848,-1000,83,1000,80,-981,736,542,-412,262,943,692,259,1000,624,-967,843,279,528,901,-97,624,734,1000,867,491,-831,-357,-813,1000,263,-204,75,631,-354,-573,-692,799,635,274,1000,-389,-29,395,-1000,806,-759,-574,426,-126,-263,34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "save(java.io.OutputStream,java.lang.String):void",
            new int[]{1000,-42,768,-1000,1000,366,538,1000,1000,778,402,-337,-690,-660,-1000,217,1000,1000,381,-291,-205,1000,-929,887,481,261,-1000,1000,-980,-477,-1000,971,1000,-1000,-729,343,-268,-1000,-602,1000,-77,-1000,403,-1000,-1000,-437,1000,211,641,1000,475,-336,789,-143,-980,1000,-1000,621,-1000,441,101,-274,-269,516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "save(java.io.OutputStream,java.lang.String):void",
            new int[]{1000,-120,209,-1000,-188,400,165,-86,-362,990,1000,-813,991,-582,-752,320,-580,488,400,203,672,542,-755,205,465,275,-143,-163,603,6,-314,46,205,-952,-4,-941,11,-998,450,923,348,-465,-176,589,400,-137,-123,854,400,-400,-1000,-955,380,-510,437,-373,-400,579,-422,-1000,-1000,516,-1000,7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("VOID|getInclude=java.lang.String:MHg4MDAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setInclude(java.lang.String):void",
            new int[]{-462,-348,-131,929,-1000,-576,-635,-1000,-125,-401,779,-597,-6,-1000,81,-583,-330,-164,-400,-1000,715,-502,-1000,-862,-1000,1000,-1000,235,69,1000,1000,663,-400,107,-1000,-422,1000,1000,-381,-1000,1000,-1000,-292,842,-949,1000,-874,477,-1000,-350,-38,1000,-1000,-1000,400,1000,1000,94,714,1000,41,-1000,585,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("VOID|getInclude=NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setInclude(java.lang.String):void",
            new int[]{805,720,359,72,690,637,603,-526,-361,1000,-1000,662,-391,1000,998,734,349,187,768,740,-668,1000,571,-688,1000,-1000,1000,-391,613,538,-400,534,-163,-1000,694,88,-1000,-285,-45,-806,-1000,1000,629,150,783,978,106,1000,773,894,-517,-1000,16,406,-45,-923,-248,-251,-173,-401,-737,767,193,403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setProperty(java.lang.String,java.lang.Object):void",
            new int[]{-986,687,454,-516,-268,290,-1000,-48,1000,-1000,-543,16,-560,-56,-723,-880,-532,-1000,-628,40,-1000,-643,-372,-1000,1000,-149,294,-824,129,607,-872,815,-196,594,960,475,849,-1000,-179,605,-241,-1000,-1000,-70,342,-1000,-1000,-348,23,1000,-896,-820,1000,501,-993,239,-39,1000,949,438,744,1000,-310,-269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setProperty(java.lang.String,java.lang.Object):void",
            new int[]{-20,513,-207,1000,-1000,-1000,-372,758,-167,1000,144,-1000,-849,-97,-1000,-1000,-1000,275,1000,-1000,206,402,-1000,1000,668,-1000,-857,-616,-1000,697,19,287,41,569,-342,1000,-1000,-703,379,601,877,592,-394,-1000,486,-1000,1000,-60,638,-1000,-304,-381,-1000,-10,-456,-259,1000,360,732,475,-149,-796,-788,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.ExtendedProperties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{-202,795,487,-244,63,5,507,-116,-777,935,356,514,-463,12,-56,-122,-1000,213,216,277,228,270,-246,-983,-629,1000,-3,-712,-1000,22,-607,113,-295,453,-736,175,81,-122,513,-140,532,-432,-478,695,218,948,198,253,-184,24,-166,-681,1000,479,238,41,-466,-619,857,-281,489,-1000,-312,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{441,-684,329,-610,678,119,339,344,389,1000,-618,-82,870,-794,-330,441,669,-320,-718,869,383,787,473,550,-402,-1000,-1000,-196,-492,-511,-414,-291,828,66,568,-28,-9,286,-463,105,838,599,535,-544,209,-363,202,1000,448,-691,-117,118,-1000,752,766,761,-301,733,427,-217,938,733,567,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{1000,12,-756,-180,-600,720,1000,-1000,-1000,647,565,375,560,-1000,66,1000,451,1000,-1000,808,1000,572,1000,-15,-79,128,-374,-1000,-975,-1000,1000,511,1000,-474,1000,957,412,1000,-17,-1000,768,1000,-236,-1000,219,-1000,-50,710,1000,-1000,-1000,-821,-636,-1000,1000,231,-601,1000,810,-1000,699,117,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{881,-609,38,1000,821,1000,1000,-1000,69,-246,-73,197,892,304,443,635,747,1000,-1000,124,1000,-585,272,1000,1000,490,-370,518,-745,-1000,-593,673,1000,-634,1000,621,-605,1000,-590,1000,1000,1000,593,-637,1000,634,282,92,755,209,-142,-1000,-1000,-1000,857,162,-665,-219,977,-238,-459,1000,-212,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{14,543,134,-385,-671,60,623,-787,598,1000,108,1000,618,326,-946,1000,-673,-550,-1000,382,-130,-1000,996,-293,-1000,997,1000,1000,-1000,-236,864,-681,-363,752,306,-801,-650,-688,-77,-1000,85,621,-99,-1000,-876,867,-233,1000,-1000,-330,315,316,469,-387,1000,-313,-389,652,-1000,911,-18,1000,-716,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{-1000,-393,-193,1000,-992,-584,901,-151,1000,1000,-1000,-14,-1000,-845,397,1000,-866,252,-220,-684,-395,-1000,1000,599,-1000,-108,1000,875,-1000,-1000,-596,61,-174,360,-425,-1000,-691,475,584,-1000,-133,-1000,1000,-181,-297,1000,-1000,1000,-1000,541,-742,-1000,-410,-1000,-85,-119,1000,-262,366,-1000,348,812,-1000,393}));
    }
}
