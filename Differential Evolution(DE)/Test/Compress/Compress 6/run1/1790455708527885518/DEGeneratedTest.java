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
            new int[]{93,-53,795,-1000,-252,-1000,-242,190,-852,788,-1000,854,187,-107,1000,759,-567,-993,-394,-1000,-1000,-190,-1000,681,-1000,1000,417,-512,-1000,227,-1000,-816,-1000,400,677,-1000,-795,-1000,360,1000,1000,-898,-1000,-1000,188,245,974,-499,-1000,-513,1000,-548,-1000,534,-1000,-900,886,1000,-1000,1000,793,225,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-840,1000,1000,671,-254,-1000,-36,-246,-739,-94,-1000,827,1000,-148,413,697,225,-86,166,183,-717,54,226,175,7,1000,789,446,-637,166,-1000,1000,-1000,774,-93,283,-546,-562,651,346,488,-1000,-1000,686,-29,-999,-720,194,-1000,533,862,-517,-755,240,695,487,124,1000,998,230,713,105,1000,576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{358,-521,1000,-1000,-612,-1000,-329,923,-843,238,-1000,19,-226,951,925,262,-39,1000,409,-851,-103,382,-70,-1000,-1000,1000,207,390,1000,-1000,-1000,-1000,-1000,-1000,631,141,417,306,316,1000,-302,-97,-833,-602,-1000,-1000,974,-384,-1000,-388,747,-538,-42,-497,888,-500,348,-200,-1000,359,710,-376,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{742,-1000,436,516,507,-816,679,191,1000,-1000,940,-1000,-30,320,1000,1000,964,1000,-1000,1000,1000,-1000,741,739,1000,520,918,1000,98,306,858,1,877,-1000,-781,1000,505,-1000,-1000,-1000,-1000,-264,-1000,1000,-1000,1000,-600,1000,550,1000,601,-1000,191,566,-1000,12,1000,-508,185,-356,1000,-1000,-1000,-692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-543,-581,-229,848,-799,-534,776,-697,301,-387,811,-873,906,-627,-346,537,195,201,385,51,731,-681,100,1000,-272,262,1000,204,-254,1000,155,58,790,-400,-530,314,-426,633,-950,973,95,-739,-1000,892,-1000,1000,-144,222,959,157,29,-585,258,361,-837,626,847,-789,-848,-328,568,371,-7,423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{1000,-73,-1000,-157,-1000,-8,-950,1000,1000,608,-77,1000,1000,-47,-714,84,1000,-1000,992,1000,-1000,412,-73,-1000,385,-840,1000,-646,-1000,-141,649,1000,659,-1000,1000,-704,-1000,-4,500,1000,-845,-1000,87,-1000,-1000,-881,-901,19,-726,-266,-426,-26,-248,-441,767,-1000,1000,1000,-1000,795,893,-962,-1000,-915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addAsFirstExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{1000,624,-808,-876,1000,-1000,478,148,-256,-482,101,-1000,-892,902,994,1000,-526,1000,-1000,-1000,88,108,9,1000,-797,1000,364,-248,746,-1000,-3,1000,-29,632,-1000,-507,198,-1000,22,802,149,684,-1000,-982,1000,616,1000,-235,44,584,1000,-1000,-642,1000,-1000,-243,267,-534,1000,1000,377,-451,872,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-314,339,-1000,-1000,287,-829,-822,-1000,1000,1000,-225,-288,493,212,-476,402,-1000,309,-1000,1000,51,-774,-547,-400,1000,-1000,1000,-1000,-1000,-811,-153,1000,-140,1000,1000,1000,-975,-623,362,-1000,-324,1000,-1000,-753,-1000,504,-1000,1000,-1000,-989,1000,1000,747,-915,814,1000,-416,-1000,-296,-829,96,701,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-165,-600,1000,-614,-141,-19,163,-573,272,555,428,-192,-618,1000,818,-765,1000,-329,1000,-857,-570,-456,-218,1000,1000,-458,-1000,-354,-1000,1000,-279,-1000,-783,-1000,464,412,-253,400,-178,-57,-708,68,531,-138,-268,721,1000,1000,-285,-511,329,-275,291,-1000,-810,-510,-307,-567,-669,-217,400,-1000,212,-894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-314,339,-1000,-1000,-500,-830,-675,-1000,507,-1000,-111,-662,724,-326,-935,-510,1000,763,-583,526,-256,-148,-276,-385,342,-1000,-861,-1000,-594,1000,-820,-592,1000,1000,1000,-920,-698,-697,1000,-1000,123,-78,-1000,-385,1000,830,-580,118,-1000,-1000,-84,764,428,252,150,680,-737,-1000,72,-1000,692,216,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-1000,1000,916,19,112,413,427,-236,77,-1000,344,-1000,798,283,67,-726,-19,-122,325,-109,-206,421,976,46,97,-178,241,86,-1000,428,-854,-692,168,452,626,-613,391,-1000,546,-1000,-247,435,-244,-1000,1000,485,-400,1000,-873,372,798,1000,-708,-526,787,-1000,-649,-711,40,-84,1000,-416,-335,305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{1000,-605,1000,-1000,-500,-180,-419,-291,500,4,-429,-380,-1000,-371,237,-731,1000,-1000,1000,-1000,290,-148,-1000,814,-149,183,-1000,-134,1000,1000,468,-592,-871,226,1000,400,-480,1000,1000,379,221,-78,108,-756,-683,719,1000,118,-158,-512,-338,112,428,202,-1000,674,360,-93,-1000,-706,-494,358,834,-924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-314,1000,1000,1000,1000,-18,649,-358,-742,-1000,801,-1000,1000,813,528,-1000,-720,-29,362,667,179,976,993,-891,1000,-891,1000,-503,-1000,-835,-1000,-297,539,626,-778,-729,616,-1000,-282,-602,-1000,1000,-471,-632,1000,54,-900,948,-745,896,345,-151,-1000,-667,1000,-1000,-714,-878,651,-1000,1000,-517,1000,48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{880,-504,140,-1000,-365,-348,33,-1000,883,-308,994,-1000,756,358,795,-468,142,8,124,-1000,199,-736,-692,834,1000,-812,-330,-1000,-1000,865,-1000,228,539,1000,1000,423,-942,-1000,1000,-707,-400,697,531,-1000,-480,812,-526,1000,-1000,-1000,345,913,1000,-468,-498,870,-1000,-637,-1000,-1000,515,-295,1000,-238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{400,-124,1000,-1000,-216,-102,468,-628,476,-297,972,-1000,266,289,815,-910,909,-329,1000,-1000,76,-288,-183,850,1000,-165,-227,-119,-1000,1000,-1000,-1000,539,745,1000,367,-318,400,1000,-522,-441,541,531,-1000,-178,812,243,1000,-869,-643,345,517,291,-429,-331,-306,-1000,-567,-841,-993,400,-1000,817,-740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "addExtraField(org.apache.commons.compress.archivers.zip.ZipExtraField):void",
            new int[]{-1000,883,60,19,9,-537,-180,138,-74,-117,-22,-572,-311,9,-972,-651,635,519,668,563,-791,1000,452,59,-1000,-76,-626,298,29,-686,35,-850,1000,-470,-17,-1000,186,-999,94,-735,176,194,-1000,235,843,1000,518,1000,-467,350,1000,197,-692,-944,259,-1000,54,-900,255,42,238,-1000,-1000,283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{637,-793,284,9,-804,-221,-11,395,-588,-298,571,99,458,340,706,687,284,836,662,-167,865,-512,1000,94,-1000,187,529,-380,-90,-45,-156,-262,-1000,-171,320,-1000,243,834,-306,420,210,348,-767,781,-740,-408,100,-129,-1000,1000,650,-385,741,60,443,920,103,-91,96,-207,96,243,219,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{6,-453,-884,873,577,269,225,1000,8,382,102,417,-263,-98,-681,177,58,1000,-626,-207,312,-858,66,289,-652,-1000,495,260,-764,-872,746,-1000,-349,149,303,723,-1000,609,1000,-785,-824,-170,-1000,-645,170,-424,-133,-32,130,632,797,100,290,-426,-139,-1,-54,530,-1000,-1000,204,-484,176,358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{899,131,914,494,826,-467,-212,222,259,-566,505,-124,150,358,797,877,518,-682,841,337,662,-56,-587,605,-321,368,-880,-922,-263,-457,888,-737,471,875,-42,657,-416,-502,-466,-84,311,372,-95,995,705,486,399,-526,921,-133,830,96,623,864,236,746,-564,-784,-335,499,972,-215,511,190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-631,575,1000,-1000,-352,-593,440,730,32,-575,779,-586,491,-1000,346,798,-873,-1000,576,775,455,1000,415,302,806,71,1000,-874,542,1000,-228,177,-69,-1000,-955,-1000,619,742,-734,19,-1000,128,20,-286,-45,-219,-1000,-160,-944,-791,930,-513,1000,-222,-86,227,770,888,668,93,438,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{-1000,-1000,1000,1000,-682,-245,-868,-727,284,-964,674,-278,-382,125,207,-932,257,166,390,-444,1000,-368,1000,385,131,-988,632,31,-932,355,-572,157,-472,-55,-4,302,301,807,487,-417,53,-929,170,1000,-1000,-461,397,-479,-235,1000,-255,-80,-968,219,-540,1000,-171,94,436,-188,841,100,-131,-152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.compress.archivers.zip.ZipArchiveEntry", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "clone():java.lang.Object",
            new int[]{637,1000,1000,-1000,876,-1000,267,988,-873,-399,1000,-641,523,-970,-9,687,-825,-1000,1000,573,242,1000,-526,1000,884,-231,318,-1000,354,1000,-898,573,785,-1000,-97,-1000,917,346,-26,1000,-1000,1000,613,-187,-740,368,-768,86,-1000,-1000,528,-385,741,1000,443,-441,603,271,96,800,755,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-663,-377,1000,1000,-561,858,-929,617,246,-823,2,369,-390,-737,623,513,-296,969,894,784,-632,900,-818,-280,-400,-234,470,-574,-342,167,-186,708,-345,124,-64,367,-1000,-1000,1000,0,1000,980,186,-1000,42,-1000,-676,-329,-1000,41,828,927,-19,-611,464,-943,389,-161,185,-293,1000,738,-350,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{1000,-840,-65,-233,-102,-269,737,761,419,584,-208,395,583,570,238,-101,636,-1000,-169,610,-287,-83,-155,128,-673,-374,370,469,-32,1000,-690,198,359,810,988,-186,1000,-687,-863,645,-729,919,-604,1000,-133,1000,-111,-90,-1000,38,582,-295,505,255,-744,619,-45,507,-173,550,802,-258,193,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-663,1000,794,879,-561,858,-510,-552,1000,-758,1,699,3,-1000,-1000,515,-1000,-450,894,-290,827,1000,-376,-95,663,-232,234,-40,-255,-592,-967,-12,-479,784,173,-225,-781,-48,1000,1000,1000,31,788,-942,215,534,-570,-976,-1000,534,605,927,-1000,-689,612,-116,365,-151,-357,300,852,-730,-777,32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{1000,1000,-598,-1000,-1000,124,-922,154,-694,1000,1000,129,-226,-41,589,557,-588,-1000,-88,-823,-43,-976,1000,-32,-427,-839,1000,1000,514,365,757,381,950,-902,212,-1000,1000,-91,870,-222,-1000,-280,-982,1000,236,711,-49,229,-65,179,-514,177,1000,1000,-1000,-488,-874,1000,771,1000,886,-255,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-1000,1000,1000,1000,-814,949,-251,194,748,-1000,-1000,708,179,-1000,-528,-685,313,1000,288,1000,-1000,1000,-1000,663,1000,-759,-502,-734,783,707,-405,327,-1000,257,-683,186,-1000,-1000,797,520,1000,868,1000,-1000,311,-158,-147,-321,-592,628,1000,319,82,-445,1000,-887,245,-707,-535,-1000,-81,478,-1000,-765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-1000,451,1000,1000,-563,604,-1000,1000,348,-1000,-307,877,-613,-1000,1000,114,-1000,1000,956,626,-811,1000,-1000,-1000,85,-1000,-345,-1000,-342,210,-445,-242,65,-815,-64,1000,-1000,-1000,1000,744,1000,1000,-1000,-1000,45,-705,-933,-995,-1000,-935,1000,1000,-605,-946,705,-956,282,-51,45,-1000,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{1000,-521,1000,530,135,952,-581,-937,276,112,-838,-340,999,31,-313,196,-1000,-1000,85,-287,1000,-723,-866,-755,341,1000,-245,1000,-316,-422,-810,116,149,745,168,-88,859,-538,-220,-4,-1000,346,-1000,1000,-68,-593,-707,-54,389,-75,263,370,-285,572,-1000,-718,-79,992,493,-274,1000,694,538,-112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-1000,-333,-385,657,947,-106,366,1000,662,1000,-1000,431,-1000,-1000,1000,-76,-734,1000,-476,299,-1000,1000,-316,-812,1000,-1000,897,-1000,-1000,881,-695,-318,-725,-56,-1000,163,-1000,-303,-215,-145,184,1000,508,-1000,157,-857,-191,6,1000,624,400,718,-2,-1000,1000,-817,1000,-1000,-723,-1000,-770,86,-879,318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{1000,271,28,-425,-463,534,357,-1000,475,1000,123,-447,-828,1000,552,1000,-286,-1000,-643,-927,1000,-1000,-67,-340,701,254,1000,122,-263,-348,141,1000,-608,730,1000,-1000,806,311,-647,-618,-428,-161,-1000,1000,123,-1000,1000,1000,550,-227,-1000,-813,226,-664,-1000,-869,-976,1000,-171,1000,922,-292,989,556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{-87,1000,809,696,-350,529,-557,-917,1000,-161,264,340,-257,-432,-1000,1000,-1000,-595,128,-214,1000,924,-259,-55,573,83,875,141,-41,-709,-979,-139,-479,653,310,-591,-507,-48,114,772,495,180,441,-180,306,-558,119,-321,476,534,-8,39,-1000,-326,-100,-224,-92,191,-281,693,848,-582,-136,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{1000,-400,119,-637,-1000,459,63,-1000,950,503,833,-18,-927,262,-848,1000,-330,-1000,153,-1000,1000,-990,452,133,403,997,1000,507,-1000,-1000,144,1000,-743,1000,1000,-1000,1000,978,-1000,-156,-166,-524,-477,1000,548,-1000,1000,1000,183,847,-1000,-1000,-588,-773,-1000,-433,-1000,1000,-409,1000,815,-1000,1000,670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "equals(java.lang.Object):boolean",
            new int[]{274,195,770,527,-140,449,-563,-862,-34,441,346,-361,-337,369,899,563,-411,-324,16,-311,524,-395,-453,-649,573,-301,897,188,-23,59,-59,252,130,-56,993,-426,-265,-561,115,-145,171,337,-629,754,164,-857,767,403,607,-866,-446,266,-2,-197,-645,-817,-620,696,121,598,888,626,327,287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTc3:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{845,-1000,87,316,-850,-267,-1000,190,-130,-608,-1000,134,-589,755,-1000,1000,-650,-279,1000,-964,-1000,-1000,-615,-1000,1000,-997,259,-575,1000,-399,-1000,-340,-419,1000,875,459,-1000,-1000,886,904,471,-334,1000,-448,-590,-756,237,-266,59,-352,980,-1000,-1000,-513,209,23,-1000,-801,-1000,226,9,662,-1000,-734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-98,-665,1000,1000,39,-1000,-898,701,982,-602,-1000,129,-720,825,-1000,638,860,-537,1000,113,-1000,-445,-1000,316,1000,-1000,175,-1000,798,1000,502,1000,234,1000,1000,475,-1000,-856,111,1000,555,619,1000,-692,-909,-28,1000,-875,1000,-798,1000,-1000,-1000,223,566,-422,-1000,231,-1000,-1000,-974,241,-480,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{87,184,-226,-282,-239,1000,603,332,1000,1000,-1000,95,-1000,-1000,-411,63,856,1000,859,-204,-1000,-697,-681,-690,-662,-643,-37,-138,26,-598,-577,461,-1000,566,-510,-53,459,-550,-74,-55,-662,286,396,-627,58,916,286,-820,-123,229,-38,70,-277,724,1000,-841,543,-495,579,400,-589,394,-785,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{44,1000,-1000,-400,346,-798,-171,-885,-425,678,113,-636,-129,130,276,287,77,275,-954,124,760,-266,386,-39,-28,-932,338,-92,76,-21,499,-620,269,-711,-1000,-239,-818,300,-1000,-547,-64,-542,-1000,145,-133,-412,-454,81,1000,1000,42,327,829,-899,1000,815,-296,-1000,-619,-892,1000,-525,-424,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-354,-125,1000,830,-359,355,-232,390,1000,70,-513,-140,-725,-134,-844,-152,856,-108,1000,52,-842,-523,-681,212,454,-1000,300,-1000,407,553,823,819,-289,440,862,177,-406,-398,131,1000,62,786,1000,-267,-537,397,1000,-698,1000,-406,1000,-391,-1000,-193,931,-174,-227,-320,-510,-1000,-322,-9,-310,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-292,-113,246,-349,396,76,-157,190,544,632,-512,-540,-877,-670,-85,15,374,219,746,-575,-523,-430,996,181,-340,636,646,47,295,-399,-684,-346,-1000,-437,-1000,-145,410,-524,1000,-472,-212,795,-493,-175,23,584,-24,-266,304,232,-815,63,52,-565,843,23,398,-366,1000,747,264,609,603,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-366,1000,-1000,79,-486,713,-96,-1000,-560,950,869,-1000,-21,201,-406,0,765,1000,-1000,1000,305,242,563,210,619,-1000,403,246,-416,222,658,-594,465,-417,-110,1000,-334,1000,-1000,-383,64,920,-482,1000,108,-672,-938,-534,-95,1000,-371,1000,-271,870,-182,528,88,-1000,-896,-1000,132,-920,-674,500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getCentralDirectoryExtra():byte[]",
            new int[]{-575,-905,1000,1000,-316,-421,-1000,1000,1000,-528,-1000,259,-762,1000,-1000,332,1000,-1000,1000,165,-1000,-446,-1000,-412,1000,-1000,-318,-1000,862,1000,1000,1000,708,1000,1000,302,-1000,-602,70,1000,289,1000,1000,-1000,-1000,-31,1000,-1000,1000,-1000,1000,-1000,-1000,-172,917,-377,-1000,-458,-1000,-1000,-912,-441,213,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Long:NTA1MjgyNTc=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{1000,-96,431,-167,-767,98,86,1000,-861,-916,-1000,89,187,-1000,-547,-516,-623,-905,795,269,399,-216,-1000,1000,1000,-169,-330,-506,299,-717,-490,168,-1000,-707,121,170,771,-589,-16,274,-405,-655,-142,1000,-8,-485,-825,112,1000,-1000,721,-1000,-856,417,1000,654,-135,468,-1000,-178,332,-552,-1000,641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{317,439,196,1000,42,-146,872,924,-561,-526,-984,269,-10,-1000,-157,-304,300,-840,211,48,137,-628,-1000,991,824,13,443,103,1000,-271,-1000,-367,-418,-32,689,18,623,-301,-76,786,-643,172,207,1000,720,-92,-685,190,527,-542,455,-1000,-56,-637,1000,602,848,106,-1000,-194,856,-218,-447,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{-30,-793,507,520,-591,-604,-297,283,406,192,-74,483,-53,-784,564,-110,-301,678,-954,457,-844,-729,-218,-949,-682,160,907,532,384,867,351,-181,-42,616,883,-261,15,-260,-291,895,-490,118,-7,-391,916,-681,-789,22,-806,84,658,905,775,-878,-396,378,-63,797,629,820,678,203,-538,-467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{468,-617,116,359,-170,-261,-324,750,-160,193,104,-587,503,-616,176,290,350,262,-488,-266,275,1000,106,647,377,857,-389,-506,-515,125,-163,-44,-618,-245,97,182,68,-1000,634,-48,-715,-573,-120,692,400,209,-394,-344,790,-349,-147,280,838,49,319,281,848,-436,-761,438,233,-788,-444,304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{738,652,-260,820,58,-553,909,809,-815,-1000,1000,451,-1000,1000,-271,129,399,-574,359,52,168,-930,617,838,-79,103,1000,894,1000,1000,-536,-1000,702,522,1000,1000,10,361,-404,752,1000,-824,313,265,439,-60,1000,-221,-998,1000,-1000,-184,241,-1000,500,-803,345,189,1000,1000,1000,896,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{656,-289,156,766,242,48,1000,822,467,-826,-1000,519,-10,-1000,-813,-803,7,-164,-29,-618,-306,-879,-1000,729,395,-119,-577,216,698,36,-386,-404,80,-32,872,-157,141,-647,-532,1000,-1000,289,565,349,720,-749,-1000,-43,345,-809,527,-73,299,-637,677,-31,814,480,-807,-70,395,-153,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{-534,155,1000,1000,-1000,-1000,-27,115,-404,1000,254,649,28,-836,1000,875,1000,-573,962,-330,777,-645,-650,-937,4,320,1000,105,831,543,-297,-225,-121,763,1000,-546,597,-448,701,1000,-254,-626,-249,887,1000,1000,-75,611,-183,994,-1000,-1000,925,-1000,-434,-335,-326,-110,-136,874,1000,-91,183,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Long:LTY1NTM2", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExternalAttributes():long",
            new int[]{907,-925,-1000,39,-740,-94,-553,-431,-1000,32,-590,1000,-1000,608,862,-1000,1000,293,67,-409,1000,265,944,-818,1000,-554,-599,310,921,-89,562,616,-496,319,-815,-151,-804,607,592,-8,423,-387,-1000,-1000,-1000,-1000,1000,980,-217,503,-185,397,-444,-1000,404,35,-55,406,1000,-713,1000,826,-388,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-38,467,-985,-868,1000,-1000,162,991,-849,435,-687,-464,952,170,958,-612,448,-523,-898,224,-1000,866,916,497,-764,698,-679,-823,-827,135,-521,-645,-822,182,-620,-214,-105,-469,978,-683,467,-520,-1000,-567,-770,1000,-797,1,743,-1000,-252,-169,127,2,775,218,-180,558,560,-727,-102,-626,1000,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{1000,511,-1000,-728,-941,-381,963,141,934,747,443,410,161,-996,563,378,618,56,257,-420,369,-758,363,-815,1000,93,1000,-416,913,769,-1000,960,-465,-191,-721,562,-796,854,-190,-316,-20,-225,1000,1000,-84,-1,-53,522,-495,839,-303,783,312,46,-1000,-351,-681,-279,637,346,98,975,-63,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-1000,559,-1000,-1000,-1000,476,759,991,-622,161,-768,-364,466,-76,-1000,-114,1000,-1000,-847,176,-1000,-389,-352,1000,1000,347,196,129,-1000,37,1000,1000,-822,182,-620,-960,988,-469,-39,-683,341,-1000,-443,-474,-108,743,-317,-126,1000,-1000,-300,-169,-392,-1000,775,-377,-85,804,1000,-1000,-102,-807,1000,222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{713,-573,-1000,-809,-1000,-954,411,1000,-1000,291,99,-1000,991,253,1000,352,215,303,-1000,345,34,897,522,-1000,-1000,564,1000,-843,-526,84,-1000,465,-977,-165,-1000,231,-726,-739,456,-556,1000,-1000,-658,377,-1000,1000,-479,1000,126,-708,-11,-517,70,-549,-1000,-194,-570,802,1000,-1000,526,118,134,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-1000,735,-2,-569,1000,-65,759,1000,298,175,-1000,-894,-217,334,-1000,-1000,896,-1000,-722,587,-1000,-39,227,1000,610,1000,75,-611,-1000,-488,1000,-421,-346,-305,183,35,784,-1000,-630,-452,-408,-863,-740,-701,-728,1000,-965,-550,1000,-1000,-315,608,422,742,298,-89,91,301,1000,-1000,1000,-180,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-1000,-429,-1000,-1000,1000,842,799,-983,-980,1000,-597,449,967,-387,741,-464,922,-1000,-1000,40,-1000,969,645,967,527,-95,-464,-97,-1000,340,1000,-1000,-1000,1000,-792,-1000,1000,-1000,1000,-724,-783,-805,-1000,-1000,-218,918,-359,-210,1000,-1000,-1000,538,-703,512,186,37,-609,1000,1000,-99,580,-1000,1000,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{630,1000,-1000,-1000,-772,-1000,738,-455,-1000,-62,551,-76,898,-1000,724,801,901,509,24,-743,641,-758,170,-901,927,-234,619,-683,417,1000,-1000,1000,-1000,-453,-1000,879,-257,425,786,-347,141,-225,907,1000,438,823,555,656,-64,323,-246,638,-450,-39,-405,-379,-706,500,1000,655,1000,-77,-782,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraField(org.apache.commons.compress.archivers.zip.ZipShort):org.apache.commons.compress.archivers.zip.ZipExtraField",
            new int[]{-654,511,-1000,-877,-737,-180,161,141,-1000,558,899,303,161,-412,599,950,693,-553,-157,-161,-88,-69,363,-324,1000,-771,1000,-1000,-854,492,-747,-1000,-823,474,-715,476,903,-231,731,-316,441,-309,1000,214,497,-318,743,1000,560,-720,-633,298,-787,-86,-234,-92,-974,659,1000,-191,1000,-170,344,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-1000,591,-172,-449,1000,473,1000,523,616,-447,-580,-142,1000,892,-602,17,351,-1000,-347,1000,1000,-137,-848,-1000,-1000,-750,-400,-1000,279,1000,913,1000,1000,193,497,1000,-408,1000,-46,-769,-1000,-48,1000,176,-1000,-710,984,69,452,1000,1000,-1000,816,359,853,198,600,297,-502,417,132,294,1000,-701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-1000,75,-1000,261,390,196,-142,-260,1000,-1000,-1000,319,-484,806,-968,-741,154,200,370,-646,287,-655,-791,-728,187,-327,-1000,-78,66,438,489,1000,127,951,-498,1000,647,323,-1000,-854,-9,-148,319,388,-780,-1000,-638,111,-1000,746,237,715,600,1000,-207,1000,-1000,-1000,536,728,-848,223,1000,-701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-1000,24,-194,524,-982,-933,504,730,-841,-732,721,-820,-164,-547,-733,-492,1000,-1000,-440,-29,-985,-545,-137,908,1000,-75,-358,631,-1000,1000,662,270,-631,-181,584,249,1000,-127,-1000,-1000,440,-613,-1000,1000,711,-1000,-843,970,-972,1000,309,325,1000,-474,-253,-112,-592,-400,957,1000,-1000,1000,-622,-817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{879,-145,606,-213,-382,-141,211,96,80,114,-1000,189,1000,845,985,-379,625,994,-206,374,-5,-256,-571,-806,-179,-622,-685,595,498,87,536,1000,-722,-929,-126,-527,806,-1000,-659,398,-584,-684,780,-177,351,89,337,15,833,-311,-368,155,-668,-597,1000,-470,1000,-492,-317,-264,223,-344,-454,-483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{1000,-940,1000,160,32,-1000,-276,-434,185,376,734,956,1000,-411,651,1000,262,1000,1000,126,-378,-758,-1000,-144,-383,270,-1000,522,906,158,1000,-976,-625,-1000,-179,-785,659,-1000,-1000,722,268,-1000,423,-424,-54,-289,288,-1000,1000,143,-826,-1000,-739,-544,-1000,1000,-592,1000,-823,-318,568,-477,-940,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-1000,-421,-521,-1000,299,-219,-593,900,534,-301,721,-60,-1000,963,-1000,-59,-1000,199,772,333,350,-1000,477,-387,-510,351,146,335,-1000,-1000,1000,72,743,482,-681,842,1000,-617,1000,-1000,-1000,1000,-902,-175,-298,-852,-704,-450,-1000,1000,-841,-619,1000,-370,488,-50,-913,-495,-760,89,-822,1000,454,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.apache.commons.compress.archivers.zip.ZipExtraField;:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getExtraFields():org.apache.commons.compress.archivers.zip.ZipExtraField[]",
            new int[]{-827,-161,-462,-319,975,-654,-650,900,-9,-76,928,394,-990,359,-385,-587,-696,891,-284,-381,-614,-471,332,-722,533,-327,499,-238,-358,196,-841,-670,-541,468,-681,-853,949,-70,-91,-751,629,789,-686,-175,-931,-852,-684,-450,-572,468,-707,-39,898,-370,-91,904,-759,95,-760,922,-789,621,305,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{-650,-453,-529,1000,736,-1000,-82,-1000,-1000,-1000,839,211,1000,969,-137,-282,873,412,-1000,812,-751,122,1000,227,649,-55,345,1000,-258,713,-1000,842,1000,1000,1000,44,93,-649,-1000,-867,516,-523,1000,221,1000,346,-1000,390,325,1000,458,356,775,102,1000,-130,-915,923,570,308,-1000,661,-1000,-533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{238,372,7,-94,676,-553,332,-1000,-203,778,-913,98,915,1000,1000,256,628,446,-52,-984,-450,-972,-720,554,-995,520,-395,259,293,1000,-809,216,-320,-574,-774,-344,-453,1000,-105,874,273,391,576,-1000,243,-625,-89,-329,5,1000,-753,42,521,87,1000,-205,-605,-267,-91,279,-619,310,-880,142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{79,-1000,637,-165,585,-555,49,109,-954,428,424,168,322,400,139,185,393,495,-937,434,-502,443,1000,506,139,247,-331,474,-1000,784,58,193,167,110,-571,32,598,-655,-895,-16,129,-686,-89,667,442,966,-601,188,405,301,63,-218,-495,704,782,-93,-1000,491,-239,162,-85,247,66,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{612,-385,1000,1000,20,360,366,1000,-1000,-816,1000,432,-309,-1000,139,-694,-488,477,-1000,-1000,21,424,1000,-213,741,405,646,-15,-1000,-1000,370,741,-309,-188,656,390,1000,-1000,-122,-1000,340,-1000,-1000,1000,-427,1000,161,1000,-873,-1000,865,-248,-1000,981,-67,750,-1000,939,-875,57,1000,344,1000,-922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{-997,-385,-1000,-1000,-849,-1000,-136,167,-1000,-1000,-598,-815,-133,444,-1000,1000,-274,1000,459,830,544,1000,-636,485,738,39,-623,139,-1000,-1000,836,-189,940,447,1000,-438,1000,-818,-717,143,-681,-400,1000,1000,-334,1000,-1000,-1000,1000,586,947,-191,1000,-1000,934,-831,1000,676,1000,-540,-699,276,-398,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{77,-237,219,-165,1000,-1000,505,-1000,-450,118,-1000,168,1000,1000,558,83,1000,-73,-291,-203,-746,1000,13,1000,-9,247,-845,1000,172,932,-1000,-390,1000,-578,486,-951,-193,817,-1000,-16,-129,-456,1000,927,1000,23,-1000,141,405,1000,63,-515,964,704,1000,-1000,-1000,615,363,150,-1000,1000,-60,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{776,692,-1000,-986,76,245,314,617,-761,-2,-146,142,556,-1000,-345,190,519,1000,435,-1000,489,642,-29,1000,-1000,238,-1000,-245,-160,433,1000,-99,1000,-187,-138,-26,436,-15,1000,-118,-602,-675,433,826,-757,393,330,-448,278,-61,659,-211,292,1000,1000,-262,17,226,497,117,-957,96,-1000,-891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getInternalAttributes():int",
            new int[]{-217,-361,-825,3,298,-1000,-888,-1000,-815,-1000,-466,237,1000,715,897,-11,1000,473,-717,-215,-384,-331,1000,-2,499,-118,-107,1000,674,824,-679,722,1000,1000,635,622,86,-423,-773,146,679,-518,1000,206,997,65,-740,424,749,1000,215,74,1000,268,422,135,-1000,548,1000,726,-1000,448,-740,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-11,411,1000,1000,-1000,-351,955,744,1000,1000,417,-217,389,493,-611,-831,143,-760,607,1000,526,-352,740,-1000,-1000,-400,546,-1000,-801,-415,-355,1000,1000,1000,1000,-1000,520,157,-1000,619,207,845,-857,-1000,1000,237,950,1000,216,-550,-1000,-673,-639,-702,974,-850,637,713,129,-1000,-721,-910,-355,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{405,1000,975,701,-1000,1000,14,57,953,-1000,-765,-116,-1000,149,1000,961,496,-224,-61,908,191,1000,-398,1000,-448,-824,-1000,19,937,-884,-1000,449,210,825,-885,-986,60,186,-332,670,615,1000,-1000,-723,1000,-252,-1000,-244,305,1000,-160,-409,-481,-726,-931,-549,1000,-839,-1000,1000,1000,-1000,-220,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-943,-1000,1000,664,711,-1000,-836,819,-1000,333,311,458,1000,-789,334,21,893,-1000,79,1000,1000,-538,841,-1000,265,-325,1000,-1000,837,1000,-154,-533,1000,-347,1000,-1000,-60,955,-862,1000,508,-1000,1000,-1000,800,412,1000,893,-1000,-1000,-51,-1000,786,-782,-1000,1000,-197,404,1000,287,-268,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{227,859,1000,-842,-782,187,-1000,-569,-989,-1000,-607,-769,158,218,748,819,295,-1000,-129,213,1000,673,-178,-149,-329,-124,-1000,74,376,-272,-648,-920,637,1000,1000,-840,-196,776,498,1000,187,81,-701,-656,941,-972,-301,-1000,-334,297,668,-896,-627,-770,-1000,457,759,-831,-369,1000,739,-978,-219,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-1000,-224,1000,488,1000,-1000,-563,-757,-381,-513,1000,-869,1000,-1000,388,-587,444,965,160,-259,334,3,-425,-986,147,36,254,-107,-358,1000,342,320,-550,1000,675,446,-534,1000,611,212,-449,-929,538,717,592,33,830,-40,106,-1000,966,1000,363,343,18,1000,1000,528,609,-213,-582,707,-363,11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-1000,-1000,-70,273,286,564,-168,1000,1000,-134,258,1000,-129,-790,57,-719,-727,1000,1000,-1000,-286,-8,640,-512,-225,790,-1000,-1000,1000,-108,-53,889,268,932,-1000,-954,-621,-241,-131,-404,1000,669,157,602,660,15,1000,494,-338,-866,-303,218,569,25,725,-598,482,192,996,-1000,-413,1000,195,739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{180,-677,31,28,-1000,180,1000,-353,-227,938,-1000,1000,-1000,304,1000,1000,1000,-1000,-984,74,1000,-8,-474,-127,-17,-690,1000,-1000,1000,-1000,-1000,-154,1000,-784,781,-1000,-106,-521,92,393,1000,342,-274,-638,1000,-395,-189,-30,51,1000,-1000,-890,96,104,7,899,1000,-1000,-608,455,700,-1000,892,976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-11,535,1000,-1000,-365,909,-1000,744,-214,-1000,-625,-737,-352,204,854,1000,-607,-441,30,578,1000,319,-1000,735,751,-127,-894,-249,364,196,-157,-689,350,373,903,-1000,-1000,327,1000,755,1000,845,-857,-1000,764,-973,-584,-260,216,-550,1000,-260,89,-702,-1000,936,702,-1000,948,1000,1000,-847,811,919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-455,852,391,-138,-1000,687,145,-491,148,-1000,152,1000,-1000,-1000,1000,628,1000,-844,301,188,-635,1000,-995,518,-1000,-1000,-1000,-285,1000,513,-856,-888,949,-132,880,-1000,590,-882,-1000,-1000,1000,315,-842,-1000,1000,967,-309,1000,597,253,89,881,446,362,-725,207,507,535,-1000,1000,1000,1000,632,-960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{-867,-1000,1000,491,-1000,1000,733,154,359,-128,964,723,621,652,-992,-303,-1000,-553,807,1000,-9,-1000,-284,-517,-266,1000,428,-914,1000,790,-1000,-400,1000,-653,861,-1000,154,802,-1000,556,256,1000,-156,-320,790,-129,568,1000,-184,-836,-900,-179,1000,-619,-480,308,642,328,1000,400,-197,-457,787,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLastModifiedDate():java.util.Date",
            new int[]{59,1000,1000,-885,-1000,1000,719,141,1000,-1000,1000,-93,-1000,-1000,1000,1000,188,-1000,443,1000,-461,-1000,-1000,-614,-1000,1000,-1000,-951,274,1000,-1000,-1000,1000,-179,704,-843,1000,181,-1000,-712,1000,1000,-1000,-1000,1000,1000,-1000,1000,1000,391,-96,-81,770,208,602,145,985,1000,-40,1000,1000,-302,174,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{996,404,-985,529,-844,509,143,-707,-200,148,-245,811,-248,-1000,-1000,582,-709,-822,862,-371,-374,-444,-353,635,702,269,56,1000,-758,-561,-540,-1000,-217,-146,70,-864,1000,293,1000,952,-156,699,1000,-415,-506,966,-1000,-612,-274,246,-7,-1000,-153,184,-282,-307,-867,1000,-198,-129,-494,-455,665,-513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{345,719,859,-517,-749,-1000,422,-91,-109,-98,1000,1000,-1000,-589,-1000,-74,-913,-1000,1000,305,-1000,699,-362,-127,-392,356,1000,-112,-788,-1000,-383,561,-55,112,381,-271,531,-57,692,33,1000,-941,766,265,-1000,-382,52,-1000,1000,-131,231,93,-744,-1000,80,-64,1000,592,293,238,306,-214,-714,385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{1000,332,1000,-1000,452,-184,622,-225,66,78,-1000,1000,905,-989,-653,21,-1000,1000,817,-769,929,-1000,-1000,-1000,-383,770,1000,1000,-389,790,1000,-914,-1000,1000,744,327,-500,1000,1000,-522,90,648,473,601,-969,-206,-475,379,893,-84,-1000,1000,238,-866,477,-1000,-1000,1000,879,1000,-1000,80,1000,972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{510,408,862,-539,-735,-215,-966,-976,-953,-269,-63,-722,224,55,181,152,999,1000,-632,689,1000,400,912,-803,-371,-933,1000,-952,-1000,195,889,997,-474,1000,25,158,404,-199,617,-913,1000,-980,-543,1000,863,-508,414,251,859,251,-1000,1000,423,206,547,-849,-63,-1000,-671,-37,147,1000,909,-183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{842,12,254,-151,-460,-324,795,94,-697,95,141,705,-4,-1000,-518,753,-28,663,-85,-527,790,494,111,-932,-157,-238,1000,-375,-992,-548,139,34,-876,1000,-242,-970,-1000,-205,-339,-362,198,-771,42,764,927,-304,150,-410,362,-49,-1000,927,114,300,133,279,-63,434,-118,-129,1000,523,585,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{445,616,-72,234,-281,25,714,174,785,537,-404,-811,-206,840,-29,-819,411,297,718,607,-541,-67,-141,-569,645,975,176,-89,-992,695,729,949,-962,127,-816,-494,-467,-610,-339,-380,247,-200,-598,-50,-904,-254,991,433,304,-618,-924,-358,-782,-414,-410,758,754,-651,-690,-346,-589,809,278,-456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{-550,419,846,-476,-547,-677,-733,-1000,209,-842,1000,578,-558,-315,-143,-680,457,1000,-284,-940,-12,-939,533,-837,-422,-663,-176,-780,653,-108,1000,1000,776,616,-129,527,-146,300,874,1000,72,-204,-920,-902,277,111,-475,-1000,850,-585,-1000,1000,468,872,677,279,11,-153,379,-415,474,159,1000,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getLocalFileDataExtra():byte[]",
            new int[]{756,759,-1000,1000,710,779,-336,-447,-758,-59,128,-884,-1000,-620,-1000,554,1000,-180,-41,527,-562,200,697,946,1000,361,-600,-221,-980,-357,-18,-129,-347,-1000,-643,-1000,-5,-512,-871,-357,-499,148,74,-51,-571,73,263,95,-990,1000,-972,-822,17,822,-684,698,-969,-715,-1000,-1000,674,322,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-190,963,810,-127,402,539,850,-1000,686,974,-881,-601,1000,770,-546,-605,1000,1000,-1000,164,-836,119,-783,1000,1000,1000,25,-861,617,289,-1000,-551,-514,-1000,763,-1000,712,240,1000,-427,1000,1000,-884,-1000,-747,-1000,-479,470,-400,965,1000,103,459,982,505,-401,345,137,629,-1000,1000,1000,62,106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{1000,72,-116,-97,-492,-1000,894,-9,-50,565,418,965,-903,-20,-977,-239,-613,157,-465,-849,448,-300,-590,126,639,92,499,-17,750,-1000,821,774,-1000,1000,1000,-139,-307,-263,552,-211,1000,865,-744,543,-306,-82,87,-905,512,6,-780,370,20,340,-47,198,791,-1000,1000,299,6,2,1000,395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-1000,579,-522,-1000,-1000,-27,1000,437,-541,478,254,-1000,1000,-645,-237,-962,422,811,-55,1000,-1000,-941,-1000,-469,-778,-514,-649,230,17,-491,-452,-307,1000,-1000,-691,-1000,-723,1000,-132,671,371,-1000,-943,-1000,-395,-1000,-478,381,-1000,-1000,1000,-302,1000,-461,-264,1000,1000,-467,-1000,177,1000,864,-925,-437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-1000,-136,-533,-1000,58,-827,384,-1000,-151,645,-881,528,801,-632,-1000,218,-1000,396,3,-13,-836,389,130,-400,1000,631,-140,-278,-625,-837,-777,1000,-813,-1000,1000,-1000,-400,918,255,-692,1000,370,-485,-851,-657,-144,-177,1000,-400,20,1000,992,1000,-215,1000,-671,1000,316,313,-113,1000,181,311,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{63,263,400,-478,890,-1000,-18,-747,1000,110,-1000,1000,583,-1000,-1000,523,-531,137,478,-1000,894,698,1000,400,363,1000,493,-965,-195,-1000,-1000,1000,-59,-19,1000,-772,-133,-284,215,-411,-1000,906,158,265,304,793,-891,238,1000,1000,563,1000,1000,107,896,-1000,1000,567,879,-789,-1000,-647,639,683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-364,792,342,-382,54,226,713,-798,144,1000,-377,-471,168,360,-763,11,336,939,-1000,-185,-868,905,-101,857,1000,1000,810,-1000,175,184,-303,679,-1000,-81,920,-886,403,-132,1000,-1000,1000,1000,-656,-245,-772,-568,-444,449,429,940,117,-93,509,1000,1000,-762,332,-297,1000,-674,721,1000,752,775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getMethod():int",
            new int[]{-283,1000,774,-457,1000,361,339,-747,769,586,-1000,-951,508,517,-162,523,661,906,-783,-1000,-275,698,409,1000,363,1000,800,-497,160,-329,-450,1000,-746,-837,1000,-1000,552,-114,1000,-757,767,1000,-121,-30,-944,149,19,933,593,1000,892,760,653,963,896,-613,-161,168,763,-1000,149,227,-117,41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDA=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{654,-1000,-1000,70,1000,165,-256,-1000,959,-1000,-127,-63,-1000,-1000,845,1000,304,1000,-1000,-262,524,-485,-324,1000,37,1000,329,-625,-899,-1000,-1000,-492,-1000,119,1000,1000,-1000,-1000,1000,680,-1000,1000,1000,-718,1000,1000,571,1000,-1000,352,1000,705,-673,-1000,711,-973,-1000,657,1000,1000,-883,1000,24,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.String:LS02NDI=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{1000,-253,362,184,388,-21,130,-493,-91,885,724,-642,-13,-153,199,-596,536,926,92,-389,443,148,-908,1000,-73,661,664,268,254,1000,386,-990,236,-485,378,119,-349,-761,1000,-533,-441,-5,695,330,963,1000,-71,652,210,724,-34,318,151,-83,-201,229,-845,1000,530,809,-78,709,24,-948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.String:KzcwOA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{1000,67,-1000,-243,1000,-1000,-694,-275,333,-1000,794,-1000,410,20,-708,-422,337,894,185,348,400,64,-1000,942,895,942,848,354,-749,473,-1000,-212,766,806,112,1000,172,-1000,1000,-407,-118,-436,565,149,1000,92,-530,1000,-128,605,-975,192,1000,257,-716,-297,-118,1000,1000,638,-632,584,1000,-926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.String:IC0tODE3IA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{-255,-197,-646,-439,170,83,-42,-374,-817,-837,-397,-1000,-187,-98,1000,184,293,-668,-280,115,270,483,456,196,-371,1000,-410,122,60,-192,132,-799,-1000,7,-723,692,5,171,-567,-180,-66,-766,-219,-202,747,789,248,-38,400,210,1000,-269,641,-1000,-400,64,-777,-286,-20,352,721,367,580,-635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4ODAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{654,-1000,1000,-897,881,-246,-466,288,861,-111,1000,-84,-1000,-67,917,-385,417,358,-574,-1000,-315,553,-1000,1000,1000,445,1000,-981,-1000,1000,-531,-1,-614,-1000,-764,-139,-220,-561,-436,-271,-1000,1000,1000,-351,1000,92,972,1000,-124,184,-407,161,-111,-871,711,-261,-1000,457,1000,419,244,1000,731,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.String:IDY2MiA=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{317,-29,-844,-725,609,509,-103,-854,662,-332,-593,-350,448,-863,807,407,622,-312,296,-775,854,599,915,33,-712,336,352,-38,-116,-446,-459,440,-646,-792,967,366,-778,-352,-501,844,-871,907,373,770,908,112,484,631,-793,891,559,289,-537,18,956,-774,-116,100,865,751,764,78,-505,-976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4ODAwMA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{896,-1000,-1000,848,869,823,79,-53,751,-1000,522,-768,-98,-1000,506,1000,-374,-1000,-319,-220,1000,-908,-986,1000,-1000,1000,-643,-333,-322,-1000,-1000,1000,-1000,-611,1000,1000,-1000,-1000,1000,667,-1000,-174,1000,-718,974,1000,725,895,-1000,805,1000,960,-673,-1000,913,-1000,-719,717,401,1000,-883,1000,6,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.String:Mjk2LjcxMA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getName():java.lang.String",
            new int[]{-920,963,1000,-1000,-555,203,701,325,-296,1000,710,-632,987,1000,-87,-788,-1000,-1000,1000,1000,1000,271,-864,-644,-383,-473,-381,1000,-451,1000,1000,757,1000,-213,-18,-1000,1000,-630,-602,-70,-335,706,-1000,725,-613,-1000,466,-1000,400,18,-1000,-697,-1000,223,-1000,1000,340,466,-1000,-1000,-506,-1000,-268,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-263,915,-330,997,570,6,-202,-642,540,-840,358,-214,614,-1000,-730,-112,-203,-493,339,-311,1000,-352,-203,-128,-685,631,-246,517,-1000,-134,421,-534,953,31,405,296,209,373,278,-621,-672,-8,831,-167,1000,396,488,-387,-298,886,50,316,343,1000,123,-148,963,-455,626,-773,159,-1,786,-688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-113,-305,-1000,-355,1000,1000,1000,914,-772,1000,-306,-738,-651,432,630,1000,1000,669,892,788,-41,572,-444,-91,-854,1000,946,-608,-1000,-1000,-1000,1000,-580,125,39,-517,-1000,849,-1000,430,1000,-672,1000,498,-1000,-1000,-1000,86,-1000,480,-347,-595,-483,1000,1000,-22,156,681,842,-301,582,585,-1000,823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{955,-836,196,-378,570,-427,774,235,-87,-998,713,-811,-945,-118,-243,713,-308,-900,-758,-704,-624,-872,800,573,-469,756,-242,-717,85,-519,452,-543,624,235,-428,791,-736,641,-66,-203,127,-725,611,795,239,485,217,32,965,-711,345,316,-326,535,-366,-570,224,116,-250,-725,82,327,691,227}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{875,83,-797,811,-225,712,1000,1000,540,99,893,-1000,654,76,-491,19,514,543,561,-54,1000,508,-1000,-1000,469,385,1000,-475,59,-278,273,1000,-1000,566,-1000,-493,-1000,1000,-1000,1000,701,-650,-1000,-617,503,-1000,-585,195,-1000,509,-239,-811,281,951,548,118,935,645,137,828,587,29,-747,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-1000,360,735,-69,-664,-1000,-756,-448,1000,-1000,-43,24,433,-864,539,971,-1000,-139,878,-963,221,-130,-437,576,-1000,816,-137,76,186,767,354,-761,829,-423,609,739,276,-497,496,82,-1000,1000,161,808,393,1000,-764,-360,1000,901,-497,216,-184,-881,1000,285,399,-1000,523,-1000,-1000,186,1000,856}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-46,263,-578,-452,-147,861,1000,1000,-65,1000,-5,-1000,-1000,-120,1000,1000,-255,934,1000,-235,71,1000,142,-879,-1000,-154,-1000,-1000,-153,-541,1000,1000,255,-554,-1000,743,-1000,-145,-1000,837,1000,468,-106,51,-1000,-1000,-628,-109,915,361,-883,-653,-1000,-619,727,-534,351,927,785,-650,-199,-191,920,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-1000,1000,-1000,-304,268,647,-661,-1000,793,-876,835,-738,-266,99,630,391,-1000,-1000,-59,-50,614,-479,199,704,-930,1000,212,236,1000,22,-1000,1000,1000,-857,39,1000,363,276,-1000,-76,1000,-100,954,504,-232,-209,1000,-1000,-858,453,-347,-338,488,908,1000,329,-164,-158,267,-23,582,1000,-191,648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{166,-981,-1000,337,1000,-816,40,934,133,27,-871,-704,376,-1000,-381,1000,158,-188,878,-518,718,-104,-400,-887,-1000,142,-137,-214,-1000,-772,595,1000,-835,294,-448,-93,-1000,-301,-1000,721,-936,199,-759,851,875,-1000,-764,923,1000,920,335,421,-888,1000,1000,209,399,-520,1000,-222,325,-301,882,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-1000,1000,1000,94,-1000,852,-776,-321,935,-1000,1000,19,-1000,-231,492,-201,-1000,-692,713,501,712,53,874,321,-792,1000,-251,-222,1000,1000,502,-143,605,-633,-1000,1000,1000,364,350,-591,-862,350,439,-1000,131,411,885,-1000,-483,266,334,-1000,1000,132,-1000,126,437,877,-110,-245,-743,-36,250,818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getPlatform():int",
            new int[]{-897,1000,-1000,-915,-432,1000,859,1000,210,-734,-569,-659,-827,745,492,1000,-1000,-791,253,20,1000,1000,78,-641,-921,952,564,-1000,1000,-328,-1000,1000,-315,-672,-448,1000,-728,810,-508,739,-1000,368,-695,-206,-341,-847,221,-511,68,288,-1000,-50,475,859,516,435,-222,96,1000,205,-1000,877,412,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Integer:NjU1MzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{223,75,66,1000,325,-767,877,485,387,-1000,148,-193,246,-923,-855,925,1000,303,-948,-1000,-83,-440,-338,104,-52,396,-713,254,235,-1000,-636,225,595,-153,874,389,-1000,379,-815,704,237,-959,245,-1000,411,631,19,-889,871,-650,521,1000,-512,2,860,-80,105,904,673,309,-718,421,349,375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{1000,1000,-622,-1000,-340,654,-195,915,-332,-700,114,598,-1000,1000,-534,-1000,-1000,-924,-370,913,-1000,1000,1000,1000,-1000,640,1000,377,-1000,1000,1000,-733,-202,-1000,-463,1000,1000,-404,144,-1000,-425,1000,110,1000,1000,-772,494,-1000,-797,1000,-738,-954,440,222,578,867,139,-998,-1000,-526,287,510,310,-350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{-891,1000,-1000,1000,-445,-1000,-687,-808,316,-614,-265,-1000,287,-363,-386,1000,-789,-986,-1000,-1000,1000,-1000,894,131,-692,-579,-1000,-1000,356,-1000,-715,-465,1000,63,-288,1000,-797,-821,-1000,37,-1000,218,684,-204,-359,-506,89,97,366,-1000,1000,-69,151,-496,-169,202,-857,1000,1000,673,-437,-1000,-703,951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{1000,1000,-1000,818,-494,-5,194,-341,1000,51,-950,-27,1000,-915,-846,444,-849,1000,-765,-984,541,227,-1000,819,-1000,-1000,-305,-402,-1000,286,728,-769,1000,-812,658,1000,-24,1000,-818,1000,-862,1000,-897,1000,932,-179,-1000,1000,-963,1000,949,-553,498,715,-453,697,-1000,-705,925,1000,1000,591,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{966,659,606,-400,-1000,-62,1000,380,-777,-572,-559,180,-929,-45,-238,-978,-719,-606,731,1000,-824,928,957,602,-958,299,89,27,-935,1000,483,-365,-718,-719,88,1000,866,-261,-150,-786,297,1000,729,8,800,39,614,-253,230,897,-647,-160,-345,166,257,399,283,-554,-298,-280,-422,331,999,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{374,-353,798,-1000,-575,-538,1000,769,-788,1000,-300,824,-1000,463,1000,-656,-1000,-446,1000,36,90,529,-63,481,1000,-1000,799,293,-1000,1000,-47,-325,-1000,-522,270,1000,1000,35,871,-1000,-776,371,-46,336,779,-750,-852,1000,-921,888,651,-1000,-59,958,-271,828,-679,-276,257,-42,840,168,-8,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Integer:NjU1MzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{605,-637,798,-92,-327,-222,505,337,524,-396,-411,704,-137,463,270,-656,664,203,-156,223,83,3,350,-530,947,197,982,321,-248,414,-15,188,-686,-522,1000,67,340,1000,149,-478,456,-219,-632,-454,660,1000,-1000,-582,-286,888,524,1000,342,1000,-731,454,-306,-41,150,-561,-117,1000,-8,-14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{1000,-280,66,-921,-613,-251,-909,485,723,-1000,-1000,580,-75,-18,995,-1000,-107,681,275,-1000,-83,-891,-338,104,1000,-404,920,1000,-1000,514,-165,225,-688,-482,1000,1000,406,816,-373,-167,-92,-959,-968,172,1000,-200,-680,653,-426,1000,-153,1000,132,153,-349,363,-406,1000,95,60,673,1000,199,472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Integer:NjU1MzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{659,-845,-324,631,-808,-266,426,285,-332,-951,114,745,-1000,-327,-116,-5,664,-319,-370,-84,395,3,1000,-1000,81,640,307,-592,80,-766,551,-282,-202,-953,540,436,340,442,-529,-273,439,1000,662,-852,-439,801,-343,-1000,619,912,567,783,317,806,-409,441,137,473,150,-967,-1000,138,1000,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "getUnixMode():int",
            new int[]{676,-605,-202,720,-92,-948,756,-43,733,-1000,-59,-733,258,-701,-407,532,685,386,-1000,-696,377,245,-163,-122,263,419,83,314,251,-1000,-661,280,1000,-373,912,676,-1000,565,-1000,729,-1000,-930,-1000,-97,545,583,-334,-552,118,-367,684,1000,-86,285,204,-107,-89,1000,846,774,-803,331,136,593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{295,123,52,-1000,714,930,424,15,-823,-1000,-80,809,276,-349,-491,558,132,1000,-823,266,-1000,100,-266,668,20,369,650,-507,76,-880,566,129,-795,542,263,921,-302,-393,-514,-1000,792,1000,-776,-536,73,-361,1000,734,-602,-303,-1000,704,-201,612,-752,-165,-448,-275,-454,299,-470,1000,1000,39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-1000,1000,44,-54,545,818,-421,114,448,602,-748,579,-578,701,-578,-566,964,708,-471,138,-16,-936,130,914,-1000,59,1000,-322,-510,-680,-1000,444,511,-36,-538,1000,283,-702,1000,-400,-552,-182,-1000,795,-428,-717,-910,381,1000,361,-387,-951,-1000,-430,11,-1000,67,-704,-1000,-835,3,-483,521,76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{44,759,-769,-927,121,812,-351,617,966,411,-576,-461,-967,-256,-1000,112,922,647,-834,624,-545,846,685,1000,-1000,-483,-161,-38,-195,-956,-812,502,-884,414,-377,1000,-506,-135,1000,-582,-428,-1000,-989,-53,-605,-1000,989,150,671,165,-758,-735,-113,867,-275,-1000,-284,-603,-912,488,96,-40,672,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-514,1000,-1000,-522,645,1000,499,219,514,465,-156,1000,-348,173,-1000,686,1000,381,1000,146,-24,-1000,423,1000,84,414,676,-1000,-1000,-451,-1000,631,1000,729,-893,1000,-961,-193,278,203,-236,767,-674,661,-120,396,-503,374,1000,1000,-149,-1000,-429,756,38,-1000,-37,-883,-1000,-1000,-531,-296,730,-748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-340,299,-1000,-617,44,1000,-77,-688,-585,173,-36,956,1000,906,-828,-82,-65,-648,-5,-323,-1000,500,76,666,-309,-413,-1000,16,-1000,514,303,-821,811,-693,-264,-187,-618,896,-861,424,-473,1000,-276,622,9,252,1000,247,-558,606,-441,-708,816,-893,-534,454,-61,340,-1000,662,-919,-128,564,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-76,-1000,-1000,1000,503,-899,124,316,-419,304,-80,-941,228,20,-491,970,68,-779,112,-1000,-203,1000,-666,-874,379,-1000,-962,893,-838,1000,-310,367,-653,1000,816,-888,-1000,1000,-7,1000,-1000,362,361,-1000,1000,388,-213,-1000,-1000,820,-1000,-188,676,-794,963,-462,-1000,-354,1000,299,-652,-1000,100,39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{178,1000,756,-927,-324,899,-263,690,-286,-553,590,1000,11,1000,1000,-261,160,949,1000,1000,-1000,-1000,620,523,695,1000,773,-1000,-1000,231,420,-945,-410,-1000,-1000,888,1000,-1000,-649,-400,1000,-362,-333,1000,-163,1000,445,1000,1000,-1000,-334,628,303,794,-512,678,-161,-308,-1000,2,-239,655,1000,642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isDirectory():boolean",
            new int[]{-1000,-400,-1000,1000,757,-573,164,-852,515,1000,-666,-1000,-434,1000,-939,1000,304,1000,823,-928,224,-1000,-495,-65,-1000,-1000,-1000,-1000,-871,807,-1000,1000,830,630,1000,-763,-760,860,1000,486,-1000,1000,-514,-563,1000,-214,-490,-865,-400,1000,-1000,-1000,588,-472,1000,-1000,-922,472,400,-164,-1000,-1000,-324,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isSupportedCompressionMethod():boolean",
            new int[]{1000,903,327,332,909,754,690,205,-1000,104,-259,-197,-510,392,805,-138,-318,-880,818,-443,-683,242,-947,-244,612,-92,-304,1000,1000,780,-926,169,-265,-381,-296,-424,418,-679,549,400,-1000,-27,-503,-145,-159,672,944,429,514,1000,-1000,-964,1000,-160,874,648,-268,-134,1000,-193,946,-1000,-308,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isSupportedCompressionMethod():boolean",
            new int[]{-238,-796,282,1000,644,679,-466,236,-868,336,1000,-592,991,31,879,1000,-580,-1000,138,152,1000,-1000,681,-617,569,97,-607,105,1000,-276,638,-1000,1000,673,-290,101,964,-1000,-291,-597,368,759,-572,941,1000,-1000,1000,-1000,-1000,1000,493,308,845,382,-932,1000,1000,560,359,160,789,950,-848,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isSupportedCompressionMethod():boolean",
            new int[]{-1000,-541,-8,152,-173,-35,-380,-219,1000,-321,85,139,248,11,-170,-348,648,905,-1000,900,1000,-577,1000,-250,-561,736,-230,-1000,146,-1000,1000,477,-562,-448,762,242,378,851,-200,-688,-393,640,-648,662,302,-127,792,449,329,-979,1000,947,146,415,-1000,86,1000,-266,230,-864,-1000,927,-524,997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isSupportedCompressionMethod():boolean",
            new int[]{-192,-1000,-644,1000,1000,-1000,-81,-568,1000,-134,-1000,792,-271,576,-272,-805,-578,664,1000,965,1000,1000,1000,-428,1000,863,-270,-109,-623,400,394,495,-23,400,-518,285,1000,-185,-474,19,-727,-351,-1000,580,-377,-1000,1000,545,-327,-159,1000,877,1000,-312,-492,-1000,711,971,1000,-1000,-252,1000,-1000,244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isSupportedCompressionMethod():boolean",
            new int[]{-315,-1000,-229,1000,1000,-881,-622,-209,400,-979,-74,221,926,1000,-1000,72,-806,1000,132,1000,1000,854,1000,559,-1000,1000,194,-1000,-267,-921,423,853,151,-1000,-58,-1000,1000,1000,614,-1000,-575,1000,-1000,1000,775,181,-1000,397,1000,-385,1000,1000,882,122,-1000,-1000,1000,524,1000,398,-1000,1000,-1000,843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isSupportedCompressionMethod():boolean",
            new int[]{466,931,276,1000,1000,-12,471,-271,495,177,459,-363,-1000,-58,-604,-392,-161,1000,-1000,590,1000,643,-907,765,391,573,635,-1000,1000,-472,-319,-519,-617,-1000,75,-579,542,1000,862,1000,-1000,1000,-1000,1000,491,724,371,729,595,-789,-132,425,1000,323,-1000,12,482,508,1000,77,-279,10,-1000,-379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isSupportedCompressionMethod():boolean",
            new int[]{606,1000,1000,1000,245,-290,-697,-762,-1000,-290,-1000,-762,-73,-1000,1000,-1000,1000,-1000,1000,-1000,-1000,843,834,1000,-808,-311,754,454,-658,-1000,-1000,1000,-1000,-725,371,-33,-1000,939,1000,130,-1000,45,-868,-550,223,-127,-1000,1000,590,-675,-869,-1000,946,1000,-651,-263,-1000,552,-739,1000,-409,169,742,656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isSupportedCompressionMethod():boolean",
            new int[]{771,931,828,-710,-931,1000,594,44,-1000,204,-214,-698,-1000,-92,1000,-334,1000,-1000,1000,-1000,-1000,209,-1000,-823,-1000,208,-720,1000,-411,-553,-236,-1000,-770,660,685,210,-1000,-1000,1000,1000,-21,-1000,1000,-1000,-1000,-786,914,352,-1000,1000,-1000,-1000,-236,-88,1000,1000,-1000,-870,-640,-908,-45,-1000,1000,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "isSupportedCompressionMethod():boolean",
            new int[]{-38,-284,197,927,948,-1000,-54,-428,1000,-474,-1000,-85,1000,523,-699,-43,-126,1000,-703,965,1000,1000,834,614,644,1000,605,-1000,1000,-1000,454,798,-23,-1000,324,-33,866,1000,-744,-1000,-1000,396,-868,1000,211,-127,364,586,590,-675,1000,825,946,-584,-1000,-261,909,781,266,289,-815,1000,-1000,858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-513,387,-1000,510,-487,-1000,145,-592,-39,292,447,-1000,468,-332,111,407,532,-710,-336,-50,392,403,-622,456,378,-506,4,-207,-199,-945,-842,455,203,-254,250,141,-367,1000,513,287,-811,181,248,-560,-257,-291,1000,-80,-706,199,-316,-124,1000,464,-533,-21,-146,-747,409,500,-376,-257,-77,724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-281,-433,339,-95,-427,-337,-715,-399,54,-181,725,325,-537,238,33,113,36,370,789,411,-124,465,459,275,-112,469,-198,350,763,-47,887,-79,138,865,1000,1000,67,-953,-1000,313,727,-469,1000,-158,388,-1000,626,-843,580,-7,739,-450,-1000,-204,-999,-1000,195,263,-24,-314,834,797,219,480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{478,-561,-981,-618,-150,-440,-202,-229,80,85,-662,456,232,314,-338,-671,381,-276,107,782,362,-231,291,-819,567,-466,100,-994,396,906,-214,632,-89,-339,419,900,-926,901,-111,51,529,679,385,-574,290,311,-112,-638,-17,-143,567,168,245,597,-808,739,-176,902,660,-582,526,-123,-423,34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-678,-1000,23,987,811,128,584,-462,350,-391,-481,-346,-134,679,1000,-81,1000,-983,491,-189,9,810,-206,-216,766,-439,-1000,-939,573,829,-306,864,-628,-443,205,1000,-1000,1000,89,7,-162,419,1000,-105,210,-1000,262,-235,182,-189,703,17,618,-630,326,888,-1000,257,-414,841,1000,-185,-86,668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-534,-1000,-622,1000,-1000,-381,-1000,-400,358,794,592,-505,-556,173,570,460,230,-884,-273,-461,-453,654,1000,821,-295,1000,-389,1000,-759,-1000,-16,585,-507,179,1000,1000,-1000,146,215,85,-1000,215,956,-755,-141,-1000,634,-212,1000,78,176,188,1000,181,-1000,-440,56,-929,-228,1000,-266,536,143,718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-418,79,-1000,-82,174,525,145,478,-759,-830,447,-1000,122,-590,339,-144,246,-212,-6,1,1000,-438,-622,278,814,-769,4,-1000,623,-630,-1000,463,437,-248,-734,900,416,920,284,-36,-811,1000,-30,-560,-257,989,907,-546,-1000,-303,305,-267,1000,-340,331,1000,371,-747,-211,338,-1000,-1000,-363,-548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-300,-1000,-1000,958,410,-352,-311,-463,212,292,-417,-1000,-598,711,1000,221,243,-1000,256,-1000,-62,302,172,1000,651,61,-135,-270,-710,-1000,-1000,550,721,-1000,68,1000,-1000,1000,1000,-159,-973,1000,640,390,-640,391,498,248,547,-119,309,-128,1000,618,217,1000,275,-1000,-566,1000,-1000,-675,-489,353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("THROW:java.util.NoSuchElementException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "removeExtraField(org.apache.commons.compress.archivers.zip.ZipShort):void",
            new int[]{-739,-400,-111,655,-167,-246,758,730,-445,-484,845,-206,34,405,301,679,969,370,60,625,717,745,-385,-4,412,-805,745,-917,842,-358,-623,829,235,-77,-72,438,917,971,402,325,-611,-336,282,607,-186,-773,828,-540,-899,-16,782,18,841,-357,-332,-739,-814,-148,-101,-687,439,743,-291,943}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{1000,-1,1000,1000,-1000,-180,-919,1000,-1000,547,1000,283,266,1000,424,-794,22,623,1000,1000,66,-295,252,1000,472,496,578,-1000,1000,-1000,401,-1000,115,-1000,238,13,774,-917,863,811,660,-1000,-129,1000,236,682,177,-274,1000,-293,-1000,-391,-50,-580,-533,398,-89,-960,69,452,840,652,363,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{-508,-780,-746,665,790,222,852,493,-16,757,679,-743,674,537,-372,-278,705,-500,675,-796,36,-329,993,-507,799,-769,353,251,-229,-74,157,328,447,-582,-923,828,669,947,-323,418,323,-234,-374,-620,1,-790,-837,209,-256,969,-703,-822,963,173,931,-776,471,208,-446,-243,-792,873,-575,-460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("THROW:java.lang.RuntimeException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{1000,-92,-536,814,-1000,13,-106,571,-1000,-782,-106,-899,553,1000,-1000,-2,-247,1000,1000,-1000,-671,1000,479,178,159,-1000,347,-1000,480,-1000,-251,-1000,318,92,1000,-324,-586,-1000,-278,-45,1000,-1000,87,1000,-759,-802,1000,-108,1000,1000,212,-298,-972,-300,-1000,940,1000,-1000,-962,756,1000,1000,768,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{338,-597,-1000,1000,-850,518,1000,941,-751,-210,755,-824,292,1000,-1000,-328,167,313,1000,94,-631,633,1000,-595,1000,-499,1000,-842,0,-1000,-94,-714,-95,-513,470,466,730,579,-14,1000,719,-1000,-414,892,624,-1000,77,-576,701,222,-1000,-794,707,37,-114,-764,1000,-381,-763,73,179,1000,-426,715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{300,-912,-1000,-735,906,-270,127,18,737,43,572,-576,-156,537,-350,167,336,-697,175,-983,271,-810,877,-213,782,399,353,521,624,-1000,119,158,-415,-919,-420,956,1000,300,-363,418,178,-147,412,-1000,267,-490,-414,-627,498,-618,-1000,516,842,-150,1000,-605,-285,258,556,-1000,-1000,-375,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{1000,-352,-1000,-400,-1000,-109,-661,626,-529,-964,182,-485,-81,1000,-1000,-423,-449,1000,1000,-811,-722,832,450,-290,934,-278,-25,-1000,559,-1000,-94,-1000,-310,-58,1000,71,-44,-390,707,-452,1000,-1000,228,1000,-114,-958,742,-1000,1000,-869,212,-377,5,-795,-751,305,1000,-1000,11,-506,949,886,-322,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{1000,-597,-1000,1000,-1000,279,1000,1000,-1000,-1000,809,-273,-295,1000,-1000,86,-878,-29,725,661,-1000,-80,1000,565,1000,464,1000,-1000,832,-1000,821,-1000,-1000,-8,822,476,770,889,556,692,486,-1000,-168,1000,-364,-1000,-38,253,613,-491,-1000,-776,1000,-685,-1000,-849,-45,-1000,-1000,1000,1000,929,366,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setCentralDirectoryExtra(byte[]):void",
            new int[]{300,-673,-1000,-735,906,-217,207,704,370,43,1000,-408,-224,537,-350,262,163,-1000,614,-1000,171,-810,891,-125,1000,494,353,521,849,242,157,298,28,-1000,-430,776,1000,711,-215,418,32,-202,412,-1000,169,-790,-414,-419,365,-19,-1000,516,963,-609,1000,-1000,558,-31,556,-1000,-1000,-361,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{-1000,-748,663,-673,877,763,-918,-884,433,-47,966,-381,-125,721,1000,-1000,-86,971,-305,1000,322,-150,-439,633,-1000,-1000,1000,-1000,-546,-626,353,-901,-891,251,-676,674,559,-1000,506,502,524,701,879,-1000,429,-755,1000,-114,352,-164,123,479,-1000,759,416,-547,-1000,330,256,1000,1000,-1000,-1000,220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{313,-305,1000,61,362,208,-170,-1000,-224,-1000,-495,-157,-370,246,248,313,-1000,6,784,300,1000,935,-694,5,187,-546,148,-182,935,-432,-192,-1000,-264,898,240,861,250,-113,447,-159,-187,796,-1000,-874,-1000,715,-830,794,-519,1000,257,98,1000,-371,-135,-308,752,214,-326,219,305,1000,-115,-824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{270,776,-248,-169,-813,-722,-241,-400,-1000,-1000,1000,-941,1000,1000,-1000,1000,459,587,880,-1000,-100,210,-1000,521,334,-1000,-517,55,260,-626,-1000,-892,299,-787,293,-5,-1000,-766,-1000,-815,-549,701,109,-819,-102,1000,-388,1000,-261,1000,338,-270,-835,384,-1000,-1000,933,-1000,1000,-516,337,297,845,-905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{653,892,-270,-379,-573,-876,-171,1000,-283,-432,1000,-342,187,743,-912,655,459,190,744,-951,-1000,1000,248,-879,-897,-826,-55,926,575,-148,110,497,795,-559,797,-926,-32,444,-589,-456,81,-44,-489,-819,-207,748,1000,510,1000,-104,1000,-192,50,-422,-953,-838,638,-143,783,-911,332,819,860,-567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{505,-1000,171,-1000,-717,-822,539,1000,-320,1000,845,-917,1000,-7,-875,909,812,1000,-1000,-930,97,1000,-1000,1000,-528,-971,-1000,1000,-361,754,366,553,922,288,-855,-1000,-1000,310,613,938,190,-467,-1000,275,-1000,466,1000,1000,-764,-569,1000,1000,-357,56,-421,-812,-950,263,960,-821,-134,741,-201,-380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{1000,1000,-1000,1000,-1000,-1000,419,638,-1000,-223,784,1000,868,109,-1000,994,-202,-1000,1000,-1000,19,972,-79,-805,-90,67,617,1000,1000,314,-493,350,276,-414,1000,-1000,-325,844,-807,-596,-1000,192,-838,-733,-942,1000,-203,824,128,-667,887,-770,1000,-1000,-1000,1000,1000,-553,1000,-1000,738,1000,412,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{943,824,-1000,400,-170,-1000,-12,-646,-90,1000,1000,820,242,-1000,-120,259,-1000,-400,-537,-1000,179,-1000,-1000,1000,-372,-1000,638,911,1000,754,-732,-1000,-989,-73,-173,-1000,-957,30,-1000,113,266,680,-179,-776,-1000,-1000,22,1000,667,541,337,487,-149,1000,343,443,192,-983,1000,81,626,915,596,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{964,911,978,-1000,93,189,-1000,1000,705,-402,935,955,-614,-298,412,-531,1000,986,1000,-753,-1000,1000,1000,-1000,-1000,504,1000,1000,849,729,1000,1000,-114,-620,195,-1000,1000,1000,-87,468,172,410,-912,865,-449,838,1000,1000,-241,-1000,1000,1000,-851,-854,-187,-710,932,-1000,-843,-791,-876,1000,543,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{911,-481,-1000,925,-376,492,-584,-227,485,-992,683,-265,-1000,432,541,-96,-1000,248,475,-54,837,-633,-175,-614,-409,972,-482,611,1000,-458,233,-534,1000,1000,1000,339,-465,491,821,-435,-459,149,-279,676,724,974,-1000,-936,-599,1000,-663,-562,1000,-1000,-769,-763,1000,80,-760,-644,-768,562,272,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("VOID|getExternalAttributes=java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExternalAttributes(long):void",
            new int[]{532,112,1000,-1000,565,355,-433,351,620,-19,381,-369,178,755,-1000,159,1000,996,-400,-438,-612,1000,-208,212,-799,-32,464,1000,-307,587,1000,1000,1000,200,397,-1000,-210,623,1000,524,305,-619,-1000,1000,-65,138,-400,311,-1000,-1000,1000,1000,374,-246,-654,262,-352,1000,-96,-888,-679,1000,-609,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{1000,575,-105,442,1000,-1000,-631,1000,-181,472,145,-150,612,-391,459,486,286,1000,-1000,1000,-517,366,981,-202,759,-651,-798,-846,643,-199,134,987,-324,1000,-54,-1000,-634,-197,-490,518,213,-1000,-255,-564,-1000,-247,724,1000,-497,-1000,-1000,549,23,-598,50,-400,677,-399,1000,-1000,-18,377,-1000,573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("THROW:java.lang.RuntimeException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{457,916,254,769,1000,-1000,-676,646,892,167,476,782,1000,806,-294,-689,758,451,-1000,-1000,336,-208,383,-120,-175,249,-147,-265,562,-19,-563,172,-888,39,555,-1000,72,382,-687,1000,-1000,-74,-287,618,17,1000,-190,1000,734,-206,-355,-471,1000,-1000,279,13,185,1000,826,-57,785,-1000,-1000,-626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{1000,-1000,-390,261,-258,-332,572,-1000,-472,-424,-858,-592,540,-1000,1000,1000,657,-1000,1000,1000,-1000,888,-1000,655,238,-564,-515,352,812,167,1000,-559,-340,340,85,712,-639,-659,760,-1000,1000,-850,-96,1000,-673,949,-55,-846,-480,-711,434,51,-1000,1000,35,-694,-115,-1000,-613,-476,-835,577,770,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{570,1000,254,-1000,174,-121,1000,-787,31,940,-108,-246,-480,-1000,995,-1000,-321,1000,-209,-389,210,1000,-561,393,1000,-643,-962,-504,288,-1000,1000,-1000,576,-721,293,838,-971,1000,1000,1000,579,-272,-47,429,-185,767,470,166,318,242,599,215,326,-288,-1000,1000,-382,246,359,811,-769,747,557,163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{1000,-104,535,-23,873,327,-466,134,-873,40,-58,-838,287,-1000,981,-20,-191,-168,1000,1000,-1000,519,-113,240,271,-550,-875,-941,979,-1000,940,-1000,-1,150,-148,-525,-624,-347,1000,-984,400,-1000,677,951,-1000,-398,936,1000,-1000,-1000,771,305,618,1000,57,772,690,-1000,1000,-1000,-1000,1000,-937,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{107,1000,1000,-1000,954,-632,714,743,-1000,76,1000,-1000,-516,58,1000,-240,-1000,279,-147,1000,626,673,414,-744,626,-829,-928,-333,-1000,-1000,1000,-1000,-1000,1000,-171,129,-411,-1000,-448,203,662,-1000,-216,-1000,-1000,402,1000,814,-1000,-1000,-483,1000,221,-151,205,1000,1000,-1000,1000,-1000,-1000,1000,-349,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtra(byte[]):void",
            new int[]{-63,1000,1000,-1000,493,-467,784,766,-1000,828,52,-1000,32,-267,1000,-991,-372,1000,-464,656,342,879,825,269,808,-347,-760,-1000,-269,-1000,1000,-684,-492,1000,-611,-1000,908,551,1000,311,733,-906,-350,-998,-614,-449,1000,1000,-1000,-473,503,431,1000,-792,-972,1000,914,374,1000,-1000,-671,1000,-750,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{1000,-1000,-121,131,912,-486,107,-932,-678,-1000,204,-1000,-706,-428,820,-130,919,-1000,1000,-1000,-234,-1000,678,-78,397,-1000,-1000,-1000,592,-894,581,719,84,1000,1000,-1000,-103,1000,1000,-737,-486,-963,1000,-1000,792,560,395,551,-263,-894,-1000,1000,-1000,-1000,-905,1000,-1000,-312,-1000,-1000,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-178,263,-1000,-1000,-538,1000,113,-1000,240,-170,1000,-536,-872,-455,1000,16,-15,343,472,-296,495,228,1000,610,-458,-868,-875,409,142,322,829,-43,-246,244,-178,-1000,1000,432,-847,-903,-1000,111,800,946,1000,-864,939,1000,-128,40,-368,936,-543,655,-1000,-133,1000,-1000,-694,-1000,172,557,-140,543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-1000,267,239,-772,-541,898,629,-875,285,174,104,1000,495,-459,574,-731,688,-566,276,-307,-35,-24,1000,-195,-226,-1000,-165,1000,-790,679,525,648,321,-693,-91,-270,220,1000,-689,183,527,439,5,836,-146,-489,-242,535,-796,-119,58,64,422,-506,-89,-207,108,-346,-206,-219,1000,-192,-98,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-1000,267,18,-772,-541,871,629,-858,285,174,172,1000,828,-455,531,-791,668,-566,276,-307,202,-24,1000,-314,-239,-1000,-165,1000,-1000,679,559,-43,284,-693,-91,-270,536,1000,-1000,266,1000,907,-198,-1000,1000,-197,-242,983,-906,-119,1,-553,883,-506,-89,-207,108,1000,-311,-219,1000,-192,-98,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-873,-1000,1000,299,-100,882,589,-940,1000,779,1000,591,1000,-1000,1000,222,1000,881,997,126,-576,1000,-913,139,-1000,539,-664,624,857,-461,305,-796,13,-812,-1000,203,1000,-910,-66,-615,-1000,-406,485,1000,243,-1000,547,788,662,43,-748,714,-877,887,-855,-957,506,-638,-327,-546,1000,-93,-558,197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-139,675,324,-78,828,-475,883,-221,433,-297,-437,696,427,-116,-247,145,167,-994,836,16,311,-238,36,12,-994,-640,221,-47,638,380,545,193,16,-954,174,-359,-762,852,-736,254,441,91,-216,-735,-879,563,-133,-66,-218,-511,930,125,303,-508,969,-362,-406,424,166,-94,875,-285,216,-613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{311,71,-861,511,1000,164,38,-966,285,174,18,-834,30,-498,440,-791,483,333,276,-232,1000,-400,1000,-550,345,-77,-1000,-753,-286,667,-142,257,284,269,1000,-639,727,337,157,-885,1000,-67,643,-400,1000,778,-680,417,501,-505,-491,266,-224,-321,-573,370,-823,-922,-387,-410,-629,455,270,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{-799,-152,-113,-328,586,1000,474,-564,-461,440,192,819,592,465,-167,-632,1000,660,-400,98,-733,645,1000,-346,-502,-563,-113,1000,101,-1000,-227,-581,774,-957,-563,-1000,5,-1000,514,-1000,1000,-24,416,987,371,-244,189,617,334,-171,-381,-400,-1,712,111,-584,-361,-603,-342,568,-200,150,-786,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setExtraFields(org.apache.commons.compress.archivers.zip.ZipExtraField[]):void",
            new int[]{493,-1000,478,-1000,-360,78,277,-1000,-57,-586,751,-1000,-195,-476,600,-981,153,-1000,1000,-612,850,-943,1000,287,-1000,-1000,-478,-79,1000,1000,960,607,-822,439,-682,-1000,1000,1000,-1000,1000,-855,-863,482,107,-949,-808,658,908,-321,-321,-905,1000,-509,-307,-994,-88,254,-200,-911,-1000,537,1000,134,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-474,-1000,1000,659,105,498,264,-136,957,182,-400,1000,-431,22,1000,-146,725,-50,179,-598,966,-33,1000,-482,-1000,999,-400,-655,-33,-90,-1000,342,-595,-868,434,-255,87,-582,1000,1000,-512,-516,1000,503,138,-781,289,-785,-794,-820,-1000,280,46,426,594,50,-478,-274,1000,459,-625,10,-95,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-948,515,-610,131,58,498,151,-1000,-215,-1000,743,-1000,473,-817,-1000,750,-1000,-701,240,1000,-1000,1000,-296,1000,1000,-434,1000,1000,1000,-90,203,-1000,388,1000,-976,28,-1000,-99,-1000,-278,-298,87,-550,-398,-463,686,508,576,1000,1000,1000,-749,1000,40,-716,1000,1000,1000,-1000,-1000,-199,-926,378,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-967,748,-865,308,-728,261,1000,-111,1000,358,1000,-238,-295,-448,-684,40,-504,-686,-157,379,89,987,669,456,1000,-76,412,662,281,-132,954,443,-96,286,30,230,-1000,287,-1000,224,-433,-685,265,1000,1000,887,558,-448,605,-163,194,-682,-395,1000,-301,810,356,133,-4,-632,466,-311,1000,-255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-193,343,-962,1000,337,-74,-172,177,50,-1000,1000,803,570,315,4,1,904,-1000,-416,4,-877,1000,-85,1000,1000,-1000,1000,1000,836,750,-66,84,489,1000,257,784,-365,225,378,-1000,268,-1000,783,-66,1000,1000,-182,698,-992,346,578,-143,683,-1000,-1000,780,66,-164,-525,17,1000,-366,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-804,520,-1000,796,-1000,-842,274,-111,-1000,-317,-71,-413,-1000,683,-396,-597,218,1000,309,-474,1000,1000,-1000,-765,-518,-985,-289,459,-1000,-1000,1000,-316,645,1000,276,-821,1000,-1000,-1000,-223,1000,770,-1000,-1000,-677,175,-944,1000,-1000,-932,495,-1000,100,-199,597,665,1000,-792,-1000,-1000,916,968,483,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-267,-237,-962,739,650,-1000,141,1000,50,-276,1000,305,-1000,1000,524,-1000,1000,-445,-366,565,-238,991,241,800,400,-399,1000,-79,-890,1000,-379,-931,-463,175,1000,-94,-647,-435,263,-400,883,-1000,299,-508,-397,167,-182,511,-1000,-597,578,-143,-743,-1000,-1000,-132,-899,-345,-525,399,890,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:NTQ=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-297,52,1000,-1000,-39,-20,-12,-72,-75,-1000,750,-453,447,-461,-301,-232,-962,-1000,-800,-70,54,-925,1000,1000,201,1000,-291,-468,475,821,-1000,1000,-1000,-388,-253,337,-1000,532,-1000,979,-1000,1000,-38,909,-66,461,523,-400,-686,1000,839,1000,1000,557,404,-587,187,339,584,-827,-564,-159,612,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-17,1000,-1000,796,73,-656,404,-2,80,-624,580,-194,-950,958,-138,-615,1000,-1000,-177,-29,-915,1000,-516,-956,1000,-1000,580,758,327,917,-66,443,717,907,1000,262,-463,-67,-666,-1000,789,-513,-760,-893,1000,846,-1000,778,-1000,1000,1000,-611,-238,-663,-1000,265,-180,-622,-1000,1000,1000,709,983,918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("VOID|getInternalAttributes=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setInternalAttributes(int):void",
            new int[]{-418,-1000,1000,368,172,-462,19,269,1000,432,-1000,317,-970,768,1000,-783,751,1,222,-1000,868,512,1000,42,-1000,1000,-1000,-1000,-995,496,-1000,-684,-1000,-1000,1000,-773,-207,-1000,767,1000,116,364,427,181,-1000,-1000,-557,-682,-1000,-1000,-1000,-389,-1000,1000,-422,-40,-830,-247,1000,281,-466,1000,-119,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{1000,-400,164,-77,-379,343,407,71,350,85,-275,-895,-189,-339,1000,365,-384,186,-673,490,486,95,1000,-1000,-824,25,-185,-481,773,-220,-347,-583,-879,631,-1000,22,-358,85,-1000,-921,-138,456,288,-179,148,-629,914,847,400,142,236,-1000,-344,-1000,-154,67,696,-884,-71,1000,540,-298,448,640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{-189,139,394,916,39,-174,-35,89,-63,-462,576,-946,1000,541,469,833,237,-1000,-522,409,-995,-1000,-292,301,1000,248,-638,1000,740,-354,-156,-273,-1000,-4,-216,-400,1000,50,1000,-142,400,104,-461,400,-18,-230,-400,731,720,772,266,-40,346,872,-341,475,-1000,-219,-1000,-400,-445,-485,-647,516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{528,-1000,1000,-659,492,-617,158,-779,531,276,727,-681,-16,-263,1000,1000,-314,-776,-1000,1000,546,-955,-685,-1000,-1000,-450,325,-258,-940,673,-924,-1000,-1000,150,-24,-1000,-16,-892,60,-1000,851,-453,878,1000,163,305,-478,1000,1000,-325,-186,-247,651,-905,814,-172,198,-17,1000,1000,-902,750,-242,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{652,-756,957,-438,346,-979,599,-148,172,-205,727,-459,845,811,-111,841,-163,-788,-755,941,-362,-940,-685,-453,301,548,-285,-117,-303,572,-891,-421,-915,499,48,-908,709,-788,783,-912,419,-647,-320,558,-410,46,-561,362,965,315,-56,337,505,-77,321,172,-216,438,487,823,-984,915,359,418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{1000,-1000,1000,657,692,-55,914,-1000,420,505,327,-1000,1000,-10,1000,1000,-774,16,3,974,861,-1000,393,-1000,511,1000,-232,471,-1000,153,-1000,-262,-1000,826,-1000,-843,-195,266,47,-1000,216,-699,-277,1000,686,-657,570,980,1000,617,1000,-198,126,-803,77,927,-1000,-162,406,1000,206,856,-465,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{-1000,1000,137,-693,-395,-333,998,102,896,1000,223,-709,179,180,281,-873,-1000,-542,809,-1000,1000,338,835,-1000,-1000,-629,226,-1000,-942,476,-1000,758,-1000,-798,1000,398,-535,99,1000,1000,397,730,-502,-172,-427,1000,-355,-416,-1000,733,643,818,116,1000,10,1000,-699,781,239,-1000,26,-253,468,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("VOID|getMethod=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setMethod(int):void",
            new int[]{-202,543,99,-932,-587,-857,-505,-1000,-107,297,17,-63,-578,-278,-336,-771,-334,-840,136,-510,169,-312,769,1000,-118,-1000,576,-17,-20,758,421,927,-585,-533,692,1000,163,-1000,-21,-268,465,-305,1000,97,-1000,859,-1000,44,-743,-462,-879,1000,1000,600,-551,56,-325,41,-1000,-1000,-510,-601,359,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:NjU1MzU=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{86,487,-712,835,557,-925,-263,-449,-392,-377,654,706,-621,116,-865,-476,830,-471,-939,600,-753,-649,-987,-879,402,299,984,-609,-554,101,107,398,645,772,374,-330,-22,740,862,-564,309,381,-689,-739,351,-961,-893,742,-43,-543,-341,594,-570,-311,975,515,-546,-972,842,-404,-941,-738,-575,-296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-562,640,214,835,1000,-1000,284,-19,-1000,-637,-538,450,904,10,-1000,231,544,373,-683,184,-380,-1000,-245,-228,-1000,400,-771,218,-580,-50,81,-214,-489,772,-410,562,-211,-232,-120,-767,-446,958,-689,450,351,-129,463,691,143,-543,1000,-129,-524,-696,-755,-539,1000,354,-233,521,452,57,-263,-242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:NjU0NTg=", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-350,780,-54,291,736,-486,161,-296,-274,-49,909,941,1000,1000,-493,1000,-264,167,37,542,1000,26,-591,-970,574,-687,-378,-341,-505,-1000,-789,-290,331,944,-970,27,-263,780,1000,357,44,-155,-257,1000,487,378,497,408,-146,99,-110,1000,-547,53,-541,-363,606,952,-1000,1000,-437,175,-477,990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-20,731,-617,129,1000,-363,305,26,-1000,-1000,1000,668,1000,1000,-1000,709,776,650,-781,418,74,-729,-460,-26,-1000,-244,-1000,513,-609,-1000,-377,1000,-621,-1000,-1000,605,190,691,862,402,-762,327,-343,1000,1000,-463,430,1000,-900,-1000,1000,413,625,-722,-236,47,-1000,1000,550,1000,-305,-900,-364,-49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-25,1000,-1000,344,568,-961,-223,-968,3,528,793,220,-1000,609,10,-681,310,-688,-897,627,-1000,-1000,-1000,-1000,700,-220,1000,489,-692,496,333,1000,829,175,-176,-658,343,1000,1000,-1000,-128,-230,-737,-1000,489,-1000,-221,673,-242,-1000,-294,489,-621,-323,884,1000,-577,-1000,1000,-337,-1000,-1000,-24,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:OA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{86,935,-712,484,891,-383,67,134,-352,-377,1000,534,527,328,1000,-831,731,-119,-494,1000,-663,-560,-837,-233,-803,-143,-369,86,-278,-483,-273,1000,-285,-15,-201,649,73,706,995,196,-137,467,-371,1000,351,-476,-632,743,-259,-554,352,449,-229,-594,237,173,-555,-89,806,-404,-897,-805,-606,-236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{-843,-561,584,702,-459,-829,585,-259,-629,-734,-824,-183,664,-65,-264,-448,212,-267,815,960,-363,494,-199,-628,-736,438,305,669,-82,-131,542,-869,829,474,763,688,-256,122,-708,-832,993,296,-893,-715,802,789,41,-519,460,166,-309,552,-927,601,885,-15,596,220,-830,-658,585,782,212,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("VOID|getUnixMode=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "org.apache.commons.compress.archivers.zip.ZipArchiveEntry", "setUnixMode(int):void",
            new int[]{1000,1000,-1000,-319,648,1000,44,-123,296,-869,1000,833,-86,-416,-232,-794,1000,-602,-1000,440,-919,-654,-1000,-215,1000,-240,185,490,298,379,80,1000,-650,-344,683,-82,1000,678,499,-354,-662,85,144,1000,-592,-1000,-1000,663,108,-1000,-985,-686,-149,-781,425,1000,-1000,-1000,1000,-700,-1000,-1000,-442,-509}));
    }
}
