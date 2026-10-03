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
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "close():void",
            new int[]{-1000,-108,583,683,-517,116,-228,-749,79,-839,-1000,522,428,573,-361,-225,635,-701,715,392,758,-306,-1000,-876,356,-913,521,-736,-1000,96,-713,-293,619,-940,794,-183,824,262,-947,-1000,463,-811,-625,539,-94,-373,-187,269,328,431,267,-406,-1000,-105,-300,851,717,-11,261,795,172,-457,1000,-883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "close():void",
            new int[]{-92,-394,1000,702,-1000,497,533,-120,283,832,26,-1000,-1000,-10,50,-353,-244,1000,-605,-813,-843,-479,330,-59,-255,-735,193,482,-112,1000,-854,783,799,-342,993,91,258,244,236,-90,124,-159,-952,62,-296,459,-110,1000,-342,-18,-343,-5,378,681,-207,-159,-41,1000,399,537,-479,-459,-681,-897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "close():void",
            new int[]{562,307,102,6,-172,-996,-1000,-481,-74,1000,-107,599,923,-203,723,-765,-170,316,-170,-243,311,96,631,-442,-365,-286,633,-1000,-884,-1,-585,-1000,762,1000,60,475,-972,96,332,1000,-611,54,-146,-1000,-416,-2,696,445,-624,-256,-452,-1000,-299,-26,285,-842,252,-55,-1000,875,-355,-1000,1000,608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "close():void",
            new int[]{-284,1000,237,395,-1000,-1000,388,1000,994,1000,-1000,203,-496,642,-952,-303,-29,-1000,403,430,-126,-1000,-12,0,-241,-312,-500,-401,478,-278,-371,762,1000,-461,754,195,-275,510,-1000,767,-288,-138,199,158,84,-564,130,359,509,359,-1000,-1000,-1000,-1000,-456,1000,210,235,-258,-769,173,420,-439,667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-1000,-660,-951,-568,1000,-1000,543,1000,-591,656,-728,-488,1000,-555,-978,1000,-752,-608,240,-428,171,568,283,464,1000,902,190,-455,-1000,1,176,-484,970,645,-303,-393,949,369,391,852,598,1000,415,103,322,-53,-120,-700,-803,662,-500,-655,-907,-1000,-492,1000,-1000,-128,-351,1000,323,201,188,911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{937,661,2,145,154,406,-1000,-691,546,43,44,-156,-612,573,676,-727,134,1000,-1000,560,1000,131,-481,-1000,-349,-827,254,-307,437,-906,849,-862,367,-640,-261,135,-891,-1000,174,-983,73,-1000,-1000,79,1000,1000,-181,4,1000,1000,-96,-536,1000,-603,-529,-131,1000,820,46,-1000,777,-577,-138,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-305,-1000,256,781,-653,474,346,913,-1000,314,434,456,480,83,-806,-75,113,-772,-265,-197,-595,-215,910,1000,508,25,-275,-488,42,863,-701,559,1000,-358,-727,-1000,1000,-733,-397,376,1000,947,1000,-75,-1000,-1000,-375,-227,-508,-1000,-253,821,-970,987,1000,780,-1000,-247,983,1000,-1000,919,-111,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{1000,532,834,373,315,439,-624,270,288,720,1000,-1000,632,-130,1000,-1000,1000,1000,-1000,1000,1000,433,-291,-568,53,-1000,649,-1000,11,356,1000,-128,-729,12,-724,459,-417,329,143,-1000,692,-1000,12,1000,1000,236,49,-897,1000,1000,-1000,-31,1000,-26,465,-104,1000,1000,262,-647,-1000,482,213,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.tar.TarArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-817,900,533,-42,-424,-184,658,64,649,-282,-307,114,947,-997,411,-187,516,-635,-922,-578,834,60,571,-193,-118,-316,72,97,-271,983,869,-625,-759,192,154,299,562,-306,933,206,875,-574,133,-696,618,399,876,-120,-220,909,451,-866,-749,603,-984,-842,237,-567,-690,915,-279,-314,703,607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.tar.TarArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-484,97,-99,-960,358,-765,440,-536,457,203,-268,257,-642,812,-897,170,68,-229,954,285,599,965,-716,163,-768,768,-906,-443,-208,312,113,-421,519,652,710,629,84,78,4,81,-776,-591,240,785,77,-911,-706,-264,-174,-898,807,954,503,-702,904,-451,965,-395,-283,-758,-96,755,979,-813}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.tar.TarArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-1000,1000,829,673,201,-890,1000,-1000,394,-64,-180,1000,1000,-1000,-287,-412,779,-1000,-204,-467,736,1000,-251,-1000,-817,578,-334,-1000,-806,1000,621,-1000,-207,314,-695,-275,1000,-288,464,-2,38,-1000,-370,-1000,893,-104,961,-1000,-1000,335,660,-1000,-143,769,-188,-1000,1000,-136,-1000,895,160,-953,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.tar.TarArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-1000,710,-156,-1000,808,117,1000,-431,-373,773,1000,540,-1000,863,-1000,1000,-158,-371,1000,-765,48,117,474,386,-671,245,0,174,390,702,-678,-511,724,701,-936,1000,100,255,705,1000,-424,-53,-627,-582,-460,695,-1000,416,-1000,-533,375,347,187,-1000,727,-979,-324,-67,-1000,-479,53,243,485,-7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "finish():void",
            new int[]{-673,978,-911,-819,198,1000,860,959,779,-1000,-906,1000,101,1000,463,-608,364,1000,187,-184,-685,65,-473,-107,920,-837,674,193,-587,-242,1000,-1000,-246,-327,-1000,133,440,-157,-21,-400,-1000,-1000,1000,-643,372,739,281,-1000,1000,56,1000,773,-1000,296,245,296,329,-1000,1000,392,1000,-143,643,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "finish():void",
            new int[]{-229,-1000,-667,921,-1000,-1000,214,-122,0,0,-821,-6,0,-841,-694,1000,-144,-239,209,127,352,475,0,-726,282,-219,-1000,-182,209,-775,-203,1000,-664,425,0,103,-42,-845,-1000,638,1000,661,0,-32,-122,260,801,1000,-195,-1000,-458,563,248,-554,250,-504,0,0,-930,-1000,-562,-274,-542,303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "finish():void",
            new int[]{748,-47,927,923,827,-852,-688,1000,181,369,-878,-70,310,-1000,400,1000,-357,-171,-847,469,-256,-1000,1000,-17,-1000,554,1000,122,1000,-291,-790,1000,-198,-574,555,112,-1000,-888,-979,482,809,-636,-548,475,-464,-380,913,1000,-1000,-156,-479,-872,996,69,557,-74,-20,-130,-562,-824,-580,80,-988,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "finish():void",
            new int[]{-1000,1000,-1000,762,-889,-1000,1000,303,671,670,-1000,48,1000,-481,-799,-291,-477,1000,77,-1000,-1000,535,1000,-1000,-368,-1000,-578,378,1000,-148,902,722,752,376,329,83,256,-285,-1000,-909,285,-175,1000,75,372,279,884,949,498,-1000,752,1000,-212,229,61,447,1000,881,341,-544,-198,-717,-32,680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "flush():void",
            new int[]{-1000,-341,665,810,279,-837,-95,-746,-746,-680,-486,-248,-1000,-1000,483,1000,-516,175,-299,949,-612,-1000,567,503,200,297,617,396,-489,-845,-1000,227,-586,312,-1000,1000,798,-594,-331,1000,1000,-1000,780,-886,-594,469,-107,-806,-851,-196,506,-717,-623,33,352,1000,394,-805,-206,-1000,98,-47,218,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "flush():void",
            new int[]{-1000,366,549,-1000,1000,597,-6,635,-410,-1000,-831,-1000,772,-1000,496,1000,-115,196,137,170,-698,476,1000,-417,600,959,1000,1000,-1000,500,-1000,1000,814,-58,111,-491,1000,-1000,-227,155,-955,-933,352,322,345,-932,1000,-84,-712,466,494,-963,1000,1000,-1000,1000,-1000,946,169,-113,-26,995,989,376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "flush():void",
            new int[]{-516,698,919,-680,1000,509,-60,-1000,-1000,-1000,-361,-1000,336,769,1000,1000,-289,222,587,910,-788,-113,50,-998,1000,1000,1000,1000,-265,-915,-1000,1000,-660,-546,31,-636,1000,-1000,-447,409,-369,997,1000,-797,1000,-1000,1000,-647,-1000,-1000,-397,-1000,1000,1000,-797,983,-1000,1000,266,-1000,1000,997,1000,305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "flush():void",
            new int[]{-1000,-680,249,838,1000,1000,-472,-1000,-917,-1000,-1000,-952,-49,-1000,378,1000,-950,181,-842,726,-968,32,796,-269,35,539,1000,642,-1000,346,-1000,881,1000,77,-372,-174,1000,-1000,-721,1000,-1000,-1000,-15,522,-413,-1000,939,460,-483,354,244,-1000,798,1000,-1000,1000,-1000,388,134,-1000,-913,165,883,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getBytesWritten():long",
            new int[]{-609,534,718,928,-883,-732,207,-512,-211,-837,-645,-1000,-1000,1000,-1000,-688,-1000,860,493,648,-768,923,34,-109,827,-767,-841,-15,1000,662,1000,-858,-319,-207,-1000,423,-110,418,-1000,363,1000,-387,1000,422,958,-541,-1000,1000,-537,-1000,-1000,-604,1000,109,-278,209,762,-1000,-1000,-1000,651,-1000,810,-349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getBytesWritten():long",
            new int[]{-268,601,1000,131,-741,6,-319,359,821,416,-135,-161,412,360,-436,793,-375,-147,407,7,947,-108,362,2,387,-377,-634,-300,595,178,-954,-158,1000,-343,124,294,320,-540,-101,673,145,320,50,1000,-103,755,-52,145,-704,653,427,401,586,-196,199,-890,326,655,52,196,858,439,-169,664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getBytesWritten():long",
            new int[]{-436,-116,-1000,355,276,-125,800,184,-422,359,78,982,-931,1000,-1000,52,254,927,465,-892,-598,1000,-893,-560,250,-571,-416,-1000,-89,-144,413,-953,-1000,-49,-593,-766,905,1000,-1000,-174,237,-157,1000,-577,1000,265,35,425,-239,-1000,-1000,533,-58,1000,91,-405,-424,-1000,-909,-1000,707,-414,-27,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getBytesWritten():long",
            new int[]{-625,-1000,712,1000,-740,-1000,923,-723,1000,-1000,-1000,-940,85,852,-815,-925,-503,10,-1000,-821,395,1000,553,1000,-1000,-21,-841,-15,321,1000,93,1000,650,589,-271,169,607,84,1000,1000,-821,1000,156,-811,-1000,-1000,110,1000,836,653,-1000,1000,-1000,-502,-1000,1000,-1000,-653,1000,-621,-1000,842,1000,-930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getCount():int",
            new int[]{-619,-168,-41,20,-745,-928,835,907,-199,-54,817,-360,-876,133,-166,122,471,-257,938,-261,-611,181,-499,83,-47,-124,-1000,-348,257,581,-641,194,753,-1000,-82,1000,181,-180,-380,-238,-274,-371,-336,55,557,262,-234,412,575,-815,-937,476,-661,634,381,428,129,753,742,-705,-421,-94,904,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getCount():int",
            new int[]{-112,-557,-745,-71,-956,-1000,593,948,297,-1000,1000,-885,-357,1000,-552,583,-48,-434,538,248,-318,-625,-975,448,1000,-944,-1000,-388,230,693,166,-370,851,-368,-61,355,-124,234,-268,-30,-783,-906,-1000,-1000,-401,316,-890,1000,508,-698,-1000,1000,-386,502,546,428,933,67,742,-446,-179,-94,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getCount():int",
            new int[]{-319,-1000,-277,103,-165,-1000,319,-1000,532,-1000,1000,-1000,-1000,150,-552,460,360,-425,1000,332,-450,181,-1000,279,-757,-409,155,-436,1000,1000,-565,-159,897,-551,-32,-103,-229,575,-1000,415,-949,-1000,-763,-166,-136,497,-1000,1000,280,-885,-999,-403,-750,778,803,1000,240,653,692,-520,-1000,-96,1000,-646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getCount():int",
            new int[]{-1000,1000,884,307,1000,123,1000,-481,-792,268,-647,-114,-1000,-498,877,-226,1000,-49,1000,-1000,-245,-1000,-99,241,1000,-623,1000,1000,366,-739,-1000,875,-811,-1000,838,716,944,-679,472,-1000,1000,509,856,375,1000,-184,1000,-1000,1000,-903,-1000,30,987,495,59,633,-1000,987,1000,-611,-423,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getRecordSize():int",
            new int[]{677,72,-37,-184,-716,-493,574,1000,912,-639,-843,-1000,-403,-159,-377,-123,-498,-401,-426,166,426,982,333,-48,-318,-130,-552,-108,-844,1000,366,601,679,443,1000,-491,151,702,-443,-316,-323,-272,-811,657,-192,501,128,499,146,37,283,122,581,630,-303,-976,-656,-122,-489,303,-1000,694,825,-124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getRecordSize():int",
            new int[]{677,745,261,419,839,-167,66,-151,912,404,-31,-454,-854,-621,280,216,-498,-4,-600,654,426,-256,-79,16,-180,254,53,534,-1000,1000,-503,321,262,-330,-85,295,669,-994,-581,382,78,-927,1000,-685,-174,-635,-660,-97,146,-74,-1000,-1000,-389,284,391,711,52,185,919,-336,1000,-677,-24,-789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getRecordSize():int",
            new int[]{823,-830,401,-458,-1000,-538,780,448,-522,2,-565,-443,-323,56,27,897,363,-547,106,-37,-772,606,590,420,-217,774,-256,121,-112,845,-524,180,-704,642,243,202,-829,1000,531,-849,-1000,176,282,-373,-641,-571,-743,1000,944,-1000,1000,546,644,-441,-1000,-1000,-733,998,971,1000,-1000,-245,-182,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getRecordSize():int",
            new int[]{-1000,-1000,1000,-1000,-84,-1000,474,1000,-949,929,133,-981,513,-1000,-636,-196,591,-213,-582,-175,184,-931,-271,1000,-98,930,-514,239,-786,1000,490,-1000,-1000,649,-254,1000,951,-1000,294,402,-1000,676,1000,-239,289,-795,-1000,526,-1000,-352,-1000,-1000,-1000,619,1000,-621,29,-911,580,694,1000,125,-618,252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-757,768,84,-592,707,817,967,567,488,-999,-710,388,391,-557,-136,42,328,-645,-106,98,-6,749,735,-615,-870,144,978,572,854,394,602,-696,261,535,-633,-556,127,-350,833,-59,868,-853,428,-848,-842,541,-133,86,585,881,862,946,570,-706,545,-409,383,-255,-653,126,-438,-878,-862,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{780,-743,-869,408,-771,-990,-709,-226,164,-284,-325,-409,-640,-980,652,-33,-327,-275,-943,886,-515,297,-674,860,-468,64,169,-350,-83,-959,839,717,841,104,476,751,163,-685,228,616,279,597,887,363,33,-315,648,565,344,-704,246,-810,-916,782,213,-564,-626,-776,-704,-463,-111,835,162,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{1000,-1000,939,646,-1000,-923,-76,272,-559,1000,252,-975,-938,683,14,-602,-248,98,1000,400,63,-209,1000,1000,1000,-1000,-290,666,-1000,-501,-532,-591,-750,741,1000,10,-828,-1000,-299,120,-708,-916,-192,109,518,-920,-148,-8,616,371,-1000,-301,-589,577,-1000,876,-1000,140,1000,-205,902,-310,700,-562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-978,580,-458,810,-954,489,108,-1000,-51,104,-286,806,1000,-1000,917,207,832,-673,-1000,-571,498,282,-905,-1000,1000,5,-756,-1000,1000,-693,90,1000,-464,-885,-722,1000,1000,1000,-81,743,76,724,611,578,-257,1000,-846,-403,-965,1000,-66,-791,-1000,1000,1000,-205,995,-99,924,-602,291,1000,-166,68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setAddPaxHeadersForNonAsciiNames(boolean):void",
            new int[]{-794,-755,128,-606,-262,-975,-213,-431,-214,762,-415,331,571,-707,-178,218,-548,0,281,22,821,767,340,-68,-895,-132,707,-983,21,-742,563,266,-999,764,889,830,-684,80,-543,-90,842,108,-414,-194,-359,-775,-879,-940,569,-674,294,317,-28,-861,-847,-427,-786,213,-963,-186,237,263,235,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setAddPaxHeadersForNonAsciiNames(boolean):void",
            new int[]{-807,-972,179,-100,-596,226,-442,-507,480,562,-670,-772,161,677,-953,-356,993,78,806,238,-79,-63,-308,-894,-250,16,327,598,-444,-396,-742,-391,-85,428,-127,-396,397,857,-718,258,-773,-607,-801,-342,199,-542,-794,-556,15,771,-977,944,37,-767,371,417,134,-803,879,-162,756,-816,92,301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setAddPaxHeadersForNonAsciiNames(boolean):void",
            new int[]{-1000,-1000,155,615,298,-1000,527,-764,-1000,46,-388,985,308,-1000,1000,-1000,-50,-490,559,1000,32,-455,208,718,1000,476,723,807,1000,1000,1000,178,-20,-602,-80,367,927,-955,1000,464,-117,-175,495,1000,-23,-65,1000,-842,-796,760,1000,-883,-434,-104,-548,-1000,296,-684,-480,661,-541,599,-811,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setAddPaxHeadersForNonAsciiNames(boolean):void",
            new int[]{-995,-734,500,478,-676,-321,-38,-591,-771,353,376,750,793,-54,1000,-747,-362,-690,1000,562,304,-371,-1000,351,1000,988,558,726,777,1000,274,93,897,34,794,-656,906,-1000,-1000,635,-179,-1000,88,737,-634,203,893,-776,-462,-47,1000,-1000,-511,474,-449,-154,1000,-1000,-24,832,25,777,121,856}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setBigNumberMode(int):void",
            new int[]{-361,36,599,-752,352,-345,819,486,-565,-613,333,219,-347,783,-726,-493,956,334,-525,234,-777,311,-187,-631,501,895,-483,-69,531,350,-34,348,-473,-326,351,491,196,211,262,619,801,-967,692,712,729,-446,312,221,-580,-202,-102,121,843,28,770,366,707,-976,790,309,544,182,30,-533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setBigNumberMode(int):void",
            new int[]{143,283,1000,347,-272,384,1000,-850,-1000,117,107,-773,-543,372,-1000,81,325,35,967,-1000,-844,518,-133,-1000,168,1000,-38,677,-964,545,-1000,55,-780,-1000,756,1000,465,-339,-783,387,-625,-545,914,777,1000,-933,518,995,-585,973,230,1000,958,1000,1000,577,1000,208,853,-84,846,273,1000,-482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setBigNumberMode(int):void",
            new int[]{272,-950,-529,-696,-196,477,-379,-409,-157,762,-913,-37,-40,-756,-718,-334,700,157,-310,313,655,-842,-458,-775,-737,545,-103,-139,-935,-632,-393,-561,-687,292,-523,490,880,-860,594,-996,187,-641,-694,-905,-452,306,575,626,749,-769,-411,156,-47,69,380,711,-912,-202,-41,396,-241,959,-987,-333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setBigNumberMode(int):void",
            new int[]{602,440,851,543,213,222,945,541,-616,469,-801,-88,-432,516,675,-442,-319,62,314,-127,-173,-787,-860,-811,621,685,847,235,-199,-945,-284,946,-998,661,631,-414,590,793,667,-953,-727,127,-852,-521,-177,34,-570,-83,721,339,705,472,-228,-616,84,818,-180,850,-79,203,571,672,879,-970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setLongFileMode(int):void",
            new int[]{112,150,758,991,915,-476,840,-75,-327,893,550,-959,852,310,-933,-662,-366,-131,835,75,163,-141,73,284,519,-507,-213,2,-283,767,-831,714,498,792,533,-685,193,-733,-783,305,495,-310,-759,809,-337,-628,-59,585,-649,338,293,204,-973,804,170,-775,-200,-915,-600,370,334,504,171,-516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setLongFileMode(int):void",
            new int[]{188,-47,-220,-942,-382,322,211,-462,602,55,917,-180,-965,746,754,946,802,89,136,420,46,339,59,630,-494,-534,-689,-452,152,-305,155,-142,757,-62,-967,479,-390,941,129,-163,124,912,0,-840,-254,822,880,599,-726,643,795,-990,-532,377,129,-109,-343,851,917,-871,944,-969,822,997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setLongFileMode(int):void",
            new int[]{-210,220,489,-383,765,-851,377,684,787,647,941,-634,-509,-404,-895,-652,830,434,854,-174,-864,-377,-81,-251,819,-343,-349,-599,-649,-736,-151,440,623,219,507,-725,-669,-445,-231,80,543,850,-283,661,336,-259,-929,912,-982,857,331,-372,267,-48,475,-880,-671,179,917,213,-693,-834,99,997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setLongFileMode(int):void",
            new int[]{-1000,-508,-179,501,212,147,-324,-740,-7,-919,-1000,-401,123,746,-1000,954,123,-309,271,430,-1000,-419,-805,23,-494,60,61,-452,648,-851,-215,-68,669,-62,-1000,-1000,712,941,-159,-1000,-377,137,900,-834,-704,822,-366,-1000,-726,-1000,-729,274,1000,-1000,1000,-756,1000,851,-162,-678,-1000,-908,-93,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{609,246,623,165,-1000,-784,-1000,-561,397,-810,253,702,-712,523,-231,-140,316,-260,30,784,-244,-860,-424,-771,-212,-283,1000,1000,-1000,-679,59,395,-364,-201,-326,144,-692,-868,461,25,310,160,-271,1000,-552,-1000,-1000,988,-1000,317,266,-844,-389,-1000,-263,-359,-348,312,-997,305,-563,972,1000,-824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{798,-677,-211,937,664,-809,1000,88,-915,740,814,-1000,-303,-442,513,-1000,191,-620,-511,727,567,409,338,663,569,859,-762,1000,111,1000,-1000,957,-509,288,251,-627,-258,-952,-1000,77,238,-208,-1000,537,680,44,10,843,159,-307,342,854,-529,671,408,646,-303,974,145,561,808,476,932,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{623,-1000,-295,745,-1000,-606,414,-178,192,-973,-1000,115,1,1000,-268,673,162,-156,695,476,-21,-1000,-610,-1000,9,415,194,77,-270,1000,685,836,-279,-201,845,-433,-87,-1000,570,-525,807,-140,-967,1000,-1000,-1000,-1000,114,-1000,311,-299,-137,-157,-64,79,714,208,487,-189,-496,-563,799,685,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-747,-8,-961,535,-183,-248,336,989,-931,341,-357,477,-345,21,-325,254,702,990,568,-460,879,-825,-583,362,-233,-391,48,-282,621,-235,515,761,2,-445,664,-337,-472,814,-780,611,34,-945,-651,-10,810,43,-584,242,252,-722,-271,87,-715,-85,492,864,-812,-219,861,575,-910,-776,70,-92}));
    }
}
