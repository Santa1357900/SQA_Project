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
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "available():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "available():int",
            new int[]{918,295,-334,818,266,-429,-415,525,-246,953,388,-572,391,375,557,-133,-747,-878,418,-426,-384,820,248,404,-639,676,-272,182,734,-378,434,-257,-596,-875,-519,-903,-768,-468,-609,-419,-874,64,788,846,-32,-183,635,567,682,448,-628,887,-772,655,-468,774,555,-30,213,-290,-649,936,112,-583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "available():int",
            new int[]{-20,-350,395,-940,224,802,462,313,616,178,588,-25,47,-77,-1000,862,-96,1000,-34,-426,764,366,-446,-1000,1000,-241,-288,-457,-238,660,-79,291,836,259,1000,142,1000,-455,791,989,650,93,209,-1000,-46,288,-1000,-246,-575,-390,216,70,825,454,141,-117,-134,-814,637,1000,14,-425,-502,365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "available():int",
            new int[]{-526,-1000,-205,554,1000,-15,620,-837,202,462,-74,-271,362,-726,-1000,755,424,181,784,-967,-1000,-621,264,1,-564,-853,675,-639,864,95,1000,93,1000,1000,335,-778,-710,-628,653,-484,-327,159,-905,233,-832,531,-449,958,1000,172,-1000,-473,1000,516,-776,-898,356,-328,-1000,212,217,-1000,-32,340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "canReadEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "canReadEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{516,127,295,353,-400,10,-963,-440,583,-421,-742,263,-65,549,194,685,1,93,325,786,724,191,-341,-225,563,-868,164,-987,-319,31,404,362,-165,-448,820,565,-696,-611,40,919,-325,-540,84,186,321,-721,-563,-523,17,514,-469,642,-722,-671,430,627,-239,-185,-800,-810,306,-465,-175,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "canReadEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{-722,-404,-631,-719,-40,-438,-376,268,509,-651,-542,676,268,-617,873,156,-789,-158,487,-865,485,559,771,311,787,523,691,-966,627,-378,282,-206,877,505,-945,-331,921,838,-901,-833,-825,-364,219,673,794,-146,523,403,-26,948,-491,-328,-584,-258,-125,-893,-765,-374,-966,-553,-116,799,-359,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "canReadEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{-336,-436,-481,703,-890,-919,-266,-755,-829,308,297,666,749,722,123,488,-805,-999,-48,-276,754,-798,-825,-862,-578,292,708,302,-316,-733,377,843,-65,430,-397,-133,-709,744,169,188,995,-395,-704,682,609,865,381,864,837,-519,-687,628,919,791,526,950,-680,-938,672,341,-338,-272,-884,840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "close():void",
            new int[]{-890,721,-919,-951,-787,-512,-428,-971,-599,-789,396,354,-981,-446,-272,-514,-370,-200,-703,454,-745,452,-421,88,-376,42,-222,-291,-498,492,226,-133,-3,-344,328,930,384,-628,-543,-97,-568,-718,-700,724,-530,961,106,-554,514,-502,-37,-753,-348,550,-170,964,585,123,-654,-978,-273,-152,-340,21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "close():void",
            new int[]{-544,-350,765,-410,-931,-859,-212,-474,294,189,350,-925,400,-346,994,327,401,962,-469,322,-587,-233,247,-195,210,-48,991,-919,-912,556,302,-691,964,569,-778,843,287,-820,-253,776,-229,37,-58,-296,218,-674,690,913,-213,-893,761,-292,-62,-809,-574,357,-27,-880,-804,-686,624,267,-555,-258}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "close():void",
            new int[]{-594,-802,-464,-401,927,-456,-73,62,639,-34,-953,-170,-791,-634,-463,-471,958,-425,464,-34,-226,534,412,-663,-131,-506,224,-15,-978,620,614,-670,775,712,-677,-125,683,412,781,695,149,227,365,-264,-324,984,-775,401,-618,897,414,-597,568,353,567,-320,-218,720,-245,269,356,-989,-721,482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getCurrentEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getCurrentEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{-143,799,-124,925,569,689,173,-979,-917,-220,971,584,-59,419,273,435,-849,703,-955,-439,679,463,96,663,524,938,-221,719,-117,306,-546,253,-821,838,44,938,-154,282,-677,-524,665,280,-385,233,187,115,-520,61,325,879,665,-84,512,-715,-904,-581,-946,-215,14,341,216,365,506,-839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getCurrentEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{-1000,-842,72,-1000,342,306,-1000,-507,-284,-935,198,376,1000,871,-953,1000,-265,-436,646,314,232,-1000,-317,-660,86,214,143,-1000,-665,-333,-1000,-979,-396,1000,243,509,14,371,-367,42,324,-256,88,-27,143,-505,-73,1000,1000,-901,-40,174,793,1000,1000,1000,245,380,-352,103,845,1000,707,282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getCurrentEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{-778,-742,1000,-1000,1000,593,551,959,-114,-83,-218,-1000,531,282,-1000,1000,519,-1000,712,912,779,-204,357,-1000,927,882,-235,-348,-28,-329,-1000,-1000,54,563,-410,1000,527,-115,-271,390,-366,386,-654,-794,808,-748,-255,1000,1000,357,384,-325,1000,706,399,1000,354,-10,-460,1000,1000,1000,1000,14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-1000,-33,468,-234,1000,390,213,-1000,1000,-812,934,192,1000,-1000,309,-414,-444,612,1000,-1000,1000,-122,499,-1000,1000,841,712,1000,807,-1000,474,-374,-433,-1000,765,-533,-1000,540,1000,862,339,-228,554,701,652,1000,-368,169,-369,-428,-836,-1000,-967,1000,-852,-250,153,1000,1000,1000,-638,-15,-431,-734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{852,-401,-770,125,234,425,-229,-336,-859,-860,970,217,-501,317,329,761,-548,102,-649,1,570,7,-417,583,563,73,-953,-427,987,-961,699,-528,290,537,733,-1,459,-606,708,-589,512,641,-216,-793,152,-21,-341,-243,-14,143,-807,573,890,837,181,-237,354,751,217,524,-81,533,-787,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-470,-784,-503,-615,-636,-276,-617,-977,-131,-400,652,-883,-385,947,-649,119,845,-461,-228,487,886,-370,-710,749,-497,-129,665,808,-791,719,-532,69,-594,161,-515,866,146,606,119,-743,-693,-670,-916,-674,1,7,-963,-218,-826,-462,154,892,-497,644,138,-466,951,513,652,538,-378,-851,749,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextEntry():org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{1000,1000,873,264,-1000,-1000,-1000,1000,640,744,-85,217,-795,-314,-1000,-662,1000,722,-619,-191,303,34,-417,393,-87,1000,391,-1000,387,-961,313,-713,-791,537,-74,690,-802,9,1000,190,756,-809,431,1000,-66,644,-588,-163,976,-454,-1000,65,-1000,-617,59,-860,-1000,326,-820,836,-71,-741,-30,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextTarEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{-138,-477,330,870,-359,-689,-6,-121,318,163,548,-711,1000,268,1000,618,120,704,801,539,-456,834,787,-41,-343,-512,-34,1000,988,-724,-882,-1000,70,301,-582,29,538,561,-956,-87,322,177,684,337,-947,-1000,-655,177,-1000,-607,993,227,158,827,-257,1000,484,125,340,-543,597,353,690,23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextTarEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextTarEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{227,523,-869,960,-87,496,839,-287,756,566,220,505,-230,-74,-989,-493,-839,-643,-731,925,-583,957,-291,-509,766,-832,-316,-344,106,-489,-901,469,186,-531,-932,-827,-379,538,-335,196,129,-347,-287,-616,-728,-110,-737,86,957,639,117,16,-50,300,-429,-311,745,-69,-597,668,-731,290,67,872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextTarEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{388,358,-274,778,-596,-617,-241,-464,17,501,-594,155,-278,-661,-823,-741,925,576,214,53,761,-183,466,210,41,336,-631,-840,672,-651,-395,6,598,496,989,-643,-935,-176,-933,-3,884,-292,-344,495,191,867,191,608,241,928,-496,-337,-299,826,-545,947,78,-267,700,622,976,-809,776,622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getNextTarEntry():org.apache.commons.compress.archivers.tar.TarArchiveEntry",
            new int[]{56,-286,-400,1000,662,1000,-67,-178,-46,-1000,-71,-201,-5,-1000,617,-144,-333,139,-393,685,-774,474,119,1000,1000,999,-715,228,658,297,-934,-13,985,122,682,-467,306,24,397,-116,-380,1000,-892,140,816,-362,-430,-53,-1000,-401,408,864,-100,-612,-1000,471,1000,201,-16,835,-956,616,12,346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getRecordSize():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getRecordSize():int",
            new int[]{-511,-263,-446,465,-568,-784,380,-16,-338,-306,783,-128,-405,165,98,-82,-355,-336,-166,-176,-227,57,-694,755,-701,700,993,-384,-107,-642,492,-897,623,-207,-807,155,-106,-38,-994,-497,177,-63,-146,-313,-466,-694,13,109,-882,98,824,-492,63,-936,861,559,482,550,170,210,-357,104,869,872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getRecordSize():int",
            new int[]{589,-34,-916,-896,-319,379,-77,-775,-487,-781,-670,285,691,198,515,854,240,736,439,528,-534,-89,464,395,216,-945,948,191,883,-957,581,-629,-593,-658,-545,-119,-448,421,-850,-527,727,-719,-576,88,791,-468,-499,55,-592,698,407,-387,784,269,-689,641,910,349,-573,-640,-213,751,-359,-737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "getRecordSize():int",
            new int[]{-467,-176,790,551,791,-579,208,-50,339,-990,-924,945,500,116,-88,-809,-464,69,575,-873,21,-700,115,-307,943,-648,915,-566,-854,-837,693,249,684,-774,-845,599,600,771,-652,-957,722,230,-917,-327,-839,-25,-786,206,391,525,991,-706,-998,-738,239,-51,-464,73,-320,887,-833,-751,373,-914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "mark(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "mark(int):void",
            new int[]{-53,355,-939,263,-933,759,-282,-626,345,-434,181,572,713,-309,592,-177,138,801,-58,-506,426,796,-500,-340,163,238,-353,252,-519,957,-28,-142,506,-285,610,-137,50,455,611,-413,401,427,412,-919,-103,250,352,-673,-590,-153,-345,739,-203,-876,244,55,930,231,-618,979,-976,-710,198,-724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "mark(int):void",
            new int[]{612,668,-469,30,-961,640,-874,447,-299,65,-186,60,-826,286,-439,683,895,-788,961,267,-40,714,-787,60,993,2,-786,60,564,-744,-107,66,789,415,-943,-424,456,-314,818,-303,308,-905,-53,271,-32,165,-615,-504,-617,-295,610,-538,-347,-406,-483,-773,627,-564,-936,-671,-240,-987,52,-46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "mark(int):void",
            new int[]{369,574,-904,-618,-346,-832,100,-223,-363,478,364,-90,-500,-117,817,990,-255,-119,-168,406,-975,-623,-449,908,-434,-123,736,-508,683,143,792,-869,41,482,-239,-667,-954,538,889,498,30,156,-358,66,737,-287,-49,-515,33,168,874,240,-279,-2,911,315,886,179,725,-217,-710,-588,176,-471}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "markSupported():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "markSupported():boolean",
            new int[]{613,19,633,-150,316,74,2,-446,-848,376,-807,148,527,279,-86,96,-59,526,593,488,-677,806,332,161,374,-375,-847,-895,788,316,702,-267,321,674,-51,-177,-147,967,915,-281,-163,-112,-61,-287,817,390,586,-541,46,-729,961,-281,695,-88,876,-210,-407,591,-919,-447,3,-540,-367,-768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "markSupported():boolean",
            new int[]{-136,974,-397,238,-406,-603,160,-808,-377,-833,-479,654,133,-615,-873,554,83,-777,-268,-369,-528,-310,707,760,318,781,952,611,-814,-489,45,418,218,-928,-336,-117,778,-440,741,27,58,567,-643,-870,309,-711,-48,824,703,756,57,866,-733,-721,487,-856,809,275,671,97,320,-729,647,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "markSupported():boolean",
            new int[]{50,64,-206,-150,77,-171,1000,55,531,1000,1000,-1000,1000,-1000,-87,528,-596,526,1000,530,-937,626,650,-1000,-467,-606,-1000,885,509,316,1000,-44,-1000,-676,663,-1000,-805,1000,1000,255,-1000,-454,300,460,-126,-1000,696,1000,-108,-115,511,-161,189,-1000,937,-923,-27,-665,-79,-712,-266,876,660,-681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "matches(byte[],int):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "read(byte[],int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "read(byte[],int,int):int",
            new int[]{945,-293,339,-272,457,-403,653,-151,997,-869,-491,147,823,240,582,-519,258,823,-109,36,-683,-539,493,464,787,-488,-486,998,87,-72,-649,128,570,-925,60,-510,481,-447,442,519,-296,557,-848,-898,632,-330,264,989,26,157,157,-979,-429,787,350,-597,-32,-973,-63,158,895,-979,-325,687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "read(byte[],int,int):int",
            new int[]{-699,284,918,-967,-1000,-996,515,-1000,1000,568,508,168,-240,-594,461,937,278,282,-51,1000,0,-461,417,164,-301,-23,-634,0,0,-529,-143,-972,1000,-450,-1000,-1000,-928,-1000,-171,626,53,583,-811,299,-697,-673,-429,-18,0,-1000,564,-933,0,0,-862,-795,-1000,188,-371,1000,-64,-231,919,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "read(byte[],int,int):int",
            new int[]{-1000,1000,926,-1000,-619,-578,0,-986,1000,54,302,1000,-38,-845,360,603,-188,39,1000,485,-41,312,1000,-1000,288,896,-478,-859,783,-578,-1000,-538,1000,515,-1000,-769,-599,-1000,412,1000,446,1000,-1000,-298,-1000,710,-860,-580,414,-134,1000,-1000,-267,-275,-463,-1000,-445,0,286,-209,507,486,664,329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "reset():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "reset():void",
            new int[]{338,331,176,194,-377,743,184,-663,-288,625,473,449,889,-104,594,-891,677,662,-522,-996,-653,-743,116,183,779,455,-354,360,833,-867,-82,437,-518,-210,-557,488,939,-194,223,789,-253,86,369,203,383,847,-354,-827,-688,672,132,922,-377,-307,28,60,-615,793,-695,731,-309,21,-611,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "reset():void",
            new int[]{549,646,-545,-76,-403,-940,747,-194,459,-405,822,-822,-460,666,335,813,71,4,-18,636,98,-825,274,837,193,428,-117,-515,-338,758,275,-557,129,-782,150,450,-652,990,-185,595,-154,-508,-986,-921,568,-108,-3,-808,-919,-220,621,684,-895,-499,151,387,-881,-363,-913,829,654,483,-192,854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "reset():void",
            new int[]{-530,20,-848,-201,-688,834,-887,-325,163,705,-227,-59,994,213,-682,999,905,-460,-482,-691,246,-56,-586,-846,797,826,-892,-471,-558,-783,-455,-646,872,608,961,274,-170,546,271,-314,148,-826,89,-703,408,-1,-692,532,447,-461,-806,-595,-887,265,-178,82,-304,-784,-678,312,-713,-928,907,-878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "skip(long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "skip(long):long",
            new int[]{527,583,315,-860,694,843,-295,-181,285,-142,37,655,329,839,-860,-719,404,-55,-15,106,992,-259,965,896,-932,-388,501,-851,-975,603,-11,-701,-797,335,319,265,-856,879,45,-627,-27,779,-373,-752,271,-241,146,940,228,-908,358,-773,-68,80,-964,819,-769,-350,179,-986,767,-316,841,-741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "skip(long):long",
            new int[]{30,-340,334,223,733,-222,6,22,-82,36,-606,287,539,-221,325,895,824,685,-47,330,-123,682,-632,967,428,-584,-324,713,765,167,493,463,83,-684,-975,-654,280,-330,742,219,551,278,-967,431,-918,709,-862,-420,572,214,-804,628,-632,-354,277,-467,-403,594,-80,-586,-179,-221,-128,90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "org.apache.commons.compress.archivers.tar.TarArchiveInputStream", "skip(long):long",
            new int[]{-405,418,260,950,-905,-322,936,756,-385,439,-905,66,898,960,-534,-370,561,-358,343,869,-586,875,-124,-114,58,507,-533,-969,41,404,978,574,116,685,718,435,-115,512,-995,-515,-503,-425,-567,-514,945,791,979,387,-9,149,513,-595,-203,126,-591,71,-106,660,-480,-727,-667,234,-977,202}));
    }
}
