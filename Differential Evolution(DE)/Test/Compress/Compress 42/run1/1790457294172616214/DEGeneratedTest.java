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
            new int[]{793,-548,-1000,640,402,-1000,184,387,-422,477,-292,293,-581,-968,564,1000,578,-924,-1000,1000,1000,930,-1000,405,1000,67,580,-1000,-323,164,-529,-792,-1000,-611,-543,1000,992,1000,1000,697,-35,97,497,850,-488,774,-322,-271,-665,-1000,1000,-5,-291,-270,-493,-514,-60,-902,-1000,306,110,-259,-1000,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{689,399,1000,-385,1000,-148,-1000,-1000,-78,195,478,-447,-227,-373,1000,311,-479,294,-164,1000,-60,598,-799,-154,-1000,1000,493,-1000,494,-74,728,1000,967,-1000,-640,1000,-521,1000,-1000,-984,-797,-1000,-980,-589,-1000,922,820,150,-468,-909,-951,-88,-1000,1000,-935,-827,-986,-676,-604,1000,-366,-101,-1000,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-885,1000,1000,1000,-131,839,-341,-469,1000,954,1000,-882,-453,196,191,482,-1000,191,415,-65,-246,-422,705,-272,-1000,-192,-1000,-451,1000,-60,213,-134,1000,-183,1000,678,11,-52,-1000,930,1000,576,-13,-960,-362,192,-514,-52,51,-446,-25,-400,8,-1000,1000,-313,-1000,1000,1000,-49,-746,713,-25,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{352,1000,1000,-191,-763,1000,-516,-848,1000,683,1000,-1000,293,-46,136,-119,-1000,-867,-292,-638,-1000,-1000,798,-1000,-1000,57,-748,-294,479,-337,350,859,767,-183,537,606,-193,-737,-1000,-194,508,466,-258,-1000,-357,-248,-1000,-928,-354,-366,-756,1000,-20,-1000,1000,430,-530,1000,427,-87,-1000,1000,106,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-144,1000,194,545,739,443,689,-1000,673,169,876,-886,539,904,782,368,-1000,-427,65,-874,564,231,1000,-257,-1000,-1000,152,-777,1000,586,-67,-63,1000,-559,-178,1000,385,754,-1000,457,-59,-243,-694,-951,-1000,389,-730,25,-353,-119,180,457,265,-128,-464,81,-806,342,82,411,-1000,475,-715,-759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{207,160,-677,640,334,419,184,-986,323,477,234,-1000,-53,1000,481,-57,-765,-601,-680,253,1000,919,367,555,315,1000,1000,-1000,1000,-314,-57,-792,669,-1000,-640,950,-175,1000,-1000,-32,-361,-327,-28,-252,262,774,-672,25,-329,321,-896,1000,338,525,-493,-346,278,-717,-1000,430,-1000,444,-1000,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{370,579,1000,1000,-665,839,-439,-626,673,412,1000,-886,-1000,-494,390,482,-1000,-665,-1000,181,-744,-385,76,114,-1000,129,-665,-777,1000,-646,543,11,427,-1000,1000,897,463,22,-801,718,377,-56,213,-673,-800,34,-574,-689,-119,-446,180,1000,-113,-1000,324,-374,-1000,-35,513,411,-763,107,-814,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{93,-1000,-1000,-465,-625,-1000,989,-527,125,-982,-862,-656,-118,640,-135,60,635,-1000,-875,282,203,629,-911,658,1000,1000,1000,-637,399,-308,-205,-634,574,400,-1000,-400,691,20,-480,-257,11,620,388,437,869,-92,-565,-437,-627,-109,-717,1000,-207,-140,-443,-456,790,-593,-955,52,-836,-341,-278,-721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-1000,908,429,216,-1000,839,1000,20,1000,-54,618,-886,-1000,878,-927,686,-1000,546,-318,-18,268,-385,286,844,102,657,-660,-187,446,-56,-458,-745,287,-807,131,-71,419,-305,-170,776,78,751,578,1,-800,453,809,232,-57,520,-171,1000,545,-1000,506,-705,-1000,566,-1000,-363,-422,633,745,-957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{644,143,-628,809,1000,241,-148,59,451,1000,1000,619,-624,916,652,-320,-1000,-727,296,612,-938,-712,-609,-891,1000,114,-781,802,352,748,-30,719,879,476,-104,-826,-233,-1000,-667,429,115,-758,-1000,453,-1000,954,-906,740,-30,1000,-566,706,269,1000,-570,1000,878,-165,-220,279,762,39,-476,303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{870,-788,138,144,118,569,-540,-400,970,-1000,-132,805,-203,-400,1000,342,-361,-908,184,-110,328,-400,-909,-598,142,-1000,84,790,1000,748,766,908,590,-345,-222,324,-636,-822,-396,-65,1000,259,-1000,-478,-1000,-229,380,182,140,359,-1000,315,212,-885,232,294,212,879,51,-610,-358,-1000,-268,538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{728,939,400,1000,751,-399,763,18,106,413,-166,195,-251,378,-393,-1000,914,1000,-377,-703,-668,195,473,-794,-529,315,-386,-184,1000,-172,1000,506,-977,-1000,195,-43,-400,1000,-1000,566,123,-1000,-471,400,-1000,-142,510,-427,-930,104,153,-102,-1000,-1000,422,-1000,-506,318,420,-343,-1000,-472,276,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-121,1000,1000,-1000,-1000,1000,-641,1000,37,-1000,66,-1000,-1000,1000,-1000,-452,65,-605,1000,1000,-1000,1000,723,1000,-1000,1000,-1000,-1000,-1000,862,-321,-301,985,1000,-1000,1000,1000,-488,247,1000,-1000,1000,1000,1000,1000,-1000,-1000,1000,1000,453,1000,-500,1000,-1000,-148,808,-911,-1000,1000,1000,357,-724,1000,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{1000,-1000,-1000,11,1000,38,-356,-254,1000,-168,-132,769,53,916,1000,-498,-361,-735,-309,-382,362,-1000,-1000,-1000,1000,-25,-678,1000,1000,572,1000,711,505,-712,405,-72,-1000,-1000,-1000,-464,1000,-305,-1000,-861,-692,805,399,1,28,359,-1000,437,-300,515,267,294,625,1000,-802,-1000,561,236,-476,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{401,1000,1000,-1000,-1000,901,-487,-17,37,-755,-726,-313,-902,226,-218,-459,-481,-420,600,1000,-957,716,-39,330,-409,746,-1000,211,-860,1000,-1000,465,1000,1000,-1000,474,226,-645,12,959,-853,1000,898,1000,537,-597,-1000,1000,547,5,738,339,1000,-448,-457,574,-186,-778,916,211,1000,779,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-481,1000,1000,-462,-773,1000,-96,-842,-324,-200,333,253,-1000,-1000,-689,-984,125,759,-342,697,-708,-664,183,152,-326,757,-1000,-636,-876,1000,-1000,-250,989,494,-364,869,-1,723,-536,-788,205,1000,702,471,797,843,-1000,-691,96,5,1000,76,1000,-199,456,193,581,-102,588,872,756,707,1000,974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{28,143,-14,-591,-828,311,-1000,-34,743,-307,1000,644,1000,807,-1000,-299,-184,-850,239,1000,-1000,1000,-147,604,-44,1000,-797,-168,-950,298,-1000,-266,1000,1000,666,521,857,-1000,504,456,-1000,1000,422,-203,219,1000,-1000,840,806,-568,-95,-349,1000,-559,219,1000,750,-853,-24,-659,1000,890,1000,991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-266,840,446,-794,-881,788,-611,-91,-804,22,254,546,-401,-288,-219,-151,370,-836,758,-487,-490,-200,485,300,-41,145,-805,-445,-609,724,-649,-60,-870,128,-340,869,-18,951,158,-202,778,743,774,339,43,735,-574,-64,358,-348,872,-209,703,-387,295,-353,343,-108,-67,63,679,50,915,905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-429,-1000,1000,-473,-127,275,-571,-998,130,384,588,272,597,129,-462,-1000,-225,479,-775,-103,957,-1000,167,256,237,-893,-603,-106,-120,1000,-390,-293,141,-673,1000,615,-205,972,305,-635,-1000,59,-1000,853,110,642,476,580,319,-249,-11,80,-145,-809,494,1000,230,1000,-20,-650,358,-24,-548,393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-127,195,-1000,-914,-621,216,-1000,-1000,-87,1000,598,954,626,608,-1000,-1000,542,-378,-1000,619,-367,-818,-1000,71,-1000,-309,-1000,345,-183,-1000,-61,265,548,-826,545,259,731,663,825,-30,103,-302,1000,1000,-87,429,-767,649,702,635,1000,13,-1000,-1000,-21,618,1000,318,171,-746,382,758,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{1000,-1000,-788,-1000,-1000,760,-571,-885,178,1000,543,-849,1000,-1000,788,-1000,241,1000,-716,-616,-1000,-423,-863,1000,151,-1000,-399,-1000,1000,-494,-727,-293,391,501,1000,231,-1000,-1000,621,111,-880,-1000,-1000,-718,-1000,-216,1000,-1000,342,-15,-257,80,472,-602,82,437,284,-1000,-1000,-650,1000,171,1000,393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{870,-572,-472,388,-1000,1000,-871,-1000,1000,-111,866,-446,363,-551,491,893,690,1000,-1000,-670,-1000,-747,-665,-129,1000,-135,165,-421,1000,-136,-51,-36,792,597,5,54,-866,-1000,993,-283,1000,-321,-1000,682,-1000,-674,-106,-348,-233,-734,-400,57,75,125,-899,242,26,-1000,1000,-759,-974,492,151,-95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-709,664,785,-137,-258,-791,193,-367,-169,-257,-650,-324,963,-180,827,173,-362,885,-627,66,-864,575,47,-691,688,-67,68,178,665,-790,-468,-559,654,-24,972,991,-855,-716,-860,-587,-975,-155,379,875,38,309,-538,723,-36,67,897,-807,386,-717,-664,-207,604,-715,-54,-935,500,877,408,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{440,-673,-393,340,62,1000,-852,672,-564,72,24,-1000,-336,-124,-406,400,1000,1000,-785,-377,-588,1000,256,-1000,-54,-437,263,-593,1000,-768,-46,677,318,37,339,1000,257,-1000,930,528,533,131,-147,-245,-617,-14,-424,-483,-837,-804,-1000,-427,-446,683,-1000,598,802,-1000,896,-519,33,-1000,-246,-72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{1000,483,-1000,-788,-1000,1000,-852,-963,234,1000,315,-549,466,-296,-34,-1000,731,1000,-1000,-668,-1000,911,211,-737,-118,-33,-323,-603,1000,-958,-112,-986,1,159,1000,919,-549,-1000,1000,-21,667,-1000,1,709,-1000,882,24,-1000,114,-140,-1000,-443,-446,-717,-1000,1000,873,-1000,722,-774,1000,-100,-122,841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-220,967,1000,-938,888,-491,416,-582,-177,-24,-273,1000,946,322,-990,-986,-1000,92,-758,1000,1000,158,-923,197,-1000,-1000,-672,-809,-857,1000,-1000,1000,-541,-1000,1000,1000,1000,1000,-229,-818,-1000,607,1000,-710,1000,1000,-50,352,11,-62,-1000,1000,1000,-1000,1000,1000,-486,1000,203,1000,1000,-1000,229,-339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-1000,1000,-209,649,1000,812,-1000,-15,387,-791,1000,-428,-1000,203,-1000,1000,1000,-643,-657,455,-83,-879,747,-1000,-1000,-290,-669,1000,984,-1000,-659,1000,1000,-564,-1000,-366,1000,728,1000,988,1000,-121,908,609,138,896,-1000,1000,-1000,-477,1000,-8,-1000,278,-883,946,1000,36,458,-1000,-1000,-843,-823,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-968,-576,1000,-1000,379,1000,-986,-712,-620,1000,503,948,102,716,-1000,344,613,1000,-555,869,914,-142,-1000,499,-854,-31,-312,994,-685,589,900,237,186,-184,1000,619,207,188,415,592,-1000,-587,385,204,1000,1000,1000,-673,670,-1000,-903,-1000,-454,-390,-1000,-1000,-545,-407,-267,107,85,156,1000,-206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{282,-201,1000,-536,-614,1000,-1000,-1000,-271,844,-228,14,365,887,-1000,778,274,994,-413,1000,839,640,-1000,489,-1000,1000,-464,410,-957,469,763,544,-1000,201,1000,608,166,528,839,465,-641,-984,519,-543,667,877,695,-626,226,-1000,-1000,-820,534,-1000,-300,-1000,-826,42,-990,-1000,828,-164,1000,-154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-1000,-1000,21,-308,-312,-769,-441,267,46,1000,145,-647,-302,1000,-83,-24,779,-574,-443,296,833,-218,132,912,-278,-1000,1000,-595,-1000,849,86,-64,-1000,-123,747,-1000,299,-222,480,88,328,187,-1000,-460,923,329,308,-877,833,691,1000,479,-734,396,-567,-1000,13,-809,198,-290,-801,-130,597,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{667,-464,319,709,-893,647,-522,-947,520,920,121,837,-306,869,7,528,279,590,225,1000,-793,254,118,586,-927,347,-237,537,588,-359,-654,749,-1000,-440,762,-207,48,225,-370,-893,-1000,-241,229,851,-971,-714,801,-38,404,-74,-182,-79,285,-1000,-295,-987,-289,230,289,825,782,15,1000,-914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{536,-544,-484,-317,-671,1000,-986,-958,-620,832,-392,948,102,59,-1000,-379,647,1000,503,514,-432,470,-1000,-403,496,81,-1000,95,182,395,359,521,-166,-66,230,1000,-193,166,-1000,-744,-917,736,1000,451,315,-445,1000,211,48,-1000,-903,-214,154,-1000,-23,-146,-58,1000,-698,107,1000,188,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-792,-669,566,-989,-733,535,-887,-107,-304,718,123,981,463,688,-56,322,435,-164,-369,354,673,952,-724,75,-840,669,-401,-53,-112,-90,712,289,-249,165,906,-6,610,690,-385,-629,-988,778,266,587,607,155,657,-244,62,-769,684,88,419,-527,-196,-377,257,458,-584,122,790,315,528,-572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-1000,-317,-461,-1000,-1000,-48,-1000,172,1000,684,1000,780,333,1000,-716,203,900,-576,-751,647,932,292,-499,1000,-933,-1000,1000,-521,986,-1000,-7,25,-1000,-1000,-7,-1000,1000,-610,-1000,-912,-208,333,-1000,1000,875,979,11,-957,1000,942,1000,-1000,-274,-112,352,261,-247,-1000,1000,-825,-100,-414,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-1000,-1000,1000,-1000,-1000,612,-233,868,-566,1000,855,-329,1000,922,-1000,647,913,-164,-1000,622,1000,-288,-1000,827,-1000,-1000,426,886,-1000,1000,1000,-54,-282,-113,1000,-931,1000,1000,-131,329,-1000,99,-1000,433,1000,1000,1000,-520,1000,4,668,-1000,-621,1000,-892,-985,258,-798,1000,-110,-539,187,182,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-1000,-1000,-303,-1000,488,209,-1000,-182,-618,1000,536,837,-889,728,-1000,278,1000,1000,-580,359,839,146,-279,1000,-613,-509,312,-25,-22,1000,1000,619,-1000,50,468,-368,699,-109,140,-45,-641,381,121,940,895,877,1000,-816,587,-733,-346,-730,-948,181,-887,-755,161,-792,33,-1000,-890,-464,41,-405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-1000,-1000,21,313,217,-1000,-71,601,-125,684,405,-934,-322,900,-109,-658,1000,-574,80,647,750,-903,1000,603,148,-1000,224,-466,-842,1000,-586,-499,-1000,-507,589,-1000,-256,-285,51,167,1000,187,-1000,-1000,918,140,39,-957,1000,1000,1000,713,-1000,540,-878,-1000,-324,-56,198,-1000,-1000,138,-393,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-593,-1000,633,-1000,-973,146,-816,327,-135,451,-483,-195,617,1000,-899,956,234,-980,-1000,-43,544,242,-1000,1000,-1000,262,365,-603,-210,-839,1000,507,-294,586,899,-685,746,1000,539,-623,-1000,393,-302,584,1000,439,973,997,-45,201,1000,-11,1000,799,190,-583,570,103,-988,232,-506,-884,-494,941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{772,-625,160,1000,-693,924,-591,1000,938,283,-1000,323,285,562,-904,-446,-1000,710,420,-84,279,925,-285,-561,1000,130,-24,232,56,-673,-532,635,-156,-821,436,-609,91,-1000,735,543,-524,-485,1000,-281,143,1000,1000,309,-109,1000,1000,1000,802,-233,618,854,-134,246,295,1000,329,1000,45,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{341,-1000,168,81,553,1000,708,338,248,1000,-400,1000,645,-408,-1000,106,-617,478,-458,-196,-206,-326,-109,102,1000,819,-321,-711,593,287,-525,-180,281,-406,-49,14,-237,-1000,-575,1000,-1000,-827,-326,-446,-1000,645,-118,-132,-174,331,1000,1000,-827,801,113,552,1000,-157,-936,-1000,464,1000,93,676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{1000,-401,-729,-10,-1000,924,-783,1000,-1000,-428,902,-879,680,1000,824,-1000,-266,710,420,1000,478,1000,-285,-1000,-201,130,1000,286,399,-373,9,1000,-1000,-677,-942,1000,-727,-1000,-221,845,-152,-201,1000,-608,143,-115,-96,1000,1000,466,1000,1000,1000,192,643,-156,-134,-550,822,910,315,-304,-880,450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-1000,176,468,1000,-651,774,213,1000,1000,412,-1000,678,-26,416,-1000,612,-1000,216,-915,-60,876,846,-238,61,1000,1000,124,443,29,-577,-1000,713,403,-1000,1000,-383,481,-1000,380,710,-539,-71,119,437,-69,81,-1000,-241,-460,1000,1000,1000,-1000,454,842,852,-454,-562,-296,-720,-97,391,48,-331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{289,-665,-330,301,-408,821,-578,216,-426,927,-967,570,239,553,706,-1000,1000,-1000,-49,150,1000,484,151,-51,890,1000,379,395,8,-1000,-880,-570,217,-510,356,-61,559,278,-131,833,632,-65,-632,358,641,1000,14,-115,656,848,1000,1000,-576,-299,649,-175,-837,254,-95,-1000,659,-125,-129,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-3,-509,803,-303,581,-472,433,-41,-865,-121,641,-832,716,348,305,792,-789,127,-108,-962,624,266,930,283,778,-694,360,-475,-32,-837,11,294,155,-84,-888,988,-810,68,330,369,-767,-947,-841,123,-929,-702,669,764,338,-32,578,950,-224,-299,-577,-691,-354,-936,-805,262,121,-713,-760,988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{1000,35,160,1000,-1000,1000,-1000,911,555,234,-1000,733,285,1000,-663,-454,-180,1000,1000,210,-115,991,-585,-969,1000,-378,450,-361,868,-673,-935,872,1000,-734,749,-408,91,-655,466,1000,-971,-711,1000,-1000,-750,1000,260,121,873,1000,1000,1000,1000,43,866,972,-22,246,704,-1000,1000,1000,1000,539}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-715,207,-701,-507,-208,860,-769,582,190,-981,-672,730,-130,672,-636,-805,356,613,915,-143,-57,287,520,225,-704,-394,-392,-300,432,-48,-203,212,-398,203,918,-166,447,483,646,779,-476,-411,972,-671,-217,-465,-73,632,930,524,934,106,709,-214,968,-815,118,190,293,561,299,-675,978,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:MA==:19:java.lang.Byte:MQ==:19:java.lang.Byte:Mw==:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{771,-1000,443,464,1000,834,-641,-980,-523,898,-90,1000,781,515,-769,870,-811,-466,-670,-1000,-160,-510,1000,-47,49,984,228,-411,-1000,-102,39,875,-1000,-217,-538,521,-733,-1000,-548,1000,-377,-1000,94,896,-585,493,30,426,-74,278,-545,1000,-51,1000,-526,26,848,-905,-1000,400,967,808,-350,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:LTE=:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{695,-1000,-535,-62,-47,1000,298,130,-931,1000,-967,934,180,38,-904,-796,255,-1000,420,-84,625,281,151,213,1000,1000,438,434,-217,-259,-305,-180,-73,-153,-234,362,91,-1000,-404,1000,528,-485,-632,618,80,792,-239,-197,240,849,-81,1000,-576,721,429,347,-134,402,-910,-1000,723,214,-191,-33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Long:NjU1Mzc=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{780,-1000,595,-93,895,237,717,-343,-611,-494,953,-282,1000,617,-1000,304,-98,-846,-1000,227,-1000,-150,-1000,1000,-162,1000,14,726,1000,-918,716,414,462,-863,-1000,1000,-116,1000,1000,-840,56,90,819,-1000,945,-1000,869,-320,-691,-965,-363,-341,549,-253,353,1000,-475,1000,184,1000,59,1000,1000,-99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{534,-341,-1000,-1000,1000,813,734,1000,-844,23,191,-636,1000,190,-1000,-572,829,619,-994,-1000,-1000,-503,-953,909,-1000,-482,1000,-247,-1000,-845,1000,961,1000,-1000,-368,1000,677,726,1000,-449,656,324,1000,-1000,219,-1000,-854,1000,-924,452,727,-846,-989,1000,-676,-777,110,1000,-1000,652,-1000,1000,1000,641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{652,1000,360,1000,-404,605,518,-647,781,-286,262,322,-482,-721,738,-466,1000,-81,292,1000,188,-80,-133,215,1000,551,1000,-301,-117,-1000,-1000,137,-163,17,-1000,510,72,-304,-1000,-872,-1000,-1000,-61,-993,499,-929,138,-1000,752,-1000,-537,-430,1,-689,1000,179,1000,388,0,636,510,356,678,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{-97,-1000,-603,-594,-140,605,194,209,1000,-286,906,-1000,-482,1000,-497,539,804,-1000,-1000,-760,-236,475,-1000,-180,-4,1000,1000,-301,-117,506,1000,1000,156,-1000,-1000,548,661,-304,1000,-1000,882,338,-375,-1000,435,-1000,366,-1000,-722,1000,-1000,168,1,-689,-420,1000,664,388,-197,636,164,356,-442,-734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Long:MTQ4NzY2NzI=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{760,-952,116,612,300,-76,1000,119,-155,-45,789,227,863,148,249,-422,-348,-627,-1000,1000,-867,-261,-1000,623,276,1000,310,962,551,127,801,526,624,-663,748,199,-321,726,157,-421,40,86,869,-181,667,-806,80,-67,-847,-809,89,-968,598,-276,324,-777,-1000,732,-171,652,-473,876,781,-345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{777,188,-678,993,-200,-41,-697,188,137,944,-440,650,24,-550,1000,-1000,152,2,-264,1000,929,-349,-865,-200,580,562,55,-928,867,269,-275,217,839,-562,928,-23,362,250,-1000,-385,-145,-328,926,139,229,-756,-1000,49,-135,-616,-269,-96,34,145,708,-1000,-566,192,-808,437,-913,765,733,-94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{1000,628,249,483,630,-778,-586,-216,-222,770,-840,-98,-175,-973,-468,-1000,616,844,938,1000,49,-707,-396,1000,-670,-53,333,138,156,-1000,322,371,375,-912,90,409,859,437,384,1000,207,54,-187,-337,568,-566,239,-373,-1000,-689,741,-1000,323,1000,293,-1000,216,935,-525,914,-569,88,556,802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Long:LTY1NTM2", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{979,-740,-370,-616,772,-202,-294,161,112,628,-86,-1000,801,318,-900,118,1000,-902,-831,-1000,-168,857,274,716,-490,374,953,-815,-951,-356,875,434,346,-485,-1000,879,449,563,892,-939,298,651,328,-336,432,-1000,253,409,-611,782,-763,-161,652,273,357,949,158,535,-492,960,61,661,823,136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{-1000,-537,942,-524,310,-766,-1000,1000,-495,1000,-600,78,446,1000,-976,1000,477,12,740,-388,-1000,-532,405,395,-435,-942,34,-784,-4,-1000,616,-94,107,1000,-229,165,-541,-98,1000,414,1000,311,627,461,1000,65,-1000,862,-594,637,422,-1000,-1000,655,-1000,-782,1000,1000,-844,-34,373,-287,-59,-534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{254,-400,790,-936,-402,1000,-131,904,188,-971,-494,194,410,409,-736,161,-209,-297,809,-284,-613,210,-427,-876,-573,-1000,349,403,124,-497,-169,-464,181,113,58,-356,403,-422,1000,-57,-798,270,252,457,-673,300,-1000,267,894,-919,-224,-555,-233,-336,-160,742,-493,400,-18,-633,-1000,584,-343,249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-814,327,-1000,-682,268,-844,-138,-1000,197,706,35,502,117,-1000,568,193,-429,-241,-382,746,-292,54,818,685,-292,-187,-1000,-970,1000,410,643,-528,-200,-379,-445,-119,-1000,-184,-68,-1000,-469,517,-179,700,-992,-883,-595,-1000,500,1000,-229,-135,963,-994,-38,1000,-941,-580,1000,-1000,3,-78,945,691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{1000,1000,154,-1000,-568,64,-301,-94,-398,-235,-593,-540,-633,1000,714,1000,582,171,1000,1000,-452,-841,-1000,-1000,69,-43,-557,1000,703,-1000,-768,-1000,1000,-1000,-1000,1000,466,504,889,-210,1000,558,1000,423,130,286,-582,139,390,-375,-689,-1000,238,-634,-1000,419,81,-486,-697,-1000,132,1000,-473,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-641,1000,-98,-1000,122,-312,315,420,-791,141,-606,660,-628,773,-1000,-24,546,-30,1000,269,-1000,882,-181,-334,445,-817,269,602,893,-299,181,-1000,-354,-1000,583,251,-351,-1000,-674,-533,-563,-226,806,524,-48,25,-1000,-742,-944,-157,248,-1000,120,-848,-501,376,-104,-1000,712,-1000,-211,843,234,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-394,747,606,-1000,-18,-586,71,950,-1000,-442,-181,923,85,-882,-805,996,688,-789,-750,-911,-297,-332,-427,158,-452,-104,110,-65,857,-356,-275,1000,181,424,843,-1000,350,-797,-1000,396,154,1000,56,20,-734,808,-1000,382,-67,-1000,588,35,-569,-366,-378,-314,81,374,88,161,-651,-361,-817,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{650,1000,-737,-895,-179,-1000,-301,-920,-124,725,-365,-579,-1000,449,1000,1000,718,-809,997,-191,-1000,-802,-828,-106,475,526,-1000,572,-241,-575,-804,-1000,800,-1000,-497,1000,81,447,141,-855,1000,639,1000,340,434,-109,-388,-616,62,373,1000,276,-1000,-941,-1000,600,16,-1000,-201,-1000,727,732,-1000,-894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{435,1000,1000,-1000,-485,-3,48,1000,-967,-312,-740,474,-790,645,-1000,-460,403,-809,700,-1000,-888,305,-1000,-876,788,-773,498,721,639,-1000,-194,-292,18,-1000,853,728,336,-701,-544,352,530,128,1000,861,-460,1000,-885,427,-956,-1000,819,-1000,-1000,-847,-1000,-833,1000,-266,-173,-1000,-353,514,-694,-143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{1000,1000,582,-1000,-1000,-245,274,804,-892,-1000,-625,1000,235,-555,-384,-1000,1000,-1000,805,-1000,-1000,92,-1000,-1000,-521,-132,-215,1000,1000,-883,16,1000,-199,-29,411,-509,1000,-1000,-1000,944,-497,66,1000,1000,-1000,536,-919,-546,-77,-1000,1000,-1000,-671,-859,-1000,-1000,1000,-1000,273,-1000,-174,314,-1000,590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-1000,607,664,-1000,-303,-760,568,-1,-776,1000,524,226,-1000,-1000,-927,-1000,-520,-842,-1000,-1000,-971,601,-691,1000,-61,-17,200,-1000,1000,183,1000,-269,-1000,-1000,611,-603,-1000,508,-1000,-987,-1000,650,-66,284,-290,760,-719,-160,-123,357,87,361,374,-529,778,1000,-1000,1000,1000,-259,-315,-706,583,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:1:69:TYPE:org.apache.commons.compress.archivers.zip.UnrecognizedExtraField", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-121,692,1000,1000,-18,226,-198,586,1000,-508,-792,-40,1000,-1000,72,-527,-1000,-332,-247,1000,-1000,1000,1000,1000,1000,-609,1000,643,-1000,1000,-1000,1000,-1000,1000,787,-89,-1000,-305,1000,1000,515,-705,1000,1000,-1000,197,1000,-698,-403,-1000,1000,-61,-599,170,-519,970,822,-1000,-1000,-188,677,-1000,-647,-427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-798,-745,88,-167,733,869,171,-679,121,-461,-307,872,693,-927,-315,-414,-102,-1000,-300,479,-51,-103,724,-987,201,-472,596,76,-623,488,387,864,-1000,510,743,-243,305,-154,822,272,130,-1000,792,173,-1000,73,-139,-1000,1000,-356,1000,216,302,-511,-203,-260,-571,-377,658,-516,-607,-493,-1000,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:1:4:NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-128,407,-544,1000,1000,1000,1000,1000,-138,-1000,-1000,-616,-502,-950,-1000,-1000,-1000,827,607,1000,31,400,351,687,726,-291,533,1000,-60,1000,-1000,878,83,1000,-398,-972,-1000,-955,820,1000,-535,653,1000,80,-715,1000,1000,-1000,862,-726,1000,-1000,-755,-137,-1000,-1000,1000,-1000,-1000,134,1000,805,-167,711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:1:4:NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-106,-248,-471,-361,850,971,769,-95,-790,-457,-476,623,-298,426,-784,-900,736,-390,660,-686,695,-871,-527,-590,-506,-226,307,-492,596,-94,309,-337,-131,-535,129,-578,673,-690,-607,-627,-610,-644,-903,-698,242,634,-377,-841,708,336,216,-119,-928,-769,-353,-701,-250,-638,551,-119,-99,908,-572,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-278,-1000,-772,868,-218,534,-532,-995,144,-203,-117,623,52,-625,-677,934,-345,-1000,-589,-436,-369,-670,-527,-586,-506,-465,303,-492,-381,-94,867,-28,-1000,458,129,-657,37,215,-660,-175,622,-1000,79,88,-973,-487,-812,-806,-80,-267,1000,714,251,74,-353,472,264,515,286,-119,15,-9,-352,-937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-1000,303,88,251,117,1000,1000,822,33,-1000,-340,-101,748,-578,-940,-1000,-612,400,-201,865,173,11,1000,-1000,596,-93,224,847,-618,90,-1000,1000,-775,305,568,214,-196,-567,1000,1000,130,269,1000,274,-1000,426,1000,-954,1000,-1000,822,-1000,838,-898,-882,-1000,171,-901,-1000,-1000,394,-81,-1000,-817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:3:4:NULL:4:NULL:4:NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{22,-248,-471,-282,-1000,-1000,-962,-157,-141,348,-39,978,502,-502,649,-863,874,-46,-1000,-716,284,622,4,-562,247,-699,126,-1000,664,-937,309,-337,-1000,-166,129,1000,48,-1000,155,-1000,-174,-1000,-398,-465,818,205,-868,640,-591,-456,675,384,1000,-1000,1000,909,-1000,-1000,141,-278,-524,-739,-572,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-1000,1000,88,-167,-63,423,754,682,-67,-271,-433,644,1000,-653,-547,-312,-168,-420,-366,-1000,-888,98,-36,-444,298,-96,294,751,-752,-115,-376,651,-901,677,470,99,566,-561,444,532,130,-254,597,473,-1000,223,611,-1000,1000,-982,1000,-1000,1000,-632,-882,-599,-75,-775,400,-765,-318,-146,-1000,-817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:3:4:NULL:4:NULL:4:NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-24,1000,1000,1000,162,803,48,727,1000,-1000,-699,-101,530,-925,-321,-1000,-612,488,-82,1000,-1000,912,1000,444,1000,-606,930,739,-866,1000,-1000,1000,-873,628,885,26,-1000,-477,1000,1000,515,-182,1000,801,-802,399,1000,-954,-403,-1000,649,-829,-889,-96,-519,570,1000,-1000,-1000,-423,1000,-81,-506,-427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-444,287,-617,1000,-1000,300,-870,-524,473,-154,-753,-722,-817,348,-655,-389,-856,-46,-898,-500,-273,-203,233,-1000,-168,-1000,559,-1000,154,108,50,-310,-489,-1000,421,905,-1000,-915,-683,-684,144,-629,-353,-662,382,335,46,-857,-1000,-655,-283,309,602,-739,955,909,-586,-1000,1000,850,341,-45,559,-866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:1:72:TYPE:org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{892,383,-192,-988,612,3,-1000,201,1000,1000,44,628,886,1000,-84,-88,-423,-278,-932,-537,-431,626,173,621,-888,-588,440,274,1000,1000,-80,-564,-1000,-342,897,-1000,-562,-703,797,-817,-177,-20,894,-173,-168,-1000,-77,1000,631,47,-209,589,120,454,-501,-1000,-1000,603,-627,-331,-817,-712,-1000,115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{1000,-513,-585,409,704,-1000,-1000,-795,1000,-217,-428,-1000,-1000,-527,1000,214,933,1000,1000,104,1000,1000,1000,858,1000,-162,811,1000,52,1000,1000,187,-830,-1000,150,-86,889,-645,-798,-314,-309,-819,-1000,-807,-12,1000,-840,-904,-981,-1000,502,1000,-692,1000,189,1000,-1000,-505,863,-1000,-76,-1000,943,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:4:4:NULL:4:NULL:4:NULL:4:NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-359,-1000,-723,-1000,935,-282,75,345,33,900,1000,-832,-1000,-1000,263,139,908,500,444,1000,-1000,844,342,323,-441,-493,832,911,-1000,-510,-350,570,-2,1000,370,959,489,1000,452,642,1000,1000,926,784,724,-100,1000,486,-1000,1000,-425,535,525,-1000,240,-704,-1000,423,-1000,-250,-927,-944,-282,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-84,-597,-604,389,1000,386,-485,293,712,767,1000,-251,-102,-1000,213,411,421,-286,-783,113,-1000,-535,712,-203,-1000,-294,1000,889,-126,1000,-459,188,-531,596,-142,161,-587,271,716,-287,1000,1000,894,556,606,-572,327,1000,70,1000,-324,857,296,-1000,-1000,291,-1000,474,-1000,698,-569,-1000,-1000,-952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:2:4:NULL:4:NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-1000,771,-1000,-468,-1000,-1000,454,-1000,746,1000,577,-231,352,1000,129,-445,-1000,532,1000,-1000,-1000,349,-699,1000,-1000,-1000,-580,-861,1000,-804,139,631,-1000,-913,-666,140,-70,-77,-1000,33,-1000,-1000,710,913,1000,204,-858,567,199,806,-1000,-724,13,-861,1000,606,1000,64,48,-318,993,-653,-246,644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:1:4:NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{278,1000,-159,-1000,-906,-678,217,316,1000,1000,141,338,1000,550,-620,-502,-104,-1000,-328,-379,173,1000,39,530,-670,232,41,-1000,581,-846,-509,-194,-799,-1000,136,-754,389,-558,1000,-1000,-499,-556,305,1000,107,-690,643,898,282,591,-505,223,1000,581,161,-706,-397,749,-18,-284,-470,-782,-768,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{931,1000,270,-1000,-1000,-1000,18,1000,1000,816,-434,329,850,-142,233,-109,293,340,-400,700,-417,-359,-1000,1000,1000,-195,1000,-583,1000,-1000,421,-450,-935,-552,1000,-1000,183,-1000,1000,-1000,-1000,-636,-1000,303,-676,-1000,919,426,940,-430,717,1000,745,604,-1000,-1000,93,1000,345,-1000,-920,-1000,-384,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:1:72:TYPE:org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{1000,1000,1000,-1000,233,799,-373,988,1000,-285,-405,628,886,1000,-199,508,748,-278,-166,-515,628,1000,768,38,-379,1000,525,-941,977,1000,-656,-1000,531,-342,1000,-1000,647,-1000,1000,-1000,478,-603,-185,-419,-1000,-1000,1000,769,631,-1000,314,1000,1000,1000,-575,-1000,-1000,1000,-562,393,-601,-566,-527,317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-1000,-1000,-1000,1000,-132,-631,679,-300,234,604,1000,-1000,-1000,-1000,462,-282,170,979,665,527,-1000,-1000,544,-178,-441,-987,432,-127,-1000,-793,-70,1000,-330,679,-47,1000,365,1000,-750,893,763,801,1000,1000,1000,1000,-312,480,-962,1000,-891,-468,195,-1000,13,-484,-648,78,-954,-634,-14,-757,-203,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{681,435,-28,-1000,-499,-1000,-59,264,753,154,190,329,-75,-759,754,-690,257,1000,-40,1000,-113,778,-1000,777,737,-766,-184,193,1000,-1,375,159,-935,848,1000,5,-258,-1000,789,-791,-1000,-101,-922,-1000,16,-1000,456,333,1000,751,-195,796,-39,-71,-77,-1000,-954,945,438,-755,-242,-319,-863,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields(boolean):org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-587,951,-1000,-1000,-750,-1000,446,-466,840,1000,263,-761,1000,-148,744,-63,-602,396,1000,69,-1000,280,-1000,563,29,1000,-685,-1000,1000,-801,-811,1000,-128,-1000,656,-669,656,-1000,153,-1000,-619,-772,-1000,1000,414,-1000,1000,347,712,-1000,-251,72,1000,279,1000,-1000,702,593,15,-1000,22,-256,-335,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{-1000,-953,1000,-798,-294,-853,-1000,408,-400,-40,179,-212,-547,-886,-546,-136,-389,-1000,-858,350,346,-986,-1000,528,362,-984,37,-823,1000,-20,654,800,-579,84,-1000,-1000,570,-209,-1000,427,1000,396,288,-1000,-1000,1000,466,-369,-403,1000,-1000,-378,883,301,-581,-201,-253,1000,1000,-150,-671,-536,-1000,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{-81,-804,-113,-1000,-1000,-289,-916,306,478,188,392,515,-16,177,-665,1000,599,878,-278,-271,1000,-930,-987,-20,1000,-1000,418,-1000,457,-15,1000,-600,-611,69,-1000,224,693,1000,-252,993,761,935,719,-1000,-431,1000,829,-1000,-811,1000,-1000,-1000,1000,-80,-614,-328,-1000,736,1000,-662,-818,50,-30,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{-303,-1000,-429,-120,931,-486,-999,543,301,-590,-346,-1000,-1000,-58,399,274,-260,35,281,593,-567,1,-1000,-53,-499,-349,-1000,-403,-2,-657,-1000,779,-512,371,705,-680,839,620,-131,-147,526,1000,-830,-9,279,753,160,0,276,182,1000,969,-647,-206,315,270,1000,1000,-206,-657,-677,-863,-1000,-513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{653,-516,454,850,-173,264,469,-918,-1000,582,-536,1000,1000,-947,-591,1000,-763,139,-702,-191,-416,-56,1000,129,-62,766,1000,1000,817,1,1000,-1000,-38,-1000,-902,-761,965,-1000,50,1000,192,-1000,619,306,-783,-839,-628,-379,-1000,-1000,-1000,-1000,312,114,-85,-982,-1000,-508,-73,1000,795,1000,492,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{-502,299,-713,1000,317,-455,-692,999,-1000,-467,-766,1000,-266,709,-239,-122,-433,268,411,-178,-967,685,-773,1000,-758,133,1000,321,-435,-223,-507,479,914,-779,429,-1000,1000,-239,10,1000,-153,-817,-1000,-1000,-635,311,-441,-728,-1000,-927,708,1000,72,-1000,-667,-663,1000,650,-1000,113,-1000,1000,-1000,-487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{-29,-1000,58,566,1000,-758,-476,1000,621,-290,-1000,-1000,-1000,-367,974,-965,-740,1000,1000,1000,-1000,-70,-1000,975,-741,815,-1000,890,-613,-815,-1000,997,-203,1000,-153,-1000,-867,1000,68,65,1000,-1000,526,577,-565,672,-285,1000,1000,410,206,1000,-775,807,-524,417,1000,514,-341,-1000,-313,-373,-1000,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{-353,-505,1000,-334,-211,-516,-943,154,-1000,-351,80,122,626,-649,-411,632,-367,-639,-1000,62,-233,-589,-1000,97,-178,-607,562,-561,1000,1000,655,-236,-332,-532,-1000,-1000,1000,-496,-319,1000,1000,566,305,-1000,-1000,984,-454,-668,-1000,-80,-1000,-968,157,-26,124,-343,-790,1000,542,1000,-970,105,-892,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{819,-1000,-99,450,443,-786,-905,-206,-620,-356,-766,1000,-490,-690,1000,-998,-955,228,-112,352,-932,551,-1000,296,-1000,52,276,-405,-192,-887,-798,29,-10,-465,185,-594,773,87,-77,1000,210,-450,-1000,275,666,16,270,-625,-1000,-743,942,972,-603,-1000,-61,-96,941,775,-499,590,-1000,932,-1000,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{941,-1000,-1000,814,1000,974,834,-1000,-228,1000,-870,-386,8,-352,1000,-123,-1000,1000,974,1000,-1000,405,400,-76,-428,1000,-509,-504,-621,-1000,-1000,-13,-874,-73,1000,-81,897,315,431,-147,-369,-609,-1000,1000,1000,-872,914,765,1000,-18,-127,1000,-1000,495,865,126,620,-56,-720,-1000,1000,-510,773,-72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{743,-701,-984,605,1000,-506,27,881,1000,1000,-1000,1000,322,-381,-511,598,728,778,814,-683,754,932,1000,-1000,1000,1000,1000,1000,-219,-601,1000,-1000,934,-159,-38,1000,-1000,-526,-290,833,-713,-997,170,1000,-1000,-1000,-864,227,689,-123,-217,816,1000,-636,-346,-1000,-428,-733,50,-97,1000,863,1000,-905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.GeneralPurposeBit", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getGeneralPurposeBit():org.apache.commons.compress.archivers.zip.GeneralPurposeBit",
            new int[]{-649,-1000,1000,-1000,455,-846,-876,535,-941,1000,10,-1000,-748,-1000,520,-1000,-1000,-75,-483,1000,-1000,-1000,-1000,424,-290,-1000,-1000,-1000,1000,211,-200,1000,-1000,359,-1000,-1000,318,1000,-1000,-208,0,-22,-197,-503,-565,1000,191,326,555,1000,-762,165,-657,1000,376,481,332,1000,938,-916,-188,-1000,-1000,305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{1000,1000,308,901,101,-576,-452,-613,957,121,918,-1000,1000,-26,1000,-496,997,1000,-914,452,-717,586,-956,1000,-1000,1000,-1000,-605,-853,-831,-1000,1000,630,602,-775,1000,-932,258,-1000,-258,430,1000,-293,-705,391,-1000,501,-33,-1000,260,-1000,-1000,-333,65,758,129,-432,1000,-586,-562,-990,-518,-924,-339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{-322,863,368,-334,-575,-261,-653,-141,401,-127,-555,793,-1000,-242,333,1000,1000,754,73,-1000,-1000,-701,236,986,-140,827,-322,-34,-993,-935,-816,-78,-1000,-931,-1000,746,408,280,-1000,1000,808,-584,384,-86,1000,-1000,-218,1000,-11,1000,-168,-673,-47,685,696,186,-1000,820,-93,-253,-194,-642,-508,76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{1000,103,1000,1000,-575,-261,216,-1000,1000,1000,-555,793,839,-1000,1000,-854,841,1000,-421,194,-1000,-861,-792,-358,-378,689,-1000,970,45,-1000,-1000,1000,-541,-931,-1000,746,-587,-414,-674,309,808,-412,221,-1000,1000,-1000,1000,63,-538,83,333,771,-1000,685,37,-553,-907,-895,434,-1000,-252,-642,-508,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{658,1000,-829,1000,304,-181,-967,330,965,-390,1000,-1000,434,1000,568,-219,-326,609,-596,501,-1000,1000,-879,1000,-1000,302,-1000,-1000,-922,-118,-1000,-41,1000,1000,532,1000,-821,-1000,-418,-74,-894,1000,-989,399,-372,-792,-757,-410,-51,-416,-1000,-260,439,166,1000,856,112,1000,-1000,-856,-1000,744,-1000,927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{-316,975,308,925,-1000,-645,194,-1000,1000,1000,1000,1000,1000,586,-320,-44,-77,1000,-578,-1000,-117,-689,-1000,-354,1000,633,-901,1000,-1000,220,-71,1000,-143,-148,-936,-947,-598,241,-270,420,-891,-1000,-669,-432,-118,-292,1000,196,-1000,541,-194,1000,-1000,622,-905,-827,277,913,-879,-389,89,-978,519,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{-244,564,-1000,86,27,1000,-452,915,957,-1000,1000,-634,-869,1000,-784,1000,-1000,-1000,809,89,-717,586,132,-49,387,-1000,340,-1000,-1000,-831,1000,1000,1000,1000,1000,471,836,-1000,1000,-22,-1000,655,-1000,1000,-1000,995,-1000,-777,1000,-409,-880,-22,-333,283,876,1000,547,1000,-1000,-428,-406,1000,-806,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{1000,751,-974,1000,545,-457,-312,156,430,-457,1000,-1000,1000,567,1000,-779,-557,716,-575,1000,113,1000,-718,893,-487,282,-545,-1000,-1000,-399,342,1000,1000,1000,1000,1000,-1000,-95,-1000,-1000,-1000,1000,-1000,1000,-1000,620,501,324,-1000,-1000,-1000,-1000,788,486,1000,660,178,1000,-1000,-1000,-1000,1000,-1000,745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{-584,888,239,478,-1000,172,1000,-413,393,668,-847,1000,-272,-237,-400,-27,900,-1000,697,-729,222,-1000,-321,-1000,124,-385,-688,591,-16,-551,703,201,-966,180,-363,-515,298,-295,-143,812,1000,-1000,-282,588,-1000,145,209,856,312,687,266,1000,-992,265,-919,-722,-793,-1000,-121,-768,694,-1000,391,-320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{1000,1000,1000,953,648,-1000,-143,-926,970,-1000,639,-930,740,-1000,1000,-687,1000,1000,-582,955,-520,620,-969,1000,-606,1000,-692,-1000,-1000,-1000,-1000,489,848,1000,-867,920,-1000,179,-1000,-1000,-452,22,-18,-1000,463,-956,-163,-71,-1000,-375,-1000,-1000,-60,-817,1000,-387,-1000,1000,-688,-1000,-1000,899,-878,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{402,95,939,-790,-104,-530,-385,125,-485,209,-482,706,-507,1000,-959,669,422,-867,181,581,-22,1000,59,-161,304,-384,-82,58,1000,1000,-854,-198,358,1000,-122,636,-123,95,442,-657,832,163,-1000,-441,-602,363,-133,386,-1000,491,302,446,-570,-327,-215,454,-1000,-1000,-314,631,847,1000,-411,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-363,-1000,11,-481,1000,-596,-281,-387,397,1000,354,571,-1000,-716,139,-690,133,688,-354,658,1000,328,417,-923,1000,-832,192,1000,359,1000,24,141,565,1000,1000,1000,-452,981,-334,90,-1000,-1000,-300,-583,-1000,-406,-676,185,485,-797,-653,464,-161,-858,298,-397,-1000,-883,1000,-1000,310,1000,168,-463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-128,220,79,-886,400,-579,407,-105,243,-670,607,1,398,1000,309,566,-78,-953,687,473,-430,-368,-765,-94,-107,-373,-894,683,376,35,-735,-726,-608,-210,-406,248,783,128,-306,-508,-965,931,-1000,-162,661,-444,-708,611,-574,975,-746,-76,-40,621,-100,464,44,152,1000,243,-181,98,355,-384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{1000,844,-1000,133,-654,717,859,-870,-505,-1000,-69,-467,-8,41,-156,-615,-523,-616,25,-1000,734,-141,159,1000,-1000,699,280,-254,226,544,-1000,751,836,-376,1000,-201,-236,-306,64,-444,-1000,233,-1000,-1000,-879,1000,1000,1000,-506,-446,1000,627,-1000,1000,162,-1000,1000,-815,127,-173,-834,518,332,-422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-1000,1000,1000,-666,-1000,1000,214,1000,-652,1000,-263,-582,-776,793,-574,1000,1000,-742,1000,1000,-1000,-6,-1000,-1000,1000,-1000,-1000,1000,1000,-1000,138,1000,-1000,-240,-1000,-1000,1000,1000,1000,449,1000,710,816,1000,1000,-1000,-1000,-458,-121,1000,-1000,-10,75,1000,-1000,840,-875,1000,-1000,1000,-938,-894,-1000,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{968,495,-720,-904,746,1000,-7,-1000,-928,-808,240,464,-526,240,610,-398,-788,444,-306,-432,1000,-611,1000,724,-737,-477,-285,-254,675,549,-1000,1000,885,831,1000,-201,-1000,55,530,-101,-1000,-1000,-1000,-1000,-879,1000,1000,932,497,-304,1000,252,-1000,-195,1000,-801,212,-734,127,-173,-523,518,-250,-46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-1000,1000,-1000,-576,-145,-960,669,227,-221,189,395,17,231,-1000,573,-953,775,-880,574,1000,-251,-12,375,-988,1000,-1000,-1000,941,129,1000,-113,1000,-1000,511,-667,-20,-254,-206,-817,-553,1000,937,-79,833,1000,-461,-1000,503,752,1000,-1000,150,994,-394,-257,832,-1000,-166,-530,1000,-1000,974,-372,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-1000,1000,-839,-523,-1000,-817,719,1000,-39,45,761,-1000,637,-610,-518,-931,1000,-252,1000,787,-1000,-1000,-647,-1000,1000,-1000,-921,1000,197,-1000,732,1000,-1000,-1000,-1000,-414,726,-538,1000,-1000,1000,1000,176,735,1000,-1000,-1000,233,239,1000,-1000,-301,3,1000,-1000,1000,-631,1000,-1000,1000,2,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-551,412,719,-211,654,-1000,-171,29,940,1000,1000,145,118,-993,-1000,-795,393,292,-134,492,623,268,-8,-609,881,-1000,346,607,44,1000,-616,399,180,1000,400,1000,-734,665,66,-940,-400,-560,-1000,-910,-927,-173,-774,395,-232,-316,-757,728,-98,-593,-92,22,-1000,-318,-557,-400,651,1000,-1000,-293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTI0:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{400,-709,662,-107,76,-1000,311,116,217,1000,1000,1000,94,-1000,-62,1000,879,878,1000,-61,-1000,-807,219,531,559,656,191,-404,-14,587,-200,1000,815,-93,135,-135,-327,1000,-1000,951,212,510,-1000,805,-1000,20,375,1000,-980,544,661,1000,631,-152,-1000,-217,1000,825,370,888,-33,-139,261,238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{387,1000,1000,1000,-1000,-1000,69,457,106,1000,-365,89,691,648,-1000,-1000,400,827,1000,-772,-610,-339,-1000,1000,-150,547,-1000,1000,1000,1000,-1000,557,239,245,-1000,-219,-1000,1000,-862,1000,-1000,1000,-1000,-1000,-218,1000,400,-513,-1000,-178,-553,-500,1000,1000,-1000,-241,1000,1000,332,706,-1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{632,-1000,-1000,-966,1000,-312,144,-563,-86,-826,170,-261,-595,638,273,-66,-400,-1000,-116,323,-687,271,1000,-438,-272,-570,-261,165,-204,-1000,-509,-586,359,-481,400,649,-200,-960,1000,-1000,127,-1000,1000,261,-671,-142,-400,366,-95,-320,1000,1000,-31,-162,1000,405,-1000,-1000,-551,-483,989,694,607,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=:19:java.lang.Byte:MTE1:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{45,964,497,675,-845,-296,436,-619,339,-411,-63,-556,-653,-121,-731,905,-37,-823,-586,-713,-494,974,-118,952,-613,171,-664,558,255,-592,-645,882,-74,66,-316,933,181,632,-348,742,-206,736,-896,520,-316,623,-862,-8,-28,-910,227,592,-102,-14,898,976,38,248,10,820,97,-846,-72,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{749,1000,-273,-330,863,-484,315,-216,1000,183,-744,-107,-556,681,-1000,-731,1000,-849,-266,-486,-234,1000,394,453,-592,555,-540,1000,1000,-1000,-899,121,-145,1000,-1000,47,-619,1000,-73,-765,-897,-143,-613,1000,-1000,1000,532,-145,788,-1000,1000,-555,182,70,331,1000,-1000,32,-548,368,1000,-580,-340,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{312,-1000,365,197,283,-650,764,-98,710,623,-687,99,990,857,1000,199,539,-895,-270,-339,675,-431,992,-6,-220,-484,-972,358,63,-1000,-486,120,-262,260,420,1000,-402,-799,1000,-75,-696,-1000,-116,292,-668,253,98,139,177,-613,993,539,352,459,1000,971,-1000,187,-375,-301,-594,-197,869,774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{596,-557,-1000,-458,1000,4,851,-75,-628,692,384,-686,-795,154,-193,402,1000,-523,-356,78,-594,-8,1000,-1000,181,663,1000,-980,-337,-1000,147,483,4,120,950,290,-553,309,607,-1000,-138,-1000,1000,1000,-1000,-783,1000,1000,775,-1000,1000,507,517,-1000,42,-241,-1000,-1000,-770,-153,1000,1000,1000,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{19,368,1000,656,-1000,-1000,-802,433,-980,623,136,1000,603,505,-229,-698,-1000,323,400,377,-814,-253,-284,721,578,544,-1000,813,1000,1000,-1000,466,-145,447,-929,22,-1000,1000,-853,1000,-570,758,-1000,-253,-212,1000,-1000,-313,-1000,1000,-189,149,225,734,185,1000,1000,1000,-217,1000,-958,-913,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{1000,691,1000,1000,-1000,-916,721,-93,551,1000,284,287,-227,-200,-350,348,1000,-19,814,-1000,-223,367,-556,1000,-841,-86,-515,477,796,298,-1000,1000,543,-326,-1000,367,-76,1000,-340,1000,-507,1000,-1000,-133,-1000,1000,1000,371,420,439,479,72,1000,358,-1000,1000,1000,1000,345,581,-453,-1000,-1000,-501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{-570,728,-850,-215,1000,590,-73,527,-927,-17,-327,-1000,523,634,-619,222,-86,-494,-1000,927,-229,-536,1000,-1000,162,-305,1000,-1000,-581,-633,925,663,-52,-953,1000,619,553,-985,510,-1000,-317,-1000,-400,392,353,-1000,-289,1000,411,-79,559,1000,-538,-1000,797,-26,-1000,1000,-1000,210,-256,1000,154,576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{136,708,-119,-670,-625,-921,914,-313,-724,532,-590,685,-122,197,586,82,-505,-539,65,656,-919,1000,610,-699,1000,-178,-248,2,938,-253,-875,-526,-651,445,-921,951,-702,1000,-891,-387,-442,144,-1000,-516,95,208,821,-28,-1000,-947,551,798,1000,522,623,81,264,1000,-1000,-696,-334,619,326,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-733,311,-1000,965,156,-1000,-782,-197,669,-61,214,-146,-1000,-137,1000,-767,237,-687,345,-771,-571,341,-225,-764,1000,71,-211,1000,131,-636,-756,36,-167,1000,1000,1000,287,1000,-499,-826,-154,-366,-1000,-940,353,-1000,727,-1000,-1000,-1000,1000,291,1000,727,588,910,-1000,343,-1000,197,-207,1000,960,-758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-514,704,-609,877,-22,358,-657,765,-217,755,860,-124,-989,-810,884,-92,-811,-873,803,228,-820,474,-923,271,480,-948,-950,-839,962,808,-363,361,457,40,342,576,-712,510,176,357,-169,-307,419,913,-485,-411,-106,-541,-737,574,454,136,330,-446,-299,970,732,334,-377,-319,1,558,-199,205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{865,1000,844,-351,110,-920,-331,973,-380,692,-431,97,-422,1000,121,381,-314,697,1000,337,373,736,-919,-951,-503,510,626,-454,961,-121,-687,-94,-603,-1000,536,1000,27,-1000,760,251,289,468,1000,1000,142,1000,-149,271,-430,-359,-547,717,616,-180,158,1000,-788,-941,1000,-241,-9,642,1000,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-165,655,-37,94,-763,-209,-81,-50,-40,-153,153,933,685,276,-35,754,-354,-274,-200,709,-795,-597,33,-346,-188,961,-154,102,1000,-333,-628,-1000,-544,-31,759,329,341,-116,195,-838,-21,-21,-632,-1000,-376,-794,-439,753,639,-465,-381,-5,-347,-331,611,-608,-260,-975,-131,-1000,732,-1000,-1000,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTAwMA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-1000,-172,515,-235,653,204,-438,725,1000,1000,1000,-997,-74,-1000,194,632,643,-1000,-701,801,-876,-1000,-1000,1000,-8,-429,-1000,-377,397,1000,20,475,1000,-805,-1000,-1000,648,-1000,619,882,368,-552,1000,761,-996,-522,-1000,432,-377,619,-66,-1000,353,336,-967,145,554,-10,-309,-543,169,-631,-1000,-887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-428,443,-473,1000,-331,-933,-81,473,226,205,526,-58,-155,367,565,253,178,10,391,291,-398,-550,-580,-711,815,737,44,565,450,-744,-1000,-1000,724,278,426,891,160,49,70,-1000,465,179,-656,-1000,342,-400,95,38,-1000,-1000,277,-832,217,212,569,290,-854,-924,-512,-1000,502,-605,-246,-561}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{387,1000,681,-1000,-666,542,579,826,-1000,249,67,399,-146,84,-629,-863,-1000,811,1000,910,-418,1000,-120,38,-277,-306,-136,-1000,1000,344,-603,-106,-552,-784,-1000,-181,-1000,-836,579,387,208,620,207,336,-18,1000,184,366,-231,112,-344,-696,-764,320,-6,526,1000,-858,35,-1000,218,0,1000,890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{892,596,306,-51,-1000,684,1000,-1000,909,-926,-535,1000,1000,257,-914,1000,297,-340,-226,-426,-131,772,-805,547,758,1000,209,1000,-682,108,-113,-1000,-552,128,-710,112,-791,532,-158,1000,583,217,67,-1000,-615,1000,-263,-675,659,-860,-1000,609,849,1000,1000,-632,9,-622,683,-769,-642,-1000,400,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{-505,487,164,60,-541,-1000,504,465,1000,-20,-826,827,-910,144,-219,703,-668,-610,387,-131,237,-315,-423,822,845,-838,-141,186,771,-37,447,621,-877,1000,-1000,-584,744,-584,-88,776,-381,719,295,-641,-1000,96,-451,-273,609,-800,-424,-95,45,-474,566,869,-268,675,620,-499,-377,-1000,-452,-702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.String:LS04MTJE", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{-170,-1000,-7,-812,-681,-435,219,7,-400,611,-382,21,-555,105,782,604,-360,1000,-718,-213,594,252,-984,-190,736,95,674,-195,837,-338,-251,-67,587,961,336,5,223,-207,-36,-141,380,-647,594,32,51,334,-372,42,-1000,-616,1000,13,1000,-451,-890,421,-427,-473,725,81,970,581,366,699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.String:MHgzZTg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{653,1000,-905,-1000,708,-87,817,1000,942,-540,-574,-753,-1000,-633,-940,33,-1000,-589,1000,976,282,741,251,-566,-819,-614,1000,-493,-983,-1000,-1000,586,147,-490,217,-536,135,-1000,-691,1000,-612,171,-237,840,-269,-923,1000,962,-577,1000,1000,-879,-75,-1000,-519,450,-975,1000,-520,-962,570,618,-823,-775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{1000,199,1000,-539,-952,359,320,284,813,-212,-40,684,237,-1000,-604,1000,913,857,-437,-510,95,666,-592,419,1000,122,661,-610,242,748,-441,1000,775,-47,-146,-732,860,1000,-252,1000,-677,-411,1000,128,138,-879,447,373,-207,-1000,975,-190,575,-622,-159,421,-255,92,-604,-524,910,590,-1000,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWFhYWFhYWFh", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{-517,1000,140,-1000,-861,-154,559,382,909,-1000,-1000,60,-85,119,18,763,-955,-260,-1000,-232,400,620,-1000,615,729,1000,1000,-1000,-389,-1000,-330,-21,-287,-251,-582,-860,1000,1000,441,422,-905,695,-588,298,-615,-626,853,1000,141,518,237,-1000,1000,138,-365,11,1000,432,1000,-1000,999,-551,-523,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAw", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{1000,-80,1000,1000,-1000,46,419,-1000,1000,-400,-541,473,772,1000,-995,-324,-194,381,-575,-357,-305,1000,377,150,59,701,400,-160,-1000,-400,-1000,-1000,-1000,832,-1000,152,905,-412,59,-1000,-204,654,-130,-1000,-310,709,744,-191,-627,-140,-1000,-372,-12,1000,387,-1000,400,-499,1000,-469,48,-1000,683,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.String:IGFfX0dSLw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{1000,199,1000,-971,-1000,-622,352,446,648,-261,-268,563,-771,-1000,-1000,539,905,493,-479,-674,498,569,-1000,432,694,-148,-297,79,288,205,718,1000,817,-204,734,-1000,480,707,-476,919,221,-91,1000,1000,-243,266,-205,687,-373,-1000,917,20,899,-622,381,732,-1000,886,-350,-667,394,198,-553,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{611,-621,1000,876,1000,-621,-149,-395,1000,970,212,484,532,-924,226,-673,937,-26,-861,421,-149,-270,-303,-252,-565,-284,67,-70,759,807,223,-347,-1000,339,948,-772,1000,432,-699,-667,-93,-1000,135,483,-1000,1000,-130,1000,-702,-220,-208,-852,-868,-922,-60,-90,-981,-959,-81,170,-1000,-21,-1000,-219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{1000,-700,1000,1000,1000,-74,623,-397,1000,549,-487,221,1000,-1000,1000,-528,1000,767,-1000,-856,104,43,-485,235,-1000,-604,135,-334,1000,1000,1000,-1000,-1000,-954,482,-1000,1000,1000,-920,-314,-499,-1000,1000,-896,-1000,1000,372,1000,-681,108,1000,-1000,149,-1000,1000,-1000,-1000,-1000,326,-5,-1000,-118,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{1000,1000,952,400,458,-980,-606,3,612,548,-518,1000,131,-850,-199,-214,-367,-187,525,-529,-1000,179,-83,794,-1000,-516,1000,950,752,1000,-919,-968,91,869,-1000,-262,102,562,-489,1000,744,920,777,-156,-1000,-1000,-1000,88,1000,-563,-776,260,-1000,876,-1000,400,-1000,837,-973,-910,1000,949,-1000,-19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-1000,715,754,432,712,-78,47,-397,1000,504,-778,1000,1000,-1000,738,766,254,2,101,-1000,-493,-757,1000,-143,-1000,-1000,545,-139,1000,1000,1000,-880,-263,-402,-199,-871,422,1000,-1000,1000,-180,-141,1000,383,-1000,400,-716,1000,-1000,-893,205,-944,-513,-1000,866,253,-1000,-400,-1000,58,-46,898,-1000,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-357,-137,1000,-50,1000,860,-270,430,1000,833,-1000,766,543,-1000,-115,236,461,-590,429,896,-302,-1000,979,-1000,-553,-1000,-359,-266,392,-123,717,-257,378,1000,1000,-904,-333,1000,-1000,362,-1000,-360,-206,1000,-914,1000,-862,718,-1000,-727,-165,-1000,-1000,-849,649,537,-683,-1000,-1000,-234,-349,581,-878,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{962,-1000,-311,329,-257,342,-676,-349,1000,444,469,-982,-1000,103,-728,-1000,-983,-916,-481,1000,-446,-19,-1000,820,856,384,-152,391,36,-875,-747,903,-976,961,890,189,501,-1000,-643,589,707,-1000,-77,666,-1000,-46,-418,-1000,992,1000,-517,-300,-958,498,-584,-732,558,-152,589,21,312,-910,170,-529}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-528,1000,-66,1000,-334,-1000,162,539,193,610,-212,52,-667,-985,-284,-661,136,-430,-58,-42,-587,288,-774,712,-56,387,1000,932,1000,889,-617,-1000,-1000,1000,-856,-666,830,-70,-572,865,-20,566,-107,691,-183,-1000,-1000,-1000,-158,517,457,-467,-781,1000,-1000,-1000,-308,557,230,-549,1000,-451,-942,814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-1000,292,327,-40,1000,373,-146,-370,223,757,-120,416,497,-247,-435,1000,-25,-396,373,-360,-86,-823,1000,-1000,-378,-710,106,-1000,142,112,1000,498,-268,-529,558,-253,-541,869,-1000,971,-964,-819,397,1000,-349,774,-273,617,-1000,-566,239,-725,-170,-1000,916,67,-107,-658,-603,136,-626,476,-20,-5}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-995,1000,1000,143,210,-62,-996,59,51,207,-662,1000,0,788,-622,149,-713,-339,1000,-210,-1000,205,1000,1000,-1000,-974,658,515,272,587,-502,589,444,722,-1000,443,-544,294,-1000,1000,1000,560,801,522,-1000,-807,-500,69,-455,-558,-1000,1000,-383,1000,-450,1000,-911,680,-1000,-1000,510,1000,-728,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{102,1000,-939,-177,-26,-314,-468,714,-1000,-197,-349,-136,-1000,347,-595,1,-94,-982,889,-80,-235,195,-242,378,245,223,572,-40,-231,-212,-1000,916,494,1000,-942,513,-255,155,-990,1000,-19,729,-1000,1000,502,-1000,-692,-1000,-410,321,-145,483,61,1000,-1000,-547,747,1000,-139,-1000,526,30,1000,166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawFlag():int",
            new int[]{-1000,-1000,-167,-1000,-371,-180,389,-644,-33,-1,1000,-100,220,-738,515,856,173,1000,298,1000,977,484,-414,88,-27,-1000,210,-35,1000,-1000,831,-680,-1000,-169,1000,-1000,-1000,-154,-1000,178,-1000,5,-59,-22,13,-1000,1000,954,-578,867,-327,599,-454,1000,533,102,-409,-697,1000,-10,-211,808,39,-809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawFlag():int",
            new int[]{250,527,-580,947,-908,90,-379,480,-301,1000,-491,-363,1000,540,-273,486,-515,-263,-527,-23,-1000,-991,1000,699,189,-680,891,-536,-919,-181,-882,709,1000,-403,-173,1000,-33,-18,635,671,-624,-1000,471,-917,526,819,-360,-1000,-727,-1000,126,-466,388,-631,-993,1000,-634,411,1000,566,-156,-832,506,537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawFlag():int",
            new int[]{-621,-849,299,-1000,129,-113,105,961,970,-394,-719,310,92,-636,-351,-247,-653,-476,508,-400,1000,420,-1000,-251,74,-146,368,191,-127,-595,282,25,-716,-486,-41,-552,-86,875,-795,-192,-275,1000,-317,-88,1000,-677,250,19,-463,-481,-1000,413,-284,229,740,-186,-149,465,404,-307,-66,552,230,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawFlag():int",
            new int[]{282,-197,-800,-238,-1000,398,-530,496,-893,-45,801,232,281,160,-825,283,-491,1000,-517,-22,221,81,-521,779,-327,-74,270,-547,1000,-57,-349,725,-1000,-1000,-868,-627,-841,-1000,854,467,-1000,-129,1000,670,1000,-902,594,1000,-694,-993,92,-255,373,-483,-33,-375,304,246,-294,903,-259,1000,-1000,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawFlag():int",
            new int[]{156,131,-372,947,-926,12,43,1000,-379,1000,-491,-736,1000,102,-757,419,-209,-334,-673,1000,-1000,-370,1000,1000,529,-443,952,-826,-471,322,-1000,227,652,-403,291,1000,-109,-18,1000,609,-624,-1000,221,-1000,77,819,-1000,-915,-1000,-543,306,-383,391,-254,-394,1000,-634,-138,1000,-42,-676,-754,506,-328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawFlag():int",
            new int[]{-866,-881,-464,-1000,-439,-95,584,-382,-55,1000,152,107,543,-117,-761,-175,-581,683,-647,788,-178,-1000,-1000,941,230,661,64,316,504,-114,1000,313,-1000,-1000,48,-1000,-640,631,-303,345,-1000,-276,298,168,1000,-1000,884,1000,-922,-720,-472,492,-494,571,123,728,544,849,943,1000,237,1000,-672,764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawFlag():int",
            new int[]{-1000,131,1000,-140,-778,-1000,-958,426,946,258,-938,-736,-73,-531,187,125,-209,286,988,1000,133,1000,1000,-639,-225,1000,-266,-727,801,452,-1000,-1000,-421,-1000,1000,-1000,-261,-384,-1000,-69,-624,-828,-1000,-1000,1000,-964,-1000,-677,-974,-868,-320,660,-1000,1000,-394,1000,-1000,-1000,1000,-1000,-171,172,506,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawFlag():int",
            new int[]{-114,-1000,1000,-746,115,868,-621,532,47,-1000,-115,-902,870,102,-448,1000,-722,267,1000,1000,1000,-174,1000,1000,-175,-300,193,-871,-175,813,-1000,406,-1000,1000,-95,-414,-822,-1000,-1000,57,-1000,-663,-1000,1000,-550,819,-11,331,-841,1000,-576,633,318,-50,-394,1000,-68,692,1000,489,139,422,1000,-729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTc4NQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawFlag():int",
            new int[]{535,1000,-1000,1000,-1000,755,-326,1000,-1000,1000,521,-785,719,981,-109,1000,-452,-108,-329,1000,-1000,-113,1000,1000,-191,-799,1000,-1000,-764,754,-1000,-490,1000,610,-1000,686,-668,-1000,1000,1000,-983,-1000,663,-1000,-941,939,-1000,-1000,-1000,-237,738,-488,595,-610,-730,1000,-517,-525,982,-527,-455,-1000,-905,442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawFlag():int",
            new int[]{138,1000,-334,-138,-119,313,-267,-742,-1000,474,58,-784,-466,415,516,-1000,1000,204,845,958,-920,1000,1000,-587,-694,1000,1000,-1000,760,1000,-855,-1000,1000,-140,1000,219,-607,-906,-1000,1000,-788,-1000,607,-768,-656,318,-1000,-1000,-994,707,1000,1000,385,-917,-985,-455,-1000,-1000,395,-808,-77,-1000,901,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawFlag():int",
            new int[]{-1000,-1000,-513,-1000,-552,940,-146,601,-486,-394,165,310,1000,-636,1000,-758,1000,-296,508,-302,941,-747,-310,-966,-1000,-427,368,869,-1000,-626,846,-1000,1000,1000,225,-415,-1000,690,-1000,982,-704,188,221,-697,-545,-723,-1000,19,-1000,122,-1000,1000,-1000,720,1000,1000,292,390,404,-1000,937,-1000,-8,98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{563,-441,122,-41,240,-591,284,868,-1000,225,-193,-616,173,849,-676,206,242,266,35,941,112,-643,1000,297,726,202,-473,961,-767,1000,911,347,-1000,193,-486,563,-844,311,-446,-589,-477,654,175,-821,518,1000,-247,258,249,115,-228,562,-157,35,-62,-209,-16,-1000,69,390,623,501,-266,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{389,568,457,1000,386,1000,-231,597,-1000,471,744,-963,-65,159,-421,1000,231,1000,492,-165,-510,917,1000,-137,1000,161,-883,208,-549,484,-1000,-1000,-1000,-636,11,900,774,-428,829,995,-655,817,-1000,-755,492,-572,-857,1000,456,-1000,-664,-85,-988,558,-1000,-382,-404,798,-1000,158,-795,1000,-966,991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{286,-164,-5,-854,598,-716,258,-959,459,-716,-983,821,-971,236,-581,-71,450,-432,-545,752,322,121,619,467,-107,972,-11,197,-601,695,1000,590,1000,687,-863,638,-211,-163,-1000,-60,-225,-2,-66,-159,-529,432,1000,-413,422,-933,320,-404,1000,-202,-181,664,-273,-748,444,503,1000,-1000,-742,464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-609,-192,-1000,-183,-1000,851,493,1000,-536,-351,-1000,498,-1000,628,-695,1000,-1000,-405,36,862,577,959,1000,-1000,-918,1000,1000,-731,1000,782,1000,567,-904,-1000,399,-732,-161,1000,-1000,-177,506,765,1000,-375,598,810,1000,713,1000,1000,663,1000,567,-1000,420,1000,-961,931,648,-1000,1000,-1000,-1000,-447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-736,863,-1000,1000,1000,75,276,779,587,17,-1000,-126,42,-60,-828,829,-172,247,-241,862,-261,1000,1000,-935,-248,980,-81,1000,1000,782,470,-582,-1000,851,-859,557,372,1000,-803,505,409,-598,-360,-424,-53,-207,-531,-196,268,754,-367,-748,6,-1000,-578,1000,-352,-1000,-585,290,504,577,732,-464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-432,627,234,-1000,-831,370,437,-228,1000,-673,-1000,1000,-1000,802,-408,-655,-690,-443,246,583,260,-1000,262,977,-751,-221,-486,749,1000,954,939,130,1000,1000,-825,305,-909,-1000,-490,-1000,185,-388,236,-683,212,-270,-350,148,791,-1000,801,729,-844,255,331,657,523,-81,751,-175,-5,-995,-293,40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-151,916,139,796,49,-664,-421,362,857,486,928,820,774,-622,-278,190,-993,206,-752,-256,229,-165,-938,-150,-475,-315,-90,182,674,708,-953,-276,-525,-836,487,285,925,916,892,-444,699,-897,-547,140,-404,212,-597,-566,441,813,-421,-226,223,-490,869,813,192,30,284,763,-894,-205,924,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-1000,476,255,395,980,787,-1000,749,-1000,459,744,942,-663,-51,-955,1000,361,29,424,-488,-1000,984,844,94,258,372,287,523,799,262,527,-984,-852,-1000,-407,780,610,-873,-712,-88,149,-911,-848,24,846,-1000,-43,149,1000,-486,-548,964,-697,0,-514,580,-468,-969,-773,783,-294,-287,-818,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-1000,708,-145,-185,410,622,-1000,297,-79,46,647,1000,-1000,-585,-1000,868,-204,266,668,-722,-1000,1000,879,-47,726,811,806,670,859,49,1000,-582,-35,-838,-684,780,831,-1000,-446,-759,1000,-1000,-763,219,643,1000,-247,220,1000,-1000,-760,1000,67,-258,-863,-209,-16,-1000,-695,-147,179,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-261,-317,255,-175,1000,1000,-1000,-974,738,-443,771,1000,-145,481,501,837,522,343,424,-291,-402,-344,116,1000,141,372,-935,-418,391,-407,-503,-450,1000,665,-935,806,-588,-1000,271,-1000,-89,-71,-1000,-1000,599,-821,-764,63,616,-876,-67,635,-1000,610,-295,-1000,-468,1000,-110,924,-864,-49,-588,234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getRawName():byte[]",
            new int[]{-296,-280,-332,-636,-447,698,-127,-387,-98,-1000,251,-2,286,540,49,-152,-60,983,73,367,-33,-157,717,-412,1000,435,568,325,1000,43,79,-131,824,-1000,424,-177,92,34,500,-721,-474,-429,-25,-291,303,102,14,-51,13,705,957,-91,31,-389,38,522,212,585,1000,-786,-260,743,1000,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{111,-993,-868,139,-1000,928,1000,-1000,-1000,-1000,50,-811,141,-972,-440,-392,-159,339,772,699,1000,896,425,485,383,-220,-1000,594,-226,1000,290,305,1000,-574,-274,314,1000,146,-232,95,-48,1000,-1000,355,435,218,921,1000,690,625,-879,-327,1000,1000,-1000,794,-445,262,7,-265,-911,40,-214,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{-759,407,-213,157,-428,124,567,-747,-408,469,-658,-965,-624,-334,-246,-13,547,88,585,685,140,664,-498,520,499,-907,-998,887,-450,568,-221,-314,916,-568,-182,-103,817,846,-128,783,478,935,-322,-168,837,114,242,924,769,-657,-153,-571,736,903,-953,-61,810,-42,842,-853,-656,651,486,-947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{-167,-1000,-232,542,383,405,-987,-205,600,-733,1000,364,94,-720,-1000,-636,27,-547,363,147,-137,-1000,1000,-1000,-258,-1000,-1000,1000,281,-410,-555,511,-1000,486,509,872,334,34,415,-902,-462,1000,400,-920,-1000,-1000,331,347,633,1000,-1000,1000,1000,1000,-614,576,-1000,667,-683,966,-693,-856,997,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{-1000,-65,94,-260,737,457,-1000,117,930,-480,657,-221,-693,-106,-736,-906,756,-1000,-118,622,-1000,-658,77,-1000,903,-1000,-535,1000,-198,-410,-1000,782,-1000,864,1000,950,-519,919,400,3,165,100,1000,-55,-710,-1000,-571,539,1000,-74,-295,977,-292,-127,292,38,398,-76,156,378,-1000,-741,1000,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{23,916,290,582,1000,-751,733,-236,1000,-1000,-309,-337,-1000,696,-453,1000,716,-1000,633,535,-759,-1000,811,1000,-1000,-669,1000,237,-780,-105,1000,273,-1000,-835,-1000,-1000,-1000,1000,330,1000,-811,550,641,61,114,-530,-780,-1000,-828,-1000,656,-609,-986,-91,554,-1000,1000,1000,446,86,472,1000,-985,-323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{82,1000,463,1000,591,-1000,249,1000,452,-1000,-750,1000,297,-143,-980,804,569,-1000,951,-1000,-1000,-1000,1000,-456,204,-1000,720,-565,-180,750,1000,294,-791,-379,-642,-756,-857,-796,581,557,305,533,1000,-236,58,171,99,348,72,-735,477,-1000,448,595,19,-6,754,660,964,157,1000,791,-1000,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{423,1000,470,681,569,-749,859,-372,557,-866,-1000,-94,-548,58,-415,768,133,-651,735,-313,-266,-874,754,645,-727,-698,1000,-59,-479,-81,1000,-125,-791,-379,-948,-1000,-459,622,571,557,-357,252,1000,112,1000,-6,-295,-551,-52,-1000,388,-1000,-202,172,143,-326,1000,895,446,-713,548,944,-985,-184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{1000,-336,-531,832,-26,-948,442,210,374,-1000,477,873,125,35,-629,926,-393,475,783,302,427,-820,1000,-111,-1000,21,43,-338,-316,-1000,1000,-34,-1000,-643,-1000,-215,218,-265,11,-294,-606,711,400,-681,-840,-483,288,-721,-769,535,-954,-61,845,1000,-775,47,-126,1000,-198,1000,487,14,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{-1000,456,824,542,-258,-1000,-987,-205,-142,923,-1000,412,617,472,872,-339,-62,1000,952,-475,-137,334,-400,-272,1000,-212,415,76,-794,-1000,-348,-811,473,-870,581,-741,-148,-322,247,-348,1000,-1000,1000,-920,1000,1000,-134,1000,593,-639,1000,265,1000,-905,579,140,1000,667,1000,-1000,987,1000,330,609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{1000,-1000,-133,64,-874,-626,877,210,-663,-1000,569,920,782,-10,-492,459,-1000,775,1000,70,573,-839,1000,-681,-1000,409,4,-758,-330,-1000,880,-983,-1000,-772,-997,-132,699,-672,292,-652,-603,711,-135,157,-1000,-830,963,-368,-570,1000,-555,951,1000,1000,-561,376,-809,781,-1000,1000,803,220,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{835,759,1000,1000,1000,-534,238,17,-397,-1000,-802,683,1000,266,23,115,993,35,344,-1000,-601,-1000,1000,-247,568,-1000,-451,-1000,660,84,1000,1000,-1000,-396,-56,-471,-513,379,227,-890,-665,1000,1000,-611,142,95,655,-419,95,-519,-358,-74,-741,981,-332,-87,753,1000,-569,-1000,1000,-218,193,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getSize():long",
            new int[]{-894,15,-498,-1000,192,-1000,-168,492,956,-267,1000,13,-1000,820,-780,-102,705,-192,26,1000,-398,-887,1000,-927,-1000,-439,742,901,-1000,-1000,-1000,939,-1000,783,1000,465,-1000,1000,-314,950,-745,-530,13,935,-1000,-353,-1000,-1000,44,-615,-540,300,-1000,-930,1000,-218,-475,-732,403,536,-472,-1000,-40,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{-221,579,-150,-176,9,1000,1000,-1000,-662,702,534,-884,-177,-486,-613,-161,-456,-1000,826,262,353,715,1000,-1000,1000,762,-730,777,-638,1000,686,414,-1000,-768,-959,161,-526,-1000,151,1000,12,187,212,305,-904,-261,1000,-553,-647,473,-264,1000,567,-382,-241,466,-653,-422,-242,849,84,1000,-550,-652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("java.lang.Integer:NjU1MzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{-1000,-1000,-1000,868,-713,-1000,-506,-753,-974,-351,269,404,-1000,-779,-603,658,314,301,-329,-693,477,-873,-1000,-283,-613,654,703,-811,1000,-916,787,-778,500,345,534,-1000,1000,351,607,-886,1000,-862,-1000,-497,-536,-790,-344,-1000,-805,-530,578,-570,370,541,976,-518,874,284,691,-935,-448,717,627,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{-424,-13,-1000,417,600,929,-506,-365,-974,-162,-37,-217,970,-630,424,-826,-125,-716,1000,552,-40,951,937,738,-646,654,-431,-1000,-822,-916,-790,636,500,-23,-190,255,-192,-1000,-133,-791,1000,-862,-223,-497,-99,892,-336,754,-805,-530,827,1000,-1000,-353,976,222,-772,313,582,729,572,315,-332,-855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{202,1000,-892,-24,367,59,1000,106,-739,-909,698,119,982,771,-1000,-618,-495,-1000,-1000,1000,987,775,95,341,-68,177,353,1000,246,-877,870,1000,611,-81,722,-416,756,845,481,-1000,858,152,-661,-1000,-72,140,-653,-366,-736,6,1000,-546,-257,-1000,1000,-999,-674,997,1000,-359,-327,424,264,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{-1000,-1000,-1000,1000,-1000,-1000,313,-942,-889,1000,590,-583,-1000,-1000,1000,1000,1000,451,826,-1000,1000,647,-538,-949,-1000,287,1000,-1000,194,-1000,572,-1000,-917,869,-204,-1000,559,1000,1000,-1000,59,445,-1000,16,-1000,-1000,-1000,-1000,947,-759,1000,-1000,-84,1000,-1000,947,1000,-318,356,297,-352,1000,1000,-865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{362,1000,1000,-1000,879,1000,208,-743,-1000,50,407,101,561,517,-510,-492,-1000,45,1000,544,-1000,96,442,304,1000,1000,-923,1000,-536,1000,-504,1000,-941,-1000,-572,85,-199,-1000,-194,919,-451,141,548,449,18,-1000,1000,-327,-630,104,852,-1000,798,-1000,1000,16,-1000,1000,-402,121,-228,-409,-1000,-282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{-380,-1000,-1000,358,1000,668,-506,-535,-25,76,-290,-848,-679,-971,781,-1000,-375,-1000,518,1000,147,1000,958,-283,-106,489,703,-811,-948,373,-800,-778,662,-298,534,52,630,-2,-883,-476,1000,-1000,-1000,-456,-751,389,-867,-178,-803,406,578,-245,-1000,-705,980,1000,-445,-1000,1000,1000,686,672,-502,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{-195,-981,35,1000,-554,-489,-1000,-764,458,-411,393,1000,-579,241,959,-81,324,1000,-596,-821,387,-981,-646,578,39,120,539,-121,-112,-1000,567,-168,-932,231,1000,88,843,124,4,-286,-208,-111,0,858,720,-46,-657,-727,718,509,-230,883,708,642,-972,-1000,207,521,-195,-1000,-19,167,-589,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{-221,1000,921,-582,-238,1000,898,-1000,-699,-500,1000,-884,288,898,-1000,-1000,-226,-64,-203,262,353,-595,-530,697,61,52,814,1000,1000,804,-271,1000,-231,-768,196,161,1000,-893,-987,147,29,-456,518,-777,972,702,703,-553,371,-464,-163,1000,334,-382,741,-859,-355,893,-242,-1000,84,-391,-1000,-652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{930,-981,655,-773,421,111,-432,-68,-692,-775,651,724,119,842,-897,-510,-881,-233,308,1000,-1000,-294,-419,578,39,454,539,544,-112,420,17,371,477,-589,1000,-487,909,-397,-700,-286,704,335,-683,-307,957,776,396,-396,578,-1000,868,-362,376,-1000,595,-1000,-510,338,620,-689,-1000,-578,303,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{13,-477,479,683,-184,647,1000,-614,140,297,-17,-1000,583,-927,947,-249,202,-1000,-75,799,626,676,748,-867,-1000,838,-219,-1000,-1000,-139,-307,511,-364,369,422,447,232,400,-1000,133,946,-723,845,-656,-1000,202,-550,-115,-8,-896,973,-1000,442,199,-106,1000,85,-376,619,1000,113,-257,-19,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{658,-1000,1000,68,-904,-223,-422,357,423,1000,1000,-316,-526,802,551,-206,422,138,-124,428,218,286,-445,489,1000,1000,19,-5,533,962,1000,-600,604,-859,1000,692,-1000,-734,485,431,-234,-1000,746,1000,60,-248,10,-265,467,-917,1000,-333,-602,-890,765,1000,893,-1000,460,928,977,556,207,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{658,-1000,1000,157,-917,-510,-352,868,423,1000,1000,-271,-223,1000,903,-404,1000,515,-389,428,401,46,-573,370,1000,1000,895,-143,533,1000,1000,-758,800,-1000,1000,692,-1000,80,809,96,-1000,-1000,210,1000,60,-16,99,-302,429,-1000,1000,-1000,-374,-1000,1000,1000,485,-1000,1000,1000,1000,568,207,-935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{-1000,-669,-1000,-1000,637,-628,1000,193,1000,507,604,-222,233,1000,-186,1000,1000,-37,214,-841,-382,-131,1000,-1000,914,-1000,-1000,399,-705,264,253,-73,-284,726,274,590,537,-734,-1000,1000,1000,1000,955,-131,-138,-570,-672,-386,-421,533,336,-871,51,1000,-756,880,1000,-1000,600,-134,132,-312,77,-645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{1000,1000,1000,1000,-858,1000,-961,-23,-838,-777,993,1000,1000,-637,-526,-111,-1000,459,515,606,-676,-816,-906,807,-15,934,-1000,200,-411,885,-1000,282,-1000,-197,-347,-1000,-1000,-477,818,-268,128,-191,-381,-21,-974,1000,-533,591,-970,-239,-750,391,94,1000,485,-1000,-1000,1000,-646,492,-1000,219,416,684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{-183,1000,-127,-1000,901,-936,-286,-113,946,1000,780,-79,-764,234,833,1000,153,-33,339,77,118,272,547,-1000,951,-540,374,435,-161,1000,767,-192,177,-180,-408,186,71,-165,-633,1000,621,124,-89,448,596,-789,-327,-339,-128,140,565,391,-560,560,189,1000,1000,495,1000,339,-354,-35,-1000,928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{526,383,51,972,-96,-1000,-1000,685,157,1000,1000,623,-358,261,1000,-556,1000,154,550,148,604,264,-165,1000,961,1000,-238,-1000,563,1000,819,-1000,1000,-623,20,1000,-172,303,971,106,-1000,-1000,678,-53,-357,489,779,-596,566,-578,1000,-1000,-1000,253,534,814,676,-906,1000,525,-139,-36,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{1000,1000,263,157,480,-306,-1000,-734,-42,410,993,1000,-150,-96,1000,-870,1000,221,785,402,-362,-312,-1000,57,1000,766,-1000,-581,654,386,-185,487,737,-521,1000,1000,-700,1000,246,868,-795,-638,27,862,-1000,-230,-266,718,-295,50,60,-924,-1000,911,330,-358,-468,-1000,334,-568,276,-662,51,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{1000,-1000,1000,-62,-1000,-104,-692,277,-1000,296,930,6,-853,-171,467,-220,486,839,-1000,1000,14,-455,-1000,16,1000,1000,-140,1000,1000,-114,1000,25,656,-1000,1000,-757,-705,744,339,-235,-935,-1000,-1000,1000,-962,-778,-352,997,-164,-831,1000,-544,56,-776,1000,828,-692,-400,874,1000,1000,976,140,-518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnparseableExtraFieldData():org.apache.commons.compress.archivers.zip.UnparseableExtraFieldData",
            new int[]{-1000,-400,-1000,-1000,1000,-510,674,-158,1000,1000,1000,151,-223,1000,977,-404,572,-1000,785,-1000,-67,795,1000,-1000,1000,-1000,46,-375,274,348,969,-758,503,855,12,1000,1000,-326,-1000,1000,1000,1000,943,-359,-645,-859,-28,-1000,-290,1000,147,391,-1000,1000,-1000,1000,485,-1000,963,-586,1000,-603,-1000,-539}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionMadeBy():int",
            new int[]{-454,-1000,-788,-87,410,-753,29,10,1000,-999,195,-107,113,538,-82,-712,713,50,-277,-1000,744,429,-416,-679,525,383,261,498,-636,-518,271,-85,-630,-256,890,-859,669,-94,998,96,-1000,-452,-266,-158,465,-990,-593,-930,579,-605,43,-986,-844,866,-493,-563,-254,567,1000,-535,-37,365,-221,440}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionMadeBy():int",
            new int[]{-454,403,744,413,-1000,-230,-1000,1000,938,-279,1000,-626,-862,1000,-1000,858,713,1000,1000,-654,744,429,-416,-679,-1000,396,-213,-1000,-1000,-1000,611,753,193,-728,-267,1000,1000,-1000,-1000,-1000,1000,746,-103,1000,136,76,1000,-1000,-1000,-1000,537,398,-831,428,-493,407,-358,-670,-1000,-535,72,781,1000,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionMadeBy():int",
            new int[]{-509,-1000,-1000,1000,9,108,-723,-357,-20,312,470,-724,-132,445,591,-1000,-607,263,-1000,-920,1000,74,63,-78,-1000,1000,1000,-682,216,1000,605,-362,-655,-1000,901,-973,986,-853,638,221,141,469,-167,-826,-783,-297,-1000,601,-2,-715,1000,-644,-881,1000,250,254,910,-771,-333,-555,-677,1000,-541,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionMadeBy():int",
            new int[]{-1000,-1000,-712,239,434,507,924,1000,-1000,-838,565,674,-731,107,158,-700,-311,475,-277,-481,528,-368,-254,-1000,8,436,-56,-311,395,-507,-273,-592,-1000,-1000,185,-294,1000,-161,-455,-1000,-408,1000,490,-198,-216,-308,-284,-53,894,863,153,-64,831,1000,-700,352,-247,1000,1000,-548,1000,977,-901,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionMadeBy():int",
            new int[]{-816,-173,-1000,-87,-169,-1000,-1000,10,767,401,378,-887,-395,1000,244,-766,713,50,-1000,-1000,1000,955,-210,-300,-484,552,896,-527,764,164,1000,885,-60,-266,929,-420,919,-323,16,-322,-913,-452,-671,413,270,-1000,-1000,-495,-821,238,66,-962,-1000,873,-793,-219,524,-735,-296,-998,-1000,560,-320,428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionMadeBy():int",
            new int[]{-1000,1000,410,-1000,-1000,-713,-818,-1000,308,-75,-1000,-1000,1000,849,466,485,662,-1000,-975,140,596,711,-340,538,520,1000,78,105,-487,-678,251,1000,560,129,-192,-927,-897,723,1000,127,-1000,-1000,37,-258,1000,-1000,306,-112,491,97,-421,-584,-1000,-1000,1000,79,1000,-1000,340,-6,-1000,617,-146,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionMadeBy():int",
            new int[]{561,364,193,-572,-1000,-147,-409,-724,-1000,50,-1000,-540,1000,613,540,616,346,-1000,367,-214,-1000,763,698,350,1000,827,-2,-297,-866,-418,172,658,46,355,-1000,-946,-292,423,1000,1000,-280,-962,13,-231,978,-750,117,1000,1000,144,120,268,-1000,-1000,1000,46,775,-1000,200,137,320,767,-584,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionMadeBy():int",
            new int[]{-836,-25,-165,-16,-973,-255,-1000,-535,895,-267,-394,-1000,826,1000,393,-510,-13,-401,-983,-903,717,1000,398,999,-276,1000,396,-449,-397,-117,1000,755,11,-678,67,-1000,374,-377,1000,223,-418,-188,115,-587,340,-1000,-164,-178,230,-954,-344,-392,-1000,-400,890,-370,1000,-1000,-770,-1000,-960,1000,57,839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionMadeBy():int",
            new int[]{-1000,1000,445,-1000,-481,-911,-552,-406,-371,111,-1000,-217,1000,-244,931,-314,373,863,-1000,-157,392,915,-584,-698,956,1000,148,-484,574,-274,274,524,34,1000,-665,-1000,-1000,1000,1000,945,-443,-1000,655,-194,128,-1000,31,925,1000,1000,-1000,257,-651,-435,868,-733,630,-1000,168,-339,-611,-305,-941,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{-808,-317,-96,-495,1000,-1000,-313,50,-137,409,676,-36,-327,-13,294,926,802,214,-233,953,-1000,965,-881,-323,995,1000,86,-738,-800,579,1000,418,1000,-382,-649,1000,389,-287,-1000,1000,-1000,-1000,-1000,-1000,1000,253,-319,800,-733,58,-180,797,-319,-146,-899,517,-1000,-40,-50,-676,746,-940,-1000,779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{-1000,-1000,-1000,-1000,-621,102,-686,1000,1000,958,1000,1000,-279,21,205,1000,857,1000,-1000,-795,-124,1000,-981,-312,-1000,1000,497,-589,-972,660,1000,1000,1000,-1000,-828,1000,389,-787,-383,1000,-1000,-1000,-1000,766,1000,1000,79,1000,1000,-1000,1000,873,-1000,-1000,-329,1000,-1000,-164,-1000,-1000,1000,65,-267,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{405,1000,875,278,628,525,-311,130,-946,-1000,-642,-992,677,-476,-884,-1000,-1000,-714,1000,-933,1000,-249,-630,1000,-260,-1000,1000,1000,-428,491,-1000,-1000,-361,16,1000,-1000,63,-362,1000,-93,1000,1000,1000,1000,-1000,-1000,-861,-715,-423,1000,-162,189,516,-910,1000,-1000,1000,-1000,661,1000,-1000,-895,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{1000,-25,94,1000,1000,124,-166,-1000,-938,966,-537,-992,-215,1000,-510,-1000,-656,-714,1000,-1000,-229,-298,-630,1000,708,266,138,-98,43,900,-1000,-1000,189,40,-747,-1000,1000,-238,-226,1000,1000,1000,746,886,-862,-1000,-204,-1000,-618,1000,-1000,-389,662,-296,1000,-824,1000,-569,1000,1000,-1000,-889,-81,396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{-544,220,924,-831,-1000,2,139,36,-36,-671,466,20,-645,-1000,142,474,3,45,-54,-661,-1000,817,885,-87,-1000,251,312,4,795,-777,-1000,-1000,992,1000,1000,343,-864,378,382,648,-1000,-471,478,-1000,442,-704,-806,1000,839,1000,-1000,-434,338,-937,90,-185,336,-1000,144,101,-421,-808,853,-520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{-158,-1000,-145,-1000,660,60,-720,-836,1000,-361,1000,1000,621,-451,-641,-460,-21,2,931,-44,1000,730,-140,941,570,-1000,1000,1000,-394,484,-505,711,-255,-399,-246,-319,920,-801,1000,-79,816,400,567,322,-21,813,-213,885,-1000,-1000,1000,577,-429,-722,31,1000,778,-832,-466,-391,481,-1000,-46,323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{-392,-597,-278,-211,628,-308,641,-926,404,-1000,669,815,-612,-400,-218,41,174,388,817,353,-99,628,-382,498,-260,-505,-22,1000,211,622,-172,-250,213,732,-434,-408,-53,-602,1000,302,-1000,-134,372,-462,256,-968,-1000,222,-28,541,400,436,-161,-810,93,-284,269,-713,562,-126,-316,-744,923,-183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{-948,1000,-1000,-935,688,-516,94,-542,-1000,-1000,-410,7,-166,-1000,-334,70,330,561,1000,465,-1000,100,265,981,-964,-466,1000,960,-1000,829,-1000,-1000,647,316,1000,-1000,-670,262,-616,1000,-1000,67,1000,-660,43,-1000,-503,527,-713,1000,-18,-152,791,-1000,1000,-1000,697,167,263,525,-902,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{-575,1000,762,420,-656,-298,573,-652,233,13,-1000,-1000,819,287,502,-382,198,-261,411,-1000,198,-1000,94,563,964,461,298,65,36,848,-330,-626,462,-121,-504,629,633,-18,159,-186,322,927,-197,734,-1000,-287,201,-928,-424,-507,-503,-812,606,125,933,-969,-75,324,588,375,207,299,6,516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{354,-788,591,-333,1000,-452,979,-741,1000,740,1000,-26,-44,261,275,14,1000,-819,1000,666,-606,871,-1000,-345,791,-64,901,-19,348,829,-450,749,479,185,-1000,905,1000,-347,18,522,408,400,-13,-691,1000,1000,-550,486,-1000,-572,-870,331,307,-379,-471,931,426,-145,-49,-569,1000,-82,-1000,887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{695,8,191,278,1000,280,-253,1000,-130,-351,-356,154,389,87,-1000,-1000,-788,-392,1000,-933,915,-249,117,1000,1000,-1000,971,1000,-933,639,-1000,-775,-710,-2,311,-1000,492,-466,1000,208,1000,1000,1000,1000,-1000,-936,17,-715,-1000,454,262,374,711,-328,1000,-902,1000,-701,999,960,-1000,-671,1000,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getVersionRequired():int",
            new int[]{-283,355,-314,973,766,802,318,-376,1000,-264,275,1000,253,-864,-1000,-129,109,-905,-31,-697,1000,-676,881,1000,486,-798,698,569,-304,1000,342,-324,-97,-74,-246,543,121,31,566,192,-146,345,1000,820,-697,-1000,1000,164,-340,522,-89,636,666,-914,412,5,776,-807,575,-370,115,-845,-1000,636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-832,183,970,-300,-207,1000,-850,324,-92,413,-959,-757,-214,334,-309,124,-1000,-1000,535,-286,752,-131,779,-12,82,1000,-119,3,-359,718,323,90,246,1000,225,152,1000,-1000,1000,148,-758,-308,-451,1000,-1000,1000,424,-193,-1000,-449,145,-20,-447,-11,-348,-641,-750,-184,1000,-201,608,143,-398,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{633,-1000,1000,-87,401,360,-566,1000,-1000,-351,-387,-1000,-1000,-126,490,-809,-511,-23,624,1000,1000,-256,-286,136,-294,-746,646,-447,-190,-1000,8,41,-1000,104,1000,-358,889,1000,1000,-694,-586,-625,-659,-185,639,203,-677,215,-1000,-97,283,523,-471,-1000,-354,901,-278,-340,840,-984,-364,771,1000,-771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{432,972,106,1000,379,292,-182,527,353,71,-1000,-167,-450,118,-160,-213,-637,-1000,-452,-920,-8,-1,-188,-709,-612,-977,-580,512,-1000,1000,-724,-877,277,-75,168,-757,-196,-1000,369,661,-1000,-74,-1000,-117,-637,-769,-576,10,-192,-208,-828,-1000,123,979,393,145,-36,953,-63,-167,777,-400,-502,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{1000,867,-1000,-401,-382,475,342,15,1000,1000,-388,573,-688,116,976,-1000,-823,-658,-1000,-1000,-187,1000,-586,-862,-327,-1000,-1000,-225,604,1000,-1000,138,-708,-1000,-650,-1000,-473,-982,-507,1000,-263,462,283,-875,-1000,-1000,795,36,574,1000,-41,-861,351,906,841,-810,-1000,930,-118,648,825,-1000,-736,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{1000,-200,-1000,-1000,767,-723,133,-678,286,722,-1000,69,624,-474,-87,-1000,-449,-400,351,-480,-1000,521,-70,-1000,933,-1000,92,-731,-672,-1000,-1000,-113,-1000,-943,-1000,-1000,-1000,-734,562,1000,123,-60,317,-154,-1000,-888,1000,-1000,-480,-294,-1000,-1000,691,-234,493,-388,-1000,1000,1000,77,205,-620,-645,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-261,-1000,790,-1000,680,-846,-928,1000,-539,1000,-374,-998,-1000,69,-87,-1000,-997,648,351,1000,735,-615,74,-269,-169,968,619,-1000,505,-1000,1000,-1000,-1000,646,154,-65,736,1000,562,-554,-252,446,37,-262,1000,1000,-182,932,19,384,-55,936,-666,-751,118,414,-654,-117,784,77,-1000,658,984,-124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{318,-813,1000,-1000,1000,-1000,-137,1000,-634,329,115,-872,-968,1000,-662,-352,-215,55,233,1000,1000,-208,-745,-145,-167,1000,1000,-1000,-314,-1000,1000,-1000,-448,603,247,1000,375,1000,1000,-905,-846,446,-240,504,-1000,1000,-559,812,55,311,398,551,334,-1000,301,227,-262,-816,1000,382,-1000,1000,634,258}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{1000,631,379,779,414,-1000,-918,-204,-361,-887,-90,-1000,-465,-319,304,-290,-821,1000,-97,-286,-87,-977,-1000,-265,469,-1000,1000,-1000,429,774,-1000,-1000,-1000,-1000,50,-1000,-848,-1000,-200,193,605,-736,1000,-694,-741,-1000,1000,250,-1000,649,-1000,-398,-408,-1000,462,-597,-1000,71,1000,61,-1000,-798,1000,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{633,-1000,543,-258,737,543,291,738,1000,1000,-453,-314,-1000,51,-1000,-1000,-998,-1000,-592,400,221,662,472,-479,311,1000,-1000,359,200,-1000,1000,-877,-197,1000,-441,-455,766,1000,-47,-902,-694,168,-999,-210,400,1000,-778,659,1000,-123,718,955,-518,995,-670,580,1000,-590,-53,589,205,160,-1000,-579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isUnixSymlink():boolean",
            new int[]{-37,804,-1000,-337,650,-947,-787,328,-1000,1000,172,-295,324,1000,-831,1000,121,54,-1000,-1000,160,148,-147,1000,-352,-922,-433,-1000,-1000,1000,-165,-85,1000,341,1000,1000,1000,725,-413,416,-137,140,517,-253,-374,-458,-1000,-1000,839,897,1000,1000,-42,1000,-654,-910,-1000,-1000,922,719,1000,-784,561,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isUnixSymlink():boolean",
            new int[]{523,947,-924,875,152,-978,693,1000,1000,-574,-128,-181,828,-413,-773,-534,601,193,-270,854,326,-1000,-935,875,-682,592,86,-95,1000,-684,901,-100,872,-558,138,-350,-289,365,-600,-1000,-414,265,459,1000,-599,-991,-261,784,-500,533,801,339,-1000,-949,-645,-256,-55,972,-995,-131,-232,343,-330,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isUnixSymlink():boolean",
            new int[]{-1000,1000,-182,1000,-908,253,-182,-709,1000,946,1000,-312,-198,94,214,-740,-962,37,155,-553,-862,66,428,519,-108,114,608,-199,668,599,-782,-662,121,518,244,285,646,1000,-9,1000,-602,-648,-1000,-1000,1000,-622,-867,1000,-1000,-1000,585,1000,-171,463,-611,-1000,-594,-75,-1000,-212,-847,927,492,235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isUnixSymlink():boolean",
            new int[]{-1000,1000,-382,-155,235,-281,1,-764,1000,1000,-256,-263,-77,1000,-938,218,12,-162,489,-22,952,1000,886,884,-186,-1000,966,1000,-1000,382,382,-1000,1000,771,-31,395,654,1000,389,1000,1000,-1000,-664,-816,1000,455,-1000,639,-661,-691,-822,634,480,-197,-1000,1000,-352,1000,-865,-1000,337,1000,-352,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isUnixSymlink():boolean",
            new int[]{-744,796,-941,945,-299,-836,285,346,751,550,128,-654,733,168,-824,113,-157,-107,-625,235,16,537,-7,1000,-449,-76,301,-76,119,333,513,-458,963,120,150,-33,359,848,-276,-209,-64,657,490,-78,306,-463,-651,412,-261,-622,236,674,-516,-380,416,-234,-161,224,-996,-493,592,410,-1000,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isUnixSymlink():boolean",
            new int[]{-998,1000,-1000,438,1000,540,-877,-670,-319,1000,504,-596,1000,-231,67,-1000,-1000,-1000,-1000,-936,-813,-1000,-792,1000,-418,-1000,1000,-63,1000,1000,-905,982,1000,-1000,-1000,1000,1000,924,-1000,-474,-1000,1000,1000,377,-759,685,-1000,-1000,1000,1000,-210,1000,-245,-494,-577,895,-1000,-927,-159,101,675,-1000,-1000,-888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isUnixSymlink():boolean",
            new int[]{-633,947,1000,-934,278,464,-815,-1000,329,257,-582,123,-292,-44,-548,372,376,229,620,-86,1000,702,486,61,526,-1000,801,1000,79,-642,-411,-610,457,-267,-1000,453,-768,327,379,874,312,-773,221,-66,5,421,-742,791,-38,-639,-1000,34,177,42,-1000,1000,684,441,40,-623,-1000,456,-631,-818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isUnixSymlink():boolean",
            new int[]{972,-865,1000,-1000,1000,1000,-1000,-734,-924,-921,-706,239,-704,-1000,275,585,-47,877,337,-650,1000,-518,15,-832,1000,-465,-44,-189,728,-1000,895,518,-1000,-1000,-976,301,-1000,-736,340,162,732,-169,877,528,-1000,-313,-642,624,550,-667,-48,-318,-936,902,-365,500,1000,432,1000,640,-1000,-988,-192,140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isUnixSymlink():boolean",
            new int[]{458,1000,221,-74,1000,666,-39,439,-441,-142,-1000,651,72,-288,625,-102,239,1000,213,-458,-624,-1000,1000,-178,919,196,-472,-426,861,-1000,-1000,482,-824,-1000,720,-310,-253,-140,-583,-643,-1000,-911,583,99,676,1000,1000,845,-306,-350,1000,30,-1000,125,565,-54,403,558,279,661,-846,-125,643,703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{573,311,1000,-1000,445,817,-155,-1000,-574,-1000,-834,-741,381,-261,1000,1000,-49,436,-1000,76,-1000,1000,-919,-1000,-857,-1000,-101,357,-1000,1000,428,475,-124,-875,-545,1000,1000,-70,-1000,-1000,918,308,1000,667,638,766,182,-655,-348,955,337,-1000,-356,1000,764,-1000,474,212,107,1000,-359,-536,-215,-821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-1000,-1000,920,583,-1000,1000,-926,226,377,6,-581,-1000,-525,-439,-427,1000,-268,992,702,110,-14,1000,667,355,456,738,-1000,-170,-391,160,75,924,-918,251,284,269,-1000,-179,1000,1000,-376,-739,-1000,-1000,954,1000,-783,-1000,-535,1000,378,403,-477,-149,1000,-297,193,955,1000,327,-922,904,817,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-864,835,879,246,244,-377,1000,-266,-128,817,1000,-353,521,-974,-806,-567,1000,-971,350,509,-853,-556,-197,-918,704,-383,1000,1000,-987,367,1,-137,-1000,108,33,202,232,-331,-784,-137,423,227,-186,149,1000,945,-985,-950,-362,877,-1000,-137,679,-504,1000,-412,-1000,1000,119,-195,-938,88,-550,856}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{290,1000,492,-1000,1000,-682,-141,-370,195,-606,367,551,400,683,396,-1000,-400,400,-808,-695,429,107,-738,-1000,-671,-1000,270,-393,788,-409,505,-400,1000,-316,-634,774,1000,-244,-1000,-853,-1000,1000,1000,400,30,-433,786,874,100,-1000,-378,390,208,1000,-400,-537,-54,-1000,-792,74,1000,-1000,-154,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-964,371,-465,-495,-123,-654,-242,653,-679,-1000,-581,-1000,849,-634,1000,1000,-1000,748,-1000,445,279,1000,-1000,-1000,-1000,-400,-908,-698,1000,1000,849,-177,716,-1000,492,1000,1000,1000,-996,-634,-923,-933,-164,782,1000,1000,522,-525,462,1000,1000,-685,789,1000,-1000,-1000,366,1000,-85,-515,-1000,-1000,839,-224}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-1000,864,-599,599,-1000,160,189,370,1000,1000,1000,-209,-106,-1000,-1000,-324,565,-198,1000,27,17,-1000,1000,661,1000,1000,-1000,1000,1000,-972,-236,-176,-679,112,638,-1000,-1000,-966,1000,1000,-1000,-1000,-1000,-1000,573,1000,-352,705,-327,1000,-608,-307,54,-1000,789,1000,-588,638,-480,201,-1000,1000,422,673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-82,-580,920,203,63,-25,-906,39,250,-414,-637,-819,378,-1000,23,454,1000,1000,93,-852,667,1000,221,-319,-277,909,-891,-719,-500,-287,300,272,-987,-132,-397,689,-1000,-454,222,1000,-415,-110,-189,-243,954,694,-931,-1000,-880,969,1000,-14,32,5,-22,1000,-557,679,1000,-138,-762,226,972,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-1000,-1000,-142,-398,-1000,297,-157,1000,370,1000,1000,-932,-372,-1000,-1000,873,1000,712,1000,381,-311,-1000,1000,1000,1000,1000,227,1000,1000,-131,-194,-567,-1000,77,1000,-1000,-1000,-1000,1000,1000,-794,-1000,-1000,-1000,762,1000,-467,400,-97,-1000,-1000,-1000,-547,-1000,463,1000,190,1000,-155,-1000,-1000,-856,257,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{415,1000,111,224,1000,-333,1000,1000,-106,-70,1000,358,-81,-1000,-483,-69,158,104,-102,-531,387,-223,187,-326,-828,-695,156,-187,-501,-169,1000,110,620,492,-208,-1000,961,-306,-957,894,-1000,1000,1000,1000,146,108,128,-195,728,-48,-621,690,-581,810,258,347,1000,-1000,-544,879,255,-222,155,451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{536,-9,652,-733,267,194,408,-266,-423,-63,94,-23,554,-627,491,833,379,-151,-697,76,-523,651,-343,-918,-386,-613,376,285,-987,948,244,-35,-924,-504,-323,327,652,-218,-815,-434,880,241,794,710,614,766,200,-655,-803,855,-136,-950,-102,896,451,-641,-258,771,91,345,-359,-345,-233,579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{45,-57,1000,1000,-1000,1000,-517,1000,793,1000,404,-366,-1000,-1000,-421,261,909,-1000,903,1000,179,-1000,1000,1000,743,1000,1000,-38,1000,-171,721,892,-212,914,1000,-1000,-1000,-1000,1000,1000,-191,-1000,-1000,-1000,1000,1000,-211,-1000,-551,793,-110,-154,-1000,-400,249,1000,1000,880,59,-960,-333,1000,182,251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{235,691,183,514,-254,522,-1000,-243,617,-887,-590,340,132,-309,-20,-722,240,64,-481,880,180,493,-45,-958,-5,282,555,-1000,-220,477,172,566,-400,523,927,867,20,225,-653,-300,1000,752,332,848,-87,398,451,-132,1000,850,578,755,249,-60,-1000,1000,233,-404,-1000,1000,759,-1000,336,-232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{-1000,47,411,1000,372,-17,-948,-1000,124,329,1000,-241,-1000,-1000,-1000,-931,1000,562,865,1000,-385,-922,-111,-1000,952,-891,1000,939,-84,578,1000,1000,1000,641,594,793,288,84,-1000,-1000,583,564,1000,1000,238,836,95,-521,-53,-236,-1000,1000,1000,1000,1000,-586,1000,-1000,-832,400,1000,-634,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{-144,1000,1000,-1000,-667,-434,119,467,-1000,158,116,1000,-812,131,987,1000,-557,260,366,-779,645,557,217,-47,1000,-1000,569,-98,-760,-1000,389,440,-491,-1000,-1000,-590,-587,-119,1000,201,572,400,-275,400,556,72,-78,656,463,-1000,104,-1000,647,-1000,1000,-540,-715,858,816,-843,-396,878,-1000,675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{-385,-605,-1000,-644,-790,815,1000,-906,-1000,1000,331,-226,205,18,1000,401,-417,-1000,804,988,98,-764,-347,-1000,-13,-651,408,1000,352,676,-1000,1000,1000,-1000,-757,-1000,-1000,-564,-226,1000,-69,1000,937,1000,964,-1000,-348,-772,-1000,-925,-443,-264,1000,-721,577,-534,-351,-536,1000,-1000,371,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{-360,148,157,1000,-453,709,-1000,-1000,-44,324,778,104,-920,-888,-991,-721,618,1000,-53,1000,99,-215,-278,-996,103,-382,810,-584,46,-284,1000,400,771,741,1000,1000,938,563,-1000,-1000,964,212,-757,1000,-1000,250,1000,-35,609,400,-668,559,1000,521,-1000,-240,1000,-1000,-1000,-52,1000,-765,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{-652,47,-386,-230,-767,601,1000,161,-1000,1000,780,874,-1000,18,1000,1000,-283,-748,1000,-127,752,-24,-584,-54,1000,-1000,-147,1000,-650,-1000,54,1000,-44,-1000,-1000,370,-823,-632,400,859,153,796,818,-20,629,-1000,-885,531,-874,-570,-612,-1000,1000,-1000,1000,-1000,-353,287,1000,-1000,23,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{76,1000,1000,-173,-1000,-791,634,-774,-1000,-50,-722,-400,-1000,-29,1000,230,-1000,1000,412,331,-1000,635,-466,-403,-1000,11,-831,-1000,-1000,43,1000,440,-491,-675,-107,-1000,758,-119,912,-492,1000,1000,-662,400,-144,-1000,-917,1000,463,-1000,104,-1000,1000,289,521,-1000,662,858,816,557,-465,713,214,427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{155,247,332,173,-1000,1000,355,-1000,-802,687,-523,-175,-781,-359,72,-771,275,-368,1000,537,867,-469,-768,-692,759,373,1000,240,-443,-1000,-98,1000,1000,-254,1000,-462,288,-390,-934,998,1000,354,703,-664,-283,-21,280,-626,-994,587,478,64,1000,-6,132,520,1000,-557,59,-1000,-215,282,-157,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{16,903,-312,937,-143,-901,556,-81,-930,436,1000,-121,-877,-1000,-400,23,947,374,691,-357,637,-346,492,434,584,-589,-231,-574,-114,-594,195,514,1000,-643,-591,-888,400,599,-1000,-1000,126,-191,-192,369,-617,-838,401,275,-1000,-499,-1000,380,1000,516,521,-1000,96,-654,-1000,-560,409,-419,214,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{841,1000,-157,816,-640,-211,601,-748,-937,-219,-814,-1000,-1000,-582,-1000,599,1000,1000,612,1000,1000,1000,-88,1000,935,1000,-422,-232,1000,1000,165,193,1000,1000,1000,253,1000,408,-1000,141,100,-155,353,-580,-1000,250,29,-905,-358,-593,768,767,-2,1000,611,872,1000,-128,-939,890,1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeUnparseableExtraFieldData():void",
            new int[]{429,599,-1000,-40,-103,113,355,385,-803,-668,842,333,916,-186,957,210,-265,1000,-1000,-768,-394,-1000,249,1000,-687,-229,-951,-330,1000,1000,-244,-620,-957,1000,-696,1,-902,1000,-504,-802,151,407,-1000,-400,1000,-713,608,-418,1000,1000,-900,743,-1000,219,-241,371,-540,190,-1000,817,11,-15,1000,-260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-1000,1000,-1000,-229,-966,-452,877,-291,1000,643,466,-139,-981,-678,-420,197,1000,-1000,-1000,-239,-400,1000,1000,1000,37,617,-365,997,-435,438,-703,1000,-531,-1000,-1000,-1000,1000,1000,1000,-1000,1000,-1000,1000,1000,-326,-1000,974,604,-568,918,431,-1000,-1000,1000,-971,-1000,-1000,1000,1000,-240,-504,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-876,-413,1000,-738,-1000,-736,563,760,-638,-256,-659,785,121,-374,1000,637,-497,1000,1000,-980,-865,-278,1000,291,-662,1000,1000,-1000,-464,1000,892,-1000,2,-879,373,432,-1000,1000,-1000,-1000,139,1000,619,-332,820,-1000,387,-331,-1000,-30,-1000,-1000,1000,-207,365,1000,131,764,487,-415,249,283,763,839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-777,-157,-808,990,497,-260,-676,783,-771,-206,330,119,19,-801,504,-83,729,-769,463,-834,-588,278,190,952,441,-588,254,-714,674,-569,-769,919,316,-661,-701,-246,-294,637,-285,-728,695,-566,-503,457,-481,-568,-16,-892,-919,-902,-850,-846,437,-16,577,-408,-69,88,791,24,-87,367,-689,-600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-949,1000,-1000,-391,386,-229,-7,-278,1000,655,1000,-812,-1000,-1000,-594,406,1000,-1000,-1000,539,-351,1000,1000,750,-366,708,-1000,1000,941,-772,-1000,1000,-133,170,-1000,-1000,1000,-482,1000,-392,264,-1000,921,1000,1000,-240,-688,414,-393,498,1000,877,-1000,848,-981,-434,-1000,698,-855,321,-1000,1000,317,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-1000,1000,-924,-319,-983,948,938,-278,1000,791,466,-143,-990,-836,-420,351,1000,-1000,-1000,-239,286,1000,1000,1000,1000,617,-218,999,941,438,-849,1000,-336,-1000,-1000,-1000,1000,1000,1000,-1000,1000,-1000,961,1000,-326,-1000,926,798,-568,826,431,-1000,-1000,1000,-985,-1000,-1000,1000,1000,-149,-326,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-1000,567,766,-828,-968,1000,885,-291,-1000,553,-740,-139,-917,-698,1000,1000,-497,599,1000,-1000,-314,1000,1000,-404,648,1000,719,-999,-170,1000,203,-1000,601,-1000,-1000,736,-1000,1000,1000,-1000,73,1000,565,-332,1000,-1000,174,604,-1000,-762,-1000,-1000,-1000,-136,-278,1000,-25,1000,534,29,260,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-1000,1000,-1000,-410,-1000,499,887,-270,1000,1000,649,-7,-981,-1000,-334,-7,1000,-1000,-1000,-238,-1000,1000,1000,1000,424,472,-583,1000,-1000,-141,-805,1000,439,330,-1000,-1000,1000,714,1000,-1000,1000,806,1000,-1000,129,-1000,731,1000,-618,-514,-1000,-402,-1000,1000,-1000,-979,-1000,1000,1000,-1000,-881,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{1000,1000,-1000,210,1000,1000,134,-851,545,3,1000,-412,-1000,257,-1000,122,1000,-770,-577,1000,-474,703,550,-726,211,371,-1000,1000,-1000,-1000,-1000,729,488,932,-743,-1000,691,-947,-31,353,-553,100,-1000,517,-1000,1000,252,1000,1000,1000,1000,1000,-1000,-522,-1000,-1000,1000,1000,-267,1000,-228,-875,-309,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-91,1000,-116,-634,788,1000,234,-863,146,691,253,-1000,-1000,174,-991,723,-124,-219,-1000,1000,-934,1000,209,-1000,1000,1000,-1000,1000,196,-772,-1000,400,1000,1000,-1000,26,400,-1000,1000,-1000,-1000,-400,-702,171,1000,-203,-1000,584,76,331,-846,1000,1000,-921,-1000,360,-291,838,-1000,1000,-682,-895,167,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-30,1000,-191,-133,695,573,-20,-308,1000,-310,738,-1000,-1000,-182,-716,697,-675,-1000,-586,537,760,1000,923,-495,-919,915,-689,895,-818,56,-855,1000,-121,906,-1000,-1000,1000,-73,497,-264,-600,-1000,65,-443,350,150,-341,-153,-322,1000,123,23,320,-380,-888,-464,-394,435,-353,1000,-462,-854,-294,-623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-1000,1000,958,-603,1000,715,-769,766,-272,-468,813,-1000,-712,351,-414,1000,493,599,1000,-1000,136,286,174,-648,653,1000,-305,-518,-1000,424,-437,256,-118,-1000,-319,251,69,1000,-376,123,503,579,-1000,892,338,-1000,745,242,18,-759,-1000,-945,742,-307,-987,180,125,522,-611,186,470,1000,495,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:NzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{-122,872,-1000,-842,-528,39,564,75,580,-24,-116,652,-1000,-420,404,710,777,1000,101,-1000,1000,923,-1000,756,-254,145,295,229,1000,-610,179,127,-941,1000,-379,574,-1000,-262,-1000,329,1000,171,1000,-201,478,-1000,-742,660,998,-670,-979,783,-444,-1000,529,1000,562,1000,-171,490,1000,945,188,610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{620,-513,362,1000,-265,1000,-613,25,-18,-188,523,-152,1000,831,903,-91,346,5,1000,1000,392,349,-762,569,-1000,383,990,-136,386,-578,-1000,-1000,1000,964,741,511,278,73,-344,-1000,-161,-689,58,466,912,445,-902,-1000,-426,1000,-78,194,7,377,-1000,-918,-1000,283,754,-1000,380,529,1000,-931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{447,1000,892,-1000,1000,-400,154,-144,69,584,802,482,-1000,-969,1000,1000,274,490,366,-1000,-1000,795,-186,-1000,1000,-457,471,-570,-739,214,1000,1000,-1000,536,-992,-803,-1000,-465,-75,1000,748,-1000,16,644,1000,1000,389,1000,-403,-1000,-1000,-434,-1000,868,-296,-9,985,1000,-1000,265,-251,-678,372,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{-339,211,558,784,-232,-636,-719,-284,-377,-278,628,-323,-467,215,968,-360,919,-605,539,-617,224,538,-754,338,311,-603,477,-447,-840,368,613,-877,-272,488,-157,-723,-847,-840,-882,934,138,800,655,999,318,-312,-726,907,677,-752,-993,43,-304,-321,-63,476,-221,425,28,231,735,-691,-190,746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{354,352,821,-374,-1000,-203,-306,1000,459,-824,372,1000,1000,1000,-1000,594,882,1000,-514,408,1000,342,-206,1000,-1000,743,-167,229,753,-15,-1000,-1000,1000,930,441,668,868,175,-449,-1000,503,-950,234,860,478,-879,-1000,660,-709,664,-979,-579,847,-410,-482,-1000,562,-1000,-171,1000,361,-265,657,-967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{215,311,200,367,45,-633,-107,964,-155,309,-426,240,-732,210,94,-17,535,29,741,-1000,285,795,-896,909,-319,-403,-1000,464,91,681,-387,-1000,400,1000,-373,-163,-283,-1000,-355,-392,920,49,380,720,512,183,-527,747,311,-537,-449,-324,199,-5,-250,-391,-557,252,809,409,252,400,763,-693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTIx", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{999,299,-1000,-954,-1000,-110,1000,-1000,1000,-1000,-83,-252,860,1000,-302,-35,-115,28,378,680,-124,-218,-854,1000,-776,278,1000,487,498,-1000,-1000,-549,888,853,-40,688,-235,78,-707,-585,-770,914,279,648,-129,-85,-1000,-1000,-870,975,109,197,-12,436,-405,-531,-962,901,-83,539,682,28,538,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{917,1000,-123,726,773,-400,348,-35,-89,14,970,-326,38,315,831,163,751,-923,1000,-1000,-242,595,824,-484,134,123,579,-477,345,173,129,195,538,1000,886,-642,-913,-250,934,354,-225,-510,202,359,671,1000,-726,-286,-571,-13,-619,-903,-311,764,-874,-533,-167,121,-1000,101,736,463,1000,-648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{-258,1000,-123,-1000,-1000,-110,712,-651,1000,-532,-86,516,-102,636,-202,1000,39,609,369,-572,-663,443,-1000,1000,141,-102,724,236,-56,-825,-1000,132,-483,948,-844,400,-333,-644,-753,815,503,-486,38,1000,871,-149,-1000,-147,45,-392,-293,218,-555,377,-138,-530,-626,244,-82,559,-10,-455,538,626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{1000,1000,-123,824,1000,273,-941,-157,-67,194,-86,501,-681,-974,673,1000,390,-52,-155,-572,-1000,873,1000,-1000,1000,-358,760,-483,-949,233,627,1000,-1000,-864,-295,-505,-53,-193,1000,1000,153,-924,-133,633,871,1000,668,488,-932,-1000,-293,-1000,-1000,1000,-600,-399,-109,-280,-881,-615,-779,-908,738,-398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-791,-385,824,-902,471,-160,6,-1000,775,1000,-866,-516,1000,20,188,-99,-950,-274,634,-1000,323,-1000,1000,63,1000,586,1000,166,-627,541,-619,-762,202,1000,-608,-327,-248,1000,1000,-510,223,-2,585,875,-439,6,-1000,-682,-1000,689,116,-389,788,174,-1000,-1000,-611,1000,-101,-697,-1000,-956,-197,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-1000,-761,82,-1000,742,448,-329,-1000,-571,27,-1000,-1000,-1000,573,1000,745,-127,-1000,508,-1000,-491,-1000,870,388,1000,1000,723,-212,76,749,329,-714,69,1000,-584,-444,895,1000,961,642,-587,224,-1000,1000,-1000,30,1000,-669,-784,605,811,-324,1000,1000,-1000,-1000,1000,1000,254,1000,-1000,1000,566,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-1000,51,138,-462,161,-78,-217,-239,-521,-343,989,64,257,1000,-416,-20,20,-28,-131,876,-830,354,-613,20,-20,1000,-727,-46,-556,-870,526,-489,-430,-20,20,38,-631,-20,-35,-205,-209,1000,672,-20,20,154,-254,-591,205,-20,-20,-938,-358,-20,20,729,-713,-339,519,-955,657,1000,-525,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-1000,-992,343,227,1000,-112,-118,-615,-329,131,371,-560,-303,742,-157,1000,-349,-757,33,-223,-810,-1000,1000,104,1000,1000,-242,-655,-563,101,245,-1000,-577,0,-249,-231,-310,739,1000,723,-171,811,-400,540,-1000,220,1000,-1000,-1000,1000,-17,1000,382,174,-1000,-1000,24,1000,-241,260,-743,418,372,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{686,300,-498,-1000,369,853,-846,310,-225,-191,241,-21,-1000,554,985,776,-1000,-1000,473,-1000,136,-1000,724,543,-777,267,-80,-232,365,-327,1000,-1000,610,1000,-1000,238,67,1000,1000,1000,247,675,-1000,132,-348,-764,-469,74,382,49,-737,-602,1000,-838,-1000,-609,593,1000,-225,1000,71,378,-636,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-207,-1000,-142,-1000,902,719,-1000,-771,331,-53,517,-504,-429,1000,-226,875,-655,-606,49,136,795,382,-94,-976,11,1000,147,-797,-872,-789,153,-537,567,441,-961,-218,526,251,-166,-991,338,1000,67,595,-370,295,476,160,-524,603,1000,-672,410,1000,-399,-138,-659,70,134,-1000,718,1000,-79,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-805,-168,-1000,-1000,-760,588,-162,-1000,-918,-804,472,-716,107,1000,-238,951,1000,1000,-84,1000,-857,767,-546,11,-1000,920,-384,-596,-594,-1000,76,588,860,123,723,-66,-785,-1000,-408,-1000,-843,-1000,-59,684,-364,458,-1000,721,808,-1000,211,-602,-516,-1000,465,1000,-1000,-186,1000,-1000,455,1000,-1000,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{1000,1000,-142,484,-630,719,-398,710,-1000,-1000,711,-55,103,1000,329,-788,1000,1000,464,1000,-653,1000,-1000,1000,-1000,988,-913,201,1000,-1000,413,490,-1000,-1000,1000,238,-684,-1000,-1000,234,-1000,-880,217,-1000,1000,459,-1000,1000,1000,-1000,-1000,-1000,410,-1000,1000,1000,647,-1000,1000,-471,718,969,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{721,-393,-46,-68,-611,890,-639,310,167,-201,888,498,-505,401,167,593,-1000,-805,1000,-1000,814,-1000,-67,-22,219,-835,-318,-557,1000,-1000,974,115,95,112,-460,1000,-697,1000,899,1000,401,168,-338,-683,666,-169,261,630,443,494,-627,623,982,-1000,37,-458,1000,1000,-223,1000,338,-1000,61,-435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{721,-365,-226,-338,-540,519,-725,310,-418,-201,888,199,-831,554,318,289,-902,-470,818,-963,814,-667,358,435,34,-872,-487,-104,989,-714,632,-40,199,112,-550,925,11,773,648,876,187,-151,-892,-683,666,-434,586,630,287,-323,-644,-83,757,222,37,442,429,942,219,851,539,-998,-199,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{1000,1000,-1000,733,958,-22,-621,76,-542,1000,238,689,1000,1000,-179,467,712,945,63,1000,909,-1000,182,-696,951,-133,-11,645,132,1000,-375,1000,-623,1000,-788,-450,-1000,947,388,-972,-140,-340,-516,-423,-932,469,742,222,804,1000,-162,62,-751,957,-591,500,-985,588,82,964,1000,772,1000,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-90,347,-16,-765,705,-513,537,506,-1000,-163,-221,266,282,-17,-454,103,1000,1000,165,123,-237,-1000,-400,315,1000,11,-230,1000,66,221,-827,64,548,-892,-357,-1000,-100,-1000,-314,-1000,913,-1000,504,570,-516,1000,-63,571,761,1000,983,571,-439,619,333,854,-1000,-1000,238,464,1000,1000,400,395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-971,-260,-91,-378,547,132,444,889,-584,-963,-28,-145,-848,526,58,-463,-81,142,490,235,971,410,-210,-98,-136,-360,-950,840,996,-802,584,-335,-472,-280,777,-487,-561,-128,633,195,549,233,-11,-335,704,-389,146,541,0,-307,761,667,-83,296,219,749,-458,-285,446,932,-321,622,-869,-133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{150,-637,-997,-146,1000,-374,444,1000,-1000,-923,-165,1000,741,526,58,753,60,835,1000,1000,837,365,1000,-350,766,-1000,-696,-573,55,-802,328,231,-1000,-157,777,-846,465,1000,394,-344,728,897,624,255,1000,-128,650,338,76,1000,1000,622,-1000,-249,219,1000,-532,-534,-272,693,17,-534,412,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-1000,395,-536,5,452,-745,222,76,-713,-1000,-141,689,-181,109,-203,-107,632,945,1000,1000,2,1000,182,-696,-506,-755,-1000,1000,1000,-1000,1000,238,-472,-106,1000,-189,-97,-69,1000,389,663,677,-516,377,1000,-812,58,959,-177,-466,865,916,-707,1000,-212,453,-706,-161,927,897,-156,568,-311,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{51,-68,-1000,1000,-464,1000,857,-27,-759,100,-50,1000,885,660,-215,1000,286,200,670,911,514,262,1000,318,-44,-294,574,-957,-1000,-29,-711,269,-386,-1000,-1000,1000,-383,1000,-649,900,-374,754,414,534,-82,-1000,564,-79,-1000,-773,-233,-961,-1000,-192,-1000,-397,-432,-332,-1000,-712,557,-40,657,-123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-348,-600,78,886,-518,836,660,-386,-262,-725,1000,163,1000,-284,887,546,-77,294,1000,986,947,80,444,1000,833,-98,288,132,286,2,-53,-479,-414,-892,612,521,266,1000,639,169,-506,179,311,292,588,-1000,-116,-339,-276,789,-202,-139,-1000,642,-86,27,304,295,-329,1000,-122,-153,63,40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-870,-1000,-91,-305,418,692,-411,-294,-91,-1000,136,-502,-1000,457,597,-829,-1000,-1000,12,103,1000,1000,-210,-437,-1000,469,151,-93,538,-161,317,-825,-703,-1000,1000,-220,-336,954,-126,543,97,362,-640,-141,726,-667,146,714,-282,-544,165,421,-83,-456,1000,1000,589,21,1000,1000,-1000,-149,-1000,-714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{295,443,-101,605,-526,-1000,-904,1000,-256,240,512,1000,1000,-1000,485,-58,-567,-125,313,880,-654,809,854,-964,-1000,-1000,-1000,735,869,-1000,-648,-606,-435,-348,-1000,-227,1000,987,1000,-289,-430,-176,-1000,-941,-220,-11,918,-566,-36,65,-739,-258,-1000,-840,844,-885,1000,1000,-360,967,-423,-871,1000,-445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{411,-532,613,836,900,-918,199,706,86,653,-345,-344,-237,493,31,1000,-62,-368,224,1000,-368,1000,1000,284,1000,240,-1000,-145,-190,-34,-136,-7,764,-623,-1000,1000,826,-510,503,-433,-649,738,263,1000,1000,-1000,-322,-18,172,425,1000,-231,1000,-1000,-615,1000,-364,-316,-433,-230,1000,657,1000,-15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{432,-1000,-1000,-408,862,1000,754,100,-549,482,1000,1000,-244,-872,-319,-399,-1000,-1000,626,-1000,601,548,256,-1000,-666,-939,-1000,-164,348,471,-603,682,-459,866,53,325,-528,-33,822,635,1000,-627,-298,106,1000,424,1000,451,-36,227,840,-352,458,1000,-487,1000,1000,-1000,-1000,-459,478,-220,869,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{201,-621,670,-1000,-1000,-275,754,530,-772,-312,-155,-382,301,-768,-1000,566,194,163,-582,-530,685,-607,1000,717,-504,-1000,134,373,-347,1000,-1000,-1000,613,515,-1000,-673,-495,-1000,-693,-818,-38,-1000,-58,-1000,-413,274,-1000,1000,505,-507,-586,385,-634,615,670,-952,1000,764,-1000,-1000,1000,1000,96,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{192,1000,233,834,1000,93,-236,1000,-1000,-766,338,622,1000,-1000,-1000,741,581,-1000,165,-1000,130,669,779,-854,-201,-996,-1000,-94,718,1000,-158,319,-1000,-306,-1000,318,-1000,-1000,682,-193,693,9,-941,-81,305,-846,-227,1000,-199,199,-612,-358,85,909,-508,715,1000,-190,-1000,-1000,1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{1000,-20,-789,-1000,-90,1000,-691,884,-371,234,1000,449,385,-366,-226,-950,-1000,-255,31,-1000,250,-966,-865,-485,-546,-1000,-1000,375,-695,1000,-839,-1000,1000,1000,-606,-147,-1000,8,-2,421,742,-796,-247,-376,-1000,674,479,609,183,-255,-1000,833,-544,1000,432,1000,626,-329,-1000,-320,14,568,-406,957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{-150,-1000,670,836,1000,-1000,754,100,1000,482,216,-742,-262,739,451,207,458,147,877,1000,-914,494,256,221,1000,1000,-855,-1000,-628,-965,228,958,937,-650,-1000,1000,979,-254,312,328,-1000,1000,-45,1000,1000,-188,-593,-1000,49,227,840,-352,1000,-1000,-363,523,1000,764,-174,-108,-78,1000,49,252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{1000,43,-829,286,1000,550,-1000,1000,230,909,867,475,9,517,496,-646,-1000,1000,596,71,-487,159,1000,-788,507,-132,-1000,12,-585,276,-235,-305,-608,203,18,1000,-76,352,835,690,314,420,-22,1000,-1000,-218,953,-103,-51,397,110,402,-1000,828,-467,1000,-329,-1000,-603,-1000,14,328,227,644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{1000,-1000,-243,514,1000,-1000,363,22,656,1000,671,-329,-131,802,77,-99,481,166,722,658,-914,-187,256,637,-379,1000,-1000,-1000,244,-954,-148,855,-359,-274,-1000,354,848,-672,164,264,-474,388,92,542,326,400,-402,-1000,444,-100,-128,-317,1000,-739,502,-400,370,-839,-803,-790,689,568,-314,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setGeneralPurposeBit(org.apache.commons.compress.archivers.zip.GeneralPurposeBit):void",
            new int[]{-102,-1000,77,-1000,-359,-314,-254,706,653,65,476,-747,1000,312,-58,373,244,899,-113,1000,366,-752,1000,-430,-32,360,90,-228,81,-169,-463,1000,-385,362,-331,-304,-470,-669,465,-444,164,747,-884,508,383,-180,-1000,-523,505,-917,242,884,-318,29,-232,-1000,-306,-494,-649,-526,-221,1000,-762,-548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-1000,-1000,-455,1000,548,-1000,629,547,-366,-1000,298,-1000,898,403,119,786,1000,1000,269,508,1000,221,1000,-242,-56,-78,-339,930,811,-773,1000,-1000,581,-657,1000,-27,-127,1000,211,-1000,-975,171,793,-382,678,-147,1000,-1000,1000,-1000,-1000,-486,-96,1000,1000,788,-1000,-630,249,-802,-128,658,207,794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-1000,611,315,-100,199,-440,693,847,190,932,631,-412,903,705,504,-319,-942,-514,889,1000,840,-521,-537,-276,55,477,-1000,568,-283,128,-445,-20,-1000,-832,366,-988,-302,45,-797,65,-1000,459,494,-718,1000,-26,1000,-639,-206,1000,323,153,-731,1000,926,489,-1000,-536,479,74,-415,-692,1000,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-1000,-625,-1000,1000,1000,-1000,629,-1000,1000,-1000,1000,-527,1000,49,-541,-757,-122,-278,196,825,1000,442,116,-411,1000,503,-681,1000,-1000,-1000,-246,-1000,644,-1000,1000,-998,-1000,1000,1000,-1000,-1000,1000,929,133,-855,-692,1000,13,1000,589,-1000,1000,1000,1000,1000,1000,1000,-1000,1000,-1000,-125,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-689,128,-402,-1000,-945,-26,-673,611,531,853,142,-799,926,-387,-192,724,-751,493,647,1000,-1000,20,-402,-1000,-873,853,-195,-367,838,479,-538,-126,-185,-38,98,520,484,-856,-665,1000,537,-667,-184,-482,692,970,-89,-9,-885,263,916,-184,-360,824,861,-1000,-414,636,532,600,-601,1000,-183,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTk2Ng==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-556,-1000,-921,377,439,-676,436,-334,-122,-1000,631,-527,1000,61,191,117,571,294,-310,392,1000,428,997,-242,386,708,-512,608,-283,-1000,553,-966,155,-1000,482,23,-478,840,671,-1000,-371,790,-412,77,-338,250,589,-672,1000,-429,-405,689,590,657,1000,459,-163,-978,36,-963,116,-215,604,-171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{415,-732,-349,-829,-411,318,148,62,-310,725,-1000,463,844,1000,1000,-23,953,-70,103,131,-131,566,843,1000,104,1000,-782,450,33,-361,491,-880,-62,-730,-608,55,545,190,253,672,269,844,-634,130,-201,1000,-50,-1000,429,346,-36,1000,846,739,611,-47,-167,-476,-672,-330,-200,-829,206,-356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:ODU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{479,-1000,-692,-419,-214,-95,-47,1000,-981,1000,-962,252,844,292,1000,-34,626,70,-114,-437,-400,1000,1000,852,-650,1000,535,-758,499,-579,814,-1000,-75,-587,-169,1000,539,368,1000,672,810,-323,-293,699,-643,870,-1000,-460,429,-502,427,-346,11,-175,587,-47,-1000,-48,-1000,-414,-315,-97,-1000,285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{81,-1000,-103,-523,335,-159,724,650,-1000,332,1000,-334,-581,928,620,260,876,1000,-495,61,617,793,1000,813,655,917,-678,1000,667,93,1000,-831,-1000,-551,-1000,-1000,-1000,230,-229,125,-705,870,-184,-973,1000,516,1000,-1000,372,-60,621,1000,-460,419,746,-400,-1000,-389,1000,-1000,-94,-905,1000,-493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-1000,1000,1000,-93,-1000,787,78,394,1000,-611,440,-260,880,-151,1000,-418,-1000,-467,-1000,1000,-655,-943,1000,-348,-30,854,-1000,294,-1000,1000,-1000,1000,1000,113,758,-1000,661,-823,-1000,-1000,-1000,120,70,-1000,-69,213,1000,-1000,1000,-1000,-124,1000,123,943,938,-796,-414,-574,952,557,141,1000,763,-693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{472,-1000,821,-443,-1000,-855,-606,-81,-181,-352,-865,-516,1000,213,250,13,-321,-929,488,-686,-20,-827,-398,-749,-512,578,1000,-974,134,-167,-333,1000,-1000,-834,457,195,656,-515,-432,-1000,669,-649,-1000,738,112,798,-694,948,1000,-1000,-303,-59,-1000,-772,515,459,-91,-643,-213,1000,403,513,-302,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{760,-1000,-705,-616,-624,-883,-66,1000,-786,321,-743,-1000,891,39,239,817,788,1000,-85,-1000,20,104,1000,-591,-1000,731,946,-1000,386,-705,959,-491,-127,-439,28,1000,1000,253,1000,171,1000,-1000,1000,925,-695,1000,-1000,466,671,-917,-486,-397,-724,-608,409,-107,-535,-334,-653,37,-174,820,-1000,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-1000,1000,1000,117,-888,964,148,1000,1000,-532,306,463,939,1000,605,-217,-1000,-463,-1000,1000,-250,-1000,-21,-1000,-321,1000,-1000,74,-1000,1000,-1000,1000,1000,388,1000,-1000,763,-1000,-1000,-400,-851,844,1000,-997,532,-251,1000,-385,-9,-1000,171,1000,-30,1000,986,-1000,-590,-151,1000,700,-13,1000,874,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{4,-484,364,-792,-809,-1000,774,346,-649,-999,1000,1000,1000,-1000,1000,1000,-1000,1000,-830,272,-878,1000,978,1000,322,-1000,891,46,-558,-1000,-670,1000,507,-298,-766,-581,483,-1000,162,-588,268,1000,644,1000,1000,-588,154,1000,-222,582,-1000,-513,84,-1000,-598,1000,-1000,-579,-1000,-177,-1000,-1000,-1000,398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{594,-125,603,1000,-488,1000,-1000,90,-368,209,-1000,1000,-1000,-1000,-8,-411,1000,-277,1000,-787,-656,1000,342,1000,-236,-4,348,-53,1000,-444,-865,1000,-44,-82,-1000,-1000,-1000,1000,-1000,-588,101,358,-864,-1000,-1000,1000,298,-51,1000,232,471,1000,-1000,1000,-962,-951,-3,-579,-412,869,740,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{-582,-428,1000,722,-1000,212,64,378,-509,-182,-46,-783,-338,-1000,45,803,485,-82,-95,-180,-1000,119,1000,79,-212,-1000,809,-229,-250,400,-299,660,-74,-179,-401,-215,-1000,330,-780,-400,99,562,-1000,-463,185,799,942,-81,-542,1000,-161,776,106,791,-20,-1000,-818,5,-1000,274,729,506,720,-153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{420,-573,-157,1000,445,1000,51,-536,45,-137,-1000,9,-1000,816,543,-761,1000,-1000,1000,-213,-467,1000,-520,995,-1000,-611,544,-292,1000,853,-1000,6,-962,-306,-1000,285,-151,42,-400,753,-427,288,-582,-1000,-896,1000,-218,-2,1000,-628,336,757,-1000,604,-255,-394,-1000,197,-625,1000,695,247,481,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{615,-457,-144,631,-1000,290,357,368,-954,-33,-251,-708,-1000,-650,-932,-260,190,710,-400,-695,-222,212,213,1000,-47,-451,728,239,320,-409,-351,405,154,-486,-968,-425,-811,330,-692,-400,-261,569,-395,-473,-18,732,-387,-680,268,301,-562,858,-656,309,-1000,-331,-563,-1000,-858,8,759,1000,891,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{-570,-1000,-17,-250,-1000,-601,499,1000,750,-1000,46,1000,-148,-389,66,-400,-1000,595,-71,-1000,-47,1000,20,876,524,-27,1000,713,406,-1000,559,274,1000,-709,579,-1000,1000,-126,137,662,-14,1000,-493,391,570,177,344,268,122,163,-1000,407,30,-661,-1000,692,-1000,-1000,-1000,-512,-1000,-18,87,873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00334() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{844,323,-314,-383,1000,1000,-812,-983,-520,-884,-1000,1000,668,418,-1000,-553,156,891,1000,89,422,82,969,492,-1000,953,-1000,232,922,-1000,-1000,-764,-515,951,-839,-528,238,-1000,-1000,-1000,-1000,-40,1000,768,-270,-197,-335,-416,748,-442,1000,-678,-1000,-272,-881,973,-105,-930,1000,605,246,-94,-575,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00335() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{1000,-293,-174,305,-268,-768,-159,-322,-466,363,272,-843,-813,-2,1000,-352,556,-929,-379,-166,-703,893,-934,1000,171,-453,530,-185,-119,1000,-1000,525,-1000,-796,213,513,-347,1000,1000,167,1000,741,-44,-1000,-402,899,-234,842,916,-505,-441,669,222,308,-456,320,-653,-630,-689,-712,-474,-697,68,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00336() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{1000,264,590,-202,1000,528,-1000,-430,-330,-990,-675,-812,673,-763,114,287,-472,1000,952,-128,-399,955,317,1000,-324,34,-1000,-447,545,-1000,-1000,471,426,495,-1000,335,129,-1000,-1000,-185,-1000,-50,1000,1000,-1000,-704,792,617,1000,-1000,608,-706,-1000,-858,-777,390,264,-1000,967,744,-471,-491,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00337() {
        org.junit.Assert.assertEquals("VOID|getRawFlag=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setRawFlag(int):void",
            new int[]{-122,-420,-586,758,384,-123,-552,796,433,-1000,-1000,898,701,291,282,-644,-1000,531,-608,552,1000,-1000,1000,456,106,669,-1000,-1000,-609,-553,910,423,928,-424,-716,471,1000,-464,582,-209,-510,-328,-357,-278,-602,358,-1000,-1000,1000,567,-168,961,-19,991,-98,746,-645,-1000,-493,601,903,501,1000,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00338() {
        org.junit.Assert.assertEquals("VOID|getRawFlag=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setRawFlag(int):void",
            new int[]{693,-509,-1000,53,78,306,-32,739,346,-1000,48,789,-560,482,-447,-729,-1000,1000,-253,173,899,-1000,311,1000,105,-1000,1000,-1000,66,366,-27,443,705,1000,-471,1000,-566,-419,-663,-1000,-796,1000,885,346,64,267,680,-139,1000,903,455,-939,-118,992,-440,-432,-911,-921,-456,519,459,498,-465,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00339() {
        org.junit.Assert.assertEquals("VOID|getRawFlag=java.lang.Integer:LTgwNQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setRawFlag(int):void",
            new int[]{21,-805,-565,-33,776,-289,223,1000,-147,193,-1000,-1000,920,-633,407,387,115,155,779,-979,-1000,194,1000,-441,992,-674,-1000,429,-898,-96,1,-293,-1000,696,117,313,1000,-156,1000,699,-1000,-1000,-260,280,-127,-835,-668,885,1000,-727,37,959,-286,836,1000,-385,1000,-978,-77,-1000,964,856,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00340() {
        org.junit.Assert.assertEquals("VOID|getRawFlag=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setRawFlag(int):void",
            new int[]{1000,-1000,110,171,413,-80,32,-751,25,205,-420,-1000,-451,1000,-367,501,-350,-668,217,-568,-1000,400,-1000,-650,1000,435,-516,252,-238,1000,380,-596,-325,716,-980,-157,-64,566,-693,-600,-61,-1000,1000,-392,-123,676,400,1000,-1000,-1000,-695,-373,-905,-1000,-689,-447,335,-865,-1000,-1000,-1000,80,-1000,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00341() {
        org.junit.Assert.assertEquals("VOID|getRawFlag=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setRawFlag(int):void",
            new int[]{1000,-728,-1000,1000,1000,-782,923,-46,-286,-1000,963,1000,-1000,1000,-822,-736,-984,-798,-1000,1000,1000,-1000,-163,973,1000,1000,-646,-1000,-1000,312,440,246,1000,1000,-318,713,-364,745,149,-473,857,1000,1000,-703,1000,55,-199,836,255,1000,-1000,157,-839,1000,-1000,-610,-1000,-681,-111,455,-391,1000,1000,-538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00342() {
        org.junit.Assert.assertEquals("VOID|getRawFlag=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setRawFlag(int):void",
            new int[]{-334,-368,-634,1000,531,-664,333,-601,-959,-366,301,-1000,503,291,-939,-1000,-1000,-748,-465,-1000,317,-338,-312,-422,350,896,-842,-244,-1000,323,498,75,928,918,-1000,-262,44,745,-642,-1000,488,-1000,24,-533,9,905,-1000,-463,-1000,-624,-15,1000,-895,-679,287,-424,-209,-740,-811,-103,-671,692,75,-756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00343() {
        org.junit.Assert.assertEquals("VOID|getRawFlag=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setRawFlag(int):void",
            new int[]{1000,-1000,680,1000,1000,-389,414,141,-582,1000,289,364,-454,964,-755,-471,366,-659,491,-161,-1000,-492,-187,-912,1000,-125,829,-1000,-789,586,928,-328,330,-759,-262,-562,649,10,296,361,551,706,1000,-430,169,-561,-396,-591,-368,1000,-538,343,398,-921,173,9,-47,-697,-466,666,-788,224,-241,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00344() {
        org.junit.Assert.assertEquals("VOID|getRawFlag=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setRawFlag(int):void",
            new int[]{757,-961,176,873,309,558,-556,-785,620,-33,147,-501,690,975,-137,466,-342,-160,399,-294,-1000,-644,-873,-630,1000,212,554,-1000,-44,1000,487,-985,531,-231,-422,-58,158,-376,-79,-575,-296,-1000,-293,38,418,638,-577,-114,-839,-1000,-677,540,172,-1000,154,-611,68,-792,-842,166,-1000,822,-957,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00345() {
        org.junit.Assert.assertEquals("VOID|getRawFlag=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setRawFlag(int):void",
            new int[]{294,-1000,-1000,1000,789,-713,-183,210,703,-1000,-524,-530,615,1000,-181,3,174,-189,-1000,937,962,-1000,-356,1000,893,1000,-1000,-1000,-374,92,1000,-1000,1000,954,-1000,153,646,-243,1000,-948,-937,194,54,-899,1000,-444,-1000,937,-1000,1000,-1000,1000,-577,1000,-1000,-68,-857,-956,-980,-1000,-29,1000,329,-821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00346() {
        org.junit.Assert.assertEquals("VOID|getRawFlag=java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setRawFlag(int):void",
            new int[]{224,-628,-222,1000,269,-561,844,-596,-987,311,540,184,160,704,-84,-1000,-609,-993,-122,-1000,369,-413,121,452,1000,449,1000,-623,-1000,148,-174,349,11,120,-246,105,-693,591,-642,-526,847,-1000,-216,-642,-340,-304,-523,-42,380,-296,504,637,-229,-51,15,165,-185,-580,-437,970,-548,-165,120,713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00347() {
        org.junit.Assert.assertEquals("VOID|getRawFlag=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setRawFlag(int):void",
            new int[]{870,-1000,-1000,1000,1000,-954,369,-13,-39,-958,-538,526,-987,1000,-974,-151,206,-1000,-1000,1000,1000,-1000,-368,252,1000,1000,-856,-1000,-985,7,1000,-350,1000,1000,-1000,-260,655,170,1000,-247,686,911,1000,-1000,1000,-44,-1000,1000,-599,1000,-1000,1000,-1000,784,-1000,-12,-1000,-994,-691,-1000,-368,479,918,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00348() {
        org.junit.Assert.assertEquals("VOID|getSize=java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{176,-1000,1000,870,429,683,-247,-86,-119,570,-377,-956,70,843,1000,-21,-193,-52,-909,1000,-1000,1000,-1000,1000,-449,577,152,-241,-830,1000,-1000,474,-475,354,-1000,1000,-100,-374,-1000,-1000,685,-1000,615,391,-1000,463,401,474,-1000,482,-955,1000,312,-573,-178,-1000,1000,-984,-1000,287,285,721,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00349() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{979,-89,919,-894,1000,361,1000,1000,-460,618,-197,-475,-281,-1000,888,483,-891,-430,-369,409,289,930,1000,891,1000,1000,1000,120,316,-323,-242,-1000,1000,-1000,1000,-569,-1000,586,358,168,631,664,840,1000,10,5,356,-1000,-350,1000,-50,825,1000,-325,-138,1000,-31,-64,1000,1000,-954,-509,238,197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00350() {
        org.junit.Assert.assertEquals("VOID|getSize=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{-739,-613,-1000,1000,975,1000,236,-1000,-682,-990,-1000,704,-38,1000,742,-1000,-708,-992,320,-1000,-1000,899,-1000,-430,-1000,100,-1000,-894,-1000,132,-204,1000,-486,1000,-1000,-338,-1000,-59,-1000,-904,-377,-1000,-819,-1000,-1000,1000,-60,1000,-1000,-842,-502,162,775,-679,-323,-1000,600,55,-691,1000,1000,-483,-543,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00351() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{1000,1000,1000,-858,-246,-1000,49,400,732,300,981,-1000,240,-253,1000,887,982,1000,-606,1000,324,-1000,922,1,528,-370,767,1000,1000,842,-454,-1000,688,350,321,1000,-469,452,780,-812,651,694,528,446,880,-1000,755,-1000,361,504,-733,1000,-510,-1000,629,1000,982,-1000,1000,-190,-121,731,-659,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00352() {
        org.junit.Assert.assertEquals("VOID|getSize=java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{-313,-540,-1000,443,545,1000,983,-46,-580,288,-669,187,-565,769,929,-523,-563,-1000,-633,-657,-881,1000,145,1000,-640,1000,91,-454,-1000,-743,-273,796,322,-330,-29,-365,-1000,20,-870,-724,430,546,725,160,-714,1000,515,943,-1000,452,-98,692,1000,3,-911,-111,471,143,-219,446,720,-299,-448,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00353() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{304,-241,919,-239,1000,1000,561,1000,-450,1000,-811,-785,222,1000,962,-44,-249,-1000,-858,1000,-937,1000,227,1000,94,1000,-1000,-697,-688,-276,-300,-91,284,-1000,335,-507,-547,-324,-170,-557,677,284,939,1000,4,1000,767,-97,-1000,666,-289,894,1000,-97,-869,288,58,-360,385,1000,-213,135,-239,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00354() {
        org.junit.Assert.assertEquals("VOID|getSize=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{370,-801,-260,329,1000,-651,116,-391,-61,-16,-774,-536,108,363,671,-534,-854,-582,53,491,-692,1000,-449,252,-733,242,-658,-436,-1000,416,-611,212,386,416,-861,-89,-352,-672,-972,-771,-116,-348,-945,-629,-942,755,514,1000,-1000,452,-634,81,158,-654,-859,-1000,901,-300,-1000,1000,173,-70,-750,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00355() {
        org.junit.Assert.assertEquals("VOID|getSize=java.lang.Long:MTAwMA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{1000,1000,482,283,164,513,475,-804,-213,-109,220,-334,-156,168,1000,-457,576,1000,142,563,-1000,-1000,-1000,-1000,675,-821,-872,868,1000,96,192,-17,5,673,-780,834,-1000,93,-259,-824,1000,-635,579,-894,-368,-829,333,-275,361,928,103,1000,-224,-914,767,731,285,-747,-420,815,430,-821,-330,600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00356() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{969,-801,-260,747,1000,-651,162,-391,53,-26,-896,-562,-142,363,708,-482,-932,-498,-1000,491,-692,1000,-449,565,-484,1000,-658,-488,-1000,434,-620,72,422,383,-861,-89,-884,-357,-972,-771,1000,-348,-588,-629,-942,387,-234,1000,-1000,909,-673,228,268,-583,-791,-1000,969,-343,-1000,1000,173,98,-750,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00357() {
        org.junit.Assert.assertEquals("VOID|getSize=java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setSize(long):void",
            new int[]{872,-129,1000,238,600,-873,-288,249,171,475,-338,-1000,541,253,799,76,-384,26,-725,1000,-736,1000,-449,806,-347,330,149,95,-881,998,-1000,44,342,11,-861,64,278,-1000,-972,-1000,520,-472,-450,345,-942,379,534,-108,-1000,1000,-896,457,-324,-681,-855,-1000,1000,-262,-1000,501,-327,532,-1000,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00358() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:NjQ1NTk=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-640,656,490,510,-939,108,136,883,478,-950,-155,901,-825,-92,-967,131,371,695,762,145,-302,497,-293,-682,26,-977,-217,854,-782,-923,-101,473,448,-873,353,-930,493,-981,484,-984,278,-678,273,804,-119,-195,872,-784,-812,-920,-757,-679,-573,-377,443,258,-50,18,-497,989,759,-438,-740,963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00359() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:NjU1MzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{428,143,-1000,654,462,1000,-1000,1000,1000,-713,-1000,479,-1000,1000,707,50,826,-110,-1000,1000,234,1000,377,-533,-1000,-980,-604,950,-1000,74,-1000,-19,1000,717,-567,823,-1000,-1000,-1000,-125,-542,1000,1000,-1000,485,-332,-368,-172,-956,-133,-720,596,809,-205,99,-739,-267,-1000,-310,-1000,1000,268,-222,-293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00360() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-303,563,-190,-458,655,176,-355,967,522,-311,-931,568,252,257,-483,715,404,-569,22,898,-537,-347,92,527,66,-613,753,86,88,-393,-178,427,-677,-357,530,-160,-748,-711,-320,939,-712,493,651,385,-223,-18,-195,-26,59,-297,70,-240,902,-889,855,487,761,35,799,-134,487,-943,-659,998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00361() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:NjU0MzY=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-1000,1000,-378,412,48,77,-41,1000,575,-847,-660,997,874,575,-57,483,880,1000,725,267,-405,-995,936,-439,-237,-1000,-662,926,-1000,-1000,212,-687,-676,421,461,-1000,176,-586,-229,-803,-787,-547,-102,-404,-544,-163,642,322,-774,-518,-1000,-992,376,-411,293,325,341,-19,-332,347,294,-231,-536,841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00362() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:NjU0NTA=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-680,220,605,-409,338,204,679,-472,-132,-932,994,-175,786,170,241,26,153,918,-996,-300,-861,-350,-859,865,-171,-877,-189,-145,-835,-161,34,-531,-978,764,-350,-941,-788,858,665,-366,-947,283,-933,-985,-881,-851,-552,637,-312,240,-998,653,541,266,-190,-318,-186,-112,722,-252,-187,24,583,-246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00363() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:NjU1MzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{405,796,323,600,-400,-267,-558,559,1000,-611,1000,-205,1000,-280,1000,-44,387,-185,-947,-632,-157,-587,-263,262,-977,163,-84,467,-595,1000,-1000,172,-258,-849,-295,300,-1000,-263,837,-1000,40,676,-406,-1000,-424,-1000,-1000,255,-1000,1000,82,-118,-145,1000,-1000,-1000,181,-427,-894,455,-1000,598,1000,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00364() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-1000,711,166,-173,-1000,-31,129,700,171,87,-1000,1000,-627,372,-1000,1000,616,831,539,1000,-537,298,1000,207,-61,-1000,125,322,-1000,-1000,504,-602,-1000,-546,1000,-1000,397,-1000,-149,304,-1000,182,676,385,-1000,446,825,-1000,205,-1000,-752,489,1000,-1000,1000,1000,1000,577,1000,601,1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00365() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:NjU1MzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-343,651,214,269,-296,108,244,680,214,-1000,-205,651,-72,18,-589,204,371,-207,656,-191,-93,335,-229,-1000,-482,-713,-254,793,126,-923,1,320,448,-1000,174,-360,-993,-1000,15,-282,278,246,315,1000,172,-195,1000,-199,-562,-920,-154,677,-263,-680,443,-251,265,-821,134,-424,1000,-1000,-783,-19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00366() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:ODI=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{428,771,-704,728,593,866,606,1000,1000,-696,-1000,479,-793,614,707,361,229,-248,530,1000,-1000,595,82,371,129,-621,-14,588,-392,265,-1000,67,580,556,-567,823,-1000,-815,-1000,534,-489,1000,1000,626,260,-329,-463,180,-353,177,-82,-886,933,-1000,-133,-739,-26,-1000,-282,-907,748,268,-222,461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00367() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-1000,-65,147,1000,-939,-412,-231,-793,554,-1000,-979,-235,-1000,573,-1000,-77,259,929,1000,1000,-270,1000,-293,-896,617,-1000,-785,1000,-894,-1000,122,-384,-53,-254,-11,-1000,-114,-1000,855,-975,-981,339,999,300,97,-340,1000,-1000,-1000,-1000,-1000,-422,97,-416,930,1000,-50,183,-251,1000,1000,-274,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00368() {
        org.junit.Assert.assertEquals("VOID|getVersionMadeBy=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionMadeBy(int):void",
            new int[]{79,555,1000,253,-1000,1000,1000,-178,976,1000,1000,-761,-1000,610,-1000,-1000,-1000,1000,-619,-899,206,-266,-867,-1000,-246,-30,1000,462,-1000,1000,1000,-324,1000,-1000,-598,-1000,1000,-1000,906,1000,-774,364,675,-115,1000,1000,318,-1000,1000,1000,413,1000,133,428,-1000,-852,400,1000,1000,395,-1000,-205,216,64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00369() {
        org.junit.Assert.assertEquals("VOID|getVersionMadeBy=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionMadeBy(int):void",
            new int[]{704,151,702,-321,93,-128,262,-926,-368,-1000,-803,-1000,-814,100,1000,-711,-544,-699,1000,-189,147,-676,-101,885,-323,385,-441,1000,957,-272,199,-473,806,296,684,-134,-820,1000,1000,44,173,-64,-596,1,-812,-41,-23,1000,541,-1000,-577,46,490,-782,371,-78,72,151,283,-916,-88,1000,429,-759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00370() {
        org.junit.Assert.assertEquals("VOID|getVersionMadeBy=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionMadeBy(int):void",
            new int[]{-1000,-544,-1000,986,460,1000,-776,-1000,1000,457,-476,-134,1000,610,-842,1000,152,301,168,881,-349,842,1000,814,-246,-30,1000,462,19,412,488,-906,662,-31,-72,-203,1000,-977,260,-275,-965,12,460,-102,-882,811,-990,-245,392,65,733,397,87,1000,-1000,1000,5,1000,-60,383,753,-1000,-494,-907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00371() {
        org.junit.Assert.assertEquals("VOID|getVersionMadeBy=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionMadeBy(int):void",
            new int[]{-563,-865,-989,-446,1000,-1000,-1000,646,-295,-1000,-1000,-72,1000,-157,539,-246,650,95,1000,228,-1000,-620,1000,1000,-152,1000,-1000,417,300,477,199,-1000,-461,296,1000,328,-441,1000,30,-413,394,-6,336,-536,745,-866,-23,701,-1000,-564,96,742,24,-374,407,1000,-1000,151,-645,683,759,-1000,368,-865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00372() {
        org.junit.Assert.assertEquals("VOID|getVersionMadeBy=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionMadeBy(int):void",
            new int[]{-213,283,814,266,88,-682,28,-325,-1000,-979,-1000,-546,456,77,570,1000,919,787,-587,-252,-717,-528,794,864,-1000,858,-1000,-1000,469,30,1000,692,-1000,-419,1000,276,-530,-556,-1000,-236,-890,-413,-1000,641,-1000,435,1000,-168,-243,-1000,-1000,-1000,-53,15,-96,-922,-810,742,-831,488,1000,1000,-25,957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00373() {
        org.junit.Assert.assertEquals("VOID|getVersionMadeBy=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionMadeBy(int):void",
            new int[]{-1000,-405,-1000,308,546,-302,-821,202,-264,-4,-782,282,1000,441,-110,1000,714,-246,109,407,197,1000,1000,549,-298,-189,1000,512,-1000,-110,-439,-377,187,269,929,1000,609,575,43,-356,603,407,1000,-358,881,-298,-1000,-400,-104,309,1000,-332,-614,1000,40,890,-992,594,-301,1000,1000,-1000,-1000,83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00374() {
        org.junit.Assert.assertEquals("VOID|getVersionMadeBy=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionMadeBy(int):void",
            new int[]{802,171,864,-76,-137,207,572,-997,-309,-1000,-126,-1000,-527,100,1000,488,809,-465,400,-32,163,-648,-20,-58,-307,-814,106,1000,729,-385,-105,171,519,22,328,-672,-165,-480,348,56,-118,-88,-330,122,-279,-820,1000,400,838,-1000,-486,-209,379,-59,339,-603,-392,-329,357,-337,-578,210,25,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00375() {
        org.junit.Assert.assertEquals("VOID|getVersionMadeBy=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionMadeBy(int):void",
            new int[]{-116,-1000,-719,-207,1000,-831,-766,1000,-972,-1000,-823,-855,133,1000,712,951,924,-874,1000,752,977,703,777,1000,-152,10,351,512,-798,-634,-783,-830,818,1000,1000,1000,-467,1000,1000,-1000,909,264,899,-979,-28,-197,-350,1000,-249,-1000,947,-637,-17,-637,407,1000,-115,482,-645,-356,610,-426,-370,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00376() {
        org.junit.Assert.assertEquals("VOID|getVersionMadeBy=java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionMadeBy(int):void",
            new int[]{54,591,386,425,-1000,1000,1000,247,1000,169,789,-754,-522,261,235,-945,-778,868,-159,-1000,-1000,-1000,-79,-76,-1000,-517,-167,-792,34,709,1000,1000,728,-1000,-51,-895,304,-917,1000,691,133,-223,-128,724,1000,689,638,-1000,255,858,-743,1000,-96,-294,-1000,541,512,361,854,-181,-550,135,-1000,-389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00377() {
        org.junit.Assert.assertEquals("VOID|getVersionMadeBy=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionMadeBy(int):void",
            new int[]{810,-1000,-1000,-802,646,-704,-197,-527,-550,-801,-1000,1000,936,237,-1000,1000,530,738,1000,81,1000,1000,184,142,460,324,509,1000,-993,355,-90,-270,220,273,1000,1000,672,1000,-500,-188,-396,252,334,-867,105,-1000,-1000,686,-551,-649,1000,-400,112,942,847,-679,-615,1000,299,609,482,-1000,-54,572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00378() {
        org.junit.Assert.assertEquals("VOID|getVersionMadeBy=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionMadeBy(int):void",
            new int[]{-722,-784,-993,-1000,40,-989,-797,191,554,-288,-1000,-72,958,539,539,73,-390,118,1000,-203,-1000,-1000,1000,600,336,332,-939,1000,300,1000,1000,-1000,496,-335,918,328,60,1000,503,984,-600,-354,-487,380,797,-618,-23,139,-71,593,96,1000,-85,-374,-523,-93,-439,795,-1000,683,-497,-1000,632,-379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00379() {
        org.junit.Assert.assertEquals("VOID|getVersionRequired=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionRequired(int):void",
            new int[]{-858,-400,-844,-893,-450,898,460,322,428,-401,-450,57,-250,714,-313,-1000,-271,18,717,-806,40,-215,400,478,694,832,-19,-335,-660,-263,1000,-1000,846,91,997,-355,400,451,-1000,-837,547,-741,-57,260,230,198,-1000,121,1000,-1000,-336,588,-454,277,-971,-224,-33,602,-374,819,711,597,875,490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00380() {
        org.junit.Assert.assertEquals("VOID|getVersionRequired=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionRequired(int):void",
            new int[]{-1000,859,-765,289,-443,-601,618,788,-767,550,-746,1000,295,-2,451,-361,-205,-173,116,-1000,-603,-1000,753,1000,-22,861,355,38,531,-1000,1000,-62,-195,1000,-402,-1000,73,21,-720,-426,174,-1,1000,-186,177,-425,-340,-783,402,-1000,436,809,-1000,-926,-1000,-77,-154,1000,-958,-22,114,-221,52,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00381() {
        org.junit.Assert.assertEquals("VOID|getVersionRequired=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionRequired(int):void",
            new int[]{-639,-1000,-646,72,-1000,84,339,-37,247,-992,-209,399,-228,94,-545,172,-1000,453,-848,-278,-751,1000,1000,-754,-76,-381,-449,-787,-1000,-88,-1000,544,672,-1000,-1000,133,365,674,1000,45,816,-488,-777,227,1000,405,-987,432,1000,789,1000,-1000,1000,-137,972,-436,353,270,-486,593,-157,-1000,618,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00382() {
        org.junit.Assert.assertEquals("VOID|getVersionRequired=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionRequired(int):void",
            new int[]{-509,703,-492,-402,-924,417,311,269,0,-521,-744,642,357,-275,-325,-286,-141,-400,654,-942,327,-522,-319,737,441,944,282,94,283,-666,1000,44,652,257,1000,-723,1000,498,-919,-588,362,-542,372,666,26,-1000,-400,-114,406,-1000,-639,633,-836,1000,-917,24,296,48,-135,-274,90,730,87,974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00383() {
        org.junit.Assert.assertEquals("VOID|getVersionRequired=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionRequired(int):void",
            new int[]{-694,-1000,-468,939,1000,-1000,543,-886,581,221,168,896,825,-984,-538,-628,-488,-542,-351,-151,-1000,16,1000,-72,-771,154,982,305,-1000,-253,-1000,-366,-466,-1000,-978,768,1000,-739,-880,1000,-417,-952,365,239,415,327,-590,1000,46,-652,-239,444,-71,-41,274,-828,1000,-766,603,1000,1000,-1000,-448,-946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00384() {
        org.junit.Assert.assertEquals("VOID|getVersionRequired=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionRequired(int):void",
            new int[]{-50,-400,-765,466,521,-601,251,389,-254,58,-31,-49,60,-991,395,-361,-44,126,-1000,123,143,-31,753,-39,11,-73,553,48,-1000,-264,41,-62,-195,-1000,-402,-70,1000,644,264,-145,634,86,241,246,177,79,-400,43,-272,11,436,-422,-71,-926,-1000,-77,1,356,376,-668,1000,-221,363,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00385() {
        org.junit.Assert.assertEquals("VOID|getVersionRequired=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionRequired(int):void",
            new int[]{-6,-1000,-1000,-763,-338,1000,999,349,585,-845,-275,590,410,213,-58,-477,-818,-252,-960,-1000,-224,340,1000,-109,610,521,-432,-49,-1000,250,1000,-825,1000,-1000,744,-271,1000,364,-692,-1000,991,-1000,-594,975,1000,-562,-1000,386,1000,-315,-529,118,47,-106,-1000,-33,430,777,406,-728,1000,-547,-248,425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00386() {
        org.junit.Assert.assertEquals("VOID|getVersionRequired=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionRequired(int):void",
            new int[]{1000,-941,775,380,697,-237,-925,-335,539,239,1000,379,1000,460,-166,-1000,245,-176,214,1000,-1000,89,523,-999,-553,-894,385,647,-498,-323,-1000,-982,69,-164,-1000,1000,-1000,-1000,944,979,-906,546,-464,744,717,1000,157,-131,894,1000,1000,183,139,-51,1000,896,74,-51,1000,1000,-42,-1000,130,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00387() {
        org.junit.Assert.assertEquals("VOID|getVersionRequired=java.lang.Integer:LTI1", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionRequired(int):void",
            new int[]{-852,976,412,537,-444,-77,-116,123,-68,-535,-1000,295,-7,-191,477,-1000,-88,997,433,-1000,-1000,-284,-260,622,611,-297,81,-1000,84,-849,2,-252,346,755,132,-1000,-346,852,521,-564,1000,542,255,-353,-52,-1000,-72,-614,146,-770,277,-482,-264,-194,-810,737,-362,187,-1000,-723,-727,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00388() {
        org.junit.Assert.assertEquals("VOID|getVersionRequired=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionRequired(int):void",
            new int[]{-622,-885,438,1000,1000,-907,1000,48,-294,1000,-383,812,-575,-1000,-135,-503,-1000,858,-1000,580,-1000,89,816,384,-659,48,982,120,31,0,-581,-371,-1000,-144,-725,326,371,-1000,-1000,637,465,246,0,-982,123,758,0,1000,-1000,-569,267,0,93,-257,-460,1000,1000,-724,-144,1000,593,-1000,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00389() {
        org.junit.Assert.assertEquals("VOID|getVersionRequired=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setVersionRequired(int):void",
            new int[]{-151,75,-358,36,-1000,-197,-1000,-576,216,-732,-678,254,538,-641,-681,-407,193,131,-842,199,-43,-235,65,249,604,-264,428,-325,-963,-88,-1000,967,532,253,-1000,291,365,615,1000,45,-648,87,-156,469,467,943,-187,-127,1000,638,1000,-451,835,262,972,-4,353,-401,90,593,-1000,3,1000,-169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00390() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{147,412,122,342,698,999,543,879,-716,194,-303,226,-345,-821,-823,-457,-331,258,7,-717,321,437,-839,63,368,896,-317,-277,-603,-390,804,-393,612,-572,758,344,-686,-222,647,382,86,806,584,-630,-981,277,-347,-892,974,-191,715,-761,-152,163,13,-890,48,-816,-325,-687,33,747,727,271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00391() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-668,-712,799,-907,-716,556,-626,315,-1000,287,-379,744,904,-1000,352,1000,-452,-1000,534,354,-504,1000,-363,-755,-665,1000,119,59,-350,-159,-736,-1000,621,95,-463,477,1000,-489,1000,1000,1000,576,495,355,26,-223,286,-748,-164,1000,-1000,-833,97,1000,1000,411,138,-271,-1000,13,-277,646,21,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00392() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "writeTo(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream):void",
            new int[]{1000,732,4,-166,547,686,780,995,329,-1000,-1000,-249,1,-36,-722,-195,-1000,673,-1000,212,-83,802,-1000,-624,-778,-832,-1000,-981,249,-844,1000,1000,-830,724,716,586,-1000,-1000,-1000,856,-1000,1000,273,-984,-273,-818,1000,-1000,762,956,-521,504,116,-188,513,484,351,-908,-386,443,1000,990,1000,-818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00393() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "writeTo(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream):void",
            new int[]{658,-797,-96,-1000,-669,-929,272,490,1000,94,-631,-897,-1000,-1000,1000,-1000,-224,-354,-65,-250,-620,-705,-672,645,7,162,335,-1000,-690,1000,-50,1000,-485,-5,-496,855,751,29,859,-432,-8,653,-567,-859,393,277,-1000,344,545,604,-518,963,442,-981,-1000,-1000,-119,774,-90,400,-1000,-444,-1000,681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00394() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "addRawArchiveEntry(org.apache.commons.compress.archivers.zip.ZipArchiveEntry,java.io.InputStream):void",
            new int[]{-515,-371,487,1000,-620,1000,-1000,1000,-569,-365,-1000,-414,-613,-372,-348,1000,220,845,-456,413,975,-497,-161,-183,472,-40,-569,-233,798,634,416,375,174,820,-461,1000,590,435,-231,-335,-1000,164,-1000,1000,-557,-244,-725,-993,15,513,-178,-960,-294,279,196,71,-441,-638,-466,1000,969,180,-1000,303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00395() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "addRawArchiveEntry(org.apache.commons.compress.archivers.zip.ZipArchiveEntry,java.io.InputStream):void",
            new int[]{-255,567,-52,-667,960,785,948,-909,-115,194,871,-266,-294,43,155,55,189,-422,-306,745,893,-514,-940,-586,-540,-797,286,-849,-685,-428,895,-518,690,11,395,-542,783,-68,230,-709,-924,-502,319,-510,372,951,-258,747,933,753,869,-25,263,546,-706,-168,539,-447,298,849,-527,472,248,817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00396() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "addRawArchiveEntry(org.apache.commons.compress.archivers.zip.ZipArchiveEntry,java.io.InputStream):void",
            new int[]{428,184,341,-657,242,1000,-899,1000,-111,-828,1000,-591,39,160,376,755,190,-764,-658,1000,198,1000,1000,-45,1000,863,-571,482,1000,1000,371,-452,633,-1000,-1000,-1000,72,-1000,-978,1000,226,1000,-1000,-1000,-844,-119,-1000,1000,-255,-567,710,920,-1000,-1000,-1000,-1000,635,127,904,561,-117,1000,948,927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00397() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{-310,-410,307,-549,683,340,-792,683,525,-836,506,-733,-826,489,-563,-455,-571,-958,28,985,855,434,70,155,692,34,-696,-590,177,-232,-649,12,397,-212,919,-739,662,861,918,-390,175,-276,-767,721,753,-300,150,942,312,394,640,788,-110,-557,-900,-340,-919,-30,-514,265,-51,-358,-728,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00398() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{-39,1000,-382,-585,1000,254,-473,851,193,-742,1000,-1000,305,1000,-500,-1000,768,-889,-172,-56,-753,1000,1000,1000,-1000,98,-682,1000,985,810,339,-240,-406,1000,-1000,233,585,1000,-1000,-1000,-691,-914,-862,612,986,-1000,907,1000,-1000,583,-170,-883,1000,347,106,-1000,-594,-382,65,-1000,1000,-1000,182,-619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00399() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-269,278,822,495,-788,399,545,1000,123,1000,-116,578,-287,-620,480,215,186,-194,767,166,127,-450,771,224,-924,-183,-681,35,761,-1000,320,-471,153,-901,-1000,1000,802,1000,-1000,659,-610,-735,-837,-843,630,647,832,311,-1000,207,132,960,407,-1000,-325,988,-44,-515,-503,-1000,519,612,570,-196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00400() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-371,629,-126,17,-1000,-656,-245,580,-210,459,234,-29,-661,-231,600,211,120,-508,-123,-449,1000,-647,771,799,438,292,-436,310,-17,-1000,320,-729,703,-656,-455,1000,-434,884,-970,-80,-652,206,676,-817,-631,878,529,621,-658,1000,1000,-292,-655,-430,-63,988,-50,-547,204,-1000,1000,1000,570,-39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00401() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{-555,203,-56,-252,-49,-1000,-364,1000,-1000,341,-1000,877,1000,-775,1000,-29,-921,1000,389,95,-292,-1000,-1000,-1000,-1000,-1000,-697,-773,1000,-793,548,-1000,264,-1000,1000,596,-592,-2,300,141,-1000,1000,260,-1000,-1000,-1000,-130,-1000,-693,117,209,-573,492,-1000,1000,651,-1000,-954,1000,1000,-157,1000,-782,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00402() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{116,673,298,693,-253,-1000,-134,627,-201,522,185,1000,1000,-554,615,985,171,308,-111,-1,-580,-693,-709,441,-616,-1000,988,-1000,1000,-789,416,-753,84,-53,-734,1000,384,-679,369,40,318,120,50,-1000,-1000,-1000,456,-483,563,187,58,-1000,681,441,1000,1000,-926,-702,-259,-419,569,1000,-299,-660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00403() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{552,181,342,711,-353,-126,-811,88,955,-168,-909,-631,503,412,-366,-686,-139,-46,-399,635,836,866,233,878,223,963,721,304,-983,-116,-220,-697,66,-911,214,-635,-855,737,984,168,-901,-455,-572,964,886,642,-986,-891,-923,-617,-702,337,727,93,587,148,-630,-193,228,-424,344,212,418,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00404() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-323,184,939,-378,272,616,359,-554,332,521,-152,362,-419,819,-965,-462,-853,-336,-951,223,748,668,310,538,233,983,-536,-876,-577,-170,-604,-534,760,-221,238,-801,-720,829,-78,231,80,-794,-52,148,654,647,-201,-959,171,-71,-311,607,158,391,179,214,848,-698,623,-567,967,-957,349,520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00405() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-1000,892,244,-1000,487,1000,-194,893,-1000,1000,1000,123,-1000,227,949,621,-1000,527,-220,-325,657,147,-849,163,787,1000,-996,423,-20,723,-1000,307,-623,268,-1000,619,487,1000,270,379,107,-684,506,1000,1000,-57,741,454,729,256,-109,-529,-449,1000,-1000,1000,807,-543,276,631,-1000,-1000,-116,461}));
    }
}
