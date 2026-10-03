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
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{832,376,100,39,139,-58,-1000,-111,51,576,163,-283,-690,333,-91,-321,595,-704,-410,-623,-387,-530,1000,-502,1000,1000,-1000,565,1000,-1000,-772,-1000,-309,906,551,633,-649,38,-60,-1000,-16,968,-1000,-1000,-1000,-618,1000,1000,-1000,237,-1000,39,-78,-867,-648,-398,-939,-107,-215,1000,517,-1000,762,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-339,321,-36,499,-205,-709,321,-972,-406,-158,-789,597,783,436,-236,-463,-852,-152,1000,26,-265,-187,-215,-272,-693,186,1000,-72,197,-152,1000,-5,289,297,-1000,-512,-743,-1000,441,629,14,512,475,-1000,-61,-374,-1000,434,433,907,-503,633,1000,-341,-222,-358,-201,-824,88,311,-217,-445,-1000,338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-581,1000,-244,499,-162,567,419,1000,-807,-86,870,954,1000,1000,-1000,-56,-436,-1000,1000,1000,-1000,-1000,-189,275,-923,1000,1000,404,230,-693,1000,-1000,-95,-507,-1000,-1000,-1000,-702,405,-107,-241,488,-181,-1000,-1000,-908,548,-1000,-769,907,256,-917,1000,-1000,-146,-1000,-23,-590,-367,373,-452,-305,-1000,-825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-665,290,305,-261,-153,-697,-708,610,-677,-17,660,486,117,1000,-961,936,466,20,1000,783,-740,-1000,187,-580,9,1000,-20,-210,675,-977,159,-1000,-620,-676,-616,-213,-1000,273,74,-458,-157,664,-343,-1000,-472,-1000,-1000,-862,-1000,684,-106,-649,58,-900,915,-1000,404,176,-337,217,-76,119,-234,-363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{189,-566,813,-521,612,-347,290,764,-101,705,120,871,-210,390,424,246,-307,1000,765,180,-342,546,1000,-1000,398,-265,-410,-1000,-1000,-806,471,-825,35,1000,-530,-86,216,38,520,-224,-1000,559,83,-864,-1000,-379,-692,178,-948,-308,-1000,427,-445,94,227,49,-887,-929,302,413,128,-1000,-108,832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-813,-32,-297,-91,1000,-989,507,158,-1000,377,1000,514,183,613,364,627,-329,211,1000,-351,763,90,245,-272,-486,75,1000,-662,-327,913,1000,223,668,-334,-202,-265,-244,-815,610,791,709,293,748,-344,265,659,-1000,-901,747,-289,492,258,697,-270,-719,-1000,640,-1000,810,354,-265,-593,120,632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{1000,497,1000,-481,731,-563,-278,-212,311,919,1000,360,-1000,19,-759,-255,743,1000,-125,-1000,-761,965,1000,77,-1000,560,-887,1000,500,-1000,-366,-438,-1000,1000,-367,93,-1000,-164,1000,-946,-541,32,148,-1000,-152,-609,-1000,-163,-404,155,-819,1000,193,198,-627,870,-888,-291,-1000,-83,-48,32,262,-768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{719,-1000,-1000,-23,864,532,-691,-1000,258,-685,-936,1000,-524,-595,-352,1000,1000,626,-925,-1000,1000,-764,-661,765,-476,101,-1000,-1000,657,1000,-1000,1000,1000,-1000,1000,1000,986,-406,-1000,-606,1000,-725,-234,527,1000,-349,-1000,494,1000,-1000,126,112,-1000,1000,1000,-397,444,-939,1000,-24,-218,840,712,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-460,271,-518,-281,761,639,864,-152,-300,-1000,361,472,144,44,1000,478,-100,1000,382,-342,-191,-119,-1000,-496,-101,-1000,-924,-1000,-1000,1000,554,1000,-166,-75,-97,-1000,831,404,243,-211,-168,-1000,-866,377,-105,-1000,-668,-1000,-962,423,1000,-881,-345,1000,-380,-1000,241,842,475,561,490,249,669,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{957,906,534,412,-422,470,-706,-983,-285,-101,819,658,934,497,963,-411,-645,-431,-694,-957,1000,-201,406,1000,512,880,1000,798,-203,-1000,-884,-883,-229,-1000,1000,624,-1000,380,252,840,-940,1000,89,-756,1000,-982,-57,-909,-1000,-1000,-400,1000,1000,566,392,-558,-546,-391,-431,1000,-842,935,-381,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{53,-569,778,494,650,879,274,-270,-1000,1000,973,41,1000,-1000,615,484,187,-447,966,-103,-1000,1000,-759,-552,1000,-22,1000,1000,-551,516,1000,1000,1000,-1000,103,662,1000,-146,-305,-286,1000,-1000,309,1000,-71,-730,-1000,-695,516,1000,55,-1000,-621,224,-400,-764,34,-729,-804,1000,1000,377,-354,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-153,19,772,694,-162,912,13,-626,-1000,342,1000,-952,1000,-275,-898,1000,-1000,-1000,992,-60,55,1000,-522,1000,646,140,413,622,-392,-800,-279,-486,258,-1000,-967,856,256,865,418,312,3,327,1000,1000,762,-583,-1000,-785,1000,135,-1000,-406,-207,1000,-1000,-826,-824,-691,410,921,562,222,-116,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{1000,636,179,321,448,-364,302,466,-210,-140,-467,1000,481,64,480,-740,912,-190,1000,-300,1000,-737,1000,415,-164,-932,332,467,164,-510,338,123,-726,-914,781,-524,-400,-273,921,-245,-1000,551,-152,459,398,746,-254,-59,-137,682,171,-533,-368,37,87,-237,-340,201,310,39,97,968,-547,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{1000,-314,371,328,939,945,651,-408,-655,-400,730,-286,1000,-140,-444,1000,414,525,367,307,-259,917,274,792,1000,723,-340,1000,864,-1000,-1000,-186,674,-843,978,1000,1000,432,-641,-538,374,-22,1000,1000,-818,-524,-350,157,625,204,1000,-243,-1000,-108,-408,495,298,-204,171,569,-634,332,-516,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{217,80,310,384,57,1000,611,237,128,-64,-710,425,-5,1000,-719,-202,-221,637,-582,865,1000,1000,-232,1000,1000,-490,-444,426,64,-1000,-884,-1000,-328,-219,718,309,378,-216,888,310,-1000,34,1000,-313,559,-1000,160,-644,39,-943,-538,1000,416,762,65,-33,967,1000,266,1000,-1000,551,-27,716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{898,-314,-105,332,939,469,153,103,-1000,221,-224,12,1000,-159,241,715,1000,396,252,307,-1000,756,40,471,49,335,815,1000,249,-223,176,475,287,-480,1000,670,958,-160,-299,-188,661,-559,-6,1000,-755,-1000,-494,246,-263,992,677,-903,-320,-739,-502,0,-660,-741,176,-6,14,527,-726,-719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-500,-1000,1000,697,-607,89,-550,-1000,-855,-526,782,-194,1000,-1000,477,836,-807,-1000,-350,-703,-389,517,-232,-392,1000,579,854,719,-428,-767,-1000,-466,1000,-843,-154,1000,-764,-637,-824,41,760,1000,363,907,905,-549,-760,-788,782,-1000,-854,-1000,605,855,-370,-378,-192,-1000,-984,1000,-477,-194,-57,-730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{364,886,-1000,-86,195,-936,109,-803,110,1000,-211,1000,546,-1000,1000,-357,417,110,-230,-103,-430,1000,779,-1000,930,148,312,340,165,970,-1000,189,665,1000,107,549,-71,-634,953,-1000,87,1000,-192,1000,738,-78,268,612,-620,133,-173,129,128,-1000,-498,-637,-1000,-598,617,-515,-642,-673,-143,-907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{634,-945,804,-738,-91,-396,-317,426,723,34,377,709,228,-251,-129,257,1000,836,1000,399,582,-275,254,-866,803,573,1000,-705,-303,-831,512,-1000,775,375,-568,1000,-1000,-357,-981,-297,609,-881,1000,-119,902,-841,-1000,72,-551,-490,-187,492,125,-1000,456,-164,276,795,-695,706,-786,-1000,1000,699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{476,-1000,543,-356,-224,-147,-127,448,-266,-145,222,334,-159,-246,-145,-1000,392,178,627,368,1000,-281,555,-658,448,631,731,332,-551,434,1000,-370,-191,389,-48,1000,-158,424,-1000,306,441,-484,746,174,380,-568,-682,-947,210,-223,78,36,-158,-83,-94,610,355,148,-915,578,614,-1000,-110,481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-43,569,-509,-471,-369,201,966,-366,385,983,864,-1000,119,-985,1000,255,559,929,537,11,-642,1000,82,-732,277,411,340,-282,1000,1000,-1000,706,-202,259,-219,929,-960,-1000,748,200,-865,889,-1000,-58,956,-676,748,549,1000,-660,294,1000,-906,-51,-276,-352,-1000,-1000,-1000,-969,1000,-906,337,-632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,199,349,-766,-164,-676,171,-65,-422,158,-276,1000,255,-764,48,-1000,215,22,-354,97,1000,758,1000,-1000,1000,841,835,710,-710,271,1000,-327,710,1000,-627,318,-306,601,-1000,-1000,708,-135,1000,956,709,-195,-1000,-1000,-1000,474,-270,-22,-20,-10,-1000,1000,807,-179,-424,1000,-1000,-1000,247,343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-222,-793,348,169,602,662,688,160,39,-456,265,-662,-31,329,-232,-725,-275,-1000,-160,-626,500,-425,-343,716,-177,-232,55,-692,-22,-115,283,45,-1000,421,345,-312,805,346,98,85,496,-714,-943,-918,53,416,707,1000,288,125,-831,-988,272,157,310,908,-1000,463,690,-700,416,306,-734,374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{822,-694,69,-656,191,-386,1000,-268,-883,576,764,1000,472,-655,466,-1000,79,-924,-661,-333,513,984,1000,-619,1000,732,832,612,319,517,-100,-418,304,459,-418,1000,-16,-693,-1000,-411,536,-199,-331,20,1000,-476,-656,-334,-312,-849,-939,-979,-262,-689,-1000,-262,-866,-744,-914,616,-1000,-1000,615,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,502,-866,-46,-1000,184,929,-508,-687,1000,1000,-65,-854,-1000,98,-1000,188,542,-31,-1000,-249,970,1000,-1000,259,-67,-455,1000,691,1000,-1000,1000,-69,986,-656,1000,-275,-890,-97,-164,-428,1000,-1000,712,712,-1000,30,305,780,-1000,-5,761,-1000,-1000,-893,-67,-672,-175,-213,216,57,-797,-152,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{84,1000,-529,-11,89,253,1000,-801,-1000,-1000,42,-1000,-450,-123,-697,153,-1000,-696,1000,453,-615,533,1000,706,-5,1000,573,-949,-389,-406,-1000,1000,-1000,1000,-474,-1000,467,1000,-150,-134,1000,157,-634,991,-33,115,-1000,38,216,690,159,-611,672,-106,182,1000,-1000,-540,78,1000,-206,-565,523,-134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-18,-221,-526,295,844,1000,-1000,-1000,-1000,418,-1000,-367,397,-1000,370,-136,-725,1000,-972,-749,816,1000,722,833,1000,-801,-723,-1000,-252,-1000,1000,-568,843,-1000,-1000,586,528,-920,443,1000,91,-1000,1000,540,496,-1000,1000,502,1000,776,-1000,1000,1000,817,1000,-1000,669,536,1000,407,-387,-304,-664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{241,34,400,-11,-817,863,-1000,144,-167,-1000,-830,1000,438,-1000,630,553,842,-586,-1000,1000,-615,289,-789,706,-504,1000,802,614,558,852,-1000,621,-1000,-393,614,400,1000,962,247,406,-270,157,294,-1000,664,6,-782,-601,-815,716,-1000,1000,672,-344,-11,-148,147,-1000,508,1000,-206,-725,-375,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-560,-1000,114,302,-190,-430,122,-1000,196,1000,709,-1000,300,588,-58,-1000,1000,174,-464,815,-1000,414,339,-1000,-1000,1000,158,1000,269,-1000,1000,-425,1000,1000,-1000,-614,-176,-1000,812,-1000,155,-1000,-16,1000,259,-423,-808,-588,802,-1000,-1000,780,-1000,1000,-648,932,-875,-963,709,442,-637,-1000,-421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,553,1000,-341,-22,541,1000,936,231,363,630,1000,477,-1000,-1000,827,40,744,-1000,73,-533,98,-479,-24,-768,1000,39,-1000,-542,-1000,-242,757,-1000,1000,-374,-711,922,-287,16,-396,72,110,1000,1000,-561,-337,271,-1000,-7,-164,1000,104,-9,-631,400,39,-1000,-1000,-1000,-284,130,570,-258,-474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,324,-556,805,173,896,-981,-1000,-529,409,-2,-254,305,125,923,-1000,-668,1000,607,-573,346,1000,388,883,1000,831,-628,-1000,-1000,-801,1000,-1000,1000,-1000,-1000,589,333,-591,142,928,625,-773,1000,673,-293,982,791,445,1000,-229,35,721,484,167,1000,-1000,43,212,418,804,-697,-26,-225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,1000,389,68,41,866,-2,849,297,-1000,-782,30,213,-1000,-539,-205,-1000,506,-1000,-1000,1000,149,748,1000,625,-1000,-273,-1000,8,-1000,577,1000,-1000,-1000,980,7,1000,1000,793,1000,510,1000,-636,-951,19,-312,1000,275,-996,545,317,-446,1000,1000,616,-845,835,1000,-343,-1000,618,1000,74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-241,1000,784,-146,-281,-135,-1000,1000,-163,-61,-900,13,644,240,-1000,848,1000,-1000,-508,1000,-532,180,-881,-532,-375,958,401,842,-1000,893,531,983,-1000,-890,-421,1000,819,880,567,597,1000,338,653,-1000,-256,-226,-787,-937,-830,783,-1000,-431,771,-692,-1000,-277,639,-1000,679,502,-35,-132,-683,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,553,1000,-911,857,-763,-775,1000,1000,964,-1000,23,-1000,-477,-1000,-594,193,-1000,-349,307,-1000,1000,97,1000,-217,1000,4,-992,-1000,-59,-1000,1000,303,-1000,-1000,989,773,1000,1000,927,1000,886,483,1,-424,694,951,-1000,810,399,840,530,-1000,1000,-1000,1000,1000,-594,1000,-846,-581,1000,-619,560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{60,754,-24,404,24,747,614,-56,-664,-914,481,1000,-383,208,-194,1000,-364,-1000,-200,461,-1000,534,-102,-1000,664,-1000,-223,458,-490,-934,520,683,1000,695,590,29,-15,605,-1000,190,-682,-810,-138,-429,108,2,331,-442,-1000,340,911,-437,-589,1000,329,-780,-716,-266,791,-228,-469,-1000,22,588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{-266,576,284,-627,1000,-799,603,72,-155,558,396,1000,8,-86,-473,1000,-818,-903,1000,1000,-1000,674,-678,517,686,-361,72,373,-610,-1000,664,348,-640,320,-78,163,-9,1000,-418,-1000,-750,-867,-1000,-1000,-273,471,53,-1000,-1000,316,836,-587,-201,1000,444,-510,-837,-903,1000,-556,563,-52,310,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{1000,1000,-1000,119,-814,-186,1000,671,-970,1000,1000,-687,896,1000,354,1000,-117,-1000,581,-86,246,1000,1000,-92,571,-136,-1000,1000,-1000,-1000,-674,-495,694,-267,584,-300,-1000,-1000,-1000,-225,-857,400,1000,538,-436,555,688,137,1000,-760,833,-1000,1000,1000,629,297,-956,953,845,1000,226,-1000,-839,-814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{1000,1000,-1000,-86,266,1000,895,962,-696,-930,1000,1000,-710,1000,662,1000,-322,-1000,290,-624,-1000,1000,1000,-473,964,-862,-1000,-772,-876,-1000,187,40,-486,899,1000,-504,-154,-779,-1000,-162,-851,-706,126,281,-956,1000,1000,-402,733,-2,1000,-1000,-825,1000,-1000,-1000,-1000,1000,558,1000,537,987,-1000,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{-798,1000,439,614,758,334,-291,-624,-385,983,1000,1000,461,570,-643,1000,-779,-1000,-69,544,-1000,-201,-517,-932,1000,-829,848,506,-592,-1000,239,1000,1000,1000,287,685,-235,666,-1000,628,-769,-865,178,-117,-42,-812,-4,-561,-1000,847,1000,-514,-1000,20,1000,-1000,-1000,-939,934,-682,-906,-889,-269,588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{1000,1000,-33,204,-162,-1000,1000,-26,-1000,1000,986,-865,492,-1000,588,79,-794,-1000,-919,1000,1000,944,-626,-703,739,-561,-270,-127,-914,-1000,58,-525,1000,-1000,-1000,-486,-911,-347,-1000,-1000,-1000,1000,1000,473,-1000,-475,-217,-138,1000,-1000,891,-153,1000,-197,670,255,1000,1000,-498,538,1000,-1000,-48,520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{1000,1000,-539,289,440,516,928,-255,-593,1000,1000,-657,-1000,1000,553,18,-836,-1000,770,506,207,817,421,209,1000,-1000,-1000,190,-318,-1000,-143,-390,1000,-45,368,-870,-686,-1000,-1000,6,-1000,1000,1000,-163,-1000,434,542,-242,971,-1000,706,-1000,521,148,-169,-725,495,1000,-970,1000,1000,-1000,-1000,-755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{-508,-847,941,794,60,-1000,342,-861,-1000,-840,29,-362,1000,-1000,727,122,-335,773,-112,964,1000,-1000,-1000,-1000,-400,803,1000,-1000,-156,-102,-548,-573,-199,-187,-1000,258,175,1000,458,-840,-355,36,-1000,421,108,449,-41,-1000,-1000,1000,-606,1000,374,-400,1000,426,222,-1000,-253,-1000,261,127,153,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{1000,1000,771,-41,-801,438,-1000,758,1000,71,-1000,-1000,-1000,-1000,-94,725,-271,259,1000,764,616,154,1000,-80,-521,-1000,-273,1000,1000,-1000,-226,559,-140,-1000,-1000,-1000,578,1000,-1000,1000,-68,944,1000,1000,1000,-1000,417,1000,498,844,-747,-1000,-868,-616,1000,-510,1000,-1000,-912,1000,261,-491,1000,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-65,706,830,229,189,1000,-805,-28,618,-334,-1000,-103,-894,-970,899,169,-913,967,456,940,1000,-708,-139,-1000,-1000,720,-75,-1000,484,710,-11,12,-397,-681,1000,-838,-1000,135,435,387,559,-1000,841,616,-1000,-687,1000,675,-1000,171,1000,135,-61,734,-73,-688,514,-344,462,319,968,1000,-757,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-1000,390,-667,-816,-1000,-509,-109,-974,-659,1000,1000,-1000,-558,203,-1000,575,1000,-1000,702,-1000,585,-402,-683,-1000,-273,-459,148,1000,209,401,-242,-543,857,1000,384,-316,761,-155,821,1000,-691,522,211,-266,260,-952,-170,-1000,-910,-1000,-302,-1000,-1000,1000,-1000,213,-885,-657,1000,1000,1000,-1000,-564,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{899,1000,660,-351,-377,758,705,155,1000,29,-510,-645,-776,-774,-19,1000,-716,564,878,650,453,-321,-9,-511,1000,-590,232,203,869,-962,406,126,113,-1000,-1000,-807,211,1000,-1000,317,372,90,647,1000,845,-1000,774,575,244,1000,-383,-1000,-771,-488,1000,-332,422,-927,-366,203,502,20,348,321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{969,-758,206,923,-113,-14,283,688,-455,-1000,-206,-671,-572,683,379,456,1000,-1000,1000,1000,584,212,731,186,94,-360,1000,-687,612,738,119,-859,-486,-436,916,525,309,-291,494,-170,1000,-59,-1000,37,160,-888,-399,-240,600,-90,805,376,431,1000,92,1000,-398,-75,301,503,-537,1000,690,427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-258,492,893,415,193,1000,-922,-299,-1000,-442,-1000,157,-1000,-934,562,-432,-925,965,305,1000,1000,-993,-518,-1000,-484,-634,163,-1000,300,928,141,566,-118,-723,1000,-549,-1000,-326,597,-516,774,-1000,633,343,-1000,-349,1000,377,-1000,1000,1000,745,-115,961,-7,-558,465,-283,175,-144,963,1000,-1000,201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{876,935,563,579,592,1000,132,-566,-460,879,56,1000,1000,766,75,-998,-1000,1000,-850,820,304,-812,-1000,-1000,-1000,-365,686,-1000,-595,-646,1000,-1000,-217,-941,809,568,-1000,-409,1000,531,-1000,-849,1000,736,-1000,631,1000,-844,-1000,164,-860,-1000,-485,235,361,-192,-136,838,1000,-772,510,787,-539,517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-1000,359,739,-236,-229,-1000,-798,-365,-1000,-577,1000,-310,-403,1000,1000,-1000,1000,19,-1000,-933,1000,-1000,-1000,-1000,-1000,1000,63,-458,-14,1000,306,-1000,-141,1000,1000,49,-1000,-1000,1000,361,-497,-1000,658,719,-1000,1000,886,-1000,-49,-1000,1000,1000,-63,1000,-233,38,-1000,1000,1000,449,1000,-566,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{264,-887,1000,404,-515,478,-811,181,-1000,-1000,667,183,60,-793,-1000,-62,1000,-1000,-337,84,-149,993,1000,-341,-981,25,-211,-1000,219,683,1000,914,1000,4,-432,168,1000,5,-113,-791,1000,-1000,436,-1000,-371,1000,-406,-332,925,-909,-777,-1000,-574,33,425,-1000,1000,843,-359,-532,365,286,-634,-886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-109,159,209,372,266,535,1000,-121,687,362,-334,-888,1000,351,-1000,-979,-585,-284,-155,-758,-339,276,-891,591,-975,-1000,152,400,941,933,-57,1000,1000,409,-1000,72,874,-736,-182,-376,-60,-145,-1000,23,1000,-991,-286,-1000,1000,-91,-282,-879,-1000,-763,948,670,1000,-822,702,-1000,-937,-267,-1000,-398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{237,-236,-1000,-351,628,-176,170,-737,1000,400,893,-1000,1000,109,400,-165,-490,-148,325,-763,-615,-263,1000,1000,-210,1000,-108,-648,598,933,766,1000,589,24,415,346,474,1000,642,763,687,705,-240,23,-777,450,-615,-1000,1000,743,-376,361,-1000,-63,-877,-197,1000,-347,1000,-1000,-736,1000,-187,836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{155,-1000,1000,674,-520,1000,-375,1000,-1000,-1000,-1000,1000,-417,-1000,-1000,1000,750,-217,-1000,525,34,640,-1000,-1000,-1000,-866,1000,-1000,-348,1000,-666,-102,106,971,-847,1000,742,-1000,280,-662,-707,-1000,179,-30,613,-162,-662,79,1000,-1000,-1000,-1000,-313,-1000,814,1000,-976,444,-959,-301,-1000,-1000,361,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-328,-887,1000,194,-539,569,-374,218,-1000,-1000,-405,183,-209,-793,-1000,154,825,-452,-337,-248,112,813,838,-341,-981,119,382,-214,474,838,-166,989,374,472,-982,913,820,-478,-141,-757,140,286,-139,-333,471,187,-620,-158,78,-909,-282,-1000,-502,-363,813,400,457,454,-49,-272,-227,-1000,319,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{264,1000,-1000,9,-1000,-1000,27,-336,1000,1000,1000,-997,1000,703,1000,-1000,-1000,1000,1000,330,-1000,-954,893,1000,1000,1000,-517,1000,-81,683,602,-138,-906,189,126,-385,474,1000,1000,-1000,171,1000,608,1000,-105,-340,1000,-1000,-1000,1000,1000,1000,-1000,1000,-1000,-1000,1000,-1000,1000,-1000,1000,1000,359,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{850,-887,726,934,-273,341,-502,592,66,-185,-108,1000,179,-992,-704,-236,816,-522,-1000,515,-563,537,861,65,-756,6,-669,-582,-785,845,864,154,154,186,-309,-109,1000,-157,1000,-512,278,-493,826,-728,-456,429,7,-596,272,-808,-299,-1000,-858,175,-426,-1000,468,-6,-218,-771,636,-717,-218,824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{1000,-490,1000,669,-684,-42,187,326,757,150,-582,-441,-780,-732,829,1000,-1000,105,-456,-958,-454,-390,-976,-227,419,-904,-739,679,-619,-740,-819,11,-1000,-1000,297,767,877,-90,-267,-1000,-1000,-1000,220,-466,-10,-813,518,-8,197,-824,-95,-1000,-977,-363,-895,-883,1000,-1000,162,-866,-1000,251,763,-705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-897,-632,241,43,360,558,131,382,-1000,-539,701,-217,-235,-218,-601,873,984,318,-1000,546,932,94,-339,-1000,102,249,1000,-1000,473,319,-416,-308,-487,42,234,586,-652,760,721,1000,1000,400,-131,-71,-11,1000,-688,573,1000,798,-835,1000,981,-627,1000,1000,-1000,1000,799,1000,1000,-1000,-586,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{-16,-119,417,-11,-284,143,1000,570,539,588,-1000,1000,1000,153,587,1000,-821,457,783,1000,95,-60,143,-1000,422,98,671,-182,400,-130,-586,-467,465,1000,-110,1000,-505,-479,44,-484,-934,-17,885,-120,-64,579,909,-187,293,-709,212,832,287,-909,693,255,-454,-4,334,487,492,-400,1000,-377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{595,744,-167,673,840,835,-366,-238,771,-462,999,-696,-696,539,934,256,747,68,988,-375,465,-640,752,52,934,-684,-394,-156,-20,-184,512,182,355,109,967,405,-450,-367,866,302,748,223,917,157,171,-828,548,910,723,-95,743,292,784,-883,-833,-12,297,140,82,11,-123,46,80,-683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{768,493,-921,998,524,278,522,-160,160,59,73,-415,-1000,-1000,-793,1000,-770,494,-696,472,125,802,70,183,235,-886,-355,612,916,1000,1000,-722,1000,-32,1000,-697,-275,-943,1000,564,-1000,1000,44,-280,945,367,669,-232,-265,-1000,1000,-396,-235,-1000,-1000,-165,-1000,-49,-1000,747,-273,1000,24,840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{671,-794,-699,514,605,-347,124,1000,889,92,-785,962,820,-235,1000,770,-1000,1000,-43,22,-1000,97,-786,-570,-1000,939,1000,-1000,1000,-793,151,-421,-446,1000,-586,1000,187,-231,-1000,68,-1000,894,1000,-1000,900,-543,1000,-1000,1000,-1000,1000,-387,1000,1000,1000,-64,608,-678,-241,378,717,-1000,1000,-517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{708,161,847,444,-517,-64,502,1000,400,-413,-843,229,1000,-302,268,177,-200,399,-27,-348,-264,227,559,-1000,735,333,574,3,1000,-1000,-74,-343,-1000,96,-1000,756,-1000,-1000,870,-260,137,-52,1000,838,-665,1000,1000,-1000,-1000,775,329,-400,-292,-55,853,663,308,886,-5,-669,833,-1000,1000,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{-837,-274,-1000,919,-332,-987,698,-84,-403,691,12,-1000,33,-905,-873,442,-1000,282,-485,-420,1000,242,1000,1000,739,-1000,-100,-101,787,770,1000,-529,326,180,-1000,-1000,-400,78,-191,1000,-872,849,-742,535,-497,1000,-610,-40,-184,518,-1000,-1000,-1000,156,-689,-682,692,855,-1000,571,209,-894,-925,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{1000,278,-711,934,626,189,137,1000,-357,206,-635,306,207,282,1000,1000,86,288,1000,1000,-1000,262,-1000,-1000,-952,1000,910,-1000,-1000,-1000,504,397,-303,694,1000,528,895,0,0,776,-1000,1000,897,-1000,1000,-821,1000,-1000,411,-469,1000,1000,1000,491,246,1000,608,-291,346,-545,429,1000,1000,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{1000,1000,-1000,-636,215,952,722,289,-989,-531,-1000,-375,-1000,194,-307,1000,-417,-359,704,472,-726,-368,-1000,-707,-989,98,-635,355,-477,784,1000,401,465,-692,-237,-1000,1000,248,1000,1000,806,1000,809,-742,1000,691,891,-341,-1000,-206,392,832,1000,-1000,-967,167,-454,95,-563,-445,-395,1000,685,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{0,107,-1000,719,81,189,57,0,-357,-1000,-255,0,-1000,1000,-1000,214,-406,-760,0,-152,189,1000,0,374,-161,0,497,0,258,442,954,645,962,-797,253,-1000,1000,0,0,291,-1000,1000,897,-50,122,-1000,-930,-1000,411,577,-662,-750,-701,491,0,479,-261,-299,742,-1000,0,799,88,736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{1000,-124,395,967,714,-1000,-940,398,-641,-222,1000,-1000,902,-269,758,-637,289,1000,657,200,-303,405,-106,-623,-428,-230,695,-426,1000,-1000,1000,-151,248,-34,-1000,-1000,-1000,-893,1000,1000,1000,969,-793,94,1000,134,1000,-330,-330,-1000,-35,633,212,848,-630,663,-942,1000,-374,-119,345,-632,695,641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{1000,1000,1000,584,587,-1000,639,-61,1000,238,-749,-623,-418,551,131,-1000,-437,1000,971,1000,695,581,1000,-232,1000,620,-1000,565,-701,-796,1000,-543,-54,-1000,200,-437,-610,-1000,817,-299,-416,-205,-719,-198,-364,-363,-719,-1000,357,-324,1000,842,-438,-275,-260,92,1000,296,250,-572,558,-1000,-173,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-108,660,934,723,461,-988,-266,-249,-140,-964,132,-640,-5,-586,-91,-599,-619,-543,-81,-178,343,397,-267,136,-383,-1000,261,678,158,-383,-345,432,-108,-436,209,238,-347,-54,-1000,1000,472,300,-1000,-142,211,5,-757,-391,597,-23,104,583,987,751,-423,-383,1000,243,1000,-193,492,180,159,-898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{506,1000,1000,734,233,-1000,961,172,720,806,-1000,-719,-429,-77,80,-93,-612,851,1000,573,685,290,1000,-1000,1000,-947,-875,652,-921,-872,1000,-1000,490,-1000,144,-766,-983,-653,1000,-525,-688,659,740,97,-399,536,16,-1000,357,48,1000,590,-1000,273,-508,436,55,1000,321,-1000,355,-306,-856,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-423,-100,-124,679,988,-348,353,-1000,-1000,-856,-369,-1000,459,1000,-1000,-673,461,-257,-489,1000,523,20,777,979,-762,-511,170,-957,-402,-1000,-284,310,-73,897,221,-253,2,-689,131,-294,-488,1000,1000,-229,469,-867,-1000,960,823,335,490,996,746,1000,193,-861,-611,226,268,718,1000,1000,-76,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{114,382,33,714,-350,-876,-172,-721,378,-493,-650,-81,66,-1000,-393,-1000,-1000,-368,-156,-227,1000,340,56,58,-3,-312,-464,-110,-1000,-1000,131,-80,-241,-1000,-335,296,-1000,19,-119,615,1,1000,-932,-138,457,229,-963,15,-37,537,152,215,-120,217,291,-713,400,1000,930,-243,629,7,460,11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-76,649,-899,454,166,766,-502,1000,-248,-820,-641,1000,1000,62,284,712,-449,-807,107,-1000,925,-533,-90,-710,-1000,-1000,-1000,278,-1000,-1000,-710,860,-628,609,-351,1000,321,-956,-376,-313,-100,1000,75,260,-719,906,-1000,1000,1000,-356,403,714,-800,1000,1,-964,-1000,-482,77,1000,468,1000,185,83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{80,1000,323,-222,493,-336,1000,582,936,1000,352,71,331,103,-418,1000,246,-243,1000,118,-191,-290,1000,-1000,796,-356,-144,521,682,-64,57,-1000,1000,-704,360,-793,-260,123,888,-572,-612,1000,283,462,507,956,546,-1000,1000,1000,468,-166,-92,237,-1000,830,344,570,176,-901,538,923,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{1000,-418,-62,-182,-23,-861,-1000,615,-1000,-746,-937,-878,-358,-1000,854,-865,-449,163,254,-1000,792,-83,-469,-192,-1000,-1000,-445,257,-1000,-981,633,980,-187,-294,-802,1000,-1000,-239,-199,1000,1000,-211,102,830,-309,328,-1000,67,-866,17,599,37,-517,1000,1000,-1000,-1000,1000,1000,289,-110,-341,269,676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-219,904,282,154,-86,-1000,192,-660,730,-144,-1000,-887,-949,-1000,483,775,-1000,-113,-59,130,403,256,-131,-1000,131,-1000,-980,147,-1000,-943,587,-786,989,-293,512,-889,1000,61,569,-331,-549,-259,676,-641,59,-38,-118,139,1000,1000,-196,1000,-119,628,106,-847,379,-82,1000,-1000,8,-262,-791,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-225,908,-592,599,-309,605,805,-374,-1000,833,-310,-178,-721,246,608,-785,-905,-655,459,-987,422,153,-577,898,-527,513,22,-48,515,-584,-447,748,331,-1000,1000,-970,-1000,-34,576,-474,-287,-146,-627,72,14,796,182,841,660,-848,-456,-679,951,-366,-413,952,92,1000,1000,225,480,625,-966,999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{874,1000,22,-856,-55,8,910,-777,457,-762,189,880,-30,-1000,-747,-209,-183,-1000,-462,299,-1000,-24,268,78,-67,483,-1000,-958,-824,-177,752,1000,1000,1000,226,536,739,-1000,-670,159,264,-51,572,139,-853,550,-246,19,279,-621,-1000,252,80,-490,-336,1000,-1000,613,-553,-326,582,-1,-654,-865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{730,350,549,809,667,-784,411,-976,-162,-893,-1000,-321,-776,58,642,224,-919,428,-101,734,-284,-277,490,-623,299,188,-267,-419,555,-220,130,-165,-200,99,298,-753,-659,60,-267,-132,205,-942,-95,674,-22,-581,-526,-37,902,-553,-1000,560,380,-231,273,983,-697,102,762,266,334,31,-951,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{501,597,398,278,1000,790,360,492,917,1000,1000,1000,733,-1000,701,-107,-346,-90,508,400,-1000,335,549,-795,585,-271,-291,399,-1000,-502,1000,-1000,-377,-232,-1000,1000,69,-771,87,1000,337,-302,1000,-377,653,947,-1000,-842,89,790,-1000,-109,337,286,1000,-297,-248,-1000,-104,-791,-289,1000,-891,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-675,1000,222,19,569,914,1000,-99,-143,750,1000,24,-830,-1000,790,36,-1000,-535,-1000,257,-1000,-1000,38,-13,694,963,79,-554,-1000,-802,1000,-503,938,690,-307,-374,1000,-870,-522,1000,570,-64,888,-107,-42,-696,-1000,542,1000,797,-1000,1000,133,-1000,955,1000,-884,-451,126,-520,1000,288,-1000,-827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{874,400,611,278,659,654,-220,319,1000,558,431,1000,-30,-1000,-1000,446,1000,499,1000,1000,-1000,-277,-1000,-662,-132,-1000,-1000,-392,494,996,33,362,-160,-530,-506,730,-187,-486,-543,967,264,-868,-534,139,120,-778,-873,-1000,279,-778,-488,-59,-18,-279,-185,-1000,-792,-80,-444,-283,622,-771,1000,118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{393,-299,726,944,630,-394,1000,-589,-563,251,724,1000,147,-236,77,791,-876,-873,-197,-494,535,751,319,759,-372,1000,-712,-275,840,397,309,-419,-627,135,217,307,1000,-125,-810,171,695,-144,120,408,497,-1000,-846,698,-1000,780,705,401,339,-856,667,1000,-599,-569,-202,78,-130,-254,760,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{992,610,-180,-203,-907,187,1000,-1000,-173,-1000,-439,-689,579,1000,-1000,-1000,813,-1000,1000,-1000,308,1000,-274,1000,-1000,-628,-429,135,28,-189,-1000,1000,39,-821,1000,466,-1000,-33,852,-1000,392,-275,-573,442,212,1000,1000,419,-526,-1000,466,-1000,434,1000,-1000,485,72,1000,781,1000,1000,427,-915,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{55,-830,-632,-761,527,445,-686,-357,560,117,960,255,100,-954,25,873,224,-710,-970,-650,97,452,557,107,-855,-701,-701,922,-871,-102,894,798,56,-47,324,-986,669,-429,-19,-289,658,-753,-553,-249,-347,494,-528,6,-442,-723,-321,-277,-124,-850,-861,141,275,-168,-920,-807,844,-135,-251,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-714,-699,664,-996,-653,-377,966,129,-87,-1000,57,797,627,556,-81,404,-860,-1000,-1000,122,-272,279,1000,1000,-303,-360,-377,1000,-1000,762,1000,1000,312,850,-1000,-1000,221,1000,812,-1000,219,619,-1000,420,-361,-488,-1000,401,-752,-107,1000,633,364,-793,-1000,659,-937,-28,-844,379,-408,749,787,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-684,-458,1000,-996,-1000,328,484,-789,-514,-417,198,414,613,174,316,178,-633,-913,-351,-36,565,-115,735,-307,-298,-62,-366,-261,-282,609,-4,-247,-242,-333,-395,-847,701,471,510,-754,298,385,-60,1000,-103,848,-286,221,-550,-340,604,617,-294,-225,-405,-583,-284,590,-836,389,-195,189,625,-410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{557,1000,-497,-391,-663,435,-781,233,-494,409,-196,1000,-553,-187,-114,1000,181,708,1000,-263,-477,1000,-499,-322,1000,-192,1000,48,-418,-322,-597,-63,970,-496,108,-180,-121,101,-784,577,-510,180,18,670,797,-724,131,-139,34,-246,649,-625,878,-454,671,476,-671,-203,1000,-287,463,64,-463,170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-394,700,1000,-701,-551,875,389,185,1000,1000,578,760,-968,-1000,1000,1000,1000,803,264,-1000,443,1000,-519,-546,617,623,698,-1000,-944,-700,412,-971,177,-1000,144,-254,1000,-1000,99,113,879,1000,250,227,96,666,-119,-1000,-1000,-453,-535,-1000,1000,1000,836,-1000,-45,-1000,280,1000,872,-19,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,82,1000,-661,-432,730,-896,104,1000,-245,1000,-523,-797,-633,903,219,1000,-433,-607,-659,-75,660,-445,-1000,171,264,-1000,-1000,-424,778,170,-743,-343,-793,1000,-817,-268,-1000,-68,644,-270,-629,-151,1000,-490,1000,134,-918,38,114,-1000,-1000,-868,587,-173,-777,1000,1,21,-512,-1000,-1000,-1000,-793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-31,-1000,922,-701,-984,26,389,-432,-167,107,484,760,-3,-183,403,1000,-594,-824,-1000,105,1000,295,1000,1000,-834,-192,-734,1000,-944,763,412,766,177,117,-659,-1000,1000,115,99,-1000,949,69,-1000,227,96,866,-973,219,-794,-728,922,990,179,-1000,-1000,1000,-45,33,-788,-270,-17,-46,781,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-493,289,190,-566,-166,1000,81,-432,416,789,152,786,492,-1000,-221,579,214,-93,-312,-1000,-413,1000,169,218,498,-651,824,77,-729,-233,412,-71,-340,-1000,-457,-1000,-501,115,-367,-184,-107,788,399,-488,-391,-1000,32,-384,-1000,-215,-406,-1000,-69,980,-25,-663,-289,-119,-611,1000,1000,-46,-1000,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{58,-1000,-253,834,214,-56,626,170,1000,-888,1000,-704,141,642,287,-1000,-109,1000,-942,-934,-1000,38,637,1000,-555,-975,-818,560,-736,223,-141,-1000,-270,1000,-125,56,-103,1000,-1000,488,-881,-45,-630,889,406,1000,398,422,119,729,204,-126,-1000,347,37,-1000,-180,-302,119,120,-69,-1000,-227,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,463,150,-106,620,-414,-501,-416,1000,-1000,-1000,-490,-1000,1000,1000,-964,-382,833,-403,247,145,-699,1000,-533,-212,-53,-1000,-554,646,-1000,1000,1000,1000,1000,1000,-335,-1000,823,-571,615,-1000,671,-476,-1000,-648,-952,-140,1000,1000,87,210,1000,-1000,-462,-976,231,297,855,1000,284,-1000,-402,1000,-845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{514,-234,-606,-875,214,-1000,-177,777,-985,-919,1000,-355,-1000,459,77,936,-1000,1000,-855,1000,465,-13,1000,-1000,125,1000,-50,-1000,-58,-1000,1000,1000,1000,803,358,11,-229,-102,-213,1000,680,-932,548,-1000,-1000,-1000,-501,936,-606,729,252,1000,-485,-992,37,1000,729,1000,1000,120,593,-878,-227,32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{109,-958,244,614,-97,-821,-424,-481,-1000,834,578,-702,376,283,-848,524,-185,374,76,1000,86,-209,-715,-322,-147,-766,652,-62,-592,293,417,-360,-615,-389,-393,-980,16,-1000,-79,20,-1000,-445,868,-151,918,688,587,-586,-1000,-79,224,124,132,-1000,-401,650,-1000,351,-335,9,587,209,-1000,468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{58,229,-253,-927,347,-211,436,350,1000,-1000,-965,-528,-753,886,1000,-936,85,1000,-1000,-516,-1000,-402,1000,-527,-446,-975,-1000,238,-736,-646,-779,-49,-270,1000,-114,570,-1000,53,-1000,141,82,621,-93,117,-552,-1000,-142,1000,916,261,204,369,-1000,928,355,-1000,15,17,1000,-25,282,-1000,1000,-891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-957,-13,-2,614,-832,880,395,716,-358,-964,-1000,-300,-929,1000,-94,-59,-1000,-443,-1000,-118,312,919,42,-596,750,255,-1000,-1000,-1000,790,773,1000,1000,1000,642,1000,-1000,-692,310,1000,-315,913,801,-1000,-1000,-1000,-1000,1000,388,318,-883,1000,-1000,-313,511,1000,-45,130,1000,-705,-376,-1000,602,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{303,1000,-295,54,-10,-243,-309,649,-633,-931,-905,-717,-1000,-476,229,-341,-1000,-1000,1000,1000,1000,592,986,1000,-11,1000,-239,530,495,-267,742,400,1000,-846,165,-44,-1000,-654,148,1000,76,-1000,1000,-1000,-400,-1000,-1000,634,-799,811,-903,1000,-1000,-910,930,368,-587,943,1000,-256,-720,-1000,430,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{68,-700,1000,-441,123,-542,523,-563,1000,-573,761,-911,228,824,1000,-1000,875,1000,-973,-927,-570,-143,5,-449,410,-1000,-806,-979,-890,-204,382,1000,56,1000,995,-930,347,321,-673,641,-1000,629,-1000,984,872,978,1000,187,1000,-523,707,53,-794,401,-1000,-1000,-207,117,1000,-425,-545,1000,-595,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-480,-1000,866,-66,645,-257,255,-1000,1000,-220,-1000,375,534,610,483,922,1000,-802,479,700,-636,9,-364,523,-195,-370,149,1000,-957,-372,201,-198,-805,-759,620,-98,-809,-348,300,-1000,-964,1000,-987,-604,-712,-785,-364,510,1000,-1000,355,1000,74,912,384,173,1000,-1000,747,817,818,132,1000,-312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-716,63,-931,16,-332,-439,1000,-991,270,415,72,-500,-1,-1000,-1000,1000,713,-910,-1000,199,-239,-343,-481,-1000,-564,284,865,479,-277,-843,-559,-321,462,-243,-375,-418,475,-280,-791,391,-975,-396,-527,993,691,-1000,-880,-63,1000,-218,76,473,51,-687,655,149,1000,-1000,900,-14,1000,-847,352,422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-310,1000,-1000,369,627,-961,560,603,-558,445,68,781,606,-554,-959,522,-123,-437,-605,-599,-44,1000,-494,-265,-470,554,962,-57,-219,-404,-1000,547,-281,892,-253,-891,-448,174,-730,886,113,619,579,755,1000,-390,543,468,808,341,-304,-274,-350,-148,517,-557,828,-517,546,-789,803,-615,148,-53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-310,-1000,-986,249,574,788,-153,-372,-1000,37,-60,-442,299,134,832,1000,524,-1000,-226,1000,-1000,53,296,1,-84,159,239,-755,-1000,-1000,884,-491,-605,-432,464,751,-1000,-865,397,-1000,-1000,1000,-1000,439,-1000,-990,700,182,243,-4,-6,1000,69,1000,634,196,914,-1000,24,693,-203,-259,828,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-1000,954,797,48,1000,-1000,847,-805,682,1000,74,-622,-420,1000,307,-568,112,-501,1000,-268,1000,-177,-609,1000,1000,-571,1000,-25,-848,644,1000,-49,1000,41,1000,-1000,764,-132,-527,-555,300,-567,569,729,1000,676,-630,-1000,1000,-451,731,248,1000,12,-132,366,-214,-1000,-443,318,-354,36,356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-280,-721,-710,-71,390,210,213,-256,-163,-40,225,-267,-82,-184,-161,553,-17,-1000,-553,594,-840,-173,-105,-400,327,826,243,639,-402,-365,136,259,-150,-682,-70,670,-1000,-613,-401,-596,-859,143,-400,681,-400,-1000,73,284,660,345,-201,439,-541,-1000,-820,-549,1000,-130,26,-287,0,-1000,366,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{773,-700,164,-97,-15,54,-718,-73,62,251,770,-161,614,645,15,911,401,-266,-136,675,262,517,-160,-1000,-1000,597,-783,990,422,-1000,180,546,850,-289,-658,312,-1000,25,-3,-796,-283,127,-421,753,-119,-84,92,-272,897,222,89,711,-393,1000,549,172,870,708,-148,345,533,-646,6,169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-167,1000,-1000,59,700,21,-694,141,-977,746,824,-802,-845,-1000,-1000,112,-159,-515,-999,32,-190,173,-170,-424,-1000,851,1000,-216,-541,-839,-528,400,1000,501,-579,-162,-58,475,-1000,1000,-305,-716,101,1000,191,-369,-650,-561,1000,522,-646,-425,-598,-838,674,-744,315,1000,153,-1000,-645,-802,-116,919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-836,152,269,-730,-815,-513,414,-59,-586,791,1000,414,74,59,47,-441,-785,109,-1000,1000,-1000,-410,787,-215,-723,-405,-146,89,1000,-861,28,-418,-779,-544,-62,106,1000,-552,441,-1000,-847,-313,1000,976,1000,-1000,-333,920,-747,454,-722,628,1000,-1000,1000,701,-1000,-1000,-574,151,1000,-973,449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-527,-777,654,-375,-689,347,-991,420,185,888,-283,57,539,1000,-812,95,-408,914,43,-259,-105,742,-363,395,477,446,-467,37,643,-531,775,203,281,-119,-300,109,897,841,10,-998,572,-898,251,-264,45,-32,245,833,456,487,104,231,679,279,928,243,164,-218,508,331,621,-301,179,-647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-826,293,451,-176,-22,330,664,-1000,357,-1000,203,-281,234,-423,-150,593,422,-304,-79,1000,-1000,-987,624,-364,-184,-57,-33,143,-227,-32,561,-433,-155,-1000,487,-789,258,393,614,-9,-222,1000,-572,-122,400,712,-673,-401,-973,459,314,703,901,-759,487,-222,1000,833,642,44,677,106,1000,234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-443,-1000,-1000,-581,-667,38,-212,968,443,-222,-570,-422,-498,26,821,-259,-957,930,-666,246,1000,617,-463,979,-957,882,-1000,-1000,-690,261,-1000,-451,849,524,-592,1000,-352,539,-175,1000,799,1000,-1000,-145,-147,-304,-924,-527,1000,522,-1000,-323,-1000,-506,-666,464,1000,-1000,-399,-458,-1000,676,-392,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-514,-777,-686,749,-796,301,-65,1000,-1000,-454,-793,57,-496,625,226,-921,-1000,314,104,-1000,-105,345,-897,297,-763,804,1000,-1000,1000,1000,642,203,789,-1000,-1000,-162,-95,1000,-191,770,609,593,-904,62,149,1000,1000,1000,621,1000,1000,158,512,14,-177,1000,1000,-1000,746,-871,1000,-301,179,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{690,-469,30,-371,114,-665,31,429,444,-277,179,-43,380,-186,-197,397,311,-719,-111,-154,-657,-495,175,-43,-185,53,-38,-146,-491,1000,765,267,-213,421,481,-27,15,376,-120,-979,-527,-516,347,402,742,285,-380,-689,-298,287,-199,189,451,-340,-487,185,-195,-140,367,-195,-400,1000,985,46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-187,-1000,-1000,-771,-491,-323,-593,1000,364,-1000,-665,-490,-180,199,1000,-247,-209,1000,-666,-1000,705,701,-1000,1000,-957,1000,-976,-892,-821,1000,-533,-990,703,-587,115,1000,-436,852,-289,590,1000,977,920,-181,-595,-304,-892,243,532,673,-1000,158,-1000,-982,-956,920,1000,-751,384,53,-644,-444,118,-3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-883,1000,-1000,373,-935,325,1000,-106,-1000,-1000,-974,1000,-7,-610,1000,-379,-604,708,-606,-1000,-530,-454,355,454,-1000,1000,1000,-1000,1000,702,31,821,122,-1000,-1000,-740,-297,1000,498,1000,279,1000,-1000,427,1000,1000,1000,1000,-84,319,1000,-490,-51,-774,-440,900,35,829,1000,-880,580,-1000,240,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-41,562,71,39,-837,492,186,407,1000,120,-664,-576,-742,-1000,-944,-132,-676,-499,182,-1000,-832,164,-290,654,-137,-1000,-318,-166,-645,-1000,-506,-893,-646,333,1000,-935,-366,-397,-312,-222,975,-1000,535,-506,40,521,1000,299,732,714,920,426,389,-44,411,229,-400,391,-1000,-687,-845,-274,-321,747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-111,645,254,-338,-611,759,632,-1000,1000,-244,902,-1000,205,-209,711,857,-359,-60,-558,-1000,185,-842,-261,-618,-13,-800,-194,-146,-617,498,246,-210,-271,-583,205,7,7,-189,-173,-192,208,-229,-557,-1000,508,365,943,92,-87,213,-847,-283,538,-381,1000,-654,571,87,-56,782,-912,-694,-866,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-295,-637,409,-1000,-178,459,791,537,-521,1000,973,-198,-180,-631,-400,-930,-400,159,-1000,-299,551,-1000,148,231,-341,-220,-626,893,-361,-233,-597,-884,400,1000,-182,282,466,-322,-423,560,90,-1000,733,653,1000,835,-543,850,611,-817,-98,1000,518,977,1000,725,400,-1000,-716,-21,400,415,-369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-30,844,680,-1000,753,442,1000,1000,-20,933,312,50,57,-108,-275,-1000,464,370,-281,157,571,-953,900,888,-310,1000,-1000,1000,-885,-268,-157,-85,-351,1000,406,-698,837,-103,202,499,-396,-1000,626,-521,1000,-82,-56,693,-364,555,37,315,-556,281,1000,245,384,-1000,-138,268,-573,-1000,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-968,-704,-951,-596,-916,-802,-473,-987,-622,293,910,158,-154,59,-128,-976,-11,-540,-898,857,981,-760,-889,668,-898,764,-271,-875,-146,793,-342,-210,-257,-603,-435,480,-100,107,-43,-806,443,-641,-673,-217,837,310,943,347,-650,174,24,210,161,370,-951,-780,-328,-999,-545,141,143,-597,-911,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-865,-169,287,889,-491,-667,7,469,1000,370,268,246,666,1000,266,-674,-825,-86,-413,-241,-92,994,805,471,-1000,142,219,-1000,290,97,-250,-265,348,-1000,1000,166,-1000,358,-786,1000,988,17,1000,713,-440,450,-582,507,-62,419,-851,-1000,1000,-950,174,1000,-167,1000,-79,417,727,952,-881,2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{741,730,-1000,-891,-171,254,565,156,-891,238,782,112,522,1000,6,1000,-626,-236,-175,-1000,16,-450,-561,-530,611,-1000,-300,560,433,-1000,773,473,185,-1000,-452,-150,618,-664,1000,-817,-1000,350,750,-667,394,373,1000,385,175,898,-259,-150,378,1000,816,414,1000,-1000,-592,-244,65,-792,1000,268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{543,565,361,583,-224,456,288,101,983,601,431,332,-1000,-351,-962,-285,-794,688,431,-440,-14,553,-648,428,141,-1000,764,-647,-243,-789,-81,-580,197,-839,474,852,-470,134,274,649,-661,473,-97,-219,574,777,489,-361,751,23,1000,25,-25,-478,527,-363,-728,290,-270,881,-479,-361,-740,-23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{904,-338,-342,88,-632,-936,77,1000,1000,212,600,555,-1000,927,-847,-459,-176,686,-502,1000,619,718,796,325,231,1000,1000,216,543,64,371,689,1000,-487,-469,231,-1000,470,-106,704,-1000,1000,-103,900,60,-399,-1000,-249,-304,662,-196,-480,-56,111,-464,74,-417,-848,-196,-337,1000,788,555,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.ArrayType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{-55,544,-202,-481,-89,-85,-837,-1000,114,-52,650,25,120,223,37,-360,-400,-777,-1000,91,-779,1000,-1000,455,-344,-697,363,297,-503,-692,262,-629,132,129,73,-683,441,-7,540,-185,-736,544,-403,134,115,-45,201,-357,743,-961,-1000,64,-129,-509,900,1000,-143,70,673,-294,1000,1000,324,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.CollectionType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{-1000,687,359,-2,-608,493,-12,-303,117,596,-587,266,808,-411,-1000,843,1000,-144,337,-821,4,947,21,325,617,107,1000,238,-796,-274,-359,-167,1000,8,-1000,-886,1000,857,-257,-1000,0,-744,45,-126,-1000,-404,-62,1000,119,273,400,-971,22,546,120,-1000,982,313,-252,1000,-1000,1000,-538,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{-1000,1000,1000,-86,-278,833,-772,-755,1000,1000,370,729,942,109,-1000,78,1000,-817,-928,-1000,-323,744,-1,1000,-466,-550,1000,-1000,-1000,-355,257,-622,321,-889,-769,-1000,1000,1000,956,-1000,509,-768,-1000,1000,-1000,88,671,1000,740,-1000,-1000,-1000,121,546,507,-162,397,-948,-294,1000,-818,-926,-438,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.CollectionType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{-469,712,-915,-786,-295,-192,-1000,-1000,258,-332,530,-1000,-120,405,-403,1000,-162,-1000,-361,-779,-1000,679,-1000,-284,1000,509,1000,-1000,-993,-1000,-807,1000,1000,-162,-353,-912,14,1000,-921,-150,271,275,-1000,-453,158,-759,585,1000,1000,-472,-25,92,-617,-1000,201,-532,1000,246,-1000,1000,-400,421,-512,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.SimpleType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{-59,626,-813,-551,418,-226,642,-826,-969,474,734,-930,67,-290,-61,108,-770,-179,-775,-24,-9,529,-678,-894,-647,385,500,93,540,-483,-301,-15,752,-119,-390,739,-501,944,-111,-795,-705,-318,50,398,-479,125,816,553,96,-743,-337,-546,312,-960,-820,885,-390,569,-561,-302,-593,-85,67,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.SimpleType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{1000,1000,-1000,-826,177,-211,-881,-1000,194,1000,867,-195,-310,42,1000,-1000,-118,-768,1000,882,-1000,903,-231,-762,-599,-834,-278,-57,297,-878,879,360,186,-267,1000,-501,-139,-1000,-1000,1000,-1000,704,-1000,-841,482,8,451,989,783,-1000,-1000,1000,-22,-1000,-285,738,-985,-446,1000,-1000,441,257,1000,-555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.ArrayType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{17,687,-806,-86,-852,-493,-95,-303,-1000,-1000,-307,-1000,-1000,-1000,319,1000,-596,-170,-1000,737,-392,466,-1000,867,1000,284,922,1000,-352,-540,-1000,-165,631,-1000,702,48,752,1000,-257,-494,-714,724,898,-994,1000,-557,-16,-992,242,329,400,-1000,29,-1000,-653,-564,1000,1000,-1000,980,978,-330,16,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.MapType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{768,1000,-331,-352,-749,-172,-913,-1000,415,-262,1000,-216,806,1000,-1000,441,-672,-327,-741,-559,-1000,504,-69,148,202,-1000,1000,-173,-738,-394,243,-150,259,137,-836,-65,-184,541,-1000,746,601,1000,-1000,-890,1000,87,619,939,1000,-241,-525,-57,456,-1000,485,827,566,351,-52,-515,1000,1000,605,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{1000,-148,-830,999,-192,-421,88,25,-952,-747,-613,-1000,-1000,-1000,535,1000,-211,52,-1000,-837,-1000,239,1000,780,495,-433,-1000,717,-1000,791,162,266,1000,268,309,1000,-667,-15,-1000,-833,-477,817,-697,-557,978,-907,963,1000,-363,191,-31,-1000,-5,-395,681,-1000,22,-889,-88,-1000,-661,-374,-602,627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{942,543,569,-826,-384,-635,237,-780,301,101,765,404,-1000,-896,41,294,-1000,1000,66,79,-253,-147,669,1000,177,-1000,828,-120,223,-662,901,-183,1000,404,-400,-716,-252,-403,-1000,610,-1000,-326,193,-471,1000,-953,203,1000,243,1000,-333,156,51,505,621,-1000,508,-1000,308,-973,-200,-855,-412,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-722,-20,-879,-71,128,-666,0,148,-999,677,-71,-646,-705,66,155,-265,854,-46,-318,-862,732,-634,-849,-880,-415,878,219,-12,800,-328,-955,222,-408,-560,34,892,696,-745,-185,-362,77,636,-393,-442,76,-999,536,-5,850,451,-685,579,-422,-595,715,-854,943,984,705,182,-309,251,-170,-700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-5,-106,640,549,-389,-780,339,720,-49,1000,393,-319,160,-108,593,-109,628,948,-1000,-244,521,307,-41,632,-488,788,296,1000,-155,-378,176,1000,-1000,533,744,32,739,-1000,312,-1000,648,-285,96,-554,1000,-1000,-862,-400,1000,150,-843,450,-250,-442,787,-269,1000,1000,1000,-503,-20,194,-1000,144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{159,857,-1000,429,378,-405,322,-44,-1000,643,0,-79,-77,682,-1000,-803,-93,-469,-1000,-215,1000,1000,780,-1000,429,974,-1000,75,660,-1000,-1000,-1000,-368,-196,371,828,771,193,-27,353,-6,-729,-639,-179,-1000,-609,936,654,736,90,-470,-264,-838,37,151,-1000,1000,1000,502,634,1000,563,-1000,105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{623,286,386,788,-211,-954,494,919,-287,830,631,452,488,515,962,-906,102,434,-262,-1000,813,-1000,-1000,-1000,191,660,1000,-309,1000,426,-117,-880,-709,-743,324,192,-193,-1000,550,-1000,354,520,-942,-796,-976,-237,558,-1000,294,349,-664,602,-18,37,488,351,-122,598,-319,-320,828,-596,130,-289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{136,-1000,-538,293,-318,1000,-1000,752,-258,76,1000,507,719,-626,84,111,-397,-732,389,555,1000,802,941,-6,832,877,702,-490,1000,-1000,-888,602,159,863,-539,-135,672,974,-222,349,-455,82,-339,-240,638,264,368,-171,-447,-1000,-517,-501,-50,347,519,1000,-168,-103,-513,497,-1000,49,81,245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{540,-329,645,-411,287,-871,93,460,584,974,419,71,-598,-385,576,-60,-237,463,876,-702,-60,184,-939,866,-397,-824,951,-586,803,560,553,52,-210,508,-922,-621,95,-963,-885,-410,-951,544,-620,-570,986,-703,931,501,713,859,-516,822,545,-2,587,-211,-824,-986,780,-281,970,-389,613,-998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{1000,1000,-958,573,340,-1000,-482,264,-1000,717,392,-1000,-1000,-1000,87,1000,1000,382,-649,-788,509,-87,751,-738,-971,1000,-257,-416,486,701,-964,889,964,-939,13,1000,1000,-706,-392,-703,633,1000,-567,-971,45,-1000,347,327,1000,510,-982,-162,1000,-1000,1000,-1000,769,741,1000,-62,175,1000,399,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{849,-956,-410,-626,945,-203,-776,805,95,-159,-853,592,783,-737,-16,227,-831,264,-625,-517,507,-454,-752,124,-583,-381,-356,420,148,695,-604,657,310,945,-457,-665,749,334,618,-394,178,-3,122,-337,-4,-836,-567,-787,323,-990,160,988,694,-482,518,-681,81,-616,693,-826,955,359,360,825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-336,36,809,129,-11,-597,-1000,61,1000,-814,619,-200,437,420,0,-886,1000,-349,-916,415,-290,-775,553,-455,6,711,-377,47,587,-982,-68,492,-445,901,-151,523,-620,6,432,184,-591,520,-690,-13,433,-728,629,-139,-334,731,-598,-594,-618,-433,-629,-106,-99,539,-315,131,589,-194,-711,-888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{783,-872,-438,-21,344,-597,-1000,-339,-480,987,-338,658,761,-584,383,-886,-977,1000,667,-863,-290,1000,1000,-621,6,979,-173,1000,-1000,507,48,1000,-1000,914,-1000,-1000,1000,-376,342,1000,-761,520,618,-1000,-338,708,1000,850,1000,1000,282,-965,349,1000,1000,-527,1000,1000,952,862,-711,-194,800,391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{312,915,643,34,-959,917,958,66,-837,536,-77,-832,-535,-47,-875,-976,-141,-147,-188,316,906,-847,408,-968,461,978,979,-346,-882,304,23,-303,-531,-150,-551,589,358,-794,-51,707,301,-68,-903,-439,29,430,458,-436,696,746,-270,-406,-252,-837,-442,667,641,-600,-10,413,-338,-412,790,-219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-336,-1000,-1000,258,198,-1000,-128,-262,-935,1000,197,-175,437,-1000,148,-153,-1000,1000,393,-1000,1000,450,-878,-1000,6,176,-654,571,94,-982,1000,-150,734,901,-1000,208,1000,399,1000,1000,-112,1000,653,-253,433,1,1000,-671,1000,-1000,801,602,373,1000,702,-1000,333,1000,584,-170,589,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-1000,1000,-528,119,-793,-632,-767,-1000,-361,1000,1000,-1000,228,1000,-375,820,1000,-935,-381,-493,-619,-261,311,-1000,-326,1000,-353,239,-262,-1000,-985,-510,-812,700,436,343,-978,-292,-741,616,625,377,-600,-454,228,-422,-474,-698,-1000,1000,-715,-1000,616,-598,-870,537,1000,466,24,1000,-345,403,635,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-800,866,866,-326,-64,-50,-465,-582,945,-1000,-307,-1000,437,598,0,-44,710,-268,-916,-339,-545,-775,-347,-671,-815,711,-307,630,-326,-982,-1000,47,-884,639,-1,-1000,-203,447,-426,808,48,269,-289,-721,-603,-197,-1000,-610,271,1000,-159,-594,429,-420,466,830,618,578,725,1000,63,717,-711,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{915,-1000,-1000,-41,402,-987,-487,-258,-396,1000,-777,-1000,744,-327,804,586,-974,201,-51,-697,1000,-108,-244,-1000,1000,897,327,-474,961,-559,-242,870,-1000,729,-1000,-675,657,-136,677,1000,-243,290,495,-413,562,-115,461,641,487,-370,-113,-244,615,634,1000,38,874,812,797,894,-295,1000,654,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{1000,-400,-1000,-921,1000,-1000,-758,1000,14,1000,-320,144,1000,1000,137,-191,-1000,181,-1000,375,641,-775,23,1000,-388,290,-981,1000,1000,1000,1000,492,106,1000,-1000,-120,-85,574,1000,402,-1000,1000,1000,-351,824,-728,629,-721,1000,-1000,547,361,-1000,-481,1000,-106,-1000,1000,53,-974,-1000,-486,699,-647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-1000,430,888,-581,34,489,-217,822,-925,-1000,1000,484,653,277,-1000,-330,-384,417,1000,1000,-1000,-298,-1000,-1000,205,815,18,306,-1000,-1000,-271,-1000,-137,-1000,1000,-1000,-677,-485,512,-1000,-438,1000,-158,-513,-1000,1000,754,267,1000,-374,-977,-402,1000,-931,-1000,766,15,-1000,-481,-1000,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{1000,930,279,592,-938,-290,-302,52,-429,-364,564,214,-1000,773,1000,658,838,61,-834,400,-1000,852,571,-257,1000,-396,1000,1000,-1000,-855,-143,-205,-396,-1000,-727,-219,-1000,-1000,1000,726,489,23,-1000,-1000,61,396,1000,-1000,1000,638,1000,1000,-318,-1000,308,-891,-1000,560,-1000,-1000,1000,455,-819,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-636,937,16,-777,-751,-878,230,-812,1000,321,1000,-80,457,-1000,1000,183,1000,906,1000,273,-278,-726,-420,12,-304,1000,-1000,136,-359,16,-388,56,-927,154,701,-1000,429,1000,862,-1000,356,-20,-189,1000,1000,-923,132,971,333,163,-686,-18,1000,-548,-843,-423,-1000,762,797,-303,-503,171,1000,-184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0NA==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{1000,-12,-276,-274,189,-1000,-272,-1000,1000,229,-532,62,694,-848,1000,-1000,-287,538,1000,219,1000,-961,19,1000,-401,-631,-1000,-1000,1000,1000,-1000,1000,-828,1000,-367,-478,1000,1000,363,70,-169,996,510,743,-104,-1000,-746,1000,-1000,614,-1000,-696,-1000,784,-645,-922,-1000,1000,1000,1000,-626,-618,-502,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{400,55,-811,329,-36,64,-1000,0,-63,-955,249,715,-601,513,-898,982,1000,174,270,971,-87,252,248,-1000,1000,191,519,-1000,-120,-53,-1000,1000,-45,400,-743,-683,-380,3,1000,-51,-122,656,-1000,-1000,-149,-858,347,-930,597,64,-327,1000,-783,-535,-585,701,511,-334,1000,-20,109,52,-329,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-20,-246,738,-996,212,808,-773,1000,-443,-884,582,499,663,1000,-20,-330,-232,508,1000,715,-1000,-311,-1000,-1000,205,1000,18,-900,-1000,-1000,-727,-20,-53,-1000,1000,-1000,-677,-1000,477,-476,-722,1000,-399,-513,-1000,1000,754,303,1000,-280,-1000,-402,668,-580,-1000,-868,513,-1000,499,-1000,-698,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{749,1000,-488,-557,432,-803,-28,-831,-583,748,781,167,565,-485,197,-400,-298,457,233,1000,379,-146,-438,400,11,-114,-1000,-576,400,400,-999,400,-402,400,-1000,-674,219,69,363,-513,-96,997,657,12,-568,-400,-588,396,-176,128,-856,-148,-400,-699,-444,146,-1000,372,214,1000,-1000,-761,-51,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mg==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{1000,-881,-3,-932,-36,364,-272,352,148,-955,147,465,233,642,1000,982,69,133,99,419,-1000,252,-1000,-1000,244,1000,331,-1000,-1000,-1000,-1000,1000,700,-1000,269,-1000,-978,-937,928,70,-345,1000,-4,-557,-1000,122,648,5,1000,-286,83,348,323,-284,-585,667,15,-739,1000,-1000,-383,396,1000,323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{1000,799,-1000,-56,-36,-289,1000,1000,1000,-1000,1000,706,480,493,1000,-450,-1000,-818,-611,816,1,-718,484,1000,-1000,584,-658,999,-1000,681,-981,953,-1000,1000,604,-965,-1000,1000,990,-7,1000,-431,119,476,1000,-1000,-1000,886,-867,82,-799,-1000,-1000,392,-8,805,-1000,-824,396,1000,-133,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-800,667,-259,414,-991,-151,926,143,-490,-1000,1000,438,307,-189,744,690,1000,265,414,175,-528,-1000,-844,-37,309,942,-344,800,-863,-405,-197,-655,-375,-813,780,-1000,-954,-702,897,-754,1000,400,42,1000,441,-470,523,16,315,507,303,-106,675,-998,-154,-391,-1000,749,-656,-934,992,613,1000,-18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{733,216,-286,139,43,-184,-1000,895,-675,-247,485,1000,205,876,-930,-942,-1000,-890,1000,263,271,-1000,-932,-472,-1000,-1000,-447,-261,-422,-1000,763,-1000,150,706,1000,62,-947,-89,448,464,907,-1000,-857,74,-1000,-1000,-22,-961,-1000,-590,-949,1000,-766,381,-1000,-475,317,-1000,-1000,-1000,351,-563,70,735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-3,-63,64,-339,-411,-1000,-727,926,134,159,-50,490,393,-876,423,-1000,-852,-923,1000,214,163,-1000,-946,724,-579,-936,-226,931,163,-768,1000,-1000,-195,334,1000,195,-509,-504,-980,395,1000,-427,-710,765,-1000,-1000,-444,-938,-1000,-506,-330,969,-1000,548,-816,-1000,-323,-1000,-525,-670,610,-598,1000,-228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-354,-416,-571,429,-584,-886,53,-449,29,436,48,-754,798,125,504,-33,-527,-142,-135,-331,-95,-428,643,1000,-572,-263,-1000,202,-112,528,133,1000,-149,-1000,-701,1000,-1000,-391,-856,-777,135,-906,-718,770,187,937,-339,-371,845,-644,370,-410,-750,956,40,-61,-927,348,954,108,-644,624,289,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-984,-598,1000,439,-1000,272,374,660,563,1000,-1000,-36,23,120,-437,-757,-106,333,144,11,416,267,-622,539,1000,438,1000,240,-849,-184,-117,113,410,785,21,-117,-176,-698,54,-637,420,803,-72,-24,-333,185,-706,-277,599,-678,1000,-255,927,415,-453,1000,-1000,1000,724,604,-1000,-706,-489,-355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,624,389,-381,-461,-1000,955,-1000,-110,905,116,1000,-293,-1000,-930,1000,753,-234,420,263,1000,-1000,601,1000,500,399,135,1000,-653,1000,-707,1000,-443,132,-55,509,197,99,-1000,-1000,842,-1000,1000,-122,1000,749,-408,727,701,73,-949,-1000,-319,-540,-100,-475,-346,832,-107,-4,-275,1000,70,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,799,410,163,143,-910,-968,880,-213,1000,1000,-301,395,291,988,-827,-11,-383,401,-806,-1000,337,507,469,-785,1000,-260,561,58,-489,839,144,-720,315,35,-109,-915,971,200,-827,-166,464,-52,31,-831,19,-665,-211,-982,353,922,409,-43,568,-415,266,-1000,-810,158,-1000,-47,-279,731,556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-113,111,89,-980,-211,855,-298,-443,-1000,594,693,348,-773,-1000,470,260,564,-1000,878,326,90,-141,-155,474,-1000,3,439,-181,-1000,-430,-102,337,327,452,1000,-99,349,-206,858,-904,954,1000,400,530,-1000,-539,-1,-799,-1000,916,-1000,-400,-912,-1000,-775,-152,978,-331,-1000,991,448,61,962,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-1000,273,-902,-803,-733,798,706,-200,1000,-518,566,379,-265,-569,-121,315,841,547,379,-352,-790,-378,-11,-632,929,326,864,359,-685,326,-821,-102,-56,958,-1000,254,-1000,374,322,352,1000,-746,546,-390,224,-1000,-535,-443,-735,651,400,753,395,-557,326,-1000,1000,-797,-252,-1000,-465,-253,-499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-90,-889,-547,84,-567,-261,-497,-999,834,110,-77,-1000,819,-612,643,134,841,-190,-905,319,139,-1000,-300,-371,-607,-767,-617,-73,-562,-950,-796,920,-522,-385,52,-738,-1000,-247,-527,131,-717,533,505,-302,-186,-472,-359,-321,69,-944,604,-509,59,124,-593,849,721,-1000,14,-34,-853,-144,-56,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-112,693,-416,173,889,253,-545,860,-419,754,568,-466,-46,-927,528,542,296,15,-219,-917,436,10,830,700,-945,937,-857,812,-13,-461,-860,722,-802,-200,295,-433,-875,283,-716,474,411,-67,373,-160,709,118,-200,-905,214,-207,769,558,-175,607,-155,-913,456,-540,-944,-906,898,-399,791,186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{1000,640,-1000,634,78,1000,963,999,831,493,-59,1000,297,-454,-858,206,-752,-391,-55,1000,321,91,-262,-912,421,1000,-1000,1000,-1000,-637,-72,-201,1000,376,-659,-227,1000,-1000,58,483,-1000,-477,-281,-88,-1000,311,312,1000,1000,961,642,-664,-1000,1000,1000,756,-925,1000,-1000,-14,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{207,907,-750,9,-146,-439,-281,-1000,1000,224,-174,-1000,621,-612,717,-195,555,-1000,-832,1000,952,-291,-95,-1000,-1000,-601,-799,1,-990,-838,-997,1000,-522,-385,-520,-418,687,344,-952,703,-717,325,515,-302,-247,153,211,1000,467,-898,138,-1000,616,124,-445,845,287,-1000,14,-34,-912,501,286,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-26,1000,-124,264,-555,627,34,-260,834,-103,-77,-957,740,-612,396,1000,768,-190,-905,319,139,-1000,-989,476,211,-971,-587,-73,-333,-453,-523,-480,-308,-537,28,-19,-1000,-1000,-429,-764,-1000,533,369,-1000,-439,469,-1000,442,-233,-940,166,216,59,486,783,1000,636,-1000,-258,1000,-811,633,1000,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{325,693,-432,-626,43,-583,-354,-577,1000,-630,-823,-1000,1000,-482,558,-1000,947,776,-905,237,-881,-1000,-311,-1000,-725,-352,-857,437,-420,-915,-527,1000,-613,1000,666,-1000,111,-1000,564,474,-1000,924,294,272,-1000,-999,-171,442,-424,-1000,992,308,21,-593,-531,885,461,27,19,-951,-1000,-424,-1000,-368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{372,-308,-20,889,352,10,1000,132,-146,140,-721,66,11,667,-180,592,843,621,-804,-1000,-828,-605,-377,241,1000,-117,309,309,806,40,-100,-413,-203,1000,-137,-199,-900,617,40,365,798,451,-1000,474,-774,959,341,-102,-689,-462,435,1000,-1000,540,-28,614,124,-592,-833,-611,-1000,-1000,-1000,-323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{18,1000,-723,247,-224,-394,-722,-69,-632,356,430,-1000,-879,-703,801,-450,504,-369,-821,257,498,-101,-255,-502,-1000,1000,7,249,381,-1000,-1000,1000,33,115,1000,-1000,1000,1000,-1000,675,-9,-186,371,366,499,-332,955,235,-847,-400,676,-1000,-246,685,671,298,841,-717,-469,-1000,376,-1000,-747,797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-854,106,-349,529,-686,187,-940,92,-551,254,717,-151,-212,861,-67,944,85,237,-1000,-400,729,179,-755,-336,653,-237,1000,1000,-37,-686,505,-1000,-404,206,732,-1000,1000,474,525,400,-188,-57,117,643,20,153,56,-416,1000,235,-26,-1000,1,162,116,-528,433,372,1000,-26,740,668,799,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{53,621,-656,347,341,359,1000,677,702,-68,656,1000,220,504,75,-1000,873,-578,262,291,-369,-98,169,328,-661,416,1000,-57,656,708,-167,1000,7,-1000,425,473,-577,970,487,-79,75,55,-1000,-414,-1000,540,-1000,373,1000,271,-339,593,954,-344,1000,1000,-951,-1000,1000,83,237,936,105,-624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{1000,-975,-226,-42,-177,6,-53,-389,832,-303,155,-670,160,1000,665,-695,772,-693,-1000,180,6,803,301,751,1000,432,-302,519,1000,694,-36,-598,444,-637,584,-311,-815,107,759,1000,389,1000,66,-1000,-700,-432,73,-503,-1000,503,-1000,-250,810,-1000,1000,-940,119,607,-25,1000,934,1000,91,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-829,-179,682,489,-91,935,-432,278,168,409,-637,225,301,-232,157,-824,1000,-1000,874,-499,-127,-99,357,-1000,358,117,-259,-215,-1000,-1000,-231,-1000,1000,599,1000,-560,-73,-334,1000,504,1000,63,1000,-339,-48,-446,-302,-56,191,1000,305,-239,-579,868,116,-446,-758,1000,140,-1000,48,-444,420,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{21,-779,346,-211,-246,899,619,882,-112,-371,315,468,78,618,-487,90,1000,-775,1000,-322,324,601,619,636,-218,1000,-354,-925,-501,-688,782,-1000,143,-854,139,-311,320,745,-13,1000,1000,809,1000,-257,-509,-1000,-743,-26,103,1000,-314,-102,288,187,1000,513,-471,-104,24,-719,165,1000,-60,-275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-285,1000,-1000,879,42,-1000,45,-1000,-811,-190,-25,1000,229,-263,1000,158,231,-40,-1000,138,-704,-451,516,-278,507,-956,1000,954,451,1000,-441,1000,69,105,-189,634,-512,-434,-469,-716,-814,-796,-1000,551,-55,528,-460,500,648,-343,-935,-384,1000,-654,-646,-275,3,-492,117,-747,761,220,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-634,1000,-834,308,-638,-44,-517,1000,-35,-147,155,339,-390,-239,-633,493,561,-342,-735,356,198,106,752,902,-392,254,126,1000,466,-881,304,-179,33,-556,-639,198,678,999,-407,-368,492,744,-17,819,-513,1000,-1000,19,989,-1000,-1000,935,794,962,673,-372,-597,1000,526,465,-959,54,820,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-1000,-1000,-42,-86,-11,-259,-405,-356,-1000,-724,777,-126,-345,1000,-1000,1000,-845,560,-573,-987,1000,-1000,-1000,920,521,855,-1000,1000,368,-784,1000,-1000,200,849,-212,-1000,1000,397,-39,1000,230,1000,-741,482,1000,-1000,1000,270,1000,-10,810,-728,810,470,811,-1000,1000,-200,1000,-2,961,1000,-459,-879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{122,-178,-1000,-871,-1000,-898,620,289,-266,731,-216,-1000,-5,752,266,79,227,-646,102,-942,509,1000,-153,-485,600,323,-1000,-1000,82,886,532,-175,-307,-1000,690,676,-788,-491,812,-1000,485,-1000,109,311,1000,238,304,-1000,-790,521,-1000,1000,-1000,-1000,1000,763,-65,1000,-962,531,-1000,-940,-1000,994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-929,261,-436,-732,-795,1000,1000,518,743,39,-232,-225,-762,-97,35,49,-553,-54,1000,97,758,650,-601,-1000,1000,141,-574,-1000,-208,401,866,102,1000,-247,195,-297,293,830,783,-791,200,-420,-78,200,898,880,1000,658,1000,1000,318,916,727,-147,845,542,-306,527,-574,11,-1000,-1000,722,235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{322,-227,-966,-336,-184,-1000,-65,-378,171,1000,226,358,1000,190,1000,715,-178,476,-1000,-425,1000,694,469,337,1000,-155,1000,-1000,-554,-983,-614,682,-803,-768,-674,1000,-355,1000,-162,-103,-1000,-215,391,-1000,-827,520,-116,876,-1000,-557,151,436,-779,263,-118,797,894,1000,-655,1000,1000,-380,-1000,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{997,185,-686,69,-686,1000,-81,-749,-12,-59,665,77,-429,813,1000,-475,1000,1000,-1000,-448,-1000,457,-344,-718,754,-362,1000,-656,-820,1000,-1000,222,806,936,417,1000,54,1000,-865,744,-830,-330,-1000,-151,-272,137,-324,-388,-1000,451,785,111,568,1000,-779,1000,-66,895,-976,173,1000,559,-1000,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{418,-380,-436,-291,-722,-191,1000,442,870,460,-1000,167,107,-665,505,294,71,637,-388,-1000,962,1000,84,537,1000,-483,-1000,-1000,-698,-770,1000,704,-376,1000,-181,-297,975,1000,-117,-1000,601,-282,-587,-175,1000,643,441,168,-752,1000,-1000,436,-1000,-1000,362,433,264,669,-520,733,-1000,-1000,-719,309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{756,-224,410,-322,-376,-1000,436,518,121,1000,-1000,662,-1000,-207,244,1000,6,530,926,-566,1000,1000,314,289,1000,-1000,-762,-36,536,-1000,672,908,-1000,-418,-429,1000,533,1000,-25,-768,-490,-1000,497,-990,95,1000,128,-1000,-1000,285,25,-1000,-1000,-1000,155,486,543,880,-163,1000,-125,-677,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{698,553,-1000,-236,-346,-550,-445,881,-586,708,639,-265,-1000,1000,249,127,43,502,-21,-423,431,1000,25,703,1000,-1000,-1000,-185,-586,-262,-877,1000,-726,1000,664,1000,-583,7,-265,540,-1000,-1000,1000,-442,1000,368,-1000,-783,-98,124,1000,-964,-776,896,1000,-278,617,727,-1000,1000,365,1000,-919,282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{883,224,-1000,-281,-1000,-660,1000,366,-558,28,-713,-491,230,-1000,35,-916,864,858,560,-262,1000,1000,395,776,145,-376,-992,-1000,-2,1000,1000,307,-1000,385,-1000,-432,207,741,998,-1000,-727,-1000,-889,-49,582,-1000,-1000,1000,695,166,-382,869,-1000,131,1000,-788,23,440,-461,479,-1000,-1000,-1000,771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-218,367,-552,583,232,803,522,440,189,-94,187,167,-421,564,-324,-755,592,1000,394,-1000,-400,508,-539,-1000,537,729,394,-557,149,-529,-416,1000,355,1000,-206,-297,-11,575,-549,-584,-434,-688,744,114,1000,214,750,576,289,-23,1000,927,259,736,362,1000,124,649,-1000,1000,-295,83,-263,309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,1000,-816,804,464,520,-403,3,1000,-971,-361,1000,-1000,265,-271,1000,573,637,-129,117,363,-864,-206,-464,-30,130,744,441,191,38,-726,444,-835,302,-574,169,-1000,400,-529,-454,743,746,-383,298,856,-1000,-62,-353,-653,359,735,598,-1000,-1000,-986,1000,424,-362,-1000,-232,696,-1000,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{559,957,-791,-205,410,-276,680,8,-1000,-249,304,211,162,-972,-95,-400,-194,-207,578,-614,-1000,-486,570,261,-1000,45,-1000,9,582,87,-741,-709,460,410,-1000,189,-353,-273,-392,548,32,-657,266,788,-230,1000,151,-47,568,-172,1000,854,104,449,222,32,-764,-584,721,85,-19,-69,-338,-295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,-1000,-396,-334,482,389,179,-1000,-1000,984,1000,-546,564,-345,576,-1000,-926,1000,-106,-1000,607,1000,-1000,384,531,-898,-1000,844,-657,-825,-714,1000,975,-1000,443,45,1000,-1000,1000,112,-1000,-1000,830,-471,490,1000,356,-883,164,952,282,436,639,1000,1000,-1000,-1000,954,-922,-668,-960,-516,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-531,-734,897,-991,488,1000,749,-1000,-88,989,304,211,1000,-972,-1000,91,423,-158,361,331,283,-135,-374,261,-755,871,411,-51,363,-892,535,-1000,855,-1000,-269,-426,-604,-254,-35,-335,-360,152,131,102,-911,898,151,145,-289,-467,950,635,802,-789,-213,32,-134,-206,967,1000,-605,209,-623,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{496,619,166,-747,-117,448,649,-363,118,-823,-213,566,697,-152,-203,359,-481,-353,-642,-81,-1000,275,1000,-549,1000,692,-709,759,1000,-686,-1000,-788,980,-39,-666,112,198,-228,-246,-286,-33,-159,-567,1000,-672,1000,86,-226,39,49,1000,963,561,-208,47,771,-210,695,375,276,-1000,540,-1000,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,-1000,-541,-1000,167,89,297,-891,-1000,1000,839,-1000,769,-807,762,-1000,-225,883,32,-1000,-141,867,-1000,1000,1000,45,-954,631,-876,-24,-1000,1000,190,-306,625,-636,1000,-1000,1000,131,-1000,-1000,1000,-1000,267,1000,1000,933,739,1000,796,1000,-150,1000,985,-1000,-341,1000,-1000,-732,-1000,-493,-44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{20,1000,791,-991,488,1000,749,-1000,888,404,304,211,-294,-575,-1000,91,196,-158,-440,331,283,2,-374,261,-755,871,411,-51,-210,-715,535,-1000,855,-145,-269,-238,-604,270,-400,168,-360,-24,131,102,-385,898,319,145,295,-752,950,635,1000,-364,-48,389,-199,-206,-362,-1000,-605,209,-571,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-361,88,400,-306,410,376,475,-306,-900,298,-816,-380,162,252,1000,452,-255,-635,392,292,205,400,715,-455,997,-69,-818,589,151,-510,-579,-440,349,385,328,189,103,-293,1000,-358,-212,400,-150,76,-230,361,-284,-21,-353,-473,575,-37,104,270,-400,-291,199,-61,142,740,7,704,-255,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-832,1000,814,169,-12,445,459,-820,86,-313,-289,500,-536,275,651,414,34,488,-1000,-520,415,1000,-507,511,-465,749,384,192,-69,941,-139,-89,-105,918,82,-291,-825,131,457,-988,-34,54,-480,-441,788,-236,642,-125,-451,-247,443,-725,-362,25,34,339,356,18,-810,708,-875,-75,-161,299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-706,684,-421,848,-57,608,-58,683,997,726,765,961,-535,803,999,-355,-284,33,524,197,285,254,-926,-184,191,252,561,368,641,-903,-507,-880,-448,-172,923,-856,811,-501,-47,354,-655,-427,-806,-468,-816,-28,781,-575,494,706,-445,-996,-734,929,-154,-674,-193,530,-80,82,-537,-100,-393,890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-208,269,-321,-72,45,309,-494,-216,-144,129,-494,-240,530,494,-513,251,1000,-940,529,179,580,204,-997,-1000,749,-1000,-242,-39,1000,-139,-966,-702,1000,-850,706,-239,-447,376,240,-1000,-194,-91,-357,1000,565,1000,-722,556,-995,134,-520,-1000,283,-319,-107,-642,-738,-1000,732,-567,-931,747,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{180,1000,136,-46,654,955,1000,247,36,657,313,822,-135,18,494,-976,378,1000,-210,559,382,585,-1000,-1000,-705,1000,384,1000,-906,286,-162,-1000,-1000,1000,575,-313,-854,-288,400,-612,-23,138,-1000,-339,1000,-1000,-706,-1000,295,-909,-520,334,-1000,625,-372,1000,1000,-1000,754,-933,-742,141,331,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-40,1000,1000,-11,1000,383,419,-226,-204,-220,403,688,-588,-1000,1000,323,691,-352,234,-498,-276,1000,-291,1000,-555,1000,489,653,-932,-894,-1000,253,226,-71,930,-424,-270,-175,-835,-984,1000,76,-275,33,-248,-875,-312,477,-439,1000,-1000,-382,-1000,-441,-496,1000,425,-1000,-2,668,-131,1000,-982,-718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{911,1000,759,-436,257,1000,781,218,-136,705,-755,16,-650,839,895,-314,1000,873,-669,1000,200,45,-289,604,228,554,1000,-448,-833,1000,-1000,286,373,440,432,961,344,572,8,-672,421,833,-1000,432,859,757,-318,-71,999,286,-45,-425,-692,856,-728,509,277,38,-1000,-713,-1000,406,-1000,899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-98,1000,1000,584,402,568,354,85,219,675,-337,-61,-633,397,255,864,467,521,-1000,301,269,268,-873,-185,350,509,1000,-672,-238,1000,-1000,484,455,1000,1000,388,270,1000,-517,1000,1000,1000,-128,594,-272,625,-420,1000,-741,779,-351,36,-268,1000,288,-137,-376,176,-240,1000,-1000,151,-1000,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-265,13,-780,-752,162,-80,265,-228,-392,-886,1000,78,839,-1000,354,-1000,264,556,-328,170,-275,-79,1000,-1000,-624,969,-899,327,-576,-963,709,-1000,-1000,365,-5,-124,-826,-362,-380,-78,-1000,86,15,-275,1000,-1000,-874,-496,400,-1000,-1000,373,-803,-204,-183,383,23,-198,-330,-271,-101,262,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-785,-55,1000,24,189,-2,-1000,-1000,-836,-663,232,-309,-1000,-78,255,-657,-1000,-253,-470,715,1000,-1000,719,133,-90,-1000,-20,860,-560,-166,-541,-213,426,-538,-872,-1000,724,-35,-1000,-969,272,-343,468,1000,706,555,1000,-43,-188,-83,539,88,125,-473,767,417,-1000,129,73,-113,-825,663,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-567,945,394,-689,549,780,-1000,160,883,-1000,1000,1000,-786,355,-629,54,-1000,476,353,637,477,-470,515,-212,472,-886,506,-1000,-1000,-973,-380,-348,-869,133,-338,1000,1,-433,-240,-1000,719,-40,-267,657,-372,-74,747,-1000,-363,-460,-212,1000,1000,-1000,745,20,1000,-1000,114,-834,-573,482,1000,806}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-28,637,540,14,934,19,595,461,-609,839,-279,-323,1000,-397,1000,-627,-39,235,-371,64,162,-852,-769,124,356,-1000,15,1000,-886,-329,-1000,192,416,389,121,-817,1000,586,-575,-670,519,392,137,1000,1000,249,1000,-724,-331,916,337,-451,362,674,-429,-239,-105,-20,782,180,366,1000,382,-804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{383,239,1000,444,976,-671,2,963,791,839,-311,526,1000,564,403,-951,-458,-375,-76,261,-398,-259,-613,974,156,-1000,123,-59,-886,-1000,-1000,553,695,318,204,-1000,20,-141,-548,-1000,671,497,-805,862,57,732,1000,-1000,-246,382,-341,-888,467,-357,123,368,7,-429,1000,947,535,523,-83,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{690,-1000,-955,-601,-536,-819,-210,1000,1000,183,-210,1000,851,957,-819,1000,-47,-448,664,-498,-856,972,-905,823,-451,57,-288,-925,-284,-1000,817,-764,114,128,645,-952,-1000,1000,-107,-146,-199,-902,-386,209,-447,-123,-111,-924,335,126,-917,-803,-469,-600,462,769,634,577,361,1000,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-976,-1000,1000,-368,64,5,-377,222,-609,1000,1000,496,1000,-489,-696,-760,786,1000,-663,715,929,-335,-1000,-954,1000,207,-930,764,-1000,629,-541,1000,-1000,523,-872,-426,-732,271,926,201,-704,-132,1000,-735,966,-705,758,-498,1000,255,-173,88,125,716,218,-239,-1000,-480,73,-744,751,1000,-905,-98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{539,637,-860,-236,-238,50,641,345,-883,433,-279,-132,733,-755,-151,1000,55,235,421,264,-273,-53,-583,642,-18,-538,-1000,910,-886,1000,-448,192,459,722,31,132,1000,-776,-523,-341,525,-360,137,1000,827,-918,425,137,-534,609,488,-72,-425,568,-918,-772,-624,-47,31,1,195,982,994,-398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{872,-55,1000,24,189,-413,153,939,1000,1000,-165,1000,660,373,147,-772,204,-253,809,-797,-899,1000,-482,1000,-90,-82,98,-513,-1000,-439,-541,-835,426,846,-134,-967,-400,1000,220,-711,560,350,-1000,803,-524,255,929,-1000,-66,-70,-1000,-677,-629,-473,24,-133,460,-906,73,573,1000,318,20,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{156,-640,-39,-601,-463,-819,421,1000,114,406,396,1000,697,1000,601,994,-264,1000,-481,1000,103,-93,-506,823,1000,-1000,-1000,600,-1000,-1000,-1000,-426,-320,375,-322,-253,750,634,10,-146,1000,-977,-239,323,-123,1,-111,-948,256,126,982,70,1000,-600,-537,-444,-871,-191,-1000,973,188,720,996,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-306,853,593,-376,453,-136,-82,-102,-693,-953,520,-95,12,240,-129,330,172,1000,-474,-687,866,-24,1000,1000,161,-436,691,365,506,133,-228,989,145,333,-407,20,41,-977,-297,-71,-729,175,770,-704,-129,903,-581,-817,868,197,-122,-343,11,-352,-425,-621,-946,190,40,-432,-37,789,112,-740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-390,-516,159,55,599,963,234,-598,-794,368,-416,-400,-387,1000,365,-640,648,400,-85,-396,-328,-1000,727,549,-2,382,-260,-778,95,637,466,400,712,-456,-1000,400,737,643,-574,-1000,-294,-28,-36,-400,903,-1000,-208,116,-603,-1000,-344,-400,-452,-17,470,-916,1000,-813,385,348,150,113,514,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{292,704,-185,-421,-232,126,-387,756,252,683,-1000,-346,-189,516,-478,-614,-1000,380,1000,662,-471,662,381,-1000,-939,-166,-1000,-277,939,1000,986,-681,591,-1000,-233,-337,530,114,-617,-604,944,632,123,-542,-458,-394,830,653,-1000,-855,-732,-808,-362,1000,719,145,756,372,64,25,-714,-1000,893,7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-598,96,-793,-955,764,-1000,-732,615,-762,818,1000,-520,-523,1000,1000,-426,-1000,54,387,-99,-1000,-725,441,-196,-1000,742,479,-845,619,-929,-1000,940,-1000,1000,-1000,-1000,926,466,1000,-242,-474,699,1000,60,213,-1000,-501,561,1000,503,407,1000,-1000,-1000,1000,-111,14,-1000,1000,971,220,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-277,943,425,-506,-601,-283,347,1000,-816,225,520,-437,487,720,-297,-1000,-1000,791,-379,210,-164,1000,567,-213,-375,-771,-1000,287,1000,355,1000,1000,558,-687,-602,-916,1000,-986,211,-1000,-768,61,-465,-1000,-1000,246,871,-92,-1000,-1000,-1000,-1000,-521,1000,778,-1000,-76,-49,365,-1000,-422,-611,490,-193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{304,692,50,-556,-342,-135,265,129,428,-638,-1000,69,374,462,-11,107,-833,180,-247,156,561,68,445,39,332,-187,209,-71,84,188,-286,-1000,328,325,-582,12,1000,591,84,-217,106,795,303,-686,-62,924,-503,-553,689,-112,733,387,-497,304,498,-215,-447,-821,-400,-214,402,678,450,-493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,-66,974,127,753,566,-111,39,-112,-196,-699,-191,978,1000,523,816,-241,657,-40,382,872,1000,-677,312,-81,-202,-581,-1000,774,-1000,-693,969,-698,-476,485,875,-611,736,-691,841,-903,617,-976,-411,501,-458,-380,380,-502,-458,-1000,20,739,800,-948,-511,-237,-203,777,-1000,1000,445,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-277,314,-1000,-666,-36,-251,1000,1000,1000,1000,-493,-1000,309,1000,-92,-895,-247,1000,556,-1000,-813,1000,1000,-1000,-375,1000,-1000,-443,-240,-95,197,-148,1000,-705,-1000,1000,725,-379,-538,-1000,417,61,470,-1000,1000,246,1000,-92,-1000,-1000,-1000,-407,-754,1000,1000,-440,733,-1000,635,151,830,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{220,226,95,-441,435,-436,-222,-366,1000,-1000,-140,-228,-304,240,-641,330,724,1000,8,-137,475,-363,-94,423,161,-1000,323,291,-548,133,-929,-328,-112,662,82,400,-417,904,-743,302,344,302,900,-188,62,18,-200,-442,611,1000,-122,835,395,-270,-721,863,48,183,40,288,192,785,-66,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-387,-296,51,389,869,1000,1000,-602,325,147,-1000,959,1000,506,1000,168,-1000,670,-506,405,910,1000,-992,-84,1000,-324,13,-108,165,560,542,666,-142,-633,127,1000,-1000,216,-1000,-292,-783,342,283,-458,577,-20,-195,-507,-1000,-775,-1000,-994,-505,1000,-1000,-13,14,384,-1000,-782,-65,997,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-191,727,-576,1000,28,972,-211,-714,-114,31,-377,-693,-838,1000,-254,886,1000,20,-600,-1000,319,-859,1000,1000,734,-93,-1000,-634,188,939,-364,-21,-826,637,1000,-990,174,-1000,-179,1000,1000,1000,1000,-190,-46,-459,414,-1000,-1000,542,-26,-1000,71,-1000,1000,-1000,-173,1000,1000,480,-565,-472,333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-741,-945,-886,-226,-420,173,-385,923,618,136,662,-283,754,-487,964,878,-642,809,246,174,346,-866,-916,688,-643,-681,967,-482,403,61,-816,499,-592,-665,-965,-298,660,491,-321,174,-139,-793,-50,699,219,-221,445,157,-201,-884,315,513,-756,233,-734,-597,859,57,-443,-156,-922,898,996,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-105,730,1000,783,848,1000,319,295,965,366,475,-540,110,735,310,531,-286,-637,425,-288,1000,830,-217,-390,1000,-1000,6,-914,-160,-17,-626,-1000,831,-227,513,1000,797,-178,281,596,-662,-1000,1000,223,-560,-1000,939,-269,133,105,250,1000,669,-473,400,1000,1000,1000,600,-686,409,-197,766,-83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-853,274,-78,273,214,-1000,311,469,258,-1000,159,368,9,328,24,-664,1000,43,135,-630,-313,-450,-1000,1000,-443,-502,525,-1000,-1000,664,303,180,-134,-873,-532,492,-976,-630,-1000,799,1000,1000,-1000,-539,610,1000,43,1000,-593,-417,396,-39,295,814,-938,217,9,-1000,-398,1000,-1000,765,-1000,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-501,-239,1000,-516,87,-192,35,392,-192,-237,674,-586,-723,141,416,507,276,374,425,405,854,-249,-509,826,-158,1000,-1000,400,218,452,-532,-153,-677,97,1000,1000,-487,-566,-73,-255,-2,392,-1000,351,-606,-661,939,18,-475,-252,1000,217,-671,-962,-123,1000,212,465,590,263,164,-408,170,-116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,-957,808,804,113,1000,-1000,327,962,-442,-574,395,368,954,-479,98,1000,1000,-1000,-6,20,-1000,0,845,1000,100,-1000,-1000,691,-80,1000,-177,-666,-1000,-527,-354,1000,-1000,725,-22,917,-941,884,-609,1000,-1000,1000,-1000,-935,-1000,-36,-1000,-347,919,-53,-924,-979,223,988,-483,-453,-633,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-20,931,-541,582,-1000,-16,-341,963,-93,-858,-467,512,435,-94,177,105,625,-646,226,-613,531,-237,1,-582,-251,819,-943,-818,-426,1000,1000,67,-510,-298,387,-468,1000,-1000,512,-254,456,218,-774,569,642,1000,634,31,-303,742,340,400,119,1000,-407,389,-659,-466,514,-130,-1000,-1000,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-659,55,-375,-77,440,-1000,-443,152,-357,-181,592,-257,-228,-23,-175,-1000,368,1000,1000,-555,-502,-340,-179,684,346,579,-126,-732,-288,772,392,-729,-277,-866,952,722,-811,-1000,-1000,1000,742,525,572,-331,-302,585,163,678,-925,-990,729,823,-503,630,-1000,591,-869,-600,405,722,-1000,445,-460,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-630,1000,1000,74,-153,-986,642,814,496,-53,1000,744,-1000,-766,919,-878,-61,-609,-61,383,-327,222,-1000,79,-1000,215,-1000,-897,405,193,664,-196,355,-122,166,1000,2,229,-440,545,987,-1000,891,370,205,-1000,1000,-572,-648,-903,328,583,-34,1000,945,305,519,-197,-27,-378,-1000,-788,45,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{671,822,168,-234,-581,-612,239,-292,-110,320,-322,-751,284,207,105,4,617,1,708,-10,-574,45,-703,517,-990,455,737,0,-301,576,96,357,-602,-665,985,-552,-300,262,-977,-389,227,743,152,-231,547,586,-403,821,16,428,-307,-961,172,-350,-531,95,-29,528,-44,963,-957,123,490,442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-1000,-1000,-21,502,11,-805,104,-400,-326,-922,-189,1000,1000,-398,859,1000,1000,428,-1000,-1000,-1000,693,-693,946,150,-650,1000,-1000,-661,550,-1000,-645,907,-438,-1000,-823,451,151,-647,863,990,621,543,934,-286,-522,-231,-148,-50,-521,-1000,42,-1000,-791,1000,-711,-519,-371,1000,740,-531,-1000,307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,431,-1000,678,1000,864,-1000,-852,275,498,-400,-1000,-272,1000,540,-446,-426,-505,267,-181,1000,517,1000,-1000,465,1000,551,400,-87,376,590,841,-339,1000,-544,911,506,-1000,-68,-968,-522,-57,874,-957,1000,-285,-293,415,-1000,332,-607,-1000,467,1000,-1000,-993,1000,-399,-1000,-459,-1000,893,706,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-501,826,686,479,-751,-847,108,1000,-1000,573,1000,49,-985,734,216,-142,-483,-1000,504,824,-872,745,109,647,-1000,107,227,-1000,-53,1000,1000,536,1000,-187,285,1000,674,535,-1000,576,1000,-411,735,97,-89,-1000,1000,-920,828,-1000,-315,-287,-489,236,927,532,-705,-967,284,228,-1000,59,97,-333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{881,-26,-766,278,180,-645,-303,-893,-255,970,-509,-367,-445,438,-110,375,995,-90,966,-374,-368,898,505,-486,983,273,389,-217,751,830,211,141,-272,993,102,878,-371,-851,-844,-989,-505,862,645,-335,978,-185,303,-405,808,-316,-660,-173,79,357,-417,-774,562,-74,151,473,228,-310,-591,415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-98,631,665,989,-519,-579,237,920,-1000,691,1000,-82,-134,950,485,-32,165,805,632,973,-1000,157,-441,772,-970,-1000,700,-1000,224,870,1000,483,1000,429,519,1000,1000,493,-1000,-111,515,397,-103,-897,-289,-997,919,-136,678,-960,-821,1000,-280,-251,961,-10,-419,1000,392,814,-718,-205,113,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-1000,-712,-571,-910,-848,251,469,-638,786,-570,-531,-481,730,-1000,905,1000,126,-699,-497,-1000,-371,880,905,354,1000,1000,1000,-383,-486,1000,-143,-534,-1000,1000,36,-558,1000,-1000,-413,686,1000,339,221,-785,-476,-501,-1000,806,-372,-686,-878,-1000,-723,1000,909,-1000,-1000,-79,1000,-705,216,-1000,-659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{190,-1000,-884,-666,377,242,-111,-186,108,5,-411,-479,10,919,200,-344,140,583,184,-268,400,325,1000,-131,421,1000,769,580,-868,554,543,95,-440,-20,116,420,1000,-400,400,-554,733,-400,854,454,496,-476,-50,-745,-248,353,-484,-60,-211,1000,-179,748,400,-826,-270,-339,-912,-523,-1000,852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{421,1000,937,-106,1000,-143,-765,-526,1000,-34,490,-1000,-735,-769,795,-1000,-495,-1000,1000,-184,962,-559,1000,260,638,1000,-230,-564,-1000,1000,70,-671,472,1000,-1000,230,557,-1000,984,-151,711,-1000,937,-185,1000,-453,-576,-178,348,-463,-15,-731,-782,1000,996,59,1000,-413,-641,-1000,-941,-317,1000,939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-616,148,510,484,-620,1000,-1000,-294,-78,-946,692,631,445,772,-143,-712,-900,-190,-1000,-315,40,-714,-1000,160,-236,-119,-1000,363,484,1000,170,-369,64,-173,336,1000,1000,-30,723,497,-12,-1000,568,-1000,-22,1000,1000,105,-182,-1000,-537,262,309,641,1000,-77,-458,-1000,776,-266,-670,-1000,308,-60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-762,444,-106,-61,835,-580,-278,-99,-1000,-954,-561,-966,285,-354,314,-536,571,-211,71,830,-1000,1000,319,72,370,1000,-979,-1000,-695,1000,-86,-97,151,-713,1000,-180,230,-633,-536,827,1000,-829,-914,60,-1000,-1000,-175,-912,-354,908,-1000,487,-1000,960,1000,29,-148,697,398,-1000,1000,213,326,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-496,61,-173,-96,-240,786,283,59,-927,-1000,929,375,547,-867,242,-123,-674,523,145,299,-706,-359,1000,661,501,-47,-188,-436,395,-302,623,-1000,-193,-526,1000,-810,-89,-384,1000,1000,933,-808,391,-1000,-597,-595,1000,94,-577,-710,-453,1000,64,26,-917,524,-791,-653,-48,239,-47,-678,1000,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{475,617,-1000,279,-96,-488,492,-1000,-887,310,-199,-783,-150,-266,-177,475,396,15,-810,684,-573,1000,-185,604,103,978,688,-1000,-1000,464,1000,-962,-641,-856,716,666,964,87,528,215,1000,871,408,661,-372,-1000,777,-47,-970,1000,867,-664,-1000,639,398,465,706,751,-1000,-755,-37,714,12,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{525,433,-400,-761,348,-88,-328,633,-598,-497,1000,-577,949,-1000,89,515,1000,-302,-367,553,-685,1000,-83,-39,706,820,-89,43,-273,-163,1000,-1000,47,406,941,1000,1000,801,-662,895,1000,354,301,542,-853,-325,1000,-67,-1000,588,-532,-137,-155,179,400,927,-651,-233,-132,-1000,-1000,65,-902,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-64,274,1000,-141,-784,-630,-123,-685,-881,427,756,-866,29,-1000,233,1000,-122,927,388,688,-954,1000,62,1000,874,1000,644,97,-1000,714,1000,-875,-800,1000,484,1000,1000,920,-737,1000,1000,1000,-738,-298,-1000,-840,1000,167,-1000,1000,233,89,413,960,1000,660,706,-616,826,-412,-1000,72,-363,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{379,553,-671,879,839,669,171,-367,554,1000,190,-294,-175,-412,143,-756,523,-1000,-486,-212,393,-170,104,1000,6,-593,519,-1000,-1000,694,449,665,-90,307,-606,856,849,-402,-318,-606,533,1000,-123,-892,527,-548,681,566,220,1000,1000,-249,-277,684,-152,9,-66,190,-1000,928,-589,229,85,-269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{655,848,-319,-446,-381,-93,-331,-458,-242,511,610,-809,-161,752,-450,139,223,801,890,596,301,224,941,561,441,280,771,568,-822,536,364,589,-978,496,263,903,567,416,722,968,350,990,685,-367,-688,-837,-156,406,-345,980,490,883,370,82,-90,-39,-203,222,673,-2,-810,23,-694,-321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{102,848,-1000,-446,-765,103,-70,-11,592,640,1000,-28,582,311,654,-439,223,1000,890,-261,259,-440,1000,601,441,-925,23,-93,-827,106,471,-169,793,1000,266,996,492,-284,722,-142,1000,990,311,-656,193,-837,-277,338,-263,1000,1000,553,203,4,-1000,229,-1000,595,-104,50,-1000,513,-694,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,-1000,-761,564,-843,-1000,470,-388,1000,436,693,114,1000,827,-164,114,-655,433,-1000,-1000,-1000,135,472,1000,995,1000,-474,-253,739,-218,-413,-28,-1000,800,463,-571,708,799,1000,-1000,-735,-924,-1000,-53,1000,-1000,-888,324,1000,-658,1000,1000,-84,-144,-889,1000,178,-1000,338,-1000,1000,-384,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-218,-93,-106,-766,-1000,772,-430,-1000,-1000,-328,1000,595,-23,990,1000,-812,285,-450,230,176,-372,-40,-1000,259,586,7,1000,-458,159,-60,-482,-545,-1000,499,-1000,-779,-183,-1000,-345,982,-1000,-559,545,-484,-56,640,218,267,-308,-42,888,605,124,1000,-111,-500,-665,-908,709,920,539,1000,-1000,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-454,148,1000,214,754,-997,-39,673,596,-94,-153,-392,762,-295,-970,1000,-438,-1000,-165,-643,-1000,-1000,746,140,630,-74,-250,971,377,-451,415,-911,466,-192,-205,-206,-279,-370,615,594,-178,-106,419,295,395,-247,573,1000,422,-338,370,-570,641,-1000,-1000,-778,726,-379,420,803,-266,-460,-660,510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-980,13,958,374,917,-1000,591,955,813,-248,-622,230,821,-1000,-997,1000,368,-390,-979,228,-543,-926,636,-749,-307,-843,-1000,1000,-837,-155,1000,55,506,863,853,-604,-363,-75,-209,361,1000,1000,528,399,536,-1000,1000,694,623,115,-423,-667,-494,-961,630,698,1000,-1000,-475,383,-938,462,1000,843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-595,-850,860,154,858,-895,-99,-993,-351,39,28,389,334,-952,77,750,588,-400,-316,982,-1,1000,-786,889,-980,241,456,810,-116,-487,610,796,112,1000,-1000,237,-45,-139,-1000,-582,-683,-323,-85,920,-1000,-733,846,-693,525,-76,964,-1000,1000,-515,-510,654,-837,-695,8,-461,958,-666,351,413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-421,-475,-105,799,772,-1000,-501,-763,-74,983,317,726,788,-372,430,493,-1000,-212,-34,124,-674,168,-423,662,157,414,-1000,-263,1000,758,373,843,593,12,-486,284,713,937,259,-295,-713,-917,-530,-622,-619,529,-230,-1000,388,-76,566,-240,768,1000,-986,1000,-50,-137,94,-778,170,840,1000,232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{889,1000,361,-236,-831,-1000,-886,-467,-92,-1000,1000,-115,-128,104,337,1000,1000,732,664,-1000,-1000,1000,802,-265,1000,822,-284,-276,175,-484,796,-742,-279,150,572,52,-87,441,994,1000,-1000,905,-545,73,-160,371,-367,336,496,-598,-1000,608,186,-496,-72,-288,300,111,-1000,435,357,1000,-329,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-1000,461,-941,858,-94,-323,-99,-1000,-1000,1000,949,51,-886,-1000,-84,1000,1000,371,978,-169,1000,-881,1000,772,800,1000,-705,-997,1000,1000,526,71,78,-455,-463,-1000,-670,-1000,-1000,1000,-1000,727,1000,-1000,-1000,1000,-694,348,248,1000,-967,1000,-907,280,807,-1000,-1000,1000,739,-1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-1000,1000,499,43,-46,-886,-859,407,-1000,198,327,955,-1000,-870,1000,1000,849,-936,1000,367,1000,-554,-471,-980,-472,-800,-276,690,-186,1000,-742,789,150,-860,-392,464,323,-859,-582,465,886,382,920,-126,-1000,1000,482,504,-539,1000,-981,480,-45,-72,-288,-778,-1000,761,-397,958,-1000,-329,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,571,1000,754,622,161,-430,-422,-185,1000,785,1000,121,-612,-194,1000,-945,184,-1000,-481,-53,69,-585,392,-301,-787,797,-1000,1000,179,436,1000,-1000,-966,-342,484,314,347,977,1000,744,-384,1000,82,-770,-9,1000,363,-982,399,-199,1000,598,-802,-211,896,1000,-1000,656,958,1000,1000,123,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{183,-33,179,-261,-83,1000,54,105,999,-103,-646,785,-706,127,775,-853,207,-153,-397,1000,-652,610,-1000,-132,-392,-688,-34,110,271,537,-476,-344,-420,-1000,-28,146,613,119,251,1000,-217,-238,-668,-1000,782,451,-147,-262,-172,-1000,121,-9,-326,-205,889,235,-976,-169,150,2,-287,1000,521,-379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{874,1000,1000,499,-46,148,168,37,858,763,11,1000,784,450,606,722,1000,429,-1000,347,-352,-150,-998,148,-595,-1000,658,-118,678,752,-82,1000,-1000,-1000,211,115,1000,883,984,544,1000,-685,-500,-895,-1000,-373,1000,-1000,-550,-957,1000,1000,1000,-1000,298,-157,-827,230,-991,842,-271,614,-808,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-1000,260,489,-11,1000,650,-508,-43,-659,1000,719,1000,-1000,-894,1000,-1000,1000,-1000,-1000,1000,389,-1000,1000,-80,-916,1000,-1000,1000,806,-488,-567,-1000,553,1000,1000,639,50,1000,1000,-246,1000,628,1000,-1000,-954,177,-383,-1000,440,1000,418,916,-1000,-1000,1000,561,-186,1000,199,1000,248,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{500,124,412,-536,909,161,-420,-422,-658,1000,785,626,182,-612,102,1000,-552,789,-1000,-17,-652,420,83,392,713,-355,797,-600,1000,179,-598,1000,-987,157,-810,776,-392,-145,108,610,654,-781,1000,746,358,539,1000,662,-982,629,-134,905,607,-415,162,779,1000,-1000,1000,540,753,222,177,-844}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{183,-185,-198,-261,-137,-149,-313,-164,-419,150,-230,-578,-981,99,-23,-743,183,61,225,222,-644,-598,-6,447,561,-390,-766,-322,-956,755,-476,40,629,-520,-953,819,-779,229,-354,-86,227,453,986,223,221,-803,-290,-645,763,622,317,-190,992,-183,-501,45,-679,-492,644,668,-636,-784,341,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-753,-352,-545,-761,733,201,243,-1000,673,-307,-1000,208,-345,-113,1000,-1000,-576,143,540,1000,-860,994,208,1000,383,730,204,608,674,-1000,-1000,-191,-160,-269,-1000,-85,-168,-620,461,-652,-280,-413,610,1000,161,-838,1000,917,679,444,-454,-887,-378,992,1000,-376,540,810,-47,-337,553,721,493,550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{762,626,1000,44,-438,-760,395,4,-610,713,-146,-697,-463,682,-1000,135,-806,1000,-782,-12,476,764,-313,8,489,-700,-28,-179,-754,810,-457,812,-581,-730,179,1000,-1000,907,-546,763,758,-834,1000,98,-1000,1000,1000,-10,977,1000,-1000,215,-520,-501,-280,186,67,-1000,555,1000,740,653,-1000,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{409,1000,1000,158,81,-602,225,464,655,1000,-580,630,-78,1000,694,35,128,922,-128,468,-821,-796,-586,-245,-193,-675,1000,506,-133,1000,-443,1000,-63,-1000,-1000,-86,126,328,1000,-878,394,-1000,-437,-1000,-724,936,1000,131,456,-381,-524,1000,-60,-92,437,-678,-121,-556,-379,726,-305,1000,-645,-818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{157,1000,-499,-281,-835,353,902,464,-802,-1000,1000,29,1000,1000,-361,-945,-1000,207,572,599,202,1000,-1000,1000,-836,-92,-771,27,1000,1000,1000,-1000,262,-1000,1000,437,-668,-651,-1000,-645,364,-402,-1000,920,262,1000,734,645,-1000,1000,-416,-349,1000,-1000,457,634,173,82,1000,-1000,773,1000,988,232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-641,846,39,-595,-1000,311,-217,1000,-317,627,-813,-24,-1000,1000,1000,-55,-417,281,605,-37,-1000,216,-331,-592,-442,-1000,460,1000,438,325,366,-451,1000,-1000,893,124,-940,-1000,-612,-218,614,-585,226,-403,948,-746,1000,1000,997,-1000,-1000,-476,-835,-615,721,633,439,90,434,48,41,403,-70,-310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{757,1000,712,-361,223,-200,84,-211,1000,-112,-427,468,-85,238,-815,-64,380,-969,-228,-701,1000,-885,-48,-1000,-517,450,927,294,-398,1000,330,538,-1000,950,-980,1000,-238,297,1000,129,-1000,832,502,285,793,169,-319,65,838,296,805,-99,-1000,235,-1000,-1000,-821,404,-741,370,-160,-967,920,-926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{29,922,-161,83,54,-191,-348,567,52,-66,562,478,153,-647,418,514,74,-1000,-75,-126,-336,895,-730,1000,494,691,-715,-274,928,423,-528,802,209,-152,834,1000,340,-138,-1000,-1000,1000,-655,646,-79,-362,-819,214,-275,134,762,-153,1000,-590,-762,1000,174,-302,-30,1000,-107,-192,94,-319,154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{557,1000,138,-347,-434,-182,436,242,1000,-1000,653,334,1000,895,-1000,385,-844,-105,288,450,987,392,-842,470,-823,302,211,-261,216,1000,1000,-165,-745,-244,763,1000,-877,-169,-540,-1000,-1000,495,-638,746,-870,1000,186,540,-1000,1000,-210,-545,307,-1000,-398,984,-130,-2,1000,-1000,646,726,1000,-207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-906,506,577,-151,-665,-849,728,738,1000,-1000,1000,190,370,1000,277,648,646,-491,-863,-1000,-532,1000,-115,-1000,200,1000,286,600,198,319,1000,-1000,-617,263,894,485,-626,-1000,-845,-770,-446,-435,89,-297,-738,1000,1000,498,-221,1000,989,-1000,1000,71,169,-1000,-828,91,900,613,1000,835,356,-888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-403,-309,-176,-613,-689,307,-23,623,-768,821,486,701,221,-1000,669,-210,-1000,-331,-453,-640,1000,-224,-569,371,1000,-1000,-190,-177,897,452,-541,-5,412,1000,826,-941,-933,-1000,-764,-1000,-861,-1000,-515,-974,1000,1000,-213,-112,999,-348,-458,374,372,-580,197,-306,117,499,367,526,-220,834,348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enable(com.fasterxml.jackson.databind.SerializationFeature,com.fasterxml.jackson.databind.SerializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{129,49,-284,118,-211,-43,380,-271,479,-510,653,740,895,443,-235,81,-78,-833,389,427,1000,793,-842,959,-667,973,-746,-373,645,692,206,-165,-163,16,539,283,-169,-169,-812,-34,866,125,-858,263,-431,896,620,280,-143,1000,-165,388,249,-275,277,90,-130,531,739,-578,338,-271,636,-213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-671,221,659,336,264,-403,-332,-1000,1000,-1000,-696,271,-547,441,-1000,1000,-491,583,-225,722,-43,-11,-602,-730,-466,805,-505,-1000,1000,1000,395,132,-599,-571,-1000,-246,313,312,1000,-106,1000,1000,-1000,1000,104,876,-1000,-439,914,257,1000,-666,1000,45,-1000,-943,1000,-253,-1000,1000,-400,118,-680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-591,-351,-1000,-210,7,151,-628,324,868,-1000,1000,759,-419,-1000,-182,193,466,-222,-200,792,-478,253,257,1000,-415,-899,-14,929,-461,-1000,760,497,1000,-736,448,1000,-1000,-392,2,598,164,-448,-604,-338,400,-141,-446,-311,-1000,-1000,1000,59,-241,-17,-72,-382,885,414,1000,-1000,78,-111,-77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-829,-67,289,164,-792,614,-1000,837,268,737,-1000,-728,1000,-340,982,602,-103,1000,-1000,-681,1000,653,-1000,310,-15,-103,731,-1000,697,-1000,-444,-78,163,503,320,-867,-675,1000,609,929,201,203,432,-99,-339,-1000,21,-948,-268,1000,975,-1,331,-529,-1000,-70,172,133,1000,-670,-947,-929,23,991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-386,-728,286,-581,-1000,-5,-564,-362,1000,-1000,581,-679,-1000,598,476,-500,287,-983,83,76,-1000,-799,86,-715,-1000,-188,-44,347,-142,-623,-810,-709,-459,1000,205,1000,-291,-744,-223,153,-188,615,-1000,-502,-627,338,-557,1000,10,-55,-253,-1000,-281,-238,-1000,599,744,-66,1000,89,-434,229,-835,-155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{371,487,-201,179,252,-970,93,-122,38,618,1000,-612,-276,239,149,-868,406,-1000,-201,547,-37,-90,937,84,-353,15,-831,190,-1000,224,-1000,62,953,683,-1000,-102,1000,-569,-371,64,-696,1000,143,-1000,822,-626,1000,-776,-1000,504,61,1000,433,-313,-5,-1000,-78,446,187,-125,1000,400,1000,-945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{817,1000,-295,513,-121,17,-3,-233,-307,-1000,-49,-536,-352,-567,-118,-74,887,-348,816,-1000,371,177,297,-1000,-562,-584,69,124,808,950,124,407,-227,103,-63,-533,-598,518,100,291,-175,906,-50,-44,1000,-961,834,-289,-74,-231,-494,222,-25,-981,214,196,174,1000,-737,-551,-198,149,-557,179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-460,-1000,1,778,-565,-468,645,-64,538,-1000,-293,-774,-1000,129,655,-1000,-108,-1000,1000,592,-637,-1000,830,-740,-877,488,-1000,1000,572,-607,210,695,687,1000,-638,1000,-57,-720,-915,-28,1000,243,-214,-365,-172,432,897,1000,-545,-547,-707,-802,-748,460,308,152,1000,800,78,-580,-16,1000,1000,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{624,-1000,-1000,703,460,-319,516,-758,-73,711,-1000,127,-46,407,-511,-421,-67,-983,723,-429,721,-733,86,-894,877,-373,-672,-94,-828,1000,255,1000,27,-351,-1000,-16,1000,-496,-165,-864,-188,919,1000,-1000,1000,338,1000,-1000,-484,-566,-629,1000,-700,-18,400,-1000,558,1000,-1000,-252,593,908,-835,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,516,659,-631,-815,-411,-711,566,1000,-1000,1000,-915,-140,146,291,-63,-468,392,-416,-436,922,267,-180,578,-811,1000,-1000,347,350,-1000,-1000,-772,-13,1000,1000,-1000,-198,-230,250,-1000,-181,307,-1000,986,-909,-442,-645,826,-364,693,-382,-866,-142,-1000,451,1000,1000,1000,756,1000,-1000,-1000,-206,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,-1000,839,-105,304,268,164,-854,1000,-1000,-665,-551,-686,987,194,1000,-1000,1000,394,-931,641,1000,720,1000,-1000,152,1000,-228,-626,397,1000,1000,1000,1000,-1000,-570,1000,206,-696,36,551,342,-685,-1000,-795,1000,226,-1000,306,-6,92,62,-1000,836,1000,552,-1000,511,-227,1000,-856,-252,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-218,294,-181,338,89,158,-627,206,-1000,-283,-1000,1000,-1000,-1000,1000,1000,-1000,-1000,-547,-261,-648,-24,127,1000,1000,210,664,-1000,188,-908,499,3,514,1000,1000,-911,-572,-1000,134,824,1000,-732,-1000,1000,0,-705,761,1000,-1000,978,1000,1000,649,-1000,792,-134,-627,467,1000,-756,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{615,1000,-1000,304,-653,433,-913,-659,-1000,20,409,1000,-1000,-1000,-965,1000,1000,-30,1000,929,406,-134,228,1000,1000,-116,982,642,420,-1000,1000,1000,697,1000,-65,-531,1000,35,1000,350,-263,398,700,1000,-129,-1000,67,949,-1000,-505,1000,1000,826,542,838,6,-327,468,-456,1000,-420,-939,254,-653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{775,1000,-198,794,617,444,219,-402,-891,-1000,-165,648,-389,-1000,1000,927,216,-1000,576,454,244,275,1000,1000,-9,-665,375,-495,155,-1000,446,1000,817,1000,1000,-864,42,854,420,-456,940,-942,-638,652,-436,-473,1000,1000,-1000,842,239,993,-136,-609,1000,-118,500,904,-43,-1000,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{720,1000,-1000,279,489,1000,-546,124,-1000,459,-1000,212,-779,-686,1000,513,-23,-1000,230,-107,-849,432,1000,1000,1000,-525,397,223,71,-1000,611,1000,918,1000,-306,-1000,-1000,-173,1000,135,980,-35,-616,413,-810,-589,1000,564,-1000,1000,527,1000,410,-1000,1000,572,587,-362,511,-234,1000,470,-252,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{434,1000,189,989,-789,315,-62,3,-1000,602,-1000,1000,-887,-1000,-75,518,410,-617,996,932,-701,376,105,859,1000,158,566,532,231,-912,163,1000,541,1000,-1000,-663,393,617,1000,-565,1000,1000,1000,-5,-448,-891,887,254,848,-726,627,-787,946,-1000,79,967,-66,263,661,399,-593,-1000,1000,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,-620,129,-525,880,115,745,-1000,416,-613,805,-1000,462,374,1000,811,-1000,1000,1000,-1000,-785,1000,1000,1000,-1000,594,1000,942,-1000,1000,667,882,1000,-905,-1000,884,961,833,-645,1000,-732,606,278,-1000,-1000,1000,1000,-1000,-969,276,1000,1000,-1000,876,839,352,382,1000,-53,321,-930,-1000,-983}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,-256,-313,131,485,-565,439,-552,1000,325,-659,-766,-1000,-400,-233,943,-286,781,629,-780,43,217,670,1000,-930,440,-732,456,-955,1000,211,572,993,916,-519,366,577,1000,1000,1000,277,784,-569,-868,-1000,829,535,-926,989,276,1000,1000,-1000,551,149,402,-214,854,1000,-400,-930,948,41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-414,259,925,282,-210,-1000,-511,-531,-548,-720,857,-1000,-1000,850,589,-1000,-33,1000,-1000,-331,709,-498,311,1000,700,1000,110,-733,-208,-114,-242,64,225,1000,-187,-520,-1000,36,1000,95,-1,-1000,1000,881,57,-25,252,-24,-71,1000,81,118,-304,-268,-433,-930,694,173,-771,707,1000,-310,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{173,-71,446,-951,-662,576,47,1000,1000,1000,16,-80,-261,-84,-573,400,-888,-1000,290,184,771,135,421,-1000,1000,-930,555,-222,-128,-148,396,-170,-1000,145,-509,10,445,-901,-331,1000,483,-113,-874,458,-749,400,1000,-52,-136,252,650,-432,-98,417,612,1000,498,39,-46,-902,-539,834,701,909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{932,660,148,1000,-528,-880,578,583,1000,-414,1000,574,671,544,-913,1000,-597,-640,1000,-318,16,-571,-1000,1000,-637,1000,534,628,-380,-536,-521,1000,1000,527,698,273,-250,1000,491,1000,-304,94,90,-998,814,935,173,323,593,-1000,-111,-722,-542,-581,-567,894,-1000,1000,99,-299,-1000,260,-409,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-454,-40,-34,-371,42,-263,-627,224,-553,-67,374,362,-675,265,821,-1000,851,-983,-839,294,-106,-92,-939,-1000,-659,-8,-718,103,192,-46,-583,-145,758,-896,540,1000,-985,-1000,518,-819,-804,386,1000,-677,491,-1000,-1000,-78,413,1000,-18,-221,1000,-339,-579,-734,-809,774,-109,118,1000,-1000,492,-676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{140,-781,250,-246,286,-621,-261,-291,1000,550,106,198,-644,102,-483,1,-1000,-907,-276,-765,32,1000,1000,400,20,-793,282,-1000,513,447,-455,-602,-1000,-242,-996,1000,-530,-536,-367,1000,-651,1000,1000,934,-728,-1000,-45,-1000,129,124,469,-689,349,312,1000,1000,684,-94,-820,-689,-753,-178,618,341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{514,-254,527,239,239,-1000,-347,833,1000,-482,921,602,-957,1000,-1000,-620,-1000,648,-600,-1000,-561,1000,1000,-235,962,-1000,757,-744,627,-25,-495,-515,211,-749,-77,364,-941,1000,1000,1000,-97,1000,-892,707,-496,-396,-151,-1000,262,262,-56,-999,321,-1000,957,479,-811,-580,-1000,-1000,-1000,-71,-1,-377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-413,775,170,599,-623,-282,17,-561,162,6,277,602,-661,170,640,-257,234,-1000,-221,-250,-359,-674,-1000,-35,-471,16,349,982,-533,-1000,-432,792,1000,-749,597,480,-507,-522,57,-312,-97,464,-399,-905,951,-333,-554,265,1000,262,-238,-307,261,-752,-1000,-1000,-921,924,130,84,105,99,-1,-377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-363,536,253,-382,151,130,-187,688,-778,-524,158,500,-505,-335,544,-400,896,471,-599,894,475,-692,-1000,-1000,-715,214,-951,521,919,-177,-801,-506,-724,-1000,920,808,-776,-1000,-760,-1000,-419,-97,450,-835,-719,-1000,-446,522,269,697,434,-110,1000,-674,-1000,-134,-1000,534,479,-183,1000,-1000,816,-538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{807,-395,121,-226,-431,844,960,-30,-74,153,686,1000,1000,149,-882,222,-496,509,-146,1000,-497,-496,-1000,-856,-170,1000,-321,462,-1000,837,278,-200,-813,408,-266,1000,304,-536,-344,-828,-214,-459,636,-757,-1000,-194,-104,366,-325,-278,869,661,542,979,-936,518,213,1000,588,1000,934,791,1000,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,240,544,299,1000,690,1000,1000,-669,657,-721,-974,-123,-596,-1000,1000,726,1000,572,168,26,929,-809,1000,-687,1000,-585,-185,-939,-884,-810,-122,-1000,139,879,157,498,491,-1000,-171,269,-691,-642,-942,-1000,1000,807,899,-1000,-877,644,-355,-341,1000,-855,1000,-293,-432,626,-542,962,526,423,-707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-426,1000,-834,-296,422,-290,-267,689,202,89,214,-1000,-301,795,400,421,214,278,-1000,-383,-727,863,-277,-412,471,-1000,564,-615,-555,-333,588,-400,-1000,-450,213,-683,1000,658,-382,-531,-359,262,3,-615,1000,20,-853,-741,-1000,-749,-92,777,-227,-855,532,-1000,314,-947,-631,660,963,-659,-529,35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{689,-696,764,489,735,-830,-422,-430,-796,-583,309,21,-32,547,600,216,55,-297,-113,698,196,124,-11,-749,-125,-617,663,105,496,596,313,-807,-497,404,703,627,867,-983,-548,-291,-791,818,-594,668,12,-859,-460,-931,882,-299,-46,-633,599,-153,-249,-553,-150,-663,-923,135,-68,635,-510,672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-519,1000,-817,-281,-364,1000,-637,1000,-443,15,-641,-852,-1000,-71,-217,418,740,1000,334,-1000,-1000,-38,658,-894,1000,-73,-711,-1000,-1000,-41,1000,22,-284,-1000,610,-932,1000,584,777,-911,-1000,506,-169,337,924,-193,-981,-341,610,-1000,-1000,1000,-1000,1000,-417,-451,725,-282,394,923,721,-1000,517,286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-568,836,-716,489,-52,-830,-212,641,98,318,-363,-891,-879,-36,425,-262,55,595,142,-621,-745,1000,610,-685,318,802,-865,398,-255,-1000,304,602,1000,365,-1000,-132,916,396,-216,-177,-1000,-883,68,668,454,818,-460,673,-366,-1000,-1000,-633,599,400,823,-987,-125,696,365,256,972,-1000,-510,-206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-486,1000,495,-497,579,1000,-245,805,276,-47,490,-814,-674,1000,-307,629,72,438,-531,-920,-689,463,895,-1000,305,-1000,-906,-1000,-656,-799,428,145,-1000,-177,-759,-620,906,-651,528,-1000,-815,-185,1000,523,977,987,-1000,830,-706,-508,-709,-155,-132,400,-855,-1000,-253,-318,-72,144,-96,-1000,-431,526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-426,1000,-566,79,396,-351,-598,419,-162,-598,222,-800,-257,1000,400,291,33,278,-746,-352,-1000,863,-277,-487,623,78,363,566,-555,-430,588,-471,-687,142,-119,-538,1000,-90,68,94,-359,466,-495,-711,1000,160,-577,-798,-1000,-117,-137,506,-1000,-855,727,-1000,431,-580,96,660,937,-898,-582,-267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-262,-476,325,-951,782,-186,-603,-920,481,-45,488,-495,655,429,-424,-369,173,-713,-169,-528,911,-47,683,506,982,-228,130,-675,382,-466,-945,-891,-526,-132,944,-793,-725,-172,-631,-846,384,247,871,413,562,-464,477,834,-516,462,961,-626,979,-860,-786,-30,195,9,-444,-378,-443,839,724,736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-93,615,-367,564,318,-847,-1000,-266,-231,879,-84,-727,-347,-207,1000,317,-534,-639,-320,108,-827,1000,-588,-295,318,229,178,119,3,-1000,-401,-678,64,1000,-6,1000,1000,-282,427,1000,245,-1000,-1000,199,-75,286,1000,-298,-1000,-1000,-1000,-261,940,566,984,-1000,-356,52,823,-223,1000,-393,-452,344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "findAndRegisterModules():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{799,-272,-123,-266,189,485,-805,623,280,1000,-417,79,-301,191,406,-601,-923,-1000,521,-1000,-157,-1000,1000,284,487,625,-691,1000,398,-880,-374,1000,-865,171,98,291,-753,204,463,-521,-152,318,126,-1000,-294,-260,-308,715,152,-283,811,260,250,-413,942,-767,259,561,-116,-725,-745,154,-633,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "findAndRegisterModules():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-16,-462,248,164,219,-811,-680,-367,-1000,345,459,-603,1000,-1000,-1000,-209,1000,499,-709,-456,-583,1000,103,351,1000,-317,-689,242,10,-1000,941,-501,-667,-862,-948,87,451,188,449,1000,-847,-592,-280,1000,518,-665,-192,-82,112,-44,-616,-30,-794,-272,638,-242,-625,548,241,401,762,-451,1000,-291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "findAndRegisterModules():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-455,1000,-1000,254,397,955,131,-552,910,-846,-165,-17,-1000,222,697,-138,-1000,-492,584,-158,-965,-491,524,-143,1000,28,4,217,-576,-820,489,-626,1000,1000,274,984,-220,-355,19,-580,-725,-1000,-556,-1000,-651,885,-143,-311,61,911,-1000,1000,-335,248,153,57,350,174,626,114,-1000,1000,-186,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "findAndRegisterModules():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{804,1000,-1000,919,-560,1000,703,-371,153,-1000,-710,-103,-537,658,1000,605,-1000,-1000,-62,-63,-107,-1000,531,581,487,-736,-17,1000,-299,-1000,-513,283,-272,1000,1000,1000,340,-1000,1000,-1000,-1000,789,251,-1000,-1000,812,-1000,-989,-872,1000,-1000,260,-1000,-1000,1000,544,496,-295,-640,55,-1000,1000,-430,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "findAndRegisterModules():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-791,-121,919,134,-798,-724,541,944,-937,994,-660,-288,-300,-238,-473,-50,967,-48,-87,214,-253,-60,390,-57,-861,131,-295,455,94,-593,293,-16,-630,-175,-847,-372,101,805,-201,382,-545,-242,-858,62,-152,115,880,837,47,93,-613,-642,83,131,-556,-5,-620,-46,-794,-407,732,-572,753,-673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "findAndRegisterModules():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{746,-64,-122,314,-361,965,-413,-991,1000,1000,-23,524,962,-608,-745,1000,-853,400,-1000,518,-225,824,564,179,535,-1000,-553,118,569,-363,-505,-200,1000,-626,-394,21,642,-569,-94,728,-668,889,490,1000,-1000,710,-1000,-866,-116,-861,-184,35,-701,373,-257,-913,1000,647,1000,668,1000,-566,207,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "findAndRegisterModules():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-308,1000,-1000,324,-531,418,-190,-558,662,-817,-460,-94,-656,617,1000,230,-890,-1000,78,282,-1000,-1000,497,119,267,-92,403,1000,190,-1000,-6,-463,515,1000,-406,1000,-202,-239,579,-102,-891,-1000,-923,-1000,-1000,950,-103,543,-530,1000,-1000,742,-790,498,464,342,416,754,477,29,-1000,1000,-230,-740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "findAndRegisterModules():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{127,-1000,-742,308,-321,-864,382,-1000,264,352,120,-389,1000,-32,1000,1000,-523,-17,-309,-405,-395,1000,865,1000,-904,-53,360,1000,424,-1000,63,957,-272,1000,-1000,13,-246,-470,204,1000,-407,-1000,-1000,-1000,-1000,1000,-1000,-287,-1000,-21,1000,316,-851,1000,1000,-188,856,1000,474,809,-1000,258,-562,-840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "findAndRegisterModules():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-3,320,-602,639,1000,189,262,-148,955,70,539,-296,-1000,-263,831,-420,-722,552,1000,-766,-635,195,631,557,760,467,-538,121,-972,-484,1000,34,1000,-97,599,-687,-156,-191,-895,-258,-577,-962,-747,-1000,-216,661,-74,-1000,489,823,-346,999,16,228,-103,-830,558,371,714,260,-473,-52,-222,662}));
    }
}
