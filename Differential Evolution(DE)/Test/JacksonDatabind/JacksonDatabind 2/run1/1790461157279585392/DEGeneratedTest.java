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
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "append(com.fasterxml.jackson.databind.util.TokenBuffer):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{-802,1000,708,-1000,713,-355,955,292,763,-96,42,-202,664,-1000,-503,1000,3,-107,-1000,-1000,1000,10,243,18,882,-714,-88,-700,869,-1000,-590,1000,251,-438,-1000,-342,289,60,-676,889,591,-731,-1000,568,-541,-810,-346,-180,994,-998,18,309,901,-338,-938,-920,29,435,676,525,-39,263,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "append(com.fasterxml.jackson.databind.util.TokenBuffer):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{-1000,-1000,396,523,232,496,590,-1000,-1000,711,-1000,-949,1000,431,1000,273,-545,-11,-246,163,-652,297,-400,-632,-714,258,934,-91,1000,-6,726,-87,591,903,-544,382,-954,-531,1000,176,-1000,-1000,224,-1000,-1000,-494,279,-815,-293,-102,1000,709,-572,-1000,470,191,977,959,1000,185,-273,-463,501,-503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer$Parser", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser():com.fasterxml.jackson.core.JsonParser",
            new int[]{8,-737,843,211,16,139,515,167,433,-367,71,261,-663,-921,741,577,7,445,648,-635,361,-530,-418,-747,642,255,-443,357,-746,828,912,-402,-122,-24,217,922,-703,49,860,-998,737,354,-195,-506,-65,-721,718,-75,636,339,451,924,782,-36,664,-389,252,-948,-698,-248,-305,36,-447,494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser(com.fasterxml.jackson.core.JsonParser):com.fasterxml.jackson.core.JsonParser",
            new int[]{-447,1000,-216,-75,364,224,1000,665,-1000,1000,197,923,-357,1000,1000,745,-269,1000,-1000,638,1000,-80,-417,1000,283,974,-1000,1000,176,596,-1000,-237,-617,-1000,842,-191,-980,-575,161,505,-706,601,-813,-1000,983,-216,-550,1000,587,-666,-1000,-997,-1000,1000,-976,-19,-622,1000,512,401,-1000,1000,-568,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer$Parser", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser(com.fasterxml.jackson.core.ObjectCodec):com.fasterxml.jackson.core.JsonParser",
            new int[]{-715,-761,893,-502,174,220,835,-121,976,435,-63,494,987,389,972,438,-423,-53,38,414,662,148,456,-46,210,610,790,-104,-177,-579,718,-590,-127,-120,-756,-74,-861,-445,435,-102,450,-294,763,502,-547,-641,979,-915,294,32,434,478,-266,437,746,94,27,-617,823,-807,750,-498,379,-374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "canWriteBinaryNatively():boolean",
            new int[]{-820,-458,-471,287,-690,-913,265,834,107,71,-185,841,-468,126,139,-265,834,-429,500,812,221,312,586,-605,739,13,914,808,-947,995,-814,-979,-285,631,-747,-526,-20,476,799,264,-742,832,948,691,905,-2,-382,945,178,-930,633,-361,951,-164,354,839,-991,-962,910,-756,-350,-925,-7,-714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "canWriteObjectId():boolean",
            new int[]{1000,1000,944,405,729,-628,-341,354,1000,-409,-500,-1000,-438,-1000,704,-551,-564,861,521,-173,-313,-939,198,-1000,1000,389,228,582,-601,-604,-460,-1000,-1000,427,771,460,-172,193,-328,300,-708,224,-341,1000,-1000,353,225,-606,618,-177,424,-1000,-703,1000,162,-620,789,1000,-582,-73,-726,986,1000,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "canWriteTypeId():boolean",
            new int[]{-119,1000,134,-915,-315,352,687,-506,-20,-857,1000,829,247,-979,-274,1000,896,-251,-356,409,-245,1000,1000,-335,990,1000,-1000,-139,1000,-949,121,577,-279,222,18,-189,9,582,-445,-128,1000,-1000,335,71,-205,-464,-638,216,-232,17,-152,-539,-268,831,-777,-674,-539,257,-800,-403,730,-251,195,-840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "close():void",
            new int[]{356,895,13,197,-743,268,-769,681,1000,-510,192,-197,-498,-169,-930,448,-34,-91,-4,75,762,-345,-365,672,427,87,371,563,-563,-687,961,-57,-514,540,881,256,421,-315,-1000,-101,-171,-826,211,-65,-974,-620,538,-67,-181,176,414,-991,-1000,520,734,369,-466,180,23,-552,-569,-80,612,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "copyCurrentEvent(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{-1000,1000,658,1000,-1000,-488,-906,1000,1000,1000,-1000,1000,635,-169,819,554,-1000,-1000,1000,-419,587,-217,-73,-1000,1000,-345,1000,1000,1000,-1000,-1000,829,1000,183,-341,885,-440,-250,757,-1000,182,-1000,-718,763,-1000,-780,624,-1000,1000,-1000,-115,-1000,-743,-564,-284,-1000,273,-930,-59,-550,-1000,87,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "copyCurrentEvent(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{-864,374,214,-846,-160,-688,-642,-848,643,-407,-243,250,34,287,893,-951,-27,808,495,26,568,-67,393,-965,-529,751,981,261,117,739,-757,644,93,962,-192,-852,-716,781,-938,142,765,372,-967,653,-153,894,403,262,175,-832,843,20,320,401,-365,934,-101,629,473,-752,983,353,-725,-907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "copyCurrentStructure(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{-138,-887,-766,-722,-811,534,513,277,-839,-885,156,-657,-59,-367,625,-624,-900,-943,-79,46,154,937,691,750,538,580,295,424,817,-325,-540,961,-744,443,960,-794,-355,972,99,-189,-341,894,-327,-412,-18,281,-433,-582,919,285,-824,272,-717,-141,-562,-566,-20,676,403,-360,-115,319,22,-481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "deserialize(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.DeserializationContext):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{268,1000,269,70,673,1000,345,-582,-434,73,230,719,133,-814,-1000,182,322,509,-740,-391,-340,-550,-732,736,-1000,-114,-478,294,1000,591,447,812,553,-337,-167,-368,-7,1000,243,124,56,-255,1000,227,-894,-360,-988,-242,635,-1000,1000,-249,-518,-611,-256,1000,784,793,-666,-444,573,99,32,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{-795,778,-501,212,-328,-755,456,594,-364,-454,811,-269,923,-709,339,351,414,-680,-492,-965,98,706,300,212,-877,246,142,362,761,-174,505,-212,-831,255,-704,-709,414,-566,231,157,-646,47,-925,-54,636,-641,-197,-214,369,-829,-421,-944,786,-524,-262,706,-361,962,918,765,976,-859,-94,-948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "enable(com.fasterxml.jackson.core.JsonGenerator$Feature):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{427,-338,232,92,940,-588,869,-262,-637,337,-542,-241,285,-396,-762,-882,333,1000,-1000,-20,-1000,-1000,-82,276,1000,-56,1000,586,-298,-1000,1000,-927,-890,-982,-469,-395,450,861,777,-955,-259,730,224,-460,937,-436,-292,-478,1000,741,1000,-494,-798,-92,280,597,-1000,146,297,-268,1000,-889,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "firstToken():com.fasterxml.jackson.core.JsonToken",
            new int[]{-203,-653,-412,-1000,-103,-184,296,1000,-765,-698,347,612,-111,-583,-769,-261,-649,-1000,267,-645,100,-601,-364,-403,1000,-18,-63,520,-496,-312,1000,102,-186,103,-208,216,1000,108,-377,934,-394,-1000,409,503,-1000,-181,-311,1000,-894,199,613,-8,32,-1000,62,-1000,-666,-1000,-1000,-1000,-323,729,398,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "flush():void",
            new int[]{-630,544,-446,44,-320,-203,35,-202,-611,-140,978,-218,502,-401,-591,-866,-405,785,-699,-28,813,-227,-363,-518,563,-469,567,558,365,-791,761,377,-652,236,-165,-817,929,372,-401,-20,-588,114,807,-144,-620,-652,59,-803,74,300,562,208,259,-571,575,812,481,186,523,292,-952,294,15,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "getCodec():com.fasterxml.jackson.core.ObjectCodec",
            new int[]{-961,-884,-237,-27,786,-734,-412,-1000,1000,528,-816,456,1000,-628,-4,1000,814,-52,236,228,1,-1000,-449,215,143,-624,318,-689,324,-230,747,-146,1000,-414,-36,88,1000,164,-98,611,645,780,859,-1000,-1000,289,181,266,819,-467,-696,527,-570,546,99,156,-260,370,-729,-1000,205,92,-1000,616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "getFeatureMask():int",
            new int[]{-437,-74,614,-333,-812,8,-404,-599,-233,-147,169,205,-94,443,397,-362,535,475,616,-222,-204,-24,-161,660,552,634,1000,698,1000,1000,200,-765,-888,-1000,259,-111,132,-875,-265,-449,276,-174,1000,-513,185,360,-979,45,104,-37,-279,-987,34,1000,199,-237,-419,194,334,-461,-341,-630,854,-242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.JsonWriteContext", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "getOutputContext():com.fasterxml.jackson.core.json.JsonWriteContext",
            new int[]{472,880,-42,484,-614,-343,-332,1000,286,-322,-1000,391,-1000,-31,993,-733,174,1000,1000,671,-1000,-1000,2,-406,-1000,-1000,48,460,631,1000,1000,-902,-630,-1000,1000,417,1000,1000,562,-68,-369,-269,14,1000,-824,514,124,-760,1000,-1000,974,897,-841,-730,252,1000,-1000,1000,868,889,140,-250,-477,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "isClosed():boolean",
            new int[]{-404,-11,94,980,-93,933,-356,600,-629,-950,-925,972,291,-537,174,549,-835,560,439,-649,561,-990,-394,503,-12,-661,376,367,427,894,279,152,-654,-655,-755,955,677,296,-312,407,-478,-996,694,281,137,729,-763,770,144,833,-737,469,-158,369,-42,-479,-996,-389,-461,906,605,760,501,-651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "isEnabled(com.fasterxml.jackson.core.JsonGenerator$Feature):boolean",
            new int[]{-1000,-212,554,368,-943,-764,-130,-1000,-836,-757,1000,-913,-661,-765,1000,187,391,-900,854,701,-780,103,-262,-938,145,732,893,1000,-1000,102,618,-1000,345,1000,-990,1000,71,299,461,510,-1000,597,-47,1000,-812,128,-745,930,-185,-558,-784,118,1000,-1000,986,638,517,-133,-1000,-1000,-759,-833,-269,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "isEnabled(com.fasterxml.jackson.core.JsonGenerator$Feature):boolean",
            new int[]{-1000,1000,764,453,-1000,-304,506,-1000,691,-948,-1000,-120,-1000,630,42,1000,1000,987,-249,-1000,-1000,394,-713,-1000,346,-595,977,-669,-82,-424,17,-918,334,-30,147,945,-660,1000,-414,821,-316,-502,-545,1000,189,-663,-225,-698,-548,621,-898,-183,976,-1000,-1000,1000,254,-1000,-918,-574,1000,1000,738,-992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "serialize(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{837,794,-256,448,-866,-236,-143,703,1000,936,127,-265,427,-38,-373,318,1000,963,-375,-767,1000,-1000,-1000,-1000,1000,1000,-1000,-716,177,-386,-86,1000,945,794,1000,-645,-502,-995,168,-828,-47,769,1000,-783,28,-425,912,789,-132,-1000,685,269,-393,654,-34,-262,-497,908,-62,-365,242,-548,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "serialize(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{624,1000,-877,-846,-478,-467,678,227,1000,-1000,-1000,197,-1000,-1000,1000,1000,-106,985,1000,532,1000,382,1000,-1000,942,-201,-951,-1000,-1000,1000,-763,222,259,-996,870,1000,1000,735,274,228,698,1000,494,-161,-1000,109,1000,-582,95,525,-1000,541,1000,517,-320,334,1000,987,-1000,282,29,333,351,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "setCodec(com.fasterxml.jackson.core.ObjectCodec):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{-539,508,-942,-263,685,794,358,-54,9,415,364,-866,-385,-71,-934,900,-634,478,-883,195,-688,845,117,-22,-802,-243,567,888,659,730,992,-331,-228,656,717,286,170,313,-643,-57,22,-953,198,527,-176,970,343,422,185,193,-253,64,-427,-128,79,-855,-685,98,725,-48,-920,-924,-812,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "setFeatureMask(int):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{621,-833,-956,-889,-857,-258,554,-199,794,-182,976,817,118,-801,41,949,-940,-134,-989,-691,279,-992,-161,194,389,-763,-826,-888,503,437,-934,-829,439,839,-420,-299,-877,-399,-112,182,857,-576,-113,351,-637,-458,-98,-291,829,814,-394,524,-475,-422,939,-47,995,-577,-478,881,304,115,794,-290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:W1Rva2VuQnVmZmVyOiBd", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "toString():java.lang.String",
            new int[]{696,-611,-822,-307,447,-40,76,-874,575,388,-680,195,-487,387,-293,-596,-997,-60,-439,-508,338,-257,-320,-523,837,-437,992,437,815,-727,697,733,251,-277,-374,18,-221,-9,51,-927,720,72,-794,293,-584,-399,-529,-530,313,-177,-697,-608,-40,-320,-226,-213,365,-348,-103,81,992,757,-217,-401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:W1Rva2VuQnVmZmVyOiBd", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "toString():java.lang.String",
            new int[]{-49,-1000,-896,308,961,920,-1000,-1000,617,431,-1000,-228,-487,-115,-975,754,-171,-576,-521,-583,-110,84,229,476,1000,-979,-63,1000,-192,-1000,1000,1000,728,-496,-576,-579,-490,1000,-159,-1000,-501,73,-1000,1000,-1000,-788,-691,-1000,651,65,-1000,-1000,-7,606,102,-430,200,-425,-161,-502,581,438,-1000,136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "useDefaultPrettyPrinter():com.fasterxml.jackson.core.JsonGenerator",
            new int[]{-577,418,-496,-288,-727,404,-642,-670,-180,-611,761,784,-634,739,-181,743,96,-607,161,893,-781,-168,862,355,-191,450,-906,-309,-363,201,804,-759,563,-971,-167,235,-488,-172,60,345,448,449,402,202,-660,-368,775,-261,640,629,512,-821,475,777,-854,998,436,284,933,228,625,-868,-925,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "version():com.fasterxml.jackson.core.Version",
            new int[]{826,1000,-642,198,-1000,-308,-256,1000,-1000,-1000,-569,-162,-413,-1000,-1000,-703,285,-266,422,-1000,-255,1000,1000,-1000,309,10,-905,-1000,875,-891,-120,-14,-185,-1000,-1000,-88,895,723,1000,904,936,-142,-1000,948,-1000,-1000,1000,-375,262,-305,105,373,580,-775,-10,547,-1000,-761,335,1000,1000,773,-339,49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBinary(com.fasterxml.jackson.core.Base64Variant,byte[],int,int):void",
            new int[]{377,-962,309,-1000,674,-173,1000,180,-304,-583,248,524,-877,-908,891,-1000,317,290,-463,147,853,-1000,-885,386,-618,-556,-279,-864,-264,-494,984,-594,336,618,-14,558,987,529,509,-992,1000,353,-336,-173,363,465,-191,-365,763,420,811,-119,-336,439,601,-260,1000,-375,340,277,36,-517,-165,-891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBinary(com.fasterxml.jackson.core.Base64Variant,java.io.InputStream,int):int",
            new int[]{1000,-560,338,176,-236,-643,-550,896,1000,-1000,-663,372,1000,1000,992,-12,-448,-254,-568,346,-390,-46,-359,-374,-439,-773,-43,453,208,216,314,1000,-766,5,-121,-344,-557,706,292,-1000,-48,1000,-380,526,-548,595,-250,1000,1000,-142,-992,-937,-90,-1000,574,-1000,-628,-18,154,265,-1000,-111,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBoolean(boolean):void",
            new int[]{-239,463,884,-26,-132,240,570,5,-687,-1000,-718,280,-286,589,203,-195,1000,-177,-993,763,-564,624,275,848,-4,329,-327,-220,439,489,697,-1000,-849,-796,-135,-76,642,-664,195,-882,-215,-633,206,507,-497,213,893,-1000,821,-1000,75,-45,-928,505,709,1000,-757,-1000,-102,33,104,164,123,-748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBoolean(boolean):void",
            new int[]{815,979,579,-320,1000,-1000,-983,544,92,900,-1000,778,712,1000,444,466,1000,116,-847,-133,-966,1000,-110,-212,-99,-722,-308,-1000,270,42,442,-283,-295,-1000,-1000,679,-179,170,1000,770,6,-1000,-291,55,-1000,600,671,531,-1000,1000,-1000,-1000,-1000,1000,-167,798,-47,-821,-1000,208,-1000,1000,339,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeEndArray():void",
            new int[]{413,-146,-526,418,170,854,1000,449,-1000,1000,1000,1000,218,788,-72,-557,783,344,-1000,341,-939,802,-955,1000,-1000,122,947,167,-1000,1000,1000,-1000,339,-488,-447,1000,1000,635,1000,916,-1000,1000,1000,317,-358,53,-1000,-1000,-1000,-647,1000,576,-1000,1000,882,-970,637,1000,499,1000,284,454,190,278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeEndObject():void",
            new int[]{-329,1000,-466,-1000,-255,-432,819,-284,-1000,556,217,410,-164,590,-942,-898,-1000,1000,-1000,-314,-1000,-74,-1000,593,-11,326,-1000,356,504,-702,-508,1000,-925,656,-532,1000,-771,619,-291,-73,-407,-292,341,-382,-847,-1000,1000,353,1000,1000,-70,456,-950,668,-1000,-1000,437,-861,-299,-879,1000,-249,99,444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeFieldName(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-917,-521,-151,-132,-585,1000,200,772,-632,264,1000,942,-516,637,-1000,1000,690,-1000,-874,-268,-673,-1000,-420,436,-1000,-223,316,-274,429,-899,-346,131,730,973,273,-484,1000,446,-842,-304,-235,904,-268,10,941,273,-1000,310,-606,1000,-284,637,-619,-703,989,-138,322,-828,69,-668,-614,-645,-284,449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeFieldName(java.lang.String):void",
            new int[]{744,-176,-672,574,310,-1000,580,-146,1000,1000,72,32,-494,1000,1000,706,650,888,-38,-960,-448,-1000,710,1000,130,1000,-198,1000,753,-402,-1000,-445,1000,-436,-656,549,560,-221,-160,1000,-605,-1000,-766,-906,398,-521,-454,1000,-856,270,922,344,285,-1000,313,-134,-1000,-861,1000,663,338,-1000,-84,881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNull():void",
            new int[]{-144,325,334,-846,467,127,772,-512,449,266,380,53,-86,888,-751,-867,-81,-361,503,613,-496,-925,-55,205,-247,333,259,1,220,-862,586,563,991,-829,461,342,627,448,-618,-741,292,426,-385,984,-520,-715,221,657,922,72,757,-450,-499,-800,-399,-432,293,-810,469,350,216,-536,-715,211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(double):void",
            new int[]{-1000,832,-231,-1000,-271,-85,68,-70,1000,828,-773,477,491,-728,126,166,616,1000,-698,1000,-573,-1000,-784,-868,422,69,93,751,514,127,-739,292,164,-1000,-702,-364,629,113,-1000,-858,-608,501,-728,-442,1000,381,-726,-1000,-608,1000,-382,-1000,386,-1000,1000,-245,411,-424,275,-104,660,649,822,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(float):void",
            new int[]{199,-935,104,-895,-1000,156,463,1000,-920,-437,-1000,365,913,443,299,286,1000,-351,740,728,-173,72,1000,-466,-295,-128,12,-620,503,1000,-1000,-179,-1000,67,-164,-739,505,-266,1000,-583,1000,-376,-113,243,589,403,-10,1000,323,-210,-382,-1000,-90,-76,790,-205,-1000,-449,-378,-261,97,-623,-77,-395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(int):void",
            new int[]{-952,1000,-927,-978,801,-691,463,-1000,-408,189,-274,-1000,66,-1000,-361,-400,1000,1000,765,-847,-1000,-717,-463,-511,429,1000,-686,20,700,1000,-184,135,1000,-579,1000,573,904,261,-1000,-1000,890,556,-168,-141,491,538,-68,57,377,1000,-210,1000,1000,-304,1000,-739,-834,-1000,-454,-328,-512,1000,325,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.lang.String):void",
            new int[]{-46,934,-823,-622,-138,772,-998,100,-686,80,-584,22,377,547,957,-749,572,194,-355,256,-6,-259,-707,-556,-451,-292,465,135,228,-461,-237,-996,-956,415,-609,272,508,-91,-313,-832,-671,-876,703,456,403,-462,772,-803,100,-553,515,-648,348,572,-252,-704,89,181,359,-123,-695,908,368,251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigDecimal):void",
            new int[]{-326,1000,518,-1000,1000,765,-936,-113,-2,-537,-921,-660,-346,518,-1000,2,328,-96,-349,-300,-456,-1000,1000,-1000,-770,55,-530,1000,-180,310,185,-27,-742,550,509,-1000,1000,-700,477,-907,-560,-330,242,-920,-758,-461,-78,-1000,1000,215,-61,350,-407,-588,973,350,-1000,918,385,470,145,-949,-656,-674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigDecimal):void",
            new int[]{673,-977,433,288,470,-75,-997,-666,450,-870,-460,-729,-750,411,-781,-157,-765,-735,442,-549,80,-765,-462,-4,242,-83,690,23,-258,-538,-851,368,-120,-673,-841,988,596,-626,-769,298,-24,-983,397,-581,-868,-949,-71,-967,-883,-967,-403,238,861,676,29,707,188,532,966,-215,-87,-530,-695,698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigInteger):void",
            new int[]{1000,361,-991,-401,47,-453,-192,435,-1000,400,474,-691,67,258,449,-298,1000,177,-495,506,316,736,1000,578,250,-286,-1000,500,36,-712,-111,-5,-129,1000,41,300,-197,-523,347,359,949,-72,-749,-1000,928,702,-15,1000,772,347,-804,-1000,341,377,-276,1000,-207,-1000,-923,-918,471,277,472,-721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigInteger):void",
            new int[]{-137,454,-2,117,-250,258,726,-1000,-1000,-1000,1000,168,-708,268,-235,31,-205,-39,-1000,892,413,181,405,-386,-1000,-887,-1000,591,1000,-177,234,-416,-253,765,-568,-728,-1000,-1000,634,589,129,663,43,-500,107,-194,-479,472,1000,-898,1000,-259,-145,840,983,836,-322,-256,517,330,1000,-151,321,-902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(long):void",
            new int[]{748,-386,539,-913,1000,-54,-1000,826,134,881,1000,656,-159,129,401,620,322,1000,-619,647,-461,-6,474,11,1000,1000,-403,-451,-33,-434,-1000,400,594,180,222,-237,329,1000,1000,-538,181,-609,-551,1000,-1000,-57,926,1,-88,364,-1000,1000,491,-235,-195,-1000,351,-1000,-30,113,-40,-15,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(short):void",
            new int[]{0,-977,389,436,0,-288,-1000,-150,-83,781,460,699,-207,-889,-802,-451,458,281,-582,521,-1000,978,1000,0,-668,-14,-708,-629,-462,471,87,1000,-1000,-1000,99,377,-1000,-274,396,-85,157,1000,-58,686,292,215,369,1000,-610,0,0,0,1000,-605,0,829,-159,0,117,-180,1000,219,0,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeObject(java.lang.Object):void",
            new int[]{390,112,768,717,1000,-1000,-96,-185,-421,-790,923,545,1000,207,1000,-579,506,1000,1000,964,207,-147,862,-955,678,1000,290,715,262,-925,-1000,-227,-191,-860,467,445,405,1000,624,1000,1000,-623,1000,-3,524,-439,-740,-516,-951,545,-317,-207,1000,-333,-526,-173,616,-1000,263,1000,-599,1000,278,-605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeObjectId(java.lang.Object):void",
            new int[]{486,-392,977,568,114,45,739,598,-770,-183,-863,519,-130,568,9,304,647,762,423,-906,238,-255,-316,167,-727,280,289,-924,497,-875,425,80,-464,418,-706,650,-115,-772,-54,777,-139,467,-413,-892,157,-426,402,-508,381,-163,805,-540,-511,-931,-266,337,381,-566,-882,-774,-895,673,-39,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(char):void",
            new int[]{474,-26,223,343,380,-240,569,-944,372,-176,970,-696,766,-572,-990,920,-236,390,-70,462,-964,477,638,466,98,-246,894,466,-957,914,513,-421,281,242,-163,608,129,-823,-843,460,315,-797,-679,994,-224,-790,293,322,482,375,-869,-948,-568,-842,-499,-970,479,16,-350,-322,167,122,570,-770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(char[],int,int):void",
            new int[]{266,229,-92,-1000,-1000,296,-1000,463,-1000,462,764,-18,-631,210,-975,-1000,-144,173,-1000,770,1000,-1000,19,-11,-751,-1000,-124,38,-896,791,345,-1000,1000,400,-1000,225,464,-598,-1000,66,-874,-383,-322,-819,-414,-64,-384,-154,-709,1000,-1000,247,-322,674,691,-619,1000,88,212,-185,1000,1000,391,118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-910,1000,249,211,598,546,1000,590,463,-1000,-960,-1000,1000,721,1000,-1000,-1000,-1000,-357,419,246,-996,-25,996,-410,-943,596,-13,416,99,-1000,-6,-401,-1000,-1000,129,-1000,666,-57,-658,-40,909,1000,-1000,463,809,31,-296,420,68,20,134,381,930,758,-705,-959,11,42,-868,-1000,-86,59,-64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(java.lang.String):void",
            new int[]{-302,760,579,-11,404,-512,289,-613,-945,770,134,-745,635,220,693,-445,-714,-731,323,347,860,-126,260,-680,-205,320,-179,409,861,398,-221,-13,-895,-466,-539,-605,-42,-870,-581,128,-58,297,623,845,645,496,-963,-428,725,-876,-823,-49,573,-964,-219,-800,-24,181,-916,-439,618,-947,344,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(java.lang.String,int,int):void",
            new int[]{728,-701,433,-1000,457,-668,-196,-726,-33,-441,-187,60,-189,-550,-361,-1000,-812,-347,-817,406,961,-415,-140,368,132,-600,-1000,-172,-1000,-895,-619,799,-1000,-363,440,-512,-573,-25,-263,326,822,49,2,-1000,1000,-911,1000,-882,-1000,-535,251,-187,-817,794,-999,1000,-630,105,306,787,-1000,-1,-725,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawUTF8String(byte[],int,int):void",
            new int[]{164,745,999,-749,-822,-502,790,-308,351,-590,-137,-584,114,34,283,878,897,783,-316,-885,230,-950,875,706,459,608,671,95,455,-974,-522,295,-178,-210,146,982,-671,372,545,581,559,-418,-612,51,-923,171,929,766,847,-86,-651,-841,-958,156,-143,591,-51,-843,860,284,215,482,-103,126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(char[],int,int):void",
            new int[]{641,589,684,-626,-47,950,-893,-309,-104,77,915,-866,-298,-302,-244,-320,-937,796,-487,576,-732,-942,656,-579,91,4,-670,940,-702,-989,-257,732,882,70,-419,-854,-224,780,914,406,-858,-173,-584,127,-873,992,560,425,-564,582,899,-4,-9,-871,-439,705,521,-842,-377,545,998,889,-817,-899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(java.lang.String):void",
            new int[]{-357,1000,-791,-726,341,-271,19,-724,302,1000,185,-143,1000,-1000,-381,-51,499,345,712,46,-1000,534,1000,1000,-52,-627,-125,30,455,224,496,-1000,-457,308,813,-1000,173,-1000,62,-366,407,27,-824,-1000,-240,-172,-106,-14,-169,-11,-514,816,-344,-233,-336,51,599,248,266,720,-635,-889,-1000,168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(java.lang.String,int,int):void",
            new int[]{41,1000,-391,-488,-357,-85,-361,400,463,-24,134,-522,437,7,1000,387,-750,-1000,685,-26,24,-115,679,241,1000,-173,1000,356,1000,-876,-334,678,448,-979,-125,-779,605,-423,-147,505,818,398,-1000,558,400,187,-589,-681,628,-1000,372,-277,398,13,-190,-11,-240,-557,-832,-604,-87,-997,400,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeStartArray():void",
            new int[]{-430,-605,-546,-348,-555,460,-1000,350,388,277,-1000,374,810,-593,-1000,47,-1000,-1000,1000,367,440,588,-178,-1000,411,734,746,-489,419,-2,285,-377,-313,645,174,-218,507,597,1000,370,782,954,159,0,44,-514,491,-528,661,-1000,284,-187,-246,667,-378,1000,-1000,-1000,-295,334,-1000,-110,25,-340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeStartObject():void",
            new int[]{296,-692,454,-69,16,-885,833,-483,878,-335,578,-562,711,988,-813,623,-455,-867,511,19,-800,-552,442,-47,-551,-773,185,-566,81,-367,208,-239,152,725,992,998,257,404,-736,147,746,129,519,182,458,-594,321,-505,788,-837,143,-238,665,-575,298,763,-633,960,912,-751,28,-67,-195,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(char[],int,int):void",
            new int[]{-335,761,-689,344,478,-787,-192,594,-608,382,-477,249,-499,-123,-232,-120,985,672,419,-561,344,810,-972,524,-368,-663,932,-177,-416,139,842,-895,423,-888,31,-144,96,352,-13,990,-302,-96,-780,-67,-84,-975,724,809,489,875,-432,493,870,-916,-949,-199,535,-237,-101,-717,-261,142,122,721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(char[],int,int):void",
            new int[]{473,-17,611,-411,942,-720,543,283,965,1000,259,775,1000,804,-650,-303,947,-93,-1000,283,457,662,-41,-647,-505,-950,57,1000,-986,245,-301,5,1000,-317,-761,1000,-1000,223,-1000,1000,75,901,164,807,-263,774,-1000,348,-1000,-559,319,1000,1000,-975,1000,-358,-650,110,986,-476,-1000,907,-521,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-98,883,-756,-1000,779,-1000,-996,-548,-1000,243,-406,644,140,-201,-863,-1000,349,-1000,1000,995,523,237,-1000,-211,1000,1000,100,-951,-1000,-185,864,-141,-1000,-1000,250,928,714,-775,898,-599,-809,-1000,773,929,-436,-406,-326,-1000,1000,532,1000,431,567,790,488,842,1000,-989,-818,-310,-533,-904,-753,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(java.lang.String):void",
            new int[]{-955,217,384,819,366,-1000,1000,932,-757,-160,-443,-888,-1000,200,-1000,1000,-74,947,-1000,-1000,1000,-970,541,-334,-871,576,686,-1000,1000,-929,1000,-1000,1000,-312,-962,1000,-936,733,-317,962,-463,223,-101,-501,-713,1000,-137,607,-219,860,418,51,1000,754,418,298,91,-800,-18,-508,-1000,-1000,154,948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(java.lang.String):void",
            new int[]{-665,4,-958,443,828,789,953,620,-221,-255,245,1000,-485,788,-113,745,1000,-389,-521,265,421,-769,440,190,-343,1000,-484,212,-1,-1000,1000,-315,-795,837,-1000,1000,-860,-674,437,107,1000,-393,78,-24,305,1000,-875,-307,1000,425,-379,-471,1000,702,-1000,-89,796,1000,-1000,-284,-38,108,-1000,-244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeTree(com.fasterxml.jackson.core.TreeNode):void",
            new int[]{-492,1000,-512,-1000,1000,-103,1000,448,-269,-650,811,-87,141,324,-1000,620,738,626,-1000,31,-670,-640,698,205,333,1000,-593,-1000,924,-1000,-1000,37,681,-1000,-282,40,687,24,-1000,-1000,-623,1000,-738,-1000,-186,-41,691,1000,-1000,-1000,830,-246,-708,-326,-214,-741,586,322,1000,242,1000,1000,-1000,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeTypeId(java.lang.Object):void",
            new int[]{-1000,1000,-816,162,226,524,1000,238,-473,-1000,1000,-480,1000,-123,-60,-1000,728,1000,-227,-763,96,1000,212,-717,814,-13,96,2,788,400,-594,525,1000,627,72,747,100,-791,-35,-929,866,-769,8,-274,468,1000,895,-185,1000,-1000,-647,-1000,-400,162,916,-371,-51,-1000,-861,-435,-572,-162,235,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeUTF8String(byte[],int,int):void",
            new int[]{139,-548,-691,-160,527,-563,725,115,243,-876,633,-347,-621,-450,-211,534,-397,974,135,418,-120,-816,947,120,328,195,420,-874,-671,-489,-61,-323,523,-153,497,-194,-218,-386,63,-889,-124,-659,-987,6,182,676,206,-353,462,676,743,-147,-451,645,11,-87,-436,-21,-783,401,226,-827,-693,850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.TokenBufferDeserializer", "com.fasterxml.jackson.databind.deser.std.TokenBufferDeserializer", "deserialize(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.DeserializationContext):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
