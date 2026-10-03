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
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.JsonWriteContext", DEReplay.run(
            "com.fasterxml.jackson.core.json.JsonWriteContext", "", "createRootContext():com.fasterxml.jackson.core.json.JsonWriteContext",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.JsonWriteContext", DEReplay.run(
            "com.fasterxml.jackson.core.json.JsonWriteContext", "", "createRootContext(com.fasterxml.jackson.core.json.DupDetector):com.fasterxml.jackson.core.json.JsonWriteContext",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "close():void",
            new int[]{206,-885,436,-213,-949,-431,954,859,-939,344,225,-422,994,906,315,-170,478,665,366,-523,-233,627,-717,-210,-734,-178,-41,-557,68,226,836,-9,228,-718,-935,-576,-483,-356,-353,184,3,-698,512,-150,959,-147,710,600,331,374,-353,-513,-736,-924,-425,-370,-960,705,-522,440,-796,554,-119,-791}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeBinary(com.fasterxml.jackson.core.Base64Variant,byte[],int,int):void",
            new int[]{250,-1000,16,-854,641,-674,1000,691,83,843,-837,782,-688,-94,1000,-610,967,-825,-1000,-842,-880,-558,936,-339,1000,-1000,563,-582,-817,-54,578,-1000,-1000,1000,1000,329,-1000,-932,-248,1000,509,589,-1000,1000,1000,-1000,-81,440,-1000,-1000,-1000,767,850,-1000,-1000,-1000,-1000,754,-638,1000,166,100,-442,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeBinary(com.fasterxml.jackson.core.Base64Variant,java.io.InputStream,int):int",
            new int[]{231,-102,484,79,620,716,163,478,834,742,922,426,411,-507,-994,148,-263,815,823,-23,977,-333,264,-602,988,-319,-443,869,-386,-720,503,625,-68,302,-214,729,-64,-452,-369,-7,-78,433,-493,107,408,-993,-216,-744,-206,27,318,864,-810,474,-215,-314,-180,-100,-778,-578,259,-616,-229,-741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeBoolean(boolean):void",
            new int[]{-1000,-234,-82,1000,-1000,-294,712,-94,-797,577,843,-162,963,818,-429,-131,389,-884,-565,-441,404,124,860,-572,896,-379,1000,-13,-245,1000,1000,820,1000,793,197,-743,399,-560,268,-111,919,-583,-1000,530,-1000,-260,-664,-1000,425,-705,-795,7,-666,1000,145,142,-339,-1000,-1000,-531,-418,-380,-1000,-996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeEndArray():void",
            new int[]{-669,-924,544,593,1000,86,-503,-113,841,509,179,-109,-423,939,693,-526,-510,626,-330,-578,123,-905,-36,-823,-781,934,13,-348,70,-362,-113,954,205,526,189,-323,-593,700,-564,997,-606,-223,-679,239,-776,689,-646,805,-585,-762,765,389,156,280,282,-730,526,-550,-589,-864,628,111,186,359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeEndObject():void",
            new int[]{513,1000,246,882,-22,-332,-386,-1000,-500,-323,987,835,581,-558,1000,1000,246,-467,-441,-603,1000,145,-994,563,-67,-133,-454,-314,-256,545,919,-239,-403,923,-321,-224,858,-703,1000,1000,558,-222,713,821,-791,207,1000,800,-533,-497,-1000,1000,47,187,-379,387,-383,-527,-731,619,454,-625,-258,-59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeFieldName(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{669,841,-179,903,600,-1000,702,190,-631,420,-1000,1000,-1000,-118,569,859,819,-241,-1000,-98,-1000,-19,434,1000,1000,-1000,1000,-1000,1000,27,1000,428,1000,997,-779,488,1000,360,-961,842,341,-1000,-873,-515,-1000,-421,-81,1000,-1000,1000,-509,403,1000,-373,1000,987,-291,1000,879,-1000,-54,-79,1000,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeFieldName(java.lang.String):void",
            new int[]{55,-61,847,-679,37,-1000,-1000,-235,1000,494,1000,1000,-677,1000,-298,-286,135,880,665,332,-501,-281,853,-1000,-141,501,-200,341,1000,-510,-887,273,99,-1000,-313,-514,1000,1000,1000,550,-75,1000,387,-251,723,-945,256,238,972,-580,1000,-1000,-302,1000,412,1000,-179,-1000,-410,-307,-175,-1000,983,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeFieldName(java.lang.String):void",
            new int[]{-934,336,156,539,-1000,-1000,-1000,522,-429,-246,-1000,563,-1000,1000,381,-670,-440,833,-961,848,-715,295,950,-1000,1000,-1000,1000,-852,-774,-580,-1000,1000,1000,1000,-270,499,788,1000,783,-806,249,695,376,-920,-783,-152,-729,1000,1000,1000,1000,-1000,-987,1000,851,-1000,-1000,-1000,-1000,-214,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNull():void",
            new int[]{426,-986,-245,313,1000,-498,-1000,-654,455,699,-253,544,1000,267,34,-1000,-431,-654,-350,1000,354,1000,723,853,-836,-1000,-90,592,132,-607,267,-237,1000,603,344,1000,-1000,-302,-114,-546,370,803,-1000,-792,-1000,240,-644,58,-83,226,-217,55,912,1000,-52,1000,-303,607,385,30,1000,-378,-84,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(double):void",
            new int[]{475,929,187,-453,650,1000,352,-17,114,-606,584,-454,-291,851,592,519,-1000,1000,-519,383,-603,-931,-1000,-197,-1000,-142,312,305,854,-1000,-126,-1000,396,-11,-571,1000,-400,-1000,231,252,-406,-245,552,-653,-1000,1000,1000,1000,-465,-239,824,537,668,604,102,155,-936,854,-242,520,616,931,-545,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(float):void",
            new int[]{-1000,1000,-216,973,1000,-525,-1000,-1000,-169,-622,-846,1000,872,-421,798,-1000,1000,1000,-940,1000,558,-955,1000,-1000,452,-1000,973,-1000,-746,-1000,342,-1000,-1000,-1000,1000,1000,-1000,826,1000,873,-1000,167,1000,20,-409,1000,-681,-1000,1000,853,1000,1000,-853,1000,1000,211,1000,1000,6,-1000,1000,1000,-912,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(int):void",
            new int[]{-827,-465,992,-896,603,405,55,707,583,-411,-213,-176,278,674,624,-378,353,136,-250,245,4,982,231,402,477,-449,778,-259,830,-562,68,-524,668,756,68,-297,-845,-829,-78,-122,-254,-498,-408,89,-247,-928,693,859,-883,433,730,519,-422,-373,290,-560,-193,702,649,-193,6,-131,-254,20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.lang.String):void",
            new int[]{372,17,-569,-635,760,1000,1000,-1000,304,117,666,-432,-381,7,113,785,-82,642,-1000,1000,242,1000,194,-35,1000,1000,-638,238,1000,-1000,-1000,319,815,767,-1000,-597,351,881,-984,1000,-127,-1000,-843,-820,511,-803,172,1000,1000,-628,-855,630,144,-814,-35,79,-225,-959,272,1000,-113,120,769,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.math.BigDecimal):void",
            new int[]{406,912,1000,-1000,-579,1000,-1000,-77,924,427,-294,53,530,1000,373,-579,1000,161,1000,-1000,-387,1000,-14,755,1000,-182,946,856,-1000,-19,-544,-11,1000,-1000,-317,-228,934,1000,1000,-334,-89,-1000,-494,1000,-959,926,1000,-256,794,-20,355,1000,416,-73,1000,1000,570,-208,1000,1000,255,881,13,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(java.math.BigInteger):void",
            new int[]{-581,143,161,279,1000,-116,-707,-56,1000,-826,-752,-509,382,-955,-870,459,-323,479,266,219,-428,757,318,-701,533,885,205,-863,610,404,-672,-155,-666,594,794,186,-119,-621,423,752,-1000,683,700,454,1000,818,-676,541,-560,-743,-56,721,-597,-578,883,451,-994,256,-217,-1000,-291,270,773,149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(long):void",
            new int[]{-566,443,-1000,471,409,-1000,1000,-595,582,-172,394,856,772,-301,-1000,-1000,-765,-1000,236,433,-28,429,340,538,-151,663,-593,171,973,-1000,-960,-1000,-491,51,119,-146,-208,-795,404,712,1000,-1000,-18,-55,-713,-1000,1000,614,1000,697,464,-500,-1000,339,-466,538,1000,375,-488,-199,644,-1000,-647,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeNumber(short):void",
            new int[]{-202,-158,199,527,-625,58,-396,359,-414,519,482,359,-229,-53,-745,35,549,-847,-1000,1000,246,854,-393,20,310,394,-109,1000,-89,-20,697,212,498,-938,-118,1000,-1000,-719,-1000,365,-479,240,-1000,1000,-90,811,-58,499,-854,-71,-20,-364,-1000,-397,-804,-421,30,-867,29,186,72,-28,-46,-511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRawUTF8String(byte[],int,int):void",
            new int[]{215,1000,-519,-529,220,-228,-808,-467,971,-337,-589,-1000,-700,-430,-264,-1000,-76,1000,-677,-24,-195,-58,752,1000,-495,941,1000,187,-64,-307,-55,676,128,863,-508,1000,89,-319,-1000,-431,-868,-229,700,37,918,218,-1000,-1000,905,89,736,654,1000,504,484,-1000,1000,-666,-253,533,1000,-1000,1000,653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeRawValue(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{1000,1000,1000,1000,-9,739,-1000,1000,-402,-814,-51,-626,-677,1000,-1000,-1000,-1000,-917,-811,453,-480,-647,-481,-253,204,-400,29,903,80,-1000,-36,898,-836,-1000,91,-693,1000,-1000,-808,-220,1000,625,448,-126,-590,-933,520,383,-1000,28,-817,783,1000,-477,1000,-1000,-23,410,373,1000,750,-1000,87,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeStartArray():void",
            new int[]{278,937,-262,925,-1000,-1000,57,-1000,-630,-41,-390,1000,700,888,-381,150,-634,-652,98,505,407,699,-963,580,232,-273,-260,-364,676,-519,716,-78,1,-577,-268,172,6,742,332,293,81,-468,-1000,143,223,-1000,343,-792,-295,-664,-947,416,401,-361,297,-333,-495,-332,1000,933,692,-1000,-718,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeStartArray():void",
            new int[]{-107,973,-692,784,1000,839,1000,1000,-337,-846,-571,-373,767,372,114,-256,1000,788,1000,-454,1000,-262,1000,-864,256,412,542,-304,515,-1000,-844,88,689,1000,-628,-961,20,464,1000,103,-1000,-1000,886,1000,-1000,1000,940,-1000,-1000,-878,-329,1000,1000,196,-15,300,-236,-665,372,703,1000,-1000,481,-495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeStartObject():void",
            new int[]{-609,1000,-1000,-215,1000,-418,-1000,914,-116,343,-662,-118,-383,321,-565,678,-465,-924,1000,94,363,-1000,1000,494,740,-522,925,-1000,949,-832,1000,1000,327,-1000,-545,-958,266,-527,-1000,-740,-837,-720,-379,-1000,-1000,-258,338,104,-1000,-112,320,-1000,-268,554,-510,-788,-721,-857,555,482,-73,428,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeStartObject():void",
            new int[]{11,-662,143,-31,-541,-776,778,-794,543,244,-27,667,-177,762,143,-390,-122,143,-375,-400,232,-192,-520,851,1000,417,74,868,-119,-764,-96,-1000,137,-64,135,481,-1000,-421,-887,696,418,1000,519,-163,345,508,-457,-966,619,78,-389,142,-97,-121,-8,1000,112,-1000,218,1000,82,-260,-123,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(char[],int,int):void",
            new int[]{278,1000,987,-2,157,864,-102,1000,271,-741,-134,481,874,-373,1000,218,963,-1000,354,-569,-322,-333,58,148,1000,480,352,600,-654,1000,-1000,471,-127,306,1000,406,209,-13,-1000,-305,-274,-263,-525,10,-1000,-1000,254,701,-745,-1000,331,-654,304,-615,-130,-95,-182,1000,-762,819,292,1000,-322,-291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-1000,-741,1000,19,-409,601,-482,881,1000,23,859,-618,-319,-1000,-966,844,1000,710,565,-1000,959,-1000,-1000,-333,-1000,-38,-765,1000,127,-68,1000,986,1000,175,-197,-1000,-914,1000,-247,-93,-1000,-800,576,1000,-438,-372,-1000,-234,-148,1000,-1000,361,1000,-167,272,-1000,1000,-27,-156,71,-784,201,-566,-620}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeString(java.lang.String):void",
            new int[]{-1000,637,23,838,400,573,1000,210,1000,867,-310,866,-714,-917,-414,-151,995,365,-765,-1000,544,-147,1000,-434,-792,523,-235,1000,-1000,1000,-176,-1000,-754,1000,-874,1000,-668,-831,-672,-867,-978,1000,-849,-1000,892,520,1000,-148,-400,-1000,464,185,1000,11,327,19,-985,728,-1000,-25,-1000,-15,1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "com.fasterxml.jackson.core.json.UTF8JsonGenerator", "writeUTF8String(byte[],int,int):void",
            new int[]{-156,-984,41,-710,-679,-253,-634,-636,-983,409,-40,552,507,-409,-105,650,301,-703,-360,690,-694,-277,-564,99,970,-269,683,98,179,-98,400,-956,-72,785,-31,565,619,-8,-681,808,-608,210,-538,889,367,-185,-129,719,851,836,572,800,-359,-732,19,-737,-599,383,823,480,-270,619,255,907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "close():void",
            new int[]{142,247,949,727,-623,-909,964,-310,708,-56,507,742,122,34,654,-486,354,-515,442,775,-657,-540,-223,922,797,138,176,-147,377,187,-664,-813,806,-454,-716,-670,-446,693,-708,-538,-333,-91,-365,163,-256,-533,813,102,-847,176,874,-19,-878,-745,-839,742,-463,-884,-62,973,969,315,141,363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "writeBinary(com.fasterxml.jackson.core.Base64Variant,byte[],int,int):void",
            new int[]{-1000,720,870,-744,-3,1000,-194,66,23,-485,1000,-261,-70,-1000,-401,323,45,1000,69,1000,-156,-655,1000,886,-684,643,455,-157,851,582,-1000,-1000,-1000,-1000,1000,-717,126,930,756,1000,-327,-151,323,318,-553,770,856,232,587,-299,308,288,656,422,688,-1000,991,187,-246,1000,95,-723,281,-331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "writeBinary(com.fasterxml.jackson.core.Base64Variant,java.io.InputStream,int):int",
            new int[]{-404,714,341,-285,-1000,307,-66,-553,-353,-681,-310,1000,549,67,951,-472,-138,-463,-348,80,-1000,118,469,136,330,-1000,220,-769,524,793,-394,223,-462,-854,-1000,543,-115,314,731,-1000,421,1000,1000,853,-441,24,-936,757,308,-603,-244,333,1000,1000,1000,701,375,498,-476,958,392,-2,593,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "writeBoolean(boolean):void",
            new int[]{1000,-719,889,-842,379,54,656,1000,-306,-656,539,-794,-627,-637,1000,869,858,1000,-590,-260,856,-218,-500,-678,772,-967,477,209,-598,-41,417,953,204,-1000,-1000,764,-262,60,143,298,-126,803,566,1000,-532,-1000,318,-579,64,954,573,44,759,-91,-599,-7,-778,468,-50,-1000,766,574,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "writeEndArray():void",
            new int[]{168,1000,608,393,-250,-710,301,-114,1000,689,10,1000,-757,-293,818,-613,-442,845,-498,-1000,12,-1000,-1000,-335,433,731,598,1000,810,581,-391,539,79,-1000,860,-1000,549,-225,103,64,412,175,19,-549,1000,734,-622,-1000,1000,576,-87,36,434,814,1000,-1000,-301,416,1000,-1000,-1000,-1000,160,-738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonGenerationException", DEReplay.run(
            "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "writeEndObject():void",
            new int[]{-1000,982,-1000,-1000,1000,97,110,-111,-581,-56,-726,388,-909,363,582,400,739,784,1000,-133,-670,233,-11,-988,-1000,495,-818,-1000,-1000,400,1000,-227,1000,-1000,-550,-155,-92,1000,-197,-902,46,-451,1000,1000,-1000,-140,-286,-1000,1000,-1000,-1000,1000,-1000,327,997,751,955,145,-1000,-1000,-547,-1000,406,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "com.fasterxml.jackson.core.json.WriterBasedJsonGenerator", "writeFieldName(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-752,-600,-294,768,687,-943,-498,-6,68,-645,-1000,-1000,573,-142,205,715,43,214,494,-952,-1000,-698,60,-12,-119,1000,-425,-832,-364,-147,1000,43,-576,-6,-1000,13,-300,170,1000,-437,-983,-480,350,1000,-823,428,-491,-74,-812,-575,753,-779,885,978,1000,-676,-288,-977,-1000,781,-85,817,-609,-598}));
    }
}
