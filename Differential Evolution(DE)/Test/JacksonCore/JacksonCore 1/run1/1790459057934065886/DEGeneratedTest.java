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
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "inLongRange(char[],int,int,boolean):boolean",
            new int[]{877,-851,140,103,-34,116,589,493,-788,-957,-737,591,513,915,407,520,790,-598,829,-464,-197,905,-593,-325,128,-876,180,-913,660,360,314,465,216,-411,-679,-100,-794,-92,235,-737,-815,-592,-831,799,118,-280,648,-12,-341,-436,436,-79,-608,439,-829,-530,69,296,-918,336,-839,483,285,697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "inLongRange(char[],int,int,boolean):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "inLongRange(java.lang.String,boolean):boolean",
            new int[]{1000,-1000,1000,16,300,-873,597,-898,-1000,-1000,1000,-1000,1000,-395,193,621,-245,-328,-187,-934,-781,-310,-1000,1000,1000,1000,-849,853,1000,-599,1000,623,719,-1000,-223,-675,-699,1000,1000,1000,-396,-644,-472,1000,-1000,829,-686,3,-498,-686,-1000,-1000,518,1000,-565,105,-190,-919,988,21,-1000,-1000,740,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "inLongRange(java.lang.String,boolean):boolean",
            new int[]{93,398,1000,719,945,1000,670,462,-1000,1000,956,-170,-153,-1000,-1000,1000,-1000,1000,502,446,127,-740,-815,591,1000,-266,-1000,-619,-44,278,-456,768,-349,-202,1000,-1000,-1000,-573,-1000,-890,-712,-166,1000,791,-974,-1000,-1000,-101,-1000,-371,-971,561,-1000,655,-414,718,213,-538,-914,-723,-445,-85,496,198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "inLongRange(java.lang.String,boolean):boolean",
            new int[]{-788,72,-402,-179,784,-443,30,832,-884,419,-331,84,993,575,456,331,-752,-932,243,-936,466,-675,-275,-194,217,5,179,179,-852,668,-796,619,-725,-789,-451,-582,170,439,-610,-592,-629,489,950,-948,329,-119,245,-37,443,916,-100,-41,-616,842,-504,-380,-272,792,-296,-963,209,-199,-438,-68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "inLongRange(java.lang.String,boolean):boolean",
            new int[]{746,619,-239,-272,-85,776,-67,-295,393,130,692,-272,751,-82,69,-343,-475,-719,-152,353,347,152,990,-327,-11,319,-389,-430,-890,-876,669,897,524,959,-584,-715,-842,-850,950,161,-519,-846,-278,-636,196,-922,-245,123,226,-564,493,-804,632,823,695,879,-6,-325,-393,-716,-4,692,748,779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Double:MzA0LjMxMg==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsDouble(java.lang.String,double):double",
            new int[]{-283,-304,302,312,-648,829,980,-929,857,596,595,-286,676,854,-521,141,386,-220,818,903,566,-721,-27,417,345,710,-583,-636,-592,5,23,-815,148,948,-384,705,-483,511,-370,388,665,441,456,-498,-67,-976,-315,-438,945,-72,775,-181,612,-674,917,-729,-292,134,-136,700,-648,-876,307,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsDouble(java.lang.String,double):double",
            new int[]{222,519,627,768,-891,349,823,10,-847,663,163,-144,504,-762,697,92,-887,796,71,513,378,680,595,-137,-953,695,269,119,724,-223,-838,640,-655,-72,663,-173,764,893,-927,-551,-812,-564,760,514,635,746,-312,-308,-205,496,-513,-327,-275,400,-539,-426,-320,-289,923,513,998,52,-940,830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsDouble(java.lang.String,double):double",
            new int[]{178,913,-981,412,-678,389,455,702,-614,9,712,-257,-468,573,-128,-923,-478,-409,615,-869,-899,135,667,-631,-541,-763,206,919,675,268,618,490,-972,522,170,517,844,-736,-908,935,-836,876,-668,781,-86,411,802,894,-484,877,351,371,-119,847,-138,-808,-378,-351,-105,-605,-43,-713,-863,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsDouble(java.lang.String,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsInt(java.lang.String,int):int",
            new int[]{758,18,-74,-6,-1000,-16,879,-697,-922,-1000,-554,638,-48,-321,1000,515,507,283,767,762,205,-560,495,-246,-253,-813,-105,1000,1000,-932,-275,291,1000,-211,119,219,125,943,-872,281,-343,-367,-720,1000,-914,762,-290,-647,1000,-641,-139,344,-996,739,-1000,-277,-1000,233,-72,566,400,-63,-340,672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTg0OQ==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsInt(java.lang.String,int):int",
            new int[]{362,-849,609,-238,-284,581,-798,-969,-903,-408,-765,-636,550,-569,263,-102,-507,-660,-772,-617,333,-440,553,229,903,-784,653,-770,72,474,425,-719,248,905,920,474,-959,15,-779,-463,-154,-442,-646,-247,-242,416,956,-801,885,-402,386,-559,-142,-785,964,-332,163,-837,-863,640,738,709,-524,822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsInt(java.lang.String,int):int",
            new int[]{78,-1000,-464,165,-702,-993,1000,280,-138,368,689,-77,1000,-1000,-161,-20,-353,634,1000,1000,1000,-1000,-714,1000,556,-1000,-616,762,896,133,-323,589,1000,1000,-351,-20,-258,-1000,850,-229,-263,1000,-555,1000,1000,907,-116,415,705,54,-619,-875,84,-77,778,-173,-179,-1000,863,-33,-455,-1000,117,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsInt(java.lang.String,int):int",
            new int[]{-63,1000,599,599,498,-64,-1000,571,-899,1000,-1000,-1000,-1000,1000,237,223,-431,235,-686,-715,221,152,-1000,-169,-255,567,-103,-159,1000,-122,442,104,95,-1000,484,-853,515,-231,1000,1000,177,-257,630,315,989,-135,-371,-238,155,-52,375,446,926,166,1000,1000,455,743,-772,-58,-85,-479,-1000,833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsInt(java.lang.String,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsLong(java.lang.String,long):long",
            new int[]{-362,-796,-406,-451,-706,-383,813,-946,-417,73,-561,-779,-491,656,-847,588,41,-229,442,-20,-919,172,-201,-369,-832,481,-162,-482,439,-223,284,417,78,655,-602,184,499,826,379,620,-136,824,-532,871,185,-346,79,-195,195,-654,133,27,498,-744,-33,304,-954,-267,-350,-984,-81,269,-608,992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Long:LTYwMw==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsLong(java.lang.String,long):long",
            new int[]{355,-603,232,-657,860,981,-321,855,-503,350,-223,-274,-481,-702,-734,-11,777,-232,302,917,557,600,561,969,-988,647,458,-107,-97,-517,637,-822,76,802,494,52,88,284,-569,-932,-202,-844,778,-77,333,989,-106,630,-182,366,799,-603,565,-29,-103,671,-54,-616,831,661,134,397,-687,-642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsLong(java.lang.String,long):long",
            new int[]{653,107,35,281,632,887,608,245,-778,874,-628,624,-171,-787,-522,-1000,-651,-928,437,1000,315,934,763,970,-1000,397,-151,-1000,32,-1000,673,75,-848,1000,-46,754,-443,-514,255,-65,-1000,-281,882,-694,185,-43,-984,858,725,926,1000,-913,1000,-1000,-428,1000,143,-1000,-414,1000,935,1000,-329,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsLong(java.lang.String,long):long",
            new int[]{-932,44,911,-400,538,-342,183,-981,-867,303,303,-129,-74,-324,391,677,-746,-799,-898,379,-306,438,-256,278,-520,-822,152,-606,-68,-391,-109,-881,256,-175,-61,-989,-963,-423,-20,-586,130,-318,-378,214,-729,-227,478,514,728,-824,-140,-769,544,634,55,-273,554,311,-381,444,-770,45,964,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseAsLong(java.lang.String,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:OQ==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseBigDecimal(char[]):java.math.BigDecimal",
            new int[]{-125,441,-79,202,513,760,-1000,101,-836,1000,-1000,1000,-138,-1000,-590,1000,-819,1000,499,706,-347,530,-390,358,-774,24,532,448,340,-832,906,-99,-845,-197,-481,642,-560,326,247,1000,950,683,-334,-725,-771,-1000,3,-563,-783,-517,-7,500,-510,-167,-852,-229,-263,-1000,448,495,-179,144,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseBigDecimal(char[],int,int):java.math.BigDecimal",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTguODRFLTM1NA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{-778,-884,-511,-356,251,355,868,-991,892,591,547,837,921,-716,693,-168,655,-146,418,-315,389,-565,-427,-258,620,-68,-609,417,-609,723,2,649,-909,524,347,697,610,-764,144,592,-127,-118,210,695,473,175,654,-20,365,678,241,-106,407,74,590,945,-102,-410,-246,-469,-769,324,-790,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Double:OTg3LjYwOQ==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseDouble(java.lang.String):double",
            new int[]{5,-987,196,609,642,322,361,-321,210,-330,610,428,583,264,174,378,805,957,333,884,685,658,352,511,-143,412,79,462,186,-163,515,-617,236,-470,705,-737,351,-574,485,-843,-167,-840,-204,931,-304,8,543,-611,-495,-887,343,-252,939,-583,837,688,-120,937,763,951,-933,859,-546,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzM=", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(char[],int,int):int",
            new int[]{839,-47,862,-630,15,96,730,-330,-842,587,-112,-506,-690,901,-880,988,-66,-693,139,-704,-962,-476,117,-915,908,998,748,87,730,595,-352,-500,941,-240,779,-169,867,865,-432,17,908,-141,-644,-477,-641,-845,956,-467,-206,-843,188,297,-132,825,-505,-751,399,-769,997,603,607,-791,-311,-223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTEwMDA=", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{-573,-1000,-317,1000,513,-1000,-197,-1000,1000,-563,466,-75,-1000,-69,172,804,-643,940,-294,-1000,762,664,-391,887,-515,1000,-536,-892,85,780,-230,1000,-543,941,-558,1000,403,324,-421,517,447,-1000,1000,-206,250,623,1000,1000,579,901,376,1000,912,-891,294,777,-1000,568,1000,1000,-340,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{69,1000,8,-760,-103,-590,-207,1000,349,-1000,-1000,-1000,934,-1000,450,646,1000,-520,413,142,-646,418,16,25,-171,-492,-26,751,-631,-957,-1000,-1000,34,-768,398,-954,449,-1000,965,-272,-726,-1000,-167,-914,-233,449,386,-867,-274,-975,570,-437,506,1000,803,16,-400,-1000,-714,-963,-365,616,200,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Integer:ODQ1", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{-749,845,338,949,515,565,-636,-648,283,909,1000,757,-805,-843,-1000,-193,-772,20,374,-213,-1000,-758,-1000,1000,-404,-844,-190,-788,-267,261,296,-192,-558,-933,726,653,-352,743,-766,965,804,-650,-271,303,-762,1000,-680,426,-790,-602,961,-1000,-384,543,-1000,-563,1000,1000,1000,-1000,350,-157,797,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTE5", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{-972,-119,-142,806,175,257,-685,-695,1000,852,575,2,-704,-36,-816,567,109,-1000,1000,-1000,21,1000,-1000,-176,937,778,62,-892,663,596,49,-649,-1000,223,-390,-807,-22,717,-591,527,326,746,-351,256,202,742,482,746,999,137,748,-1000,-572,-767,79,-482,510,-444,343,-698,-362,306,644,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{-663,-1000,981,301,-312,-1000,-437,11,1000,33,1000,-873,-1000,-1000,-816,-128,-385,397,894,-1000,-1000,-742,-1000,1000,-1000,-1000,-357,-881,-1000,1000,782,1000,482,1000,-720,1000,-569,-534,-1000,280,368,-1000,-767,-814,-468,156,629,1000,1000,1000,1000,234,1000,-561,-930,867,-114,-390,1000,511,967,670,182,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{-247,-79,425,-400,-15,298,-759,-186,-785,728,-166,53,604,711,-496,-651,-543,-1000,951,-295,1000,814,-295,-207,-55,1000,1000,29,810,-310,225,-194,-1000,-1000,1000,-125,-870,176,-429,-860,85,73,767,-403,1000,476,-1000,-747,169,2,-342,-1000,-788,-85,-971,-1000,1000,339,-1000,-33,-365,1000,-426,-872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTgw", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{643,-80,-866,517,499,985,-287,-661,573,-244,-199,103,-592,-164,30,-633,659,-758,-860,-69,467,53,30,464,-648,881,709,-508,202,-885,-883,-266,-170,-857,84,-332,745,-147,292,969,252,-441,968,967,-140,-757,292,661,-976,-539,-4,960,-259,641,-504,831,231,578,14,789,992,930,651,-199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{-507,-43,-1000,-54,214,-483,-322,480,515,-292,-1000,-784,1000,806,-148,1000,195,-285,544,-1000,1000,659,406,-696,1000,1000,-90,442,427,-1000,-82,350,14,-178,534,-1000,315,534,1000,-609,-307,-357,622,-352,588,503,943,175,331,497,-1000,8,400,372,-45,-636,5,-734,62,743,-696,-748,54,-804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{-121,545,-631,-720,-385,142,-879,-130,-119,143,462,628,337,644,-816,-128,-541,-234,-103,851,219,-742,-4,-389,-884,934,750,-746,-791,-816,52,-649,616,-957,-390,-807,175,-607,-29,280,-572,746,-351,991,894,742,-129,746,743,45,-63,120,-620,590,79,-645,219,-693,805,511,967,-607,212,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{-945,764,-59,602,300,347,-131,570,314,625,762,-242,-862,-465,-415,-655,388,-834,-755,534,-950,-831,-815,847,-328,-932,277,-349,95,485,-433,-67,-252,-445,-312,320,723,880,-635,994,312,-757,-680,-244,-469,24,105,-189,-761,-251,913,-977,-77,529,-739,-636,940,297,583,-921,79,667,238,687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{207,-471,578,-627,276,293,-509,-673,338,260,576,-968,18,-879,834,-483,-661,754,-423,-959,462,-667,-723,18,619,-946,760,-518,-373,-540,12,606,210,592,140,703,-428,304,628,-134,112,-83,910,-61,40,135,510,-123,704,612,-472,706,933,-102,-935,-718,-608,-350,-643,828,42,-110,-166,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseInt(java.lang.String):int",
            new int[]{-547,-774,-418,424,-602,-15,347,971,-697,446,248,-784,-786,504,705,-522,736,-813,-781,-177,219,659,414,-486,625,281,761,-334,913,916,-789,350,-356,946,-729,285,720,534,-404,999,-377,-452,121,-140,359,-731,943,-229,419,499,-321,-562,-506,-975,-105,-854,-553,-480,322,732,69,911,54,-924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(char[],int,int):long",
            new int[]{-920,245,-327,819,-196,593,763,893,-79,762,365,194,583,-767,195,392,1,157,-771,-769,-529,917,957,-700,-102,25,-404,189,-41,544,348,-260,88,-982,388,634,-885,705,-553,193,570,814,-462,-73,-583,380,682,-897,-713,473,-657,-582,-822,-549,656,-302,-980,-818,541,492,-523,-698,-690,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Long:LTEwMDA=", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(java.lang.String):long",
            new int[]{739,-1000,-122,-1000,227,1000,-741,905,248,845,-611,-617,832,-991,-194,-826,-635,685,-938,226,-614,-143,-618,1000,-574,-958,-855,1000,-104,-1000,-132,-683,-969,1000,784,1000,1000,1000,-479,484,-506,614,-48,-840,-1000,345,210,347,-943,-245,-515,-623,467,-104,-382,-292,555,-823,22,-1000,-878,-1000,272,721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(java.lang.String):long",
            new int[]{789,-1000,156,174,828,-562,-1000,-457,248,943,1000,604,-1000,607,-62,-712,-1000,836,-174,-530,-1000,-434,43,-835,745,582,-855,-769,-315,290,-1000,241,-443,-657,-821,1000,-464,976,-479,484,-927,1000,950,-868,975,541,62,-680,-674,67,-1000,125,326,1000,794,-131,1000,1000,-1000,677,-619,-1000,272,-119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Long:NDQ1", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(java.lang.String):long",
            new int[]{100,-445,-924,-630,-517,186,345,-467,268,-25,-468,168,674,-238,197,43,-822,666,395,188,158,-213,-116,-692,-920,55,-17,538,-288,959,-909,-202,-976,753,588,-305,212,681,127,-339,552,-185,169,803,733,-861,-983,800,-316,-306,-403,134,-26,-232,-272,-128,-605,414,768,-659,463,880,-874,-927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Long:NDQ5", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(java.lang.String):long",
            new int[]{756,-449,-270,929,-853,-986,-183,480,357,-216,139,762,427,-511,214,684,-526,489,235,48,-114,-542,165,-972,-236,934,-393,-908,-321,908,550,489,-998,-615,-196,82,-166,-174,-779,-944,-151,375,-337,827,269,884,-945,-664,-393,861,-138,835,-168,697,845,266,-843,-222,42,373,806,425,603,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(java.lang.String):long",
            new int[]{969,-1000,-955,32,666,1000,-325,106,116,112,449,-1000,993,-1000,-460,-904,-1000,1000,-1000,-202,-304,-431,-981,-267,260,164,-869,212,131,65,-596,848,-1000,128,996,-258,978,1000,-533,1000,232,-200,832,-1000,-165,-157,-315,269,-1000,-129,594,136,515,386,-993,-889,-696,401,-61,198,-997,-652,-666,-302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Long:NjU=", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(java.lang.String):long",
            new int[]{707,65,-498,1000,-235,-996,856,141,303,-503,-368,1000,-451,889,1000,285,-54,57,1000,-770,-41,56,-1000,-1000,-61,945,-77,-1000,-416,523,689,896,-55,897,-400,-1000,-400,-569,-625,482,452,125,322,1000,-376,1000,-615,-196,-178,-64,738,737,-918,914,924,1000,-131,400,-1000,920,791,197,-116,-17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(java.lang.String):long",
            new int[]{-642,-1000,287,-371,1000,1000,-737,-156,75,307,-317,-819,438,-1000,-186,-1000,592,-847,-305,1000,-549,1000,-101,902,-1000,-1000,599,1000,-631,-1000,-1000,-1000,-566,839,328,-308,185,407,-375,961,-172,-123,946,-1000,-690,-860,1000,1000,348,-515,-345,-1000,64,-809,-1000,-641,1000,98,-341,-637,-1000,-606,-1000,173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(java.lang.String):long",
            new int[]{1000,603,-527,-1000,-872,923,-1000,1000,-1000,846,-885,-1000,1000,-1000,419,-964,-927,1000,-917,-696,-1000,-491,-1000,1000,629,444,-1000,1000,371,168,544,494,-1000,1000,1000,1000,1000,205,-1000,-36,122,80,-273,-1000,-1000,1000,-1000,-1000,-1000,357,-897,350,1000,1000,1000,-300,-630,1000,344,-533,-304,-506,1000,325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(java.lang.String):long",
            new int[]{-306,-272,416,32,1000,-860,-1000,-1000,1000,41,1000,793,-1000,1000,1000,-466,-457,-347,58,-38,-1000,850,1000,67,-710,-984,467,410,-1000,-1000,-208,87,350,686,-1000,-561,-1000,1000,-780,-561,-931,483,875,-666,-360,295,916,1000,-61,-521,155,-1000,-70,-207,-491,920,1000,1000,-1000,798,-918,-1000,-299,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Long:Ng==", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(java.lang.String):long",
            new int[]{-641,603,-208,376,1000,-265,-51,268,1000,-1000,1000,-13,-775,-146,363,-1000,-1000,979,-370,-996,-660,-363,647,-1000,31,206,1000,-351,371,-246,-1000,494,-210,705,-782,-1000,-775,331,51,523,1000,-200,662,-1000,642,513,123,1000,1000,-237,-897,484,-1000,552,-1000,418,486,1000,-1000,-639,-1000,-463,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.io.NumberInput", "com.fasterxml.jackson.core.io.NumberInput", "parseLong(java.lang.String):long",
            new int[]{-309,753,-768,241,321,952,-129,-437,-128,-659,-881,678,897,998,507,-878,-692,643,-192,763,-678,482,-669,-967,-559,-664,969,946,667,-67,690,-622,-730,-820,881,716,-742,126,-742,-222,-67,404,-147,-366,-143,-23,-947,893,-37,-826,-408,888,-459,210,-33,201,-582,-359,-197,25,751,-458,-600,951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "append(char):void",
            new int[]{-257,166,-12,359,86,-411,704,-230,-161,73,-896,449,-379,394,-231,554,-600,299,587,721,248,-702,682,840,-776,-70,774,-302,243,-97,209,597,-62,395,-984,-992,-9,-694,668,-913,180,757,-75,439,-527,-710,139,916,-94,-678,-304,-579,-297,-796,-117,696,93,946,429,-81,656,-862,163,535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "append(char[],int,int):void",
            new int[]{255,1000,-819,579,-1000,-708,-1000,619,200,1000,19,-743,-248,38,649,-1000,466,479,973,205,-903,210,-378,1000,-648,285,612,-1000,130,501,633,-1000,-1000,468,-1000,-197,910,692,90,1000,456,-492,-211,759,-364,-445,295,396,-452,612,-509,-997,510,-663,-253,-1000,-976,211,-433,-357,-1000,1000,-204,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "append(java.lang.String,int,int):void",
            new int[]{-581,-563,-742,298,258,192,-260,-787,-9,37,-983,-518,-167,-937,498,-931,550,-353,443,-114,-179,389,-760,678,845,546,175,-467,-112,-371,-931,-344,485,102,-280,89,-421,342,146,65,-25,360,-675,-173,231,200,-685,-94,-705,347,-562,-930,-923,-968,-448,491,-540,866,344,10,-752,919,54,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsArray():char[]",
            new int[]{-444,-910,-173,196,-810,800,591,-762,-197,-585,-493,-941,-754,163,995,-602,245,-705,729,457,-267,283,-640,892,976,-694,643,-557,-159,-876,-222,-103,-467,-169,168,135,476,212,-485,-140,-432,362,-815,266,42,-859,141,395,-651,583,357,-791,75,-96,618,-482,-243,-53,-124,-181,-362,-973,-99,814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsDecimal():java.math.BigDecimal",
            new int[]{-141,851,-221,309,596,243,795,-48,884,741,406,-843,917,553,-561,167,390,372,-548,829,952,67,6,-887,-783,487,-527,-303,-635,780,-284,941,-632,500,978,-220,365,-208,868,-665,838,1,487,-616,-101,-876,-607,-805,-812,-257,-619,-285,703,-719,860,-391,207,-257,981,-787,766,438,-382,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsDouble():double",
            new int[]{-302,224,402,-238,218,-533,476,586,-491,554,180,-383,-788,458,-94,464,931,131,-306,498,85,277,280,-34,566,67,-938,592,-434,-631,-159,669,454,-934,973,235,-899,-300,385,-910,-334,-122,-198,118,-433,574,504,688,33,-148,919,-279,499,344,470,-558,-86,-709,210,799,-14,-39,-992,69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsString():java.lang.String",
            new int[]{-20,-17,-243,-759,-204,71,-313,-973,46,159,656,539,-937,997,457,-808,951,8,722,-793,99,-347,457,-478,208,839,-529,606,982,560,-38,-767,164,-839,940,614,-569,-441,-89,-602,-188,583,-696,-988,-247,-843,-504,569,748,-493,-32,144,-496,-226,-885,593,57,-540,-446,-680,-81,-999,-630,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("ARRAY:[C:200:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "emptyAndGetCurrentSegment():char[]",
            new int[]{447,-135,468,926,819,-834,262,-255,142,-360,-245,72,-113,410,-620,-707,777,394,-589,-431,-671,6,682,-442,-690,-123,-594,-274,-218,441,535,-284,-116,-508,-279,827,-204,-644,-605,-320,-588,-785,-259,382,-802,-727,406,106,-586,-21,-320,901,427,-697,580,757,202,191,-234,-523,-785,761,-445,-246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "ensureNotShared():void",
            new int[]{-462,-808,-175,383,985,847,-138,514,487,-828,-300,140,-242,651,-956,29,654,828,-53,237,-939,-695,469,197,-222,-612,827,-702,694,-533,220,535,36,-384,-227,-802,109,-225,-555,-476,542,-650,135,714,-228,117,738,-204,-721,889,-577,437,902,-416,-962,-4,-543,37,206,-130,841,-814,426,482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "expandCurrentSegment():char[]",
            new int[]{-833,-314,289,-158,-646,-39,619,-437,598,436,-250,-92,-716,-256,167,793,-704,703,-703,-311,-203,361,210,-315,-669,-558,-969,-897,107,-659,414,-190,592,-917,420,-486,587,-741,793,840,877,837,232,780,-784,-21,576,247,-63,-496,987,875,-219,-522,-786,966,-301,-428,-212,526,-700,282,621,722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "finishCurrentSegment():char[]",
            new int[]{-25,896,-419,87,-332,-851,-271,-349,85,499,-790,763,885,642,-440,32,280,-965,184,540,-721,-111,282,160,448,534,678,588,655,568,510,-658,-490,-254,711,-478,-559,-330,277,268,-313,-36,424,542,913,-123,-109,99,-453,-974,-79,340,-482,318,-287,113,-393,44,523,-539,-699,542,-996,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("ARRAY:[C:200:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getCurrentSegment():char[]",
            new int[]{-922,70,652,683,-196,82,-447,-521,774,-393,-871,-831,676,981,804,-283,191,-500,-286,-84,-520,-493,125,-618,-557,-835,227,42,521,670,521,56,23,-471,-816,224,605,297,299,-210,379,664,655,-739,-611,-805,-85,361,-325,122,-599,-419,-993,-366,496,-107,-470,-157,689,-833,-8,-743,-308,-421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTI3", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getCurrentSegmentSize():int",
            new int[]{-112,567,896,-768,138,656,246,824,-271,-146,491,-210,613,348,-758,520,-153,-789,-817,-669,133,-443,-103,-157,648,-440,-676,-315,390,28,460,-927,268,-749,605,-240,0,193,935,45,-487,-270,-553,159,-28,627,468,149,859,179,786,-394,545,-922,238,-950,742,-66,-907,616,-138,-904,-57,780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getTextBuffer():char[]",
            new int[]{-928,640,448,853,23,305,-212,-603,-923,708,-259,514,375,-234,543,-975,-678,478,-674,357,-650,638,623,-352,-274,-21,-249,864,-847,763,847,331,-190,-843,-781,-243,963,-820,-763,511,289,500,-3,-322,675,-115,-336,-728,406,-265,-454,860,-646,-190,754,798,-900,-22,-440,320,740,625,493,-212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getTextOffset():int",
            new int[]{-887,-751,-874,112,-971,45,-702,396,-622,789,723,277,-114,778,-102,414,-187,-7,-332,-146,606,-474,959,-740,692,455,-280,-608,808,700,-398,-354,-126,-83,-501,239,-424,110,-722,570,517,313,776,-799,131,-565,451,-51,339,143,962,-354,-613,544,-620,263,-757,-674,-258,-684,-528,30,-782,-280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "hasTextAsCharacters():boolean",
            new int[]{834,846,-621,-932,-214,403,-810,-925,342,-18,-135,-927,-195,-780,50,-977,885,-682,315,-672,-144,-170,333,-924,103,-495,15,-271,802,460,387,680,-638,-312,713,895,-784,-811,48,162,-427,-133,255,316,801,724,-659,-695,281,-131,931,900,763,-605,-360,474,515,251,-812,358,-93,-74,200,999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "releaseBuffers():void",
            new int[]{676,664,784,-996,-936,291,-551,260,97,17,834,-420,888,482,141,-996,-794,-246,-211,-240,572,15,-900,561,-2,805,693,-76,373,-898,-903,78,143,-633,-770,-361,-905,-455,-854,897,828,-10,686,-688,-189,-703,-435,-396,-217,97,805,675,702,-982,392,551,-528,219,-396,493,-807,-829,492,479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithCopy(char[],int,int):void",
            new int[]{-235,-609,-204,182,801,-248,-934,870,642,387,-644,887,-297,-716,247,-763,-887,36,-654,920,339,-843,-715,-511,577,-160,523,972,-346,-387,454,-382,-286,-982,-839,-892,-409,-413,41,574,-557,172,-528,-950,-58,984,-271,855,145,583,-574,-227,985,490,-92,-106,-694,-901,207,-487,283,-860,-531,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithEmpty():void",
            new int[]{738,-581,798,254,801,154,-867,-747,61,-961,506,-8,32,-268,368,-374,908,559,667,-388,-496,-415,97,-770,834,382,782,233,280,-611,-971,-906,221,577,-891,769,-727,-243,689,203,-190,-653,499,892,322,634,-915,-240,-638,665,101,298,-708,793,472,-512,875,-440,998,381,-123,164,481,-668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithShared(char[],int,int):void",
            new int[]{-83,-14,-425,59,210,627,-262,-443,926,992,954,972,217,352,-837,797,370,-170,947,634,-716,339,940,957,924,-412,-171,800,-475,744,-372,-859,-564,-848,-51,-312,17,95,-617,559,706,29,-233,-106,-467,-87,111,-157,690,315,-713,553,-461,-145,156,798,422,-852,796,723,-8,-42,691,787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithString(java.lang.String):void",
            new int[]{129,381,-640,153,-641,-720,842,-561,210,817,150,-614,807,938,363,-81,988,67,445,-541,-950,589,193,51,-809,-413,-127,806,-960,665,-54,414,171,-281,-966,-870,-950,187,-881,746,-541,-535,-142,-181,128,-57,848,687,-963,-572,-295,655,647,607,872,156,194,-394,-748,-365,70,-233,-56,454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "setCurrentLength(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "size():int",
            new int[]{-436,-504,-136,-476,-559,-269,-564,-830,-60,778,-511,684,-851,2,-68,81,-834,-427,-690,-962,339,-315,-225,-870,-150,-704,374,-314,399,939,364,726,-502,-540,-960,942,-759,779,951,-745,341,170,-664,-849,670,-36,81,-325,-417,61,-410,-254,910,-796,585,-710,-353,559,24,-445,93,-175,-912,-35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "toString():java.lang.String",
            new int[]{702,-793,600,632,197,513,126,-905,333,52,-107,977,338,265,745,-940,-655,887,634,49,635,-366,-243,467,233,889,290,908,-408,878,46,-935,568,606,623,812,-608,-829,935,944,942,-9,328,-845,-758,927,-30,313,836,355,623,-820,448,785,-473,322,-840,-163,658,167,246,-935,-627,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("ARRAY:[C:5:24:java.lang.Character:LQ==:24:java.lang.Character:LQ==:24:java.lang.Character:Ng==:24:java.lang.Character:Ng==:24:java.lang.Character:Mw==", DEReplay.run(
            "com.fasterxml.jackson.core.io.JsonStringEncoder", "com.fasterxml.jackson.core.io.JsonStringEncoder", "quoteAsString(java.lang.String):char[]",
            new int[]{283,326,974,-364,663,-905,-21,191,-811,738,-361,162,-345,-340,775,-21,782,924,-422,-110,914,461,600,-723,594,-726,613,419,-832,-887,-365,464,651,72,359,-609,-648,94,-789,-714,975,462,-705,-589,276,15,-375,205,-268,44,179,-373,-865,-381,575,-932,-450,-582,-533,-589,636,-925,90,797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "com.fasterxml.jackson.core.io.JsonStringEncoder", "com.fasterxml.jackson.core.io.JsonStringEncoder", "quoteAsString(java.lang.String):char[]",
            new int[]{-131,1000,1000,-751,1000,-1000,522,335,-93,-255,-410,-281,-870,-814,-689,-588,1000,-431,75,236,1000,95,74,-766,1000,-63,293,-757,822,-1000,165,1000,885,-267,-215,-1000,-542,-992,87,-1000,1000,122,408,-720,-736,778,-1000,206,-1000,1000,51,-260,-993,-631,742,-1000,-688,-821,775,217,-824,118,662,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SegmentedStringWriter", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "append(char):java.io.Writer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SegmentedStringWriter", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "append(java.lang.CharSequence):java.io.Writer",
            new int[]{527,-724,773,398,-756,-641,-618,-993,804,-555,-823,498,636,336,-66,-284,364,-641,258,-632,444,25,-337,-9,334,-155,-835,-426,588,438,-463,225,-816,208,388,-85,-896,-601,-839,195,-249,122,807,2,-887,74,424,-786,787,99,477,-576,-89,489,-693,-429,883,968,655,536,-687,692,611,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SegmentedStringWriter", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "append(java.lang.CharSequence,int,int):java.io.Writer",
            new int[]{246,737,-843,-903,-269,-359,525,-660,-300,719,-696,-191,-305,560,848,318,51,661,292,383,127,661,843,-640,-992,-410,951,496,99,981,-575,-397,324,-932,-876,-23,-991,225,-484,663,946,321,72,-829,628,692,264,380,183,-875,-251,502,166,-649,194,705,12,-618,211,-720,-273,-725,805,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "getAndClear():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(char[]):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(char[],int,int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(java.lang.String):void",
            new int[]{924,-988,-820,202,-677,378,-99,601,352,169,-350,368,-378,325,417,-290,-808,-517,-992,-717,501,-162,553,978,848,-111,-119,-696,-912,885,-995,-722,520,778,-170,578,844,-399,592,535,475,311,508,478,-474,-650,-344,951,322,-563,192,-672,441,-548,675,517,969,635,-504,-444,-94,817,169,208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(java.lang.String,int,int):void",
            new int[]{-186,804,214,-253,831,21,828,582,-207,-657,401,564,-802,-443,-575,-677,258,-571,-257,856,-61,457,796,-657,-293,-200,-666,618,476,-173,848,-737,-562,38,-338,-610,-903,503,-182,355,-899,-583,684,873,-708,899,-424,330,-263,731,555,410,479,-619,67,-384,236,-644,114,-276,-687,-294,222,773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "appendQuoted(char[],int):int",
            new int[]{891,-176,-498,264,-319,555,682,-549,-526,-743,475,-91,-705,778,-331,-101,-391,-918,-220,-417,-487,272,354,-53,108,920,-994,452,-258,-762,-49,880,449,202,792,-916,-76,-872,-197,-619,-299,-606,595,185,-226,-988,135,149,873,-428,-623,-444,-157,-274,-556,-15,-897,-185,262,397,76,507,801,-304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "appendQuoted(char[],int):int",
            new int[]{710,68,577,166,-163,652,946,-156,-628,-946,-795,-583,303,-137,168,860,-133,-15,140,201,-578,574,-379,340,-290,-837,-681,228,-363,770,-839,598,-617,-710,-933,-322,658,911,-335,349,224,-552,832,-954,-606,-473,-28,417,207,-634,281,161,161,524,-104,-161,-9,-550,-203,-528,814,924,-410,734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("ARRAY:[C:9:24:java.lang.Character:LQ==:24:java.lang.Character:LQ==:24:java.lang.Character:Nw==:24:java.lang.Character:NA==:24:java.lang.Character:OA==:24:java.lang.Character:Lg==:24:java.lang.Character:OQ==:24:java.lang.Character:OA==:24:java.lang.Character:Mw==", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "asQuotedChars():char[]",
            new int[]{642,490,-235,-748,803,-17,-502,785,687,372,531,-134,493,541,-945,-470,762,-173,-97,-666,120,-977,436,609,-428,-431,798,311,621,177,-955,560,-556,-627,762,516,-959,885,-358,-827,-152,649,203,-167,645,-706,339,-435,-249,423,-296,-194,-514,740,87,-642,669,-874,-136,577,519,140,376,-751}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "asQuotedChars():char[]",
            new int[]{-735,-502,337,-338,46,683,-37,-207,-735,296,-254,-212,276,994,989,-294,868,-242,448,552,-991,18,-993,191,-538,997,402,-355,-985,740,468,-449,22,-474,-511,-835,640,-184,92,-806,-264,-283,840,962,737,-942,-828,-87,-90,597,675,-679,505,-357,807,218,1000,-603,117,2,-702,732,798,985}));
    }
}
