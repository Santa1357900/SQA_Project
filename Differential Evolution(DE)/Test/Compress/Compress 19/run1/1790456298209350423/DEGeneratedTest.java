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
        org.junit.Assert.assertEquals("ARRAY:[B:28:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:23:java.lang.Byte:LTEyOA==:19:java.lang.Byte:MQ==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:MTI3:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:23:java.lang.Byte:LTEyOA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getCentralDirectoryData():byte[]",
            new int[]{898,-506,110,-130,-135,634,26,313,-673,-383,-872,-161,112,962,122,510,-151,440,190,1000,-210,474,-412,541,-657,-1000,-4,-1000,709,-1000,860,250,-936,1000,1000,-290,289,-174,-544,469,-815,79,265,1000,1000,897,-410,-868,-576,210,-595,-1000,610,1000,-470,-571,-951,-323,-619,-777,-179,1000,123,631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getCentralDirectoryData():byte[]",
            new int[]{870,-372,-181,-617,1000,-1000,-345,1000,662,335,-653,-612,-785,-323,-833,-440,432,221,350,95,-146,167,-400,-169,-519,-1000,-939,-400,-1000,-281,366,1000,809,855,-317,257,226,495,-629,854,-483,-1000,-915,-702,-1000,751,1000,535,-647,131,316,36,559,781,302,-1000,18,-627,1000,-322,-70,-284,-817,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getCentralDirectoryLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{1000,1000,-736,-287,-652,-1000,-1000,-115,-457,-1000,629,-383,-655,-999,-1000,-904,996,1000,-286,-145,-596,-167,-907,1000,418,-376,-244,482,-711,422,952,-507,564,-954,464,-734,-899,-676,449,-495,301,794,-209,67,381,190,598,-848,1000,507,71,811,569,-650,-196,-1000,-63,-111,-11,106,-1000,1000,-1000,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getCentralDirectoryLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{985,86,241,-92,364,1000,-659,325,-201,82,473,591,-718,-505,-500,-912,424,-400,612,358,-540,528,-348,995,580,-757,310,-241,-42,468,-1,-102,145,-494,-1000,-506,382,-403,1000,-1000,-885,-71,-67,-400,853,-87,295,-143,-198,-579,-131,-137,448,152,-136,383,-258,758,27,-338,67,1000,-540,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getCentralDirectoryLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{-905,-516,-396,228,-472,-200,637,112,630,-34,-900,172,-413,-642,-370,586,637,-689,650,982,134,-98,-83,-938,-172,378,-396,823,897,-180,835,-801,341,-997,-488,413,522,-574,148,-714,-196,969,441,358,-455,-217,345,777,-13,635,-467,-393,-966,-838,297,471,-701,-701,-826,232,387,-560,-95,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipEightByteInteger", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getCompressedSize():org.apache.commons.compress.archivers.zip.ZipEightByteInteger",
            new int[]{-463,1000,-611,-184,514,-232,-197,54,38,469,-269,-79,495,-208,1000,-1000,839,850,-1000,530,-837,-426,-489,-318,442,230,232,-1000,-327,808,544,-185,437,222,470,658,633,-1000,-86,999,261,-1000,-524,769,382,1000,-537,1000,-775,74,656,-747,173,-99,-236,-676,-1000,-1000,1000,618,-583,641,-671,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipEightByteInteger", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getCompressedSize():org.apache.commons.compress.archivers.zip.ZipEightByteInteger",
            new int[]{-186,246,-652,841,220,182,-253,755,-72,237,-217,759,96,157,-759,-534,-664,577,450,-961,318,-561,-438,336,666,198,-898,46,-607,534,393,697,504,853,386,-237,-670,525,-97,532,668,489,-695,871,856,-350,-770,584,542,-712,175,-336,-898,631,-569,417,-591,-341,-841,168,956,262,-762,64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getDiskStartNumber():org.apache.commons.compress.archivers.zip.ZipLong",
            new int[]{-495,-245,539,494,-207,-173,-835,97,5,-928,422,781,-1000,-541,826,-123,656,-107,-1000,-406,-677,39,55,520,463,-1000,-625,677,-1000,-159,-1000,-1000,-1000,-191,-563,-1000,1000,1000,-1000,-1000,-261,116,476,-1000,-1000,1000,1000,1000,-814,1000,-463,-25,-466,425,405,784,-989,-655,-1000,314,-778,1000,-706,-656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipLong", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getDiskStartNumber():org.apache.commons.compress.archivers.zip.ZipLong",
            new int[]{117,-861,-696,810,-1000,424,665,610,1000,743,-1000,-1000,1000,184,-1000,420,872,-173,-989,46,1000,-757,-1000,297,-652,1000,-850,-1000,354,760,973,1000,1000,-777,-563,831,-1000,-262,-1000,-1000,1000,-1000,476,1000,1000,1000,1000,-1000,442,-95,-1000,-909,1000,-982,146,-995,1000,286,1000,-1000,191,-47,-706,606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getHeaderId():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{-70,973,-89,-576,335,495,1000,113,-511,-48,168,519,978,680,449,925,720,-955,-1,609,-715,351,315,-978,183,140,-153,-960,-1000,115,359,17,667,346,57,-214,366,-1000,-224,-848,-287,923,-589,-1000,-303,-66,57,888,-913,825,1000,-450,-1000,136,1000,55,-561,424,-893,-734,451,-813,721,-653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getHeaderId():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{-136,-447,-776,-96,-117,721,-771,-797,798,-900,-238,105,-681,854,517,-316,-969,582,359,-323,798,-562,-9,-17,-637,-159,-429,718,-747,-268,693,731,62,209,120,-339,728,412,239,-90,500,533,305,213,957,-30,-714,-98,679,799,-691,391,-92,981,564,-50,947,-987,-114,-595,879,116,178,36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("ARRAY:[B:16:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:MTI3:19:java.lang.Byte:MQ==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getLocalFileDataData():byte[]",
            new int[]{-615,34,449,-51,-608,186,161,848,843,-880,65,660,-299,728,-537,-190,-107,651,-462,-425,-931,-756,202,356,241,-290,-406,990,-182,956,530,679,-551,221,-994,796,281,927,-29,-627,-209,320,-985,349,-832,-511,-613,834,-613,-888,-240,575,558,-94,-399,555,-65,-853,-379,270,-955,-117,814,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getLocalFileDataData():byte[]",
            new int[]{556,285,249,-387,-172,-1000,-1000,-525,-187,354,-750,311,-826,-1000,-101,70,-693,-302,137,241,-58,808,1000,688,664,690,1000,102,1000,-388,579,-1000,-1000,268,474,-857,-1000,-1000,-316,65,-1000,5,1000,-927,-584,-362,-776,-1000,-529,622,-575,-829,-369,-781,434,383,-199,-809,-1000,1000,387,248,484,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getLocalFileDataData():byte[]",
            new int[]{758,442,22,595,-573,21,331,-31,918,724,-191,-498,-964,-275,912,511,-967,687,-931,-988,794,944,239,-774,729,960,572,492,389,374,494,428,-242,-515,-642,-95,-641,391,-99,-109,-129,138,194,558,751,-748,510,-840,539,781,-407,-841,974,-403,-817,828,815,-881,-680,-878,-629,855,-34,7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getLocalFileDataData():byte[]",
            new int[]{131,433,-502,383,903,-610,796,-587,-317,-532,715,-785,865,458,-241,549,127,247,874,623,223,-253,682,-133,-406,-967,244,-789,-814,85,529,-692,-256,-893,538,868,-902,-308,213,743,451,-672,-379,-246,517,-332,713,-634,544,-182,-37,-723,-333,-533,-408,739,861,-874,-240,-986,807,-43,76,721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getLocalFileDataLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{323,952,385,414,-1000,-412,250,-927,-547,-1000,404,1000,-351,-147,-423,308,50,-840,675,982,-127,124,133,-486,173,731,206,648,1000,201,222,421,383,-313,-526,-467,-892,-1000,-819,20,687,480,286,-675,383,-125,-214,24,1000,-798,-592,-604,393,-70,918,1000,192,-1000,-566,548,-1000,-742,1000,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getLocalFileDataLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{147,447,-546,-733,-1000,-356,1000,677,1000,-186,-18,-1000,-596,1000,-163,-1000,-119,242,1000,-401,-552,1000,-324,594,-1000,-81,-244,-332,1000,567,-1000,629,1000,339,-1000,1000,-685,413,-1000,226,-82,-882,-1000,-511,-1000,-532,1000,-363,414,104,-717,-1000,-958,1000,-295,-1000,-682,1000,70,313,-1000,1000,352,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipEightByteInteger", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getRelativeHeaderOffset():org.apache.commons.compress.archivers.zip.ZipEightByteInteger",
            new int[]{1000,466,656,-205,-980,-362,-528,708,-557,-373,-1000,-122,1000,-1000,-1000,1000,1000,-595,60,-1000,-444,353,-1000,427,-711,-400,-1000,-810,191,-1000,-1000,-424,-586,-799,1000,261,-1000,390,-467,1000,-14,-407,1000,-1000,1000,1000,-967,-1000,-611,553,-183,267,1000,-1000,-1000,-674,218,-177,156,-600,544,162,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipEightByteInteger", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getRelativeHeaderOffset():org.apache.commons.compress.archivers.zip.ZipEightByteInteger",
            new int[]{682,-567,-806,-238,-106,-334,-595,85,965,204,-781,-921,-449,411,-820,853,805,501,-259,467,-375,639,-192,-516,652,-647,-84,-853,-845,-88,-788,-703,-793,641,311,391,615,700,-135,433,965,765,-832,438,-160,267,528,-793,764,-630,-736,561,767,690,185,-7,-416,934,-803,697,463,74,-725,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipEightByteInteger", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getSize():org.apache.commons.compress.archivers.zip.ZipEightByteInteger",
            new int[]{107,241,1000,-1000,1000,-835,-238,-1000,1000,1000,-367,-527,-392,513,-346,-353,-1000,1000,669,-954,273,-231,-166,-336,-605,563,-318,-248,-773,121,-340,1000,-1000,195,155,-1000,-736,813,1000,758,605,-292,-1000,-159,-144,1000,911,-60,656,-962,-552,-135,-1000,-795,-3,463,1000,-509,242,-419,1000,282,-1000,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipEightByteInteger", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "getSize():org.apache.commons.compress.archivers.zip.ZipEightByteInteger",
            new int[]{-784,888,-961,-67,-455,-456,-78,358,-63,260,-897,-67,969,748,-841,-480,358,-923,376,-220,328,-322,-744,-657,117,1,642,636,-480,-462,436,-715,-400,-435,-460,-673,235,384,-419,-457,-919,-861,-705,161,673,-677,-992,-112,-113,985,-454,794,826,363,-838,900,258,-857,-468,-501,213,897,-239,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "parseFromCentralDirectoryData(byte[],int,int):void",
            new int[]{-522,1000,-45,975,-1000,-1000,-1000,724,-1000,-1000,9,1000,1000,-647,250,-349,889,1000,379,-1000,-465,905,-1000,-1000,-1000,1000,919,-551,1000,1000,714,1000,-64,191,-1000,-1000,-2,-685,-294,387,-923,1000,1000,-666,-620,-319,1000,1000,1000,-322,1000,-331,474,-1000,1000,-642,458,-953,560,-1000,-1000,-112,-815,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "parseFromCentralDirectoryData(byte[],int,int):void",
            new int[]{-643,-711,-81,-541,-604,-1000,-92,133,154,-150,-491,534,616,110,-169,233,172,494,415,791,615,1000,-80,-299,1000,-345,740,1000,-85,1000,875,-169,131,825,-40,964,-392,-407,1000,432,906,-120,132,115,45,817,759,-1000,-380,954,-475,7,606,-163,-550,289,1000,259,904,731,1000,22,-211,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.util.zip.ZipException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "parseFromLocalFileData(byte[],int,int):void",
            new int[]{1000,1000,-1000,1000,783,205,-678,1000,297,-489,1000,-568,-389,246,-245,422,-81,279,770,685,-582,-464,1000,-951,371,186,771,-629,359,1000,-631,-395,-926,-1000,266,1000,-708,501,-543,710,613,-204,91,1000,-522,-277,-8,935,-1000,1000,-514,-646,-140,-1000,-754,-1000,517,956,794,-22,161,989,-566,407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "parseFromLocalFileData(byte[],int,int):void",
            new int[]{-947,420,978,-721,375,-476,746,104,603,-132,-418,-575,-1000,-712,34,-373,-1000,1000,756,334,-234,891,353,2,305,-1000,-1000,39,-565,555,-188,223,-1000,-539,453,-820,202,-1000,845,-541,-394,-1000,214,-44,970,772,-217,1000,1000,-910,-153,-1000,-34,1000,1000,-362,-1000,-1000,-783,268,-510,91,773,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "reparseCentralDirectoryData(boolean,boolean,boolean,boolean):void",
            new int[]{451,-233,579,-1000,1000,949,1000,1000,750,-313,311,203,-598,-1000,-671,-1000,405,-2,463,965,-224,-800,-39,500,594,-284,558,1000,-586,119,-831,107,-1000,231,255,-305,-1000,-79,1000,387,-1000,-42,-1000,-784,746,-264,430,-84,484,1000,1000,-424,-21,481,858,-121,174,1000,1000,953,935,-1000,559,656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "reparseCentralDirectoryData(boolean,boolean,boolean,boolean):void",
            new int[]{203,-147,874,279,715,-76,-966,-599,-717,854,-12,414,872,-289,62,-434,183,-1000,-963,643,321,142,739,509,531,499,-45,-431,178,97,68,-904,-958,-628,900,955,-243,-234,-903,-818,727,810,121,-958,-753,431,-583,-227,682,-321,627,8,577,792,887,49,317,586,-841,622,-400,-489,-661,-483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "setCompressedSize(org.apache.commons.compress.archivers.zip.ZipEightByteInteger):void",
            new int[]{147,-146,-385,-877,-519,92,-595,-657,144,564,214,-938,-102,-670,-10,639,597,312,959,-503,983,300,-474,621,80,-12,-424,193,-437,-1000,-284,598,1000,673,-402,291,323,50,309,-530,404,275,-673,-211,594,506,52,-553,-524,-211,687,544,14,292,208,-939,-172,624,639,547,-406,-331,251,288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "setCompressedSize(org.apache.commons.compress.archivers.zip.ZipEightByteInteger):void",
            new int[]{365,603,-736,25,160,-348,50,1000,346,-254,-595,287,585,-698,852,252,804,-149,24,278,-1000,140,-318,38,-88,756,133,-197,154,70,244,1000,133,-321,145,112,-678,981,-631,107,-257,740,406,-587,559,802,1000,-584,1000,8,351,-123,-1000,208,-390,-878,-495,623,136,662,-81,5,291,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "setDiskStartNumber(org.apache.commons.compress.archivers.zip.ZipLong):void",
            new int[]{400,328,290,343,-1000,-1000,-1000,-385,-626,722,103,-476,1000,-903,257,-331,-838,281,-789,-473,666,617,52,534,-601,-526,-61,1000,-772,-1000,885,556,-1000,186,-301,-246,534,-929,-1000,-853,-484,158,-67,-200,113,-1000,1000,-660,436,-488,172,-854,-1000,374,-1000,-658,88,-1000,-1000,-277,-997,539,-135,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "setDiskStartNumber(org.apache.commons.compress.archivers.zip.ZipLong):void",
            new int[]{765,642,-751,977,-220,-799,72,868,-328,-149,-409,667,-844,675,-17,902,-865,242,-968,706,883,-539,590,-785,-724,628,912,764,906,953,812,-817,-728,-788,-423,-211,-513,-52,-534,343,906,-401,-714,105,-76,-679,-17,-579,829,-105,522,797,29,-904,399,296,249,-856,59,-712,346,-531,220,-954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "setRelativeHeaderOffset(org.apache.commons.compress.archivers.zip.ZipEightByteInteger):void",
            new int[]{-246,385,-331,-915,967,-1000,-277,557,104,708,399,-41,863,1000,-779,1000,-273,-586,517,-496,-1000,208,-878,-1000,-1000,-581,-1000,522,1000,43,767,586,-826,-745,134,1000,1000,845,614,-964,267,32,815,-1000,32,-702,-140,100,-770,618,328,1000,1000,1000,-37,-717,956,189,-436,41,491,-797,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "setRelativeHeaderOffset(org.apache.commons.compress.archivers.zip.ZipEightByteInteger):void",
            new int[]{-572,-690,-522,471,-676,-55,-135,449,-454,1000,-1000,-422,-984,450,247,-192,-363,-1000,-886,-367,-559,453,-109,-1000,-1000,244,-392,-768,-931,-657,758,736,-158,-104,214,869,-1000,-522,1000,-711,689,-568,98,-937,-27,-168,-370,-1000,1000,1000,-916,-517,1000,-369,-62,-389,312,-953,-61,227,-279,-129,1000,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "setSize(org.apache.commons.compress.archivers.zip.ZipEightByteInteger):void",
            new int[]{-137,-533,-635,479,20,448,789,-36,234,-663,-56,1000,1000,-683,964,804,1000,-1000,163,-53,-386,-157,-941,969,-949,348,-1000,-1000,-386,132,958,264,-258,-1000,1000,412,-253,-503,495,945,348,270,921,65,718,1000,-457,-1000,182,247,-1000,1000,-456,447,-201,-1000,92,15,736,1000,-980,1000,-810,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "org.apache.commons.compress.archivers.zip.Zip64ExtendedInformationExtraField", "setSize(org.apache.commons.compress.archivers.zip.ZipEightByteInteger):void",
            new int[]{-800,-81,-831,-894,946,843,-630,161,62,-799,50,-384,-538,1000,-863,-1000,895,-1000,-236,1000,576,1000,92,1000,361,59,324,-1000,981,733,-1000,25,-646,280,-339,292,-1000,-947,1000,387,-670,-617,-1000,361,-1000,560,1000,1000,-638,999,1000,-1000,-7,26,-929,-155,622,1000,-733,-1000,1000,-955,-197,982}));
    }
}
