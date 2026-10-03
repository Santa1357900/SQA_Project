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
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "addRawArchiveEntry(org.apache.commons.compress.archivers.zip.ZipArchiveEntry,java.io.InputStream):void",
            new int[]{239,-366,-356,360,-725,-1000,-2,-616,716,418,35,-192,34,-826,1000,-73,-238,1000,-515,-469,195,-1000,-141,700,-184,831,94,5,1000,-117,337,-703,-338,-899,776,23,390,1000,-375,-49,-508,730,-604,-261,-372,-78,-111,-322,237,-316,607,981,-73,1000,-974,-177,-728,1000,59,-4,-517,258,-165,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "addRawArchiveEntry(org.apache.commons.compress.archivers.zip.ZipArchiveEntry,java.io.InputStream):void",
            new int[]{433,-2,448,-222,-57,-428,1000,-1000,351,5,562,182,34,-517,1000,-1000,-504,837,-1000,-1000,26,-641,-597,671,-80,831,-84,438,818,-257,823,-631,-207,-1000,784,-44,390,1000,-984,502,-1000,74,-569,-495,-1000,-78,15,-322,1000,-149,-414,1000,267,-1000,-755,426,-1000,939,-11,-40,22,-43,1000,477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "addRawArchiveEntry(org.apache.commons.compress.archivers.zip.ZipArchiveEntry,java.io.InputStream):void",
            new int[]{-1000,545,-462,-112,844,60,-77,-599,695,611,234,160,1000,342,-818,314,816,-433,1000,62,805,246,1000,934,416,1000,19,-141,-908,20,-723,-854,232,1000,-340,511,-1000,142,1000,-496,505,-822,1000,1000,127,1000,306,850,-174,-536,-626,606,572,-1000,-240,-439,-657,714,-679,972,299,-570,-168,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "addRawArchiveEntry(org.apache.commons.compress.archivers.zip.ZipArchiveEntry,java.io.InputStream):void",
            new int[]{-327,200,199,-566,1000,-1000,299,-731,351,1000,-823,-1000,324,-1000,-407,534,356,837,969,-813,1000,-101,1000,1000,1000,831,-84,534,-731,-97,-811,-757,-320,-1000,884,1000,-1000,1000,-984,268,-656,1000,-569,-495,-1000,-1000,1000,-898,-702,-1000,-414,1000,267,374,-1000,-15,-914,624,-1000,32,278,317,489,898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "addRawArchiveEntry(org.apache.commons.compress.archivers.zip.ZipArchiveEntry,java.io.InputStream):void",
            new int[]{-341,-379,-751,-73,348,-1000,3,-438,909,-541,-31,-92,1000,-311,-1000,218,627,1000,545,94,934,-476,234,1000,-141,607,269,447,1000,833,-490,-354,-1000,1000,-396,120,-1000,764,162,-21,-832,738,-1000,-110,993,1000,317,381,-455,-1000,1000,-446,23,1000,-261,72,-466,-5,-74,607,-117,269,-207,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "addRawArchiveEntry(org.apache.commons.compress.archivers.zip.ZipArchiveEntry,java.io.InputStream):void",
            new int[]{1000,-124,663,485,497,-960,260,1000,-147,147,512,-139,-456,-447,-5,200,26,6,-696,948,258,-218,-1000,-793,-442,-710,752,-6,629,-546,693,140,149,-1000,-77,-1000,-332,-621,-570,611,-69,160,117,-576,415,-1000,-433,31,-7,570,-804,-605,732,-311,763,458,147,-535,-276,-47,160,-217,1000,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "canWriteEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{40,663,1000,-886,-33,-277,-236,1000,-56,-725,-693,-405,665,-289,565,-175,-780,15,-362,55,510,-609,-131,-710,-895,-292,-315,-1000,-191,963,-167,618,218,216,-188,212,-629,91,-55,-25,-955,-14,-72,1000,471,-1000,607,-567,-209,-148,110,28,1000,-100,249,64,98,-557,1000,-195,-290,-206,311,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "canWriteEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{540,-758,-92,50,166,283,734,668,-1000,-93,1000,-166,-811,242,-23,1000,254,-765,-253,-237,-165,-817,-301,-595,-51,-392,1000,1000,-53,583,-113,-503,319,134,1000,1000,148,423,367,-1000,20,-159,1000,-649,-406,-494,-919,-91,-1000,-300,1000,-392,-116,766,750,-336,-317,1000,561,-772,-806,-402,1000,-712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "canWriteEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{-256,155,-641,34,-1000,-716,-987,1000,227,740,-437,488,793,979,-628,-1000,-726,464,-151,-499,213,-12,1000,-209,1000,-895,372,-1000,-251,552,-31,-667,-1000,1000,850,-236,-703,-433,-26,-282,-1000,708,-801,-85,-293,1000,261,-720,427,-28,-654,-699,252,-239,95,-1000,-872,577,462,21,-1000,-279,1000,610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "canWriteEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{-1000,-1000,-342,901,-307,968,170,117,-1000,309,1000,983,-46,-1000,763,92,1000,-188,-144,90,-1000,764,831,660,992,-103,909,-814,-492,-84,-81,-1000,427,202,578,1000,591,389,-54,114,704,-314,-1000,219,-1000,870,35,1000,36,1000,-31,-1000,-1000,-272,-564,-216,-87,239,-392,664,-1000,-740,1000,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "canWriteEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{-145,368,-967,338,-595,-271,-706,683,28,306,-437,566,526,612,-3,-84,-26,602,140,915,851,-641,692,264,164,-679,557,-946,-386,947,-245,-471,-815,355,875,713,-84,-268,954,-914,-888,37,-127,388,702,-75,683,-88,-897,-317,-387,-324,539,461,955,-712,-515,-880,507,-549,-300,-415,773,623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "canWriteEntryData(org.apache.commons.compress.archivers.ArchiveEntry):boolean",
            new int[]{814,-559,694,1000,944,-501,8,-987,1000,-800,-313,737,-106,-483,772,-1000,96,-171,1000,346,663,598,865,-75,-151,564,-1000,-621,-795,214,967,-544,395,-591,-1000,1000,1000,-668,-893,1000,1000,-563,1000,256,1000,-178,-848,-1000,-33,263,1000,1000,-58,-881,-1000,523,623,1000,-1000,430,1000,-527,-1000,520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{-1000,-495,-64,242,-794,-340,674,-58,1000,-1000,322,1000,511,341,-1000,-408,-478,-447,-1000,847,-850,-265,-1000,1000,297,-800,903,-879,20,-191,231,-940,-644,795,-538,977,-1000,983,563,-321,-1000,923,-1000,188,1000,551,-416,779,-349,198,3,-503,-327,852,1000,976,-1000,-657,294,-675,186,-385,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{188,-787,819,-357,-512,-130,-700,-431,-35,297,784,-741,33,277,-128,77,134,872,491,-957,608,-509,418,-471,963,-286,-845,354,-69,-86,-246,631,222,-417,-901,295,-370,994,-643,-425,-654,299,-170,858,689,550,-699,-667,-736,-823,87,648,583,505,498,377,-434,-445,163,-490,15,-981,786,439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{-1000,1000,659,440,-306,77,-1000,-690,119,-1000,1000,192,-981,-754,-953,-284,-914,-193,62,-802,-1000,799,-61,178,963,-1000,318,507,1000,245,-695,-504,487,-64,1000,-470,-816,-454,-758,933,-303,151,-1000,259,832,1000,-1000,262,271,-1000,1000,310,396,1000,1000,334,-514,1000,1000,-764,1000,431,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.io.UnsupportedEncodingException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{-246,-246,-1000,249,-396,813,196,-646,122,-560,424,208,1000,-799,-689,-1000,-825,-887,-506,208,-1000,1000,-628,786,-1000,-81,1000,-639,583,-343,-854,1000,1000,746,119,946,-57,152,1000,572,-912,838,-1000,300,525,59,-339,68,362,-364,-697,-730,-281,-1000,-1000,-885,-481,-522,134,-854,-856,156,457,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{-134,620,-126,484,-227,687,-1000,-1000,400,-450,360,126,-259,-1000,469,-803,-1000,-1000,-812,-1000,-1000,-633,-130,1000,655,-821,979,250,867,-349,-400,-715,376,1000,990,-216,-1000,400,-352,400,-578,1000,-843,841,1000,733,-669,-506,-636,-1000,984,-540,-40,-45,-95,103,-537,434,1000,-1000,112,-566,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{404,-396,400,-513,-608,586,534,-413,1000,-149,-1000,1000,-1000,692,-1000,963,178,1000,-542,782,-786,734,-581,-79,798,222,-1000,544,1000,-181,-717,1000,-798,-148,-172,589,-1000,1000,376,-120,56,-272,-1000,1000,1000,-570,1000,1000,1000,573,607,-773,-1000,69,353,1000,445,-510,1000,-1000,310,-1000,307,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "close():void",
            new int[]{824,15,-740,389,-94,1000,-541,599,-1000,1000,290,-844,761,122,-456,-731,-467,-792,82,-889,-468,-384,1000,207,-127,914,289,782,-1000,352,809,-909,1000,1000,420,-1000,509,-1000,58,-1000,-116,-191,212,288,-1000,59,-1000,503,-1000,60,66,-799,794,-967,-368,-1000,431,603,-1000,782,-334,769,85,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{159,15,288,-27,-736,-976,-147,-441,-464,973,21,925,1000,-680,-399,143,-783,926,118,-733,-123,958,-77,344,-850,-504,-134,943,-57,-496,-1000,-635,1000,-1000,441,806,181,176,1000,-560,514,-793,178,-379,375,602,-342,-1000,-354,-164,-526,419,1000,1000,195,335,412,185,-90,1000,846,-106,-317,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-1000,-553,-731,-767,-1000,-903,-367,-337,1000,-408,-309,-145,1000,545,-495,-118,-1000,902,1000,-532,98,-1000,351,1000,-1000,-1000,546,248,-1000,384,-951,465,1000,-518,-577,591,-453,675,-637,-311,-915,702,804,287,375,932,-225,-328,740,398,-787,-659,165,597,-248,69,-1000,-273,311,1000,-1000,-1000,-764,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-802,-500,-77,-1000,-233,-814,397,726,313,-915,-623,-261,-234,-329,-313,247,-1000,-1000,775,-203,-123,-1000,-454,1000,-1000,1000,-501,-411,-995,159,-1000,953,-400,786,441,-358,245,-1000,-1000,405,-1000,1000,959,18,279,590,412,688,-354,-238,120,-678,-1000,-166,436,-450,-929,-1000,410,-830,-761,-106,-549,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{71,1000,289,-435,1000,-1000,-394,-1000,-29,-874,14,96,-913,-11,296,39,859,324,-618,-1000,-927,1000,-616,269,-325,-177,535,532,-155,613,1000,-214,1000,-1000,480,-924,73,1000,-203,39,1000,-653,-840,-984,407,-218,33,-967,-354,-748,249,-1000,-118,664,471,-1000,369,410,25,309,-1000,475,1000,636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{204,-1000,544,-972,-1000,-71,-847,-21,726,320,-624,635,660,-240,904,278,-1000,264,-193,-1000,-243,-225,-144,1000,-1000,454,-273,-999,-279,-142,-1000,-643,-70,796,-1000,784,-477,733,772,53,-393,1000,519,422,238,1000,669,567,-50,1000,-409,419,-977,-549,222,1000,-336,-746,-833,-281,-243,-503,-889,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.io.IOException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "closeArchiveEntry():void",
            new int[]{-346,-1000,-831,-556,-1000,-222,-706,-204,729,-297,-893,-216,1000,-479,-248,-184,-1000,1000,-263,-541,-40,-1000,-1000,1000,-493,661,-681,-354,-1000,5,-1000,1000,773,331,111,1000,-300,412,-394,357,-391,887,1000,861,1000,1000,-222,712,-915,119,-1000,300,377,458,-457,-649,-811,-375,185,-369,-1000,-698,-627,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{267,33,-344,-409,-186,-212,-6,-321,-97,439,566,-116,221,987,254,-9,-293,-367,1,1000,733,-1000,-776,-690,1000,-312,-926,-139,347,371,-620,-1000,535,-880,806,-190,-23,87,943,843,63,351,-236,-76,815,-891,933,-244,-498,-367,-752,-131,-137,230,560,389,-1000,-318,449,-325,-23,-708,-828,-56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{192,-68,514,628,-841,420,-451,440,1000,662,-414,-1000,-976,137,162,166,-1000,-940,-596,-203,-215,-263,-56,524,-1000,-110,401,585,-1000,-429,1000,1000,515,-711,-644,1000,-277,880,-164,-79,87,113,-1000,-1000,705,-1000,488,675,-45,103,-663,-1000,-1000,-885,882,440,-485,-581,130,-430,-404,-563,612,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-764,-952,-496,141,-1000,-376,320,344,27,-490,1000,-84,418,702,-1000,-604,-602,467,304,-376,156,-485,-1000,494,571,-1000,-1000,-35,-840,224,335,222,1000,-753,410,-68,-305,677,1000,867,-270,-987,175,208,738,375,1000,889,-458,260,629,407,-433,151,111,660,408,-400,-665,746,88,-825,492,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{9,-1000,-628,471,-80,-281,505,-607,-568,-70,1000,-325,641,1000,-663,-713,-933,-175,852,1000,505,-1000,-667,-697,1000,-422,-1000,-897,-405,962,513,-1000,186,-646,1000,-123,-498,-400,1000,1000,353,-876,-494,-374,2,472,1000,396,-1000,-934,-1000,788,-230,-28,987,-148,-677,-1000,-311,132,603,-1000,-921,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-1000,-437,998,763,1000,1000,234,-519,138,-22,-493,341,1000,1000,848,-1000,836,1000,691,-1000,719,752,1000,-1000,480,1000,-127,863,1000,-434,1000,229,-1000,1000,407,-828,-286,-1000,591,826,-173,504,1000,1000,-1000,570,-749,-62,1000,-266,-1000,336,1000,875,-685,-1000,1000,-226,-1000,-1000,1000,1000,-1000,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{506,-772,-541,651,-389,437,-268,-300,-1000,-661,1000,-815,-401,-14,-812,-6,-1000,-88,216,1000,-747,62,-843,-245,100,-1000,-1000,-1000,-1000,569,777,-454,501,-444,129,890,-180,792,1000,289,415,-752,-684,-1000,-163,947,953,774,-37,-509,-1000,798,-698,-1000,1000,171,617,-334,-86,835,-1000,-645,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "createArchiveEntry(java.io.File,java.lang.String):org.apache.commons.compress.archivers.ArchiveEntry",
            new int[]{-767,-70,-726,-415,559,-47,969,-159,-795,-520,1000,141,459,1000,-546,-1000,-507,493,1000,1000,1000,-1000,-1000,-67,566,-626,-1000,-165,-110,1000,-109,-1000,419,-638,706,-170,-127,408,1000,361,-212,-489,342,334,167,1000,981,715,-339,-1000,-961,640,-472,710,1000,739,780,-377,-135,835,-95,-817,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{-1000,-681,-1000,1000,111,-1000,346,-56,-1000,-474,-357,-132,340,415,-100,-310,1000,1000,1000,239,112,535,402,-690,598,339,111,1000,424,-1000,-631,931,303,476,-836,1000,-626,-747,-844,464,-75,632,-291,-270,922,-235,1000,700,-1000,965,666,-198,-506,1000,410,1000,411,-203,1000,397,193,-437,1000,-207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{-1000,-466,584,-193,-378,-131,-190,-289,-696,-519,-766,129,636,1000,691,-277,1000,-400,1000,-1000,-287,-409,752,387,3,778,50,391,1000,535,-320,-55,-336,-419,-1000,-400,628,400,-217,255,793,1000,-396,180,75,-62,-66,-15,-400,-391,-487,-1000,1000,-181,132,1000,811,296,741,580,1000,-939,-226,-76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{-544,-947,-816,1000,1000,-1000,1000,-490,491,-348,233,520,273,691,-608,-275,553,1000,193,993,531,1000,-475,-1000,922,-466,164,1000,1000,-1000,160,1000,1000,1000,130,1000,99,-1000,-916,-149,-1000,183,-1000,-1000,926,-1000,851,1000,-892,1000,-1000,1000,636,1000,415,1000,329,312,734,1000,-348,140,1000,-474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.io.UnsupportedEncodingException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{-400,195,-812,1000,-223,-1000,881,-317,-434,-485,653,-378,759,120,-805,461,-136,1000,400,-716,-745,1000,-727,-810,1000,1000,-478,1000,597,-678,-194,1000,512,1000,-1000,1000,-253,-1000,-888,-203,-1000,142,43,-493,1000,-1000,510,107,-881,921,21,266,-517,1000,200,1000,526,-744,-165,460,347,-100,1000,-102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{973,876,588,818,-475,289,624,-1000,966,-439,759,414,84,-452,-315,293,-136,-293,-1000,1000,-1000,17,-1000,-662,432,853,-1000,810,1000,243,-208,954,878,913,1000,-30,779,-651,403,26,-1000,-1000,907,196,386,-724,-1000,-32,519,877,-731,657,446,-288,-78,594,-462,925,-540,880,494,154,780,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.io.UnsupportedEncodingException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{-737,-594,-816,1000,-234,-1000,227,-92,131,-348,634,309,-45,825,-100,25,914,1000,1000,-799,-485,489,-646,-1000,585,477,164,-30,629,-351,94,1000,594,623,-716,1000,686,-1000,-817,622,-560,567,-5,-628,922,-566,905,671,-1000,1000,-1000,11,1000,1000,415,1000,328,-203,467,817,933,-98,863,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{-1000,-984,180,111,297,994,-176,492,-509,-882,-48,263,-1000,1000,1000,102,559,-55,651,-140,195,119,-104,-53,-798,208,-1000,1000,986,1000,422,239,-328,76,496,477,-784,357,501,642,-453,-83,400,-325,-284,-237,-400,157,1000,1000,1000,-418,-874,159,275,611,465,494,14,978,172,334,255,64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "finish():void",
            new int[]{973,833,-188,545,376,-1000,624,96,704,-824,998,63,-39,-490,-1000,101,-33,1000,-756,1000,-916,510,-1000,-577,529,373,-432,603,710,-113,-330,707,1000,1000,614,-137,769,-1000,293,324,-1000,-1000,-192,230,591,-1000,-558,903,-845,923,-522,1000,368,700,-147,1000,-274,417,-46,1000,97,204,616,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{-259,-24,-436,659,-265,-922,369,-627,333,4,-292,989,902,862,-35,-612,749,375,376,-188,907,-472,824,782,344,965,439,-49,-386,-359,-973,-262,-450,-306,309,760,459,-275,-754,-71,467,-205,-712,-150,138,293,-699,692,327,-458,146,-964,-686,-475,-610,404,-274,-576,-529,-887,-518,804,-185,926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{1000,-1000,-611,-149,-744,338,938,-304,-850,-735,787,-1000,1000,-661,-519,476,-759,791,-525,415,1000,-1000,1000,-1000,-412,-254,-844,-744,-526,-1000,-139,-36,-820,1000,-1000,-1000,-978,-1000,590,863,-16,428,-853,1000,-961,1000,-784,512,-1000,-543,521,-560,562,-1000,1000,-841,-378,-450,193,-1000,599,947,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{-97,1000,-402,401,-791,-210,232,-102,47,-94,-381,1000,379,166,213,-1000,170,-152,200,619,-1000,-212,-272,353,758,1000,940,-217,-743,-868,629,117,344,738,811,-169,-922,825,-1000,627,-195,1000,-976,270,268,-841,-170,380,264,-115,-477,905,-876,710,-219,274,-507,396,-884,-1000,-770,18,-461,974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{1000,-608,329,-146,-469,801,1000,561,-140,-238,429,-671,-301,-517,-396,-139,-719,-132,-272,-179,-800,-551,1000,-405,1000,-1000,-1000,-1000,-1000,-676,509,378,-1000,1000,-963,-792,-304,-812,1000,997,560,-924,723,1000,-826,65,-720,-1000,-1000,-151,993,-14,1000,-356,454,-395,271,-201,915,289,1000,298,754,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{-404,-742,-436,-474,12,809,234,-784,-643,68,1000,347,1000,-966,18,-631,356,-530,-697,227,-1000,-377,1000,-887,-934,-733,-1000,669,-98,342,986,-587,790,-44,313,-1000,-927,-1000,1000,1000,-654,-551,80,-1000,955,193,-949,-1000,911,576,826,-95,-147,-510,306,357,309,210,-1000,617,-243,-332,229,-104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{-97,242,463,1000,-1000,-465,870,-392,165,-853,-726,-388,53,-429,-472,1000,-1000,863,270,1000,175,-545,-707,301,-805,-541,97,-1000,-1000,-957,-35,986,-1000,1000,-1000,-1000,-213,-298,-284,627,-367,513,-976,890,-708,-407,408,-1,264,-789,-477,525,-115,1000,1000,276,-513,165,-452,-594,442,1000,-461,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "flush():void",
            new int[]{-248,965,-416,-177,-669,-516,-350,-38,92,-173,-402,1000,-29,-159,-208,-515,355,68,130,851,-1000,-272,-156,593,1000,601,529,-492,-305,-716,772,367,-31,548,295,491,-3,331,-553,696,-209,396,-638,478,-94,-706,169,342,215,-337,-572,275,-290,836,-225,205,-956,-5,-363,-958,-172,-351,718,799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.String:NDEzLjA=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "getEncoding():java.lang.String",
            new int[]{1000,924,788,-1000,-542,-108,2,609,507,389,413,576,-1000,-156,-930,18,-179,-943,157,-196,-400,-922,-400,-400,-369,-1000,-24,-306,400,-369,-18,783,225,82,62,-78,-110,51,-400,106,-375,-247,-132,-281,-149,-424,597,440,-646,-964,-400,-272,-12,-327,521,-1000,47,-799,-303,489,-24,682,125,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.String:VVRGOA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "getEncoding():java.lang.String",
            new int[]{964,-554,-331,863,1000,434,-1000,905,473,-522,-668,676,103,-616,-101,470,-1000,1000,-152,735,325,-228,-969,-776,370,-860,-226,18,-94,728,375,-350,667,438,-1000,-580,50,359,-535,341,-424,17,-247,-306,-364,-620,-468,130,-64,-521,-169,-37,350,900,-294,-112,456,935,-595,-278,-216,-973,-406,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4MTJi", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "getEncoding():java.lang.String",
            new int[]{1000,152,184,975,711,299,789,-111,331,-277,-551,-232,400,384,773,792,969,-469,467,556,1000,127,984,675,1000,91,398,-299,-1000,611,934,283,455,-315,-126,-106,910,-52,0,439,462,1000,-1000,-504,933,-985,-39,-780,347,-1000,1000,885,1000,1000,-33,-426,362,538,-87,322,-355,827,-417,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:YQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "getEncoding():java.lang.String",
            new int[]{964,-554,289,863,776,-22,-663,952,822,-788,-994,274,751,-362,-101,339,-881,148,480,76,-36,-365,-498,-776,370,-662,-335,-806,-94,542,126,-677,757,438,-455,-983,50,435,-757,-800,-520,17,-247,-314,-364,-620,-468,618,-368,-521,-53,-37,607,900,-223,-812,456,992,-595,-278,765,92,-337,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMC42NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "getEncoding():java.lang.String",
            new int[]{1000,802,737,-393,-75,1000,212,-352,140,1000,-175,1000,-34,1000,243,-187,1000,-1000,471,927,1000,23,1000,1000,1000,-953,614,-377,-1000,901,1000,-161,566,-627,-768,-441,903,1000,1000,692,830,1000,-1000,517,864,-478,165,353,-607,-434,1000,1000,-289,0,879,1000,1000,-117,-1000,-572,-631,930,396,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "getEncoding():java.lang.String",
            new int[]{1000,-400,-336,570,516,1000,-1000,641,1000,-861,-516,-1000,352,-111,881,651,405,1000,176,881,1000,1000,-1000,452,1000,-558,95,-732,-1000,615,481,18,1000,-263,-1000,394,560,-264,121,-365,-317,215,85,-1000,52,-150,114,469,-398,-449,-98,302,185,901,-141,-428,694,694,-493,-250,377,32,-1000,284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "isSeekable():boolean",
            new int[]{-326,-414,1000,933,1000,323,-1000,-666,-11,496,-490,-1000,-641,-824,-187,-577,1000,-1000,-1000,-106,-484,1000,1000,381,-1000,609,1000,-604,-863,-1000,-222,609,1000,-1000,307,-922,1000,230,-954,-104,-704,-227,-120,-723,453,342,-1000,-1000,448,672,-1000,-846,1000,1000,-671,1000,337,-1000,-984,-1000,410,636,-841,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "isSeekable():boolean",
            new int[]{-1000,-52,218,462,1000,-793,-1000,153,-794,223,-229,-68,-870,-270,-122,-198,1000,-1000,-1000,-1000,-1000,1000,1000,342,-973,730,186,-51,-1000,298,1000,-954,-1000,-1000,1000,-1000,1000,703,-1000,-468,-51,119,-90,-1000,-295,-444,206,-497,-997,897,-1000,-519,532,1000,-814,498,-175,-1000,-768,-945,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "isSeekable():boolean",
            new int[]{1000,-515,523,-409,-531,620,1000,-1000,352,191,960,797,-399,-710,809,1000,-1000,87,866,-386,1000,-97,-1000,31,68,519,-526,-349,-68,-1000,-197,-322,-571,219,-1000,1000,449,-1000,881,1000,716,-168,671,521,89,971,-886,-1000,-1000,73,1000,139,-1000,306,1000,888,-564,148,1000,1000,-239,381,-484,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "isSeekable():boolean",
            new int[]{550,989,-937,-841,204,353,353,-707,400,445,-927,-599,31,-837,950,-643,203,479,-745,-532,74,415,906,76,-378,-520,364,338,983,-398,982,539,674,-430,-233,-915,-584,970,332,-928,73,723,240,226,-288,459,910,-97,778,-848,794,-145,927,-265,-390,-515,-797,728,-902,-552,-56,303,-23,-377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "isSeekable():boolean",
            new int[]{-424,-275,509,1000,828,635,-735,-871,-483,-112,161,-927,-426,-337,-780,-158,676,-1000,-1000,1000,1000,1000,-1000,-1000,457,566,-417,-63,882,-1000,131,-31,-346,-587,-1000,440,1000,-454,-560,1000,-72,-72,499,-25,629,-16,-1000,-836,-338,-278,-1000,-1000,457,1000,844,1000,350,-1000,-372,1000,-605,-593,-159,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "isSeekable():boolean",
            new int[]{-746,-1000,-282,904,1000,48,-1000,-188,-760,-70,178,-658,-366,-659,-944,-541,1000,-1000,-949,281,-264,929,129,874,-835,442,-35,-242,-615,-141,655,-583,-1000,-195,9,-889,1000,309,-934,53,-173,494,457,-322,521,251,-89,36,-656,220,-1000,-208,532,956,-240,1000,-509,-1000,-1000,-596,-328,-892,-773,960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{653,-996,-892,28,2,-1000,-760,124,-20,-724,156,331,-972,-1000,665,-247,-281,-1000,-1000,-556,166,517,543,-251,1000,250,-1000,902,666,-368,-703,-270,-712,1000,1000,-326,342,501,574,1000,-900,-538,-488,171,242,-416,249,-179,1000,1000,145,903,-336,458,-959,804,944,-1000,232,1000,-1000,-1000,711,630}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-476,46,-811,615,1000,1000,1000,641,-265,1000,-631,376,520,-86,-14,1000,-245,1000,543,-893,-416,-703,-853,-232,-869,-804,-348,756,878,-460,192,704,277,-853,-100,542,-815,-242,629,-1000,-461,72,-1000,-311,-850,83,-712,-47,-617,663,-456,471,734,541,1000,141,-158,548,1000,-27,1000,-324,829,658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{279,-1000,-696,79,-53,-1000,-337,141,-790,-1000,68,687,-983,-1000,1000,817,-922,-871,-1000,-479,950,-95,-671,73,-62,19,-1000,1000,1000,-906,-374,-567,562,-378,1000,511,439,-575,355,-719,305,-21,-431,402,261,-45,-194,-252,1000,1000,-383,291,-225,1000,33,1000,383,-1000,-701,1000,-278,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-515,868,-496,284,1000,1000,38,-273,-591,240,912,-699,-507,-11,-148,-186,1000,775,1000,-893,129,-880,518,-232,120,773,533,756,31,646,91,361,-711,394,-1000,-1000,579,-657,-1000,-861,-461,106,-674,-279,-504,-1000,-1000,-47,-544,1000,-456,-1000,504,918,206,-551,92,548,-1000,1000,-308,-723,1000,11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{984,-172,-741,-706,774,-729,-215,-53,-919,-539,122,484,-726,-580,917,-671,598,-621,-767,-366,434,-41,747,-523,500,462,-674,768,606,248,122,-486,-672,381,681,238,417,251,-288,751,-866,-274,-855,245,-209,-490,-79,-278,768,896,-485,-873,-2,820,-346,270,504,-740,-105,581,-34,-606,776,73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setComment(java.lang.String):void",
            new int[]{-68,-753,36,210,38,-204,113,753,579,636,113,-1000,-1000,-15,183,-1000,344,567,-423,559,-787,-603,-895,-385,-559,-628,-1000,-231,-949,299,-634,667,1000,-806,367,351,-858,-225,769,-496,-156,251,539,-882,253,-1000,1000,774,-346,-457,423,304,1000,-517,18,-1000,574,56,456,-853,-458,212,-671,699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setComment(java.lang.String):void",
            new int[]{-1000,-1000,-251,-913,622,-272,74,551,-886,-59,-587,565,13,-384,-964,-241,-1000,-506,704,512,1000,-771,-169,-993,1000,-107,-546,428,704,-326,202,-969,789,317,-724,352,440,-134,725,-1000,-987,720,-1000,-634,829,53,952,425,757,355,505,470,373,443,1000,-364,-502,540,-295,-416,1000,138,-431,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setComment(java.lang.String):void",
            new int[]{242,118,-106,759,-171,292,758,-532,-470,408,500,-91,182,931,-332,628,-536,836,-893,-423,910,-630,-54,-710,99,334,-223,81,-488,617,690,488,544,926,-623,-852,-274,906,590,337,342,-801,921,522,309,-637,70,-538,446,-958,-424,606,72,275,303,-153,-760,-558,-98,-942,874,-38,-63,-749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setComment(java.lang.String):void",
            new int[]{79,-276,751,-989,1000,-752,-338,-313,464,300,89,400,864,-1000,-1000,205,-1000,-901,1000,1000,1000,-1000,144,-251,-328,169,-988,-487,-111,735,83,-1000,88,22,-103,1000,-307,-1000,430,-552,1000,352,171,-169,-1000,-594,503,1000,818,214,735,-422,-296,-96,-183,-333,43,1000,286,-1000,700,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setComment(java.lang.String):void",
            new int[]{-296,-352,-146,1000,-519,896,-410,-575,-1000,220,-690,-985,347,1000,-411,1000,-551,1000,-1000,-670,437,-950,-282,-1000,1000,232,-242,1000,-228,462,316,-151,681,1000,-1000,-867,46,1000,826,152,-830,-453,447,-162,1000,-576,-1000,-850,282,-1000,348,409,-76,550,1000,440,-1000,-447,-601,-608,1000,-449,-392,738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setCreateUnicodeExtraFields(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream$UnicodeExtraFieldPolicy):void",
            new int[]{-102,-795,-802,1000,816,370,-510,106,-1000,618,299,1000,-1000,483,1000,850,-384,643,534,-1000,-1000,-1000,1000,333,651,3,-1000,1000,873,-692,1000,-1000,-787,-1000,-980,175,-972,-1000,584,354,173,-837,-18,-215,-972,-961,-949,1000,-4,-1000,-1000,-1000,1000,-948,716,599,295,-586,-571,671,155,1000,-709,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setCreateUnicodeExtraFields(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream$UnicodeExtraFieldPolicy):void",
            new int[]{-461,722,298,860,188,397,376,-996,-112,1000,-897,-369,-366,1000,1000,-505,-1000,1000,118,-1000,207,-1000,971,-1000,67,-1000,1000,1000,490,460,1000,1000,-1000,-192,417,847,1000,-126,-626,-1000,1000,661,593,-1000,587,-1000,-881,1000,599,-21,-1000,-775,984,-1000,-491,1000,-954,-1000,-567,657,-484,1000,235,-935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setCreateUnicodeExtraFields(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream$UnicodeExtraFieldPolicy):void",
            new int[]{370,1,-871,-886,890,266,-102,-632,-734,462,-160,434,721,254,986,44,-853,363,-476,-829,-759,-273,-144,274,-437,265,-1000,699,-1000,-497,-117,-843,-452,-584,417,542,-1000,94,-967,759,1000,-120,-175,-760,237,97,1000,1000,976,-763,-1000,-260,34,369,167,1000,493,212,-551,573,401,6,-678,678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setCreateUnicodeExtraFields(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream$UnicodeExtraFieldPolicy):void",
            new int[]{319,1000,-571,520,41,809,-558,-76,247,196,763,1000,-1000,-384,-186,380,-727,638,373,-1000,-1000,94,1000,521,670,224,1000,1000,-827,-316,970,718,-632,-934,653,633,1000,-1000,1000,352,-678,-323,-490,608,196,307,-718,-249,-449,1000,33,-292,1000,-861,-537,-241,-239,-699,224,1000,20,889,320,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setCreateUnicodeExtraFields(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream$UnicodeExtraFieldPolicy):void",
            new int[]{27,1000,639,831,59,767,-306,-632,401,774,-1000,-128,334,858,-382,-683,-853,741,-540,-790,711,710,625,-1000,-437,-995,744,1000,-287,792,583,1000,-1000,440,417,1000,1000,396,-967,-1000,1000,254,735,-1000,1000,-257,-518,927,599,743,-1000,-260,984,-170,-1000,725,-1000,-934,-339,657,-484,1000,235,-935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setCreateUnicodeExtraFields(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream$UnicodeExtraFieldPolicy):void",
            new int[]{572,-34,934,-374,441,646,1000,95,-647,-282,710,1000,0,-243,-850,-270,414,-763,-1000,289,573,1000,-576,-1000,-1000,-1000,414,-740,-1000,1000,-404,1000,1000,230,1000,-781,1000,77,-1000,-1000,-966,-47,787,806,-147,843,352,-1000,-1000,478,1000,-785,723,640,-1000,1000,-643,-656,1000,1000,-1000,483,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setCreateUnicodeExtraFields(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream$UnicodeExtraFieldPolicy):void",
            new int[]{918,862,709,146,-1000,637,-691,-1000,1000,-69,-684,617,424,650,110,-616,-897,868,-521,-130,349,286,1000,-1000,-1000,-221,386,445,-695,1000,303,431,-45,63,-104,784,481,795,-912,-741,802,-222,954,-1000,1000,275,-1000,902,713,-68,-1000,1000,263,-16,-932,1000,-619,365,643,130,266,661,588,781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("VOID|getEncoding=java.lang.String:MHg4MDAwMDAwMDAw", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setEncoding(java.lang.String):void",
            new int[]{1000,311,-891,-1000,1000,-400,-502,-1000,410,652,-636,-210,-298,119,1000,-383,1000,329,738,188,-105,-858,1000,-768,-1000,-805,-754,578,-1000,-19,89,-951,1000,268,587,-529,-718,-1000,5,472,-1000,-1000,980,-780,360,-411,505,-1000,-926,-245,-582,-1000,983,1000,-568,-914,-572,883,197,799,-1000,545,-1000,771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.nio.charset.IllegalCharsetNameException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setEncoding(java.lang.String):void",
            new int[]{675,537,12,234,-321,1000,663,-955,983,394,-395,460,-840,945,275,-1000,1000,663,-654,171,1000,1000,-972,1000,50,-1000,-390,1000,-122,864,-491,-258,-369,-472,-48,-500,-716,1000,-21,151,213,770,54,-614,686,790,-236,125,-961,-230,-45,-31,-380,404,-1000,1000,-652,119,786,-1000,264,78,207,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("VOID|getEncoding=java.lang.String:MTAwMC44MDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setEncoding(java.lang.String):void",
            new int[]{415,514,839,-888,1000,187,-547,-589,237,1000,-1000,494,313,565,1000,164,518,-235,1000,900,-192,-1000,142,-1000,-267,142,-568,1000,-803,71,-544,-1000,97,-247,-207,493,-821,-440,-586,1000,-1000,610,1000,-1000,745,41,-817,-1000,-1000,-776,-149,-927,1000,741,-338,-875,-889,863,472,640,-1000,340,-1000,518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("VOID|getEncoding=java.lang.String:MHg4", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setEncoding(java.lang.String):void",
            new int[]{-170,-1000,724,-538,332,-400,-597,463,248,-667,-636,670,982,1000,-1000,-1000,1000,-1000,-1000,1000,454,-387,524,-426,856,-1000,-104,856,135,-19,-1000,1000,547,680,-1000,-1000,-1000,215,560,-292,-881,184,-833,128,-16,581,-1000,1000,1000,-729,-582,767,983,1000,-1,1000,-572,954,322,219,713,437,863,37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("VOID|getEncoding=NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setEncoding(java.lang.String):void",
            new int[]{323,417,500,-697,-933,-198,151,376,502,-992,446,-500,-429,1000,179,423,1000,172,202,-1000,22,1000,1000,-1000,-205,-92,-336,-515,-8,493,-963,1000,1000,376,416,-190,401,-945,-622,-569,-301,-576,257,-903,725,-478,356,613,868,-748,22,110,-130,522,508,1000,-456,-11,-309,533,196,-422,123,239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("VOID|getEncoding=NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setEncoding(java.lang.String):void",
            new int[]{180,192,1000,-665,725,658,-903,-513,584,1000,-551,477,43,194,111,-476,144,-664,-861,881,-3,-919,-1000,275,-338,-798,-173,1000,-641,471,-794,-1000,-514,-584,-555,161,-999,392,419,543,-376,598,202,-600,1000,240,-1000,-721,-1000,-932,-347,-134,554,477,-1000,-731,-1000,506,755,1000,-193,111,-485,-575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setFallbackToUTF8(boolean):void",
            new int[]{-468,120,-416,-613,-357,662,-547,434,-244,-399,-569,-921,-595,-573,75,-1000,-732,1000,-52,1000,32,-279,612,366,-21,-1000,-620,-543,-6,1000,-206,-761,119,191,906,936,191,-192,-1000,-1000,-1000,543,448,268,-524,-562,337,542,-423,229,1000,1000,241,-83,-724,354,1000,-57,164,851,-1000,45,982,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setFallbackToUTF8(boolean):void",
            new int[]{77,611,484,-517,-899,-946,428,1000,-1000,-17,-225,-958,-1000,564,-1000,-55,-68,1000,-1000,197,669,-1000,-181,1000,-533,-1000,850,1000,-57,1000,1000,-628,462,598,354,977,-2,-1000,51,13,-1000,1000,530,-820,815,-507,-294,-632,-756,74,488,-485,-54,469,-284,373,-55,-15,-1000,-661,-249,1000,-179,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setFallbackToUTF8(boolean):void",
            new int[]{647,1000,-386,-748,-1000,102,1000,-428,1000,1000,-426,-630,-1000,-355,-1000,306,-194,1000,288,1000,-375,-1000,-165,1000,161,-1000,-904,110,-1000,1000,-354,-1000,1000,-49,1000,-129,-535,-576,-1000,-1000,-1000,1000,-191,-410,-908,-1000,1000,321,-1000,-65,1000,1000,130,293,-529,1000,1000,638,-1000,851,-876,1000,1000,172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setFallbackToUTF8(boolean):void",
            new int[]{-1000,-828,-1000,-930,1000,1000,-926,-1000,571,-1000,-1000,-1000,1000,-945,1000,-1000,133,691,1000,826,-892,846,1000,-1000,1000,-158,-1000,-1000,956,-546,-1000,-26,806,-426,1000,947,-1000,765,-1000,-414,-178,-1000,349,1000,-1000,410,476,-275,1000,1000,1000,905,730,-173,-1000,-387,1000,-1000,1000,1000,-1000,-1000,789,38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setFallbackToUTF8(boolean):void",
            new int[]{-1000,-1000,-271,624,1000,1000,-1000,-1000,-623,-548,-1000,-1000,424,-1000,1000,-1000,-1000,857,621,71,264,276,1000,-1000,250,-896,-942,-1000,1000,322,-1000,-1000,-1000,-832,956,1000,-88,1000,-955,-675,-825,72,648,1000,-1000,-1000,-495,745,844,1000,1000,1000,449,526,-1000,-74,1000,-1000,1000,1000,-1000,-1000,-188,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setFallbackToUTF8(boolean):void",
            new int[]{572,649,484,99,-699,-738,1000,-939,480,-402,-348,352,532,663,-566,546,411,-371,-606,496,548,-685,-140,1000,746,1000,549,1000,-1000,-35,639,1000,1000,-270,-392,-664,-1000,-322,589,264,960,-839,328,-442,1000,-1000,117,-558,482,-279,-1000,-1000,229,46,883,188,-1000,-350,-846,871,1000,388,2,241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setLevel(int):void",
            new int[]{-528,891,-1000,-1000,-458,-751,-596,-851,-654,-586,1000,-992,-136,951,-567,542,-616,31,-632,93,-59,-247,717,-1000,-873,734,-985,367,-1000,-459,-474,-667,815,-723,222,608,938,106,801,-336,288,-294,22,954,1000,-953,807,-398,-967,-450,-775,-182,289,47,652,221,-328,118,661,69,-338,284,-263,-106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setLevel(int):void",
            new int[]{-54,-880,-56,-1000,-1000,-376,-120,-308,215,-130,531,623,-1000,-138,181,542,451,1000,-387,-29,1000,1000,134,-1000,672,-763,-218,1000,-1000,206,-268,1000,171,1000,1000,1000,306,818,624,-336,-725,1000,1000,-286,-217,1000,-570,820,949,719,1000,-399,544,738,297,-127,215,-268,-1000,144,-559,911,248,-211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setLevel(int):void",
            new int[]{-1000,1000,474,277,-68,445,-448,-782,109,-289,-1000,-806,221,1000,8,428,510,1000,-431,1000,-1000,-529,-855,-543,1000,1000,457,-1000,548,-692,-355,-416,402,-354,751,173,722,-368,1000,1000,1000,1000,-1000,1000,-13,-1000,490,-375,-48,-280,50,400,-568,-1000,-175,165,-1000,161,-330,-457,14,-446,1000,170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setLevel(int):void",
            new int[]{45,-481,-21,-645,-548,1000,388,185,862,548,-1000,234,-34,83,1000,-53,548,1000,1000,406,-369,785,-530,-1000,1000,-13,307,1000,270,79,-247,1000,-575,1000,817,-511,-64,-1000,359,958,1000,1000,-516,90,-581,1000,23,460,1000,712,1000,-1000,-455,-255,144,-287,-1000,330,-647,1000,-1000,-1000,-102,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setLevel(int):void",
            new int[]{-191,-105,260,-381,178,391,46,102,-144,-614,-512,-1000,438,966,731,344,-66,-53,-1000,107,-983,18,-524,-558,277,990,-239,-795,1000,-1000,-989,-400,-211,-915,-201,-958,531,-1000,-122,47,1000,768,-1000,346,666,-702,524,-256,-882,-540,-532,-299,-354,-847,745,949,-608,956,627,-400,-1000,-1000,47,664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{-1000,-531,616,-186,-852,-382,382,479,-1000,-70,-346,920,717,-400,1000,-185,1000,-975,-262,-189,-591,1000,177,131,-1000,412,112,1000,-155,1000,-50,102,-1000,-1000,664,-704,-383,-450,74,-583,547,201,950,1000,341,1000,-1000,-422,-400,1000,247,322,-1000,849,-1000,221,309,-500,-429,-72,-87,-384,670,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{150,-901,518,-196,-752,-375,219,363,445,361,-578,1,427,-400,913,-30,490,-48,355,236,-62,956,22,169,-1000,719,112,526,-626,1000,-201,1000,-394,-134,901,1000,-12,504,-1000,-634,79,777,814,-371,139,315,-714,183,33,925,-2,512,-605,143,43,267,529,-1000,-588,602,460,-384,777,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{340,451,939,140,-970,201,-436,-53,-1000,-432,-999,-418,638,853,1000,-1000,700,-480,122,-271,1000,681,1000,-438,-618,471,-263,564,-139,-559,1000,96,-793,1000,1000,1000,1000,106,-1000,-753,210,1000,638,-578,450,704,-1000,-441,1000,-428,1000,106,-730,1000,-338,-264,410,1000,-337,710,91,713,148,245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{-304,-70,-566,849,366,388,18,996,364,588,-924,1000,235,-122,-808,-854,370,-484,620,1000,70,901,66,629,-1000,442,387,251,-804,32,184,416,571,-360,214,-1000,1000,-697,75,408,-88,-476,750,-604,-861,935,-1000,710,-12,289,-401,181,-823,-8,-738,-852,-753,-1000,7,918,-737,764,0,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{-887,-157,-957,-398,-437,655,508,-148,-690,602,-340,-481,-747,-1000,-631,832,650,636,-556,977,-1000,634,884,-565,493,487,541,579,-902,691,-314,-975,-195,1000,1000,688,-542,-785,-359,-749,876,-501,880,704,389,275,-714,1000,33,-988,181,544,-1000,632,-63,484,857,-693,-242,766,305,-183,-917,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{1000,-1000,-472,-140,-810,-654,-949,-258,131,-1000,-276,296,497,-72,1000,1000,-19,-467,487,106,382,28,-311,-766,513,343,704,-4,-137,-1000,-545,1000,-1000,-15,488,-274,-470,1000,-488,-208,116,811,-498,-1000,1000,851,-707,-867,-381,1000,-9,425,-612,524,-58,724,-947,1000,500,415,1000,-328,-373,-832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setMethod(int):void",
            new int[]{-1000,377,-296,1000,1000,492,-842,1000,-114,16,2,872,548,-275,-222,966,653,-119,1000,940,-1000,-1000,-1000,-628,502,974,-524,737,-1000,-1000,-389,-835,31,981,-503,-1000,1000,-1000,929,912,-1000,-1000,-1000,120,573,435,-330,1000,-97,-721,-545,87,629,-539,58,527,-206,727,423,237,-1000,1000,-451,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseLanguageEncodingFlag(boolean):void",
            new int[]{-388,384,-392,-438,-1000,-883,28,-87,-555,-103,-568,464,-172,478,68,-146,917,536,-306,402,-615,462,-398,-1000,-707,-942,278,-521,1000,235,-343,1000,201,1000,862,-1000,-737,-233,1000,983,594,-215,-1000,-35,-240,-619,-5,555,638,-16,-1000,458,-575,355,-673,222,-507,-724,583,290,-295,609,335,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseLanguageEncodingFlag(boolean):void",
            new int[]{-1000,-116,-546,464,-263,-1000,13,633,-445,-258,105,1000,-625,577,543,-34,387,469,-313,1000,-303,40,-435,59,289,-997,510,-747,605,924,-1000,875,-602,685,253,-552,-72,1000,739,1000,837,-785,-1000,-1000,435,-113,-567,1000,1000,-226,1000,644,-1000,1000,-108,1000,122,118,843,-545,792,1000,454,436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseLanguageEncodingFlag(boolean):void",
            new int[]{-1000,623,799,-731,-1000,74,-964,-700,380,-22,419,-714,47,-165,-307,-1000,1000,816,16,-73,204,46,-984,-800,-1000,-1000,521,-676,1000,-327,56,1000,993,1000,1000,-1000,-546,-1000,1000,309,835,-154,-403,-573,-884,-616,255,1000,302,393,-1000,1000,-1000,-198,311,-1000,-987,-86,164,934,-233,-303,1000,-768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseLanguageEncodingFlag(boolean):void",
            new int[]{-160,361,219,-759,679,638,1000,1000,1000,-182,731,-771,400,-1000,-519,-923,-1000,172,-139,-392,-226,1000,-584,1000,1000,1000,413,-1000,-1000,1000,1000,563,589,-841,585,-213,555,501,-37,-1000,442,319,-147,1000,-929,-611,-1000,-945,267,-1000,1000,482,-163,673,-29,133,-221,36,-1000,1000,896,-745,870,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseLanguageEncodingFlag(boolean):void",
            new int[]{271,-703,319,-1000,1000,1000,61,-662,987,690,415,-1000,1000,-950,-976,-1000,-1000,-645,-874,-187,-694,-530,-145,1000,-1000,-564,375,1000,-996,417,996,901,1000,-218,178,-300,116,-1000,-124,-1000,227,291,1000,-863,-948,23,584,80,-393,196,951,-76,629,-1000,-584,-394,-484,-1000,-765,-96,616,154,7,-632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode):void",
            new int[]{532,-1000,-696,-204,441,-1000,-1000,447,898,1000,1000,-1000,-121,-457,447,-751,324,142,1000,414,-413,-895,33,-1000,1000,650,-406,1000,223,760,-583,-1000,-1000,159,32,1000,-1000,1000,1000,-1000,1000,-122,-646,-836,-485,-1000,-168,-889,853,-543,1000,674,273,-1000,-1000,1000,422,555,423,-1000,990,168,-1000,-743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode):void",
            new int[]{517,132,-1000,-119,66,-924,-505,883,700,963,663,1000,-250,-121,-311,735,456,182,972,1000,-550,-809,-147,-1000,-267,1000,-437,-320,-109,-593,53,-869,-698,915,-1000,893,588,1000,1000,-680,1000,-1000,236,-145,-135,-758,-256,-260,57,-331,1000,365,220,229,-1000,-1000,330,291,350,-659,368,830,-767,264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode):void",
            new int[]{308,-824,-741,592,-174,-388,629,-192,144,227,-580,-472,-335,-315,-1000,13,369,-22,583,107,-628,-181,-377,173,36,99,-477,-283,640,-699,-388,-503,-330,592,-808,1000,304,-649,-57,208,671,829,-211,442,-333,611,426,-942,286,1000,489,16,-157,-162,-197,-58,196,-420,1000,257,1000,26,202,-864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode):void",
            new int[]{-26,319,-21,734,-1000,26,-292,832,1000,1000,775,-291,-62,558,1000,1000,190,-1000,367,49,-303,-435,-473,495,-245,-32,551,262,553,40,57,-693,-1000,-312,492,-483,535,1000,1000,-15,-258,-1000,766,-272,-769,-206,-270,140,611,792,-89,-411,831,-149,-1000,403,-190,-381,71,-85,111,-142,-157,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode):void",
            new int[]{-388,-436,-482,455,144,-791,-23,1000,-61,1000,-31,-585,-756,-160,-704,-404,273,-460,283,-353,-120,422,-236,909,-707,1000,-846,585,784,-1000,-1000,-324,776,956,-247,1000,1000,-865,-473,283,778,416,29,794,-114,597,35,-1000,665,-50,-1000,-260,855,208,58,344,-400,-37,-233,290,1000,-156,-958,123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode):void",
            new int[]{-1000,-1000,292,444,672,-1000,-6,-326,890,-20,-188,878,1000,-1000,220,44,1000,1000,1000,1000,-634,91,-188,100,529,-47,-1000,-140,1000,-282,-1000,-879,81,111,-62,-766,303,-1000,-213,652,172,125,-885,113,-330,189,891,-867,1000,-532,1000,637,-733,-1000,-34,-1000,872,390,1000,-41,378,-1000,39,-198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode):void",
            new int[]{6,-1000,-142,-227,-227,-348,-951,21,-1000,686,-355,1000,309,-848,-1000,-832,78,1000,273,508,-611,843,-287,-104,1000,-180,-342,1000,626,-429,-793,-1000,605,238,-922,-207,-156,-1000,-201,-1000,1000,776,-733,844,-508,513,1000,-734,436,157,542,477,-874,-486,236,-1000,407,214,115,-174,1000,-1000,18,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{479,405,488,988,474,-111,-378,-216,363,-267,-452,1000,-106,764,-390,658,630,-87,-147,-1000,-610,-84,75,-456,-1000,64,902,-646,-310,-658,-1000,-457,-53,351,137,-19,112,-323,170,-1000,810,710,368,265,258,-403,338,-1000,796,592,-435,-195,-56,1000,105,93,-196,-158,-400,1000,1000,631,-797,-208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-416,1000,-691,-292,-1000,1000,-47,817,-878,-887,1000,283,1000,819,-125,369,164,103,-89,759,789,-639,-658,358,557,174,-1000,-1000,-766,-340,709,1000,-916,-412,-699,-1000,1000,-382,11,999,-147,285,-591,593,-210,-197,767,1000,931,-584,-58,1000,-1000,-120,838,608,346,188,471,-1000,1000,1000,312,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{286,866,-51,-281,30,-791,60,690,-1000,-417,956,30,-382,630,-335,637,160,400,-445,950,-150,278,805,251,513,369,1000,25,394,-392,847,-300,750,-609,576,24,1000,1000,1000,1000,851,-816,-883,157,-74,-389,421,-1000,-513,-403,389,448,727,-440,253,531,-147,896,-256,-674,-54,762,16,-682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{483,277,-951,-823,131,-739,63,950,-550,-139,456,30,222,1000,105,158,-652,-1000,1000,1000,236,-587,1000,-1000,800,732,413,-691,716,-550,11,836,1000,-876,250,-1000,927,1000,1000,1000,1000,-316,-882,-635,-456,56,-455,764,124,-850,-3,671,241,-1000,143,304,326,345,1000,-955,294,886,37,-614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{88,-782,294,590,960,-1000,52,66,-762,387,33,743,-63,-297,-437,-305,-227,-130,1000,-898,168,202,400,-588,-138,-326,465,-469,-219,-753,-152,-20,677,-455,-99,11,-363,-115,-351,-1000,267,329,141,-1000,-400,-52,-862,-1000,276,287,-388,16,331,-218,741,272,118,1000,-286,-124,859,407,-932,220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{789,380,429,3,784,-934,712,664,-1000,-134,776,15,814,1000,-65,449,-712,-760,210,-191,-252,-303,1000,-137,-1000,1000,-921,358,-637,-763,606,451,1000,-845,805,-1000,77,692,1000,956,556,-23,-292,-1000,-722,-631,-416,-128,-27,-968,95,-268,550,-442,-96,728,-410,1000,681,-869,-279,680,-1000,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream", "write(byte[],int,int):void",
            new int[]{-89,-952,-711,-1000,-736,-132,450,1000,-762,-152,382,260,738,256,138,-544,-870,-778,1000,804,655,-1000,845,-610,758,376,-171,-1000,-219,-606,-152,1000,125,-1000,-1000,122,1000,544,-74,308,171,628,280,-807,-1000,-184,-862,690,425,-175,-261,-1000,-274,-1000,571,777,1000,1000,-275,-1000,288,864,-151,152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{634,321,-685,13,934,4,-37,720,1000,675,-237,-342,-131,32,-893,26,83,-1000,589,2,-886,-1000,1000,-665,-452,6,283,212,1000,-583,-1000,354,658,630,-851,-1000,72,-600,768,-1000,429,-526,-517,-413,1000,-362,85,-1000,-651,333,-822,-862,210,264,-740,1000,110,-600,524,683,680,81,687,780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{264,-778,419,-214,-1000,671,223,-1000,372,400,715,920,718,-600,-930,-1000,-124,616,1000,-192,1000,928,-556,112,1000,1000,66,-1000,1000,1000,399,-84,-232,-166,779,902,-607,-1000,649,693,-327,535,-1000,-445,-546,622,-482,1000,-1000,-676,-362,-1000,-1000,-1000,508,-1000,-1000,-321,-161,673,81,-622,-480,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{-65,-85,616,585,-1000,-493,369,-912,1000,675,554,42,910,-411,-893,-1000,-245,102,1000,-6,1000,928,-193,-488,460,1000,-390,-1000,1000,1000,702,-224,-157,-937,1000,244,-342,-1000,136,-1000,1000,1000,-1000,-998,-666,-681,-692,406,-1000,-440,-1000,-1000,-267,-1000,-368,-1000,-1000,-1000,1000,1000,-828,-631,-1000,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{82,-778,-623,-463,-998,-176,86,-1000,725,1000,641,920,1000,-600,-1000,-211,-124,1000,933,217,1000,637,-1000,-196,1000,1000,-806,-1000,84,1000,863,-402,-1000,-827,886,441,-836,956,-1000,1000,694,595,-657,251,320,303,-1000,1000,-612,-676,-1000,133,-253,-595,615,-1000,-1000,-626,681,65,408,-1000,-1000,-139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream", "putArchiveEntry(org.apache.commons.compress.archivers.ArchiveEntry):void",
            new int[]{433,-1000,-846,1000,-517,-631,854,702,-103,-991,-401,47,759,43,85,-1000,-325,-730,538,811,697,22,1000,-510,379,1000,-772,-1000,1000,-141,-979,-417,592,364,177,-66,270,-990,767,-1000,491,83,-467,30,953,-216,-793,-314,-659,-763,-750,-1000,7,-720,-320,226,-618,-703,-285,536,1000,-867,509,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "writeTo(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream):void",
            new int[]{-513,566,49,-537,-208,575,-380,-62,-677,395,684,-873,-771,-62,694,-516,-5,777,-231,-434,-644,-958,-189,415,-912,-497,-530,587,-352,-721,-981,-268,-937,-570,-570,426,9,-986,-760,-623,589,-899,-847,20,-32,-277,-234,-951,-279,737,266,-514,-463,227,-485,515,-293,-834,-451,-495,778,835,-826,377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "writeTo(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream):void",
            new int[]{-174,-172,814,63,604,1000,-836,-83,801,-286,794,420,385,938,298,-724,176,515,31,-846,-262,776,-666,883,218,-630,-85,654,794,-329,-420,-466,-232,-91,-223,427,-94,-471,691,-25,207,-843,997,848,885,-920,-199,-638,-897,111,-198,434,-264,361,-787,520,278,-424,-353,-664,-109,-590,-196,971}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "writeTo(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream):void",
            new int[]{-421,-689,546,253,-557,-484,-240,-844,93,-920,-756,451,406,-173,787,-198,-940,645,621,-905,485,637,419,887,-733,309,-806,579,387,-812,-438,-861,146,51,113,-946,161,286,913,-484,361,-170,-474,-151,594,566,371,616,134,142,703,-19,153,-277,86,461,61,380,793,447,490,525,280,-405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator", "writeTo(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream):void",
            new int[]{-810,-562,-328,-206,553,915,662,-914,-736,-465,-374,-757,-451,12,59,-622,-967,528,-521,-828,246,-57,-357,988,-318,78,947,48,-568,-628,643,-49,909,995,-232,-420,702,-147,-26,-158,-848,824,787,620,901,-972,283,-177,-625,-12,-382,-532,-75,-896,435,379,-846,55,-253,-734,912,430,-409,-321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ScatterZipOutputStream", "org.apache.commons.compress.archivers.zip.ScatterZipOutputStream", "writeTo(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream):void",
            new int[]{-182,-263,1000,582,594,-871,-1000,-134,-1000,837,-809,-464,-779,-243,-166,-76,-47,1000,-1000,-1000,1000,63,-101,-385,-706,-170,597,-393,113,-303,-1000,-734,-1000,-756,-717,-781,1000,-348,-729,1000,-968,-191,-1000,-362,-130,-547,-912,1000,-746,-15,216,-637,1000,205,-689,-1000,460,-600,-1000,721,-229,-1000,397,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ScatterZipOutputStream", "org.apache.commons.compress.archivers.zip.ScatterZipOutputStream", "writeTo(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream):void",
            new int[]{-479,647,444,220,-759,183,155,-806,-43,-789,210,310,757,-207,935,808,-837,45,-880,221,-215,-556,-90,-226,871,210,870,-245,-124,-673,730,-694,-854,-192,1000,953,652,141,-933,-483,352,-496,755,-487,177,-705,350,-896,931,801,-916,-218,447,433,740,-504,220,-179,-379,728,467,312,-544,136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ScatterZipOutputStream", "org.apache.commons.compress.archivers.zip.ScatterZipOutputStream", "writeTo(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream):void",
            new int[]{145,584,-813,-857,751,821,937,-134,-136,681,906,216,126,457,-195,-785,-995,517,33,-296,-509,370,99,-947,-458,972,-331,8,341,566,211,-680,445,-627,878,720,-273,-207,740,-930,-353,418,785,47,840,121,179,625,-746,781,-756,-252,163,-239,277,144,-756,173,931,987,-366,190,-804,-750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ScatterZipOutputStream", "org.apache.commons.compress.archivers.zip.ScatterZipOutputStream", "writeTo(org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream):void",
            new int[]{30,-89,3,824,-105,-430,-948,55,-780,842,-621,232,718,434,740,-590,-17,904,459,-778,960,898,374,-864,-917,674,944,239,877,-594,-821,-607,-246,-240,-285,-489,621,597,-153,-41,503,295,-395,645,861,-868,-920,237,-465,-515,-884,-906,316,-854,200,-952,25,-17,-53,927,700,-621,-85,857}));
    }
}
