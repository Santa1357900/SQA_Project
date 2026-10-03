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
            new int[]{-767,860,-92,243,803,723,161,-954,-658,67,-978,457,-618,-268,-777,-452,-935,277,351,-922,544,-336,-969,719,-541,122,927,-84,29,-58,63,535,133,-696,905,107,-745,-329,148,-793,-787,-231,214,-780,-837,-288,-895,-365,537,460,-578,864,302,457,-858,-457,193,-972,27,303,-556,944,452,-931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "append(char[],int,int):void",
            new int[]{-806,-945,467,512,-1000,839,210,951,266,-176,-629,-899,730,79,-1000,-875,408,1000,-680,149,-481,1000,-701,-216,-258,-348,885,-628,-1000,-1000,-615,-169,-631,-229,1000,-620,-1000,-1000,333,1000,186,861,1000,434,-647,1000,578,1000,-1000,-195,-896,1000,90,-1000,-1000,-386,-1000,1000,867,702,-1000,925,-132,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "append(java.lang.String,int,int):void",
            new int[]{520,-570,966,683,810,78,-531,-211,-873,178,-254,150,450,702,-450,144,-27,-388,877,-388,11,-653,763,-49,-7,-929,-398,-634,730,-289,212,-426,192,-1,-824,-77,-855,-644,389,-903,-829,769,-799,-634,838,-413,38,-303,64,545,-986,-73,114,-711,-376,703,-947,139,371,876,2,917,-82,-298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "append(java.lang.String,int,int):void",
            new int[]{716,-30,-976,-276,429,-275,-642,891,16,-554,522,-22,29,137,350,596,584,-207,962,-523,-181,211,19,180,605,-605,738,-276,-440,231,205,546,-579,-827,883,838,-5,-563,-427,-476,40,-602,-432,-61,680,735,-766,612,267,229,-151,449,940,54,-833,-887,582,-70,884,-607,334,534,-64,988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsArray():char[]",
            new int[]{196,810,704,-348,-668,77,-390,169,651,-902,-54,-189,-441,-3,8,-98,-931,-192,-579,-634,-393,-301,565,-322,-305,-719,915,4,-221,-827,-175,316,-142,796,-313,-540,556,-670,-550,309,817,-258,11,-556,-526,836,132,111,630,398,219,596,1,-320,-437,-361,-875,-302,441,-22,750,-883,-129,757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsArray():char[]",
            new int[]{-640,291,702,854,846,-216,929,171,614,159,-233,966,58,248,-634,746,-20,-524,-446,-240,612,367,600,255,-418,319,725,480,-142,813,150,754,-868,-911,932,-906,44,-757,825,91,-774,681,-41,985,183,-83,-711,-767,745,825,-576,238,-4,607,-290,383,410,-13,255,-981,230,450,-33,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsDecimal():java.math.BigDecimal",
            new int[]{216,-897,294,598,590,-310,-712,-782,-469,-138,83,985,-862,-304,-127,374,-704,120,-554,351,-541,-42,-899,-863,524,691,982,857,555,649,368,421,-59,-485,-941,198,-549,-265,-307,753,643,-276,524,287,41,594,50,-763,639,-466,-316,-13,386,954,608,603,-469,-963,-41,89,976,430,-498,786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsDecimal():java.math.BigDecimal",
            new int[]{104,-537,-926,-777,956,523,-709,-23,70,976,-827,-83,-171,-631,714,597,-269,-413,-251,470,644,69,882,135,-184,242,-232,864,-830,-67,546,-702,252,-341,-140,-611,81,433,-430,413,-875,990,-21,-811,-371,208,238,-464,-410,870,393,412,855,-70,531,202,982,-71,404,178,-201,353,9,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsDouble():double",
            new int[]{-459,617,529,-721,150,311,-389,-442,382,2,638,-635,-666,-739,692,-545,-895,483,-505,526,-321,-84,-371,-233,-31,-21,-527,19,560,-422,309,230,-317,-176,-470,287,-606,-793,923,806,63,-919,597,-982,-157,-854,-738,-518,23,-523,317,-964,900,648,467,-844,116,471,-559,-356,305,986,-210,-517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsDouble():double",
            new int[]{-610,-662,881,-792,-8,446,389,-133,-265,-179,-817,-48,759,208,-169,805,640,-832,245,-759,-316,-6,207,-770,189,853,850,-579,832,225,-350,40,296,348,950,93,704,112,95,301,-434,569,-189,729,492,100,675,709,182,138,-817,302,-95,320,-746,-697,26,-39,-167,-430,4,798,660,-654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsString():java.lang.String",
            new int[]{523,830,-836,192,-746,-116,-504,-887,974,-551,239,994,-998,570,-436,77,-720,-458,625,22,884,699,-897,626,265,-995,417,63,-464,-66,819,55,-934,757,-947,-172,128,65,301,168,429,-864,-275,-525,-31,-444,-912,-713,857,426,632,-546,493,-577,-301,848,796,-989,-586,320,-927,170,-838,458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "contentsAsString():java.lang.String",
            new int[]{554,-834,-442,-366,597,65,142,-194,162,-998,130,600,-728,-499,-783,301,-151,-434,620,-445,-411,709,383,-173,349,596,-752,-46,-115,568,362,494,723,955,-467,989,471,782,766,-230,-108,245,456,-549,751,179,707,-122,580,599,30,1,288,385,-88,829,861,176,606,-546,-749,632,920,998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("ARRAY:[C:200:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "emptyAndGetCurrentSegment():char[]",
            new int[]{-166,380,204,719,817,-778,158,897,-747,-931,-228,488,522,-947,739,-762,-464,-461,-96,582,794,295,5,116,708,-661,-789,40,650,-433,365,454,29,913,860,431,399,-304,-118,101,40,75,-583,-347,-222,-942,828,118,885,431,114,75,851,102,-21,-8,500,-122,-211,-157,519,499,-876,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "ensureNotShared():void",
            new int[]{495,-716,165,-766,-987,482,-142,-256,-973,165,-952,437,338,-204,-160,108,-204,807,454,695,644,552,-294,-970,-530,111,825,-91,108,980,268,-344,669,-90,735,-402,773,-209,336,556,-996,-147,-466,833,-369,220,327,-978,711,314,-32,-605,853,-438,476,-348,-905,377,-863,376,-349,-493,276,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "expandCurrentSegment():char[]",
            new int[]{417,48,-484,253,-948,-956,-329,424,10,-447,-311,-756,880,-589,-393,199,355,553,-995,671,-60,117,604,-268,80,-790,380,-12,115,576,973,-383,664,-192,487,584,-996,-497,-68,-833,810,-196,-516,-606,-788,159,521,760,556,-206,939,681,-519,398,-335,-445,-49,-182,452,-1,-239,196,-914,-145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "expandCurrentSegment(int):char[]",
            new int[]{988,-710,-941,-158,314,156,738,215,727,-518,488,554,-355,-772,813,570,70,-980,-621,-64,53,-160,152,81,-931,-346,545,327,-189,-537,-485,-627,-443,-241,-989,-295,973,-631,-396,492,-19,-709,733,224,147,-720,570,-490,934,644,216,-815,-852,-978,-352,-761,866,-260,651,-92,-715,-245,829,-261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "finishCurrentSegment():char[]",
            new int[]{-354,-515,-974,933,574,-170,578,770,69,-257,455,444,-744,-448,-839,707,-550,488,228,682,-604,977,-243,270,-578,656,-477,561,-790,411,662,-416,184,-432,-105,421,-337,535,77,-193,-120,651,-576,-305,702,755,-780,-70,496,118,11,-799,933,-274,-662,-280,750,667,-64,713,474,-641,-45,723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("ARRAY:[C:200:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getCurrentSegment():char[]",
            new int[]{-53,-184,-206,-606,980,-853,270,-415,-755,-677,615,-356,996,-375,338,-410,730,-885,-237,-743,882,349,932,-72,493,3,312,-244,316,-621,193,-536,-560,-832,-908,-712,252,933,-40,201,272,313,537,89,-713,176,825,742,45,-402,295,249,778,887,212,-263,-209,-317,241,-749,473,580,-642,998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getCurrentSegmentSize():int",
            new int[]{308,174,-363,979,-967,970,-873,-180,913,-298,746,-215,-533,750,-183,456,41,207,964,655,550,-542,834,107,-152,577,749,949,-557,180,-327,-357,-390,-209,-560,687,-741,-332,-167,-935,390,-415,823,347,439,100,-988,-15,-665,-521,-796,485,763,624,-135,888,740,-869,-769,-777,-621,-124,827,-494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getTextBuffer():char[]",
            new int[]{555,265,162,-138,-415,648,-316,-16,246,-672,390,909,692,-803,-126,466,-838,947,-376,-862,304,578,820,-867,382,701,837,897,208,114,81,-615,-352,402,-26,-855,-549,-17,-86,271,762,884,-130,642,210,-148,-113,-502,710,891,243,-638,-747,-757,246,-376,855,970,-265,-351,575,-189,-751,201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "getTextOffset():int",
            new int[]{199,74,419,282,-444,-360,-198,-679,446,-662,462,-32,969,-601,405,-308,540,663,-168,-351,-522,720,-660,293,-617,638,-425,-908,-383,-641,996,717,753,885,-46,-702,-61,378,428,-302,-200,-559,-556,146,-298,7,460,-2,3,499,807,-39,249,-763,657,-355,-818,397,-64,989,-87,-701,86,-214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "hasTextAsCharacters():boolean",
            new int[]{-43,-680,-400,-131,280,-295,-534,120,1000,714,-666,967,507,-947,1000,-60,1000,-773,-892,545,-1000,878,-260,618,243,960,317,-936,-1000,-487,-89,60,-703,691,-736,204,-982,749,-238,-504,916,-641,683,-969,-1000,-444,-1000,795,307,1000,-172,-582,-719,-145,255,107,-1000,-1000,423,1000,-473,1000,558,-427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "releaseBuffers():void",
            new int[]{670,-486,272,639,-253,-544,395,-670,348,270,308,294,421,-937,268,-25,633,844,229,217,878,21,-567,-818,-394,938,950,-669,756,-206,-702,462,396,781,26,965,-102,-233,-490,826,946,-539,-888,-795,-470,598,333,-100,-352,436,597,-564,462,573,938,-882,-711,975,989,-39,-982,-49,-597,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithCopy(char[],int,int):void",
            new int[]{30,586,-303,-301,120,382,316,-501,-148,-116,716,118,-865,-584,-342,799,486,624,-402,-1000,-12,1000,-463,-486,-48,1000,-156,-781,-473,-865,71,-349,480,67,995,-425,244,887,799,321,-392,-396,551,-261,-389,423,-896,93,-15,-784,-277,-444,-1000,39,445,-85,-811,141,696,144,291,-96,59,69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithEmpty():void",
            new int[]{-611,749,467,583,-159,233,-808,835,346,-732,-388,-999,-245,-799,32,696,595,-797,523,-841,905,-642,705,925,-455,992,-293,-211,-521,903,-213,-351,-309,-727,876,-206,315,517,-82,655,-665,96,295,-348,783,413,843,-209,-259,-690,-969,-20,657,-612,698,-357,478,-580,-566,-869,966,431,155,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithShared(char[],int,int):void",
            new int[]{280,-731,941,-272,-294,576,371,375,-44,148,-270,200,619,-779,-452,-46,-805,-923,407,-315,-330,139,341,-169,238,-602,244,-956,-44,6,-356,160,713,660,-106,-21,194,-467,344,-976,-265,-68,-618,309,-96,607,361,-742,452,692,383,429,-103,-359,-339,424,-626,178,770,492,-333,-703,459,709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "resetWithString(java.lang.String):void",
            new int[]{-308,-726,559,-417,-63,280,-706,-288,-759,33,808,-468,-245,-439,521,-406,-787,-863,276,364,348,-295,375,-591,215,833,592,511,159,-345,-853,-398,372,-30,494,33,-6,-316,-568,633,537,137,544,247,-959,479,222,-11,-184,262,-309,-645,-101,-359,-681,459,-602,-785,-749,222,-177,399,928,-917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "setCurrentAndReturn(int):java.lang.String",
            new int[]{-896,-750,-804,872,582,-227,-810,-685,524,-91,604,-341,-800,-570,-726,-934,492,450,479,462,340,-500,375,-785,-75,415,756,930,971,532,630,119,202,-726,755,380,79,-201,-542,-413,-278,-942,-185,544,-825,-638,-422,600,9,-668,126,767,-297,-13,-860,52,176,641,-288,-86,396,-730,166,-992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "setCurrentLength(int):void",
            new int[]{604,-887,-493,-737,962,606,-566,134,765,-954,64,-404,868,652,-509,134,-567,769,-367,-80,-96,549,-677,-524,-916,846,-892,-849,-538,-901,784,151,-148,-337,-836,285,-83,705,608,-937,-804,-408,446,-667,-81,-499,-427,273,-964,893,-773,-796,680,417,232,-559,-436,882,854,188,721,942,-210,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "size():int",
            new int[]{-664,18,-273,374,642,847,833,-288,329,-209,-305,-471,-124,139,-949,612,-832,-624,-125,-32,335,795,-33,211,452,812,459,950,550,600,722,432,222,-735,-287,-721,891,941,407,-638,718,-93,-364,-382,84,103,384,913,589,278,-593,-67,606,-600,-120,-455,549,947,728,32,-763,-800,-754,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "toString():java.lang.String",
            new int[]{513,-583,-330,212,169,594,444,-556,-632,414,-787,778,-508,416,-921,173,-966,-769,-666,-916,402,-94,256,826,-331,437,-339,766,-424,-606,-2,-829,848,352,364,525,-110,-483,780,-17,251,422,276,240,938,-163,-84,539,575,171,766,-165,716,-469,953,154,194,-653,-484,116,-729,-988,-507,255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.util.TextBuffer", "com.fasterxml.jackson.core.util.TextBuffer", "toString():java.lang.String",
            new int[]{-374,761,-792,318,-254,869,-956,-807,386,-984,-860,-596,833,-582,262,906,449,-170,-123,-796,-105,525,435,551,-978,183,-181,862,129,-52,-858,547,14,740,623,447,-746,-416,-732,922,491,209,281,734,-831,-560,-291,845,96,23,690,489,-477,436,-152,966,973,965,-120,476,1,872,-806,-977}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("ARRAY:[C:7:24:java.lang.Character:IA==:24:java.lang.Character:LQ==:24:java.lang.Character:LQ==:24:java.lang.Character:NQ==:24:java.lang.Character:Ng==:24:java.lang.Character:MQ==:24:java.lang.Character:IA==", DEReplay.run(
            "com.fasterxml.jackson.core.io.JsonStringEncoder", "com.fasterxml.jackson.core.io.JsonStringEncoder", "quoteAsString(java.lang.String):char[]",
            new int[]{450,638,-592,-22,-561,863,-799,-209,-708,154,732,-269,538,-122,-979,132,-257,-210,4,-512,790,-648,-206,669,641,64,122,771,169,81,-513,523,252,479,-201,133,672,939,-704,803,590,-663,420,727,351,733,-43,-658,-787,605,259,-173,232,832,642,-585,-44,865,-850,-41,871,834,810,-667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "com.fasterxml.jackson.core.io.JsonStringEncoder", "com.fasterxml.jackson.core.io.JsonStringEncoder", "quoteAsString(java.lang.String):char[]",
            new int[]{-617,320,-433,161,-66,254,-422,-730,-258,711,-273,-99,-738,117,-816,108,689,718,-474,782,-126,-221,-440,246,262,812,382,-337,739,648,1,178,-161,-751,68,743,-839,551,-699,866,-977,368,-961,-873,-593,-634,52,271,-932,-493,167,909,-700,972,-397,404,323,-441,-1000,-566,-504,-188,18,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SegmentedStringWriter", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "append(char):java.io.Writer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SegmentedStringWriter", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "append(java.lang.CharSequence):java.io.Writer",
            new int[]{-233,-526,13,174,-430,975,-707,-707,740,-397,919,-722,-691,570,118,232,-496,-147,-30,616,136,-501,431,212,-597,-828,-930,302,225,393,483,324,132,809,892,-973,-592,-932,803,283,717,354,-264,-853,747,527,-954,493,-449,940,-364,629,211,439,950,-376,205,-99,78,811,433,-337,-985,-116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SegmentedStringWriter", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "append(java.lang.CharSequence,int,int):java.io.Writer",
            new int[]{-488,489,-782,624,178,519,221,36,-114,-30,-66,781,-690,-181,870,-9,-912,-157,398,-556,-33,490,642,375,201,-389,-847,-663,-887,695,-69,-315,441,542,961,689,-12,420,280,-441,-580,-328,-207,724,-374,445,523,-406,934,784,808,739,-262,669,73,921,-436,-271,-772,-260,-520,388,496,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "getAndClear():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(char[]):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(char[],int,int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(int):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(java.lang.String):void",
            new int[]{-441,-84,289,-484,690,-587,-728,-793,663,896,-331,-911,-333,878,-612,-456,842,-468,-289,635,659,-484,359,844,355,576,992,970,-755,605,294,184,943,-391,-667,-437,835,430,-543,-631,-505,-977,634,-231,192,-443,-156,762,-924,-291,896,-354,210,-886,891,-458,234,-843,419,181,-919,-991,551,480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.core.io.SegmentedStringWriter", "com.fasterxml.jackson.core.io.SegmentedStringWriter", "write(java.lang.String,int,int):void",
            new int[]{278,673,24,-757,109,-722,-645,106,-328,-527,691,934,842,290,-981,810,-466,589,459,-627,408,126,-416,142,-73,-471,-371,328,-718,-408,528,685,-741,-574,954,963,-475,388,572,-161,976,417,-427,794,-155,-555,218,-635,39,886,-389,423,-140,835,190,709,661,-386,555,-310,302,-464,141,-971}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "appendQuoted(char[],int):int",
            new int[]{-348,-863,509,-682,592,771,-837,252,-823,-450,251,7,650,-511,970,-235,431,-308,-751,-479,245,-237,264,248,-174,-488,961,528,-913,612,-954,-473,460,-845,373,446,-226,-259,942,131,449,-332,837,-600,65,90,-657,825,444,-417,-790,196,245,465,-423,701,790,-576,-592,996,-667,-451,489,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "appendQuoted(char[],int):int",
            new int[]{-24,-796,-191,703,699,-886,218,587,467,746,20,-926,-460,-395,-302,-528,-57,-789,172,639,-744,-375,-739,-171,701,-197,-294,835,-932,-966,-177,931,806,58,-633,371,-755,101,-574,-237,391,40,304,-521,953,171,-732,-513,370,-337,732,-558,464,-344,-972,-296,62,-813,253,71,-568,663,833,427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("ARRAY:[C:3:24:java.lang.Character:OA==:24:java.lang.Character:Nw==:24:java.lang.Character:MQ==", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "asQuotedChars():char[]",
            new int[]{9,-621,403,871,150,-725,-649,-84,-82,573,66,959,-932,-378,-485,616,637,953,-162,-983,-121,-219,419,475,353,767,891,-197,176,-332,813,-281,-373,487,-365,-233,-531,-738,-403,-257,-364,723,229,742,258,-273,241,644,-613,-434,766,98,258,67,-855,-127,-598,-296,767,833,-447,-102,-201,-267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "com.fasterxml.jackson.core.io.SerializedString", "com.fasterxml.jackson.core.io.SerializedString", "asQuotedChars():char[]",
            new int[]{-562,948,993,770,823,429,-125,-608,-818,189,44,672,-761,-753,-922,-868,-815,659,-507,536,-489,-528,-308,562,234,586,165,355,681,-708,-196,507,119,-775,-934,30,-268,980,-231,-571,724,-228,-698,-877,-371,142,166,150,338,-37,-69,-663,774,756,-60,-53,689,693,-638,-837,-376,566,599,-260}));
    }
}
