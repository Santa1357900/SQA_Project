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
            "com.fasterxml.jackson.databind.JsonDeserializer", "com.fasterxml.jackson.databind.deser.AbstractDeserializer,com.fasterxml.jackson.databind.deser.BeanDeserializer,com.fasterxml.jackson.databind.deser.BuilderBasedDeserializer,com.fasterxml.jackson.databind.ext.NioPathDeserializer", "deserialize(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):java.lang.Object",
            new int[]{-165,46,609,-778,-608,-160,-203,-725,897,-622,541,660,842,92,958,403,289,411,-860,550,-368,-588,-784,537,-677,-808,357,-475,915,-20,-484,464,-389,563,697,-329,-835,606,-616,50,485,613,364,721,562,-95,670,803,437,804,588,361,-112,421,772,-884,175,-57,376,-574,782,-897,-168,242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-600,-1000,266,49,-161,912,-934,167,-1000,-551,-61,1000,363,-1000,-371,166,-529,575,51,-852,-438,139,-10,1000,953,179,-85,292,143,160,345,-907,-396,-638,898,-728,-185,116,779,-933,1000,655,-583,-309,-544,-28,281,846,-325,-13,19,-195,609,-913,-555,740,1000,160,904,-153,694,-179,-328,-196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-1000,-605,-587,164,-253,-1000,83,495,-650,688,-1000,841,-954,-1000,400,201,-1000,1000,1000,-1000,1000,-1000,1000,-400,1000,1000,-1000,287,400,642,686,-970,-400,410,-361,-20,-788,141,41,-1000,1000,-567,204,-400,930,-659,397,-314,-1000,1000,-1000,1000,-268,1000,410,712,-254,475,1000,-847,-613,-1000,-1000,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-1000,-821,-194,-967,484,-630,1000,682,-310,739,-326,-596,394,-965,574,151,112,852,1000,-489,788,-1000,1000,-1000,1000,1000,-1000,-1000,1000,-85,543,-611,-385,-1000,-1000,-640,-1000,-376,448,-1000,792,-182,350,-1000,635,-1000,-476,515,-1000,1000,-1000,645,-1000,851,-1000,1000,-932,449,763,-318,-37,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{70,992,-944,683,-1000,-974,-876,518,733,-386,1000,184,-437,-186,562,361,-237,1000,463,-71,-413,231,-771,527,263,6,251,-245,-1000,-804,205,-155,-420,-372,-908,-276,1000,236,-496,-891,529,1000,1000,-455,403,600,52,291,1000,839,-657,-474,-817,-198,-1000,-330,507,555,586,274,-1000,-380,-1000,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{265,-921,552,780,-1000,-1000,1000,1000,-497,-1000,-715,647,-1000,-1000,-1000,-702,-1000,-701,715,-1000,-1000,1000,622,-1000,1000,1000,-1000,1000,-548,1000,-736,-400,1000,1000,664,800,15,-1000,1000,1000,-1000,-1000,1000,1000,1000,-1000,1000,-593,1000,-1000,1000,540,-924,-1000,481,787,1000,-1000,-1000,1000,400,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-866,-1000,-346,23,-1000,-1000,43,164,1000,1000,-1000,397,347,1000,704,474,783,1000,1000,1000,1000,-1000,347,1000,-763,1000,-1000,805,-1000,1000,466,-1000,1000,176,794,313,-832,-1000,-411,945,370,1000,1000,-1000,1000,1000,-1000,-554,1000,-660,-312,952,-1000,-1000,-1000,-1000,1000,-852,456,1000,1000,467,435,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-860,-286,-666,478,891,-1000,1000,-1000,733,481,-1000,1000,294,-110,-191,477,-731,22,-871,-1000,1000,-1000,-324,94,-1000,1000,569,1000,1000,-637,-4,928,-936,820,518,927,1000,-1000,11,-237,310,1000,1000,-431,-477,-448,-809,971,-247,-654,-940,-1000,-106,3,-791,-844,1000,-921,845,-845,1000,-915,1000,891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(byte[]):com.fasterxml.jackson.databind.JsonNode",
            new int[]{127,112,-321,554,680,170,-1000,997,-1000,-1000,-840,-1000,1000,-477,-1000,1000,768,703,152,-471,-373,-316,417,-962,-1000,-282,600,936,-1000,620,-207,689,-700,988,250,482,582,472,-700,-1000,-1000,-115,-692,-471,992,59,-448,463,886,628,138,-868,-463,645,25,-959,-1000,-541,-427,480,-180,1000,-360,-652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.IntNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(byte[]):com.fasterxml.jackson.databind.JsonNode",
            new int[]{163,-1000,27,273,-1000,-445,498,-129,-134,-1000,651,1000,-1000,-1000,415,492,539,-698,-713,727,771,-229,129,789,-117,-691,-1000,365,-126,181,-1000,-381,287,-494,-513,-473,356,-694,699,-739,-1000,-579,-625,1000,975,678,918,-799,-984,-61,50,-1000,238,-869,684,14,-177,1000,-792,1000,-1000,457,-1000,964}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(com.fasterxml.jackson.core.JsonParser):com.fasterxml.jackson.core.TreeNode",
            new int[]{-213,1000,1000,-433,-1000,-332,158,191,-680,-1000,-146,860,1000,-947,6,-979,734,-1000,-1000,-1000,112,555,-297,-892,-1000,1000,-857,-856,1000,1000,1000,271,969,1000,-926,-1000,887,1000,1000,-576,1000,587,951,1000,-480,-436,21,1000,-277,-9,-765,-851,-840,268,223,297,-1000,-808,817,549,420,370,1000,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(com.fasterxml.jackson.core.JsonParser):com.fasterxml.jackson.core.TreeNode",
            new int[]{-25,171,-107,-327,-1000,773,-510,-34,-1000,-1000,856,118,1000,-781,1000,-1000,1000,-714,-1000,1000,1000,1000,-1000,-749,-1000,1000,-1000,-809,1000,1000,-1000,-572,35,1000,-811,-1000,1000,1000,552,1000,1000,1000,683,959,155,707,-779,-1000,16,1000,-1000,-704,-1000,174,1000,239,-1000,-1000,543,-179,139,14,1000,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(com.fasterxml.jackson.core.JsonParser):com.fasterxml.jackson.core.TreeNode",
            new int[]{-231,-1000,1000,-468,78,-1000,1000,619,1000,199,-996,1000,-1000,-1000,-729,1000,-1000,-758,1000,1000,-1000,-1000,370,-55,1000,-1000,-61,1000,-583,-1000,583,1000,548,-883,-933,-28,-16,142,639,-314,-42,-1000,743,-1000,-180,-460,1000,-402,-1000,-1000,-283,-774,539,514,-456,538,-44,883,1000,772,156,511,-1000,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(java.io.File):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-667,-32,622,-462,-78,352,1000,-1000,194,-833,-1000,-954,-1000,83,1000,-1000,1000,845,1000,-1000,1000,133,454,-943,76,-213,846,1000,1000,336,211,-322,-1000,-349,-303,-1000,-1000,1000,856,-395,1000,1000,-201,-42,1000,1000,-1000,505,906,902,-214,-78,-1000,-357,1000,-808,-907,-1000,28,806,-332,355,1000,-334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(java.io.File):com.fasterxml.jackson.databind.JsonNode",
            new int[]{1000,292,-251,-102,-403,-1000,-1000,129,1000,57,-86,-554,465,1000,0,0,-365,85,-1000,-458,-917,0,-144,-914,957,1000,640,62,26,811,-453,34,0,-1000,-1000,-929,1000,28,886,1000,361,-811,-1000,-425,-183,0,777,-1000,326,-654,802,622,-1000,0,-1000,102,-488,0,-1000,371,29,790,666,520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(java.io.InputStream):com.fasterxml.jackson.databind.JsonNode",
            new int[]{260,653,-146,-646,1000,-516,-1000,901,189,-422,8,-223,1000,782,953,785,-682,390,-1000,465,-13,-805,-553,-127,-217,-785,23,-1000,-1000,497,-594,1000,-636,929,571,-1000,-921,-641,-677,-126,-1000,627,1000,1000,-106,442,744,-1000,-312,899,-1000,760,364,646,342,-714,-164,-594,1000,-1000,296,-1000,-571,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(java.io.InputStream):com.fasterxml.jackson.databind.JsonNode",
            new int[]{947,76,-510,-211,854,-371,743,635,-932,543,-603,639,-729,-357,-284,805,513,402,379,614,701,834,442,-6,-60,-701,-911,-306,-322,-126,954,-994,-271,-401,137,969,410,237,-461,982,-719,910,-386,-878,-753,-576,257,-682,-812,997,-838,64,-351,-391,-306,-659,663,-720,-898,-968,291,168,-293,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonParseException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(java.io.Reader):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-1000,1000,1000,-441,-1000,1000,-1000,-880,-1000,-1000,-1000,1000,-175,1000,-1000,-29,203,181,-878,-449,999,-153,-35,-204,994,-1000,1000,1000,-845,1000,-103,-425,988,-415,1000,475,1000,-635,514,-1000,-1000,1000,-883,-774,91,-1000,-107,104,205,685,31,1000,383,-628,517,-1000,884,750,753,13,-1000,-431,662,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonParseException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(java.io.Reader):com.fasterxml.jackson.databind.JsonNode",
            new int[]{1000,-1000,497,-236,-810,-1000,560,1000,534,-1000,32,-1000,-1000,754,1000,-350,-135,135,-1000,-1000,789,1000,-281,-616,-1000,29,332,1000,-206,-146,-1000,150,-1000,1000,1000,-1000,-794,1000,-116,-1000,148,765,-1000,1000,-717,62,486,930,-1000,-1000,1000,155,1000,-267,-773,-1000,1000,-632,287,646,-610,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.IntNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{490,929,-15,187,-1000,-298,-850,-115,-511,-1000,526,260,101,1000,-892,-290,-1000,-604,973,-752,1000,-975,400,-349,497,154,-1000,856,391,-865,1000,-388,-1000,-335,214,-877,-218,237,-110,-1000,-488,-710,-226,-527,1000,870,888,-749,-899,1000,1000,-1000,871,786,1000,58,-918,-405,-981,-1000,-783,959,75,-518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-676,-96,-708,522,-1000,298,12,-1000,-1000,-997,-106,752,-1000,-371,60,-382,-524,-561,739,17,26,-1000,-400,-185,1000,-154,-1000,467,-994,-235,1000,-23,-620,1000,626,-996,-471,1000,-646,-297,-387,-1000,803,-154,806,-255,1000,-336,-712,434,833,-1000,1000,-468,175,1000,-1000,-503,-1000,1000,-632,968,-75,-674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.DoubleNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{255,-201,501,-334,-155,1000,133,517,-650,-1000,-1000,414,-969,-800,1000,671,-533,442,-3,-598,1000,-83,-461,23,299,242,-1000,-277,249,630,863,606,96,413,1000,-1000,-64,1000,-186,-71,279,-716,-223,34,1000,-58,606,539,-611,-498,-480,-1000,1000,-519,676,1000,-1000,-186,-1000,638,-1000,-704,-1000,-155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(java.net.URL):com.fasterxml.jackson.databind.JsonNode",
            new int[]{1000,-201,839,1000,-1000,646,20,1000,786,961,-1000,-476,565,1000,-603,-119,32,975,1000,1000,57,-1000,1000,2,1000,-1000,-164,-728,-1000,1000,-1000,-713,-546,-1000,-936,396,-1000,-1000,692,404,-1000,780,333,-245,-86,463,282,-684,1000,662,-977,304,-570,367,139,-544,-958,-1000,-275,-507,533,442,83,361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readTree(java.net.URL):com.fasterxml.jackson.databind.JsonNode",
            new int[]{442,-344,1000,597,-552,-1000,215,-620,410,917,-180,-219,143,1000,-619,-1000,-1000,1000,88,-663,238,-381,749,1000,123,-467,-447,108,-961,82,-568,-457,1000,180,-295,432,-1000,-1000,672,787,-782,965,1000,-250,100,-421,1000,175,910,-504,-1000,1000,1000,700,-443,752,50,-34,-1000,-86,-737,1000,-1000,-519}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-1000,386,747,379,-530,232,-1000,-134,-391,1000,402,-1000,1000,450,-1000,-1,-1000,1000,-915,-953,-43,1000,461,1000,30,1000,808,556,-487,343,53,554,1000,-265,438,-92,647,436,18,1000,573,112,1000,551,1000,-774,-1000,65,454,501,1000,-1000,-643,630,1000,-244,-767,1000,507,1000,-1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{1000,-51,-451,-475,-1000,921,804,1000,-948,-1000,-1000,-413,-392,1000,662,-1000,1000,-1000,488,-180,-1000,1000,1000,-175,247,-1000,628,-289,21,762,-483,1000,1000,133,1000,-468,706,-432,464,-594,991,-274,829,1000,-218,-160,-1000,109,1000,407,1000,-706,-1000,-1000,-190,455,706,734,-82,-671,1000,392,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-244,51,258,-848,1000,36,-1000,-1000,-568,-30,928,-383,-291,400,115,-883,-1000,684,171,410,-910,820,-446,-114,1000,548,632,-744,-176,-41,-586,-8,-171,1000,-238,196,-356,-275,709,-381,-1000,-1000,-725,-1000,55,277,508,-273,178,-1000,549,-65,317,-184,-227,916,-123,732,-125,349,769,2,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.databind.exc.MismatchedInputException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-335,-160,30,-462,-593,-988,-373,-575,589,-1000,1000,-268,505,-492,666,948,-383,-897,-682,-446,165,-224,1000,829,508,-102,753,693,-761,1000,-1000,-332,-804,284,513,-1000,-1000,1000,-1000,-1000,-530,-468,-701,-124,-951,-210,384,21,623,-263,-957,540,338,-990,-604,533,-243,81,140,243,296,1000,450,-635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonParseException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-679,1000,-171,-741,-623,860,-1000,-1000,1000,-1000,1000,-844,1000,-313,1000,1000,-391,-776,-1000,371,-1000,1000,537,679,1000,651,-789,1000,-380,-31,-1000,-554,703,291,1000,-218,-1000,1000,-336,-1000,-1000,-1000,-1000,1000,-1000,-18,1000,-1000,1000,-700,843,1000,101,-1000,-1000,1000,-1000,-60,310,1000,-462,277,-411,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.databind.exc.MismatchedInputException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-1000,283,521,-741,-989,743,-1000,-1000,969,-1000,1000,-836,774,-313,1000,990,-753,-768,-1000,-668,-951,754,451,808,1000,777,-1000,943,-1000,-422,-1000,501,-1000,-934,1000,-1000,-1000,1000,-336,-1000,-1000,-825,-1000,1000,-1000,-10,1000,-446,761,-476,-1000,586,955,-1000,-1000,1000,-1000,-556,404,446,-462,1000,-670,-251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],int,int,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{731,-1000,262,-926,1000,204,-1000,-1000,-978,-1000,-221,-1000,224,-851,-1000,430,-799,-1000,-576,-1000,-1000,142,-323,-411,333,1000,1000,-1000,-772,574,958,-1000,-91,-324,-1000,841,-1000,-596,1000,-504,427,553,-427,-323,-1000,939,1000,-709,883,971,1000,1000,418,-1000,865,-1000,-413,64,1000,1000,835,-399,812,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],int,int,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{67,-1000,-881,99,400,351,-292,-1000,1000,-1000,-136,-1000,-1000,-855,-334,24,-296,698,-1000,-1000,-1000,726,-440,1000,495,-739,365,896,1000,-106,763,780,-688,1000,851,33,542,-1000,1000,-148,-543,-1000,810,637,189,-560,-67,531,1000,934,1000,-818,452,-1000,-290,-400,34,744,1000,801,848,-764,734,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.databind.exc.MismatchedInputException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],int,int,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{748,71,-1000,-967,-1000,433,137,85,-206,1000,-815,422,-869,818,-359,-856,-78,1000,-1000,-14,-1000,-126,1000,1000,-725,-579,-414,-1000,-122,-370,-1000,-11,240,-975,-1000,1000,-1000,-753,150,103,546,-101,-19,-1000,-460,-1000,-487,892,-679,-135,659,-113,-352,747,1000,-39,-1000,79,-1000,-599,1000,155,887,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.databind.exc.MismatchedInputException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],int,int,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-782,-400,138,-927,-922,1000,-1000,-1000,343,961,-339,668,-907,1000,-1000,315,-93,721,-67,400,215,193,690,-1000,-1000,-18,236,-860,-642,-699,-1000,292,1000,-153,1000,1000,457,-933,269,636,-1000,-501,660,78,295,816,-72,564,-1000,509,-575,-1000,-537,399,624,1000,-475,-309,-404,-1000,851,-63,-734,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.databind.exc.MismatchedInputException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],int,int,java.lang.Class):java.lang.Object",
            new int[]{-1000,-124,633,509,-1000,-698,-931,757,-930,-1000,1000,-1000,1000,-842,191,-1000,-1000,-806,-953,584,-685,88,-9,1000,1000,-1000,-1000,622,1000,1000,-1000,1000,-1000,-623,-637,-843,288,919,-72,75,-917,-1000,-801,306,1000,-1000,-730,-471,1000,1000,-1000,394,-487,561,-790,-151,-1000,-1000,143,-437,1000,1000,-849,863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.databind.exc.MismatchedInputException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],int,int,java.lang.Class):java.lang.Object",
            new int[]{981,-189,-297,524,-921,-1000,1000,616,-593,-1000,1000,-1000,-80,-60,81,-306,-934,-692,-38,-251,-1000,-148,-168,561,218,-974,-1000,408,757,884,-849,132,-610,-696,195,-1000,1000,-1000,1000,-313,1000,-324,-1000,1000,197,-135,-606,784,1000,-146,436,-1000,-1000,-377,598,-839,945,-256,33,-473,1000,496,-509,-169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.databind.exc.MismatchedInputException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],int,int,java.lang.Class):java.lang.Object",
            new int[]{574,-466,-43,-431,-1000,-300,300,300,-1000,-586,757,-1000,237,-641,119,-293,-104,-1000,681,-281,-522,658,509,1000,-348,-1000,-1000,854,913,-132,-711,-406,-888,-442,-689,-924,162,-490,988,-662,863,287,466,1000,1000,-1000,-609,153,1000,-369,1000,-399,-931,-379,-79,51,-4,-573,239,-818,300,-300,-784,546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.databind.exc.MismatchedInputException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],java.lang.Class):java.lang.Object",
            new int[]{-120,1000,1000,499,-1000,856,1000,-1000,625,-710,702,-378,995,-885,-1000,380,101,-632,350,-204,623,-1000,1000,-174,-1000,645,1000,-400,-15,116,149,7,1000,633,850,684,-40,-493,237,156,-155,-440,178,-1000,788,-852,-629,387,-1000,-595,-483,-972,594,1000,-154,-700,-946,1000,-721,-744,-876,-63,298,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.databind.exc.MismatchedInputException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],java.lang.Class):java.lang.Object",
            new int[]{1000,-1000,929,303,-993,-619,404,-1000,156,1000,-1000,1000,5,-638,1000,-750,367,-555,-689,973,1000,-1000,-340,1000,-81,-263,-1000,283,140,336,-480,102,908,-778,-520,622,372,-1000,-1000,1000,1000,-1000,1000,-1000,938,-1000,-1000,452,-582,-609,-755,382,-1000,-169,1000,-300,734,2,-714,647,-687,1000,309,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonParseException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(byte[],java.lang.Class):java.lang.Object",
            new int[]{-324,414,314,154,-1000,491,1000,-597,-108,-232,79,665,-40,-1000,-161,-617,1000,-302,-298,-707,932,178,-718,-194,-1000,-52,328,-208,-255,-1000,348,172,-90,135,-125,343,787,400,242,747,-219,-324,-28,-248,1000,470,-978,326,400,-1000,-913,-1000,-47,-243,-24,576,719,-42,-1000,-400,-76,1000,-400,-482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.core.type.ResolvedType):java.lang.Object",
            new int[]{1000,-358,1000,124,715,-1000,752,-665,-1000,202,1000,509,146,-1000,-1000,918,1000,-1000,1000,1000,1000,1000,682,1000,193,675,-1000,1000,-44,1000,107,1000,-659,-1000,1000,-250,-746,-1000,1000,-271,-582,856,1000,471,1000,544,-1000,-1000,-1000,647,1000,-1000,148,-475,887,-587,101,-1000,1000,1000,-910,-776,230,-736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.core.type.ResolvedType):java.lang.Object",
            new int[]{-889,106,930,-282,-512,-400,1000,20,150,582,840,-1000,20,1000,1000,-988,1000,-1000,-593,-125,475,-160,465,915,-381,0,931,-216,-44,3,475,-269,-659,649,-205,-378,350,-1000,-60,907,-749,1000,613,925,-419,-451,905,-53,-365,-639,285,-922,641,-739,115,601,-1000,251,78,17,667,-493,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.core.type.ResolvedType):java.lang.Object",
            new int[]{-589,-1000,1000,554,200,532,706,-1000,334,991,1000,-165,19,-683,-818,1000,-808,-1000,-405,-128,1000,539,-749,669,39,-1000,253,1000,1000,-6,292,970,-815,-1000,-80,-685,834,371,774,240,-920,-194,1000,342,-1000,384,-975,-684,-517,-116,1000,-441,964,-65,765,1000,-687,598,-984,569,-851,-36,841,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{189,-20,-252,794,-1000,1000,-1000,496,924,-73,-1000,17,410,-1000,-427,1000,409,-705,26,400,-31,-926,-993,-795,1000,1000,1000,-246,515,421,-834,-85,-1000,255,1000,532,329,-1000,-355,-473,-656,-434,620,64,0,1000,-190,503,-607,1000,286,1000,941,1000,-147,1000,-296,229,-541,901,1000,467,-212,728}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{400,1000,-924,-502,77,1000,-1000,-272,-150,-1000,-1000,-1000,1000,111,1000,962,-855,-1000,13,-197,1000,518,-704,190,1000,-149,-63,-1000,1000,-560,141,-944,-1000,255,255,230,511,-1000,-1000,694,1000,1000,-1000,897,-754,-279,1000,190,1000,1000,-1000,1000,1000,719,-1000,-398,553,1000,157,1000,122,880,-451,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{127,1000,165,304,-24,-1000,-748,232,193,925,590,-1000,92,651,-140,-842,-1000,484,1000,-29,-629,1000,-1000,-1000,84,235,-727,-297,-323,322,650,59,-1000,-63,-114,-263,-772,318,-86,-922,-85,127,-596,-88,234,-1000,1000,642,855,-1000,251,-1000,-383,-51,-887,-978,825,3,344,1000,624,338,44,327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "readValue(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-353,1000,223,588,374,-1000,-613,-24,1000,1000,1000,-818,544,1000,417,443,-1000,-863,1000,696,292,1000,-1000,-1000,-737,-935,471,-903,-771,788,461,-52,-1000,-912,-1000,-637,-1000,37,-167,872,147,967,505,964,765,-1000,735,1000,562,-1000,724,-1000,-1000,328,-1000,-1000,1000,-907,-1000,1000,1000,1000,545,1000}));
    }
}
