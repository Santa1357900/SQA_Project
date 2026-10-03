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
            new int[]{-416,939,743,-279,-28,266,11,877,-25,-942,-517,-884,602,-686,-696,775,387,-941,-354,957,930,-563,-476,952,-94,202,657,-230,-918,-560,-161,91,616,220,-59,909,913,522,-410,-992,-330,-712,796,-993,377,711,385,-805,-478,-993,940,-402,-1000,801,142,-163,-157,13,-95,-839,-746,655,492,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "beginArray():void",
            new int[]{791,-886,-942,47,835,28,974,-227,-872,-802,330,16,606,-631,432,-954,-857,561,741,-608,183,-135,-694,-449,-312,-508,-549,-235,-839,580,-636,827,190,-836,-83,922,-35,328,-91,-716,-387,-916,-758,-869,-214,-222,-967,-236,938,-53,-428,971,998,-544,204,-36,712,-515,-289,-40,853,464,-424,194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "beginObject():void",
            new int[]{-416,866,423,336,726,159,-397,-963,-825,295,971,-546,693,-711,-585,-536,753,721,-137,-411,789,-370,-221,-714,956,-395,150,787,-252,130,-96,85,242,-364,-230,963,-106,423,-583,-424,-145,651,383,40,567,-71,802,-285,-928,681,187,434,-132,-570,-321,792,-552,429,-949,796,704,636,504,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "beginObject():void",
            new int[]{-622,924,68,729,-387,605,768,-672,-139,-10,-784,198,-528,97,161,-870,221,619,750,-15,-378,559,-338,418,318,-241,-113,188,588,527,-201,810,415,-960,196,-351,749,849,848,-845,599,-114,749,875,-588,-325,-238,112,865,-868,-391,688,574,-726,-692,90,567,-559,15,-493,-633,242,-983,723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "close():void",
            new int[]{472,764,-872,-461,-672,-324,-597,616,742,-483,201,273,-878,813,628,430,-336,-177,-879,-439,468,-645,136,190,296,585,-389,518,-553,-614,502,-590,-240,-321,249,288,876,345,189,-249,-497,-625,580,-812,689,-921,176,59,869,-563,119,76,-182,959,251,66,512,-217,-68,-42,422,179,927,-970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endArray():void",
            new int[]{172,989,-111,125,-633,-292,-398,984,713,881,541,588,-609,510,835,922,-776,711,64,966,966,138,-696,385,729,997,-243,824,29,-124,-420,258,-690,803,-514,743,885,117,-787,-69,711,11,-905,-276,-370,945,-437,-408,-685,513,690,-786,482,-195,-379,-617,691,-188,124,-635,472,34,-635,-694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endArray():void",
            new int[]{771,193,630,241,-434,-766,480,446,-248,-46,420,-91,-801,565,825,164,396,-324,768,506,12,927,-927,-494,61,642,-717,-177,-372,-39,-859,-562,-500,-925,-903,423,-925,-649,949,-725,-524,-539,769,952,938,-100,-35,-165,190,709,508,990,866,-343,-816,404,721,666,-967,-910,-129,822,77,-620}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endObject():void",
            new int[]{113,984,-960,-327,-520,577,751,729,-950,396,179,-987,-620,-176,-674,220,-564,186,956,-462,742,-410,-110,-799,-630,-798,284,-828,-628,-592,654,458,-193,304,-259,498,195,-649,762,-430,-111,134,-949,220,-322,-302,671,361,706,137,-847,845,744,818,243,111,749,893,217,-220,-294,-389,169,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endObject():void",
            new int[]{-328,709,-306,-955,359,-200,-290,855,292,201,-374,-443,-119,538,-886,724,-550,388,-214,421,856,803,-534,681,-588,872,658,849,936,772,-325,-262,306,684,-744,534,-438,412,920,845,-731,585,-208,376,674,900,187,334,-37,54,855,-137,-142,-306,441,-457,-271,-970,488,317,-571,-412,-928,92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:JA==", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "getPath():java.lang.String",
            new int[]{-111,903,323,724,-10,296,598,563,44,-398,835,516,872,-616,353,826,575,-337,135,542,173,447,-832,8,-863,-816,-727,-86,935,-721,-158,536,-696,435,729,948,-465,-245,411,711,-246,329,-478,-175,923,-697,856,586,-959,331,22,-616,963,-255,86,-547,89,-230,721,-868,241,-309,-319,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "hasNext():boolean",
            new int[]{826,693,-361,385,-960,-671,175,869,975,-940,-225,-807,-427,-831,583,-744,-669,-402,224,-691,335,606,497,973,637,-439,721,154,560,948,-73,787,-341,504,304,650,920,596,424,-930,-440,-537,607,-761,-687,-294,541,-936,-572,-418,724,30,796,59,664,-652,-424,-668,-119,854,-486,569,704,558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "hasNext():boolean",
            new int[]{-55,986,486,98,-157,-819,-652,-413,-49,35,247,688,524,545,-474,-559,-695,641,-54,214,385,-627,132,674,608,160,72,241,484,-467,209,-631,-307,685,-69,-758,-830,-699,-654,97,477,670,97,-740,733,-158,674,-803,-278,755,-797,-563,934,-339,-7,4,320,938,-725,211,-700,-576,503,-919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "isLenient():boolean",
            new int[]{355,-772,38,-854,796,-247,-757,252,689,-183,849,-703,717,838,-434,555,217,-560,650,607,-965,947,-576,139,-618,609,129,231,-736,987,657,991,847,718,331,404,-923,-397,-149,-948,260,-593,-258,60,927,-936,213,993,294,-932,-806,-242,891,785,525,-403,-491,90,718,-856,555,-666,580,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextBoolean():boolean",
            new int[]{-359,3,989,-78,-783,72,-378,857,-839,-779,515,-590,-622,869,473,-909,-302,-560,396,-82,184,495,-525,-645,-129,-413,78,-863,-95,869,-882,397,-818,863,51,361,-390,588,-235,-974,-123,256,582,-295,-780,-836,-643,-915,104,-962,-794,545,969,131,816,868,-801,-355,-537,-784,371,-316,866,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextBoolean():boolean",
            new int[]{208,-469,-946,330,268,810,144,-928,766,546,-704,-780,951,227,-102,-724,-452,-710,104,-808,847,992,-958,-462,-580,924,-743,-797,-109,383,-840,753,-655,270,596,427,-36,-118,-230,-70,226,-920,710,-415,653,-372,-779,-749,479,222,-158,440,848,-306,718,507,188,202,-530,-513,284,824,-798,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextDouble():double",
            new int[]{546,731,-743,72,-64,-454,-448,-20,-741,174,769,-739,361,105,-156,-690,520,-651,-21,591,127,195,-921,699,-8,-322,-715,-931,285,-48,720,183,-166,699,-580,512,-57,-35,56,46,-789,-651,481,-738,921,353,-586,618,494,884,640,280,-785,380,980,673,9,854,-997,-341,-635,-737,940,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextDouble():double",
            new int[]{750,348,-672,971,-88,183,643,-383,-87,204,232,-781,-523,-399,517,690,856,-485,773,902,264,246,-203,-77,953,-374,215,-249,791,-673,926,-472,-104,-144,-18,530,349,193,-148,911,-707,612,451,-879,845,-916,599,193,-854,829,-392,691,625,-888,-371,16,60,-854,42,-47,412,113,-286,905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextInt():int",
            new int[]{135,417,-265,-872,219,221,682,-731,1000,-969,368,604,574,821,-553,994,173,-293,-491,-700,918,119,918,-841,222,-99,-812,-767,435,473,76,372,499,-586,11,898,643,-338,-960,554,401,-102,-13,-393,-356,608,230,14,706,-158,-843,637,-808,-262,-850,62,-404,-505,783,748,-538,706,-210,954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextInt():int",
            new int[]{805,-482,-967,375,958,711,-823,-463,761,-921,25,-701,-57,-151,199,-596,-918,-776,532,-95,-900,-475,113,569,-428,-446,6,475,-653,156,-337,110,-886,236,-432,-948,809,-703,119,-334,-969,932,-635,-847,-964,-341,843,766,251,971,-526,33,-216,-453,227,-574,925,-790,-879,-923,-750,376,995,-149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextLong():long",
            new int[]{-655,978,-222,-2,255,-860,197,136,797,-712,993,251,808,-967,198,184,-874,-234,-863,388,917,-759,571,1,913,-239,930,-991,460,-614,-255,755,-970,259,-927,3,87,-94,-920,796,-923,124,789,-602,-287,-21,-545,797,-316,315,19,410,466,-811,-326,-88,0,705,-304,-537,-334,-219,-639,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextLong():long",
            new int[]{185,634,-929,427,907,918,-438,-840,-778,831,-488,835,-187,802,737,-598,880,837,735,341,532,-941,337,-430,-864,561,-94,167,545,252,669,-63,889,-894,562,814,-141,-82,237,-879,274,488,799,-232,-850,-985,255,-379,-597,397,663,-510,-66,-857,538,435,853,613,-799,424,328,-552,-558,-220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextName():java.lang.String",
            new int[]{388,603,-693,-744,215,-130,628,639,-736,827,865,503,-409,30,603,77,16,-6,248,395,-68,141,-688,-370,-184,553,959,966,489,650,-259,-76,600,190,342,24,515,-855,-736,452,898,-29,-128,-987,-398,501,-21,63,-801,314,242,338,-560,-208,-954,-348,278,-735,-737,263,-321,-314,293,411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextName():java.lang.String",
            new int[]{-847,-967,-135,825,438,-278,672,-6,211,612,283,-675,-993,907,-77,-226,652,54,58,-616,-21,741,836,-620,666,-243,-79,976,445,794,390,-726,-633,-427,201,-420,548,-204,-94,-682,-125,425,738,-753,-464,480,965,-820,-117,-818,518,-433,-810,613,266,-612,-893,-722,-935,642,-715,-45,693,372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextNull():void",
            new int[]{200,-372,627,-974,87,-859,214,-92,-814,450,168,252,-915,-312,-147,-836,-389,-345,451,172,410,-893,-663,545,470,341,471,755,166,-872,-327,632,954,-292,443,949,-537,-279,903,-130,-608,-284,-610,989,361,-799,-772,-464,-330,53,318,-276,778,561,290,-808,-770,-617,-786,27,755,240,-920,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextNull():void",
            new int[]{-6,-884,-974,-418,771,-177,-942,-241,-835,-637,881,251,480,-777,-616,103,777,-102,156,-907,901,-708,-642,296,-338,159,806,265,204,-391,-448,-665,110,304,76,707,-568,-828,102,-310,-508,-790,-14,-231,-948,-720,670,10,-784,-345,259,714,351,-478,410,-793,-848,664,964,-801,54,-924,468,-789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:aGVhZGVy", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextString():java.lang.String",
            new int[]{-590,-288,-904,-24,140,304,80,-916,174,133,684,-443,-51,-963,-739,31,388,121,34,-640,259,-760,-730,655,58,-679,328,476,-622,-328,931,85,-624,527,678,-871,-836,270,-914,-488,750,986,255,795,-519,-682,-382,-15,-126,-48,-801,255,159,479,282,834,921,868,479,62,-197,345,-218,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextString():java.lang.String",
            new int[]{376,-976,-853,4,-602,655,-320,-441,-914,-186,313,-969,67,639,-786,40,812,-784,-250,-648,-421,-651,-135,-565,-97,-984,-853,-964,651,225,-34,277,314,-933,-209,162,429,-10,9,-139,822,488,424,621,-316,-234,-47,782,416,-764,996,-41,658,192,-535,214,921,427,496,-227,870,-825,-474,678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("ENUM:com.google.gson.stream.JsonToken:STRING", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "peek():com.google.gson.stream.JsonToken",
            new int[]{-718,-494,-599,770,686,-905,-8,-3,72,-127,190,751,488,987,-634,-785,-692,308,160,136,-907,-902,453,808,-357,904,860,-864,40,836,344,431,765,-789,501,334,-855,-308,-98,-678,309,531,-563,-391,604,271,51,-44,976,385,-519,-125,243,696,643,35,332,-830,-832,295,-383,-340,-735,711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "peek():com.google.gson.stream.JsonToken",
            new int[]{-79,229,-649,-106,647,-668,1,-137,625,985,-809,-56,554,489,22,-304,699,999,-737,-894,-789,-222,209,-231,-40,-692,-321,-48,10,494,578,770,-822,50,454,477,266,-729,227,903,495,905,-72,460,428,584,-348,-187,244,-639,192,-694,617,314,834,-469,633,885,-332,67,209,-357,439,339}));
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
            new int[]{711,108,-424,-526,218,-123,15,-731,-409,-904,-170,-517,237,-263,-648,-168,-720,113,59,-657,945,637,825,189,835,-680,518,-364,949,-520,-441,738,706,-998,492,587,-801,419,612,-882,465,65,281,-268,599,600,672,-663,-737,322,365,-191,314,843,-502,-702,-931,-36,962,368,-908,865,517,496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "skipValue():void",
            new int[]{185,-489,201,-619,-627,-663,-887,-375,-536,-409,801,-630,390,-356,490,861,-7,608,300,363,-822,-271,259,-977,183,-90,115,205,-234,291,-504,401,501,663,-587,928,-642,414,-920,-124,-738,-295,625,-5,444,610,367,558,18,-934,-980,-358,485,665,469,-300,608,541,-464,-181,-527,-88,-435,667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.String:SnNvblJlYWRlciBhdCBsaW5lIDEgY29sdW1uIDE=", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "toString():java.lang.String",
            new int[]{180,140,-86,-507,-197,475,-192,926,247,-290,879,-842,529,-591,-531,883,93,153,-833,-342,405,-383,-849,933,476,-303,776,167,-196,-958,352,-191,-671,-496,869,957,-23,-516,972,904,-889,-101,-15,438,-223,-359,925,774,30,721,-427,-713,-188,-909,443,-138,870,-924,217,-439,-988,-235,-330,610}));
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
            new int[]{-149,865,684,172,-166,405,-295,-463,-435,878,-135,-296,889,61,-953,522,533,-169,196,236,-898,9,-115,-843,307,329,99,-52,-111,-99,514,816,103,-209,735,-800,91,-882,-800,298,977,414,-273,-780,-897,-110,80,-574,-524,735,-675,-335,180,609,247,671,-708,77,-357,-514,676,887,920,-497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(com.google.gson.stream.JsonReader,java.lang.reflect.Type):java.lang.Object",
            new int[]{798,362,-902,-834,-606,131,471,-650,602,-321,-418,923,-928,52,-827,883,585,-395,-884,-113,-603,144,880,947,381,44,73,775,764,840,-814,692,-316,990,-153,232,-764,305,130,442,171,881,-246,-806,-969,-712,369,-184,-887,216,-429,814,-629,-157,-268,556,-848,-238,331,374,446,-524,-965,771}));
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
            new int[]{710,229,675,591,227,-611,622,-64,-504,-759,-752,81,437,-739,-166,-954,989,744,-586,-516,-945,-449,-318,-175,-450,677,313,-789,118,399,329,104,761,-910,199,239,-511,648,400,46,-101,946,-659,600,-446,-317,742,721,14,-383,-177,-74,396,248,-987,584,489,-249,-759,-121,585,-834,372,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.io.Reader,java.lang.reflect.Type):java.lang.Object",
            new int[]{-540,711,-175,775,-797,-580,26,-114,-15,284,971,-772,-748,-460,-118,-944,345,854,-288,139,-933,733,80,-702,-644,730,117,840,874,711,645,807,77,-172,-858,-280,118,-349,-973,-998,-204,-593,924,152,-298,249,-211,321,676,43,44,-958,-235,296,-810,477,-48,542,756,-178,135,728,-68,-741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-1000,591,-1000,-307,560,718,-530,-101,954,249,746,674,1000,402,1000,-1000,-1000,-1000,-1000,-1000,972,-244,463,-650,-405,425,-1000,993,-765,969,-1000,1000,-201,-235,1000,1000,68,-727,793,286,-5,1000,-961,311,-653,869,203,864,-1000,394,1000,-925,-1000,-395,588,1000,-19,-398,312,764,-1000,1000,-748,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.String:LTM4N2UtNzM0", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-1000,-400,-450,774,387,437,-734,541,860,686,-404,-192,688,311,71,270,873,-533,-469,145,1000,1000,943,-500,-1000,291,-762,593,276,214,163,1000,-400,333,137,1000,873,616,5,-659,559,-967,-106,479,140,563,-1000,-404,3,-318,395,114,479,58,588,-400,-10,-447,-782,-156,440,1000,-595,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.String:OTc0", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{369,356,-162,474,-974,-164,-305,538,571,-156,439,667,846,386,-1000,-861,975,32,-396,-479,-628,66,-482,-197,-553,27,-166,544,847,521,352,1000,-451,-1000,-265,775,37,87,709,-1000,406,10,-458,207,20,-404,-456,1000,209,-654,1000,-85,-159,407,-1000,-1000,-43,310,-1000,-1000,351,-137,-713,-247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-306,354,-41,955,426,126,1000,-62,-932,-491,-1000,-1000,-629,-1000,1000,1000,-1000,-1000,499,1000,844,480,-405,58,-811,-3,-957,-1000,-338,614,-1000,165,-482,-164,1000,-250,-1000,-15,-448,233,-752,-304,371,985,1000,367,-1000,-83,-320,-160,-702,-1000,-982,-831,-270,1000,-106,-459,410,-19,176,228,964,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{677,613,-464,443,-1000,62,-338,860,1000,95,-553,643,1000,325,-1000,-1000,1000,-768,668,-118,-99,347,-618,-200,-375,1000,538,779,1000,1000,1000,708,-199,-1000,48,439,882,-568,-162,879,1000,-972,-798,166,1000,-122,-11,1000,-277,-82,275,257,184,803,-1000,-1000,-517,-45,-1000,-1000,21,390,-941,-62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.String:LTEwMDAuNzM0", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-27,1000,537,373,1000,-251,-266,-505,378,382,746,-448,-434,1000,314,443,803,-639,-1000,-1000,1000,326,686,-988,1000,1000,-71,293,102,-739,-467,-1000,986,-1000,-19,822,501,-852,263,1000,774,973,-1000,-1000,-352,459,1000,-171,-165,1000,541,1000,163,730,1000,1000,543,-471,-1000,1000,-1000,-202,-853,-247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.String:TmFO", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{1000,-1000,649,459,-1000,-793,-452,1000,-842,-177,-142,1000,1000,298,186,-929,1000,600,120,143,-1000,-615,-95,-328,-1000,-997,-1000,-507,327,-250,862,595,-1000,-570,-450,-1000,-837,711,1000,-827,-710,-1000,421,577,733,-192,-1000,1000,-26,-1000,522,115,-873,-261,-988,-696,-251,1000,-553,-1000,-1000,-969,-798,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{938,993,267,845,510,-1000,-684,921,-780,-163,-54,422,682,-400,-51,642,1000,-625,-533,-1000,-225,-435,1000,-1000,542,361,-565,451,1000,340,507,-1000,629,206,-675,-808,-1000,-1000,1000,1000,764,-151,-961,-566,1000,385,-1000,1000,834,-791,1000,1000,-272,967,-434,133,-582,188,-1000,359,-377,-500,-1000,-109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-408,170,162,706,539,1000,16,434,293,651,-52,127,1000,1000,70,-1000,914,-1000,-1000,-373,722,349,1000,420,-702,-1000,399,49,-260,1000,-1000,692,583,500,82,-808,-1000,60,276,594,1000,1000,-883,-547,-1000,-406,203,669,14,939,1000,-1000,-150,748,1000,-161,-487,-1000,-165,-783,-91,426,148,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsc2U=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{826,-197,403,603,561,540,926,1000,888,-431,12,1000,846,715,-473,-1000,-1000,-800,-85,38,-1000,-334,738,-22,-1000,550,-865,-130,141,-609,-1000,230,16,-812,-282,-556,662,470,112,1000,-142,-1000,-479,92,-552,-1000,448,393,-1000,-576,353,-78,-412,-391,1000,1000,269,-211,-667,-1000,760,-1000,-1000,-195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:LTU2MWUyNTA=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-33,804,-1000,742,561,785,250,-666,907,-540,396,871,455,-228,1000,-1000,-1000,-1000,-1000,-432,1000,666,738,-375,-162,696,-865,-237,-1000,-723,-1000,-32,198,366,479,1000,-1000,588,-238,785,-410,1000,-561,-746,-552,537,-187,184,-1000,1000,1000,-78,-1000,402,1000,1000,115,-302,530,81,-1000,422,-791,152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:LTEwMDA=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{1000,-747,-587,787,-1000,-252,-142,1000,-744,-981,743,1000,1000,121,-628,-1000,995,1000,43,169,-1000,338,-759,-198,-1000,-958,-971,13,-55,214,902,1000,-1000,-1000,-87,850,-1000,824,1000,-1000,-857,-933,466,722,-823,-368,1000,1000,199,-1000,684,-261,-814,224,-1000,384,-443,1000,-20,-1000,1000,-693,-316,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-130,-1000,269,-529,-561,-1000,-809,1000,-790,-67,-66,-384,178,873,-231,-1000,520,474,-316,-408,-1000,-714,-228,-404,458,-601,-575,225,644,-40,297,-94,-531,-210,-1000,-351,7,627,1000,732,-101,-1000,-114,-1000,571,-45,347,-203,593,-1000,580,1000,7,-477,-786,-873,568,704,323,917,1000,-603,-792,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-443,-426,-1000,414,580,-1000,-290,1000,-642,-1000,308,-1000,-762,1000,780,-820,1000,-965,-1000,-647,1000,1000,-389,-667,1000,1000,330,342,462,-1000,-1000,-1000,1000,-1000,-907,897,180,105,1000,-530,681,20,-58,-1000,121,-377,1000,317,-997,-318,602,1000,1000,-613,-516,-243,1000,260,-407,1000,815,-274,-743,575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDgwMDA=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-125,-647,-1000,1000,-796,155,-217,-1000,1000,-283,260,410,-499,-360,271,-79,1000,-932,460,1000,1000,-452,-682,316,129,973,-480,-949,-1000,-198,-893,749,-1000,-904,-720,1000,-37,1000,-1000,-1000,-367,210,904,-776,368,997,-1000,583,-962,1000,141,-402,-539,732,495,95,-809,997,953,-1000,-200,1000,46,613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.String:RTY=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-1000,-1000,385,621,1000,736,-848,-954,1000,1000,-496,1000,284,-343,885,1000,600,-546,-1000,421,1000,1000,1000,-1000,-770,346,-1000,986,-225,180,-286,1000,-469,1000,1000,1000,1000,1000,-215,-1000,665,-1000,-758,499,-10,1000,-321,-1000,672,-883,389,566,218,685,1000,162,951,-1000,-581,608,-769,1000,-506,-461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.String:LTEwMDBlLTMyOQ==", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-1000,886,652,-906,1000,-15,-329,-372,-118,1000,286,251,892,33,1000,-485,386,1000,-60,973,-348,754,-400,1000,-157,1000,-502,1000,1000,-300,-175,810,-1000,4,557,-298,-657,844,323,-1000,-351,1000,-122,-995,1000,-121,855,826,-80,-378,479,-1000,-841,-652,402,-751,-56,1000,-300,1000,-483,943,890,-821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{956,-320,750,591,856,1000,-560,123,92,1000,860,686,-199,57,864,-646,339,1000,-75,496,-462,-927,-672,516,607,5,-305,-397,429,1000,-1000,-586,-1000,-532,-729,436,435,642,-3,45,-575,462,-534,-816,410,678,772,-62,-546,-700,487,-204,-450,-78,317,-529,-15,654,-590,200,354,332,-311,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{706,-1000,109,539,-436,363,-1000,759,-126,-810,-1000,-1000,-1000,-1000,-646,1000,-93,-724,-243,-285,738,-1000,1000,811,222,-994,-180,-1000,936,1000,1000,-951,-359,1000,-375,1000,-1000,-1000,-782,-80,-960,41,620,629,-1000,383,-148,590,270,519,-1000,-669,284,1000,1000,588,148,1000,641,-1000,1000,795,-10,-499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-1000,-1000,-1000,330,981,-644,-1000,1000,-111,-1000,-1000,-427,-378,5,-1000,495,1000,-452,1000,566,1000,-1000,1000,178,1000,325,736,366,111,-159,674,-729,-156,1000,-422,-711,-1000,-704,-1000,109,349,1000,1000,-1000,-601,1000,-605,-1000,861,252,323,240,971,571,1000,363,1000,-539,712,-107,1000,530,36,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-68,1000,1000,139,-192,404,397,-302,-1000,327,977,21,1000,506,-150,-1000,-1000,-360,-49,-85,-653,976,633,1000,-649,587,-29,1000,625,-209,-43,-750,128,-1000,105,1000,251,-81,-920,91,-313,285,-30,-240,1000,-251,662,1000,280,-710,1000,-444,-158,784,351,-1000,-1000,969,-1000,356,-1000,958,1000,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsc2U=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-1000,399,891,-853,116,1000,-49,-6,-604,833,418,-91,1000,-743,-411,-632,66,579,276,289,-413,373,332,1000,-205,233,305,1000,-196,-893,-33,378,-255,1000,-548,168,-53,-150,362,-680,-452,455,291,-380,1000,645,714,1000,37,-164,568,-348,-284,-394,373,-275,-306,545,-227,1000,-934,438,452,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.String:LTYyMS4xNDk=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-161,1000,735,133,-621,901,149,240,-678,683,463,1000,1000,210,303,-1000,107,1000,-1000,138,-804,423,-1000,1000,1000,562,1000,1000,238,-879,-1000,514,1000,93,906,982,104,-748,982,-402,953,-103,-5,-25,1000,-110,529,1000,1000,-595,1000,-353,-1000,-1000,-251,-1000,-922,202,-1000,1000,-539,457,617,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.String:LTUxMw==", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-1000,-447,23,-262,513,597,-858,222,359,-413,-91,162,870,123,-354,-384,1000,396,596,1000,901,321,-306,-352,1000,717,611,1000,310,49,-730,663,-131,-690,-718,-1000,-691,-865,905,-872,719,1000,-577,-1000,799,268,833,445,500,792,353,-717,-878,-388,488,-317,39,1000,114,1000,625,1000,-572,777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDgwMDAw", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-8,-1000,738,-1000,-622,-729,164,0,1000,95,464,541,1000,-333,46,-718,-905,-146,1000,-512,867,393,-636,1000,458,-588,694,-1000,-913,-920,394,-363,-941,-50,-463,472,903,-765,-1000,689,302,480,-67,-158,690,307,-686,795,770,879,699,-94,603,-286,-376,-956,-668,-24,-811,212,-1000,511,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-1000,1000,180,34,-1000,68,-1000,-891,-353,-1000,716,-416,-91,457,1000,-723,-787,1000,-425,-155,-900,188,-1000,-73,905,115,972,583,-328,-361,542,8,-860,1000,-645,1000,906,514,226,1000,221,1000,430,-363,914,444,-490,484,29,-299,413,-1000,-1000,48,-1000,-1000,89,143,-910,322,-193,711,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.String:KzEwMDA=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{448,1000,1000,682,-1000,518,348,-651,-1000,1000,1000,864,1000,-150,400,-722,-1000,-171,-37,-731,-1000,1000,291,1000,183,151,493,1000,147,-1000,-462,750,812,173,1000,1000,526,-206,1000,-245,365,3,501,445,1000,-243,177,1000,750,-1000,756,-145,-469,-388,-272,465,-1000,-402,-165,1000,-1000,1000,1000,-179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{1000,1000,1000,-645,-1000,-92,663,-1000,-802,505,1000,-394,515,-80,717,-264,-1000,-566,-748,-981,-1000,-496,-393,896,-992,-568,349,623,-496,-56,-610,330,730,-708,1000,1000,1000,321,588,638,-947,-1000,-1000,1000,393,-711,195,1000,-592,-528,228,-403,-104,193,-784,377,-1000,994,-795,-1000,719,88,1000,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{573,1000,1000,-778,-1000,-451,782,-1000,-1000,1000,982,397,1000,79,-293,-699,-895,616,-814,-679,-1000,1000,-24,1000,-1000,-159,552,1000,1000,-872,88,1000,-2,1000,906,1000,1000,178,982,-842,43,72,-5,451,1000,-444,708,1000,-220,-83,194,-339,-1000,-886,-251,-847,-922,395,-532,1000,-1000,-484,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-324,1000,1000,623,-701,-290,117,495,-1000,394,1000,1000,1000,1000,-490,-1000,-126,634,-606,149,-713,1000,-1000,1000,1000,-296,1000,1000,4,-962,-976,409,-877,-47,-1000,105,756,-453,-1000,36,238,1000,-335,-658,1000,-171,-686,1000,1000,149,1000,-145,903,-954,-13,-1000,342,-89,-137,1000,-1000,976,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-447,-987,-1000,-579,243,1000,-519,582,0,256,-904,-897,-31,311,-1000,146,483,219,-35,-568,-653,-1000,1000,699,1000,115,-1000,-539,1000,278,-985,-449,911,610,-764,-370,-376,943,104,-156,734,-150,1000,-564,-1000,537,370,167,-222,-924,29,0,609,1000,904,-310,1000,-63,108,-1000,-969,185,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{887,642,1000,-19,-40,-1000,760,-78,462,-1000,217,-1000,401,464,-89,-680,-465,-1000,-690,964,5,-72,307,-640,-1000,-702,-988,366,-236,-759,-818,1000,911,229,1000,-873,-92,-1000,318,-595,-570,-269,-1000,1000,-7,-231,927,-223,449,924,-506,-4,-335,200,-45,406,-72,-420,299,413,1000,58,-470,-995}));
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
            new int[]{193,1000,555,398,-1000,-331,-589,694,1000,1000,457,535,-375,-1000,-643,-1000,-1000,-244,-226,981,523,877,-168,-240,-386,-798,-1000,-1000,-458,-1000,-364,-560,-840,259,122,89,-1000,-468,347,-215,341,-347,-513,88,-282,-1000,-95,-493,-264,-404,51,58,1000,-1000,-531,1000,452,650,648,69,642,1000,-742,-511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-399,1000,-894,86,994,165,-653,732,1000,-1000,1000,635,1000,-746,445,620,330,1000,1000,804,99,-1000,794,-980,-1000,1000,1000,1000,287,-618,1000,-66,1000,1000,-665,13,-1000,-1000,1000,-850,1000,-1000,1000,998,-553,-839,310,1000,-1000,-1000,-1000,249,653,339,278,-1000,1000,-168,-433,1000,1000,244,-631,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{584,1000,1000,266,-635,672,-673,-650,-307,1000,630,676,-1000,1000,-1000,-477,63,157,-1000,-660,541,1000,-196,1000,84,-1000,-1000,-1000,398,627,-1000,61,-1000,-1000,-541,66,-402,1000,-234,242,-1000,1000,-1000,275,1000,1000,597,-1000,-371,1000,1000,-331,309,-812,322,891,-873,628,479,-1000,-537,83,849,-999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{432,568,151,-5,1000,48,66,-832,176,-1000,-865,-702,609,1000,-528,834,-166,269,484,47,451,-1000,56,-44,197,3,-884,-104,1000,-1000,-597,-453,21,-262,-676,493,1000,254,-811,-496,1000,361,-134,764,314,1000,63,78,-183,-398,1000,-335,-302,1000,-872,619,1000,-234,1000,-792,864,-241,-721,-790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonNull", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-958,978,339,635,-690,444,-148,710,966,772,663,424,597,44,-96,-542,-838,78,-294,525,360,457,-85,483,-680,-582,-293,387,398,-357,366,104,-143,391,-159,185,-481,606,451,-477,-919,630,119,-1,-253,999,842,791,43,-588,-217,-667,250,-579,295,970,768,646,505,-668,-104,393,-365,116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,252,-54,157,1000,1000,-706,140,679,1000,326,1000,278,-1000,-1000,-897,-1000,228,722,-397,754,1000,379,73,-1000,-634,-1000,-1000,-194,-1000,-249,-81,-631,-1000,-630,997,-836,647,291,184,-295,723,-789,1000,-727,1000,759,-769,127,1000,570,249,1000,-211,-703,1000,-521,936,1000,-366,473,145,890,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{255,327,1000,-882,774,-1000,217,-75,1000,1000,-1000,16,705,-1000,1000,1000,-262,-480,-504,439,61,-1000,-413,557,-474,701,184,1000,170,1000,-797,-1000,121,-1000,207,-507,-712,-1000,-915,-607,1000,-1000,-196,-172,1000,-647,594,-190,376,-763,437,179,-662,495,169,-1000,-1000,-757,-1000,-1000,400,1000,-149,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,-426,-415,-715,203,-375,-635,1000,693,-1000,1000,-422,400,-1000,1000,1000,762,442,349,1000,-1000,1000,1000,-1000,316,1000,468,1000,-1000,-851,-262,-855,1000,1000,460,-852,-895,-1000,67,173,540,-1000,0,859,-874,-1000,-1000,1000,-657,-1000,-1000,411,729,-317,-463,1000,1000,-1000,1000,1000,-42,176,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonNull", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,-550,339,690,1000,-131,330,633,1000,573,-843,-896,-120,-275,-57,856,-1000,983,-733,850,-946,-882,923,1000,108,-857,1000,248,308,458,419,228,1000,373,40,-773,-346,606,236,-1000,-29,-1000,-1000,-391,133,-1000,369,776,132,24,401,-810,111,157,-639,65,1000,723,-28,-468,291,53,-1000,936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,-286,1000,157,106,169,-54,408,457,590,48,1000,-568,-619,-505,-454,1000,512,767,10,70,385,794,340,-887,-621,-943,-707,-281,-836,-214,-309,-566,-67,-595,-1000,-164,191,472,-158,-354,604,754,367,-669,41,-674,1000,-924,-36,-147,-708,1000,405,72,554,1000,-96,1000,433,-65,742,525,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,426,-234,-709,-962,113,75,-255,324,-211,301,907,-1000,1000,-350,505,416,-379,1000,1000,772,-383,1000,-107,-619,408,-844,-445,1000,-1000,-1000,-1000,-1000,-8,-355,-268,1000,561,-102,1000,1000,1000,487,441,407,460,-1000,-607,283,418,-29,685,944,-475,844,129,-106,-253,1000,-388,-151,-360,794,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-90,1000,-311,-426,-899,-575,275,-378,133,57,-147,-270,832,1000,-282,-203,458,-503,-240,-264,1000,595,141,823,-1000,-163,-670,-164,783,-558,-567,-302,522,-696,-305,-30,-297,963,62,-342,-1000,1000,-149,225,417,1000,1000,299,436,585,4,7,178,-248,34,377,-102,234,717,-1000,-653,427,925,-702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{931,595,-311,307,-1000,488,764,-968,738,808,-405,754,-1000,844,649,-563,-979,-1000,-115,-687,432,1000,-984,-545,154,394,-1000,-1000,710,-614,153,-556,490,248,-937,-880,-837,607,-33,9,-721,1000,-419,-171,408,1000,721,-949,-371,-813,134,275,924,-1000,-767,-488,-963,683,1000,-1000,-638,1000,1000,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{1000,-953,-407,415,-511,523,-381,-70,1000,928,-364,1000,644,-1000,72,393,-1000,-867,184,-1000,-1000,-446,-1000,-425,91,29,-458,317,-316,481,-238,-694,640,-371,389,1000,-1000,-991,240,-907,861,-150,-671,141,862,-568,1000,-1000,-832,1000,125,611,-134,-103,-1000,130,-277,1000,-727,-1000,1000,1000,-436,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,-1000,277,-290,-352,-502,956,-93,128,72,-259,-749,1000,938,1000,403,930,384,-1000,-790,-652,-518,510,-238,1000,111,1000,1000,1000,259,-28,673,-492,759,-782,-942,446,202,-1000,-827,756,-400,1000,-1000,91,1000,-471,-149,900,-1000,507,863,-367,1000,319,-1000,612,-420,-1000,1000,-495,-633,295,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{777,-519,876,729,94,443,-788,-123,21,541,-444,997,461,-950,326,167,-803,-736,129,-916,-834,-594,-458,-373,-92,-43,-524,516,-866,148,543,-611,-613,-262,281,671,-924,-616,-246,-547,755,-42,112,266,487,-67,999,-864,355,644,392,-451,-268,-351,-645,112,-174,687,-463,-426,147,790,-231,-377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-1000,-529,790,1000,373,-493,233,-991,324,897,1000,-513,-197,-580,-570,-619,-1000,-996,-96,429,1000,-248,-219,1000,-690,65,774,-1000,-889,476,-493,224,997,-1000,1000,516,-207,-594,210,-652,95,381,-1000,438,1000,-1000,-1000,224,-262,-66,813,373,4,1000,-901,38,-800,275,-457,512,983,1000,-14,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-835,-963,-757,899,-334,558,-133,-724,-49,573,971,-39,-834,-607,878,-236,930,403,-980,188,234,-925,73,503,523,243,674,-615,-574,-123,-34,354,-834,-797,-938,-629,320,209,401,720,-779,348,-80,-476,80,530,-993,156,670,398,-758,-842,-700,319,-888,-717,708,507,74,-196,-486,283,-395,-681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{39,1,618,-342,-920,1000,-1000,1000,611,-972,-1000,-605,-266,913,907,356,1000,-157,604,114,-962,-628,990,-1000,-402,1000,410,969,-589,777,1000,393,1000,367,-786,285,-551,147,1000,-437,-1000,-452,820,-366,-1000,259,678,1000,101,1000,498,-227,1000,-103,992,250,947,56,-544,510,17,-1000,222,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{875,-847,-485,-244,817,-639,1000,757,-173,-110,452,-1000,-736,1000,-30,-172,-667,702,998,71,-1000,966,-471,1000,581,108,-977,632,1000,-1000,438,-196,-176,-594,-466,-770,56,1000,-1000,1000,400,-102,286,646,1000,-341,1000,-932,359,-639,-261,-1000,146,-128,-1000,827,-345,736,775,-191,-258,699,1000,342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonIOException", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-660,-859,354,-239,-451,84,938,849,749,-104,-313,-892,-77,-855,-791,150,9,-698,-180,-697,702,-966,-280,652,-725,-615,-411,-352,-369,566,83,461,339,-357,-486,-559,22,674,-66,734,-872,526,750,-569,874,968,50,-586,918,496,877,-461,-787,-225,858,702,390,604,758,-85,-978,-134,845,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{185,-575,-859,-794,-819,314,-1000,482,-818,-1000,-836,435,1000,1000,-8,-385,-1000,-1000,90,-479,-1000,-442,87,-301,400,658,1000,1000,-574,538,-1000,582,-223,-400,324,-265,225,-215,-1000,-225,580,1000,-95,-520,931,1000,566,-1000,-1000,-909,-110,526,-99,-107,-56,-1000,548,400,728,410,1000,442,-475,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-275,759,-133,478,300,-1000,-997,-1000,539,256,-309,-554,14,889,921,251,-215,747,274,-433,-126,-1000,113,1000,335,238,216,-369,-828,-897,-413,162,-1000,-404,436,289,-241,170,1000,-912,-174,-384,-16,-918,664,878,-424,235,167,-42,528,-85,-1000,-128,-675,147,-925,351,185,-539,-23,-1000,141,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-456,-213,934,-1000,753,1000,-1000,1000,-365,-1000,-1000,527,1000,1000,-437,1000,89,-1000,529,-1000,-876,-969,1000,-1000,-233,299,-20,622,-395,1000,1000,469,-84,1000,-491,960,1000,1000,39,-1000,1000,490,1000,-1000,-1000,1000,1000,1000,-956,1000,1000,186,-125,844,232,103,-761,-1000,-77,1000,52,458,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-254,155,-572,-1000,333,1000,-573,1000,-323,-1000,-1000,125,449,1000,271,871,522,256,1000,-437,-1000,-398,1000,-1000,-1000,838,-1000,1000,425,652,475,745,-611,736,-577,-12,400,1000,-247,-65,386,-75,1000,-475,-764,1000,1000,583,-55,439,767,-673,-280,169,-797,1000,-549,-250,-279,1000,-965,54,1000,962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{313,-61,266,423,-725,-138,-1000,-545,-52,-454,-1000,-160,-54,-917,309,-1000,-427,-643,71,344,276,-628,-248,645,146,139,-580,-544,522,-1000,-917,78,473,-822,-786,-279,-551,-539,-26,1000,338,260,-457,332,-1000,-586,-400,1000,384,-417,-801,-459,222,1000,-795,1000,-576,741,-534,-1000,-245,-4,-1000,-825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-719,571,1000,-704,-590,1000,-1000,-391,-815,328,-1000,466,1000,1000,-923,564,-645,328,233,-836,400,-1000,657,-779,-818,216,242,580,-600,666,297,1000,343,-184,-457,-753,1000,710,589,-1000,973,891,808,-868,-764,770,1000,1000,-639,1000,660,288,654,-390,-1000,330,-967,-663,320,1000,423,1000,551,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{79,-351,-634,454,881,-170,442,312,1000,0,-275,895,587,-224,62,-141,492,118,-74,43,-904,-385,84,268,479,-211,-112,-291,-736,-440,541,1,-357,-273,-311,90,422,-6,674,534,292,-21,-307,747,679,-668,375,588,-125,-268,546,-106,-504,22,598,-446,334,-128,488,-43,449,-110,21,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-915,-881,973,349,-373,762,41,-653,459,-722,1000,-74,-673,-1000,637,1000,-456,-1000,558,-665,-416,603,-115,655,-580,1000,-1000,860,-1000,-1000,620,1000,86,-248,-67,-1000,-1000,639,1000,1000,854,-218,476,622,546,704,-942,-1000,1000,-1000,-349,98,137,-454,-443,986,-1000,-1000,-765,-937,1000,400,1000,29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{8,-283,-134,407,1000,1000,337,433,-1000,-455,1000,-1000,-1000,-644,-1000,930,381,742,1000,-1000,-969,838,1000,-1000,-302,659,-885,369,1000,-817,591,1000,942,552,-645,475,627,-1000,244,546,-1000,-714,1000,-593,-32,1000,-915,479,-26,-1000,-1000,1000,553,306,-295,716,-1000,491,829,-1000,66,-524,1000,-403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonNull", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{1000,-227,-389,-1000,1000,-268,-169,1000,-1000,-760,758,-51,1000,304,-1000,1000,971,-246,343,-1000,-526,167,1000,609,-712,-1000,54,1000,-67,624,30,-381,581,-845,62,1000,1000,639,-1000,1000,-456,-843,1000,-377,-33,-400,359,1000,-1000,13,-784,1000,80,1000,1000,-900,501,923,521,160,1000,-686,494,-128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-1000,119,-229,966,177,295,-375,410,433,216,-31,-474,-231,-118,800,482,379,-695,584,121,-489,752,-212,-717,132,1000,174,-973,763,-1000,-353,702,-901,-840,254,442,-1000,-780,1000,-858,723,310,-1000,559,-274,139,699,-886,1000,328,167,-1000,914,49,513,706,520,-243,599,-644,235,-499,27,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonIOException", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{1000,-143,546,346,6,211,549,-240,-478,-966,974,-337,682,975,-597,-1000,16,60,30,-157,-115,418,-228,524,-1000,-294,1000,871,-673,-25,769,74,1000,-264,-873,-439,33,1000,-1000,1000,-1000,-98,-62,789,-298,-689,133,1000,-1000,-612,-159,456,-579,-407,-242,239,307,-174,572,1000,-916,-766,780,245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-1000,-143,-171,355,-399,806,568,65,732,-956,207,1000,-221,1000,856,-469,517,-716,-343,939,-553,-492,-1000,-630,1000,809,252,-413,-1000,107,-376,932,-415,1000,-466,-236,-318,-1000,92,-549,519,-286,-289,-562,-766,-570,-1000,-270,553,1000,-158,-543,-1000,-1000,-518,473,-427,445,409,-534,968,908,122,668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{1000,-655,1000,-142,-49,331,-796,-608,-370,-861,1000,-1000,921,970,297,-1000,-850,-835,598,1000,-142,316,-1000,643,-689,-613,-301,405,-447,-941,800,619,749,-928,516,-597,-603,877,-1000,1000,110,31,-300,-772,-556,343,97,1000,-386,-596,275,-256,-289,-278,-596,787,92,-727,-233,940,-767,-930,-488,-545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-189,-673,426,-90,162,1000,-1000,-1000,-408,-1000,1000,400,153,939,-338,-739,-1000,-636,133,-144,-716,-234,-511,-136,-1000,665,-1000,383,953,455,-209,1000,365,1000,-422,-1000,-1000,-204,-610,-84,846,447,22,-1000,440,75,-1000,99,-538,294,-31,352,-565,-646,-640,-77,-665,-503,-1000,-949,1000,-291,7,-518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{94,-9,-261,-129,483,231,-441,943,-1000,-832,474,1000,846,-14,89,-77,595,338,507,-215,-661,-1000,354,45,-413,-1000,658,289,-775,561,-875,-223,-674,-609,-898,1000,-538,918,-807,996,465,-278,-559,-673,-283,-1000,-835,-35,-827,617,-372,-257,685,-483,509,-89,312,555,-1000,630,-370,-963,232,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{807,-181,-634,106,881,63,-920,580,-392,-92,448,929,26,93,-668,738,523,380,-74,-771,-458,82,385,-123,-780,-302,905,740,-29,676,60,-428,911,-110,-23,500,263,-260,-916,534,-630,-21,574,-476,-189,-668,-521,302,-749,-226,-686,693,118,-85,598,-161,-17,401,268,-136,-390,-590,973,264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{355,377,420,-1000,345,-219,-670,448,-559,-1000,-861,-1000,809,1000,-89,-234,1000,201,62,-883,-323,-739,892,935,-243,-1000,-56,707,-1000,1000,-784,-294,-358,-112,-235,1000,1000,1000,-482,1000,-400,-776,607,-426,-152,-1000,84,1000,-1000,827,-367,522,-131,944,1000,-447,798,1000,89,573,-851,-143,-529,-768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.internal.Streams", "", "parse(com.google.gson.stream.JsonReader):com.google.gson.JsonElement",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.ArrayTypeAdapter", "com.google.gson.internal.bind.ArrayTypeAdapter", "read(com.google.gson.stream.JsonReader):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.DateTypeAdapter", "com.google.gson.internal.bind.DateTypeAdapter", "read(com.google.gson.stream.JsonReader):java.util.Date",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.SqlDateTypeAdapter", "com.google.gson.internal.bind.SqlDateTypeAdapter", "read(com.google.gson.stream.JsonReader):java.sql.Date",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.TimeTypeAdapter", "com.google.gson.internal.bind.TimeTypeAdapter", "read(com.google.gson.stream.JsonReader):java.sql.Time",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.gson.internal.bind.ArrayTypeAdapter", "com.google.gson.internal.bind.ArrayTypeAdapter", "write(com.google.gson.stream.JsonWriter,java.lang.Object):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.gson.internal.bind.DateTypeAdapter", "com.google.gson.internal.bind.DateTypeAdapter", "write(com.google.gson.stream.JsonWriter,java.util.Date):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.gson.internal.bind.SqlDateTypeAdapter", "com.google.gson.internal.bind.SqlDateTypeAdapter", "write(com.google.gson.stream.JsonWriter,java.sql.Date):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.google.gson.internal.bind.TimeTypeAdapter", "com.google.gson.internal.bind.TimeTypeAdapter", "write(com.google.gson.stream.JsonWriter,java.sql.Time):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
