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
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.cpio.CpioArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{268,-530,-665,735,495,-954,177,-1000,-561,-68,-1000,-1000,1,1000,-1000,-331,-308,-386,783,-187,-449,1000,-42,-1000,867,23,-464,-1000,-338,-450,363,-896,286,279,-177,400,-3,323,-866,1000,142,91,213,169,450,1000,538,-347,618,-133,-334,-1000,-1000,257,563,-240,-390,-240,-937,761,1000,-1000,160,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "finish():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-379,1000,108,-1000,123,205,-626,1000,378,510,-144,193,927,649,-871,-406,-532,-125,472,-861,885,35,121,-146,-673,-150,110,-1000,173,-314,-446,-1000,182,774,692,-1000,-696,-236,66,-122,879,-300,777,-389,-49,8,36,-822,1000,47,314,325,-322,774,1000,512,338,-142,-1000,-87,-230,-810,-387,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{914,784,-337,-916,-217,626,-168,547,608,71,-35,36,924,473,-222,640,-54,-500,98,807,-645,-348,866,-275,-1,-573,782,-756,505,105,-959,-419,-934,52,814,-938,-754,174,593,-915,566,675,-627,174,820,648,699,-519,-375,178,-900,456,-473,-372,137,-494,135,554,358,423,231,-683,-167,-878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-1000,0,1000,-22,692,0,18,-731,673,706,0,-310,-60,0,-458,1000,278,682,-1000,-1000,763,0,896,-235,0,0,183,161,-801,-1000,-61,413,-106,97,0,-16,293,325,-1000,1000,165,-1000,0,-298,-211,-440,-1000,455,545,-1000,-135,-490,120,1000,-448,424,1000,-692,-732,-311,-932,-899,-4,606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "org.apache.commons.compress.archivers.cpio.CpioArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-674,-566,540,-201,-10,480,-413,-313,-306,-150,-948,82,-353,804,156,761,231,604,270,766,-678,705,221,618,-61,447,675,-761,-216,292,-389,830,473,-430,107,75,-108,-580,127,895,993,147,-193,669,530,-415,891,382,-170,-786,-651,-558,443,-246,-15,65,750,-254,902,86,-996,-739,41,772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "close():void",
            new int[]{114,948,-706,714,776,-43,-663,460,-387,921,-650,-778,483,802,-167,909,229,484,884,791,-148,120,141,759,-224,160,901,524,-546,842,396,271,-153,-573,-557,5,12,-263,-753,187,-45,664,-983,-404,-782,-759,-792,-641,223,-423,-270,-975,-679,-480,247,-775,692,240,811,-15,780,124,-471,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "close():void",
            new int[]{435,1000,-1000,714,67,-379,-92,1000,149,1000,279,-810,366,-376,-1000,1000,-778,-473,751,164,-233,397,65,968,241,565,902,587,356,746,-1000,371,-153,668,-951,-136,933,-30,-667,87,402,112,-983,-404,-975,-851,-957,118,-325,-802,-1000,-1000,-1000,631,620,-554,705,-768,811,74,828,935,-1000,-215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-323,-444,-337,580,-888,149,-221,652,207,506,289,700,804,645,-63,-482,993,-732,-438,-102,-495,635,365,-784,-468,530,759,-183,-111,-126,29,426,-613,801,-174,140,308,860,265,414,929,420,290,-83,-767,322,-116,113,93,-649,695,7,526,207,-326,604,-77,311,-531,-664,557,-22,-538,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-113,-515,-560,-95,-312,1000,545,1000,455,1000,934,1000,-235,920,-943,-1000,180,-1000,-581,-558,-566,490,-413,-1000,-1000,327,1000,-972,784,690,-878,671,-1000,-127,-19,449,83,-707,1000,1000,1000,-595,319,-242,-939,654,-524,1000,-542,-1000,259,-1000,-456,1000,-546,241,126,-291,598,210,-752,-436,-743,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.tar.TarArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-768,882,-21,-951,-255,684,-331,-897,331,377,447,-558,826,153,482,-638,890,-600,-512,-687,804,210,-773,-748,145,640,-247,-454,-538,-122,-27,199,414,793,504,-181,739,-303,-261,-664,-105,549,524,-782,-750,-635,939,680,-706,95,270,-772,-511,-119,482,750,124,-745,224,351,-207,-409,733,119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.tar.TarArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{574,997,976,-563,-897,618,-684,208,-220,453,788,-683,901,-147,128,-611,-97,460,332,-383,367,-202,328,-149,-669,-310,337,355,121,773,-330,157,315,-288,-971,501,-682,61,369,-432,-479,-856,-149,316,-956,-175,-516,-932,661,-436,691,315,648,-205,215,-928,-953,310,-460,105,-914,90,-701,-663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "finish():void",
            new int[]{995,496,-714,43,-437,-21,995,898,212,456,311,-202,-378,956,-349,282,-608,-727,-656,259,381,-775,351,-23,119,431,80,962,-726,558,444,598,-810,817,-943,904,582,923,-642,-514,-374,-325,-626,-338,-118,-134,-209,-685,754,-683,89,154,-475,-504,583,755,394,-606,398,8,403,-873,630,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "finish():void",
            new int[]{645,-942,261,978,-249,164,-262,-331,-733,-171,-425,342,-747,280,789,801,-800,485,562,742,204,480,116,772,-810,-185,467,-597,991,-893,-549,199,-948,-484,234,181,641,-638,-650,997,-420,105,-732,378,709,-592,453,972,-409,-884,927,773,244,638,-509,33,-861,-556,-126,-173,-887,164,-606,-767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "flush():void",
            new int[]{921,45,584,522,971,126,698,45,588,-616,-201,557,688,629,75,466,627,942,626,-899,275,-481,-76,-417,-685,952,-23,-866,543,419,980,-144,-255,-206,799,648,553,871,261,117,556,-376,873,-900,918,-312,331,-302,-247,101,-985,-356,-381,-234,-535,235,-414,-959,812,-941,592,-585,-894,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "flush():void",
            new int[]{858,-884,-694,-486,639,403,97,174,-921,895,-184,-489,-803,721,48,-458,490,722,983,345,450,-328,213,-58,-328,-650,420,947,262,-953,177,690,7,700,606,127,-372,4,260,-939,-713,-444,-734,926,-489,-597,-485,-278,708,171,455,551,494,397,-58,-349,-743,621,491,-174,-478,367,-708,735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getRecordSize():int",
            new int[]{662,-549,-538,-26,339,538,997,177,-395,-685,956,-61,809,98,382,125,619,-750,583,63,24,735,265,-156,-619,197,-605,204,-419,243,-195,107,-541,309,169,743,221,-631,-858,221,-184,600,-833,-44,-16,114,730,-549,-30,837,706,977,-188,-266,831,-450,527,-292,-675,474,543,692,201,752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTEy", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "getRecordSize():int",
            new int[]{-744,-245,900,-313,-242,-671,-427,124,-569,96,500,943,-597,505,-19,-340,-392,421,-675,-707,-346,-31,512,-5,554,304,-459,-597,954,719,547,123,-495,297,321,321,673,102,68,7,471,-147,929,-731,-658,923,92,-547,-249,-438,-845,79,-108,311,-184,-854,390,-291,-67,414,-141,607,-469,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-621,-915,532,-856,-600,-772,145,-236,-84,319,-766,-560,366,689,-925,330,946,641,885,535,-510,-569,-788,-397,511,253,790,-976,534,-351,-184,934,-280,718,175,-923,-492,660,-547,170,-521,748,795,-731,-7,649,589,-535,274,-841,795,291,564,-99,305,615,-266,-726,170,443,301,-924,718,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{737,439,657,288,-509,129,289,-98,-605,-463,-398,522,-791,99,831,-128,-35,-219,-314,-748,291,-740,-106,480,-383,-622,547,-282,647,-419,479,697,357,-72,-556,587,477,486,178,-43,-74,-564,660,-365,-996,-930,-194,68,242,992,647,808,-184,314,656,-854,130,-595,-723,-431,790,835,-39,-266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setLongFileMode(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "setLongFileMode(int):void",
            new int[]{866,-2,815,871,-782,796,-825,-228,-999,462,-963,904,209,101,-519,-538,902,536,-792,558,-270,600,-850,218,307,558,-566,138,-299,-326,533,-895,-279,-335,-320,857,227,-819,-74,500,244,338,548,-456,-672,792,-2,-865,323,-115,4,119,-589,-999,38,-813,199,407,934,554,-513,-415,-845,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{320,964,-382,918,-913,-222,525,661,-979,199,207,621,623,-386,-812,895,-301,641,467,296,87,-955,-23,772,-677,-781,-482,282,-273,-771,-960,-584,464,-153,-965,-538,-704,958,711,-376,-87,259,-406,671,32,-721,-723,-585,768,373,133,-894,-835,-384,975,-900,-752,250,-98,931,467,-279,-144,-653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "org.apache.commons.compress.archivers.tar.TarArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{146,-834,358,-456,959,541,-821,-6,440,132,662,-858,-102,-549,70,-708,-887,-558,-933,-342,227,495,343,-17,585,-925,875,465,-443,768,-260,-470,436,936,-737,432,614,157,196,696,-479,144,633,931,-13,952,377,-284,-900,-81,-367,916,966,-971,-973,-933,262,141,-767,-749,-15,915,745,290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{603,-488,-1000,696,-921,1000,-1000,-896,621,350,322,-693,1000,-1000,1000,-832,-521,-381,-451,1000,80,-660,-384,-493,-507,-763,-1000,483,-1000,-1000,494,-39,201,-731,1000,-770,75,714,1000,1000,-455,71,9,1000,-82,-517,-137,-612,725,114,139,-892,709,-668,-1000,485,-977,-965,-826,-344,-492,277,-45,-653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{216,77,-412,585,-776,-231,107,-1000,-384,-692,-935,34,-644,200,-896,-111,-321,1000,-221,667,-156,668,-657,-1000,914,-127,73,292,1000,15,822,-401,1000,-718,-505,811,270,-689,263,207,830,528,1000,-432,611,596,403,1000,307,168,-453,814,-129,-1000,-30,532,1000,150,-352,727,-908,-918,203,690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{-1000,-1000,-1000,219,-1000,1000,987,174,1000,-653,-709,-323,1000,148,477,549,-1000,-1000,-1000,1000,-1000,-1000,-975,-1000,890,-142,1000,816,-1000,-1000,217,-4,-496,-1000,722,-1000,-112,-1000,-1000,1000,-919,812,-1000,302,-584,-107,-1000,-1000,1000,-1000,1000,-593,-363,598,-1000,575,423,435,-1000,-221,-880,892,1000,-827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{1000,-773,-947,67,201,-1000,-724,-576,-371,-205,-726,497,-1000,-693,267,488,1000,-558,-709,244,-399,-686,-312,294,-1000,-561,770,656,-1000,400,1000,-1000,1000,-179,603,-563,-222,210,-494,-1000,754,-867,935,-159,287,-574,-787,485,-1000,740,119,-703,393,-551,1000,171,4,-604,-286,541,-754,228,394,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-1000,-1000,-1000,-1000,-1000,-359,89,-921,72,-1000,-15,1000,-1000,1000,109,46,-1000,-1000,1000,528,1000,1000,-599,1000,1000,-151,-1000,-1000,1000,628,1000,-1000,-1000,325,-599,1000,763,-977,58,1000,-1000,1000,1000,-481,314,-464,-1000,1000,1000,153,-394,-354,-1000,-1000,-1000,1000,1000,-883,-1000,1000,351,1000,-971,987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{514,727,-366,-325,-480,1000,-503,634,972,218,486,-1000,412,-1000,-187,82,1000,583,36,-678,609,-1000,-183,-1000,516,-1000,-1000,776,-749,694,-560,-295,1000,-284,-506,-1000,706,895,452,-288,606,-133,-221,-1000,586,488,309,-1000,252,-480,1000,-419,272,128,-1000,-1000,-828,1000,835,-504,-1000,-304,977,807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-761,-589,-127,54,-400,17,798,425,941,19,-574,256,572,-643,-44,630,-116,-470,895,-909,-40,-613,-346,-520,1000,-309,-434,779,711,688,-810,-720,-408,340,-236,-691,1000,708,112,494,986,890,350,46,480,79,-667,-905,419,80,195,439,-458,708,-605,-640,451,132,663,-883,-861,782,590,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{45,-1000,-10,-395,-308,-502,149,636,-331,300,-222,-241,1000,-401,-84,1000,195,-1000,1000,-1000,52,-921,557,426,-477,7,-273,1000,880,931,-569,-1000,1000,797,282,-967,475,1000,720,1000,-1000,512,839,1000,630,-862,-141,61,407,719,-212,934,-500,289,9,454,769,-577,114,-1000,-1000,1000,-542,608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-1000,868,-280,633,517,439,755,-876,-78,790,-691,343,-306,-280,-710,-1000,1000,1000,73,1000,328,-485,1000,-1000,-1000,-1000,-952,357,442,1000,1000,1000,515,1000,229,832,-1000,828,-135,819,144,405,-1000,417,-168,-797,950,161,1000,-540,-356,304,-1000,723,460,-244,-113,-1000,-1000,-184,-585,317,-611,-47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{176,473,-966,-1000,-298,602,545,165,-47,-990,-1000,-546,26,992,-1000,-1000,-1000,1000,1000,668,649,1000,-672,-348,907,356,-1000,-1000,281,1000,-168,1000,-962,37,913,895,1000,920,-174,1000,230,-190,-1000,-703,-1000,67,1000,-1000,-547,-512,-624,874,-259,-1000,172,-232,-717,-1000,-779,-13,-1000,1000,229,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{1000,688,-820,1000,1000,-171,240,289,167,790,-691,-306,661,-325,-710,501,766,1000,742,1000,340,-359,-472,-672,-665,-1000,-405,1000,442,1000,1000,1000,-129,268,137,888,-152,1000,-495,1000,-845,-1000,-17,866,-332,-47,198,-1000,1000,415,-1000,361,298,1000,5,-1000,303,-1000,-1000,-426,223,1000,-611,431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-855,8,-528,-1000,-1000,1000,1000,429,1000,-1000,-550,-1000,501,1000,-1000,130,-1000,1000,1000,-1000,1000,1000,-551,474,1000,1000,-1000,-1000,-1000,975,-1000,1000,-1000,1000,1000,1000,132,563,-484,990,921,-455,752,-1000,-1000,966,1000,-1000,-886,-1000,-570,963,383,-1000,218,893,1000,-1000,-88,-509,-1000,529,781,442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{-673,630,296,-118,1000,102,944,1000,749,-823,-407,1000,388,-31,30,461,586,-39,-1000,864,59,68,-707,-709,-210,-1000,389,-421,-651,-784,-128,90,-402,1000,568,1000,52,5,-888,-843,-274,-201,816,-580,-1000,13,1000,-642,1000,-753,-39,1000,-1000,-138,-701,-536,-788,562,-1000,-815,-721,-1000,-701,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{463,-103,-476,926,1000,-422,-1000,-483,-1000,1000,-177,-1000,-125,-8,980,1000,1000,462,1000,-669,-1000,-1000,1000,1000,-128,1000,1000,-318,454,1000,710,749,494,-86,131,-775,-1000,-891,225,1000,-968,86,-378,1000,657,-162,1000,-802,-1000,96,929,333,-1000,1000,-1000,1000,402,94,1000,1000,-1000,1000,216,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{114,-1000,-1000,101,-1000,764,431,-41,-954,-518,-1000,-630,-476,955,-186,165,-345,-100,1000,-1000,-596,-965,591,709,-559,1000,-592,-279,466,-434,-500,355,1000,1000,-405,687,892,491,-1000,-340,302,477,1000,334,1000,-256,1000,-763,-1000,528,572,-349,-1000,645,-1000,93,133,-570,1000,-1000,-597,-1000,-626,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{-213,-246,-1000,101,-991,-141,-1000,-26,-866,582,-938,-366,-99,352,162,50,-156,-290,896,-966,-829,-567,454,1000,-53,1000,246,-413,466,808,-424,-848,641,465,-119,515,998,-225,-1000,-240,-306,-77,-378,334,747,-736,1000,-670,-893,312,-103,-227,-836,190,-981,-120,-44,-553,903,404,-651,21,-649,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{-1000,1000,744,210,1000,1000,-866,881,750,-201,-1000,570,-1000,596,-1000,-285,1000,245,190,-507,1000,1000,-1000,1000,-575,1000,-636,-1000,1000,-426,-1000,143,-1000,1000,102,-1000,-966,-624,-1000,550,-1000,818,-691,-159,1000,1000,-1000,-1000,-329,-382,-1000,-609,-1000,920,-1000,575,860,738,-211,-797,-767,-641,-192,299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{-452,-283,564,849,1000,-29,-834,31,-514,310,-797,746,-1000,1000,-1000,582,190,-1000,382,347,-44,1000,314,289,1000,1000,-432,-1000,406,-1000,-1000,1000,-292,651,906,-65,400,-1000,-703,-165,1000,1000,-1000,431,260,833,628,119,768,-6,-654,-467,-1000,192,-355,1000,789,25,46,-965,-1000,353,316,766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{896,319,-596,558,734,1000,-42,-643,-365,806,361,844,-436,589,1000,-14,-216,1000,-295,-474,223,754,-704,1000,204,1000,-977,339,-282,53,-975,688,321,1000,896,810,706,164,14,-1000,635,86,266,-813,-1000,-477,-760,1000,-1000,676,-573,500,729,-162,-1000,697,448,834,711,1000,1000,-889,-1000,-610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{-16,540,1000,1000,1000,247,-788,954,550,-763,-1000,753,-1000,1000,591,598,1000,-1000,-625,-179,-199,1000,-739,430,1000,1000,1000,-1000,533,53,-1000,1000,-183,1000,213,-1000,1000,-1000,-578,-1000,88,1000,-1000,-282,1000,1000,-619,-1000,1000,-256,-1000,-944,-1000,1000,-226,-1000,1000,1000,-1000,-1000,-1000,-886,1000,166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{-346,523,-496,892,382,400,1000,638,-267,480,-420,-991,1000,653,969,-201,481,47,1000,547,268,600,-259,322,-275,400,1000,960,73,55,-400,586,-209,-1000,422,262,-1000,802,-463,-175,-1000,-459,529,787,761,622,-859,-1000,-297,-444,-365,-185,-704,655,86,-702,744,-759,170,-213,-1000,-428,-575,-174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{1000,1000,-1000,1000,-513,297,1000,69,205,1000,151,-280,-1000,1000,1000,-285,717,682,1000,-525,292,1000,31,367,-375,-28,1000,-123,-635,-942,-152,1000,-414,-700,-569,-1000,54,770,-1000,-1000,486,1000,-1000,-524,640,-357,-1000,-1000,-1000,-1000,609,-145,1000,878,614,-892,-223,-59,-1000,-583,-163,-724,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDA=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "getEncoding():java.lang.String",
            new int[]{-792,310,-870,1000,-1000,-1000,-616,1000,-932,521,136,1000,154,-109,-1000,856,-1000,-1000,1000,-729,1000,-1000,1000,-989,491,-1000,-1000,-40,-713,780,159,536,-917,-773,-28,-71,666,-995,-932,-475,-1000,-390,344,-1000,-943,-1000,1000,413,-586,-1000,-1000,1000,-902,1000,-48,5,-1000,599,-794,956,-972,702,423,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:VVRGOA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "getEncoding():java.lang.String",
            new int[]{1000,-847,264,1000,-606,-843,-869,692,1000,-607,480,692,774,1000,750,1000,478,-113,-837,-1000,114,-450,9,-1000,-1000,670,206,-483,1000,-190,1000,-1000,-998,250,531,1000,89,393,-24,-1000,33,1000,371,-1000,491,170,-710,640,-1000,628,654,1000,18,-1000,-376,237,-395,-460,1000,711,837,-269,-888,-33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.String:VVRGOA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "getEncoding():java.lang.String",
            new int[]{792,53,-191,246,810,-594,-1000,-920,79,-334,537,-542,-596,987,1000,255,939,-306,-381,-1000,401,-621,-495,-1000,-935,-1000,-688,257,1000,308,686,-1000,1000,1000,-137,65,216,-1000,-621,-1000,-651,1000,521,-107,400,1000,-506,693,689,-272,-1000,-140,1000,-739,-994,-439,-361,-193,803,39,-230,1000,791,831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.String:Kzc0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "getEncoding():java.lang.String",
            new int[]{1000,-257,-356,-353,-267,370,383,780,100,44,-428,-74,574,-847,-1000,88,-925,539,-36,52,-374,467,-329,970,-1000,1000,587,-850,-382,-1000,256,888,-143,136,891,84,-757,-658,-651,-1000,-127,20,80,-196,-443,-111,11,603,172,-304,332,-269,-1000,-937,361,-1000,444,498,269,430,-28,268,-807,-754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "isSeekable():boolean",
            new int[]{-308,-452,364,1000,1000,634,163,-436,202,1,1000,366,-124,-90,84,-1000,-1000,696,-453,-63,-1000,1000,-812,848,-919,-754,164,250,-706,288,121,-1000,-301,74,-128,-662,293,739,299,-648,-321,-1000,-25,-194,-726,1000,-253,-1000,-1000,892,226,-262,551,440,123,859,-193,43,-107,1000,735,-19,-729,768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "isSeekable():boolean",
            new int[]{-607,-689,409,88,773,1000,277,113,839,-850,469,-83,256,629,1000,-1000,624,1000,-464,1000,210,-655,-743,-31,760,193,790,-330,334,-141,1000,435,-582,-349,-167,584,298,-1000,-1000,-435,310,-1000,168,-198,-457,-81,-1000,-863,-136,-548,-255,-170,-1000,-1000,1000,1000,389,-936,723,721,673,1000,746,330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "isSeekable():boolean",
            new int[]{-1000,483,54,-324,-386,830,154,-209,279,251,-442,-792,517,431,-773,631,725,623,159,-1000,687,-45,-98,650,-1000,504,-718,-441,325,327,-1000,-549,-24,824,208,-838,-28,-81,1000,560,-823,668,73,-782,111,33,-526,834,333,543,-197,811,-1000,1000,-140,530,-801,392,-812,53,431,-42,-745,-724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "isSeekable():boolean",
            new int[]{-308,-452,364,1000,1000,1000,163,-436,202,1,1000,366,-124,722,84,-1000,1000,696,-453,-1000,-1000,-708,-812,848,-919,-754,-1000,245,-706,288,121,-1000,-301,74,-128,-662,293,739,299,-648,-321,-1000,-25,-194,-726,1000,-253,506,-1000,344,226,-262,551,440,123,859,-41,43,-107,1000,492,-19,-729,768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{1000,1000,800,809,653,-1000,789,124,-495,507,-1000,370,-355,-686,-1000,-45,-583,458,-101,-1000,860,-713,-550,-332,-488,1000,-243,-947,-351,1000,301,256,116,334,1000,167,-981,-186,967,102,964,400,434,-1000,879,-541,229,-689,453,-400,180,1000,187,-288,-107,181,1000,-103,-394,688,-415,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{467,-393,639,-127,812,-479,347,-124,-577,-906,-677,319,385,1000,-50,1000,510,-608,117,154,852,-1000,-1000,59,-285,-61,-1000,-365,718,-1000,365,-265,1000,1000,-366,737,-743,14,-28,1000,998,-20,1000,1000,1000,417,-86,-120,671,-64,-230,-174,35,-1000,-382,-143,615,-693,1000,878,17,-962,-1000,123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-108,-354,562,809,-989,400,-263,-351,-327,-1000,-254,-319,736,524,-281,1000,-1000,-121,-92,400,465,-1000,406,631,681,-788,-1000,400,853,-967,1000,-35,-372,1000,-136,164,-1000,1000,-400,929,785,-820,902,965,404,611,-171,863,1000,1000,-400,645,-206,-1000,24,-223,632,-535,1000,761,-413,381,-612,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-132,1000,484,760,1000,1000,-732,-656,-186,141,61,-1000,921,444,-1000,-353,389,-31,633,236,332,822,258,-776,-426,-813,-464,618,52,-780,1000,1000,-1000,434,76,-830,-267,315,-534,652,284,-1000,406,937,-434,1000,325,664,-865,-399,109,256,711,-720,-347,-69,232,-1000,417,-220,372,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setComment(java.lang.String):void",
            new int[]{939,324,688,463,-578,628,-485,-973,392,-188,450,-769,-574,-529,-207,849,-481,-471,800,-238,251,873,404,-134,791,-219,-16,-170,774,203,-697,326,-820,-434,950,243,114,472,366,-665,-355,359,-170,-210,-292,22,-308,280,-501,-259,544,-807,-260,685,50,948,739,-748,-295,586,-732,-948,-81,-886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setComment(java.lang.String):void",
            new int[]{490,-323,359,998,-1000,-816,626,560,217,421,1000,-878,-123,58,400,1000,-529,-1000,478,-856,441,-484,400,-1000,96,458,142,179,-400,-1000,373,530,1000,-1000,-657,-74,412,847,1000,-1000,846,-4,850,-396,641,-212,-639,69,-697,-216,614,-316,-673,-224,-945,37,-241,-1000,-273,189,-46,-545,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setComment(java.lang.String):void",
            new int[]{677,-771,704,-1000,913,552,534,-103,-1000,-1000,345,805,370,86,-868,-168,676,839,-439,-538,-414,-551,-479,-354,-158,1000,505,-554,-1000,-49,-607,111,227,-274,-548,-1000,-103,-170,-1000,90,769,-803,-171,1000,-553,-1000,1000,442,1000,589,96,823,1000,-943,1000,-1000,-87,-307,1000,-575,-59,241,709,170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setComment(java.lang.String):void",
            new int[]{455,1000,-916,-478,-1000,295,-717,-87,575,578,-462,-1000,-48,776,155,1000,-611,-276,620,897,-854,-2,2,1000,-34,-453,-1000,150,157,636,-213,-140,-164,375,1000,1000,-285,-179,556,-233,-1000,-978,-628,-447,755,503,-641,831,-651,-458,643,-1000,-821,463,-417,-464,1000,-1000,-813,110,-1000,-23,-357,-698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setCreateUnicodeExtraFields(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream$UnicodeExtraFieldPolicy):void",
            new int[]{-177,400,400,12,-440,-244,-74,-681,-35,-869,-3,255,6,965,-192,-649,-560,-684,78,-24,120,-1000,-212,368,-1000,-1000,382,1000,334,1000,636,-216,-796,565,803,747,623,-446,29,146,1000,-541,216,-615,125,1000,-127,-1000,300,-452,-957,854,297,-589,-257,960,54,11,-202,-288,-81,768,-479,23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setCreateUnicodeExtraFields(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream$UnicodeExtraFieldPolicy):void",
            new int[]{1000,-489,-561,1000,641,844,625,-440,806,664,459,1000,-275,-565,680,991,70,-895,733,882,222,-270,1000,5,7,-358,1000,-397,693,-1000,-795,1000,435,694,-532,-617,685,1000,1000,505,-450,-1000,216,-175,286,1000,786,860,-1000,-1000,-1000,-910,-1000,-4,-526,1000,-82,906,920,-721,-3,1000,591,163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setCreateUnicodeExtraFields(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream$UnicodeExtraFieldPolicy):void",
            new int[]{799,-990,860,664,697,987,-51,674,1000,-402,240,1000,-482,-483,-386,599,33,-250,503,824,-1000,-354,-286,1000,-940,629,1000,-965,958,-1000,-730,-162,1000,573,-1000,-1000,683,1000,906,1000,-450,-1000,287,812,1000,1000,-273,874,1000,999,-938,1000,-1000,-76,-1000,646,-641,1000,361,-1000,-211,1000,724,-410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setCreateUnicodeExtraFields(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream$UnicodeExtraFieldPolicy):void",
            new int[]{1000,-489,-561,1000,-310,423,424,508,841,372,-590,593,303,-170,768,810,-316,-913,733,768,784,-842,1000,-204,-391,-811,1000,-50,1000,1000,-795,1000,103,1000,186,-177,685,662,1000,804,-25,-885,33,-175,454,1000,786,-84,-1000,-815,-1000,-147,-1000,-1000,-225,1000,-225,906,920,-721,-292,1000,591,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID|getEncoding=java.lang.String:MjE5ZTc1Mw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setEncoding(java.lang.String):void",
            new int[]{-16,108,100,1000,926,600,-223,343,-1000,-350,202,-850,-843,-83,-799,-746,-219,-544,753,690,-115,-88,55,-8,78,688,44,523,751,1000,-475,1000,-861,818,-539,-970,54,305,-648,-974,136,567,874,1000,408,-833,179,-192,-1000,532,60,347,940,1000,100,695,218,864,-648,-580,-999,756,739,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID|getEncoding=java.lang.String:MHg4MDAwMDAwMA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setEncoding(java.lang.String):void",
            new int[]{610,301,678,368,-1000,-1000,-236,-198,1000,450,62,-1000,1000,-153,20,-773,167,-683,-738,-189,150,-136,-193,1000,624,-661,485,-1000,440,-670,168,-943,-277,-773,650,613,284,-128,1000,351,610,-1000,545,-27,-344,429,412,-248,-700,-705,502,-112,-204,1000,-97,-192,-93,-568,-1000,1000,290,-96,-151,572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.nio.charset.IllegalCharsetNameException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setEncoding(java.lang.String):void",
            new int[]{203,731,-192,-177,937,1000,-381,-877,-630,-757,-869,926,838,-1000,-1000,298,-1000,443,1000,-560,47,325,1000,-1000,180,1000,-1000,970,-1000,557,990,1000,-474,698,-820,-1000,-598,203,-1000,83,930,137,16,1000,-205,956,-923,334,1000,1000,-131,99,1000,-1000,-96,128,-1000,572,1000,-993,-211,749,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("VOID|getEncoding=NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setEncoding(java.lang.String):void",
            new int[]{-314,-778,640,-1000,-517,-33,-527,528,963,877,1000,-207,30,565,952,367,-864,694,-242,530,740,122,470,-561,-1000,1000,-554,-2,-412,824,-402,656,-225,-309,1000,-797,514,-88,-164,-301,125,-1000,-438,645,-198,-263,-92,388,364,368,-621,240,-1000,512,1000,1000,655,-226,-1000,-337,-669,-453,-839,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.nio.charset.IllegalCharsetNameException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setEncoding(java.lang.String):void",
            new int[]{609,-1000,-164,1000,-697,557,-187,64,-204,464,146,-1000,991,217,-322,-49,-374,321,-741,-1000,-545,521,67,145,-1000,-364,-412,-109,1000,1000,6,630,-1000,-64,557,522,-207,-959,-1000,-1000,-425,-285,1000,615,1000,-1000,390,895,1000,-1000,-496,1000,370,248,-718,-86,69,297,-635,-832,419,748,174,-918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setFallbackToUTF8(boolean):void",
            new int[]{648,636,-764,66,-970,-986,-331,-736,-429,275,703,477,243,-832,68,-220,-262,-154,-256,625,-236,-665,-584,33,602,655,-377,446,-781,-355,381,-136,473,157,174,-287,-275,377,-519,-359,-826,305,341,626,-498,-56,400,1000,-620,-821,453,60,630,980,157,380,-1000,-686,302,1000,514,-358,158,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setFallbackToUTF8(boolean):void",
            new int[]{-23,-437,64,-859,301,93,311,712,551,-1000,703,-946,-792,554,-648,1000,1000,26,531,1000,1000,834,-584,382,1000,-1000,569,1000,715,-355,-584,-319,488,-707,174,1000,-275,1000,1000,529,538,367,1000,-26,-1000,-1000,-1000,86,1000,331,1000,177,-842,-623,112,-1000,-1000,-1000,-356,-267,828,-358,-1000,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setFallbackToUTF8(boolean):void",
            new int[]{-130,34,190,-885,289,-330,562,640,430,-346,9,-923,-19,501,-32,1000,1000,-317,1000,632,662,692,816,1000,842,-383,746,1000,593,1000,-489,97,405,-582,-333,561,-30,923,82,-540,185,850,1000,-391,-871,10,-1000,460,741,230,620,399,544,827,-174,-724,-1000,-348,213,130,278,374,-1000,3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setFallbackToUTF8(boolean):void",
            new int[]{1000,1000,1000,-398,-340,240,889,-10,908,-315,-948,-791,812,808,-794,622,1000,-317,-1000,-945,1000,-816,1000,110,-266,1000,-46,137,-304,314,-1000,-316,1000,-13,322,939,-294,450,-27,406,1000,97,221,-1000,-446,-471,-428,460,1000,-141,-59,351,-654,211,284,355,-333,616,1000,-673,59,24,915,513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setFallbackToUTF8(boolean):void",
            new int[]{-44,-50,1000,-58,-276,589,1000,-653,820,275,-143,-1000,-427,379,847,893,1000,1000,-190,408,1000,236,-584,33,602,502,-206,-617,912,1000,-88,-136,123,69,177,-972,997,-1000,542,267,-292,-155,362,-135,-498,-56,400,-1000,1000,-821,508,-561,-100,123,-316,380,-10,415,302,-809,-114,-665,1000,-817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setLevel(int):void",
            new int[]{-708,-400,-708,534,-253,418,968,-402,-264,280,-244,256,688,412,1000,264,825,-75,-951,-782,-899,-1000,292,-713,856,-907,-1000,-1000,519,-700,634,-400,1000,141,-1000,-878,-319,-849,400,-504,-61,-1000,-973,-400,648,1000,-506,-583,133,400,-81,618,598,898,1000,281,-108,94,1000,-620,1000,-1000,-231,-882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setLevel(int):void",
            new int[]{-635,77,439,-601,94,-361,-1000,-318,97,-312,-680,352,-74,75,533,402,-1000,-498,149,-152,1000,-144,-1000,-398,-359,764,1000,277,648,-134,180,615,-1000,713,-1000,1000,-137,-721,129,901,41,22,214,288,-299,-221,-1000,103,-472,-971,-970,-973,-598,-120,413,195,-740,690,-485,411,-754,1000,773,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setLevel(int):void",
            new int[]{694,-977,14,1000,1000,1000,619,-795,594,-658,277,-161,658,1000,-497,698,788,387,-753,-156,-881,826,686,97,-623,-1000,63,-1000,951,-357,-909,-229,726,196,649,-1000,417,-749,298,-874,-717,116,188,64,1000,1000,-807,829,104,285,-822,110,79,370,-817,-329,-908,-379,-985,496,-107,-627,-269,-878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{331,186,-842,-131,-1000,1000,40,1000,-326,-827,539,-150,615,669,933,492,131,11,491,-984,1000,-1000,-893,-47,-388,1000,-353,234,413,661,1000,1000,401,-768,1000,-357,1000,-436,170,-1000,335,1000,-633,15,-235,1000,1000,669,1000,-331,-942,390,552,-132,900,-978,234,-155,-536,-282,345,441,293,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{-236,979,539,1000,-1000,-382,-398,-1000,-750,242,-1000,337,-779,-1000,258,-482,-167,9,-951,1000,-10,1000,731,-91,11,-677,440,1000,1000,-402,1000,1000,-1000,945,-569,-43,640,1000,83,563,-689,-309,-809,665,562,-1000,-935,574,-875,1000,-1000,-746,-961,-1000,241,-652,-1000,-843,-1000,786,-499,89,-264,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{907,-463,-956,207,42,-89,-476,-237,-660,-892,1000,-803,-52,124,552,142,-98,1000,29,-643,961,-504,-20,-558,698,679,-68,-679,149,1000,119,-1000,-500,-316,1000,-545,-368,-331,539,799,549,632,912,-110,-46,-603,1000,-455,639,824,445,1000,1000,-280,-1000,1000,-566,-530,1000,-450,299,-984,281,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{1000,1000,-1000,-234,480,1000,-611,899,-225,-1000,1000,-47,683,944,-1000,934,-1000,1000,-1000,-1000,1000,-1000,-470,-110,698,952,193,-946,149,489,1000,-1000,1000,-642,1000,-848,188,-331,-892,-409,549,316,1000,-232,1000,-437,586,1000,1000,-1000,-1000,1000,1000,-1000,1000,64,-566,-912,-1000,-105,810,311,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{1000,-523,-486,-985,-689,639,-611,-334,-1000,-452,819,-886,-1000,-509,491,-281,-753,-502,-530,-1000,1000,-811,493,-829,808,-32,193,-103,918,1000,960,-1000,-703,-1000,824,-712,604,-303,151,30,204,316,586,304,251,-799,577,213,648,824,-451,100,300,-468,-504,64,-687,-1000,565,-170,-414,-514,331,581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseLanguageEncodingFlag(boolean):void",
            new int[]{-1000,-1000,288,564,436,-1000,-591,72,-475,627,1000,-106,-885,-452,-430,-1000,-1000,-973,-1000,240,895,-301,-740,-1000,1000,1000,-404,-127,-738,-285,1000,-644,-129,-402,1000,-547,1000,639,1000,88,-799,-1000,-1000,706,396,-596,895,-339,1000,970,752,-78,697,-838,-27,1000,-202,-1000,-346,910,-25,1000,1000,-296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseLanguageEncodingFlag(boolean):void",
            new int[]{282,-557,-51,314,211,389,211,-668,-59,-381,307,-13,-719,-848,-793,-74,149,282,654,715,-577,1000,902,102,1000,-149,839,-403,276,769,-1000,1000,-946,104,-371,-1000,-508,-403,1000,-483,849,-298,-1000,-1000,851,189,400,-511,-667,79,-753,899,-738,-1000,817,1000,599,1000,27,105,1000,54,-397,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseLanguageEncodingFlag(boolean):void",
            new int[]{-443,-845,309,228,192,-1000,-127,-196,-513,-105,1000,247,-30,-757,243,323,-1000,-26,939,310,-110,809,531,-338,268,779,170,33,-766,552,-1000,1000,-923,-279,1000,-1000,1000,95,1000,341,33,-1000,-551,-548,623,-277,1000,-301,845,298,-288,827,813,-771,739,982,-85,-50,-115,1000,161,1000,1000,557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseLanguageEncodingFlag(boolean):void",
            new int[]{111,387,-51,-516,-278,-731,759,-297,679,31,-53,352,136,194,1000,458,149,875,654,-3,-553,-89,523,-893,-652,80,317,-596,-45,-498,-1000,1000,-535,793,515,-1000,211,-398,-81,485,859,596,285,-639,114,238,-309,-32,204,-1000,-168,101,-169,203,86,1000,442,-688,749,-874,-282,910,-7,96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{1000,-1000,110,445,133,-242,-934,28,-61,-243,119,126,40,-343,1000,1000,-1000,160,-255,-30,223,1000,18,114,597,392,-203,-1000,-509,-264,-1000,-1000,398,-11,626,1000,-564,-613,1000,-1000,-865,-816,-157,431,-1000,-1000,-292,-1000,-176,1000,1000,-1000,-1000,-400,-1000,54,-373,634,671,646,86,-1000,244,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-1000,737,-16,-64,833,-193,-976,-1000,-38,116,-250,1000,-57,-206,-385,442,879,-1000,58,-58,-769,189,767,-27,-280,-908,603,-674,207,881,-737,-683,-266,720,572,-265,183,-1000,563,817,-480,-1000,572,1000,1000,1000,557,-1000,912,-400,-747,772,-482,144,-47,-682,165,571,1000,773,78,56,-1000,190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-324,-1000,-1000,229,1000,-513,299,-541,1000,-724,1000,216,697,-1000,-1000,1000,151,-371,1000,1000,845,225,-1000,-643,1000,-257,196,-431,901,-916,-1000,-1000,1000,1000,1000,-271,-432,-242,573,85,-758,9,209,1000,-701,-1000,1000,1000,-991,1000,-400,-579,-1000,-1000,-239,-496,457,-160,-1000,1000,-15,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-531,-963,-116,-457,771,-728,-583,-862,-175,207,-959,149,-275,-588,-217,-400,-16,-811,263,130,-102,385,400,-308,2,-585,120,-474,1000,-1000,-850,-683,923,410,413,619,194,-773,4,240,-160,-697,153,559,684,400,-232,-400,-1000,-267,-349,-231,400,400,73,-174,109,416,548,1000,-836,-113,1000,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-14,-963,-211,1000,529,-248,-599,-890,1000,-1000,982,-944,-524,-167,1000,-808,1000,-17,-1000,-1000,1000,-451,679,1000,1000,1000,-125,1000,56,-951,819,-989,987,-400,274,-1000,1000,-1000,1000,-878,-1000,556,-71,128,-728,67,-86,-561,-601,524,-240,-1000,651,149,-1000,517,-326,132,33,-603,-1000,107,221,-93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-938,-742,839,-44,977,-643,666,794,609,-355,-1000,392,459,-952,429,571,1000,443,29,-510,581,-255,-430,-878,314,1000,1000,353,1000,700,1000,291,525,1000,1,17,959,-812,665,-1000,-1000,505,-701,61,-122,632,681,-1000,1000,978,-947,1000,28,616,920,760,1000,241,-147,-894,863,251,-230,871}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{81,-490,-426,711,388,-265,-241,88,739,-80,1000,-1000,1000,687,875,-1000,1000,597,-1000,-1000,1000,-1000,1000,1000,33,637,-785,1000,337,-1000,566,-918,1000,1000,-331,-1000,1000,-255,1000,-1000,-1000,676,511,-681,-264,-70,-22,-197,-807,1000,-390,-982,760,48,-1000,-1000,766,684,-1000,-344,1000,251,688,408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{983,-487,-136,388,328,-166,-1000,513,364,371,164,-619,1000,1000,-1000,-433,786,481,-677,-740,1000,-1000,964,-1000,868,678,1000,520,-188,517,741,-719,905,-1000,-1000,-823,901,-12,130,-1000,-584,450,164,1000,-955,770,1000,-1000,-916,516,-1000,-75,-75,126,-422,196,-1000,-699,-642,-432,597,1000,-433,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.changes.ChangeSetPerformer", "org.apache.commons.compress.changes.ChangeSetPerformer", "perform(org.apache.commons.compress.archivers.ArchiveInputStream,org.apache.commons.compress.archivers.ArchiveOutputStream):org.apache.commons.compress.changes.ChangeSetResults",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
