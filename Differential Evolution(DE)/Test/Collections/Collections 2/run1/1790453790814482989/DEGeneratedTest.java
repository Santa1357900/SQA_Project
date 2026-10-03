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
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "addProperty(java.lang.String,java.lang.Object):void",
            new int[]{-664,-657,-176,-309,548,631,1000,-423,-355,-693,-66,-1000,339,1000,-1000,964,-1000,940,-1000,-415,-307,-1000,-697,-231,334,49,-58,735,401,1000,323,-849,776,576,-910,290,-452,-231,435,765,1000,-423,-14,1000,-325,689,-235,337,145,1000,619,-412,-1000,-1000,-228,613,-1000,289,974,-61,-88,1000,-1000,97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{-297,417,413,1000,-543,314,-1000,-53,-397,-474,-361,-82,481,-406,933,140,-968,887,156,744,761,-682,1000,394,-1000,743,1000,133,-811,-236,-999,788,1000,-194,-600,829,1000,330,-916,353,-1000,371,1000,546,-664,-549,-1000,192,147,1000,-948,1000,-484,-353,865,-674,-185,-130,-313,1000,629,1000,-887,-560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{63,-720,49,994,777,151,468,-411,-1000,-743,90,738,-799,864,713,-814,90,-949,-179,-90,361,1000,-363,-1000,1000,94,259,54,392,376,877,-1000,983,1000,-1000,577,-785,663,997,960,-1000,270,-282,-516,1000,-98,884,-835,136,-575,818,-1000,777,-500,-1000,-856,-54,619,18,312,-428,281,-956,136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{111,-615,269,468,867,660,1000,700,-467,-538,623,143,-1000,442,721,-613,-349,-949,-1000,-857,-946,-348,91,-598,1000,-1000,720,297,86,-300,1000,-400,-619,598,759,-515,-980,377,579,31,-845,-422,335,-972,-547,217,496,101,-149,348,706,-813,887,-1000,1000,453,400,925,-241,344,-435,151,366,-11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.ExtendedProperties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "convertProperties(java.util.Properties):org.apache.commons.collections.ExtendedProperties",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "display():void",
            new int[]{927,48,97,1000,-158,368,-1000,665,-997,347,1000,1000,-400,670,1000,-1000,-16,1000,-1000,1000,540,-536,-676,-1000,1000,-1000,-279,589,1000,-923,1000,-1000,1000,753,-1000,-1000,394,1000,-1000,-517,-216,-1000,1000,27,-151,-359,1000,-393,-1000,-617,-1000,-1000,804,-530,69,-1000,343,-1000,1000,-1000,-912,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String):boolean",
            new int[]{224,87,-333,-848,-239,662,-1000,-13,-1000,1000,164,289,1000,-254,887,92,294,-264,334,622,526,1000,-707,-211,1000,-1000,-1000,625,-935,111,-1000,-782,534,1000,-830,399,1000,-488,1000,924,-532,324,897,-714,330,137,906,-691,-54,1000,-253,-914,576,14,102,-814,-435,-834,-746,-575,993,630,514,482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{-413,-873,3,-184,-1000,-148,475,-625,818,-54,882,805,-969,-430,-893,-1000,-1000,-429,-1000,-915,1000,-714,1000,1000,-41,748,-1000,-947,348,-740,851,163,-754,-202,1000,-640,-339,741,1000,1000,-185,-204,1000,216,131,569,1000,-1000,-450,1000,469,561,-200,1000,-391,-660,-370,1000,524,-1000,57,-799,1000,524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{331,-999,348,245,-869,233,494,-590,127,-525,238,-60,155,442,-753,-966,-339,-824,-256,-552,680,-122,713,976,-267,627,-827,-538,788,630,-520,331,915,-753,32,-640,-942,455,398,985,384,644,900,-174,-873,663,989,324,-478,921,352,372,-543,981,-339,-349,816,576,68,26,127,-724,145,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{-514,891,203,360,1000,-359,-1000,28,875,1000,-943,1000,483,1000,-1000,1000,-204,-1000,-1000,-332,-1000,-1000,1000,-492,1000,-15,1000,1000,1000,-1000,-1000,239,-1000,-475,1000,1000,181,-1000,-172,1000,-1000,1000,-1000,653,1000,-100,-477,1000,1000,-1000,951,983,-853,387,-1000,-1000,-1000,1000,-1000,1000,-660,830,-402,-299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{-488,-849,84,486,927,1000,-1000,125,437,1000,-712,958,619,-199,-643,1000,-828,-1000,-1000,116,-845,-675,1000,-536,1000,133,1000,740,874,-648,-1000,-81,-1000,-255,1000,1000,628,-1000,-363,1000,-1000,1000,-1000,-1000,731,-213,-517,1000,817,-1000,945,1000,-493,45,-1000,-1000,-1000,1000,-1000,1000,-575,760,-271,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{827,-939,-991,-779,825,63,768,749,892,-14,334,-826,-944,506,970,160,906,-685,420,464,356,61,495,608,944,295,-264,667,-50,929,-444,485,948,-554,-956,-20,-883,-286,711,479,-383,-399,851,42,643,-178,-110,587,-603,-578,152,304,-863,-847,783,-689,725,-375,230,-186,627,545,-359,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{182,-513,-672,940,912,959,656,-461,485,442,-53,718,-967,575,-719,682,-706,-890,-832,-100,-456,-916,-330,657,-434,-889,525,-60,-827,790,-658,-724,-685,58,-416,-629,-832,419,608,-93,769,418,326,-293,-276,-515,-803,-386,339,-700,203,-93,82,240,-771,-169,-247,107,-170,940,-87,-628,-246,320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{414,-513,127,-727,-1000,-858,-1000,-877,-1000,442,84,-420,1000,-1000,8,-411,1000,-1000,1000,283,104,1000,963,-1000,-5,-414,-1000,605,-827,201,1000,1000,-459,-292,1000,-629,283,748,608,-420,769,947,538,-1000,-785,-1000,537,-415,-78,922,391,-1000,520,649,532,-1000,1000,-257,-490,-1000,-87,1000,-843,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTI0", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{-1000,681,-892,660,1000,-34,1000,-1000,-1000,-541,-631,1000,1000,-1000,1000,-402,1000,846,744,-1000,1000,841,-150,-172,902,1000,-313,1000,-1000,866,-1000,-1000,1000,1000,886,972,1000,-567,1000,744,875,1000,-499,-672,1000,806,340,1000,-203,1000,-125,460,-904,1000,-1000,1000,395,-269,-537,336,1000,1000,25,-841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{-210,63,254,-363,-1000,-1000,485,-595,29,-1000,85,436,-790,-341,-453,916,127,-1000,399,-746,-13,1000,-1000,-1000,2,-603,-1000,1000,-1000,39,932,98,-113,-1000,-1000,1000,-267,278,-730,-423,-1000,585,-1000,-614,706,917,-1000,-804,992,933,-82,521,959,480,-1000,-867,1000,-801,1000,-703,535,730,-786,846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{953,405,-916,178,-1000,857,-1000,1000,1000,388,1000,-481,-1000,1000,-1000,222,-1000,-1000,-644,1000,-1000,-64,1000,-630,-1000,-1000,288,-1000,501,-453,1000,1000,-1000,-1000,-1000,767,-334,830,-1000,-1000,-1000,447,-810,1000,269,-7,-980,-691,1000,-359,-352,99,-528,-470,1000,-1000,365,-536,586,-1000,469,-473,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{227,753,-786,257,-888,1000,81,-1000,952,368,872,58,-482,1000,0,-277,-17,1000,-1000,817,1000,-1000,998,-254,912,0,357,830,-36,365,-1000,0,-183,898,719,-799,550,0,520,-579,18,-1000,1000,810,1000,436,344,419,0,255,386,657,891,-1000,-592,-1000,-357,-714,-121,-553,0,41,928,542}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{438,756,381,545,-946,1000,-759,-1000,605,567,431,335,-903,-1000,-131,727,570,1000,-1000,-1000,1000,-683,970,22,987,110,535,137,81,-998,-688,-29,-1000,722,-552,-587,642,-1000,724,767,-1000,-1000,895,805,1000,307,-526,1000,520,-1000,69,1000,65,-1000,-789,134,-275,-1000,-1000,1000,163,-1000,594,-240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String):double",
            new int[]{-331,-588,-973,1000,997,-1000,149,100,287,-345,-1000,-1000,-1000,1000,-300,-1000,1000,-251,386,-814,-899,733,-1000,1000,187,872,1000,1000,-807,399,835,-181,48,-181,-1000,-1000,626,1000,1000,-978,-1000,-887,-1000,-1000,472,-30,1000,-1000,-1000,689,1000,607,1000,922,-209,-202,337,192,-93,845,-1000,-1000,-958,-824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEwLjU=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{-692,-921,504,-704,452,667,1000,-829,75,537,-572,515,49,-1000,-849,-926,-1000,-1000,1000,622,1000,343,-1000,1000,564,-557,1000,-105,-866,-1000,-245,-143,-393,-149,-902,120,-928,1000,-817,1000,10,63,1000,-767,-586,-56,-1000,14,717,-1000,-21,-1000,-310,329,1000,251,631,1000,1000,1000,469,761,-687,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{716,-189,486,-319,-1000,1000,1000,-1000,736,-917,-1000,-881,-1000,-1000,-1,-1000,-1000,-497,1000,970,948,98,-1000,1000,-121,-1000,-893,353,-709,-848,-859,-1000,257,-14,-629,-419,-733,462,-583,847,1000,-1000,1000,-179,-546,1000,-1000,-855,-1000,-1000,-204,-549,266,288,1000,238,900,1000,148,1000,704,762,957,-434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{878,135,557,857,-1000,1000,51,-233,764,1000,958,106,92,287,-1000,65,167,-1000,-1000,-1000,-217,96,664,-946,-88,-503,-964,-465,-499,97,729,1000,90,-62,474,-1000,-585,-723,952,-430,1000,-721,719,556,-202,811,464,-793,-1000,673,123,-340,-530,-942,-610,746,305,-156,-804,1000,192,-191,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{442,555,778,275,-1000,978,102,-599,304,483,147,936,471,-18,-463,1000,-795,484,348,-466,587,1000,-1000,49,-534,367,-1000,-339,74,217,1000,585,-102,202,-1000,-323,-1000,538,22,-573,648,-1000,-491,-38,-652,-766,-362,297,100,1000,1000,1000,-1000,1000,926,620,632,797,608,1000,-1000,877,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{468,609,-72,818,26,-886,797,1000,371,926,661,-163,-895,-938,363,77,302,-324,217,325,-687,-691,-1000,53,355,38,820,224,-1000,364,-605,-642,-342,-63,-223,-1000,1000,-987,-421,706,-613,1000,563,-812,-718,334,-426,-799,524,-1000,265,-509,-207,520,1000,-301,-191,-1,-400,850,843,-237,960,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{879,-81,794,179,1000,-60,129,142,927,609,-214,-437,-103,-515,1000,-653,-130,596,527,-421,515,397,949,785,-432,470,-575,276,-23,740,1000,422,1000,1000,367,-945,816,-329,506,-855,405,-497,1000,-1000,-1000,-647,-211,48,439,-474,-20,728,-870,1000,130,-1000,979,-648,627,1000,-1000,-709,-134,650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{-1000,924,-176,-815,1000,-376,-452,-1000,-87,743,1000,-739,-503,-550,-868,-944,1000,-182,-332,-206,-1000,-472,-1000,-425,-305,1000,659,37,-151,-330,-770,-411,999,276,-451,269,-377,366,-307,-873,-876,-128,266,176,33,-1000,342,653,-470,-1000,-72,-408,687,1000,533,-86,-611,-632,-481,-564,-798,-532,115,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{-1000,-993,-672,-566,807,405,-113,911,1000,-489,85,1000,-224,1000,902,-998,-247,-1000,612,-7,1000,-1000,-281,-446,648,-861,-1000,-149,629,1000,886,-1000,-450,-64,-1000,-923,1000,-1000,840,1000,1000,321,-744,-330,756,277,-1000,-1000,884,517,-908,-1000,-1000,-1000,-171,-930,-1000,1000,-1000,810,-247,1000,-993,206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{-1000,798,329,206,-1000,-1000,-962,-450,-6,-490,-1000,-679,-538,-1000,-719,5,982,-178,-418,618,-991,1000,45,-142,520,-97,1000,-1000,1000,-1000,857,163,518,111,1000,-579,-1000,-924,-193,1000,1000,-462,309,1000,-374,644,-813,1000,-219,1000,-276,248,1000,173,671,155,-1000,73,-385,176,1000,613,-704,434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{-727,234,-272,-325,-1000,675,608,-781,200,381,-746,-666,-464,744,-152,530,-142,324,-56,215,1000,-116,587,421,-134,338,1000,987,-1000,-169,-113,-608,370,1000,-472,181,336,39,373,120,1000,755,-177,747,-856,36,-1000,1000,-324,568,-609,435,-945,-1000,-908,-308,-391,-245,740,-30,1000,62,-287,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{-735,-84,-946,345,-1000,595,612,629,348,1000,980,130,280,-14,-789,285,111,-90,-1000,-88,322,-1000,89,1,-1000,150,-62,1000,-1000,913,1000,483,1000,-502,559,-526,453,-1000,649,658,189,599,-650,-982,-1000,-92,-360,789,394,-354,400,377,-45,-1000,-930,292,694,45,-1000,1000,223,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{-81,114,-847,172,-916,-518,119,-1000,768,-436,-804,1000,625,208,-256,-828,497,621,214,1000,-786,1000,-699,1000,-146,-1000,-495,-279,807,-58,-513,1000,-361,688,717,-1000,-768,-1000,1000,-147,-38,805,-543,-500,1000,-1000,1000,1000,-1000,-824,819,-270,803,136,416,727,227,143,-1000,1000,-585,898,625,-967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{1000,219,908,479,-564,1000,-987,1000,-1000,239,804,3,1000,171,-812,-228,910,-457,-600,944,-859,-494,-1000,1000,-658,270,306,1000,-672,687,-1000,708,544,1000,695,-739,586,-849,71,-668,-961,1000,-1000,407,637,-1000,1000,-254,-1000,-294,1000,817,889,1000,237,-134,-272,1000,87,162,-1000,-259,627,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{-305,-237,-451,-294,-582,665,-762,163,414,-1000,458,-160,534,34,-653,-344,1000,-99,-200,1000,-14,164,-1000,274,-686,-100,17,792,329,643,-426,39,544,608,429,-739,1000,-1000,158,-534,-1000,923,-810,389,-763,345,1000,-408,-1000,319,401,505,1000,103,955,406,358,999,-363,380,-817,520,936,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{-405,747,428,1000,-389,-958,-495,0,101,780,1000,-454,-536,-118,37,-1000,-75,1000,257,-494,-308,-674,-1000,-568,-516,-484,-227,1000,-429,0,202,-310,1000,626,-665,357,1000,-437,-1000,904,0,-665,44,-1000,-463,1000,-43,1000,-330,0,-1000,0,646,-676,1000,0,-445,922,463,0,-732,-518,1000,143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{1000,795,278,15,-1000,541,-722,-995,1000,1000,1000,1000,217,695,-1000,-943,1000,-1000,-1000,-1000,1000,-468,1000,-1000,201,-767,408,1000,-618,987,-286,490,1000,-1000,-799,1000,-1000,1000,-1000,-1000,-1000,-1000,809,-514,-752,-1000,1000,-19,-833,-1000,-1000,-1000,-664,-211,537,-1000,955,424,1000,929,-1000,-1000,158,-599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTEwMDA=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{-1000,-411,444,262,-398,419,-304,887,-351,-537,-115,1000,1000,-37,-538,-1000,78,486,614,1000,-846,204,-1000,-642,-1000,1000,1000,-391,-220,-919,-1000,-1000,-433,228,1000,-1000,-604,420,-89,-173,-1000,1000,131,-259,-401,566,-877,930,-579,700,566,-889,-544,627,244,-1000,-970,-639,-630,-34,-343,-1000,-48,414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{-1000,-384,-26,-320,-1000,-429,392,-1000,-715,1000,-779,1000,400,-97,682,317,-112,-15,148,-44,-120,1000,-1000,-934,-956,1000,1000,-271,-816,-1000,-1000,-1000,-117,-220,1000,-1000,86,385,-89,-254,-258,1000,131,-259,444,814,-247,1000,181,700,-347,113,305,68,360,-400,-1000,1000,412,113,-1000,1000,-149,310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,java.lang.Integer):java.lang.Integer",
            new int[]{402,-705,314,671,-920,-632,182,1000,328,408,451,1000,-150,429,1000,-537,-222,877,1000,1000,-235,750,251,-360,12,-131,662,406,162,-162,164,-723,-1000,464,780,465,-162,-1000,-849,1000,550,-653,-418,20,-1000,-229,344,419,1000,1000,-417,1000,1000,1000,-808,485,-1000,-789,-1000,419,492,979,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,java.lang.Integer):java.lang.Integer",
            new int[]{-675,342,924,-564,-1000,502,768,-1000,-215,-903,-1000,-102,-456,1000,1000,1000,1000,-7,1000,-1000,171,348,-998,1000,-403,-678,-36,-917,-982,-526,-485,-224,-400,-89,-1000,-1000,-215,640,616,199,-215,10,848,-1000,1000,686,-403,-1000,1000,820,599,-296,-131,546,273,-431,96,-37,-530,-51,571,1000,-417,642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys():java.util.Iterator",
            new int[]{-1000,-678,-541,-338,-646,899,-714,300,-777,798,33,788,309,-565,-77,314,-362,-650,1000,982,-196,-11,884,664,247,42,222,163,-194,-167,-1000,-158,830,-160,85,717,-175,-809,-31,634,22,799,821,-1000,1000,-1000,383,507,-694,-1000,116,582,-248,65,-466,376,-29,-531,-793,958,233,-826,834,-423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{-495,-33,629,774,-852,-1000,-277,1000,672,98,-128,1000,107,-844,225,-50,-503,-771,1000,-233,-191,-1000,-218,-277,447,-304,747,384,310,32,851,782,-290,48,830,-304,256,-246,361,-429,857,555,-443,-697,475,394,-920,-390,1000,229,393,977,593,809,-258,-977,1000,-412,742,-1000,-555,-436,-876,598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{-495,-33,-637,-635,-852,-23,-917,1000,622,103,687,942,-930,-1000,-312,-50,701,-901,479,-786,-1000,-1000,-1000,-772,728,-72,-400,419,1000,-511,624,470,-687,-331,1000,687,-750,-246,361,243,652,836,-443,-809,22,-35,504,10,235,-781,46,1000,617,809,307,-1000,1000,-726,1000,-629,-289,-337,-876,-236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String):java.util.List",
            new int[]{-495,321,-422,-519,896,817,265,-402,34,-456,-232,-102,-20,371,-905,933,-317,-586,-106,103,-340,-775,328,447,-192,160,-363,8,750,146,682,491,-355,452,-932,-682,985,-53,-229,110,531,-178,-317,-871,236,761,900,-700,-194,-822,-815,-981,339,248,624,181,-603,-772,-840,596,819,814,452,269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{-1000,-261,-782,-1000,-248,-849,1000,-455,15,-174,1000,995,609,705,580,635,-965,-559,-353,-1000,-1000,316,-1000,-748,-1000,373,747,601,-353,1000,539,934,203,-97,-383,-263,839,-516,954,1000,-680,104,-325,-500,625,-688,-1000,1000,749,-228,-736,132,-653,-311,-1000,500,872,-713,475,-149,186,1000,991,327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{1000,-753,-621,-119,147,-1000,-1000,-675,274,-369,718,-1000,-2,-85,-802,-891,-284,125,-1000,-24,-1000,521,-279,649,-353,1000,1000,222,1000,134,1000,775,118,-797,-1000,-571,-565,-331,876,427,1000,1000,-661,-839,-377,801,388,804,327,-20,-156,-295,-96,-507,-1000,1000,1000,-718,1000,40,-710,-17,45,-415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{476,-693,124,840,-1000,1000,281,1000,1000,-8,-200,-189,-1000,997,281,-1000,-757,1000,-460,-805,851,1000,-1000,872,1000,788,-212,44,865,294,1000,-769,645,-234,-1000,358,1000,928,945,-888,294,1000,255,-329,-253,1000,507,57,-592,174,-1000,571,-849,-363,1000,-338,1000,1000,403,576,383,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{1000,213,-132,1000,819,-912,-497,-14,-101,783,-1000,298,894,-484,-375,240,1000,-238,246,-994,1000,1000,-400,-414,-972,-1000,603,-221,-108,-426,107,528,985,585,495,408,109,-423,102,1000,-39,-463,-730,175,-1000,193,-143,-222,46,-527,-956,154,761,367,-522,451,-1000,-72,1000,-34,554,558,768,275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{-832,-819,322,1000,887,1000,280,665,-408,659,862,31,148,615,872,-228,-410,-1000,418,1000,245,-738,315,-1000,-423,416,-617,-995,8,1000,501,977,-1000,-25,-396,701,582,719,1000,-677,174,-745,-1000,-686,527,-235,-692,-776,-993,-201,-362,1000,-224,-1000,-907,-983,-338,-893,589,-626,418,778,581,914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{-752,381,-947,488,-543,1000,-287,1000,-1000,720,349,-823,888,-1000,1000,660,-51,-693,232,1000,-883,120,-507,664,557,1000,156,-1000,995,622,-341,1000,338,63,-1000,275,217,-139,152,-619,1000,-910,-1000,-1000,1000,930,1000,25,616,-146,-1000,264,-1000,-596,925,-1000,-1000,-1000,-405,-62,1000,349,1000,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{341,96,-196,-169,776,369,709,-947,904,-476,87,-589,512,-179,762,-764,674,504,707,-782,-301,-964,785,-115,-207,-589,995,711,-326,-146,-104,377,6,448,-314,-477,-87,-192,592,109,-608,574,299,1000,421,-928,-212,622,-322,667,-42,1000,-562,-200,-593,810,-136,-525,-759,810,286,-612,667,232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{-237,-120,-258,-866,-56,1000,202,-801,84,129,-1000,-395,110,42,-31,184,155,1000,-760,-660,-920,-660,1000,715,860,-287,-543,-1000,166,1000,993,746,-1000,834,264,422,-1000,1000,823,-51,186,122,488,306,517,-798,-1000,461,-308,804,-804,-61,-849,-820,-530,963,-1000,-901,-83,993,920,849,1000,611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{-844,21,-227,-193,-404,-745,1000,1000,1000,134,182,-1000,-1000,1000,785,766,327,625,1000,207,1000,1000,-842,-38,-940,-1000,-982,416,-1000,80,1000,1000,-617,273,-163,-42,-1000,-166,-495,79,-491,-687,-11,-1000,1000,980,-694,-1000,-58,1000,-173,108,-1000,-931,656,1000,692,185,874,1000,440,-1000,20,-979}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{366,-741,348,-302,3,-1000,316,362,-1000,1000,-235,-1000,-687,-143,-972,469,-495,-260,691,-891,814,-1000,-118,1000,87,509,186,144,140,814,1000,-221,-324,987,-953,-952,-199,-90,367,306,-1000,-1000,1000,-816,-84,744,-1000,-725,-1000,-411,-439,-667,-1000,629,-449,-85,363,-426,-496,432,253,138,767,238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String,java.util.Properties):java.util.Properties",
            new int[]{1000,942,-906,8,1000,-1000,1000,130,-115,1000,-589,-579,-1000,101,-1000,-1000,-1000,-1000,1000,-1000,957,4,-1000,1000,-859,668,-1000,-1000,-309,1000,412,1000,1000,-77,856,-199,-1000,-540,-378,797,81,39,-259,1000,-1000,746,1000,-1000,1000,1000,1000,-1000,-1000,947,895,1000,-1000,-1000,1000,-1000,-875,-745,-1000,-664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String,java.util.Properties):java.util.Properties",
            new int[]{1000,-504,-471,8,-170,-35,1000,71,432,433,-888,-804,-738,181,1000,-788,-112,-672,-212,-1000,785,-1000,-1000,-164,1000,4,542,1000,-973,121,84,-945,-131,-982,1000,102,870,-577,-16,393,-163,-145,1000,-551,-543,357,-625,1000,-398,624,-6,-353,143,-1000,205,1000,1000,-1000,89,-337,1000,-440,794,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperty(java.lang.String):java.lang.Object",
            new int[]{367,252,-243,952,379,524,-238,-29,323,-366,1000,-235,-142,-317,660,-986,-1000,412,-50,191,-623,-689,1000,1000,-216,330,-1000,-1000,1000,696,1000,-484,-962,-955,728,-584,-914,235,-1000,-1000,-442,959,-677,-1000,-246,736,995,1000,386,883,662,751,-1000,-1000,-1000,314,-348,-281,-1000,1000,1000,144,1000,337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String):short",
            new int[]{867,591,198,-463,-686,-263,-88,-995,154,-620,209,-588,662,381,467,29,256,-323,105,53,-1000,-1000,-179,821,1000,-845,802,-132,1000,771,-99,-272,299,-102,-756,-163,112,-924,-664,-746,-751,-104,585,-441,-706,-1000,755,435,-1000,-1000,-711,-805,773,179,-559,-746,-186,-895,-423,-13,39,851,-101,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{1000,-108,-357,886,-800,-966,-956,905,1000,-489,-1000,1000,-1000,969,1000,-1000,-427,-1000,-311,1000,-1000,1000,891,1000,1000,-999,1000,1000,-760,1000,364,-779,164,171,-1000,523,-1000,-190,-563,-207,-1000,1000,-526,684,-351,-590,-241,119,2,-1000,-1000,-191,237,-832,-625,1000,785,1000,-1000,-226,-668,-1000,691,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{-1000,-225,-586,-835,1000,1000,1000,-1000,1000,694,1000,-825,1000,403,242,1000,566,1000,396,-1000,-1000,-1000,-1000,-1000,1000,1000,-96,-610,657,-1000,799,1000,1000,807,1000,-1000,-692,-59,5,-106,-667,-1000,-604,-56,273,1000,-199,1000,-949,783,1000,-1000,1000,-497,-99,-1000,944,-115,907,-1000,-526,-823,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{107,243,209,-506,-534,-401,-734,-67,-229,673,601,231,350,-1000,1000,1000,-1000,-156,6,-113,122,-567,-1000,0,-406,1000,-429,1000,-323,-714,-343,-1000,516,-138,-11,-626,1000,-549,205,-132,897,860,-151,1000,120,146,-394,-1000,104,-272,-500,-140,133,1000,936,-564,400,427,-5,-435,-183,457,-71,781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{1000,-390,564,134,1000,-1000,-895,1000,-610,1000,-1000,441,1000,439,1000,1000,-157,1000,-419,1000,1000,1000,-1000,-1000,1000,1000,1000,-1000,-1000,269,1000,1000,1000,-1000,631,1000,1000,-1000,733,-16,1000,-1000,1000,-1000,1000,265,-1000,1000,-1000,711,-65,-243,123,1000,346,873,-1000,-1000,-239,-1000,-493,-1000,-1000,-969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{-1000,639,564,533,555,-1000,357,391,-911,1000,-963,505,-1000,789,1000,834,-157,957,-1000,869,-1000,-970,-126,39,-626,1000,727,826,-288,1000,-72,-1000,-158,-939,702,413,489,304,733,548,-319,-220,1000,-259,-705,668,-32,349,-794,-81,-65,-537,123,-643,1000,811,-460,766,154,-773,-818,250,-672,-300}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.String:KzB4ODAwMDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{-1000,621,598,1000,138,897,-159,-799,523,887,638,-542,40,-475,-1000,-585,68,479,668,1000,159,-194,-405,-350,-770,-275,-703,-177,746,3,904,1000,-1000,198,-1000,-654,1000,-1000,1000,-68,363,211,860,-407,-218,766,-873,-378,-480,17,-253,223,571,-237,-1000,566,-167,590,-1000,-1000,471,-1000,910,618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0NA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{-1000,-294,86,863,1000,1000,-1000,-1000,1000,-526,1000,306,1000,-1000,-532,-1000,-872,1000,952,-179,1000,-616,-443,1000,1000,-1000,-1000,-427,1000,712,374,-238,467,-284,733,-683,1000,-1000,1000,291,-35,567,62,-10,1000,909,717,1000,864,1000,-1000,-1000,166,-116,-209,832,362,-63,-1000,-766,393,-873,236,181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{-692,615,559,1000,-426,-503,-1000,-228,417,640,1000,-542,394,-475,-1000,-393,-428,1000,371,1000,761,-430,-780,247,-1000,-1000,350,-761,1000,1000,-16,213,-605,-656,-358,-458,320,-1000,1000,227,-26,-671,1000,-1000,-294,262,-1000,-378,-424,-211,417,-553,1000,830,-651,-89,-83,454,-1000,-1000,937,-391,-354,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:0", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getStringArray(java.lang.String):java.lang.String[]",
            new int[]{-436,-693,194,-982,-1000,678,64,48,-1000,181,-151,1000,-873,-972,836,1000,1000,1000,-68,1000,1000,242,-860,1000,1000,983,1000,1000,269,1000,-1000,-1000,-20,-1000,-1000,-428,1000,1000,-303,-1000,1000,-186,487,1000,1000,-858,-35,-312,587,406,978,-444,-1000,-230,-89,-665,990,-964,1000,1000,684,-1000,-719,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{-914,45,-413,-577,1000,-93,650,-1000,604,70,576,-1000,634,-478,1000,-242,949,-813,-358,774,-1000,1000,-1000,372,-1000,-1000,-355,-40,-189,-657,1000,607,796,716,400,450,-655,-861,265,-900,-1000,428,-96,10,-382,-1000,-485,942,247,1000,-715,-277,-699,985,-27,-966,-408,-268,-685,-784,-1000,371,-604,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{-933,-81,-241,-791,1000,-205,835,-1000,-42,-930,660,-1000,1000,379,1000,-1000,851,-637,-771,-819,-1000,1000,-788,-439,-1000,-690,144,847,1000,-264,1000,-531,1000,496,-637,696,-163,-440,-631,-594,559,1000,-258,1000,-400,-691,122,314,531,-1000,1000,-826,-1000,1000,593,332,405,-1000,-1000,329,-1000,507,765,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{258,-588,109,1000,917,444,625,138,-427,-942,-899,-1000,-598,-746,643,1000,859,612,898,281,-1000,1000,54,923,-1000,-1000,901,-1000,-73,1000,1000,-1000,-870,1000,11,340,-696,1000,-271,357,1000,-1000,-1000,174,1000,-1000,-1000,1000,-1000,278,-591,-492,67,797,87,199,473,123,-547,1000,-785,-293,1000,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{-197,-180,-221,743,-1000,311,1000,536,-671,356,-363,-916,79,106,1000,32,-47,1000,11,1000,-854,531,-567,-1000,1000,760,-1000,1000,484,-773,-77,-391,-1000,-612,1000,-1000,683,259,-77,-71,994,-1000,-187,-1000,86,359,1000,-624,205,-192,-545,-488,1000,-876,559,-1000,-1000,-935,-441,33,814,-341,-1000,100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "isInitialized():boolean",
            new int[]{268,771,113,-31,477,1000,-888,-1000,779,-662,-364,799,1000,852,-687,1000,-1000,778,-30,811,692,-216,-1000,-809,-723,1000,1000,1000,191,177,216,150,231,644,-1000,-1000,-838,-1000,704,13,-404,510,-61,-857,791,-437,-1000,268,845,245,501,-396,-1000,790,-1000,-795,1000,938,-1000,-1000,648,1000,-658,288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "isInitialized():boolean",
            new int[]{110,339,-356,425,785,-974,978,142,-139,-529,171,458,-928,35,699,-617,370,611,-8,-658,349,-637,702,-553,462,-652,-722,-457,-750,403,-81,-361,-905,864,707,209,-360,679,-270,-509,961,351,347,-593,-211,704,415,6,-327,994,234,-551,807,-916,262,460,-532,-494,222,896,465,-894,150,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream):void",
            new int[]{-61,-333,852,-794,-70,812,239,-613,-518,93,933,-638,914,-434,-639,334,254,-285,-566,-982,-876,667,-822,579,-6,990,778,553,-262,725,-996,-435,-92,147,664,-184,553,-168,-775,-125,-469,-937,108,128,-731,352,-759,-611,-293,-113,460,-340,-244,-885,55,764,46,-664,-624,-285,918,-834,752,-967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{-887,360,-181,1000,58,263,649,-715,-154,289,-850,-687,211,1000,1000,1000,-775,-910,-32,-296,-65,-349,919,360,98,929,-940,1000,1000,-484,312,658,-354,-981,-1000,1000,291,969,-665,218,1000,1000,516,-743,497,1000,-156,-1000,-1000,-172,109,972,1000,552,221,1000,236,330,25,-674,77,415,1000,-284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{-636,-513,-547,1000,-823,834,-598,412,-1000,1000,1000,438,-80,878,170,1000,-1000,560,-379,-1000,1000,-181,995,-287,42,960,94,259,1000,-403,1000,271,185,-1000,-219,1000,-756,31,64,-1000,703,-686,-141,1000,1000,1000,-52,-1000,-1000,-1000,860,1000,701,859,637,1000,-329,-1000,427,-151,-412,1000,1000,197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "save(java.io.OutputStream,java.lang.String):void",
            new int[]{-333,408,-938,-452,762,-181,727,-925,881,-887,39,-367,202,152,738,833,815,463,210,-80,-520,-753,-675,-47,-977,935,-943,25,766,-375,-953,-514,183,918,-121,-660,-110,-628,518,742,25,74,-916,828,87,-303,951,-14,392,-212,-992,123,-561,-967,849,479,-309,-246,643,113,530,-993,-510,292}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("VOID|getInclude=java.lang.String:IDY4MiA=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setInclude(java.lang.String):void",
            new int[]{-91,462,-539,-61,-625,-535,583,-1000,695,547,-406,682,1000,271,-899,622,233,-1000,-566,619,-291,290,169,-1000,755,-1000,118,861,-855,-676,-294,-230,-1000,-808,-195,516,-407,542,-1000,-1000,-860,-1000,375,153,565,445,-1000,-1000,-222,709,273,-514,-329,-562,621,173,-112,1000,-565,271,-243,65,-1000,542}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setProperty(java.lang.String,java.lang.Object):void",
            new int[]{-121,-24,-551,-205,701,-474,741,-749,-30,950,-1,244,251,-846,720,-136,-861,709,-444,673,-700,-698,-124,89,249,-48,-804,410,-660,-646,-540,-941,844,-798,-354,365,63,-253,230,-73,10,479,688,473,-650,-645,373,-8,8,-261,-109,888,-858,894,398,908,-978,-283,97,696,-236,231,709,952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.ExtendedProperties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{555,-555,214,1000,-1000,-1000,1000,870,-403,-222,-790,334,1000,946,-601,552,-1000,-210,515,1000,-377,-276,-1000,-896,560,-564,-1000,1000,1000,-59,-1000,-566,-1000,357,-458,132,-9,-471,1000,601,-813,451,794,-231,-707,-484,1000,-131,1000,-572,-577,260,69,-949,827,845,1000,397,112,-341,-352,-1000,1000,57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{-4,-993,489,-673,321,21,300,-1000,-413,402,193,470,-537,497,-495,128,-180,-891,842,-242,73,-883,1000,377,-963,1000,-749,582,-234,669,74,-563,-429,-1000,1000,1000,-353,380,62,-711,-160,629,128,-241,289,105,-961,786,-1000,-1000,448,-33,1000,155,-316,-894,349,650,111,472,-1000,558,-288,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.ExtendedProperties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{840,-792,202,-24,70,192,593,1000,-149,83,833,-1000,381,502,-1000,-1000,-1000,-962,-791,402,-1000,-1000,660,199,-93,-597,-311,-1000,-1000,422,227,-370,-628,384,-1000,332,-977,355,513,-481,-454,606,808,694,916,-298,849,-1000,298,-677,-14,1000,523,1000,2,-472,-197,1000,-167,224,-428,-583,-1000,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{-75,324,553,-1,-306,-805,346,-1000,-1000,-1000,-657,455,-1000,-865,737,1000,-738,-1000,132,-1000,210,-992,45,329,951,0,288,843,-156,766,-2,82,-654,-168,362,-511,-840,-149,1000,-1000,341,-829,200,998,589,-220,42,137,495,1000,-556,1000,-679,1000,-650,179,1000,617,433,-1000,1000,1000,-316,36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{211,-510,129,277,599,-839,-630,-208,465,-654,510,-1000,-6,-881,392,414,-227,475,821,-921,350,-120,439,-847,914,1000,1000,508,-15,1000,-54,769,542,-407,784,-675,-924,-374,-465,110,207,544,-1000,1000,779,-1000,-251,-16,-1000,913,942,-442,551,-54,-98,672,-667,-1000,661,280,-262,-51,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{-284,681,1000,427,627,-608,296,-1000,-1000,-1000,-1000,1000,-1000,-398,1000,1000,-1000,-1000,-569,-1000,1000,-614,-269,29,804,1000,886,293,1000,216,-1000,256,-901,605,553,-753,-1000,392,1000,-1000,431,-923,1000,1000,1000,-1000,103,586,352,1000,-1000,327,-932,521,-1000,424,1000,-98,603,-1000,1000,-301,-1000,1000}));
    }
}
