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
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "beginArray():void",
            new int[]{252,-670,-467,827,531,-950,965,-182,463,42,-755,527,408,-664,470,401,996,-262,23,-58,-591,447,-152,-426,-630,-641,-359,606,-185,-988,-585,474,524,-910,90,-417,558,101,-765,-116,-757,-604,912,73,-320,50,-310,781,-732,-715,-694,-547,552,109,232,749,895,-382,-571,245,904,17,-41,984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "beginArray():void",
            new int[]{-676,-153,-204,-31,208,-897,188,-879,573,-991,196,368,-561,-980,-300,-400,229,346,47,982,-234,707,-199,-184,839,-613,584,540,-795,686,875,749,211,-141,-180,156,781,292,595,-658,-562,261,-331,798,224,-65,444,308,-87,470,-838,11,-245,-988,-270,-363,-865,221,166,-632,987,538,734,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "beginObject():void",
            new int[]{620,-831,-272,-75,856,59,224,212,-310,-769,-67,974,-962,-7,-370,-436,157,-872,-969,971,-380,-567,-58,-229,-536,407,674,-370,-297,-804,-215,756,-972,-983,893,132,-258,466,-433,-244,-222,336,97,697,-33,891,341,-705,664,-123,781,521,265,-1,-594,204,-764,65,-864,575,831,552,-906,458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "beginObject():void",
            new int[]{-614,390,-692,-383,313,-73,-316,484,476,171,161,-710,245,763,-252,502,-540,281,914,184,502,-121,508,-673,264,-432,-990,-322,-891,-615,567,-885,-443,-168,963,-970,652,-841,932,-352,-249,-745,35,695,-170,-739,-305,449,-708,338,922,-191,759,477,775,-239,-676,708,-345,355,-617,-384,-842,-846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "close():void",
            new int[]{-564,82,-229,712,-688,629,922,-662,78,-997,649,212,970,943,-682,-198,118,221,-676,-867,-489,-138,-301,880,-1000,-382,716,747,975,-850,-473,883,-246,653,741,50,-497,843,971,-930,-467,139,-143,675,210,632,-155,283,375,41,-915,476,190,187,220,986,-393,-238,541,-154,299,153,13,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endArray():void",
            new int[]{-751,-748,-708,766,604,599,-716,-224,119,-232,-947,-479,-629,733,695,381,-924,-247,863,383,-788,-438,792,801,720,993,172,-548,-666,805,138,-657,976,219,576,359,-465,-394,340,-126,-186,629,10,332,987,158,-640,142,981,334,-149,536,-138,240,140,-546,542,841,-138,-45,-130,66,-263,-807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endArray():void",
            new int[]{215,103,-487,580,-747,222,-365,800,921,409,-213,-930,913,-445,892,-174,986,-297,841,-637,-499,-663,-281,-41,-192,71,366,-359,-975,934,528,-347,533,228,700,-202,965,-205,-787,-64,412,465,-882,143,-882,438,-8,308,-979,388,-104,252,157,222,438,-132,177,566,-894,-764,-17,-702,-108,-843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endObject():void",
            new int[]{-342,184,626,186,632,154,-487,584,252,736,-802,-536,468,-529,-770,-24,808,149,924,73,-256,-32,477,-677,698,123,-120,-626,269,490,-437,-971,-819,975,-749,674,-881,291,859,-52,-857,-526,-566,952,-305,284,-891,-944,336,-237,840,403,-682,-276,-699,-406,829,-318,-277,708,-653,-141,-316,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endObject():void",
            new int[]{-1000,390,-143,-980,469,3,-527,748,138,-60,-264,-463,-767,-119,-287,-601,-132,-922,-35,72,-310,2,878,54,-914,633,-305,-146,252,496,-595,358,-660,498,181,54,569,-211,425,954,716,445,-360,480,-297,551,-699,-652,163,473,748,-480,-224,-483,656,-337,965,987,-893,259,-921,720,494,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:JA==", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "getPath():java.lang.String",
            new int[]{-126,-380,698,-484,-492,259,-392,523,-702,121,-931,-294,806,-771,-465,-713,-123,656,-583,845,345,-689,-231,358,-433,997,-897,-207,948,-493,-698,-833,230,905,419,-247,746,793,671,215,65,373,356,834,472,433,599,716,621,973,-145,900,-673,154,913,596,966,-531,773,484,-764,606,-766,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "hasNext():boolean",
            new int[]{848,-924,53,-376,501,-402,-19,652,371,267,742,-364,60,923,-162,864,-870,-928,710,496,-923,-466,-565,550,746,-289,-400,-397,-613,-917,311,-992,288,289,711,144,515,269,-373,184,644,-682,994,-800,607,-377,547,5,-256,898,977,107,-992,-970,-834,359,875,345,473,-534,-11,-969,-913,-630}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "hasNext():boolean",
            new int[]{-917,84,877,-940,273,283,-777,251,99,2,738,177,569,904,-562,-905,-989,121,-543,513,-528,-370,5,546,926,166,645,543,806,345,-318,-589,148,-530,-90,-270,215,577,145,737,-155,664,63,-404,-31,-215,-325,-50,752,-304,-550,-282,-372,74,591,317,-848,2,-760,-931,15,368,-503,-602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "isLenient():boolean",
            new int[]{-776,984,-150,-226,338,-874,381,-643,-365,86,134,653,-405,418,391,-712,-730,-421,334,287,-536,-191,-994,53,270,57,17,-810,850,-335,-852,-519,-911,-432,-541,354,487,-645,50,-163,-497,-315,-740,151,453,-707,742,864,-991,91,-401,858,-617,709,-443,196,349,247,-975,463,-618,-32,-205,-882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextBoolean():boolean",
            new int[]{-636,262,-17,-165,311,-93,271,28,49,192,-742,503,-775,420,-228,54,398,689,-759,-955,-36,280,886,-842,-602,904,52,233,403,-122,839,172,-339,-848,462,131,815,296,857,-191,8,-937,898,585,878,-67,335,-606,-767,-831,26,-162,827,353,394,806,-395,-895,-852,-946,602,-249,285,665}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextBoolean():boolean",
            new int[]{-599,-182,819,-529,-732,332,773,-16,40,-617,-326,904,631,294,122,-117,-262,743,-302,646,-39,-66,925,705,-613,819,647,-884,-239,211,-962,543,474,-502,-312,-885,520,-141,-681,-805,287,361,529,51,-394,-868,-295,-346,-168,927,697,-665,-129,826,-760,-126,-906,-441,-826,766,-179,994,-488,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextDouble():double",
            new int[]{-765,485,-165,-267,669,634,-219,-7,109,-887,-87,194,-73,651,97,-434,718,-229,-931,416,-705,-139,340,-423,-249,-150,723,955,-968,441,571,851,-136,20,752,-373,915,547,-48,275,622,10,976,518,-778,357,375,153,-739,216,-43,-459,59,139,-487,377,452,676,-561,177,496,113,-148,-730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextDouble():double",
            new int[]{775,-612,-758,-476,-573,372,777,-498,94,508,-70,-697,624,-405,-715,-39,522,-589,-913,931,927,-161,-608,946,187,141,-498,313,996,-851,807,586,-526,-752,94,478,601,249,-60,852,-42,523,898,691,865,71,820,53,703,-362,-70,-821,429,-615,-15,-805,-850,201,251,-907,967,277,-78,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextInt():int",
            new int[]{-88,738,-56,-530,692,464,943,-20,-571,39,602,831,944,-361,-686,52,646,-333,-744,-839,-10,-777,-678,-402,742,92,-343,159,-249,372,-903,-956,-17,349,851,-101,167,787,-591,-941,548,400,847,-909,-18,947,-551,-302,-134,-856,-124,-954,-731,362,2,684,-977,-46,959,-665,497,-11,-47,-923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextInt():int",
            new int[]{79,-49,-688,958,-994,-861,788,-948,584,771,-769,-573,400,163,996,97,-343,-532,272,127,479,692,-304,97,856,-203,94,-301,-972,-133,223,-582,165,801,450,24,-230,-480,960,422,411,320,-760,-692,-905,-349,-643,-972,-113,-656,861,-942,216,-877,23,-401,-264,-927,537,938,-451,-313,851,-598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextLong():long",
            new int[]{759,656,911,-467,284,-600,-969,-433,717,348,610,-995,626,-256,132,-733,-342,-214,83,750,-427,-300,750,-717,-675,309,-439,639,-908,-485,-783,400,-16,-375,187,-223,-491,-967,75,319,-149,413,-181,-102,483,-713,723,828,726,590,916,527,-522,703,998,-260,535,278,335,-969,676,-802,-229,964}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextLong():long",
            new int[]{-961,-971,197,-922,961,-869,-607,-313,64,566,-463,-913,581,-845,-565,-386,-211,774,888,-659,109,-971,611,456,321,435,474,160,-241,930,-727,838,-886,-884,-469,-493,899,-839,-219,944,-452,-36,224,308,-390,879,-296,-460,597,59,531,-592,633,-677,844,-358,277,-673,-79,-705,618,789,140,203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextName():java.lang.String",
            new int[]{760,399,888,397,-348,-549,126,-641,-989,-858,-428,-955,644,178,-880,13,416,-457,-480,-531,418,488,256,152,-290,-288,-998,872,100,-63,230,-23,-819,-604,-402,897,-367,620,-216,-694,-329,-634,595,406,430,517,-600,386,63,-649,-843,202,-598,-297,700,468,111,-758,-725,-506,-547,329,824,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextName():java.lang.String",
            new int[]{608,-240,-795,656,486,-454,-643,-198,104,401,-557,761,467,-626,998,738,989,-527,870,-858,832,-349,-381,-507,324,523,-511,376,-344,-507,-241,-294,783,-744,-698,-615,268,-445,250,894,-349,-507,810,277,-127,684,-516,-463,-766,137,-782,568,467,-148,316,-624,-441,-187,-631,587,-823,318,-108,-986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextNull():void",
            new int[]{-885,88,304,-160,-690,-353,17,-963,794,186,-432,-208,-382,788,104,881,586,418,25,-930,130,-941,725,866,-390,376,-909,-916,-861,-927,351,-962,396,-587,258,736,-185,-272,502,-882,962,-840,-222,69,-995,-620,975,-293,726,-436,602,286,715,-478,-270,-67,86,-443,512,-599,360,354,626,-959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextNull():void",
            new int[]{-26,5,-111,-733,331,142,709,-68,212,-937,-937,255,-28,731,-761,-832,-10,570,-297,334,-484,101,-832,437,289,618,-834,-192,66,-640,241,-997,559,-75,-717,-532,184,31,-569,-868,485,150,-504,426,157,46,904,-985,150,772,812,373,-478,-693,197,183,428,-316,-484,-461,-102,206,-181,935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:aGVhZGVy", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextString():java.lang.String",
            new int[]{286,-916,-467,926,-688,134,81,990,-993,-874,-441,-202,378,709,-690,819,910,612,-241,296,-499,-964,899,-217,-320,-178,37,-98,971,911,-908,-766,-94,-667,186,-867,41,534,105,-41,582,-823,-672,728,-282,719,-282,498,-367,74,771,-41,-722,-582,-687,-376,494,-163,-45,784,798,909,-734,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextString():java.lang.String",
            new int[]{-836,-300,-487,-476,-823,-515,363,-138,-262,-797,-393,-107,-806,672,-253,-80,281,-948,666,285,-225,-18,418,787,409,465,-375,-593,-881,695,932,906,-698,-223,-529,803,630,818,101,894,214,206,276,-992,-598,99,-122,10,-625,75,-966,-808,-198,-269,55,-846,334,585,-50,-985,446,-911,-311,390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("ENUM:com.google.gson.stream.JsonToken:STRING", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "peek():com.google.gson.stream.JsonToken",
            new int[]{-780,599,122,-904,-129,585,846,223,12,107,902,737,840,-810,224,-664,583,-942,458,793,448,784,959,-457,700,441,439,-357,258,-535,1000,-514,-188,-650,-977,-573,-127,-560,959,551,-608,-389,-536,-769,-373,-78,499,838,-198,935,-947,970,-246,-658,-71,-74,246,-219,723,-206,-85,-728,504,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "peek():com.google.gson.stream.JsonToken",
            new int[]{329,34,-297,233,819,-906,631,963,-815,645,-399,-43,881,789,646,999,901,-4,976,-96,-290,-307,-614,-51,-497,271,712,295,-433,-405,419,135,105,-482,-810,-893,-683,-469,929,588,746,-275,993,881,962,-491,648,28,-532,174,790,-995,-569,294,739,-610,-734,-749,609,-752,-549,-345,146,942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("VOID|isLenient=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "setLenient(boolean):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "skipValue():void",
            new int[]{-446,-165,-833,333,-304,-614,-900,846,546,-18,577,-941,-899,987,795,-168,-256,-699,723,918,408,287,831,-684,-831,-318,-672,-858,46,37,68,-265,503,-325,178,629,-306,1,132,221,-151,96,-460,-480,-648,-38,209,-845,-936,-456,197,-991,-790,-324,503,-704,766,-485,-177,-340,697,671,339,994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "skipValue():void",
            new int[]{622,-752,933,-965,-471,-677,-618,-452,-991,998,120,-93,142,-895,958,-769,667,872,819,871,-670,433,942,-959,-575,914,323,-393,-537,-974,198,-307,-385,586,264,-383,128,182,-952,-87,272,329,310,-293,-469,900,739,558,722,340,-726,11,-454,246,-610,-390,653,259,393,836,720,890,397,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.String:SnNvblJlYWRlciBhdCBsaW5lIDEgY29sdW1uIDEgcGF0aCAk", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "toString():java.lang.String",
            new int[]{671,-60,493,-816,-469,-579,-107,-444,-7,-307,205,-369,538,883,221,-711,123,-925,706,-719,-704,-517,732,293,-795,816,657,-172,388,-432,398,-569,893,-647,-140,990,541,843,-829,28,966,417,-291,270,-581,167,840,-943,287,600,-285,-337,-221,22,562,21,269,-582,-559,-72,-554,418,652,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.String:aGVhZGVy", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(com.google.gson.stream.JsonReader,java.lang.reflect.Type):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(com.google.gson.stream.JsonReader,java.lang.reflect.Type):java.lang.Object",
            new int[]{963,-108,-670,777,408,-164,-832,-455,374,-959,-356,-166,-73,449,-389,683,-587,183,-101,-410,412,886,-204,-732,-20,-364,753,739,496,-565,-403,101,-352,830,-450,-131,357,302,377,918,-225,723,-894,-266,-131,411,-56,-45,-420,-931,238,971,43,-720,629,-254,-49,-900,-77,964,-230,784,608,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(com.google.gson.stream.JsonReader,java.lang.reflect.Type):java.lang.Object",
            new int[]{-797,-487,-103,922,793,-850,312,644,546,257,-622,714,-148,-540,571,-708,-53,-943,742,-524,-979,-944,784,-454,-406,352,-43,-318,-368,426,813,-611,-897,-457,676,435,-82,494,-349,245,-454,574,267,356,990,744,31,-178,245,-325,-604,243,903,-270,-44,-501,121,-148,-960,-822,841,796,-708,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.io.Reader,java.lang.Class):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.io.Reader,java.lang.reflect.Type):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.io.Reader,java.lang.reflect.Type):java.lang.Object",
            new int[]{-603,296,584,-179,-764,512,59,-347,-255,-24,252,-649,-898,837,-450,119,-778,82,922,902,973,407,347,-799,809,250,501,-138,-709,397,500,399,290,-866,417,763,-370,582,-246,-90,-563,-469,92,988,-495,877,-272,-463,-372,586,278,670,-960,3,-67,-74,850,292,-654,173,668,778,-29,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.io.Reader,java.lang.reflect.Type):java.lang.Object",
            new int[]{-215,-743,-30,-446,-254,66,205,51,854,-210,290,-466,623,206,-241,-86,-979,-678,587,88,-981,-335,-559,601,550,858,-290,470,438,-966,261,-147,-762,167,-251,-384,-132,-911,-201,-747,410,642,-613,130,536,177,391,-455,-390,-531,366,723,762,977,-595,537,914,357,-847,478,105,143,-63,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-178,84,1000,-387,-1000,876,-934,-141,1000,58,-915,-1000,-647,-1000,164,736,-1000,27,-1000,778,-792,189,-631,257,-1000,-1000,949,-1000,-1000,-66,-105,727,478,-1000,1000,-814,-1000,-828,-765,475,367,74,105,266,284,1000,-571,-132,1000,532,-262,0,163,439,-1000,-1000,-804,551,1000,904,-824,216,482,241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.String:LTEyNGUtNTQx", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{594,-722,966,182,-124,-995,-541,-66,371,849,-319,212,-94,-481,-181,-33,155,-160,-533,-741,302,273,864,-372,535,-506,391,-719,-565,-404,-996,-749,67,-229,311,-529,-611,193,-941,-812,425,-694,962,39,616,668,989,704,711,113,-491,-889,625,87,18,-163,-188,-271,560,573,-153,498,-583,301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-699,1000,-621,219,299,146,130,-988,769,-444,355,146,691,2,307,-778,889,791,781,424,609,1000,636,371,400,871,895,371,1000,444,-1000,408,-86,146,-687,657,446,-298,949,-616,59,-516,864,638,-934,421,-446,592,-1000,-1000,-259,-1000,-678,649,400,104,-1000,-49,-654,-1000,1000,-1000,-1000,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.String:LTQyMg==", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-1000,610,-471,-534,-422,-175,763,698,41,-1000,1000,247,158,280,427,-636,-184,-382,-266,490,-397,805,-515,-455,177,1000,32,1000,283,991,1000,-137,-924,279,-278,-129,331,1000,-401,-933,302,-171,969,154,-1000,-726,-1000,783,1000,221,-211,593,-555,239,-313,1000,-593,-844,-1000,38,123,-158,119,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.String:TnVX", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{521,1000,-8,-387,-1000,876,-497,-235,669,58,1000,-1000,257,-1000,164,76,560,27,-550,870,1000,1000,356,640,-1000,-587,940,-1000,1000,560,-844,1000,824,-1000,1000,273,-1000,-1000,-837,-46,-19,301,675,1000,88,1000,288,-532,-1000,-846,-644,-1000,133,175,-592,-1000,-195,551,236,-452,183,-71,-903,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{25,-596,-294,651,779,492,582,914,-543,-840,-78,595,-632,-1,667,-83,96,142,-555,289,-948,-293,-786,-527,-92,-507,-342,51,-390,839,-894,185,-651,-172,778,-511,-303,927,-781,-767,-29,-416,-56,-906,800,-694,501,175,880,292,459,130,-82,-154,-879,888,-80,-521,-819,661,99,-171,-15,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.String:LTMwLjIzNQ==", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-252,-1000,282,-235,30,209,-765,630,288,-330,738,1000,691,112,712,338,395,-563,-341,-216,1000,311,-1000,-1000,200,-379,205,351,-982,544,237,-1000,-1000,465,539,-1000,446,1000,-1000,-1000,390,-516,1000,476,-512,421,468,1000,1000,882,-259,690,-997,237,-1000,1000,955,-648,-1000,834,-277,-387,-725,-381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-38,-1000,-687,-257,1000,151,-713,1000,1000,280,1000,1000,1000,861,-1,-12,1000,291,823,182,1000,664,-326,-1000,1000,-33,302,55,-831,119,-1000,-1000,-1000,338,-1000,716,1000,1000,-1000,-1000,923,-830,173,778,-819,-327,1000,1000,798,97,-1000,-1000,-1000,376,-451,1000,1000,-1000,-1000,535,563,-1000,-1000,-761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.String:Rg==", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{866,293,273,494,-791,21,-449,680,879,169,-381,110,-98,-5,-1000,-1000,-1000,347,-217,-741,287,887,267,321,-266,-972,-50,-1000,47,-857,-908,-739,233,328,-255,-833,-858,-484,1000,-124,22,-117,949,-141,1000,999,491,411,-913,-449,211,-1000,1000,-202,438,1000,-221,280,1000,-414,-777,-143,-587,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-753,310,-1000,-914,-118,-963,371,227,-1000,173,-68,1000,1000,1000,-1000,467,-727,851,1000,113,-726,1000,787,-520,1000,1000,-1000,1000,1000,245,706,-577,-713,1000,-1000,923,327,562,1000,-1000,614,-60,282,832,60,-385,698,-252,-400,-1000,-1000,-813,-236,-1000,1000,635,-590,682,-785,-698,1000,-1000,1000,650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsc2U=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{1000,-598,966,27,19,-1000,-954,459,1000,-1000,-281,-952,243,650,549,-1000,357,-160,502,-741,1000,1000,117,-483,587,-7,-939,574,167,-404,-1000,-1000,-898,978,-416,316,-992,803,-941,125,-348,186,1000,698,616,-454,1000,775,711,1000,-247,-1000,-109,1000,296,-1000,1000,-909,560,961,-153,-147,1000,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{69,894,-745,-558,318,-547,319,-198,-787,-956,850,-464,-463,-775,-681,-788,-941,195,379,444,376,898,361,263,-473,-955,35,-288,569,725,436,946,49,-409,-415,-84,-899,-517,-1,-837,-241,-216,-948,-799,13,-180,414,-501,-914,-831,-885,-817,510,870,-944,-787,-355,221,225,44,483,-899,-687,-481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.String:LTcxMWU1NTk=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-471,1000,-1000,182,-711,-995,559,337,-754,209,784,934,179,718,-1000,-1000,74,759,446,654,-622,1000,1000,-1000,884,1000,391,240,709,726,496,110,-696,977,-1000,180,-13,193,1000,-1000,-164,118,962,370,341,-750,251,-1000,1000,-1000,-1000,-1000,1000,-841,1000,136,-1000,326,-1000,573,-153,-859,921,-139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.String:LS03OTJlODQ4", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{465,607,217,918,-792,603,848,347,284,-659,-20,-876,-994,-211,681,-349,-566,-33,-240,66,-895,471,-401,823,-950,500,753,-183,-253,976,-453,214,370,-301,946,68,512,-682,-981,126,398,371,-496,288,-447,628,-859,-361,859,542,743,123,991,744,-714,-453,-673,-800,696,-134,-386,901,-532,-562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.String:Kzc1MC42MTc=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{2,-996,-75,-795,-750,966,617,546,0,-680,526,-468,725,-441,-651,564,179,-661,-219,269,-899,877,272,-86,343,182,46,425,538,951,571,919,135,277,739,-471,-567,167,28,-340,130,-643,-860,-159,791,-142,732,830,648,268,201,-259,-798,-999,-251,-377,-830,-727,394,131,-451,-66,20,721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{454,220,-552,239,293,-440,-408,-237,942,-215,918,1000,581,910,349,-627,926,-412,-179,-328,1000,744,-919,-719,1000,1000,-1000,665,459,-500,-303,-1000,-848,641,-481,586,540,1000,-148,-812,-224,-236,540,950,-883,182,1000,1000,365,-211,-513,155,44,303,-6,71,174,-439,-773,-1000,614,-285,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-490,-824,460,847,-648,-660,-463,410,-275,576,69,-531,492,640,-127,-601,-777,-925,41,-504,-497,67,361,901,-239,-481,-465,-860,892,109,-848,-973,941,-706,-107,-355,685,500,553,173,-917,-926,-886,-560,232,-56,328,-731,-244,-586,56,-108,326,-984,945,-974,-581,944,937,573,-650,218,-409,304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMGUtMjE2", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{314,1000,-1000,886,1000,-1000,-216,750,-475,-24,184,557,-268,583,-229,126,1000,-1000,-273,741,-1000,-1000,189,-940,-464,106,1000,363,986,-1000,563,775,-1000,1000,1000,-995,197,641,746,363,331,1000,1000,501,-949,-1000,543,-391,-1000,793,-1000,-1000,-1000,-1000,-1000,101,-578,-423,1000,963,-337,247,-107,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.String:LTc1OQ==", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{326,-1000,395,714,-759,-191,642,1000,-468,-21,-891,317,31,624,-948,368,-1000,350,257,-206,117,164,263,1000,-1000,-1000,-1000,693,726,1000,-1000,-250,-698,-805,-1000,-1000,1000,719,-275,-741,-371,-1000,-1000,-1000,532,1000,1000,324,253,-1000,1000,567,718,-489,-494,-1000,-382,1000,-1000,666,-1000,992,-329,988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-777,-213,416,891,-554,-304,-773,-948,52,-261,977,-823,-329,-547,235,-437,-264,-989,468,-660,-860,-833,-371,-620,987,655,974,943,-571,566,-190,-455,622,407,409,-467,-612,-147,-585,-139,296,-515,734,-277,558,-757,111,-171,407,743,-603,-620,414,-842,828,67,600,-87,576,814,-878,-901,276,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-919,-529,460,-523,-541,-191,-747,338,482,1000,593,-1000,240,1000,-267,-1000,-546,-204,-359,-323,-522,448,-1000,166,-614,-818,-465,-1000,1000,-209,-1000,-538,430,229,-216,-355,918,-573,1000,698,-34,283,-823,-1000,-341,-56,-882,74,-244,-337,925,-94,349,-770,1000,-744,-760,-206,937,-409,-766,824,-679,39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-1000,-59,192,79,-1000,-211,-43,280,451,698,881,198,1000,364,-159,-370,-1000,-400,1000,-1000,1000,671,572,831,-1000,110,-369,-185,-564,493,-546,-1000,1000,-298,-1000,-1000,397,1000,180,536,-1000,-1000,-1000,-1000,1000,-75,-1000,493,-3,-822,1000,-234,484,-400,1000,554,-967,539,20,-220,-463,-255,-309,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsc2U=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-1000,141,1000,331,144,-916,961,-690,199,-78,-128,250,131,417,-908,261,2,-225,11,843,803,-44,-166,-660,-1000,1,80,1000,-504,-649,-251,-415,-17,-1000,-795,-881,539,-393,550,-708,555,-13,626,-1000,-214,-1000,1000,164,171,-776,63,-1000,583,543,-416,-1000,301,-494,-76,-208,-617,912,-1000,-317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-520,-400,-120,534,-231,-715,801,-883,-116,-900,-207,-1000,42,152,-889,245,-400,363,260,491,1000,400,429,358,-853,225,-587,-554,-1000,453,-813,102,400,226,-1000,-493,397,-397,431,-451,98,-868,-400,-1000,708,401,-355,-492,596,-844,352,-741,887,392,712,1000,-418,211,1000,-1000,-10,1000,-297,-552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.String:LS02NTM=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{370,989,534,-988,653,431,-12,523,-886,-549,-709,-596,-949,396,-74,-391,504,-473,949,-800,-399,-566,608,648,-854,335,518,720,-726,-905,892,-274,-976,-8,899,-58,-792,533,260,7,131,949,910,269,338,-609,729,-829,899,304,-845,-952,-524,980,831,979,-145,849,327,707,-469,-248,496,-925}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-649,725,837,-549,395,-494,-1000,504,-927,1000,513,-1000,-12,1000,306,-1000,-886,456,-184,64,344,-722,-1000,867,686,840,-358,-1000,-1000,-1000,8,-116,-1000,422,-268,762,-679,-1000,1000,-730,-548,387,1000,-622,860,65,-1000,-4,426,530,549,-278,394,969,80,-1000,485,-919,262,-338,-404,-1000,1000,-974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{1000,-559,-702,594,-370,-153,-196,935,-439,-1000,-134,1000,1000,109,-988,1000,0,747,-106,1000,-1,1000,1000,1000,-194,-1000,0,1000,1000,-51,0,1000,394,-32,1000,-239,-1000,792,-79,375,-1000,687,-791,-863,737,-623,919,-699,-709,-1000,720,-1000,0,106,897,455,38,1000,-463,895,-941,-395,-1000,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.String:NzY2", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-893,-1000,794,442,-766,-200,66,-1000,473,-955,1000,216,270,39,-992,-437,-1000,1000,-140,590,449,250,70,825,-1000,926,278,718,-770,1000,-1000,-812,1000,-706,-1000,-719,-280,-1000,703,152,-157,-1000,-789,-1000,1000,263,-878,-710,1000,-380,227,-157,791,1000,1000,-769,-596,121,424,-771,-1000,821,-946,-305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.String:KzB4YWU=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{310,-1000,1000,-281,174,-170,630,421,-1000,-23,-273,63,-810,221,1000,-348,-1000,1000,342,-221,1000,1000,-543,-159,-46,1000,541,395,-1000,835,-1000,-297,1000,-1000,-1000,-108,1000,-906,195,111,392,525,-1000,-832,508,1000,1000,237,1000,-1000,-452,1000,1000,581,357,-687,-384,1000,-714,-1000,-331,849,-144,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-565,344,699,189,-339,277,-333,406,-468,1000,1000,-116,109,961,-420,-160,14,530,576,-206,494,-1000,124,775,186,451,121,-22,-87,250,-635,-76,188,164,125,489,-40,-89,-496,-208,-371,-400,-400,-1000,593,-537,-230,636,409,-18,423,-312,-209,283,-316,-241,-134,1000,-992,252,-630,-35,-329,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{220,717,-622,-133,32,941,-472,-827,-486,473,816,-6,-320,-938,532,-711,674,17,898,-540,896,699,-330,138,532,-79,374,-627,921,-553,788,-118,187,968,820,968,120,182,2,792,-558,-28,508,787,253,-970,-200,175,-282,760,-189,-223,-113,-201,900,622,880,64,728,207,639,903,697,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-75,1000,321,-929,885,-285,-209,-838,-1000,-277,234,-254,-1000,443,-35,-778,973,1000,423,-902,1000,-806,326,1000,-1000,-157,-244,-1000,-1000,175,-1000,-144,404,-380,-917,1000,226,524,57,-443,696,502,1000,-652,-636,126,611,-9,1000,-28,-272,0,81,-350,-200,-408,-187,327,377,-528,-378,-616,280,-302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(com.google.gson.stream.JsonReader):com.google.gson.JsonElement",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.io.Reader):com.google.gson.JsonElement",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{34,531,1000,975,-800,-1000,516,430,227,993,-579,354,147,245,1000,-789,531,1000,1000,42,-1000,-1000,306,-1000,-407,-86,-1000,340,12,-266,-942,701,-1000,513,-400,162,1000,1000,-397,-669,-1000,145,-1000,867,-14,1000,1000,658,1000,-187,605,-470,-1000,-1000,6,-74,-1000,-939,-680,323,1000,-1000,387,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-240,-1000,624,-170,-1000,-815,-480,1000,751,269,-1000,406,-52,-364,356,-1000,1000,84,-560,-944,221,769,932,-998,-1000,348,-1000,529,1000,-1000,-555,-1000,914,-639,-191,1000,1000,1000,-614,579,-36,-1000,609,135,-467,1000,590,-1000,864,-861,-931,-1000,-353,-1000,363,1000,-157,-1000,-914,-212,613,-575,1000,-415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{949,968,1000,-293,-809,312,1000,526,-577,-853,-804,49,15,-1000,494,-196,-718,-118,1000,130,-768,-173,-71,-1000,-169,-935,-773,141,-483,-539,-352,-1000,-1000,1000,-1000,1000,1000,1000,352,-231,-1000,1000,1000,41,-1000,1000,-110,188,467,-57,1000,-1000,-511,-1000,-970,-421,-642,-49,-353,-860,1000,-652,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{1000,90,-669,875,1000,1000,8,326,742,-604,635,503,132,623,-515,-1000,36,-410,-267,444,-296,625,1000,747,330,-774,1000,645,306,929,-630,746,680,803,906,259,-1000,-444,509,-9,-102,623,-289,-347,-212,114,401,663,-1000,723,344,-1000,-473,-1000,-298,-15,-772,962,-806,1000,-121,-72,-109,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,-824,-691,538,-432,-735,-1000,-210,84,993,833,354,-1000,1000,-1000,-300,202,1000,225,181,72,-1000,1000,-11,-905,-86,308,-343,12,1000,-185,701,1000,-839,1000,-547,-510,-350,1000,-1000,805,-1000,-1000,867,1000,-1000,932,-1000,775,-187,348,1000,-1000,1000,66,-34,-537,633,-684,190,-1000,196,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{1000,-499,401,911,712,457,168,-217,58,-80,-65,-95,983,360,-1000,-994,1000,-12,167,490,-784,1000,674,747,443,-94,268,804,1000,606,357,46,794,-741,1000,-180,7,146,586,701,-101,-114,-401,-650,190,114,67,-1000,-1000,1000,-366,-1000,-655,-1000,455,-241,-128,413,-1000,214,-364,483,583,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonNull", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{522,-453,74,-933,857,221,-78,224,84,409,800,404,243,4,-317,-992,481,496,-356,660,-81,712,658,715,-296,-266,643,-239,-838,794,-262,532,233,918,622,-587,-713,204,110,613,-179,-257,226,-14,-414,406,490,-536,-798,-20,-755,133,493,-525,511,73,-562,808,81,269,899,-465,341,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{1000,-273,-578,-219,-58,405,-736,311,-817,260,-25,1000,-1000,-48,400,286,458,1000,346,-558,1000,400,1000,-702,224,726,-399,-64,1000,-400,1000,-186,755,724,45,1000,-393,-556,-225,390,-645,-950,201,-360,-211,534,-1000,-1000,1000,1000,-611,-400,189,-171,438,145,-1000,263,1000,-23,941,627,480,688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{1000,-557,-859,685,-340,-223,-897,1000,604,-75,-846,-400,-964,266,916,-92,20,1000,-58,90,117,131,1000,-779,-734,874,-1000,-84,898,1000,654,-1000,755,207,-407,905,1000,1000,-239,-517,934,-1000,326,-552,-1000,1000,-852,-1000,1000,572,-527,-1000,-168,-1000,584,934,778,1000,719,-549,378,1000,876,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,-1000,625,171,-653,-182,731,-857,873,1000,238,-1000,1000,-215,-1000,310,1000,1000,1000,528,-7,199,607,-1000,526,-744,-1000,-976,-1000,1000,-189,154,101,-60,1000,-709,-1000,-169,1000,-1000,704,-549,-1000,1000,1000,-1000,-82,-640,-1000,77,959,1000,-124,886,648,-878,957,1000,-355,1000,-1000,-436,-1000,-449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonNull", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{670,-378,-428,434,-910,439,-819,1000,-114,-45,1000,132,-201,-1000,-150,61,499,-724,446,490,-523,908,1000,-1000,1000,777,958,1000,571,282,-427,727,-391,-1000,176,264,-538,-311,607,-104,579,-797,-183,355,1000,-657,-444,-355,-273,-8,-599,496,-207,540,-508,932,-235,391,661,1000,887,23,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{41,-1000,624,422,-422,-815,736,-245,-1000,1000,-1000,-207,1000,-364,266,-1000,1000,1000,1000,-598,-566,127,344,-998,572,1000,-1000,529,1000,271,757,-1000,-497,699,1000,-39,55,449,499,-808,-1000,-1000,-138,-376,1000,273,212,-1000,798,266,-931,277,-597,234,1000,1000,-670,-942,214,436,402,932,1000,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,-543,-68,653,-1000,-735,61,-68,644,993,-405,-394,697,332,-1000,-837,407,211,78,-603,-787,-505,409,-296,-837,-787,308,-526,-516,683,-1000,-325,162,-902,428,-242,-73,1000,750,-672,588,402,-407,254,729,-613,833,-640,-850,-1000,-248,580,-620,67,1000,-493,1000,-284,-1000,803,-1000,83,-20,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{849,-1000,241,-1000,-102,643,-974,-896,-1000,-759,326,1000,19,1000,-608,-1000,-213,-677,-1000,-249,-521,270,160,893,-519,1000,1000,181,54,802,-889,438,1000,-1000,1000,875,-836,38,-3,-1000,-1000,-687,1000,-942,1000,-1000,1000,-1000,100,-513,-201,1000,-1000,1000,-282,1000,-259,77,218,5,-706,688,-503,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-671,-3,-254,1000,566,258,76,397,471,334,396,479,-1000,-1000,603,-402,-514,838,-39,-594,810,-837,251,-813,143,-761,-392,440,-1000,992,701,432,-194,948,-77,714,-158,-663,393,-38,418,-331,528,222,-209,700,-704,-486,-120,-292,596,674,972,798,-513,300,-701,-215,1000,-70,616,-242,-251,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{67,-641,30,-593,-1000,-778,-940,40,385,-613,903,736,279,1000,-110,-1000,-1000,-1000,-1000,-322,-419,-1000,220,1000,-788,323,1000,215,-75,-300,-1000,423,1000,-1000,239,-459,809,735,-158,-1000,706,-241,-489,170,338,-73,1000,-1000,626,-1000,-616,799,-1000,1000,1000,943,-196,-1000,-1000,-704,-325,-117,-893,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-1000,-457,998,115,237,-504,-1000,479,-1000,1000,-1000,379,-708,-144,336,-472,-698,-600,-1000,567,1000,218,1000,-1000,-1000,-758,1000,636,-876,466,-1000,1000,1000,-521,-633,427,1000,1000,-159,472,-856,-686,607,-1000,647,-297,1000,-594,-422,1000,-914,962,420,1000,369,1000,-561,-1000,310,638,811,92,250,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{740,-537,-181,-51,-360,-662,-166,58,-920,445,-939,846,-89,378,290,548,-429,-662,-264,631,-955,-238,52,-14,-735,203,809,510,786,-129,276,96,224,-272,139,800,518,62,-488,-1000,-121,93,-489,-267,814,-578,-82,-225,1000,148,674,407,122,-1000,-95,218,-701,-1000,466,709,585,283,275,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{926,397,-182,-620,1000,598,-872,687,1000,1000,108,61,-1000,480,-1000,-144,1000,-519,-199,-751,538,-1000,1000,1000,-1000,1000,-1000,-464,1000,1000,205,1000,187,-44,238,460,571,1000,-659,-1000,613,1000,1000,-489,402,-1000,-1000,279,1000,-958,1000,-1000,-1000,1000,674,1000,-1000,-8,749,811,-1000,-34,351,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-236,15,715,1000,-1000,246,84,-752,622,168,-1000,1000,393,-163,-276,-101,-163,682,1000,1000,-36,760,1000,-48,44,920,506,197,20,-208,395,890,-1000,-674,38,-568,237,-861,880,-535,-576,277,1000,223,-619,-145,-1000,1000,26,-895,-1000,-618,-510,658,189,689,114,-1000,-389,-862,-199,-407,-889,584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonIOException", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{956,-607,946,142,619,-687,-754,-953,882,187,-841,743,-523,-319,-550,406,-574,-133,990,-967,144,-428,-169,774,-962,-397,563,-250,160,682,780,-953,749,-991,-489,671,809,39,-403,990,-697,-731,160,-615,944,-373,540,694,128,391,-594,370,-891,481,54,-750,167,-576,-603,303,214,350,-430,812}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-430,-21,469,-1000,-191,-718,301,-541,505,-1000,213,742,-82,268,-382,-374,150,-239,134,-499,-270,1000,-602,596,-111,1000,-717,1000,-452,-1000,745,-400,-328,-1000,49,1000,1000,626,-140,159,-927,191,891,536,-267,978,-182,-1000,-327,-160,-328,708,121,1000,170,-400,93,70,-450,-123,-358,-1000,-1000,193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-255,-193,11,-964,-1000,367,-517,-1000,537,1000,-542,40,912,-1000,-481,-380,-846,659,-996,-888,239,713,1000,-749,191,1000,133,732,-661,655,-626,-473,499,-427,392,1000,1000,-1000,-28,372,60,334,466,-917,-1000,724,1000,-250,-938,227,191,-597,-934,-41,-336,-135,1000,103,1000,1000,-978,-275,-1000,700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-237,491,347,446,-482,-1000,-405,-47,-1000,478,-1000,120,-111,-53,-578,376,188,-228,-594,1000,-184,647,337,-602,14,310,1000,353,619,-764,-378,1000,-738,821,-726,-78,-642,-880,813,-255,368,218,-413,-435,1000,-694,293,593,176,-1000,1000,663,-437,-1,-169,503,-596,-1000,-1000,335,-629,580,52,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-354,-537,326,-137,-347,442,67,-399,361,-835,255,427,-563,378,-122,-448,-136,-127,-263,-857,-497,521,52,-767,-212,735,-1000,886,-988,-319,276,-798,-18,-308,-653,800,1000,62,-122,580,-669,628,1000,-254,384,1000,78,-943,-716,-118,-879,360,182,-1000,-95,-746,200,-416,-104,-567,-941,-1000,-872,317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-324,-491,-173,-1000,-881,1000,301,-1000,1000,75,857,-52,603,-1000,-443,461,-1000,1000,-1000,-1000,-705,-38,0,-829,256,425,-1000,742,54,1000,-216,-1000,-249,-1000,-952,1000,448,469,87,1000,-47,1000,1000,-1000,-1000,1000,142,-1000,-1000,-1000,-1000,-1000,-1000,41,-512,-1000,1000,781,1000,-31,-519,-1000,-1000,704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-1000,531,-1000,-1000,-677,1000,618,-1000,-606,-579,-989,-618,-952,-738,-1000,461,-264,-590,-291,886,-225,1000,-426,-719,-74,-1000,573,423,218,-366,-1000,393,-435,670,-44,623,-1000,-1000,-133,1000,-754,-84,-1000,-626,1000,-1000,926,-754,265,442,787,-259,201,1000,-1000,808,-678,-639,-616,310,1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{1000,-283,-104,587,-206,-1000,-459,-842,520,-129,-557,375,-335,332,-1000,563,13,1000,991,1000,-1000,874,1000,1000,23,441,-963,798,645,-456,1000,-653,-1000,7,20,905,6,-1000,1000,-886,-1000,103,507,1000,-1000,-1000,-595,248,1000,-1000,-1000,-468,-658,277,939,79,-1000,-64,-1000,628,875,-1000,-846,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{277,-431,521,484,-591,-447,255,891,588,268,-849,-745,-379,-695,320,-990,540,490,-117,951,383,-691,-364,574,810,78,-527,-919,648,-164,-681,-491,-286,927,-310,-223,-742,539,-804,-770,270,-910,326,219,-476,293,-350,-364,740,-48,-761,955,511,127,533,-691,-238,148,136,376,-589,165,-870,479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-247,-141,-410,-143,-1000,-400,-405,1000,-1000,1000,-697,-781,875,-786,-1000,-400,-602,378,-597,83,310,397,1000,-400,326,400,-880,439,-108,-1000,-983,-684,786,576,-557,412,91,1000,-744,-400,-292,379,716,-1000,-1000,343,350,-239,992,759,-849,805,481,396,-448,-406,-805,448,596,78,-400,838,-1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonNull", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-1000,-3,-533,-714,-1000,-338,-1000,-575,632,391,-88,-1000,434,-415,-1000,53,-57,-860,1000,-1000,-658,1000,-778,622,-1000,-162,1000,1000,-93,-516,1000,1000,363,1000,-1000,741,-68,924,582,919,1000,737,276,-161,1000,472,140,-257,-840,-576,-510,-807,-1000,-116,484,-194,328,-487,153,-1000,1000,-379,752,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{740,-899,-291,1000,-526,-611,68,535,-787,180,-1000,-325,-52,255,1000,470,1000,235,787,951,227,-691,-1000,1000,1000,-898,-429,-919,-48,317,-139,407,-1000,858,339,-707,-327,67,-753,-770,270,-1000,1000,708,-476,773,-670,-1000,1000,296,-1000,385,-288,-930,1000,-1000,192,536,407,-408,-1000,-719,-870,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-1000,903,74,-1000,-559,-1000,393,-13,-1000,990,902,944,-253,351,-575,-42,-936,157,-658,-65,-204,1000,376,-1000,93,1000,558,1000,-696,-480,177,-306,1000,955,-1000,831,-400,-330,356,685,960,657,82,915,1000,620,-694,-6,-748,156,-937,164,204,-150,-1000,294,-950,-24,-330,37,-419,1000,249,882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-1000,-75,-389,1000,-528,856,67,273,482,-272,701,502,306,-1000,-442,-339,1000,-363,1000,1000,205,705,-1000,345,91,-1000,1000,-707,1000,-516,-771,1000,-1000,98,279,802,322,-161,1000,1000,315,-336,874,-873,-81,-924,-1000,-2,-620,-541,-538,-816,-1000,750,1000,-388,1000,839,407,-1000,1000,21,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-756,-503,-75,-575,-883,400,-704,127,-1000,1000,-639,-1000,871,-1000,-1000,-403,1000,-400,80,91,775,302,-373,400,1000,-246,24,-1000,-833,571,368,400,80,904,-1000,-1000,-920,-684,-1000,106,486,-613,1000,-35,287,-400,-400,858,695,-450,-1000,-178,-400,1000,-588,-1000,273,372,-1000,188,400,-256,332,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-55,-435,-354,66,-594,20,-726,1000,400,760,-1000,-1000,589,-1000,-362,-1000,402,400,-671,395,1000,-400,-373,74,340,1000,-999,264,1000,-269,-1000,-638,-447,610,-360,-339,-1000,467,-588,-400,-340,-287,716,-257,-1000,-20,400,403,-68,-365,-827,1000,889,999,219,-210,595,750,978,364,-400,23,-1000,-969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-290,-397,858,-205,-413,818,-559,-352,1000,614,1000,235,1000,-722,-180,544,759,-384,169,-515,-137,-1000,-478,1000,-56,-1000,99,-404,777,1000,-308,784,546,-185,777,-160,-357,-180,-611,1000,1000,-1000,1000,-489,1000,-1000,-351,967,-38,-120,-1000,-472,-21,1000,972,134,426,371,437,-554,989,337,768,-853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-70,315,510,-916,-1000,-527,-570,619,-1000,1000,-980,-687,473,-1000,-400,-506,-288,580,-724,-36,755,-376,1000,-151,-400,1000,-1000,-47,-248,-287,101,-281,815,1000,-972,982,-287,141,-648,-1000,315,553,-1000,1000,-1000,-400,-137,528,966,-180,-1000,594,276,-74,-100,-291,-460,-208,-354,-302,-1000,1000,400,315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{1000,209,-101,790,1000,-1000,1000,-407,1000,-1000,1000,1000,-981,183,1000,39,1000,1000,307,1000,-590,-1000,-554,-1000,1000,1000,766,-437,-356,1000,-1000,-682,351,-671,1000,-193,-53,-1000,1000,-1000,-1000,1000,-1000,1000,-1000,1000,1000,1000,-513,739,1000,357,-1000,1000,-1000,1000,-344,-89,946,1000,-1000,-245,-1000,-589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonIOException", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{389,197,-174,-522,-798,-1000,-1000,-118,594,371,980,360,-700,-1000,-1000,979,-599,601,1000,-942,-453,51,905,512,509,-498,443,680,-446,375,-78,-868,634,-407,334,-1000,1000,-798,-1000,-313,767,-1000,139,-1000,-982,443,90,147,-1000,990,-1000,1000,963,-303,-590,-766,-1000,956,1000,-871,1000,777,1000,-902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{1000,-129,230,508,-487,327,-1000,-1000,1000,-367,978,1000,1000,-197,-495,-317,486,-271,462,-924,-961,669,-570,694,223,284,17,-45,-796,225,557,828,-73,427,1000,-1000,814,-630,685,-93,848,-1000,1000,156,-96,-52,-154,-677,659,914,-1000,186,-621,-210,1000,-1000,-528,231,1000,-226,-1000,-543,-210,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{731,937,-118,714,620,-400,-422,200,864,-740,1000,828,-164,-319,1000,-144,1000,1000,-78,1000,824,-1000,-457,-1000,1000,1000,916,-1000,-1000,680,-1000,-481,-634,702,568,-106,-1000,71,-317,-524,365,278,71,1000,-485,1000,459,659,-1000,-1000,906,498,401,-289,-1000,1000,1000,123,315,1000,-745,-390,40,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{93,193,1000,-1000,366,-188,67,-808,790,452,1000,124,306,-937,626,-262,1000,448,-746,1000,84,-1000,331,345,1000,-292,-371,-1000,-1000,-516,30,324,645,847,-92,-487,-992,665,1000,1000,935,284,591,604,-81,1000,-1000,-713,494,-576,666,728,535,108,-382,727,-123,-1000,461,901,-100,-379,-1000,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.internal.Streams", "", "parse(com.google.gson.stream.JsonReader):com.google.gson.JsonElement",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.ArrayTypeAdapter", "com.google.gson.internal.bind.ArrayTypeAdapter", "read(com.google.gson.stream.JsonReader):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.DateTypeAdapter", "com.google.gson.internal.bind.DateTypeAdapter", "read(com.google.gson.stream.JsonReader):java.util.Date",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.SqlDateTypeAdapter", "com.google.gson.internal.bind.SqlDateTypeAdapter", "read(com.google.gson.stream.JsonReader):java.sql.Date",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.TimeTypeAdapter", "com.google.gson.internal.bind.TimeTypeAdapter", "read(com.google.gson.stream.JsonReader):java.sql.Time",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.gson.internal.bind.TreeTypeAdapter", "com.google.gson.internal.bind.TreeTypeAdapter", "read(com.google.gson.stream.JsonReader):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
