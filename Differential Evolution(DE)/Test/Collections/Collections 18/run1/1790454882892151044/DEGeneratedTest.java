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
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "add(int,java.lang.Object):void",
            new int[]{1000,1000,-326,1000,142,937,525,803,84,486,-685,434,-658,-1000,-173,-894,-1000,961,899,295,-245,-1000,335,-317,667,-526,176,842,-1000,26,807,-1000,-681,1000,-957,863,1000,1000,-33,-204,329,-580,901,1000,-638,-1000,139,817,-363,-3,877,1000,549,-846,189,897,-60,-100,613,-6,-470,571,-455,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "add(int,java.lang.Object):void",
            new int[]{-800,264,299,30,1000,794,514,-605,263,-883,-1000,457,904,-178,442,-71,-1000,-53,-1000,-428,-76,-1000,-181,417,-1000,-592,243,-1000,-218,158,-17,-1000,-1000,818,-139,441,468,171,772,764,-176,-204,302,1000,-1000,-1000,-793,-461,376,-1000,1000,328,-704,402,-665,-694,776,366,1000,169,-748,-644,401,481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "add(int,java.lang.Object):void",
            new int[]{668,-185,-1,616,-249,134,849,-340,737,652,-741,799,604,-962,-917,-860,-524,-10,-827,155,-549,-436,-940,952,-667,-110,-457,-598,-849,-200,434,-985,-68,630,238,366,117,964,320,570,204,-513,-736,791,-273,-760,-477,121,-465,-415,247,279,-535,-69,-810,-459,974,-63,998,60,81,269,834,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "add(int,java.lang.Object):void",
            new int[]{-1000,1000,-377,1000,-919,226,68,-72,-293,395,-1000,23,71,121,-386,-1000,-1000,1000,-1000,721,391,-641,400,-175,-1000,-582,-542,199,1000,741,389,243,-228,466,-1000,-257,1000,1000,735,-538,1000,-1000,34,1000,-398,-1000,-655,1000,-1000,1000,65,823,-647,-172,207,191,-194,-663,-16,-1000,-1000,839,-400,-700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "add(java.lang.Object):boolean",
            new int[]{-87,513,-216,178,246,611,38,664,438,833,834,-828,602,790,-424,368,-942,857,-71,-131,-790,249,517,85,486,681,605,-642,-666,698,101,-800,191,60,-78,724,379,421,581,-725,959,-71,-280,-331,-293,-655,-778,-516,592,-154,-470,381,408,-276,851,-413,-490,887,32,-660,49,275,68,-632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "add(java.lang.Object):boolean",
            new int[]{239,1000,898,-204,-232,-584,178,-1000,-261,1000,-113,-139,-751,-688,105,133,946,22,-781,-726,255,446,2,-1000,-198,467,290,-149,499,673,-33,802,-375,-641,-76,324,-165,168,-74,-494,651,364,-1000,-668,724,326,302,-422,-78,-260,-4,275,70,97,-468,-128,182,218,-912,9,153,237,452,112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "add(java.lang.Object):boolean",
            new int[]{1000,606,898,661,-547,-997,839,1000,443,270,940,-566,-425,198,-355,-189,1000,508,-984,22,554,672,14,-779,-236,1000,-377,-74,-766,1000,-791,-663,-375,-836,-565,422,755,-1000,772,-682,991,59,126,-884,137,-813,-846,636,-438,-1000,-583,-318,-1000,-560,277,-416,-625,-222,256,-1000,128,714,934,-96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "add(java.lang.Object):boolean",
            new int[]{-175,271,159,-467,-578,1000,342,1000,909,584,1000,-1000,272,1000,-475,888,-242,479,-185,818,-882,405,523,-944,-169,445,905,-1000,-974,646,40,-1000,-758,353,1000,1000,832,-180,455,-969,-202,196,452,-978,-300,-1000,-1000,175,648,209,49,-665,-161,511,920,-574,-1000,920,-30,-1000,-332,864,22,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "addAll(int,java.util.Collection):boolean",
            new int[]{526,521,29,-434,-212,134,369,855,-231,-22,310,-762,1000,-96,35,295,-65,549,-935,384,-1000,1000,-1000,-584,-458,-1000,-1000,767,1000,-112,-563,745,-1000,-190,-1000,459,-855,-1000,762,-346,-572,271,651,446,890,15,693,689,1000,-1000,1000,-1000,131,131,1000,-419,838,1000,-1000,1000,573,-312,-1000,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "addAll(int,java.util.Collection):boolean",
            new int[]{-205,-1000,-456,563,-1,1000,615,-7,-607,-979,-479,-414,630,1000,137,-734,-931,545,-215,-796,-401,-961,612,-750,-1000,-1000,1000,175,-1000,-1000,259,532,-791,304,889,-466,866,-387,-1000,623,1000,-1000,-128,-519,-575,-531,-138,-3,-1000,223,-436,-2,-69,-725,-247,-455,1000,-778,1000,677,-542,347,-906,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "addAll(int,java.util.Collection):boolean",
            new int[]{446,997,-656,328,319,-341,1000,182,-558,-1000,-264,382,-422,-481,-613,-801,938,1000,833,795,1000,1000,604,-732,-1000,569,-119,-655,-392,583,-1000,-583,-164,617,-700,1000,1000,949,643,-172,1000,435,-679,-1000,1000,-693,-229,413,-63,-283,202,977,-302,82,745,-429,-514,-475,-601,-1000,-837,-501,860,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "addAll(int,java.util.Collection):boolean",
            new int[]{804,1000,-691,120,-854,-904,1000,-142,-1000,-954,-575,311,-584,-1000,-1000,-743,1000,1000,916,407,1000,1000,1000,153,-345,972,-140,-1000,701,1000,-1000,-447,355,577,-911,684,1000,1000,1000,-148,1000,20,-566,-1000,-199,-1000,-1000,42,-279,302,-1000,1000,-318,763,219,178,-1000,138,356,-1000,-315,-396,1000,358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "addAll(java.util.Collection):boolean",
            new int[]{-751,-110,979,668,224,422,366,-15,182,280,956,114,763,286,-600,909,547,443,673,-252,-41,406,957,-662,556,-827,327,881,-186,-411,-283,360,923,-172,568,-958,-971,659,33,-262,876,35,-352,-165,-163,-749,966,662,992,-170,-186,378,-939,-894,-445,219,-223,-514,-980,-295,587,-629,617,-812}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "addAll(java.util.Collection):boolean",
            new int[]{-813,-220,-56,668,-107,79,-194,-166,405,-343,-672,-387,173,781,1000,725,762,683,532,-155,233,632,552,-81,488,267,518,-1000,-380,281,-283,342,183,328,568,494,-283,-380,-575,-757,264,559,673,118,-1000,-872,-680,-335,599,655,504,711,-939,-1000,-445,-1000,-789,-1000,-1000,516,237,663,1000,-563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "addAll(java.util.Collection):boolean",
            new int[]{638,-1000,314,-219,190,1000,22,1000,-1000,447,45,-393,124,-1000,47,-376,-1000,-732,-829,-375,703,-952,-409,1000,-950,258,-822,1000,-743,212,-575,-379,-1000,779,-747,-86,238,1000,1000,544,-764,-1000,-114,-457,13,943,299,-31,-739,-772,-979,-1000,-167,-261,786,1000,1000,-983,-68,-171,80,-636,204,849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "addAll(java.util.Collection):boolean",
            new int[]{543,30,398,147,-11,-128,632,293,-911,95,-651,565,480,-681,254,-161,45,-1000,-912,-744,-625,-1000,-319,285,-688,-498,247,1000,18,-1000,672,-799,723,-686,-1000,622,-952,977,816,924,-1000,-1000,-39,-417,468,122,263,-252,-1000,1000,-199,-404,590,-791,-44,1000,-308,798,347,-1000,-276,-560,-342,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.UnmodifiableList", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "asList():java.util.List",
            new int[]{-861,143,744,178,114,-222,-1000,-87,-408,638,227,-148,85,-914,1000,-186,-664,-623,1000,-324,406,822,1000,-1000,282,-98,-471,374,719,-778,1000,1000,174,-810,-149,196,-910,949,-1000,-122,449,-497,765,-122,692,1000,1000,-267,330,-643,-983,-1000,1000,7,-935,-1000,-1000,123,877,-434,676,1000,1000,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.UnmodifiableList", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "asList():java.util.List",
            new int[]{-3,599,-866,-14,666,1000,492,967,-408,-363,482,-759,416,-416,312,284,589,1000,1000,488,-911,-162,-450,359,-763,960,-965,770,286,-905,-338,274,763,-650,-176,994,-134,898,403,-906,1000,921,896,-563,-193,-107,899,609,223,-643,-493,488,-416,-8,328,227,390,-580,150,-139,-254,922,461,-717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.UnmodifiableList", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "asList():java.util.List",
            new int[]{-317,-398,163,138,477,-1000,413,-886,307,-113,-143,-83,-1000,-940,1000,-332,-1000,-1000,-137,-225,-418,890,-667,1000,129,1000,1000,60,-843,-332,1000,645,-1000,457,921,534,-1000,787,736,223,-263,-400,-1000,-393,1000,-299,-1000,-748,1000,-342,733,-670,888,-1000,-786,-464,132,-562,-1000,-80,1000,1000,1000,-175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.UnmodifiableList", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "asList():java.util.List",
            new int[]{-1000,1000,449,-106,-1000,-151,-405,840,-597,326,-256,-89,-237,-803,-1000,-895,104,412,978,-163,360,1000,174,-407,308,-193,332,-232,-663,652,1000,1000,112,-297,428,110,-404,1000,-1000,-935,1000,217,34,778,-1000,833,235,75,-1000,-983,-1000,217,-313,1000,-1000,-251,-1000,-1000,-78,680,137,1000,215,-429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.UnmodifiableList", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "asList():java.util.List",
            new int[]{547,-8,-186,-944,476,324,-501,453,-327,-90,-466,-666,-1000,-986,-775,-361,548,121,1000,611,334,620,584,-286,-656,1000,1000,-545,92,145,-131,111,423,613,40,-60,-289,-237,-396,103,-42,808,-501,545,-808,-90,248,-634,-850,-987,-109,-678,-705,655,-1000,-302,-960,-602,-648,420,945,1000,-528,-93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "clear():void",
            new int[]{-600,-1000,-461,-403,-152,-762,-111,720,603,-71,350,123,570,389,487,-235,-638,-99,-1000,-317,24,856,506,1000,1000,935,-833,506,-1000,706,453,1000,-478,1000,-1000,686,-779,-146,-439,543,-1000,648,1000,1000,-1000,67,973,51,583,1000,-1000,722,-703,-86,636,573,1000,311,1000,-709,1000,63,-121,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "clear():void",
            new int[]{864,649,479,59,1000,-848,-468,1000,-588,-1000,68,705,341,-129,-376,501,1000,-1000,886,-1000,1000,-141,102,694,-1000,613,-274,-153,928,139,416,-1000,-1000,275,1000,1000,-377,-344,447,48,-921,543,-646,400,1000,-1000,713,-1000,-1000,901,334,-1000,39,1000,-200,-1000,-65,-992,887,1000,-1000,-849,-235,394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "clear():void",
            new int[]{20,-749,-157,479,-12,-762,481,1000,1000,-480,-265,-112,-264,-299,1000,-1000,-654,-790,-1000,-1000,-69,295,578,1000,-52,178,287,960,42,-938,190,818,365,851,441,-706,-576,261,1000,1000,1000,-1000,338,309,-1000,-434,1000,-251,-697,1000,-984,1000,1,405,-640,-577,447,331,590,-1000,1000,400,-581,-899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "clear():void",
            new int[]{673,-488,-331,-151,783,-1000,236,1000,-564,-989,-1000,1000,427,-223,-310,438,982,-838,974,-989,1000,-181,331,40,224,751,-90,-661,-234,39,1000,-598,-1000,674,644,952,-1000,1000,343,20,-860,408,64,931,1000,-926,20,-1000,866,-1000,-105,-277,-537,921,-575,-543,629,-568,-315,1000,-928,-987,314,130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "clear():void",
            new int[]{248,1000,132,1000,-588,441,-197,269,-21,538,-1000,-1000,-624,1000,28,-853,457,-909,-1000,1000,1000,-224,-135,-1000,-1000,228,333,-413,624,-58,159,-945,1000,-528,463,718,475,-86,-1000,-1000,-1000,-1000,-1000,-342,217,-1000,-519,1000,-893,-301,1000,-546,-939,-851,-503,-254,-1000,-926,540,805,92,-870,-1000,-888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:aXRlbTI=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "get(int):java.lang.Object",
            new int[]{-573,-1000,544,447,752,680,687,393,171,670,460,11,191,-270,594,-208,-1000,619,-1000,-1000,-39,384,-953,1000,-739,-868,-877,-491,-964,785,-639,566,413,-797,167,-1000,825,17,425,-1000,-936,-294,895,6,926,248,957,745,784,202,-928,-1000,327,20,-505,-633,-69,-329,152,-237,-1000,-466,-129,536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "get(int):java.lang.Object",
            new int[]{-1000,282,299,337,-115,1000,-1000,-996,1000,1000,-712,1000,-630,-1000,1000,-635,-559,-308,-579,-1000,-989,-356,400,267,-1000,-1000,-635,-541,1000,1000,1000,-1000,429,-1000,-238,-39,107,176,1000,684,571,59,787,-72,-1000,-1000,-363,580,1000,-1000,1000,457,1000,186,-502,515,-400,-1000,-1000,-742,-374,-23,-653,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "get(int):java.lang.Object",
            new int[]{138,-1000,-531,-295,-19,59,-1000,-396,-785,-497,1000,-515,397,-480,981,293,53,374,-1000,33,-236,1000,-1000,-907,1000,1000,93,-197,51,-778,-533,930,-357,-574,1000,412,613,1000,-316,1000,-1000,-659,-1000,-437,1000,-710,1000,828,-1000,905,-1000,-180,-307,-802,868,-96,-1000,-1000,1000,1000,1000,-640,-995,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "get(int):java.lang.Object",
            new int[]{-1000,-655,-461,28,32,376,921,-1000,-127,-571,-1000,318,-301,297,718,-65,-1000,356,-1000,407,-1000,-625,1000,-739,983,-318,-247,-27,-30,-624,-1000,328,44,1000,135,-1000,-850,527,282,1000,409,359,-753,1000,820,369,335,-1000,-358,-423,-927,-41,1000,1000,-265,-956,29,213,-997,-900,-218,-602,937,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "indexOf(java.lang.Object):int",
            new int[]{-1000,-1000,448,398,-1000,-1000,-802,-1000,911,-1000,-956,99,297,1000,-573,-420,-601,860,643,-1000,-755,-486,1000,-846,1000,-470,884,-767,-1000,218,1000,1000,429,-408,144,-1000,924,-1000,1000,-246,87,-400,-1000,357,570,-167,-970,-761,-1000,140,-699,-1000,-1000,-1000,-318,621,1000,-456,-1000,160,467,159,238,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "indexOf(java.lang.Object):int",
            new int[]{-233,-558,163,-474,-583,-559,-1000,677,717,-383,-607,200,85,-407,445,189,-1000,939,1000,-171,51,-567,431,-692,1000,1000,523,187,-1000,272,-498,-3,330,1000,1000,-386,741,-1000,1000,913,-169,1000,41,270,-305,1000,849,1000,-56,183,-1000,-133,961,402,20,312,512,-877,-640,-2,84,143,-1000,421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "indexOf(java.lang.Object):int",
            new int[]{566,358,-246,137,881,-53,944,993,-1000,480,402,185,-292,-201,-306,-989,685,292,-779,482,1000,540,-109,571,-1000,329,542,-373,871,-119,-1000,1000,-891,-544,-515,279,-602,1000,255,-986,334,-400,1000,-196,282,-935,-559,-269,42,-89,1000,-300,267,-216,341,-930,-1000,1000,1000,-698,1000,485,367,-161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "indexOf(java.lang.Object):int",
            new int[]{-524,174,-276,-285,-171,-139,-77,-365,321,-889,114,-373,342,267,657,-262,143,545,48,-680,-53,-138,26,-32,1000,-148,-547,-484,271,67,688,301,286,-146,-67,-260,25,124,-566,-451,303,-840,1000,441,-696,-409,726,-457,-735,915,-265,-151,-1000,-467,-383,357,920,56,108,-502,-385,-646,790,374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "indexOf(java.lang.Object):int",
            new int[]{-964,498,978,-38,251,-671,1000,462,-846,13,687,522,-1000,330,-1000,-967,-1000,-412,-1000,821,988,326,-213,1000,-209,148,1000,-1000,-387,-986,-107,-338,531,1000,1000,636,1000,-252,-239,-1000,-896,-692,1000,557,735,861,-392,447,1000,1000,1000,274,1000,-235,-1000,-658,-1000,1000,-239,1000,343,506,590,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.set.ListOrderedSet$OrderedSetIterator", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "iterator():org.apache.commons.collections.OrderedIterator",
            new int[]{-9,1000,869,-276,444,1000,661,1000,442,373,648,90,-1000,-1000,-686,628,611,250,209,-1000,600,-251,78,-967,1000,229,-910,-100,877,-633,-268,-524,1000,-861,-727,-1000,532,516,-592,1000,-392,-975,-1000,703,272,1000,-1000,1000,-1000,1000,0,-1000,-929,-334,-284,335,904,-373,-437,-581,-95,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.set.ListOrderedSet$OrderedSetIterator", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "iterator():org.apache.commons.collections.OrderedIterator",
            new int[]{350,380,233,-988,442,815,48,704,-70,41,-153,368,-977,954,569,476,-164,-764,311,873,661,497,60,-18,-41,-823,16,-956,948,-755,875,-107,159,786,176,-634,865,221,-300,598,894,-931,-256,-832,45,916,96,377,-993,594,-478,728,-452,-584,-324,151,532,-234,-528,-568,302,-628,176,-124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.set.ListOrderedSet$OrderedSetIterator", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "iterator():org.apache.commons.collections.OrderedIterator",
            new int[]{369,111,484,-961,-665,-836,-761,295,243,-1000,1000,-381,-1000,417,34,468,1000,-817,-500,728,-928,1000,213,-1000,-159,-1000,-1000,-1000,-436,-1000,1000,-974,-711,-382,-638,184,1000,1000,892,1000,-851,-462,440,-1000,666,-826,-419,-686,413,590,-998,181,-1000,-1000,-353,-109,-1000,-1000,326,-1000,-350,-1000,1000,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.set.ListOrderedSet$OrderedSetIterator", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "iterator():org.apache.commons.collections.OrderedIterator",
            new int[]{585,111,972,-565,128,906,349,547,210,-715,617,1000,-495,417,687,468,1000,-757,-178,690,378,1000,213,-1000,631,-912,-580,-816,612,-971,832,-1000,346,526,-209,334,1000,600,239,1000,-485,-1000,-108,-1000,422,1000,-467,12,86,698,-749,780,-1000,-1000,-355,307,-749,-945,178,-620,-127,-794,727,-758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.set.ListOrderedSet$OrderedSetIterator", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "iterator():org.apache.commons.collections.OrderedIterator",
            new int[]{-331,-1000,-761,-207,-368,-1000,-83,-140,160,-348,-225,-1000,1000,-714,-441,1000,-34,-137,-1000,-1000,-1000,251,-701,-673,-1000,-1000,132,-353,-781,1000,-523,506,-1000,669,-1000,1000,-732,898,356,-970,197,1000,1000,-1000,-519,-1000,-798,-1000,405,-1000,-1000,-1000,-612,-317,-386,-1000,1000,-149,51,309,-1000,1000,1000,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.set.ListOrderedSet", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "listOrderedSet(java.util.List):org.apache.commons.collections.set.ListOrderedSet",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.set.ListOrderedSet", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "listOrderedSet(java.util.Set):org.apache.commons.collections.set.ListOrderedSet",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.set.ListOrderedSet", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "listOrderedSet(java.util.Set,java.util.List):org.apache.commons.collections.set.ListOrderedSet",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "listOrderedSet(java.util.Set,java.util.List):org.apache.commons.collections.set.ListOrderedSet",
            new int[]{80,-523,847,-763,572,-209,359,-960,302,170,-447,551,-62,849,784,838,736,-688,137,-962,567,327,-50,659,835,826,-226,934,668,-64,425,-695,-155,147,182,-646,113,873,644,-759,-226,480,268,-897,735,-811,199,-253,547,-728,-368,-565,433,397,680,625,-278,-928,-561,91,-271,-229,834,24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "listOrderedSet(java.util.Set,java.util.List):org.apache.commons.collections.set.ListOrderedSet",
            new int[]{-68,-731,783,-798,65,-843,-690,497,563,-873,107,232,-316,338,51,-468,-61,-554,222,-628,847,-952,-560,-687,-924,20,-820,-60,474,466,-846,-44,646,664,306,160,-137,-313,-646,292,351,-854,581,-912,-698,917,129,788,-699,631,-299,808,-696,-783,291,-656,-230,843,307,220,-892,-749,302,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.String:aXRlbTE=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "remove(int):java.lang.Object",
            new int[]{538,-1000,594,429,-163,-1000,-437,740,156,-391,-938,-1000,-187,-789,-339,-69,580,-941,-456,-519,976,-787,-297,108,368,1000,1000,135,-386,-1000,1000,-37,-304,1000,731,502,1000,-288,850,-41,1000,945,380,-1000,383,-1000,-1000,-1000,-587,-546,-1000,895,-467,-194,-108,205,-264,1000,277,548,320,-1000,354,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "remove(int):java.lang.Object",
            new int[]{-941,-673,-216,-991,-900,-85,343,185,1000,471,329,1000,1000,-1000,-427,-191,-179,-1000,1000,-450,-469,1000,-269,-559,-1000,-1000,-431,-129,81,121,1000,962,-715,-940,-327,139,1000,-516,-453,352,1000,-511,-1000,-688,138,1000,1000,380,1000,-193,463,320,1000,967,-84,178,626,-1000,-738,-838,-306,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "remove(int):java.lang.Object",
            new int[]{536,-550,278,940,-713,-109,144,425,470,523,-597,-304,-121,-521,588,965,922,76,-692,-289,130,-446,190,-201,816,427,742,-714,600,-866,32,39,167,939,611,693,603,-815,990,761,894,860,156,-225,-191,-779,-892,-785,-768,-287,-805,695,-722,-182,-825,-639,-989,588,319,812,437,-357,-350,953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "remove(int):java.lang.Object",
            new int[]{-1000,-1000,-861,1000,10,372,-853,425,-617,95,-398,-1000,-324,-560,273,-1000,632,-246,1000,1000,945,326,287,1000,-676,4,-36,1000,1000,-209,55,1000,724,588,962,1000,1000,-986,-8,466,750,1000,1000,-242,-636,459,-1000,246,-55,186,654,792,310,-956,1000,-1000,1000,1000,961,800,-1000,962,-77,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "remove(java.lang.Object):boolean",
            new int[]{1000,560,169,515,25,400,877,692,130,1000,-1000,378,-87,-772,-171,-88,123,380,1000,1000,586,189,-144,-326,237,1000,248,135,-301,-1000,-1000,-1000,-344,146,137,55,-1000,-840,-64,357,-247,-165,-653,-631,1000,-1000,-118,-1000,-416,1000,18,469,-895,1000,-451,247,1000,162,117,-156,-791,-1000,580,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "remove(java.lang.Object):boolean",
            new int[]{1000,-491,129,817,402,51,-815,-1000,-818,-996,-396,553,1000,46,1000,558,1000,84,821,593,1000,421,-1000,1000,396,-374,-148,-989,-1000,-658,1000,1000,-898,-114,-629,700,-819,-363,-653,1000,-157,-584,-620,742,596,244,-1000,-1000,277,-1000,1000,-113,-1000,1000,1000,-64,1000,702,-1000,-431,-130,358,-568,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "remove(java.lang.Object):boolean",
            new int[]{866,984,834,1000,-658,-1000,968,1000,306,157,-915,1000,-368,-817,-273,-338,-507,-665,738,930,1000,209,1000,1000,-168,1000,35,-388,-875,-265,1000,-505,1000,-425,-189,-878,-1000,1000,-981,177,27,632,-1000,54,690,808,-281,-100,-844,-1000,-464,-861,305,784,-1000,1000,934,-61,-348,1000,-988,-605,626,-847}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "remove(java.lang.Object):boolean",
            new int[]{33,847,294,452,172,-1000,109,1000,1000,997,295,1000,-1000,-543,54,-282,-126,217,-652,1000,-397,401,1000,1000,480,217,-63,681,-469,-830,624,-1000,-73,-1000,-969,52,639,523,905,148,582,1000,-1000,174,1000,-684,116,-504,745,1000,-1000,-1000,1000,-400,1000,-182,-410,-621,-588,1000,-905,1000,-1000,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "remove(java.lang.Object):boolean",
            new int[]{382,-802,257,506,-992,-449,12,-1000,651,-996,-252,-1000,1000,-538,-341,47,609,1000,268,-323,-850,308,-993,1000,105,832,-1000,-427,-214,-208,34,-697,104,-160,557,-70,-680,839,-315,496,240,-803,440,-1000,356,-179,-1000,-367,-1000,-1000,1000,881,-1000,1000,-1000,-97,35,-421,182,-405,927,-397,948,247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "remove(java.lang.Object):boolean",
            new int[]{1000,-114,309,234,-89,-915,177,1000,596,1000,-288,1000,-1000,-923,-740,-76,-1000,1000,-51,1000,-160,189,1000,-1000,-1000,923,-81,271,-652,58,207,-1000,222,-657,-792,486,159,758,1000,-536,1000,1000,-1000,-670,1000,-1000,485,-1000,-697,1000,615,-887,276,338,1000,-1000,148,-1000,-926,1000,-578,-137,-1000,-619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "removeAll(java.util.Collection):boolean",
            new int[]{246,489,-63,920,-1000,576,-503,768,-927,156,438,63,13,1000,489,537,436,1000,1000,1000,-1000,1000,505,-737,-856,-1000,-469,-992,252,-1000,-1000,1000,-776,-1000,-318,208,652,1000,-872,952,-1000,1000,-170,335,541,-1000,7,803,433,-1000,-990,819,-156,839,1000,-972,-943,-1000,-1000,432,748,-1000,-281,25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "removeAll(java.util.Collection):boolean",
            new int[]{-732,1000,-146,-518,-818,502,-274,1000,1000,-677,-324,365,463,624,181,-302,-250,270,270,85,-970,-869,-484,682,917,41,893,192,975,-524,-673,961,-842,-1000,-1000,83,900,725,-932,84,491,498,1000,-967,636,1000,1000,1000,737,-1000,-108,1000,-105,-400,1000,381,-1000,-193,559,-1000,1000,-61,-176,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "removeAll(java.util.Collection):boolean",
            new int[]{420,1000,989,-1000,1000,-522,-397,-103,-44,924,-933,-451,378,852,369,-1000,1000,-354,-308,-1000,-731,-287,-206,1000,-1000,-1000,-360,141,64,898,202,-319,345,783,-1000,-396,-561,1000,-731,791,-327,232,289,-189,1000,1000,1000,-1000,-504,1000,36,686,1000,-206,-526,-535,713,-61,-1000,-335,-343,-428,-361,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "removeAll(java.util.Collection):boolean",
            new int[]{404,-253,-401,1000,-546,712,33,29,-1000,156,-235,443,-369,450,-761,-770,-1000,1000,-5,-602,163,-347,906,-366,1000,96,-230,-30,-21,-1000,-412,-612,1000,136,-106,-288,-1000,-712,-872,-643,729,57,-348,-681,-1000,601,-629,-726,-791,-129,837,-1000,160,303,-496,-527,568,317,687,-1000,-1000,1000,-960,-614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "removeAll(java.util.Collection):boolean",
            new int[]{265,172,-277,649,-853,11,-531,360,-91,400,673,621,776,1000,517,666,674,483,88,421,-567,558,159,-577,767,101,-331,-949,-105,-945,-632,557,-1000,-1000,595,-141,1000,350,-304,1000,-814,1000,673,-448,-571,-89,-291,824,23,-898,131,608,-1000,392,1000,-611,-1000,-88,306,467,310,-602,-1000,-798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "retainAll(java.util.Collection):boolean",
            new int[]{236,274,-226,1000,137,1000,-477,-986,-440,-986,521,-24,634,-112,-222,-384,217,853,774,-310,1000,566,400,-83,-393,483,-474,-85,-301,772,683,-400,119,520,853,-142,1000,549,938,839,-454,-953,-538,367,-193,-139,-447,223,670,-825,147,-484,-487,600,-669,1000,-389,798,1000,-1000,-328,-83,-756,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "retainAll(java.util.Collection):boolean",
            new int[]{836,-755,-606,-555,292,692,1000,-1000,-736,938,666,1000,425,352,-48,858,186,702,524,505,109,1000,-1000,1000,1000,-451,-463,274,342,-331,24,204,-742,-737,294,-551,590,-552,-263,999,-1000,-807,-786,910,365,-328,-421,-1000,899,-559,-785,-353,1000,-329,-318,270,-1000,-521,-793,-1000,-1000,821,911,-895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "retainAll(java.util.Collection):boolean",
            new int[]{647,-907,-226,-791,5,1000,657,-1000,-329,1000,648,5,917,198,-85,1000,-254,702,-659,-628,673,1000,-865,673,1000,-498,-1000,729,1000,-331,276,906,-910,-580,853,-1000,1000,-526,-340,269,-1000,-271,-1000,910,295,-1000,-576,223,115,-1000,-1000,130,1000,-329,-669,-917,-1000,-760,-913,-1000,-1000,1000,323,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "retainAll(java.util.Collection):boolean",
            new int[]{-756,843,-41,-27,-285,1000,-700,-930,482,-957,306,-96,838,480,195,689,265,1000,389,-696,989,-208,477,19,24,51,-754,25,189,-192,736,-1000,527,-1000,1000,-123,882,533,810,-178,212,-123,-740,259,-399,-123,-76,-813,162,-962,-945,-95,868,503,851,-20,-309,-1000,315,-1000,-326,769,-516,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "retainAll(java.util.Collection):boolean",
            new int[]{874,500,728,1000,1000,-37,27,144,784,-1000,108,-133,-67,-1000,38,-1000,-859,1000,924,244,-65,-528,769,-1000,-1000,-1000,-291,-589,-618,1000,1000,1000,650,1000,-312,451,-301,329,814,513,-854,-1000,-60,1000,-732,-56,-1000,-17,-177,1000,1000,-1000,-1000,150,-701,692,347,540,1000,461,1000,-205,-998,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:2:25:java.lang.String:aXRlbTM=:25:java.lang.String:aXRlbTA=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toArray():java.lang.Object[]",
            new int[]{437,400,618,376,-541,128,699,-724,-791,588,888,608,-1000,-1000,155,-279,41,998,22,1000,-1000,-267,-1000,534,758,818,-267,417,1000,385,297,1000,279,-234,-168,-388,531,1000,176,1000,-1000,-303,-262,1000,-1000,954,-21,-935,-259,-1000,560,-473,-349,212,-548,184,-286,-791,188,-318,-758,826,929,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:5:4:NULL:25:java.lang.String:aXRlbTQ=:25:java.lang.String:aXRlbTI=:25:java.lang.String:aXRlbTM=:25:java.lang.String:aXRlbTA=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toArray():java.lang.Object[]",
            new int[]{-1000,970,-296,786,-612,-993,443,-1000,299,471,-558,394,-1000,-1000,-251,520,-270,990,870,783,-283,1000,74,1000,-174,844,520,368,1000,428,592,1000,-401,-543,-48,371,-219,217,96,1000,-1000,-292,-277,318,-384,1000,-591,-397,679,-388,-431,623,-498,392,-556,-372,400,-476,143,123,-690,224,-1000,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:0", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toArray():java.lang.Object[]",
            new int[]{-243,-1000,-816,292,1000,1000,845,545,1000,-989,-1000,701,621,-2,186,-164,-833,-1000,801,-194,1000,994,1000,227,-831,-989,633,537,-387,-912,601,-748,-1000,848,588,1000,-473,-1000,995,1000,763,-1000,-385,-946,1000,-988,-485,-21,679,897,-1000,967,1000,-561,834,338,-877,-17,1000,230,208,555,-1000,406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:2:25:java.lang.String:aXRlbTM=:25:java.lang.String:aXRlbTE=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toArray():java.lang.Object[]",
            new int[]{-407,-985,473,70,-722,869,668,-361,812,51,-321,1000,-186,-583,408,26,-498,192,527,-602,-586,1000,524,667,-911,954,185,866,-880,1000,-588,-363,-807,162,-127,829,-935,-374,-112,-240,-400,-9,820,-261,163,1000,-843,-318,119,-502,686,-38,-1000,-162,347,-373,638,-553,432,438,-1000,-757,350,257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:2:29:java.lang.String:b2JqZWN0MA==:25:java.lang.String:aXRlbTE=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toArray():java.lang.Object[]",
            new int[]{-498,-739,729,352,302,545,522,947,781,-121,-1000,515,-581,-1000,181,-436,-933,-999,541,99,965,792,483,-459,-1000,-417,926,556,-940,-215,688,169,-1000,943,-1000,1000,-1000,-711,-545,530,-107,-453,289,-1000,1000,-581,-38,-92,1000,-15,-1000,798,926,-1000,492,591,-799,210,842,1000,-229,-304,-761,739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:3:25:java.lang.String:aXRlbTA=:25:java.lang.String:aXRlbTI=:25:java.lang.String:aXRlbTM=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toArray(java.lang.Object[]):java.lang.Object[]",
            new int[]{846,-240,573,686,207,-293,-824,-1000,646,-891,269,89,89,-208,183,-107,-518,478,625,-528,49,-286,-421,440,833,-290,536,-691,-302,-312,637,1000,-677,1000,-755,-173,-8,-98,-416,-666,-403,-550,-478,-1000,654,-148,39,-1000,580,341,286,101,-92,-146,-588,-564,-913,1000,-878,-199,979,-710,-902,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:2:4:NULL:29:java.lang.String:b2JqZWN0MQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toArray(java.lang.Object[]):java.lang.Object[]",
            new int[]{-213,1000,74,-15,672,-915,-434,-144,-242,876,45,-850,1000,-107,49,584,842,-214,-380,861,-740,-195,440,-624,-76,422,301,-146,-399,426,560,-639,89,-493,-582,348,-713,1000,-158,704,-69,-614,-1000,266,540,323,347,612,323,888,-421,-789,400,-310,-594,442,414,-1000,1000,458,738,-254,600,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:4:25:java.lang.String:aXRlbTQ=:29:java.lang.String:b2JqZWN0Mg==:4:NULL:29:java.lang.String:b2JqZWN0MQ==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toArray(java.lang.Object[]):java.lang.Object[]",
            new int[]{1000,-779,74,173,-161,954,-877,-956,1000,-1000,488,894,-57,-55,754,-241,-1000,693,1000,-69,361,-38,-623,1000,1000,97,675,-184,-511,-632,746,679,-969,1000,1000,-266,244,-568,-591,-872,-582,-685,-635,576,372,-454,-545,-1000,706,-236,463,67,235,-403,-148,-472,-1000,1000,-1000,-21,640,-254,-465,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:2:4:NULL:29:java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toArray(java.lang.Object[]):java.lang.Object[]",
            new int[]{22,-2,-676,144,-923,-73,298,1000,-252,-89,1000,-193,605,826,-536,1000,1000,-1000,305,884,-1000,1000,1000,548,339,-1000,-1000,118,-39,196,-1000,-292,-11,665,-159,753,-1000,-125,-184,147,920,-91,228,1000,-876,1000,-882,90,109,713,-1000,-1000,-982,664,-1000,337,-742,492,641,428,2,-971,0,398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.String:W2l0ZW0xLCBpdGVtMF0=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toString():java.lang.String",
            new int[]{-132,461,-496,-74,-948,124,-140,-942,-312,-149,536,-1000,821,45,-196,-77,-246,-635,-409,-55,149,-202,30,913,355,387,-207,319,839,-319,-1000,658,430,195,1000,-402,1000,304,439,-270,106,-66,-711,-892,-907,19,715,-651,1000,1000,93,-185,230,-219,1000,-1000,-499,573,224,-423,-904,-687,-186,-339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.String:W2l0ZW0yLCBpdGVtMV0=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toString():java.lang.String",
            new int[]{580,193,-747,353,-774,-713,1000,-384,-16,-75,167,452,-489,365,315,727,-1000,430,208,-273,1000,266,-1000,-385,-911,-169,220,805,85,-362,-1000,-316,817,83,-251,713,-579,1000,-247,869,554,-130,288,548,193,446,-1000,-858,-1000,-678,-1000,-185,1000,-1000,-158,323,-1000,1000,-1000,88,147,-309,664,268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.String:W10=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toString():java.lang.String",
            new int[]{1000,-348,-586,-873,-1000,-713,1000,960,-932,140,-49,234,758,1000,315,722,260,1000,72,596,1000,1000,462,-1000,-950,881,264,1000,85,107,340,-551,-306,-791,759,713,-343,1000,-180,1000,1000,364,872,974,1000,446,-381,-1000,-209,797,-1000,795,1000,-257,211,503,-1000,1000,-1000,1000,984,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.String:W2l0ZW0wLCBpdGVtMl0=", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toString():java.lang.String",
            new int[]{1000,844,622,-708,-894,-448,794,12,-834,-948,-80,792,-162,-313,953,1000,-806,550,-237,-1000,-414,1000,1000,-1000,-1000,1000,48,1000,-810,653,-573,-68,746,259,329,-843,-1000,-41,-817,1000,160,-16,58,332,562,1000,-861,385,-168,-600,-768,142,463,-1000,1000,571,-146,1000,-1000,915,-718,-1000,1000,67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.String:W29iamVjdDBd", DEReplay.run(
            "org.apache.commons.collections.set.ListOrderedSet", "org.apache.commons.collections.set.ListOrderedSet", "toString():java.lang.String",
            new int[]{1000,685,138,619,-894,-716,1000,62,-1000,117,-56,1000,-1000,-1000,406,805,679,178,377,-400,923,1000,-228,-1000,477,1000,-320,457,-1000,-84,119,161,-722,-393,-1000,51,-1000,368,338,429,596,-604,-1000,830,1000,-387,-1000,-655,-1000,-720,-1000,281,229,-9,10,928,-402,673,46,1000,512,-1000,927,489}));
    }
}
