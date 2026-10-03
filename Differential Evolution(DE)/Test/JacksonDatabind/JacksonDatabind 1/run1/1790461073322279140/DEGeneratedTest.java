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
        org.junit.Assert.assertEquals("JSON:W251bGxd", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsColumn(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-1000,558,-101,-26,-99,139,-159,310,618,-499,28,111,-801,320,-1000,465,527,1000,1000,-319,-1000,298,-936,-529,-495,-496,-559,8,-774,185,-1000,467,395,650,-455,258,327,-571,851,-669,166,-223,204,106,-854,443,594,633,1000,-463,125,-903,-1000,-456,-607,782,-428,-272,1000,-592,505,-49,1000,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("JSON:W251bGxd", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsColumn(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{104,-819,-741,-351,-858,177,-41,908,936,1000,2,1000,-499,-805,-580,348,-618,580,767,-440,-997,378,-205,217,25,776,-565,983,51,1000,-1000,419,430,554,-404,-266,-911,-719,1000,-97,477,-1000,398,200,-854,-710,932,33,665,-1000,175,-485,-353,-447,104,1000,-190,-60,-362,-283,835,284,361,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("JSON:e30=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsField(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-281,788,959,942,-1000,1000,748,467,807,423,558,-310,588,217,-431,1000,1000,772,1000,1000,1000,-1000,-1000,-199,-324,-1000,-1000,-373,-199,-920,1000,-1000,-1000,943,457,1000,-1000,-315,1000,865,-380,1000,-1000,-611,-1000,1000,-873,-645,501,-304,882,608,1000,-1000,-397,1000,401,-1000,-1000,1000,1000,-1000,-613,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("JSON:eyJjb3VudHMiOm51bGx9", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsField(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-670,386,-884,999,819,-1000,-445,1000,-1000,-625,-630,-921,695,47,507,-479,-465,658,-1000,-798,-1000,766,1000,925,1000,632,-884,791,-1000,1000,-773,1000,694,-6,1000,-311,-1000,-434,-1000,308,-782,-194,1000,852,-15,613,381,-126,1000,384,-1000,-906,570,1000,-124,50,-217,1000,-1000,432,-1000,7,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("JSON:W251bGxd", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsPlaceholder(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-193,447,-886,312,216,-203,226,-862,-503,-334,-35,-401,224,-112,-85,-951,-678,-8,749,-143,110,39,-907,224,130,301,87,-825,192,-598,35,412,669,-938,-101,-782,-159,584,-73,-309,-202,-144,205,158,277,-241,-404,881,-307,-383,125,-143,-437,614,-306,-137,-433,-85,719,761,-455,680,225,-479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("JSON:W251bGxd", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsPlaceholder(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-769,1000,-96,1000,268,-60,-1000,-14,-402,1000,1000,-447,-998,-403,-743,-1000,536,1000,1000,-1000,1000,848,-373,705,460,50,519,-977,682,-1000,-1000,1000,1000,-1000,233,-710,-482,-672,704,-1000,-1000,1000,1000,1000,-1000,-1000,550,1000,516,-1000,-306,-1000,-499,514,600,1000,-1000,-904,1000,226,-878,1000,390,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "assignNullSerializer(com.fasterxml.jackson.databind.JsonSerializer):void",
            new int[]{725,44,29,1000,-272,835,-1000,79,-432,-1000,-245,-699,-305,297,-191,-60,65,-183,-885,-750,-815,806,-851,568,61,-344,-1000,-207,1000,-1000,639,-191,-289,-202,-913,573,-305,816,-481,-278,-328,-1000,177,886,1000,808,76,-7,-795,782,870,393,-1000,-401,1000,417,-761,-513,-1000,-917,1000,-712,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "assignNullSerializer(com.fasterxml.jackson.databind.JsonSerializer):void",
            new int[]{-209,-141,-341,403,-103,922,-629,295,807,-465,555,791,451,-854,-67,-145,56,-902,361,-567,-500,-560,931,-563,-584,374,236,378,-769,995,-390,462,14,-168,958,-742,-555,983,-579,717,-447,201,603,-719,-61,-881,-712,93,139,-974,-289,-88,498,70,288,-773,-527,-413,415,293,627,365,-522,324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "assignSerializer(com.fasterxml.jackson.databind.JsonSerializer):void",
            new int[]{865,478,-241,147,354,400,400,-1000,1000,-185,1000,-100,-104,-1000,250,396,477,-1000,979,695,601,820,-542,1000,459,-514,66,-34,149,-285,-375,-151,-246,413,-876,-61,311,213,595,314,-224,-574,778,-78,325,-1000,-40,1000,-400,962,970,488,-965,-287,1000,-400,-165,-224,99,126,-615,733,28,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "assignSerializer(com.fasterxml.jackson.databind.JsonSerializer):void",
            new int[]{-883,-254,-81,944,55,-885,-934,1000,-441,354,1000,840,-1000,220,-180,161,-199,578,-84,-44,-1000,-1000,283,-286,-1000,1000,299,1000,57,-860,83,-564,1000,951,247,1000,3,-1000,-1000,629,154,-58,-852,1000,476,-846,-753,-276,-580,-1000,912,-970,290,122,-30,-348,-1000,1000,616,1000,-285,-1000,1000,772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor):void",
            new int[]{-706,-199,811,884,709,894,460,-976,-914,-793,-743,-820,309,441,9,993,973,-643,-209,-221,-888,838,129,964,-257,-363,-453,646,-30,-373,391,658,572,985,-22,-627,-315,397,-82,481,-479,-857,740,953,849,684,-163,515,435,-753,-804,72,-442,-153,-628,-260,-707,134,-282,428,304,-462,38,197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor):void",
            new int[]{364,607,543,69,616,-287,-400,-1000,-185,-717,-281,-1000,672,-1000,-1000,1000,-252,-1000,-329,462,-1000,551,757,203,-227,-1000,558,-39,727,378,-278,-663,268,-391,607,-880,-384,270,1000,1000,-879,773,-436,160,911,289,-1000,506,115,891,-654,1000,716,-442,-1000,574,-37,889,811,-30,339,394,-374,567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.node.ObjectNode,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-691,-781,389,374,-65,-148,-33,1000,-480,-120,-924,771,-694,-478,-1000,-51,-1000,-884,353,317,-1000,254,-480,152,-417,-993,689,625,146,1000,-605,-440,-539,207,-665,-140,-678,1000,-660,-507,676,420,-469,42,-287,439,1000,654,-879,1000,-216,-228,-1000,-463,-514,93,538,421,875,237,66,115,-978,-117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.node.ObjectNode,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-741,-1000,129,0,-965,-395,236,760,486,599,-1000,238,-1000,-157,-10,-1000,-204,-965,518,1000,-1000,257,379,-386,35,-389,300,-541,496,1000,-1000,-659,-67,713,-764,-143,-1000,630,-772,-589,1000,320,-219,956,533,-69,1000,136,-1000,1000,-682,254,-756,115,-640,-759,1000,1000,1000,1000,-284,332,-523,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "get(java.lang.Object):java.lang.Object",
            new int[]{-751,-939,-711,-204,528,1000,610,721,128,-1000,139,-1000,-164,194,-371,-252,-1000,-706,35,-543,210,-221,-545,14,969,-722,207,-735,1000,-264,476,9,-1000,664,658,1000,332,205,147,-30,340,-717,-922,120,944,-187,134,1000,256,-1000,-936,240,641,341,385,-1000,-692,-1000,-367,-301,-698,1000,1000,403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "get(java.lang.Object):java.lang.Object",
            new int[]{296,-1000,-401,674,637,421,-534,1000,0,-1000,0,283,-314,423,-1000,732,1000,-663,539,-613,434,-432,-498,-283,1000,-552,660,-1000,1000,-381,400,-1000,-967,1000,716,398,-800,-43,2,939,-130,894,-881,750,-163,926,-411,1000,458,-459,780,-470,753,-202,-29,-1000,-493,-383,-748,-387,-884,1000,557,511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getAnnotation(java.lang.Class):java.lang.annotation.Annotation",
            new int[]{689,300,129,987,381,679,828,-683,-319,748,-729,-250,973,387,-639,488,-308,369,-221,1000,677,-926,28,1000,-557,71,-107,-1000,-330,-987,-685,111,227,-798,-703,-138,740,125,146,90,-900,-880,-103,1000,-1000,-194,1000,554,-653,-296,-959,185,-418,1000,-1000,755,350,334,600,689,388,1000,-749,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getAnnotation(java.lang.Class):java.lang.annotation.Annotation",
            new int[]{307,690,-176,-48,-11,1000,-776,759,-1000,375,130,-391,-359,401,431,723,16,188,399,-1000,-243,489,-577,-237,-504,322,-694,1000,737,-1000,534,-111,1000,-400,401,-816,367,312,-204,992,-323,-362,-264,-449,-511,1000,-409,1000,-636,-1000,1000,-909,-940,627,261,-120,-1000,-1000,-88,662,475,-355,99,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getContextAnnotation(java.lang.Class):java.lang.annotation.Annotation",
            new int[]{367,-1000,904,-981,-1000,-498,-734,-1000,-52,1000,960,859,871,-934,-852,816,-525,-954,-415,-347,-706,829,90,7,1000,-1000,155,1000,-767,157,-637,-465,881,-105,594,376,1000,-793,-1000,-172,1000,713,-149,464,1000,-17,737,279,-404,-669,1000,460,1000,645,-267,-1000,-488,-234,-780,268,-422,436,-122,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getContextAnnotation(java.lang.Class):java.lang.annotation.Annotation",
            new int[]{333,-206,794,1000,-334,-1000,-635,-1000,-1000,915,241,1000,462,-623,-835,-743,1000,-950,-1000,-158,-1000,-589,462,1000,-1000,-1000,1000,188,783,-1000,-1000,-973,-921,1000,-406,110,156,-1000,-719,539,1000,1000,-1000,935,751,966,-720,23,-85,-760,1000,1000,936,89,1000,-1000,914,766,665,378,579,-955,300,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getGenericPropertyType():java.lang.reflect.Type",
            new int[]{85,1000,259,-807,-384,1000,-250,150,1000,674,-779,744,-1000,34,-381,-368,13,248,-166,678,1000,-97,-1000,-379,-608,-473,-143,155,763,259,481,-1000,1000,197,881,687,-116,1000,-1000,-1000,1000,-1000,369,296,-1000,640,459,1000,-1000,850,368,-333,-699,-184,77,36,176,-858,83,193,306,1000,583,608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getGenericPropertyType():java.lang.reflect.Type",
            new int[]{-386,686,-351,29,894,980,-1000,1000,-51,-947,-989,-912,-486,16,1000,-323,362,550,-701,468,400,-1000,5,-648,-368,-152,257,582,1000,925,1000,384,-64,694,440,-623,-546,-144,311,-1000,-216,-931,-862,-250,-1000,-510,-417,699,-328,-1000,-14,-1000,-536,-753,627,536,536,506,1000,-212,212,225,-388,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getInternalSetting(java.lang.Object):java.lang.Object",
            new int[]{-767,-533,694,750,658,282,1000,308,822,1000,34,1000,1000,-424,1000,-252,27,-838,667,350,-834,654,323,163,150,-447,-7,964,647,-222,-63,564,-1000,-613,-246,-176,-31,931,829,-62,-996,170,154,216,264,-361,-251,248,348,-1000,755,733,-1000,141,521,679,-829,184,120,-231,-355,-1000,-171,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getInternalSetting(java.lang.Object):java.lang.Object",
            new int[]{-1000,989,-405,-91,-18,-104,-91,-20,-61,103,-345,-1000,828,-284,693,-768,499,-728,592,-1000,-1000,-11,-730,668,696,442,-611,974,195,-432,262,209,1000,4,1000,-775,556,453,-423,-554,-248,383,-1000,-549,-134,224,-12,-502,661,651,284,-73,-1000,304,218,-56,-98,615,-186,1000,1000,-444,884,869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.AnnotatedField", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getMember():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{456,899,-285,729,-459,302,454,313,-756,950,-500,93,61,-459,-324,-15,-98,440,831,-643,-946,-547,-236,734,-923,-53,-552,175,-900,380,128,-345,592,874,-507,-348,-190,65,-708,986,-112,166,-441,-49,-221,-143,882,-235,-873,-511,51,482,552,887,858,608,-212,999,-178,913,-325,601,169,150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.AnnotatedField", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getMember():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{201,1000,74,606,-307,-376,1000,1000,-1000,1000,676,619,-846,-592,19,531,229,-201,1000,-617,-670,-194,-437,908,-370,650,-1000,-296,-617,-1000,1000,-536,72,1000,918,-830,385,-118,0,759,-592,-177,21,-735,-463,-1000,668,-592,-1000,-609,1000,177,769,1000,27,1000,488,902,1000,179,-652,1000,1000,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:bmFtZXM=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getName():java.lang.String",
            new int[]{801,-401,544,1000,-343,-1000,-1000,-136,-564,-729,-19,345,314,1000,1000,615,-137,-563,-337,-932,-444,1000,1000,1000,362,1000,-1000,1000,674,1000,1000,-838,-1000,-570,1000,452,-959,-1000,-684,1000,385,528,863,-945,-1000,-73,1000,-103,-1000,-51,-1000,-1000,-1000,179,1000,496,-336,1000,-1000,626,-1000,-454,1000,20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:bmFtZXM=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getName():java.lang.String",
            new int[]{-368,571,-73,-211,675,353,-766,-101,-199,-398,615,-1000,-248,1000,-1000,882,1000,-982,425,-859,-755,-545,710,-451,-1000,754,1000,-693,1000,-150,-587,282,-1,423,-1,-575,-152,1000,1000,-880,-205,11,-78,66,694,663,-127,-18,-25,330,-245,-991,988,-455,953,1000,-74,427,249,-506,-946,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getPropertyType():java.lang.Class",
            new int[]{463,1000,-892,-782,296,389,1000,-153,-420,1000,-641,186,1000,-1000,-65,1000,718,1000,-873,-775,-598,775,-617,-270,273,-1000,287,-1000,684,1000,89,-111,680,1000,-64,494,738,-33,-1000,-1000,1000,-509,837,-432,-951,-1000,311,-1000,-1000,-38,-1000,641,-740,678,-1000,-748,339,1000,-1000,1000,1000,-1000,852,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getPropertyType():java.lang.Class",
            new int[]{-911,517,-867,-74,236,441,351,901,-71,408,475,-659,270,899,-720,56,183,-547,652,873,-706,520,4,-525,520,-247,-474,-348,70,305,-846,126,-920,200,262,517,430,-283,-887,-201,-593,-161,-160,634,-145,-676,382,336,173,680,-823,-98,142,476,-335,561,-98,751,-304,558,623,59,-236,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getRawSerializationType():java.lang.Class",
            new int[]{639,-276,-51,-735,288,424,-493,1000,-8,294,-736,-798,106,1000,-1000,59,-488,-374,-15,192,776,-299,1000,394,-82,-1000,-1000,-633,543,-382,1000,-700,-273,1000,496,-152,-568,-703,-518,444,-178,42,431,1000,625,492,-880,-939,529,-284,-1000,-1000,-583,-344,416,-31,1000,1000,-276,-958,-557,-516,1000,-348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getRawSerializationType():java.lang.Class",
            new int[]{-29,-448,-136,10,1000,-138,101,-83,843,-783,17,151,-924,1000,-212,1000,-1000,1000,617,23,1000,-698,208,-1000,-347,292,410,-75,198,641,960,-241,367,-755,1000,1000,1000,1000,485,188,-1000,129,-1000,-430,-1000,-421,1000,170,-1000,-881,755,-480,-130,1000,393,-587,1000,762,137,811,-306,-444,106,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializationType():com.fasterxml.jackson.databind.JavaType",
            new int[]{-1000,20,239,-397,-378,922,1000,1000,-1000,515,-1000,-305,-27,-1000,1000,-168,591,731,1000,1000,20,1000,50,-61,-190,1000,-962,1000,1000,1000,1000,639,-343,1000,566,-307,-179,1000,-1000,-272,-73,106,-1000,893,-1000,1000,536,1000,-147,175,34,-1000,1000,1000,-1000,288,-1000,1000,-906,-380,100,-19,-1000,-62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializationType():com.fasterxml.jackson.databind.JavaType",
            new int[]{956,-852,91,-991,-305,-198,244,-95,-88,-23,-851,669,-22,150,-930,-888,337,-433,933,941,-708,-75,-861,-400,755,630,-680,920,-209,-952,978,809,322,-709,-636,570,194,89,728,551,-31,65,92,776,-761,-631,-901,982,72,-12,-848,41,-746,-659,62,142,-580,764,725,303,-149,349,-36,788}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SerializedString", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializedName():com.fasterxml.jackson.core.io.SerializedString",
            new int[]{729,664,-477,-551,-289,66,-672,-207,398,-14,-117,655,185,183,359,-876,-614,-151,903,-337,678,-655,-591,-446,-370,-277,774,849,-118,305,-297,-373,-322,500,-144,-899,-752,-48,400,-209,849,-826,-120,-176,-891,876,744,-748,818,347,-192,230,267,-548,485,-144,813,-921,-289,544,-514,-805,145,-98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SerializedString", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializedName():com.fasterxml.jackson.core.io.SerializedString",
            new int[]{748,1000,-77,-116,-539,66,-1000,-710,479,-281,-1000,-184,1000,608,828,-1000,-77,331,603,-1000,693,-878,-639,215,-207,1000,787,261,312,-49,-865,300,-170,322,300,-366,-810,-1000,812,-514,712,327,31,-179,-840,-762,-357,-1000,1000,-88,-714,979,1000,466,553,-1000,743,-241,91,-720,-249,-1000,588,-98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializer():com.fasterxml.jackson.databind.JsonSerializer",
            new int[]{-267,-1000,289,1000,-72,703,-522,531,-269,-678,775,261,-148,-442,753,-552,400,-624,1000,-210,-981,-931,4,-96,-715,125,216,628,531,-514,1000,-603,-1000,48,-875,-27,4,939,32,292,1000,865,164,1000,-379,164,-1000,-342,232,169,657,714,-1,-650,1000,-1000,709,-895,1000,1000,-570,-60,-1000,488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializer():com.fasterxml.jackson.databind.JsonSerializer",
            new int[]{1000,373,-227,-111,-513,76,133,-17,114,-719,-1000,235,140,649,48,228,-282,3,578,-318,-628,257,20,-813,-479,369,-439,32,-716,15,639,264,-1000,386,1000,-18,-262,-401,819,848,206,-390,420,501,-389,-971,-1000,578,202,1000,534,74,-73,28,-25,1000,-66,-424,-670,254,-72,-715,-1000,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.SimpleType", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getType():com.fasterxml.jackson.databind.JavaType",
            new int[]{269,-420,269,-503,-140,1000,1000,-305,-623,742,1000,1000,-1000,-915,1000,1000,156,-1000,-1000,1000,-1000,798,694,-422,-1000,-269,-1000,-1000,-79,257,207,1000,57,124,1000,-1000,288,1000,652,-1000,1000,-1000,-576,-959,-1000,30,-333,516,1000,-1000,343,1000,-1000,-335,-1000,-1000,382,-1000,1000,1000,-171,1000,654,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.ArrayType", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getType():com.fasterxml.jackson.databind.JavaType",
            new int[]{-137,-979,-446,306,-145,641,-536,-680,-549,-181,752,-455,-766,-957,-993,-783,462,-526,-846,881,88,289,20,686,-850,380,871,-736,-710,-572,-159,-235,-214,-572,689,738,-688,312,-149,-865,445,492,366,-77,820,860,950,-462,808,-944,-808,728,-394,-313,-317,207,-294,-455,716,-12,-166,902,-850,256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getViews():java.lang.Class[]",
            new int[]{-76,-590,405,-452,10,-627,516,-12,-282,792,259,1000,966,118,566,473,419,-544,958,612,-798,443,215,121,374,583,-184,216,-263,-261,759,-1000,364,801,419,109,-1000,661,590,354,-64,1000,411,767,39,664,-587,125,485,-1000,-1000,-385,190,-1000,285,1000,-807,-400,-257,-412,-220,882,-437,-797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getViews():java.lang.Class[]",
            new int[]{-663,852,654,916,981,806,-362,289,-342,527,382,-704,628,930,-107,130,360,-486,-474,-838,15,-209,-590,315,-651,-355,-968,619,861,147,117,984,48,-571,-911,599,344,281,-933,691,490,-636,-380,912,-90,132,319,-930,-21,859,-518,536,-4,-450,468,-694,146,910,367,381,-250,-947,6,899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getWrapperName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{-365,-193,203,856,870,-714,313,102,228,-510,642,-509,164,130,817,834,-838,266,-769,-94,91,-26,-503,164,-529,-594,986,129,324,-439,-58,-811,-703,941,652,744,-749,-704,738,-99,-386,-307,745,-734,466,-744,-97,753,513,-896,-819,-209,-67,-753,-422,840,382,492,-45,-309,383,794,676,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getWrapperName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{-990,-960,233,-122,645,-1000,65,514,42,-1000,560,597,-1000,-53,-250,932,-761,1000,-1000,-929,672,-768,-1000,-426,-103,-1000,352,125,-1000,-1000,932,-453,1000,424,-765,-79,-1000,1000,686,118,-227,1000,841,-699,-123,-553,1000,1000,-276,1000,824,-991,-855,554,10,-44,-128,-1000,-567,-881,481,-110,713,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "hasNullSerializer():boolean",
            new int[]{-531,-1000,753,-320,435,-1000,-141,-378,-725,-1000,-186,-498,-744,-114,570,-614,-298,-1000,567,76,1000,492,-1000,-327,576,618,573,-529,92,-444,-1000,813,-1000,-1000,-33,-242,180,1000,1000,-726,860,-582,778,89,-984,-836,71,-1000,556,248,855,68,-1000,82,-1000,1000,-1000,956,-1000,693,-587,-1000,696,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "hasNullSerializer():boolean",
            new int[]{131,919,-101,-879,-624,541,726,265,32,692,481,-373,-268,-710,-668,-463,994,-898,-134,706,262,-379,152,-880,-256,-117,322,779,-235,920,688,733,508,201,-165,-902,147,-295,-432,-481,-356,50,113,868,762,-499,-435,904,-782,604,192,-913,-686,713,928,742,-113,-280,167,411,-53,938,-244,348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "hasSerializer():boolean",
            new int[]{205,-295,-166,-497,1000,300,622,-664,-99,67,673,-423,-1000,729,-264,-377,-1000,-1000,-350,397,-215,805,237,-370,7,-355,338,1000,295,-1000,-432,-1000,400,-1000,1000,422,1000,-660,-246,105,205,686,-251,1000,-179,-81,618,-360,-1000,151,-1000,384,1000,823,1000,1000,-400,-527,185,159,-530,-414,603,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "hasSerializer():boolean",
            new int[]{523,-460,-71,240,874,727,1000,-710,-206,-734,6,-237,-490,811,163,-1000,-1000,-966,958,900,466,-501,-82,-425,-582,-745,-257,281,439,-136,-415,-596,624,-1000,1000,-978,1000,-618,77,-917,1000,-127,-594,1000,364,38,324,-481,-452,-646,-863,-98,1000,841,1000,690,-1000,-278,-729,610,51,-641,1000,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "isRequired():boolean",
            new int[]{1000,1000,-363,909,-60,442,-358,1000,615,-982,890,1000,27,90,750,-879,91,-744,-55,-713,-969,1000,-790,425,-340,-113,1000,1000,-1000,-196,1000,369,1000,938,307,-1000,443,-1000,1000,-1000,-295,314,935,-941,756,234,12,31,111,-1000,-1000,198,-594,20,180,-1000,52,386,-472,-1000,-199,1000,171,-481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "isRequired():boolean",
            new int[]{-508,89,-693,269,1000,-223,-392,545,-1000,822,-692,-382,1000,-1000,-1000,-178,302,-912,44,337,1000,1000,-1000,-1000,-1000,62,1000,-1000,-411,425,-1000,-544,605,-691,926,-232,1000,123,1000,-677,1000,181,1000,9,-1000,-1000,1000,-1000,440,454,1000,999,-1000,-503,-1000,1000,1000,819,218,580,-28,-1000,1,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "removeInternalSetting(java.lang.Object):java.lang.Object",
            new int[]{-1000,-698,-835,-116,296,-156,1000,1000,736,689,1000,461,101,493,755,1000,-93,-133,-1000,720,340,493,1000,-1000,379,218,329,1000,270,468,1000,204,-526,775,-548,-1000,352,-1000,-619,-897,-88,-1000,-635,487,1000,1000,293,-916,464,-481,-505,-714,1000,-70,-400,1000,-938,750,-832,-577,518,292,-559,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "removeInternalSetting(java.lang.Object):java.lang.Object",
            new int[]{-232,107,-215,369,1000,446,-935,23,982,-225,36,-1000,1000,-1000,-1000,887,-195,1000,-1000,137,678,122,1000,305,967,121,-1000,-1000,-596,416,-1000,1000,987,738,-1000,567,-1000,-404,-94,-416,984,1000,1000,227,-851,475,666,768,1000,-66,-164,-568,-413,1000,1000,907,-790,1000,871,53,18,15,-225,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "rename(com.fasterxml.jackson.databind.util.NameTransformer):com.fasterxml.jackson.databind.ser.BeanPropertyWriter",
            new int[]{-1000,754,-381,-101,-990,-229,758,157,-1000,-788,-578,-880,-434,-328,206,239,65,438,663,-666,294,-154,517,995,376,-589,-578,817,1000,240,185,479,-1000,240,-42,-298,96,-21,443,141,-254,-165,-734,425,577,-984,385,-187,-1000,177,157,124,514,-1,644,-680,-831,-77,137,396,-872,56,-283,90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ser.BeanPropertyWriter", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "rename(com.fasterxml.jackson.databind.util.NameTransformer):com.fasterxml.jackson.databind.ser.BeanPropertyWriter",
            new int[]{492,182,-694,-591,-483,491,1000,1000,-651,-180,-90,396,-1000,-177,315,1000,1000,1000,-870,631,-9,-1000,-150,-723,970,-1000,-757,-266,-560,-193,185,42,-599,880,-204,1000,-103,-41,1000,-404,919,888,-1000,936,1000,748,944,-673,160,-560,-336,-717,-689,-896,784,424,-1000,-1000,1000,-430,153,-203,-113,-171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "setInternalSetting(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{-147,634,344,-150,414,64,-14,-375,-1000,13,492,965,208,-23,326,-297,-1000,-785,-109,39,-623,-1000,-863,1000,1000,781,-625,540,548,695,-541,476,212,563,316,-982,-359,438,231,-442,98,1000,-304,-1000,459,-153,-282,1000,-1000,-150,-1000,331,-967,729,103,1000,-218,-856,1000,479,179,486,617,58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "setInternalSetting(java.lang.Object,java.lang.Object):java.lang.Object",
            new int[]{819,643,-456,-1000,-440,-318,446,844,933,765,426,-1000,-703,-1000,-613,-1000,-825,-577,1000,649,437,-218,-440,-270,-607,777,-55,386,258,-966,-170,-74,-253,-353,159,-245,-187,1000,583,-439,-603,-236,-62,657,1000,951,-763,72,-204,741,-221,-1000,218,-768,-225,555,-894,76,-58,-118,-430,123,229,372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "setNonTrivialBaseType(com.fasterxml.jackson.databind.JavaType):void",
            new int[]{-775,-1000,-731,-606,1000,1000,1000,1000,290,1000,-148,634,-183,-1000,422,-558,-828,1000,-1000,-436,-1000,-1000,-403,-1000,198,-1000,388,770,-576,-1000,1000,1000,1000,525,1000,-966,1000,-1000,194,1000,-727,1000,-1000,690,1000,-799,-1000,-137,-1000,-1000,-1000,179,374,1000,-1000,589,-342,727,728,-1000,309,-1000,1000,-535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "setNonTrivialBaseType(com.fasterxml.jackson.databind.JavaType):void",
            new int[]{-587,-599,813,285,733,131,-533,1000,1000,517,-953,699,1000,640,238,972,-465,165,-1000,271,-1000,1000,-440,-196,-230,-313,1000,-127,-723,334,436,-524,-829,206,1000,-554,-705,901,1000,1000,-300,903,-79,-856,570,-246,-1000,-16,-267,668,1000,-1000,-100,-1000,198,-122,44,213,-492,389,335,821,-740,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.String:cHJvcGVydHkgJ2NvdW50cycgKGZpZWxkICJERVJlcGxheSRHZW5lcmljSW5wdXQjY291bnRzLCBubyBzdGF0aWMgc2VyaWFsaXplcik=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "toString():java.lang.String",
            new int[]{385,-1000,429,-306,350,-795,1000,-1000,-1000,-542,444,-783,-1000,-472,487,-1000,509,1000,-183,-189,321,-799,-1000,1000,509,1000,1000,-133,880,130,-927,247,-1000,-376,298,1000,1000,429,1000,1000,1000,-1000,1000,856,-1000,584,338,912,1000,1000,-452,1000,955,-821,1000,-764,546,-400,1000,-1000,-673,410,-1000,-433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.String:cHJvcGVydHkgJ3RleHQnIChmaWVsZCAiREVSZXBsYXkkR2VuZXJpY0lucHV0I3RleHQsIHN0YXRpYyBzZXJpYWxpemVyIG9mIHR5cGUgY29tLmZhc3RlcnhtbC5qYWNrc29uLmRhdGFiaW5kLnNlci5zdGQuU3RyaW5nU2VyaWFsaXplcik=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "toString():java.lang.String",
            new int[]{-587,-144,-96,-443,-864,-1000,31,39,-819,-754,1000,888,-124,429,319,-310,560,453,310,211,-235,-548,-992,-294,531,-64,-722,436,251,-1000,484,-804,-574,-1000,284,1000,-705,-1000,-1000,-249,362,-1000,563,-656,-349,1000,-535,471,-456,264,991,655,-1000,-1000,1000,-5,-272,-286,398,-225,-227,695,-170,-279}));
    }
}
