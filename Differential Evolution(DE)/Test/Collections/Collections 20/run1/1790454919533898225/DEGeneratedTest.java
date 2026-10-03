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
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "add(int,java.lang.Object):void",
            new int[]{-83,495,279,-525,660,-1000,-14,-92,532,428,858,341,1000,247,1000,-663,458,-1000,-228,1000,-521,-1000,271,1000,-1000,1000,574,-143,931,126,-1000,104,1000,-1000,1000,-1000,-643,1000,440,-1000,1000,81,567,1000,1000,-226,-218,-1000,-459,-955,-1000,61,234,1000,87,-1000,789,-897,632,-509,-1000,-1000,-1000,909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "add(int,java.lang.Object):void",
            new int[]{1000,-666,279,-898,1000,-605,-186,447,1000,142,844,860,1000,247,1000,-1000,-51,-684,731,1000,566,-1000,-1000,849,-749,1000,574,-143,1000,-961,-1000,-1000,1000,-473,1000,-498,-1000,38,-96,-1000,202,1000,608,1000,1000,-385,-287,-1000,921,-1000,-282,189,-364,1000,-269,-1000,789,-880,841,452,-1000,-1000,-1000,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "add(int,java.lang.Object):void",
            new int[]{1000,-609,-427,826,-1000,348,-727,-125,-781,531,-484,253,325,57,-837,1000,99,1000,-42,-567,156,1000,-139,-1000,1000,-1000,705,390,-702,-1,1000,-913,-548,1000,-1000,228,690,-1000,572,1000,198,-1000,-12,1000,-1000,1000,-661,1000,-1000,448,77,-137,55,-1000,455,333,-414,504,962,-898,1000,1000,-175,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "add(int,java.lang.Object):void",
            new int[]{-265,721,523,42,-397,-1000,218,-531,-1000,1000,926,-168,621,49,-1000,447,991,-317,-493,-225,-710,-236,888,601,-1000,583,337,336,-18,1000,67,609,111,-1000,-100,-441,92,749,1000,919,856,-802,-92,590,-11,259,178,-47,-1000,-469,-1000,-598,724,483,918,-105,782,-928,-65,-716,-467,238,-274,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "add(int,java.lang.Object):void",
            new int[]{-83,281,98,-307,536,-364,248,43,532,531,767,603,1000,-251,924,-209,436,-476,-365,327,-350,-271,271,126,-122,295,1000,17,623,-146,-337,-34,232,187,801,228,246,576,-626,-305,230,127,460,704,447,-114,-457,-422,-202,-584,-472,432,-155,579,-654,-437,671,-897,379,-448,-356,-238,-264,972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "add(int,java.lang.Object):void",
            new int[]{-109,299,-224,-375,844,-825,355,-336,783,-52,747,856,945,-223,949,202,540,-563,-440,343,-241,-397,326,347,-703,593,-930,-82,294,48,-581,-177,579,-250,877,-515,-103,837,-244,-847,381,55,199,825,971,361,-844,-788,-286,-812,-500,-602,-123,973,-280,-400,722,-794,599,-575,-595,-854,-879,379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "add(int,java.lang.Object):void",
            new int[]{896,-321,-379,-130,662,111,323,-501,947,771,824,1000,853,1000,1000,838,266,1000,73,675,304,-570,-658,-171,483,-216,-1000,-386,348,-730,-594,-1000,336,130,42,-682,306,1000,195,826,246,174,126,1000,1000,667,-907,-595,264,-657,198,172,26,243,710,-165,-259,-1000,462,39,-490,-41,-1000,-690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "add(int,java.lang.Object):void",
            new int[]{1000,-1000,359,163,-356,57,-499,1000,-1000,-910,-489,-1000,-1000,-326,-176,-544,-390,-593,727,927,-711,279,-1000,629,149,-865,400,1000,-489,-185,698,735,363,656,202,-802,-653,423,453,1000,-172,487,1000,28,300,630,-927,107,817,371,163,253,211,-420,531,-162,-952,714,1000,655,-795,168,-712,-594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "add(int,java.lang.Object):void",
            new int[]{276,-1000,-251,-705,370,-1000,-177,528,783,452,227,-1000,-263,516,253,-1000,-1000,-1000,1000,1000,-1000,-997,84,1000,-195,1000,524,856,-46,397,50,214,1000,-317,1000,-1000,-1000,-263,1000,-917,381,1000,199,-648,971,7,1000,-1000,847,599,-1000,52,958,514,837,-1000,-595,829,-729,-593,-1000,-73,-832,951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "clear():void",
            new int[]{-669,139,74,498,-1000,1000,707,-546,1000,188,119,578,492,182,-624,-439,513,1000,-365,-970,-1000,1000,970,607,-810,87,-669,530,468,342,494,1000,80,422,1000,23,219,-42,121,-1000,1000,1000,657,-1000,-136,-505,22,-573,-182,-1000,1000,-839,772,-1000,1000,-725,810,-640,1000,-705,-617,871,-90,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "clear():void",
            new int[]{-996,364,-257,293,-887,411,7,-188,139,977,661,983,510,-28,-576,-701,-437,578,-62,-230,751,980,223,401,-268,-176,-711,622,-251,-384,846,521,578,-45,772,-506,-701,803,-315,-767,-865,836,-205,-146,715,-680,72,-604,236,-73,-334,-62,-782,-545,543,143,800,-506,298,147,-130,739,-728,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "clear():void",
            new int[]{-11,639,519,-595,-278,602,786,-671,197,68,-329,1000,-574,-476,757,145,-870,1000,-236,-806,-155,1000,754,485,-810,951,-1000,-190,43,402,-78,460,-840,171,667,-371,-1000,-237,-611,-1000,-835,400,-49,-478,-312,163,113,-919,-598,-1000,87,-7,414,-1000,1000,-1000,32,-736,1000,-1000,-149,737,332,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "clear():void",
            new int[]{-671,411,291,1000,379,1000,441,-1000,-32,90,-934,993,191,353,-857,-1000,280,553,-194,-977,-1000,1000,437,-448,-299,1000,-1000,66,980,374,-553,1000,-151,801,1000,-636,547,-265,390,-554,1000,-99,1000,-656,-742,168,-606,-642,-627,-1000,1000,863,650,-963,1000,-657,824,-853,1000,-1000,-27,1000,-853,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "clear():void",
            new int[]{200,-223,759,-842,-1000,166,296,204,1000,590,25,-415,-950,-765,1000,1000,86,-158,-995,-565,-295,725,1000,537,-1000,-881,629,562,-243,-401,1000,-15,-872,99,-781,1000,-352,-50,-8,699,481,670,-607,-590,70,598,-102,-469,96,-204,-1000,-942,49,-286,-980,-105,189,193,208,1000,397,-709,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "clear():void",
            new int[]{393,501,519,1000,700,405,971,-671,-9,221,1000,246,1000,1000,-81,382,-870,-362,232,282,559,239,118,-103,918,-787,-1000,37,-231,-777,-84,763,1000,265,-199,-371,-896,586,844,-1000,-966,-545,86,-144,381,-1000,113,1000,347,-1000,-541,-7,-165,-262,1000,49,-657,903,46,-1000,-454,737,-1000,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "clear():void",
            new int[]{-184,-925,836,904,-857,1000,1000,-101,1000,792,-31,45,280,-97,-322,1000,841,439,-615,-452,-20,357,718,-595,-188,-291,-654,779,-75,-272,631,891,-1000,687,-1000,176,-24,-1000,633,-1000,216,660,387,-754,286,17,398,86,-196,-511,1000,-185,-285,-1000,-1000,-435,816,158,1000,-705,2,483,-1000,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "contains(java.lang.Object):boolean",
            new int[]{-888,611,153,-155,598,-1000,989,931,1000,-1000,-233,-578,-1000,727,302,-278,37,-211,-406,-643,-469,1000,-200,432,1000,912,846,-208,293,-1000,383,-933,-187,425,811,-252,567,-377,665,244,-1000,-858,971,5,452,-381,569,519,-674,-584,-597,30,1000,824,840,-1000,-449,469,822,-677,794,688,-913,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "contains(java.lang.Object):boolean",
            new int[]{922,921,557,-520,-142,397,89,-696,102,-104,759,-489,-406,-166,-341,-881,-1000,-436,691,149,142,-297,-947,258,-647,-107,-79,-664,-478,137,60,-761,-422,-731,-814,-454,-192,-548,-1000,-656,66,388,1000,-443,-756,195,-710,1000,849,-100,-522,-716,-814,-237,-668,55,-914,279,-51,-111,1000,780,72,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "contains(java.lang.Object):boolean",
            new int[]{1000,-255,-432,395,-1000,-224,774,-757,169,-282,601,-740,-785,362,-49,-194,759,-278,667,537,-516,-504,-291,-133,677,632,-430,33,-169,-616,106,-849,-187,-776,264,-871,-124,-658,839,341,-365,-477,214,742,-207,43,974,488,158,127,189,3,356,481,196,473,1000,422,-611,-546,508,564,-108,-220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "contains(java.lang.Object):boolean",
            new int[]{1000,1000,969,-963,901,-845,1000,1000,320,-1000,-771,73,218,-573,-29,-955,-966,-987,-374,-150,100,1000,-632,1000,532,373,276,-332,-544,-1000,-624,-1000,-71,187,618,-1000,-250,-269,-572,-173,-610,419,1000,-1000,-59,-1000,-533,-433,253,145,-1000,-286,410,589,417,-1000,-1000,1000,1000,-788,1000,650,-1000,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "contains(java.lang.Object):boolean",
            new int[]{917,859,733,-1000,-878,563,128,-702,-703,727,-621,283,-695,-72,-299,958,-114,-327,116,1000,-902,570,-889,961,-1000,718,-1000,1000,-1000,-169,-639,321,239,-245,-350,514,529,803,-652,793,-410,-10,-440,-614,572,-221,1000,619,1000,1000,425,-481,-256,422,-101,1000,213,152,-662,-20,-655,-475,385,-658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "contains(java.lang.Object):boolean",
            new int[]{441,231,768,-794,490,-256,-546,-1000,-30,174,-777,-210,-473,234,181,-1000,65,-357,614,428,61,-98,-328,96,-883,632,990,-930,-469,-79,309,-74,22,-329,-548,176,-635,10,-158,378,-441,761,844,-43,-18,-83,-788,1000,639,-1000,16,-1000,-262,-420,-397,360,1000,703,837,-217,686,730,44,287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "contains(java.lang.Object):boolean",
            new int[]{-716,81,402,-766,-239,457,307,-751,644,697,-613,707,-666,198,-509,823,-740,-744,210,514,-644,593,-867,-189,28,842,-691,41,-785,-228,-256,209,-370,132,-3,1000,472,256,432,851,-616,107,-467,-189,798,-91,387,573,-99,20,368,-329,-139,376,11,1000,789,951,-61,-574,877,-186,273,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "contains(java.lang.Object):boolean",
            new int[]{-352,-111,66,374,-478,-159,-198,59,414,773,331,-305,-12,303,-398,-83,-915,480,537,32,-531,-189,-296,445,-416,556,-430,-432,-695,767,-363,195,127,167,-192,211,-1000,-210,733,-42,-288,-26,37,108,-243,934,630,288,326,36,93,-255,-240,217,586,-5,-173,193,275,-581,-513,781,-575,-390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "get(int):java.lang.Object",
            new int[]{-1000,-793,294,-367,369,-877,1000,-551,320,-173,-378,69,1000,-350,-713,774,-431,737,-318,-934,-461,-694,-117,-884,1000,264,1000,208,-175,-175,-643,-229,-976,721,-868,689,-1000,-1000,1000,352,207,662,-983,528,38,1000,-300,252,218,-187,442,48,427,-266,177,-85,-323,-277,488,1000,60,-1000,-712,598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "get(int):java.lang.Object",
            new int[]{421,473,719,2,-1000,-891,-238,-791,466,-582,165,307,-875,-939,-1000,191,912,-834,458,347,730,-1000,-595,95,-1000,-2,310,-127,188,-501,-422,431,472,1000,-1000,1000,572,-1000,-566,-214,-609,634,1000,909,513,-170,-1000,1000,-575,487,870,-452,-1000,-1000,257,-340,-374,1000,1000,430,-858,-1000,510,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "get(int):java.lang.Object",
            new int[]{44,-327,-203,12,-767,323,1000,-797,468,108,-711,384,-340,318,-713,-332,502,-289,623,121,-907,-444,593,588,481,-89,169,211,-631,-300,892,-1000,74,206,-804,1000,-999,-89,-541,70,-680,129,644,673,116,-290,23,407,157,-85,418,265,-399,-83,69,-901,712,786,879,-712,498,35,-657,830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "get(int):java.lang.Object",
            new int[]{-1000,-100,494,-266,-893,596,966,-1000,1000,1000,-385,-1000,-277,927,730,1000,69,273,294,-723,146,-444,-690,-970,481,544,347,121,-244,-55,-537,288,-740,1000,-1000,1000,157,-1000,-923,-805,-957,-154,-331,461,-569,455,-139,952,-1000,-14,1000,-119,638,-53,-1000,-708,791,-1000,396,501,-924,848,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "get(int):java.lang.Object",
            new int[]{-211,473,-898,-215,241,733,-238,-425,-246,543,-154,307,-75,495,409,-78,591,-834,458,95,-900,122,-595,165,712,-2,481,2,-1000,310,899,389,472,-141,-442,163,-957,-658,774,-458,724,420,-653,325,353,-170,1000,1000,524,-139,229,550,797,777,-963,-340,687,-1000,161,277,353,-79,25,128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "get(int):java.lang.Object",
            new int[]{-211,-19,-28,1000,665,404,-238,167,-983,-220,-650,649,161,477,429,-1000,684,348,1000,-196,-131,759,1000,915,-120,-728,-490,41,-1000,-837,-109,-562,153,-218,-283,-1000,-710,-658,-1000,515,96,-638,1000,687,447,210,-384,618,197,-253,-358,736,-838,777,981,-1000,-20,-747,-666,43,1000,984,-614,-993}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:aXRlbTM=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "get(int):java.lang.Object",
            new int[]{44,-225,-203,12,-767,-861,1000,-797,468,-474,-627,384,-340,318,-1000,11,502,-289,744,121,-907,-444,593,1000,481,-89,907,211,-770,-300,892,-1000,74,206,-804,1000,-999,-1000,-541,70,-680,129,1000,673,774,-290,649,407,157,-85,418,265,-402,-83,69,-901,712,-269,879,-712,498,-324,-991,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "indexOf(java.lang.Object):int",
            new int[]{-786,499,-217,1000,568,1000,-646,1000,1000,379,1000,-296,847,538,-569,346,278,1000,1000,1000,1000,627,1000,-644,-1000,-9,273,-776,-290,-1000,125,1000,359,182,-1000,615,-1000,1000,154,527,-467,1000,-62,6,-1000,-1000,1000,603,-1000,821,-1000,-1000,-576,1000,-786,-1000,336,876,-453,-961,812,-1000,-855,-576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "indexOf(java.lang.Object):int",
            new int[]{-655,-175,-511,581,-1000,-213,-261,-749,-883,-722,77,79,-690,181,-97,-542,-494,-1000,-1000,-488,-750,-146,-1000,-574,213,-776,-618,1000,-178,418,-922,-196,-831,300,560,-625,1,-400,-1000,-113,-225,412,190,1000,376,235,-961,-1000,584,19,1000,451,867,-1000,142,489,-570,-313,-463,-605,-1000,-120,732,-793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "indexOf(java.lang.Object):int",
            new int[]{77,787,-32,87,-1000,460,-687,137,-400,792,-87,-263,-812,-400,-1000,1000,-166,1000,-895,-46,-400,-381,166,678,33,646,-226,369,-329,-308,-898,-400,424,-549,-273,-101,-469,-400,329,364,1000,633,-928,-283,298,242,-1000,1000,312,-713,-487,-817,1000,-143,-882,-734,175,823,-837,385,558,-864,301,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "indexOf(java.lang.Object):int",
            new int[]{-10,789,-486,836,129,816,-747,439,-63,820,579,473,523,546,-344,851,595,270,121,337,-142,992,503,-33,-589,724,-337,306,1000,-205,-625,663,435,-244,-1000,477,-1000,-542,-334,-390,-175,936,-982,223,-261,-817,757,-558,-342,502,-400,-400,284,-867,-841,155,-1000,-983,156,257,102,-400,-581,-554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "indexOf(java.lang.Object):int",
            new int[]{-1000,308,99,776,635,1000,-507,197,1000,-362,785,373,1000,1000,285,-659,-192,-400,1000,-400,765,324,-360,-133,-255,42,751,-1000,42,-250,-9,1000,-181,-7,-108,348,-1000,1000,412,-328,-365,612,1000,-577,-57,-781,-230,-215,-793,1000,-142,-1000,-1000,653,510,-21,229,1000,-635,-927,190,-493,-563,-135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "indexOf(java.lang.Object):int",
            new int[]{-75,988,-111,916,-806,1000,431,763,400,-358,835,804,791,-166,-254,952,1000,-850,1000,1000,23,-489,1000,-784,-400,1000,-1000,-1000,1000,-702,1000,417,596,-269,-840,1000,-958,-542,-657,325,-386,1000,-597,942,-695,936,1000,-439,-705,781,-400,-400,-1000,-556,-1000,-616,-454,-1000,-258,-1000,707,-400,-385,-285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "indexOf(java.lang.Object):int",
            new int[]{-726,489,479,-538,57,1000,-580,-562,-915,-905,-846,-38,296,372,-59,-560,-314,-400,-684,-948,185,-416,-513,-133,1000,-81,694,258,400,575,-770,-209,-461,-193,1000,244,720,-513,71,-855,897,-516,1000,-411,986,214,-746,-279,1000,-185,1000,1000,135,1000,551,-21,-627,243,-710,500,190,1000,888,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "indexOf(java.lang.Object):int",
            new int[]{-1000,-167,-321,1000,-461,160,-1000,-103,432,227,-36,-57,230,775,-158,-514,-1000,-83,-269,-393,-214,66,-400,355,-685,-459,1000,184,-1000,96,-651,757,-755,317,288,-105,50,1000,756,754,-159,128,475,-681,-178,-1000,-400,656,193,812,-351,-638,807,-839,183,36,181,1000,-700,-719,-222,-87,-244,-324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "indexOf(java.lang.Object):int",
            new int[]{-1000,-131,-236,849,-559,639,-1000,-986,-271,363,-473,663,736,722,596,-610,-20,-1000,-1000,-969,-398,392,-1000,455,-123,134,439,146,197,1000,-832,1000,-222,166,397,-76,150,-28,-664,-578,-609,-296,562,-420,-31,772,-1000,-1000,1000,852,459,66,139,-1000,552,1000,-782,-136,-222,-405,-1000,1000,288,-447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "indexOf(java.lang.Object):int",
            new int[]{579,-780,334,1000,202,199,-1000,-358,1000,-722,-78,1000,184,1000,279,-660,-1000,-315,-1000,-960,-5,346,-1000,-1000,-780,-621,1000,523,-1000,-202,-1000,1000,-828,500,-98,-960,-664,1000,-654,804,-162,-199,860,508,-425,-1000,-876,-885,597,1000,-908,-1000,865,-905,872,-206,760,1000,-1000,-1000,-190,-1000,-704,-527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "iterator():java.util.Iterator",
            new int[]{389,-747,-986,-551,-552,-1000,-333,-881,-977,-97,-1000,-6,-195,-423,854,-737,-724,905,-339,1000,-1000,-342,-49,703,1000,-997,-630,-690,-419,626,1000,537,523,596,-945,-132,-162,-1000,1000,836,1000,32,-735,398,-634,-373,1000,-407,-846,-317,860,-300,-252,-62,-319,-1000,-706,-1000,361,-436,694,-1000,1000,-518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "iterator():java.util.Iterator",
            new int[]{639,91,-936,-47,-731,-668,-132,-119,127,-69,-321,464,-406,-422,1000,-760,-1000,-336,6,483,-357,449,79,-440,413,-133,-1000,-1000,-64,-88,129,310,225,1000,-225,254,-553,-1000,1000,183,401,-286,-120,1000,-1000,-206,558,-423,73,-458,867,414,-16,-451,-629,-237,52,28,-103,-361,1000,-282,-20,386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "iterator():java.util.Iterator",
            new int[]{-919,1000,488,-919,1000,-1000,560,57,578,-863,663,-541,583,-264,-1000,1000,-847,1000,147,-579,992,872,841,-762,262,556,-604,287,-63,198,-1000,1000,-623,-1000,1000,351,1000,1000,-302,158,669,-277,-959,-838,1000,555,-1000,-463,-758,-1000,-283,-494,-470,-912,1000,448,653,728,-1000,-262,-1000,666,213,-261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "iterator():java.util.Iterator",
            new int[]{-630,665,221,1000,-71,930,1000,180,-556,-477,1000,859,-815,958,499,314,-624,-1000,973,-1000,1000,1000,514,-51,-1000,-805,-334,3,735,-1000,588,-831,-1000,-1000,831,895,-81,-67,-1000,-637,-684,-350,581,601,771,1000,-418,-558,781,973,-817,-184,737,1000,-964,-882,-767,1000,-1000,-110,969,1000,-1000,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "iterator():java.util.Iterator",
            new int[]{-8,-751,493,431,-20,944,-667,46,676,-491,388,-793,342,-960,94,314,929,-896,-823,691,-317,508,167,-819,427,628,-334,515,-96,95,-419,899,585,339,-495,45,-295,-67,-343,545,919,-994,340,893,-64,-936,-247,-242,781,507,-817,165,899,-172,-912,-882,-767,-959,-624,920,-378,-766,337,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "iterator():java.util.Iterator",
            new int[]{-871,609,-976,-280,822,-1000,500,-272,-563,-461,-1000,-329,-536,557,-431,-528,-851,1000,-506,325,-308,444,-661,-511,866,-334,418,-55,-367,-20,891,675,-686,-1000,-664,31,590,-246,265,-409,493,948,-122,-1000,1000,-1000,-369,636,688,301,468,-1000,103,-749,-161,694,-30,-195,310,522,-563,-932,995,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator():java.util.ListIterator",
            new int[]{1000,909,99,176,-305,127,1000,-771,-874,-479,-791,-573,-259,200,99,-365,-1000,341,775,-222,1000,401,-315,74,959,-166,-477,-772,-370,917,-765,1000,-645,-883,-1000,-898,-34,-205,33,-659,-1000,-448,938,-268,838,504,834,966,775,148,834,698,-1000,-145,24,-1000,-1000,417,-715,295,-859,-25,-752,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator():java.util.ListIterator",
            new int[]{-150,158,-322,461,988,456,-539,-12,42,832,-384,-946,-284,822,1000,-623,-1000,-911,-54,1000,-713,1000,-1000,-1000,-1000,649,250,-75,-1000,268,-1000,-1000,385,1000,896,-1000,1000,-68,817,12,1000,1000,931,-1000,1000,344,915,-1000,641,1000,-172,-941,-1000,1000,-85,400,767,767,-441,114,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator():java.util.ListIterator",
            new int[]{203,-411,157,-1000,-213,127,1000,894,-264,542,-243,-573,239,81,-419,163,1000,623,775,731,1000,-1000,-171,919,484,772,-6,-772,156,7,1000,-1000,-645,-121,425,1000,1000,-739,-542,1000,-1000,13,-662,404,-821,769,135,-372,-41,57,-1000,-909,470,-1000,1000,298,-270,323,-73,324,527,-331,579,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator():java.util.ListIterator",
            new int[]{892,637,924,959,-188,-628,-451,-183,-651,-745,-204,-410,-837,-225,-885,-82,-623,-198,605,-112,-228,821,-594,520,140,831,-135,-61,514,523,-148,964,-184,-266,-271,-184,425,204,705,175,124,229,553,924,-47,-80,20,561,591,462,621,483,-917,-950,-1,-850,-65,938,530,-551,-55,-512,423,514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator():java.util.ListIterator",
            new int[]{15,969,-307,-432,-661,-976,877,-308,4,366,146,-680,-186,-1000,-198,-1000,356,337,44,102,985,-500,-842,26,537,-666,-399,612,-332,-765,-286,902,-273,-54,133,-767,-36,262,907,-705,-119,-159,-361,1000,-863,351,-1000,-1000,-797,265,232,373,-362,387,-806,-182,1000,-145,1000,148,36,991,-617,386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator():java.util.ListIterator",
            new int[]{360,1000,-356,623,-198,-1000,213,-837,10,183,118,47,271,-1000,-583,-801,915,702,-1000,-1000,1000,-1000,460,834,1000,-830,-415,-1000,215,-598,443,1000,-1000,-1000,-1000,120,-668,-291,223,-1000,-705,-70,-562,1000,-1000,1000,-1000,-577,-1000,419,69,481,-928,-121,-96,-1000,521,-274,49,478,980,-395,-267,-893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator():java.util.ListIterator",
            new int[]{699,668,-2,803,-523,-506,292,120,-320,793,-625,-192,711,-142,-584,-490,477,-346,18,361,-68,37,49,567,706,883,-468,-861,-390,969,361,-339,-246,-1000,-403,54,417,-347,-13,565,-401,-14,-415,1000,-293,1000,-20,143,-272,497,515,-32,-620,-753,729,-210,879,-311,-1000,400,500,-545,-199,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator():java.util.ListIterator",
            new int[]{1000,809,214,461,-26,-598,435,-12,-818,1000,-203,-109,-284,-562,995,-1000,-737,-911,-296,-341,606,534,414,267,-1000,598,111,522,-1000,-632,-691,83,-187,274,49,-547,753,95,705,-1000,528,1000,239,-620,-164,344,-304,-1000,-174,1000,-814,-771,-1000,278,-260,-676,1000,767,400,627,-1000,1000,-727,-30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator():java.util.ListIterator",
            new int[]{-1000,-855,944,-590,267,689,626,74,491,-1000,180,-252,-124,1000,-939,258,28,261,-806,1000,-300,782,-271,-204,271,501,-406,-402,-437,192,152,-884,1000,997,1000,-674,649,-135,-112,1000,-85,-1000,-425,-81,521,-640,1000,360,887,-692,-113,-54,1000,277,276,1000,-898,859,126,-487,196,66,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator(int):java.util.ListIterator",
            new int[]{667,-213,168,173,1000,352,-897,958,41,91,-152,87,144,-735,447,-1000,-830,-814,1000,106,1000,-65,-1000,-699,1000,1000,-159,-325,606,-1000,275,-83,-495,-454,495,-911,364,347,45,-865,-587,-359,633,-736,-45,-762,-643,-1000,-436,-1000,1000,-239,220,-608,-151,-188,-201,315,459,-1000,-119,-835,643,-157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator(int):java.util.ListIterator",
            new int[]{-1000,-165,-916,-397,-689,-223,208,-793,482,-1000,-96,372,-1000,431,-902,1000,655,-1000,-976,317,-935,-1000,628,86,-695,900,214,-132,-289,569,-1000,-223,-69,438,-1000,943,612,-536,202,173,1000,663,197,-450,468,1000,1000,-1000,1000,1000,-346,-1000,1000,-54,530,1000,372,-522,-584,606,-355,676,-771,43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator(int):java.util.ListIterator",
            new int[]{0,0,538,-327,-72,-233,-63,-105,179,821,841,889,758,0,-278,-307,281,108,1000,-446,-192,-91,-835,-935,-1000,-532,-460,1000,755,-215,0,-861,-525,1000,182,1000,365,1000,974,490,-203,1000,260,782,-83,-1000,-1000,335,169,608,-727,0,-532,539,-1000,426,1000,1000,-1000,-180,-1000,0,0,41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator(int):java.util.ListIterator",
            new int[]{1000,-567,942,-8,1000,243,-447,-760,677,-706,763,-392,1000,-1000,-246,-445,-1000,-1000,1000,1000,-194,49,-1000,-638,-665,307,1000,-301,1000,-277,324,1000,1000,11,1000,898,595,-216,-1000,-1000,681,-1000,1000,697,897,196,1000,436,-1000,-229,-427,-376,-755,48,962,79,-238,-140,1000,-1000,-503,-735,486,180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator(int):java.util.ListIterator",
            new int[]{-36,-567,-81,-157,728,33,-785,861,677,883,-709,-688,258,-1000,141,-900,-733,-308,1000,-944,637,612,-368,-638,962,-1000,-1000,-301,-433,-1000,-274,1000,436,11,454,-215,145,596,80,-1000,-106,-774,-147,-111,688,196,221,-438,209,-1000,865,-5,-119,932,962,-1000,86,1000,-829,-2,-308,-576,563,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator(int):java.util.ListIterator",
            new int[]{-892,91,848,179,-711,802,88,-1000,21,-368,249,962,-635,-275,-727,-1000,-488,-1000,-143,-8,-284,-29,-211,150,-882,-178,734,-88,-258,-935,-537,398,1000,-1000,930,1000,373,-280,-14,1000,613,-1000,-450,1000,1000,1000,997,31,-1000,336,-354,-40,-680,1000,-174,-21,-782,476,380,-450,-1000,-324,486,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator(int):java.util.ListIterator",
            new int[]{-809,345,-751,-930,-511,-824,85,-553,-466,-744,645,-253,-899,826,-515,776,-48,-801,161,472,-789,-866,381,492,74,207,669,-78,881,765,-202,-369,961,560,-124,841,207,248,-193,56,827,-484,-371,-318,371,847,145,-637,784,627,226,-637,979,-331,154,148,231,-944,-512,-253,539,684,47,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.list.TreeList$TreeListIterator", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "listIterator(int):java.util.ListIterator",
            new int[]{-990,-311,696,133,-103,566,-273,-160,-36,232,958,-912,-234,625,-131,-406,590,-781,-715,-837,273,514,281,-202,-247,-259,-220,-807,-582,929,-854,680,-229,10,-255,769,-614,-322,-780,-13,694,368,88,-84,742,884,104,-998,-254,987,-151,41,-992,540,971,-949,-477,-294,619,691,-471,119,-263,-700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "remove(int):java.lang.Object",
            new int[]{616,129,-216,-110,-222,988,-932,-107,-741,112,814,-28,50,-318,927,-6,580,-43,-498,619,-697,-624,-427,-351,-999,206,856,-253,400,169,68,514,-905,-827,-47,-985,-961,286,-445,245,-157,-643,450,720,38,346,-310,425,84,-654,-4,686,-598,-495,-589,168,991,-365,38,176,-702,-835,769,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.String:aXRlbTM=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "remove(int):java.lang.Object",
            new int[]{1000,71,458,-686,-1000,399,564,-248,-1000,-234,463,293,1000,766,239,1000,857,-537,-492,583,-193,-7,-786,-990,-492,448,-764,-1000,1000,830,-1000,368,368,444,354,-757,-1000,-21,-396,-964,1000,-140,784,195,-139,-132,-1000,344,1000,-1000,511,1000,-520,-658,230,927,763,966,1000,342,-775,-634,-249,97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "remove(int):java.lang.Object",
            new int[]{-930,-169,839,1000,1000,781,-1000,-162,218,-51,-198,456,-497,-1000,-228,-1000,234,1000,-951,-770,-384,-793,42,16,-1000,333,-328,884,160,-1000,-1000,-1000,-25,-239,1000,-692,559,-1000,-933,511,-587,1000,-422,-312,-203,-232,1000,315,-1000,555,552,-1000,58,-1000,-17,-509,527,-533,-863,-153,-583,-769,-443,797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "remove(int):java.lang.Object",
            new int[]{906,1000,-636,-363,-1000,-1000,-1000,761,-877,-741,353,649,643,1000,-135,1000,-596,185,-193,474,-255,158,-454,-1000,261,353,-775,-1000,138,1000,1000,134,511,445,-1000,1000,-514,-315,345,-127,254,-608,454,1000,-306,-845,-1000,-1000,159,-1000,-1000,1000,403,-905,1000,781,455,19,1000,272,1000,1000,-459,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "remove(int):java.lang.Object",
            new int[]{-397,679,-601,270,1000,219,-1000,254,1,1000,1000,210,593,753,1000,870,-39,-576,317,436,-205,-380,94,-1000,-326,-206,578,-1000,-348,935,637,-1000,58,-718,-1000,118,78,-151,434,970,-1000,416,-470,1000,-389,-842,-196,123,214,-191,-295,450,684,-744,597,-312,770,-795,454,-1000,866,748,1000,505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "remove(int):java.lang.Object",
            new int[]{956,403,-571,1000,-357,-538,-195,194,-337,-32,92,551,-580,-215,1000,49,223,506,-359,-267,-557,-1000,681,-1000,-974,83,-864,-347,472,-342,-22,-708,-805,463,496,993,96,-1000,-768,1000,-948,409,-520,161,-259,-1000,-606,-528,159,-681,-1000,1,1000,-417,-655,569,579,-591,-381,118,-466,505,-506,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "remove(int):java.lang.Object",
            new int[]{683,-225,-352,-1000,-1000,714,688,-614,-1000,-431,423,244,-309,-42,159,1000,356,-1000,-498,455,-1000,-769,-412,-351,467,-628,856,-426,1000,1000,-692,1000,-706,-1000,-466,-230,-1000,286,-338,118,556,-1000,1000,229,509,953,-1000,643,918,-755,-133,1000,-1000,-1000,-186,756,770,108,348,1000,-1000,-1000,1000,-659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "set(int,java.lang.Object):java.lang.Object",
            new int[]{-513,-229,939,-1000,67,-770,1000,19,1000,-862,511,340,-150,1000,278,-971,735,-24,375,-352,-942,262,-1000,750,-287,475,206,144,509,305,108,790,-445,217,1000,-481,258,786,369,-155,-25,1000,821,1000,17,863,-203,614,1000,-122,587,-568,-88,-150,1000,-240,-552,665,-202,-336,-238,-1000,1000,113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.String:aXRlbTE=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "set(int,java.lang.Object):java.lang.Object",
            new int[]{-702,731,474,-379,-783,-849,-1000,-266,867,-57,1000,-1000,718,57,-628,-1000,165,320,315,-1000,-362,-584,-801,1000,-1000,1000,23,6,-794,1000,758,87,-1000,-23,-1000,840,563,886,-482,1000,229,-449,645,-1000,1000,1000,-197,1000,1000,-452,1000,-916,969,-933,1000,-968,1000,-1000,-121,-1000,-705,511,-918,583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "set(int,java.lang.Object):java.lang.Object",
            new int[]{-1000,1000,-716,-571,-298,-1000,-1000,-81,983,632,1000,-419,917,57,-927,-1000,187,779,181,-670,-362,-849,-415,62,-832,766,141,176,-1000,718,240,-374,-1000,428,-809,-76,563,-278,-377,414,229,-52,214,190,1000,671,-197,183,607,-452,971,-470,533,-933,1000,-968,1000,-1000,-49,-1000,-705,859,-958,373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "set(int,java.lang.Object):java.lang.Object",
            new int[]{87,837,249,-89,377,-369,734,329,894,797,-276,626,426,371,-998,712,743,451,634,444,249,-663,-733,-628,342,-94,-87,-68,701,537,-903,545,272,-528,-554,-530,-987,305,746,40,-381,-552,301,941,361,-95,28,-418,-27,-573,-866,266,-927,-763,-540,-104,-28,-93,-397,208,-35,-329,-211,-790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "set(int,java.lang.Object):java.lang.Object",
            new int[]{-10,-479,119,575,-822,-15,20,16,307,814,926,-460,-768,-260,592,20,584,323,221,1000,-1000,1000,-412,-489,1000,-845,386,495,-856,1000,63,152,-145,-565,440,-428,-356,-765,129,-1000,723,-615,-1000,-744,-559,-431,146,-41,577,-389,953,-330,-748,447,-512,-194,1000,341,-693,220,-147,-195,1000,473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "set(int,java.lang.Object):java.lang.Object",
            new int[]{69,682,-371,-79,204,202,1000,523,148,595,-1000,52,986,-484,-521,1000,386,444,677,1000,-33,-1000,118,-748,-12,-1000,215,546,248,826,1000,467,528,209,-1000,-915,-309,-48,-177,315,-1000,-870,-712,1000,361,-752,-588,-313,-236,276,-164,-842,-320,-881,-1000,-51,-300,-285,-214,443,178,-417,732,-101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "set(int,java.lang.Object):java.lang.Object",
            new int[]{-807,1000,14,-39,-200,-849,-1000,-313,780,939,1000,-600,732,1000,-820,-650,-174,562,454,184,-1000,-584,41,1000,-1000,510,-167,860,-1000,1000,590,-865,-1000,-219,-1000,1000,-302,886,-482,1000,287,-1000,365,-1000,1000,325,-1000,1000,1000,-774,-400,-1000,-124,-536,986,-1000,1000,-1000,-109,-1000,-419,637,-873,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "size():int",
            new int[]{1000,-747,98,-833,-266,-216,-291,-99,105,157,891,-121,-271,491,416,456,-702,-128,671,438,-119,175,-86,236,-34,-16,214,-983,-97,320,-1000,324,-42,-1000,5,127,494,-1000,312,-200,411,-864,-578,385,-692,62,-559,143,-982,778,-289,-52,-72,-42,258,145,-247,727,-383,-1000,-492,-4,413,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "size():int",
            new int[]{1000,-810,-286,-1000,920,-1000,-705,-444,98,-71,-1000,1000,-708,259,-14,-428,-536,955,432,751,395,505,-932,-492,228,-320,-1000,1000,134,212,-1000,126,352,1000,592,-988,-761,632,-276,-145,958,1000,410,-88,27,257,-1000,-743,-133,-1000,633,115,-83,-1000,-733,432,-304,-1000,-388,-480,-156,411,-112,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "size():int",
            new int[]{-149,-261,439,-191,-115,406,233,-466,-391,444,1000,-1000,-487,683,653,516,793,-298,657,-995,-443,-186,432,762,-1000,-133,556,-500,-154,868,461,189,-386,290,-687,1000,-92,-1000,447,388,307,-177,-162,-88,-1000,-229,749,157,-716,580,-432,43,-208,258,570,-1000,315,410,-267,149,1000,25,280,-850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "size():int",
            new int[]{232,25,343,-766,615,-1000,-336,1000,979,-231,-254,1000,190,-850,-654,-759,-781,677,-48,1000,-572,1000,-1000,-1000,85,-498,-1000,-175,553,739,-990,-211,-1000,190,-166,-1000,2,456,374,-698,1000,802,1000,831,-833,-216,-693,-69,272,-546,669,393,315,-1000,-723,-99,-447,-1000,-472,-1000,-15,-583,-177,64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "size():int",
            new int[]{810,630,-51,-438,354,384,-323,-257,416,-1000,-389,325,-524,788,626,780,-112,-1000,-364,-1000,1000,775,402,-86,-167,-207,-294,-940,-79,100,-657,1000,36,398,303,1000,-951,-619,-529,838,-46,84,-142,-62,-176,474,-219,-209,-1000,430,-104,880,768,264,-294,-436,373,988,1000,1000,1000,705,149,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "size():int",
            new int[]{827,-1000,194,-486,-267,644,-389,-335,-291,344,927,-263,-492,824,610,688,-40,-472,976,430,122,144,-86,236,-419,-368,545,-645,-390,467,-1000,560,-42,-687,-421,517,592,-1000,185,-179,309,-1000,-991,213,-663,405,-200,355,-676,851,-453,-175,-385,-42,380,-436,-241,802,-235,-954,-75,355,509,-220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "size():int",
            new int[]{708,337,551,144,-936,596,678,-272,730,-617,486,1000,-995,30,299,738,596,-1000,-570,-268,1000,42,731,-130,535,-48,-368,510,-551,-19,-106,1000,-1000,-84,653,-72,216,382,-486,-79,204,524,146,153,311,177,138,359,-582,654,-50,376,561,1000,807,-1000,423,1000,914,970,955,-300,413,913}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:1:25:java.lang.String:aXRlbTI=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "toArray():java.lang.Object[]",
            new int[]{164,1000,294,-508,-1000,702,-41,922,1000,-222,-320,-373,813,-907,-582,870,119,-778,-32,569,248,885,-503,-590,312,-147,-893,1000,77,931,346,-701,-970,89,470,1000,1000,19,142,-859,-732,-444,8,169,932,342,-325,-176,574,352,-29,-132,-285,1000,1000,-274,602,-238,-326,-1000,577,-152,-655,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:3:25:java.lang.String:aXRlbTE=:25:java.lang.String:aXRlbTE=:29:java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "toArray():java.lang.Object[]",
            new int[]{351,-653,1000,464,329,-649,-349,939,-39,-1000,215,-1000,-400,-1000,1000,-961,-838,-312,-77,207,11,-654,-311,-1000,-515,826,396,1000,140,-1000,1000,-1000,46,866,-1000,591,373,19,-1000,-1000,-422,11,1000,1000,-693,1000,1000,369,279,958,-714,911,536,-37,433,-356,-551,-611,1000,131,-1000,-279,1000,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:4:29:java.lang.String:b2JqZWN0MA==:25:java.lang.String:aXRlbTI=:25:java.lang.String:aXRlbTI=:25:java.lang.String:aXRlbTA=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "toArray():java.lang.Object[]",
            new int[]{37,647,-486,710,122,-188,680,-753,-241,-639,319,263,180,-885,-629,585,493,62,950,514,123,220,332,-2,-716,126,-462,688,1000,462,60,316,-632,-1000,519,-646,9,375,-66,-750,-44,83,-10,-293,55,-509,706,1000,155,1000,-308,-305,304,148,1000,124,170,-885,314,1000,1000,251,-865,575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:0", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "toArray():java.lang.Object[]",
            new int[]{-44,480,-306,-687,-976,-18,197,-276,233,-910,-84,108,65,-340,334,530,-602,-181,-200,1000,665,-906,-190,-47,-125,868,85,359,-1000,-1000,1000,459,120,-195,469,288,179,-599,269,-928,-93,1000,-719,653,774,-953,1000,352,-689,790,105,651,-1000,1000,220,-1000,685,-915,-198,170,-250,-58,876,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:3:25:java.lang.String:aXRlbTA=:29:java.lang.String:b2JqZWN0MA==:25:java.lang.String:aXRlbTA=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "toArray():java.lang.Object[]",
            new int[]{499,-349,-487,220,696,-125,238,-285,226,-179,1000,-1000,301,-1000,5,-338,631,-594,135,-639,270,-184,-630,-1000,-1000,327,-465,-777,1000,460,620,-1000,606,-271,-414,89,7,165,87,-742,-298,345,578,790,70,464,766,-605,-358,679,-416,140,1000,-767,885,452,-1000,-498,1000,-351,-265,-101,633,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:1:25:java.lang.String:aXRlbTQ=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "toArray():java.lang.Object[]",
            new int[]{365,379,-902,-235,-732,-766,848,-315,-382,-1000,117,482,315,-552,-172,620,-118,150,276,581,769,-745,-259,378,-435,681,251,330,-191,129,704,-138,-114,-877,423,-613,-444,117,485,-925,197,1000,-606,20,663,-1000,1000,-227,-134,983,457,334,-1000,383,483,-120,1000,-1000,-181,483,358,104,387,4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:5:25:java.lang.String:aXRlbTA=:25:java.lang.String:aXRlbTE=:25:java.lang.String:aXRlbTA=:25:java.lang.String:aXRlbTA=:25:java.lang.String:aXRlbTE=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "toArray():java.lang.Object[]",
            new int[]{-987,-781,294,215,516,-595,-125,922,-877,71,-644,697,-352,482,-582,-76,-298,929,199,-755,220,885,-503,-231,470,263,216,-917,939,931,-985,-86,-609,-578,-198,-955,-668,19,419,417,143,266,718,210,227,342,-976,-857,656,-268,-29,238,989,-871,-378,844,-755,640,-124,-135,289,974,-735,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:3:4:NULL:29:java.lang.String:b2JqZWN0MA==:29:java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "toArray():java.lang.Object[]",
            new int[]{1000,470,-826,-1000,-1000,-468,-505,-85,1000,-394,-495,-939,1000,-835,450,539,-375,-1000,-1000,-600,-258,473,1000,-1000,541,327,-171,240,-1000,-512,1000,-405,-441,1000,605,1000,374,-1000,1000,-1000,-919,388,58,797,-95,-251,1000,153,1000,438,-288,469,-1000,1000,1000,444,1000,282,480,-676,-1000,-647,1000,2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:1:25:java.lang.String:aXRlbTM=", DEReplay.run(
            "org.apache.commons.collections.list.TreeList", "org.apache.commons.collections.list.TreeList", "toArray():java.lang.Object[]",
            new int[]{-238,-1000,-31,-662,577,-393,-432,1000,-527,62,-93,-222,535,510,-276,-127,-586,364,-806,-1000,167,344,59,-660,890,101,928,-1000,624,1000,-744,169,-894,885,-971,-136,-379,302,952,-752,324,185,1000,-1000,768,241,-526,-415,1000,-971,228,-485,554,-1000,146,831,-1000,651,192,342,329,491,-1000,391}));
    }
}
