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
            new int[]{870,-705,-101,517,-612,-284,168,-186,237,-932,698,-861,-706,939,688,-773,525,-52,860,-393,334,-30,34,-133,-686,-962,-329,-593,458,899,799,-606,205,-638,-539,-524,-706,-352,845,-504,350,699,592,914,360,99,445,-62,760,681,332,469,-295,-850,287,-121,315,131,-822,385,-866,-54,-880,-712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "beginArray():void",
            new int[]{-286,-134,-941,-749,-506,112,-631,492,-79,56,549,961,216,-952,-392,-706,701,-531,976,764,602,-745,-361,224,26,-272,-720,52,-29,746,-861,-641,503,352,2,549,884,-868,763,-16,325,313,342,-982,324,-751,-611,-87,-918,417,166,285,-643,283,-719,-370,-679,479,526,205,-552,-378,-26,100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "beginObject():void",
            new int[]{62,488,-19,555,828,247,-303,390,627,-273,-491,-563,-337,764,-437,165,725,-566,-575,210,-920,604,828,-311,338,946,-76,-862,739,460,-206,-847,607,-597,929,512,-114,653,-792,657,578,-979,-511,746,706,788,364,-806,-35,828,-185,600,618,-910,611,-727,410,916,79,183,-760,702,-409,-903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "beginObject():void",
            new int[]{621,441,199,-472,151,719,774,-607,624,-952,155,803,-695,-235,-676,988,-830,43,909,289,-987,243,-660,-651,-867,495,324,-323,984,-984,-657,-897,-518,477,-170,-667,805,-94,-527,-75,462,-934,-279,-661,171,21,944,-597,-334,694,-25,545,921,-368,-242,-571,-518,-124,707,271,632,897,44,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "close():void",
            new int[]{-510,202,188,-343,-623,-854,869,964,-233,-575,-322,-22,-866,-235,573,765,-57,620,87,190,511,-236,-827,-836,-874,657,985,-845,393,767,233,-239,85,-206,120,-816,-425,-480,715,-509,379,-133,63,721,-708,-295,328,78,-880,-120,-141,741,-872,39,142,-872,973,-100,355,-138,107,-196,-565,137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endArray():void",
            new int[]{-215,-972,-834,-842,953,61,-714,329,-116,-835,-52,-701,-981,1,271,-178,445,-212,73,-147,483,545,268,-569,-513,268,-958,-110,844,-388,15,-402,224,-375,94,35,484,-180,470,-555,-663,-568,329,-506,-138,126,-786,-550,259,-405,717,217,869,493,-805,-757,-609,-14,527,793,-490,-160,-390,372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endArray():void",
            new int[]{-336,-616,-775,-824,-423,-12,-800,389,362,-272,-626,-159,-529,830,766,-892,614,144,579,-518,845,456,173,390,479,-283,-994,-153,232,449,-346,-703,31,500,-466,806,-683,-903,-318,984,-646,727,56,-736,15,-273,218,156,877,707,-18,17,-972,-100,607,469,-56,376,-514,-851,-959,-307,-78,-298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endObject():void",
            new int[]{-990,24,-757,-802,716,-263,-907,425,-461,-850,-454,393,-127,-609,-784,743,10,76,-478,-970,-648,-623,250,56,-183,-270,647,25,619,549,-356,-620,-120,898,-752,173,-331,-84,-581,54,938,983,800,-414,-416,-893,-428,478,396,-177,381,-456,-3,12,820,720,-937,464,250,-689,74,793,888,792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "endObject():void",
            new int[]{421,-747,-943,-234,135,918,-921,902,-319,977,-942,833,680,285,240,-247,-364,-897,-552,-999,-762,23,369,879,-48,750,-640,677,-632,936,693,583,-882,824,593,-871,223,314,-321,82,-311,-759,-480,-703,-128,-826,854,929,-303,-173,766,-360,-855,-699,-411,924,-403,809,-356,-436,627,-716,-947,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:JA==", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "getPath():java.lang.String",
            new int[]{20,851,-489,976,-423,-540,165,-14,-256,376,-436,672,484,521,-927,149,856,589,-405,2,497,81,498,-631,-601,436,86,-463,410,-101,112,-205,587,-914,-115,102,371,-946,597,560,-623,181,-370,635,548,94,-195,611,383,507,-170,-138,17,-87,-503,-824,26,-202,592,-412,-983,277,-60,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "hasNext():boolean",
            new int[]{969,434,55,-536,-206,64,771,-578,599,-524,-610,764,-602,-531,-264,-206,269,-745,-375,567,-14,254,912,-980,-603,679,92,-993,-230,543,-459,-231,947,-599,-974,207,-997,745,-129,-944,354,-735,342,-451,781,391,180,484,-354,-497,-980,755,-238,969,-564,-663,-177,804,762,-511,-924,-875,493,504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "hasNext():boolean",
            new int[]{-839,-883,184,-572,-67,545,-238,482,-437,505,15,-664,-809,414,-837,81,631,-920,355,-412,555,-904,471,539,-605,-15,120,507,-415,-930,-651,-19,317,-197,-991,80,931,804,602,425,224,-776,-519,-852,523,-792,-224,307,-803,228,89,-846,417,820,-859,624,460,884,-164,-834,607,115,965,650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "isLenient():boolean",
            new int[]{173,-474,-513,-296,490,800,-471,-554,389,-180,-296,-551,666,165,359,-489,-738,12,823,-683,-189,879,969,-117,962,27,-357,-433,-837,-969,641,-874,-766,664,-930,509,77,-433,-733,-851,-832,-588,226,-671,-898,256,-557,911,383,616,-515,-857,895,-578,-800,-397,71,-538,-533,-607,611,181,-345,-349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextBoolean():boolean",
            new int[]{253,-783,56,-910,-118,653,-715,-177,658,-403,83,533,584,31,58,-126,197,593,501,183,-755,-676,-400,721,851,-625,-60,785,765,-859,-462,925,-45,-135,-753,363,-116,364,594,580,-398,182,825,-988,11,-754,285,794,-458,-136,-888,584,885,-209,-331,637,368,-1000,149,-490,131,-211,-661,34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextBoolean():boolean",
            new int[]{382,-293,966,-87,113,-923,-182,-41,-651,-909,-174,242,-735,-694,823,681,445,386,-505,350,-333,-538,-116,-778,-917,0,667,-598,-471,805,551,-771,320,301,771,-552,-964,878,862,567,513,-269,621,437,556,-605,-513,-853,720,935,471,370,-72,-349,-838,470,646,-951,-613,856,-102,-156,-377,-628}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextDouble():double",
            new int[]{863,-153,-964,626,-695,-320,-53,-535,-709,8,238,369,914,579,-36,-629,-269,-636,-581,-374,11,-782,-818,-639,-890,972,769,-151,-703,-282,-177,-703,-1000,930,-410,916,-57,356,43,62,-79,118,-490,312,-934,-610,859,-383,388,-863,-568,-745,-930,964,743,209,-421,-144,701,-192,970,-222,-399,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextDouble():double",
            new int[]{814,-465,-105,186,-867,36,-610,-41,-416,976,617,-504,211,-395,-93,-247,439,-674,390,-756,211,-539,-764,-63,707,-941,887,-83,-707,315,-509,545,976,-953,-374,184,-844,986,-17,-816,-282,-865,158,-176,655,-636,-53,144,286,547,-377,218,898,101,543,-708,-849,-481,143,455,-41,-322,-116,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextInt():int",
            new int[]{142,-836,-866,-292,409,-88,-773,76,639,942,691,891,-588,166,242,-783,959,690,-187,-78,887,-819,-418,-529,-14,-936,856,870,-702,338,324,243,-436,86,-90,-171,171,582,284,92,839,-671,929,-967,-617,-675,-414,-153,-874,369,904,585,-207,-651,147,-369,44,-919,945,507,202,-488,218,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextInt():int",
            new int[]{542,191,-873,-863,-271,-964,-659,109,-134,739,266,529,-158,686,-62,817,127,162,-136,76,952,-735,-882,-996,-349,-983,824,522,753,375,-35,-447,385,178,212,-329,563,860,-762,253,-990,200,873,-842,318,-661,358,-987,437,-167,-47,-294,770,-404,195,-158,465,-743,-774,-417,-93,670,770,571}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextLong():long",
            new int[]{71,-88,791,195,-685,831,497,-489,-73,-742,-895,-770,827,433,419,-956,-334,-92,-431,412,-137,391,628,-254,647,461,243,-256,-717,787,595,541,-898,914,997,-317,-901,-631,357,531,915,-127,332,623,803,795,-473,797,-140,-405,360,410,-804,-964,325,-139,28,-868,293,814,-286,467,237,-992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextLong():long",
            new int[]{552,223,-55,-501,-739,572,-917,228,-749,-601,-67,836,-992,747,947,138,-565,813,-434,-677,-132,-308,876,-758,-360,-322,445,-151,-888,-69,332,687,431,762,-719,581,502,482,996,-652,646,-211,21,237,-732,991,-730,-904,-537,54,503,765,958,379,-824,-877,-963,510,-1,346,163,-426,-747,-529}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextName():java.lang.String",
            new int[]{-407,10,-278,891,-233,-295,867,336,-830,-418,31,626,189,224,-547,-236,713,796,271,116,-348,-746,-996,-833,864,717,121,-678,-881,-92,-544,-446,-201,183,41,-813,-114,546,-597,921,-464,-721,-249,311,997,321,-333,359,994,395,-158,606,-184,-996,236,175,-722,-101,-370,-400,523,-606,929,919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextName():java.lang.String",
            new int[]{232,146,237,340,456,719,-113,481,-274,892,595,-877,-682,-654,930,-770,-502,729,-742,527,670,-81,-890,230,-114,692,-147,233,-328,-205,315,164,-785,-951,730,173,820,-47,920,-840,-752,-471,-3,-744,86,634,996,-206,-439,817,880,-237,414,956,94,793,344,358,506,-926,-686,792,-66,328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextNull():void",
            new int[]{-148,-93,660,823,287,557,970,-976,509,-764,-376,421,823,776,572,-690,-493,452,453,-881,201,-597,-229,-102,-328,333,-810,-608,-316,-376,-608,671,207,-121,-150,824,5,591,-940,-429,763,173,335,-158,321,-125,-938,315,-619,-716,-490,705,43,11,-288,236,-53,961,-264,82,461,-864,-254,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextNull():void",
            new int[]{-666,321,-360,100,-427,173,-206,140,891,283,423,-812,26,-442,-536,505,-547,951,-523,576,601,970,-926,-95,-482,691,-377,813,135,55,-415,-628,771,116,739,-871,-691,609,750,-356,-383,711,-922,-973,210,-801,47,641,-124,325,63,-79,83,-293,-592,-131,314,255,843,-799,184,-237,-696,-651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:aGVhZGVy", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextString():java.lang.String",
            new int[]{320,521,-881,-229,64,-715,-466,478,-698,-353,5,837,233,-248,-505,648,-24,-625,-258,429,407,-859,696,-584,-391,575,-176,-911,-328,-914,551,377,483,24,993,-75,489,897,-693,-102,953,582,488,687,144,851,-761,566,115,952,670,850,-746,85,-999,-532,604,-348,199,-61,-864,912,-463,746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "nextString():java.lang.String",
            new int[]{-927,-380,245,-8,801,16,-933,872,402,-271,-328,128,877,-506,-844,-175,-901,680,9,-496,-151,-876,-462,344,958,-129,-910,492,410,-715,1000,584,260,106,82,791,451,944,639,746,239,429,-522,-381,-177,-224,212,-685,-32,-124,272,-767,982,194,-562,419,200,734,-499,0,-706,-884,681,-202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("ENUM:com.google.gson.stream.JsonToken:STRING", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "peek():com.google.gson.stream.JsonToken",
            new int[]{21,-925,459,-494,868,-104,-160,740,714,956,-283,-10,-816,-862,116,496,-372,-638,190,-729,-890,579,456,-396,-417,602,-570,559,-503,26,669,670,-649,-829,-904,-402,653,43,-827,-487,-406,-406,-80,-851,392,-722,-884,68,686,799,-919,537,631,-731,-612,-402,409,559,-438,398,-217,-472,-638,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "peek():com.google.gson.stream.JsonToken",
            new int[]{-883,40,-873,-808,10,738,-844,-142,216,779,-924,-919,-653,231,-45,-317,49,-828,751,-231,-541,-897,349,119,581,330,144,-642,166,-617,-515,928,-110,218,-803,439,-370,-993,15,-27,-548,618,-716,-903,565,-423,541,953,951,-753,711,-392,627,224,570,90,568,570,-160,965,225,371,835,-829}));
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
            new int[]{9,8,595,328,117,-176,894,918,-579,467,624,-465,250,920,-536,701,-154,555,-742,655,-731,-431,597,312,602,587,-517,771,319,693,-841,55,514,-40,-945,812,74,422,-750,-119,909,350,843,-192,-192,665,199,-316,660,-606,147,-15,377,171,-69,-491,-884,-34,-86,-48,-831,-551,907,-836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "skipValue():void",
            new int[]{-536,-486,-578,253,634,-211,522,-286,195,300,-435,160,-310,-776,-719,672,5,-26,420,353,455,893,-753,603,887,-869,593,-985,707,554,633,-879,590,310,379,-705,-955,556,-629,-728,977,771,-988,-75,899,54,573,-747,-416,-124,16,797,-431,-161,493,167,163,206,-418,763,378,-849,195,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.String:SnNvblJlYWRlciBhdCBsaW5lIDEgY29sdW1uIDE=", DEReplay.run(
            "com.google.gson.stream.JsonReader", "com.google.gson.stream.JsonReader", "toString():java.lang.String",
            new int[]{150,23,-765,758,-509,826,-26,-714,-883,488,697,-445,-168,-425,507,615,-68,923,-14,-335,443,-670,-71,863,-370,20,-997,41,-210,-868,-334,541,474,966,-988,-786,258,-564,361,-224,12,-757,313,-810,174,-339,811,-502,977,478,257,633,-194,-315,172,-871,-107,-259,61,-630,-585,-69,-865,39}));
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
            new int[]{-77,-486,535,-531,896,572,55,-94,833,197,326,-65,698,-945,-357,459,782,-136,138,52,-639,896,782,-785,648,516,553,723,-552,-310,-501,-831,142,-533,18,-430,-902,311,-421,400,423,-115,-691,796,-905,-639,411,840,922,-301,207,-194,-446,415,-251,29,798,369,-582,-304,-126,803,-322,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(com.google.gson.stream.JsonReader,java.lang.reflect.Type):java.lang.Object",
            new int[]{-71,-1000,332,-129,295,39,-156,-1000,753,-1000,5,80,-938,-798,793,-1000,-1000,1000,387,529,-94,-636,-718,618,-1000,-872,-675,376,221,560,1000,659,-1000,595,18,101,-586,-534,167,-338,108,1000,624,770,-911,-400,159,-252,272,-65,152,-1000,-512,-216,-357,-1000,346,-705,-1000,608,1000,-1000,1000,1000}));
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
            new int[]{706,-428,-167,992,584,-341,872,-86,-989,-467,138,-171,726,389,-682,-817,-127,19,-771,-567,4,781,-129,-890,-881,-732,823,-464,-951,-881,-799,-695,33,537,630,-396,733,468,-259,785,-856,105,510,191,842,-585,-516,192,-152,-290,365,-379,24,952,745,-646,-644,439,871,-341,-264,-227,252,-893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.io.Reader,java.lang.reflect.Type):java.lang.Object",
            new int[]{947,-363,630,339,-588,784,-602,791,-10,844,-129,601,589,117,31,-669,720,-681,-719,-77,206,423,-393,321,-750,189,-924,589,-191,754,79,26,776,751,-87,771,-108,-197,-642,-293,-524,979,-251,-917,-911,-196,713,200,62,761,-770,-222,456,875,-61,-859,469,922,744,246,-439,99,676,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-51,-749,-594,46,1000,-718,-276,-803,1000,814,-272,848,647,-1000,715,-880,865,264,886,400,-105,-551,303,334,819,-41,684,734,-127,-1000,-179,411,-1000,-1000,270,781,488,-653,92,432,-1000,-815,-167,467,293,-411,-266,-51,-245,722,-1000,-513,221,-915,1000,-967,-527,1000,241,385,-296,-509,111,873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.String:LTMwZS0xMDAw", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-49,-1000,-269,502,-30,-283,-1000,1000,129,-233,655,-345,-813,-314,-495,-94,1000,543,1000,908,591,623,-576,1000,546,-381,770,-55,999,-1000,994,290,-1000,1000,833,947,721,-101,-316,388,637,-154,564,-423,95,-500,-743,-823,-225,1000,-369,-189,961,233,1000,-925,86,177,360,-388,-79,-1000,329,971}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.String:NDE3", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{847,528,-208,-838,-417,-372,-76,-55,-917,762,-489,700,300,890,786,658,308,334,924,306,-107,79,774,-159,624,99,125,-100,-719,795,185,605,-743,-249,-856,753,-399,-48,119,-418,738,-601,597,975,-406,-415,521,156,-164,-278,637,27,-749,777,-638,88,-97,405,-494,-398,-355,673,-497,113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsc2U=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-333,-755,553,731,842,-495,331,566,-650,699,864,704,-73,-527,-703,327,239,-497,-549,-489,944,-536,294,322,-477,184,-853,144,358,-584,416,-878,265,-237,804,392,733,270,410,-621,-485,937,-98,854,883,-29,-967,-324,-151,21,-829,-832,978,-224,241,-38,-983,-627,477,-215,516,-148,-262,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.String:dEJKNzZ5X0xGTA==", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{127,538,-1000,-545,-1000,-1000,-815,952,-34,-97,-987,1000,318,-1000,331,-811,-663,1000,462,-28,348,663,-828,167,718,-180,-754,1000,-265,-702,-605,324,1000,-814,-55,-472,646,-1000,-69,-228,-1000,-162,-110,-419,1000,-21,324,-1000,1000,217,-100,-1000,1000,-728,389,978,-815,-858,-636,-162,-222,-530,1000,990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-545,611,693,207,-823,-431,-830,-927,1000,-718,437,1000,-542,-327,721,-1000,670,628,1000,683,-393,378,-481,-469,783,112,601,229,-626,97,1000,150,-1000,1000,-829,595,-1000,-1000,-910,-620,-400,-514,-1000,-510,35,1000,826,1000,231,-75,1000,-20,-612,-1000,928,-400,373,1000,-139,293,267,748,1000,-381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{50,-97,-422,-405,-364,840,-198,803,-767,360,778,-21,-462,461,567,-219,-416,-345,857,-453,-31,876,869,29,-49,399,-65,-217,60,880,-280,757,615,746,-777,-527,607,-870,161,-263,755,292,-906,-723,-742,46,-236,-93,714,-904,510,878,31,387,-685,-92,-258,-434,-891,69,-927,-500,245,-826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.String:LTcxMy45MzI=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{1000,-131,-1000,-523,-713,-747,932,-172,-741,-1000,61,1000,1000,-91,-202,1000,-792,928,1000,1000,-588,-1000,393,-737,-1000,-750,-1000,-521,-332,382,-1000,-1000,-321,-346,-1000,-1000,517,-108,-286,-1000,-293,311,534,-835,-41,625,947,-244,692,-314,-655,711,-102,-1000,-816,1000,309,1000,-974,686,-1000,-970,690,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{657,-873,-196,398,-30,-123,594,-575,-1000,264,-1000,187,-150,400,69,954,-1000,1000,-1000,-983,-877,1000,1000,518,-1000,289,-1000,-55,524,1000,-953,-1000,381,-1000,277,-479,-501,-919,-586,99,1000,1000,928,-342,117,1000,814,1000,1000,-1000,949,929,143,1000,703,62,-1000,1000,-1000,773,-649,-602,1000,469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{791,581,-75,482,-944,967,649,922,-161,612,-509,765,-439,-556,-741,-984,-683,-290,788,912,-643,635,44,332,896,468,-113,-328,722,640,827,-173,-126,835,256,748,653,-222,-912,-372,657,275,523,-809,170,599,-247,787,245,-247,-209,47,-857,-734,-636,670,372,-741,-203,638,545,-717,-825,575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{417,-362,343,-194,223,-808,604,118,-1000,-1000,564,155,-337,-1000,-1000,300,-813,578,-414,203,-268,-1000,52,260,-911,-972,-1000,-677,924,-902,-1000,-585,-70,-335,-1000,398,1000,446,-237,-348,890,522,1000,-1000,725,-589,-740,-232,-987,89,-825,219,316,78,605,899,-65,-1000,179,-989,580,-217,-478,296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:LTY4OWU3ODE=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{1000,-448,-1000,-346,-689,-595,781,-836,-972,-791,414,-1000,569,-91,-1000,1000,48,1000,-92,-877,-589,612,-92,-1000,-1000,-625,-112,-521,723,174,-1000,-1000,-1000,-61,-1000,-1000,-97,243,-682,-867,13,-276,451,158,-241,247,371,58,-220,-296,-956,711,-102,1000,-569,1000,1000,1000,-509,226,-1000,-528,449,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.String:LTEwMDA=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-580,-1000,-561,-157,-1000,772,1000,0,-1000,-970,-15,-651,328,-1000,-1000,-847,42,629,-636,-303,-413,418,-94,0,-115,-1000,427,-40,-724,-180,-562,1000,-1000,-332,-538,-1000,600,527,-249,-810,0,-1000,1000,1000,-519,-76,126,-455,-543,6,-1000,470,0,-106,-311,1000,0,-353,-299,312,-276,215,-461,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.String:MDY2cFo2X1pDZ0Y=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{90,-973,-323,623,1000,-302,-339,-781,1000,1000,-543,487,1000,-1000,842,-104,726,964,1000,-587,-1000,596,1000,1000,505,-291,-209,311,947,-1000,-41,-81,-1000,-915,-231,534,382,-548,-233,747,-684,-608,204,148,752,308,254,956,-132,147,-932,-885,-1000,-162,1000,1000,-1000,95,-362,601,-348,-1000,-849,642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.String:Nit0VzZf", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{-777,-1000,23,573,-285,-280,-769,1000,-9,668,768,-136,-1000,-520,-1000,-498,-404,721,-1000,654,-73,163,-1000,569,1000,-245,1000,1000,722,-1000,-1000,1000,-1000,927,1000,1000,-1000,131,-487,922,584,-995,242,997,81,-896,1000,-446,-1000,824,-519,-432,973,-327,1000,-1000,750,-164,1000,-874,333,-1000,186,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.Class):java.lang.Object",
            new int[]{760,846,-1000,-67,1000,-1000,343,-48,1000,483,58,-733,-1000,-1000,1000,-922,-831,577,1000,496,-290,-1000,871,-595,326,148,-736,1000,-729,1000,190,-813,1000,-1000,-1000,-406,0,-1000,-146,-1000,637,1000,-1000,-138,1000,1000,1000,1000,-663,1000,-1000,-1000,904,-809,-1000,244,585,1000,133,-265,10,-797,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{109,617,1000,-282,-586,-651,-509,681,-1000,-219,1000,-214,-251,201,-200,97,222,1000,-1000,-130,1000,106,694,-467,1000,-152,70,1000,258,-1000,870,-369,-1000,-980,-1000,865,-403,1000,-1000,326,-899,637,598,-89,-550,25,1000,-1000,-115,239,1000,-1000,78,581,1000,-397,-221,639,-1000,47,-1000,-118,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-40,-561,328,-1000,365,-1000,1000,858,1000,-861,293,271,-133,-148,-780,463,-513,-336,413,-283,-949,-866,-1000,-261,-681,45,-952,-1000,-1000,-130,-1000,1000,1000,1000,1000,-1000,1000,1000,1000,-418,-1000,-1000,1000,49,460,-1000,-362,-357,-703,536,-130,88,787,271,-1000,1000,-753,223,-98,257,573,-933,1000,437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{79,401,978,-181,-586,-823,-509,632,-791,-1000,748,-599,-77,201,-251,-98,159,1000,-1000,129,913,989,694,-467,955,43,39,470,439,-1000,1000,-369,-628,-980,-660,837,-371,702,-1000,376,-884,637,598,103,243,-97,931,-1000,-175,406,339,-771,135,832,1000,-57,-356,702,-1000,795,-390,408,-1000,-881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.String:ODc=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{46,293,937,-550,-87,-1000,882,-542,729,-689,-527,-1000,-290,-770,540,-214,225,928,-1000,93,90,-405,698,-564,802,1000,-442,-1000,280,-1000,760,1000,316,1000,1000,-1000,-679,130,-421,12,38,-54,1000,1000,2,-911,281,-853,-125,437,-735,641,1000,1000,1000,1000,-1000,816,-519,1000,-1000,164,878,-653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{532,302,247,-466,610,49,-181,559,-558,400,-393,105,1000,-785,277,181,-218,-400,-648,547,291,508,-437,400,1000,343,-990,514,-400,-1000,-400,13,-77,446,-144,510,-808,761,1000,230,806,-690,120,932,-689,603,177,-446,-213,-544,447,101,-485,144,1000,950,586,-76,-1000,315,400,-652,-1000,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.String:LTc5MC40NzE=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{459,717,896,101,-790,-771,-529,330,36,-679,517,-571,-329,-432,275,797,340,-338,-608,-605,442,779,355,-640,84,900,-86,268,982,-894,466,-679,-1000,-1000,-192,691,1000,780,-415,-299,-144,432,8,-545,329,883,-488,610,348,294,360,-74,159,-463,557,-234,-749,722,-954,94,-1000,-941,-374,-801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{250,1000,1000,-853,-1000,-651,-243,1000,400,-628,1000,50,-1000,5,-768,864,-15,1000,121,-792,211,188,722,-840,582,-733,-878,745,674,-110,978,55,-1000,-699,659,803,-829,539,-703,-145,-181,1000,460,-150,747,421,109,-501,589,357,636,-305,-317,-635,597,-445,-705,565,-485,-246,-1000,304,400,-846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{240,642,627,-625,1000,6,-427,-146,115,1000,-442,227,-729,-481,-389,-119,-885,346,49,-598,11,287,-236,-1000,245,343,-993,-246,665,506,591,242,-197,768,554,-305,-571,686,695,-459,-248,359,-235,-285,-152,-192,-438,-39,-65,215,656,127,85,-487,126,202,279,68,-704,795,-583,361,1000,-25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-227,1000,1000,-853,-1000,-71,-1000,576,929,-947,1000,-51,-1000,-753,-334,142,-315,1000,121,-599,46,713,1000,-1000,390,-1000,-569,1000,1000,-1000,1000,-1000,-1000,-1000,-987,1000,-595,137,-1000,-300,-688,1000,-484,8,850,391,35,-80,787,622,1000,-546,-121,-568,-48,-1000,-118,635,-485,216,-1000,994,325,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{255,-167,961,66,300,1000,-262,764,1000,950,-837,1000,-302,543,-262,-851,-397,-716,1000,-86,-693,-277,-1000,-1,206,-529,1000,-646,-10,1000,-834,-894,215,393,528,-34,-726,-625,1000,-16,-264,-1000,-1000,-341,336,-216,-858,386,-439,-335,-271,1000,-599,-435,-378,-730,901,342,326,-82,507,246,1000,-359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.String:LS05MjU=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-60,-146,-394,378,925,211,-162,-1000,-692,701,274,-178,440,1000,-1000,-701,1000,912,-1000,759,-660,-1000,76,1000,-392,158,-586,-877,-212,-423,-502,922,1000,1000,-119,-498,1000,-186,-837,1000,-739,-1000,576,-490,-1000,-1000,1000,-265,-1000,-875,-458,-629,530,1000,-1000,989,-43,1000,-498,-893,1000,-1000,-53,938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{107,-1000,-63,-181,1000,-935,503,747,1000,-667,-1000,364,804,177,202,75,-1000,-1000,299,548,-1000,354,-1000,-32,-989,339,-655,-1000,-267,233,434,357,989,871,980,-1000,1000,90,1000,-489,-816,-914,638,-1000,-52,-1000,-1000,-783,-311,450,-884,-58,709,144,-1000,1000,-276,832,-319,718,1000,-783,1000,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMGUxMDAw", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{1000,-167,627,390,1000,172,1000,570,-721,1000,-640,545,751,1000,-212,181,41,-1000,-601,-122,608,55,-1000,1000,480,721,557,-170,-1000,1000,-1000,1000,469,1000,265,-961,-726,621,1000,205,460,-1000,547,-652,-1000,434,379,-1000,-828,-714,210,183,-222,641,-426,-53,65,68,-995,22,1000,-587,-893,-25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.String:LTEwMDA=", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-959,701,452,-13,-1000,164,-750,637,30,-722,846,24,-808,257,-773,-200,386,1000,655,458,-96,-1000,-1000,-266,494,-528,-245,441,-457,-656,550,-371,-1000,-713,-347,865,11,-180,-1000,269,-188,987,-1000,540,288,732,1000,337,677,-265,-256,-433,114,96,567,-578,-428,745,-909,146,-1000,189,-20,-390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-1000,727,-682,-657,-703,-361,-948,-456,773,-1000,-505,-830,-674,-173,115,-567,-1000,823,140,-121,-193,747,408,-1000,890,612,-1000,-190,1000,-701,858,-622,-611,373,1000,-173,-962,-426,52,-634,-884,1000,-643,-43,412,391,-414,630,45,634,15,264,892,-999,105,726,-26,749,-1000,1000,-1000,258,1000,464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.Gson", "com.google.gson.Gson", "fromJson(java.lang.String,java.lang.reflect.Type):java.lang.Object",
            new int[]{-668,1000,-523,174,-900,412,-873,-783,539,-355,35,-200,180,-540,-633,245,-727,734,-292,260,-857,-788,-45,-387,-268,759,-920,-237,776,-519,209,-675,-7,135,182,-421,-646,190,-327,304,762,1000,-364,498,1000,562,-134,311,-366,-308,-354,832,192,503,-467,531,-220,792,-937,562,-325,-608,649,548}));
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
            new int[]{1000,-1000,912,-225,1000,1000,-390,1000,1000,440,761,577,281,1000,-265,-1000,547,-1000,242,-567,116,756,-205,1000,-1000,-937,229,-920,-299,1000,-1000,74,437,62,-641,-788,1000,1000,-1000,1000,-866,-420,1000,-1000,320,-134,-1000,-1000,-1000,-743,-1000,1000,1000,395,-382,-1000,1000,-763,491,-1000,697,419,-1000,-267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,825,-554,987,-1000,-1000,585,-385,340,717,-988,1000,-347,-238,599,63,336,-203,354,1000,-1000,-484,568,-956,-713,486,1000,1000,1000,766,-703,-166,-672,1000,-714,1000,70,72,-11,1000,32,244,-1000,236,1000,-852,139,-396,484,-387,-533,1000,464,-157,1000,364,-242,1000,123,-365,441,-502,1000,-838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{1000,-1000,-545,-938,956,625,-293,985,124,440,367,-337,589,1000,494,-862,923,-997,-1000,-11,759,340,-103,1000,-645,-1000,630,163,-135,704,-420,1000,437,62,-641,-788,-168,1000,-855,115,-866,-652,678,-201,374,1000,-668,-233,-503,-913,-65,1000,567,395,-435,330,1000,-763,491,152,697,-145,-566,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-961,478,-1000,-950,1000,737,-1000,866,163,0,-404,-1000,112,-790,714,-1000,907,225,-1000,992,653,312,536,-684,157,-1000,1000,1000,91,-675,897,990,-1000,1000,810,397,-476,-874,-24,-521,384,-1000,1000,-1000,260,1000,552,1000,455,663,1000,164,-1000,1000,103,1000,580,187,352,-742,1000,-1000,59,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonNull", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-824,-812,-520,-437,927,-206,862,477,795,-1000,1000,543,633,-263,-16,1000,-1000,811,-849,1000,1000,290,13,-1000,-1000,-258,-760,-1000,810,-443,1000,1000,276,161,-752,-99,202,781,-1000,-811,297,-160,482,-843,-269,544,-505,95,1000,-872,858,255,-540,-865,234,-164,280,-868,-486,589,-1000,512,-672,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-961,162,-1000,-683,758,-283,-708,447,249,-12,1000,-1000,258,-884,758,-1000,1000,-55,-1000,977,1000,784,-461,-339,80,-1000,1000,1000,-162,1000,1000,1000,-933,1000,461,887,-910,695,-580,-585,923,35,-368,-919,1000,984,169,1000,137,-98,1000,661,-1000,1000,-1000,1000,1000,699,-27,265,1000,-1000,364,267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-186,-222,377,839,878,-401,539,39,-745,977,-421,246,271,-540,239,229,-715,-300,-655,347,-383,349,425,-404,407,-883,-254,726,-872,-44,728,546,88,-13,-72,119,726,-243,945,379,-248,672,459,930,-733,67,-599,-761,866,-759,-268,278,-567,-979,-276,754,-137,948,-245,-476,-767,-9,625,534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-160,924,686,31,-1000,1000,-735,-287,419,-556,460,1000,-626,-1000,-1000,-1000,-545,-477,1000,527,-288,-103,-108,-71,609,-1000,-108,1000,266,210,-477,-1000,445,890,-271,130,760,-230,655,872,-302,696,-1000,-1000,-208,-773,715,-1000,560,-80,1000,-193,-239,1000,1000,844,447,452,-1000,-1000,1000,1000,-355,688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{102,-961,677,-277,437,-76,306,-627,-147,669,-1000,1000,-1000,478,1000,1000,1000,-1000,-45,682,-989,141,772,-204,-110,-474,1000,415,1000,1000,-642,-380,590,606,-1000,930,-791,280,-591,1000,-199,856,-673,-52,984,349,-1000,-1000,-185,-339,-103,347,941,-1000,157,439,566,-513,-904,-1000,367,670,621,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonNull", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-729,273,-639,-638,816,921,224,135,674,509,954,506,398,-73,-583,934,352,764,-406,874,-463,-396,254,741,-655,-119,-721,-51,186,223,324,610,-462,882,-290,-932,-553,787,-371,-725,750,-946,599,-781,362,367,-135,113,863,-460,968,-168,310,790,-931,-759,717,-237,776,466,-43,-775,198,-100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-602,734,-990,-354,395,710,-214,-8,176,-13,1000,-134,655,-65,-393,1000,782,848,-649,423,105,-1000,-669,1000,-187,-745,-644,49,-107,637,150,1000,-708,875,-335,-913,-1000,817,-1000,-318,974,-1000,281,-218,682,642,-62,-249,384,-1000,1000,-701,337,1000,-395,-540,159,87,1000,931,499,-875,579,459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{19,-263,1000,301,-292,1000,-807,72,-770,-1000,311,121,-1000,-866,-1000,-1000,-1000,851,1000,-227,-233,-40,-900,557,-507,89,-1000,-698,-953,-578,-1000,-1000,1000,-627,-81,-438,1000,-216,-313,924,-1000,322,941,129,-1000,-1000,-108,219,-821,522,895,-357,1000,914,525,-494,-658,586,-858,-1000,-547,813,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,-511,742,390,-331,-464,441,-793,-594,-174,-566,1000,-984,-344,224,931,-89,-494,-310,457,-281,-227,154,-463,-283,608,64,63,1000,682,-461,713,677,-34,-707,821,45,71,-459,697,406,238,-180,-600,178,-996,-691,-687,98,-1000,-8,479,121,-916,1000,415,-1000,460,-615,-645,-594,618,137,-722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{885,-1000,858,-166,542,408,345,207,-204,1000,-299,708,-301,1000,810,369,688,-1000,871,40,-1000,196,989,820,-283,-1000,665,-10,1000,1000,-593,915,385,-70,-1000,113,-326,575,-433,878,-666,238,143,328,583,1000,-604,-997,-369,-729,-24,1000,678,-946,-699,-156,955,-743,-497,-266,170,938,357,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,1000,362,93,-1000,-737,-206,-823,-1000,4,-333,-441,-681,-735,-1000,-587,-1000,1000,1000,-298,-556,-541,-149,-874,943,-175,-1000,414,-1000,-914,817,-1000,-653,-16,787,1000,593,-1000,1000,-141,1000,166,657,827,-1000,-121,985,1000,678,1000,212,-1000,-757,1000,368,109,-1000,924,-936,612,267,179,988,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.JsonParser", "com.google.gson.JsonParser", "parse(java.lang.String):com.google.gson.JsonElement",
            new int[]{-1000,484,-1000,-274,-180,228,107,-343,-676,583,564,-563,400,-216,-1000,-447,-871,1000,514,-7,66,-520,264,-801,481,327,-1000,219,-23,-832,1000,-1000,-1000,611,489,700,-573,-533,448,-1000,-261,-198,615,1000,-992,486,509,1000,793,1000,965,-1000,-1000,203,-82,400,-1000,1000,396,377,471,-306,686,-613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{1000,-183,614,-435,-659,-628,-1000,377,-1000,-195,63,-77,-1000,318,-491,1000,140,225,228,-1000,1000,6,-99,-335,-212,-424,1000,-670,-76,-223,942,287,1000,-239,-484,785,437,-73,15,-238,810,1000,30,-967,279,544,-850,84,679,826,172,-878,347,67,-940,-3,-1000,-191,-59,364,257,184,-462,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-1000,-99,139,1000,65,-137,-1000,1000,259,1000,-632,369,-103,-722,-365,109,-507,-807,601,74,-230,-62,515,-572,175,974,951,-9,-186,-680,896,940,-346,1000,-102,778,753,-1000,-546,-533,-153,1000,-165,331,905,-373,-1000,600,-1000,-1000,1000,-1000,1000,-610,-511,-1000,877,-384,-361,-372,1000,-721,-998,-241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{327,-399,442,111,428,773,1000,1000,-1000,312,-36,-192,-107,-1000,-1000,-379,-390,724,1000,-276,962,1000,-299,-763,-427,451,-1000,441,41,528,1000,216,-201,-58,-504,1000,1000,-184,-838,-504,75,1000,69,-881,1000,-753,-939,538,1000,917,26,-1000,1000,333,-662,-920,-824,-1000,-1000,566,159,160,-121,-918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-350,-475,27,-979,65,-1000,-447,242,-1000,373,175,452,-1000,-1000,749,-702,1000,-291,872,379,572,88,450,-1000,-819,-380,1000,142,1000,113,978,768,290,43,-1000,1000,1000,628,368,2,1000,452,-606,952,267,1000,-970,494,-698,1000,523,-996,753,-532,-1000,-48,487,-680,400,392,242,-713,-692,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonIOException", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{13,-579,-14,901,151,760,522,350,3,629,657,-561,636,822,-443,-262,-151,692,-708,786,367,-323,-307,670,52,-73,-641,-695,-629,77,-493,-541,-594,975,-60,130,-677,15,-417,-529,-594,617,-262,162,515,-659,509,834,-863,-851,-308,967,-913,-595,815,-449,336,-654,-986,-303,470,-39,-63,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-71,-309,453,-328,-335,-628,-210,554,-1000,1000,692,363,-1000,-1000,-438,-319,182,207,985,-306,825,110,171,-1000,-417,-1000,495,-400,93,472,984,473,-92,776,-652,1000,1000,118,-205,-581,557,1000,-224,142,724,-756,-957,276,64,664,318,-998,855,7,-806,-894,-52,-1000,143,183,382,-308,-385,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{1000,319,774,-687,-615,18,129,511,-852,-155,207,600,-695,-117,-632,431,222,491,469,-535,1000,282,-311,-285,273,278,295,1000,-106,1000,942,-795,878,-429,-209,1000,437,139,-54,-563,920,1000,-111,-1000,441,-1000,-1000,484,1000,1000,445,-969,542,-429,-909,-60,-944,-1000,-313,717,230,-269,-654,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-1000,377,-349,-1000,335,-1000,-257,-393,-1000,-1000,691,1000,-532,722,1000,-558,1000,-1000,857,918,-151,-183,-255,357,-78,800,718,1000,1000,1000,966,733,-135,-1000,53,1000,99,1000,-393,418,1000,1000,-776,1000,-523,1000,-798,920,-497,1000,1000,-753,-371,-245,-1000,996,1000,-306,1000,639,628,-1000,326,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{405,-485,-707,-1000,-108,-327,0,859,-1000,-1000,-649,168,-1000,-691,1000,-32,292,-152,1000,238,-1000,546,160,213,-234,1000,1000,-727,1000,555,711,171,958,-795,615,354,1000,752,693,1000,1000,-80,806,-215,566,431,572,-103,1000,717,939,-1000,1000,1000,-197,0,0,-86,521,-170,133,931,-42,-345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{150,-855,-70,-857,-925,-290,865,582,-1000,-295,-486,482,-262,-1000,-176,-1000,681,-155,1000,881,1000,837,79,-834,-707,-856,-1000,716,-1000,-1000,1000,401,-718,-300,8,848,1000,1000,-886,-310,1000,217,-526,-128,588,-3,-17,613,541,948,523,-871,648,104,-933,-698,-1000,-1000,-269,65,479,-231,-322,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{968,-301,926,-917,-1000,864,-16,1000,819,1000,-1000,-89,265,778,-982,621,-1000,1000,630,-1000,-1000,733,-1000,-184,185,-1000,-1000,-666,-1000,-665,1000,-822,620,556,904,-141,-1000,747,-1000,-807,-1000,-800,-333,-1000,604,666,528,754,1000,155,-1000,-1000,1000,-549,1000,65,-1000,-1000,-1000,-221,-644,1000,1000,-914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "hasNext():boolean",
            new int[]{-1000,-263,340,609,174,-228,-1000,1000,1000,-611,214,-189,681,158,1000,556,103,-267,22,-31,-1000,1000,-398,-7,59,1000,-174,985,-1000,886,1000,1000,372,-11,-928,920,0,-435,-1000,668,228,1000,70,116,310,1000,-1000,947,-843,786,1000,-681,1000,-900,-962,-307,1000,862,50,811,957,-518,-1000,-610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{249,-697,-103,-686,509,1000,-1000,-401,-136,-765,-1000,-64,-713,260,-514,-1000,-1000,814,846,-1000,-218,562,1000,220,825,-962,-88,-728,-276,-282,227,-241,-122,-412,-294,-105,-1000,-417,-1000,-485,-406,-326,-470,62,-956,1000,-1000,33,-606,-1000,545,1000,-359,-701,-662,996,1000,-792,1000,-379,135,690,17,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{263,-279,550,-151,-56,-748,-331,928,-655,-257,-113,156,309,250,-1000,180,-1000,-910,120,593,-1000,-369,-43,88,908,461,-872,-8,-117,816,-1000,848,567,485,-216,-513,416,275,-1000,-213,526,219,576,531,-706,950,-400,47,-538,62,1000,-339,-606,-637,1000,107,346,-666,-72,807,-467,1000,-256,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonNull", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{792,47,-133,-1000,-1000,892,451,-1000,-508,1000,-717,-529,-1000,1000,1000,899,-437,-978,268,-305,-862,-111,1000,706,208,-162,1000,-1000,1000,-579,-451,-1000,1000,1000,-604,298,1000,-479,-257,-197,-425,-885,919,-407,1000,340,-494,754,-195,926,1000,-805,1000,-1000,-1000,1000,847,-1000,-59,-269,-125,531,-32,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-1000,615,890,926,129,1000,104,481,1000,-1000,479,-793,1000,213,554,-469,787,-248,-556,1000,-1000,1000,1000,-1000,31,-82,-306,162,-317,-862,-847,883,-577,-874,-360,-18,401,-550,-568,-912,-727,-388,-182,979,-1000,1000,275,-428,-380,-235,763,588,-403,-81,907,-368,293,-837,-1000,-508,311,360,672,438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-243,-855,-517,-227,-1000,325,-1000,-1000,88,-148,-577,217,-359,33,1000,243,192,1000,1000,-647,-465,-375,67,-20,482,-125,1000,-1000,609,-530,-536,15,1000,194,-1000,1000,131,-1000,-283,-575,-277,1000,739,495,391,-817,-1000,-242,-1000,-868,164,810,515,774,-400,-264,626,729,-137,1000,1000,403,-116,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonIOException", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{307,425,-350,1000,-101,1000,353,-1000,506,-273,123,-423,-713,1000,1000,-476,650,1000,519,-418,-600,139,736,-582,420,128,1000,-935,298,-1000,-651,-664,457,-55,254,842,380,-1000,-298,254,-528,-1000,510,-464,1000,-705,30,421,-1000,531,1000,-349,849,-1000,1000,779,961,-1000,-1000,-815,1000,1000,100,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-677,-965,533,916,-499,-773,-261,178,1000,-461,123,-563,711,-1000,-89,192,409,-155,416,670,-465,-857,-1000,-1000,-46,191,-343,-434,-29,855,113,1000,-70,716,367,-177,966,259,-1000,-575,-1000,1000,492,354,-1000,46,30,-1000,-168,-987,534,565,-59,828,1000,-965,1000,105,-251,1000,305,-87,994,588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{92,365,29,-315,270,18,-545,5,-400,-107,-308,-573,231,1000,941,-880,13,956,-53,753,-692,900,950,-1000,700,128,905,1000,-29,-886,-664,-400,-251,-580,-1000,270,-497,-1000,95,-83,-1000,-654,369,812,391,268,279,908,-768,545,524,213,-806,100,-400,-1000,-912,-535,-1000,-1000,-53,-189,-112,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{109,-521,13,562,649,-865,724,772,-110,67,-689,-507,1000,-765,-1000,783,-586,-724,-1000,692,104,618,1000,-746,67,132,79,541,-1000,1000,199,-224,-227,-708,-166,-499,227,3,268,-520,932,1000,846,1000,331,364,375,-332,-31,1000,-128,16,-852,711,-400,367,467,-707,839,-405,-467,-1000,449,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{792,287,-298,-886,509,121,683,-543,18,1000,-644,-442,-713,333,576,303,-1000,52,-24,-1000,-218,-666,200,220,56,-257,102,-1000,488,84,207,-1000,1000,458,822,103,758,-399,-523,21,-42,-969,604,-570,1000,-485,-1000,441,-271,869,770,-833,1000,-1000,-1000,996,979,-436,1000,-429,662,-219,100,-754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{-220,-469,890,177,-1000,809,-216,10,-880,-1000,-156,574,1000,-587,-372,-643,554,-414,-908,488,431,-9,-875,1000,70,-632,-353,1000,-393,-31,236,-835,10,11,-281,-514,905,954,-568,107,554,129,-897,230,-1000,1000,425,-346,307,384,10,933,-853,534,-922,-725,293,-427,-285,59,-848,932,638,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{644,549,654,-146,-875,265,141,-828,-446,125,289,296,-8,-915,237,-1000,341,1000,1000,-157,-193,-166,-423,-274,23,283,-670,313,355,-190,574,-54,-1000,-1000,118,227,694,552,-977,-391,-565,-220,280,-1000,-582,-518,-98,211,-1000,-476,79,608,-96,42,-361,-45,545,-227,-672,190,259,-16,-342,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{10,-267,841,-139,691,890,-722,-147,-399,-14,-440,-356,495,-63,-449,-919,864,650,-64,72,-116,958,352,927,482,-759,-643,-891,566,-788,922,-283,-244,719,-289,-47,307,333,-945,-707,375,-977,-779,-892,209,271,-632,76,-556,-409,391,902,269,-359,-250,-452,902,-167,-654,507,570,-322,-487,455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:com.google.gson.JsonPrimitive", DEReplay.run(
            "com.google.gson.JsonStreamParser", "com.google.gson.JsonStreamParser", "next():com.google.gson.JsonElement",
            new int[]{60,-661,-986,-309,-678,462,-631,-834,243,-767,-440,-81,-798,-538,907,-716,-180,63,434,-798,-567,-3,769,229,430,-182,850,819,-594,-278,-969,-348,437,-616,-188,-294,-908,-188,-982,199,-738,-71,-20,615,-664,697,-959,-412,-154,-835,521,573,-730,86,-811,-16,381,-576,226,-267,266,458,698,963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.JsonSyntaxException", DEReplay.run(
            "com.google.gson.internal.Streams", "", "parse(com.google.gson.stream.JsonReader):com.google.gson.JsonElement",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.ArrayTypeAdapter", "com.google.gson.internal.bind.ArrayTypeAdapter", "read(com.google.gson.stream.JsonReader):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.DateTypeAdapter", "com.google.gson.internal.bind.DateTypeAdapter", "read(com.google.gson.stream.JsonReader):java.util.Date",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.SqlDateTypeAdapter", "com.google.gson.internal.bind.SqlDateTypeAdapter", "read(com.google.gson.stream.JsonReader):java.sql.Date",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:com.google.gson.stream.MalformedJsonException", DEReplay.run(
            "com.google.gson.internal.bind.TimeTypeAdapter", "com.google.gson.internal.bind.TimeTypeAdapter", "read(com.google.gson.stream.JsonReader):java.sql.Time",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
