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
            new int[]{-1000,-468,173,1000,-1000,-1000,455,672,-205,-3,-551,1000,925,-1000,-524,-428,-61,297,-360,623,-157,-47,1000,1000,-372,-1000,-931,-825,155,172,352,-114,385,658,760,272,262,768,-1000,295,-1000,1000,-1000,761,-264,61,41,-296,-443,-552,1000,-45,-915,278,-383,-1000,435,894,17,-419,103,642,-112,722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "addProperty(java.lang.String,java.lang.Object):void",
            new int[]{253,-690,348,-179,-332,-128,-274,-923,665,317,363,-23,1000,-80,451,1000,-198,-1000,-748,-570,704,696,150,1000,1000,529,556,295,266,-1000,-1000,66,1000,-1000,-1000,674,485,-1000,1000,-245,1000,-1000,-174,1000,939,-343,-1,160,178,459,-939,92,400,157,219,350,-748,-445,609,-889,117,-1000,840,-541}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{-735,54,99,-1000,-1000,-1000,-184,504,-696,1000,-1000,1000,-1000,-1000,-71,1000,385,1000,1000,-1000,-938,-1000,-1000,-1000,1000,66,-337,682,-146,524,955,-888,-451,-747,-397,782,359,-1000,-320,-1000,-676,1000,1000,-1000,-920,195,1000,1000,-1000,1000,-1000,-691,1000,-95,1000,-400,-52,1000,1000,86,-1000,-1000,1000,-854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{-862,-453,-541,-374,364,-489,-1000,-513,-319,-674,-441,718,368,192,-400,504,867,-92,673,-529,599,115,227,-848,-627,257,-560,407,1000,-478,877,152,-846,-506,-100,-611,473,660,-736,594,68,408,888,-1000,-632,392,-65,166,-485,769,-1000,-699,767,-818,-150,542,-365,598,766,276,786,-689,931,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{-1000,-285,-121,-689,-129,-589,-886,143,823,700,-128,1000,-704,-284,-1000,712,398,400,975,-279,557,1000,627,-103,1000,-722,-640,418,1000,-217,857,-891,-867,-560,-377,500,1000,-1000,66,-1000,188,1000,668,-400,-565,-626,859,981,-889,1000,-1000,-411,814,-275,-1000,530,-508,1000,1000,1000,-101,-373,970,-76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{-115,-906,913,-464,-865,-170,55,-185,-624,1000,-642,-573,74,-1000,490,332,-774,1000,-1000,765,1000,-299,-28,-901,-1000,1000,-159,330,493,-364,-79,131,816,270,253,20,-178,165,655,474,701,396,-852,1000,-664,516,788,644,270,-778,1000,268,161,1000,-422,-601,-417,-12,1000,166,984,-348,-178,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{458,120,-683,-766,851,260,1000,-592,348,1000,384,-1000,848,-562,1000,225,1000,-1000,1000,-931,-208,-1000,1000,34,1000,-101,-1000,314,-1000,-567,-822,-649,1000,-1000,714,231,929,-1000,1000,645,-303,-1000,-785,509,692,1000,1000,-29,432,-561,460,-1000,-145,369,-464,-1000,622,-291,-303,576,-644,-537,-1000,619}));
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
            new int[]{596,804,243,-822,3,1000,215,1000,818,378,-685,1000,-483,-244,-128,1000,-72,-1000,-1000,-1,592,-1000,-1000,621,-462,406,-877,-110,443,230,1000,30,932,-405,658,1000,-755,-552,928,262,-423,-331,-1000,-71,-436,1000,-1000,1000,-1000,-372,-437,-1000,-798,-1000,-598,-400,-326,-487,-263,-61,-1000,277,1000,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "display():void",
            new int[]{666,120,-526,-1000,152,-476,-668,-389,1000,1000,-976,791,-250,137,-327,-460,664,-685,588,-1000,1000,-664,479,-966,166,1000,-1000,1000,1000,355,67,-427,580,864,-153,160,-1000,-152,-164,621,1000,-64,1000,368,-963,-63,-1000,928,-662,-252,-676,178,-331,267,-878,480,-179,-486,-306,-376,675,262,699,-952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "display():void",
            new int[]{1000,-9,-821,-624,-263,872,-400,743,1000,1000,-698,288,-227,-199,-714,-460,-399,-1000,1000,-1000,1000,-664,-1000,-966,643,1000,-1000,1000,-943,964,993,-1000,667,1000,-1000,970,514,-1000,248,344,819,1000,1000,717,-1000,1000,-1000,550,-215,-21,770,-1000,-105,819,-1000,-443,-983,-881,-703,-1000,1000,699,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String):boolean",
            new int[]{269,255,182,-752,399,985,9,518,-573,263,-995,-592,969,-696,391,336,-898,890,676,936,865,-814,-193,487,-153,-622,755,829,373,-938,-763,-266,406,-780,-393,586,-95,-909,-609,-223,103,291,419,242,-84,-888,-941,221,543,95,-334,505,199,709,42,-824,-123,215,-585,179,59,-313,293,927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String):boolean",
            new int[]{715,-459,-162,118,368,790,52,726,-142,-511,-374,254,113,-452,-682,386,-108,-636,1000,1000,533,-29,64,893,-514,-462,544,466,107,-1000,-694,181,864,-160,-832,-104,-275,-983,-1000,-538,-56,629,825,-488,400,21,-1000,854,-275,732,384,176,-460,1000,-478,73,-29,760,29,288,-314,-596,-140,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{-133,261,-146,-278,-800,-1000,469,-1000,440,1000,-1000,-1000,817,-907,42,-601,549,-134,-131,-983,-203,-154,-1000,212,-756,-160,113,1000,-1000,555,618,762,-1000,1000,42,1000,-202,-101,990,-942,1000,-1000,1000,311,477,-63,-269,1000,-136,101,-1000,-967,-1000,1000,-829,497,51,-1000,1000,192,-230,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{1000,414,-358,-1000,-1000,-816,690,-1000,993,580,-1000,-1000,-786,-1000,-1000,1000,1000,1000,-983,-1000,34,-361,797,754,-912,-810,-1000,116,-1000,-1000,1000,164,-1000,114,1000,-990,-247,-1000,-1000,-1000,-1000,895,-568,-765,-330,-1000,-1000,87,778,-669,-744,-898,531,1000,-1000,-197,-478,-1000,1000,-967,222,-104,1000,131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{1000,-264,-311,-488,-806,492,756,-293,395,408,-260,-245,-673,1000,-1000,343,-20,-738,13,844,-556,1000,367,1000,-900,-331,700,-963,-1000,496,-1000,1000,910,1000,-641,-594,-946,-931,-340,-1000,-752,112,634,1000,-80,-1000,-375,-1000,-485,-611,1000,-982,277,786,586,-584,-650,868,863,188,-696,1000,-753,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{-862,-573,-443,522,-782,1000,-344,995,587,497,-174,633,118,-1000,450,-1000,-57,-470,1000,422,557,1000,-428,334,-1000,596,99,787,-248,-389,869,-675,26,701,916,-577,-653,-137,1000,-48,-674,-980,225,-558,758,336,-726,416,832,-282,-1000,757,-882,274,-191,269,-1000,-688,464,924,301,447,-920,583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{-135,84,384,1000,-103,1000,-614,404,381,-208,1000,645,-831,-696,370,-1000,-96,159,43,3,1000,-68,-865,558,762,-155,-1000,-378,155,510,98,-37,-1000,-399,1000,-820,312,-365,-636,-1000,-641,-1000,-1000,852,-163,1000,-1000,485,1000,1000,206,14,-325,-721,-387,-254,-1000,510,843,1000,350,-939,-729,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{20,366,-211,876,625,-509,58,1000,-224,732,110,726,-473,-1000,-26,-1000,-96,159,-257,-406,-956,41,727,408,198,-653,84,-417,-876,421,372,-1,-819,-531,-529,317,-383,84,-636,-897,-641,-71,222,1000,-461,614,-708,1000,853,1000,-1000,39,-811,-1000,-213,775,-730,510,1000,580,-425,-1000,-502,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{-720,-399,863,784,-976,816,-483,-76,476,-454,407,-104,-244,291,684,-1000,-377,-194,-82,885,790,-929,237,440,-35,333,-49,1000,169,-687,147,124,-445,698,1000,-404,991,6,737,-757,-1000,-1000,644,-6,605,-307,-757,-667,274,-487,-452,596,89,394,-176,3,-491,-78,406,1000,791,193,486,657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{-41,-675,-492,-277,52,-696,731,210,-997,547,-670,120,-482,405,-728,-668,-552,-269,-766,923,-824,-725,-807,-257,-776,-231,-836,727,463,-305,21,58,-408,747,-791,106,428,-920,-469,-84,-996,575,-813,956,-530,560,-237,802,330,700,-23,-485,733,80,624,539,151,-82,416,169,99,-209,-841,825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{-1000,-858,383,852,184,-767,1000,1000,166,291,2,180,936,1000,1000,-832,-1000,527,-294,543,-706,320,1000,-11,1000,-283,1000,-336,1000,-614,-483,-1000,640,-1000,336,-1000,932,1000,-1000,-977,857,-1000,1000,-155,-206,-722,-694,-81,-881,810,80,-352,1000,-920,-340,1000,215,242,-1000,1000,1000,-161,-106,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{-16,-552,752,1000,-336,-390,827,678,740,-56,751,-1000,179,948,1000,548,-671,928,733,-847,15,-601,-329,1000,1000,-598,1000,-333,1000,-355,350,-607,899,-536,-263,272,-220,1000,-314,-275,1000,-1000,1000,-1000,71,47,203,-261,-620,-25,-385,-192,-360,-621,65,964,216,473,-608,-89,1000,601,666,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{0,-294,-629,71,-479,-81,149,-1000,990,625,-147,-603,546,-1000,494,796,-384,388,190,584,-1000,1000,483,-1000,978,595,-230,1000,-1000,-182,-449,566,520,-53,-889,-41,524,636,-899,522,-632,79,971,1000,882,-325,-804,-925,952,1000,-308,-943,409,903,380,327,18,-1000,-1000,-339,1000,-282,503,-427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{-154,-525,124,-978,-171,-776,-394,318,-175,410,592,1000,617,134,-504,-817,1000,1000,1000,-600,-683,136,1000,72,-559,-225,120,-1000,-578,-85,417,-312,-115,-1000,1000,-353,-128,941,370,-10,-117,346,-86,-1000,-250,1000,361,-1000,-322,-1000,922,87,-327,-325,-1000,804,-478,347,-341,409,550,470,842,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{-90,-120,-269,-481,-271,-1000,-597,374,80,225,1000,1000,522,-373,78,-863,801,1000,531,-881,-577,620,1000,-480,-1000,-8,149,-988,-481,530,50,-263,-102,-112,796,-430,303,152,302,-767,279,-232,-871,-1000,-520,317,85,-1000,204,-946,1000,-489,459,-991,-691,721,409,-88,339,524,1000,-816,509,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{365,150,488,1000,-528,-492,-1000,1000,-60,-382,948,155,1000,-1000,-311,-425,-294,835,2,-113,896,1000,-356,450,-972,1000,663,-678,169,934,-1000,1000,-1000,-667,-286,729,1000,782,-1000,-1000,1000,-733,-896,-57,-70,-1000,-139,-292,-343,1000,652,-871,-606,-1000,668,1000,-104,-538,1000,-110,-1000,-456,-1000,937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{576,960,-446,-409,-871,-996,32,-989,-701,734,604,405,412,311,292,418,-737,843,254,176,547,602,-820,292,-724,-501,252,761,142,606,473,621,-955,934,880,481,-185,-770,572,314,544,98,342,-719,868,-567,-791,-217,58,-172,-862,392,-182,397,-116,-86,-643,-718,-999,-612,-558,-983,-177,552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Byte:MQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{88,951,944,-176,-112,-679,1000,111,-741,487,-1000,168,-1000,120,108,1000,-161,-743,-648,-403,-1000,-278,240,146,776,51,-424,-994,-1000,-283,-415,922,512,885,366,746,432,-460,-176,600,439,837,-185,378,121,-449,-610,-536,-911,691,353,-472,-493,-470,1000,-265,136,-724,-101,-577,518,759,416,-72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String):double",
            new int[]{359,537,-957,786,-869,-44,-708,-1000,-1000,230,431,193,958,-171,308,-1000,-342,-245,296,567,1000,-1000,182,385,-1000,-130,1000,363,-449,1000,318,-29,-1000,341,-1000,1000,-504,1000,-1000,467,-281,1000,123,-691,1000,1000,-1000,945,610,-338,-173,65,-1000,-1000,1000,208,362,-272,-51,-92,-1000,-445,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String):double",
            new int[]{1000,696,798,1000,9,266,941,-1000,-218,448,888,-680,-720,-538,-486,711,321,686,161,-489,812,-532,-839,-189,36,454,73,496,-302,488,773,-336,629,285,823,-20,-207,883,-1000,344,846,-87,-91,-887,-1000,-706,670,1000,388,-126,880,-307,-558,-579,427,-15,549,-1000,350,363,-631,1000,-263,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{837,399,-632,-852,125,693,358,128,467,-698,725,338,-181,221,-537,-376,-20,961,-618,-866,-661,35,-255,-567,439,82,74,981,5,696,-708,-37,91,-103,627,677,992,-666,779,323,-755,-150,-248,890,-386,808,-583,94,298,712,412,809,-244,86,293,299,-461,317,988,694,308,236,552,617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{-229,345,759,-396,292,1000,900,-130,-428,992,-436,-331,186,94,-1000,-294,1000,-995,585,-520,1000,927,929,845,1000,777,332,601,-1000,-1000,436,162,-258,-230,-658,-122,411,1000,-178,253,60,-1000,-38,404,632,111,681,-874,-786,-1000,-747,205,1000,-478,741,-1000,911,1000,-11,-444,1000,-385,-1000,301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQ2Mi4w", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{157,-279,624,-122,608,407,-69,1000,-1000,-464,982,-1000,-621,-708,300,104,1000,-621,437,102,173,866,-462,-421,707,36,831,290,628,610,-1000,-1000,-1000,380,-617,215,190,223,-441,641,-772,-142,453,540,-1000,414,-631,527,-582,-343,177,1000,602,-897,-26,-1000,-1000,108,960,-119,214,-581,30,-275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{459,-180,-341,-145,471,-896,-144,-253,-368,140,696,298,-650,-158,827,415,-695,989,-655,-465,971,457,-857,-904,-140,979,-206,389,-567,777,29,-245,941,429,-324,879,348,-605,-625,-186,-83,-218,-693,-718,33,136,-245,777,422,-699,371,55,-954,-266,-281,-282,-376,-956,-154,-270,-988,-19,446,-994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{1000,-240,-307,-432,630,1000,668,1000,-214,1000,-16,-517,763,-44,464,-551,-1000,945,-1000,112,1000,342,-375,-26,214,-105,696,1000,326,-344,366,511,1000,-927,-821,478,-281,136,211,-177,-1000,-330,-791,1000,-403,236,742,1000,-435,-290,-499,526,-840,-1000,247,-651,-110,-643,-322,-533,545,-417,284,-332}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{-50,-498,-22,-1000,-318,1000,473,597,-378,-302,-949,-1000,150,152,1000,-137,485,1000,-1000,-1000,1000,1000,-1000,-1000,-1000,638,360,590,-771,336,386,125,1000,1000,-20,872,-1000,538,170,-280,-602,-279,-770,1000,-38,114,1000,-727,-926,-1000,-1000,-96,-432,-763,-882,-1000,319,-10,-898,-619,1000,-790,803,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{-431,702,-567,-750,-600,-1000,-1000,1000,1000,-1000,-643,1000,1000,1000,1000,288,907,-44,1000,-1000,387,-525,-662,598,1000,-33,702,659,256,986,-1000,251,-703,-1000,-1000,1000,1000,521,1000,604,698,1000,271,256,1000,875,1000,549,-603,-1000,-615,1000,1000,-879,485,1000,-260,-1000,-1000,494,-1000,820,1000,475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{-1000,-150,514,1000,-1000,-1000,816,-590,-322,915,251,305,-1000,-478,-599,-1000,1000,775,-1000,1000,391,-1000,1000,-141,485,1000,217,204,-253,-77,1000,1000,1000,1000,1000,745,1000,-43,1000,-1000,856,-1000,235,457,-15,1000,-1000,116,-387,1000,-1000,488,-1000,846,764,-381,-821,1000,1000,-1000,-1000,1000,1000,-718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{-116,309,999,-601,-575,666,-74,920,-155,-27,-955,656,147,-388,445,-586,374,469,400,1,1000,-812,971,624,123,203,643,-716,-729,-60,-754,580,1000,400,-654,-34,436,742,571,-1000,387,-1000,371,1000,-562,514,47,-525,757,-580,-1000,266,311,27,-821,212,-76,-446,-216,-783,788,-79,888,-275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{-862,-804,139,1000,-1000,-665,-355,507,359,-221,-400,-805,-1000,508,48,445,356,-325,-605,428,622,-1000,40,-60,1000,356,-1000,-573,747,1000,-808,1000,-343,-131,264,797,-357,1000,-67,923,1000,-1000,193,-82,-88,-213,-408,66,316,-1000,1000,-484,671,-600,-197,-6,354,-844,-972,-1000,399,102,-338,-265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{-961,258,302,713,-510,185,1000,-94,-876,1000,90,-901,725,146,803,230,1000,-302,-1000,281,-1000,740,433,1000,346,313,128,-836,-1000,-128,110,302,-356,-86,414,-400,571,614,178,65,-41,-255,148,436,-142,-835,-1000,458,542,-1000,-1000,-654,676,-720,617,299,832,-785,1000,-132,-127,-1000,309,180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{-912,-696,697,912,424,140,509,1000,252,1000,-400,172,-1000,135,375,-478,1000,-1000,-655,306,-630,1000,855,-49,422,-794,633,-1000,-1000,-1000,1000,228,-1000,-1000,544,-1000,495,842,185,-178,-80,-609,-756,955,769,-1000,-1000,1000,205,-280,-779,20,455,361,514,-36,676,-662,527,971,-868,-1000,364,-244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{-445,-219,-526,190,-428,-146,-909,-793,14,1000,-773,-730,19,-648,579,52,726,-263,358,124,233,990,-976,-441,759,616,-539,-563,-767,852,-1000,18,-78,-829,1000,-477,491,-46,-853,-53,-370,64,167,81,-1000,-626,689,-1000,1000,104,-304,471,546,-477,1000,999,-62,-485,-865,-587,-688,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEwMC4w", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{50,399,507,-428,640,136,992,-793,-35,-27,-223,1000,-3,120,-218,816,1000,-1000,358,124,233,-1000,-742,-441,-12,616,-618,-731,-36,-239,-176,18,160,1000,-146,-643,-74,-377,-544,798,-904,-229,167,-826,11,-844,666,1000,325,-194,-304,528,913,1000,-1000,-710,-307,-676,-235,-1000,-297,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{-221,-375,954,1000,43,695,1000,-1000,1000,-400,-400,-558,-361,1000,148,-1000,259,-706,-533,-1000,-188,669,813,-718,817,-1000,-318,-330,-877,-1000,798,-1000,-1000,280,-456,286,-497,572,-189,235,332,-1000,-459,1000,484,647,274,-540,-313,1000,1000,-273,503,1000,556,295,976,373,-1000,-572,-908,-89,-485,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{349,-801,954,1000,-1000,-1000,-1000,-38,1000,-1000,-17,-1000,-1000,-937,168,-553,62,-925,-512,1000,-111,1000,-282,1000,-26,-644,1000,-443,1000,-1000,1000,-1000,-940,1000,-701,1000,1000,524,-189,318,863,-1000,-1000,1000,1000,907,158,-1000,-1000,1000,-113,1000,-492,-1000,616,-1000,862,183,713,1000,1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.String:aW5jbHVkZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{-13,309,-36,-724,-677,433,779,505,-590,-842,-966,692,-303,174,-301,664,802,203,484,-445,320,-867,-767,399,-90,83,-725,279,504,614,294,780,-402,-331,986,649,-730,197,938,-293,-149,874,979,-703,-61,-145,-411,-124,882,-927,-132,682,743,-767,-983,-132,-705,-627,351,-51,-349,609,416,992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{375,540,223,425,461,1000,1000,1000,1000,724,119,271,83,-434,-1000,-531,608,285,-103,-44,1000,118,-814,179,1000,1000,537,888,-1000,1000,621,-1000,-1000,78,-583,-506,265,314,793,128,-897,848,-481,41,1000,-562,282,1000,838,-1000,339,-1000,-748,-980,687,396,303,-256,-859,-27,-1000,-482,-552,648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{314,459,154,233,998,363,901,796,977,610,-314,80,-94,-377,-981,-520,-6,332,-302,-86,641,-464,-244,78,861,463,-121,318,-869,980,667,-741,-703,422,-608,-551,-805,184,213,-123,-197,859,219,-486,855,12,128,754,138,-561,968,-996,-48,-822,679,577,-397,-539,-830,-488,-800,-693,-876,969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{-790,-354,382,1000,-287,1000,-1000,161,289,-438,-344,-1000,4,33,419,880,1000,1000,-1000,1000,1000,-1000,-1000,-911,1000,-484,1000,477,-1000,1000,1000,-741,318,1000,792,-1000,-618,1000,823,323,-1000,-386,-495,-1000,-545,-1000,1000,1000,1000,-848,-393,1000,-1000,-1000,-721,-823,333,-1000,-125,912,-1000,-1000,-480,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{-211,-399,828,-397,-878,-263,416,-638,-1000,-710,272,560,-286,-340,670,263,-85,798,-490,331,128,157,-564,850,-622,134,-643,-295,1000,-258,-802,563,-362,556,1000,-75,-788,1000,-384,1000,42,300,115,312,-236,54,170,-893,-1000,118,570,115,-633,-420,-363,812,-741,246,1000,-859,-427,118,-531,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{-197,204,-421,353,-1000,-360,1000,-556,1000,-137,-1000,-1000,738,-204,-535,165,-1000,-859,-475,-373,1000,243,174,1000,585,-103,-143,-783,492,-65,1000,269,-224,-990,1000,-1000,518,-299,-775,-207,-14,-273,194,-1000,-885,696,-546,387,918,-1000,248,-440,848,1000,-337,1000,-658,288,-770,-1000,-79,-254,773,-114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{-965,264,-568,95,-399,65,-534,-304,-886,532,-33,181,-386,429,-166,553,-322,951,653,874,468,963,-909,704,188,-401,-201,-558,-598,-285,-547,-431,8,-229,106,-683,-167,-818,-673,-878,811,900,-273,366,-546,-943,-543,-613,-23,867,-19,500,823,651,22,858,574,-121,961,-597,-724,385,-834,-153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{-1000,213,-627,-582,-303,890,1000,-196,143,819,454,-72,-771,-211,228,-318,-750,1000,190,605,486,425,-400,323,-788,-73,96,-391,-680,-60,-357,650,65,-1000,-29,-995,95,-303,-861,-365,-5,730,-713,1000,-350,722,-584,-995,723,675,400,646,-79,426,1000,120,-1000,1000,893,-55,-315,797,-901,-905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{1000,-312,619,39,872,-911,771,652,256,570,776,646,339,-869,862,138,-742,-92,-1000,765,1000,-878,-109,-639,-223,-298,-787,1000,863,1000,740,394,803,910,225,-787,-273,428,1000,-340,-1000,-1000,-772,-29,-1000,193,-470,387,-1000,-1000,-381,630,339,-1000,-1000,32,1000,1000,15,1000,600,-1000,1000,-692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{309,72,-577,914,-177,-104,994,-97,-418,-331,761,-549,184,-801,44,1000,1000,-1000,-864,531,-549,-436,380,-211,-400,-59,1000,439,671,-587,-225,-664,140,629,400,-497,262,-340,-352,-614,1000,1000,-997,-413,1000,-263,815,563,42,1000,-134,-92,1000,452,1000,-1000,-1000,-224,-145,-343,225,-123,-856,-270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{-856,-435,749,-222,-1000,769,234,-1000,-403,-361,-1000,1000,-643,1000,976,-1000,-13,1000,375,23,11,-980,-556,-1000,399,1000,126,-329,-258,-1000,-400,-756,866,-1000,-1000,-414,-1000,-312,361,708,-312,-1000,936,49,1000,915,841,1000,-594,-151,-385,226,-642,-679,-1000,947,1000,743,-1000,198,420,-1000,-222,873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{55,240,566,-319,-1000,1000,932,-1000,250,230,-1000,1000,-1000,1000,1000,-1000,488,583,-965,-1000,-923,-436,239,1000,1000,1000,-658,-231,-25,-933,1000,-848,-1,-21,75,-838,-1000,-999,1000,-339,611,-553,622,160,778,-149,1000,1000,877,-683,-829,-496,-1000,-1000,1000,-434,-619,958,-311,-289,922,-1000,-634,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,java.lang.Integer):java.lang.Integer",
            new int[]{-1000,-696,-911,160,-1000,-441,-1000,1000,1000,-240,443,19,-431,707,299,-1000,1000,-1000,157,468,-633,-1000,-1000,393,1000,1000,-1000,-985,1000,-374,-1000,1000,814,-1000,-191,-1000,-141,1000,-1000,-1000,788,-275,-310,-875,1000,-956,-415,1000,1000,-1000,-1000,-1000,278,-266,-1000,-744,112,-1000,1000,-1000,272,-1000,-444,-770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,java.lang.Integer):java.lang.Integer",
            new int[]{701,564,34,858,-747,-685,-414,-302,-177,694,421,-787,-57,899,576,290,-1000,630,-1000,728,290,66,130,1000,-581,-1000,374,1000,-135,-112,-263,19,-590,-289,-801,1000,689,-45,1000,228,351,1000,1000,1000,-1000,125,-781,-712,-501,732,-1000,921,352,-348,1000,-147,-40,34,-1000,-329,433,1000,723,614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys():java.util.Iterator",
            new int[]{1000,264,-236,-264,-196,1000,-726,575,680,-194,1000,815,1000,-660,-165,1000,1000,359,193,1000,-1000,-288,-1000,-61,1000,78,-1000,424,252,-787,-383,514,263,-450,-1000,1000,1000,593,-138,-187,1000,-905,1000,652,-197,1000,794,221,138,1000,-738,-1000,-908,-229,-1000,1000,-828,-924,358,607,1000,-131,-617,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys():java.util.Iterator",
            new int[]{-1000,-465,464,5,-245,-243,1000,-1000,-1000,-424,1000,-293,-1000,945,99,46,-206,-528,822,141,-745,309,-237,-802,879,837,489,-222,152,-600,-833,1000,770,-35,591,140,-1000,-468,-433,-210,-594,-410,429,-1000,-84,-38,1000,-1000,-78,748,310,945,944,-1000,-669,-850,-57,362,975,-1000,-152,450,-388,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys():java.util.Iterator",
            new int[]{1000,-381,-622,-680,-601,1000,-400,-1000,296,-616,1000,-400,134,641,-464,731,1000,467,1000,103,-380,-572,-1000,486,321,-798,-359,992,1000,95,332,226,-202,-759,-656,124,20,-165,710,-194,716,-353,620,-335,-374,1000,-400,882,-107,1000,339,-793,-379,400,-187,774,-213,-1000,-400,-117,566,-1000,-1000,358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{-229,402,-2,370,220,-345,-653,-1000,-643,-1000,-1000,-837,-1000,-404,953,-1000,-629,859,-831,1000,806,-54,-193,89,-276,1000,466,-256,-1000,1000,-920,-1000,216,-1000,636,-1000,-961,96,-128,-736,1000,773,-17,1000,-71,1000,1000,-677,-363,1000,-1000,808,-1000,-883,1000,452,-1000,-1000,-1000,1000,-1000,936,808,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{323,249,-567,88,1000,1000,-621,-657,-754,-777,101,1000,-1000,-121,-400,-877,-895,-403,-745,717,-151,-1000,-193,967,-334,792,-289,-369,-779,974,-845,-160,318,-292,754,-646,-399,224,-472,248,434,93,-502,902,448,418,173,-1000,-537,1000,-875,132,-1000,201,658,-948,-278,-1000,-725,499,-850,-133,1000,-809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{1000,-747,-236,-729,-1000,521,923,1000,201,660,1000,371,1000,736,185,-446,-854,-192,-502,704,-1000,-689,910,969,-1000,-808,425,-657,209,-400,-751,399,791,240,1000,6,1000,-648,-886,697,-924,-411,-400,-873,-20,-583,-459,440,-980,406,-1000,-872,468,1000,-702,-1000,-726,1000,-382,387,875,-729,1000,-236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String):java.util.List",
            new int[]{775,-444,329,-795,228,1000,1000,450,79,-124,934,-529,-911,1000,712,-415,154,1000,110,-967,-1000,-1000,1000,-749,-1000,-1000,-209,-1000,701,31,-1000,-111,-1000,198,-379,59,625,-1000,1000,1000,524,1000,-176,-566,739,213,648,702,-1000,993,-1000,57,151,1000,1000,-720,-158,1000,37,1000,-843,-864,-554,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String):java.util.List",
            new int[]{1000,-96,637,683,707,-72,177,-1000,681,1000,-543,100,926,-930,-1000,-513,886,701,1000,-1000,-825,-320,490,-888,-1000,206,-1000,-924,-260,726,-12,-355,181,1000,-977,974,1000,-1000,-607,1000,1000,29,-1000,494,671,356,-46,1000,-68,395,613,674,-658,-418,-94,-1000,887,1000,-1000,396,-1000,-162,-837,130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{-269,546,959,-823,638,-263,-1000,-1000,313,-1000,1000,-1000,-1000,-224,1000,-654,-95,1000,-291,1000,149,-1000,1000,1000,784,1000,145,-1000,-61,306,-1000,1000,243,1000,1000,-1000,-1000,-944,-855,-227,1000,-1000,-1000,-1,-869,-244,-617,-1000,-23,569,1000,-486,-1000,1000,-1000,-584,796,1000,890,1000,-284,-614,-758,705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{787,672,809,-546,1000,-714,-408,-1000,1000,-83,1000,-1000,-1000,-140,1000,-427,728,783,-1000,314,-1000,-1000,707,1000,1000,260,418,495,-1000,737,-1000,1000,-26,-288,-1000,-1000,-1000,221,596,635,1000,-1000,1000,-1000,209,365,-1000,-1000,-303,1000,1000,-792,-1000,1000,-1000,-823,-32,1000,1000,1000,-568,848,-488,447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{-644,-498,-576,1000,604,970,548,90,199,-415,-550,699,-726,-1000,-1000,196,752,-823,-786,-470,-679,-613,-813,-214,213,-1000,466,-405,-1000,-704,-485,-1000,114,-68,430,-350,460,104,-46,-836,-1000,-851,-352,-530,-702,-1000,371,798,511,532,854,-275,1000,-343,-506,-401,561,-273,1000,-139,-435,611,-191,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{-54,-360,4,-247,970,57,-954,-1000,-886,1000,952,-903,787,-49,-328,1000,1000,-1000,-269,160,-359,-1000,71,499,-1000,-1000,-936,-761,-678,-1000,277,-564,-1000,-506,-1000,328,470,-1000,-414,517,1000,1000,895,-45,253,390,-319,993,-93,-1000,884,1000,-488,983,-1000,-688,-1000,-788,782,-1000,-570,-290,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{506,-135,559,-180,-594,-1000,-833,386,132,-622,1000,474,-571,-1000,87,1000,1000,-146,-378,365,-1000,-610,-164,1000,-1000,-294,905,593,-414,-660,-635,976,-639,-260,-63,-95,-1000,-1000,835,892,48,1000,-276,-941,633,-250,-1000,-1000,-939,-987,-418,-297,-78,541,-1000,-399,-1000,-1000,1000,-1000,-1000,1000,182,609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{-1000,-477,-257,208,576,-575,1000,-407,1000,-1000,854,-1000,151,-961,-1000,62,1000,-113,1000,-283,1000,760,721,1000,-1000,1000,1000,191,1000,843,495,625,1000,824,738,-1000,-1000,-691,431,-307,-1000,-1000,1000,-1000,767,173,178,-252,-1000,774,-536,-1000,-737,680,-1000,1000,-722,-848,709,-675,-1000,559,404,-323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{440,-165,-401,-968,487,277,681,-997,391,-233,-774,160,778,425,-578,307,-853,883,-430,995,-505,870,-237,-757,-508,-669,-886,-492,-367,327,-19,979,-228,687,764,431,255,482,812,333,-540,-286,727,-342,959,326,923,968,-241,-377,699,-283,125,-501,-746,450,-682,-513,-893,-426,270,574,-793,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{218,-432,154,271,180,-886,901,-345,777,856,368,-137,1000,750,-189,-175,-408,193,-573,1000,-209,1000,-21,-852,-380,299,-198,451,-561,-222,48,777,1000,-1000,744,115,476,-353,498,28,744,-400,599,-349,1000,-280,686,255,-671,-349,121,196,190,-581,-992,-576,-241,-200,-582,-320,400,-363,412,-289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{-706,-705,-907,-228,-211,929,228,1000,37,-1000,808,510,223,104,-338,606,-202,407,29,-1000,1000,-1000,7,649,612,224,-868,100,-539,250,-665,-1000,481,427,-11,1,-559,706,550,-1000,887,82,552,-56,-1000,-359,-298,-1000,-450,-646,250,74,-1000,-101,728,682,-466,-706,1000,-174,-579,-719,-740,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{320,-435,-447,107,-482,-155,-150,481,-287,-1000,-438,467,-665,-493,-1000,-8,1000,144,-12,-116,-822,34,-691,-391,-319,1000,39,1000,-1000,-928,-663,1000,-983,1000,-940,-416,-306,-1000,-116,702,1000,277,1000,1000,-567,-59,801,1000,-1000,-956,607,-559,1000,361,192,-571,819,-183,-1000,1000,-130,-1000,-62,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{879,249,407,358,-482,-53,180,830,-132,-1000,-680,1000,-892,-462,-1000,-344,1000,824,-914,-705,-1000,561,-1000,-216,-67,1000,-444,1000,-1000,-928,-533,1000,-1000,1000,27,-1000,-449,-1000,-839,-264,517,289,1000,1000,-428,-95,1000,1000,-1000,-1000,219,-368,1000,783,215,-589,-84,-568,-1000,1000,-514,-1000,-469,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{628,-300,794,556,1000,1000,-1000,1000,109,569,929,607,-1000,1000,1000,971,1000,-181,1000,1000,1000,-1000,-1000,1000,1000,-1000,1000,1000,133,-1000,897,358,452,-583,842,197,-99,692,22,-1000,1000,1000,-1000,1000,-1000,-645,235,50,-1000,-1000,-1000,-615,1000,1000,161,-924,1000,-340,-884,-1000,1000,-166,1000,607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{228,-447,438,35,1000,-149,-1000,1000,428,1000,743,439,-1000,1000,1000,1000,654,-1000,400,684,1000,-330,-504,480,934,470,1000,1000,-1000,-1000,311,-167,115,-1000,640,1000,-1000,959,654,-1000,1000,1000,-1000,1000,-176,-1000,203,1000,163,-1000,190,-303,1000,1000,906,-514,1000,207,-1000,-454,960,-1000,1000,-625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{-310,-300,164,1000,208,-1000,-400,115,179,108,232,1000,-1000,1000,-86,717,1000,236,33,1000,940,-997,-254,-1000,633,1000,-557,-192,-250,-994,-353,1000,513,-715,584,575,-756,438,610,918,426,-398,678,-288,-285,214,-1000,-314,915,794,-382,-1000,-1000,511,1000,-87,623,1000,736,297,-223,-60,1000,960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String,java.util.Properties):java.util.Properties",
            new int[]{-565,57,119,1000,-1000,182,-1000,1000,-408,-130,549,332,1000,1000,844,30,-620,-162,132,571,-122,-1000,1000,1000,-52,-807,1000,-1000,888,-1000,-986,-1000,120,-22,931,-1000,102,660,1000,-601,-202,-402,-261,-1000,889,1000,-913,1000,-243,-717,-1000,653,-1000,-1000,329,1000,-296,976,1000,-1000,-238,-1000,352,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String,java.util.Properties):java.util.Properties",
            new int[]{-398,-582,-211,-629,1000,176,400,1000,-412,1000,831,-379,434,219,67,1000,-989,332,-393,-204,1000,1000,-1000,-237,-607,-990,-541,401,179,127,-484,-424,-3,-490,-361,899,275,741,-597,1000,-342,515,-907,1000,1000,1000,-93,-422,-22,-343,259,423,-482,631,630,-941,507,-570,1000,345,172,499,-410,-368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperty(java.lang.String):java.lang.Object",
            new int[]{-483,-213,917,1000,-448,293,-1000,-331,-389,-534,1000,-400,767,490,531,1000,315,-414,-484,-1000,-1000,738,1000,499,-400,1000,-78,621,-1000,233,-790,-987,942,-994,1000,-420,-1000,-566,739,-1000,1000,-1000,-243,649,-268,-822,-628,-1000,187,-1000,1000,141,-1000,-1000,505,1000,-1000,-420,400,-1000,-400,300,146,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperty(java.lang.String):java.lang.Object",
            new int[]{417,102,38,-655,-1000,-1000,1000,500,467,5,156,176,715,1000,-394,-319,-878,-1000,415,-707,-328,-847,1000,-1000,-120,-426,-1000,-442,505,-449,-175,430,952,-803,-495,-334,1000,86,-627,-41,-98,1000,-840,295,18,1000,613,289,-28,1000,155,-220,384,-446,-97,-352,747,-68,270,-79,569,1000,-1000,872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0NA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperty(java.lang.String):java.lang.Object",
            new int[]{538,366,542,-97,-895,495,83,-1000,739,254,-678,910,45,-1000,1000,843,-445,-1000,803,-349,-1000,632,541,393,552,412,-758,-869,1000,1000,-418,-1000,400,-294,-880,-911,440,-260,1000,-184,1000,199,-405,-371,1000,-1000,548,-264,491,1000,400,-390,416,-1000,103,-687,-618,-156,-521,-1000,975,-374,729,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String):short",
            new int[]{210,306,322,653,-248,1000,303,-73,-592,1000,400,-546,102,188,155,569,-1000,-233,1000,233,1000,1000,441,17,-451,-1000,1000,526,-582,71,-421,400,863,563,-384,352,-869,-937,246,1000,259,375,983,439,-69,400,259,67,-37,579,26,-207,731,175,715,101,619,-237,392,-770,-272,-400,-99,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String):short",
            new int[]{540,-465,667,754,1000,-813,-174,-632,1000,560,-378,-334,267,1000,-400,-863,605,118,-938,1000,-1000,-400,12,26,961,821,-192,400,1000,-49,908,153,-1000,477,-846,-365,385,1000,528,1000,-314,59,-1000,-1000,-641,506,-764,1000,-1000,-557,472,-251,-1000,-201,295,169,-982,487,-1000,814,-348,186,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{-554,-66,-576,905,-101,-299,-349,523,216,431,-136,785,194,-207,-838,807,736,-608,530,-392,-40,98,-648,705,-456,-6,870,-850,-223,-220,130,787,-519,64,23,-246,-51,348,-562,-730,375,720,264,638,-174,256,991,21,-201,986,0,56,-413,893,-707,-349,289,-150,-640,411,127,708,179,58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{-1000,282,-842,-648,-1000,-758,802,-575,-146,957,-73,-56,782,827,1000,1000,-7,-566,872,1000,482,-1000,-696,-961,-877,1000,1000,-1000,-1000,1000,-763,199,-700,-444,-519,1000,233,355,-952,-1000,-283,514,1000,197,1000,20,-514,-734,-1000,1000,783,575,-1000,1000,437,-275,102,649,-117,1000,-1000,1000,-533,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{1000,-231,-462,-609,1000,329,-406,-1000,-475,-860,-899,-263,-1000,207,1000,640,-81,-425,1000,315,-1000,-1000,-1000,1000,805,281,193,-1000,-37,-820,608,104,-612,-433,-951,155,1000,996,-817,1000,898,301,-831,1000,-486,981,-1000,-1000,75,192,-770,-1000,1000,924,1000,-404,1000,146,-581,-950,496,913,1000,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{310,0,918,-414,-37,899,-922,1000,246,1000,-548,1000,-592,-1000,-265,249,1000,-1000,-865,1000,1000,906,-1000,-1000,1000,-1000,114,297,-977,991,-173,0,-1000,-1000,-45,-1000,-435,0,1000,-1000,-607,819,-400,-964,-89,867,1000,1000,243,67,-1000,868,65,12,-1000,894,-1000,371,611,-1000,1000,627,147,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{-487,774,489,1000,-240,-443,-341,539,198,685,1000,-1000,1000,97,-109,613,-307,-1000,201,1000,1000,1000,-82,858,-84,-127,1000,197,863,-119,-1000,-649,-1000,-1000,598,-724,-611,175,-736,-1000,1000,-634,-544,916,-285,-642,1000,675,-1000,899,-1000,792,-1000,-723,1000,1000,913,-208,-1000,-7,-533,-1000,777,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{1000,-606,463,1000,-1000,1000,-738,-1000,-103,705,902,1000,1000,228,858,-194,1000,626,-342,1000,1000,-1000,1000,-1000,717,763,1000,353,1000,753,-1000,-1000,-771,756,-68,1000,1000,-135,-1000,-221,-1000,1000,-1000,1000,495,1000,1000,-1000,1000,-191,-1000,396,-309,-1000,-55,1000,-736,110,-577,-1000,-908,-400,1000,-161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.String:KzB4OA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{1000,-417,-796,-503,-643,-200,1000,1000,-977,-102,750,657,424,-1000,-74,619,-734,544,-318,953,590,52,-408,499,-250,-1000,-1000,696,-595,678,673,513,-541,-705,-126,-246,210,-1000,520,1000,-989,-24,1,-10,-608,-746,-943,-1000,296,1000,506,1000,-1000,401,-51,1000,715,-707,697,-1000,-1000,-1000,591,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{-284,-441,134,831,758,906,662,-584,171,-379,442,-273,247,-193,786,-194,-82,190,115,207,-382,89,907,-936,-532,796,-178,282,646,702,-912,-7,712,-305,312,-3,-621,830,746,819,-219,-900,771,-778,-363,242,-176,-471,850,944,364,973,-896,-429,-428,418,770,-418,441,-729,-925,167,-324,-832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:1:29:java.lang.String:b2JqZWN0NA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getStringArray(java.lang.String):java.lang.String[]",
            new int[]{-901,348,-276,18,-744,-1000,393,-1000,94,934,1000,1000,-280,1000,880,281,860,1000,400,987,1000,-312,-740,-1000,955,234,-947,1000,-960,-227,1000,-689,97,-138,-208,787,1000,662,-162,768,-1000,-969,-1000,400,10,1000,-979,719,-591,-956,-632,400,600,579,143,197,-1000,404,-118,-1000,781,803,-743,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:0", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getStringArray(java.lang.String):java.lang.String[]",
            new int[]{-1000,159,-587,614,-392,1000,-307,286,371,1000,1000,853,1000,966,125,1000,76,-1000,-1000,1000,197,-1000,-1000,-652,993,1000,-469,426,-863,337,1000,-1000,27,904,-1000,1000,501,-1000,665,1000,-1000,-327,-1000,-1000,550,413,1000,1000,944,-241,-774,-1000,1000,406,-1000,1000,638,1000,-997,-837,-998,979,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:0", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getStringArray(java.lang.String):java.lang.String[]",
            new int[]{-1000,-327,-242,1000,-1000,1000,-399,1000,-1000,1000,964,712,512,-901,-1000,1000,347,1000,-809,186,1000,-312,-181,-920,548,-1000,95,1000,142,1000,-80,1000,828,846,-118,20,-1000,458,948,405,1000,1000,-853,-1000,379,246,-808,719,648,-1000,-1000,-1000,1000,-68,610,-349,1000,-654,549,-1000,767,611,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{-1000,126,-812,3,-1000,-1000,1000,-920,1000,-570,1000,-1000,851,329,1000,-1000,1000,1000,1000,-1000,1000,755,1000,-1000,1000,-125,-1000,1000,1000,1000,-1000,1000,1000,1000,929,-1000,1000,-1000,1000,-1000,1000,1000,560,-1000,170,-875,-1000,1000,997,1000,-1000,-172,1000,775,1000,-1000,-1000,-1000,1000,1000,-194,938,76,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{139,-627,184,957,376,-312,1000,-887,677,-746,145,-210,646,1000,64,-1000,1000,-297,1000,-304,-175,-143,-904,-484,933,95,-402,669,-14,170,-1000,1000,1000,944,-562,-620,60,319,-436,-1000,586,437,1000,487,739,-599,-328,-1000,-623,329,680,-422,885,455,-167,339,372,331,-329,1000,-1000,-85,317,-971}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{1000,33,-142,275,61,-336,211,-575,-1000,1000,936,-335,832,860,1000,1000,-1000,-829,774,-874,-1000,26,-578,-602,-403,864,-1000,578,-1000,-976,305,6,-70,1000,-1000,355,-414,-1000,-1000,-1000,-598,137,-405,384,71,-670,-1000,-330,-406,-1000,-439,214,1000,397,320,-479,-371,-878,-1000,-597,-546,1000,583,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{704,-537,614,248,-154,-891,-200,-863,-1000,1000,-579,85,1000,613,234,557,-1000,-1000,1000,-912,1000,51,-1000,-792,400,768,-1000,922,-789,-1000,12,-137,-332,-297,-1000,-594,312,-1000,-1000,-821,-811,264,-1000,155,71,-650,-937,-330,-558,-805,-601,287,903,-273,-546,-1000,-254,-735,-1000,-930,-516,669,-378,-575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{1000,204,399,406,-622,171,191,-628,-946,400,-411,-398,220,697,41,1000,-400,67,-167,-485,134,97,22,-209,-313,701,-400,-85,-267,23,-707,-165,-309,684,16,94,1000,-880,-1000,-254,-116,-180,-677,-171,-201,-177,-1000,901,-654,-165,-1000,1000,186,-345,-177,170,163,-400,232,82,-52,-347,-588,340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "isInitialized():boolean",
            new int[]{735,144,248,95,-698,-850,358,1000,-1000,111,-380,-430,-1000,438,1000,-1000,-373,637,-1000,-1000,213,-1000,-266,902,-1000,372,1000,267,-1000,605,-200,170,-760,1000,-183,-1000,465,-552,-59,-879,592,-894,-1000,1000,-299,-124,307,-734,270,1000,714,114,-136,10,-149,786,36,238,1000,-1000,-20,703,-166,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "isInitialized():boolean",
            new int[]{-169,-777,692,-512,-256,-1000,-896,1000,94,1000,-1000,-646,-526,-1000,745,-1000,-1000,1000,-527,161,719,-1000,1000,1000,-88,-717,1000,1000,276,210,-575,1000,-1000,443,88,643,-931,-346,314,-1000,-795,-1000,-449,-954,-1000,-1000,-1000,1000,-1000,821,530,1000,-1000,579,-529,1000,327,1000,-963,729,-386,-199,1000,-148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream):void",
            new int[]{1000,-321,-256,-70,253,-1000,1000,336,766,-1000,-1000,749,762,-535,898,1000,1000,-707,289,918,596,-216,-397,-527,705,353,-809,-1000,-55,-1000,-877,-426,813,465,-1000,823,-1000,-184,-262,1000,-434,-1000,-495,195,-711,-174,1000,1000,1000,1000,428,1000,206,-160,852,1000,-1000,390,1000,1000,-1000,-1000,1000,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream):void",
            new int[]{-545,-219,-856,317,-902,1000,21,301,-876,1000,-552,1000,858,-689,-107,-854,-62,-422,-689,-574,913,-200,953,-573,-625,-649,-959,57,954,271,-811,-150,818,1000,676,-375,-570,1000,595,268,890,587,-963,38,560,470,-960,-1000,-585,881,906,-240,-569,441,-7,523,1000,200,-172,-131,-252,-521,-1000,558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{103,264,-557,64,-935,-1000,170,7,-85,-221,6,-559,1000,-338,316,976,-1000,1000,-879,-1000,1000,-1000,-519,86,1000,-299,138,-632,-927,1000,1000,1000,1000,1000,997,528,-1000,-1000,-1000,-547,1000,-147,1000,-494,1000,-219,454,1000,586,-150,-611,-1000,183,582,-220,-1000,-1000,723,451,-63,-1000,137,1000,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{-986,-15,-34,-595,325,1000,558,196,622,1000,-368,-501,-481,-1000,190,566,1000,-1000,-709,584,-1000,1000,604,949,671,35,620,-524,902,-1000,-674,-745,-1000,-758,-262,486,626,876,1000,-209,-59,112,-873,-469,-1000,-367,-30,-675,-739,-663,-183,83,-1000,-231,-269,1000,841,-614,-425,-561,1000,-518,-314,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "putAll(java.util.Map):void",
            new int[]{152,-333,878,-446,-845,-594,-423,971,-745,-742,836,-68,-943,-828,-42,156,614,-305,-780,-224,-361,-309,473,-286,824,652,892,485,-936,-703,358,959,887,42,-97,161,-819,597,-182,-468,445,-515,-408,234,-796,-413,-359,83,999,945,984,-537,-612,-989,-557,-817,-219,-709,-242,-332,-375,-93,61,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "putAll(java.util.Map):void",
            new int[]{822,195,-532,807,140,1000,412,-442,120,1000,0,1000,103,-343,-747,-920,0,882,665,672,764,-1000,913,1000,674,-387,-648,-1000,-1000,891,0,-395,212,313,1000,932,-1000,-1000,-88,650,-792,-334,1000,-886,526,1000,0,-458,-1000,351,0,489,571,892,-171,0,0,956,254,681,678,-323,-1000,768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "save(java.io.OutputStream,java.lang.String):void",
            new int[]{718,654,34,-326,1000,-701,-496,1000,766,824,-828,-1000,-15,-685,228,-217,1000,-1000,-184,-707,-1000,928,-1000,-1000,601,-822,-1000,-1000,-1000,-629,926,-807,-363,1000,-669,-464,1000,-986,-461,1000,-362,693,-1000,-1000,479,-575,-797,9,112,104,-897,-159,-613,61,505,1000,1000,-951,548,800,486,-642,-198,-256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "save(java.io.OutputStream,java.lang.String):void",
            new int[]{1000,-690,-597,-481,1000,-304,926,1000,651,1000,-912,-1000,-1000,705,-1000,-1000,-89,582,1000,-569,-841,-623,-301,1000,-1000,-643,455,579,-121,-939,-704,236,1000,-1000,370,1000,558,-1000,-316,-854,-912,-1000,-957,1000,-1000,709,46,1000,-1000,-1000,-653,1000,1000,-78,-890,1000,-223,1000,718,1000,-969,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("VOID|getInclude=NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setInclude(java.lang.String):void",
            new int[]{-165,-825,79,317,-415,1000,-68,316,636,119,-64,-298,-687,-847,675,685,46,1000,-17,-784,-342,656,-607,301,-244,-279,-715,-448,178,-206,564,-1000,-870,-332,88,-399,-360,434,280,1000,-775,447,386,296,50,1000,369,-899,-49,172,551,-50,107,-441,1000,-1000,1000,-890,-1000,-807,-82,-1000,-768,-208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("VOID|getInclude=java.lang.String:LS0zNDEuNDc4", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setInclude(java.lang.String):void",
            new int[]{366,87,-72,-373,421,-50,-629,-922,-866,-945,881,-384,-121,11,-994,-174,-871,913,-913,-876,720,821,-341,-753,478,383,19,392,-818,33,686,376,21,-104,-686,-207,722,-432,279,93,336,257,676,734,327,451,702,130,-980,-787,543,983,-235,497,792,-241,212,-540,-871,583,997,-352,573,-396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setProperty(java.lang.String,java.lang.Object):void",
            new int[]{-616,474,-251,1000,1000,1000,1000,-58,-1000,-663,-1000,1000,-443,1000,-160,-1000,1000,-1000,1000,1000,-1000,-1000,1000,-1000,-89,1000,388,-77,-1000,-1000,1000,-1000,-189,-609,1000,-1000,1000,-156,-512,-1000,62,-1000,259,103,-993,-1000,-1000,-1000,966,67,106,469,1000,1000,1000,-1000,-631,1000,-1000,937,365,-1000,298,-770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.ExtendedProperties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{83,99,-32,790,-1000,-449,91,-1000,-157,584,-252,-49,-450,1000,-1000,-777,600,-49,1000,433,303,-284,446,554,-151,992,297,-586,982,-1000,-132,-458,-284,18,-85,1000,-104,720,407,-795,-1000,300,-792,328,-348,462,515,-82,-390,304,770,-151,42,-841,889,-92,683,-143,407,-726,234,436,581,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{907,-6,269,826,791,-524,-1000,857,-123,-521,-1000,113,524,757,862,-753,982,-783,1000,120,-733,1000,1000,1000,-955,1000,-1000,69,-669,1000,94,369,-1000,-459,-215,122,176,128,-534,-889,-693,-316,-1000,1000,-456,-42,73,-914,1000,1000,706,130,797,-533,-1000,-1000,828,-637,-215,-964,-247,776,153,-15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{922,-483,-277,533,1000,-42,782,-732,-847,-439,-70,1000,-774,1000,429,-701,-192,1000,-634,-447,446,1000,1000,-1000,434,312,400,-605,-808,391,755,400,990,-73,-312,501,517,115,605,-299,389,490,32,-442,948,742,-1000,-534,-604,444,-1000,400,223,-306,327,-459,218,-433,-64,-548,1000,-827,-819,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{-618,-714,154,562,-1000,-1000,-652,-10,-647,-831,-604,394,353,-13,1000,1000,1000,1000,950,-533,-948,1000,-194,-932,-493,606,2,880,254,-509,1000,-623,-341,1000,-441,688,102,1000,553,628,299,-1000,-474,803,-331,-3,1000,1000,-437,-198,-1000,-993,782,1000,-536,541,-74,-656,-472,1000,302,-827,-83,-405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{-189,81,-49,391,-949,-765,-87,-845,931,-843,-1000,-1000,-679,-1000,-518,358,-803,-727,1000,879,-97,-259,516,802,508,-21,-323,-427,470,382,360,-73,-1000,507,-82,212,49,728,74,1000,1000,-444,-85,185,77,-407,-186,1000,149,-238,1000,934,1000,1000,1000,59,-255,1000,913,-1000,-50,1000,363,1000}));
    }
}
