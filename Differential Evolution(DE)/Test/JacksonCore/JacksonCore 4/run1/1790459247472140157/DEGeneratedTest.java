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
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "append(char):void",
            new int[]{-38,933,-954,526,527,-92,-17,-497,-141,-63,216,57,78,41,-873,666,-471,-247,523,656,421,298,116,-974,-105,-812,817,932,221,888,-966,990,-324,176,-46,-298,-204,-358,-196,254,-832,321,491,-302,691,936,672,-915,325,403,-682,-464,761,311,-853,-603,-854,956,-113,-342,16,149,-687,627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "append(char[],int,int):void",
            new int[]{654,756,-823,759,-170,-883,385,131,460,-805,-897,804,350,-765,875,-839,-733,408,45,278,-984,-139,-709,-12,-998,-36,993,156,-844,260,-680,517,467,250,-908,142,-279,-619,-365,856,-481,-612,-942,-292,-824,-520,708,25,657,766,918,466,362,818,-442,-961,292,238,434,74,854,768,-927,801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "append(java.lang.String,int,int):void",
            new int[]{-865,-156,649,-276,111,960,-216,586,17,-144,988,355,833,-62,738,-44,411,-773,-544,21,510,-131,268,768,-698,-857,977,-580,108,80,-168,359,409,-805,-535,-591,965,434,-304,986,269,-73,836,-827,500,-130,583,-769,-378,-508,-379,527,384,870,-342,-583,541,-820,774,-772,-130,855,674,162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsArray():char[]",
            new int[]{150,-161,-496,-461,-643,508,919,-267,361,804,538,876,291,304,111,-914,648,105,677,547,504,893,-732,-915,-906,-575,467,653,501,-403,940,426,793,-233,516,750,-368,-2,-142,224,198,-473,321,-163,-943,-115,217,-736,-482,839,-646,-825,-32,591,481,-262,700,537,51,-642,818,274,-415,-855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsDecimal():java.math.BigDecimal",
            new int[]{309,-709,955,-541,498,-152,801,-511,-640,-246,-455,-512,807,916,81,-271,-277,-484,643,-588,561,-228,695,-31,-271,-35,-402,-367,641,-388,212,-342,885,-701,411,727,561,862,401,789,-487,-609,688,879,917,-205,524,-700,-510,-402,-209,952,438,25,396,-416,199,-523,436,262,2,-954,-275,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsDouble():double",
            new int[]{513,-724,96,507,785,-523,262,24,195,542,-661,280,85,815,742,103,28,399,-76,785,-151,-547,421,851,634,254,531,-600,264,126,-817,-7,-692,10,670,797,-472,-337,63,-100,-319,-744,399,-437,-157,499,989,-482,45,308,-389,-593,-352,521,-293,-101,617,187,-739,-857,587,563,526,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsString():java.lang.String",
            new int[]{603,20,141,128,-119,730,790,966,618,894,-928,-742,994,728,815,480,-371,636,771,-758,-379,-938,-854,68,-524,-518,-778,-39,-636,-371,162,343,720,942,-557,902,840,-286,795,399,-165,-573,15,-479,-931,374,548,515,37,-174,-621,-473,542,-288,-591,304,972,-926,-730,261,-510,-606,-716,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("ARRAY:[C:200:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "emptyAndGetCurrentSegment():char[]",
            new int[]{397,64,-777,721,239,-478,283,920,851,158,228,-568,-214,-640,454,797,-682,499,-714,251,433,-949,860,529,339,645,696,-765,418,410,505,-973,832,320,370,-294,354,206,92,824,-571,599,-835,-582,954,-372,659,106,804,-168,74,-91,761,-82,151,-91,156,-726,564,118,-716,-656,-555,791}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "ensureNotShared():void",
            new int[]{889,638,195,832,-816,-441,-645,-766,899,105,118,-229,-544,5,-470,-442,-706,766,784,930,-914,-535,80,-369,828,857,-608,-930,-757,384,954,587,-456,395,-187,608,-504,435,551,228,-82,-195,-544,-700,314,766,-198,-601,-852,-253,-327,189,-588,-318,818,-334,-364,-757,-624,496,235,114,-982,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "expandCurrentSegment():char[]",
            new int[]{-767,-532,716,-266,868,815,884,712,917,-78,-845,558,503,786,-273,-753,845,778,-409,662,-378,282,-934,-881,-739,11,978,-761,851,891,581,867,63,-473,-991,106,630,-576,-738,-972,790,371,-406,100,370,-269,521,972,-845,-326,592,904,628,-112,611,-170,-806,-265,17,785,-372,-609,787,311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "expandCurrentSegment(int):char[]",
            new int[]{731,510,940,-971,812,-859,-254,-503,-165,257,-469,389,-52,743,-199,-497,410,-555,-454,-838,-466,612,164,167,555,503,932,-713,-155,-463,963,895,-624,-178,153,-976,-616,327,-352,-69,-254,-414,-7,-947,-224,351,322,142,-151,367,699,330,542,101,35,877,-164,123,-130,-553,333,-347,-232,-102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "finishCurrentSegment():char[]",
            new int[]{-673,-651,-252,-564,-178,287,-334,699,-605,50,-698,987,517,437,-128,881,646,-884,658,-239,124,-828,-634,728,-181,476,-615,742,-388,703,963,211,-98,896,20,-860,-931,-207,-233,-752,506,770,600,955,-734,-744,856,-926,615,704,897,-820,896,-171,-225,133,934,694,-804,-945,-33,467,-60,-839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("ARRAY:[C:200:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getCurrentSegment():char[]",
            new int[]{80,926,485,58,-632,-930,-665,643,-265,159,157,-381,954,200,-590,-462,544,-449,110,-570,231,332,-61,65,-861,-450,108,-392,306,349,-612,487,-223,-183,-308,-56,-562,-416,-17,-747,-9,-896,-110,-167,-952,928,-593,619,-822,578,295,-24,-82,667,444,-871,273,-197,280,679,7,-459,599,-924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getCurrentSegmentSize():int",
            new int[]{-825,-318,-17,96,99,533,-798,-196,-625,-264,-699,867,423,602,141,-889,-593,473,975,-97,629,143,884,-361,-909,-184,129,108,-436,-760,-538,172,-868,96,727,-556,-521,-872,826,-181,-26,-951,473,-932,992,836,-906,14,860,-343,-334,-715,-707,-626,345,-924,-711,-631,-669,-58,31,221,-455,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getTextBuffer():char[]",
            new int[]{-388,103,-368,989,187,-407,602,-361,215,-405,-648,-131,-140,-555,-439,597,-657,-291,-711,-572,840,68,-415,176,-429,-61,100,-189,-349,-465,986,325,444,-964,284,1,-640,73,993,-856,-702,821,54,-547,-721,-344,601,-89,-822,566,-993,-687,-32,256,-527,369,-647,269,-769,698,-722,-665,776,-31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getTextOffset():int",
            new int[]{572,-184,665,-879,-971,336,727,-882,702,-793,415,-28,-588,-147,356,-672,376,710,267,-1,-834,5,964,-118,-627,779,-489,168,992,109,446,649,185,-845,-514,837,-61,260,946,-716,370,-373,208,-994,947,-695,31,257,736,179,206,995,931,439,131,367,394,-368,-726,-718,-308,788,-874,706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "hasTextAsCharacters():boolean",
            new int[]{-212,460,-777,667,-662,-693,385,-512,69,75,-201,-521,-853,730,310,614,537,651,455,855,872,-2,809,224,420,-310,167,-55,-284,180,-70,-438,821,-327,282,633,784,-282,319,971,-143,226,145,755,288,867,153,817,-882,-464,-817,355,271,-886,-945,7,-511,-889,126,700,148,932,902,-935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "releaseBuffers():void",
            new int[]{655,899,946,422,-539,-75,-822,438,603,790,-204,-197,937,-197,-876,572,-430,-78,733,361,-156,304,591,-886,417,-905,149,-560,709,25,82,660,-998,725,-165,-251,-225,-90,840,209,474,-431,260,558,854,-885,-494,-355,278,-895,-633,229,-105,573,-386,-582,-482,275,-845,452,-819,233,-536,-961}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithCopy(char[],int,int):void",
            new int[]{471,76,-53,999,718,243,-514,-650,422,-921,782,893,67,2,-726,259,113,559,783,924,674,839,855,229,155,-108,711,428,-80,466,-528,990,487,-869,-479,247,-719,237,203,797,552,35,397,-932,706,-221,940,912,-993,-356,-658,166,363,909,-182,-221,-244,-12,-773,-763,759,645,227,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithEmpty():void",
            new int[]{-107,658,658,-956,-680,-339,-130,371,237,193,364,-599,317,-179,897,827,895,-144,748,-948,828,436,187,-110,435,-637,-267,717,873,-774,-594,125,-993,-955,98,-560,-473,46,257,302,903,-3,110,903,-396,-980,828,466,-50,-244,-774,-924,931,365,-153,57,397,-66,-355,773,-882,582,-168,918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithShared(char[],int,int):void",
            new int[]{16,-675,-391,303,-560,506,-879,22,-426,344,870,28,-3,-106,-981,713,956,50,-282,180,560,-422,878,-428,-427,281,605,-440,-672,-447,529,-438,181,722,348,-189,669,-879,-208,-698,-938,607,-111,-120,-633,-638,963,218,988,-736,64,-688,-743,-156,-271,-761,801,-101,846,-587,-652,-777,-765,-681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithString(java.lang.String):void",
            new int[]{-996,-968,72,-72,-614,-901,-667,42,-19,-389,76,-302,-919,-16,-863,-898,947,-247,-65,78,402,-493,388,803,-32,-159,-148,237,457,121,-318,-461,-971,196,759,-56,-557,-430,470,-292,-530,30,-539,967,234,-799,811,670,-338,129,-810,103,-878,-418,-989,936,859,-477,668,211,-108,-84,486,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "setCurrentLength(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "size():int",
            new int[]{-121,-33,118,-423,-793,604,742,341,-438,961,382,-332,-260,499,444,-159,-292,601,443,514,219,517,-386,887,275,-576,308,-835,572,-286,-757,397,375,-861,123,739,-642,-637,399,-529,879,-395,20,-320,859,588,903,417,-984,-210,555,-631,512,130,-227,-417,291,637,707,634,-706,-590,-351,-206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "toString():java.lang.String",
            new int[]{-302,-725,-358,-833,647,-497,395,653,343,-487,-713,-579,482,-697,-975,14,-300,400,348,511,677,611,-424,755,386,-585,880,-645,130,219,964,268,-77,808,712,1,101,-784,536,576,-425,998,-332,-747,-901,-586,405,-256,-902,371,499,-886,-864,-821,250,-75,-45,98,-345,296,-849,506,991,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("ARRAY:[C:1:24:java.lang.Character:IA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.JsonStringEncoder", "com.fasterxml.jackson.core.io.JsonStringEncoder", "quoteAsString(java.lang.String):char[]",
            new int[]{418,239,415,802,-232,892,-202,5,790,-573,68,-341,-141,148,-90,992,218,169,250,562,445,-941,804,165,880,-997,677,762,875,-895,659,-973,-726,-959,660,261,-858,502,-352,-855,-548,-430,-617,101,-857,-456,-717,-145,791,-453,-186,213,-17,-262,-41,-962,408,921,510,-69,-329,926,-791,252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "com.fasterxml.jackson.core.io.JsonStringEncoder", "com.fasterxml.jackson.core.io.JsonStringEncoder", "quoteAsString(java.lang.String):char[]",
            new int[]{942,-236,200,-129,-276,-436,0,815,28,-1000,0,255,452,-437,0,514,474,134,564,-1000,602,0,133,0,222,1000,0,768,-453,0,628,1000,-1000,333,307,-79,108,299,201,-1000,-377,845,433,1000,-1000,363,-756,-1000,-34,-978,-1000,-1000,628,0,-312,-1000,97,59,902,-660,667,-904,418,-898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SegmentedStringWriter", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "append(char):java.io.Writer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SegmentedStringWriter", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "append(java.lang.CharSequence):java.io.Writer",
            new int[]{678,354,-160,618,430,700,-156,-403,586,306,-13,401,372,-47,182,-130,997,-561,738,-360,179,711,241,113,-658,351,-303,-539,57,61,740,941,-680,-254,-854,359,-345,-460,691,-983,-315,697,465,-390,-156,674,-839,-332,172,888,564,933,777,685,-495,-255,482,-156,862,-989,-603,600,-143,577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SegmentedStringWriter", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "append(java.lang.CharSequence,int,int):java.io.Writer",
            new int[]{233,-624,-728,316,-959,1000,1000,-1000,18,260,972,392,822,642,-935,-1000,-4,-253,-437,-332,-706,-614,-142,797,58,-222,245,258,-25,-210,1000,-1000,231,-401,-1000,-876,-491,174,538,816,-942,-655,-1000,225,440,756,1000,-546,-523,-2,-471,29,-422,-855,340,967,-655,518,527,900,134,805,-523,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "getAndClear():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(char[]):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(char[],int,int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(java.lang.String):void",
            new int[]{-703,-144,-281,444,-589,-502,900,532,572,-969,-598,-226,-477,712,-526,251,920,-145,-179,-380,107,1,-553,-894,-643,-352,321,-681,-451,-815,137,902,590,-159,-425,-696,-601,274,757,761,-265,767,836,-27,-226,-111,-936,865,958,-27,-924,364,-643,-211,634,-836,539,-636,619,-927,785,-901,-349,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(java.lang.String,int,int):void",
            new int[]{-940,-147,876,482,969,933,-395,973,-927,-203,-708,-624,889,100,894,-650,969,561,272,918,-331,237,699,-320,-530,461,283,72,-680,50,951,-50,341,-166,-192,-288,173,-433,-148,-254,197,828,-673,-955,-55,-338,-690,-482,108,392,-484,735,-275,-704,783,630,376,-365,-823,-971,511,-70,-711,778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "appendQuoted(char[],int):int",
            new int[]{312,477,-433,-864,864,-559,-494,-908,-865,886,653,-639,-368,-224,690,-408,91,-837,762,-760,-622,951,-541,391,350,833,975,-403,549,738,841,-434,-525,107,-643,702,-683,116,954,-575,496,559,-965,976,130,-485,554,964,645,-835,585,319,-866,-845,421,496,-210,-232,-608,-207,-241,804,487,-545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "appendQuoted(char[],int):int",
            new int[]{-344,-123,-895,-304,-756,-46,-506,14,773,-201,806,591,-856,-630,375,-706,-644,761,835,-21,722,-858,-911,329,-827,-966,-934,722,-732,728,-104,-50,345,635,855,-262,445,-851,-491,-14,-960,212,-157,380,-565,161,844,-377,-240,-284,-994,897,-120,-55,534,-615,269,951,618,676,-78,664,131,-294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("ARRAY:[C:4:24:java.lang.Character:LQ==:24:java.lang.Character:Ng==:24:java.lang.Character:Nw==:24:java.lang.Character:OQ==", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "asQuotedChars():char[]",
            new int[]{394,469,227,-679,336,-514,-934,-901,199,544,-544,41,208,-536,978,577,-843,612,-861,552,724,304,678,-607,-127,103,-70,-52,-990,-249,-178,851,-611,-668,-838,-330,104,-265,4,-927,924,-539,-898,776,239,385,-288,-861,-596,-761,350,-82,-96,-776,-509,-73,-82,-539,-347,118,102,761,843,-37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "asQuotedChars():char[]",
            new int[]{-27,-287,353,194,-81,510,360,-197,-874,34,650,859,-480,931,-322,-179,-336,732,198,427,-940,-114,845,429,675,-370,-968,-930,-396,-320,988,403,-382,-388,844,968,170,-772,807,308,394,215,677,-668,-421,-45,-776,653,-308,499,275,646,590,-628,748,-762,769,146,338,192,-278,-451,-939,6}));
    }
}
