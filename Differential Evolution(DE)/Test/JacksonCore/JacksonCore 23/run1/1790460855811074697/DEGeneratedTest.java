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
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "beforeArrayValues(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{110,-128,-617,-1000,987,-1000,-1000,-883,1000,39,-641,-1000,200,-394,1000,-377,-1000,-455,629,-260,-219,1000,1000,-528,-1000,-649,1000,-88,267,-274,979,-403,-758,1000,-1000,148,-90,226,-360,813,1000,564,1000,382,-1000,-470,1000,979,1000,360,-698,-1000,1000,-1000,-1000,1000,-578,-76,-1000,-171,-842,1000,-136,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "beforeArrayValues(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{581,-33,913,96,991,-400,-191,-296,-220,-544,946,-1000,1000,620,1000,232,-400,-372,-129,-3,-168,708,368,358,-1000,238,146,706,369,-456,507,-168,-400,856,-772,89,14,-345,-128,585,452,297,1000,820,-349,76,400,-853,1000,-48,1000,-87,-146,-400,1000,977,-626,992,-877,180,-612,607,-1000,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "beforeArrayValues(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{493,647,-25,589,952,129,492,868,-637,-492,-437,501,439,410,-641,-204,922,-993,657,-712,-202,451,-244,-377,-629,-583,-998,790,260,-236,198,389,-407,-387,-746,-884,215,372,904,-21,-469,-846,36,-107,-216,-123,-775,236,-979,911,475,737,-225,555,804,594,578,-143,343,164,-177,-647,-115,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "beforeObjectEntries(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{375,-838,758,29,-341,18,385,618,539,-759,789,-16,117,129,-750,-731,-315,-235,812,774,-426,-856,120,904,518,-871,-882,-879,135,949,869,-584,-744,-105,-284,-224,-967,-578,962,-292,47,-44,-693,-6,935,365,404,565,479,261,42,-402,-466,108,730,-212,-307,-572,-276,101,183,-665,-61,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "beforeObjectEntries(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{69,-663,594,863,-912,-169,546,1000,-1000,671,-912,-47,-871,-214,-1000,416,-1000,-1000,-408,91,-821,-993,61,558,-1000,-400,31,-319,-135,1000,930,-765,-642,-489,-1000,-1000,-1000,-183,874,-12,107,245,-969,-1000,310,-987,256,1000,-6,46,709,1000,-419,-548,-356,-778,-799,-597,-120,1000,-898,141,511,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "beforeObjectEntries(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{511,-76,287,810,-573,-942,23,728,751,-759,68,-1000,117,-365,254,1000,1000,-770,61,657,523,78,362,1000,-907,-826,414,-821,135,-360,869,592,-858,-105,38,-224,-893,226,1000,-670,-37,83,-957,-723,-1000,568,404,245,-50,11,-670,-28,545,466,53,-212,1000,-572,547,508,167,-319,824,393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "createInstance():com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{212,-113,583,-409,-984,427,-956,924,-639,27,-78,245,-55,-715,983,928,430,-161,-925,574,-608,171,804,-590,-899,715,570,-380,498,-829,753,-761,-136,-201,859,-850,-840,1,-171,539,786,-24,390,655,596,400,313,-800,390,-808,762,-210,861,-788,527,988,-63,457,873,-782,-146,769,976,-507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "createInstance():com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-361,298,384,-1000,1000,-1000,-336,71,-1000,333,1000,-1000,-1000,792,-1000,-247,-422,-240,173,287,1000,429,-1000,-135,1000,-542,-1000,-248,79,1000,345,-450,683,-805,-1000,-253,1000,637,80,-534,-388,692,-13,-632,284,-1000,-809,1000,-851,200,172,-820,-1000,-694,380,-1000,667,-1000,-825,1000,1000,-35,476,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "createInstance():com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "indentArraysWith(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):void",
            new int[]{-348,-273,-117,437,50,-196,708,-369,825,172,560,902,-539,504,677,6,952,153,960,285,-768,-790,-880,447,-949,87,-970,-646,-918,327,990,506,315,-64,30,-82,-98,373,-340,322,-445,-456,-471,254,-712,-396,365,439,-130,216,230,149,690,-987,668,695,-90,-565,-378,79,752,-119,153,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "indentArraysWith(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):void",
            new int[]{-254,-543,-585,-279,-812,220,-83,-572,496,-336,720,-394,472,-444,-334,404,486,-996,-327,-548,-596,691,-948,485,-922,531,803,135,716,252,-400,888,716,-42,-360,158,-323,611,792,-285,188,544,544,279,-251,-405,-868,-991,698,-704,947,809,-601,-363,799,-74,74,367,970,114,680,-459,95,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "indentArraysWith(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):void",
            new int[]{793,193,912,484,858,-497,-1000,531,82,-1000,-1000,-65,-518,1000,-1000,-1000,-65,-902,-766,107,-209,-1000,47,1000,-676,-189,-709,395,-908,-525,1000,111,-694,1000,684,90,-1000,-293,1000,-1000,865,1000,1000,119,646,703,-827,-527,1000,-693,66,-458,-173,1000,-1000,1000,689,386,-832,-1000,-190,379,1000,641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "indentObjectsWith(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):void",
            new int[]{-35,-298,734,873,-1000,-239,-91,35,1000,-455,878,-1000,-900,207,-305,179,-159,-280,-1000,172,955,-550,-136,-510,-1000,-869,88,-184,521,1000,156,867,1000,77,339,-152,-707,-151,532,-1000,196,-568,1000,367,-877,-745,-1000,-84,178,-283,487,-564,312,-161,-1000,349,-117,-1000,-669,102,513,-279,1000,452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "indentObjectsWith(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):void",
            new int[]{-71,-973,-721,-925,-864,105,-334,-239,487,-245,469,-961,-467,784,-680,-944,-815,-988,-977,-378,908,-997,-839,9,-473,-811,-265,108,327,394,796,-753,489,-744,254,-442,537,331,-381,-729,-559,-239,284,-462,-703,-694,-841,484,83,281,-61,66,-82,461,-889,996,-583,-611,-79,524,309,659,946,-816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "indentObjectsWith(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):void",
            new int[]{235,-742,-32,753,1000,1000,1000,10,582,258,-726,-395,-1000,662,1000,513,814,-1000,-1000,-139,1000,-1000,-279,318,-1000,357,706,-476,-1000,-140,743,-793,-897,-1000,1000,911,352,-1000,588,1000,-1000,1000,702,701,-1000,200,174,-1000,744,1000,-1000,546,1000,-779,-1000,-965,902,-274,727,-202,-848,-1000,-1000,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withArrayIndenter(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{938,-621,708,-527,837,-79,795,304,-277,-640,608,630,-56,223,-174,-391,-838,-545,826,590,932,-76,-978,498,-556,-215,452,-797,978,-154,-681,-769,388,77,148,-365,-437,824,960,-388,202,-63,-531,-447,-515,30,-343,-411,263,393,-696,452,-764,-295,54,437,-745,610,410,-351,-628,-526,707,326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withArrayIndenter(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-1000,-632,944,-864,860,641,-780,969,-338,-610,1000,379,290,717,-340,236,-882,-791,116,623,1000,-1000,-960,1000,-432,1000,-649,-1000,479,-536,-1000,453,-1000,-460,546,-1000,-566,857,697,-1000,1000,747,-1000,461,-856,-1000,-1000,-295,1000,1000,622,-320,-412,1000,319,171,-1000,-1000,-130,509,-906,-893,742,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withArrayIndenter(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withObjectIndenter(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{868,-23,-896,-922,-728,940,928,-591,-647,-458,-701,268,-916,59,-51,998,736,957,393,-871,-908,-386,-41,-18,261,138,-499,-356,926,906,-207,-722,965,-495,509,715,-397,211,-281,853,535,-703,846,-469,746,85,149,196,-794,888,992,965,-975,776,-652,-502,188,436,351,448,-418,309,7,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withObjectIndenter(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-598,178,-352,1000,1000,-1000,-729,-90,-820,310,1000,-367,642,953,-881,868,640,192,955,-184,1000,-683,-834,-792,377,-1000,493,-18,-685,1000,-1000,273,-42,1000,-918,274,455,276,622,516,35,-660,314,397,-468,-593,-70,134,-885,-861,1000,521,337,843,255,1000,1000,-239,416,-418,-290,1000,822,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withObjectIndenter(com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withRootSeparator(com.fasterxml.jackson.core.SerializableString):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{921,772,-982,347,538,-231,-944,214,799,-31,-239,-782,-822,639,165,720,-989,637,-445,833,-988,-919,721,772,-413,-783,-33,660,793,57,-469,51,-44,-543,-374,166,-552,-823,-759,-603,959,-844,-966,959,-304,762,183,-242,368,-909,-104,514,590,512,-558,962,355,-377,3,573,634,-470,-550,643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withRootSeparator(com.fasterxml.jackson.core.SerializableString):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{609,-466,397,743,48,1000,-969,601,1000,1000,-100,570,748,1000,-908,402,733,-216,-929,311,279,724,-563,609,-1000,-785,515,85,-1000,201,-1000,102,-1000,-94,596,-1000,-380,-839,285,-1000,-651,-271,-1000,1000,610,-159,599,-285,416,1000,1000,-602,-587,862,-431,849,558,-117,-718,400,-1000,-1000,244,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withRootSeparator(com.fasterxml.jackson.core.SerializableString):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-945,187,1000,470,549,-682,437,-1000,-366,-1000,695,-1000,-535,1000,1000,287,-1000,548,917,857,-446,-387,-29,1000,443,213,-801,197,107,721,367,-139,-666,-288,-131,1000,-1000,120,-78,1000,-343,-1000,1000,-609,-921,-505,-1000,-1000,1000,997,-961,1000,754,1000,-396,-1000,-339,-204,384,-1000,516,-170,319,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withRootSeparator(java.lang.String):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{602,997,-92,777,586,-1000,613,-1000,-926,-1000,1000,-456,202,151,1000,-696,1000,353,909,935,23,172,-469,-1000,-722,-978,1000,646,465,-1000,-390,-197,1000,655,-1000,-791,150,-346,-501,-275,-116,-13,421,-822,777,1000,-1000,213,870,740,-1000,-1000,-631,132,-163,-662,795,-73,747,499,1000,664,130,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withRootSeparator(java.lang.String):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-1000,604,222,155,-1000,400,-1000,243,-219,385,-584,-76,-25,29,-611,263,353,-242,304,-36,1000,-792,777,-200,-353,176,-869,-738,-1000,1000,1000,1000,-869,986,-661,-365,1000,-160,547,815,-1000,639,623,-1000,139,274,153,746,-391,-1000,-420,144,1000,20,-984,951,131,-1000,-420,-1000,63,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withRootSeparator(java.lang.String):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-998,-36,477,-817,-112,-127,-1000,134,-990,324,-435,-111,434,422,-1000,-565,318,-681,-266,-1000,1000,803,559,1000,-589,-1000,-1000,1000,-904,1000,880,-629,-1000,-562,1000,-323,1000,-596,-290,475,-1000,-12,384,389,-761,649,-755,-213,346,-1000,-778,247,1000,1000,-1000,1000,-1000,-1000,-404,-1000,-950,-1000,-618,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withRootSeparator(java.lang.String):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-1000,52,452,918,24,6,-1000,-320,-1000,1000,-5,-1000,-1000,744,-99,-104,1000,170,243,-290,97,436,72,-733,-645,-730,982,749,-241,-1000,886,1000,-1000,-17,-934,1000,1000,861,-349,518,109,-257,551,369,-295,1000,585,715,-68,14,-889,781,185,-400,-564,-400,783,-1000,88,-889,-669,675,198,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withRootSeparator(java.lang.String):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{982,-423,-82,1000,233,822,-338,92,-432,1000,-1000,804,412,-943,-1000,-747,-1000,-1000,-886,-102,962,-546,-1000,1000,1000,207,344,308,-1000,-114,1000,1000,387,93,1000,-650,-1000,996,1000,-156,-990,-1000,-150,671,-710,-1000,289,1000,-337,738,756,-1000,-798,-1000,9,1000,-1000,1000,-312,701,346,-818,642,319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withSeparators(com.fasterxml.jackson.core.util.Separators):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{141,-863,-341,218,-262,-258,421,913,940,-468,73,316,754,639,968,-843,168,515,-811,-595,220,-611,-663,806,58,208,-863,-334,357,-842,506,-437,-660,-533,-491,344,857,475,779,279,531,181,394,589,777,423,945,-344,693,-363,-406,216,244,769,285,559,-33,-700,953,359,-497,602,-429,-870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withSeparators(com.fasterxml.jackson.core.util.Separators):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{561,-758,683,-304,-482,-909,-265,-952,813,-253,-351,-658,10,-358,716,-460,389,624,-525,918,-321,-749,-792,192,-894,-852,-816,766,-379,-651,-649,-963,-893,-957,-81,409,-458,-441,-545,-820,660,-475,502,-228,450,-832,485,-79,-991,476,-247,-609,724,-420,-69,532,533,86,468,-427,-919,-630,-746,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withSeparators(com.fasterxml.jackson.core.util.Separators):com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-665,422,437,-1000,90,-343,-573,-1000,1000,-700,1000,314,317,219,-227,885,1000,-917,883,-771,904,13,539,-1000,1000,-826,286,895,-1000,-355,1000,797,1000,1000,-160,-371,-633,1000,-1000,766,193,461,-1000,1000,-1000,836,-106,-1000,-858,-1000,-535,964,-1000,706,-488,1000,-1000,-1000,-497,249,-338,-206,172,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withSpacesInObjectEntries():com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-696,117,299,903,549,-663,-898,369,-168,695,515,-546,-822,185,-8,836,946,608,44,-757,-951,-628,-887,-154,429,-540,535,-76,607,689,-680,-355,-779,-240,-676,-728,-411,-418,-640,-895,551,885,-283,-991,-890,243,661,538,720,254,-430,331,462,-105,-458,723,800,-202,-456,310,143,587,484,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withSpacesInObjectEntries():com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-598,-38,-950,874,-49,-960,530,440,251,-280,-62,-896,-742,-961,-933,559,679,-801,-604,304,-943,-806,-778,882,298,917,652,-984,-837,-960,-108,-288,309,-631,-900,-201,-386,330,10,413,721,809,517,471,22,627,-543,-682,-408,-382,-59,421,28,-526,-241,-764,-440,-660,609,-824,346,-562,226,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withSpacesInObjectEntries():com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-911,-12,-272,-800,-507,-858,-920,621,-546,359,-280,-576,682,-137,527,162,-214,-124,434,-43,246,-723,-638,-237,957,274,227,642,992,520,540,220,291,-529,-978,-71,141,-193,560,669,-497,-776,-198,-946,-471,-968,-216,557,916,-368,-972,-452,-841,-814,797,-528,-785,550,-644,-761,-408,914,453,254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withoutSpacesInObjectEntries():com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{-856,53,-336,833,-932,633,-74,-32,90,-464,-29,-470,-7,491,-157,-783,-935,795,384,339,440,-119,961,-510,801,185,521,-152,616,-22,270,957,198,-895,297,176,-760,584,-251,-754,-770,-114,796,-640,151,-626,557,8,-791,-137,388,461,-3,555,507,235,-544,703,-213,102,-730,-726,-260,327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withoutSpacesInObjectEntries():com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{52,263,889,870,278,980,38,970,-616,895,-679,254,974,-856,443,-180,183,-293,724,-467,542,-149,-487,163,713,389,-922,586,82,-248,-895,209,-699,-895,-65,-873,-732,-203,953,753,-267,-736,-816,-355,767,-826,-921,-906,642,101,-300,666,350,285,102,-668,-80,142,-455,-912,993,-886,376,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.util.DefaultPrettyPrinter", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "withoutSpacesInObjectEntries():com.fasterxml.jackson.core.util.DefaultPrettyPrinter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeArrayValueSeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{-535,937,818,144,0,-144,-805,-365,-933,-642,-64,-630,-970,-299,0,-739,0,0,70,402,-145,0,-240,0,521,-159,687,-98,-1000,-535,1000,58,-1000,-362,270,0,-991,384,-995,585,103,545,-737,-101,-984,184,-392,-478,1000,-15,-1000,0,83,-997,-231,-1000,1000,-251,321,-169,-416,-880,-286,881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeArrayValueSeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{612,232,-117,883,568,860,353,299,648,-851,-1000,228,1000,580,796,598,-876,622,-388,1000,-852,-953,806,623,-214,1000,291,861,-445,724,-741,291,1000,-519,-604,-526,90,-259,159,206,-260,858,-475,883,973,759,-462,162,-1000,-1000,1000,-1000,278,509,-815,1000,199,594,-202,-240,485,307,47,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeArrayValueSeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{895,-18,-765,602,376,-218,811,-393,-162,-615,74,402,-596,-399,502,976,677,302,-821,837,97,-905,-214,-301,-983,-606,-169,746,-239,906,619,-325,-401,-765,751,256,974,-775,-972,34,616,981,563,-26,218,62,647,-668,-737,257,420,-137,270,568,789,-425,232,-457,172,-306,-636,-65,-348,-786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeEndArray(com.fasterxml.jackson.core.JsonGenerator,int):void",
            new int[]{505,89,-243,328,-265,-1000,1000,997,-448,380,772,620,869,699,763,196,977,1000,-1000,-948,-1000,-1000,-1000,-1000,425,490,397,-522,-587,-506,-133,1000,-548,-1000,537,-999,-790,-376,-483,571,718,748,600,504,1000,-411,-1000,-1000,201,395,1000,-223,539,880,358,-827,-322,1000,-338,388,55,523,225,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeEndArray(com.fasterxml.jackson.core.JsonGenerator,int):void",
            new int[]{-38,-513,295,-150,579,-1000,1000,1000,1000,-935,-179,-133,528,243,453,613,180,-1000,-483,-305,-744,1000,-1000,291,1000,318,-563,-380,177,-774,735,1000,145,330,395,-1000,-365,844,-1000,945,-1000,-111,1000,20,1000,-172,-1000,-905,508,213,-294,1000,198,-278,-24,-301,274,100,1000,-203,-1000,947,1000,-605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeEndArray(com.fasterxml.jackson.core.JsonGenerator,int):void",
            new int[]{-774,-151,398,-800,-182,-1000,-664,527,401,208,-1000,-688,378,1000,417,1000,1000,-485,54,-1000,476,445,575,446,610,925,-372,-904,-233,342,-1000,845,5,-1000,1000,-21,-290,-791,-793,269,610,1000,1000,-750,-477,1000,-271,-692,1000,1000,894,1000,-1000,1000,-332,-39,-322,1000,444,548,-681,-1000,-877,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeEndObject(com.fasterxml.jackson.core.JsonGenerator,int):void",
            new int[]{368,-348,-663,953,682,-41,-708,-1000,75,-1000,-711,-410,-185,364,125,-726,393,825,-895,840,995,372,936,-636,679,103,1000,217,-307,-793,-313,662,217,119,1000,420,-315,39,504,-173,-316,-1000,-104,31,933,-826,-663,-512,-524,-365,-246,-298,-969,213,-693,332,441,-689,322,306,-400,1000,-27,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeEndObject(com.fasterxml.jackson.core.JsonGenerator,int):void",
            new int[]{-459,-88,-648,-792,-912,320,850,1000,458,586,1000,-584,314,355,1000,1000,-1000,-1000,-91,-1000,-1000,-203,-1000,604,-1000,-1000,-1000,-483,136,1000,-232,-373,452,-332,-1000,-978,1000,386,1000,-70,-512,533,-622,1000,-1000,-1000,20,-331,929,1000,1000,1000,-1000,-398,1000,-337,207,-1000,-1000,806,1000,-1000,1000,648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeEndObject(com.fasterxml.jackson.core.JsonGenerator,int):void",
            new int[]{702,17,-1000,-1000,152,-278,-888,77,-1000,687,553,-692,425,-1000,-553,1000,-244,196,77,-186,-94,98,69,465,-603,-334,-190,445,1000,-19,222,210,-501,-187,244,456,1000,-285,705,-1000,351,542,1000,1000,-394,606,235,-484,-1000,269,271,145,-68,-981,652,-707,567,637,158,1000,1000,-338,1000,-693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeObjectEntrySeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{-827,-948,708,-646,825,992,92,-822,488,598,752,929,49,-293,399,-773,-559,218,48,947,936,822,11,549,163,-681,-526,477,287,-585,738,-517,-294,110,724,-280,-749,-983,462,495,-21,317,-92,698,-684,-559,690,-920,-398,-3,-865,-205,874,26,302,-76,-896,206,636,56,58,296,326,429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeObjectEntrySeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{1000,-373,533,-144,-845,-551,-541,222,-413,-1000,213,153,-34,1000,1000,-1000,1000,248,826,735,1,1000,984,1000,35,153,311,765,-255,-1000,1000,-148,1000,-986,-195,-502,1000,-430,-614,1000,-143,1000,-5,1000,-1000,-734,1000,-135,-1000,-1000,-680,42,1000,-1000,-946,144,1000,1000,1000,-1000,-3,-400,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeObjectEntrySeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{1,974,622,290,658,-967,-1,-498,-223,965,250,126,-364,97,181,581,613,426,949,-255,333,-32,-579,-276,-722,-32,-373,-361,-407,977,-752,871,848,966,570,-779,-955,934,471,-546,-694,-184,-367,843,367,835,-585,478,248,-30,-403,354,-709,-336,-457,540,-873,-486,-738,108,78,316,-717,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeObjectFieldValueSeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{-824,727,398,588,-203,-824,293,431,-918,-782,-1000,-931,-1000,-798,-135,262,-120,-889,128,-205,-1000,-1000,129,-106,-572,441,-760,360,-973,909,-150,-720,-1000,322,-117,842,-112,-1000,-503,76,-922,-682,1000,-384,-721,-797,-216,430,-113,-373,919,181,211,-613,-1000,-954,-1000,544,380,1000,259,700,904,297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeObjectFieldValueSeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{570,42,398,-400,-393,314,-306,-1000,-1000,378,-389,1000,-1000,1000,-1000,53,131,-798,-1000,-1000,544,391,1000,-1000,478,125,254,-401,-1000,68,260,-1000,-242,322,1000,-400,-607,-395,-400,177,-336,267,-138,-1000,-400,-797,-216,-238,308,-400,400,446,-109,-313,-1000,-1000,182,-229,-1000,-1000,259,-160,904,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeObjectFieldValueSeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{377,-778,100,207,-869,-237,-940,-150,-61,-524,917,-520,987,-643,-517,343,31,-51,441,126,-578,6,-881,-383,721,-981,-332,-203,980,819,-387,564,699,165,215,656,949,852,108,-140,251,731,-719,-851,396,-220,619,-34,591,318,958,588,-913,-617,863,-789,720,304,217,205,-280,-394,-905,-148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeRootValueSeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{-19,637,539,-172,-748,-720,-539,-237,-1000,-574,60,-252,736,334,-189,890,-376,787,33,101,547,525,437,144,-1000,-372,-931,-82,998,-475,-155,-48,513,-143,411,-205,-263,163,452,41,943,1000,-1000,385,-639,-491,306,173,-829,-220,251,651,226,-292,-804,-803,177,389,-240,397,-378,134,-249,208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeRootValueSeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{672,82,802,1000,-1000,-21,-658,222,-82,941,-277,-229,-291,744,-1000,949,-770,540,-585,-723,1000,221,54,-372,-493,1000,-1000,-562,319,1000,1000,193,823,168,708,-448,315,-737,210,-1000,-726,-843,-876,371,773,185,1000,-491,-45,-49,-837,426,-270,457,400,-1000,-69,-292,180,1000,1000,681,395,-691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeRootValueSeparator(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{-18,337,-742,640,-571,205,-540,374,79,228,-327,-72,251,-67,33,501,-517,1000,761,119,78,-376,-1000,61,251,1000,-757,-81,-1000,-407,-548,834,-1000,889,-608,-388,314,203,-800,191,-858,-192,537,-147,558,-658,-26,230,556,-1000,812,-633,-260,-149,-500,730,-14,677,-642,91,-99,-591,-186,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeStartArray(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{-479,-713,-631,313,782,288,-317,-250,-218,511,-388,607,-823,610,-73,-913,645,-30,-180,-548,387,-229,420,-463,-433,261,-505,-493,-696,140,487,731,82,199,914,-174,-482,2,-69,407,318,-756,374,-107,-63,665,952,847,976,924,-557,-261,673,-354,-297,788,979,-951,101,571,-355,982,597,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeStartArray(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{-773,-903,730,173,-339,486,1000,469,-340,790,5,364,1000,-459,-427,961,-928,-648,-1000,-890,-910,-231,-1000,-100,126,-1000,1000,1000,1000,-1000,-22,-152,458,-313,-796,102,5,-739,-1000,-893,-239,1000,824,-356,1000,93,-47,-1000,-1000,566,921,658,-517,-697,-541,-1000,-1000,52,590,-1000,378,-1000,-233,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeStartArray(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{-450,-347,-320,-949,-857,-298,149,1000,1000,108,334,-569,-190,655,408,223,261,-241,327,724,-955,-575,-114,-992,-630,-1000,-649,35,865,813,-580,734,-1000,972,-4,-976,625,-1000,544,-899,-60,-808,-708,1000,-1000,10,-862,449,703,-874,878,82,-471,677,-884,1000,872,150,-635,277,-983,-782,-1000,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeStartObject(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{728,-588,688,-170,89,761,483,-203,-839,277,680,-971,-921,-252,-153,-583,604,-550,597,-314,-734,-608,942,410,983,595,87,394,-100,762,286,651,-510,-474,920,-477,195,863,-477,406,303,140,-872,22,-868,840,21,203,-876,-994,746,-255,-48,123,26,-930,-588,-696,195,-882,479,-552,-367,-783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeStartObject(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{1000,-173,633,-320,932,1000,-899,121,-583,-962,144,-1000,-1000,-290,490,938,946,-1000,599,65,-1000,-1000,1000,-1000,1000,1000,-1000,15,1000,1000,-1000,-266,-491,-857,-180,-1000,1000,1000,-1000,1000,-687,1000,-1000,-59,-1000,1000,-1000,545,-957,-1000,1000,-1000,98,-1000,-551,-1000,-1000,320,-8,-1000,-165,448,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "com.fasterxml.jackson.core.util.DefaultPrettyPrinter", "writeStartObject(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{-122,962,-1000,-724,-746,14,812,-94,110,-863,555,-33,433,948,506,307,-1000,278,1000,-1000,-1000,-322,799,234,-210,-796,-199,1000,-204,-1000,371,427,-731,600,-1000,-61,-111,337,16,286,1000,-1000,107,-639,-523,1000,972,-156,-882,207,-279,-410,485,70,-1000,232,-522,-294,-396,-422,-815,903,957,-716}));
    }
}
