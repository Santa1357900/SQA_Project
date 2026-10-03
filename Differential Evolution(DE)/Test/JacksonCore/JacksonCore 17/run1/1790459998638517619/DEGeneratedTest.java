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
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "close():void",
            new int[]{-718,-113,272,386,-270,106,969,339,250,-589,691,629,933,577,-273,-705,667,-348,683,982,45,-486,255,759,-971,327,670,-86,491,-199,-5,-864,-480,-171,-245,787,555,-404,-183,-790,-222,582,335,-86,999,12,709,-657,-718,240,-45,486,-50,340,-314,28,-714,724,-40,-36,-769,13,-317,640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "close():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "flush():void",
            new int[]{-719,-795,-637,-149,188,823,235,524,-297,-183,-214,-325,550,991,-471,707,-477,-2,-489,-984,605,-797,565,5,663,669,-792,290,-523,-859,211,-719,582,-704,-861,-496,-373,-998,900,287,800,-184,704,686,-793,-451,749,-66,70,-625,-603,-911,89,-354,-329,65,376,359,465,-240,18,45,-957,-382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "flush():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "getOutputBuffered():int",
            new int[]{755,-714,-911,906,246,583,-983,898,-548,768,-678,409,-981,-31,-866,732,155,-845,259,748,-143,517,-92,-566,-384,-104,336,977,696,316,276,757,-109,415,-494,-862,205,678,-927,-4,-700,-227,-209,230,744,997,415,-882,-879,866,646,296,-150,231,502,412,244,938,977,-960,-264,22,938,307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "getOutputBuffered():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "getOutputTarget():java.lang.Object",
            new int[]{-256,-14,88,-387,-547,131,832,-309,-817,-513,-216,896,64,343,887,309,-901,272,-128,337,179,-915,352,-242,160,-199,633,-673,-103,-897,844,762,-795,297,-327,447,-383,-270,584,967,-534,-194,710,741,387,286,-204,427,598,386,-448,524,494,-670,-456,-339,16,496,383,-588,-975,55,365,-815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "getOutputTarget():java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeBinary(com.fasterxml.jackson.core.Base64Variant,byte[],int,int):void",
            new int[]{-723,496,-611,597,-102,846,-952,-758,218,-910,976,964,-599,-660,764,-17,-819,14,-846,-387,326,-286,741,-589,32,-877,55,-661,441,695,512,579,114,901,742,-645,-773,610,126,-461,-518,-151,-952,158,-694,-409,-685,986,-243,-490,464,-654,758,714,301,-41,758,329,863,-771,-264,531,260,-300}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeBinary(com.fasterxml.jackson.core.Base64Variant,byte[],int,int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeBinary(com.fasterxml.jackson.core.Base64Variant,java.io.InputStream,int):int",
            new int[]{-75,-532,-435,686,-453,-562,-87,730,469,-977,436,234,287,-448,301,-462,571,-456,127,385,-363,178,881,-76,-458,-242,871,-182,899,655,912,-676,244,353,512,-673,-344,701,-436,-378,-938,248,401,463,253,-393,344,-629,13,-773,879,-500,-43,632,470,490,699,230,-314,654,-230,-865,-221,600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeBinary(com.fasterxml.jackson.core.Base64Variant,java.io.InputStream,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeBoolean(boolean):void",
            new int[]{-526,316,-135,-58,-138,121,-600,-34,-798,-84,550,102,838,909,649,333,521,127,174,-605,968,717,856,197,-65,423,447,379,264,-487,-425,403,-299,-305,515,178,206,962,-391,-191,946,-309,618,839,172,843,-702,-955,814,-366,176,743,751,-333,644,-603,-542,-811,-739,333,-1,882,484,320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeBoolean(boolean):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeEndArray():void",
            new int[]{160,459,-848,518,282,331,-882,758,-709,-914,467,122,-535,-549,833,786,315,401,-735,-819,540,-703,74,986,-377,585,-563,269,-542,787,-710,778,-497,489,16,-580,194,-452,-609,-501,593,-137,306,436,-394,688,-105,-621,-535,700,-119,341,-355,807,174,352,898,45,-935,-542,670,-203,-289,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeEndArray():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeEndObject():void",
            new int[]{-277,-466,-882,964,253,-763,-44,-376,-801,579,45,405,-792,-148,996,-748,-693,-675,-199,-786,-560,-551,-782,513,-693,-67,987,996,925,-681,575,-284,-849,851,-246,594,-706,-881,-7,875,-93,-709,-641,377,-140,557,463,440,-117,-172,808,-103,-98,697,-161,-585,705,227,984,-909,-271,185,4,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeEndObject():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeFieldName(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-581,976,-488,-185,819,19,-725,-247,21,-20,265,-15,-144,402,480,-901,-773,-567,790,255,81,451,243,317,338,-861,-925,-7,-689,128,-512,-122,-794,-494,-441,-48,394,142,-931,-700,-684,-942,-534,611,128,-138,-282,350,495,674,-628,428,288,873,-205,624,-628,518,261,-906,-830,376,-664,858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeFieldName(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeFieldName(java.lang.String):void",
            new int[]{535,-893,-897,-6,274,-1000,1000,1000,-550,-181,243,186,1000,-186,1000,-937,61,-1000,1000,-455,501,253,-1000,-458,1000,661,-1000,1000,-881,1000,-1000,-994,-656,370,-1000,1000,-73,-199,570,-628,695,116,1000,-866,-313,361,752,281,-678,419,781,-359,376,728,-146,550,869,-480,-682,997,-911,192,77,-475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeFieldName(java.lang.String):void",
            new int[]{-661,-93,-1000,1000,-241,-220,-719,-423,63,-600,526,1000,128,-862,-631,503,653,-67,1000,475,-561,-500,1000,-1000,338,-748,568,1000,1000,-531,-671,1000,566,-1000,1000,-1000,385,-513,-317,254,-1000,-372,-1000,-954,610,1000,-529,335,475,-511,-1000,-130,-574,-1000,457,784,-192,-7,-565,440,-708,592,-708,-585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeFieldName(java.lang.String):void",
            new int[]{320,899,282,593,538,-91,40,-965,-308,915,690,881,-327,-626,-727,-877,594,972,446,288,357,-267,-423,381,-52,-479,880,-190,-724,-954,-658,156,469,-118,18,-920,-341,978,216,682,522,-322,-919,-829,-919,360,-889,-152,-377,-528,-713,-919,-21,-63,-363,206,850,715,320,-886,405,636,587,-360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNull():void",
            new int[]{-990,532,392,693,-105,-426,932,887,-132,242,858,-833,-520,-512,-96,477,-885,128,-700,18,471,-477,-682,423,412,-282,-196,-332,701,-371,-330,-618,531,-414,911,-721,970,-523,60,-124,-469,111,-768,331,885,976,-113,-704,415,544,-721,-3,807,-506,193,409,133,793,-997,-640,-996,-562,-621,-334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNull():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(double):void",
            new int[]{905,-1000,808,-467,-15,-1000,384,983,1000,790,-1000,621,238,-118,-760,-644,88,-783,1000,1000,-232,1000,-653,928,-347,164,-1000,701,-1000,531,-89,-695,1000,1000,-314,1000,1000,-554,-109,-521,405,477,122,-904,86,-1000,817,1000,-268,181,-387,-1000,444,1000,-670,1000,1000,1000,1000,1000,-567,1000,1000,436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(double):void",
            new int[]{294,-724,21,-542,-139,-898,507,213,406,-225,-197,621,-526,-889,70,-433,-421,-271,553,528,-710,834,-593,374,187,164,-23,96,-43,180,73,-344,817,138,-571,1000,394,-297,-774,-584,187,-257,-799,113,832,-370,321,761,-162,-520,-349,-169,799,459,-674,894,-128,-149,1000,882,-261,669,930,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(double):void",
            new int[]{387,-50,55,-738,-847,249,288,-714,-14,82,572,-175,-691,147,834,-102,-628,890,989,-529,477,-690,-468,201,828,460,-579,-936,-487,-154,-198,468,230,821,-567,-252,258,232,704,522,950,97,56,264,511,857,-720,714,-771,918,705,211,-544,-418,-408,-84,-373,243,-799,-969,-592,-246,-81,-433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(double):void",
            new int[]{-403,959,-441,799,95,859,-679,-432,-315,-52,396,-397,670,901,-645,502,847,71,-197,545,189,-738,13,-604,611,171,772,-473,-958,-824,241,927,-888,-970,872,-28,719,195,-470,-116,-578,40,-192,-860,-821,963,-734,-634,295,560,-196,-172,-393,458,708,-579,-87,-866,-951,-773,439,99,-931,-804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(double):void",
            new int[]{-506,657,-716,-31,-368,530,-574,-743,478,654,541,-182,-708,-926,-11,585,-8,712,354,-511,326,733,-655,-8,-274,335,78,-844,186,-232,647,943,-393,257,174,741,993,696,587,-330,-431,466,-735,150,-531,539,-621,-594,-17,-844,284,-874,512,-221,-384,-688,-643,-208,-88,520,286,447,-519,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(float):void",
            new int[]{933,-158,183,345,-815,76,-602,292,-556,521,-261,-422,-810,-955,-832,-817,-222,-170,-245,-158,354,127,-15,46,-575,794,376,-179,-591,-806,477,308,66,-926,-667,809,677,-140,-120,-565,65,-578,328,816,105,-158,602,397,265,943,-839,-208,-140,-568,-895,-623,-757,395,383,848,917,556,-859,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(float):void",
            new int[]{1000,-293,-832,620,-48,287,919,-98,-645,183,-305,-299,-73,-751,-63,-633,-1000,-188,-724,444,-1000,-462,560,651,837,-218,151,1000,-69,-478,-922,-1000,73,-758,677,-407,663,850,1000,345,557,736,56,-400,2,-1000,-72,469,-678,157,-867,-1000,92,942,-1000,-903,-1000,459,-177,787,305,503,-462,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(float):void",
            new int[]{753,-82,-713,-1000,345,893,-452,994,433,-99,-429,-117,657,-298,-1000,-489,-288,-244,4,-822,-733,-784,1000,1000,-108,-456,274,-46,379,1000,-1000,-885,-1000,53,-366,-663,1000,361,232,-651,848,-415,-496,405,598,492,-899,247,-629,851,301,401,97,-111,-266,549,1000,-413,-751,-517,1000,-1000,409,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(float):void",
            new int[]{707,-744,-252,807,-455,-599,-112,-487,171,779,280,-51,105,86,5,-499,610,-234,226,-715,-920,-660,-822,947,-783,282,-319,239,-255,692,-839,-473,-40,-951,-809,55,-608,-885,879,270,-916,725,113,809,-470,-372,611,-161,-768,-185,-990,367,-40,-176,811,-128,339,431,900,133,163,789,-412,-511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(int):void",
            new int[]{850,215,277,9,468,-918,-550,578,-356,317,909,231,999,737,299,235,691,-850,932,230,-838,756,-625,354,509,952,-759,543,-378,577,61,605,-636,-11,810,739,771,738,826,-614,-533,-929,714,-488,-658,-992,102,-290,-94,310,75,908,645,326,-435,854,255,361,1000,-947,653,-962,-873,391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.lang.String):void",
            new int[]{-852,361,74,-127,474,-998,-709,159,-14,-337,412,-774,935,-942,860,-650,560,-629,-332,525,216,992,934,-801,174,346,-654,-681,549,-837,527,117,-518,313,976,-684,456,-387,341,-689,397,914,884,-738,-884,-797,-894,23,518,-535,731,-640,-971,-392,503,7,738,-123,576,767,-165,892,-315,-921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.lang.String):void",
            new int[]{457,773,837,692,701,521,302,636,510,271,-748,236,-745,232,-60,567,640,-37,24,-700,-397,-632,771,836,58,56,-558,-506,-173,-696,-495,-1,765,-398,305,-116,815,-922,-181,-822,550,-20,221,247,-406,-172,102,541,258,844,-577,-393,702,-142,-473,775,-15,973,860,-396,482,-990,-522,-615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.math.BigDecimal):void",
            new int[]{328,501,1000,-597,1000,-369,-1000,80,-1000,431,1000,-139,307,-962,105,289,223,-48,561,-1000,-431,378,257,1000,977,1000,-509,118,-884,-603,29,-149,367,-540,1000,-501,-487,632,-433,530,951,141,-918,233,1000,898,883,106,-833,923,1000,1000,574,-575,833,1000,1000,-761,527,-719,1000,-832,-602,-520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.math.BigDecimal):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.math.BigDecimal):void",
            new int[]{541,765,-370,-572,468,-355,577,-1000,-92,175,1000,-837,-424,298,-1000,-70,149,336,-618,-1000,401,626,-245,-1000,-74,648,-161,143,481,-1000,710,1000,46,-1000,-281,-502,-401,-329,-1000,1000,1000,213,-743,909,748,1000,777,32,284,466,-121,-616,298,253,793,-108,149,-496,44,68,879,483,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.math.BigDecimal):void",
            new int[]{1000,-667,-789,-682,57,1000,577,550,-988,512,-1000,1000,1000,-373,-796,-1000,97,1000,-1000,1000,-746,-1000,-1000,-1000,-246,-377,271,1000,1000,-1000,-120,-442,-1000,1000,1000,731,1000,-329,-908,-704,-965,1000,-434,-1000,-1000,376,1000,988,-31,392,-802,859,-800,-1000,793,-222,-1000,-1000,-397,-1000,1000,551,-1000,923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.math.BigDecimal):void",
            new int[]{-273,749,225,423,-202,889,1000,-497,-1000,612,-245,1000,1000,-1000,-60,-1000,623,-837,115,1000,279,-60,323,703,247,1000,-572,-121,677,-1000,-964,241,-948,736,1000,36,239,-174,-488,0,-588,836,597,-476,-723,276,1000,1000,-488,-348,342,1000,-658,-855,-441,1000,825,-13,15,-654,-254,-232,-935,985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.math.BigInteger):void",
            new int[]{-995,136,-930,-786,697,778,341,-149,77,-828,-817,596,797,-538,-32,957,-46,459,-404,38,-753,-761,2,13,-939,-740,-965,474,154,100,-337,-447,737,-372,-102,-84,901,674,231,549,-406,-932,-56,564,-357,306,196,-822,-323,28,462,501,235,-992,-422,768,821,413,-565,-712,-768,-286,183,500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.math.BigInteger):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.math.BigInteger):void",
            new int[]{998,-901,-417,778,-1000,430,855,-636,422,788,-937,-437,-875,64,344,-1000,-464,-685,-974,-326,555,1000,-330,1000,-1000,-387,-201,-151,336,-1000,161,656,-717,495,34,638,413,957,-13,675,1000,-421,333,1000,-810,301,-834,-737,-1000,508,-22,-226,403,263,335,-1000,610,-335,364,-439,-25,-1000,969,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(long):void",
            new int[]{-106,893,443,523,745,517,-257,100,-537,-215,-359,-362,-926,-25,-995,288,-713,552,143,-743,960,-551,982,343,-110,219,-550,-538,641,817,329,966,697,968,-304,-758,-69,825,-958,-596,89,920,173,678,-190,318,-918,563,551,292,114,741,-278,863,958,-677,-705,-898,263,28,-758,-787,-981,-357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(long):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(short):void",
            new int[]{-838,260,406,366,-837,-867,389,159,481,993,250,-77,-186,-788,838,-497,442,-294,820,-127,-895,-317,-286,-598,-88,292,22,-160,-83,998,-614,-439,783,-737,-861,-712,526,-784,-198,512,700,-597,-830,-874,-395,384,438,417,-917,23,530,489,-679,-398,221,-567,673,-412,-18,-883,10,-203,547,-667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(short):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(char):void",
            new int[]{733,388,-867,367,-213,952,746,-391,-661,-128,-722,-57,843,-358,566,699,-202,635,-521,354,-397,-88,-275,93,756,762,-199,-623,-753,-481,380,-729,-86,-795,646,-209,-490,119,-656,619,284,-372,632,226,-13,-930,-757,623,385,982,-109,-216,-384,633,393,-918,-13,248,539,-958,220,244,-585,-855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(char):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(char[],int,int):void",
            new int[]{-295,513,-676,907,-271,-672,160,-365,611,14,-122,203,167,-352,1000,-718,-995,-702,680,1000,-1000,1000,-569,-9,224,197,277,420,-250,-183,398,1000,154,55,327,149,553,-941,85,-247,-406,-416,-477,-300,-204,-455,2,-63,-164,-932,-319,251,-1000,593,-128,350,-30,893,199,-340,717,659,-582,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(char[],int,int):void",
            new int[]{-295,-261,560,150,-271,37,262,-670,611,1000,-424,-4,561,-352,450,645,-995,132,1000,246,-1000,508,-809,-9,-302,-1000,318,-727,129,-716,-1000,794,868,-1000,544,1000,1000,-388,1000,-811,-96,-1000,-678,-183,588,-105,48,-975,-1000,-932,-565,1000,-204,162,-29,325,1000,968,199,-9,717,-1000,1000,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(char[],int,int):void",
            new int[]{-164,-139,638,-630,-980,27,504,-71,214,289,345,-808,219,-618,-423,541,-138,-896,699,288,488,930,-267,776,218,-642,-110,-154,960,-124,-396,-121,114,-112,-388,978,-345,-272,243,-452,-117,-34,-748,-487,-532,627,615,729,-342,-366,-478,-237,512,-168,512,145,819,281,121,429,-951,149,755,990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(char[],int,int):void",
            new int[]{217,669,667,-782,-903,389,732,51,968,970,20,-734,523,887,236,-42,-598,-550,815,-706,-845,602,-590,110,972,-322,-190,-200,560,-609,-211,70,-534,-249,962,495,933,-562,-832,-473,909,-810,-903,547,-740,-787,952,-87,400,293,-234,544,573,348,-329,509,853,476,-104,-645,546,-795,543,-541}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(char[],int,int):void",
            new int[]{-860,132,221,-466,680,339,-305,496,-691,-385,994,446,-741,-290,-621,890,-36,427,496,-205,-908,-958,276,635,324,46,-812,866,856,-219,-600,-745,-484,71,52,-153,160,663,-781,45,596,415,-583,-727,647,-430,498,984,179,136,-333,405,911,471,-984,-650,677,-557,425,-337,-588,-748,1,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{767,-631,-64,-789,921,-925,-906,998,-718,-581,397,13,916,622,-746,-343,29,-467,-400,-848,-90,544,717,724,19,-990,-337,-185,604,-352,-663,-786,120,-525,588,-140,-847,3,-230,-21,565,565,645,229,-870,-14,-334,-465,-943,-924,307,468,504,-150,-653,121,408,838,-230,740,804,-664,-677,-372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(java.lang.String):void",
            new int[]{-597,468,-110,-16,-642,44,420,347,-635,732,-645,217,670,161,211,101,542,828,907,387,860,926,115,962,-581,763,51,557,900,266,-80,865,-327,-484,-938,-212,-541,771,155,412,-803,-965,753,-294,463,839,-87,-661,-760,-346,-320,523,-73,-940,825,-697,106,-303,345,374,753,850,-991,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(java.lang.String):void",
            new int[]{310,-921,-443,-148,-40,723,-981,301,930,124,-279,34,861,-149,210,-80,946,-527,-223,-875,-584,150,-459,-138,873,-621,-193,-139,-627,-183,-106,-890,353,570,-617,613,-638,-144,-905,-784,-103,504,19,-54,608,433,860,-321,-383,929,-58,-513,790,326,768,-468,-612,-499,721,1,752,522,-918,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(java.lang.String,int,int):void",
            new int[]{610,-561,332,-325,1000,-337,-1000,987,-1000,174,-310,632,351,-254,-1000,460,-841,-491,-219,306,-243,-923,-1000,193,49,-110,-466,-420,-309,874,10,-917,593,-230,-1000,1000,54,-741,1000,252,-650,-1000,-14,1000,-844,-792,743,-856,778,361,487,-613,-909,1000,-118,551,887,948,-574,-759,-589,-495,145,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRaw(java.lang.String,int,int):void",
            new int[]{-349,-63,209,-288,294,793,-662,-180,-725,-277,-845,632,351,-119,-534,1000,-731,-1000,-1000,375,258,-190,-911,573,55,302,971,1000,-308,-155,174,208,-269,-266,235,245,45,-1000,1000,-937,130,-813,423,-144,-819,-946,630,-941,778,-147,341,-975,-1000,1000,-92,310,622,835,-413,47,-606,-739,-387,102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRawUTF8String(byte[],int,int):void",
            new int[]{1000,-1000,-451,-574,-710,-1000,991,297,-1000,876,-471,-48,1000,0,-406,648,-1000,373,-370,521,-608,-176,-242,-1000,-1000,325,-249,-156,196,1000,0,-99,314,-551,27,-278,397,0,202,-83,1000,-1000,1000,-925,132,275,-1000,-735,0,0,201,-1000,1000,-1000,-1000,-675,-816,-460,923,823,-12,-1000,-1000,-88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRawUTF8String(byte[],int,int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRawValue(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{119,541,650,-133,-965,-281,-55,53,644,-31,108,13,-64,-737,431,742,957,-623,193,-606,-995,-252,529,-262,435,-83,630,262,-478,277,-3,470,71,-92,461,55,-167,-28,833,-677,128,-671,-797,486,-415,-156,-847,275,-613,197,432,668,-2,888,21,507,14,-346,-65,243,441,-514,-421,-849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRawValue(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeStartArray():void",
            new int[]{-468,-89,-505,-757,481,-88,-460,595,65,-769,-888,410,-405,563,847,-610,985,877,20,-977,-179,-751,-731,394,-99,-543,-883,876,-358,-521,221,-561,-7,-74,-975,936,175,-582,746,492,-50,891,499,920,952,951,749,937,-925,488,-739,-114,906,-995,260,-350,-235,-649,581,503,-983,-91,290,738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeStartArray():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeStartObject():void",
            new int[]{616,634,-559,-651,354,-498,-531,-769,-980,-546,850,806,888,746,-904,10,318,980,323,692,606,-323,896,361,899,50,-191,-537,-679,-57,-119,985,-815,-870,635,-256,540,471,112,-738,-784,-214,811,812,-30,-284,460,-122,-591,357,-644,75,-75,-31,680,-603,66,693,349,539,-542,880,734,16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeStartObject():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(char[],int,int):void",
            new int[]{1000,-1000,1000,324,-973,556,498,1000,542,649,1000,1000,105,870,-991,-512,-345,-172,1000,-1000,-1000,571,-733,757,405,-108,-1000,-1000,83,396,-1000,-109,-36,-1000,-1000,-817,-797,474,1000,-80,867,378,1000,-824,-1000,-1000,-1000,-487,1000,-229,-973,-1000,204,559,-1000,801,1000,-1000,1000,445,-382,485,-290,-487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(char[],int,int):void",
            new int[]{976,-853,271,454,0,-77,708,859,907,587,-314,396,-457,41,-120,-800,-318,-121,-419,340,-899,-307,-220,762,2,-763,-351,-635,-419,-222,-981,-729,-937,-74,-954,-380,642,-657,366,557,-608,770,-264,-542,-544,-958,-584,903,609,969,344,-568,607,268,554,680,674,-910,214,-857,-57,110,158,-465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(char[],int,int):void",
            new int[]{1000,-1000,-697,355,-1000,363,556,1000,1000,1000,796,1000,-350,1000,-1000,-1000,-155,-265,661,-902,-1000,425,-653,835,828,-528,-1000,-1000,-491,208,-1000,-660,80,-950,-1000,-863,275,45,1000,655,179,444,823,-420,-1000,-1000,-897,281,1000,211,-601,-179,709,783,-550,1000,1000,-1000,-249,-838,26,-158,136,-581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(char[],int,int):void",
            new int[]{904,97,171,171,136,-853,-85,-857,-523,1000,1000,858,-83,1000,1000,120,-1000,900,-1000,-478,-346,-859,-1000,-1000,-913,-329,450,649,471,-721,-1000,-818,-721,-262,-837,-791,-365,429,977,1000,286,-203,-468,866,773,-567,-1000,458,1000,-1000,571,252,-1000,104,-1000,215,1000,1000,-121,410,-503,868,-1000,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(char[],int,int):void",
            new int[]{-191,792,-243,-270,906,-13,-318,-1000,641,-1000,1000,-795,-726,261,-452,860,871,549,759,-1000,1000,1000,295,-834,-814,1000,-678,-379,259,-68,675,1000,1000,-940,1000,189,931,760,-107,1000,700,-498,1000,1000,-184,1000,632,-454,-1000,-1000,-287,923,-660,-985,-1000,1000,1000,-258,-876,716,-1000,223,-604,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{329,386,-586,481,-803,-692,-716,-345,551,-959,545,-84,-731,647,750,813,148,-693,448,942,63,-303,590,-51,-431,716,-755,-960,-25,-433,68,518,-465,652,397,-593,128,790,-644,244,-459,844,-157,-215,325,-986,957,-471,62,775,-962,740,-133,706,263,954,-68,323,-482,821,-983,-574,-849,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(java.lang.String):void",
            new int[]{-715,1000,-495,-119,59,-400,-177,290,351,163,232,-757,-271,-920,1000,-897,393,-259,-386,-701,194,42,-300,873,889,-605,1000,-856,-500,554,-1000,-1000,-431,-471,-233,932,-481,5,-534,-211,504,167,400,4,1000,-18,570,624,-145,-1000,-201,-53,400,-949,-1000,-485,90,-613,-272,-1000,53,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(java.lang.String):void",
            new int[]{-393,-260,-836,454,74,1000,231,1000,-1000,-455,-387,385,-1000,-858,-764,-117,226,424,-1000,1000,1000,-360,-962,-257,267,412,-537,-505,1000,-344,168,-1000,348,1000,-879,-41,1000,-1000,1000,200,254,904,-1000,-705,-836,559,-1000,1000,948,142,-836,-949,-187,918,160,-571,-326,1000,-284,825,-587,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(java.lang.String):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(java.lang.String):void",
            new int[]{-109,-731,540,50,-225,770,-104,-328,-494,-316,-91,276,-197,65,-815,-917,464,-123,-237,-385,491,918,-377,548,-224,-485,55,-782,-358,418,952,-986,520,576,285,670,938,-858,-987,433,637,955,-464,-532,135,-977,401,491,-689,-968,439,-422,727,-147,-246,-611,-700,128,-920,629,656,552,883,-545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeUTF8String(byte[],int,int):void",
            new int[]{-1000,-598,1000,-846,-265,1000,-922,424,-1000,-627,371,1000,-1000,-1000,1000,-1000,224,448,-1000,-61,1000,996,274,-575,-875,-1000,1000,1000,-1000,358,21,-404,86,615,1000,-842,555,1000,-1000,1000,1000,-132,611,-663,918,-372,-1000,-1000,132,101,-245,1000,1000,-638,-1000,-1000,1000,625,962,-574,-1000,1000,839,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeUTF8String(byte[],int,int):void",
            new int[]{-411,105,718,-61,-771,720,-1000,180,-1000,-836,-241,1000,-1000,-892,506,-170,597,-137,-1000,-995,120,303,163,-159,-979,-643,236,1000,-511,340,-147,-1000,-733,275,846,-200,1000,1000,-1000,1000,725,122,1000,-1000,215,-617,-1000,-1000,-699,-400,-798,-92,1000,-768,-732,-337,182,1000,-18,-477,-1000,400,497,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeUTF8String(byte[],int,int):void",
            new int[]{1000,1000,-12,1000,1000,-602,1000,-1000,-152,-860,-1000,863,239,1000,-1000,522,546,-354,-299,-929,-1000,-1000,1000,672,-117,1000,-524,-1000,674,395,-673,151,-84,746,-1000,-547,-144,-1000,1000,-540,-146,1000,591,-275,-559,-100,-24,843,1000,295,743,-883,-1000,-487,1000,1000,-871,248,-935,-325,1000,-1000,-1000,966}));
    }
}
