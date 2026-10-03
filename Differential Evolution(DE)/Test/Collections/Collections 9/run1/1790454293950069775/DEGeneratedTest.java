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
            new int[]{554,717,784,1000,1000,554,-586,270,-657,-1000,1000,36,-217,-310,135,1000,967,-218,-766,944,-352,-20,170,-1000,14,1000,-521,-872,-227,-605,-1000,-921,-1000,115,-197,-355,-698,-299,-814,1000,10,1000,346,-1000,-299,773,-526,554,-592,-1000,-1000,190,-6,1000,16,679,2,143,-671,51,-821,-628,-1000,-423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "addProperty(java.lang.String,java.lang.Object):void",
            new int[]{-861,-486,544,-421,786,966,-273,-716,-1000,1000,-756,-798,-189,-557,1000,87,-510,-1000,1000,1000,-183,1000,-10,194,-248,-794,-1000,-498,1000,1000,-31,-293,73,714,988,1000,-756,-39,-709,-1000,-1000,-482,278,697,422,1000,731,1000,-1000,1000,1000,-1000,249,-977,-424,-1000,-1000,-1000,-534,1000,420,859,482,-462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "addProperty(java.lang.String,java.lang.Object):void",
            new int[]{-1000,327,368,1000,528,1000,-1000,-700,-1000,1000,-1000,-1000,63,-1000,-56,-375,-527,-1000,669,-940,474,1000,1000,754,-745,-958,-79,851,793,788,-326,-105,1000,327,520,628,-492,1000,-1000,-304,1000,-937,-152,389,-53,85,-383,680,-1000,611,1000,627,219,-1000,1000,-871,-766,-1000,-938,1000,151,1000,335,938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{-1000,-705,539,-1000,1000,-256,126,-1000,1000,1000,-1000,-610,-697,-810,1000,-399,1000,-1000,1000,1000,-1000,-213,1000,393,-1000,-1000,-1000,-1000,973,-825,1000,374,-1000,246,-1000,-952,-907,-1000,-1000,687,-1000,779,225,-1000,-648,1000,32,265,273,-278,-1000,-1000,-1000,-1000,231,-1000,-1000,889,265,-1000,-1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{-300,-630,-981,-763,1000,-99,-693,-1000,1000,733,-98,1000,436,-729,1000,-118,1000,-725,938,386,-925,-206,-400,162,-1000,-705,-1000,-1000,467,-155,798,382,142,1000,272,-512,-559,5,-912,1000,-1000,300,-41,-445,-541,1000,-23,166,-1000,-168,-1000,-915,-1000,-721,151,-680,-1000,617,-130,-1000,-794,1000,729,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{701,-336,-923,-804,293,-646,337,-34,1000,558,-456,-686,910,-810,632,-708,-430,-578,1000,289,-1000,76,1000,1000,996,-1000,140,-470,-1000,-63,756,452,-58,-777,-1000,1000,-1000,702,-1000,578,320,-916,-215,-317,359,747,-225,-263,53,1000,-1000,-1000,-963,527,-201,406,-756,512,400,-86,21,731,1000,238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "clearProperty(java.lang.String):void",
            new int[]{1000,-294,591,172,400,-1000,761,335,394,-1000,542,32,1000,-1000,-150,-473,-324,1000,724,-813,-1000,-1000,504,-115,1000,683,833,-1000,-319,1000,1000,400,13,-690,-714,1000,383,1000,-29,-865,460,-886,-1000,1000,-1000,326,-319,-1000,746,-813,-162,-302,329,1000,-245,1000,314,755,375,1000,570,982,548,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{311,498,-801,1000,365,374,499,-357,1000,-6,-341,-915,-139,-623,1000,-741,-582,-84,808,-743,1000,-251,-99,-1000,1000,14,-1000,980,442,150,-1000,-453,1000,-1000,-1000,1000,-1000,1000,-1000,1000,-1000,-400,-1000,501,-1000,-60,436,445,-135,-371,1000,-169,1000,41,1000,-909,-331,611,385,513,1000,69,-701,-122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{49,204,259,944,755,357,-951,105,803,-893,731,-1000,-490,-653,153,-575,-609,431,1000,293,-262,298,-1000,-1000,-891,-29,-612,699,-285,381,-675,-1000,875,-524,721,-145,1000,414,-536,-843,-1000,-171,-271,-48,-1000,16,356,85,746,-670,987,-416,-1000,1000,-629,930,251,-771,1000,1000,74,51,-489,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "combine(org.apache.commons.collections.ExtendedProperties):void",
            new int[]{166,135,-516,-674,32,-695,-1000,-28,-830,-600,50,-1000,133,-1000,-331,-301,-1000,-629,1000,-858,-880,837,508,385,-535,426,-1000,178,740,-879,-521,-784,569,266,115,1000,669,-106,-400,-400,125,156,-775,-1000,-288,-663,918,642,1000,279,350,-1000,195,831,-161,-199,52,1000,916,166,1000,1000,552,-928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.ExtendedProperties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "convertProperties(java.util.Properties):org.apache.commons.collections.ExtendedProperties",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "display():void",
            new int[]{-213,-288,279,814,-1000,10,481,1000,106,-350,464,923,-1000,204,755,-1000,419,1000,-816,-329,-541,-522,-918,597,-844,1000,1000,-400,219,-877,-81,-1000,494,-685,-736,46,1000,66,690,-372,-559,-1000,-998,-60,-367,-293,182,-764,1000,543,189,809,501,-16,-241,147,-981,84,-117,123,-1000,806,-588,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "display():void",
            new int[]{699,-288,669,-182,912,-586,-24,-231,-400,-461,-400,183,-451,-93,-403,1000,-379,388,-488,464,-256,-400,400,-246,-102,-439,-400,-424,-159,998,-702,-62,1000,-20,-141,22,-66,-573,-400,-665,24,-350,-122,291,1000,-400,-344,-517,806,432,107,-125,-262,201,751,-16,286,129,-400,-78,1,-179,-617,618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String):boolean",
            new int[]{-87,837,-78,-618,-1,651,765,250,1000,596,-754,1000,-1000,-177,-1000,-642,-1000,-396,100,388,1000,-370,1000,632,-939,269,657,371,1000,-322,177,-674,-1000,-835,640,-534,-46,1000,643,-896,661,-1000,165,838,-982,525,1000,-750,-355,-1000,-1000,-231,413,-21,78,898,952,-455,-987,421,-1000,1000,782,-880}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String):boolean",
            new int[]{1000,-234,699,702,-1000,722,-593,-1000,974,-143,1000,-384,-1000,-670,593,-1000,-392,76,-785,1000,992,826,1000,-1000,447,-843,177,709,941,840,1000,1000,-810,-339,82,1000,401,225,-1000,803,-238,-1000,-576,-282,-441,-56,928,456,-782,1000,153,-18,26,-48,32,67,378,766,-831,-427,-924,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{915,237,819,297,-31,-622,605,-276,1000,-115,-1000,1000,-850,683,-1000,-182,201,-491,-121,544,-1000,76,-178,-913,664,-135,-473,586,975,201,-500,-521,675,1000,947,-1000,-343,400,-54,-465,892,971,-924,795,27,481,-588,-1000,343,-1000,-1000,584,667,-1000,-771,79,332,863,-107,-365,-1000,767,-1000,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{-940,-429,-969,-366,-991,-273,328,-413,-557,593,776,808,176,525,599,991,-264,-174,859,-546,281,-643,-494,265,-739,-342,79,507,544,-486,-527,936,-410,-920,-705,834,47,176,-815,-212,638,-850,189,319,-424,-147,-226,825,917,-314,-584,140,-766,423,795,-863,-224,-423,-396,-693,908,-556,740,978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,boolean):boolean",
            new int[]{-674,48,-666,550,1000,32,-472,-234,-692,-336,-151,359,518,-945,-165,-1000,874,1000,-797,1000,-370,-201,-713,72,-352,1000,261,-454,-1000,-1000,750,-208,-1000,-297,-566,-739,340,-321,-667,-214,294,1000,1000,-150,-44,1000,778,-183,1000,-148,654,-7,-268,851,-1000,1000,436,-844,681,699,667,1000,-295,56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{547,174,394,1000,835,-1000,1000,-349,-840,-608,-180,-17,-364,251,-897,-887,-801,-1000,-1000,-1000,761,156,411,914,-911,1000,-934,-402,-570,-447,-708,668,941,1000,-1000,9,-1000,-847,157,-12,964,-220,179,864,516,-458,-22,47,101,954,-320,167,758,26,-1000,796,-676,-1000,-834,-615,1000,-789,62,-469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{51,852,394,-882,-42,182,1000,869,1000,557,899,1000,867,204,-1000,-1000,1000,41,-1000,655,-1000,700,-73,687,-911,-1000,-878,-18,1000,1000,-376,-308,-487,-349,1000,-58,-1000,-847,-584,969,1000,1000,1000,557,371,-647,-284,-232,-1000,-1000,1000,-986,758,956,-502,-36,1000,-993,242,-982,1000,624,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{-1000,789,827,-524,458,-1000,-61,90,1000,334,1000,1000,1000,-153,-1000,-180,-1000,1000,-400,-1000,-99,974,-175,155,-338,342,-546,479,1000,1000,-981,-704,-261,-786,-400,1000,-941,127,385,-169,-211,394,1000,-476,1000,-1000,-124,-292,883,356,-37,-203,819,-412,-1000,1000,179,-740,-286,124,1000,52,501,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getBoolean(java.lang.String,java.lang.Boolean):java.lang.Boolean",
            new int[]{-1000,585,-11,-1000,-858,-673,209,740,612,585,1000,-16,1000,463,786,921,-242,1000,1000,1000,-1000,1000,649,-810,1000,-895,-425,251,1000,1000,-761,-1000,-1000,-1000,1000,580,1000,1000,653,428,207,65,1000,-1000,-742,-839,370,470,-1000,-1000,-47,120,-1000,1000,-893,-823,1000,-772,684,709,935,-209,-743,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String):byte",
            new int[]{155,-402,-232,652,1000,-375,531,-243,191,13,499,791,-9,-728,-1000,-1000,-348,-867,181,548,461,-808,816,-912,-1000,115,185,340,1000,-7,-315,230,-1000,513,-274,-1000,750,-220,-1000,860,1000,-182,-1000,-768,-806,437,-407,794,-1000,409,1000,-759,90,-462,-1000,-402,5,-1000,-58,-957,284,-642,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{1000,906,-756,-884,1000,-395,-496,526,1000,-929,-404,-382,-1000,749,726,-1000,205,65,1000,75,-748,496,-1000,1000,1000,1000,-543,-377,-1000,-5,760,1000,-1000,-809,389,1000,674,66,485,214,-1000,-1000,-642,-924,1000,1000,-1000,-631,1000,-254,416,-988,1000,930,-510,-1000,-1000,-1000,1000,-1000,-756,-1000,-1000,279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{242,342,29,-1000,-12,-353,-93,-92,601,5,-1000,-600,-751,920,121,179,40,-559,1000,664,-978,416,-686,743,220,961,308,1000,-137,634,-400,78,-302,-813,284,-400,400,2,1000,372,-215,268,-1000,-202,1000,643,-496,18,1000,25,-328,-875,-158,-252,-789,-1000,-160,-285,1000,-521,-1000,-321,182,352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,byte):byte",
            new int[]{1000,324,-61,-97,1000,188,229,61,-921,982,939,78,-687,-51,615,472,-1000,466,999,-326,-48,466,-455,294,1000,1000,482,-592,-400,-402,805,-679,1000,578,-1000,255,140,645,819,-1000,483,-461,-615,-506,-437,125,-224,686,1000,-545,273,-988,-409,73,-48,-49,-506,-1000,346,799,-486,-19,-8,324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{-635,240,634,-1000,-1000,-76,-556,-891,-301,223,-441,-1000,-935,-913,-538,623,1000,367,402,-332,-709,-199,505,-344,-355,644,-838,-819,223,183,418,-314,192,-482,-528,-1000,-114,-314,10,956,297,344,-258,1000,792,-663,378,-37,-245,1000,-431,-527,795,836,-817,-713,524,1000,-518,418,-262,486,288,-317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{-458,378,343,-258,-130,-99,-811,-1000,-58,-698,59,-239,538,355,998,155,265,-176,779,162,797,-1000,-563,-1000,411,-33,-115,-15,891,-506,-573,975,388,29,-805,-452,1000,-856,-99,814,1000,16,-400,870,-70,324,460,232,-1000,262,1000,-753,-691,338,-578,-153,1000,-13,636,-63,672,870,-449,-484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{-143,-573,574,-721,1000,756,95,-492,38,81,-967,-387,931,-970,-1000,1000,1000,166,797,1000,-569,-315,161,700,1000,-1000,-107,1000,809,-413,-655,862,-1,-1000,-836,-844,-1000,-752,892,-579,-234,638,1000,-1000,824,-305,597,1000,358,348,337,-449,-282,447,-343,-39,-935,257,-677,858,1000,464,1000,388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getByte(java.lang.String,java.lang.Byte):java.lang.Byte",
            new int[]{-547,-408,424,668,324,1000,-849,875,-1000,-642,-1000,1000,-199,387,-521,-281,-87,319,-920,-512,425,-436,-113,960,-722,1000,-746,-1000,497,-805,276,865,-489,21,-330,-677,1000,1000,-1000,-728,-70,547,-74,-814,-855,698,-316,-1000,22,-662,255,-232,-672,1000,612,-258,351,-803,-58,300,-1000,494,4,691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String):double",
            new int[]{1000,-345,839,728,-918,-1000,1000,-1000,-967,116,-62,-1000,-916,802,1000,-944,560,721,-7,-1000,1000,-828,-1000,1000,1000,-1000,-786,6,530,-1000,1000,-556,-1000,975,-553,-1000,-1000,-678,-1000,430,1000,-651,-848,-995,-563,-256,-1000,448,2,-1000,48,-1000,-1000,-239,826,41,1000,1000,-1000,1000,-1000,-704,-989,577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String):double",
            new int[]{1000,531,159,-995,-1000,-1000,1000,239,-99,-354,543,-439,109,358,-491,-1000,1000,196,689,1000,1000,-1000,458,170,381,1000,532,-791,-1000,-631,136,-1000,-1000,949,-1000,-711,-1000,1000,18,1000,95,-593,-1000,-1000,-977,-1000,-1000,1000,-366,-601,-894,-1000,354,1000,1000,1000,20,-973,-979,270,-1000,1000,-962,-545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{-1000,384,9,-1000,-1000,233,-349,1000,-433,-1000,-608,308,-1000,-1000,1000,377,1000,-464,-79,-240,1000,1000,623,-347,-1000,1000,1000,-263,1000,402,-802,57,1000,-924,291,577,-126,1000,1000,248,1000,-1000,1000,-895,1000,850,-1000,41,-20,-1000,1000,1000,71,777,22,715,-1000,-956,-452,-172,-1000,-1000,-370,933}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{545,318,-316,306,-645,925,-1000,995,230,103,1000,1000,1000,-1000,-706,-1000,956,-134,850,400,331,-1000,1000,-297,-207,-1000,357,-1000,-229,1000,-124,-400,233,-192,-1000,353,779,1000,887,-1000,808,-1000,803,1000,74,400,-600,-1000,-580,81,400,533,233,1000,-656,214,556,1000,-72,-966,608,1000,1000,548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,double):double",
            new int[]{0,-204,541,875,1000,400,1000,1000,-1000,1000,-1000,976,-920,-1000,241,891,1000,685,-1000,-1000,-104,1000,200,-271,-950,-1000,-1000,48,192,871,442,1000,-1000,216,-342,-1000,1000,-445,1000,-405,-1000,-314,-1000,-1000,668,-716,867,-887,-1000,-405,-1000,-1000,734,937,676,-1000,1000,111,1000,710,714,-993,-523,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{769,-774,-502,569,-675,-335,1000,-90,75,31,1000,161,-1000,-935,-766,-717,1000,1000,1000,-1000,974,-210,634,1000,1000,-758,427,166,1000,145,-175,825,-136,-955,-1000,1000,465,-1000,214,578,-926,-459,-140,843,-21,-1000,-163,103,-245,57,-472,-656,1000,488,627,368,-75,-1000,33,-861,889,-778,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{488,450,89,-268,24,462,-620,331,-367,-64,-842,1000,656,-396,818,-437,154,-699,-312,-750,232,61,-258,-76,-312,-261,-426,-156,51,851,449,-440,-26,544,376,877,1000,-699,-99,1000,-444,822,-130,124,537,-429,-481,310,-439,-771,-400,-1000,345,-149,-380,-202,-1000,882,893,1000,-961,-1000,137,282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getDouble(java.lang.String,java.lang.Double):java.lang.Double",
            new int[]{266,-3,743,578,77,1000,-72,-1000,146,1000,-1000,56,559,35,471,-147,-834,-1000,-1000,1000,-1000,-1000,-1000,-1000,-1000,680,591,1000,-789,-772,134,11,-840,374,368,1000,-561,1000,1000,0,1000,-1000,193,157,240,1000,-1000,-525,-1000,851,1000,-605,-415,1000,1000,597,22,-446,-426,659,924,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{117,738,-521,-62,-480,840,644,-571,-121,-1000,204,-1000,460,298,-344,454,727,206,554,-683,231,-154,595,-373,-480,-11,-847,-788,-1000,122,229,-133,-72,231,19,468,33,-162,136,522,-248,-650,632,-442,-286,877,-538,838,862,23,1000,-135,135,101,-489,-923,674,-444,-616,322,-651,920,256,135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String):float",
            new int[]{-1000,129,-761,1000,-600,778,-779,-545,-1000,-1000,-1000,-235,-83,-1000,-1000,1000,1000,-1000,687,225,683,-488,-84,222,796,1000,173,-1000,633,-1000,1000,1000,1000,-255,542,-731,225,-1000,-695,31,-1000,-483,222,-1000,-1000,1000,1000,-951,1000,-1000,403,-1000,-1000,1000,-503,-1000,1000,-1000,-681,-1000,1000,1000,-1000,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{1000,-396,494,-439,724,-881,1000,1000,-112,-204,296,86,249,-224,-831,-370,-1000,606,760,-288,-1000,271,-893,-1000,-1000,-10,-1000,1000,94,765,-40,-573,813,-802,253,-1000,620,-1000,-116,1000,1000,-525,1000,-250,1000,-1000,900,390,-1000,-993,-1000,-1000,-140,-1000,1000,882,1000,-55,188,-606,-234,481,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{788,90,-276,70,674,-1000,149,1000,-427,-1000,1000,-497,-382,1000,281,-319,589,1000,1000,504,-130,379,1000,-1000,-835,-1000,474,1000,1000,1000,-250,1000,1000,-632,662,1000,206,483,221,628,-620,-1000,1000,443,-403,-1000,0,918,-1000,-311,-1000,355,-1000,-1000,159,-228,1000,-316,203,-900,-165,426,-1000,948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,float):float",
            new int[]{993,-219,898,-109,876,-926,-725,-260,-1000,-893,-1000,-1000,-9,-66,-1000,990,-424,-600,827,145,-1000,-581,-1000,-1000,-348,528,-466,1000,1000,1000,368,-5,992,-1000,-1000,124,-421,531,650,-215,-676,-1000,572,283,-1000,1000,1000,1000,-1000,-776,-1000,-514,1000,-1000,601,567,-74,-1000,1000,-356,-758,-32,-77,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{1000,123,-151,-647,1000,1000,1000,-393,-679,-217,18,-806,-388,1000,77,-364,-1000,190,10,1000,460,-221,840,504,-505,-497,315,126,-1000,379,0,-233,-488,1000,0,-174,40,694,1000,429,-705,-1000,1000,-1000,345,-1000,816,21,932,-835,-890,-996,-1000,-734,992,-169,743,-1000,-1000,355,-1000,305,-189,-608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{19,-963,328,-184,882,594,620,-103,-762,-424,180,-307,-223,693,377,139,-939,-303,10,756,15,-221,676,504,-228,-308,503,129,-335,579,-461,-428,-695,247,0,-733,-147,243,593,-292,-299,-760,298,-273,475,-746,710,144,452,-317,-742,-426,-439,-491,404,20,-270,-786,-195,752,-565,734,-686,-258}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getFloat(java.lang.String,java.lang.Float):java.lang.Float",
            new int[]{772,156,254,434,-204,-831,-1000,-537,-894,-1000,612,1000,576,-1000,76,852,-373,-823,-375,-1000,-262,1000,-35,-1000,1000,-446,1000,847,-10,1000,-1000,-973,-1000,-1000,-710,-1000,-488,-366,173,-471,-343,599,395,1000,985,447,-60,-43,-212,999,-1000,1000,1000,-839,-1000,1000,-141,-708,1000,0,-756,1000,-1000,553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.String:cUhXLkRvb00wTTY2Zl82QmQ0CV8=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{-1000,-108,-971,1000,-82,-920,-956,-928,-647,0,-912,-605,-508,-141,-1000,-499,-518,-402,-598,128,1000,-210,448,-182,-1000,798,-1000,-1000,-89,-1000,-295,-1000,448,910,176,-112,-158,-1000,566,959,544,558,421,642,-155,310,256,910,-78,-600,-331,-686,-165,-213,190,1000,1000,441,-1000,1000,108,-200,856,69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{575,-957,-327,166,-816,178,608,-313,1000,-638,-321,1000,1000,-471,-375,512,956,-562,-1000,-800,628,567,571,765,878,656,54,753,-976,107,-816,282,635,-465,-248,-199,-154,421,-235,70,98,-443,117,53,-458,-408,876,-336,-102,1000,1000,1000,442,-401,-1000,-1000,506,-805,1000,-37,-89,-1000,-662,106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.String:aW5jbHVkZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInclude():java.lang.String",
            new int[]{483,-315,73,-382,705,-683,89,-758,653,1000,-1000,120,-289,648,637,177,87,690,65,126,698,-153,-154,-70,80,610,744,762,704,-915,-303,387,782,201,866,784,178,281,1000,-485,296,-22,-1000,1000,689,-280,-1000,365,-803,-353,26,-399,1000,-422,178,-11,-1000,142,742,-880,1000,-1000,-346,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{-865,972,-246,-1000,1000,-205,1000,-1000,834,1000,344,-908,977,1000,-23,-565,1000,714,487,-288,973,-1000,1000,658,-1000,-1000,-731,543,-1000,911,836,1000,1000,-1000,-533,-179,1000,1000,-1000,-587,467,-148,-552,-1000,-1000,135,-239,-1000,-221,-621,-1000,-936,-612,906,-1000,19,938,840,226,931,-321,-872,467,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{-1000,-453,-342,731,664,-1000,1000,-1000,536,-366,1000,-1000,929,776,-1000,1000,1000,568,1000,878,1000,-1000,1000,-1000,-1000,1000,-1000,956,1000,-25,-280,1000,1000,295,-1000,1000,820,1000,-1000,316,850,-957,-302,578,-1000,1000,280,-556,-1000,-840,-447,-1000,83,787,-1000,-1000,979,-216,-1000,-599,332,-647,117,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String):int",
            new int[]{793,483,478,1000,624,-224,-536,98,1000,-865,-1000,27,279,-1000,-7,251,328,855,208,347,384,140,978,-1000,955,1000,387,42,1000,833,97,-299,-460,37,-319,-1000,187,237,-1000,-353,772,1000,-546,972,154,-170,239,70,-853,-137,759,-149,709,159,123,-635,-750,-505,271,650,-752,1000,470,4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{1000,774,-961,831,1000,1000,639,1000,467,-401,358,359,-45,-246,162,151,-1000,704,1000,591,-531,672,12,399,-1000,368,15,428,56,-702,329,1000,215,-1000,1000,-97,-143,-1000,-63,1000,953,971,-462,383,282,165,508,477,-1000,593,1000,1000,-123,-515,-667,-1000,-300,533,490,-580,1000,300,592,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{-573,660,-866,837,1000,1000,944,735,1000,-1000,-1000,-402,257,-846,-1000,-1000,278,1000,131,435,-577,-520,-24,-130,20,72,596,276,699,-1000,980,-1000,-212,-1000,1000,-1000,-185,-586,173,1000,651,453,-1000,661,915,-134,1000,331,-360,977,314,886,146,366,1000,197,-1000,825,800,-1000,616,4,365,304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInt(java.lang.String,int):int",
            new int[]{-623,-165,-678,274,-320,-478,-782,-232,633,708,-779,-20,227,1000,-1000,852,963,-1000,1000,334,402,1000,1000,-1000,-1000,-326,529,-30,-1000,951,1000,705,-1000,618,-788,364,-220,-448,-1000,-54,-813,1000,236,598,-740,-375,37,1000,309,-1000,1000,-19,680,-1000,-707,909,1000,-237,-504,-1000,-1000,-947,-962,-625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{-440,27,-67,-253,1000,-1000,1000,-545,-1000,-231,-1000,93,796,-920,-1000,996,733,-194,673,179,99,-1000,-48,-1000,-344,574,-1000,211,-151,1000,-335,79,930,-812,-1000,-798,349,-401,-112,-1000,-259,255,-293,-503,-804,103,-657,172,205,432,-1000,-1000,-818,-282,488,111,838,245,83,-609,1000,-105,-858,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{1000,411,-771,-1000,-409,1000,-583,-1000,668,21,1000,-335,1000,945,-467,-402,-240,1000,588,-1000,1000,1000,-1000,1000,181,1000,1000,886,596,537,-147,-1000,-1000,719,118,-1000,-1000,-767,636,-333,1000,1000,815,624,239,-143,889,147,913,-1000,-298,1000,790,-438,-564,913,-1000,-1000,1000,1000,363,-1000,-223,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String):int",
            new int[]{-973,552,-71,1000,-1000,1000,-1000,-1000,-284,-864,304,771,258,393,1000,-335,-156,-233,636,606,691,1000,1000,1000,-1000,-1000,40,138,-413,-1000,-745,-352,-137,1000,353,410,131,1000,-69,1000,-189,1000,-118,1000,-135,194,1000,1000,701,435,786,1000,520,842,-796,988,172,-1000,1000,309,-78,978,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,int):int",
            new int[]{-450,528,74,1000,-1000,1000,744,866,-806,1000,65,-1000,901,-1000,488,1000,-67,-1000,-1000,1000,227,670,742,-1000,1000,1000,-126,-144,-669,1000,1000,-1000,-294,1000,1000,1000,705,-152,-493,-373,-773,-1000,-1000,1000,824,-190,183,1000,170,1000,-1000,-1000,1000,-1000,-1000,-1000,593,-1000,-168,-1000,138,-1000,-404,407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getInteger(java.lang.String,java.lang.Integer):java.lang.Integer",
            new int[]{675,492,279,1000,-352,-1000,321,74,40,1000,791,-862,848,-910,-1000,-1000,580,138,366,-1000,835,-728,1000,-1000,852,819,-1000,508,652,-170,-85,-1000,594,316,-1000,470,827,-1000,-884,-990,-1000,642,802,400,400,-943,-218,-1000,-457,606,580,-1000,-310,-366,528,265,1000,-181,-448,-575,1000,1000,1000,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys():java.util.Iterator",
            new int[]{0,282,243,550,-202,-471,120,735,38,320,-144,539,193,-237,-140,332,275,642,384,108,157,-840,-161,-744,383,0,-187,-140,-398,-1000,60,417,-580,-1000,270,647,294,-622,893,864,917,301,1000,-473,-818,55,196,-454,1000,0,-1000,336,-178,80,360,259,-214,690,605,-562,-791,-191,-57,-329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys():java.util.Iterator",
            new int[]{-1000,-582,-582,187,-202,-35,704,1000,208,232,-1000,-1000,694,1000,-340,-484,-587,839,689,688,-59,-1000,541,314,-237,943,773,-68,-272,-1000,268,-329,-300,-455,-599,-467,-1000,264,352,-420,917,-29,1000,-895,195,-853,955,1000,1000,89,-1000,-803,-1000,-836,312,-1000,381,1000,252,212,-661,356,-525,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{-318,774,-537,-103,-65,-810,-397,-578,-513,-694,-247,-660,814,977,-367,-152,675,-857,-1000,1000,-1000,18,-47,-153,1000,-248,-1000,1000,-1000,349,878,-400,1000,-80,929,-348,-1000,656,24,1000,-159,866,1000,-713,-1000,493,-1000,-1000,393,-295,1000,-645,-729,-62,400,532,-555,-1000,-683,269,-648,-889,627,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList$Itr", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getKeys(java.lang.String):java.util.Iterator",
            new int[]{-1000,978,458,690,1000,-660,-493,-118,374,-14,190,-1000,388,1000,-530,-391,-694,-937,320,1000,1000,625,-104,-523,-1000,425,-710,1000,-913,219,144,767,-108,980,254,138,-661,-200,372,443,-1000,25,1000,-200,-324,-804,-1000,1000,-529,-423,-282,-563,-812,-22,1000,510,526,-462,-154,154,235,-413,746,362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String):java.util.List",
            new int[]{-629,660,948,-1000,74,-441,-523,1000,-8,3,-457,-1000,516,1000,748,-500,-310,-409,85,-1000,416,-1000,-851,-187,-1000,66,251,493,-360,-954,-1000,1000,-284,-729,110,-519,34,-1000,-8,-623,-1000,-817,642,67,-47,-639,-1000,-407,128,-275,-150,-244,-693,-430,-910,-1000,-239,-380,46,931,197,-759,894,-471}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String):java.util.List",
            new int[]{821,-675,-541,-1000,-584,545,106,564,384,-1000,400,-368,-570,-138,288,272,-1000,-213,713,1000,-87,-924,-761,800,400,300,-301,-254,-821,55,-400,320,-621,1000,750,442,-180,-113,209,-564,-128,-145,785,-902,-1000,-122,29,224,222,918,-836,-291,-109,-749,-981,826,1000,-335,165,1000,145,-390,616,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{-150,-894,-2,-664,-1000,-999,-211,764,-1000,-564,909,49,-589,718,-1000,-869,-952,1000,-331,1000,356,-304,220,1000,-1000,45,-342,1000,-893,-399,-1000,-1000,-180,-1000,1000,771,1000,889,-1000,391,394,-339,-1000,-514,-539,275,-760,-1000,-1000,-723,-977,401,-1000,-101,-151,-557,1000,-1000,1000,976,-265,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{365,9,-281,-96,-690,-231,581,421,334,1000,-247,-1000,-622,493,70,1000,184,-952,979,-1000,-829,-727,146,410,-947,-1000,-144,632,1000,1000,-243,46,1000,-871,148,53,497,-769,69,-208,-131,937,251,-1000,-280,1000,376,-788,-1000,806,1000,301,1000,584,730,995,176,-804,-57,329,-849,-282,709,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getList(java.lang.String,java.util.List):java.util.List",
            new int[]{143,39,-336,-1000,-1000,-1000,1000,-400,-942,-1000,-648,359,-696,516,-1000,-1000,-1000,1000,-1000,-86,1000,-232,-1000,1000,-1000,-981,327,239,-1000,362,-571,-960,-1000,-734,897,619,411,193,331,812,-781,-1000,-1000,-506,-531,493,-1000,-333,-344,-1000,-1000,477,-1000,-1000,-1000,-1000,528,-1000,1000,912,-504,-114,873,-148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{835,984,179,-1000,152,1000,-245,-50,-36,777,-1000,916,-337,-1000,-468,535,1000,-455,-1000,1000,360,197,-369,1000,1000,428,130,531,1000,-528,-853,-746,1000,1000,-1000,-218,536,937,-1000,-1000,305,501,-366,-1000,-588,331,683,-292,-147,-394,-1000,-632,-421,-602,496,-365,-1000,1000,-814,-62,-606,1000,-307,-490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{1000,744,-348,-809,-169,351,610,-365,-1000,-175,-578,-863,382,-277,-127,-1000,-453,-653,1000,1000,670,-274,589,309,-81,321,729,65,1000,895,-330,139,881,1000,753,-1000,-555,-1000,247,-456,476,-583,429,449,-52,404,655,-154,-732,1000,-1000,-142,-15,1000,-1000,302,45,401,-341,-1000,-896,815,658,715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String):long",
            new int[]{-961,129,548,-55,400,1000,36,629,-186,1000,-608,-32,659,-580,106,1000,1000,-213,-1000,841,1000,21,1000,233,1000,484,935,1000,1000,477,-172,-228,151,-333,-431,-649,267,491,-1000,-791,54,-1000,-34,-1000,191,-945,-31,-667,-539,-467,-739,466,-493,-1000,-528,1000,-615,712,875,336,69,871,-219,-626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{-452,-411,-1,16,1000,12,184,-1000,595,71,-674,-1000,455,-636,-920,343,1000,929,-1000,869,-1000,-79,972,-931,450,-33,-1000,-484,652,1000,-676,114,-263,-466,1000,-599,955,1000,-468,-407,-230,162,658,1000,240,977,-472,-1000,513,719,386,-162,471,-656,-573,78,-510,-624,726,-1000,213,1000,-123,921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{771,210,-306,196,869,1000,-276,-210,1000,813,-578,-928,-441,148,-494,835,1000,652,-735,-221,-446,-98,521,-865,-1000,-969,-59,1000,459,400,-928,756,1000,533,-359,-688,-1000,503,-139,-1000,238,-1000,-905,-847,-496,1000,-112,-1000,-439,461,-63,1000,-1000,-333,-773,-420,-550,-800,302,-768,960,-1000,184,562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,java.lang.Long):java.lang.Long",
            new int[]{-276,-492,32,-526,-220,330,-978,-995,1000,309,1000,-308,-210,433,878,-1000,-1000,1000,234,-706,1000,1000,-986,862,487,727,1000,898,-805,-62,1000,367,-133,-902,128,1000,606,368,1000,-496,-881,-385,-670,301,488,32,-676,459,638,-492,-802,666,-798,-918,-681,134,-888,-315,-1000,19,168,144,-1000,-903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Long:ODM=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{408,-321,-336,70,-241,-433,856,-379,131,-718,-34,-102,-838,474,-448,-278,-102,127,1000,298,740,375,107,44,-281,-601,523,-141,735,-940,-794,288,456,272,7,-1000,-126,197,829,209,1000,261,396,-1000,711,429,1000,360,-519,-445,204,800,340,775,651,663,-872,-357,839,-398,-563,-548,1000,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{-56,-468,-932,-918,129,-1000,339,-485,272,-544,417,1000,-1000,459,-443,-1000,156,48,-1000,1000,142,-51,358,-940,-385,-1000,171,-1000,922,-1000,-974,1000,890,1000,-1000,-1000,966,-118,704,263,-967,1000,768,-1000,1000,895,1000,1000,-1000,-466,719,450,-29,439,1000,-715,-1000,-691,1000,887,-590,-1000,721,-427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getLong(java.lang.String,long):long",
            new int[]{300,-786,-121,-153,14,-684,-387,-434,400,-191,327,553,-358,1000,-538,-573,135,204,-400,-400,485,171,142,-52,-400,-280,614,-226,-400,63,-705,-179,767,1000,226,-716,-23,603,662,-736,48,660,403,-572,-382,499,263,-928,1000,-535,-204,534,4,-35,292,-13,-830,-439,303,92,-660,129,1000,429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{-749,192,-991,-307,263,-1000,-1000,1000,997,-184,-602,-1000,1000,272,530,400,1000,-1000,-579,-665,-102,1000,-210,-723,1000,-683,-1000,-801,-1000,-1000,917,462,539,-1000,-485,-308,386,729,-1000,-245,499,979,954,1000,-1000,1000,-200,-263,-369,-1000,-595,1000,496,-1000,-1000,-692,185,1000,-757,-568,-415,-208,436,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String):java.util.Properties",
            new int[]{654,252,639,36,972,-1000,-1000,1000,1000,1000,1000,-1000,-438,-720,-1000,916,615,-789,-1000,-331,-81,-320,-946,-1000,-104,-557,281,-1000,-500,861,1000,570,516,-277,-861,286,-332,-360,-352,-942,-734,666,550,-1000,-531,463,200,559,1000,-289,509,-119,-707,-746,399,-137,-661,523,-688,1000,-284,-278,-400,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String,java.util.Properties):java.util.Properties",
            new int[]{-1000,342,-117,605,-364,651,202,944,1000,-1000,-479,-682,1000,-920,1000,-1000,45,-1000,191,-871,875,-1000,847,627,-82,-690,1000,-1000,178,865,1000,-1000,-19,-30,-1000,1000,293,-993,-458,-713,-1000,-953,837,-627,1000,-135,-667,-515,-90,268,1000,-345,280,433,1000,1000,-327,1000,-1000,-1000,502,-1000,1000,769}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:java.util.Properties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperties(java.lang.String,java.util.Properties):java.util.Properties",
            new int[]{-732,498,-577,-777,-1000,354,-40,-1000,-448,-838,770,382,-400,-887,1000,-1000,1000,-1000,894,-41,1000,-1000,426,528,-135,251,985,-1000,-1000,1000,1000,-1000,344,507,-1000,1000,581,-1000,491,-713,-1000,28,1000,-453,729,72,-1000,1000,949,1000,1000,-614,812,1000,1000,822,-1000,1000,-1000,-1000,-1000,-1000,1000,-6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperty(java.lang.String):java.lang.Object",
            new int[]{734,432,459,-666,283,-408,881,-813,-85,-149,-643,-84,-735,-669,-135,1000,309,957,-817,634,-355,-408,-532,693,-845,1000,1000,1000,376,-1000,-203,-1000,559,-1000,124,-49,-1000,-977,-1000,1000,973,-983,1000,-688,1000,-118,-520,-1000,-816,213,-171,-1000,1000,-1000,1000,-350,345,-1000,-1000,-892,816,-224,52,-914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getProperty(java.lang.String):java.lang.Object",
            new int[]{-1000,-720,158,1000,527,-1000,-815,1000,-1000,-445,-1000,1000,-464,-1000,428,-286,-1000,1000,1000,332,66,-951,115,476,-1000,-111,776,488,-16,-1000,1000,-1000,740,243,-1000,-1000,-895,-279,1000,-299,822,1000,1000,142,625,-981,-100,-108,685,-1000,1000,-535,1000,-1000,1000,1000,918,-272,-1000,510,-62,-1000,-900,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String):short",
            new int[]{-26,171,-816,-1000,-963,-509,-626,23,-1000,-576,-889,-206,1000,-282,1000,477,-34,1000,-438,-1000,-806,591,-1000,1000,-517,-241,348,968,-640,-559,675,-1000,405,-255,40,1000,685,-692,1000,-500,-531,-541,1000,395,921,-1000,-866,-737,1000,87,178,567,-189,-641,478,741,-52,-49,-601,-377,-1000,878,-587,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String):short",
            new int[]{174,-783,-226,-925,-631,-564,-138,399,-513,-1000,-558,1000,400,717,-1000,-1000,490,1000,1000,-294,-852,-12,20,483,-62,-736,518,1000,-166,-915,67,-651,-17,1,265,-1000,617,-706,1000,440,-1000,18,388,309,977,-591,-420,64,400,518,483,211,634,-648,-226,840,-345,502,346,782,-626,540,1000,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Short:MQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{-601,-225,164,200,-1000,137,942,-1000,709,62,-1000,1000,-423,695,584,-1000,1000,-808,203,1000,1000,-1000,-843,747,1000,-296,-1000,-714,-905,-804,373,677,815,396,-400,-1000,1000,281,627,-695,830,1000,-437,-1000,-381,122,-246,-226,1000,943,-484,-265,494,-179,972,1000,469,1000,1000,471,-975,-938,349,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{960,441,-886,-679,1000,-1000,1000,-1000,1000,1000,1000,-1000,281,148,9,-894,-256,296,682,338,1000,672,-1000,-1000,1000,1000,-598,-1000,1000,-1000,1000,-1000,1000,1000,-1000,-1000,1000,-1000,178,-41,1000,369,-598,1000,-1000,-486,1000,27,378,1000,-1000,1000,-1000,-311,-224,1000,-661,531,477,-1000,159,263,1000,-852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Short:MQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,java.lang.Short):java.lang.Short",
            new int[]{47,525,-118,-744,-13,-499,324,-299,-770,-542,-816,842,-21,-89,209,290,-13,337,-907,747,598,-1000,-790,-61,871,615,-991,543,-493,353,925,-202,727,821,625,-588,224,-138,964,346,933,288,-912,136,-570,-3,-199,504,505,288,-455,180,-317,-315,-914,113,-718,73,-613,-471,993,-478,26,-342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{304,534,983,-622,-247,-934,-703,-278,-155,693,373,1000,808,182,-75,165,299,382,1000,-1000,522,-498,65,207,1000,-580,401,863,595,369,-707,117,-1000,170,-1000,266,582,-355,-1000,-237,1000,-950,34,1000,1000,-634,1000,-1000,800,1000,-792,-880,853,-717,1000,260,-1000,-599,-443,278,533,-358,-919,-98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{1000,681,699,-420,748,1000,281,-1000,-1000,478,-217,-527,-228,964,-1000,831,-947,871,1000,-457,1000,-1000,1000,-228,-319,-1000,332,411,1000,1000,399,-937,-400,1000,-228,-209,1000,683,-494,1000,370,827,-1000,655,1000,-1000,-19,736,1000,-135,505,-178,698,-1000,898,268,889,93,91,-320,1000,955,-234,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Short:MTA5", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getShort(java.lang.String,short):short",
            new int[]{-470,228,93,1000,-288,-769,279,-258,-235,-227,-1000,20,1000,-400,842,-275,-4,-100,-188,-71,-1000,109,-649,1000,-417,1000,-1000,217,-1000,-121,246,-331,611,-29,-558,-1000,-687,973,-29,399,-1000,-1000,-84,444,1000,1000,-614,-1000,175,767,260,-552,-1000,1000,-1000,900,-170,144,-140,1000,-629,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{694,429,873,-1000,379,-518,384,-1000,-777,-1000,1000,1000,95,402,1000,-75,1000,-452,-263,878,1000,-298,496,-1000,1000,1000,160,-307,1000,-333,-906,-767,-122,392,1000,292,1000,967,-965,-724,190,-1000,433,439,-1000,-1000,-1000,295,-475,1000,-936,1000,-1000,300,68,-128,-412,-1000,1000,1000,841,-1000,-1000,-119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{862,636,539,-598,267,-205,673,-468,-28,-292,1000,538,980,282,684,-743,132,344,-941,-92,-1000,-506,424,0,-16,-511,1000,110,-11,987,-926,-367,-902,635,727,-1000,916,-1000,376,483,1000,-1000,-603,-98,-1000,-668,816,-1000,121,963,-112,-221,-361,168,1000,396,588,-90,782,820,1000,-414,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String):java.lang.String",
            new int[]{497,-408,227,-1000,1000,-668,1000,-240,-844,-1000,1000,339,1000,-197,1000,14,450,-1000,-876,1000,213,-461,1000,197,1000,1000,183,-293,-85,-108,-1000,782,-475,312,1000,-288,1000,-48,-1000,-1000,659,1000,1000,699,-1000,-838,-1000,-731,-990,947,-917,-101,-1000,-599,715,267,-1000,-1000,1000,747,1000,-1000,-1000,385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.String:KzB4ODAwMDAwMDAwMDA=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{117,-408,-222,161,-706,-254,-1000,1000,-1000,351,-373,257,-1000,1000,-158,-1000,1000,1000,-1000,338,295,-218,1000,828,-1000,376,-1000,-930,-230,328,-459,-1000,-1000,-686,1000,87,-153,-645,99,-1000,57,1000,-970,-737,-829,-577,-176,132,-460,-4,-584,1000,-537,1000,326,958,-1000,855,990,-305,-1000,-1000,-434,323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.String:NjE4LjQ2Ng==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{767,186,-518,-448,351,1000,-128,1000,-298,1000,-748,-538,-1000,165,-618,-1000,-534,802,-378,-688,1000,-7,1000,452,-1000,771,-1000,-858,1000,365,188,-74,-596,413,1000,376,-105,-652,539,-1000,1000,-294,-998,-661,-637,-1000,89,-874,-162,1000,656,1000,-150,1000,537,709,-174,684,-1000,5,-393,-1000,-400,-629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{-5,252,272,768,583,-1000,-1000,433,-705,286,1000,-1000,-808,819,-393,-1000,1000,1000,-859,181,-918,-1000,559,37,-572,-1000,-405,971,-1000,61,-1000,-1000,-536,-566,727,-645,-108,-1000,-476,-522,-277,-305,156,-551,646,1000,-1000,1000,-1000,-1000,-1000,894,-805,-384,-1000,1000,728,-863,-1000,638,-598,150,169,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getString(java.lang.String,java.lang.String):java.lang.String",
            new int[]{-744,-87,868,-140,-80,-820,614,317,-1000,642,1000,-441,254,793,168,-1000,1000,524,-193,1000,-1000,-792,119,-1000,254,0,287,886,-1000,468,-471,-234,-336,271,1000,-1000,420,-653,513,814,1000,-11,-389,-289,306,958,-1000,1000,-1000,-658,-896,-1000,-1000,1000,-1000,1000,1000,-1000,-124,159,1000,1000,-805,-359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:1:29:java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getStringArray(java.lang.String):java.lang.String[]",
            new int[]{310,993,27,-550,-418,377,-397,-159,133,-373,1000,248,-529,-187,646,-1000,490,693,566,-1000,-1000,347,-649,-1000,254,-406,429,889,1000,1000,-470,293,-1000,1000,-934,-186,749,-1000,-282,129,323,-617,1000,-567,-327,655,-175,-1000,-186,-714,-902,-1000,-265,-490,999,543,-669,294,-41,-1000,-260,213,1000,-802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:0", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getStringArray(java.lang.String):java.lang.String[]",
            new int[]{-871,-546,-108,22,-69,326,-665,-1000,1000,144,449,21,-158,-612,991,-1000,-1000,-718,1000,-1000,1000,-400,645,1000,-136,-661,149,10,-357,-1000,-334,-652,-628,-1000,365,70,961,-838,-309,-1000,-1000,392,-1000,1000,1000,118,1000,-40,-40,-674,-228,-1000,-1000,21,-416,-949,-896,321,611,-201,-365,-706,-345,-355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{-687,-675,908,516,1000,-1000,-7,-761,275,-833,-802,1000,1000,810,-1000,-1000,273,-921,1000,940,99,204,-520,-1000,-909,-968,-1000,-1000,1000,-134,693,-1000,-1000,37,873,1000,1000,1000,-1000,-836,-1000,-126,-375,-296,-536,-411,-299,-1000,1000,-605,537,1000,-948,614,-1000,1000,-1000,-701,1000,-1000,1000,-484,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{-59,-870,-111,-198,1000,1000,-1000,-1000,1000,-641,-992,-823,698,-656,1000,-303,102,-71,555,108,873,-1000,-264,771,1000,-590,-888,-178,-1000,-1000,-73,1000,1000,-13,638,-427,404,-1000,-607,749,1000,-136,475,1000,-358,718,-437,803,-945,-12,-1000,804,1000,-1000,368,1000,-170,-268,-1000,487,-798,-442,-1000,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{830,219,-111,564,895,-734,-17,983,370,469,-1000,-170,698,-137,-398,-639,105,-469,555,878,154,-526,-812,-855,32,-574,-888,-392,941,-1000,759,134,-1000,679,190,-118,624,753,-983,-401,-1000,-140,-60,-348,-153,-578,962,-273,101,75,-1000,453,-506,-624,-184,710,-373,-385,-294,-1000,1000,-216,77,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String):java.util.Vector",
            new int[]{1000,-402,-17,576,407,383,-1000,-588,1000,-400,-1000,-524,-702,-1000,807,-30,-510,-61,-815,1000,514,-369,-1000,223,1000,-217,756,-1000,431,-1000,374,1000,385,462,-26,-1000,342,237,-865,-76,790,-1000,522,810,-78,-107,-524,657,-1000,-197,-753,257,364,-1000,66,511,-769,-386,-1000,391,174,629,-1000,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{241,-369,-971,-1000,1000,1000,-403,-1000,330,-199,-573,-1000,692,-116,1000,-718,-1000,-311,72,550,594,455,238,103,801,-173,-35,-284,200,176,929,-535,-479,291,477,-1000,-988,295,1000,1000,-771,520,-501,958,-1000,1000,-1000,-1000,1000,-1000,730,-215,-927,-818,-288,-1000,437,1000,-428,1000,1000,414,530,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{589,-408,818,-327,-1000,256,927,330,459,562,-245,795,-794,-1000,425,1000,74,518,1000,769,-286,-485,32,111,-1000,1000,822,921,1000,-1000,393,-506,-245,-1000,-392,795,465,-208,-750,-324,-1000,-664,1000,-699,189,238,-374,817,-90,-588,139,387,1000,194,-857,-517,-42,244,1000,140,-458,-1000,673,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{-1000,-516,-211,-1000,859,701,1000,-862,1000,913,579,1000,-560,285,173,-1000,688,367,-1000,-1000,1000,-222,-395,471,337,954,206,844,292,-896,-813,72,-1000,1000,513,-1000,-1000,1000,-344,-504,905,-587,-1000,-1000,885,-306,812,367,-403,1000,-1000,78,-271,980,1000,710,-428,684,1000,1000,-1000,-1000,93,-859}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:java.util.Vector", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "getVector(java.lang.String,java.util.Vector):java.util.Vector",
            new int[]{-1000,-666,-271,281,1000,629,122,282,1000,-764,310,99,820,1000,1000,-133,-1000,562,-220,754,928,89,-641,458,1000,453,-230,-846,1000,789,1000,45,513,-371,125,-1000,-1000,825,521,358,1000,-300,-1000,694,50,-51,195,192,1000,190,406,1000,-1000,-621,1000,-631,-384,667,-1000,-652,130,831,1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "isInitialized():boolean",
            new int[]{-107,843,969,-62,-543,-583,-964,-642,-147,-598,704,-1000,-1000,1000,485,972,-101,-1000,-1000,-760,1000,-394,674,-863,445,-1000,1000,1000,272,-337,585,537,-361,-542,41,-1000,1000,-1000,-565,1000,797,64,-1000,-314,-1000,-1000,1000,110,288,-168,-567,-116,-553,1000,1000,24,158,-400,676,-598,-1000,733,30,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "isInitialized():boolean",
            new int[]{1000,-519,109,-1000,-1000,-163,-92,-125,-176,895,1000,-1000,-1000,400,-287,70,-400,1000,-1000,-504,-440,257,327,469,-541,236,-1000,843,-1000,-1000,657,-753,499,20,739,-1000,74,-998,78,-66,-369,310,-1000,-537,-1000,-896,-615,1000,-228,955,364,250,162,938,586,658,1000,-1000,-216,-1000,-239,29,-432,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream):void",
            new int[]{853,-276,24,1000,-653,820,890,-727,-378,1000,379,715,-1000,100,326,-664,-816,-714,-125,1000,938,840,-199,1000,976,-586,-913,-498,-256,-293,-1000,-821,-219,1000,-1000,-229,264,-489,-959,1000,-191,-1000,1000,-152,-173,-1000,1000,202,-1000,-813,776,-1000,1000,-869,951,-933,-199,19,-419,531,-233,882,289,-470}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream):void",
            new int[]{-967,348,-786,240,1000,-643,-215,225,508,438,379,-1000,-116,-338,-620,736,-317,975,-1000,520,-1000,-865,-148,297,-386,172,96,340,-523,-1000,595,-1000,1000,780,-214,-1000,-52,840,154,741,-32,-1000,-155,-666,-496,-1000,-628,-120,-762,-1000,-250,1000,34,-1000,160,-1000,80,-1000,-1000,597,-513,78,-855,-143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{1000,-591,-886,-1000,796,-1000,-818,-250,966,-991,-206,-355,-580,-1000,1000,-311,-1000,156,-183,-485,284,-213,-722,-239,202,337,1000,95,111,254,1000,-329,-602,915,-492,-559,-690,410,-647,1000,-1000,1000,1000,692,-836,1000,47,6,630,877,1000,-1000,1000,301,-841,902,-1000,-400,-1000,-1000,1000,422,-678,-876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{819,441,519,338,673,-1000,-612,377,-608,-877,-350,543,-264,-553,824,-501,-549,308,-109,-478,352,711,1000,64,-220,382,416,91,-274,-1000,483,-161,-987,627,-1000,180,-478,2,237,395,-474,911,-249,-907,1000,-267,-428,1000,-1000,808,-36,-848,35,1000,-637,153,1000,211,293,-66,33,-116,-347,388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "load(java.io.InputStream,java.lang.String):void",
            new int[]{319,-240,-287,1000,75,-422,-61,906,-574,202,-704,-224,-80,1000,17,377,-504,-737,198,-700,215,136,-266,931,339,-590,751,606,-31,-11,-1000,-439,-167,-662,691,310,-337,1000,-164,-664,378,330,-469,-1000,123,-880,482,-1000,-22,450,773,742,207,29,-787,-1000,-719,-52,1000,409,463,312,-712,-23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{1000,-678,864,-748,-91,-11,-115,965,-105,-121,-99,694,-904,0,-1000,-665,675,494,1000,-436,1000,-1000,664,-1000,-930,492,-350,-1000,-307,-878,-440,-646,-98,758,783,949,-800,593,-487,346,-50,191,-1000,-338,-552,633,-400,-51,-219,-650,-634,785,291,670,-263,-400,1000,-617,-1000,-296,-183,-492,-836,324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "put(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{1000,66,599,-40,-501,-692,-352,-128,-1000,-385,-369,-381,-1000,-342,-747,1000,466,565,1000,-610,400,-1000,325,-336,-1000,1000,-1000,-400,-669,-219,-8,-1000,-770,643,548,663,-794,720,43,892,553,-249,-67,64,1000,827,492,66,-419,-155,-708,-811,579,769,116,-1000,-122,-344,509,-773,-1000,432,-115,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "putAll(java.util.Map):void",
            new int[]{-400,672,-56,1000,162,1000,-1000,-1000,1000,706,1000,-925,834,372,-247,569,-326,1000,74,1000,-1000,-598,-930,-605,-108,-99,646,1000,-1000,-1000,-1000,-1000,-1000,-638,1000,801,731,1000,-673,709,-1000,-1000,863,-281,-718,-159,-1000,1000,242,-272,1000,513,1000,-76,1000,903,1000,1000,1000,202,-1000,1000,1000,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "putAll(java.util.Map):void",
            new int[]{-584,171,-56,-230,162,568,-795,-913,315,228,1000,902,328,577,1000,22,160,1000,1000,-748,138,-1000,51,-1000,-108,305,987,-236,-1000,201,-643,-523,-187,-638,782,249,1000,-1000,-174,508,-612,388,1000,168,163,135,487,514,1000,-272,736,-1000,820,-529,1000,911,922,153,-713,907,249,-130,920,-209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "putAll(java.util.Map):void",
            new int[]{-135,672,-38,804,-327,568,-1000,-1000,287,794,1000,-800,655,1000,-186,156,160,1000,74,829,-1000,-1000,-1000,-1000,-1000,175,646,1000,-1000,-943,-1000,-1000,-1000,-1000,1000,463,292,1000,-1000,1000,-612,-809,1000,-135,-223,426,-866,1000,608,-272,1000,57,641,-137,1000,1000,1000,658,1000,690,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "remove(java.lang.Object):java.lang.Object",
            new int[]{608,-960,28,69,-89,436,-75,-841,-105,-986,375,-914,1000,1000,-496,744,-695,-161,-224,-129,-285,451,-236,694,1000,-476,733,480,-47,-236,548,-194,1000,-558,843,732,-555,90,-1000,539,-113,-343,257,1000,-980,858,21,490,515,129,-919,1000,-838,0,1000,-910,9,-1000,1000,710,-1000,-1000,-523,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "remove(java.lang.Object):java.lang.Object",
            new int[]{-608,672,149,-650,-790,296,-1000,-1000,-285,-511,-564,708,-231,145,1000,-608,-303,-339,-1000,-149,-215,-982,150,881,-517,-784,-815,-532,-541,649,718,151,-34,733,-164,-434,64,495,703,-943,325,-404,-663,433,-540,525,-1000,-998,-710,-216,-117,-899,-522,690,-854,-413,-818,-507,-1000,196,1000,-865,415,-404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "save(java.io.OutputStream,java.lang.String):void",
            new int[]{-670,534,354,656,1000,276,-127,-216,-212,283,-484,728,1000,88,702,417,-508,280,-1000,-1000,-1000,-1000,-309,501,1000,1000,-644,-564,-505,-312,-532,1000,275,182,-139,-1000,556,-109,379,-883,104,-1000,841,-1000,514,-118,-640,-1000,1000,680,1000,212,482,-270,-46,-406,-558,436,881,-873,736,760,-79,-398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "save(java.io.OutputStream,java.lang.String):void",
            new int[]{-505,-342,-427,1000,-64,-119,-501,-91,-412,-430,620,1000,532,411,1000,896,-403,192,613,-619,-47,-449,-400,51,836,-1000,-77,47,-1000,-932,800,186,-1000,768,-584,-742,931,-555,-57,-1000,64,-602,942,-994,1000,-896,-1000,-883,398,746,356,1000,1000,-397,1000,20,-979,-342,-1000,63,825,1000,1000,183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("VOID|getInclude=java.lang.String:IC0tMTAwMCA=", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setInclude(java.lang.String):void",
            new int[]{-606,39,-661,1000,-1000,1000,1000,604,-102,-1000,-1000,1000,1000,-1000,1000,-1000,174,-480,1000,-823,1000,1000,-794,783,-422,-1000,-185,-1000,-719,195,-1000,-1000,1000,1000,-334,1000,-1000,890,-591,-252,-565,1000,-580,-387,286,524,-623,854,-1000,-809,98,-1000,-1000,737,-1000,813,-930,-305,-660,809,-412,398,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("VOID|getInclude=NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setInclude(java.lang.String):void",
            new int[]{30,-573,763,-1000,847,-551,242,1000,1000,479,68,-1000,-948,805,-1000,-144,203,-190,-706,34,206,-188,1000,669,-921,-573,-1000,52,174,-531,1000,379,-1000,-968,1000,-431,-15,-950,-349,791,-14,-1000,1000,-168,519,214,366,-91,1000,515,-995,1000,-658,57,1000,-843,487,264,1000,-432,1000,-596,1000,771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setProperty(java.lang.String,java.lang.Object):void",
            new int[]{-350,405,-617,-1000,-710,343,1000,-48,-698,560,89,-282,-979,-1000,326,1000,631,1000,399,-334,-896,39,-871,262,-1000,637,-173,-287,1000,-172,79,583,527,-21,366,194,302,950,-1000,-395,-181,-344,6,1000,739,481,-642,344,1000,1000,1000,1000,799,-450,1000,48,-339,-376,-115,-641,1000,-622,-137,46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setProperty(java.lang.String,java.lang.Object):void",
            new int[]{761,246,-939,624,145,985,252,341,-731,-993,1000,-302,1000,-1000,-614,1000,-1000,1000,-339,863,631,-374,732,-1000,450,528,140,-1000,-291,1000,808,-1000,-849,-925,876,596,387,-588,1000,993,-724,-449,-430,-134,-73,-246,685,-1000,1000,1000,1000,-988,154,-73,1000,-1000,-1000,-874,116,320,1000,-1000,-718,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "setProperty(java.lang.String,java.lang.Object):void",
            new int[]{-659,681,864,-1000,-880,-425,1000,434,1000,973,-668,-1000,-642,-1000,946,81,396,-576,1000,-115,-951,1000,-397,527,-992,-365,-631,774,1000,-1000,-325,691,614,1000,-9,-381,707,628,-800,-520,797,716,-22,-1000,857,-781,-342,870,-374,-97,293,-37,857,43,-886,213,-1000,582,82,-1000,-327,534,545,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.ExtendedProperties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{465,-114,-231,1000,-1000,476,-411,1000,161,-1000,-584,-806,-1000,-929,-1000,443,1000,-113,-882,-469,-1000,-357,968,-1000,505,-991,-1000,1000,-1000,-471,1000,-1000,844,-1000,117,-956,560,-674,393,-777,-1000,382,-1000,302,-1000,-1000,-565,392,231,42,151,-801,1000,1000,-1000,-396,-684,-203,-487,597,-351,1000,243,-881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{33,-828,469,1000,-960,164,-286,-922,-304,-1000,419,-275,256,475,-184,1000,1000,-1000,-1000,453,-1000,-313,1000,-1000,-1000,-1000,-1000,614,-1000,-676,-959,785,-608,687,-1000,-666,-919,-437,731,724,-130,76,-1000,1000,-1000,23,-7,1000,-473,920,-374,1000,227,960,822,547,11,-1000,1000,201,-901,1000,1000,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.collections.ExtendedProperties", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "subset(java.lang.String):org.apache.commons.collections.ExtendedProperties",
            new int[]{-1000,-168,-411,1000,-931,-409,622,639,-598,-930,-191,-888,-598,708,-1000,-986,-11,382,-498,-849,-1000,-714,248,-760,-771,246,561,-995,-1000,857,50,-1000,925,-1000,943,-1000,1000,-1000,741,-295,-873,-724,-906,-464,-1000,-23,1000,570,-431,-318,588,-1000,-139,721,-1000,48,-742,-938,-932,346,1000,516,-902,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{300,711,153,-838,-670,-842,-800,363,-204,683,746,-973,-529,846,-873,-239,718,-629,524,-384,-860,-149,872,-995,691,-570,-685,668,-970,-334,651,781,206,-824,-225,-812,145,-395,765,-760,-217,948,996,-271,-764,329,385,20,-100,-27,-266,623,1,-722,33,-806,810,670,507,209,-575,-788,-748,-570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.collections.ExtendedProperties", "org.apache.commons.collections.ExtendedProperties", "testBoolean(java.lang.String):java.lang.String",
            new int[]{1000,-519,-96,1000,321,209,-774,587,-1000,24,-1000,-1000,447,-1000,-1000,-491,1000,-432,-991,-34,-580,990,-400,400,-882,1000,-960,1000,-412,-1000,227,1000,886,832,1000,-927,-400,118,-330,1000,-1000,216,-270,-737,1000,400,-1000,-1000,252,-400,1000,251,1000,668,-1000,1000,-233,148,1000,1000,1000,-475,-871,-1000}));
    }
}
