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
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "createArchiveInputStream(java.io.InputStream):org.apache.commons.compress.archivers.ArchiveInputStream",
            new int[]{248,-664,-368,-94,-343,697,-723,-92,-796,-79,739,-313,-559,-390,626,-729,869,-563,853,413,-156,588,955,-259,-673,-86,-218,-80,940,-753,-94,625,282,-58,363,851,-782,114,-887,-323,935,274,-465,-16,-9,120,-384,818,248,-946,86,-902,652,-278,716,210,134,-716,207,-380,557,-516,666,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "createArchiveInputStream(java.io.InputStream):org.apache.commons.compress.archivers.ArchiveInputStream",
            new int[]{614,-381,554,-241,-164,489,128,768,473,96,952,-619,-92,-588,-202,-10,-539,901,-576,321,-1000,-373,123,765,574,773,693,381,456,183,-482,-983,-541,142,-156,-269,118,-985,-3,300,477,619,-716,943,-423,-779,-361,-359,-535,911,-320,380,584,-217,-213,-466,-270,-201,39,-827,398,561,-579,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "createArchiveInputStream(java.lang.String,java.io.InputStream):org.apache.commons.compress.archivers.ArchiveInputStream",
            new int[]{789,-228,-992,-651,-733,131,-67,-266,62,-921,480,586,-638,969,355,69,-23,84,558,-105,-392,917,-267,-70,920,-915,-289,880,-989,-885,-530,96,-206,791,-698,-84,592,-472,427,-362,-196,318,-506,-783,-110,-774,475,-921,-884,-930,287,70,515,-992,-833,489,-649,-955,284,-610,968,148,-623,916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "createArchiveInputStream(java.lang.String,java.io.InputStream):org.apache.commons.compress.archivers.ArchiveInputStream",
            new int[]{-472,331,-1000,-559,-1000,-252,-34,552,120,-393,-357,-355,-800,-536,1000,-457,199,-950,-129,-624,-1000,895,-97,-897,-1000,-389,545,608,184,-868,-633,-602,1000,550,211,608,-1000,57,418,700,127,-721,-1000,-677,662,-531,-528,201,-852,-583,-877,565,-999,-1000,813,-953,-530,-1000,-524,1000,1000,-1000,-1000,-537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "createArchiveOutputStream(java.lang.String,java.io.OutputStream):org.apache.commons.compress.archivers.ArchiveOutputStream",
            new int[]{27,708,69,-52,-362,-85,-809,-714,-536,-716,-831,-257,672,-22,17,-988,-805,25,-220,-142,-726,-424,45,-323,-625,131,899,346,800,999,-836,-172,769,-317,259,703,-224,-639,122,576,-361,185,-798,-883,-201,-265,-215,412,-837,-370,617,-774,-944,52,-879,-162,-299,-293,-601,133,49,-345,-389,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "createArchiveOutputStream(java.lang.String,java.io.OutputStream):org.apache.commons.compress.archivers.ArchiveOutputStream",
            new int[]{191,-817,638,16,1000,-393,-1000,-224,-117,752,-264,763,-308,-321,-1000,837,472,1000,416,1000,852,-776,1000,-574,583,980,245,618,1000,-347,306,1000,-400,-906,723,-1000,-352,201,-941,-150,-927,-272,7,-481,-774,121,559,-15,-1000,-32,400,1000,-197,-1000,1000,-1000,988,-1000,1000,-966,-482,20,-68,429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "getEntryEncoding():java.lang.String",
            new int[]{-24,818,253,331,562,104,-183,-263,-565,829,556,-821,785,-606,531,-226,-51,-656,-920,900,43,-376,-478,96,846,208,-205,520,-125,-193,975,50,-520,-322,-446,782,284,15,861,380,294,879,67,-496,636,162,-152,357,-993,709,-27,308,656,-622,-172,-519,4,576,200,233,-155,-418,-463,-490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.String:Smg5a1hSejRQRy16Wk55U05pRm5ycUg=", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "getEntryEncoding():java.lang.String",
            new int[]{-591,717,-866,90,-762,-677,45,-835,932,304,-83,621,248,-706,264,468,-292,-178,-223,759,34,-88,830,941,183,-829,595,310,-170,-801,-879,325,-972,537,-332,-804,-713,767,-932,855,291,876,-19,174,-876,551,515,165,-8,-332,-118,737,178,930,-486,575,221,-678,-635,-277,-807,-123,-984,507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID|getEntryEncoding=NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "setEntryEncoding(java.lang.String):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "setEntryEncoding(java.lang.String):void",
            new int[]{359,113,-5,-727,-848,-506,-904,-838,-605,-415,-93,-318,124,968,381,-48,-764,674,-54,262,544,-254,538,485,-611,-839,-798,-886,-251,-543,-847,-160,32,-420,-589,-932,-487,-267,-52,270,719,-745,-332,-349,974,-18,463,259,798,-93,121,366,-135,-517,-117,709,849,-548,-887,32,-382,672,-284,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "available():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "available():int",
            new int[]{434,-479,93,130,34,-651,-844,179,-323,-148,-750,-703,470,-771,925,-955,-821,-513,310,363,83,-669,-703,339,366,-112,-540,744,286,-586,-585,-139,494,-791,105,-832,-208,-519,92,-155,-579,648,-620,707,518,675,-522,-798,-241,410,-502,394,427,640,914,-519,137,580,-734,-672,266,-146,-409,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "available():int",
            new int[]{526,-822,118,195,800,-192,-806,-374,144,117,-262,395,-158,-915,-707,395,473,-740,364,660,-261,-378,477,-330,380,-490,-251,-399,-650,-831,595,496,557,617,995,353,407,975,359,526,168,-792,-588,497,-266,302,793,85,-251,287,-928,441,-735,-742,-258,-158,92,-464,433,165,851,493,52,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "close():void",
            new int[]{622,181,82,803,-395,-381,356,925,137,586,-973,-827,877,-756,-30,-67,-5,-140,-534,44,-768,401,46,776,-52,552,-877,721,-222,-951,-446,310,103,-418,-812,-212,-636,852,773,386,-523,396,167,-853,72,-307,-314,-570,94,-230,656,-547,414,14,756,398,558,242,-441,-419,-634,-794,-138,-994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "close():void",
            new int[]{-513,-354,816,-815,945,786,-308,546,-251,-273,156,-9,-549,-428,372,162,901,322,-72,-105,74,599,631,-72,-262,57,543,486,8,-13,-718,589,-614,-927,-428,-913,-264,504,-685,303,-161,-271,25,916,672,-969,-614,-752,-649,-232,-122,-526,-208,-323,-4,-561,608,927,-653,-247,446,752,141,-394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "getNextCPIOEntry():org.apache.commons.compress.archivers.cpio.CpioArchiveEntry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "getNextCPIOEntry():org.apache.commons.compress.archivers.cpio.CpioArchiveEntry",
            new int[]{103,-707,-85,784,891,418,719,984,272,583,-471,12,508,445,847,-668,-625,562,60,380,883,769,-818,544,887,-458,858,345,-394,33,341,710,312,-160,-711,748,944,723,658,-256,-737,449,719,263,314,-216,254,149,-598,-817,403,-2,589,213,-726,-742,-201,-72,-569,473,-990,479,-290,-605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "getNextCPIOEntry():org.apache.commons.compress.archivers.cpio.CpioArchiveEntry",
            new int[]{-450,686,-138,-59,956,-431,-354,111,194,-572,538,-269,-748,728,227,-692,668,-227,817,-40,-248,160,-727,86,395,-569,307,321,-781,-902,-354,624,-982,-429,745,-93,-997,-352,441,871,153,-743,778,902,-832,-108,-685,-202,-44,-202,566,870,-625,396,922,-471,28,632,399,858,-675,999,859,852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{92,-427,-760,47,-621,-518,-536,-383,-806,-302,-617,-129,-434,349,298,934,-749,410,259,-672,-600,-369,-422,148,-227,-114,-38,908,888,-233,-502,460,-482,-97,-344,243,-717,956,-281,745,739,-440,615,-511,321,-451,-23,151,787,749,-543,-551,-615,-675,-204,-638,-139,244,988,911,664,-275,-53,254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{671,-46,968,-711,712,-48,-714,470,335,-200,-333,-47,-188,-236,-583,404,77,553,888,-68,-381,-2,-723,880,94,608,-807,-932,-245,269,-540,904,851,-935,522,557,435,616,-905,48,-277,685,243,-444,-929,856,-62,-509,-67,-965,-667,-38,-256,-761,-509,-843,573,681,97,-163,-399,517,-187,676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "matches(byte[],int):boolean",
            new int[]{-922,311,178,658,-94,230,-813,559,-872,-469,-986,951,584,495,587,-885,707,149,555,133,592,107,817,23,-435,-646,-486,98,-672,866,-470,568,-873,-742,959,-3,272,-20,-98,-140,817,-935,-513,150,254,-152,54,772,-347,-313,77,254,842,-709,-418,244,955,-62,-415,-833,-65,-376,368,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "matches(byte[],int):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "read(byte[],int,int):int",
            new int[]{893,-903,563,-531,820,-286,627,-892,63,-678,-479,-504,-773,-431,-523,-546,48,-888,410,-569,-639,-817,-772,-416,190,720,-868,921,950,951,-301,601,523,136,908,-765,-466,230,-432,-17,548,644,580,565,102,-19,372,-73,-516,675,-530,-790,-982,-234,-316,-38,226,-280,645,-971,-302,154,557,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "read(byte[],int,int):int",
            new int[]{-724,802,-976,660,308,299,-286,356,-159,169,-427,29,646,372,-424,-75,487,182,287,-420,-148,748,818,398,14,-497,579,922,-930,538,270,-887,430,-199,991,638,762,-919,-404,170,803,141,423,-1,623,82,416,873,975,-985,299,-70,141,-497,967,-186,-645,-164,-495,-368,-691,216,-587,268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "read(byte[],int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "read(byte[],int,int):int",
            new int[]{-473,149,464,-224,-562,982,-599,-964,896,-621,-801,-318,561,-220,-37,-563,-908,381,728,399,403,32,370,-416,377,596,-348,95,394,761,-965,-382,755,-901,274,685,697,222,75,74,-961,320,-879,-909,732,-958,-380,23,-424,-260,-797,-938,174,-588,482,170,882,-661,-142,-33,-108,-629,280,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "read(byte[],int,int):int",
            new int[]{-795,-864,957,-204,-740,965,736,-336,878,653,-837,-367,74,-17,297,-33,-810,603,321,-466,-782,885,-26,-810,-376,941,553,-504,54,87,-364,283,-959,-188,-919,245,-570,399,-878,750,614,752,379,-634,-145,678,476,-809,-997,924,-359,56,721,588,98,862,-21,170,-601,908,20,-481,-82,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "skip(long):long",
            new int[]{579,505,-391,-856,603,344,-667,-262,-437,595,-599,632,934,443,-351,932,870,913,-947,-474,-18,-655,869,-630,200,654,-320,589,-466,950,-9,327,345,-384,-612,-382,-847,496,-875,851,-108,-480,-445,-102,88,777,776,388,447,-184,513,223,994,-203,-615,-12,-911,996,450,-372,403,-883,-823,-534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "skip(long):long",
            new int[]{-773,912,-138,453,-596,642,909,405,-422,106,358,-119,467,122,580,-616,-550,663,-838,572,853,115,-339,309,-972,-402,-894,-457,-427,-333,945,-761,-151,279,456,905,-683,480,551,-422,-816,-107,-620,610,-681,-188,-822,26,494,-530,377,-175,-407,871,178,166,679,-529,502,78,-512,-33,501,-927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "skip(long):long",
            new int[]{-315,-26,180,661,-360,-1000,789,-518,995,675,-808,74,297,111,-463,-1000,666,473,475,591,-108,-249,-706,1000,114,-251,858,786,-444,-656,-313,-8,755,42,1000,-511,313,-560,564,814,97,361,-610,1000,575,-62,-141,494,273,215,-1000,1000,1000,-75,-1000,-1000,-1000,790,-470,-812,-403,501,133,864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream", "skip(long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "close():void",
            new int[]{-372,-294,-316,459,-28,373,359,-319,-217,744,-81,-498,-191,383,-91,35,-304,-887,94,-587,-640,-147,-431,-244,149,830,-239,-983,593,681,473,-556,-767,456,-979,-718,-438,346,-800,550,-515,69,-674,268,-152,289,943,-683,-413,167,-119,311,810,-758,-660,-727,-241,146,399,-281,999,406,951,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-396,31,-500,360,975,-494,-692,-737,404,-26,-57,834,-114,-276,800,-527,110,-963,-920,-570,35,977,-150,-946,886,-185,962,-646,227,-561,344,-432,736,-865,-125,-611,-855,-545,-481,-276,-540,543,-300,-591,987,-758,969,202,-20,-400,-21,535,-210,-36,-531,-4,268,753,-479,-655,893,230,-290,955}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.cpio.CpioArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-1000,1000,199,-614,1000,-898,-246,800,-1000,-911,198,-1000,-772,-27,-1000,-305,542,-862,400,-713,111,-1000,729,1000,1000,-1000,886,-540,-769,-1000,-316,-845,1000,1000,-840,-163,-282,-798,-928,1000,969,-1000,157,525,-422,167,227,-831,417,-1000,-1000,1000,1000,-886,510,985,-525,-171,-765,-655,1000,-439,740,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{516,476,229,837,-328,-317,-230,477,676,-667,-611,-689,-448,747,-346,701,-397,-880,-908,826,161,-732,68,-782,-85,-912,610,-958,728,-102,-115,270,-577,462,836,75,-377,658,-609,474,-601,-464,-373,-458,-779,822,254,-632,504,767,-96,-686,-87,729,822,-864,487,-156,773,-964,-337,-291,-744,822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{185,573,-995,-731,732,1000,502,-810,-486,1000,1000,656,1000,-575,1000,-668,529,-135,-114,1000,404,-1000,-983,1000,-838,-282,-1000,874,891,752,467,56,1000,-608,-913,691,1000,18,953,689,666,-451,3,-515,357,-103,-381,-492,228,-741,-1000,1000,1000,37,-324,1000,-657,-1000,-770,-612,1000,1000,77,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "finish():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "finish():void",
            new int[]{-442,621,-375,-947,-564,-198,-441,386,42,555,-666,899,-167,190,-897,844,-99,250,102,-66,-424,72,441,896,-524,425,572,-420,-307,371,-931,-850,-681,-84,331,-59,-604,285,-887,-486,563,744,338,-849,-831,-254,585,-400,-602,899,-165,179,121,768,582,-406,-425,-193,280,-283,742,-87,207,-563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "finish():void",
            new int[]{1000,-392,-697,169,116,-980,1000,-1000,763,21,309,871,-151,-345,372,547,-861,46,133,-593,-796,355,-394,184,862,405,440,-812,-512,-10,495,13,-920,-241,642,153,54,55,-975,1000,-155,-45,663,1000,-358,601,-587,110,-1000,127,-717,-896,90,-496,-157,-496,-1000,435,-1000,117,218,-774,-1000,510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{544,371,415,-437,470,-597,203,390,906,283,-919,-391,258,-4,40,271,302,409,991,495,-298,0,-900,-45,-857,-279,44,-522,800,-214,79,-537,257,655,536,888,-515,-407,123,-778,-952,-243,238,773,302,-3,401,675,36,283,850,41,732,-6,-616,612,-571,182,-402,689,-964,369,-184,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-1000,823,374,37,195,-804,448,-582,-380,-188,-580,900,410,-491,-560,111,224,152,151,902,509,-1000,-509,-16,-1000,984,-583,1000,-574,1000,-218,456,868,-307,411,-416,-501,590,-520,921,-774,-1000,107,-576,461,369,-1000,561,1000,-1000,-768,193,-43,629,773,-559,-480,-20,871,-697,-231,-987,-1000,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-104,-90,139,243,56,61,-360,448,-166,-544,49,288,281,553,1000,1000,976,-1000,106,-830,-281,281,439,-83,-1000,665,-15,614,-237,53,1,-228,918,-19,58,-1000,79,350,670,85,-931,-131,-314,-1000,-657,-530,-15,647,202,-96,-269,-135,-580,494,-1000,-127,-138,-1000,601,432,199,-1000,-30,-213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{42,438,58,-623,-986,858,-418,279,-242,761,-605,-1000,378,795,724,850,-1000,468,-431,-366,-395,35,-1000,-258,156,-754,277,-447,-109,948,-747,679,-201,399,-1000,682,918,1000,-119,788,643,-1000,-1000,1000,-722,-206,429,315,1000,-1000,234,-304,860,-570,1000,-24,178,-1000,-542,-18,-177,346,13,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{610,881,-997,551,930,253,-829,112,22,761,-235,309,-611,442,-782,733,791,-575,-94,955,-395,-603,-352,-808,156,-441,245,-158,-800,834,737,-589,736,-766,386,-995,-499,-26,-228,788,-395,182,-137,-424,-934,-151,429,-899,242,65,748,-627,581,507,-806,-951,-529,660,12,734,-487,-699,-660,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{103,-730,-881,-266,1,397,-354,674,-141,491,791,-141,-407,-521,-307,-632,325,-984,451,928,499,547,953,930,219,688,-914,642,-61,403,-510,-668,-328,611,318,-284,21,516,-129,-675,466,237,-566,213,-357,103,-856,721,569,-631,323,983,-796,-956,311,258,-888,-969,601,-716,500,821,-218,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{47,425,372,160,-597,961,-538,-879,0,384,891,-156,-826,652,85,-889,383,768,-904,-347,348,-305,351,-805,-451,376,855,629,547,163,-715,868,597,-921,790,-409,164,-956,655,-703,-886,434,467,894,-417,-840,941,354,846,-162,132,-69,838,542,403,-155,-563,168,245,-571,84,453,-232,276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.dump.DumpArchiveInputStream", "org.apache.commons.compress.archivers.dump.DumpArchiveInputStream", "matches(byte[],int):boolean",
            new int[]{67,368,650,252,-1000,413,-966,744,-211,-750,-197,-877,489,-762,-1000,-149,684,685,-490,1000,111,1000,186,198,844,-436,-619,176,1000,-924,353,-117,706,-705,-1000,-146,-855,896,556,-307,945,1000,1000,-647,-579,386,-417,-183,-695,-977,747,418,37,-902,-1000,-1000,-382,-838,-1000,-907,509,-183,1000,581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.dump.DumpArchiveInputStream", "org.apache.commons.compress.archivers.dump.DumpArchiveInputStream", "matches(byte[],int):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "available():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "available():int",
            new int[]{-909,979,-424,80,161,48,651,-629,-506,713,527,142,-597,2,-709,-316,-953,806,-284,483,-922,869,-983,-860,794,76,-440,992,-433,784,-929,125,524,-374,-296,-937,480,-716,-973,372,-93,-976,444,-349,400,-537,-135,-66,523,1,-452,-600,185,603,937,-512,759,-774,73,967,-932,89,-336,-633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "available():int",
            new int[]{812,820,-348,-654,-50,750,-303,-379,-475,913,-548,474,95,-530,349,-249,177,-656,239,-770,899,-176,517,-776,14,926,-812,839,-974,-39,-138,-238,385,474,531,-890,685,749,649,-210,660,415,488,-963,-625,-677,-932,-579,478,853,46,-609,628,55,859,699,292,-357,-838,-533,978,-260,123,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "available():int",
            new int[]{-319,362,1000,543,-1000,1000,293,1000,325,1000,805,1000,6,336,1000,788,1000,46,154,900,-1000,-421,-1000,573,-944,-895,712,953,-1000,1000,-1000,-48,-1000,-976,-787,203,54,888,-423,811,720,994,479,-1000,346,-691,1000,365,-976,-715,-1000,677,284,-175,452,1000,15,-655,694,-890,-1000,-551,523,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "canReadEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "canReadEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{-103,271,160,-739,512,136,-523,138,109,443,426,-751,-219,386,-509,410,759,799,724,559,494,773,625,-177,838,215,-594,-866,-373,708,-151,844,790,921,-968,-751,925,229,345,-136,249,-768,-578,848,798,413,-119,-944,800,302,-896,870,-748,215,656,170,929,-140,434,596,-750,956,121,588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "canReadEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{357,-448,251,916,319,-343,962,372,-781,556,-149,655,-49,-793,-997,772,-911,860,-167,642,-5,-396,-440,-404,65,-642,138,171,-29,701,408,762,578,861,299,44,67,-399,83,128,-301,727,-149,-141,-318,-461,681,844,616,655,-725,387,-881,318,252,-225,407,577,720,-882,-158,-486,292,-693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "canReadEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{-172,1000,1000,-1000,667,1000,1000,1000,1000,-239,545,-1000,-1000,-1000,-950,1000,-937,-465,1000,1000,311,1000,372,1000,330,-939,-149,-428,320,-660,-900,426,340,517,67,-148,1000,19,-511,131,-871,-1000,355,991,-141,-1000,-292,63,888,-1000,-1000,-233,-97,818,-495,309,1000,1000,1000,770,-107,975,-537,-624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "close():void",
            new int[]{933,-521,-505,-838,33,559,349,518,-515,235,-236,-69,-76,-915,761,-660,696,959,437,-1,100,98,146,146,-753,714,-622,-908,374,439,768,904,-276,731,328,95,-364,736,-51,-111,380,390,496,499,-575,-401,-582,615,365,-761,676,-165,-520,396,-978,-891,-916,782,-819,451,-803,-399,-678,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "close():void",
            new int[]{-389,-806,-217,-851,320,-634,601,411,841,334,245,-551,-565,-667,618,-912,873,-572,349,27,818,-439,149,-675,981,-318,712,-865,280,-226,984,225,483,-788,-744,88,-647,346,505,-434,203,-547,692,820,-64,-711,293,-792,148,339,-731,173,-328,572,-151,-396,67,10,92,278,-22,760,-48,9}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "close():void",
            new int[]{-496,-532,672,-1000,774,-475,1000,397,968,-44,505,-584,-382,-1000,1000,-1000,546,587,482,801,1000,-626,-222,-1000,1000,-408,-361,-1000,736,-885,1000,-238,-121,313,-512,117,-1000,360,862,-85,-52,-526,1000,886,994,-929,8,876,-12,-129,-1000,220,-402,381,-443,-758,-512,-184,761,-328,-246,1000,23,-635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getCurrentEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getCurrentEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{-832,55,-76,252,-691,-9,243,555,698,-901,-872,-88,339,-107,890,-986,-141,770,-281,-772,-919,453,-161,359,271,-300,155,-421,-965,-190,-865,-522,-241,-633,673,-551,63,-315,228,847,-192,-586,680,715,-136,-71,-985,-738,75,-735,231,711,610,-387,-373,-319,291,-323,-937,-162,-681,570,685,168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getCurrentEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{367,1000,-1000,1000,-880,-624,1000,856,-649,-511,602,-815,438,-1000,-1000,-1000,-884,-636,-197,-108,-619,-401,-391,696,49,527,780,-985,-309,611,-644,713,105,53,-135,-615,-464,732,-165,-1000,852,-886,-1000,-813,1000,1000,-311,-291,-851,-589,767,204,-28,-352,-557,-897,-1000,-1000,-588,-69,-272,24,-459,117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getCurrentEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{789,-136,-881,707,-281,-998,611,465,26,366,791,-24,-17,-485,-41,-934,-527,80,-689,977,13,11,-591,1000,-776,-308,742,-170,-1000,278,-472,581,1000,-21,31,-425,-100,326,988,-281,-311,649,-561,-632,59,784,-147,88,-1000,-776,47,159,-1000,-481,298,172,6,-245,-427,-473,807,-614,207,-501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-305,-435,-795,288,608,-509,989,704,-788,-266,299,722,640,-28,861,-608,509,617,82,-375,66,-692,185,825,-277,-311,-698,-370,624,-861,257,-161,-656,-144,-969,445,354,90,168,42,431,-793,787,847,875,-277,227,47,-931,-485,916,265,707,-30,-482,572,-179,-960,107,635,-336,-165,722,-633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-392,-911,-409,-407,-900,-899,509,-881,-528,-530,554,-143,-993,-53,355,226,604,-42,482,383,-173,547,-256,361,-843,897,590,-294,258,-233,536,361,-440,-43,792,409,-36,267,547,-354,-945,318,-262,884,-147,-922,566,-594,-155,-33,476,-946,-214,14,895,-963,-384,-314,636,767,717,-248,514,771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{479,208,-686,947,812,640,-1,565,-70,851,553,585,-496,-591,-293,-198,-510,718,-782,-348,-914,-861,215,-268,305,847,-391,-271,-420,-210,70,589,249,-37,-709,-408,278,489,-421,-635,-802,971,-858,-189,-181,-13,-90,408,42,-302,-838,454,-178,73,-169,387,185,-559,924,564,-921,-225,-788,587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{640,-1000,-32,-31,-200,-616,254,653,668,-423,52,95,348,-438,660,71,-100,939,224,781,125,16,-502,-14,-618,72,905,685,-814,-691,189,-238,-219,-257,-730,869,402,-331,-853,-844,-484,219,-915,-575,988,-992,758,775,805,913,-528,-128,-229,-410,-831,-401,408,-466,-486,39,-795,-825,38,504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextTarEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{532,423,719,944,-13,463,-97,366,-762,-937,477,-855,-745,-275,-973,206,-410,69,-15,735,-799,515,651,-552,-463,-192,-871,721,-731,106,-278,-552,500,-575,-699,-564,293,502,-309,115,910,356,-786,-609,-710,186,-802,593,990,627,464,627,38,840,600,685,-2,-567,-829,179,894,790,924,890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextTarEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextTarEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{220,715,26,30,24,-514,175,-956,58,-277,599,-529,-265,208,397,393,-266,-625,-768,-723,-469,-360,111,874,-632,-902,406,-393,319,-469,-94,-419,538,790,-350,-739,-242,843,860,-166,-295,36,-647,-632,-805,-554,-526,929,-619,598,846,50,872,-721,962,-866,245,-311,-955,-290,875,748,-538,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextTarEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{-755,340,775,201,982,923,-276,145,917,-805,430,632,-469,-388,-447,-53,297,-367,-34,-164,221,-729,257,768,195,199,-859,-950,185,432,424,494,-358,765,115,-696,590,-672,-165,-210,-618,927,-240,588,-775,113,-16,184,-694,-626,-578,690,-430,253,150,75,-982,-266,432,-651,-209,826,-431,551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextTarEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{821,140,392,598,-72,-784,-3,567,878,349,-996,844,222,-783,-965,-477,929,-373,358,-55,91,-507,324,362,12,-27,551,615,720,-614,550,-907,-613,-367,-540,-812,-831,-948,87,-578,493,-71,568,-504,-3,-818,584,-398,-20,-829,-661,534,490,-8,-779,-665,993,-822,493,-430,930,-730,198,610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getRecordSize():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getRecordSize():int",
            new int[]{192,409,567,580,-196,-668,472,223,23,-470,-815,597,-670,929,28,-630,-111,759,-556,-933,416,98,366,-890,-593,308,352,-809,-854,563,-581,-832,-830,-44,454,831,-52,251,-203,414,-586,259,-883,-510,679,2,82,57,775,396,476,-994,-404,-16,381,-57,-664,421,-126,372,-326,863,-761,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getRecordSize():int",
            new int[]{553,-112,-819,-757,-659,-108,834,-800,649,83,-398,330,-639,-457,239,-525,-322,-269,204,-773,75,-729,980,460,925,-165,875,55,523,121,550,-195,-500,-759,466,675,-306,-619,137,98,-570,536,659,754,850,-709,296,-538,454,-803,101,174,-661,74,516,-125,734,-228,674,188,-598,345,-229,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getRecordSize():int",
            new int[]{849,-956,-375,67,579,318,-30,666,-507,475,-756,-511,-822,-588,70,-761,-701,-925,-679,-53,-290,877,184,869,142,-223,-166,-881,-853,-498,916,-504,230,-366,959,93,-824,-118,545,-184,439,-240,650,701,208,-894,-146,83,-777,395,-631,-936,-243,-350,-276,500,337,888,-183,-238,673,-481,589,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "mark(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "mark(int):void",
            new int[]{-380,-317,149,-209,670,345,-580,-700,-8,-745,-417,-57,778,-607,-645,-778,-761,912,-254,-374,881,-670,-69,-802,762,667,-202,643,63,485,290,163,736,695,79,-721,-18,-116,132,-826,792,-89,295,-700,-62,-416,120,362,424,-672,-110,167,-371,918,-985,-716,-817,-386,-197,-23,-75,309,985,457}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "mark(int):void",
            new int[]{-38,938,908,-729,118,-447,-545,-185,183,-939,173,769,365,-631,-368,787,114,241,-253,286,358,744,595,-757,860,652,163,-578,750,-21,827,348,-323,-343,-196,545,998,328,-191,-310,961,-824,734,-332,926,797,-731,799,-668,-793,-906,-79,343,247,-481,-546,876,916,-749,-879,-937,424,354,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "mark(int):void",
            new int[]{676,1000,1,373,1000,544,204,860,463,387,403,-1000,277,-610,-252,12,114,206,759,-908,803,-842,595,-838,-577,603,938,1000,-230,-1000,827,45,-90,-997,-189,-138,-317,-588,892,-135,961,17,-1000,-332,923,-594,-223,418,-1000,-473,-48,602,-341,160,-1000,-208,834,-1000,188,76,720,-26,-907,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "markSupported():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "markSupported():boolean",
            new int[]{5,-101,351,902,-314,470,202,570,429,621,867,477,278,513,-643,142,-730,708,-945,-954,393,101,-720,-498,-724,-58,-984,-37,508,193,-490,388,-847,442,459,973,-64,-567,852,7,-179,915,175,-72,252,932,655,740,-770,970,496,880,529,420,-355,-659,-110,720,-478,-588,-173,231,372,-267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "markSupported():boolean",
            new int[]{-46,-388,-900,-196,192,-813,801,-676,-772,-204,-927,-931,-326,-290,805,-579,-186,707,498,-898,668,-244,-873,609,-815,244,716,291,2,740,347,974,-143,333,365,-286,-335,361,867,725,-491,-983,226,-166,-636,-243,-659,-90,-781,668,-380,632,478,84,-766,687,-21,-450,-860,39,803,997,-455,252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "markSupported():boolean",
            new int[]{-732,718,312,632,188,-549,-704,796,396,-948,724,387,502,-866,925,-702,-520,-496,-20,-793,-121,-363,58,284,735,-200,-631,-538,259,466,-938,808,12,-355,-535,-805,-798,618,-613,-51,-29,-858,-166,-542,127,-298,-511,790,610,591,-452,340,-816,-282,121,204,-662,882,-723,545,181,-2,-947,566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "matches(byte[],int):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "read(byte[],int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "read(byte[],int,int):int",
            new int[]{220,613,-473,-577,-367,931,231,-196,-213,-475,179,29,577,-157,625,-245,-634,-342,924,-917,417,305,-130,48,717,253,442,-238,-408,897,-772,-217,185,-100,-363,-817,437,271,-237,-13,-431,-564,-779,-798,-800,46,637,-518,253,944,590,614,-439,837,-246,503,-297,531,742,-932,10,313,747,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "read(byte[],int,int):int",
            new int[]{-248,-284,617,-164,648,-31,-152,-153,959,616,-899,502,-960,-169,825,897,831,660,-530,521,42,-493,83,-393,933,49,-247,-175,-723,-600,843,366,814,-121,-98,354,-941,-528,777,736,-342,-719,-984,-672,675,608,922,-470,-850,114,806,378,93,546,-121,-936,626,858,31,814,-529,-715,-714,-863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "read(byte[],int,int):int",
            new int[]{-878,-508,379,-502,-528,-389,-565,-500,-278,847,515,489,794,497,468,328,258,829,-260,125,-101,319,-445,941,-255,571,377,-198,553,398,-479,-801,-536,49,-322,-815,-917,-634,-174,364,608,-220,-522,-386,784,-514,569,212,429,-125,412,179,-908,-642,87,-864,1,112,-594,734,-156,-99,149,784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "reset():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "reset():void",
            new int[]{955,313,-999,-182,879,360,418,630,896,-53,-652,-554,287,440,-621,979,-187,199,893,545,493,-427,-105,499,571,476,-908,734,613,-951,671,-162,75,-343,-266,-420,928,-583,777,-189,952,790,787,-426,-823,-627,-487,-766,603,966,343,-212,828,226,-205,-842,-528,792,129,-756,433,-393,-201,-236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "reset():void",
            new int[]{-17,778,650,46,155,357,-533,659,813,499,-630,354,897,225,758,-616,-582,156,495,768,-621,724,-111,659,-324,615,640,241,-486,315,290,-419,279,327,-770,952,-968,-388,-245,561,-153,-867,-187,-686,46,-31,124,-721,85,-567,-832,842,274,-164,171,220,856,-866,-339,-77,40,531,437,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "reset():void",
            new int[]{33,-262,1000,-400,-800,395,-183,866,-421,-77,6,207,401,-418,146,64,-860,-828,426,-243,46,-6,-991,-578,-20,-258,391,393,61,-708,-459,46,529,-219,-686,-373,-5,-834,-364,943,795,248,530,267,-47,148,119,-167,-778,-178,20,-82,-355,-301,-556,-262,-31,-221,498,655,619,-82,-426,341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "skip(long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "skip(long):long",
            new int[]{-367,-119,256,-703,-703,-569,519,-812,260,434,-815,-271,-451,-754,-567,-317,205,-203,-25,466,-350,819,339,784,269,-957,-579,-502,939,-584,-250,884,253,342,368,76,-429,694,21,866,723,-53,-570,-692,-831,-876,811,562,720,490,536,-683,580,-100,842,-236,-656,-441,838,-425,-109,559,-282,-349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "skip(long):long",
            new int[]{-880,776,-90,-807,448,849,-609,183,503,584,-766,792,-854,-669,918,-739,-188,-261,857,114,422,-466,-875,-947,-743,758,-665,977,-699,841,-80,-380,642,801,-450,879,-132,669,177,358,87,756,295,850,-801,-190,394,-470,-668,-728,318,-498,-987,883,244,-23,-470,683,-657,-133,218,111,837,859}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "skip(long):long",
            new int[]{51,682,-291,-535,260,517,992,-307,-24,251,-488,829,-520,36,-894,887,-222,732,-833,473,661,759,3,97,-980,382,678,-479,-219,408,-33,-286,880,178,467,593,64,-463,-283,-843,996,-985,258,830,-879,-173,949,-96,73,536,597,776,-996,181,-37,393,173,545,-458,-243,338,835,185,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "createArchiveInputStream(java.io.InputStream):org.apache.commons.compress.archivers.ArchiveInputStream",
            new int[]{248,-664,-368,-94,-343,697,-723,-92,-796,-79,739,-313,-559,-390,626,-729,869,-563,853,413,-156,588,955,-259,-673,-86,-218,-80,940,-753,-94,625,282,-58,363,851,-782,114,-887,-323,935,274,-465,-16,-9,120,-384,818,248,-946,86,-902,652,-278,716,210,134,-716,207,-380,557,-516,666,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "createArchiveInputStream(java.io.InputStream):org.apache.commons.compress.archivers.ArchiveInputStream",
            new int[]{614,-381,554,-241,-164,489,128,768,473,96,952,-619,-92,-588,-202,-10,-539,901,-576,321,-1000,-373,123,765,574,773,693,381,456,183,-482,-983,-541,142,-156,-269,118,-985,-3,300,477,619,-716,943,-423,-779,-361,-359,-535,911,-320,380,584,-217,-213,-466,-270,-201,39,-827,398,561,-579,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.Lister", "", "main(java.lang.String[]):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "close():void",
            new int[]{1000,372,-292,-5,-869,725,687,239,-1000,-106,671,1000,601,-286,400,-173,-1000,1000,393,-741,1000,-331,537,-1000,-488,-481,851,-37,555,-438,-804,1000,66,90,985,-1000,-469,-761,1000,916,372,-180,413,-202,-216,188,-1000,451,-941,731,38,-1000,1000,-726,-25,672,364,14,1000,204,-625,738,-483,626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "close():void",
            new int[]{1000,907,-897,-761,-307,1000,800,-407,-136,518,-44,471,601,539,-121,736,-1000,-1000,1000,-1000,-665,-55,537,-1000,226,248,599,94,112,-1000,-84,1000,276,90,623,1000,1000,-1000,1000,938,-1000,-340,690,-819,779,-136,-1000,33,1000,731,-691,-1000,897,896,98,1000,1000,-91,1000,751,109,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "close():void",
            new int[]{-864,698,924,-968,854,-257,-686,777,607,995,-390,-998,-441,-998,619,-91,472,-162,-74,998,-654,179,103,706,481,272,-92,723,613,-919,-188,-823,592,-263,-903,972,488,245,-788,-305,-11,335,-955,-281,468,359,375,-598,219,125,-143,596,4,731,-391,89,262,602,-295,-955,145,173,713,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "close():void",
            new int[]{998,1000,-1000,310,-869,846,268,1000,-397,-402,502,538,692,340,845,-16,-1000,-1000,959,-1000,738,-827,639,-158,502,630,963,-128,-133,-1000,-1000,779,681,22,1000,282,283,-1000,487,1000,-360,789,968,-142,558,201,-455,127,-29,821,-833,-1000,900,-774,620,1000,638,-557,883,110,-30,-194,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-446,-204,-191,-735,-1000,-387,-442,340,-511,730,-1000,-240,-426,-7,387,499,766,273,-1000,560,379,350,-210,-1000,977,-465,-966,249,1000,29,0,636,150,614,1000,-1000,-544,-414,644,-98,610,364,-565,1000,-11,864,-511,1000,1000,380,552,878,-439,-783,-741,19,758,-1000,588,-634,452,-195,-706,373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-1000,-53,1000,-1000,-496,1000,620,1000,-685,1000,820,-990,-568,-251,829,846,850,-912,-1000,1000,916,1000,-1000,-703,229,-922,106,-1000,-365,-1000,-1000,-531,833,1000,708,-488,437,34,901,-549,1000,-42,622,-92,1000,542,-1000,1000,1000,1000,1000,-873,-1000,251,-829,-1000,105,-1000,-274,-136,-97,-868,907,14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-147,374,875,-914,-259,253,614,371,803,730,-261,655,-814,-2,-137,-827,-259,431,419,299,996,478,31,941,-309,467,654,-720,-802,-467,119,-200,-571,-926,505,445,469,177,-280,-635,112,233,60,-774,693,-356,137,332,-806,934,-461,-89,458,-141,863,-165,531,-828,-657,787,-364,582,205,-602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-656,-380,-399,1000,1000,837,-636,-1000,-457,1000,738,1000,830,-1000,963,-102,747,-1000,-1000,-1000,-1000,-1000,621,272,823,-307,-1000,604,1000,1000,1000,364,-221,-591,1000,1000,-1000,-1000,-100,-1000,-697,-1000,41,695,-1000,-1000,1000,-939,-771,-1000,-60,167,271,1000,-1000,433,420,-706,826,-716,-193,-1000,-576,865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.tar.TarArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{148,-539,-534,716,-521,-306,-519,-862,-714,-759,619,615,-411,-527,-465,805,-788,862,566,-434,-587,80,295,818,778,-263,-202,551,-595,172,914,-546,56,-516,852,936,733,271,-550,123,884,-552,-497,859,354,939,687,-324,71,59,-150,270,502,932,673,-932,-111,467,57,182,-530,50,977,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.tar.TarArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-678,-852,228,-1000,1000,583,550,-913,-1000,861,552,202,433,955,32,799,862,896,-60,-153,-269,970,-282,141,904,1000,561,436,-883,-492,150,-451,484,35,-1000,1000,153,-392,197,-497,-412,44,390,1000,-162,244,706,-1000,502,617,218,698,-602,-744,-226,-797,-216,519,392,-570,1000,-1000,-987,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.tar.TarArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{988,-1000,-717,1000,421,54,-520,-761,-446,373,301,-315,1000,1000,-541,984,-22,-1000,1000,1000,976,1000,595,-155,660,-489,1000,416,307,169,111,-501,-1000,-209,-382,557,218,-723,-216,655,-693,-376,1000,204,476,1000,1000,-483,-584,810,-248,-1000,-88,1000,239,-856,274,356,209,-1000,-136,407,1000,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.tar.TarArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{236,1000,-1000,-1000,-5,-440,-172,968,-56,-594,994,-1000,-1000,489,459,-1000,1000,761,-4,512,-466,-1000,1000,-424,-697,-222,229,-451,475,-737,-1000,1000,1000,-1000,-1000,321,-622,-1000,-1000,198,-1000,165,-1000,261,-990,-1000,-291,-859,-767,-213,848,1000,210,471,-302,-934,-1000,-1000,897,-167,416,-1000,-222,-980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "finish():void",
            new int[]{-741,-900,-476,-231,790,298,-420,894,-532,408,-510,712,546,-634,-348,-275,277,-876,-315,-625,302,-835,-938,160,276,398,667,-916,277,-43,343,-855,39,835,62,-708,67,775,361,-952,289,399,-654,-197,-929,-848,739,704,-890,-680,204,-993,-943,-877,-497,770,504,-339,-104,-133,-573,-615,-341,-586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "finish():void",
            new int[]{-721,703,539,104,153,-147,117,-38,936,5,-259,1000,363,771,94,1000,500,580,-320,185,-679,-1000,31,-142,594,400,-1000,-311,6,161,-926,-841,334,153,282,21,390,-674,474,-392,-820,790,-882,825,17,301,-813,78,-691,726,-193,-142,764,-2,-156,221,-863,-1000,-708,485,850,-579,-889,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "finish():void",
            new int[]{903,-442,588,-422,48,-645,-326,329,-429,427,-792,-556,720,-274,-734,-802,-106,289,386,639,749,-722,-149,465,-133,-911,664,-148,-558,923,182,823,-134,-384,-114,-546,-834,-948,-172,989,820,986,919,-249,644,736,-589,645,-840,900,694,992,141,936,-190,-547,877,295,5,892,-898,-370,42,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "finish():void",
            new int[]{-192,1000,501,-1000,-757,-907,656,-655,279,447,-505,-880,177,-204,1000,561,423,-933,127,-490,868,810,667,1000,-629,-944,748,-1000,-542,406,72,1000,-612,-395,-385,-1000,-771,-549,-1000,-324,-969,-1000,855,-300,-213,-1000,980,486,1000,-1000,705,-253,-1000,1000,-501,-277,1000,-862,1000,188,74,132,-376,32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "flush():void",
            new int[]{-198,-822,-191,-697,-870,684,165,-547,-674,837,755,622,100,-851,-530,-566,-359,707,972,-911,350,832,597,87,954,-613,131,-247,567,-198,478,-494,333,-69,-114,713,71,153,875,-777,996,833,315,-755,-979,166,-813,-906,426,747,-506,492,730,781,-496,-344,509,-272,-941,143,570,434,17,610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "flush():void",
            new int[]{-160,541,623,-119,-431,-422,-947,413,-778,-284,936,78,-749,-100,209,-253,-764,-673,-915,113,957,783,-973,206,-106,571,57,331,47,-75,-265,-850,-984,814,780,580,88,-604,804,-579,-315,-995,889,-830,39,-450,-105,-625,145,-547,242,-974,-829,-138,675,-985,-635,750,62,462,196,759,-397,873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "flush():void",
            new int[]{-677,458,840,-226,-864,-92,149,908,190,-744,235,-268,101,678,389,-667,585,-454,-406,48,-447,-766,499,516,-539,700,-814,302,-202,-568,506,-956,596,-600,-549,10,689,978,476,186,-947,-547,-999,595,33,703,412,-606,-483,903,-192,482,-836,-296,454,-143,-272,-777,451,753,164,413,-502,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "flush():void",
            new int[]{-566,1000,222,-281,-195,684,-991,-472,856,-27,269,123,-1000,874,1000,492,-639,-563,-1000,-180,1000,-259,-410,-1000,-404,83,327,1000,-259,523,-239,-796,-616,-706,879,-223,-265,-614,727,-32,-767,-283,-1000,-974,23,-922,-124,1000,-1000,-265,649,-585,-328,523,-764,53,-1000,277,502,-269,1000,-472,904,885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getBytesWritten():long",
            new int[]{-1000,30,-901,-588,-930,1000,-482,-1000,1000,-1000,-1000,-1000,477,-862,-355,-1000,-1000,1000,1000,1000,1000,-579,-524,1000,-383,-359,-1000,685,-177,698,179,400,-968,-1000,564,-55,704,65,-183,-17,-341,441,796,-1000,-185,331,1000,-1000,604,-474,1000,-1000,343,702,-290,-274,-393,-998,-713,1000,-688,529,-1000,375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getBytesWritten():long",
            new int[]{231,703,-899,-405,-291,658,-344,-1000,-123,84,-239,-1000,189,-1000,-379,-1000,-941,-402,1000,243,1000,1000,-667,1000,163,-440,-374,1000,-53,8,425,-931,-978,244,-381,-523,-639,640,172,288,-157,-851,842,-760,-834,-248,1000,-1000,751,-456,121,810,-55,191,144,314,98,-1000,-1000,1000,31,457,-912,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getBytesWritten():long",
            new int[]{-250,-212,-736,1000,981,-389,496,-309,389,338,-769,-784,-1000,-93,2,-51,-764,436,588,-167,808,-719,-1000,663,-163,-180,-422,51,-1000,579,1000,39,-1000,-200,-7,982,400,574,1000,1000,1000,201,1000,800,-129,452,-261,44,1000,-1000,441,-547,-102,-843,180,-334,-58,-508,156,82,-324,-151,-366,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getBytesWritten():long",
            new int[]{-1000,470,-87,318,-1000,-745,-566,-197,1000,-601,-1000,-65,-1000,-1000,710,-180,-459,462,1000,342,-1000,-1000,-1000,-449,-206,1000,-1000,-224,-186,-188,244,-123,-181,202,-412,1000,354,627,-159,-43,-1000,1000,1000,105,250,497,415,1000,632,-1000,-167,493,-1000,-68,459,-1000,-331,-1000,-1000,-366,1000,-16,-1000,-488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getCount():int",
            new int[]{-503,-485,382,35,243,833,109,595,538,299,-78,324,-37,391,539,761,-396,-149,511,420,83,673,-390,-401,-275,-281,-521,699,-591,138,-239,-41,531,-273,-256,-1000,688,1000,366,72,956,584,-725,690,-88,432,289,27,326,-414,-156,476,-908,428,-37,305,-45,499,-484,-423,531,-755,-212,105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getCount():int",
            new int[]{126,-390,894,-270,-697,1000,-557,-859,-691,838,651,-73,1000,-552,-851,-143,-381,-392,-892,861,892,274,-805,260,729,178,613,-1000,-542,528,1000,408,-196,1000,910,546,1000,-1000,-529,468,946,-1000,-351,-113,-381,-1000,-323,844,-760,107,477,-36,-756,-71,-1000,-268,1000,-62,537,292,-497,845,1000,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getCount():int",
            new int[]{-1000,-1000,1000,1000,-1000,159,979,-107,367,95,764,286,-333,709,-304,-1000,600,-1000,-170,-880,884,-554,-1000,-1000,891,761,224,-1000,-822,-531,1000,-761,738,-481,-1000,-67,623,452,1000,898,198,301,397,409,-1000,424,-957,1000,-863,-308,-840,211,-509,34,387,-534,-433,-1000,-733,-186,945,108,-729,-94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getCount():int",
            new int[]{-331,-920,146,-1000,631,990,1000,828,-400,187,-286,-635,525,-649,243,1000,-841,117,1000,370,77,1000,995,400,-1000,65,-1000,1000,-247,400,-1000,-565,95,1000,51,-1000,892,1000,179,-126,1000,1000,-985,1000,170,1000,379,85,-16,-681,1000,733,-1000,-1000,-161,1000,732,921,149,-682,-289,183,-609,66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getRecordSize():int",
            new int[]{945,955,-588,634,23,433,-144,-831,509,-814,614,-261,-144,-147,-436,301,464,-552,-631,913,821,554,110,601,-243,513,432,923,276,-42,515,-926,760,-135,91,106,472,425,-411,-97,-766,-456,39,-72,-95,-487,-193,-579,878,-488,-533,733,174,975,137,796,930,604,-529,329,73,-210,-824,323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getRecordSize():int",
            new int[]{174,-306,-861,-309,226,75,241,1000,501,189,-847,-471,238,857,4,-931,-274,718,1000,500,-108,552,-387,-125,14,-217,794,343,-73,-71,-573,-856,245,303,-13,-346,745,-402,991,-319,161,756,291,413,1,190,-335,-728,637,-268,78,207,-114,33,-249,57,-437,240,-547,-567,-134,448,626,-94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getRecordSize():int",
            new int[]{915,-308,890,1000,-1000,-1000,1000,-717,-731,111,-1000,-356,81,-728,-1000,61,-674,471,-830,1000,-257,181,-426,-1000,203,556,-142,247,-986,89,-623,973,-1000,-1000,431,-89,1000,-129,-693,122,747,-660,618,-703,26,415,-670,1000,-512,-480,314,-294,886,-921,386,-397,-906,381,578,1000,-395,395,651,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getRecordSize():int",
            new int[]{786,776,812,781,-614,555,348,-1000,-711,359,313,-237,1000,-1000,-884,813,-57,-91,-617,1000,692,1000,-573,175,-556,-160,-432,369,741,913,-55,173,-583,-175,671,5,85,448,-1000,647,-1000,-1000,866,-682,-1000,-400,119,43,443,224,648,383,698,-62,-630,-604,-199,49,-936,778,21,512,-824,-876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-1000,-456,-491,610,979,-888,-492,930,1000,1000,1000,-106,-277,-993,842,-928,636,404,166,-852,1000,1000,-486,-692,-200,1000,1000,1000,-118,327,-171,-1000,637,1000,1000,-382,1000,-989,-1000,-956,1000,811,391,216,38,338,-1000,96,843,-247,218,-377,-838,-1000,-591,1000,735,-844,-1000,437,-225,-1000,156,213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{412,91,-713,-325,674,-576,-578,286,503,-426,760,-343,-7,254,-208,279,-286,166,389,-1000,958,549,-467,-29,-145,-1000,487,249,-514,-640,243,-874,544,1000,296,150,-29,429,-178,-377,-103,887,710,-354,26,-55,693,-767,65,820,160,-119,590,6,-910,-85,-481,-1000,1000,521,-920,-471,75,379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-1000,218,-316,67,1000,-791,-477,-171,954,-300,178,-441,1000,-1000,567,-519,849,-110,-763,-1000,671,1000,-142,191,72,895,8,914,-313,896,-314,-563,406,1000,595,411,1000,-753,-356,-727,214,0,713,569,843,-36,-235,-25,-208,156,736,-90,-197,788,555,848,-487,-517,32,-571,-477,-1000,-177,-66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-241,1000,-754,-1000,1000,-1000,-1000,210,409,-1000,661,-832,1000,833,-1000,629,1000,773,-192,-1000,1000,1000,-20,-487,809,-366,994,218,-1000,536,597,-1000,1000,1000,244,1000,121,0,-1000,-1000,-473,-870,1000,-47,524,-587,1000,-921,-1000,101,-95,-1000,237,1000,-445,-56,-637,-968,1000,150,-1000,-1000,-521,-26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setAddPaxHeadersForNonAsciiNames(boolean):void",
            new int[]{468,-318,694,-781,-76,-248,469,-558,329,874,67,427,616,-966,430,278,-730,600,-837,-427,-837,556,283,-631,668,25,340,-881,-152,-367,217,-949,770,-397,-132,-240,-923,-1,-49,-784,-177,-531,-139,264,293,64,44,-269,16,-811,-399,-232,-485,-376,204,721,484,686,116,957,985,61,-281,368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setAddPaxHeadersForNonAsciiNames(boolean):void",
            new int[]{790,223,335,530,-116,-902,-947,425,-581,1000,-142,-448,-1000,-783,1000,-686,-1000,-790,-570,-499,-988,0,-296,1000,1000,-231,31,121,-350,-1000,1000,-509,978,-995,-1000,902,-1000,-540,105,677,-562,512,490,-342,-500,1000,-844,-700,633,-376,-239,269,37,-915,-1000,-178,-326,1000,-1000,1000,854,-193,144,741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setAddPaxHeadersForNonAsciiNames(boolean):void",
            new int[]{326,-26,-706,334,880,517,257,-267,133,-373,-86,-174,617,-82,861,-840,-694,360,672,-845,193,-931,125,158,-236,-223,-784,-884,779,175,910,-952,42,614,501,675,-79,231,-831,72,-758,485,-135,985,908,-744,23,621,-932,254,745,-895,23,246,91,-437,156,823,906,-141,-858,-137,-805,346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setAddPaxHeadersForNonAsciiNames(boolean):void",
            new int[]{366,2,41,-1000,1000,-279,-516,-495,1000,1000,28,1000,899,-591,786,1000,-1000,1000,494,-847,341,823,414,-794,-982,283,245,-1000,590,889,-970,-1000,138,629,-1000,-1000,171,524,-227,-785,-1000,-1000,919,-220,360,-615,-78,481,-702,-151,760,-1000,33,904,280,1000,1000,-859,1000,85,1000,-416,475,379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setBigNumberMode(int):void",
            new int[]{-239,-198,48,-388,-711,-806,180,271,-479,-743,-994,792,-941,604,-279,-861,524,-577,695,-495,465,-767,325,-48,-674,-924,668,-962,-514,372,681,-98,-497,-686,-716,-475,-130,-12,-104,666,-176,-840,-359,290,41,-905,-20,-743,427,-317,-824,-532,-370,-333,698,737,-629,823,-549,213,274,562,-467,376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setBigNumberMode(int):void",
            new int[]{-873,721,-899,-925,24,646,-640,394,-421,627,-145,732,704,399,-746,534,-155,242,588,-853,664,516,-681,93,149,371,-863,768,-435,529,-784,218,-217,665,-967,847,711,-779,-659,715,-242,275,364,416,590,449,900,432,776,834,630,-650,-637,332,660,328,888,-34,80,831,913,256,-863,784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setBigNumberMode(int):void",
            new int[]{-715,-626,454,-466,-88,-908,680,-605,264,770,-51,762,-43,-150,-82,718,310,-500,834,325,697,-563,-41,-709,185,-822,-824,-984,-714,-745,389,814,769,605,756,-662,-352,595,-775,-989,699,-22,-634,-393,-839,424,-917,53,631,-765,-589,961,-408,341,334,475,504,739,-692,572,914,-833,4,148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setBigNumberMode(int):void",
            new int[]{-23,-586,-348,564,-340,-422,-1000,1000,-416,-386,716,-616,573,953,242,-709,-1000,-135,-859,-524,-447,-718,-293,1000,-219,1000,467,1000,-280,694,-1000,-471,-1000,-989,87,-22,-702,-475,-242,-77,-26,804,-988,1000,-839,-1000,710,-699,310,1000,682,794,646,-373,157,-1000,-455,-524,-772,385,-1000,949,-1000,328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setLongFileMode(int):void",
            new int[]{869,126,-346,-762,-675,901,-360,-471,156,-194,-304,373,-816,284,-589,463,-463,-144,460,-731,22,-709,879,471,-492,-949,67,188,753,524,31,-889,-936,693,-782,-69,-536,791,-489,-536,623,225,412,841,701,579,-736,746,-508,893,494,264,747,542,262,-826,417,40,-327,685,700,880,-257,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setLongFileMode(int):void",
            new int[]{874,625,-681,-179,598,-1000,18,603,73,1000,393,-447,362,-763,1000,-189,-865,348,140,225,407,-662,36,-1000,862,269,1000,113,1000,-1000,-648,494,-46,-526,446,769,432,-244,260,-305,-290,-124,-1000,-982,375,-348,850,1000,-721,-508,-965,-63,935,-602,216,112,880,-16,308,-1000,-1000,-609,1000,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setLongFileMode(int):void",
            new int[]{885,-398,-635,-371,-400,-571,529,87,-363,904,-704,468,-299,-701,992,-544,229,38,-231,-819,-72,298,-150,78,223,226,609,-782,706,-795,-851,-366,-361,721,-99,-585,835,272,-58,412,-171,526,-752,-707,147,442,868,367,718,447,-839,123,-41,-97,641,-255,937,107,-258,-17,-859,-173,849,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setLongFileMode(int):void",
            new int[]{-233,-1000,312,-747,-796,845,-198,-639,604,1000,-526,155,-626,1000,-441,149,249,-448,585,-625,364,-281,-199,385,569,-919,50,-264,1000,1000,-310,-613,-141,54,112,-613,-464,444,841,-964,505,-331,120,211,-424,1000,992,1000,445,962,1000,419,-771,-620,108,-921,1000,-293,218,-278,673,-501,-1000,305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-776,427,957,-396,209,-642,244,-722,779,973,1000,-51,-393,-826,113,-944,-439,246,328,-943,121,-776,394,446,665,-131,619,169,-304,-1000,-693,-590,1000,-678,88,-594,-530,-430,-1000,-885,-10,837,841,-841,-1000,73,-585,216,-842,947,-519,332,-813,619,386,1000,-406,-593,104,188,-304,1000,804,-944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-950,666,-196,-984,182,1000,-259,-779,72,-352,296,-753,-164,214,-1000,-925,-809,-408,552,-262,-274,715,-1000,929,-933,-715,-1000,1000,1000,839,779,71,636,496,-195,-58,-1000,-626,76,-300,448,486,-229,-775,1000,-509,-322,-1000,767,1000,-87,-466,-203,759,-233,993,226,729,-479,-1000,-431,963,1000,-24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{76,286,-614,-758,-931,489,-317,692,810,-95,-958,-42,-849,-578,267,18,199,-869,-241,1000,525,738,-134,-219,712,-141,554,842,6,597,2,580,-903,-430,303,644,324,968,990,-249,-21,139,-253,-77,-195,707,417,107,269,-701,-884,-248,733,576,166,-948,-725,-548,381,-691,-446,-854,-736,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{133,-1000,-373,-323,255,-724,-331,461,-427,-542,-840,97,631,1000,586,-1000,-1000,1000,55,-943,403,1000,337,-545,665,-37,-979,-516,-687,454,406,-913,298,840,1000,-327,-204,-746,615,-885,949,-604,841,150,-398,-896,-827,-1000,193,390,1000,467,372,805,-807,-73,1000,819,107,188,1000,-120,197,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "canReadEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "getNextZipEntry():org.apache.commons.compress.archivers.zip.ZipArchiveEntry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "matches(byte[],int):boolean",
            new int[]{-110,159,227,-505,758,330,162,302,885,992,106,490,-416,494,810,66,-691,-525,-141,76,527,-267,-831,-491,-951,354,642,-704,-71,-431,603,559,-201,13,14,408,955,611,609,-25,-116,123,-900,169,12,-455,360,647,-557,-958,120,890,-816,-178,117,948,-63,498,-122,-579,-65,843,-671,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "matches(byte[],int):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "read(byte[],int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "skip(long):long",
            new int[]{-918,-636,44,-854,101,-913,235,580,-356,-481,627,-245,-881,-866,484,-924,956,-696,-275,-318,-39,-726,-447,171,-685,-649,185,-491,-835,-528,339,133,603,23,-957,-662,-787,726,-249,-815,379,-732,-247,-636,-957,387,437,-278,-292,131,84,-515,158,-848,575,617,557,-937,613,-818,396,-898,929,-871}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "skip(long):long",
            new int[]{-1000,116,562,624,586,81,568,397,1000,-205,-1000,-763,609,-107,1000,-417,959,321,739,597,-142,770,348,515,-219,-735,-194,-1000,-755,81,928,-497,-361,-852,-719,-959,21,185,-1000,513,-1000,-1000,426,-874,-296,-990,292,567,103,473,-1000,799,-1000,-198,799,-869,967,-1000,873,891,1000,-174,976,605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "skip(long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveInputStream", "skip(long):long",
            new int[]{-845,-1000,-421,-1000,172,685,1000,1000,5,-610,663,-939,-1000,-1000,-232,-1000,1000,-1000,900,-888,795,322,-858,135,-1000,-625,1000,-1000,-653,-214,-153,-620,307,-412,233,594,-326,-25,-6,-1000,851,-667,-87,-749,-1000,283,943,-1000,-533,382,-1000,-1000,79,-1000,326,663,515,-1000,639,-417,-227,-433,293,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "createArchiveInputStream(java.io.InputStream):org.apache.commons.compress.archivers.ArchiveInputStream",
            new int[]{248,-664,-368,-94,-343,697,-723,-92,-796,-79,739,-313,-559,-390,626,-729,869,-563,853,413,-156,588,955,-259,-673,-86,-218,-80,940,-753,-94,625,282,-58,363,851,-782,114,-887,-323,935,274,-465,-16,-9,120,-384,818,248,-946,86,-902,652,-278,716,210,134,-716,207,-380,557,-516,666,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.ArchiveStreamFactory", "org.apache.commons.compress.archivers.ArchiveStreamFactory", "createArchiveInputStream(java.io.InputStream):org.apache.commons.compress.archivers.ArchiveInputStream",
            new int[]{614,-381,554,-241,-164,489,128,768,473,96,952,-619,-92,-588,-202,-10,-539,901,-576,321,-1000,-373,123,765,574,773,693,381,456,183,-482,-983,-541,142,-156,-269,118,-985,-3,300,477,619,-716,943,-423,-779,-361,-359,-535,911,-320,380,584,-217,-213,-466,-270,-201,39,-827,398,561,-579,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.Lister", "", "main(java.lang.String[]):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveInputStream", "", "matches(byte[],int):boolean",
            new int[]{-113,826,-77,582,507,-861,-482,951,237,-688,773,480,-719,978,5,675,-395,987,-721,-357,581,-866,-114,913,974,540,617,-607,-865,-97,-793,58,596,-523,-527,-362,-352,-139,-189,-159,944,468,980,455,685,885,379,51,525,-759,401,941,976,-814,-145,-436,801,-911,-50,-478,643,-312,-126,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveInputStream", "", "matches(byte[],int):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
