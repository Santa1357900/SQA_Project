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
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.StdDateFormat", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "clone():com.fasterxml.jackson.databind.util.StdDateFormat",
            new int[]{-848,689,-394,-496,308,427,251,-672,403,-686,-401,-529,1000,-367,1000,-126,-740,859,-14,304,306,-687,177,441,-298,-387,1000,416,627,621,-1000,-479,-444,56,286,918,1000,238,-488,-251,621,-40,-599,234,502,562,212,-71,-920,-940,-75,278,712,1000,782,100,-27,-593,-229,-287,701,23,196,247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "equals(java.lang.Object):boolean",
            new int[]{923,-519,106,424,460,-290,651,-372,-840,-684,858,109,918,105,901,993,-199,895,178,-507,376,865,294,93,650,-442,-365,567,952,1,216,673,-332,-873,31,254,642,228,-117,178,-729,-475,847,45,846,-912,892,940,-474,-646,995,-391,-508,247,81,410,-724,515,525,-863,50,344,-375,402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "format(java.util.Date,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{-532,-357,-520,-105,15,-781,191,-369,1000,-680,1000,331,282,-1000,358,-988,-516,-561,-1000,-75,-1000,-1000,-1000,-275,1000,-986,-413,-1000,1000,-284,910,-1000,-226,132,-633,-819,1000,-632,-307,-99,-118,488,445,-381,961,910,761,306,-946,1000,-1000,914,-121,1000,-1000,73,-859,-775,725,-714,138,1000,307,667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "format(java.util.Date,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{336,-337,-370,215,99,1000,77,1000,1000,-405,671,-183,748,-672,94,-824,917,-1000,-707,-581,-97,159,-361,808,1000,-135,795,-453,-109,-1000,941,-990,-271,1000,94,-343,55,431,-415,495,392,-261,1000,-117,1000,-1000,-277,740,-1000,674,663,1000,58,1000,-40,901,247,-1000,1000,-1000,813,937,-707,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "format(java.util.Date,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{-457,-717,216,-771,-711,93,-683,-811,826,-28,-825,-820,324,-117,-376,878,104,717,-138,514,766,-291,556,-653,243,915,-612,-952,77,878,196,593,720,942,-809,650,-745,590,61,344,558,-367,-782,-163,644,-855,928,192,693,-400,-393,-384,104,-163,-830,389,774,219,-978,-479,819,-399,982,485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:sun.util.calendar.ZoneInfo", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "getDefaultTimeZone():java.util.TimeZone",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:java.text.SimpleDateFormat", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "getISO8601Format(java.util.TimeZone,java.util.Locale):java.text.DateFormat",
            new int[]{88,-953,-116,-589,-233,643,15,947,151,352,-659,894,100,214,-430,434,86,-876,-497,-54,762,390,447,406,-543,-667,136,-956,-84,613,439,228,436,626,-901,-429,234,-770,593,626,-658,-704,-142,-859,-373,157,-978,-157,-356,-127,-395,976,-82,-568,654,422,21,-510,-759,110,312,-940,-628,662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:java.text.SimpleDateFormat", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "getRFC1123Format(java.util.TimeZone,java.util.Locale):java.text.DateFormat",
            new int[]{-27,-3,-169,838,161,953,120,639,-519,-603,137,464,-557,509,316,-560,449,769,575,638,483,693,-519,738,608,-235,366,524,-66,288,147,767,-779,-981,-973,374,-866,448,-999,-159,326,-656,162,962,-35,844,141,-477,-888,-284,-270,-706,-553,709,706,983,224,908,199,-946,-921,888,-456,284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "getTimeZone():java.util.TimeZone",
            new int[]{1000,65,1000,-1000,-139,673,-150,-1000,-682,-2,1000,-256,461,-186,197,1000,458,-227,-1000,-716,-115,-527,-238,-779,-1000,656,-444,-1000,337,374,514,-958,-403,-349,651,-248,557,-592,-836,1000,564,-491,-266,203,513,-1000,395,713,350,83,1000,-1000,1000,492,-795,-101,906,-62,775,-90,90,32,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "isColonIncludedInTimeZone():boolean",
            new int[]{95,-495,-647,249,-548,-610,-753,-1000,1000,-452,1000,-841,449,-424,-877,-627,-154,496,193,-216,-1000,482,588,-1000,-1000,-118,-341,-645,160,-1000,-903,-201,-467,-1000,1000,10,535,-158,535,-1000,290,1000,-1000,272,1000,-1000,-1000,88,93,738,-543,1000,324,-1000,-298,-392,-699,115,1000,-1000,594,1000,660,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "isLenient():boolean",
            new int[]{898,797,-743,-820,-905,719,-630,-111,-339,363,224,-617,65,-492,397,554,814,-697,517,888,312,-764,-967,901,877,207,209,681,-982,-887,-176,-488,-151,-814,-877,32,-613,824,741,-714,-591,-403,-512,609,-468,-283,187,351,205,370,56,123,462,995,540,459,-614,-137,944,-429,563,100,-267,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "isLenient():boolean",
            new int[]{548,768,74,-267,-176,177,-874,-820,633,-649,-236,303,-623,-826,-233,1000,375,149,464,-16,-680,83,-715,294,39,-238,381,-605,-401,-228,267,419,858,-1000,-1000,249,-334,-345,-119,-116,-157,61,900,850,680,-700,1000,-317,822,383,-736,521,205,-592,208,163,-443,249,-911,164,-1000,179,310,49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "isLenient():boolean",
            new int[]{-195,-703,144,401,463,606,151,72,-253,613,-896,29,168,-411,-409,919,-690,-534,-190,-800,-783,-31,996,174,-920,-597,-902,175,348,775,715,700,-841,-211,-629,685,-100,713,382,-910,-425,-895,735,505,-312,953,695,-437,361,-92,173,-375,560,-10,-715,-581,429,381,987,78,-995,-247,184,396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.text.ParseException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String):java.util.Date",
            new int[]{-1000,261,-1000,1000,-196,1000,-559,1000,-526,-1000,1000,1000,-683,999,-492,-68,-335,887,-1000,1000,468,208,616,-1000,-231,172,-1000,1000,-115,1000,1000,-1000,-1000,-1000,-551,-1000,-1000,320,-230,212,-1000,1000,-834,598,1000,-52,-446,1000,-11,-1000,-1000,-1000,789,-162,741,-1000,1000,1000,-223,-612,542,-212,740,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String):java.util.Date",
            new int[]{-626,-148,523,269,-194,-1000,401,744,458,434,-567,456,415,-413,-879,-341,1000,294,-432,207,-41,-214,983,-642,-753,279,-402,559,-1000,-888,790,1000,299,-828,779,943,-10,126,-1,-755,311,213,267,-481,-178,822,-401,-789,-434,165,325,638,-959,-159,-628,-736,-1000,220,311,-898,-658,271,3,961}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.text.ParseException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String):java.util.Date",
            new int[]{726,-1000,795,-171,712,-1000,658,-449,180,864,-1000,-1000,374,-916,-206,-1000,515,-1000,920,-1000,-504,140,33,1000,-1000,1000,-319,528,-221,-733,-539,811,1000,1000,544,446,377,562,669,-713,1000,-1000,-69,115,-32,1000,952,-1000,322,-327,-402,984,-726,746,-557,814,-831,-731,24,178,-1000,688,-629,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.text.ParseException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String):java.util.Date",
            new int[]{261,314,-161,-63,41,945,-365,-40,-404,-634,920,503,-603,504,-669,782,-227,807,-736,801,743,-734,931,-867,-121,-576,462,-99,-465,-508,504,566,-382,-580,26,522,176,-735,-494,-350,-482,518,-425,-30,328,-807,-700,297,271,430,763,-548,-1000,-877,-617,-51,105,-358,491,-655,570,128,93,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.text.ParseException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String):java.util.Date",
            new int[]{249,582,154,-1000,577,873,730,529,496,276,72,82,-444,1000,459,1000,-264,265,-1000,-490,1000,-1000,1000,-680,-385,-1000,1000,-726,-1000,-676,493,856,-691,-167,810,326,1000,944,1000,-1000,778,972,777,-695,102,-997,-970,1000,679,645,1000,175,-1000,-314,-1000,-968,978,-803,-328,-1000,565,-184,673,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String):java.util.Date",
            new int[]{-917,-249,98,-403,-880,514,-918,-1000,-719,-1000,1000,691,903,-763,-571,-1000,564,-1000,251,174,-351,118,-323,545,-329,852,-60,-639,-1000,-371,-330,-437,1000,1000,427,-1000,-1000,-879,809,1000,-1000,728,-1000,-578,320,-1000,89,-61,-86,-1000,-90,562,-354,665,636,390,-97,688,1000,1000,168,488,184,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String,java.text.ParsePosition):java.util.Date",
            new int[]{919,77,-176,-78,-417,-423,1000,854,1000,641,-298,-284,33,665,-1000,1000,964,-514,1000,1000,-786,602,1000,-1000,-1000,1000,-1000,53,443,1000,1000,482,456,227,1000,-853,-148,-900,42,1000,1000,1000,1000,-808,-1000,-1000,-623,1000,512,-478,-1000,1000,-417,-981,524,-197,-123,-617,-364,1000,207,448,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String,java.text.ParsePosition):java.util.Date",
            new int[]{164,190,-676,221,304,-880,-179,608,-678,707,-798,-517,598,-427,-302,-501,650,53,-390,607,-690,268,921,162,652,937,-846,742,624,740,898,-504,-560,-216,-955,362,386,569,927,-890,-89,-242,257,849,594,-34,84,367,918,857,787,-531,-345,870,564,339,94,-927,-159,-109,351,815,-460,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String,java.text.ParsePosition):java.util.Date",
            new int[]{1000,1000,-1000,-930,19,-902,-251,1000,1000,536,-798,653,224,-394,-1000,-862,618,273,-1000,607,1000,-478,1000,283,94,1000,-917,517,558,1000,1000,-111,528,503,-1000,553,-1000,-1000,1000,-248,1000,1000,-1000,-800,92,-1000,608,398,-1000,-320,844,433,-1000,-58,-809,-202,580,-564,494,1000,-219,527,-535,-941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String,java.text.ParsePosition):java.util.Date",
            new int[]{1000,293,-437,-404,-375,389,931,1000,1000,755,-261,-951,-159,663,-1000,-59,183,-446,377,1000,-889,277,1000,-307,-884,1000,-1000,-68,443,1000,1000,-322,695,298,-211,292,-978,-1000,355,1000,31,1000,918,-1000,-364,-1000,-330,1000,-321,-735,-355,1000,-1000,-1000,190,-109,1000,-1000,-200,1000,351,739,749,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String,java.text.ParsePosition):java.util.Date",
            new int[]{952,-373,-771,181,-990,652,-146,-256,579,244,385,-75,-263,297,-782,684,-681,319,-697,770,-678,-500,-316,-326,498,698,-1000,-411,-661,-1000,869,106,459,79,-1000,-716,-116,-236,-1000,638,1000,115,39,-734,-279,-695,1000,705,-1000,-1000,479,-737,-450,519,-700,-565,-659,-129,-50,980,-855,-1000,1000,290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "parse(java.lang.String,java.text.ParsePosition):java.util.Date",
            new int[]{1000,968,-152,-519,56,-712,-315,1000,867,293,-324,-346,-506,112,-875,458,1000,-657,111,432,-799,338,1000,-344,197,972,-886,478,406,478,1000,441,558,734,811,51,-887,-1000,251,1000,1000,1000,53,-1000,-956,-1000,184,857,-413,-365,-80,509,-764,-1000,-150,-404,-684,-989,196,1000,-100,508,242,646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID|isLenient=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "setLenient(boolean):void",
            new int[]{-163,373,-526,1,137,-826,246,-215,89,-963,-672,967,882,-942,723,-91,-174,710,440,797,891,696,-721,629,375,543,-458,-462,-842,-504,298,228,828,315,-186,-206,-472,-46,-517,936,834,782,-803,-297,-837,992,-546,-740,276,-832,713,-66,-724,-423,-477,775,-806,-291,-255,-770,703,-199,-141,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("VOID|isLenient=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "setLenient(boolean):void",
            new int[]{-1000,393,172,192,102,334,732,60,474,-694,628,329,353,238,-307,-199,-255,1000,327,-491,166,1000,241,390,830,1000,1000,-370,-857,-290,327,-15,367,-789,363,-360,-790,-858,-564,45,706,-1000,1000,450,-619,-295,-1000,-734,225,-515,1000,-432,346,527,-1000,357,1000,1000,-596,-353,988,-564,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "setTimeZone(java.util.TimeZone):void",
            new int[]{226,-53,-141,-640,885,-888,-48,-799,-492,585,1000,-645,-648,670,340,669,34,-33,1000,437,-429,-454,905,517,212,188,235,-666,-63,-860,213,-863,903,-496,1000,818,743,615,-662,-182,-516,-411,779,-529,-962,347,1000,64,-748,1000,-974,278,-575,-970,-147,1000,95,263,-1000,-575,-506,71,-346,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:W29uZSBvZjogJ3l5eXktTU0tZGQnVCdISDptbTpzcy5TU1NaJywgJ0VFRSwgZGQgTU1NIHl5eXkgSEg6bW06c3Mgenp6JyAoc3RyaWN0KV0=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "toPattern():java.lang.String",
            new int[]{-767,43,-1000,235,-823,-1000,-297,193,695,-596,-281,286,75,319,241,23,800,1000,361,447,-12,-1000,-538,39,-1000,1000,177,-887,677,-279,505,-1000,436,-195,-622,-494,180,172,-185,-346,168,-340,164,-509,-1000,-612,-979,38,-1000,1000,445,502,598,1000,-997,250,876,150,-1000,-1000,490,-757,-287,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.String:W29uZSBvZjogJ3l5eXktTU0tZGQnVCdISDptbTpzcy5TU1NaJywgJ0VFRSwgZGQgTU1NIHl5eXkgSEg6bW06c3Mgenp6JyAobGVuaWVudCld", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "toPattern():java.lang.String",
            new int[]{-1000,169,-26,74,-224,398,-479,-152,121,-47,-487,-158,177,-542,-256,402,915,550,-479,-95,-1000,286,-438,-204,-487,397,751,474,-26,191,401,-1000,317,-5,-870,-1000,-1000,-822,929,339,-388,-1000,754,-32,-1000,-328,22,812,20,735,-608,-199,-207,-755,329,-76,757,322,1000,-1000,302,-367,73,-535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:RGF0ZUZvcm1hdCBjb20uZmFzdGVyeG1sLmphY2tzb24uZGF0YWJpbmQudXRpbC5TdGREYXRlRm9ybWF0OiAodGltZXpvbmU6IG51bGwsIGxvY2FsZTogMHgxY2MsIGxlbmllbnQ6IHRydWUp", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "toString():java.lang.String",
            new int[]{-806,-783,684,231,460,-1000,-782,812,21,438,-39,1000,188,-729,436,253,726,636,133,-281,-1000,-499,-1000,-182,515,544,-456,409,-183,254,-668,-675,415,4,-496,1000,437,966,-1000,-48,110,268,-327,602,8,-474,291,-512,-1000,8,-634,-458,782,-456,-67,107,358,-434,1000,-410,819,-949,-596,826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.StdDateFormat", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "withColonInTimeZone(boolean):com.fasterxml.jackson.databind.util.StdDateFormat",
            new int[]{406,-943,76,-836,-404,-231,-132,-395,786,-783,953,-476,-477,-235,-413,521,207,-787,978,-584,529,161,-407,652,-668,284,-34,-41,216,-531,645,546,-78,-850,-908,243,769,626,-838,260,253,669,-879,-472,256,323,-924,950,-699,406,153,-690,-257,958,-196,-919,-109,-355,210,-97,653,-57,-94,154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.StdDateFormat", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "withColonInTimeZone(boolean):com.fasterxml.jackson.databind.util.StdDateFormat",
            new int[]{1000,859,874,-266,1000,46,-690,-879,-1000,654,-646,285,-1000,1000,-927,-804,620,-95,-663,-638,746,-571,-28,1000,462,530,281,724,-430,-1000,566,993,1000,1000,784,-1000,348,-118,976,-1000,-788,248,-447,-207,-547,-321,-13,615,51,-67,986,1000,-670,-332,668,-418,1000,-721,-587,362,194,170,508,-611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.StdDateFormat", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "withLenient(java.lang.Boolean):com.fasterxml.jackson.databind.util.StdDateFormat",
            new int[]{79,-497,-1000,-528,73,383,192,691,1000,528,-241,1000,-907,585,-54,1000,701,438,803,-569,-1000,-386,-832,-515,882,61,1000,527,833,-102,-1000,-950,774,-265,-140,-267,823,108,-1000,1000,-100,805,-868,364,-1000,1000,107,556,616,882,-580,-476,644,402,-1000,-654,1000,220,-943,350,-895,853,784,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.StdDateFormat", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "withLenient(java.lang.Boolean):com.fasterxml.jackson.databind.util.StdDateFormat",
            new int[]{276,89,-495,-1000,383,1000,-240,184,717,69,219,-240,677,-917,303,546,-444,-808,862,-993,708,-1000,-335,-822,-271,333,1000,-1000,948,-979,-311,172,3,23,-656,227,1000,1000,-1000,441,1000,864,843,283,-1000,811,-582,-324,-693,163,222,-350,-627,-217,813,176,1000,220,-947,830,652,-313,-742,-668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.StdDateFormat", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "withLocale(java.util.Locale):com.fasterxml.jackson.databind.util.StdDateFormat",
            new int[]{-829,419,-474,-172,-375,620,312,605,924,281,494,433,-623,-392,218,699,-596,212,443,-883,-320,-591,-700,903,146,747,24,-880,389,335,-219,894,-540,-83,-551,-982,-337,325,-988,-490,26,926,-420,-870,-759,-5,-741,537,697,-443,133,825,-95,183,-627,-514,-917,460,-791,527,972,406,-134,398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.StdDateFormat", DEReplay.run(
            "com.fasterxml.jackson.databind.util.StdDateFormat", "com.fasterxml.jackson.databind.util.StdDateFormat", "withTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.util.StdDateFormat",
            new int[]{241,95,-752,731,292,-365,863,126,251,42,455,-57,-916,397,-795,193,-386,984,-147,-580,889,-282,-645,-710,989,-832,-311,-451,-266,-504,265,-790,-832,-684,-57,930,938,467,-928,953,-288,2,149,669,638,-683,-292,-86,520,274,-134,422,854,-583,-394,533,171,-69,-708,-792,-790,872,-787,662}));
    }
}
