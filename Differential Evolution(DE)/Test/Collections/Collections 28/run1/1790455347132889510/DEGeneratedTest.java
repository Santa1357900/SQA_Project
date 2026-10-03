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
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "clear():void",
            new int[]{-662,389,-441,-56,2,-753,1000,1000,1000,161,232,1000,596,1000,-212,-561,108,-261,-864,-412,80,443,784,843,958,-1000,-1000,-381,-183,231,-1000,602,558,1000,-30,-351,529,-287,-205,-457,5,826,-173,-586,-891,-251,861,701,-692,1000,495,-45,-18,-254,8,212,-488,1000,1000,-1000,40,-474,-83,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "clear():void",
            new int[]{429,-712,-777,-1000,251,-1000,18,-334,-904,1000,697,-1000,46,-216,-228,-302,521,1000,706,-22,-587,-1000,-274,-442,795,-1000,893,-1000,736,-1000,1000,-501,-978,135,-283,542,-775,10,691,870,-1000,-159,-296,458,609,-1000,256,-274,951,-624,953,282,15,258,-395,80,-989,-919,1000,796,151,717,-288,-372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "clear():void",
            new int[]{561,-351,-567,-592,625,-1000,-98,-200,-1000,255,352,-301,-521,-578,83,-82,-666,-127,1000,410,-217,-111,530,-221,-268,-876,-919,-325,516,-631,136,-1000,589,-4,390,331,-670,1000,-815,-1000,-578,215,249,-79,481,-179,-195,798,98,-133,-751,294,-63,-90,-624,359,-1000,713,-1000,1000,-337,1000,-1000,14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.analyzer.StringKeyAnalyzer", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "comparator():java.util.Comparator",
            new int[]{340,819,569,-353,1000,495,782,357,-547,-432,-322,798,-361,1000,329,-747,658,-177,645,618,-526,506,-957,-86,-1000,396,-229,1000,-677,-49,-39,-52,1000,653,-1000,224,1000,-978,-317,-248,201,576,-31,545,-860,674,64,-719,-1000,-35,-621,988,119,-239,500,488,39,-303,-382,-320,-75,82,-157,371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.analyzer.StringKeyAnalyzer", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "comparator():java.util.Comparator",
            new int[]{-507,-392,-547,-890,-141,-684,914,404,-222,-876,-14,882,765,-967,-533,15,-626,666,-637,35,-139,334,-767,-249,810,304,270,-269,693,774,511,609,421,-518,408,727,94,815,948,-677,359,-131,480,-956,45,957,-564,667,-923,738,296,-355,-918,-616,-744,296,-478,941,357,-694,731,423,82,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.analyzer.StringKeyAnalyzer", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "comparator():java.util.Comparator",
            new int[]{878,605,124,-66,1000,292,40,-835,55,-247,-369,1000,-469,501,1000,-992,957,-106,1000,-627,-9,4,-94,433,-1000,-943,-771,973,-1000,-1000,144,-751,1000,458,-843,-918,547,-1000,-891,236,415,662,-621,-149,-1000,-514,533,-918,-50,-842,-717,428,35,-619,-262,-376,1000,1000,-704,-131,-511,-1000,-271,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "containsKey(java.lang.Object):boolean",
            new int[]{122,-913,139,-404,928,-670,-820,555,1000,-89,13,249,563,191,434,657,174,938,373,-39,-512,315,168,-106,778,287,313,472,529,-120,1000,266,96,441,-429,-315,24,698,-371,-433,1000,367,558,20,-564,1000,-504,149,-329,-983,205,-76,166,459,-263,-586,-408,517,985,-480,93,-215,563,-279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "containsKey(java.lang.Object):boolean",
            new int[]{111,-16,-856,-550,-853,-926,-813,461,-733,14,722,-150,115,846,-947,20,527,-630,-541,864,-849,-464,-917,-10,-704,808,604,598,719,-666,-558,-799,458,-148,-460,-496,537,54,-580,823,385,856,78,-776,171,450,-395,-815,-126,-657,-85,-932,-97,-330,856,905,332,980,169,104,-581,459,238,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "containsKey(java.lang.Object):boolean",
            new int[]{-1000,-293,289,-136,481,-229,-770,-494,-948,191,385,909,-192,-980,992,354,-1000,-1000,-1000,-863,835,95,-463,1000,390,42,236,1000,68,-762,1000,1000,355,1000,-1000,173,512,1000,-904,777,-42,1000,401,-1000,-87,1000,-843,1000,-369,-1000,-614,972,-454,1000,-744,-151,509,1000,1000,-852,-676,-549,-551,509}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$EntrySet", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "entrySet():java.util.Set",
            new int[]{90,-199,-336,-98,302,-1000,-232,175,975,-891,1000,348,-414,-748,-943,-126,-473,-132,426,5,-540,229,-938,-673,826,557,573,1000,637,-1000,-167,252,335,18,-794,538,1000,1000,-181,-1000,-497,-15,1000,-737,-92,-15,-916,-302,104,-919,-826,-726,116,470,968,-120,-628,-619,51,1000,-383,40,-141,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$EntrySet", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "entrySet():java.util.Set",
            new int[]{-82,-1000,164,-196,453,-279,-489,175,-507,-291,496,764,800,620,-29,-159,-87,-1000,1000,249,352,-413,-220,-87,-969,482,-1000,51,-562,411,1000,65,513,-1000,915,-674,-641,996,-294,-193,-616,-498,-252,-341,737,-505,144,-649,-91,793,546,217,-137,521,-902,-1000,268,1000,1000,1000,68,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5MA==", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "firstKey():java.lang.Object",
            new int[]{58,427,249,-1000,300,-1000,691,692,-537,466,-500,93,-611,-952,117,708,594,231,-471,893,976,-181,-942,-723,-544,-898,-1000,-879,392,959,451,233,-316,663,1000,1000,-575,-131,503,-547,1000,-1000,-1000,1000,-330,-835,868,502,985,1000,-683,-1000,1000,-412,115,352,406,-20,-115,553,-746,120,-274,502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "firstKey():java.lang.Object",
            new int[]{-177,232,-476,-512,823,-150,203,-243,283,52,-850,981,-830,-627,-555,952,572,-394,39,2,841,-342,-641,-791,-940,-504,447,-783,-755,541,-69,342,-626,-5,826,-989,-472,742,414,46,257,-295,-730,818,-434,-147,-127,-120,903,394,-830,-682,914,127,994,797,849,407,-735,-8,-970,725,-442,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5MA==", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "firstKey():java.lang.Object",
            new int[]{-1000,-943,-336,-486,572,1000,-937,-602,423,-97,686,-1000,205,1000,-258,619,-34,1000,-395,-609,366,707,-1000,729,-968,-602,749,55,-130,-33,786,-1000,1000,-915,-304,-333,-983,-990,-129,112,-895,1000,322,427,-514,64,-283,265,-810,553,-1000,-71,215,649,1000,-548,916,-973,748,-305,1000,-739,-903,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "get(java.lang.Object):java.lang.Object",
            new int[]{843,-203,-576,-988,-52,1000,-313,244,-211,-551,1000,-912,-574,-30,-844,824,349,-1000,-923,-230,-581,654,-117,-377,-1000,-57,-767,655,-3,-200,-999,-294,-1000,857,-318,710,55,-1000,-649,1000,-174,-134,649,726,452,-453,782,-409,-996,-62,275,-745,180,44,-962,722,-974,-106,-375,-426,1000,-357,-22,628}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "get(java.lang.Object):java.lang.Object",
            new int[]{53,840,-426,448,-921,-716,711,-990,514,247,918,133,778,-270,373,-360,-967,375,-87,-816,-289,297,196,38,292,800,-83,449,176,962,811,820,789,285,-153,794,687,-377,59,-976,367,-601,792,-139,-290,786,-198,-510,574,-43,-411,-837,-114,-243,651,-934,794,-735,901,-888,917,-453,-592,481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "get(java.lang.Object):java.lang.Object",
            new int[]{1000,-687,309,6,-1000,218,-320,-817,1000,418,490,400,790,-1000,282,326,-74,-1000,883,651,642,273,384,-1000,1000,-243,-838,-221,1000,-55,-45,142,252,-70,486,615,1000,328,-741,1000,-485,214,-1000,-355,259,-268,-791,-1000,-214,1000,-153,-752,64,1000,-474,116,-716,-439,137,117,207,64,-388,587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$RangeEntryMap", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "headMap(java.lang.Object):java.util.SortedMap",
            new int[]{443,-641,258,267,-621,260,-150,-158,333,-516,341,-446,-801,682,-649,-57,-57,803,381,348,493,-315,-186,153,478,-1000,846,-10,455,956,319,146,-683,-585,-87,400,1000,1000,-746,-217,198,-90,400,582,1000,-492,117,-307,-280,532,96,984,325,730,53,-844,625,-1000,-622,-4,-42,1000,-147,-545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "headMap(java.lang.Object):java.util.SortedMap",
            new int[]{255,258,-42,853,-1000,547,-355,-1000,-388,-935,-1000,-243,512,57,-81,-99,1000,-278,-81,-1000,764,-989,-336,581,-93,-536,-154,302,556,684,-1000,562,-249,-655,-638,-546,-514,-232,-953,-485,654,1000,-1000,578,-287,678,-156,-738,-735,-869,-106,-1000,435,-18,-731,-427,7,-371,-309,814,827,-304,-935,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$RangeEntryMap", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "headMap(java.lang.Object):java.util.SortedMap",
            new int[]{-819,563,329,-103,103,-672,-945,-262,-1000,-531,-354,852,451,-919,1000,398,148,-604,-35,-610,501,-339,-391,418,-276,647,244,-441,-366,-905,-176,82,239,-969,396,-988,-478,-954,-551,-1000,705,340,-1000,-1000,-1000,655,279,879,380,-815,855,763,-945,333,488,-218,-882,318,983,714,-249,-1000,-235,205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$KeySet", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "keySet():java.util.Set",
            new int[]{-1000,-829,-171,-1000,1000,-167,608,-1000,-1000,679,1000,1000,477,1000,1000,289,1000,-971,-804,-292,1000,379,-611,359,1000,-593,-484,1000,456,-1000,-1000,543,-52,-553,-726,-1000,-489,-402,-338,-648,-541,-1000,-1000,1000,-611,1000,-848,-69,366,1000,-1000,1000,-1000,-773,-12,-512,-1000,-1000,-483,410,-241,384,-912,-398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$KeySet", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "keySet():java.util.Set",
            new int[]{-164,-860,-16,-442,486,630,254,-333,-1000,941,1000,907,89,-1000,486,-3,-25,964,-681,-1000,1000,39,137,-246,-1000,-1000,-1000,349,-246,553,-1000,424,-1000,-406,-1000,-726,-683,-1000,-660,-138,-930,-536,-1000,981,-733,717,-1000,-462,-458,761,-771,488,-851,-374,677,-702,1000,-228,-184,119,-919,523,89,-545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$KeySet", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "keySet():java.util.Set",
            new int[]{326,163,-996,-146,-883,206,-170,313,-477,-13,-342,-829,-673,-832,-465,-811,954,-611,-211,-857,120,669,894,-838,427,-147,-898,-621,753,487,423,598,-467,-441,-388,819,-601,-768,909,155,991,893,461,914,237,637,605,-563,919,-342,-538,-325,-106,-104,-328,-593,18,808,-467,512,-501,-883,254,774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5NA==", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "lastKey():java.lang.Object",
            new int[]{-515,661,134,1000,-562,-462,817,-649,298,-196,-404,-141,68,-910,-665,122,119,999,-570,1000,1000,647,-54,353,-1000,-611,653,-156,499,118,171,-931,447,509,-132,1000,586,-72,1000,1000,1000,-384,-1000,-1000,984,773,-669,580,-478,-216,-1000,643,625,1000,-624,270,-744,1000,296,-1000,1000,-83,934,-719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "lastKey():java.lang.Object",
            new int[]{867,226,224,-837,405,238,-742,927,575,245,-870,-109,120,480,-361,119,520,-337,-464,328,880,660,-562,-936,964,-553,355,473,-773,287,346,-602,832,58,-219,-458,829,-811,617,-982,-44,-263,-796,-310,861,828,-793,460,899,-129,-582,-158,165,780,469,38,-110,-266,762,-934,953,956,-728,-559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5NA==", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "lastKey():java.lang.Object",
            new int[]{-1000,-235,-562,164,-14,-673,713,-804,-71,-893,-1000,579,827,-1000,-601,-679,-201,1000,-821,245,354,-476,824,834,-978,248,981,-1000,209,8,68,-1000,244,-394,-220,863,223,908,767,1000,545,708,-513,-703,430,146,-30,137,-835,830,765,864,-452,-136,106,496,-632,1000,950,20,507,-359,248,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$TrieMapIterator", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "mapIterator():org.apache.commons.collections4.OrderedMapIterator",
            new int[]{443,603,719,698,-160,859,1000,217,808,24,699,910,-821,831,-424,342,816,-240,200,697,351,-1000,672,315,1000,-658,111,-1000,1000,-897,1000,-885,700,-1000,905,211,-567,-990,174,-406,-6,-1000,613,-785,-1000,-568,-99,655,61,225,1000,-535,-796,1000,440,1000,197,-906,-363,604,-518,-1000,-312,979}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$TrieMapIterator", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "mapIterator():org.apache.commons.collections4.OrderedMapIterator",
            new int[]{329,-20,964,-392,519,268,211,-867,-236,-247,372,-216,660,534,-925,-414,929,48,-708,461,-570,607,-49,-632,300,-696,-912,-54,-206,-828,727,-964,564,-474,39,832,-797,-330,538,150,698,-177,85,-669,127,5,-722,596,-597,-104,470,-631,-836,229,59,15,994,-858,-987,-278,-888,-895,596,853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$TrieMapIterator", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "mapIterator():org.apache.commons.collections4.OrderedMapIterator",
            new int[]{47,-923,-277,-422,531,-618,719,-888,-994,380,-90,949,-802,129,28,767,67,941,244,1000,52,759,125,762,746,-672,201,-740,-442,-872,321,-334,-129,121,99,-45,235,147,-239,-699,-875,-787,145,-1000,-16,-624,-70,569,-1000,-89,993,-4,466,85,-825,-336,132,119,328,-776,614,3,-436,857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "nextKey(java.lang.Object):java.lang.Object",
            new int[]{904,389,-486,616,467,271,-616,-805,1000,60,140,14,-72,-1000,-604,-312,-965,-543,186,-1000,379,-236,-978,-1000,266,703,340,-584,-1000,-1000,306,-565,831,198,-640,928,528,4,334,56,859,813,991,734,-29,195,-437,-225,455,691,-314,-176,-831,322,-617,-41,1000,1000,-1000,1000,-641,205,133,-390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "nextKey(java.lang.Object):java.lang.Object",
            new int[]{-1000,-8,673,59,-498,165,-649,-305,-383,305,691,292,791,359,-750,603,702,-141,-119,462,768,-837,-742,-895,-818,1000,-396,1000,47,-1000,786,-1000,-1000,380,-552,-769,1000,-548,1000,-163,307,-401,968,-1000,421,-285,721,-328,998,-719,-1000,-613,-725,1000,-933,639,-656,853,-580,959,563,41,-897,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$PrefixRangeMap", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "prefixMap(java.lang.Object):java.util.SortedMap",
            new int[]{-1000,-429,-127,-423,-431,588,-107,-535,1000,-465,-1000,911,-346,-439,-396,-1000,-435,463,26,244,-410,-80,231,-54,566,-329,-350,-490,703,885,992,-374,-142,-728,2,623,-303,627,-1000,-547,286,-16,926,259,-464,326,-276,-284,885,-1000,889,520,50,-286,-134,-342,-249,-350,-907,-540,123,405,479,541}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.PatriciaTrie", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "prefixMap(java.lang.Object):java.util.SortedMap",
            new int[]{-460,-228,-816,-57,-672,442,-697,72,255,-630,51,1000,1000,-722,-811,100,1000,1000,456,372,-638,574,1000,-808,1000,454,435,-1000,173,754,376,-570,-79,-1000,448,298,-36,206,-589,446,496,223,-58,-580,-779,-661,580,254,90,729,380,456,-65,-135,4,-479,-793,-1000,950,-492,-778,-619,832,-780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$PrefixRangeMap", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "prefixMap(java.lang.Object):java.util.SortedMap",
            new int[]{827,97,-927,-79,930,876,-207,-1000,363,-160,-971,640,799,790,-1000,897,922,87,1000,460,-1000,-234,123,-727,1000,1000,-175,-1000,801,1000,-945,-409,-285,-1000,-186,-441,378,-973,-2,1000,371,87,-1000,236,-1000,-1000,1000,1000,-73,1000,170,1000,91,1000,-570,-1000,-109,-881,-1000,-115,-701,130,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "previousKey(java.lang.Object):java.lang.Object",
            new int[]{-88,-641,-401,877,-234,92,1000,27,-806,-526,524,-475,836,-23,520,-997,930,1000,1000,-928,682,-1000,338,870,139,-1000,508,317,1000,-714,-97,-608,-913,374,-298,1000,329,543,392,-1000,-960,-600,-123,-350,340,993,-675,113,587,492,-358,-227,-1000,609,-86,505,-222,262,-208,-22,-95,-175,1000,963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "previousKey(java.lang.Object):java.lang.Object",
            new int[]{110,-684,-102,867,-384,102,186,-700,1000,-111,326,-129,-539,-389,-399,-140,292,-711,348,428,58,676,0,-860,8,172,-26,-200,1000,-630,-912,-807,-209,215,-53,6,475,-57,447,-652,-1000,-211,304,-696,-23,-67,-37,-870,710,18,-375,737,128,219,-1000,1000,-818,529,1000,-323,-430,469,872,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-422,-639,-826,143,49,-48,939,731,-1000,-431,-366,-28,-514,226,-245,-73,-198,414,-1000,774,426,-806,54,651,-1000,-627,-85,-505,186,1000,60,-706,959,1000,-43,-297,-561,237,-38,-272,-445,-1000,214,-1000,-1000,-970,-668,297,-90,-921,-28,844,-274,-295,-6,160,882,-164,546,-569,-1000,-282,2,441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-253,-388,779,-888,75,-329,-379,665,-719,-255,-353,125,82,874,-866,219,891,-328,-10,381,-84,884,-227,416,-653,623,-562,-987,-805,246,-532,-327,-319,-144,906,-545,-63,-511,197,-226,-762,-817,-266,-565,958,336,-474,51,-960,35,-491,-938,-130,-640,671,-333,-851,47,392,158,-392,-619,-831,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-876,349,169,-391,1000,659,509,-1000,-1000,123,-955,1000,819,1000,-1000,-832,756,-88,-1000,-1000,1000,422,-359,-241,630,-476,132,-597,88,901,-349,1000,871,603,-74,-1000,1000,651,-823,457,1000,-608,1000,-1000,254,-139,-979,1000,599,-823,-323,8,1000,1000,-78,-251,-131,1000,1000,839,-171,553,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "remove(java.lang.Object):java.lang.Object",
            new int[]{-271,455,104,71,188,-938,-37,786,-279,1000,450,-858,-211,-494,-1000,-987,734,198,-603,-302,172,-77,159,930,-579,-1000,711,433,1000,923,767,57,96,1000,400,-70,290,-1000,768,-1000,1000,-159,-605,-405,-22,1000,-630,-246,1000,-33,-943,25,231,107,-73,8,-215,587,-279,183,224,-1000,-1000,-897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "remove(java.lang.Object):java.lang.Object",
            new int[]{-251,772,394,-69,-139,280,633,1000,146,670,-207,-870,-38,451,-45,-853,528,-819,-604,578,355,715,-714,1000,369,-959,655,-732,-487,1000,-914,1000,-891,1000,-594,1000,-194,-92,1000,-644,1000,1000,806,179,-275,-661,563,648,605,-1000,-580,-362,69,-1000,480,12,84,294,-1000,-888,-695,290,-500,886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "remove(java.lang.Object):java.lang.Object",
            new int[]{376,359,229,658,-101,-402,-477,665,-70,172,595,-763,-14,-659,1000,-797,373,-764,-1000,321,-70,776,922,891,-1000,-1000,1000,347,87,961,262,1000,-645,1000,-413,150,-385,-209,906,-699,711,1000,461,-1000,31,-655,418,-779,1000,-1000,-1000,360,553,-1000,-1000,-164,-497,1000,-470,-1000,1000,-1000,-959,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "remove(java.lang.Object):java.lang.Object",
            new int[]{-296,485,394,169,906,-1000,-902,-928,-285,-32,-1000,-552,350,-1000,563,644,-777,-431,1000,1000,523,211,1000,457,-99,-522,150,122,616,1000,775,78,-850,-1000,171,-1000,-566,747,-170,-276,85,890,959,-397,786,-139,540,326,-218,-637,-303,210,-314,-560,439,1000,-149,-4,481,175,82,-407,-285,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$TrieEntry", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "select(java.lang.Object):java.util.Map$Entry",
            new int[]{-529,-455,114,681,-204,174,-1000,-618,737,446,706,578,184,-881,377,-103,-642,-1000,515,864,-1000,-261,96,327,66,-1000,1000,1000,-745,-738,134,-743,819,614,1000,-537,-375,-860,-365,401,979,520,-52,287,204,388,-535,497,260,608,0,674,528,-630,180,1000,359,400,317,91,468,955,-124,377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "select(java.lang.Object):java.util.Map$Entry",
            new int[]{-1000,-546,-861,-675,-738,321,122,391,176,573,30,932,1000,-1000,1000,-776,-485,-105,1000,-883,-297,-420,1000,253,-1000,-536,-206,-1000,-697,552,697,234,-1000,-57,1000,1000,-171,293,-1000,-637,-1000,168,-308,-894,1000,-466,-927,1000,-456,1000,1000,-136,-647,-1000,-928,679,-1000,-488,-27,-494,1000,324,-406,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$TrieEntry", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "select(java.lang.Object):java.util.Map$Entry",
            new int[]{1000,-183,744,826,-1000,-961,144,-620,-1000,-1000,1000,1000,-367,877,-37,568,-1000,-79,833,558,-500,693,703,1000,-395,-1000,-181,596,11,230,332,1000,1000,-360,277,862,-1000,-1000,-497,986,122,407,1000,1000,-810,-219,-847,173,-1000,-1000,1000,-351,256,-739,-992,1000,-756,-1000,1000,-470,-782,1000,-1000,365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5MA==", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "selectKey(java.lang.Object):java.lang.Object",
            new int[]{-899,-73,-681,-684,-245,492,1000,-661,-578,167,518,-16,-126,428,-55,26,-400,-374,777,-171,132,-231,21,-1000,651,1000,-429,540,-406,-1000,153,-1000,-1000,-9,350,-370,-952,-226,1000,-481,-1000,-346,-813,-123,-345,-564,-1000,-874,-376,-153,1000,117,151,406,-506,-53,-529,804,-788,29,-279,-1000,-587,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "selectKey(java.lang.Object):java.lang.Object",
            new int[]{-1000,-748,-282,-222,71,837,526,111,521,207,831,-1000,-1000,-152,39,374,-1000,1000,679,-1000,1000,979,727,-337,135,-1000,-97,-11,132,713,319,340,-423,1000,553,-205,38,-172,-1000,367,221,54,505,-73,-293,-516,175,-1000,-620,1000,1000,951,-532,-53,656,-994,-332,306,316,-320,-287,58,-869,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.String:a2V5MQ==", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "selectKey(java.lang.Object):java.lang.Object",
            new int[]{1000,-733,128,807,-365,-4,30,802,-1000,-59,-699,-26,513,1000,63,117,554,75,908,-52,-633,-103,-793,-705,1000,-815,-176,184,-453,376,-485,-629,-196,-205,860,487,142,-20,312,-431,-760,-165,-1000,-439,286,-327,-995,731,43,36,-1000,750,-703,303,282,620,390,144,247,-811,-626,-278,199,303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "selectValue(java.lang.Object):java.lang.Object",
            new int[]{-697,-861,-76,-848,47,46,504,80,-490,-118,314,1000,705,-1000,152,279,-980,-150,-847,-59,127,-413,1000,-603,813,-510,-150,-817,505,1000,-1000,-94,240,-257,-1000,-666,1000,-601,-693,-328,1000,917,1000,255,190,-437,-1000,1000,-528,345,552,-67,-1000,375,509,920,-510,-592,-108,24,-847,-154,-1000,-480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "selectValue(java.lang.Object):java.lang.Object",
            new int[]{304,-868,399,-694,71,-748,-231,-536,-621,787,-936,956,759,-843,-940,835,840,569,-211,202,710,473,669,-228,-804,-405,395,767,-890,106,620,-855,-307,-263,397,397,-354,-727,63,585,-55,720,-205,-854,393,-311,-583,78,329,36,210,565,270,-346,452,-149,-633,-542,198,206,-469,-935,-567,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Double:MTAwLjA=", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "selectValue(java.lang.Object):java.lang.Object",
            new int[]{-344,381,-737,-254,1000,-734,-1000,-213,336,773,-1000,761,278,559,892,61,165,1000,348,-468,-525,458,1000,-1000,1000,-406,1000,769,888,-1000,-453,-199,-452,871,-1000,-975,248,469,-1000,-43,-885,521,353,192,85,-624,608,-719,199,72,-730,794,-505,512,277,-1000,-654,699,-1000,-1000,-1000,117,-481,593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "size():int",
            new int[]{-455,429,-57,448,-600,-1000,-949,-949,-145,-1000,-1000,790,-136,951,1000,-1000,1000,-77,272,-1000,-344,-684,-767,-246,475,-1000,76,-240,81,912,-76,-219,1000,180,-627,434,-656,-1000,692,1000,-408,13,997,805,-1000,-515,-640,-869,16,1000,-254,-214,1000,1000,-1000,-1000,272,1000,688,-1000,-1000,716,-218,-499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "size():int",
            new int[]{275,584,544,-734,-459,117,-1000,-919,-453,-766,-690,-246,1000,234,255,1000,1000,541,1000,-1000,1000,1000,385,-22,1000,-690,-1000,-567,-338,106,1000,-1000,773,788,1000,770,-337,1000,1000,828,-175,664,701,-579,-1000,-1000,201,-137,628,-1000,-605,-195,-167,171,-565,441,-30,884,-313,-1000,-1000,-798,-1000,-303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "size():int",
            new int[]{-121,-441,-896,843,638,-652,-343,-259,-1000,-1000,104,883,549,1000,192,803,593,-367,-178,-898,53,414,32,-229,-147,1000,-669,-463,-138,-408,-505,-361,368,1000,1000,411,-239,922,-310,565,-874,1000,1000,189,-487,-947,-680,-387,1000,227,217,763,-600,577,-924,180,88,1000,-342,-673,-440,134,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$RangeEntryMap", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "subMap(java.lang.Object,java.lang.Object):java.util.SortedMap",
            new int[]{810,-881,124,-724,-358,956,-230,-302,16,-71,183,-11,-68,-663,470,-152,-579,122,596,-93,433,-849,-958,-404,-523,73,-974,-705,-336,-292,752,-884,-525,959,-870,396,-535,-953,814,394,-613,-466,994,34,757,-13,-624,582,-231,469,55,116,625,-948,-437,342,-126,-526,-714,853,128,732,640,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "subMap(java.lang.Object,java.lang.Object):java.util.SortedMap",
            new int[]{-1000,438,-451,-66,309,30,-722,-866,1000,-58,-1000,-1000,1000,-269,153,-104,658,-1000,499,371,359,-512,-695,-169,4,721,-617,-255,-868,-722,-822,-1000,-736,468,212,574,904,173,1000,-102,237,-941,-100,-359,607,-492,499,40,862,-473,568,502,914,48,1000,-1000,-1000,-1000,787,-66,473,433,-78,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "subMap(java.lang.Object,java.lang.Object):java.util.SortedMap",
            new int[]{670,477,14,351,65,525,804,502,101,-1000,-822,-1000,823,1000,733,-341,-694,698,-1000,-958,-175,1000,405,224,-68,1000,167,103,-779,733,-1000,527,343,678,-81,975,466,262,-1000,-27,361,1000,856,-1000,747,660,-125,1000,1000,1000,1000,1000,-385,1000,-672,970,410,1000,-950,-227,521,597,269,-133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$RangeEntryMap", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "subMap(java.lang.Object,java.lang.Object):java.util.SortedMap",
            new int[]{1000,105,124,626,-772,1000,51,1000,-1000,-1000,-243,-290,-68,-663,-1000,-831,-891,-965,951,-198,-996,-251,940,-720,491,-531,34,-1000,834,1000,1000,740,-426,959,-27,-363,-1000,-1000,208,573,-1000,-1000,227,-419,985,-63,-624,257,-1000,-1000,-1000,280,975,-143,-437,-526,-146,680,-617,1000,-1000,131,596,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$RangeEntryMap", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "subMap(java.lang.Object,java.lang.Object):java.util.SortedMap",
            new int[]{1000,339,534,702,-204,1000,735,520,-536,-988,-1000,-1000,599,540,781,-716,-265,874,215,-1000,-83,1000,464,222,-1000,707,1000,272,-804,1000,-416,527,414,414,-94,1000,-89,266,-1000,-523,-845,1000,1000,-716,905,1000,-216,283,722,946,1000,260,-883,964,-1000,1000,1000,829,-441,-80,347,-648,0,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$RangeEntryMap", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "tailMap(java.lang.Object):java.util.SortedMap",
            new int[]{-185,107,-661,141,856,1000,-407,-110,-2,-331,-114,-15,-245,-419,300,-391,-568,-809,-1000,334,836,-1000,649,-677,48,788,-1000,295,697,-1000,511,267,-280,254,-1000,-414,67,605,-1000,-661,-464,9,1000,167,-687,890,-1000,549,309,-73,-39,1000,-1000,580,1000,-1000,38,-573,-327,619,401,-223,-1000,-299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "tailMap(java.lang.Object):java.util.SortedMap",
            new int[]{136,-280,408,1000,-381,342,-910,779,37,678,863,-70,245,-1000,1000,573,1000,801,-741,12,-413,-1000,1000,1000,1000,-162,-1000,-372,181,661,1000,210,-467,-729,513,-57,-91,392,-839,785,-93,1000,1000,-318,-971,-737,-18,-24,151,1000,687,6,-1000,324,-633,581,0,1000,758,1000,-716,440,-914,192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$Values", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "values():java.util.Collection",
            new int[]{975,-515,354,-172,-450,939,947,-783,-923,1000,1000,-1000,-100,-938,302,633,336,510,-163,497,309,555,-554,240,-1000,1000,-941,111,-38,844,396,557,-509,1000,1000,1000,-269,1000,1000,-1000,741,-753,-1000,1000,-1000,-1000,1000,-32,-80,955,-1000,-587,567,20,388,-828,918,-814,830,384,374,63,489,-102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$Values", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "values():java.util.Collection",
            new int[]{303,-766,228,111,497,241,-860,948,417,-795,534,-350,836,189,558,367,753,660,-323,-623,531,170,-40,947,-400,-257,-245,370,-51,-838,-222,-644,-305,-611,827,-666,685,471,973,580,319,-540,-642,-433,535,95,541,-582,288,318,-294,972,-292,-967,975,566,649,-41,608,-636,-667,-95,-123,-161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections4.trie.AbstractPatriciaTrie$Values", DEReplay.run(
            "org.apache.commons.collections4.trie.AbstractPatriciaTrie", "org.apache.commons.collections4.trie.PatriciaTrie", "values():java.util.Collection",
            new int[]{-81,-485,354,829,-69,718,1000,1000,88,-319,1000,1000,982,687,1000,548,-1000,1000,-1000,1000,1000,-1000,783,314,657,1000,-1000,985,1000,-1000,-1000,1000,-1000,-305,-1000,1000,131,-296,1000,1000,-291,1000,-1000,1000,-1000,-1000,1000,960,-1000,538,-862,1000,-127,-1000,-1000,1000,1000,1000,-1000,-352,1000,400,-255,-1000}));
    }
}
