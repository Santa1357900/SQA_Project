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
        org.junit.Assert.assertEquals("ARRAY:[B:2000:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "allocBase64Buffer():byte[]",
            new int[]{378,-433,500,483,369,-794,664,-34,907,-193,592,-942,467,-962,678,-420,539,-927,-929,-601,518,-936,-434,978,382,-239,708,770,-460,-147,-711,-636,382,309,835,-578,176,-735,-890,309,-738,-225,218,607,735,174,402,642,-797,-308,367,615,-383,493,571,-39,-338,-628,-236,153,742,-56,849,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("ARRAY:[C:4000:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "allocConcatBuffer():char[]",
            new int[]{-300,-191,92,-664,151,122,279,-268,133,694,-217,629,683,-748,942,-291,-958,239,816,143,73,-260,-997,755,495,435,-630,920,-247,714,-893,665,858,-781,-58,-232,349,79,-897,780,731,877,-346,-152,-860,-983,-768,-39,824,538,852,-156,-939,698,-508,70,586,-366,-588,194,-729,-681,-141,694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("ARRAY:[C:200:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "allocNameCopyBuffer(int):char[]",
            new int[]{-766,-865,324,429,662,416,-780,819,-863,-85,-251,425,-935,703,852,-4,-434,-348,967,908,-759,-160,64,-114,721,703,-120,-246,985,-441,763,-73,637,-677,-241,-196,-843,-562,437,-254,-546,373,281,-96,-47,379,441,803,-37,647,-873,-709,41,-548,3,-774,-270,-533,561,118,141,-991,633,822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("ARRAY:[B:8000:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "allocReadIOBuffer():byte[]",
            new int[]{-835,-321,-995,-637,300,-489,-762,-402,40,17,-811,784,869,-733,-678,-482,675,697,-435,879,230,308,426,682,-629,206,57,-427,-93,350,-817,-20,401,138,896,885,-538,85,640,-848,-42,-752,-551,-301,634,293,445,212,21,-862,938,-728,259,-49,82,-207,-558,951,970,475,475,-43,297,639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("ARRAY:[B:8000:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "allocReadIOBuffer(int):byte[]",
            new int[]{848,-144,210,514,-803,636,557,-343,-392,331,451,110,-299,542,-22,628,-515,-228,-74,-751,-491,-108,-190,634,508,783,-917,-553,14,857,366,-325,-898,-336,426,-283,-974,581,-668,-750,799,715,739,441,982,-423,540,889,-94,-177,-701,831,340,428,-201,627,-775,-989,806,292,521,-275,-267,948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("ARRAY:[C:4000:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "allocTokenBuffer():char[]",
            new int[]{721,-86,-448,48,-530,112,-132,435,968,816,496,97,-956,-207,-881,-555,334,646,-60,38,617,-808,772,392,-199,-668,86,-184,721,217,878,-22,-293,317,92,-13,320,864,-217,900,-388,-950,-815,-212,793,372,-568,-251,-702,-473,812,602,894,423,96,-638,698,-8,765,88,744,-233,-248,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("ARRAY:[C:4000:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "allocTokenBuffer(int):char[]",
            new int[]{-991,-183,291,94,-230,575,573,-669,288,-674,-665,-219,-321,-794,418,-620,-126,47,585,642,60,759,534,242,-400,388,-95,509,849,-599,-888,-225,931,539,-678,-548,-599,-925,-292,387,952,-312,238,-440,833,-668,669,504,-492,441,179,976,210,616,-876,-661,520,-454,757,889,879,-680,459,922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("ARRAY:[B:8000:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "allocWriteEncodingBuffer():byte[]",
            new int[]{-407,-913,24,-297,12,689,537,-273,318,-986,876,-371,430,-418,-727,-976,374,-102,707,527,-628,475,254,495,802,158,525,978,932,638,687,-356,441,-119,798,716,-342,420,-709,561,-823,-99,-719,459,-968,128,-886,302,-4,945,52,485,-278,730,-663,-891,405,15,-26,-254,-531,824,-426,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("ARRAY:[B:8000:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==:19:java.lang.Byte:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "allocWriteEncodingBuffer(int):byte[]",
            new int[]{825,315,370,845,-559,-255,746,829,439,-764,-459,-621,-521,369,-216,393,553,426,-68,-635,624,-766,250,650,-948,-285,677,-745,264,641,-694,23,-809,-300,366,-390,-92,884,-893,448,582,392,377,997,358,-190,-193,-950,125,132,-468,553,-742,-648,141,-487,350,-178,-821,885,-264,-666,-90,51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.TextBuffer", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "constructTextBuffer():com.fasterxml.jackson.core.util.TextBuffer",
            new int[]{451,-599,-731,183,-231,686,3,956,-498,-790,675,24,909,129,350,-341,-340,-765,968,314,-935,408,8,675,-506,-142,-296,-817,-999,505,665,-565,456,-724,-489,146,263,-357,480,-370,-872,-732,-115,-873,312,304,-235,723,-737,-419,-909,109,603,-329,-488,-45,90,-315,-237,297,330,505,579,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("ENUM:com.fasterxml.jackson.core.JsonEncoding:UTF16_BE", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "getEncoding():com.fasterxml.jackson.core.JsonEncoding",
            new int[]{-59,-399,-265,-871,332,856,-36,-547,-42,-216,-926,-425,880,-704,-449,-217,871,-526,517,-750,43,-373,-807,760,183,640,982,506,-812,-438,251,280,64,-33,-382,-100,862,-458,106,-513,587,540,-139,350,657,-724,-678,-339,-215,-309,-324,-157,319,-864,-745,659,-9,16,-888,346,819,689,444,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0NA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "getSourceReference():java.lang.Object",
            new int[]{143,212,67,80,-411,-180,-462,321,-84,-884,-407,-482,115,-166,563,996,297,-991,244,-758,-132,618,-550,843,-280,446,512,-308,-33,-461,-690,355,-625,780,-708,-807,310,-114,-67,-889,-244,438,75,683,755,429,668,-234,-502,-750,-404,-271,-805,-939,-546,931,861,-986,-571,-827,-816,971,748,536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "isResourceManaged():boolean",
            new int[]{-410,-567,-17,-97,297,124,587,-773,-301,311,-380,227,905,-472,-681,-833,-585,-971,656,-540,-107,-737,-603,-449,-79,-583,-869,-755,788,41,-275,348,-664,-213,-702,-593,759,-359,308,-408,-162,665,-382,-704,-417,252,544,932,-525,-867,-252,565,605,-988,-362,-495,-119,566,-598,823,151,-227,-764,833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "releaseBase64Buffer(byte[]):void",
            new int[]{-834,839,764,702,-586,-296,-306,-470,63,339,-789,462,361,-641,-473,737,548,-152,-726,528,-280,991,623,-59,-370,67,-773,914,-380,-397,510,-160,183,-679,773,449,812,-315,-633,-434,-279,-716,835,717,792,84,-720,250,680,681,-504,-392,368,44,400,-268,702,-996,194,-841,399,-275,-678,328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "releaseConcatBuffer(char[]):void",
            new int[]{-205,-295,193,66,-140,606,-74,333,505,0,-530,-448,-446,678,-141,-407,916,407,-430,-395,144,-80,802,853,-823,-240,657,242,-108,-132,995,153,-506,535,-468,686,-583,-74,-175,-403,-569,-224,-807,-744,-63,-970,-666,832,68,-755,426,-437,-872,-651,-162,-573,166,-961,-781,868,-984,-505,710,-714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "releaseNameCopyBuffer(char[]):void",
            new int[]{-908,-319,-553,-719,-383,39,-812,-946,-695,-518,-948,-344,-119,383,659,-502,-749,-953,-949,-352,862,-172,691,-726,-5,-979,-262,-719,196,-304,107,773,-189,-209,-956,-233,624,692,359,-817,5,320,244,-449,604,-689,-273,-881,-327,-765,-848,56,-627,44,-158,330,48,-102,103,489,386,375,-935,-941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "releaseReadIOBuffer(byte[]):void",
            new int[]{-86,-941,963,-978,783,-22,-685,-545,170,-571,419,610,-964,-812,803,668,504,991,-203,69,-199,882,-424,412,722,-2,511,536,-579,641,821,129,733,628,-911,783,-666,283,-537,216,91,-742,562,-458,693,347,5,-52,-720,885,-743,-103,214,987,79,-947,333,-149,215,560,102,706,176,987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "releaseTokenBuffer(char[]):void",
            new int[]{588,-658,-720,375,839,-884,-561,363,314,-839,-113,384,-642,-343,665,-975,740,-922,-170,670,-818,97,243,-218,502,972,-995,-718,233,-442,284,678,-916,-503,-451,967,188,999,-387,298,743,131,281,863,329,546,-78,-446,-19,184,787,563,-103,638,563,-738,-414,596,263,-502,190,-947,-645,692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "releaseWriteEncodingBuffer(byte[]):void",
            new int[]{262,-735,-872,-892,522,453,591,-176,-560,60,-759,-841,643,881,923,-59,-779,-124,61,-85,-676,-969,95,684,-924,297,324,-867,142,-248,-546,-392,633,410,558,-306,-70,481,61,-880,505,295,893,-44,804,596,-1000,-888,907,-536,-956,912,808,721,-713,459,-142,-625,-558,-583,-735,-640,564,907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "setEncoding(com.fasterxml.jackson.core.JsonEncoding):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.IOContext", DEReplay.run(
            "com.fasterxml.jackson.core.io.IOContext", "com.fasterxml.jackson.core.io.IOContext", "withEncoding(com.fasterxml.jackson.core.JsonEncoding):com.fasterxml.jackson.core.io.IOContext",
            new int[]{899,601,130,-607,-284,600,-857,-819,101,338,-639,-148,701,355,-125,-576,475,573,677,-279,-776,602,-276,-845,-970,552,-657,704,-79,667,986,81,700,723,663,-391,-681,-850,-541,-793,459,-959,484,-931,-969,-136,-699,-900,-796,-449,-555,3,-726,-124,-5,15,-837,533,304,-285,-283,202,11,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.ReaderBasedJsonParser", DEReplay.run(
            "com.fasterxml.jackson.core.JsonFactory", "com.fasterxml.jackson.core.JsonFactory", "createJsonParser(java.lang.String):com.fasterxml.jackson.core.JsonParser",
            new int[]{-237,-262,-504,-798,595,441,-497,-409,-808,111,-401,612,-481,-755,749,-382,729,-221,-991,37,353,-867,55,701,882,483,943,-814,546,-988,824,328,-589,878,-70,581,-628,128,-99,907,-932,838,252,-537,349,-600,-364,555,-529,-937,440,-14,-77,-419,-803,-724,503,-850,-657,19,48,-329,892,-910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.ReaderBasedJsonParser", DEReplay.run(
            "com.fasterxml.jackson.core.JsonFactory", "com.fasterxml.jackson.core.JsonFactory", "createParser(java.lang.String):com.fasterxml.jackson.core.JsonParser",
            new int[]{129,676,983,746,-308,514,105,-247,602,-266,587,-481,-516,341,77,208,-245,-88,-882,265,-909,855,676,-90,-107,275,-596,-848,490,-531,-942,-334,569,-255,-763,-134,573,-7,930,455,-319,-755,238,-99,381,307,-940,-653,-389,491,561,735,988,438,-220,138,-261,84,-129,669,-405,-59,177,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.io.MergedStream", "com.fasterxml.jackson.core.io.MergedStream", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.io.MergedStream", "com.fasterxml.jackson.core.io.MergedStream", "read():int",
            new int[]{-835,680,-847,-551,-594,351,65,-582,825,190,-709,-325,901,319,-98,-628,444,152,-216,972,-214,-541,175,-602,-441,-644,-830,-213,169,132,591,65,-556,375,848,-817,-421,90,-20,322,450,-339,-83,617,656,369,-772,-979,239,956,-953,-164,-78,237,-246,-897,923,112,38,589,931,536,-552,-601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.io.MergedStream", "com.fasterxml.jackson.core.io.MergedStream", "read(byte[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.io.MergedStream", "com.fasterxml.jackson.core.io.MergedStream", "read(byte[],int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.io.MergedStream", "com.fasterxml.jackson.core.io.MergedStream", "skip(long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.UTF32Reader", "com.fasterxml.jackson.core.io.UTF32Reader", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "com.fasterxml.jackson.core.io.UTF32Reader", "com.fasterxml.jackson.core.io.UTF32Reader", "read():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.UTF32Reader", "com.fasterxml.jackson.core.io.UTF32Reader", "read(char[],int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.UTF8Writer", "com.fasterxml.jackson.core.io.UTF8Writer", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeBinary(com.fasterxml.jackson.core.Base64Variant,java.io.InputStream,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "writeBinary(com.fasterxml.jackson.core.Base64Variant,java.io.InputStream,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
