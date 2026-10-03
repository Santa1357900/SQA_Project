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
        org.junit.Assert.assertEquals("ENUM:com.fasterxml.jackson.core.JsonToken:START_OBJECT", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "asToken():com.fasterxml.jackson.core.JsonToken",
            new int[]{66,1000,949,-19,894,-823,336,-707,267,-19,-189,-913,-1000,846,-528,-958,392,400,-844,265,-14,-1000,-1000,958,619,-1000,-1000,-1000,1000,381,1000,-829,311,-1000,-50,1000,331,47,-1000,1000,-1000,785,1000,-1000,-1000,-704,636,-1000,-198,-811,631,1000,1000,1000,810,1000,1000,1000,-1000,20,1000,-1000,-487,-739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("ENUM:com.fasterxml.jackson.core.JsonToken:START_OBJECT", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "asToken():com.fasterxml.jackson.core.JsonToken",
            new int[]{829,285,352,288,153,-73,466,-543,29,-957,-562,-582,4,510,-506,-384,228,-835,384,271,352,-554,-483,502,-334,70,-439,-496,71,-682,251,792,-869,763,725,-108,400,395,-437,195,524,10,-110,-297,-828,446,405,-688,768,-181,-7,974,218,221,-303,-431,328,-432,-720,-333,464,-807,312,-533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "deepCopy():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-1000,965,1000,-166,-472,437,1000,-427,-1000,-538,-193,997,683,594,-936,608,-755,-819,-1000,-345,-644,-622,1000,-392,413,-1000,1000,-1000,-645,-398,796,-1000,-381,-1000,1000,978,-1000,171,1000,900,20,696,-447,32,464,210,-962,647,172,399,-605,-244,-1000,120,628,-1000,1000,1000,-700,86,-283,-1000,-794,-106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "deepCopy():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-592,461,353,130,-856,-638,808,70,-270,-666,223,903,209,791,-629,397,804,-876,-114,582,-854,-942,750,-129,-486,-708,819,-493,-574,305,-988,-889,-458,-774,775,798,-256,466,804,773,548,-516,-370,-362,513,-547,-341,-268,-787,910,521,-256,-497,505,170,-436,314,850,-922,-109,14,-796,91,-554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:java.util.LinkedHashMap$LinkedValueIterator", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "elements():java.util.Iterator",
            new int[]{-646,965,-1000,-89,181,241,1000,-1000,739,1000,754,-1000,616,191,-74,604,-1000,472,-25,-1000,-189,-120,1000,-1000,-61,1000,-400,-656,662,-1000,-822,-770,31,-551,1000,1000,-1000,-343,-337,95,149,-594,577,-1000,-399,-1000,-1000,714,1000,-146,-936,1000,-530,770,644,1000,-1000,142,183,6,943,633,-932,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:java.util.LinkedHashMap$LinkedValueIterator", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "elements():java.util.Iterator",
            new int[]{-104,1000,-1000,466,239,-184,-251,98,-1000,828,196,-504,1000,-814,-855,539,-483,-433,1000,-748,744,596,-188,-906,-430,268,223,146,529,-902,160,-725,-1000,473,85,-66,-995,-1000,131,583,91,-628,-976,-363,1000,-633,-552,834,562,114,-1000,259,1000,115,-1000,-113,258,-340,-641,-227,476,932,-234,-479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "equals(java.lang.Object):boolean",
            new int[]{-283,737,-821,438,-1000,-16,837,761,-630,-587,208,413,52,-533,376,115,-403,418,-406,-22,1000,-652,-1000,-851,-463,-443,-539,-162,380,-80,-748,1000,481,-33,-1000,806,71,-1000,1000,-668,991,-749,807,1000,368,515,1000,564,-559,1000,-472,-590,400,728,-94,-89,301,740,827,783,-405,-544,-709,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "equals(java.lang.Object):boolean",
            new int[]{1000,618,-1000,500,-441,-683,-1000,484,1000,-1000,-1000,-991,-1000,-90,-1000,1000,-197,1000,-969,-1000,-1000,1000,-1000,-215,-1000,-1000,-250,1000,-1000,-1000,822,1000,197,1000,1000,1000,-1000,-1000,1000,696,-896,-107,-1000,494,1000,726,1000,607,1000,1000,1000,-1000,1000,1000,1000,-373,-297,244,1000,1000,-1000,-82,78,-687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:java.util.LinkedHashMap$LinkedKeyIterator", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "fieldNames():java.util.Iterator",
            new int[]{243,174,744,-416,419,-356,481,336,-1000,-165,593,-1000,927,-942,1000,578,743,463,-131,238,437,1000,400,1000,-521,-561,-541,-130,800,-477,-612,422,1000,-627,-804,116,-847,348,-368,-201,369,785,1000,-145,-62,-389,212,-1000,596,-1000,-1000,335,-1000,-1000,-1000,-1000,-71,-108,671,1000,925,-79,1000,265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:java.util.LinkedHashMap$LinkedKeyIterator", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "fieldNames():java.util.Iterator",
            new int[]{-942,384,-31,-493,-346,-234,-1000,948,-212,-774,931,-1000,506,-567,1000,927,151,-721,-254,670,134,840,220,-518,800,-635,-802,-532,159,-1000,-47,484,891,81,-289,-242,-1000,-1000,-517,-481,-821,1000,-728,-409,-290,-545,-194,-591,939,-1000,-1000,-108,202,-1000,136,-1000,207,78,121,1000,897,-88,330,-948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:java.util.LinkedHashMap$LinkedEntryIterator", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "fields():java.util.Iterator",
            new int[]{-786,-745,79,594,180,84,-68,-1000,-205,102,-733,-98,-446,210,-1000,-391,469,-197,903,-1000,-53,-215,797,-427,645,-370,325,-137,-366,-37,424,-343,23,666,-146,-533,-14,-30,-515,1000,-946,669,559,447,-441,249,293,-135,-556,-304,-223,89,215,-77,200,651,463,-20,-68,-638,102,451,-250,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:java.util.LinkedHashMap$LinkedEntryIterator", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "fields():java.util.Iterator",
            new int[]{-1000,1000,142,-214,429,-786,33,-1000,-970,-281,-663,1000,-1000,-1000,-801,821,366,106,-174,-872,-95,13,16,219,1000,-322,-894,97,-523,-304,1000,-420,-237,-392,375,-85,-571,76,-312,290,961,1000,241,1000,586,-431,403,827,484,-228,-231,604,-559,308,181,679,147,-840,-512,310,1000,-805,478,128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findParent(java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-29,-940,-768,114,604,-391,1000,901,-495,29,-648,571,-1000,-184,-402,-342,97,-849,667,-699,-430,570,449,1000,-719,-860,1000,-508,-631,-209,1000,-325,-286,1000,-1000,-92,-562,-480,-1000,-662,1000,324,-1000,-461,630,-1000,-1000,-833,288,355,-1000,772,794,670,907,-802,-993,713,213,-755,-210,-143,708,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findParent(java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{368,-1000,-1000,-37,254,1000,999,1000,-320,569,566,1000,-1000,-1000,1000,-310,-233,-55,1000,-709,1000,-980,-843,662,-792,-1000,275,212,948,-1000,545,1000,411,-108,1000,-688,-282,188,-1000,1000,849,1000,293,-47,-225,-1000,162,-1000,822,806,193,386,819,-980,79,-259,-634,1000,531,591,-43,1000,-453,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findParents(java.lang.String,java.util.List):java.util.List",
            new int[]{316,-144,-970,-108,694,233,-169,1000,563,99,423,-890,-471,644,-612,-138,927,1000,1000,578,1000,-27,20,-871,-550,285,523,872,291,919,-630,6,509,1000,439,-526,1000,-804,-746,-1000,775,246,-635,803,76,-294,730,-66,-1000,-1000,749,-388,-575,240,1000,-297,-519,-613,116,-77,-80,1000,-263,-396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findParents(java.lang.String,java.util.List):java.util.List",
            new int[]{508,-144,-1000,202,764,909,-1000,708,575,-1000,841,-1000,-693,28,-1000,-482,1000,1000,665,1000,1000,-648,1000,-881,-651,758,146,1000,-47,455,-698,-312,409,490,-414,-1000,750,-681,-1000,-504,265,1000,-1000,-119,-627,-1000,472,769,-1000,-1000,-192,-841,-1000,-484,413,-980,-519,-770,-559,-500,-834,1000,-984,-77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findParents(java.lang.String,java.util.List):java.util.List",
            new int[]{-594,1000,1000,885,184,-970,-499,884,-472,353,-870,-46,-840,1000,-741,-96,1000,-13,-1000,1000,81,-1000,529,812,134,-638,-1000,-933,-1000,1000,-1000,1000,1000,-1000,-329,-1000,731,-651,-1000,1000,-403,753,-1000,706,-715,1000,-708,-91,1000,-1000,-626,-624,1000,-983,628,109,-1000,260,-788,615,211,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findValue(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{569,-1000,-1000,249,379,-1000,797,140,1000,1000,261,-613,801,-1000,-523,-1000,-1000,483,1000,-1000,1000,-40,1000,1000,1000,-696,-444,1000,-1000,-422,-552,-933,243,60,-1000,-281,519,1000,-310,1000,-434,-1000,-1000,1000,143,-119,293,1000,-1000,1000,-244,836,1000,-18,-1000,731,916,1000,1000,294,17,-35,674,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findValue(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-453,879,878,-839,-126,-17,-360,429,-287,626,-517,821,-804,31,501,-29,-672,544,505,-110,191,-456,-790,-291,623,767,228,-955,457,341,-25,-3,891,-747,138,462,-427,-544,-610,261,601,-640,681,360,-233,84,609,393,132,500,-852,623,275,852,-698,-473,-593,-487,-859,-769,-32,-677,-884,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.NullNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findValue(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-1000,240,1000,1000,537,-37,-1000,93,-1000,557,-1000,-1000,-968,635,-996,1000,1000,-608,-1000,132,-1000,-1000,-1000,-863,-1000,409,-447,-1000,1000,1000,984,-799,391,-483,966,-1000,604,767,-328,-1000,-1000,1000,1000,-1000,172,-1000,-1000,-1000,-694,-1000,-692,176,-1000,12,-530,778,-56,-1000,567,919,-402,1000,504,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findValues(java.lang.String,java.util.List):java.util.List",
            new int[]{4,82,-169,478,-126,867,-246,-54,-131,17,340,42,372,-91,-220,1000,325,552,-253,185,-637,-507,-708,-21,-564,-436,603,109,357,363,204,977,397,-550,-607,774,294,-101,-942,410,-364,-19,-618,664,-655,-121,1000,-216,-250,-227,-224,-181,-50,336,485,379,845,77,383,658,-180,-618,-691,-592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findValues(java.lang.String,java.util.List):java.util.List",
            new int[]{-780,792,21,-1000,904,-905,479,-1000,-901,446,1000,423,1000,-606,-1000,1000,-940,509,-963,665,-1000,418,-1000,1000,-140,163,422,-576,1000,-880,-604,801,-1000,-613,-511,-1000,1000,-725,-779,421,-170,779,-199,-168,997,136,1000,-1000,381,-1000,425,-945,-545,-324,-738,495,-629,1000,1000,1000,697,-54,-1000,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findValues(java.lang.String,java.util.List):java.util.List",
            new int[]{860,1000,-319,-358,68,-594,1000,-1000,-1000,583,1000,1000,863,-1000,-237,1000,-781,940,-1000,1000,-976,1000,-1000,739,-1000,1000,1000,-1000,-539,-1000,-1000,1000,-867,61,-1000,-318,1000,-1000,1000,161,-1000,399,-639,180,1000,-622,1000,-1000,-1000,-1000,851,-1000,1000,1000,-1000,-242,-868,566,1000,1000,-1000,1000,-1000,841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findValuesAsText(java.lang.String,java.util.List):java.util.List",
            new int[]{-1000,1000,1000,473,194,-369,1000,442,-941,-60,-631,937,-630,1000,67,-500,976,729,-309,943,302,-1000,-1000,-588,-1000,-1000,1000,-1000,668,-1000,-1000,1000,1000,1000,-424,-1000,825,-300,-1000,-113,553,-565,1000,-143,310,-404,1000,1000,-1000,-898,1000,1000,1000,-1000,575,-765,827,1000,-1000,-1000,-62,-1000,-48,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findValuesAsText(java.lang.String,java.util.List):java.util.List",
            new int[]{-1000,1000,-1000,-1000,554,703,-1000,1000,-941,1000,316,937,-1000,37,912,862,885,-107,-787,-1000,-93,218,-718,1000,-1000,356,-1000,-1000,1000,453,-781,969,40,-989,-1000,-1000,871,-300,1000,527,350,125,1000,-50,1000,1000,106,1000,1000,1000,1000,595,697,-1000,279,566,395,47,-294,-661,1000,118,-48,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "findValuesAsText(java.lang.String,java.util.List):java.util.List",
            new int[]{16,-166,923,642,-21,-1000,519,-159,-624,136,37,920,718,980,167,-145,-641,-1000,507,1000,-823,-1000,-90,-603,-45,-866,488,-352,136,-1000,-1000,272,-536,1000,946,8,715,-267,-1000,-249,-407,-243,1000,512,-24,-1000,987,1000,-400,-743,-727,53,581,-434,-676,229,939,1000,-1000,-175,-1000,127,-508,-567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "get(int):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-1000,686,-513,1000,84,425,1000,695,481,-776,756,934,-652,-105,-38,-597,-677,262,-308,-30,730,317,-200,-602,-339,423,215,670,-1000,-1000,-104,-316,-295,-640,-773,-480,444,-399,383,-780,-317,-370,1000,-208,524,1000,198,68,-273,-1000,-155,521,345,335,832,231,908,808,-1000,-334,876,-498,-228,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "get(int):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-801,-110,500,-400,-141,-56,343,118,-1000,-349,-1000,887,-798,867,590,-40,18,902,188,-688,510,-260,-779,1000,-475,-893,-701,300,645,150,-965,282,-99,546,-567,1000,-233,1000,-708,70,1000,-220,56,517,144,1000,600,270,117,823,-292,1000,290,-1000,-116,-1000,1000,380,-1000,1000,53,621,1000,95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "get(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{712,-1000,-1000,-582,-151,313,-1000,329,-394,-963,113,318,310,-325,707,-1000,181,1000,88,38,-351,-524,-52,-186,-62,-284,472,-45,-963,696,-699,-13,816,-709,-515,1000,1000,1000,-89,-1000,-449,-293,1000,-1000,-274,1000,1000,-255,734,240,181,4,-73,997,-252,300,-926,-993,922,-446,835,-1000,-210,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "get(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{695,-1000,45,1000,-126,209,1000,-864,423,1000,1000,93,729,631,737,-1000,-1000,-976,-785,-1000,-385,-1000,1000,1000,-232,67,-561,-1000,1000,-711,-751,496,391,-1000,-1000,-909,-1000,-1000,-1000,-1000,1000,1000,-845,1000,1000,-1000,-1000,1000,-998,-713,-285,-471,1000,-984,1000,132,1000,993,474,672,1000,752,-266,-872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("ENUM:com.fasterxml.jackson.databind.node.JsonNodeType:OBJECT", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "getNodeType():com.fasterxml.jackson.databind.node.JsonNodeType",
            new int[]{864,237,-411,-757,311,619,37,394,-186,-206,-1000,1000,1000,-207,-103,78,1000,193,626,-91,-328,1000,-363,-537,-96,-1000,-623,-1000,559,-217,453,50,-10,-254,412,1000,871,-854,816,-1000,-367,-1000,1000,593,-157,-1000,39,-1000,-221,-871,1000,-770,-550,1000,1000,450,1000,-419,747,1000,1000,-917,413,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("ENUM:com.fasterxml.jackson.databind.node.JsonNodeType:OBJECT", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "getNodeType():com.fasterxml.jackson.databind.node.JsonNodeType",
            new int[]{1000,-392,215,-373,473,325,631,1000,149,-230,600,-711,1000,-369,-954,913,856,-504,671,85,-441,541,-243,378,1000,-1000,-538,-1000,-915,-440,-799,-1000,-84,-213,-317,155,1000,454,513,862,-1000,-1000,-706,-243,1000,-676,-545,722,-1000,-653,549,635,-659,-192,187,331,1000,-662,287,-954,1000,-732,551,-198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.MissingNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "path(int):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-149,466,789,69,294,-661,721,241,-746,384,-80,-413,1000,703,53,-1000,-545,314,1000,-280,1000,-1000,-161,-324,255,-1000,1000,-979,-366,-772,37,484,-655,-1000,-910,256,-469,-962,565,-1000,-728,-291,-67,-602,1000,911,457,-488,-1000,-1000,-1000,-57,568,-925,40,-745,156,295,888,-380,-558,1000,766,407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.MissingNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "path(int):com.fasterxml.jackson.databind.JsonNode",
            new int[]{653,-40,-878,608,458,131,410,775,-767,384,127,-301,-621,-517,-909,481,-519,427,414,786,384,708,680,-324,696,-549,-392,896,-33,727,-128,-131,541,204,438,668,68,967,-338,53,42,-201,650,296,-666,-469,954,963,-310,155,-474,92,431,-691,40,-745,156,-506,-806,904,188,886,-606,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.MissingNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "path(int):com.fasterxml.jackson.databind.JsonNode",
            new int[]{570,564,-233,475,44,243,173,-136,-952,47,-1000,-786,221,647,211,-429,-636,1000,350,-488,536,-259,873,-103,583,-969,-835,622,232,-732,-959,630,-643,-864,-1000,903,-583,-102,1000,839,-953,361,457,131,1000,-380,-101,232,-490,-958,-454,-951,802,-1000,617,603,-1000,-449,-645,578,-735,1000,661,319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.MissingNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "path(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-104,106,890,-339,854,628,-433,201,-58,611,-75,944,8,-363,-90,797,-321,-867,-222,539,116,-223,324,275,-143,-432,-1000,-404,546,147,463,-1000,1000,704,126,213,1000,1000,329,-475,-429,-1000,1000,595,571,-568,666,40,373,244,215,-1000,-620,-707,-95,-906,828,704,943,-173,966,-418,21,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.MissingNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "path(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-29,-1000,-467,-697,559,413,1000,775,1000,-686,393,-105,1000,654,-999,-1000,754,-601,-1000,-877,-1000,-1000,106,45,-601,1000,-1000,1000,-1000,-1000,-473,515,-369,793,-1000,401,1000,-581,-781,-1000,1000,-700,-566,-1000,-577,813,1000,-830,-991,-836,-1000,787,825,1000,932,1000,1000,922,-914,16,1000,636,-957,932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,boolean):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-274,921,842,190,-801,-227,-81,375,42,-1000,-79,-1000,-1000,1000,-498,-205,73,699,971,507,-156,-1000,699,1000,834,343,622,-1000,499,420,-75,1000,120,411,839,613,1,98,164,249,123,-248,235,931,-1000,-349,-94,-1000,-1000,230,1000,793,-158,-1000,276,-1000,-186,-393,-107,1000,1000,-426,-222,-111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,boolean):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{1000,787,777,687,-245,-41,138,1000,1000,466,-107,-1000,-325,1000,-405,-701,1000,1000,-1000,-331,627,1000,1000,-398,461,352,-612,-1000,1000,827,-443,1000,245,1000,-1000,-455,1000,169,-701,51,1000,1000,718,-1000,-70,-145,-618,-1000,1000,665,289,-527,-1000,-475,1000,71,1000,-992,-1000,1000,1000,192,-676,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,byte[]):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-899,-519,749,666,-45,-456,1000,466,-1000,-458,543,829,-1000,1000,-1000,131,-1000,-887,1000,-866,327,112,-475,-910,1000,-1000,830,84,39,362,-508,-515,-724,-1000,-485,-515,-1000,361,708,43,264,-1000,1000,-1000,-830,204,182,-869,-33,-1000,308,-1000,-192,445,-1000,650,239,343,209,337,18,-152,-819,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,byte[]):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{433,269,-1000,959,-706,142,-330,624,-2,-748,-732,-612,-703,-309,908,910,465,59,-228,469,213,979,-327,188,959,-203,266,680,271,-584,-117,928,-3,-1000,962,963,-132,341,-312,1000,-1000,-1000,-1000,-592,657,-1000,-343,338,261,-756,-100,-355,-611,-1000,-672,779,-239,934,74,-147,31,-341,1000,372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,com.fasterxml.jackson.databind.JsonNode):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-461,-447,1000,1000,179,-40,-497,1000,677,-841,-122,552,-492,788,-156,1000,845,609,1000,530,1000,1000,-687,203,460,692,518,-436,71,50,787,352,-648,713,736,-213,-245,-432,1000,-76,-936,421,783,1000,956,-732,754,212,-1000,-124,417,-763,855,153,-601,-148,-38,795,1000,-1000,692,-726,202,653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,com.fasterxml.jackson.databind.JsonNode):com.fasterxml.jackson.databind.JsonNode",
            new int[]{1000,-596,913,748,264,-917,47,189,-579,-841,-284,-1000,1000,1000,-156,1000,421,-142,-34,490,-75,-1000,837,203,-901,692,4,214,1000,-1000,25,-1000,1000,-199,-975,-213,1000,-1000,537,411,-281,-454,15,-452,-368,393,591,356,-1000,-1000,708,290,855,153,801,-594,-298,-381,1000,188,-443,-29,-1000,668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,double):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{662,-1000,1000,-255,719,1000,497,-276,-1000,-1000,-93,-175,-1000,-1000,-1000,209,278,544,-1000,-427,-817,-861,409,11,-760,-767,-534,1000,1000,375,941,1000,957,1000,818,1000,1000,413,288,-68,-331,-1000,684,528,-849,-238,135,461,370,1000,399,-1000,-890,81,-721,16,-116,-1000,-983,386,-850,-800,-1000,-987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,double):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{639,40,39,-76,509,-95,-1000,500,-618,240,1000,-281,-157,-595,-271,-382,-18,-555,-686,-1000,-534,50,245,-54,-762,-928,1000,-930,411,-665,697,777,244,-274,1000,-1000,748,-708,643,-544,669,400,-218,38,561,666,740,-1000,-717,-504,-38,-408,-899,591,1000,1000,-791,1000,-20,475,198,1000,-36,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,float):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{79,89,549,754,347,216,-987,403,793,-1000,-576,-701,717,-62,676,404,779,923,-1000,863,-1000,-1000,-1000,367,-1000,830,-148,640,-1000,-335,-1000,-55,-1000,-1000,662,-159,-1000,73,-1000,-272,1000,925,-1000,-1000,-898,792,-718,-1000,-1000,131,-808,538,1000,1000,-583,905,-768,892,1000,-149,-1000,252,216,123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,float):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-580,96,663,1000,784,-827,-86,981,-390,31,-9,-1000,432,412,-36,-246,180,249,-1000,591,-1000,-1000,-737,1000,-54,740,314,1000,-385,-241,-1000,-1000,-1000,-1000,939,478,-932,644,-990,206,1000,971,-220,-1000,-612,108,-364,-564,-1000,-391,102,891,1000,167,-1000,-35,34,763,1000,-648,-1000,-91,671,-688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,int):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{809,24,-876,-40,-476,-376,-163,-481,-179,-786,-343,372,-255,96,753,326,279,-688,1000,-128,-261,883,-773,178,-50,-103,-19,662,213,-502,1000,824,584,-92,231,-723,-1000,503,-130,-1000,-175,279,1000,-156,-824,438,-806,-140,601,-875,765,-246,-792,430,-420,-410,122,-1000,295,-890,-213,152,-348,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,int):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-331,-154,-143,-691,159,-456,1000,-250,539,-823,125,-925,680,-725,1000,211,-920,-272,1000,225,-141,-828,-1000,127,533,-720,-1000,-964,-367,871,-390,-1000,-845,-402,-498,-441,-995,1000,1000,358,-1000,769,708,708,474,-648,1000,-709,-728,-1000,-141,-451,-10,908,-99,-14,-328,-666,-877,898,1000,-907,860,544}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Boolean):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{100,1000,-621,873,818,538,143,1000,1000,-187,92,-619,116,-182,769,106,-652,-341,-924,-693,507,76,-420,1000,-847,-1000,925,516,-605,1000,-353,-773,919,378,-1000,647,705,-1000,213,-635,1000,137,-357,-1000,-465,91,-1000,-1000,-636,-985,741,-662,683,528,441,771,28,-1000,-505,-65,835,-407,-685,805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Boolean):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{993,35,814,1000,-157,-520,-1000,-1000,-1000,-656,161,-786,-1000,-295,34,-179,218,746,-120,562,-294,-546,279,-223,42,1000,-807,599,560,-217,-1000,-589,15,1000,-387,-834,-625,-7,958,772,-643,724,-967,-643,-1000,1000,-413,602,-567,904,392,496,554,-273,76,-107,74,-517,1000,1000,-1000,779,-441,162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Double):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{671,-855,262,570,-1000,-671,-609,-506,792,-829,399,1000,-300,-16,-504,437,146,-598,-277,-1000,595,1000,-147,-500,1000,-903,525,-1000,533,-727,-1000,209,977,-1000,-884,-359,-110,-686,-780,-9,-327,54,76,369,-646,-1000,349,171,-1000,-1000,-448,-1000,297,-1000,-950,-328,-559,-169,102,250,310,-14,89,800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Double):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{1000,-68,-712,487,339,18,-1000,-75,990,-239,326,347,-370,315,-1000,1000,-1000,1000,126,-1000,1000,-1000,5,-795,-118,-588,-1000,-809,54,-1000,231,-1000,794,-1000,-1000,1000,1000,-1000,519,-1000,1000,1000,-774,1000,-124,-1000,511,300,-411,-262,-1000,-701,1000,616,937,-653,-1000,1000,-41,-587,843,-345,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Float):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-51,-245,-1000,-1000,-244,36,-603,-130,-826,-783,1000,497,1000,-194,1000,1000,455,786,23,3,533,-340,-659,-307,1000,356,-577,-1000,-62,221,1000,978,1000,1000,1000,-940,472,-811,-1000,-75,-365,1000,-600,-315,1000,-484,40,-666,-137,369,-1000,-573,-1000,230,106,1000,-1000,1000,-244,-139,1000,-617,-730,135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Float):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-1000,-127,435,890,-1000,-876,-237,-318,-96,21,132,312,-50,-29,688,853,302,238,-209,770,-798,-744,749,-45,805,173,327,1000,492,1000,1000,89,1000,370,360,1000,-1000,-222,-627,-969,-661,400,513,257,1000,-643,-1000,-829,-469,-886,384,288,170,1000,736,741,-187,895,319,298,131,-456,658,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Integer):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-402,587,701,-263,-407,-1000,-230,-1000,-177,-279,-207,-939,752,-795,619,1000,514,1000,-186,-767,213,-254,-1000,-897,873,-299,-102,-207,-589,701,-537,408,1000,-656,470,858,149,-1000,752,574,41,-836,183,-279,275,-931,40,1000,452,-923,-323,-874,270,-351,-565,-89,273,-516,799,-1000,-1000,-537,282,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Integer):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{647,-1000,-530,940,-186,4,-1000,1000,573,966,-675,1000,202,1000,-73,-1000,170,-1000,-927,1000,722,1000,1000,-117,-1000,-1000,-548,-944,434,-71,-127,900,264,268,-181,654,91,1000,-215,-407,-594,1000,109,980,-179,521,708,-716,550,1000,825,1000,-1000,765,1000,-177,-1000,-677,-172,-270,-72,604,-273,-651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Long):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{828,-244,668,-149,-701,435,-289,470,1000,-549,-185,-992,-687,-582,-1000,-501,331,298,-416,207,658,782,1000,123,-308,136,-668,159,-85,1000,-1000,-1000,522,-640,-919,-810,-482,-895,200,-466,925,-120,934,384,-1000,-1000,140,586,-341,-917,-738,-776,-1000,335,-131,-1000,-377,-638,-510,963,1000,888,1000,-711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Long):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{280,1000,1000,988,-351,-115,-347,-87,-583,-1000,593,-18,-164,-756,-1000,-379,766,-673,-526,1000,437,1000,1000,-191,-205,29,-103,-137,-583,356,-1000,489,839,-427,-690,294,-1000,-162,307,-572,1000,1000,152,64,192,154,768,1000,-1000,-796,-738,-815,-1000,565,919,-656,-658,-361,-754,-1000,1000,1000,160,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Long):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-910,1000,1000,1000,-701,-708,308,388,226,-289,767,460,434,-865,929,-210,-90,357,555,1000,-463,-155,-1000,298,-352,1000,959,633,938,-984,-973,244,522,-640,826,953,-1000,-1000,651,11,-512,-120,-33,-335,444,-475,518,1000,519,-392,-219,-596,-1000,335,1000,-335,-799,-638,449,-720,995,109,-550,-447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Short):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{72,-591,306,754,902,1000,-1000,244,-458,12,-399,-17,-331,-1000,-471,537,72,401,172,-686,850,98,754,-209,128,-151,272,-1000,-211,-116,551,244,-592,412,192,-433,-55,313,-141,-370,-183,-390,-961,755,-787,-403,-939,722,299,-442,1000,784,-94,229,286,1000,-741,-180,767,-783,104,-791,92,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.Short):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-240,-938,-368,713,359,750,-534,992,-531,203,-111,763,-326,-1000,114,537,-722,35,420,-784,1000,-141,536,193,27,-232,58,-1000,-1000,484,281,-293,-330,-326,790,-186,-1000,103,-519,-454,56,-597,-1000,829,-571,-517,-380,692,-416,-236,384,1000,-348,1000,-466,805,-37,-822,-1000,-927,373,-96,160,193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{320,711,339,-1000,158,979,267,-1000,-906,-180,1000,391,-165,-153,434,-81,753,188,-304,53,1000,1000,-1000,-244,785,-55,-1000,-560,649,-220,-1000,259,1000,-393,1000,-338,-398,-402,1000,457,649,-284,1000,-745,1000,-812,143,-111,183,617,-138,1000,722,-1000,-11,425,-207,-580,-1000,787,-191,285,-820,-125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{321,12,-400,72,184,48,458,1000,-232,-197,-322,-87,-361,-321,-1000,81,-167,313,482,161,-1000,-650,231,-127,-560,55,42,716,-407,204,338,-205,1000,321,-293,-152,702,-1000,1000,-1000,-649,-818,-902,297,1000,572,-419,-400,125,1000,-400,-140,-443,450,117,702,-182,752,261,-133,1000,902,1000,-758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{745,823,339,-733,-172,1000,629,-734,-1000,19,779,-41,-213,-997,173,1000,344,94,270,-333,1000,623,-955,-867,785,-354,-1000,-97,553,144,-758,362,215,-919,1000,-338,-256,-833,967,-28,1000,-289,854,-504,954,-762,-431,-961,-157,-1000,-138,1000,-380,-1000,-478,494,-581,-695,-646,281,-715,371,530,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.math.BigDecimal):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-577,-443,-334,-766,-366,1000,637,210,1000,-953,-112,450,-300,1000,918,-342,1000,819,154,858,-1000,223,-247,396,966,-319,858,236,513,-178,743,-411,523,-468,473,-486,253,230,655,-1000,41,-311,-132,1000,140,417,281,743,-1000,1000,8,-1000,889,-476,576,-254,119,385,62,-1000,-432,-192,-400,579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.math.BigDecimal):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-1000,-1000,-220,777,494,-56,436,-290,610,-762,1000,-1000,-202,-1000,137,-1000,-659,1000,194,-773,-1000,113,-65,434,-191,-1000,-659,1000,-914,511,826,304,699,1000,354,-47,795,-1000,-647,576,562,-1000,-1000,107,-1000,1000,148,-38,-36,-1000,760,400,696,-570,-69,-202,439,-1000,-135,1000,496,-1000,-1000,83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,java.math.BigDecimal):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-337,-911,365,1000,660,444,-1000,471,217,-1000,217,-1000,-331,-1000,1000,1000,1000,-215,1000,669,-297,-433,-1000,-1000,-1000,1000,-472,244,-1000,1000,930,-162,1000,631,687,-1000,460,1000,-118,-1000,1000,511,-716,361,178,1000,1000,-1000,71,547,208,-1000,224,-981,1000,255,1000,-247,-893,238,399,-847,-794,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,long):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{277,-910,635,675,454,-67,-744,334,-249,-465,171,-193,-504,-140,-1000,-574,436,285,340,879,-210,17,-1000,1000,-440,-590,-621,-1000,284,-495,-242,1000,1000,-1000,-697,-543,356,414,-279,-973,1000,-218,-448,-849,-562,-388,-38,-1000,21,-481,-204,1000,-668,-715,396,-76,-749,-6,84,-1000,318,-562,610,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,long):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{262,-6,631,-559,504,-84,-277,1000,997,169,-743,1000,629,-235,-1000,-1000,1000,-1000,-722,-336,320,-449,548,1000,743,794,-91,-1000,836,342,1000,1000,383,-1000,134,-1000,1000,-324,443,90,1000,-794,-1000,-1000,1000,-575,-592,-1000,1000,-1000,488,1000,-553,1000,188,278,-865,789,802,-1000,678,618,-975,-685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,short):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-734,928,-1000,-1000,119,-1000,563,569,-1000,730,-915,-598,189,-916,-37,-1000,176,-85,199,-1000,1000,999,-1000,-290,154,182,-1000,1000,-201,1000,414,304,-5,-300,-1000,88,1000,273,809,502,-271,945,410,-1000,973,-607,161,-726,921,751,-535,47,-1000,-337,-79,408,-1000,-797,658,-598,606,-621,419,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "put(java.lang.String,short):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{1000,-882,1000,-1000,-451,961,-807,-1000,1000,-280,269,-1000,762,1000,962,-1000,-1000,868,-870,-530,1000,1000,-800,-1000,693,1000,-1000,213,-1000,-1000,-1000,-604,1000,588,865,343,-1000,591,777,1000,120,82,-600,1000,842,314,125,-1000,-743,-164,245,-1000,-1000,-282,-848,1000,-491,1000,402,815,1000,-1000,552,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putAll(com.fasterxml.jackson.databind.node.ObjectNode):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-202,-1000,1000,1000,109,87,-949,447,1000,1000,-649,-148,1000,-645,556,-1000,334,-766,-388,737,-800,97,311,134,-128,1000,254,1000,-1000,665,860,-643,31,-908,-1000,-671,-274,-1000,895,-606,-1000,148,-828,-1000,342,-432,625,855,832,920,327,-128,421,685,-31,1000,-771,562,550,-141,-344,663,-576,-234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putAll(com.fasterxml.jackson.databind.node.ObjectNode):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-996,615,-67,-595,642,-220,-493,-930,-753,-162,8,-696,-935,197,-908,-974,-306,739,-905,-376,908,483,-553,621,-286,-850,-469,-980,109,-179,245,-250,100,-510,-97,-130,-265,631,-407,951,124,673,592,407,715,-323,63,-522,483,-584,854,990,76,-382,962,107,484,-874,-33,-161,-688,-581,-406,256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putAll(java.util.Map):com.fasterxml.jackson.databind.JsonNode",
            new int[]{1000,-1000,-1000,-1000,599,-808,481,35,1000,685,-896,1000,-400,-885,478,-1000,1000,-1000,-1000,1000,1000,1000,305,-1000,-1000,1000,-806,-1000,132,1000,-310,-173,-629,213,-1000,-345,-1000,-56,-376,-511,484,977,-740,-1000,283,-1000,881,938,-1000,400,1000,-665,-1000,299,-1000,-1000,881,1000,808,-874,-722,-442,-55,-980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putAll(java.util.Map):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-971,-324,575,-1000,809,80,-96,-408,240,924,-1000,-597,1000,319,-100,-176,683,-628,1000,-223,-540,-457,1000,-226,-1000,-580,975,-827,644,837,-1000,887,1000,-1000,485,1000,-922,780,-1000,1000,1000,1000,-1000,765,28,-1000,-11,-685,980,-974,1000,680,1000,1000,-344,466,1000,-254,1000,-1000,135,1000,117,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putArray(java.lang.String):com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-1000,-261,378,-87,-1000,484,1000,1000,858,-438,719,-247,853,1000,1000,1000,-220,736,262,19,507,-735,432,-1000,709,-28,-739,-292,-229,-676,1000,-218,182,-54,-732,372,266,-1000,709,-157,339,1000,1000,269,808,-1000,1000,1000,758,-929,-280,894,-1000,1000,-557,84,-814,-385,892,269,-414,-652,1000,393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putArray(java.lang.String):com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{369,-261,981,-1000,-1000,284,-144,-1000,-1000,1000,606,819,-138,580,148,-1000,271,137,-849,149,-793,1000,432,1000,186,-348,792,1000,162,1000,-1000,-538,115,-54,1000,-1000,-655,-324,709,952,-1000,-1000,1000,-568,-1000,-577,-447,586,-1000,433,366,-992,188,846,-195,1000,1000,1000,-274,404,544,-67,-1000,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putNull(java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{161,-628,-906,-284,-641,-176,-31,459,218,-398,-927,225,-699,349,-297,-226,396,-913,-176,-625,356,-972,402,-802,39,582,465,-337,-919,431,-244,564,489,552,297,-774,-913,349,303,-382,694,-286,-50,415,564,724,306,310,128,962,355,-450,348,-14,516,-398,89,341,293,171,866,-293,797,345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putNull(java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-637,-245,-61,176,1000,-41,941,150,-1000,159,366,-1000,-154,349,647,-1000,-135,27,-42,-474,1000,1000,247,-578,797,582,-667,-1000,-563,1000,-1000,564,1000,1000,499,732,-34,349,-275,-382,423,-286,835,-1000,-552,-149,-361,-718,337,-387,-598,60,648,-54,227,1000,581,-282,-1000,314,392,-696,687,-390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putObject(java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{996,681,1000,428,-801,172,-651,-449,-246,1000,-595,610,-1000,-1000,-1000,986,-267,589,307,869,-1000,-207,342,160,-529,-1000,-87,1000,-318,-120,-1000,-374,1000,771,1000,-1000,202,-309,-400,359,363,-279,-104,-896,1000,184,-553,347,870,-757,-709,1000,843,461,-45,-40,85,-940,572,-106,281,-522,-705,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putObject(java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-452,-806,561,296,198,188,299,-454,482,529,238,48,508,336,569,-707,-84,749,-704,264,-564,-422,4,-829,-379,-446,234,-298,-71,-923,-515,620,-917,-520,-997,-635,693,763,982,899,484,-456,601,457,-822,512,354,-903,43,71,601,-21,453,62,920,-846,406,443,-105,-677,-108,654,325,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putPOJO(java.lang.String,java.lang.Object):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{1000,-506,1000,-1000,-246,833,1000,-603,-355,-781,1000,-135,872,-440,-500,233,-621,-827,-865,-224,876,-201,-1000,1000,-1000,1000,-280,1000,617,798,576,-830,-1000,-726,1000,-985,524,617,-1000,-1000,-1000,-1000,798,879,229,1000,1000,1000,-865,-92,172,3,-711,-1000,1000,1000,1000,1000,1000,1000,1000,-1000,261,-737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "putPOJO(java.lang.String,java.lang.Object):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-790,726,-32,644,-166,-845,-1000,599,579,-1000,421,-48,-1000,-417,1000,-579,-1000,1000,-931,910,-932,534,662,-281,-1000,108,346,-664,-85,-1000,127,163,537,151,1000,745,-1000,-199,826,-599,441,-807,1000,-369,-932,-1000,-128,546,1000,34,923,1000,-808,995,-456,-1000,-981,-736,1000,-320,-1000,270,11,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "remove(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{1000,-1000,-1000,-364,329,-397,681,866,-1000,639,-207,-794,233,-1000,102,509,1000,-839,74,-1000,-632,-297,129,-1000,1000,-671,373,-659,965,758,-3,869,-245,-966,1000,101,-1000,-855,1000,-1000,1000,-362,1000,-245,266,-474,-400,-192,238,932,518,-25,-129,-382,-726,598,-701,437,342,-1000,-250,884,-181,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "remove(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{424,-1000,-131,508,544,-329,947,900,-1000,1000,-99,240,-392,-333,115,296,221,44,455,228,-327,-370,30,-1000,232,-644,481,-287,12,-108,-429,477,854,-39,780,9,-704,-463,-244,560,614,-337,-504,-99,-191,-282,92,-1000,-338,322,-8,-1000,-178,-549,-595,407,42,217,-174,-1000,-141,-1000,739,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "remove(java.util.Collection):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{111,-781,-511,-339,-455,429,-686,-1000,1000,688,77,168,1000,137,-18,324,400,854,170,-1000,172,138,-400,-454,409,-75,1000,688,457,607,224,141,1000,-1000,-1000,52,1000,-473,-228,-93,961,733,-1000,-1000,1000,225,-669,-101,-241,-69,574,-788,1000,-396,-879,414,-746,-357,540,-300,1000,-1000,-515,-717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "remove(java.util.Collection):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{1000,1000,-934,706,324,-364,-681,-477,206,-547,964,-1000,-118,673,551,1000,673,-1000,-183,-881,-344,280,1000,-549,998,-1000,-1000,1000,-1000,607,-595,-1000,-1000,488,-512,105,1000,-1000,-62,908,-1000,-1000,-1000,78,-1000,1000,68,-400,215,1000,-653,-1000,352,543,-192,808,-1000,324,63,1000,846,-515,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "removeAll():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{192,-163,319,920,335,39,438,-80,580,574,-895,-225,93,-532,360,1000,54,-14,-836,352,607,706,-106,281,636,-276,755,668,431,965,365,639,-184,261,52,-767,-589,540,-36,182,217,348,178,48,588,-334,-425,-1000,-261,242,-58,418,239,-728,1000,168,328,-510,167,971,1000,198,-196,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "removeAll():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-505,-144,292,-746,-956,-223,-137,-403,653,267,-531,-865,-812,647,-533,-788,-191,-273,-500,212,-77,762,128,-753,718,611,-452,173,-903,956,744,-310,-224,-249,-648,924,750,566,-965,-387,-614,-105,-791,-225,977,129,-387,-453,774,430,-626,-176,206,-525,-995,231,308,746,58,-984,200,840,-946,-494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "replace(java.lang.String,com.fasterxml.jackson.databind.JsonNode):com.fasterxml.jackson.databind.JsonNode",
            new int[]{1000,1000,-1000,5,-111,469,348,136,772,863,599,-182,-562,61,561,-227,-31,403,55,-859,647,-39,-1000,287,1000,86,1000,375,910,-982,-659,1000,-599,-1000,860,-225,-240,-637,693,-927,1000,-169,-1000,148,-260,1000,248,378,1000,722,537,1000,-881,-145,896,-911,235,1000,-123,302,-31,425,-477,314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "replace(java.lang.String,com.fasterxml.jackson.databind.JsonNode):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-365,-240,694,-1000,-861,710,-338,1000,-224,-1000,-925,-1000,444,1000,-575,1000,-70,-310,-424,337,326,243,-604,-1000,1000,1000,-788,-1000,-650,-1000,29,141,278,-10,-171,-718,-1000,-330,1000,371,-67,-278,217,-281,-1000,900,1000,1000,228,1000,1000,235,-808,-809,-1000,503,245,658,-222,961,413,234,769,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "retain(java.lang.String[]):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{637,431,606,651,138,954,387,-751,-496,-584,-1000,97,303,248,-451,-223,172,200,511,-442,1000,-320,-341,376,-160,-805,-904,-791,231,-741,-579,-333,412,673,-165,-614,790,-793,8,305,-32,137,355,-864,632,-262,-115,94,507,13,-183,660,-560,453,522,-195,439,-351,253,504,221,-298,-695,273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "retain(java.lang.String[]):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-495,82,-530,438,-207,-954,619,836,858,1000,539,-780,146,-131,236,-223,172,-1000,511,192,-1000,583,228,214,174,382,1000,702,-397,388,1000,-841,352,-961,-142,193,-869,-36,-741,-1000,897,137,-28,-80,415,150,1000,669,-176,-1000,940,-753,-560,-493,-216,391,-723,-150,-353,504,-378,-298,-168,-474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "retain(java.util.Collection):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-453,-8,-217,241,589,1000,-391,1000,-316,1000,707,259,-967,197,-574,-1000,-1000,-218,414,-1000,-117,119,-576,-560,507,703,-822,9,-769,-27,104,-551,252,-609,1000,-341,6,-872,1000,1000,-299,1000,-1000,-713,-691,-1000,553,874,-311,-370,-1000,-638,-1000,-580,1000,780,-1000,378,-261,142,-287,-316,299,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "retain(java.util.Collection):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{105,869,312,-312,525,644,-1000,-343,1000,933,1000,24,-313,755,-606,-747,-927,-404,444,-424,96,84,-850,-510,749,991,-935,-22,-902,1000,-893,1000,235,1000,532,836,1000,-1000,-51,518,-55,206,-353,1000,-446,-17,723,216,-296,1000,-944,-431,-187,163,57,5,-406,157,-171,-176,978,-81,349,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "serialize(com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-368,-81,-398,780,-633,1000,824,-625,784,813,-349,343,-45,668,488,1000,120,946,-1000,-825,-876,233,480,483,898,776,254,-825,126,-1000,-788,-271,-415,492,-257,168,1000,-959,1000,161,-610,1000,1000,-2,-88,-638,-176,1000,-478,-1000,853,-428,32,1000,144,345,-49,-1000,1000,-1000,-417,56,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "serialize(com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-144,-973,-28,688,-840,939,-993,1000,1000,226,136,-667,913,9,-261,256,2,529,1000,249,77,-64,488,534,872,249,195,-895,1000,-824,664,44,-1000,518,510,-574,270,-793,-1000,482,-730,1000,-125,-618,232,-738,155,877,1000,-449,1000,-239,579,824,188,448,677,-219,279,-379,-563,-272,-1000,770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "serialize(com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-96,120,-1000,-1000,488,307,1000,-697,-515,165,147,1000,-255,-606,97,-1,-844,488,-1000,-2,-251,300,-508,-695,822,442,-911,1000,385,-1000,-646,-1000,68,-68,145,-1000,218,-15,-800,413,58,-1000,147,-886,-9,-979,606,-25,447,34,1000,-609,-534,-351,325,-600,581,-45,-46,89,-1000,-490,-367,600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "serializeWithType(com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider,com.fasterxml.jackson.databind.jsontype.TypeSerializer):void",
            new int[]{353,-708,951,-553,729,219,-1000,-222,344,308,-743,-161,325,673,-410,746,-973,-1000,1000,-863,80,153,-1000,1000,1000,-459,-611,1000,-184,469,746,749,245,1000,808,408,-324,-983,-408,989,447,146,-646,-1000,1000,1000,1000,-573,-598,436,232,117,723,859,900,-796,-451,910,1000,-1000,-1000,-686,890,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "serializeWithType(com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider,com.fasterxml.jackson.databind.jsontype.TypeSerializer):void",
            new int[]{849,290,-256,935,824,-937,-706,229,-79,-391,-325,-771,718,521,-863,-177,11,379,-738,648,-464,542,-98,512,957,4,-909,657,-918,213,219,-749,10,918,919,271,978,-972,577,571,46,-742,148,-692,418,-122,-951,-831,226,-710,-696,845,-115,-655,834,-927,-254,-685,-214,236,734,280,168,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "set(java.lang.String,com.fasterxml.jackson.databind.JsonNode):com.fasterxml.jackson.databind.JsonNode",
            new int[]{357,-655,-386,-811,3,-473,1000,387,-1000,826,-11,47,-465,284,-611,-919,35,-420,-186,97,-85,-142,286,-1000,109,500,-1000,44,442,1000,1000,-874,-1000,-4,1000,-475,-241,1000,144,836,-779,-251,477,343,147,-63,1000,-852,-74,1000,-92,-499,885,676,752,137,-5,698,127,-300,537,-283,-849,-317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "set(java.lang.String,com.fasterxml.jackson.databind.JsonNode):com.fasterxml.jackson.databind.JsonNode",
            new int[]{357,-1000,387,-1000,3,-139,909,100,-989,826,166,-482,-360,-283,-668,-308,35,-375,-345,905,-365,-239,596,-1000,-303,269,-794,707,442,1000,1000,-186,1000,-366,1000,-1000,-412,-189,-1000,231,-10,1000,-386,1000,-1000,387,-969,-696,1000,504,-530,313,1000,304,752,1000,586,-22,168,-1000,-488,-392,-1000,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "setAll(com.fasterxml.jackson.databind.node.ObjectNode):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-73,-136,63,-81,74,671,755,569,-56,464,-379,-57,-405,441,484,282,-146,-151,519,504,-1000,-125,-183,-916,192,-519,234,287,-875,-133,706,-1000,699,-384,183,-378,-1000,595,388,-949,-453,-551,-472,748,489,-1000,-339,891,337,87,-140,-110,766,207,442,134,284,-131,-177,45,393,414,-444,326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "setAll(com.fasterxml.jackson.databind.node.ObjectNode):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-737,683,149,-415,-14,-568,452,282,-571,477,-324,-102,-718,-190,1000,-149,-689,-1000,-162,212,925,822,-133,888,540,-805,493,-244,-1000,216,594,272,-913,-490,916,-620,270,-119,-1000,278,-706,226,-303,-30,171,-358,970,-406,821,136,557,-253,1000,150,-1000,173,-799,1000,-74,92,867,86,530,-216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "setAll(java.util.Map):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-458,1000,-400,-1000,-161,1000,-283,748,1000,378,-693,1000,-1000,923,-1000,-1000,1000,-1000,-687,765,-552,-901,323,-601,-684,777,-1000,-716,1000,566,-1000,-169,64,855,108,-1000,1000,889,-914,1000,287,290,-1000,739,967,-80,-1000,613,-916,-441,-332,1000,-511,-1000,-709,745,-400,205,-252,555,391,-684,-360,548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "setAll(java.util.Map):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-224,1000,-1000,-112,834,303,34,914,907,-107,-570,-706,155,-227,-266,-730,-61,-433,-1000,-820,-1000,-23,-524,1000,-315,819,626,-508,709,-680,927,-554,1000,156,431,-294,1000,713,-659,252,583,542,402,-775,93,-1000,-287,1000,-163,1000,1000,68,1000,141,10,-703,-1000,-1000,-1000,-263,-26,-68,-230,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "size():int",
            new int[]{-460,582,-27,505,-56,729,274,-236,-1000,-388,1000,-827,536,-514,903,927,-360,64,754,538,179,-1000,-740,1000,713,-656,270,-560,123,-221,583,3,-1000,460,291,-1000,448,874,663,-1000,-1000,752,-963,380,1000,-1000,1000,433,1000,473,1000,-1000,928,1000,559,-574,-1000,674,390,113,-761,-10,-52,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "size():int",
            new int[]{-728,-82,118,-1000,558,193,53,827,-664,476,947,-1000,468,461,-460,1000,-195,1000,-100,101,-344,-741,-902,648,-286,-794,-530,-20,1000,930,622,-1000,-1000,357,-724,-303,-923,1000,-163,-459,-640,432,196,1000,753,-449,755,761,57,211,873,-20,795,-1000,926,-499,-894,400,-682,-541,502,-234,516,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.String:eyIrMHg4MCI6bnVsbCwiICI6bnVsbH0=", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "toString():java.lang.String",
            new int[]{770,-176,-817,-1000,-161,460,32,405,791,-367,343,-358,1000,-711,-300,-758,-1000,136,944,-948,-357,1000,-196,-758,-699,-471,466,1000,-379,982,-677,-414,459,-473,-956,381,281,685,1000,1000,-662,-254,1000,265,-183,504,-367,62,733,-386,709,586,1000,487,-223,1000,11,715,1000,-1000,-582,1000,-647,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "toString():java.lang.String",
            new int[]{-570,-683,468,-262,509,-882,53,522,-437,-741,923,445,-143,-710,-548,737,226,688,906,603,-650,859,312,415,322,833,-276,995,-555,468,-491,638,675,873,192,-907,-81,-568,599,23,-441,347,-421,690,953,929,402,-467,-552,990,-900,-846,473,-333,471,-231,665,723,237,663,649,2,959,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.String:eyItLTB4OCI6bnVsbH0=", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "toString():java.lang.String",
            new int[]{397,-556,-156,368,989,-1000,113,776,-288,673,1000,441,-520,-360,-653,1000,1000,1000,171,736,-469,2,335,-975,773,-570,-503,-207,-797,-383,97,971,955,422,-505,208,86,-775,855,-507,401,725,-1000,751,738,-26,796,-1000,-1000,113,-948,-747,-16,-121,1000,-254,-865,1000,144,1000,1000,1000,1000,-590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "with(java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{1000,-152,1000,-157,584,746,1000,221,878,-1000,21,616,-225,-158,267,874,-546,141,809,815,1000,-387,-12,-1000,-604,-149,-1000,404,-1000,1000,238,-740,-732,-1000,696,-117,802,1000,-846,-910,1000,1000,804,1000,426,400,-1000,-932,-770,584,-646,-1000,511,-1000,-944,841,-871,-654,877,-23,18,-657,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "with(java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-416,611,-317,268,-698,873,22,178,-510,-181,-665,-598,912,-1000,-870,-496,601,381,-543,387,154,-767,-300,-588,-1000,799,966,-862,-363,201,1000,-1000,928,-254,-239,-620,591,-1000,2,-383,9,-1000,1000,400,-88,-890,-186,-160,-294,569,1000,200,203,-40,248,1000,807,-56,-162,-483,310,-27,708,-7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "with(java.lang.String):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{573,-442,-27,578,-321,-79,159,527,972,-974,-638,-348,914,-784,-453,167,343,698,998,-656,335,672,416,-998,-655,-162,-565,282,-403,602,-227,-284,-940,-835,-360,-274,347,607,80,-104,-612,64,-568,629,841,132,-739,138,-157,774,-207,-906,943,-867,-885,739,830,946,257,-251,-181,494,-399,756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "withArray(java.lang.String):com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{362,546,278,-521,924,171,-326,-455,346,935,578,90,87,831,615,747,-490,-221,125,-448,493,845,754,547,-841,823,-540,101,-834,-223,-551,522,28,622,598,305,-479,371,586,-793,-591,293,96,-800,425,891,-758,-664,-270,-648,-328,-432,-68,-76,-325,-357,-860,-484,60,341,499,-222,588,-490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "withArray(java.lang.String):com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{314,1000,-400,595,-587,-1000,-582,-574,-522,-1000,-411,-337,238,-603,-1000,833,-27,-453,-409,758,-288,-110,-525,0,511,209,145,118,507,972,47,-400,187,-612,79,-100,-1000,100,-335,-688,400,999,-357,-502,336,884,-589,518,403,185,-651,674,-618,-181,-705,738,-494,-730,-623,1000,400,-976,-400,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "withArray(java.lang.String):com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-875,-177,269,421,199,-399,316,-71,177,354,-1000,991,980,305,-1000,-1000,35,929,89,-637,-737,-428,-1000,-132,400,333,-942,-202,449,669,-365,733,333,-1000,365,-1000,105,986,-1000,-2,-519,-669,343,916,1000,-570,-252,-1000,-63,-548,915,-165,-343,-950,-4,400,-544,715,205,-361,-137,-324,82,352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "without(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-478,-138,551,855,824,-97,733,-246,-846,947,346,591,436,276,-431,605,141,677,-92,-961,-54,832,-213,863,211,-868,-696,11,-58,146,804,573,601,-776,-698,-480,639,-780,938,934,-38,155,624,-493,-356,-190,-161,79,183,680,136,403,-673,510,-675,-39,101,-281,-448,692,-997,-596,-507,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "without(java.lang.String):com.fasterxml.jackson.databind.JsonNode",
            new int[]{415,886,-52,-154,494,-391,-1000,65,413,-598,580,-42,81,38,423,763,1000,624,-1000,-210,73,817,-668,160,74,-1000,-461,347,-363,-313,-121,8,-454,-1000,128,1000,503,-517,30,-332,-1000,-835,1000,252,113,-348,951,273,508,311,594,653,-538,228,345,-762,-490,146,-244,1000,-929,-503,-374,126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "without(java.util.Collection):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-1000,219,-284,78,1000,-956,687,-113,-646,-356,-1000,-438,565,871,815,-226,1000,-146,321,-135,-931,-1000,-44,1000,681,224,1000,649,969,613,-452,-884,566,-24,321,115,-1000,658,-923,283,753,-463,880,-293,615,-985,671,-1000,46,-1000,1000,-45,110,987,-539,-733,276,-394,45,1000,-463,1000,398,287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.node.ObjectNode", "com.fasterxml.jackson.databind.node.ObjectNode", "without(java.util.Collection):com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{1000,-734,1000,-1000,229,1000,349,-699,-1000,1000,-245,898,118,213,-734,713,-564,-487,-230,243,-160,-1000,-550,1000,-267,-743,624,1000,583,-817,-151,-1000,-1000,-288,-305,-1000,-959,70,-656,-713,1000,1000,-1000,173,292,1000,151,222,-1000,-253,-1000,-467,1000,-105,1000,1000,1000,-1000,620,-1000,-166,-254,-1000,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.jsonschema.JsonSchema", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "generateJsonSchema(java.lang.Class):com.fasterxml.jackson.databind.jsonschema.JsonSchema",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.jsonschema.JsonSchema", "com.fasterxml.jackson.databind.jsonschema.JsonSchema", "equals(java.lang.Object):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.jsonschema.JsonSchema", "com.fasterxml.jackson.databind.jsonschema.JsonSchema", "equals(java.lang.Object):boolean",
            new int[]{462,384,-41,-634,277,-236,-841,-79,205,43,668,-796,-981,-946,451,130,-387,558,-774,333,342,-197,-542,-877,374,627,-499,-317,376,-16,-187,363,-332,426,404,287,93,-380,-819,622,-717,275,-280,-109,-249,2,128,-554,-708,584,108,-139,538,-135,-851,407,-789,-922,828,-8,-406,-225,607,-999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.jsonschema.JsonSchema", "", "getDefaultSchemaNode():com.fasterxml.jackson.databind.JsonNode",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.node.ObjectNode,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{583,5,526,-218,904,939,-357,-205,533,187,-899,655,293,824,547,-977,-424,766,-471,842,-831,680,478,-506,500,-836,-807,278,321,-811,598,119,65,-491,412,59,-893,744,-975,21,990,221,337,545,-700,266,-352,802,-661,-370,750,17,992,-995,-690,632,-743,-844,-641,51,-205,117,233,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.std.AsArraySerializerBase", "com.fasterxml.jackson.databind.ser.std.CollectionSerializer,com.fasterxml.jackson.databind.ser.std.EnumSetSerializer,com.fasterxml.jackson.databind.ser.std.IterableSerializer", "getSchema(com.fasterxml.jackson.databind.SerializerProvider,java.lang.reflect.Type):com.fasterxml.jackson.databind.JsonNode",
            new int[]{570,-110,953,226,97,961,347,-281,970,182,-72,-563,-413,678,816,-713,678,489,-676,-162,361,-9,-735,-577,-525,267,627,238,-378,66,-558,-131,-959,-879,394,106,645,738,-65,313,339,840,-501,173,80,-88,-193,187,-950,689,143,-33,-217,-169,-962,56,120,319,831,768,472,860,206,-604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.std.EnumMapSerializer", "com.fasterxml.jackson.databind.ser.std.EnumMapSerializer", "getSchema(com.fasterxml.jackson.databind.SerializerProvider,java.lang.reflect.Type):com.fasterxml.jackson.databind.JsonNode",
            new int[]{719,-843,-489,-922,267,-846,679,-830,62,763,-608,754,357,-750,-595,-799,-910,774,803,-338,163,826,-858,-567,920,467,-695,124,-674,-906,755,-143,328,684,-625,666,-532,718,206,353,-94,-683,-971,-860,754,521,971,-362,-181,96,-894,-91,710,776,-662,-347,612,404,-206,968,995,-881,-585,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.std.ObjectArraySerializer", "com.fasterxml.jackson.databind.ser.std.ObjectArraySerializer", "getSchema(com.fasterxml.jackson.databind.SerializerProvider,java.lang.reflect.Type):com.fasterxml.jackson.databind.JsonNode",
            new int[]{-548,-274,280,334,517,960,-723,734,-347,780,376,-310,-20,63,-406,-783,-513,332,-938,445,853,-615,955,-640,283,-169,-906,858,721,330,798,574,-950,689,670,515,-621,108,429,807,-628,884,666,7,-273,-365,-567,406,365,743,955,459,675,100,-558,114,-765,-499,-34,123,855,-958,663,-309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.std.StdArraySerializers$CharArraySerializer", "com.fasterxml.jackson.databind.ser.std.StdArraySerializers$CharArraySerializer", "getSchema(com.fasterxml.jackson.databind.SerializerProvider,java.lang.reflect.Type):com.fasterxml.jackson.databind.JsonNode",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer", "com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer", "getSchema(com.fasterxml.jackson.databind.SerializerProvider,java.lang.reflect.Type):com.fasterxml.jackson.databind.JsonNode",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer", "com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer", "getSchema(com.fasterxml.jackson.databind.SerializerProvider,java.lang.reflect.Type,boolean):com.fasterxml.jackson.databind.JsonNode",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.std.StdSerializer", "com.fasterxml.jackson.databind.ser.std.BooleanSerializer,com.fasterxml.jackson.databind.ser.std.ByteBufferSerializer,com.fasterxml.jackson.databind.ser.std.CalendarSerializer,com.fasterxml.jackson.databind.ser.std.ClassSerializer", "getSchema(com.fasterxml.jackson.databind.SerializerProvider,java.lang.reflect.Type):com.fasterxml.jackson.databind.JsonNode",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
