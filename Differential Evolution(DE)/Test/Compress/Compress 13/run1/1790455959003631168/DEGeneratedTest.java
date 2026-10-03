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
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-1000,-129,-1000,746,-1000,284,1000,-372,-1000,1000,602,-122,1000,335,-640,-1000,80,-1000,11,475,-1000,130,-931,-318,221,-251,-638,324,865,-1000,1000,-149,504,-1000,-802,1000,346,-712,289,782,-82,-4,1000,1000,1000,-1000,-603,537,-818,1000,-13,-1000,-727,-1000,-383,359,202,-1000,-395,1000,227,1000,-805,286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{830,1000,1000,106,439,421,-671,-11,972,962,-99,427,-294,-1,810,521,824,1000,-414,1000,1000,128,436,362,-346,1000,475,902,-922,-252,525,1000,1000,-152,1000,-656,43,-905,650,164,-764,77,995,-1000,38,-370,-1000,-289,1000,-146,-1000,-54,-1000,228,140,1000,354,-1000,-628,-595,-209,-1000,705,914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-400,1000,-641,871,-457,-1000,779,-508,-1000,1000,298,1000,-75,1000,-1000,-1000,-1000,-117,719,1000,-662,1000,-1000,-291,-170,-1000,46,1000,-549,-1000,-1000,-121,-1000,386,-802,-627,750,-712,-256,782,-392,874,-1000,1000,1000,-1000,-522,650,608,1000,1000,-1000,-97,-1000,-1000,-1000,1000,-764,-1000,1000,1000,1000,240,-688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-406,-1000,-1000,-686,-302,296,579,-944,-464,-455,-670,1000,739,9,801,1000,-626,-235,-716,-399,-546,-741,605,666,-1000,1000,-361,-1000,564,1000,1000,511,209,-54,-1000,645,-1000,767,295,1000,-837,-1000,-327,-201,914,812,97,821,425,1000,-224,-272,-736,594,-274,1000,-1000,-404,-211,-917,-879,272,269,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-1000,-1000,-943,-1000,-724,84,212,-530,-455,-184,-157,853,430,503,1000,1000,-749,-396,-700,125,-996,-782,1000,370,-656,36,-668,-1000,-65,409,1000,-1000,504,-806,-1000,-70,-1000,-352,654,778,-82,-999,-681,-1000,1000,835,-193,259,-176,-1000,389,405,29,-1000,-1000,359,-1000,-745,-1000,-238,-1000,151,723,815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{493,-1000,-852,-702,622,-897,-286,-773,-25,-1000,803,1000,703,-231,-451,656,-1000,-514,1000,-564,-1000,-610,724,150,-336,-1000,284,-660,1000,1000,1000,-1000,-1000,1000,977,141,231,425,1000,-312,470,-323,-1000,-441,860,770,515,349,878,-1000,1000,609,1000,1000,-688,-1000,-610,38,-1000,-202,1000,-651,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{612,-1000,-1000,-1000,471,-51,-516,-835,-331,-1000,173,1,1000,470,-318,991,-877,-817,590,281,-1000,-1000,492,596,-794,-1000,622,-870,945,477,1000,-1000,-1000,93,977,480,-279,358,1000,-1000,547,-630,-1000,-287,1000,1000,515,-76,878,-1000,1000,1000,1000,1000,-1000,-1000,-997,196,-1000,-634,446,-651,957,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-295,-1000,-863,-1000,-470,916,424,-643,1000,111,-670,1000,381,9,801,1000,100,1000,-716,-117,122,-471,1000,666,-1000,1000,-798,-846,524,1000,1000,1000,789,-86,-967,1000,-882,767,295,1000,-1000,-1000,1000,-1000,481,460,-18,851,567,1000,-1000,-272,-830,594,-258,1000,-1000,-824,30,-548,-879,-41,271,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{3,-1000,-781,-392,-116,-441,212,-771,-128,-613,-73,670,535,443,290,522,-749,-765,-109,230,-1000,-832,436,355,-530,-372,-276,-976,25,186,940,-594,-275,63,-1000,142,-1000,-115,654,571,75,-730,-1000,-235,1000,359,-78,248,-176,-220,797,191,29,103,-570,-659,-828,16,-1000,421,-131,151,273,699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{639,-673,139,336,978,464,847,-964,-310,-67,823,-522,-1000,518,-358,-516,-333,1000,-555,-831,-30,-89,-621,-781,543,-519,-353,-473,1000,1000,443,-475,506,331,-74,-275,202,-676,-49,529,1000,829,794,141,-207,737,197,753,737,89,-215,282,819,652,-921,383,310,797,1000,400,-90,-348,-924,-58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-1000,-1000,-690,1000,405,145,-409,344,874,1000,-795,-243,-43,560,-456,444,1000,828,-1000,370,-292,161,-1000,-294,-637,486,-891,472,190,481,-534,1000,-1000,36,-997,1000,203,-211,-614,-248,1000,1000,-1000,1000,-1000,-1000,599,-253,226,-1000,-858,-1000,181,517,193,1000,-392,806,-1000,1000,-57,1000,153,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-484,-1000,699,393,-896,-554,33,798,1000,-773,1000,-347,832,724,-62,-1000,574,-61,439,189,439,595,863,-198,-318,1000,-1000,83,-1000,-284,-538,1000,-362,341,708,405,204,358,-341,309,16,308,-859,-1000,1000,249,771,-429,-570,-706,264,-583,1000,268,1000,-930,-273,-847,-1000,-1000,410,39,-165,200}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-347,-1000,-690,1000,1000,-1000,272,2,415,1000,-919,-228,-1000,-326,-291,849,596,-139,-1000,-651,-1000,-679,-967,-746,72,-1000,-437,-993,1000,1000,-120,453,-550,-212,-1000,1000,99,-860,-1000,361,1000,656,-623,1000,-1000,-1000,962,396,-8,-794,-1000,-920,-67,841,-355,1000,-265,1000,58,981,16,945,-24,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-461,-1000,-321,826,65,-104,34,-147,507,1000,-1000,-1000,-1000,512,-443,807,1000,1000,-1000,-189,622,393,-1000,-177,-154,-189,-247,-276,81,949,-814,192,-618,602,-794,1000,-273,-130,383,-160,1000,1000,-1000,630,-724,390,1000,677,-299,-866,-832,-707,-78,166,-1000,1000,116,873,728,1000,-882,1000,-640,-714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-749,-1000,13,1000,-654,-243,-417,-1000,1000,627,-264,110,374,884,-928,129,898,1000,-22,756,541,1000,174,-416,-202,1000,-1000,-748,-573,583,-911,1000,-1000,583,-54,405,38,62,423,-502,118,817,-941,-772,67,298,1000,-331,1000,-1000,-11,-378,-526,394,-197,1000,326,-220,-533,-312,-221,774,-678,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{93,-1000,1000,393,-258,-1000,-161,-790,1000,-1000,1000,-316,974,1000,-341,-1000,1000,59,1000,288,1000,1000,218,-724,-854,1000,-1000,-722,-1000,-504,-1000,1000,-362,1000,1000,1000,815,1000,31,54,237,52,-776,-512,1000,1000,672,-16,1000,-800,1000,-154,509,-242,1000,1000,-180,-877,-411,-109,-836,711,-521,200}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-1000,-1000,777,575,-1000,-559,28,214,1000,-122,-161,-587,604,1000,-561,-125,600,293,-87,459,1000,1000,-16,79,-757,1000,-1000,-340,-1000,131,-1000,1000,-994,642,423,768,16,448,376,-556,-138,1000,-1000,-772,224,537,839,-302,94,-1000,-314,-174,282,46,96,254,-86,-483,-816,-312,-307,765,-804,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-32,-557,334,642,-162,-295,-441,-338,1000,-186,541,-77,-791,305,-1000,-1000,-1000,259,-217,772,381,667,825,-40,150,-118,295,596,-663,-405,180,1000,247,244,-165,712,49,361,749,829,731,-1000,-479,-317,850,1000,538,959,-481,-563,-72,-79,-56,-357,-300,173,-521,1000,964,473,-1000,1000,-206,-332}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-1000,667,610,-738,-1000,-180,-711,-57,612,747,-694,719,-440,752,570,-196,183,-298,-95,-215,577,-227,713,269,-121,354,158,-1000,439,-618,-767,314,396,-208,-453,877,62,1000,-828,-37,-142,-1000,420,-65,-988,655,754,-788,-870,549,-87,-52,-818,368,539,-276,177,-1000,476,-555,-1000,381,198,-537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-171,-325,420,-292,-16,-709,-502,-486,142,-710,-621,-965,-722,547,155,798,871,101,-521,-675,44,122,-164,-429,-410,579,-38,625,-63,-646,819,523,-899,165,-546,811,58,187,288,154,569,-482,978,708,486,-250,835,-729,-161,831,-28,-746,-432,118,857,-555,-292,-932,735,-807,995,-632,-476,-818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{12,3,655,-249,1000,-185,-750,726,169,-1000,-935,-664,-828,894,58,567,-732,195,-742,-222,500,530,-654,-270,-145,1000,-610,658,-226,-915,92,253,-395,897,-618,817,-112,608,621,-52,-519,-610,1000,232,1000,448,622,-1000,559,539,-57,13,-548,349,946,-1000,-804,30,-994,-931,77,-109,243,-970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-965,311,-1000,-923,1000,992,1000,1000,-1000,615,-1000,-632,-730,883,1000,698,233,-1000,-1000,426,-1000,-1000,-660,1000,-257,621,1000,-657,-1000,-425,-1000,-1000,-579,-1000,226,821,613,-1000,-938,-1000,-1000,-798,941,-1000,-1000,-643,89,-1000,912,1000,1000,1000,8,1000,-1000,1000,1000,1000,-890,-1000,-897,1000,932,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-757,20,-261,-1000,1000,883,208,955,419,976,289,290,-627,136,952,-539,160,-1000,-648,283,-7,-460,40,1000,-61,189,1000,-415,-845,-428,-1000,226,392,-428,855,921,138,279,-1000,-533,-1000,-901,215,-819,-846,185,744,485,-487,-126,1000,1000,-776,610,-947,1000,192,413,-130,-92,-86,934,-1000,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-32,259,826,-422,589,-515,-131,-955,1000,-40,-68,-77,-564,208,-119,110,219,6,-217,-732,-211,-312,256,365,150,118,295,504,25,-204,374,535,-447,-439,-424,712,200,42,380,313,276,-1000,266,-167,386,972,538,-186,-481,545,560,-521,313,-140,-74,-213,-200,-67,1000,-1000,-405,168,-545,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-546,-333,476,153,-182,-535,-400,-1000,1000,-633,-191,-578,-736,125,224,256,107,445,-522,-666,622,-319,339,-754,-684,202,-400,775,336,-807,-267,1000,565,286,-13,855,-47,1000,773,92,1000,-1000,594,400,35,-810,1000,-1000,-389,820,-503,575,-808,224,1000,1000,-643,248,1000,-1000,102,-20,-354,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{100,-325,-657,-1000,1000,130,598,-486,149,267,-303,-188,1000,547,857,-313,27,-810,866,-343,1000,-861,-842,320,-410,548,1000,-655,-691,-154,-1000,-142,-149,-11,1000,438,1000,773,-340,1000,453,-89,-182,-734,486,1000,416,149,1000,6,-239,232,-11,-716,-234,335,-860,-956,1000,-928,-282,675,66,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-161,23,512,1000,383,-330,1000,-783,-970,-9,-522,-101,592,433,1000,765,-1000,-861,970,839,238,-1000,587,577,-44,1000,-44,464,330,-1000,1000,1000,-805,337,-1000,1000,676,-259,-107,-340,737,1000,242,-1000,703,61,-277,433,-39,-83,169,831,361,-203,939,-335,359,-700,-436,-848,-1000,-85,570,893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{108,-1000,174,1000,-1000,317,323,-764,252,92,-851,34,952,1000,1000,1000,-536,-471,1000,974,-12,-318,-475,804,-1000,598,-1000,830,-390,-768,736,185,-1000,-761,-809,119,124,120,-408,-58,1000,498,-495,-820,649,305,-286,-820,-411,-255,-697,599,-993,240,1000,-875,565,-1000,-163,-103,-723,-100,-708,505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{550,1000,-851,-1000,85,1000,543,-186,317,1000,1000,-477,-361,211,1000,-904,975,1000,-1000,-65,546,762,1000,51,-735,-1000,1000,-1000,1000,1000,-1000,-112,-154,310,-228,-1000,-1000,1000,-386,1000,-896,428,1000,1000,295,-444,490,-700,-1000,-1000,-1000,1000,1000,465,654,-997,969,1000,1000,1000,1000,-1000,-485,-608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{50,876,-717,-727,209,577,463,436,295,521,464,-156,-736,-104,856,-769,-552,-50,-476,105,-251,-269,-34,-781,433,-73,865,115,437,878,-358,-906,187,-445,-925,-793,120,513,-485,-380,-703,-312,766,82,-137,-759,-737,-900,-610,610,-173,-75,628,-549,985,465,649,170,608,-144,903,-582,447,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{430,1000,-444,1000,-705,-697,1000,-1000,-1000,420,-971,-699,-1000,796,-1000,1000,-1000,-157,1000,829,-663,-174,-358,247,528,1000,-615,1000,-1000,-474,21,1000,-1000,-617,-1000,1000,1000,-1000,523,-1000,1000,1000,-1000,-1000,-614,1000,120,1000,762,818,-577,1000,640,-1000,1000,912,-240,-580,-541,-1000,-1000,-748,1000,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-456,512,1000,-391,541,76,339,340,-745,809,409,-458,171,117,573,-20,337,-14,-464,99,297,25,-996,676,665,-257,989,-341,1000,613,-623,726,329,529,49,259,-211,91,596,598,593,834,1000,413,-250,-221,675,1000,-468,-501,237,1000,1000,277,674,-228,250,392,610,-65,-406,-597,1000,-157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-1000,1000,310,-1000,911,491,-456,826,-1000,-763,913,-960,398,218,83,-723,179,-787,-1000,-570,-403,-161,1000,1000,1000,-1000,-331,-139,1000,442,-270,455,1000,1000,1000,-311,-1000,-572,-670,30,-630,-1000,1000,915,1000,354,937,-1000,-867,-364,-1000,-298,-56,416,868,-185,1000,1000,839,1000,1000,217,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-740,-1000,1000,41,-1000,1000,603,-45,413,-926,-217,-584,820,-398,701,31,-684,295,1000,759,-774,-606,-1000,-279,-48,347,-299,471,-155,-1000,305,535,-411,1000,751,398,-138,-777,885,865,1000,1000,1000,-264,-63,-248,825,1000,311,657,195,1000,86,959,1000,-349,1000,-14,502,565,744,-285,-962,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-959,-1000,-28,495,-1000,-821,101,-1000,796,-122,-191,-326,561,-662,-71,-98,-1000,-251,-400,839,-1000,-1000,-1000,-677,596,937,-830,1000,486,-1000,1000,1000,-805,-89,846,872,256,-1000,-111,-1000,677,-829,-9,-1000,-955,632,-718,731,1000,-83,178,-751,-686,777,1000,362,523,-576,-990,-759,-1000,14,490,-96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:MQ==:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTk4:19:java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-709,-81,-388,550,-82,811,-656,573,-555,-858,-1000,-217,314,620,771,-949,-345,-212,-53,952,-159,385,698,-463,-98,755,-785,-1000,806,379,-776,590,-630,69,-551,-257,777,-1000,-635,-716,-883,-29,-408,-752,-6,1000,-1000,-932,105,520,1000,-896,970,1000,314,409,-240,-1000,1000,1,-1000,-280,-170,502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-514,-365,-340,1000,-831,1000,-477,810,-198,-858,-232,-776,-500,354,328,-929,-131,306,-7,1000,-159,423,246,-620,-820,-467,-60,-1000,469,287,-1000,97,-1000,-188,-885,-1000,292,-387,-1000,-716,-129,-782,-131,-372,-56,1000,-658,-573,394,273,1000,-1000,1000,1000,124,1000,-1000,-1000,174,207,-1000,-625,320,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{471,-621,1000,933,509,1000,261,-63,1000,-433,-857,-774,352,452,-447,-915,169,268,-597,802,443,835,-988,-677,-527,891,1000,-1000,-478,-71,-120,-430,-1000,-672,-114,-820,-702,-415,-1000,32,1000,-29,218,1000,-510,950,562,525,-51,50,430,109,715,-88,90,550,-422,-193,-950,-737,-282,12,674,287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{74,-621,1000,1000,509,1000,202,108,1000,-390,-527,-948,-628,219,-467,-350,326,798,-639,286,1000,1000,-239,-682,-874,755,1000,-1000,1000,295,229,-1000,-1000,-526,335,-576,-90,80,16,1000,662,-29,213,297,-510,1000,-1000,632,1000,1000,1000,669,975,-357,1000,409,-240,-1000,-950,-800,374,12,160,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{459,-700,1000,1000,327,739,58,-221,236,1000,-747,-780,539,65,280,-1000,279,745,-203,364,1000,1000,-606,-454,-8,406,232,-1000,1000,234,309,-843,-1000,192,1000,-800,32,-776,278,1000,845,-82,1000,-51,-453,1000,-1000,82,1000,802,764,1000,60,382,1000,-21,-276,-280,-842,-186,254,1000,147,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{628,-384,-356,236,699,366,424,373,86,-168,776,515,-272,-372,-330,479,1000,-1000,-184,-70,-921,-863,128,227,-42,-567,-515,-341,-754,771,-444,-88,867,1000,-21,-328,-580,121,5,173,-1000,306,1000,-219,276,-98,182,814,865,194,-630,-858,84,-524,-681,666,369,-1000,-1000,-428,-167,-677,980,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{63,-149,-1000,-155,327,122,-367,1000,-1000,-221,347,534,-442,-502,970,1000,1000,-1000,-134,364,-1000,-794,1000,1000,-41,-1000,-1000,-1000,-809,549,-126,122,1000,830,1000,63,845,-80,17,-1000,-1000,-1000,-1000,-1000,1000,-105,-1000,-1000,1000,-1000,-552,-1000,95,590,-1000,1000,541,-929,-1000,58,-1000,-379,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-1000,48,510,573,-710,446,-253,-151,-173,-41,-1000,-1000,-1000,345,302,-1000,597,306,119,505,-556,1000,286,-891,-307,1000,-79,-802,634,-113,-998,552,463,-603,-996,-71,1000,-769,-303,-137,15,456,-923,-294,-477,1000,-433,-802,48,56,1000,301,241,666,1000,-166,-1000,-1000,1000,205,-183,548,-915,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{635,351,-837,1000,-713,1000,86,650,1000,-1000,-70,-1000,-1000,-21,-512,191,540,684,-1000,616,1000,1000,-167,-252,-1000,-350,1000,1000,1000,-152,697,-1000,-1000,-1000,121,253,-994,1000,-520,1000,1000,-53,155,811,164,1000,-909,824,836,1000,1000,437,1000,-787,490,1000,-139,-1000,-1000,-1000,-23,-643,670,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-414,207,858,-374,873,-425,744,-1000,122,1000,-923,115,1000,-386,-367,227,695,-1000,-4,-778,1000,691,184,-161,691,646,-542,685,-519,200,-440,561,-232,407,-179,1000,485,-1000,1000,1000,-794,728,-131,264,-325,1000,-93,487,371,-116,900,1000,-1000,-296,1000,-1000,-228,-868,1000,-430,1000,1000,-784,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{-720,-724,1000,991,-83,-872,889,-872,373,-1000,290,-1,100,126,-434,861,-316,4,-359,-177,876,1000,786,1000,-263,100,177,-61,-291,381,-398,-327,748,-445,-23,-1000,-571,710,1000,547,365,-1000,-563,-511,-1000,-87,-96,-74,-334,-72,801,326,-1000,-987,169,-723,831,-1000,195,106,806,1000,-119,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{-391,336,1000,144,400,-459,-1,-724,696,256,-205,-1000,763,-1000,585,-1000,-84,-271,-655,372,1000,318,-208,-1000,475,286,-372,639,-451,-578,424,939,-356,-1000,-360,-262,-1000,-1000,70,-244,365,-156,89,-932,1000,-766,252,-896,406,1000,-1000,895,-415,-281,717,457,264,-96,-1000,1000,-1000,-1000,408,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{-616,79,666,-480,1000,574,26,-354,-685,309,-37,-665,1000,354,-991,812,1000,777,-1000,821,122,-354,912,-96,-550,288,394,-235,-1000,-464,-274,515,328,-504,708,387,-210,-1000,-123,-875,663,618,-56,492,1000,-873,418,775,-1000,362,-86,764,843,-747,-1000,-484,-83,-130,-567,48,-1000,-778,423,-223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{750,-992,182,301,-413,-89,-186,164,230,-990,232,-716,3,-387,-211,599,-276,18,-198,-663,-203,-1000,182,731,-42,-873,102,531,362,-482,112,1000,427,742,-1000,-302,472,134,666,161,-509,-393,-768,-321,440,309,-639,-763,-249,46,453,1000,614,-416,192,-1000,204,-411,-17,105,-370,805,1000,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{-37,-637,1000,1000,-184,-486,597,-411,1000,-1000,-602,-378,-21,402,-400,422,-1000,1000,-377,-159,619,1000,661,1000,396,-76,-397,646,-393,781,1000,-243,1000,-719,249,617,-991,88,400,247,819,-298,-721,-510,-212,49,-599,-200,435,-684,343,109,166,-894,115,-1000,680,223,-325,749,191,1000,242,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{1000,-1000,1000,678,251,923,34,262,1000,-1000,-971,-1000,495,24,-1000,-542,-1000,50,769,-1000,-265,-1,1000,1000,70,-911,126,743,1000,-374,-562,-581,907,785,-164,227,489,-785,1000,-262,-202,-943,-651,-1000,218,1000,-1000,-1000,622,-966,-928,643,693,-508,550,-1000,1000,-361,382,887,-409,1000,476,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{1000,-813,952,325,-1000,698,1000,-421,1000,-1000,-953,-362,-845,857,1000,839,-1000,540,257,-1000,-660,-496,616,695,1000,-165,115,1000,12,-197,588,-1000,1000,969,-246,1000,902,326,-443,-827,-231,872,-1000,-776,-515,357,-495,-484,1000,-1000,-414,-915,664,-842,-90,-1000,-1000,905,-415,-103,1000,952,-59,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{-982,-689,928,1000,-1000,-750,1000,-1000,441,-909,374,-97,-708,245,1000,-542,-457,1000,-475,382,1000,-345,-92,-138,606,37,-368,648,-1000,523,379,-208,1000,-1000,-62,-445,-796,1000,-773,538,590,883,-838,-748,134,-885,506,312,180,1000,1000,-389,-521,-1000,52,337,-650,730,-540,105,1000,117,-400,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{-1000,-1000,1000,147,446,-1000,-56,-662,842,-15,-321,-956,684,-1000,-419,-1000,-17,-968,-966,176,1000,706,192,-425,354,-477,-130,482,-143,-158,84,1000,-520,-1000,-585,-1000,-1000,-400,416,-351,102,-872,-167,-999,600,383,132,-572,459,1000,-490,915,-762,-664,825,588,860,-534,-904,701,-547,-1000,418,134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{534,-684,101,-655,1000,-452,-607,-5,0,721,-321,-956,945,-1000,-655,-412,316,-387,-12,467,30,1000,-483,-1000,354,-758,-318,0,-730,-158,555,1000,395,-189,695,455,53,1000,-462,-351,204,-302,321,-397,1000,383,214,650,107,936,1000,942,-723,371,630,15,133,-105,-307,701,-404,-1000,752,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Long:LTQxOTQzMDQ=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{-450,-457,498,1000,-83,128,879,-1000,617,-621,-143,-992,78,383,-681,1000,-441,-1000,41,-641,574,1000,1000,1000,-297,561,165,-296,1000,-178,-1000,-1000,844,268,-909,-194,-161,618,1000,547,357,-1000,-648,-616,-1000,958,227,-81,438,-853,98,474,-1000,-987,394,-1000,369,-1000,303,-46,806,1000,1000,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-577,-605,-1000,-1000,-607,1000,737,-1000,-748,239,593,-406,766,1000,181,975,168,-1000,-127,188,1000,-1000,-228,550,742,-69,-1000,-917,528,-951,279,-1000,1000,1000,786,599,1000,-1000,85,86,378,-290,-336,1000,-1000,955,1000,710,-1000,-417,689,1000,-1000,-1000,509,-855,-512,-881,151,-225,318,-317,143,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{400,1000,343,-1000,-216,-496,-306,162,-28,-277,-646,818,923,-1000,64,589,1000,386,593,773,1000,822,649,1000,-256,-51,794,898,1000,-1000,-811,-973,-709,-1000,-1000,-956,225,1000,-1000,177,1000,202,-296,-264,197,-38,-352,37,791,444,-194,-76,1000,-875,79,-359,943,349,-768,996,435,470,-980,341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{356,175,-793,-666,385,400,-578,-703,284,952,620,204,445,1000,-391,-501,1000,-127,-233,705,400,-714,-951,-403,74,-1000,-530,-41,349,-340,972,-842,343,470,-1000,-924,340,-125,-383,-201,-1000,-963,-938,290,-650,-1000,1000,1000,-332,81,311,426,-378,-765,73,-776,-1000,-555,-359,-56,1000,588,667,-805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-577,-457,856,711,422,855,180,-581,902,-52,352,358,-725,959,755,-203,-568,-821,37,-256,-57,-829,467,550,742,-391,160,-947,-881,144,428,794,69,503,731,-91,-321,-247,172,-840,114,223,280,-695,970,-106,979,-521,307,-724,25,599,-503,-265,-13,-48,-827,44,-148,-959,-956,522,765,545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{109,-17,720,-1000,595,1000,1000,1000,666,904,-284,323,541,372,-619,697,466,-500,-790,-1000,416,-34,784,931,706,-706,-200,-899,-1000,303,-727,473,1000,-364,696,-912,-480,1000,-1000,-33,-273,31,-223,-742,-666,-800,-527,151,702,-235,355,-767,317,-530,379,650,-1000,443,-856,-14,308,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{1000,-413,-448,115,-412,686,-1000,-1000,318,566,474,-88,613,1000,417,651,418,-61,-306,-760,923,-1000,-69,-875,-48,-434,-1000,640,1000,-1000,-308,-819,956,279,-1000,212,-203,-853,673,277,-474,-87,58,955,-1000,741,1000,135,400,232,235,782,-402,19,91,-674,4,281,694,-537,1000,-741,439,-5}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-81,871,-116,-105,-216,233,237,-1000,629,-846,-113,1000,1000,-1000,884,650,-525,-1000,-1000,-718,-652,-1000,-830,43,693,729,529,-1000,-544,-1000,-401,1000,1000,1000,-1000,-3,1000,-37,-562,-431,910,-459,-743,-642,-1000,-708,-245,37,-1000,-1000,49,663,-800,-1000,171,-359,759,-579,-1000,-988,-1000,-245,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{1000,-837,-512,-393,-524,216,-1000,-1000,485,205,639,73,1000,1000,483,672,-76,-573,-114,-775,933,-1000,108,-1000,-20,-240,-1000,-248,1000,-818,-11,-951,1000,813,1000,-151,977,-710,568,1000,1000,624,561,957,-1000,301,1000,93,400,-99,809,803,-398,-17,-400,-448,-254,400,833,-1000,239,-541,-377,-174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:1:69:TYPE:org.apache.commons.compress.archivers.zip.UnrecognizedExtraField", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{241,768,179,-435,-1000,-786,48,-19,337,361,400,-30,1000,336,-157,-354,1000,-1000,87,-463,-385,-390,-32,838,1000,-904,-838,740,-1000,-221,1000,223,1000,334,-745,1000,-351,-1000,-5,1000,-138,-601,-655,-445,168,635,-711,683,-107,-298,636,-502,-858,19,-1000,-1000,339,-243,-235,-728,-553,-724,40,160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{1000,-601,-284,-1000,-1000,-1000,-1000,-575,-125,1000,439,652,693,401,-1000,-1000,133,304,1000,630,391,889,-17,1000,1000,-183,1000,964,-1000,-321,1000,619,1000,1000,-1000,1000,424,-604,-678,1000,-112,-1000,-1000,1000,105,1000,790,873,66,-484,967,-1000,-1000,812,-1000,-1000,-398,-1000,1000,-661,-197,-882,1000,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-1000,107,-664,518,503,1000,49,309,-1000,-560,1000,649,857,-840,1000,768,-675,-375,-611,1000,-9,-185,-752,-19,-1000,1000,-437,725,1000,-687,-209,55,987,360,1000,1000,1000,-40,559,560,140,-820,1000,-853,91,-1000,1000,-1000,-1000,-811,-302,154,1000,143,774,1000,-1000,600,-604,-796,1000,-1000,-31,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{193,-1000,-150,281,1000,824,-5,-297,372,734,77,-46,-1000,424,294,-260,-792,921,-250,226,-106,-751,511,-661,-634,228,1000,1000,-340,200,-47,46,-330,-1000,-201,-877,25,-124,291,729,913,-869,-438,92,176,-311,-17,-93,494,-394,-504,-1000,85,-51,-301,-49,37,-301,908,564,714,-160,-484,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-238,-296,-570,-34,-1000,-705,468,-375,296,-152,532,-86,1000,-447,-684,757,-378,-211,-1000,-669,136,323,-461,-523,1000,-927,-557,496,-93,1000,1000,-138,400,465,-1000,565,-351,155,-543,-414,-866,503,-411,-1000,228,-345,-410,476,83,-772,1000,552,-566,512,-1000,-806,379,-257,-1000,-1000,-1000,-1000,270,-562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-756,751,-405,554,-392,-723,578,-339,474,-695,10,-1000,993,-243,43,478,-1000,59,-611,-190,-903,-655,366,-359,653,-720,-712,-10,16,-687,1000,-564,561,-636,-508,-1000,-54,666,146,-438,390,366,317,-702,91,543,156,-657,-49,351,976,1000,-363,-228,774,492,-151,35,-788,-565,-962,-43,-1000,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:1:69:TYPE:org.apache.commons.compress.archivers.zip.UnrecognizedExtraField", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{460,-1000,-308,883,400,824,573,-914,976,772,-1000,-571,-1000,36,1000,-829,-792,821,-1000,-948,-558,-1000,723,-1000,-634,228,1000,686,428,1000,666,-350,-1000,-1000,-201,-1000,29,306,136,250,869,-389,-315,-856,354,-568,28,-139,1000,67,-1000,-1000,1000,-388,519,-652,384,460,757,1000,220,822,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{369,-1000,-1000,-153,-254,1000,-561,-886,-269,745,-341,1000,-1000,682,-381,-260,-1000,742,377,721,1000,-88,43,-157,-1000,228,1000,622,-307,223,-650,359,-752,405,1000,718,25,-1000,-117,729,913,-211,-121,1000,432,-989,678,1000,1000,-575,400,-1000,353,1000,-623,362,-200,352,1000,1000,961,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-400,563,519,-993,-625,-1000,-698,-240,596,-450,-921,31,-170,-1000,484,-126,318,-1000,-1000,779,1000,730,399,-22,1000,307,340,-1000,-1000,270,-1000,-1000,-156,1000,1000,104,-908,1000,-163,38,-414,1000,330,-174,308,1000,-172,961,-204,956,-1000,304,-1000,1000,-921,-8,-713,-678,-1000,-1000,-1000,-925,1000,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-1000,-1000,519,1000,-833,-671,-301,437,596,698,379,31,1000,1000,217,-903,-1000,-489,1000,-539,-907,551,1000,-763,-62,1000,-474,974,862,1000,1000,1000,1000,-334,-194,1000,723,638,775,294,-839,188,-793,-155,-961,-528,-693,-1000,94,-738,-311,576,1000,-290,1000,1000,-1000,211,1000,1000,1000,321,-255,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{1000,623,-1000,-826,1000,-1000,67,348,579,-737,1000,563,87,-1000,-82,-9,1000,-351,-501,-1000,959,54,-293,-381,179,-383,-895,-1000,-28,706,-1000,440,-1000,-64,-1000,1000,-665,445,250,486,-1000,396,-530,-1000,1000,902,1000,-134,10,969,-213,-692,-420,1000,-843,617,-1000,1000,381,-548,-274,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:1:72:TYPE:org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{531,588,-1000,-864,-778,-543,-246,529,1000,-1000,925,-1000,-947,-1000,1000,1000,70,630,-872,-1000,1000,725,134,1000,-365,-1000,911,-462,-778,-1000,-1000,-40,-1000,1000,-35,794,-224,-628,-1000,948,219,128,1000,293,1000,397,1000,194,-1000,562,14,-1000,-1000,5,612,986,-1000,63,344,-910,-1000,-658,-510,-754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-14,-1000,-1000,150,280,279,39,310,1000,230,116,-1000,-61,-1000,963,807,-248,-1000,-153,-1000,39,1000,508,1000,-1000,-400,-127,727,-778,-400,-274,1000,-203,-1000,-1000,1000,147,-764,-400,1000,800,-404,-168,753,79,-842,400,-139,-265,1000,14,-217,-27,-1000,1000,1000,-833,117,1000,729,-1000,169,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{400,1000,-345,699,-230,-527,151,1000,-139,572,379,-322,346,1000,728,-1000,-296,-332,465,-534,-859,700,1000,-300,-469,-353,-638,181,721,-237,671,233,247,-145,-350,494,15,-569,285,387,421,-82,203,647,1000,-407,-210,-605,-918,-826,-311,576,1000,-694,167,775,678,-124,-400,956,1000,-476,-443,948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:1:69:TYPE:org.apache.commons.compress.archivers.zip.UnrecognizedExtraField", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{723,-1000,-154,-30,1000,-943,999,-823,-1000,480,808,1000,1000,261,-727,-366,1000,-720,374,-881,-213,693,-21,-1000,-358,-349,-1000,164,237,1000,-987,546,-655,-770,-1000,352,-252,408,886,-474,-525,-41,-981,-280,183,603,-778,574,1000,516,-631,1000,1000,-408,365,131,-73,259,1000,1000,1000,-24,228,745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{623,-317,-1000,-802,1000,-461,1000,-347,579,-705,650,-321,87,-842,1000,772,1000,382,-1000,-938,1000,1000,1000,514,-1000,-1000,344,335,-1000,-14,-937,440,-1000,-254,-1000,-131,-531,-1000,-1000,212,898,-602,712,588,979,433,1000,1000,334,486,143,959,-541,-316,446,272,-94,-13,1000,-548,-399,-183,-1000,475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{976,-1000,1000,-39,-408,-227,233,913,-1000,746,245,-76,-474,-174,-463,-240,214,-746,90,-232,-358,564,523,-60,-124,400,354,48,258,1000,27,343,400,37,-259,154,-637,218,400,-667,-188,-251,-1000,635,-25,-154,-400,-436,-1000,307,746,571,79,-945,365,678,-32,-855,-796,220,-21,-572,-162,336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-1000,-812,-1000,-864,322,218,-86,336,151,-1000,1000,-1000,-956,-1000,1000,1000,39,169,-1000,-1000,1000,998,852,1000,-591,-1000,1000,1000,-1000,-1000,-1000,1000,-1000,1000,-198,668,344,-628,-1000,591,631,119,941,767,1000,418,-763,-535,-950,-62,58,-425,-1000,-945,1000,505,-1000,-695,511,-1000,-1000,-401,-510,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{-631,1000,-402,-234,-96,-1000,-426,314,-286,579,283,-343,626,238,235,-345,-696,-400,-452,-585,1000,-133,-1000,214,317,-628,-149,544,-722,208,-61,119,1000,429,-616,-600,4,526,561,39,821,223,1000,-457,-1000,-280,475,-111,-1000,654,-526,-659,-79,-8,400,1000,-648,754,901,8,791,-759,57,142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{1000,-1000,949,-808,-1000,133,-7,567,413,613,868,1000,692,-395,1000,-1000,-483,1000,270,1000,737,1000,1000,730,-1000,-1000,-964,-1000,-828,51,-433,-1000,-227,435,761,1000,-325,432,836,854,287,-1000,-405,-944,-520,610,435,-41,-668,1000,885,-1000,105,-1000,-1000,-1000,1000,-1000,1000,1000,-716,-225,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{-1000,575,-412,1000,429,-205,-1000,-561,-508,1000,495,-101,563,-22,-374,594,-1000,-1000,-452,-1000,1000,-1000,-1000,-1000,193,-1000,1000,-856,-926,-738,-224,119,1000,-322,759,-174,797,115,1000,898,1000,558,284,198,-1000,-417,-753,-942,-1000,-963,229,283,-1000,691,854,722,-891,1000,1000,317,-980,908,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{316,-1000,630,506,1000,-470,64,695,-545,579,-1000,284,-371,-291,-421,130,-325,1000,-452,1000,-216,529,-427,-255,-199,-731,-120,-705,92,-609,-460,-379,-405,-629,-312,696,89,690,-925,39,-875,-757,-1000,503,118,351,63,-440,-27,-440,759,190,-628,-8,-1000,1000,-648,-1000,536,146,-363,107,315,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{-1000,1000,-348,811,554,-43,337,121,-1000,317,-1000,-1000,522,336,-506,1000,-358,-1000,-269,-1000,-623,-1000,-320,-706,-162,444,1000,723,-656,-1000,-919,1000,1000,-1000,382,-678,449,-358,44,897,439,624,-1000,414,-1000,686,-693,71,-1000,-709,-14,852,-782,370,854,1000,542,1000,-884,-813,5,-4,597,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{1000,-1000,935,457,-1000,369,829,1000,-87,1000,-424,1000,766,-99,1000,-822,-454,1000,-721,1000,737,1000,873,670,-1000,-1000,-964,-1000,-908,-327,-770,-983,884,-297,1000,1000,-845,-10,397,1000,287,-1000,-563,-1000,-914,610,627,768,-1000,1000,1000,-577,-127,-1000,-1000,-1000,-400,-860,1000,275,-716,255,1000,907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{1000,-1000,740,-1000,-400,159,101,103,1000,-157,-294,1000,-605,-767,1000,-1000,436,1000,-211,1000,-254,1000,1000,1000,-1000,-628,-132,-1000,231,328,-43,-1000,-870,919,-261,1000,-185,671,-439,1000,-1000,-1000,-1000,-758,831,1000,-171,-239,1000,813,1000,-480,641,-1000,-1000,1000,1000,-1000,1000,667,-1000,-962,745,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{340,-336,-511,-529,253,163,1000,-377,-786,-408,-491,655,-596,108,-74,-869,-418,684,99,-199,1000,302,-835,862,34,-260,-778,1000,203,124,-351,-1000,1000,504,200,853,824,-137,672,558,-241,-761,-712,922,-169,696,-254,-32,868,339,281,-43,-342,-478,-718,253,211,-601,-710,719,370,-755,-659,430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{695,-1000,724,288,17,-678,172,666,-823,241,-983,-9,404,730,450,-193,-583,1000,-1000,557,192,1000,459,-289,-198,-320,-250,-411,-750,-609,-854,-407,-577,-435,690,611,-39,261,-27,989,-587,-437,-640,-125,-112,905,-1000,-68,-712,1000,775,-553,-440,-804,-847,-241,222,-1000,95,1000,120,183,1000,142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{-258,480,1000,-1000,917,-46,734,760,1000,855,-1000,-801,-51,-95,342,-1000,337,25,1000,1000,115,-1000,1000,1000,948,-401,1000,1000,1000,188,521,1000,1000,1000,-636,452,1000,-198,671,651,-49,-383,1000,-47,42,1000,-796,351,-269,-141,-1000,355,1000,-683,950,622,-1000,-1000,-563,1000,-708,384,348,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{510,311,-76,-1000,300,150,-100,675,75,-889,793,-1,253,646,1000,-880,470,127,356,625,453,1000,511,157,677,-870,630,403,-37,-1000,318,694,841,-227,299,-685,356,-19,491,948,27,847,420,599,-835,214,-691,786,-4,245,678,1000,971,-1000,175,669,-13,-411,336,436,578,900,158,-657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{-27,1000,1000,-1000,829,-99,384,976,683,-545,-613,-598,-217,319,1000,-1000,148,-221,1000,517,-153,-25,1000,1000,793,-935,563,1000,875,-1000,647,948,1000,1000,-962,-552,1000,709,1000,1000,891,1000,449,1000,-719,1000,-1000,-288,-1000,-387,1000,789,1000,-1000,459,946,-1000,-685,746,1000,-624,572,104,-792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{552,-832,249,994,-629,716,559,-459,-353,943,-434,501,132,510,-519,252,-348,-634,-685,-843,-982,-155,930,380,-580,173,-495,-558,151,-668,-555,-164,977,-845,694,554,279,197,571,724,-767,446,837,747,696,259,492,262,-771,-949,-31,-892,-260,-810,-146,5,598,343,-961,-873,757,-947,-703,966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{-335,352,17,-1000,182,374,403,460,379,278,9,-238,-379,-140,1000,-1000,161,-194,375,1000,537,1000,1000,1000,743,-1000,1000,970,248,-1000,318,458,1000,-431,299,33,1000,-1000,131,1000,-259,253,1000,1000,-805,814,-630,1000,-13,373,678,966,955,-1000,1000,669,-116,-1000,1000,1000,1000,1000,299,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{-488,496,852,187,5,84,563,408,917,352,-777,-1000,-237,829,-224,-874,1,806,-231,90,-87,497,422,720,696,225,449,241,701,-1000,-273,-214,347,794,-260,99,731,526,-586,216,104,-105,409,335,-66,718,-935,559,-1000,-688,-701,-489,-589,-21,131,-1000,-241,58,356,298,-674,319,-1000,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{365,-832,-890,375,-629,582,4,107,-456,119,-1000,501,-789,510,-594,-616,-14,-221,-685,-789,-977,1000,986,1000,-569,5,-34,-713,-901,-668,502,1000,1000,-290,-555,226,14,-553,887,1000,653,653,1000,427,279,143,-196,-14,-194,-167,959,-314,1000,-1000,419,987,173,343,652,1000,627,235,236,213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{46,416,-78,1000,276,-212,599,-18,212,105,279,-253,-1000,895,649,-88,-646,1000,-1000,-406,757,-1000,-1000,-470,1000,347,-824,-598,-423,188,-688,-1000,-1000,218,164,-30,-1000,1000,-1000,-113,-390,326,106,-47,42,-767,-160,-539,-737,371,-1000,-680,914,-734,-1000,-1000,-306,846,-563,-164,-453,-1000,-1000,625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{1000,-637,-1000,613,196,-81,134,760,-489,366,486,-801,-442,235,860,499,-650,-314,1000,-75,577,-309,-30,-216,447,-960,-425,-305,-927,1000,-193,481,1000,-481,-437,452,-182,1000,146,369,131,1000,-153,-45,472,-580,-796,-157,-615,-141,-193,437,1000,-1000,329,722,-327,553,-489,-272,325,-800,348,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{1000,403,-788,-294,214,-1000,673,160,529,704,760,582,-379,565,990,765,-229,76,671,278,-1000,992,-804,-223,1000,-1000,1000,1000,562,156,-189,-707,284,290,652,-759,934,764,780,-1000,-209,1000,281,-621,1000,63,-805,412,1000,-787,33,-845,-620,-1000,495,337,-1000,-872,956,-206,1000,256,759,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{612,-1000,-472,395,1000,-1000,-701,535,-379,1000,341,-47,-1000,-929,767,1000,777,1000,391,562,-1000,272,-1000,-886,967,-1000,661,707,-1000,953,-598,-659,-481,161,462,43,1000,444,1000,-1000,-766,-1000,80,176,1000,-1000,3,-1000,-393,-998,181,-714,-180,-777,-62,-970,688,-754,150,1000,1000,-19,783,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{596,-412,642,-132,-785,-26,-499,940,557,-505,522,672,-446,-851,-178,-942,-446,953,180,136,-166,776,943,398,-768,929,348,-462,342,-105,-325,881,262,-350,-507,426,367,-360,-985,-387,-757,680,539,-385,-434,-215,-155,271,-558,277,485,599,-452,347,958,247,-753,-255,903,204,-207,225,-335,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{285,-1000,-85,1000,942,-1000,-57,494,329,241,392,512,-293,-911,284,941,943,1000,-672,-18,-404,462,-234,-216,-159,-1000,968,322,-1000,804,-77,-42,-152,326,-185,-969,1000,1000,13,-1000,-800,-1000,400,223,1000,789,134,-339,-165,29,558,-425,-3,-581,-72,173,292,-372,149,542,383,493,600,518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{649,1000,965,-961,463,24,603,160,908,1000,105,401,-84,-445,83,146,1000,434,-1000,-278,370,869,-120,-600,455,-680,-145,473,-439,693,349,-422,-119,-335,-1000,127,616,98,-156,-290,-252,-728,193,-213,326,-238,149,684,-359,666,-195,-1000,43,353,-512,267,1000,-679,636,106,754,339,593,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{287,-1000,-132,1000,233,-182,-122,342,321,549,558,-380,-293,-1000,636,176,817,1000,352,1000,-977,742,-1000,-314,1000,-1000,1000,612,-992,1000,-1000,-590,-1000,-48,1000,-294,1000,403,337,-1000,-1000,-908,-435,792,1000,1000,-602,-587,-451,-462,191,-158,-539,-1000,-579,-124,987,-1000,510,687,709,-387,1000,802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{381,-1000,-94,208,-1000,-398,-229,494,329,-1000,507,255,405,-155,125,-694,880,536,-398,571,421,808,407,6,160,-400,20,178,59,206,1000,-251,721,498,-185,-665,1000,1000,-1000,-798,-162,-72,890,223,-310,18,-652,129,-165,-631,127,-804,-1000,97,366,648,-102,-95,278,-438,383,363,1000,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{126,159,184,271,817,289,1000,-431,-187,-148,-36,1000,956,856,-711,-185,916,7,614,-1000,-289,-89,619,-960,-72,728,-689,474,-21,438,542,69,28,467,-803,112,-114,799,628,-888,1000,-1000,508,-1000,70,-158,192,852,1000,1000,141,-252,291,485,-925,395,-921,715,-1000,246,426,33,610,-353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{555,-57,712,-735,-1000,284,510,760,589,-540,651,561,-354,-972,248,985,576,868,835,257,-202,1000,-777,667,105,400,-406,-18,59,-160,-1000,548,535,-153,-114,400,344,-374,-1000,-371,-160,1000,115,-474,-390,-643,-643,9,-178,45,-248,694,-1000,-1000,669,-164,386,-688,622,-286,-243,235,301,581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{-171,107,-1000,-981,742,104,625,-180,-1000,726,196,-387,550,-215,138,370,917,-841,-1000,248,-953,-572,636,-764,579,894,-695,118,1000,-150,-338,604,416,-622,-643,279,-465,322,-257,-67,157,1000,-1000,-661,-419,-691,151,1000,50,-462,232,1000,-17,613,1000,686,773,-928,270,136,-665,712,-1000,686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTQy:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{-462,1000,279,-943,-455,644,-523,389,216,281,651,-389,311,-425,766,780,105,1000,-859,-759,-36,-653,703,737,29,81,-661,98,-1000,883,-136,268,540,-204,-781,238,246,864,-574,170,-168,-552,-503,627,-96,341,-370,1000,-927,-519,-109,-176,-206,186,119,1000,-721,481,46,457,547,166,-637,-145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTgw:19:java.lang.Byte:LTE=:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{-219,1000,758,-1000,1000,312,-726,-283,-543,1000,58,-1000,627,-775,577,25,1000,176,-697,248,-717,-943,534,539,1000,1000,-1000,1000,-982,475,-302,860,312,816,-417,-44,-1000,774,-469,-741,-357,529,-494,-1000,-1000,-956,1000,1000,-582,-1000,1000,1000,786,1000,1000,1000,1000,-1000,1000,29,1000,1000,-640,-714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{-1000,-1000,1000,598,225,-502,-66,159,-149,-396,338,-78,-1000,752,577,457,374,289,-601,655,451,370,1000,119,-707,66,79,-1000,-400,88,541,1000,576,-1000,-274,-688,-519,-12,-279,-1000,502,-86,53,514,1000,222,-858,293,106,497,-856,21,-561,-347,383,1000,-167,92,-1000,58,-64,26,-117,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{576,-1000,-521,78,-323,-561,862,1000,-691,-681,651,84,49,1000,368,942,310,-673,-790,-759,316,250,-84,-1000,29,674,-661,-210,764,635,1000,-173,643,-341,-840,559,507,-101,1000,592,1000,917,-896,746,-968,-569,-370,894,1000,-44,466,952,-295,-732,711,194,44,-624,187,527,-268,166,899,856}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{-1000,103,-36,945,1000,908,508,25,-543,817,504,-572,-1000,826,668,176,622,-562,102,922,1000,581,1000,-196,-879,275,-1000,-562,400,1000,-385,339,-265,-250,-551,-1000,165,585,-418,-1000,242,1000,144,423,-782,121,-383,788,-140,535,-815,605,303,-40,204,329,-123,-477,-136,27,65,785,35,-186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{-925,51,994,660,-447,1000,391,525,920,-93,-1000,-841,-201,-210,283,1000,-148,553,-178,1000,1000,1000,706,-1000,-17,125,-513,-1000,-1000,-1000,-319,-403,-773,-252,-1000,-446,46,-544,-59,-855,1000,-1000,-355,-424,457,-618,-596,-723,608,542,-1000,904,22,966,337,-477,-1000,-538,-1000,337,-89,848,615,-802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTE=:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{-182,724,-106,378,372,-357,-657,391,484,77,923,420,-975,892,-542,-734,845,-202,792,-474,233,-821,-631,854,-654,-614,64,-525,-146,346,-757,-693,-163,-209,-301,-402,38,619,-864,-590,-1,932,135,439,-689,178,-789,695,225,-537,-566,382,-781,-415,-591,-51,773,641,889,728,549,163,227,853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTg2:19:java.lang.Byte:MzA=:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{-352,-1000,758,-1000,-965,312,-726,71,422,-396,676,866,445,-598,577,1000,-425,1000,-1000,-1000,598,-943,682,191,304,526,-570,64,-982,475,541,-55,125,-533,-1000,215,900,558,-77,356,362,-635,-795,740,122,656,64,968,-582,-571,-856,21,-341,682,358,1000,-1000,92,-431,90,1000,465,398,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-823,-1000,-332,-280,-571,944,-37,-1000,-877,-134,-702,-722,980,1000,193,-1000,-1000,1000,-626,1000,703,394,202,-717,595,-658,-1000,594,1000,1000,-1000,1000,1,341,-135,-1000,665,-695,115,985,21,-483,554,-742,635,-1000,-481,499,-287,-1000,240,-635,617,-1000,797,-255,1000,670,646,285,-742,-900,-374,900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-599,1000,693,-71,-307,-1000,-432,-676,147,-521,-586,1000,-1000,331,986,804,68,626,953,331,773,1000,129,-677,865,1000,-17,641,290,1000,-852,-907,-89,-688,914,1000,-887,-489,-1000,-33,27,1000,573,704,1000,431,614,-4,674,939,-505,551,-765,459,-607,-334,861,-405,622,819,-938,-1000,720,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{210,-672,247,745,-687,149,-66,-462,-280,-669,63,-851,587,510,675,-829,-799,1000,-337,90,-289,641,-49,-341,144,-445,-1000,-66,553,362,65,785,242,-45,-339,-169,330,-235,-682,-186,-877,1000,-554,-56,-51,-614,167,237,373,-412,-604,-408,323,-14,-166,-189,993,584,882,504,34,-919,-230,-88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{1000,695,1000,45,-1000,-1000,208,-656,-280,-979,-802,373,587,79,1000,691,-703,1000,716,778,-1000,905,-1000,-442,435,-1000,-1000,110,-775,242,1000,-1000,1000,-1000,522,1000,971,-57,-385,-186,156,1000,-1000,-56,-521,-1000,214,1000,979,-785,-1000,-82,785,278,-750,1000,470,-380,766,879,-1000,-513,-745,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-391,-1000,-1000,1000,54,930,-66,-689,299,-669,-773,1000,587,519,675,-1000,-640,104,-539,319,-289,-241,802,-341,-636,459,-635,305,553,848,1000,1000,-629,36,-886,-711,353,483,1000,559,-282,-703,-733,1000,97,-966,1000,908,-679,-250,826,277,18,-1000,-551,46,-1000,-851,-724,1000,495,430,-230,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{62,-400,56,237,-1000,67,-756,-38,335,-1000,-516,-160,260,122,378,-407,1000,832,-341,583,-582,1000,1000,-1000,-29,72,1000,192,108,-295,1000,365,-410,-283,-901,490,969,785,-730,646,-880,1000,-1000,515,-314,-632,1000,735,-307,1000,-861,393,313,1000,-816,-547,-73,-379,978,-926,-300,-1000,-459,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-613,-1000,-483,-1000,738,815,803,35,1000,880,-649,-622,661,-1000,-26,-103,273,1000,-785,-1000,405,-66,-801,-361,-406,697,759,313,-690,882,1000,-546,-629,1000,628,168,506,-703,1000,211,-391,-1000,-496,230,697,172,-472,189,-1000,188,318,439,1000,-504,-993,91,-1000,-1000,-692,250,459,652,-896,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{444,-1000,17,630,-1000,-727,262,801,-175,-151,-754,-1000,-597,313,531,-1000,-1000,962,-463,430,210,107,893,-1000,-1000,884,-1000,-306,1000,832,62,791,-170,-1000,-796,-718,-649,168,-324,130,-829,1000,-16,-687,110,-955,-1000,724,62,419,144,287,251,48,845,-534,1000,1000,1000,-187,955,-1000,574,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-568,400,626,677,-992,-136,-862,-825,-893,-579,-910,1000,-488,1000,517,-120,-771,1000,954,1000,197,764,472,-713,815,429,-1000,904,680,1000,-896,143,744,-1000,-86,-9,-267,-699,-750,1000,380,921,-82,682,1000,-496,49,973,794,113,-6,25,-476,-316,-349,-59,395,-330,519,1000,-1000,-887,400,999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-344,1000,626,248,-81,-812,-408,-1000,-1000,-962,-1000,42,-1000,19,1000,1000,403,525,972,851,135,1000,-1000,-21,1000,1000,-59,925,-377,627,542,-1000,1000,-730,1000,364,309,-1000,-186,383,-291,1000,-677,1000,-106,594,45,895,118,-345,-339,468,-516,-324,-273,1000,100,-810,-164,741,-672,-399,23,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAwMDA=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{299,-665,-934,222,841,347,827,805,23,-433,540,-1000,-129,-1000,148,-51,949,-761,-521,46,95,543,68,-797,-1000,-1000,619,-7,1000,-1000,-32,273,-405,-1000,-48,-150,263,607,850,-452,68,82,206,-690,712,694,469,663,622,502,721,-246,919,-746,239,-258,-641,133,91,-493,-591,-677,906,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4ODAwMDAwMDAwMDAwMDAwMDA=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{-839,968,-1000,-526,-235,136,623,247,18,906,-946,216,571,-836,509,1000,-1000,-1000,930,738,75,578,-66,-962,-41,-961,161,160,726,693,91,1000,-1000,-562,387,1000,-742,68,-232,840,1000,-913,553,38,-738,-311,1000,191,101,295,583,-288,-202,834,107,-846,-643,655,394,1000,135,41,205,332}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{-476,591,239,1000,-350,1000,785,1000,1000,-822,-176,-133,1000,-111,1000,-686,758,281,536,-1000,597,1000,1000,-817,-1000,1000,-1000,1000,206,-1000,1000,705,1000,-1000,-419,-531,321,1000,1000,588,-207,1000,-98,-306,-1000,-282,-1000,321,-234,1000,602,-54,894,-1000,-1000,1000,57,-1000,-1,-170,1000,1000,265,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.String:TmFO", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{922,39,99,602,23,961,-975,-1000,-253,-581,-310,-519,238,-226,148,806,725,-340,-656,-188,808,-88,589,272,328,-1000,461,1000,608,-930,-129,723,396,-1000,-820,-220,-302,114,-364,-441,89,-461,1000,-667,1000,-381,1000,255,582,803,323,-112,-627,-422,-385,-370,-87,435,-674,-299,-899,-965,-693,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.String:NDA4LjA=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{1000,-449,304,95,551,723,-153,539,-1000,-170,53,-679,1000,-990,-1000,-485,-276,-105,294,-784,-67,1000,149,1000,-58,-724,312,474,888,-347,-408,-212,-1000,34,-900,-455,-497,1000,839,-302,406,497,524,306,23,891,731,-992,1000,160,-43,-168,970,110,700,400,191,-796,-183,-237,-352,-1000,-850,-366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4ODAwMDAwMDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{657,-285,-842,426,-1000,384,-125,-636,262,341,-706,449,-1000,473,-839,138,-456,-718,5,-250,252,-17,-775,505,667,34,547,-1000,1000,1000,-443,220,-400,1000,493,-32,-1000,206,137,1000,-483,53,-1000,-457,-950,1000,-373,775,479,744,770,-1000,-842,485,601,995,278,-663,1000,491,24,-1000,-437,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{378,39,-778,-214,-841,665,180,-61,-200,-275,-702,363,192,288,561,592,504,-544,-1000,-606,1000,-388,-540,744,509,111,716,-574,-43,902,-19,744,325,-389,-458,-135,-832,289,-320,-808,-904,53,-503,-466,-1000,897,720,696,101,942,323,-670,-584,1,658,499,158,-90,-129,-218,-316,-1000,-18,-688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4Lw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{1000,547,55,-911,-1000,-527,-671,-1000,657,1000,-998,1000,-1000,1000,-1000,693,-1000,-1000,1000,1000,1000,-250,623,230,1000,-534,279,-1000,16,989,-1000,1000,-1000,1000,1000,1000,-671,-417,-1000,735,905,-685,-694,1000,-697,-877,-683,68,-384,172,638,-966,-1000,1000,-152,1000,238,-245,1000,1000,1000,-108,-1000,-121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.String:KzB4ODAwMA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{889,668,-1000,-965,-766,343,623,-332,110,1000,-1000,968,-613,-130,-923,685,-683,-846,391,162,-84,737,-889,102,57,-1000,220,-1000,145,1000,-1000,553,-1000,1000,1000,798,-1000,551,1000,235,-113,-878,-1000,-604,-1000,1000,720,306,347,741,1000,-1000,-157,985,636,751,-19,-938,1000,513,723,-296,108,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-252,-1000,412,-1000,-962,-577,-392,151,446,1000,-8,1000,-1000,-998,59,1000,-291,-390,-163,342,-1000,-604,815,-817,-394,209,529,568,-588,-731,293,-230,635,1000,-533,47,-715,1000,-1000,-662,18,-279,-91,-795,-235,678,467,378,21,-899,846,-85,202,-429,-1000,-1000,1000,-1000,-342,456,98,-53,573,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-562,875,-196,463,840,-1000,-767,-387,-17,75,602,-1000,400,400,289,-730,-744,-967,613,-197,863,253,-785,1000,661,95,284,-714,340,-185,-159,361,-604,-406,789,-358,-481,-1000,-500,-535,-311,1000,-354,426,350,-92,-91,-357,-634,710,727,801,-634,509,145,1000,13,41,158,201,304,-413,316,652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-212,1000,786,-837,-850,74,-535,-916,830,1000,-983,1000,-761,-1000,139,1000,1000,1000,-492,1000,-979,-1000,-75,-1000,-904,1000,-99,1000,-1000,464,897,70,-979,764,155,448,-1000,404,-1000,1000,680,-145,1000,-977,-561,-482,-339,-713,-389,-954,-21,-257,-230,625,-1000,-1000,-1000,-1000,1000,553,-747,1000,585,-536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{403,1000,-1000,948,-1000,-850,384,394,-1000,117,-626,-1000,-1000,702,-406,-263,1000,-1000,1000,504,1000,-723,-89,1000,-1000,754,381,260,-69,-482,688,216,-1000,-940,945,-244,-1000,940,-1000,1000,1000,915,-1000,1,556,-320,-1000,493,456,834,794,1000,-1000,1000,-358,1000,-1000,1000,-1000,-876,-982,236,-243,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-480,944,546,-675,216,-651,-522,-613,617,597,-30,-548,-569,-1000,-351,-323,-129,-84,478,129,-319,-1000,-941,781,789,649,580,-278,-709,-82,1000,944,-543,414,705,-41,-45,1000,-40,-1000,310,939,1000,33,-223,-374,27,-620,-1000,519,509,570,-1000,1000,-906,-934,795,-665,785,542,1,-998,1000,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-589,455,-466,-1000,1000,-71,-438,567,74,-1000,1000,942,773,1000,-301,965,-1000,1000,-121,1000,-1000,1000,1000,-1000,580,-1000,-944,765,-1000,-904,-1000,475,1000,1000,-1000,-483,-1000,-1000,770,-756,-26,-1000,505,739,1000,1000,1000,300,240,-395,630,193,683,-909,-249,-1000,-287,-466,-24,423,1000,944,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-59,1000,-1000,-638,815,150,-836,-445,-142,209,463,-731,1000,1000,-608,-1000,-681,166,278,-1000,57,1000,-322,136,-423,121,497,-926,914,-539,234,73,-7,-1000,301,96,-597,591,-647,-298,-1000,1000,-128,451,55,-236,153,-121,-478,1000,220,243,-318,540,695,-384,-664,206,-576,0,145,-162,-595,656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{174,1000,-1000,150,-342,150,73,445,-346,-1000,212,-1000,131,20,-1000,-1000,22,67,439,-1000,649,-127,-322,1000,-112,121,998,-199,914,-377,251,574,-455,-599,168,16,678,1000,-276,66,187,141,-555,-226,105,-178,-617,-425,-413,1000,296,628,-1000,1000,318,1000,-1000,206,-621,-29,422,-619,-274,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-1000,956,-1000,-360,1000,-500,-483,445,328,-146,1000,-965,1000,1000,-380,-1000,-807,226,410,-1000,-49,1000,-281,527,977,-816,197,-691,914,-776,-863,256,600,-599,-484,-414,-381,194,-210,-1000,-190,1000,-1000,1000,1000,-178,221,166,-478,588,411,115,-147,422,729,123,-685,865,-621,391,1000,-1000,-596,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{1000,-125,608,82,-468,-590,662,-1000,1000,1000,963,508,-68,469,288,49,-892,1000,232,-570,974,-374,952,-1000,1000,1000,657,280,1000,-1000,-1000,-673,-214,-430,1000,771,-127,40,-593,-1000,846,-217,-454,-556,-1000,672,507,1000,-1000,1000,466,-1000,-1000,-1000,-306,-1000,-30,-1000,836,-522,-1000,27,91,202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-858,-516,-145,1000,-914,-1000,439,-400,-1000,-462,461,729,580,400,130,-397,-586,-819,650,523,1000,-737,908,289,1000,400,1000,469,-104,-414,-387,-272,1000,1000,851,1000,-330,-1000,1000,1000,-744,938,-174,1000,1000,-1000,-1000,-1000,1000,-115,628,-416,881,-984,-1000,596,460,1000,-1000,-636,1000,-240,172,584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-731,-412,-51,360,-305,-750,-11,-9,-863,598,-237,471,651,992,-586,613,-122,680,969,958,587,368,234,931,290,-32,353,710,-44,-481,-195,-263,50,965,455,420,-861,-461,973,-496,-891,-217,-621,837,700,-53,-216,-196,484,-581,496,-416,-301,-854,-426,558,402,592,-359,690,-705,652,308,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-404,-1000,1,-1000,-1000,604,423,905,-769,-334,241,-145,761,-608,160,-437,1000,21,-982,-153,432,-529,1000,-529,-144,-1000,-9,-860,-1000,497,987,-229,516,-177,1000,-724,-19,578,334,587,-1000,791,78,1000,596,-197,-812,-1000,924,-1000,-938,752,1000,728,641,708,364,-846,1000,848,-36,-829,-269,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{407,196,261,682,-535,209,4,-143,1000,-282,-99,-629,-440,-268,-1000,1000,938,-231,284,-704,-93,241,-579,149,1000,-418,-904,847,270,1000,-104,-173,-661,205,-590,47,919,1000,-534,530,-43,-1000,-212,-1000,-717,438,441,-292,360,109,184,44,760,211,-309,-208,49,-125,-131,573,1000,-631,-177,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-258,124,488,741,-828,488,358,293,367,892,100,127,525,-288,-527,637,-615,-175,-817,52,535,-246,589,372,693,-100,-544,798,-310,338,721,-461,-896,-684,-849,529,512,671,-626,482,-889,-311,300,-8,-856,580,-798,-77,-24,721,-152,-800,-65,-916,985,305,-878,859,206,275,251,-833,-225,-205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-1000,-837,-337,667,157,-446,-521,383,-817,-954,608,-551,1000,-952,-121,-255,-694,-1000,-374,552,1000,-563,956,-424,521,-20,418,1000,-830,264,-4,-962,1000,1000,711,550,-358,-1000,1000,1000,-92,1000,-711,1000,1000,-1000,-1000,-881,1000,-99,382,1000,1000,-444,-1000,1000,614,-549,-1000,-815,916,-1000,810,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-177,539,-408,-559,-868,-19,-275,-339,53,694,318,368,-462,337,38,-612,938,-4,-86,-176,-975,1000,-579,-95,12,673,-860,-1000,-319,-941,199,-173,-784,-308,-967,-233,276,960,541,-254,-323,-1000,-126,548,106,394,726,189,-54,-840,-626,528,-745,770,933,-208,-1000,-125,-131,355,-529,1000,-358,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-665,327,-214,1000,-329,-1000,-424,1000,-1000,-30,-1000,472,363,-337,-121,-608,-470,-710,634,199,1000,-625,566,944,1000,1000,1000,241,-660,-1000,-942,188,976,1000,53,1000,-56,-1000,1000,-20,-780,330,388,1000,509,-1000,-1000,-710,1000,-808,1000,1000,829,-174,-890,173,228,911,-1000,-705,809,86,252,514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-165,-457,-94,-858,-1000,-750,686,-400,-253,400,461,-568,260,400,160,-437,-122,441,-1000,-337,44,-280,952,-1000,-629,181,-511,-860,-44,-290,213,-406,30,-194,1000,-362,143,1000,-39,-496,-776,349,-174,78,93,486,400,530,-400,-115,-772,-416,197,1000,896,-191,364,-1000,-359,1000,-692,-486,-269,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-527,-785,170,-232,-329,460,1000,905,-1000,-223,-290,425,854,-251,497,-862,1000,599,-766,-172,1000,-374,1000,-1000,715,-1000,332,241,-1000,53,1000,188,-9,-436,53,1000,450,1000,-365,660,-775,951,722,78,509,-477,-706,-486,45,-482,-1000,-443,1000,-174,1000,201,364,-1000,1000,878,-343,-1000,-1000,683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{750,-789,839,1000,-189,-176,-154,1000,-1000,-1000,-839,1000,-269,-1000,1000,309,587,-555,-70,913,-1000,-1000,-100,-279,-383,1000,626,-1000,-1000,1000,-996,135,1000,-779,-1000,783,-1000,-602,1000,1000,-87,-189,95,-1000,-1000,-668,-888,624,432,1000,-957,568,1000,-179,-476,-1000,-794,-226,-1000,-1000,1000,535,-597,-609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{1000,-1000,-1000,1000,197,-1000,-531,-437,-357,649,-269,945,-1000,-894,1000,-641,1000,251,-1000,-1000,-1000,1000,1000,1000,-492,627,1000,748,-489,-1000,409,1000,865,303,440,1000,-1000,1000,1000,1000,-580,-1000,251,-1000,-570,400,-1000,1000,245,497,-761,364,1000,-249,-1000,-1000,553,1000,-531,1000,1000,-1000,-1000,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{-1000,-1000,111,-275,368,-1000,-656,394,-400,281,-900,1000,-1000,-355,-815,-194,744,49,-1000,912,-1000,-400,352,-1000,260,188,41,-97,-243,-1000,409,1000,-751,-314,931,-1000,-745,606,1000,533,-317,112,-893,-1000,-1000,-156,-1000,1000,703,568,-1000,660,1000,1000,-1000,-1000,738,427,-105,687,990,-238,-901,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.Long:OTg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{428,-120,-244,443,-554,760,-32,-46,983,322,779,195,943,271,-932,-335,-111,322,172,-321,956,749,-668,-353,816,-975,671,783,251,-552,-272,-752,-415,274,105,355,-892,993,417,-333,-505,-847,387,572,178,-628,677,-796,655,-928,-154,509,793,-174,-326,-371,-669,227,-967,850,981,-306,456,-148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{1000,-1000,-1000,1000,395,-1000,138,-556,-799,42,-200,1000,-1000,-894,1000,-1000,1000,917,-1000,-1000,10,1000,1000,1000,-386,879,1000,1000,-489,-1000,-159,1000,1000,881,19,1000,-1000,1000,1000,798,-557,-1000,700,-986,-570,1000,-1000,1000,643,680,-791,390,1000,80,-1000,-1000,705,1000,-531,838,1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{95,-1000,-1000,1000,-716,-1000,-367,-914,1000,1000,-890,531,-1000,-1000,-304,-572,1000,1000,-1000,-1000,-964,1000,1000,1000,-1000,-935,1000,1000,-1000,-846,788,1000,-307,1000,1000,1000,-1000,1000,1000,1000,-1000,-1000,1000,-1000,-1000,1000,-1000,1000,1000,568,-1000,717,1000,-894,-1000,-1000,1000,1000,-1000,1000,1000,-1000,-1000,116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{-11,1000,1000,-1000,1000,1000,963,668,-1000,-488,-794,936,964,1000,270,-988,-867,-1000,703,1000,1000,-1000,-1000,-1000,605,1000,-1000,-987,1000,684,-1000,-220,253,-862,-1000,-1000,1000,-1000,-220,-1000,1000,-1000,-422,1000,315,-1000,1000,-429,-1000,1000,970,730,59,1000,1000,818,-1000,-1000,675,-1000,58,736,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{-1000,-816,-1000,610,-1000,-741,-172,-865,1000,27,188,499,-152,-110,-353,-1000,511,1000,-707,-231,1000,1000,370,948,-178,-637,1000,1000,-9,-1000,-147,-139,37,1000,-69,978,-1000,1000,828,-318,-1000,-1000,571,400,414,1000,856,220,1000,-408,-760,366,386,-1000,-1000,-560,431,222,-885,1000,1000,-1000,-337,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{-109,611,334,1000,-116,117,-728,1000,-511,-55,1000,1000,-380,-1000,-1000,-1000,-1000,-726,703,-312,-1000,679,79,399,-829,1000,1000,196,-536,994,-887,32,369,652,-1000,1000,-702,944,336,820,-977,-152,683,-773,-662,887,-630,-257,634,1000,-1000,170,-356,81,-414,-563,-1000,758,-1000,-1000,410,-372,253,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{1000,-1000,-1000,1000,-946,-640,-781,-221,1000,587,632,1000,-432,-1000,468,-1000,888,1000,-1000,-1000,-268,1000,732,1000,315,-858,1000,1000,-371,-1000,715,102,890,395,1000,1000,-1000,1000,1000,1000,-1000,-1000,119,-821,-1000,-218,-673,205,1000,-1000,-1000,360,1000,-1000,-1000,-1000,-794,1000,-1000,1000,1000,-988,-597,574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{-1000,-668,287,610,-999,-380,-376,-422,-400,-702,167,499,360,-110,-353,-83,223,-31,-311,909,496,-400,-371,53,51,-346,764,-987,-9,-384,-287,-220,37,-862,-1000,-1000,-1000,-121,828,-318,-671,-432,305,390,414,-168,934,-43,982,-408,-729,253,386,-1000,-633,-560,-11,-526,-885,687,1000,736,-150,251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{429,-929,-433,684,175,141,337,50,-131,-683,334,-1000,-19,-390,1000,646,1000,969,-142,1000,-738,305,-639,356,514,-227,-37,-1000,87,-1000,1000,-936,349,205,-339,-944,912,-1000,3,25,413,-87,-575,-1000,-265,1000,121,400,62,-932,1000,1000,511,-1000,1000,-1000,1000,766,-118,121,-442,1000,926,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("java.lang.Integer:NjU1MzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{-968,-1000,1000,49,901,488,-716,-278,-467,-608,628,15,619,450,839,-1000,-160,538,-582,-408,630,-913,1000,-1000,667,180,-301,488,739,233,-1000,420,1000,-79,-686,-8,155,-2,929,-1000,223,1000,-321,-632,466,-376,226,424,245,-1000,283,60,382,-972,956,393,-411,-1000,945,1000,-306,-759,596,981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{406,-913,-1000,150,-1000,-557,-1000,865,618,-148,-537,-314,-143,-518,-286,819,412,-107,-151,585,-479,1000,-1000,482,-412,-436,873,-871,-391,-463,1000,-1000,-517,-45,219,-1000,363,-298,-334,543,434,-1000,-562,-904,-664,563,-405,803,591,468,102,408,-436,381,-369,-575,588,766,232,121,472,1000,592,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{122,-1000,-783,-737,-530,304,-88,384,217,-588,-991,423,1000,-1000,428,-1000,-1000,227,-27,569,1000,725,685,-362,211,-547,-317,836,-842,1000,708,73,-986,-1000,-1000,925,322,-1000,1000,536,112,-1000,-1000,-1000,-1000,985,-660,534,-624,1000,1000,289,41,-941,-273,239,-602,-1000,-300,1000,258,752,-1000,348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{-279,-1000,-1000,-30,-483,515,-71,907,340,-930,-24,-710,-498,82,590,-532,594,235,-697,692,357,1000,-243,-405,-477,-629,701,168,-40,600,722,-1000,-1000,-985,-433,-1000,1000,-1000,1000,327,772,-789,-1000,-1000,-1000,1000,628,751,600,524,1000,837,161,-958,91,-1000,922,-1000,-1000,896,-261,756,-1000,611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{630,-1000,-1000,464,-119,930,-342,220,-824,-1000,-915,-20,-230,1000,873,1000,1000,-155,623,-440,926,1000,-680,1000,-396,-535,311,-32,484,1000,454,-1000,456,-1000,-825,-1000,1000,-1000,878,-113,240,-546,-708,-1000,-1000,353,1000,491,1000,1000,795,152,1000,465,-1,-1000,1000,-165,-1000,0,-210,-112,118,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{-966,-1000,-631,-1000,185,-248,-276,908,-864,-1000,489,-1000,-586,874,1000,1000,587,538,1000,1000,1000,258,-348,-134,-269,336,342,-576,391,-443,1000,-315,-368,737,-1000,-1000,1000,-614,1000,673,1000,-950,-1000,-1000,-1000,1000,447,1000,-980,-111,1000,640,517,-1000,454,-830,411,-1000,-971,1000,1000,-638,-268,-546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{748,-313,883,29,850,1000,600,-911,-1000,-597,754,20,1000,-1000,1000,-442,-840,724,883,-660,-666,29,534,437,819,-850,417,655,507,-1000,385,14,1000,921,311,649,-397,-938,-44,-19,-1000,968,20,-659,990,-591,226,-753,-1000,-115,1000,-1000,749,634,1000,-283,-311,999,-613,-768,620,-1000,-548,972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{429,-216,-794,-38,185,-510,194,885,604,-767,-616,-310,-231,676,968,693,1000,716,-435,1000,-999,360,-664,573,173,-119,64,-298,98,-1000,1000,-286,-366,69,98,-302,753,-1000,-453,-71,957,-439,-598,-794,-599,1000,894,649,-464,-1000,912,1000,511,-1000,761,-788,1000,725,161,132,-442,1000,926,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{1000,520,1000,466,-602,-1000,-76,462,-400,-642,-1000,871,1000,-75,469,1000,-280,1000,513,19,761,-715,508,1000,-1000,609,32,-753,-727,-1000,-1000,-476,-1000,602,-1000,784,-840,-195,-1000,-476,181,-888,-515,-543,-820,833,703,-1000,-1000,278,16,1000,-97,555,-632,-696,1000,-110,1000,1000,-879,-1000,1000,-199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{1000,664,1000,555,-25,-1000,304,611,-672,-418,-1000,873,1000,-847,129,496,-286,1000,613,1000,1000,232,578,321,105,1000,32,-1000,-346,-1000,-1000,924,-1000,386,-1000,61,-1000,613,-1000,-1000,1000,-1000,885,321,-1000,-139,410,-600,-1000,443,599,1000,826,302,-1000,-720,1000,-879,902,1000,230,-1000,339,717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{-1000,520,1000,-1000,419,-247,398,-598,1000,101,-79,-16,655,1000,1000,1000,1000,-1000,513,1000,761,849,1000,1000,1000,-176,1000,-753,90,-784,282,787,1000,-1000,-191,-542,1000,989,144,81,242,-888,-1000,-436,190,1000,1000,-416,-389,279,-323,-400,-97,336,322,1000,-1000,-1000,-730,-907,-973,112,698,-84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{1000,851,914,369,647,-1000,-384,-1000,-110,490,-286,54,1000,-1000,115,-739,-385,1000,-393,-1000,578,-19,111,-216,11,409,-1000,-55,299,-778,-796,557,-1000,655,-433,15,-1000,1000,-1000,-802,171,-1000,1000,329,-400,-1000,-1000,-335,-745,602,347,1000,121,-392,-811,-1000,930,-19,784,1000,168,-400,-742,9}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{-1000,520,1000,-505,1000,-1000,-417,94,1000,428,-247,-228,1000,653,1000,-1000,1000,-345,727,646,1000,-271,727,443,-39,-155,421,83,524,-1000,-454,352,-207,-563,606,-616,168,1000,-1000,-182,1000,-727,-775,-763,-982,1000,108,-1000,-420,616,528,27,692,252,-1000,4,144,-1000,635,131,-512,-1000,-272,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{-691,896,587,583,303,223,299,-1000,-167,1000,711,467,86,-504,275,1000,-158,-597,-1000,-1000,381,864,-206,-821,998,-498,-1000,69,650,-840,1000,581,534,-224,-83,-202,-1000,1000,759,-27,171,-825,172,898,932,-86,-1000,-113,-44,-4,-887,-1000,-635,-963,1000,24,-994,208,-1000,-158,-700,830,-438,21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{831,896,587,-716,468,-911,-661,-410,292,-100,-789,-770,1000,-1000,275,-108,200,1000,-1000,-1000,381,1000,-206,-821,1000,1000,-1000,-671,-862,-840,-841,1000,-857,804,-1000,152,-1000,1000,-901,-1000,1000,-1000,1000,898,1000,-1000,-1000,985,-402,-4,172,-1000,93,-966,67,-556,779,-724,-378,702,-512,629,-787,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{-400,664,1000,-772,549,-687,743,-425,1000,358,-458,357,1000,251,402,258,414,-117,209,-1000,1000,124,606,-98,966,565,-5,-242,67,-1000,-854,1000,-120,-653,-762,121,-65,1000,-1000,-427,-911,-680,512,-234,-103,25,-820,-157,-700,456,-213,-1000,185,-328,-438,-399,271,-1000,425,1000,-772,-400,545,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-322,-717,-437,671,-1000,-829,1000,-182,-271,-1000,-819,137,636,924,431,-515,71,-1000,-541,588,594,851,-1000,1000,1000,-76,-1000,1000,1000,-403,601,45,-1000,-286,422,-78,429,-1000,-607,-536,719,-133,-508,587,83,461,82,956,292,-532,11,319,-1000,-976,-540,-1000,1000,-550,-128,33,-186,-177,311,130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-649,-1000,-376,417,-1000,-422,-876,220,-1000,-72,-79,-177,382,-340,745,1000,927,-463,1000,1000,-763,-1000,-1000,458,-1000,1000,-1000,-890,-37,-1000,138,1000,759,199,1000,1000,1000,-466,-1000,-663,1000,-196,-476,348,-1000,695,1000,997,127,-552,494,-464,-31,-42,-1000,1000,-549,-875,1000,456,-1000,-1000,480,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{1000,-552,-278,417,-844,-422,987,-1000,-38,-266,58,852,-969,-340,22,-1000,514,946,28,-404,833,1000,961,467,513,-124,-604,815,-37,89,-527,-84,-641,-170,-242,-224,-731,72,730,-16,-355,542,245,61,-490,1000,-913,406,260,-517,925,-493,-31,-527,-398,536,616,-425,-66,270,-1000,-386,-374,67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{1000,-1000,-428,136,-916,-691,674,-816,1000,-802,554,-763,453,174,-513,172,-93,1000,192,161,-1000,247,1000,242,209,-429,-748,31,1000,431,-414,1000,-559,-1000,646,-979,696,87,91,-19,-490,1000,-643,-167,-1000,1000,-1000,-433,367,-611,631,-335,665,-926,629,544,-408,-583,-1000,58,1000,-415,166,36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-605,-592,433,-600,297,-96,-349,211,-294,-40,-351,544,-666,326,237,410,934,959,924,750,-508,459,-731,334,-258,931,-868,-493,-350,-183,504,263,236,-194,748,335,54,-678,-560,67,998,-724,-265,641,-324,801,195,945,-430,-518,365,-192,-591,250,-981,640,767,-458,-9,617,740,-397,724,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{396,-1000,-894,-21,-1000,-806,-1000,-368,-488,213,383,-1000,-297,-788,-358,624,949,298,1000,1000,-59,-807,-541,46,-867,896,-1000,-1000,-596,-1000,-714,1000,680,-284,1000,764,477,112,-405,-728,204,962,-471,-112,-1000,1000,82,659,458,-801,1000,-1000,-795,-240,-1000,1000,547,-347,400,448,-183,-874,570,303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-370,415,-490,1000,660,239,-657,1000,276,346,551,1000,130,1000,638,674,-1000,871,-211,-523,-941,387,40,-597,533,-227,711,297,433,1000,-868,400,759,-578,635,-272,136,562,-932,1000,378,367,250,-863,831,-345,-986,-839,184,-298,-14,245,-28,-1000,541,762,-485,-228,-402,-876,-163,-909,1000,861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-863,-709,-505,950,-173,-942,-515,-382,-1000,-67,439,414,-209,174,1000,-365,-139,-1000,130,1000,-1000,247,-1000,-33,-251,1000,-443,-918,-328,-604,-101,162,845,1000,236,569,400,-267,-878,-14,884,-1000,-643,-136,8,-503,546,518,233,-959,631,-615,-294,-462,-1000,734,-339,31,1000,90,-690,-970,226,622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-1000,587,-42,1000,-86,-35,-356,389,-381,110,30,-35,-613,373,1000,-652,-214,-729,-968,-584,-1000,390,820,-459,405,626,1000,924,475,209,-172,-1000,637,752,-796,156,-978,30,-186,664,884,583,-147,-337,1000,-951,400,-144,146,-368,5,155,-421,-764,-523,367,152,311,246,-607,400,-955,834,655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-613,415,-301,592,-822,-337,718,-207,1000,-456,9,750,-205,-33,638,601,739,991,-416,-891,-108,274,40,822,659,-1000,-1000,-944,383,-676,203,-921,46,212,398,240,-295,253,-136,931,702,-759,-486,241,745,532,-1000,1000,136,-1000,898,-425,-960,-669,373,555,1000,368,-1000,-729,396,-542,897,509}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-649,-709,-652,260,-412,-550,-1000,-393,-1000,168,369,-383,122,91,988,982,774,-461,1000,974,-1000,-887,-1000,39,-945,650,-899,-1000,-730,-1000,357,1000,1000,685,1000,826,635,91,-1000,-85,1000,-487,-588,-134,-605,166,115,617,-123,-895,437,-559,-303,-194,-1000,1000,-284,-662,570,-49,106,-1000,672,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-150,587,-1000,1000,325,-575,-971,-980,129,227,1000,-743,-883,-989,451,-72,627,-385,-936,704,-677,-1000,-458,-511,753,628,596,-294,801,-523,-1000,-159,979,752,-702,169,-685,800,309,-439,399,22,220,-693,907,-338,-569,-119,774,-659,828,-1000,1000,-1000,-949,1000,-1000,376,246,117,204,-994,277,862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-1000,-972,278,445,-95,-196,-272,725,93,-703,-620,-587,-1000,249,393,-439,902,-239,-346,1000,-447,133,477,241,902,1000,17,229,729,-109,-285,-587,236,745,150,739,-1000,-645,123,1000,102,-119,-855,-76,781,1000,-1000,721,545,-1000,965,-790,-307,-431,-625,957,345,823,64,-179,427,-803,-229,785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{704,-481,1000,844,-1000,294,-1000,-1000,683,400,-74,-733,-956,-347,-1000,1000,697,581,400,400,-1000,-1000,-1000,1000,1000,-680,910,325,-398,1000,648,1000,-1000,-324,-787,-660,589,-469,-283,-1000,-533,-541,-1000,-203,277,50,-604,-609,1000,-1000,317,-1000,-1000,671,-1000,-345,-972,197,42,-1000,196,-188,-1000,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-356,-1000,650,367,-573,-21,337,-858,-169,555,-288,-187,-755,-316,-479,250,504,203,724,-24,-303,-442,-1000,1000,1000,-660,532,136,702,334,-870,813,-634,34,-262,-198,1000,-480,22,-641,268,-280,-800,561,-29,-246,-526,-699,662,11,813,658,-789,1000,-207,-1000,623,1000,41,-858,689,-1000,203,206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{798,-1000,1000,-254,-886,1000,314,849,-354,510,307,-178,128,-229,1000,1000,-353,235,845,-24,464,481,-254,77,1000,-522,399,-747,623,-176,-118,619,-1000,-184,-65,-363,164,1000,400,-839,1000,1000,-330,-228,376,392,1000,-873,1000,-313,-1000,402,-470,-240,203,-1000,-262,5,583,-1000,-589,-521,-465,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-1000,1000,-995,153,226,404,-778,-1000,-713,-928,759,976,486,525,285,-491,-896,-1000,-680,-1000,747,-1000,473,1000,-1000,-4,-324,-596,524,228,-1000,-922,1000,157,720,183,52,-1000,105,115,-1000,-1000,600,-1000,-76,-408,-806,126,175,381,846,1000,934,404,-1000,1000,1000,15,-919,1000,632,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-508,415,-105,1000,4,124,-386,-311,-433,-1000,-1000,561,361,1000,-876,-317,-1000,-1000,-928,1000,951,-47,-891,1000,207,739,-580,1000,-1000,804,561,-357,-520,35,1000,318,-293,676,749,-789,1000,185,736,-619,325,-107,-1000,616,-376,377,828,261,-1000,327,237,1000,-206,-19,-1000,266,1000,622,582,-579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{1000,-481,-264,1000,167,488,-646,-177,-452,-1000,-813,1000,1000,1000,487,137,-1000,-1000,-1000,877,1000,708,-796,117,-745,533,-347,916,-1000,214,555,5,695,-476,1000,47,-63,-1000,-1000,-369,-286,605,1000,102,1000,-115,-1000,860,-474,228,229,-811,1000,-727,477,-667,-870,-1000,-1000,1000,1000,207,536,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{64,1000,-1000,-740,1000,-1000,429,4,25,-664,-235,36,752,-583,1000,-1000,-1000,-1000,-885,-479,1000,-1,573,-1000,-1000,1000,1000,-359,-1000,609,-107,-1000,1000,840,1000,851,-1000,-1000,-148,-472,-1000,-1000,446,-1000,-778,-1000,-1000,832,-994,880,1000,-619,1000,112,-396,733,957,-1000,-1000,1000,-719,1000,-115,-979}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{1000,-1000,885,167,-1000,28,-531,-599,413,1000,484,-870,748,-1000,-135,961,1000,754,618,1000,-861,-203,-1000,870,1000,-1000,1000,-19,857,-384,-921,1000,-1000,421,-165,-890,1000,468,264,-379,1000,-660,-1000,1000,-750,-597,339,-1000,763,-938,882,688,-1000,204,-1000,-1000,-154,798,491,-1000,109,-1000,-834,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{635,-357,1000,1000,-1000,983,-1000,-1000,556,-843,-178,74,709,246,-1000,1000,289,379,400,-412,-1000,-525,-1000,1000,1000,-520,323,167,-59,1000,704,904,-1000,-437,-100,-1000,453,-38,-190,-1000,1000,-307,199,-203,858,103,-82,15,1000,-1000,206,-175,-540,-252,-716,-345,-972,116,44,-1000,956,-93,-950,-228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-639,580,-392,993,-187,-273,579,62,148,-57,631,859,-956,-258,423,25,504,-922,-118,235,-576,132,-316,275,-636,568,854,434,-398,330,-251,-190,904,-794,-787,-310,589,-175,-3,973,-533,-541,-301,-203,12,92,-604,-69,727,-413,924,-665,92,-570,313,-670,696,570,-699,251,205,141,997,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{870,1000,1000,1000,473,1000,-296,-440,461,580,-169,-656,150,1000,-479,808,-135,38,-156,-31,585,920,-873,1000,-619,1000,-892,1000,-287,-478,-1000,-837,-383,60,-1000,523,1000,659,-933,-148,1000,145,-1000,241,-1000,64,154,-768,-400,246,1000,-1000,724,-812,-1000,-1000,-1000,-152,1000,665,-821,706,-613,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{-375,411,1000,639,1000,327,1000,-1000,-380,1000,490,499,-261,1000,-223,747,785,871,-1000,-834,1000,77,-1000,-1000,-655,1000,-607,979,-970,493,-1000,461,471,-654,-603,334,-490,1000,-54,803,76,632,-446,-555,-784,96,770,109,-779,-630,1000,-180,-1000,-781,-263,-1000,-1000,-170,921,-892,-864,-1000,315,-928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{-534,1000,777,1000,702,126,-296,-920,-977,-919,904,-1000,871,1000,-853,738,684,327,-1000,-365,1000,189,-1000,503,-1000,1000,-892,275,-351,1000,-1000,-1000,426,-1000,-1000,1000,354,659,-933,-532,1000,555,-1000,-238,-376,555,686,-437,-973,1000,1000,-131,-702,-451,-1000,-1000,-1000,-152,470,-784,-594,-764,71,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{-1000,-449,-1000,388,-456,-794,-107,1000,294,-61,554,-941,1000,1000,-596,-117,236,1000,-1000,-1000,250,341,-1000,-320,411,1000,-484,-36,-81,1000,-865,-829,1000,-6,-691,122,-555,125,-946,-528,1000,1000,-1000,1000,478,-261,-105,656,1000,187,-104,433,-995,1000,-653,-849,-1000,656,243,282,-654,-775,-1000,-516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{-904,1000,1000,1000,-53,400,274,899,-320,391,-626,-1000,1000,1000,30,-592,127,38,-1000,-896,1000,-41,-1000,183,-1000,1000,-1000,1000,-584,-840,-898,-871,718,-6,-1000,155,361,5,-1000,-1000,1000,1000,-1000,831,-1000,163,770,632,1000,1000,1000,-990,-303,-462,-1000,-1000,-1000,-1000,-227,549,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{749,1000,1000,1000,1000,1000,-626,-1000,-273,730,-939,-384,-776,827,294,750,-147,-1000,-678,1000,349,121,-400,1000,389,310,-1000,1000,577,-659,38,-942,705,-158,-1000,575,-170,1000,310,-297,30,-926,-1000,780,-628,-312,1000,29,-712,1000,1000,-1000,606,-1000,-827,-1000,-632,239,1000,-620,435,512,-665,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{-713,-1000,-1000,-1000,-232,-1000,-237,1000,585,209,451,259,-265,-289,-208,-356,907,1000,764,-1000,-928,-4,1000,-690,1000,-888,593,-1000,178,1000,-467,966,561,-243,1000,-1000,-789,675,699,642,-1000,1000,400,938,1000,-584,-672,919,1000,-725,-1000,1000,-1000,1000,1000,543,1000,368,101,186,-451,-964,1000,491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{-327,831,1000,1000,1000,1000,1000,-1000,-320,1000,570,613,-334,1000,-386,1000,1000,1000,-1000,-146,1000,150,1000,-527,-701,1000,248,782,-1000,-840,-1000,610,-301,-1000,-984,979,73,1000,1000,1000,444,283,594,-1000,-1000,180,770,-665,-1000,-772,1000,-600,-740,-1000,-656,1000,-1000,-138,921,-1000,-617,-946,555,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{322,1000,1000,1000,1000,1000,-362,-1000,-416,112,86,-941,-629,1000,-975,1000,-99,-836,-400,199,695,750,-1000,1000,-995,1000,-1000,1000,282,328,-1000,-1000,-618,-53,-1000,1000,1000,816,-1000,223,1000,165,-1000,1000,-1000,465,148,62,-1000,1000,1000,433,917,-1000,-653,-1000,-1000,-32,243,282,-654,-100,-237,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{1000,-345,1000,-1000,1000,-1000,-1000,-659,-1000,1000,1000,-637,-80,1000,-1000,1000,875,117,-609,1000,12,372,1000,-1000,-1000,-470,402,-97,653,1000,-1000,1000,-815,-299,1000,-590,-1000,1000,-256,-207,-63,-1000,-1,951,-1000,1000,1000,1000,-418,-1000,-1000,810,-1000,-657,1000,-424,-1000,267,435,-798,-1000,-1000,963,-627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{1000,1000,-399,50,-652,492,-106,-982,-199,-10,-1000,-530,-868,-1000,1000,-1000,-180,-740,-906,-305,611,-515,-302,374,1000,869,-902,-831,231,190,690,75,866,-1000,-659,-12,-471,-98,-651,1000,157,-1000,1000,703,402,-1000,-896,271,-143,-856,-1000,-273,456,124,67,-818,-1000,-750,-95,666,-439,-1000,189,680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{744,1000,-1000,-103,-184,141,96,-431,1000,-75,-1000,-673,-389,-285,1000,175,-244,-415,-542,-1000,931,-261,-24,392,217,468,-391,-908,-1000,506,876,102,773,-460,272,-554,-1000,-227,-70,1000,556,-1000,460,229,74,-876,-725,118,-1000,-1000,-1000,-890,630,621,509,-405,-1000,735,-90,1000,-1000,1000,287,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{1000,-268,-958,118,289,954,-109,-44,1000,799,-1000,-297,149,13,1000,389,-498,-827,-1000,-631,749,-282,372,-181,642,387,-583,-1000,-1000,1000,1000,-239,1000,-1000,184,-868,-1000,-482,535,1000,556,-1000,1000,229,154,-623,-170,-1000,-1000,-1000,-1000,-1000,643,725,881,-457,-787,1000,-381,590,-1000,891,-225,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{744,-265,-481,-505,860,141,123,-220,1000,-568,-1000,1000,-342,459,-256,1000,-169,573,294,-564,1000,362,-24,-1000,-467,-726,322,173,-481,51,50,-143,-390,12,886,-868,400,326,706,-400,723,-214,-400,-622,-472,-127,-378,-1000,-1000,-443,-557,-971,1000,621,-169,-669,100,1000,696,900,140,1000,-186,-320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-400,1000,-1000,-343,-221,-761,-516,-383,-358,-229,-607,293,97,-340,-776,1000,347,-14,-936,-413,1000,-704,700,-141,-1000,-256,271,-1000,-1000,-529,928,532,113,829,1000,226,-3,453,367,79,542,-1000,9,-167,603,282,-965,-793,-1000,-967,153,-470,544,706,-274,-260,64,41,-245,856,-1000,-77,-563,-342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{96,664,1000,-432,-586,-1000,-872,-794,-1000,-307,765,-363,-934,115,664,-666,434,279,398,641,-331,-425,-1000,-166,-434,627,-624,231,1000,-699,-541,856,-146,280,349,1000,-117,-277,-1000,357,1000,75,-400,1000,-199,-832,-77,36,554,-641,-644,-217,-1000,-1000,95,-288,-640,-340,-338,-820,591,-924,1000,-136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-1000,708,861,-993,-556,-850,-81,-301,-534,-1000,940,1000,-892,924,-527,940,761,1000,1000,364,1000,116,-323,-1000,-1000,21,7,517,719,701,-723,1000,567,1000,1000,108,984,914,-1000,-811,1000,435,-1000,400,-714,-529,-467,711,288,-630,701,-137,-1000,-1000,-392,-137,-115,305,122,390,-443,368,876,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{1000,547,314,-110,40,-431,-1000,-240,-1000,952,318,-1000,-297,362,794,-476,-157,-1000,-1000,899,-362,-1000,-158,382,584,590,-1000,-676,134,701,357,280,985,-1000,-3,1000,-1000,-803,-735,1000,761,-1000,1000,1000,-106,-570,504,250,-633,-1000,-1000,-1000,-930,-493,737,-426,-491,273,-538,-447,-1000,-1000,372,606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{1000,683,226,311,-916,636,-259,-760,-1000,1000,-1000,-1000,-628,-1000,1000,-1000,-902,-1000,-1000,-411,5,-592,242,1000,1000,1000,-1000,-1000,-127,41,1000,-20,1000,-1000,741,-131,251,-528,-720,1000,20,-1000,1000,1000,823,-1000,18,1000,115,-1000,-1000,-273,-70,-457,391,-417,-1000,-835,-1000,-283,-870,-1000,290,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{-1000,-105,-184,20,329,1000,856,-893,-894,589,184,1000,232,204,776,1000,1000,-1000,-893,616,-185,-307,494,1000,-665,738,714,-1000,-456,636,224,862,-721,-212,-1000,27,1000,248,1000,291,393,-1000,-710,-16,1000,-373,581,-444,1000,341,71,-231,816,418,859,-81,-657,-1000,1000,-234,-1000,718,-60,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTQy", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{-1000,516,-232,258,1000,69,-681,718,-1000,1000,-871,1000,1000,187,964,-662,-97,-1000,207,-1000,-644,-57,149,-45,-621,196,942,-869,-1000,-649,-422,394,1000,549,-1000,-662,-27,705,1000,-1000,-69,951,1000,1000,400,173,579,612,-459,917,936,1000,-865,-350,-1000,786,-402,-961,1000,1000,62,-406,-153,-336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{71,1000,514,58,128,579,-1000,-419,-25,195,-112,24,1000,1000,543,-42,538,1000,-285,-1000,794,-827,-1000,348,538,-1000,622,-261,-827,-821,-964,538,237,-267,-357,563,-1000,1000,308,-603,643,882,-51,-679,-1000,835,-897,1000,-270,504,-144,1000,88,672,-1000,-74,45,-449,1000,1000,-745,-577,-921,-62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{-961,411,-93,857,333,420,532,81,231,548,280,-904,481,919,-136,-601,21,921,372,282,-356,426,542,-297,-563,-639,1000,-363,-249,933,245,659,-271,-910,121,-257,482,362,173,-299,903,167,900,536,799,-155,-786,80,512,-770,-386,-289,559,393,-449,720,934,-649,-756,-72,-352,-3,827,-482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTMx", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{242,1000,-265,-20,-991,484,-1000,-950,613,-1000,233,-265,1000,675,214,-734,1000,1000,553,-195,429,283,-1000,602,-313,-902,1000,58,-918,-237,-853,326,-382,-1000,-951,1000,-827,801,-968,-1000,969,1000,-278,-887,-1000,668,-3,946,-527,761,292,892,619,388,-234,225,-837,-988,-343,952,-782,-558,-879,222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTQ2", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{-1000,1000,-44,-234,-261,154,-1000,-419,-796,74,-637,612,1000,772,887,-423,-723,549,141,-1000,-1000,-922,631,409,1000,-416,622,798,-1000,-408,-987,291,-865,-463,-218,114,-372,950,394,-980,215,-510,-165,118,1000,-180,-617,-245,521,-937,-26,419,-3,243,-1000,-141,-182,-91,126,1000,-1000,-495,458,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{-1000,587,83,367,155,841,400,1000,613,-138,204,1000,-382,-635,17,518,-290,1000,-127,1000,591,-941,-1000,1000,-427,-78,559,-124,234,138,114,1000,-531,24,355,395,63,445,-658,386,274,-930,-884,-486,-1000,126,1000,759,353,103,-697,-298,1000,192,1000,-1000,-364,400,-1000,-1000,-875,536,124,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{1000,1000,-1000,501,-389,-326,129,-676,-1000,71,790,73,1000,-752,432,-751,279,866,-398,1000,-197,-556,-514,116,-859,-374,222,347,385,-251,159,-94,76,336,-393,920,746,346,189,-476,-468,1000,903,-63,-778,-57,-464,-62,-535,-702,97,1000,-736,1000,505,143,1000,-598,324,-245,-456,-533,-1000,-583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{-763,-741,-232,22,-773,705,-299,346,365,1000,23,-257,570,185,740,-767,309,1000,438,743,583,-511,-1000,886,-621,-1000,892,-164,-786,192,-918,218,-712,-835,-552,-662,-731,580,-729,-661,916,-297,-850,-1000,-1000,1000,-1000,1000,-55,726,-386,643,819,979,486,-186,153,-20,-1000,-239,-233,-1000,53,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{-962,1000,109,564,474,1000,-991,54,194,-245,21,1000,1000,1000,-196,-295,-284,891,245,-507,-492,706,253,66,-242,-1000,1000,-937,-1000,547,-394,835,-152,-1000,-309,248,159,331,404,-931,1000,388,422,-51,326,-150,-325,641,296,21,-169,1000,284,-89,-849,1000,462,-798,1000,545,-413,-18,651,-251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-17,515,-1000,283,-1000,-1000,-836,1000,-1000,-1000,253,469,1000,-1000,396,-776,-1000,503,-562,-1000,1000,-1000,-1000,-1000,-180,-1000,333,-733,1000,987,591,-1000,-1000,-732,-586,-920,1000,761,403,224,1000,1000,-777,1000,-1000,1000,-1000,-481,-426,1000,441,1000,1000,-288,-658,-1000,217,-1000,-1000,1000,-1000,878,1000,974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-1000,-1000,1000,995,1000,1000,423,613,1000,-683,526,-93,-1000,-154,1000,741,-895,574,1000,-1000,946,1000,1000,1000,-1000,1000,-1000,-1000,933,-572,620,539,907,-1000,-1000,-242,-919,-1000,-1000,-578,214,-1000,-351,-732,-297,675,1000,-1000,1000,221,-996,-1000,-1000,-362,969,1000,72,739,-1000,488,118,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-989,-1000,502,878,1000,-365,-186,-653,679,-735,339,706,-1000,-328,952,-408,-1000,934,636,-499,743,409,179,-342,584,380,-691,-445,844,741,444,-413,156,-819,-9,-158,-446,-321,-750,243,1000,-645,-852,356,-1000,1000,426,-815,404,642,-828,-706,366,1000,310,461,816,-475,-1000,211,-875,685,-401,30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-1000,-437,1000,310,1000,685,1000,399,1000,-1000,461,1000,-1000,-238,1000,-67,-1000,1000,577,-745,453,586,972,-208,1000,927,-784,-181,1000,1000,201,-697,-434,-809,606,-882,-612,-1000,-234,-156,1000,168,-1000,278,-928,985,3,-1000,-153,948,-1000,-1000,366,617,249,1000,1000,-1000,-584,104,-1000,1000,400,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{60,-301,-920,399,-832,-355,-1000,101,-1000,-42,480,-492,1000,157,-746,-383,480,165,163,-1000,-371,-339,-959,574,-322,-848,-556,-718,708,-366,1000,-934,-974,-508,-1000,705,-38,-109,917,-728,1000,-813,729,516,899,188,405,41,1000,-329,628,1000,443,165,-920,-1000,10,-37,-1000,-477,461,-707,473,-66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-213,592,-501,569,-301,-1000,-431,138,-1000,391,1000,-70,1000,757,-460,-760,-620,1000,651,-993,344,-991,-1000,-595,-584,-383,-724,614,634,943,1000,-396,-890,-364,-525,388,705,720,176,-91,578,556,-891,1000,-897,1000,-287,644,-609,-359,1000,800,-128,-598,429,-1000,602,-1000,-905,957,-4,962,508,778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-1000,103,-861,310,-3,-428,-630,-157,317,-1000,387,-330,95,-526,829,-686,-1000,872,459,227,1000,-530,-111,-7,171,-349,-246,-1000,1000,801,578,-697,-1000,-809,-553,-280,368,256,-234,372,797,-580,-390,251,-928,1000,3,-1000,355,913,-660,472,1000,1000,949,-542,411,-954,-1000,93,-950,780,400,-214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-798,-1000,-585,283,-198,679,-981,1000,1000,-755,989,-121,-71,473,438,-776,-74,468,511,-1000,1000,-186,460,1000,-1000,112,-1000,-548,1000,-877,703,-1000,-497,-732,-1000,1000,-1000,81,403,224,749,-1000,1000,-33,1000,1000,-1000,-856,1000,823,-1000,105,-515,1000,-358,-1000,826,56,-1000,1000,-217,-965,-34,-144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-1000,-1000,179,686,258,727,62,-1000,1000,-32,540,-116,-718,709,20,299,222,306,1000,1000,698,1000,1000,1000,-1000,1000,-1000,-1000,83,-1000,356,229,-329,-1000,-1000,1000,-1000,598,619,-1000,-542,-1000,652,-69,1000,-161,1000,-587,1000,422,139,-752,-1000,893,278,-147,203,424,221,-1000,1000,-1000,-1000,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-1000,907,658,-323,206,-967,1000,1000,1000,-96,505,-401,-189,-424,515,193,-1000,-803,739,-179,337,-76,1000,-944,666,904,-1000,-1000,471,564,457,-1000,282,-1000,-895,-498,335,736,304,-1000,-1000,-1000,-1000,611,-1000,-457,-807,-500,-696,1000,-410,-964,74,351,1000,-1000,-712,-795,-32,313,-965,240,-154,940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-650,695,-1000,-1000,-194,-347,-341,-490,1000,-1000,295,129,-1000,508,331,-1000,-125,829,1000,-342,-1000,974,-1000,-105,208,1000,144,-897,-560,329,-80,614,1000,490,-1000,-384,-659,475,1000,657,1000,-299,548,-391,-621,-342,30,-720,-1000,281,-1000,-1000,647,378,754,-367,-172,-487,-1000,-442,1000,495,930,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-834,292,321,-392,479,34,-673,253,258,-102,679,476,394,697,785,-948,-26,-613,-71,1000,240,-354,282,342,339,-593,-994,635,732,280,-1000,127,116,-672,651,-840,652,-767,-395,-507,-311,761,-179,682,147,629,-115,-552,-325,-391,780,1000,-739,151,352,873,-683,463,988,-767,-663,-495,789,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-347,-84,1000,-742,883,933,-997,196,16,28,379,535,-602,1000,1000,-1000,130,-1000,77,1000,547,-650,803,-788,-527,-407,-983,941,609,1000,-1000,-972,-361,-263,460,-79,643,-738,-961,-1000,155,1000,-939,603,882,821,154,-26,382,-813,1000,760,-427,259,1000,285,-955,597,293,-1000,-679,-1000,862,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{654,-493,-614,64,-464,-121,-1000,-311,164,741,-903,957,207,-833,306,1000,747,404,-569,129,817,889,401,183,-216,289,-684,705,304,-1000,-500,931,-218,962,898,-151,194,675,89,-293,-770,200,-467,-161,-302,-326,-72,573,-26,-717,-471,1000,82,918,898,915,-675,574,902,-317,-130,-269,1000,551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-785,-669,791,-583,-405,-27,992,137,-529,853,-789,413,-1000,-551,1000,489,1000,-1000,-1000,-361,1000,-912,1000,45,-1000,689,-6,1000,-596,-1000,931,-590,-1000,-193,222,528,687,415,-1000,-1000,-148,638,-1000,1000,-304,845,-6,1000,690,350,545,222,-382,-296,-385,10,-291,1000,-278,-34,-1000,-1000,-384,-7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-477,-1000,645,848,779,968,-798,693,-922,1000,18,978,1000,279,1000,447,816,-1000,-1000,1000,1000,-699,401,923,-701,232,-1000,1000,1000,188,-1000,-244,-1000,637,1000,-488,738,675,-1000,-1000,-1000,931,-1000,1000,-53,1000,-72,556,-660,-834,1000,1000,-1000,-101,244,916,-1000,1000,1000,-908,-1000,-1000,779,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-742,-1000,-1000,320,595,-448,544,331,-1000,-1000,-557,465,633,-593,350,1000,872,-1000,-1000,75,-147,-1000,1000,393,1000,1000,-766,765,-632,1000,-219,1000,-359,-1000,1000,-30,1000,994,-103,372,258,1000,-760,786,-1000,394,-186,1000,-236,-474,1000,1000,579,-1000,-554,1000,-155,1000,1000,-652,-1000,-1000,6,353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{105,519,-1000,-641,-1000,-342,-917,-423,1000,-9,-562,-192,874,-292,-187,353,-125,1000,237,325,-37,1000,15,-671,-881,-167,-1000,332,-391,-1000,24,1000,237,723,8,-1000,-94,475,1000,277,-228,662,689,-440,-315,-797,328,-370,-958,-1000,-1000,196,151,908,-12,989,-444,-106,443,132,183,495,985,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-1000,1000,1000,-614,1000,315,-992,713,1000,-1000,1000,-300,-895,1000,1000,-1000,-1000,722,1000,82,-1000,-1000,-59,-638,938,391,-804,765,1000,1000,-1000,-1000,370,-390,-925,-28,-763,-1000,-103,-668,-328,-334,615,977,-185,891,177,-1000,-1000,911,1000,-1000,-1000,94,-469,-203,-1000,-247,-1000,-1000,858,-297,517,-686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{1000,-281,-1000,-397,594,-790,-292,-188,-1000,-21,-67,577,1000,-1000,-759,1000,6,924,162,393,-405,1000,988,1000,-421,149,77,865,-383,-810,-330,-1000,190,-1000,653,-139,-1000,-1000,895,1000,1000,-322,-815,1000,826,-546,-521,-1000,-336,-322,-554,-224,-1000,-1000,-506,-1000,-4,735,-731,40,528,376,-171,309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{369,-1000,1000,305,-424,619,-282,-410,-640,-78,-1000,716,285,336,-227,328,-1000,-674,1000,727,-702,-975,827,-209,-569,22,334,1000,1000,161,-550,1000,760,-1000,-659,-619,-335,-1000,695,59,513,165,-141,777,1000,-1000,776,-240,-946,157,-909,-625,209,958,-745,395,-795,206,1000,-454,753,-103,689,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{211,1000,570,803,-1000,749,815,-1000,-1000,1000,490,1000,-39,-803,-992,-44,1000,-101,1000,-1000,-1000,799,-1000,-315,-319,1000,71,1000,1000,1000,-987,-178,1000,-1000,-1000,999,-1000,17,1000,-1000,547,1000,-621,1000,922,-1000,860,-1000,-1000,1000,405,-1000,971,1000,-910,-492,-1000,-302,1000,476,1000,-1000,714,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{-847,-684,570,-545,182,239,421,-1000,-731,1000,574,1000,1000,-1000,-1000,118,1000,1000,1000,-255,-1000,-221,-790,819,-1000,-1000,71,777,1000,51,-900,260,-28,-1000,-302,-387,-1000,571,1000,-417,1000,183,-1000,1000,55,-1000,337,-1000,148,505,95,-1000,-156,387,-959,-341,-784,724,1000,-252,1000,524,897,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{924,1000,889,750,-624,-625,-706,-166,588,-601,-939,-1000,-1000,1000,-432,969,-835,-627,771,-1000,-1000,403,1000,-476,611,-158,857,575,-582,1000,-1000,149,1000,364,-897,1000,400,-56,515,660,-661,185,1000,-200,163,-1000,1000,-41,-1000,1000,-828,-1000,1000,1000,-66,-1000,-771,29,-185,1000,951,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{369,-1000,509,-794,717,85,52,-597,-669,1000,-930,1000,357,-201,-1000,218,-337,-263,647,1000,-668,-717,1000,780,-1000,-625,433,859,694,-102,-925,-149,185,-1000,403,-1000,-1000,-1000,876,923,729,-92,-867,1000,1000,-722,-260,-943,720,-352,1000,-587,-953,-118,-509,386,-28,284,106,-1000,500,999,12,-575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{492,39,-1000,346,-118,-275,-1000,1000,-401,-885,-936,-116,1000,-188,138,1000,-1000,464,-885,235,287,290,701,698,-189,-603,-3,406,-1000,-1000,996,408,564,1000,382,304,160,-680,-549,891,1000,-696,-89,23,232,-148,284,210,187,-1000,-1000,1000,-238,-1000,118,223,629,949,-813,728,781,368,652,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{1000,299,-509,702,-546,-256,-626,-1,-971,-546,-137,-398,928,-463,-617,1000,-656,514,515,-296,-440,743,45,262,-257,797,-22,1000,-77,-547,45,149,765,-603,-409,820,-92,-995,714,136,784,-64,-89,747,826,-823,515,-297,-546,188,-1000,-85,162,383,-743,-16,-771,657,163,869,781,99,506,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{-248,-1000,426,717,-458,619,-626,646,-428,-1000,-482,-259,717,571,662,480,-1000,514,602,252,-48,-1000,456,-803,348,797,-244,1000,339,-358,45,1000,181,-101,-930,-338,-92,-685,-516,278,616,-367,584,188,-2,-410,1000,307,-63,-190,-127,466,972,634,-703,889,-162,794,982,234,661,758,462,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{92,1000,-155,953,-819,469,-666,-410,-148,-1000,568,-1000,897,313,437,478,-579,219,318,-808,-162,-512,-428,-666,652,1000,-1000,1000,251,-624,856,689,217,-1000,-524,902,1000,-21,-656,-503,524,205,409,83,-350,-293,776,496,-190,332,216,623,660,537,-740,-58,-356,443,703,601,753,271,375,596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-1000,1000,1000,-84,-985,-1000,-781,-1000,-263,1000,-1000,-626,902,-436,234,-339,-510,350,193,371,192,-32,-1000,-1000,878,260,-1000,-1000,-1000,421,-50,59,-296,-141,977,400,125,-713,-295,-372,414,660,609,1000,237,922,-1000,846,1000,1000,-241,-896,1000,519,-879,-281,5,688,1000,-1000,-83,-861,824,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-495,-665,648,3,254,-93,-94,-1000,1000,962,1,-505,381,-1000,-1000,744,-214,252,589,454,646,-376,669,-311,649,1000,1000,-1000,816,-642,397,530,710,-693,734,-922,544,-977,651,1000,208,274,-1000,-890,1000,171,649,-798,438,-22,-1000,-298,1000,1000,-648,-1000,208,-173,85,529,1000,389,-176,693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-978,400,-197,-632,-612,433,-276,-873,905,430,-501,-567,604,-1000,1000,-1000,-80,1000,-6,1000,390,-204,73,-1000,1000,374,-1000,-436,888,-1000,82,165,-346,-399,772,209,1000,-1000,1000,1000,-242,1000,944,86,1000,-1000,-524,-490,208,504,-1000,1000,1000,683,-819,-653,234,1000,763,-944,-1000,-571,1000,668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{959,1000,1000,118,-1000,-10,-163,-431,717,1000,-158,-92,1000,1000,1000,-956,-463,-425,418,1000,767,-541,-1000,-674,814,493,-1000,1000,1000,396,22,-339,842,139,631,1000,-810,-1000,-314,31,1000,830,1000,1000,-1000,541,-1000,94,1000,440,-618,668,1000,964,-188,-367,1000,1000,-1000,23,-242,-1000,1000,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MjIy", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-1000,1000,1000,-779,181,-1000,709,-316,-630,1000,-907,-1000,414,983,293,-1000,-1000,222,59,55,147,554,-1000,180,-653,-1000,447,1000,-1000,469,-489,-748,-587,-14,293,38,-133,-1000,-1000,-873,1000,-943,-195,1000,905,-560,-1000,1000,964,1000,732,17,823,-511,1000,-410,140,42,139,343,-63,-548,734,-105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-1000,1000,1000,-23,-1000,-1000,-806,-1000,-263,1000,934,165,1000,-456,400,-568,-1000,1000,-95,1000,-136,-447,-1000,-1000,814,260,-1000,1000,-1000,396,110,-727,842,-141,977,548,380,-1000,-144,-281,975,272,609,1000,363,419,-1000,1000,285,1000,-232,423,901,447,-848,192,139,1000,1000,-1000,-546,-861,1000,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{233,-777,-574,-451,661,-868,-12,413,1000,-83,622,-248,-417,-754,367,184,596,798,-702,-199,-3,-756,177,-618,1000,1000,664,-1000,640,1000,1000,-345,895,12,-1000,-626,388,164,972,923,-158,365,-717,-117,827,189,496,-647,312,19,-796,378,-977,501,-466,800,78,-50,-401,150,-197,165,518,-380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{262,-1000,-1000,3,664,1000,229,-643,1000,13,821,-284,-936,-607,-334,214,456,-119,-61,-510,454,-119,1000,-143,817,1000,535,-1000,800,-195,35,530,710,-1000,734,-912,140,1000,651,703,-940,885,-1000,-1000,554,313,1000,-1000,560,-581,-725,-691,-731,460,-778,-498,101,-757,-730,822,1000,540,22,454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-1000,207,-340,157,-10,665,-81,678,1000,476,-606,-563,1000,-1000,504,882,542,597,658,1000,930,-529,176,-535,1000,237,235,-620,-620,-1000,-91,479,1000,-600,-977,-2,1000,-1000,756,1000,1000,1000,325,-362,1000,67,-1000,-314,788,-523,-1000,786,312,1000,-57,-932,-97,1000,779,462,481,-1000,1000,688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{1000,-1000,266,64,292,192,-515,326,-400,-46,1000,301,-593,-743,-834,309,-210,668,634,118,23,363,955,-745,-340,1000,950,-614,1000,-826,771,1000,866,-1000,1000,-1000,1000,713,1000,1000,-889,88,-1000,-800,252,-389,1000,-1000,71,-420,-933,-367,-685,398,-1000,-379,895,-400,-730,282,954,1000,-139,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{-601,871,34,1000,-419,-1000,155,771,-921,381,-1000,280,1000,579,588,-583,-934,1000,-1000,-1000,558,1000,-17,-683,-739,-1000,722,-694,37,499,-141,-723,377,814,-813,-1000,709,-484,-977,-1000,-641,-428,-18,-964,-455,180,586,884,-20,-947,-922,-419,1000,1000,-739,1000,1000,-711,-735,-1000,1000,898,-827,708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{445,1000,1000,732,405,-645,-796,-1000,-834,-819,-907,-273,445,304,840,-703,1000,36,-1000,-1000,-686,1000,-453,-24,-589,-135,1000,-515,1000,-508,-185,71,357,-999,-1000,-245,693,511,-86,369,223,-638,1000,498,803,785,259,655,42,327,-301,798,-295,1000,-1000,-68,-164,321,305,-1000,954,-782,818,-925}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:NTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{-1000,487,-284,507,-1000,1000,-175,310,68,1000,802,168,192,1000,-640,-44,316,515,550,-623,844,-1000,-752,-1000,-748,-430,687,-1000,-167,584,601,589,-1000,1000,95,762,667,-1000,76,-1000,1000,-1000,-658,-617,-866,749,-264,-608,824,-361,-518,-1000,499,542,105,433,271,-495,-290,1000,-277,-656,-164,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{26,812,-15,-949,99,-677,-6,-962,946,-550,447,638,738,-932,280,262,515,465,-832,-800,-1000,-326,203,1000,-1000,-667,1000,496,91,709,924,-295,217,-1000,-133,-515,1000,1000,609,583,477,-825,-783,941,-786,548,-491,78,-59,566,-1000,1000,629,-93,782,737,-615,-387,368,-367,293,-134,-437,283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{372,1000,564,471,292,-663,485,158,-745,-1000,-1000,828,-458,-844,680,708,1000,670,-513,-1000,-189,-912,-413,479,-438,137,758,507,1000,919,-71,-952,820,806,-124,-334,522,-215,-644,-1000,-397,-751,290,298,-492,-52,314,562,173,51,-600,1000,54,1000,-198,384,1000,955,52,-1000,2,1000,-536,37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{232,371,-854,781,-427,-417,1000,-740,-893,-427,867,299,-1000,619,1000,-245,827,-1000,-201,-644,-853,746,-1000,-650,170,-168,1000,-1000,674,2,831,-477,-1000,-1000,-870,27,680,-220,-446,1000,1000,29,1000,366,-126,675,-1000,417,-353,-243,-1000,-217,13,542,-144,-867,-1000,-601,-1000,92,456,-1000,1000,-307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{-505,732,138,371,-646,419,-694,-307,276,868,-164,7,697,732,-344,98,766,935,-712,-346,-246,223,368,-701,-818,-538,382,-877,-732,311,197,141,-86,-273,156,-313,667,-344,196,-557,809,-783,-236,-461,-922,690,115,-52,357,732,-979,-727,159,347,-141,937,697,-626,-401,-220,907,-492,-689,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{224,1000,1000,370,406,-214,556,-647,148,-806,-1000,894,377,-672,-90,921,1000,1000,-1000,-1000,-1000,-1000,22,688,-1000,-112,1000,593,1000,456,-433,-421,1000,-31,-827,72,1000,244,165,-1000,328,-1000,-132,675,-1000,330,264,291,279,1000,-964,1000,-56,882,91,1000,1000,861,174,-1000,189,1000,-1000,-512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{-247,992,748,314,-288,-133,-137,-571,121,867,-982,-446,410,361,-642,27,-217,80,129,849,-231,761,265,799,-207,-900,-366,-981,162,951,348,799,518,-467,-297,879,979,-676,29,-703,-37,249,-971,-487,982,795,-975,357,592,781,920,206,-907,632,683,-287,623,224,683,410,-203,630,-708,546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{-594,779,-378,-1000,-192,-720,581,184,-42,-493,-20,141,-853,79,492,684,-495,-36,-1000,-824,-458,307,-598,-307,219,-999,-246,-13,-274,-331,137,-89,-777,548,-310,-184,-718,1000,-859,264,-230,-1000,-374,1000,-142,835,-846,-457,666,-117,277,-20,-494,392,-199,317,615,442,-572,1000,829,199,-1000,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("VOID|getSize=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{109,1000,-338,661,-884,-413,-49,-124,-848,50,1000,-1000,1000,1000,-481,-959,-119,-400,-16,1000,-666,-456,757,335,-375,1000,-677,-1000,-710,-421,-1000,-1000,-74,-1000,1000,-165,-457,549,800,-347,671,804,540,-1000,-1000,150,-950,151,-865,-674,-746,-54,1000,536,-1000,528,159,505,178,-1000,270,917,8,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("VOID|getSize=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{-1000,684,-1000,-713,-703,-987,739,75,913,284,400,1000,-1000,-1000,508,1000,-501,787,448,400,-354,1000,-1000,-723,742,-967,15,-441,-454,-238,92,-495,-1000,355,-158,-499,-943,-350,-1000,-207,-433,-1000,-194,1000,-587,1000,454,-1000,1000,-595,-32,400,-1000,244,218,664,1000,655,-1000,1000,1000,146,287,686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("VOID|getSize=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{224,1000,-159,-1000,-993,893,-714,715,192,940,-79,620,-1000,944,825,-624,-429,453,-794,347,-1000,996,32,-435,-757,776,448,-1000,-291,797,808,-520,120,1000,1000,-641,1000,1000,1000,352,954,139,-853,-1000,-84,-1000,-977,-186,-625,-715,1000,-352,-491,-664,498,88,-314,-1000,-1000,1000,-11,-141,624,439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{328,1000,1000,-1000,1000,670,-187,31,538,660,-1000,-973,-853,312,-214,326,-481,-488,-1000,-1000,233,-1000,-598,-95,-1000,-1000,229,433,145,45,458,732,-88,1000,-900,552,483,326,-745,1000,-204,-1000,-858,1000,895,450,353,893,360,1000,1000,-1000,-427,737,-58,-493,-673,20,-227,1000,-564,-286,-1000,-734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("VOID|getSize=java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{-774,1000,-1000,-204,-917,-1000,694,980,130,-705,1000,767,-1000,-428,1000,1000,115,1000,1000,1000,-696,1000,69,-1000,719,-877,-361,-984,-696,-264,-297,-1000,-814,112,-1000,-1000,-1000,-823,-758,-697,352,-138,-298,326,-156,1000,-55,-1000,542,-814,158,1000,-747,-1000,-184,453,577,658,-1000,-38,1000,505,719,359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{984,1000,-99,661,197,446,361,751,-42,-493,-539,-454,143,115,477,512,1000,448,-1000,-824,764,307,298,-1000,460,-999,457,-786,-1000,46,812,-89,577,453,-324,-1000,104,1000,869,115,726,280,-374,-1000,-1000,-1000,-846,-301,-710,1000,628,-189,-494,-1000,233,-1000,-766,-945,178,-15,-901,-413,8,178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:NjU1MzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{563,1000,1000,610,207,-368,-148,-794,-180,388,-668,341,597,733,-677,-141,8,723,322,139,-1000,981,-494,669,-699,1000,-357,-576,283,-1000,-83,-289,37,874,249,-1000,-885,-18,-87,-387,-718,-29,76,-586,1000,-771,-71,321,334,497,-448,-598,-1000,-642,153,1000,-435,142,-1000,-1000,-339,-704,15,430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{1000,215,-1000,-552,1000,-805,1000,-410,-124,548,-437,174,86,-212,-1000,-1000,716,-1000,-93,1000,-1000,-1000,47,159,-1000,436,-1000,358,632,-1000,473,1000,-1000,161,1000,-1000,-808,1000,-245,-29,666,123,-238,734,1000,22,-781,-400,-714,543,1000,-1000,1000,1000,485,949,-1000,346,-563,-1000,559,186,201,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{333,-45,688,1000,1000,512,711,92,-36,33,-1000,333,-269,-217,-322,370,337,-783,302,289,-1000,734,503,-289,-693,1000,-363,-613,-795,-707,729,-253,420,-343,215,356,-305,-31,-87,-643,185,201,154,1000,-3,1000,755,-667,-690,-455,653,-147,750,1000,-948,288,-1000,-611,319,72,266,26,709,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:NjU1MzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-635,812,1000,-288,-85,686,444,-375,-68,441,555,-786,-740,-430,-455,354,68,193,1000,-582,-531,357,-286,260,141,-32,618,-1000,-1000,-795,-1000,679,-572,607,-153,682,1000,-760,309,-1000,-1000,201,1000,-141,-584,867,1000,-546,879,-485,-800,616,537,-419,-1000,770,980,-781,18,-795,62,-280,952,472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{689,-369,-105,-466,628,24,-1000,737,-496,808,-207,624,375,372,-542,608,-589,218,-746,-546,-423,-520,-523,479,-299,-1000,664,-309,-1000,712,276,16,513,-1000,-639,-9,-551,-681,-748,-1000,-429,1000,1000,1000,1000,1000,600,-481,468,-688,19,-145,-248,1000,-734,-745,749,-657,-1000,-562,44,-255,215,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:NjU0ODQ=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-329,1000,554,778,486,944,632,138,-198,48,528,-261,332,-108,143,361,-176,13,968,-560,138,775,-466,-405,-388,-522,406,-178,-1000,-1000,-188,-303,210,560,-407,971,912,-890,404,-1000,-757,-201,499,926,-584,1000,885,-71,-714,-300,-302,335,319,344,-1000,1000,-420,-226,640,-559,-6,-176,394,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{1000,16,-549,-15,1000,-252,-856,-104,-1000,436,-585,1000,894,236,-141,1000,-1000,103,-662,-106,128,1000,-24,-22,-742,-793,737,-309,-634,-795,608,-216,1000,-1000,-361,-757,-1000,-1000,-1000,-1000,-987,1000,713,371,-210,730,-150,444,553,-1000,148,-674,-1000,-874,440,-745,738,-79,888,-366,-492,-300,-58,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{1000,175,-248,-257,-240,-368,-643,368,-660,391,-8,874,481,807,-21,642,-939,219,42,-584,-295,-827,-537,-147,-313,-443,516,-1000,-1000,-795,106,-146,969,817,-358,-816,-1000,-687,-748,-1000,-849,836,810,841,-22,851,-35,444,538,-923,-467,-598,-1000,-642,153,-477,1000,142,314,-1000,-236,-642,-376,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{119,223,452,310,1000,819,-804,-268,-766,381,-317,74,615,290,-943,679,508,193,-804,-203,-512,1000,1000,747,-980,-476,194,-639,-546,-1000,161,3,-614,96,-153,153,-243,-353,-264,-643,-118,482,241,218,-3,867,836,-1000,-707,648,-38,50,537,468,-565,617,-794,-1000,633,-375,62,-90,952,68}));
    }
}
