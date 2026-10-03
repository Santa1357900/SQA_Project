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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "clone():java.lang.Object",
            new int[]{742,235,504,196,856,1000,263,50,712,469,-594,200,688,80,403,465,-1000,-278,1000,-184,-321,645,-1000,-436,-128,-1000,146,156,-527,235,806,1000,-229,884,1000,306,-1000,331,800,871,617,1000,1000,-499,1000,-587,-1000,-544,-1000,52,-1000,-834,-949,378,1000,-168,-1000,620,-1000,-565,945,-259,-1000,497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "clone():java.lang.Object",
            new int[]{557,-1000,4,-989,-864,178,756,-556,-956,881,-1000,-956,773,591,1000,-954,-428,1000,-448,497,969,606,-827,-283,1000,-395,100,-596,-1000,-359,311,-607,-638,827,-118,978,-545,-617,871,699,60,-347,1000,-133,893,-869,1000,365,583,1000,-760,-71,-151,-1000,-369,271,-480,1000,205,299,365,564,-370,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "clone():java.lang.Object",
            new int[]{448,-1000,-381,-1000,1000,-1000,519,126,-1000,128,-430,-1000,553,1000,919,-1000,-921,-177,-331,-730,1000,-312,-75,214,1000,-577,-521,-418,-1000,845,85,-429,-1000,980,-1000,1000,645,482,951,744,861,599,574,-293,-72,-349,-688,1000,277,1000,977,-277,902,-1000,-435,671,-1000,605,-644,970,-689,-531,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "equals(java.lang.Object):boolean",
            new int[]{-865,-422,-231,494,1000,-1000,334,-923,-840,1000,-26,-650,1000,-964,-1000,-418,745,364,-238,397,-1000,-909,697,314,764,-13,1000,1000,545,-1000,584,1000,-363,-1000,921,357,-653,-330,-261,1000,54,901,-1000,-338,402,-1000,-586,1000,1000,1000,-150,1000,-1000,-1000,-1000,358,-302,-1000,939,-1000,703,1000,592,378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "equals(java.lang.Object):boolean",
            new int[]{578,-241,-906,-131,1000,-1000,179,-304,-588,1000,1000,-939,1000,488,-710,131,297,342,-1000,762,-113,-1000,835,597,745,600,-878,1000,435,246,-9,1000,148,440,348,-144,-629,-663,80,1000,1000,1000,-1000,-55,-115,-53,-847,1000,1000,-3,790,176,-584,16,-347,849,482,-802,752,-127,488,907,-913,600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "equals(java.lang.Object):boolean",
            new int[]{816,316,364,501,370,-139,-88,-200,-657,-343,-1000,-1000,917,129,292,-412,1000,565,-828,-169,-889,1000,934,552,1000,422,544,1000,297,-987,375,633,-1000,-700,-663,674,-437,399,1000,547,208,-23,-789,-1000,933,-868,-45,1000,946,289,546,270,-427,-64,-124,1000,-442,602,-1000,475,1000,-1000,936,846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getAccessJavaTime():java.util.Date",
            new int[]{810,400,-176,-901,51,893,127,-1000,-42,-1000,-58,-118,-1000,668,141,321,-1000,194,-724,-777,1000,-110,1000,805,1000,-1000,5,548,-975,-388,-83,717,-1000,219,338,-69,333,633,901,-635,70,-546,-675,738,15,1000,263,932,-529,1000,296,529,-1000,-249,334,203,989,99,-229,1000,1000,1000,162,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getAccessJavaTime():java.util.Date",
            new int[]{-924,-1000,713,200,-345,1000,1000,-225,770,905,-481,550,-1000,-657,-249,-1000,1000,194,110,-1000,1000,-216,-703,-981,-79,1000,213,-862,-157,1000,-1000,-1000,1000,-665,1000,-726,-183,611,-680,363,738,1000,-250,1000,56,-116,1000,1000,-1000,-1000,296,-88,-115,202,-797,-55,-869,-507,-1000,-1000,-994,-1000,162,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getAccessJavaTime():java.util.Date",
            new int[]{532,-1000,-431,-27,-652,763,613,1000,528,-1000,-1000,659,-1000,1000,1000,68,-952,-1000,-152,76,1000,-701,284,-979,1000,-581,-516,1000,496,129,-1000,-711,-754,-1000,1000,13,1000,896,-89,-1000,865,1000,707,1000,-1000,-625,1000,-1000,-1000,679,-245,1000,-837,-734,685,-1000,-768,-1000,-1000,1000,650,1000,-341,-140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipLong", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getAccessTime():org.apache.commons.compress.archivers.zip.ZipLong",
            new int[]{147,-450,-651,-33,1000,424,-1000,495,294,218,320,388,-30,84,691,1000,632,856,156,-120,581,891,-1000,54,1000,-1000,1000,-548,723,-1000,1000,-199,1000,1000,355,1000,1000,1000,1000,700,719,1000,-739,1000,-1000,-400,283,295,739,-101,-1000,771,536,-150,324,1000,-1000,636,349,-84,51,1000,-700,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getAccessTime():org.apache.commons.compress.archivers.zip.ZipLong",
            new int[]{-1000,-1000,324,-349,517,-242,805,367,1000,374,-1000,-1000,-686,1000,-192,-847,-762,-1000,707,398,1000,-817,564,429,547,846,-298,622,568,-684,256,-1000,14,692,35,1000,-752,69,1000,-1000,883,653,-281,487,-239,1000,-473,41,-933,586,-715,-1000,749,481,519,-88,-248,-1000,-532,-628,820,-465,-208,698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getAccessTime():org.apache.commons.compress.archivers.zip.ZipLong",
            new int[]{1000,332,649,1000,780,-714,-374,-1000,-700,-117,237,-1000,352,-66,-53,198,351,688,-171,-1000,-770,1000,225,1000,576,-290,-326,-778,482,-353,535,-48,895,635,-1000,-54,646,598,-590,791,313,-926,-281,1000,-470,-1000,832,270,-97,601,-715,1000,-637,588,699,223,-665,968,124,-1000,45,676,-284,569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:Nw==:23:java.lang.Byte:LTEyOA==:19:java.lang.Byte:OTQ=:19:java.lang.Byte:Mjk=:19:java.lang.Byte:NA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCentralDirectoryData():byte[]",
            new int[]{-309,1000,-106,488,799,583,-151,-1000,658,-624,397,-234,-601,-1000,845,681,983,-1000,-399,991,132,-572,-671,-988,-1000,736,558,1000,1000,-952,51,-1000,-493,1000,-1000,-1000,27,-1000,-1000,-942,-422,1000,655,788,-1000,1000,-1000,1000,-746,-1000,627,-1000,1000,392,-1000,1000,-155,463,-930,964,596,-531,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCentralDirectoryData():byte[]",
            new int[]{1000,540,-721,865,1000,1000,-856,611,-620,675,1000,1000,-506,225,-955,-1000,75,884,-1000,308,-1000,1000,-1000,942,458,1000,0,15,-715,230,-324,-82,334,1000,446,161,-800,1000,68,262,1000,-666,765,-787,-451,-172,-120,-668,80,284,1000,1000,-921,1000,911,-983,-318,-953,-493,-851,-137,1000,0,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:NQ==:19:java.lang.Byte:MA==:19:java.lang.Byte:Mjk=:19:java.lang.Byte:NzM=:19:java.lang.Byte:Mw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCentralDirectoryData():byte[]",
            new int[]{-1000,-1000,519,-1000,-501,-50,-171,-1000,316,-247,-356,1000,-241,1000,-170,899,-346,-519,-682,348,638,247,-319,-727,173,-97,267,-286,37,-31,818,1000,-1000,-764,165,-23,-427,363,1000,328,-764,-654,-901,299,538,80,2,-7,-203,262,-343,335,570,639,516,642,349,-740,481,738,-178,818,-591,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:Mw==:23:java.lang.Byte:LTEyOA==:23:java.lang.Byte:LTEyMA==:19:java.lang.Byte:ODM=:19:java.lang.Byte:LTI=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCentralDirectoryData():byte[]",
            new int[]{-1000,-253,-422,130,-304,-1000,-30,-325,70,1000,130,159,837,400,-786,-843,-342,400,-933,-355,207,-774,-1000,-1000,400,-904,1000,422,-623,-368,872,338,-191,53,234,85,1000,972,400,1000,-485,587,-1000,-982,736,-1000,582,826,-81,111,962,1000,-121,-73,1000,812,1000,-845,-962,846,-338,71,1000,-601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCentralDirectoryLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{-510,608,-186,-269,831,-1000,702,983,-672,806,706,486,935,449,-432,336,-768,-64,782,-546,862,-867,-9,-619,-1000,-167,1000,725,385,-62,-663,-595,377,-1000,430,546,775,-821,1000,176,-383,-318,-239,-864,257,-951,-1000,-126,532,221,375,-592,-661,1000,-265,-349,-268,604,-233,523,-158,-57,-104,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCentralDirectoryLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{1000,884,-321,-458,1000,1000,162,-557,877,-1000,-1000,-1000,468,786,-560,-291,1000,301,458,29,737,-542,926,1000,-1000,-572,1000,-1000,684,-272,-607,712,-246,1000,-54,938,1000,1000,19,279,762,-1000,-23,-909,243,1000,1000,346,-1000,-1000,-66,211,-1000,174,95,1000,-235,1000,107,-959,1000,229,854,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCentralDirectoryLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{580,517,334,-408,393,-106,760,152,444,-397,-462,-68,303,544,-766,-142,657,282,440,246,678,773,-409,534,-1000,-190,438,696,583,-53,-475,-920,295,295,18,998,398,339,145,-314,319,-433,-224,-444,51,168,41,-296,-411,-59,186,-280,-385,7,343,892,-462,214,-92,-205,262,-440,468,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCreateJavaTime():java.util.Date",
            new int[]{-796,-140,-86,-997,-329,-915,-1000,286,-220,-1000,425,-172,-803,1000,840,598,-113,-639,-1000,-651,574,263,1000,-393,247,-549,824,1000,685,-112,1000,1000,1000,-1000,-454,1000,-1000,-1000,867,665,-51,-747,-1000,-1000,-56,-412,886,157,1000,640,192,570,-853,746,821,1000,694,820,-64,-682,1000,-895,506,-222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCreateJavaTime():java.util.Date",
            new int[]{489,-995,988,-346,-596,440,-1000,1000,-582,-690,1000,-1000,-1000,1000,204,1000,453,-403,342,-9,1000,-132,672,377,1000,-733,1000,1000,-236,922,1000,-37,1000,-1000,1000,-1000,-187,-525,-92,815,29,-805,-1000,-1000,-1000,124,-316,-1000,99,419,445,86,-986,649,416,1000,128,-640,42,1000,871,-360,-425,567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCreateJavaTime():java.util.Date",
            new int[]{-1000,-1000,-91,-740,-333,-127,-389,-1000,-387,-1000,636,1000,-1000,1000,-364,-844,-717,-1000,-1000,-1000,-714,613,807,199,-164,55,1000,1000,1000,1000,480,1000,-351,-1000,-1000,1000,-598,-1000,-931,35,-375,-629,-1000,-1000,1000,1000,1000,1000,1000,385,501,1000,890,1000,-294,678,297,1000,-1000,-178,844,-178,1000,706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipLong", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCreateTime():org.apache.commons.compress.archivers.zip.ZipLong",
            new int[]{-387,390,894,630,-306,674,1000,999,1000,67,-197,1000,27,-1000,168,1000,888,-241,-271,-149,-1000,-1000,251,234,-558,-1000,1000,-570,-1000,-882,147,1000,272,-1000,-1000,344,-420,-1000,176,158,1000,1000,1000,348,884,1000,-1000,-610,-836,1000,1000,757,980,-882,1000,-1000,1000,933,320,-673,-608,427,-79,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipLong", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCreateTime():org.apache.commons.compress.archivers.zip.ZipLong",
            new int[]{-301,1000,894,21,789,674,229,97,-445,-580,-465,-506,430,-966,1000,351,-353,-241,-271,-149,-814,-1000,-1000,-724,72,-582,-815,-529,-288,-945,4,1000,-827,-208,83,-47,517,-453,400,595,-356,-571,892,405,-384,734,487,755,-9,-660,-20,228,-305,-86,-1000,-590,1000,-107,218,117,1000,-857,317,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getCreateTime():org.apache.commons.compress.archivers.zip.ZipLong",
            new int[]{981,-129,-516,156,1000,-1000,-812,799,714,495,376,-739,437,-1000,-965,188,348,1000,816,425,-1000,-1000,-563,47,935,-434,-1000,973,-553,-1000,1000,-1000,-1000,-326,-444,1000,-568,89,-922,1000,-974,1000,1000,-490,-582,270,-517,592,-604,447,678,1000,-77,-262,-81,804,1000,-605,524,-341,191,-634,1000,885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Byte:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getFlags():byte",
            new int[]{-813,888,734,723,1000,-696,-1000,-95,663,-150,-1000,-93,758,1000,161,-627,-17,-195,823,-204,-1000,403,-529,980,-967,1000,-1000,-1000,-1000,1000,-1000,226,567,-1000,-1000,609,-1000,-132,378,1000,-310,-253,-284,-489,-1000,-1000,534,279,-900,522,-292,114,-1000,1000,-279,-1000,5,207,625,-1000,-1000,-361,-753,840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getFlags():byte",
            new int[]{-1000,-59,854,-311,383,-1000,-140,860,1000,-194,-1000,469,-809,1000,-1000,-97,700,-210,1000,1000,696,-431,179,1000,495,1000,-1000,-1000,-1000,-639,-509,864,757,1000,717,1000,-886,-42,519,-3,295,-1000,498,381,-1000,-481,1000,1000,439,1000,-1000,-118,-121,43,-456,-1000,477,1000,-100,-1000,276,-627,-90,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getFlags():byte",
            new int[]{936,-475,778,-712,-139,1000,-968,-1000,-1000,286,772,-149,-304,-1000,-204,428,-140,-744,-648,-1000,1000,60,225,-239,-327,-1000,1000,570,930,1000,1000,-369,668,1000,-1000,-704,658,489,626,-446,1000,1000,-249,-732,1000,-334,154,-1000,-198,-1000,781,-289,601,-400,622,773,-1000,-1000,-6,-1000,1000,79,-80,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getHeaderId():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{885,-945,-826,429,1000,-590,-1000,-675,273,1000,-899,959,839,439,976,-1000,-395,717,385,-771,100,-141,-1000,-646,367,796,-1000,-286,582,-386,1000,200,-779,829,1000,-417,-1000,-753,-1000,748,118,326,-1000,-656,-49,-1000,857,-84,1000,-1000,-536,-1000,-79,1000,825,1000,-967,-1000,-801,-1000,-244,1000,-42,425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getHeaderId():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{1000,-593,-411,455,-531,-1000,-1000,1000,395,-423,-282,-245,140,1000,-681,267,906,-1000,-216,729,668,-111,1000,-1000,1000,1000,-922,-817,965,-939,-1000,804,953,594,1000,-288,-748,1000,1000,1000,136,-408,-854,357,-465,-191,124,673,450,-568,-304,819,-662,-1000,1000,-1000,683,256,-1000,-140,-1000,296,523,285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getHeaderId():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{1000,-303,-531,1000,-1000,-1000,-979,1000,318,83,-423,508,-158,805,-760,878,653,-493,202,-1000,-449,575,-945,-1000,403,61,-659,-817,801,-1000,-1000,-378,-290,971,446,-404,-748,714,195,790,1000,-610,-484,-619,-93,-720,1000,1000,317,-125,-622,330,-380,-932,628,-164,254,-272,-924,54,-57,392,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("ARRAY:[B:13:19:java.lang.Byte:Nw==:23:java.lang.Byte:LTEyOA==:19:java.lang.Byte:NDY=:19:java.lang.Byte:MTA=:19:java.lang.Byte:LTQ=:19:java.lang.Byte:MA==:19:java.lang.Byte:LTYy:23:java.lang.Byte:LTEwMA==:19:java.lang.Byte:LTU=:19:java.lang.Byte:MA==:19:java.lang.Byte:LTky:19:java.lang.Byte:LTM5:19:java.lang.Byte:LTY=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getLocalFileDataData():byte[]",
            new int[]{1000,-47,-411,191,-1000,-86,-769,-682,-1000,-1000,-175,-852,738,1000,-697,723,1000,-103,1000,-484,-647,644,-1000,595,188,-245,-39,-1000,191,-1000,102,689,1000,-559,-836,-52,-841,930,1000,307,242,-146,-1000,1000,-817,532,-910,-1000,1000,476,-1000,-1000,-363,-1000,1000,-111,923,1000,353,1000,269,126,798,728}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getLocalFileDataData():byte[]",
            new int[]{544,763,-511,-24,507,510,-553,-10,-1000,-605,895,253,535,-1000,-37,1000,1000,-1000,-38,-1000,830,-1000,-470,992,-211,1000,523,-1000,-317,-1000,-225,371,1000,-233,-1000,-1000,827,878,-962,-1000,244,-1000,-275,-768,1000,-308,930,-63,422,308,460,1000,586,179,-735,-605,-419,239,-379,683,-790,-629,-106,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getLocalFileDataData():byte[]",
            new int[]{255,-734,-486,1000,-504,-60,-214,239,-454,-1000,1000,-192,575,-952,1000,403,873,-214,1000,655,-1000,-1000,-1000,493,-1000,-38,-252,-1000,1000,-1000,-131,-35,376,-1000,-1000,-711,-405,775,-1000,-1000,276,-1000,-251,275,-286,55,-83,-1000,1000,-501,-1000,-381,688,-1000,1000,-66,706,1000,-604,835,378,234,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getLocalFileDataLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{943,50,-241,-439,-812,-1000,142,406,763,-569,-1000,970,571,1000,-904,567,520,-1000,141,-1000,-146,1000,514,-33,28,-1000,105,1000,1000,-1000,563,-890,1000,-100,198,-813,1000,-1000,-118,468,-1000,1000,496,-74,-838,-1000,-1000,-81,-663,656,-1000,952,1000,-1000,131,-124,1000,1000,531,450,1000,-1000,-322,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getLocalFileDataLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{-97,356,-456,-1000,1000,-126,924,-665,641,-1000,-967,-179,-1000,914,-1000,-115,-682,399,-815,1000,473,155,969,-73,-325,-171,155,-381,-113,195,-1000,282,-863,228,863,1000,-806,-1000,-947,-694,-1000,996,-851,56,1000,-586,284,-1000,-1000,-1000,856,542,-401,-1000,1000,1000,-1000,64,-814,-25,-1000,750,737,-967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getLocalFileDataLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{-1000,347,454,1000,-117,532,1000,-370,-201,-1000,1000,-377,226,1000,-1000,-968,-773,985,-866,929,-516,-117,851,919,739,272,160,-610,-1000,1000,-215,-498,-527,16,-33,254,231,248,1000,-689,130,-1000,-288,142,926,43,517,-1000,-239,-613,-588,268,-713,466,-290,-1000,-162,777,-103,-593,1000,186,217,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipShort", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getLocalFileDataLength():org.apache.commons.compress.archivers.zip.ZipShort",
            new int[]{-1000,-281,-371,988,992,83,728,-309,16,-789,751,-1000,430,994,-842,-582,-1000,1000,-800,1000,884,-574,631,907,1000,779,-286,-653,-1000,767,554,-16,-1000,458,-1000,1000,-579,-468,-152,157,446,-809,-202,-484,599,512,1000,-966,-690,-129,-389,118,-436,-196,-564,-889,-670,777,-454,-1000,1000,1000,129,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getModifyJavaTime():java.util.Date",
            new int[]{767,854,564,747,527,-383,-264,-707,537,-297,604,-1000,383,-536,67,-331,412,-593,1000,65,-1000,1000,5,-138,-209,592,119,398,-92,-966,-650,1000,858,659,-818,-19,-631,-375,-267,299,187,951,694,272,-71,-1000,768,194,509,-301,-319,-410,127,275,-759,-394,-828,-597,41,491,-1000,839,162,512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getModifyJavaTime():java.util.Date",
            new int[]{1000,-1000,634,474,1000,855,656,-257,-1000,1000,-318,-1000,1000,272,1000,-1000,1000,-82,894,473,-1000,-1000,-228,-297,-1000,-900,-998,1000,-1000,304,842,613,1000,209,-1000,1000,1000,-1000,-1000,586,-1000,-35,1000,1000,-1000,1000,558,-25,1000,-1000,-1000,378,-393,-196,1000,-73,942,-1000,667,1000,-1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getModifyJavaTime():java.util.Date",
            new int[]{-747,-1000,809,-718,88,645,183,-265,765,269,-1000,767,-663,1000,-668,1000,-566,1000,-791,527,1000,-1000,564,-477,-1000,1000,111,-545,-539,-1000,-1000,-1000,644,-401,-768,634,-336,-285,-1000,-243,1000,-1000,738,-1000,-837,-1000,-1000,-194,-1000,12,682,266,685,60,-545,-1000,-1000,-1000,-1000,932,-444,331,-490,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getModifyTime():org.apache.commons.compress.archivers.zip.ZipLong",
            new int[]{1000,1000,-91,-843,962,765,114,150,1000,1000,-748,1000,-839,1000,-1000,672,-416,-93,-962,1000,1000,585,413,335,359,-858,-1000,-154,-321,-677,-417,-671,-1000,1000,-1000,1000,-794,-463,840,-331,646,318,-708,1000,1000,687,-1000,1000,318,-7,-610,1000,-814,541,-1000,1000,1000,230,902,1000,1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipLong", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getModifyTime():org.apache.commons.compress.archivers.zip.ZipLong",
            new int[]{1000,-339,29,-179,-1000,430,1000,-249,-764,460,415,349,-1000,-1000,535,-1000,-881,691,-23,-374,424,-1000,13,574,1000,-394,-312,-565,629,505,389,-815,-525,-607,337,230,582,319,-388,469,-1000,1000,-355,877,-1000,1000,-221,531,1000,-799,495,719,836,-1000,-764,-211,172,-510,380,919,-580,164,-99,154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipLong", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "getModifyTime():org.apache.commons.compress.archivers.zip.ZipLong",
            new int[]{1000,1000,-506,-417,-839,1000,-1000,459,934,1000,-20,375,1000,1000,1000,-1000,1000,-442,-499,331,-211,-811,1000,964,-516,-1000,-1000,1000,1000,-520,741,1000,-711,747,-124,1000,563,860,630,-924,-1000,-797,-7,450,563,161,1000,-1000,714,-1000,1000,496,701,-304,-6,-461,1000,906,731,927,378,264,165,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "isBit0_modifyTimePresent():boolean",
            new int[]{663,-197,914,691,-1000,302,-1000,1000,-404,15,1000,-643,-1000,-1000,45,605,-964,-590,921,-781,-56,513,412,-1000,-219,936,-1000,1000,645,1000,-946,93,1000,529,969,-32,-1000,1000,1000,245,402,-516,-1000,875,-1000,1000,-1000,278,375,-1000,455,-452,-264,-988,1000,442,-363,562,1000,-556,1000,1000,355,726}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "isBit0_modifyTimePresent():boolean",
            new int[]{1000,1000,439,-451,-884,389,-1000,1000,572,639,-400,-640,959,252,1000,605,-773,-548,-451,-670,793,642,399,1000,1000,-1000,-827,-1000,-1000,355,1000,-1000,1000,1000,969,-233,1000,-1000,1000,-886,402,-814,-856,975,1000,-1000,1000,321,996,-278,-175,-1000,-539,-1000,-1000,442,-1000,376,578,-351,-1000,-360,685,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "isBit0_modifyTimePresent():boolean",
            new int[]{427,295,414,-117,-695,595,-390,606,-40,-367,1000,-416,-508,-57,988,697,-1000,-441,640,-915,588,927,748,1000,627,241,-1000,270,-403,521,-55,-235,769,961,958,-1000,593,304,90,-753,-192,136,-269,887,201,-1000,321,225,418,-920,467,-607,517,-849,-355,-845,-688,590,1000,-12,-283,405,278,808}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "isBit1_accessTimePresent():boolean",
            new int[]{-448,-378,-441,135,507,-191,859,64,671,-119,-488,-720,439,26,116,838,774,-742,834,38,-145,55,701,-627,412,20,-226,-167,-302,344,748,-946,824,888,-410,-704,-194,-918,-484,997,-921,522,-790,732,-544,-867,549,-174,560,77,-674,-454,612,-798,444,873,115,762,896,-853,455,381,-538,558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "isBit1_accessTimePresent():boolean",
            new int[]{32,273,769,-574,-1000,1000,627,-725,-122,-123,186,-514,549,985,943,231,123,170,255,419,101,-34,-178,-1000,-371,29,-524,65,-1000,957,-253,998,785,208,585,-229,1000,65,-165,530,-1000,166,857,-384,-550,609,-343,1000,183,598,1000,-1000,424,233,1000,1000,346,1000,1000,500,539,-970,143,167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "isBit1_accessTimePresent():boolean",
            new int[]{820,1000,164,413,-46,-11,855,1000,-650,741,1000,1000,185,609,-594,-1000,1000,35,-232,1000,-1000,1000,84,-361,-106,-38,-22,777,-158,-1000,-818,-720,-118,-718,436,1000,-396,1000,884,-425,-218,-159,-817,230,403,-1000,-1000,1000,208,-1000,1000,-914,-696,622,-1000,355,-1000,-1000,1000,554,-494,1000,316,619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "isBit2_createTimePresent():boolean",
            new int[]{899,648,-886,404,-172,-572,386,-864,478,-421,-714,724,-227,-371,-474,601,257,880,-74,-1000,-192,-164,-236,724,1000,-598,921,-202,122,1000,-1000,-243,-641,-1000,-395,670,608,993,-504,-327,-720,-1000,194,-544,1000,-1000,-435,-175,-1000,-767,-228,-113,121,31,277,-905,289,-733,-1000,1000,399,-1000,788,-365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "isBit2_createTimePresent():boolean",
            new int[]{-1000,852,-641,424,745,-1000,-871,-194,-224,535,1000,807,-78,1000,133,359,-357,400,-687,-85,57,-722,-1000,984,-1000,345,974,998,294,-228,748,-177,1000,759,-159,423,558,205,1000,-467,-627,-287,-1000,613,-112,-1000,1000,1000,131,-840,85,1000,557,-1000,452,-271,-529,-374,412,-358,-444,566,-477,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "isBit2_createTimePresent():boolean",
            new int[]{559,-24,504,-697,-186,385,1000,-591,408,289,-632,146,-1000,-1000,-1000,183,148,925,256,-257,-306,-88,674,1000,756,-902,658,-1000,-119,866,-79,1000,-859,-1000,278,387,1000,1000,-1000,-1000,-1000,-91,-1000,-1000,1000,409,-238,-698,-1000,-1000,-691,188,-191,-209,58,-684,-1000,331,-697,874,117,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "parseFromCentralDirectoryData(byte[],int,int):void",
            new int[]{712,405,-761,-562,-92,-223,806,-242,-378,-68,852,480,-738,-205,-620,-441,-62,583,9,367,-6,219,-700,177,-36,-700,936,709,132,-599,-267,-30,-124,415,-515,842,497,617,-818,109,330,-804,307,-38,592,96,-445,421,647,196,625,-456,152,90,-499,829,877,20,54,-197,932,884,703,396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "parseFromCentralDirectoryData(byte[],int,int):void",
            new int[]{-903,689,-632,329,190,-242,1000,493,-1000,526,29,-515,535,-880,-865,-473,245,368,985,699,-712,-297,-1000,-1000,822,-1000,-521,1000,653,-773,-635,-497,-369,1000,-1000,636,-222,-191,-166,-297,638,-1000,354,-739,372,1000,-1000,-51,1000,331,53,-228,-1000,962,-983,1000,631,-159,624,1000,512,1000,982,-920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "parseFromCentralDirectoryData(byte[],int,int):void",
            new int[]{-898,-1000,-51,-1000,-659,-1000,-1000,1000,735,-900,-854,-1000,-881,787,905,-239,1000,1000,-1000,-1000,-633,-387,36,-6,-961,717,605,1000,884,1000,-1000,1000,1000,-85,-1000,-238,1000,-1000,-1000,-852,1000,288,-1000,159,-558,-579,1000,1000,-1000,430,-54,1000,1000,-974,1000,-24,-1000,-495,-316,-1000,-1000,379,799,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "parseFromCentralDirectoryData(byte[],int,int):void",
            new int[]{268,555,-758,-1000,-1000,-1000,220,168,342,168,1000,-206,-1000,1000,-925,48,-950,563,-1000,-1000,-148,-1000,1000,858,1000,81,991,1000,-1000,11,1000,993,538,-1000,1000,-117,1000,-1000,1000,1000,-826,1000,-200,-188,-38,-1000,1000,-213,-194,564,-179,-284,1000,-1000,200,65,-1000,-1000,552,-816,-584,-1000,-810,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "parseFromLocalFileData(byte[],int,int):void",
            new int[]{711,624,-321,-180,-1000,-1000,22,1000,1000,302,-856,-743,-459,-421,355,1000,-1000,1000,-1000,371,-1000,254,-35,458,-474,-1000,-799,1000,450,646,-30,-661,978,-195,-1000,-466,1000,636,158,-1000,1000,-848,475,892,-553,-400,-370,-819,919,-1000,-1000,-847,8,1000,-238,400,-925,317,374,232,67,73,-1000,-353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "parseFromLocalFileData(byte[],int,int):void",
            new int[]{-776,-159,768,-822,1000,-432,1000,-1000,402,833,1000,-358,1000,1000,-431,-684,1000,-173,1000,-240,588,-898,-1000,1000,1000,1000,-13,-1000,-1000,-356,-1000,1000,-1000,-585,67,1000,-1000,-1000,-654,1000,-1000,-1000,-1000,-1000,1000,1000,920,-1000,-766,1000,1000,1000,1000,-1000,429,-728,1000,-1000,-1000,181,1000,-172,1000,427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "parseFromLocalFileData(byte[],int,int):void",
            new int[]{498,532,9,-542,-612,-909,-645,350,586,4,493,-969,785,-115,-183,449,-366,-704,129,-316,-183,-772,-415,-953,628,817,244,185,-510,46,-351,-512,-48,1000,-654,-223,-375,-597,-128,522,517,402,-470,150,-470,393,-1000,-470,-1000,766,133,154,900,-843,1000,-685,-1000,-793,735,904,-43,849,-246,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "parseFromLocalFileData(byte[],int,int):void",
            new int[]{481,90,124,294,-715,-671,730,811,701,46,-431,-1000,327,-905,53,760,-1000,1000,-1000,594,-455,770,1000,-153,-679,-1000,-246,535,926,-388,9,-314,1000,-585,-899,-1000,275,1000,-216,-256,1000,-802,273,1000,261,-1000,-414,107,741,-786,-729,-538,432,591,370,1000,-808,89,-9,-171,-3,-115,36,-181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "parseFromLocalFileData(byte[],int,int):void",
            new int[]{-359,-724,449,-31,1000,-217,1000,-694,605,283,1000,597,829,1000,274,-1000,1000,-169,1000,592,1000,-311,-1000,1000,121,568,700,-901,-1000,-1000,-1000,1000,-809,-1000,899,996,-1000,-1000,509,1000,-1000,750,-312,-1000,1000,1000,1000,-658,-1000,1000,810,310,-1000,791,-1000,-659,1000,-1000,-1000,-442,1000,-1000,643,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "parseFromLocalFileData(byte[],int,int):void",
            new int[]{477,-116,-568,-568,-1000,-761,-676,371,739,-182,12,-1000,498,335,55,795,-585,-540,-1000,147,-1000,-36,720,-1000,-382,529,-759,648,-1000,-751,-682,361,-655,693,-371,177,-184,-819,-331,1000,1000,236,-407,1000,-322,96,-1000,371,-792,-109,-164,664,516,-685,36,-832,-123,572,-310,186,1000,126,-790,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setAccessJavaTime(java.util.Date):void",
            new int[]{-734,667,-896,171,1000,-455,-1000,-411,214,871,-459,445,-289,-400,1000,621,-651,394,-85,357,-896,-461,-331,-455,-323,426,-873,177,-276,1000,-290,472,-614,-610,-1000,-496,1000,578,-833,1000,668,400,779,-631,-423,-982,-619,246,776,-556,-82,575,-506,1000,-663,-617,-231,-462,-184,-1000,302,334,-400,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setAccessJavaTime(java.util.Date):void",
            new int[]{442,-35,-451,901,691,-1000,-800,-1000,1000,-179,685,642,695,-355,1000,-669,-593,-379,-308,186,-1000,-612,677,269,-261,1000,-642,-1000,731,313,-735,882,782,521,-1000,-52,1000,237,-70,-426,729,842,644,218,-377,1000,497,126,1000,-1000,-1000,1000,-641,1000,-1000,-1000,256,1000,-1000,-999,1000,1000,-517,-426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setAccessTime(org.apache.commons.compress.archivers.zip.ZipLong):void",
            new int[]{1000,-617,-421,-808,-129,-289,-236,-204,292,86,-112,-786,-729,1000,582,-1000,-22,-537,-659,-814,-822,1000,-452,699,116,386,-362,-1000,468,288,-1000,-846,-288,287,602,1000,-886,-464,170,904,59,441,1000,-793,516,98,882,1000,127,-230,-198,136,107,-496,470,-1000,-1000,-1000,97,-267,-118,445,-282,-119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setAccessTime(org.apache.commons.compress.archivers.zip.ZipLong):void",
            new int[]{1000,529,-911,-311,-185,-845,-58,-95,-249,539,-178,-786,-306,837,-76,-1000,345,-701,-1000,-1000,1000,881,-550,807,383,386,-232,-1000,793,287,-592,-429,-476,80,854,-1000,-1000,-596,464,1000,556,-1000,585,-843,676,243,836,945,-158,-641,-4,-273,-590,-466,1000,-834,300,-662,-192,125,364,653,-220,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setCreateJavaTime(java.util.Date):void",
            new int[]{703,-76,-726,-661,716,323,-275,83,-962,-270,437,273,-161,105,788,-605,801,148,-544,-990,-878,-148,331,-885,-108,580,31,-43,-463,440,-217,340,551,-732,-955,282,-59,-848,453,790,517,-906,-580,450,-566,156,-981,-425,240,213,-321,-288,-880,-671,-357,-532,943,-339,153,800,316,-192,883,-792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setCreateJavaTime(java.util.Date):void",
            new int[]{-1000,-656,869,-113,50,-415,140,-389,-1000,908,-427,-492,-430,175,-902,1000,129,130,943,1000,-90,1000,-807,501,567,484,-211,149,1000,-337,165,-699,-393,693,1000,-796,690,1000,-1000,-667,-360,-29,37,-1000,910,191,-544,-1000,640,-155,609,940,794,376,813,1000,-680,276,-139,-355,-766,256,1000,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setCreateJavaTime(java.util.Date):void",
            new int[]{1000,-109,-686,-661,175,-1000,-275,-77,-210,93,-162,683,-60,-77,1000,-1000,-15,-238,844,-835,-25,816,-1000,1000,-438,580,662,-912,-663,-127,-1000,1000,-940,-732,1000,-1000,1000,1000,1000,1000,-393,266,952,1000,-555,156,1000,1000,-137,678,-410,-470,-433,1000,-972,-532,-316,-212,-344,873,560,-430,-981,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setCreateTime(org.apache.commons.compress.archivers.zip.ZipLong):void",
            new int[]{422,-998,74,-66,474,927,-761,418,868,-776,566,631,56,-569,247,-315,-225,563,169,291,146,959,788,-855,-203,-477,13,903,-237,472,-135,-911,-46,-155,-993,968,119,-308,-361,-388,-622,-854,-297,-805,-343,362,-133,876,627,-282,-679,550,-840,579,-918,216,675,-547,198,-259,-705,415,-997,-602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setCreateTime(org.apache.commons.compress.archivers.zip.ZipLong):void",
            new int[]{-415,759,363,-1000,-905,-624,-1000,90,873,1000,389,-1000,-222,569,-534,-182,6,-263,632,-616,-138,-841,-813,-107,20,1000,26,-856,-698,363,577,580,-597,-1000,-288,-852,325,468,1000,-436,-1000,1000,8,-90,-796,-170,1000,-530,-999,828,-552,38,-737,-808,1000,-806,-929,-324,1000,1000,167,778,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("VOID|getFlags=java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setFlags(byte):void",
            new int[]{-912,-1000,414,74,732,-727,387,-289,446,-56,204,123,-686,359,-356,-1000,-1000,-64,-538,68,200,-176,-1000,-665,-480,-306,-72,624,-17,1000,918,25,-1000,-410,1000,-229,-1000,-1000,475,-759,-91,-237,-1000,-530,-388,457,-667,1000,813,-686,-523,-335,891,1000,94,-298,353,1000,-565,86,-453,-498,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("VOID|getFlags=java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setFlags(byte):void",
            new int[]{958,400,-931,415,825,175,-649,426,627,338,547,-257,-928,-689,855,300,-641,-296,-204,433,176,893,-1000,-745,-631,400,-1000,-391,-178,27,-1000,-1000,-194,-1000,-425,-689,691,-621,-299,837,-301,162,-556,1000,87,-442,-350,-867,1000,-399,-1000,-600,-603,1000,930,-702,-1000,813,-131,-1000,461,-1000,-1000,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setModifyJavaTime(java.util.Date):void",
            new int[]{183,225,299,-992,469,-55,-308,80,258,265,476,-337,109,-118,826,602,135,163,683,400,-786,-1000,0,565,-409,-28,222,177,388,493,760,-1000,416,-585,597,-377,-717,-1000,-513,871,-776,-416,946,753,1000,224,113,247,-425,-874,-170,-1000,-84,409,1000,931,4,-94,354,-700,-854,-897,319,-700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setModifyJavaTime(java.util.Date):void",
            new int[]{320,896,329,101,-1000,447,256,-456,-321,317,-876,-492,-960,-481,-351,937,-845,150,-117,16,-223,-857,1000,498,155,-591,1000,-703,39,-276,-657,1000,-746,-1000,-899,1000,-1000,-307,694,594,295,-1000,-1000,475,-225,123,206,52,77,752,-288,-435,-972,-262,-127,-1000,91,527,-168,-135,-164,-80,-525,-809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setModifyJavaTime(java.util.Date):void",
            new int[]{-1000,-1000,-426,-346,1000,-1000,-468,-1000,-506,-889,646,-1000,-938,-1000,555,-193,587,-1000,1000,-401,39,-1000,-780,428,532,332,-14,965,-23,-1000,-627,-801,436,-122,1000,-215,-1000,-706,-1000,1000,-1000,-1000,1000,91,347,219,574,-307,-1000,25,382,1000,72,761,624,752,-428,-654,312,1000,-786,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setModifyTime(org.apache.commons.compress.archivers.zip.ZipLong):void",
            new int[]{-918,452,-151,-928,818,-416,52,140,-93,586,-637,-878,-458,-239,323,932,668,136,-136,535,-826,289,-245,-301,498,285,-439,210,802,-371,464,944,904,378,843,-830,-292,789,-407,514,6,-762,830,-890,-801,527,-155,-336,761,-862,959,366,-932,536,-730,-94,592,188,187,-807,396,600,-859,812}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setModifyTime(org.apache.commons.compress.archivers.zip.ZipLong):void",
            new int[]{-939,286,-71,-1000,1000,145,-648,621,-52,723,1000,-404,-1000,409,1000,-365,-258,-930,-237,1000,-1000,-1000,-965,1000,-1000,-700,-293,815,-1000,298,202,350,458,-979,1000,-1000,1000,-332,1000,-638,-133,161,-563,-122,-341,-1000,-115,-559,241,-430,545,94,-421,275,888,-1000,1000,1000,-1000,-504,-1000,-1000,1000,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "setModifyTime(org.apache.commons.compress.archivers.zip.ZipLong):void",
            new int[]{-145,-87,544,124,103,-234,-207,-550,-600,1000,-412,289,503,451,442,335,695,-454,-545,-673,-472,-167,-733,70,-88,-234,247,225,-304,242,449,472,485,597,419,-507,114,1000,-397,563,620,-570,-128,-454,-1000,441,-570,-1000,1000,102,271,1000,-334,-303,-777,528,156,404,-514,-797,676,107,-791,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.String:MHg1NDU1IFppcCBFeHRyYSBGaWVsZDogRmxhZ3M9MCA=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "toString():java.lang.String",
            new int[]{-1000,-1000,-586,1000,-1000,-687,1000,-954,1000,413,-204,1000,-622,-696,379,1000,248,345,1000,255,1000,739,1000,-1000,-1000,-1000,-1000,-707,1000,639,346,-198,1000,819,-1000,933,-103,-1000,-1000,88,95,440,-675,268,-1000,825,1000,-78,-1000,1000,-1000,-1000,250,201,-1000,1000,-1000,-571,-92,77,483,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.String:MHg1NDU1IFppcCBFeHRyYSBGaWVsZDogRmxhZ3M9MTExMTEwMSA=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "toString():java.lang.String",
            new int[]{-1000,-952,-603,211,-821,-106,-1000,-755,-37,1000,1000,1000,648,125,-361,1000,295,-110,390,78,1000,1000,1000,-1000,-1000,-1000,-1000,-1000,1000,858,377,-804,-764,405,-1000,-197,-1000,-691,-1000,1000,-701,849,-1000,1000,-821,1000,1000,600,-758,1000,449,400,-917,550,-1000,1000,-1000,440,400,-254,-478,-289,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "org.apache.commons.compress.archivers.zip.X5455_ExtendedTimestamp", "toString():java.lang.String",
            new int[]{236,1000,129,-1000,366,-61,-1000,102,-1000,20,12,578,807,-419,-696,719,-1000,-1000,166,155,-654,554,-1000,-478,1000,1000,850,-132,699,-149,689,1000,-386,-1000,-711,-1000,-160,1000,783,88,-304,-1000,497,-529,49,-373,-258,855,91,-216,1000,-411,-197,669,-53,-987,608,360,158,-64,-316,643,290,322}));
    }
}
